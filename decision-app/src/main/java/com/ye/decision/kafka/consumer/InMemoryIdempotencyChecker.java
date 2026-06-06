package com.ye.decision.kafka.consumer;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的幂等检查（默认实现，仅供单实例 / 演示使用）。
 *
 * <p><b>注意：</b>该实现的去重集合不会过期、且不跨实例共享。生产多副本部署时请自行提供
 * 基于 Redis（如 SETNX + TTL）或数据库唯一键的 {@link IdempotencyChecker} 实现，
 * 并在该实现上标注 {@code @Primary} 以覆盖本默认实现。</p>
 */
@Component
public class InMemoryIdempotencyChecker implements IdempotencyChecker {

    private final Map<String, Boolean> seen = new ConcurrentHashMap<>();

    @Override
    public boolean markIfAbsent(String messageId) {
        return seen.putIfAbsent(messageId, Boolean.TRUE) == null;
    }
}
