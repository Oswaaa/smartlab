package com.smartlab.management.service.db.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.adapter.InProcessAdapterSimulator;
import com.smartlab.engine.connection.InterfaceConnectionCatalog;
import com.smartlab.engine.statemachine.StateMachineCommandPort;
import com.smartlab.engine.workflow.WorkflowDefinitionCompiler;
import com.smartlab.global.contract.WorkflowControlSignal;
import com.smartlab.global.contract.WorkflowNodeFunctionType;
import com.smartlab.global.contract.WorkflowNodeType;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.service.db.resource.device.DeviceTwinStateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 流程模拟结构遍历：只跟 NODE_TO_NODE 连线，不求值分支条件、不启动 {@code WorkflowEngine}。
 * DEV_NODE 用原状态机探测，优先按本次 messageId 的 CMD COMPLETED 结果判定。
 * SENT/RUNNING 后回到 IDLE 但没有匹配的完成结果，不能当成走通。
 */
@Component
public class WorkflowStructureWalker {

    private static final long PROBE_POLL_MS = 20;
    private static final Duration RESET_RESULT_GRACE = Duration.ofMillis(200);

    private final WorkflowService workflowService;
    private final InterfaceConnectionCatalog connectionCatalog;
    private final StateMachineCommandPort stateMachineCommandPort;
    private final DeviceTwinStateService twinStateService;
    private final InProcessAdapterSimulator adapterSimulator;
    private final Duration probeTimeout;

    @Autowired
    public WorkflowStructureWalker(WorkflowService workflowService,
                                     InterfaceConnectionCatalog connectionCatalog,
                                     StateMachineCommandPort stateMachineCommandPort,
                                     DeviceTwinStateService twinStateService,
                                     InProcessAdapterSimulator adapterSimulator) {
        this(workflowService, connectionCatalog, stateMachineCommandPort, twinStateService, adapterSimulator,
                Duration.ofSeconds(WorkflowSimulationService.DEVICE_PROBE_BUDGET_SECONDS));
    }

    WorkflowStructureWalker(WorkflowService workflowService,
                            InterfaceConnectionCatalog connectionCatalog,
                            StateMachineCommandPort stateMachineCommandPort,
                            DeviceTwinStateService twinStateService,
                            InProcessAdapterSimulator adapterSimulator, Duration probeTimeout) {
        this.workflowService = workflowService;
        this.connectionCatalog = connectionCatalog;
        this.stateMachineCommandPort = stateMachineCommandPort;
        this.twinStateService = twinStateService;
        this.adapterSimulator = adapterSimulator;
        this.probeTimeout = probeTimeout;
    }

    public WalkResult walk(long flowModelId, Map<Long, DeviceInstances> tempsByModel, Instant deadline) {
        return walk(flowModelId, null, null, tempsByModel, deadline);
    }

    public WalkResult walk(WorkflowDefinitionCompiler.CompiledWorkflow compiled, JsonNode interfaceConnections,
                            Long flowModelId, Map<Long, DeviceInstances> tempsByModel, Instant deadline) {
        long rootId = flowModelId == null ? 0L : flowModelId;
        return walk(rootId, compiled, interfaceConnections, tempsByModel, deadline);
    }

    private WalkResult walk(long rootFlowModelId, WorkflowDefinitionCompiler.CompiledWorkflow rootCompiled,
                              JsonNode rootConnections, Map<Long, DeviceInstances> tempsByModel, Instant deadline) {
        Session session = new Session(tempsByModel == null ? Map.of() : tempsByModel, deadline,
                rootFlowModelId, rootCompiled, rootConnections);
        try {
            visitFlow(rootFlowModelId, session, new LinkedHashSet<>());
        } catch (WalkStopped ignored) {
        }
        return new WalkResult(session.issues.isEmpty(), List.copyOf(session.pathTaken),
                session.issues.isEmpty() ? enumerateRoutes(rootFlowModelId, session) : List.of(),
                List.copyOf(session.issues));
    }

    private void visitFlow(long flowModelId, Session session, Set<Long> subflowStack) {
        WorkflowDefinitionCompiler.CompiledWorkflow compiled = compiledOf(flowModelId, session);
        visitNode(flowModelId, compiled.startNodeIdRef(), compiled, session, new LinkedHashSet<>(), subflowStack);
    }

