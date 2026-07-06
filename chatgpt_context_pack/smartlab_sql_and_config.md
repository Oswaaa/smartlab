# smartlab_sql_and_config

Generated at: 2026-07-03T11:09:11

This file is generated from the local SmartLab repository for model-readable project context.

## File Tree

- Backend/src/main/java/com/smartlab/management/mapper/common/PostgresJsonbTypeHandler.java
- Backend/src/main/java/com/smartlab/management/mapper/constraint/ConstraintRuleMapper.java
- Backend/src/main/java/com/smartlab/management/mapper/constraint/ViolationLogMapper.java
- Backend/src/main/java/com/smartlab/management/mapper/resource/adapter/AdapterIndexMapper.java
- Backend/src/main/java/com/smartlab/management/mapper/resource/data/DataIndexMapper.java
- Backend/src/main/java/com/smartlab/management/mapper/resource/data/DataTemplateDetailMapper.java
- Backend/src/main/java/com/smartlab/management/mapper/resource/data/DataTemplateMainMapper.java
- Backend/src/main/java/com/smartlab/management/mapper/resource/device/DeviceCategoryMapper.java
- Backend/src/main/java/com/smartlab/management/mapper/resource/device/DeviceComponentsMapper.java
- Backend/src/main/java/com/smartlab/management/mapper/resource/device/DeviceInstancesMapper.java
- Backend/src/main/java/com/smartlab/management/mapper/resource/device/DeviceModelsMapper.java
- Backend/src/main/java/com/smartlab/management/mapper/resource/device/DeviceTwinStatesMapper.java
- Backend/src/main/java/com/smartlab/management/mapper/resource/device/PropertyTypeMapper.java
- Backend/src/main/java/com/smartlab/management/mapper/resource/device/ResourceStructureMapper.java
- Backend/src/main/java/com/smartlab/management/mapper/resource/scene/SceneDetailMapper.java
- Backend/src/main/java/com/smartlab/management/mapper/resource/scene/SceneMainMapper.java
- Backend/src/main/java/com/smartlab/management/mapper/user/PermissionInfoMapper.java
- Backend/src/main/java/com/smartlab/management/mapper/user/UserInfoMapper.java
- Backend/src/main/java/com/smartlab/management/mapper/workflow/FlowModelsMapper.java
- Backend/src/main/java/com/smartlab/management/mapper/workflow/FlowNodeMapper.java
- Backend/src/main/java/com/smartlab/management/mapper/workflow/StepLogMapper.java
- Backend/src/main/java/com/smartlab/management/mapper/workflow/TaskMapper.java
- Backend/src/main/java/com/smartlab/management/mapper/workflow/TaskStepMapper.java
- Backend/src/main/java/com/smartlab/management/service/protocol/AdapterPayloadMapperService.java
- Backend/src/main/java/com/smartlab/SmartLabApplication.java
- Backend/src/main/resources/application.yml
- Backend/src/main/resources/samples/adapter-heat-pressure-test.json
- Backend/src/main/resources/schemas/constraint-model.json
- Backend/src/main/resources/schemas/device-capability-model.json
- Backend/src/main/resources/schemas/device-state-machine-model.json
- Backend/src/main/resources/schemas/protocol-dict.json
- Backend/src/main/resources/schemas/resource-connection-graph.json
- Backend/src/main/resources/schemas/workflow-model.json
- Backend/src/main/resources/sql/permission_info_seed.sql
- schemas_archive/adapter-message-envelope.json
- schemas_archive/adapter-northbound-contract.json
- schemas_archive/README.md
- schemas_archive/resource-connection-graph.json
- schemas_archive/工作流模型.json
- schemas_archive/设备状态机模型.json
- schemas_archive/设备能力模型.json
- schemas_archive/资源结构图.json
- schemas_archive/需求约束模型.json

## Files

---

## Backend/src/main/java/com/smartlab/management/mapper/common/PostgresJsonbTypeHandler.java

````text
package com.smartlab.management.mapper.common;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * PostgresJsonbTypeHandler 领域实体/配置模型类。
 */
public class PostgresJsonbTypeHandler extends JacksonTypeHandler {

    public PostgresJsonbTypeHandler(Class<?> type) {
        super(type);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType) throws SQLException {
        PGobject value = new PGobject();
        value.setType("jsonb");
        value.setValue(toJson(parameter));
        ps.setObject(i, value);
    }
}


````

---

## Backend/src/main/java/com/smartlab/management/mapper/constraint/ConstraintRuleMapper.java

````text
package com.smartlab.management.mapper.constraint;

import com.smartlab.management.entity.constraint.ConstraintRule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * ConstraintRule持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface ConstraintRuleMapper extends BaseMapper<ConstraintRule> {
}


````

---

## Backend/src/main/java/com/smartlab/management/mapper/constraint/ViolationLogMapper.java

````text
package com.smartlab.management.mapper.constraint;

import com.smartlab.management.entity.constraint.ViolationLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * ViolationLog持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface ViolationLogMapper extends BaseMapper<ViolationLog> {
}


````

---

## Backend/src/main/java/com/smartlab/management/mapper/resource/adapter/AdapterIndexMapper.java

````text
package com.smartlab.management.mapper.resource.adapter;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * AdapterIndex持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface AdapterIndexMapper extends BaseMapper<AdapterIndex> {
}

````

---

## Backend/src/main/java/com/smartlab/management/mapper/resource/data/DataIndexMapper.java

````text
package com.smartlab.management.mapper.resource.data;

import com.smartlab.management.entity.resource.data.DataIndex;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * DataIndex持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface DataIndexMapper extends BaseMapper<DataIndex> {
}


````

---

## Backend/src/main/java/com/smartlab/management/mapper/resource/data/DataTemplateDetailMapper.java

````text
package com.smartlab.management.mapper.resource.data;

import com.smartlab.management.entity.resource.data.DataTemplateDetail;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * DataTemplateDetail持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface DataTemplateDetailMapper extends BaseMapper<DataTemplateDetail> {
}


````

---

## Backend/src/main/java/com/smartlab/management/mapper/resource/data/DataTemplateMainMapper.java

````text
package com.smartlab.management.mapper.resource.data;

import com.smartlab.management.entity.resource.data.DataTemplateMain;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * DataTemplateMain持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface DataTemplateMainMapper extends BaseMapper<DataTemplateMain> {
}


````

---

## Backend/src/main/java/com/smartlab/management/mapper/resource/device/DeviceCategoryMapper.java

````text
package com.smartlab.management.mapper.resource.device;

import com.smartlab.management.entity.resource.device.DeviceCategory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * DeviceCategory持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface DeviceCategoryMapper extends BaseMapper<DeviceCategory> {
}


````

---

## Backend/src/main/java/com/smartlab/management/mapper/resource/device/DeviceComponentsMapper.java

````text
package com.smartlab.management.mapper.resource.device;

import com.smartlab.management.entity.resource.device.DeviceComponents;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * DeviceComponents持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface DeviceComponentsMapper extends BaseMapper<DeviceComponents> {
}


````

---

## Backend/src/main/java/com/smartlab/management/mapper/resource/device/DeviceInstancesMapper.java

````text
package com.smartlab.management.mapper.resource.device;

import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * DeviceInstances持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface DeviceInstancesMapper extends BaseMapper<DeviceInstances> {
}


````

---

## Backend/src/main/java/com/smartlab/management/mapper/resource/device/DeviceModelsMapper.java

````text
package com.smartlab.management.mapper.resource.device;

import com.smartlab.management.entity.resource.device.DeviceModels;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * DeviceModels持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface DeviceModelsMapper extends BaseMapper<DeviceModels> {
}


````

---

## Backend/src/main/java/com/smartlab/management/mapper/resource/device/DeviceTwinStatesMapper.java

````text
package com.smartlab.management.mapper.resource.device;

import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * DeviceTwinStates持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface DeviceTwinStatesMapper extends BaseMapper<DeviceTwinStates> {
}


````

---

## Backend/src/main/java/com/smartlab/management/mapper/resource/device/PropertyTypeMapper.java

````text
package com.smartlab.management.mapper.resource.device;

import com.smartlab.management.entity.resource.device.PropertyType;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * PropertyType持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface PropertyTypeMapper extends BaseMapper<PropertyType> {
}


````

---

## Backend/src/main/java/com/smartlab/management/mapper/resource/device/ResourceStructureMapper.java

````text
package com.smartlab.management.mapper.resource.device;

import com.smartlab.management.entity.resource.device.ResourceStructure;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * ResourceStructure持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface ResourceStructureMapper extends BaseMapper<ResourceStructure> {
}


````

---

## Backend/src/main/java/com/smartlab/management/mapper/resource/scene/SceneDetailMapper.java

````text
package com.smartlab.management.mapper.resource.scene;

import com.smartlab.management.entity.resource.scene.SceneDetail;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * SceneDetail持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface SceneDetailMapper extends BaseMapper<SceneDetail> {
}


````

---

## Backend/src/main/java/com/smartlab/management/mapper/resource/scene/SceneMainMapper.java

````text
package com.smartlab.management.mapper.resource.scene;

import com.smartlab.management.entity.resource.scene.SceneMain;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * SceneMain持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface SceneMainMapper extends BaseMapper<SceneMain> {
}


````

---

## Backend/src/main/java/com/smartlab/management/mapper/user/PermissionInfoMapper.java

````text
package com.smartlab.management.mapper.user;

import com.smartlab.management.entity.user.PermissionInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * PermissionInfo持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface PermissionInfoMapper extends BaseMapper<PermissionInfo> {
}


````

---

## Backend/src/main/java/com/smartlab/management/mapper/user/UserInfoMapper.java

````text
package com.smartlab.management.mapper.user;

import com.smartlab.management.entity.user.UserInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * UserInfo持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface UserInfoMapper extends BaseMapper<UserInfo> {
}


````

---

## Backend/src/main/java/com/smartlab/management/mapper/workflow/FlowModelsMapper.java

````text
package com.smartlab.management.mapper.workflow;

import com.smartlab.management.entity.workflow.FlowModels;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * FlowModels持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface FlowModelsMapper extends BaseMapper<FlowModels> {
}


````

---

## Backend/src/main/java/com/smartlab/management/mapper/workflow/FlowNodeMapper.java

````text
package com.smartlab.management.mapper.workflow;

import com.smartlab.management.entity.workflow.FlowNode;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * FlowNode持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface FlowNodeMapper extends BaseMapper<FlowNode> {
}


````

---

## Backend/src/main/java/com/smartlab/management/mapper/workflow/StepLogMapper.java

````text
package com.smartlab.management.mapper.workflow;

import com.smartlab.management.entity.workflow.StepLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * StepLog持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface StepLogMapper extends BaseMapper<StepLog> {
}


````

---

## Backend/src/main/java/com/smartlab/management/mapper/workflow/TaskMapper.java

````text
package com.smartlab.management.mapper.workflow;

import com.smartlab.management.entity.workflow.Task;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * Task持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface TaskMapper extends BaseMapper<Task> {
}


````

---

## Backend/src/main/java/com/smartlab/management/mapper/workflow/TaskStepMapper.java

````text
package com.smartlab.management.mapper.workflow;

import com.smartlab.management.entity.workflow.TaskStep;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * TaskStep持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface TaskStepMapper extends BaseMapper<TaskStep> {
}


````

---

## Backend/src/main/java/com/smartlab/management/service/protocol/AdapterPayloadMapperService.java

````text
package com.smartlab.management.service.protocol;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.StateMachineEngine;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.adapter.AdapterManifestService;
import com.smartlab.management.dto.resource.adapter.AdapterRouteDTO;
import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.management.mapper.resource.device.DeviceTwinStatesMapper;
import com.smartlab.management.service.db.resource.adapter.AdapterIndexService;
import com.smartlab.management.service.db.resource.data.DataIndexService;
import com.smartlab.management.service.db.resource.data.DataRecordService;
import com.smartlab.management.entity.resource.data.DataIndex;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
/**
 * 适配器物理报文与物模型逻辑属性双向翻译映射核心转换层服务（原DeviceProtocolMapperService）。
 */
public class AdapterPayloadMapperService {

    private final DeviceInstancesMapper deviceInstancesMapper;
    private final DeviceModelsMapper deviceModelsMapper;
    private final DeviceTwinStatesMapper twinStatesMapper;
    private final AdapterIndexService adapterIndexService;
    private final AdapterManifestService adapterManifestService;
    private final StateMachineEngine stateMachineEngine;
    private final DataIndexService dataIndexService;
    private final DataRecordService dataRecordService;
    private final ConcurrentHashMap<String, AdapterRouteDTO> adapterRouteTable = new ConcurrentHashMap<>();

