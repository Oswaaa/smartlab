package com.smartlab.engine.connection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.StateMachineCommandPort;
import com.smartlab.engine.statemachine.StateMachineInterfaceSignalEvent;
import com.smartlab.engine.workflow.WorkflowExecutionLogs;
import com.smartlab.engine.workflow.WorkflowExecutionOperations;
import com.smartlab.engine.workflow.WorkflowInterfaceSnapshots;
import com.smartlab.engine.workflow.WorkflowTaskLocks;
import com.smartlab.engine.workflow.WorkflowTaskPollScheduler;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.workflow.FlowNodeService;
import com.smartlab.management.service.db.workflow.WorkflowRuntimeService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 接口连接转发：模板在 catalog，运行时走带任务/步骤/实例的活边。
 * NODE_TO_NODE / NODE_TO_DEVICE 由工作流交出 OUT 后调用；DEVICE_TO_NODE 监听状态机广播并原路写回。
 */
@Service
public class InterfaceConnectionForwarder {
    private static final Set<String> TERMINAL_NODE_STATES = Set.of("SUCCEEDED", "FAILED", "TERMINATED");
    private static final Set<String> ACTIVE_NODE_STATES = Set.of("PENDING", "RUNNING", "TERMINATING");
    private static final Set<String> DEVICE_EDGE_STATES = Set.of("RUNNING", "TERMINATING");
    private static final Set<String> TERMINAL_COMMAND_STATES = Set.of("COMPLETED", "FAILED", "ABORTED");

    private final WorkflowRuntimeService runtime;
    private final WorkflowService workflowService;
    private final FlowNodeService flowNodeService;
    private final WorkflowExecutionOperations executionOperations;
    private final InterfaceConnectionCatalog catalog;
    private final PortConnectionPuller portConnectionPuller;
    private final StateMachineCommandPort stateMachineCommandPort;
    private final InterfaceConnectionIndex index;
    private final WorkflowTaskPollScheduler pollScheduler;

    @Autowired
    public InterfaceConnectionForwarder(WorkflowRuntimeService runtime, WorkflowService workflowService,
            FlowNodeService flowNodeService, @Lazy WorkflowExecutionOperations executionOperations,
            InterfaceConnectionCatalog catalog, PortConnectionPuller portConnectionPuller,
            StateMachineCommandPort stateMachineCommandPort, InterfaceConnectionIndex index,
            @Lazy WorkflowTaskPollScheduler pollScheduler) {
        this.runtime = runtime;
        this.workflowService = workflowService;
        this.flowNodeService = flowNodeService;
        this.executionOperations = executionOperations;
        this.catalog = catalog;
        this.portConnectionPuller = portConnectionPuller;
        this.stateMachineCommandPort = stateMachineCommandPort;
        this.index = index != null ? index : new InterfaceConnectionIndex();
        this.pollScheduler = pollScheduler;
    }

    public InterfaceConnectionForwarder(WorkflowRuntimeService runtime, WorkflowService workflowService,
            FlowNodeService flowNodeService, WorkflowExecutionOperations executionOperations,
            InterfaceConnectionCatalog catalog, PortConnectionPuller portConnectionPuller,
            StateMachineCommandPort stateMachineCommandPort, InterfaceConnectionIndex index) {
        this(runtime, workflowService, flowNodeService, executionOperations, catalog, portConnectionPuller,
                stateMachineCommandPort, index, null);
    }

    public InterfaceConnectionForwarder(WorkflowRuntimeService runtime, WorkflowService workflowService,
            FlowNodeService flowNodeService, WorkflowExecutionOperations executionOperations,
            InterfaceConnectionCatalog catalog, PortConnectionPuller portConnectionPuller,
            StateMachineCommandPort stateMachineCommandPort) {
        this(runtime, workflowService, flowNodeService, executionOperations, catalog, portConnectionPuller,
                stateMachineCommandPort, new InterfaceConnectionIndex(), null);
    }

    public InterfaceConnectionForwarder(WorkflowRuntimeService runtime, WorkflowService workflowService,
            FlowNodeService flowNodeService, WorkflowExecutionOperations executionOperations,
            PortConnectionPuller portConnectionPuller) {
        this(runtime, workflowService, flowNodeService, executionOperations,
                new InterfaceConnectionCatalog(workflowService), portConnectionPuller, null,
                new InterfaceConnectionIndex(), null);
    }

    public void refreshForTask(Task task) {
        if (task == null || task.getId() == null) return;
        refreshForTask(task, runtime.steps(task.getId()));
    }

