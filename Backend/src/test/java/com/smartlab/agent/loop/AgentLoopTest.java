package com.smartlab.agent.loop;

import com.smartlab.agent.AgentProperties;
import com.smartlab.agent.api.AgentInteractionLog;
import com.smartlab.agent.api.WorkflowGenerateResponse;
import com.smartlab.agent.catalog.DeviceCatalogAssembler;
import com.smartlab.agent.catalog.DeviceCatalogItem;
import com.smartlab.agent.llm.LlmCompletion;
import com.smartlab.agent.llm.LlmMessage;
import com.smartlab.agent.llm.LlmToolCall;
import com.smartlab.agent.llm.MockLlmClient;
import com.smartlab.agent.nudge.AgentConceptDictionary;
import com.smartlab.agent.operator.InstanceResolver;
import com.smartlab.agent.operator.OperatorContext;
import com.smartlab.agent.operator.OperatorDeviceCatalog;
import com.smartlab.agent.operator.OperatorSession;
import com.smartlab.agent.operator.OperatorSessionStore;
import com.smartlab.agent.operator.OperatorTool;
import com.smartlab.agent.operator.tool.PrepareDeviceActionTool;
import com.smartlab.agent.skill.WorkflowGenerationSkill;
import com.smartlab.agent.tool.AgentTool;
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
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.service.db.workflow.WorkflowService;
import com.smartlab.management.service.db.workflow.WorkflowSimulationService;
import com.smartlab.global.util.JsonNodeSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void saveDraftBecomesConfirmCardInsteadOfPersisting() throws Exception {
        WorkflowService workflows = mock(WorkflowService.class);
        DeviceCatalogAssembler catalog = mock(DeviceCatalogAssembler.class);
        when(catalog.list(null)).thenReturn(List.of(new DeviceCatalogItem(1L, "加热套", "热工", List.of(), List.of(), null, null)));
        when(workflows.validate(any())).thenReturn(cleanPreparation());
        when(workflows.saveDraft(any())).thenReturn(savedPreparation());

        AgentLoop loop = loop(List.of(
                listCatalogs(),
                call("validate_workflow", "{\"document\":{\"metadata\":{\"flowModelName\":\"加热\"},\"nodes\":[]}}"),
                call("save_draft", "{\"document\":{\"metadata\":{\"flowModelName\":\"加热\",\"description\":\"先升温再保温\"},\"nodes\":[]}}")
        ), catalog, workflows);

        WorkflowGenerateResponse result = loop.generate("把样品加热到80度");

        // 草稿不再直接落库，改成一张待确认卡片
        assertNull(result.definition());
        assertNull(result.flowModelId());
        assertNotNull(result.card());
        assertEquals("FLOW_DRAFT", result.card().kind());
        assertTrue(result.card().ready());
        assertNotNull(result.token());
        assertTrue(result.card().rows().stream()
                .anyMatch(row -> "流程名".equals(row.label()) && "加热".equals(row.value())));
        assertTrue(result.summary().contains("确认后才会保存"));
        assertTrue(result.trace().contains("list_device_catalog"));
        assertTrue(result.trace().contains("list_workflow_catalog"));
        assertTrue(result.trace().contains("validate_workflow"));
        assertTrue(result.trace().contains("save_draft"));
        assertTrue(result.logs().stream().anyMatch(item -> "start".equals(item.kind())));
        assertTrue(result.logs().stream().anyMatch(item -> "llm_call".equals(item.kind())));
        assertTrue(result.logs().stream().anyMatch(item -> "llm_reply".equals(item.kind())));
        assertTrue(result.logs().stream().anyMatch(item -> "tool_result".equals(item.kind()) && item.title().contains("save_draft")));
        assertTrue(result.logs().stream().anyMatch(item -> "gate".equals(item.kind()) && "生成确认卡片".equals(item.title())));
        assertTrue(result.logs().stream().anyMatch(item -> "done".equals(item.kind())));
        assertTrue(result.logs().stream().anyMatch(item -> "answer".equals(item.kind()) && item.detail() != null && !item.detail().isBlank()));
        // 关键：整个对话过程一次都没有写库
        verify(workflows, never()).saveDraft(any());

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

        loop(llm, catalog, workflows).generate("帮我设计一个加热流程");

        String nudge = lastUser(llm.messagesAt(1));
        assertTrue(nudge.contains("list_device_catalog"));
        assertTrue(nudge.contains("list_workflow_catalog"));
        assertFalse(nudge.contains("不要再整表列出"));
    }

    /**
     * 同一会话里先画过流程、再下设备指令：不能被历史里的流程痕迹拖去 simulate/save_draft。
     *
     * <p>复现的是线上那条 12 轮耗尽的路径：restore() 从历史恢复出"查过目录、校验通过"，
     * 于是设备指令被当成"流程画到一半"，nudge 一路按流程口径催下去。
     */
    @Test
    void deviceCommandAfterFlowTurnGetsDeviceNudgeInsteadOfFlowNudge() {
        WorkflowService workflows = mock(WorkflowService.class);
        DeviceCatalogAssembler catalog = mock(DeviceCatalogAssembler.class);
        when(catalog.list(null)).thenReturn(List.of(new DeviceCatalogItem(1L, "加热套", "热工", List.of(), List.of(), null, null)));
        when(workflows.validate(any())).thenReturn(cleanPreparation());
        when(workflows.saveDraft(any())).thenReturn(savedPreparation());

        OperatorDeviceCatalog devices = mock(OperatorDeviceCatalog.class);
        when(devices.resolver()).thenReturn(new InstanceResolver(List.of()));
        OperatorSessionStore store = new OperatorSessionStore();
        OperatorSession session = store.getOrCreate("device-after-flow");
        OperatorContext context = new OperatorContext(session, null, "tester");

        MockLlmClient llm = new MockLlmClient(List.of(
                listCatalogs(),
                call("validate_workflow", "{\"document\":{\"metadata\":{\"flowModelName\":\"加热\"},\"nodes\":[]}}"),
                call("save_draft", "{\"document\":{\"metadata\":{\"flowModelName\":\"加热\"},\"nodes\":[]}}"),
                new LlmCompletion("草稿已保存，编号 9。", List.of()),
                new LlmCompletion("我先看看这个动作要注意什么。", List.of()),
                call("prepare_device_action", "{\"instance\":\"反应釜1\",\"capability\":\"加热\",\"parameters\":{\"温度\":50}}"),
                new LlmCompletion("没有找到这台设备，请确认设备名。", List.of())
        ));
        AgentLoop loop = loop(llm, catalog, workflows, new PrepareDeviceActionTool(devices, store));

        loop.chat(session, context, "帮我设计一个加热流程", log -> {});
        WorkflowGenerateResponse second = loop.chat(session, context, "反应釜1加热到50度", log -> {});

        List<AgentInteractionLog> nudges = second.logs().stream()
                .filter(item -> "nudge".equals(item.kind()))
                .toList();
        assertEquals(1, nudges.size());
        assertTrue(nudges.get(0).detail().contains("直接调用 prepare_device_action"));
        assertFalse(nudges.get(0).detail().contains("按第 7 步"));
        assertTrue(second.trace().contains("prepare_device_action"));
        assertTrue(second.summary().contains("没有找到"));
    }

    @Test
    void flowDraftCardTranslatesCapabilityPlaceholders() throws Exception {
        WorkflowService workflows = mock(WorkflowService.class);
        DeviceCatalogAssembler catalog = mock(DeviceCatalogAssembler.class);
        when(catalog.list(null)).thenReturn(List.of(new DeviceCatalogItem(1L, "反应釜", "热工", List.of(), List.of(), null, null)));
        when(workflows.validate(any())).thenReturn(cleanPreparation());

        DeviceModels model = new DeviceModels();
        model.setCapabilities(JsonNodeSupport.MAPPER.readTree("""
                [{"capabilityName":"capability_1","displayName":"加热",
                  "parameters":[{"name":"parameter_1","displayName":"目标温度1","unit":"℃"},
                                {"name":"parameter_2","displayName":"加热时间1","unit":"min"}]}]
                """));
        OperatorDeviceCatalog deviceCatalog = mock(OperatorDeviceCatalog.class);
        when(deviceCatalog.modelOrNull(41L)).thenReturn(model);

        String document = """
                {"metadata":{"flowModelName":"加热"},
                 "nodes":[{"name":"开始","nodeType":"FUNC_NODE","functionType":"START"},
                          {"name":"反应釜加热","nodeType":"DEV_NODE","deviceModelId":41,
                           "capability":{"capabilityName":"capability_1",
                                         "capabilityParameters":{"parameter_1":80,"parameter_2":30}}},
                          {"name":"结束","nodeType":"FUNC_NODE","functionType":"END"}]}
                """;
        MockLlmClient llm = new MockLlmClient(List.of(
                listCatalogs(),
                call("validate_workflow", "{\"document\":" + document + "}"),
                call("save_draft", "{\"document\":" + document + "}")
        ));
        AgentLoop loop = loopWithDeviceCatalog(llm, catalog, workflows, deviceCatalog);

        WorkflowGenerateResponse result = loop.generate("设计一个反应釜加热流程");
        List<String> steps = result.card().parameters().stream()
                .map(row -> row.label() + " / " + row.value())
                .toList();
        assertEquals(3, steps.size());
        // capability_1 / parameter_1 必须翻成人能看懂的名字
        assertTrue(steps.get(1).contains("加热"), steps.get(1));
        assertTrue(steps.get(1).contains("目标温度1=80"), steps.get(1));
        assertTrue(steps.get(1).contains("加热时间1=30"), steps.get(1));
        assertFalse(steps.get(1).contains("capability_1"), steps.get(1));
        assertFalse(steps.get(1).contains("parameter_1"), steps.get(1));
    }

    private AgentLoop loopWithDeviceCatalog(MockLlmClient llm, DeviceCatalogAssembler catalog,
                                            WorkflowService workflows, OperatorDeviceCatalog deviceCatalog) {
        AgentProperties properties = new AgentProperties();
        properties.setBaseUrl("http://localhost/v1");
        properties.setApiKey("test-key");
        properties.setModel("test-model");
        properties.setMaxRounds(6);
        when(workflows.list()).thenReturn(List.of());
        WorkflowSimulationService simulation = mock(WorkflowSimulationService.class);
        when(simulation.simulate(any())).thenReturn(new WorkflowSimulationReport(
                null, 9L, null, true, List.of(), List.of(), List.of()));
        List<AgentTool> tools = new ArrayList<>(List.of(
                new ListDeviceCatalogTool(catalog), new ListWorkflowCatalogTool(workflows),
                new GetDeviceModelTool(catalog), new ValidateWorkflowTool(workflows),
                new SimulateWorkflowTool(simulation), new SaveDraftTool(workflows)));
        return new AgentLoop(llm, tools, List.of(), new WorkflowGenerationSkill(),
                new AgentConceptDictionary(), properties, deviceCatalog);
    }

    private AgentLoop loop(List<LlmCompletion> completions, DeviceCatalogAssembler catalog, WorkflowService workflows) {
        return loop(new MockLlmClient(completions), catalog, workflows);
    }

    private AgentLoop loop(MockLlmClient llm, DeviceCatalogAssembler catalog, WorkflowService workflows) {
        return loop(llm, catalog, workflows, null);
    }

    private AgentLoop loop(MockLlmClient llm, DeviceCatalogAssembler catalog, WorkflowService workflows,
                           OperatorTool extraTool) {
        AgentProperties properties = new AgentProperties();
        properties.setBaseUrl("http://localhost/v1");
        properties.setApiKey("test-key");
        properties.setModel("test-model");
        properties.setMaxRounds(6);
        when(workflows.list()).thenReturn(List.of());
        WorkflowSimulationService simulation = mock(WorkflowSimulationService.class);
        when(simulation.simulate(any())).thenReturn(new WorkflowSimulationReport(
                null, 9L, null, true, List.of(), List.of(), List.of()));
        List<AgentTool> flowTools = new ArrayList<>(List.of(
                new ListDeviceCatalogTool(catalog), new ListWorkflowCatalogTool(workflows),
                new GetDeviceModelTool(catalog), new ValidateWorkflowTool(workflows),
                new SimulateWorkflowTool(simulation), new SaveDraftTool(workflows)));
        List<OperatorTool> operatorTools = new ArrayList<>();
        if (extraTool != null) operatorTools.add(extraTool);
        return new AgentLoop(
                llm,
                flowTools,
                operatorTools,
                new WorkflowGenerationSkill(),
                new AgentConceptDictionary(),
                properties,
                null);
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
