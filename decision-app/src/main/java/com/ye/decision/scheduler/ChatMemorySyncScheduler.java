package com.ye.decision.scheduler;

import com.ye.decision.config.RedissonChatMemoryRepository;
import com.ye.decision.mq.ChatMemoryConsumer;
import com.ye.decision.mq.ChatMemoryMqMessage;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 定时任务保底策略：每 60 秒扫描 pending 集合，
 * 对尚未被 MQ 消费写入 MySQL 的会话执行直接同步。
 *
 * @author Administrator
 */
@Component
public class ChatMemorySyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(ChatMemorySyncScheduler.class);

    private final RedissonClient      redissonClient;
    private final ChatMemoryConsumer  consumer;

    public ChatMemorySyncScheduler(RedissonClient redissonClient, ChatMemoryConsumer consumer) {
        this.redissonClient = redissonClient;
        this.consumer       = consumer;
    }

    @Scheduled(fixedDelay = 60_000)
    public void syncPending() {
        var pendingSet = redissonClient.<String>getSet(RedissonChatMemoryRepository.PENDING_KEY);
        if (pendingSet == null) {
            return;
        }
        var pendingIds = pendingSet.readAll();
        if (pendingIds.isEmpty()) {
            return;
        }

        log.info("ChatMemorySyncScheduler: {} pending conversation(s) to sync", pendingIds.size());
        for (String conversationId : pendingIds) {
            try {
                RList<String> redisMessages = redissonClient.getList(
                    RedissonChatMemoryRepository.KEY_PREFIX + conversationId);
                List<ChatMemoryMqMessage.MessageItem> items = redisMessages.stream()
                    .map(RedissonChatMemoryRepository::parseItem)
                    .toList();
                consumer.persistToMysql(conversationId, items);
                pendingSet.remove(conversationId);
            } catch (Exception e) {
                log.error("Failed to sync conversation {} to MySQL", conversationId, e);
            }
        }
    }
}
