package com.smartlab.management.service.db.resource.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.adapter.AdapterManifestService;
import com.smartlab.global.protocol.ProtocolDictionaryService;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.resource.device.DeviceModelSaveDTO;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.management.service.db.resource.adapter.AdapterIndexService;
import com.smartlab.management.service.db.resource.data.DataTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceModelServiceTest {

    private AdapterManifestService adapterManifestService;
    private DeviceModelsMapper modelMapper;
    private DeviceModelService service;
    private AdapterIndexService adapterIndexService;

    @BeforeEach
    void setUp() {
        modelMapper = mock(DeviceModelsMapper.class);
        adapterIndexService = mock(AdapterIndexService.class);
        ProtocolDictionaryService protocol = new ProtocolDictionaryService();
        adapterManifestService = mock(AdapterManifestService.class);
        service = new DeviceModelService(
                modelMapper,
                mock(DeviceInstancesMapper.class),
                mock(DeviceCategoryService.class),
                adapterManifestService,
                protocol,
                mock(DataTemplateService.class),
                adapterIndexService);
        when(adapterManifestService.normalizeDataType(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void previewUsesCategoryAsTheOnlyAdapterContractSelector() {
        DeviceModelSaveDTO payload = minimalPayload();
        ObjectNode config = (ObjectNode) payload.getAdapterContract().path("config");
        config.put("categoryName", "Reactor");
        config.put("adapterName", "adapter-1");
        when(adapterIndexService.buildAdapterContract("adapter-1", "Reactor"))
                .thenReturn(registeredContract("adapter-1", "Reactor", "heat"));
        config.put("templateName", "ReactorTemplate");

        ObjectNode bundle = service.previewModel(payload);
        JsonNode previewConfig = bundle.path("capabilityModel").path("adapterContract").path("config");

        assertEquals("Reactor", previewConfig.path("categoryName").asText());
        assertFalse(previewConfig.has("templateName"));
    }

    @Test
    void saveUsesTheSameCanonicalAdapterContractAsPreview() {
        DeviceModelSaveDTO payload = minimalPayload();
        payload.setCategoryId(7L);
        ObjectNode config = (ObjectNode) payload.getAdapterContract().path("config");
        config.put("categoryName", "Reactor");
        config.put("adapterName", "adapter-1");
        when(adapterIndexService.buildAdapterContract("adapter-1", "Reactor"))
                .thenReturn(registeredContract("adapter-1", "Reactor", "heat"));
        config.put("templateName", "ReactorTemplate");

        service.savePayload(payload);

        ArgumentCaptor<DeviceModels> captor = ArgumentCaptor.forClass(DeviceModels.class);
        verify(modelMapper).insert(captor.capture());
        JsonNode savedConfig = captor.getValue().getAdapterContract().path("config");
        assertEquals("Reactor", savedConfig.path("categoryName").asText());
        assertFalse(savedConfig.has("templateName"));
    }

    @Test
    void saveRejectsCapabilityWithoutRequiredShapeBeforeInsert() {
        DeviceModelSaveDTO payload = completePayload();
        ((ArrayNode) payload.getCapabilities()).addObject()
                .put("capabilityName", "broken");

        assertThrows(IllegalArgumentException.class, () -> service.savePayload(payload));
        verify(modelMapper, never()).insert(any(DeviceModels.class));
    }

    @Test
    void saveRejectsStateTransitionReferencingUnknownAdapterEvent() {
        DeviceModelSaveDTO payload = completePayload();
        payload.setStateTransitions(arrayWithTransition(
                "CMD", "SENT", "RUNNING", "UNKNOWN_EVENT"));

        assertThrows(IllegalArgumentException.class, () -> service.savePayload(payload));
        verify(modelMapper, never()).insert(any(DeviceModels.class));
    }

    @Test
    void runtimeReadyReturnsOnlyFullyValidatedModel() {
        DeviceModels model = completeModel();
        when(modelMapper.selectById(7L)).thenReturn(model);

        assertSame(model, service.requireRuntimeReady(7L));
    }

    @Test
    void previewRejectsCapabilityModelThatViolatesItsSchema() {
        DeviceModelSaveDTO payload = minimalPayload();
        ((ArrayNode) payload.getAttributes()).addObject()
                .put("attributeName", "temperature")
                .put("valueKind", "CONTINUOUS")
                .put("dataType", "UNSUPPORTED");

        assertThrows(IllegalArgumentException.class, () -> service.previewModel(payload));
    }

    @Test
    void previewBuildsUniqueCurrentStateMachineContract() {
        DeviceModelSaveDTO payload = minimalPayload();
        ObjectNode config = (ObjectNode) payload.getAdapterContract().path("config");
        config.put("adapterName", "adapter-1");
        config.put("categoryName", "Reactor");
        when(adapterIndexService.buildAdapterContract("adapter-1", "Reactor"))
                .thenReturn(registeredContract("adapter-1", "Reactor", "heat"));
        ObjectNode bundle = service.previewModel(payload);
        JsonNode stateMachine = bundle.path("stateMachineModel");
        JsonNode interfaces = stateMachine.path("interfaces");

        assertEquals(6, interfaces.size());
        Set<String> names = new HashSet<>();
        interfaces.forEach(item -> names.add(item.path("name").asText()));
        assertEquals(6, names.size());
        assertEquals(Set.of("WF_EXECUTE_START", "WF_EXECUTE_ABORT"),
                interfaceSignals(interfaces, "Interface_workflow_in"));
        assertEquals(Set.of("SENT_EVENT", "RUNNING_EVENT", "COMPLETED_EVENT", "FAILED_EVENT", "ABORTED_EVENT"),
                interfaceSignals(interfaces, "Interface_adapter_in"));
        assertEquals(Set.of("CMD_STATE", "OP_STATE"), interfaceSignals(interfaces, "Interface_state_out"));
        assertTrue(hasTransition(stateMachine.path("transitions"), "SENT", "RUNNING", "SENT_EVENT"));
        assertTrue(hasTransition(stateMachine.path("transitions"), "RUNNING", "COMPLETED", "COMPLETED_EVENT"));
        assertTrue(hasTransition(stateMachine.path("transitions"), "RUNNING", "FAILED", "FAILED_EVENT"));
        assertTrue(hasTransition(stateMachine.path("transitions"), "ABORTING", "ABORTED", "ABORTED_EVENT"));
        assertEquals(Set.of("IDLE", "SENT", "RUNNING", "COMPLETED", "FAILED", "ABORTING", "ABORTED"),
                stateNames(stateMachine.path("cmdLifecycleSpace").path("states")));

    }

    @Test
    void rejectsMalformedCustomTransitionInsteadOfSilentlyDroppingIt() {
        DeviceModelSaveDTO payload = minimalPayload();
        ObjectNode transition = ((ArrayNode) payload.getStateTransitions()).addObject();
        transition.put("stateSpace", "OP");
        transition.put("fromStateName", "IDLE");
        transition.put("toStateName", "IDLE");

        assertThrows(IllegalArgumentException.class, () -> service.previewModel(payload));
    }
    @Test
    void rejectsCommandTransitionTriggeredByOperationEvent() {
        DeviceModelSaveDTO payload = minimalPayload();
        ObjectNode events = (ObjectNode) payload.getAdapterContract().path("events");
        events.withArray("cmdEvents").addObject().put("eventName", "DONE");
        events.withArray("opEvents").addObject().put("eventName", "HEAT_STARTED");
        addTransition(payload, "CMD", "RUNNING", "COMPLETED", "HEAT_STARTED");

        assertThrows(IllegalArgumentException.class, () -> service.previewModel(payload));
    }

    @Test
    void rejectsTransitionWhoseStatesDoNotBelongToDeclaredStateSpace() {
        DeviceModelSaveDTO payload = minimalPayload();
        ObjectNode events = (ObjectNode) payload.getAdapterContract().path("events");
        events.withArray("opEvents").addObject().put("eventName", "HEAT_STARTED");
        addTransition(payload, "OP", "IDLE", "MISSING", "HEAT_STARTED");

        assertThrows(IllegalArgumentException.class, () -> service.previewModel(payload));
    }


    @Test
    void rejectsAutomaticExecutionLifecycleTransition() {
        DeviceModelSaveDTO payload = minimalPayload();
        ObjectNode transition = ((ArrayNode) payload.getStateTransitions()).addObject();
        transition.put("stateSpace", "CMD");
        transition.put("fromStateName", "SENT");
        transition.put("toStateName", "RECEIVED");
        transition.putNull("trigger");
        transition.putArray("actions");

        assertThrows(IllegalArgumentException.class, () -> service.previewModel(payload));
    }

    @Test
    void capabilityModelRejectsAdapterInternalParameters() {
        DeviceModelSaveDTO payload = minimalPayload();
        ObjectNode command = ((ArrayNode) payload.getAdapterContract().path("commands")).addObject();
        command.put("commandName", "heat");
        ObjectNode parameter = command.putArray("commandParameters").addObject();
        parameter.put("paramName", "index");
        parameter.put("dataType", "INTEGER");
        parameter.put("internal", true);

        assertThrows(IllegalArgumentException.class, () -> service.previewModel(payload));
    }

    @Test
    void submittedAdapterCommandsCannotOverrideRegisteredContract() {
        DeviceModelSaveDTO payload = minimalPayload();
        ObjectNode config = (ObjectNode) payload.getAdapterContract().path("config");
        config.put("adapterName", "adapter-1");
        config.put("categoryName", "Reactor");
        ((ArrayNode) payload.getAdapterContract().path("commands")).addObject()
                .put("commandName", "injected-command")
                .putArray("commandParameters");
        when(adapterIndexService.buildAdapterContract("adapter-1", "Reactor"))
                .thenReturn(registeredContract("adapter-1", "Reactor", "heat"));

        ObjectNode preview = service.previewModel(payload);

        JsonNode commands = preview.path("capabilityModel").path("adapterContract").path("commands");
        assertEquals(1, commands.size());
        assertEquals("heat", commands.get(0).path("commandName").asText());
    }
    private void addTransition(DeviceModelSaveDTO payload, String stateSpace, String from, String to, String signal) {
        ObjectNode transition = ((ArrayNode) payload.getStateTransitions()).addObject();
        transition.put("stateSpace", stateSpace);
        transition.put("fromStateName", from);
        transition.put("toStateName", to);
        transition.putObject("trigger")
                .put("interfaceName", "Interface_adapter_in")
                .put("signalName", signal);
        transition.putArray("actions");
    }
    private Set<String> interfaceSignals(JsonNode interfaces, String interfaceName) {
        Set<String> result = new HashSet<>();
        interfaces.forEach(item -> {
            if (interfaceName.equals(item.path("name").asText())) {
                item.path("allowedSignals").forEach(signal -> result.add(signal.asText()));
            }
        });
        return result;
    }

    private boolean hasTransition(JsonNode transitions, String from, String to, String signal) {
        for (JsonNode transition : transitions) {
            if (from.equals(transition.path("fromStateName").asText())
                    && to.equals(transition.path("toStateName").asText())
                    && signal.equals(transition.path("trigger").path("signalName").asText())) {
                return true;
            }
        }
        return false;
    }


    private boolean hasAutomaticTransition(JsonNode transitions, String from, String to) {
        for (JsonNode transition : transitions) {
            if (from.equals(transition.path("fromStateName").asText())
                    && to.equals(transition.path("toStateName").asText())
                    && transition.path("trigger").isNull()) {
                return true;
            }
        }
        return false;
    }

    private ObjectNode registeredContract(String adapterName, String categoryName, String commandName) {
        ObjectNode contract = JsonNodeSupport.objectNode();
        contract.putObject("config")
                .put("protocol", "MQTT")
                .put("adapterName", adapterName)
                .put("categoryName", categoryName);
        contract.putArray("commands").addObject()
                .put("commandName", commandName)
                .putArray("commandParameters");
        ObjectNode telemetry = contract.putObject("telemetry");
        telemetry.putArray("adapterAttributes");
        telemetry.putArray("attributesMapping");
        ObjectNode events = contract.putObject("events");
        addEvent(events.putArray("cmdEvents"), "SENT_EVENT");
        addEvent(events.withArray("cmdEvents"), "RUNNING_EVENT");
        addEvent(events.withArray("cmdEvents"), "COMPLETED_EVENT");
        addEvent(events.withArray("cmdEvents"), "FAILED_EVENT");
        addEvent(events.withArray("cmdEvents"), "ABORTED_EVENT");
        events.putArray("opEvents");
        return contract;
    }

    private DeviceModelSaveDTO completePayload() {
        DeviceModelSaveDTO payload = minimalPayload();
        ((ArrayNode) payload.getAttributes()).addObject()
                .put("attributeName", "temperature")
                .put("valueKind", "CONTINUOUS")
                .put("dataType", "DOUBLE");
        ObjectNode capability = ((ArrayNode) payload.getCapabilities()).addObject();
        capability.put("capabilityName", "start")
                .put("adapterCommandName", "start")
                .put("displayName", "启动");
        capability.putArray("parameters").addObject()
                .put("name", "duration")
                .put("displayName", "时长")
                .put("dataType", "INTEGER");
        capability.putArray("parameterMapping").addObject()
                .put("commandParamName", "duration")
                .put("capabilityParamName", "duration");

        ObjectNode contract = (ObjectNode) payload.getAdapterContract();
        ObjectNode config = (ObjectNode) contract.path("config");
        config.put("adapterName", "adapter-1");
        config.put("categoryName", "Reactor");
        contract.putArray("commands").addObject()
                .put("commandName", "start")
                .putArray("commandParameters").addObject()
                .put("paramName", "duration")
                .put("dataType", "INTEGER");
        ObjectNode telemetry = (ObjectNode) contract.path("telemetry");
        telemetry.putArray("adapterAttributes").addObject()
                .put("telemetryName", "temperature")
                .put("dataType", "DOUBLE");
        telemetry.putArray("attributesMapping").addObject()
                .put("adapterAttrName", "temperature")
                .put("modelAttributeName", "temperature");
        ObjectNode events = (ObjectNode) contract.path("events");
        addEvent(events.withArray("cmdEvents"), "SENT_EVENT");
        addEvent(events.withArray("cmdEvents"), "RUNNING_EVENT");
        addEvent(events.withArray("cmdEvents"), "COMPLETED_EVENT");
        addEvent(events.withArray("cmdEvents"), "FAILED_EVENT");
        addEvent(events.withArray("cmdEvents"), "ABORTED_EVENT");
        payload.setStateMachineInterfaces(standardInterfaces());
        payload.setCmdState(completeCmdState());
        payload.setStateTransitions(completeCmdTransitions());
        when(adapterIndexService.buildAdapterContract("adapter-1", "Reactor")).thenReturn(contract);
        return payload;
    }

    private DeviceModels completeModel() {
        DeviceModelSaveDTO payload = completePayload();
        DeviceModels model = new DeviceModels();
        model.setModelName(payload.getModelName());
        model.setCategoryId(payload.getCategoryId());
        model.setAttributes(payload.getAttributes());
        model.setCapabilities(payload.getCapabilities());
        model.setAdapterContract(payload.getAdapterContract());
        model.setPorts(payload.getPorts());
        model.setIntrinsicConstraints(payload.getIntrinsicConstraints());
        model.setStateMachineInterfaces(payload.getStateMachineInterfaces());
        model.setCmdState(payload.getCmdState());
        model.setOpState(payload.getOpState());
        model.setStateTransitions(payload.getStateTransitions());
        return model;
    }

    private ArrayNode completeCmdTransitions() {
        ArrayNode transitions = JsonNodeSupport.arrayNode();
        addTransition(transitions, "CMD", "SENT", "RUNNING", "SENT_EVENT");
        addTransition(transitions, "CMD", "RUNNING", "RUNNING", "RUNNING_EVENT");
        addTransition(transitions, "CMD", "RUNNING", "COMPLETED", "COMPLETED_EVENT");
        addTransition(transitions, "CMD", "RUNNING", "FAILED", "FAILED_EVENT");
        addTransition(transitions, "CMD", "ABORTING", "ABORTED", "ABORTED_EVENT");
        return transitions;
    }

    private ArrayNode arrayWithTransition(String stateSpace, String from, String to, String signal) {
        ArrayNode transitions = JsonNodeSupport.arrayNode();
        addTransition(transitions, stateSpace, from, to, signal);
        return transitions;
    }

    private void addTransition(ArrayNode transitions, String stateSpace, String from, String to, String signal) {
        ObjectNode transition = transitions.addObject();
        transition.put("stateSpace", stateSpace);
        transition.put("fromStateName", from);
        transition.put("toStateName", to);
        transition.putObject("trigger")
                .put("interfaceName", "Interface_adapter_in")
                .put("signalName", signal);
        transition.putArray("actions");
    }

    private ObjectNode completeCmdState() {
        ObjectNode cmdState = JsonNodeSupport.objectNode();
        cmdState.put("initialStateName", "IDLE");
        for (String stateName : Set.of("IDLE", "SENT", "RUNNING", "COMPLETED", "FAILED", "ABORTING", "ABORTED")) {
            ObjectNode state = cmdState.withArray("states").addObject();
            state.put("stateName", stateName);
            state.putArray("onEntry").addObject()
                    .put("actionName", "SEND")
                    .putObject("payload")
                    .put("interfaceName", "Interface_state_out")
                    .put("signalName", "CMD_STATE");
        }
        return cmdState;
    }

    private ArrayNode standardInterfaces() {
        ArrayNode interfaces = JsonNodeSupport.arrayNode();
        addInterface(interfaces, "Interface_workflow_in", "IN", "WORKFLOW", "WF_EXECUTE_START", "WF_EXECUTE_ABORT");
        addInterface(interfaces, "Interface_control_in", "IN", "CONTROL", "MANUAL_EXECUTE_START", "MANUAL_EXECUTE_ABORT");
        addInterface(interfaces, "Interface_constraint_in", "IN", "CONSTRAINT", "CONSTRAINT_EXECUTE", "CONSTRAINT_ABORT");
        addInterface(interfaces, "Interface_adapter_in", "IN", "ADAPTER", "SENT_EVENT", "RUNNING_EVENT", "COMPLETED_EVENT", "FAILED_EVENT", "ABORTED_EVENT");
        addInterface(interfaces, "Interface_adapter_out", "OUT", "ADAPTER", "CMD_START", "CMD_ABORT");
        addInterface(interfaces, "Interface_state_out", "OUT", "STATE", "CMD_STATE", "OP_STATE");
        return interfaces;
    }

    private void addInterface(ArrayNode interfaces, String name, String direction, String type, String... signals) {
        ObjectNode item = interfaces.addObject();
        item.put("name", name);
        item.put("direction", direction);
        item.put("interfaceType", type);
        ArrayNode allowedSignals = item.putArray("allowedSignals");
        for (String signal : signals) {
            allowedSignals.add(signal);
        }
    }

    private void addEvent(ArrayNode events, String eventName) {
        events.addObject()
                .put("eventName", eventName)
                .put("description", eventName);
    }
    private DeviceModelSaveDTO minimalPayload() {
        DeviceModelSaveDTO payload = new DeviceModelSaveDTO();
        payload.setModelName("ReactorModel");
        payload.setCategoryId(7L);
        payload.setAttributes(JsonNodeSupport.arrayNode());
        payload.setCapabilities(JsonNodeSupport.arrayNode());
        ObjectNode contract = JsonNodeSupport.objectNode();
        contract.putObject("config")
                .put("protocol", "MQTT")
                .put("adapterName", "")
                .put("categoryName", "");
        contract.putArray("commands");
        ObjectNode telemetry = contract.putObject("telemetry");
        telemetry.putArray("adapterAttributes");
        telemetry.putArray("attributesMapping");
        ObjectNode events = contract.putObject("events");
        addEvent(events.putArray("cmdEvents"), "SENT_EVENT");
        addEvent(events.withArray("cmdEvents"), "RUNNING_EVENT");
        addEvent(events.withArray("cmdEvents"), "COMPLETED_EVENT");
        addEvent(events.withArray("cmdEvents"), "FAILED_EVENT");
        addEvent(events.withArray("cmdEvents"), "ABORTED_EVENT");
        events.putArray("opEvents");
        payload.setAdapterContract(contract);
        payload.setPorts(JsonNodeSupport.arrayNode());
        payload.setIntrinsicConstraints(JsonNodeSupport.arrayNode());
        ObjectNode opState = JsonNodeSupport.objectNode();
        ArrayNode regions = opState.putArray("regions");
        ObjectNode region = regions.addObject();
        region.put("regionName", "operatingMode");
        region.put("initialStateName", "IDLE");
        ArrayNode states = region.putArray("states");
        ObjectNode state = states.addObject();
        state.put("stateName", "IDLE");
        state.putArray("onEntry");
        payload.setOpState(opState);
        payload.setStateMachineInterfaces(standardInterfaces());
        payload.setCmdState(completeCmdState());
        payload.setStateTransitions(completeCmdTransitions());
        return payload;
    }
    private Set<String> stateNames(JsonNode states) {
        Set<String> names = new HashSet<>();
        states.forEach(state -> names.add(state.path("stateName").asText()));
        return names;
    }
}