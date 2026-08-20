package com.smartlab.engine.constraint;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.observation.ObservableKey;
import com.smartlab.engine.observation.ObservationSample;
import com.smartlab.engine.observation.ObservationSnapshot;
import com.smartlab.engine.observation.ObservationStatus;
import com.smartlab.engine.observation.SnapshotOrigin;
import com.smartlab.engine.statemachine.StateMachineCommandPort;
import com.smartlab.engine.workflow.WorkflowTaskControlService;
import com.smartlab.global.event.ConstraintAlertEvent;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.constraint.ConstraintRule;
import com.smartlab.management.entity.constraint.ViolationLog;
import com.smartlab.management.service.db.constraint.ViolationLogService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConstraintEngineTest {

    @Test
    void doesNotExposeLegacyEventPayloadEvaluationEntrypoints() {
        Set<String> removedEntrypoints = Set.of(
                "observeTelemetry", "observeStateMachine", "observeWorkflowNode", "observeTask",
                "evaluateSustainedConstraints");

        assertFalse(Arrays.stream(ConstraintEngine.class.getMethods())
                .anyMatch(method -> removedEntrypoints.contains(method.getName())));
    }

    @Test
    void evaluatesOnlyFromObservationSnapshots() {
        ConstraintRule rule = rule();
        ViolationLogService logs = mock(ViolationLogService.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        ConstraintEngine engine = engine(logs, mock(StateMachineCommandPort.class), events);
        ObservableKey key = temperatureKey();
        RuntimeConstraint runtime = runtime(rule, key, null);

        engine.evaluate(runtime, Map.of(key, snapshot(key, 82, 1)));

        verify(events).publishEvent(any(ConstraintAlertEvent.class));
        verify(logs).save(any());
    }

    @Test
    void evaluatesTaskConstraintOnlyInsideOwningTaskScope() {
        ConstraintRule rule = rule();
        rule.setId(-7_000_001L);
        ViolationLogService logs = mock(ViolationLogService.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        ConstraintEngine engine = engine(logs, mock(StateMachineCommandPort.class), events);
        ObservableKey key = temperatureKey();
        RuntimeConstraint runtime = runtime(rule, key, 7L);

        engine.evaluate(runtime, Map.of(key, snapshot(key, 82, 1)));

        ArgumentCaptor<ViolationLog> logCaptor = ArgumentCaptor.forClass(ViolationLog.class);
        verify(logs).save(logCaptor.capture());
        assertNull(logCaptor.getValue().getConstraintRuleId());
        assertEquals("TASK_CONSTRAINT", logCaptor.getValue().getConstraintType());
        assertEquals(7L, logCaptor.getValue().getTaskId());
    }

    @Test
    void persistsStructuredViolationEvidenceFromTheRuntimeConstraint() {
        ConstraintRule rule = rule();
        ViolationLogService logs = mock(ViolationLogService.class);
        ConstraintScopeSnapshotReader snapshots = mock(ConstraintScopeSnapshotReader.class);
        ObjectNode fullScope = JsonNodeSupport.objectNode();
        fullScope.putObject("tasks").putObject("7").put("taskStatus", "RUNNING");
        fullScope.putObject("devices");
        when(snapshots.snapshot(any(), any())).thenReturn(fullScope);
        ConstraintEngine engine = engine(logs, mock(StateMachineCommandPort.class),
                mock(ApplicationEventPublisher.class), snapshots);
        ObservableKey key = temperatureKey();

        engine.evaluate(runtime(rule, key, 7L), Map.of(key, snapshot(key, 82, 1)));

        ArgumentCaptor<ViolationLog> captor = ArgumentCaptor.forClass(ViolationLog.class);
        verify(logs).save(captor.capture());
        ViolationLog saved = captor.getValue();
        assertEquals("global_1_temperature",
                saved.getObservedVariable().path("temperature").path("observableName").asText());
        assertFalse(saved.getObservedVariable().has("limit"));
        assertEquals("temperature > limit", saved.getExpression().asText());
        assertEquals(82, saved.getActualValue().path("temperature").asInt());
        assertEquals(80, saved.getActualValue().path("limit").asInt());
        assertEquals("RUNNING", saved.getVariableSnapshot().path("tasks")
                .path("7").path("taskStatus").asText());
    }

    @Test
    void preservesSnapshotFailureEvidenceAndWritesOneRowPerAction() {
        ConstraintRule rule = rule();
        ObjectNode secondAction = JsonNodeSupport.objectNode();
        secondAction.put("actionType", "SYSTEM");
        secondAction.put("action", "ALERT");
        rule.setViolationActions(((com.fasterxml.jackson.databind.node.ArrayNode) rule.getViolationActions())
                .add(secondAction));
        ViolationLogService logs = mock(ViolationLogService.class);
        ConstraintScopeSnapshotReader snapshots = mock(ConstraintScopeSnapshotReader.class);
        when(snapshots.snapshot(any(), any())).thenThrow(new IllegalStateException("snapshot unavailable"));
        ConstraintEngine engine = engine(logs, mock(StateMachineCommandPort.class),
                mock(ApplicationEventPublisher.class), snapshots);
        ObservableKey key = temperatureKey();

        engine.evaluate(runtime(rule, key, null), Map.of(key, snapshot(key, 82, 1)));

        ArgumentCaptor<ViolationLog> captor = ArgumentCaptor.forClass(ViolationLog.class);
        verify(logs, org.mockito.Mockito.times(2)).save(captor.capture());
        captor.getAllValues().forEach(saved -> assertEquals("snapshot unavailable",
                saved.getVariableSnapshot().path("snapshotError").asText()));
    }

    @Test
    void evaluatesTemporalFunctionsFromObservationHistory() {
        ConstraintRule rule = rule();
        rule.setExpression("delta(temperature, 30) >= 10");
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        ConstraintEngine engine = engine(mock(ViolationLogService.class), mock(StateMachineCommandPort.class), events);
        ObservableKey key = temperatureKey();
        Instant now = Instant.now();
        RuntimeConstraint runtime = new RuntimeConstraint(new RuntimeConstraintKey("GLOBAL", "1", "device:2", "v1"),
                rule, Map.of("temperature", key), null, null, 2L);
        ObservationSnapshot latest = new ObservationSnapshot(key, JsonNodeSupport.toNode(92), now, now, 2,
                ObservationStatus.VALID, SnapshotOrigin.LIVE);

        engine.evaluate(runtime, Map.of(key, latest), Map.of(key, List.of(
                new ObservationSample(key, JsonNodeSupport.toNode(80), now.minusSeconds(10), 1),
                new ObservationSample(key, JsonNodeSupport.toNode(92), now, 2))));

        verify(events).publishEvent(any(ConstraintAlertEvent.class));
    }

    @Test
    void routesDeviceViolationActionsThroughStateMachineCommandPort() {
        ConstraintRule rule = rule();
        ObjectNode action = JsonNodeSupport.objectNode();
        action.put("actionType", "DEVICE_CAPABILITY");
        action.put("deviceInstanceId", 2);
        action.put("capabilityName", "EmergencyStop");
        action.set("parameters", JsonNodeSupport.objectNode().put("reason", "temperature"));
        rule.setViolationActions(JsonNodeSupport.MAPPER.createArrayNode().add(action));
        StateMachineCommandPort commands = mock(StateMachineCommandPort.class);
        ConstraintEngine engine = engine(mock(ViolationLogService.class), commands,
                mock(ApplicationEventPublisher.class));
        ObservableKey key = temperatureKey();

        engine.evaluate(runtime(rule, key, null), Map.of(key, snapshot(key, 82, 1)));

        verify(commands).executeConstraintCapability(org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq("EmergencyStop"),
                org.mockito.ArgumentMatchers.argThat(parameters -> "temperature".equals(parameters.get("reason"))));
    }

    @Test
    void deviceActionWithoutInstanceDoesNotBorrowScope() {
        ConstraintRule rule = rule();
        ObjectNode action = JsonNodeSupport.objectNode();
        action.put("actionType", "DEVICE_CAPABILITY");
        action.put("capabilityName", "EmergencyStop");
        rule.setViolationActions(JsonNodeSupport.MAPPER.createArrayNode().add(action));
        StateMachineCommandPort commands = mock(StateMachineCommandPort.class);
        ConstraintEngine engine = engine(mock(ViolationLogService.class), commands,
                mock(ApplicationEventPublisher.class));
        ObservableKey key = temperatureKey();

        engine.evaluate(runtime(rule, key, null), Map.of(key, snapshot(key, 82, 1)));

        org.mockito.Mockito.verifyNoInteractions(commands);
    }

    @Test
    void violationSnapshotIncludesTemporalFunctionValue() {
        ConstraintRule rule = rule();
        rule.setExpression("rate(temperature, 30) > limit");
        ((ObjectNode) rule.getBindings().path("limit")).put("value", 0);
        Instant now = Instant.now();
        ObservableKey key = temperatureKey();
        ViolationLogService logs = mock(ViolationLogService.class);
        ConstraintEngine engine = engine(logs, mock(StateMachineCommandPort.class),
                mock(ApplicationEventPublisher.class));

        engine.evaluate(runtime(rule, key, null), Map.of(key, snapshot(key, 20, 2)), Map.of(key, List.of(
                new ObservationSample(key, JsonNodeSupport.toNode(10), now.minusSeconds(10), 1),
                new ObservationSample(key, JsonNodeSupport.toNode(20), now, 2))));

        ArgumentCaptor<ViolationLog> captor = ArgumentCaptor.forClass(ViolationLog.class);
        verify(logs).save(captor.capture());
        assertEquals(20, captor.getValue().getActualValue().path("temperature").asInt());
        assertEquals(0, captor.getValue().getActualValue().path("limit").asInt());
        assertEquals(1.0, captor.getValue().getActualValue().path("rate(temperature, 30)").asDouble(), 0.01);
    }

    private ConstraintEngine engine(ViolationLogService logs, StateMachineCommandPort commands,
                                    ApplicationEventPublisher events) {
        return engine(logs, commands, events, mock(ConstraintScopeSnapshotReader.class));
    }

    private ConstraintEngine engine(ViolationLogService logs, StateMachineCommandPort commands,
                                    ApplicationEventPublisher events,
                                    ConstraintScopeSnapshotReader snapshots) {
        return new ConstraintEngine(logs, mock(WorkflowTaskControlService.class), commands,
                new ConstraintExpressionEvaluator(), events, snapshots);
    }

    private ObservableKey temperatureKey() {
        return new ObservableKey(com.smartlab.global.contract.ObservableObjectType.DEVICE_ATTRIBUTE,
                2L, null, null, null, null, "temperature", null);
    }

    private RuntimeConstraint runtime(ConstraintRule rule, ObservableKey key, Long taskId) {
        String origin = taskId == null ? "GLOBAL" : "TASK";
        String scope = taskId == null ? "device:2" : "task:" + taskId;
        ObjectNode observedVariables = JsonNodeSupport.objectNode();
        observedVariables.set("temperature", JsonNodeSupport.objectNode()
                .put("bindingType", "OBSERVABLE")
                .put("observableName", "global_1_temperature"));
        return new RuntimeConstraint(new RuntimeConstraintKey(origin, String.valueOf(rule.getId()), scope, "v1"),
                rule, Map.of("temperature", key), taskId, null, 2L, observedVariables);
    }

    private ObservationSnapshot snapshot(ObservableKey key, int value, long revision) {
        Instant now = Instant.now();
        return new ObservationSnapshot(key, JsonNodeSupport.toNode(value), now, now, revision,
                ObservationStatus.VALID, SnapshotOrigin.LIVE);
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
