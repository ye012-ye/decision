package com.ye.decision.kafka.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 死信消费者：消费 &lt;topic&gt;.DLT，对最终失败的消息做记录/告警/人工兜底。
 *
 * <p>使用独立的 String 容器工厂，避免对“反序列化失败的毒丸消息”再次反序列化而陷入死循环。
 * DeadLetterPublishingRecoverer 会把原始异常信息写入消息头。</p>
 */
@Slf4j
@Component
public class DeadLetterConsumer {

    @KafkaListener(
            topics = "${app.kafka.topic}${app.kafka.dlt-suffix}",
            groupId = "${app.kafka.consumer-group}-dlt",
            containerFactory = "dltKafkaListenerContainerFactory")
    public void onDeadLetter(
            ConsumerRecord<String, String> record,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) byte[] originalTopic,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exceptionMessage) {

        String origin = originalTopic == null ? "unknown" : new String(originalTopic, StandardCharsets.UTF_8);
        log.error("[Kafka-DLT] 收到死信 dltTopic={} originalTopic={} key={} error={} payload={}",
                record.topic(), origin, record.key(), exceptionMessage, record.value());

        // TODO: 落库 / 告警 / 触发人工介入。死信默认不再自动重试。
    }
}
