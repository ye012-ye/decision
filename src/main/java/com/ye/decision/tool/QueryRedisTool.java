package com.ye.decision.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.dto.QueryRedisReq;
import org.springframework.data.redis.core.*;

import java.util.*;
import java.util.function.Function;

public class QueryRedisTool implements Function<QueryRedisReq, String> {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public QueryRedisTool(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String apply(QueryRedisReq req) {
        try {
            return switch (req.dataType()) {
                case "string" -> queryString(req.keyPattern());
                case "hash"   -> queryHash(req.keyPattern());
                case "zset"   -> queryZset(req.keyPattern());
                case "list"   -> queryList(req.keyPattern());
                default       -> errorJson("unsupported_type", "不支持的 dataType: " + req.dataType());
            };
        } catch (Exception e) {
            return errorJson("redis_error", e.getMessage());
        }
    }

    private String queryString(String key) throws Exception {
        Object value = redisTemplate.opsForValue().get(key);
        return buildResponse(key, "string", value, value != null);
    }

    private String queryHash(String key) throws Exception {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        boolean found = !entries.isEmpty();
        return buildResponse(key, "hash", found ? entries : null, found);
    }

    private String queryZset(String key) throws Exception {
        Set<Object> members = redisTemplate.opsForZSet().range(key, 0, -1);
        boolean found = members != null && !members.isEmpty();
        return buildResponse(key, "zset", found ? new ArrayList<>(members) : null, found);
    }

    private String queryList(String key) throws Exception {
        List<Object> items = redisTemplate.opsForList().range(key, 0, -1);
        boolean found = items != null && !items.isEmpty();
        return buildResponse(key, "list", found ? items : null, found);
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
