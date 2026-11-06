package com.smartlab.agent.operator;

import com.smartlab.management.dto.workflow.DeviceBindingRequirement;
import com.smartlab.management.dto.workflow.TaskCreateRequest;
import com.smartlab.management.dto.workflow.TaskDeviceBindingRequest;
import com.smartlab.management.dto.workflow.TaskPreflightRequest;
import com.smartlab.management.dto.workflow.TaskPreflightResponse;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.workflow.FlowModels;
import com.smartlab.management.service.db.workflow.TaskService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import com.smartlab.management.service.db.workflow.WorkflowTaskResourceService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 建任务的准备工作：认流程 → 算槽位绑定 → 跑预检。
 *
 * <p>为什么要按"型号分组"来问，而不是逐个槽位问：
 * 一个流程可能有 17 个设备节点，逐个问用户要问答 17 次。按型号归组后，
 * 通常只有两三种型号，用户回答两三次就能定完。
 *
 * <p>绑定来源必须可追溯。用户明确指定的记为 USER，服务端按"该型号只有一个实例"自动推出来的
 * 记为 AGENT_INFERRED，确认页上要分开显示——推错的那几行必须让人重点核对。
 */
@Service
public class TaskPreparationService {

    public record FlowMatch(InstanceResolver.Status status, FlowModels resolved, List<FlowModels> candidates, String hint) {
    }

    /** 需要用户指定的型号分组。 */
    public record ModelQuestion(long modelId, String modelName, List<String> slotIds, List<String> nodeNames,
                                List<InstanceResolver.Candidate> candidates) {
    }

    public record BindingPlan(List<PendingAction.SlotBinding> bindings, List<ModelQuestion> questions,
                              List<String> problems) {
        public boolean ready() {
            return questions.isEmpty() && problems.isEmpty();
        }
    }

    private final WorkflowService workflowService;
    private final WorkflowTaskResourceService resourceService;
    private final TaskService taskService;
    private final OperatorDeviceCatalog catalog;

    public TaskPreparationService(WorkflowService workflowService,
                                  WorkflowTaskResourceService resourceService,
                                  TaskService taskService,
                                  OperatorDeviceCatalog catalog) {
        this.workflowService = workflowService;
        this.resourceService = resourceService;
        this.taskService = taskService;
        this.catalog = catalog;
    }

    public List<FlowModels> flows() {
        return workflowService.list();
    }

    /** 按名字或 id 找流程。名字重名时必须返回候选，不能挑第一个。 */
    public FlowMatch matchFlow(String query) {
        List<FlowModels> all = workflowService.list();
        if (query == null || query.isBlank()) {
            return new FlowMatch(InstanceResolver.Status.NOT_FOUND, null, all, "请告诉我要用哪个流程");
        }
        String trimmed = query.trim();
        if (trimmed.chars().allMatch(Character::isDigit)) {
            long id = Long.parseLong(trimmed);
            for (FlowModels flow : all) {
                if (flow.getId() != null && flow.getId() == id) {
                    return new FlowMatch(InstanceResolver.Status.FOUND, flow, List.of(flow), null);
                }
            }
            return new FlowMatch(InstanceResolver.Status.NOT_FOUND, null, all, "没有 id 为 " + id + " 的流程");
        }
        String normalized = InstanceResolver.normalize(trimmed);
        List<FlowModels> exact = new ArrayList<>();
        for (FlowModels flow : all) {
            if (normalized.equals(InstanceResolver.normalize(flow.getFlowName()))) exact.add(flow);
        }
        if (exact.size() == 1) return new FlowMatch(InstanceResolver.Status.FOUND, exact.get(0), exact, null);
        if (exact.size() > 1) {
            return new FlowMatch(InstanceResolver.Status.AMBIGUOUS, null, exact, "有多个同名流程，需要用户指定");
        }
        List<FlowModels> fuzzy = new ArrayList<>();
        for (FlowModels flow : all) {
            String name = InstanceResolver.normalize(flow.getFlowName());
            if (name.contains(normalized) || normalized.contains(name)) fuzzy.add(flow);
        }
        if (fuzzy.size() == 1) return new FlowMatch(InstanceResolver.Status.FOUND, fuzzy.get(0), fuzzy, null);
        if (fuzzy.size() > 1) {
            return new FlowMatch(InstanceResolver.Status.AMBIGUOUS, null, fuzzy, "有多个相似的流程，需要用户指定");
        }
        return new FlowMatch(InstanceResolver.Status.NOT_FOUND, null, all, "没有找到这个流程");
    }

    public List<DeviceBindingRequirement> requirements(long flowModelId) {
        return resourceService.requirements(flowModelId).bindings();
    }