    private void visitNode(long flowModelId, long nodeIdRef, WorkflowDefinitionCompiler.CompiledWorkflow compiled,
                            Session session, Set<String> onPath, Set<Long> subflowStack) {
        session.currentNode = String.join(" → ", session.subflowNames)
                + (session.subflowNames.isEmpty() ? "" : " → ") + compiled.nodeName(nodeIdRef);
        session.requireTime();
        String pathKey = flowModelId + ":" + nodeIdRef;
        if (!onPath.add(pathKey)) {
            session.fail(issue("SIM_CYCLE", String.valueOf(nodeIdRef),
                    compiled.nodeName(nodeIdRef) + ": 结构遍历遇到环", "去掉 NODE_TO_NODE 环路后重试"));
        }
        JsonNode node = compiled.nodes().get(nodeIdRef);
        if (node == null) {
            session.fail(issue("SIM_FAILED", String.valueOf(nodeIdRef),
                    "结构遍历找不到节点 " + nodeIdRef, "重新保存流程草稿后重试"));
        }
        if (session.pathSeen.add(pathKey)) {
            session.pathTaken.add(compiled.nodeName(nodeIdRef));
        }
        try {
            String nodeType = node.path("nodeType").asText("");
            if (WorkflowNodeType.DEV_NODE.name().equals(nodeType)) {
                probeDevice(flowModelId, nodeIdRef, node, compiled, session);
            } else if (WorkflowNodeType.SUBFLOW_NODE.name().equals(nodeType)) {
                long subFlowId = node.path("subFlowModelId").asLong(0);
                if (subFlowId <= 0) {
                    session.fail(issue("SIM_FAILED", String.valueOf(nodeIdRef),
                            compiled.nodeName(nodeIdRef) + ": SUBFLOW_NODE缺少subFlowModelId",
                            "为子流程节点指定已保存的子流程"));
                }
                if (!subflowStack.add(subFlowId)) {
                    session.fail(issue("SIM_CYCLE", String.valueOf(nodeIdRef),
                            compiled.nodeName(nodeIdRef) + ": 子流程递归成环", "去掉子流程互相引用后重试"));
                }
                try {
                    session.subflowNames.add(compiled.nodeName(nodeIdRef));
                    visitFlow(subFlowId, session, subflowStack);
                } finally {
                    session.subflowNames.remove(session.subflowNames.size() - 1);
                    subflowStack.remove(subFlowId);
                }
            }
            if (WorkflowNodeFunctionType.END.name().equals(node.path("functionType").asText())) {
                return;
            }
            if (WorkflowNodeFunctionType.AGGREGATE.name().equals(node.path("functionType").asText())
                    && !allIncomingArrived(flowModelId, compiled, nodeIdRef, session)) {
                return;
            }
            for (WorkflowDefinitionCompiler.Connection outgoing : compiled.outgoing(nodeIdRef)) {
                session.requireTime();
                session.arrived.add(edgeKey(flowModelId, outgoing));
                visitNode(flowModelId, outgoing.targetNodeIdRef(), compiled, session, onPath, subflowStack);
            }
        } finally {
            onPath.remove(pathKey);
        }
    }

    private boolean allIncomingArrived(long flowModelId, WorkflowDefinitionCompiler.CompiledWorkflow compiled,
                                         long nodeIdRef, Session session) {
        List<WorkflowDefinitionCompiler.Connection> incoming = compiled.incoming(nodeIdRef);
        if (incoming.isEmpty()) {
            return true;
        }
        for (WorkflowDefinitionCompiler.Connection incomingEdge : incoming) {
            if (!session.arrived.contains(edgeKey(flowModelId, incomingEdge))) {
                return false;
            }
        }
        return true;
    }

