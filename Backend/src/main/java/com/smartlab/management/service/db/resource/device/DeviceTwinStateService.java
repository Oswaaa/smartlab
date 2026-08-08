package com.smartlab.management.service.db.resource.device;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.mapper.resource.device.DeviceTwinStatesMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Collection;

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

    public DeviceTwinStates patchRuntimeState(DeviceTwinStates state, boolean updateCommand,
                                              Collection<String> changedOperationRegions) {
        if (state == null || state.getInstanceId() == null) {
            throw new IllegalArgumentException("设备运行态补丁缺少instanceId");
        }
        ObjectNode operationPatch = JsonNodeSupport.objectNode();
        JsonNode currentOperationState = state.getCurrentOpState();
        if (changedOperationRegions != null) {
            for (String regionName : changedOperationRegions) {
                if (regionName == null || regionName.isBlank()) continue;
                JsonNode value = currentOperationState == null ? null : currentOperationState.get(regionName);
                if (value == null || !value.isArray()) {
                    throw new IllegalStateException("OP状态补丁缺少数组区域: " + regionName);
                }
                operationPatch.set(regionName, value.deepCopy());
            }
        }
        OffsetDateTime now = OffsetDateTime.now();
        int updated = mapper.patchRuntimeState(state.getInstanceId(), updateCommand,
                state.getCurrentCmdState(), operationPatch.toString(), now);
        if (updated != 1) {
            throw new IllegalStateException("设备运行态补丁更新失败: " + state.getInstanceId());
        }
        state.setUpdateTime(now);
        return state;
    }

    public boolean addExceptionState(Long instanceId, String regionName, String stateName) {
        return mapper.addExceptionState(instanceId, regionName, stateName, OffsetDateTime.now()) == 1;
    }

    public boolean removeExceptionState(Long instanceId, String regionName, String stateName) {
        return mapper.removeExceptionState(instanceId, regionName, stateName, OffsetDateTime.now()) == 1;
    }
}



