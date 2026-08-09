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

