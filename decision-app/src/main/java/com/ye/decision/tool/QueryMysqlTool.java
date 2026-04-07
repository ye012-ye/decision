package com.ye.decision.tool;

import com.ye.decision.domain.dto.QueryMysqlReq;
import com.ye.decision.feign.DownstreamClient;

import java.util.Map;
import java.util.function.Function;

/**
 * Mysql 查询工具。
 * @author ye
 */
public class QueryMysqlTool implements Function<QueryMysqlReq, String> {

    private final Map<String, DownstreamClient> clients;

    public QueryMysqlTool(Map<String, DownstreamClient> clients) {
        this.clients = clients;
    }

    @Override
    public String apply(QueryMysqlReq req) {
        DownstreamClient client = clients.get(req.target());
        if (client == null) {
            return errorJson("unknown_target", "不支持的下游服务: " + req.target());
        }
        try {
            return client.query(req.query());
        } catch (Exception e) {
            return errorJson("feign_error", e.getMessage());
        }
    }

    private String errorJson(String code, String message) {
        return "{\"error\":\"" + code + "\",\"message\":\"" + message + "\",\"tool\":\"queryMysqlTool\"}";
    }
}
