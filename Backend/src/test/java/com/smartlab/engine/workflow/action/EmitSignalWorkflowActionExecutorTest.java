package com.smartlab.engine.workflow.action;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.workflow.WorkflowExecutionOperations;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class EmitSignalWorkflowActionExecutorTest {
    @Test
    void stateEmitReturnsMailboxSignalWithoutDispatching() {
        WorkflowExecutionOperations operations = mock(WorkflowExecutionOperations.class);

        WorkflowActionResult result = new EmitSignalWorkflowActionExecutor().execute(
                action("Interface_state_out", "WF_EXECUTE_START"),
                new WorkflowActionContext(new Task(), new TaskStep(), node("STATE"),
                        JsonNodeSupport.objectNode(), java.time.Instant.now(), operations));

        assertEquals(WorkflowActionStatus.CONTINUE, result.status());
        assertEquals("Interface_state_out", result.emittedInterfaceName());
        assertEquals("WF_EXECUTE_START", result.emittedSignalName());
        assertNull(result.messageId());
        verify(operations, never()).dispatchDeviceSignal(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void workflowEmitStillReturnsMailboxSignal() {
        WorkflowActionResult result = new EmitSignalWorkflowActionExecutor().execute(
                action("Interface_workflow_out", "ACTIVE"),
                new WorkflowActionContext(new Task(), new TaskStep(), node("WORKFLOW"),
                        JsonNodeSupport.objectNode(), java.time.Instant.now(), mock(WorkflowExecutionOperations.class)));

        assertEquals("Interface_workflow_out", result.emittedInterfaceName());
        assertEquals("ACTIVE", result.emittedSignalName());
    }

    private WorkflowActionDefinition action(String interfaceName, String signalName) {
        ObjectNode action = JsonNodeSupport.objectNode();
        action.put("actionName", "EMIT");
        action.putObject("payload")
                .put("targetInterfaceName", interfaceName)
                .put("signalName", signalName);
        return WorkflowActionDefinition.from(action);
    }

    private FlowNode node(String interfaceType) {
        FlowNode result = new FlowNode();
        result.setCapability(JsonNodeSupport.objectNode().put("capabilityName", "HEAT"));
        var interfaces = JsonNodeSupport.arrayNode();
        ObjectNode out = interfaces.addObject();
        out.put("name", "STATE".equals(interfaceType) ? "Interface_state_out" : "Interface_workflow_out");
        out.put("direction", "OUT");
        out.put("interfaceType", interfaceType);
        out.putArray("allowedSignals").add("STATE".equals(interfaceType) ? "WF_EXECUTE_START" : "ACTIVE");
        result.setInterfaces(interfaces);
        return result;
    }
}
