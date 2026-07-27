package com.smartlab.engine.workflow.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.workflow.WorkflowExecutionOperations;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmitSignalWorkflowActionExecutorTest {
    @Test
    void waitsForIdleWhenTheStateMachineReportsDeviceBusy() {
        FakeOperations operations = new FakeOperations(WorkflowExecutionOperations.DeviceDispatchResult.DEVICE_BUSY);

        WorkflowActionResult result = new EmitSignalWorkflowActionExecutor().execute(
                action(), new WorkflowActionContext(new Task(), new TaskStep(), node(),
                        JsonNodeSupport.objectNode(), java.time.Instant.now(), operations));

        assertEquals(WorkflowActionStatus.WAIT_DEVICE_IDLE, result.status());
        assertEquals("message-1", result.messageId());
    }

    @Test
    void waitsForCommandStateWhenTheStateMachineAcceptsTheCommand() {
        FakeOperations operations = new FakeOperations(WorkflowExecutionOperations.DeviceDispatchResult.ACCEPTED);

        WorkflowActionResult result = new EmitSignalWorkflowActionExecutor().execute(
                action(), new WorkflowActionContext(new Task(), new TaskStep(), node(),
                        JsonNodeSupport.objectNode(), java.time.Instant.now(), operations));

        assertEquals(WorkflowActionStatus.AWAIT_EXTERNAL_SIGNAL, result.status());
        assertEquals("message-1", result.messageId());
    }

    private WorkflowActionDefinition action() {
        ObjectNode payload = JsonNodeSupport.objectNode();
        payload.put("targetInterfaceName", "Interface_state_out");
        payload.put("signalName", "WF_EXECUTE_START");
        return new WorkflowActionDefinition("start", "EMIT", payload);
    }

    private FlowNode node() {
        FlowNode result = new FlowNode();
        ObjectNode capability = JsonNodeSupport.objectNode();
        capability.put("capabilityName", "HEAT");
        result.setCapability(capability);
        var interfaces = JsonNodeSupport.arrayNode();
        ObjectNode stateOut = interfaces.addObject();
        stateOut.put("name", "Interface_state_out");
        stateOut.put("direction", "OUT");
        stateOut.put("interfaceType", "STATE");
        stateOut.putArray("allowedSignals").add("WF_EXECUTE_START");
        result.setInterfaces(interfaces);
        return result;
    }

    private static final class FakeOperations implements WorkflowExecutionOperations {
        private final DeviceDispatchResult dispatchResult;

        private FakeOperations(DeviceDispatchResult dispatchResult) {
            this.dispatchResult = dispatchResult;
        }

        @Override
        public long resolveDeviceInstance(Task task, TaskStep step, FlowNode node) {
            return 55L;
        }

        @Override
        public String ensureMessageId(TaskStep step, long deviceInstanceId, String capabilityRef) {
            return "message-1";
        }

        @Override
        public DeviceDispatchResult dispatchDeviceSignal(Task task, TaskStep step, FlowNode node, long deviceInstanceId,
                                                         String messageId, String nodeOutputInterfaceName,
                                                         String signalName, JsonNode parameters) {
            return dispatchResult;
        }

        @Override
        public void dispatchDeviceAbort(Task task, TaskStep step, FlowNode node, long deviceInstanceId, String messageId) {
        }
    }
}
