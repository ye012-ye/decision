package com.ye.decision.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.domain.dto.QueryRedisReq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class QueryRedisToolTest {

    RedissonClient redissonClient = mock(RedissonClient.class);
    @SuppressWarnings("unchecked")
    RBucket<Object> bucket = mock(RBucket.class);
    @SuppressWarnings("unchecked")
    RMap<Object, Object> hashMap = mock(RMap.class);
    ObjectMapper objectMapper = new ObjectMapper();
    QueryRedisTool tool;

    @BeforeEach
    void setUp() {
        tool = new QueryRedisTool(redissonClient, objectMapper);
    }

    @Test
    void string_keyFound_returnsValueAndFoundTrue() throws Exception {
        when(redissonClient.getBucket("user:1:name")).thenReturn(bucket);
        when(bucket.get()).thenReturn("Alice");
        String result = tool.apply(new QueryRedisReq("user:1:name", "string"));
        assertThat(result).contains("\"value\":\"Alice\"").contains("\"found\":true");
    }

    @Test
    void string_keyNotFound_returnsFoundFalse() throws Exception {
        when(redissonClient.getBucket("missing:key")).thenReturn(bucket);
        when(bucket.get()).thenReturn(null);
        String result = tool.apply(new QueryRedisReq("missing:key", "string"));
        assertThat(result).contains("\"found\":false");
    }

    @Test
    void hash_keyFound_returnsEntries() throws Exception {
        when(redissonClient.getMap("user:1:profile")).thenReturn(hashMap);
        when(hashMap.isEmpty()).thenReturn(false);
        when(hashMap.readAllMap()).thenReturn(Map.of("age", 30));
        String result = tool.apply(new QueryRedisReq("user:1:profile", "hash"));
        assertThat(result).contains("\"found\":true").contains("age");
    }

    @Test
    void unknownDataType_returnsErrorJson() {
        String result = tool.apply(new QueryRedisReq("k", "unknown"));
        assertThat(result).contains("\"error\"");
    }
}
