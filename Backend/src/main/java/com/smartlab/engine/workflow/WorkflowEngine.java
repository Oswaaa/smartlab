package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.StateMachineEngine;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.entity.workflow.FlowModels;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.StepLog;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.mapper.workflow.FlowModelsMapper;
import com.smartlab.management.mapper.workflow.FlowNodeMapper;
import com.smartlab.management.mapper.workflow.StepLogMapper;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.mapper.workflow.TaskStepMapper;
import com.smartlab.management.service.db.resource.device.DeviceTwinStateService;
import com.smartlab.global.util.JsonNodeSupport;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final TaskMapper taskMapper;
    private final TaskStepMapper taskStepMapper;
    private final FlowModelsMapper flowModelsMapper;
    private final FlowNodeMapper flowNodeMapper;
    private final StepLogMapper stepLogMapper;
    private final StateMachineEngine stateMachineEngine;
    private final DeviceTwinStateService deviceTwinStateService;

    public WorkflowEngine(TaskMapper taskMapper, TaskStepMapper taskStepMapper,
                          FlowModelsMapper flowModelsMapper, FlowNodeMapper flowNodeMapper,
                          StepLogMapper stepLogMapper,
                          StateMachineEngine stateMachineEngine, DeviceTwinStateService deviceTwinStateService) {
        this.taskMapper = taskMapper;
        this.taskStepMapper = taskStepMapper;
        this.flowModelsMapper = flowModelsMapper;
        this.flowNodeMapper = flowNodeMapper;
        this.stepLogMapper = stepLogMapper;
        this.stateMachineEngine = stateMachineEngine;
        this.deviceTwinStateService = deviceTwinStateService;
    }

    @Scheduled(fixedDelay = 2000)
    public void driveWorkflows() {
        List<Task> runningTasks = taskMapper.selectList(Wrappers.<Task>lambdaQuery().eq(Task::getTaskStatus, "RUNNING"));
        for (Task task : runningTasks) {
            try {
                processTask(task);
            } catch (Exception e) {
                log.error("执行任务 {} 失败", task.getId(), e);
                appendLog(task.getId(), null, "ERROR", "执行引擎异常: " + e.getMessage());
            }
        }
    }

    private void processTask(Task task) {
        FlowModels flowModel = flowModelsMapper.selectById(task.getFlowModelId());
        if (flowModel == null) {
            failTask(task, "找不到关联的流程模型");
            return;
        }

        List<TaskStep> activeSteps = taskStepMapper.selectList(
                Wrappers.<TaskStep>lambdaQuery().eq(TaskStep::getTaskId, task.getId())
                        .in(TaskStep::getNodeStatus, "PENDING", "RUNNING"));

        if (activeSteps.isEmpty()) {
            List<TaskStep> completedSteps = taskStepMapper.selectList(
                    Wrappers.<TaskStep>lambdaQuery().eq(TaskStep::getTaskId, task.getId())
                            .eq(TaskStep::getNodeStatus, "COMPLETED"));
            if (completedSteps.isEmpty()) {
                // 初始化主流程
                startFlow(task, flowModel.getId(), null, 0);
            }
        } else {
            // 将节点列表按模型分组缓存，避免多次查询
            Map<Long, List<FlowNode>> modelNodesCache = new HashMap<>();
            
            for (TaskStep step : activeSteps) {
                Long currentModelId = getModelIdForStep(task, step);
                if (currentModelId == null) continue;
                
                List<FlowNode> flowNodes = modelNodesCache.computeIfAbsent(currentModelId, 
                    id -> flowNodeMapper.selectList(Wrappers.<FlowNode>lambdaQuery().eq(FlowNode::getFlowModelId, id)));
                
                processStep(task, currentModelId, flowNodes, step);
            }
        }
    }

    private Long getModelIdForStep(Task task, TaskStep step) {
        if (step.getParentStepId() == null) {
            return task.getFlowModelId();
        }
        TaskStep parentStep = taskStepMapper.selectById(step.getParentStepId());
        if (parentStep == null) return null;
        FlowNode parentNode = flowNodeMapper.selectById(parentStep.getFlowNodeId());
        if (parentNode == null) return null;
        return parentNode.getSubFlowModelId();
    }

    private void startFlow(Task task, Long flowModelId, Long parentStepId, int depth) {
        List<FlowNode> flowNodes = flowNodeMapper.selectList(
                Wrappers.<FlowNode>lambdaQuery().eq(FlowNode::getFlowModelId, flowModelId));
                
        FlowNode startNode = flowNodes.stream()
                .filter(n -> "FUNCTIONAL_NODE".equals(n.getNodeType()) && 
                             n.getCapability() != null && 
                             "START".equals(n.getCapability().path("functionType").asText()))
                .findFirst().orElse(null);

        if (startNode == null) {
            if (depth == 0) failTask(task, "流程中没有找到 START 节点");
            return;
        }

        startNewStep(task, startNode, parentStepId, depth, JsonNodeSupport.objectNode());
    }

    private void processStep(Task task, Long currentModelId, List<FlowNode> flowNodes, TaskStep step) {
        FlowNode nodeDef = flowNodes.stream().filter(n -> n.getId().equals(step.getFlowNodeId())).findFirst().orElse(null);
        if (nodeDef == null) {
            failStep(step, "找不到节点定义: " + step.getFlowNodeId());
            return;
        }

        String nodeType = nodeDef.getNodeType();
        
        if ("PENDING".equals(step.getNodeStatus())) {
            step.setNodeStatus("RUNNING");
            step.setStartTime(OffsetDateTime.now());
            taskStepMapper.updateById(step);
            appendLog(task.getId(), null, "INFO", "节点 [REF:" + nodeDef.getNodeIdRef() + "] 开始执行");

            if ("DEVICE_CAPABILITY_NODE".equals(nodeType)) {
                triggerDevice(task, step, nodeDef);
            } else if ("SUB_FLOW_NODE".equals(nodeType)) {
                // 启动子流程
                startFlow(task, nodeDef.getSubFlowModelId(), step.getId(), step.getStepDepth() + 1);
            } else {
                // FUNCTIONAL_NODE
                String funcType = nodeDef.getCapability() != null ? nodeDef.getCapability().path("functionType").asText() : "";
                if ("WAIT".equals(funcType)) {
                    step.setInterfaceInSnapshot(JsonNodeSupport.objectNode().put("waitStartTime", System.currentTimeMillis()));
                    taskStepMapper.updateById(step);
                } else if ("SYNC".equals(funcType) || "JOIN".equals(funcType)) {
                    completeStep(task, currentModelId, step, nodeDef, "flow_out");
                } else if ("BRANCH".equals(funcType)) {
                    completeStep(task, currentModelId, step, nodeDef, "flow_yes"); 
                } else if ("END".equals(funcType)) {
                    completeStep(task, currentModelId, step, nodeDef, "flow_out");
                    if (step.getStepDepth() == 0) {
                        completeTask(task);
                    } else {
                        // 子流程结束，标记父 SUB_FLOW_NODE 步骤为 COMPLETED
                        TaskStep parentStep = taskStepMapper.selectById(step.getParentStepId());
                        if (parentStep != null) {
                            Long parentModelId = getModelIdForStep(task, parentStep);
                            if (parentModelId != null) {
                                FlowNode parentNodeDef = flowNodeMapper.selectById(parentStep.getFlowNodeId());
                                completeStep(task, parentModelId, parentStep, parentNodeDef, "flow_out");
                            }
                        }
                    }
                } else {
                    completeStep(task, currentModelId, step, nodeDef, "flow_out");
                }
            }
        } else if ("RUNNING".equals(step.getNodeStatus())) {
            if ("DEVICE_CAPABILITY_NODE".equals(nodeType)) {
                checkDeviceStatus(task, currentModelId, step, nodeDef);
            } else if ("FUNCTIONAL_NODE".equals(nodeType)) {
                String funcType = nodeDef.getCapability() != null ? nodeDef.getCapability().path("functionType").asText() : "";
                if ("WAIT".equals(funcType)) {
                    long waitStart = step.getInterfaceInSnapshot() != null ? step.getInterfaceInSnapshot().path("waitStartTime").asLong(0) : 0;
                    long duration = nodeDef.getCapability().path("durationMs").asLong(0);
                    if (System.currentTimeMillis() - waitStart >= duration) {
                        completeStep(task, currentModelId, step, nodeDef, "flow_out");
                    }
                }
            }
        }
    }

    private void triggerDevice(Task task, TaskStep step, FlowNode nodeDef) {
        String nodeRefId = String.valueOf(nodeDef.getNodeIdRef());
        JsonNode resourceMap = task.getResourceMap();
        Long instanceId = null;
        if (resourceMap != null && resourceMap.has(nodeRefId)) {
            instanceId = resourceMap.get(nodeRefId).asLong();
        }

        if (instanceId == null) {
            failStep(step, "无法分配设备资源，节点 REF: " + nodeRefId);
            return;
        }

        ObjectNode snapshot = step.getInterfaceInSnapshot() == null ? JsonNodeSupport.objectNode() : (ObjectNode) step.getInterfaceInSnapshot().deepCopy();
        snapshot.put("boundInstanceId", instanceId);
        step.setInterfaceInSnapshot(snapshot);
        taskStepMapper.updateById(step);

        JsonNode actions = nodeDef.getActions();
        if (actions == null || !actions.isArray() || actions.isEmpty()) {
             failStep(step, "节点未配置设备动作");
             return;
        }
        
        JsonNode action = actions.get(0);
        String commandId = action.path("payload").path("commandId").asText("");
        JsonNode paramsNode = action.path("payload").path("parameters");
        Map<String, Object> parameters = new HashMap<>();
        if (paramsNode != null && paramsNode.isObject()) {
            paramsNode.fields().forEachRemaining(entry -> parameters.put(entry.getKey(), entry.getValue().asText()));
        }

        try {
            stateMachineEngine.handleManualControl(instanceId, "EXECUTE_START", commandId, parameters);
            appendLog(task.getId(), instanceId, "INFO", "已向设备下发指令: " + commandId);
        } catch (Exception e) {
            failStep(step, "设备控制调用失败: " + e.getMessage());
        }
    }

    private void checkDeviceStatus(Task task, Long currentModelId, TaskStep step, FlowNode nodeDef) {
        Long instanceId = step.getInterfaceInSnapshot().path("boundInstanceId").asLong(0);
        if (instanceId == 0) {
            failStep(step, "节点丢失绑定的设备实例");
            return;
        }

        DeviceTwinStates twinState = deviceTwinStateService.getByInstanceId(instanceId);
        if (twinState == null) return;

        String cmdState = twinState.getCurrentCmdState();
        if ("IDLE".equals(cmdState) || "SUCCESS".equals(cmdState) || "COMPLETED".equals(cmdState)) {
            appendLog(task.getId(), instanceId, "INFO", "设备指令执行完成");
            completeStep(task, currentModelId, step, nodeDef, "flow_out");
        } else if ("FAILED".equals(cmdState) || "ERROR".equals(cmdState)) {
            failStep(step, "设备报告错误状态: " + cmdState);
        }
    }

    private void completeStep(Task task, Long currentModelId, TaskStep step, FlowNode nodeDef, String outputInterfacePrefix) {
        step.setNodeStatus("COMPLETED");
        step.setEndTime(OffsetDateTime.now());
        if (step.getStartTime() != null) {
            step.setDurationMs(step.getEndTime().toInstant().toEpochMilli() - step.getStartTime().toInstant().toEpochMilli());
        }
        taskStepMapper.updateById(step);
        appendLog(task.getId(), null, "INFO", "节点 [REF:" + nodeDef.getNodeIdRef() + "] 执行完成");

        FlowModels currentFlowModel = flowModelsMapper.selectById(currentModelId);
        if (currentFlowModel == null) return;

        JsonNode connections = currentFlowModel.getInterfaceConnection();
        if (connections != null && connections.isArray()) {
            for (JsonNode conn : connections) {
                String sourceRef = conn.path("source").path("interfaceRef").asText("");
                String expectedRefPrefix = nodeDef.getNodeIdRef() + "_" + outputInterfacePrefix;
                String expectedRefFallback = nodeDef.getNodeIdRef() + "_";

                if (sourceRef.startsWith(expectedRefPrefix) || sourceRef.startsWith(expectedRefFallback)) {
                    String targetRef = conn.path("target").path("interfaceRef").asText("");
                    String targetNodeRefStr = targetRef.split("_")[0];
                    try {
                        Long targetNodeRef = Long.valueOf(targetNodeRefStr);
                        FlowNode nextNodeDef = flowNodeMapper.selectOne(
                            Wrappers.<FlowNode>lambdaQuery()
                                .eq(FlowNode::getFlowModelId, currentModelId)
                                .eq(FlowNode::getNodeIdRef, targetNodeRef)
                        );
                        if (nextNodeDef != null) {
                            startNewStep(task, nextNodeDef, step.getParentStepId(), step.getStepDepth(), JsonNodeSupport.objectNode());
                        }
                    } catch (NumberFormatException e) {
                        log.warn("无法解析 targetRef: {}", targetRef);
                    }
                }
            }
        }
    }

    private void startNewStep(Task task, FlowNode nodeDef, Long parentStepId, int depth, JsonNode inputSnapshot) {
        long count = taskStepMapper.selectCount(Wrappers.<TaskStep>lambdaQuery()
            .eq(TaskStep::getTaskId, task.getId())
            .eq(TaskStep::getFlowNodeId, nodeDef.getId())
            .eq(parentStepId != null, TaskStep::getParentStepId, parentStepId)
            .in(TaskStep::getNodeStatus, "PENDING", "RUNNING"));
        if (count > 0) return;

        TaskStep newStep = new TaskStep();
        newStep.setTaskId(task.getId());
        newStep.setFlowNodeId(nodeDef.getId());
        newStep.setNodeIdRef(nodeDef.getNodeIdRef());
        newStep.setParentStepId(parentStepId);
        newStep.setStepDepth(depth);
        newStep.setNodeStatus("PENDING");
        newStep.setInterfaceInSnapshot(inputSnapshot);
        
        taskStepMapper.insert(newStep);
        appendLog(task.getId(), null, "INFO", "触发节点 [REF:" + nodeDef.getNodeIdRef() + "]");
    }

    private void completeTask(Task task) {
        if (!"COMPLETED".equals(task.getTaskStatus())) {
            task.setTaskStatus("COMPLETED");
            task.setEndTime(OffsetDateTime.now());
            taskMapper.updateById(task);
            appendLog(task.getId(), null, "INFO", "流程到达 END，任务成功结束");
        }
    }

    private void failStep(TaskStep step, String reason) {
        step.setNodeStatus("FAILED");
        step.setEndTime(OffsetDateTime.now());
        taskStepMapper.updateById(step);
        appendLog(step.getTaskId(), null, "ERROR", "节点执行失败: " + reason);
        
        Task task = taskMapper.selectById(step.getTaskId());
        if (task != null) {
            failTask(task, "由于节点执行失败导致任务失败");
        }
    }

    private void failTask(Task task, String reason) {
        if ("FAILED".equals(task.getTaskStatus())) return;
        task.setTaskStatus("FAILED");
        task.setEndTime(OffsetDateTime.now());
        taskMapper.updateById(task);
        appendLog(task.getId(), null, "ERROR", "任务失败: " + reason);
    }

    private void appendLog(Long taskId, Long deviceInstanceId, String level, String message) {
        StepLog logEntry = new StepLog();
        logEntry.setSourceType("TASK");
        logEntry.setTaskId(taskId);
        logEntry.setDeviceInstanceId(deviceInstanceId);
        logEntry.setLogLevel(level);
        logEntry.setLogInfo(message);
        logEntry.setLogTime(OffsetDateTime.now());
        stepLogMapper.insert(logEntry);
    }
}
