package com.smartlab.engine.statemachine;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.observation.ObservableKey;
import com.smartlab.engine.observation.ObservationSnapshot;
import com.smartlab.engine.observation.device.DeviceTwinSnapshot;
import com.smartlab.engine.observation.device.DeviceTwinSnapshotRegistry;
import com.smartlab.engine.observation.statemachine.StateMachineObservationRegistry;
import com.smartlab.engine.statemachine.action.StateMachineActionExecutor;
import com.smartlab.engine.statemachine.action.StateMachineActionRegistry;
import com.smartlab.engine.statemachine.IntrinsicConstraintPlanRegistry.CompiledIntrinsicConstraint;
import com.smartlab.engine.statemachine.IntrinsicConstraintPlanRegistry.IntrinsicConstraintPlan;
import com.smartlab.global.contract.ObservableObjectType;
import com.smartlab.global.contract.SystemExecutionContract;
import com.smartlab.global.event.DeviceInstanceRetiredEvent;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备状态机引擎。系统级CMD转移直接实现定稿系统执行规范；设备模型只声明Adapter事件和OP状态转移
 */
@Service
public class StateMachineEngine implements StateMachineCommandPort {

    private static final Logger log = LoggerFactory.getLogger(StateMachineEngine.class);

    private final DeviceModelService deviceModelService;
    private final DeviceTwinStateService deviceTwinStateService;
    private final DeviceInstancesMapper deviceInstancesMapper;
    private final StateMachineActionRegistry actionRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final ProtocolDictionaryService protocolDictionaryService;
    private final TaskStepMapper taskStepMapper;
    private final DeviceTwinSnapshotRegistry deviceTwinSnapshots;
    private final StateMachineInterfaceOutputDispatcher stateOutputDispatcher;
    private final IntrinsicConstraintPlanRegistry intrinsicConstraintPlans;
    private final StateMachineObservationRegistry stateObservations;
    private final ConcurrentHashMap<String, ObjectNode> interfaceSignalTable = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, DeviceStateMachineRuntime> deviceRuntimes = new ConcurrentHashMap<>();

    public StateMachineEngine(DeviceModelService deviceModelService,
                              DeviceTwinStateService deviceTwinStateService,
                              DeviceInstancesMapper deviceInstancesMapper,
                              StateMachineActionRegistry actionRegistry,
                              ApplicationEventPublisher eventPublisher,
                              ProtocolDictionaryService protocolDictionaryService) {
        this(deviceModelService, deviceTwinStateService, deviceInstancesMapper, actionRegistry,
                eventPublisher, protocolDictionaryService, null, null,
                new StateMachineInterfaceOutputDispatcher(), null, null);
    }

    @Autowired
    public StateMachineEngine(DeviceModelService deviceModelService,
                              DeviceTwinStateService deviceTwinStateService,
                              DeviceInstancesMapper deviceInstancesMapper,
                              StateMachineActionRegistry actionRegistry,
                              ApplicationEventPublisher eventPublisher,
                              ProtocolDictionaryService protocolDictionaryService,
                              TaskStepMapper taskStepMapper,
                              DeviceTwinSnapshotRegistry deviceTwinSnapshots,
                              StateMachineInterfaceOutputDispatcher stateOutputDispatcher,
                              IntrinsicConstraintPlanRegistry intrinsicConstraintPlans,
                              StateMachineObservationRegistry stateObservations) {
        this.deviceModelService = deviceModelService;
        this.deviceTwinStateService = deviceTwinStateService;
        this.deviceInstancesMapper = deviceInstancesMapper;
        this.actionRegistry = actionRegistry;
        this.eventPublisher = eventPublisher;
        this.protocolDictionaryService = protocolDictionaryService;
        this.taskStepMapper = taskStepMapper;
        this.deviceTwinSnapshots = deviceTwinSnapshots;
        this.stateOutputDispatcher = stateOutputDispatcher;
        this.intrinsicConstraintPlans = intrinsicConstraintPlans;
        this.stateObservations = stateObservations;
    }

    public ObjectNode handleManualControl(Long instanceId, String capabilityName, Map<String, Object> parameters) {
        return handleManualControl(instanceId, "MANUAL_EXECUTE_START", capabilityName, parameters);
    }

