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
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfig {

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
     * DataAgent 同时使用本地工具与 MCP 远端工具。MCP 工具由 {@link McpToolRegistry}
     * 异步刷新（默认初始 1s 延迟），@Bean 装配时刷新可能尚未触发；这里同步调用
     * {@code refreshNow()} 以保证 byNames 时工具已到位。MCP server 必须在 decision-app
     * 启动前已就绪（详见 CLAUDE.md）。
     */
    @Bean
    public DataAgent dataAgent(ChatModel chatModel,
                               ToolCatalog catalog,
                               McpToolRegistry mcpToolRegistry,
                               AgentHookFactory hookFactory,
                               AgentProperties properties) {
        mcpToolRegistry.refreshNow();
        List<ToolCallback> tools = catalog.byNames(
            "queryRedisTool", "queryMysqlTool",
            "listTables", "describeTable", "queryData", "executeSql"
        );
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
