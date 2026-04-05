package com.ye.decision.mcp.domain.enums;

/**
 * 审计日志状态。
 *
 * @author ye
 */
public enum AuditStatus {

    SUCCESS("SUCCESS"),
    DENIED("DENIED"),
    ERROR("ERROR");

    private final String label;

    AuditStatus(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}
