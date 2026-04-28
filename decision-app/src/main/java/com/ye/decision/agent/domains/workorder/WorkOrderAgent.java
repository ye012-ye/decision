package com.ye.decision.agent.domains.workorder;

import com.ye.decision.agent.core.AbstractDomainAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public class WorkOrderAgent extends AbstractDomainAgent {

    public static final String NAME = "workorder";

    public WorkOrderAgent(ChatModel chatModel, List<ToolCallback> tools) {
        super(chatModel, tools);
    }

    @Override public String name() { return NAME; }
    @Override public String description() { return WorkOrderPrompts.DESCRIPTION; }
    @Override protected String systemPrompt() { return WorkOrderPrompts.SYSTEM; }
}
