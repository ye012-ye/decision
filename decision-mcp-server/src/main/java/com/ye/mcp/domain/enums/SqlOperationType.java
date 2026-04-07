package com.ye.mcp.domain.enums;

public enum SqlOperationType {
    SELECT("SELECT"), INSERT("INSERT"), UPDATE("UPDATE"), DELETE("DELETE");
    private final String label;
    SqlOperationType(String label) { this.label = label; }
    public String getLabel() { return label; }
}
