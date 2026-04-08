package com.ye.decision.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

public enum WorkOrderType {
    ORDER("ORDER", "订单问题"),
    LOGISTICS("LOGISTICS", "物流问题"),
    ACCOUNT("ACCOUNT", "账户问题"),
    TECH_FAULT("TECH_FAULT", "技术故障"),
    CONSULTATION("CONSULTATION", "咨询类"),
    OTHER("OTHER", "其他");

    @EnumValue
    @JsonValue
    private final String code;
    private final String label;

    WorkOrderType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }
}
