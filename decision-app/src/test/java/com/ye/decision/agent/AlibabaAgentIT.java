package com.ye.decision.agent;

import com.ye.decision.agent.core.Agent;
import com.ye.decision.agent.core.AgentContext;
import com.ye.decision.agent.core.AgentEvent;
import com.ye.decision.agent.core.AgentEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成测试：验证路由 + 5 个域 + 跨域 ChatMemory 共享。
 *
 * <p>需要本地外部依赖（Nacos/MySQL/Redis/Milvus/RabbitMQ/decision-mcp-server），
 * 以及 {@code DASHSCOPE_API_KEY} 环境变量。CI 默认跳过本测试类。</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class AlibabaAgentIT {

    @Autowired
    private Agent agent;

    private List<AgentEvent> run(String session, String message) {
        return agent.chat(new AgentContext(session, message)).collectList().block();
    }

    private void assertRoutedTo(List<AgentEvent> events, String expectedAgent) {
        assertThat(events)
            .filteredOn(e -> e.type() == AgentEventType.ROUTE)
            .extracting(AgentEvent::payload)
            .contains(expectedAgent);
    }

    private void assertHasAnswer(List<AgentEvent> events) {
        assertThat(events)
            .filteredOn(e -> e.type() == AgentEventType.ANSWER)
            .isNotEmpty();
    }

    @Test
    void knowledgeDomain() {
        var events = run(UUID.randomUUID().toString(),
            "请在知识库里查一下我们公司的退换货政策。");
        assertRoutedTo(events, "knowledge");
        assertHasAnswer(events);
    }

    @Test
    void dataDomain() {
        var events = run(UUID.randomUUID().toString(),
            "查询数据库里订单表有多少条记录。");
        assertRoutedTo(events, "data");
        assertHasAnswer(events);
    }

    @Test
    void workOrderDomain() {
        var events = run(UUID.randomUUID().toString(),
            "帮我提一个工单：customerId=C001，物流问题，订单 SO123 还没收到。");
        assertRoutedTo(events, "workorder");
        assertHasAnswer(events);
    }

    @Test
    void externalDomain() {
        var events = run(UUID.randomUUID().toString(),
            "查一下今天北京的天气。");
        assertRoutedTo(events, "external");
        assertHasAnswer(events);
    }

    @Test
    void chatFallback() {
        var events = run(UUID.randomUUID().toString(), "你好，介绍一下你自己。");
        assertRoutedTo(events, "chat");
        assertHasAnswer(events);
    }

    @Test
    void crossDomainContext_sharedMemory() {
        String session = UUID.randomUUID().toString();
        var first = run(session, "知识库里说工单流程是什么？");
        assertRoutedTo(first, "knowledge");

        var second = run(session, "好的，那帮我按这个流程提一个工单。");
        assertRoutedTo(second, "workorder");
        assertHasAnswer(second);
    }
}
