package com.smartlab.management.service.db.workflow;

import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.mapper.workflow.TaskStepMapper;
import com.smartlab.management.service.db.constraint.TaskConstraintService;
import com.smartlab.management.service.db.resource.adapter.VirtualLeaseService;
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
        when(resources.expectedInstanceKind(any(), any())).thenReturn("PHYSICAL");
        TaskService service = new TaskService(taskMapper, stepMapper, logService, workflows,
                resources, readiness, constraints, publisher);

        Task result = service.start(7L);

        assertSame(task, result);
        assertEquals("RUNNING", task.getTaskStatus());
        verify(workflows).requireExecutableDefinition(11L);
        verify(resources).validate(11L, task.getResourceMap(), "PHYSICAL");
        verify(readiness).inspect(task.getResourceMap());
        verify(taskMapper).updateById(task);
    }

    @Test
    void startingSimulationTaskAcquiresLeasesBeforeRunning() {
        TaskMapper taskMapper = mock(TaskMapper.class);
        WorkflowTaskResourceService resources = mock(WorkflowTaskResourceService.class);
        WorkflowExecutionReadinessService readiness = mock(WorkflowExecutionReadinessService.class);
        TaskConstraintService constraints = mock(TaskConstraintService.class);
        VirtualLeaseService leases = mock(VirtualLeaseService.class);
        Task task = new Task();
        task.setId(7L);
        task.setFlowModelId(11L);
        task.setTaskStatus("PENDING");
        task.setExecutionKind("SIMULATION");
        task.setResourceMap(JsonNodeSupport.objectNode());
        task.setTaskConstraints(JsonNodeSupport.arrayNode());
        when(taskMapper.selectById(7L)).thenReturn(task);
        when(constraints.normalizeAndValidate(task, task.getTaskConstraints())).thenReturn(JsonNodeSupport.arrayNode());
        when(resources.expectedInstanceKind(any(), any())).thenReturn("PHYSICAL");
        when(resources.boundDeviceInstanceIds(task.getResourceMap())).thenReturn(java.util.Set.of(3L));
        TaskService service = new TaskService(taskMapper, mock(TaskStepMapper.class), mock(ExecutionLogService.class),
                mock(WorkflowService.class), resources, readiness, constraints,
                mock(ApplicationEventPublisher.class), leases);

        Task result = service.start(7L);

        assertEquals("RUNNING", result.getTaskStatus());
        verify(leases).acquireForTask(java.util.Set.of(3L), 7L);
        verify(taskMapper).updateById(task);
    }

    @Test
    void startCanSwitchPendingTaskToSimulation() {
        TaskMapper taskMapper = mock(TaskMapper.class);
        WorkflowTaskResourceService resources = mock(WorkflowTaskResourceService.class);
        WorkflowExecutionReadinessService readiness = mock(WorkflowExecutionReadinessService.class);
        TaskConstraintService constraints = mock(TaskConstraintService.class);
        VirtualLeaseService leases = mock(VirtualLeaseService.class);
        Task task = new Task();
        task.setId(7L);
        task.setFlowModelId(11L);
        task.setTaskStatus("PENDING");
        task.setExecutionKind("PRODUCTION");
        task.setResourceMap(JsonNodeSupport.objectNode());
        task.setTaskConstraints(JsonNodeSupport.arrayNode());
        when(taskMapper.selectById(7L)).thenReturn(task);
        when(constraints.normalizeAndValidate(task, task.getTaskConstraints())).thenReturn(JsonNodeSupport.arrayNode());
        when(resources.expectedInstanceKind(any(), any())).thenReturn("PHYSICAL");
        when(resources.boundDeviceInstanceIds(task.getResourceMap())).thenReturn(java.util.Set.of(3L));
        TaskService service = new TaskService(taskMapper, mock(TaskStepMapper.class), mock(ExecutionLogService.class),
                mock(WorkflowService.class), resources, readiness, constraints,
                mock(ApplicationEventPublisher.class), leases);

        Task result = service.start(7L, true, "SIMULATION");

        assertEquals("SIMULATION", result.getExecutionKind());
        verify(leases).acquireForTask(java.util.Set.of(3L), 7L);
    }

    @Test
    void startingSimulationTaskStaysPendingWhenLeaseFails() {
        TaskMapper taskMapper = mock(TaskMapper.class);
        WorkflowTaskResourceService resources = mock(WorkflowTaskResourceService.class);
        TaskConstraintService constraints = mock(TaskConstraintService.class);
        VirtualLeaseService leases = mock(VirtualLeaseService.class);
        Task task = new Task();
        task.setId(7L);
        task.setFlowModelId(11L);
        task.setTaskStatus("PENDING");
        task.setExecutionKind("SIMULATION");
        task.setResourceMap(JsonNodeSupport.objectNode());
        task.setTaskConstraints(JsonNodeSupport.arrayNode());
        when(taskMapper.selectById(7L)).thenReturn(task);
        when(constraints.normalizeAndValidate(task, task.getTaskConstraints())).thenReturn(JsonNodeSupport.arrayNode());
        when(resources.expectedInstanceKind(any(), any())).thenReturn("PHYSICAL");
        when(resources.boundDeviceInstanceIds(task.getResourceMap())).thenReturn(java.util.Set.of(3L));
        org.mockito.Mockito.doThrow(new IllegalStateException("虚拟点租约未授予")).when(leases)
                .acquireForTask(any(), org.mockito.ArgumentMatchers.eq(7L));
        TaskService service = new TaskService(taskMapper, mock(TaskStepMapper.class), mock(ExecutionLogService.class),
                mock(WorkflowService.class), resources, mock(WorkflowExecutionReadinessService.class), constraints,
                mock(ApplicationEventPublisher.class), leases);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.start(7L));

        assertEquals("虚拟点租约未授予", error.getMessage());
        assertEquals("PENDING", task.getTaskStatus());
        verify(taskMapper, never()).updateById(any(Task.class));
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
        WorkflowTaskResourceService resources = mock(WorkflowTaskResourceService.class);
        when(resources.expectedInstanceKind(any(), any())).thenReturn("PHYSICAL");
        when(readiness.inspect(task.getResourceMap())).thenReturn(java.util.List.of(new WorkflowIssue(
                "DEVICE_OFFLINE", "READINESS", "resourceMap", "deviceInstance", "7", true, "设备离线", "等待设备上线")));
        TaskService service = new TaskService(taskMapper, mock(TaskStepMapper.class), mock(ExecutionLogService.class),
                mock(WorkflowService.class), resources, readiness,
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

    @Test
    void createDoesNotInsertWhenPreflightConstraintIsBlocking() {
        TaskMapper mapper = mock(TaskMapper.class);
        WorkflowTaskResourceService resources = mock(WorkflowTaskResourceService.class);
        var map = JsonNodeSupport.objectNode();
        when(resources.prepare(org.mockito.ArgumentMatchers.eq(11L), any(), any())).thenReturn(new WorkflowTaskResourceService.PreparedTaskResources(map, java.util.List.of()));
        TaskConstraintService constraints = mock(TaskConstraintService.class);
        when(constraints.inspect(org.mockito.ArgumentMatchers.eq(11L), any(), org.mockito.ArgumentMatchers.eq(map), any())).thenReturn(java.util.List.of(
                new WorkflowIssue("TASK_CONSTRAINT_INVALID", "CONSTRAINT", "taskConstraints[0]", "taskConstraint", "", true, "非法规则", "修正")));
        com.smartlab.management.dto.workflow.TaskCreateRequest request = new com.smartlab.management.dto.workflow.TaskCreateRequest();
        request.setTaskName("task"); request.setFlowModelId(11L); request.setDeviceBindings(java.util.List.of()); request.setTaskVariables(JsonNodeSupport.objectNode()); request.setTaskConstraints(JsonNodeSupport.arrayNode());
        TaskService service = new TaskService(mapper, mock(TaskStepMapper.class), mock(ExecutionLogService.class), mock(WorkflowService.class), resources, mock(WorkflowExecutionReadinessService.class), constraints, mock(ApplicationEventPublisher.class));
        assertThrows(IllegalStateException.class, () -> service.create(request));
        verify(mapper, never()).insert(any(Task.class));
    }
    @Test
    void resumingTaskRejectsBlockingIssueWithoutLifecycleMutation() {
        TaskMapper mapper = mock(TaskMapper.class);
        Task task = new Task();
        task.setId(8L);
        task.setFlowModelId(11L);
        task.setTaskStatus("PAUSED");
        task.setStartTime(java.time.OffsetDateTime.parse("2026-08-06T09:30:00+08:00"));
        task.setResourceMap(JsonNodeSupport.objectNode());
        task.setTaskConstraints(JsonNodeSupport.arrayNode());
        when(mapper.selectById(8L)).thenReturn(task);
        WorkflowExecutionReadinessService readiness = mock(WorkflowExecutionReadinessService.class);
        when(readiness.inspect(task.getResourceMap())).thenReturn(java.util.List.of(new WorkflowIssue(
                "DEVICE_OFFLINE", "READINESS", "deviceBindings[slot-a]", "deviceBinding", "slot-a", true, "设备离线", "等待设备上线")));
        TaskService service = new TaskService(mapper, mock(TaskStepMapper.class), mock(ExecutionLogService.class),
                mock(WorkflowService.class), mock(WorkflowTaskResourceService.class), readiness,
                mock(TaskConstraintService.class), mock(ApplicationEventPublisher.class));
        java.time.OffsetDateTime startTime = task.getStartTime();

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.resume(8L));

        assertEquals("设备离线", error.getMessage());
        assertEquals("PAUSED", task.getTaskStatus());
        assertEquals(startTime, task.getStartTime());
        verify(mapper, never()).updateById(any(Task.class));
    }}
