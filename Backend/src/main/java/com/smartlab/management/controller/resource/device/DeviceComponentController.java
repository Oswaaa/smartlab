package com.smartlab.management.controller.resource.device;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.resource.device.DeviceComponents;
import com.smartlab.management.service.db.resource.device.DeviceComponentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/device/component")
/**
 * 复杂集成设备内部零部件/子组件生命周期管理控制器。
 */
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

