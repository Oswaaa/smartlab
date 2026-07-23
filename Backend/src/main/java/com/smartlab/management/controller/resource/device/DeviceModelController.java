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
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) Long categoryId) {
        return ApiResponse.ok(deviceModelService.page(pageNo, pageSize, keyword, categoryId));
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

