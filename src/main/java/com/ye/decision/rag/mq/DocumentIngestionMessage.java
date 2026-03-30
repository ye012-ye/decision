package com.ye.decision.rag.mq;

public record DocumentIngestionMessage(
    String kbCode,
    String docId,
    String filePath
) {}
