package com.smartlab.agent.operator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.agent.api.WorkflowGenerateResponse;
import com.smartlab.agent.loop.AgentLoop;
import com.smartlab.engine.statemachine.StateMachineEngine;
import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.workflow.TaskCreateRequest;
import com.smartlab.management.dto.workflow.TaskDeviceBindingRequest;
import com.smartlab.management.dto.workflow.TaskPreflightResponse;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.dto.workflow.WorkflowPreparationResponse;
import com.smartlab.management.dto.resource.device.VirtualMachineView;
import com.smartlab.management.dto.workflow.WorkflowSimulateRequest;
import com.smartlab.management.dto.workflow.WorkflowSimulationReport;
import com.smartlab.management.entity.resource.device.DeviceInstanceKind;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskExecutionKind;
import com.smartlab.management.service.db.resource.adapter.VirtualLeaseService;
import com.smartlab.management.service.db.user.CurrentUserPermissionService;
import com.smartlab.management.service.db.workflow.TaskService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import com.smartlab.management.service.db.workflow.WorkflowSimulationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 设备操作助手接口。
 *
 * <p>三个动作严格分开：
 * <ul>
 *   <li>{@code chat} 只对话，不改设备；</li>
 *   <li>{@code confirm} 才真正下发；</li>
 *   <li>{@code cancel} 丢弃待确认动作。</li>
 * </ul>
 *
 * <p>confirm 会<b>重新跑一遍预检</b>而不是相信预演时的快照。预演和点确认之间可能隔了几十秒，
 * 设备可能已经掉线或被人占用，这个窗口必须重新校验。
 */
@RestController
@RequestMapping("/api/agent/operator")
public class OperatorController {

    public record ChatRequest(String sessionId, String message) {
    }

    public record ConfirmRequest(String sessionId, String token, String mode) {
    }

    public record CancelRequest(String sessionId) {
    }

    public record ChatReply(String sessionId, String message, ConfirmCard card, String token,
                            long pendingTtlSeconds, List<String> trace) {
    }

    public record ConfirmReply(String status, String message, Object detail) {
    }

    private final AgentLoop loop;
    private final OperatorSessionStore store;
    private final OperatorDeviceCatalog catalog;
    private final TaskPreparationService preparation;
    private final StateMachineEngine stateMachineEngine;
    private final TaskService taskService;
    private final WorkflowService workflowService;
    private final CurrentUserPermissionService permissionService;
    private final VirtualLeaseService virtualLeaseService;
    private final WorkflowSimulationService simulationService;

    public OperatorController(AgentLoop loop, OperatorSessionStore store, OperatorDeviceCatalog catalog,
                              TaskPreparationService preparation, StateMachineEngine stateMachineEngine,
                              TaskService taskService, WorkflowService workflowService,
                              CurrentUserPermissionService permissionService,
                              VirtualLeaseService virtualLeaseService,
                              WorkflowSimulationService simulationService) {
        this.loop = loop;
        this.store = store;
        this.catalog = catalog;
        this.preparation = preparation;
        this.stateMachineEngine = stateMachineEngine;
        this.taskService = taskService;
        this.workflowService = workflowService;
        this.permissionService = permissionService;
        this.virtualLeaseService = virtualLeaseService;
        this.simulationService = simulationService;
    }

    @PostMapping("/chat")
    public ApiResponse<ChatReply> chat(@RequestBody ChatRequest request, Authentication authentication) {
        try {
            if (request == null || request.message() == null || request.message().isBlank()) {
                return ApiResponse.fail("请输入要执行的操作");
            }
            OperatorSession session = store.getOrCreate(request.sessionId());
            OperatorContext context = new OperatorContext(session, null,
                    authentication == null ? null : authentication.getName());
            WorkflowGenerateResponse reply = loop.chat(session, context, request.message(), log -> {});
            return ApiResponse.ok(new ChatReply(reply.sessionId(), reply.summary(), reply.card(),
                    reply.token(), store.pendingTtlSeconds(), reply.trace()));
        } catch (IllegalArgumentException | IllegalStateException error) {
            return ApiResponse.fail(error.getMessage());
        } catch (RuntimeException error) {
            return ApiResponse.fail(error.getMessage() == null ? "处理失败" : error.getMessage());
        }
    }