    public AdapterPayloadMapperService(DeviceInstancesMapper deviceInstancesMapper,
            DeviceModelsMapper deviceModelsMapper,
            DeviceTwinStatesMapper twinStatesMapper,
            AdapterIndexService adapterIndexService,
            AdapterManifestService adapterManifestService,
            DataIndexService dataIndexService,
            DataRecordService dataRecordService,
            @Lazy StateMachineEngine stateMachineEngine) {
        this.deviceInstancesMapper = deviceInstancesMapper;
        this.deviceModelsMapper = deviceModelsMapper;
        this.twinStatesMapper = twinStatesMapper;
        this.adapterIndexService = adapterIndexService;
        this.adapterManifestService = adapterManifestService;
        this.dataIndexService = dataIndexService;
        this.dataRecordService = dataRecordService;
        this.stateMachineEngine = stateMachineEngine;
    }

    public Map<String, AdapterRouteDTO> refreshAdapterRouteTable() {
        adapterRouteTable.clear();
        List<DeviceInstances> instances = deviceInstancesMapper.selectList(Wrappers.<DeviceInstances>lambdaQuery()
                .isNotNull(DeviceInstances::getBoundAdapterName)
                .isNotNull(DeviceInstances::getBoundDevicePoint)
                .orderByAsc(DeviceInstances::getId));
        for (DeviceInstances instance : instances) {
            adapterRouteTable.put(routeKey(instance.getBoundAdapterName(), instance.getBoundDevicePoint()),
                    toRoute(instance));
        }
        return getAdapterRouteTable();
    }

    public Map<String, AdapterRouteDTO> getAdapterRouteTable() {
        return Collections.unmodifiableMap(new HashMap<>(adapterRouteTable));
    }

    public AdapterRouteDTO resolveAdapterRoute(String adapterName, String devicePoint) {
        if (adapterRouteTable.isEmpty()) {
            refreshAdapterRouteTable();
        }
        return adapterRouteTable.get(routeKey(adapterName, devicePoint));
    }

    public ObjectNode buildCommandMessage(String id, String commandId, Map<String, Object> parameters) {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException("commandId 不能为空");
        }
        DeviceInstances instance = deviceInstancesMapper.selectById(parseId(id));
        if (instance == null) {
            throw new IllegalArgumentException("设备实例不存在");
        }
        if (!hasAdapterBinding(instance)) {
            throw new IllegalStateException("设备实例未绑定 Adapter 设备点");
        }
        DeviceModels model = deviceModelsMapper.selectById(instance.getDeviceModelId());
        if (model == null) {
            throw new IllegalArgumentException("设备模型不存在");
        }
        JsonNode command = resolveCommandDefinition(model, commandId);
        String commandName = command.path("commandName").asText(command.path("name").asText(commandId));
        ObjectNode mappedParameters = buildOutgoingParameters(model, commandName, commandId,
                parameters == null ? Map.of() : parameters);

        ObjectNode payload = JsonNodeSupport.objectNode();
        payload.put("messageId", UUID.randomUUID().toString());
        payload.put("adapterName", instance.getBoundAdapterName());
        payload.put("devicePoint", instance.getBoundDevicePoint());
        payload.put("commandName", commandName);
        payload.set("parameters", mappedParameters);
        payload.put("timestamp", Instant.now().toEpochMilli());

        ObjectNode result = JsonNodeSupport.objectNode();
        result.put("topic", commandTopic(instance.getBoundAdapterName(), instance.getBoundDevicePoint()));
        result.set("payload", payload);
        return result;
    }

    public ObjectNode buildAdapterBinding(Long modelId, String adapterName, String devicePoint) {
        if (modelId == null) {
            throw new IllegalArgumentException("设备实例缺少 deviceModelId");
        }
        AdapterIndex adapter = adapterIndexService.requireAdapter(adapterName);
        JsonNode manifest = adapter.getParsedConfig();
        JsonNode point = adapterManifestService.findDevicePoint(manifest, devicePoint);
        if (point == null) {
            throw new IllegalArgumentException("Adapter 设备点不存在: " + devicePoint);
        }
        String templateName = point.path("templateName").asText();
        JsonNode template = adapterManifestService.findTemplate(manifest, templateName);
        if (template == null) {
            throw new IllegalArgumentException("Adapter 模板不存在: " + templateName);
        }
        DeviceModels model = deviceModelsMapper.selectById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("设备模型不存在");
        }
        JsonNode contract = model.getAdapterContract();
        String modelTemplateName = contract == null ? null : contract.path("config").path("templateName").asText(null);
        if (modelTemplateName != null && !modelTemplateName.isBlank() && !modelTemplateName.equals(templateName)) {
            throw new IllegalArgumentException("设备模型绑定的是模板 " + modelTemplateName + "，不能绑定设备点模板 " + templateName);
        }
        String pointCategoryName = point.path("categoryName").asText(null);
        String modelCategoryName = contract == null ? null : contract.path("config").path("categoryName").asText(null);
        if (modelCategoryName != null && !modelCategoryName.isBlank()
                && pointCategoryName != null && !pointCategoryName.isBlank()
                && !modelCategoryName.equals(pointCategoryName)) {
            throw new IllegalArgumentException("设备模型绑定的是 Adapter 类别 " + modelCategoryName + "，不能绑定设备点类别 " + pointCategoryName);
        }

        ObjectNode binding = JsonNodeSupport.objectNode();
        binding.put("adapterName", adapterName);
        binding.put("devicePoint", devicePoint);
        binding.put("templateName", templateName);
        if (pointCategoryName != null && !pointCategoryName.isBlank()) {
            binding.put("categoryName", pointCategoryName);
        }        binding.set("attributeMapping", point.path("attributeMapping"));
        ObjectNode topics = binding.putObject("topics");
        topics.put("command", commandTopic(adapterName, devicePoint));
        topics.put("telemetry", telemetryTopic(adapterName, devicePoint));
        topics.put("event", eventTopic(adapterName, devicePoint));

        ObjectNode modelToRaw = binding.putObject("modelToRawAttribute");
        ObjectNode rawToModel = binding.putObject("rawToModelAttribute");
        ArrayNode resolved = binding.putArray("resolvedAttributes");
        Map<String, String> modelAttrTypes = modelAttributeTypes(model.getAttributes());
        Map<String, String> adapterAttrTypes = adapterAttributeTypes(contract);
        for (JsonNode mapping : iterable(
                contract == null ? null : contract.path("telemetry").path("attributesMapping"))) {
            String modelAttr = mapping.path("modelAttributeName").asText("");
            String templateAttr = mapping.path("adapterAttrName").asText("");
            String rawAttr = point.path("attributeMapping").path(templateAttr).asText("");
            if (modelAttr.isBlank() || templateAttr.isBlank() || rawAttr.isBlank()) {
                continue;
            }
            String modelType = modelAttrTypes.get(modelAttr);
            String adapterType = adapterAttrTypes.get(templateAttr);
            if (modelType != null && adapterType != null && !modelType.equals(adapterType)) {
                throw new IllegalArgumentException("实例属性映射类型不一致: " + modelAttr + " -> " + templateAttr);
            }
            modelToRaw.put(modelAttr, rawAttr);
            rawToModel.put(rawAttr, modelAttr);
            ObjectNode row = resolved.addObject();
            row.put("modelAttributeName", modelAttr);
            row.put("templateAttributeName", templateAttr);
            row.put("rawAttributeName", rawAttr);
            row.put("dataType", modelType == null ? Objects.toString(adapterType, "STRING") : modelType);
        }
        return binding;
    }

    public void applyTelemetry(String adapterName, String devicePoint, JsonNode message) {
        AdapterRouteDTO route = resolveAdapterRoute(adapterName, devicePoint);
        if (route == null) {
            return;
        }
        JsonNode data = message == null ? null : message.path("data");
        if (data == null || !data.isObject()) {
            return;
        }
        Map<String, String> rawToModel = route.getRawToModelMap();
        if (rawToModel == null || rawToModel.isEmpty()) {
            return;
        }
        Long instanceId = route.getDeviceInstanceId();
        DeviceTwinStates state = getOrCreateTwinState(instanceId);
        ObjectNode current = state.getCurrentAttr() != null && state.getCurrentAttr().isObject()
                ? (ObjectNode) state.getCurrentAttr().deepCopy()
                : JsonNodeSupport.objectNode();
        data.fields().forEachRemaining(entry -> {
            String modelAttr = rawToModel.get(entry.getKey());
            if (modelAttr != null && !modelAttr.isBlank()) {
                current.set(modelAttr, entry.getValue());
            }
        });
        state.setCurrentAttr(current);
        state.setOnlineStatus("ONLINE");
        state.setLastOnlineTime(OffsetDateTime.now());
        state.setUpdateTime(OffsetDateTime.now());
        twinStatesMapper.updateById(state);

        try {
            Map<String, Object> recordMap = new HashMap<>();
            current.fields().forEachRemaining(e -> {
                JsonNode val = e.getValue();
                if (val.isNumber()) {
                    recordMap.put(e.getKey(), val.numberValue());
                } else if (val.isBoolean()) {
                    recordMap.put(e.getKey(), val.booleanValue());
                } else {
                    recordMap.put(e.getKey(), val.asText());
                }
            });
            List<DataIndex> dataIndexes = dataIndexService.listByDeviceInstance(instanceId);
            if (dataIndexes != null) {
                for (DataIndex index : dataIndexes) {
                    try {
                        dataRecordService.appendRecord(index.getId(), recordMap);
                    } catch (Exception e) {
                        // ignore insertion failures (e.g. strict schema constraints)
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void applyAdapterEvent(String adapterName, String devicePoint, JsonNode message) {
        AdapterRouteDTO route = resolveAdapterRoute(adapterName, devicePoint);
        if (route == null || message == null) {
            return;
        }
        String eventName = message.path("eventName").asText("");
        if (eventName.isBlank()) {
            return;
        }
        stateMachineEngine.dispatchAdapterEvent(route.getDeviceInstanceId(), eventName, message);
    }

    private ObjectNode buildOutgoingParameters(DeviceModels model, String commandName, String commandId,
            Map<String, Object> parameters) {
        JsonNode capability = findCapability(model.getCapabilities(), commandId);
        if (capability != null && !capability.path("adapterCommandName").asText("").equals(commandName)) {
            capability = null;
        }
        JsonNode command = findCommand(model.getAdapterContract(), commandName);
        if (command == null) {
            throw new IllegalArgumentException("Adapter 命令不存在: " + commandName);
        }
        ObjectNode result = JsonNodeSupport.objectNode();
        if (capability != null) {
            for (JsonNode mapping : iterable(capability.path("parameterMapping"))) {
                String commandParamName = mapping.path("commandParamName").asText("");
                JsonNode commandParam = findCommandParam(command, commandParamName);
                if (commandParam == null || commandParam.path("hidden").asBoolean(false)) {
                    continue;
                }
                if (mapping.path("isFixedValue").asBoolean(false)) {
                    result.set(commandParamName, mapping.path("fixedValue"));
                } else {
                    String capabilityParamName = mapping.path("capabilityParamName").asText("");
                    if (!parameters.containsKey(capabilityParamName)) {
                        throw new IllegalArgumentException("缺少操作参数: " + capabilityParamName);
                    }
                    result.set(commandParamName, JsonNodeSupport.toNode(parameters.get(capabilityParamName)));
                }
            }
            return result;
        }

        for (JsonNode param : iterable(command.path("commandParameters"))) {
            String paramName = param.path("paramName").asText("");
            if (paramName.isBlank() || param.path("hidden").asBoolean(false)) {
                continue;
            }
            if (!parameters.containsKey(paramName)) {
                throw new IllegalArgumentException("缺少命令参数: " + paramName);
            }
            result.set(paramName, JsonNodeSupport.toNode(parameters.get(paramName)));
        }
        return result;
    }

    private JsonNode resolveCommandDefinition(DeviceModels model, String commandId) {
        JsonNode capability = findCapability(model.getCapabilities(), commandId);
        String commandName = capability == null ? commandId : capability.path("adapterCommandName").asText(commandId);
        JsonNode command = findCommand(model.getAdapterContract(), commandName);
        if (command == null) {
            throw new IllegalArgumentException("Adapter 命令不存在: " + commandName);
        }
        return command;
    }

    private JsonNode findCapability(JsonNode capabilities, String id) {
        if (id == null) {
            return null;
        }
        for (JsonNode capability : iterable(capabilities)) {
            if (id.equals(capability.path("name").asText()) || id.equals(capability.path("capabilityId").asText())) {
                return capability;
            }
        }
        return null;
    }

    private JsonNode findCommand(JsonNode adapterContract, String commandName) {
        if (adapterContract == null || commandName == null) {
            return null;
        }
        for (JsonNode command : iterable(adapterContract.path("commands"))) {
            String current = command.path("commandName").asText(command.path("name").asText(""));
            if (commandName.equals(current)) {
                return command;
            }
        }
        return null;
    }

    private JsonNode findCommandParam(JsonNode command, String paramName) {
        for (JsonNode param : iterable(command.path("commandParameters"))) {
            if (paramName.equals(param.path("paramName").asText(param.path("name").asText("")))) {
                return param;
            }
        }
        return null;
    }

    private Map<String, String> modelAttributeTypes(JsonNode attributes) {
        Map<String, String> result = new HashMap<>();
        for (JsonNode attr : iterable(attributes)) {
            String name = attr.path("name").asText("");
            if (!name.isBlank()) {
                result.put(name, adapterManifestService.normalizeDataType(attr.path("dataType").asText("STRING")));
            }
        }
        return result;
    }

    private Map<String, String> adapterAttributeTypes(JsonNode contract) {
        Map<String, String> result = new HashMap<>();
        for (JsonNode attr : iterable(contract == null ? null : contract.path("telemetry").path("adapterAttributes"))) {
            String name = attr.path("name").asText("");
            if (!name.isBlank()) {
                result.put(name, adapterManifestService.normalizeDataType(attr.path("dataType").asText("STRING")));
            }
        }
        return result;
    }

    private DeviceTwinStates getOrCreateTwinState(Long instanceId) {
        DeviceTwinStates state = twinStatesMapper
                .selectOne(Wrappers.<DeviceTwinStates>lambdaQuery().eq(DeviceTwinStates::getInstanceId, instanceId));
        if (state != null) {
            return state;
        }
        DeviceTwinStates created = new DeviceTwinStates();
        created.setInstanceId(instanceId);
        created.setCurrentOpState("IDLE");
        created.setCurrentCmdState("IDLE");
        created.setOnlineStatus("UNKNOWN");
        created.setCurrentAttr(JsonNodeSupport.objectNode());
        created.setUpdateTime(OffsetDateTime.now());
        twinStatesMapper.insert(created);
        return twinStatesMapper
                .selectOne(Wrappers.<DeviceTwinStates>lambdaQuery().eq(DeviceTwinStates::getInstanceId, instanceId));
    }

    private AdapterRouteDTO toRoute(DeviceInstances instance) {
        AdapterRouteDTO route = new AdapterRouteDTO();
        route.setDeviceInstanceId(instance.getId());
        route.setDeviceModelId(instance.getDeviceModelId());
        route.setInstanceName(instance.getInstanceName());
        route.setBoundAdapterName(instance.getBoundAdapterName());
        route.setBoundDevicePoint(instance.getBoundDevicePoint());
        JsonNode config = instance.getInstanceConfig();
        if (config != null && config.has("adapterBinding")) {
            JsonNode binding = config.path("adapterBinding");
            route.setTemplateName(binding.path("templateName").asText(null));
            route.setCategoryName(binding.path("categoryName").asText(null));
            route.setResolvedAttributes(binding.path("resolvedAttributes"));

            JsonNode rawToModel = binding.path("rawToModelAttribute");
            if (rawToModel != null && rawToModel.isObject()) {
                Map<String, String> map = new HashMap<>();
                rawToModel.fields().forEachRemaining(entry -> {
                    map.put(entry.getKey(), entry.getValue().asText());
                });
                route.setRawToModelMap(map);
            }
        }
        return route;
    }

    private boolean hasAdapterBinding(DeviceInstances instance) {
        return instance != null
                && instance.getBoundAdapterName() != null && !instance.getBoundAdapterName().isBlank()
                && instance.getBoundDevicePoint() != null && !instance.getBoundDevicePoint().isBlank();
    }

    private Long parseId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return Long.valueOf(id);
    }

    private String routeKey(String adapterName, String devicePoint) {
        if (adapterName == null || adapterName.isBlank() || devicePoint == null || devicePoint.isBlank()) {
            throw new IllegalArgumentException("Adapter 标识和设备点字段不能为空");
        }
        return adapterName.trim() + "::" + devicePoint.trim();
    }

    private String commandTopic(String adapterName, String devicePoint) {
        return "smartlab/adapter/" + adapterName + "/" + devicePoint + "/command";
    }

    private String telemetryTopic(String adapterName, String devicePoint) {
        return "smartlab/adapter/" + adapterName + "/" + devicePoint + "/telemetry";
    }

    private String eventTopic(String adapterName, String devicePoint) {
        return "smartlab/adapter/" + adapterName + "/" + devicePoint + "/event";
    }

    private Iterable<JsonNode> iterable(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || !node.isArray()) {
            return JsonNodeSupport.arrayNode();
        }
        return node;
    }
}

````

---

## Backend/src/main/java/com/smartlab/SmartLabApplication.java

````text
package com.smartlab;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan(basePackages = "com.smartlab", annotationClass = Mapper.class)
/**
 * SmartLabApplication 领域实体/配置模型类。
 */
public class SmartLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartLabApplication.class, args);
    }
}


