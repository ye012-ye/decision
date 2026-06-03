package com.ye.decision.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 启动时自动建主题（依赖 Spring Boot 自动装配的 KafkaAdmin）。
 *
 * <p>生产环境若由运维统一建主题，可删除本类。分区/副本通过 app.kafka.* 配置。</p>
 */
@Configuration
public class KafkaTopicConfig {

    private final KafkaTemplateProperties props;

    public KafkaTopicConfig(KafkaTemplateProperties props) {
        this.props = props;
    }

    @Bean
    public NewTopic businessTopic() {
        return TopicBuilder.name(props.getTopic())
                .partitions(props.getPartitions())
                .replicas(props.getReplicas())
                .build();
    }

    @Bean
    public NewTopic deadLetterTopic() {
        return TopicBuilder.name(props.getTopic() + props.getDltSuffix())
                .partitions(props.getPartitions())
                .replicas(props.getReplicas())
                .build();
    }
}
