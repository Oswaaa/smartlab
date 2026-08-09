package com.smartlab.management.service.db.resource.device;

import com.smartlab.management.entity.resource.device.DeviceComponents;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.mapper.resource.device.DeviceComponentsMapper;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceComponentServiceTest {
    @Test
    void retiredParentMakesComponentTopologyReadOnly() {
        DeviceComponentsMapper components = mock(DeviceComponentsMapper.class);
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceComponentService service = new DeviceComponentService(components, instances);
        DeviceComponents component = new DeviceComponents();
        component.setId(4L);
        component.setParentInstanceId(7L);
        component.setStatus("IN_USE");
        when(components.selectById(4L)).thenReturn(component);
        DeviceInstances parent = new DeviceInstances();
        parent.setId(7L);
        parent.setLifecycleStatus("RETIRED");
        when(instances.selectById(7L)).thenReturn(parent);

        assertThrows(IllegalStateException.class, () -> service.configure(4L, new DeviceComponents()));
        assertThrows(IllegalStateException.class, () -> service.markPendingReplacement(4L, "broken"));
        assertThrows(IllegalStateException.class, () -> service.replace(4L, new DeviceComponents()));
    }
}
