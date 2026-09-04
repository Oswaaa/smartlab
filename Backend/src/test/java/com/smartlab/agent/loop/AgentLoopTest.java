package com.smartlab.agent.loop;

import com.smartlab.agent.AgentProperties;
import com.smartlab.agent.api.WorkflowGenerateResponse;
import com.smartlab.agent.catalog.DeviceCatalogAssembler;
import com.smartlab.agent.catalog.DeviceCatalogItem;
import com.smartlab.agent.llm.LlmCompletion;
import com.smartlab.agent.llm.LlmMessage;
import com.smartlab.agent.llm.LlmToolCall;
import com.smartlab.agent.llm.MockLlmClient;
import com.smartlab.agent.nudge.AgentConceptDictionary;
import com.smartlab.agent.skill.WorkflowGenerationSkill;
import com.smartlab.agent.tool.GetDeviceModelTool;
import com.smartlab.agent.tool.ListDeviceCatalogTool;
import com.smartlab.agent.tool.ListWorkflowCatalogTool;
import com.smartlab.agent.tool.SaveDraftTool;
import com.smartlab.agent.tool.SimulateWorkflowTool;
import com.smartlab.agent.tool.ValidateWorkflowTool;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.dto.workflow.WorkflowModelDocument;
import com.smartlab.management.dto.workflow.WorkflowPreparationResponse;
import com.smartlab.management.dto.workflow.WorkflowSimulationReport;
import com.smartlab.management.service.db.workflow.WorkflowService;
import com.smartlab.management.service.db.workflow.WorkflowSimulationService;
import com.smartlab.global.util.JsonNodeSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void refusesSaveDraftBeforeWorkflowCatalog() {
        WorkflowService workflows = mock(WorkflowService.class);
        DeviceCatalogAssembler catalog = mock(DeviceCatalogAssembler.class);
        when(catalog.list(null)).thenReturn(List.of(new DeviceCatalogItem(1L, "加热套", "热工", List.of(), List.of(), null, null)));
        when(workflows.validate(any())).thenReturn(cleanPreparation());

        AgentLoop loop = loop(List.of(
                call("list_device_catalog", "{}"),
                call("validate_workflow", "{\"document\":{\"metadata\":{\"flowModelName\":\"加热\"},\"nodes\":[]}}"),
                call("save_draft", "{\"document\":{\"metadata\":{\"flowModelName\":\"加热\"},\"nodes\":[]}}")
        ), catalog, workflows);

        AgentGenerateException error = assertThrows(AgentGenerateException.class, () -> loop.generate("加热"));
        assertTrue(error.partial().logs().stream().anyMatch(item ->
                "gate".equals(item.kind()) && item.detail() != null && item.detail().contains("list_workflow_catalog")));
        verify(workflows, never()).saveDraft(any());
    }

    @Test
    void refusesSaveDraftWhenValidationStillBlocks() {
        WorkflowService workflows = mock(WorkflowService.class);
        DeviceCatalogAssembler catalog = mock(DeviceCatalogAssembler.class);
        when(catalog.list(null)).thenReturn(List.of(new DeviceCatalogItem(1L, "加热套", "热工", List.of(), List.of(), null, null)));
        when(workflows.validate(any())).thenReturn(blockedPreparation());

        AgentLoop loop = loop(List.of(
                listCatalogs(),
                call("validate_workflow", "{\"document\":{\"metadata\":{\"flowModelName\":\"加热\"},\"nodes\":[]}}"),
                call("save_draft", "{\"document\":{\"metadata\":{\"flowModelName\":\"加热\"},\"nodes\":[]}}")
        ), catalog, workflows);

        assertThrows(AgentGenerateException.class, () -> loop.generate("加热"));
        verify(workflows, never()).saveDraft(any());
    }

    @Test
    void savesDraftAfterCatalogAndCleanValidate() throws Exception {
        WorkflowService workflows = mock(WorkflowService.class);
        DeviceCatalogAssembler catalog = mock(DeviceCatalogAssembler.class);
        when(catalog.list(null)).thenReturn(List.of(new DeviceCatalogItem(1L, "加热套", "热工", List.of(), List.of(), null, null)));
        when(workflows.validate(any())).thenReturn(cleanPreparation());
        when(workflows.saveDraft(any())).thenReturn(savedPreparation());

        AgentLoop loop = loop(List.of(
                listCatalogs(),
                call("validate_workflow", "{\"document\":{\"metadata\":{\"flowModelName\":\"加热\"},\"nodes\":[]}}"),
                call("save_draft", "{\"document\":{\"metadata\":{\"flowModelName\":\"加热\"},\"nodes\":[]}}")
        ), catalog, workflows);

        WorkflowGenerateResponse result = loop.generate("把样品加热到80度");
        assertEquals("加热", result.definition().flowModelName());
        assertEquals(9L, result.flowModelId());
        assertEquals("DRAFT", result.status());
        assertTrue(result.trace().contains("list_device_catalog"));
        assertTrue(result.trace().contains("list_workflow_catalog"));
        assertTrue(result.trace().contains("validate_workflow"));
        assertTrue(result.trace().contains("save_draft"));
        assertTrue(result.logs().stream().anyMatch(item -> "start".equals(item.kind())));
        assertTrue(result.logs().stream().anyMatch(item -> "llm_call".equals(item.kind())));
        assertTrue(result.logs().stream().anyMatch(item -> "llm_reply".equals(item.kind())));
        assertTrue(result.logs().stream().anyMatch(item -> "tool_result".equals(item.kind()) && item.title().contains("save_draft")));
        assertTrue(result.logs().stream().anyMatch(item -> "done".equals(item.kind())));
        assertTrue(result.logs().stream().anyMatch(item -> "answer".equals(item.kind()) && item.detail() != null && !item.detail().isBlank()));
        assertTrue(result.summary() != null && !result.summary().isBlank());
        verify(workflows).saveDraft(any(WorkflowModelDocument.class));

        JsonNode reply = JsonNodeSupport.MAPPER.readTree(result.logs().stream()
                .filter(item -> "llm_reply".equals(item.kind()))
                .findFirst().orElseThrow().payload());
        assertEquals("assistant", reply.path("role").asText());
        assertTrue(reply.path("tool_calls").isArray());
        assertEquals("function", reply.path("tool_calls").path(0).path("type").asText());
        assertEquals("list_device_catalog", reply.path("tool_calls").path(0).path("function").path("name").asText());

        JsonNode toolCall = JsonNodeSupport.MAPPER.readTree(result.logs().stream()
                .filter(item -> "tool_call".equals(item.kind()))
                .findFirst().orElseThrow().payload());
        assertEquals("function", toolCall.path("type").asText());
        assertEquals("list_device_catalog", toolCall.path("function").path("name").asText());
        assertEquals("{}", toolCall.path("function").path("arguments").asText());
    }

    @Test
    void compactHistoryKeepsLatestValidateAndCatalogsAndDropsOldDraft() {
        List<LlmMessage> compacted = AgentLoop.compactHistory(List.of(
                LlmMessage.system("sys"),
                LlmMessage.user("加热"),
                LlmMessage.assistant("", List.of(new LlmToolCall("c1", "list_device_catalog", "{}"))),
                LlmMessage.tool("c1", "list_device_catalog", "{\"devices\":[1]}"),
                LlmMessage.assistant("", List.of(new LlmToolCall("w1", "list_workflow_catalog", "{\"marker\":\"OLD_FLOW\"}"))),
                LlmMessage.tool("w1", "list_workflow_catalog", "{\"workflows\":[]}"),
                LlmMessage.assistant("", List.of(new LlmToolCall("w2", "list_workflow_catalog", "{\"marker\":\"NEW_FLOW\"}"))),
                LlmMessage.tool("w2", "list_workflow_catalog", "{\"workflows\":[2]}"),
                LlmMessage.assistant("", List.of(new LlmToolCall("v1", "validate_workflow", "{\"marker\":\"FIRST_DRAFT_MARKER\"}"))),
                LlmMessage.tool("v1", "validate_workflow", "{\"blocking\":true}"),
                LlmMessage.assistant("", List.of(new LlmToolCall("v2", "validate_workflow", "{\"marker\":\"SECOND_DRAFT_MARKER\"}"))),
                LlmMessage.tool("v2", "validate_workflow", "{\"blocking\":false}"),
                LlmMessage.assistant("", List.of(new LlmToolCall("s1", "simulate_workflow", "{\"marker\":\"OLD_SIM\"}"))),
                LlmMessage.tool("s1", "simulate_workflow", "{\"walkable\":false}"),
                LlmMessage.assistant("", List.of(new LlmToolCall("s2", "simulate_workflow", "{\"marker\":\"NEW_SIM\"}"))),
                LlmMessage.tool("s2", "simulate_workflow", "{\"walkable\":true}")
        ));
        String blob = blob(compacted);
        assertTrue(blob.contains("list_device_catalog"));
        assertTrue(blob.contains("NEW_FLOW"));
        assertFalse(blob.contains("OLD_FLOW"));
        assertTrue(blob.contains("SECOND_DRAFT_MARKER"));
        assertFalse(blob.contains("FIRST_DRAFT_MARKER"));
        assertTrue(blob.contains("NEW_SIM"));
        assertFalse(blob.contains("OLD_SIM"));
    }

    private String blob(List<LlmMessage> messages) {
        StringBuilder text = new StringBuilder();
        for (var message : messages) {
            if (message.content() != null) text.append(message.content());
            if (message.toolCalls() == null) continue;
            for (LlmToolCall call : message.toolCalls()) {
                text.append(call.name()).append(call.argumentsJson());
            }
        }
        return text.toString();
    }

    @Test
    void blankPromptAndMissingLlmConfigFailFast() {
        AgentProperties properties = new AgentProperties();
        AgentLoop loop = new AgentLoop(new MockLlmClient(List.of()), List.of(), new WorkflowGenerationSkill(),
                new AgentConceptDictionary(), properties);
        assertThrows(IllegalArgumentException.class, () -> loop.generate(" "));
        IllegalStateException missing = assertThrows(IllegalStateException.class, () -> loop.generate("加热"));
        assertTrue(missing.getMessage().contains("SMARTLAB_AGENT"));
    }

    @Test
    void afterCatalogNudgeWritesDraftInsteadOfRelistingAndExplainsPorts() {
        WorkflowService workflows = mock(WorkflowService.class);
        DeviceCatalogAssembler catalog = mock(DeviceCatalogAssembler.class);
        when(catalog.list(null)).thenReturn(List.of(new DeviceCatalogItem(1L, "加热套", "热工", List.of(), List.of(), null, null)));
        when(workflows.validate(any())).thenReturn(cleanPreparation());
        when(workflows.saveDraft(any())).thenReturn(savedPreparation());
        MockLlmClient llm = new MockLlmClient(List.of(
                listCatalogs(),
                new LlmCompletion("温度从哪来？数据端口怎么连？请确认后再写。", List.of()),
                call("validate_workflow", "{\"document\":{\"metadata\":{\"flowModelName\":\"加热\"},\"nodes\":[]}}"),
                call("save_draft", "{\"document\":{\"metadata\":{\"flowModelName\":\"加热\"},\"nodes\":[]}}")
        ));

        WorkflowGenerateResponse result = loop(llm, catalog, workflows).generate("加热");

        String nudge = lastUser(llm.messagesAt(2));
        assertTrue(nudge.contains("已经查过") || nudge.contains("不要再整表列出"));
        assertTrue(nudge.contains("validate_workflow"));
        assertTrue(nudge.contains("数据端口") || nudge.contains("读数不会"));
        assertFalse(nudge.contains("先调用 list_device_catalog"));
        assertTrue(result.logs().stream().anyMatch(item -> "nudge".equals(item.kind()) && "解答并继续".equals(item.title())));
    }

    @Test
    void afterCleanValidateQuestionNudgeExplainsInsteadOfForcingSaveOrRelist() {
        WorkflowService workflows = mock(WorkflowService.class);
        DeviceCatalogAssembler catalog = mock(DeviceCatalogAssembler.class);
        when(catalog.list(null)).thenReturn(List.of(new DeviceCatalogItem(1L, "加热套", "热工", List.of(), List.of(), null, null)));
        when(workflows.validate(any())).thenReturn(cleanPreparation());
        when(workflows.saveDraft(any())).thenReturn(savedPreparation());
        MockLlmClient llm = new MockLlmClient(List.of(
                listCatalogs(),
                call("validate_workflow", "{\"document\":{\"metadata\":{\"flowModelName\":\"加热\"},\"nodes\":[]}}"),
                new LlmCompletion("校验通过。请确认温度从哪来，我先不保存。", List.of()),
                call("save_draft", "{\"document\":{\"metadata\":{\"flowModelName\":\"加热\"},\"nodes\":[]}}")
        ));

        loop(llm, catalog, workflows).generate("加热");

        String nudge = lastUser(llm.messagesAt(3));
        assertTrue(nudge.contains("simulate_workflow"));
        assertFalse(nudge.contains("先调用 list_device_catalog"));
    }

    @Test
    void beforeCatalogNudgeStillRequiresCatalog() {
        WorkflowService workflows = mock(WorkflowService.class);
        DeviceCatalogAssembler catalog = mock(DeviceCatalogAssembler.class);
        when(catalog.list(null)).thenReturn(List.of(new DeviceCatalogItem(1L, "加热套", "热工", List.of(), List.of(), null, null)));
        when(workflows.validate(any())).thenReturn(cleanPreparation());
        when(workflows.saveDraft(any())).thenReturn(savedPreparation());
        MockLlmClient llm = new MockLlmClient(List.of(
                new LlmCompletion("我先规划流程步骤。", List.of()),
                listCatalogs(),
                call("validate_workflow", "{\"document\":{\"metadata\":{\"flowModelName\":\"加热\"},\"nodes\":[]}}"),
                call("save_draft", "{\"document\":{\"metadata\":{\"flowModelName\":\"加热\"},\"nodes\":[]}}")
        ));

        loop(llm, catalog, workflows).generate("加热");

        String nudge = lastUser(llm.messagesAt(1));
        assertTrue(nudge.contains("list_device_catalog"));
        assertTrue(nudge.contains("list_workflow_catalog"));
        assertFalse(nudge.contains("不要再整表列出"));
    }

    private AgentLoop loop(List<LlmCompletion> completions, DeviceCatalogAssembler catalog, WorkflowService workflows) {
        return loop(new MockLlmClient(completions), catalog, workflows);
    }

    private AgentLoop loop(MockLlmClient llm, DeviceCatalogAssembler catalog, WorkflowService workflows) {
        AgentProperties properties = new AgentProperties();
        properties.setBaseUrl("http://localhost/v1");
        properties.setApiKey("test-key");
        properties.setModel("test-model");
        properties.setMaxRounds(6);
        when(workflows.list()).thenReturn(List.of());
        WorkflowSimulationService simulation = mock(WorkflowSimulationService.class);
        when(simulation.simulate(any())).thenReturn(new WorkflowSimulationReport(
                null, 9L, null, true, List.of(), List.of(), List.of()));
        return new AgentLoop(
                llm,
                List.of(new ListDeviceCatalogTool(catalog), new ListWorkflowCatalogTool(workflows),
                        new GetDeviceModelTool(catalog), new ValidateWorkflowTool(workflows),
                        new SimulateWorkflowTool(simulation), new SaveDraftTool(workflows)),
                new WorkflowGenerationSkill(),
                new AgentConceptDictionary(),
                properties);
    }

    private String lastUser(List<LlmMessage> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            if ("user".equals(messages.get(index).role())) return messages.get(index).content();
        }
        return "";
    }

    private LlmCompletion listCatalogs() {
        return new LlmCompletion("", List.of(
                new LlmToolCall("c-list_device_catalog", "list_device_catalog", "{}"),
                new LlmToolCall("c-list_workflow_catalog", "list_workflow_catalog", "{}")
        ));
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