````

---

## Backend/src/main/resources/application.yml

````text
server:
  port: 8080

spring:
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://101.42.50.179:5432/lab
    username: user
    password: 123456
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      idle-timeout: 30000
      connection-timeout: 20000

  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: GMT+8

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: auto

smartlab:
  auth:
    jwt-secret: ${SMARTLAB_JWT_SECRET:change_me_to_a_256_bit_secret_for_local_dev}
    expiration-ms: ${SMARTLAB_JWT_EXPIRATION_MS:86400000}

# MQTT Broker Config
mqtt:
  broker: tcp://127.0.0.1:1883
  client-id: smartlab_backend_2.0
  username: lab_system
  password: 123456
  auto-start: true
  register-topic: smartlab/adapter/register
  adapter-heartbeat-topic-pattern: smartlab/adapter/{adapterName}/heartbeat
  device-telemetry-topic-pattern: smartlab/adapter/{adapterName}/{devicePoint}/telemetry
  device-event-topic-pattern: smartlab/adapter/{adapterName}/{devicePoint}/event
  reconnect-delay-ms: 15000

logging:
  level:
    root: INFO
    com.smartlab: DEBUG

````

---

## Backend/src/main/resources/samples/adapter-heat-pressure-test.json

````text
{
  "specVersion": "smartlab.adapter.config.v1",
  "adapterName": "TestHeatPressureAdapter-01",
  "rawConfigFormat": "JSON",
  "deviceTemplates": [
    {
      "templateName": "ReactorUnit",
      "description": "测试用反应单元模板",
      "attributes": [
        {
          "name": "temperature",
          "dataType": "DOUBLE",
          "description": "当前温度"
        },
        {
          "name": "pressure",
          "dataType": "DOUBLE",
          "description": "当前压力"
        }
      ],
      "commands": [
        {
          "name": "heat",
          "description": "加热到目标温度",
          "parameters": [
            {
              "name": "targetTemperature",
              "dataType": "DOUBLE",
              "description": "目标温度"
            },
            {
              "name": "durationSec",
              "dataType": "INTEGER",
              "description": "加热持续时间，秒"
            },
            {
              "name": "index",
              "dataType": "INTEGER",
              "description": "设备点内部编号",
              "hidden": true,
              "sourceField": "index"
            }
          ]
        },
        {
          "name": "pressurize",
          "description": "加压到目标压力",
          "parameters": [
            {
              "name": "targetPressure",
              "dataType": "DOUBLE",
              "description": "目标压力"
            },
            {
              "name": "durationSec",
              "dataType": "INTEGER",
              "description": "加压持续时间，秒"
            },
            {
              "name": "index",
              "dataType": "INTEGER",
              "description": "设备点内部编号",
              "hidden": true,
              "sourceField": "index"
            }
          ]
        }
      ],
      "events": {
        "cmdEvents": [
          {
            "name": "COMMAND_RECEIVED",
            "description": "指令已接收"
          },
          {
            "name": "COMMAND_RUNNING",
            "description": "指令执行中"
          },
          {
            "name": "COMMAND_COMPLETED",
            "description": "指令执行完成"
          },
          {
            "name": "COMMAND_FAILED",
            "description": "指令执行失败"
          },
          {
            "name": "COMMAND_TIMEOUT",
            "description": "指令执行超时"
          },
          {
            "name": "COMMAND_CANCELLED",
            "description": "指令已取消"
          }
        ],
        "opEvents": [
          {
            "name": "HEAT_STARTED",
            "description": "开始加热"
          },
          {
            "name": "TARGET_TEMPERATURE_REACHED",
            "description": "达到目标温度"
          },
          {
            "name": "PRESSURIZE_STARTED",
            "description": "开始加压"
          },
          {
            "name": "TARGET_PRESSURE_REACHED",
            "description": "达到目标压力"
          },
          {
            "name": "PRESSURE_ALARM",
            "description": "压力报警"
          }
        ]
      }
    }
  ],
  "devicePoints": [
    {
      "devicePoint": "Reactor1",
      "templateName": "ReactorUnit",
      "description": "1号测试反应单元",
      "index": 1,
      "attributeMapping": {
        "temperature": "R1_TEMP",
        "pressure": "R1_PRESS"
      }
    },
    {
      "devicePoint": "Reactor2",
      "templateName": "ReactorUnit",
      "description": "2号测试反应单元",
      "index": 2,
      "attributeMapping": {
        "temperature": "R2_TEMP",
        "pressure": "R2_PRESS"
      }
    }
  ]
}
````

---

## Backend/src/main/resources/schemas/constraint-model.json

````text
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "ConstraintModel",
  "description": "需求约束模型：定义约束数据的统一格式。全局约束和任务级约束均遵循此格式。",
  "type": "object",
  "properties": {
    "observableObjects": {
      "type": "array",
      "description": "可观测对象空间",
      "items": {
        "type": "object",
        "properties": {
          "name": { "type": "string", "description": "可观测对象唯一标识名" },
          "sourceType": { 
            "type": "string", 
            "enum": ["DEVICE_ATTRIBUTE", "DEVICE_OPERATION_STATE", "DEVICE_COMMAND_LIFECYCLE", "NODE_LIFECYCLE_STATE"] 
          },
          "dataType": { "$ref": "protocol-dict.json#/definitions/DataType" }
        },
        "required": ["name", "sourceType", "dataType"],
        "allOf": [
          {
            "if": { "properties": { "sourceType": { "const": "DEVICE_ATTRIBUTE" } } },
            "then": {
              "properties": {
                "deviceModelId": { "type": "integer", "description": "设备模型ID" },
                "deviceInstanceId": { "type": "integer", "description": "设备实例ID（可选，不填则监控该型号所有实例）" },
                "targetName": { "type": "string", "description": "对应 attributeName" }
              },
              "required": ["deviceModelId", "targetName"]
            }
          },
          {
            "if": { "properties": { "sourceType": { "const": "DEVICE_OPERATION_STATE" } } },
            "then": {
              "properties": {
                "deviceModelId": { "type": "integer" },
                "deviceInstanceId": { "type": "integer", "description": "可选，不填则监控该型号所有实例" }
              },
              "required": ["deviceModelId"]
            }
          },
          {
            "if": { "properties": { "sourceType": { "const": "DEVICE_COMMAND_LIFECYCLE" } } },
            "then": {
              "properties": {
                "deviceModelId": { "type": "integer" },
                "deviceInstanceId": { "type": "integer", "description": "可选，不填则监控该型号所有实例" }
              },
              "required": ["deviceModelId"]
            }
          },
          {
            "if": { "properties": { "sourceType": { "const": "NODE_LIFECYCLE_STATE" } } },
            "then": {
              "properties": {
                "workflowTemplateId": { "type": "integer" },
                "nodeName": { "type": "string" }
              },
              "required": ["workflowTemplateId", "nodeName"]
            }
          }
        ]
      }
    },
    "constraints": {
      "type": "array",
      "description": "约束规则集",
      "items": {
        "type": "object",
        "properties": {
          "name": { "type": "string", "description": "约束规则名" },
          "observedObjectName": { "type": "string", "description": "引用observableObjects中的name" },
          "operator": { "$ref": "protocol-dict.json#/definitions/ConstraintOperator" },
          "threshold": { "type": ["number", "string", "array"] },
          "violationActions": {
            "type": "array",
            "description": "违规处置动作列表，用户自由组合系统动作和设备动作。前端界面：用户点击'+添加动作'，可从系统动作（下拉框）或设备动作（选择设备→选择功能→配置参数）中选择。",
            "items": {
              "type": "object",
              "properties": {
                "actionType": { 
                  "type": "string", 
                  "enum": ["SYSTEM", "DEVICE_CAPABILITY"],
                  "description": "SYSTEM：系统级动作；DEVICE_CAPABILITY：触发设备能力"
                }
              },
              "required": ["actionType"],
              "allOf": [
                {
                  "if": { "properties": { "actionType": { "const": "SYSTEM" } } },
                  "then": {
                    "properties": {
                      "action": { "$ref": "protocol-dict.json#/definitions/SystemViolationAction" }
                    },
                    "required": ["action"]
                  }
                },
                {
                  "if": { "properties": { "actionType": { "const": "DEVICE_CAPABILITY" } } },
                  "then": {
                    "properties": {
                      "deviceInstanceId": { "type": "integer", "description": "目标设备实例ID" },
                      "capabilityName": { "type": "string", "description": "引用设备能力模型中的capability name" },
                      "parameters": { 
                        "type": "object", 
                        "description": "设备能力的参数，从设备能力模型中读取"
                      }
                    },
                    "required": ["deviceInstanceId", "capabilityName"]
                  }
                }
              ]
            },
            "minItems": 1
          }
        },
        "required": ["name", "observedObjectName", "operator", "threshold", "violationActions"]
      }
    }
  },
  "required": ["observableObjects", "constraints"]
}

