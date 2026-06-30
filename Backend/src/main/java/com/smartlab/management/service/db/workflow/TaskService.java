package com.smartlab.management.service.db.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.dto.workflow.TaskMonitorSummary;
import com.smartlab.management.entity.workflow.StepLog;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.mapper.workflow.StepLogMapper;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.mapper.workflow.TaskStepMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务表服务。
 * 对应 TASK、TASK_STEP、STEP_LOG 三张任务运行记录表。
 */
@Service
/**
 * 实验任务工作流生命周期控制与守护调度业务服务。
 */
public class TaskService extends ManagementCrudService<Task> {

    private final TaskMapper taskMapper;
    private final TaskStepMapper taskStepMapper;
    private final StepLogMapper stepLogMapper;

    public TaskService(TaskMapper taskMapper, TaskStepMapper taskStepMapper, StepLogMapper stepLogMapper) {
        super(taskMapper);
        this.taskMapper = taskMapper;
        this.taskStepMapper = taskStepMapper;
        this.stepLogMapper = stepLogMapper;
    }

    public PageResult<Task> page(long pageNo, long pageSize, String keyword, String status) {
        LambdaQueryWrapper<Task> query = Wrappers.lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            query.like(Task::getTaskName, keyword.trim());
        }
        if (status != null && !status.isBlank()) {
            query.eq(Task::getTaskStatus, status.trim());
        }
        query.orderByDesc(Task::getId);
        Page<Task> page = taskMapper.selectPage(new Page<>(Math.max(1, pageNo), Math.max(1, pageSize)), query);
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    public Map<String, Long> summary() {
        Map<String, Long> result = new HashMap<>();
        result.put("total", countAll());
        result.put("pending", countByStatus("PENDING"));
        result.put("running", countByStatus("RUNNING"));
        result.put("completed", countByStatus("COMPLETED"));
        result.put("failed", countByStatus("FAILED"));
        result.put("aborted", countByStatus("ABORTED"));
        return result;
    }

    public Task savePayload(Map<String, Object> payload) {
        Task task = new Task();
        Object id = first(payload, "id", "taskId");
        if (id != null && !String.valueOf(id).isBlank()) {
            task.setId(Long.valueOf(String.valueOf(id)));
        }
        Object flowModelId = first(payload, "flowModelId", "templateId");
        if (flowModelId != null && !String.valueOf(flowModelId).isBlank()) {
            task.setFlowModelId(Long.valueOf(String.valueOf(flowModelId)));
        }
        task.setTaskName(stringValue(first(payload, "taskName", "name")));
        task.setTaskDesc(stringValue(first(payload, "taskDesc", "description")));
        task.setTaskStatus(stringValue(first(payload, "taskStatus", "currentStatus")));
        if (task.getTaskStatus() == null) {
            task.setTaskStatus("PENDING");
        }
        task.setTaskConstraints(JsonNodeSupport.toNode(first(payload, "taskConstraints", "globalConstraints")));
        task.setResourceMap(JsonNodeSupport.toNode(first(payload, "resourceMap")));
        task.setTaskVariables(JsonNodeSupport.toNode(first(payload, "taskVariables", "variables")));
        Object creatorId = first(payload, "creatorId");
        if (creatorId != null && !String.valueOf(creatorId).isBlank()) {
            task.setCreatorId(Long.valueOf(String.valueOf(creatorId)));
        }
        if (task.getId() == null) {
            taskMapper.insert(task);
        } else {
            taskMapper.updateById(task);
        }
        return task;
    }

    public Task start(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        task.setTaskStatus("RUNNING");
        if (task.getStartTime() == null) {
            task.setStartTime(OffsetDateTime.now());
        }
        taskMapper.updateById(task);
        appendLog(taskId, "TASK", null, "INFO", "任务已启动");
        return task;
    }

    public Task abort(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        task.setTaskStatus("ABORTED");
        task.setEndTime(OffsetDateTime.now());
        taskMapper.updateById(task);
        appendLog(taskId, "TASK", null, "WARN", "任务已终止");
        return task;
    }

    public List<StepLog> logs(Long taskId, Long afterLogId, Integer limit) {
        LambdaQueryWrapper<StepLog> query = Wrappers.lambdaQuery();
        query.eq(StepLog::getTaskId, taskId);
        if (afterLogId != null) {
            query.gt(StepLog::getId, afterLogId);
        }
        query.orderByAsc(StepLog::getId);
        if (limit != null && limit > 0) {
            query.last("limit " + Math.min(limit, 1000));
        }
        return stepLogMapper.selectList(query);
    }

    public List<TaskStep> snapshots(Long taskId) {
        return taskStepMapper.selectList(
                Wrappers.<TaskStep>lambdaQuery().eq(TaskStep::getTaskId, taskId).orderByAsc(TaskStep::getId));
    }

    public List<Task> listByCreator(Long creatorId) {
        if (creatorId == null) {
            return List.of();
        }
        return taskMapper.selectList(
                Wrappers.<Task>lambdaQuery()
                        .eq(Task::getCreatorId, creatorId)
                        .orderByDesc(Task::getId));
    }

    public TaskMonitorSummary monitorSummary() {
        TaskMonitorSummary summary = new TaskMonitorSummary();
        summary.setRunningTasks(taskMapper
                .selectList(Wrappers.<Task>lambdaQuery().eq(Task::getTaskStatus, "RUNNING").orderByDesc(Task::getId)));
        summary.setPendingTasks(taskMapper
                .selectList(Wrappers.<Task>lambdaQuery().eq(Task::getTaskStatus, "PENDING").orderByDesc(Task::getId)));
        OffsetDateTime today = OffsetDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        summary.setTodayCompletedCount(taskMapper.selectCount(
                Wrappers.<Task>lambdaQuery().eq(Task::getTaskStatus, "COMPLETED").ge(Task::getEndTime, today)));
        summary.setTodayFailedCount(taskMapper.selectCount(
                Wrappers.<Task>lambdaQuery().eq(Task::getTaskStatus, "FAILED").ge(Task::getEndTime, today)));
        return summary;
    }

    public List<Task> listByStatus(String status) {
        if (status == null || status.isBlank()) {
            return list();
        }
        return taskMapper.selectList(
                Wrappers.<Task>lambdaQuery()
                        .eq(Task::getTaskStatus, status.trim())
                        .orderByDesc(Task::getId));
    }

    public Long countByStatus(String status) {
        return taskMapper.selectCount(Wrappers.<Task>lambdaQuery().eq(Task::getTaskStatus, status));
    }

    private void appendLog(Long taskId, String sourceType, Long deviceInstanceId, String level, String message) {
        StepLog log = new StepLog();
        log.setSourceType(sourceType);
        log.setTaskId(taskId);
        log.setDeviceInstanceId(deviceInstanceId);
        log.setLogLevel(level);
        log.setLogInfo(message);
        log.setLogTime(OffsetDateTime.now());
        stepLogMapper.insert(log);
    }

    private Object first(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            if (payload.containsKey(key)) {
                return payload.get(key);
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
