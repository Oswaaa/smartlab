package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowSaveRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowDefinitionCompilerTest {
    private final WorkflowDefinitionCompiler compiler = new WorkflowDefinitionCompiler();

    @Test
    void compilesDistinctActionNamesWithTheSameEmitActionType() throws Exception {
        var compiled = compiler.compile(validDeviceDefinition());

        assertEquals(3, compiled.nodes().size());
        assertEquals(1, compiled.outgoing(2).size());
    }

    @Test
    void rejectsPortConnectionWhenVariableTypesDiffer() throws Exception {
        WorkflowSaveRequest request = validDefinition();
        ObjectNode connection = ((ArrayNode) request.getPortConnections()).addObject();
        connection.putObject("source").put("nodeName", "source").put("portName", "valueOut");
        connection.putObject("target").put("nodeName", "target").put("portName", "valueIn");
        ObjectNode sourceNode = (ObjectNode) request.getNodesDef().get(0);
        ObjectNode targetNode = (ObjectNode) request.getNodesDef().get(1);
        ((ObjectNode) sourceNode.withArray("internalVariables").get(0)).put("dataType", "DOUBLE");
        ((ObjectNode) targetNode.withArray("internalVariables").get(0)).put("dataType", "STRING");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> compiler.compile(request));
        assertTrue(error.getMessage().contains("portConnections[0]"));
        assertTrue(error.getMessage().contains("数据类型不一致"));
    }

    @Test
    void rejectsBranchWithoutFalseOutput() throws Exception {
        WorkflowSaveRequest request = validBranchDefinition();
        ObjectNode branch = (ObjectNode) request.getNodesDef().get(1);
        removeNamedItem(branch.withArray("interfaces"), "Interface_false_out");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> compiler.compile(request));
        assertTrue(error.getMessage().contains("节点branch"));
        assertTrue(error.getMessage().contains("Interface_false_out"));
    }

    @Test
    void rejectsEmitToInputInterface() throws Exception {
        WorkflowSaveRequest request = validDefinition();
        ObjectNode start = (ObjectNode) request.getNodesDef().get(0);
        ((ObjectNode) start.withArray("actions").get(0)).put("targetInterfaceName", "Interface_workflow_in");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> compiler.compile(request));
        assertTrue(error.getMessage().contains("EMIT"));
        assertTrue(error.getMessage().contains("OUT接口"));
    }

    private WorkflowSaveRequest validDefinition() throws Exception {
        WorkflowSaveRequest request = new WorkflowSaveRequest();
        request.setName("port-workflow");
        request.setNodesDef(JsonNodeSupport.MAPPER.readTree("""
                [
                  {"name":"source","nodeType":"FUNC_NODE","functionType":"START","internalVariables":[{"name":"value","dataType":"DOUBLE"}],"interfaces":[{"name":"Interface_workflow_out","direction":"OUT","interfaceType":"WORKFLOW","allowedSignals":["ACTIVE"]},{"name":"Interface_workflow_in","direction":"IN","interfaceType":"WORKFLOW","allowedSignals":["ACTIVE"]}],"ports":[{"name":"valueOut","direction":"OUT","internalVariableName":"value"}],"actions":[{"actionName":"emitActive","actionType":"EMIT","targetInterfaceName":"Interface_workflow_out","signalName":"ACTIVE"}]},
                  {"name":"target","nodeType":"FUNC_NODE","functionType":"END","internalVariables":[{"name":"value","dataType":"DOUBLE"}],"interfaces":[{"name":"Interface_workflow_in","direction":"IN","interfaceType":"WORKFLOW","allowedSignals":["ACTIVE"]}],"ports":[{"name":"valueIn","direction":"IN","internalVariableName":"value"}],"actions":[]}
                ]
                """));
        request.setInterfaceConnections(JsonNodeSupport.MAPPER.readTree("""
                [{"connectionType":"NODE_TO_NODE","source":{"nodeName":"source","interfaceName":"Interface_workflow_out"},"target":{"nodeName":"target","interfaceName":"Interface_workflow_in"}}]
                """));
        request.setPortConnections(JsonNodeSupport.arrayNode());
        return request;
    }

    private WorkflowSaveRequest validBranchDefinition() throws Exception {
        WorkflowSaveRequest request = new WorkflowSaveRequest();
        request.setName("branch-workflow");
        request.setNodesDef(JsonNodeSupport.MAPPER.readTree("""
                [
                  {"name":"start","nodeType":"FUNC_NODE","functionType":"START","internalVariables":[],"interfaces":[{"name":"Interface_workflow_out","direction":"OUT","interfaceType":"WORKFLOW","allowedSignals":["ACTIVE"]}],"ports":[],"actions":[{"actionName":"emitActive","actionType":"EMIT","targetInterfaceName":"Interface_workflow_out","signalName":"ACTIVE"}]},
                  {"name":"branch","nodeType":"FUNC_NODE","functionType":"BRANCH","expression":"temperature > 30","internalVariables":[],"interfaces":[{"name":"Interface_workflow_in","direction":"IN","interfaceType":"WORKFLOW","allowedSignals":["ACTIVE"],"bindingTriggers":[{"condition":{"object":"expression","operator":"=","threshold":true},"action":"emitTrue"},{"condition":{"object":"expression","operator":"=","threshold":false},"action":"emitFalse"}]},{"name":"Interface_true_out","direction":"OUT","interfaceType":"WORKFLOW","allowedSignals":["ACTIVE"]},{"name":"Interface_false_out","direction":"OUT","interfaceType":"WORKFLOW","allowedSignals":["ACTIVE"]}],"ports":[],"actions":[{"actionName":"emitTrue","actionType":"EMIT","targetInterfaceName":"Interface_true_out","signalName":"ACTIVE"},{"actionName":"emitFalse","actionType":"EMIT","targetInterfaceName":"Interface_false_out","signalName":"ACTIVE"}]},
                  {"name":"end","nodeType":"FUNC_NODE","functionType":"END","internalVariables":[],"interfaces":[{"name":"Interface_workflow_in","direction":"IN","interfaceType":"WORKFLOW","allowedSignals":["ACTIVE"]}],"ports":[],"actions":[]}
                ]
                """));
        request.setInterfaceConnections(JsonNodeSupport.MAPPER.readTree("""
                [
                  {"connectionType":"NODE_TO_NODE","source":{"nodeName":"start","interfaceName":"Interface_workflow_out"},"target":{"nodeName":"branch","interfaceName":"Interface_workflow_in"}},
                  {"connectionType":"NODE_TO_NODE","source":{"nodeName":"branch","interfaceName":"Interface_true_out"},"target":{"nodeName":"end","interfaceName":"Interface_workflow_in"}}
                ]
                """));
        request.setPortConnections(JsonNodeSupport.arrayNode());
        return request;
    }

    private WorkflowSaveRequest validDeviceDefinition() throws Exception {
        WorkflowSaveRequest request = new WorkflowSaveRequest();
        request.setName("final-workflow");
        request.setNodesDef(JsonNodeSupport.MAPPER.readTree("""
                [
                  {"name":"start","nodeType":"FUNC_NODE","functionType":"START","internalVariables":[],"interfaces":[{"name":"Interface_workflow_out","direction":"OUT","interfaceType":"WORKFLOW","allowedSignals":["ACTIVE"]}],"ports":[],"actions":[{"actionName":"emitActive","actionType":"EMIT","targetInterfaceName":"Interface_workflow_out","signalName":"ACTIVE"}]},
                  {"name":"device","nodeType":"DEV_NODE","deviceModelId":1,"capability":{"capabilityName":"mix","capabilityParameters":{}},"internalVariables":[],"lifecycle":{"initialStateName":"PENDING","states":["PENDING","RUNNING","SUCCEEDED","FAILED","TERMINATING","TERMINATED"],"transitions":[{"fromStateName":"PENDING","toStateName":"RUNNING"},{"fromStateName":"PENDING","toStateName":"TERMINATED"},{"fromStateName":"RUNNING","toStateName":"SUCCEEDED"},{"fromStateName":"RUNNING","toStateName":"FAILED"},{"fromStateName":"RUNNING","toStateName":"TERMINATING"},{"fromStateName":"TERMINATING","toStateName":"TERMINATED"},{"fromStateName":"TERMINATING","toStateName":"FAILED"}]},"interfaces":[{"name":"Interface_workflow_in","direction":"IN","interfaceType":"WORKFLOW","allowedSignals":["ACTIVE"],"bindingTriggers":[{"condition":{"object":"inputSignalName","operator":"=","threshold":"ACTIVE"},"action":"startDevice"}]},{"name":"Interface_state_out","direction":"OUT","interfaceType":"STATE","allowedSignals":["WF_EXECUTE_START"]},{"name":"Interface_state_in","direction":"IN","interfaceType":"STATE","allowedSignals":["CMD_STATE","OP_STATE"],"bindingTriggers":[{"condition":{"object":"inputPayload.stateName","operator":"=","threshold":"COMPLETED"},"action":"completeNode"}]},{"name":"Interface_workflow_out","direction":"OUT","interfaceType":"WORKFLOW","allowedSignals":["ACTIVE"]}],"ports":[],"actions":[{"actionName":"startDevice","actionType":"EMIT","targetInterfaceName":"Interface_state_out","signalName":"WF_EXECUTE_START"},{"actionName":"completeNode","actionType":"EMIT","targetInterfaceName":"Interface_workflow_out","signalName":"ACTIVE"}]},
                  {"name":"end","nodeType":"FUNC_NODE","functionType":"END","internalVariables":[],"interfaces":[{"name":"Interface_workflow_in","direction":"IN","interfaceType":"WORKFLOW","allowedSignals":["ACTIVE"]}],"ports":[],"actions":[]}
                ]
                """));
        request.setInterfaceConnections(JsonNodeSupport.MAPPER.readTree("""
                [
                  {"connectionType":"NODE_TO_NODE","source":{"nodeName":"start","interfaceName":"Interface_workflow_out"},"target":{"nodeName":"device","interfaceName":"Interface_workflow_in"}},
                  {"connectionType":"NODE_TO_NODE","source":{"nodeName":"device","interfaceName":"Interface_workflow_out"},"target":{"nodeName":"end","interfaceName":"Interface_workflow_in"}},
                  {"connectionType":"NODE_TO_DEVICE","source":{"nodeName":"device","interfaceName":"Interface_state_out"},"target":{"deviceModelId":1,"interfaceName":"Interface_workflow_in"}},
                  {"connectionType":"DEVICE_TO_NODE","source":{"deviceModelId":1,"interfaceName":"Interface_state_out"},"target":{"nodeName":"device","interfaceName":"Interface_state_in"}}
                ]
                """));
        request.setPortConnections(JsonNodeSupport.arrayNode());
        return request;
    }

    private void removeNamedItem(ArrayNode items, String name) {
        for (int index = 0; index < items.size(); index++) {
            if (name.equals(items.get(index).path("name").asText())) {
                items.remove(index);
                return;
            }
        }
    }
}