    @PostMapping("/confirm")
    public ApiResponse<ConfirmReply> confirm(@RequestBody ConfirmRequest request, Authentication authentication) {
        try {
            if (request == null || request.sessionId() == null || request.sessionId().isBlank()) {
                return ApiResponse.fail("会话不存在或已过期");
            }
            OperatorSession session = store.find(request.sessionId());
            if (session == null) {
                return ApiResponse.fail("会话已过期，请重新说一遍要做什么");
            }
            PendingAction pending = session.takePending(request.token());
            if (pending == null) {
                return ApiResponse.fail("没有待确认的操作（可能已确认过或已过期），请重新准备");
            }
            boolean simulation = "SIMULATION".equalsIgnoreCase(request.mode());
            if (pending instanceof PendingAction.Device device) {
                return ApiResponse.ok(simulation
                        ? executeDeviceSimulation(session, device)
                        : executeDevice(device, authentication));
            }
            if (pending instanceof PendingAction.Task task) {
                return ApiResponse.ok(executeTask(task, authentication));
            }
            if (pending instanceof PendingAction.TaskStart start) {
                return ApiResponse.ok(simulation
                        ? executeTaskStartSimulation(session, start)
                        : executeTaskStart(start));
            }
            if (pending instanceof PendingAction.FlowDraft draft) {
                return ApiResponse.ok(executeFlowDraft(draft));
            }
            return ApiResponse.fail("未知的待确认操作类型");
        } catch (IllegalArgumentException | IllegalStateException error) {
            return ApiResponse.fail(error.getMessage());
        } catch (RuntimeException error) {
            return ApiResponse.fail(error.getMessage() == null ? "执行失败" : error.getMessage());
        }
    }

    @PostMapping("/cancel")
    public ApiResponse<ConfirmReply> cancel(@RequestBody CancelRequest request) {
        OperatorSession session = request == null ? null : store.find(request.sessionId());
        if (session == null) {
            return ApiResponse.ok(new ConfirmReply("NOTHING_TO_CANCEL", "当前没有待确认的操作", null));
        }
        session.clearPending();
        return ApiResponse.ok(new ConfirmReply("CANCELLED", "已取消，设备没有任何动作", null));
    }

    private ConfirmReply executeDevice(PendingAction.Device device, Authentication authentication) {
        permissionService.require("device_instance", "control");

        ActionGuard.Snapshot snapshot = catalog.snapshot(device.instanceId());
        InstanceResolver.Candidate candidate = catalog.resolver().byInstanceId(device.instanceId());
        DeviceModels model = candidate == null ? null : catalog.modelOrNull(candidate.modelId());
        // 必须取模型里的真实能力定义：中止类能力要能在"设备正忙"时照常下发
        CapabilityBinder.CapabilityView capability = model == null
                ? null : CapabilityBinder.findCapability(model, device.capabilityName());
        if (capability == null) {
            capability = new CapabilityBinder.CapabilityView(device.capabilityName(),
                    device.capabilityDisplayName(), "", false, null, List.of());
        }
        List<CapabilityBinder.BoundParameter> bound = new ArrayList<>();
        device.parameters().forEach((key, value) ->
                bound.add(new CapabilityBinder.BoundParameter(key, key, value, null, CapabilityBinder.Source.USER)));

        ActionGuard.Result guard = ActionGuard.check(snapshot, capability, bound, model);
        if (!guard.pass()) {
            List<String> reasons = new ArrayList<>();
            guard.blocking().forEach(issue -> reasons.add(issue.text()));
            return new ConfirmReply("BLOCKED", "执行前复检未通过：" + String.join("；", reasons), reasons);
        }

        ObjectNode result = stateMachineEngine.handleManualControl(
                device.instanceId(), device.capabilityName(), device.parameters());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("instanceId", device.instanceId());
        detail.put("instanceName", device.instanceName());
        detail.put("capabilityName", device.capabilityName());
        detail.put("parameters", device.parameters());
        detail.put("result", result);
        return new ConfirmReply("EXECUTED",
                device.instanceName() + " 的「" + device.capabilityDisplayName() + "」指令已下发", detail);
    }

