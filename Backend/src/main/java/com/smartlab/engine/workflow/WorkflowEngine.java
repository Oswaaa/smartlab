package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.StateMachineInterfaceSignalEvent;
import com.smartlab.engine.workflow.action.WorkflowActionContext;
import com.smartlab.engine.workflow.action.WorkflowActionDefinition;
import com.smartlab.engine.workflow.action.WorkflowActionRegistry;
import com.smartlab.engine.workflow.action.WorkflowActionStatus;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.workflow.FlowNodeService;
import com.smartlab.management.service.db.workflow.WorkflowRuntimeService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import com.smartlab.management.service.db.workflow.WorkflowTaskResourceService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Executes compiled workflow definitions without accessing database mappers directly. */
@Service
public class WorkflowEngine {
    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);
    private static final int MAX_SUB_FLOW_DEPTH = 32;
    private static final Object[] TASK_LOCKS = createTaskLocks(256);

    private final WorkflowRuntimeService runtime;
    private final WorkflowService workflowService;
    private final FlowNodeService flowNodeService;
    private final WorkflowConditionEvaluator conditionEvaluator;
    private final WorkflowActionRegistry actionRegistry;
    private final WorkflowExecutionOperations executionOperations;

    public WorkflowEngine(WorkflowRuntimeService runtime, WorkflowService workflowService,
                          FlowNodeService flowNodeService, WorkflowConditionEvaluator conditionEvaluator,
                          WorkflowActionRegistry actionRegistry, WorkflowExecutionOperations executionOperations) {
        this.runtime = runtime;
        this.workflowService = workflowService;
        this.flowNodeService = flowNodeService;
        this.conditionEvaluator = conditionEvaluator;
        this.actionRegistry = actionRegistry;
        this.executionOperations = executionOperations;
    }

    @Scheduled(fixedDelay = 1000)
    public void driveWorkflows() {
        for (Task task : runtime.runningTasks()) {
            try {
                processTask(task);
            } catch (Exception e) {
                log.error("工作流任务 {} 调度失败", task.getId(), e);
                runtime.failTask(task, e.getMessage());
            }
        }
    }

    void processTask(Task task) { synchronized (taskLock(task.getId())) { processTaskLocked(task); } }
    private void processTaskLocked(Task task) {
        List<TaskStep> active = runtime.activeSteps(task.getId());
        if (active.isEmpty()) {
            if (runtime.steps(task.getId()).isEmpty()) {
                startFlow(task, task.getFlowModelId(), null, 0);
            } else {
                runtime.failTask(task, "流程没有活动步骤且尚未到达 END");
            }
            return;
        }
        for (TaskStep step : active) {
            if (!"RUNNING".equals(runtime.task(task.getId()).getTaskStatus())) return;
            try {
                processStep(task, step);
            } catch (Exception e) {
                runtime.failStep(step, e.getMessage());
            }
        }
    }

    private void startFlow(Task task, Long flowModelId, Long parentStepId, int depth) {
        if (flowModelId == null) throw new IllegalArgumentException("子流程节点缺少流程模型引用");
        if (depth > MAX_SUB_FLOW_DEPTH) throw new IllegalStateException("子流程嵌套超过 " + MAX_SUB_FLOW_DEPTH + " 层");
        var compiled = workflowService.compileDefinition(flowModelId);
        FlowNode start = nodeByRef(flowModelId, compiled.startNodeIdRef());
        runtime.createStep(task, start, parentStepId, depth, JsonNodeSupport.objectNode());
    }

    private void processStep(Task task, TaskStep step) {
        FlowNode node = flowNodeService.getById(step.getFlowNodeId());
        if (node == null) throw new IllegalArgumentException("找不到 FLOW_NODE: " + step.getFlowNodeId());
        if ("PENDING".equals(step.getNodeStatus())) runtime.startStep(task, step);
        if (!executeActions(task, step, node)) return;

        switch (node.getNodeType()) {
            case "FUNCTIONAL_NODE" -> executeFunctional(task, step, node);
            case "DEVICE_CAPABILITY_NODE" -> throw new IllegalStateException("设备动作链必须以 EMIT_SIGNAL 结束");
            case "SUB_FLOW_NODE" -> executeSubFlow(task, step, node);
            default -> throw new IllegalArgumentException("不支持的节点类型: " + node.getNodeType());
        }
    }

    private void executeFunctional(Task task, TaskStep step, FlowNode node) {
        String functionType = node.getCapability().path("functionType").asText("");
        switch (functionType) {
            case "START" -> completeAndRoute(task, step, node, outputInterface(node), JsonNodeSupport.objectNode());
            case "END" -> finishFlow(task, step, node);
            case "BRANCH" -> {
                if ("RUNNING".equals(step.getNodeStatus())) {
                    String selected = selectBranch(task, step, node);
                    completeAndRoute(task, step, node, selected,
                            JsonNodeSupport.objectNode().put("selectedInterface", selected));
                }
            }
            case "AGGREGATE" -> {
                if (aggregateReady(task, step, node))
                    completeAndRoute(task, step, node, outputInterface(node), JsonNodeSupport.objectNode().put("policy", "ALL"));
            }
            default -> throw new IllegalArgumentException("不支持的功能节点: " + functionType);
        }
    }

    private boolean executeActions(Task task, TaskStep step, FlowNode node) {
        if (node.getActions() == null || !node.getActions().isArray() || node.getActions().isEmpty()) return true;
        ObjectNode variables = (ObjectNode) mergeVariables(task, step);
        for (JsonNode actionNode : node.getActions()) {
            WorkflowActionDefinition action = WorkflowActionDefinition.from(actionNode);
            var result = actionRegistry.required(action.actionName()).execute(
                    action, new WorkflowActionContext(task, step, node, variables, Instant.now(), executionOperations));
            if (!result.variableUpdates().isEmpty()) {
                runtime.mergeVariableSpace(step, result.variableUpdates());
                mergeObject(variables, result.variableUpdates());
            }
            if (result.status() == WorkflowActionStatus.SUSPEND_UNTIL
                    || result.status() == WorkflowActionStatus.AWAIT_EXTERNAL_SIGNAL) return false;
        }
        return true;
    }

    private void mergeObject(ObjectNode target, JsonNode updates) {
        updates.fields().forEachRemaining(entry -> {
            JsonNode existing = target.get(entry.getKey());
            if (existing != null && existing.isObject() && entry.getValue().isObject())
                mergeObject((ObjectNode) existing, entry.getValue());
            else target.set(entry.getKey(), entry.getValue().deepCopy());
        });
    }


    private void executeSubFlow(Task task, TaskStep step, FlowNode node) {
        boolean hasChildren = runtime.steps(task.getId()).stream().anyMatch(child -> step.getId().equals(child.getParentStepId()));
        if (!hasChildren) startFlow(task, node.getSubFlowModelId(), step.getId(), step.getStepDepth() + 1);
    }

    private void finishFlow(Task task, TaskStep endStep, FlowNode endNode) {
        runtime.completeStep(endStep, JsonNodeSupport.objectNode());
        if (endStep.getParentStepId() == null) {
            runtime.completeTask(task);
            return;
        }
        TaskStep parentStep = runtime.step(endStep.getParentStepId());
        if (parentStep == null) throw new IllegalStateException("子流程父步骤不存在");
        FlowNode parentNode = flowNodeService.getById(parentStep.getFlowNodeId());
        runtime.completeStep(parentStep, JsonNodeSupport.objectNode().put("subFlowModelId", endNode.getFlowModelId()));
        route(task, parentStep, parentNode, outputInterface(parentNode));
    }

    private void completeAndRoute(Task task, TaskStep step, FlowNode node, String sourceInterface, JsonNode output) {
        runtime.completeStep(step, output);
        route(task, step, node, sourceInterface);
    }

    private void route(Task task, TaskStep completed, FlowNode node, String sourceInterface) {
        var compiled = workflowService.compileDefinition(node.getFlowModelId());
        for (var connection : compiled.outgoing(node.getNodeIdRef())) {
            if (!sourceInterface.equals(connection.sourceInterface())) continue;
            FlowNode target = nodeByRef(node.getFlowModelId(), connection.targetNodeIdRef());
            ObjectNode input = JsonNodeSupport.objectNode();
            input.put("sourceNodeIdRef", node.getNodeIdRef());
            input.put("sourceInterface", sourceInterface);
            if (completed.getInterfaceOutSnapshot() != null)
                input.set("sourceOutput", completed.getInterfaceOutSnapshot().deepCopy());
            TaskStep targetStep = runtime.createStep(task, target, completed.getParentStepId(), completed.getStepDepth(), input);
            runtime.mergeVariableSpace(targetStep, mapPortValues(node, target, completed));
        }
    }

    private ObjectNode mapPortValues(FlowNode sourceNode, FlowNode targetNode, TaskStep sourceStep) {
        ObjectNode values = JsonNodeSupport.objectNode();
        if (sourceNode.getPorts() == null || sourceNode.getPorts().isEmpty()
                || targetNode.getPorts() == null || targetNode.getPorts().isEmpty()) return values;
        JsonNode connections = workflowService.getDefinition(sourceNode.getFlowModelId()).getPortConnections();
        if (connections == null || !connections.isArray()) return values;
        for (JsonNode connection : connections) {
            JsonNode source = connection.path("source");
            JsonNode target = connection.path("target");
            if (source.path("nodeIdRef").asLong() != sourceNode.getNodeIdRef()
                    || target.path("nodeIdRef").asLong() != targetNode.getNodeIdRef()) continue;
            String sourcePortName = source.path("portName").asText("");
            String targetPortName = target.path("portName").asText("");
            String sourceVariable = portVariable(sourceNode.getPorts(), sourcePortName, sourcePortName);
            String targetVariable = portVariable(targetNode.getPorts(), targetPortName, targetPortName);
            JsonNode value = valueFromStep(sourceStep, sourceVariable);
            if (value != null && !value.isMissingNode()) values.set(targetVariable, value.deepCopy());
        }
        return values;
    }

    private String portVariable(JsonNode ports, String portName, String fallback) {
        if (ports != null && ports.isArray()) {
            for (JsonNode port : ports) {
                if (portName.equals(port.path("name").asText()))
                    return port.path("internalVariableName").asText(fallback);
            }
        }
        return fallback;
    }

    private JsonNode valueFromStep(TaskStep step, String variableName) {
        JsonNode value = step.getVariableSpace() == null ? null : step.getVariableSpace().get(variableName);
        if (value != null) return value;
        return step.getInterfaceOutSnapshot() == null ? null : step.getInterfaceOutSnapshot().get(variableName);
    }

    private String outputInterface(FlowNode node) {
        String selected = null;
        if (node.getInterfaces() != null && node.getInterfaces().isArray()) {
            for (JsonNode item : node.getInterfaces()) {
                if (!"OUT".equals(item.path("direction").asText())) continue;
                if (selected != null) throw new IllegalArgumentException("非分支节点只能声明一个输出接口: " + node.getNodeIdRef());
                selected = item.path("name").asText("");
            }
        }
        if (selected == null || selected.isBlank())
            throw new IllegalArgumentException("节点缺少输出接口: " + node.getNodeIdRef());
        return selected;
    }

    private String selectBranch(Task task, TaskStep step, FlowNode node) {
        JsonNode variables = mergeVariables(task, step);
        String fallback = null;
        for (JsonNode branch : node.getCapability().path("branches")) {
            String interfaceName = branch.path("interfaceName").asText("");
            if (branch.path("isDefault").asBoolean(false)) fallback = interfaceName;
            else if (conditionEvaluator.evaluate(branch.path("condition"), variables)) return interfaceName;
        }
        if (fallback != null) return fallback;
        throw new IllegalStateException("BRANCH 没有匹配条件且未配置默认出口");
    }

    private boolean aggregateReady(Task task, TaskStep aggregateStep, FlowNode node) {
        var compiled = workflowService.compileDefinition(node.getFlowModelId());
        Set<Long> predecessors = new HashSet<>();
        compiled.incoming(node.getNodeIdRef()).forEach(c -> predecessors.add(c.sourceNodeIdRef()));
        List<TaskStep> contextSteps = runtime.steps(task.getId()).stream()
                .filter(item -> java.util.Objects.equals(item.getParentStepId(), aggregateStep.getParentStepId()))
                .filter(item -> java.util.Objects.equals(item.getStepDepth(), aggregateStep.getStepDepth()))
                .toList();
        boolean anyActivated = false;
        for (Long predecessor : predecessors) {
            List<TaskStep> activated = contextSteps.stream().filter(item -> predecessor.equals(item.getNodeIdRef())).toList();
            if (activated.isEmpty()) return false;
            anyActivated = true;
            if (activated.stream().anyMatch(item -> !"COMPLETED".equals(item.getNodeStatus()))) return false;
        }
        return anyActivated && !predecessors.isEmpty();
    }

    @EventListener
    public void handleStateMachineSignal(StateMachineInterfaceSignalEvent event) {
        JsonNode signal = event.signal();
        if (!"STAT".equals(event.interfaceType())
                || !"CMD_STATE".equals(signal.path("signalName").asText())) return;
        String messageId = String.valueOf(event.executionContext().getOrDefault("messageId", ""));
        TaskStep located = runtime.findRunningDeviceStepByMessageId(messageId);
        if (located == null) return;
        synchronized (taskLock(located.getTaskId())) {
            TaskStep step = runtime.findRunningDeviceStepByMessageId(messageId);
            if (step == null) return;
            String cmdState = signal.path("payload").path("state").asText("");
            if ("COMPLETED".equals(cmdState)) {
                Task task = runtime.task(step.getTaskId());
                FlowNode node = flowNodeService.getById(step.getFlowNodeId());
                ObjectNode output = JsonNodeSupport.objectNode();
                output.put("messageId", messageId);
                output.put("cmdState", cmdState);
                completeAndRoute(task, step, node, outputInterface(node), output);
            } else if ("FAILED".equals(cmdState) || "ABORTED".equals(cmdState)) {
                runtime.failStep(step, "设备指令状态: " + cmdState);
            }
        }
    }

    private FlowNode nodeByRef(Long flowModelId, long nodeIdRef) {
        return workflowService.nodes(flowModelId).stream()
                .filter(node -> node.getNodeIdRef() == nodeIdRef)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("流程节点不存在: " + nodeIdRef));
    }

    private static Object[] createTaskLocks(int count) {
        Object[] locks = new Object[count];
        for (int i = 0; i < count; i++) locks[i] = new Object();
        return locks;
    }

    private Object taskLock(Long taskId) {
        if (taskId == null) throw new IllegalArgumentException("任务 ID 不能为空");
        return TASK_LOCKS[Math.floorMod(Long.hashCode(taskId), TASK_LOCKS.length)];
    }

    private JsonNode mergeVariables(Task task, TaskStep step) {
        ObjectNode result = JsonNodeSupport.objectNode();
        if (task.getTaskVariables() != null && task.getTaskVariables().isObject())
            task.getTaskVariables().fields().forEachRemaining(entry -> result.set(entry.getKey(), entry.getValue()));
        if (step.getVariableSpace() != null && step.getVariableSpace().isObject())
            step.getVariableSpace().fields().forEachRemaining(entry -> result.set(entry.getKey(), entry.getValue()));
        return result;
    }
}
