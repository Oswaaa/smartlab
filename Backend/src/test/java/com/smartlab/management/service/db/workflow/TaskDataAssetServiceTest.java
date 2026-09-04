package com.smartlab.management.service.db.workflow;

import com.smartlab.management.dto.workflow.TaskDataAssetsResponse;
import com.smartlab.management.entity.resource.data.DataIndex;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.service.db.resource.adapter.VirtualLeaseService;
import com.smartlab.management.service.db.resource.data.DataIndexService;
import com.smartlab.management.service.db.resource.device.DeviceInstanceService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskDataAssetServiceTest {

    @Test
    void productionUsesPhysicalInstanceDatasets() {
        Fixture fixture = new Fixture();
        Task task = task(6L, "PRODUCTION");
        when(fixture.tasks.selectById(6L)).thenReturn(task);
        when(fixture.resources.boundDeviceInstanceIds(task.getResourceMap()))
                .thenReturn(java.util.Set.of(3L));
        when(fixture.instances.getById(3L)).thenReturn(physical(3L, "反应器"));
        DataIndex production = dataIndex(11L);
        when(fixture.data.listByDeviceInstance(3L)).thenReturn(List.of(production));

        TaskDataAssetsResponse result = fixture.service.listDataAssets(6L);

        assertEquals("PRODUCTION", result.executionKind());
        assertEquals(3L, result.instances().get(0).physicalInstanceId());
        assertEquals("反应器", result.instances().get(0).instanceName());
        assertEquals(11L, result.instances().get(0).datasets().get(0).getId());
        verify(fixture.leases, never()).resolveArchiveDataIndexId(3L, 6L);
        verify(fixture.resources).boundDeviceInstanceIds(task.getResourceMap());
        verify(fixture.resources, never()).boundDeviceBindings(task.getResourceMap());
    }

    @Test
    void listsEachPhysicalInstanceOnce() {
        Fixture fixture = new Fixture();
        Task task = task(6L, "PRODUCTION");
        when(fixture.tasks.selectById(6L)).thenReturn(task);
        when(fixture.resources.boundDeviceInstanceIds(task.getResourceMap()))
                .thenReturn(java.util.Set.of(3L));
        when(fixture.instances.getById(3L)).thenReturn(physical(3L, "反应器"));
        when(fixture.data.listByDeviceInstance(3L)).thenReturn(List.of());

        TaskDataAssetsResponse result = fixture.service.listDataAssets(6L);

        assertEquals(1, result.instances().size());
        assertEquals(3L, result.instances().get(0).physicalInstanceId());
    }

    @Test
    void simulationUsesReleasedLeasePointer() {
        Fixture fixture = new Fixture();
        Task task = task(9L, "SIMULATION");
        when(fixture.tasks.selectById(9L)).thenReturn(task);
        when(fixture.resources.boundDeviceInstanceIds(task.getResourceMap()))
                .thenReturn(java.util.Set.of(3L));
        when(fixture.instances.getById(3L)).thenReturn(physical(3L, "反应器"));
        when(fixture.leases.resolveArchiveDataIndexId(3L, 9L)).thenReturn(91L);
        DataIndex sim = dataIndex(91L);
        when(fixture.data.getById(91L)).thenReturn(sim);

        TaskDataAssetsResponse result = fixture.service.listDataAssets(9L);

        assertEquals("SIMULATION", result.executionKind());
        assertEquals(91L, result.instances().get(0).datasets().get(0).getId());
        verify(fixture.data, never()).listByDeviceInstance(3L);
    }

    @Test
    void missingTaskFails() {
        Fixture fixture = new Fixture();
        when(fixture.tasks.selectById(9L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> fixture.service.listDataAssets(9L));
    }

    private static Task task(long id, String kind) {
        Task task = new Task();
        task.setId(id);
        task.setTaskName("任务" + id);
        task.setExecutionKind(kind);
        task.setResourceMap(com.smartlab.global.util.JsonNodeSupport.objectNode());
        return task;
    }

    private static DeviceInstances physical(long id, String name) {
        DeviceInstances instance = new DeviceInstances();
        instance.setId(id);
        instance.setInstanceName(name);
        return instance;
    }

    private static DataIndex dataIndex(long id) {
        DataIndex index = new DataIndex();
        index.setId(id);
        return index;
    }

    private static final class Fixture {
        final TaskMapper tasks = mock(TaskMapper.class);
        final WorkflowTaskResourceService resources = mock(WorkflowTaskResourceService.class);
        final DeviceInstanceService instances = mock(DeviceInstanceService.class);
        final DataIndexService data = mock(DataIndexService.class);
        final VirtualLeaseService leases = mock(VirtualLeaseService.class);
        final TaskDataAssetService service = new TaskDataAssetService(
                tasks, resources, instances, data, leases);
    }
}
