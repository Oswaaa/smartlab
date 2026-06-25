package com.smartlab.management.service.db.resource.device;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.management.entity.DeviceTwinStates;
import com.smartlab.management.mapper.DeviceTwinStatesMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 设备实时状态表服务。
 *
 * 对应 DEVICE_TWIN_STATES 表，用于保存设备实例的最新运行快照。
 */
@Service
public class DeviceTwinStateService extends ManagementCrudService<DeviceTwinStates> {

    private final DeviceTwinStatesMapper mapper;

    public DeviceTwinStateService(DeviceTwinStatesMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    public DeviceTwinStates getByInstanceId(Long instanceId) {
        if (instanceId == null) {
            return null;
        }
        return mapper.selectOne(
                Wrappers.<DeviceTwinStates>lambdaQuery().eq(DeviceTwinStates::getInstanceId, instanceId)
        );
    }

    @Override
    public DeviceTwinStates save(DeviceTwinStates entity) {
        if (entity.getUpdateTime() == null) {
            entity.setUpdateTime(LocalDateTime.now());
        }
        return super.save(entity);
    }
}



