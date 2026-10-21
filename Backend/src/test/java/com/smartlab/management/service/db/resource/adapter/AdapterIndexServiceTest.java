package com.smartlab.management.service.db.resource.adapter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.adapter.AdapterManifestService;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.mapper.resource.adapter.AdapterIndexMapper;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceTwinStatesMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdapterIndexServiceTest {

    private AdapterIndexMapper adapterMapper;
    private DeviceInstancesMapper instanceMapper;
    private AdapterIndexService service;

    @BeforeEach
    void setUp() {
        adapterMapper = mock(AdapterIndexMapper.class);
        instanceMapper = mock(DeviceInstancesMapper.class);
        service = new AdapterIndexService(adapterMapper, new AdapterManifestService(), instanceMapper);
    }

    @Test
    void refusesToDeleteAdapterStillBoundToDeviceInstances() {
        AdapterIndex adapter = new AdapterIndex();
        adapter.setId(7L);
        adapter.setAdapterName("plc");
        when(adapterMapper.selectById(7L)).thenReturn(adapter);
        when(instanceMapper.selectCount(any())).thenReturn(1L);

        assertThrows(IllegalStateException.class, () -> service.delete((Serializable) 7L));
        verify(adapterMapper, never()).deleteById((Serializable) 7L);
    }

    @Test
    void deletePublishesAdapterDeletedEventAfterRemovingRow() {
        AdapterIndex adapter = new AdapterIndex();
        adapter.setId(7L);
        adapter.setAdapterName("plc");
        when(adapterMapper.selectById(7L)).thenReturn(adapter);
        when(instanceMapper.selectCount(any())).thenReturn(0L);
        org.springframework.context.ApplicationEventPublisher events =
                mock(org.springframework.context.ApplicationEventPublisher.class);
        service.setEventPublisher(events);

        service.delete((Serializable) 7L);

        verify(adapterMapper).deleteById((Serializable) 7L);
        verify(events).publishEvent(new com.smartlab.global.event.AdapterDeletedEvent("plc"));
    }

    @Test
    void heartbeatDoesNotInsertUnregisteredAdapter() {
        when(adapterMapper.selectOne(any())).thenReturn(null);

        assertNull(service.heartbeat("new-adapter", aliveHeartbeat()));

        verify(adapterMapper, never()).insert(any(AdapterIndex.class));
        verify(adapterMapper, never()).updateById(any(AdapterIndex.class));
    }

    @Test
    void heartbeatDoesNotUpdateConfiglessStub() {
        AdapterIndex stub = new AdapterIndex();
        stub.setId(3L);
        stub.setAdapterName("new-adapter");
        stub.setStatus("ENABLED");
        when(adapterMapper.selectOne(any())).thenReturn(stub);

        assertNull(service.heartbeat("new-adapter", aliveHeartbeat()));

        verify(adapterMapper, never()).insert(any(AdapterIndex.class));
        verify(adapterMapper, never()).updateById(any(AdapterIndex.class));
    }

    @Test
    void heartbeatUpdatesCompletedAdapter() {
        AdapterIndex adapter = completedAdapter(8L, "plc");
        when(adapterMapper.selectOne(any())).thenReturn(adapter);

        AdapterIndex saved = service.heartbeat("plc", aliveHeartbeat());

        assertEquals("plc", saved.getAdapterName());
        verify(adapterMapper).updateById(adapter);
        verify(adapterMapper, never()).insert(any(AdapterIndex.class));
    }

    @Test
    void listHidesHeartbeatStubsWithoutConfig() {
        AdapterIndex stub = new AdapterIndex();
        stub.setId(1L);
        stub.setAdapterName("empty");
        AdapterIndex complete = completedAdapter(2L, "full");
        when(adapterMapper.selectList(any())).thenReturn(List.of(stub, complete));

        assertEquals(List.of(complete), service.list());
    }

    @Test
    void heartbeatStubIsNotACompletedRegistration() {
        AdapterIndex stub = new AdapterIndex();
        stub.setAdapterName("empty");
        assertFalse(service.hasCompletedRegistration(stub));
        assertFalse(service.hasCompletedRegistration((AdapterIndex) null));

        AdapterIndex withOriginal = new AdapterIndex();
        withOriginal.setOriginalConfig("[adapter]\nadapterName = plc\n");
        assertTrue(service.hasCompletedRegistration(withOriginal));

        AdapterIndex withManifest = new AdapterIndex();
        var manifest = JsonNodeSupport.objectNode();
        manifest.putArray("deviceCategories");
        withManifest.setParsedConfig(manifest);
        assertTrue(service.hasCompletedRegistration(withManifest));
        assertTrue(service.hasCompletedRegistration(completedAdapter(1L, "plc")));
    }

    @Test
    void heartbeatAppliesReportedPointsAndMarksMissingInstancesOffline() {
        DeviceTwinStatesMapper twinMapper = mock(DeviceTwinStatesMapper.class);
        service = new AdapterIndexService(adapterMapper, new AdapterManifestService(), instanceMapper, null, twinMapper);
        when(adapterMapper.selectOne(any())).thenReturn(completedAdapter(8L, "plc"));
        when(instanceMapper.selectList(any())).thenReturn(List.of(
                boundInstance(1L, "Valve00"),
                boundInstance(2L, "Valve01"),
                boundInstance(3L, "Valve02"),
                boundInstance(4L, "Valve00_sim_12")));
        ObjectNode payload = aliveHeartbeat();
        payload.withArray("points").addObject().put("devicePoint", "Valve00").put("online", true);
        payload.withArray("points").addObject().put("devicePoint", "Valve01").put("online", false);
        payload.withArray("points").addObject().put("devicePoint", "Valve00_sim_12").put("online", true);

        service.heartbeat("plc", payload);

        verify(twinMapper).updateOnlineStatus(eq(1L), eq("ONLINE"), any());
        verify(twinMapper).updateOnlineStatus(eq(2L), eq("OFFLINE"), any());
        verify(twinMapper).updateOnlineStatus(eq(3L), eq("OFFLINE"), any());
        verify(twinMapper).updateOnlineStatus(eq(4L), eq("ONLINE"), any());
    }

    @Test
    void duplicatePointRejectsHeartbeatBeforeSaving() {
        DeviceTwinStatesMapper twinMapper = mock(DeviceTwinStatesMapper.class);
        service = new AdapterIndexService(adapterMapper, new AdapterManifestService(), instanceMapper, null, twinMapper);
        when(adapterMapper.selectOne(any())).thenReturn(completedAdapter(8L, "plc"));
        ObjectNode payload = aliveHeartbeat();
        payload.withArray("points").addObject().put("devicePoint", "Valve00").put("online", true);
        payload.withArray("points").addObject().put("devicePoint", "Valve00").put("online", false);

        assertThrows(IllegalArgumentException.class, () -> service.heartbeat("plc", payload));

        verify(adapterMapper, never()).updateById(any(AdapterIndex.class));
        verify(twinMapper, never()).updateOnlineStatus(any(), any(), any());
    }

    private ObjectNode aliveHeartbeat() {
        ObjectNode payload = JsonNodeSupport.objectNode();
        payload.put("status", "ALIVE");
        payload.put("timestamp", 1);
        payload.putArray("points");
        return payload;
    }

    private DeviceInstances boundInstance(Long id, String devicePoint) {
        DeviceInstances instance = new DeviceInstances();
        instance.setId(id);
        instance.setBoundDevicePoint(devicePoint);
        instance.setLifecycleStatus("IN_USE");
        return instance;
    }

    private AdapterIndex completedAdapter(Long id, String name) {
        AdapterIndex adapter = new AdapterIndex();
        adapter.setId(id);
        adapter.setAdapterName(name);
        adapter.setStatus("ENABLED");
        adapter.setOriginalConfig("[adapter]\nadapterName = " + name + "\n");
        return adapter;
    }
}
