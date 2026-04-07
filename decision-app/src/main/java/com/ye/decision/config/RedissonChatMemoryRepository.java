package com.ye.decision.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.mq.ChatMemoryMqMessage;
import com.ye.decision.mq.ChatMemoryPublisher;
import org.redisson.api.RList;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 使用 Redisson 支持的 ChatMemoryRepository。
 * 每个对话的消息都以 JSON 列表存储在 "chat：memory：{conversationId}"。
 * 所有已知的对话ID都被记录在Redis的"chat：memory：__ids__"中。
 * saveAll() 同时将 conversationId 加入 pending 集合并发布 MQ 消息供异步写入 MySQL。
 */
public class RedissonChatMemoryRepository implements ChatMemoryRepository {

    public static final String KEY_PREFIX  = "chat:memory:";
    public static final String IDS_KEY     = "chat:memory:__ids__";
    public static final String PENDING_KEY = "chat:memory:pending:sync";

    private final RedissonClient       redissonClient;
    private final ObjectMapper         objectMapper;
    private final ChatMemoryPublisher  publisher;

    public RedissonChatMemoryRepository(RedissonClient redissonClient,
                                        ObjectMapper objectMapper,
                                        ChatMemoryPublisher publisher) {
        this.redissonClient = redissonClient;
        this.objectMapper   = objectMapper;
        this.publisher      = publisher;
    }

    @Override
    public List<String> findConversationIds() {
        RSet<String> ids = redissonClient.getSet(IDS_KEY);
        return new ArrayList<>(ids.readAll());
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        RList<String> list = redissonClient.getList(KEY_PREFIX + conversationId);
        return list.stream().map(this::deserialize).collect(Collectors.toList());
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        // 1. 写 Redis
        RList<String> list = redissonClient.getList(KEY_PREFIX + conversationId);
        list.clear();
        messages.stream().map(this::serialize).forEach(list::add);
        redissonClient.<String>getSet(IDS_KEY).add(conversationId);

        // 2. 加入 pending 集合（定时任务保底）
        redissonClient.<String>getSet(PENDING_KEY).add(conversationId);

        // 3. 发布 MQ 消息（异步写 MySQL）
        List<ChatMemoryMqMessage.MessageItem> items = messages.stream()
            .map(m -> new ChatMemoryMqMessage.MessageItem(m.getMessageType().getValue(), m.getText()))
            .toList();
        publisher.publish(new ChatMemoryMqMessage(conversationId, items));
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        redissonClient.getList(KEY_PREFIX + conversationId).delete();
        redissonClient.<String>getSet(IDS_KEY).remove(conversationId);
        redissonClient.<String>getSet(PENDING_KEY).remove(conversationId);
    }

    // ── serialization ────────────────────────────────────────────────────────

    private String serialize(Message message) {
        try {
            Map<String, Object> map = Map.of(
                "type", message.getMessageType().getValue(),
                "text", message.getText()
            );
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize message", e);
        }
    }

    private Message deserialize(String json) {
        ChatMemoryMqMessage.MessageItem item = parseItem(json);
        return switch (item.type()) {
            case "user"      -> new UserMessage(item.text());
            case "assistant" -> new AssistantMessage(item.text());
            case "system"    -> new SystemMessage(item.text());
            default -> throw new IllegalArgumentException("Unknown message type: " + item.type());
        };
    }

    /** Static helper so the scheduler can reuse parsing without holding an instance. */
    @SuppressWarnings("unchecked")
    public static ChatMemoryMqMessage.MessageItem parseItem(String json) {
        try {
            ObjectMapper om = new ObjectMapper();
            Map<String, Object> map = om.readValue(json, Map.class);
            return new ChatMemoryMqMessage.MessageItem(
                (String) map.get("type"),
                (String) map.get("text")
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse message item", e);
        }
    }
}
