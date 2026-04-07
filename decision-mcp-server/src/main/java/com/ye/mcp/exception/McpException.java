package com.ye.mcp.exception;

/**
 * MCP 模块业务异常。
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
