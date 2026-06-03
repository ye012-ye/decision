package com.ye.decision.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class);

    @Test
    void bindsAgentFrameworkSettings() {
        runner
            .withPropertyValues(
                "decision.agent.memory-window-size=30",
                "decision.agent.router.fallback-agent=chat",
                "decision.agent.model-call-limit.run-limit=7",
                "decision.agent.tool-call-limit.run-limit=11",
                "decision.agent.tools.parallel-execution=true",
                "decision.agent.tools.max-parallel-tools=3",
                "decision.agent.tools.execution-timeout=25s",
                "decision.agent.tools.wrap-sync-tools-as-async=true",
                "decision.agent.tools.max-retries=2",
                "decision.agent.skills.mode=classpath",
                "decision.agent.skills.classpath-path=skills",
                "decision.agent.skills.base-path=/tmp/decision-skills",
                "decision.agent.skills.auto-reload=true",
                "decision.agent.skills.fail-fast=true"
            )
            .run(context -> {
                AgentProperties props = context.getBean(AgentProperties.class);

                assertThat(props.getMemoryWindowSize()).isEqualTo(30);
                assertThat(props.getRouter().getFallbackAgent()).isEqualTo("chat");
                assertThat(props.getModelCallLimit().getRunLimit()).isEqualTo(7);
                assertThat(props.getToolCallLimit().getRunLimit()).isEqualTo(11);
                assertThat(props.getTools().isParallelExecution()).isTrue();
                assertThat(props.getTools().getMaxParallelTools()).isEqualTo(3);
                assertThat(props.getTools().getExecutionTimeout()).isEqualTo(Duration.ofSeconds(25));
                assertThat(props.getTools().isWrapSyncToolsAsAsync()).isTrue();
                assertThat(props.getTools().getMaxRetries()).isEqualTo(2);
                assertThat(props.getSkills().getMode()).isEqualTo("classpath");
                assertThat(props.getSkills().getClasspathPath()).isEqualTo("skills");
                assertThat(props.getSkills().getBasePath()).isEqualTo("/tmp/decision-skills");
                assertThat(props.getSkills().isAutoReload()).isTrue();
                assertThat(props.getSkills().isFailFast()).isTrue();
            });
    }

    @Configuration
    @EnableConfigurationProperties(AgentProperties.class)
    static class TestConfig {
    }
}
