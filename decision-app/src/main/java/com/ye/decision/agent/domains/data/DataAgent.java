package com.ye.decision.agent.domains.data;

import com.ye.decision.agent.core.AbstractDomainAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public class DataAgent extends AbstractDomainAgent {

    public static final String NAME = "data";

    public DataAgent(ChatModel chatModel, List<ToolCallback> tools) {
        super(chatModel, tools);
    }

    @Override public String name() { return NAME; }
    @Override public String description() { return DataPrompts.DESCRIPTION; }
    @Override protected String systemPrompt() { return DataPrompts.SYSTEM; }
}
