package com.smartlab.engine.constraint;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.observation.ObservableKey;
import com.smartlab.global.contract.ObservableObjectType;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.constraint.ConstraintRule;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.mapper.resource.device.DeviceTwinStatesMapper;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.mapper.workflow.TaskStepMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConstraintScopeSnapshotReaderTest {

    @Test
    void capturesFullTaskAndDeviceScopes() {
        TaskMapper tasks = mock(TaskMapper.class);
        TaskStepMapper steps = mock(TaskStepMapper.class);
        DeviceTwinStatesMapper devices = mock(DeviceTwinStatesMapper.class);
        when(tasks.selectById(7L)).thenReturn(task(7L));
        when(steps.selectList(any(Wrapper.class))).thenReturn(List.of(step(31L, 7L)));
        when(devices.selectOne(any(Wrapper.class))).thenReturn(device(18L, 105.2));
        ConstraintScopeSnapshotReader reader = new ConstraintScopeSnapshotReader(tasks, steps, devices);

        ObjectNode snapshot = reader.snapshot(runtime(7L, Map.of(
                "temperature", deviceKey(18L))), JsonNodeSupport.objectNode());

        assertEquals("RUNNING", snapshot.path("tasks").path("7").path("taskStatus").asText());
        assertEquals("batch-A", snapshot.path("tasks").path("7").path("taskVariables")
                .path("batch").asText());
        assertEquals("RUNNING", snapshot.path("tasks").path("7").path("steps")
                .path("31").path("nodeStatus").asText());
        assertEquals(105.2, snapshot.path("tasks").path("7").path("steps")
                .path("31").path("variableSpace").path("temperature").asDouble());
        assertEquals(105.2, snapshot.path("devices").path("18")
                .path("attributes").path("temperature").asDouble());
        assertEquals("IDLE", snapshot.path("devices").path("18")
                .path("commandLifecycle").asText());
        assertEquals("RUNNING", snapshot.path("devices").path("18")
                .path("operationState").path("main").asText());
    }

    @Test
    void includesEveryObservedAndActionTargetDevice() {
        TaskMapper tasks = mock(TaskMapper.class);
        TaskStepMapper steps = mock(TaskStepMapper.class);
        DeviceTwinStatesMapper devices = mock(DeviceTwinStatesMapper.class);
        when(devices.selectOne(any(Wrapper.class)))
                .thenReturn(device(18L, 80), device(19L, 70), device(20L, 60));
        ConstraintScopeSnapshotReader reader = new ConstraintScopeSnapshotReader(tasks, steps, devices);
        Map<String, ObservableKey> bindings = new LinkedHashMap<>();
        bindings.put("temperature1", deviceKey(18L));
        bindings.put("temperature2", deviceKey(19L));
        ObjectNode action = JsonNodeSupport.objectNode().put("deviceInstanceId", 20L);

        ObjectNode snapshot = reader.snapshot(runtime(null, bindings), action);

        assertEquals(3, snapshot.path("devices").size());
        assertTrue(snapshot.path("devices").has("18"));
        assertTrue(snapshot.path("devices").has("19"));
        assertTrue(snapshot.path("devices").has("20"));
        assertEquals(0, snapshot.path("tasks").size());
    }

    @Test
    void recordsOneScopeFailureWithoutDiscardingOtherScopes() {
        TaskMapper tasks = mock(TaskMapper.class);
        TaskStepMapper steps = mock(TaskStepMapper.class);
        DeviceTwinStatesMapper devices = mock(DeviceTwinStatesMapper.class);
        when(tasks.selectById(7L)).thenThrow(new IllegalStateException("task database unavailable"));
        when(devices.selectOne(any(Wrapper.class))).thenReturn(device(18L, 105.2));
        ConstraintScopeSnapshotReader reader = new ConstraintScopeSnapshotReader(tasks, steps, devices);

        ObjectNode snapshot = reader.snapshot(runtime(7L, Map.of(
                "temperature", deviceKey(18L))), JsonNodeSupport.objectNode());

        assertEquals("task database unavailable",
                snapshot.path("tasks").path("7").path("snapshotError").asText());
        assertEquals(105.2, snapshot.path("devices").path("18")
                .path("attributes").path("temperature").asDouble());
    }

    private RuntimeConstraint runtime(Long taskId, Map<String, ObservableKey> bindings) {
        return new RuntimeConstraint(new RuntimeConstraintKey("GLOBAL", "1",
                taskId == null ? "global" : "task:" + taskId, "v1"),
                new ConstraintRule(), bindings, taskId, null, null);
    }

    private ObservableKey deviceKey(Long instanceId) {
        return new ObservableKey(ObservableObjectType.DEVICE_ATTRIBUTE, instanceId,
                null, null, null, null, "temperature", null);
    }

    private Task task(Long id) {
        Task task = new Task();
        task.setId(id);
        task.setTaskStatus("RUNNING");
        task.setTaskVariables(JsonNodeSupport.objectNode().put("batch", "batch-A"));
        return task;
    }

    private TaskStep step(Long id, Long taskId) {
        TaskStep step = new TaskStep();
        step.setId(id);
        step.setTaskId(taskId);
        step.setNodeStatus("RUNNING");
        step.setVariableSpace(JsonNodeSupport.objectNode().put("temperature", 105.2));
        return step;
    }

    private DeviceTwinStates device(Long instanceId, double temperature) {
        DeviceTwinStates state = new DeviceTwinStates();
        state.setInstanceId(instanceId);
        state.setCurrentAttr(JsonNodeSupport.objectNode().put("temperature", temperature));
        state.setCurrentOpState(JsonNodeSupport.objectNode().put("main", "RUNNING"));
        state.setCurrentCmdState("IDLE");
        return state;
    }
}
