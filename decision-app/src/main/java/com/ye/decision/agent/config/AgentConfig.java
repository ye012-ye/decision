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
import com.ye.decision.agent.hooks.AgentHookFactory;
import com.ye.decision.agent.router.RouterAgentFactory;
import com.ye.decision.agent.skills.AgentSkillRegistryFactory;
import com.ye.decision.service.McpToolRegistry;
import com.ye.decision.service.ToolCatalog;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    @Bean
    public AgentSkillRegistryFactory agentSkillRegistryFactory(AgentProperties properties) {
        return new AgentSkillRegistryFactory(properties);
    }

    @Bean
    public AgentHookFactory agentHookFactory(AgentProperties properties,
                                             ChatMemory chatMemory,
                                             AgentSkillRegistryFactory skillRegistryFactory) {
        return new AgentHookFactory(properties, chatMemory, skillRegistryFactory);
    }

    @Bean
    public KnowledgeAgent knowledgeAgent(ChatModel chatModel,
                                         ToolCatalog catalog,
                                         AgentHookFactory hookFactory,
                                         AgentProperties properties) {
        List<ToolCallback> tools = catalog.byNames("knowledgeSearchTool");
        return new KnowledgeAgent(chatModel, tools,
            hookFactory.domainHooks(KnowledgeAgent.NAME, tools),
            hookFactory.toolInterceptors(),
            properties);
    }

    /**
     * DataAgent 同时使用本地工具与 MCP 远端工具。本地工具（queryRedisTool/queryMysqlTool）
     * 缺失则 fail-fast；MCP 工具（listTables 等）为"尽力获取"：装配前同步 {@code refreshNow()}
     * 拉取一次，若 {@link McpToolRegistry}（即 decision-mcp-server）尚未就绪则跳过，
     * decision-app 仍照常启动（数据域以降级的本地工具运行）。MCP server 就绪后重启本应用即可获得完整 DB 工具。
     */
    @Bean
    public DataAgent dataAgent(ChatModel chatModel,
                               ToolCatalog catalog,
                               McpToolRegistry mcpToolRegistry,
                               AgentHookFactory hookFactory,
                               AgentProperties properties) {
        mcpToolRegistry.refreshNow();
        List<ToolCallback> tools = new ArrayList<>(catalog.byNames("queryRedisTool", "queryMysqlTool"));
        List<ToolCallback> mcpTools = catalog.byNamesIfPresent(
            "listTables", "describeTable", "queryData", "executeSql");
        if (mcpTools.size() < 4) {
            log.warn("DataAgent: MCP 工具未完全就绪（已获取 {}/4），decision-app 仍照常启动；"
                + "请确认 decision-mcp-server(:8081) 已运行，就绪后重启本应用即可获得完整 DB 工具。",
                mcpTools.size());
        }
        tools.addAll(mcpTools);
        return new DataAgent(chatModel, tools,
            hookFactory.domainHooks(DataAgent.NAME, tools),
            hookFactory.toolInterceptors(),
            properties);
    }

    @Bean
    public WorkOrderAgent workOrderAgent(ChatModel chatModel,
                                         ToolCatalog catalog,
                                         AgentHookFactory hookFactory,
                                         AgentProperties properties) {
        List<ToolCallback> tools = catalog.byNames("workOrderTool");
        return new WorkOrderAgent(chatModel, tools,
            hookFactory.domainHooks(WorkOrderAgent.NAME, tools),
            hookFactory.toolInterceptors(),
            properties);
    }

    @Bean
    public ExternalApiAgent externalApiAgent(ChatModel chatModel,
                                             ToolCatalog catalog,
                                             AgentHookFactory hookFactory,
                                             AgentProperties properties) {
        List<ToolCallback> tools = catalog.byNames("callExternalApiTool");
        return new ExternalApiAgent(chatModel, tools,
            hookFactory.domainHooks(ExternalApiAgent.NAME, tools),
            hookFactory.toolInterceptors(),
            properties);
    }

    @Bean
    public ChatAgent chatAgent(ChatModel chatModel,
                               AgentHookFactory hookFactory,
                               AgentProperties properties) {
        List<ToolCallback> tools = List.of();
        return new ChatAgent(chatModel,
            hookFactory.domainHooks(ChatAgent.NAME, tools),
            hookFactory.toolInterceptors(),
            properties);
    }

    @Bean
    public LlmRoutingAgent rootRouter(ChatModel chatModel,
                                      List<AbstractDomainAgent> domains,
                                      AgentHookFactory hookFactory,
                                      AgentProperties properties) {
        return RouterAgentFactory.build(chatModel, domains,
            properties.getRouter().getFallbackAgent(),
            hookFactory.rootHooks());
    }

    @Bean
    public Agent agent(LlmRoutingAgent rootRouter) {
        return new AlibabaAgent(rootRouter);
    }
}
