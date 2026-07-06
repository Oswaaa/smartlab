# smartlab_backend_management

Generated at: 2026-07-03T11:09:10

This file is generated from the local SmartLab repository for model-readable project context.

## File Tree

- Backend/src/main/java/com/smartlab/management/controller/constraint/ConstraintRuleController.java
- Backend/src/main/java/com/smartlab/management/controller/constraint/ViolationLogController.java
- Backend/src/main/java/com/smartlab/management/controller/resource/adapter/AdapterIndexController.java
- Backend/src/main/java/com/smartlab/management/controller/resource/adapter/MqttBridgeController.java
- Backend/src/main/java/com/smartlab/management/controller/resource/data/DataIndexController.java
- Backend/src/main/java/com/smartlab/management/controller/resource/data/DataRecordController.java
- Backend/src/main/java/com/smartlab/management/controller/resource/data/DataTemplateController.java
- Backend/src/main/java/com/smartlab/management/controller/resource/device/DeviceCategoryController.java
- Backend/src/main/java/com/smartlab/management/controller/resource/device/DeviceComponentController.java
- Backend/src/main/java/com/smartlab/management/controller/resource/device/DeviceInstanceController.java
- Backend/src/main/java/com/smartlab/management/controller/resource/device/DeviceModelController.java
- Backend/src/main/java/com/smartlab/management/controller/resource/device/DeviceTwinStateController.java
- Backend/src/main/java/com/smartlab/management/controller/resource/device/PropertyTypeController.java
- Backend/src/main/java/com/smartlab/management/controller/resource/device/ResourceStructureController.java
- Backend/src/main/java/com/smartlab/management/controller/resource/scene/SceneController.java
- Backend/src/main/java/com/smartlab/management/controller/user/PermissionController.java
- Backend/src/main/java/com/smartlab/management/controller/user/UserController.java
- Backend/src/main/java/com/smartlab/management/controller/workflow/FlowNodeController.java
- Backend/src/main/java/com/smartlab/management/controller/workflow/TaskController.java
- Backend/src/main/java/com/smartlab/management/controller/workflow/WorkflowController.java
- Backend/src/main/java/com/smartlab/management/dto/common/ApiResponse.java
- Backend/src/main/java/com/smartlab/management/dto/common/PageResult.java
- Backend/src/main/java/com/smartlab/management/dto/constraint/ConstraintDTO.java
- Backend/src/main/java/com/smartlab/management/dto/resource/adapter/AdapterRouteDTO.java
- Backend/src/main/java/com/smartlab/management/dto/resource/data/DataTemplateDTO.java
- Backend/src/main/java/com/smartlab/management/dto/resource/data/DataTemplateSaveDTO.java
- Backend/src/main/java/com/smartlab/management/dto/resource/device/DeviceInstanceDTO.java
- Backend/src/main/java/com/smartlab/management/dto/resource/device/DeviceModelDTO.java
- Backend/src/main/java/com/smartlab/management/dto/resource/device/DeviceModelSaveDTO.java
- Backend/src/main/java/com/smartlab/management/dto/resource/device/DeviceStateMachineSaveDTO.java
- Backend/src/main/java/com/smartlab/management/dto/user/MenuDTO.java
- Backend/src/main/java/com/smartlab/management/dto/user/UserRequestDTO.java
- Backend/src/main/java/com/smartlab/management/dto/workflow/TaskDTO.java
- Backend/src/main/java/com/smartlab/management/dto/workflow/TaskMonitorSummary.java
- Backend/src/main/java/com/smartlab/management/dto/workflow/WorkflowDTO.java
- Backend/src/main/java/com/smartlab/management/entity/constraint/ConstraintRule.java
- Backend/src/main/java/com/smartlab/management/entity/constraint/ViolationLog.java
- Backend/src/main/java/com/smartlab/management/entity/resource/adapter/AdapterIndex.java
- Backend/src/main/java/com/smartlab/management/entity/resource/data/DataIndex.java
- Backend/src/main/java/com/smartlab/management/entity/resource/data/DataTemplateDetail.java
- Backend/src/main/java/com/smartlab/management/entity/resource/data/DataTemplateMain.java
- Backend/src/main/java/com/smartlab/management/entity/resource/device/DeviceCategory.java
- Backend/src/main/java/com/smartlab/management/entity/resource/device/DeviceComponents.java
- Backend/src/main/java/com/smartlab/management/entity/resource/device/DeviceInstances.java
- Backend/src/main/java/com/smartlab/management/entity/resource/device/DeviceModels.java
- Backend/src/main/java/com/smartlab/management/entity/resource/device/DeviceTwinStates.java
- Backend/src/main/java/com/smartlab/management/entity/resource/device/PropertyType.java
- Backend/src/main/java/com/smartlab/management/entity/resource/device/ResourceStructure.java
- Backend/src/main/java/com/smartlab/management/entity/resource/scene/SceneDetail.java
- Backend/src/main/java/com/smartlab/management/entity/resource/scene/SceneMain.java
- Backend/src/main/java/com/smartlab/management/entity/user/PermissionInfo.java
- Backend/src/main/java/com/smartlab/management/entity/user/UserInfo.java
- Backend/src/main/java/com/smartlab/management/entity/workflow/FlowModels.java
- Backend/src/main/java/com/smartlab/management/entity/workflow/FlowNode.java
- Backend/src/main/java/com/smartlab/management/entity/workflow/StepLog.java
- Backend/src/main/java/com/smartlab/management/entity/workflow/Task.java
- Backend/src/main/java/com/smartlab/management/entity/workflow/TaskStep.java
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
- Backend/src/main/java/com/smartlab/management/service/db/common/ManagementCrudService.java
- Backend/src/main/java/com/smartlab/management/service/db/constraint/ConstraintRuleService.java
- Backend/src/main/java/com/smartlab/management/service/db/constraint/ViolationLogService.java
- Backend/src/main/java/com/smartlab/management/service/db/resource/adapter/AdapterIndexService.java
- Backend/src/main/java/com/smartlab/management/service/db/resource/data/DataIndexService.java
- Backend/src/main/java/com/smartlab/management/service/db/resource/data/DataRecordService.java
- Backend/src/main/java/com/smartlab/management/service/db/resource/data/DataTemplateService.java
- Backend/src/main/java/com/smartlab/management/service/db/resource/data/PropertyTypeService.java
- Backend/src/main/java/com/smartlab/management/service/db/resource/device/DeviceCategoryService.java
- Backend/src/main/java/com/smartlab/management/service/db/resource/device/DeviceComponentService.java
- Backend/src/main/java/com/smartlab/management/service/db/resource/device/DeviceInstanceService.java
- Backend/src/main/java/com/smartlab/management/service/db/resource/device/DeviceModelService.java
- Backend/src/main/java/com/smartlab/management/service/db/resource/device/DeviceTwinStateService.java
- Backend/src/main/java/com/smartlab/management/service/db/resource/scene/ResourceStructureService.java
- Backend/src/main/java/com/smartlab/management/service/db/resource/scene/SceneService.java
- Backend/src/main/java/com/smartlab/management/service/db/user/PermissionService.java
- Backend/src/main/java/com/smartlab/management/service/db/user/UserService.java
- Backend/src/main/java/com/smartlab/management/service/db/workflow/FlowNodeService.java
- Backend/src/main/java/com/smartlab/management/service/db/workflow/TaskService.java
- Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowService.java
- Backend/src/main/java/com/smartlab/management/service/menu/MenuCatalogService.java
- Backend/src/main/java/com/smartlab/management/service/menu/MenuService.java
- Backend/src/main/java/com/smartlab/management/service/protocol/AdapterPayloadMapperService.java

## Files

---

## Backend/src/main/java/com/smartlab/management/controller/constraint/ConstraintRuleController.java

````text
package com.smartlab.management.controller.constraint;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.constraint.ConstraintRule;
import com.smartlab.management.service.db.constraint.ConstraintRuleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/constraint/rule")
/**
 * 设备安全与逻辑约束规则管理控制器。用于配置实验室设备间相互制约的联锁保护规则与参数阈值约束。
 */
public class ConstraintRuleController {

    private final ConstraintRuleService service;

