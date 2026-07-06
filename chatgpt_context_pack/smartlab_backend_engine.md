# smartlab_backend_engine

Generated at: 2026-07-03T11:09:10

This file is generated from the local SmartLab repository for model-readable project context.

## File Tree

- Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineDictionary.java
- Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineEngine.java
- Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineModels.java
- Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineSendActionEvent.java
- Backend/src/main/java/com/smartlab/engine/workflow/WorkflowEngine.java

## Files

---

## Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineDictionary.java

````text
package com.smartlab.engine.statemachine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 状态机动作字典与执行器策略
 */
public class StateMachineDictionary {

    /**
     * 动作执行器接口
     */
    public interface ActionExecutor {
        String getSupportedActionName();
        ObjectNode execute(StateMachineModels.ActionDefinition action, StateMachineModels.EventContext context);
    }

    /**
     * 动作注册表
     */
    @Component
    public static class Registry {
        private final Map<String, ActionExecutor> registry = new HashMap<>();

        @Autowired
        public Registry(List<ActionExecutor> executors) {
            for (ActionExecutor executor : executors) {
                registry.put(executor.getSupportedActionName(), executor);
            }
        }

        public ActionExecutor getExecutor(String actionName) {
            return registry.get(actionName);
        }
    }

    /**
     * SEND 动作执行器
     */
    @Component
    public static class SendActionExecutor implements ActionExecutor {
        private static final Logger log = LoggerFactory.getLogger(SendActionExecutor.class);
        private final ApplicationEventPublisher eventPublisher;

        public SendActionExecutor(ApplicationEventPublisher eventPublisher) {
            this.eventPublisher = eventPublisher;
        }

        @Override
        public String getSupportedActionName() {
            return "SEND";
        }

        @Override
        public ObjectNode execute(StateMachineModels.ActionDefinition action, StateMachineModels.EventContext context) {
            JsonNode payload = action.payload();
            if (payload == null) {
                throw new IllegalArgumentException("SEND 动作缺少 payload");
            }
            
            String destInterface = payload.path("interfaceName").asText("");
            String destSignal = payload.path("signalName").asText("");
            if (destInterface.isBlank() || destSignal.isBlank()) {
                throw new IllegalArgumentException("SEND 动作缺少目标接口或信号");
            }

            ObjectNode signal = JsonNodeSupport.objectNode();
            signal.put("instanceId", context.instance().getId());
            signal.put("interfaceName", destInterface);
            signal.put("signalName", destSignal);
            signal.put("opState", context.twinState().getCurrentOpState());
            signal.put("cmdState", context.twinState().getCurrentCmdState());
            signal.set("attributes", context.twinState().getCurrentAttr() == null ? JsonNodeSupport.objectNode() : context.twinState().getCurrentAttr());
            signal.set("context", JsonNodeSupport.toNode(context.payloadContext() == null ? Map.of() : context.payloadContext()));
            signal.put("timestamp", Instant.now().toEpochMilli());

            if ("Interface_adapter_out".equals(destInterface)) {
                String commandId = context.payloadContext() != null ? (String) context.payloadContext().get("commandId") : null;
                @SuppressWarnings("unchecked")
                Map<String, Object> parameters = context.payloadContext() != null ? (Map<String, Object>) context.payloadContext().get("parameters") : null;
                
                eventPublisher.publishEvent(new StateMachineSendActionEvent(
                        context.instance().getId(),
                        destInterface,
                        destSignal,
                        commandId,
                        parameters == null ? Map.of() : parameters,
                        signal
                ));
                log.info("设备 {} 状态机输出 Adapter 指令信号 {}，指令 {}", context.instance().getId(), destSignal, commandId);
            }

            return signal;
        }
    }
}

````

---

## Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineEngine.java

````text
package com.smartlab.engine.statemachine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.service.db.resource.device.DeviceModelService;
import com.smartlab.management.service.db.resource.device.DeviceTwinStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 软电路状态机引擎大脑（门面与解析调度层）。
 * 负责衔接数据库资源、解析模型进行纯逻辑状态偏转，以及调用动作字典执行。
 */
@Service
public class StateMachineEngine {

    private static final Logger log = LoggerFactory.getLogger(StateMachineEngine.class);

