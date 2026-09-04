package com.smartlab.management.service.db.resource.adapter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.adapter.MqttAdapterMessagingService;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.adapter.VirtualLease;
import com.smartlab.management.entity.resource.adapter.VirtualLeaseStatus;
import com.smartlab.management.entity.resource.device.DeviceInstanceKind;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.data.DataIndex;
import com.smartlab.management.mapper.resource.adapter.VirtualLeaseMapper;
import com.smartlab.management.service.db.resource.data.DataIndexService;
import com.smartlab.management.service.db.resource.device.DeviceInstanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VirtualLeaseServiceTest {

    private VirtualLeaseMapper mapper;
    private DeviceInstanceService instances;
    private DataIndexService dataIndexes;
    private MqttAdapterMessagingService mqtt;
    private VirtualLeaseService service;

    @BeforeEach
    void setUp() {
        mapper = mock(VirtualLeaseMapper.class);
        instances = mock(DeviceInstanceService.class);
        dataIndexes = mock(DataIndexService.class);
        mqtt = mock(MqttAdapterMessagingService.class);
        service = new VirtualLeaseService(mapper, instances);
        service.setMqttAdapterMessagingService(mqtt);
        service.setDataIndexService(dataIndexes);
    }

    @AfterEach
    void tearDownTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void saveRequiresPhysicalInstanceAndAdapter() {
        VirtualLease lease = new VirtualLease();
        assertThrows(IllegalArgumentException.class, () -> service.save(lease));
        lease.setPhysicalInstanceId(3L);
        assertThrows(IllegalArgumentException.class, () -> service.save(lease));
    }

    @Test
    void beginLeasePublishesPhysicalPoint() {
        when(instances.requireUsable(3L)).thenReturn(physical());
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.insert(any(VirtualLease.class))).thenAnswer(invocation -> {
            VirtualLease lease = invocation.getArgument(0);
            lease.setId(11L);
            return 1;
        });

        VirtualLease created = service.beginLease(3L, 9L);

        assertEquals(11L, created.getId());
        assertEquals(VirtualLeaseStatus.LEASING, created.getStatus());
        verify(mqtt).publishLeaseRequest(any());
    }

    @Test
    void grantedResultDefersWaiterUntilTransactionCommits() throws Exception {
        VirtualLease leasing = stored(11L, VirtualLeaseStatus.LEASING);
        AtomicBoolean committed = new AtomicBoolean(false);
        AtomicBoolean duringGrant = new AtomicBoolean(false);
        AtomicInteger grantSelectCount = new AtomicInteger();
        when(mapper.selectById(11L)).thenAnswer(invocation -> {
            if (duringGrant.get()) {
                if (grantSelectCount.incrementAndGet() == 2) {
                    return activeLease();
                }
                return leasing;
            }
            if (committed.get()) {
                return activeLease();
            }
            return leasing;
        });
        DeviceInstances physical = physical();
        when(instances.requireUsable(3L)).thenReturn(physical);
        DeviceInstances created = virtualInstance(24L);
        when(instances.findVirtual("plc", "Reactor1_sim_11")).thenAnswer(invocation -> committed.get() ? created : null);
        when(instances.createVirtual(physical, "Reactor1_sim_11")).thenReturn(created);
        when(dataIndexes.listByDeviceInstance(24L)).thenReturn(List.of(dataIndex(91L)));

        CompletableFuture<VirtualLease> outcome = CompletableFuture.supplyAsync(
                () -> service.awaitOutcome(11L, Duration.ofSeconds(2)));
        Thread.sleep(50);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            duringGrant.set(true);
            grantSelectCount.set(0);
            VirtualLease updated = service.applyLeaseResult(granted());
            duringGrant.set(false);
            assertEquals(VirtualLeaseStatus.ACTIVE, updated.getStatus());
            assertFalse(outcome.isDone(), "waiter must not complete before commit");

            IllegalStateException beforeCommit = assertThrows(IllegalStateException.class,
                    () -> requireVirtualInstanceLikeProduction(updated));
            assertTrue(beforeCommit.getMessage().contains("租约已生效但虚拟实例不存在"));

            committed.set(true);
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            VirtualLease awaited = outcome.get(1, TimeUnit.SECONDS);
            assertEquals(VirtualLeaseStatus.ACTIVE, awaited.getStatus());
            assertNotNull(requireVirtualInstanceLikeProduction(awaited));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void grantedResultCreatesVirtualBeforeActivatingLease() {
        VirtualLease lease = stored(11L, VirtualLeaseStatus.LEASING);
        when(mapper.selectById(11L)).thenReturn(lease);
        DeviceInstances physical = physical();
        when(instances.requireUsable(3L)).thenReturn(physical);
        when(instances.findVirtual("plc", "Reactor1_sim_11")).thenReturn(null);
        DeviceInstances created = virtualInstance(24L);
        when(instances.createVirtual(physical, "Reactor1_sim_11")).thenReturn(created);
        when(dataIndexes.listByDeviceInstance(24L)).thenReturn(List.of(dataIndex(91L)));

        service.applyLeaseResult(granted());

        var order = inOrder(instances, mapper);
        order.verify(instances).createVirtual(physical, "Reactor1_sim_11");
        order.verify(mapper).updateById(any(VirtualLease.class));
    }

    @Test
    void grantedResultCreatesVirtualAndActivates() {
        VirtualLease lease = stored(11L, VirtualLeaseStatus.LEASING);
        when(mapper.selectById(11L)).thenReturn(lease);
        DeviceInstances physical = physical();
        when(instances.requireUsable(3L)).thenReturn(physical);
        when(instances.findVirtual("plc", "Reactor1_sim_11")).thenReturn(null);
        DeviceInstances created = virtualInstance(24L);
        when(instances.createVirtual(physical, "Reactor1_sim_11")).thenReturn(created);
        when(dataIndexes.listByDeviceInstance(24L)).thenReturn(List.of(dataIndex(91L)));

        VirtualLease updated = service.applyLeaseResult(granted());

        assertEquals("Reactor1_sim_11", updated.getVirtualDevicePoint());
        assertEquals(VirtualLeaseStatus.ACTIVE, updated.getStatus());
        assertEquals(91L, lease.getDataIndexId());
        assertNotNull(updated.getLastUsedTime());
        verify(instances).createVirtual(physical, "Reactor1_sim_11");
        verify(mapper).updateById(any(VirtualLease.class));
    }

    @Test
    void grantedVirtualPointWithMqttWildcardIsFailed() {
        VirtualLease lease = stored(11L, VirtualLeaseStatus.LEASING);
        when(mapper.selectById(11L)).thenReturn(lease);

        ObjectNode result = granted();
        result.put("devicePoint", "Reactor1#sim-11");
        VirtualLease updated = service.applyLeaseResult(result);

        assertEquals(VirtualLeaseStatus.FAILED, updated.getStatus());
        verify(instances, never()).createVirtual(any(), any());
    }

    @Test
    void repeatedGrantedDoesNotCreateSecondVirtual() {
        VirtualLease lease = stored(11L, VirtualLeaseStatus.ACTIVE);
        lease.setVirtualDevicePoint("Reactor1_sim_11");
        when(mapper.selectById(11L)).thenReturn(lease);
        when(instances.requireUsable(3L)).thenReturn(physical());
        DeviceInstances existing = virtualInstance(24L);
        when(instances.findVirtual("plc", "Reactor1_sim_11")).thenReturn(existing);
        when(dataIndexes.listByDeviceInstance(24L)).thenReturn(List.of(dataIndex(91L)));

        service.applyLeaseResult(granted());

        verify(instances, never()).createVirtual(any(), any());
        assertEquals(VirtualLeaseStatus.ACTIVE, lease.getStatus());
        assertEquals(91L, lease.getDataIndexId());
    }

    @Test
    void releasedPokeDeletesVirtualWithoutDetachingDatasets() {
        VirtualLease lease = stored(11L, VirtualLeaseStatus.RELEASING);
        lease.setVirtualDevicePoint("Reactor1_sim_11");
        when(mapper.selectById(11L)).thenReturn(lease);
        when(instances.findVirtual("plc", "Reactor1_sim_11")).thenReturn(virtualInstance(24L));

        VirtualLease updated = service.applyLeaseResult(released());

        assertEquals(VirtualLeaseStatus.RELEASED, updated.getStatus());
        verify(dataIndexes, never()).detachFromInstance(any());
        verify(instances).deleteVirtual(24L);
        verify(mapper).updateById(any(VirtualLease.class));
    }

    @Test
    void releasedTaskDetachesDatasetsThenDeletesVirtual() {
        VirtualLease lease = stored(11L, VirtualLeaseStatus.RELEASING);
        lease.setTaskId(9L);
        lease.setVirtualDevicePoint("Reactor1_sim_11");
        when(mapper.selectById(11L)).thenReturn(lease);
        when(instances.findVirtual("plc", "Reactor1_sim_11")).thenReturn(virtualInstance(24L));
        when(dataIndexes.listByDeviceInstance(24L)).thenReturn(List.of(dataIndex(91L)));

        VirtualLease updated = service.applyLeaseResult(released());

        assertEquals(VirtualLeaseStatus.RELEASED, updated.getStatus());
        assertEquals(91L, lease.getDataIndexId());
        var order = inOrder(dataIndexes, instances);
        order.verify(dataIndexes).detachFromInstance(24L);
        order.verify(instances).deleteVirtual(24L);
        verify(dataIndexes, never()).delete(any());
    }

    @Test
    void resolveArchivePrefersActiveLeasePointer() {
        VirtualLease latestReleased = stored(12L, VirtualLeaseStatus.RELEASED);
        latestReleased.setTaskId(9L);
        latestReleased.setDataIndexId(92L);
        VirtualLease active = stored(11L, VirtualLeaseStatus.ACTIVE);
        active.setTaskId(9L);
        active.setDataIndexId(91L);
        when(mapper.selectList(any())).thenReturn(List.of(latestReleased, active));

        assertEquals(91L, service.resolveArchiveDataIndexId(3L, 9L));
    }

    @Test
    void resolveArchiveFallsBackToLatestReleasedPointer() {
        VirtualLease latest = stored(12L, VirtualLeaseStatus.RELEASED);
        latest.setTaskId(9L);
        latest.setDataIndexId(92L);
        VirtualLease older = stored(11L, VirtualLeaseStatus.FAILED);
        older.setTaskId(9L);
        older.setDataIndexId(90L);
        when(mapper.selectList(any())).thenReturn(List.of(latest, older));

        assertEquals(92L, service.resolveArchiveDataIndexId(3L, 9L));
    }

    @Test
    void beginReleaseWithoutGrantFailsLocally() {
        VirtualLease lease = stored(11L, VirtualLeaseStatus.LEASING);
        when(mapper.selectById(11L)).thenReturn(lease);

        VirtualLease updated = service.beginRelease(11L);

        assertEquals(VirtualLeaseStatus.FAILED, updated.getStatus());
        verify(mqtt, never()).publishLeaseRequest(any());
    }

    @Test
    void beginReleasePublishesVirtualPoint() {
        VirtualLease lease = stored(11L, VirtualLeaseStatus.ACTIVE);
        lease.setVirtualDevicePoint("Reactor1_sim_11");
        when(mapper.selectById(11L)).thenReturn(lease);

        VirtualLease updated = service.beginRelease(11L);

        assertEquals(VirtualLeaseStatus.RELEASING, updated.getStatus());
        assertNotNull(updated.getLastUsedTime());
        verify(mqtt).publishLeaseRequest(any());
    }

    @Test
    void beginReleaseWhenAlreadyReleasingRepublishesWithoutRefreshingAnchor() {
        VirtualLease lease = stored(11L, VirtualLeaseStatus.RELEASING);
        lease.setVirtualDevicePoint("Reactor1_sim_11");
        OffsetDateTime anchor = OffsetDateTime.now().minusSeconds(10);
        lease.setLastUsedTime(anchor);
        when(mapper.selectById(11L)).thenReturn(lease);

        VirtualLease updated = service.beginRelease(11L);

        assertEquals(VirtualLeaseStatus.RELEASING, updated.getStatus());
        assertEquals(anchor, updated.getLastUsedTime());
        verify(mqtt).publishLeaseRequest(any());
        verify(mapper, never()).updateById(any(VirtualLease.class));
    }

    @Test
    void applyReleasedIsIdempotentWhenAlreadyReleased() {
        VirtualLease lease = stored(11L, VirtualLeaseStatus.RELEASED);
        lease.setVirtualDevicePoint("Reactor1_sim_11");
        when(mapper.selectById(11L)).thenReturn(lease);

        VirtualLease updated = service.applyLeaseResult(released());

        assertEquals(VirtualLeaseStatus.RELEASED, updated.getStatus());
        verify(instances, never()).deleteVirtual(any());
        verify(mapper, never()).updateById(any(VirtualLease.class));
    }

    @Test
    void refusesToDeleteActiveLease() {
        VirtualLease lease = stored(11L, VirtualLeaseStatus.ACTIVE);
        when(mapper.selectById(11L)).thenReturn(lease);

        assertThrows(IllegalStateException.class, () -> service.delete((Serializable) 11L));
        verify(mapper, never()).deleteById((Serializable) 11L);
    }

    @Test
    void awaitOutcomeReturnsWhenAlreadyActive() {
        VirtualLease lease = stored(11L, VirtualLeaseStatus.ACTIVE);
        when(mapper.selectById(11L)).thenReturn(lease);

        assertEquals(VirtualLeaseStatus.ACTIVE, service.awaitOutcome(11L, Duration.ofSeconds(1)).getStatus());
    }

    @Test
    void acquireForTaskWaitsUntilGranted() {
        when(instances.requireUsable(3L)).thenReturn(physical());
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.insert(any(VirtualLease.class))).thenAnswer(invocation -> {
            VirtualLease lease = invocation.getArgument(0);
            lease.setId(11L);
            return 1;
        });
        VirtualLease granted = stored(11L, VirtualLeaseStatus.ACTIVE);
        granted.setVirtualDevicePoint("Reactor1_sim_11");
        when(mapper.selectById(11L)).thenReturn(granted);

        service.acquireForTask(List.of(3L), 9L);

        verify(mqtt).publishLeaseRequest(any());
    }

    @Test
    void acquireForTaskLeasesEachPhysicalInstanceOnce() {
        when(instances.requireUsable(3L)).thenReturn(physical());
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.insert(any(VirtualLease.class))).thenAnswer(invocation -> {
            VirtualLease lease = invocation.getArgument(0);
            lease.setId(11L);
            return 1;
        });
        VirtualLease granted = stored(11L, VirtualLeaseStatus.ACTIVE);
        granted.setVirtualDevicePoint("Reactor1_sim_11");
        when(mapper.selectById(11L)).thenReturn(granted);

        service.acquireForTask(List.of(3L, 3L), 9L);

        verify(mapper, times(1)).insert(any(VirtualLease.class));
        verify(mqtt, times(1)).publishLeaseRequest(any());
    }

    @Test
    void acquireForTaskReleasesWhenGrantFails() {
        when(instances.requireUsable(3L)).thenReturn(physical());
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.insert(any(VirtualLease.class))).thenAnswer(invocation -> {
            VirtualLease lease = invocation.getArgument(0);
            lease.setId(11L);
            return 1;
        });
        VirtualLease failed = stored(11L, VirtualLeaseStatus.FAILED);
        when(mapper.selectById(11L)).thenReturn(failed);
        when(mapper.selectList(any())).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> service.acquireForTask(Set.of(3L), 9L));
    }

    @Test
    void beginLeaseAllowsPokeWhileTaskLeaseIsActive() {
        when(instances.requireUsable(3L)).thenReturn(physical());
        when(mapper.insert(any(VirtualLease.class))).thenAnswer(invocation -> {
            VirtualLease lease = invocation.getArgument(0);
            lease.setId(12L);
            return 1;
        });

        VirtualLease created = service.beginLease(3L, null);

        assertEquals(12L, created.getId());
        assertEquals(VirtualLeaseStatus.LEASING, created.getStatus());
        verify(mapper, never()).selectOne(any());
        verify(mqtt).publishLeaseRequest(any());
    }

    @Test
    void expireIdlePokeReleasesUnusedVirtuals() {
        VirtualLease stale = stored(11L, VirtualLeaseStatus.ACTIVE);
        stale.setTaskId(null);
        stale.setVirtualDevicePoint("Reactor1_sim_11");
        stale.setLastUsedTime(OffsetDateTime.now().minusHours(2));
        when(mapper.selectList(any())).thenReturn(List.of(stale));
        when(mapper.selectById(11L)).thenReturn(stale);

        service.expireStalePokeLeases();

        verify(mqtt).publishLeaseRequest(any());
    }

    @Test
    void expireIdlePokeSkipsRecentAndTaskLeases() {
        VirtualLease recent = stored(11L, VirtualLeaseStatus.ACTIVE);
        recent.setTaskId(null);
        recent.setLastUsedTime(OffsetDateTime.now());
        VirtualLease taskLease = stored(12L, VirtualLeaseStatus.ACTIVE);
        taskLease.setTaskId(9L);
        taskLease.setLastUsedTime(OffsetDateTime.now().minusHours(2));
        VirtualLease fallback = stored(13L, VirtualLeaseStatus.ACTIVE);
        fallback.setTaskId(null);
        fallback.setLastUsedTime(null);
        fallback.setCreateTime(OffsetDateTime.now().minusMinutes(10));
        when(mapper.selectList(any())).thenReturn(List.of(recent, taskLease, fallback));

        service.expireStalePokeLeases();

        verify(mqtt, never()).publishLeaseRequest(any());
    }

    @Test
    void expireIdlePokeUsesCreateTimeWhenLastUsedMissing() {
        VirtualLease stale = stored(11L, VirtualLeaseStatus.ACTIVE);
        stale.setTaskId(null);
        stale.setVirtualDevicePoint("Reactor1_sim_11");
        stale.setLastUsedTime(null);
        stale.setCreateTime(OffsetDateTime.now().minusHours(2));
        when(mapper.selectList(any())).thenReturn(List.of(stale));
        when(mapper.selectById(11L)).thenReturn(stale);

        service.expireStalePokeLeases();

        verify(mqtt).publishLeaseRequest(any());
    }

    @Test
    void releasePokeVirtualRejectsTaskLease() {
        VirtualLease lease = stored(11L, VirtualLeaseStatus.ACTIVE);
        lease.setTaskId(9L);
        when(mapper.selectById(11L)).thenReturn(lease);

        assertThrows(IllegalStateException.class, () -> service.releasePokeVirtual(11L));
        verify(mqtt, never()).publishLeaseRequest(any());
    }

    @Test
    void touchLastUsedUpdatesPokeLease() {
        DeviceInstances virtual = new DeviceInstances();
        virtual.setId(24L);
        virtual.setInstanceKind(DeviceInstanceKind.VIRTUAL);
        virtual.setBoundAdapterName("plc");
        virtual.setBoundDevicePoint("Reactor1_sim_11");
        when(instances.requireUsable(24L)).thenReturn(virtual);
        VirtualLease lease = stored(11L, VirtualLeaseStatus.ACTIVE);
        lease.setTaskId(null);
        when(mapper.selectOne(any())).thenReturn(lease);

        service.touchLastUsedForInstance(24L);

        assertNotNull(lease.getLastUsedTime());
        verify(mapper).updateById(lease);
    }

    @Test
    void applyPokeVirtualIncludesAdapterErrorMessage() {
        when(instances.requireUsable(3L)).thenReturn(physical());
        when(mapper.insert(any(VirtualLease.class))).thenAnswer(invocation -> {
            VirtualLease lease = invocation.getArgument(0);
            lease.setId(4L);
            return 1;
        });
        VirtualLease leasing = stored(4L, VirtualLeaseStatus.LEASING);
        VirtualLease failed = stored(4L, VirtualLeaseStatus.FAILED);
        when(mapper.selectById(4L)).thenReturn(leasing, failed, failed);

        ObjectNode result = JsonNodeSupport.objectNode();
        result.put("leaseId", 4);
        result.put("adapterName", "plc");
        result.put("action", "LEASE");
        result.put("status", "FAILED");
        result.put("errorMessage", "未知物理点: Reactor2");
        result.put("timestamp", 1L);
        service.applyLeaseResult(result);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.applyPokeVirtual(3L));
        assertTrue(error.getMessage().contains("未知物理点: Reactor2"));
    }

    @Test
    void applyPokeVirtualDoesNotReuseExistingPokeLease() {
        when(instances.requireUsable(3L)).thenReturn(physical());
        when(mapper.insert(any(VirtualLease.class))).thenAnswer(invocation -> {
            VirtualLease lease = invocation.getArgument(0);
            lease.setId(11L);
            return 1;
        });
        VirtualLease granted = stored(11L, VirtualLeaseStatus.ACTIVE);
        granted.setVirtualDevicePoint("Reactor1_sim_11");
        when(mapper.selectById(11L)).thenReturn(granted);
        DeviceInstances virtual = new DeviceInstances();
        virtual.setId(24L);
        virtual.setInstanceKind(DeviceInstanceKind.VIRTUAL);
        virtual.setInstanceName("VIRTUAL-reactor");
        when(instances.findVirtual("plc", "Reactor1_sim_11")).thenReturn(virtual);

        var view = service.applyPokeVirtual(3L);

        assertEquals(24L, view.virtualInstanceId());
        assertEquals(11L, view.leaseId());
        verify(mqtt).publishLeaseRequest(any());
    }

    @Test
    void requireActiveVirtualLooksUpVirtualInstance() {
        VirtualLease lease = stored(11L, VirtualLeaseStatus.ACTIVE);
        lease.setVirtualDevicePoint("Reactor1_sim_11");
        when(mapper.selectOne(any())).thenReturn(lease);
        DeviceInstances virtual = new DeviceInstances();
        virtual.setId(24L);
        virtual.setInstanceKind(DeviceInstanceKind.VIRTUAL);
        when(instances.findVirtual("plc", "Reactor1_sim_11")).thenReturn(virtual);

        assertEquals(24L, service.requireActiveVirtual(3L, 9L).getId());
    }

    @Test
    void listActiveVirtualInstanceIdsIncludesPokeAndTaskLeases() {
        VirtualLease poke = stored(11L, VirtualLeaseStatus.ACTIVE);
        poke.setTaskId(null);
        poke.setVirtualDevicePoint("Reactor1_sim_11");
        VirtualLease task = stored(12L, VirtualLeaseStatus.ACTIVE);
        task.setTaskId(9L);
        task.setVirtualDevicePoint("Reactor1_sim_12");
        when(mapper.selectList(any())).thenReturn(List.of(poke, task));
        DeviceInstances pokeVm = new DeviceInstances();
        pokeVm.setId(24L);
        DeviceInstances taskVm = new DeviceInstances();
        taskVm.setId(25L);
        when(instances.findVirtual("plc", "Reactor1_sim_11")).thenReturn(pokeVm);
        when(instances.findVirtual("plc", "Reactor1_sim_12")).thenReturn(taskVm);

        assertEquals(Set.of(24L, 25L), service.listActiveVirtualInstanceIds(3L));
    }

    private ObjectNode granted() {
        ObjectNode result = JsonNodeSupport.objectNode();
        result.put("leaseId", 11);
        result.put("adapterName", "plc");
        result.put("action", "LEASE");
        result.put("devicePoint", "Reactor1_sim_11");
        result.put("status", "GRANTED");
        result.put("timestamp", 1L);
        return result;
    }

    private ObjectNode released() {
        ObjectNode result = JsonNodeSupport.objectNode();
        result.put("leaseId", 11);
        result.put("adapterName", "plc");
        result.put("action", "RELEASE");
        result.put("devicePoint", "Reactor1_sim_11");
        result.put("status", "RELEASED");
        result.put("timestamp", 1L);
        return result;
    }

    private DeviceInstances physical() {
        DeviceInstances physical = new DeviceInstances();
        physical.setId(3L);
        physical.setDeviceModelId(7L);
        physical.setInstanceKind(DeviceInstanceKind.PHYSICAL);
        physical.setBoundAdapterName("plc");
        physical.setBoundDevicePoint("Reactor1");
        return physical;
    }

    private DeviceInstances virtualInstance(long id) {
        DeviceInstances virtual = new DeviceInstances();
        virtual.setId(id);
        virtual.setInstanceKind(DeviceInstanceKind.VIRTUAL);
        return virtual;
    }

    private DataIndex dataIndex(long id) {
        DataIndex index = new DataIndex();
        index.setId(id);
        return index;
    }

    private VirtualLease activeLease() {
        VirtualLease active = stored(11L, VirtualLeaseStatus.ACTIVE);
        active.setVirtualDevicePoint("Reactor1_sim_11");
        return active;
    }

    private VirtualLease stored(long id, String status) {
        VirtualLease lease = new VirtualLease();
        lease.setId(id);
        lease.setPhysicalInstanceId(3L);
        lease.setAdapterName("plc");
        lease.setStatus(status);
        return lease;
    }

    private DeviceInstances requireVirtualInstanceLikeProduction(VirtualLease lease) {
        String point = lease.getVirtualDevicePoint();
        if (point == null || point.isBlank()) {
            throw new IllegalStateException("租约已生效但没有虚拟点位: leaseId=" + lease.getId());
        }
        DeviceInstances virtual = instances.findVirtual(lease.getAdapterName(), point);
        if (virtual == null) {
            throw new IllegalStateException("租约已生效但虚拟实例不存在: " + lease.getAdapterName() + "/" + point);
        }
        return virtual;
    }
}