````

---

## Backend/src/main/resources/schemas/device-capability-model.json

````text
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "DeviceCapabilityModel",
  "description": "设备能力模型：定义设备的属性空间、操作能力空间和约束限制空间。",
  "type": "object",
  "properties": {
    "metadata": {
      "type": "object",
      "properties": {
        "modelId": { "type": "integer", "description": "设备模型在数据库中的ID" },
        "modelName": { "type": "string", "description": "设备模型名称" },
        "deviceCategoryId": { "type": "integer", "description": "设备类别在数据库中的ID" }
      },
      "required": ["modelId", "modelName", "deviceCategoryId"]
    },
    "attributes": {
      "type": "array",
      "description": "设备属性空间",
      "items": {
        "type": "object",
        "properties": {
          "name": { "type": "string", "description": "属性唯一标识名" },
          "displayName": { "type": "string", "description": "前端UI渲染显示名" },
          "valueKind": { "type": "string", "enum": ["DISCRETE", "CONTINUOUS"] },
          "dataType": { "$ref": "protocol-dict.json#/definitions/DataType" },
          "unit": { "type": "string", "description": "物理单位" }
        },
        "required": ["name", "valueKind", "dataType"]
      }
    },
    "capabilities": {
      "type": "array",
      "description": "设备对外暴露的操作能力",
      "items": {
        "type": "object",
        "properties": {
          "name": { "type": "string", "description": "操作能力唯一标识名" },
          "adapterCommandName": { "type": "string", "description": "映射并指向adapterContract.commands中的commandName" },
          "displayName": { "type": "string", "example": "反应釜加热" },
          "parameters": {
            "type": "array",
            "description": "capability对外暴露的操作参数列表",
            "items": {
              "type": "object",
              "properties": {
                "name": { "type": "string", "description": "参数标识名" },
                "displayName": { "type": "string", "example": "目标温度" },
                "dataType": { "$ref": "protocol-dict.json#/definitions/DataType" }
              },
              "required": ["name", "displayName", "dataType"]
            }
          },
          "parameterMapping": {
            "type": "array",
            "description": "参数映射关系",
            "items": {
              "type": "object",
              "properties": {
                "commandParamName": { "type": "string", "description": "adapter命令的paramName" },
                "capabilityParamName": { "type": "string", "description": "capability的paramName" },
                "isFixedValue": { "type": "boolean", "default": false },
                "fixedValue": { "type": ["string", "number", "boolean"], "description": "固定参数的值" }
              },
              "required": ["commandParamName", "isFixedValue"]
            }
          }
        },
        "required": ["name", "adapterCommandName", "displayName", "parameters", "parameterMapping"]
      }
    },
    "adapterContract": {
      "type": "object",
      "description": "adapter适配契约",
      "properties": {
        "config": {
          "type": "object",
          "description": "Adapter通信配置。主题和消息外壳在protocol-dict中统一规定。此处声明协议类型、Adapter名称及设备类别名称。",
          "properties": {
            "protocol": {
              "type": "string",
              "enum": ["MQTT", "HTTP"],
              "description": "通信协议类型"
            },
           "adapterName": {
              "type": "string",
              "description": "Adapter名称"
            },
            "categoryName": {
              "type": "string",
              "description": "Adapter侧类别名称"
            }
          },
          "required": ["protocol", "adapterName", "categoryName"]
        },
        "commands": {
          "type": "array",
          "description": "adapter执行命令",
          "items": {
            "type": "object",
            "properties": {
              "commandName": { "type": "string", "description": "adapter命令唯一标识名" },
              "commandParameters": {
                "type": "array",
                "items": {
                  "type": "object",
                  "properties": {
                    "paramName": { "type": "string" },
                    "dataType": { "$ref": "protocol-dict.json#/definitions/DataType" }
                  },
                  "required": ["paramName", "dataType"]
                }
              }
            },
            "required": ["commandName", "commandParameters"]
          }
        },
        "telemetry": {
          "type": "object",
          "description": "adapter遥测数据上报",
          "properties": {
            "adapterAttributes": {
              "type": "array",
              "description": "声明Adapter能够上报的属性字段列表",
              "items": {
                "type": "object",
                "properties": {
                  "name": { "type": "string", "description": "Adapter上报data中的字段名" },
                  "dataType": { "$ref": "protocol-dict.json#/definitions/DataType" },
                  "description": { "type": "string" }
                },
                "required": ["name", "dataType"]
              }
            },
            "attributesMapping": {
              "type": "array",
              "description": "将Adapter上报字段映射到设备属性",
              "items": {
                "type": "object",
                "properties": {
                  "adapterAttrName": { "type": "string", "description": "引用adapterAttributes中的name" },
                  "modelAttributeName": { "type": "string", "description": "设备模型中对应的属性标识名" }
                },
                "required": ["adapterAttrName", "modelAttributeName"]
              }
            }
          },
          "required": ["adapterAttributes", "attributesMapping"]
        },
        "events": {
          "type": "object",
          "description": "adapter离散事件反馈",
          "properties": {
            "cmdEvents": {
              "type": "array",
              "description": "adapter命令执行生命周期事件",
              "items": {
                "type": "object",
                "properties": {
                  "eventName": { "type": "string", "description": "事件标识名" },
                  "description": { "type": "string", "description": "事件描述" }
                },
                "required": ["eventName"]
              }
            },
            "opEvents": {
              "type": "array",
              "description": "adapter功能状态事件",
              "items": {
                "type": "object",
                "properties": {
                  "eventName": { "type": "string", "description": "事件标识名" },
                  "description": { "type": "string", "description": "事件描述" }
                },
                "required": ["eventName"]
              }
            }
          },
          "required": ["cmdEvents", "opEvents"]
        }
      },
      "required": ["config", "commands", "telemetry", "events"]
    },
    "ports": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "portName": { "type": "string", "description": "端口标识名" },
          "direction": { "type": "string", "enum": ["IN", "OUT"] },
          "bindingAttrName": { "type": "string", "description": "绑定到attributes中的属性标识名" }
        },
        "required": ["portName", "direction", "bindingAttrName"]
      }
    },
    "intrinsicConstraints": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "objectAttributeName": { "type": "string", "description": "设备属性标识名" },
          "operator": { "$ref": "protocol-dict.json#/definitions/ConstraintOperator" },
          "boundaryValue": { "type": "number", "description": "阈值" },
          "violationStateName": { "type": "string", "description": "对应状态机中的状态名" }
        },
        "required": ["objectAttributeName", "operator", "boundaryValue", "violationStateName"]
      }
    }
  },
  "required": ["metadata", "attributes", "capabilities", "adapterContract", "ports", "intrinsicConstraints"]
}

````

---

## Backend/src/main/resources/schemas/device-state-machine-model.json

````text
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "DeviceStateMachineModel",
  "description": "设备状态机模型",
  "type": "object",
  "properties": {
    "deviceModelId": { "type": "integer", "description": "所属设备模型在数据库中的ID" },
    "interfaces": {
      "type": "array",
      "description": "状态机对外接口声明列表",
      "items": {
        "type": "object",
        "properties": {
          "name": { "type": "string", "example": "Interface_control_in" },
          "direction": { "type": "string", "enum": ["IN", "OUT"] },
          "interfaceType": { 
            "type": "string", 
            "enum": ["WORKFLOW", "STAT", "ADAPTER", "CONTROL", "CONSTRAINT"],
            "description": "接口类型"
          },
          "allowedSignals": {
            "type": "array",
            "description": "该接口允许通过的信号名列表。当interfaceType为ADAPTER时，此处的信号名必须与device-capability-model中adapterContract.events声明的eventName完全一致。",
            "items": { "type": "string" }
          }
        },
        "required": ["name", "direction", "interfaceType", "allowedSignals"]
      }
    },
    "opStateSpace": {
      "type": "object",
      "description": "设备功能状态空间",
      "properties": {
        "initialStateName": { "type": "string" },
        "states": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "stateName": { "type": "string" },
              "onEntry": {
                "type": "array",
                "items": { "$ref": "protocol-dict.json#/definitions/StateMachineAction" }
              }
            },
            "required": ["stateName"]
          }
        }
      },
      "required": ["initialStateName", "states"]
    },
    "cmdLifecycleSpace": {
      "type": "object",
      "description": "设备指令执行生命周期",
      "properties": {
        "initialStateName": { "type": "string" },
        "states": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "stateName": { "type": "string" },
              "onEntry": {
                "type": "array",
                "items": { "$ref": "protocol-dict.json#/definitions/StateMachineAction" }
              }
            },
            "required": ["stateName"]
          }
        }
      },
      "required": ["initialStateName", "states"]
    },
    "transitions": {
      "type": "array",
      "description": "全局状态转移规则，对应 STATE_TRANSITIONS 字段",
      "items": {
        "type": "object",
        "properties": {
          "description": { "type": "string" },
          "fromStateName": { "type": "string" },
          "toStateName": { "type": "string" },
          "trigger": {
            "type": "object",
            "description": "触发条件：某个接口收到了某个具体的信号",
            "properties": {
              "interfaceName": { "type": "string" },
              "signalName": { "type": "string", "description": "须在该接口的allowedSignals中" }
            },
            "required": ["interfaceName", "signalName"]
          },
          "actions": {
            "type": "array",
            "items": { "$ref": "protocol-dict.json#/definitions/StateMachineAction" }
          }
        },
        "required": ["fromStateName", "toStateName", "trigger"]
      }
    }
  },
  "required": ["deviceModelId", "interfaces", "opStateSpace", "cmdLifecycleSpace", "transitions"]
}

````

---

## Backend/src/main/resources/schemas/protocol-dict.json

