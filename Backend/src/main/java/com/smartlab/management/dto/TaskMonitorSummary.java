package com.smartlab.management.dto;

import com.smartlab.management.entity.Task;
import lombok.Data;

import java.util.List;

@Data
public class TaskMonitorSummary {

    private List<Task> runningTasks;
    private List<Task> pendingTasks;
    private long todayCompletedCount;
    private long todayFailedCount;
}

