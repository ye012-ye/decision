package com.ye.decision.mq;

import java.util.List;

/**
 * MQ消息体：携带一个会话的全量消息快照，用于异步写入MySQL。
 *
 * @author Administrator
 */
public record ChatMemoryMqMessage(
    String conversationId,
    List<MessageItem> messages
) {
    public record MessageItem(String type, String text) {}
}
