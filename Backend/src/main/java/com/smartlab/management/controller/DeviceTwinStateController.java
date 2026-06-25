package com.smartlab.management.controller;

import com.smartlab.management.dto.ApiResponse;
import com.smartlab.management.entity.DeviceTwinStates;
import com.smartlab.management.service.db.resource.device.DeviceTwinStateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/device/twin-state")
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

