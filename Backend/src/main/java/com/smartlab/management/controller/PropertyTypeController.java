package com.smartlab.management.controller;

import com.smartlab.management.dto.ApiResponse;
import com.smartlab.management.dto.PageResult;
import com.smartlab.management.entity.PropertyType;
import com.smartlab.management.service.db.resource.data.PropertyTypeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/data/property-type")
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