    private final DeviceModelService deviceModelService;
    private final DeviceTwinStateService deviceTwinStateService;
    private final DeviceInstancesMapper deviceInstancesMapper;
    private final StateMachineDictionary.Registry actionRegistry;

    private final ConcurrentHashMap<String, ObjectNode> interfaceSignalTable = new ConcurrentHashMap<>();

    public StateMachineEngine(DeviceModelService deviceModelService,
                              DeviceTwinStateService deviceTwinStateService,
                              DeviceInstancesMapper deviceInstancesMapper,
                              StateMachineDictionary.Registry actionRegistry) {
        this.deviceModelService = deviceModelService;
        this.deviceTwinStateService = deviceTwinStateService;
        this.deviceInstancesMapper = deviceInstancesMapper;
        this.actionRegistry = actionRegistry;
    }

    public ObjectNode handleManualControl(Long instanceId, String commandId, Map<String, Object> parameters) {
        return handleManualControl(instanceId, "MANUAL_EXECUTE", commandId, parameters);
    }

    public ObjectNode handleManualControl(Long instanceId, String signalName, String commandId, Map<String, Object> parameters) {
        String resolvedSignal = signalName == null || signalName.isBlank() ? "MANUAL_EXECUTE" : signalName;
        Map<String, Object> context = new HashMap<>();
        context.put("commandId", commandId);
        context.put("parameters", parameters == null ? Map.of() : parameters);

        log.info("手动触发设备 {} 的控制信号 {}，指令 {}", instanceId, resolvedSignal, commandId);
        List<ObjectNode> emittedSignals = dispatchSignal(instanceId, "Interface_control_in", resolvedSignal, context);
        if (emittedSignals.isEmpty()) {
            DeviceTwinStates twinState = deviceTwinStateService.getByInstanceId(instanceId);
            String currentCmd = twinState == null ? "IDLE" : twinState.getCurrentCmdState();
            throw new IllegalStateException("当前指令执行生命周期状态为 " + currentCmd + "，状态机拒绝此手动操作");
        }

        ObjectNode result = JsonNodeSupport.objectNode();
        result.put("instanceId", instanceId);
        result.put("interfaceName", "Interface_control_in");
        result.put("signalName", resolvedSignal);
        result.put("accepted", true);
        result.set("emittedSignals", JsonNodeSupport.toNode(emittedSignals));
        result.put("timestamp", Instant.now().toEpochMilli());
        return result;
    }

    public void dispatchAdapterEvent(Long instanceId, String eventName, JsonNode message) {
        Map<String, Object> context = new HashMap<>();
        context.put("payload", message);
        log.info("接收到设备 {} 的 Adapter 事件: {}", instanceId, eventName);
        dispatchSignal(instanceId, "Interface_adapter_in", eventName, context);
    }

