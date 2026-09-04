package com.smartlab.management.service.db.resource.device;

import com.smartlab.global.event.DeviceInstanceDeletedEvent;
import com.smartlab.global.event.DeviceInstanceRetiredEvent;
import com.smartlab.management.entity.resource.data.DataIndex;
import com.smartlab.management.entity.resource.device.DeviceInstanceKind;
import com.smartlab.management.entity.resource.device.DeviceInstanceLifecycle;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.management.mapper.resource.device.DeviceTwinStatesMapper;
import com.smartlab.management.service.db.resource.data.DataIndexService;
import com.smartlab.management.service.protocol.AdapterPayloadMapperService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
        assertEquals(DeviceInstanceLifecycle.IN_USE, saved.getLifecycleStatus());
        assertEquals(DeviceInstanceKind.PHYSICAL, saved.getInstanceKind());
    }

    @Test
    void createLocksAndValidatesModelBeforeInsertingInstance() {
        Fixture fixture = new Fixture();
        DeviceModels runtimeModel = new DeviceModels();
        runtimeModel.setId(3L);
        when(fixture.modelService.requireRuntimeReadyForUpdate(3L)).thenReturn(runtimeModel);
        doAnswer(invocation -> {
            DeviceInstances instance = invocation.getArgument(0);
            instance.setId(11L);
            return 1;
        }).when(fixture.instances).insert(any(DeviceInstances.class));

        fixture.service.savePayload(Map.of("deviceModelId", 3L, "instanceName", "Reactor-01"));

        InOrder order = inOrder(fixture.modelService, fixture.instances);
        order.verify(fixture.modelService).requireRuntimeReadyForUpdate(3L);
        order.verify(fixture.instances).insert(any(DeviceInstances.class));
    }

    @Test
    void createInstanceRequiresRuntimeReadyModelBeforeAnyWrite() {
        Fixture fixture = new Fixture();
        when(fixture.modelService.requireRuntimeReadyForUpdate(7L))
                .thenThrow(new IllegalArgumentException("设备模型不完整"));

        Map<String, Object> payload = Map.of(
                "deviceModelId", 7L,
                "instanceName", "device-1");

        assertThrows(IllegalArgumentException.class, () -> fixture.service.savePayload(payload));
        verify(fixture.modelService).requireRuntimeReadyForUpdate(7L);
        verify(fixture.instances, never()).insert(any(DeviceInstances.class));
        verify(fixture.twins, never()).insert(any(DeviceTwinStates.class));
        verifyNoInteractions(fixture.data, fixture.routes, fixture.models, fixture.components);
    }

    @Test
    void existingInstanceCannotChangeDeviceModel() {
        Fixture fixture = new Fixture();
        DeviceInstances existing = instance(10L, DeviceInstanceLifecycle.IN_USE);
        when(fixture.instances.selectById(10L)).thenReturn(existing);

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", 10L);
        payload.put("deviceModelId", 8L);
        payload.put("instanceName", "device-1");

        assertThrows(IllegalStateException.class, () -> fixture.service.savePayload(payload));
        verify(fixture.instances, never()).updateById(any(DeviceInstances.class));
        verifyNoInteractions(fixture.twins, fixture.data, fixture.routes, fixture.models, fixture.components);
    }

    @Test
    void existingInstanceUpdateInheritsDeviceModelWhenOmitted() {
        Fixture fixture = new Fixture();
        DeviceInstances existing = instance(10L, DeviceInstanceLifecycle.IN_USE);
        when(fixture.instances.selectById(10L)).thenReturn(existing);

        DeviceInstances updated = fixture.service.savePayload(Map.of(
                "id", 10L,
                "instanceName", "device-1"));

        assertEquals(3L, updated.getDeviceModelId());
        verify(fixture.instances).updateById(updated);
    }
    @Test
    void retiredInstanceCannotBeEdited() {
        Fixture fixture = new Fixture();
        DeviceInstances retired = instance(7L, DeviceInstanceLifecycle.RETIRED);
        when(fixture.instances.selectById(7L)).thenReturn(retired);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> fixture.service.savePayload(Map.of("instanceId", 7L, "instanceName", "changed")));
        assertEquals("设备实例已注销，不能继续修改", error.getMessage());
        verify(fixture.instances, never()).updateById(any(DeviceInstances.class));
    }

    @Test
    void retirementKeepsInstanceAndTwinSnapshot() {
        Fixture fixture = new Fixture();
        DeviceInstances active = instance(7L, DeviceInstanceLifecycle.IN_USE);
        DeviceTwinStates twin = new DeviceTwinStates();
        twin.setInstanceId(7L);
        twin.setCurrentCmdState("IDLE");
        when(fixture.instances.selectById(7L)).thenReturn(active);
        when(fixture.twins.selectOne(any())).thenReturn(twin);
        DeviceInstances retired = fixture.service.retire("7");
        assertEquals(DeviceInstanceLifecycle.RETIRED, retired.getLifecycleStatus());
        verify(fixture.instances).updateById(active);
        verify(fixture.instances, never()).deleteById(any(java.io.Serializable.class));
        verify(fixture.twins, never()).delete(any());
        verify(fixture.routes).refreshAdapterRouteTable();
        verify(fixture.events).publishEvent(any(DeviceInstanceRetiredEvent.class));
    }

    @Test
    void createTemporarySkipsDataIndexAndBom() {
        Fixture fixture = new Fixture();
        DeviceModels model = new DeviceModels();
        model.setId(3L);
        model.setModelName("Reactor");
        when(fixture.modelService.requireRuntimeReady(3L)).thenReturn(model);
        when(fixture.models.selectById(3L)).thenReturn(model);
        doAnswer(invocation -> {
            DeviceInstances instance = invocation.getArgument(0);
            instance.setId(22L);
            return 1;
        }).when(fixture.instances).insert(any(DeviceInstances.class));

        DeviceInstances saved = fixture.service.createTemporary(3L);

        assertEquals(DeviceInstanceKind.TEMPORARY, saved.getInstanceKind());
        verify(fixture.data, never()).createDefaultDataSetsForDeviceInstance(any(), any(), any());
        verifyNoInteractions(fixture.components);
        verify(fixture.twins).insert(any(DeviceTwinStates.class));
    }

    @Test
    void createVirtualCreatesDefaultDataSetsWithoutBom() {
        Fixture fixture = new Fixture();
        DeviceModels model = new DeviceModels();
        model.setId(3L);
        model.setModelName("Reactor");
        when(fixture.modelService.requireRuntimeReady(3L)).thenReturn(model);
        when(fixture.models.selectById(3L)).thenReturn(model);
        when(fixture.routes.buildVirtualAdapterBinding(any(DeviceInstances.class), eq("Reactor1_sim_11")))
                .thenReturn(com.smartlab.global.util.JsonNodeSupport.objectNode());
        when(fixture.instances.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            DeviceInstances instance = invocation.getArgument(0);
            instance.setId(24L);
            return 1;
        }).when(fixture.instances).insert(any(DeviceInstances.class));

        DeviceInstances physical = instance(7L, DeviceInstanceLifecycle.IN_USE);
        physical.setInstanceKind(DeviceInstanceKind.PHYSICAL);
        physical.setBoundAdapterName("adapter-a");
        physical.setBoundDevicePoint("Reactor1");
        DeviceInstances saved = fixture.service.createVirtual(physical, "Reactor1_sim_11");

        assertEquals(DeviceInstanceKind.VIRTUAL, saved.getInstanceKind());
        assertEquals("adapter-a", saved.getBoundAdapterName());
        assertEquals("Reactor1_sim_11", saved.getBoundDevicePoint());
        verify(fixture.data).createDefaultDataSetsForDeviceInstance(eq(3L), eq(24L), any());
        verifyNoInteractions(fixture.components);
        verify(fixture.routes).refreshAdapterRouteTable();
        verify(fixture.twins).insert(any(DeviceTwinStates.class));
    }

    @Test
    void deleteVirtualRejectsPhysicalInstance() {
        Fixture fixture = new Fixture();
        DeviceInstances physical = instance(7L, DeviceInstanceLifecycle.IN_USE);
        physical.setInstanceKind(DeviceInstanceKind.PHYSICAL);
        when(fixture.instances.selectById(7L)).thenReturn(physical);

        assertThrows(IllegalStateException.class, () -> fixture.service.deleteVirtual(7L));
        verify(fixture.instances, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    void deleteVirtualRemovesOnlyVirtualRow() {
        Fixture fixture = new Fixture();
        DeviceInstances virtual = instance(24L, DeviceInstanceLifecycle.IN_USE);
        virtual.setInstanceKind(DeviceInstanceKind.VIRTUAL);
        when(fixture.instances.selectById(24L)).thenReturn(virtual);

        fixture.service.deleteVirtual(24L);

        verify(fixture.events).publishEvent(new DeviceInstanceRetiredEvent(24L));
        verify(fixture.instances).deleteById(24L);
        verify(fixture.twins).delete(any());
        verify(fixture.routes).refreshAdapterRouteTable();
        verify(fixture.events).publishEvent(new DeviceInstanceDeletedEvent(24L));
    }

    @Test
    void deleteVirtualDropsRemainingDatasets() {
        Fixture fixture = new Fixture();
        DeviceInstances virtual = instance(24L, DeviceInstanceLifecycle.IN_USE);
        virtual.setInstanceKind(DeviceInstanceKind.VIRTUAL);
        when(fixture.instances.selectById(24L)).thenReturn(virtual);
        DataIndex remaining = new DataIndex();
        remaining.setId(91L);
        when(fixture.data.listByDeviceInstance(24L)).thenReturn(java.util.List.of(remaining));

        fixture.service.deleteVirtual(24L);

        verify(fixture.data).delete(91L);
        verify(fixture.instances).deleteById(24L);
    }

    @Test
    void retirementRejectsRunningCommand() {
        Fixture fixture = new Fixture();
        DeviceInstances active = instance(7L, DeviceInstanceLifecycle.IN_USE);
        DeviceTwinStates twin = new DeviceTwinStates();
        twin.setCurrentCmdState("RUNNING");
        when(fixture.instances.selectById(7L)).thenReturn(active);
        when(fixture.twins.selectOne(any())).thenReturn(twin);
        assertThrows(IllegalStateException.class, () -> fixture.service.retire("7"));
        verify(fixture.instances, never()).updateById(any(DeviceInstances.class));
    }

    @Test
    void requireOnlineRejectsWhenOfflineOrExpired() {
        Fixture fixture = new Fixture();
        DeviceTwinStates offlineTwin = new DeviceTwinStates();
        offlineTwin.setOnlineStatus("OFFLINE");
        when(fixture.twins.selectOne(any())).thenReturn(offlineTwin);
        assertThrows(IllegalStateException.class, () -> fixture.service.requireOnline(7L));

        DeviceTwinStates expiredTwin = new DeviceTwinStates();
        expiredTwin.setOnlineStatus("ONLINE");
        expiredTwin.setLastOnlineTime(java.time.OffsetDateTime.now().minusSeconds(35));
        when(fixture.twins.selectOne(any())).thenReturn(expiredTwin);
        assertThrows(IllegalStateException.class, () -> fixture.service.requireOnline(7L));
    }

    @Test
    void requireOnlinePassesWhenFreshAndOnline() {
        Fixture fixture = new Fixture();
        DeviceTwinStates freshTwin = new DeviceTwinStates();
        freshTwin.setOnlineStatus("ONLINE");
        freshTwin.setLastOnlineTime(java.time.OffsetDateTime.now().minusSeconds(5));
        when(fixture.twins.selectOne(any())).thenReturn(freshTwin);
        fixture.service.requireOnline(7L);
    }

    @Test
    void deleteRejectsActiveInstance() {
        Fixture fixture = new Fixture();
        DeviceInstances inUse = instance(7L, DeviceInstanceLifecycle.IN_USE);
        when(fixture.instances.selectById(7L)).thenReturn(inUse);
        assertThrows(IllegalStateException.class, () -> fixture.service.delete("7"));
        verify(fixture.instances, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    void deleteRejectsWhenExecutionLogsExist() {
        Fixture fixture = new Fixture();
        DeviceInstances retired = instance(7L, DeviceInstanceLifecycle.RETIRED);
        when(fixture.instances.selectById(7L)).thenReturn(retired);
        fixture.service.executionLogMapper = mock(com.smartlab.management.mapper.workflow.ExecutionLogMapper.class);
        when(fixture.service.executionLogMapper.selectCount(any())).thenReturn(3L);

        assertThrows(IllegalStateException.class, () -> fixture.service.delete("7"));
        verify(fixture.instances, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    void deleteCascadesWhenCleanAndRetired() {
        Fixture fixture = new Fixture();
        DeviceInstances retired = instance(7L, DeviceInstanceLifecycle.RETIRED);
        when(fixture.instances.selectById(7L)).thenReturn(retired);
        fixture.service.executionLogMapper = mock(com.smartlab.management.mapper.workflow.ExecutionLogMapper.class);
        when(fixture.service.executionLogMapper.selectCount(any())).thenReturn(0L);

        fixture.service.delete("7");

        verify(fixture.instances).deleteById(7L);
        verify(fixture.twins).delete(any());
        verify(fixture.routes).refreshAdapterRouteTable();
        verify(fixture.events).publishEvent(new DeviceInstanceDeletedEvent(7L));
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
        final DeviceModelService modelService = mock(DeviceModelService.class);
        final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        final DeviceInstanceService service = new DeviceInstanceService(
                instances, twins, data, routes, models, components, modelService, events);
    }
}
