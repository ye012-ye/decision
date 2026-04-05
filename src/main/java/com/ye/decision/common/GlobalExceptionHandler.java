package com.ye.decision.common;

import com.ye.decision.mcp.exception.McpException;
import com.ye.decision.rag.exception.RagException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 参数校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return Result.error(400, msg);
    }

    /** RAG 业务异常 */
    @ExceptionHandler(RagException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleRagException(RagException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    /** MCP 业务异常 */
    @ExceptionHandler(McpException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMcpException(McpException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    /** 非法参数 */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        return Result.error(400, e.getMessage());
    }

    /** 兜底 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return Result.error(500, "服务内部错误");
    }
}
