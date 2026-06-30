package com.smartlab.management.service.db.resource.device;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.mapper.resource.device.DeviceTwinStatesMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * 设备实时状态表服务。
 *
 * 对应 DEVICE_TWIN_STATES 表，用于保存设备实例的最新运行快照。
 */
@Service
/**
 * 设备孪生影子运行态快照持久层服务。
 */
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
            entity.setUpdateTime(OffsetDateTime.now());
        }
        return super.save(entity);
    }
}



