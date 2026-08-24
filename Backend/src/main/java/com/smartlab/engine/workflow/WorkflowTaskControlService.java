package com.smartlab.engine.workflow;

import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.service.db.workflow.TaskService;
import org.springframework.stereotype.Service;

@Service
public class WorkflowTaskControlService {
    private final TaskService taskService;

    public WorkflowTaskControlService(TaskService taskService) {
        this.taskService = taskService;
    }

    public Task start(Long taskId) {
        return taskService.start(taskId);
    }

    public Task pause(Long taskId) {
        return taskService.pause(taskId);
    }

    public Task resume(Long taskId) {
        return taskService.resume(taskId);
    }

    public Task terminate(Long taskId) {
        Task task = taskService.requestTermination(taskId);
        return taskService.completeTerminationIfSettled(task.getId());
    }
}
