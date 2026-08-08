package com.smartlab.management.service.db.resource.device;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.device.DeviceComponents;
import com.smartlab.management.entity.resource.device.DeviceInstanceLifecycle;
import com.smartlab.management.mapper.resource.device.DeviceComponentsMapper;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DEVICE_COMPONENTS 组件拓扑服务。
 * 负责设备实例下组件槽位的配置、废弃、更换和 predecessor_id 历史链追溯。
 */
@Service
public class DeviceComponentService extends ManagementCrudService<DeviceComponents> {

    private static final String STATUS_IN_USE = "IN_USE";
    private static final String STATUS_PENDING_REPLACEMENT = "PENDING_REPLACEMENT";
    private static final String STATUS_REPLACED = "已更换";

    private final DeviceComponentsMapper mapper;
    private final DeviceInstancesMapper deviceInstancesMapper;

    public DeviceComponentService(DeviceComponentsMapper mapper, DeviceInstancesMapper deviceInstancesMapper) {
        super(mapper);
        this.mapper = mapper;
        this.deviceInstancesMapper = deviceInstancesMapper;
    }

    @Override
    public DeviceComponents save(DeviceComponents entity) {
        if (entity.getParentInstanceId() != null) requireInstanceUsable(entity.getParentInstanceId(), "父设备实例已注销，组件拓扑只允许查看");
        if (entity.getSelfInstanceId() != null) requireInstanceUsable(entity.getSelfInstanceId(), "绑定的组件实例已注销");
        if (entity.getComponentName() == null || entity.getComponentName().isBlank()) {
            throw new IllegalArgumentException("组件名称不能为空");
        }
        entity.setComponentName(entity.getComponentName().trim());
        if (entity.getId() == null) {
            entity.setStatus(STATUS_IN_USE);
            entity.setRemark(normalizeRemark(entity.getRemark()));
        }
        if (entity.getInstallTime() == null) {
            entity.setInstallTime(OffsetDateTime.now());
        }
        if (entity.getId() == null && entity.getCreateTime() == null) {
            entity.setCreateTime(OffsetDateTime.now());
        }
        return super.save(entity);
    }

    public List<DeviceComponents> listByParentInstance(Long parentInstanceId) {
        return listByParentInstance(parentInstanceId, false);
    }

    public List<DeviceComponents> listByParentInstance(Long parentInstanceId, boolean includeHistory) {
        var query = Wrappers.<DeviceComponents>lambdaQuery();
        if (parentInstanceId != null) {
            query.eq(DeviceComponents::getParentInstanceId, parentInstanceId);
        }
        List<DeviceComponents> all = mapper.selectList(query.orderByAsc(DeviceComponents::getComponentName)
                .orderByAsc(DeviceComponents::getId));
        if (includeHistory) {
            return all;
        }
        Set<Long> predecessorIds = new HashSet<>();
        for (DeviceComponents component : all) {
            if (component.getPredecessorId() != null) {
                predecessorIds.add(component.getPredecessorId());
            }
        }
        return all.stream().filter(component -> !predecessorIds.contains(component.getId())).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceComponents configure(Long id, DeviceComponents payload) {
        DeviceComponents component = requireComponent(id);
        requireComponentMutable(component);
        requireStatus(component, STATUS_IN_USE, "仅使用中的组件可配置");
        if (payload != null) {
            if (payload.getSelfInstanceId() != null) requireInstanceUsable(payload.getSelfInstanceId(), "绑定的组件实例已注销");
            component.setSelfInstanceId(payload.getSelfInstanceId());
            if (payload.getSpecification() != null) {
                component.setSpecification(payload.getSpecification());
            }
        }
        mapper.updateById(component);
        return requireComponent(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceComponents markPendingReplacement(Long id, String remark) {
        DeviceComponents component = requireComponent(id);
        requireComponentMutable(component);
        requireStatus(component, STATUS_IN_USE, "仅使用中的组件可标记为待更换");
        String normalizedRemark = normalizeRemark(remark);
        if (normalizedRemark == null) {
            throw new IllegalArgumentException("标记待更换时必须填写备注");
        }
        component.setStatus(STATUS_PENDING_REPLACEMENT);
        component.setRemark(normalizedRemark);
        mapper.updateById(component);
        return requireComponent(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceComponents replace(Long id, DeviceComponents replacement) {
        DeviceComponents old = requireComponent(id);
        requireComponentMutable(old);
        if (!STATUS_IN_USE.equals(old.getStatus()) && !STATUS_PENDING_REPLACEMENT.equals(old.getStatus())) {
            throw new IllegalArgumentException("仅使用中或待更换的组件可执行更换");
        }
        old.setStatus(STATUS_REPLACED);
        mapper.updateById(old);

        DeviceComponents next = new DeviceComponents();
        next.setComponentName(resolveReplacementName(old, replacement));
        next.setCategoryId(old.getCategoryId());
        next.setParentInstanceId(old.getParentInstanceId());
        next.setSelfInstanceId(replacement == null ? null : replacement.getSelfInstanceId());
        if (next.getSelfInstanceId() != null) requireInstanceUsable(next.getSelfInstanceId(), "绑定的组件实例已注销");
        next.setSpecification(replacement == null || replacement.getSpecification() == null
                ? old.getSpecification() : replacement.getSpecification());
        if (next.getSpecification() == null) {
            next.setSpecification(JsonNodeSupport.objectNode());
        }
        next.setRemark(replacement == null ? null : normalizeRemark(replacement.getRemark()));
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

    @Override
    public void delete(Serializable id) {
        throw new IllegalStateException("组件记录不支持物理删除，请通过更换流程保留历史链");
    }

    private void requireComponentMutable(DeviceComponents component) {
        requireInstanceUsable(component.getParentInstanceId(), "父设备实例已注销，组件拓扑只允许查看");
    }

    private void requireInstanceUsable(Long instanceId, String message) {
        var instance = instanceId == null ? null : deviceInstancesMapper.selectById(instanceId);
        if (!DeviceInstanceLifecycle.isUsable(instance)) throw new IllegalStateException(message);
    }

    private void requireStatus(DeviceComponents component, String status, String message) {
        if (!status.equals(component.getStatus())) {
            throw new IllegalArgumentException(message);
        }
    }

    private String normalizeRemark(String remark) {
        return remark == null || remark.isBlank() ? null : remark.trim();
    }

    private String resolveReplacementName(DeviceComponents old, DeviceComponents replacement) {
        return replacement == null || replacement.getComponentName() == null || replacement.getComponentName().isBlank()
                ? old.getComponentName() : replacement.getComponentName().trim();
    }
}