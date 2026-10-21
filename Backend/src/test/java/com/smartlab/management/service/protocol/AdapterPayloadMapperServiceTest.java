package com.smartlab.management.service.protocol;

import com.smartlab.adapter.AdapterManifestService;
import com.smartlab.engine.statemachine.StateMachineEngine;
import com.smartlab.global.protocol.ProtocolDictionaryService;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import com.smartlab.management.entity.resource.data.DataIndex;
import com.smartlab.management.entity.resource.device.DeviceInstanceKind;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.management.mapper.resource.device.DeviceTwinStatesMapper;
import com.smartlab.management.service.db.resource.adapter.AdapterIndexService;
import com.smartlab.management.service.db.resource.data.DataIndexService;
import com.smartlab.management.service.db.resource.data.DataRecordService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdapterPayloadMapperServiceTest {

    @Test
    void rejectsBindingToAnotherAdapterEvenWhenTheCategoryMatches() {
        DeviceModelsMapper models = mock(DeviceModelsMapper.class);
        AdapterIndexService adapters = mock(AdapterIndexService.class);
        AdapterPayloadMapperService service = new AdapterPayloadMapperService(
                mock(DeviceInstancesMapper.class), models, mock(DeviceTwinStatesMapper.class), adapters,
                new AdapterManifestService(), new ProtocolDictionaryService(),
                mock(DataIndexService.class), mock(DataRecordService.class),
                mock(ApplicationEventPublisher.class), mock(StateMachineEngine.class));

        AdapterIndex adapter = new AdapterIndex();
        adapter.setAdapterName("adapter-B");
        var manifest = JsonNodeSupport.objectNode();
        var category = manifest.putArray("deviceCategories").addObject();
        category.put("categoryName", "Reactor");
        category.putObject("deviceTemplate").put("templateName", "ReactorTemplate");
        category.putArray("devicePoints").addObject()
                .put("devicePoint", "point-1")
                .put("templateName", "ReactorTemplate")
                .put("categoryName", "Reactor");
        adapter.setParsedConfig(manifest);
        when(adapters.requireAdapter("adapter-B")).thenReturn(adapter);

        DeviceModels model = new DeviceModels();
        var contract = JsonNodeSupport.objectNode();
        contract.putObject("config")
                .put("adapterName", "adapter-A")
                .put("categoryName", "Reactor");
        model.setAdapterContract(contract);
        when(models.selectById(9L)).thenReturn(model);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.buildAdapterBinding(9L, "adapter-B", "point-1"));

        assertEquals("设备模型绑定的是 Adapter adapter-A，不能绑定 Adapter adapter-B", error.getMessage());
    }

    @Test
    void virtualBindingClonesPhysicalBindingAndRewritesTopics() {
        AdapterIndexService adapters = mock(AdapterIndexService.class);
        AdapterPayloadMapperService service = new AdapterPayloadMapperService(
                mock(DeviceInstancesMapper.class), mock(DeviceModelsMapper.class),
                mock(DeviceTwinStatesMapper.class), adapters,
                new AdapterManifestService(), new ProtocolDictionaryService(),
                mock(DataIndexService.class), mock(DataRecordService.class),
                mock(ApplicationEventPublisher.class), mock(StateMachineEngine.class));

        DeviceInstances physical = new DeviceInstances();
        physical.setDeviceModelId(9L);
        physical.setInstanceKind(DeviceInstanceKind.PHYSICAL);
        physical.setBoundAdapterName("adapter-A");
        physical.setBoundDevicePoint("point-1");
        var config = JsonNodeSupport.objectNode();
        var stored = config.putObject("adapterBinding");
        stored.put("adapterName", "adapter-A");
        stored.put("devicePoint", "point-1");
        stored.put("templateName", "ReactorTemplate");
        stored.putObject("topics").put("command", "smartlab/adapter/adapter-A/point-1/command");
        physical.setInstanceConfig(config);

        var binding = service.buildVirtualAdapterBinding(physical, "point-1_sim_4");

        assertEquals("point-1_sim_4", binding.path("devicePoint").asText());
        assertEquals("smartlab/adapter/adapter-A/point-1_sim_4/command",
                binding.path("topics").path("command").asText());
        assertEquals("ReactorTemplate", binding.path("templateName").asText());
        verify(adapters, never()).requireAdapter(any());
    }

    @Test
    void commandEventsRequireMessageIdButOperationEventsDoNot() {
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceModelsMapper models = mock(DeviceModelsMapper.class);
        StateMachineEngine stateMachine = mock(StateMachineEngine.class);
        AdapterPayloadMapperService service = new AdapterPayloadMapperService(
                instances, models, mock(DeviceTwinStatesMapper.class), mock(AdapterIndexService.class),
                new AdapterManifestService(), new ProtocolDictionaryService(),
                mock(DataIndexService.class), mock(DataRecordService.class), mock(ApplicationEventPublisher.class), stateMachine);

        DeviceInstances instance = new DeviceInstances();
        instance.setId(7L);
        instance.setDeviceModelId(9L);
        instance.setBoundAdapterName("adapter-1");
        instance.setBoundDevicePoint("point-1");
        instance.setLifecycleStatus("IN_USE");
        when(instances.selectList(any())).thenReturn(List.of(instance));

        DeviceModels model = new DeviceModels();
        var contract = JsonNodeSupport.objectNode();
        var events = contract.putObject("events");
        events.putArray("cmdEvents").addObject().put("eventName", "DONE");
        events.putArray("opEvents").addObject().put("eventName", "FAULT");
        model.setAdapterContract(contract);
        when(models.selectById(9L)).thenReturn(model);

        assertThrows(IllegalArgumentException.class, () -> service.applyAdapterEvent(
                "adapter-1", "point-1", JsonNodeSupport.objectNode().put("eventName", "DONE")));
        assertDoesNotThrow(() -> service.applyAdapterEvent(
                "adapter-1", "point-1", JsonNodeSupport.objectNode().put("eventName", "FAULT")));
        verify(stateMachine).dispatchAdapterEvent(7L, "FAULT", JsonNodeSupport.objectNode().put("eventName", "FAULT"));
    }
    @Test
    void retiredInstancesAreExcludedFromAdapterRoutes() {
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        AdapterPayloadMapperService service = new AdapterPayloadMapperService(
                instances, mock(DeviceModelsMapper.class), mock(DeviceTwinStatesMapper.class),
                mock(AdapterIndexService.class), new AdapterManifestService(), new ProtocolDictionaryService(),
                mock(DataIndexService.class), mock(DataRecordService.class), mock(ApplicationEventPublisher.class), mock(StateMachineEngine.class));
        DeviceInstances retired = new DeviceInstances();
        retired.setId(8L);
        retired.setBoundAdapterName("adapter-1");
        retired.setBoundDevicePoint("point-1");
        retired.setLifecycleStatus("RETIRED");
        when(instances.selectList(any())).thenReturn(List.of(retired));

        service.refreshAdapterRouteTable();

        org.junit.jupiter.api.Assertions.assertNull(service.resolveAdapterRoute("adapter-1", "point-1"));
    }

    @Test
    void rejectsCommandParameterWhoseValueDoesNotMatchDeclaredDataType() {
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceModelsMapper models = mock(DeviceModelsMapper.class);
        AdapterPayloadMapperService service = new AdapterPayloadMapperService(
                instances, models, mock(DeviceTwinStatesMapper.class), mock(AdapterIndexService.class),
                new AdapterManifestService(), new ProtocolDictionaryService(),
                mock(DataIndexService.class), mock(DataRecordService.class), mock(ApplicationEventPublisher.class), mock(StateMachineEngine.class));
        DeviceInstances instance = new DeviceInstances();
        instance.setId(7L);
        instance.setDeviceModelId(9L);
        instance.setBoundAdapterName("adapter-1");
        instance.setBoundDevicePoint("point-1");
        instance.setLifecycleStatus("IN_USE");
        when(instances.selectById(7L)).thenReturn(instance);

        DeviceModels model = new DeviceModels();
        model.setCapabilities(JsonNodeSupport.arrayNode());
        var contract = JsonNodeSupport.objectNode();
        contract.putObject("config").put("protocol", "MQTT");
        contract.putArray("commands").addObject()
                .put("commandName", "heat")
                .putArray("commandParameters").addObject()
                .put("paramName", "durationSec").put("dataType", "INTEGER");
        model.setAdapterContract(contract);
        when(models.selectById(9L)).thenReturn(model);

        assertThrows(IllegalArgumentException.class, () -> service.buildCommandMessage(
                "7", "heat", "msg-1", java.util.Map.of("durationSec", "sixty")));
    }

    @Test
    void omitsUnmappedOptionalCapabilityParameter() {
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceModelsMapper models = mock(DeviceModelsMapper.class);
        AdapterPayloadMapperService service = new AdapterPayloadMapperService(
                instances, models, mock(DeviceTwinStatesMapper.class), mock(AdapterIndexService.class),
                new AdapterManifestService(), new ProtocolDictionaryService(),
                mock(DataIndexService.class), mock(DataRecordService.class), mock(ApplicationEventPublisher.class), mock(StateMachineEngine.class));
        DeviceInstances instance = new DeviceInstances();
        instance.setId(7L);
        instance.setDeviceModelId(9L);
        instance.setBoundAdapterName("adapter-1");
        instance.setBoundDevicePoint("point-1");
        instance.setLifecycleStatus("IN_USE");
        when(instances.selectById(7L)).thenReturn(instance);

        DeviceModels model = new DeviceModels();
        var capability = JsonNodeSupport.objectNode();
        capability.put("capabilityName", "heat");
        capability.put("adapterCommandName", "heat");
        capability.putArray("parameters")
                .add(JsonNodeSupport.objectNode().put("name", "duration").put("dataType", "INTEGER").put("required", true))
                .add(JsonNodeSupport.objectNode().put("name", "temperature2").put("dataType", "DOUBLE").put("required", false));
        capability.putArray("parameterMapping")
                .add(JsonNodeSupport.objectNode().put("commandParamName", "duration").put("capabilityParamName", "duration").put("isFixedValue", false))
                .add(JsonNodeSupport.objectNode().put("commandParamName", "temperature2").put("capabilityParamName", "temperature2").put("isFixedValue", false));
        model.setCapabilities(JsonNodeSupport.MAPPER.createArrayNode().add(capability));
        var contract = JsonNodeSupport.objectNode();
        contract.putObject("config").put("protocol", "MQTT");
        var command = contract.putArray("commands").addObject().put("commandName", "heat");
        command.putArray("commandParameters")
                .add(JsonNodeSupport.objectNode().put("paramName", "duration").put("dataType", "INTEGER"))
                .add(JsonNodeSupport.objectNode().put("paramName", "temperature2").put("dataType", "DOUBLE"));
        model.setAdapterContract(contract);
        when(models.selectById(9L)).thenReturn(model);

        var message = service.buildCommandMessage("7", "heat", "msg-1", java.util.Map.of("duration", 30));

        assertEquals(30, message.path("payload").path("parameters").path("duration").asInt());
        assertFalse(message.path("payload").path("parameters").has("temperature2"));
    }

    @Test
    void booleanFixedValueIsSentAsBoolean() {
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceModelsMapper models = mock(DeviceModelsMapper.class);
        AdapterPayloadMapperService service = new AdapterPayloadMapperService(
                instances, models, mock(DeviceTwinStatesMapper.class), mock(AdapterIndexService.class),
                new AdapterManifestService(), new ProtocolDictionaryService(),
                mock(DataIndexService.class), mock(DataRecordService.class), mock(ApplicationEventPublisher.class), mock(StateMachineEngine.class));
        DeviceInstances instance = new DeviceInstances();
        instance.setId(7L);
        instance.setDeviceModelId(9L);
        instance.setBoundAdapterName("adapter-1");
        instance.setBoundDevicePoint("point-1");
        instance.setLifecycleStatus("IN_USE");
        when(instances.selectById(7L)).thenReturn(instance);

        DeviceModels model = new DeviceModels();
        var capability = JsonNodeSupport.objectNode();
        capability.put("capabilityName", "mute");
        capability.put("adapterCommandName", "setAlarmMute");
        capability.putArray("parameters");
        capability.putArray("parameterMapping").addObject()
                .put("commandParamName", "muted")
                .put("isFixedValue", true)
                .put("fixedValue", true);
        model.setCapabilities(JsonNodeSupport.MAPPER.createArrayNode().add(capability));
        var contract = JsonNodeSupport.objectNode();
        contract.putObject("config").put("protocol", "MQTT");
        contract.putArray("commands").addObject()
                .put("commandName", "setAlarmMute")
                .putArray("commandParameters").addObject()
                .put("paramName", "muted").put("dataType", "BOOLEAN");
        model.setAdapterContract(contract);
        when(models.selectById(9L)).thenReturn(model);

        var message = service.buildCommandMessage("7", "mute", "msg-1", java.util.Map.of());

        assertTrue(message.path("payload").path("parameters").path("muted").isBoolean());
        assertTrue(message.path("payload").path("parameters").path("muted").booleanValue());
    }

    @Test
    void stringFixedBooleanIsRejected() {
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceModelsMapper models = mock(DeviceModelsMapper.class);
        AdapterPayloadMapperService service = new AdapterPayloadMapperService(
                instances, models, mock(DeviceTwinStatesMapper.class), mock(AdapterIndexService.class),
                new AdapterManifestService(), new ProtocolDictionaryService(),
                mock(DataIndexService.class), mock(DataRecordService.class), mock(ApplicationEventPublisher.class), mock(StateMachineEngine.class));
        DeviceInstances instance = new DeviceInstances();
        instance.setId(7L);
        instance.setDeviceModelId(9L);
        instance.setBoundAdapterName("adapter-1");
        instance.setBoundDevicePoint("point-1");
        instance.setLifecycleStatus("IN_USE");
        when(instances.selectById(7L)).thenReturn(instance);

        DeviceModels model = new DeviceModels();
        var capability = JsonNodeSupport.objectNode();
        capability.put("capabilityName", "mute");
        capability.put("adapterCommandName", "setAlarmMute");
        capability.putArray("parameters");
        capability.putArray("parameterMapping").addObject()
                .put("commandParamName", "muted")
                .put("isFixedValue", true)
                .put("fixedValue", "true");
        model.setCapabilities(JsonNodeSupport.MAPPER.createArrayNode().add(capability));
        var contract = JsonNodeSupport.objectNode();
        contract.putObject("config").put("protocol", "MQTT");
        contract.putArray("commands").addObject()
                .put("commandName", "setAlarmMute")
                .putArray("commandParameters").addObject()
                .put("paramName", "muted").put("dataType", "BOOLEAN");
        model.setAdapterContract(contract);
        when(models.selectById(9L)).thenReturn(model);

        assertThrows(IllegalArgumentException.class,
                () -> service.buildCommandMessage("7", "mute", "msg-1", java.util.Map.of()));
    }

    @Test
    void rejectsTelemetryWhoseValueDoesNotMatchTheResolvedAttributeType() {
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceTwinStatesMapper twins = mock(DeviceTwinStatesMapper.class);
        AdapterPayloadMapperService service = new AdapterPayloadMapperService(
                instances, mock(DeviceModelsMapper.class), twins, mock(AdapterIndexService.class),
                new AdapterManifestService(), new ProtocolDictionaryService(),
                mock(DataIndexService.class), mock(DataRecordService.class), mock(ApplicationEventPublisher.class), mock(StateMachineEngine.class));
        DeviceInstances instance = new DeviceInstances();
        instance.setId(7L);
        instance.setDeviceModelId(9L);
        instance.setBoundAdapterName("adapter-1");
        instance.setBoundDevicePoint("point-1");
        instance.setLifecycleStatus("IN_USE");
        var config = JsonNodeSupport.objectNode();
        var binding = config.putObject("adapterBinding");
        binding.putObject("rawToModelAttribute").put("MW0", "temperature");
        binding.putArray("resolvedAttributes").addObject()
                .put("modelAttributeName", "temperature")
                .put("templateAttributeName", "MW0")
                .put("dataType", "DOUBLE");
        instance.setInstanceConfig(config);
        when(instances.selectList(any())).thenReturn(List.of(instance));
        var telemetry = JsonNodeSupport.objectNode();
        telemetry.put("timestamp", 1_700_000_000_000L);
        telemetry.putObject("telemetryData").put("MW0", "not-a-number");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.applyTelemetry("adapter-1", "point-1", telemetry));

        org.junit.jupiter.api.Assertions.assertEquals(
                "telemetry.MW0 类型不匹配，要求 DOUBLE，实际 STRING", error.getMessage());
        org.mockito.Mockito.verify(twins, org.mockito.Mockito.never()).insert(
                any(com.smartlab.management.entity.resource.device.DeviceTwinStates.class));
        org.mockito.Mockito.verify(twins, org.mockito.Mockito.never()).updateById(
                any(com.smartlab.management.entity.resource.device.DeviceTwinStates.class));
        org.mockito.Mockito.verify(twins, org.mockito.Mockito.never()).patchAttributesOnly(
                any(), any(), any());
    }
    @Test
    void telemetryPatchesOnlyCurrentAdapterFieldsWithSourceTime() {
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceTwinStatesMapper twins = mock(DeviceTwinStatesMapper.class);
        DataIndexService dataSets = mock(DataIndexService.class);
        DataRecordService records = mock(DataRecordService.class);
        AdapterPayloadMapperService service = new AdapterPayloadMapperService(
                instances, mock(DeviceModelsMapper.class), twins, mock(AdapterIndexService.class),
                new AdapterManifestService(), new ProtocolDictionaryService(),
                dataSets, records, mock(ApplicationEventPublisher.class), mock(StateMachineEngine.class));
        DeviceInstances instance = new DeviceInstances();
        instance.setId(7L);
        instance.setDeviceModelId(9L);
        instance.setBoundAdapterName("adapter-1");
        instance.setBoundDevicePoint("point-1");
        instance.setLifecycleStatus("IN_USE");
        var config = JsonNodeSupport.objectNode();
        var binding = config.putObject("adapterBinding");
        binding.putObject("rawToModelAttribute").put("MW0", "temperature");
        binding.putArray("resolvedAttributes").addObject()
                .put("modelAttributeName", "temperature")
                .put("templateAttributeName", "MW0")
                .put("dataType", "DOUBLE");
        instance.setInstanceConfig(config);
        when(instances.selectList(any())).thenReturn(List.of(instance));
        DeviceTwinStates twin = new DeviceTwinStates();
        twin.setId(5L);
        twin.setInstanceId(7L);
        twin.setCurrentAttr(JsonNodeSupport.objectNode().put("pressure", 8.0));
        when(twins.selectOne(any())).thenReturn(twin);
        when(twins.patchAttributesOnly(eq(7L), any(), any())).thenReturn(1);
        DataIndex dataSet = new DataIndex();
        dataSet.setId(33L);
        when(dataSets.listByDeviceInstance(7L)).thenReturn(List.of(dataSet));
        long timestamp = 1_700_000_000_123L;
        var telemetry = JsonNodeSupport.objectNode().put("timestamp", timestamp);
        telemetry.putObject("telemetryData").put("MW0", 21.5);

        service.applyTelemetry("adapter-1", "point-1", telemetry);

        verify(twins).patchAttributesOnly(eq(7L), argThat(json -> json.contains("\"temperature\":21.5")
                && !json.contains("pressure")), any());
        verify(twins, never()).patchAttributes(any(), any(), any(), any());
        verify(records).appendRecord(eq(33L), argThat(row -> row.size() == 1
                && ((Number) row.get("temperature")).doubleValue() == 21.5), eq(Instant.ofEpochMilli(timestamp)));
    }

    @Test
    void batchTelemetryMapsAllItemsAndAppendsRecords() {
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceTwinStatesMapper twins = mock(DeviceTwinStatesMapper.class);
        DataIndexService dataSets = mock(DataIndexService.class);
        DataRecordService records = mock(DataRecordService.class);
        AdapterPayloadMapperService service = new AdapterPayloadMapperService(
                instances, mock(DeviceModelsMapper.class), twins, mock(AdapterIndexService.class),
                new AdapterManifestService(), new ProtocolDictionaryService(),
                dataSets, records, mock(ApplicationEventPublisher.class), mock(StateMachineEngine.class));
        DeviceInstances instance = new DeviceInstances();
        instance.setId(17L);
        instance.setDeviceModelId(9L);
        instance.setBoundAdapterName("adapter-1");
        instance.setBoundDevicePoint("point-1");
        instance.setLifecycleStatus("IN_USE");
        var config = JsonNodeSupport.objectNode();
        var binding = config.putObject("adapterBinding");
        binding.putObject("rawToModelAttribute").put("MW0", "temperature");
        binding.putArray("resolvedAttributes").addObject()
                .put("modelAttributeName", "temperature")
                .put("templateAttributeName", "MW0")
                .put("dataType", "DOUBLE");
        instance.setInstanceConfig(config);
        when(instances.selectList(any())).thenReturn(List.of(instance));
        DeviceTwinStates twin = new DeviceTwinStates();
        twin.setId(5L);
        twin.setInstanceId(17L);
        when(twins.selectOne(any())).thenReturn(twin);
        when(twins.patchAttributesOnly(eq(17L), any(), any())).thenReturn(1);
        DataIndex dataSet = new DataIndex();
        dataSet.setId(33L);
        when(dataSets.listByDeviceInstance(17L)).thenReturn(List.of(dataSet));

        var batch = JsonNodeSupport.objectNode();
        batch.put("formatType", "BATCH");
        batch.put("timestamp", 1_700_000_000_000L);
        batch.put("adapterName", "adapter-1");
        batch.put("devicePoint", "point-1");
        var items = batch.putArray("items");
        items.addObject().put("timestamp", 1_700_000_001_000L).putObject("telemetryData").put("MW0", 25.0);
        items.addObject().put("timestamp", 1_700_000_002_000L).putObject("telemetryData").put("MW0", 26.0);

        service.applyBatchTelemetry("adapter-1", "point-1", batch);

        verify(records).appendRecords(eq(33L), argThat(list -> list.size() == 2
                && ((Number) list.get(0).get("temperature")).doubleValue() == 25.0
                && ((Number) list.get(1).get("temperature")).doubleValue() == 26.0), any());
        verify(twins).patchAttributesOnly(eq(17L), argThat(json -> json.contains("\"temperature\":26.0")), any());
    }

    @Test
    void singleSamplesOfOneDeviceShareOneAppendAndKeepTheLatestTwinValue() {
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceTwinStatesMapper twins = mock(DeviceTwinStatesMapper.class);
        DataIndexService dataSets = mock(DataIndexService.class);
        DataRecordService records = mock(DataRecordService.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        AdapterPayloadMapperService service = new AdapterPayloadMapperService(
                instances, mock(DeviceModelsMapper.class), twins, mock(AdapterIndexService.class),
                new AdapterManifestService(), new ProtocolDictionaryService(),
                dataSets, records, events, mock(StateMachineEngine.class));
        DeviceInstances instance = new DeviceInstances();
        instance.setId(17L);
        instance.setDeviceModelId(9L);
        instance.setBoundAdapterName("adapter-1");
        instance.setBoundDevicePoint("point-1");
        instance.setLifecycleStatus("IN_USE");
        var config = JsonNodeSupport.objectNode();
        var binding = config.putObject("adapterBinding");
        binding.putObject("rawToModelAttribute").put("MW0", "temperature");
        binding.putArray("resolvedAttributes").addObject()
                .put("modelAttributeName", "temperature")
                .put("templateAttributeName", "MW0")
                .put("dataType", "DOUBLE");
        instance.setInstanceConfig(config);
        when(instances.selectList(any())).thenReturn(List.of(instance));
        DeviceTwinStates twin = new DeviceTwinStates();
        twin.setId(5L);
        twin.setInstanceId(17L);
        when(twins.selectOne(any())).thenReturn(twin);
        when(twins.patchAttributesOnly(eq(17L), any(), any())).thenReturn(1);
        DataIndex dataSet = new DataIndex();
        dataSet.setId(33L);
        when(dataSets.listByDeviceInstance(17L)).thenReturn(List.of(dataSet));
        var first = JsonNodeSupport.objectNode().put("timestamp", 1_000L);
        first.putObject("telemetryData").put("MW0", 23.4);
        var second = JsonNodeSupport.objectNode().put("timestamp", 4_000L);
        second.putObject("telemetryData").put("MW0", 23.5);
        var rejected = JsonNodeSupport.objectNode().put("timestamp", 7_000L);
        rejected.putObject("telemetryData").put("MW0", "bad");

        service.applyTelemetrySamples("adapter-1", "point-1", List.of(first, rejected, second));

        verify(records).appendRecords(eq(33L), argThat(list -> list.size() == 2
                && ((Number) list.get(0).get("temperature")).doubleValue() == 23.4
                && list.get(0).get("create_time").equals(Instant.ofEpochMilli(1_000L))
                && ((Number) list.get(1).get("temperature")).doubleValue() == 23.5
                && list.get(1).get("create_time").equals(Instant.ofEpochMilli(4_000L))), eq(Instant.ofEpochMilli(4_000L)));
        verify(records, never()).appendRecord(any(), any(), any());
        verify(twins).patchAttributesOnly(eq(17L), argThat(json -> json.contains("\"temperature\":23.5")), any());
        verify(events).publishEvent(argThat((Object event) -> {
            if (!(event instanceof com.smartlab.global.event.DeviceTelemetryUpdatedEvent telemetry)) {
                return false;
            }
            return telemetry.occurredAt().equals(Instant.ofEpochMilli(4_000L))
                    && telemetry.attributes().path("temperature").asDouble() == 23.5;
        }));
    }

}
