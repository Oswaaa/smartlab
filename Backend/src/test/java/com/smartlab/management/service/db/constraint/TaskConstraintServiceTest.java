package com.smartlab.management.service.db.constraint;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.service.db.workflow.WorkflowTaskResourceService;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskConstraintServiceTest {

    @Test
    void normalizesTaskLifecycleAndSystemActionToOwningTask() {
        ConstraintRuleService ruleService = mock(ConstraintRuleService.class);
        WorkflowTaskResourceService resources = mock(WorkflowTaskResourceService.class);
        when(resources.boundDeviceInstanceIds(any())).thenReturn(Set.of(55L));
        TaskConstraintService service = new TaskConstraintService(ruleService, resources, mock(TaskMapper.class));
        Task task = task();
        ArrayNode input = JsonNodeSupport.arrayNode();
        ObjectNode rule = input.addObject();
        rule.put("ruleName", "任务运行超时");
        rule.put("expression", "taskState == 'RUNNING'");
        ObjectNode binding = rule.putObject("bindings").putObject("taskState");
        binding.put("bindingType", "OBSERVABLE");
        ObjectNode source = binding.putObject("source");
        source.put("sourceType", "TASK_LIFECYCLE_STATE");
        source.put("dataType", "STRING");
        ObjectNode action = rule.putArray("violationActions").addObject();
        action.put("actionType", "SYSTEM");
        action.put("action", "ABORT");

        ArrayNode normalized = service.normalizeAndValidate(task, input);

        assertEquals(7L, normalized.path(0).path("bindings").path("taskState").path("source").path("taskId").asLong());
        assertEquals(7L, normalized.path(0).path("violationActions").path(0).path("targetTaskId").asLong());
        verify(ruleService).validateTaskDefinition(any());
    }

    @Test
    void rejectsDeviceOutsideTaskResourceMap() {
        ConstraintRuleService ruleService = mock(ConstraintRuleService.class);
        WorkflowTaskResourceService resources = mock(WorkflowTaskResourceService.class);
        when(resources.boundDeviceInstanceIds(any())).thenReturn(Set.of(55L));
        TaskConstraintService service = new TaskConstraintService(ruleService, resources, mock(TaskMapper.class));
        ArrayNode input = JsonNodeSupport.arrayNode();
        ObjectNode rule = input.addObject();
        rule.put("ruleName", "越权设备动作");
        rule.put("expression", "true");
        rule.putObject("bindings");
        ObjectNode action = rule.putArray("violationActions").addObject();
        action.put("actionType", "DEVICE_CAPABILITY");
        action.put("deviceInstanceId", 99L);
        action.put("capabilityName", "abort");

        assertThrows(IllegalArgumentException.class, () -> service.normalizeAndValidate(task(), input));
    }

    @Test
    void keepsTaskConstraintsActiveWhileTaskIsTerminating() {
        Task task = task();
        task.setTaskStatus("TERMINATING");

        assertEquals(true, TaskConstraintService.isActive(task));
    }

    @Test
    void inspectsDeviceActionOutsideBoundResourcesWithoutMutatingTask() {
        WorkflowTaskResourceService resources = mock(WorkflowTaskResourceService.class);
        when(resources.boundDeviceInstanceIds(any())).thenReturn(Set.of(55L));
        TaskConstraintService service = new TaskConstraintService(mock(ConstraintRuleService.class), resources, mock(TaskMapper.class));
        ArrayNode input = JsonNodeSupport.arrayNode();
        ObjectNode rule = input.addObject();
        rule.put("ruleName", "越权设备动作");
        rule.put("expression", "true");
        rule.putObject("bindings");
        rule.putArray("violationActions").addObject().put("actionType", "DEVICE_CAPABILITY").put("deviceInstanceId", 99L);

        java.util.List<WorkflowIssue> issues = service.inspect(3L, JsonNodeSupport.objectNode(), JsonNodeSupport.objectNode(), input);

        assertEquals(java.util.List.of("TASK_CONSTRAINT_INVALID"), issues.stream().map(WorkflowIssue::code).toList());
        assertTrue(issues.get(0).path().contains("violationActions[0]"));
    }

    private Task task() {
        Task task = new Task();
        task.setId(7L);
        task.setFlowModelId(3L);
        ObjectNode map = JsonNodeSupport.objectNode();
        map.putObject("deviceBindings").putObject("3:heat").put("deviceInstanceId", 55L);
        task.setResourceMap(map);
        return task;
    }
}
