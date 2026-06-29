package com.smartlab.management.dto.workflow;

import com.smartlab.management.entity.workflow.Task;
import lombok.Data;

import java.util.List;

@Data
/**
 * TaskMonitorSummary 领域实体/配置模型类。
 */
public class TaskMonitorSummary {

    private List<Task> runningTasks;
    private List<Task> pendingTasks;
    private long todayCompletedCount;
    private long todayFailedCount;
}

