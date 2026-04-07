package com.ye.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP 模块配置属性，绑定 {@code mcp.*} 前缀。
 *
 * @author ye
 */
@Component
@ConfigurationProperties(prefix = "mcp")
public class McpProperties {

    /** 模块总开关 */
    private boolean enabled = true;

    /** 是否启用写操作（INSERT/UPDATE/DELETE），默认关闭 */
    private boolean writeEnabled = false;

    /** SQL 查询超时（秒） */
    private int queryTimeoutSeconds = 30;

    /** 单次查询最大返回行数 */
    private int maxRowLimit = 1000;

    /** 默认返回行数（调用方未指定时） */
    private int defaultRowLimit = 100;

    /** SQL 语句最大长度（字符） */
    private int maxSqlLength = 4096;

    /** 静态表白名单（与 DB 白名单合并生效） */
    private List<String> tableWhitelist = new ArrayList<>();

    /** 表黑名单（始终禁止访问） */
    private List<String> tableBlacklist = new ArrayList<>(List.of(
        "mcp_audit_log", "mcp_table_whitelist"
    ));

    /** SQL 禁止关键词 */
    private List<String> forbiddenKeywords = new ArrayList<>(List.of(
        "TRUNCATE", "DROP", "ALTER", "CREATE", "GRANT", "REVOKE",
        "INTO OUTFILE", "LOAD_FILE", "SLEEP", "BENCHMARK"
    ));

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isWriteEnabled() { return writeEnabled; }
    public void setWriteEnabled(boolean writeEnabled) { this.writeEnabled = writeEnabled; }
    public int getQueryTimeoutSeconds() { return queryTimeoutSeconds; }
    public void setQueryTimeoutSeconds(int queryTimeoutSeconds) { this.queryTimeoutSeconds = queryTimeoutSeconds; }
    public int getMaxRowLimit() { return maxRowLimit; }
    public void setMaxRowLimit(int maxRowLimit) { this.maxRowLimit = maxRowLimit; }
    public int getDefaultRowLimit() { return defaultRowLimit; }
    public void setDefaultRowLimit(int defaultRowLimit) { this.defaultRowLimit = defaultRowLimit; }
    public int getMaxSqlLength() { return maxSqlLength; }
    public void setMaxSqlLength(int maxSqlLength) { this.maxSqlLength = maxSqlLength; }
    public List<String> getTableWhitelist() { return tableWhitelist; }
    public void setTableWhitelist(List<String> tableWhitelist) { this.tableWhitelist = tableWhitelist; }
    public List<String> getTableBlacklist() { return tableBlacklist; }
    public void setTableBlacklist(List<String> tableBlacklist) { this.tableBlacklist = tableBlacklist; }
    public List<String> getForbiddenKeywords() { return forbiddenKeywords; }
    public void setForbiddenKeywords(List<String> forbiddenKeywords) { this.forbiddenKeywords = forbiddenKeywords; }
}
