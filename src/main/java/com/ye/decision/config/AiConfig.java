package com.ye.decision.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.domain.dto.ApiCallReq;
import com.ye.decision.domain.dto.QueryMysqlReq;
import com.ye.decision.domain.dto.QueryRedisReq;
import com.ye.decision.mcp.domain.dto.DescribeTableReq;
import com.ye.decision.mcp.domain.dto.ExecuteSqlReq;
import com.ye.decision.mcp.domain.dto.ListTablesReq;
import com.ye.decision.mcp.domain.dto.QueryDataReq;
import com.ye.decision.mcp.tool.DescribeTableTool;
import com.ye.decision.mcp.tool.ExecuteSqlTool;
import com.ye.decision.mcp.tool.ListTablesTool;
import com.ye.decision.mcp.tool.QueryDataTool;
import com.ye.decision.mq.ChatMemoryPublisher;
import com.ye.decision.rag.domain.dto.KnowledgeSearchReq;
import com.ye.decision.tool.KnowledgeSearchTool;
import com.ye.decision.tool.CallExternalApiTool;
import com.ye.decision.tool.QueryMysqlTool;
import com.ye.decision.tool.QueryRedisTool;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @author ye
 */
@Configuration
public class AiConfig {

    @Value("${decision.agent.memory-window-size:10}")
    private int memoryWindowSize;

    @Bean
    public ChatMemory chatMemory(RedissonClient redissonClient,
                                 ObjectMapper objectMapper,
                                 ChatMemoryPublisher publisher) {
        return MessageWindowChatMemory.builder()
            .chatMemoryRepository(new RedissonChatMemoryRepository(redissonClient, objectMapper, publisher))
            .maxMessages(memoryWindowSize)
            .build();
    }

    // MCP 工具通过 required=false 注入：
    // - 当 decision.mcp.enabled=false 时，McpConfig 不生效，这些字段为 null
    // - ExecuteSqlTool 额外受 write-enabled 控制，关闭写操作时也为 null
    @Autowired(required = false)
    private ListTablesTool listTablesTool;

    @Autowired(required = false)
    private DescribeTableTool describeTableTool;

    @Autowired(required = false)
    private QueryDataTool queryDataTool;

    @Autowired(required = false)
    private ExecuteSqlTool executeSqlTool;

    @Bean
    public List<ToolCallback> toolCallbacks(QueryMysqlTool queryMysqlTool,
                                            QueryRedisTool queryRedisTool,
                                            CallExternalApiTool callExternalApiTool,
                                            KnowledgeSearchTool knowledgeSearchTool) {
        List<ToolCallback> callbacks = new ArrayList<>(List.of(
            FunctionToolCallback.builder("queryMysqlTool", queryMysqlTool)
                .description("查询结构化业务数据，如订单、用户信息、交易记录、统计报表。适用于精确条件查询场景。")
                .inputType(QueryMysqlReq.class)
                .build(),
            FunctionToolCallback.builder("queryRedisTool", queryRedisTool)
                .description("查询 Redis 中的缓存数据、热点数据、实时计数器、会话信息或排行榜。适用于低延迟、高频访问场景。")
                .inputType(QueryRedisReq.class)
                .build(),
            FunctionToolCallback.builder("callExternalApiTool", callExternalApiTool)
                .description("调用外部第三方服务，包括天气查询（weather）、物流追踪（logistics）、汇率查询（exchange-rate）。")
                .inputType(ApiCallReq.class)
                .build(),
            FunctionToolCallback.builder("knowledgeSearchTool", knowledgeSearchTool)
                .description("在企业知识库中搜索相关文档。适用于查询产品文档、操作手册、FAQ、政策规范、技术文档等非结构化知识。需要指定知识库编码(kbCode)和查询内容(query)。")
                .inputType(KnowledgeSearchReq.class)
                .build()
        ));

        // MCP 数据库操作工具（条件注册）
        if (listTablesTool != null) {
            callbacks.add(FunctionToolCallback.builder("mcpListTables", listTablesTool)
                .description("列出数据库中可查询的��。返回表名和注释。用于了解数据库结构。")
                .inputType(ListTablesReq.class)
                .build());
        }
        if (describeTableTool != null) {
            callbacks.add(FunctionToolCallback.builder("mcpDescribeTable", describeTableTool)
                .description("查看指定表的结构，包括列名、数据类型、索引信息。用于了解表结构后编写SQL。输入参数：tableName（表名）。")
                .inputType(DescribeTableReq.class)
                .build());
        }
        if (queryDataTool != null) {
            callbacks.add(FunctionToolCallback.builder("mcpQueryData", queryDataTool)
                .description("在本地数据库上执行只读SELECT查询。支持复杂查询、JOIN、聚合。返回查询结果JSON。需先用mcpListTables和mcpDescribeTable了解表结构。输入参数：sql（SQL语句）、maxRows（最大行数，可选）。")
                .inputType(QueryDataReq.class)
                .build());
        }
        if (executeSqlTool != null) {
            callbacks.add(FunctionToolCallback.builder("mcpExecuteSql", executeSqlTool)
                .description("在本地数据库执行INSERT/UPDATE/DELETE操作。仅在写操作启用时可用。需谨慎使用。输入参数：sql（SQL语句）。")
                .inputType(ExecuteSqlReq.class)
                .build());
        }

        return callbacks;
    }

    @Bean
    public String systemPrompt() throws IOException {
        ClassPathResource resource = new ClassPathResource("prompt/system-prompt.md");
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }
}
