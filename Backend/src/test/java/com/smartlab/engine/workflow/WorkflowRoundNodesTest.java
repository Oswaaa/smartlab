package com.smartlab.engine.workflow;

import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.workflow.FlowNodeService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WorkflowRoundNodesTest {
    @Test
    void preservesCompletedSourcesButMatchesOnlyPeersInSameModelParentAndDepth() {
        var service = mock(FlowNodeService.class);
        var source = node(1, 10, 1); var target = node(2, 10, 2);
        var otherModel = node(3, 20, 1);
        when(service.listByIds(List.of(1L, 2L, 3L))).thenReturn(List.of(source, target, otherModel));
        var finished = step(101, 1, 1, 50L, 1, "SUCCEEDED");
        var active = step(102, 2, 2, 50L, 1, "RUNNING");
        var sibling = step(103, 1, 1, 60L, 1, "SUCCEEDED");
        var deeper = step(104, 1, 1, 50L, 2, "SUCCEEDED");
        var foreign = step(105, 3, 1, 50L, 1, "SUCCEEDED");
        var round = WorkflowRoundNodes.load(List.of(finished, active, sibling, deeper, foreign), service);

        assertThat(round.activeSteps()).containsExactly(active);
        assertThat(round.activeModels()).containsExactly(10L);
        assertThat(round.activeTargets(10L, 1L)).isEmpty();
        assertThat(round.activeTargets(10L, 2L)).containsExactly(active);
        assertThat(round.peer(10L, 1L, active)).isSameAs(finished);
        assertThat(round.peer(20L, 1L, active)).isSameAs(foreign);
        assertThat(round.peer(10L, 1L, deeper)).isSameAs(deeper);
        assertThat(round.peer(10L, 1L, sibling)).isSameAs(sibling);
        verify(service).listByIds(List.of(1L, 2L, 3L));
        verify(service, never()).getById(any());
    }

    @Test
    void nextRoundSeesDefinitionAndLifecycleChangesWithoutReusingOldState() {
        var service = mock(FlowNodeService.class);
        var original = node(1, 10, 1); var changed = node(1, 10, 1);
        changed.setNodeType("DEV_NODE");
        when(service.listByIds(List.of(1L))).thenReturn(List.of(original), List.of(changed));
        var step = step(101, 1, 1, null, 0, "RUNNING");
        var before = WorkflowRoundNodes.load(List.of(step), service);
        step.setNodeStatus("SUCCEEDED");
        var after = WorkflowRoundNodes.load(List.of(step), service);
        assertThat(before.node(step)).isSameAs(original);
        assertThat(after.node(step)).isSameAs(changed);
        assertThat(after.activeSteps()).isEmpty();
        assertThat(after.peer(10L, 1L, step)).isSameAs(step);
    }

    @Test
    void emptyRoundDoesNotQueryDatabase() {
        var service = mock(FlowNodeService.class);
        assertThat(WorkflowRoundNodes.load(List.of(), service).steps()).isEmpty();
        verifyNoInteractions(service);
    }

    private FlowNode node(long id, long modelId, long ref) {
        var node = new FlowNode(); node.setId(id); node.setFlowModelId(modelId); node.setNodeIdRef(ref);
        return node;
    }
    private TaskStep step(long id, long nodeId, long ref, Long parent, int depth, String status) {
        var step = new TaskStep(); step.setId(id); step.setFlowNodeId(nodeId); step.setNodeIdRef(ref);
        step.setParentStepId(parent); step.setStepDepth(depth); step.setNodeStatus(status);
        return step;
    }
}
