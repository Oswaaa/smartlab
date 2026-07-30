package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.StateMachineEngine;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.resource.device.DeviceTwinStateService;
import com.smartlab.management.service.db.workflow.WorkflowRuntimeService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import com.smartlab.management.service.db.workflow.WorkflowTaskResourceService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultWorkflowExecutionOperationsTest {

    private final WorkflowRuntimeService runtime = mock(WorkflowRuntimeService.class);
    private final WorkflowTaskResourceService resources = mock(WorkflowTaskResourceService.class);
    private final StateMachineEngine stateMachine = mock(StateMachineEngine.class);
    private final WorkflowService workflows = mock(WorkflowService.class);
    private final DeviceTwinStateService twins = mock(DeviceTwinStateService.class);
    private final DefaultWorkflowExecutionOperations operations =
            new DefaultWorkflowExecutionOperations(runtime, resources, stateMachine, workflows, twins);
    private final Task task = new Task();
    private final TaskStep step = new TaskStep();

    @Test
    void mapsDeclaredDeviceAttributesFromCurrentTwinState() {
        FlowNode node = deviceNodeWithVariable("temperature", "DOUBLE", "currentTemperature");
        stubTwin(node, JsonNodeSupport.objectNode().put("currentTemperature", 31.5));

        ObjectNode updates = operations.resolveMappedVariables(task, step, node);

        assertThat(updates.path("temperature").decimalValue()).isEqualByComparingTo("31.5");
    }

    @Test
    void rejectsMappedAttributeWithWrongDeclaredType() {
        FlowNode node = deviceNodeWithVariable("temperature", "STRING", "currentTemperature");
        stubTwin(node, JsonNodeSupport.objectNode().put("currentTemperature", 31.5));

        assertThatThrownBy(() -> operations.resolveMappedVariables(task, step, node))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("变量temperature要求STRING")
                .hasMessageContaining("currentTemperature");
    }

    private void stubTwin(FlowNode node, ObjectNode attributes) {
        DeviceInstances instance = new DeviceInstances();
        instance.setId(101L);
        DeviceTwinStates twin = new DeviceTwinStates();
        twin.setCurrentAttr(attributes);
        when(resources.resolveDeviceInstance(task, step, node)).thenReturn(instance);
        when(twins.getByInstanceId(101L)).thenReturn(twin);
    }

    private FlowNode deviceNodeWithVariable(String name, String dataType, String mapping) {
        FlowNode node = new FlowNode();
        node.setNodeType("DEV_NODE");
        ArrayNode variables = JsonNodeSupport.arrayNode();
        variables.addObject().put("name", name).put("dataType", dataType).put("attributesMapping", mapping);
        node.setInVariables(variables);
        return node;
    }
}
