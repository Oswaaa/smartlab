package com.smartlab.management.service.db.constraint;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.engine.constraint.ConstraintExpressionEvaluator;
import com.smartlab.management.entity.constraint.ConstraintRule;
import com.smartlab.management.mapper.constraint.ConstraintRuleMapper;
import com.smartlab.management.mapper.constraint.ViolationLogMapper;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConstraintRuleServiceTest {

    @Test
    void acceptsOneRuleWithInlineObservableSources() {
        ConstraintRuleMapper rules = mock(ConstraintRuleMapper.class);
        when(rules.insert(any(ConstraintRule.class))).thenReturn(1);
        ConstraintRuleService service = new ConstraintRuleService(rules, mock(DeviceInstancesMapper.class), new ConstraintExpressionEvaluator());

        assertDoesNotThrow(() -> service.save(validRule()));
    }

    @Test
    void rejectsLegacyObservableNameBinding() {
        ConstraintRuleMapper rules = mock(ConstraintRuleMapper.class);
        ConstraintRuleService service = new ConstraintRuleService(rules, mock(DeviceInstancesMapper.class), new ConstraintExpressionEvaluator());
        ConstraintRule rule = validRule();
        ((ObjectNode) rule.getBindings().path("temperature")).put("observableName", "temperature");
        ((ObjectNode) rule.getBindings().path("temperature")).remove("source");

        assertThrows(IllegalArgumentException.class, () -> service.save(rule));
    }

    @Test
    void rejectsMixedDeviceAndTaskScopedBindingsWithoutCorrelation() {
        ConstraintRuleMapper rules = mock(ConstraintRuleMapper.class);
        ConstraintRuleService service = new ConstraintRuleService(rules, mock(DeviceInstancesMapper.class), new ConstraintExpressionEvaluator());
        ConstraintRule rule = validRule();
        ObjectNode taskState = ((ObjectNode) rule.getBindings()).putObject("taskState");
        taskState.put("bindingType", "OBSERVABLE");
        ObjectNode source = taskState.putObject("source");
        source.put("sourceType", "TASK_LIFECYCLE_STATE");
        source.put("dataType", "STRING");
        source.put("workflowTemplateId", 3);
        source.put("taskId", 9L);

        assertThrows(IllegalArgumentException.class, () -> service.save(rule));
    }
    @Test
    void rejectsBareStringAsAnUndeclaredVariable() {
        ConstraintRuleService service = new ConstraintRuleService(mock(ConstraintRuleMapper.class), mock(DeviceInstancesMapper.class), new ConstraintExpressionEvaluator());
        ConstraintRule rule = validRule();
        rule.setExpression("temperature == RUNNING");

        assertThrows(IllegalArgumentException.class, () -> service.save(rule));
    }

    @Test
    void rejectsBindingsThatAreNotReferencedByTheExpression() {
        ConstraintRuleService service = new ConstraintRuleService(mock(ConstraintRuleMapper.class), mock(DeviceInstancesMapper.class), new ConstraintExpressionEvaluator());
        ConstraintRule rule = validRule();
        rule.setExpression("temperature > 0");

        assertThrows(IllegalArgumentException.class, () -> service.save(rule));
    }

    @Test
    void acceptsModelWideDeviceActionWithoutExplicitInstance() {
        ConstraintRuleMapper rules = mock(ConstraintRuleMapper.class);
        when(rules.insert(any(ConstraintRule.class))).thenReturn(1);
        ConstraintRuleService service = new ConstraintRuleService(rules, mock(DeviceInstancesMapper.class), new ConstraintExpressionEvaluator());
        ConstraintRule rule = validRule();
        ((ObjectNode) rule.getBindings().path("temperature").path("source")).remove("deviceInstanceId");
        ObjectNode action = JsonNodeSupport.objectNode();
        action.put("actionType", "DEVICE_CAPABILITY");
        action.put("deviceModelId", 1);
        action.put("capabilityName", "cool");
        rule.setViolationActions(JsonNodeSupport.MAPPER.createArrayNode().add(action));

        assertDoesNotThrow(() -> service.save(rule));
    }

    @Test
    void rejectsDeviceActionWithoutInstanceWhenObservablesAreInstanceScoped() {
        ConstraintRuleService service = new ConstraintRuleService(mock(ConstraintRuleMapper.class), mock(DeviceInstancesMapper.class), new ConstraintExpressionEvaluator());
        ConstraintRule rule = validRule();
        ObjectNode action = JsonNodeSupport.objectNode();
        action.put("actionType", "DEVICE_CAPABILITY");
        action.put("deviceModelId", 1);
        action.put("capabilityName", "cool");
        rule.setViolationActions(JsonNodeSupport.MAPPER.createArrayNode().add(action));

        assertThrows(IllegalArgumentException.class, () -> service.save(rule));
    }

    @Test
    void rejectsDeleteWhenViolationLogsExist() {
        ViolationLogMapper logs = mock(ViolationLogMapper.class);
        when(logs.selectCount(any())).thenReturn(3L);
        ConstraintRuleService service = new ConstraintRuleService(
                mock(ConstraintRuleMapper.class), mock(DeviceInstancesMapper.class),
                new ConstraintExpressionEvaluator(), event -> { }, logs);

        assertThrows(IllegalStateException.class, () -> service.delete(4L));
    }

    @Test
    void deletesWhenNoViolationLogsExist() {
        ConstraintRuleMapper rules = mock(ConstraintRuleMapper.class);
        ViolationLogMapper logs = mock(ViolationLogMapper.class);
        when(logs.selectCount(any())).thenReturn(0L);
        when(rules.deleteById(org.mockito.ArgumentMatchers.<java.io.Serializable>any())).thenReturn(1);
        ConstraintRuleService service = new ConstraintRuleService(
                rules, mock(DeviceInstancesMapper.class),
                new ConstraintExpressionEvaluator(), event -> { }, logs);

        assertDoesNotThrow(() -> service.delete(4L));
    }

    @Test
    void acceptsTemplateWideAbortWithoutExplicitTask() {
        ConstraintRuleMapper rules = mock(ConstraintRuleMapper.class);
        when(rules.insert(any(ConstraintRule.class))).thenReturn(1);
        ConstraintRuleService service = new ConstraintRuleService(rules, mock(DeviceInstancesMapper.class), new ConstraintExpressionEvaluator());
        ConstraintRule rule = validRule();
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

        assertDoesNotThrow(() -> service.save(rule));
    }

    @Test
    void rejectsAbortWithoutTaskWhenLifecycleIsPinned() {
        ConstraintRuleService service = new ConstraintRuleService(mock(ConstraintRuleMapper.class), mock(DeviceInstancesMapper.class), new ConstraintExpressionEvaluator());
        ConstraintRule rule = validRule();
        ObjectNode bindings = JsonNodeSupport.objectNode();
        ObjectNode taskState = bindings.putObject("taskState");
        taskState.put("bindingType", "OBSERVABLE");
        ObjectNode source = taskState.putObject("source");
        source.put("sourceType", "TASK_LIFECYCLE_STATE");
        source.put("dataType", "STRING");
        source.put("workflowTemplateId", 3);
        source.put("taskId", 9L);
        ObjectNode expected = bindings.putObject("expected");
        expected.put("bindingType", "LITERAL");
        expected.put("value", "FAILED");
        rule.setBindings(bindings);
        rule.setExpression("taskState == expected");
        ObjectNode action = JsonNodeSupport.objectNode();
        action.put("actionType", "SYSTEM");
        action.put("action", "ABORT");
        rule.setViolationActions(JsonNodeSupport.MAPPER.createArrayNode().add(action));

        assertThrows(IllegalArgumentException.class, () -> service.save(rule));
    }

    private ConstraintRule validRule() {
        ObjectNode bindings = JsonNodeSupport.objectNode();
        ObjectNode temperature = bindings.putObject("temperature");
        temperature.put("bindingType", "OBSERVABLE");
        ObjectNode source = temperature.putObject("source");
        source.put("sourceType", "DEVICE_ATTRIBUTE");
        source.put("dataType", "DOUBLE");
        source.put("deviceModelId", 1);
        source.put("deviceInstanceId", 2);
        source.put("targetName", "temperature");
        ObjectNode limit = bindings.putObject("limit");
        limit.put("bindingType", "LITERAL");
        limit.put("value", 80);

        var actions = JsonNodeSupport.MAPPER.getNodeFactory().arrayNode();
        ObjectNode alert = actions.addObject();
        alert.put("actionType", "SYSTEM");
        alert.put("action", "ALERT");
        alert.putNull("targetTaskId");

        ConstraintRule rule = new ConstraintRule();
        rule.setRuleName("温度上限");
        rule.setExpression("temperature > limit");
        rule.setBindings(bindings);
        rule.setViolationActions(actions);
        rule.setIsEnabled(true);
        return rule;
    }
}
