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

