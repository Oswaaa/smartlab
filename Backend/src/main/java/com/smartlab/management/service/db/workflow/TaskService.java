package com.smartlab.management.service.db.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.dto.workflow.TaskCreateRequest;
import com.smartlab.management.dto.workflow.TaskMonitorSummary;
import com.smartlab.management.entity.workflow.ExecutionLog;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.mapper.workflow.TaskStepMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TaskService extends ManagementCrudService<Task> {
    private final TaskMapper taskMapper;
    private final TaskStepMapper taskStepMapper;
    private final ExecutionLogService executionLogService;
    private final WorkflowService workflowService;
    private final WorkflowTaskResourceService resourceService;

    public TaskService(TaskMapper taskMapper, TaskStepMapper taskStepMapper,
                       ExecutionLogService executionLogService, WorkflowService workflowService,
                       WorkflowTaskResourceService resourceService) {
        super(taskMapper);
        this.taskMapper = taskMapper;
        this.taskStepMapper = taskStepMapper;
        this.executionLogService = executionLogService;
        this.workflowService = workflowService;
        this.resourceService = resourceService;
    }

    public PageResult<Task> page(long pageNo, long pageSize, String keyword, String status) {
        LambdaQueryWrapper<Task> query = Wrappers.lambdaQuery();
        if (keyword != null && !keyword.isBlank()) query.like(Task::getTaskName, keyword.trim());
        if (status != null && !status.isBlank()) query.eq(Task::getTaskStatus, status.trim());
        query.orderByDesc(Task::getId);
        Page<Task> page = taskMapper.selectPage(new Page<>(Math.max(1, pageNo), Math.max(1, pageSize)), query);
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    public Map<String, Long> summary() {
        Map<String, Long> result = new HashMap<>();
        result.put("total", countAll());
        for (String status : List.of("PENDING", "RUNNING", "COMPLETED", "FAILED", "ABORTED"))
            result.put(status.toLowerCase(), countByStatus(status));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Task create(TaskCreateRequest request) {
        if (request == null || request.getTaskName() == null || request.getTaskName().isBlank())
            throw new IllegalArgumentException("任务名称不能为空");
        if (request.getFlowModelId() == null || workflowService.getDefinition(request.getFlowModelId()) == null)
            throw new IllegalArgumentException("任务引用的流程模型不存在");
        resourceService.validate(request.getFlowModelId(), request.getResourceMap());
        Task task = new Task();
        task.setFlowModelId(request.getFlowModelId());
        task.setTaskName(request.getTaskName().trim());
        task.setTaskDesc(request.getTaskDesc());
        task.setParentTaskId(request.getParentTaskId());
        task.setTaskStatus("PENDING");
        task.setTaskConstraints(nonNullObject(request.getTaskConstraints()));
        task.setResourceMap(nonNullObject(request.getResourceMap()));
        task.setTaskVariables(nonNullObject(request.getTaskVariables()));
        task.setCreatorId(request.getCreatorId());
        taskMapper.insert(task);
        executionLogService.append("TASK", task.getId(), null, null, "INFO", "任务已创建");
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public Task start(Long taskId) {
        Task task = requireTask(taskId);
        if (!"PENDING".equals(task.getTaskStatus()))
            throw new IllegalStateException("只有 PENDING 任务可以启动，当前状态: " + task.getTaskStatus());
        resourceService.validate(task.getFlowModelId(), task.getResourceMap());
        task.setTaskStatus("RUNNING");
        task.setStartTime(OffsetDateTime.now());
        task.setEndTime(null);
        taskMapper.updateById(task);
        executionLogService.append("TASK", taskId, null, null, "INFO", "任务已启动");
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public Task abort(Long taskId) {
        Task task = requireTask(taskId);
        if (!"PENDING".equals(task.getTaskStatus()) && !"RUNNING".equals(task.getTaskStatus()))
            throw new IllegalStateException("只有 PENDING 或 RUNNING 任务可以终止，当前状态: " + task.getTaskStatus());
        task.setTaskStatus("ABORTED");
        task.setEndTime(OffsetDateTime.now());
        task.setCurrentFlowNodeId(null);
        task.setCurrentNodeIdRef(null);
        taskMapper.updateById(task);
        TaskStep patch = new TaskStep();
        patch.setNodeStatus("ABORTED");
        patch.setEndTime(OffsetDateTime.now());
        taskStepMapper.update(patch, Wrappers.<TaskStep>lambdaUpdate()
                .eq(TaskStep::getTaskId, taskId).in(TaskStep::getNodeStatus, "PENDING", "RUNNING"));
        executionLogService.append("TASK", taskId, null, null, "WARN", "任务已终止");
        return task;
    }

    public List<ExecutionLog> logs(Long taskId, Long afterLogId, Integer limit) {
        return executionLogService.byTask(taskId, afterLogId, limit);
    }

    public List<TaskStep> snapshots(Long taskId) {
        return taskStepMapper.selectList(Wrappers.<TaskStep>lambdaQuery()
                .eq(TaskStep::getTaskId, taskId).orderByAsc(TaskStep::getId));
    }

    public List<Task> listByCreator(Long creatorId) {
        if (creatorId == null) return List.of();
        return taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .eq(Task::getCreatorId, creatorId).orderByDesc(Task::getId));
    }

    public TaskMonitorSummary monitorSummary() {
        TaskMonitorSummary summary = new TaskMonitorSummary();
        summary.setRunningTasks(listByStatus("RUNNING"));
        summary.setPendingTasks(listByStatus("PENDING"));
        OffsetDateTime today = OffsetDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        summary.setTodayCompletedCount(taskMapper.selectCount(Wrappers.<Task>lambdaQuery()
                .eq(Task::getTaskStatus, "COMPLETED").ge(Task::getEndTime, today)));
        summary.setTodayFailedCount(taskMapper.selectCount(Wrappers.<Task>lambdaQuery()
                .eq(Task::getTaskStatus, "FAILED").ge(Task::getEndTime, today)));
        return summary;
    }

    public List<Task> listByStatus(String status) {
        if (status == null || status.isBlank()) return list();
        return taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .eq(Task::getTaskStatus, status.trim()).orderByDesc(Task::getId));
    }

    public Long countByStatus(String status) {
        return taskMapper.selectCount(Wrappers.<Task>lambdaQuery().eq(Task::getTaskStatus, status));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long id) {
        Task task = requireTask(id);
        if ("RUNNING".equals(task.getTaskStatus())) throw new IllegalStateException("运行中的任务不能删除");
        taskStepMapper.delete(Wrappers.<TaskStep>lambdaQuery().eq(TaskStep::getTaskId, id));
        taskMapper.deleteById(id);
    }

    private Task requireTask(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) throw new IllegalArgumentException("任务不存在");
        return task;
    }

    private com.fasterxml.jackson.databind.JsonNode nonNullObject(com.fasterxml.jackson.databind.JsonNode node) {
        return node != null && node.isObject() ? node : JsonNodeSupport.objectNode();
    }
}
