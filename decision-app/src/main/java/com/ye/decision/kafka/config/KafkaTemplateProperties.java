package com.ye.decision.kafka.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 模板自定义配置（前缀 app.kafka）。
 *
 * <p>连接级参数（bootstrap-servers、SASL/SSL 等）仍走 Spring 标准的 {@code spring.kafka.*}，
 * 这里只放业务/可靠性相关、需要被代码读取的项，避免与 Spring Boot 自动配置重复。</p>
 */
@Data
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaTemplateProperties {

    /** 业务主题名。 */
    private String topic = "demo-topic";

    /** 死信主题后缀，最终死信主题名 = topic + dltSuffix。 */
    private String dltSuffix = ".DLT";

    /** 消费者组。 */
    private String consumerGroup = "demo-group";

    /** 监听容器并发线程数，建议设为目标主题的分区数。 */
    private int concurrency = 3;

    /** 自动建主题时的分区数。 */
    private int partitions = 3;

    /** 自动建主题时的副本因子，单机=1，生产集群建议 >= 3。 */
    private short replicas = 1;

    /** 消费失败重试策略。 */
    private final Retry retry = new Retry();

    @Data
    public static class Retry {
        /** 最大重试次数（不含首次消费）。 */
        private int maxAttempts = 3;
        /** 初始重试间隔(ms)。 */
        private long backoffMs = 1000;
        /** 退避倍数（每次重试间隔 *= multiplier）。 */
        private double multiplier = 2.0;
        /** 重试间隔上限(ms)。 */
        private long maxBackoffMs = 10000;
    }
}
