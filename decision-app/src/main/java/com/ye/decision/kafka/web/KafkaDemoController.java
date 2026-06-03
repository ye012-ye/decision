package com.ye.decision.kafka.web;

import com.ye.decision.kafka.config.KafkaTemplateProperties;
import com.ye.decision.kafka.core.KafkaMessage;
import com.ye.decision.kafka.core.KafkaProducerService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 仅用于本地联调：触发一条示例消息。生产环境请删除或加权限控制。
 *
 * <pre>
 * curl -X POST "http://localhost:8080/api/kafka/send?eventType=ORDER_CREATED" \
 *      -H "Content-Type: application/json" -d '{"orderId":1001,"amount":99.9}'
 * </pre>
 */
@RestController
@RequestMapping("/api/kafka")
public class KafkaDemoController {

    private final KafkaProducerService producerService;
    private final KafkaTemplateProperties props;

    public KafkaDemoController(KafkaProducerService producerService, KafkaTemplateProperties props) {
        this.producerService = producerService;
        this.props = props;
    }

    @PostMapping("/send")
    public Map<String, Object> send(
            @RequestParam(defaultValue = "DEMO_EVENT") String eventType,
            @RequestBody(required = false) Object payload) {
        KafkaMessage message = KafkaMessage.of(eventType, payload);
        message.setSource("kafka-template");
        producerService.sendAsync(props.getTopic(), message.getMessageId(), message);
        return Map.of("status", "accepted", "messageId", message.getMessageId());
    }
}
