package com.smartlab.engine.workflow;

import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.service.db.workflow.TaskService;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class WorkflowTaskControlServiceTest {

    @Test
    void terminateOnlyRequestsTaskTerminationWithoutDispatchingDeviceAbort() {
        TaskService tasks = mock(TaskService.class);
        WorkflowTaskControlService service = new WorkflowTaskControlService(tasks);
        Task task = new Task();
        task.setId(9L);
        when(tasks.requestTermination(9L)).thenReturn(task);
        when(tasks.completeTerminationIfSettled(9L)).thenReturn(task);

        service.terminate(9L);

        verify(tasks).requestTermination(9L);
        verify(tasks).completeTerminationIfSettled(9L);
        verifyNoMoreInteractions(tasks);
    }
}
