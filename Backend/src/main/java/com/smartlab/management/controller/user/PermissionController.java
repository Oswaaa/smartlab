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

