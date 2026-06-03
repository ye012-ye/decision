package com.ye.decision.agent.core;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import com.ye.decision.agent.config.AgentProperties;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 域专家 Agent 模板。每个业务域继承本类，声明：
 * <ul>
 *   <li>{@link #name()} — 唯一域名（路由器据此识别）</li>
 *   <li>{@link #description()} — 域职责一句话描述（路由器据此选择）</li>
 *   <li>{@link #systemPrompt()} — 域专属系统提示</li>
 * </ul>
 * 工具集、hooks、skills 等由构造时传入；ReactAgent 由本基类按 Alibaba Agent Framework API 装配。
 * @author ye
 */
public abstract class AbstractDomainAgent {

    private final ReactAgent reactAgent;

    protected AbstractDomainAgent(ChatModel chatModel,
                                  List<ToolCallback> tools,
                                  List<? extends Hook> hooks,
                                  List<? extends Interceptor> interceptors,
                                  AgentProperties properties) {
        Objects.requireNonNull(chatModel, "chatModel");
        Objects.requireNonNull(properties, "properties");
        this.reactAgent = buildReactAgent(
            chatModel,
            tools == null ? List.of() : tools,
            hooks == null ? List.of() : hooks,
            interceptors == null ? List.of() : interceptors,
            properties
        );
    }

    /** 域名，全局唯一，路由器据此 dispatch。例如 "knowledge"/"data"/"workorder"。 */
    public abstract String name();

    /** 一句话描述域职责，路由器把所有子 agent 的 description 拼成路由提示。 */
    public abstract String description();

    /** 域专属系统提示。 */
    protected abstract String systemPrompt();

    /** 领域技能名，默认与域名一致。 */
    public String skillName() {
        return name();
    }

    /** 暴露内部 ReactAgent，用于注册到 LlmRoutingAgent 的 subAgents。 */
    public ReactAgent getReactAgent() {
        return reactAgent;
    }

    private ReactAgent buildReactAgent(ChatModel chatModel,
                                      List<ToolCallback> tools,
                                      List<? extends Hook> hooks,
                                      List<? extends Interceptor> interceptors,
                                      AgentProperties properties) {
        try {
            var builder = ReactAgent.builder()
                .name(name())
                .description(description())
                .model(chatModel)
                .instruction(systemPrompt())
                .toolContext(Map.of(
                    "agentName", name(),
                    "skillName", skillName()
                ))
                .parallelToolExecution(properties.getTools().isParallelExecution())
                .maxParallelTools(properties.getTools().getMaxParallelTools())
                .toolExecutionTimeout(properties.getTools().getExecutionTimeout())
                .wrapSyncToolsAsAsync(properties.getTools().isWrapSyncToolsAsAsync());
            if (!tools.isEmpty()) {
                builder.tools(tools);
            }
            if (!hooks.isEmpty()) {
                builder.hooks(hooks);
            }
            if (!interceptors.isEmpty()) {
                builder.interceptors(interceptors);
            }
            return builder.build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build ReactAgent for domain " + name(), e);
        }
    }
}
