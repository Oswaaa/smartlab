package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.constraint.ConstraintExpressionEvaluator;
import com.smartlab.engine.workflow.action.WorkflowActionContext;
import com.smartlab.engine.workflow.action.WorkflowActionDefinition;
import com.smartlab.engine.workflow.action.WorkflowActionExecutor;
import com.smartlab.engine.workflow.action.WorkflowActionRegistry;
import com.smartlab.engine.workflow.action.WorkflowActionResult;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.workflow.FlowNodeService;
import com.smartlab.management.service.db.workflow.TaskService;
import com.smartlab.management.service.db.workflow.WorkflowRuntimeService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowEngineExecutionTest {

    private final WorkflowRuntimeService runtime = mock(WorkflowRuntimeService.class);
    private final WorkflowExecutionOperations operations = mock(WorkflowExecutionOperations.class);
    private final WorkflowService workflows = mock(WorkflowService.class);
    private final FlowNodeService flowNodes = mock(FlowNodeService.class);
    private final List<String> executionOrder = new ArrayList<>();
    private final WorkflowActionRegistry registry = new WorkflowActionRegistry(List.of(
            executor("UPDATE", WorkflowActionResult.continueWith(JsonNodeSupport.objectNode().put("temperature", 28))),
            executor("EMIT", WorkflowActionResult.emitWorkflowSignal("Interface_workflow_out", "ACTIVE"))));
    private final WorkflowEngine engine = new WorkflowEngine(runtime, workflows,
            flowNodes, new WorkflowConditionEvaluator(), new ConstraintExpressionEvaluator(),
            registry, operations, mock(TaskService.class));

    @Test
    void pendingStepIsPolledWithoutEngineAutoStart() {
        Task task = pollingTask();
        TaskStep step = pollingStep("PENDING");
        FlowNode node = pollingNode(List.of(triggerInterface("out", "OUT", "out")));
        stubPoll(task, step, node);

        engine.processTask(task);

        verify(runtime, never()).startStep(task, step);
        assertThat(executionOrder).containsExactly("out");
    }

    @Test
    void pollsAllOutputInterfacesBeforeAllInputInterfacesInStableOrder() {
        Task task = pollingTask();
        TaskStep step = pollingStep("RUNNING");
        FlowNode node = pollingNode(List.of(
                triggerInterface("in-a", "IN", "in-a"),
                triggerInterface("out-a", "OUT", "out-a"),
                triggerInterface("in-b", "IN", "in-b"),
                triggerInterface("out-b", "OUT", "out-b")));
        stubPoll(task, step, node);

        engine.processTask(task);

        assertThat(executionOrder).containsExactly("out-a", "out-b", "in-a", "in-b");
    }

    @Test
    void insertingUnrelatedTriggerDoesNotRetriggerExistingTrueCondition() {
        Task task = pollingTask();
        TaskStep step = pollingStep("RUNNING");
        ObjectNode output = triggerInterface("out", "OUT", "original");
        FlowNode node = pollingNode(List.of(output));
        stubPoll(task, step, node);

        engine.processTask(task);

        ArgumentCaptor<com.fasterxml.jackson.databind.JsonNode> updates =
                ArgumentCaptor.forClass(com.fasterxml.jackson.databind.JsonNode.class);
        verify(runtime, atLeastOnce()).mergeVariableSpace(eq(step), updates.capture());
        ObjectNode persistedStates = updates.getAllValues().stream()
                .filter(item -> item.path("_triggerStates").isObject())
                .map(item -> (ObjectNode) item.path("_triggerStates").deepCopy())
                .reduce((first, second) -> second)
                .orElseThrow();
        step.setVariableSpace(JsonNodeSupport.objectNode().put("ready", true).set("_triggerStates", persistedStates));
        ObjectNode inserted = triggerInterface("unused", "OUT", "inserted")
                .withArray("bindingTriggers").get(0).deepCopy();
        ((ObjectNode) inserted.path("condition")).put("object", "missing");
        ((ArrayNode) output.path("bindingTriggers")).insert(0, inserted);
        executionOrder.clear();
        clearInvocations(runtime);
        stubPoll(task, step, node);

        engine.processTask(task);

        assertThat(executionOrder).isEmpty();
    }

    @Test
    void executesActionsImmediatelyInTriggerOrderWithoutReordering() {
        Task task = new Task();
        TaskStep step = new TaskStep();
        FlowNode node = new FlowNode();
        ArrayNode actions = JsonNodeSupport.arrayNode();
        actions.add(action("EMIT"));
        actions.add(action("UPDATE"));

        engine.executeActions(task, step, node, actions, JsonNodeSupport.objectNode());

        assertThat(executionOrder).containsExactly("EMIT", "UPDATE");
        verify(runtime).mergeVariableSpace(eq(step), eq(JsonNodeSupport.objectNode().put("temperature", 28)));
    }

    @Test
    void executesEveryEmitActionThatWasTriggered() {
        Task task = new Task();
        TaskStep step = new TaskStep();
        FlowNode node = new FlowNode();
        ArrayNode actions = JsonNodeSupport.arrayNode();
        actions.add(action("EMIT"));
        actions.add(action("EMIT"));

        engine.executeActions(task, step, node, actions, JsonNodeSupport.objectNode());

        assertThat(executionOrder).containsExactly("EMIT", "EMIT");
    }

    @Test
    void copiesStrictlyTypedSourcePortValueIntoTargetVariable() {
        WorkflowService workflows = mock(WorkflowService.class);
        WorkflowEngine portEngine = engine(workflows);
        FlowNode source = nodeWithPort(1L, 1L, "measured", "DOUBLE", "temperatureOut", "OUT");
        FlowNode target = nodeWithPort(1L, 2L, "target", "DOUBLE", "temperatureIn", "IN");
        TaskStep step = new TaskStep();
        step.setTaskId(9L);
        step.setVariableSpace(JsonNodeSupport.objectNode().put("measured", 26.5));
        stubPortConnection(workflows);

        ObjectNode values = portEngine.mapPortValues(source, target, step);

        assertThat(values.path("target").decimalValue()).isEqualByComparingTo("26.5");
    }

    @Test
    void logsAndSkipsUnsetSourcePortValue() {
        WorkflowService workflows = mock(WorkflowService.class);
        WorkflowEngine portEngine = engine(workflows);
        FlowNode source = nodeWithPort(1L, 1L, "measured", "DOUBLE", "temperatureOut", "OUT");
        FlowNode target = nodeWithPort(1L, 2L, "target", "DOUBLE", "temperatureIn", "IN");
        TaskStep step = new TaskStep();
        step.setTaskId(9L);
        step.setId(12L);
        step.setVariableSpace(JsonNodeSupport.objectNode());
        Task task = new Task();
        task.setId(9L);
        when(runtime.task(9L)).thenReturn(task);
        stubPortConnection(workflows);

        assertThat(portEngine.mapPortValues(source, target, step)).isEmpty();
        verify(runtime).appendStepLog(eq(task), eq(step), eq("WARN"),
                eq("端口temperatureOut绑定变量measured尚无值"));
    }

    private WorkflowActionExecutor executor(String actionType, WorkflowActionResult result) {
        return new WorkflowActionExecutor() {
            @Override
            public String actionName() {
                return actionType;
            }

            @Override
            public WorkflowActionResult execute(WorkflowActionDefinition action, WorkflowActionContext context) {
                executionOrder.add(action.payload().path("trace").asText(action.actionName()));
                return result;
            }
        };
    }

    private ObjectNode action(String type) {
        ObjectNode action = JsonNodeSupport.objectNode();
        action.put("actionName", type);
        ObjectNode payload = action.putObject("payload");
        if ("EMIT".equals(type)) {
            payload.put("targetInterfaceName", "Interface_workflow_out");
            payload.put("signalName", "ACTIVE");
        } else {
            payload.put("updateType", "INTERNAL_VARIABLE");
            payload.put("targetName", "temperature");
            payload.put("value", 28);
        }
        return action;
    }

    private Task pollingTask() {
        Task task = new Task();
        task.setId(9L);
        task.setTaskStatus("RUNNING");
        return task;
    }

    private TaskStep pollingStep(String status) {
        TaskStep step = new TaskStep();
        step.setId(12L);
        step.setTaskId(9L);
        step.setFlowNodeId(7L);
        step.setNodeIdRef(2L);
        step.setNodeStatus(status);
        step.setVariableSpace(JsonNodeSupport.objectNode().put("ready", true));
        step.setInterfaceInSnapshot(JsonNodeSupport.objectNode()
                .put("targetInterfaceName", "in-a")
                .put("inputSignalName", "ACTIVE")
                .set("inputPayload", JsonNodeSupport.objectNode()));
        return step;
    }

    private FlowNode pollingNode(List<ObjectNode> interfaces) {
        FlowNode node = new FlowNode();
        node.setId(7L);
        node.setFlowModelId(3L);
        node.setNodeIdRef(2L);
        node.setNodeType("DEV_NODE");
        ArrayNode definitions = JsonNodeSupport.arrayNode();
        interfaces.forEach(definitions::add);
        node.setInterfaces(definitions);
        node.setActions(JsonNodeSupport.arrayNode().add("UPDATE"));
        node.setInVariables(JsonNodeSupport.arrayNode()
                .add(JsonNodeSupport.objectNode().put("name", "ready").put("dataType", "BOOLEAN")));
        return node;
    }

    private ObjectNode triggerInterface(String name, String direction, String trace) {
        ObjectNode item = JsonNodeSupport.objectNode();
        item.put("name", name);
        item.put("direction", direction);
        item.put("interfaceType", "WORKFLOW");
        item.putArray("allowedSignals").add("ACTIVE");
        ObjectNode trigger = item.putArray("bindingTriggers").addObject();
        trigger.putObject("condition").put("object", "ready").put("operator", "=").put("threshold", true);
        trigger.putObject("action").put("actionName", "UPDATE").putObject("payload")
                .put("updateType", "INTERNAL_VARIABLE").put("targetName", "ready")
                .put("value", true).put("trace", trace);
        return item;
    }

    private void stubPoll(Task task, TaskStep step, FlowNode node) {
        when(runtime.activeSteps(task.getId())).thenReturn(List.of(step));
        when(runtime.task(task.getId())).thenReturn(task);
        when(flowNodes.getById(step.getFlowNodeId())).thenReturn(node);
        when(operations.resolveMappedVariables(task, step, node)).thenReturn(JsonNodeSupport.objectNode());
    }

    private WorkflowEngine engine(WorkflowService workflows) {
        return new WorkflowEngine(runtime, workflows, mock(FlowNodeService.class),
                new WorkflowConditionEvaluator(), new ConstraintExpressionEvaluator(), registry, operations,
                mock(TaskService.class));
    }

    private FlowNode nodeWithPort(long flowModelId, long nodeRef, String variableName, String dataType,
                                  String portName, String direction) {
        FlowNode node = new FlowNode();
        node.setFlowModelId(flowModelId);
        node.setNodeIdRef(nodeRef);
        ArrayNode variables = JsonNodeSupport.arrayNode();
        variables.addObject().put("name", variableName).put("dataType", dataType);
        node.setInVariables(variables);
        ArrayNode ports = JsonNodeSupport.arrayNode();
        ports.addObject().put("name", portName).put("direction", direction)
                .put("internalVariableName", variableName);
        node.setPorts(ports);
        return node;
    }

    private void stubPortConnection(WorkflowService workflows) {
        ObjectNode connection = JsonNodeSupport.objectNode();
        connection.set("source", JsonNodeSupport.objectNode()
                .put("nodeName", "source").put("portName", "temperatureOut"));
        connection.set("target", JsonNodeSupport.objectNode()
                .put("nodeName", "target").put("portName", "temperatureIn"));
        WorkflowDetailResponse definition = new WorkflowDetailResponse();
        definition.setPortConnections(JsonNodeSupport.arrayNode().add(connection));
        when(workflows.getDefinition(1L)).thenReturn(definition);
        when(workflows.compileDefinition(1L)).thenReturn(new WorkflowDefinitionCompiler.CompiledWorkflow(
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(),
                java.util.Map.of("source", 1L, "target", 2L), 1L, 2L));
    }
}
