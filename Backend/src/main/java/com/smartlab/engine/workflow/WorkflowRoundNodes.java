package com.smartlab.engine.workflow;

import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.workflow.FlowNodeService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Set;

/** 本轮步骤与节点定义的索引。对象本身不保留到下一轮；跨轮保留由任务调度内存负责。 */
public final class WorkflowRoundNodes {
    private static final Set<String> ACTIVE = Set.of("PENDING", "RUNNING", "TERMINATING");
    private final List<TaskStep> steps;
    private final Map<Long, FlowNode> nodesByStepId;
    private final Map<NodeRef, FlowNode> nodesByRef = new LinkedHashMap<>();
    private final Map<StepScope, TaskStep> peers = new LinkedHashMap<>();
    private final Map<NodeRef, List<TaskStep>> activeTargets = new LinkedHashMap<>();
    private final List<TaskStep> activeSteps = new ArrayList<>();
    private final Set<Long> activeModels = new java.util.LinkedHashSet<>();

    private WorkflowRoundNodes(List<TaskStep> steps, Map<Long, FlowNode> nodesById) {
        this.steps = List.copyOf(steps);
        this.nodesByStepId = new LinkedHashMap<>();
        for (TaskStep step : steps) {
            FlowNode node = nodesById.get(step.getFlowNodeId());
            if (node == null) continue;
            nodesByStepId.put(step.getId(), node);
            nodesByRef.put(new NodeRef(node.getFlowModelId(), node.getNodeIdRef()), node);
            peers.putIfAbsent(scope(node.getFlowModelId(), step.getNodeIdRef(), step), step);
            if (step.getNodeStatus() != null && ACTIVE.contains(step.getNodeStatus())) {
                activeSteps.add(step);
                activeModels.add(node.getFlowModelId());
                activeTargets.computeIfAbsent(new NodeRef(node.getFlowModelId(), step.getNodeIdRef()),
                        ignored -> new ArrayList<>()).add(step);
            }
        }
    }

    public static WorkflowRoundNodes load(List<TaskStep> steps, FlowNodeService service) {
        List<TaskStep> current = steps == null ? List.of() : steps;
        List<Long> ids = current.stream().map(TaskStep::getFlowNodeId).filter(Objects::nonNull).distinct().toList();
        Map<Long, FlowNode> nodes = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            for (FlowNode node : service.listByIds(ids)) nodes.put(node.getId(), node);
        }
        return new WorkflowRoundNodes(current, nodes);
    }

    /** 使用已经装入内存的节点定义，不再访问数据库。 */
    public static WorkflowRoundNodes of(List<TaskStep> steps, Map<Long, FlowNode> nodesById) {
        List<TaskStep> current = steps == null ? List.of() : steps;
        Map<Long, FlowNode> nodes = nodesById == null ? Map.of() : nodesById;
        return new WorkflowRoundNodes(current, nodes);
    }

    public List<TaskStep> steps() { return steps; }
    public Map<Long, FlowNode> nodesByStepId() { return java.util.Collections.unmodifiableMap(nodesByStepId); }
    public FlowNode node(TaskStep step) { return nodesByStepId.get(step.getId()); }
    public FlowNode node(Long modelId, Long nodeRef) { return nodesByRef.get(new NodeRef(modelId, nodeRef)); }
    public List<TaskStep> activeSteps() { return java.util.Collections.unmodifiableList(activeSteps); }
    public Set<Long> activeModels() { return java.util.Collections.unmodifiableSet(activeModels); }
    public List<TaskStep> activeTargets(Long modelId, Long nodeRef) {
        return java.util.Collections.unmodifiableList(activeTargets.getOrDefault(new NodeRef(modelId, nodeRef), List.of()));
    }
    public TaskStep peer(Long modelId, Long nodeRef, TaskStep target) {
        return peers.get(scope(modelId, nodeRef, target));
    }
    private static StepScope scope(Long modelId, Long nodeRef, TaskStep step) {
        return new StepScope(modelId, nodeRef, step.getParentStepId(), step.getStepDepth() == null ? 0 : step.getStepDepth());
    }
    private record NodeRef(Long modelId, Long nodeRef) { }
    private record StepScope(Long modelId, Long nodeRef, Long parentStepId, int depth) { }
}
