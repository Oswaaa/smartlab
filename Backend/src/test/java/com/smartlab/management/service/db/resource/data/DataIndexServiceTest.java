package com.smartlab.management.service.db.resource.data;

import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.mapper.resource.data.DataIndexMapper;
import com.smartlab.management.mapper.resource.data.DataTemplateDetailMapper;
import com.smartlab.management.mapper.resource.data.DataTemplateMainMapper;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.PropertyTypeMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataIndexServiceTest {
    @Test
    void retiredInstanceCannotReceiveNewDataSet() {
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceInstances retired = new DeviceInstances();
        retired.setId(9L);
        retired.setLifecycleStatus("已注销");
        when(instances.selectById(9L)).thenReturn(retired);
        DataIndexService service = new DataIndexService(
                mock(DataIndexMapper.class), mock(DataTemplateMainMapper.class),
                mock(DataTemplateDetailMapper.class), mock(PropertyTypeMapper.class),
                mock(JdbcTemplate.class), instances);

        assertThrows(IllegalStateException.class, () -> service.createDataSet(2L, 9L, "retired"));
    }
}
