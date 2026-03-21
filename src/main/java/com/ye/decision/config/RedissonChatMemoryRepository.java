package com.ye.decision.config;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Redis-backed ChatMemoryRepository using Redisson.
 * Each conversation's messages are stored as a JSON list at "chat:memory:{conversationId}".
 * All known conversation IDs are tracked in a Redis set at "chat:memory:__ids__".
 */
public class RedissonChatMemoryRepository implements ChatMemoryRepository {

    private static final String KEY_PREFIX = "chat:memory:";
    private static final String IDS_KEY    = "chat:memory:__ids__";

    private final RedissonClient redissonClient;
    private final ObjectMapper   objectMapper;

    public RedissonChatMemoryRepository(RedissonClient redissonClient, ObjectMapper objectMapper) {
        this.redissonClient = redissonClient;
        this.objectMapper   = objectMapper;
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
        RList<String> list = redissonClient.getList(KEY_PREFIX + conversationId);
        list.clear();
        messages.stream().map(this::serialize).forEach(list::add);
        redissonClient.<String>getSet(IDS_KEY).add(conversationId);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        redissonClient.getList(KEY_PREFIX + conversationId).delete();
        redissonClient.<String>getSet(IDS_KEY).remove(conversationId);
    }

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

    @SuppressWarnings("unchecked")
    private Message deserialize(String json) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            String type = (String) map.get("type");
            String text = (String) map.get("text");
            return switch (type) {
                case "user"      -> new UserMessage(text);
                case "assistant" -> new AssistantMessage(text);
                case "system"    -> new SystemMessage(text);
                default -> throw new IllegalArgumentException("Unknown message type: " + type);
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize message", e);
        }
    }
}
