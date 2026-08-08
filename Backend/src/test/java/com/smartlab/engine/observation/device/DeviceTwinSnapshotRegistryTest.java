package com.smartlab.engine.observation.device;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.observation.ObservableSnapshotService;
import com.smartlab.engine.observation.ObservationHistoryStore;
import com.smartlab.engine.observation.ObservableKey;
import com.smartlab.engine.observation.ObservationStatus;
import com.smartlab.engine.observation.SnapshotOrigin;
import com.smartlab.engine.observation.statemachine.StateMachineObservationRegistry;
import com.smartlab.engine.observation.workflow.WorkflowRuntimeSnapshotRegistry;
import com.smartlab.global.contract.ObservableObjectType;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceTwinStatesMapper;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceTwinSnapshotRegistryTest {
    @Test
    void atomicallyMergesAttributesAndProtectsInternalSnapshot() {
        DeviceTwinStatesMapper twins = mock(DeviceTwinStatesMapper.class);
        when(twins.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        DeviceTwinSnapshotRegistry registry = new DeviceTwinSnapshotRegistry(twins,
                mock(DeviceInstancesMapper.class), mock(ApplicationEventPublisher.class));
        ObjectNode first = JsonNodeSupport.objectNode();
        first.put("temperature", 20);
        registry.updateAttributes(7L, 3L, first, Instant.now());
        ObjectNode second = JsonNodeSupport.objectNode();
        second.put("pressure", 2);
        registry.updateAttributes(7L, 3L, second, Instant.now());

        DeviceTwinSnapshot snapshot = registry.snapshot(7L);
        assertEquals(20, snapshot.attributes().path("temperature").asInt());
        assertEquals(2, snapshot.attributes().path("pressure").asInt());
        assertEquals(2, snapshot.revision());
        ((ObjectNode) snapshot.attributes()).put("temperature", 999);
        assertEquals(20, registry.snapshot(7L).attributes().path("temperature").asInt());
    }

    @Test
    void restoredDatabaseValueIsStaleUntilLiveTelemetryArrives() {
        DeviceTwinStatesMapper twins = mock(DeviceTwinStatesMapper.class);
        when(twins.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        DeviceTwinSnapshotRegistry registry = new DeviceTwinSnapshotRegistry(twins,
                mock(DeviceInstancesMapper.class), mock(ApplicationEventPublisher.class));
        registry.restore(7L, 3L, JsonNodeSupport.objectNode().put("temperature", 20), "ONLINE", Instant.now());
        assertEquals(ObservationStatus.STALE, registry.snapshot(7L).status());
        assertEquals(SnapshotOrigin.RECOVERED, registry.snapshot(7L).origin());
    }

    @Test
    void retainsLiveTelemetryHistoryForTemporalConstraints() {
        DeviceTwinStatesMapper twins = mock(DeviceTwinStatesMapper.class);
        when(twins.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        ObservationHistoryStore history = new ObservationHistoryStore();
        DeviceTwinSnapshotRegistry registry = new DeviceTwinSnapshotRegistry(twins,
                mock(DeviceInstancesMapper.class), mock(ApplicationEventPublisher.class), history);
        ObservableSnapshotService snapshots = new ObservableSnapshotService(registry,
                mock(StateMachineObservationRegistry.class), mock(WorkflowRuntimeSnapshotRegistry.class), history);
        Instant now = Instant.now();
        registry.updateAttributes(7L, 3L, JsonNodeSupport.objectNode().put("temperature", 20), now.minusSeconds(2));
        registry.updateAttributes(7L, 3L, JsonNodeSupport.objectNode().put("temperature", 32), now);

        ObservableKey key = new ObservableKey(ObservableObjectType.DEVICE_ATTRIBUTE, 7L, null, null,
                null, null, "temperature", null);
        assertEquals(2, snapshots.readHistory(key, now.minusSeconds(5)).size());
        assertEquals(20, snapshots.readHistory(key, now.minusSeconds(5)).getFirst().value().asInt());
        assertEquals(32, snapshots.readHistory(key, now.minusSeconds(5)).getLast().value().asInt());
    }

    @Test
    void retirementRemovesLiveSnapshotAndAttributeHistory() {
        DeviceTwinStatesMapper twins = mock(DeviceTwinStatesMapper.class);
        when(twins.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        ObservationHistoryStore history = new ObservationHistoryStore();
        DeviceTwinSnapshotRegistry registry = new DeviceTwinSnapshotRegistry(twins,
                mock(DeviceInstancesMapper.class), mock(ApplicationEventPublisher.class), history);
        Instant now = Instant.now();
        registry.updateAttributes(7L, 3L, JsonNodeSupport.objectNode().put("temperature", 20), now);

        registry.remove(7L);

        ObservableKey key = DeviceTwinSnapshotRegistry.attributeKey(7L, "temperature");
        assertEquals(null, registry.snapshot(7L));
        assertEquals(List.of(), history.read(key, now.minusSeconds(5)));
    }

    @Test
    void startupDoesNotRestoreRetiredDeviceSnapshots() {
        DeviceTwinStatesMapper twins = mock(DeviceTwinStatesMapper.class);
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceTwinStates state = new DeviceTwinStates();
        state.setInstanceId(7L);
        state.setCurrentAttr(JsonNodeSupport.objectNode().put("temperature", 20));
        DeviceInstances retired = new DeviceInstances();
        retired.setId(7L);
        retired.setDeviceModelId(3L);
        retired.setLifecycleStatus("已注销");
        when(twins.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(state));
        when(instances.selectById(7L)).thenReturn(retired);
        DeviceTwinSnapshotRegistry registry = new DeviceTwinSnapshotRegistry(twins, instances,
                mock(ApplicationEventPublisher.class));

        registry.restorePersistedState();

        assertEquals(null, registry.snapshot(7L));
    }
}