````text
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "protocol-dict.json",
  "title": "ProtocolDictionary",
  "description": "全局协议层字典规范，定义系统中通用的数据类型、操作符、消息格式等基础元素，供各模块引用使用。",
  "definitions": {
    "DataType": {
      "type": "string",
      "enum": ["INTEGER", "DOUBLE", "STRING", "BOOLEAN", "JSON"],
      "description": "全局标准数据类型"
    },
    
    "ConstraintOperator": {
      "type": "string",
      "enum": ["GT", "LT", "GE", "LE", "EQ", "NE", "BETWEEN", "IN"],
      "description": "逻辑与数学比较符"
    },

    "StateMachineAction": {
      "type": "object",
      "description": "设备状态机动作对象，由后端解析和执行",
      "properties": {
        "actionName": {
          "type": "string",
          "enum": ["SEND", "ASSIGN"],
          "description": "设备状态机动作名称"
        },
        "payload": {
          "type": "object",
          "description": "动作参数载荷（例如：发送信号时的目标接口和数据，或者赋值操作时的目标变量和值）",
          "additionalProperties": true
        }
      },
      "required": ["actionName"]
    },

    "WorkflowNodeFunctionType": {
      "type": "string",
      "enum": ["START", "END", "BRANCH", "SYNC", "WAIT", "LOOP", "JOIN", "EXPRESSION"],
      "description": "工作流内置基础逻辑节点类型"
    },

    "WorkflowNodeAction": {
      "type": "object",
      "description": "工作流节点内部动作对象，由后端解析和执行",
      "properties": {
        "actionName": {
          "type": "string",
          "enum": ["EMIT_SIGNAL", "UPDATE_VARIABLE", "EXECUTE_LOGIC"],
          "description": "工作流节点内部动作名称"
        },
        "payload": {
          "type": "object",
          "description": "动作参数载荷",
          "additionalProperties": true
        }
      },
      "required": ["actionName"]
    },

    "SystemViolationAction": {
      "type": "string",
      "enum": ["HALT", "PAUSE", "ALERT", "LOG_ONLY"],
      "description": "系统级约束违规动作类型。具体参数由后端根据动作类型和约束上下文解析"
    },

     "SystemSignalFormat": {
      "type": "object",
      "description": "系统内部统一信号格式规范",
      "properties": {
        "signalName": { "type": "string", "description": "信号名" },
        "payload": { "type": "object", "additionalProperties": true, "description": "信号携带的数据内容" }
      },
      "required": ["signalName"]
    },

    "MqttTopicConvention": {
      "type": "object",
      "description": "MQTT主题命名规范",
      "properties": {
        "registerTopic":   { "const": "smartlab/adapter/register", "description": "系统订阅，adapter发布: 用于adapter注册" },
        "heartbeatTopic":  { "const": "smartlab/adapter/{adapterName}/heartbeat", "description": "系统订阅，adapter发布: 用于adapter心跳检测" },
        "commandTopic":    { "const": "smartlab/adapter/{adapterName}/{devicePoint}/command", "description": "adapter订阅，系统发布: 用于系统下发执行指令" },
        "telemetryTopic":  { "const": "smartlab/adapter/{adapterName}/{devicePoint}/telemetry", "description": "系统订阅，adapter发布: 用于adapter上行遥测数据" },
        "eventTopic":      { "const": "smartlab/adapter/{adapterName}/{devicePoint}/event", "description": "系统订阅，adapter发布: 用于adapter上行离散事件" }
      }
    },

    "AdapterRegisterRequest": {
      "type": "object",
      "description": "registerTopic消息格式规范",
      "properties": {
        "adapterName": { "type": "string", "description": "adapter全局唯一代号 (SN)" },
        "rawConfigFormat": { "type": "string", "enum": ["INI", "YAML", "JSON", "XML"], "default": "INI" },
        "rawConfigContent": { "type": "string", "description": "完整的adapter配置信息" },
        "timestamp": { "type": "integer" }
      },
      "required": ["adapterName", "rawConfigFormat", "rawConfigContent", "timestamp"]
    },
    
    "CommandMessageFormat": {
      "type": "object",
      "description": "commandTopic消息格式规范",
      "properties": {
        "messageId": { "type": "string", "description": "唯一ID" },
        "adapterName": { "type": "string", "description": "adapter唯一代号 (SN)" },
        "devicePoint": { "type": "string", "description": "设备点" },
        "commandName": { "type": "string", "description": "指令名" },
        "parameters": { "type": "object", "additionalProperties": true, "description": "指令对应的参数键值对" },
        "timestamp": { "type": "integer" }
      },
      "required": ["messageId", "adapterName", "devicePoint", "commandName", "parameters", "timestamp"]
    },

    "TelemetryMessageFormat": {
      "type": "object",
      "description": "telemetryTopic消息格式规范",
      "properties": {
        "timestamp": { "type": "integer" },
        "adapterName": { "type": "string", "description": "adapter唯一代号 (SN)" },
        "devicePoint": { "type": "string", "description": "设备点" },
        "data": { "type": "object", "additionalProperties": true, "description": "属性键值对" }
      },
      "required": ["timestamp", "adapterName", "devicePoint", "data"]
    },

    "EventMessageFormat": {
      "type": "object",
      "description": "eventTopic消息格式规范",
      "properties": {
        "timestamp": { "type": "integer" },
        "adapterName": { "type": "string", "description": "adapter唯一代号 (SN)" },
        "devicePoint": { "type": "string", "description": "设备点" },
        "eventName": { "type": "string", "description": "事件名称代号" },
        "payload": { "type": "object", "additionalProperties": true, "description": "事件发生时携带的具体参数" }
      },
      "required": ["timestamp", "adapterName", "devicePoint", "eventName"]
    },

    "AdapterHeartbeat": {
      "type": "object",
      "description": "heartbeatTopic消息格式规范",
      "properties": {
        "status": { "type": "string", "enum": ["ALIVE", "DEGRADED", "SHUTTING_DOWN"] },
        "timestamp": { "type": "integer" }
      },
      "required": ["status", "timestamp"]
    }
  }
}

````

---

## Backend/src/main/resources/schemas/resource-connection-graph.json

````text
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "ResourceConnectionGraph",
  "description": "资源连接图：定义了设备实例之间的连接关系，用于描述系统中资源之间的数据流向。",
  "type": "object",
  "properties": {
    "connections": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "name": { "type": "string" },
          "connectionType": { "type": "string", "enum": ["DATA_CHANNEL"] },
          "source": { 
            "type": "object",
            "properties": { "deviceInstanceId": {"type":"integer"}, "devicePortName": {"type":"string"} },
            "required": ["deviceInstanceId", "devicePortName"]
          },
          "target": { 
            "type": "object",
            "properties": { "deviceInstanceId": {"type":"integer"}, "devicePortName": {"type":"string"} },
            "required": ["deviceInstanceId", "devicePortName"]
          }
        },
        "required": ["name", "connectionType", "source", "target"]
      }
    }
  },
  "required": ["connections"]
}

````

---

## Backend/src/main/resources/schemas/workflow-model.json

````text
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "DynamicWorkflowModel",
  "description": "工作流模型：节点融合了设备能力模型+状态机的接口/触发器/动作机制。触发器实时监测条件，条件满足即触发绑定的动作。",
  "type": "object",
  "properties": {
    "workflowTemplateId": { "type": "integer", "description": "工作流模板在数据库中的ID" },
    "name": { "type": "string", "description": "工作流模板名称" },
    "description": { "type": "string", "description": "工作流模板描述" },
    "nodes": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "name": { "type": "string", "description": "节点标识名" },
          "nodeType": { 
            "type": "string", 
            "enum": ["DEVICE_CAPABILITY_NODE", "FUNCTIONAL_NODE", "SUB_FLOW_NODE"] 
          },
          "capability": {
            "type": "object",
            "description": "节点能力定义"
          },
          "internalVariables": {
            "type": "array",
            "description": "节点内部变量空间",
            "items": {
              "type": "object",
              "properties": {
                "name": { "type": "string", "description": "内部变量名" },
                "dataType": { "$ref": "protocol-dict.json#/definitions/DataType" },
                "attributesMapping": {
                  "type": "string",
                  "description": "映射到设备属性（可选）"
                }
              },
              "required": ["name", "dataType"]
            }
          },
          "lifecycle": {
            "type": "object",
            "description": "节点生命周期",
            "properties": {
              "initialStateName": { "type": "string" },
              "states": { 
                "type": "array", 
                "items": { "type": "string" }
              },
              "transitions": {
                "type": "array",
                "items": {
                  "type": "object",
                  "properties": {
                    "description": { "type": "string" },
                    "fromStateName": { "type": "string" },
                    "toStateName": { "type": "string" }
                  },
                  "required": ["fromStateName", "toStateName"]
                }
              }
            },
            "required": ["initialStateName", "states", "transitions"]
          },
          "interfaces": {
            "type": "array",
            "description": "节点接口集合",
            "items": {
              "type": "object",
              "properties": {
                "name": { "type": "string", "description": "接口标识名" },
                "direction": { "type": "string", "enum": ["IN", "OUT"] },
                "interfaceType": { 
                  "type": "string", 
                  "enum": ["CONTROL", "SIGNAL"],
                  "description": "CONTROL：节点控制信号，用于约束层的直接控制；SIGNAL：系统交互信号，用于节点间的交互、与状态机的交互"
                },
                "allowedSignals": { "type": "array", "items": { "type": "string" } },
                "bindingTriggers": { 
                  "type": "array",
                  "description": "绑定的触发器列表，实时监测条件。每个触发器独立判断自己的条件（0/1），条件满足即执行绑定的动作。多个触发器互不干扰。",
                  "items": { 
                    "type": "object",
                    "properties": {
                      "condition": { 
                        "type": "object",
                        "description": "结构化条件谓词，后端可直接用if-else求值",
                        "properties": {
                          "subject": { "type": "string", "description": "被观测的目标（内部变量名 / 生命周期状态名 / 信号名）" },
                          "operator": { "$ref": "protocol-dict.json#/definitions/ConstraintOperator" },
                          "threshold": { "type": ["number", "string", "boolean", "array"] }
                        },
                        "required": ["subject", "operator", "threshold"]
                      },
                      "actionName": {
                        "description": "引用actions数组中的动作类型",
                        "allOf": [
                          { "$ref": "protocol-dict.json#/definitions/WorkflowNodeAction" }
                        ]
                      }
                    },
                    "required": ["condition", "actionName"]
                  } 
                }
              },
              "required": ["name", "direction", "interfaceType"]
            }
          },
          "ports": {
            "type": "array",
            "description": "节点数据端口",
            "items": {
              "type": "object",
              "properties": {
                "name": { "type": "string" },
                "direction": { "type": "string", "enum": ["IN", "OUT"] },
                "internalVariableName": { "type": "string", "description": "绑定的内部变量名" }
              },
              "required": ["name", "direction", "internalVariableName"]
            }
          },
          "actions": {
            "type": "array",
            "description": "节点允许的内部动作类型列表。具体动作参数由后端根据动作类型和节点上下文解析",
            "items": { "$ref": "protocol-dict.json#/definitions/WorkflowNodeAction" }
          }
        },
        "required": ["name", "nodeType", "capability", "lifecycle", "interfaces", "actions"],
        "allOf": [
          {
            "if": { "properties": { "nodeType": { "const": "DEVICE_CAPABILITY_NODE" } } },
            "then": {
              "properties": {
                "deviceModelId": { "type": "integer", "description": "所属设备模型ID（仅DEVICE_CAPABILITY_NODE使用）" },
                "capability": {
                  "type": "object",
                  "properties": {
                    "capabilityName": { "type": "string", "description": "引用设备能力模型中的capability name" },
                    "capabilityParameters": { 
                      "type": "object",
                      "description": "设备能力的参数"
                    }
                  },
                  "required": ["capabilityName"]
                }
              },
              "required": ["deviceModelId"]
            }
          },
          {
            "if": { "properties": { "nodeType": { "const": "FUNCTIONAL_NODE" } } },
            "then": {
              "properties": {
                "capability": {
                  "type": "object",
                  "properties": {
                    "functionType": { "$ref": "protocol-dict.json#/definitions/WorkflowNodeFunctionType" },
                    "expression": { "type": "string", "description": "当functionType为EXPRESSION时，用户自定义的表达式，由后端表达式求值器解析" }
                  },
                  "required": ["functionType"]
                }
              }
            }
          },
          {
            "if": { "properties": { "nodeType": { "const": "SUB_FLOW_NODE" } } },
            "then": {
              "properties": {
                "capability": {
                  "type": "object",
                  "properties": {
                    "subFlowModelId": { "type": "integer", "description": "被引用的子流程模型ID" },
                    "subFlowModelDescription": { "type": "string", "description": "被引用的子流程模型名称" }
                  },
                  "required": ["subFlowModelId"]
                }
              }
            }
          }
        ]
      }
    },
    "interfaceConnections": {
      "type": "array",
      "description": "接口连接关系",
      "items": {
        "type": "object",
        "properties": {
          "description": { "type": "string" },
          "connectionType": { "type": "string", "enum": ["NODE_TO_NODE", "NODE_TO_STATE_MACHINE", "STATE_MACHINE_TO_NODE"] },
          "source": { "type": "object" },
          "target": { "type": "object" }
        },
        "required": ["connectionType", "source", "target"],
        "allOf": [
          {
            "if": { "properties": { "connectionType": { "const": "NODE_TO_NODE" } } },
            "then": {
              "properties": {
                "source": { 
                  "properties": { "nodeName": {"type":"string"}, "interfaceName": {"type":"string"} },
                  "required": ["nodeName", "interfaceName"]
                },
                "target": { 
                  "properties": { "nodeName": {"type":"string"}, "interfaceName": {"type":"string"} },
                  "required": ["nodeName", "interfaceName"]
                }
              }
            }
          },
          {
            "if": { "properties": { "connectionType": { "const": "NODE_TO_STATE_MACHINE" } } },
            "then": {
              "properties": {
                "source": { 
                  "properties": { "nodeName": {"type":"string"}, "interfaceName": {"type":"string"} },
                  "required": ["nodeName", "interfaceName"]
                },
                "target": { 
                  "properties": { "deviceInstanceId": {"type":"integer"}, "interfaceName": {"type":"string"} },
                  "required": ["deviceInstanceId", "interfaceName"]
                }
              }
            }
          },
          {
            "if": { "properties": { "connectionType": { "const": "STATE_MACHINE_TO_NODE" } } },
            "then": {
              "properties": {
                "source": { 
                  "properties": { "deviceInstanceId": {"type":"integer"}, "interfaceName": {"type":"string"} },
                  "required": ["deviceInstanceId", "interfaceName"]
                },
                "target": { 
                  "properties": { "nodeName": {"type":"string"}, "interfaceName": {"type":"string"} },
                  "required": ["nodeName", "interfaceName"]
                }
              }
            }
          }
        ]
      }
    },
    "portConnections": {
      "type": "array",
      "description": "端口连接关系",
      "items": {
        "type": "object",
        "properties": {
          "description": { "type": "string" },
          "source": { 
            "type": "object",
            "properties": { "nodeName": {"type": "string"}, "portName": {"type": "string"} },
            "required": ["nodeName", "portName"]
          },
          "target": { 
            "type": "object",
            "properties": { "nodeName": {"type": "string"}, "portName": {"type": "string"} },
            "required": ["nodeName", "portName"]
          }
        },
        "required": ["source", "target"]
      }
    }
  },
  "required": ["workflowTemplateId", "name", "nodes", "interfaceConnections", "portConnections"]
}