    public synchronized List<ObjectNode> dispatchSignal(Long instanceId, String interfaceName, String signalName, Map<String, Object> payloadContext) {
        DeviceInstances instance = deviceInstancesMapper.selectById(instanceId);
        if (instance == null) {
            log.warn("设备实例不存在，无法执行状态机: {}", instanceId);
            return List.of();
        }

        DeviceModels model = deviceModelService.getById(instance.getDeviceModelId().toString());
        if (model == null) {
            log.warn("设备模型不存在，无法执行状态机: {}", instance.getDeviceModelId());
            return List.of();
        }

        DeviceTwinStates twinState = deviceTwinStateService.getByInstanceId(instanceId);
        if (twinState == null) {
            twinState = createDefaultTwinState(instanceId);
        }

        String currentCmd = twinState.getCurrentCmdState() == null ? "IDLE" : twinState.getCurrentCmdState();
        String currentOp = twinState.getCurrentOpState() == null ? "IDLE" : twinState.getCurrentOpState();

        // 1. 将模型转换为引擎需要的领域实体
        StateMachineModels.Definition definition = new StateMachineModels.Definition(model);

        // 2. 解析计算下一个状态和需执行的动作
        TransitionResult result = computeNextState(definition, currentCmd, currentOp, interfaceName, signalName);

        if (result.nextCmdState == null && result.nextOpState == null) {
            return List.of();
        }

        if (result.nextCmdState != null) {
            twinState.setCurrentCmdState(result.nextCmdState);
        }
        if (result.nextOpState != null) {
            twinState.setCurrentOpState(result.nextOpState);
        }

        if ("Interface_adapter_in".equals(interfaceName)
                || "Interface_control_in".equals(interfaceName)
                || "Interface_workflow_in".equals(interfaceName)) {
            twinState.setOnlineStatus("ONLINE");
            twinState.setLastOnlineTime(OffsetDateTime.now());
        }
        twinState.setUpdateTime(OffsetDateTime.now());

        // 3. 构造运行上下文
        StateMachineModels.EventContext eventContext = new StateMachineModels.EventContext(
                instance, twinState, interfaceName, signalName, payloadContext
        );

        List<ObjectNode> emittedSignals = new ArrayList<>();

        // 4. 调用动作字典执行具体动作
        for (StateMachineModels.ActionDefinition action : result.actionsToExecute) {
            StateMachineDictionary.ActionExecutor executor = actionRegistry.getExecutor(action.actionName());
            if (executor != null) {
                ObjectNode emitted = executor.execute(action, eventContext);
                if (emitted != null) {
                    emittedSignals.add(emitted);
                    cacheSignal(instance.getId(), emitted);
                }
            } else {
                log.warn("未找到动作执行器: {}", action.actionName());
            }
        }

        // 系统原生内置状态广播逻辑
        if (result.nextCmdState != null) {
            ObjectNode cmdBroadcast = createBuiltInSignal(instance, "Interface_status_out", "CMD_STATE", twinState, payloadContext);
            emittedSignals.add(cmdBroadcast);
            cacheSignal(instance.getId(), cmdBroadcast);
        }
        if (result.nextOpState != null) {
            ObjectNode opBroadcast = createBuiltInSignal(instance, "Interface_status_out", "OP_STATE", twinState, payloadContext);
            emittedSignals.add(opBroadcast);
            cacheSignal(instance.getId(), opBroadcast);
        }

        // 5. 调用 management db 服务保存新状态
        deviceTwinStateService.save(twinState);
        return emittedSignals;
    }

    private TransitionResult computeNextState(
            StateMachineModels.Definition definition,
            String currentCmd,
            String currentOp,
            String interfaceName,
            String signalName
    ) {
        String nextCmdState = null;
        String nextOpState = null;
        List<StateMachineModels.ActionDefinition> actionsToExecute = new ArrayList<>();

        boolean isCommandSpace = isCommandSignal(signalName)
                || "Interface_control_in".equals(interfaceName)
                || "Interface_workflow_in".equals(interfaceName)
                || "Interface_constraint_in".equals(interfaceName);

        for (StateMachineModels.TransitionRule transition : definition.getTransitions()) {
            if (!transition.triggerInterface().equals(interfaceName) || !transition.triggerSignal().equals(signalName)) {
                continue;
            }

            if (isCommandSpace && transition.fromState().equals(currentCmd)) {
                nextCmdState = transition.toState();
                actionsToExecute.addAll(transition.actions());
                break;
            }
            if (!isCommandSpace && transition.fromState().equals(currentOp)) {
                nextOpState = transition.toState();
                actionsToExecute.addAll(transition.actions());
                break;
            }
        }

        if (nextCmdState != null) {
            actionsToExecute.addAll(findOnEntryActions(definition, "CMD", nextCmdState));
        }
        if (nextOpState != null) {
            actionsToExecute.addAll(findOnEntryActions(definition, "OP", nextOpState));
        }

        return new TransitionResult(nextCmdState, nextOpState, actionsToExecute);
    }

    private List<StateMachineModels.ActionDefinition> findOnEntryActions(StateMachineModels.Definition definition, String spaceType, String stateName) {
        List<StateMachineModels.ActionDefinition> result = new ArrayList<>();
        JsonNode spaceNode = "CMD".equals(spaceType) ? definition.getModel().getCmdState() : definition.getModel().getOpState();
        if (spaceNode == null || !spaceNode.has("states")) {
            return result;
        }
        JsonNode states = spaceNode.get("states");
        if (states.isArray()) {
            for (JsonNode state : states) {
                if (stateName.equals(state.path("stateName").asText(""))) {
                    JsonNode onEntry = state.path("onEntry");
                    if (onEntry.isArray()) {
                        for (JsonNode action : onEntry) {
                            result.add(StateMachineModels.ActionDefinition.fromJson(action));
                        }
                    }
                    break;
                }
            }
        }
        return result;
    }

