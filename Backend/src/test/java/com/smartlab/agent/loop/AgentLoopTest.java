package com.smartlab.agent.loop;

import com.smartlab.agent.AgentProperties;
import com.smartlab.agent.api.WorkflowGenerateResponse;
import com.smartlab.agent.catalog.DeviceCatalogAssembler;
import com.smartlab.agent.catalog.DeviceCatalogItem;
import com.smartlab.agent.llm.LlmCompletion;
import com.smartlab.agent.llm.LlmToolCall;
import com.smartlab.agent.llm.MockLlmClient;
import com.smartlab.agent.skill.WorkflowGenerationSkill;
import com.smartlab.agent.tool.GetDeviceModelTool;
import com.smartlab.agent.tool.ListDeviceCatalogTool;
import com.smartlab.agent.tool.SaveDraftTool;
import com.smartlab.agent.tool.ValidateWorkflowTool;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.dto.workflow.WorkflowModelDocument;
import com.smartlab.management.dto.workflow.WorkflowPreparationResponse;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentLoopTest {
    @Test
    void refusesSaveDraftBeforeCatalog() {
        WorkflowService workflows = mock(WorkflowService.class);
        AgentLoop loop = loop(List.of(call("save_draft", "{\"document\":{\"metadata\":{\"flowModelName\":\"x\"},\"nodes\":[]}}")),
                mock(DeviceCatalogAssembler.class), workflows);

        AgentGenerateException error = assertThrows(AgentGenerateException.class, () -> loop.generate("加热"));
        assertTrue(error.getMessage().contains("最大轮次") || error.partial().trace().stream().anyMatch(item -> item.contains("拒绝 save_draft")));
        assertTrue(error.partial().logs().stream().anyMatch(item -> "gate".equals(item.kind()) || "start".equals(item.kind())));
        verify(workflows, never()).saveDraft(any());
    }

    @Test
    void refusesSaveDraftWhenValidationStillBlocks() {
        WorkflowService workflows = mock(WorkflowService.class);
        DeviceCatalogAssembler catalog = mock(DeviceCatalogAssembler.class);
        when(catalog.list(null)).thenReturn(List.of(new DeviceCatalogItem(1L, "加热套", "热工", List.of(), List.of(), null, null)));
        when(workflows.validate(any())).thenReturn(blockedPreparation());

        AgentLoop loop = loop(List.of(
                call("list_device_catalog", "{}"),
                call("validate_workflow", "{\"document\":{\"metadata\":{\"flowModelName\":\"加热\"},\"nodes\":[]}}"),
                call("save_draft", "{\"document\":{\"metadata\":{\"flowModelName\":\"加热\"},\"nodes\":[]}}")
        ), catalog, workflows);

        assertThrows(AgentGenerateException.class, () -> loop.generate("加热"));
        verify(workflows, never()).saveDraft(any());
    }

    @Test
    void savesDraftAfterCatalogAndCleanValidate() {
        WorkflowService workflows = mock(WorkflowService.class);
        DeviceCatalogAssembler catalog = mock(DeviceCatalogAssembler.class);
        when(catalog.list(null)).thenReturn(List.of(new DeviceCatalogItem(1L, "加热套", "热工", List.of(), List.of(), null, null)));
        when(workflows.validate(any())).thenReturn(cleanPreparation());
        when(workflows.saveDraft(any())).thenReturn(savedPreparation());

        AgentLoop loop = loop(List.of(
                call("list_device_catalog", "{}"),
                call("validate_workflow", "{\"document\":{\"metadata\":{\"flowModelName\":\"加热\"},\"nodes\":[]}}"),
                call("save_draft", "{\"document\":{\"metadata\":{\"flowModelName\":\"加热\"},\"nodes\":[]}}")
        ), catalog, workflows);

        WorkflowGenerateResponse result = loop.generate("把样品加热到80度");
        assertEquals("加热", result.definition().flowModelName());
        assertEquals(9L, result.flowModelId());
        assertEquals("DRAFT", result.status());
        assertTrue(result.trace().contains("list_device_catalog"));
        assertTrue(result.trace().contains("validate_workflow"));
        assertTrue(result.trace().contains("save_draft"));
        assertTrue(result.logs().stream().anyMatch(item -> "start".equals(item.kind())));
        assertTrue(result.logs().stream().anyMatch(item -> "llm_call".equals(item.kind())));
        assertTrue(result.logs().stream().anyMatch(item -> "llm_reply".equals(item.kind())));
        assertTrue(result.logs().stream().anyMatch(item -> "tool_result".equals(item.kind()) && item.title().contains("save_draft")));
        assertTrue(result.logs().stream().anyMatch(item -> "done".equals(item.kind())));
        verify(workflows).saveDraft(any(WorkflowModelDocument.class));
    }

    @Test
    void blankPromptAndMissingLlmConfigFailFast() {
        AgentProperties properties = new AgentProperties();
        AgentLoop loop = new AgentLoop(new MockLlmClient(List.of()), List.of(), new WorkflowGenerationSkill(), properties);
        assertThrows(IllegalArgumentException.class, () -> loop.generate(" "));
        IllegalStateException missing = assertThrows(IllegalStateException.class, () -> loop.generate("加热"));
        assertTrue(missing.getMessage().contains("SMARTLAB_AGENT"));
    }

    private AgentLoop loop(List<LlmCompletion> completions, DeviceCatalogAssembler catalog, WorkflowService workflows) {
        AgentProperties properties = new AgentProperties();
        properties.setBaseUrl("http://localhost/v1");
        properties.setApiKey("test-key");
        properties.setModel("test-model");
        properties.setMaxRounds(6);
        return new AgentLoop(
                new MockLlmClient(completions),
                List.of(new ListDeviceCatalogTool(catalog), new GetDeviceModelTool(catalog),
                        new ValidateWorkflowTool(workflows), new SaveDraftTool(workflows)),
                new WorkflowGenerationSkill(),
                properties);
    }

    private LlmCompletion call(String name, String arguments) {
        return new LlmCompletion("", List.of(new LlmToolCall("c-" + name, name, arguments)));
    }

    private WorkflowPreparationResponse blockedPreparation() {
        WorkflowDetailResponse detail = new WorkflowDetailResponse();
        detail.setName("加热");
        return new WorkflowPreparationResponse(detail, List.of(
                new WorkflowIssue("WORKFLOW_DEFINITION_INVALID", "COMPILATION", "", "workflow", "", true, "nodes必须是非空数组", "补节点")
        ), false, false);
    }

    private WorkflowPreparationResponse cleanPreparation() {
        WorkflowDetailResponse detail = new WorkflowDetailResponse();
        detail.setName("加热");
        return new WorkflowPreparationResponse(detail, List.of(), true, false);
    }

    private WorkflowPreparationResponse savedPreparation() {
        WorkflowDetailResponse detail = new WorkflowDetailResponse();
        detail.setId(9L);
        detail.setName("加热");
        detail.setVersion(1);
        detail.setStatus("DRAFT");
        detail.setNodesDef(com.smartlab.global.util.JsonNodeSupport.arrayNode());
        return new WorkflowPreparationResponse(detail, List.of(), true, false);
    }
}
