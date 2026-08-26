package com.smartlab.agent.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowGenerationSkillTest {
    @Test
    void combinedPromptTeachesAuthoringShape() {
        String prompt = new WorkflowGenerationSkill().combinedSystemMessage();
        assertTrue(prompt.contains("FUNC_NODE"));
        assertTrue(prompt.contains("functionType"));
        assertTrue(prompt.contains("capabilityParameters"));
        assertTrue(prompt.contains("Interface_state_out"));
        assertTrue(prompt.contains("source"));
        assertTrue(prompt.contains("fromNodeId"));
        assertTrue(prompt.contains("issues 摘要"));
    }
}
