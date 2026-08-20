package com.smartlab.engine.constraint;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.observation.ObservationStatus;
import com.smartlab.engine.observation.SnapshotOrigin;
import com.smartlab.engine.observation.device.DeviceTwinSnapshot;
import com.smartlab.engine.observation.device.DeviceTwinSnapshotRegistry;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.constraint.ConstraintRule;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.service.db.constraint.ConstraintRuleService;
import com.smartlab.management.service.db.constraint.TaskConstraintService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EffectiveConstraintModelCompilerTest {

    @Test
    void producesJsonAndRuntimePlanFromOneEffectiveCompilation() {
        ConstraintRuleService globalRules = mock(ConstraintRuleService.class);
        TaskConstraintService taskRules = mock(TaskConstraintService.class);
        DeviceTwinSnapshotRegistry devices = mock(DeviceTwinSnapshotRegistry.class);
        ConstraintRule global = rule(12L, "全局温度上限", "temperature > limit", 80);
        ConstraintRule taskRule = rule(-1_024_000_001L, "任务温度上限", "temperature > limit", 70);
        Task task = task(1024L);
        when(globalRules.list(Boolean.TRUE)).thenReturn(List.of(global));
        when(taskRules.task(1024L)).thenReturn(task);
        when(taskRules.rulesForTask(task)).thenReturn(List.of(taskRule));

        EffectiveConstraintModelCompiler compiler = new EffectiveConstraintModelCompiler(globalRules, taskRules, devices);
        EffectiveConstraintModel result = compiler.compileForTask(1024L);

        assertEquals(1, result.globalConstraints().size());
        assertEquals(1, result.taskConstraints().size());
        assertEquals(2, result.jsonModel().path("constraints").size());
        assertEquals(1, result.jsonModel().path("observableObjects").size());
        assertEquals(2, result.monitoringPlan().constraints().size());
        assertEquals(1, result.monitoringPlan().dependencies().size());
        assertEquals(result.modelHash(), result.monitoringPlan().modelHash());
        assertEquals(result.revision(), result.monitoringPlan().revision());
        assertEquals("attr_3_18_temperature",
                result.jsonModel().path("constraints").get(0).path("bindings")
                        .path("temperature").path("observableName").asText());
        assertFalse(result.jsonModel().path("constraints").get(0).path("bindings")
                .path("temperature").has("source"));
        RuntimeConstraint runtime = result.monitoringPlan().constraints().values().stream()
                .filter(item -> item.rule().getId().equals(12L))
                .findFirst()
                .orElseThrow();
        assertEquals("OBSERVABLE",
                runtime.observedVariables().path("temperature").path("bindingType").asText());
        assertEquals("attr_3_18_temperature",
                runtime.observedVariables().path("temperature").path("observableName").asText());
        assertFalse(runtime.observedVariables().has("limit"));
    }

    @Test
    void retainsEveryObservableBindingForComparativeExpressionsInDeclarationOrder() {
        ConstraintRuleService globalRules = mock(ConstraintRuleService.class);
        TaskConstraintService taskRules = mock(TaskConstraintService.class);
        ConstraintRule comparative = rule(21L, "反应釜温度差", "temperature1 > temperature2", 0);
        ObjectNode bindings = JsonNodeSupport.objectNode();
        bindings.set("temperature1", observableBinding(18L, "temperature"));
        bindings.set("temperature2", observableBinding(19L, "temperature"));
        comparative.setBindings(bindings);
        when(globalRules.list(Boolean.TRUE)).thenReturn(List.of(comparative));
        when(taskRules.activeTasks()).thenReturn(List.of());

        EffectiveConstraintModel result = new EffectiveConstraintModelCompiler(
                globalRules, taskRules, mock(DeviceTwinSnapshotRegistry.class)).compileGlobal();

        RuntimeConstraint runtime = result.monitoringPlan().constraints().values().iterator().next();
        List<String> observedNames = new java.util.ArrayList<>();
        runtime.observedVariables().fieldNames().forEachRemaining(observedNames::add);
        assertEquals(List.of("temperature1", "temperature2"), observedNames);
        assertEquals("attr_3_18_temperature",
                runtime.observedVariables().path("temperature1").path("observableName").asText());
        assertEquals("attr_3_19_temperature",
                runtime.observedVariables().path("temperature2").path("observableName").asText());
    }

    @Test
    void deduplicatesTheSameObservableForExportAndRuntime() {
        ConstraintRuleService globalRules = mock(ConstraintRuleService.class);
        TaskConstraintService taskRules = mock(TaskConstraintService.class);
        when(globalRules.list(Boolean.TRUE)).thenReturn(List.of(
                rule(7L, "温度规则一", "temperature > limit", 10),
                rule(8L, "温度规则二", "temperature > limit", 20)
        ));
        when(taskRules.activeTasks()).thenReturn(List.of());
        EffectiveConstraintModelCompiler compiler = new EffectiveConstraintModelCompiler(
                globalRules, taskRules, mock(DeviceTwinSnapshotRegistry.class));

        EffectiveConstraintModel first = compiler.compileGlobal();
        EffectiveConstraintModel second = compiler.compileGlobal();

        assertEquals(1, first.jsonModel().path("observableObjects").size());
        assertEquals(1, first.monitoringPlan().dependencies().size());
        assertEquals(first.jsonModel().path("constraints").get(0).path("bindings")
                        .path("temperature").path("observableName").asText(),
                first.jsonModel().path("constraints").get(1).path("bindings")
                        .path("temperature").path("observableName").asText());
        assertEquals(first.modelHash(), second.modelHash());
        assertEquals(first.revision(), second.revision());
        assertTrue(first.modelHash().matches("[0-9a-f]{64}"));
    }

    @Test
    void internSamePhysicalSourceEvenWhenVariableNamesDiffer() {
        ConstraintRuleService globalRules = mock(ConstraintRuleService.class);
        TaskConstraintService taskRules = mock(TaskConstraintService.class);
        ConstraintRule ceiling = rule(3L, "温度上限", "observedValue > threshold", 480);
        ObjectNode ceilingBindings = JsonNodeSupport.objectNode();
        ceilingBindings.set("observedValue", observableBinding(19L, "attribute_1"));
        ObjectNode threshold = ceilingBindings.putObject("threshold");
        threshold.put("bindingType", "LITERAL");
        threshold.put("value", 480);
        ceiling.setBindings(ceilingBindings);
        ceiling.setExpression("observedValue > threshold");

        ConstraintRule rate = rule(4L, "升温速率", "rate(value, 10) > limit", 10);
        ObjectNode rateBindings = JsonNodeSupport.objectNode();
        rateBindings.set("value", observableBinding(19L, "attribute_1"));
        ObjectNode limit = rateBindings.putObject("limit");
        limit.put("bindingType", "LITERAL");
        limit.put("value", 10);
        rate.setBindings(rateBindings);
        rate.setExpression("rate(value, 10) > limit");

        when(globalRules.list(Boolean.TRUE)).thenReturn(List.of(ceiling, rate));
        when(taskRules.activeTasks()).thenReturn(List.of());

        EffectiveConstraintModel result = new EffectiveConstraintModelCompiler(
                globalRules, taskRules, mock(DeviceTwinSnapshotRegistry.class)).compileGlobal();

        assertEquals(1, result.jsonModel().path("observableObjects").size());
        assertEquals("attr_3_19_attribute_1",
                result.jsonModel().path("observableObjects").get(0).path("name").asText());
        assertEquals("attr_3_19_attribute_1",
                result.jsonModel().path("constraints").get(0).path("bindings")
                        .path("observedValue").path("observableName").asText());
        assertEquals("attr_3_19_attribute_1",
                result.jsonModel().path("constraints").get(1).path("bindings")
                        .path("value").path("observableName").asText());
    }

    @Test
    void distinguishesPinnedInstanceFromModelWideObservableNames() {
        ConstraintRuleService globalRules = mock(ConstraintRuleService.class);
        TaskConstraintService taskRules = mock(TaskConstraintService.class);
        ConstraintRule pinned = rule(3L, "特例上限", "temperature > limit", 480);
        ConstraintRule modelWide = rule(5L, "全场上限", "temperature > limit", 500);
        ((ObjectNode) modelWide.getBindings().path("temperature").path("source")).remove("deviceInstanceId");
        when(globalRules.list(Boolean.TRUE)).thenReturn(List.of(pinned, modelWide));
        when(taskRules.activeTasks()).thenReturn(List.of());

        EffectiveConstraintModel result = new EffectiveConstraintModelCompiler(
                globalRules, taskRules, mock(DeviceTwinSnapshotRegistry.class)).compileGlobal();

        assertEquals(2, result.jsonModel().path("observableObjects").size());
        assertEquals("attr_3_18_temperature",
                result.jsonModel().path("constraints").get(0).path("bindings")
                        .path("temperature").path("observableName").asText());
        assertEquals("attr_3_all_temperature",
                result.jsonModel().path("constraints").get(1).path("bindings")
                        .path("temperature").path("observableName").asText());
    }

    @Test
    void namesNonAttributeSourcesByIdentityFields() {
        ConstraintRuleService globalRules = mock(ConstraintRuleService.class);
        TaskConstraintService taskRules = mock(TaskConstraintService.class);
        ConstraintRule rule = rule(9L, "混合观测", "opState != cmd && nodeVar > 0 && taskState == 'RUNNING'", 0);
        ObjectNode bindings = JsonNodeSupport.objectNode();
        ObjectNode op = bindings.putObject("opState");
        op.put("bindingType", "OBSERVABLE");
        ObjectNode opSource = op.putObject("source");
        opSource.put("sourceType", "DEVICE_OPERATION_STATE");
        opSource.put("dataType", "JSON");
        opSource.put("deviceModelId", 26);
        opSource.put("deviceInstanceId", 19);
        opSource.put("regionName", "OPERATIONAL");
        ObjectNode cmd = bindings.putObject("cmd");
        cmd.put("bindingType", "OBSERVABLE");
        ObjectNode cmdSource = cmd.putObject("source");
        cmdSource.put("sourceType", "DEVICE_COMMAND_LIFECYCLE");
        cmdSource.put("dataType", "STRING");
        cmdSource.put("deviceModelId", 26);
        cmdSource.put("deviceInstanceId", 19);
        ObjectNode nodeLife = bindings.putObject("nodeLife");
        nodeLife.put("bindingType", "OBSERVABLE");
        ObjectNode nodeLifeSource = nodeLife.putObject("source");
        nodeLifeSource.put("sourceType", "NODE_LIFECYCLE_STATE");
        nodeLifeSource.put("dataType", "STRING");
        nodeLifeSource.put("workflowTemplateId", 3);
        nodeLifeSource.put("nodeName", "heat");
        ObjectNode nodeVar = bindings.putObject("nodeVar");
        nodeVar.put("bindingType", "OBSERVABLE");
        ObjectNode nodeSource = nodeVar.putObject("source");
        nodeSource.put("sourceType", "NODE_INTERNAL_VARIABLE");
        nodeSource.put("dataType", "DOUBLE");
        nodeSource.put("workflowTemplateId", 3);
        nodeSource.put("nodeName", "heat");
        nodeSource.put("variableName", "temperature");
        ObjectNode taskState = bindings.putObject("taskState");
        taskState.put("bindingType", "OBSERVABLE");
        ObjectNode taskSource = taskState.putObject("source");
        taskSource.put("sourceType", "TASK_LIFECYCLE_STATE");
        taskSource.put("dataType", "STRING");
        taskSource.put("workflowTemplateId", 3);
        taskSource.put("taskId", 1024);
        rule.setBindings(bindings);
        rule.setExpression("opState != cmd && nodeVar > 0 && taskState == 'RUNNING'");
        when(globalRules.list(Boolean.TRUE)).thenReturn(List.of(rule));
        when(taskRules.activeTasks()).thenReturn(List.of());

        EffectiveConstraintModel result = new EffectiveConstraintModelCompiler(
                globalRules, taskRules, mock(DeviceTwinSnapshotRegistry.class)).compileGlobal();

        assertEquals("op_26_19_OPERATIONAL",
                result.jsonModel().path("constraints").get(0).path("bindings")
                        .path("opState").path("observableName").asText());
        assertEquals("cmd_26_19",
                result.jsonModel().path("constraints").get(0).path("bindings")
                        .path("cmd").path("observableName").asText());
        assertEquals("nvar_3_heat_temperature",
                result.jsonModel().path("constraints").get(0).path("bindings")
                        .path("nodeVar").path("observableName").asText());
        assertEquals("node_3_heat",
                result.jsonModel().path("constraints").get(0).path("bindings")
                        .path("nodeLife").path("observableName").asText());
        assertEquals("task_3_1024",
                result.jsonModel().path("constraints").get(0).path("bindings")
                        .path("taskState").path("observableName").asText());
    }

    @Test
    void stampsHomologousDeviceInstanceOntoRuntimeActions() {
        ConstraintRuleService globalRules = mock(ConstraintRuleService.class);
        TaskConstraintService taskRules = mock(TaskConstraintService.class);
        DeviceTwinSnapshotRegistry devices = mock(DeviceTwinSnapshotRegistry.class);
        ConstraintRule modelWide = rule(5L, "全场上限", "temperature > limit", 500);
        ((ObjectNode) modelWide.getBindings().path("temperature").path("source")).remove("deviceInstanceId");
        ObjectNode action = JsonNodeSupport.objectNode();
        action.put("actionType", "DEVICE_CAPABILITY");
        action.put("deviceModelId", 3);
        action.put("capabilityName", "cool");
        modelWide.setViolationActions(JsonNodeSupport.MAPPER.createArrayNode().add(action));
        Instant now = Instant.now();
        DeviceTwinSnapshot first = new DeviceTwinSnapshot(18L, 3L, JsonNodeSupport.objectNode(), "ONLINE",
                now, now, 1, ObservationStatus.VALID, SnapshotOrigin.LIVE);
        DeviceTwinSnapshot second = new DeviceTwinSnapshot(19L, 3L, JsonNodeSupport.objectNode(), "ONLINE",
                now, now, 1, ObservationStatus.VALID, SnapshotOrigin.LIVE);
        when(globalRules.list(Boolean.TRUE)).thenReturn(List.of(modelWide));
        when(taskRules.activeTasks()).thenReturn(List.of());
        when(devices.snapshotAll()).thenReturn(Map.of(18L, first, 19L, second));

        EffectiveConstraintModel result = new EffectiveConstraintModelCompiler(
                globalRules, taskRules, devices).compileGlobal();

        assertFalse(result.jsonModel().path("constraints").get(0).path("violationActions")
                .get(0).has("deviceInstanceId"));
        Set<Long> stamped = result.monitoringPlan().constraints().values().stream()
                .map(item -> item.violationActions().get(0).path("deviceInstanceId").asLong())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(18L, 19L), stamped);
    }

    @Test
    void stampsHomologousTaskIdOntoRuntimeAbortActions() {
        ConstraintRuleService globalRules = mock(ConstraintRuleService.class);
        TaskConstraintService taskRules = mock(TaskConstraintService.class);
        ConstraintRule rule = rule(6L, "全任务失败终止", "taskState == expected", 0);
        ObjectNode bindings = JsonNodeSupport.objectNode();
        ObjectNode taskState = bindings.putObject("taskState");
        taskState.put("bindingType", "OBSERVABLE");
        ObjectNode source = taskState.putObject("source");
        source.put("sourceType", "TASK_LIFECYCLE_STATE");
        source.put("dataType", "STRING");
        source.put("workflowTemplateId", 3);
        ObjectNode expected = bindings.putObject("expected");
        expected.put("bindingType", "LITERAL");
        expected.put("value", "FAILED");
        rule.setBindings(bindings);
        rule.setExpression("taskState == expected");
        ObjectNode action = JsonNodeSupport.objectNode();
        action.put("actionType", "SYSTEM");
        action.put("action", "ABORT");
        rule.setViolationActions(JsonNodeSupport.MAPPER.createArrayNode().add(action));
        Task matching = task(10L);
        Task otherFlow = task(11L);
        otherFlow.setFlowModelId(5L);
        when(globalRules.list(Boolean.TRUE)).thenReturn(List.of(rule));
        when(taskRules.activeTasks()).thenReturn(List.of(matching, otherFlow, task(12L)));

        EffectiveConstraintModel result = new EffectiveConstraintModelCompiler(
                globalRules, taskRules, mock(DeviceTwinSnapshotRegistry.class)).compileGlobal();

        assertFalse(result.jsonModel().path("constraints").get(0).path("violationActions")
                .get(0).hasNonNull("targetTaskId"));
        assertEquals("task_3_all",
                result.jsonModel().path("constraints").get(0).path("bindings")
                        .path("taskState").path("observableName").asText());
        Set<Long> stamped = result.monitoringPlan().constraints().values().stream()
                .map(item -> item.violationActions().get(0).path("targetTaskId").asLong())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(10L, 12L), stamped);
    }

    private Task task(Long id) {
        Task task = new Task();
        task.setId(id);
        task.setTaskName("反应任务");
        task.setTaskStatus("RUNNING");
        task.setFlowModelId(3L);
        return task;
    }

    private ConstraintRule rule(Long id, String name, String expression, int limit) {
        ConstraintRule rule = new ConstraintRule();
        rule.setId(id);
        rule.setRuleName(name);
        rule.setExpression(expression);
        rule.setBindings(bindings(limit));
        rule.setViolationActions(alertActions());
        rule.setIsEnabled(true);
        return rule;
    }

    private ObjectNode bindings(int limit) {
        ObjectNode bindings = JsonNodeSupport.objectNode();
        bindings.set("temperature", observableBinding(18L, "temperature"));
        ObjectNode literal = bindings.putObject("limit");
        literal.put("bindingType", "LITERAL");
        literal.put("value", limit);
        return bindings;
    }

    private ObjectNode observableBinding(Long deviceInstanceId, String targetName) {
        ObjectNode observed = JsonNodeSupport.objectNode();
        observed.put("bindingType", "OBSERVABLE");
        ObjectNode source = observed.putObject("source");
        source.put("sourceType", "DEVICE_ATTRIBUTE");
        source.put("dataType", "DOUBLE");
        source.put("deviceModelId", 3);
        source.put("deviceInstanceId", deviceInstanceId);
        source.put("targetName", targetName);
        return observed;
    }

    private ArrayNode alertActions() {
        ArrayNode actions = JsonNodeSupport.arrayNode();
        ObjectNode action = actions.addObject();
        action.put("actionType", "SYSTEM");
        action.put("action", "ALERT");
        action.putNull("targetTaskId");
        return actions;
    }
}
