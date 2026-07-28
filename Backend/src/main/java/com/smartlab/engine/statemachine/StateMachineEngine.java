package com.smartlab.engine.statemachine;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.action.StateMachineActionExecutor;
import com.smartlab.engine.statemachine.action.StateMachineActionRegistry;
import com.smartlab.global.contract.SystemExecutionContract;
import com.smartlab.global.protocol.ProtocolDictionaryService;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.workflow.TaskStepMapper;
import com.smartlab.management.service.db.resource.device.DeviceModelService;
import com.smartlab.management.service.db.resource.device.DeviceTwinStateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备状态机引擎。系统级CMD转移直接实现定稿系统执行规范；设备模型只声明Adapter事件和OP状态转移
 */
@Service
public class StateMachineEngine {

    private final DeviceModelService deviceModelService;
    private final DeviceTwinStateService deviceTwinStateService;
    private final DeviceInstancesMapper deviceInstancesMapper;
    private final StateMachineActionRegistry actionRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final ProtocolDictionaryService protocolDictionaryService;
    private final TaskStepMapper taskStepMapper;
    private final ConcurrentHashMap<String, ObjectNode> interfaceSignalTable = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> activeCommandMessages = new ConcurrentHashMap<>();

    public StateMachineEngine(DeviceModelService deviceModelService,
                              DeviceTwinStateService deviceTwinStateService,
                              DeviceInstancesMapper deviceInstancesMapper,
                              StateMachineActionRegistry actionRegistry,
                              ApplicationEventPublisher eventPublisher,
                              ProtocolDictionaryService protocolDictionaryService) {
        this(deviceModelService, deviceTwinStateService, deviceInstancesMapper, actionRegistry,
                eventPublisher, protocolDictionaryService, null);
    }

    @Autowired
    public StateMachineEngine(DeviceModelService deviceModelService,
                              DeviceTwinStateService deviceTwinStateService,
                              DeviceInstancesMapper deviceInstancesMapper,
                              StateMachineActionRegistry actionRegistry,
                              ApplicationEventPublisher eventPublisher,
                              ProtocolDictionaryService protocolDictionaryService,
                              TaskStepMapper taskStepMapper) {
        this.deviceModelService = deviceModelService;
        this.deviceTwinStateService = deviceTwinStateService;
        this.deviceInstancesMapper = deviceInstancesMapper;
        this.actionRegistry = actionRegistry;
        this.eventPublisher = eventPublisher;
        this.protocolDictionaryService = protocolDictionaryService;
        this.taskStepMapper = taskStepMapper;
    }

    public ObjectNode handleManualControl(Long instanceId, String capabilityName, Map<String, Object> parameters) {
        return handleManualControl(instanceId, "MANUAL_EXECUTE_START", capabilityName, parameters);
    }

    public ObjectNode handleManualControl(Long instanceId, String signalName, String capabilityName,
                                          Map<String, Object> parameters) {
        String resolvedSignal = signalName == null || signalName.isBlank() ? "MANUAL_EXECUTE_START" : signalName;
        if (!SystemExecutionContract.isCommandStartSignal(resolvedSignal)
                && !SystemExecutionContract.isCommandAbortSignal(resolvedSignal)) {
            throw new IllegalArgumentException("不是可由人工控制台发送的状态机信号: " + resolvedSignal);
        }
        Map<String, Object> context = new HashMap<>();
        if (SystemExecutionContract.isCommandStartSignal(resolvedSignal)) {
            context.put("capabilityName", capabilityName);
            context.put("parameters", parameters == null ? Map.of() : Map.copyOf(parameters));
        }
        List<ObjectNode> emittedSignals = dispatchSignalByType(instanceId, "CONTROL", resolvedSignal, context);
        if (emittedSignals.isEmpty()) {
            DeviceTwinStates twinState = deviceTwinStateService.getByInstanceId(instanceId);
            String currentCmd = twinState == null ? "IDLE" : twinState.getCurrentCmdState();
            throw new IllegalStateException("当前指令执行生命周期状态为" + currentCmd + "，状态机拒绝此人工操作");
        }
        ObjectNode result = JsonNodeSupport.objectNode();
        result.put("instanceId", instanceId);
        result.put("signalName", resolvedSignal);
        result.put("accepted", true);
        result.set("emittedSignals", JsonNodeSupport.toNode(emittedSignals));
        result.put("timestamp", Instant.now().toEpochMilli());
        return result;
    }

