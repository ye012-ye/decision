package com.ye.decision.kafka.core;

/**
 * 自定义消息头键名常量。与 Spring 内置的
 * {@code org.springframework.kafka.support.KafkaHeaders} 区分使用。
 */
public final class MessageHeaderKeys {

    private MessageHeaderKeys() {
    }

    public static final String MESSAGE_ID = "x-message-id";
    public static final String EVENT_TYPE = "x-event-type";
    public static final String TRACE_ID = "x-trace-id";
    public static final String SOURCE = "x-source";
}
