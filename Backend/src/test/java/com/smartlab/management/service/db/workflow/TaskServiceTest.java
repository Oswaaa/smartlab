package com.smartlab.management.service.db.workflow;

import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.mapper.workflow.TaskStepMapper;
import com.smartlab.management.service.db.constraint.TaskConstraintService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskServiceTest {

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
                mock(WorkflowTaskResourceService.class), mock(TaskConstraintService.class), publisher);

        Task result = service.requestTermination(7L);

        assertSame(task, result);
        verify(taskMapper, never()).updateById(any(Task.class));
        verifyNoInteractions(logService, publisher);
    }
}
