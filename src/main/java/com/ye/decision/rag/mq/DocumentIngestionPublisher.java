package com.ye.decision.rag.mq;

import com.ye.decision.rag.config.RagConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 文档摄入消息发布者。
 * <p>
 * 将 {@link DocumentIngestionMessage} 发送到 RabbitMQ 的文档摄入交换机，
 * 实现上传与摄入的异步解耦。
 *
 * @author ye
 */
@Component
public class DocumentIngestionPublisher {

    private final RabbitTemplate rabbitTemplate;

    public DocumentIngestionPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(String kbCode, String docId, String filePath) {
        rabbitTemplate.convertAndSend(
            RagConfig.INGESTION_EXCHANGE,
            RagConfig.INGESTION_ROUTING,
            new DocumentIngestionMessage(kbCode, docId, filePath));
    }
}
