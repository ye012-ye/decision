package com.ye.decision.mq;

import com.ye.decision.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 将聊天记录快照发布到 RabbitMQ。
 *
 * @author Administrator
 */
@Component
public class ChatMemoryPublisher {

    private final RabbitTemplate rabbitTemplate;

    public ChatMemoryPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(ChatMemoryMqMessage message) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, RabbitMqConfig.ROUTING, message);
    }
}