    public ObjectNode handleManualControl(Long instanceId, String signalName, String capabilityName,
                                          Map<String, Object> parameters) {
        String resolvedSignal = signalName == null || signalName.isBlank() ? "MANUAL_EXECUTE_START" : signalName;
        if (!SystemExecutionContract.isCommandStartSignal(resolvedSignal)
                && !SystemExecutionContract.isCommandAbortSignal(resolvedSignal)
                && !SystemExecutionContract.isCommandResetSignal(resolvedSignal)) {
            throw new IllegalArgumentException("不是可由人工控制台发送的状态机信号: " + resolvedSignal);
        }
        Map<String, Object> context = new HashMap<>();
        if (SystemExecutionContract.isCommandStartSignal(resolvedSignal)) {
            context.put("capabilityName", capabilityName);
            context.put("parameters", parameters == null ? Map.of() : Map.copyOf(parameters));
        }
        List<ObjectNode> emittedSignals = dispatchSignalByType(instanceId, "CONTROL", resolvedSignal, context);
        if (emittedSignals.isEmpty() && !SystemExecutionContract.isCommandResetSignal(resolvedSignal)) {
            String currentCmd = observedCommandState(instanceId);
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

    @Value("${smartlab.state-machine.command-timeout-seconds:10}")
    private long commandTimeoutSeconds = 10;

    /**
     * 指令执行超时看门狗：定时扫描处于 SENT 状态超过时限的指令，自动触发 FAILED 熔断并复位至 IDLE。
     */
    @Scheduled(fixedDelayString = "${smartlab.state-machine.command-timeout-scan-ms:1000}")
    public void checkCommandExecutionTimeouts() {
        if (deviceRuntimes.isEmpty()) return;
        Instant now = Instant.now();
        for (Map.Entry<Long, DeviceStateMachineRuntime> entry : deviceRuntimes.entrySet()) {
            Long instanceId = entry.getKey();
            DeviceStateMachineRuntime runtime = entry.getValue();
            if (!runtime.lock().tryLock()) continue;
            try {
                DeviceStateMachineRuntime.CommandExecution normal = runtime.normalExecution();
                if (normal != null && "SENT".equals(normal.state()) && normal.startedAt() != null) {
                    long elapsedSeconds = Duration.between(normal.startedAt(), now).getSeconds();
                    if (elapsedSeconds >= commandTimeoutSeconds) {
                        log.warn("设备指令发送超时未收到响应，看门狗自动熔断并复位: instanceId={}, capability={}, elapsed={}s",
                                instanceId, normal.capabilityName(), elapsedSeconds);
                        IntrinsicConstraintPlan plan = requireRuntimePlan(instanceId);
                        DeviceInstances instance = plan.instance();
                        DeviceModels model = plan.model();
                        DeviceTwinStates twinState = inMemoryTwinState(plan, null);
                        List<ObjectNode> emitted = new ArrayList<>();
                        transitionNormalState(instance, model, twinState,
                                "", "", normal, "FAILED", emitted);
                        runtime.removeExecution(normal);
                        resetTerminalCommandState(instance, model, twinState,
                                "", "", emitted);
                    }
                }
            } catch (Exception e) {
                log.error("检查指令超时异常, instanceId={}", instanceId, e);
            } finally {
                runtime.lock().unlock();
            }
        }
    }

    /** Releases transient command execution state after the instance retirement commits. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleInstanceRetired(DeviceInstanceRetiredEvent event) {
        if (event != null && event.deviceInstanceId() != null) {
            deviceRuntimes.remove(event.deviceInstanceId());
        }
    }

    /**
     * 遥测更新后由状态机内部的内置约束监测器评估设备模型约束，违规状态会锁存到 EXCEPTION 区域。
     */
    public List<ObjectNode> evaluateIntrinsicConstraints(DeviceTwinSnapshot attributeSnapshot) {
        if (attributeSnapshot == null || intrinsicConstraintPlans == null) return List.of();
        Long instanceId = attributeSnapshot.deviceInstanceId();
        DeviceStateMachineRuntime runtime = deviceRuntimes.computeIfAbsent(
                instanceId, DeviceStateMachineRuntime::new);
        runtime.exceptionLock().lock();
        try {
            return evaluateIntrinsicConstraintsLocked(
                    intrinsicConstraintPlans.require(instanceId), attributeSnapshot.attributes());
        } finally {
            runtime.exceptionLock().unlock();
        }
    }

    private List<ObjectNode> evaluateIntrinsicConstraintsLocked(IntrinsicConstraintPlan plan,
                                                                 JsonNode currentAttributes) {
        Long instanceId = plan.deviceInstanceId();
        DeviceInstances instance = plan.instance();
        DeviceModels model = plan.model();
        if (plan.constraints().isEmpty() || currentAttributes == null || !currentAttributes.isObject()) {
            return List.of();
        }

        LinkedHashSet<String> newlyViolated = new LinkedHashSet<>();
        Map<String, String> violationAttributes = new HashMap<>();
        Map<String, CompiledIntrinsicConstraint> violationDefinitions = new HashMap<>();
        Map<String, LinkedHashSet<String>> activeByRegion = new HashMap<>();
        for (String regionName : plan.exceptionRegions().values()) {
            activeByRegion.computeIfAbsent(regionName,
                    ignored -> currentOperationStates(instanceId, regionName));
        }
        for (CompiledIntrinsicConstraint constraint : plan.constraints()) {
            Double actual = numericValue(currentAttributes.get(constraint.attributeName()));
            if (actual == null || !violates(actual, constraint.operator(), constraint.boundaryValue())) {
                continue;
            }
            LinkedHashSet<String> activeStates = activeByRegion.get(constraint.regionName());
            if (activeStates.contains(constraint.violationStateName())) {
                continue;
            }
            if (deviceTwinStateService.addExceptionState(
                    instanceId, constraint.regionName(), constraint.violationStateName())) {
                activeStates.add(constraint.violationStateName());
                newlyViolated.add(constraint.violationStateName());
                violationAttributes.putIfAbsent(constraint.violationStateName(), constraint.attributeName());
                violationDefinitions.putIfAbsent(constraint.violationStateName(), constraint);
            }
        }
        if (newlyViolated.isEmpty()) return List.of();
        DeviceTwinStates twinState = inMemoryTwinState(plan, currentAttributes);
        for (Map.Entry<String, LinkedHashSet<String>> entry : activeByRegion.entrySet()) {
            ((ObjectNode) twinState.getCurrentOpState()).set(
                    entry.getKey(), JsonNodeSupport.toNode(entry.getValue()));
        }
        List<ObjectNode> emitted = new ArrayList<>();
        List<StateMachineModels.ActionDefinition> actions = new ArrayList<>();
        Map<String, Object> context = new HashMap<>();
        List<String> violationNames = new ArrayList<>();
        for (String violationStateName : newlyViolated) {
            String regionName = plan.exceptionRegion(violationStateName);
            CompiledIntrinsicConstraint constraint = violationDefinitions.get(violationStateName);
            context.put("regionName", regionName);
            context.put("constraintAttributeName", violationAttributes.get(violationStateName));
            context.put("actualValue", currentAttributes.path(violationAttributes.get(violationStateName)).asDouble());
            context.put("boundaryValue", constraint.boundaryValue());
            context.put("operator", constraint.operator());
            violationNames.add(violationStateName);
            actions.addAll(findOpOnEntryActions(
                    new StateMachineModels.Definition(model), regionName, violationStateName));
        }
        context.put("violationStateNames", List.copyOf(violationNames));
        executeExecutionActions(instance, model, twinState,
                "Interface_constraint_in", "INTRINSIC_CONSTRAINT_VIOLATED", context, actions, emitted);
        return List.copyOf(emitted);
    }

    /**
     * 人工解除一个已锁存的内置约束异常。解除前必须用当前遥测值复核该异常关联的全部约束。
     */
    public List<ObjectNode> clearIntrinsicException(Long instanceId, String violationStateName) {
        DeviceStateMachineRuntime runtime = deviceRuntimes.computeIfAbsent(
                instanceId, DeviceStateMachineRuntime::new);
        runtime.exceptionLock().lock();
        try {
            return clearIntrinsicExceptionLocked(instanceId, violationStateName);
        } finally {
            runtime.exceptionLock().unlock();
        }
    }

    private List<ObjectNode> clearIntrinsicExceptionLocked(Long instanceId, String violationStateName) {
        if (violationStateName == null || violationStateName.isBlank()) {
            throw new IllegalArgumentException("violationStateName不能为空");
        }
        if (intrinsicConstraintPlans == null) {
            throw new IllegalStateException("内置约束运行计划未初始化");
        }
        IntrinsicConstraintPlan plan = intrinsicConstraintPlans.require(instanceId);
        DeviceInstances instance = plan.instance();
        DeviceModels model = plan.model();
        String regionName = plan.exceptionRegion(violationStateName);
        if (regionName == null) {
            throw new IllegalArgumentException("不是已声明的异常状态: " + violationStateName);
        }
        List<CompiledIntrinsicConstraint> related = plan.constraintsForState(violationStateName);
        if (related.isEmpty()) {
            throw new IllegalArgumentException("异常状态未被任何内置约束引用，不能人工解除: " + violationStateName);
        }
        LinkedHashSet<String> activeStates = currentOperationStates(instanceId, regionName);
        if (!activeStates.contains(violationStateName)) {
            throw new IllegalStateException("异常状态当前未锁存: " + violationStateName);
        }
        DeviceTwinSnapshot attributeSnapshot = deviceTwinSnapshots == null
                ? null : deviceTwinSnapshots.snapshot(instanceId);
        JsonNode currentAttributes = attributeSnapshot == null ? null : attributeSnapshot.attributes();
        if (currentAttributes == null || !currentAttributes.isObject()) {
            throw new IllegalStateException("缺少当前遥测值，不能确认异常是否已经恢复");
        }
        for (CompiledIntrinsicConstraint constraint : related) {
            Double actual = numericValue(currentAttributes.get(constraint.attributeName()));
            if (actual == null) {
                throw new IllegalStateException("缺少约束属性当前值，不能解除异常: " + constraint.attributeName());
            }
            if (violates(actual, constraint.operator(), constraint.boundaryValue())) {
                throw new IllegalStateException("内置约束仍然违规，不能解除异常: " + violationStateName);
            }
        }

        if (!deviceTwinStateService.removeExceptionState(instanceId, regionName, violationStateName)) {
            throw new IllegalStateException("异常状态当前未锁存: " + violationStateName);
        }
        activeStates.remove(violationStateName);
        DeviceTwinStates twinState = inMemoryTwinState(plan, currentAttributes);
        ((ObjectNode) twinState.getCurrentOpState()).set(regionName, JsonNodeSupport.toNode(activeStates));
        List<ObjectNode> emitted = new ArrayList<>();
        executeExecutionActions(instance, model, twinState,
                "Interface_constraint_in", "INTRINSIC_EXCEPTION_CLEARED",
                Map.of("regionName", regionName, "violationStateName", violationStateName),
                List.of(sendAction(SystemExecutionContract.stateOutputInterfaceName(), "OP_STATE", regionName)), emitted);
        return List.copyOf(emitted);
    }

    private List<ObjectNode> dispatchSignalByType(Long instanceId, String interfaceType, String signalName,
                                                   Map<String, Object> executionContext) {
        IntrinsicConstraintPlan plan = requireRuntimePlan(instanceId);
        DeviceInstances instance = plan.instance();
        DeviceModels model = plan.model();
        return dispatchResolvedSignal(instance, model, resolveInputInterface(model, interfaceType, signalName),
                signalName, executionContext);
    }

    @Override
    public List<ObjectNode> executeConstraintCapability(Long deviceInstanceId,
                                                         String capabilityName,
                                                         Map<String, Object> parameters) {
        Map<String, Object> context = new HashMap<>();
        context.put("capabilityName", capabilityName);
        context.put("parameters", parameters == null ? Map.of() : Map.copyOf(parameters));
        return dispatchSignalByType(deviceInstanceId, "CONSTRAINT", "CONSTRAINT_EXECUTE", context);
    }

    @Override
    public List<ObjectNode> abortConstraintCommand(Long deviceInstanceId, String messageId) {
        Map<String, Object> context = new HashMap<>();
        if (messageId != null && !messageId.isBlank()) context.put("messageId", messageId);
        return dispatchSignalByType(deviceInstanceId, "CONSTRAINT", "CONSTRAINT_ABORT", context);
    }

    public List<ObjectNode> dispatchSignal(Long instanceId, String interfaceName, String signalName,
                                           Map<String, Object> executionContext) {
        IntrinsicConstraintPlan plan = requireRuntimePlan(instanceId);
        DeviceInstances instance = plan.instance();
        DeviceModels model = plan.model();
        return dispatchResolvedSignal(instance, model, interfaceName, signalName, executionContext);
    }

    @Override
    public List<ObjectNode> dispatchInputSignal(Long deviceInstanceId, String interfaceName,
                                                 String signalName, Map<String, Object> context) {
        return dispatchSignal(deviceInstanceId, interfaceName, signalName, context);
    }

    private List<ObjectNode> dispatchResolvedSignal(DeviceInstances instance, DeviceModels model,
                                                     String interfaceName, String signalName,
                                                     Map<String, Object> executionContext) {
        Long instanceId = instance.getId();
        DeviceStateMachineRuntime runtime = deviceRuntimes.computeIfAbsent(
                instanceId, DeviceStateMachineRuntime::new);
        runtime.lock().lock();
        try {
            return dispatchSignalLocked(runtime, instance, model, interfaceName, signalName, executionContext);
        } finally {
            runtime.lock().unlock();
        }
    }

    private List<ObjectNode> dispatchSignalLocked(DeviceStateMachineRuntime runtime,
                                                  DeviceInstances instance, DeviceModels model,
                                                  String interfaceName, String signalName,
                                                  Map<String, Object> executionContext) {
        Long instanceId = instance.getId();
        requireInputInterface(model.getStateMachineInterfaces(), interfaceName, signalName);
        DeviceTwinStates twinState;
        if (stateObservations == null) {
            twinState = deviceTwinStateService.getByInstanceId(instanceId);
            if (twinState == null) twinState = createDefaultTwinState(instanceId, model);
        } else {
            DeviceTwinSnapshot attributeSnapshot = deviceTwinSnapshots == null
                    ? null : deviceTwinSnapshots.snapshot(instanceId);
            twinState = inMemoryTwinState(new IntrinsicConstraintPlan(
                    instance, model, List.of(), Map.of()),
                    attributeSnapshot == null ? null : attributeSnapshot.attributes());
        }
        JsonNode currentOpState = currentOperationState(twinState, model);
        Map<String, Object> context = new HashMap<>(executionContext == null ? Map.of() : executionContext);
        if (SystemExecutionContract.isCommandStartSignal(signalName)) {
            if (SystemExecutionContract.findStateMachineSystemTransition(
                    "CMD", initialState(model.getCmdState(), "CMD"), interfaceName, signalName).isEmpty()) return List.of();
            return startExecution(runtime, instance, model, twinState, interfaceName, signalName, context);
        }
        if (SystemExecutionContract.isCommandAbortSignal(signalName)) {
            return startAttachedAbort(runtime, instance, model, twinState, interfaceName, signalName, context);
        }
        if (SystemExecutionContract.isCommandResetSignal(signalName)) {
            return handleManualReset(runtime, instance, model, twinState, interfaceName, signalName, context);
        }
        if (isAdapterInputInterface(model, interfaceName)) {
            return handleAdapterEvent(runtime, instance, model, twinState, currentOpState,
                    interfaceName, signalName, context);
        }
        return handleOperationOnlyEvent(instance, model, twinState, currentOpState, interfaceName, signalName, context);
    }

    private List<ObjectNode> handleManualReset(DeviceStateMachineRuntime runtime, DeviceInstances instance,
                                               DeviceModels model, DeviceTwinStates twinState, String interfaceName,
                                               String signalName, Map<String, Object> context) {
        runtime.normalExecution(null);
        runtime.terminationExecution(null);
        List<ObjectNode> emitted = new ArrayList<>();
        resetTerminalCommandState(instance, model, twinState, interfaceName, signalName, emitted);
        log.info("设备指令周期已通过人工复位信号恢复: instanceId={}, signal={}", instance.getId(), signalName);
        return List.copyOf(emitted);
    }

    private List<ObjectNode> startExecution(DeviceStateMachineRuntime runtime, DeviceInstances instance,
                                            DeviceModels model, DeviceTwinStates twinState, String interfaceName,
                                            String signalName, Map<String, Object> original) {
        CapabilityCommand capability = prepareCapability(instance, model, signalName, original);
        recoverNormalExecution(runtime, instance, model, twinState);
        if (capability.isAbort()) {
            if (runtime.terminationExecution() != null) return List.of();
            return beginExecution(runtime, instance, model, twinState, interfaceName, signalName, capability,
                    DeviceStateMachineRuntime.ExecutionRole.TERMINATION, "CMD_START", affectedNormal(runtime, capability));
        }
        if (runtime.terminationExecution() != null || runtime.normalExecution() != null
                || !initialState(model.getCmdState(), "CMD").equals(currentCommandState(twinState, model))) return List.of();
        return beginExecution(runtime, instance, model, twinState, interfaceName, signalName, capability,
                DeviceStateMachineRuntime.ExecutionRole.NORMAL, "CMD_START", Set.of());
    }

    private List<ObjectNode> startAttachedAbort(DeviceStateMachineRuntime runtime, DeviceInstances instance,
                                                DeviceModels model, DeviceTwinStates twinState, String interfaceName,
                                                String signalName, Map<String, Object> original) {
        DeviceStateMachineRuntime.CommandExecution normal = recoverNormalExecution(runtime, instance, model, twinState);
        if (normal == null || !isAbortable(normal.state()) || normal.attachedAbortExecution() != null) return List.of();
        String explicit = text(original.get("messageId"));
        if (!explicit.isBlank() && !explicit.equals(normal.messageId())) {
            throw new IllegalArgumentException("终止目标messageId与当前普通命令不一致");
        }
        CapabilityCommand source = resolveCapability(model, normal.capabilityName());
        CapabilityCommand abort = source != null && !source.abortCapabilityName().isBlank() ? resolveCapability(model, source.abortCapabilityName()) : null;
        if (abort == null) {
            throw new IllegalStateException("设备能力未在物模型中配置关联的终止能力，无法执行硬件停机: " + normal.capabilityName());
        }
        if (!abort.isAbort()) {
            throw new IllegalStateException("终止能力必须声明isAbort=true: " + abort.capabilityName());
        }
        if (!abort.scope().contains(normal.capabilityName())) {
            throw new IllegalStateException("终止能力scope必须包含其关联普通能力: " + normal.capabilityName());
        }
        abort = prepareAttachedAbortCapability(abort, original);
        Set<String> affected = Set.of(normal.messageId());
        return beginExecution(runtime, instance, model, twinState, interfaceName, signalName, abort,
                DeviceStateMachineRuntime.ExecutionRole.ATTACHED_ABORT, "CMD_ABORT", affected);
    }

    private List<ObjectNode> beginExecution(DeviceStateMachineRuntime runtime, DeviceInstances instance,
                                            DeviceModels model, DeviceTwinStates twinState, String interfaceName,
                                            String signalName, CapabilityCommand capability,
                                            DeviceStateMachineRuntime.ExecutionRole role, String outboundSignal,
                                            Set<String> affectedMessageIds) {
        Map<String, Object> context = new HashMap<>(capability.context());
        context.put("stateName", "SENT");
        String executionMessageId = text(context.get("messageId"));
        if (executionMessageId.isBlank() || "null".equals(executionMessageId)) {
            throw new IllegalStateException("终止或执行命令必须具有非空messageId");
        }
        if (role == DeviceStateMachineRuntime.ExecutionRole.ATTACHED_ABORT
                && executionMessageId.equals(runtime.normalExecution().messageId())) {
            throw new IllegalStateException("附属终止命令必须使用不同于普通命令的新messageId");
        }
        DeviceStateMachineRuntime.CommandExecution execution = new DeviceStateMachineRuntime.CommandExecution(
                executionMessageId, capability.capabilityName(), capability.adapterCommandName(),
                "SENT", context, role, affectedMessageIds);
        if (role == DeviceStateMachineRuntime.ExecutionRole.NORMAL) runtime.normalExecution(execution);
        else if (role == DeviceStateMachineRuntime.ExecutionRole.TERMINATION) runtime.terminationExecution(execution);
        else runtime.normalExecution().attachedAbortExecution(execution);
        List<ObjectNode> emitted = new ArrayList<>();
        try {
            executeExecutionActions(instance, model, twinState, interfaceName, signalName, context,
                    List.of(sendAction("Interface_adapter_out", outboundSignal)), emitted);
        } catch (RuntimeException publishFailure) {
            runtime.removeExecution(execution);
            throw publishFailure;
        }
        if (role == DeviceStateMachineRuntime.ExecutionRole.NORMAL) {
            twinState.setCurrentCmdState("SENT");
            twinState.setUpdateTime(OffsetDateTime.now());
        }
        if (role == DeviceStateMachineRuntime.ExecutionRole.ATTACHED_ABORT
                && !affectedMessageIds.isEmpty()) {
            DeviceStateMachineRuntime.CommandExecution normal = runtime.normalExecution();
            if (normal != null && affectedMessageIds.contains(normal.messageId())) {
                transitionNormalState(instance, model, twinState, interfaceName, signalName, normal, "ABORTING", emitted);
            }
        }
        if (role == DeviceStateMachineRuntime.ExecutionRole.NORMAL) {
            deviceTwinStateService.patchRuntimeState(twinState, true, List.of());
        }
        executeExecutionActions(instance, model, twinState, interfaceName, signalName, context,
                findOnEntryActions(new StateMachineModels.Definition(model), "CMD", "SENT"), emitted);
        return List.copyOf(emitted);
    }
    private List<ObjectNode> handleAdapterEvent(DeviceStateMachineRuntime runtime, DeviceInstances instance,
                                                DeviceModels model, DeviceTwinStates twinState, JsonNode currentOpState,
                                                String interfaceName, String signalName, Map<String, Object> context) {
        StateMachineModels.Definition definition = new StateMachineModels.Definition(model);
        boolean commandEvent = isCommandLifecycleAdapterEvent(model, interfaceName, signalName);
        String messageId = text(context.get("messageId"));
        if (commandEvent && messageId.isBlank()) {
            throw new IllegalArgumentException("Adapter指令生命周期事件必须携带messageId: " + signalName);
        }
        DeviceStateMachineRuntime.CommandExecution execution = commandEvent ? runtime.findExecution(messageId) : null;
        if (commandEvent && execution == null) execution = recoverNormalExecution(runtime, instance, model, twinState, messageId);
        if (commandEvent && execution == null) {
            log.info("忽略未关联或已过期的Adapter命令事件, instanceId={}, signal={}, messageId={}", instance.getId(), signalName, messageId);
            return List.of();
        }
        TransitionResult result = execution == null
                ? operationOnlyTransition(definition, currentOpState, interfaceName, signalName)
                : computeNextState(definition, execution.state(), currentOpState, interfaceName, signalName, context);
        if (!result.changed()) return List.of();
        if (result.nextOpState() != null) {
            twinState.setCurrentOpState(result.nextOpState());
            twinState.setUpdateTime(OffsetDateTime.now());
        }
        List<ObjectNode> emitted = new ArrayList<>();
        if (execution != null && result.nextCmdState() != null) {
            transitionExecutionState(runtime, instance, model, twinState, interfaceName, signalName,
                    execution, result.nextCmdState(), result.actions(), result.changedOpRegions(), emitted);
        } else {
            publishOperationActions(instance, model, twinState, interfaceName, signalName, context,
                    result.actions(), result.changedOpRegions(), emitted);
        }
        return List.copyOf(emitted);
    }

    private List<ObjectNode> handleOperationOnlyEvent(DeviceInstances instance, DeviceModels model, DeviceTwinStates twinState,
                                                      JsonNode currentOpState, String interfaceName, String signalName,
                                                      Map<String, Object> context) {
        TransitionResult result = operationOnlyTransition(new StateMachineModels.Definition(model), currentOpState, interfaceName, signalName);
        if (!result.changed()) return List.of();
        twinState.setCurrentOpState(result.nextOpState());
        twinState.setUpdateTime(OffsetDateTime.now());
        List<ObjectNode> emitted = new ArrayList<>();
        publishOperationActions(instance, model, twinState, interfaceName, signalName, context,
                result.actions(), result.changedOpRegions(), emitted);
        return List.copyOf(emitted);
    }

    private void transitionExecutionState(DeviceStateMachineRuntime runtime, DeviceInstances instance, DeviceModels model,
                                          DeviceTwinStates twinState, String interfaceName, String signalName,
                                          DeviceStateMachineRuntime.CommandExecution execution, String nextState,
                                          List<StateMachineModels.ActionDefinition> transitionActions,
                                          List<String> changedOpRegions, List<ObjectNode> emitted) {
        execution.state(nextState);
        Map<String, Object> context = executionContext(execution, nextState);
        if (execution.role() == DeviceStateMachineRuntime.ExecutionRole.NORMAL) {
            twinState.setCurrentCmdState(nextState);
            twinState.setUpdateTime(OffsetDateTime.now());
        }
        deviceTwinStateService.patchRuntimeState(twinState,
                execution.role() == DeviceStateMachineRuntime.ExecutionRole.NORMAL, changedOpRegions);
        executeExecutionActions(instance, model, twinState, interfaceName, signalName, context,
                transitionActions, emitted);
        if (!SystemExecutionContract.terminalCommandStateNames().contains(nextState)) return;
        if (execution.role() == DeviceStateMachineRuntime.ExecutionRole.NORMAL) {
            runtime.removeExecution(execution);
            resetTerminalCommandState(instance, model, twinState, interfaceName, signalName, emitted);
            return;
        }
        boolean completed = "COMPLETED".equals(nextState);
        runtime.removeExecution(execution);
        if (completed) completeAbortTargets(runtime, instance, model, twinState, interfaceName, signalName,
                execution.affectedMessageIds(), emitted);
    }

    private void completeAbortTargets(DeviceStateMachineRuntime runtime, DeviceInstances instance, DeviceModels model,
                                      DeviceTwinStates twinState, String interfaceName, String signalName,
                                      Set<String> affectedMessageIds, List<ObjectNode> emitted) {
        DeviceStateMachineRuntime.CommandExecution normal = runtime.normalExecution();
        if (normal == null || !affectedMessageIds.contains(normal.messageId())
                || SystemExecutionContract.terminalCommandStateNames().contains(normal.state())) return;
        transitionNormalState(instance, model, twinState, interfaceName, signalName, normal, "ABORTED", emitted);
        runtime.removeExecution(normal);
        resetTerminalCommandState(instance, model, twinState, interfaceName, signalName, emitted);
    }

    private void transitionNormalState(DeviceInstances instance, DeviceModels model, DeviceTwinStates twinState,
                                       String interfaceName, String signalName,
                                       DeviceStateMachineRuntime.CommandExecution normal, String nextState,
                                       List<ObjectNode> emitted) {
        normal.state(nextState);
        twinState.setCurrentCmdState(nextState);
        twinState.setUpdateTime(OffsetDateTime.now());
        deviceTwinStateService.patchRuntimeState(twinState, true, List.of());
        executeExecutionActions(instance, model, twinState, interfaceName, signalName,
                executionContext(normal, nextState),
                findOnEntryActions(new StateMachineModels.Definition(model), "CMD", nextState), emitted);
    }

    private void publishOperationActions(DeviceInstances instance, DeviceModels model, DeviceTwinStates twinState,
                                         String interfaceName, String signalName, Map<String, Object> context,
                                         List<StateMachineModels.ActionDefinition> transitionActions,
                                         List<String> changedRegions, List<ObjectNode> emitted) {
        deviceTwinStateService.patchRuntimeState(twinState, false, changedRegions);
        executeExecutionActions(instance, model, twinState, interfaceName, signalName, context,
                transitionActions, emitted);
    }

    private void executeExecutionActions(DeviceInstances instance, DeviceModels model, DeviceTwinStates twinState,
                                         String interfaceName, String signalName, Map<String, Object> context,
                                         List<StateMachineModels.ActionDefinition> actions, List<ObjectNode> emitted) {
        StateMachineModels.EventContext eventContext = new StateMachineModels.EventContext(
                instance, model, twinState, interfaceName, signalName, Map.copyOf(context));
        List<StateMachineModels.ActionDefinition> adapterActions = actions.stream().filter(action -> isAdapterOutputAction(model, action)).toList();
        List<StateMachineModels.ActionDefinition> internalActions = actions.stream().filter(action -> !isAdapterOutputAction(model, action)).toList();
        executeAndPublish(instance.getId(), adapterActions, eventContext, emitted);
        executeAndPublish(instance.getId(), internalActions, eventContext, emitted);
    }

    private Map<String, Object> executionContext(DeviceStateMachineRuntime.CommandExecution execution, String stateName) {
        Map<String, Object> result = new HashMap<>(execution.context());
        result.put("stateName", stateName);
        result.put("messageId", execution.messageId());
        result.put("capabilityName", execution.capabilityName());
        result.put("commandName", execution.adapterCommandName());
        return result;
    }

    private Set<String> affectedNormal(DeviceStateMachineRuntime runtime, CapabilityCommand capability) {
        DeviceStateMachineRuntime.CommandExecution normal = runtime.normalExecution();
        return normal != null && capability.scope().contains(normal.capabilityName()) ? Set.of(normal.messageId()) : Set.of();
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
        twinState.setCurrentCmdState(initialState);
        twinState.setUpdateTime(OffsetDateTime.now());
        Map<String, Object> resetContext = Map.of();
        List<StateMachineModels.ActionDefinition> resetActions = findOnEntryActions(
                new StateMachineModels.Definition(model), "CMD", initialState);
        deviceTwinStateService.patchRuntimeState(twinState, true, List.of());
        executeExecutionActions(instance, model, twinState, interfaceName, signalName, resetContext,
                resetActions, emittedSignals);
    }

    private void executeAndPublish(Long instanceId, List<StateMachineModels.ActionDefinition> actions,
                                   StateMachineModels.EventContext context, List<ObjectNode> emittedSignals) {
        List<PendingInterfaceSignal> pendingSignals = new ArrayList<>();
        for (StateMachineModels.ActionDefinition action : actions) {
            StateMachineActionExecutor executor = actionRegistry.required(action.actionName());
            ObjectNode emitted = executor.execute(action, context);
            if (emitted == null) {
                continue;
            }
            emittedSignals.add(emitted);
            String outputInterface = action.payload().path("interfaceName").asText("");
            String interfaceType = resolveOutputInterfaceType(context.model().getStateMachineInterfaces(), outputInterface);
            pendingSignals.add(new PendingInterfaceSignal(outputInterface, interfaceType, emitted.deepCopy()));
        }
        publishInterfaceSignalsAfterCommit(instanceId, context.executionContext(), pendingSignals);
    }

    private void publishInterfaceSignalsAfterCommit(Long instanceId, Map<String, Object> executionContext,
                                                    List<PendingInterfaceSignal> pendingSignals) {
        if (pendingSignals.isEmpty()) return;
        Runnable publication = () -> {
            List<StateMachineInterfaceOutputDispatcher.OutputRequest> stateOutputs = new ArrayList<>();
            for (PendingInterfaceSignal pending : pendingSignals) {
                String signalName = pending.signal().path("signalName").asText("");
                if (SystemExecutionContract.stateOutputInterfaceName().equals(pending.interfaceName())
                        && ("CMD_STATE".equals(signalName) || "OP_STATE".equals(signalName))) {
                    stateOutputs.add(new StateMachineInterfaceOutputDispatcher.OutputRequest(
                            pending.interfaceName(), pending.interfaceType(), pending.signal(), executionContext));
                    continue;
                }
                publishInterfaceSignal(instanceId, pending.interfaceName(), pending.interfaceType(),
                        pending.signal(), executionContext);
            }
            stateOutputDispatcher.submitBatch(instanceId, stateOutputs,
                    request -> publishInterfaceSignal(instanceId, request.interfaceName(), request.interfaceType(),
                            request.signal(), request.executionContext()));
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publication.run();
                }
            });
            return;
        }
        publication.run();
    }

    private void publishInterfaceSignal(Long instanceId, String interfaceName, String interfaceType,
                                        ObjectNode signal, Map<String, Object> executionContext) {
        cacheSignal(instanceId, interfaceName, signal);
        eventPublisher.publishEvent(new StateMachineInterfaceSignalEvent(
                instanceId, interfaceName, interfaceType, signal.deepCopy(), executionContext));
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
                    || !firstState(currentOpState.path(regionName)).equals(transition.fromState())) continue;
            nextOpState.set(regionName, JsonNodeSupport.arrayNode().add(transition.toState()));
            changedOpRegions.add(regionName);
            actions.addAll(withOperationRegion(transition.actions(), regionName));
            actions.addAll(findOpOnEntryActions(definition, regionName, transition.toState()));
        }
        if (nextCmdState == null && changedOpRegions.isEmpty()) return TransitionResult.none();
        return new TransitionResult(nextCmdState, changedOpRegions.isEmpty() ? null : nextOpState,
                List.copyOf(actions), List.copyOf(changedOpRegions));
    }

    private TransitionResult operationOnlyTransition(StateMachineModels.Definition definition, JsonNode currentOpState,
                                                     String interfaceName, String signalName) {
        ObjectNode nextOpState = currentOpState != null && currentOpState.isObject()
                ? currentOpState.deepCopy() : JsonNodeSupport.objectNode();
        List<StateMachineModels.ActionDefinition> actions = new ArrayList<>();
        LinkedHashSet<String> changedRegions = new LinkedHashSet<>();
        for (StateMachineModels.TransitionRule transition : definition.getTransitions()) {
            if (!"OP".equals(transition.stateSpace()) || !interfaceName.equals(transition.triggerInterface())
                    || !signalName.equals(transition.triggerSignal())) continue;
            String regionName = transition.regionName();
            if (regionName.isBlank() || changedRegions.contains(regionName)
                    || !firstState(currentOpState.path(regionName)).equals(transition.fromState())) continue;
            nextOpState.set(regionName, JsonNodeSupport.arrayNode().add(transition.toState()));
            changedRegions.add(regionName);
            actions.addAll(withOperationRegion(transition.actions(), regionName));
            actions.addAll(findOpOnEntryActions(definition, regionName, transition.toState()));
        }
        return changedRegions.isEmpty() ? TransitionResult.none()
                : new TransitionResult(null, nextOpState, List.copyOf(actions), List.copyOf(changedRegions));
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

    private CapabilityCommand prepareCapability(DeviceInstances instance, DeviceModels model,
                                                String signalName, Map<String, Object> original) {
        if ("CONSTRAINT_EXECUTE".equals(signalName)) {
            Object targetInstanceId = original.get("deviceInstanceId");
            if (targetInstanceId != null && !String.valueOf(instance.getId()).equals(String.valueOf(targetInstanceId))) {
                throw new IllegalArgumentException("CONSTRAINT_EXECUTE目标设备与状态机实例不一致");
            }
        }
        String capabilityName = text(original.get("capabilityName"));
        if (capabilityName.isBlank()) capabilityName = text(original.get("commandName"));
        if (capabilityName.isBlank()) throw new IllegalArgumentException(signalName + "缺少capabilityName");
        CapabilityCommand capability = resolveCapability(model, capabilityName);
        return capability.withContext(commandContext(capability, original, textOrGenerated(original.get("messageId"))));
    }

    private CapabilityCommand prepareAttachedAbortCapability(CapabilityCommand capability,
                                                              Map<String, Object> original) {
        return capability.withContext(commandContext(capability, original, UUID.randomUUID().toString()));
    }

    private Map<String, Object> commandContext(CapabilityCommand capability, Map<String, Object> original, String messageId) {
        Map<String, Object> context = new HashMap<>();
        context.put("capabilityName", capability.capabilityName());
        context.put("commandName", capability.adapterCommandName());
        context.put("parameters", parametersOrEmpty(original));
        context.put("messageId", messageId);
        protocolDictionaryService.validateSignalExecutionContext("CMD_START", context);
        return Map.copyOf(context);
    }

    private CapabilityCommand resolveCapability(DeviceModels model, String capabilityName) {
        JsonNode capabilities = model.getCapabilities();
        if (capabilities != null && capabilities.isArray()) {
            for (JsonNode capability : capabilities) {
                if (!capabilityName.equals(capability.path("capabilityName").asText())) continue;
                String adapterCommandName = capability.path("adapterCommandName").asText("");
                if (adapterCommandName.isBlank()) throw new IllegalStateException("能力缺少adapterCommandName: " + capabilityName);
                List<String> scope = new ArrayList<>();
                if (capability.path("scope").isArray()) {
                    for (JsonNode item : capability.path("scope")) {
                        if (item.isTextual() && !item.asText().isBlank()) scope.add(item.asText());
                    }
                }
                return new CapabilityCommand(capabilityName, adapterCommandName,
                        capability.path("abortCapabilityName").asText(""), capability.path("isAbort").asBoolean(false),
                        List.copyOf(scope), Map.of());
            }
        }
        throw new IllegalArgumentException("设备模型未声明能力: " + capabilityName);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parametersOrEmpty(Map<String, Object> context) {
        Object parameters = context.get("parameters");
        if (parameters == null) return Map.of();
        if (parameters instanceof Map<?, ?> values) return Map.copyOf((Map<String, Object>) values);
        if (parameters instanceof JsonNode node && node.isObject()) return JsonNodeSupport.MAPPER.convertValue(node, Map.class);
        throw new IllegalArgumentException("能力参数parameters必须是对象");
    }

    private boolean isAbortable(String state) {
        return "SENT".equals(state) || "RUNNING".equals(state);
    }

    private DeviceStateMachineRuntime.CommandExecution recoverNormalExecution(DeviceStateMachineRuntime runtime,
                                                                                DeviceInstances instance,
                                                                                DeviceModels model,
                                                                                DeviceTwinStates twinState) {
        return recoverNormalExecution(runtime, instance, model, twinState, null);
    }

    private DeviceStateMachineRuntime.CommandExecution recoverNormalExecution(DeviceStateMachineRuntime runtime,
                                                                                DeviceInstances instance,
                                                                                DeviceModels model,
                                                                                DeviceTwinStates twinState,
                                                                                String expectedMessageId) {
        if (runtime.normalExecution() != null) {
            return expectedMessageId == null || expectedMessageId.isBlank()
                    || expectedMessageId.equals(runtime.normalExecution().messageId()) ? runtime.normalExecution() : null;
        }
        if (taskStepMapper == null || !isAbortable(currentCommandState(twinState, model))) return null;
        List<JsonNode> candidates = taskStepMapper.selectList(Wrappers.<TaskStep>lambdaQuery()
                        .in(TaskStep::getNodeStatus, List.of("RUNNING", "TERMINATING")))
                .stream().filter(step -> step.getInterfaceInSnapshot() != null && step.getInterfaceInSnapshot().isObject())
                .map(TaskStep::getInterfaceInSnapshot)
                .filter(snapshot -> snapshot.path("deviceInstanceId").canConvertToLong()
                        && instance.getId().equals(snapshot.path("deviceInstanceId").asLong()))
                .filter(snapshot -> expectedMessageId == null || expectedMessageId.isBlank()
                        || expectedMessageId.equals(snapshot.path("messageId").asText()))
                .toList();
        if (candidates.size() != 1) return null;
        JsonNode snapshot = candidates.getFirst();
        String messageId = snapshot.path("messageId").asText("");
        String capabilityName = snapshot.path("capabilityName").asText("");
        if (messageId.isBlank() || capabilityName.isBlank()) return null;
        CapabilityCommand capability;
        try {
            capability = resolveCapability(model, capabilityName);
        } catch (RuntimeException ignored) {
            return null;
        }
        Map<String, Object> source = new HashMap<>();
        source.put("parameters", snapshot.path("parameters"));
        DeviceStateMachineRuntime.CommandExecution restored = new DeviceStateMachineRuntime.CommandExecution(
                messageId, capability.capabilityName(), capability.adapterCommandName(), currentCommandState(twinState, model),
                commandContext(capability, source, messageId), DeviceStateMachineRuntime.ExecutionRole.NORMAL, Set.of());
        runtime.normalExecution(restored);
        log.info("从TaskStep快照恢复设备普通命令运行时, instanceId={}, messageId={}", instance.getId(), messageId);
        return restored;
    }

    private boolean isAdapterInputInterface(DeviceModels model, String interfaceName) {
        JsonNode interfaces = model.getStateMachineInterfaces();
        if (interfaces == null || !interfaces.isArray()) return false;
        for (JsonNode item : interfaces) {
            if (interfaceName.equals(item.path("name").asText()) && "IN".equals(item.path("direction").asText())) {
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
    private boolean isAdapterOutputAction(DeviceModels model, StateMachineModels.ActionDefinition action) {
        if (!"SEND".equals(action.actionName())) return false;
        String interfaceName = action.payload().path("interfaceName").asText("");
        return "ADAPTER".equals(resolveOutputInterfaceType(model.getStateMachineInterfaces(), interfaceName));
    }

    private String resolveInputInterface(DeviceModels model, String interfaceType, String signalName) {
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

    private IntrinsicConstraintPlan requireRuntimePlan(Long instanceId) {
        if (intrinsicConstraintPlans != null) {
            return intrinsicConstraintPlans.require(instanceId);
        }
        DeviceInstances instance = requireInstance(instanceId);
        DeviceModels model = requireModel(instance);
        return new IntrinsicConstraintPlan(instance, model, List.of(), Map.of());
    }

    private String currentCommandState(DeviceTwinStates twinState, DeviceModels model) {
        String current = twinState.getCurrentCmdState();
        return current == null || current.isBlank() ? initialState(model.getCmdState(), "CMD") : current;
    }

    private JsonNode currentOperationState(DeviceTwinStates twinState, DeviceModels model) {
        JsonNode value = twinState.getCurrentOpState() == null ? initialOperationState(model.getOpState()) : twinState.getCurrentOpState();
        return normalizeOperationState(value);
    }

    private DeviceTwinStates inMemoryTwinState(IntrinsicConstraintPlan plan, JsonNode currentAttributes) {
        DeviceTwinStates state = new DeviceTwinStates();
        state.setInstanceId(plan.deviceInstanceId());
        state.setCurrentCmdState(inMemoryCommandState(plan));
        ObjectNode operationState = (ObjectNode) initialOperationState(plan.model().getOpState());
        JsonNode regions = plan.model().getOpState() == null
                ? null : plan.model().getOpState().path("regions");
        if (regions != null && regions.isArray()) {
            for (JsonNode region : regions) {
                String regionName = region.path("regionName").asText("");
                if (regionName.isBlank()) continue;
                ObservationSnapshot observed = readStateObservation(
                        ObservableObjectType.DEVICE_OPERATION_STATE, plan.deviceInstanceId(), regionName);
                JsonNode value = observed == null ? null : observed.value();
                if (value != null) {
                    if (!value.isArray()) {
                        throw new IllegalStateException("OP状态内存快照必须使用数组格式: " + regionName);
                    }
                    operationState.set(regionName, value);
                }
            }
        }
        state.setCurrentOpState(operationState);
        state.setCurrentAttr(currentAttributes);
        state.setUpdateTime(OffsetDateTime.now());
        return state;
    }

    private String inMemoryCommandState(IntrinsicConstraintPlan plan) {
        ObservationSnapshot observed = readStateObservation(
                ObservableObjectType.DEVICE_COMMAND_LIFECYCLE, plan.deviceInstanceId(), null);
        JsonNode value = observed == null ? null : observed.value();
        return value != null && value.isTextual() && !value.asText().isBlank()
                ? value.asText() : initialState(plan.model().getCmdState(), "CMD");
    }

    private String observedCommandState(Long instanceId) {
        ObservationSnapshot observed = readStateObservation(
                ObservableObjectType.DEVICE_COMMAND_LIFECYCLE, instanceId, null);
        JsonNode value = observed == null ? null : observed.value();
        if (value != null && value.isTextual() && !value.asText().isBlank()) {
            return value.asText();
        }
        if (intrinsicConstraintPlans != null) {
            return initialState(intrinsicConstraintPlans.require(instanceId).model().getCmdState(), "CMD");
        }
        DeviceTwinStates persisted = deviceTwinStateService.getByInstanceId(instanceId);
        return persisted == null || persisted.getCurrentCmdState() == null
                ? "IDLE" : persisted.getCurrentCmdState();
    }

    private LinkedHashSet<String> currentOperationStates(Long instanceId, String regionName) {
        ObservationSnapshot observed = readStateObservation(
                ObservableObjectType.DEVICE_OPERATION_STATE, instanceId, regionName);
        JsonNode value = observed == null ? null : observed.value();
        LinkedHashSet<String> states = new LinkedHashSet<>();
        if (value == null) return states;
        if (!value.isArray()) {
            throw new IllegalStateException("OP状态内存快照必须使用数组格式: " + regionName);
        }
        for (JsonNode state : value) {
            String stateName = state.asText("");
            if (!stateName.isBlank()) states.add(stateName);
        }
        return states;
    }

    private ObservationSnapshot readStateObservation(ObservableObjectType type,
                                                      Long instanceId,
                                                      String regionName) {
        if (stateObservations == null) return null;
        return stateObservations.read(new ObservableKey(type, instanceId, null, null,
                regionName, null, null, null));
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
                    ArrayNode state = initial.putArray(regionName);
                    if (!initialState.isBlank()) state.add(initialState);
                }
            }
        }
        return initial;
    }

    private JsonNode normalizeOperationState(JsonNode value) {
        if (value == null || !value.isObject()) return JsonNodeSupport.objectNode();
        value.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isArray()) {
                throw new IllegalStateException("OP状态必须使用数组格式: " + entry.getKey());
            }
        });
        return value.deepCopy();
    }

    private String firstState(JsonNode value) {
        if (value == null || !value.isArray()) throw new IllegalStateException("OP状态必须使用数组格式");
        return value.size() == 0 ? "" : value.get(0).asText("");
    }

    private Double numericValue(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (value.isNumber()) return value.asDouble();
        if (!value.isTextual()) return null;
        try {
            return Double.parseDouble(value.asText());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean violates(double actual, String operator, double boundary) {
        return switch (operator) {
            case ">" -> actual > boundary;
            case "<" -> actual < boundary;
            case ">=" -> actual >= boundary;
            case "<=" -> actual <= boundary;
            case "=" -> Double.compare(actual, boundary) == 0;
            case "!=" -> Double.compare(actual, boundary) != 0;
            default -> false;
        };
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
                        return withOperationRegion(result, regionName);
                    }
                }
            }
        }
        return result;
    }

    private List<StateMachineModels.ActionDefinition> withOperationRegion(
            List<StateMachineModels.ActionDefinition> actions, String regionName) {
        List<StateMachineModels.ActionDefinition> result = new ArrayList<>();
        for (StateMachineModels.ActionDefinition action : actions) {
            if (!"SEND".equals(action.actionName())
                    || !"OP_STATE".equals(action.payload().path("signalName").asText())
                    || !action.payload().path("regionName").asText("").isBlank()) {
                result.add(action);
                continue;
            }
            ObjectNode payload = action.payload().deepCopy();
            payload.put("regionName", regionName);
            result.add(new StateMachineModels.ActionDefinition(action.actionName(), payload));
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

    private record CapabilityCommand(String capabilityName, String adapterCommandName, String abortCapabilityName,
                                     boolean isAbort, List<String> scope, Map<String, Object> context) {
        CapabilityCommand withContext(Map<String, Object> context) {
            return new CapabilityCommand(capabilityName, adapterCommandName, abortCapabilityName, isAbort, scope, context);
        }
    }

    private record PendingInterfaceSignal(String interfaceName, String interfaceType, ObjectNode signal) {
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