    /**
     * 单设备仿真执行：真下发，但只发给这台设备的点动虚拟机。
     *
     * <p>步骤是「申请虚拟点租约 → 对虚拟实例下发同一能力同一参数 → 立刻释放租约」，
     * 真机一个字节都不发。跑完立刻把"正式执行"卡片重新挂到会话上，
     * 用户点确认才是真的接线。
     */
    private ConfirmReply executeDeviceSimulation(OperatorSession session, PendingAction.Device device) {
        permissionService.require("device_instance", "control");

        ActionGuard.Snapshot snapshot = catalog.snapshot(device.instanceId());
        if (!DeviceInstanceKind.PHYSICAL.equals(DeviceInstanceKind.normalize(snapshot.instanceKind()))) {
            return new ConfirmReply("BLOCKED", "只有物理设备可以做仿真执行：当前实例已经是虚拟设备",
                    List.of("当前实例种类: " + snapshot.instanceKind()));
        }
        InstanceResolver.Candidate candidate = catalog.resolver().byInstanceId(device.instanceId());
        DeviceModels model = candidate == null ? null : catalog.modelOrNull(candidate.modelId());
        CapabilityBinder.CapabilityView capability = model == null
                ? null : CapabilityBinder.findCapability(model, device.capabilityName());
        if (capability == null) {
            capability = new CapabilityBinder.CapabilityView(device.capabilityName(),
                    device.capabilityDisplayName(), "", false, null, List.of());
        }
        List<CapabilityBinder.BoundParameter> bound = new ArrayList<>();
        device.parameters().forEach((key, value) ->
                bound.add(new CapabilityBinder.BoundParameter(key, key, value, null, CapabilityBinder.Source.USER)));

        ActionGuard.Result guard = ActionGuard.check(snapshot, capability, bound, model);
        if (!guard.pass()) {
            List<String> reasons = new ArrayList<>();
            guard.blocking().forEach(issue -> reasons.add(issue.text()));
            return new ConfirmReply("BLOCKED", "仿真执行前复检未通过：" + String.join("；", reasons), reasons);
        }

        VirtualMachineView virtual;
        try {
            virtual = virtualLeaseService.applyPokeVirtual(device.instanceId());
        } catch (RuntimeException error) {
            return new ConfirmReply("BLOCKED", "仿真执行没能拿到虚拟点位：" + messageOf(error),
                    List.of(messageOf(error)));
        }
        try {
            stateMachineEngine.handleManualControl(virtual.virtualInstanceId(),
                    device.capabilityName(), device.parameters());
        } catch (RuntimeException error) {
            return new ConfirmReply("BLOCKED", "仿真下发失败：" + messageOf(error), List.of(messageOf(error)));
        } finally {
            try {
                virtualLeaseService.releasePokeVirtual(virtual.leaseId());
            } catch (RuntimeException ignored) {
                // 释放失败就让它自然过期，不影响这次仿真的结论
            }
        }

        ConfirmCard followUp = device.card().asFollowUp(
                device.card().title(),
                "仿真执行已通过（虚拟点位 " + virtual.virtualDevicePoint() + "，真机没有动作）。"
                        + "确认后才会把这条指令真正下发给 " + device.instanceName() + "。",
                List.of(ConfirmCard.Row.of("仿真结果", "通过 · " + virtual.virtualDevicePoint())),
                List.of(new ConfirmCard.Issue(ActionGuard.WARNING, "接下来这一步会驱动真实设备")));
        PendingAction.Device next = new PendingAction.Device(
                UUID.randomUUID().toString(), Instant.now(), device.instanceId(), device.instanceName(),
                device.capabilityName(), device.capabilityDisplayName(), device.parameters(), followUp);
        session.setPending(next);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("simulated", true);
        detail.put("instanceId", device.instanceId());
        detail.put("instanceName", device.instanceName());
        detail.put("virtualInstanceId", virtual.virtualInstanceId());
        detail.put("virtualDevicePoint", virtual.virtualDevicePoint());
        detail.put("followUpCard", followUp.toMap());
        detail.put("followUpToken", next.token());
        return new ConfirmReply("EXECUTED",
                device.instanceName() + " 的「" + device.capabilityDisplayName() + "」仿真执行通过，真机没有动作",
                detail);
    }

