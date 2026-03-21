package com.ye.decision.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.dto.QueryRedisReq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.*;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class QueryRedisToolTest {

    @SuppressWarnings("unchecked")
    RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
    @SuppressWarnings("unchecked")
    HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
    ObjectMapper objectMapper = new ObjectMapper();
    QueryRedisTool tool;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        tool = new QueryRedisTool(redisTemplate, objectMapper);
    }

    @Test
    void string_keyFound_returnsValueAndFoundTrue() throws Exception {
        when(valueOps.get("user:1:name")).thenReturn("Alice");
        String result = tool.apply(new QueryRedisReq("user:1:name", "string"));
        assertThat(result).contains("\"value\":\"Alice\"").contains("\"found\":true");
    }

    @Test
    void string_keyNotFound_returnsFoundFalse() throws Exception {
        when(valueOps.get("missing:key")).thenReturn(null);
        String result = tool.apply(new QueryRedisReq("missing:key", "string"));
        assertThat(result).contains("\"found\":false");
    }

    @Test
    void hash_keyFound_returnsEntries() throws Exception {
        when(hashOps.entries("user:1:profile")).thenReturn(Map.of("age", 30));
        String result = tool.apply(new QueryRedisReq("user:1:profile", "hash"));
        assertThat(result).contains("\"found\":true").contains("age");
    }

    @Test
    void unknownDataType_returnsErrorJson() {
        String result = tool.apply(new QueryRedisReq("k", "unknown"));
        assertThat(result).contains("\"error\"");
    }
}
