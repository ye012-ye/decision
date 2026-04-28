package com.ye.decision.agent.router;

import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.ye.decision.agent.core.AbstractDomainAgent;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用所有 {@link AbstractDomainAgent} 子 Agent 构造一个 {@link LlmRoutingAgent}。
 * 路由系统提示由各子 agent 的 name + description 自动拼装，新增域时零修改本工厂。
 */
public final class RouterAgentFactory {

    private RouterAgentFactory() {}

    public static LlmRoutingAgent build(ChatModel chatModel,
                                        List<AbstractDomainAgent> domains,
                                        String fallbackAgentName) {
        if (domains == null || domains.isEmpty()) {
            throw new IllegalArgumentException("Router needs at least one sub-agent");
        }
        String routingPrompt = buildRoutingPrompt(domains);
        try {
            return LlmRoutingAgent.builder()
                .name("root-router")
                .description("根路由器：根据用户问题选择最合适的域专家 Agent。")
                .model(chatModel)
                .systemPrompt(routingPrompt)
                .fallbackAgent(fallbackAgentName)
                .subAgents(domains.stream()
                    .map(d -> (com.alibaba.cloud.ai.graph.agent.Agent) d.getReactAgent())
                    .collect(Collectors.toList()))
                .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build root LlmRoutingAgent", e);
        }
    }

    private static String buildRoutingPrompt(List<AbstractDomainAgent> domains) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是分流器。读取用户最新消息（结合历史），从下列子 Agent 中选最合适的一个：\n");
        for (AbstractDomainAgent d : domains) {
            sb.append("  - ").append(d.name()).append(" : ").append(d.description()).append('\n');
        }
        sb.append("""

            选择规则：
              1. 优先精确匹配领域；
              2. 多个领域均可时，选用户问题里最具体的那个；
              3. 都不沾边时，选 chat 兜底；
              4. 只输出选中的 agent 名（小写），不要任何额外解释。
            """);
        return sb.toString();
    }
}