    private boolean isCommandSignal(String signalName) {
        if (signalName == null) {
            return false;
        }
        return switch (signalName) {
            case "EXECUTE_START", "EXECUTE_PAUSE", "EXECUTE_RESUME", "EXECUTE_CANCEL", "EXECUTE_RESET",
                 "MANUAL_EXECUTE", "MANUAL_CANCEL", "MANUAL_PAUSE", "MANUAL_RESUME", "MANUAL_RESET",
                 "CONSTRAINT_CANCEL", "CONSTRAINT_PAUSE", "CONSTRAINT_RESUME", "CONSTRAINT_RESET",
                 "COMMAND_RECEIVED", "COMMAND_RUNNING", "COMMAND_COMPLETED", "COMMAND_DONE", "COMMAND_FAILED",
                 "COMMAND_TIMEOUT", "COMMAND_CANCELLED" -> true;
            default -> false;
        };
    }

    public Map<String, ObjectNode> interfaceSignalSnapshot() {
        return Map.copyOf(interfaceSignalTable);
    }

    private void cacheSignal(Long instanceId, ObjectNode signal) {
        String interfaceName = signal.path("interfaceName").asText("");
        String signalName = signal.path("signalName").asText("");
        interfaceSignalTable.put(instanceId + "::" + interfaceName + "::" + signalName, signal);
        interfaceSignalTable.put(instanceId + "::" + interfaceName + "::__last__", signal);
    }

    private ObjectNode createBuiltInSignal(DeviceInstances instance, String interfaceName, String signalName, DeviceTwinStates twinState, Map<String, Object> payloadContext) {
        ObjectNode signal = JsonNodeSupport.objectNode();
        signal.put("instanceId", instance.getId());
        signal.put("interfaceName", interfaceName);
        signal.put("signalName", signalName);
        signal.put("opState", twinState.getCurrentOpState());
        signal.put("cmdState", twinState.getCurrentCmdState());
        signal.set("attributes", twinState.getCurrentAttr() == null ? JsonNodeSupport.objectNode() : twinState.getCurrentAttr());
        signal.set("context", JsonNodeSupport.toNode(payloadContext == null ? Map.of() : payloadContext));
        signal.put("timestamp", Instant.now().toEpochMilli());
        return signal;
    }

    private DeviceTwinStates createDefaultTwinState(Long instanceId) {
        DeviceTwinStates state = new DeviceTwinStates();
        state.setInstanceId(instanceId);
        state.setCurrentOpState("IDLE");
        state.setCurrentCmdState("IDLE");
        state.setOnlineStatus("UNKNOWN");
        state.setCurrentAttr(JsonNodeSupport.objectNode());
        state.setUpdateTime(OffsetDateTime.now());
        deviceTwinStateService.save(state);
        return state;
    }

    private record TransitionResult(
            String nextCmdState,
            String nextOpState,
            List<StateMachineModels.ActionDefinition> actionsToExecute
    ) {}
}

````

---

## Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineModels.java

````text
package com.smartlab.engine.statemachine;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 状态机领域模型合集
 */
public class StateMachineModels {

    /**
     * 状态机完整定义
     */
    public static class Definition {
        private final DeviceModels model;
        private final List<TransitionRule> transitions;

        public Definition(DeviceModels model) {
            this.model = model;
            this.transitions = new ArrayList<>();
            JsonNode transitionsNode = model.getStateTransitions();
            if (transitionsNode != null && transitionsNode.isArray()) {
                for (JsonNode transitionNode : transitionsNode) {
                    this.transitions.add(TransitionRule.fromJson(transitionNode));
                }
            }
        }

        public DeviceModels getModel() {
            return model;
        }

        public List<TransitionRule> getTransitions() {
            return transitions;
        }
    }

    /**
     * 状态转移规则
     */
    public record TransitionRule(
            String fromState,
            String toState,
            String triggerInterface,
            String triggerSignal,
            List<ActionDefinition> actions
    ) {
        public static TransitionRule fromJson(JsonNode node) {
            JsonNode trigger = node.path("trigger");
            String trigInterface = trigger.path("interfaceName").asText("");
            String trigSignal = trigger.path("signalName").asText("");
            String fromState = node.path("fromStateName").asText("");
            String toState = node.path("toStateName").asText("");

            List<ActionDefinition> actions = new ArrayList<>();
            JsonNode actionsNode = node.path("actions");
            if (actionsNode.isArray()) {
                for (JsonNode actionNode : actionsNode) {
                    actions.add(ActionDefinition.fromJson(actionNode));
                }
            }
            return new TransitionRule(fromState, toState, trigInterface, trigSignal, actions);
        }
    }

