package com.ye.decision.kafka.config;

import com.ye.decision.kafka.core.KafkaMessage;
import com.ye.decision.kafka.exception.NonRetryableException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * 消费者配置：保证 at-least-once + 失败可控。
 *
 * <ul>
 *   <li>关闭自动提交，由容器在记录处理成功后按 RECORD 模式提交位移。</li>
 *   <li>ErrorHandlingDeserializer 包裹反序列化器：避免“毒丸消息”反复抛错卡死分区。</li>
 *   <li>DefaultErrorHandler + 指数退避：可重试异常本地重试，耗尽后投递死信主题。</li>
 *   <li>{@link NonRetryableException}：业务判定不可恢复，跳过重试直接进死信。</li>
 * </ul>
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    private final KafkaProperties kafkaProperties;
    private final KafkaTemplateProperties appProps;

    public KafkaConsumerConfig(KafkaProperties kafkaProperties, KafkaTemplateProperties appProps) {
        this.kafkaProperties = kafkaProperties;
        this.appProps = appProps;
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, appProps.getConsumerGroup());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // 消费位移初始位置：从头开始消费，避免消费组迁移后无法消费
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        // 反序列化容错：外层 ErrorHandlingDeserializer，内层真实反序列化器
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, KafkaMessage.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);
        //
        props.putAll(kafkaProperties.getConsumer().getProperties());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /** 业务监听容器工厂：携带重试 + 死信的错误处理器。 */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory, DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(appProps.getConcurrency());
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        // 重试耗尽后投递到 <topic>.DLT，保持与原消息相同的分区号便于排查
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(
                        record.topic() + appProps.getDltSuffix(), record.partition()));

        KafkaTemplateProperties.Retry retry = appProps.getRetry();
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(retry.getMaxAttempts());
        backOff.setInitialInterval(retry.getBackoffMs());
        backOff.setMultiplier(retry.getMultiplier());
        backOff.setMaxInterval(retry.getMaxBackoffMs());

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // 业务不可恢复异常不重试，直接进死信
        handler.addNotRetryableExceptions(NonRetryableException.class);
        // 投递死信后提交原始位移，避免重复消费
        handler.setCommitRecovered(true);
        return handler;
    }

    // ---- 死信消费专用：用 String 反序列化，避免对“毒丸消息”再次反序列化失败形成死循环 ----

    @Bean
    public ConsumerFactory<String, String> dltConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, appProps.getConsumerGroup() + "-dlt");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> dltKafkaListenerContainerFactory(
            ConsumerFactory<String, String> dltConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(dltConsumerFactory);
        // 单线程
        factory.setConcurrency(1);
        // 手动提交
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }
}
