package com.ye.decision.agent.domains.knowledge;

import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import com.ye.decision.agent.config.AgentProperties;
import com.ye.decision.agent.core.AbstractDomainAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public class KnowledgeAgent extends AbstractDomainAgent {

    public static final String NAME = "knowledge";

    public KnowledgeAgent(ChatModel chatModel,
                          List<ToolCallback> tools,
                          List<? extends Hook> hooks,
                          List<? extends Interceptor> interceptors,
                          AgentProperties properties) {
        super(chatModel, tools, hooks, interceptors, properties);
    }

    @Override public String name() { return NAME; }
    @Override public String description() { return KnowledgePrompts.DESCRIPTION; }
    @Override protected String systemPrompt() { return KnowledgePrompts.SYSTEM; }
}
