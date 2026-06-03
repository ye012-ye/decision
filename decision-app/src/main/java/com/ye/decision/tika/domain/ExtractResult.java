package com.ye.decision.tika.domain;

import java.util.Map;

/** 单个文档（或单个嵌入文档）的抽取结果。不可变。 */
public record ExtractResult(
        Status status,
        String text,
        /**
         * 纯文本的 MIME 类型。
         */
        String contentType,
        Map<String, String> metadata,
        /**
         * 是否被截断。
         */
        boolean truncated,
        String message) {

    public ExtractResult {
        // 收口：永不暴露可变集合 / null
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        text = text == null ? "" : text;
    }

    public boolean ok() {
        return status == Status.OK;
    }

    public enum Status {
        OK,          // 成功（text 可信；truncated=true 时为截断版）
        ENCRYPTED,   // 加密且没给对密码
        TIMEOUT,     // 超过 parseTimeout
        SKIPPED_MIME,// 不在 MIME 白名单
        FAILED       // 文件损坏 / 不支持 / IO 失败（message 里有原因）
    }
}