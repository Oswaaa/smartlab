package com.smartlab.management.controller.workflow;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.entity.workflow.FlowModels;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflow")
/**
 * 实验协同工作流模板设计与发布控制器。管理基于流程图编排生成的协同控制逻辑逻辑模板。
 */
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping("/list")
    public ApiResponse<List<FlowModels>> list() {
        return ApiResponse.ok(workflowService.list());
    }

    @PostMapping("/save")
    public ApiResponse<Map<String, String>> save(@RequestBody Map<String, Object> payload) {
        try {
            FlowModels model = workflowService.savePayload(payload);
            return ApiResponse.ok(Map.of("templateId", String.valueOf(model.getId())));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable String id) {
        try {
            workflowService.delete(id);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping({"/export/{id}", "/detail/{id}"})
    public ApiResponse<FlowModels> detail(@PathVariable String id) {
        FlowModels template = workflowService.getById(id);
        return template == null ? ApiResponse.fail("流程模板不存在: " + id) : ApiResponse.ok(template);
    }

}

