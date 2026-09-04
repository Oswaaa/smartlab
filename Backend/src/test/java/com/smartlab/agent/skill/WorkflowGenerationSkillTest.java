package com.smartlab.agent.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
        assertTrue(prompt.contains("BRANCH"));
        assertTrue(prompt.contains("至少 2 个 OUT"));
        assertTrue(prompt.contains("repair"));
        assertTrue(prompt.contains("bindingTriggers"));
        assertTrue(prompt.contains("禁止向人提问"));
        assertTrue(prompt.contains("必须经数据端口和数据连线"));
        assertTrue(prompt.contains("已经拿到列表后不要再整表列出"));
        assertTrue(prompt.contains("list_workflow_catalog"));
        assertTrue(prompt.contains("SUBFLOW_NODE"));
        assertTrue(prompt.contains("工作流模型结构说明"));
        assertTrue(prompt.contains("5.3 子流程节点"));
        assertTrue(prompt.contains("6.6 触发器"));
        assertTrue(prompt.contains("simulate_workflow"));
        assertTrue(prompt.contains("不保存草稿"));
        assertFalse(prompt.contains("保存一份草稿"));
        assertTrue(prompt.contains("payload.stateName"));
        assertTrue(prompt.contains("taskLifecycleState"));
        assertTrue(prompt.contains("| 种类 | 业务配置 | 节点变量 `internalVariables` | 数据端口 `ports` | 节点 JSON 中的控制接口 `interfaces` |"));
        assertTrue(prompt.contains("| START | 无 | 不写 | `[]` | 不写 |"));
        assertTrue(prompt.contains("禁止冗余/占位节点"));
        assertTrue(prompt.contains("特别注意“读取/检测/检查”类意图的识别"));
        assertTrue(prompt.contains("禁止占位节点与数据读取复用规约"));
    }
}
