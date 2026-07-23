package com.smartlab.management.service.db.constraint;

import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.constraint.ConstraintRule;
import com.smartlab.management.mapper.constraint.ConstraintRuleMapper;
import com.smartlab.management.entity.resource.device.DeviceInstanceLifecycle;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConstraintRuleServiceTest {

    private final DeviceInstancesMapper deviceInstancesMapper = mock(DeviceInstancesMapper.class);
    private final ConstraintRuleService service = new ConstraintRuleService(
            mock(ConstraintRuleMapper.class), new com.smartlab.global.protocol.ProtocolDictionaryService(), deviceInstancesMapper);

    @Test
    void rejectsLegacyOperatorAliases() {
        assertThrows(IllegalArgumentException.class, () -> service.save(validRule("GT", "80")));
    }

    @Test
    void validatesBetweenAndInOperandsAsJsonArrays() {
        assertDoesNotThrow(() -> service.save(validRule("BETWEEN", "[20,80]")));
        assertDoesNotThrow(() -> service.save(validRule("IN", "[\"IDLE\",\"RUNNING\"]")));
        assertThrows(IllegalArgumentException.class, () -> service.save(validRule("BETWEEN", "[20]")));
        assertThrows(IllegalArgumentException.class, () -> service.save(validRule("BETWEEN", "[80,20]")));
        assertThrows(IllegalArgumentException.class, () -> service.save(validRule("IN", "[]")));
        assertThrows(IllegalArgumentException.class, () -> service.save(validRule("IN", "IDLE,RUNNING")));
    }

    @Test
    void rejectsRetiredDeviceCapabilityAction() {
        DeviceInstances retired = new DeviceInstances();
        retired.setId(9L);
        retired.setLifecycleStatus(DeviceInstanceLifecycle.RETIRED);
        when(deviceInstancesMapper.selectById(9L)).thenReturn(retired);

        ConstraintRule rule = validRule(">", "80");
        var actions = JsonNodeSupport.arrayNode();
        actions.addObject()
                .put("actionType", "DEVICE_CAPABILITY")
                .put("deviceInstanceId", 9L)
                .put("capabilityName", "stop");
        rule.setViolationActions(actions);

        assertThrows(IllegalStateException.class, () -> service.save(rule));
    }
    private ConstraintRule validRule(String operator, String threshold) {
        ConstraintRule rule = new ConstraintRule();
        rule.setRuleName("temperature guard");
        rule.setSourceType("DEVICE_ATTRIBUTE");
        rule.setObjectEndpoint("device/1/temperature");
        rule.setObjectName("temperature");
        rule.setOperator(operator);
        rule.setThreshold(threshold);
        var actions = JsonNodeSupport.arrayNode();
        actions.addObject().put("actionType", "SYSTEM").put("action", "HALT");
        rule.setViolationActions(actions);
        return rule;
    }
}