    public void refreshForTask(Task task, List<TaskStep> steps) {
        if (task == null || task.getId() == null) return;
        if (steps == null || steps.isEmpty()) {
            index.replaceTaskEdges(task.getId(), List.of());
            return;
        }
        Map<Long, FlowNode> nodesByStepId = nodesByStepId(steps);
        List<LiveInterfaceConnection> active = new ArrayList<>();
        java.util.Set<Long> processedModels = new java.util.HashSet<>();
        for (FlowNode node : nodesByStepId.values()) {
            Long flowModelId = node.getFlowModelId();
            if (flowModelId == null || !processedModels.add(flowModelId)) continue;
            collectNodeToNode(task, steps, nodesByStepId, flowModelId, active);
        }
        for (TaskStep step : steps) {
            FlowNode node = nodesByStepId.get(step.getId());
            if (node == null || !"DEV_NODE".equals(node.getNodeType())) continue;
            if (!DEVICE_EDGE_STATES.contains(step.getNodeStatus())) continue;
            Long instanceId = resolveInstanceOrNull(task, step, node);
            if (instanceId == null) continue;
            collectDeviceEdges(task, step, node, instanceId, active);
        }
        index.replaceTaskEdges(task.getId(), active);
    }

    /** 工作流节点 OUT 已发出：按 NODE_TO_NODE 活边投递到下游 IN。 */
    public void forwardFromNode(Task task, TaskStep sourceStep, FlowNode sourceNode,
            String sourceInterfaceName, String signalName) {
        if (task == null || sourceStep == null || sourceNode == null || sourceInterfaceName == null) return;
        for (var connection : catalog.nodeToNodeFrom(sourceNode, sourceInterfaceName)) {
            FlowNode target = nodeByRef(sourceNode.getFlowModelId(), connection.targetNodeIdRef());
            JsonNode targetInterface = interfaceByName(target, connection.targetInterface());
            if (!allows(targetInterface, signalName)) continue;
            TaskStep targetStep = runtime.createStep(task, target, sourceStep.getParentStepId(),
                    sourceStep.getStepDepth(), null);
            if (TERMINAL_NODE_STATES.contains(targetStep.getNodeStatus())) continue;
            index.upsert(new LiveInterfaceConnection("NODE_TO_NODE", task.getId(), sourceNode.getFlowModelId(),
                    sourceNode.getNodeIdRef(), target.getNodeIdRef(), sourceInterfaceName,
                    connection.targetInterface(), null, null, sourceStep.getId(), targetStep.getId()));
            JsonNode sourceSlot = WorkflowInterfaceSnapshots.find(
                    sourceStep.getInterfaceOutSnapshot(), sourceInterfaceName);
            JsonNode payload = sourceSlot.path("payload");
            ArrayNode input = WorkflowInterfaceSnapshots.withSignal(
                    targetStep.getInterfaceInSnapshot(), target.getInterfaces(), "IN",
                    connection.targetInterface(), signalName, payload.isObject() ? payload : null);
            if (!input.equals(targetStep.getInterfaceInSnapshot())) {
                runtime.updateInputSnapshot(targetStep, input);
                runtime.appendStepLog(task, targetStep, "INFO", WorkflowExecutionLogs.received(
                        nodeName(target), target.getNodeIdRef(), connection.targetInterface(), signalName,
                        payload.isObject() ? payload : null));
            }
            if (portConnectionPuller != null) {
                portConnectionPuller.pullBetween(task, sourceStep, sourceNode, targetStep, target);
            }
        }
    }

    public WorkflowExecutionOperations.DeviceDispatchResult forwardToDevice(Task task, TaskStep step, FlowNode node,
            long deviceInstanceId, String messageId, String nodeOutputInterfaceName, String signalName,
            JsonNode parameters) {
        ensureDeviceLiveEdges(task, step, node, deviceInstanceId);
        LiveInterfaceConnection outbound = index.nodeToDevice(task.getId(), step.getId());
        if (outbound == null) {
            throw new IllegalStateException("DEV_NODE缺少NODE_TO_DEVICE运行时连接: " + node.getNodeIdRef());
        }
        if (!outbound.sourceInterfaceName().equals(nodeOutputInterfaceName)) {
            throw new IllegalArgumentException("STATE动作输出接口未连接到设备模型: " + nodeOutputInterfaceName);
        }
        if (stateMachineCommandPort == null) {
            throw new IllegalStateException("接口连接转发缺少状态机命令端口");
        }
        Map<String, Object> context = new HashMap<>();
        context.put("messageId", messageId);
        context.put("capabilityName", node.getCapability().path("capabilityName").asText());
        context.put("parameters", parameters != null && parameters.isObject()
                ? JsonNodeSupport.MAPPER.convertValue(parameters, Map.class) : Map.of());
        index.occupy(deviceInstanceId, task.getId(), step.getId(), messageId);
        WorkflowExecutionOperations.DeviceDispatchResult result =
                stateMachineCommandPort.dispatchInputSignal(deviceInstanceId, outbound.targetInterfaceName(),
                        signalName, context).isEmpty()
                ? WorkflowExecutionOperations.DeviceDispatchResult.DEVICE_BUSY
                : WorkflowExecutionOperations.DeviceDispatchResult.ACCEPTED;
        if (result == WorkflowExecutionOperations.DeviceDispatchResult.DEVICE_BUSY) {
            index.releaseIfRejected(deviceInstanceId, messageId);
        }
        return result;
    }

