package com.ye.decision.kafka.consumer;

import com.ye.decision.kafka.core.KafkaMessage;
import com.ye.decision.kafka.exception.NonRetryableException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 业务消费者示例：演示「幂等去重 -> 业务处理 -> 异常分类」的标准消费流程。
 *
 * <p>无需手动 ack：方法正常返回后容器按 RECORD 模式提交位移；抛异常则交由
 * DefaultErrorHandler 重试，重试耗尽后进入 &lt;topic&gt;.DLT。</p>
 */
@Slf4j
@Component
public class DemoMessageConsumer {

    private final IdempotencyChecker idempotencyChecker;

    public DemoMessageConsumer(IdempotencyChecker idempotencyChecker) {
        this.idempotencyChecker = idempotencyChecker;
    }

    @KafkaListener(
            topics = "${app.kafka.topic}",
            groupId = "${app.kafka.consumer-group}",
            containerFactory = "kafkaListenerContainerFactory")
    public void onMessage(ConsumerRecord<String, KafkaMessage> record) {
        KafkaMessage message = record.value();

        // 1) 幂等去重：相同 messageId 只处理一次
        if (message.getMessageId() != null && !idempotencyChecker.markIfAbsent(message.getMessageId())) {
            log.warn("[Kafka] 重复消息已跳过 messageId={}", message.getMessageId());
            return;
        }

        // 2) 业务处理
        log.info("[Kafka] 收到消息 topic={} partition={} offset={} eventType={} payload={}",
                record.topic(), record.partition(), record.offset(),
                message.getEventType(), message.getPayload());
        process(message);
    }

    /**
     * 真实业务逻辑占位。
     *
     * <p>异常约定：瞬时故障抛普通异常或 {@code RetryableException}（会重试）；
     * 业务不可恢复抛 {@link NonRetryableException}（不重试，直接进死信）。</p>
     */
    private void process(KafkaMessage message) {
        if (message.getPayload() == null) {
            throw new NonRetryableException("payload 为空，无法处理 messageId=" + message.getMessageId());
        }
        // TODO: 替换为你的业务逻辑
    }
}