````

---

## Backend/src/main/resources/sql/permission_info_seed.sql

````text
-- 权限字典初始化脚本
-- 数据库：lab
-- 说明：
-- 1. 本脚本只写入 PERMISSION_INFO，不创建表、不修改表结构。
-- 2. 菜单所需权限在后端菜单字典中维护，菜单入口统一引用对应对象的 view 权限。
-- 3. 按钮和接口动作使用 create/edit/delete/control/export/assign 等动作权限。

INSERT INTO PERMISSION_INFO (ID, SCOPE, OBJECT, ACTION, PERMISSION_LEVEL, PERMIS_DESC) VALUES
(1,  'ALL', 'all',              'all',    4, '系统管理员拥有全部权限'),

-- 首页与设备资源层
(2,  'LAB', 'dashboard',        'view',   1, '查看首页总览'),
(3,  'LAB', 'device_category',  'view',   1, '查看设备类别'),
(4,  'LAB', 'device_category',  'edit',   3, '维护设备类别'),
(5,  'LAB', 'device_model',     'view',   1, '查看设备模型'),
(6,  'LAB', 'device_model',     'create', 3, '创建设备模型'),
(7,  'LAB', 'device_model',     'edit',   3, '编辑设备模型'),
(8,  'LAB', 'device_model',     'delete', 3, '删除设备模型'),
(9,  'LAB', 'device_instance',  'view',   1, '查看设备实例'),
(10, 'LAB', 'device_instance',  'create', 3, '创建设备实例'),
(11, 'LAB', 'device_instance',  'edit',   3, '编辑设备实例'),
(12, 'LAB', 'device_instance',  'delete', 3, '删除设备实例'),
(13, 'LAB', 'device_instance',  'control',2, '控制设备实例'),
(14, 'LAB', 'device_component', 'view',   1, '查看设备组件拓扑'),
(15, 'LAB', 'device_component', 'edit',   3, '维护设备组件拓扑'),
(16, 'LAB', 'adapter',          'view',   2, '查看设备执行代理聚合配置'),
(17, 'LAB', 'adapter',          'edit',   3, '编辑设备模型契约和实例代理配置'),

-- 数据资源层
(18, 'LAB', 'data_template',    'view',   1, '查看数据模板'),
(19, 'LAB', 'data_template',    'create', 3, '创建数据模板'),
(20, 'LAB', 'data_template',    'edit',   3, '编辑数据模板'),
(21, 'LAB', 'data_template',    'delete', 3, '删除数据模板'),
(22, 'LAB', 'data_dataset',     'view',   1, '查看数据集和数据点'),
(23, 'LAB', 'data_dataset',     'create', 3, '创建数据集'),
(24, 'LAB', 'data_dataset',     'edit',   3, '编辑数据集'),
(25, 'LAB', 'data_dataset',     'delete', 3, '删除或归档数据集'),
(26, 'LAB', 'data_dataset',     'export', 2, '导出数据集'),
(27, 'LAB', 'property_type',    'view',   1, '查看字段类型'),
(28, 'ALL', 'property_type',    'edit',   4, '维护字段类型'),

-- 工作流层
(29, 'LAB', 'workflow',         'view',   1, '查看流程模型'),
(30, 'OWN', 'workflow',         'create', 2, '创建自己的流程模型'),
(31, 'OWN', 'workflow',         'edit',   2, '编辑自己的流程模型'),
(32, 'OWN', 'workflow',         'delete', 2, '删除自己的流程模型'),
(33, 'LAB', 'workflow',         'create', 3, '创建本实验室流程模型'),
(34, 'LAB', 'workflow',         'edit',   3, '编辑本实验室流程模型'),
(35, 'LAB', 'workflow',         'delete', 3, '删除本实验室流程模型'),
(36, 'LAB', 'workflow',         'export', 2, '导出流程模型'),
(37, 'LAB', 'task',             'view',   1, '查看任务'),
(38, 'OWN', 'task',             'create', 2, '创建自己的任务'),
(39, 'OWN', 'task',             'edit',   2, '编辑自己的任务'),
(40, 'OWN', 'task',             'start',  2, '启动自己的任务'),
(41, 'OWN', 'task',             'stop',   2, '停止自己的任务'),
(42, 'LAB', 'task',             'create', 3, '创建本实验室任务'),
(43, 'LAB', 'task',             'edit',   3, '编辑本实验室任务'),
(44, 'LAB', 'task',             'delete', 3, '删除本实验室任务'),
(45, 'LAB', 'task',             'start',  3, '启动本实验室任务'),
(46, 'LAB', 'task',             'stop',   3, '停止本实验室任务'),

-- 约束层
(47, 'LAB', 'constraint_rule',  'view',   2, '查看约束规则'),
(48, 'LAB', 'constraint_rule',  'create', 3, '创建约束规则'),
(49, 'LAB', 'constraint_rule',  'edit',   3, '编辑约束规则'),
(50, 'LAB', 'constraint_rule',  'delete', 3, '删除约束规则'),
(51, 'LAB', 'violation_log',    'view',   1, '查看违规记录'),
(52, 'LAB', 'violation_log',    'export', 3, '导出违规记录'),

-- 场景与资源结构
(53, 'LAB', 'scene',            'view',   1, '查看场景'),
(54, 'LAB', 'scene',            'create', 3, '创建场景'),
(55, 'LAB', 'scene',            'edit',   3, '编辑场景'),
(56, 'LAB', 'scene',            'delete', 3, '删除场景'),
(57, 'LAB', 'resource',         'view',   1, '查看资源结构'),
(58, 'LAB', 'resource',         'edit',   3, '编辑资源结构'),

-- 用户与权限
(59, 'LAB', 'user',             'view',   3, '查看用户'),
(60, 'LAB', 'user',             'create', 3, '创建用户'),
(61, 'LAB', 'user',             'edit',   3, '编辑用户'),
(62, 'LAB', 'user',             'delete', 3, '删除用户'),
(63, 'LAB', 'permission',       'view',   3, '查看权限'),
(64, 'LAB', 'permission',       'assign', 3, '分配权限'),
(65, 'ALL', 'permission',       'edit',   4, '维护权限字典')
ON CONFLICT (ID) DO UPDATE SET
    SCOPE = EXCLUDED.SCOPE,
    OBJECT = EXCLUDED.OBJECT,
    ACTION = EXCLUDED.ACTION,
    PERMISSION_LEVEL = EXCLUDED.PERMISSION_LEVEL,
    PERMIS_DESC = EXCLUDED.PERMIS_DESC;

````

---

## schemas_archive/adapter-message-envelope.json

````text
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "AdapterMessageEnvelope",
  "description": "Canonical runtime copy: Backend/src/main/resources/schemas/adapter-message-envelope.json",
  "$ref": "../Backend/src/main/resources/schemas/adapter-message-envelope.json"
}

````

---

## schemas_archive/adapter-northbound-contract.json

````text
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "AdapterNorthboundContract",
  "description": "SmartLab 与设备执行代理之间的确定性北向契约。当前主要支持 MQTT；GetPLCData 等既有 adapter 可声明 TCP/socket 北向命令。",
  "type": "object",
  "properties": {
    "transportType": { "type": "string", "enum": ["MQTT", "TCP", "SOCKET_TCP", "HTTP"] },
    "contractVersion": { "type": "string", "minLength": 1 },
    "commandTopic": { "type": "string" },
    "statusTopic": { "type": "string" },
    "eventTopic": { "type": "string" },
    "telemetryTopic": { "type": "string" },
    "alarmTopic": { "type": "string" },
    "heartbeatTopic": { "type": "string" },
    "qos": { "type": "integer", "minimum": 0, "maximum": 2 },
    "commands": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "object",
        "properties": {
          "commandId": { "type": "string", "minLength": 1 },
          "commandName": { "type": "string" },
          "timeoutMs": { "type": "integer", "minimum": 1 },
          "commandParameters": {
            "type": "array",
            "items": {
              "type": "object",
              "properties": {
                "paramId": { "type": "string", "minLength": 1 },
                "paramName": { "type": "string" },
                "dataType": { "type": "string", "enum": ["integer", "float", "string", "boolean"] }
              },
              "required": ["paramId", "dataType"]
            }
          },
          "topic": { "type": "string" },
          "payloadTemplate": { "type": "object" },
          "lineTemplate": { "type": "string" },
          "query": { "type": "object", "additionalProperties": true },
          "tcp": { "type": "object", "additionalProperties": true }
        },
        "required": ["commandId"]
      }
    },
    "feedbackEvents": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "eventId": { "type": "string", "minLength": 1 },
          "eventName": { "type": "string" }
        },
        "required": ["eventId"]
      }
    }
  },
  "required": [
    "transportType",
    "contractVersion",
    "commands",
    "feedbackEvents"
  ],
  "additionalProperties": true
}

````

---

## schemas_archive/README.md

````text
# ⚠️ 注意 / WARNING

此目录内的 Schema 文件为**早期理论设计版本**，目前已**全面废弃并归档**。

为了确保开发过程中的结构一致性，系统中所有最新且正在被校验的 JSON Schema 文件统一存放在后端资源目录下：
👉 `Backend/src/main/resources/schemas/`

请不要再使用、参考或修改本目录下的旧文件！

````

---

## schemas_archive/resource-connection-graph.json

````text
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "ResourceConnectionGraph",
  "description": "Canonical runtime copy: Backend/src/main/resources/schemas/resource-connection-graph.json",
  "$ref": "../Backend/src/main/resources/schemas/resource-connection-graph.json"
}

````

---

## schemas_archive/工作流模型.json