    public List<ObjectNode> dispatchAdapterEvent(Long instanceId, String eventName, JsonNode message) {
        Map<String, Object> context = new HashMap<>();
        context.put("payload", message == null ? JsonNodeSupport.objectNode() : message.deepCopy());
        if (message != null && message.path("messageId").isTextual()
                && !message.path("messageId").asText().isBlank()) {
            context.put("messageId", message.path("messageId").asText());
        }
        return dispatchSignalByType(instanceId, "ADAPTER", eventName, context);
    }

    public List<ObjectNode> dispatchSignalByType(Long instanceId, String interfaceType, String signalName,
                                                  Map<String, Object> executionContext) {
        return dispatchSignal(instanceId, resolveInputInterface(instanceId, interfaceType, signalName),
                signalName, executionContext == null ? Map.of() : executionContext);
    }

    public synchronized List<ObjectNode> dispatchSignal(Long instanceId, String interfaceName, String signalName,
                                                         Map<String, Object> executionContext) {
        DeviceInstances instance = requireInstance(instanceId);
        DeviceModels model = requireModel(instance);
        requireInputInterface(model.getStateMachineInterfaces(), interfaceName, signalName);

        DeviceTwinStates twinState = deviceTwinStateService.getByInstanceId(instanceId);
        if (twinState == null) {
            twinState = createDefaultTwinState(instanceId, model);
        }
        String currentCmdState = currentCommandState(twinState, model);
        JsonNode currentOpState = currentOperationState(twinState, model);
        Map<String, Object> context = new HashMap<>(executionContext == null ? Map.of() : executionContext);
        String incomingMessageId = text(context.get("messageId"));
        if (isAdapterInputInterface(model, interfaceName)) {
            validateCommandEventCorrelation(instanceId, model, interfaceName, signalName, incomingMessageId);
        }
        if (SystemExecutionContract.isCommandStartSignal(signalName)) {
            context = prepareStartContext(instance, model, signalName, context);
        } else if (SystemExecutionContract.isCommandAbortSignal(signalName)) {
            context.put("messageId", activeMessageId(instanceId, context));
        } else if (!context.containsKey("messageId")) {
            String activeMessageId = activeCommandMessages.get(instanceId);
            if (activeMessageId != null) {
                context.put("messageId", activeMessageId);
            }
        }

        StateMachineModels.Definition definition = new StateMachineModels.Definition(model);
        TransitionResult result = computeNextState(definition, currentCmdState, currentOpState,
                interfaceName, signalName, context);
        if (!result.changed()) {
            return List.of();
        }

        if (result.nextCmdState() != null) {
            twinState.setCurrentCmdState(result.nextCmdState());
        }
        if (result.nextOpState() != null) {
            twinState.setCurrentOpState(result.nextOpState());
        }
        twinState.setUpdateTime(OffsetDateTime.now());

        List<StateMachineModels.ActionDefinition> actions = new ArrayList<>(result.actions());
        if (result.nextCmdState() != null) {
            ensureStatusAction(actions, "CMD_STATE");
        }
        for (String regionName : result.changedOpRegions()) {
            ensureStatusAction(actions, "OP_STATE", regionName);
        }

        StateMachineModels.EventContext eventContext = new StateMachineModels.EventContext(
                instance, model, twinState, interfaceName, signalName, Map.copyOf(context));
        List<ObjectNode> emittedSignals = new ArrayList<>();
        List<StateMachineModels.ActionDefinition> adapterActions = actions.stream()
                .filter(action -> isAdapterOutputAction(model, action)).toList();
        List<StateMachineModels.ActionDefinition> internalActions = actions.stream()
                .filter(action -> !isAdapterOutputAction(model, action)).toList();
        try {
            executeAndPublish(instanceId, adapterActions, eventContext, emittedSignals);
        } catch (RuntimeException e) {
            if (SystemExecutionContract.isCommandStartSignal(signalName)) {
                activeCommandMessages.remove(instanceId);
            }
            throw e;
        }
        deviceTwinStateService.save(twinState);
        executeAndPublish(instanceId, internalActions, eventContext, emittedSignals);

        if (result.nextCmdState() != null
                && SystemExecutionContract.terminalCommandStateNames().contains(result.nextCmdState())) {
            resetTerminalCommandState(instance, model, twinState, interfaceName, signalName, emittedSignals);
        }
        return List.copyOf(emittedSignals);
    }

