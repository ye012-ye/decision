package com.ye.decision.rag.mq;

/**
 * 文档摄入 MQ 消息体。
 * <p>
 * 由 {@link DocumentIngestionPublisher} 发布到 RabbitMQ，
 * {@link DocumentIngestionConsumer} 消费后触发摄入管线。
 *
 * @author ye
 * @param kbCode   目标知识库编码
 * @param docId    文档唯一标识（UUID）
 * @param filePath 文件在磁盘上的绝对路径
 */
public record DocumentIngestionMessage(
    String kbCode,
    String docId,
    String filePath
) {}
