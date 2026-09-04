package com.smartlab.management.controller.workflow;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.workflow.WorkflowSimulateRequest;
import com.smartlab.management.dto.workflow.WorkflowSimulationReport;
import com.smartlab.management.service.db.workflow.WorkflowSimulationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflow")
public class WorkflowSimulationController {

    private final WorkflowSimulationService simulationService;

    public WorkflowSimulationController(WorkflowSimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/simulate")
    public ApiResponse<WorkflowSimulationReport> simulate(@RequestBody WorkflowSimulateRequest request) {
        try {
            return ApiResponse.ok(simulationService.simulate(request));
        } catch (Exception error) {
            return ApiResponse.fail(error.getMessage());
        }
    }

    @GetMapping("/simulate/{taskId}")
    public ApiResponse<WorkflowSimulationReport> report(@PathVariable Long taskId) {
        try {
            return ApiResponse.ok(simulationService.report(taskId));
        } catch (Exception error) {
            return ApiResponse.fail(error.getMessage());
        }
    }
}