````text
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "DynamicWorkflowModel",
  "description": "动态工作流模型：定义特定任务语境下的资源执行切片与组织关系",
  "type": "object",
  "properties": {
    "workflowTemplateId": { "type": "string" },
    "name": { "type": "string" },
    "nodes": {
      "type": "array",
      "description": "工作流节点集，对应能力执行切片 (VF)",
      "items": {
        "type": "object",
        "properties": {
          "nodeId": { "type": "string" },
          "name": { "type": "string" },
          "nodeType": { 
            "type": "string", 
            "enum": ["DEVICE_CAPABILITY_NODE", "FUNCTIONAL_NODE"] 
          },
          "capability": {
            "type": "object",
            "description": "节点能力语义 (ψ)。基于 nodeType 呈现多态结构"
          },
          "internalVariables": {
            "type": "array",
            "description": "内部变量空间 (X)，维护节点执行上下文",
            "items": {
              "type": "object",
              "properties": {
                "variableId": { "type": "string" },
                "name": { "type": "string" },
                "dataType": { "type": "string" },
                "attributesMapping": {
                  "type": "string",
                  "description": "可选的属性映射，格式为 deviceModelRef.attributeId"
                }
              },
              "required": ["variableId", "name", "dataType"]
            }
          },
          "lifecycle": {
            "type": "object",
            "description": "节点生命周期状态 (S) 与内部转移规则 (Θ)",
            "properties": {
              "initialState": { "type": "string" },
              "states": {
                "type": "array",
                "items": {
                  "oneOf": [
                    { "type": "string" },
                    {
                      "type": "object",
                      "properties": {
                        "stateId": { "type": "string" },
                        "stateName": { "type": "string" }
                      },
                      "required": ["stateId"]
                    }
                  ]
                }
              },
              "transitions": {
                "type": "array",
                "items": {
                  "type": "object",
                  "properties": {
                    "transitionId": { "type": "string" },
                    "from": { "type": "string" },
                    "to": { "type": "string" }
                  },
                  "required": ["transitionId", "from", "to"]
                }
              }
            },
            "required": ["initialState", "states", "transitions"]
          },
          "interfaces": {
            "type": "array",
            "description": "通信接口集 (I) 与挂载的触发器集合",
            "items": {
              "type": "object",
              "properties": {
                "interfaceId": { "type": "string" },
                "direction": { "type": "string", "enum": ["IN", "OUT"] },
                "interfaceType": { "type": "string", "enum": ["CONTROL_FLOW", "STATE_MACHINE_SIGNAL"] },
                "allowedSignals": { "type": "array", "items": { "type": "string" } },
                "bindingTriggers": { 
                  "type": "array",
                  "description": "输入接口的触发器规则 <cond, m>",
                  "items": { 
                    "type": "object",
                    "properties": {
                      "triggerId": { "type": "string" },
                      "cond": { "type": "string", "description": "触发条件表达式。支持使用 '*' 表示无条件触发" },
                      "actionRef": { "type": "string", "description": "引用的内部动作(M) ID" }
                    },
                    "required": ["triggerId", "cond", "actionRef"]
                  } 
                }
              },
              "required": ["interfaceId", "direction", "interfaceType"]
            }
          },
          "ports": {
            "type": "array",
            "description": "数据交换端口集 (P)",
            "items": {
              "type": "object",
              "properties": {
                "portId": { "type": "string" },
                "direction": { "type": "string", "enum": ["IN", "OUT"] },
                "internalVariableBinding": { "type": "string", "description": "绑定的内部变量ID" }
              },
              "required": ["portId", "direction", "internalVariableBinding"]
            }
          },
          "actions": {
            "type": "array",
            "description": "节点内部动作库 (M)",
            "items": {
              "type": "object",
              "properties": {
                "actionId": { "type": "string" },
                "actionType": { "type": "string", "enum": ["EMIT_SIGNAL", "UPDATE_VARIABLE", "EXECUTE_LOGIC", "SEND", "ASSIGN"] },
                "payload": { "type": "object", "description": "信息载荷或运算上下文" }
              },
              "required": ["actionId", "actionType"],
              "allOf": [
                {
                  "if": { "properties": { "actionType": { "const": "EMIT_SIGNAL" } } },
                  "then": {
                    "properties": {
                      "targetInterfaceRef": { "type": "string", "description": "信号发出的目的端口(OUT)" },
                      "interfaceRef": { "type": "string", "description": "信号发出的接口 ID" },
                      "signalType": { "type": "string", "description": "协议 Type 标识" }
                    },
                    "required": ["signalType"]
                  }
                },
                {
                  "if": { "properties": { "actionType": { "const": "UPDATE_VARIABLE" } } },
                  "then": {
                    "properties": {
                      "variableRef": { "type": "string", "description": "待更新的内部变量ID" },
                      "valueExpression": { "type": "string", "description": "更新计算表达式" }
                    },
                    "required": ["variableRef", "valueExpression"]
                  }
                },
                {
                  "if": { "properties": { "actionType": { "const": "EXECUTE_LOGIC" } } },
                  "then": {
                    "properties": {
                      "logicExpression": { "type": "string", "description": "执行的内部运算或判断逻辑" }
                    },
                    "required": ["logicExpression"]
                  }
                }
              ]
            }
          }
        },
        "required": ["nodeId", "nodeType", "capability", "lifecycle", "interfaces", "actions"],
        "allOf": [
          {
            "if": { "properties": { "nodeType": { "const": "DEVICE_CAPABILITY_NODE" } } },
            "then": {
              "properties": {
                "capability": {
                  "type": "object",
                  "properties": {
                    "deviceModelRef": { "type": "string" },
                    "capabilityRef": { "type": "string" }
                  },
                  "required": ["deviceModelRef", "capabilityRef"]
                }
              }
            }
          },
          {
            "if": { "properties": { "nodeType": { "const": "FUNCTIONAL_NODE" } } },
            "then": {
              "properties": {
                "capability": {
                  "type": "object",
                  "properties": {
                    "functionType": { "type": "string", "enum": ["START", "END", "BRANCH", "SYNC", "WAIT", "LOOP", "JOIN"] },
                    "actionRefs": { 
                      "type": "array", 
                      "items": { "type": "string" },
                      "description": "绑定的具体执行动作ID列表" 
                    }
                  },
                  "required": ["functionType"]
                }
              }
            }
          }
        ]
      }
    },
    "interfaceConnections": {
      "type": "array",
      "description": "接口路由拓扑 (CI)。控制流流转图",
      "items": {
        "type": "object",
        "properties": {
          "connectionId": { "type": "string" },
          "connectionType": { "type": "string", "enum": ["NODE_TO_NODE", "NODE_TO_STATE_MACHINE", "STATE_MACHINE_TO_NODE"] },
          "source": { 
            "type": "object",
            "properties": { "interfaceRef": {"type": "string"} },
            "required": ["interfaceRef"]
          },
          "target": { 
            "type": "object",
            "properties": { "interfaceRef": {"type": "string"} },
            "required": ["interfaceRef"]
          }
        },
        "required": ["connectionId", "connectionType", "source", "target"]
      }
    },
    "portConnections": {
      "type": "array",
      "description": "端口数据流拓扑 (CP)。定义持久的数据依赖网络",
      "items": {
        "type": "object",
        "properties": {
          "connectionId": { "type": "string" },
          "source": { 
            "type": "object",
            "properties": { "nodeRef": {"type": "string"}, "portRef": {"type": "string"} },
            "required": ["nodeRef", "portRef"]
          },
          "target": { 
            "type": "object",
            "properties": { "nodeRef": {"type": "string"}, "portRef": {"type": "string"} },
            "required": ["nodeRef", "portRef"]
          }
        },
        "required": ["connectionId", "source", "target"]
      }
    }
  },
  "required": ["workflowTemplateId", "name", "nodes", "interfaceConnections", "portConnections"]
}

````

---

## schemas_archive/设备状态机模型.json

````text
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "DeviceStateMachineModel",
  "description": "设备状态机模型：定义常驻设备实体的命令生命周期（逻辑进度）与物理操作状态（设备孪生）的正交并发演化",
  "type": "object",
  "properties": {
    "stateMachineId": { "type": "string", "description": "状态机实体全局唯一编码" },
    "deviceModelRef": { "type": "string", "description": "指向对应的静态设备能力模型 ID" },
    "interfaces": {
      "type": "array",
      "description": "接口集合（I），确立状态机在系统边界中的通信端点契约",
      "items": {
        "type": "object",
        "properties": {
          "interfaceId": { "type": "string" },
          "name": { "type": "string", "example": "if_wf_cmd_in" },
          "direction": { "type": "string", "enum": ["IN", "OUT"] },
          "interfaceType": { 
            "type": "string", 
            "enum": ["WORKFLOW", "STAT", "ADAPTER"],
            "description": "WORKFLOW: 接收外部节点控制命令; STAT: 发布指令运行进度状态; ADAPTER: 接收硬件事件或下发动作报文"
          },
          "allowedSignals": {
            "type": "array",
            "description": "契约验证层：限制并规定该通信接口允许进出的合法信号类型(Type)列表",
            "items": { "type": "string" }
          }
        },
        "required": ["interfaceId", "name", "direction", "interfaceType", "allowedSignals"]
      }
    },
    "stateRegions": {
      "type": "array",
      "description": "正交并发区域空间（Q = Q_cmd x Q_op）。外部输入信号将同时全局广播至所有区域",
      "minItems": 2,
      "items": {
        "type": "object",
        "properties": {
          "regionId": { "type": "string" },
          "regionType": { 
            "type": "string", 
            "enum": ["COMMAND_LIFECYCLE", "OPERATION_STATE"],
            "description": "COMMAND_LIFECYCLE管控任务进度指令响应；OPERATION_STATE管控底层硬件客观状态的孪生映射"
          },
          "initialState": { "type": "string", "description": "该并发区域的初试状态节点（q0）" },
          "states": {
            "type": "array",
            "description": "区域包含的状态节点集合",
            "items": {
              "type": "object",
              "properties": {
                "stateId": { "type": "string" },
                "stateName": { "type": "string", "example": "EXECUTING" },
                "onEntry": {
                  "type": "array",
                  "description": "进入该状态节点时自动触发并执行的动作流水线",
                  "items": { "$ref": "#/definitions/Action" }
                }
              },
              "required": ["stateId", "stateName"]
            }
          },
          "transitions": {
            "type": "array",
            "description": "状态转移规则集合（T），明确表达‘源状态、接口、触发信号、目标状态、动作集’的控制论语义",
            "items": {
              "type": "object",
              "properties": {
                "transitionId": { "type": "string" },
                "from": { "type": "string", "description": "源状态状态ID，允许使用 '*' 关键字声明任意状态跳转" },
                "to": { "type": "string", "description": "目标状态状态ID" },
                "trigger": {
                  "type": "object",
                  "description": "触发器：基于特定接口接收到的特定路由信号类型（Type）",
                  "properties": {
                    "interfaceRef": { "type": "string", "description": "接收到信号的 IN 方向接口唯一编码" },
                    "signalType": { "type": "string", "description": "匹配信息的 Type 意图标识" }
                  },
                  "required": ["interfaceRef", "signalType"]
                },
                "actions": {
                  "type": "array",
                  "description": "跃迁过程中伴随执行的副作用隔离动作流水线",
                  "items": { "$ref": "#/definitions/Action" }
                }
              },
              "required": ["transitionId", "from", "to", "trigger"]
            }
          }
        },
        "required": ["regionId", "regionType", "initialState", "states", "transitions"]
      }
    }
  },
  "required": ["stateMachineId", "deviceModelRef", "interfaces", "stateRegions"],
  "definitions": {
    "Action": {
      "type": "object",
      "description": "状态机动作原语模型：利用 JSON Schema 模式条件限制，实现强类型多态字段约束结构",
      "properties": {
        "actionType": { 
          "type": "string", 
          "enum": ["SEND", "ASSIGN"],
          "description": "SEND代表向外发送事件或底层委托；ASSIGN代表纯粹的内部更新上下文记忆"
        },
        "payload": {
          "type": "object",
          "description": "万能动态数据载荷。当ASSIGN时，承载键值对覆盖Context；当SEND时，承载发送正文"
        }
      },
      "required": ["actionType", "payload"],
      "allOf": [
        {
          "if": {
            "properties": { "actionType": { "const": "SEND" } }
          },
          "then": {
            "properties": {
              "interfaceRef": { "type": "string", "description": "SEND动作的指定信息出口接口 ID" },
              "signalType": { "type": "string", "description": "发送信息的协议 Type 标识（如 cmd_heat 或 EXEC_SUCCESS）" }
            },
            "required": ["interfaceRef", "signalType"]
          }
        },
        {
          "if": {
            "properties": { "actionType": { "const": "ASSIGN" } }
          },
          "then": {
            "properties": {
              "interfaceRef": { "not": {} },
              "signalType": { "not": {} }
            },
            "description": "确保 ASSIGN 动作结构的纯净性，严格禁止包含网络 I/O 相关的物理通道字段"
          }
        }
      ]
    }
  }
}
````

---

## schemas_archive/设备能力模型.json

