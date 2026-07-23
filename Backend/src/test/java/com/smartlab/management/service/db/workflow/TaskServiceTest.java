package com.smartlab.management.service.db.workflow;

import com.smartlab.management.dto.workflow.TaskCreateRequest;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.mapper.workflow.TaskStepMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceTest {

    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final TaskStepMapper stepMapper = mock(TaskStepMapper.class);
    private final ExecutionLogService logService = mock(ExecutionLogService.class);
    private final WorkflowService workflowService = mock(WorkflowService.class);
    private final WorkflowTaskResourceService resourceService = mock(WorkflowTaskResourceService.class);
    private final TaskService service = new TaskService(taskMapper, stepMapper, logService, workflowService, resourceService);

    @Test
    void createsPendingTaskOnlyForExistingWorkflow() {
        WorkflowDetailResponse workflow = new WorkflowDetailResponse();
        workflow.setId(10L);
        when(workflowService.getDefinition(10L)).thenReturn(workflow);
        TaskCreateRequest request = new TaskCreateRequest();
        request.setTaskName("run reactor");
        request.setFlowModelId(10L);

        Task task = service.create(request);

        assertEquals("PENDING", task.getTaskStatus());
        verify(taskMapper).insert(task);
    }

    @Test
    void rejectsRestartingTerminalTask() {
        Task completed = new Task();
        completed.setId(1L);
        completed.setTaskStatus("COMPLETED");
        when(taskMapper.selectById(1L)).thenReturn(completed);

        assertThrows(IllegalStateException.class, () -> service.start(1L));
    }

    @Test
    void revalidatesDeviceResourcesBeforeStarting() {
        Task pending = new Task();
        pending.setId(3L);
        pending.setFlowModelId(10L);
        pending.setTaskStatus("PENDING");
        when(taskMapper.selectById(3L)).thenReturn(pending);

        service.start(3L);

        verify(resourceService).validate(10L, pending.getResourceMap());
        assertEquals("RUNNING", pending.getTaskStatus());
    }
    @Test
    void abortsOnlyActiveTaskAndItsSteps() {
        Task running = new Task();
        running.setId(2L);
        running.setTaskStatus("RUNNING");
        when(taskMapper.selectById(2L)).thenReturn(running);

        Task aborted = service.abort(2L);

        assertEquals("ABORTED", aborted.getTaskStatus());
        verify(stepMapper).update(any(), any());
    }
}
