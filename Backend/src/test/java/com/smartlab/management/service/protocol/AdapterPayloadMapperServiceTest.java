package com.smartlab.management.service.protocol;

import com.smartlab.adapter.AdapterManifestService;
import com.smartlab.engine.statemachine.StateMachineEngine;
import com.smartlab.global.protocol.ProtocolDictionaryService;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.management.mapper.resource.device.DeviceTwinStatesMapper;
import com.smartlab.management.service.db.resource.adapter.AdapterIndexService;
import com.smartlab.management.service.db.resource.data.DataIndexService;
import com.smartlab.management.service.db.resource.data.DataRecordService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdapterPayloadMapperServiceTest {

    @Test
    void commandEventsRequireMessageIdButOperationEventsDoNot() {
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceModelsMapper models = mock(DeviceModelsMapper.class);
        StateMachineEngine stateMachine = mock(StateMachineEngine.class);
        AdapterPayloadMapperService service = new AdapterPayloadMapperService(
                instances, models, mock(DeviceTwinStatesMapper.class), mock(AdapterIndexService.class),
                new AdapterManifestService(), new ProtocolDictionaryService(),
                mock(DataIndexService.class), mock(DataRecordService.class), stateMachine);

        DeviceInstances instance = new DeviceInstances();
        instance.setId(7L);
        instance.setDeviceModelId(9L);
        instance.setBoundAdapterName("adapter-1");
        instance.setBoundDevicePoint("point-1");
        instance.setLifecycleStatus("使用中");
        when(instances.selectList(any())).thenReturn(List.of(instance));

        DeviceModels model = new DeviceModels();
        var contract = JsonNodeSupport.objectNode();
        var events = contract.putObject("events");
        events.putArray("cmdEvents").addObject().put("name", "DONE");
        events.putArray("opEvents").addObject().put("name", "FAULT");
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
                mock(DataIndexService.class), mock(DataRecordService.class), mock(StateMachineEngine.class));
        DeviceInstances retired = new DeviceInstances();
        retired.setId(8L);
        retired.setBoundAdapterName("adapter-1");
        retired.setBoundDevicePoint("point-1");
        retired.setLifecycleStatus("已注销");
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
                mock(DataIndexService.class), mock(DataRecordService.class), mock(StateMachineEngine.class));
        DeviceInstances instance = new DeviceInstances();
        instance.setId(7L);
        instance.setDeviceModelId(9L);
        instance.setBoundAdapterName("adapter-1");
        instance.setBoundDevicePoint("point-1");
        instance.setLifecycleStatus("使用中");
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
    void rejectsTelemetryWhoseValueDoesNotMatchTheResolvedAttributeType() {
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceTwinStatesMapper twins = mock(DeviceTwinStatesMapper.class);
        AdapterPayloadMapperService service = new AdapterPayloadMapperService(
                instances, mock(DeviceModelsMapper.class), twins, mock(AdapterIndexService.class),
                new AdapterManifestService(), new ProtocolDictionaryService(),
                mock(DataIndexService.class), mock(DataRecordService.class), mock(StateMachineEngine.class));
        DeviceInstances instance = new DeviceInstances();
        instance.setId(7L);
        instance.setDeviceModelId(9L);
        instance.setBoundAdapterName("adapter-1");
        instance.setBoundDevicePoint("point-1");
        instance.setLifecycleStatus("使用中");
        var config = JsonNodeSupport.objectNode();
        var binding = config.putObject("adapterBinding");
        binding.putObject("rawToModelAttribute").put("MW0", "temperature");
        binding.putArray("resolvedAttributes").addObject()
                .put("modelAttributeName", "temperature")
                .put("rawAttributeName", "MW0")
                .put("dataType", "DOUBLE");
        instance.setInstanceConfig(config);
        when(instances.selectList(any())).thenReturn(List.of(instance));
        var telemetry = JsonNodeSupport.objectNode();
        telemetry.putObject("data").put("MW0", "not-a-number");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.applyTelemetry("adapter-1", "point-1", telemetry));

        org.junit.jupiter.api.Assertions.assertEquals(
                "telemetry.MW0 类型不匹配，要求 DOUBLE，实际 STRING", error.getMessage());
        org.mockito.Mockito.verify(twins, org.mockito.Mockito.never()).insert(
                any(com.smartlab.management.entity.resource.device.DeviceTwinStates.class));
        org.mockito.Mockito.verify(twins, org.mockito.Mockito.never()).updateById(
                any(com.smartlab.management.entity.resource.device.DeviceTwinStates.class));
    }
}