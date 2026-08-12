package com.smartlab.engine.constraint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.observation.ObservationSample;
import com.smartlab.engine.statemachine.StateMachineCommandPort;
import com.smartlab.engine.observation.ObservableKey;
import com.smartlab.engine.observation.ObservationSnapshot;
import com.smartlab.engine.observation.ObservationStatus;
import com.smartlab.engine.workflow.WorkflowTaskControlService;
import com.smartlab.global.event.ConstraintAlertEvent;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.constraint.ConstraintRule;
import com.smartlab.management.entity.constraint.ViolationLog;
import com.smartlab.management.service.db.constraint.ViolationLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConstraintEngine {
    private static final Logger log = LoggerFactory.getLogger(ConstraintEngine.class);
    private static final int MAX_HISTORY_SIZE = 4096;
    private static final long MAX_HISTORY_SECONDS = 1800;

    private final ViolationLogService violationLogService;
    private final WorkflowTaskControlService taskControlService;
    private final StateMachineCommandPort stateMachineCommandPort;
    private final ConstraintExpressionEvaluator expressionEvaluator;
    private final ApplicationEventPublisher eventPublisher;
    private final ConstraintScopeSnapshotReader scopeSnapshotReader;
    private final Map<String, ScopeState> scopes = new ConcurrentHashMap<>();
    private final Map<String, Instant> expressionTrueSince = new ConcurrentHashMap<>();
    private final Map<String, Boolean> expressionTriggered = new ConcurrentHashMap<>();

    public ConstraintEngine(ViolationLogService violationLogService,
                            WorkflowTaskControlService taskControlService, StateMachineCommandPort stateMachineCommandPort,
                            ConstraintExpressionEvaluator expressionEvaluator,
                            ApplicationEventPublisher eventPublisher,
                            ConstraintScopeSnapshotReader scopeSnapshotReader) {
        this.violationLogService = violationLogService;
        this.taskControlService = taskControlService;
        this.stateMachineCommandPort = stateMachineCommandPort;
        this.expressionEvaluator = expressionEvaluator;
        this.eventPublisher = eventPublisher;
        this.scopeSnapshotReader = scopeSnapshotReader;
    }

    public void evaluate(RuntimeConstraint constraint,
                         Map<ObservableKey, ObservationSnapshot> observations) {
        evaluate(constraint, observations, Map.of());
    }

    public void evaluate(RuntimeConstraint constraint,
                         Map<ObservableKey, ObservationSnapshot> observations,
                         Map<ObservableKey, List<ObservationSample>> histories) {
        ConstraintRule rule = constraint.rule();
        String versionedScope = constraint.key().scopeKey() + "@" + constraint.key().version();
        ScopeState scope = scope(rule, versionedScope, constraint.taskId(),
                constraint.taskStepId(), constraint.deviceInstanceId());
        synchronized (scope) {
            for (Map.Entry<String, ObservableKey> entry : constraint.observableBindings().entrySet()) {
                ObservationSnapshot observation = observations.get(entry.getValue());
                if (observation == null || observation.status() != ObservationStatus.VALID
                        || observation.value() == null || observation.value().isNull()) return;
                Long lastRevision = scope.revisions.get(entry.getKey());
                if (!Long.valueOf(observation.revision()).equals(lastRevision)) {
                    List<ObservationSample> samples = histories == null ? null : histories.get(entry.getValue());
                    if (samples == null || samples.isEmpty()) {
                        update(scope, entry.getKey(), observation.value(), observation.observedAt());
                    } else {
                        scope.values.put(entry.getKey(), observation.value());
                        scope.histories.put(entry.getKey(), toTimedValues(samples));
                    }
                    scope.revisions.put(entry.getKey(), observation.revision());
                }
            }
        }
        evaluateRule(constraint, rule, scope);
    }

    public void retainRuntimeKeys(java.util.Set<RuntimeConstraintKey> activeKeys) {
        java.util.Set<String> valid = activeKeys.stream()
                .map(key -> key.stableRuleKey() + "::" + key.scopeKey() + "@" + key.version())
                .collect(java.util.stream.Collectors.toSet());
        scopes.entrySet().removeIf(entry -> !valid.contains(entry.getKey()));
        expressionTrueSince.keySet().removeIf(key -> valid.stream().noneMatch(key::startsWith));
        expressionTriggered.keySet().removeIf(key -> valid.stream().noneMatch(key::startsWith));
    }

    private void evaluateRule(RuntimeConstraint constraint, ConstraintRule rule, ScopeState scope) {
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
            executeActions(constraint, rule, scope, variables);
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

    private void executeActions(RuntimeConstraint constraint, ConstraintRule rule, ScopeState scope,
                                Map<String, JsonNode> values) {
        for (JsonNode action : elements(rule.getViolationActions())) {
            String actionTaken;
            try {
                actionTaken = executeAction(action, scope, rule, values);
            } catch (RuntimeException e) {
                actionTaken = "FAILED:" + text(action, "actionType") + ":" + e.getMessage();
                log.warn("约束动作执行失败, ruleId={}: {}", rule.getId(), e.getMessage());
            }
            writeViolationLog(constraint, rule, action, scope, values, actionTaken);
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
                    eventPublisher.publishEvent(new ConstraintAlertEvent(isTaskRule(rule) ? null : rule.getId(), rule.getRuleName(),
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
        stateMachineCommandPort.executeConstraintCapability(deviceInstanceId, capabilityName,
                action.path("parameters").isObject()
                        ? JsonNodeSupport.MAPPER.convertValue(action.path("parameters"), Map.class) : Map.of());
        return "DEVICE_CAPABILITY:CONSTRAINT_EXECUTE:" + capabilityName;
    }


    private void writeViolationLog(RuntimeConstraint constraint, ConstraintRule rule, JsonNode action, ScopeState scope,
                                   Map<String, JsonNode> values, String actionTaken) {
        ViolationLog logEntry = new ViolationLog();
        logEntry.setConstraintRuleId(isTaskRule(rule) ? null : rule.getId());
        logEntry.setConstraintType(isTaskRule(rule) ? "TASK_CONSTRAINT" : "CONSTRAINT_MODEL");
        logEntry.setTaskId(scope.taskId == null ? positiveLong(action.get("targetTaskId")) : scope.taskId);
        logEntry.setTaskStepId(scope.taskStepId);
        logEntry.setDeviceInstanceId(scope.deviceInstanceId == null ? positiveLong(action.get("deviceInstanceId")) : scope.deviceInstanceId);
        logEntry.setObservedVariable(constraint.observedVariables());
        logEntry.setExpression(JsonNodeSupport.toNode(rule.getExpression()));
        logEntry.setActualValue(snapshot(values));
        logEntry.setVariableSnapshot(scopeSnapshot(constraint, action));
        logEntry.setActionTaken(actionTaken);
        violationLogService.save(logEntry);
    }

    private ObjectNode scopeSnapshot(RuntimeConstraint constraint, JsonNode action) {
        try {
            return scopeSnapshotReader.snapshot(constraint, action);
        } catch (RuntimeException e) {
            ObjectNode failed = JsonNodeSupport.objectNode();
            failed.putObject("tasks");
            failed.putObject("devices");
            failed.put("snapshotError", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return failed;
        }
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
        Instant threshold = Instant.now().minusSeconds(MAX_HISTORY_SECONDS);
        history.removeIf(sample -> sample.occurredAt().isBefore(threshold));
        if (history.size() > MAX_HISTORY_SIZE) history.subList(0, history.size() - MAX_HISTORY_SIZE).clear();
    }

    private List<ConstraintExpressionEvaluator.TimedValue> toTimedValues(List<ObservationSample> samples) {
        return samples.stream()
                .map(sample -> new ConstraintExpressionEvaluator.TimedValue(sample.observedAt(), sample.value()))
                .toList();
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

    private boolean isTaskRule(ConstraintRule rule) {
        return rule != null && rule.getId() != null && rule.getId() < 0;
    }

    private static final class ScopeState {
        private final Long ruleId;
        private final String scopeKey;
        private final Map<String, JsonNode> values = new HashMap<>();
        private final Map<String, List<ConstraintExpressionEvaluator.TimedValue>> histories = new HashMap<>();
        private final Map<String, Long> revisions = new HashMap<>();
        private Long taskId;
        private Long taskStepId;
        private Long deviceInstanceId;

        private ScopeState(Long ruleId, String scopeKey) {
            this.ruleId = ruleId;
            this.scopeKey = scopeKey;
        }
    }
}
