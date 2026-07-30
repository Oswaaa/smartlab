package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.constraint.ConstraintExpressionEvaluator;
import com.smartlab.engine.workflow.action.WorkflowActionContext;
import com.smartlab.engine.workflow.action.WorkflowActionDefinition;
import com.smartlab.engine.workflow.action.WorkflowActionExecutor;
import com.smartlab.engine.workflow.action.WorkflowActionRegistry;
import com.smartlab.engine.workflow.action.WorkflowActionResult;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.workflow.FlowNodeService;
import com.smartlab.management.service.db.workflow.TaskService;
import com.smartlab.management.service.db.workflow.WorkflowRuntimeService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkflowEngineExecutionTest {

    private final WorkflowRuntimeService runtime = mock(WorkflowRuntimeService.class);
    private final WorkflowExecutionOperations operations = mock(WorkflowExecutionOperations.class);
    private final List<String> executionOrder = new ArrayList<>();
    private final WorkflowActionRegistry registry = new WorkflowActionRegistry(List.of(
            executor("UPDATE", WorkflowActionResult.continueWith(JsonNodeSupport.objectNode().put("temperature", 28))),
            executor("EMIT", WorkflowActionResult.emitWorkflowSignal("Interface_workflow_out", "ACTIVE"))));
    private final WorkflowEngine engine = new WorkflowEngine(runtime, mock(WorkflowService.class),
            mock(FlowNodeService.class), new WorkflowConditionEvaluator(), new ConstraintExpressionEvaluator(),
            registry, operations, mock(TaskService.class));

    @Test
    void executesAllUpdatesBeforeSingleEmitEvenWhenEmitWasDeclaredFirst() {
        Task task = new Task();
        TaskStep step = new TaskStep();
        FlowNode node = new FlowNode();
        ArrayNode actions = JsonNodeSupport.arrayNode();
        actions.add(action("continue", "EMIT"));
        actions.add(action("setTemperature", "UPDATE"));

        engine.executeActions(task, step, node, actions, JsonNodeSupport.objectNode());

        assertThat(executionOrder).containsExactly("UPDATE", "EMIT");
        verify(runtime).mergeVariableSpace(eq(step), eq(JsonNodeSupport.objectNode().put("temperature", 28)));
    }

    @Test
    void rejectsTwoEmitActionsBeforeAnyDeviceOperationRuns() {
        Task task = new Task();
        TaskStep step = new TaskStep();
        FlowNode node = new FlowNode();
        ArrayNode actions = JsonNodeSupport.arrayNode();
        actions.add(action("first", "EMIT"));
        actions.add(action("second", "EMIT"));

        assertThatThrownBy(() -> engine.executeActions(task, step, node, actions, JsonNodeSupport.objectNode()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("同一输入命中多个EMIT动作");
        verifyNoInteractions(operations);
    }

    @Test
    void copiesStrictlyTypedSourcePortValueIntoTargetVariable() {
        WorkflowService workflows = mock(WorkflowService.class);
        WorkflowEngine portEngine = engine(workflows);
        FlowNode source = nodeWithPort(1L, 1L, "measured", "DOUBLE", "temperatureOut", "OUT");
        FlowNode target = nodeWithPort(1L, 2L, "target", "DOUBLE", "temperatureIn", "IN");
        TaskStep step = new TaskStep();
        step.setTaskId(9L);
        step.setVariableSpace(JsonNodeSupport.objectNode().put("measured", 26.5));
        stubPortConnection(workflows);

        ObjectNode values = portEngine.mapPortValues(source, target, step);

        assertThat(values.path("target").decimalValue()).isEqualByComparingTo("26.5");
    }

    @Test
    void logsAndSkipsUnsetSourcePortValue() {
        WorkflowService workflows = mock(WorkflowService.class);
        WorkflowEngine portEngine = engine(workflows);
        FlowNode source = nodeWithPort(1L, 1L, "measured", "DOUBLE", "temperatureOut", "OUT");
        FlowNode target = nodeWithPort(1L, 2L, "target", "DOUBLE", "temperatureIn", "IN");
        TaskStep step = new TaskStep();
        step.setTaskId(9L);
        step.setId(12L);
        step.setVariableSpace(JsonNodeSupport.objectNode());
        Task task = new Task();
        task.setId(9L);
        when(runtime.task(9L)).thenReturn(task);
        stubPortConnection(workflows);

        assertThat(portEngine.mapPortValues(source, target, step)).isEmpty();
        verify(runtime).appendStepLog(eq(task), eq(step), eq("WARN"),
                eq("端口temperatureOut绑定变量measured尚无值"));
    }

    private WorkflowActionExecutor executor(String actionType, WorkflowActionResult result) {
        return new WorkflowActionExecutor() {
            @Override
            public String actionName() {
                return actionType;
            }

            @Override
            public WorkflowActionResult execute(WorkflowActionDefinition action, WorkflowActionContext context) {
                executionOrder.add(action.actionType());
                return result;
            }
        };
    }

    private ObjectNode action(String name, String type) {
        return JsonNodeSupport.objectNode().put("actionName", name).put("actionType", type);
    }

    private WorkflowEngine engine(WorkflowService workflows) {
        return new WorkflowEngine(runtime, workflows, mock(FlowNodeService.class),
                new WorkflowConditionEvaluator(), new ConstraintExpressionEvaluator(), registry, operations,
                mock(TaskService.class));
    }

    private FlowNode nodeWithPort(long flowModelId, long nodeRef, String variableName, String dataType,
                                  String portName, String direction) {
        FlowNode node = new FlowNode();
        node.setFlowModelId(flowModelId);
        node.setNodeIdRef(nodeRef);
        ArrayNode variables = JsonNodeSupport.arrayNode();
        variables.addObject().put("name", variableName).put("dataType", dataType);
        node.setInVariables(variables);
        ArrayNode ports = JsonNodeSupport.arrayNode();
        ports.addObject().put("name", portName).put("direction", direction)
                .put("internalVariableName", variableName);
        node.setPorts(ports);
        return node;
    }

    private void stubPortConnection(WorkflowService workflows) {
        ObjectNode connection = JsonNodeSupport.objectNode();
        connection.set("source", JsonNodeSupport.objectNode()
                .put("nodeName", "source").put("portName", "temperatureOut"));
        connection.set("target", JsonNodeSupport.objectNode()
                .put("nodeName", "target").put("portName", "temperatureIn"));
        WorkflowDetailResponse definition = new WorkflowDetailResponse();
        definition.setPortConnections(JsonNodeSupport.arrayNode().add(connection));
        when(workflows.getDefinition(1L)).thenReturn(definition);
        when(workflows.compileDefinition(1L)).thenReturn(new WorkflowDefinitionCompiler.CompiledWorkflow(
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(),
                java.util.Map.of("source", 1L, "target", 2L), 1L, 2L));
    }
}
