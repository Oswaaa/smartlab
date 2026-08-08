package com.smartlab.management.service.db.resource.adapter;

import com.smartlab.adapter.AdapterManifestService;
import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import com.smartlab.management.mapper.resource.adapter.AdapterIndexMapper;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
}
