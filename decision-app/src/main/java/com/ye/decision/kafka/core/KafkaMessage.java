package com.ye.decision.kafka.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * 统一消息信封：所有业务消息外包一层，承载幂等、链路追踪、审计所需的元数据。
 *
 * <p>payload 为任意业务对象；消费端经 JSON 反序列化后通常是 Map，可用
 * {@link #payloadAs(Class)} 转成目标类型。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KafkaMessage implements Serializable {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    /** 全局唯一消息ID，消费端据此做幂等去重。 */
    private String messageId;

    /** 业务事件类型，例如 ORDER_CREATED。 */
    private String eventType;

    /** 消息来源（生产方服务名）。 */
    private String source;

    /** 链路追踪ID，便于跨服务排查。 */
    private String traceId;

    /** 业务负载。 */
    private Object payload;

    /** 生产时间(epoch millis)。 */
    private Long timestamp;

    /** 快速构造：自动生成 messageId 与时间戳。 */
    public static KafkaMessage of(String eventType, Object payload) {
        return KafkaMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .eventType(eventType)
                .payload(payload)
                .timestamp(Instant.now().toEpochMilli())
                .build();
    }

    /** 将 payload 转换为目标类型。 */
    public <T> T payloadAs(Class<T> type) {
        return MAPPER.convertValue(payload, type);
    }
}
