package com.smartlab.management.service.db.resource.device;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.AdapterRouteDTO;
import com.smartlab.management.dto.PageResult;
import com.smartlab.management.entity.DeviceInstances;
import com.smartlab.management.entity.DeviceTwinStates;
import com.smartlab.management.mapper.DeviceInstancesMapper;
import com.smartlab.management.mapper.DeviceTwinStatesMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import com.smartlab.management.service.db.resource.data.DataIndexService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设备实例表服务。
 * 对应 DEVICE_INSTANCES 表，并在新增实例时初始化 DEVICE_TWIN_STATES 状态快照。
 */
@Service
public class DeviceInstanceService extends ManagementCrudService<DeviceInstances> {

    private final DeviceInstancesMapper mapper;
    private final DeviceTwinStatesMapper twinStatesMapper;
    private final DataIndexService dataIndexService;
    private final ConcurrentHashMap<String, AdapterRouteDTO> adapterRouteTable = new ConcurrentHashMap<>();

    public DeviceInstanceService(DeviceInstancesMapper mapper,
                                 DeviceTwinStatesMapper twinStatesMapper,
                                 DataIndexService dataIndexService) {
        super(mapper);
        this.mapper = mapper;
        this.twinStatesMapper = twinStatesMapper;
        this.dataIndexService = dataIndexService;
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

        Object config = first(payload, "instanceConfig", "commConfig");
        if (config == null) {
            config = new HashMap<String, Object>();
        }
        if (config instanceof Map<?, ?> configMap && payload.containsKey("localConstraints")) {
            Map<String, Object> merged = new HashMap<>();
            configMap.forEach((key, value) -> merged.put(String.valueOf(key), value));
            merged.put("constraints", payload.get("localConstraints"));
            config = merged;
        }
        instance.setInstanceConfig(JsonNodeSupport.toNode(config));
        if (instance.getId() == null) {
            instance.setCreateTime(LocalDateTime.now());
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
        refreshAdapterRouteTable();
        return instance;
    }

    public void delete(String id) {
        Long instanceId = parseId(id);
        long dataSetCount = dataIndexService.countByDeviceInstance(instanceId);
        if (dataSetCount > 0) {
            throw new IllegalStateException("该设备实例已绑定 " + dataSetCount + " 个数据集，不能硬删除。请保留历史数据链路，后续可改为停用/归档。");
        }
        mapper.deleteById(instanceId);
        twinStatesMapper.delete(Wrappers.<DeviceTwinStates>lambdaQuery().eq(DeviceTwinStates::getInstanceId, instanceId));
        refreshAdapterRouteTable();
    }

    /**
     * 刷新内存中的 Adapter 路由表。
     */
    public Map<String, AdapterRouteDTO> refreshAdapterRouteTable() {
        adapterRouteTable.clear();
        List<DeviceInstances> instances = mapper.selectList(Wrappers.<DeviceInstances>lambdaQuery()
                .isNotNull(DeviceInstances::getBoundAdapterName)
                .isNotNull(DeviceInstances::getBoundDevicePoint)
                .orderByAsc(DeviceInstances::getId));
        for (DeviceInstances instance : instances) {
            String key = routeKey(instance.getBoundAdapterName(), instance.getBoundDevicePoint());
            adapterRouteTable.put(key, toRoute(instance));
        }
        return getAdapterRouteTable();
    }

    /**
     * 查询当前内存中的 Adapter 路由表。
     */
    public Map<String, AdapterRouteDTO> getAdapterRouteTable() {
        return Collections.unmodifiableMap(new HashMap<>(adapterRouteTable));
    }

    /**
     * 根据 Adapter 标识和设备点字段查询绑定的设备实例。
     */
    public AdapterRouteDTO resolveAdapterRoute(String adapterName, String devicePoint) {
        if (adapterRouteTable.isEmpty()) {
            refreshAdapterRouteTable();
        }
        return adapterRouteTable.get(routeKey(adapterName, devicePoint));
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

    public void control(String id, String commandId, Map<String, Object> parameters) {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException("commandId 不能为空");
        }
        DeviceTwinStates state = getSnapshot(id);
        if (state == null) {
            throw new IllegalStateException("设备状态不存在，无法发送指令");
        }
        state.setCurrentCmdState("PENDING");
        state.setUpdateTime(LocalDateTime.now());
        twinStatesMapper.updateById(state);
    }

    private void createDefaultTwinState(Long instanceId) {
        DeviceTwinStates state = new DeviceTwinStates();
        state.setInstanceId(instanceId);
        state.setCurrentOpState("Idle");
        state.setCurrentCmdState("PENDING");
        state.setOnlineStatus("UNKNOWN");
        state.setCurrentAttr(JsonNodeSupport.objectNode());
        state.setUpdateTime(LocalDateTime.now());
        twinStatesMapper.insert(state);
    }

    /**
     * 查询某个设备模型下的实例 ID 集合，用于按模型统计在线数量。
     */
    private Set<Long> loadInstanceIds(Long modelId) {
        Set<Long> result = new HashSet<>();
        mapper.selectList(Wrappers.<DeviceInstances>lambdaQuery()
                        .select(DeviceInstances::getId)
                        .eq(DeviceInstances::getDeviceModelId, modelId))
                .forEach(instance -> result.add(instance.getId()));
        return result;
    }

    /**
     * 将设备实例转换为 Adapter 路由表条目。
     */
    private AdapterRouteDTO toRoute(DeviceInstances instance) {
        AdapterRouteDTO route = new AdapterRouteDTO();
        route.setDeviceInstanceId(instance.getId());
        route.setDeviceModelId(instance.getDeviceModelId());
        route.setInstanceName(instance.getInstanceName());
        route.setBoundAdapterName(instance.getBoundAdapterName());
        route.setBoundDevicePoint(instance.getBoundDevicePoint());
        return route;
    }

    /**
     * 生成 Adapter 路由键。
     */
    private String routeKey(String adapterName, String devicePoint) {
        if (adapterName == null || adapterName.isBlank() || devicePoint == null || devicePoint.isBlank()) {
            throw new IllegalArgumentException("Adapter 标识和设备点字段不能为空");
        }
        return adapterName.trim() + "::" + devicePoint.trim();
    }

    private Object first(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            if (payload.containsKey(key)) {
                return payload.get(key);
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}


