package com.ye.decision.agent.skills;

import com.ye.decision.agent.config.AgentProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentSkillRegistryFactoryTest {

    @Test
    void exposesOnlyRequestedSkillToAgent() {
        AgentSkillRegistryFactory factory = new AgentSkillRegistryFactory(new AgentProperties());

        var registry = factory.forSkill("knowledge");

        assertThat(registry.contains("knowledge")).isTrue();
        assertThat(registry.contains("data")).isFalse();
        assertThat(registry.listAll()).extracting("name").containsExactly("knowledge");
    }

    @Test
    void failsFastForMissingSkill() {
        AgentSkillRegistryFactory factory = new AgentSkillRegistryFactory(new AgentProperties());

        assertThatThrownBy(() -> factory.forSkill("missing-skill"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Agent skill not found");
    }
}