    public Map<String, ObjectNode> interfaceSignalSnapshot() {
        return Map.copyOf(interfaceSignalTable);
    }

    private void resetTerminalCommandState(DeviceInstances instance, DeviceModels model, DeviceTwinStates twinState,
                                           String interfaceName, String signalName, List<ObjectNode> emittedSignals) {
        String initialState = initialState(model.getCmdState(), "CMD");
        if (initialState.equals(twinState.getCurrentCmdState())) {
            return;
        }
        activeCommandMessages.remove(instance.getId());
        twinState.setCurrentCmdState(initialState);
        twinState.setUpdateTime(OffsetDateTime.now());
        Map<String, Object> resetContext = Map.of();
        StateMachineModels.EventContext resetEventContext = new StateMachineModels.EventContext(
                instance, model, twinState, interfaceName, signalName, resetContext);
        List<StateMachineModels.ActionDefinition> resetActions = findOnEntryActions(
                new StateMachineModels.Definition(model), "CMD", initialState);
        ensureStatusAction(resetActions, "CMD_STATE");
        deviceTwinStateService.save(twinState);
        executeAndPublish(instance.getId(), resetActions, resetEventContext, emittedSignals);
    }

    private void executeAndPublish(Long instanceId, List<StateMachineModels.ActionDefinition> actions,
                                   StateMachineModels.EventContext context, List<ObjectNode> emittedSignals) {
        for (StateMachineModels.ActionDefinition action : actions) {
            StateMachineActionExecutor executor = actionRegistry.required(action.actionName());
            ObjectNode emitted = executor.execute(action, context);
            if (emitted == null) {
                continue;
            }
            emittedSignals.add(emitted);
            String outputInterface = action.payload().path("interfaceName").asText("");
            String interfaceType = resolveOutputInterfaceType(context.model().getStateMachineInterfaces(), outputInterface);
            cacheSignal(instanceId, outputInterface, emitted);
            eventPublisher.publishEvent(new StateMachineInterfaceSignalEvent(
                    instanceId, outputInterface, interfaceType, emitted.deepCopy(), context.executionContext()));
        }
    }

