package com.smartlab.management.controller.constraint;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.constraint.ConstraintRule;
import com.smartlab.management.service.db.constraint.ConstraintRuleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/constraint/rule")
/**
 * 设备安全与逻辑约束规则管理控制器。用于配置实验室设备间相互制约的联锁保护规则与参数阈值约束。
 */
public class ConstraintRuleController {

    private final ConstraintRuleService service;

    public ConstraintRuleController(ConstraintRuleService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ConstraintRule>> getAllRules(@RequestParam(required = false) String sourceType,
                                                         @RequestParam(required = false) String objectEndpoint,
                                                         @RequestParam(required = false) Boolean isEnabled) {
        return ApiResponse.ok(service.list(sourceType, objectEndpoint, isEnabled));
    }

    @GetMapping("/list")
    public ApiResponse<List<ConstraintRule>> list(@RequestParam(required = false) String sourceType,
                                                  @RequestParam(required = false) String objectEndpoint,
                                                  @RequestParam(required = false) Boolean isEnabled) {
        return ApiResponse.ok(service.list(sourceType, objectEndpoint, isEnabled));
    }

    @GetMapping("/options")
    public ApiResponse<Map<String, Object>> options() {
        return ApiResponse.ok(service.options());
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<ConstraintRule>> page(@RequestParam(defaultValue = "1") long pageNo,
                                                        @RequestParam(defaultValue = "20") long pageSize,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String sourceType,
                                                        @RequestParam(required = false) String objectEndpoint,
                                                        @RequestParam(required = false) String objectName,
                                                        @RequestParam(required = false) String operator,
                                                        @RequestParam(required = false) Boolean isEnabled) {
        return ApiResponse.ok(service.page(pageNo, pageSize, keyword, sourceType, objectEndpoint, objectName, operator, isEnabled));
    }

    @GetMapping("/{id}")
    public ApiResponse<ConstraintRule> getById(@PathVariable Long id) {
        ConstraintRule rule = service.getById(id);
        return rule == null ? ApiResponse.fail("约束规则不存在") : ApiResponse.ok(rule);
    }

    @PostMapping
    public ApiResponse<ConstraintRule> createRule(@RequestBody ConstraintRule rule) {
        try {
            return ApiResponse.ok(service.save(rule));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/save")
    public ApiResponse<ConstraintRule> save(@RequestBody ConstraintRule rule) {
        try {
            return ApiResponse.ok(service.save(rule));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<ConstraintRule> updateRule(@PathVariable Long id, @RequestBody ConstraintRule rule) {
        rule.setId(id);
        try {
            return ApiResponse.ok(service.save(rule));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PatchMapping("/{id}/enabled")
    public ApiResponse<ConstraintRule> setEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        try {
            return ApiResponse.ok(service.setEnabled(id, enabled));
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

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> deleteByPath(@PathVariable Long id) {
        return delete(id);
    }
}