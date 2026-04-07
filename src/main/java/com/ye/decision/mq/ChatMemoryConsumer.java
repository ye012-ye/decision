package com.ye.decision.mq;

import com.ye.decision.config.RabbitMqConfig;
import com.ye.decision.config.RedissonChatMemoryRepository;
import com.ye.decision.domain.entity.ChatMessageEntity;
import com.ye.decision.mapper.ChatMessageMapper;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 监听 MQ 消息，将聊天记录异步写入 MySQL。
 * 写入成功后从 pending 集合中移除该 conversationId。
 *
 * @author Administrator
 */
@Component
public class ChatMemoryConsumer {

    private final ChatMessageMapper chatMessageMapper;
    private final RedissonClient    redissonClient;

    public ChatMemoryConsumer(ChatMessageMapper chatMessageMapper, RedissonClient redissonClient) {
        this.chatMessageMapper = chatMessageMapper;
        this.redissonClient    = redissonClient;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE)
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(ChatMemoryMqMessage msg) {
        persistToMysql(msg.conversationId(), msg.messages());
        redissonClient.<String>getSet(RedissonChatMemoryRepository.PENDING_KEY)
                      .remove(msg.conversationId());
    }

    public void persistToMysql(String conversationId, List<ChatMemoryMqMessage.MessageItem> items) {
        chatMessageMapper.deleteByConversationId(conversationId);
        for (int i = 0; i < items.size(); i++) {
            ChatMemoryMqMessage.MessageItem item = items.get(i);
            chatMessageMapper.insert(new ChatMessageEntity(conversationId, i, item.type(), item.text()));
        }
    }
}
