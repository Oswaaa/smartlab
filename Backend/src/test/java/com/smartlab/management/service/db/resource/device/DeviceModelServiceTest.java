package com.smartlab.management.service.db.resource.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.adapter.AdapterManifestService;
import com.smartlab.global.protocol.ProtocolDictionaryService;
import com.smartlab.global.schema.SchemaMetadataService;
import com.smartlab.global.schema.StateMachineInterfacePolicyService;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.global.util.JsonSchemaValidationService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceModelServiceTest {

    private DeviceModelsMapper modelMapper;
    private DeviceModelService service;
    private AdapterIndexService adapterIndexService;

    @BeforeEach
    void setUp() {
        modelMapper = mock(DeviceModelsMapper.class);
        adapterIndexService = mock(AdapterIndexService.class);
        ProtocolDictionaryService protocol = new ProtocolDictionaryService();
        SchemaMetadataService metadata = new SchemaMetadataService(protocol);
        service = new DeviceModelService(
                modelMapper,
                mock(DeviceInstancesMapper.class),
                mock(DeviceCategoryService.class),
                mock(AdapterManifestService.class),
                protocol,
                metadata,
                new StateMachineInterfacePolicyService(metadata),
                mock(DataTemplateService.class),
                new JsonSchemaValidationService(),
                adapterIndexService);
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
    void previewRejectsCapabilityModelThatViolatesItsSchema() {
        DeviceModelSaveDTO payload = minimalPayload();
        ((ArrayNode) payload.getAttributes()).addObject()
                .put("name", "temperature")
                .put("valueKind", "CONTINUOUS")
                .put("dataType", "UNSUPPORTED");

        assertThrows(IllegalArgumentException.class, () -> service.previewModel(payload));
    }

    @Test
    void previewBuildsUniqueCurrentStateMachineContract() {
        ObjectNode bundle = service.previewModel(minimalPayload());
        JsonNode stateMachine = bundle.path("stateMachineModel");
        JsonNode interfaces = stateMachine.path("interfaces");

        assertEquals(6, interfaces.size());
        Set<String> names = new HashSet<>();
        interfaces.forEach(item -> names.add(item.path("name").asText()));
        assertEquals(6, names.size());
        assertEquals(Set.of("WF_EXECUTE_START", "WF_EXECUTE_ABORT"),
                interfaceSignals(interfaces, "Interface_workflow_in"));
        assertEquals(Set.of(), interfaceSignals(interfaces, "Interface_adapter_in"));
        assertFalse(hasTransition(stateMachine.path("transitions"), "SENT", "RECEIVED", "COMMAND_RECEIVED"));
        assertTrue(hasTransition(stateMachine.path("transitions"), "RUNNING", "RUNNING", "WF_EXECUTE_ABORT"));
        stateMachine.path("transitions").forEach(transition ->
                assertEquals("CMD", transition.path("stateSpace").asText()));

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
    void previewKeepsAutomaticExecutionLifecycleTransition() {
        DeviceModelSaveDTO payload = minimalPayload();
        ObjectNode transition = ((ArrayNode) payload.getStateTransitions()).addObject();
        transition.put("stateSpace", "CMD");
        transition.put("fromStateName", "SENT");
        transition.put("toStateName", "RECEIVED");
        transition.putNull("trigger");
        transition.putArray("actions");

        ObjectNode bundle = service.previewModel(payload);

        assertTrue(hasAutomaticTransition(
                bundle.path("stateMachineModel").path("transitions"), "SENT", "RECEIVED"));
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
        events.putArray("cmdEvents");
        events.putArray("opEvents");
        return contract;
    }
    private DeviceModelSaveDTO minimalPayload() {
        DeviceModelSaveDTO payload = new DeviceModelSaveDTO();
        payload.setModelName("");
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
        events.putArray("cmdEvents");
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
        payload.setStateTransitions(JsonNodeSupport.arrayNode());
        return payload;
    }
}