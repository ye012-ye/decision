package com.ye.decision.rag.exception;

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
