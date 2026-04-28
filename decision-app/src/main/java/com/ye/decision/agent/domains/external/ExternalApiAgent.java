package com.ye.decision.agent.domains.external;

import com.ye.decision.agent.core.AbstractDomainAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public class ExternalApiAgent extends AbstractDomainAgent {

    public static final String NAME = "external";

    public ExternalApiAgent(ChatModel chatModel, List<ToolCallback> tools) {
        super(chatModel, tools);
    }

    @Override public String name() { return NAME; }
    @Override public String description() { return ExternalPrompts.DESCRIPTION; }
    @Override protected String systemPrompt() { return ExternalPrompts.SYSTEM; }
}
