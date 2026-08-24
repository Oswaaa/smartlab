package com.smartlab.engine.statemachine;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.observation.ObservableKey;
import com.smartlab.engine.observation.ObservationSnapshot;
import com.smartlab.engine.observation.ObservationStatus;
import com.smartlab.engine.observation.SnapshotOrigin;
import com.smartlab.engine.observation.device.DeviceTwinSnapshot;
import com.smartlab.engine.observation.device.DeviceTwinSnapshotRegistry;
import com.smartlab.engine.observation.statemachine.StateMachineObservationRegistry;
import com.smartlab.engine.statemachine.action.SendStateMachineActionExecutor;
import com.smartlab.engine.statemachine.action.StateMachineActionRegistry;
import com.smartlab.global.protocol.ProtocolDictionaryService;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.service.db.resource.device.DeviceModelService;
import com.smartlab.management.service.db.resource.device.DeviceTwinStateService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StateMachineEngineTest {

    @Test
    void normalStartPersistsSentAndUsesNormalLane() {
        Fixture fixture = fixture();
        List<ObjectNode> emitted = start(fixture, "A-1", "heat");
        assertEquals("SENT", fixture.twinState().getCurrentCmdState());
        assertEquals("A-1", state(emitted, "SENT", "A-1").path("payload").path("messageId").asText());
        verify(fixture.twins()).patchRuntimeState(fixture.twinState(), true, List.of());
    }

    @Test
    void workflowTaskIdsAreNotCopiedIntoCommandStateBroadcast() {
        Fixture fixture = fixture();
        fixture.engine().dispatchSignal(7L, "Interface_workflow_in", "WF_EXECUTE_START",
                Map.of("messageId", "A-1", "capabilityName", "heat", "parameters", Map.of("temperature", 80),
                        "taskId", 9L, "taskStepId", 12L));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(fixture.publisher(), atLeastOnce()).publishEvent(captor.capture());
        List<StateMachineInterfaceSignalEvent> cmdStates = captor.getAllValues().stream()
                .filter(StateMachineInterfaceSignalEvent.class::isInstance)
                .map(StateMachineInterfaceSignalEvent.class::cast)
                .filter(event -> "CMD_STATE".equals(event.signal().path("signalName").asText()))
                .toList();
        assertFalse(cmdStates.isEmpty());
        assertTrue(cmdStates.stream().allMatch(event ->
                event.executionContext() == null
                        || (!event.executionContext().containsKey("taskId")
                        && !event.executionContext().containsKey("taskStepId"))));
        assertTrue(cmdStates.stream().allMatch(event ->
                "A-1".equals(event.signal().path("payload").path("messageId").asText())));
    }
    @Test
    void dispatchDoesNotUseAGlobalSynchronizedMonitor() throws Exception {
        int modifiers = StateMachineEngine.class
                .getMethod("dispatchSignal", Long.class, String.class, String.class, Map.class)
                .getModifiers();

        assertFalse(Modifier.isSynchronized(modifiers));
    }

    @Test
    void intrinsicConstraintUsesMemoryPlanAndStateSnapshotBeforePersistingChange() {
        DeviceModelService models = mock(DeviceModelService.class);
        DeviceTwinStateService twins = mock(DeviceTwinStateService.class);
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        DeviceTwinSnapshotRegistry deviceSnapshots = mock(DeviceTwinSnapshotRegistry.class);
        StateMachineObservationRegistry stateSnapshots = mock(StateMachineObservationRegistry.class);
        IntrinsicConstraintPlanRegistry plans = mock(IntrinsicConstraintPlanRegistry.class);
        DeviceInstances instance = new DeviceInstances();
        instance.setId(7L);
        instance.setDeviceModelId(9L);
        DeviceModels model = model();
        model.setId(9L);
        var exceptionRegion = ((ArrayNode) model.getOpState().path("regions")).addObject();
        exceptionRegion.put("regionName", "Exception");
        exceptionRegion.put("regionType", "EXCEPTION");
        exceptionRegion.put("initialStateName", "");
        ObjectNode overTemperature = exceptionRegion.putArray("states").addObject()
                .put("stateName", "OVER_TEMPERATURE");
        addStatusOnEntry(overTemperature, "OP_STATE");
        var definitions = JsonNodeSupport.arrayNode();
        definitions.addObject().put("objectAttributeName", "temperature").put("operator", ">")
                .put("boundaryValue", 200).put("violationStateName", "OVER_TEMPERATURE");
        model.setIntrinsicConstraint(definitions);
        var constraint = new IntrinsicConstraintPlanRegistry.CompiledIntrinsicConstraint(
                "temperature", ">", 200, "OVER_TEMPERATURE", "Exception");
        var plan = new IntrinsicConstraintPlanRegistry.IntrinsicConstraintPlan(
                instance, model, List.of(constraint), Map.of("OVER_TEMPERATURE", "Exception"));
        when(plans.require(7L)).thenReturn(plan);
        when(stateSnapshots.read(any(ObservableKey.class))).thenAnswer(invocation -> {
            ObservableKey key = invocation.getArgument(0);
            var value = key.regionName() == null
                    ? JsonNodeSupport.MAPPER.getNodeFactory().textNode("IDLE")
                    : "operatingMode".equals(key.regionName())
                    ? JsonNodeSupport.arrayNode().add("IDLE") : JsonNodeSupport.arrayNode();
            return new ObservationSnapshot(key, value, Instant.now(), Instant.now(), 1,
                    ObservationStatus.VALID, SnapshotOrigin.LIVE);
        });
        when(twins.addExceptionState(7L, "Exception", "OVER_TEMPERATURE")).thenReturn(true);
        ProtocolDictionaryService protocol = new ProtocolDictionaryService();
        StateMachineActionRegistry registry = new StateMachineActionRegistry(
                List.of(new SendStateMachineActionExecutor(publisher, protocol)));
        StateMachineEngine engine = new StateMachineEngine(models, twins, instances, registry, publisher,
                protocol, null, deviceSnapshots, new StateMachineInterfaceOutputDispatcher(), plans, stateSnapshots);
        DeviceTwinSnapshot attributes = new DeviceTwinSnapshot(7L, 9L,
                JsonNodeSupport.objectNode().put("temperature", 205), "ONLINE",
                Instant.now(), Instant.now(), 2, ObservationStatus.VALID, SnapshotOrigin.LIVE);

        List<ObjectNode> emitted = engine.evaluateIntrinsicConstraints(attributes);

        assertEquals("OVER_TEMPERATURE",
                emitted.getFirst().path("payload").path("state").get(0).asText());
        verify(twins).addExceptionState(7L, "Exception", "OVER_TEMPERATURE");
        verify(twins, never()).getByInstanceId(anyLong());
        verifyNoInteractions(models, instances);
    }

    @Test
    void attachedAbortUsesDistinctMessageAndDefaultsNormalToAbortedOnCompletion() {
        Fixture fixture = fixture();
        startAndRun(fixture, "A-1");
        List<ObjectNode> started = fixture.engine().dispatchSignal(7L, "Interface_workflow_in", "WF_EXECUTE_ABORT", Map.of());
        ObjectNode abortSent = started.stream().filter(signal -> "CMD_STATE".equals(signal.path("signalName").asText())).filter(signal -> "SENT".equals(signal.path("payload").path("stateName").asText())).filter(signal -> !"A-1".equals(signal.path("payload").path("messageId").asText())).findFirst().orElseThrow();
        String abortMessageId = abortSent.path("payload").path("messageId").asText();
        assertFalse(abortMessageId.isBlank());
        assertNotEquals("A-1", abortMessageId);
        assertEquals("ABORTING", fixture.twinState().getCurrentCmdState());
        assertEquals("CMD_ABORT", signal(started, "CMD_ABORT").path("signalName").asText());

        List<ObjectNode> second = fixture.engine().dispatchSignal(7L, "Interface_workflow_in", "WF_EXECUTE_ABORT", Map.of());
        assertEquals(List.of(), second);
        assertEquals("ABORTING", fixture.twinState().getCurrentCmdState());

        List<ObjectNode> completed = fixture.engine().dispatchAdapterEvent(7L, "STOP_DONE",
                JsonNodeSupport.objectNode().put("messageId", abortMessageId));
        assertNotNull(state(completed, "COMPLETED", abortMessageId));
        assertNotNull(state(completed, "ABORTED", "A-1"));
        assertEquals("IDLE", fixture.twinState().getCurrentCmdState());
    }

    @Test
    void independentTerminationPreemptsNormalAndUsesItsOwnMessageId() {
        Fixture fixture = fixture();
        startAndRun(fixture, "A-1");
        List<ObjectNode> started = fixture.engine().dispatchSignal(7L, "Interface_constraint_in", "CONSTRAINT_EXECUTE",
                Map.of("deviceInstanceId", 7L, "capabilityName", "emergencyStop", "messageId", "C-1", "parameters", Map.of()));
        assertNotNull(state(started, "SENT", "C-1"));
        assertEquals("RUNNING", fixture.twinState().getCurrentCmdState());
        assertEquals("CMD_START", signal(started, "CMD_START").path("signalName").asText());

        List<ObjectNode> completed = fixture.engine().dispatchAdapterEvent(7L, "EMERGENCY_DONE",
                JsonNodeSupport.objectNode().put("messageId", "C-1"));
        assertNotNull(state(completed, "COMPLETED", "C-1"));
        assertNotNull(state(completed, "ABORTED", "A-1"));
    }

    @Test
    void unknownLifecycleEventIsIgnoredWithoutOperationTransition() {
        Fixture fixture = fixture();
        start(fixture, "A-1", "heat");
        List<ObjectNode> emitted = fixture.engine().dispatchAdapterEvent(7L, "HEAT_STARTED",
                JsonNodeSupport.objectNode().put("messageId", "late-message"));
        assertEquals(List.of(), emitted);
        assertEquals("IDLE", fixture.twinState().getCurrentOpState().path("operatingMode").get(0).asText());
        assertEquals("SENT", fixture.twinState().getCurrentCmdState());
    }

    @Test
    void commandLifecycleEventWithoutMessageIdIsRejected() {
        Fixture fixture = fixture();
        start(fixture, "A-1", "heat");

        assertThrows(IllegalArgumentException.class, () -> fixture.engine().dispatchAdapterEvent(
                7L, "HEAT_STARTED", JsonNodeSupport.objectNode()));
        assertEquals("SENT", fixture.twinState().getCurrentCmdState());
    }

    @Test
    void abortScopeMustContainAssociatedNormalCapability() {
        Fixture fixture = fixture();
        ((ObjectNode) fixture.model().getCapabilities().get(1)).putArray("scope").removeAll();
        startAndRun(fixture, "A-1");
        assertThrows(IllegalStateException.class, () -> fixture.engine().dispatchSignal(
                7L, "Interface_workflow_in", "WF_EXECUTE_ABORT", Map.of()));
        assertEquals("RUNNING", fixture.twinState().getCurrentCmdState());
    }

    @Test
    void failedAbortPublishRollsBackAttachedLaneAndDoesNotAnnounceAborting() {
        Fixture fixture = fixture();
        startAndRun(fixture, "A-1");
        doThrow(new IllegalStateException("mqtt down")).when(fixture.publisher()).publishEvent(any(StateMachineSendActionEvent.class));
        assertThrows(IllegalStateException.class, () -> fixture.engine().dispatchSignal(
                7L, "Interface_workflow_in", "WF_EXECUTE_ABORT", Map.of()));
        assertEquals("RUNNING", fixture.twinState().getCurrentCmdState());
        reset(fixture.publisher());
        assertDoesNotThrow(() -> fixture.engine().dispatchSignal(7L, "Interface_workflow_in", "WF_EXECUTE_ABORT", Map.of()));
    }

    @Test
    void adapterEventAdvancesNormalAndOperationStateTogether() {
        Fixture fixture = fixture();
        start(fixture, "A-1", "heat");

        List<ObjectNode> emitted = fixture.engine().dispatchAdapterEvent(7L, "HEAT_STARTED",
                JsonNodeSupport.objectNode().put("messageId", "A-1"));

        assertNotNull(state(emitted, "RUNNING", "A-1"));
        assertEquals("RUNNING", fixture.twinState().getCurrentCmdState());
        assertEquals("HEATING", fixture.twinState().getCurrentOpState().path("operatingMode").get(0).asText());
        assertEquals("HEATING", signal(emitted, "OP_STATE").path("payload").path("state").get(0).asText());
    }

    @Test
    void activeTerminationLaneBlocksNewNormalUntilItFinishes() {
        Fixture fixture = fixture();
        List<ObjectNode> termination = fixture.engine().dispatchSignal(7L, "Interface_constraint_in", "CONSTRAINT_EXECUTE",
                Map.of("deviceInstanceId", 7L, "capabilityName", "emergencyStop", "messageId", "C-1", "parameters", Map.of()));

        assertNotNull(state(termination, "SENT", "C-1"));
        assertEquals("IDLE", fixture.twinState().getCurrentCmdState());
        assertEquals(List.of(), start(fixture, "A-1", "heat"));

        fixture.engine().dispatchAdapterEvent(7L, "EMERGENCY_DONE",
                JsonNodeSupport.objectNode().put("messageId", "C-1"));
        assertFalse(start(fixture, "A-2", "heat").isEmpty());
    }

    @Test
    void explicitNormalTerminalResultWinsAndDestroysAttachedAbortRuntime() {
        Fixture fixture = fixture();
        startAndRun(fixture, "A-1");
        List<ObjectNode> abortStarted = fixture.engine().dispatchSignal(
                7L, "Interface_workflow_in", "WF_EXECUTE_ABORT", Map.of());
        String abortMessageId = abortStarted.stream()
                .filter(signal -> stateName(signal).equals("SENT"))
                .map(signal -> signal.path("payload").path("messageId").asText())
                .filter(messageId -> !"A-1".equals(messageId))
                .findFirst().orElseThrow();

        List<ObjectNode> normalCompleted = fixture.engine().dispatchAdapterEvent(7L, "HEAT_DONE",
                JsonNodeSupport.objectNode().put("messageId", "A-1"));
        assertNotNull(state(normalCompleted, "COMPLETED", "A-1"));
        assertEquals("IDLE", fixture.twinState().getCurrentCmdState());

        List<ObjectNode> lateAbort = fixture.engine().dispatchAdapterEvent(7L, "STOP_DONE",
                JsonNodeSupport.objectNode().put("messageId", abortMessageId));
        assertEquals(List.of(), lateAbort);
    }

    @Test
    void failedTerminationDoesNotDefaultNormalToAborted() {
        Fixture fixture = fixture();
        startAndRun(fixture, "A-1");
        fixture.engine().dispatchSignal(7L, "Interface_constraint_in", "CONSTRAINT_EXECUTE",
                Map.of("deviceInstanceId", 7L, "capabilityName", "emergencyStop", "messageId", "C-1", "parameters", Map.of()));

        List<ObjectNode> failed = fixture.engine().dispatchAdapterEvent(7L, "EMERGENCY_FAILED",
                JsonNodeSupport.objectNode().put("messageId", "C-1"));

        assertNotNull(state(failed, "FAILED", "C-1"));
        assertEquals("RUNNING", fixture.twinState().getCurrentCmdState());
        assertNull(state(failed, "ABORTED", "A-1"));
    }

    private List<ObjectNode> start(Fixture fixture, String messageId, String capability) {
        return fixture.engine().dispatchSignal(7L, "Interface_workflow_in", "WF_EXECUTE_START",
                Map.of("messageId", messageId, "capabilityName", capability, "parameters", Map.of("temperature", 80)));
    }

    private void startAndRun(Fixture fixture, String messageId) {
        start(fixture, messageId, "heat");
        fixture.engine().dispatchAdapterEvent(7L, "HEAT_STARTED", JsonNodeSupport.objectNode().put("messageId", messageId));
        assertEquals("RUNNING", fixture.twinState().getCurrentCmdState());
    }

    private Fixture fixture() {
        DeviceModelService models = mock(DeviceModelService.class);
        DeviceTwinStateService twins = mock(DeviceTwinStateService.class);
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        DeviceInstances instance = new DeviceInstances(); instance.setId(7L); instance.setDeviceModelId(9L);
        when(instances.selectById(7L)).thenReturn(instance);
        DeviceModels model = model(); model.setId(9L); when(models.getById("9")).thenReturn(model);
        DeviceTwinStates twin = new DeviceTwinStates(); twin.setInstanceId(7L); twin.setCurrentCmdState("IDLE");
        ObjectNode currentOpState = JsonNodeSupport.objectNode();
        currentOpState.putArray("operatingMode").add("IDLE");
        twin.setCurrentOpState(currentOpState);
        when(twins.getByInstanceId(7L)).thenReturn(twin);
        doAnswer(invocation -> invocation.getArgument(0)).when(twins).save(any(DeviceTwinStates.class));
        ProtocolDictionaryService protocol = new ProtocolDictionaryService();
        StateMachineActionRegistry registry = new StateMachineActionRegistry(List.of(new SendStateMachineActionExecutor(publisher, protocol)));
        return new Fixture(new StateMachineEngine(models, twins, instances, registry, publisher, protocol), twin, model, twins, publisher);
    }

    private DeviceModels model() {
        DeviceModels model = new DeviceModels();
        ArrayNode capabilities = JsonNodeSupport.arrayNode();
        capabilities.addObject().put("capabilityName", "heat").put("adapterCommandName", "heat_cmd")
                .put("abortCapabilityName", "stopHeat").put("isAbort", false);
        capabilities.addObject().put("capabilityName", "stopHeat").put("adapterCommandName", "stop_heat_cmd")
                .putNull("abortCapabilityName").put("isAbort", true).putArray("scope").add("heat");
        capabilities.addObject().put("capabilityName", "emergencyStop").put("adapterCommandName", "emergency_stop_cmd")
                .putNull("abortCapabilityName").put("isAbort", true).putArray("scope").add("heat");
        model.setCapabilities(capabilities);
        ArrayNode interfaces = JsonNodeSupport.arrayNode();
        interfaces.addObject().put("name", "Interface_workflow_in").put("direction", "IN").put("interfaceType", "WORKFLOW").putArray("allowedSignals").add("WF_EXECUTE_START").add("WF_EXECUTE_ABORT");
        interfaces.addObject().put("name", "Interface_constraint_in").put("direction", "IN").put("interfaceType", "CONSTRAINT").putArray("allowedSignals").add("CONSTRAINT_EXECUTE").add("CONSTRAINT_ABORT");
        interfaces.addObject().put("name", "Interface_adapter_in").put("direction", "IN").put("interfaceType", "ADAPTER").putArray("allowedSignals").add("HEAT_STARTED").add("HEAT_DONE").add("STOP_DONE").add("EMERGENCY_DONE").add("EMERGENCY_FAILED");
        interfaces.addObject().put("name", "Interface_adapter_out").put("direction", "OUT").put("interfaceType", "ADAPTER").putArray("allowedSignals").add("CMD_START").add("CMD_ABORT");
        interfaces.addObject().put("name", "Interface_state_out").put("direction", "OUT").put("interfaceType", "STATE").putArray("allowedSignals").add("CMD_STATE").add("OP_STATE");
        model.setStateMachineInterfaces(interfaces);
        ObjectNode cmd = JsonNodeSupport.objectNode(); cmd.put("initialStateName", "IDLE"); ArrayNode states = cmd.putArray("states");
        for (String state : List.of("IDLE", "SENT", "RUNNING", "COMPLETED", "FAILED", "ABORTING", "ABORTED")) {
            ObjectNode stateNode = states.addObject().put("stateName", state);
            addStatusOnEntry(stateNode, "CMD_STATE");
        }
        model.setCmdState(cmd);
        ObjectNode op = JsonNodeSupport.objectNode(); ObjectNode region = op.putArray("regions").addObject(); region.put("regionName", "operatingMode").put("regionType", "OPERATIONAL").put("initialStateName", "IDLE");
        ObjectNode idle = region.putArray("states").addObject().put("stateName", "IDLE"); addStatusOnEntry(idle, "OP_STATE");
        ObjectNode heating = ((ArrayNode) region.path("states")).addObject().put("stateName", "HEATING"); addStatusOnEntry(heating, "OP_STATE"); model.setOpState(op);
        ArrayNode transitions = JsonNodeSupport.arrayNode(); addTransition(transitions, "SENT", "RUNNING", "HEAT_STARTED", "CMD"); addTransition(transitions, "RUNNING", "COMPLETED", "HEAT_DONE", "CMD"); addTransition(transitions, "ABORTING", "COMPLETED", "HEAT_DONE", "CMD"); addTransition(transitions, "SENT", "COMPLETED", "STOP_DONE", "CMD"); addTransition(transitions, "SENT", "COMPLETED", "EMERGENCY_DONE", "CMD"); addTransition(transitions, "SENT", "FAILED", "EMERGENCY_FAILED", "CMD");
        ObjectNode opTransition = transitions.addObject(); opTransition.put("stateSpace", "OP").put("regionName", "operatingMode").put("fromStateName", "IDLE").put("toStateName", "HEATING"); opTransition.putObject("trigger").put("interfaceName", "Interface_adapter_in").put("signalName", "HEAT_STARTED"); opTransition.putArray("actions");
        model.setStateTransitions(transitions); return model;
    }

    private void addTransition(ArrayNode transitions, String from, String to, String event, String space) { ObjectNode transition = transitions.addObject(); transition.put("stateSpace", space).put("fromStateName", from).put("toStateName", to); transition.putObject("trigger").put("interfaceName", "Interface_adapter_in").put("signalName", event); transition.putArray("actions"); }
    private void addStatusOnEntry(ObjectNode state, String signalName) { state.putArray("onEntry").addObject().put("actionName", "SEND").putObject("payload").put("interfaceName", "Interface_state_out").put("signalName", signalName); }
    private ObjectNode signal(List<ObjectNode> signals, String name) { return signals.stream().filter(signal -> name.equals(signal.path("signalName").asText())).findFirst().orElseThrow(); }
    private String stateName(ObjectNode signal) { return signal.path("payload").path("stateName").asText(); }
    private ObjectNode state(List<ObjectNode> signals, String state, String messageId) { return signals.stream().filter(signal -> "CMD_STATE".equals(signal.path("signalName").asText())).filter(signal -> state.equals(signal.path("payload").path("stateName").asText())).filter(signal -> messageId.equals(signal.path("payload").path("messageId").asText())).findFirst().orElse(null); }
    private record Fixture(StateMachineEngine engine, DeviceTwinStates twinState, DeviceModels model, DeviceTwinStateService twins, ApplicationEventPublisher publisher) { }
}
