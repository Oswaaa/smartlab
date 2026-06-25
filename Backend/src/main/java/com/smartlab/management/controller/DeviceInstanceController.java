package com.smartlab.management.controller;

import com.smartlab.management.dto.ApiResponse;
import com.smartlab.management.dto.PageResult;
import com.smartlab.management.entity.DeviceInstances;
import com.smartlab.management.entity.DeviceTwinStates;
import com.smartlab.management.service.db.resource.device.DeviceInstanceService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/device/instance")
public class DeviceInstanceController {

    private final DeviceInstanceService deviceInstanceService;

    public DeviceInstanceController(DeviceInstanceService deviceInstanceService) {
        this.deviceInstanceService = deviceInstanceService;
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

    @PostMapping("/control/{id}")
    public ApiResponse<String> control(@PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            String commandId = body == null ? "" : String.valueOf(body.getOrDefault("commandId", ""));
            Map<String, Object> parameters = new HashMap<>();
            if (body != null && body.get("parameters") instanceof Map<?, ?> raw) {
                raw.forEach((key, value) -> parameters.put(String.valueOf(key), value));
            }
            deviceInstanceService.control(id, commandId, parameters);
            return ApiResponse.ok("指令已进入设备状态机");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}

