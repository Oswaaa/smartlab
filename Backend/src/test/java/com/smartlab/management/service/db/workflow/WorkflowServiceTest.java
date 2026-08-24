package com.smartlab.management.service.db.workflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.workflow.WorkflowDefinitionCompiler;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.dto.workflow.WorkflowPreparationResponse;
import com.smartlab.global.contract.WorkflowNodeSystemContract;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.dto.workflow.WorkflowModelDocument;
import com.smartlab.management.dto.workflow.WorkflowModelDocuments;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.entity.workflow.FlowModels;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.mapper.workflow.FlowModelsMapper;
import com.smartlab.management.mapper.workflow.FlowNodeMapper;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.service.db.resource.device.DeviceModelService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowServiceTest {

    @Test
    void savesIncompleteDraftButDoesNotMakeItExecutable() {
        DraftLifecycleFixture fixture = draftLifecycleFixture();

        WorkflowPreparationResponse saved = fixture.service().saveDraft(incompleteRequest());

        assertEquals("DRAFT", saved.definition().getStatus());
        assertFalse(saved.executable());
        assertTrue(saved.issues().stream().noneMatch(WorkflowIssue::blocking));
        assertEquals(0, fixture.savedNodes().size());
    }

    @Test
    void getDefinitionUsesCacheUntilTheModelIsSavedAgain() {
        DraftLifecycleFixture fixture = draftLifecycleFixture();
        Long id = fixture.service().saveDraft(incompleteRequest()).definition().getId();
        org.mockito.Mockito.clearInvocations(fixture.modelMapper(), fixture.nodeMapper());

        assertNotNull(fixture.service().getDefinition(id));
        assertNotNull(fixture.service().getDefinition(id));

        org.mockito.Mockito.verify(fixture.modelMapper(), org.mockito.Mockito.never()).selectById(id);
        org.mockito.Mockito.verify(fixture.nodeMapper(), org.mockito.Mockito.never()).selectList(any());
    }
    @Test
    void editingActiveModelCreatesSuccessorDraft() {
        DraftLifecycleFixture fixture = draftLifecycleFixture();
        FlowModels active = new FlowModels();
        active.setId(7L);
        active.setFlowName("published-flow");
        active.setStatus("ACTIVE");
        active.setVersion(2);
        active.setNodes(JsonNodeSupport.arrayNode());
        fixture.models().put(7L, active);

        WorkflowModelDocument edit = incompleteRequest();
        edit.flowModelId(7L);
        edit.flowModelName("published-flow-v3");
        WorkflowPreparationResponse saved = fixture.service().saveDraft(edit);

        assertNotEquals(7L, saved.definition().getId());
        assertEquals(7L, fixture.savedModel().get().getPredecessorId());
        assertEquals(3, saved.definition().getVersion());
        assertEquals("DRAFT", saved.definition().getStatus());
    }


    @Test
    void activeDraftKeepsSparseNodeRefsAcrossRenameAndReorder() {
        DraftLifecycleFixture fixture = draftLifecycleFixture();
        FlowModels active = new FlowModels();
        active.setId(7L);
        active.setStatus("ACTIVE");
        active.setVersion(2);
        active.setNodes(JsonNodeSupport.MAPPER.createArrayNode()
                .add(JsonNodeSupport.objectNode().put("nodeIdRef", 10).put("nodeName", "first"))
                .add(JsonNodeSupport.objectNode().put("nodeIdRef", 30).put("nodeName", "second")));
        fixture.models().put(7L, active);
        WorkflowModelDocument edit = incompleteRequest();
        edit.flowModelId(7L);
        edit.setNodes(JsonNodeSupport.MAPPER.createArrayNode()
                .add(JsonNodeSupport.objectNode().put("name", "renamed").put("nodeIdRef", 30))
                .add(JsonNodeSupport.objectNode().put("name", "first").put("nodeIdRef", 10))
                .add(JsonNodeSupport.objectNode().put("name", "new")));

        fixture.service().saveDraft(edit);

        assertEquals(List.of(30L, 10L, 31L), fixture.savedNodes().stream().map(FlowNode::getNodeIdRef).toList());
    }

    @Test
    void publishingAnIncompleteActiveModelCreatesDraftSuccessor() {
        DraftLifecycleFixture fixture = draftLifecycleFixture();
        FlowModels active = new FlowModels();
        active.setId(7L);
        active.setStatus("ACTIVE");
        active.setVersion(2);
        active.setNodes(JsonNodeSupport.arrayNode());
        fixture.models().put(7L, active);
        WorkflowModelDocument edit = incompleteRequest();
        edit.flowModelId(7L);

        WorkflowPreparationResponse saved = fixture.service().publish(edit);

        assertEquals("DRAFT", saved.definition().getStatus());
        assertFalse(saved.published());
        assertEquals(7L, fixture.savedModel().get().getPredecessorId());
        assertEquals(3, saved.definition().getVersion());
        assertNotEquals(7L, saved.definition().getId());
    }

    @Test
    void publishingAnExecutableActiveModelPublishesSuccessorInOneStep() throws Exception {
        DraftLifecycleFixture fixture = draftLifecycleFixture();
        FlowModels active = new FlowModels();
        active.setId(7L);
        active.setFlowName("published-flow");
        active.setStatus("ACTIVE");
        active.setVersion(2);
        active.setNodes(JsonNodeSupport.arrayNode());
        fixture.models().put(7L, active);

        WorkflowModelDocument edit = startEndWorkflow();
        edit.flowModelId(7L);
        WorkflowPreparationResponse saved = fixture.service().publish(edit);

        assertEquals("ACTIVE", saved.definition().getStatus());
        assertTrue(saved.published());
        assertEquals(7L, fixture.savedModel().get().getPredecessorId());
        assertEquals(3, saved.definition().getVersion());
        assertNotEquals(7L, saved.definition().getId());
        assertEquals("ACTIVE", fixture.models().get(7L).getStatus());
    }

    @Test
    void forkingActiveModelTwiceRejectsExistingSuccessor() {
        DraftLifecycleFixture fixture = draftLifecycleFixture();
        FlowModels active = seededActive(7L, 1);
        fixture.models().put(7L, active);

        WorkflowModelDocument first = incompleteRequest();
        first.flowModelId(7L);
        WorkflowPreparationResponse saved = fixture.service().saveDraft(first);

        WorkflowModelDocument second = incompleteRequest();
        second.flowModelId(7L);
        WorkflowSuccessorExistsException error = assertThrows(WorkflowSuccessorExistsException.class,
                () -> fixture.service().saveDraft(second));
        assertEquals(saved.definition().getId(), error.conflict().successorId());
        assertEquals("DRAFT", error.conflict().status());
        assertEquals(2, error.conflict().version());
        assertTrue(error.getMessage().contains("已有后续版本"));
        assertTrue(error.getMessage().contains("草稿"));
        org.mockito.Mockito.verify(fixture.modelMapper(), org.mockito.Mockito.times(1)).insert(any(FlowModels.class));
    }

    @Test
    void savingTheSuccessorDraftOverwritesTheSameVersion() {
        DraftLifecycleFixture fixture = draftLifecycleFixture();
        fixture.models().put(7L, seededActive(7L, 1));

        WorkflowModelDocument first = incompleteRequest();
        first.flowModelId(7L);
        Long draftId = fixture.service().saveDraft(first).definition().getId();

        WorkflowModelDocument edit = incompleteRequest();
        edit.flowModelId(draftId);
        edit.flowModelName("published-flow-edited");
        WorkflowPreparationResponse saved = fixture.service().saveDraft(edit);

        assertEquals(draftId, saved.definition().getId());
        assertEquals(2, saved.definition().getVersion());
        assertEquals("published-flow-edited", saved.definition().getName());
        org.mockito.Mockito.verify(fixture.modelMapper(), org.mockito.Mockito.times(1)).insert(any(FlowModels.class));
    }

    @Test
    void deletingDraftSuccessorAllowsForkAgain() {
        DraftLifecycleFixture fixture = draftLifecycleFixture();
        fixture.models().put(7L, seededActive(7L, 1));

        WorkflowModelDocument first = incompleteRequest();
        first.flowModelId(7L);
        Long draftId = fixture.service().saveDraft(first).definition().getId();
        fixture.service().deleteDefinition(draftId);

        WorkflowModelDocument second = incompleteRequest();
        second.flowModelId(7L);
        WorkflowPreparationResponse again = fixture.service().saveDraft(second);

        assertEquals("DRAFT", again.definition().getStatus());
        assertEquals(7L, again.definition().getPredecessorId());
        assertEquals(2, again.definition().getVersion());
        assertNotEquals(draftId, again.definition().getId());
    }

    @Test
    void publishingActiveTwiceRejectsWhenSuccessorExists() throws Exception {
        DraftLifecycleFixture fixture = draftLifecycleFixture();
        fixture.models().put(7L, seededActive(7L, 2));

        WorkflowModelDocument first = startEndWorkflow();
        first.flowModelId(7L);
        WorkflowPreparationResponse saved = fixture.service().publish(first);
        assertTrue(saved.published());

        WorkflowModelDocument second = startEndWorkflow();
        second.flowModelId(7L);
        WorkflowSuccessorExistsException error = assertThrows(WorkflowSuccessorExistsException.class,
                () -> fixture.service().publish(second));
        assertEquals(saved.definition().getId(), error.conflict().successorId());
        assertEquals("ACTIVE", error.conflict().status());
        assertTrue(error.getMessage().contains("已启用"));
    }

    @Test
    void unrelatedNewDraftsCanShareVersionOne() {
        DraftLifecycleFixture fixture = draftLifecycleFixture();

        WorkflowPreparationResponse first = fixture.service().saveDraft(incompleteRequest());
        WorkflowPreparationResponse second = fixture.service().saveDraft(incompleteRequest());

        assertNotEquals(first.definition().getId(), second.definition().getId());
        assertEquals(1, first.definition().getVersion());
        assertEquals(1, second.definition().getVersion());
        assertEquals(null, first.definition().getPredecessorId());
        assertEquals(null, second.definition().getPredecessorId());
    }

    @Test
    void saveAsNewCreatesRootDraftWithoutConsumingSuccessorSlot() {
        DraftLifecycleFixture fixture = draftLifecycleFixture();
        fixture.models().put(7L, seededActive(7L, 1));
        WorkflowModelDocument source = incompleteRequest();
        source.flowModelId(7L);
        source.flowModelName("published-flow");

        WorkflowPreparationResponse copy = fixture.service().saveAsNew(source);

        assertNotEquals(7L, copy.definition().getId());
        assertEquals(1, copy.definition().getVersion());
        assertEquals(null, copy.definition().getPredecessorId());
        assertEquals("DRAFT", copy.definition().getStatus());
        assertEquals("published-flow（副本）", copy.definition().getName());
        assertEquals("ACTIVE", fixture.models().get(7L).getStatus());
        assertEquals(Integer.valueOf(1), fixture.models().get(7L).getVersion());

        WorkflowModelDocument fork = incompleteRequest();
        fork.flowModelId(7L);
        WorkflowPreparationResponse successor = fixture.service().saveDraft(fork);
        assertEquals(7L, successor.definition().getPredecessorId());
        assertEquals(2, successor.definition().getVersion());
        assertNotEquals(copy.definition().getId(), successor.definition().getId());
    }

    @Test
    void validateIncompleteDoesNotPersist() {
        DraftLifecycleFixture fixture = draftLifecycleFixture();

        WorkflowPreparationResponse result = fixture.service().validate(incompleteRequest());

        assertFalse(result.published());
        assertFalse(result.executable());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.blocking()));
        assertEquals(null, fixture.savedModel().get());
        org.mockito.Mockito.verify(fixture.modelMapper(), org.mockito.Mockito.never()).insert(any(FlowModels.class));
        org.mockito.Mockito.verify(fixture.modelMapper(), org.mockito.Mockito.never()).updateById(any(FlowModels.class));
    }

    @Test
    void validateMissingSubflowDoesNotPersist() throws Exception {
        DraftLifecycleFixture fixture = draftLifecycleFixture();

        WorkflowPreparationResponse result = fixture.service().validate(validSubflowWorkflow());

        assertFalse(result.published());
        assertFalse(result.executable());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.blocking()
                && issue.code().equals("WORKFLOW_PUBLISH_VALIDATION_FAILED")));
        assertEquals(null, fixture.savedModel().get());
        org.mockito.Mockito.verify(fixture.modelMapper(), org.mockito.Mockito.never()).insert(any(FlowModels.class));
    }

    @Test
    void validateExecutableActiveModelDoesNotForkOrPersist() throws Exception {
        DraftLifecycleFixture fixture = draftLifecycleFixture();
        FlowModels active = new FlowModels();
        active.setId(7L);
        active.setFlowName("published-flow");
        active.setStatus("ACTIVE");
        active.setVersion(2);
        active.setNodes(JsonNodeSupport.arrayNode());
        fixture.models().put(7L, active);

        WorkflowModelDocument edit = startEndWorkflow();
        edit.flowModelId(7L);
        WorkflowPreparationResponse result = fixture.service().validate(edit);

        assertTrue(result.executable());
        assertFalse(result.published());
        assertEquals(null, fixture.savedModel().get());
        assertEquals("ACTIVE", fixture.models().get(7L).getStatus());
        org.mockito.Mockito.verify(fixture.modelMapper(), org.mockito.Mockito.never()).insert(any(FlowModels.class));
    }

    @Test
    void publishReturnsStructuredIssueWithoutPersistingWhenSubflowIsMissing() throws Exception {
        DraftLifecycleFixture fixture = draftLifecycleFixture();

        WorkflowPreparationResponse result = fixture.service().publish(validSubflowWorkflow());

        assertFalse(result.published());
        assertFalse(result.executable());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.blocking()
                && issue.code().equals("WORKFLOW_PUBLISH_VALIDATION_FAILED")));
        assertEquals(null, fixture.savedModel().get());
    }
    @Test
    void rejectsDraftWorkflowAsNonExecutable() {
        FlowModelsMapper models = mock(FlowModelsMapper.class);
        FlowNodeMapper nodes = mock(FlowNodeMapper.class);
        TaskMapper tasks = mock(TaskMapper.class);
        WorkflowDefinitionCompiler compiler = mock(WorkflowDefinitionCompiler.class);
        DeviceModelService deviceModels = mock(DeviceModelService.class);
        FlowModels draft = new FlowModels();
        draft.setId(1L);
        draft.setFlowName("draft-flow");
        draft.setStatus("DRAFT");
        when(models.selectById(1L)).thenReturn(draft);

        WorkflowService service = new WorkflowService(models, nodes, tasks, compiler, deviceModels);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.requireExecutableDefinition(1L));
        assertTrue(error.getMessage().contains("只有ACTIVE工作流"));
    }

    @Test
    void rejectsUpdatingASubflowReferencedByAnExistingTaskRoot() {
        FlowModelsMapper models = mock(FlowModelsMapper.class);
        FlowNodeMapper nodes = mock(FlowNodeMapper.class);
        TaskMapper tasks = mock(TaskMapper.class);
        WorkflowDefinitionCompiler compiler = mock(WorkflowDefinitionCompiler.class);
        DeviceModelService deviceModels = mock(DeviceModelService.class);
        FlowModels subflow = new FlowModels();
        subflow.setId(2L);
        subflow.setFlowName("subflow");
        subflow.setNodes(JsonNodeSupport.arrayNode());
        when(models.selectById(2L)).thenReturn(subflow);
        when(compiler.compile(any(WorkflowModelDocument.class))).thenReturn(emptyCompilation());
        Task task = new Task();
        task.setFlowModelId(1L);
        when(tasks.selectList(any())).thenReturn(List.of(task));
        when(tasks.selectCount(any())).thenReturn(0L);
        FlowNode subflowNode = new FlowNode();
        subflowNode.setFlowModelId(1L);
        subflowNode.setNodeIdRef(1L);
        subflowNode.setNodeType("SUBFLOW_NODE");
        subflowNode.setSubFlowModelId(2L);
        when(nodes.selectList(any())).thenReturn(List.of(subflowNode));
        WorkflowService service = new WorkflowService(models, nodes, tasks, compiler, deviceModels);
        WorkflowModelDocument request = WorkflowModelDocuments.of("workflow");
        request.flowModelId(2L);
        request.flowModelName("subflowV2");
        request.setNodes(JsonNodeSupport.arrayNode());
        request.setInterfaceConnections(JsonNodeSupport.arrayNode());
        request.setPortConnections(JsonNodeSupport.arrayNode());

        assertThrows(IllegalStateException.class, () -> service.saveDefinition(request));
    }

    @Test
    void rejectsUnknownCapabilityAndExtraParameter() throws Exception {
        ServiceFixture fixture = semanticFixture();
        WorkflowModelDocument request = validDeviceWorkflow();
        ((ObjectNode) request.getNodes().get(0)).with("capability")
                .put("capabilityName", "missing")
                .with("capabilityParameters").put("extra", 1);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> fixture.service().saveDefinition(request));
        assertTrue(error.getMessage().contains("heater.capability.capabilityName"));
        assertTrue(error.getMessage().contains("missing"));
    }

    @Test
    void rejectsAttributeMappingWithDifferentDataType() throws Exception {
        ServiceFixture fixture = semanticFixture();
        WorkflowModelDocument request = validDeviceWorkflow();
        ObjectNode variable = (ObjectNode) request.getNodes().get(0).withArray("internalVariables").get(0);
        variable.put("name", "temperature").put("dataType", "STRING").put("attributesMapping", "temperature");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> fixture.service().saveDefinition(request));
        assertTrue(error.getMessage().contains("attributesMapping"));
        assertTrue(error.getMessage().contains("不一致"));
    }

    @Test
    void roundTripsSubFlowModelDescriptionThroughCapabilityJson() throws Exception {
        FlowModelsMapper models = mock(FlowModelsMapper.class);
        FlowNodeMapper nodes = mock(FlowNodeMapper.class);
        TaskMapper tasks = mock(TaskMapper.class);
        DeviceModelService deviceModels = mock(DeviceModelService.class);
        AtomicReference<FlowModels> savedModel = new AtomicReference<>();
        List<FlowNode> savedNodes = new ArrayList<>();
        FlowModels referenced = new FlowModels();
        referenced.setId(2L);
        referenced.setFlowName("test");
        when(models.selectById(any())).thenAnswer(invocation -> {
            long id = ((Number) invocation.getArgument(0)).longValue();
            return id == 2L ? referenced : savedModel.get();
        });
        doAnswer(invocation -> {
            FlowModels model = invocation.getArgument(0);
            model.setId(10L);
            savedModel.set(model);
            return 1;
        }).when(models).insert(any(FlowModels.class));
        doAnswer(invocation -> {
            savedNodes.add(invocation.getArgument(0));
            return 1;
        }).when(nodes).insert(any(FlowNode.class));
        when(nodes.selectList(any())).thenAnswer(invocation -> {
            List<FlowNode> list = new ArrayList<>(savedNodes);
            list.sort(Comparator.comparing(FlowNode::getNodeIdRef));
            return list;
        });
        WorkflowService service = new WorkflowService(models, nodes, tasks,
                new WorkflowDefinitionCompiler(), deviceModels);
        WorkflowModelDocument request = validSubflowWorkflow();
        ((ObjectNode) request.getNodes().get(1)).put("subFlowModelDescription", "机械臂打开装置");

        WorkflowDetailResponse saved = service.saveDefinition(request);
        WorkflowDetailResponse loaded = service.getDefinition(saved.getId());

        assertEquals("机械臂打开装置", loaded.getNodesDef().get(1).path("subFlowModelDescription").asText());
    }

    private ServiceFixture semanticFixture() throws Exception {
        FlowModelsMapper models = mock(FlowModelsMapper.class);
        FlowNodeMapper nodes = mock(FlowNodeMapper.class);
        TaskMapper tasks = mock(TaskMapper.class);
        WorkflowDefinitionCompiler compiler = mock(WorkflowDefinitionCompiler.class);
        DeviceModelService deviceModels = mock(DeviceModelService.class);
        when(compiler.compile(any(WorkflowModelDocument.class))).thenReturn(emptyCompilation());
        when(deviceModels.getById(anyString())).thenReturn(deviceModel());
        return new ServiceFixture(new WorkflowService(models, nodes, tasks, compiler, deviceModels));
    }

    private WorkflowDefinitionCompiler.CompiledWorkflow emptyCompilation() {
        return new WorkflowDefinitionCompiler.CompiledWorkflow(Map.of(), Map.of(), Map.of(), Map.of(), 1, 2);
    }

    private DeviceModels deviceModel() throws Exception {
        DeviceModels model = new DeviceModels();
        model.setCapabilities(JsonNodeSupport.MAPPER.readTree("[{\"capabilityName\":\"heat\",\"parameters\":[{\"name\":\"target\",\"dataType\":\"DOUBLE\"}]}]"));
        model.setAttributes(JsonNodeSupport.MAPPER.readTree("[{\"attributeName\":\"temperature\",\"dataType\":\"DOUBLE\"}]"));
        return model;
    }

    private WorkflowModelDocument validDeviceWorkflow() throws Exception {
        WorkflowModelDocument request = WorkflowModelDocuments.of("workflow");
        request.flowModelName("device-test");
        request.setNodes(JsonNodeSupport.MAPPER.readTree("[{\"name\":\"heater\",\"nodeType\":\"DEV_NODE\",\"deviceModelId\":1,\"capability\":{\"capabilityName\":\"heat\",\"capabilityParameters\":{\"target\":30}},\"internalVariables\":[{\"name\":\"temp\",\"dataType\":\"DOUBLE\"}],\"lifecycle\":{\"initialStateName\":\"PENDING\",\"states\":[\"PENDING\",\"RUNNING\",\"SUCCEEDED\",\"FAILED\",\"TERMINATING\",\"TERMINATED\"],\"transitions\":[{\"fromStateName\":\"PENDING\",\"toStateName\":\"RUNNING\"},{\"fromStateName\":\"PENDING\",\"toStateName\":\"TERMINATED\"},{\"fromStateName\":\"RUNNING\",\"toStateName\":\"SUCCEEDED\"},{\"fromStateName\":\"RUNNING\",\"toStateName\":\"FAILED\"},{\"fromStateName\":\"RUNNING\",\"toStateName\":\"TERMINATING\"},{\"fromStateName\":\"TERMINATING\",\"toStateName\":\"TERMINATED\"},{\"fromStateName\":\"TERMINATING\",\"toStateName\":\"FAILED\"}]},\"interfaces\":[{\"name\":\"Interface_workflow_in\",\"direction\":\"IN\",\"interfaceType\":\"WORKFLOW\",\"allowedSignals\":[\"ACTIVE\"],\"bindingTriggers\":[{\"condition\":{\"object\":\"inputSignalName\",\"operator\":\"=\",\"threshold\":\"ACTIVE\"},\"action\":\"startDevice\"}]},{\"name\":\"Interface_state_out\",\"direction\":\"OUT\",\"interfaceType\":\"STATE\",\"allowedSignals\":[\"WF_EXECUTE_START\"]},{\"name\":\"Interface_state_in\",\"direction\":\"IN\",\"interfaceType\":\"STATE\",\"allowedSignals\":[\"CMD_STATE\",\"OP_STATE\"],\"bindingTriggers\":[{\"condition\":{\"object\":\"inputPayload.stateName\",\"operator\":\"=\",\"threshold\":\"COMPLETED\"},\"action\":\"completeNode\"}]},{\"name\":\"Interface_workflow_out\",\"direction\":\"OUT\",\"interfaceType\":\"WORKFLOW\",\"allowedSignals\":[\"ACTIVE\"]}],\"ports\":[],\"actions\":[{\"actionName\":\"startDevice\",\"actionType\":\"EMIT\",\"targetInterfaceName\":\"Interface_state_out\",\"signalName\":\"WF_EXECUTE_START\"},{\"actionName\":\"completeNode\",\"actionType\":\"EMIT\",\"targetInterfaceName\":\"Interface_workflow_out\",\"signalName\":\"ACTIVE\"}]}]"));
        request.setInterfaceConnections(JsonNodeSupport.arrayNode());
        request.setPortConnections(JsonNodeSupport.arrayNode());
        return request;
    }

    private WorkflowModelDocument startEndWorkflow() throws Exception {
        WorkflowModelDocument request = WorkflowModelDocuments.of("workflow");
        request.flowModelName("published-flow");

        ObjectNode start = WorkflowNodeSystemContract.template("FUNC_NODE", "START");
        start.put("name", "start").put("nodeType", "FUNC_NODE").put("functionType", "START");
        start.putArray("internalVariables");
        start.putArray("ports");

        ObjectNode end = WorkflowNodeSystemContract.template("FUNC_NODE", "END");
        end.put("name", "end").put("nodeType", "FUNC_NODE").put("functionType", "END");
        end.putArray("internalVariables");
        end.putArray("ports");

        request.setNodes(JsonNodeSupport.arrayNode().add(start).add(end));
        request.setInterfaceConnections(JsonNodeSupport.MAPPER.readTree("[{\"connectionType\":\"NODE_TO_NODE\",\"source\":{\"nodeName\":\"start\",\"interfaceName\":\"Interface_workflow_out\"},\"target\":{\"nodeName\":\"end\",\"interfaceName\":\"Interface_workflow_in\"}}]"));
        request.setPortConnections(JsonNodeSupport.arrayNode());
        return request;
    }

    private WorkflowModelDocument validSubflowWorkflow() throws Exception {
        WorkflowModelDocument request = WorkflowModelDocuments.of("workflow");
        request.flowModelName("subflow-parent");

        ObjectNode start = WorkflowNodeSystemContract.template("FUNC_NODE", "START");
        start.put("name", "start").put("nodeType", "FUNC_NODE").put("functionType", "START");
        start.putArray("internalVariables");
        start.putArray("ports");

        ObjectNode subflow = WorkflowNodeSystemContract.template("SUBFLOW_NODE", null);
        subflow.put("name", "subflow").put("nodeType", "SUBFLOW_NODE")
                .put("subFlowModelId", 2).put("subFlowModelDescription", "");
        subflow.putArray("internalVariables");
        subflow.putArray("ports");

        ObjectNode end = WorkflowNodeSystemContract.template("FUNC_NODE", "END");
        end.put("name", "end").put("nodeType", "FUNC_NODE").put("functionType", "END");
        end.putArray("internalVariables");
        end.putArray("ports");

        request.setNodes(JsonNodeSupport.arrayNode().add(start).add(subflow).add(end));
        request.setInterfaceConnections(JsonNodeSupport.MAPPER.readTree("[{\"connectionType\":\"NODE_TO_NODE\",\"source\":{\"nodeName\":\"start\",\"interfaceName\":\"Interface_workflow_out\"},\"target\":{\"nodeName\":\"subflow\",\"interfaceName\":\"Interface_workflow_in\"}},{\"connectionType\":\"NODE_TO_NODE\",\"source\":{\"nodeName\":\"subflow\",\"interfaceName\":\"Interface_workflow_out\"},\"target\":{\"nodeName\":\"end\",\"interfaceName\":\"Interface_workflow_in\"}}]"));
        request.setPortConnections(JsonNodeSupport.arrayNode());
        return request;
    }

    private record ServiceFixture(WorkflowService service) {
}
    private DraftLifecycleFixture draftLifecycleFixture() {
        Map<Long, FlowModels> models = new LinkedHashMap<>();
        List<FlowNode> savedNodes = new ArrayList<>();
        AtomicReference<FlowModels> savedModel = new AtomicReference<>();
        FlowModelsMapper modelMapper = mock(FlowModelsMapper.class);
        FlowNodeMapper nodeMapper = mock(FlowNodeMapper.class);
        TaskMapper taskMapper = mock(TaskMapper.class);
        DeviceModelService deviceModels = mock(DeviceModelService.class);
        when(modelMapper.selectById(any())).thenAnswer(invocation -> models.get(((Number) invocation.getArgument(0)).longValue()));
        when(modelMapper.selectByPredecessorId(any())).thenAnswer(invocation -> {
            Object argument = invocation.getArgument(0);
            if (!(argument instanceof Number)) return null;
            long predecessorId = ((Number) argument).longValue();
            return models.values().stream()
                    .filter(model -> model.getPredecessorId() != null && model.getPredecessorId() == predecessorId)
                    .min(Comparator.comparing(FlowModels::getId))
                    .orElse(null);
        });
        AtomicLong nextId = new AtomicLong(1);
        doAnswer(invocation -> {
            FlowModels model = invocation.getArgument(0);
            if (model.getId() == null) model.setId(nextId.getAndIncrement());
            models.put(model.getId(), model);
            savedModel.set(model);
            return 1;
        }).when(modelMapper).insert(any(FlowModels.class));
        doAnswer(invocation -> {
            Object argument = invocation.getArgument(0);
            if (argument instanceof Number) models.remove(((Number) argument).longValue());
            return 1;
        }).when(modelMapper).deleteById(any(java.io.Serializable.class));
        doAnswer(invocation -> {
            savedNodes.add(invocation.getArgument(0));
            return 1;
        }).when(nodeMapper).insert(any(FlowNode.class));
        when(nodeMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(savedNodes));
        WorkflowService service = new WorkflowService(modelMapper, nodeMapper, taskMapper,
                new WorkflowDefinitionCompiler(), deviceModels);
        return new DraftLifecycleFixture(service, models, savedModel, savedNodes, modelMapper, nodeMapper);
    }

    private FlowModels seededActive(Long id, int version) {
        FlowModels active = new FlowModels();
        active.setId(id);
        active.setFlowName("published-flow");
        active.setStatus("ACTIVE");
        active.setVersion(version);
        active.setNodes(JsonNodeSupport.arrayNode());
        return active;
    }

    private WorkflowModelDocument incompleteRequest() {
        WorkflowModelDocument request = WorkflowModelDocuments.of("workflow");
        request.flowModelName("incomplete");
        request.setNodes(JsonNodeSupport.arrayNode());
        request.setInterfaceConnections(JsonNodeSupport.arrayNode());
        request.setPortConnections(JsonNodeSupport.arrayNode());
        return request;
    }

    private record DraftLifecycleFixture(WorkflowService service, Map<Long, FlowModels> models,
                                         AtomicReference<FlowModels> savedModel, List<FlowNode> savedNodes,
                                         FlowModelsMapper modelMapper, FlowNodeMapper nodeMapper) {}
}
