package com.smartlab.management.controller.workflow;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.dto.workflow.WorkflowPreparationResponse;
import com.smartlab.management.dto.workflow.WorkflowResourceRequirementsResponse;
import com.smartlab.management.dto.workflow.WorkflowSaveRequest;
import com.smartlab.management.entity.workflow.FlowModels;
import com.smartlab.management.service.db.workflow.WorkflowTaskResourceService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflow")
public class WorkflowController {
    private final WorkflowService workflowService;
    private final WorkflowTaskResourceService resourceService;

    public WorkflowController(WorkflowService workflowService, WorkflowTaskResourceService resourceService) {
        this.workflowService = workflowService;
        this.resourceService = resourceService;
    }

    @GetMapping("/list")
    public ApiResponse<List<FlowModels>> list() {
        return ApiResponse.ok(workflowService.list());
    }

    @PostMapping("/draft")
    public ApiResponse<WorkflowPreparationResponse> saveDraft(@Valid @RequestBody WorkflowSaveRequest request) {
        return ApiResponse.ok(workflowService.saveDraft(request));
    }

    @PostMapping("/publish")
    public ApiResponse<WorkflowPreparationResponse> publish(@Valid @RequestBody WorkflowSaveRequest request) {
        return ApiResponse.ok(workflowService.publish(request));
    }

    @PostMapping("/save")
    public ApiResponse<Map<String, Long>> save(@Valid @RequestBody WorkflowSaveRequest request) {
        WorkflowPreparationResponse published = workflowService.publish(request);
        if (!published.published()) {
            String message = published.issues().stream().filter(issue -> issue.blocking()).map(issue -> issue.message()).findFirst()
                    .orElse("流程模型未发布");
            return ApiResponse.fail(message);
        }
        Long workflowId = published.definition() == null ? null : published.definition().getId();
        return workflowId == null ? ApiResponse.fail("流程模型未发布") : ApiResponse.ok(Map.of("workflowId", workflowId));
    }
    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        try {
            workflowService.deleteDefinition(id);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/{id}/requirements")
    public ApiResponse<WorkflowResourceRequirementsResponse> requirements(@PathVariable Long id) {
        workflowService.requireExecutableDefinition(id);
        return ApiResponse.ok(resourceService.requirements(id));
    }
    @GetMapping({"/export/{id}", "/detail/{id}"})
    public ApiResponse<WorkflowDetailResponse> detail(@PathVariable Long id) {
        WorkflowDetailResponse definition = workflowService.getDefinition(id);
        return definition == null ? ApiResponse.fail("流程模型不存在: " + id) : ApiResponse.ok(definition);
    }
}
