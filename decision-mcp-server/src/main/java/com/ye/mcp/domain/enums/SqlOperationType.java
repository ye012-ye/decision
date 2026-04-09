package com.ye.mcp.domain.enums;

/**
 * @author ye
 */

public enum SqlOperationType {
    /**
     * SQL 操作类型
     */
    SELECT("SELECT"), INSERT("INSERT"), UPDATE("UPDATE"), DELETE("DELETE");
    private final String label;
    SqlOperationType(String label) { this.label = label; }
    public String getLabel() { return label; }
}
