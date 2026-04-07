package com.ye.mcp.domain.enums;

/**
 * @author ye
 */

public enum AuditStatus {
    /** 成功 */
    SUCCESS("SUCCESS"),
    /** 拒绝 */
    DENIED("DENIED"),
    /** 错误 */
    ERROR("ERROR");
    private final String label;
    AuditStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}