    private TransitionResult computeNextState(StateMachineModels.Definition definition, String currentCmdState,
                                              JsonNode currentOpState, String interfaceName, String signalName,
                                              Map<String, Object> context) {
        TransitionResult standardTransition = systemCommandTransition(currentCmdState, interfaceName, signalName);
        if (standardTransition != null) return standardTransition;
        if (SystemExecutionContract.isCommandStartSignal(signalName)
                || SystemExecutionContract.isCommandAbortSignal(signalName)) return TransitionResult.none();

        ObjectNode nextOpState = currentOpState != null && currentOpState.isObject()
                ? currentOpState.deepCopy() : JsonNodeSupport.objectNode();
        String nextCmdState = null;
        List<StateMachineModels.ActionDefinition> actions = new ArrayList<>();
        LinkedHashSet<String> changedOpRegions = new LinkedHashSet<>();
        for (StateMachineModels.TransitionRule transition : definition.getTransitions()) {
            if (!interfaceName.equals(transition.triggerInterface()) || !signalName.equals(transition.triggerSignal())) continue;
            if ("CMD".equals(transition.stateSpace())) {
                if (!currentCmdState.equals(transition.fromState())) continue;
                if (nextCmdState != null) {
                    throw new IllegalStateException("同一事件命中多个CMD状态转移: " + interfaceName + "." + signalName);
                }
                nextCmdState = transition.toState();
                actions.addAll(transition.actions());
                actions.addAll(findOnEntryActions(definition, "CMD", transition.toState()));
                continue;
            }
            if (!"OP".equals(transition.stateSpace())) continue;
            String regionName = transition.regionName();
            if (regionName.isBlank() || changedOpRegions.contains(regionName)
                    || !currentOpState.path(regionName).asText("").equals(transition.fromState())) continue;
            nextOpState.put(regionName, transition.toState());
            changedOpRegions.add(regionName);
            actions.addAll(transition.actions());
            actions.addAll(findOpOnEntryActions(definition, regionName, transition.toState()));
        }
        if (nextCmdState == null && changedOpRegions.isEmpty()) return TransitionResult.none();
        return new TransitionResult(nextCmdState, changedOpRegions.isEmpty() ? null : nextOpState,
                List.copyOf(actions), List.copyOf(changedOpRegions));
    }

    private TransitionResult systemCommandTransition(String currentCmdState,
                                                     String interfaceName,
                                                     String signalName) {
        return SystemExecutionContract.findStateMachineSystemTransition(
                        "CMD", currentCmdState, interfaceName, signalName)
                .map(row -> new TransitionResult(
                        row.toStateName(),
                        null,
                        List.of(sendAction(row.actionInterfaceName(), row.actionSignalName())),
                        List.of()))
                .orElse(null);
    }

    private StateMachineModels.ActionDefinition sendAction(String interfaceName, String signalName) {
        return sendAction(interfaceName, signalName, null);
    }

    private StateMachineModels.ActionDefinition sendAction(String interfaceName, String signalName, String regionName) {
        ObjectNode payload = JsonNodeSupport.objectNode();
        payload.put("interfaceName", interfaceName);
        payload.put("signalName", signalName);
        if (regionName != null && !regionName.isBlank()) payload.put("regionName", regionName);
        return new StateMachineModels.ActionDefinition("SEND", payload);
    }

    private void ensureStatusAction(List<StateMachineModels.ActionDefinition> actions, String signalName) {
        ensureStatusAction(actions, signalName, null);
    }

    private void ensureStatusAction(List<StateMachineModels.ActionDefinition> actions, String signalName, String regionName) {
        boolean alreadyDeclared = actions.stream().anyMatch(action -> "SEND".equals(action.actionName())
                && signalName.equals(action.payload().path("signalName").asText())
                && (regionName == null || regionName.equals(action.payload().path("regionName").asText())));
        if (!alreadyDeclared) {
            actions.add(sendAction(SystemExecutionContract.stateOutputInterfaceName(), signalName, regionName));
        }
    }

    private Map<String, Object> prepareStartContext(DeviceInstances instance, DeviceModels model,
                                                    String signalName, Map<String, Object> original) {
        if ("CONSTRAINT_EXECUTE".equals(signalName)) {
            Object targetInstanceId = original.get("deviceInstanceId");
            if (targetInstanceId != null && !String.valueOf(instance.getId()).equals(String.valueOf(targetInstanceId))) {
                throw new IllegalArgumentException("CONSTRAINT_EXECUTE目标设备与状态机实例不一致");
            }
        }
        String capabilityName = text(original.get("capabilityName"));
        if (capabilityName.isBlank()) {
            capabilityName = text(original.get("commandName"));
        }
        if (capabilityName.isBlank()) {
            throw new IllegalArgumentException(signalName + "缺少capabilityName");
        }
        CapabilityCommand capability = resolveCapability(model, capabilityName);
        Map<String, Object> context = new HashMap<>(original);
        context.put("capabilityName", capability.capabilityName());
        context.put("commandName", capability.adapterCommandName());
        context.put("parameters", requiredParameters(original));
        context.put("messageId", textOrGenerated(original.get("messageId")));
        protocolDictionaryService.validateSignalExecutionContext("CMD_START", context);
        activeCommandMessages.put(instance.getId(), String.valueOf(context.get("messageId")));
        return context;
    }

