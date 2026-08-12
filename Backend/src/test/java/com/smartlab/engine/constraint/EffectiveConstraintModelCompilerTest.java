package com.smartlab.engine.constraint;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.observation.device.DeviceTwinSnapshotRegistry;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.constraint.ConstraintRule;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.service.db.constraint.ConstraintRuleService;
import com.smartlab.management.service.db.constraint.TaskConstraintService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
        assertEquals("global_12_temperature",
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
        assertEquals("global_12_temperature",
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
        assertEquals("global_21_temperature1",
                runtime.observedVariables().path("temperature1").path("observableName").asText());
        assertEquals("global_21_temperature2",
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