````text
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "DeviceCapabilityModel",
  "description": "设备能力模型：静态定义设备的静态属性空间、暴露的操作能力、底层适配器契约与固有物理约束",
  "type": "object",
  "properties": {
    "metadata": {
      "type": "object",
      "properties": {
        "modelId": { "type": "string", "description": "设备能力模型唯一全局编码" },
        "modelName": { "type": "string", "description": "设备模型名称" },
        "deviceCategory": { "type": "string", "description": "设备物理分类" }
      },
      "required": ["modelId", "modelName", "deviceCategory"]
    },
    "attributes": {
      "type": "array",
      "description": "设备静态及动态属性空间（Att），由状态机直接进行物理映射或读取",
      "items": {
        "type": "object",
        "properties": {
          "attributeId": { "type": "string", "description": "属性唯一编码" },
          "name": { "type": "string", "description": "属性英文标识名" },
          "displayName": { "type": "string", "description": "前端 UI 渲染显示名" },
          "valueKind": { "type": "string", "enum": ["DISCRETE", "CONTINUOUS"] },
          "dataType": { "type": "string", "enum": ["integer", "float", "string", "boolean"] },
          "unit": { "type": "string", "description": "物理单位" }
        },
        "required": ["attributeId", "name", "valueKind", "dataType"]
      }
    },
    "capabilities": {
      "type": "array",
      "description": "设备向业务层暴露的操作能力集合（Φ）",
      "items": {
        "type": "object",
        "properties": {
          "capabilityId": { "type": "string", "description": "操作能力唯一编码" },
          "adapterCommandId": { "type": "string", "description": "映射并指向 adapterContract.commands 中的唯一 ID" },
          "name": { "type": "string", "example": "cap_heat" },
          "displayName": { "type": "string", "example": "反应釜加热" },
          "parameters": {
            "type": "array",
            "description": "对外暴露的业务参数列表，供前端生成输入表单及工作流节点配置",
            "items": {
              "type": "object",
              "properties": {
                "id": { "type": "string", "description": "参数编码" },
                "name": { "type": "string", "example": "targetTemp" },
                "displayName": { "type": "string", "example": "目标温度" }
              },
              "required": ["id", "name", "displayName"]
            }
          },
          "parameterMapping": {
            "type": "array",
            "description": "参数翻译与注入词典：打通业务参数与底层硬件命令参数的转换边界",
            "items": {
              "type": "object",
              "properties": {
                "commandParamId": { "type": "string", "description": "底层真实硬件命令的参数ID" },
                "capabilityParamId": { "type": "string", "description": "上层业务能力的参数ID，若为动态映射则填此字段" },
                "isFixedValue": { "type": "boolean", "default": false, "description": "是否为隐式注入的系统固定参数" },
                "fixedValue": { "type": "string", "description": "固定参数的值，在后端拼装时强行注入，不暴露给前端" }
              },
              "required": ["commandParamId", "isFixedValue"]
            }
          }
        },
        "required": ["capabilityId", "adapterCommandId", "name", "displayName", "parameters", "parameterMapping"]
      }
    },
    "adapterContract": {
      "type": "object",
      "description": "设备执行代理北向契约（Γ）：只描述上位机系统与 adapter 的 MQTT 命令、状态、事件、遥测接口，不描述 Modbus/串口/寄存器等南向硬件细节",
      "properties": {
        "transportType": { "type": "string", "enum": ["MQTT", "TCP", "SOCKET_TCP", "HTTP"], "description": "系统到 adapter 的北向协议。推荐 MQTT；GetPLCData 等既有 adapter 可使用 TCP/socket 契约。" },
        "contractVersion": { "type": "string", "default": "smartlab.adapter.v1" },
        "commandTopic": { "type": "string" },
        "statusTopic": { "type": "string" },
        "eventTopic": { "type": "string" },
        "telemetryTopic": { "type": "string" },
        "alarmTopic": { "type": "string" },
        "heartbeatTopic": { "type": "string" },
        "topicTemplates": {
          "type": "object",
          "additionalProperties": { "type": "string" },
          "description": "标准 topic 模板，允许使用 ${tenantId}/${labId}/${deviceType}/${modelId}/${deviceSn}/${commandId} 占位符"
        },
        "commands": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "commandId": { "type": "string", "description": "底层硬件指令唯一编码" },
              "commandName": { "type": "string", "example": "start_heating" },
              "topic": { "type": "string", "description": "MQTT/工业协议路由的主题" },
              "timeoutMs": { "type": "integer", "minimum": 1 },
              "successEvents": { "type": "array", "items": { "type": "string" } },
              "failureEvents": { "type": "array", "items": { "type": "string" } },
              "commandParameters": {
                "type": "array",
                "description": "底层硬件指令严格要求的参数声明，用于后端执行引擎在渲染前的类型安全校验",
                "items": {
                  "type": "object",
                  "properties": {
                    "paramId": { "type": "string" },
                    "paramName": { "type": "string", "example": "set_point" },
                    "dataType": { "type": "string", "enum": ["integer", "float", "string", "boolean"] }
                  },
                  "required": ["paramId", "paramName", "dataType"]
                }
              },
              "payloadTemplate": {
                "type": "object",
                "description": "发往底层适配器的真实 JSON 骨架结构，含变量占位符。由 Java 引擎拦截动作后自动调用并动态渲染"
              }
            },
            "required": ["commandId", "commandName", "topic", "commandParameters", "payloadTemplate"]
          }
        },
        "feedbackEvents": {
          "type": "array",
          "description": "适配器物理层回传的异步反馈事件字典",
          "items": {
            "type": "object",
            "properties": {
              "eventId": { "type": "string" },
              "eventName": { "type": "string", "example": "evt_temp_reached" }
            },
            "required": ["eventId", "eventName"]
          }
        }
      },
      "required": ["commands", "feedbackEvents"]
    },
    "ports": {
      "type": "array",
      "description": "属性-数据交互端口空间（PD），定义资源层节点间持久的数据交换边界",
      "items": {
        "type": "object",
        "properties": {
          "portId": { "type": "string" },
          "direction": { "type": "string", "enum": ["IN", "OUT"] },
          "bindingAttrId": { "type": "string", "description": "直连并绑定的内部属性 attributeId" }
        },
        "required": ["portId", "direction", "bindingAttrId"]
      }
    },
    "intrinsicConstraints": {
      "type": "array",
      "description": "设备固有物理约束（ΛD）：独立于特定任务上下文、常驻于资源层内部的安全防线",
      "items": {
        "type": "object",
        "properties": {
          "targetAttrId": { "type": "string", "description": "受监控的设备属性ID" },
          "operator": { "type": "string", "enum": ["<", "<=", "==", "!=", ">=", ">"] },
          "boundaryValue": { "type": "number", "description": "触发越界惩罚的阈值边界" },
          "violationStateRef": { "type": "string", "description": "一旦发生约束破坏，设备状态机功能区应强行跃迁至的异常状态ID（如 op_abnormal）" }
        },
        "required": ["targetAttrId", "operator", "boundaryValue", "violationStateRef"]
      }
    }
  },
  "required": ["metadata", "attributes", "capabilities", "adapterContract", "ports", "intrinsicConstraints"]
}

````

---

## schemas_archive/资源结构图.json

````text
{
  "connectionGraphId": "rcg_lab_001",
  "modelType": "RESOURCE_CONNECTION_GRAPH",
  "connections": [
    {
      "connectionId": "conn_reactor_temp_to_collector",
      "connectionType": "DATA_CHANNEL",
      "source": {
        "deviceInstanceRef": "dev_inst_reactor_01",
        "devicePortRef": "dport_temp_out"
      },
      "target": {
        "deviceInstanceRef": "dev_inst_data_collector_01",
        "devicePortRef": "dport_temp_in"
      },
      "direction": "SOURCE_TO_TARGET",
      "description": "反应釜温度数据接入采集器"
    }
  ]
}
````

---

## schemas_archive/需求约束模型.json

````text
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "ConstraintModel",
  "description": "需求约束模型：严格映射 C = <B0, O, H, U> 结构，提供全局边界约束与任务级补充约束的持续监督",
  "type": "object",
  "properties": {
    "observableObjects": {
      "type": "array",
      "description": "异构可观测对象空间 (O)。跨层融合资源对象与流程进度",
      "items": {
        "type": "object",
        "properties": {
          "observableId": { "type": "string" },
          "name": { "type": "string" },
          "sourceLayer": { "type": "string", "enum": ["R", "F"], "description": "数据来源：资源层(R) 或 工作流层(F)" },
          "sourceType": { 
            "type": "string", 
            "enum": ["DEVICE_ATTRIBUTE", "DEVICE_OPERATION_STATE", "NODE_LIFECYCLE_STATE"] 
          },
          "dataType": { "type": "string" }
        },
        "required": ["observableId", "sourceLayer", "sourceType"],
        "allOf": [
          {
            "if": { "properties": { "sourceLayer": { "const": "R" } } },
            "then": {
              "properties": {
                "deviceModelRef": { "type": "string" },
                "targetRef": { "type": "string", "description": "对应 attributeId 或 stateMachine 的 regionId" }
              },
              "required": ["deviceModelRef", "targetRef"]
            }
          },
          {
            "if": { "properties": { "sourceLayer": { "const": "F" } } },
            "then": {
              "properties": {
                "workflowTemplateRef": { "type": "string" },
                "nodeRef": { "type": "string" }
              },
              "required": ["workflowTemplateRef", "nodeRef"]
            }
          }
        ]
      }
    },
    "handlingActions": {
      "type": "array",
      "description": "违规后置处理动作集 (H)",
      "items": {
        "type": "object",
        "properties": {
          "handlingActionId": { "type": "string" },
          "actionType": { "type": "string", "enum": ["WARNING", "ALARM", "STOP_NODE", "ABORT_SYSTEM"] },
          "target": { 
            "type": "object",
            "description": "基于动作强度的作用域定向",
            "properties": {
              "nodeRef": { "type": "string", "description": "针对 STOP_NODE 必须指定目标节点" },
              "interfaceRef": { "type": "string", "description": "强制终止信号发射的接口路径" }
            }
          },
          "signalType": { "type": "string" }
        },
        "required": ["handlingActionId", "actionType"]
      }
    },
    "globalConstraints0": {
      "type": "array",
      "description": "全局边界约束集合 (B0)。持续活跃并长期约束整个实验室运行状态",
      "items": {
        "$ref": "#/definitions/ConstraintRuleTuple"
      }
    },
    "rules": {
      "type": "array",
      "description": "任务级约束规则集合",
      "items": {
        "$ref": "#/definitions/ConstraintRuleTuple"
      }
    },
    "observableObjects_O": {
      "type": "array",
      "items": {
        "type": "object"
      }
    },
    "handlingActions_H": {
      "type": "array",
      "items": {
        "type": "object"
      }
    },
    "globalConstraints_B0": {
      "type": "array",
      "items": {
        "$ref": "#/definitions/ConstraintRuleTuple"
      }
    },
    "taskRequirements": {
      "type": "object",
      "description": "任务级补充需求约束 (U_tau)。仅在特定任务执行期挂载生效",
      "properties": {
        "goals_G": {
          "type": "array",
          "description": "任务目标定义 (G_tau)",
          "items": {
            "type": "object",
            "properties": {
              "goalId": { "type": "string" },
              "description": { "type": "string" }
            },
            "required": ["goalId"]
          }
        },
        "taskConstraints": {
          "type": "array",
          "description": "特定于该任务的动态边界约束集 (B_tau)",
          "items": {
            "$ref": "#/definitions/ConstraintRuleTuple"
          }
        }
      },
      "required": ["goals_G", "taskConstraints"]
    }
  },
  "additionalProperties": true,
  "definitions": {
    "ConstraintRuleTuple": {
      "type": "object",
      "description": "统一边界检查规则四元组 β = <o, ⊙, v, h>",
      "properties": {
        "constraintRuleId": { "type": "string" },
        "observedObjectRef": { "type": "string", "description": "被观测对象 o，指向 observableObjects 中的 ID" },
        "operator": { "type": "string", "enum": ["<", "<=", "==", "!=", ">=", ">", "in", "not_in", "contains"], "description": "比较运算符 ⊙。默认语义为命中该谓词即违规；predicateType=SAFE 时表示必须满足该谓词。" },
        "threshold": { "type": ["number", "string", "array", "boolean"], "description": "约束边界、枚举集合或取值范围 v" },
        "predicateType": { "type": "string", "enum": ["VIOLATION", "SAFE"], "default": "VIOLATION", "description": "VIOLATION 表示条件成立即违规；SAFE 表示条件不成立才违规。" },
        "violationHandlingRef": { "type": "string", "description": "违规处理动作 h，指向 handlingActions 中的 ID" }
      },
      "required": ["constraintRuleId", "observedObjectRef", "operator", "threshold", "violationHandlingRef"]
    }
  }
}

````
