package com.ye.decision.mcp.domain.enums;

/**
 * SQL 操作类型。
 *
 * @author ye
 */
public enum SqlOperationType {

    SELECT("SELECT"),
    INSERT("INSERT"),
    UPDATE("UPDATE"),
    DELETE("DELETE");

    private final String label;

    SqlOperationType(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}
