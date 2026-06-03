package com.ye.decision.agent.skills;

import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.classpath.ClasspathSkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import com.ye.decision.agent.config.AgentProperties;

public class AgentSkillRegistryFactory {

    private final AgentProperties properties;
    private final SkillRegistry registry;

    public AgentSkillRegistryFactory(AgentProperties properties) {
        this.properties = properties;
        this.registry = buildRegistry(properties);
    }

    public SkillRegistry forSkill(String skillName) {
        if (properties.getSkills().isFailFast() && !registry.contains(skillName)) {
            throw new IllegalStateException("Agent skill not found: " + skillName
                + " (available: " + registry.listAll().stream().map(s -> s.getName()).toList() + ")");
        }
        return new SingleSkillRegistry(registry, skillName);
    }

    public SkillRegistry allSkills() {
        return registry;
    }

    private SkillRegistry buildRegistry(AgentProperties properties) {
        AgentProperties.Skills skills = properties.getSkills();
        if ("filesystem".equalsIgnoreCase(skills.getMode())) {
            return FileSystemSkillRegistry.builder()
                .projectSkillsDirectory(skills.getBasePath())
                .autoLoad(true)
                .build();
        }
        return ClasspathSkillRegistry.builder()
            .classpathPath(skills.getClasspathPath())
            .basePath(skills.getBasePath())
            .autoLoad(true)
            .build();
    }
}
