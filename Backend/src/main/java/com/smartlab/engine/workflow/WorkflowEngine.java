package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 工作流运行时按节点轮询接口触发器；设备事件只持久化输入，由下一轮统一求值。 */
@Service
public class WorkflowEngine {
    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);
    private static final int MAX_SUB_FLOW_DEPTH = 32;
    private static final Set<String> TERMINAL_NODE_STATES = Set.of("SUCCEEDED", "FAILED", "TERMINATED");
    private static final Object[] TASK_LOCKS = createTaskLocks(256);

    private final WorkflowRuntimeService runtime;
    private final WorkflowService workflowService;
    private final FlowNodeService flowNodeService;
    private final WorkflowConditionEvaluator conditionEvaluator;
    private final ConstraintExpressionEvaluator expressionEvaluator;
    private final WorkflowActionRegistry actionRegistry;
    private final WorkflowExecutionOperations executionOperations;
    private final ObservableSnapshotReader observationReader;
    private final Executor workflowExecutor;
    private final Set<Long> inFlightTaskIds = ConcurrentHashMap.newKeySet();

    @Autowired
    public WorkflowEngine(WorkflowRuntimeService runtime, WorkflowService workflowService,
            FlowNodeService flowNodeService, WorkflowConditionEvaluator conditionEvaluator,
            ConstraintExpressionEvaluator expressionEvaluator, WorkflowActionRegistry actionRegistry,
            WorkflowExecutionOperations executionOperations, ObservableSnapshotReader observationReader,
            @Qualifier("workflowEngineExecutor") Executor workflowExecutor) {
        this.runtime = runtime;
        this.workflowService = workflowService;
        this.flowNodeService = flowNodeService;
        this.conditionEvaluator = conditionEvaluator;
        this.expressionEvaluator = expressionEvaluator;
        this.actionRegistry = actionRegistry;
        this.executionOperations = executionOperations;
        this.observationReader = observationReader;
        this.workflowExecutor = workflowExecutor;
    }

    public WorkflowEngine(WorkflowRuntimeService runtime, WorkflowService workflowService,
            FlowNodeService flowNodeService, WorkflowConditionEvaluator conditionEvaluator,
            ConstraintExpressionEvaluator expressionEvaluator, WorkflowActionRegistry actionRegistry,
            WorkflowExecutionOperations executionOperations) {
        this(runtime, workflowService, flowNodeService, conditionEvaluator, expressionEvaluator,
                actionRegistry, executionOperations, emptyObservationReader(), Runnable::run);
    }

    public WorkflowEngine(WorkflowRuntimeService runtime, WorkflowService workflowService,
            FlowNodeService flowNodeService, WorkflowConditionEvaluator conditionEvaluator,
            ConstraintExpressionEvaluator expressionEvaluator, WorkflowActionRegistry actionRegistry,
            WorkflowExecutionOperations executionOperations, ObservableSnapshotReader observationReader) {
        this(runtime, workflowService, flowNodeService, conditionEvaluator, expressionEvaluator,
                actionRegistry, executionOperations, observationReader, Runnable::run);
    }

    public WorkflowEngine(WorkflowRuntimeService runtime, WorkflowService workflowService,
            FlowNodeService flowNodeService, WorkflowConditionEvaluator conditionEvaluator,
            ConstraintExpressionEvaluator expressionEvaluator, WorkflowActionRegistry actionRegistry,
            WorkflowExecutionOperations executionOperations, Executor workflowExecutor) {
        this(runtime, workflowService, flowNodeService, conditionEvaluator, expressionEvaluator,
                actionRegistry, executionOperations, emptyObservationReader(), workflowExecutor);
    }

    @Scheduled(fixedDelayString = "${smartlab.workflow.poll-interval-ms:100}")
    public void driveWorkflows() {
        for (Task task : runtime.runningTasks()) {
            dispatchTask(task.getId());
        }
    }

    void dispatchTask(Long taskId) {
        if (taskId == null || !inFlightTaskIds.add(taskId)) return;
        try {
            workflowExecutor.execute(() -> {
                try {
                    Task task = runtime.task(taskId);
                    if (task != null && "RUNNING".equals(task.getTaskStatus())) {
                        processTask(task);
                    }
                } catch (Exception error) {
                    log.error("工作流任务{}调度失败", taskId, error);
                    Task task = runtime.task(taskId);
                    if (task != null) runtime.failTask(task, error.getMessage());
                } finally {
                    inFlightTaskIds.remove(taskId);
                }
            });
        } catch (RuntimeException error) {
            inFlightTaskIds.remove(taskId);
            log.warn("工作流任务{}本轮未进入执行线程池: {}", taskId, error.getMessage());
        }
    }

    void processTask(Task task) {
        synchronized (taskLock(task.getId())) {
            processTaskLocked(task);
        }
    }

    private void processTaskLocked(Task task) {
        List<TaskStep> pollable = runtime.pollableSteps(task.getId());
        if (pollable.isEmpty()) {
            if (runtime.steps(task.getId()).isEmpty())
                startFlow(task, task.getFlowModelId(), null, 0);
            return;
        }
        for (TaskStep step : pollable) {
            if (!"RUNNING".equals(runtime.task(task.getId()).getTaskStatus()))
                return;
            try {
                boolean terminalAtPollStart = TERMINAL_NODE_STATES.contains(step.getNodeStatus());
                processStep(task, step);
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
        if ("SUBFLOW_NODE".equals(node.getNodeType()) && "RUNNING".equals(step.getNodeStatus())) {
            int depth = step.getStepDepth() == null ? 0 : step.getStepDepth();
            startFlow(task, node.getSubFlowModelId(), step.getId(), depth + 1);
        }
        if ("AGGREGATE".equals(functionType(node)) && !aggregateReady(task, step, node))
            return;
        ObjectNode frozenSnapshot = actionVariables(task, step, null, node);
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
        ArrayNode input = WorkflowInterfaceSnapshots.withSignal(
                parent.getInterfaceInSnapshot(), parentNode.getInterfaces(), "IN",
                "Interface_workflow_in", WorkflowNodeSignal.SUBFLOW_COMPLETED.name(), null);
        runtime.updateInputSnapshot(parent, input);
    }

    private void evaluateNodeTriggers(Task task, TaskStep step, FlowNode node, ObjectNode frozenSnapshot) {
        ObjectNode triggerStates = triggerStates(step);
        List<JsonNode> orderedInterfaces = new java.util.ArrayList<>();
        for (String direction : List.of("OUT", "IN")) {
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
                    WorkflowActionResult result = executeAction(task, step, node, interfaceSnapshot,
                            WorkflowActionDefinition.from(trigger.path("action")));
                    if (result.status() != WorkflowActionStatus.WAIT_DEVICE_IDLE) {
                        triggerStates.put(key, true);
                        persistTriggerStates(step, triggerStates);
                    }
                    if (result.emittedInterfaceName() != null && !result.emittedInterfaceName().isBlank()) {
                        routeEmission(task, step, node, result);
                    }
                } else if (!current && previous) {
                    triggerStates.put(key, false);
                    persistTriggerStates(step, triggerStates);
                }
            }
        }
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

    private void routeEmission(Task task, TaskStep step, FlowNode node, WorkflowActionResult result) {
        ArrayNode output = WorkflowInterfaceSnapshots.withSignal(
                step.getInterfaceOutSnapshot(), node.getInterfaces(), "OUT",
                result.emittedInterfaceName(), result.emittedSignalName(), null);
        runtime.updateOutputSnapshot(step, output);
        route(task, step, node, result.emittedInterfaceName(), result.emittedSignalName());
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
        WorkflowActionResult result = actionRegistry.required(action.actionName()).execute(
                action, new WorkflowActionContext(task, step, node, variables, Instant.now(), executionOperations));
        if (!result.variableUpdates().isEmpty()) {
            runtime.mergeVariableSpace(step, result.variableUpdates());
        }
        return result;
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

    private void route(Task task, TaskStep completed, FlowNode node, String sourceInterface, String signalName) {
        var compiled = workflowService.compileDefinition(node.getFlowModelId());
        for (var connection : compiled.outgoing(node.getNodeIdRef())) {
            if (!sourceInterface.equals(connection.sourceInterface()))
                continue;
            FlowNode target = nodeByRef(node.getFlowModelId(), connection.targetNodeIdRef());
            JsonNode targetInterface = interfaceByName(target, connection.targetInterface());
            if (!allows(targetInterface, signalName)) continue;
            TaskStep targetStep = runtime.createStep(task, target, completed.getParentStepId(),
                    completed.getStepDepth(), null);
            JsonNode sourceSlot = WorkflowInterfaceSnapshots.find(
                    completed.getInterfaceOutSnapshot(), sourceInterface);
            JsonNode payload = sourceSlot.path("payload");
            ArrayNode input = WorkflowInterfaceSnapshots.withSignal(
                    targetStep.getInterfaceInSnapshot(), target.getInterfaces(), "IN",
                    connection.targetInterface(), signalName, payload.isObject() ? payload : null);
            runtime.updateInputSnapshot(targetStep, input);
            runtime.mergeVariableSpace(targetStep, mapPortValues(node, target, completed));
        }
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

    @EventListener
    public void handleStateMachineSignal(StateMachineInterfaceSignalEvent event) {
        JsonNode signal = event.signal();
        if (!"STATE".equals(event.interfaceType()))
            return;
        String signalName = signal.path("signalName").asText();
        if (!("CMD_STATE".equals(signalName) || "OP_STATE".equals(signalName)))
            return;

        if ("CMD_STATE".equals(signalName)) {
            Long taskStepId = longContext(event, "taskStepId");
            if (taskStepId == null) return;
            TaskStep step = runtime.step(taskStepId);
            if (step != null)
                handleStateMachineSignalForStep(event, step);
            return;
        }

        Long deviceInstanceId = signal.path("payload").path("deviceInstanceId").canConvertToLong()
                ? signal.path("payload").path("deviceInstanceId").asLong()
                : null;
        if (deviceInstanceId == null || deviceInstanceId <= 0)
            return;
        for (TaskStep step : runtime.runningDeviceSteps()) {
            Task task = runtime.task(step.getTaskId());
            FlowNode node = flowNodeService.getById(step.getFlowNodeId());
            if (task == null || node == null || !"DEV_NODE".equals(node.getNodeType())) continue;
            if (executionOperations.resolveDeviceInstance(task, step, node) == deviceInstanceId) {
                handleStateMachineSignalForStep(event, step);
            }
        }
    }

    private Long longContext(StateMachineInterfaceSignalEvent event, String key) {
        if (event.executionContext() == null) return null;
        Object value = event.executionContext().get(key);
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void handleStateMachineSignalForStep(StateMachineInterfaceSignalEvent event, TaskStep located) {
        synchronized (taskLock(located.getTaskId())) {
            TaskStep step = runtime.step(located.getId());
            if (step == null || !("RUNNING".equals(step.getNodeStatus()) || "TERMINATING".equals(step.getNodeStatus())))
                return;
            Task task = runtime.task(step.getTaskId());
            FlowNode node = flowNodeService.getById(step.getFlowNodeId());
            if (task == null || node == null)
                return;
            String inputInterface = stateInputInterface(node);
            if (!hasDeviceToNodeConnection(task, step, node, event, inputInterface)) {
                runtime.failStep(step, "设备状态回执未声明DEVICE_TO_NODE连接");
                return;
            }
            JsonNode targetInterface = interfaceByName(node, inputInterface);
            String signalName = event.signal().path("signalName").asText("");
            if (!allows(targetInterface, signalName)) return;
            JsonNode payload = event.signal().path("payload");
            ArrayNode input = WorkflowInterfaceSnapshots.withSignal(
                    step.getInterfaceInSnapshot(), node.getInterfaces(), "IN", inputInterface,
                    signalName, payload.isObject() ? payload : null);
            runtime.updateInputSnapshot(step, input);
            ObjectNode mapped = executionOperations.resolveMappedVariables(task, step, node);
            if (mapped != null && !mapped.isEmpty()) {
                runtime.mergeVariableSpace(step, mapped);
            }
        }
    }

    private boolean hasDeviceToNodeConnection(Task task, TaskStep step, FlowNode node,
            StateMachineInterfaceSignalEvent event, String nodeInputInterface) {
        Long deviceInstanceId = event.signal().path("payload").path("deviceInstanceId").canConvertToLong()
                ? event.signal().path("payload").path("deviceInstanceId").asLong()
                : null;
        if (deviceInstanceId == null || deviceInstanceId <= 0)
            return false;
        if (executionOperations.resolveDeviceInstance(task, step, node) != deviceInstanceId)
            return false;
        var definition = workflowService.getDefinition(node.getFlowModelId());
        if (definition == null)
            return false;
        var compiled = workflowService.compileDefinition(node.getFlowModelId());
        for (JsonNode connection : iterable(definition.getInterfaceConnections())) {
            if (!"DEVICE_TO_NODE".equals(connection.path("connectionType").asText()))
                continue;
            Long targetRef = compiled.refsByNodeName().get(connection.path("target").path("nodeName").asText(""));
            if (targetRef == null || targetRef != node.getNodeIdRef())
                continue;
            if (connection.path("source").path("deviceModelId").asLong(0) != node.getDeviceModelId())
                continue;
            if (!event.interfaceName().equals(connection.path("source").path("interfaceName").asText("")))
                continue;
            if (nodeInputInterface.equals(connection.path("target").path("interfaceName").asText("")))
                return true;
        }
        return false;
    }

    private String stateInputInterface(FlowNode node) {
        String selected = null;
        for (JsonNode item : iterable(node.getInterfaces())) {
            if (!"IN".equals(item.path("direction").asText()) || !"STATE".equals(item.path("interfaceType").asText()))
                continue;
            if (selected != null)
                throw new IllegalArgumentException("DEV_NODE存在多个STATE输入接口: " + node.getNodeIdRef());
            selected = item.path("name").asText();
        }
        if (selected == null || selected.isBlank())
            throw new IllegalArgumentException("DEV_NODE缺少STATE输入接口: " + node.getNodeIdRef());
        return selected;
    }

    private boolean aggregateReady(Task task, TaskStep aggregateStep, FlowNode node) {
        var compiled = workflowService.compileDefinition(node.getFlowModelId());
        Set<Long> predecessors = new HashSet<>();
        compiled.incoming(node.getNodeIdRef()).forEach(connection -> predecessors.add(connection.sourceNodeIdRef()));
        List<TaskStep> contextSteps = runtime.steps(task.getId()).stream()
                .filter(item -> java.util.Objects.equals(item.getParentStepId(), aggregateStep.getParentStepId()))
                .filter(item -> java.util.Objects.equals(item.getStepDepth(), aggregateStep.getStepDepth()))
                .toList();
        if (predecessors.isEmpty())
            return false;
        for (Long predecessor : predecessors) {
            List<TaskStep> activated = contextSteps.stream().filter(item -> predecessor.equals(item.getNodeIdRef()))
                    .toList();
            if (activated.isEmpty() || activated.stream().anyMatch(item -> !"SUCCEEDED".equals(item.getNodeStatus())))
                return false;
        }
        return true;
    }

    private JsonNode interfaceByName(FlowNode node, String name) {
        for (JsonNode item : iterable(node.getInterfaces()))
            if (name.equals(item.path("name").asText()))
                return item;
        return null;
    }

    private boolean allows(JsonNode interfaceDefinition, String signalName) {
        if (interfaceDefinition == null)
            return false;
        for (JsonNode item : iterable(interfaceDefinition.path("allowedSignals")))
            if (signalName.equals(item.asText()))
                return true;
        return false;
    }

    private String functionType(FlowNode node) {
        return node.getCapability() == null ? "" : node.getCapability().path("functionType").asText("");
    }

    private FlowNode nodeByRef(Long flowModelId, long nodeIdRef) {
        return workflowService.nodes(flowModelId).stream().filter(node -> node.getNodeIdRef() == nodeIdRef).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("流程节点不存在: " + nodeIdRef));
    }

    private static Object[] createTaskLocks(int count) {
        Object[] locks = new Object[count];
        for (int index = 0; index < count; index++)
            locks[index] = new Object();
        return locks;
    }

    private Object taskLock(Long taskId) {
        if (taskId == null)
            throw new IllegalArgumentException("任务ID不能为空");
        return TASK_LOCKS[Math.floorMod(Long.hashCode(taskId), TASK_LOCKS.length)];
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
