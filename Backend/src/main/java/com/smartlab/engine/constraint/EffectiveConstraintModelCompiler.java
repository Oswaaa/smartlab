package com.smartlab.engine.constraint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.observation.ObservableKey;
import com.smartlab.engine.observation.device.DeviceTwinSnapshot;
import com.smartlab.engine.observation.device.DeviceTwinSnapshotRegistry;
import com.smartlab.global.contract.ObservableObjectType;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.constraint.ConstraintModelRuleSummary;
import com.smartlab.management.entity.constraint.ConstraintRule;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.service.db.constraint.ConstraintRuleService;
import com.smartlab.management.service.db.constraint.TaskConstraintService;
import com.smartlab.management.service.db.resource.adapter.VirtualLeaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Compiles one immutable effective model into both its downloadable JSON and
 * its executable monitoring plan. Rule parsing and observable normalization
 * happen only once, so the two representations cannot drift independently.
 */
@Service
public class EffectiveConstraintModelCompiler {
    private final ConstraintRuleService globalRules;
    private final TaskConstraintService taskRules;
    private final DeviceTwinSnapshotRegistry devices;
    private VirtualLeaseService virtualLeases;
    private final AtomicLong revisionSequence = new AtomicLong();
    private final ConcurrentHashMap<String, RevisionState> revisions = new ConcurrentHashMap<>();

    public EffectiveConstraintModelCompiler(ConstraintRuleService globalRules,
                                            TaskConstraintService taskRules,
                                            DeviceTwinSnapshotRegistry devices) {
        this.globalRules = globalRules;
        this.taskRules = taskRules;
        this.devices = devices;
    }

    @Autowired(required = false)
    public void setVirtualLeaseService(VirtualLeaseService virtualLeases) {
        this.virtualLeases = virtualLeases;
    }

    public EffectiveConstraintModel compileRuntime() {
        List<Task> activeTasks = sortedTasks(taskRules.activeTasks());
        List<RuleSource> sources = new ArrayList<>(globalSources());
        for (Task task : activeTasks) sources.addAll(taskSources(task));
        return compile("runtime", null, activeTasks, sources);
    }

    public EffectiveConstraintModel compileGlobal() {
        return compile("global", null, sortedTasks(taskRules.activeTasks()), globalSources());
    }

    public EffectiveConstraintModel compileForTask(Long taskId) {
        Task task = taskId == null ? null : taskRules.task(taskId);
        if (task == null) throw new IllegalArgumentException("任务不存在");
        List<RuleSource> sources = new ArrayList<>(globalSources());
        sources.addAll(taskSources(task));
        return compile("task:" + taskId, task, List.of(task), sources);
    }

    private EffectiveConstraintModel compile(String scopeKey, Task task, List<Task> scopeTasks,
                                             List<RuleSource> sources) {
        ArrayNode observableObjects = JsonNodeSupport.arrayNode();
        ArrayNode constraintsJson = JsonNodeSupport.arrayNode();
        Map<String, String> observableNames = new LinkedHashMap<>();
        Set<String> usedObservableNames = new HashSet<>();
        List<ConstraintModelRuleSummary> globalSummaries = new ArrayList<>();
        List<ConstraintModelRuleSummary> taskSummaries = new ArrayList<>();
        List<CompiledRuleTemplate> templates = new ArrayList<>();

        for (RuleSource source : sources) {
            CompiledRuleTemplate template = compileTemplate(source, observableObjects, constraintsJson,
                    observableNames, usedObservableNames);
            templates.add(template);
            if (source.taskId() == null) globalSummaries.add(template.summary());
            else taskSummaries.add(template.summary());
        }

        Map<RuntimeConstraintKey, RuntimeConstraint> runtimeConstraints = new LinkedHashMap<>();
        for (CompiledRuleTemplate template : templates) {
            expandRuntime(template, scopeTasks, runtimeConstraints);
        }
        Map<ObservableKey, Set<RuntimeConstraintKey>> dependencies = dependencyIndex(runtimeConstraints);

        ObjectNode jsonModel = JsonNodeSupport.objectNode();
        jsonModel.set("observableObjects", observableObjects);
        jsonModel.set("constraints", constraintsJson);
        String modelHash = hash(canonical(jsonModel).toString());
        String runtimeFingerprint = runtimeFingerprint(modelHash, runtimeConstraints, dependencies);
        long revision = revision(scopeKey, runtimeFingerprint);
        Instant compiledAt = Instant.now();
        ConstraintMonitoringPlan plan = new ConstraintMonitoringPlan(revision, modelHash, compiledAt,
                runtimeConstraints, dependencies);
        return new EffectiveConstraintModel(
                task == null ? null : task.getId(),
                task == null ? null : task.getTaskName(),
                task == null ? null : task.getTaskStatus(),
                compiledAt,
                revision,
                modelHash,
                globalSummaries,
                taskSummaries,
                jsonModel,
                plan
        );
    }

