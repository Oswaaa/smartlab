package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.constraint.ConstraintExpressionEvaluator;
import com.smartlab.engine.observation.ObservableKey;
import com.smartlab.engine.observation.ObservableSnapshotReader;
import com.smartlab.engine.observation.ObservationSample;
import com.smartlab.engine.workflow.action.UpdateWorkflowActionExecutor;
import com.smartlab.engine.workflow.action.WorkflowActionContext;
import com.smartlab.engine.workflow.action.WorkflowActionDefinition;
import com.smartlab.engine.workflow.action.WorkflowActionExecutor;
import com.smartlab.engine.workflow.action.WorkflowActionRegistry;
import com.smartlab.engine.workflow.action.WorkflowActionResult;
import com.smartlab.global.contract.ObservableObjectType;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.workflow.FlowNodeService;
import com.smartlab.management.service.db.workflow.WorkflowRuntimeService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class WorkflowEngineExpressionHistoryTest {
    private final WorkflowRuntimeService runtime = mock(WorkflowRuntimeService.class);
    private final WorkflowExecutionOperations operations = mock(WorkflowExecutionOperations.class);
    private final WorkflowService workflows = mock(WorkflowService.class);
    private final FlowNodeService flowNodes = mock(FlowNodeService.class);
    private final ObservableSnapshotReader observations = mock(ObservableSnapshotReader.class);

    private final WorkflowEngine engine = new WorkflowEngine(runtime, workflows, flowNodes,
            new WorkflowConditionEvaluator(), new ConstraintExpressionEvaluator(),
            new WorkflowActionRegistry(List.of(stubAction("EMIT"), stubAction("UPDATE"))),
            operations, observations);

    @Test
    void rateOnPortFedDeviceVariableUsesLiveAttributeHistoryNotBranchSlot() {
        Task task = task();
        FlowNode heater = deviceNode();
        FlowNode branch = branchNode("temp_rate = rate(temp, 30)");
        TaskStep heaterStep = heaterStep("SUCCEEDED");
        TaskStep branchStep = branchStep(0);
        ObservableKey attributeKey = new ObservableKey(ObservableObjectType.DEVICE_ATTRIBUTE,
                99L, null, null, null, null, "Temperature", null);
        ObservableKey branchTempKey = new ObservableKey(ObservableObjectType.NODE_INTERNAL_VARIABLE,
                null, 1L, task.getId(), null, "branch", null, "temp");
        Instant now = Instant.now();
        stubFlow(task, heater, branch, heaterStep, branchStep);
        when(operations.resolveDeviceInstance(eq(task), eq(heaterStep), eq(heater))).thenReturn(99L);
        when(operations.resolveMappedVariables(task, heaterStep, heater))
                .thenReturn(JsonNodeSupport.objectNode().put("temp", 32));
        when(observations.readHistory(any(), any())).thenReturn(List.of());
        when(observations.readHistory(eq(attributeKey), any())).thenReturn(List.of(
                new ObservationSample(attributeKey, JsonNodeSupport.toNode(22), now.minusSeconds(10), 1)));
        when(observations.readHistory(eq(branchTempKey), any())).thenReturn(List.of(
                new ObservationSample(branchTempKey, JsonNodeSupport.toNode(0), now.minusMillis(3), 1)));

        engine.processTask(task);

        assertThat(branchStep.getVariableSpace().path("temp_rate").asDouble()).isCloseTo(1.0, within(0.15));
        verify(observations).readHistory(eq(attributeKey), any());
        verify(observations, never()).readHistory(eq(branchTempKey), any());
    }

    @Test
    void rateWithoutPortUsesEvaluatingNodeVariableHistory() {
        Task task = task();
        FlowNode branch = branchNode("temp_rate = rate(temp, 30)");
        branch.setPorts(JsonNodeSupport.arrayNode());
        TaskStep branchStep = branchStep(20);
        ObservableKey branchTempKey = new ObservableKey(ObservableObjectType.NODE_INTERNAL_VARIABLE,
                null, 1L, task.getId(), null, "branch", null, "temp");
        Instant now = Instant.now();
        when(runtime.steps(task.getId())).thenReturn(List.of(branchStep));
        when(runtime.task(task.getId())).thenReturn(task);
        when(flowNodes.getById(branch.getId())).thenReturn(branch);
        when(operations.resolveMappedVariables(task, branchStep, branch)).thenReturn(JsonNodeSupport.objectNode());
        when(workflows.getDefinition(1L)).thenReturn(definition(JsonNodeSupport.arrayNode()));
        when(workflows.compileDefinition(1L)).thenReturn(compiled(Map.of("branch", 2L)));
        persistMerges();
        when(observations.readHistory(any(), any())).thenReturn(List.of());
        when(observations.readHistory(eq(branchTempKey), any())).thenReturn(List.of(
                new ObservationSample(branchTempKey, JsonNodeSupport.toNode(12), now.minusSeconds(10), 1)));

        engine.processTask(task);

        assertThat(branchStep.getVariableSpace().path("temp_rate").asDouble()).isCloseTo(0.8, within(0.15));
        verify(observations).readHistory(eq(branchTempKey), any());
        verify(operations, never()).resolveDeviceInstance(any(), any(), any());
    }

    @Test
    void rateOnPortFedUnmappedVariableUsesSourceNodeHistory() {
        Task task = task();
        FlowNode heater = deviceNode();
        heater.setInVariables(JsonNodeSupport.arrayNode()
                .add(JsonNodeSupport.objectNode().put("name", "temp").put("dataType", "DOUBLE")));
        FlowNode branch = branchNode("temp_rate = rate(temp, 30)");
        TaskStep heaterStep = heaterStep("SUCCEEDED");
        ((ObjectNode) heaterStep.getVariableSpace()).put("temp", 32);
        TaskStep branchStep = branchStep(0);
        ObservableKey sourceTempKey = new ObservableKey(ObservableObjectType.NODE_INTERNAL_VARIABLE,
                null, 1L, task.getId(), null, "heater", null, "temp");
        ObservableKey branchTempKey = new ObservableKey(ObservableObjectType.NODE_INTERNAL_VARIABLE,
                null, 1L, task.getId(), null, "branch", null, "temp");
        Instant now = Instant.now();
        stubFlow(task, heater, branch, heaterStep, branchStep);
        when(operations.resolveMappedVariables(task, heaterStep, heater)).thenReturn(JsonNodeSupport.objectNode());
        when(observations.readHistory(any(), any())).thenReturn(List.of());
        when(observations.readHistory(eq(sourceTempKey), any())).thenReturn(List.of(
                new ObservationSample(sourceTempKey, JsonNodeSupport.toNode(16), now.minusSeconds(10), 1)));
        when(observations.readHistory(eq(branchTempKey), any())).thenReturn(List.of(
                new ObservationSample(branchTempKey, JsonNodeSupport.toNode(0), now.minusMillis(3), 1)));

        engine.processTask(task);

        assertThat(branchStep.getVariableSpace().path("temp_rate").asDouble()).isCloseTo(1.6, within(0.15));
        verify(observations).readHistory(eq(sourceTempKey), any());
        verify(observations, never()).readHistory(eq(branchTempKey), any());
        verify(operations, never()).resolveDeviceInstance(any(), any(), any());
    }

    @Test
    void updateValueExpressionUsesSamePortAttributeHistoryAsNodeExpression() {
        Task task = task();
        FlowNode heater = deviceNode();
        FlowNode branch = branchNode("");
        branch.setCapability(JsonNodeSupport.objectNode().put("functionType", "BRANCH"));
        ObjectNode output = JsonNodeSupport.objectNode();
        output.put("name", "branch-out");
        output.put("direction", "OUT");
        output.put("interfaceType", "WORKFLOW");
        output.putArray("allowedSignals").add("ACTIVE");
        ObjectNode trigger = output.putArray("bindingTriggers").addObject();
        trigger.putObject("condition").put("object", "nodeLifecycleState")
                .put("operator", "=").put("threshold", "RUNNING");
        trigger.putObject("action").put("actionName", "UPDATE").putObject("payload")
                .put("updateType", "INTERNAL_VARIABLE")
                .put("targetName", "temp_rate")
                .put("valueExpression", "rate(temp, 30)");
        branch.setInterfaces(JsonNodeSupport.arrayNode().add(output));
        branch.setActions(JsonNodeSupport.arrayNode().add("UPDATE"));
        TaskStep heaterStep = heaterStep("SUCCEEDED");
        TaskStep branchStep = branchStep(0);
        branchStep.setInterfaceOutSnapshot(WorkflowInterfaceSnapshots.initialize(branch.getInterfaces(), "OUT"));
        ObservableKey attributeKey = new ObservableKey(ObservableObjectType.DEVICE_ATTRIBUTE,
                99L, null, null, null, null, "Temperature", null);
        ObservableKey branchTempKey = new ObservableKey(ObservableObjectType.NODE_INTERNAL_VARIABLE,
                null, 1L, task.getId(), null, "branch", null, "temp");
        Instant now = Instant.now();
        stubFlow(task, heater, branch, heaterStep, branchStep);
        when(operations.resolveDeviceInstance(eq(task), eq(heaterStep), eq(heater))).thenReturn(99L);
        when(operations.resolveMappedVariables(task, heaterStep, heater))
                .thenReturn(JsonNodeSupport.objectNode().put("temp", 32));
        when(observations.readHistory(any(), any())).thenReturn(List.of());
        when(observations.readHistory(eq(attributeKey), any())).thenReturn(List.of(
                new ObservationSample(attributeKey, JsonNodeSupport.toNode(22), now.minusSeconds(10), 1)));
        when(observations.readHistory(eq(branchTempKey), any())).thenReturn(List.of(
                new ObservationSample(branchTempKey, JsonNodeSupport.toNode(0), now.minusMillis(3), 1)));
        ConstraintExpressionEvaluator evaluator = new ConstraintExpressionEvaluator();
        WorkflowEngine updateEngine = new WorkflowEngine(runtime, workflows, flowNodes,
                new WorkflowConditionEvaluator(), evaluator,
                new WorkflowActionRegistry(List.of(
                        stubAction("EMIT"),
                        new UpdateWorkflowActionExecutor(evaluator,
                                new WorkflowExpressionHistoryResolver(workflows, flowNodes, runtime, operations,
                                        observations, evaluator)))),
                operations, observations);

        updateEngine.processTask(task);

        assertThat(branchStep.getVariableSpace().path("temp_rate").asDouble()).isCloseTo(1.0, within(0.15));
        verify(observations).readHistory(eq(attributeKey), any());
        verify(observations, never()).readHistory(eq(branchTempKey), any());
    }

    private void stubFlow(Task task, FlowNode heater, FlowNode branch, TaskStep heaterStep, TaskStep branchStep) {
        when(runtime.steps(task.getId())).thenReturn(List.of(heaterStep, branchStep));
        when(runtime.task(task.getId())).thenReturn(task);
        when(flowNodes.getById(heater.getId())).thenReturn(heater);
        when(flowNodes.getById(branch.getId())).thenReturn(branch);
        when(operations.resolveMappedVariables(task, branchStep, branch)).thenReturn(JsonNodeSupport.objectNode());
        ObjectNode connection = JsonNodeSupport.objectNode();
        connection.set("source", JsonNodeSupport.objectNode().put("nodeName", "heater").put("portName", "tempOut"));
        connection.set("target", JsonNodeSupport.objectNode().put("nodeName", "branch").put("portName", "tempIn"));
        when(workflows.getDefinition(1L)).thenReturn(definition(JsonNodeSupport.arrayNode().add(connection)));
        when(workflows.compileDefinition(1L)).thenReturn(compiled(Map.of("heater", 1L, "branch", 2L)));
        persistMerges();
    }

    private void persistMerges() {
        doAnswer(invocation -> {
            TaskStep step = invocation.getArgument(0);
            ObjectNode merged = step.getVariableSpace() == null
                    ? JsonNodeSupport.objectNode() : (ObjectNode) step.getVariableSpace().deepCopy();
            invocation.<com.fasterxml.jackson.databind.JsonNode>getArgument(1).fields()
                    .forEachRemaining(entry -> merged.set(entry.getKey(), entry.getValue().deepCopy()));
            step.setVariableSpace(merged);
            return null;
        }).when(runtime).mergeVariableSpace(any(), any());
        doAnswer(invocation -> {
            invocation.<TaskStep>getArgument(0).setPortInSnapshot(invocation.getArgument(1));
            return null;
        }).when(runtime).updatePortInSnapshot(any(), any());
        doAnswer(invocation -> {
            invocation.<TaskStep>getArgument(0).setPortOutSnapshot(invocation.getArgument(1));
            return null;
        }).when(runtime).updatePortOutSnapshot(any(), any());
    }

    private WorkflowDetailResponse definition(ArrayNode connections) {
        WorkflowDetailResponse definition = new WorkflowDetailResponse();
        definition.setPortConnections(connections);
        return definition;
    }

    private WorkflowDefinitionCompiler.CompiledWorkflow compiled(Map<String, Long> refs) {
        return new WorkflowDefinitionCompiler.CompiledWorkflow(
                Map.of(), Map.of(), Map.of(), refs, 1L, 2L);
    }

    private WorkflowActionExecutor stubAction(String name) {
        return new WorkflowActionExecutor() {
            @Override
            public String actionName() {
                return name;
            }

            @Override
            public WorkflowActionResult execute(WorkflowActionDefinition action, WorkflowActionContext context) {
                return WorkflowActionResult.continueExecution();
            }
        };
    }

    private Task task() {
        Task task = new Task();
        task.setId(9L);
        task.setTaskStatus("RUNNING");
        return task;
    }

    private FlowNode deviceNode() {
        FlowNode node = new FlowNode();
        node.setId(21L);
        node.setFlowModelId(1L);
        node.setNodeIdRef(1L);
        node.setNodeType("DEV_NODE");
        node.setDeviceModelId(7L);
        node.setInVariables(JsonNodeSupport.arrayNode()
                .add(JsonNodeSupport.objectNode().put("name", "temp").put("dataType", "DOUBLE")
                        .put("attributesMapping", "Temperature")));
        node.setPorts(JsonNodeSupport.arrayNode()
                .add(JsonNodeSupport.objectNode().put("name", "tempOut").put("direction", "OUT")
                        .put("internalVariableName", "temp")));
        node.setInterfaces(JsonNodeSupport.arrayNode());
        node.setActions(JsonNodeSupport.arrayNode());
        return node;
    }

    private FlowNode branchNode(String expression) {
        FlowNode node = new FlowNode();
        node.setId(22L);
        node.setFlowModelId(1L);
        node.setNodeIdRef(2L);
        node.setNodeType("FUNC_NODE");
        node.setCapability(JsonNodeSupport.objectNode()
                .put("functionType", "BRANCH")
                .put("expression", expression));
        node.setInVariables(JsonNodeSupport.arrayNode()
                .add(JsonNodeSupport.objectNode().put("name", "temp").put("dataType", "DOUBLE"))
                .add(JsonNodeSupport.objectNode().put("name", "temp_rate").put("dataType", "DOUBLE")));
        node.setPorts(JsonNodeSupport.arrayNode()
                .add(JsonNodeSupport.objectNode().put("name", "tempIn").put("direction", "IN")
                        .put("internalVariableName", "temp")));
        node.setInterfaces(JsonNodeSupport.arrayNode());
        node.setActions(JsonNodeSupport.arrayNode());
        return node;
    }

    private TaskStep heaterStep(String status) {
        TaskStep step = new TaskStep();
        step.setId(31L);
        step.setTaskId(9L);
        step.setFlowNodeId(21L);
        step.setNodeIdRef(1L);
        step.setStepDepth(0);
        step.setNodeStatus(status);
        step.setVariableSpace(JsonNodeSupport.objectNode()
                .set("_triggerStates", JsonNodeSupport.objectNode()
                        .put(WorkflowTriggerState.TERMINAL_OBSERVED_KEY, true)));
        step.setInterfaceInSnapshot(JsonNodeSupport.arrayNode());
        step.setInterfaceOutSnapshot(JsonNodeSupport.arrayNode());
        return step;
    }

    private TaskStep branchStep(double temp) {
        TaskStep step = new TaskStep();
        step.setId(32L);
        step.setTaskId(9L);
        step.setFlowNodeId(22L);
        step.setNodeIdRef(2L);
        step.setStepDepth(0);
        step.setNodeStatus("RUNNING");
        step.setVariableSpace(JsonNodeSupport.objectNode().put("temp", temp));
        step.setInterfaceInSnapshot(JsonNodeSupport.arrayNode());
        step.setInterfaceOutSnapshot(JsonNodeSupport.arrayNode());
        return step;
    }
}