    /**
     * 计算绑定方案。
     *
     * @param assignments 用户明确给出的绑定：键可以是型号名，也可以是 slotId；值是设备名
     */
    public BindingPlan plan(long flowModelId, Map<String, String> assignments) {
        List<DeviceBindingRequirement> requirements = requirements(flowModelId);
        Map<Long, List<DeviceInstances>> byModel = catalog.instancesByModel();
        InstanceResolver resolver = catalog.resolver();

        Map<String, String> normalizedAssignments = new LinkedHashMap<>();
        if (assignments != null) {
            assignments.forEach((key, value) -> {
                if (key == null || value == null) return;
                normalizedAssignments.put(InstanceResolver.normalize(key), value);
            });
        }

        List<PendingAction.SlotBinding> bindings = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        Map<Long, ModelQuestion> pending = new LinkedHashMap<>();

        for (DeviceBindingRequirement requirement : requirements) {
            long modelId = requirement.deviceModelId();
            String source = null;
            Long chosen = null;

            // 1) 按 slotId 精确指定
            String bySlot = lookup(normalizedAssignments, requirement.slotId());
            // 2) 按型号名指定（整组生效）
            if (bySlot == null) bySlot = lookup(normalizedAssignments, catalog.modelName(modelId));
            // 3) 按节点名指定
            if (bySlot == null) bySlot = lookup(normalizedAssignments, requirement.nodeName());

            if (bySlot != null) {
                InstanceResolver.Resolution resolution = resolver.resolve(bySlot);
                if (resolution.found()) {
                    InstanceResolver.Candidate candidate = resolution.resolved();
                    if (candidate.modelId() == modelId) {
                        chosen = candidate.id();
                        source = "USER";
                    } else {
                        problems.add("节点「" + requirement.nodeName() + "」需要型号「" + catalog.modelName(modelId)
                                + "」，但指定的是「" + candidate.instanceName() + "」（型号 "
                                + catalog.modelName(candidate.modelId()) + "），型号不匹配");
                        continue;
                    }
                } else if (resolution.status() == InstanceResolver.Status.AMBIGUOUS) {
                    problems.add("「" + bySlot + "」命中多台设备，需要说清具体是哪一台");
                    continue;
                } else {
                    problems.add("没有找到设备「" + bySlot + "」");
                    continue;
                }
            }

            // 4) 型号下只有一个实例，直接绑
            if (chosen == null) {
                List<DeviceInstances> instances = byModel.getOrDefault(modelId, List.of());
                if (instances.size() == 1) {
                    chosen = instances.get(0).getId();
                    source = "AGENT_INFERRED";
                } else if (instances.isEmpty()) {
                    problems.add("型号「" + catalog.modelName(modelId) + "」没有任何设备实例，无法绑定");
                    continue;
                } else {
                    pending.computeIfAbsent(modelId, key -> new ModelQuestion(
                            modelId,
                            catalog.modelName(modelId),
                            new ArrayList<>(),
                            new ArrayList<>(),
                            instances.stream().map(item -> new InstanceResolver.Candidate(
                                    item.getId(), item.getInstanceName(), item.getBoundDevicePoint(),
                                    item.getBoundAdapterName(),
                                    item.getDeviceModelId() == null ? 0 : item.getDeviceModelId(),
                                    item.getLifecycleStatus(), item.getInstanceKind())).toList()
                    ));
                    pending.get(modelId).slotIds().add(requirement.slotId());
                    pending.get(modelId).nodeNames().add(requirement.nodeName());
                    continue;
                }
            }

            if (chosen != null) {
                InstanceResolver.Candidate candidate = resolver.byInstanceId(chosen);
                bindings.add(new PendingAction.SlotBinding(
                        requirement.slotId(),
                        requirement.nodeName(),
                        chosen,
                        candidate == null ? String.valueOf(chosen) : candidate.instanceName(),
                        catalog.modelName(modelId),
                        source
                ));
            }
        }

        return new BindingPlan(List.copyOf(bindings), List.copyOf(pending.values()), List.copyOf(problems));
    }

    private static String lookup(Map<String, String> assignments, String key) {
        if (key == null || assignments.isEmpty()) return null;
        return assignments.get(InstanceResolver.normalize(key));
    }

    /** 用计划结果拼一个创建请求（只用于预检和展示，不落库）。 */
    public TaskCreateRequest buildRequest(long flowModelId, String taskName, List<PendingAction.SlotBinding> bindings,
                                          Long creatorId) {
        TaskCreateRequest request = new TaskCreateRequest();
        request.setFlowModelId(flowModelId);
        request.setTaskName(taskName);
        request.setCreatorId(creatorId);
        List<TaskDeviceBindingRequest> deviceBindings = new ArrayList<>();
        for (PendingAction.SlotBinding binding : bindings) {
            deviceBindings.add(new TaskDeviceBindingRequest(binding.slotId(), binding.deviceInstanceId()));
        }
        request.setDeviceBindings(deviceBindings);
        return request;
    }

    public TaskPreflightResponse preflight(TaskCreateRequest request) {
        return taskService.preflight(TaskPreflightRequest.from(request));
    }

    /** 把预检问题压成可读文本，避免把整坨 JSON 丢给模型。 */
    public List<String> describeIssues(TaskPreflightResponse response) {
        if (response == null || response.issues() == null) return List.of();
        LinkedHashSet<String> lines = new LinkedHashSet<>();
        for (var issue : response.issues()) {
            String prefix = issue.blocking() ? "[阻断] " : "[提醒] ";
            String path = issue.path() == null || issue.path().isBlank() ? "" : "（" + issue.path() + "）";
            lines.add(prefix + issue.message() + path);
        }
        return List.copyOf(lines);
    }

    public static String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }
}
