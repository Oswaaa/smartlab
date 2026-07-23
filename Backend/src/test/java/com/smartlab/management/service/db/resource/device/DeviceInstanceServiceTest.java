package com.smartlab.management.service.db.resource.device;

import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.management.mapper.resource.device.DeviceTwinStatesMapper;
import com.smartlab.management.service.db.resource.data.DataIndexService;
import com.smartlab.management.service.protocol.AdapterPayloadMapperService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceInstanceServiceTest {
    @Test
    void newInstanceDefaultsToInUse() {
        Fixture fixture = new Fixture();
        doAnswer(invocation -> {
            DeviceInstances instance = invocation.getArgument(0);
            instance.setId(11L);
            return 1;
        }).when(fixture.instances).insert(any(DeviceInstances.class));
        DeviceInstances saved = fixture.service.savePayload(Map.of("deviceModelId", 3L, "instanceName", "Reactor-01"));
        assertEquals("使用中", saved.getLifecycleStatus());
    }

    @Test
    void retiredInstanceCannotBeEdited() {
        Fixture fixture = new Fixture();
        DeviceInstances retired = instance(7L, "已注销");
        when(fixture.instances.selectById(7L)).thenReturn(retired);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> fixture.service.savePayload(Map.of("instanceId", 7L, "instanceName", "changed")));
        assertEquals("设备实例已注销，不能继续修改", error.getMessage());
        verify(fixture.instances, never()).updateById(any(DeviceInstances.class));
    }

    @Test
    void retirementKeepsInstanceAndTwinSnapshot() {
        Fixture fixture = new Fixture();
        DeviceInstances active = instance(7L, "使用中");
        DeviceTwinStates twin = new DeviceTwinStates();
        twin.setInstanceId(7L);
        twin.setCurrentCmdState("IDLE");
        when(fixture.instances.selectById(7L)).thenReturn(active);
        when(fixture.twins.selectOne(any())).thenReturn(twin);
        DeviceInstances retired = fixture.service.retire("7");
        assertEquals("已注销", retired.getLifecycleStatus());
        verify(fixture.instances).updateById(active);
        verify(fixture.instances, never()).deleteById(any(java.io.Serializable.class));
        verify(fixture.twins, never()).delete(any());
        verify(fixture.routes).refreshAdapterRouteTable();
    }

    @Test
    void retirementRejectsRunningCommand() {
        Fixture fixture = new Fixture();
        DeviceInstances active = instance(7L, "使用中");
        DeviceTwinStates twin = new DeviceTwinStates();
        twin.setCurrentCmdState("RUNNING");
        when(fixture.instances.selectById(7L)).thenReturn(active);
        when(fixture.twins.selectOne(any())).thenReturn(twin);
        assertThrows(IllegalStateException.class, () -> fixture.service.retire("7"));
        verify(fixture.instances, never()).updateById(any(DeviceInstances.class));
    }

    private static DeviceInstances instance(Long id, String status) {
        DeviceInstances instance = new DeviceInstances();
        instance.setId(id);
        instance.setDeviceModelId(3L);
        instance.setLifecycleStatus(status);
        return instance;
    }

    private static final class Fixture {
        final DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        final DeviceTwinStatesMapper twins = mock(DeviceTwinStatesMapper.class);
        final DataIndexService data = mock(DataIndexService.class);
        final AdapterPayloadMapperService routes = mock(AdapterPayloadMapperService.class);
        final DeviceModelsMapper models = mock(DeviceModelsMapper.class);
        final DeviceComponentService components = mock(DeviceComponentService.class);
        final DeviceInstanceService service = new DeviceInstanceService(instances, twins, data, routes, models, components);
    }
}
