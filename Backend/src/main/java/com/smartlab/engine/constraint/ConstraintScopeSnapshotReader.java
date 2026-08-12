package com.smartlab.engine.constraint;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.mapper.resource.device.DeviceTwinStatesMapper;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.mapper.workflow.TaskStepMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ConstraintScopeSnapshotReader {

    private final TaskMapper taskMapper;
    private final TaskStepMapper taskStepMapper;
    private final DeviceTwinStatesMapper deviceTwinStatesMapper;

    public ConstraintScopeSnapshotReader(TaskMapper taskMapper, TaskStepMapper taskStepMapper,
                                         DeviceTwinStatesMapper deviceTwinStatesMapper) {
        this.taskMapper = taskMapper;
        this.taskStepMapper = taskStepMapper;
        this.deviceTwinStatesMapper = deviceTwinStatesMapper;
    }

    public ObjectNode snapshot(RuntimeConstraint constraint, JsonNode action) {
        ObjectNode result = JsonNodeSupport.objectNode();
        ObjectNode tasks = result.putObject("tasks");
        ObjectNode devices = result.putObject("devices");

        taskIds(constraint, action).forEach(taskId -> captureTask(tasks, taskId));
        deviceIds(constraint, action).forEach(deviceId -> captureDevice(devices, deviceId));
        return result;
    }

    private Set<Long> taskIds(RuntimeConstraint constraint, JsonNode action) {
        Set<Long> ids = new LinkedHashSet<>();
        addPositive(ids, constraint.taskId());
        addPositive(ids, positiveLong(action == null ? null : action.get("targetTaskId")));
        return ids;
    }

    private Set<Long> deviceIds(RuntimeConstraint constraint, JsonNode action) {
        Set<Long> ids = new LinkedHashSet<>();
        constraint.observableBindings().values().forEach(key -> addPositive(ids, key.deviceInstanceId()));
        addPositive(ids, constraint.deviceInstanceId());
        addPositive(ids, positiveLong(action == null ? null : action.get("deviceInstanceId")));
        return ids;
    }

    private void captureTask(ObjectNode tasks, Long taskId) {
        ObjectNode snapshot = tasks.putObject(String.valueOf(taskId));
        try {
            Task task = taskMapper.selectById(taskId);
            if (task == null) {
                snapshot.put("snapshotError", "任务不存在");
                return;
            }
            snapshot.put("taskStatus", task.getTaskStatus());
            snapshot.set("taskVariables", copyOrObject(task.getTaskVariables()));
            ObjectNode steps = snapshot.putObject("steps");
            List<TaskStep> taskSteps = taskStepMapper.selectList(Wrappers.<TaskStep>lambdaQuery()
                    .eq(TaskStep::getTaskId, taskId)
                    .orderByAsc(TaskStep::getId));
            for (TaskStep step : taskSteps == null ? List.<TaskStep>of() : taskSteps) {
                ObjectNode stepSnapshot = steps.putObject(String.valueOf(step.getId()));
                stepSnapshot.put("nodeStatus", step.getNodeStatus());
                stepSnapshot.set("variableSpace", copyOrObject(step.getVariableSpace()));
            }
        } catch (RuntimeException e) {
            snapshot.removeAll();
            snapshot.put("snapshotError", message(e));
        }
    }

    private void captureDevice(ObjectNode devices, Long deviceId) {
        ObjectNode snapshot = devices.putObject(String.valueOf(deviceId));
        try {
            DeviceTwinStates state = deviceTwinStatesMapper.selectOne(
                    Wrappers.<DeviceTwinStates>lambdaQuery()
                            .eq(DeviceTwinStates::getInstanceId, deviceId));
            if (state == null) {
                snapshot.put("snapshotError", "设备孪生状态不存在");
                return;
            }
            snapshot.set("attributes", copyOrObject(state.getCurrentAttr()));
            snapshot.set("operationState", copyOrObject(state.getCurrentOpState()));
            if (state.getCurrentCmdState() == null) snapshot.putNull("commandLifecycle");
            else snapshot.put("commandLifecycle", state.getCurrentCmdState());
        } catch (RuntimeException e) {
            snapshot.removeAll();
            snapshot.put("snapshotError", message(e));
        }
    }

    private JsonNode copyOrObject(JsonNode value) {
        return value == null || value.isNull() ? JsonNodeSupport.objectNode() : value.deepCopy();
    }

    private void addPositive(Set<Long> ids, Long value) {
        if (value != null && value > 0) ids.add(value);
    }

    private Long positiveLong(JsonNode value) {
        return value != null && value.canConvertToLong() && value.asLong() > 0 ? value.asLong() : null;
    }

    private String message(RuntimeException error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName()
                : error.getMessage();
    }
}