    private CompiledRuleTemplate compileTemplate(RuleSource source,
                                                 ArrayNode observableObjects,
                                                 ArrayNode constraintsJson,
                                                 Map<String, String> observableNames,
                                                 Set<String> usedObservableNames) {
        ConstraintRule rule = source.rule();
        JsonNode bindings = rule.getBindings();
        if (bindings == null || !bindings.isObject()) {
            throw new IllegalArgumentException(rule.getRuleName() + "的bindings必须是对象");
        }
        if (rule.getViolationActions() == null || !rule.getViolationActions().isArray()) {
            throw new IllegalArgumentException(rule.getRuleName() + "的violationActions必须是数组");
        }

        ObjectNode exportedBindings = JsonNodeSupport.objectNode();
        ObjectNode observedVariables = JsonNodeSupport.objectNode();
        Map<String, ObservableKey> runtimeBindings = new LinkedHashMap<>();
        int bindingCount = 0;
        var fields = bindings.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String variableName = entry.getKey();
            JsonNode binding = entry.getValue();
            String bindingType = binding.path("bindingType").asText();
            if ("OBSERVABLE".equals(bindingType)) {
                JsonNode observableSource = binding.path("source");
                if (!observableSource.isObject()) {
                    throw new IllegalArgumentException(rule.getRuleName() + "的变量" + variableName + "缺少source");
                }
                ObservableKey runtimeKey = parseObservableKey(observableSource);
                runtimeBindings.put(variableName, runtimeKey);
                String signature = canonical(observableSource).toString();
                String observableName = observableNames.get(signature);
                if (observableName == null) {
                    observableName = uniqueObservableName(observableSource, usedObservableNames);
                    observableNames.put(signature, observableName);
                    ObjectNode exportedObservable = ((ObjectNode) observableSource).deepCopy();
                    exportedObservable.put("name", observableName);
                    observableObjects.add(exportedObservable);
                }
                ObjectNode exportedBinding = JsonNodeSupport.objectNode();
                exportedBinding.put("bindingType", "OBSERVABLE");
                exportedBinding.put("observableName", observableName);
                exportedBindings.set(variableName, exportedBinding);
                observedVariables.set(variableName, exportedBinding.deepCopy());
            } else if ("LITERAL".equals(bindingType)) {
                ObjectNode exportedBinding = JsonNodeSupport.objectNode();
                exportedBinding.put("bindingType", "LITERAL");
                JsonNode value = binding.get("value");
                exportedBinding.set("value", value == null ? JsonNodeSupport.MAPPER.nullNode() : value.deepCopy());
                exportedBindings.set(variableName, exportedBinding);
            } else {
                throw new IllegalArgumentException(rule.getRuleName() + "包含未知bindingType: " + bindingType);
            }
            bindingCount++;
        }

        ObjectNode exportedRule = JsonNodeSupport.objectNode();
        exportedRule.put("name", rule.getRuleName());
        exportedRule.put("expression", rule.getExpression());
        exportedRule.set("bindings", exportedBindings);
        if (rule.getWindowSeconds() != null) exportedRule.put("windowSeconds", rule.getWindowSeconds());
        exportedRule.set("violationActions", rule.getViolationActions().deepCopy());
        constraintsJson.add(exportedRule);

