package com.smartlab.engine.statemachine;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.action.SendStateMachineActionExecutor;
import com.smartlab.engine.statemachine.action.StateMachineActionExecutor;
import com.smartlab.engine.statemachine.action.StateMachineActionRegistry;
import com.smartlab.global.util.JsonSchemaValidationService;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.service.db.resource.device.DeviceModelService;
import com.smartlab.management.service.db.resource.device.DeviceTwinStateService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StateMachineEngineTest {

    @Test
    void terminalCommandStateIsPublishedBeforeReturningToIdle() {
        DeviceModelService modelService = mock(DeviceModelService.class);
        DeviceTwinStateService twinService = mock(DeviceTwinStateService.class);
        DeviceInstancesMapper instancesMapper = mock(DeviceInstancesMapper.class);
        StateMachineActionRegistry registry = mock(StateMachineActionRegistry.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        StateMachineActionExecutor send = mock(StateMachineActionExecutor.class);

        DeviceInstances instance = new DeviceInstances();
        instance.setId(7L);
        instance.setDeviceModelId(9L);
        when(instancesMapper.selectById(7L)).thenReturn(instance);

        DeviceModels model = modelWithTerminalTransition();
        when(modelService.getById("9")).thenReturn(model);

        DeviceTwinStates twin = new DeviceTwinStates();
        twin.setInstanceId(7L);
        twin.setCurrentCmdState("RUNNING");
        twin.setCurrentOpState(JsonNodeSupport.objectNode().put("operatingMode", "IDLE"));
        when(twinService.getByInstanceId(7L)).thenReturn(twin);

        ObjectNode terminalSignal = JsonNodeSupport.objectNode();
        terminalSignal.put("signalName", "CMD_STATE");
        terminalSignal.putObject("payload").put("state", "COMPLETED");
        when(registry.required("SEND")).thenReturn(send);
        when(send.execute(any(), any())).thenReturn(terminalSignal);

        List<String> savedStates = new ArrayList<>();
        doAnswer(invocation -> {
            savedStates.add(invocation.<DeviceTwinStates>getArgument(0).getCurrentCmdState());
            return invocation.getArgument(0);
        }).when(twinService).save(any(DeviceTwinStates.class));

        StateMachineEngine engine = new StateMachineEngine(
                modelService, twinService, instancesMapper, registry, publisher);

        List<ObjectNode> emitted = engine.dispatchSignal(7L, "Interface_adapter_in", "DONE", Map.of());

        assertEquals(1, emitted.size());
        assertEquals(List.of("COMPLETED", "IDLE"), savedStates);
        assertEquals("IDLE", twin.getCurrentCmdState());
        assertEquals("COMPLETED", emitted.getFirst().path("payload").path("state").asText());
    }

    @Test
    void transitionUsesDeclaredStateSpaceWhenCommandAndOperationStatesShareNames() {
        DeviceModelService modelService = mock(DeviceModelService.class);
        DeviceTwinStateService twinService = mock(DeviceTwinStateService.class);
        DeviceInstancesMapper instancesMapper = mock(DeviceInstancesMapper.class);
        DeviceInstances instance = new DeviceInstances();
        instance.setId(8L);
        instance.setDeviceModelId(10L);
        when(instancesMapper.selectById(8L)).thenReturn(instance);

        DeviceModels model = modelWithOperationTransitionSharingCommandStateNames();
        when(modelService.getById("10")).thenReturn(model);
        DeviceTwinStates twin = new DeviceTwinStates();
        twin.setInstanceId(8L);
        twin.setCurrentCmdState("IDLE");
        twin.setCurrentOpState(JsonNodeSupport.objectNode().put("operatingMode", "IDLE"));
        when(twinService.getByInstanceId(8L)).thenReturn(twin);

        StateMachineEngine engine = new StateMachineEngine(
                modelService, twinService, instancesMapper,
                mock(StateMachineActionRegistry.class), mock(ApplicationEventPublisher.class));

        engine.dispatchSignal(8L, "Interface_adapter_in", "HEAT_STARTED", Map.of());

        assertEquals("IDLE", twin.getCurrentCmdState());
        assertEquals("RUNNING", twin.getCurrentOpState().path("operatingMode").asText());
    }

    @Test
    void followsAutomaticCommandTransitionsUntilTheNextSignalDrivenState() {
        DeviceModelService modelService = mock(DeviceModelService.class);
        DeviceTwinStateService twinService = mock(DeviceTwinStateService.class);
        DeviceInstancesMapper instancesMapper = mock(DeviceInstancesMapper.class);
        DeviceInstances instance = new DeviceInstances();
        instance.setId(11L);
        instance.setDeviceModelId(12L);
        when(instancesMapper.selectById(11L)).thenReturn(instance);

        DeviceModels model = modelWithAutomaticCommandTransitions();
        when(modelService.getById("12")).thenReturn(model);
        DeviceTwinStates twin = new DeviceTwinStates();
        twin.setInstanceId(11L);
        twin.setCurrentCmdState("IDLE");
        twin.setCurrentOpState(JsonNodeSupport.objectNode().put("operatingMode", "IDLE"));
        when(twinService.getByInstanceId(11L)).thenReturn(twin);

        StateMachineEngine engine = new StateMachineEngine(
                modelService, twinService, instancesMapper,
                mock(StateMachineActionRegistry.class), mock(ApplicationEventPublisher.class));

        engine.dispatchSignal(11L, "Interface_workflow_in", "WF_EXECUTE_START",
                Map.of("commandName", "heat", "parameters", Map.of()));

        assertEquals("RUNNING", twin.getCurrentCmdState());
    }

    @Test
    void rejectsUnknownActionInsteadOfSilentlyContinuing() {
        DeviceModelService modelService = mock(DeviceModelService.class);
        DeviceTwinStateService twinService = mock(DeviceTwinStateService.class);
        DeviceInstancesMapper instancesMapper = mock(DeviceInstancesMapper.class);
        DeviceInstances instance = new DeviceInstances();
        instance.setId(41L);
        instance.setDeviceModelId(42L);
        when(instancesMapper.selectById(41L)).thenReturn(instance);

        DeviceModels model = modelWithTerminalTransition();
        ((ObjectNode) model.getStateTransitions().get(0)).putArray("actions")
                .addObject().put("actionName", "UNKNOWN_ACTION").putObject("payload");
        when(modelService.getById("42")).thenReturn(model);
        DeviceTwinStates twin = new DeviceTwinStates();
        twin.setInstanceId(41L);
        twin.setCurrentCmdState("RUNNING");
        twin.setCurrentOpState(JsonNodeSupport.objectNode().put("operatingMode", "IDLE"));
        when(twinService.getByInstanceId(41L)).thenReturn(twin);

        StateMachineEngine engine = new StateMachineEngine(
                modelService, twinService, instancesMapper,
                new StateMachineActionRegistry(List.of(), java.util.Set.of()), mock(ApplicationEventPublisher.class));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> engine.dispatchSignal(41L, "Interface_adapter_in", "DONE", Map.of()));
        assertEquals("状态机动作没有执行器: UNKNOWN_ACTION", error.getMessage());
    }

    private DeviceModels modelWithTerminalTransition() {
        DeviceModels model = new DeviceModels();
        ObjectNode cmdState = JsonNodeSupport.objectNode();
        cmdState.put("initialStateName", "IDLE");
        ArrayNode states = cmdState.putArray("states");
        states.addObject().put("stateName", "IDLE").putArray("onEntry");
        states.addObject().put("stateName", "RUNNING").putArray("onEntry");
        ObjectNode completed = states.addObject();
        completed.put("stateName", "COMPLETED");
        completed.putArray("onEntry").addObject()
                .put("actionName", "SEND")
                .putObject("payload")
                .put("interfaceName", "Interface_status_out")
                .put("signalName", "CMD_STATE");
        model.setCmdState(cmdState);

        ObjectNode opState = JsonNodeSupport.objectNode();
        opState.put("initialStateName", "IDLE");
        opState.putArray("states").addObject().put("stateName", "IDLE").putArray("onEntry");
        model.setOpState(opState);

        ArrayNode transitions = JsonNodeSupport.arrayNode();
        ObjectNode transition = transitions.addObject();
        transition.put("stateSpace", "CMD");
        transition.put("fromStateName", "RUNNING");
        transition.put("toStateName", "COMPLETED");
        transition.putObject("trigger")
                .put("interfaceName", "Interface_adapter_in")
                .put("signalName", "DONE");
        transition.putArray("actions");
        model.setStateTransitions(transitions);
        ArrayNode interfaces = JsonNodeSupport.arrayNode();
        interfaces.addObject()
                .put("name", "Interface_status_out")
                .put("direction", "OUT")
                .put("interfaceType", "STAT")
                .putArray("allowedSignals").add("CMD_STATE");
        model.setStateMachineInterfaces(interfaces);
        return model;
    }
    private DeviceModels modelWithOperationTransitionSharingCommandStateNames() {
        DeviceModels model = new DeviceModels();
        ObjectNode cmdState = JsonNodeSupport.objectNode();
        cmdState.put("initialStateName", "IDLE");
        ArrayNode cmdStates = cmdState.putArray("states");
        cmdStates.addObject().put("stateName", "IDLE").putArray("onEntry");
        cmdStates.addObject().put("stateName", "RUNNING").putArray("onEntry");
        model.setCmdState(cmdState);

        model.setOpState(orthogonalOpState(List.of("IDLE", "RUNNING")));

        ArrayNode transitions = JsonNodeSupport.arrayNode();
        ObjectNode transition = transitions.addObject();
        transition.put("stateSpace", "OP");
        transition.put("regionName", "operatingMode");
        transition.put("fromStateName", "IDLE");
        transition.put("toStateName", "RUNNING");
        transition.putObject("trigger")
                .put("interfaceName", "Interface_adapter_in")
                .put("signalName", "HEAT_STARTED");
        transition.putArray("actions");
        model.setStateTransitions(transitions);
        model.setStateMachineInterfaces(JsonNodeSupport.arrayNode());
        return model;
    }
    private DeviceModels modelWithAutomaticCommandTransitions() {
        DeviceModels model = new DeviceModels();
        ObjectNode cmdState = JsonNodeSupport.objectNode();
        cmdState.put("initialStateName", "IDLE");
        ArrayNode cmdStates = cmdState.putArray("states");
        for (String name : List.of("IDLE", "SENT", "RECEIVED", "RUNNING")) {
            cmdStates.addObject().put("stateName", name).putArray("onEntry");
        }
        model.setCmdState(cmdState);

        ObjectNode opState = JsonNodeSupport.objectNode();
        opState.put("initialStateName", "IDLE");
        opState.putArray("states").addObject().put("stateName", "IDLE").putArray("onEntry");
        model.setOpState(opState);

        ArrayNode transitions = JsonNodeSupport.arrayNode();
        ObjectNode start = transitions.addObject();
        start.put("stateSpace", "CMD");
        start.put("fromStateName", "IDLE");
        start.put("toStateName", "SENT");
        start.putObject("trigger")
                .put("interfaceName", "Interface_workflow_in")
                .put("signalName", "WF_EXECUTE_START");
        start.putArray("actions");
        addAutomaticTransition(transitions, "SENT", "RECEIVED");
        addAutomaticTransition(transitions, "RECEIVED", "RUNNING");
        model.setStateTransitions(transitions);
        model.setStateMachineInterfaces(JsonNodeSupport.arrayNode());
        return model;
    }

    private void addAutomaticTransition(ArrayNode transitions, String from, String to) {
        ObjectNode transition = transitions.addObject();
        transition.put("stateSpace", "CMD");
        transition.put("fromStateName", from);
        transition.put("toStateName", to);
        transition.putNull("trigger");
        transition.putArray("actions");
    }
    @Test
    void automaticCommandTransitionsEmitEveryEnteredStateInOrder() {
        DeviceModelService modelService = mock(DeviceModelService.class);
        DeviceTwinStateService twinService = mock(DeviceTwinStateService.class);
        DeviceInstancesMapper instancesMapper = mock(DeviceInstancesMapper.class);
        DeviceInstances instance = new DeviceInstances();
        instance.setId(21L);
        instance.setDeviceModelId(22L);
        when(instancesMapper.selectById(21L)).thenReturn(instance);

        DeviceModels model = modelWithObservableAutomaticCommandTransitions();
        when(modelService.getById("22")).thenReturn(model);
        DeviceTwinStates twin = new DeviceTwinStates();
        twin.setInstanceId(21L);
        twin.setCurrentCmdState("IDLE");
        twin.setCurrentOpState(JsonNodeSupport.objectNode().put("operatingMode", "IDLE"));
        when(twinService.getByInstanceId(21L)).thenReturn(twin);

        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        StateMachineActionRegistry registry = new StateMachineActionRegistry(List.of(
                new SendStateMachineActionExecutor(publisher, new JsonSchemaValidationService())), java.util.Set.of("SEND"));
        StateMachineEngine engine = new StateMachineEngine(
                modelService, twinService, instancesMapper, registry, publisher);

        List<ObjectNode> emitted = engine.dispatchSignal(21L, "Interface_workflow_in", "WF_EXECUTE_START",
                Map.of("commandName", "heat", "parameters", Map.of()));

        assertEquals(List.of("SENT", "RECEIVED", "RUNNING"), emitted.stream()
                .map(signal -> signal.path("payload").path("state").asText())
                .toList());
    }

    @Test
    void terminalResetEntersIdleAndEmitsItsStatusSignal() {
        DeviceModelService modelService = mock(DeviceModelService.class);
        DeviceTwinStateService twinService = mock(DeviceTwinStateService.class);
        DeviceInstancesMapper instancesMapper = mock(DeviceInstancesMapper.class);
        DeviceInstances instance = new DeviceInstances();
        instance.setId(31L);
        instance.setDeviceModelId(32L);
        when(instancesMapper.selectById(31L)).thenReturn(instance);

        DeviceModels model = modelWithObservableTerminalReset();
        when(modelService.getById("32")).thenReturn(model);
        DeviceTwinStates twin = new DeviceTwinStates();
        twin.setInstanceId(31L);
        twin.setCurrentCmdState("RUNNING");
        twin.setCurrentOpState(JsonNodeSupport.objectNode().put("operatingMode", "IDLE"));
        when(twinService.getByInstanceId(31L)).thenReturn(twin);

        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        StateMachineActionRegistry registry = new StateMachineActionRegistry(List.of(
                new SendStateMachineActionExecutor(publisher, new JsonSchemaValidationService())), java.util.Set.of("SEND"));
        StateMachineEngine engine = new StateMachineEngine(
                modelService, twinService, instancesMapper, registry, publisher);

        List<ObjectNode> emitted = engine.dispatchSignal(31L, "Interface_adapter_in", "DONE", Map.of());

        assertEquals(List.of("COMPLETED", "IDLE"), emitted.stream()
                .map(signal -> signal.path("payload").path("state").asText())
                .toList());
    }

    private DeviceModels modelWithObservableAutomaticCommandTransitions() {
        DeviceModels model = observableCommandModel(List.of("IDLE", "SENT", "RECEIVED", "RUNNING"));
        ArrayNode transitions = JsonNodeSupport.arrayNode();
        ObjectNode start = transitions.addObject();
        start.put("stateSpace", "CMD");
        start.put("fromStateName", "IDLE");
        start.put("toStateName", "SENT");
        start.putObject("trigger")
                .put("interfaceName", "Interface_workflow_in")
                .put("signalName", "WF_EXECUTE_START");
        start.putArray("actions");
        addAutomaticTransition(transitions, "SENT", "RECEIVED");
        addAutomaticTransition(transitions, "RECEIVED", "RUNNING");
        model.setStateTransitions(transitions);
        return model;
    }

    private DeviceModels modelWithObservableTerminalReset() {
        DeviceModels model = observableCommandModel(List.of("IDLE", "RUNNING", "COMPLETED"));
        ArrayNode transitions = JsonNodeSupport.arrayNode();
        ObjectNode completed = transitions.addObject();
        completed.put("stateSpace", "CMD");
        completed.put("fromStateName", "RUNNING");
        completed.put("toStateName", "COMPLETED");
        completed.putObject("trigger")
                .put("interfaceName", "Interface_adapter_in")
                .put("signalName", "DONE");
        completed.putArray("actions");
        model.setStateTransitions(transitions);
        return model;
    }

    private DeviceModels observableCommandModel(List<String> commandStates) {
        DeviceModels model = new DeviceModels();
        ObjectNode cmdState = JsonNodeSupport.objectNode();
        cmdState.put("initialStateName", "IDLE");
        ArrayNode states = cmdState.putArray("states");
        for (String name : commandStates) {
            ObjectNode state = states.addObject();
            state.put("stateName", name);
            state.putArray("onEntry").addObject().put("actionName", "SEND").putObject("payload")
                    .put("interfaceName", "Interface_status_out").put("signalName", "CMD_STATE");
            ((ObjectNode) state.path("onEntry").get(0).path("payload"))
                    .put("stateName", name);
        }
        model.setCmdState(cmdState);
        model.setOpState(orthogonalOpState(List.of("IDLE")));
        ArrayNode interfaces = JsonNodeSupport.arrayNode();
        interfaces.addObject().put("name", "Interface_status_out").put("direction", "OUT")
                .put("interfaceType", "STAT").putArray("allowedSignals").add("CMD_STATE");
        model.setStateMachineInterfaces(interfaces);
        return model;
    }

    private ObjectNode orthogonalOpState(List<String> statesList) {
        ObjectNode opState = JsonNodeSupport.objectNode();
        ArrayNode regions = opState.putArray("regions");
        ObjectNode region = regions.addObject();
        region.put("regionName", "operatingMode");
        region.put("initialStateName", statesList.isEmpty() ? "IDLE" : statesList.get(0));
        ArrayNode states = region.putArray("states");
        for (String sName : statesList) {
            ObjectNode state = states.addObject();
            state.put("stateName", sName);
            state.putArray("onEntry");
        }
        return opState;
    }


}