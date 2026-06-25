package com.smartlab.management.controller;

import com.smartlab.management.dto.ApiResponse;
import com.smartlab.management.dto.PageResult;
import com.smartlab.management.entity.ResourceStructure;
import com.smartlab.management.service.db.resource.scene.ResourceStructureService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/resource/structure")
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

