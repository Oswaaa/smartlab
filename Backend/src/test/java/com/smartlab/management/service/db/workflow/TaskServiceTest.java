package com.smartlab.management.service.db.workflow;

import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.mapper.workflow.TaskStepMapper;
import com.smartlab.management.service.db.constraint.TaskConstraintService;
import com.smartlab.global.util.JsonNodeSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskServiceTest {

    @Test
    void startingTaskRequiresExecutableWorkflowAndExecutionReadiness() {
        TaskMapper taskMapper = mock(TaskMapper.class);
        TaskStepMapper stepMapper = mock(TaskStepMapper.class);
        ExecutionLogService logService = mock(ExecutionLogService.class);
        WorkflowService workflows = mock(WorkflowService.class);
        WorkflowTaskResourceService resources = mock(WorkflowTaskResourceService.class);
        WorkflowExecutionReadinessService readiness = mock(WorkflowExecutionReadinessService.class);
        TaskConstraintService constraints = mock(TaskConstraintService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        Task task = new Task();
        task.setId(7L);
        task.setFlowModelId(11L);
        task.setTaskStatus("PENDING");
        task.setResourceMap(JsonNodeSupport.objectNode());
        task.setTaskConstraints(JsonNodeSupport.arrayNode());
        when(taskMapper.selectById(7L)).thenReturn(task);
        when(constraints.normalizeAndValidate(task, task.getTaskConstraints())).thenReturn(JsonNodeSupport.arrayNode());
        TaskService service = new TaskService(taskMapper, stepMapper, logService, workflows,
                resources, readiness, constraints, publisher);

        Task result = service.start(7L);

        assertSame(task, result);
        assertEquals("RUNNING", task.getTaskStatus());
        verify(workflows).requireExecutableDefinition(11L);
        verify(resources).validate(11L, task.getResourceMap());
        verify(readiness).inspect(task.getResourceMap());
        verify(taskMapper).updateById(task);
    }

    @Test
    void startingTaskRejectsBlockingReadinessIssueWithoutMutatingTask() {
        TaskMapper taskMapper = mock(TaskMapper.class);
        Task task = new Task();
        task.setId(7L);
        task.setFlowModelId(11L);
        task.setTaskStatus("PENDING");
        task.setResourceMap(JsonNodeSupport.objectNode());
        task.setTaskConstraints(JsonNodeSupport.arrayNode());
        when(taskMapper.selectById(7L)).thenReturn(task);
        WorkflowExecutionReadinessService readiness = mock(WorkflowExecutionReadinessService.class);
        when(readiness.inspect(task.getResourceMap())).thenReturn(java.util.List.of(new WorkflowIssue(
                "DEVICE_OFFLINE", "READINESS", "resourceMap", "deviceInstance", "7", true, "设备离线", "等待设备上线")));
        TaskService service = new TaskService(taskMapper, mock(TaskStepMapper.class), mock(ExecutionLogService.class),
                mock(WorkflowService.class), mock(WorkflowTaskResourceService.class), readiness,
                mock(TaskConstraintService.class), mock(ApplicationEventPublisher.class));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.start(7L));

        assertEquals("设备离线", error.getMessage());
        assertEquals("PENDING", task.getTaskStatus());
        verify(taskMapper, never()).updateById(any(Task.class));
    }
    @Test
    void requestingTerminationAgainIsIdempotent() {
        TaskMapper taskMapper = mock(TaskMapper.class);
        TaskStepMapper stepMapper = mock(TaskStepMapper.class);
        ExecutionLogService logService = mock(ExecutionLogService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        Task task = new Task();
        task.setId(7L);
        task.setTaskStatus("TERMINATING");
        when(taskMapper.selectById(7L)).thenReturn(task);
        TaskService service = new TaskService(taskMapper, stepMapper, logService, mock(WorkflowService.class),
                mock(WorkflowTaskResourceService.class), mock(WorkflowExecutionReadinessService.class),
                mock(TaskConstraintService.class), publisher);

        Task result = service.requestTermination(7L);

        assertSame(task, result);
        verify(taskMapper, never()).updateById(any(Task.class));
        verifyNoInteractions(logService, publisher);
    }
}
