package com.ye.decision.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RList;
import org.redisson.api.RMap;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Redis 查询工具。
 * @author ye
 */
@Component
public class QueryRedisTool {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    public QueryRedisTool(RedissonClient redissonClient, ObjectMapper objectMapper) {
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "queryRedisTool", description = "查询 Redis 中的缓存数据、热点数据、实时计数器、会话信息或排行榜。适用于低延迟、高频访问场景。")
    public String queryRedis(
            @ToolParam(description = "Redis key 或 key 模式") String keyPattern,
            @ToolParam(description = "数据类型: string / hash / zset / list") String dataType) {
        try {
            return switch (dataType) {
                case "string" -> queryString(keyPattern);
                case "hash"   -> queryHash(keyPattern);
                case "zset"   -> queryZset(keyPattern);
                case "list"   -> queryList(keyPattern);
                default       -> errorJson("unsupported_type", "不支持的 dataType: " + dataType);
            };
        } catch (Exception e) {
            return errorJson("redis_error", e.getMessage());
        }
    }

    private String queryString(String key) throws Exception {
        Object value = redissonClient.getBucket(key).get();
        return buildResponse(key, "string", value, value != null);
    }

    private String queryHash(String key) throws Exception {
        RMap<Object, Object> map = redissonClient.getMap(key);
        boolean found = !map.isEmpty();
        return buildResponse(key, "hash", found ? map.readAllMap() : null, found);
    }

    private String queryZset(String key) throws Exception {
        RScoredSortedSet<Object> zset = redissonClient.getScoredSortedSet(key);
        boolean found = !zset.isEmpty();
        return buildResponse(key, "zset", found ? new ArrayList<>(zset.readAll()) : null, found);
    }

    private String queryList(String key) throws Exception {
        RList<Object> list = redissonClient.getList(key);
        boolean found = !list.isEmpty();
        return buildResponse(key, "list", found ? new ArrayList<>(list) : null, found);
    }

    private String buildResponse(String key, String type, Object value, boolean found) throws Exception {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("key", key);
        resp.put("type", type);
        resp.put("value", value);
        resp.put("found", found);
        return objectMapper.writeValueAsString(resp);
    }

    private String errorJson(String code, String message) {
        return "{\"error\":\"" + code + "\",\"message\":\"" + message + "\",\"tool\":\"queryRedisTool\"}";
    }
}
