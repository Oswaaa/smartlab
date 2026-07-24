package com.smartlab.engine.constraint;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.StateMachineEngine;
import com.smartlab.engine.workflow.WorkflowTaskControlService;
import com.smartlab.global.event.ConstraintAlertEvent;
import com.smartlab.global.event.DeviceTelemetryUpdatedEvent;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.constraint.ConstraintRule;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.management.service.db.constraint.ConstraintRuleService;
import com.smartlab.management.service.db.constraint.ViolationLogService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConstraintEngineTest {

    @Test
    void evaluatesInlineObservableSourceFromTelemetry() {
        ConstraintRule rule = rule();
        ConstraintRuleService rules = mock(ConstraintRuleService.class);
        when(rules.list(Boolean.TRUE)).thenReturn(List.of(rule));
        ViolationLogService logs = mock(ViolationLogService.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        ConstraintEngine engine = new ConstraintEngine(rules, logs, mock(WorkflowTaskControlService.class),
                mock(StateMachineEngine.class), mock(DeviceInstancesMapper.class), mock(DeviceModelsMapper.class),
                mock(WorkflowService.class), new ConstraintExpressionEvaluator(), events);
        ObjectNode attributes = JsonNodeSupport.objectNode();
        attributes.put("temperature", 82);

        engine.observeTelemetry(new DeviceTelemetryUpdatedEvent(2L, 1L, attributes, Instant.now()));

        verify(events).publishEvent(any(ConstraintAlertEvent.class));
        verify(logs).save(any());
    }

    private ConstraintRule rule() {
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
        rule.setId(1L);
        rule.setRuleName("温度过高");
        rule.setExpression("temperature > limit");
        rule.setBindings(bindings);
        rule.setViolationActions(actions);
        return rule;
    }
}
