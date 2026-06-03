package com.ye.decision.kafka.exception;

/**
 * 可重试异常：消费时抛出会触发 DefaultErrorHandler 的退避重试。
 *
 * <p>其实任何未被列入“不可重试”名单的异常都会重试；本类仅用于显式表达“这是瞬时故障”的意图。</p>
 */
public class RetryableException extends RuntimeException {

    public RetryableException(String message) {
        super(message);
    }

    public RetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