    private void probeDevice(long flowModelId, long nodeIdRef, JsonNode node,
                              WorkflowDefinitionCompiler.CompiledWorkflow compiled, Session session) {
        session.requireTime();
        long deviceModelId = node.path("deviceModelId").asLong(0);
        DeviceInstances instance = session.tempsByModel.get(deviceModelId);
        String label = compiled.nodeName(nodeIdRef);
        if (instance == null || instance.getId() == null) {
            session.fail(issue("SIM_FAILED", String.valueOf(nodeIdRef),
                    label + ": 没有对应模型的临时设备",
                    "按设备模型重新发起流程模拟"));
        }
        InterfaceConnectionCatalog.NodeToDeviceRoute route;
        try {
            route = connectionCatalog.nodeToDevice(compiled, connectionsFor(flowModelId, session),
                    nodeIdRef, deviceModelId);
        } catch (RuntimeException error) {
            session.fail(issue("SIM_FAILED", String.valueOf(nodeIdRef),
                    label + ": " + error.getMessage(),
                    "检查 NODE_TO_DEVICE 连接后重试"));
            return;
        }
        Long instanceId = instance.getId();
        adapterSimulator.clearLastError(instanceId);
        Map<String, Object> context = new HashMap<>();
        context.put("messageId", UUID.randomUUID().toString());
        context.put("capabilityName", node.path("capability").path("capabilityName").asText(""));
        context.put("parameters", capabilityParameters(node));
        Instant probeDeadline = Instant.now().plus(probeTimeout);
        List<?> emitted;
        try {
            emitted = stateMachineCommandPort.dispatchInputSignal(
                    instanceId, route.deviceInputInterfaceName(),
                    WorkflowControlSignal.WF_EXECUTE_START.name(), context);
        } catch (RuntimeException error) {
            session.fail(issue("SIM_FAILED", String.valueOf(nodeIdRef),
                    label + ": " + error.getMessage(),
                    "根据状态机错误修复设备模型或流程后重试"));
            return;
        }
        if (emitted == null || emitted.isEmpty()) {
            session.fail(issue("SIM_DEVICE_REJECTED", String.valueOf(nodeIdRef),
                    label + ": 状态机未接受 WF_EXECUTE_START",
                    "确认临时实例 CMD 为空闲后再试，或检查设备模型系统转移"));
        }
        String messageId = String.valueOf(context.get("messageId"));
        boolean seenSent = false;
        boolean seenRunning = false;
        Instant idleSince = null;
        while (true) {
            session.requireTime();
            if (!Instant.now().isBefore(probeDeadline)) {
                session.fail(issue("SIM_DEVICE_TIMEOUT", String.valueOf(nodeIdRef),
                        "设备探测超时：" + session.currentNode + "；已完成 " + session.completedProbes + " 个设备探测",
                        "检查该设备的模拟 Adapter 完成事件和状态机转移"));
            }
            String adapterError = adapterSimulator.takeLastError(instanceId);
            if (adapterError != null) {
                session.fail(issueFromAdapter(nodeIdRef, label, adapterError));
            }
            String probeResult = adapterSimulator.takeProbeResult(instanceId, messageId);
            if ("COMPLETED".equals(probeResult)) {
                session.completedProbes++;
                return;
            }
            if ("FAILED".equals(probeResult) || "ABORTED".equals(probeResult)) {
                session.fail(issue("SIM_DEVICE_FAILED", String.valueOf(nodeIdRef),
                        session.currentNode + ": 设备指令以 " + probeResult + " 结束",
                        "检查 Adapter 解析配置中的完成事件与状态机转移"));
            }
            String cmd = currentCmdState(instanceId);
            if ("FAILED".equals(cmd) || "ABORTED".equals(cmd)) {
                session.fail(issue("SIM_DEVICE_FAILED", String.valueOf(nodeIdRef),
                        session.currentNode + ": 设备指令以 " + cmd + " 结束",
                        "检查 Adapter 解析配置中的完成事件与状态机转移"));
            }
            if ("COMPLETED".equals(cmd)) {
                session.completedProbes++;
                return;
            }
            if ("SENT".equals(cmd)) {
                seenSent = true;
            }
            if ("RUNNING".equals(cmd)) {
                seenRunning = true;
            }
            if ("IDLE".equals(cmd) && (seenSent || seenRunning)) {
                if (idleSince == null) idleSince = Instant.now();
                if (!Instant.now().isBefore(idleSince.plus(RESET_RESULT_GRACE))) {
                    session.fail(issue("SIM_DEVICE_FAILED", String.valueOf(nodeIdRef),
                            session.currentNode + ": SENT/RUNNING 后回到 IDLE，但未收到本次指令的 COMPLETED 结果",
                            "检查 Adapter 完成事件、messageId 和状态机转移；SENT 看门狗复位不算完成"));
                }
            } else {
                idleSince = null;
            }
            sleepPoll();
        }
    }

    private WorkflowDefinitionCompiler.CompiledWorkflow compiledOf(long flowModelId, Session session) {
        if (session.rootCompiled != null && flowModelId == session.rootFlowModelId) {
            return session.rootCompiled;
        }
        return workflowService.compileDefinition(flowModelId);
    }

    private JsonNode connectionsFor(long flowModelId, Session session) {
        if (session.rootCompiled != null && flowModelId == session.rootFlowModelId) {
            return session.rootConnections;
        }
        var definition = workflowService.getDefinition(flowModelId);
        return definition == null ? null : definition.getInterfaceConnections();
    }

