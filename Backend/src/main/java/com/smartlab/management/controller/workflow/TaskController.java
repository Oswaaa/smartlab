package com.smartlab.management.controller.workflow;

import com.smartlab.engine.workflow.WorkflowTaskControlService;
import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.dto.workflow.TaskCreateRequest;
import com.smartlab.management.dto.workflow.TaskPreflightRequest;
import com.smartlab.management.dto.workflow.TaskPreflightResponse;
import com.smartlab.management.dto.workflow.TaskMonitorSummary;
import com.smartlab.management.entity.workflow.ExecutionLog;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.workflow.TaskExecutionViewService;
import com.smartlab.management.service.db.workflow.TaskService;
import com.smartlab.management.dto.workflow.TaskExecutionView;
import com.smartlab.management.sse.TaskExecutionSseHub;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/task")
public class TaskController {
    private final TaskService taskService;
    private final WorkflowTaskControlService taskControlService;
    private final TaskExecutionViewService executionViewService;
    private final TaskExecutionSseHub taskExecutionSseHub;

    public TaskController(TaskService taskService, WorkflowTaskControlService taskControlService,
                          TaskExecutionViewService executionViewService,
                          TaskExecutionSseHub taskExecutionSseHub) {
        this.taskService = taskService;
        this.taskControlService = taskControlService;
        this.executionViewService = executionViewService;
        this.taskExecutionSseHub = taskExecutionSseHub;
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<Task>> page(@RequestParam(defaultValue = "1") long pageNo,
                                              @RequestParam(defaultValue = "20") long pageSize,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) Long flowModelId) {
        return ApiResponse.ok(taskService.page(pageNo, pageSize, keyword, status, flowModelId));
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Long>> summary() { return ApiResponse.ok(taskService.summary()); }

    @GetMapping("/monitor/summary")
    public ApiResponse<TaskMonitorSummary> monitorSummary() { return ApiResponse.ok(taskService.monitorSummary()); }

    @GetMapping("/{id}")
    public ApiResponse<Task> get(@PathVariable Long id) {
        Task task = taskService.getById(id);
        return task == null ? ApiResponse.fail("任务不存在") : ApiResponse.ok(task);
    }

    @PostMapping("/save")
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody TaskCreateRequest request) {
        try { return ApiResponse.ok(Map.of("taskId", taskService.create(request).getId())); }
        catch (Exception e) { return ApiResponse.fail(e.getMessage()); }
    }

    @PostMapping("/preflight")
    public ApiResponse<TaskPreflightResponse> preflight(@RequestBody TaskPreflightRequest request) {
        return ApiResponse.ok(taskService.preflight(request));
    }

    @PostMapping("/start/{id}")
    public ApiResponse<Task> start(@PathVariable Long id) {
        try { return ApiResponse.ok(taskControlService.start(id)); }
        catch (Exception e) { return ApiResponse.fail(e.getMessage()); }
    }

    @PostMapping("/pause/{id}")
    public ApiResponse<Task> pause(@PathVariable Long id) {
        try { return ApiResponse.ok(taskControlService.pause(id)); }
        catch (Exception e) { return ApiResponse.fail(e.getMessage()); }
    }

    @PostMapping("/resume/{id}")
    public ApiResponse<Task> resume(@PathVariable Long id) {
        try { return ApiResponse.ok(taskControlService.resume(id)); }
        catch (Exception e) { return ApiResponse.fail(e.getMessage()); }
    }

    @PostMapping("/terminate/{id}")
    public ApiResponse<Task> terminate(@PathVariable Long id) {
        try { return ApiResponse.ok(taskControlService.terminate(id)); }
        catch (Exception e) { return ApiResponse.fail(e.getMessage()); }
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        try { taskService.deleteTask(id); return ApiResponse.ok("删除成功"); }
        catch (Exception e) { return ApiResponse.fail(e.getMessage()); }
    }

    @GetMapping("/logs/{taskId}")
    public ApiResponse<List<ExecutionLog>> logs(@PathVariable Long taskId,
                                                @RequestParam(required = false) Long afterLogId,
                                                @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(taskService.logs(taskId, afterLogId, limit));
    }

    @GetMapping("/snapshots/{taskId}")
    public ApiResponse<List<TaskStep>> snapshots(@PathVariable Long taskId) {
        return ApiResponse.ok(taskService.snapshots(taskId));
    }

    @GetMapping("/execution-view/{taskId}")
    public ApiResponse<TaskExecutionView> executionView(@PathVariable Long taskId) {
        try { return ApiResponse.ok(executionViewService.get(taskId)); }
        catch (Exception e) { return ApiResponse.fail(e.getMessage()); }
    }

    @GetMapping(value = "/stream/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long id) {
        return taskExecutionSseHub.register(id);
    }
}
