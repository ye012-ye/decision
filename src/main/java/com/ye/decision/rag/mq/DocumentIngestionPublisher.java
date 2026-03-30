package com.ye.decision.rag.mq;

import com.ye.decision.rag.config.RagConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

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
