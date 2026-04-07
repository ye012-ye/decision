package com.ye.decision.rag.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 文档摄入状态枚举。
 * <p>
 * 状态流转：{@code PENDING → PROCESSING → COMPLETED / FAILED}
 * <ul>
 *   <li>{@link #PENDING}    — 文件已上传，等待消费端拉取</li>
 *   <li>{@link #PROCESSING} — 消费端正在执行 解析 → 切片 → 嵌入</li>
 *   <li>{@link #COMPLETED}  — 摄入完成，向量已写入 Milvus</li>
 *   <li>{@link #FAILED}     — 摄入失败，错误信息记录在 error_message 字段</li>
 * </ul>
 * <p>
 * 通过 {@code @EnumValue} 与 MyBatis-Plus 自动映射，{@code @JsonValue} 序列化为 code 字符串。
 *
 * @author ye
 */
public enum DocumentStatus {
    /*
     * 待处理
     */
    PENDING("PENDING", "待处理"),
    /*
     * 处理中
     */
    PROCESSING("PROCESSING", "处理中"),
    /*
     * 处理完成
     */
    COMPLETED("COMPLETED", "已完成"),
    /*
     * 处理失败
     */
    FAILED("FAILED", "失败");

    @EnumValue
    @JsonValue
    private final String code;
    private final String label;

    DocumentStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
