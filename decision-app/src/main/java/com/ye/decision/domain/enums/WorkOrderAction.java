package com.ye.decision.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

public enum WorkOrderAction {
    CREATE("CREATE", "创建"),
    ASSIGN("ASSIGN", "指派"),
    UPDATE_STATUS("UPDATE_STATUS", "更新状态"),
    ADD_NOTE("ADD_NOTE", "添加备注"),
    CLOSE("CLOSE", "关闭");

    @EnumValue
    @JsonValue
    private final String code;
    private final String label;

    WorkOrderAction(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }
}
