package com.smartlab.management.service.db.constraint;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.constraint.ConstraintRule;
import com.smartlab.management.mapper.constraint.ConstraintRuleMapper;
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
        ConstraintRuleService service = new ConstraintRuleService(rules, mock(DeviceInstancesMapper.class));

        assertDoesNotThrow(() -> service.save(validRule()));
    }

    @Test
    void rejectsLegacyObservableNameBinding() {
        ConstraintRuleMapper rules = mock(ConstraintRuleMapper.class);
        ConstraintRuleService service = new ConstraintRuleService(rules, mock(DeviceInstancesMapper.class));
        ConstraintRule rule = validRule();
        ((ObjectNode) rule.getBindings().path("temperature")).put("observableName", "temperature");
        ((ObjectNode) rule.getBindings().path("temperature")).remove("source");

        assertThrows(IllegalArgumentException.class, () -> service.save(rule));
    }

    @Test
    void rejectsMixedDeviceAndTaskScopedBindingsWithoutCorrelation() {
        ConstraintRuleMapper rules = mock(ConstraintRuleMapper.class);
        ConstraintRuleService service = new ConstraintRuleService(rules, mock(DeviceInstancesMapper.class));
        ConstraintRule rule = validRule();
        ObjectNode taskState = ((ObjectNode) rule.getBindings()).putObject("taskState");
        taskState.put("bindingType", "OBSERVABLE");
        ObjectNode source = taskState.putObject("source");
        source.put("sourceType", "TASK_LIFECYCLE_STATE");
        source.put("dataType", "STRING");
        source.put("taskId", 9L);

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
