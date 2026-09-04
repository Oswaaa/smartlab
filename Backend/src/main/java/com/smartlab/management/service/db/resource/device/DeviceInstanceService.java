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
import com.smartlab.management.entity.resource.device.DeviceInstanceKind;
import com.smartlab.management.entity.resource.device.DeviceInstanceLifecycle;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.management.mapper.resource.device.DeviceTwinStatesMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import com.smartlab.management.service.db.resource.data.DataIndexService;
import com.smartlab.global.event.DeviceInstanceDeletedEvent;
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

import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import com.smartlab.management.entity.resource.data.DataIndex;
import com.smartlab.management.entity.workflow.ExecutionLog;
import com.smartlab.management.entity.constraint.ViolationLog;
import com.smartlab.management.entity.resource.scene.SceneDetail;
import com.smartlab.management.entity.resource.device.ResourceStructure;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.constraint.ConstraintRule;
import com.smartlab.management.mapper.resource.adapter.AdapterIndexMapper;
import com.smartlab.management.mapper.workflow.ExecutionLogMapper;
import com.smartlab.management.mapper.constraint.ViolationLogMapper;
import com.smartlab.management.mapper.resource.scene.SceneDetailMapper;
import com.smartlab.management.mapper.resource.device.ResourceStructureMapper;
import com.smartlab.management.mapper.resource.device.DeviceComponentsMapper;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.mapper.constraint.ConstraintRuleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

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

    @Autowired(required = false)
    AdapterIndexMapper adapterIndexMapper;

    @Autowired(required = false)
    ExecutionLogMapper executionLogMapper;

    @Autowired(required = false)
    ViolationLogMapper violationLogMapper;

    @Autowired(required = false)
    SceneDetailMapper sceneDetailMapper;

    @Autowired(required = false)
    ResourceStructureMapper resourceStructureMapper;

    @Autowired(required = false)
    DeviceComponentsMapper deviceComponentsMapper;

    @Autowired(required = false)
    TaskMapper taskMapper;

    @Autowired(required = false)
    ConstraintRuleMapper constraintRuleMapper;

    @Autowired(required = false)
    JdbcTemplate jdbcTemplate;

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
        applyInstanceKindFilter(query, null);
        return mapper.selectList(query.orderByDesc(DeviceInstances::getId));
    }

    public List<DeviceInstances> list(String lifecycleStatus, String instanceKind) {
        LambdaQueryWrapper<DeviceInstances> query = Wrappers.lambdaQuery();
        applyLifecycleFilter(query, lifecycleStatus);
        applyInstanceKindFilter(query, instanceKind);
        return mapper.selectList(query.orderByDesc(DeviceInstances::getId));
    }

    public List<DeviceInstanceDTO> listDTO(String lifecycleStatus) {
        return listDTO(lifecycleStatus, DeviceInstanceKind.PHYSICAL);
    }

    public List<DeviceInstanceDTO> listDTO(String lifecycleStatus, String instanceKind) {
        List<DeviceInstances> records = list(lifecycleStatus, instanceKind);
        return assembleDTOs(records);
    }

    public PageResult<DeviceInstances> page(long pageNo, long pageSize, String modelId, String keyword,
                                             Boolean online, String lifecycleStatus) {
        return page(pageNo, pageSize, modelId, keyword, online, lifecycleStatus, DeviceInstanceKind.PHYSICAL);
    }

    public PageResult<DeviceInstances> page(long pageNo, long pageSize, String modelId, String keyword,
                                             Boolean online, String lifecycleStatus, String instanceKind) {
        LambdaQueryWrapper<DeviceInstances> query = Wrappers.lambdaQuery();
        Long parsedModelId = parseId(modelId);
        if (parsedModelId != null) {
            query.eq(DeviceInstances::getDeviceModelId, parsedModelId);
        }
        applyLifecycleFilter(query, lifecycleStatus);
        applyInstanceKindFilter(query, instanceKind);
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
        return pageDTO(pageNo, pageSize, modelId, keyword, online, lifecycleStatus, DeviceInstanceKind.PHYSICAL);
    }

    public PageResult<DeviceInstanceDTO> pageDTO(long pageNo, long pageSize, String modelId, String keyword,
                                                 Boolean online, String lifecycleStatus, String instanceKind) {
        LambdaQueryWrapper<DeviceInstances> query = Wrappers.lambdaQuery();
        Long parsedModelId = parseId(modelId);
        if (parsedModelId != null) {
            query.eq(DeviceInstances::getDeviceModelId, parsedModelId);
        }
        applyLifecycleFilter(query, lifecycleStatus);
        applyInstanceKindFilter(query, instanceKind);
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
            dto.setInstanceKind(inst.getInstanceKind());
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
        applyInstanceKindFilter(query, DeviceInstanceKind.PHYSICAL);
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
        instance.setInstanceKind(DeviceInstanceKind.PHYSICAL);

        boolean creating = instance.getId() == null;
        if (creating) {
            instance.setLifecycleStatus(DeviceInstanceLifecycle.IN_USE);
            deviceModelService.requireRuntimeReadyForUpdate(instance.getDeviceModelId());
        } else {
            DeviceInstances existing = mapper.selectById(instance.getId());
            if (existing == null) {
                throw new IllegalArgumentException("设备实例不存在: " + instance.getId());
            }
            if (!DeviceInstanceKind.isPhysical(existing)) {
                throw new IllegalStateException("不能通过实例保存接口修改虚拟或临时设备");
            }
            requireUsable(existing, "设备实例已注销，不能继续修改");
            if (!deviceModelIdSpecified) {
                instance.setDeviceModelId(existing.getDeviceModelId());
            }
            if (!Objects.equals(existing.getDeviceModelId(), instance.getDeviceModelId())) {
                throw new IllegalStateException("设备实例创建后不能更换设备模型");
            }
            instance.setLifecycleStatus(existing.getLifecycleStatus());
            instance.setInstanceKind(DeviceInstanceKind.PHYSICAL);
        }
        ObjectNode configNode = toObjectNode(first(payload, "instanceConfig", "commConfig"));
        if (payload.containsKey("localConstraints")) {
            configNode.set("constraints", JsonNodeSupport.toNode(payload.get("localConstraints")));
        }
        if (hasAdapterBinding(instance)) {
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
            persistCreatedRuntime(instance, DeviceInstanceKind.PHYSICAL);
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
    @Transactional(rollbackFor = Exception.class)
    public void delete(Serializable id) {
        Long instanceId = parseId(String.valueOf(id));
        if (instanceId == null) {
            throw new IllegalArgumentException("设备实例ID不能为空");
        }
        DeviceInstances instance = mapper.selectById(instanceId);
        if (instance == null) {
            throw new IllegalArgumentException("设备实例不存在: " + instanceId);
        }

        // 1. 状态前置：必须是已注销状态
        if (!DeviceInstanceLifecycle.RETIRED.equals(instance.getLifecycleStatus())) {
            throw new IllegalStateException("只有已注销的设备实例才允许彻底删除，请先注销设备");
        }

        // 2. 防线1：检查实验执行日志 EXECUTION_LOG
        if (executionLogMapper != null) {
            Long logCount = executionLogMapper.selectCount(
                    Wrappers.<ExecutionLog>lambdaQuery().eq(ExecutionLog::getDeviceInstanceId, instanceId));
            if (logCount != null && logCount > 0) {
                throw new IllegalStateException("该设备已产生实验执行日志（共 " + logCount + " 条），涉及历史审计追溯，禁止物理删除，仅支持保持注销状态");
            }
        }

        // 3. 防线2：检查采集数据集物理表 DATA_INDEX & 物理数据行
        List<DataIndex> dataIndices = dataIndexService != null ? dataIndexService.listByDeviceInstance(instanceId) : List.of();
        if (jdbcTemplate != null && dataIndices != null) {
            for (DataIndex index : dataIndices) {
                if (index.getDataTable() != null && !index.getDataTable().isBlank()) {
                    try {
                        Integer rowCount = jdbcTemplate.queryForObject(
                                "SELECT COUNT(1) FROM \"" + index.getDataTable().replace("\"", "\"\"") + "\"",
                                Integer.class
                        );
                        if (rowCount != null && rowCount > 0) {
                            throw new IllegalStateException("该设备已采集并存储了实验数据（共 " + rowCount + " 条），禁止物理删除，仅支持保持注销状态");
                        }
                    } catch (IllegalStateException e) {
                        throw e;
                    } catch (Exception ignored) {
                        // 忽略物理表不存在等异常，允许继续后续级联删除
                    }
                }
            }
        }

        // 4. 防线3：检查工作流任务 TASK.resource_map
        if (taskMapper != null) {
            List<Task> tasks = taskMapper.selectList(Wrappers.emptyWrapper());
            for (Task task : tasks) {
                if (taskResourceMapBindsInstance(task.getResourceMap(), instanceId)) {
                    String taskName = task.getTaskName() != null ? task.getTaskName() : String.valueOf(task.getId());
                    throw new IllegalStateException("该设备曾参与实验工作流任务【" + taskName + "】，禁止物理删除，仅支持保持注销状态");
                }
            }
        }

        // 5. 防线4：检查违规审计日志 VIOLATION_LOG
        if (violationLogMapper != null) {
            Long violationCount = violationLogMapper.selectCount(
                    Wrappers.<ViolationLog>lambdaQuery().eq(ViolationLog::getDeviceInstanceId, instanceId));
            if (violationCount != null && violationCount > 0) {
                throw new IllegalStateException("该设备存在安全违规审计记录（共 " + violationCount + " 条），禁止物理删除，仅支持保持注销状态");
            }
        }

        // 6. 防线5：检查 3D 场景与拓扑管路 SCENE_DETAIL & RESOURCE_STRUCTURE
        if (sceneDetailMapper != null) {
            Long sceneCount = sceneDetailMapper.selectCount(
                    Wrappers.<SceneDetail>lambdaQuery().eq(SceneDetail::getDeviceInstanceId, instanceId));
            if (sceneCount != null && sceneCount > 0) {
                throw new IllegalStateException("该设备仍绑定在3D场景中（共 " + sceneCount + " 处），请先在场景中移除该设备");
            }
        }
        if (resourceStructureMapper != null) {
            Long structCount = resourceStructureMapper.selectCount(
                    Wrappers.<ResourceStructure>lambdaQuery()
                            .eq(ResourceStructure::getSourceInstanceId, instanceId)
                            .or()
                            .eq(ResourceStructure::getTargetInstanceId, instanceId));
            if (structCount != null && structCount > 0) {
                throw new IllegalStateException("该设备仍绑定在设备拓扑管路结构中（共 " + structCount + " 处），请先在拓扑中解绑");
            }
        }

        // 7. 防线6：检查是否作为子部件被安装在其他父设备上 DEVICE_COMPONENTS.self_instance_id
        if (deviceComponentsMapper != null) {
            Long mountedCount = deviceComponentsMapper.selectCount(
                    Wrappers.<DeviceComponents>lambdaQuery().eq(DeviceComponents::getSelfInstanceId, instanceId));
            if (mountedCount != null && mountedCount > 0) {
                throw new IllegalStateException("该设备作为子部件安装在其他设备中，请先在父设备中卸载该部件后再删除");
            }
        }

        // 8. 防线7：检查全局约束规则强绑定 CONSTRAINT_RULE
        if (constraintRuleMapper != null) {
            List<ConstraintRule> rules = constraintRuleMapper.selectList(Wrappers.emptyWrapper());
            for (ConstraintRule rule : rules) {
                if (hasExplicitInstanceBinding(rule.getBindings(), instanceId) || hasExplicitInstanceAction(rule.getViolationActions(), instanceId)) {
                    String ruleName = rule.getRuleName() != null ? rule.getRuleName() : String.valueOf(rule.getId());
                    throw new IllegalStateException("该设备被约束规则【" + ruleName + "】绑定，禁止物理删除，请先解除约束规则绑定");
                }
            }
        }

        // 9. 级联清理
        // 9.1 清理关联的空 DataIndex 及物理表
        if (dataIndexService != null && dataIndices != null) {
            for (DataIndex index : dataIndices) {
                try {
                    dataIndexService.delete(index.getId());
                } catch (Exception ignored) {
                }
            }
        }

        // 9.2 清理属于该实例的组件槽位
        if (deviceComponentsMapper != null) {
            deviceComponentsMapper.delete(
                    Wrappers.<DeviceComponents>lambdaQuery().eq(DeviceComponents::getParentInstanceId, instanceId));
        }

        // 9.3 清理孪生快照
        twinStatesMapper.delete(Wrappers.<DeviceTwinStates>lambdaQuery().eq(DeviceTwinStates::getInstanceId, instanceId));

        // 9.4 物理删除设备实例主记录
        mapper.deleteById(instanceId);

        // 9.5 刷新适配器路由表并通知 MQTT 退订
        protocolMapperService.refreshAdapterRouteTable();
        eventPublisher.publishEvent(new DeviceInstanceDeletedEvent(instanceId));
    }

    private boolean hasExplicitInstanceBinding(JsonNode bindings, Long instanceId) {
        if (bindings == null || !bindings.isObject() || instanceId == null) return false;
        var fields = bindings.fields();
        while (fields.hasNext()) {
            JsonNode binding = fields.next().getValue();
            JsonNode source = binding.path("source");
            if (source.hasNonNull("deviceInstanceId") && source.path("deviceInstanceId").asLong(0) == instanceId) {
                return true;
            }
        }
        return false;
    }

    private boolean hasExplicitInstanceAction(JsonNode actions, Long instanceId) {
        if (actions == null || instanceId == null) return false;
        if (actions.isArray()) {
            for (JsonNode item : actions) {
                if (item.hasNonNull("deviceInstanceId") && item.path("deviceInstanceId").asLong(0) == instanceId) {
                    return true;
                }
            }
        } else if (actions.isObject()) {
            if (actions.hasNonNull("deviceInstanceId") && actions.path("deviceInstanceId").asLong(0) == instanceId) {
                return true;
            }
        }
        return false;
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

    public DeviceInstances requirePhysical(Long instanceId) {
        DeviceInstances instance = requireUsable(instanceId);
        if (!DeviceInstanceKind.isPhysical(instance)) {
            throw new IllegalStateException("只能对物理设备实例执行该操作");
        }
        return instance;
    }

    public DeviceInstances requireControllable(Long instanceId) {
        DeviceInstances instance = requireUsable(instanceId);
        if (!DeviceInstanceKind.allowsManualControl(instance)) {
            throw new IllegalStateException("临时设备不能进行控制台点动");
        }
        return instance;
    }

    private void requireUsable(DeviceInstances instance, String message) {
        if (!DeviceInstanceLifecycle.isUsable(instance)) {
            throw new IllegalStateException(message);
        }
    }

    public void requireOnline(Long instanceId) {
        DeviceInstances instance = mapper.selectById(instanceId);
        if (instance != null && instance.getBoundAdapterName() != null && !instance.getBoundAdapterName().isBlank() && adapterIndexMapper != null) {
            AdapterIndex adapter = adapterIndexMapper.selectOne(Wrappers.<AdapterIndex>lambdaQuery().eq(AdapterIndex::getAdapterName, instance.getBoundAdapterName().trim()).last("limit 1"));
            if (adapter != null && "DISABLED".equalsIgnoreCase(adapter.getStatus())) {
                throw new IllegalStateException("设备绑定的 Adapter“" + instance.getBoundAdapterName() + "”已被停用，无法下发控制指令");
            }
        }
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
        createDefaultTwinState(instanceId, modelId, "OFFLINE");
    }

    private void createDefaultTwinState(Long instanceId, Long modelId, String onlineStatus) {
        DeviceModels model = modelId == null ? null : deviceModelsMapper.selectById(modelId);
        DeviceTwinStates state = new DeviceTwinStates();
        state.setInstanceId(instanceId);
        state.setCurrentOpState(initialOperationState(model == null ? null : model.getOpState()));
        state.setCurrentCmdState(initialStateName(model == null ? null : model.getCmdState(), "IDLE"));
        state.setOnlineStatus(onlineStatus == null || onlineStatus.isBlank() ? "OFFLINE" : onlineStatus);
        if ("ONLINE".equalsIgnoreCase(state.getOnlineStatus())) {
            state.setLastOnlineTime(OffsetDateTime.now());
        }
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

    @Transactional(rollbackFor = Exception.class)
    public DeviceInstances createTemporary(Long deviceModelId) {
        if (deviceModelId == null) {
            throw new IllegalArgumentException("设备模型ID不能为空");
        }
        DeviceModels model = deviceModelService.requireRuntimeReady(deviceModelId);
        DeviceInstances instance = new DeviceInstances();
        instance.setDeviceModelId(deviceModelId);
        instance.setInstanceName(temporaryInstanceName(model));
        instance.setInstanceKind(DeviceInstanceKind.TEMPORARY);
        instance.setLifecycleStatus(DeviceInstanceLifecycle.IN_USE);
        instance.setInstanceConfig(JsonNodeSupport.objectNode());
        instance.setCreateTime(OffsetDateTime.now());
        mapper.insert(instance);
        persistCreatedRuntime(instance, DeviceInstanceKind.TEMPORARY);
        eventPublisher.publishEvent(new DeviceInstanceSavedEvent(
                instance.getId(), instance.getDeviceModelId(), true));
        return instance;
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceInstances createVirtual(DeviceInstances physical, String virtualDevicePoint) {
        if (physical == null || physical.getId() == null) {
            throw new IllegalArgumentException("虚拟实例必须从物理实例克隆");
        }
        if (!DeviceInstanceKind.isPhysical(physical)) {
            throw new IllegalArgumentException("只能从物理设备实例克隆虚拟机");
        }
        String adapterName = physical.getBoundAdapterName();
        String virtualPoint = virtualDevicePoint == null ? "" : virtualDevicePoint.trim();
        if (adapterName == null || adapterName.isBlank() || virtualPoint.isBlank()) {
            throw new IllegalArgumentException("虚拟实例必须绑定 Adapter 与批复的虚拟点位");
        }
        if (physical.getBoundDevicePoint() == null || physical.getBoundDevicePoint().isBlank()) {
            throw new IllegalStateException("物理实例未绑定 Adapter 点位，无法克隆虚拟机: " + physical.getId());
        }
        DeviceInstances existing = findVirtual(adapterName, virtualPoint);
        if (existing != null) {
            return existing;
        }
        DeviceModels model = deviceModelService.requireRuntimeReady(physical.getDeviceModelId());
        DeviceInstances instance = new DeviceInstances();
        instance.setDeviceModelId(physical.getDeviceModelId());
        instance.setInstanceName(virtualInstanceName(model));
        instance.setBoundAdapterName(adapterName.trim());
        instance.setBoundDevicePoint(virtualPoint);
        instance.setInstanceKind(DeviceInstanceKind.VIRTUAL);
        instance.setLifecycleStatus(DeviceInstanceLifecycle.IN_USE);
        ObjectNode configNode = JsonNodeSupport.objectNode();
        configNode.set("adapterBinding", protocolMapperService.buildVirtualAdapterBinding(physical, virtualPoint));
        instance.setInstanceConfig(configNode);
        instance.setCreateTime(OffsetDateTime.now());
        mapper.insert(instance);
        persistCreatedRuntime(instance, DeviceInstanceKind.VIRTUAL);
        protocolMapperService.refreshAdapterRouteTable();
        eventPublisher.publishEvent(new DeviceInstanceSavedEvent(
                instance.getId(), instance.getDeviceModelId(), true));
        return instance;
    }

    public DeviceInstances findVirtual(String adapterName, String devicePoint) {
        if (adapterName == null || adapterName.isBlank() || devicePoint == null || devicePoint.isBlank()) {
            return null;
        }
        return mapper.selectOne(Wrappers.<DeviceInstances>lambdaQuery()
                .eq(DeviceInstances::getBoundAdapterName, adapterName.trim())
                .eq(DeviceInstances::getBoundDevicePoint, devicePoint.trim())
                .eq(DeviceInstances::getInstanceKind, DeviceInstanceKind.VIRTUAL)
                .eq(DeviceInstances::getLifecycleStatus, DeviceInstanceLifecycle.IN_USE)
                .orderByAsc(DeviceInstances::getId)
                .last("limit 1"));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteTemporary(Long instanceId) {
        DeviceInstances instance = mapper.selectById(instanceId);
        if (instance == null) {
            return;
        }
        if (!DeviceInstanceKind.isTemporary(instance)) {
            throw new IllegalStateException("只能删除 TEMPORARY 设备实例: " + instanceId);
        }
        forceDeleteNonPhysical(instance);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteVirtual(Long instanceId) {
        DeviceInstances instance = mapper.selectById(instanceId);
        if (instance == null) {
            return;
        }
        if (!DeviceInstanceKind.isVirtual(instance)) {
            throw new IllegalStateException("只能删除 VIRTUAL 设备实例: " + instanceId);
        }
        forceDeleteNonPhysical(instance);
    }

    private void persistCreatedRuntime(DeviceInstances instance, String kind) {
        createDefaultTwinState(instance.getId(), instance.getDeviceModelId(), onlineStatusForKind(kind));
        if (DeviceInstanceKind.TEMPORARY.equals(kind)) {
            return;
        }
        dataIndexService.createDefaultDataSetsForDeviceInstance(
                instance.getDeviceModelId(),
                instance.getId(),
                instance.getInstanceName()
        );
        if (DeviceInstanceKind.PHYSICAL.equals(kind)) {
            createComponentSlotsFromBom(instance.getId(), instance.getDeviceModelId());
        }
    }

    private void forceDeleteNonPhysical(DeviceInstances instance) {
        Long instanceId = instance.getId();
        eventPublisher.publishEvent(new DeviceInstanceRetiredEvent(instanceId));
        if (dataIndexService != null) {
            List<DataIndex> dataIndices = dataIndexService.listByDeviceInstance(instanceId);
            if (dataIndices != null) {
                for (DataIndex index : dataIndices) {
                    try {
                        dataIndexService.delete(index.getId());
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        if (deviceComponentsMapper != null) {
            deviceComponentsMapper.delete(
                    Wrappers.<DeviceComponents>lambdaQuery().eq(DeviceComponents::getParentInstanceId, instanceId));
        }
        twinStatesMapper.delete(Wrappers.<DeviceTwinStates>lambdaQuery().eq(DeviceTwinStates::getInstanceId, instanceId));
        mapper.deleteById(instanceId);
        protocolMapperService.refreshAdapterRouteTable();
        eventPublisher.publishEvent(new DeviceInstanceDeletedEvent(instanceId));
    }

    private String virtualInstanceName(DeviceModels model) {
        String modelName = model == null || model.getModelName() == null || model.getModelName().isBlank()
                ? String.valueOf(model == null ? "" : model.getId())
                : model.getModelName().trim();
        return "VIRTUAL-" + modelName + "-" + System.currentTimeMillis();
    }

    private String temporaryInstanceName(DeviceModels model) {
        String modelName = model == null || model.getModelName() == null || model.getModelName().isBlank()
                ? String.valueOf(model == null ? "" : model.getId())
                : model.getModelName().trim();
        return "TEMPORARY-" + modelName + "-" + System.currentTimeMillis();
    }

    private String onlineStatusForKind(String kind) {
        return DeviceInstanceKind.TEMPORARY.equals(kind) ? "ONLINE" : "OFFLINE";
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

    private void applyInstanceKindFilter(LambdaQueryWrapper<DeviceInstances> query, String instanceKind) {
        if (instanceKind != null && "ALL".equalsIgnoreCase(instanceKind.trim())) {
            return;
        }
        String kind = (instanceKind == null || instanceKind.isBlank())
                ? DeviceInstanceKind.PHYSICAL
                : DeviceInstanceKind.normalize(instanceKind);
        query.and(wrapper -> {
            wrapper.eq(DeviceInstances::getInstanceKind, kind);
            if (DeviceInstanceKind.PHYSICAL.equals(kind)) {
                wrapper.or().isNull(DeviceInstances::getInstanceKind);
            }
        });
    }


    private boolean taskResourceMapBindsInstance(JsonNode resourceMap, Long instanceId) {
        if (resourceMap == null || instanceId == null || !resourceMap.path("deviceBindings").isObject()) {
            return false;
        }
        var fields = resourceMap.path("deviceBindings").fields();
        while (fields.hasNext()) {
            if (fields.next().getValue().path("deviceInstanceId").asLong(0) == instanceId) {
                return true;
            }
        }
        return false;
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
