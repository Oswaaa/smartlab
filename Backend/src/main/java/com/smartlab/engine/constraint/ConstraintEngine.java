package com.smartlab.engine.constraint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.StateMachineEngine;
import com.smartlab.engine.statemachine.StateMachineInterfaceSignalEvent;
import com.smartlab.engine.workflow.WorkflowTaskControlService;
import com.smartlab.global.event.ConstraintAlertEvent;
import com.smartlab.global.event.DeviceTelemetryUpdatedEvent;
import com.smartlab.global.event.TaskLifecycleObservationEvent;
import com.smartlab.global.event.WorkflowNodeObservationEvent;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.constraint.ConstraintRule;
import com.smartlab.management.entity.constraint.ViolationLog;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.management.service.db.constraint.ConstraintRuleService;
import com.smartlab.management.service.db.constraint.ViolationLogService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConstraintEngine {
    private static final Logger log = LoggerFactory.getLogger(ConstraintEngine.class);
    private static final int MAX_HISTORY_SIZE = 4096;

    private final ConstraintRuleService ruleService;
    private final ViolationLogService violationLogService;
    private final WorkflowTaskControlService taskControlService;
    private final StateMachineEngine stateMachineEngine;
    private final DeviceInstancesMapper deviceInstancesMapper;
    private final DeviceModelsMapper deviceModelsMapper;
    private final WorkflowService workflowService;
    private final ConstraintExpressionEvaluator expressionEvaluator;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, ScopeState> scopes = new ConcurrentHashMap<>();
    private final Map<String, Instant> expressionTrueSince = new ConcurrentHashMap<>();
    private final Map<String, Boolean> expressionTriggered = new ConcurrentHashMap<>();

    public ConstraintEngine(ConstraintRuleService ruleService, ViolationLogService violationLogService,
                            WorkflowTaskControlService taskControlService, StateMachineEngine stateMachineEngine,
                            DeviceInstancesMapper deviceInstancesMapper, DeviceModelsMapper deviceModelsMapper,
                            WorkflowService workflowService, ConstraintExpressionEvaluator expressionEvaluator,
                            ApplicationEventPublisher eventPublisher) {
        this.ruleService = ruleService;
        this.violationLogService = violationLogService;
        this.taskControlService = taskControlService;
        this.stateMachineEngine = stateMachineEngine;
        this.deviceInstancesMapper = deviceInstancesMapper;
        this.deviceModelsMapper = deviceModelsMapper;
        this.workflowService = workflowService;
        this.expressionEvaluator = expressionEvaluator;
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    public void observeTelemetry(DeviceTelemetryUpdatedEvent event) {
        for (ConstraintRule rule : ruleService.list(Boolean.TRUE)) {
            ScopeState scope = null;
            for (ObservableBinding binding : observableBindings(rule)) {
                JsonNode source = binding.source();
                if (!"DEVICE_ATTRIBUTE".equals(text(source, "sourceType"))
                        || !matchesDevice(source, event.deviceModelId(), event.deviceInstanceId())) continue;
                JsonNode value = event.attributes().get(text(source, "targetName"));
                if (value == null) continue;
                scope = scope(rule, deviceScope(event.deviceInstanceId()), null, null, event.deviceInstanceId());
                update(scope, binding.variableName(), value, event.occurredAt());
            }
            if (scope != null) evaluateRule(rule, scope);
        }
    }

    @EventListener
    public void observeStateMachine(StateMachineInterfaceSignalEvent event) {
        if (!"STATE".equals(event.interfaceType()) || !"Interface_state_out".equals(event.interfaceName())) return;
        String signalName = text(event.signal(), "signalName");
        JsonNode payload = event.signal().path("payload");
        Long deviceModelId = positiveLong(payload.get("deviceModelId"));
        Long deviceInstanceId = positiveLong(payload.get("deviceInstanceId"));
        if (deviceModelId == null || deviceInstanceId == null) return;
        Instant occurredAt = timestamp(payload.path("timestamp"));
        for (ConstraintRule rule : ruleService.list(Boolean.TRUE)) {
            ScopeState scope = null;
            for (ObservableBinding binding : observableBindings(rule)) {
                JsonNode source = binding.source();
                String sourceType = text(source, "sourceType");
                if (!matchesDevice(source, deviceModelId, deviceInstanceId)) continue;
                if ("CMD_STATE".equals(signalName) && "DEVICE_COMMAND_LIFECYCLE".equals(sourceType)) {
                    scope = scope(rule, deviceScope(deviceInstanceId), null, null, deviceInstanceId);
                    update(scope, binding.variableName(), payload.path("stateName"), occurredAt);
                }
                if ("OP_STATE".equals(signalName) && "DEVICE_OPERATION_STATE".equals(sourceType)
                        && Objects.equals(text(source, "regionName"), text(payload, "regionName"))) {
                    scope = scope(rule, deviceScope(deviceInstanceId), null, null, deviceInstanceId);
                    update(scope, binding.variableName(), payload.path("stateName"), occurredAt);
                }
            }
            if (scope != null) evaluateRule(rule, scope);
        }
    }

    @EventListener
    public void observeWorkflowNode(WorkflowNodeObservationEvent event) {
        for (ConstraintRule rule : ruleService.list(Boolean.TRUE)) {
            ScopeState scope = null;
            for (ObservableBinding binding : observableBindings(rule)) {
                JsonNode source = binding.source();
                String sourceType = text(source, "sourceType");
                if (!("NODE_LIFECYCLE_STATE".equals(sourceType) || "NODE_INTERNAL_VARIABLE".equals(sourceType))) continue;
                if (!Objects.equals(positiveLong(source.get("workflowTemplateId")), event.workflowTemplateId())
                        || !matchesNodeName(source, event.workflowTemplateId(), event.nodeIdRef())) continue;
                JsonNode value = "NODE_LIFECYCLE_STATE".equals(sourceType)
                        ? JsonNodeSupport.MAPPER.getNodeFactory().textNode(event.nodeLifecycleState())
                        : event.variableSpace().get(text(source, "variableName"));
                if (value == null) continue;
                scope = scope(rule, taskScope(event.taskId()), event.taskId(), event.taskStepId(), null);
                update(scope, binding.variableName(), value, event.occurredAt());
            }
            if (scope != null) evaluateRule(rule, scope);
        }
    }

    @EventListener
    public void observeTask(TaskLifecycleObservationEvent event) {
        for (ConstraintRule rule : ruleService.list(Boolean.TRUE)) {
            ScopeState scope = null;
            for (ObservableBinding binding : observableBindings(rule)) {
                JsonNode source = binding.source();
                if (!"TASK_LIFECYCLE_STATE".equals(text(source, "sourceType"))
                        || !Objects.equals(positiveLong(source.get("taskId")), event.taskId())) continue;
                scope = scope(rule, taskScope(event.taskId()), event.taskId(), null, null);
                update(scope, binding.variableName(), JsonNodeSupport.MAPPER.getNodeFactory().textNode(event.taskLifecycleState()), event.occurredAt());
            }
            if (scope != null) evaluateRule(rule, scope);
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void evaluateSustainedConstraints() {
        Map<Long, ConstraintRule> enabledRules = new HashMap<>();
        for (ConstraintRule rule : ruleService.list(Boolean.TRUE)) enabledRules.put(rule.getId(), rule);
        scopes.entrySet().removeIf(entry -> !enabledRules.containsKey(entry.getValue().ruleId));
        for (ScopeState scope : scopes.values()) {
            ConstraintRule rule = enabledRules.get(scope.ruleId);
            if (rule != null) evaluateRule(rule, scope);
        }
    }

    private void evaluateRule(ConstraintRule rule, ScopeState scope) {
        synchronized (scope) {
            Map<String, JsonNode> variables = bindings(rule.getBindings(), scope.values);
            if (variables == null) return;
            String expressionKey = rule.getId() + "::" + scope.scopeKey;
            boolean violated;
            try {
                violated = expressionEvaluator.evaluate(rule.getExpression(), variables, histories(rule.getBindings(), scope.histories), Instant.now());
            } catch (RuntimeException e) {
                log.warn("约束表达式求值失败, ruleId={}: {}", rule.getId(), e.getMessage());
                expressionTrueSince.remove(expressionKey);
                expressionTriggered.remove(expressionKey);
                return;
            }
            if (!violated) {
                expressionTrueSince.remove(expressionKey);
                expressionTriggered.remove(expressionKey);
                return;
            }
            Instant firstTrueAt = expressionTrueSince.computeIfAbsent(expressionKey, ignored -> Instant.now());
            Integer windowSeconds = rule.getWindowSeconds();
            if (windowSeconds != null && Instant.now().isBefore(firstTrueAt.plusSeconds(windowSeconds))) return;
            if (Boolean.TRUE.equals(expressionTriggered.putIfAbsent(expressionKey, Boolean.TRUE))) return;
            executeActions(rule, scope, variables);
        }
    }

    private Map<String, JsonNode> bindings(JsonNode bindings, Map<String, JsonNode> values) {
        if (bindings == null || !bindings.isObject()) return null;
        Map<String, JsonNode> result = new HashMap<>();
        var entries = bindings.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            JsonNode binding = entry.getValue();
            if ("LITERAL".equals(text(binding, "bindingType"))) {
                result.put(entry.getKey(), binding.get("value"));
            } else if ("OBSERVABLE".equals(text(binding, "bindingType"))) {
                JsonNode value = values.get(entry.getKey());
                if (value == null) return null;
                result.put(entry.getKey(), value);
            } else {
                return null;
            }
        }
        return result;
    }

    private Map<String, List<ConstraintExpressionEvaluator.TimedValue>> histories(JsonNode bindings,
                                                                                     Map<String, List<ConstraintExpressionEvaluator.TimedValue>> source) {
        Map<String, List<ConstraintExpressionEvaluator.TimedValue>> result = new HashMap<>();
        if (bindings == null || !bindings.isObject()) return result;
        var entries = bindings.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            if ("OBSERVABLE".equals(text(entry.getValue(), "bindingType"))) {
                result.put(entry.getKey(), source.getOrDefault(entry.getKey(), List.of()));
            }
        }
        return result;
    }

    private void executeActions(ConstraintRule rule, ScopeState scope, Map<String, JsonNode> values) {
        for (JsonNode action : elements(rule.getViolationActions())) {
            String actionTaken;
            try {
                actionTaken = executeAction(action, scope, rule, values);
            } catch (RuntimeException e) {
                actionTaken = "FAILED:" + text(action, "actionType") + ":" + e.getMessage();
                log.warn("约束动作执行失败, ruleId={}: {}", rule.getId(), e.getMessage());
            }
            writeViolationLog(rule, action, scope, values, actionTaken);
        }
    }

    private String executeAction(JsonNode action, ScopeState scope, ConstraintRule rule, Map<String, JsonNode> values) {
        String actionType = text(action, "actionType");
        if ("SYSTEM".equals(actionType)) {
            String systemAction = text(action, "action");
            Long targetTaskId = positiveLong(action.get("targetTaskId"));
            return switch (systemAction) {
                case "ABORT" -> {
                    taskControlService.terminate(requiredTaskId(targetTaskId, systemAction));
                    yield "SYSTEM:ABORT";
                }
                case "PAUSE" -> {
                    taskControlService.pause(requiredTaskId(targetTaskId, systemAction));
                    yield "SYSTEM:PAUSE";
                }
                case "ALERT" -> {
                    eventPublisher.publishEvent(new ConstraintAlertEvent(rule.getId(), rule.getRuleName(),
                            targetTaskId == null ? scope.taskId : targetTaskId, snapshot(values), Instant.now()));
                    yield "SYSTEM:ALERT";
                }
                default -> throw new IllegalArgumentException("未知系统违规动作: " + systemAction);
            };
        }
        if (!"DEVICE_CAPABILITY".equals(actionType)) throw new IllegalArgumentException("未知违规动作类型: " + actionType);
        Long deviceInstanceId = positiveLong(action.get("deviceInstanceId"));
        if (deviceInstanceId == null) throw new IllegalArgumentException("设备能力违规动作缺少deviceInstanceId");
        String capabilityName = text(action, "capabilityName");
        if (isAbortCapability(deviceInstanceId, capabilityName)) {
            stateMachineEngine.dispatchSignalByType(deviceInstanceId, "CONSTRAINT", "CONSTRAINT_ABORT", Map.of());
            return "DEVICE_CAPABILITY:CONSTRAINT_ABORT:" + capabilityName;
        }
        Map<String, Object> context = new HashMap<>();
        context.put("deviceInstanceId", deviceInstanceId);
        context.put("capabilityName", capabilityName);
        context.put("parameters", action.path("parameters").isObject()
                ? JsonNodeSupport.MAPPER.convertValue(action.path("parameters"), Map.class) : Map.of());
        stateMachineEngine.dispatchSignalByType(deviceInstanceId, "CONSTRAINT", "CONSTRAINT_EXECUTE", context);
        return "DEVICE_CAPABILITY:CONSTRAINT_EXECUTE:" + capabilityName;
    }

    private boolean isAbortCapability(Long deviceInstanceId, String capabilityName) {
        DeviceInstances instance = deviceInstancesMapper.selectById(deviceInstanceId);
        if (instance == null) throw new IllegalArgumentException("设备实例不存在: " + deviceInstanceId);
        DeviceModels model = deviceModelsMapper.selectById(instance.getDeviceModelId());
        if (model == null) throw new IllegalArgumentException("设备模型不存在: " + instance.getDeviceModelId());
        for (JsonNode capability : elements(model.getCapabilities())) {
            if (capabilityName.equals(text(capability, "capabilityName"))) return capability.path("isAbort").asBoolean(false);
        }
        throw new IllegalArgumentException("设备模型未声明能力: " + capabilityName);
    }

    private void writeViolationLog(ConstraintRule rule, JsonNode action, ScopeState scope,
                                   Map<String, JsonNode> values, String actionTaken) {
        ViolationLog logEntry = new ViolationLog();
        logEntry.setConstraintRuleId(rule.getId());
        logEntry.setConstraintType("CONSTRAINT_MODEL");
        logEntry.setTaskId(scope.taskId == null ? positiveLong(action.get("targetTaskId")) : scope.taskId);
        logEntry.setTaskStepId(scope.taskStepId);
        logEntry.setDeviceInstanceId(scope.deviceInstanceId == null ? positiveLong(action.get("deviceInstanceId")) : scope.deviceInstanceId);
        logEntry.setObservedVariable(rule.getRuleName());
        ObjectNode expected = JsonNodeSupport.objectNode();
        expected.put("expression", rule.getExpression());
        logEntry.setExpectedCondition(expected);
        logEntry.setActualValue(snapshot(values));
        logEntry.setVariableSnapshot(snapshot(scope.values));
        logEntry.setActionTaken(actionTaken);
        violationLogService.save(logEntry);
    }

    private ScopeState scope(ConstraintRule rule, String scopeKey, Long taskId, Long taskStepId, Long deviceInstanceId) {
        String key = rule.getId() + "::" + scopeKey;
        ScopeState state = scopes.computeIfAbsent(key, ignored -> new ScopeState(rule.getId(), scopeKey));
        if (taskId != null) state.taskId = taskId;
        if (taskStepId != null) state.taskStepId = taskStepId;
        if (deviceInstanceId != null) state.deviceInstanceId = deviceInstanceId;
        return state;
    }

    private void update(ScopeState scope, String variableName, JsonNode value, Instant occurredAt) {
        if (variableName == null || variableName.isBlank() || value == null || value.isNull()) return;
        JsonNode copy = value.deepCopy();
        scope.values.put(variableName, copy);
        List<ConstraintExpressionEvaluator.TimedValue> history = scope.histories.computeIfAbsent(variableName,
                ignored -> new ArrayList<>());
        history.add(new ConstraintExpressionEvaluator.TimedValue(occurredAt == null ? Instant.now() : occurredAt, copy));
        if (history.size() > MAX_HISTORY_SIZE) history.subList(0, history.size() - MAX_HISTORY_SIZE).clear();
    }

    private List<ObservableBinding> observableBindings(ConstraintRule rule) {
        JsonNode bindings = rule.getBindings();
        if (bindings == null || !bindings.isObject()) return List.of();
        List<ObservableBinding> result = new ArrayList<>();
        var entries = bindings.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            JsonNode binding = entry.getValue();
            if ("OBSERVABLE".equals(text(binding, "bindingType")) && binding.path("source").isObject()) {
                result.add(new ObservableBinding(entry.getKey(), binding.path("source")));
            }
        }
        return result;
    }

    private boolean matchesDevice(JsonNode source, Long deviceModelId, Long deviceInstanceId) {
        if (!Objects.equals(positiveLong(source.get("deviceModelId")), deviceModelId)) return false;
        Long expectedInstanceId = positiveLong(source.get("deviceInstanceId"));
        return expectedInstanceId == null || Objects.equals(expectedInstanceId, deviceInstanceId);
    }

    private boolean matchesNodeName(JsonNode source, Long workflowTemplateId, Long nodeIdRef) {
        try {
            Long expectedNodeIdRef = workflowService.compileDefinition(workflowTemplateId)
                    .refsByNodeName().get(text(source, "nodeName"));
            return Objects.equals(expectedNodeIdRef, nodeIdRef);
        } catch (RuntimeException e) {
            log.warn("约束节点引用无法解析, workflowTemplateId={}, nodeName={}: {}", workflowTemplateId,
                    text(source, "nodeName"), e.getMessage());
            return false;
        }
    }

    private Long requiredTaskId(Long targetTaskId, String action) {
        if (targetTaskId == null) throw new IllegalArgumentException(action + "缺少targetTaskId");
        return targetTaskId;
    }

    private ObjectNode snapshot(Map<String, JsonNode> values) {
        ObjectNode result = JsonNodeSupport.objectNode();
        values.forEach((name, value) -> result.set(name, value == null ? null : value.deepCopy()));
        return result;
    }

    private List<JsonNode> elements(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<JsonNode> result = new ArrayList<>();
        node.forEach(result::add);
        return result;
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private Long positiveLong(JsonNode value) {
        if (value == null || value.isNull() || !value.canConvertToLong() || value.asLong() <= 0) return null;
        return value.asLong();
    }

    private Instant timestamp(JsonNode value) {
        return value != null && value.canConvertToLong() ? Instant.ofEpochMilli(value.asLong()) : Instant.now();
    }

    private String deviceScope(Long deviceInstanceId) {
        return "device:" + deviceInstanceId;
    }

    private String taskScope(Long taskId) {
        return "task:" + taskId;
    }

    private record ObservableBinding(String variableName, JsonNode source) {
    }

    private static final class ScopeState {
        private final Long ruleId;
        private final String scopeKey;
        private final Map<String, JsonNode> values = new HashMap<>();
        private final Map<String, List<ConstraintExpressionEvaluator.TimedValue>> histories = new HashMap<>();
        private Long taskId;
        private Long taskStepId;
        private Long deviceInstanceId;

        private ScopeState(Long ruleId, String scopeKey) {
            this.ruleId = ruleId;
            this.scopeKey = scopeKey;
        }
    }
}
