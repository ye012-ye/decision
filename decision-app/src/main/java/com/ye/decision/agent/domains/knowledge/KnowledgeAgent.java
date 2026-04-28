package com.ye.decision.agent.domains.knowledge;

import com.ye.decision.agent.core.AbstractDomainAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public class KnowledgeAgent extends AbstractDomainAgent {

    public static final String NAME = "knowledge";

    public KnowledgeAgent(ChatModel chatModel, List<ToolCallback> tools) {
        super(chatModel, tools);
    }

    @Override public String name() { return NAME; }
    @Override public String description() { return KnowledgePrompts.DESCRIPTION; }
    @Override protected String systemPrompt() { return KnowledgePrompts.SYSTEM; }
}