    public ConstraintRuleController(ConstraintRuleService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ConstraintRule>> getAllRules(@RequestParam(required = false) String sourceType,
                                                         @RequestParam(required = false) String objectEndpoint,
                                                         @RequestParam(required = false) Boolean isEnabled) {
        return ApiResponse.ok(service.list(sourceType, objectEndpoint, isEnabled));
    }

    @GetMapping("/list")
    public ApiResponse<List<ConstraintRule>> list(@RequestParam(required = false) String sourceType,
                                                  @RequestParam(required = false) String objectEndpoint,
                                                  @RequestParam(required = false) Boolean isEnabled) {
        return ApiResponse.ok(service.list(sourceType, objectEndpoint, isEnabled));
    }

    @GetMapping("/options")
    public ApiResponse<Map<String, Object>> options() {
        return ApiResponse.ok(service.options());
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<ConstraintRule>> page(@RequestParam(defaultValue = "1") long pageNo,
                                                        @RequestParam(defaultValue = "20") long pageSize,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String sourceType,
                                                        @RequestParam(required = false) String objectEndpoint,
                                                        @RequestParam(required = false) String objectName,
                                                        @RequestParam(required = false) String operator,
                                                        @RequestParam(required = false) Boolean isEnabled) {
        return ApiResponse.ok(service.page(pageNo, pageSize, keyword, sourceType, objectEndpoint, objectName, operator, isEnabled));
    }

    @GetMapping("/{id}")
    public ApiResponse<ConstraintRule> getById(@PathVariable Long id) {
        ConstraintRule rule = service.getById(id);
        return rule == null ? ApiResponse.fail("约束规则不存在") : ApiResponse.ok(rule);
    }

    @PostMapping
    public ApiResponse<ConstraintRule> createRule(@RequestBody ConstraintRule rule) {
        try {
            return ApiResponse.ok(service.save(rule));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/save")
    public ApiResponse<ConstraintRule> save(@RequestBody ConstraintRule rule) {
        try {
            return ApiResponse.ok(service.save(rule));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<ConstraintRule> updateRule(@PathVariable Long id, @RequestBody ConstraintRule rule) {
        rule.setId(id);
        try {
            return ApiResponse.ok(service.save(rule));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PatchMapping("/{id}/enabled")
    public ApiResponse<ConstraintRule> setEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        try {
            return ApiResponse.ok(service.setEnabled(id, enabled));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> deleteByPath(@PathVariable Long id) {
        return delete(id);
    }
}
````

---

## Backend/src/main/java/com/smartlab/management/controller/constraint/ViolationLogController.java

````text
package com.smartlab.management.controller.constraint;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.constraint.ViolationLog;
import com.smartlab.management.service.db.constraint.ViolationLogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/constraint/violation")
/**
 * 设备约束规则冲突违规日志控制器。用于检索与导出设备运行过程中触发联锁保护的违规警报历史日志。
 */
public class ViolationLogController {

    private final ViolationLogService service;

    public ViolationLogController(ViolationLogService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ViolationLog>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<ViolationLog>> page(@RequestParam(defaultValue = "1") long pageNo,
                                                      @RequestParam(defaultValue = "20") long pageSize,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) Long constraintRuleId,
                                                      @RequestParam(required = false) Long taskId,
                                                      @RequestParam(required = false) Long deviceInstanceId) {
        return ApiResponse.ok(service.page(pageNo, pageSize, keyword, constraintRuleId, taskId, deviceInstanceId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ViolationLog> get(@PathVariable Long id) {
        ViolationLog entity = service.getById(id);
        return entity == null ? ApiResponse.fail("违规日志不存在") : ApiResponse.ok(entity);
    }

    @PostMapping
    public ApiResponse<ViolationLog> create(@RequestBody ViolationLog entity) {
        try {
            return ApiResponse.ok(service.save(entity));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<ViolationLog> update(@PathVariable Long id, @RequestBody ViolationLog entity) {
        try {
            entity.setId(id);
            return ApiResponse.ok(service.save(entity));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}

````

---

## Backend/src/main/java/com/smartlab/management/controller/resource/adapter/AdapterIndexController.java

````text
package com.smartlab.management.controller.resource.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import com.smartlab.management.service.db.resource.adapter.AdapterIndexService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/adapter/index")
/**
 * 适配器静态元数据管理控制器。提供适配器模板配置的CRUD操作，用于管理支持的物理接入网关通道。
 */
public class AdapterIndexController {

    private final AdapterIndexService service;

    public AdapterIndexController(AdapterIndexService service) {
        this.service = service;
    }

    /**
     * 查询全部 Adapter 索引。
     */
    @GetMapping("/list")
    public ApiResponse<List<AdapterIndex>> list() {
        return ApiResponse.ok(service.list());
    }

    /**
     * 分页查询 Adapter 索引。
     */
    @GetMapping("/page")
    public ApiResponse<PageResult<AdapterIndex>> page(@RequestParam(defaultValue = "1") long pageNo,
                                                      @RequestParam(defaultValue = "20") long pageSize,
                                                      @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.page(pageNo, pageSize, keyword, "adapter_name", "status"));
    }

    /**
     * 按 ID 查询 Adapter 索引。
     */
    @GetMapping("/{id}")
    public ApiResponse<AdapterIndex> get(@PathVariable Long id) {
        AdapterIndex entity = service.getById(id);
        return entity == null ? ApiResponse.fail("Adapter 索引不存在") : ApiResponse.ok(entity);
    }

    /**
     * 保存 Adapter 索引。
     */
    @PostMapping("/save")
    public ApiResponse<AdapterIndex> save(@RequestBody AdapterIndex entity) {
        try {
            return ApiResponse.ok(service.save(entity));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 解析 AdapterRegisterRequest，但不写入 ADAPTER_INDEX。
     */
    @PostMapping("/parse-register")
    public ApiResponse<ObjectNode> parseRegister(@RequestBody Map<String, Object> payload) {
        try {
            return ApiResponse.ok(service.previewRegisterPayload(payload));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 保存 AdapterRegisterRequest 到 ADAPTER_INDEX。
     */
    @PostMapping("/register")
    public ApiResponse<AdapterIndex> register(@RequestBody Map<String, Object> payload) {
        try {
            return ApiResponse.ok(service.register(payload));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/{adapterName}/templates")
    public ApiResponse<JsonNode> templates(@PathVariable String adapterName) {
        try {
            return ApiResponse.ok(service.listTemplates(adapterName));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/{adapterName}/categories")
    public ApiResponse<JsonNode> categories(@PathVariable String adapterName) {
        try {
            return ApiResponse.ok(service.listAdapterCategories(adapterName));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/{adapterName}/device-points")
    public ApiResponse<JsonNode> devicePoints(@PathVariable String adapterName,
                                             @RequestParam(required = false) String templateName,
                                             @RequestParam(required = false) String categoryName) {
        try {
            return ApiResponse.ok(service.listDevicePoints(adapterName, templateName, categoryName));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/{adapterName}/adapter-contract")
    public ApiResponse<ObjectNode> adapterContract(@PathVariable String adapterName,
                                                   @RequestParam(required = false) String templateName,
                                                   @RequestParam(required = false) String categoryName) {
        try {
            if ((templateName == null || templateName.isBlank()) && (categoryName == null || categoryName.isBlank())) {
                return ApiResponse.fail("请选择 Adapter 类别或模板");
            }
            return ApiResponse.ok(service.buildAdapterContract(adapterName, templateName, categoryName));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
    /**
     * 更新 Adapter 心跳。
     */
    @PostMapping("/heartbeat")
    public ApiResponse<AdapterIndex> heartbeat(@RequestBody Map<String, Object> payload) {
        try {
            String adapterName = payload.get("adapterName") == null ? null : String.valueOf(payload.get("adapterName"));
            String status = payload.get("status") == null ? null : String.valueOf(payload.get("status"));
            return ApiResponse.ok(service.heartbeat(adapterName, status));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 删除 Adapter 索引。
     */
    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}

````

---

## Backend/src/main/java/com/smartlab/management/controller/resource/adapter/MqttBridgeController.java

````text
package com.smartlab.management.controller.resource.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import com.smartlab.adapter.MqttAdapterMessagingService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/adapter")
/**
 * MQTT物理网关桥接运行时控制器。提供实时MQTT长连接状态监测、快照获取以及手动触发重连注册的控制端点。
 */
public class MqttBridgeController {

    private final MqttAdapterMessagingService mqttAdapterMessagingService;

    public MqttBridgeController(MqttAdapterMessagingService mqttAdapterMessagingService) {
        this.mqttAdapterMessagingService = mqttAdapterMessagingService;
    }

    @GetMapping({"/mqtt/mqtt/status", "/protocol/mqtt/status"})
    public ApiResponse<Map<String, Object>> mqttStatus() {
        return ApiResponse.ok(mqttAdapterMessagingService.statusSnapshot());
    }


    @GetMapping({"/mqtt/pending-registrations", "/protocol/pending-registrations"})
    public ApiResponse<Object> pendingRegistrations() {
        return ApiResponse.ok(mqttAdapterMessagingService.pendingAdapterRegistrations());
    }

    @GetMapping(value = {"/mqtt/pending-registrations/stream", "/protocol/pending-registrations/stream"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter pendingRegistrationStream() {
        return mqttAdapterMessagingService.subscribePendingRegistrationEvents();
    }

    @PostMapping({"/mqtt/pending-registrations/{adapterName}/complete", "/protocol/pending-registrations/{adapterName}/complete"})
    public ApiResponse<AdapterIndex> completePendingRegistration(@PathVariable String adapterName,
                                                                 @RequestBody(required = false) JsonNode body) {
        try {
            JsonNode reviewedConfig = body == null || body.isNull() ? null : body.path("parsedConfig");
            if (reviewedConfig != null && reviewedConfig.isMissingNode()) {
                reviewedConfig = body;
            }
            return ApiResponse.ok(mqttAdapterMessagingService.completePendingAdapterRegistration(adapterName, reviewedConfig));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping({"/mqtt/pending-registrations/{adapterName}", "/protocol/pending-registrations/{adapterName}"})
    public ApiResponse<String> discardPendingRegistration(@PathVariable String adapterName) {
        mqttAdapterMessagingService.discardPendingAdapterRegistration(adapterName);
        return ApiResponse.ok("已忽略");
    }
    @PostMapping({"/mqtt/mqtt/reconnect", "/protocol/mqtt/reconnect"})
    public ApiResponse<Map<String, Object>> reconnectMqtt() {
        return ApiResponse.ok(mqttAdapterMessagingService.reconnectRegistrationListener());
    }
}

````

---

## Backend/src/main/java/com/smartlab/management/controller/resource/data/DataIndexController.java

````text
package com.smartlab.management.controller.resource.data;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.resource.data.DataIndex;
import com.smartlab.management.service.db.resource.data.DataIndexService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/data/index")
/**
 * 数据采集指标主索引控制器。管理系统支持采集的所有实验环境与设备运行原始指标项。
 */
public class DataIndexController {

    private final DataIndexService service;

    public DataIndexController(DataIndexService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ApiResponse<List<DataIndex>> list(@RequestParam(required = false) Long deviceInstanceId,
                                             @RequestParam(required = false) Long templateId) {
        if (deviceInstanceId != null) {
            return ApiResponse.ok(service.listByDeviceInstance(deviceInstanceId));
        }
        if (templateId != null) {
            return ApiResponse.ok(service.listByTemplate(templateId));
        }
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<DataIndex>> page(@RequestParam(defaultValue = "1") long pageNo,
                                                   @RequestParam(defaultValue = "20") long pageSize,
                                                   @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.page(pageNo, pageSize, keyword, "data_table", "data_desc"));
    }

    @GetMapping("/{id}")
    public ApiResponse<DataIndex> get(@PathVariable Long id) {
        DataIndex entity = service.getById(id);
        return entity == null ? ApiResponse.fail("数据索引不存在") : ApiResponse.ok(entity);
    }

    @PostMapping("/save")
    public ApiResponse<DataIndex> save(@RequestBody DataIndex entity) {
        try {
            return ApiResponse.ok(service.save(entity));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 创建数据集并自动创建对应的物理数据表。
     */
    @PostMapping("/create-dataset")
    public ApiResponse<DataIndex> createDataSet(@RequestBody Map<String, Object> payload) {
        try {
            Long templateId = longValue(payload.get("templateId"));
            Long deviceInstanceId = longValue(payload.get("deviceInstanceId"));
            String dataDesc = payload.get("dataDesc") == null ? null : String.valueOf(payload.get("dataDesc"));
            return ApiResponse.ok(service.createDataSet(templateId, deviceInstanceId, dataDesc));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    private Long longValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }
}


````

---

## Backend/src/main/java/com/smartlab/management/controller/resource/data/DataRecordController.java

````text
package com.smartlab.management.controller.resource.data;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.resource.data.DataIndex;
import com.smartlab.management.service.db.resource.data.DataRecordService;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

/**
 * 动态数据记录查询控制器。
 *
 * 通过 DATA_INDEX 定位真实物理数据表，然后分页读取记录。
 */
@RestController
@RequestMapping("/api/data/record")
/**
 * 物理实验历史采集数据流记录检索控制器。用于查询设备上传的历史时序遥测值或事件快照。
 */
public class DataRecordController {

    private final DataRecordService dataRecordService;

    public DataRecordController(DataRecordService dataRecordService) {
        this.dataRecordService = dataRecordService;
    }

    /**
     * 查询所有数据集索引。
     */
    @GetMapping("/datasets")
    public ApiResponse<List<DataIndex>> datasets() {
        return ApiResponse.ok(dataRecordService.listDataSets());
    }

    /**
     * 按模板 ID 查询第一张匹配的数据表记录。
     */
    @GetMapping("/page/{templateId}")
    public ApiResponse<PageResult<Map<String, Object>>> pageByTemplate(@PathVariable Long templateId,
                                                                       @RequestParam(defaultValue = "1") long pageNo,
                                                                       @RequestParam(defaultValue = "50") long pageSize) {
        try {
            return ApiResponse.ok(dataRecordService.pageByTemplateId(templateId, pageNo, pageSize));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 按 DATA_INDEX.ID 查询指定数据集记录。
     */
    @GetMapping("/dataset/{dataIndexId}")
    public ApiResponse<PageResult<Map<String, Object>>> pageByDataIndex(@PathVariable Long dataIndexId,
                                                                        @RequestParam(defaultValue = "1") long pageNo,
                                                                        @RequestParam(defaultValue = "50") long pageSize) {
        try {
            return ApiResponse.ok(dataRecordService.pageByDataIndexId(dataIndexId, pageNo, pageSize));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 导出数据集记录为 CSV 文件。
     */
    @GetMapping("/export/{dataIndexId}")
    public void exportCsv(@PathVariable Long dataIndexId, HttpServletResponse response) {
        try {
            dataRecordService.exportCsv(dataIndexId, response);
        } catch (Exception e) {
            response.setStatus(500);
        }
    }
}


````

---

## Backend/src/main/java/com/smartlab/management/controller/resource/data/DataTemplateController.java

````text
package com.smartlab.management.controller.resource.data;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.resource.data.DataTemplateSaveDTO;
import com.smartlab.management.entity.resource.data.DataTemplateDetail;
import com.smartlab.management.entity.resource.data.DataTemplateMain;
import com.smartlab.management.service.db.resource.data.DataTemplateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据模板控制器。
 *
 * 对应 DATA_TEMPLATE_MAIN 和 DATA_TEMPLATE_DETAIL。
 */
@RestController
@RequestMapping("/api/data/template")
/**
 * 实验数据采集模版配置控制器。定义特定实验方案下需要聚合监测的指标模板及图表映射规则。
 */
public class DataTemplateController {

    private final DataTemplateService dataTemplateService;

    public DataTemplateController(DataTemplateService dataTemplateService) {
        this.dataTemplateService = dataTemplateService;
    }

    @GetMapping("/list")
    public ApiResponse<List<DataTemplateMain>> list() {
        return ApiResponse.ok(dataTemplateService.list());
    }

    @GetMapping("/{id}/details")
    public ApiResponse<List<DataTemplateDetail>> details(@PathVariable Long id) {
        return ApiResponse.ok(dataTemplateService.listDetails(id));
    }

    @PostMapping("/save")
    public ApiResponse<Map<String, String>> save(@RequestBody Map<String, Object> payload) {
        try {
            DataTemplateMain template = dataTemplateService.savePayload(payload);
            return ApiResponse.ok(Map.of("templateId", String.valueOf(template.getId())));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 使用明确 DTO 保存模板主表和字段明细。
     */
    @PostMapping("/save-structured")
    public ApiResponse<DataTemplateMain> saveStructured(@RequestBody DataTemplateSaveDTO dto) {
        try {
            return ApiResponse.ok(dataTemplateService.saveTemplate(dto));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable String id) {
        try {
            dataTemplateService.delete(id);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}



````

---

## Backend/src/main/java/com/smartlab/management/controller/resource/device/DeviceCategoryController.java

````text
package com.smartlab.management.controller.resource.device;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.resource.device.DeviceCategory;
import com.smartlab.management.service.db.resource.device.DeviceCategoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/device/category")
/**
 * 设备品类与分类元数据控制器。用于管理基础物理设备类别（如高压反应釜、温控仪、机械臂等）。
 */
public class DeviceCategoryController {

    private final DeviceCategoryService service;

    public DeviceCategoryController(DeviceCategoryService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ApiResponse<List<DeviceCategory>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<DeviceCategory>> page(@RequestParam(defaultValue = "1") long pageNo,
                                                        @RequestParam(defaultValue = "20") long pageSize,
                                                        @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.page(pageNo, pageSize, keyword, "category_name", "description"));
    }

    @GetMapping("/{id}")
    public ApiResponse<DeviceCategory> get(@PathVariable Long id) {
        DeviceCategory entity = service.getById(id);
        return entity == null ? ApiResponse.fail("设备类别不存在") : ApiResponse.ok(entity);
    }

    @PostMapping("/save")
    public ApiResponse<DeviceCategory> save(@RequestBody DeviceCategory entity) {
        try {
            return ApiResponse.ok(service.save(entity));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}


````

---

## Backend/src/main/java/com/smartlab/management/controller/resource/device/DeviceComponentController.java

````text
package com.smartlab.management.controller.resource.device;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.resource.device.DeviceComponents;
import com.smartlab.management.service.db.resource.device.DeviceComponentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/device/component")
public class DeviceComponentController {

    private final DeviceComponentService service;

    public DeviceComponentController(DeviceComponentService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ApiResponse<List<DeviceComponents>> list(@RequestParam(required = false) Long parentInstanceId) {
        return ApiResponse.ok(service.listByParentInstance(parentInstanceId));
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<DeviceComponents>> page(@RequestParam(defaultValue = "1") long pageNo,
                                                          @RequestParam(defaultValue = "20") long pageSize,
                                                          @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.page(pageNo, pageSize, keyword, "component_name", "status"));
    }

    @GetMapping("/{id}")
    public ApiResponse<DeviceComponents> get(@PathVariable Long id) {
        DeviceComponents entity = service.getById(id);
        return entity == null ? ApiResponse.fail("设备组件不存在") : ApiResponse.ok(entity);
    }

    @PostMapping("/save")
    public ApiResponse<DeviceComponents> save(@RequestBody DeviceComponents entity) {
        try {
            return ApiResponse.ok(service.save(entity));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/{id}/configure")
    public ApiResponse<DeviceComponents> configure(@PathVariable Long id, @RequestBody DeviceComponents payload) {
        try {
            return ApiResponse.ok(service.configure(id, payload));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/{id}/discard")
    public ApiResponse<DeviceComponents> discard(@PathVariable Long id) {
        try {
            return ApiResponse.ok(service.discard(id));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/{id}/replace")
    public ApiResponse<DeviceComponents> replace(@PathVariable Long id, @RequestBody DeviceComponents payload) {
        try {
            return ApiResponse.ok(service.replace(id, payload));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/{id}/history")
    public ApiResponse<List<DeviceComponents>> history(@PathVariable Long id) {
        try {
            return ApiResponse.ok(service.history(id));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}
````

---

## Backend/src/main/java/com/smartlab/management/controller/resource/device/DeviceInstanceController.java

````text
package com.smartlab.management.controller.resource.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.management.dto.resource.adapter.AdapterRouteDTO;
import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.adapter.MqttAdapterMessagingService;
import com.smartlab.management.service.db.resource.device.DeviceInstanceService;
import com.smartlab.management.service.protocol.AdapterPayloadMapperService;
import com.smartlab.engine.statemachine.StateMachineEngine;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/device/instance")
/**
 * 设备物理实例生命周期与点位绑定控制器。控制物理硬件的网关配对、MQTT点位映射及指令/状态广播下发。
 */
public class DeviceInstanceController {

    private final DeviceInstanceService deviceInstanceService;
    private final MqttAdapterMessagingService mqttAdapterMessagingService;
    private final StateMachineEngine stateMachineEngine;
    private final AdapterPayloadMapperService protocolMapperService;

    public DeviceInstanceController(DeviceInstanceService deviceInstanceService,
                                    MqttAdapterMessagingService mqttAdapterMessagingService,
                                    StateMachineEngine stateMachineEngine,
                                    AdapterPayloadMapperService protocolMapperService) {
        this.deviceInstanceService = deviceInstanceService;
        this.mqttAdapterMessagingService = mqttAdapterMessagingService;
        this.stateMachineEngine = stateMachineEngine;
        this.protocolMapperService = protocolMapperService;
    }

    @GetMapping("/list")
    public ApiResponse<List<DeviceInstances>> list() {
        return ApiResponse.ok(deviceInstanceService.list());
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<DeviceInstances>> page(@RequestParam(defaultValue = "1") long pageNo,
                                                        @RequestParam(defaultValue = "24") long pageSize,
                                                        @RequestParam(required = false) String modelId,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) Boolean online) {
        return ApiResponse.ok(deviceInstanceService.page(pageNo, pageSize, modelId, keyword, online));
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Long>> summary(@RequestParam(required = false) String modelId) {
        return ApiResponse.ok(deviceInstanceService.summary(modelId));
    }

    @PostMapping("/save")
    public ApiResponse<Map<String, String>> save(@RequestBody Map<String, Object> payload) {
        try {
            DeviceInstances instance = deviceInstanceService.savePayload(payload);
            mqttAdapterMessagingService.trySubscribeDevicePointTopics(instance.getBoundAdapterName(), instance.getBoundDevicePoint());
            return ApiResponse.ok(Map.of("instanceId", String.valueOf(instance.getId())));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable String id) {
        try {
            deviceInstanceService.delete(id);
            return ApiResponse.ok("注销成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/snapshots")
    public ApiResponse<List<DeviceTwinStates>> listSnapshots() {
        return ApiResponse.ok(deviceInstanceService.listSnapshots());
    }

    @GetMapping("/snapshot/{id}")
    public ApiResponse<DeviceTwinStates> getSnapshot(@PathVariable String id) {
        DeviceTwinStates snapshot = deviceInstanceService.getSnapshot(id);
        return snapshot == null ? ApiResponse.fail("设备状态机监控记录不存在") : ApiResponse.ok(snapshot);
    }

    @GetMapping("/adapter-routes")
    public ApiResponse<Map<String, AdapterRouteDTO>> adapterRoutes(@RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResponse.ok(refresh ? protocolMapperService.refreshAdapterRouteTable() : protocolMapperService.getAdapterRouteTable());
    }

    @GetMapping("/binding/preview")
    public ApiResponse<JsonNode> previewBinding(@RequestParam Long modelId,
                                                @RequestParam String adapterName,
                                                @RequestParam String devicePoint) {
        try {
            return ApiResponse.ok(protocolMapperService.buildAdapterBinding(modelId, adapterName, devicePoint));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/control/{id}")
    public ApiResponse<ObjectNode> control(@PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            String commandId = body == null ? "" : String.valueOf(body.getOrDefault("commandId", ""));
            String signalName = body == null ? "MANUAL_EXECUTE" : String.valueOf(body.getOrDefault("signalName", "MANUAL_EXECUTE"));
            Map<String, Object> parameters = new HashMap<>();
            if (body != null && body.get("parameters") instanceof Map<?, ?> raw) {
                raw.forEach((key, value) -> parameters.put(String.valueOf(key), value));
            }
            ObjectNode result = stateMachineEngine.handleManualControl(Long.valueOf(id), signalName, commandId, parameters);
            return ApiResponse.ok(result);
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}

````

---

## Backend/src/main/java/com/smartlab/management/controller/resource/device/DeviceModelController.java

````text
package com.smartlab.management.controller.resource.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.dto.resource.device.DeviceModelSaveDTO;
import com.smartlab.management.dto.resource.device.DeviceStateMachineSaveDTO;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.service.db.resource.device.DeviceModelService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/device")
/**
 * 设备物模型（Capability Model）核心定义控制器。用于管理设备模型的属性空间、服务指令空间及状态机空间定义。
 */
public class DeviceModelController {

    private final DeviceModelService deviceModelService;

    public DeviceModelController(DeviceModelService deviceModelService) {
        this.deviceModelService = deviceModelService;
    }

    @GetMapping("/model/list")
    public ApiResponse<List<DeviceModels>> list() {
        return ApiResponse.ok(deviceModelService.list());
    }

    @GetMapping("/model/page")
    public ApiResponse<PageResult<DeviceModels>> page(@RequestParam(defaultValue = "1") long pageNo,
                                                      @RequestParam(defaultValue = "20") long pageSize,
                                                      @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(deviceModelService.page(pageNo, pageSize, keyword));
    }

    @GetMapping("/model/{id}")
    public ApiResponse<DeviceModels> getById(@PathVariable String id) {
        DeviceModels model = deviceModelService.getById(id);
        return model == null ? ApiResponse.fail("设备模型不存在") : ApiResponse.ok(model);
    }

    @PostMapping("/model/save")
    public ApiResponse<Map<String, String>> save(@RequestBody DeviceModelSaveDTO payload) {
        try {
            DeviceModels model = deviceModelService.savePayload(payload);
            return ApiResponse.ok(Map.of("modelId", String.valueOf(model.getId())));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/model/preview")
    public ApiResponse<ObjectNode> previewModel(@RequestBody DeviceModelSaveDTO payload) {
        try {
            ObjectNode result = deviceModelService.previewModel(payload);
            return ApiResponse.ok(result);
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/model/{id}/bundle")
    public ApiResponse<ObjectNode> modelBundle(@PathVariable String id) {
        try {
            return ApiResponse.ok(deviceModelService.modelBundle(id));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PutMapping("/model/{modelId}/adapter-contract")
    public ApiResponse<DeviceModels> updateAdapterContract(@PathVariable Long modelId,
                                                           @RequestBody JsonNode adapterContract) {
        try {
            return ApiResponse.ok(deviceModelService.updateAdapterContract(modelId, adapterContract));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/model/state-machine/list")
    public ApiResponse<List<ObjectNode>> listStateMachines() {
        return ApiResponse.ok(deviceModelService.listStateMachines());
    }

    @PostMapping("/model/state-machine/save")
    public ApiResponse<Map<String, String>> saveStateMachine(@RequestBody DeviceStateMachineSaveDTO payload) {
        try {
            String stateMachineId = deviceModelService.saveStateMachine(payload);
            return ApiResponse.ok(Map.of("stateMachineId", stateMachineId));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/model/state-machine/delete/{id}")
    public ApiResponse<String> deleteStateMachine(@PathVariable String id) {
        try {
            deviceModelService.deleteStateMachine(id);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/model/constraints/rules")
    public ApiResponse<List<Map<String, Object>>> listModelConstraintRules() {
        return ApiResponse.ok(deviceModelService.listModelConstraintRules());
    }

    @PostMapping("/model/constraints/rule/save")
    public ApiResponse<String> saveModelConstraintRule(@RequestBody Map<String, Object> payload) {
        try {
            deviceModelService.saveModelConstraintRule(payload);
            return ApiResponse.ok("保存成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/model/constraints/rule/delete")
    public ApiResponse<String> deleteModelConstraintRule(@RequestParam String modelId,
                                                         @RequestParam String constraintRuleId) {
        try {
            deviceModelService.deleteModelConstraintRule(modelId, constraintRuleId);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/model/delete/{id}")
    public ApiResponse<String> delete(@PathVariable String id) {
        try {
            deviceModelService.delete(id);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}


````

---

## Backend/src/main/java/com/smartlab/management/controller/resource/device/DeviceTwinStateController.java

````text
package com.smartlab.management.controller.resource.device;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.service.db.resource.device.DeviceTwinStateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/device/twin-state")
/**
 * 设备孪生影子（Device Twin）运行态快照控制器。提供高频遥测产生的逻辑属性状态及状态机所处阶段的检索端点。
 */
public class DeviceTwinStateController {

    private final DeviceTwinStateService service;

    public DeviceTwinStateController(DeviceTwinStateService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ApiResponse<List<DeviceTwinStates>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<DeviceTwinStates> get(@PathVariable Long id) {
        DeviceTwinStates entity = service.getById(id);
        return entity == null ? ApiResponse.fail("设备实时状态不存在") : ApiResponse.ok(entity);
    }

    @GetMapping("/by-instance/{instanceId}")
    public ApiResponse<DeviceTwinStates> getByInstance(@PathVariable Long instanceId) {
        DeviceTwinStates entity = service.getByInstanceId(instanceId);
        return entity == null ? ApiResponse.fail("设备实时状态不存在") : ApiResponse.ok(entity);
    }

    @PostMapping("/save")
    public ApiResponse<DeviceTwinStates> save(@RequestBody DeviceTwinStates entity) {
        try {
            return ApiResponse.ok(service.save(entity));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}


````

---

## Backend/src/main/java/com/smartlab/management/controller/resource/device/PropertyTypeController.java

````text
package com.smartlab.management.controller.resource.device;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.resource.device.PropertyType;
import com.smartlab.management.service.db.resource.data.PropertyTypeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/data/property-type")
/**
 * 物模型物理量属性类别定义控制器。规定各设备模型属性的数据类型、量纲单位及验证机制。
 */
public class PropertyTypeController {

    private final PropertyTypeService service;

    public PropertyTypeController(PropertyTypeService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ApiResponse<List<PropertyType>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<PropertyType>> page(@RequestParam(defaultValue = "1") long pageNo,
                                                      @RequestParam(defaultValue = "20") long pageSize,
                                                      @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.page(pageNo, pageSize, keyword, "type_name", "db_type", "description"));
    }

    @GetMapping("/{id}")
    public ApiResponse<PropertyType> get(@PathVariable Long id) {
        PropertyType entity = service.getById(id);
        return entity == null ? ApiResponse.fail("属性类型不存在") : ApiResponse.ok(entity);
    }

    @PostMapping("/save")
    public ApiResponse<PropertyType> save(@RequestBody PropertyType entity) {
        try {
            return ApiResponse.ok(service.save(entity));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}


````

---

## Backend/src/main/java/com/smartlab/management/controller/resource/device/ResourceStructureController.java

````text
package com.smartlab.management.controller.resource.device;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.resource.device.ResourceStructure;
import com.smartlab.management.service.db.resource.scene.ResourceStructureService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/resource/structure")
/**
 * 智能实验室物理空间与资源树形结构维护控制器。用于拓扑化呈现楼宇、实验室、房间及机位的层级归属。
 */
public class ResourceStructureController {

    private final ResourceStructureService service;

    public ResourceStructureController(ResourceStructureService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ApiResponse<List<ResourceStructure>> list(@RequestParam(required = false) Long sceneId) {
        return ApiResponse.ok(service.listByScene(sceneId));
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<ResourceStructure>> page(@RequestParam(defaultValue = "1") long pageNo,
                                                           @RequestParam(defaultValue = "20") long pageSize,
                                                           @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.page(pageNo, pageSize, keyword, "connection_name", "connection_type", "description"));
    }

    @GetMapping("/{id}")
    public ApiResponse<ResourceStructure> get(@PathVariable Long id) {
        ResourceStructure entity = service.getById(id);
        return entity == null ? ApiResponse.fail("资源结构不存在") : ApiResponse.ok(entity);
    }

    @PostMapping("/save")
    public ApiResponse<ResourceStructure> save(@RequestBody ResourceStructure entity) {
        try {
            return ApiResponse.ok(service.save(entity));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}


````

---

## Backend/src/main/java/com/smartlab/management/controller/resource/scene/SceneController.java

````text
package com.smartlab.management.controller.resource.scene;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.entity.resource.scene.SceneDetail;
import com.smartlab.management.entity.resource.scene.SceneMain;
import com.smartlab.management.service.db.resource.scene.SceneService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scene")
/**
 * 实验室多设备协同工作场景（Scene）定义与执行控制器。用于一键编排并控制场景下所有设备的初始逻辑状态与指令广播。
 */
public class SceneController {

    private final SceneService service;

    public SceneController(SceneService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ApiResponse<List<SceneMain>> listScenes() {
        return ApiResponse.ok(service.listScenes());
    }

    @GetMapping("/{id}")
    public ApiResponse<SceneMain> getScene(@PathVariable Long id) {
        SceneMain entity = service.getScene(id);
        return entity == null ? ApiResponse.fail("场景不存在") : ApiResponse.ok(entity);
    }

    @PostMapping("/save")
    public ApiResponse<SceneMain> saveScene(@RequestBody SceneMain entity) {
        try {
            return ApiResponse.ok(service.saveScene(entity));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> deleteScene(@PathVariable Long id) {
        try {
            service.deleteScene(id);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/detail/list")
    public ApiResponse<List<SceneDetail>> listDetails(@RequestParam(required = false) Long sceneId) {
        return ApiResponse.ok(service.listSceneDetails(sceneId));
    }

    @PostMapping("/detail/save")
    public ApiResponse<SceneDetail> saveDetail(@RequestBody SceneDetail entity) {
        try {
            return ApiResponse.ok(service.saveSceneDetail(entity));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/detail/delete/{id}")
    public ApiResponse<String> deleteDetail(@PathVariable Long id) {
        try {
            service.deleteSceneDetail(id);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}


````

---

## Backend/src/main/java/com/smartlab/management/controller/user/PermissionController.java

````text
package com.smartlab.management.controller.user;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.user.PermissionInfo;
import com.smartlab.management.service.db.user.PermissionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/permission")
/**
 * 系统基础权限元数据定义控制器。提供操作权限、菜单权限的配置及层级架构检索。
 */
public class PermissionController {

    private final PermissionService service;

    public PermissionController(PermissionService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ApiResponse<List<PermissionInfo>> list() {
        return ApiResponse.ok(service.listAll());
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<PermissionInfo>> page(@RequestParam(defaultValue = "1") long pageNo,
                                                        @RequestParam(defaultValue = "20") long pageSize,
                                                        @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.page(pageNo, pageSize, keyword, "scope", "object", "action", "permis_desc"));
    }

    @GetMapping("/{id}")
    public ApiResponse<PermissionInfo> get(@PathVariable Long id) {
        PermissionInfo entity = service.getById(id);
        return entity == null ? ApiResponse.fail("权限项不存在") : ApiResponse.ok(entity);
    }

    @PostMapping("/save")
    public ApiResponse<PermissionInfo> save(@RequestBody PermissionInfo entity) {
        try {
            return ApiResponse.ok(service.save(entity));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}


````

---

## Backend/src/main/java/com/smartlab/management/controller/user/UserController.java

````text
package com.smartlab.management.controller.user;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.user.UserRequestDTO;
import com.smartlab.management.entity.user.PermissionInfo;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.user.UserInfo;
import com.smartlab.management.service.db.user.PermissionService;
import com.smartlab.management.service.db.user.UserService;
import com.smartlab.management.service.db.workflow.TaskService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
/**
 * 系统用户账户管理与鉴权认证控制器。提供系统用户的登录、注册、个人资料拉取与角色控制接口。
 */
public class UserController {

    private final UserService userService;
    private final PermissionService permissionService;
    private final TaskService taskService;

    public UserController(UserService userService, PermissionService permissionService, TaskService taskService) {
        this.userService = userService;
        this.permissionService = permissionService;
        this.taskService = taskService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody UserRequestDTO request) {
        try {
            return ApiResponse.ok(userService.login(request.getUserName(), request.getPassword()));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile(Authentication authentication) {
        try {
            if (authentication == null || authentication.getName() == null) {
                return ApiResponse.fail("未登录或登录已过期");
            }
            return ApiResponse.ok(userService.currentUserProfile(authentication.getName()));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/menus")
    public ApiResponse<Object> menus(Authentication authentication) {
        try {
            if (authentication == null || authentication.getName() == null) {
                return ApiResponse.fail("未登录或登录已过期");
            }
            return ApiResponse.ok(userService.currentUserProfile(authentication.getName()).get("menus"));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/register")
    public ApiResponse<String> register(@RequestBody UserRequestDTO request) {
        try {
            userService.register(request);
            return ApiResponse.ok("注册成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/verify")
    public ApiResponse<String> verify(@RequestBody UserRequestDTO request) {
        try {
            userService.verifyAdmin(request.getUserName(), request.getPassword());
            return ApiResponse.ok("验证通过");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/list")
    public ApiResponse<List<UserInfo>> list() {
        return ApiResponse.ok(userService.listUsers());
    }

    @PostMapping("/update")
    public ApiResponse<String> update(@RequestBody UserRequestDTO request) {
        try {
            userService.updateUser(request);
            return ApiResponse.ok("更新成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.ok("删除成功");
    }

    @GetMapping("/permissions/list")
    public ApiResponse<List<PermissionInfo>> listPermissions() {
        return ApiResponse.ok(permissionService.listAll());
    }

    @GetMapping("/permissions/by-user/{id}")
    public ApiResponse<List<Integer>> permissionsByUser(@PathVariable Long id) {
        return ApiResponse.ok(permissionService.getUserPermissionIds(id));
    }

    @PostMapping("/permissions/assign")
    public ApiResponse<String> assignPermissions(@RequestBody Map<String, Object> payload) {
        Number userIdRaw = (Number) payload.get("userId");
        if (userIdRaw == null) {
            return ApiResponse.fail("userId 不能为空");
        }
        @SuppressWarnings("unchecked")
        List<Number> rawIds = (List<Number>) payload.getOrDefault("permissionIds", Collections.emptyList());
        List<Integer> ids = rawIds.stream().map(Number::intValue).toList();
        permissionService.assignPermissionsToUser(userIdRaw.longValue(), ids);
        return ApiResponse.ok("特权分配成功");
    }

    @GetMapping("/task-history/{id}")
    public ApiResponse<List<Task>> taskHistory(@PathVariable Long id) {
        return ApiResponse.ok(taskService.listByCreator(id));
    }
}


````

---

## Backend/src/main/java/com/smartlab/management/controller/workflow/FlowNodeController.java

````text
package com.smartlab.management.controller.workflow;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.service.db.workflow.FlowNodeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflow/node")
/**
 * 工作流设计节点定义与参数模型控制器。管理流程编排画布中各任务节点（如延时、读属性、发指令）的结构规范。
 */
public class FlowNodeController {

    private final FlowNodeService service;

    public FlowNodeController(FlowNodeService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ApiResponse<List<FlowNode>> list(@RequestParam(required = false) Long flowModelId) {
        return ApiResponse.ok(service.listByFlowModel(flowModelId));
    }

    @GetMapping("/{id}")
    public ApiResponse<FlowNode> get(@PathVariable Long id) {
        FlowNode entity = service.getById(id);
        return entity == null ? ApiResponse.fail("流程节点不存在") : ApiResponse.ok(entity);
    }

    @PostMapping("/save")
    public ApiResponse<FlowNode> save(@RequestBody FlowNode entity) {
        try {
            return ApiResponse.ok(service.save(entity));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}


````

---

## Backend/src/main/java/com/smartlab/management/controller/workflow/TaskController.java

````text
package com.smartlab.management.controller.workflow;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.dto.workflow.TaskMonitorSummary;
import com.smartlab.management.entity.workflow.StepLog;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.workflow.TaskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/task")
/**
 * 实验编排工作流执行实例（Task）生命周期监控控制器。追踪工作流实例的启动、挂起、终止及各步骤执行轨迹。
 */
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<Task>> page(@RequestParam(defaultValue = "1") long pageNo,
                                              @RequestParam(defaultValue = "20") long pageSize,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String status) {
        return ApiResponse.ok(taskService.page(pageNo, pageSize, keyword, status));
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Long>> summary() {
        return ApiResponse.ok(taskService.summary());
    }

    @GetMapping("/monitor/summary")
    public ApiResponse<TaskMonitorSummary> monitorSummary() {
        return ApiResponse.ok(taskService.monitorSummary());
    }

    @GetMapping("/{id}")
    public ApiResponse<Task> get(@PathVariable Long id) {
        Task task = taskService.getById(id);
        return task == null ? ApiResponse.fail("任务不存在") : ApiResponse.ok(task);
    }

    @PostMapping("/save")
    public ApiResponse<Map<String, Long>> save(@RequestBody Map<String, Object> payload) {
        try {
            Task task = taskService.savePayload(payload);
            return ApiResponse.ok(Map.of("taskId", task.getId()));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/start/{id}")
    public ApiResponse<Task> start(@PathVariable Long id) {
        try {
            return ApiResponse.ok(taskService.start(id));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/abort/{id}")
    public ApiResponse<Task> abort(@PathVariable Long id) {
        try {
            return ApiResponse.ok(taskService.abort(id));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        taskService.delete(id);
        return ApiResponse.ok("删除成功");
    }

    @GetMapping("/logs/{taskId}")
    public ApiResponse<List<StepLog>> logs(@PathVariable Long taskId,
                                           @RequestParam(required = false) Long afterLogId,
                                           @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(taskService.logs(taskId, afterLogId, limit));
    }

    @GetMapping("/snapshots/{taskId}")
    public ApiResponse<List<TaskStep>> snapshots(@PathVariable Long taskId) {
        return ApiResponse.ok(taskService.snapshots(taskId));
    }

}


````

---

## Backend/src/main/java/com/smartlab/management/controller/workflow/WorkflowController.java

````text
package com.smartlab.management.controller.workflow;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.entity.workflow.FlowModels;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflow")
/**
 * 实验协同工作流模板设计与发布控制器。管理基于流程图编排生成的协同控制逻辑逻辑模板。
 */
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping("/list")
    public ApiResponse<List<FlowModels>> list() {
        return ApiResponse.ok(workflowService.list());
    }

    @PostMapping("/save")
    public ApiResponse<Map<String, String>> save(@RequestBody Map<String, Object> payload) {
        try {
            FlowModels model = workflowService.savePayload(payload);
            return ApiResponse.ok(Map.of("templateId", String.valueOf(model.getId())));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable String id) {
        try {
            workflowService.delete(id);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping({"/export/{id}", "/detail/{id}"})
    public ApiResponse<FlowModels> detail(@PathVariable String id) {
        FlowModels template = workflowService.getById(id);
        return template == null ? ApiResponse.fail("流程模板不存在: " + id) : ApiResponse.ok(template);
    }

}


````

---

## Backend/src/main/java/com/smartlab/management/dto/common/ApiResponse.java

````text
package com.smartlab.management.dto.common;

import lombok.Data;

@Data
/**
 * ApiResponse 领域实体/配置模型类。
 */
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;

    public ApiResponse() {}

    public ApiResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, "操作成功");
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "操作成功", data);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, message);
    }
}


````

---

## Backend/src/main/java/com/smartlab/management/dto/common/PageResult.java

````text
package com.smartlab.management.dto.common;

import lombok.Data;

import java.util.List;

@Data
/**
 * PageResult 领域实体/配置模型类。
 */
public class PageResult<T> {

    private long total;
    private long pageNo;
    private long pageSize;
    private List<T> records;

    public PageResult(long total, long pageNo, long pageSize, List<T> records) {
        this.total = total;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.records = records;
    }
}


````

---

## Backend/src/main/java/com/smartlab/management/dto/constraint/ConstraintDTO.java

````text
package com.smartlab.management.dto.constraint;

import lombok.Data;

@Data
/**
 * Constraint业务数据传输载体对象（DTO）。
 */
public class ConstraintDTO {

    private String constraintRuleId;
    private String observedObjectRef;
    private String operator;
    private Object threshold; // numeric or string
    private String violationHandlingRef;
}


````

---

## Backend/src/main/java/com/smartlab/management/dto/resource/adapter/AdapterRouteDTO.java

````text
package com.smartlab.management.dto.resource.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
/**
 * AdapterRoute业务数据传输载体对象（DTO）。
 */
public class AdapterRouteDTO {

    private Long deviceInstanceId;
    private Long deviceModelId;
    private String instanceName;
    private String boundAdapterName;
    private String boundDevicePoint;
    private String templateName;
    private String categoryName;
    private JsonNode resolvedAttributes;
    private java.util.Map<String, String> rawToModelMap;
}

````

---

## Backend/src/main/java/com/smartlab/management/dto/resource/data/DataTemplateDTO.java

````text
package com.smartlab.management.dto.resource.data;

import lombok.Data;
import java.util.Map;

@Data
/**
 * DataTemplate业务数据传输载体对象（DTO）。
 */
public class DataTemplateDTO {

    private String templateId;
    private String templateName;
    private Map<String, Object> dataSchemaSpec;
    private String description;
}


````

---

## Backend/src/main/java/com/smartlab/management/dto/resource/data/DataTemplateSaveDTO.java

````text
package com.smartlab.management.dto.resource.data;

import com.smartlab.management.entity.resource.data.DataTemplateDetail;
import com.smartlab.management.entity.resource.data.DataTemplateMain;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据模板保存 DTO。
 *
 * 用于一次保存 DATA_TEMPLATE_MAIN 和 DATA_TEMPLATE_DETAIL。
 */
@Data
/**
 * DataTemplateSave业务数据传输载体对象（DTO）。
 */
public class DataTemplateSaveDTO {

    /** 模板主表。 */
    private DataTemplateMain main;

    /** 模板字段明细。 */
    private List<DataTemplateDetail> details = new ArrayList<>();
}


````

---

## Backend/src/main/java/com/smartlab/management/dto/resource/device/DeviceInstanceDTO.java

````text
package com.smartlab.management.dto.resource.device;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
/**
 * DeviceInstance业务数据传输载体对象（DTO）。
 */
public class DeviceInstanceDTO {

    private Long id;
    private Long deviceModelId;
    private String instanceName;
    private Map<String, Object> instanceConfig;
    private String picture;
    private List<Map<String, Object>> componentBindings;
}


````

---

## Backend/src/main/java/com/smartlab/management/dto/resource/device/DeviceModelDTO.java

````text
package com.smartlab.management.dto.resource.device;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
/**
 * DeviceModel业务数据传输载体对象（DTO）。
 */
public class DeviceModelDTO {

    private Long id;
    private String modelName;
    private Long categoryId;
    private List<Map<String, Object>> attributes;
    private List<Map<String, Object>> capabilities;
    private Map<String, Object> adapterContract;
    private List<Map<String, Object>> ports;
    private List<Map<String, Object>> intrinsicConstraints;
    private List<Map<String, Object>> stateMachineInterfaces;
    private Map<String, Object> opState;
    private Map<String, Object> cmdState;
    private List<Map<String, Object>> stateTransitions;
    private List<Map<String, Object>> componentsBom;
}


````

---

## Backend/src/main/java/com/smartlab/management/dto/resource/device/DeviceModelSaveDTO.java

````text
package com.smartlab.management.dto.resource.device;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
/**
 * DeviceModelSave业务数据传输载体对象（DTO）。
 */
public class DeviceModelSaveDTO {

    private Long modelId;
    private String modelName;
    private Long categoryId;
    private String categoryName;
    private JsonNode attributes;
    private JsonNode capabilities;
    private JsonNode adapterContract;
    private JsonNode ports;
    private JsonNode intrinsicConstraints;
    private JsonNode stateMachineInterfaces;
    private JsonNode opState;
    private JsonNode cmdState;
    private JsonNode stateTransitions;
    private JsonNode componentsBom;
    private JsonNode defaultDataTemplate;
}

````

---

## Backend/src/main/java/com/smartlab/management/dto/resource/device/DeviceStateMachineSaveDTO.java

````text
package com.smartlab.management.dto.resource.device;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
/**
 * DeviceStateMachineSave业务数据传输载体对象（DTO）。
 */
public class DeviceStateMachineSaveDTO {

    private Long modelId;
    private JsonNode interfacesDef;
    private JsonNode commandLifecycleDef;
    private JsonNode operationStateDef;
    private JsonNode transitions;
}

````

---

## Backend/src/main/java/com/smartlab/management/dto/user/MenuDTO.java

````text
package com.smartlab.management.dto.user;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu业务数据传输载体对象（DTO）。
 */
public class MenuDTO {

    private String name;
    private String path;
    private List<String> requires = new ArrayList<>();
    private List<MenuDTO> children = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<String> getRequires() {
        return requires != null ? requires : new ArrayList<>();
    }

    public void setRequires(List<String> requires) {
        this.requires = requires != null ? requires : new ArrayList<>();
    }

    public List<MenuDTO> getChildren() {
        return children != null ? children : new ArrayList<>();
    }

    public void setChildren(List<MenuDTO> children) {
        this.children = children != null ? children : new ArrayList<>();
    }

    public void addRequire(String object) {
        if (this.requires == null) {
            this.requires = new ArrayList<>();
        }
        this.requires.add(object);
    }

    public void addChild(MenuDTO child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }
}


````

---

## Backend/src/main/java/com/smartlab/management/dto/user/UserRequestDTO.java

````text
package com.smartlab.management.dto.user;

import lombok.Data;
import java.util.Map;

@Data
/**
 * UserRequest业务数据传输载体对象（DTO）。
 */
public class UserRequestDTO {

    private Integer id;        // 编辑时必传
    private String userName;
    private String password;
    private String roleName;
    private String lab;
    private Map<String, Object> userBasicInfo;
    private Integer userLevel;
}


````

---

## Backend/src/main/java/com/smartlab/management/dto/workflow/TaskDTO.java

````text
package com.smartlab.management.dto.workflow;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.Map;

@Data
/**
 * Task业务数据传输载体对象（DTO）。
 */
public class TaskDTO {

    private Integer taskId;
    private String taskName;
    private String templateId;
    private Map<String, Object> globalConstraints;
    private String currentStatus;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
}


````

---

## Backend/src/main/java/com/smartlab/management/dto/workflow/TaskMonitorSummary.java

````text
package com.smartlab.management.dto.workflow;

import com.smartlab.management.entity.workflow.Task;
import lombok.Data;

import java.util.List;

@Data
/**
 * TaskMonitorSummary 领域实体/配置模型类。
 */
public class TaskMonitorSummary {

    private List<Task> runningTasks;
    private List<Task> pendingTasks;
    private long todayCompletedCount;
    private long todayFailedCount;
}


````

---

## Backend/src/main/java/com/smartlab/management/dto/workflow/WorkflowDTO.java

````text
package com.smartlab.management.dto.workflow;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
/**
 * Workflow业务数据传输载体对象（DTO）。
 */
public class WorkflowDTO {

    private String templateId;
    private String templateName;
    private List<Map<String, Object>> nodesDef;
    private List<Map<String, Object>> interfaceConnections;
    private List<Map<String, Object>> portConnections;
}


````

---

## Backend/src/main/java/com/smartlab/management/entity/constraint/ConstraintRule.java

````text
package com.smartlab.management.entity.constraint;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.common.PostgresJsonbTypeHandler;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"CONSTRAINT_RULE\"", autoResultMap = true)
/**
 * 安全联锁与物理制约规则实体类。对应 CONSTRAINT_RULES 表，定义设备状态或数据指标异常时的联锁触发逻辑。
 */
public class ConstraintRule {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("rule_name")
    private String ruleName;

    @TableField("source_type")
    private String sourceType;

    @TableField("object_endpoint")
    private String objectEndpoint;

    /**
     * User-defined observable object name. This maps to observableObjects[].name
     * when rules are assembled into a constraint model file.
     */
    @TableField("object_name")
    private String objectName;

    @TableField("operator")
    private String operator;

    @TableField("threshold")
    private String threshold;

    @TableField(value = "violation_actions", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode violationActions;

    @TableField("description")
    private String description;

    @TableField("is_enabled")
    private Boolean isEnabled;

    @TableField("create_time")
    private OffsetDateTime createTime;

}
````

---

## Backend/src/main/java/com/smartlab/management/entity/constraint/ViolationLog.java

````text
package com.smartlab.management.entity.constraint;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.common.PostgresJsonbTypeHandler;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"VIOLATION_LOG\"", autoResultMap = true)
/**
 * 联锁违规报警记录实体。对应 VIOLATION_LOGS 表，保存触发安全联锁动作的现场异常参数记录。
 */
public class ViolationLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("constraint_rule_id")
    private Long constraintRuleId;

    @TableField("constraint_type")
    private String constraintType;

    @TableField("task_id")
    private Long taskId;

    @TableField("task_step_id")
    private Long taskStepId;

    @TableField("device_instance_id")
    private Long deviceInstanceId;

    @TableField("observed_variable")
    private String observedVariable;

    @TableField(value = "expected_condition", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode expectedCondition;

    @TableField(value = "actual_value", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode actualValue;

    @TableField(value = "variable_snapshot", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode variableSnapshot;

    @TableField("action_taken")
    private String actionTaken;

    @TableField("violation_time")
    private OffsetDateTime violationTime;

}


````

---

## Backend/src/main/java/com/smartlab/management/entity/resource/adapter/AdapterIndex.java

````text
package com.smartlab.management.entity.resource.adapter;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.common.PostgresJsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName(value = "\"ADAPTER_INDEX\"", autoResultMap = true)
/**
 * 适配器网关注册实体类。对应 ADAPTER_INDEX 表，持久化存储物理接入通道的静态连接属性与模版关联。
 */
public class AdapterIndex {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("adapter_name")
    private String adapterName;

    @TableField("original_config")
    private String originalConfig;

    @TableField(value = "parsed_config", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode parsedConfig;

    @TableField("status")
    private String status;

    @TableField("last_heartbeat")
    private OffsetDateTime lastHeartbeat;

    @TableField("create_time")
    private OffsetDateTime createTime;

    @TableField("update_time")
    private OffsetDateTime updateTime;
}

````

---

## Backend/src/main/java/com/smartlab/management/entity/resource/data/DataIndex.java

````text
package com.smartlab.management.entity.resource.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"DATA_INDEX\"", autoResultMap = false)
/**
 * 数据采集指标配置实体。对应 DATA_INDEX 表，持久化管理每个采集点位的量纲和属性。
 */
public class DataIndex {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("data_template_id")
    private Long dataTemplateId;

    @TableField("data_table")
    private String dataTable;

    @TableField("data_desc")
    private String dataDesc;

    @TableField("device_instance_id")
    private Long deviceInstanceId;

    @TableField("create_time")
    private OffsetDateTime createTime;

}


````

---

## Backend/src/main/java/com/smartlab/management/entity/resource/data/DataTemplateDetail.java

````text
package com.smartlab.management.entity.resource.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"DATA_TEMPLATE_DETAIL\"", autoResultMap = false)
/**
 * 数据采集模版明细实体。对应 DATA_TEMPLATE_DETAILS 表，关联模版下各具体指标项的展示排序与转换系数。
 */
public class DataTemplateDetail {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("data_template_id")
    private Long dataTemplateId;

    @TableField("column_name")
    private String columnName;

    @TableField("column_desc")
    private String columnDesc;

    @TableField("property_type_id")
    private Long propertyTypeId;

    @TableField("column_length")
    private Integer columnLength;

    @TableField("device_attr_key")
    private String deviceAttrKey;

    @TableField("default_value")
    private String defaultValue;

    @TableField("create_time")
    private OffsetDateTime createTime;

}


````

---

## Backend/src/main/java/com/smartlab/management/entity/resource/data/DataTemplateMain.java

````text
package com.smartlab.management.entity.resource.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"DATA_TEMPLATE_MAIN\"", autoResultMap = false)
/**
 * 数据采集模板主表实体。对应 DATA_TEMPLATE_MAIN 表，存储数据模板基本描述与拥有者信息。
 */
public class DataTemplateMain {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    public String getTemplateId() {
        return id == null ? null : String.valueOf(id);
    }

    @JsonSetter("templateId")
    public void setTemplateId(String templateId) {
        if (templateId != null && !templateId.isBlank()) {
            this.id = Long.valueOf(templateId);
        }
    }

    @TableField("template_name")
    private String templateName;

    @TableField("template_desc")
    private String templateDesc;

    @TableField("device_model_id")
    private Long deviceModelId;

    @TableField("is_default")
    private Boolean isDefault;

    @TableField("create_time")
    private OffsetDateTime createTime;

}


````

---

## Backend/src/main/java/com/smartlab/management/entity/resource/device/DeviceCategory.java

````text
package com.smartlab.management.entity.resource.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"DEVICE_CATEGORY\"", autoResultMap = false)
/**
 * 设备基础品类定义实体。对应 DEVICE_CATEGORIES 表，划定硬件的分类大纲。
 */
public class DeviceCategory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("category_name")
    private String categoryName;

    @TableField("parent_category_id")
    private Long parentCategoryId;

    @TableField("description")
    private String description;

    @TableField("create_time")
    private OffsetDateTime createTime;

}


````

---

## Backend/src/main/java/com/smartlab/management/entity/resource/device/DeviceComponents.java

````text
package com.smartlab.management.entity.resource.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.common.PostgresJsonbTypeHandler;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"DEVICE_COMPONENTS\"", autoResultMap = true)
/**
 * 设备内部组装部件实体。对应 DEVICE_COMPONENTS 表，表征复杂仪器的内部拓扑硬件清单。
 */
public class DeviceComponents {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("component_name")
    private String componentName;

    @TableField("category_id")
    private Long categoryId;

    @TableField("parent_instance_id")
    private Long parentInstanceId;

    @TableField("self_instance_id")
    private Long selfInstanceId;

    @TableField("status")
    private String status;

    @TableField(value = "specification", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode specification;

    @TableField("predecessor_id")
    private Long predecessorId;

    @TableField("install_time")
    private OffsetDateTime installTime;

    @TableField("create_time")
    private OffsetDateTime createTime;

}


````

---

## Backend/src/main/java/com/smartlab/management/entity/resource/device/DeviceInstances.java

````text
package com.smartlab.management.entity.resource.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.common.PostgresJsonbTypeHandler;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"DEVICE_INSTANCES\"", autoResultMap = true)
/**
 * 物理设备实例注册实体。对应 DEVICE_INSTANCES 表，保存设备名、出厂编号、在线网关绑定及孪生配置JSON。
 */
public class DeviceInstances {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    public String getInstanceId() {
        return id == null ? null : String.valueOf(id);
    }

    @JsonSetter("instanceId")
    public void setInstanceId(String instanceId) {
        if (instanceId != null && !instanceId.isBlank()) {
            this.id = Long.valueOf(instanceId);
        }
    }

    @TableField("device_model_id")
    private Long deviceModelId;

    public String getModelId() {
        return deviceModelId == null ? null : String.valueOf(deviceModelId);
    }

    @JsonSetter("modelId")
    public void setModelId(String modelId) {
        if (modelId != null && !modelId.isBlank()) {
            this.deviceModelId = Long.valueOf(modelId);
        }
    }

    @TableField("instance_name")
    private String instanceName;

    @TableField(value = "instance_config", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode instanceConfig;

    @TableField("bound_adapter_name")
    private String boundAdapterName;

    @TableField("bound_device_point")
    private String boundDevicePoint;

    @TableField("picture")
    private String picture;

    @TableField("create_time")
    private OffsetDateTime createTime;

    public JsonNode getCommConfig() {
        return instanceConfig;
    }

    public void setCommConfig(JsonNode commConfig) {
        this.instanceConfig = commConfig;
    }

    public Boolean getIsOnline() {
        return null;
    }

}


````

---

## Backend/src/main/java/com/smartlab/management/entity/resource/device/DeviceModels.java

````text
package com.smartlab.management.entity.resource.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.common.PostgresJsonbTypeHandler;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"DEVICE_MODELS\"", autoResultMap = true)
/**
 * 设备物模型核心定义实体。对应 DEVICE_MODELS 表，保存属性、服务及双状态机空间的大JSON配置定义。
 */
public class DeviceModels {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    public String getModelId() {
        return id == null ? null : String.valueOf(id);
    }

    @JsonSetter("modelId")
    public void setModelId(String modelId) {
        if (modelId != null && !modelId.isBlank()) {
            this.id = Long.valueOf(modelId);
        }
    }

    @TableField("model_name")
    private String modelName;

    @TableField("category_id")
    private Long categoryId;

    @TableField(value = "attributes", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode attributes;

    @TableField(value = "capabilities", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode capabilities;

    @TableField(value = "adapter_contract", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode adapterContract;

    @TableField(value = "ports", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode ports;

    @JsonAlias("intrinsicConstraints")
    @TableField(value = "intrinsic_constraint", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode intrinsicConstraint;

    @TableField(value = "state_machine_interfaces", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode stateMachineInterfaces;

    @TableField(value = "op_state", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode opState;

    @TableField(value = "cmd_state", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode cmdState;

    @TableField(value = "state_transitions", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode stateTransitions;

    @TableField(value = "components_bom", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode componentsBom;

    @TableField("create_time")
    private OffsetDateTime createTime;

    @TableField("update_time")
    private OffsetDateTime updateTime;

    public JsonNode getIntrinsicConstraints() {
        return intrinsicConstraint;
    }

    @JsonSetter("intrinsicConstraints")
    public void setIntrinsicConstraints(JsonNode intrinsicConstraints) {
        this.intrinsicConstraint = intrinsicConstraints;
    }

}


````

---

## Backend/src/main/java/com/smartlab/management/entity/resource/device/DeviceTwinStates.java

````text
package com.smartlab.management.entity.resource.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.common.PostgresJsonbTypeHandler;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"DEVICE_TWIN_STATES\"", autoResultMap = true)
/**
 * 设备孪生实时状态快照实体。对应 DEVICE_TWIN_STATES 表，高速缓存最新的遥测值属性与双状态机当前状态。
 */
public class DeviceTwinStates {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("instance_id")
    private Long instanceId;

    @TableField("current_op_state")
    private String currentOpState;

    @TableField("current_cmd_state")
    private String currentCmdState;

    @TableField(value = "current_attr", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode currentAttr;

    @TableField("online_status")
    private String onlineStatus;

    @TableField("last_online_time")
    private OffsetDateTime lastOnlineTime;

    @TableField("update_time")
    private OffsetDateTime updateTime;

    public String getCurrentCommandState() {
        return currentCmdState;
    }

    public String getCurrentOperationState() {
        return currentOpState;
    }

    public JsonNode getLatestAttributes() {
        return currentAttr;
    }

}


````

---

## Backend/src/main/java/com/smartlab/management/entity/resource/device/PropertyType.java

````text
package com.smartlab.management.entity.resource.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"PROPERTY_TYPE\"", autoResultMap = false)
/**
 * 物模型属性物理量类型实体。对应 PROPERTY_TYPES 表，规定数据指标的标准数据规范。
 */
public class PropertyType {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("type_name")
    private String typeName;

    @TableField("db_type")
    private String dbType;

    @TableField("description")
    private String description;

    @TableField("create_time")
    private OffsetDateTime createTime;

}


````

---

## Backend/src/main/java/com/smartlab/management/entity/resource/device/ResourceStructure.java

````text
package com.smartlab.management.entity.resource.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"RESOURCE_STRUCTURE\"", autoResultMap = false)
/**
 * 实验室资源拓扑节点实体。对应 RESOURCE_STRUCTURE 表，构建实验室空间拓扑树。
 */
public class ResourceStructure {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("scene_id")
    private Long sceneId;

    @TableField("connection_name")
    private String connectionName;

    @TableField("connection_type")
    private String connectionType;

    @TableField("source_instance_id")
    private Long sourceInstanceId;

    @TableField("source_port_ref")
    private String sourcePortRef;

    @TableField("target_instance_id")
    private Long targetInstanceId;

    @TableField("target_port_ref")
    private String targetPortRef;

    @TableField("description")
    private String description;

    @TableField("create_time")
    private OffsetDateTime createTime;

}


````

---

## Backend/src/main/java/com/smartlab/management/entity/resource/scene/SceneDetail.java

````text
package com.smartlab.management.entity.resource.scene;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"SCENE_DETAIL\"", autoResultMap = false)
/**
 * 场景明细控制指令实体。对应 SCENE_DETAILS 表，保存场景一键启动时关联每个设备实例应发出的标准目标指令。
 */
public class SceneDetail {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("scene_id")
    private Long sceneId;

    @TableField("device_instance_id")
    private Long deviceInstanceId;

    @TableField("pos_x")
    private Double posX;

    @TableField("pos_y")
    private Double posY;

    @TableField("pos_z")
    private Double posZ;

    @TableField("create_time")
    private OffsetDateTime createTime;

}


````

---

## Backend/src/main/java/com/smartlab/management/entity/resource/scene/SceneMain.java

````text
package com.smartlab.management.entity.resource.scene;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"SCENE_MAIN\"", autoResultMap = false)
/**
 * 场景模板主表实体。对应 SCENE_MAIN 表，存储复合设备协同场景的方案名称与执行状态。
 */
public class SceneMain {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("scene_name")
    private String sceneName;

    @TableField("description")
    private String description;

    @TableField("scene_picture")
    private String scenePicture;

    @TableField("update_time")
    private OffsetDateTime updateTime;

    @TableField("create_time")
    private OffsetDateTime createTime;

}


````

---

## Backend/src/main/java/com/smartlab/management/entity/user/PermissionInfo.java

````text
package com.smartlab.management.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName(value = "\"PERMISSION_INFO\"", autoResultMap = false)
/**
 * 系统权限项实体。对应 PERMISSION_INFO 表，划定细粒度接口控制的RBAC权限码。
 */
public class PermissionInfo {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("scope")
    private String scope;

    @TableField("object")
    private String object;

    @TableField("action")
    private String action;

    @TableField("permission_level")
    private Integer permissionLevel;

    @TableField("permis_desc")
    private String permisDesc;

}

````

---

## Backend/src/main/java/com/smartlab/management/entity/user/UserInfo.java

````text
package com.smartlab.management.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.common.PostgresJsonbTypeHandler;
import lombok.Data;

@Data
@TableName(value = "\"USER_INFO\"", autoResultMap = true)
/**
 * 系统用户账户实体。对应 USER_INFO 表，存储登录密码、所属实验室及绑定角色。
 */
public class UserInfo {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_name")
    private String userName;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("user_basicinfo")
    private String userBasicinfo;

    @TableField("role")
    private String role;

    public String getRoleName() {
        return role;
    }

    @JsonSetter("roleName")
    public void setRoleName(String roleName) {
        this.role = roleName;
    }

    @TableField("lab")
    private String lab;

    @TableField("user_level")
    private Integer userLevel;

    @TableField("is_privileged_user")
    private Boolean isPrivilegedUser;

    @TableField(value = "privileged", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode privileged;

}

````

---

## Backend/src/main/java/com/smartlab/management/entity/workflow/FlowModels.java

````text
package com.smartlab.management.entity.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.common.PostgresJsonbTypeHandler;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"FLOW_MODELS\"", autoResultMap = true)
/**
 * 实验工作流模板定义实体。对应 FLOW_MODELS 表，保存基于流程图编排的XML或JSON流拓扑结构。
 */
public class FlowModels {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    public String getTemplateId() {
        return id == null ? null : String.valueOf(id);
    }

    @JsonSetter("templateId")
    public void setTemplateId(String templateId) {
        if (templateId != null && !templateId.isBlank()) {
            this.id = Long.valueOf(templateId);
        }
    }

    @TableField("flow_name")
    private String flowName;

    public String getTemplateName() {
        return flowName;
    }

    @JsonSetter("templateName")
    public void setTemplateName(String templateName) {
        this.flowName = templateName;
    }

    @TableField("version")
    private Integer version;

    @TableField("predecessor_id")
    private Long predecessorId;

    @TableField("status")
    private String status;

    @TableField("description")
    private String description;

    @TableField(value = "nodes", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode nodes;

    public JsonNode getNodesDef() {
        return nodes;
    }

    @JsonSetter("nodesDef")
    public void setNodesDef(JsonNode nodesDef) {
        this.nodes = nodesDef;
    }

    @TableField(value = "interface_connection", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode interfaceConnection;

    public JsonNode getInterfaceConnections() {
        return interfaceConnection;
    }

    @JsonSetter("interfaceConnections")
    public void setInterfaceConnections(JsonNode interfaceConnections) {
        this.interfaceConnection = interfaceConnections;
    }

    @TableField(value = "port_connection", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode portConnection;

    public JsonNode getPortConnections() {
        return portConnection;
    }

    @JsonSetter("portConnections")
    public void setPortConnections(JsonNode portConnections) {
        this.portConnection = portConnections;
    }

    @TableField("creator_id")
    private Long creatorId;

    @TableField("create_time")
    private OffsetDateTime createTime;

}


````

---

## Backend/src/main/java/com/smartlab/management/entity/workflow/FlowNode.java

````text
package com.smartlab.management.entity.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.common.PostgresJsonbTypeHandler;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"FLOW_NODE\"", autoResultMap = true)
/**
 * 工作流节点配置实体。对应 FLOW_NODE 表，保存设计画布上单个行为节点的逻辑判定属性。
 */
public class FlowNode {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("flow_model_id")
    private Long flowModelId;

    @TableField("node_id_ref")
    private Long nodeIdRef;

    @TableField("node_type")
    private String nodeType;

    @TableField("sub_flow_model_id")
    private Long subFlowModelId;

    @TableField("device_model_id")
    private Long deviceModelId;

    @TableField(value = "capability", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode capability;

    @TableField(value = "in_variables", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode inVariables;

    @TableField(value = "lifecycle", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode lifecycle;

    @TableField(value = "interfaces", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode interfaces;

    @TableField(value = "ports", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode ports;

    @TableField(value = "actions", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode actions;

    @TableField("create_time")
    private OffsetDateTime createTime;

}


````

---

## Backend/src/main/java/com/smartlab/management/entity/workflow/StepLog.java

````text
package com.smartlab.management.entity.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"STEP_LOG\"", autoResultMap = false)
/**
 * 工作流节点步骤执行历史日志实体。对应 STEP_LOGS 表，保存特定工作流任务中单个节点执行成败与返回报文。
 */
public class StepLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("source_type")
    private String sourceType;

    @TableField("task_id")
    private Long taskId;

    @TableField("device_instance_id")
    private Long deviceInstanceId;

    @TableField("task_step_id")
    private Long taskStepId;

    @TableField("log_level")
    private String logLevel;

    @TableField("log_info")
    private String logInfo;

    @TableField("log_time")
    private OffsetDateTime logTime;

}


````

---

## Backend/src/main/java/com/smartlab/management/entity/workflow/Task.java

````text
package com.smartlab.management.entity.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.common.PostgresJsonbTypeHandler;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"TASK\"", autoResultMap = true)
/**
 * 实验流程执行任务主实体。对应 TASKS 表，持久化存储后台跑的协同控制链生命周期状态。
 */
public class Task {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    public Long getTaskId() {
        return id;
    }

    @JsonSetter("taskId")
    public void setTaskId(Long taskId) {
        this.id = taskId;
    }

    @TableField("flow_model_id")
    private Long flowModelId;

    public String getTemplateId() {
        return flowModelId == null ? null : String.valueOf(flowModelId);
    }

    @JsonSetter("templateId")
    public void setTemplateId(String templateId) {
        if (templateId != null && !templateId.isBlank()) {
            this.flowModelId = Long.valueOf(templateId);
        }
    }

    @TableField("task_name")
    private String taskName;

    @TableField("task_desc")
    private String taskDesc;

    @TableField("parent_task_id")
    private Long parentTaskId;

    @TableField("task_status")
    private String taskStatus;

    public String getCurrentStatus() {
        return taskStatus;
    }

    @JsonSetter("currentStatus")
    public void setCurrentStatus(String currentStatus) {
        this.taskStatus = currentStatus;
    }

    @TableField(value = "task_constraints", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode taskConstraints;

    @TableField("current_flow_node_id")
    private Long currentFlowNodeId;

    @TableField("current_node_id_ref")
    private Long currentNodeIdRef;

    public JsonNode getGlobalConstraints() {
        return taskConstraints;
    }

    @JsonSetter("globalConstraints")
    public void setGlobalConstraints(JsonNode globalConstraints) {
        this.taskConstraints = globalConstraints;
    }

    @TableField(value = "resource_map", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode resourceMap;

    @TableField(value = "task_variables", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode taskVariables;

    @TableField("creator_id")
    private Long creatorId;

    @TableField("start_time")
    private OffsetDateTime startTime;

    @TableField("end_time")
    private OffsetDateTime endTime;

}


````

---

## Backend/src/main/java/com/smartlab/management/entity/workflow/TaskStep.java

````text
package com.smartlab.management.entity.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.common.PostgresJsonbTypeHandler;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"TASK_STEP\"", autoResultMap = true)
/**
 * 任务节点执行明细实体。对应 TASK_STEPS 表，记录特定任务中每个被实例化执行节点的实时调度属性。
 */
public class TaskStep {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    @TableField("flow_node_id")
    private Long flowNodeId;

    @TableField("node_id_ref")
    private Long nodeIdRef;

    @TableField("parent_step_id")
    private Long parentStepId;

    @TableField("step_depth")
    private Integer stepDepth;

    @TableField("node_status")
    private String nodeStatus;

    @TableField(value = "interface_in_snapshot", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode interfaceInSnapshot;

    @TableField(value = "interface_out_snapshot", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode interfaceOutSnapshot;

    @TableField(value = "port_in_snapshot", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode portInSnapshot;

    @TableField(value = "port_out_snapshot", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode portOutSnapshot;

    @TableField(value = "variable_space", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode variableSpace;

    @TableField("start_time")
    private OffsetDateTime startTime;

    @TableField("end_time")
    private OffsetDateTime endTime;

    @TableField("duration_ms")
    private Long durationMs;

}


````

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

## Backend/src/main/java/com/smartlab/management/service/db/common/ManagementCrudService.java

````text
package com.smartlab.management.service.db.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlab.management.dto.common.PageResult;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 管理模块通用 CRUD 基类。
 * 该类只封装数据库表的基础增删改查，不承载设备控制、工作流执行等业务引擎逻辑。
 */
public abstract class ManagementCrudService<T> {

    private final BaseMapper<T> mapper;

    protected ManagementCrudService(BaseMapper<T> mapper) {
        this.mapper = mapper;
    }

    protected BaseMapper<T> mapper() {
        return mapper;
    }

    public List<T> list() {
        return mapper.selectList(new QueryWrapper<T>().orderByDesc("id"));
    }

    public PageResult<T> page(long pageNo, long pageSize, String keyword, String... keywordColumns) {
        QueryWrapper<T> query = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank() && keywordColumns != null && keywordColumns.length > 0) {
            query.and(wrapper -> {
                for (String column : keywordColumns) {
                    wrapper.or().like(column, keyword.trim());
                }
            });
        }
        query.orderByDesc("id");
        Page<T> page = mapper.selectPage(new Page<>(Math.max(1, pageNo), Math.max(1, pageSize)), query);
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    public T getById(Serializable id) {
        return mapper.selectById(id);
    }

    public long countAll() {
        Long count = mapper.selectCount(new QueryWrapper<>());
        return count == null ? 0 : count;
    }

    public T save(T entity) {
        Serializable id = readId(entity);
        OffsetDateTime now = OffsetDateTime.now();
        if (id == null) {
            invokeSetter(entity, "setCreateTime", now);
            invokeSetter(entity, "setUpdateTime", now);
            mapper.insert(entity);
        } else {
            invokeSetter(entity, "setUpdateTime", now);
            mapper.updateById(entity);
        }
        return entity;
    }

    public void delete(Serializable id) {
        mapper.deleteById(id);
    }

    protected Long parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.valueOf(value);
    }

    private Serializable readId(T entity) {
        try {
            Method method = entity.getClass().getMethod("getId");
            Object value = method.invoke(entity);
            return value instanceof Serializable serializable ? serializable : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void invokeSetter(T entity, String methodName, OffsetDateTime value) {
        try {
            Method method = entity.getClass().getMethod(methodName, OffsetDateTime.class);
            method.invoke(entity, value);
        } catch (Exception ignored) {
            // 不是所有表都有 create_time/update_time 字段。
        }
    }

}



````

---

## Backend/src/main/java/com/smartlab/management/service/db/constraint/ConstraintRuleService.java

````text
package com.smartlab.management.service.db.constraint;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.constraint.ConstraintRule;
import com.smartlab.management.mapper.constraint.ConstraintRuleMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 约束规则表服务。
 *
 * CONSTRAINT_RULE 是规则集合表，一行数据表示一条约束规则。
 * 它不直接等同于 constraint-model.json 的完整模型文件；后续导出模型时，
 * 后端可基于多行规则组装 observableObjects 与 constraints。
 */
@Service
/**
 * 设备安全联锁控制与违规检测持久层核心服务。
 */
public class ConstraintRuleService extends ManagementCrudService<ConstraintRule> {

    private static final Set<String> SOURCE_TYPES = Set.of(
            "DEVICE_ATTRIBUTE",
            "DEVICE_OPERATION_STATE",
            "DEVICE_COMMAND_LIFECYCLE",
            "NODE_LIFECYCLE_STATE"
    );

    private static final Set<String> OPERATORS = Set.of("GT", "LT", "GE", "LE", "EQ", "NE", "BETWEEN", "IN");

    private static final Set<String> SYSTEM_ACTIONS = Set.of("HALT", "PAUSE", "ALERT", "LOG_ONLY");

    private final ConstraintRuleMapper mapper;

    public ConstraintRuleService(ConstraintRuleMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    public List<ConstraintRule> list(String sourceType, String objectEndpoint, Boolean isEnabled) {
        QueryWrapper<ConstraintRule> query = baseQuery(sourceType, objectEndpoint, null, null, isEnabled);
        query.orderByDesc("create_time", "id");
        return mapper.selectList(query);
    }

    public PageResult<ConstraintRule> page(long pageNo,
                                           long pageSize,
                                           String keyword,
                                           String sourceType,
                                           String objectEndpoint,
                                           String objectName,
                                           String operator,
                                           Boolean isEnabled) {
        QueryWrapper<ConstraintRule> query = baseQuery(sourceType, objectEndpoint, objectName, operator, isEnabled);
        if (hasText(keyword)) {
            String value = keyword.trim();
            query.and(wrapper -> wrapper
                    .like("rule_name", value)
                    .or()
                    .like("source_type", value)
                    .or()
                    .like("object_endpoint", value)
                    .or()
                    .like("object_name", value)
                    .or()
                    .like("operator", value)
                    .or()
                    .like("threshold", value)
                    .or()
                    .like("description", value));
        }
        query.orderByDesc("create_time", "id");
        Page<ConstraintRule> page = mapper.selectPage(new Page<>(Math.max(1, pageNo), Math.max(1, pageSize)), query);
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    public ConstraintRule setEnabled(Long id, boolean enabled) {
        ConstraintRule rule = getById(id);
        if (rule == null) {
            throw new IllegalArgumentException("约束规则不存在");
        }
        rule.setIsEnabled(enabled);
        mapper.updateById(rule);
        return rule;
    }

    public Map<String, Object> options() {
        return Map.of(
                "sourceTypes", SOURCE_TYPES,
                "operators", OPERATORS,
                "systemViolationActions", SYSTEM_ACTIONS
        );
    }

    @Override
    public ConstraintRule save(ConstraintRule entity) {
        normalize(entity);
        validate(entity);
        return super.save(entity);
    }

    private QueryWrapper<ConstraintRule> baseQuery(String sourceType,
                                                   String objectEndpoint,
                                                   String objectName,
                                                   String operator,
                                                   Boolean isEnabled) {
        QueryWrapper<ConstraintRule> query = new QueryWrapper<>();
        eqIfPresent(query, "source_type", sourceType);
        eqIfPresent(query, "object_endpoint", objectEndpoint);
        eqIfPresent(query, "object_name", objectName);
        eqIfPresent(query, "operator", operator);
        if (isEnabled != null) {
            query.eq("is_enabled", isEnabled);
        }
        return query;
    }

    private void normalize(ConstraintRule entity) {
        if (entity == null) {
            throw new IllegalArgumentException("约束规则不能为空");
        }
        entity.setRuleName(trim(entity.getRuleName()));
        entity.setSourceType(upperTrim(entity.getSourceType()));
        entity.setObjectEndpoint(trim(entity.getObjectEndpoint()));
        entity.setObjectName(trim(entity.getObjectName()));
        entity.setOperator(upperTrim(entity.getOperator()));
        entity.setThreshold(trim(entity.getThreshold()));
        entity.setDescription(trim(entity.getDescription()));
        if (entity.getIsEnabled() == null) {
            entity.setIsEnabled(Boolean.TRUE);
        }
    }

    private void validate(ConstraintRule entity) {
        requireText(entity.getRuleName(), "约束名称不能为空");
        requireText(entity.getSourceType(), "来源类型不能为空");
        requireText(entity.getObjectEndpoint(), "监控对象端点不能为空");
        requireText(entity.getObjectName(), "约束对象名称不能为空");
        requireText(entity.getOperator(), "比较符不能为空");
        requireText(entity.getThreshold(), "阈值界限不能为空");

        if (!SOURCE_TYPES.contains(entity.getSourceType())) {
            throw new IllegalArgumentException("来源类型不符合约束模型规范: " + entity.getSourceType());
        }
        if (!OPERATORS.contains(entity.getOperator())) {
            throw new IllegalArgumentException("比较符不符合协议字典规范: " + entity.getOperator());
        }
        validateViolationActions(entity.getViolationActions());
    }

    private void validateViolationActions(JsonNode actions) {
        if (actions == null || !actions.isArray() || actions.isEmpty()) {
            throw new IllegalArgumentException("违规触发动作集必须是非空数组");
        }
        for (int i = 0; i < actions.size(); i++) {
            JsonNode action = actions.get(i);
            if (action == null || !action.isObject()) {
                throw new IllegalArgumentException("违规触发动作第 " + (i + 1) + " 项必须是对象");
            }
            String actionType = text(action, "actionType");
            if (!hasText(actionType)) {
                throw new IllegalArgumentException("违规触发动作第 " + (i + 1) + " 项缺少 actionType");
            }
            if ("SYSTEM".equals(actionType)) {
                validateSystemAction(action, i);
            } else if ("DEVICE_CAPABILITY".equals(actionType)) {
                validateDeviceCapabilityAction(action, i);
            } else {
                throw new IllegalArgumentException("违规触发动作第 " + (i + 1) + " 项 actionType 不符合约束模型规范: " + actionType);
            }
        }
    }

    private void validateSystemAction(JsonNode action, int index) {
        String systemAction = text(action, "action");
        if (!hasText(systemAction)) {
            throw new IllegalArgumentException("系统违规动作第 " + (index + 1) + " 项缺少 action");
        }
        if (!SYSTEM_ACTIONS.contains(systemAction)) {
            throw new IllegalArgumentException("系统违规动作第 " + (index + 1) + " 项 action 不符合协议字典规范: " + systemAction);
        }
    }

    private void validateDeviceCapabilityAction(JsonNode action, int index) {
        JsonNode deviceInstanceId = action.get("deviceInstanceId");
        if (deviceInstanceId == null || !deviceInstanceId.canConvertToLong()) {
            throw new IllegalArgumentException("设备能力动作第 " + (index + 1) + " 项缺少有效的 deviceInstanceId");
        }
        if (!hasText(text(action, "capabilityName"))) {
            throw new IllegalArgumentException("设备能力动作第 " + (index + 1) + " 项缺少 capabilityName");
        }
        JsonNode parameters = action.get("parameters");
        if (parameters != null && !parameters.isObject()) {
            throw new IllegalArgumentException("设备能力动作第 " + (index + 1) + " 项 parameters 必须是对象");
        }
    }

    private void eqIfPresent(QueryWrapper<ConstraintRule> query, String column, String value) {
        if (hasText(value)) {
            query.eq(column, value.trim());
        }
    }

    private void requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String upperTrim(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
````

---

## Backend/src/main/java/com/smartlab/management/service/db/constraint/ViolationLogService.java

````text
package com.smartlab.management.service.db.constraint;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.constraint.ViolationLog;
import com.smartlab.management.mapper.constraint.ViolationLogMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * 约束违规日志表服务。
 * 对应 VIOLATION_LOG 表，只负责日志查询和人工维护，不执行约束判断。
 */
@Service
/**
 * ViolationLog业务持久层核心操作服务。
 */
public class ViolationLogService extends ManagementCrudService<ViolationLog> {

    private final ViolationLogMapper mapper;

    public ViolationLogService(ViolationLogMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    public PageResult<ViolationLog> page(long pageNo,
                                         long pageSize,
                                         String keyword,
                                         Long constraintRuleId,
                                         Long taskId,
                                         Long deviceInstanceId) {
        QueryWrapper<ViolationLog> query = new QueryWrapper<>();
        if (constraintRuleId != null) {
            query.eq("constraint_rule_id", constraintRuleId);
        }
        if (taskId != null) {
            query.eq("task_id", taskId);
        }
        if (deviceInstanceId != null) {
            query.eq("device_instance_id", deviceInstanceId);
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            query.and(wrapper -> wrapper
                    .like("observed_variable", value)
                    .or()
                    .like("action_taken", value)
                    .or()
                    .like("constraint_type", value));
        }
        query.orderByDesc("violation_time", "id");
        Page<ViolationLog> page = mapper.selectPage(new Page<>(Math.max(1, pageNo), Math.max(1, pageSize)), query);
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    @Override
    public ViolationLog save(ViolationLog entity) {
        if (entity.getViolationTime() == null) {
            entity.setViolationTime(OffsetDateTime.now());
        }
        return super.save(entity);
    }
}

````

---

## Backend/src/main/java/com/smartlab/management/service/db/resource/adapter/AdapterIndexService.java

````text
package com.smartlab.management.service.db.resource.adapter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import com.smartlab.management.mapper.resource.adapter.AdapterIndexMapper;
import com.smartlab.adapter.AdapterManifestService;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adapter 索引表服务。
 * 对应 ADAPTER_INDEX 表，用于维护设备执行代理的注册配置与在线状态。
 */
@Service
/**
 * 适配器网关静态注册与模板加载持久层业务服务。
 */
public class AdapterIndexService extends ManagementCrudService<AdapterIndex> {

    private final AdapterIndexMapper mapper;
    private final AdapterManifestService manifestService;

    public AdapterIndexService(AdapterIndexMapper mapper,
                               AdapterManifestService manifestService) {
        super(mapper);
        this.mapper = mapper;
        this.manifestService = manifestService;
    }

    /**
     * 查询全部 Adapter 索引，按更新时间倒序排列。
     */
    @Override
    public List<AdapterIndex> list() {
        return mapper.selectList(Wrappers.<AdapterIndex>lambdaQuery().orderByDesc(AdapterIndex::getUpdateTime, AdapterIndex::getId));
    }

    /**
     * 按 Adapter 标识名查询注册记录。
     */
    public AdapterIndex getByName(String adapterName) {
        if (adapterName == null || adapterName.isBlank()) {
            return null;
        }
        return mapper.selectOne(
                Wrappers.<AdapterIndex>lambdaQuery()
                        .eq(AdapterIndex::getAdapterName, adapterName.trim())
                        .last("limit 1")
        );
    }

    /**
     * 保存 Adapter 索引，自动维护创建时间和更新时间。
     */
    @Override
    public AdapterIndex save(AdapterIndex entity) {
        if (entity.getAdapterName() == null || entity.getAdapterName().isBlank()) {
            throw new IllegalArgumentException("Adapter 标识名不能为空");
        }
        entity.setAdapterName(entity.getAdapterName().trim());
        if (entity.getParsedConfig() != null) {
            manifestService.validate(entity.getParsedConfig());
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (entity.getId() == null) {
            entity.setCreateTime(now);
            if (entity.getStatus() == null || entity.getStatus().isBlank()) {
                entity.setStatus("UNKNOWN");
            }
        }
        entity.setUpdateTime(now);
        return super.save(entity);
    }

    /**
     * 解析 Adapter 注册报文但不落库，用于建模页面预览。
     */
    public ObjectNode previewRegisterPayload(Map<String, Object> payload) {
        String adapterName = stringValue(payload.get("adapterName"));
        String format = stringValue(payload.getOrDefault("rawConfigFormat", "JSON"));
        String content = stringValue(payload.get("rawConfigContent"));
        return manifestService.parseRawConfig(adapterName, format, content);
    }

    /**
     * 保存 AdapterRegisterRequest。rawConfigContent 是 adapter 自己提供的 manifest。
     */
    public AdapterIndex register(Map<String, Object> payload) {
        ObjectNode manifest = manifestFromRegisterPayload(payload);
        String adapterName = manifest.path("adapterName").asText();
        AdapterIndex adapter = getByName(adapterName);
        if (adapter == null) {
            adapter = new AdapterIndex();
            adapter.setAdapterName(adapterName);
            adapter.setCreateTime(OffsetDateTime.now());
        }
        adapter.setOriginalConfig(stringValue(payload.get("rawConfigContent")));
        adapter.setParsedConfig(manifest);
        adapter.setStatus(stringValue(payload.getOrDefault("status", "REGISTERED")));
        adapter.setUpdateTime(OffsetDateTime.now());
        return save(adapter);
    }

    private ObjectNode manifestFromRegisterPayload(Map<String, Object> payload) {
        Object reviewed = payload.get("parsedConfig");
        if (reviewed instanceof ObjectNode objectNode) {
            manifestService.validate(objectNode);
            return objectNode;
        }
        if (reviewed instanceof JsonNode jsonNode) {
            if (!jsonNode.isObject()) {
                throw new IllegalArgumentException("审阅后的 Adapter 配置必须是 JSON 对象");
            }
            ObjectNode objectNode = jsonNode.deepCopy();
            manifestService.validate(objectNode);
            return objectNode;
        }
        if (reviewed != null) {
            JsonNode node = JsonNodeSupport.MAPPER.valueToTree(reviewed);
            if (!node.isObject()) {
                throw new IllegalArgumentException("审阅后的 Adapter 配置必须是 JSON 对象");
            }
            ObjectNode objectNode = (ObjectNode) node;
            manifestService.validate(objectNode);
            return objectNode;
        }
        return previewRegisterPayload(payload);
    }

    /**
     * 查询 Adapter 的模板列表。
     */
    public ArrayNode listTemplates(String adapterName) {
        AdapterIndex adapter = requireAdapter(adapterName);
        ArrayNode templates = JsonNodeSupport.arrayNode();
        for (JsonNode category : adapter.getParsedConfig().path("deviceCategories")) {
            ObjectNode template = category.path("deviceTemplate").deepCopy();
            template.put("categoryName", category.path("categoryName").asText(""));
            template.put("categoryDescription", category.path("categoryDescription").asText(""));
            templates.add(template);
        }
        return templates;
    }

    public ArrayNode listAdapterCategories(String adapterName) {
        AdapterIndex adapter = requireAdapter(adapterName);
        ArrayNode categories = JsonNodeSupport.arrayNode();
        for (JsonNode category : adapter.getParsedConfig().path("deviceCategories")) {
            categories.add(category);
        }
        return categories;
    }

    public ArrayNode listDevicePoints(String adapterName, String templateName) {
        return listDevicePoints(adapterName, templateName, null);
    }

    public ArrayNode listDevicePoints(String adapterName, String templateName, String categoryName) {
        AdapterIndex adapter = requireAdapter(adapterName);
        ArrayNode points = JsonNodeSupport.arrayNode();
        JsonNode category = manifestService.findCategory(adapter.getParsedConfig(), firstNonBlank(categoryName, templateName));
        if (category != null) {
            for (JsonNode point : category.path("devicePoints")) {
                points.add(point);
            }
            return points;
        }
        return points;
    }

    public ObjectNode buildAdapterContract(String adapterName, String templateName) {
        return buildAdapterContract(adapterName, templateName, null);
    }

    public ObjectNode buildAdapterContract(String adapterName, String templateName, String categoryName) {
        return manifestService.buildAdapterContract(requireAdapter(adapterName), firstNonBlank(categoryName, templateName));
    }
    public AdapterIndex requireAdapter(String adapterName) {
        AdapterIndex adapter = getByName(adapterName);
        if (adapter == null || adapter.getParsedConfig() == null) {
            throw new IllegalArgumentException("Adapter 未注册或没有解析后的配置: " + Objects.toString(adapterName, ""));
        }
        return adapter;
    }

    /**
     * 记录 Adapter 心跳并更新状态。
     */
    public AdapterIndex heartbeat(String adapterName, String status) {
        AdapterIndex adapter = getByName(adapterName);
        if (adapter == null) {
            adapter = new AdapterIndex();
            adapter.setAdapterName(adapterName);
        }
        adapter.setStatus(status == null || status.isBlank() ? "ONLINE" : status);
        adapter.setLastHeartbeat(OffsetDateTime.now());
        return save(adapter);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}

````

---

## Backend/src/main/java/com/smartlab/management/service/db/resource/data/DataIndexService.java

````text
package com.smartlab.management.service.db.resource.data;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.management.entity.resource.data.DataIndex;
import com.smartlab.management.entity.resource.data.DataTemplateDetail;
import com.smartlab.management.entity.resource.data.DataTemplateMain;
import com.smartlab.management.entity.resource.device.PropertyType;
import com.smartlab.management.mapper.resource.data.DataIndexMapper;
import com.smartlab.management.mapper.resource.data.DataTemplateDetailMapper;
import com.smartlab.management.mapper.resource.data.DataTemplateMainMapper;
import com.smartlab.management.mapper.resource.device.PropertyTypeMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 数据索引表服务。
 *
 * 对应 DATA_INDEX 表，负责数据集索引和物理数据表的创建生命周期。
 */
@Service
/**
 * DataIndex业务持久层核心操作服务。
 */
public class DataIndexService extends ManagementCrudService<DataIndex> {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final int DEFAULT_VARCHAR_LENGTH = 255;

    private final DataIndexMapper mapper;
    private final DataTemplateMainMapper templateMainMapper;
    private final DataTemplateDetailMapper templateDetailMapper;
    private final PropertyTypeMapper propertyTypeMapper;
    private final JdbcTemplate jdbcTemplate;

    public DataIndexService(DataIndexMapper mapper,
                            DataTemplateMainMapper templateMainMapper,
                            DataTemplateDetailMapper templateDetailMapper,
                            PropertyTypeMapper propertyTypeMapper,
                            JdbcTemplate jdbcTemplate) {
        super(mapper);
        this.mapper = mapper;
        this.templateMainMapper = templateMainMapper;
        this.templateDetailMapper = templateDetailMapper;
        this.propertyTypeMapper = propertyTypeMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DataIndex> listByDeviceInstance(Long deviceInstanceId) {
        if (deviceInstanceId == null) {
            return list();
        }
        return mapper.selectList(
                Wrappers.<DataIndex>lambdaQuery()
                        .eq(DataIndex::getDeviceInstanceId, deviceInstanceId)
                        .orderByDesc(DataIndex::getId)
        );
    }

    public List<DataIndex> listByTemplate(Long templateId) {
        if (templateId == null) {
            return list();
        }
        return mapper.selectList(
                Wrappers.<DataIndex>lambdaQuery()
                        .eq(DataIndex::getDataTemplateId, templateId)
                        .orderByDesc(DataIndex::getId)
        );
    }

    public long countByDeviceInstance(Long deviceInstanceId) {
        if (deviceInstanceId == null) {
            return 0;
        }
        Long count = mapper.selectCount(
                Wrappers.<DataIndex>lambdaQuery().eq(DataIndex::getDeviceInstanceId, deviceInstanceId)
        );
        return count == null ? 0 : count;
    }

    public long countByTemplate(Long templateId) {
        if (templateId == null) {
            return 0;
        }
        Long count = mapper.selectCount(
                Wrappers.<DataIndex>lambdaQuery().eq(DataIndex::getDataTemplateId, templateId)
        );
        return count == null ? 0 : count;
    }

    /**
     * 创建一个数据集：写 DATA_INDEX，并按模板字段创建对应的 DATA_RECORD_xxxx 物理表。
     */
    @Transactional(rollbackFor = Exception.class)
    public DataIndex createDataSet(Long templateId, Long deviceInstanceId, String dataDesc) {
        if (templateId == null) {
            throw new IllegalArgumentException("数据模板ID不能为空");
        }
        DataTemplateMain template = templateMainMapper.selectById(templateId);
        if (template == null) {
            throw new IllegalArgumentException("数据模板不存在");
        }
        List<DataTemplateDetail> details = templateDetailMapper.selectList(
                Wrappers.<DataTemplateDetail>lambdaQuery()
                        .eq(DataTemplateDetail::getDataTemplateId, templateId)
                        .orderByAsc(DataTemplateDetail::getId)
        );
        if (details.isEmpty()) {
            throw new IllegalArgumentException("数据模板没有字段明细，无法创建物理数据表");
        }

        DataIndex index = new DataIndex();
        index.setDataTemplateId(templateId);
        index.setDeviceInstanceId(deviceInstanceId);
        index.setDataTable(generateDataTableName());
        index.setDataDesc(resolveDataDesc(dataDesc, template));
        index.setCreateTime(OffsetDateTime.now());
        mapper.insert(index);

        createPhysicalRecordTable(index, details);
        return index;
    }

    /**
     * 设备实例创建后，根据设备模型的默认模板自动创建该设备实例的数据集。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<DataIndex> createDefaultDataSetsForDeviceInstance(Long deviceModelId, Long deviceInstanceId, String instanceName) {
        if (deviceModelId == null || deviceInstanceId == null) {
            return List.of();
        }
        List<DataTemplateMain> defaultTemplates = templateMainMapper.selectList(
                Wrappers.<DataTemplateMain>lambdaQuery()
                        .eq(DataTemplateMain::getDeviceModelId, deviceModelId)
                        .eq(DataTemplateMain::getIsDefault, true)
                        .orderByAsc(DataTemplateMain::getId)
        );
        return defaultTemplates.stream()
                .filter(template -> !hasDataSet(deviceInstanceId, template.getId()))
                .map(template -> createDataSet(
                        template.getId(),
                        deviceInstanceId,
                        defaultDataDesc(instanceName, template.getTemplateName())
                ))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataIndex save(DataIndex entity) {
        if (entity.getId() == null) {
            return createDataSet(entity.getDataTemplateId(), entity.getDeviceInstanceId(), entity.getDataDesc());
        }
        return super.save(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(java.io.Serializable id) {
        if (id == null || String.valueOf(id).isBlank()) {
            throw new IllegalArgumentException("数据集ID不能为空");
        }
        Long dataIndexId = Long.valueOf(String.valueOf(id));
        DataIndex index = mapper.selectById(dataIndexId);
        if (index == null) {
            throw new IllegalArgumentException("数据集不存在");
        }
        if (index.getDataTable() == null || index.getDataTable().isBlank()) {
            throw new IllegalStateException("数据集缺少物理表名，无法删除");
        }
        jdbcTemplate.execute("drop table if exists " + quoteIdentifier(index.getDataTable()));
        mapper.deleteById(dataIndexId);
    }

    private boolean hasDataSet(Long deviceInstanceId, Long templateId) {
        Long count = mapper.selectCount(
                Wrappers.<DataIndex>lambdaQuery()
                        .eq(DataIndex::getDeviceInstanceId, deviceInstanceId)
                        .eq(DataIndex::getDataTemplateId, templateId)
        );
        return count != null && count > 0;
    }

    private void createPhysicalRecordTable(DataIndex index, List<DataTemplateDetail> details) {
        Map<Long, PropertyType> typeMap = loadPropertyTypes();
        Set<String> usedColumns = new HashSet<>(Set.of("id", "data_index_id", "create_time"));
        StringBuilder sql = new StringBuilder();
        sql.append("create table ").append(quoteIdentifier(index.getDataTable())).append(" (")
                .append(quoteIdentifier("id")).append(" bigserial primary key, ")
                .append(quoteIdentifier("data_index_id")).append(" integer not null default ").append(index.getId()).append(", ");

        for (DataTemplateDetail detail : details) {
            String columnName = normalizeIdentifier(detail.getColumnName(), "数据字段名");
            if (!usedColumns.add(columnName)) {
                    throw new IllegalArgumentException("数据模板字段名重复或占用系统字段: " + detail.getColumnName());
            }
            String sqlType = resolveSqlType(detail, typeMap);
            sql.append(quoteIdentifier(columnName))
                    .append(" ")
                    .append(sqlType)
                    .append(defaultClause(detail, sqlType))
                    .append(", ");
        }

        sql.append(quoteIdentifier("create_time")).append(" timestamptz not null default now())");
        jdbcTemplate.execute(sql.toString());
        jdbcTemplate.execute("create index " + quoteIdentifier(index.getDataTable() + "_idx_time")
                + " on " + quoteIdentifier(index.getDataTable()) + " (" + quoteIdentifier("create_time") + ")");
        jdbcTemplate.execute("create index " + quoteIdentifier(index.getDataTable() + "_idx_data_index")
                + " on " + quoteIdentifier(index.getDataTable()) + " (" + quoteIdentifier("data_index_id") + ")");
    }

    private Map<Long, PropertyType> loadPropertyTypes() {
        Map<Long, PropertyType> result = new HashMap<>();
        for (PropertyType type : propertyTypeMapper.selectList(Wrappers.<PropertyType>lambdaQuery())) {
            if (type.getId() != null) {
                result.put(type.getId(), type);
            }
        }
        return result;
    }

    private String resolveSqlType(DataTemplateDetail detail, Map<Long, PropertyType> typeMap) {
        PropertyType propertyType = typeMap.get(detail.getPropertyTypeId());
        String dbType = propertyType == null ? null : propertyType.getDbType();
        if (dbType == null || dbType.isBlank()) {
            return "text";
        }
        String normalized = dbType.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return switch (normalized) {
            case "int", "int4", "integer" -> "integer";
            case "bigint", "int8", "long" -> "bigint";
            case "numeric", "decimal" -> "numeric";
            case "real", "float4" -> "real";
            case "double", "float8", "double precision" -> "double precision";
            case "bool", "boolean" -> "boolean";
            case "date" -> "date";
            case "time" -> "time";
            case "datetime", "timestamp", "timestamp without time zone" -> "timestamp";
            case "timestamptz", "timestamp with time zone" -> "timestamptz";
            case "json", "jsonb" -> "jsonb";
            case "text" -> "text";
            case "string", "varchar", "character varying", "char", "character" ->
                    "varchar(" + varcharLength(detail.getColumnLength()) + ")";
            default -> throw new IllegalArgumentException("不支持的数据字段类型: " + dbType);
        };
    }

    private int varcharLength(Integer length) {
        if (length == null || length <= 0) {
            return DEFAULT_VARCHAR_LENGTH;
        }
        return Math.min(length, 4096);
    }

    private String defaultClause(DataTemplateDetail detail, String sqlType) {
        String value = detail.getDefaultValue();
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (isNumericType(sqlType)) {
            if (!trimmed.matches("[-+]?\\d+(\\.\\d+)?")) {
                throw new IllegalArgumentException("字段默认值不是合法数字: " + detail.getColumnName());
            }
            return " default " + trimmed;
        }
        if ("boolean".equals(sqlType)) {
            String normalized = trimmed.toLowerCase(Locale.ROOT);
            if (!List.of("true", "false").contains(normalized)) {
                throw new IllegalArgumentException("字段默认值不是合法布尔值: " + detail.getColumnName());
            }
            return " default " + normalized;
        }
        if ("jsonb".equals(sqlType)) {
            return " default " + sqlLiteral(trimmed) + "::jsonb";
        }
        return " default " + sqlLiteral(trimmed);
    }

    private boolean isNumericType(String sqlType) {
        return List.of("integer", "bigint", "numeric", "real", "double precision").contains(sqlType);
    }

    private String sqlLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private String generateDataTableName() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "data_record_" + System.currentTimeMillis() + "_" + suffix;
    }

    private String resolveDataDesc(String dataDesc, DataTemplateMain template) {
        if (dataDesc != null && !dataDesc.isBlank()) {
            return dataDesc.trim();
        }
        return template.getTemplateName() == null ? "未命名数据集" : template.getTemplateName();
    }

    private String defaultDataDesc(String instanceName, String templateName) {
        String left = instanceName == null || instanceName.isBlank() ? "设备实例" : instanceName.trim();
        String right = templateName == null || templateName.isBlank() ? "默认数据" : templateName.trim();
        return left + " - " + right;
    }

    private String normalizeIdentifier(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!SAFE_IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException(label + "只能包含字母、数字和下划线，且不能以数字开头: " + value);
        }
        return normalized;
    }

    private String quoteIdentifier(String identifier) {
        String normalized = normalizeIdentifier(identifier, "数据库标识符");
        return "\"" + normalized + "\"";
    }
}




````

---

## Backend/src/main/java/com/smartlab/management/service/db/resource/data/DataRecordService.java

````text
package com.smartlab.management.service.db.resource.data;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.resource.data.DataIndex;
import com.smartlab.management.entity.resource.data.DataTemplateDetail;
import com.smartlab.management.mapper.resource.data.DataIndexMapper;
import com.smartlab.management.mapper.resource.data.DataTemplateDetailMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 动态数据记录读写服务。
 *
 * DATA_INDEX.DATA_TABLE 指向真实物理数据表，本服务负责按数据集读写记录。
 */
@Service
/**
 * DataRecord业务持久层核心操作服务。
 */
public class DataRecordService {

    private static final Pattern SAFE_TABLE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Object MISSING_VALUE = new Object();

    private final DataIndexMapper dataIndexMapper;
    private final DataTemplateDetailMapper detailMapper;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 注入数据索引、模板字段和 JDBC 访问能力。
     */
    public DataRecordService(DataIndexMapper dataIndexMapper,
                             DataTemplateDetailMapper detailMapper,
                             JdbcTemplate jdbcTemplate) {
        this.dataIndexMapper = dataIndexMapper;
        this.detailMapper = detailMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询所有数据集索引。
     */
    public List<DataIndex> listDataSets() {
        return dataIndexMapper.selectList(Wrappers.<DataIndex>lambdaQuery().orderByDesc(DataIndex::getId));
    }

    /**
     * 按模板 ID 查询最新一个匹配数据集的记录页。
     */
    public PageResult<Map<String, Object>> pageByTemplateId(Long templateId, long pageNo, long pageSize) {
        DataIndex index = dataIndexMapper.selectOne(
                Wrappers.<DataIndex>lambdaQuery()
                        .eq(DataIndex::getDataTemplateId, templateId)
                        .orderByDesc(DataIndex::getId)
                        .last("limit 1")
        );
        if (index == null) {
            return new PageResult<>(0, pageNo, pageSize, List.of());
        }
        return pageByDataIndex(index, pageNo, pageSize);
    }

    /**
     * 按 DATA_INDEX.ID 查询指定数据集的记录页。
     */
    public PageResult<Map<String, Object>> pageByDataIndexId(Long dataIndexId, long pageNo, long pageSize) {
        DataIndex index = dataIndexMapper.selectById(dataIndexId);
        if (index == null) {
            return new PageResult<>(0, pageNo, pageSize, List.of());
        }
        return pageByDataIndex(index, pageNo, pageSize);
    }

    /**
     * 向指定数据集追加一行记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public int appendRecord(Long dataIndexId, Map<String, Object> record) {
        if (record == null || record.isEmpty()) {
            return 0;
        }
        return appendRecords(dataIndexId, List.of(record));
    }

    /**
     * 向指定数据集批量追加记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public int appendRecords(Long dataIndexId, List<Map<String, Object>> records) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        DataIndex index = requireDataIndex(dataIndexId);
        String table = quoteIdentifier(index.getDataTable());
        List<DataTemplateDetail> templateFields = loadTemplateFields(index.getDataTemplateId());
        int inserted = 0;
        for (Map<String, Object> record : records) {
            Map<String, Object> row = normalizeRecord(record, templateFields);
            if (row.isEmpty()) {
                continue;
            }
            insertRow(table, row);
            inserted++;
        }
        return inserted;
    }

    /**
     * 根据数据集索引分页读取真实物理表。
     */
    private PageResult<Map<String, Object>> pageByDataIndex(DataIndex index, long pageNo, long pageSize) {
        String table = quoteIdentifier(index.getDataTable());
        long current = Math.max(1, pageNo);
        long size = Math.max(1, pageSize);
        long offset = (current - 1) * size;
        Long total = jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "select * from " + table + " order by create_time desc limit ? offset ?",
                size,
                offset
        );
        return new PageResult<>(total == null ? 0 : total, current, size, records);
    }

    /**
     * 查询并校验数据集索引。
     */
    private DataIndex requireDataIndex(Long dataIndexId) {
        if (dataIndexId == null) {
            throw new IllegalArgumentException("数据集ID不能为空");
        }
        DataIndex index = dataIndexMapper.selectById(dataIndexId);
        if (index == null) {
            throw new IllegalArgumentException("数据集不存在: " + dataIndexId);
        }
        return index;
    }

    /**
     * 加载模板字段定义。字段定义里的 column_name 是物理表列，device_attr_key 是设备模型属性标识。
     */
    private List<DataTemplateDetail> loadTemplateFields(Long templateId) {
        return detailMapper.selectList(
                Wrappers.<DataTemplateDetail>lambdaQuery()
                        .eq(DataTemplateDetail::getDataTemplateId, templateId)
                        .orderByAsc(DataTemplateDetail::getId)
        );
    }

    /**
     * 按 device_attr_key 从记录里取值，再写入对应的物理列 column_name。
     */
    private Map<String, Object> normalizeRecord(Map<String, Object> record, List<DataTemplateDetail> templateFields) {
        if (record == null || record.isEmpty() || templateFields == null || templateFields.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> row = new LinkedHashMap<>();
        for (DataTemplateDetail detail : templateFields) {
            String column = normalizeIdentifier(detail.getColumnName(), "数据字段名");
            String sourceKey = firstNonBlank(detail.getDeviceAttrKey(), detail.getColumnName());
            Object value = valueBySourceKey(record, sourceKey);
            if (value != MISSING_VALUE) {
                row.put(column, value);
            }
        }
        return row;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }

    private Object valueBySourceKey(Map<String, Object> record, String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank()) {
            return MISSING_VALUE;
        }
        if (record.containsKey(sourceKey)) {
            return record.get(sourceKey);
        }
        String trimmed = sourceKey.trim();
        if (!trimmed.equals(sourceKey) && record.containsKey(trimmed)) {
            return record.get(trimmed);
        }
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            String key = entry.getKey();
            if (key != null && key.trim().equals(trimmed)) {
                return entry.getValue();
            }
        }
        return MISSING_VALUE;
    }

    /**
     * 执行单行动态插入。
     */
    private void insertRow(String table, Map<String, Object> row) {
        List<String> columns = new ArrayList<>(row.keySet());
        String columnSql = columns.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
        String valuesSql = columns.stream().map(column -> "?").collect(Collectors.joining(", "));
        String sql = "insert into " + table + " (" + columnSql + ") values (" + valuesSql + ")";
        Object[] values = columns.stream().map(row::get).toArray();
        jdbcTemplate.update(sql, values);
    }

    /**
     * 校验并规范化数据库标识符。
     */
    private String normalizeIdentifier(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        String normalized = value.trim().toLowerCase();
        if (!SAFE_TABLE.matcher(normalized).matches()) {
            throw new IllegalArgumentException(label + "只能包含字母、数字和下划线，且不能以数字开头: " + value);
        }
        return normalized;
    }

    /**
     * 生成安全的 PostgreSQL 标识符引用。
     */
    private String quoteIdentifier(String identifier) {
        return "\"" + normalizeIdentifier(identifier, "数据库标识符") + "\"";
    }

    /**
     * 导出数据集记录为 CSV。
     */
    public void exportCsv(Long dataIndexId, jakarta.servlet.http.HttpServletResponse response) throws Exception {
        DataIndex index = requireDataIndex(dataIndexId);
        String table = quoteIdentifier(index.getDataTable());
        
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + index.getDataTable() + ".csv\"");
        
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(response.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            writer.write('\ufeff'); // BOM for Excel
            List<DataTemplateDetail> templateFields = loadTemplateFields(index.getDataTemplateId());
            List<String> columns = new ArrayList<>();
            columns.add("create_time");
            columns.addAll(templateFields.stream()
                    .map(detail -> normalizeIdentifier(detail.getColumnName(), "数据字段名"))
                    .toList());
            
            writer.println(String.join(",", columns));
            
            jdbcTemplate.query("select * from " + table + " order by create_time desc", rs -> {
                List<String> row = new ArrayList<>();
                for (String col : columns) {
                    Object val = rs.getObject(col);
                    String valStr = val == null ? "" : val.toString().replace("\"", "\"\"");
                    row.add("\"" + valStr + "\"");
                }
                writer.println(String.join(",", row));
            });
        }
    }
}




````

---

## Backend/src/main/java/com/smartlab/management/service/db/resource/data/DataTemplateService.java

````text
package com.smartlab.management.service.db.resource.data;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.management.dto.resource.data.DataTemplateSaveDTO;
import com.smartlab.management.entity.resource.data.DataTemplateDetail;
import com.smartlab.management.entity.resource.data.DataTemplateMain;
import com.smartlab.management.mapper.resource.data.DataIndexMapper;
import com.smartlab.management.mapper.resource.data.DataTemplateDetailMapper;
import com.smartlab.management.mapper.resource.data.DataTemplateMainMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据模板表服务。
 * 对应 DATA_TEMPLATE_MAIN 和 DATA_TEMPLATE_DETAIL 两张表。
 */
@Service
/**
 * DataTemplate业务持久层核心操作服务。
 */
public class DataTemplateService extends ManagementCrudService<DataTemplateMain> {

    private final DataTemplateMainMapper mainMapper;
    private final DataTemplateDetailMapper detailMapper;
    private final DataIndexMapper dataIndexMapper;

    public DataTemplateService(DataTemplateMainMapper mainMapper,
                               DataTemplateDetailMapper detailMapper,
                               DataIndexMapper dataIndexMapper) {
        super(mainMapper);
        this.mainMapper = mainMapper;
        this.detailMapper = detailMapper;
        this.dataIndexMapper = dataIndexMapper;
    }

    @Override
    public List<DataTemplateMain> list() {
        return mainMapper.selectList(Wrappers.<DataTemplateMain>lambdaQuery().orderByDesc(DataTemplateMain::getId));
    }

    public List<DataTemplateDetail> listDetails(Long templateId) {
        return detailMapper.selectList(
                Wrappers.<DataTemplateDetail>lambdaQuery()
                        .eq(DataTemplateDetail::getDataTemplateId, templateId)
                        .orderByAsc(DataTemplateDetail::getId)
        );
    }

    public DataTemplateMain findDefaultTemplateByModelId(Long deviceModelId) {
        if (deviceModelId == null) {
            return null;
        }
        return mainMapper.selectOne(
                Wrappers.<DataTemplateMain>lambdaQuery()
                        .eq(DataTemplateMain::getDeviceModelId, deviceModelId)
                        .eq(DataTemplateMain::getIsDefault, true)
                        .orderByDesc(DataTemplateMain::getId)
                        .last("limit 1")
        );
    }

    /**
     * 按 DTO 保存数据模板主表和字段明细。
     */
    @Transactional(rollbackFor = Exception.class)
    public DataTemplateMain saveTemplate(DataTemplateSaveDTO dto) {
        if (dto == null || dto.getMain() == null) {
            throw new IllegalArgumentException("模板主表不能为空");
        }
        DataTemplateMain main = dto.getMain();
        if (main.getId() == null) {
            main.setCreateTime(OffsetDateTime.now());
            mainMapper.insert(main);
        } else {
            mainMapper.updateById(main);
            detailMapper.delete(Wrappers.<DataTemplateDetail>lambdaQuery().eq(DataTemplateDetail::getDataTemplateId, main.getId()));
        }

        for (DataTemplateDetail detail : dto.getDetails()) {
            detail.setId(null);
            detail.setDataTemplateId(main.getId());
            if (detail.getColumnLength() == null || detail.getColumnLength() <= 0) {
                detail.setColumnLength(255);
            }
            if (detail.getCreateTime() == null) {
                detail.setCreateTime(OffsetDateTime.now());
            }
            detailMapper.insert(detail);
        }
        ensureSingleDefaultTemplate(main);
        return main;
    }

    @Transactional(rollbackFor = Exception.class)
    public DataTemplateMain savePayload(Map<String, Object> payload) {
        DataTemplateMain main = new DataTemplateMain();
        Object id = first(payload, "id", "templateId");
        if (id != null && !String.valueOf(id).isBlank()) {
            main.setId(Long.valueOf(String.valueOf(id)));
        }
        main.setTemplateName(stringValue(first(payload, "templateName", "name")));
        main.setTemplateDesc(stringValue(first(payload, "templateDesc", "description")));
        Object modelId = first(payload, "deviceModelId", "modelId");
        if (modelId != null && !String.valueOf(modelId).isBlank()) {
            main.setDeviceModelId(Long.valueOf(String.valueOf(modelId)));
        }
        if (payload.containsKey("isDefault")) {
            main.setIsDefault(Boolean.valueOf(String.valueOf(payload.get("isDefault"))));
        }
        if (main.getId() == null) {
            main.setCreateTime(OffsetDateTime.now());
            mainMapper.insert(main);
        } else {
            mainMapper.updateById(main);
        }

        Object detailPayload = first(payload, "details", "columns");
        if (detailPayload instanceof List<?> details) {
            detailMapper.delete(Wrappers.<DataTemplateDetail>lambdaQuery().eq(DataTemplateDetail::getDataTemplateId, main.getId()));
            for (Object item : details) {
                if (item instanceof Map<?, ?> row) {
                    DataTemplateDetail detail = toDetail(main.getId(), row);
                    detailMapper.insert(detail);
                }
            }
        } else if (payload.get("dataSchemaSpec") instanceof Map<?, ?> schemaSpec) {
            upsertSchemaSpecDetails(main.getId(), schemaSpec);
        }
        ensureSingleDefaultTemplate(main);
        return main;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        Long templateId = parseId(id);
        Long dataSetCount = dataIndexMapper.selectCount(
                Wrappers.<com.smartlab.management.entity.resource.data.DataIndex>lambdaQuery()
                        .eq(com.smartlab.management.entity.resource.data.DataIndex::getDataTemplateId, templateId)
        );
        if (dataSetCount != null && dataSetCount > 0) {
            throw new IllegalStateException("该数据模板已生成 " + dataSetCount + " 个数据集，不能删除。请保留模板与历史数据表的解释关系。");
        }
        detailMapper.delete(Wrappers.<DataTemplateDetail>lambdaQuery().eq(DataTemplateDetail::getDataTemplateId, templateId));
        mainMapper.deleteById(templateId);
    }

    private void upsertSchemaSpecDetails(Long templateId, Map<?, ?> schemaSpec) {
        if (!schemaSpec.containsKey("fields") && !schemaSpec.containsKey("properties")) {
            return;
        }
        detailMapper.delete(Wrappers.<DataTemplateDetail>lambdaQuery().eq(DataTemplateDetail::getDataTemplateId, templateId));
        Object fields = schemaSpec.containsKey("fields") ? schemaSpec.get("fields") : schemaSpec.get("properties");
        if (fields instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> row) {
                    detailMapper.insert(toDetail(templateId, row));
                }
            }
        } else if (fields instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> row) {
                    DataTemplateDetail detail = toDetail(templateId, row);
                    if (detail.getColumnName() == null) {
                        detail.setColumnName(String.valueOf(entry.getKey()));
                    }
                    detailMapper.insert(detail);
                }
            }
        }
    }

    private DataTemplateDetail toDetail(Long templateId, Map<?, ?> row) {
        DataTemplateDetail detail = new DataTemplateDetail();
        detail.setDataTemplateId(templateId);
        detail.setColumnName(stringValue(first(row, "columnName", "fieldName", "name")));
        detail.setColumnDesc(stringValue(first(row, "columnDesc", "description", "label")));
        detail.setDeviceAttrKey(stringValue(first(row, "deviceAttrKey", "attributeId", "attrKey")));
        detail.setDefaultValue(stringValue(first(row, "defaultValue")));
        Object propertyTypeId = first(row, "propertyTypeId", "typeId");
        if (propertyTypeId != null && !String.valueOf(propertyTypeId).isBlank()) {
            detail.setPropertyTypeId(Long.valueOf(String.valueOf(propertyTypeId)));
        }
        Object length = first(row, "columnLength", "length");
        if (length != null && !String.valueOf(length).isBlank()) {
            detail.setColumnLength(Integer.valueOf(String.valueOf(length)));
        } else {
            detail.setColumnLength(255);
        }
        detail.setCreateTime(OffsetDateTime.now());
        return detail;
    }

    private void ensureSingleDefaultTemplate(DataTemplateMain main) {
        if (!Boolean.TRUE.equals(main.getIsDefault()) || main.getDeviceModelId() == null || main.getId() == null) {
            return;
        }
        mainMapper.update(
                null,
                Wrappers.<DataTemplateMain>lambdaUpdate()
                        .eq(DataTemplateMain::getDeviceModelId, main.getDeviceModelId())
                        .eq(DataTemplateMain::getIsDefault, true)
                        .ne(DataTemplateMain::getId, main.getId())
                        .set(DataTemplateMain::getIsDefault, false)
        );
    }

    private Object first(Map<?, ?> payload, String... keys) {
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



````

---

## Backend/src/main/java/com/smartlab/management/service/db/resource/data/PropertyTypeService.java

````text
package com.smartlab.management.service.db.resource.data;

import com.smartlab.management.entity.resource.device.PropertyType;
import com.smartlab.management.mapper.resource.device.PropertyTypeMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

/**
 * 字段类型表服务。
 *
 * 对应 PROPERTY_TYPE 表，用于数据模板字段的类型选择。
 */
@Service
/**
 * PropertyType业务持久层核心操作服务。
 */
public class PropertyTypeService extends ManagementCrudService<PropertyType> {

    public PropertyTypeService(PropertyTypeMapper mapper) {
        super(mapper);
    }
}




````

---

## Backend/src/main/java/com/smartlab/management/service/db/resource/device/DeviceCategoryService.java

````text
package com.smartlab.management.service.db.resource.device;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.management.entity.resource.device.DeviceCategory;
import com.smartlab.management.mapper.resource.device.DeviceCategoryMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * 设备类别表服务。
 *
 * 对应 DEVICE_CATEGORY 表，用于维护设备类别树。
 */
@Service
/**
 * DeviceCategory业务持久层核心操作服务。
 */
public class DeviceCategoryService extends ManagementCrudService<DeviceCategory> {

    private final DeviceCategoryMapper mapper;
    private final DeviceModelsMapper deviceModelsMapper;

    public DeviceCategoryService(DeviceCategoryMapper mapper, DeviceModelsMapper deviceModelsMapper) {
        super(mapper);
        this.mapper = mapper;
        this.deviceModelsMapper = deviceModelsMapper;
    }

    @Override
    public DeviceCategory save(DeviceCategory entity) {
        if (entity.getCategoryName() == null || entity.getCategoryName().isBlank()) {
            throw new IllegalArgumentException("设备类别名称不能为空");
        }
        entity.setCategoryName(entity.getCategoryName().trim());
        if (entity.getDescription() != null) {
            entity.setDescription(entity.getDescription().trim());
        }
        if (entity.getId() == null && entity.getCreateTime() == null) {
            entity.setCreateTime(OffsetDateTime.now());
        }
        return super.save(entity);
    }
    public boolean hasChildren(Long categoryId) {
        if (categoryId == null) {
            return false;
        }
        return mapper.selectCount(Wrappers.<DeviceCategory>lambdaQuery()
                .eq(DeviceCategory::getParentCategoryId, categoryId)) > 0;
    }

    public boolean hasModels(Long categoryId) {
        if (categoryId == null) {
            return false;
        }
        return deviceModelsMapper.selectCount(Wrappers.<com.smartlab.management.entity.resource.device.DeviceModels>lambdaQuery()
                .eq(com.smartlab.management.entity.resource.device.DeviceModels::getCategoryId, categoryId)) > 0;
    }

    public void requireLeafCategory(Long categoryId) {
        if (categoryId != null && hasChildren(categoryId)) {
            throw new IllegalArgumentException("设备模型只能挂在叶子类别下，请先选择最末级类别");
        }
    }

    public void delete(Long id) {
        if (id == null) {
            return;
        }
        if (hasChildren(id)) {
            throw new IllegalStateException("该类别下仍有子类别，不能删除");
        }
        if (hasModels(id)) {
            throw new IllegalStateException("该类别下仍有设备模型，不能删除");
        }
        super.delete(id);
    }
    public DeviceCategory findOrCreateByName(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return null;
        }
        DeviceCategory existing = mapper.selectOne(
                Wrappers.<DeviceCategory>lambdaQuery().eq(DeviceCategory::getCategoryName, categoryName.trim())
        );
        if (existing != null) {
            return existing;
        }
        DeviceCategory category = new DeviceCategory();
        category.setCategoryName(categoryName.trim());
        category.setCreateTime(OffsetDateTime.now());
        mapper.insert(category);
        return category;
    }
}




````

---

## Backend/src/main/java/com/smartlab/management/service/db/resource/device/DeviceComponentService.java

````text
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
````

---

## Backend/src/main/java/com/smartlab/management/service/db/resource/device/DeviceInstanceService.java

````text
package com.smartlab.management.service.db.resource.device;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.resource.device.DeviceComponents;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.management.mapper.resource.device.DeviceTwinStatesMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import com.smartlab.management.service.db.resource.data.DataIndexService;
import com.smartlab.management.service.protocol.AdapterPayloadMapperService;
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
    private final DeviceModelsMapper deviceModelsMapper;
    private final DeviceComponentService deviceComponentService;

    public DeviceInstanceService(DeviceInstancesMapper mapper,
                                 DeviceTwinStatesMapper twinStatesMapper,
                                 DataIndexService dataIndexService,
                                 AdapterPayloadMapperService protocolMapperService,
                                 DeviceModelsMapper deviceModelsMapper,
                                 DeviceComponentService deviceComponentService) {
        super(mapper);
        this.mapper = mapper;
        this.twinStatesMapper = twinStatesMapper;
        this.dataIndexService = dataIndexService;
        this.protocolMapperService = protocolMapperService;
        this.deviceModelsMapper = deviceModelsMapper;
        this.deviceComponentService = deviceComponentService;
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

    private void createDefaultTwinState(Long instanceId, Long modelId) {
        DeviceModels model = modelId == null ? null : deviceModelsMapper.selectById(modelId);
        DeviceTwinStates state = new DeviceTwinStates();
        state.setInstanceId(instanceId);
        state.setCurrentOpState(initialStateName(model == null ? null : model.getOpState(), "IDLE"));
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
                component.setStatus("未配置");
                component.setSpecification(JsonNodeSupport.objectNode());
                component.setInstallTime(OffsetDateTime.now());
                component.setCreateTime(OffsetDateTime.now());
                deviceComponentService.save(component);
            }
        }
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

````

---

## Backend/src/main/java/com/smartlab/management/service/db/resource/device/DeviceModelService.java

````text
package com.smartlab.management.service.db.resource.device;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.dto.resource.data.DataTemplateSaveDTO;
import com.smartlab.management.dto.resource.device.DeviceModelSaveDTO;
import com.smartlab.management.dto.resource.device.DeviceStateMachineSaveDTO;
import com.smartlab.management.entity.resource.data.DataTemplateDetail;
import com.smartlab.management.entity.resource.data.DataTemplateMain;
import com.smartlab.management.entity.resource.device.DeviceCategory;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.adapter.AdapterManifestService;
import com.smartlab.management.service.db.common.ManagementCrudService;
import com.smartlab.management.service.db.resource.data.DataTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 设备模型表服务。
 * 对应 DEVICE_MODELS 表，只负责模型基础资料和 JSON 字段的保存读取。
 */
@Service
/**
 * 设备物模型与状态机持久层核心服务。处理属性定义生成、标准指令动作生成及 states 保留拷贝。
 */
public class DeviceModelService extends ManagementCrudService<DeviceModels> {

    private final DeviceModelsMapper mapper;
    private final DeviceInstancesMapper deviceInstancesMapper;
    private final DeviceCategoryService deviceCategoryService;
    private final AdapterManifestService adapterManifestService;
    private final DataTemplateService dataTemplateService;

    public DeviceModelService(DeviceModelsMapper mapper,
            DeviceInstancesMapper deviceInstancesMapper,
            DeviceCategoryService deviceCategoryService,
            AdapterManifestService adapterManifestService,
            DataTemplateService dataTemplateService) {
        super(mapper);
        this.mapper = mapper;
        this.deviceInstancesMapper = deviceInstancesMapper;
        this.deviceCategoryService = deviceCategoryService;
        this.adapterManifestService = adapterManifestService;
        this.dataTemplateService = dataTemplateService;
    }

    @Override
    public List<DeviceModels> list() {
        return mapper.selectList(Wrappers.<DeviceModels>lambdaQuery().orderByDesc(DeviceModels::getId));
    }

    public PageResult<DeviceModels> page(long pageNo, long pageSize, String keyword) {
        return super.page(pageNo, pageSize, keyword, "model_name");
    }

    public DeviceModels getById(String id) {
        return mapper.selectById(parseId(id));
    }

    /**
     * 更新设备模型的 Adapter 北向契约字段。
     */
    public DeviceModels updateAdapterContract(Long modelId, JsonNode adapterContract) {
        DeviceModels model = mapper.selectById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("设备模型不存在");
        }
        model.setAdapterContract(adapterContract);
        validateModelAdapterContract(model);
        model.setUpdateTime(OffsetDateTime.now());
        mapper.updateById(model);
        return model;
    }

    /**
     * 查询所有设备模型中的状态机配置。
     */
    public List<ObjectNode> listStateMachines() {
        return list().stream().map(this::toStateMachineView).toList();
    }

    /**
     * 保存设备模型中的状态机相关 JSON 字段。
     */
    public String saveStateMachine(DeviceStateMachineSaveDTO payload) {
        if (payload == null || payload.getModelId() == null) {
            throw new IllegalArgumentException("modelId 不能为空");
        }
        DeviceModels model = getById(String.valueOf(payload.getModelId()));
        if (model == null) {
            throw new IllegalArgumentException("设备模型不存在，无法保存状态机");
        }
        if (payload.getInterfacesDef() != null) {
            model.setStateMachineInterfaces(payload.getInterfacesDef());
        }
        if (payload.getCommandLifecycleDef() != null) {
            model.setCmdState(payload.getCommandLifecycleDef());
        }
        if (payload.getOperationStateDef() != null) {
            model.setOpState(payload.getOperationStateDef());
        }
        if (payload.getTransitions() != null) {
            model.setStateTransitions(payload.getTransitions());
        }

        enrichStateMachine(model, payload.getOperationStateDef(), payload.getTransitions());

        model.setUpdateTime(OffsetDateTime.now());
        mapper.updateById(model);
        return String.valueOf(model.getId());
    }

    /**
     * 清空设备模型中的状态机相关 JSON 字段。
     */
    public void deleteStateMachine(String modelId) {
        DeviceModels model = getById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("设备模型不存在");
        }
        model.setStateMachineInterfaces(null);
        model.setCmdState(null);
        model.setOpState(null);
        model.setStateTransitions(null);
        model.setUpdateTime(OffsetDateTime.now());
        mapper.updateById(model);
    }

    /**
     * 查询设备模型内置约束规则。
     */
    public List<Map<String, Object>> listModelConstraintRules() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DeviceModels model : list()) {
            JsonNode constraints = model.getIntrinsicConstraint();
            if (constraints == null || !constraints.isArray()) {
                continue;
            }
            for (JsonNode rule : constraints) {
                result.add(Map.of(
                        "scope", "MODEL",
                        "targetId", String.valueOf(model.getId()),
                        "targetName", Objects.toString(model.getModelName(), ""),
                        "ruleId", Objects.toString(rule.path("constraintRuleId").asText(), ""),
                        "property", Objects.toString(rule.path("targetAttr").asText(), ""),
                        "operator", Objects.toString(rule.path("operator").asText(), ""),
                        "threshold", Objects.toString(rule.path("threshold").asText(), ""),
                        "action", Objects.toString(rule.path("violationStateRef").asText("WARN"), "WARN"),
                        "description", Objects.toString(rule.path("permisDesc").asText(), ""),
                        "active", true));
            }
        }
        return result;
    }

    /**
     * 保存设备模型内置约束规则。
     */
    public void saveModelConstraintRule(Map<String, Object> payload) {
        String modelId = Objects.toString(payload.get("modelId"), "");
        DeviceModels model = getById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("设备模型不存在");
        }
        ArrayNode constraints = model.getIntrinsicConstraint() != null && model.getIntrinsicConstraint().isArray()
                ? (ArrayNode) model.getIntrinsicConstraint()
                : JsonNodeSupport.arrayNode();
        String ruleId = Objects.toString(payload.getOrDefault("constraintRuleId", "RULE_" + UUID.randomUUID()), "");
        ArrayNode next = JsonNodeSupport.arrayNode();
        constraints.forEach(rule -> {
            if (!ruleId.equals(rule.path("constraintRuleId").asText())) {
                next.add(rule);
            }
        });
        Map<String, Object> storedRule = new HashMap<>(payload);
        storedRule.put("constraintRuleId", ruleId);
        if (!storedRule.containsKey("violationStateRef") && storedRule.containsKey("action")) {
            storedRule.put("violationStateRef", storedRule.get("action"));
        }
        next.add(JsonNodeSupport.toNode(storedRule));
        model.setIntrinsicConstraint(next);
        model.setUpdateTime(OffsetDateTime.now());
        mapper.updateById(model);
    }

    /**
     * 删除设备模型内置约束规则。
     */
    public void deleteModelConstraintRule(String modelId, String constraintRuleId) {
        DeviceModels model = getById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("设备模型不存在");
        }
        ArrayNode next = JsonNodeSupport.arrayNode();
        JsonNode current = model.getIntrinsicConstraint();
        if (current != null && current.isArray()) {
            current.forEach(rule -> {
                if (!constraintRuleId.equals(rule.path("constraintRuleId").asText())) {
                    next.add(rule);
                }
            });
        }
        model.setIntrinsicConstraint(next);
        model.setUpdateTime(OffsetDateTime.now());
        mapper.updateById(model);
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceModels savePayload(DeviceModelSaveDTO payload) {
        if (payload == null) {
            throw new IllegalArgumentException("设备模型保存请求不能为空");
        }
        DeviceModels model = new DeviceModels();
        if (payload.getModelId() != null) {
            model.setId(payload.getModelId());
        }
        model.setModelName(payload.getModelName());

        if (payload.getCategoryId() != null) {
            model.setCategoryId(payload.getCategoryId());
        } else if (payload.getCategoryName() != null && !payload.getCategoryName().isBlank()) {
            DeviceCategory category = deviceCategoryService.findOrCreateByName(payload.getCategoryName());
            if (category != null) {
                model.setCategoryId(category.getId());
            }
        }

        if (model.getCategoryId() != null) {
            deviceCategoryService.requireLeafCategory(model.getCategoryId());
        }
        model.setAttributes(payload.getAttributes());
        model.setCapabilities(payload.getCapabilities());
        model.setAdapterContract(payload.getAdapterContract());
        model.setPorts(payload.getPorts());
        model.setIntrinsicConstraint(payload.getIntrinsicConstraints());
        model.setStateMachineInterfaces(payload.getStateMachineInterfaces());
        model.setOpState(payload.getOpState());
        model.setCmdState(payload.getCmdState());
        model.setStateTransitions(payload.getStateTransitions());
        model.setComponentsBom(payload.getComponentsBom());

        OffsetDateTime now = OffsetDateTime.now();
        if (model.getId() == null) {
            model.setCreateTime(now);
        }
        model.setUpdateTime(now);

        enrichStateMachine(model, payload.getOpState(), payload.getStateTransitions());
        validateModelAdapterContract(model);

        if (model.getId() == null) {
            mapper.insert(model);
        } else {
            mapper.updateById(model);
        }
        saveDefaultDataTemplate(model.getId(), model.getModelName(), payload.getDefaultDataTemplate());
        return model;
    }

    private void saveDefaultDataTemplate(Long modelId, String modelName, JsonNode templateNode) {
        if (modelId == null || templateNode == null || templateNode.isNull() || templateNode.isMissingNode()) {
            return;
        }
        if (templateNode.has("enabled") && !templateNode.path("enabled").asBoolean(true)) {
            return;
        }
        JsonNode mainNode = templateNode.has("main") ? templateNode.path("main") : templateNode;
        JsonNode detailsNode = templateNode.has("details") ? templateNode.path("details") : templateNode.path("columns");
        if (detailsNode == null || !detailsNode.isArray() || detailsNode.size() == 0) {
            return;
        }

        DataTemplateMain existing = dataTemplateService.findDefaultTemplateByModelId(modelId);
        DataTemplateMain main = new DataTemplateMain();
        if (existing != null) {
            main.setId(existing.getId());
            main.setCreateTime(existing.getCreateTime());
        }
        main.setTemplateName(textValue(mainNode, "templateName", modelName + " 默认数据模板"));
        main.setTemplateDesc(textValue(mainNode, "templateDesc", "系统根据设备模型自动生成的默认数据模板"));
        main.setDeviceModelId(modelId);
        main.setIsDefault(true);

        DataTemplateSaveDTO dto = new DataTemplateSaveDTO();
        dto.setMain(main);
        List<DataTemplateDetail> details = new ArrayList<>();
        for (JsonNode row : detailsNode) {
            String columnName = textValue(row, "columnName", "");
            if (columnName.isBlank()) {
                continue;
            }
            DataTemplateDetail detail = new DataTemplateDetail();
            detail.setColumnName(columnName);
            detail.setColumnDesc(textValue(row, "columnDesc", columnName));
            detail.setDeviceAttrKey(nullableTextValue(row, "deviceAttrKey"));
            detail.setDefaultValue(nullableTextValue(row, "defaultValue"));
            detail.setPropertyTypeId(longValue(row, "propertyTypeId"));
            Integer columnLength = integerValue(row, "columnLength");
            detail.setColumnLength(columnLength == null || columnLength <= 0 ? 255 : columnLength);
            details.add(detail);
        }
        if (details.isEmpty()) {
            return;
        }
        dto.setDetails(details);
        dataTemplateService.saveTemplate(dto);
    }

    private String textValue(JsonNode node, String field, String fallback) {
        String value = nullableTextValue(node, field);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String nullableTextValue(JsonNode node, String field) {
        if (node == null || node.isNull() || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private Long longValue(JsonNode node, String field) {
        if (node == null || node.isNull() || !node.has(field) || node.get(field).isNull() || node.get(field).asText().isBlank()) {
            return null;
        }
        return node.get(field).asLong();
    }

    private Integer integerValue(JsonNode node, String field) {
        if (node == null || node.isNull() || !node.has(field) || node.get(field).isNull() || node.get(field).asText().isBlank()) {
            return null;
        }
        return node.get(field).asInt();
    }

    public ObjectNode previewModel(DeviceModelSaveDTO payload) {
        if (payload == null) {
            throw new IllegalArgumentException("设备模型预览请求不能为空");
        }
        DeviceModels model = new DeviceModels();
        model.setId(payload.getModelId());
        model.setModelName(payload.getModelName());
        model.setCategoryId(payload.getCategoryId());
        model.setAttributes(payload.getAttributes());
        model.setCapabilities(payload.getCapabilities());
        model.setAdapterContract(payload.getAdapterContract());
        model.setPorts(payload.getPorts());
        model.setIntrinsicConstraint(payload.getIntrinsicConstraints());
        model.setStateMachineInterfaces(payload.getStateMachineInterfaces());
        model.setOpState(payload.getOpState());
        model.setCmdState(payload.getCmdState());
        model.setStateTransitions(payload.getStateTransitions());
        model.setComponentsBom(payload.getComponentsBom());

        enrichStateMachine(model, payload.getOpState(), payload.getStateTransitions());

        return toModelBundle(model);
    }

    public ObjectNode modelBundle(String id) {
        DeviceModels model = getById(id);
        if (model == null) {
            throw new IllegalArgumentException("设备模型不存在");
        }
        return toModelBundle(model);
    }

    private ObjectNode toModelBundle(DeviceModels model) {
        ObjectNode result = JsonNodeSupport.objectNode();
        result.set("capabilityModel", toCapabilityModel(model));
        result.set("stateMachineModel", toStateMachineModel(model));
        return result;
    }

    private ObjectNode toCapabilityModel(DeviceModels model) {
        ObjectNode capabilityModel = JsonNodeSupport.objectNode();
        ObjectNode metadata = JsonNodeSupport.objectNode();
        if (model.getId() != null) {
            metadata.put("modelId", model.getId());
        } else {
            metadata.putNull("modelId");
        }
        metadata.put("modelName", Objects.toString(model.getModelName(), ""));
        if (model.getCategoryId() != null) {
            metadata.put("deviceCategoryId", model.getCategoryId());
        } else {
            metadata.putNull("deviceCategoryId");
        }
        capabilityModel.set("metadata", metadata);
        capabilityModel.set("attributes", nullToArray(model.getAttributes()));
        capabilityModel.set("capabilities", nullToArray(model.getCapabilities()));
        capabilityModel.set("adapterContract", nullToObject(model.getAdapterContract()));
        capabilityModel.set("ports", nullToArray(model.getPorts()));
        capabilityModel.set("intrinsicConstraints", nullToArray(model.getIntrinsicConstraint()));
        return capabilityModel;
    }

    private ObjectNode toStateMachineModel(DeviceModels model) {
        ObjectNode stateMachineModel = JsonNodeSupport.objectNode();
        if (model.getId() != null) {
            stateMachineModel.put("deviceModelId", model.getId());
        } else {
            stateMachineModel.putNull("deviceModelId");
        }
        stateMachineModel.set("interfaces", nullToArray(model.getStateMachineInterfaces()));
        stateMachineModel.set("opStateSpace", nullToObject(model.getOpState()));
        stateMachineModel.set("cmdLifecycleSpace", nullToObject(model.getCmdState()));
        stateMachineModel.set("transitions", nullToArray(model.getStateTransitions()));
        return stateMachineModel;
    }

    private void enrichStateMachine(DeviceModels model, JsonNode opStateNode, JsonNode transitionNode) {
        List<String> adapterEvents = extractAdapterEvents(model.getAdapterContract());
        ArrayNode interfaces = generateStandardInterfaces(adapterEvents);
        model.setStateMachineInterfaces(interfaces);

        ObjectNode cmdSpace = generateStandardCmdLifecycleSpace();
        model.setCmdState(cmdSpace);

        JsonNode opNode = opStateNode != null ? opStateNode : model.getOpState();
        ObjectNode opSpace = parseOpStateSpace(opNode);
        model.setOpState(opSpace);

        ArrayNode transitions = JsonNodeSupport.arrayNode();
        addStandardTransitions(transitions);

        JsonNode transNode = transitionNode != null ? transitionNode : model.getStateTransitions();
        mergeCustomTransitions(transitions, transNode);

        model.setStateTransitions(transitions);
    }

    private List<String> extractAdapterEvents(JsonNode adapterContract) {
        List<String> eventNames = new ArrayList<>();
        if (adapterContract == null || adapterContract.isNull() || !adapterContract.has("events")) {
            return eventNames;
        }
        JsonNode eventsNode = adapterContract.get("events");
        if (eventsNode.isArray()) {
            for (JsonNode event : eventsNode) {
                String name = event.has("eventName") ? event.get("eventName").asText()
                        : (event.has("name") ? event.get("name").asText() : "");
                if (!name.isBlank()) {
                    eventNames.add(name);
                }
            }
        } else if (eventsNode.isObject()) {
            if (eventsNode.has("cmdEvents") && eventsNode.get("cmdEvents").isArray()) {
                for (JsonNode event : eventsNode.get("cmdEvents")) {
                    String name = event.has("eventName") ? event.get("eventName").asText()
                            : (event.has("name") ? event.get("name").asText() : "");
                    if (!name.isBlank()) {
                        eventNames.add(name);
                    }
                }
            }
            if (eventsNode.has("opEvents") && eventsNode.get("opEvents").isArray()) {
                for (JsonNode event : eventsNode.get("opEvents")) {
                    String name = event.has("eventName") ? event.get("eventName").asText()
                            : (event.has("name") ? event.get("name").asText() : "");
                    if (!name.isBlank()) {
                        eventNames.add(name);
                    }
                }
            }
        }
        return eventNames;
    }

    private ArrayNode generateStandardInterfaces(List<String> adapterEvents) {
        ArrayNode interfaces = JsonNodeSupport.arrayNode();

        ObjectNode workflowIn = JsonNodeSupport.objectNode();
        workflowIn.put("name", "Interface_workflow_in");
        workflowIn.put("direction", "IN");
        workflowIn.put("interfaceType", "WORKFLOW");
        ArrayNode workflowSignals = JsonNodeSupport.arrayNode();
        for (String sig : new String[] { "EXECUTE_START", "EXECUTE_PAUSE", "EXECUTE_RESUME", "EXECUTE_CANCEL",
                "EXECUTE_RESET" }) {
            workflowSignals.add(sig);
        }
        workflowIn.set("allowedSignals", workflowSignals);
        interfaces.add(workflowIn);

        ObjectNode statusOut = JsonNodeSupport.objectNode();
        statusOut.put("name", "Interface_status_out");
        statusOut.put("direction", "OUT");
        statusOut.put("interfaceType", "STAT");
        ArrayNode statusSignals = JsonNodeSupport.arrayNode();
        for (String sig : new String[] { "OP_STATE", "CMD_STATE" }) {
            statusSignals.add(sig);
        }
        statusOut.set("allowedSignals", statusSignals);
        interfaces.add(statusOut);

        ObjectNode controlIn = JsonNodeSupport.objectNode();
        controlIn.put("name", "Interface_control_in");
        controlIn.put("direction", "IN");
        controlIn.put("interfaceType", "CONTROL");
        ArrayNode controlSignals = JsonNodeSupport.arrayNode();
        for (String sig : new String[] { "MANUAL_EXECUTE", "MANUAL_CANCEL", "MANUAL_PAUSE", "MANUAL_RESUME",
                "MANUAL_RESET" }) {
            controlSignals.add(sig);
        }
        controlIn.set("allowedSignals", controlSignals);
        interfaces.add(controlIn);

        ObjectNode constraintIn = JsonNodeSupport.objectNode();
        constraintIn.put("name", "Interface_constraint_in");
        constraintIn.put("direction", "IN");
        constraintIn.put("interfaceType", "CONSTRAINT");
        ArrayNode constraintSignals = JsonNodeSupport.arrayNode();
        for (String sig : new String[] { "CONSTRAINT_CANCEL", "CONSTRAINT_PAUSE", "CONSTRAINT_RESUME",
                "CONSTRAINT_RESET" }) {
            constraintSignals.add(sig);
        }
        constraintIn.set("allowedSignals", constraintSignals);
        interfaces.add(constraintIn);

        ObjectNode adapterOut = JsonNodeSupport.objectNode();
        adapterOut.put("name", "Interface_adapter_out");
        adapterOut.put("direction", "OUT");
        adapterOut.put("interfaceType", "ADAPTER");
        ArrayNode adapterOutSignals = JsonNodeSupport.arrayNode();
        for (String sig : new String[] { "CMD_START", "CMD_CANCEL", "CMD_PAUSE", "CMD_RESUME", "CMD_RESET" }) {
            adapterOutSignals.add(sig);
        }
        adapterOut.set("allowedSignals", adapterOutSignals);
        interfaces.add(adapterOut);

        ObjectNode adapterIn = JsonNodeSupport.objectNode();
        adapterIn.put("name", "Interface_adapter_in");
        adapterIn.put("direction", "IN");
        adapterIn.put("interfaceType", "ADAPTER");
        ArrayNode adapterInSignals = JsonNodeSupport.arrayNode();
        for (String sig : adapterEvents) {
            adapterInSignals.add(sig);
        }
        adapterIn.set("allowedSignals", adapterInSignals);
        interfaces.add(adapterIn);

        return interfaces;
    }

    private ObjectNode generateStandardCmdLifecycleSpace() {
        ObjectNode cmdSpace = JsonNodeSupport.objectNode();
        cmdSpace.put("initialStateName", "IDLE");
        ArrayNode states = JsonNodeSupport.arrayNode();
        for (String stateName : new String[] { "IDLE", "SENT", "RECEIVED", "RUNNING", "DONE", "FAILED", "TIMEOUT",
                "CANCELLED" }) {
            ObjectNode state = JsonNodeSupport.objectNode();
            state.put("stateName", stateName);
            state.set("onEntry", createStatusOnEntryActions("CMD_STATE", stateName));
            states.add(state);
        }
        cmdSpace.set("states", states);
        return cmdSpace;
    }

    private ArrayNode createStatusOnEntryActions(String signalName, String stateName) {
        ArrayNode actions = JsonNodeSupport.arrayNode();
        ObjectNode action = JsonNodeSupport.objectNode();
        action.put("actionName", "SEND");
        ObjectNode payload = JsonNodeSupport.objectNode();
        payload.put("interfaceName", "Interface_status_out");
        payload.put("signalName", signalName);
        payload.put("stateName", stateName);
        action.set("payload", payload);
        actions.add(action);
        return actions;
    }

    private void addStandardTransitions(ArrayNode transitions) {
        transitions.add(createTransition("工作流触发指令下发", "IDLE", "SENT",
                "Interface_workflow_in", "EXECUTE_START",
                "SEND", "Interface_adapter_out", "CMD_START"));

        transitions.add(createTransition("用户手动触发指令下发", "IDLE", "SENT",
                "Interface_control_in", "MANUAL_EXECUTE",
                "SEND", "Interface_adapter_out", "CMD_START"));

        transitions.add(createTransition("Adapter 已接收", "SENT", "RECEIVED",
                "Interface_adapter_in", "COMMAND_RECEIVED",
                null, null, null));

        transitions.add(createTransition("Adapter 执行中", "RECEIVED", "RUNNING",
                "Interface_adapter_in", "COMMAND_RUNNING",
                null, null, null));

        transitions.add(createTransition("执行完成", "RUNNING", "DONE",
                "Interface_adapter_in", "COMMAND_COMPLETED",
                null, null, null));

        transitions.add(createTransition("执行失败", "RUNNING", "FAILED",
                "Interface_adapter_in", "COMMAND_FAILED",
                null, null, null));

        transitions.add(createTransition("执行超时", "RUNNING", "TIMEOUT",
                "Interface_adapter_in", "COMMAND_TIMEOUT",
                null, null, null));

        transitions.add(createTransition("Adapter 确认取消", "SENT", "CANCELLED",
                "Interface_adapter_in", "COMMAND_CANCELLED",
                null, null, null));

        transitions.add(createTransition("工作流取消指令", "RUNNING", "CANCELLED",
                "Interface_workflow_in", "EXECUTE_CANCEL",
                "SEND", "Interface_adapter_out", "CMD_CANCEL"));

        transitions.add(createTransition("用户手动取消指令", "RUNNING", "CANCELLED",
                "Interface_control_in", "MANUAL_CANCEL",
                "SEND", "Interface_adapter_out", "CMD_CANCEL"));

        transitions.add(createTransition("约束引擎取消指令", "RUNNING", "CANCELLED",
                "Interface_constraint_in", "CONSTRAINT_CANCEL",
                "SEND", "Interface_adapter_out", "CMD_CANCEL"));
    }

    private ObjectNode createTransition(String description, String fromState, String toState,
            String triggerInterface, String triggerSignal,
            String actionName, String actionInterface, String actionSignal) {
        ObjectNode transition = JsonNodeSupport.objectNode();
        transition.put("description", description);
        transition.put("fromStateName", fromState);
        transition.put("toStateName", toState);

        ObjectNode trigger = JsonNodeSupport.objectNode();
        trigger.put("interfaceName", triggerInterface);
        trigger.put("signalName", triggerSignal);
        transition.set("trigger", trigger);

        ArrayNode actions = JsonNodeSupport.arrayNode();
        if (actionName != null) {
            ObjectNode action = JsonNodeSupport.objectNode();
            action.put("actionName", actionName);
            ObjectNode payload = JsonNodeSupport.objectNode();
            payload.put("interfaceName", actionInterface);
            payload.put("signalName", actionSignal);
            action.set("payload", payload);
            actions.add(action);
        }
        transition.set("actions", actions);
        return transition;
    }

    private ObjectNode parseOpStateSpace(JsonNode node) {
        ObjectNode opSpace = JsonNodeSupport.objectNode();
        String initial = "IDLE";
        ArrayNode states = JsonNodeSupport.arrayNode();

        java.util.Map<String, JsonNode> originalStates = new java.util.LinkedHashMap<>();

        if (node == null || node.isNull() || node.isMissingNode()) {
            originalStates.put("IDLE", JsonNodeSupport.arrayNode());
        } else if (node.isArray()) {
            for (JsonNode s : node) {
                originalStates.put(s.asText(), JsonNodeSupport.arrayNode());
            }
        } else if (node.isObject()) {
            initial = node.path("initialStateName").asText("IDLE");
            JsonNode inputStates = node.get("states");
            if (inputStates != null && inputStates.isArray()) {
                for (JsonNode s : inputStates) {
                    if (s.isObject() && s.has("stateName")) {
                        String name = s.get("stateName").asText();
                        JsonNode onEntry = s.get("onEntry");
                        originalStates.put(name, onEntry != null && onEntry.isArray() ? onEntry.deepCopy()
                                : JsonNodeSupport.arrayNode());
                    } else if (s.isTextual()) {
                        originalStates.put(s.asText(), JsonNodeSupport.arrayNode());
                    }
                }
            } else {
                originalStates.put("IDLE", JsonNodeSupport.arrayNode());
            }
        } else {
            originalStates.put("IDLE", JsonNodeSupport.arrayNode());
        }

        if (!originalStates.containsKey(initial) && !originalStates.isEmpty()) {
            initial = originalStates.keySet().iterator().next();
        }

        opSpace.put("initialStateName", initial);
        for (java.util.Map.Entry<String, JsonNode> entry : originalStates.entrySet()) {
            ObjectNode stateObj = JsonNodeSupport.objectNode();
            stateObj.put("stateName", entry.getKey());
            stateObj.set("onEntry", entry.getValue());
            states.add(stateObj);
        }
        opSpace.set("states", states);
        return opSpace;
    }

    private void mergeCustomTransitions(ArrayNode targetTransitions, JsonNode sourceTransitions) {
        if (sourceTransitions == null || sourceTransitions.isNull() || !sourceTransitions.isArray()) {
            return;
        }

        Set<String> standardTriggerSignals = Set.of(
                "EXECUTE_START", "MANUAL_EXECUTE", "COMMAND_RECEIVED", "COMMAND_RUNNING",
                "COMMAND_COMPLETED", "COMMAND_FAILED", "COMMAND_TIMEOUT", "COMMAND_CANCELLED",
                "EXECUTE_CANCEL", "MANUAL_CANCEL", "CONSTRAINT_CANCEL");

        for (JsonNode t : sourceTransitions) {
            if (!t.isObject() || !t.has("fromStateName") || !t.has("toStateName") || !t.has("trigger")) {
                continue;
            }
            JsonNode trigger = t.get("trigger");
            String signalName = trigger.path("signalName").asText("");
            if (signalName.isBlank()) {
                continue;
            }

            if (standardTriggerSignals.contains(signalName)) {
                continue;
            }

            ObjectNode transition = JsonNodeSupport.objectNode();
            transition.put("description", t.path("description").asText(""));
            transition.put("fromStateName", t.path("fromStateName").asText());
            transition.put("toStateName", t.path("toStateName").asText());

            ObjectNode newTrigger = JsonNodeSupport.objectNode();
            newTrigger.put("interfaceName", trigger.path("interfaceName").asText("Interface_adapter_in"));
            newTrigger.put("signalName", signalName);
            transition.set("trigger", newTrigger);

            JsonNode actions = t.get("actions");
            transition.set("actions",
                    actions != null && actions.isArray() ? actions.deepCopy() : JsonNodeSupport.arrayNode());

            targetTransitions.add(transition);
        }
    }

    public void delete(String id) {
        Long modelId = parseId(id);
        Long count = deviceInstancesMapper.selectCount(
                Wrappers.<DeviceInstances>lambdaQuery().eq(DeviceInstances::getDeviceModelId, modelId));
        if (count != null && count > 0) {
            throw new IllegalStateException("该模型下仍有 " + count + " 台设备实例，无法删除");
        }
        mapper.deleteById(modelId);
    }

    private void validateModelAdapterContract(DeviceModels model) {
        validateCapabilityModelIdentifiers(model);
        JsonNode contract = model.getAdapterContract();
        if (contract == null || contract.isNull() || contract.isMissingNode()) {
            return;
        }

        Map<String, String> modelAttrTypes = new HashMap<>();
        for (JsonNode attr : iterable(model.getAttributes())) {
            String name = attr.path("name").asText("");
            if (!name.isBlank()) {
                modelAttrTypes.put(name, normalizeDataType(attr.path("dataType").asText("STRING")));
            }
        }

        Map<String, String> adapterAttrTypes = new HashMap<>();
        for (JsonNode attr : iterable(contract.path("telemetry").path("adapterAttributes"))) {
            String name = attr.path("name").asText("");
            if (!name.isBlank()) {
                adapterAttrTypes.put(name, normalizeDataType(attr.path("dataType").asText("STRING")));
            }
        }

        for (JsonNode mapping : iterable(contract.path("telemetry").path("attributesMapping"))) {
            String adapterAttr = mapping.path("adapterAttrName").asText("");
            String modelAttr = mapping.path("modelAttributeName").asText("");
            if (adapterAttr.isBlank() || modelAttr.isBlank()) {
                continue;
            }
            String adapterType = adapterAttrTypes.get(adapterAttr);
            String modelType = modelAttrTypes.get(modelAttr);
            if (adapterType == null) {
                throw new IllegalArgumentException("属性映射引用了不存在的 Adapter 属性: " + adapterAttr);
            }
            if (modelType == null) {
                throw new IllegalArgumentException("属性映射引用了不存在的模型属性: " + modelAttr);
            }
            if (!adapterType.equals(modelType)) {
                throw new IllegalArgumentException(
                        "属性映射类型不一致: " + modelAttr + "(" + modelType + ") -> " + adapterAttr + "(" + adapterType + ")");
            }
        }

        Map<String, Map<String, JsonNode>> commandParamsByCommand = new HashMap<>();
        for (JsonNode command : iterable(contract.path("commands"))) {
            String commandName = command.path("commandName").asText(command.path("name").asText(""));
            if (commandName.isBlank()) {
                continue;
            }
            Map<String, JsonNode> params = new HashMap<>();
            for (JsonNode param : iterable(
                    command.path("commandParameters").isMissingNode() ? command.path("parameters")
                            : command.path("commandParameters"))) {
                String paramName = param.path("paramName").asText(param.path("name").asText(""));
                if (!paramName.isBlank()) {
                    params.put(paramName, param);
                }
            }
            commandParamsByCommand.put(commandName, params);
        }

        for (JsonNode capability : iterable(model.getCapabilities())) {
            String capabilityName = capability.path("name").asText("");
            String commandName = capability.path("adapterCommandName").asText("");
            if (commandName.isBlank()) {
                continue;
            }
            Map<String, JsonNode> commandParams = commandParamsByCommand.get(commandName);
            if (commandParams == null) {
                throw new IllegalArgumentException("操作 " + capabilityName + " 引用了不存在的 Adapter 命令: " + commandName);
            }
            validateCapabilityParameterMapping(capability, commandName, commandParams);
        }
    }

    private void validateCapabilityModelIdentifiers(DeviceModels model) {
        ensureUniqueNames(model.getAttributes(), "属性");
        ensureUniqueNames(model.getCapabilities(), "操作");
        for (JsonNode capability : iterable(model.getCapabilities())) {
            String capabilityName = capability.path("name").asText("");
            ensureUniqueNames(capability.path("parameters"), "操作 " + capabilityName + " 的参数");
        }
    }

    private void ensureUniqueNames(JsonNode rows, String label) {
        Set<String> names = new HashSet<>();
        for (JsonNode row : iterable(rows)) {
            String name = row.path("name").asText("");
            if (name.isBlank()) {
                throw new IllegalArgumentException(label + "标识符不能为空");
            }
            if (!names.add(name)) {
                throw new IllegalArgumentException(label + "标识符重复: " + name);
            }
        }
    }

    private void validateCapabilityParameterMapping(JsonNode capability, String commandName,
            Map<String, JsonNode> commandParams) {
        Map<String, String> capabilityParamTypes = new HashMap<>();
        for (JsonNode param : iterable(capability.path("parameters"))) {
            String paramName = param.path("name").asText("");
            if (!paramName.isBlank()) {
                capabilityParamTypes.put(paramName, normalizeDataType(param.path("dataType").asText("STRING")));
            }
        }

        Set<String> mappedCommandParams = new HashSet<>();
        for (JsonNode mapping : iterable(capability.path("parameterMapping"))) {
            String commandParamName = mapping.path("commandParamName").asText("");
            if (commandParamName.isBlank()) {
                continue;
            }
            JsonNode commandParam = commandParams.get(commandParamName);
            if (commandParam == null) {
                throw new IllegalArgumentException("参数映射引用了不存在的命令参数: " + commandName + "." + commandParamName);
            }
            if (commandParam.path("hidden").asBoolean(false)) {
                throw new IllegalArgumentException("隐藏命令参数不能在设备模型中映射: " + commandName + "." + commandParamName);
            }
            mappedCommandParams.add(commandParamName);
            String commandType = normalizeDataType(commandParam.path("dataType").asText("STRING"));
            if (mapping.path("isFixedValue").asBoolean(false)) {
                if (!mapping.has("fixedValue") || mapping.path("fixedValue").isNull()) {
                    throw new IllegalArgumentException("固定参数缺少 fixedValue: " + commandName + "." + commandParamName);
                }
                if (!isFixedValueCompatible(commandType, mapping.path("fixedValue"))) {
                    throw new IllegalArgumentException(
                            "固定参数值类型不匹配: " + commandName + "." + commandParamName + " 需要 " + commandType);
                }
                continue;
            }

            String capabilityParamName = mapping.path("capabilityParamName").asText("");
            String capabilityType = capabilityParamTypes.get(capabilityParamName);
            if (capabilityType == null) {
                throw new IllegalArgumentException("参数映射引用了不存在的操作参数: " + capabilityParamName);
            }
            if (!commandType.equals(capabilityType)) {
                throw new IllegalArgumentException("参数映射类型不一致: " + capabilityParamName + "(" + capabilityType + ") -> "
                        + commandParamName + "(" + commandType + ")");
            }
        }

        for (Map.Entry<String, JsonNode> entry : commandParams.entrySet()) {
            if (!entry.getValue().path("hidden").asBoolean(false) && !mappedCommandParams.contains(entry.getKey())) {
                throw new IllegalArgumentException("命令 " + commandName + " 的参数未映射: " + entry.getKey());
            }
        }
    }

    private boolean isFixedValueCompatible(String dataType, JsonNode value) {
        if (value == null || value.isNull()) {
            return false;
        }
        return switch (normalizeDataType(dataType)) {
            case "BOOLEAN" -> value.isBoolean() || "true".equalsIgnoreCase(value.asText())
                    || "false".equalsIgnoreCase(value.asText());
            case "INTEGER" -> value.isIntegralNumber() || value.asText().matches("-?\\d+");
            case "DOUBLE" -> value.isNumber() || isNumeric(value.asText());
            default -> !value.asText().isBlank();
        };
    }

    private boolean isNumeric(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private String normalizeDataType(String dataType) {
        return adapterManifestService.normalizeDataType(dataType);
    }

    private Iterable<JsonNode> iterable(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || !node.isArray()) {
            return JsonNodeSupport.arrayNode();
        }
        return node;
    }

    private ObjectNode toStateMachineView(DeviceModels model) {
        ObjectNode node = JsonNodeSupport.objectNode();
        if (model.getId() != null) {
            node.put("stateMachineId", model.getId() + "StateMachine");
            node.put("deviceModelRef", String.valueOf(model.getId()));
        } else {
            node.putNull("stateMachineId");
            node.putNull("deviceModelRef");
        }
        node.set("interfacesDef", nullToObject(model.getStateMachineInterfaces()));
        node.set("commandLifecycleDef", nullToObject(model.getCmdState()));
        node.set("operationStateDef", nullToObject(model.getOpState()));
        node.set("transitions", nullToArray(model.getStateTransitions()));
        return node;
    }

    private JsonNode nullToObject(JsonNode node) {
        return node == null ? JsonNodeSupport.objectNode() : node;
    }

    private JsonNode nullToArray(JsonNode node) {
        return node == null ? JsonNodeSupport.arrayNode() : node;
    }
}
````

---

## Backend/src/main/java/com/smartlab/management/service/db/resource/device/DeviceTwinStateService.java

````text
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




````

---

## Backend/src/main/java/com/smartlab/management/service/db/resource/scene/ResourceStructureService.java

````text
package com.smartlab.management.service.db.resource.scene;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.management.entity.resource.device.ResourceStructure;
import com.smartlab.management.mapper.resource.device.ResourceStructureMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 资源结构表服务。
 *
 * 对应 RESOURCE_STRUCTURE 表，用于记录场景内设备实例之间的连接关系。
 */
@Service
/**
 * ResourceStructure业务持久层核心操作服务。
 */
public class ResourceStructureService extends ManagementCrudService<ResourceStructure> {

    private final ResourceStructureMapper mapper;

    public ResourceStructureService(ResourceStructureMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    public List<ResourceStructure> listByScene(Long sceneId) {
        if (sceneId == null) {
            return list();
        }
        return mapper.selectList(
                Wrappers.<ResourceStructure>lambdaQuery()
                        .eq(ResourceStructure::getSceneId, sceneId)
                        .orderByDesc(ResourceStructure::getId)
        );
    }
}




````

---

## Backend/src/main/java/com/smartlab/management/service/db/resource/scene/SceneService.java

````text
package com.smartlab.management.service.db.resource.scene;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.management.entity.resource.scene.SceneDetail;
import com.smartlab.management.entity.resource.scene.SceneMain;
import com.smartlab.management.mapper.resource.scene.SceneDetailMapper;
import com.smartlab.management.mapper.resource.scene.SceneMainMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 场景表服务。
 *
 * 对应 SCENE_MAIN 与 SCENE_DETAIL 表。
 */
@Service
/**
 * Scene业务持久层核心操作服务。
 */
public class SceneService {

    private final SceneMainMapper sceneMainMapper;
    private final SceneDetailMapper sceneDetailMapper;

    public SceneService(SceneMainMapper sceneMainMapper, SceneDetailMapper sceneDetailMapper) {
        this.sceneMainMapper = sceneMainMapper;
        this.sceneDetailMapper = sceneDetailMapper;
    }

    public List<SceneMain> listScenes() {
        return sceneMainMapper.selectList(Wrappers.<SceneMain>lambdaQuery().orderByDesc(SceneMain::getId));
    }

    public SceneMain getScene(Long id) {
        return sceneMainMapper.selectById(id);
    }

    public SceneMain saveScene(SceneMain scene) {
        OffsetDateTime now = OffsetDateTime.now();
        if (scene.getId() == null) {
            scene.setCreateTime(now);
            scene.setUpdateTime(now);
            sceneMainMapper.insert(scene);
        } else {
            scene.setUpdateTime(now);
            sceneMainMapper.updateById(scene);
        }
        return scene;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteScene(Long id) {
        sceneDetailMapper.delete(Wrappers.<SceneDetail>lambdaQuery().eq(SceneDetail::getSceneId, id));
        sceneMainMapper.deleteById(id);
    }

    public List<SceneDetail> listSceneDetails() {
        return sceneDetailMapper.selectList(Wrappers.<SceneDetail>lambdaQuery().orderByDesc(SceneDetail::getId));
    }

    public List<SceneDetail> listSceneDetails(Long sceneId) {
        if (sceneId == null) {
            return listSceneDetails();
        }
        return sceneDetailMapper.selectList(
                Wrappers.<SceneDetail>lambdaQuery()
                        .eq(SceneDetail::getSceneId, sceneId)
                        .orderByDesc(SceneDetail::getId)
        );
    }

    public SceneDetail saveSceneDetail(SceneDetail detail) {
        if (detail.getId() == null) {
            detail.setCreateTime(OffsetDateTime.now());
            sceneDetailMapper.insert(detail);
        } else {
            sceneDetailMapper.updateById(detail);
        }
        return detail;
    }

    public void deleteSceneDetail(Long id) {
        sceneDetailMapper.deleteById(id);
    }
}




````

---

## Backend/src/main/java/com/smartlab/management/service/db/user/PermissionService.java

````text
package com.smartlab.management.service.db.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.user.PermissionInfo;
import com.smartlab.management.entity.user.UserInfo;
import com.smartlab.management.mapper.user.PermissionInfoMapper;
import com.smartlab.management.mapper.user.UserInfoMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限表服务。
 * 对应 PERMISSION_INFO 表，并根据用户等级和用户特权 JSON 计算实际权限。
 */
@Service
/**
 * Permission业务持久层核心操作服务。
 */
public class PermissionService extends ManagementCrudService<PermissionInfo> {

    private final PermissionInfoMapper permissionMapper;
    private final UserInfoMapper userInfoMapper;

    public PermissionService(PermissionInfoMapper permissionMapper, UserInfoMapper userInfoMapper) {
        super(permissionMapper);
        this.permissionMapper = permissionMapper;
        this.userInfoMapper = userInfoMapper;
    }

    public List<PermissionInfo> listAll() {
        return permissionMapper.selectList(Wrappers.<PermissionInfo>lambdaQuery().orderByAsc(PermissionInfo::getId));
    }

    public List<PermissionInfo> getUserPermissions(Long userId, String labCode, Integer userLevel) {
        List<PermissionInfo> all = listAll();
        Set<Long> privilegedIds = getUserPermissionIds(userId).stream().map(Long::valueOf).collect(Collectors.toSet());
        List<PermissionInfo> result = new ArrayList<>();
        for (PermissionInfo permission : all) {
            boolean levelMatched = permission.getPermissionLevel() == null
                    || (userLevel != null && userLevel >= permission.getPermissionLevel());
            boolean explicitlyGranted = permission.getId() != null && privilegedIds.contains(permission.getId());
            if (levelMatched || explicitlyGranted) {
                result.add(permission);
            }
        }
        return result;
    }

    public List<Integer> getUserPermissionIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        UserInfo user = userInfoMapper.selectById(userId);
        if (user == null || user.getPrivileged() == null || user.getPrivileged().isNull()) {
            return List.of();
        }
        Set<Integer> ids = new HashSet<>();
        JsonNode node = user.getPrivileged();
        if (node.isArray()) {
            node.forEach(item -> collectPermissionId(item, ids));
        } else {
            collectPermissionId(node, ids);
        }
        return ids.stream().sorted().toList();
    }

    public void assignPermissionsToUser(Long userId, List<Integer> permissionIds) {
        UserInfo user = userInfoMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        List<Integer> ids = permissionIds == null ? List.of() : permissionIds;
        user.setPrivileged(JsonNodeSupport.toNode(ids));
        user.setIsPrivilegedUser(!ids.isEmpty());
        userInfoMapper.updateById(user);
    }

    private void collectPermissionId(JsonNode node, Set<Integer> ids) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.canConvertToInt()) {
            ids.add(node.asInt());
        } else if (node.isObject()) {
            JsonNode id = node.get("id");
            if (id != null && id.canConvertToInt()) {
                ids.add(id.asInt());
            }
        }
    }
}

````

---

## Backend/src/main/java/com/smartlab/management/service/db/user/UserService.java

````text
package com.smartlab.management.service.db.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.global.auth.AuthenticationTokenService;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.user.MenuDTO;
import com.smartlab.management.dto.user.UserRequestDTO;
import com.smartlab.management.entity.user.PermissionInfo;
import com.smartlab.management.entity.user.UserInfo;
import com.smartlab.management.mapper.user.UserInfoMapper;
import com.smartlab.management.service.menu.MenuService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户表服务。
 * 对应 USER_INFO 表，负责登录、注册、用户维护和用户可见菜单计算。
 */
@Service
/**
 * User业务持久层核心操作服务。
 */
public class UserService {

    private final UserInfoMapper userInfoMapper;
    private final PasswordEncoder passwordEncoder;
    private final MenuService menuService;
    private final PermissionService permissionService;
    private final AuthenticationTokenService authenticationTokenService;

    public UserService(UserInfoMapper userInfoMapper,
                       PasswordEncoder passwordEncoder,
                       MenuService menuService,
                       PermissionService permissionService,
                       AuthenticationTokenService authenticationTokenService) {
        this.userInfoMapper = userInfoMapper;
        this.passwordEncoder = passwordEncoder;
        this.menuService = menuService;
        this.permissionService = permissionService;
        this.authenticationTokenService = authenticationTokenService;
    }

    public Map<String, Object> login(String username, String password) {
        UserInfo user = userInfoMapper.selectOne(Wrappers.<UserInfo>lambdaQuery().eq(UserInfo::getUserName, username));
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        String token = authenticationTokenService.generateToken(user.getUserName());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", buildProfile(user));
        return result;
    }

    public Map<String, Object> currentUserProfile(String username) {
        UserInfo user = userInfoMapper.selectOne(Wrappers.<UserInfo>lambdaQuery().eq(UserInfo::getUserName, username));
        if (user == null) {
            throw new IllegalArgumentException("用户不存在或登录已过期");
        }
        return buildProfile(user);
    }

    public void register(UserRequestDTO request) {
        UserInfo existing = userInfoMapper.selectOne(Wrappers.<UserInfo>lambdaQuery().eq(UserInfo::getUserName, request.getUserName()));
        if (existing != null) {
            throw new IllegalArgumentException("该用户名已被注册");
        }
        UserInfo user = new UserInfo();
        applyRequest(user, request);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userInfoMapper.insert(user);
    }

    public void verifyAdmin(String username, String password) {
        UserInfo user = userInfoMapper.selectOne(Wrappers.<UserInfo>lambdaQuery().eq(UserInfo::getUserName, username));
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("验证密码错误");
        }
        if (user.getUserLevel() == null || user.getUserLevel() < 3) {
            throw new IllegalArgumentException("无管理员权限");
        }
    }

    public List<UserInfo> listUsers() {
        List<UserInfo> users = userInfoMapper.selectList(Wrappers.<UserInfo>lambdaQuery().orderByAsc(UserInfo::getId));
        users.forEach(user -> user.setPasswordHash(null));
        return users;
    }

    public void updateUser(UserRequestDTO request) {
        if (request.getId() == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        UserInfo user = userInfoMapper.selectById(request.getId());
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        applyRequest(user, request);
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        userInfoMapper.updateById(user);
    }

    public void deleteUser(Long id) {
        userInfoMapper.deleteById(id);
    }

    private Map<String, Object> buildProfile(UserInfo user) {
        List<PermissionInfo> permissions = permissionService.getUserPermissions(user.getId(), user.getLab(), user.getUserLevel());
        Set<String> authObjects = permissions.stream()
                .map(PermissionInfo::getObject)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<MenuDTO> menus = menuService.getVisibleMenus(authObjects);

        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getUserName());
        profile.put("userName", user.getUserName());
        profile.put("role", user.getRole());
        profile.put("roleName", user.getRole());
        profile.put("userBasicInfo", user.getUserBasicinfo());
        profile.put("lab", user.getLab());
        profile.put("userLevel", user.getUserLevel());
        profile.put("permissions", permissions);
        profile.put("authObjects", authObjects);
        profile.put("menus", menus);
        return profile;
    }

    private void applyRequest(UserInfo user, UserRequestDTO request) {
        if (request.getUserName() != null) {
            user.setUserName(request.getUserName());
        }
        if (request.getRoleName() != null) {
            user.setRole(request.getRoleName());
            user.setUserLevel(roleLevel(request.getRoleName()));
        }
        if (request.getLab() != null) {
            user.setLab(request.getLab());
        }
        if (request.getUserLevel() != null) {
            user.setUserLevel(request.getUserLevel());
        }
        if (request.getUserBasicInfo() != null) {
            user.setUserBasicinfo(JsonNodeSupport.toNode(request.getUserBasicInfo()).toString());
        }
        if (user.getUserLevel() == null) {
            user.setUserLevel(roleLevel(user.getRole()));
        }
    }

    private int roleLevel(String role) {
        if ("system_admin".equals(role)) return 4;
        if ("lab_admin".equals(role)) return 3;
        if ("researcher".equals(role)) return 2;
        if ("observer".equals(role)) return 1;
        return 1;
    }
}

````

---

## Backend/src/main/java/com/smartlab/management/service/db/workflow/FlowNodeService.java

````text
package com.smartlab.management.service.db.workflow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.mapper.workflow.FlowNodeMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 流程节点表服务。
 * 对应 FLOW_NODE 表，用于维护流程模型中的节点明细。
 */
@Service
/**
 * FlowNode业务持久层核心操作服务。
 */
public class FlowNodeService extends ManagementCrudService<FlowNode> {

    private final FlowNodeMapper mapper;

    public FlowNodeService(FlowNodeMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    public List<FlowNode> listByFlowModel(Long flowModelId) {
        if (flowModelId == null) {
            return list();
        }
        return mapper.selectList(
                Wrappers.<FlowNode>lambdaQuery()
                        .eq(FlowNode::getFlowModelId, flowModelId)
                        .orderByAsc(FlowNode::getId));
    }
}

````

---

## Backend/src/main/java/com/smartlab/management/service/db/workflow/TaskService.java

````text
package com.smartlab.management.service.db.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.dto.workflow.TaskMonitorSummary;
import com.smartlab.management.entity.workflow.StepLog;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.mapper.workflow.StepLogMapper;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.mapper.workflow.TaskStepMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务表服务。
 * 对应 TASK、TASK_STEP、STEP_LOG 三张任务运行记录表。
 */
@Service
/**
 * 实验任务工作流生命周期控制与守护调度业务服务。
 */
public class TaskService extends ManagementCrudService<Task> {

    private final TaskMapper taskMapper;
    private final TaskStepMapper taskStepMapper;
    private final StepLogMapper stepLogMapper;

    public TaskService(TaskMapper taskMapper, TaskStepMapper taskStepMapper, StepLogMapper stepLogMapper) {
        super(taskMapper);
        this.taskMapper = taskMapper;
        this.taskStepMapper = taskStepMapper;
        this.stepLogMapper = stepLogMapper;
    }

    public PageResult<Task> page(long pageNo, long pageSize, String keyword, String status) {
        LambdaQueryWrapper<Task> query = Wrappers.lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            query.like(Task::getTaskName, keyword.trim());
        }
        if (status != null && !status.isBlank()) {
            query.eq(Task::getTaskStatus, status.trim());
        }
        query.orderByDesc(Task::getId);
        Page<Task> page = taskMapper.selectPage(new Page<>(Math.max(1, pageNo), Math.max(1, pageSize)), query);
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    public Map<String, Long> summary() {
        Map<String, Long> result = new HashMap<>();
        result.put("total", countAll());
        result.put("pending", countByStatus("PENDING"));
        result.put("running", countByStatus("RUNNING"));
        result.put("completed", countByStatus("COMPLETED"));
        result.put("failed", countByStatus("FAILED"));
        result.put("aborted", countByStatus("ABORTED"));
        return result;
    }

    public Task savePayload(Map<String, Object> payload) {
        Task task = new Task();
        Object id = first(payload, "id", "taskId");
        if (id != null && !String.valueOf(id).isBlank()) {
            task.setId(Long.valueOf(String.valueOf(id)));
        }
        Object flowModelId = first(payload, "flowModelId", "templateId");
        if (flowModelId != null && !String.valueOf(flowModelId).isBlank()) {
            task.setFlowModelId(Long.valueOf(String.valueOf(flowModelId)));
        }
        task.setTaskName(stringValue(first(payload, "taskName", "name")));
        task.setTaskDesc(stringValue(first(payload, "taskDesc", "description")));
        task.setTaskStatus(stringValue(first(payload, "taskStatus", "currentStatus")));
        if (task.getTaskStatus() == null) {
            task.setTaskStatus("PENDING");
        }
        task.setTaskConstraints(JsonNodeSupport.toNode(first(payload, "taskConstraints", "globalConstraints")));
        task.setResourceMap(JsonNodeSupport.toNode(first(payload, "resourceMap")));
        task.setTaskVariables(JsonNodeSupport.toNode(first(payload, "taskVariables", "variables")));
        Object creatorId = first(payload, "creatorId");
        if (creatorId != null && !String.valueOf(creatorId).isBlank()) {
            task.setCreatorId(Long.valueOf(String.valueOf(creatorId)));
        }
        if (task.getId() == null) {
            taskMapper.insert(task);
        } else {
            taskMapper.updateById(task);
        }
        return task;
    }

    public Task start(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        task.setTaskStatus("RUNNING");
        if (task.getStartTime() == null) {
            task.setStartTime(OffsetDateTime.now());
        }
        taskMapper.updateById(task);
        appendLog(taskId, "TASK", null, "INFO", "任务已启动");
        return task;
    }

    public Task abort(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        task.setTaskStatus("ABORTED");
        task.setEndTime(OffsetDateTime.now());
        taskMapper.updateById(task);
        appendLog(taskId, "TASK", null, "WARN", "任务已终止");
        return task;
    }

    public List<StepLog> logs(Long taskId, Long afterLogId, Integer limit) {
        LambdaQueryWrapper<StepLog> query = Wrappers.lambdaQuery();
        query.eq(StepLog::getTaskId, taskId);
        if (afterLogId != null) {
            query.gt(StepLog::getId, afterLogId);
        }
        query.orderByAsc(StepLog::getId);
        if (limit != null && limit > 0) {
            query.last("limit " + Math.min(limit, 1000));
        }
        return stepLogMapper.selectList(query);
    }

    public List<TaskStep> snapshots(Long taskId) {
        return taskStepMapper.selectList(
                Wrappers.<TaskStep>lambdaQuery().eq(TaskStep::getTaskId, taskId).orderByAsc(TaskStep::getId));
    }

    public List<Task> listByCreator(Long creatorId) {
        if (creatorId == null) {
            return List.of();
        }
        return taskMapper.selectList(
                Wrappers.<Task>lambdaQuery()
                        .eq(Task::getCreatorId, creatorId)
                        .orderByDesc(Task::getId));
    }

    public TaskMonitorSummary monitorSummary() {
        TaskMonitorSummary summary = new TaskMonitorSummary();
        summary.setRunningTasks(taskMapper
                .selectList(Wrappers.<Task>lambdaQuery().eq(Task::getTaskStatus, "RUNNING").orderByDesc(Task::getId)));
        summary.setPendingTasks(taskMapper
                .selectList(Wrappers.<Task>lambdaQuery().eq(Task::getTaskStatus, "PENDING").orderByDesc(Task::getId)));
        OffsetDateTime today = OffsetDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        summary.setTodayCompletedCount(taskMapper.selectCount(
                Wrappers.<Task>lambdaQuery().eq(Task::getTaskStatus, "COMPLETED").ge(Task::getEndTime, today)));
        summary.setTodayFailedCount(taskMapper.selectCount(
                Wrappers.<Task>lambdaQuery().eq(Task::getTaskStatus, "FAILED").ge(Task::getEndTime, today)));
        return summary;
    }

    public List<Task> listByStatus(String status) {
        if (status == null || status.isBlank()) {
            return list();
        }
        return taskMapper.selectList(
                Wrappers.<Task>lambdaQuery()
                        .eq(Task::getTaskStatus, status.trim())
                        .orderByDesc(Task::getId));
    }

    public Long countByStatus(String status) {
        return taskMapper.selectCount(Wrappers.<Task>lambdaQuery().eq(Task::getTaskStatus, status));
    }

    private void appendLog(Long taskId, String sourceType, Long deviceInstanceId, String level, String message) {
        StepLog log = new StepLog();
        log.setSourceType(sourceType);
        log.setTaskId(taskId);
        log.setDeviceInstanceId(deviceInstanceId);
        log.setLogLevel(level);
        log.setLogInfo(message);
        log.setLogTime(OffsetDateTime.now());
        stepLogMapper.insert(log);
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

````

---

## Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowService.java

````text
package com.smartlab.management.service.db.workflow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.workflow.FlowModels;
import com.smartlab.management.mapper.workflow.FlowModelsMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 流程模型表服务。
 * 对应 FLOW_MODELS 表，只负责流程模型的基础保存、查询和删除。
 */
@Service
/**
 * 工作流模板编排、定义与发布核心服务。
 */
public class WorkflowService extends ManagementCrudService<FlowModels> {

    private final FlowModelsMapper mapper;

    public WorkflowService(FlowModelsMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    @Override
    public List<FlowModels> list() {
        return mapper.selectList(Wrappers.<FlowModels>lambdaQuery().orderByDesc(FlowModels::getId));
    }

    public FlowModels getById(String id) {
        return mapper.selectById(parseId(id));
    }

    public FlowModels savePayload(Map<String, Object> payload) {
        FlowModels model = new FlowModels();
        Object id = first(payload, "id", "templateId", "workflowId");
        if (id != null && !String.valueOf(id).isBlank()) {
            model.setId(Long.valueOf(String.valueOf(id)));
        }
        model.setFlowName(stringValue(first(payload, "flowName", "templateName", "workflowName", "name")));
        model.setDescription(stringValue(first(payload, "description", "templateDesc")));
        model.setVersion(intValue(first(payload, "version"), 1));
        model.setStatus(stringValue(first(payload, "status", "workflowStatus")));
        if (model.getStatus() == null) {
            model.setStatus("ACTIVE");
        }
        putIfPresent(payload, "nodes", model::setNodes);
        putIfPresent(payload, "nodesDef", model::setNodes);
        putIfPresent(payload, "interfaceConnection", model::setInterfaceConnection);
        putIfPresent(payload, "interfaceConnections", model::setInterfaceConnection);
        putIfPresent(payload, "portConnection", model::setPortConnection);
        putIfPresent(payload, "portConnections", model::setPortConnection);
        Object creatorId = first(payload, "creatorId");
        if (creatorId != null && !String.valueOf(creatorId).isBlank()) {
            model.setCreatorId(Long.valueOf(String.valueOf(creatorId)));
        }
        if (model.getId() == null) {
            model.setCreateTime(OffsetDateTime.now());
            mapper.insert(model);
        } else {
            mapper.updateById(model);
        }
        return model;
    }

    public void delete(String id) {
        mapper.deleteById(parseId(id));
    }

    private Object first(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            if (payload.containsKey(key)) {
                return payload.get(key);
            }
        }
        return null;
    }

    private void putIfPresent(Map<String, Object> payload, String key, java.util.function.Consumer<JsonNode> setter) {
        if (payload.containsKey(key)) {
            setter.accept(JsonNodeSupport.toNode(payload.get(key)));
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer intValue(Object value, int defaultValue) {
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return Integer.valueOf(String.valueOf(value));
    }
}



````

---

## Backend/src/main/java/com/smartlab/management/service/menu/MenuCatalogService.java

````text
package com.smartlab.management.service.menu;

import com.smartlab.management.dto.user.MenuDTO;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
/**
 * 系统菜单目录管理持久层服务。
 */
public class MenuCatalogService {

    private final List<MenuDTO> fullMenuTree = new ArrayList<>();

    @PostConstruct
    public void init() {
        fullMenuTree.clear();
        fullMenuTree.add(create("首页", "/home"));

        MenuDTO deviceCenter = createFolder("设备中心");
        deviceCenter.addChild(create("设备模型管理", "/device-model-management", "device_model"));
        deviceCenter.addChild(create("设备实例管理", "/device-instance-management", "device_instance"));
        deviceCenter.addChild(create("设备执行代理", "/adapter-management", "adapter"));
        fullMenuTree.add(deviceCenter);

        fullMenuTree.add(create("数据中心", "/data-management", "data_template", "data_dataset"));

        MenuDTO taskCenter = createFolder("任务中心");
        taskCenter.addChild(create("任务列表", "/task-management", "task"));
        taskCenter.addChild(create("流程设计", "/task-designer", "workflow"));
        fullMenuTree.add(taskCenter);

        fullMenuTree.add(create("约束管理", "/constraint-management", "constraint_rule", "violation_log"));
        fullMenuTree.add(create("用户管理", "/user-management", "user", "permission"));
    }

    public List<MenuDTO> getFullMenuTree() {
        return fullMenuTree;
    }

    private MenuDTO create(String name, String path, String... requires) {
        MenuDTO menu = new MenuDTO();
        menu.setName(name);
        menu.setPath(path);
        for (String require : requires) {
            menu.addRequire(require);
        }
        return menu;
    }

    private MenuDTO createFolder(String name, String... requires) {
        return create(name, null, requires);
    }
}

````

---

## Backend/src/main/java/com/smartlab/management/service/menu/MenuService.java

````text
package com.smartlab.management.service.menu;

import com.smartlab.management.dto.user.MenuDTO;
import com.smartlab.management.entity.user.PermissionInfo;
import com.smartlab.management.service.db.user.PermissionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单可见性服务。
 * 菜单定义保存在后端代码字典中，是否可见由 PERMISSION_INFO 计算出的权限对象决定。
 */
@Service
/**
 * 系统侧边功能菜单加载与动态路由生成业务服务。
 */
public class MenuService {

    private final MenuCatalogService menuCatalogService;
    private final PermissionService permissionService;

    public MenuService(MenuCatalogService menuCatalogService, PermissionService permissionService) {
        this.menuCatalogService = menuCatalogService;
        this.permissionService = permissionService;
    }

    public List<MenuDTO> getVisibleMenusForUser(Long userId, String labCode, Integer userLevel) {
        Set<String> hasObjects = permissionService.getUserPermissions(userId, labCode, userLevel).stream()
                .map(PermissionInfo::getObject)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<MenuDTO> fullTree = menuCatalogService.getFullMenuTree();
        return filter(fullTree, hasObjects);
    }

    public List<MenuDTO> getVisibleMenus(Set<String> hasObjects) {
        List<MenuDTO> fullTree = menuCatalogService.getFullMenuTree();
        return filter(fullTree, hasObjects);
    }

    private List<MenuDTO> filter(List<MenuDTO> nodes, Set<String> hasObjects) {
        List<MenuDTO> result = new ArrayList<>();

        for (MenuDTO node : nodes) {
            if (canSee(node, hasObjects)) {
                MenuDTO copy = copyNode(node);
                copy.setChildren(filter(node.getChildren(), hasObjects));
                if (copy.getPath() == null && copy.getChildren().isEmpty()) {
                    continue;
                }
                result.add(copy);
            }
        }
        return result;
    }

    private boolean canSee(MenuDTO node, Set<String> hasObjects) {
        if (node.getRequires() == null || node.getRequires().isEmpty()) {
            return true;
        }
        for (String req : node.getRequires()) {
            if (hasObjects.contains(req)) {
                return true;
            }
        }
        return false;
    }

    private MenuDTO copyNode(MenuDTO source) {
        MenuDTO target = new MenuDTO();
        target.setName(source.getName());
        target.setPath(source.getPath());
        target.setRequires(new ArrayList<>(source.getRequires()));
        return target;
    }
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