    public void forwardAbort(Task task, TaskStep step, FlowNode node, long deviceInstanceId, String messageId) {
        ensureDeviceLiveEdges(task, step, node, deviceInstanceId);
        LiveInterfaceConnection outbound = index.nodeToDevice(task.getId(), step.getId());
        if (outbound == null) {
            throw new IllegalStateException("DEV_NODE缺少NODE_TO_DEVICE运行时连接: " + node.getNodeIdRef());
        }
        if (stateMachineCommandPort == null) {
            throw new IllegalStateException("接口连接转发缺少状态机命令端口");
        }
        Map<String, Object> context = new HashMap<>();
        context.put("messageId", messageId);
        if (stateMachineCommandPort.dispatchInputSignal(deviceInstanceId, outbound.targetInterfaceName(),
                "WF_EXECUTE_ABORT", context).isEmpty()) {
            throw new IllegalStateException("设备状态机未接受WF_EXECUTE_ABORT");
        }
    }

    @EventListener
    public void onStateMachineSignal(StateMachineInterfaceSignalEvent event) {
        if (event == null || !"STATE".equals(event.interfaceType()) || event.signal() == null) return;
        String signalName = event.signal().path("signalName").asText();
        if (!("CMD_STATE".equals(signalName) || "OP_STATE".equals(signalName))) return;

        Long deviceInstanceId = deviceInstanceId(event);
        if (deviceInstanceId == null) return;
        bindRunningDeviceEdges(deviceInstanceId);

        if ("CMD_STATE".equals(signalName)) {
            String messageId = event.signal().path("payload").path("messageId").asText("");
            if (index.isClosed(deviceInstanceId, messageId)) return;
            LiveInterfaceConnection inbound = occupiedInbound(deviceInstanceId, messageId);
            if (inbound == null) inbound = inboundMatchingMessage(deviceInstanceId, messageId);
            if (inbound == null || inbound.targetStepId() == null) return;
            TaskStep step = runtime.step(inbound.targetStepId());
            if (step == null) return;
            if (deliverDeviceToNode(event, step, inbound) && pollScheduler != null) {
                pollScheduler.requestPoll(step.getTaskId());
            }
            if (TERMINAL_COMMAND_STATES.contains(event.signal().path("payload").path("stateName").asText(""))) {
                index.releaseAndClose(deviceInstanceId, messageId);
            }
            return;
        }

        for (LiveInterfaceConnection inbound : index.deviceToNode(deviceInstanceId)) {
            if (inbound.targetStepId() == null) continue;
            TaskStep step = runtime.step(inbound.targetStepId());
            if (step != null && deliverDeviceToNode(event, step, inbound) && pollScheduler != null) {
                pollScheduler.requestPoll(step.getTaskId());
            }
        }
    }

    public InterfaceConnectionIndex index() {
        return index;
    }

