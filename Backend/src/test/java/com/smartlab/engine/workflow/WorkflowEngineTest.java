package com.smartlab.engine.workflow;

import com.smartlab.engine.statemachine.StateMachineEngine;
import com.smartlab.engine.statemachine.StateMachineInterfaceSignalEvent;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowSaveRequest;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.workflow.FlowNodeService;
import com.smartlab.management.service.db.workflow.WorkflowRuntimeService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import com.smartlab.management.service.db.workflow.WorkflowTaskResourceService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowEngineTest {
    private final WorkflowRuntimeService runtime = mock(WorkflowRuntimeService.class);
    private final WorkflowService workflows = mock(WorkflowService.class);
    private final FlowNodeService nodes = mock(FlowNodeService.class);
    private final WorkflowEngine engine = new WorkflowEngine(runtime, workflows, nodes,
            new WorkflowConditionEvaluator(),
            mock(com.smartlab.engine.workflow.action.WorkflowActionRegistry.class), mock(WorkflowExecutionOperations.class));

    @Test
    void createsStartStepForNewRunningTask() {
        Task task = task();
        FlowNode start = flowNode(101L, 10L, 1L, "START");
        when(runtime.activeSteps(task.getId())).thenReturn(List.of());
        when(runtime.steps(task.getId())).thenReturn(List.of());
        when(workflows.compileDefinition(10L)).thenReturn(compiled());
        when(workflows.nodes(10L)).thenReturn(List.of(start));

        engine.processTask(task);

        verify(runtime).createStep(task, start, null, 0, JsonNodeSupport.objectNode());
    }

    @Test
    void completesDeviceStepOnlyForMatchingMessageIdEvent() {
        Task task = task();
        FlowNode device = flowNode(102L, 10L, 2L, null);
        device.setNodeType("DEVICE_CAPABILITY_NODE");
        FlowNode end = flowNode(103L, 10L, 3L, "END");
        TaskStep step = new TaskStep();
        step.setId(88L);
        step.setTaskId(task.getId());
        step.setFlowNodeId(device.getId());
        step.setNodeIdRef(2L);
        step.setNodeStatus("RUNNING");
        step.setStepDepth(0);
        step.setInterfaceInSnapshot(JsonNodeSupport.objectNode().put("messageId", "msg-1"));
        when(runtime.findRunningDeviceStepByMessageId("msg-1")).thenReturn(step);
        when(runtime.task(task.getId())).thenReturn(task);
        when(nodes.getById(device.getId())).thenReturn(device);
        when(workflows.compileDefinition(10L)).thenReturn(compiled());
        when(workflows.nodes(10L)).thenReturn(List.of(device, end));

        var signal = JsonNodeSupport.objectNode();
        signal.put("signalName", "CMD_STATE");
        signal.putObject("payload").put("state", "COMPLETED");
        engine.handleStateMachineSignal(new StateMachineInterfaceSignalEvent(
                20L, "Interface_status_out", "STAT", signal, Map.of("messageId", "msg-1")));


        verify(runtime).completeStep(eq(step), any());
        verify(runtime).createStep(eq(task), eq(end), eq(null), eq(0), any());
    }

    @Test
    void aggregateWaitsUntilEveryIncomingPredecessorCompletes() {
        Task task = task();
        FlowNode first = flowNode(101L, 10L, 1L, "START");
        FlowNode second = flowNode(102L, 10L, 2L, "START");
        FlowNode aggregate = flowNode(103L, 10L, 3L, "AGGREGATE");
        FlowNode end = flowNode(104L, 10L, 4L, "END");
        TaskStep aggregateStep = step(task, aggregate, "RUNNING");
        TaskStep completedFirst = step(task, first, "COMPLETED");
        var incoming = Map.of(3L, List.of(
                new WorkflowDefinitionCompiler.Connection(1L, "flow_out", 3L, "flow_in"),
                new WorkflowDefinitionCompiler.Connection(2L, "flow_out", 3L, "flow_in")));
        var outgoing = Map.of(3L, List.of(
                new WorkflowDefinitionCompiler.Connection(3L, "flow_out", 4L, "flow_in")));
        var compiled = new WorkflowDefinitionCompiler.CompiledWorkflow(
                Map.of(), outgoing, incoming, 1L, 4L);
        when(runtime.activeSteps(task.getId())).thenReturn(List.of(aggregateStep));
        when(runtime.task(task.getId())).thenReturn(task);
        when(runtime.steps(task.getId())).thenReturn(List.of(completedFirst, aggregateStep));
        when(nodes.getById(aggregate.getId())).thenReturn(aggregate);
        when(workflows.compileDefinition(aggregate.getFlowModelId())).thenReturn(compiled);

        engine.processTask(task);

        verify(runtime, never()).completeStep(eq(aggregateStep), any());
        verify(runtime, never()).createStep(eq(task), eq(end), any(), eq(0), any());
    }

    private Task task() {
        Task task = new Task();
        task.setId(7L);
        task.setFlowModelId(10L);
        task.setTaskStatus("RUNNING");
        task.setTaskVariables(JsonNodeSupport.objectNode());
        return task;
    }

    private FlowNode flowNode(Long id, Long modelId, Long ref, String functionType) {
        FlowNode node = new FlowNode();
        node.setId(id);
        node.setFlowModelId(modelId);
        node.setNodeIdRef(ref);
        node.setNodeType("FUNCTIONAL_NODE");
        var capability = JsonNodeSupport.objectNode();
        if (functionType != null) capability.put("functionType", functionType);
        node.setCapability(capability);
        var interfaces = JsonNodeSupport.arrayNode();
        if (!"END".equals(functionType)) interfaces.addObject().put("name", "flow_out").put("direction", "OUT");
        if (!"START".equals(functionType)) interfaces.addObject().put("name", "flow_in").put("direction", "IN");
        node.setInterfaces(interfaces);
        node.setPorts(JsonNodeSupport.arrayNode());
        return node;
    }

    private TaskStep step(Task task, FlowNode node, String status) {
        TaskStep step = new TaskStep();
        step.setId(node.getId() + 1000);
        step.setTaskId(task.getId());
        step.setFlowNodeId(node.getId());
        step.setNodeIdRef(node.getNodeIdRef());
        step.setNodeStatus(status);
        step.setStepDepth(0);
        return step;
    }

    private WorkflowDefinitionCompiler.CompiledWorkflow compiled() {
        WorkflowSaveRequest request = new WorkflowSaveRequest();
        request.setName("test");
        var definitions = JsonNodeSupport.arrayNode();
        definitions.add(definition(1, "FUNCTIONAL_NODE", "START"));
        var device = definition(2, "DEVICE_CAPABILITY_NODE", null);
        device.with("capability").put("deviceModelRef", 5).put("capabilityRef", "heat");
        definitions.add(device);
        definitions.add(definition(3, "FUNCTIONAL_NODE", "END"));
        request.setNodesDef(definitions);
        request.setInterfaceConnections(JsonNodeSupport.arrayNode()
                .add(connection(1, 2)).add(connection(2, 3)));
        request.setPortConnections(JsonNodeSupport.arrayNode());
        var protocol = new com.smartlab.global.protocol.ProtocolDictionaryService();
        return new WorkflowDefinitionCompiler(
                protocol,
                new com.smartlab.global.schema.SchemaMetadataService(protocol)).compile(request);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode definition(long ref, String type, String function) {
        var node = JsonNodeSupport.objectNode();
        node.put("nodeIdRef", ref).put("nodeType", type).put("name", "node" + ref);
        var capability = node.putObject("capability");
        if (function != null) capability.put("functionType", function);
        var interfaces = node.putArray("interfaces");
        if (!"START".equals(function)) interfaces.addObject().put("name", "flow_in").put("direction", "IN");
        if (!"END".equals(function)) interfaces.addObject().put("name", "flow_out").put("direction", "OUT");
        node.set("ports", JsonNodeSupport.arrayNode());
        var actions = node.putArray("actions");
        if ("DEVICE_CAPABILITY_NODE".equals(type)) actions.addObject().put("actionName", "EMIT_SIGNAL")
                .putObject("payload").put("interfaceType", "WORKFLOW").put("signalName", "WF_EXECUTE_START");
        return node;
    }

    private com.fasterxml.jackson.databind.node.ObjectNode connection(long source, long target) {
        var value = JsonNodeSupport.objectNode();
        value.putObject("source").put("nodeIdRef", source).put("interfaceName", "flow_out");
        value.putObject("target").put("nodeIdRef", target).put("interfaceName", "flow_in");
        return value;
    }
}
