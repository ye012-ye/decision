package com.ye.decision.kafka.core;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * 通用消息发送服务：对 KafkaTemplate 的薄封装，统一注入消息头、统一日志。
 *
 * <p>优先使用 {@link #sendAsync}（非阻塞）；仅在“必须确保已落盘才能继续”的关键链路用
 * {@link #sendSync}。</p>
 */
@Slf4j
@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /** 异步发送（推荐）：返回 future 并注册回调记录成功/失败，不阻塞业务线程。 */
    public CompletableFuture<SendResult<String, Object>> sendAsync(String topic, String key, KafkaMessage message) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(buildRecord(topic, key, message));
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[Kafka] 发送失败 topic={} key={} messageId={} : {}",
                        topic, key, message.getMessageId(), ex.getMessage(), ex);
            } else if (log.isDebugEnabled()) {
                log.debug("[Kafka] 发送成功 topic={} partition={} offset={} messageId={}",
                        topic, result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(), message.getMessageId());
            }
        });
        return future;
    }

    /** 同步发送：阻塞直到 broker 确认。失败抛出运行时异常。 */
    public SendResult<String, Object> sendSync(String topic, String key, KafkaMessage message) {
        try {
            return sendAsync(topic, key, message).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("发送被中断 topic=" + topic, e);
        } catch (Exception e) {
            throw new IllegalStateException("同步发送失败 topic=" + topic, e);
        }
    }

    private ProducerRecord<String, Object> buildRecord(String topic, String key, KafkaMessage message) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, message);
        addHeader(record, MessageHeaderKeys.MESSAGE_ID, message.getMessageId());
        addHeader(record, MessageHeaderKeys.EVENT_TYPE, message.getEventType());
        addHeader(record, MessageHeaderKeys.TRACE_ID, message.getTraceId());
        addHeader(record, MessageHeaderKeys.SOURCE, message.getSource());
        return record;
    }

    private void addHeader(ProducerRecord<String, Object> record, String key, String value) {
        if (value != null) {
            record.headers().add(new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8)));
        }
    }
}
