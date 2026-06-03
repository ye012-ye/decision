package com.ye.decision.tool;

import com.ye.decision.feign.DownstreamClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Mysql 查询工具。
 * @author ye
 */
@Component
public class QueryMysqlTool {

    private final Map<String, DownstreamClient> clients;

    public QueryMysqlTool(Map<String, DownstreamClient> clients) {
        this.clients = clients;
    }

    @Tool(name = "queryMysqlTool", description = "查询结构化业务数据，如订单、用户信息、交易记录、统计报表。适用于精确条件查询场景。")
    public String queryMysql(
            @ToolParam(description = "目标下游服务名: order-service / user-service") String target,
            @ToolParam(description = "查询内容") String query) {
        DownstreamClient client = clients.get(target);
        if (client == null) {
            return errorJson("unknown_target", "不支持的下游服务: " + target);
        }
        try {
            return client.query(query);
        } catch (Exception e) {
            return errorJson("feign_error", e.getMessage());
        }
    }

    private String errorJson(String code, String message) {
        return "{\"error\":\"" + code + "\",\"message\":\"" + message + "\",\"tool\":\"queryMysqlTool\"}";
    }
}