        ConstraintModelRuleSummary summary = new ConstraintModelRuleSummary(
                source.origin(),
                source.taskId() == null ? rule.getId() : null,
                source.taskRuleIndex(),
                rule.getRuleName(),
                rule.getExpression(),
                bindingCount,
                rule.getViolationActions().size(),
                rule.getWindowSeconds(),
                rule.getBindings() == null ? JsonNodeSupport.objectNode() : rule.getBindings().deepCopy(),
                rule.getViolationActions().deepCopy()
        );
        String version = hash(canonical(exportedRule).toString()).substring(0, 16);
        return new CompiledRuleTemplate(source, Map.copyOf(runtimeBindings), observedVariables, version, summary);
    }

    private void expandRuntime(CompiledRuleTemplate template, List<Task> scopeTasks,
                               Map<RuntimeConstraintKey, RuntimeConstraint> target) {
        RuleSource source = template.source();
        Map<String, ObservableKey> bindings = template.runtimeBindings();
        if (source.taskId() != null) {
            appendRuntime(template, source.taskId(), firstDevice(bindings), bindings, target);
            inheritOntoActiveVirtuals(template, source.taskId(), bindings, target);
            return;
        }

        boolean workflowScoped = bindings.values().stream().anyMatch(key ->
                key.sourceType() == ObservableObjectType.NODE_LIFECYCLE_STATE
                        || key.sourceType() == ObservableObjectType.NODE_INTERNAL_VARIABLE
                        || key.sourceType() == ObservableObjectType.TASK_LIFECYCLE_STATE);
        if (workflowScoped) {
            Set<Long> explicitTaskIds = new HashSet<>();
            Set<Long> templateIds = new HashSet<>();
            bindings.values().forEach(key -> {
                if (key.taskId() != null) explicitTaskIds.add(key.taskId());
                if (key.workflowTemplateId() != null) templateIds.add(key.workflowTemplateId());
            });
            for (Task task : scopeTasks) {
                if (!explicitTaskIds.isEmpty() && !explicitTaskIds.contains(task.getId())) continue;
                if (!templateIds.isEmpty() && !templateIds.contains(task.getFlowModelId())) continue;
                appendRuntime(template, task.getId(), null, bindings, target);
            }
            return;
        }

        Set<Long> explicitDevices = deviceIds(bindings);
        if (!explicitDevices.isEmpty()) {
            for (Long deviceId : explicitDevices) {
                appendRuntime(template, null, deviceId, bindings, target);
            }
            inheritOntoActiveVirtuals(template, null, bindings, target);
            return;
        }
        boolean deviceWildcard = bindings.values().stream().anyMatch(key -> isDevice(key.sourceType()));
        if (deviceWildcard) {
            for (DeviceTwinSnapshot snapshot : devices.snapshotAll().values()) {
                if (matchesDeviceModel(source.rule(), snapshot.deviceModelId())) {
                    appendRuntime(template, null, snapshot.deviceInstanceId(),
                            bindDevice(bindings, snapshot.deviceInstanceId()), target);
                }
            }
            return;
        }
        appendRuntime(template, null, null, bindings, target);
    }

    private void appendRuntime(CompiledRuleTemplate template, Long taskId, Long deviceId,
                               Map<String, ObservableKey> bindings,
                               Map<RuntimeConstraintKey, RuntimeConstraint> target) {
        Map<String, ObservableKey> scoped = new LinkedHashMap<>();
        bindings.forEach((name, key) -> scoped.put(name, taskId == null ? key : withTask(key, taskId)));
        RuleSource source = template.source();
        String scope = runtimeScope(taskId, deviceId);
        RuntimeConstraintKey key = new RuntimeConstraintKey(source.origin(), source.stableKey(), scope,
                template.ruleVersion());
        target.put(key, new RuntimeConstraint(key, source.rule(), scoped, taskId, null, deviceId,
                template.observedVariables(), stampActions(source.rule().getViolationActions(), taskId, deviceId)));
    }

    /** 模板动作可以省略实例/任务；拆份后写入这一份要执行的完整命令，执行器不再向观测作用域借地址。 */
    private JsonNode stampActions(JsonNode sourceActions, Long taskId, Long deviceId) {
        ArrayNode result = JsonNodeSupport.arrayNode();
        if (sourceActions == null || !sourceActions.isArray()) return result;
        for (JsonNode action : sourceActions) {
            if (!action.isObject()) {
                result.add(action.deepCopy());
                continue;
            }
            ObjectNode copy = action.deepCopy();
            String actionType = copy.path("actionType").asText();
            if ("DEVICE_CAPABILITY".equals(actionType) && deviceId != null) {
                copy.put("deviceInstanceId", deviceId);
            }
            if ("SYSTEM".equals(actionType)) {
                String systemAction = copy.path("action").asText();
                if (("ABORT".equals(systemAction) || "PAUSE".equals(systemAction))
                        && positive(copy.get("targetTaskId")) == null && taskId != null) {
                    copy.put("targetTaskId", taskId);
                }
            }
            result.add(copy);
        }
        return result;
    }

    private Map<ObservableKey, Set<RuntimeConstraintKey>> dependencyIndex(
            Map<RuntimeConstraintKey, RuntimeConstraint> constraints) {
        Map<ObservableKey, Set<RuntimeConstraintKey>> mutable = new LinkedHashMap<>();
        constraints.forEach((constraintKey, constraint) -> constraint.observableBindings().values().forEach(key ->
                mutable.computeIfAbsent(key, ignored -> new HashSet<>()).add(constraintKey)));
        Map<ObservableKey, Set<RuntimeConstraintKey>> frozen = new LinkedHashMap<>();
        mutable.forEach((key, value) -> frozen.put(key, Set.copyOf(value)));
        return Map.copyOf(frozen);
    }

    private List<RuleSource> globalSources() {
        List<ConstraintRule> rules = new ArrayList<>(globalRules.list(Boolean.TRUE));
        rules.sort(Comparator.comparing(ConstraintRule::getId,
                Comparator.nullsLast(Comparator.naturalOrder())));
        List<RuleSource> result = new ArrayList<>();
        for (int index = 0; index < rules.size(); index++) {
            ConstraintRule rule = rules.get(index);
            String stableKey = rule.getId() == null ? "index:" + index : String.valueOf(rule.getId());
            result.add(new RuleSource("GLOBAL", stableKey, null, null, rule));
        }
        return result;
    }

    private List<RuleSource> taskSources(Task task) {
        List<ConstraintRule> rules = taskRules.rulesForTask(task);
        List<RuleSource> result = new ArrayList<>();
        for (int index = 0; index < rules.size(); index++) {
            ConstraintRule rule = rules.get(index);
            int taskRuleIndex = taskRuleIndex(task.getId(), rule.getId(), index);
            result.add(new RuleSource("TASK", String.valueOf(rule.getId()), task.getId(), taskRuleIndex, rule));
        }
        return result;
    }

    private List<Task> sortedTasks(List<Task> tasks) {
        List<Task> result = tasks == null ? new ArrayList<>() : new ArrayList<>(tasks);
        result.sort(Comparator.comparing(Task::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return List.copyOf(result);
    }

    private int taskRuleIndex(Long taskId, Long runtimeRuleId, int fallback) {
        if (taskId == null || runtimeRuleId == null || runtimeRuleId >= 0) return fallback;
        long base = Math.multiplyExact(taskId, 1_000_000L);
        long index = -runtimeRuleId - base - 1L;
        return index >= 0 && index <= Integer.MAX_VALUE ? (int) index : fallback;
    }

    private ObservableKey parseObservableKey(JsonNode source) {
        String sourceType = source.path("sourceType").asText(null);
        if (sourceType == null) throw new IllegalArgumentException("observable缺少sourceType");
        return new ObservableKey(
                ObservableObjectType.valueOf(sourceType),
                positive(source.get("deviceInstanceId")),
                positive(source.get("workflowTemplateId")),
                positive(source.get("taskId")),
                text(source, "regionName"),
                text(source, "nodeName"),
                text(source, "targetName"),
                text(source, "variableName")
        );
    }

    private void inheritOntoActiveVirtuals(CompiledRuleTemplate template, Long taskId,
                                              Map<String, ObservableKey> bindings,
                                              Map<RuntimeConstraintKey, RuntimeConstraint> target) {
        Set<Long> explicitDevices = deviceIds(bindings);
        for (Long deviceId : explicitDevices) {
            for (Long virtualId : inheritedVirtualInstanceIds(deviceId)) {
                if (virtualId == null || virtualId.equals(deviceId) || explicitDevices.contains(virtualId)) {
                    continue;
                }
                appendRuntime(template, taskId, virtualId, bindDevice(bindings, virtualId), target);
            }
        }
    }

    private String runtimeScope(Long taskId, Long deviceId) {
        if (taskId != null && deviceId != null) {
            return "task:" + taskId + ":device:" + deviceId;
        }
        if (taskId != null) {
            return "task:" + taskId;
        }
        if (deviceId != null) {
            return "device:" + deviceId;
        }
        return "global";
    }

    private Set<Long> inheritedVirtualInstanceIds(Long physicalInstanceId) {
        if (virtualLeases == null || physicalInstanceId == null) {
            return Set.of();
        }
        return virtualLeases.listActiveVirtualInstanceIds(physicalInstanceId);
    }

    private Map<String, ObservableKey> bindDevice(Map<String, ObservableKey> source, Long deviceId) {
        Map<String, ObservableKey> result = new LinkedHashMap<>();
        source.forEach((name, key) -> result.put(name, isDevice(key.sourceType())
                ? new ObservableKey(key.sourceType(), deviceId, key.workflowTemplateId(), key.taskId(),
                key.regionName(), key.nodeName(), key.targetName(), key.variableName()) : key));
        return result;
    }

    private ObservableKey withTask(ObservableKey key, Long taskId) {
        if (key.sourceType() == ObservableObjectType.NODE_LIFECYCLE_STATE
                || key.sourceType() == ObservableObjectType.NODE_INTERNAL_VARIABLE
                || key.sourceType() == ObservableObjectType.TASK_LIFECYCLE_STATE) {
            return new ObservableKey(key.sourceType(), key.deviceInstanceId(), key.workflowTemplateId(), taskId,
                    key.regionName(), key.nodeName(), key.targetName(), key.variableName());
        }
        return key;
    }

    private boolean matchesDeviceModel(ConstraintRule rule, Long modelId) {
        if (modelId == null || rule.getBindings() == null) return false;
        var fields = rule.getBindings().fields();
        while (fields.hasNext()) {
            JsonNode source = fields.next().getValue().path("source");
            if (source.path("deviceModelId").canConvertToLong()
                    && source.path("deviceModelId").asLong() != modelId) return false;
        }
        return true;
    }

    private Set<Long> deviceIds(Map<String, ObservableKey> bindings) {
        Set<Long> result = new HashSet<>();
        bindings.values().forEach(key -> {
            if (key.deviceInstanceId() != null) result.add(key.deviceInstanceId());
        });
        return result;
    }

    private Long firstDevice(Map<String, ObservableKey> bindings) {
        return bindings.values().stream().map(ObservableKey::deviceInstanceId)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    private boolean isDevice(ObservableObjectType type) {
        return type == ObservableObjectType.DEVICE_ATTRIBUTE
                || type == ObservableObjectType.DEVICE_OPERATION_STATE
                || type == ObservableObjectType.DEVICE_COMMAND_LIFECYCLE;
    }

    private String uniqueObservableName(JsonNode source, Set<String> usedNames) {
        String base = sanitize(observableIdentityName(source));
        String candidate = base;
        int suffix = 2;
        while (!usedNames.add(candidate)) candidate = base + "_" + suffix++;
        return candidate;
    }

    /** 按 source 身份字段命名，不使用规则 id 或表达式变量名。 */
    private String observableIdentityName(JsonNode source) {
        String sourceType = text(source, "sourceType");
        if (sourceType == null) return "observable";
        return switch (sourceType) {
            case "DEVICE_ATTRIBUTE" -> join("attr", idPart(source, "deviceModelId"), instanceOrAll(source),
                    tokenPart(source, "targetName"));
            case "DEVICE_OPERATION_STATE" -> join("op", idPart(source, "deviceModelId"), instanceOrAll(source),
                    tokenPart(source, "regionName"));
            case "DEVICE_COMMAND_LIFECYCLE" -> join("cmd", idPart(source, "deviceModelId"), instanceOrAll(source));
            case "NODE_LIFECYCLE_STATE" -> join("node", idPart(source, "workflowTemplateId"), tokenPart(source, "nodeName"));
            case "NODE_INTERNAL_VARIABLE" -> join("nvar", idPart(source, "workflowTemplateId"),
                    tokenPart(source, "nodeName"), tokenPart(source, "variableName"));
            case "TASK_LIFECYCLE_STATE" -> join("task", idPart(source, "workflowTemplateId"), taskOrAll(source));
            default -> "observable";
        };
    }

    private String instanceOrAll(JsonNode source) {
        Long instanceId = positive(source.get("deviceInstanceId"));
        return instanceId == null ? "all" : String.valueOf(instanceId);
    }

    private String taskOrAll(JsonNode source) {
        Long taskId = positive(source.get("taskId"));
        return taskId == null ? "all" : String.valueOf(taskId);
    }

    private String idPart(JsonNode source, String field) {
        Long value = positive(source.get(field));
        return value == null ? "0" : String.valueOf(value);
    }

    private String tokenPart(JsonNode source, String field) {
        String value = text(source, field);
        return value == null ? "unnamed" : sanitize(value);
    }

    private String join(String... parts) {
        return String.join("_", parts);
    }

    private String runtimeFingerprint(String modelHash,
                                      Map<RuntimeConstraintKey, RuntimeConstraint> constraints,
                                      Map<ObservableKey, Set<RuntimeConstraintKey>> dependencies) {
        List<String> keys = constraints.keySet().stream().map(Object::toString).sorted().toList();
        List<String> observables = dependencies.keySet().stream().map(Object::toString).sorted().toList();
        return hash(modelHash + "|" + keys + "|" + observables);
    }

    private long revision(String scopeKey, String fingerprint) {
        return revisions.compute(scopeKey, (ignored, current) -> {
            if (current != null && current.fingerprint().equals(fingerprint)) return current;
            return new RevisionState(revisionSequence.incrementAndGet(), fingerprint);
        }).revision();
    }

    private JsonNode canonical(JsonNode value) {
        if (value == null || value.isValueNode()) return value == null ? JsonNodeSupport.MAPPER.nullNode() : value.deepCopy();
        if (value.isArray()) {
            ArrayNode array = JsonNodeSupport.arrayNode();
            value.forEach(item -> array.add(canonical(item)));
            return array;
        }
        ObjectNode object = JsonNodeSupport.objectNode();
        List<String> names = new ArrayList<>();
        value.fieldNames().forEachRemaining(names::add);
        names.stream().sorted().forEach(name -> object.set(name, canonical(value.get(name))));
        return object;
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("无法生成约束模型hash", e);
        }
    }

    private Long positive(JsonNode value) {
        return value != null && value.canConvertToLong() && value.asLong() > 0 ? value.asLong() : null;
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private String sanitize(String value) {
        String sanitized = value == null ? "observable" : value.replaceAll("[^A-Za-z0-9_]", "_");
        return sanitized.isBlank() ? "observable" : sanitized;
    }

    private record RuleSource(String origin, String stableKey, Long taskId, Integer taskRuleIndex,
                              ConstraintRule rule) {
    }

    private record CompiledRuleTemplate(RuleSource source,
                                        Map<String, ObservableKey> runtimeBindings,
                                        JsonNode observedVariables,
                                        String ruleVersion,
                                        ConstraintModelRuleSummary summary) {
        private CompiledRuleTemplate {
            observedVariables = observedVariables.deepCopy();
        }

        @Override
        public JsonNode observedVariables() {
            return observedVariables.deepCopy();
        }
    }

    private record RevisionState(long revision, String fingerprint) {
    }
}
