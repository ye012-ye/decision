package com.ye.decision.agent.skills;

import com.alibaba.cloud.ai.graph.skills.SkillMetadata;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

final class SingleSkillRegistry implements SkillRegistry {

    private final SkillRegistry delegate;
    private final String skillName;

    SingleSkillRegistry(SkillRegistry delegate, String skillName) {
        this.delegate = delegate;
        this.skillName = skillName;
    }

    @Override
    public Optional<SkillMetadata> get(String name) {
        if (!skillName.equals(name)) {
            return Optional.empty();
        }
        return delegate.get(name);
    }

    @Override
    public List<SkillMetadata> listAll() {
        return get(skillName).stream().toList();
    }

    @Override
    public boolean contains(String name) {
        return skillName.equals(name) && delegate.contains(name);
    }

    @Override
    public int size() {
        return contains(skillName) ? 1 : 0;
    }

    @Override
    public void reload() {
        delegate.reload();
    }

    @Override
    public String readSkillContent(String name) throws IOException {
        if (!skillName.equals(name)) {
            throw new IllegalStateException("Skill not available for this agent: " + name);
        }
        return delegate.readSkillContent(name);
    }

    @Override
    public String getSkillLoadInstructions() {
        return "Only skill `" + skillName + "` is available to this agent. Use `read_skill` with that exact id.";
    }

    @Override
    public String getRegistryType() {
        return delegate.getRegistryType() + "[single]";
    }

    @Override
    public SystemPromptTemplate getSystemPromptTemplate() {
        return delegate.getSystemPromptTemplate();
    }
}
