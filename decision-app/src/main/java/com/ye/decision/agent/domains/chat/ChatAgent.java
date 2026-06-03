package com.ye.decision.agent.domains.chat;

import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import com.ye.decision.agent.config.AgentProperties;
import com.ye.decision.agent.core.AbstractDomainAgent;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

public class ChatAgent extends AbstractDomainAgent {

    public static final String NAME = "chat";

    public ChatAgent(ChatModel chatModel,
                     List<? extends Hook> hooks,
                     List<? extends Interceptor> interceptors,
                     AgentProperties properties) {
        super(chatModel, List.of(), hooks, interceptors, properties);
    }

    @Override public String name() { return NAME; }
    @Override public String description() { return ChatPrompts.DESCRIPTION; }
    @Override protected String systemPrompt() { return ChatPrompts.SYSTEM; }
}