    private void collectNodeToNode(Task task, List<TaskStep> steps, Map<Long, FlowNode> nodesByStepId,
            Long flowModelId, List<LiveInterfaceConnection> active) {
        WorkflowDetailResponse definition = workflowService.getDefinition(flowModelId);
        JsonNode connections = definition == null ? null : definition.getInterfaceConnections();
        if (connections == null || !connections.isArray()) return;
        var compiled = workflowService.compileDefinition(flowModelId);
        for (JsonNode connection : connections) {
            if (!"NODE_TO_NODE".equals(connection.path("connectionType").asText())) continue;
            Long sourceRef = compiled.refsByNodeName().get(connection.path("source").path("nodeName").asText(""));
            Long targetRef = compiled.refsByNodeName().get(connection.path("target").path("nodeName").asText(""));
            String sourceInterface = connection.path("source").path("interfaceName").asText("");
            String targetInterface = connection.path("target").path("interfaceName").asText("");
            if (sourceRef == null || targetRef == null || sourceInterface.isBlank() || targetInterface.isBlank()) {
                continue;
            }
            for (TaskStep targetStep : steps) {
                FlowNode targetNode = nodesByStepId.get(targetStep.getId());
                if (targetNode == null || !flowModelId.equals(targetNode.getFlowModelId())) continue;
                if (!Objects.equals(targetRef, targetStep.getNodeIdRef())) continue;
                if (!ACTIVE_NODE_STATES.contains(targetStep.getNodeStatus())) continue;
                TaskStep sourceStep = findPeerStep(steps, nodesByStepId, flowModelId, sourceRef, targetStep);
                active.add(new LiveInterfaceConnection("NODE_TO_NODE", task.getId(), flowModelId,
                        sourceRef, targetRef, sourceInterface, targetInterface, null, null,
                        sourceStep == null ? null : sourceStep.getId(), targetStep.getId()));
            }
        }
    }

    private void collectDeviceEdges(Task task, TaskStep step, FlowNode node, long instanceId,
            List<LiveInterfaceConnection> active) {
        InterfaceConnectionCatalog.NodeToDeviceRoute outbound = catalog.findNodeToDevice(node);
        if (outbound != null) {
            active.add(new LiveInterfaceConnection("NODE_TO_DEVICE", task.getId(), node.getFlowModelId(),
                    node.getNodeIdRef(), null, outbound.nodeOutputInterfaceName(),
                    outbound.deviceInputInterfaceName(), outbound.deviceModelId(), instanceId,
                    step.getId(), null));
        }
        InterfaceConnectionEdge inbound = catalog.deviceToNode(node);
        if (inbound != null) {
            active.add(new LiveInterfaceConnection("DEVICE_TO_NODE", task.getId(), node.getFlowModelId(),
                    null, node.getNodeIdRef(), inbound.sourceInterfaceName(), inbound.targetInterfaceName(),
                    inbound.deviceModelId(), instanceId, null, step.getId()));
        }
    }

    private void ensureDeviceLiveEdges(Task task, TaskStep step, FlowNode node, long deviceInstanceId) {
        List<LiveInterfaceConnection> collected = new ArrayList<>();
        collectDeviceEdges(task, step, node, deviceInstanceId, collected);
        for (LiveInterfaceConnection edge : collected) {
            index.upsert(edge);
        }
    }

    private void bindRunningDeviceEdges(long deviceInstanceId) {
        for (TaskStep step : runtime.runningDeviceSteps()) {
            Task task = runtime.task(step.getTaskId());
            FlowNode node = flowNodeService.getById(step.getFlowNodeId());
            if (task == null || node == null || !"DEV_NODE".equals(node.getNodeType())) continue;
            Long instanceId = resolveInstanceOrNull(task, step, node);
            if (instanceId == null || instanceId != deviceInstanceId) continue;
            ensureDeviceLiveEdges(task, step, node, deviceInstanceId);
        }
    }

    private LiveInterfaceConnection occupiedInbound(long deviceInstanceId, String messageId) {
        InterfaceConnectionIndex.Occupancy occupancy = index.occupancy(deviceInstanceId);
        if (occupancy == null || !occupancy.messageId().equals(messageId)) return null;
        return index.deviceToNode(occupancy.taskId(), occupancy.stepId());
    }

    private LiveInterfaceConnection inboundMatchingMessage(long deviceInstanceId, String messageId) {
        if (messageId == null || messageId.isBlank()) return null;
        for (LiveInterfaceConnection inbound : index.deviceToNode(deviceInstanceId)) {
            if (inbound.targetStepId() == null) continue;
            TaskStep step = runtime.step(inbound.targetStepId());
            if (step == null) continue;
            Task task = runtime.task(step.getTaskId());
            FlowNode node = flowNodeService.getById(step.getFlowNodeId());
            if (task == null || node == null) continue;
            String expected = executionOperations.ensureMessageId(step, deviceInstanceId,
                    node.getCapability() == null ? "" : node.getCapability().path("capabilityName").asText(""));
            if (messageId.equals(expected)) {
                index.occupy(deviceInstanceId, task.getId(), step.getId(), messageId);
                return inbound;
            }
        }
        return null;
    }

