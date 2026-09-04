package com.smartlab.management.service.db.resource.data;

import com.smartlab.management.entity.resource.data.DataIndex;
import com.smartlab.management.entity.resource.data.DataTemplateMain;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.mapper.resource.adapter.VirtualLeaseMapper;
import com.smartlab.management.mapper.resource.data.DataIndexMapper;
import com.smartlab.management.mapper.resource.data.DataTemplateDetailMapper;
import com.smartlab.management.mapper.resource.data.DataTemplateMainMapper;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.PropertyTypeMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataIndexServiceTest {
    @Test
    void retiredInstanceCannotReceiveNewDataSet() {
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceInstances retired = new DeviceInstances();
        retired.setId(9L);
        retired.setLifecycleStatus("RETIRED");
        when(instances.selectByIdForUpdate(9L)).thenReturn(retired);
        DataIndexService service = new DataIndexService(
                mock(DataIndexMapper.class), mock(DataTemplateMainMapper.class),
                mock(DataTemplateDetailMapper.class), mock(PropertyTypeMapper.class),
                mock(JdbcTemplate.class), instances);

        assertThrows(IllegalStateException.class, () -> service.createDataSet(2L, 9L, "retired"));
    }

    @Test
    void dataSetMustBindDeviceInstance() {
        DataIndexService service = new DataIndexService(
                mock(DataIndexMapper.class), mock(DataTemplateMainMapper.class),
                mock(DataTemplateDetailMapper.class), mock(PropertyTypeMapper.class),
                mock(JdbcTemplate.class), mock(DeviceInstancesMapper.class));

        assertThrows(IllegalArgumentException.class, () -> service.createDataSet(2L, null, "missing instance"));
    }

    @Test
    void templateAndInstanceMustBelongToSameModel() {
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DataTemplateMainMapper templates = mock(DataTemplateMainMapper.class);
        DeviceInstances instance = new DeviceInstances();
        instance.setId(9L);
        instance.setDeviceModelId(11L);
        instance.setLifecycleStatus("IN_USE");
        DataTemplateMain template = new DataTemplateMain();
        template.setId(2L);
        template.setDeviceModelId(12L);
        when(instances.selectByIdForUpdate(9L)).thenReturn(instance);
        when(templates.selectByIdForUpdate(2L)).thenReturn(template);
        DataIndexService service = new DataIndexService(
                mock(DataIndexMapper.class), templates,
                mock(DataTemplateDetailMapper.class), mock(PropertyTypeMapper.class),
                mock(JdbcTemplate.class), instances);

        assertThrows(IllegalStateException.class, () -> service.createDataSet(2L, 9L, "mismatch"));
    }

    @Test
    void detachFromInstanceDoesNothingWhenIdMissing() {
        DataIndexMapper mapper = mock(DataIndexMapper.class);
        DataIndexService service = new DataIndexService(
                mapper, mock(DataTemplateMainMapper.class),
                mock(DataTemplateDetailMapper.class), mock(PropertyTypeMapper.class),
                mock(JdbcTemplate.class), mock(DeviceInstancesMapper.class));

        assertEquals(0, service.detachFromInstance(null));
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void detachFromInstanceClearsBindingWithoutDroppingTable() {
        DataIndexMapper mapper = mock(DataIndexMapper.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DataIndexService service = new DataIndexService(
                mapper, mock(DataTemplateMainMapper.class),
                mock(DataTemplateDetailMapper.class), mock(PropertyTypeMapper.class),
                jdbc, mock(DeviceInstancesMapper.class));
        when(mapper.update(any(), any())).thenReturn(1);

        assertEquals(1, service.detachFromInstance(24L));
        verify(mapper).update(any(), any());
        verify(jdbc, never()).execute(anyString());
    }

    @Test
    void deleteClearsLeasePointerThenDropsTable() {
        DataIndexMapper mapper = mock(DataIndexMapper.class);
        VirtualLeaseMapper leases = mock(VirtualLeaseMapper.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DataIndex index = new DataIndex();
        index.setId(91L);
        index.setDataTable("data_record_1");
        when(mapper.selectById(91L)).thenReturn(index);
        DataIndexService service = new DataIndexService(
                mapper, mock(DataTemplateMainMapper.class),
                mock(DataTemplateDetailMapper.class), mock(PropertyTypeMapper.class),
                jdbc, mock(DeviceInstancesMapper.class));
        service.setVirtualLeaseMapper(leases);

        service.delete(91L);

        verify(jdbc).execute(anyString());
        verify(leases).update(any(), any());
        verify(mapper).deleteById(91L);
    }
}
