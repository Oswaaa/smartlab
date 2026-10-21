package com.smartlab.engine.workflow;

import com.smartlab.engine.constraint.ConstraintExpressionEvaluator;
import com.smartlab.engine.workflow.action.WorkflowActionRegistry;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.mapper.workflow.FlowNodeMapper;
import com.smartlab.management.service.db.workflow.FlowNodeService;
import com.smartlab.management.service.db.workflow.WorkflowRuntimeService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowRoundQueriesTest {
    @ParameterizedTest
    @ValueSource(ints = {2, 18, 100})
    void loadsDefinitionsOnceRegardlessOfCompletedStepCount(int count) {
        var runtime = mock(WorkflowRuntimeService.class);
        var workflows = mock(WorkflowService.class);
        var mapper = mock(FlowNodeMapper.class);
        var nodes = new FlowNodeService(mapper);
        var task = new Task();
        task.setId(1L);
        task.setTaskStatus("RUNNING");
        var steps = new ArrayList<TaskStep>();
        var definitions = new ArrayList<FlowNode>();
        for (long id = 1; id <= count; id++) {
            var node = new FlowNode();
            node.setId(id); node.setFlowModelId(1L); node.setNodeIdRef(id);
            node.setNodeType("FUNC_NODE");
            definitions.add(node);
            when(mapper.selectById(id)).thenReturn(node);
            var step = new TaskStep();
            step.setId(id); step.setTaskId(1L); step.setFlowNodeId(id); step.setNodeIdRef(id);
            step.setNodeStatus("SUCCEEDED");
            step.setVariableSpace(JsonNodeSupport.objectNode().set("_triggerStates",
                    JsonNodeSupport.objectNode().put(WorkflowTriggerState.TERMINAL_OBSERVED_KEY, true)));
            steps.add(step);
        }
        when(mapper.selectBatchIds(any())).thenReturn(definitions);
        when(runtime.steps(1L)).thenReturn(steps);
        when(runtime.task(1L)).thenReturn(task);
        var engine = new WorkflowEngine(runtime, workflows, nodes, new WorkflowConditionEvaluator(),
                new ConstraintExpressionEvaluator(), mock(WorkflowActionRegistry.class),
                mock(WorkflowExecutionOperations.class));

        engine.processTask(task);
        engine.processTask(task);

        verify(runtime, times(1)).steps(1L);
        verify(runtime).stepsForRound(any(), any());
        verify(mapper, times(1)).selectBatchIds(any());
        verify(mapper, never()).selectById(any());
    }
}
