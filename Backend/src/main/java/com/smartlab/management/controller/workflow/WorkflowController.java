package com.smartlab.management.controller.workflow;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.dto.workflow.WorkflowModelDocument;
import com.smartlab.management.dto.workflow.WorkflowModelDocuments;
import com.smartlab.management.dto.workflow.WorkflowPreparationApiResponse;
import com.smartlab.management.dto.workflow.WorkflowPreparationResponse;
import com.smartlab.management.dto.workflow.WorkflowResourceRequirementsResponse;
import com.smartlab.management.dto.workflow.WorkflowSummaryResponse;
import com.smartlab.management.dto.workflow.WorkflowViewResponse;
import com.smartlab.management.service.db.workflow.WorkflowTaskResourceService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import com.smartlab.management.service.db.workflow.WorkflowSuccessorExistsException;
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
    public ApiResponse<List<WorkflowSummaryResponse>> list() {
        return ApiResponse.ok(workflowService.list().stream().map(WorkflowModelDocuments::toSummary).toList());
    }

    @PostMapping("/draft")
    public ApiResponse<WorkflowPreparationApiResponse> saveDraft(@Valid @RequestBody WorkflowModelDocument document) {
        try {
            return ApiResponse.ok(WorkflowModelDocuments.toPreparation(workflowService.saveDraft(document)));
        } catch (WorkflowSuccessorExistsException exception) {
            return successorConflict(exception);
        } catch (RuntimeException exception) {
            return ApiResponse.fail(exception.getMessage());
        }
    }

    @PostMapping("/copy")
    public ApiResponse<WorkflowPreparationApiResponse> saveAsNew(@Valid @RequestBody WorkflowModelDocument document) {
        try {
            return ApiResponse.ok(WorkflowModelDocuments.toPreparation(workflowService.saveAsNew(document)));
        } catch (RuntimeException exception) {
            return ApiResponse.fail(exception.getMessage());
        }
    }

    @PostMapping("/validate")
    public ApiResponse<WorkflowPreparationApiResponse> validate(@Valid @RequestBody WorkflowModelDocument document) {
        return ApiResponse.ok(WorkflowModelDocuments.toPreparation(workflowService.validate(document)));
    }

    @PostMapping("/publish")
    public ApiResponse<WorkflowPreparationApiResponse> publish(@Valid @RequestBody WorkflowModelDocument document) {
        try {
            return ApiResponse.ok(WorkflowModelDocuments.toPreparation(workflowService.publish(document)));
        } catch (WorkflowSuccessorExistsException exception) {
            return successorConflict(exception);
        } catch (RuntimeException exception) {
            return ApiResponse.fail(exception.getMessage());
        }
    }

    @PostMapping("/save")
    public ApiResponse<Map<String, Long>> save(@Valid @RequestBody WorkflowModelDocument document) {
        try {
            WorkflowPreparationResponse published = workflowService.publish(document);
            if (!published.published()) {
                String message = published.issues().stream().filter(issue -> issue.blocking()).map(issue -> issue.message()).findFirst()
                        .orElse("流程模型未发布");
                return ApiResponse.fail(message);
            }
            Long workflowId = published.definition() == null ? null : published.definition().getId();
            return workflowId == null ? ApiResponse.fail("流程模型未发布") : ApiResponse.ok(Map.of("workflowId", workflowId));
        } catch (WorkflowSuccessorExistsException exception) {
            return ApiResponse.fail(exception.getMessage());
        } catch (RuntimeException exception) {
            return ApiResponse.fail(exception.getMessage());
        }
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

    @GetMapping("/detail/{id}")
    public ApiResponse<WorkflowViewResponse> detail(@PathVariable Long id) {
        WorkflowDetailResponse definition = workflowService.getDefinition(id);
        return definition == null ? ApiResponse.fail("流程模型不存在: " + id) : ApiResponse.ok(WorkflowModelDocuments.toView(definition));
    }

    @GetMapping("/export/{id}")
    public ApiResponse<WorkflowModelDocument> export(@PathVariable Long id) {
        WorkflowDetailResponse definition = workflowService.getDefinition(id);
        return definition == null ? ApiResponse.fail("流程模型不存在: " + id) : ApiResponse.ok(WorkflowModelDocuments.toDocument(definition));
    }

    @SuppressWarnings("unchecked")
    private static <T> ApiResponse<T> successorConflict(WorkflowSuccessorExistsException exception) {
        return (ApiResponse<T>) ApiResponse.fail(exception.getMessage(), exception.conflict());
    }
}
