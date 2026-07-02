package com.smartlab.management.service.db.resource.device;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.management.entity.resource.device.DeviceComponents;
import com.smartlab.management.mapper.resource.device.DeviceComponentsMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

import java.util.List;

/**
 * 设备组件拓扑表服务。
 *
 * 对应 DEVICE_COMPONENTS 表，用于记录设备实例下的组件、零件和替换关系。
 */
@Service
/**
 * DeviceComponent业务持久层核心操作服务。
 */
public class DeviceComponentService extends ManagementCrudService<DeviceComponents> {

    private final DeviceComponentsMapper mapper;

    public DeviceComponentService(DeviceComponentsMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    @Override
    public DeviceComponents save(DeviceComponents entity) {
        if (entity.getComponentName() == null || entity.getComponentName().isBlank()) {
            throw new IllegalArgumentException("组件名称不能为空");
        }
        entity.setComponentName(entity.getComponentName().trim());
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus("使用中");
        }
        if (entity.getId() == null && entity.getCreateTime() == null) {
            entity.setCreateTime(OffsetDateTime.now());
        }
        return super.save(entity);
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



