package com.smartlab.engine.workflow;

import com.smartlab.engine.statemachine.StateMachineEngine;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.workflow.TaskService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowTaskControlServiceTest {

    @Test
    void abortsRunningDeviceBeforeTerminatingTask() {
        TaskService tasks = mock(TaskService.class);
        StateMachineEngine stateMachine = mock(StateMachineEngine.class);
        WorkflowTaskControlService service = new WorkflowTaskControlService(tasks, stateMachine);
        TaskStep step = new TaskStep();
        step.setId(12L);
        step.setNodeStatus("RUNNING");
        step.setInterfaceInSnapshot(JsonNodeSupport.objectNode()
                .put("deviceInstanceId", 44L).put("messageId", "message-1"));
        Task task = new Task();
        task.setId(5L);
        when(tasks.snapshots(5L)).thenReturn(List.of(step));
        when(tasks.abort(5L)).thenReturn(task);

        service.abort(5L);

        verify(stateMachine).dispatchSignalByType(org.mockito.ArgumentMatchers.eq(44L),
                org.mockito.ArgumentMatchers.eq("WORKFLOW"),
                org.mockito.ArgumentMatchers.eq("WF_EXECUTE_ABORT"), anyMap());
        verify(tasks).abort(5L);
    }
}
