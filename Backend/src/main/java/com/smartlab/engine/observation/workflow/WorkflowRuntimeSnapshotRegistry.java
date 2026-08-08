package com.smartlab.engine.observation.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.engine.observation.ObservableKey;
import com.smartlab.engine.observation.ObservationHistoryStore;
import com.smartlab.engine.observation.ObservationSnapshot;
import com.smartlab.engine.observation.ObservationStatus;
import com.smartlab.engine.observation.SnapshotOrigin;
import com.smartlab.engine.observation.event.ObservableChangedEvent;
import com.smartlab.global.contract.ObservableObjectType;
import com.smartlab.global.event.TaskLifecycleObservationEvent;
import com.smartlab.global.event.WorkflowNodeObservationEvent;
import com.smartlab.management.service.db.workflow.WorkflowService;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.mapper.workflow.TaskStepMapper;
import com.smartlab.management.mapper.workflow.FlowNodeMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class WorkflowRuntimeSnapshotRegistry {
    private final ConcurrentHashMap<ObservableKey, ObservationSnapshot> values = new ConcurrentHashMap<>();
    private final AtomicLong revisions = new AtomicLong();
    private final WorkflowService workflowService;
    private final ApplicationEventPublisher events;
    private final TaskMapper taskMapper;
    private final TaskStepMapper stepMapper;
    private final FlowNodeMapper nodeMapper;
    private final ObservationHistoryStore history;

    /** Compatibility constructor for focused unit tests. */
    public WorkflowRuntimeSnapshotRegistry(WorkflowService workflowService, ApplicationEventPublisher events,
                                           TaskMapper taskMapper, TaskStepMapper stepMapper,
                                           FlowNodeMapper nodeMapper) {
        this(workflowService, events, taskMapper, stepMapper, nodeMapper, new ObservationHistoryStore());
    }

    @Autowired
    public WorkflowRuntimeSnapshotRegistry(WorkflowService workflowService, ApplicationEventPublisher events,
                                           TaskMapper taskMapper, TaskStepMapper stepMapper,
                                           FlowNodeMapper nodeMapper, ObservationHistoryStore history) {
        this.workflowService = workflowService;
        this.events = events;
        this.taskMapper = taskMapper;
        this.stepMapper = stepMapper;
        this.nodeMapper = nodeMapper;
        this.history = history;
    }

    @PostConstruct
    public void restorePersistedState() {
        for (Task task : taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .in(Task::getTaskStatus, "RUNNING", "PAUSED", "TERMINATING"))) {
            restore(new ObservableKey(ObservableObjectType.TASK_LIFECYCLE_STATE, null, null, task.getId(),
                    null, null, null, null), com.smartlab.global.util.JsonNodeSupport.MAPPER.getNodeFactory()
                    .textNode(task.getTaskStatus()), Instant.now());
            for (TaskStep step : stepMapper.selectList(Wrappers.<TaskStep>lambdaQuery()
                    .eq(TaskStep::getTaskId, task.getId()))) {
                FlowNode node = nodeMapper.selectById(step.getFlowNodeId());
                if (node == null) continue;
                String nodeName = resolveNodeName(node.getFlowModelId(), step.getNodeIdRef());
                if (nodeName == null) continue;
                restore(new ObservableKey(ObservableObjectType.NODE_LIFECYCLE_STATE, null,
                        node.getFlowModelId(), task.getId(), null, nodeName, null, null),
                        com.smartlab.global.util.JsonNodeSupport.MAPPER.getNodeFactory()
                                .textNode(step.getNodeStatus()), Instant.now());
                if (step.getVariableSpace() != null && step.getVariableSpace().isObject()) {
                    step.getVariableSpace().fields().forEachRemaining(entry -> restore(new ObservableKey(
                            ObservableObjectType.NODE_INTERNAL_VARIABLE, null, node.getFlowModelId(), task.getId(),
                            null, nodeName, null, entry.getKey()), entry.getValue(), Instant.now()));
                }
            }
        }
    }

    @EventListener
    public void observeTask(TaskLifecycleObservationEvent event) {
        ObservableKey key = new ObservableKey(ObservableObjectType.TASK_LIFECYCLE_STATE, null, null,
                event.taskId(), null, null, null, null);
        update(key, com.smartlab.global.util.JsonNodeSupport.MAPPER.getNodeFactory()
                .textNode(event.taskLifecycleState()), event.occurredAt());
    }

    @EventListener
    public void observeNode(WorkflowNodeObservationEvent event) {
        String nodeName = resolveNodeName(event.workflowTemplateId(), event.nodeIdRef());
        if (nodeName == null) return;
        ObservableKey lifecycle = new ObservableKey(ObservableObjectType.NODE_LIFECYCLE_STATE, null,
                event.workflowTemplateId(), event.taskId(), null, nodeName, null, null);
        update(lifecycle, com.smartlab.global.util.JsonNodeSupport.MAPPER.getNodeFactory()
                .textNode(event.nodeLifecycleState()), event.occurredAt());
        if (event.variableSpace() != null && event.variableSpace().isObject()) {
            event.variableSpace().fields().forEachRemaining(entry -> update(new ObservableKey(
                    ObservableObjectType.NODE_INTERNAL_VARIABLE, null, event.workflowTemplateId(), event.taskId(),
                    null, nodeName, null, entry.getKey()), entry.getValue(), event.occurredAt()));
        }
    }

    public ObservationSnapshot read(ObservableKey key) {
        ObservationSnapshot value = values.get(key);
        return value == null ? ObservationSnapshot.unavailable(key) : value;
    }

    public Map<ObservableKey, ObservationSnapshot> readBatch(Collection<ObservableKey> keys) {
        Map<ObservableKey, ObservationSnapshot> result = new LinkedHashMap<>();
        if (keys != null) for (ObservableKey key : keys) result.put(key, read(key));
        return Map.copyOf(result);
    }

    public void removeTask(Long taskId) {
        values.keySet().removeIf(key -> taskId != null && taskId.equals(key.taskId()));
        history.removeIf(key -> taskId != null && taskId.equals(key.taskId()));
    }

    private void update(ObservableKey key, JsonNode value, Instant observedAt) {
        long revision = revisions.incrementAndGet();
        Instant now = Instant.now();
        values.put(key, new ObservationSnapshot(key, value, observedAt == null ? now : observedAt, now,
                revision, ObservationStatus.VALID, SnapshotOrigin.LIVE));
        history.append(key, value, observedAt, revision);
        events.publishEvent(new ObservableChangedEvent(key, revision, now));
    }

    private void restore(ObservableKey key, JsonNode value, Instant observedAt) {
        long revision = revisions.incrementAndGet();
        values.put(key, new ObservationSnapshot(key, value, observedAt, Instant.now(), revision,
                ObservationStatus.STALE, SnapshotOrigin.RECOVERED));
    }

    private String resolveNodeName(Long workflowTemplateId, Long nodeIdRef) {
        return workflowService.compileDefinition(workflowTemplateId).refsByNodeName().entrySet().stream()
                .filter(entry -> entry.getValue().equals(nodeIdRef)).map(Map.Entry::getKey)
                .findFirst().orElse(null);
    }
}
