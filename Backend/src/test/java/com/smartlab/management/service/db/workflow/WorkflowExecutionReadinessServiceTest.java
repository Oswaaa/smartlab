package com.smartlab.management.service.db.workflow;

import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.service.db.resource.adapter.AdapterIndexService;
import com.smartlab.management.service.db.resource.device.DeviceTwinStateService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowExecutionReadinessServiceTest {

    @Test
    void acceptsOnlineDeviceWithFreshAdapterHeartbeat() {
        Fixture fixture = fixture();
        DeviceTwinStates twin = new DeviceTwinStates();
        twin.setOnlineStatus("ONLINE");
        when(fixture.twins().getByInstanceId(7L)).thenReturn(twin);
        AdapterIndex adapter = adapter("ALIVE", OffsetDateTime.now());
        when(fixture.adapters().getByName("adapter-a")).thenReturn(adapter);

        assertDoesNotThrow(() -> fixture.service().validate(null));
    }

    @Test
    void rejectsOfflineDeviceBeforeTaskStarts() {
        Fixture fixture = fixture();
        DeviceTwinStates twin = new DeviceTwinStates();
        twin.setOnlineStatus("OFFLINE");
        when(fixture.twins().getByInstanceId(7L)).thenReturn(twin);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> fixture.service().validate(null));
        assertTrue(error.getMessage().contains("当前不在线"));
    }

    @Test
    void rejectsStaleAdapterHeartbeatBeforeTaskStarts() {
        Fixture fixture = fixture();
        DeviceTwinStates twin = new DeviceTwinStates();
        twin.setOnlineStatus("ONLINE");
        when(fixture.twins().getByInstanceId(7L)).thenReturn(twin);
        when(fixture.adapters().getByName("adapter-a"))
                .thenReturn(adapter("ALIVE", OffsetDateTime.now().minusMinutes(1)));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> fixture.service().validate(null));
        assertTrue(error.getMessage().contains("心跳已超时"));
    }

    @Test
    void inspectsOfflineDeviceWithoutThrowing() {
        Fixture fixture = fixture();
        DeviceTwinStates twin = new DeviceTwinStates();
        twin.setOnlineStatus("OFFLINE");
        when(fixture.twins().getByInstanceId(7L)).thenReturn(twin);

        java.util.List<WorkflowIssue> issues = fixture.service().inspect(null);

        assertEquals(java.util.List.of("ADAPTER_UNREGISTERED", "DEVICE_OFFLINE"),
                issues.stream().map(WorkflowIssue::code).sorted().toList());
        assertTrue(issues.get(0).blocking());
        assertEquals("deviceBindings[slot-7]", issues.get(0).path());
        assertEquals("slot-7", issues.get(0).elementId());
    }

    private Fixture fixture() {
        WorkflowTaskResourceService resources = mock(WorkflowTaskResourceService.class);
        DeviceTwinStateService twins = mock(DeviceTwinStateService.class);
        AdapterIndexService adapters = mock(AdapterIndexService.class);
        DeviceInstances instance = new DeviceInstances();
        instance.setId(7L);
        instance.setInstanceName("heater");
        instance.setBoundAdapterName("adapter-a");
        when(resources.boundDeviceBindings(null)).thenReturn(java.util.List.of(new WorkflowTaskResourceService.BoundDeviceBinding("slot-7", 7L)));
        when(resources.requireUsableInstance(7L)).thenReturn(instance);
        return new Fixture(new WorkflowExecutionReadinessService(resources, twins, adapters, 30), twins, adapters);
    }

    private AdapterIndex adapter(String status, OffsetDateTime lastHeartbeat) {
        AdapterIndex adapter = new AdapterIndex();
        adapter.setAdapterName("adapter-a");
        adapter.setStatus(status);
        adapter.setLastHeartbeat(lastHeartbeat);
        return adapter;
    }

    private record Fixture(WorkflowExecutionReadinessService service,
                           DeviceTwinStateService twins,
                           AdapterIndexService adapters) {
    }
}
