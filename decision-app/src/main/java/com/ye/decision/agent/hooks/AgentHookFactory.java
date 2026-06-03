package com.ye.decision.agent.hooks;

import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolerror.ToolErrorInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolretry.ToolRetryInterceptor;
import com.ye.decision.agent.config.AgentProperties;
import com.ye.decision.agent.skills.AgentSkillRegistryFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

public class AgentHookFactory {

    private final AgentProperties properties;
    private final ChatMemory chatMemory;
    private final AgentSkillRegistryFactory skillRegistryFactory;

    public AgentHookFactory(AgentProperties properties,
                            ChatMemory chatMemory,
                            AgentSkillRegistryFactory skillRegistryFactory) {
        this.properties = properties;
        this.chatMemory = chatMemory;
        this.skillRegistryFactory = skillRegistryFactory;
    }

    public List<Hook> rootHooks() {
        return List.of(new ChatMemoryAgentHook(chatMemory));
    }

    public List<Hook> domainHooks(String skillName, List<ToolCallback> tools) {
        SkillsAgentHook skillsHook = SkillsAgentHook.builder()
            .skillRegistry(skillRegistryFactory.forSkill(skillName))
            .groupedTools(Map.of(skillName, List.copyOf(tools)))
            .autoReload(properties.getSkills().isAutoReload())
            .build();

        return List.of(
            skillsHook,
            ModelCallLimitHook.builder()
                .runLimit(properties.getModelCallLimit().getRunLimit())
                .exitBehavior(ModelCallLimitHook.ExitBehavior.END)
                .build(),
            ToolCallLimitHook.builder()
                .runLimit(properties.getToolCallLimit().getRunLimit())
                .exitBehavior(ToolCallLimitHook.ExitBehavior.END)
                .build()
        );
    }

    public List<Interceptor> toolInterceptors() {
        return List.of(
            ToolErrorInterceptor.builder().build(),
            ToolRetryInterceptor.builder()
                .maxRetries(properties.getTools().getMaxRetries())
                .onFailure(ToolRetryInterceptor.OnFailureBehavior.RETURN_MESSAGE)
                .build()
        );
    }
}
