package com.smartlab.management.service.db.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.management.dto.workflow.TaskExecutionView;
import com.smartlab.management.dto.workflow.TaskNodeExecutionView;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.entity.workflow.FlowModels;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.mapper.workflow.FlowModelsMapper;
import com.smartlab.management.mapper.workflow.FlowNodeMapper;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.mapper.workflow.TaskStepMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TaskExecutionViewService {

    private final TaskMapper taskMapper;
    private final TaskStepMapper taskStepMapper;
    private final FlowNodeMapper flowNodeMapper;
    private final FlowModelsMapper flowModelsMapper;
    private final ObjectMapper objectMapper;

    public TaskExecutionViewService(TaskMapper taskMapper, TaskStepMapper taskStepMapper,
                                     FlowNodeMapper flowNodeMapper, FlowModelsMapper flowModelsMapper,
                                     ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.taskStepMapper = taskStepMapper;
        this.flowNodeMapper = flowNodeMapper;
        this.flowModelsMapper = flowModelsMapper;
        this.objectMapper = objectMapper;
    }

    public TaskExecutionView get(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) throw new IllegalArgumentException("task not found: " + taskId);

        FlowModels flowModel = flowModelsMapper.selectById(task.getFlowModelId());
        if (flowModel == null) throw new IllegalArgumentException("flow model not found: " + task.getFlowModelId());

        List<FlowNode> flowNodes = flowNodeMapper.selectList(
                new LambdaQueryWrapper<FlowNode>().eq(FlowNode::getFlowModelId, flowModel.getId()));

        List<TaskStep> steps = taskStepMapper.selectList(
                new LambdaQueryWrapper<TaskStep>().eq(TaskStep::getTaskId, taskId)
                        .orderByAsc(TaskStep::getId));

        // Build node name lookup from flow model JSON
        Map<Long, String> nodeNameByRef = new HashMap<>();
        Map<Long, String> nodeTypeByRef = new HashMap<>();
        JsonNode nodesJson = flowModel.getNodes();
        if (nodesJson != null && nodesJson.isArray()) {
            for (JsonNode node : nodesJson) {
                if (node.has("nodeIdRef") && node.has("name")) {
                    long ref = node.get("nodeIdRef").asLong();
                    nodeNameByRef.put(ref, node.get("name").asText());
                }
                if (node.has("nodeIdRef") && node.has("nodeType")) {
                    long ref = node.get("nodeIdRef").asLong();
                    nodeTypeByRef.put(ref, node.get("nodeType").asText());
                }
            }
        }

        List<TaskNodeExecutionView> nodes = new ArrayList<>();
        for (TaskStep step : steps) {
            long nodeIdRef = step.getNodeIdRef() != null ? step.getNodeIdRef() : 0L;
            String nodeName = nodeNameByRef.getOrDefault(nodeIdRef, "node-" + nodeIdRef);
            String nodeType = nodeTypeByRef.getOrDefault(nodeIdRef, "UNKNOWN");

            String deviceName = null;
            String capabilityName = null;
            JsonNode interfaceIn = step.getInterfaceInSnapshot();
            if (interfaceIn != null) {
                if (interfaceIn.has("deviceName")) {
                    deviceName = interfaceIn.get("deviceName").asText(null);
                }
                if (interfaceIn.has("capabilityName")) {
                    capabilityName = interfaceIn.get("capabilityName").asText(null);
                } else if (interfaceIn.has("capabilityRef")) {
                    capabilityName = interfaceIn.get("capabilityRef").asText(null);
                }
            }

            JsonNode inputs = sanitizeOutputs(step.getInterfaceInSnapshot());
            JsonNode outputs = sanitizeOutputs(step.getInterfaceOutSnapshot());

            nodes.add(new TaskNodeExecutionView(
                    step.getId(),
                    flowModel.getId(),
                    flowModel.getVersion(),
                    nodeIdRef,
                    null,
                    nodeName,
                    nodeType,
                    step.getNodeStatus(),
                    deviceName,
                    capabilityName,
                    inputs,
                    outputs,
                    step.getStartTime(),
                    step.getEndTime(),
                    step.getDurationMs(),
                    List.of()
            ));
        }

        int completed = (int) steps.stream()
                .filter(s -> "COMPLETED".equals(s.getNodeStatus())).count();

        return new TaskExecutionView(
                task.getId(),
                task.getTaskName(),
                task.getTaskStatus(),
                completed,
                nodes.size(),
                nodes
        );
    }

    private JsonNode sanitizeOutputs(JsonNode node) {
        if (node == null) return null;
        if (!node.isObject()) return node;
        ObjectNode cleaned = node.deepCopy();
        cleaned.remove("messageId");
        cleaned.remove("taskId");
        cleaned.remove("taskStepId");
        cleaned.remove("resourceMap");
        cleaned.remove("bindingKey");
        return cleaned;
    }
}
