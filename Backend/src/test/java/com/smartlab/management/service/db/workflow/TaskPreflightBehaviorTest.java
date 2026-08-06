package com.smartlab.management.service.db.workflow;

import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.TaskPreflightRequest;
import com.smartlab.management.dto.workflow.TaskPreflightResponse;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.mapper.workflow.TaskStepMapper;
import com.smartlab.management.service.db.constraint.TaskConstraintService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskPreflightBehaviorTest {

    @Test
    void preflightReturnsBindingIssuesWithoutInsertingTask() {
        TaskMapper taskMapper = mock(TaskMapper.class);
        WorkflowTaskResourceService resources = mock(WorkflowTaskResourceService.class);
        when(resources.prepare(11L, java.util.List.of())).thenReturn(new WorkflowTaskResourceService.PreparedTaskResources(
                JsonNodeSupport.objectNode(), java.util.List.of(new WorkflowIssue("TASK_BINDING_MISSING", "BINDING", "deviceBindings", "DEV_NODE", "11:1", true, "缺少设备绑定", "请选择设备实例"))));
        TaskService service = new TaskService(taskMapper, mock(TaskStepMapper.class), mock(ExecutionLogService.class),
                mock(WorkflowService.class), resources, mock(WorkflowExecutionReadinessService.class), mock(TaskConstraintService.class), mock(ApplicationEventPublisher.class));

        TaskPreflightResponse result = service.preflight(new TaskPreflightRequest(11L, JsonNodeSupport.objectNode(), java.util.List.of(), JsonNodeSupport.arrayNode()));

        assertFalse(result.ready());
        assertEquals(java.util.List.of("TASK_BINDING_MISSING"), result.issues().stream().map(WorkflowIssue::code).toList());
        verify(taskMapper, never()).insert(any(com.smartlab.management.entity.workflow.Task.class));
    }
}
