package com.smartlab.management.controller;

import com.smartlab.management.dto.ApiResponse;
import com.smartlab.management.dto.PageResult;
import com.smartlab.management.entity.DeviceCategory;
import com.smartlab.management.service.db.resource.device.DeviceCategoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/device/category")
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

