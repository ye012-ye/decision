package com.ye.decision.rag.mq;

/**
 * 文档摄入 MQ 消息体。
 *
 * @author ye
 * @param kbCode   目标知识库编码
 * @param docId    文档唯一标识（UUID）
 * @param filePath 文件在磁盘上的绝对路径
 * @param fileName 原始文件名，摄入时注入到 chunk 元数据供来源溯源
 */
public record DocumentIngestionMessage(
    String kbCode,
    String docId,
    String filePath,
    String fileName
) {}