    private boolean deliverDeviceToNode(StateMachineInterfaceSignalEvent event, TaskStep located,
            LiveInterfaceConnection inbound) {
        synchronized (WorkflowTaskLocks.of(located.getTaskId())) {
            TaskStep step = runtime.step(located.getId());
            if (step == null || !DEVICE_EDGE_STATES.contains(step.getNodeStatus())) return false;
            Task task = runtime.task(step.getTaskId());
            FlowNode node = flowNodeService.getById(step.getFlowNodeId());
            if (task == null || node == null || inbound == null) return false;
            Long deviceInstanceId = deviceInstanceId(event);
            if (deviceInstanceId == null || !Objects.equals(inbound.deviceInstanceId(), deviceInstanceId)) return false;
            if (!event.interfaceName().equals(inbound.sourceInterfaceName())) return false;
            JsonNode targetInterface = interfaceByName(node, inbound.targetInterfaceName());
            String signalName = event.signal().path("signalName").asText("");
            if (!allows(targetInterface, signalName)) return false;
            JsonNode payload = event.signal().path("payload");
            ArrayNode input = WorkflowInterfaceSnapshots.withSignal(
                    step.getInterfaceInSnapshot(), node.getInterfaces(), "IN", inbound.targetInterfaceName(),
                    signalName, payload.isObject() ? payload : null);
            boolean snapshotChanged = !input.equals(step.getInterfaceInSnapshot());
            if (snapshotChanged) {
                runtime.updateInputSnapshot(step, input);
                runtime.appendStepLog(task, step, "INFO", WorkflowExecutionLogs.received(
                        nodeName(node), node.getNodeIdRef(), inbound.targetInterfaceName(), signalName,
                        payload.isObject() ? payload : null));
            }
            ObjectNode mapped = executionOperations.resolveMappedVariables(task, step, node);
            if (mapped != null && !mapped.isEmpty()) {
                runtime.mergeVariableSpace(step, mapped);
            }
            return snapshotChanged;
        }
    }

    private String nodeName(FlowNode node) {
        if (node == null || node.getFlowModelId() == null || node.getNodeIdRef() == null) return null;
        try {
            return workflowService.nodeName(node.getFlowModelId(), node.getNodeIdRef());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Long resolveInstanceOrNull(Task task, TaskStep step, FlowNode node) {
        try {
            long instanceId = executionOperations.resolveDeviceInstance(task, step, node);
            return instanceId > 0 ? instanceId : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Map<Long, FlowNode> nodesByStepId(List<TaskStep> steps) {
        Map<Long, FlowNode> nodesByStepId = new HashMap<>();
        for (TaskStep step : steps) {
            FlowNode node = flowNodeService.getById(step.getFlowNodeId());
            if (node != null) nodesByStepId.put(step.getId(), node);
        }
        return nodesByStepId;
    }

    private TaskStep findPeerStep(List<TaskStep> steps, Map<Long, FlowNode> nodesByStepId,
            Long flowModelId, Long nodeIdRef, TaskStep peer) {
        for (TaskStep step : steps) {
            FlowNode node = nodesByStepId.get(step.getId());
            if (node == null || !flowModelId.equals(node.getFlowModelId())) continue;
            if (!Objects.equals(nodeIdRef, step.getNodeIdRef())) continue;
            if (!Objects.equals(peer.getParentStepId(), step.getParentStepId())) continue;
            int peerDepth = peer.getStepDepth() == null ? 0 : peer.getStepDepth();
            int depth = step.getStepDepth() == null ? 0 : step.getStepDepth();
            if (peerDepth == depth) return step;
        }
        return null;
    }

    private Long deviceInstanceId(StateMachineInterfaceSignalEvent event) {
        if (event.instanceId() != null && event.instanceId() > 0) return event.instanceId();
        if (event.signal().path("payload").path("deviceInstanceId").canConvertToLong()) {
            long value = event.signal().path("payload").path("deviceInstanceId").asLong();
            return value > 0 ? value : null;
        }
        return null;
    }

    private FlowNode nodeByRef(Long flowModelId, long nodeIdRef) {
        return workflowService.nodes(flowModelId).stream()
                .filter(node -> node.getNodeIdRef() == nodeIdRef)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("流程节点不存在: " + nodeIdRef));
    }

    private JsonNode interfaceByName(FlowNode node, String name) {
        if (node == null || node.getInterfaces() == null) return null;
        for (JsonNode item : node.getInterfaces()) {
            if (name.equals(item.path("name").asText())) return item;
        }
        return null;
    }

    private boolean allows(JsonNode interfaceDefinition, String signalName) {
        if (interfaceDefinition == null) return false;
        JsonNode allowed = interfaceDefinition.path("allowedSignals");
        if (!allowed.isArray()) return false;
        for (JsonNode item : allowed) {
            if (signalName.equals(item.asText())) return true;
        }
        return false;
    }
}
