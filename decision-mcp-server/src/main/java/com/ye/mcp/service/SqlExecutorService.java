package com.ye.mcp.service;

import com.ye.mcp.config.McpProperties;
import com.ye.mcp.exception.McpErrorCode;
import com.ye.mcp.exception.McpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * MCP SQL 执行服务。
 * <p>
 * 基于 JDBC 直接执行动态 SQL，复用项目的 HikariCP 连接池。
 * 提供语句级别超时、最大行数限制、只读连接等安全控制。
 *
 * @author ye
 */
@Service
public class SqlExecutorService {

    private static final Logger log = LoggerFactory.getLogger(SqlExecutorService.class);

    private final DataSource dataSource;
    private final McpProperties mcpProperties;

    public SqlExecutorService(DataSource dataSource, McpProperties mcpProperties) {
        this.dataSource = dataSource;
        this.mcpProperties = mcpProperties;
    }

    /**
     * 执行 SELECT 查询。
     *
     * @param sql     已通过安全校验的 SQL（含 LIMIT）
     * @param maxRows 最大返回行数
     * @param timeout 超时秒数
     * @return 查询结果列表，每行为 Map&lt;列名, 值&gt;
     */
    public List<Map<String, Object>> executeQuery(String sql, int maxRows, int timeout) {
        try (Connection conn = dataSource.getConnection()) {
            // 标记连接为只读，部分数据库/驱动会据此优化（如 MySQL 走从库）
            conn.setReadOnly(true);
            try (java.sql.Statement stmt = conn.createStatement()) {
                // setQueryTimeout: 超时后驱动会中断查询，防止慢 SQL 占用连接池
                stmt.setQueryTimeout(timeout > 0 ? timeout : mcpProperties.getQueryTimeoutSeconds());
                // setMaxRows: JDBC 驱动层面限制，与 SQL 的 LIMIT 双重保护
                stmt.setMaxRows(maxRows > 0 ? Math.min(maxRows, mcpProperties.getMaxRowLimit())
                    : mcpProperties.getDefaultRowLimit());

                try (ResultSet rs = stmt.executeQuery(sql)) {
                    return resultSetToList(rs);
                }
            } finally {
                // 归还连接前恢复读写模式，避免影响连接池中其他使用者
                conn.setReadOnly(false);
            }
        } catch (SQLTimeoutException e) {
            log.error("SQL query timeout: {}", sql, e);
            throw new McpException(McpErrorCode.SQL_TIMEOUT, e);
        } catch (McpException e) {
            throw e;
        } catch (SQLException e) {
            log.error("SQL query failed: {}", sql, e);
            throw new McpException(McpErrorCode.SQL_EXECUTION_FAILED, e.getMessage());
        }
    }

    /**
     * 执行 INSERT/UPDATE/DELETE。
     *
     * @param sql     已通过安全校验的 SQL
     * @param timeout 超时秒数
     * @return 影响行数
     */
    public int executeUpdate(String sql, int timeout) {
        try (Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(timeout > 0 ? timeout : mcpProperties.getQueryTimeoutSeconds());
            return stmt.executeUpdate(sql);
        } catch (SQLTimeoutException e) {
            log.error("SQL update timeout: {}", sql, e);
            throw new McpException(McpErrorCode.SQL_TIMEOUT, e);
        } catch (SQLException e) {
            log.error("SQL update failed: {}", sql, e);
            throw new McpException(McpErrorCode.SQL_EXECUTION_FAILED, e.getMessage());
        }
    }

    /**
     * 列出当前数据库中的所有用户表。
     */
    public List<Map<String, String>> listTables() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            List<Map<String, String>> tables = new ArrayList<>();

            // 第4参数 {"TABLE"} 过滤只返回用户表，排除视图、系统表等
            try (ResultSet rs = meta.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    Map<String, String> table = new LinkedHashMap<>();
                    table.put("tableName", rs.getString("TABLE_NAME"));
                    table.put("comment", rs.getString("REMARKS"));
                    tables.add(table);
                }
            }
            return tables;
        } catch (SQLException e) {
            log.error("Failed to list tables", e);
            throw new McpException(McpErrorCode.SQL_EXECUTION_FAILED, e.getMessage());
        }
    }

    /**
     * 获取指定表的列信息和索引信息。
     */
    public Map<String, Object> describeTable(String tableName) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();

            // 验证表存在
            try (ResultSet tableRs = meta.getTables(catalog, null, tableName, new String[]{"TABLE"})) {
                if (!tableRs.next()) {
                    throw new McpException(McpErrorCode.TABLE_NOT_FOUND, tableName);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tableName", tableName);

            // 列信息
            List<Map<String, String>> columns = new ArrayList<>();
            try (ResultSet rs = meta.getColumns(catalog, null, tableName, "%")) {
                while (rs.next()) {
                    Map<String, String> col = new LinkedHashMap<>();
                    col.put("name", rs.getString("COLUMN_NAME"));
                    col.put("type", rs.getString("TYPE_NAME"));
                    col.put("size", rs.getString("COLUMN_SIZE"));
                    col.put("nullable", rs.getString("IS_NULLABLE"));
                    col.put("default", rs.getString("COLUMN_DEF"));
                    col.put("comment", rs.getString("REMARKS"));
                    columns.add(col);
                }
            }
            result.put("columns", columns);

            // 索引信息：getIndexInfo 第4参数 unique=false 表示返回所有索引，第5参数 approximate=false 要求精确结果
            List<Map<String, String>> indexes = new ArrayList<>();
            try (ResultSet rs = meta.getIndexInfo(catalog, null, tableName, false, false)) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    if (indexName == null) continue; // 统计信息行无索引名，跳过
                    Map<String, String> idx = new LinkedHashMap<>();
                    idx.put("indexName", indexName);
                    idx.put("column", rs.getString("COLUMN_NAME"));
                    idx.put("unique", String.valueOf(!rs.getBoolean("NON_UNIQUE")));
                    indexes.add(idx);
                }
            }
            result.put("indexes", indexes);

            return result;
        } catch (McpException e) {
            throw e;
        } catch (SQLException e) {
            log.error("Failed to describe table: {}", tableName, e);
            throw new McpException(McpErrorCode.SQL_EXECUTION_FAILED, e.getMessage());
        }
    }

    /**
     * 将 ResultSet 转为 List<Map> 结构，方便 JSON 序列化返回给 Agent。
     * 使用 getColumnLabel 而非 getColumnName，以支持 SQL 中的列别名（AS）。
     * 使用 LinkedHashMap 保持列的原始顺序。
     */
    private List<Map<String, Object>> resultSetToList(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        List<Map<String, Object>> rows = new ArrayList<>();

        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }
}
