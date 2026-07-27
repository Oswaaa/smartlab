package com.smartlab.management.service.db.workflow;

import com.fasterxml.jackson.databind.JsonNode;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlab.global.contract.TaskLifecycleState;
import com.smartlab.global.event.TaskLifecycleObservationEvent;
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
import com.smartlab.management.service.db.constraint.TaskConstraintService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TaskService extends ManagementCrudService<Task> {
    private static final Set<String> ACTIVE_TASK_STATES = Set.of("RUNNING", "PAUSED", "TERMINATING");
    private static final Set<String> ACTIVE_NODE_STATES = Set.of("PENDING", "RUNNING", "TERMINATING");

    private final TaskMapper taskMapper;
    private final TaskStepMapper taskStepMapper;
    private final ExecutionLogService executionLogService;
    private final WorkflowService workflowService;
    private final WorkflowTaskResourceService resourceService;
    private final TaskConstraintService taskConstraintService;
    private final ApplicationEventPublisher eventPublisher;

    public TaskService(TaskMapper taskMapper, TaskStepMapper taskStepMapper,
                       ExecutionLogService executionLogService, WorkflowService workflowService,
                       WorkflowTaskResourceService resourceService, TaskConstraintService taskConstraintService,
                       ApplicationEventPublisher eventPublisher) {
        super(taskMapper);
        this.taskMapper = taskMapper;
        this.taskStepMapper = taskStepMapper;
        this.executionLogService = executionLogService;
        this.workflowService = workflowService;
        this.resourceService = resourceService;
        this.taskConstraintService = taskConstraintService;
        this.eventPublisher = eventPublisher;
    }

    public PageResult<Task> page(long pageNo, long pageSize, String keyword, String status) {
        var query = Wrappers.<Task>lambdaQuery();
        if (keyword != null && !keyword.isBlank()) query.like(Task::getTaskName, keyword.trim());
        if (status != null && !status.isBlank()) query.eq(Task::getTaskStatus, status.trim());
        query.orderByDesc(Task::getId);
        Page<Task> page = taskMapper.selectPage(new Page<>(Math.max(1, pageNo), Math.max(1, pageSize)), query);
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    public Map<String, Long> summary() {
        Map<String, Long> result = new HashMap<>();
        result.put("total", countAll());
        for (TaskLifecycleState state : TaskLifecycleState.values()) {
            result.put(state.name().toLowerCase(), countByStatus(state.name()));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Task create(TaskCreateRequest request) {
        if (request == null || request.getTaskName() == null || request.getTaskName().isBlank()) {
            throw new IllegalArgumentException("任务名称不能为空");
        }
        if (request.getFlowModelId() == null || workflowService.getDefinition(request.getFlowModelId()) == null) {
            throw new IllegalArgumentException("任务引用的工作流模型不存在");
        }
        JsonNode resourceMap = nonNullObject(request.getResourceMap());
        resourceService.validate(request.getFlowModelId(), resourceMap);
        Task task = new Task();
        task.setFlowModelId(request.getFlowModelId());
        task.setTaskName(request.getTaskName().trim());
        task.setTaskDesc(request.getTaskDesc());
        task.setParentTaskId(request.getParentTaskId());
        task.setTaskStatus(TaskLifecycleState.PENDING.name());
        task.setTaskConstraints(JsonNodeSupport.arrayNode());
        task.setResourceMap(resourceMap);
        task.setTaskVariables(nonNullObject(request.getTaskVariables()));
        task.setCreatorId(request.getCreatorId());
        taskMapper.insert(task);
        task.setTaskConstraints(taskConstraintService.normalizeAndValidate(task, request.getTaskConstraints()));
        taskMapper.updateById(task);
        executionLogService.append("TASK", task.getId(), null, null, "INFO", "任务已创建");
        publishLifecycle(task);
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public Task start(Long taskId) {
        Task task = requireTask(taskId);
        requireStatus(task, TaskLifecycleState.PENDING);
        resourceService.validate(task.getFlowModelId(), task.getResourceMap());
        task.setTaskConstraints(taskConstraintService.normalizeAndValidate(task, task.getTaskConstraints()));
        task.setTaskStatus(TaskLifecycleState.RUNNING.name());
        task.setStartTime(OffsetDateTime.now());
        task.setEndTime(null);
        taskMapper.updateById(task);
        executionLogService.append("TASK", taskId, null, null, "INFO", "任务已启动");
        publishLifecycle(task);
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public Task pause(Long taskId) {
        Task task = requireTask(taskId);
        requireStatus(task, TaskLifecycleState.RUNNING);
        if (hasRunningDeviceStep(taskId)) {
            throw new IllegalStateException("任务存在正在执行的设备能力节点，当前协议没有暂停设备命令，不能将任务标记为PAUSED");
        }
        task.setTaskStatus(TaskLifecycleState.PAUSED.name());
        taskMapper.updateById(task);
        executionLogService.append("TASK", taskId, null, null, "WARN", "任务已暂停");
        publishLifecycle(task);
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public Task resume(Long taskId) {
        Task task = requireTask(taskId);
        requireStatus(task, TaskLifecycleState.PAUSED);
        task.setTaskStatus(TaskLifecycleState.RUNNING.name());
        taskMapper.updateById(task);
        executionLogService.append("TASK", taskId, null, null, "INFO", "任务已恢复");
        publishLifecycle(task);
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public Task requestTermination(Long taskId) {
        Task task = requireTask(taskId);
        if (TaskLifecycleState.TERMINATING.name().equals(task.getTaskStatus())) return task;
        if (!TaskLifecycleState.RUNNING.name().equals(task.getTaskStatus())
                && !TaskLifecycleState.PAUSED.name().equals(task.getTaskStatus())) {
            throw new IllegalStateException("只有RUNNING或PAUSED任务可以终止，当前状态: " + task.getTaskStatus());
        }
        task.setTaskStatus(TaskLifecycleState.TERMINATING.name());
        taskMapper.updateById(task);
        executionLogService.append("TASK", taskId, null, null, "WARN", "任务进入终止流程");
        publishLifecycle(task);
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public Task completeTerminationIfSettled(Long taskId) {
        Task task = requireTask(taskId);
        if (!TaskLifecycleState.TERMINATING.name().equals(task.getTaskStatus())) return task;
        Long activeCount = taskStepMapper.selectCount(Wrappers.<TaskStep>lambdaQuery()
                .eq(TaskStep::getTaskId, taskId).in(TaskStep::getNodeStatus, ACTIVE_NODE_STATES));
        if (activeCount != null && activeCount > 0) return task;
        task.setTaskStatus(TaskLifecycleState.TERMINATED.name());
        task.setEndTime(OffsetDateTime.now());
        task.setCurrentFlowNodeId(null);
        task.setCurrentNodeIdRef(null);
        taskMapper.updateById(task);
        executionLogService.append("TASK", taskId, null, null, "WARN", "任务终止完成");
        publishLifecycle(task);
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
        summary.setRunningTasks(listByStatus(TaskLifecycleState.RUNNING.name()));
        summary.setPendingTasks(listByStatus(TaskLifecycleState.PENDING.name()));
        OffsetDateTime today = OffsetDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        summary.setTodayCompletedCount(taskMapper.selectCount(Wrappers.<Task>lambdaQuery()
                .eq(Task::getTaskStatus, TaskLifecycleState.SUCCEEDED.name()).ge(Task::getEndTime, today)));
        summary.setTodayFailedCount(taskMapper.selectCount(Wrappers.<Task>lambdaQuery()
                .eq(Task::getTaskStatus, TaskLifecycleState.FAILED.name()).ge(Task::getEndTime, today)));
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
        if (ACTIVE_TASK_STATES.contains(task.getTaskStatus())) throw new IllegalStateException("执行中的任务不能删除");
        taskStepMapper.delete(Wrappers.<TaskStep>lambdaQuery().eq(TaskStep::getTaskId, id));
        taskMapper.deleteById(id);
    }

    private boolean hasRunningDeviceStep(Long taskId) {
        return taskStepMapper.selectList(Wrappers.<TaskStep>lambdaQuery()
                        .eq(TaskStep::getTaskId, taskId).eq(TaskStep::getNodeStatus, "RUNNING"))
                .stream().anyMatch(step -> step.getInterfaceInSnapshot() != null
                        && step.getInterfaceInSnapshot().path("deviceInstanceId").canConvertToLong()
                        && step.getInterfaceInSnapshot().path("deviceInstanceId").asLong() > 0);
    }

    private Task requireTask(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) throw new IllegalArgumentException("任务不存在");
        return task;
    }

    private void requireStatus(Task task, TaskLifecycleState expected) {
        if (!expected.name().equals(task.getTaskStatus())) {
            throw new IllegalStateException("任务状态必须为" + expected.name() + "，当前状态: " + task.getTaskStatus());
        }
    }

    private com.fasterxml.jackson.databind.JsonNode nonNullObject(com.fasterxml.jackson.databind.JsonNode node) {
        return node != null && node.isObject() ? node : JsonNodeSupport.objectNode();
    }

    private void publishLifecycle(Task task) {
        eventPublisher.publishEvent(new TaskLifecycleObservationEvent(task.getId(), task.getTaskStatus(), java.time.Instant.now()));
    }
}