    /**
     * 任务仿真执行：只做流程推演，任务状态一动不动。
     *
     * <p>这里刻意不走 {@code taskService.start(SIMULATION)}：那条路会把任务推到 RUNNING，
     * "待开始"被吃掉，第二张卡就没有原任务可启动了。推演只编译流程、走结构、
     * 查设备是否可达，任务仍然是 PENDING，确认后直接启动它本人。
     */
    private ConfirmReply executeTaskStartSimulation(OperatorSession session, PendingAction.TaskStart action) {
        if (action.flowModelId() <= 0) {
            return new ConfirmReply("BLOCKED", "这条任务没有关联流程，无法仿真执行",
                    List.of("缺少 flowModelId"));
        }
        WorkflowSimulationReport report;
        try {
            report = simulationService.simulate(new WorkflowSimulateRequest(action.flowModelId(), null));
        } catch (RuntimeException error) {
            return new ConfirmReply("BLOCKED", "仿真执行失败：" + messageOf(error), List.of(messageOf(error)));
        }

        List<String> blockers = new ArrayList<>();
        List<ConfirmCard.Issue> warnings = new ArrayList<>();
        if (report != null && report.issues() != null) {
            for (WorkflowIssue issue : report.issues()) {
                if (issue.blocking()) {
                    blockers.add(issue.message());
                } else {
                    warnings.add(new ConfirmCard.Issue(ActionGuard.WARNING, issue.message()));
                }
            }
        }
        boolean walkable = report != null && report.walkable() && blockers.isEmpty();
        if (!walkable) {
            String reason = blockers.isEmpty() ? "流程走不通" : String.join("；", blockers);
            return new ConfirmReply("BLOCKED", "仿真执行未通过：" + reason, blockers);
        }

        List<String> taken = report.pathTaken() == null ? List.of() : report.pathTaken();
        String path = taken.isEmpty() ? "—" : String.join(" → ", taken);
        List<ConfirmCard.Issue> followWarnings = new ArrayList<>(warnings);
        followWarnings.add(new ConfirmCard.Issue(ActionGuard.WARNING, "接下来这一步会驱动真实设备"));
        ConfirmCard followUp = action.card().asFollowUp(
                "确认启动任务",
                "仿真执行已通过（任务仍是待开始，没有任何设备动作）。确认后才会真正启动这条任务。",
                List.of(ConfirmCard.Row.of("仿真结果", "通过 · 走通 " + taken.size() + " 个节点"),
                        ConfirmCard.Row.of("推演路径", path)),
                followWarnings);
        PendingAction.TaskStart next = new PendingAction.TaskStart(
                UUID.randomUUID().toString(), Instant.now(), action.taskId(), action.taskName(),
                action.flowModelId(), action.flowName(), action.statusBefore(),
                TaskExecutionKind.PRODUCTION, followUp);
        session.setPending(next);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("simulated", true);
        detail.put("taskId", action.taskId());
        detail.put("taskName", action.taskName());
        detail.put("taskStatus", action.statusBefore());
        detail.put("walkable", true);
        detail.put("pathTaken", taken);
        detail.put("followUpCard", followUp.toMap());
        detail.put("followUpToken", next.token());
        return new ConfirmReply("EXECUTED",
                "任务「" + action.taskName() + "」仿真执行通过，任务仍未启动", detail);
    }

