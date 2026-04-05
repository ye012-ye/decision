package com.ye.decision.mcp.exception;

/**
 * MCP 模块业务异常。
 * <p>
 * 携带 {@link McpErrorCode} 错误码，由 {@link com.ye.decision.common.GlobalExceptionHandler}
 * 统一捕获并转换为标准 {@link com.ye.decision.common.Result} 响应。
 *
 * @author ye
 */
public class McpException extends RuntimeException {

    private final McpErrorCode errorCode;

    public McpException(McpErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public McpException(McpErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + ": " + detail);
        this.errorCode = errorCode;
    }

    public McpException(McpErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public int getCode() {
        return errorCode.getCode();
    }

    public McpErrorCode getErrorCode() {
        return errorCode;
    }
}
