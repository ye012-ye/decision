package com.ye.decision.rag.exception;

public enum RagErrorCode {

    KB_NOT_FOUND(40401, "知识库不存在"),
    KB_CODE_DUPLICATE(40901, "知识库编码已存在"),
    KB_CODE_INVALID(40001, "知识库编码包含非法字符"),

    DOC_NOT_FOUND(40402, "文档不存在"),
    DOC_ID_INVALID(40002, "文档ID包含非法字符"),
    DOC_FILE_TYPE_UNSUPPORTED(40003, "不支持的文件类型"),
    DOC_FILE_EMPTY(40004, "上传文件为空"),

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
