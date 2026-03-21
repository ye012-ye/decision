package com.ye.decision.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.dto.QueryRedisReq;
import org.redisson.api.RList;
import org.redisson.api.RMap;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;

import java.util.*;
import java.util.function.Function;

public class QueryRedisTool implements Function<QueryRedisReq, String> {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    public QueryRedisTool(RedissonClient redissonClient, ObjectMapper objectMapper) {
        this.redissonClient = redissonClient;
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
