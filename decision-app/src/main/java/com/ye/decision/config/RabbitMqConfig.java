package com.ye.decision.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 交换机 / 队列 / 绑定声明。
 *
 * @author Administrator
 */
@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "chat.memory.exchange";
    public static final String QUEUE    = "chat.memory.sync.queue";
    public static final String ROUTING  = "chat.memory.sync";

    @Bean
    public DirectExchange chatMemoryExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue chatMemorySyncQueue() {
        return QueueBuilder.durable(QUEUE)
                .build();
    }

    @Bean
    public Binding chatMemorySyncBinding(DirectExchange chatMemoryExchange, Queue chatMemorySyncQueue) {
        return BindingBuilder.bind(chatMemorySyncQueue).to(chatMemoryExchange).with(ROUTING);
    }

    /** 使用 Jackson 序列化 MQ 消息体 */
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
