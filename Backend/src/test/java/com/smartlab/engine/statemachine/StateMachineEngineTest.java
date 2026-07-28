package com.smartlab.engine.statemachine;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StateMachineEngineTest {

    @Test
    void workflowStartUsesContractTransitionAndEmitsCmdStart() {
        Fixture fixture = fixture();

        List<ObjectNode> emitted = start(fixture, "message-contract-start");

        assertEquals("CMD_START", signal(emitted, "CMD_START").path("signalName").asText());
        assertEquals("SENT", fixture.twinState().getCurrentCmdState());
    }

    @Test
    void constraintAbortUsesContractTransitionOnlyFromSentOrRunning() {
        Fixture sentFixture = fixture();
        start(sentFixture, "message-contract-abort");

        List<ObjectNode> emitted = sentFixture.engine().dispatchSignal(
                7L, "Interface_constraint_in", "CONSTRAINT_ABORT", Map.of());

        assertEquals("CMD_ABORT", signal(emitted, "CMD_ABORT").path("signalName").asText());
        assertEquals("ABORTING", sentFixture.twinState().getCurrentCmdState());
    }

    @Test
    void sameSignalOnDifferentDeclaredInterfaceDoesNotTriggerSystemTransition() {
        Fixture fixture = fixture();

        List<ObjectNode> emitted = fixture.engine().dispatchSignal(
                7L, "Interface_control_in", "WF_EXECUTE_START",
                Map.of("messageId", "message-wrong-interface", "capabilityName", "heat",
                        "parameters", Map.of("temperature", 80)));

        assertEquals(List.of(), emitted);
        assertEquals("IDLE", fixture.twinState().getCurrentCmdState());
    }

    @Test
    void rejectedRunningStartPreservesActiveCommandCorrelation() {
        Fixture fixture = fixture();
        startAndRun(fixture, "message-1");

        List<ObjectNode> rejected = start(fixture, "message-2");

        assertEquals(List.of(), rejected);
        assertEquals("RUNNING", fixture.twinState().getCurrentCmdState());
        List<ObjectNode> associated = assertDoesNotThrow(() -> fixture.engine().dispatchAdapterEvent(
                7L, "HEAT_STARTED", JsonNodeSupport.objectNode().put("messageId", "message-1")));
        assertEquals(List.of(), associated);
        assertEquals("RUNNING", fixture.twinState().getCurrentCmdState());
    }

    @Test
    void idleSystemAbortsWithoutMessageIdReturnNoTransition() {
        for (Map.Entry<String, String> abort : List.of(
                Map.entry("Interface_constraint_in", "CONSTRAINT_ABORT"),
                Map.entry("Interface_control_in", "MANUAL_EXECUTE_ABORT"))) {
            Fixture fixture = fixture();

            List<ObjectNode> emitted = assertDoesNotThrow(() -> fixture.engine().dispatchSignal(
                    7L, abort.getKey(), abort.getValue(), Map.of()));

            assertEquals(List.of(), emitted);
            assertEquals("IDLE", fixture.twinState().getCurrentCmdState());
        }
    }

    @Test
    void workflowStartResolvesCapabilityAndPublishesFinalSentState() {
        Fixture fixture = fixture();

        List<ObjectNode> emitted = start(fixture, "message-1");

        ObjectNode start = signal(emitted, "CMD_START");
        assertEquals("heat_cmd", start.path("payload").path("commandName").asText());
        assertEquals(80, start.path("payload").path("parameters").path("temperature").asInt());
        ObjectNode sent = stateSignal(emitted, "SENT");
        assertEquals(9L, sent.path("payload").path("deviceModelId").asLong());
        assertEquals(7L, sent.path("payload").path("deviceInstanceId").asLong());
        assertEquals("message-1", sent.path("payload").path("messageId").asText());
        assertEquals("SENT", fixture.twinState().getCurrentCmdState());
    }

    @Test
    void abortUsesTheAbortSignalOnlyAfterARealActiveCommandExists() {
        Fixture fixture = fixture();
        startAndRun(fixture, "message-1");

        List<ObjectNode> emitted = fixture.engine().dispatchSignal(7L, "Interface_workflow_in", "WF_EXECUTE_ABORT", Map.of());

        ObjectNode abort = signal(emitted, "CMD_ABORT");
        assertFalse(abort.has("payload"));
        assertEquals("ABORTING", stateSignal(emitted, "ABORTING").path("payload").path("stateName").asText());
        assertEquals("ABORTING", fixture.twinState().getCurrentCmdState());
    }

    @Test
    void adapterAbortCompletionPublishesTerminalStateBeforeReturningToIdle() {
        Fixture fixture = fixture();
        startAndRun(fixture, "message-1");
        fixture.engine().dispatchSignal(7L, "Interface_workflow_in", "WF_EXECUTE_ABORT", Map.of());

        List<ObjectNode> emitted = fixture.engine().dispatchAdapterEvent(7L, "ABORT_DONE",
                JsonNodeSupport.objectNode().put("messageId", "message-1"));

        assertEquals(List.of("ABORTED", "IDLE"), emitted.stream()
                .filter(signal -> "CMD_STATE".equals(signal.path("signalName").asText()))
                .map(signal -> signal.path("payload").path("stateName").asText())
                .toList());
        assertEquals("IDLE", fixture.twinState().getCurrentCmdState());
    }

    @Test
    void adapterEventCanAdvanceCommandAndOperationStateTogether() {
        Fixture fixture = fixture();
        start(fixture, "message-1");

        List<ObjectNode> emitted = fixture.engine().dispatchAdapterEvent(7L, "HEAT_STARTED",
                JsonNodeSupport.objectNode().put("messageId", "message-1"));

        assertEquals("RUNNING", stateSignal(emitted, "RUNNING").path("payload").path("stateName").asText());
        ObjectNode state = signal(emitted, "OP_STATE");
        assertEquals("operatingMode", state.path("payload").path("regionName").asText());
        assertEquals("HEATING", state.path("payload").path("stateName").asText());
        assertEquals("HEATING", fixture.twinState().getCurrentOpState().path("operatingMode").asText());
    }

    private List<ObjectNode> start(Fixture fixture, String messageId) {
        return fixture.engine().dispatchSignal(7L, "Interface_workflow_in", "WF_EXECUTE_START",
                Map.of("messageId", messageId, "capabilityName", "heat", "parameters", Map.of("temperature", 80)));
    }

    private void startAndRun(Fixture fixture, String messageId) {
        start(fixture, messageId);
        fixture.engine().dispatchAdapterEvent(7L, "HEAT_STARTED", JsonNodeSupport.objectNode().put("messageId", messageId));
    }

    private Fixture fixture() {
        DeviceModelService models = mock(DeviceModelService.class);
        DeviceTwinStateService twins = mock(DeviceTwinStateService.class);
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        DeviceInstances instance = new DeviceInstances();
        instance.setId(7L);
        instance.setDeviceModelId(9L);
        when(instances.selectById(7L)).thenReturn(instance);

        DeviceModels model = model();
        model.setId(9L);
        when(models.getById("9")).thenReturn(model);
        DeviceTwinStates twin = new DeviceTwinStates();
        twin.setInstanceId(7L);
        twin.setCurrentCmdState("IDLE");
        twin.setCurrentOpState(JsonNodeSupport.objectNode().put("operatingMode", "IDLE"));
        when(twins.getByInstanceId(7L)).thenReturn(twin);
        doAnswer(invocation -> invocation.getArgument(0)).when(twins).save(any(DeviceTwinStates.class));

        ProtocolDictionaryService protocol = new ProtocolDictionaryService();
        StateMachineActionRegistry registry = new StateMachineActionRegistry(List.of(
                new SendStateMachineActionExecutor(publisher, protocol)));
        return new Fixture(new StateMachineEngine(models, twins, instances, registry, publisher, protocol), twin);
    }

    private DeviceModels model() {
        DeviceModels model = new DeviceModels();
        ArrayNode capabilities = JsonNodeSupport.arrayNode();
        capabilities.addObject().put("capabilityName", "heat").put("adapterCommandName", "heat_cmd");
        model.setCapabilities(capabilities);

        ArrayNode interfaces = JsonNodeSupport.arrayNode();
        interfaces.addObject().put("name", "Interface_workflow_in").put("direction", "IN")
                .put("interfaceType", "WORKFLOW").putArray("allowedSignals")
                .add("WF_EXECUTE_START").add("WF_EXECUTE_ABORT");
        interfaces.addObject().put("name", "Interface_control_in").put("direction", "IN")
                .put("interfaceType", "CONTROL").putArray("allowedSignals")
                .add("WF_EXECUTE_START").add("MANUAL_EXECUTE_ABORT");
        interfaces.addObject().put("name", "Interface_constraint_in").put("direction", "IN")
                .put("interfaceType", "CONSTRAINT").putArray("allowedSignals")
                .add("CONSTRAINT_ABORT");
        interfaces.addObject().put("name", "Interface_adapter_in").put("direction", "IN")
                .put("interfaceType", "ADAPTER").putArray("allowedSignals")
                .add("HEAT_STARTED").add("ABORT_DONE");
        interfaces.addObject().put("name", "Interface_adapter_out").put("direction", "OUT")
                .put("interfaceType", "ADAPTER").putArray("allowedSignals").add("CMD_START").add("CMD_ABORT");
        interfaces.addObject().put("name", "Interface_state_out").put("direction", "OUT")
                .put("interfaceType", "STATE").putArray("allowedSignals").add("CMD_STATE").add("OP_STATE");
        model.setStateMachineInterfaces(interfaces);

        ObjectNode cmd = JsonNodeSupport.objectNode();
        cmd.put("initialStateName", "IDLE");
        ArrayNode cmdStates = cmd.putArray("states");
        for (String name : List.of("IDLE", "SENT", "RUNNING", "COMPLETED", "FAILED", "ABORTING", "ABORTED")) {
            cmdStates.addObject().put("stateName", name).putArray("onEntry");
        }
        model.setCmdState(cmd);

        ObjectNode op = JsonNodeSupport.objectNode();
        ObjectNode region = op.putArray("regions").addObject();
        region.put("regionName", "operatingMode");
        region.put("initialStateName", "IDLE");
        region.putArray("states").addObject().put("stateName", "IDLE").putArray("onEntry");
        ((ArrayNode) region.path("states")).addObject().put("stateName", "HEATING").putArray("onEntry");
        model.setOpState(op);

        ArrayNode transitions = JsonNodeSupport.arrayNode();
        ObjectNode heatStarted = transitions.addObject();
        heatStarted.put("stateSpace", "CMD");
        heatStarted.put("fromStateName", "SENT");
        heatStarted.put("toStateName", "RUNNING");
        heatStarted.putObject("trigger").put("interfaceName", "Interface_adapter_in").put("signalName", "HEAT_STARTED");
        heatStarted.putArray("actions");
        ObjectNode abortDone = transitions.addObject();
        abortDone.put("stateSpace", "CMD");
        abortDone.put("fromStateName", "ABORTING");
        abortDone.put("toStateName", "ABORTED");
        abortDone.putObject("trigger").put("interfaceName", "Interface_adapter_in").put("signalName", "ABORT_DONE");
        abortDone.putArray("actions");
        ObjectNode heating = transitions.addObject();
        heating.put("stateSpace", "OP");
        heating.put("regionName", "operatingMode");
        heating.put("fromStateName", "IDLE");
        heating.put("toStateName", "HEATING");
        heating.putObject("trigger").put("interfaceName", "Interface_adapter_in").put("signalName", "HEAT_STARTED");
        heating.putArray("actions");
        model.setStateTransitions(transitions);
        return model;
    }

    private ObjectNode signal(List<ObjectNode> emitted, String signalName) {
        return emitted.stream().filter(signal -> signalName.equals(signal.path("signalName").asText())).findFirst().orElseThrow();
    }

    private ObjectNode stateSignal(List<ObjectNode> emitted, String stateName) {
        return emitted.stream()
                .filter(signal -> "CMD_STATE".equals(signal.path("signalName").asText()))
                .filter(signal -> stateName.equals(signal.path("payload").path("stateName").asText()))
                .findFirst().orElseThrow();
    }

    private record Fixture(StateMachineEngine engine, DeviceTwinStates twinState) {
    }
}
