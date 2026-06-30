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
