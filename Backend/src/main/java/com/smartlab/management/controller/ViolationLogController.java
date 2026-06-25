package com.smartlab.management.controller;

import com.smartlab.management.dto.ApiResponse;
import com.smartlab.management.dto.PageResult;
import com.smartlab.management.entity.ViolationLog;
import com.smartlab.management.service.db.constraint.ViolationLogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/constraint/violation")
public class ViolationLogController {

    private final ViolationLogService service;

    public ViolationLogController(ViolationLogService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ViolationLog>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<ViolationLog>> page(@RequestParam(defaultValue = "1") long pageNo,
                                                      @RequestParam(defaultValue = "20") long pageSize,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) Long constraintRuleId,
                                                      @RequestParam(required = false) Long taskId,
                                                      @RequestParam(required = false) Long deviceInstanceId) {
        return ApiResponse.ok(service.page(pageNo, pageSize, keyword, constraintRuleId, taskId, deviceInstanceId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ViolationLog> get(@PathVariable Long id) {
        ViolationLog entity = service.getById(id);
        return entity == null ? ApiResponse.fail("违规日志不存在") : ApiResponse.ok(entity);
    }

    @PostMapping
    public ApiResponse<ViolationLog> create(@RequestBody ViolationLog entity) {
        try {
            return ApiResponse.ok(service.save(entity));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<ViolationLog> update(@PathVariable Long id, @RequestBody ViolationLog entity) {
        try {
            entity.setId(id);
            return ApiResponse.ok(service.save(entity));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}
