package com.ye.decision.mcp.exception;

/**
 * MCP 模块业务错误码。
 * <p>
 * 编码规则：{@code XXYYZ}，使用 06 作为 MCP 模块标识，避免与 RAG（04/09）冲突。
 * <ul>
 *   <li>XX  — HTTP 语义（40=客户端错误，50=服务端错误）</li>
 *   <li>YY  — 模块标识（06=MCP）</li>
 *   <li>Z   — 序号</li>
 * </ul>
 *
 * @author ye
 */
public enum McpErrorCode {

    /* ── 客户端错误 ──────────────────────────────────────────── */

    TABLE_NOT_IN_WHITELIST(40601, "表不在允许访问的白名单中"),
    TABLE_IN_BLACKLIST(40602, "表在黑名单中，禁止访问"),
    SQL_SYNTAX_INVALID(40603, "SQL语法无效"),
    SQL_INJECTION_DETECTED(40604, "检测到潜在SQL注入"),
    SQL_FORBIDDEN_KEYWORD(40605, "SQL包含禁止的关键词"),
    SQL_NOT_READONLY(40606, "仅允许SELECT查询"),
    SQL_TOO_LONG(40607, "SQL语句超出长度限制"),
    TABLE_NOT_FOUND(40608, "表不存在"),
    WRITE_NOT_ENABLED(40609, "写操作未启用"),
    MULTI_STATEMENT_DENIED(40610, "不允许多语句执行"),
    WHITELIST_NOT_FOUND(40611, "白名单记录不存在"),

    /* ── 服务端错误 ──────────────────────────────────────────── */

    SQL_EXECUTION_FAILED(50601, "SQL执行失败"),
    SQL_TIMEOUT(50602, "SQL执行超时");

    private final int code;
    private final String message;

    McpErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
