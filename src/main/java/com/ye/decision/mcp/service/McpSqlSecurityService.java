package com.ye.decision.mcp.service;

import com.ye.decision.mcp.config.McpProperties;
import com.ye.decision.mcp.domain.enums.SqlOperationType;
import com.ye.decision.mcp.exception.McpErrorCode;
import com.ye.decision.mcp.exception.McpException;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MCP SQL 安全校验服务。
 * <p>
 * 校验链：长度 → JSqlParser 解析 → 多语句检测 → 操作类型 → 禁词扫描 → ��名白名单。
 * 使用 JSqlParser（通过 mybatis-plus-jsqlparser 引入）进行 SQL 解析，
 * 避免正则表达式在字符串字面值、注释、嵌套子查��等场景下的误判。
 *
 * @author ye
 */
@Service
public class McpSqlSecurityService {

    private static final Logger log = LoggerFactory.getLogger(McpSqlSecurityService.class);

    private final McpProperties mcpProperties;
    private final McpWhitelistService whitelistService;

    public McpSqlSecurityService(McpProperties mcpProperties, McpWhitelistService whitelistService) {
        this.mcpProperties = mcpProperties;
        this.whitelistService = whitelistService;
    }

    /**
     * 完整 SQL 安全校验。
     *
     * @param sql      待校验的 SQL 语句
     * @param readOnly true 时仅允许 SELECT
     * @return 解析后的操作类型
     * @throws McpException 校验不通过时抛出
     */
    public SqlOperationType validateSql(String sql, boolean readOnly) {
        // 1. 长度检查
        checkLength(sql);

        // 2. JSqlParser 解析
        Statement statement = parseSql(sql);

        // 3. 多语句检测
        checkMultiStatement(sql);

        // 4. 操作类型判定
        SqlOperationType opType = resolveOperationType(statement);
        if (readOnly && opType != SqlOperationType.SELECT) {
            throw new McpException(McpErrorCode.SQL_NOT_READONLY);
        }

        // 5. 禁词扫描
        checkForbiddenKeywords(sql);

        // 6. 表名白名单校验
        checkTableAccess(statement);

        return opType;
    }

    /**
     * 为 SELECT 语句强制行数限制。
     * 在原 SQL 外层包一层 LIMIT，确保不超过最大行数。
     */
    public String enforceRowLimit(String sql, int requestedLimit) {
        int effectiveLimit = requestedLimit > 0
            ? Math.min(requestedLimit, mcpProperties.getMaxRowLimit())
            : mcpProperties.getDefaultRowLimit();
        // 包装为子查询 + LIMIT，兼容各种 SQL 写法
        return "SELECT * FROM (" + sql + ") _mcp_limited LIMIT " + effectiveLimit;
    }

    private void checkLength(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new McpException(McpErrorCode.SQL_SYNTAX_INVALID, "SQL不能为空");
        }
        if (sql.length() > mcpProperties.getMaxSqlLength()) {
            throw new McpException(McpErrorCode.SQL_TOO_LONG,
                "长度 " + sql.length() + " 超过上限 " + mcpProperties.getMaxSqlLength());
        }
    }

    private Statement parseSql(String sql) {
        try {
            return CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            log.warn("SQL parse failed: {}", sql, e);
            throw new McpException(McpErrorCode.SQL_SYNTAX_INVALID, e.getMessage());
        }
    }

    private void checkMultiStatement(String sql) {
        try {
            Statements stmts = CCJSqlParserUtil.parseStatements(sql);
            if (stmts.getStatements().size() > 1) {
                throw new McpException(McpErrorCode.MULTI_STATEMENT_DENIED);
            }
        } catch (McpException e) {
            throw e;
        } catch (JSQLParserException e) {
            // 已在 parseSql 中校验过，此处忽略
        }
    }

    private SqlOperationType resolveOperationType(Statement statement) {
        if (statement instanceof Select) return SqlOperationType.SELECT;
        if (statement instanceof Insert) return SqlOperationType.INSERT;
        if (statement instanceof Update) return SqlOperationType.UPDATE;
        if (statement instanceof Delete) return SqlOperationType.DELETE;
        // 非 DML 语句（DDL/DCL 等）一律拒绝
        throw new McpException(McpErrorCode.SQL_FORBIDDEN_KEYWORD,
            "不支持的语句类型: " + statement.getClass().getSimpleName());
    }

    private void checkForbiddenKeywords(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        for (String keyword : mcpProperties.getForbiddenKeywords()) {
            if (upper.contains(keyword.toUpperCase(Locale.ROOT))) {
                throw new McpException(McpErrorCode.SQL_FORBIDDEN_KEYWORD, keyword);
            }
        }
    }

    private void checkTableAccess(Statement statement) {
        TablesNamesFinder finder = new TablesNamesFinder();
        List<String> tables = finder.getTableList(statement);
        if (tables == null || tables.isEmpty()) {
            return;
        }

        Set<String> blacklist = whitelistService.getBlacklist();
        Set<String> whitelist = whitelistService.getEffectiveWhitelist();

        for (String table : tables) {
            String lower = table.toLowerCase(Locale.ROOT)
                .replace("`", "")
                .replace("\"", "");

            if (blacklist.contains(lower)) {
                throw new McpException(McpErrorCode.TABLE_IN_BLACKLIST, table);
            }
            if (!whitelist.isEmpty() && !whitelist.contains(lower)) {
                throw new McpException(McpErrorCode.TABLE_NOT_IN_WHITELIST, table);
            }
        }
    }
}
