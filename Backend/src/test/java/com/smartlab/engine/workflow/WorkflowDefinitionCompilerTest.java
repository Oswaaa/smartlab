package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.contract.WorkflowNodeSystemContract;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowSaveRequest;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
        assertEquals(
                "portConnections[0]: 源目标内部变量数据类型不一致: DOUBLE -> STRING",
                error.getMessage());
    }

    @Test
    void acceptsCustomTriggerThatTargetsCustomUpdate() {
        ObjectNode aggregate = node(new NodeCase("FUNC_NODE", "AGGREGATE"));
        aggregate.withArray("internalVariables").addObject()
                .put("name", "payload")
                .put("dataType", "JSON");
        aggregate.withArray("actions").addObject()
                .put("actionName", "setPayload")
                .put("actionType", "UPDATE")
                .put("internalVariableName", "payload")
                .put("valueExpression", "{}");
        ((ObjectNode) aggregate.withArray("interfaces").get(0)).withArray("bindingTriggers").addObject()
                .put("action", "setPayload")
                .putObject("condition")
                .put("object", "inputSignalName")
                .put("operator", "=")
                .put("threshold", "OTHER");

        assertDoesNotThrow(() -> compiler.compile(requestWith(aggregate)));
    }

    @Test
    void rejectsCustomTriggerThatTargetsSystemEmit() {
        ObjectNode aggregate = node(new NodeCase("FUNC_NODE", "AGGREGATE"));
        ((ObjectNode) aggregate.withArray("interfaces").get(0)).withArray("bindingTriggers").addObject()
                .put("action", "emitActive")
                .putObject("condition")
                .put("object", "inputSignalName")
                .put("operator", "=")
                .put("threshold", "OTHER");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> compiler.compile(requestWith(aggregate)));
        assertEquals(
                "节点candidate.interfaces.Interface_workflow_in.bindingTriggers[1].action: "
                        + "自定义触发器只能调用非系统UPDATE动作: emitActive",
                error.getMessage());
    }

    @Test
    void rejectsCustomEmitEvenWhenItsTargetWouldOtherwiseBeValid() {
        ObjectNode aggregate = node(new NodeCase("FUNC_NODE", "AGGREGATE"));
        aggregate.withArray("actions").addObject()
                .put("actionName", "customEmit")
                .put("actionType", "EMIT")
                .put("targetInterfaceName", "Interface_workflow_out")
                .put("signalName", "ACTIVE");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> compiler.compile(requestWith(aggregate)));
        assertEquals(
                "节点candidate.actions.customEmit: 只允许新增非系统UPDATE动作",
                error.getMessage());
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
        ObjectNode end = (ObjectNode) request.getNodesDef().get(1);
        end.withArray("actions").addObject()
                .put("actionName", "invalidEmit")
                .put("actionType", "EMIT")
                .put("targetInterfaceName", "Interface_workflow_in")
                .put("signalName", "ACTIVE");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> compiler.compile(request));
        assertTrue(error.getMessage().contains("EMIT"));
        assertTrue(error.getMessage().contains("OUT接口"));
    }

    @Test void compilerAcceptsEveryBackendSystemTemplate() { for (NodeCase c : cases()) assertDoesNotThrow(() -> compiler.compile(requestWith(c)), c.toString()); }

    @Test void compilerRejectsTamperedBackendSystemItem() { ObjectNode n = node(new NodeCase("DEV_NODE", null)); ((ObjectNode)n.withArray("interfaces").get(1)).putArray("allowedSignals").add("UNKNOWN"); assertThrows(IllegalArgumentException.class, () -> compiler.compile(requestWith(n))); }

    private List<NodeCase> cases() { return List.of(new NodeCase("FUNC_NODE", "START"), new NodeCase("FUNC_NODE", "END"), new NodeCase("FUNC_NODE", "BRANCH"), new NodeCase("FUNC_NODE", "AGGREGATE"), new NodeCase("DEV_NODE", null), new NodeCase("SUBFLOW_NODE", null)); }
    private ObjectNode node(NodeCase c) { ObjectNode n=WorkflowNodeSystemContract.template(c.type(),c.function()).deepCopy(); n.put("name","candidate"); n.put("nodeType",c.type()); n.putArray("internalVariables"); n.putArray("ports"); if(c.function()!=null)n.put("functionType",c.function()); if("BRANCH".equals(c.function()))n.put("expression","x > 1"); if("DEV_NODE".equals(c.type())){n.put("deviceModelId",1);n.putObject("capability").put("capabilityName","mix");} if("SUBFLOW_NODE".equals(c.type()))n.put("subFlowModelId",2); return n; }
    private WorkflowSaveRequest requestWith(NodeCase c) { return requestWith(node(c)); }
    private WorkflowSaveRequest requestWith(ObjectNode n) { WorkflowSaveRequest r=new WorkflowSaveRequest(); r.setName("contract"); ObjectNode s=node(new NodeCase("FUNC_NODE","START")); s.put("name","start"); ObjectNode e=node(new NodeCase("FUNC_NODE","END")); e.put("name","end"); ArrayNode ns=JsonNodeSupport.arrayNode().add(s).add(n).add(e); ArrayNode cs=JsonNodeSupport.arrayNode(); if("START".equals(n.path("functionType").asText())||"END".equals(n.path("functionType").asText())){ns.remove(1); link(cs,"start","end");} else if ("AGGREGATE".equals(n.path("functionType").asText())) { ObjectNode relay=node(new NodeCase("SUBFLOW_NODE",null)); relay.put("name","relay"); ns.insert(1,relay); link(cs,"start","candidate"); link(cs,"start","relay"); link(cs,"relay","candidate"); link(cs,"candidate","end"); } else {link(cs,"start","candidate"); if ("BRANCH".equals(n.path("functionType").asText())) linkNamed(cs,"candidate","Interface_true_out","end"); else link(cs,"candidate","end");} if ("DEV_NODE".equals(n.path("nodeType").asText())) { ObjectNode x=cs.addObject(); x.put("connectionType","NODE_TO_DEVICE"); x.putObject("source").put("nodeName","candidate").put("interfaceName","Interface_state_out"); x.putObject("target").put("deviceModelId",1).put("interfaceName","Interface_workflow_in"); x=cs.addObject(); x.put("connectionType","DEVICE_TO_NODE"); x.putObject("source").put("deviceModelId",1).put("interfaceName","Interface_state_out"); x.putObject("target").put("nodeName","candidate").put("interfaceName","Interface_state_in"); } r.setNodesDef(ns);r.setInterfaceConnections(cs);r.setPortConnections(JsonNodeSupport.arrayNode());return r; }
    private void linkNamed(ArrayNode cs,String from,String sourceInterface,String to){ObjectNode c=cs.addObject();c.put("connectionType","NODE_TO_NODE");c.putObject("source").put("nodeName",from).put("interfaceName",sourceInterface);c.putObject("target").put("nodeName",to).put("interfaceName","Interface_workflow_in");}
        private void link(ArrayNode cs,String from,String to){ObjectNode c=cs.addObject();c.put("connectionType","NODE_TO_NODE");c.putObject("source").put("nodeName",from).put("interfaceName","Interface_workflow_out");c.putObject("target").put("nodeName",to).put("interfaceName","Interface_workflow_in");}
    private record NodeCase(String type,String function) {}

    private WorkflowSaveRequest validDefinition() throws Exception {
        WorkflowSaveRequest request = new WorkflowSaveRequest();
        request.setName("port-workflow");
        ObjectNode source = node(new NodeCase("FUNC_NODE", "START"));
        source.put("name", "source");
        source.withArray("internalVariables").addObject()
                .put("name", "value")
                .put("dataType", "DOUBLE");
        source.withArray("ports").addObject()
                .put("name", "valueOut")
                .put("direction", "OUT")
                .put("internalVariableName", "value");
        ObjectNode target = node(new NodeCase("FUNC_NODE", "END"));
        target.put("name", "target");
        target.withArray("internalVariables").addObject()
                .put("name", "value")
                .put("dataType", "DOUBLE");
        target.withArray("ports").addObject()
                .put("name", "valueIn")
                .put("direction", "IN")
                .put("internalVariableName", "value");
        request.setNodesDef(JsonNodeSupport.arrayNode().add(source).add(target));
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
        normalize(request);
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
        normalize(request);
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

    private void normalize(WorkflowSaveRequest request) { for (JsonNode n : request.getNodesDef()) { ObjectNode node=(ObjectNode)n; String type=node.path("nodeType").asText(); String function=node.path("functionType").isTextual()?node.path("functionType").asText():null; node.set("lifecycle", WorkflowNodeSystemContract.template(type,function).path("lifecycle")); } }

    private void removeNamedItem(ArrayNode items, String name) {
        for (int index = 0; index < items.size(); index++) {
            if (name.equals(items.get(index).path("name").asText())) {
                items.remove(index);
                return;
            }
        }
    }
}
