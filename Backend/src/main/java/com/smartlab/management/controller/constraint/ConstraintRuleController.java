package com.smartlab.management.controller.constraint;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.constraint.ConstraintRule;
import com.smartlab.management.service.db.constraint.ConstraintRuleService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/constraint/rule")
public class ConstraintRuleController {

    private final ConstraintRuleService service;

    public ConstraintRuleController(ConstraintRuleService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ConstraintRule>> list(@RequestParam(required = false) Boolean isEnabled) {
        return ApiResponse.ok(service.list(isEnabled));
    }

    @GetMapping("/options")
    public ApiResponse<Map<String, Object>> options() {
        return ApiResponse.ok(service.options());
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<ConstraintRule>> page(@RequestParam(defaultValue = "1") long pageNo,
                                                         @RequestParam(defaultValue = "20") long pageSize,
                                                         @RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) Boolean isEnabled) {
        return ApiResponse.ok(service.page(pageNo, pageSize, keyword, isEnabled));
    }

    @GetMapping("/{id}")
    public ApiResponse<ConstraintRule> getById(@PathVariable Long id) {
        ConstraintRule rule = service.getById(id);
        return rule == null ? ApiResponse.fail("约束模型不存在") : ApiResponse.ok(rule);
    }

    @PostMapping
    public ApiResponse<ConstraintRule> create(@RequestBody ConstraintRule rule) {
        return save(rule);
    }

    @PutMapping("/{id}")
    public ApiResponse<ConstraintRule> update(@PathVariable Long id, @RequestBody ConstraintRule rule) {
        rule.setId(id);
        return save(rule);
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

    private ApiResponse<ConstraintRule> save(ConstraintRule rule) {
        try {
            return ApiResponse.ok(service.save(rule));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}
