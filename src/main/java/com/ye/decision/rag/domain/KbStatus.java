package com.ye.decision.rag.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * @author ye
 */

public enum KbStatus {
    /**
     * 禁用
     */
    ACTIVE(1, "ACTIVE"),
    /**
     * 启用
     */
    DISABLED(0, "DISABLED");


    @EnumValue
    @JsonValue
    private final int code;
    private final String label;

    KbStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
