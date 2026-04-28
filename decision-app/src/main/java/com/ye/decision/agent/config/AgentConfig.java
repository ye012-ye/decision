package com.ye.decision.agent.config;

import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.ye.decision.agent.AlibabaAgent;
import com.ye.decision.agent.core.AbstractDomainAgent;
import com.ye.decision.agent.core.Agent;
import com.ye.decision.agent.domains.chat.ChatAgent;
import com.ye.decision.agent.domains.data.DataAgent;
import com.ye.decision.agent.domains.external.ExternalApiAgent;
import com.ye.decision.agent.domains.knowledge.KnowledgeAgent;
import com.ye.decision.agent.domains.workorder.WorkOrderAgent;
import com.ye.decision.agent.router.RouterAgentFactory;
import com.ye.decision.service.McpToolRegistry;
import com.ye.decision.service.ToolCatalog;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AgentConfig {

    @Value("${decision.agent.router.fallback-agent:chat}")
    private String fallbackAgent;

    @Bean
    public KnowledgeAgent knowledgeAgent(ChatModel chatModel, ToolCatalog catalog) {
        return new KnowledgeAgent(chatModel, catalog.byNames("knowledgeSearchTool"));
    }

    /**
     * DataAgent 同时使用本地工具与 MCP 远端工具。MCP 工具由 {@link McpToolRegistry}
     * 异步刷新（默认初始 1s 延迟），@Bean 装配时刷新可能尚未触发；这里同步调用
     * {@code refreshNow()} 以保证 byNames 时工具已到位。MCP server 必须在 decision-app
     * 启动前已就绪（详见 CLAUDE.md）。
     */
    @Bean
    public DataAgent dataAgent(ChatModel chatModel, ToolCatalog catalog, McpToolRegistry mcpToolRegistry) {
        mcpToolRegistry.refreshNow();
        return new DataAgent(chatModel, catalog.byNames(
            "queryRedisTool", "queryMysqlTool",
            "listTables", "describeTable", "queryData", "executeSql"
        ));
    }

    @Bean
    public WorkOrderAgent workOrderAgent(ChatModel chatModel, ToolCatalog catalog) {
        return new WorkOrderAgent(chatModel, catalog.byNames("workOrderTool"));
    }

    @Bean
    public ExternalApiAgent externalApiAgent(ChatModel chatModel, ToolCatalog catalog) {
        return new ExternalApiAgent(chatModel, catalog.byNames("callExternalApiTool"));
    }

    @Bean
    public ChatAgent chatAgent(ChatModel chatModel) {
        return new ChatAgent(chatModel);
    }

    @Bean
    public LlmRoutingAgent rootRouter(ChatModel chatModel, List<AbstractDomainAgent> domains) {
        return RouterAgentFactory.build(chatModel, domains, fallbackAgent);
    }

    @Bean
    public Agent agent(LlmRoutingAgent rootRouter, ChatMemory chatMemory) {
        return new AlibabaAgent(rootRouter, chatMemory);
    }
}
