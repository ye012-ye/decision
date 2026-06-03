package com.ye.decision.kafka.consumer;

/**
 * 幂等检查抽象：消费端据消息ID判断是否首次处理。
 *
 * <p>Kafka 是 at-least-once 投递，消费端必须自行去重以达到“效果上恰好一次”。</p>
 */
public interface IdempotencyChecker {

    /**
     * 原子地“标记并判断是否首次”。
     *
     * @param messageId 全局唯一消息ID
     * @return true=首次处理（应继续）；false=重复消息（应跳过）
     */
    boolean markIfAbsent(String messageId);
}
