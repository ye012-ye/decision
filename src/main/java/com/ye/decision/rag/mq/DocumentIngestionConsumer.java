package com.ye.decision.rag.mq;

import com.ye.decision.rag.config.RagConfig;
import com.ye.decision.rag.service.DocumentIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

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
        ingestionService.ingest(message.kbCode(), message.docId(), message.filePath());
    }
}