    private static String messageOf(RuntimeException error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private ConfirmReply executeTask(PendingAction.Task task, Authentication authentication) {
        TaskCreateRequest request = new TaskCreateRequest();
        request.setFlowModelId(task.flowModelId());
        request.setTaskName(task.taskName());
        List<TaskDeviceBindingRequest> bindings = new ArrayList<>();
        for (PendingAction.SlotBinding binding : task.bindings()) {
            bindings.add(new TaskDeviceBindingRequest(binding.slotId(), binding.deviceInstanceId()));
        }
        request.setDeviceBindings(bindings);

        TaskPreflightResponse preflight = preparation.preflight(request);
        List<String> blockers = new ArrayList<>();
        if (preflight != null && preflight.issues() != null) {
            for (WorkflowIssue issue : preflight.issues()) {
                if (issue.blocking()) blockers.add(issue.message());
            }
        }
        if (!blockers.isEmpty()) {
            return new ConfirmReply("BLOCKED", "创建前复检未通过：" + String.join("；", blockers), blockers);
        }

        Task created = taskService.create(request);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("taskId", created == null ? null : created.getId());
        detail.put("taskName", task.taskName());
        detail.put("flowModelId", task.flowModelId());
        detail.put("taskStatus", created == null ? null : created.getTaskStatus());
        detail.put("bindings", task.bindings().size());
        return new ConfirmReply("EXECUTED",
                "任务「" + task.taskName() + "」已创建，状态为待开始，需要时再启动", detail);
    }

    /**
     * 启动已存在的任务。
     *
     * <p>不做"预检快照复用"——{@code taskService.start} 自己会重新查一遍流程可执行性、
     * 状态、租约，失败就以异常回来，这里原样转成 BLOCKED。
     */
    private ConfirmReply executeTaskStart(PendingAction.TaskStart action) {
        try {
            Task started = taskService.start(action.taskId(), true, action.executionKind());
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("taskId", action.taskId());
            detail.put("taskName", action.taskName());
            detail.put("taskStatus", started == null ? "RUNNING" : started.getTaskStatus());
            detail.put("executionKind", action.executionKind());
            detail.put("flowModelId", action.flowModelId());
            return new ConfirmReply("EXECUTED",
                    "任务「" + action.taskName() + "」已启动，当前状态 "
                            + (started == null ? "RUNNING" : started.getTaskStatus()), detail);
        } catch (RuntimeException error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            return new ConfirmReply("BLOCKED", "启动前复检未通过：" + message, List.of(message));
        }
    }

    /**
     * 保存一份待确认的流程草稿。
     *
     * <p>这里是流程设计唯一的落库口：对话时模型调 {@code save_draft} 只会生成卡片，
     * 用户点了确认才走到这里。保存前不信任卡片上的节点数，重新编译一遍——
     * 卡片是给人看的，真正入库的是 {@code saveDraft} 规范化之后的结果。
     */
    private ConfirmReply executeFlowDraft(PendingAction.FlowDraft draft) {
        try {
            WorkflowPreparationResponse saved = workflowService.saveDraft(draft.document());
            Long flowModelId = saved == null || saved.definition() == null
                    ? null : saved.definition().getId();
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("flowModelId", flowModelId);
            detail.put("flowName", draft.flowName());
            detail.put("nodeCount", draft.nodeCount());
            detail.put("status", saved == null || saved.definition() == null
                    ? "DRAFT" : saved.definition().getStatus());
            String name = draft.flowName() == null || draft.flowName().isBlank() ? "未命名" : draft.flowName();
            return new ConfirmReply("EXECUTED", "流程草稿「" + name + "」已保存", detail);
        } catch (RuntimeException error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            return new ConfirmReply("BLOCKED", "保存前复检未通过：" + message, List.of(message));
        }
    }

    /** 供调试：当前会话里有没有待确认动作。 */
    @PostMapping("/peek")
    public ApiResponse<Map<String, Object>> peek(@RequestBody CancelRequest request) {
        OperatorSession session = request == null ? null : store.find(request.sessionId());
        Map<String, Object> map = new LinkedHashMap<>();
        if (session == null) {
            map.put("exists", false);
            return ApiResponse.ok(map);
        }
        PendingAction pending = session.pending();
        map.put("exists", pending != null);
        if (pending != null) {
            map.put("kind", pending instanceof PendingAction.Device ? ConfirmCard.KIND_DEVICE_ACTION
                    : pending instanceof PendingAction.Task ? ConfirmCard.KIND_CREATE_TASK
                    : pending instanceof PendingAction.FlowDraft ? ConfirmCard.KIND_FLOW_DRAFT
                    : ConfirmCard.KIND_START_TASK);
            map.put("expired", !store.pendingAlive(session));
            map.put("card", pending.card());
        }
        return ApiResponse.ok(map);
    }
}
