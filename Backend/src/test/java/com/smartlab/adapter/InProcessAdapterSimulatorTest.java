package com.smartlab.adapter;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.StateMachineEngine;
import com.smartlab.engine.statemachine.StateMachineSendActionEvent;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import com.smartlab.management.entity.resource.device.DeviceInstanceKind;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.management.service.db.resource.adapter.AdapterIndexService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InProcessAdapterSimulatorTest {

    @Test
    void ignoresNonTemporaryInstances() {
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        StateMachineEngine engine = mock(StateMachineEngine.class);
        DeviceInstances virtual = new DeviceInstances();
        virtual.setId(7L);
        virtual.setInstanceKind(DeviceInstanceKind.VIRTUAL);
        when(instances.selectById(7L)).thenReturn(virtual);
        InProcessAdapterSimulator simulator = simulator(instances, mock(DeviceModelsMapper.class),
                mock(AdapterIndexService.class), new AdapterManifestService(), engine, Runnable::run);

        simulator.handleSendAction(startEvent(7L, Map.of()));

        verifyNoInteractions(engine);
    }

    @Test
    void repliesAfterCurrentStackSoSentCanSettle() {
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceModelsMapper models = mock(DeviceModelsMapper.class);
        StateMachineEngine engine = mock(StateMachineEngine.class);
        AdapterIndexService adapters = mock(AdapterIndexService.class);
        DeviceInstances temporary = temporary(8L, 4L);
        when(instances.selectById(8L)).thenReturn(temporary);
        when(models.selectById(4L)).thenReturn(modelWithTransitions());
        when(adapters.requireAdapter("TempAdapter")).thenReturn(adapterIndex());
        AtomicReference<Runnable> queued = new AtomicReference<>();
        InProcessAdapterSimulator simulator = simulator(instances, models, adapters, new AdapterManifestService(),
                engine, queued::set);

        simulator.handleSendAction(startEvent(8L, Map.of()));

        verify(engine, never()).dispatchAdapterEvent(any(), anyString(), any());
        queued.get().run();
        verify(engine).dispatchAdapterEvent(eq(8L), eq("COMMAND_RUNNING"), any());
        verify(engine).dispatchAdapterEvent(eq(8L), eq("COMMAND_COMPLETED"), any());
    }

    @Test
    void dispatchesDeclaredStartAndCompleteEventsFromParsedConfig() {
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceModelsMapper models = mock(DeviceModelsMapper.class);
        StateMachineEngine engine = mock(StateMachineEngine.class);
        AdapterIndexService adapters = mock(AdapterIndexService.class);
        DeviceInstances temporary = temporary(8L, 4L);
        when(instances.selectById(8L)).thenReturn(temporary);
        when(models.selectById(4L)).thenReturn(modelWithTransitions());
        when(adapters.requireAdapter("TempAdapter")).thenReturn(adapterIndex());
        InProcessAdapterSimulator simulator = simulator(instances, models, adapters, new AdapterManifestService(),
                engine, Runnable::run);

        simulator.handleSendAction(startEvent(8L, Map.of()));

        verify(engine).dispatchAdapterEvent(eq(8L), eq("COMMAND_RUNNING"), any());
        verify(engine).dispatchAdapterEvent(eq(8L), eq("COMMAND_COMPLETED"), any());
    }

    @Test
    void neverPatchesTwinAttributesFromCommandParameters() {
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceModelsMapper models = mock(DeviceModelsMapper.class);
        AdapterIndexService adapters = mock(AdapterIndexService.class);
        DeviceInstances temporary = temporary(8L, 4L);
        when(instances.selectById(8L)).thenReturn(temporary);
        DeviceModels model = modelWithTransitions();
        ObjectNode capability = JsonNodeSupport.objectNode();
        capability.put("capabilityName", "heat");
        capability.put("adapterCommandName", "heat");
        capability.putArray("parameterMapping").addObject()
                .put("commandParamName", "targetTemperature")
                .put("capabilityParamName", "parameter_1")
                .put("isFixedValue", false);
        model.setCapabilities(JsonNodeSupport.arrayNode().add(capability));
        ObjectNode mapping = JsonNodeSupport.objectNode();
        mapping.put("adapterAttrName", "temperature");
        mapping.put("modelAttributeName", "attribute_1");
        ((ObjectNode) model.getAdapterContract().path("telemetry")).putArray("attributesMapping").add(mapping);
        when(models.selectById(4L)).thenReturn(model);
        when(adapters.requireAdapter("TempAdapter")).thenReturn(adapterIndex());
        StateMachineEngine engine = mock(StateMachineEngine.class);
        InProcessAdapterSimulator simulator = simulator(instances, models, adapters, new AdapterManifestService(),
                engine, Runnable::run);

        simulator.handleSendAction(startEvent(8L, Map.of("parameter_1", 300)));

        verify(engine).dispatchAdapterEvent(eq(8L), eq("COMMAND_RUNNING"), any());
        verify(engine).dispatchAdapterEvent(eq(8L), eq("COMMAND_COMPLETED"), any());
        assertNull(simulator.takeLastError(8L));
    }

    @Test
    void missingParsedCmdEventRecordsError() {
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceModelsMapper models = mock(DeviceModelsMapper.class);
        AdapterIndexService adapters = mock(AdapterIndexService.class);
        DeviceInstances temporary = temporary(8L, 4L);
        when(instances.selectById(8L)).thenReturn(temporary);
        DeviceModels model = modelWithTransitions();
        when(models.selectById(4L)).thenReturn(model);
        AdapterIndex adapter = adapterIndex();
        ((ObjectNode) adapter.getParsedConfig().path("deviceCategories").get(0)
                .path("deviceTemplate").path("events")).set("cmdEvents", JsonNodeSupport.arrayNode());
        when(adapters.requireAdapter("TempAdapter")).thenReturn(adapter);
        InProcessAdapterSimulator simulator = simulator(instances, models, adapters, new AdapterManifestService(),
                mock(StateMachineEngine.class), Runnable::run);

        simulator.handleSendAction(startEvent(8L, Map.of()));

        assertTrue(simulator.takeLastError(8L).contains("SIM_NO_ADAPTER_EVENT"));
    }

    private InProcessAdapterSimulator simulator(DeviceInstancesMapper instances, DeviceModelsMapper models,
                                                   AdapterIndexService adapters, AdapterManifestService manifests,
                                                   StateMachineEngine engine,
                                                   java.util.concurrent.Executor executor) {
        return new InProcessAdapterSimulator(instances, models, adapters, manifests, engine, executor);
    }

    private DeviceInstances temporary(long id, long modelId) {
        DeviceInstances instance = new DeviceInstances();
        instance.setId(id);
        instance.setDeviceModelId(modelId);
        instance.setInstanceKind(DeviceInstanceKind.TEMPORARY);
        return instance;
    }

    private DeviceModels modelWithTransitions() {
        DeviceModels model = new DeviceModels();
        model.setId(4L);
        ObjectNode contract = JsonNodeSupport.objectNode();
        contract.putObject("config").put("adapterName", "TempAdapter").put("categoryName", "TempSensor");
        contract.putObject("telemetry").putArray("attributesMapping");
        model.setAdapterContract(contract);
        model.setStateTransitions(transitions(
                transition("SENT", "RUNNING", "COMMAND_RUNNING"),
                transition("RUNNING", "COMPLETED", "COMMAND_COMPLETED")));
        return model;
    }

    private AdapterIndex adapterIndex() {
        AdapterIndex adapter = new AdapterIndex();
        adapter.setAdapterName("TempAdapter");
        ObjectNode parsed = JsonNodeSupport.objectNode();
        ObjectNode category = parsed.putArray("deviceCategories").addObject();
        category.put("categoryName", "TempSensor");
        ObjectNode template = category.putObject("deviceTemplate");
        template.put("templateName", "TempSensorTemplate");
        template.putArray("attributes").addObject().put("name", "temperature").put("dataType", "DOUBLE");
        ObjectNode heat = template.putArray("commands").addObject();
        heat.put("name", "heat");
        heat.putArray("parameters").addObject().put("name", "targetTemperature").put("dataType", "DOUBLE");
        ObjectNode events = template.putObject("events");
        events.putArray("cmdEvents")
                .addObject().put("name", "COMMAND_RUNNING").put("description", "running");
        events.withArray("cmdEvents").addObject().put("name", "COMMAND_COMPLETED").put("description", "done");
        adapter.setParsedConfig(parsed);
        return adapter;
    }

    private StateMachineSendActionEvent startEvent(long instanceId, Map<String, Object> parameters) {
        return new StateMachineSendActionEvent(
                instanceId, "Interface_adapter_out", "ADAPTER", "CMD_START",
                "heat", "heat", "msg-1", parameters, JsonNodeSupport.objectNode());
    }

    private ArrayNode transitions(ObjectNode... items) {
        ArrayNode array = JsonNodeSupport.arrayNode();
        for (ObjectNode item : items) {
            array.add(item);
        }
        return array;
    }

    private ObjectNode transition(String from, String to, String signal) {
        ObjectNode node = JsonNodeSupport.objectNode();
        node.put("stateSpace", "CMD");
        node.put("fromStateName", from);
        node.put("toStateName", to);
        node.putObject("trigger").put("interfaceName", "Interface_adapter_in").put("signalName", signal);
        return node;
    }
}
