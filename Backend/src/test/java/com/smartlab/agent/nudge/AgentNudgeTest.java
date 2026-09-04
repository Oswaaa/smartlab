package com.smartlab.agent.nudge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNudgeTest {
    private final AgentConceptDictionary dictionary = new AgentConceptDictionary();

    @Test
    void beforeCatalogsAsksToListDevicesAndWorkflows() {
        String nudge = AgentNudge.build(false, false, null, null, "我先规划流程。", dictionary);
        assertTrue(nudge.contains("list_device_catalog"));
        assertTrue(nudge.contains("list_workflow_catalog"));
        assertFalse(nudge.contains("不要再整表列出"));
    }

    @Test
    void afterDeviceCatalogStillAsksForWorkflowCatalog() {
        String nudge = AgentNudge.build(true, false, null, null, "设备已经列完。", dictionary);
        assertTrue(nudge.contains("list_workflow_catalog"));
        assertFalse(nudge.contains("list_device_catalog"));
        assertFalse(nudge.contains("不要再整表列出"));
    }

    @Test
    void afterCatalogsTellsModelToWriteDraftNotRelist() {
        String nudge = AgentNudge.build(true, true, null, null, "温度从哪来？请确认。", dictionary);
        assertTrue(nudge.contains("不要再整表列出"));
        assertTrue(nudge.contains("validate_workflow"));
        assertTrue(nudge.contains("get_device_model"));
        assertTrue(nudge.contains("【数据端口】"));
        assertFalse(nudge.contains("先调用 list_device_catalog"));
    }

    @Test
    void afterCleanValidateAsksToSimulate() {
        String nudge = AgentNudge.build(true, true, true, null, "校验通过。", dictionary);
        assertTrue(nudge.contains("simulate_workflow"));
        assertFalse(nudge.contains("立即调用 save_draft"));
    }

    @Test
    void afterFailedWalkTellsModelToFixFromValidate() {
        String nudge = AgentNudge.build(true, true, true, false, "走图失败。", dictionary);
        assertTrue(nudge.contains("simulate_workflow"));
        assertTrue(nudge.contains("第 5 步"));
        assertTrue(nudge.contains("SIM_NO_ADAPTER_EVENT"));
    }

    @Test
    void afterWalkableQuestionTellsModelToFixThenSave() {
        String nudge = AgentNudge.build(true, true, true, true, "走图通过。请确认端口，我先不保存。", dictionary);
        assertTrue(nudge.contains("改稿"));
        assertTrue(nudge.contains("save_draft"));
        assertTrue(nudge.contains("【数据端口】") || nudge.contains("【含糊描述】"));
        assertFalse(nudge.contains("先调用 list_device_catalog"));
    }

    @Test
    void afterWalkableWithoutQuestionAsksToSave() {
        String nudge = AgentNudge.build(true, true, true, true, "稿件已覆盖需求。", dictionary);
        assertTrue(nudge.contains("立即调用 save_draft"));
        assertFalse(nudge.contains("先调用 list_device_catalog"));
    }
}
