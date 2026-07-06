package com.smartlab.management.service.db.resource.device;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.device.DeviceComponents;
import com.smartlab.management.mapper.resource.device.DeviceComponentsMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DEVICE_COMPONENTS 组件拓扑服务。
 * 负责设备实例下组件槽位的配置、废弃、更换和 predecessor_id 历史链追溯。
 */
@Service
public class DeviceComponentService extends ManagementCrudService<DeviceComponents> {

    private static final String STATUS_UNCONFIGURED = "未配置";
    private static final String STATUS_IN_USE = "使用中";
    private static final String STATUS_DISCARDED = "已废弃";
    private static final String STATUS_REPLACED = "已更换";

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
            entity.setStatus(entity.getSelfInstanceId() == null && isEmptySpec(entity) ? STATUS_UNCONFIGURED : STATUS_IN_USE);
        }
        if (STATUS_IN_USE.equals(entity.getStatus()) && entity.getInstallTime() == null) {
            entity.setInstallTime(OffsetDateTime.now());
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
                        .orderByAsc(DeviceComponents::getComponentName)
                        .orderByAsc(DeviceComponents::getId)
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceComponents configure(Long id, DeviceComponents payload) {
        DeviceComponents component = requireComponent(id);
        if (payload != null) {
            if (payload.getComponentName() != null && !payload.getComponentName().isBlank()) {
                component.setComponentName(payload.getComponentName().trim());
            }
            if (payload.getCategoryId() != null) {
                component.setCategoryId(payload.getCategoryId());
            }
            component.setSelfInstanceId(payload.getSelfInstanceId());
            if (payload.getSpecification() != null) {
                component.setSpecification(payload.getSpecification());
            }
        }
        component.setStatus(STATUS_IN_USE);
        component.setInstallTime(OffsetDateTime.now());
        mapper.updateById(component);
        return requireComponent(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceComponents discard(Long id) {
        DeviceComponents component = requireComponent(id);
        component.setStatus(STATUS_DISCARDED);
        mapper.updateById(component);
        return requireComponent(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceComponents replace(Long id, DeviceComponents replacement) {
        DeviceComponents old = requireComponent(id);
        old.setStatus(STATUS_REPLACED);
        mapper.updateById(old);

        DeviceComponents next = new DeviceComponents();
        next.setComponentName(resolveReplacementName(old, replacement));
        next.setCategoryId(old.getCategoryId());
        next.setParentInstanceId(old.getParentInstanceId());
        next.setSelfInstanceId(replacement == null ? null : replacement.getSelfInstanceId());
        next.setSpecification(replacement == null ? JsonNodeSupport.objectNode() : replacement.getSpecification());
        if (next.getSpecification() == null) {
            next.setSpecification(JsonNodeSupport.objectNode());
        }
        next.setStatus(STATUS_IN_USE);
        next.setPredecessorId(old.getId());
        next.setInstallTime(OffsetDateTime.now());
        next.setCreateTime(OffsetDateTime.now());
        mapper.insert(next);
        return next;
    }

    public List<DeviceComponents> history(Long id) {
        DeviceComponents current = requireComponent(id);
        List<DeviceComponents> ancestors = new ArrayList<>();
        DeviceComponents cursor = current;
        while (cursor != null) {
            ancestors.add(cursor);
            cursor = cursor.getPredecessorId() == null ? null : mapper.selectById(cursor.getPredecessorId());
        }
        Collections.reverse(ancestors);

        List<DeviceComponents> result = new ArrayList<>(ancestors);
        Long tailId = current.getId();
        while (tailId != null) {
            DeviceComponents successor = mapper.selectOne(
                    Wrappers.<DeviceComponents>lambdaQuery()
                            .eq(DeviceComponents::getPredecessorId, tailId)
                            .orderByAsc(DeviceComponents::getId)
                            .last("limit 1")
            );
            if (successor == null) {
                break;
            }
            result.add(successor);
            tailId = successor.getId();
        }
        return result;
    }

    private DeviceComponents requireComponent(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("组件ID不能为空");
        }
        DeviceComponents component = mapper.selectById(id);
        if (component == null) {
            throw new IllegalArgumentException("组件不存在: " + id);
        }
        return component;
    }

    private boolean isEmptySpec(DeviceComponents entity) {
        return entity.getSpecification() == null || entity.getSpecification().isNull()
                || (entity.getSpecification().isObject() && entity.getSpecification().isEmpty());
    }

    private String resolveReplacementName(DeviceComponents old, DeviceComponents replacement) {
        if (replacement != null && replacement.getComponentName() != null && !replacement.getComponentName().isBlank()) {
            return replacement.getComponentName().trim();
        }
        return old.getComponentName();
    }
}