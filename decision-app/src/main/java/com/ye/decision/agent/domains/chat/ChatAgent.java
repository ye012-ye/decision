package com.ye.decision.agent.domains.chat;

import com.ye.decision.agent.core.AbstractDomainAgent;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

public class ChatAgent extends AbstractDomainAgent {

    public static final String NAME = "chat";

    public ChatAgent(ChatModel chatModel) {
        super(chatModel, List.of());
    }

    @Override public String name() { return NAME; }
    @Override public String description() { return ChatPrompts.DESCRIPTION; }
    @Override protected String systemPrompt() { return ChatPrompts.SYSTEM; }
}
