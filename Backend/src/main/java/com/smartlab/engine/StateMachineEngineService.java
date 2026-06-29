package com.smartlab.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.DeviceInstances;
import com.smartlab.management.entity.DeviceModels;
import com.smartlab.management.entity.DeviceTwinStates;
import com.smartlab.management.mapper.DeviceInstancesMapper;
import com.smartlab.management.service.db.resource.device.DeviceModelService;
import com.smartlab.management.service.db.resource.device.DeviceTwinStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 软电路状态机引擎服务。
 */
@Service
public class StateMachineEngineService {

    private static final Logger log = LoggerFactory.getLogger(StateMachineEngineService.class);

    private final DeviceModelService deviceModelService;
    private final DeviceTwinStateService deviceTwinStateService;
    private final DeviceInstancesMapper deviceInstancesMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ConcurrentHashMap<String, ObjectNode> interfaceSignalTable = new ConcurrentHashMap<>();

    public StateMachineEngineService(DeviceModelService deviceModelService,
                                     DeviceTwinStateService deviceTwinStateService,
                                     DeviceInstancesMapper deviceInstancesMapper,
                                     ApplicationEventPublisher eventPublisher) {
        this.deviceModelService = deviceModelService;
        this.deviceTwinStateService = deviceTwinStateService;
        this.deviceInstancesMapper = deviceInstancesMapper;
        this.eventPublisher = eventPublisher;
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

        JsonNode transitions = model.getStateTransitions();
        if (transitions == null || !transitions.isArray()) {
            log.debug("设备模型 {} 未配置状态转移规则", model.getId());
            return List.of();
        }

        String nextCmdState = null;
        String nextOpState = null;
        List<JsonNode> actionsToExecute = new ArrayList<>();
        boolean isCommandSpace = isCommandSignal(signalName)
                || "Interface_control_in".equals(interfaceName)
                || "Interface_workflow_in".equals(interfaceName)
                || "Interface_constraint_in".equals(interfaceName);

        for (JsonNode transition : transitions) {
            JsonNode trigger = transition.path("trigger");
            String trigInterface = trigger.path("interfaceName").asText("");
            String trigSignal = trigger.path("signalName").asText("");
            if (!trigInterface.equals(interfaceName) || !trigSignal.equals(signalName)) {
                continue;
            }

            String fromState = transition.path("fromStateName").asText("");
            String toState = transition.path("toStateName").asText("");
            if (isCommandSpace && fromState.equals(currentCmd)) {
                nextCmdState = toState;
                collectActions(transition, actionsToExecute);
                log.info("设备 {} 指令生命周期转移: {} -> {} (触发: {}::{})",
                        instanceId, currentCmd, nextCmdState, interfaceName, signalName);
                break;
            }
            if (!isCommandSpace && fromState.equals(currentOp)) {
                nextOpState = toState;
                collectActions(transition, actionsToExecute);
                log.info("设备 {} 功能状态转移: {} -> {} (触发: {}::{})",
                        instanceId, currentOp, nextOpState, interfaceName, signalName);
                break;
            }
        }

        if (nextCmdState != null) {
            twinState.setCurrentCmdState(nextCmdState);
            actionsToExecute.addAll(findOnEntryActions(model, "CMD", nextCmdState));
        }
        if (nextOpState != null) {
            twinState.setCurrentOpState(nextOpState);
            actionsToExecute.addAll(findOnEntryActions(model, "OP", nextOpState));
        }

        if ("Interface_adapter_in".equals(interfaceName)
                || "Interface_control_in".equals(interfaceName)
                || "Interface_workflow_in".equals(interfaceName)) {
            twinState.setOnlineStatus("ONLINE");
            twinState.setLastOnlineTime(LocalDateTime.now());
        }
        twinState.setUpdateTime(LocalDateTime.now());

        List<ObjectNode> emittedSignals = new ArrayList<>();
        for (JsonNode action : actionsToExecute) {
            ObjectNode emitted = executeAction(instance, action, payloadContext, twinState);
            if (emitted != null) {
                emittedSignals.add(emitted);
            }
        }

        // 系统原生内置状态广播逻辑：当状态发生变更时，由引擎底座自动广播最新状态，无需注入模型JSON
        if (nextCmdState != null) {
            ObjectNode cmdBroadcast = emitInterfaceSignal(instance, "Interface_status_out", "CMD_STATE", payloadContext, twinState);
            if (cmdBroadcast != null) {
                emittedSignals.add(cmdBroadcast);
            }
        }
        if (nextOpState != null) {
            ObjectNode opBroadcast = emitInterfaceSignal(instance, "Interface_status_out", "OP_STATE", payloadContext, twinState);
            if (opBroadcast != null) {
                emittedSignals.add(opBroadcast);
            }
        }

        deviceTwinStateService.save(twinState);
        return emittedSignals;
    }

