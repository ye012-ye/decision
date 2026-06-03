package com.ye.decision.kafka.exception;

/**
 * 不可恢复异常：消费时抛出将跳过重试，直接投递到死信主题。
 *
 * <p>用于业务校验失败、数据格式错误等“重试也不会成功”的场景，避免无谓的重试放大。</p>
 */
public class NonRetryableException extends RuntimeException {

    public NonRetryableException(String message) {
        super(message);
    }

    public NonRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