    /**
     * 动作定义
     */
    public record ActionDefinition(
            String actionName,
            JsonNode payload
    ) {
        public static ActionDefinition fromJson(JsonNode node) {
            return new ActionDefinition(
                    node.path("actionName").asText(""),
                    node.path("payload")
            );
        }
    }

    /**
     * 事件执行上下文
     */
    public record EventContext(
            DeviceInstances instance,
            DeviceTwinStates twinState,
            String triggerInterface,
            String triggerSignal,
            Map<String, Object> payloadContext
    ) {}
}

````

---

## Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineSendActionEvent.java

````text
package com.smartlab.engine.statemachine;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * 状态机 SEND 动作产生的接口输出事件。
 */
public record StateMachineSendActionEvent(
        Long instanceId,
        String interfaceName,
        String signalName,
        String commandId,
        Map<String, Object> parameters,
        ObjectNode interfaceSignal
) {
}

````

---

## Backend/src/main/java/com/smartlab/engine/workflow/WorkflowEngine.java

````text
package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.StateMachineEngine;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.entity.workflow.FlowModels;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.StepLog;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.mapper.workflow.FlowModelsMapper;
import com.smartlab.management.mapper.workflow.FlowNodeMapper;
import com.smartlab.management.mapper.workflow.StepLogMapper;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.mapper.workflow.TaskStepMapper;
import com.smartlab.management.service.db.resource.device.DeviceTwinStateService;
import com.smartlab.global.util.JsonNodeSupport;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final TaskMapper taskMapper;
    private final TaskStepMapper taskStepMapper;
    private final FlowModelsMapper flowModelsMapper;
    private final FlowNodeMapper flowNodeMapper;
    private final StepLogMapper stepLogMapper;
    private final StateMachineEngine stateMachineEngine;
    private final DeviceTwinStateService deviceTwinStateService;

    public WorkflowEngine(TaskMapper taskMapper, TaskStepMapper taskStepMapper,
                          FlowModelsMapper flowModelsMapper, FlowNodeMapper flowNodeMapper,
                          StepLogMapper stepLogMapper,
                          StateMachineEngine stateMachineEngine, DeviceTwinStateService deviceTwinStateService) {
        this.taskMapper = taskMapper;
        this.taskStepMapper = taskStepMapper;
        this.flowModelsMapper = flowModelsMapper;
        this.flowNodeMapper = flowNodeMapper;
        this.stepLogMapper = stepLogMapper;
        this.stateMachineEngine = stateMachineEngine;
        this.deviceTwinStateService = deviceTwinStateService;
    }

    @Scheduled(fixedDelay = 2000)
    public void driveWorkflows() {
        List<Task> runningTasks = taskMapper.selectList(Wrappers.<Task>lambdaQuery().eq(Task::getTaskStatus, "RUNNING"));
        for (Task task : runningTasks) {
            try {
                processTask(task);
            } catch (Exception e) {
                log.error("执行任务 {} 失败", task.getId(), e);
                appendLog(task.getId(), null, "ERROR", "执行引擎异常: " + e.getMessage());
            }
        }
    }

    private void processTask(Task task) {
        FlowModels flowModel = flowModelsMapper.selectById(task.getFlowModelId());
        if (flowModel == null) {
            failTask(task, "找不到关联的流程模型");
            return;
        }

        List<TaskStep> activeSteps = taskStepMapper.selectList(
                Wrappers.<TaskStep>lambdaQuery().eq(TaskStep::getTaskId, task.getId())
                        .in(TaskStep::getNodeStatus, "PENDING", "RUNNING"));

        if (activeSteps.isEmpty()) {
            List<TaskStep> completedSteps = taskStepMapper.selectList(
                    Wrappers.<TaskStep>lambdaQuery().eq(TaskStep::getTaskId, task.getId())
                            .eq(TaskStep::getNodeStatus, "COMPLETED"));
            if (completedSteps.isEmpty()) {
                // 初始化主流程
                startFlow(task, flowModel.getId(), null, 0);
            }
        } else {
            // 将节点列表按模型分组缓存，避免多次查询
            Map<Long, List<FlowNode>> modelNodesCache = new HashMap<>();
            
            for (TaskStep step : activeSteps) {
                Long currentModelId = getModelIdForStep(task, step);
                if (currentModelId == null) continue;
                
                List<FlowNode> flowNodes = modelNodesCache.computeIfAbsent(currentModelId, 
                    id -> flowNodeMapper.selectList(Wrappers.<FlowNode>lambdaQuery().eq(FlowNode::getFlowModelId, id)));
                
                processStep(task, currentModelId, flowNodes, step);
            }
        }
    }

    private Long getModelIdForStep(Task task, TaskStep step) {
        if (step.getParentStepId() == null) {
            return task.getFlowModelId();
        }
        TaskStep parentStep = taskStepMapper.selectById(step.getParentStepId());
        if (parentStep == null) return null;
        FlowNode parentNode = flowNodeMapper.selectById(parentStep.getFlowNodeId());
        if (parentNode == null) return null;
        return parentNode.getSubFlowModelId();
    }

    private void startFlow(Task task, Long flowModelId, Long parentStepId, int depth) {
        List<FlowNode> flowNodes = flowNodeMapper.selectList(
                Wrappers.<FlowNode>lambdaQuery().eq(FlowNode::getFlowModelId, flowModelId));
                
        FlowNode startNode = flowNodes.stream()
                .filter(n -> "FUNCTIONAL_NODE".equals(n.getNodeType()) && 
                             n.getCapability() != null && 
                             "START".equals(n.getCapability().path("functionType").asText()))
                .findFirst().orElse(null);

        if (startNode == null) {
            if (depth == 0) failTask(task, "流程中没有找到 START 节点");
            return;
        }

        startNewStep(task, startNode, parentStepId, depth, JsonNodeSupport.objectNode());
    }

    private void processStep(Task task, Long currentModelId, List<FlowNode> flowNodes, TaskStep step) {
        FlowNode nodeDef = flowNodes.stream().filter(n -> n.getId().equals(step.getFlowNodeId())).findFirst().orElse(null);
        if (nodeDef == null) {
            failStep(step, "找不到节点定义: " + step.getFlowNodeId());
            return;
        }

        String nodeType = nodeDef.getNodeType();
        
        if ("PENDING".equals(step.getNodeStatus())) {
            step.setNodeStatus("RUNNING");
            step.setStartTime(OffsetDateTime.now());
            taskStepMapper.updateById(step);
            appendLog(task.getId(), null, "INFO", "节点 [REF:" + nodeDef.getNodeIdRef() + "] 开始执行");

            if ("DEVICE_CAPABILITY_NODE".equals(nodeType)) {
                triggerDevice(task, step, nodeDef);
            } else if ("SUB_FLOW_NODE".equals(nodeType)) {
                // 启动子流程
                startFlow(task, nodeDef.getSubFlowModelId(), step.getId(), step.getStepDepth() + 1);
            } else {
                // FUNCTIONAL_NODE
                String funcType = nodeDef.getCapability() != null ? nodeDef.getCapability().path("functionType").asText() : "";
                if ("WAIT".equals(funcType)) {
                    step.setInterfaceInSnapshot(JsonNodeSupport.objectNode().put("waitStartTime", System.currentTimeMillis()));
                    taskStepMapper.updateById(step);
                } else if ("SYNC".equals(funcType) || "JOIN".equals(funcType)) {
                    completeStep(task, currentModelId, step, nodeDef, "flow_out");
                } else if ("BRANCH".equals(funcType)) {
                    completeStep(task, currentModelId, step, nodeDef, "flow_yes"); 
                } else if ("END".equals(funcType)) {
                    completeStep(task, currentModelId, step, nodeDef, "flow_out");
                    if (step.getStepDepth() == 0) {
                        completeTask(task);
                    } else {
                        // 子流程结束，标记父 SUB_FLOW_NODE 步骤为 COMPLETED
                        TaskStep parentStep = taskStepMapper.selectById(step.getParentStepId());
                        if (parentStep != null) {
                            Long parentModelId = getModelIdForStep(task, parentStep);
                            if (parentModelId != null) {
                                FlowNode parentNodeDef = flowNodeMapper.selectById(parentStep.getFlowNodeId());
                                completeStep(task, parentModelId, parentStep, parentNodeDef, "flow_out");
                            }
                        }
                    }
                } else {
                    completeStep(task, currentModelId, step, nodeDef, "flow_out");
                }
            }
        } else if ("RUNNING".equals(step.getNodeStatus())) {
            if ("DEVICE_CAPABILITY_NODE".equals(nodeType)) {
                checkDeviceStatus(task, currentModelId, step, nodeDef);
            } else if ("FUNCTIONAL_NODE".equals(nodeType)) {
                String funcType = nodeDef.getCapability() != null ? nodeDef.getCapability().path("functionType").asText() : "";
                if ("WAIT".equals(funcType)) {
                    long waitStart = step.getInterfaceInSnapshot() != null ? step.getInterfaceInSnapshot().path("waitStartTime").asLong(0) : 0;
                    long duration = nodeDef.getCapability().path("durationMs").asLong(0);
                    if (System.currentTimeMillis() - waitStart >= duration) {
                        completeStep(task, currentModelId, step, nodeDef, "flow_out");
                    }
                }
            }
        }
    }

    private void triggerDevice(Task task, TaskStep step, FlowNode nodeDef) {
        String nodeRefId = String.valueOf(nodeDef.getNodeIdRef());
        JsonNode resourceMap = task.getResourceMap();
        Long instanceId = null;
        if (resourceMap != null && resourceMap.has(nodeRefId)) {
            instanceId = resourceMap.get(nodeRefId).asLong();
        }

        if (instanceId == null) {
            failStep(step, "无法分配设备资源，节点 REF: " + nodeRefId);
            return;
        }

        ObjectNode snapshot = step.getInterfaceInSnapshot() == null ? JsonNodeSupport.objectNode() : (ObjectNode) step.getInterfaceInSnapshot().deepCopy();
        snapshot.put("boundInstanceId", instanceId);
        step.setInterfaceInSnapshot(snapshot);
        taskStepMapper.updateById(step);

        JsonNode actions = nodeDef.getActions();
        if (actions == null || !actions.isArray() || actions.isEmpty()) {
             failStep(step, "节点未配置设备动作");
             return;
        }
        
        JsonNode action = actions.get(0);
        String commandId = action.path("payload").path("commandId").asText("");
        JsonNode paramsNode = action.path("payload").path("parameters");
        Map<String, Object> parameters = new HashMap<>();
        if (paramsNode != null && paramsNode.isObject()) {
            paramsNode.fields().forEachRemaining(entry -> parameters.put(entry.getKey(), entry.getValue().asText()));
        }

        try {
            stateMachineEngine.handleManualControl(instanceId, "EXECUTE_START", commandId, parameters);
            appendLog(task.getId(), instanceId, "INFO", "已向设备下发指令: " + commandId);
        } catch (Exception e) {
            failStep(step, "设备控制调用失败: " + e.getMessage());
        }
    }

    private void checkDeviceStatus(Task task, Long currentModelId, TaskStep step, FlowNode nodeDef) {
        Long instanceId = step.getInterfaceInSnapshot().path("boundInstanceId").asLong(0);
        if (instanceId == 0) {
            failStep(step, "节点丢失绑定的设备实例");
            return;
        }

        DeviceTwinStates twinState = deviceTwinStateService.getByInstanceId(instanceId);
        if (twinState == null) return;

        String cmdState = twinState.getCurrentCmdState();
        if ("IDLE".equals(cmdState) || "SUCCESS".equals(cmdState) || "COMPLETED".equals(cmdState)) {
            appendLog(task.getId(), instanceId, "INFO", "设备指令执行完成");
            completeStep(task, currentModelId, step, nodeDef, "flow_out");
        } else if ("FAILED".equals(cmdState) || "ERROR".equals(cmdState)) {
            failStep(step, "设备报告错误状态: " + cmdState);
        }
    }

    private void completeStep(Task task, Long currentModelId, TaskStep step, FlowNode nodeDef, String outputInterfacePrefix) {
        step.setNodeStatus("COMPLETED");
        step.setEndTime(OffsetDateTime.now());
        if (step.getStartTime() != null) {
            step.setDurationMs(step.getEndTime().toInstant().toEpochMilli() - step.getStartTime().toInstant().toEpochMilli());
        }
        taskStepMapper.updateById(step);
        appendLog(task.getId(), null, "INFO", "节点 [REF:" + nodeDef.getNodeIdRef() + "] 执行完成");

        FlowModels currentFlowModel = flowModelsMapper.selectById(currentModelId);
        if (currentFlowModel == null) return;

        JsonNode connections = currentFlowModel.getInterfaceConnection();
        if (connections != null && connections.isArray()) {
            for (JsonNode conn : connections) {
                String sourceRef = conn.path("source").path("interfaceRef").asText("");
                String expectedRefPrefix = nodeDef.getNodeIdRef() + "_" + outputInterfacePrefix;
                String expectedRefFallback = nodeDef.getNodeIdRef() + "_";

                if (sourceRef.startsWith(expectedRefPrefix) || sourceRef.startsWith(expectedRefFallback)) {
                    String targetRef = conn.path("target").path("interfaceRef").asText("");
                    String targetNodeRefStr = targetRef.split("_")[0];
                    try {
                        Long targetNodeRef = Long.valueOf(targetNodeRefStr);
                        FlowNode nextNodeDef = flowNodeMapper.selectOne(
                            Wrappers.<FlowNode>lambdaQuery()
                                .eq(FlowNode::getFlowModelId, currentModelId)
                                .eq(FlowNode::getNodeIdRef, targetNodeRef)
                        );
                        if (nextNodeDef != null) {
                            startNewStep(task, nextNodeDef, step.getParentStepId(), step.getStepDepth(), JsonNodeSupport.objectNode());
                        }
                    } catch (NumberFormatException e) {
                        log.warn("无法解析 targetRef: {}", targetRef);
                    }
                }
            }
        }
    }

    private void startNewStep(Task task, FlowNode nodeDef, Long parentStepId, int depth, JsonNode inputSnapshot) {
        long count = taskStepMapper.selectCount(Wrappers.<TaskStep>lambdaQuery()
            .eq(TaskStep::getTaskId, task.getId())
            .eq(TaskStep::getFlowNodeId, nodeDef.getId())
            .eq(parentStepId != null, TaskStep::getParentStepId, parentStepId)
            .in(TaskStep::getNodeStatus, "PENDING", "RUNNING"));
        if (count > 0) return;

        TaskStep newStep = new TaskStep();
        newStep.setTaskId(task.getId());
        newStep.setFlowNodeId(nodeDef.getId());
        newStep.setNodeIdRef(nodeDef.getNodeIdRef());
        newStep.setParentStepId(parentStepId);
        newStep.setStepDepth(depth);
        newStep.setNodeStatus("PENDING");
        newStep.setInterfaceInSnapshot(inputSnapshot);
        
        taskStepMapper.insert(newStep);
        appendLog(task.getId(), null, "INFO", "触发节点 [REF:" + nodeDef.getNodeIdRef() + "]");
    }

    private void completeTask(Task task) {
        if (!"COMPLETED".equals(task.getTaskStatus())) {
            task.setTaskStatus("COMPLETED");
            task.setEndTime(OffsetDateTime.now());
            taskMapper.updateById(task);
            appendLog(task.getId(), null, "INFO", "流程到达 END，任务成功结束");
        }
    }

    private void failStep(TaskStep step, String reason) {
        step.setNodeStatus("FAILED");
        step.setEndTime(OffsetDateTime.now());
        taskStepMapper.updateById(step);
        appendLog(step.getTaskId(), null, "ERROR", "节点执行失败: " + reason);
        
        Task task = taskMapper.selectById(step.getTaskId());
        if (task != null) {
            failTask(task, "由于节点执行失败导致任务失败");
        }
    }

    private void failTask(Task task, String reason) {
        if ("FAILED".equals(task.getTaskStatus())) return;
        task.setTaskStatus("FAILED");
        task.setEndTime(OffsetDateTime.now());
        taskMapper.updateById(task);
        appendLog(task.getId(), null, "ERROR", "任务失败: " + reason);
    }

    private void appendLog(Long taskId, Long deviceInstanceId, String level, String message) {
        StepLog logEntry = new StepLog();
        logEntry.setSourceType("TASK");
        logEntry.setTaskId(taskId);
        logEntry.setDeviceInstanceId(deviceInstanceId);
        logEntry.setLogLevel(level);
        logEntry.setLogInfo(message);
        logEntry.setLogTime(OffsetDateTime.now());
        stepLogMapper.insert(logEntry);
    }
}

````