    private void collectActions(JsonNode transition, List<JsonNode> actionsToExecute) {
        JsonNode actions = transition.path("actions");
        if (!actions.isArray()) {
            return;
        }
        for (JsonNode action : actions) {
            actionsToExecute.add(action);
        }
    }

    private ObjectNode executeAction(DeviceInstances instance, JsonNode action, Map<String, Object> payloadContext, DeviceTwinStates twinState) {
        String actionName = action.path("actionName").asText("");
        if (!"SEND".equals(actionName)) {
            return null;
        }

        JsonNode payload = action.path("payload");
        String destInterface = payload.path("interfaceName").asText("");
        String destSignal = payload.path("signalName").asText("");
        if (destInterface.isBlank() || destSignal.isBlank()) {
            throw new IllegalArgumentException("SEND 动作缺少目标接口或信号");
        }

        ObjectNode emitted = emitInterfaceSignal(instance, destInterface, destSignal, payloadContext, twinState);
        if ("Interface_adapter_out".equals(destInterface)) {
            String commandId = payloadContext != null ? (String) payloadContext.get("commandId") : null;
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = payloadContext != null ? (Map<String, Object>) payloadContext.get("parameters") : null;
            eventPublisher.publishEvent(new StateMachineSendActionEvent(
                    instance.getId(),
                    destInterface,
                    destSignal,
                    commandId,
                    parameters == null ? Map.of() : parameters,
                    emitted
            ));
            log.info("设备 {} 状态机输出 Adapter 指令信号 {}，指令 {}", instance.getId(), destSignal, commandId);
        }
        return emitted;
    }

    private List<JsonNode> findOnEntryActions(DeviceModels model, String spaceType, String stateName) {
        List<JsonNode> result = new ArrayList<>();
        JsonNode spaceNode = "CMD".equals(spaceType) ? model.getCmdState() : model.getOpState();
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
                            result.add(action);
                        }
                    }
                    break;
                }
            }
        }
        return result;
    }

    public Map<String, ObjectNode> interfaceSignalSnapshot() {
        return Map.copyOf(interfaceSignalTable);
    }

    private ObjectNode emitInterfaceSignal(DeviceInstances instance,
                                           String interfaceName,
                                           String signalName,
                                           Map<String, Object> payloadContext,
                                           DeviceTwinStates twinState) {
        ObjectNode signal = JsonNodeSupport.objectNode();
        signal.put("instanceId", instance.getId());
        signal.put("interfaceName", interfaceName);
        signal.put("signalName", signalName);
        signal.put("opState", twinState.getCurrentOpState());
        signal.put("cmdState", twinState.getCurrentCmdState());
        signal.set("attributes", twinState.getCurrentAttr() == null ? JsonNodeSupport.objectNode() : twinState.getCurrentAttr());
        signal.set("context", JsonNodeSupport.toNode(payloadContext == null ? Map.of() : payloadContext));
        signal.put("timestamp", Instant.now().toEpochMilli());

        interfaceSignalTable.put(signalKey(instance.getId(), interfaceName, signalName), signal);
        interfaceSignalTable.put(signalKey(instance.getId(), interfaceName, "__last__"), signal);
        log.info("设备 {} 接口 {} 输出信号 {}", instance.getId(), interfaceName, signalName);
        return signal;
    }

    private String signalKey(Long instanceId, String interfaceName, String signalName) {
        return instanceId + "::" + interfaceName + "::" + signalName;
    }

    private DeviceTwinStates createDefaultTwinState(Long instanceId) {
        DeviceTwinStates state = new DeviceTwinStates();
        state.setInstanceId(instanceId);
        state.setCurrentOpState("IDLE");
        state.setCurrentCmdState("IDLE");
        state.setOnlineStatus("UNKNOWN");
        state.setCurrentAttr(JsonNodeSupport.objectNode());
        state.setUpdateTime(LocalDateTime.now());
        deviceTwinStateService.save(state);
        return state;
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
}
