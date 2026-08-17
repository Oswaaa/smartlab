package com.smartlab.management.service.db.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.global.event.TaskExecutionLogEvent;
import com.smartlab.management.entity.workflow.ExecutionLog;
import com.smartlab.management.mapper.workflow.ExecutionLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@Service
public class ExecutionLogService {
    private static final Set<String> SOURCE_TYPES = Set.of("TASK", "MANUAL", "CONSTRAINT", "SYSTEM", "ADAPTER");
    private static final Set<String> LEVELS = Set.of("INFO", "WARN", "ERROR");
    private final ExecutionLogMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    public ExecutionLogService(ExecutionLogMapper mapper) {
        this(mapper, null);
    }

    @Autowired
    public ExecutionLogService(ExecutionLogMapper mapper, ApplicationEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    public ExecutionLog append(String sourceType, Long taskId, Long taskStepId, Long deviceInstanceId,
                               String level, String message) {
        if (!SOURCE_TYPES.contains(sourceType)) throw new IllegalArgumentException("未知日志来源: " + sourceType);
        if (!LEVELS.contains(level)) throw new IllegalArgumentException("未知日志级别: " + level);
        ExecutionLog log = new ExecutionLog();
        log.setSourceType(sourceType);
        log.setTaskId(taskId);
        log.setTaskStepId(taskStepId);
        log.setDeviceInstanceId(deviceInstanceId);
        log.setLogLevel(level);
        log.setLogInfo(message);
        log.setLogTime(OffsetDateTime.now());
        mapper.insert(log);
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new TaskExecutionLogEvent(log));
        }
        return log;
    }

    public List<ExecutionLog> byTask(Long taskId, Long afterLogId, Integer limit) {
        LambdaQueryWrapper<ExecutionLog> query = Wrappers.<ExecutionLog>lambdaQuery()
                .eq(ExecutionLog::getTaskId, taskId);
        if (afterLogId != null) query.gt(ExecutionLog::getId, afterLogId);
        query.orderByAsc(ExecutionLog::getId);
        if (limit != null && limit > 0) query.last("limit " + Math.min(limit, 1000));
        return mapper.selectList(query);
    }
}
