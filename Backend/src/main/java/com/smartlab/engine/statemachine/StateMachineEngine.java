package com.smartlab.engine.statemachine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.action.StateMachineActionExecutor;
import com.smartlab.engine.statemachine.action.StateMachineActionRegistry;
import com.smartlab.global.schema.SchemaMetadataService;
import com.smartlab.global.protocol.ProtocolDictionaryService;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.service.db.resource.device.DeviceModelService;
import com.smartlab.management.service.db.resource.device.DeviceTwinStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final StateMachineActionRegistry actionRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final SchemaMetadataService schemaMetadataService;
    private final ProtocolDictionaryService protocolDictionaryService;
    private final ConcurrentHashMap<String, ObjectNode> interfaceSignalTable = new ConcurrentHashMap<>();

    public StateMachineEngine(DeviceModelService deviceModelService,
                              DeviceTwinStateService deviceTwinStateService,
                              DeviceInstancesMapper deviceInstancesMapper,
                              StateMachineActionRegistry actionRegistry,
                              ApplicationEventPublisher eventPublisher) {
        this(deviceModelService, deviceTwinStateService, deviceInstancesMapper,
                actionRegistry, eventPublisher, new SchemaMetadataService(), new ProtocolDictionaryService());
    }

    @Autowired
    public StateMachineEngine(DeviceModelService deviceModelService,
                              DeviceTwinStateService deviceTwinStateService,
                              DeviceInstancesMapper deviceInstancesMapper,
                              StateMachineActionRegistry actionRegistry,
                              ApplicationEventPublisher eventPublisher,
                              SchemaMetadataService schemaMetadataService,
                              ProtocolDictionaryService protocolDictionaryService) {
        this.deviceModelService = deviceModelService;
        this.deviceTwinStateService = deviceTwinStateService;
        this.deviceInstancesMapper = deviceInstancesMapper;
        this.actionRegistry = actionRegistry;
        this.eventPublisher = eventPublisher;
        this.schemaMetadataService = schemaMetadataService;
        this.protocolDictionaryService = protocolDictionaryService;
    }

    public ObjectNode handleManualControl(Long instanceId, String commandId, Map<String, Object> parameters) {
        return handleManualControl(instanceId, "MANUAL_EXECUTE_START", commandId, parameters);
    }

    public ObjectNode handleManualControl(Long instanceId, String signalName, String commandId, Map<String, Object> parameters) {
        String resolvedSignal = signalName == null || signalName.isBlank() ? "MANUAL_EXECUTE_START" : signalName;
        Map<String, Object> context = new HashMap<>();
        context.put("commandName", commandId);
        context.put("parameters", parameters == null ? Map.of() : parameters);
        protocolDictionaryService.validateSignalExecutionContext(resolvedSignal, context);

        log.info("手动触发设备 {} 的控制信号 {}，指令 {}", instanceId, resolvedSignal, commandId);
        String interfaceName = resolveInputInterface(instanceId, "CONTROL", resolvedSignal);
        List<ObjectNode> emittedSignals = dispatchSignal(instanceId, interfaceName, resolvedSignal, context);
        if (emittedSignals.isEmpty()) {
            DeviceTwinStates twinState = deviceTwinStateService.getByInstanceId(instanceId);
            String currentCmd = twinState == null ? "IDLE" : twinState.getCurrentCmdState();
            throw new IllegalStateException("当前指令执行生命周期状态为 " + currentCmd + "，状态机拒绝此手动操作");
        }

        ObjectNode result = JsonNodeSupport.objectNode();
        result.put("instanceId", instanceId);
        result.put("interfaceName", interfaceName);
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
        dispatchSignalByType(instanceId, "ADAPTER", eventName, context);
    }

    public List<ObjectNode> dispatchSignalByType(Long instanceId, String interfaceType, String signalName,
                                                  Map<String, Object> executionContext) {
        if (Set.of("WORKFLOW", "CONTROL", "CONSTRAINT").contains(interfaceType))
            protocolDictionaryService.validateSignalExecutionContext(signalName, executionContext);
        return dispatchSignal(instanceId, resolveInputInterface(instanceId, interfaceType, signalName), signalName, executionContext);
    }

    private String resolveInputInterface(Long instanceId, String interfaceType, String signalName) {
        DeviceInstances instance = deviceInstancesMapper.selectById(instanceId);
        if (instance == null) throw new IllegalArgumentException("设备实例不存在: " + instanceId);
        DeviceModels model = deviceModelService.getById(String.valueOf(instance.getDeviceModelId()));
        if (model == null) throw new IllegalArgumentException("设备模型不存在: " + instance.getDeviceModelId());
        JsonNode interfaces = model.getStateMachineInterfaces();
        if (interfaces != null && interfaces.isArray()) {
            for (JsonNode item : interfaces) {
                if (interfaceType.equals(item.path("interfaceType").asText())
                        && "IN".equals(item.path("direction").asText())
                        && containsText(item.path("allowedSignals"), signalName))
                    return item.path("name").asText();
            }
        }
        throw new IllegalArgumentException("状态机没有可接收信号的接口: " + interfaceType + "." + signalName);
    }

    private boolean containsText(JsonNode values, String expected) {
        if (values != null && values.isArray())
            for (JsonNode value : values) if (expected.equals(value.asText())) return true;
        return false;
    }

    public synchronized List<ObjectNode> dispatchSignal(Long instanceId, String interfaceName, String signalName, Map<String, Object> executionContext) {
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
            twinState = createDefaultTwinState(instanceId, model);
        }

        String currentCmd = twinState.getCurrentCmdState() == null
                ? initialState(model.getCmdState(), "CMD") : twinState.getCurrentCmdState();
        JsonNode currentOp = twinState.getCurrentOpState() == null
                ? initialOperationState(model.getOpState()) : twinState.getCurrentOpState();

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

        twinState.setUpdateTime(OffsetDateTime.now());

        // 3. 构造运行上下文
        StateMachineModels.EventContext eventContext = new StateMachineModels.EventContext(
                instance, model, twinState, interfaceName, signalName, executionContext
        );

        List<ObjectNode> emittedSignals = new ArrayList<>();
        List<EmittedSignal> routedSignals = executeActions(
                result.actionsToExecute, eventContext, emittedSignals);
        deviceTwinStateService.save(twinState);
        publishRoutedSignals(instanceId, routedSignals, executionContext);
        if (result.nextCmdState != null && isTerminalCommandState(result.nextCmdState)) {
            twinState.setCurrentCmdState(initialState(model.getCmdState(), "CMD"));
            twinState.setUpdateTime(OffsetDateTime.now());
            deviceTwinStateService.save(twinState);
            StateMachineModels.EventContext idleContext = new StateMachineModels.EventContext(
                    instance, model, twinState, interfaceName, signalName, executionContext);
            List<EmittedSignal> idleSignals = executeActions(
                    findOnEntryActions(definition, "CMD", twinState.getCurrentCmdState()),
                    idleContext,
                    emittedSignals);
            publishRoutedSignals(instanceId, idleSignals, executionContext);
        }
        return emittedSignals;
    }

    private List<EmittedSignal> executeActions(
            List<StateMachineModels.ActionDefinition> actions,
            StateMachineModels.EventContext context,
            List<ObjectNode> emittedSignals
    ) {
        List<EmittedSignal> routedSignals = new ArrayList<>();
        for (StateMachineModels.ActionDefinition action : actions) {
            StateMachineActionExecutor executor = actionRegistry.required(action.actionName());
            ObjectNode emitted = executor.execute(action, context);
            if (emitted == null) {
                continue;
            }
            emittedSignals.add(emitted);
            String outputInterface = action.payload().path("interfaceName").asText("");
            String outputInterfaceType =
                    resolveOutputInterfaceType(context.model().getStateMachineInterfaces(), outputInterface);
            routedSignals.add(new EmittedSignal(outputInterface, outputInterfaceType, emitted));
            cacheSignal(context.instance().getId(), outputInterface, emitted);
        }
        return routedSignals;
    }

    private void publishRoutedSignals(
            Long instanceId,
            List<EmittedSignal> routedSignals,
            Map<String, Object> executionContext
    ) {
        for (EmittedSignal routedSignal : routedSignals) {
            eventPublisher.publishEvent(new StateMachineInterfaceSignalEvent(
                    instanceId,
                    routedSignal.interfaceName(),
                    routedSignal.interfaceType(),
                    routedSignal.signal().deepCopy(),
                    executionContext == null ? Map.of() : Map.copyOf(executionContext)));
        }
    }
    private TransitionResult computeNextState(
            StateMachineModels.Definition definition,
            String currentCmd,
            JsonNode currentOp,
            String interfaceName,
            String signalName
    ) {
        String nextCmdState = null;
        ObjectNode updatedOpState = null;
        List<StateMachineModels.ActionDefinition> actionsToExecute = new ArrayList<>();

        Set<String> cmdStates = stateNames(definition.getModel().getCmdState());
        ObjectNode currentOpCopy = currentOp != null && currentOp.isObject() ? currentOp.deepCopy() : JsonNodeSupport.objectNode();
        boolean opStateChanged = false;

        for (StateMachineModels.TransitionRule transition : definition.getTransitions()) {
            if (transition.automatic()
                    || !transition.triggerInterface().equals(interfaceName)
                    || !transition.triggerSignal().equals(signalName)) {
                continue;
            }

            if ("CMD".equals(transition.stateSpace())
                    && nextCmdState == null
                    && transition.fromState().equals(currentCmd)
                    && cmdStates.contains(transition.fromState())
                    && cmdStates.contains(transition.toState())) {
                nextCmdState = transition.toState();
                actionsToExecute.addAll(transition.actions());
                continue;
            }
            if ("OP".equals(transition.stateSpace())) {
                String regionName = transition.regionName();
                if (regionName.isBlank()) {
                    regionName = firstOpRegionName(definition.getModel().getOpState());
                }
                if (regionName.isBlank()) {
                    continue;
                }
                String currentRegionState = currentOpCopy.path(regionName).asText("");
                Set<String> opRegionStates = regionStateNames(definition.getModel().getOpState(), regionName);
                if (currentRegionState.equals(transition.fromState())
                        && opRegionStates.contains(transition.fromState())
                        && opRegionStates.contains(transition.toState())) {
                    currentOpCopy.put(regionName, transition.toState());
                    opStateChanged = true;
                    actionsToExecute.addAll(transition.actions());
                    actionsToExecute.addAll(findOpOnEntryActions(definition, regionName, transition.toState()));
                }
            }
        }

        if (nextCmdState != null) {
            actionsToExecute.addAll(findOnEntryActions(definition, "CMD", nextCmdState));
            Set<String> visitedAutomaticStates = new java.util.HashSet<>();
            while (nextCmdState != null) {
                if (!visitedAutomaticStates.add(nextCmdState)) {
                    throw new IllegalStateException("状态机自动转移形成循环: " + nextCmdState);
                }
                StateMachineModels.TransitionRule automaticTransition =
                        findAutomaticCommandTransition(definition, nextCmdState, cmdStates);
                if (automaticTransition == null) {
                    break;
                }
                nextCmdState = automaticTransition.toState();
                actionsToExecute.addAll(automaticTransition.actions());
                actionsToExecute.addAll(findOnEntryActions(definition, "CMD", nextCmdState));
            }
        }
        if (opStateChanged) {
            updatedOpState = currentOpCopy;
        }

        return new TransitionResult(nextCmdState, updatedOpState, actionsToExecute);
    }

    private StateMachineModels.TransitionRule findAutomaticCommandTransition(
            StateMachineModels.Definition definition,
            String currentState,
            Set<String> commandStates
    ) {
        for (StateMachineModels.TransitionRule transition : definition.getTransitions()) {
            if (transition.automatic()
                    && "CMD".equals(transition.stateSpace())
                    && transition.fromState().equals(currentState)
                    && commandStates.contains(transition.toState())) {
                return transition;
            }
        }
        return null;
    }

    private Set<String> stateNames(JsonNode stateSpace) {
        if (stateSpace == null || !stateSpace.has("states") || !stateSpace.get("states").isArray()) {
            return Set.of();
        }
        Set<String> names = new java.util.HashSet<>();
        for (JsonNode state : stateSpace.get("states")) {
            String name = state.path("stateName").asText("");
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return names;
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

    public Map<String, ObjectNode> interfaceSignalSnapshot() {
        return Map.copyOf(interfaceSignalTable);
    }

    private void cacheSignal(Long instanceId, String interfaceName, ObjectNode signal) {
        String signalName = signal.path("signalName").asText("");

        interfaceSignalTable.put(instanceId + "::" + interfaceName + "::" + signalName, signal);
        interfaceSignalTable.put(instanceId + "::" + interfaceName + "::__last__", signal);
    }

    private boolean isTerminalCommandState(String state) {
        return containsText(schemaMetadataService.frontendMetadata()
                .path("stateMachine").path("commandTerminalStates"), state);
    }

    private String initialState(JsonNode stateSpace, String stateSpaceName) {
        String initialState = stateSpace == null ? "" : stateSpace.path("initialStateName").asText("");
        if (initialState.isBlank()) {
            throw new IllegalStateException(stateSpaceName + " 状态空间缺少 initialStateName");
        }
        return initialState;
    }

    private String resolveOutputInterfaceType(JsonNode interfaces, String interfaceName) {
        if (interfaces != null && interfaces.isArray()) {
            for (JsonNode item : interfaces) {
                if (interfaceName.equals(item.path("name").asText())
                        && "OUT".equals(item.path("direction").asText())) {
                    return item.path("interfaceType").asText("");
                }
            }
        }
        throw new IllegalArgumentException("状态机没有输出接口: " + interfaceName);
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

    private JsonNode initialOperationState(JsonNode opStateSpace) {
        ObjectNode initial = JsonNodeSupport.objectNode();
        if (opStateSpace != null && opStateSpace.has("regions") && opStateSpace.path("regions").isArray()) {
            for (JsonNode region : opStateSpace.path("regions")) {
                String regionName = region.path("regionName").asText("");
                String initState = region.path("initialStateName").asText("");
                if (!regionName.isBlank()) {
                    initial.put(regionName, initState);
                }
            }
        }
        return initial;
    }

    private String firstOpRegionName(JsonNode opState) {
        if (opState != null && opState.has("regions") && opState.path("regions").isArray()) {
            JsonNode regions = opState.path("regions");
            if (regions.size() > 0) {
                return regions.get(0).path("regionName").asText("");
            }
        }
        return "";
    }

    private Set<String> regionStateNames(JsonNode opState, String regionName) {
        Set<String> states = new java.util.HashSet<>();
        if (opState != null && opState.has("regions") && opState.path("regions").isArray()) {
            for (JsonNode region : opState.path("regions")) {
                if (regionName.equals(region.path("regionName").asText(""))) {
                    JsonNode statesNode = region.path("states");
                    if (statesNode.isArray()) {
                        for (JsonNode state : statesNode) {
                            String name = state.path("stateName").asText("");
                            if (!name.isBlank()) {
                                states.add(name);
                            }
                        }
                    }
                }
            }
        }
        return states;
    }

    private List<StateMachineModels.ActionDefinition> findOpOnEntryActions(
            StateMachineModels.Definition definition, String regionName, String stateName) {
        List<StateMachineModels.ActionDefinition> result = new ArrayList<>();
        JsonNode opState = definition.getModel().getOpState();
        if (opState != null && opState.has("regions") && opState.path("regions").isArray()) {
            for (JsonNode region : opState.path("regions")) {
                if (regionName.equals(region.path("regionName").asText(""))) {
                    JsonNode statesNode = region.path("states");
                    if (statesNode.isArray()) {
                        for (JsonNode state : statesNode) {
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
                }
            }
        }
        return result;
    }

    private record TransitionResult(
            String nextCmdState,
            JsonNode nextOpState,
            List<StateMachineModels.ActionDefinition> actionsToExecute
    ) {}

    private record EmittedSignal(String interfaceName, String interfaceType, ObjectNode signal) {}
}