    private CapabilityCommand resolveCapability(DeviceModels model, String capabilityName) {
        JsonNode capabilities = model.getCapabilities();
        if (capabilities != null && capabilities.isArray()) {
            for (JsonNode capability : capabilities) {
                if (capabilityName.equals(capability.path("capabilityName").asText())) {
                    String adapterCommandName = capability.path("adapterCommandName").asText("");
                    if (adapterCommandName.isBlank()) {
                        throw new IllegalStateException("能力缺少adapterCommandName: " + capabilityName);
                    }
                    return new CapabilityCommand(capabilityName, adapterCommandName);
                }
            }
        }
        throw new IllegalArgumentException("设备模型未声明能力: " + capabilityName);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requiredParameters(Map<String, Object> context) {
        Object parameters = context.get("parameters");
        if (parameters instanceof Map<?, ?> values) {
            return Map.copyOf((Map<String, Object>) values);
        }
        if (parameters instanceof JsonNode node && node.isObject()) {
            return JsonNodeSupport.MAPPER.convertValue(node, Map.class);
        }
        throw new IllegalArgumentException("能力参数parameters必须是对象");
    }

    private String activeMessageId(Long instanceId, Map<String, Object> context) {
        String explicit = text(context.get("messageId"));
        String active = activeCommandMessages.get(instanceId);
        if (active == null || active.isBlank()) active = restoreActiveTaskMessageId(instanceId);
        if (!explicit.isBlank()) {
            if (active != null && !active.isBlank() && !active.equals(explicit)) {
                throw new IllegalArgumentException("终止messageId与当前设备命令不一致");
            }
            return explicit;
        }
        if (active == null || active.isBlank()) {
            throw new IllegalStateException("设备没有可关联的活动命令，拒绝发送终止命令");
        }
        return active;
    }

    private void validateCommandEventCorrelation(Long instanceId, DeviceModels model, String interfaceName,
                                                 String signalName, String incomingMessageId) {
        if (!isCommandLifecycleAdapterEvent(model, interfaceName, signalName)) return;
        if (incomingMessageId == null || incomingMessageId.isBlank()) {
            throw new IllegalArgumentException("Adapter指令生命周期事件必须携带messageId: " + signalName);
        }
        String expected = activeCommandMessages.get(instanceId);
        if (expected == null || expected.isBlank()) expected = restoreActiveTaskMessageId(instanceId);
        if (expected == null || expected.isBlank()) {
            throw new IllegalStateException("设备没有可关联的活动命令，拒绝处理Adapter指令事件: " + signalName);
        }
        if (!expected.equals(incomingMessageId)) {
            throw new IllegalArgumentException("Adapter事件messageId与当前设备命令不一致");
        }
        activeCommandMessages.put(instanceId, expected);
    }

    private boolean isAdapterInputInterface(DeviceModels model, String interfaceName) {
        JsonNode interfaces = model.getStateMachineInterfaces();
        if (interfaces == null || !interfaces.isArray()) return false;
        for (JsonNode item : interfaces) {
            if (interfaceName.equals(item.path("name").asText())
                    && "IN".equals(item.path("direction").asText())) {
                return "ADAPTER".equals(item.path("interfaceType").asText());
            }
        }
        return false;
    }
    private boolean isCommandLifecycleAdapterEvent(DeviceModels model, String interfaceName, String signalName) {
        JsonNode transitions = model.getStateTransitions();
        if (transitions == null || !transitions.isArray()) return false;
        for (JsonNode transition : transitions) {
            JsonNode trigger = transition.path("trigger");
            if ("CMD".equals(transition.path("stateSpace").asText())
                    && interfaceName.equals(trigger.path("interfaceName").asText())
                    && signalName.equals(trigger.path("signalName").asText())) return true;
        }
        return false;
    }

    private String restoreActiveTaskMessageId(Long instanceId) {
        if (taskStepMapper == null || instanceId == null) return null;
        List<String> candidates = taskStepMapper.selectList(Wrappers.<TaskStep>lambdaQuery()
                        .eq(TaskStep::getNodeStatus, "RUNNING"))
                .stream().map(TaskStep::getInterfaceInSnapshot)
                .filter(snapshot -> snapshot != null && snapshot.isObject()
                        && snapshot.path("deviceInstanceId").canConvertToLong()
                        && instanceId.equals(snapshot.path("deviceInstanceId").asLong()))
                .map(snapshot -> snapshot.path("messageId").asText(""))
                .filter(messageId -> !messageId.isBlank()).distinct().toList();
        if (candidates.size() == 1) {
            activeCommandMessages.put(instanceId, candidates.getFirst());
            return candidates.getFirst();
        }
        return null;
    }

    private boolean isAdapterOutputAction(DeviceModels model, StateMachineModels.ActionDefinition action) {
        if (!"SEND".equals(action.actionName())) return false;
        String interfaceName = action.payload().path("interfaceName").asText("");
        return "ADAPTER".equals(resolveOutputInterfaceType(model.getStateMachineInterfaces(), interfaceName));
    }

    private String resolveInputInterface(Long instanceId, String interfaceType, String signalName) {
        DeviceInstances instance = requireInstance(instanceId);
        DeviceModels model = requireModel(instance);
        JsonNode interfaces = model.getStateMachineInterfaces();
        if (interfaces != null && interfaces.isArray()) {
            for (JsonNode item : interfaces) {
                if (interfaceType.equals(item.path("interfaceType").asText())
                        && "IN".equals(item.path("direction").asText())
                        && contains(item.path("allowedSignals"), signalName)) {
                    return item.path("name").asText();
                }
            }
        }
        throw new IllegalArgumentException("状态机没有可接收信号的接口: " + interfaceType + "." + signalName);
    }

    private void requireInputInterface(JsonNode interfaces, String interfaceName, String signalName) {
        if (interfaces != null && interfaces.isArray()) {
            for (JsonNode item : interfaces) {
                if (interfaceName.equals(item.path("name").asText())
                        && "IN".equals(item.path("direction").asText())
                        && contains(item.path("allowedSignals"), signalName)) {
                    return;
                }
            }
        }
        throw new IllegalArgumentException("状态机输入接口不接受信号: " + interfaceName + "." + signalName);
    }

    private DeviceInstances requireInstance(Long instanceId) {
        DeviceInstances instance = deviceInstancesMapper.selectById(instanceId);
        if (instance == null) {
            throw new IllegalArgumentException("设备实例不存在: " + instanceId);
        }
        return instance;
    }

    private DeviceModels requireModel(DeviceInstances instance) {
        DeviceModels model = deviceModelService.getById(String.valueOf(instance.getDeviceModelId()));
        if (model == null) {
            throw new IllegalArgumentException("设备模型不存在: " + instance.getDeviceModelId());
        }
        return model;
    }

    private String currentCommandState(DeviceTwinStates twinState, DeviceModels model) {
        String current = twinState.getCurrentCmdState();
        return current == null || current.isBlank() ? initialState(model.getCmdState(), "CMD") : current;
    }

    private JsonNode currentOperationState(DeviceTwinStates twinState, DeviceModels model) {
        return twinState.getCurrentOpState() == null ? initialOperationState(model.getOpState()) : twinState.getCurrentOpState();
    }

    private String initialState(JsonNode stateSpace, String stateSpaceName) {
        String initialState = stateSpace == null ? "" : stateSpace.path("initialStateName").asText("");
        if (initialState.isBlank()) {
            throw new IllegalStateException(stateSpaceName + "状态空间缺少initialStateName");
        }
        return initialState;
    }

    private JsonNode initialOperationState(JsonNode opStateSpace) {
        ObjectNode initial = JsonNodeSupport.objectNode();
        if (opStateSpace != null && opStateSpace.path("regions").isArray()) {
            for (JsonNode region : opStateSpace.path("regions")) {
                String regionName = region.path("regionName").asText("");
                String initialState = region.path("initialStateName").asText("");
                if (!regionName.isBlank()) {
                    initial.put(regionName, initialState);
                }
            }
        }
        return initial;
    }

    private List<StateMachineModels.ActionDefinition> findOnEntryActions(StateMachineModels.Definition definition,
                                                                           String stateSpace, String stateName) {
        List<StateMachineModels.ActionDefinition> result = new ArrayList<>();
        JsonNode states = "CMD".equals(stateSpace) ? definition.getModel().getCmdState().path("states") : null;
        if (states != null && states.isArray()) {
            for (JsonNode state : states) {
                if (stateName.equals(state.path("stateName").asText())) {
                    addActions(state.path("onEntry"), result);
                    return result;
                }
            }
        }
        return result;
    }

    private List<StateMachineModels.ActionDefinition> findOpOnEntryActions(StateMachineModels.Definition definition,
                                                                             String regionName, String stateName) {
        List<StateMachineModels.ActionDefinition> result = new ArrayList<>();
        JsonNode regions = definition.getModel().getOpState().path("regions");
        if (regions.isArray()) {
            for (JsonNode region : regions) {
                if (!regionName.equals(region.path("regionName").asText())) {
                    continue;
                }
                for (JsonNode state : region.path("states")) {
                    if (stateName.equals(state.path("stateName").asText())) {
                        addActions(state.path("onEntry"), result);
                        return result;
                    }
                }
            }
        }
        return result;
    }

    private void addActions(JsonNode nodes, List<StateMachineModels.ActionDefinition> target) {
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                target.add(StateMachineModels.ActionDefinition.fromJson(node));
            }
        }
    }

    private String resolveOutputInterfaceType(JsonNode interfaces, String interfaceName) {
        if (interfaces != null && interfaces.isArray()) {
            for (JsonNode item : interfaces) {
                if (interfaceName.equals(item.path("name").asText())
                        && "OUT".equals(item.path("direction").asText())) {
                    return item.path("interfaceType").asText();
                }
            }
        }
        throw new IllegalArgumentException("状态机没有输出接口: " + interfaceName);
    }

    private void cacheSignal(Long instanceId, String interfaceName, ObjectNode signal) {
        String signalName = signal.path("signalName").asText("");
        interfaceSignalTable.put(instanceId + "::" + interfaceName + "::" + signalName, signal.deepCopy());
        interfaceSignalTable.put(instanceId + "::" + interfaceName + "::__last__", signal.deepCopy());
    }

    private DeviceTwinStates createDefaultTwinState(Long instanceId, DeviceModels model) {
        DeviceTwinStates state = new DeviceTwinStates();
        state.setInstanceId(instanceId);
        state.setCurrentOpState(initialOperationState(model.getOpState()));
        state.setCurrentCmdState(initialState(model.getCmdState(), "CMD"));
        state.setOnlineStatus("UNKNOWN");
        state.setCurrentAttr(JsonNodeSupport.objectNode());
        state.setUpdateTime(OffsetDateTime.now());
        deviceTwinStateService.save(state);
        return state;
    }

    private boolean contains(JsonNode values, String expected) {
        if (values != null && values.isArray()) {
            for (JsonNode value : values) {
                if (expected.equals(value.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String textOrGenerated(Object value) {
        String text = text(value);
        return text.isBlank() ? UUID.randomUUID().toString() : text;
    }

    private record CapabilityCommand(String capabilityName, String adapterCommandName) {
    }

    private record TransitionResult(String nextCmdState, JsonNode nextOpState,
                                    List<StateMachineModels.ActionDefinition> actions, List<String> changedOpRegions) {
        static TransitionResult none() {
            return new TransitionResult(null, null, List.of(), List.of());
        }

        boolean changed() {
            return nextCmdState != null || nextOpState != null;
        }
    }
}
