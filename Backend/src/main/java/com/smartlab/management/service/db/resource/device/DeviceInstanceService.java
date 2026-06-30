package com.smartlab.management.service.db.resource.device;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceTwinStatesMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import com.smartlab.management.service.db.resource.data.DataIndexService;
import com.smartlab.adapter.AdapterPayloadMapperService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设备实例表服务，只负责实例持久化和实例快照基础读写。
 */
@Service
/**
 * 设备物理实例生命周期与点位绑定持久层基础服务。
 */
public class DeviceInstanceService extends ManagementCrudService<DeviceInstances> {

    private final DeviceInstancesMapper mapper;
    private final DeviceTwinStatesMapper twinStatesMapper;
    private final DataIndexService dataIndexService;
    private final AdapterPayloadMapperService protocolMapperService;

    public DeviceInstanceService(DeviceInstancesMapper mapper,
                                 DeviceTwinStatesMapper twinStatesMapper,
                                 DataIndexService dataIndexService,
                                 AdapterPayloadMapperService protocolMapperService) {
        super(mapper);
        this.mapper = mapper;
        this.twinStatesMapper = twinStatesMapper;
        this.dataIndexService = dataIndexService;
        this.protocolMapperService = protocolMapperService;
    }

    @Override
    public List<DeviceInstances> list() {
        return mapper.selectList(Wrappers.<DeviceInstances>lambdaQuery().orderByDesc(DeviceInstances::getId));
    }

