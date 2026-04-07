package com.ye.mcp.domain.enums;

public enum AuditStatus {
    SUCCESS("SUCCESS"), DENIED("DENIED"), ERROR("ERROR");
    private final String label;
    AuditStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}
