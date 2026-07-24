package com.smartlab.engine.workflow;

import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowSaveRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowDefinitionCompilerTest {
    private final WorkflowDefinitionCompiler compiler = new WorkflowDefinitionCompiler();

    @Test
    void compilesDistinctActionNamesWithTheSameEmitActionType() throws Exception {
        WorkflowSaveRequest request = new WorkflowSaveRequest();
        request.setName("final-workflow");
        request.setNodesDef(JsonNodeSupport.MAPPER.readTree("""
                [
                  {"name":"start","nodeType":"FUNC_NODE","functionType":"START","internalVariables":[],"interfaces":[{"name":"Interface_workflow_out","direction":"OUT","interfaceType":"WORKFLOW","allowedSignals":["ACTIVE"]}],"ports":[],"actions":[{"actionName":"emitStart","actionType":"EMIT","targetInterfaceName":"Interface_workflow_out","signalName":"ACTIVE"}]},
                  {"name":"device","nodeType":"DEV_NODE","deviceModelId":1,"capability":{"capabilityName":"mix"},"internalVariables":[],"lifecycle":{"initialStateName":"PENDING","states":["PENDING","RUNNING","SUCCEEDED","FAILED","TERMINATING","TERMINATED"],"transitions":[{"fromStateName":"PENDING","toStateName":"RUNNING"},{"fromStateName":"PENDING","toStateName":"TERMINATED"},{"fromStateName":"RUNNING","toStateName":"SUCCEEDED"},{"fromStateName":"RUNNING","toStateName":"FAILED"},{"fromStateName":"RUNNING","toStateName":"TERMINATING"},{"fromStateName":"TERMINATING","toStateName":"TERMINATED"},{"fromStateName":"TERMINATING","toStateName":"FAILED"}]},"interfaces":[{"name":"Interface_workflow_in","direction":"IN","interfaceType":"WORKFLOW","allowedSignals":["ACTIVE"],"bindingTriggers":[{"condition":{"object":"inputSignalName","operator":"=","threshold":"ACTIVE"},"action":"startDevice"}]},{"name":"Interface_state_out","direction":"OUT","interfaceType":"STATE","allowedSignals":["WF_EXECUTE_START"]},{"name":"Interface_state_in","direction":"IN","interfaceType":"STATE","allowedSignals":["CMD_STATE"],"bindingTriggers":[{"condition":{"object":"inputPayload.stateName","operator":"=","threshold":"COMPLETED"},"action":"completeDevice"}]},{"name":"Interface_workflow_out","direction":"OUT","interfaceType":"WORKFLOW","allowedSignals":["ACTIVE"]}],"ports":[],"actions":[{"actionName":"startDevice","actionType":"EMIT","targetInterfaceName":"Interface_state_out","signalName":"WF_EXECUTE_START"},{"actionName":"completeDevice","actionType":"EMIT","targetInterfaceName":"Interface_workflow_out","signalName":"ACTIVE"}]},
                  {"name":"end","nodeType":"FUNC_NODE","functionType":"END","internalVariables":[],"interfaces":[{"name":"Interface_workflow_in","direction":"IN","interfaceType":"WORKFLOW","allowedSignals":["ACTIVE"]}],"ports":[],"actions":[]}
                ]
                """));
        request.setInterfaceConnections(JsonNodeSupport.MAPPER.readTree("""
                [
                  {"connectionType":"NODE_TO_NODE","source":{"nodeName":"start","interfaceName":"Interface_workflow_out"},"target":{"nodeName":"device","interfaceName":"Interface_workflow_in"}},
                  {"connectionType":"NODE_TO_NODE","source":{"nodeName":"device","interfaceName":"Interface_workflow_out"},"target":{"nodeName":"end","interfaceName":"Interface_workflow_in"}},
                  {"connectionType":"NODE_TO_DEVICE","source":{"nodeName":"device","interfaceName":"Interface_state_out"},"target":{"deviceInstanceId":99,"interfaceName":"Interface_workflow_in"}},
                  {"connectionType":"DEVICE_TO_NODE","source":{"deviceInstanceId":99,"interfaceName":"Interface_state_out"},"target":{"nodeName":"device","interfaceName":"Interface_state_in"}}
                ]
                """));
        request.setPortConnections(JsonNodeSupport.arrayNode());

        var compiled = compiler.compile(request);

        assertEquals(3, compiled.nodes().size());
        assertEquals(1, compiled.outgoing(2).size());
    }
}
