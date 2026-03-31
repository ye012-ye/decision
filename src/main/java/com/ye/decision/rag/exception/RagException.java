package com.ye.decision.rag.exception;

/**
 * RAG 模块业务异常。
 * <p>
 * 携带 {@link RagErrorCode} 错误码，由 {@link com.ye.decision.common.GlobalExceptionHandler}
 * 统一捕获并转换为标准 {@link com.ye.decision.common.Result} 响应。
 *
 * @author ye
 */
public class RagException extends RuntimeException {

    private final RagErrorCode errorCode;

    public RagException(RagErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public RagException(RagErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + ": " + detail);
        this.errorCode = errorCode;
    }

    public RagException(RagErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public int getCode() {
        return errorCode.getCode();
    }

    public RagErrorCode getErrorCode() {
        return errorCode;
    }
}
