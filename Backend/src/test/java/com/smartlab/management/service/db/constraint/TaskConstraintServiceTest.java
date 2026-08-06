package com.smartlab.management.service.db.constraint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.entity.constraint.ConstraintRule;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.service.db.workflow.WorkflowTaskResourceService;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
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

    @Test
    void inspectReportsInvalidRuleDefinitionWithoutMutatingInput() {
        ConstraintRuleService ruleService = mock(ConstraintRuleService.class);
        WorkflowTaskResourceService resources = mock(WorkflowTaskResourceService.class);
        when(resources.boundDeviceInstanceIds(any())).thenReturn(Set.of());
        when(resources.workflowModelIds(3L)).thenReturn(Set.of(3L));
        doThrow(new IllegalArgumentException("规则名称不能为空")).when(ruleService).validateTaskDefinition(any());
        TaskConstraintService service = new TaskConstraintService(ruleService, resources, mock(TaskMapper.class));
        ArrayNode input = JsonNodeSupport.arrayNode();
        ObjectNode rule = input.addObject();
        rule.put("expression", "true");
        rule.putObject("bindings");
        rule.putArray("violationActions");
        JsonNode snapshot = input.deepCopy();

        java.util.List<WorkflowIssue> issues = service.inspect(3L, JsonNodeSupport.objectNode(), JsonNodeSupport.objectNode(), input);

        assertEquals(java.util.List.of("TASK_CONSTRAINT_INVALID"), issues.stream().map(WorkflowIssue::code).toList());
        assertEquals(snapshot, input);
        verify(ruleService).validateTaskDefinition(any());
    }
    @Test
    void inspectAcceptsTaskLifecycleAndSystemAbortWithoutMutatingInput() {
        WorkflowTaskResourceService resources = mock(WorkflowTaskResourceService.class);
        when(resources.boundDeviceInstanceIds(any())).thenReturn(Set.of());
        when(resources.workflowModelIds(3L)).thenReturn(Set.of(3L));
        ConstraintRuleService ruleService = mock(ConstraintRuleService.class);
        TaskConstraintService service = new TaskConstraintService(ruleService, resources, mock(TaskMapper.class));
        ArrayNode input = JsonNodeSupport.arrayNode();
        ObjectNode rule = input.addObject();
        rule.put("ruleName", "lifecycle"); rule.put("expression", "true");
        rule.putObject("bindings").putObject("state").put("bindingType", "OBSERVABLE").putObject("source").put("sourceType", "TASK_LIFECYCLE_STATE").put("dataType", "STRING");
        rule.putArray("violationActions").addObject().put("actionType", "SYSTEM").put("action", "ABORT");
        JsonNode snapshot = input.deepCopy();

        assertEquals(java.util.List.of(), service.inspect(3L, JsonNodeSupport.objectNode(), JsonNodeSupport.objectNode(), input));
        var captor = forClass(ConstraintRule.class);
        verify(ruleService).validateTaskDefinition(captor.capture());
        assertTrue(captor.getValue().getBindings().path("state").path("source").path("taskId").asLong() > 0);
        assertTrue(captor.getValue().getViolationActions().path(0).path("targetTaskId").asLong() > 0);
        assertEquals(snapshot, input);
    }}
