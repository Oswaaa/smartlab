package com.smartlab.management.service.db.resource.device;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.management.entity.DeviceComponents;
import com.smartlab.management.mapper.DeviceComponentsMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 设备组件拓扑表服务。
 *
 * 对应 DEVICE_COMPONENTS 表，用于记录设备实例下的组件、零件和替换关系。
 */
@Service
public class DeviceComponentService extends ManagementCrudService<DeviceComponents> {

    private final DeviceComponentsMapper mapper;

    public DeviceComponentService(DeviceComponentsMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    public List<DeviceComponents> listByParentInstance(Long parentInstanceId) {
        if (parentInstanceId == null) {
            return list();
        }
        return mapper.selectList(
                Wrappers.<DeviceComponents>lambdaQuery()
                        .eq(DeviceComponents::getParentInstanceId, parentInstanceId)
                        .orderByDesc(DeviceComponents::getId)
        );
    }
}