    public PageResult<DeviceInstances> page(long pageNo, long pageSize, String modelId, String keyword, Boolean online) {
        LambdaQueryWrapper<DeviceInstances> query = Wrappers.lambdaQuery();
        Long parsedModelId = parseId(modelId);
        if (parsedModelId != null) {
            query.eq(DeviceInstances::getDeviceModelId, parsedModelId);
        }
        if (keyword != null && !keyword.isBlank()) {
            query.like(DeviceInstances::getInstanceName, keyword.trim());
        }
        query.orderByDesc(DeviceInstances::getId);
        Page<DeviceInstances> page = mapper.selectPage(new Page<>(Math.max(1, pageNo), Math.max(1, pageSize)), query);
        List<DeviceInstances> records = page.getRecords();
        if (online != null) {
            records = records.stream().filter(instance -> {
                DeviceTwinStates state = getSnapshot(instance.getId());
                boolean isOnline = state != null && "ONLINE".equalsIgnoreCase(state.getOnlineStatus());
                return online.equals(isOnline);
            }).toList();
        }
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    public Map<String, Long> summary(String modelId) {
        LambdaQueryWrapper<DeviceInstances> query = Wrappers.lambdaQuery();
        Long parsedModelId = parseId(modelId);
        if (parsedModelId != null) {
            query.eq(DeviceInstances::getDeviceModelId, parsedModelId);
        }
        long total = mapper.selectCount(query);
        Set<Long> allowedInstanceIds = parsedModelId == null ? null : loadInstanceIds(parsedModelId);
        long online = twinStatesMapper.selectList(Wrappers.<DeviceTwinStates>lambdaQuery())
                .stream()
                .filter(state -> "ONLINE".equalsIgnoreCase(state.getOnlineStatus()))
                .filter(state -> allowedInstanceIds == null || allowedInstanceIds.contains(state.getInstanceId()))
                .count();
        Map<String, Long> result = new HashMap<>();
        result.put("total", total);
        result.put("online", online);
        result.put("offline", Math.max(0, total - online));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceInstances savePayload(Map<String, Object> payload) {
        DeviceInstances instance = new DeviceInstances();
        Object id = first(payload, "id", "instanceId");
        if (id != null && !String.valueOf(id).isBlank()) {
            instance.setId(Long.valueOf(String.valueOf(id)));
        }
        Object modelId = first(payload, "deviceModelId", "modelId");
        if (modelId != null && !String.valueOf(modelId).isBlank()) {
            instance.setDeviceModelId(Long.valueOf(String.valueOf(modelId)));
        }
        instance.setInstanceName(stringValue(first(payload, "instanceName", "name")));
        instance.setBoundAdapterName(stringValue(first(payload, "boundAdapterName", "adapterName")));
        instance.setBoundDevicePoint(stringValue(first(payload, "boundDevicePoint", "devicePoint")));
        instance.setPicture(stringValue(payload.get("picture")));

        ObjectNode configNode = toObjectNode(first(payload, "instanceConfig", "commConfig"));
        if (instance.getBoundAdapterName() == null || instance.getBoundAdapterName().isBlank()) {
            instance.setBoundAdapterName(text(configNode, "boundAdapterName", text(configNode, "adapterName", null)));
        }
        if (instance.getBoundDevicePoint() == null || instance.getBoundDevicePoint().isBlank()) {
            instance.setBoundDevicePoint(text(configNode, "boundDevicePoint", text(configNode, "devicePoint", null)));
        }
        if (payload.containsKey("localConstraints")) {
            configNode.set("constraints", JsonNodeSupport.toNode(payload.get("localConstraints")));
        }
        if (hasAdapterBinding(instance)) {
            configNode.put("boundAdapterName", instance.getBoundAdapterName());
            configNode.put("boundDevicePoint", instance.getBoundDevicePoint());
            configNode.set("adapterBinding", protocolMapperService.buildAdapterBinding(
                    instance.getDeviceModelId(),
                    instance.getBoundAdapterName(),
                    instance.getBoundDevicePoint()
            ));
        }
        instance.setInstanceConfig(configNode);

        if (instance.getId() == null) {
            instance.setCreateTime(OffsetDateTime.now());
            mapper.insert(instance);
            createDefaultTwinState(instance.getId());
            dataIndexService.createDefaultDataSetsForDeviceInstance(
                    instance.getDeviceModelId(),
                    instance.getId(),
                    instance.getInstanceName()
            );
        } else {
            mapper.updateById(instance);
        }
        protocolMapperService.refreshAdapterRouteTable();
        return instance;
    }

    public void delete(String id) {
        Long instanceId = parseId(id);
        long dataSetCount = dataIndexService.countByDeviceInstance(instanceId);
        if (dataSetCount > 0) {
            throw new IllegalStateException("该设备实例已绑定 " + dataSetCount + " 个数据集，不能硬删除。请保留历史数据链路，后续可改为停用/归档");
        }
        mapper.deleteById(instanceId);
        twinStatesMapper.delete(Wrappers.<DeviceTwinStates>lambdaQuery().eq(DeviceTwinStates::getInstanceId, instanceId));
        protocolMapperService.refreshAdapterRouteTable();
    }

    public List<DeviceTwinStates> listSnapshots() {
        return twinStatesMapper.selectList(Wrappers.<DeviceTwinStates>lambdaQuery().orderByDesc(DeviceTwinStates::getUpdateTime));
    }

    public DeviceTwinStates getSnapshot(String id) {
        return getSnapshot(parseId(id));
    }

    public DeviceTwinStates getSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        return twinStatesMapper.selectOne(Wrappers.<DeviceTwinStates>lambdaQuery().eq(DeviceTwinStates::getInstanceId, id));
    }

    private void createDefaultTwinState(Long instanceId) {
        DeviceTwinStates state = new DeviceTwinStates();
        state.setInstanceId(instanceId);
        state.setCurrentOpState("IDLE");
        state.setCurrentCmdState("IDLE");
        state.setOnlineStatus("UNKNOWN");
        state.setCurrentAttr(JsonNodeSupport.objectNode());
        state.setUpdateTime(OffsetDateTime.now());
        twinStatesMapper.insert(state);
    }

    private Set<Long> loadInstanceIds(Long modelId) {
        Set<Long> result = new HashSet<>();
        mapper.selectList(Wrappers.<DeviceInstances>lambdaQuery()
                        .select(DeviceInstances::getId)
                        .eq(DeviceInstances::getDeviceModelId, modelId))
                .forEach(instance -> result.add(instance.getId()));
        return result;
    }

    private boolean hasAdapterBinding(DeviceInstances instance) {
        return instance != null
                && instance.getBoundAdapterName() != null && !instance.getBoundAdapterName().isBlank()
                && instance.getBoundDevicePoint() != null && !instance.getBoundDevicePoint().isBlank();
    }

    private ObjectNode toObjectNode(Object value) {
        JsonNode node = JsonNodeSupport.toNode(value);
        if (node != null && node.isObject()) {
            return (ObjectNode) node;
        }
        return JsonNodeSupport.objectNode();
    }

    private Object first(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            if (payload.containsKey(key)) {
                return payload.get(key);
            }
        }
        return null;
    }

    private String text(JsonNode node, String key, String fallback) {
        if (node == null || !node.hasNonNull(key)) {
            return fallback;
        }
        return node.path(key).asText(fallback);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
