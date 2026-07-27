package com.smartlab.management.service.db.constraint;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.constraint.ConstraintRule;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.service.db.workflow.WorkflowTaskResourceService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 校验TASK.task_constraints并将其转换为约束引擎可执行的任务规则快照 */
@Service
public class TaskConstraintService {
    private static final Set<String> DEVICE_SOURCES = Set.of(
            "DEVICE_ATTRIBUTE", "DEVICE_OPERATION_STATE", "DEVICE_COMMAND_LIFECYCLE");
    private static final Set<String> NODE_SOURCES = Set.of("NODE_LIFECYCLE_STATE", "NODE_INTERNAL_VARIABLE");
    private static final Set<String> ACTIVE_STATES = Set.of("RUNNING", "PAUSED", "TERMINATING");

    private final ConstraintRuleService ruleService;
    private final WorkflowTaskResourceService resourceService;
    private final TaskMapper taskMapper;

    public TaskConstraintService(ConstraintRuleService ruleService, WorkflowTaskResourceService resourceService,
                                 TaskMapper taskMapper) {
        this.ruleService = ruleService;
        this.resourceService = resourceService;
        this.taskMapper = taskMapper;
    }

    public ArrayNode normalizeAndValidate(Task task, JsonNode sourceRules) {
        if (task == null || task.getId() == null || task.getFlowModelId() == null) {
            throw new IllegalArgumentException("任务约束校验需要已持久化的任务和工作流模型");
        }
        if (sourceRules == null || sourceRules.isNull()) return JsonNodeSupport.arrayNode();
        if (!sourceRules.isArray()) throw new IllegalArgumentException("taskConstraints必须是数组");
        Set<Long> boundInstances = resourceService.boundDeviceInstanceIds(task.getResourceMap());
        Set<Long> workflowModelIds = resourceService.workflowModelIds(task.getFlowModelId());
        ArrayNode result = JsonNodeSupport.arrayNode();
        for (int index = 0; index < sourceRules.size(); index++) {
            JsonNode sourceRule = sourceRules.get(index);
            if (!sourceRule.isObject()) throw new IllegalArgumentException("taskConstraints[" + index + "]必须是对象");
            ObjectNode ruleNode = (ObjectNode) sourceRule.deepCopy();
            if (!ruleNode.has("isEnabled")) ruleNode.put("isEnabled", true);
            normalizeBindings(task, ruleNode.path("bindings"), boundInstances, workflowModelIds, index);
            normalizeActions(task, ruleNode.path("violationActions"), boundInstances, index);
            ConstraintRule rule = toRule(ruleNode);
            ruleService.validateTaskDefinition(rule);
            result.add(ruleNode);
        }
        return result;
    }

    public List<ConstraintRule> rulesForTask(Task task) {
        if (task == null || task.getId() == null || task.getTaskConstraints() == null || !task.getTaskConstraints().isArray()) {
            return List.of();
        }
        List<ConstraintRule> result = new ArrayList<>();
        for (int index = 0; index < task.getTaskConstraints().size(); index++) {
            JsonNode node = task.getTaskConstraints().get(index);
            if (!node.isObject() || node.path("isEnabled").isBoolean() && !node.path("isEnabled").asBoolean()) continue;
            ConstraintRule rule = toRule(node);
            rule.setId(runtimeRuleId(task.getId(), index));
            result.add(rule);
        }
        return List.copyOf(result);
    }

    public List<Task> activeTasks() {
        return taskMapper.selectList(Wrappers.<Task>lambdaQuery().in(Task::getTaskStatus, ACTIVE_STATES));
    }

    public List<Task> activeTasksForDevice(Long deviceInstanceId) {
        if (deviceInstanceId == null) return List.of();
        return activeTasks().stream()
                .filter(task -> resourceService.boundDeviceInstanceIds(task.getResourceMap()).contains(deviceInstanceId))
                .toList();
    }

    public Task task(Long taskId) {
        return taskId == null ? null : taskMapper.selectById(taskId);
    }

