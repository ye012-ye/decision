package com.ye.decision.rag.mq;

import com.ye.decision.rag.config.RagConfig;
import com.ye.decision.rag.service.DocumentIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 文档摄入消息消费者。
 * <p>
 * 监听文档摄入队列，接收到消息后调用 {@link DocumentIngestionService#ingest}
 * 执行完整的 解析 → 切片 → 嵌入 流程。
 * <p>
 * 重试策略：
 * <ul>
 *   <li>瞬时故障（Milvus 不可用、网络抖动）—— ingestionService 抛出 RuntimeException，
 *       由 Spring Retry（bootstrap.yaml 配置 max-attempts=3）自动重试</li>
 *   <li>重试耗尽后 —— 消息进入死信队列（DLQ），文档状态标记为 FAILED</li>
 *   <li>永久性故障（文件损坏、解析为空）—— ingestionService 内部标记 FAILED，不抛异常不重试</li>
 * </ul>
 *
 * @author ye
 * @see com.ye.decision.rag.config.RagConfig
 */
@Component
public class DocumentIngestionConsumer {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionConsumer.class);

    private final DocumentIngestionService ingestionService;

    public DocumentIngestionConsumer(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @RabbitListener(queues = RagConfig.INGESTION_QUEUE)
    public void onMessage(DocumentIngestionMessage message) {
        log.info("Received ingestion task: kbCode={}, docId={}", message.kbCode(), message.docId());
        try {
            ingestionService.ingest(message.kbCode(), message.docId(), message.filePath(), message.fileName());
        } catch (RuntimeException e) {
            // 瞬时故障：让异常传播给 Spring Retry 进行重试，
            // 重试耗尽后 Spring 会 reject 消息路由到 DLQ
            log.warn("Ingestion retriable failure, docId={}, attempt will be retried by Spring Retry: {}",
                message.docId(), e.getMessage());
            // 标记失败状态（如果重试成功会被覆盖为 COMPLETED）
            ingestionService.markFailed(message.docId(), "重试中: " + e.getMessage());
            throw e;
        }
    }
}
