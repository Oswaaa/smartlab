package com.smartlab.agent.skill;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class WorkflowGenerationSkill {
    public static final String ID = "generate-workflow";

    private final Skill skill;
    private final String systemPrompt;

    public WorkflowGenerationSkill() {
        this.skill = new Skill(ID, read("agent/skills/generate-workflow.md"));
        this.systemPrompt = read("agent/prompts/system.md");
    }

    public Skill skill() {
        return skill;
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public String combinedSystemMessage() {
        return systemPrompt + "\n\n# 技能：" + skill.id() + "\n\n" + skill.body();
    }

    private String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取代理资源: " + path, exception);
        }
    }
}