    public static boolean isActive(Task task) {
        return task != null && ACTIVE_STATES.contains(task.getTaskStatus());
    }

    public static boolean isTaskRule(ConstraintRule rule) {
        return rule != null && rule.getId() != null && rule.getId() < 0;
    }

    private void normalizeBindings(Task task, JsonNode bindings, Set<Long> boundInstances,
                                   Set<Long> workflowModelIds, int ruleIndex) {
        if (!bindings.isObject()) return;
        var fields = bindings.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            JsonNode binding = entry.getValue();
            if (!"OBSERVABLE".equals(binding.path("bindingType").asText()) || !binding.path("source").isObject()) continue;
            ObjectNode source = (ObjectNode) binding.path("source");
            String sourceType = source.path("sourceType").asText();
            if (DEVICE_SOURCES.contains(sourceType)) {
                long instanceId = source.path("deviceInstanceId").asLong(0);
                requireBoundDevice(boundInstances, instanceId, "taskConstraints[" + ruleIndex + "].bindings." + entry.getKey());
                DeviceInstances instance = resourceService.requireUsableInstance(instanceId);
                long modelId = source.path("deviceModelId").asLong(0);
                if (modelId <= 0 || !Long.valueOf(modelId).equals(instance.getDeviceModelId())) {
                    throw new IllegalArgumentException("任务约束设备观测的deviceModelId与绑定实例不一致");
                }
            } else if (NODE_SOURCES.contains(sourceType)) {
                long flowModelId = source.path("workflowTemplateId").asLong(0);
                String nodeName = source.path("nodeName").asText("");
                if (!workflowModelIds.contains(flowModelId) || !resourceService.containsNode(flowModelId, nodeName)) {
                    throw new IllegalArgumentException("任务约束引用了任务工作流之外的节点: " + flowModelId + ":" + nodeName);
                }
            } else if ("TASK_LIFECYCLE_STATE".equals(sourceType)) {
                source.put("taskId", task.getId());
            }
        }
    }

    private void normalizeActions(Task task, JsonNode actions, Set<Long> boundInstances, int ruleIndex) {
        if (!actions.isArray()) return;
        for (int actionIndex = 0; actionIndex < actions.size(); actionIndex++) {
            JsonNode actionNode = actions.get(actionIndex);
            if (!actionNode.isObject()) continue;
            ObjectNode action = (ObjectNode) actionNode;
            if ("SYSTEM".equals(action.path("actionType").asText())) {
                action.put("targetTaskId", task.getId());
            } else if ("DEVICE_CAPABILITY".equals(action.path("actionType").asText())) {
                requireBoundDevice(boundInstances, action.path("deviceInstanceId").asLong(0),
                        "taskConstraints[" + ruleIndex + "].violationActions[" + actionIndex + "]");
            }
        }
    }

    private void requireBoundDevice(Set<Long> boundInstances, long instanceId, String scope) {
        if (instanceId <= 0 || !boundInstances.contains(instanceId)) {
            throw new IllegalArgumentException(scope + "引用的设备实例不属于当前任务resourceMap");
        }
    }

    private ConstraintRule toRule(JsonNode node) {
        ConstraintRule rule = new ConstraintRule();
        rule.setRuleName(node.path("ruleName").asText(null));
        rule.setExpression(node.path("expression").asText(null));
        rule.setBindings(node.path("bindings").deepCopy());
        rule.setWindowSeconds(node.path("windowSeconds").canConvertToInt() ? node.path("windowSeconds").asInt() : null);
        rule.setViolationActions(node.path("violationActions").deepCopy());
        rule.setDescription(node.path("description").asText(null));
        rule.setIsEnabled(!node.path("isEnabled").isBoolean() || node.path("isEnabled").asBoolean());
        return rule;
    }

    private long runtimeRuleId(long taskId, int index) {
        return -Math.addExact(Math.multiplyExact(taskId, 1_000_000L), index + 1L);
    }
}

