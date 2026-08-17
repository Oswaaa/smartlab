package com.smartlab.management.service.db.resource.device;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.dto.resource.device.DeviceInstanceDTO;
import com.smartlab.management.entity.resource.device.DeviceComponents;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceInstanceLifecycle;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.management.mapper.resource.device.DeviceTwinStatesMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import com.smartlab.management.service.db.resource.data.DataIndexService;
import com.smartlab.global.event.DeviceInstanceRetiredEvent;
import com.smartlab.global.event.DeviceInstanceSavedEvent;
import com.smartlab.management.service.protocol.AdapterPayloadMapperService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final DeviceModelsMapper deviceModelsMapper;
    private final DeviceComponentService deviceComponentService;
    private final DeviceModelService deviceModelService;
    private final ApplicationEventPublisher eventPublisher;

    public DeviceInstanceService(DeviceInstancesMapper mapper,
                                 DeviceTwinStatesMapper twinStatesMapper,
                                 DataIndexService dataIndexService,
                                 AdapterPayloadMapperService protocolMapperService,
                                 DeviceModelsMapper deviceModelsMapper,
                                 DeviceComponentService deviceComponentService,
                                 DeviceModelService deviceModelService,
                                 ApplicationEventPublisher eventPublisher) {
        super(mapper);
        this.mapper = mapper;
        this.twinStatesMapper = twinStatesMapper;
        this.dataIndexService = dataIndexService;
        this.protocolMapperService = protocolMapperService;
        this.deviceModelsMapper = deviceModelsMapper;
        this.deviceComponentService = deviceComponentService;
        this.deviceModelService = deviceModelService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<DeviceInstances> list() {
        return list(null);
    }

    public List<DeviceInstances> list(String lifecycleStatus) {
        LambdaQueryWrapper<DeviceInstances> query = Wrappers.lambdaQuery();
        applyLifecycleFilter(query, lifecycleStatus);
        return mapper.selectList(query.orderByDesc(DeviceInstances::getId));
    }

    public List<DeviceInstanceDTO> listDTO(String lifecycleStatus) {
        List<DeviceInstances> records = list(lifecycleStatus);
        return assembleDTOs(records);
    }

    public PageResult<DeviceInstances> page(long pageNo, long pageSize, String modelId, String keyword,
                                             Boolean online, String lifecycleStatus) {
        LambdaQueryWrapper<DeviceInstances> query = Wrappers.lambdaQuery();
        Long parsedModelId = parseId(modelId);
        if (parsedModelId != null) {
            query.eq(DeviceInstances::getDeviceModelId, parsedModelId);
        }
        applyLifecycleFilter(query, lifecycleStatus);
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

    public PageResult<DeviceInstanceDTO> pageDTO(long pageNo, long pageSize, String modelId, String keyword,
                                                 Boolean online, String lifecycleStatus) {
        LambdaQueryWrapper<DeviceInstances> query = Wrappers.lambdaQuery();
        Long parsedModelId = parseId(modelId);
        if (parsedModelId != null) {
            query.eq(DeviceInstances::getDeviceModelId, parsedModelId);
        }
        applyLifecycleFilter(query, lifecycleStatus);
        if (keyword != null && !keyword.isBlank()) {
            query.like(DeviceInstances::getInstanceName, keyword.trim());
        }
        query.orderByDesc(DeviceInstances::getId);
        Page<DeviceInstances> page = mapper.selectPage(new Page<>(Math.max(1, pageNo), Math.max(1, pageSize)), query);
        List<DeviceInstances> records = page.getRecords();
        List<DeviceInstanceDTO> dtos = assembleDTOs(records);
        if (online != null) {
            dtos = dtos.stream().filter(dto -> online.equals(dto.getIsOnline())).toList();
        }
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), dtos);
    }

    private List<DeviceInstanceDTO> assembleDTOs(List<DeviceInstances> records) {
        if (records == null || records.isEmpty()) return List.of();
        List<Long> instanceIds = records.stream().map(DeviceInstances::getId).filter(Objects::nonNull).toList();
        Map<Long, DeviceTwinStates> twinMap = twinStatesMapper.selectList(
                Wrappers.<DeviceTwinStates>lambdaQuery().in(DeviceTwinStates::getInstanceId, instanceIds)
        ).stream().collect(java.util.stream.Collectors.toMap(DeviceTwinStates::getInstanceId, s -> s, (a, b) -> a));

        return records.stream().map(inst -> {
            DeviceTwinStates twin = twinMap.get(inst.getId());
            DeviceInstanceDTO dto = new DeviceInstanceDTO();
            dto.setId(inst.getId());
            dto.setDeviceModelId(inst.getDeviceModelId());
            dto.setInstanceName(inst.getInstanceName());
            dto.setInstanceConfig(inst.getInstanceConfig());
            dto.setBoundAdapterName(inst.getBoundAdapterName());
            dto.setBoundDevicePoint(inst.getBoundDevicePoint());
            dto.setLifecycleStatus(inst.getLifecycleStatus());
            dto.setPicture(inst.getPicture());
            dto.setCreateTime(inst.getCreateTime());
            String onlineStatus = twin != null && twin.getOnlineStatus() != null ? twin.getOnlineStatus() : "OFFLINE";
            dto.setOnlineStatus(onlineStatus);
            dto.setIsOnline("ONLINE".equalsIgnoreCase(onlineStatus));
            dto.setCurrentCmdState(twin != null ? twin.getCurrentCmdState() : "IDLE");
            return dto;
        }).toList();
    }

    public Map<String, Long> summary(String modelId) {
        LambdaQueryWrapper<DeviceInstances> query = Wrappers.lambdaQuery();
        Long parsedModelId = parseId(modelId);
        if (parsedModelId != null) {
            query.eq(DeviceInstances::getDeviceModelId, parsedModelId);
        }
        List<DeviceInstances> scopedInstances = mapper.selectList(query);
        long total = scopedInstances.size();
        Set<Long> activeInstanceIds = scopedInstances.stream()
                .filter(DeviceInstanceLifecycle::isUsable)
                .map(DeviceInstances::getId)
                .collect(java.util.stream.Collectors.toSet());
        long active = activeInstanceIds.size();
        long retired = scopedInstances.stream()
                .filter(instance -> DeviceInstanceLifecycle.RETIRED.equals(instance.getLifecycleStatus()))
                .count();
        long online = twinStatesMapper.selectList(Wrappers.<DeviceTwinStates>lambdaQuery())
                .stream()
                .filter(state -> "ONLINE".equalsIgnoreCase(state.getOnlineStatus()))
                .filter(state -> activeInstanceIds.contains(state.getInstanceId()))
                .count();
        Map<String, Long> result = new HashMap<>();
        result.put("total", total);
        result.put("active", active);
        result.put("retired", retired);
        result.put("online", online);
        result.put("offline", Math.max(0, active - online));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceInstances savePayload(Map<String, Object> payload) {
        DeviceInstances instance = new DeviceInstances();
        Object id = first(payload, "id", "instanceId");
        if (id != null && !String.valueOf(id).isBlank()) {
            instance.setId(Long.valueOf(String.valueOf(id)));
        }
        boolean deviceModelIdSpecified = payload.containsKey("deviceModelId") || payload.containsKey("modelId");
        Object modelId = first(payload, "deviceModelId", "modelId");
        if (modelId != null && !String.valueOf(modelId).isBlank()) {
            instance.setDeviceModelId(Long.valueOf(String.valueOf(modelId)));
        }
        instance.setInstanceName(stringValue(first(payload, "instanceName", "name")));
        instance.setBoundAdapterName(stringValue(first(payload, "boundAdapterName", "adapterName")));
        instance.setBoundDevicePoint(stringValue(first(payload, "boundDevicePoint", "devicePoint")));
        instance.setPicture(stringValue(payload.get("picture")));

        boolean creating = instance.getId() == null;
        if (creating) {
            instance.setLifecycleStatus(DeviceInstanceLifecycle.IN_USE);
            deviceModelService.requireRuntimeReadyForUpdate(instance.getDeviceModelId());
        } else {
            DeviceInstances existing = mapper.selectById(instance.getId());
            if (existing == null) {
                throw new IllegalArgumentException("设备实例不存在: " + instance.getId());
            }
            requireUsable(existing, "设备实例已注销，不能继续修改");
            if (!deviceModelIdSpecified) {
                instance.setDeviceModelId(existing.getDeviceModelId());
            }
            if (!Objects.equals(existing.getDeviceModelId(), instance.getDeviceModelId())) {
                throw new IllegalStateException("设备实例创建后不能更换设备模型");
            }
            instance.setLifecycleStatus(existing.getLifecycleStatus());
        }

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

        if (creating) {
            instance.setCreateTime(OffsetDateTime.now());
            mapper.insert(instance);
            createDefaultTwinState(instance.getId(), instance.getDeviceModelId());
            dataIndexService.createDefaultDataSetsForDeviceInstance(
                    instance.getDeviceModelId(),
                    instance.getId(),
                    instance.getInstanceName()
            );
            createComponentSlotsFromBom(instance.getId(), instance.getDeviceModelId());
        } else {
            mapper.updateById(instance);
        }
        protocolMapperService.refreshAdapterRouteTable();
        eventPublisher.publishEvent(new DeviceInstanceSavedEvent(
                instance.getId(), instance.getDeviceModelId(), creating));
        return instance;
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceInstances retire(String id) {
        Long instanceId = parseId(id);
        if (instanceId == null) {
            throw new IllegalArgumentException("设备实例ID不能为空");
        }
        DeviceInstances instance = mapper.selectById(instanceId);
        if (instance == null) {
            throw new IllegalArgumentException("设备实例不存在: " + instanceId);
        }
        if (DeviceInstanceLifecycle.RETIRED.equals(instance.getLifecycleStatus())) {
            return instance;
        }
        DeviceTwinStates snapshot = getSnapshot(instanceId);
        if (snapshot != null && snapshot.getCurrentCmdState() != null
                && !"IDLE".equals(snapshot.getCurrentCmdState())) {
            throw new IllegalStateException("设备当前仍有指令在执行，不能注销");
        }
        instance.setLifecycleStatus(DeviceInstanceLifecycle.RETIRED);
        mapper.updateById(instance);
        protocolMapperService.refreshAdapterRouteTable();
        eventPublisher.publishEvent(new DeviceInstanceRetiredEvent(instanceId));
        return instance;
    }

    @Override
    public void delete(Serializable id) {
        throw new IllegalStateException("设备实例不支持物理删除，请使用注销操作");
    }

    public DeviceInstances requireUsable(Long instanceId) {
        if (instanceId == null) {
            throw new IllegalArgumentException("设备实例ID不能为空");
        }
        DeviceInstances instance = mapper.selectById(instanceId);
        if (instance == null) {
            throw new IllegalArgumentException("设备实例不存在: " + instanceId);
        }
        requireUsable(instance, "设备实例已注销，不能执行该操作");
        return instance;
    }

    private void requireUsable(DeviceInstances instance, String message) {
        if (!DeviceInstanceLifecycle.isUsable(instance)) {
            throw new IllegalStateException(message);
        }
    }

    public void requireOnline(Long instanceId) {
        DeviceTwinStates state = getSnapshot(instanceId);
        if (state == null || !"ONLINE".equalsIgnoreCase(state.getOnlineStatus())) {
            throw new IllegalStateException("设备当前处于离线状态 (OFFLINE)，无法下发控制指令");
        }
        if (state.getLastOnlineTime() == null
                || Duration.between(state.getLastOnlineTime(), OffsetDateTime.now()).getSeconds() > 30) {
            throw new IllegalStateException("设备通信已断开（超过30秒未收到心跳遥测），当前处于离线状态，无法下发控制指令");
        }
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

    private void createDefaultTwinState(Long instanceId, Long modelId) {
        DeviceModels model = modelId == null ? null : deviceModelsMapper.selectById(modelId);
        DeviceTwinStates state = new DeviceTwinStates();
        state.setInstanceId(instanceId);
        state.setCurrentOpState(initialOperationState(model == null ? null : model.getOpState()));
        state.setCurrentCmdState(initialStateName(model == null ? null : model.getCmdState(), "IDLE"));
        state.setOnlineStatus("OFFLINE");
        state.setCurrentAttr(JsonNodeSupport.objectNode());
        state.setUpdateTime(OffsetDateTime.now());
        twinStatesMapper.insert(state);
    }

    private String initialStateName(JsonNode stateSpace, String fallback) {
        if (stateSpace != null && stateSpace.hasNonNull("initialStateName") && !stateSpace.path("initialStateName").asText().isBlank()) {
            return stateSpace.path("initialStateName").asText();
        }
        return fallback;
    }

    private ObjectNode initialOperationState(JsonNode stateSpace) {
        ObjectNode stateVector = JsonNodeSupport.objectNode();
        if (stateSpace == null || !stateSpace.path("regions").isArray()) {
            return stateVector;
        }
        for (JsonNode region : stateSpace.path("regions")) {
            String regionName = region.path("regionName").asText("");
            String initialStateName = region.path("initialStateName").asText("");
            if (!regionName.isBlank()) {
                com.fasterxml.jackson.databind.node.ArrayNode arr = stateVector.putArray(regionName);
                if (!initialStateName.isBlank()) arr.add(initialStateName);
            }
        }
        return stateVector;
    }

    private void createComponentSlotsFromBom(Long instanceId, Long modelId) {
        if (instanceId == null || modelId == null) {
            return;
        }
        DeviceModels model = deviceModelsMapper.selectById(modelId);
        if (model == null || model.getComponentsBom() == null || !model.getComponentsBom().isArray()) {
            return;
        }
        for (JsonNode item : model.getComponentsBom()) {
            String slotName = text(item, "slotName", text(item, "componentName", text(item, "name", "")));
            if (slotName == null || slotName.isBlank()) {
                continue;
            }
            int quantity = Math.max(1, item.path("quantity").asInt(1));
            for (int index = 1; index <= quantity; index++) {
                DeviceComponents component = new DeviceComponents();
                component.setComponentName(quantity > 1 ? slotName + "-" + index : slotName);
                if (item.hasNonNull("categoryId") && !item.path("categoryId").asText().isBlank()) {
                    component.setCategoryId(item.path("categoryId").asLong());
                }
                component.setParentInstanceId(instanceId);
                component.setStatus("使用中");
                component.setSpecification(JsonNodeSupport.objectNode());
                component.setInstallTime(OffsetDateTime.now());
                component.setCreateTime(OffsetDateTime.now());
                deviceComponentService.save(component);
            }
        }
    }
    private void applyLifecycleFilter(LambdaQueryWrapper<DeviceInstances> query, String lifecycleStatus) {
        if (lifecycleStatus == null || lifecycleStatus.isBlank()) {
            return;
        }
        String normalized = lifecycleStatus.trim();
        if (!Set.of(DeviceInstanceLifecycle.IN_USE, DeviceInstanceLifecycle.RETIRED).contains(normalized)) {
            throw new IllegalArgumentException("不支持的设备实例生命周期状态: " + normalized);
        }
        query.eq(DeviceInstances::getLifecycleStatus, normalized);
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