    private String currentCmdState(Long instanceId) {
        DeviceTwinStates twin = twinStateService.getByInstanceId(instanceId);
        return twin == null || twin.getCurrentCmdState() == null ? "" : twin.getCurrentCmdState();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capabilityParameters(JsonNode node) {
        JsonNode parameters = node.path("capability").path("capabilityParameters");
        if (parameters == null || !parameters.isObject()) {
            return Map.of();
        }
        return JsonNodeSupport.MAPPER.convertValue(parameters, Map.class);
    }

    private void sleepPoll() {
        try {
            Thread.sleep(PROBE_POLL_MS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new WalkStopped(issue("SIM_WALL_TIMEOUT", "", "流程模拟被中断",
                    "重新发起流程模拟"));
        }
    }

    private static String edgeKey(long flowModelId, WorkflowDefinitionCompiler.Connection connection) {
        return flowModelId + ":" + connection.sourceNodeIdRef() + ":" + connection.sourceInterface()
                + "->" + connection.targetNodeIdRef() + ":" + connection.targetInterface();
    }

    private List<List<String>> enumerateRoutes(long flowModelId, Session session) {
        WorkflowDefinitionCompiler.CompiledWorkflow compiled = compiledOf(flowModelId, session);
        List<List<String>> routes = new ArrayList<>();
        enumerateNode(compiled, compiled.startNodeIdRef(), new ArrayList<>(), new HashSet<>(), routes);
        return List.copyOf(routes);
    }

    private void enumerateNode(WorkflowDefinitionCompiler.CompiledWorkflow compiled, long nodeIdRef,
                                 List<String> current, Set<Long> onPath, List<List<String>> routes) {
        if (!onPath.add(nodeIdRef)) {
            return;
        }
        current.add(compiled.nodeName(nodeIdRef));
        JsonNode node = compiled.nodes().get(nodeIdRef);
        if (node != null && WorkflowNodeFunctionType.END.name().equals(node.path("functionType").asText())) {
            routes.add(List.copyOf(current));
        } else {
            for (WorkflowDefinitionCompiler.Connection outgoing : compiled.outgoing(nodeIdRef)) {
                enumerateNode(compiled, outgoing.targetNodeIdRef(), current, onPath, routes);
            }
        }
        current.remove(current.size() - 1);
        onPath.remove(nodeIdRef);
    }

    private static WorkflowIssue issue(String code, String nodeId, String message, String suggestion) {
        return new WorkflowIssue(code, "SIMULATION", "nodes[" + nodeId + "]", "FLOW_NODE",
                nodeId, true, message, suggestion);
    }

    private static WorkflowIssue issueFromAdapter(long nodeIdRef, String nodeName, String reason) {
        String code = "SIM_NO_ADAPTER_EVENT";
        String message = reason;
        int colon = reason.indexOf(':');
        if (colon > 0 && reason.substring(0, colon).startsWith("SIM_")) {
            code = reason.substring(0, colon);
            message = reason.substring(colon + 1);
        }
        return issue(code, String.valueOf(nodeIdRef), nodeName + ": " + message,
                "在设备模型 STATE_TRANSITIONS 中为 SENT→RUNNING 与 RUNNING→COMPLETED 各声明唯一 Adapter 事件，且事件名须出现在 parsed_config.cmdEvents");
    }

    public record WalkResult(boolean walkable, List<String> pathTaken, List<List<String>> paths,
                             List<WorkflowIssue> issues) {
        public WalkResult(boolean walkable, List<String> pathTaken, List<WorkflowIssue> issues) {
            this(walkable, pathTaken, List.of(), issues);
        }
    }

    private static final class Session {
        private final Map<Long, DeviceInstances> tempsByModel;
        private final Instant deadline;
        private final long rootFlowModelId;
        private final WorkflowDefinitionCompiler.CompiledWorkflow rootCompiled;
        private final JsonNode rootConnections;
        private final List<String> pathTaken = new ArrayList<>();
        private final Set<String> pathSeen = new LinkedHashSet<>();
        private final List<WorkflowIssue> issues = new ArrayList<>();
        private final Set<String> arrived = new HashSet<>();
        private final List<String> subflowNames = new ArrayList<>();
        private String currentNode = "起点";
        private int completedProbes;

        private Session(Map<Long, DeviceInstances> tempsByModel, Instant deadline, long rootFlowModelId,
                        WorkflowDefinitionCompiler.CompiledWorkflow rootCompiled, JsonNode rootConnections) {
            this.tempsByModel = tempsByModel;
            this.deadline = deadline;
            this.rootFlowModelId = rootFlowModelId;
            this.rootCompiled = rootCompiled;
            this.rootConnections = rootConnections;
        }

        private void requireTime() {
            if (deadline != null && !Instant.now().isBefore(deadline)) {
                fail(issue("SIM_WALL_TIMEOUT", "", "仿真总预算耗尽，当前节点：" + currentNode
                        + "；已完成 " + completedProbes + " 个设备探测",
                        "检查当前节点的模拟执行结果，或调整流程中的设备探测数量"));
            }
        }

        private void fail(WorkflowIssue issue) {
            issues.add(issue);
            throw new WalkStopped(issue);
        }
    }

    private static final class WalkStopped extends RuntimeException {
        private WalkStopped(WorkflowIssue issue) {
            super(issue == null ? "walk stopped" : issue.message());
        }
    }
}
