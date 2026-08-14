package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.constraint.ConstraintExpressionEvaluator;
import com.smartlab.engine.statemachine.StateMachineInterfaceSignalEvent;
import com.smartlab.engine.workflow.action.WorkflowActionContext;
import com.smartlab.engine.workflow.action.WorkflowActionDefinition;
import com.smartlab.engine.workflow.action.WorkflowActionExecutor;
import com.smartlab.engine.workflow.action.WorkflowActionRegistry;
import com.smartlab.engine.workflow.action.WorkflowActionResult;
import com.smartlab.engine.workflow.action.UpdateWorkflowActionExecutor;
import com.smartlab.engine.workflow.action.WorkflowValueResolver;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.workflow.FlowNodeService;
import com.smartlab.management.service.db.workflow.WorkflowRuntimeService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowEngineExecutionTest {

    @Test
    void nodeAssignmentResultIsPersistedAndVisibleToTriggersInTheSamePoll() {
        Task task = pollingTask();
        TaskStep step = pollingStep("RUNNING");
        step.setVariableSpace(JsonNodeSupport.objectNode().put("temperature", 2.0));
        ObjectNode output = triggerInterface("branch-out", "OUT", "calculated");
        output.path("bindingTriggers").get(0).path("condition").deepCopy();
        ((ObjectNode) output.path("bindingTriggers").get(0).path("condition"))
                .put("object", "scaled").put("operator", "=").put("threshold", 200.0);
        FlowNode node = pollingNode(List.of(output));
        node.setNodeType("FUNC_NODE");
        node.setCapability(JsonNodeSupport.objectNode()
                .put("functionType", "BRANCH")
                .put("expression", "scaled = temperature * 100"));
        node.setInVariables(JsonNodeSupport.arrayNode()
                .add(JsonNodeSupport.objectNode().put("name", "temperature").put("dataType", "DOUBLE"))
                .add(JsonNodeSupport.objectNode().put("name", "scaled").put("dataType", "DOUBLE")));
        stubPoll(task, step, node);
        when(workflows.compileDefinition(node.getFlowModelId())).thenReturn(new WorkflowDefinitionCompiler.CompiledWorkflow(
                Map.of(), Map.of(), Map.of(), Map.of("candidate", node.getNodeIdRef()), 1L, 2L));
        doAnswer(invocation -> {
            ObjectNode merged = (ObjectNode) step.getVariableSpace().deepCopy();
            merge(merged, invocation.getArgument(1));
            step.setVariableSpace(merged);
            return null;
        }).when(runtime).mergeVariableSpace(eq(step), org.mockito.ArgumentMatchers.any());

        engine.processTask(task);

        assertThat(step.getVariableSpace().path("scaled").asDouble()).isEqualTo(200.0);
        assertThat(executionOrder).containsExactly("calculated");
    }

    @Test
    void lifecycleUpdateBecomesVisibleToEmitTriggerOnFollowingPollOnly() {
        Task task = pollingTask();
        TaskStep step = pollingStep("RUNNING");
        FlowNode node = pollingNode(List.of(
                lifecycleTriggerInterface("workflow-out", "OUT", "SUCCEEDED", "EMIT"),
                triggerInterface("workflow-in", "IN", "UPDATE")));
        step.setInterfaceInSnapshot(WorkflowInterfaceSnapshots.initialize(node.getInterfaces(), "IN"));
        step.setInterfaceOutSnapshot(WorkflowInterfaceSnapshots.initialize(node.getInterfaces(), "OUT"));
        node.setActions(JsonNodeSupport.arrayNode().add("UPDATE").add("EMIT"));
        WorkflowActionRegistry lifecycleRegistry = new WorkflowActionRegistry(List.of(
                mutatingLifecycleExecutor(),
                executor("EMIT", WorkflowActionResult.emitWorkflowSignal("workflow-out", "ACTIVE"))));
        WorkflowEngine lifecycleEngine = new WorkflowEngine(runtime, workflows, flowNodes,
                new WorkflowConditionEvaluator(), new ConstraintExpressionEvaluator(), lifecycleRegistry,
                operations);
        when(runtime.pollableSteps(task.getId())).thenReturn(List.of(step));
        when(runtime.task(task.getId())).thenReturn(task);
        when(flowNodes.getById(step.getFlowNodeId())).thenReturn(node);
        when(operations.resolveMappedVariables(task, step, node)).thenReturn(JsonNodeSupport.objectNode());
        when(workflows.compileDefinition(node.getFlowModelId())).thenReturn(emptyCompiledWorkflow());
        doAnswer(invocation -> {
            ObjectNode merged = step.getVariableSpace() == null
                    ? JsonNodeSupport.objectNode() : (ObjectNode) step.getVariableSpace().deepCopy();
            merge(merged, invocation.getArgument(1));
            step.setVariableSpace(merged);
            return null;
        }).when(runtime).mergeVariableSpace(eq(step), org.mockito.ArgumentMatchers.any());

        lifecycleEngine.processTask(task);
        assertThat(executionOrder).containsExactly("UPDATE");

        lifecycleEngine.processTask(task);
        assertThat(executionOrder).containsExactly("UPDATE", "EMIT");
        assertThat(step.getVariableSpace().path("_triggerStates")
                .path(WorkflowTriggerState.TERMINAL_OBSERVED_KEY).asBoolean()).isTrue();
    }

    @Test
    void consecutiveUpdatesUseLatestVariableSpaceWhileConditionsRemainFrozen() {
        Task task = pollingTask();
        TaskStep step = pollingStep("RUNNING");
        step.setVariableSpace(JsonNodeSupport.objectNode().put("aggregateCount", 0));
        ObjectNode firstInput = incrementTriggerInterface("aggregate-in-1");
        ObjectNode secondInput = incrementTriggerInterface("aggregate-in-2");
        FlowNode node = pollingNode(List.of(firstInput, secondInput));
        node.setNodeType("FUNC_NODE");
        node.setCapability(JsonNodeSupport.objectNode().put("functionType", "AGGREGATE"));
        node.setInVariables(JsonNodeSupport.arrayNode()
                .add(JsonNodeSupport.objectNode().put("name", "aggregateCount").put("dataType", "INTEGER")));
        step.setInterfaceInSnapshot(JsonNodeSupport.arrayNode()
                .add(JsonNodeSupport.objectNode().put("interfaceName", "aggregate-in-1").put("signalName", "ACTIVE"))
                .add(JsonNodeSupport.objectNode().put("interfaceName", "aggregate-in-2").put("signalName", "ACTIVE")));
        WorkflowActionRegistry latestValueRegistry = new WorkflowActionRegistry(List.of(
                new UpdateWorkflowActionExecutor(new WorkflowValueResolver(new ConstraintExpressionEvaluator())),
                executor("EMIT", WorkflowActionResult.continueExecution())));
        WorkflowEngine latestValueEngine = new WorkflowEngine(runtime, workflows, flowNodes,
                new WorkflowConditionEvaluator(), new ConstraintExpressionEvaluator(), latestValueRegistry,
                operations);
        stubPoll(task, step, node);
        when(workflows.compileDefinition(node.getFlowModelId())).thenReturn(emptyCompiledWorkflow());
        doAnswer(invocation -> {
            ObjectNode merged = (ObjectNode) step.getVariableSpace().deepCopy();
            merge(merged, invocation.getArgument(1));
            step.setVariableSpace(merged);
            return null;
        }).when(runtime).mergeVariableSpace(eq(step), org.mockito.ArgumentMatchers.any());

        latestValueEngine.processTask(task);

        verify(runtime, never()).failStep(eq(step), org.mockito.ArgumentMatchers.anyString());
        assertThat(step.getVariableSpace().path("aggregateCount").asInt()).isEqualTo(2);
    }

    @Test
    void terminalPollEvaluatesOutputTriggersButNotInputTriggers() {
        Task task = pollingTask();
        TaskStep step = pollingStep("SUCCEEDED");
        FlowNode node = pollingNode(List.of(
                lifecycleTriggerInterface("workflow-out", "OUT", "SUCCEEDED", "EMIT"),
                triggerInterface("workflow-in", "IN", "input-cleanup")));
        node.setActions(JsonNodeSupport.arrayNode().add("UPDATE").add("EMIT"));
        WorkflowActionRegistry terminalRegistry = new WorkflowActionRegistry(List.of(
                executor("UPDATE", WorkflowActionResult.continueExecution()),
                executor("EMIT", WorkflowActionResult.emitWorkflowSignal("workflow-out", "ACTIVE"))));
        WorkflowEngine terminalEngine = new WorkflowEngine(runtime, workflows, flowNodes,
                new WorkflowConditionEvaluator(), new ConstraintExpressionEvaluator(), terminalRegistry,
                operations);
        stubPoll(task, step, node);
        when(workflows.compileDefinition(node.getFlowModelId())).thenReturn(emptyCompiledWorkflow());

        terminalEngine.processTask(task);

        assertThat(executionOrder).containsExactly("EMIT");
    }

    @Test
    void stateMachineEventPersistsInputWithoutExecutingTriggerActions() {
        Task task = pollingTask();
        TaskStep step = pollingStep("RUNNING");
        FlowNode node = pollingNode(List.of(deviceStateInputInterface()));
        node.setDeviceModelId(21L);
        step.setInterfaceInSnapshot(WorkflowInterfaceSnapshots.initialize(node.getInterfaces(), "IN"));
        WorkflowDetailResponse definition = new WorkflowDetailResponse();
        ObjectNode connection = JsonNodeSupport.objectNode().put("connectionType", "DEVICE_TO_NODE");
        connection.set("source", JsonNodeSupport.objectNode()
                .put("deviceModelId", 21L).put("interfaceName", "Interface_state_out"));
        connection.set("target", JsonNodeSupport.objectNode()
                .put("nodeName", "device").put("interfaceName", "state-in"));
        definition.setInterfaceConnections(JsonNodeSupport.arrayNode().add(connection));
        ObjectNode signal = JsonNodeSupport.objectNode().put("signalName", "CMD_STATE");
        signal.set("payload", JsonNodeSupport.objectNode()
                .put("deviceModelId", 21L).put("deviceInstanceId", 99L).put("messageId", "m-1")
                .put("stateName", "RUNNING").put("timestamp", 1L));
        StateMachineInterfaceSignalEvent event = new StateMachineInterfaceSignalEvent(
                99L, "Interface_state_out", "STATE", signal,
                Map.of("messageId", "m-1", "taskStepId", step.getId()));
        when(runtime.step(step.getId())).thenReturn(step);
        when(runtime.task(task.getId())).thenReturn(task);
        when(flowNodes.getById(step.getFlowNodeId())).thenReturn(node);
        when(operations.resolveDeviceInstance(task, step, node)).thenReturn(99L);
        when(workflows.getDefinition(node.getFlowModelId())).thenReturn(definition);
        when(workflows.compileDefinition(node.getFlowModelId())).thenReturn(new WorkflowDefinitionCompiler.CompiledWorkflow(
                Map.of(), Map.of(), Map.of(), Map.of("device", node.getNodeIdRef()), 1L, 2L));
        doAnswer(invocation -> {
            step.setInterfaceInSnapshot(invocation.getArgument(1));
            return null;
        }).when(runtime).updateInputSnapshot(eq(step), org.mockito.ArgumentMatchers.any());

        engine.handleStateMachineSignal(event);

        ArgumentCaptor<com.fasterxml.jackson.databind.JsonNode> snapshot =
                ArgumentCaptor.forClass(com.fasterxml.jackson.databind.JsonNode.class);
        verify(runtime).updateInputSnapshot(eq(step), snapshot.capture());
        assertThat(snapshot.getValue().isArray()).isTrue();
        assertThat(WorkflowInterfaceSnapshots.find(snapshot.getValue(), "state-in")
                .path("signalName").asText()).isEqualTo("CMD_STATE");
        assertThat(WorkflowInterfaceSnapshots.find(snapshot.getValue(), "state-in")
                .path("payload").path("stateName").asText()).isEqualTo("RUNNING");
        assertThat(executionOrder).isEmpty();
        verify(runtime, never()).failStep(eq(step), org.mockito.ArgumentMatchers.anyString());

        when(runtime.pollableSteps(task.getId())).thenReturn(List.of(step));
        when(operations.resolveMappedVariables(task, step, node)).thenReturn(JsonNodeSupport.objectNode());
        engine.processTask(task);

        assertThat(executionOrder).containsExactly("UPDATE");
    }

    @Test
    void evaluatesEachTriggerAgainstItsOwnInterfaceCurrentValue() {
        Task task = pollingTask();
        TaskStep step = pollingStep("RUNNING");
        FlowNode node = pollingNode(List.of(
                signalTriggerInterface("workflow-in", "ACTIVE", "workflow-action"),
                signalTriggerInterface("state-in", "CMD_STATE", "state-action")));
        ArrayNode input = WorkflowInterfaceSnapshots.initialize(node.getInterfaces(), "IN");
        input = WorkflowInterfaceSnapshots.withSignal(
                input, node.getInterfaces(), "IN", "workflow-in", "ACTIVE", null);
        input = WorkflowInterfaceSnapshots.withSignal(
                input, node.getInterfaces(), "IN", "state-in", "CMD_STATE",
                JsonNodeSupport.objectNode().put("stateName", "COMPLETED"));
        step.setInterfaceInSnapshot(input);
        stubPoll(task, step, node);

        engine.processTask(task);

        assertThat(executionOrder).containsExactly("workflow-action", "state-action");
    }

    @Test
    void emittedWorkflowSignalUpdatesCanonicalSourceAndTargetInterfaceSlots() {
        Task task = pollingTask();
        TaskStep sourceStep = pollingStep("RUNNING");
        FlowNode sourceNode = pollingNode(List.of(
                lifecycleTriggerInterface("Interface_workflow_out", "OUT", "RUNNING", "EMIT")));
        sourceStep.setInterfaceOutSnapshot(WorkflowInterfaceSnapshots.initialize(sourceNode.getInterfaces(), "OUT"));
        FlowNode targetNode = pollingNode(List.of(
                signalTriggerInterface("Interface_workflow_in", "ACTIVE", "target")));
        targetNode.setId(8L);
        targetNode.setNodeIdRef(3L);
        TaskStep targetStep = pollingStep("PENDING");
        targetStep.setId(13L);
        targetStep.setFlowNodeId(8L);
        targetStep.setNodeIdRef(3L);
        targetStep.setInterfaceInSnapshot(WorkflowInterfaceSnapshots.initialize(targetNode.getInterfaces(), "IN"));
        WorkflowDefinitionCompiler.Connection connection = new WorkflowDefinitionCompiler.Connection(
                2L, "Interface_workflow_out", 3L, "Interface_workflow_in");
        WorkflowDefinitionCompiler.CompiledWorkflow compiled = new WorkflowDefinitionCompiler.CompiledWorkflow(
                Map.of(), Map.of(2L, List.of(connection)), Map.of(3L, List.of(connection)),
                Map.of("source", 2L, "target", 3L), 2L, 3L);
        stubPoll(task, sourceStep, sourceNode);
        when(workflows.compileDefinition(3L)).thenReturn(compiled);
        when(workflows.nodes(3L)).thenReturn(List.of(sourceNode, targetNode));
        when(runtime.createStep(eq(task), eq(targetNode), eq(null), eq(0),
                org.mockito.ArgumentMatchers.any())).thenReturn(targetStep);
        doAnswer(invocation -> {
            sourceStep.setInterfaceOutSnapshot(invocation.getArgument(1));
            return null;
        }).when(runtime).updateOutputSnapshot(eq(sourceStep), org.mockito.ArgumentMatchers.any());
        doAnswer(invocation -> {
            targetStep.setInterfaceInSnapshot(invocation.getArgument(1));
            return null;
        }).when(runtime).updateInputSnapshot(eq(targetStep), org.mockito.ArgumentMatchers.any());

        engine.processTask(task);

        assertThat(sourceStep.getInterfaceOutSnapshot().isArray()).isTrue();
        assertThat(WorkflowInterfaceSnapshots.find(sourceStep.getInterfaceOutSnapshot(), "Interface_workflow_out")
                .path("signalName").asText()).isEqualTo("ACTIVE");
        assertThat(WorkflowInterfaceSnapshots.find(sourceStep.getInterfaceOutSnapshot(), "Interface_workflow_out")
                .has("payload")).isFalse();
        assertThat(targetStep.getInterfaceInSnapshot().isArray()).isTrue();
        assertThat(WorkflowInterfaceSnapshots.find(targetStep.getInterfaceInSnapshot(), "Interface_workflow_in")
                .path("signalName").asText()).isEqualTo("ACTIVE");
        assertThat(targetStep.getInterfaceInSnapshot().toString())
                .doesNotContain("sourceNodeIdRef", "sourceInterface", "targetInterfaceName",
                        "inputSignalName", "inputPayload", "sourceOutput");
        verify(runtime).updateOutputSnapshot(eq(sourceStep), org.mockito.ArgumentMatchers.any());
        verify(runtime).updateInputSnapshot(eq(targetStep), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void workflowEmissionDoesNotWriteInputSnapshotOfTerminalTarget() {
        Task task = pollingTask();
        TaskStep sourceStep = pollingStep("RUNNING");
        FlowNode sourceNode = pollingNode(List.of(
                lifecycleTriggerInterface("Interface_workflow_out", "OUT", "RUNNING", "EMIT")));
        sourceStep.setInterfaceOutSnapshot(WorkflowInterfaceSnapshots.initialize(sourceNode.getInterfaces(), "OUT"));
        FlowNode targetNode = pollingNode(List.of(
                signalTriggerInterface("Interface_workflow_in", "ACTIVE", "target")));
        targetNode.setId(8L);
        targetNode.setNodeIdRef(3L);
        TaskStep targetStep = pollingStep("SUCCEEDED");
        targetStep.setId(13L);
        targetStep.setFlowNodeId(8L);
        targetStep.setNodeIdRef(3L);
        targetStep.setInterfaceInSnapshot(WorkflowInterfaceSnapshots.initialize(targetNode.getInterfaces(), "IN"));
        WorkflowDefinitionCompiler.Connection connection = new WorkflowDefinitionCompiler.Connection(
                2L, "Interface_workflow_out", 3L, "Interface_workflow_in");
        WorkflowDefinitionCompiler.CompiledWorkflow compiled = new WorkflowDefinitionCompiler.CompiledWorkflow(
                Map.of(), Map.of(2L, List.of(connection)), Map.of(3L, List.of(connection)),
                Map.of("source", 2L, "target", 3L), 2L, 3L);
        stubPoll(task, sourceStep, sourceNode);
        when(workflows.compileDefinition(3L)).thenReturn(compiled);
        when(workflows.nodes(3L)).thenReturn(List.of(sourceNode, targetNode));
        when(runtime.createStep(eq(task), eq(targetNode), eq(null), eq(0),
                org.mockito.ArgumentMatchers.any())).thenReturn(targetStep);
        doAnswer(invocation -> {
            sourceStep.setInterfaceOutSnapshot(invocation.getArgument(1));
            return null;
        }).when(runtime).updateOutputSnapshot(eq(sourceStep), org.mockito.ArgumentMatchers.any());

        engine.processTask(task);

        assertThat(WorkflowInterfaceSnapshots.find(targetStep.getInterfaceInSnapshot(), "Interface_workflow_in")
                .path("signalName").isNull()).isTrue();
        verify(runtime, never()).updateInputSnapshot(eq(targetStep), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void workflowSignalRejectedByTargetInterfaceIsSilentlySkipped() {
        Task task = pollingTask();
        TaskStep sourceStep = pollingStep("RUNNING");
        FlowNode sourceNode = pollingNode(List.of(
                lifecycleTriggerInterface("Interface_workflow_out", "OUT", "RUNNING", "EMIT")));
        sourceStep.setInterfaceOutSnapshot(WorkflowInterfaceSnapshots.initialize(sourceNode.getInterfaces(), "OUT"));
        FlowNode targetNode = pollingNode(List.of(
                signalTriggerInterface("Interface_workflow_in", "SUBFLOW_COMPLETED", "target")));
        targetNode.setId(8L);
        targetNode.setNodeIdRef(3L);
        WorkflowDefinitionCompiler.Connection connection = new WorkflowDefinitionCompiler.Connection(
                2L, "Interface_workflow_out", 3L, "Interface_workflow_in");
        WorkflowDefinitionCompiler.CompiledWorkflow compiled = new WorkflowDefinitionCompiler.CompiledWorkflow(
                Map.of(), Map.of(2L, List.of(connection)), Map.of(3L, List.of(connection)),
                Map.of("source", 2L, "target", 3L), 2L, 3L);
        stubPoll(task, sourceStep, sourceNode);
        when(workflows.compileDefinition(3L)).thenReturn(compiled);
        when(workflows.nodes(3L)).thenReturn(List.of(sourceNode, targetNode));
        doAnswer(invocation -> {
            sourceStep.setInterfaceOutSnapshot(invocation.getArgument(1));
            return null;
        }).when(runtime).updateOutputSnapshot(eq(sourceStep), org.mockito.ArgumentMatchers.any());

        engine.processTask(task);

        assertThat(WorkflowInterfaceSnapshots.find(sourceStep.getInterfaceOutSnapshot(), "Interface_workflow_out")
                .path("signalName").asText()).isEqualTo("ACTIVE");
        verify(runtime, never()).createStep(eq(task), eq(targetNode), eq(null), eq(0),
                org.mockito.ArgumentMatchers.any());
        verify(runtime, never()).failStep(eq(sourceStep), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void operationStateRejectedByTargetInterfaceDoesNotOverwriteCommandSnapshot() {
        Task task = pollingTask();
        TaskStep step = pollingStep("RUNNING");
        FlowNode node = pollingNode(List.of(deviceStateInputInterface()));
        node.setDeviceModelId(21L);
        ArrayNode existing = WorkflowInterfaceSnapshots.initialize(node.getInterfaces(), "IN");
        existing = WorkflowInterfaceSnapshots.withSignal(existing, node.getInterfaces(), "IN", "state-in",
                "CMD_STATE", JsonNodeSupport.objectNode().put("stateName", "RUNNING"));
        step.setInterfaceInSnapshot(existing);
        WorkflowDetailResponse definition = new WorkflowDetailResponse();
        ObjectNode connection = JsonNodeSupport.objectNode().put("connectionType", "DEVICE_TO_NODE");
        connection.set("source", JsonNodeSupport.objectNode()
                .put("deviceModelId", 21L).put("interfaceName", "Interface_state_out"));
        connection.set("target", JsonNodeSupport.objectNode()
                .put("nodeName", "device").put("interfaceName", "state-in"));
        definition.setInterfaceConnections(JsonNodeSupport.arrayNode().add(connection));
        ObjectNode signal = JsonNodeSupport.objectNode().put("signalName", "OP_STATE");
        signal.set("payload", JsonNodeSupport.objectNode()
                .put("deviceModelId", 21L).put("deviceInstanceId", 99L)
                .put("stateName", "RUNNING").put("timestamp", 1L));
        StateMachineInterfaceSignalEvent event = new StateMachineInterfaceSignalEvent(
                99L, "Interface_state_out", "STATE", signal, Map.of());
        when(runtime.runningDeviceSteps()).thenReturn(List.of(step));
        when(runtime.step(step.getId())).thenReturn(step);
        when(runtime.task(task.getId())).thenReturn(task);
        when(flowNodes.getById(step.getFlowNodeId())).thenReturn(node);
        when(operations.resolveDeviceInstance(task, step, node)).thenReturn(99L);
        when(workflows.getDefinition(node.getFlowModelId())).thenReturn(definition);
        when(workflows.compileDefinition(node.getFlowModelId())).thenReturn(new WorkflowDefinitionCompiler.CompiledWorkflow(
                Map.of(), Map.of(), Map.of(), Map.of("device", node.getNodeIdRef()), 1L, 2L));

        engine.handleStateMachineSignal(event);

        verify(runtime, never()).updateInputSnapshot(eq(step), org.mockito.ArgumentMatchers.any());
        verify(runtime).runningDeviceSteps();
        assertThat(WorkflowInterfaceSnapshots.find(step.getInterfaceInSnapshot(), "state-in")
                .path("signalName").asText()).isEqualTo("CMD_STATE");
    }

    @Test
    void runningSubflowCreatesNestedStartStepInTheSameTask() {
        Task task = pollingTask();
        TaskStep parent = pollingStep("RUNNING");
        parent.setStepDepth(1);
        FlowNode subflow = pollingNode(List.of());
        subflow.setNodeType("SUBFLOW_NODE");
        subflow.setSubFlowModelId(4L);
        FlowNode childStart = pollingNode(List.of());
        childStart.setId(40L);
        childStart.setFlowModelId(4L);
        childStart.setNodeIdRef(1L);
        childStart.setNodeType("FUNC_NODE");
        childStart.setCapability(JsonNodeSupport.objectNode().put("functionType", "START"));
        stubPoll(task, parent, subflow);
        when(workflows.compileDefinition(4L)).thenReturn(new WorkflowDefinitionCompiler.CompiledWorkflow(
                Map.of(), Map.of(), Map.of(), Map.of("start", 1L), 1L, 2L));
        when(workflows.nodes(4L)).thenReturn(List.of(childStart));

        engine.processTask(task);

        verify(runtime).createStep(eq(task), eq(childStart), eq(parent.getId()), eq(2),
                org.mockito.ArgumentMatchers.isNull());
        assertThat(parent.getTaskId()).isEqualTo(task.getId());
    }

    @Test
    void pendingSubflowDoesNotStartNestedFlowInTheActivationPoll() {
        Task task = pollingTask();
        TaskStep parent = pollingStep("PENDING");
        FlowNode subflow = pollingNode(List.of(triggerInterface("workflow-in", "IN", "activate")));
        subflow.setNodeType("SUBFLOW_NODE");
        subflow.setSubFlowModelId(4L);
        stubPoll(task, parent, subflow);

        engine.processTask(task);

        verify(workflows, never()).compileDefinition(4L);
        verify(runtime, never()).createStep(eq(task), org.mockito.ArgumentMatchers.any(), eq(parent.getId()),
                eq(1), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void successfulRootEndCompletesTaskAfterTerminalTriggerEvaluation() {
        Task task = pollingTask();
        TaskStep endStep = pollingStep("SUCCEEDED");
        FlowNode endNode = functionNode("END", 3L, 2L);
        stubPoll(task, endStep, endNode);

        engine.processTask(task);

        verify(runtime).completeTask(task);
    }

    @Test
    void nestedSuccessfulEndNotifiesParentWithoutCompletingTaskOrChangingParentLifecycle() {
        Task task = pollingTask();
        TaskStep childEnd = pollingStep("SUCCEEDED");
        childEnd.setId(31L);
        childEnd.setParentStepId(20L);
        childEnd.setStepDepth(1);
        FlowNode endNode = functionNode("END", 4L, 2L);
        childEnd.setFlowNodeId(endNode.getId());
        TaskStep parent = pollingStep("RUNNING");
        parent.setId(20L);
        parent.setFlowNodeId(70L);
        FlowNode parentNode = pollingNode(List.of(subflowInputInterface()));
        parentNode.setId(70L);
        parentNode.setNodeType("SUBFLOW_NODE");
        parent.setInterfaceInSnapshot(WorkflowInterfaceSnapshots.initialize(parentNode.getInterfaces(), "IN"));
        stubPoll(task, childEnd, endNode);
        when(runtime.step(parent.getId())).thenReturn(parent);
        when(flowNodes.getById(parent.getFlowNodeId())).thenReturn(parentNode);
        doAnswer(invocation -> {
            parent.setInterfaceInSnapshot(invocation.getArgument(1));
            return null;
        }).when(runtime).updateInputSnapshot(eq(parent), org.mockito.ArgumentMatchers.any());

        engine.processTask(task);

        assertThat(WorkflowInterfaceSnapshots.find(parent.getInterfaceInSnapshot(), "Interface_workflow_in")
                .path("signalName").asText()).isEqualTo("SUBFLOW_COMPLETED");
        assertThat(WorkflowInterfaceSnapshots.find(parent.getInterfaceInSnapshot(), "Interface_workflow_in")
                .has("payload")).isFalse();
        assertThat(parent.getNodeStatus()).isEqualTo("RUNNING");
        verify(runtime, never()).completeTask(task);
        verify(operations, never()).transitionNodeLifecycle(eq(task), eq(parent), eq(parentNode), eq("SUCCEEDED"));
    }

    @Test
    void nestedSuccessfulEndDoesNotNotifyTerminalParentInput() {
        Task task = pollingTask();
        TaskStep childEnd = pollingStep("SUCCEEDED");
        childEnd.setId(31L);
        childEnd.setParentStepId(20L);
        childEnd.setStepDepth(1);
        FlowNode endNode = functionNode("END", 4L, 2L);
        childEnd.setFlowNodeId(endNode.getId());
        TaskStep parent = pollingStep("TERMINATED");
        parent.setId(20L);
        parent.setFlowNodeId(70L);
        FlowNode parentNode = pollingNode(List.of(subflowInputInterface()));
        parentNode.setId(70L);
        parentNode.setNodeType("SUBFLOW_NODE");
        parent.setInterfaceInSnapshot(WorkflowInterfaceSnapshots.initialize(parentNode.getInterfaces(), "IN"));
        stubPoll(task, childEnd, endNode);
        when(runtime.step(parent.getId())).thenReturn(parent);
        when(flowNodes.getById(parent.getFlowNodeId())).thenReturn(parentNode);

        engine.processTask(task);

        assertThat(WorkflowInterfaceSnapshots.find(parent.getInterfaceInSnapshot(), "Interface_workflow_in")
                .path("signalName").isNull()).isTrue();
        verify(runtime, never()).updateInputSnapshot(eq(parent), org.mockito.ArgumentMatchers.any());
    }

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
            registry, operations);

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

    private WorkflowActionExecutor executor(String actionName, WorkflowActionResult result) {
        return new WorkflowActionExecutor() {
            @Override
            public String actionName() {
                return actionName;
            }

            @Override
            public WorkflowActionResult execute(WorkflowActionDefinition action, WorkflowActionContext context) {
                executionOrder.add(action.payload().path("trace").asText(action.actionName()));
                return result;
            }
        };
    }

    private WorkflowActionExecutor mutatingLifecycleExecutor() {
        return new WorkflowActionExecutor() {
            @Override
            public String actionName() {
                return "UPDATE";
            }

            @Override
            public WorkflowActionResult execute(WorkflowActionDefinition action, WorkflowActionContext context) {
                executionOrder.add("UPDATE");
                context.step().setNodeStatus("SUCCEEDED");
                return WorkflowActionResult.continueExecution();
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
        step.setStepDepth(0);
        step.setNodeStatus(status);
        step.setVariableSpace(JsonNodeSupport.objectNode().put("ready", true));
        step.setInterfaceInSnapshot(JsonNodeSupport.arrayNode());
        step.setInterfaceOutSnapshot(JsonNodeSupport.arrayNode());
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

    private ObjectNode lifecycleTriggerInterface(String name, String direction, String state, String actionName) {
        ObjectNode item = JsonNodeSupport.objectNode();
        item.put("name", name);
        item.put("direction", direction);
        item.put("interfaceType", "WORKFLOW");
        item.putArray("allowedSignals").add("ACTIVE");
        ObjectNode trigger = item.putArray("bindingTriggers").addObject();
        trigger.putObject("condition").put("object", "nodeLifecycleState")
                .put("operator", "=").put("threshold", state);
        trigger.putObject("action").put("actionName", actionName).putObject("payload")
                .put("targetInterfaceName", name).put("signalName", "ACTIVE");
        return item;
    }

    private ObjectNode deviceStateInputInterface() {
        ObjectNode item = JsonNodeSupport.objectNode();
        item.put("name", "state-in");
        item.put("direction", "IN");
        item.put("interfaceType", "STATE");
        item.putArray("allowedSignals").add("CMD_STATE");
        ObjectNode trigger = item.putArray("bindingTriggers").addObject();
        ArrayNode conditions = trigger.putObject("condition").put("logic", "AND").putArray("conditions");
        conditions.addObject().put("object", "nodeLifecycleState").put("operator", "=").put("threshold", "RUNNING");
        conditions.addObject().put("object", "signalName").put("operator", "=").put("threshold", "CMD_STATE");
        conditions.addObject().put("object", "payload.stateName").put("operator", "=").put("threshold", "RUNNING");
        trigger.set("action", action("UPDATE"));
        return item;
    }

    private ObjectNode signalTriggerInterface(String name, String signalName, String trace) {
        ObjectNode item = JsonNodeSupport.objectNode();
        item.put("name", name);
        item.put("direction", "IN");
        item.put("interfaceType", "WORKFLOW");
        item.putArray("allowedSignals").add(signalName);
        ObjectNode trigger = item.putArray("bindingTriggers").addObject();
        trigger.putObject("condition").put("object", "signalName")
                .put("operator", "=").put("threshold", signalName);
        trigger.putObject("action").put("actionName", "UPDATE").putObject("payload")
                .put("updateType", "INTERNAL_VARIABLE").put("targetName", "ready")
                .put("value", true).put("trace", trace);
        return item;
    }

    private ObjectNode incrementTriggerInterface(String name) {
        ObjectNode item = JsonNodeSupport.objectNode();
        item.put("name", name);
        item.put("direction", "IN");
        item.put("interfaceType", "WORKFLOW");
        item.putArray("allowedSignals").add("ACTIVE");
        ObjectNode trigger = item.putArray("bindingTriggers").addObject();
        trigger.putObject("condition").put("object", "signalName")
                .put("operator", "=").put("threshold", "ACTIVE");
        trigger.putObject("action").put("actionName", "UPDATE").putObject("payload")
                .put("updateType", "INTERNAL_VARIABLE").put("targetName", "aggregateCount")
                .put("valueExpression", "aggregateCount + 1");
        return item;
    }

    private ObjectNode subflowInputInterface() {
        ObjectNode item = JsonNodeSupport.objectNode();
        item.put("name", "Interface_workflow_in");
        item.put("direction", "IN");
        item.put("interfaceType", "WORKFLOW");
        item.putArray("allowedSignals").add("ACTIVE").add("SUBFLOW_COMPLETED");
        item.putArray("bindingTriggers");
        return item;
    }

    private FlowNode functionNode(String functionType, long flowModelId, long nodeRef) {
        FlowNode node = pollingNode(List.of());
        node.setNodeType("FUNC_NODE");
        node.setFlowModelId(flowModelId);
        node.setNodeIdRef(nodeRef);
        node.setCapability(JsonNodeSupport.objectNode().put("functionType", functionType));
        return node;
    }

    private WorkflowDefinitionCompiler.CompiledWorkflow emptyCompiledWorkflow() {
        return new WorkflowDefinitionCompiler.CompiledWorkflow(
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), 1L, 2L);
    }

    private void merge(ObjectNode target, com.fasterxml.jackson.databind.JsonNode updates) {
        updates.fields().forEachRemaining(entry -> {
            if (target.path(entry.getKey()).isObject() && entry.getValue().isObject()) {
                merge((ObjectNode) target.path(entry.getKey()), entry.getValue());
            } else {
                target.set(entry.getKey(), entry.getValue().deepCopy());
            }
        });
    }

    private void stubPoll(Task task, TaskStep step, FlowNode node) {
        if (step.getInterfaceInSnapshot() == null || step.getInterfaceInSnapshot().isEmpty()) {
            step.setInterfaceInSnapshot(WorkflowInterfaceSnapshots.initialize(node.getInterfaces(), "IN"));
        }
        if (step.getInterfaceOutSnapshot() == null || step.getInterfaceOutSnapshot().isEmpty()) {
            step.setInterfaceOutSnapshot(WorkflowInterfaceSnapshots.initialize(node.getInterfaces(), "OUT"));
        }
        when(runtime.pollableSteps(task.getId())).thenReturn(List.of(step));
        when(runtime.task(task.getId())).thenReturn(task);
        when(flowNodes.getById(step.getFlowNodeId())).thenReturn(node);
        when(operations.resolveMappedVariables(task, step, node)).thenReturn(JsonNodeSupport.objectNode());
    }

    private WorkflowEngine engine(WorkflowService workflows) {
        return new WorkflowEngine(runtime, workflows, mock(FlowNodeService.class),
                new WorkflowConditionEvaluator(), new ConstraintExpressionEvaluator(), registry, operations);
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
