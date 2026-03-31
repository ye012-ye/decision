package com.ye.decision.rag.exception;

/**
 * RAG 模块业务错误码。
 * <p>
 * 编码规则：{@code XXYYZ}
 * <ul>
 *   <li>XX  — HTTP 语义（40=客户端错误，50=服务端错误）</li>
 *   <li>YY  — 资源类型（04=知识库，09=冲突；同上用于文档）</li>
 *   <li>Z   — 序号</li>
 * </ul>
 *
 * @author ye
 */
public enum RagErrorCode {

    /* ── 知识库相关 ──────────────────────────────────────────── */

    KB_NOT_FOUND(40401, "知识库不存在"),
    KB_CODE_DUPLICATE(40901, "知识库编码已存在"),
    KB_CODE_INVALID(40001, "知识库编码包含非法字符"),

    /* ── 文档相关 ────────────────────────────────────────────── */

    DOC_NOT_FOUND(40402, "文档不存在"),
    DOC_ID_INVALID(40002, "文档ID包含非法字符"),
    DOC_FILE_TYPE_UNSUPPORTED(40003, "不支持的文件类型"),
    DOC_FILE_EMPTY(40004, "上传文件为空"),

    /* ── 服务端错误 ──────────────────────────────────────────── */

    INGESTION_FAILED(50001, "文档摄入失败"),
    SEARCH_FAILED(50002, "知识库检索失败");

    private final int code;
    private final String message;

    RagErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
