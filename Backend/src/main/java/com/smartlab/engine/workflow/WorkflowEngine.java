package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.constraint.ConstraintExpressionEvaluator;
import com.smartlab.engine.statemachine.StateMachineInterfaceSignalEvent;
import com.smartlab.engine.workflow.action.WorkflowActionContext;
import com.smartlab.engine.workflow.action.WorkflowActionDefinition;
import com.smartlab.engine.workflow.action.WorkflowActionRegistry;
import com.smartlab.engine.workflow.action.WorkflowActionResult;
import com.smartlab.engine.workflow.action.WorkflowActionStatus;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.workflow.FlowNodeService;
import com.smartlab.management.service.db.workflow.TaskService;
import com.smartlab.management.service.db.workflow.WorkflowRuntimeService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 工作流运行时只解释最终工作流模型：WORKFLOW或STATE输入接口收到信号后，计算该接口的bindingTriggers并执行其引用的EMIT或UPDATE动作
 * START节点是唯一没有上游输入的例外，它在任务启动时执行自身声明的初始化动作
 */
@Service
public class WorkflowEngine {
    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);
    private static final int MAX_SUB_FLOW_DEPTH = 32;
    private static final Object[] TASK_LOCKS = createTaskLocks(256);

    private final WorkflowRuntimeService runtime;
    private final WorkflowService workflowService;
    private final FlowNodeService flowNodeService;
    private final WorkflowConditionEvaluator conditionEvaluator;
    private final ConstraintExpressionEvaluator expressionEvaluator;
    private final WorkflowActionRegistry actionRegistry;
    private final WorkflowExecutionOperations executionOperations;
    private final TaskService taskService;

    public WorkflowEngine(WorkflowRuntimeService runtime, WorkflowService workflowService,
            FlowNodeService flowNodeService, WorkflowConditionEvaluator conditionEvaluator,
            ConstraintExpressionEvaluator expressionEvaluator, WorkflowActionRegistry actionRegistry,
            WorkflowExecutionOperations executionOperations, TaskService taskService) {
        this.runtime = runtime;
        this.workflowService = workflowService;
        this.flowNodeService = flowNodeService;
        this.conditionEvaluator = conditionEvaluator;
        this.expressionEvaluator = expressionEvaluator;
        this.actionRegistry = actionRegistry;
        this.executionOperations = executionOperations;
        this.taskService = taskService;
    }

    @Scheduled(fixedDelay = 1000)
    public void driveWorkflows() {
        for (Task task : runtime.runningTasks()) {
            try {
                processTask(task);
            } catch (Exception error) {
                log.error("工作流任务{}调度失败", task.getId(), error);
                runtime.failTask(task, error.getMessage());
            }
        }
    }

    void processTask(Task task) {
        synchronized (taskLock(task.getId())) {
            processTaskLocked(task);
        }
    }

    private void processTaskLocked(Task task) {
        List<TaskStep> active = runtime.activeSteps(task.getId());
        if (active.isEmpty()) {
            if (runtime.steps(task.getId()).isEmpty())
                startFlow(task, task.getFlowModelId(), null, 0);
            else
                runtime.failTask(task, "流程没有活动步骤且尚未到达END");
            return;
        }
        for (TaskStep step : active) {
            if (!"RUNNING".equals(runtime.task(task.getId()).getTaskStatus()))
                return;
            try {
                processStep(task, step);
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
                JsonNodeSupport.objectNode());
    }

    private void processStep(Task task, TaskStep step) {
        FlowNode node = flowNodeService.getById(step.getFlowNodeId());
        if (node == null)
            throw new IllegalArgumentException("找不到FLOW_NODE: " + step.getFlowNodeId());
        if ("PENDING".equals(step.getNodeStatus()))
            runtime.startStep(task, step);
        String inputInterface = step.getInterfaceInSnapshot() == null ? ""
                : step.getInterfaceInSnapshot().path("targetInterfaceName").asText("");

        if (isStartNode(node) && inputInterface.isBlank()) {
            ActionRunResult result = executeStartActions(task, step, node);
            settleEmittedWorkflowSignal(task, step, node, result);
            if (!result.waiting() && !result.hasEmission())
                throw new IllegalStateException("START节点必须通过EMIT动作发送ACTIVE");
            return;
        }
        if ("AGGREGATE".equals(functionType(node)) && !aggregateReady(task, step, node))
            return;

        ActionRunResult result = executeBindingTriggers(task, step, node, inputInterface);
        if (result.waiting())
            return;
        if (result.hasEmission()) {
            settleEmittedWorkflowSignal(task, step, node, result);
            return;
        }

        switch (node.getNodeType()) {
            case "DEV_NODE" -> throw new IllegalStateException("DEV_NODE输入触发器未发送STATE控制信号");
            case "SUBFLOW_NODE" -> executeSubFlow(task, step, node);
            case "FUNC_NODE" -> {
                if ("END".equals(functionType(node)))
                    finishFlow(task, step, node);
                else if (!"AGGREGATE".equals(functionType(node))) {
                    throw new IllegalStateException(functionType(node) + "节点输入触发器未发送WORKFLOW信号");
                }
            }
            default -> throw new IllegalArgumentException("不支持的节点类型: " + node.getNodeType());
        }
    }

    /** START没有上游接口输入，任务启动就是它的固定系统输入，因此只在这里直接执行声明的初始化动作 */
    private ActionRunResult executeStartActions(Task task, TaskStep step, FlowNode node) {
        ObjectNode variables = actionVariables(task, step, null, node);
        return executeActions(task, step, node, node.getActions(), variables);
    }

    private ActionRunResult executeBindingTriggers(Task task, TaskStep step, FlowNode node, String inputInterfaceName) {
        if (inputInterfaceName == null || inputInterfaceName.isBlank()) {
            if ("END".equals(functionType(node)))
                return ActionRunResult.continueWithoutEmission();
            throw new IllegalStateException("节点缺少实际进入的输入接口");
        }
        JsonNode inputInterface = interfaceByName(node, inputInterfaceName);
        if (inputInterface == null || !"IN".equals(inputInterface.path("direction").asText())) {
            throw new IllegalArgumentException("节点输入接口不存在或不是IN: " + inputInterfaceName);
        }
        ObjectNode variables = actionVariables(task, step, inputInterface, node);
        return executeActions(task, step, node, collectMatchedActions(node, inputInterface, variables), variables);
    }

    List<JsonNode> collectMatchedActions(FlowNode node, JsonNode inputInterface, ObjectNode variables) {
        java.util.LinkedHashMap<String, JsonNode> matched = new java.util.LinkedHashMap<>();
        for (JsonNode trigger : iterable(inputInterface.path("bindingTriggers"))) {
            if (!conditionEvaluator.evaluate(trigger.path("condition"), variables)) continue;
            String actionName = trigger.path("action").asText("");
            matched.putIfAbsent(actionName, actionByName(node, actionName));
        }
        return List.copyOf(matched.values());
    }

    private ObjectNode actionVariables(Task task, TaskStep step, JsonNode inputInterface, FlowNode node) {
        ObjectNode variables = (ObjectNode) mergeVariables(task, step);
        ObjectNode mapped = executionOperations.resolveMappedVariables(task, step, node);
        if (mapped != null && !mapped.isEmpty()) {
            runtime.mergeVariableSpace(step, mapped);
            mergeObject(variables, mapped);
        }
        JsonNode input = step.getInterfaceInSnapshot();
        if (input != null && input.isObject()) {
            variables.set("input", input.deepCopy());
            variables.put("inputSignalName", input.path("inputSignalName").asText(""));
            variables.set("inputPayload", input.path("inputPayload").deepCopy());
            String signalName = input.path("inputSignalName").asText("");
            if (!signalName.isBlank())
                variables.put(signalName, true);
        }
        variables.put("nodeLifecycleState", step.getNodeStatus());
        if (inputInterface != null)
            variables.put("inputInterfaceName", inputInterface.path("name").asText(""));
        String expression = "FUNC_NODE".equals(node.getNodeType()) && node.getCapability() != null
                ? node.getCapability().path("expression").asText("").trim()
                : "";
        if (!expression.isBlank()) {
            Map<String, JsonNode> expressionVariables = new java.util.LinkedHashMap<>();
            variables.fields().forEachRemaining(entry -> expressionVariables.put(entry.getKey(), entry.getValue()));
            variables.put("expression",
                    expressionEvaluator.evaluate(expression, expressionVariables, Map.of(), Instant.now()));
        }
        return variables;
    }

    ActionRunResult executeActions(Task task, TaskStep step, FlowNode node, Iterable<JsonNode> actions,
            ObjectNode variables) {
        List<WorkflowActionDefinition> updates = new java.util.ArrayList<>();
        List<WorkflowActionDefinition> emits = new java.util.ArrayList<>();
        for (JsonNode actionNode : actions) {
            WorkflowActionDefinition action = WorkflowActionDefinition.from(actionNode);
            switch (action.actionType()) {
                case "UPDATE" -> updates.add(action);
                case "EMIT" -> emits.add(action);
                default -> throw new IllegalArgumentException("不支持的工作流动作类型: " + action.actionType());
            }
        }
        if (emits.size() > 1) throw new IllegalStateException("同一输入命中多个EMIT动作");
        for (WorkflowActionDefinition update : updates) {
            WorkflowActionResult result = executeAction(task, step, node, variables, update);
            if (result.status() != WorkflowActionStatus.CONTINUE) {
                throw new IllegalStateException("UPDATE动作不能挂起工作流节点: " + update.actionName());
            }
        }
        if (emits.isEmpty()) return ActionRunResult.continueWithoutEmission();
        WorkflowActionResult result = executeAction(task, step, node, variables, emits.get(0));
        if (result.status() != WorkflowActionStatus.CONTINUE) return ActionRunResult.awaitingExternalSignal();
        if (result.emittedInterfaceName() == null || result.emittedInterfaceName().isBlank()) {
            throw new IllegalStateException("EMIT动作未产生输出接口: " + emits.get(0).actionName());
        }
        return ActionRunResult.emitted(result.emittedInterfaceName(), result.emittedSignalName(), variables);
    }

    private WorkflowActionResult executeAction(Task task, TaskStep step, FlowNode node, ObjectNode variables,
            WorkflowActionDefinition action) {
        WorkflowActionResult result = actionRegistry.required(action.actionType()).execute(
                action, new WorkflowActionContext(task, step, node, variables, Instant.now(), executionOperations));
        if (!result.variableUpdates().isEmpty()) {
            runtime.mergeVariableSpace(step, result.variableUpdates());
            mergeObject(variables, result.variableUpdates());
        }
        return result;
    }

    private void settleEmittedWorkflowSignal(Task task, TaskStep step, FlowNode node, ActionRunResult result) {
        if (!result.hasEmission())
            return;
        ObjectNode output = JsonNodeSupport.objectNode();
        output.put("signalName", result.signalName());
        output.set("payload", result.variables().deepCopy());
        completeAndRoute(task, step, node, result.interfaceName(), result.signalName(), output);
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

    private void executeSubFlow(Task task, TaskStep step, FlowNode node) {
        boolean hasChildren = runtime.steps(task.getId()).stream()
                .anyMatch(child -> step.getId().equals(child.getParentStepId()));
        if (!hasChildren)
            startFlow(task, node.getSubFlowModelId(), step.getId(), step.getStepDepth() + 1);
    }

    private void finishFlow(Task task, TaskStep endStep, FlowNode endNode) {
        runtime.completeStep(endStep, JsonNodeSupport.objectNode());
        if (endStep.getParentStepId() == null) {
            runtime.completeTask(task);
            return;
        }
        TaskStep parentStep = runtime.step(endStep.getParentStepId());
        if (parentStep == null)
            throw new IllegalStateException("子流程父步骤不存在");
        FlowNode parentNode = flowNodeService.getById(parentStep.getFlowNodeId());
        runtime.completeStep(parentStep, JsonNodeSupport.objectNode().put("subFlowModelId", endNode.getFlowModelId()));
        String output = workflowOutputInterface(parentNode);
        completeAndRoute(task, parentStep, parentNode, output, "ACTIVE", parentStep.getInterfaceOutSnapshot());
    }

    private void completeAndRoute(Task task, TaskStep step, FlowNode node, String sourceInterface, String signalName,
            JsonNode output) {
        runtime.completeStep(step, output);
        route(task, step, node, sourceInterface, signalName);
    }

    private void route(Task task, TaskStep completed, FlowNode node, String sourceInterface, String signalName) {
        var compiled = workflowService.compileDefinition(node.getFlowModelId());
        for (var connection : compiled.outgoing(node.getNodeIdRef())) {
            if (!sourceInterface.equals(connection.sourceInterface()))
                continue;
            FlowNode target = nodeByRef(node.getFlowModelId(), connection.targetNodeIdRef());
            JsonNode targetInterface = interfaceByName(target, connection.targetInterface());
            if (!allows(targetInterface, signalName)) {
                throw new IllegalStateException(
                        "目标接口不允许信号: " + target.getNodeIdRef() + "." + connection.targetInterface() + "." + signalName);
            }
            ObjectNode input = JsonNodeSupport.objectNode();
            input.put("sourceNodeIdRef", node.getNodeIdRef());
            input.put("sourceInterface", sourceInterface);
            input.put("targetInterfaceName", connection.targetInterface());
            input.put("inputSignalName", signalName);
            input.set("inputPayload", completed.getInterfaceOutSnapshot() == null ? JsonNodeSupport.objectNode()
                    : completed.getInterfaceOutSnapshot().deepCopy());
            if (completed.getInterfaceOutSnapshot() != null)
                input.set("sourceOutput", completed.getInterfaceOutSnapshot().deepCopy());
            TaskStep targetStep = runtime.createStep(task, target, completed.getParentStepId(),
                    completed.getStepDepth(), input);
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
            String messageId = event.executionContext() == null ? ""
                    : String.valueOf(event.executionContext().getOrDefault("messageId", ""));
            if (messageId.isBlank())
                return;
            TaskStep step = runtime.findRunningDeviceStepByMessageId(messageId);
            if (step != null)
                handleStateMachineSignalForStep(event, step, messageId);
            return;
        }

        Long deviceInstanceId = signal.path("payload").path("deviceInstanceId").canConvertToLong()
                ? signal.path("payload").path("deviceInstanceId").asLong()
                : null;
        if (deviceInstanceId == null || deviceInstanceId <= 0)
            return;
        for (TaskStep step : runtime.findRunningDeviceStepsByInstanceId(deviceInstanceId)) {
            handleStateMachineSignalForStep(event, step, "");
        }
    }

    private void handleStateMachineSignalForStep(StateMachineInterfaceSignalEvent event, TaskStep located,
            String messageId) {
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
            ObjectNode input = step.getInterfaceInSnapshot() != null && step.getInterfaceInSnapshot().isObject()
                    ? (ObjectNode) step.getInterfaceInSnapshot().deepCopy()
                    : JsonNodeSupport.objectNode();
            input.put("targetInterfaceName", inputInterface);
            input.put("inputSignalName", event.signal().path("signalName").asText());
            input.set("inputPayload", event.signal().path("payload").deepCopy());
            input.put("sourceInterface", "Interface_state_out");
            runtime.updateInputSnapshot(step, input);

            String signalName = event.signal().path("signalName").asText();
            String cmdState = "CMD_STATE".equals(signalName)
                    ? event.signal().path("payload").path("stateName").asText("")
                    : "";
            if ("FAILED".equals(cmdState)) {
                runtime.failStep(step, "设备指令状态: " + cmdState);
                return;
            }
            if ("TERMINATING".equals(step.getNodeStatus()) && !"ABORTED".equals(cmdState))
                return;

            if ("ABORTED".equals(cmdState)) {
                if ("TERMINATING".equals(task.getTaskStatus())) {
                    runtime.terminateStep(step,
                            JsonNodeSupport.objectNode().put("messageId", messageId).put("cmdState", cmdState));
                    taskService.completeTerminationIfSettled(task.getId());
                } else {
                    runtime.failStep(step, "设备指令状态: " + cmdState);
                }
                return;
            }

            ActionRunResult result = executeBindingTriggers(task, step, node, inputInterface);
            if (result.waiting())
                return;
            if (result.hasEmission()) {
                if ("CMD_STATE".equals(signalName) && !"COMPLETED".equals(cmdState)) {
                    runtime.failStep(step, "设备指令状态" + cmdState + "不允许通过STATE输入接口提前完成节点");
                    return;
                }
                settleEmittedWorkflowSignal(task, step, node, result);
                return;
            }
            if ("COMPLETED".equals(cmdState)) {
                runtime.failStep(step, "DEV_NODE收到CMD_STATE=COMPLETED后未通过bindingTriggers发送WORKFLOW信号");
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

    private String workflowOutputInterface(FlowNode node) {
        String selected = null;
        for (JsonNode item : iterable(node.getInterfaces())) {
            if (!"OUT".equals(item.path("direction").asText())
                    || !"WORKFLOW".equals(item.path("interfaceType").asText()))
                continue;
            if (selected != null)
                throw new IllegalArgumentException("节点存在多个WORKFLOW输出接口，必须由EMIT动作明确选择: " + node.getNodeIdRef());
            selected = item.path("name").asText();
        }
        if (selected == null || selected.isBlank())
            throw new IllegalArgumentException("节点缺少WORKFLOW输出接口: " + node.getNodeIdRef());
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

    private JsonNode actionByName(FlowNode node, String actionName) {
        for (JsonNode action : iterable(node.getActions())) {
            if (actionName.equals(action.path("actionName").asText()))
                return action;
        }
        throw new IllegalArgumentException("接口触发器引用不存在动作: " + actionName);
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

    private boolean isStartNode(FlowNode node) {
        return "FUNC_NODE".equals(node.getNodeType()) && "START".equals(functionType(node));
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
                    .forEachRemaining(entry -> result.set(entry.getKey(), entry.getValue().deepCopy()));
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
