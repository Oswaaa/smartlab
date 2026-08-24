package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.connection.InterfaceConnectionForwarder;
import com.smartlab.engine.connection.PortConnectionPuller;
import com.smartlab.engine.constraint.ConstraintExpressionEvaluator;
import com.smartlab.engine.observation.ObservableKey;
import com.smartlab.engine.observation.ObservableSnapshotReader;
import com.smartlab.engine.observation.ObservationHistoryStore;
import com.smartlab.engine.statemachine.StateMachineInterfaceSignalEvent;
import com.smartlab.engine.workflow.action.WorkflowActionContext;
import com.smartlab.engine.workflow.action.WorkflowActionDefinition;
import com.smartlab.engine.workflow.action.WorkflowActionRegistry;
import com.smartlab.engine.workflow.action.WorkflowActionResult;
import com.smartlab.engine.workflow.action.WorkflowActionStatus;
import com.smartlab.global.contract.WorkflowNodeSignal;
import com.smartlab.global.contract.ObservableObjectType;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.workflow.FlowNodeService;
import com.smartlab.management.service.db.workflow.WorkflowRuntimeService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 工作流运行时按节点轮询接口触发器；设备事件只持久化输入，由下一轮统一求值。 */
@Service
public class WorkflowEngine implements WorkflowTaskPollScheduler {
    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);
    private static final int MAX_SUB_FLOW_DEPTH = 32;
    private static final int MAX_CONSECUTIVE_DIRTY_ROUNDS = 64;
    private static final Set<String> TERMINAL_NODE_STATES = Set.of("SUCCEEDED", "FAILED", "TERMINATED");
    private static final Set<String> ACTIVE_NODE_STATES = Set.of("PENDING", "RUNNING", "TERMINATING");

    private final WorkflowRuntimeService runtime;
    private final WorkflowService workflowService;
    private final FlowNodeService flowNodeService;
    private final WorkflowConditionEvaluator conditionEvaluator;
    private final ConstraintExpressionEvaluator expressionEvaluator;
    private final WorkflowActionRegistry actionRegistry;
    private final WorkflowExecutionOperations executionOperations;
    private final ObservableSnapshotReader observationReader;
    private final Executor workflowExecutor;
    private final PortConnectionPuller portConnectionPuller;
    private final InterfaceConnectionForwarder interfaceConnections;
    private final Set<Long> inFlightTaskIds = ConcurrentHashMap.newKeySet();
    private final Set<Long> pendingTaskIds = ConcurrentHashMap.newKeySet();
    private final ThreadLocal<Boolean> roundDirty = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Autowired
    public WorkflowEngine(WorkflowRuntimeService runtime, WorkflowService workflowService,
            FlowNodeService flowNodeService, WorkflowConditionEvaluator conditionEvaluator,
            ConstraintExpressionEvaluator expressionEvaluator, WorkflowActionRegistry actionRegistry,
            WorkflowExecutionOperations executionOperations, ObservableSnapshotReader observationReader,
            @Qualifier("workflowEngineExecutor") Executor workflowExecutor,
            PortConnectionPuller portConnectionPuller, InterfaceConnectionForwarder interfaceConnections) {
        this.runtime = runtime;
        this.workflowService = workflowService;
        this.flowNodeService = flowNodeService;
        this.conditionEvaluator = conditionEvaluator;
        this.expressionEvaluator = expressionEvaluator;
        this.actionRegistry = actionRegistry;
        this.executionOperations = executionOperations;
        this.observationReader = observationReader;
        this.workflowExecutor = workflowExecutor;
        this.portConnectionPuller = portConnectionPuller != null
                ? portConnectionPuller
                : new PortConnectionPuller(runtime, workflowService, flowNodeService, executionOperations);
        this.interfaceConnections = interfaceConnections != null
                ? interfaceConnections
                : new InterfaceConnectionForwarder(runtime, workflowService, flowNodeService, executionOperations,
                        this.portConnectionPuller);
    }

    public WorkflowEngine(WorkflowRuntimeService runtime, WorkflowService workflowService,
            FlowNodeService flowNodeService, WorkflowConditionEvaluator conditionEvaluator,
            ConstraintExpressionEvaluator expressionEvaluator, WorkflowActionRegistry actionRegistry,
            WorkflowExecutionOperations executionOperations) {
        this(runtime, workflowService, flowNodeService, conditionEvaluator, expressionEvaluator,
                actionRegistry, executionOperations, emptyObservationReader(), Runnable::run, null, null);
    }

    public WorkflowEngine(WorkflowRuntimeService runtime, WorkflowService workflowService,
            FlowNodeService flowNodeService, WorkflowConditionEvaluator conditionEvaluator,
            ConstraintExpressionEvaluator expressionEvaluator, WorkflowActionRegistry actionRegistry,
            WorkflowExecutionOperations executionOperations, ObservableSnapshotReader observationReader) {
        this(runtime, workflowService, flowNodeService, conditionEvaluator, expressionEvaluator,
                actionRegistry, executionOperations, observationReader, Runnable::run, null, null);
    }

    public WorkflowEngine(WorkflowRuntimeService runtime, WorkflowService workflowService,
            FlowNodeService flowNodeService, WorkflowConditionEvaluator conditionEvaluator,
            ConstraintExpressionEvaluator expressionEvaluator, WorkflowActionRegistry actionRegistry,
            WorkflowExecutionOperations executionOperations, Executor workflowExecutor) {
        this(runtime, workflowService, flowNodeService, conditionEvaluator, expressionEvaluator,
                actionRegistry, executionOperations, emptyObservationReader(), workflowExecutor, null, null);
    }

    @Scheduled(fixedDelayString = "${smartlab.workflow.poll-interval-ms:100}")
    public void driveWorkflows() {
        for (Task task : runtime.runningTasks()) {
            requestPoll(task.getId());
        }
    }

    @Override
    public void requestPoll(Long taskId) {
        if (taskId == null) return;
        pendingTaskIds.add(taskId);
        trySchedule(taskId);
    }

    void dispatchTask(Long taskId) {
        requestPoll(taskId);
    }

    private void trySchedule(Long taskId) {
        if (!inFlightTaskIds.add(taskId)) return;
        try {
            workflowExecutor.execute(() -> runTaskRounds(taskId));
        } catch (RuntimeException error) {
            inFlightTaskIds.remove(taskId);
            log.warn("工作流任务{}本轮未进入执行线程池: {}", taskId, error.getMessage());
        }
    }

    private void runTaskRounds(Long taskId) {
        boolean yieldToScan = false;
        try {
            int consecutive = 0;
            while (pendingTaskIds.remove(taskId)) {
                if (++consecutive > MAX_CONSECUTIVE_DIRTY_ROUNDS) {
                    pendingTaskIds.add(taskId);
                    yieldToScan = true;
                    break;
                }
                try {
                    Task task = runtime.task(taskId);
                    if (task == null || !isPollableTask(task.getTaskStatus())) break;
                    if (processTask(task)) pendingTaskIds.add(taskId);
                } catch (Exception error) {
                    log.error("工作流任务{}调度失败", taskId, error);
                    Task task = runtime.task(taskId);
                    if (task != null) runtime.failTask(task, error.getMessage());
                    break;
                }
            }
        } finally {
            inFlightTaskIds.remove(taskId);
            if (!yieldToScan && pendingTaskIds.contains(taskId)) trySchedule(taskId);
        }
    }

    boolean processTask(Task task) {
        roundDirty.set(false);
        try {
            synchronized (WorkflowTaskLocks.of(task.getId())) {
                processTaskLocked(task);
            }
            return Boolean.TRUE.equals(roundDirty.get());
        } finally {
            roundDirty.remove();
        }
    }

    private void markRoundDirty() {
        roundDirty.set(true);
    }

    private void processTaskLocked(Task task) {
        List<TaskStep> steps = runtime.steps(task.getId());
        if (steps == null) steps = List.of();
        portConnectionPuller.pullForTask(task, steps);
        interfaceConnections.refreshForTask(task, steps);
        List<TaskStep> pollable = pollableSteps(steps);
        if (pollable.isEmpty()) {
            Task latest = runtime.task(task.getId());
            if (steps.isEmpty() && latest != null && "RUNNING".equals(latest.getTaskStatus())) {
                startFlow(task, task.getFlowModelId(), null, 0);
                markRoundDirty();
            } else if (latest != null && "TERMINATING".equals(latest.getTaskStatus())) {
                runtime.completeTerminationIfSettled(latest.getId());
            }
            return;
        }
        for (TaskStep step : pollable) {
            String taskStatus = runtime.task(task.getId()).getTaskStatus();
            if (!isPollableTask(taskStatus))
                return;
            try {
                boolean terminalAtPollStart = TERMINAL_NODE_STATES.contains(step.getNodeStatus());
                processStep(task, step);
                failTaskIfNodeFailed(task, step);
                if (terminalAtPollStart) {
                    FlowNode node = flowNodeService.getById(step.getFlowNodeId());
                    if (node != null && "SUCCEEDED".equals(step.getNodeStatus())
                            && "END".equals(functionType(node))) {
                        settleSuccessfulEnd(task, step);
                    }
                    markTerminalObserved(step);
                }
            } catch (Exception error) {
                runtime.failStep(step, error.getMessage());
            }
        }
        Task latest = runtime.task(task.getId());
        if (latest != null && "TERMINATING".equals(latest.getTaskStatus())) {
            runtime.completeTerminationIfSettled(latest.getId());
        }
    }

    private void startFlow(Task task, Long flowModelId, Long parentStepId, int depth) {
        if (flowModelId == null)
            throw new IllegalArgumentException("子流程节点缺少流程模型引用");
        if (depth > MAX_SUB_FLOW_DEPTH)
            throw new IllegalStateException("子流程嵌套超过" + MAX_SUB_FLOW_DEPTH + "层");
        var compiled = workflowService.compileDefinition(flowModelId);
        runtime.createStep(task, nodeByRef(flowModelId, compiled.startNodeIdRef()), parentStepId, depth,
                null);
    }

    private void processStep(Task task, TaskStep step) {
        FlowNode node = flowNodeService.getById(step.getFlowNodeId());
        if (node == null)
            throw new IllegalArgumentException("找不到FLOW_NODE: " + step.getFlowNodeId());
        if ("SUBFLOW_NODE".equals(node.getNodeType()) && "RUNNING".equals(step.getNodeStatus())
                && "RUNNING".equals(task.getTaskStatus())) {
            int depth = step.getStepDepth() == null ? 0 : step.getStepDepth();
            startFlow(task, node.getSubFlowModelId(), step.getId(), depth + 1);
        }
        ObjectNode frozenSnapshot = actionVariables(task, step, null, node);
        portConnectionPuller.syncOutputMailbox(node, step);
        evaluateNodeTriggers(task, step, node, frozenSnapshot);
    }

    private void settleSuccessfulEnd(Task task, TaskStep endStep) {
        if (endStep.getParentStepId() == null) {
            runtime.completeTask(task);
            return;
        }
        TaskStep parent = runtime.step(endStep.getParentStepId());
        if (parent == null || !java.util.Objects.equals(parent.getTaskId(), task.getId())) {
            throw new IllegalStateException("子流程END引用的父步骤不存在或不属于当前任务");
        }
        FlowNode parentNode = flowNodeService.getById(parent.getFlowNodeId());
        if (parentNode == null || !"SUBFLOW_NODE".equals(parentNode.getNodeType())) {
            throw new IllegalStateException("子流程END的父步骤不是SUBFLOW_NODE");
        }
        if (TERMINAL_NODE_STATES.contains(parent.getNodeStatus())) {
            return;
        }
        ArrayNode input = WorkflowInterfaceSnapshots.withSignal(
                parent.getInterfaceInSnapshot(), parentNode.getInterfaces(), "IN",
                "Interface_workflow_in", WorkflowNodeSignal.SUBFLOW_COMPLETED.name(), null);
        runtime.updateInputSnapshot(parent, input);
        runtime.appendStepLog(task, parent, "INFO", WorkflowExecutionLogs.received(
                nodeName(parentNode), parentNode.getNodeIdRef(), "Interface_workflow_in",
                WorkflowNodeSignal.SUBFLOW_COMPLETED.name(), null));
        markRoundDirty();
    }

    /** 各接口触发器对照本轮开始时的冻结快照求值；动作立即持久化，但不把生命周期写回本轮快照。 */
    private void evaluateNodeTriggers(Task task, TaskStep step, FlowNode node, ObjectNode frozenSnapshot) {
        ObjectNode triggerStates = triggerStates(step);
        boolean statesChanged = false;
        List<JsonNode> orderedInterfaces = new java.util.ArrayList<>();
        List<String> directions = TERMINAL_NODE_STATES.contains(step.getNodeStatus())
                ? List.of("OUT") : List.of("OUT", "IN");
        for (String direction : directions) {
            for (JsonNode item : iterable(node.getInterfaces())) {
                if (direction.equals(item.path("direction").asText())) orderedInterfaces.add(item);
            }
        }
        for (JsonNode interfaceNode : orderedInterfaces) {
            ObjectNode interfaceSnapshot = interfaceVariables(step, interfaceNode, frozenSnapshot);
            Map<String, Integer> duplicateOrdinals = new java.util.HashMap<>();
            for (JsonNode trigger : iterable(interfaceNode.path("bindingTriggers"))) {
                String fingerprint = WorkflowTriggerState.fingerprint(trigger);
                int duplicateOrdinal = duplicateOrdinals.merge(fingerprint, 1, Integer::sum) - 1;
                String key = WorkflowTriggerState.key(
                        interfaceNode.path("name").asText(""), fingerprint, duplicateOrdinal);
                boolean previous = triggerStates.path(key).asBoolean(false);
                boolean current = conditionEvaluator.evaluate(trigger.path("condition"), interfaceSnapshot);
                if (current && !previous) {
                    String lifecycleBefore = step.getNodeStatus();
                    WorkflowActionResult result = WorkflowActionResult.continueExecution();
                    for (JsonNode actionNode : WorkflowTriggerState.actions(trigger)) {
                        result = executeAction(task, step, node, interfaceSnapshot,
                                WorkflowActionDefinition.from(actionNode));
                        if (result.emittedInterfaceName() != null && !result.emittedInterfaceName().isBlank()) {
                            result = routeEmission(task, step, node, result);
                        }
                        if (result.status() == WorkflowActionStatus.WAIT_DEVICE_IDLE) break;
                    }
                    if (!java.util.Objects.equals(lifecycleBefore, step.getNodeStatus())) markRoundDirty();
                    if (result.status() != WorkflowActionStatus.WAIT_DEVICE_IDLE) {
                        triggerStates.put(key, true);
                        statesChanged = true;
                    }
                } else if (!current && previous) {
                    triggerStates.put(key, false);
                    statesChanged = true;
                }
            }
        }
        if (statesChanged) persistTriggerStates(step, triggerStates);
    }

    private ObjectNode interfaceVariables(TaskStep step, JsonNode interfaceNode, ObjectNode frozenSnapshot) {
        ObjectNode result = frozenSnapshot.deepCopy();
        String direction = interfaceNode.path("direction").asText("");
        JsonNode snapshot = "OUT".equals(direction)
                ? step.getInterfaceOutSnapshot() : step.getInterfaceInSnapshot();
        JsonNode current = WorkflowInterfaceSnapshots.find(snapshot, interfaceNode.path("name").asText(""));
        if (current.path("signalName").isTextual()) {
            result.set("signalName", current.path("signalName").deepCopy());
        } else {
            result.putNull("signalName");
        }
        if (current.path("payload").isObject()) {
            result.set("payload", current.path("payload").deepCopy());
        } else {
            result.remove("payload");
        }
        return result;
    }

    private ObjectNode triggerStates(TaskStep step) {
        JsonNode variableSpace = step.getVariableSpace();
        return variableSpace != null && variableSpace.path("_triggerStates").isObject()
                ? (ObjectNode) variableSpace.path("_triggerStates").deepCopy()
                : JsonNodeSupport.objectNode();
    }

    private void persistTriggerStates(TaskStep step, ObjectNode triggerStates) {
        ObjectNode update = JsonNodeSupport.objectNode();
        update.set("_triggerStates", triggerStates.deepCopy());
        runtime.mergeVariableSpace(step, update);
    }

    private void markTerminalObserved(TaskStep step) {
        ObjectNode states = triggerStates(step);
        states.put(WorkflowTriggerState.TERMINAL_OBSERVED_KEY, true);
        persistTriggerStates(step, states);
    }

    private WorkflowActionResult routeEmission(Task task, TaskStep step, FlowNode node, WorkflowActionResult result) {
        JsonNode target = interfaceByName(node, result.emittedInterfaceName());
        JsonNode payload = "STATE".equals(target.path("interfaceType").asText())
                ? stateEmissionPayload(task, step, node) : null;
        ArrayNode output = WorkflowInterfaceSnapshots.withSignal(
                step.getInterfaceOutSnapshot(), node.getInterfaces(), "OUT",
                result.emittedInterfaceName(), result.emittedSignalName(), payload);
        runtime.updateOutputSnapshot(step, output);
        runtime.appendStepLog(task, step, "INFO", WorkflowExecutionLogs.emitted(
                nodeName(node), node.getNodeIdRef(), result.emittedInterfaceName(), result.emittedSignalName(), payload));
        markRoundDirty();
        if ("STATE".equals(target.path("interfaceType").asText())) {
            long instanceId = executionOperations.resolveDeviceInstance(task, step, node);
            String messageId = payload.path("messageId").asText();
            JsonNode parameters = payload.path("parameters");
            return executionOperations.dispatchDeviceSignal(task, step, node, instanceId, messageId,
                    result.emittedInterfaceName(), result.emittedSignalName(), parameters)
                    == WorkflowExecutionOperations.DeviceDispatchResult.DEVICE_BUSY
                    ? WorkflowActionResult.waitDeviceIdle(messageId)
                    : WorkflowActionResult.awaitExternalSignal(messageId);
        }
        interfaceConnections.forwardFromNode(task, step, node, result.emittedInterfaceName(), result.emittedSignalName());
        return result;
    }

    private ObjectNode stateEmissionPayload(Task task, TaskStep step, FlowNode node) {
        long instanceId = executionOperations.resolveDeviceInstance(task, step, node);
        String capabilityName = node.getCapability() == null ? "" : node.getCapability().path("capabilityName").asText("");
        ObjectNode payload = JsonNodeSupport.objectNode();
        payload.put("messageId", executionOperations.ensureMessageId(step, instanceId, capabilityName));
        payload.put("capabilityName", capabilityName);
        JsonNode parameters = node.getCapability() == null ? null : node.getCapability().path("capabilityParameters");
        payload.set("parameters", parameters != null && parameters.isObject()
                ? parameters.deepCopy() : JsonNodeSupport.objectNode());
        return payload;
    }

    private JsonNode interfaceByName(FlowNode node, String name) {
        if (node != null && node.getInterfaces() != null) {
            for (JsonNode item : node.getInterfaces()) {
                if (name.equals(item.path("name").asText())) return item;
            }
        }
        throw new IllegalArgumentException("节点接口不存在: " + name);
    }

    private ObjectNode actionVariables(Task task, TaskStep step, JsonNode inputInterface, FlowNode node) {
        ObjectNode variables = (ObjectNode) mergeVariables(task, step);
        ObjectNode mapped = executionOperations.resolveMappedVariables(task, step, node);
        if (mapped != null && !mapped.isEmpty()) {
            runtime.mergeVariableSpace(step, mapped);
            mergeObject(variables, mapped);
        }
        applyNodeExpression(task, step, node, variables);
        variables.put("nodeLifecycleState", step.getNodeStatus());
        variables.put("taskLifecycleState", task.getTaskStatus());
        if (inputInterface != null)
            variables.put("inputInterfaceName", inputInterface.path("name").asText(""));
        return variables;
    }

    private void applyNodeExpression(Task task, TaskStep step, FlowNode node, ObjectNode variables) {
        String type = functionType(node);
        String source = "FUNC_NODE".equals(node.getNodeType())
                && ("BRANCH".equals(type) || "AGGREGATE".equals(type))
                && node.getCapability() != null
                ? node.getCapability().path("expression").asText("").trim() : "";
        if (source.isBlank()) return;
        WorkflowAssignmentExpression assignment = WorkflowAssignmentExpression.parse(source);
        Instant now = Instant.now();
        Map<String, JsonNode> values = new LinkedHashMap<>();
        variables.fields().forEachRemaining(entry -> values.put(entry.getKey(), entry.getValue()));
        Map<String, List<ConstraintExpressionEvaluator.TimedValue>> histories = expressionHistories(
                task, node, assignment.valueExpression(), values, now);
        try {
            JsonNode result = expressionEvaluator.evaluateWorkflowValue(
                    assignment.valueExpression(), values, histories, now);
            result = normalizeExpressionResult(node, assignment.targetName(), result);
            ObjectNode update = JsonNodeSupport.objectNode();
            update.set(assignment.targetName(), result.deepCopy());
            runtime.mergeVariableSpace(step, update);
            variables.set(assignment.targetName(), result.deepCopy());
        } catch (ConstraintExpressionEvaluator.TemporalDataUnavailableException unavailable) {
            variables.remove(assignment.targetName());
        }
    }

    private Map<String, List<ConstraintExpressionEvaluator.TimedValue>> expressionHistories(
            Task task, FlowNode node, String expression, Map<String, JsonNode> values, Instant now) {
        Map<String, List<ConstraintExpressionEvaluator.TimedValue>> result = new LinkedHashMap<>();
        if (!expression.matches("(?is).*\\b(?:rate|delta|avg|max|min)\\s*\\(.*")) return result;
        String nodeName = workflowService.compileDefinition(node.getFlowModelId()).refsByNodeName().entrySet().stream()
                .filter(entry -> java.util.Objects.equals(entry.getValue(), node.getNodeIdRef()))
                .map(Map.Entry::getKey).findFirst()
                .orElseThrow(() -> new IllegalStateException("流程节点缺少名称映射: " + node.getNodeIdRef()));
        for (String variable : expressionEvaluator.referencedVariables(expression)) {
            ObservableKey key = new ObservableKey(ObservableObjectType.NODE_INTERNAL_VARIABLE,
                    null, node.getFlowModelId(), task.getId(), null, nodeName, null, variable);
            List<ConstraintExpressionEvaluator.TimedValue> samples = new ArrayList<>();
            observationReader.readHistory(key, now.minus(ObservationHistoryStore.DEFAULT_MAX_SECONDS, ChronoUnit.SECONDS))
                    .forEach(sample -> samples.add(new ConstraintExpressionEvaluator.TimedValue(
                            sample.observedAt(), sample.value())));
            JsonNode current = values.get(variable);
            if (current != null && current.isNumber()) {
                samples.add(new ConstraintExpressionEvaluator.TimedValue(now, current.deepCopy()));
            }
            result.put(variable, List.copyOf(samples));
        }
        return result;
    }

    private JsonNode normalizeExpressionResult(FlowNode node, String targetName, JsonNode value) {
        JsonNode definition = requiredNamed(node.getInVariables(), targetName, "表达式目标内部变量");
        String type = definition.path("dataType").asText("");
        if ("DOUBLE".equals(type) && value.isNumber()) return value;
        if ("INTEGER".equals(type) && value.isNumber()) {
            try {
                return JsonNodeSupport.toNode(value.decimalValue().longValueExact());
            } catch (ArithmeticException ignored) {
                // Fall through to the uniform type error below.
            }
        }
        throw new IllegalStateException("表达式结果与目标内部变量类型不一致: " + targetName);
    }

    private static ObservableSnapshotReader emptyObservationReader() {
        return new ObservableSnapshotReader() {
            public com.smartlab.engine.observation.ObservationSnapshot read(ObservableKey key) { return null; }
            public Map<ObservableKey, com.smartlab.engine.observation.ObservationSnapshot> readBatch(java.util.Collection<ObservableKey> keys) { return Map.of(); }
            public List<com.smartlab.engine.observation.ObservationSample> readHistory(ObservableKey key, Instant since) { return List.of(); }
            public Map<ObservableKey, List<com.smartlab.engine.observation.ObservationSample>> readHistoryBatch(java.util.Collection<ObservableKey> keys, Instant since) { return Map.of(); }
        };
    }

    ActionRunResult executeActions(Task task, TaskStep step, FlowNode node, Iterable<JsonNode> actions,
            ObjectNode variables) {
        ActionRunResult aggregate = ActionRunResult.continueWithoutEmission();
        for (JsonNode actionNode : actions) {
            WorkflowActionDefinition action = WorkflowActionDefinition.from(actionNode);
            WorkflowActionResult result = executeAction(task, step, node, variables, action);
            if (result.status() != WorkflowActionStatus.CONTINUE) {
                aggregate = ActionRunResult.awaitingExternalSignal();
                continue;
            }
            if (result.emittedInterfaceName() != null && !result.emittedInterfaceName().isBlank()) {
                aggregate = ActionRunResult.emitted(
                        result.emittedInterfaceName(), result.emittedSignalName(), variables);
            }
        }
        return aggregate;
    }

    private WorkflowActionResult executeAction(Task task, TaskStep step, FlowNode node, ObjectNode variables,
            WorkflowActionDefinition action) {
        ObjectNode currentVariables = latestActionVariables(step, variables);
        WorkflowActionResult result = actionRegistry.required(action.actionName()).execute(
                action, new WorkflowActionContext(task, step, node, currentVariables, Instant.now(), executionOperations));
        if (!result.variableUpdates().isEmpty()) {
            runtime.mergeVariableSpace(step, result.variableUpdates());
        }
        return result;
    }

    private ObjectNode latestActionVariables(TaskStep step, ObjectNode frozenVariables) {
        ObjectNode current = frozenVariables.deepCopy();
        JsonNode latest = step.getVariableSpace();
        if (latest == null || !latest.isObject()) return current;
        latest.fields().forEachRemaining(entry -> {
            if (!"_triggerStates".equals(entry.getKey())
                    && !"nodeLifecycleState".equals(entry.getKey())
                    && !"taskLifecycleState".equals(entry.getKey())
                    && !"inputInterfaceName".equals(entry.getKey())
                    && !"signalName".equals(entry.getKey())
                    && !"payload".equals(entry.getKey())) {
                current.set(entry.getKey(), entry.getValue().deepCopy());
            }
        });
        return current;
    }

    private void mergeObject(ObjectNode target, JsonNode updates) {
        updates.fields().forEachRemaining(entry -> {
            JsonNode existing = target.get(entry.getKey());
            if (existing != null && existing.isObject() && entry.getValue().isObject())
                mergeObject((ObjectNode) existing, entry.getValue());
            else
                target.set(entry.getKey(), entry.getValue().deepCopy());
        });
    }

    ObjectNode mapPortValues(FlowNode sourceNode, FlowNode targetNode, TaskStep sourceStep) {
        ObjectNode values = JsonNodeSupport.objectNode();
        if (sourceNode.getPorts() == null || sourceNode.getPorts().isEmpty() || targetNode.getPorts() == null
                || targetNode.getPorts().isEmpty()) return values;
        var definition = workflowService.getDefinition(sourceNode.getFlowModelId());
        JsonNode connections = definition == null ? null : definition.getPortConnections();
        if (connections == null || !connections.isArray()) return values;
        var compiled = workflowService.compileDefinition(sourceNode.getFlowModelId());
        for (JsonNode connection : connections) {
            JsonNode source = connection.path("source");
            JsonNode target = connection.path("target");
            Long sourceRef = compiled.refsByNodeName().get(source.path("nodeName").asText(""));
            Long targetRef = compiled.refsByNodeName().get(target.path("nodeName").asText(""));
            if (!java.util.Objects.equals(sourceRef, sourceNode.getNodeIdRef())
                    || !java.util.Objects.equals(targetRef, targetNode.getNodeIdRef())) continue;
            String sourcePortName = source.path("portName").asText("");
            String targetPortName = target.path("portName").asText("");
            JsonNode sourcePort = requiredNamed(sourceNode.getPorts(), sourcePortName, "源端口");
            JsonNode targetPort = requiredNamed(targetNode.getPorts(), targetPortName, "目标端口");
            if (!"OUT".equals(sourcePort.path("direction").asText())
                    || !"IN".equals(targetPort.path("direction").asText())) {
                throw new IllegalStateException("端口连接必须从OUT指向IN: " + sourcePortName + "→" + targetPortName);
            }
            String sourceVariableName = sourcePort.path("internalVariableName").asText("");
            String targetVariableName = targetPort.path("internalVariableName").asText("");
            JsonNode sourceVariable = requiredNamed(sourceNode.getInVariables(), sourceVariableName, "源变量");
            JsonNode targetVariable = requiredNamed(targetNode.getInVariables(), targetVariableName, "目标变量");
            String sourceType = sourceVariable.path("dataType").asText("");
            String targetType = targetVariable.path("dataType").asText("");
            if (!sourceType.equals(targetType)) {
                throw new IllegalStateException("端口连接变量数据类型不一致: " + sourceType + "→" + targetType);
            }
            JsonNode value = sourceStep.getVariableSpace() == null
                    ? null : sourceStep.getVariableSpace().get(sourceVariableName);
            if (value == null || value.isNull() || value.isMissingNode()) {
                Task task = runtime.task(sourceStep.getTaskId());
                runtime.appendStepLog(task, sourceStep, "WARN",
                        "端口" + sourcePortName + "绑定变量" + sourceVariableName + "尚无值");
                continue;
            }
            requireValueType(targetVariableName, targetType, value);
            values.set(targetVariableName, value.deepCopy());
        }
        return values;
    }

    private JsonNode requiredNamed(JsonNode definitions, String name, String label) {
        for (JsonNode definition : iterable(definitions)) {
            if (name.equals(definition.path("name").asText())) return definition;
        }
        throw new IllegalStateException(label + "不存在: " + name);
    }

    private void requireValueType(String variableName, String dataType, JsonNode value) {
        boolean valid = switch (dataType) {
            case "INTEGER" -> value.isIntegralNumber();
            case "DOUBLE" -> value.isNumber();
            case "STRING" -> value.isTextual();
            case "BOOLEAN" -> value.isBoolean();
            case "JSON" -> value.isObject() || value.isArray();
            default -> false;
        };
        if (!valid) throw new IllegalStateException("端口目标变量" + variableName + "要求" + dataType);
    }

    void handleStateMachineSignal(StateMachineInterfaceSignalEvent event) {
        interfaceConnections.onStateMachineSignal(event);
    }

    private String nodeName(FlowNode node) {
        if (node == null || node.getFlowModelId() == null || node.getNodeIdRef() == null) return null;
        try {
            return workflowService.nodeName(node.getFlowModelId(), node.getNodeIdRef());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean isPollableTask(String status) {
        return "RUNNING".equals(status) || "TERMINATING".equals(status);
    }

    private void failTaskIfNodeFailed(Task task, TaskStep step) {
        if (!"FAILED".equals(step.getNodeStatus())) return;
        Task current = runtime.task(task.getId());
        if (current != null && ("RUNNING".equals(current.getTaskStatus()) || "PAUSED".equals(current.getTaskStatus()))) {
            runtime.failTask(current, "节点失败: " + step.getNodeIdRef());
        }
    }

    private List<TaskStep> pollableSteps(List<TaskStep> steps) {
        if (steps == null || steps.isEmpty()) return List.of();
        return steps.stream()
                .filter(step -> ACTIVE_NODE_STATES.contains(step.getNodeStatus())
                        || (TERMINAL_NODE_STATES.contains(step.getNodeStatus())
                        && !terminalObservationSettled(step)))
                .toList();
    }

    private boolean terminalObservationSettled(TaskStep step) {
        JsonNode variableSpace = step.getVariableSpace();
        return variableSpace != null
                && variableSpace.path("_triggerStates")
                        .path(WorkflowTriggerState.TERMINAL_OBSERVED_KEY).asBoolean(false);
    }

    private String functionType(FlowNode node) {
        return node.getCapability() == null ? "" : node.getCapability().path("functionType").asText("");
    }

    private FlowNode nodeByRef(Long flowModelId, long nodeIdRef) {
        return workflowService.nodes(flowModelId).stream().filter(node -> node.getNodeIdRef() == nodeIdRef).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("流程节点不存在: " + nodeIdRef));
    }

    private JsonNode mergeVariables(Task task, TaskStep step) {
        ObjectNode result = JsonNodeSupport.objectNode();
        if (task.getTaskVariables() != null && task.getTaskVariables().isObject())
            task.getTaskVariables().fields()
                    .forEachRemaining(entry -> result.set(entry.getKey(), entry.getValue().deepCopy()));
        if (step.getVariableSpace() != null && step.getVariableSpace().isObject())
            step.getVariableSpace().fields()
                    .forEachRemaining(entry -> {
                        if (!"_triggerStates".equals(entry.getKey())) {
                            result.set(entry.getKey(), entry.getValue().deepCopy());
                        }
                    });
        return result;
    }

    private Iterable<JsonNode> iterable(JsonNode node) {
        return node != null && node.isArray() ? node : List.of();
    }

    record ActionRunResult(boolean waiting, String interfaceName, String signalName, ObjectNode variables) {
        private static ActionRunResult awaitingExternalSignal() {
            return new ActionRunResult(true, null, null, null);
        }

        private static ActionRunResult continueWithoutEmission() {
            return new ActionRunResult(false, null, null, null);
        }

        private static ActionRunResult emitted(String interfaceName, String signalName, ObjectNode variables) {
            return new ActionRunResult(false, interfaceName, signalName,
                    variables == null ? JsonNodeSupport.objectNode() : variables.deepCopy());
        }

        private boolean hasEmission() {
            return interfaceName != null && !interfaceName.isBlank();
        }
    }
}
