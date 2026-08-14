package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.contract.WorkflowNodeSystemContract;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowSaveRequest;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowDefinitionCompilerTest {
    private final WorkflowDefinitionCompiler compiler = new WorkflowDefinitionCompiler();

    @Test
    void compilesCanonicalDeviceDefinition() {
        var compiled = compiler.compile(requestWith(new NodeCase("DEV_NODE", null)));

        assertEquals(3, compiled.nodes().size());
        assertEquals(1, compiled.outgoing(2).size());
    }

    @Test
    void acceptsInlineConstantUpdateTriggerOnOutputInterface() {
        ObjectNode aggregate = node(new NodeCase("FUNC_NODE", "AGGREGATE"));
        aggregate.withArray("internalVariables").addObject()
                .put("name", "ready")
                .put("dataType", "BOOLEAN");
        aggregate.putArray("actions").add("EMIT").add("UPDATE");
        ObjectNode output = interfaceNamed(aggregate, "Interface_workflow_out");
        ObjectNode trigger = output.withArray("bindingTriggers").addObject();
        trigger.putObject("condition")
                .put("object", "ready")
                .put("operator", "=")
                .put("threshold", true);
        ObjectNode action = trigger.putObject("action");
        action.put("actionName", "UPDATE");
        action.putObject("payload")
                .put("updateType", "INTERNAL_VARIABLE")
                .put("targetName", "ready")
                .put("value", false);

        WorkflowDefinitionCompiler.CompiledWorkflow compiled = compiler.compile(requestWith(aggregate));
        JsonNode compiledAggregate = compiled.nodes().values().stream()
                .filter(item -> "candidate".equals(item.path("name").asText()))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("UPDATE", "EMIT"), JsonNodeSupport.MAPPER.convertValue(
                compiledAggregate.path("actions"), List.class));
        assertEquals("UPDATE", findTriggerByUpdateTarget(compiledAggregate, "ready")
                .path("action").path("actionName").asText());
    }

    @Test
    void preservesUserDefinedNWayBranchOutputsWithoutRestoringTrueFalseInterfaces() throws Exception {
        WorkflowSaveRequest request = validBranchDefinition();
        ObjectNode branch = (ObjectNode) request.getNodesDef().get(1);
        branch.put("expression", "result = temperature * 100");
        branch.putArray("actions").add("EMIT");
        ArrayNode interfaces = branch.putArray("interfaces");
        interfaces.add(workflowInterface("Interface_workflow_in", "IN"));
        int threshold = 0;
        for (String name : List.of("low", "normal", "high")) {
            ObjectNode output = workflowInterface(name, "OUT");
            ObjectNode trigger = output.withArray("bindingTriggers").addObject();
            trigger.putObject("condition")
                    .put("object", "result")
                    .put("operator", "=")
                    .put("threshold", threshold++);
            trigger.putObject("action").put("actionName", "EMIT").putObject("payload")
                    .put("targetInterfaceName", name).put("signalName", "ACTIVE");
            interfaces.add(output);
        }
        request.setInterfaceConnections(JsonNodeSupport.MAPPER.readTree("""
                [
                  {"connectionType":"NODE_TO_NODE","source":{"nodeName":"start","interfaceName":"Interface_workflow_out"},"target":{"nodeName":"candidate","interfaceName":"Interface_workflow_in"}},
                  {"connectionType":"NODE_TO_NODE","source":{"nodeName":"candidate","interfaceName":"low"},"target":{"nodeName":"end","interfaceName":"Interface_workflow_in"}}
                ]
                """));

        WorkflowDefinitionCompiler.CompiledWorkflow compiled = compiler.compile(request);
        JsonNode compiledBranch = compiled.nodes().values().stream()
                .filter(item -> "candidate".equals(item.path("name").asText()))
                .findFirst().orElseThrow();
        assertEquals(List.of("Interface_workflow_in", "low", "normal", "high"),
                JsonNodeSupport.MAPPER.convertValue(compiledBranch.path("interfaces").findValues("name"), List.class));
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
                .put("name", "counter")
                .put("dataType", "INTEGER");
        aggregate.withArray("actions").addObject()
                .put("actionName", "setPayload")
                .put("actionType", "UPDATE")
                .put("internalVariableName", "counter")
                .put("valueExpression", "1");
        ((ObjectNode) aggregate.withArray("interfaces").get(0)).withArray("bindingTriggers").addObject()
                .put("action", "setPayload")
                .putObject("condition")
                .put("object", "inputSignalName")
                .put("operator", "=")
                .put("threshold", "OTHER");

        assertDoesNotThrow(() -> compiler.compile(requestWith(aggregate)));
    }

    @Test
    void acceptsAdditionalEmitTriggerOnAnyInterface() {
        ObjectNode aggregate = node(new NodeCase("FUNC_NODE", "AGGREGATE"));
        ObjectNode trigger = ((ObjectNode) aggregate.withArray("interfaces").get(1))
                .withArray("bindingTriggers").addObject();
        trigger.putObject("action")
                .put("actionName", "EMIT")
                .putObject("payload")
                .put("targetInterfaceName", "Interface_workflow_out")
                .put("signalName", "ACTIVE");
        trigger.putObject("condition")
                .put("object", "inputSignalName")
                .put("operator", "=")
                .put("threshold", "OTHER");

        assertDoesNotThrow(() -> compiler.compile(requestWith(aggregate)));
    }

    @Test
    void acceptsMultipleInlineEmitTriggersWithTheSameActionName() {
        ObjectNode aggregate = node(new NodeCase("FUNC_NODE", "AGGREGATE"));
        aggregate.putArray("actions").add("EMIT");
        ObjectNode output = interfaceNamed(aggregate, "Interface_workflow_out");
        for (String object : List.of("readyA", "readyB")) {
            aggregate.withArray("internalVariables").addObject().put("name", object).put("dataType", "BOOLEAN");
            ObjectNode trigger = output.withArray("bindingTriggers").addObject();
            trigger.putObject("condition").put("object", object).put("operator", "=").put("threshold", true);
            trigger.putObject("action").put("actionName", "EMIT").putObject("payload")
                    .put("targetInterfaceName", "Interface_workflow_out").put("signalName", "ACTIVE");
        }

        assertDoesNotThrow(() -> compiler.compile(requestWith(aggregate)));
    }

    @Test
    void rejectsEmitToInputInterface() throws Exception {
        WorkflowSaveRequest request = validDefinition();
        ObjectNode end = (ObjectNode) request.getNodesDef().get(1);
        end.putArray("actions").add("EMIT");
        ObjectNode trigger = interfaceNamed(end, "Interface_workflow_in")
                .withArray("bindingTriggers").addObject();
        trigger.putObject("condition").put("object", "inputSignalName").put("operator", "=").put("threshold", "ACTIVE");
        trigger.putObject("action").put("actionName", "EMIT").putObject("payload")
                .put("targetInterfaceName", "Interface_workflow_in").put("signalName", "ACTIVE");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> compiler.compile(request));
        assertTrue(error.getMessage().contains("EMIT"));
        assertTrue(error.getMessage().contains("OUT接口"));
    }

    @Test
    void rejectsEmitWhoseTargetDiffersFromTriggerInterface() {
        ObjectNode aggregate = node(new NodeCase("FUNC_NODE", "AGGREGATE"));
        ObjectNode host = interfaceNamed(aggregate, "Interface_workflow_out");
        ObjectNode otherOutput = workflowInterface("other_out", "OUT");
        aggregate.withArray("interfaces").add(otherOutput);
        ObjectNode trigger = host.withArray("bindingTriggers").addObject();
        trigger.putObject("condition")
                .put("object", "nodeLifecycleState")
                .put("operator", "=")
                .put("threshold", "RUNNING");
        trigger.putObject("action")
                .put("actionName", "EMIT")
                .putObject("payload")
                .put("targetInterfaceName", "other_out")
                .put("signalName", "ACTIVE");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(requestWith(aggregate)));

        assertTrue(error.getMessage().contains("触发器所在接口"));
        assertTrue(error.getMessage().contains("Interface_workflow_out"));
    }

    @Test
    void acceptsNumericWorkflowCalculationExpression() {
        ObjectNode branch = node(new NodeCase("FUNC_NODE", "BRANCH"));
        addBranchOutput(branch);
        branch.withArray("internalVariables").addObject()
                .put("name", "temperature")
                .put("dataType", "DOUBLE");
        branch.withArray("internalVariables").addObject()
                .put("name", "scaled")
                .put("dataType", "DOUBLE");
        branch.put("expression", "scaled = temperature * 100");

        assertDoesNotThrow(() -> compiler.compile(requestWith(branch)));
    }

    @Test
    void acceptsBranchWithoutOptionalCalculationExpression() {
        ObjectNode branch = node(new NodeCase("FUNC_NODE", "BRANCH"));
        branch.remove("expression");
        addBranchOutput(branch);

        assertDoesNotThrow(() -> compiler.compile(requestWith(branch)));
    }

    @Test
    void acceptsUnicodeVariableNamesInWorkflowCalculationExpression() {
        ObjectNode branch = node(new NodeCase("FUNC_NODE", "BRANCH"));
        addBranchOutput(branch);
        branch.withArray("internalVariables").addObject()
                .put("name", "温度输入")
                .put("dataType", "DOUBLE");
        branch.withArray("internalVariables").addObject()
                .put("name", "温度变量")
                .put("dataType", "DOUBLE");
        branch.put("expression", "温度变量 = 温度输入 / 10");

        assertDoesNotThrow(() -> compiler.compile(requestWith(branch)));
    }

    @Test
    void rejectsWorkflowCalculationExpressionWithUnknownVariable() {
        ObjectNode branch = node(new NodeCase("FUNC_NODE", "BRANCH"));
        addBranchOutput(branch);
        branch.withArray("internalVariables").addObject()
                .put("name", "scaled")
                .put("dataType", "DOUBLE");
        branch.put("expression", "scaled = temperature * 100");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(requestWith(branch)));

        assertTrue(error.getMessage().contains("未声明的数值变量"));
        assertTrue(error.getMessage().contains("temperature"));
    }

    @Test
    void acceptsLaboratoryTemporalFunctionInWorkflowCalculationExpression() {
        ObjectNode branch = node(new NodeCase("FUNC_NODE", "BRANCH"));
        addBranchOutput(branch);
        branch.withArray("internalVariables").addObject()
                .put("name", "temperature")
                .put("dataType", "DOUBLE");
        branch.withArray("internalVariables").addObject()
                .put("name", "temperatureRate")
                .put("dataType", "DOUBLE");
        branch.put("expression", "temperatureRate = abs(rate(temperature, 10))");

        assertDoesNotThrow(() -> compiler.compile(requestWith(branch)));
    }

    @Test
    void rejectsWorkflowExpressionWithoutAssignmentTarget() {
        ObjectNode branch = node(new NodeCase("FUNC_NODE", "BRANCH"));
        addBranchOutput(branch);
        branch.withArray("internalVariables").addObject()
                .put("name", "temperature")
                .put("dataType", "DOUBLE");
        branch.put("expression", "temperature * 100");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(requestWith(branch)));

        assertTrue(error.getMessage().contains("赋值"));
    }

    @Test
    void reportsUndeclaredWorkflowAssignmentTargetPrecisely() {
        ObjectNode branch = node(new NodeCase("FUNC_NODE", "BRANCH"));
        addBranchOutput(branch);
        branch.withArray("internalVariables").addObject()
                .put("name", "temperature")
                .put("dataType", "DOUBLE");
        branch.put("expression", "missing = temperature / 2");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(requestWith(branch)));

        assertTrue(error.getMessage().contains("赋值目标未在变量空间中声明：missing"));
    }

    @Test
    void reportsNonNumericWorkflowAssignmentTargetPrecisely() {
        ObjectNode branch = node(new NodeCase("FUNC_NODE", "BRANCH"));
        addBranchOutput(branch);
        branch.withArray("internalVariables").addObject()
                .put("name", "temperature")
                .put("dataType", "DOUBLE");
        branch.withArray("internalVariables").addObject()
                .put("name", "statusText")
                .put("dataType", "STRING");
        branch.put("expression", "statusText = temperature / 2");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(requestWith(branch)));

        assertTrue(error.getMessage().contains("赋值目标必须是数值类型（INTEGER 或 DOUBLE）：statusText"));
    }

    @Test
    void rejectsUnknownVariableInUpdateValueExpression() {
        ObjectNode aggregate = node(new NodeCase("FUNC_NODE", "AGGREGATE"));
        aggregate.withArray("internalVariables").addObject().put("name", "temperature").put("dataType", "DOUBLE");
        ObjectNode output = interfaceNamed(aggregate, "Interface_workflow_out");
        ObjectNode trigger = output.withArray("bindingTriggers").addObject();
        trigger.putObject("condition").put("object", "temperature").put("operator", ">").put("threshold", 100);
        trigger.putObject("action").put("actionName", "UPDATE").putObject("payload")
                .put("updateType", "INTERNAL_VARIABLE")
                .put("targetName", "temperature")
                .put("valueExpression", "unknown / 100");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> compiler.compile(requestWith(aggregate)));

        assertTrue(error.getMessage().contains("未声明的数值变量"));
    }

    @Test
    void acceptsOneLevelAndTriggerConditionGroup() {
        ObjectNode aggregate = node(new NodeCase("FUNC_NODE", "AGGREGATE"));
        aggregate.withArray("internalVariables").addObject()
                .put("name", "temperature")
                .put("dataType", "DOUBLE");
        ObjectNode output = interfaceNamed(aggregate, "Interface_workflow_out");
        ObjectNode trigger = output.withArray("bindingTriggers").addObject();
        ObjectNode condition = trigger.putObject("condition");
        condition.put("logic", "AND");
        condition.putArray("conditions")
                .addObject().put("object", "nodeLifecycleState").put("operator", "=").put("threshold", "RUNNING");
        condition.withArray("conditions")
                .addObject().put("object", "temperature").put("operator", ">").put("threshold", 100);
        trigger.putObject("action").put("actionName", "EMIT").putObject("payload")
                .put("targetInterfaceName", "Interface_workflow_out").put("signalName", "ACTIVE");

        assertDoesNotThrow(() -> compiler.compile(requestWith(aggregate)));
    }

    @Test
    void rejectsUnsupportedOrTriggerConditionGroup() {
        ObjectNode aggregate = node(new NodeCase("FUNC_NODE", "AGGREGATE"));
        ObjectNode trigger = interfaceNamed(aggregate, "Interface_workflow_out")
                .withArray("bindingTriggers").addObject();
        ObjectNode group = JsonNodeSupport.objectNode();
        group.put("logic", "OR");
        var conditions = group.putArray("conditions");
        conditions
                .addObject().put("object", "nodeLifecycleState").put("operator", "=").put("threshold", "RUNNING");
        conditions
                .addObject().put("object", "nodeLifecycleState").put("operator", "=").put("threshold", "SUCCEEDED");
        trigger.set("condition", group);
        trigger.putObject("action").put("actionName", "EMIT").putObject("payload")
                .put("targetInterfaceName", "Interface_workflow_out").put("signalName", "ACTIVE");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> compiler.compile(requestWith(aggregate)));

        assertTrue(error.getMessage().contains("只支持AND"));
    }

    @Test
    void rejectsNestedTriggerConditionGroup() {
        ObjectNode aggregate = node(new NodeCase("FUNC_NODE", "AGGREGATE"));
        ObjectNode trigger = interfaceNamed(aggregate, "Interface_workflow_out")
                .withArray("bindingTriggers").addObject();
        ObjectNode outer = JsonNodeSupport.objectNode();
        outer.put("logic", "AND");
        var conditions = outer.putArray("conditions");
        ObjectNode nested = conditions.addObject();
        nested.put("logic", "AND");
        nested.putArray("conditions")
                .addObject().put("object", "nodeLifecycleState").put("operator", "=").put("threshold", "RUNNING");
        conditions.addObject().put("object", "nodeLifecycleState").put("operator", "=").put("threshold", "RUNNING");
        trigger.set("condition", outer);
        trigger.putObject("action").put("actionName", "EMIT").putObject("payload")
                .put("targetInterfaceName", "Interface_workflow_out").put("signalName", "ACTIVE");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> compiler.compile(requestWith(aggregate)));

        assertTrue(error.getMessage().contains("不支持嵌套"));
    }

    @Test
    void rejectsProtocolSignalThatDoesNotMatchInterfaceKind() {
        ObjectNode aggregate = node(new NodeCase("FUNC_NODE", "AGGREGATE"));
        ObjectNode customOutput = workflowInterface("custom_out", "OUT");
        customOutput.putArray("allowedSignals").add("WF_EXECUTE_ABORT");
        aggregate.withArray("interfaces").add(customOutput);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> compiler.compile(requestWith(aggregate)));

        assertTrue(error.getMessage().contains("WORKFLOW OUT接口信号不合法"));
    }

    @Test void compilerAcceptsEveryBackendSystemTemplate() { for (NodeCase c : cases()) assertDoesNotThrow(() -> compiler.compile(requestWith(c)), c.toString()); }

    @Test void compilerRejectsTamperedBackendSystemItem() { ObjectNode n = node(new NodeCase("DEV_NODE", null)); ((ObjectNode)n.withArray("interfaces").get(1)).putArray("allowedSignals").add("UNKNOWN"); assertThrows(IllegalArgumentException.class, () -> compiler.compile(requestWith(n))); }

    private List<NodeCase> cases() { return List.of(new NodeCase("FUNC_NODE", "START"), new NodeCase("FUNC_NODE", "END"), new NodeCase("FUNC_NODE", "BRANCH"), new NodeCase("FUNC_NODE", "AGGREGATE"), new NodeCase("DEV_NODE", null), new NodeCase("SUBFLOW_NODE", null)); }
    @Test
    void draftPreparationRestoresSystemContractAndPreservesBusinessItems() {
        WorkflowSaveRequest request = markerlessAggregateWithCustomUpdate();

        WorkflowPreparation result = compiler.prepare(request, WorkflowPreparation.Mode.DRAFT);

        JsonNode node = result.normalized().getNodesDef().get(1);
        assertEquals("aggregate.lifecycle", node.path("lifecycle").path("_systemKey").asText());
        assertEquals(List.of("UPDATE", "EMIT"), JsonNodeSupport.MAPPER.convertValue(
                node.path("actions"), List.class));
        assertEquals("counter", node.path("internalVariables").get(0).path("name").asText());
        assertEquals("counterOut", node.path("ports").get(0).path("name").asText());
        JsonNode customTrigger = findTriggerByUpdateTarget(node, "counter");
        assertEquals("UPDATE", customTrigger.path("action").path("actionName").asText());
        assertEquals("counter", customTrigger.path("action").path("payload").path("targetName").asText());
        assertTrue(result.issues().stream().noneMatch(WorkflowIssue::blocking), result.issues().toString());
    }

    @Test
    void publishPreparationNormalizesLegacyActionNameToUpdateCapability() {
        WorkflowSaveRequest request = definitionWithCustomActionNamed("emitActive");

        WorkflowPreparation result = compiler.prepare(request, WorkflowPreparation.Mode.PUBLISH);

        assertTrue(result.issues().stream().noneMatch(WorkflowIssue::blocking), result.issues().toString());
        result.normalized().getNodesDef().get(1).path("actions")
                .forEach(action -> assertTrue(action.isTextual()));
        assertTrue(JsonNodeSupport.MAPPER.convertValue(
                result.normalized().getNodesDef().get(1).path("actions"), List.class).contains("UPDATE"));
    }

    private ObjectNode node(NodeCase c) { ObjectNode n=WorkflowNodeSystemContract.template(c.type(),c.function()).deepCopy(); n.put("name","candidate"); n.put("nodeType",c.type()); n.putArray("internalVariables"); n.putArray("ports"); if(c.function()!=null)n.put("functionType",c.function()); if("BRANCH".equals(c.function())){n.withArray("internalVariables").addObject().put("name","result").put("dataType","DOUBLE");n.put("expression","result = 1");} if("DEV_NODE".equals(c.type())){n.put("deviceModelId",1);n.putObject("capability").put("capabilityName","mix");} if("SUBFLOW_NODE".equals(c.type()))n.put("subFlowModelId",2); return n; }
    private void addBranchOutput(ObjectNode branch) {
        branch.withArray("actions").add("EMIT");
        ObjectNode output = workflowInterface("branch_out", "OUT");
        ObjectNode trigger = output.withArray("bindingTriggers").addObject();
        trigger.putObject("condition").put("object", "result").put("operator", ">=").put("threshold", 0);
        trigger.putObject("action").put("actionName", "EMIT").putObject("payload")
                .put("targetInterfaceName", "branch_out").put("signalName", "ACTIVE");
        branch.withArray("interfaces").add(output);
    }
    private WorkflowSaveRequest requestWith(NodeCase c) {
        ObjectNode candidate = node(c);
        if ("BRANCH".equals(c.function())) {
            candidate.putArray("actions").add("EMIT");
            ObjectNode output = workflowInterface("branch_out", "OUT");
            ObjectNode trigger = output.withArray("bindingTriggers").addObject();
            trigger.putObject("condition").put("object", "result").put("operator", ">=").put("threshold", 0);
            trigger.putObject("action").put("actionName", "EMIT").putObject("payload")
                    .put("targetInterfaceName", "branch_out").put("signalName", "ACTIVE");
            candidate.withArray("interfaces").add(output);
        }
        return requestWith(candidate);
    }
    private WorkflowSaveRequest requestWith(ObjectNode n) { WorkflowSaveRequest r=new WorkflowSaveRequest(); r.setName("contract"); ObjectNode s=node(new NodeCase("FUNC_NODE","START")); s.put("name","start"); ObjectNode e=node(new NodeCase("FUNC_NODE","END")); e.put("name","end"); ArrayNode ns=JsonNodeSupport.arrayNode().add(s).add(n).add(e); ArrayNode cs=JsonNodeSupport.arrayNode(); if("START".equals(n.path("functionType").asText())||"END".equals(n.path("functionType").asText())){ns.remove(1); link(cs,"start","end");} else if ("AGGREGATE".equals(n.path("functionType").asText())) { ObjectNode relay=node(new NodeCase("SUBFLOW_NODE",null)); relay.put("name","relay"); ns.insert(1,relay); link(cs,"start","candidate"); link(cs,"start","relay"); link(cs,"relay","candidate"); link(cs,"candidate","end"); } else {link(cs,"start","candidate"); if ("BRANCH".equals(n.path("functionType").asText())) linkNamed(cs,"candidate","branch_out","end"); else link(cs,"candidate","end");} if ("DEV_NODE".equals(n.path("nodeType").asText())) { ObjectNode x=cs.addObject(); x.put("connectionType","NODE_TO_DEVICE"); x.putObject("source").put("nodeName","candidate").put("interfaceName","Interface_state_out"); x.putObject("target").put("deviceModelId",1).put("interfaceName","Interface_workflow_in"); x=cs.addObject(); x.put("connectionType","DEVICE_TO_NODE"); x.putObject("source").put("deviceModelId",1).put("interfaceName","Interface_state_out"); x.putObject("target").put("nodeName","candidate").put("interfaceName","Interface_state_in"); } r.setNodesDef(ns);r.setInterfaceConnections(cs);r.setPortConnections(JsonNodeSupport.arrayNode());return r; }
    private void linkNamed(ArrayNode cs,String from,String sourceInterface,String to){ObjectNode c=cs.addObject();c.put("connectionType","NODE_TO_NODE");c.putObject("source").put("nodeName",from).put("interfaceName",sourceInterface);c.putObject("target").put("nodeName",to).put("interfaceName","Interface_workflow_in");}
        private void link(ArrayNode cs,String from,String to){ObjectNode c=cs.addObject();c.put("connectionType","NODE_TO_NODE");c.putObject("source").put("nodeName",from).put("interfaceName","Interface_workflow_out");c.putObject("target").put("nodeName",to).put("interfaceName","Interface_workflow_in");}
    private record NodeCase(String type,String function) {}


    private WorkflowSaveRequest markerlessAggregateWithCustomUpdate() {
        ObjectNode aggregate = node(new NodeCase("FUNC_NODE", "AGGREGATE"));
        aggregate.withArray("internalVariables").addObject()
                .put("name", "counter")
                .put("dataType", "INTEGER");
        aggregate.withArray("ports").addObject()
                .put("name", "counterOut")
                .put("direction", "OUT")
                .put("internalVariableName", "counter");
        aggregate.withArray("actions").addObject()
                .put("actionName", "setCounter")
                .put("actionType", "UPDATE")
                .put("internalVariableName", "counter")
                .put("valueExpression", "1");
        ((ObjectNode) aggregate.withArray("interfaces").get(0)).withArray("bindingTriggers").addObject()
                .put("action", "setCounter")
                .putObject("condition")
                .put("object", "inputSignalName")
                .put("operator", "=")
                .put("threshold", "OTHER");
        removeSystemMarkers(aggregate);
        ((ObjectNode) aggregate.path("lifecycle")).put("initialStateName", "BROKEN");

        WorkflowSaveRequest request = requestWith(aggregate);
        ArrayNode nodes = (ArrayNode) request.getNodesDef();
        JsonNode relay = nodes.remove(1);
        nodes.insert(2, relay);
        return request;
    }

    private WorkflowSaveRequest definitionWithCustomActionNamed(String actionName) {
        WorkflowSaveRequest request = markerlessAggregateWithCustomUpdate();
        ObjectNode aggregate = (ObjectNode) request.getNodesDef().get(1);
        ((ArrayNode) aggregate.path("actions")).removeAll();
        aggregate.withArray("actions").addObject()
                .put("actionName", actionName)
                .put("actionType", "UPDATE")
                .put("internalVariableName", "counter")
                .put("valueExpression", "1");
        aggregate.path("interfaces").forEach(interfaceNode ->
                interfaceNode.path("bindingTriggers").forEach(trigger -> {
                    if ("setCounter".equals(trigger.path("action").asText())) {
                        ((ObjectNode) trigger).put("action", actionName);
                    }
                }));
        return request;
    }

    private void removeSystemMarkers(JsonNode node) {
        if (node.isObject()) {
            ((ObjectNode) node).remove(List.of("_system", "_systemKey"));
            node.elements().forEachRemaining(this::removeSystemMarkers);
        } else if (node.isArray()) {
            node.elements().forEachRemaining(this::removeSystemMarkers);
        }
    }

    private JsonNode findTriggerByUpdateTarget(JsonNode node, String targetName) {
        for (JsonNode interfaceNode : node.path("interfaces")) {
            for (JsonNode trigger : interfaceNode.path("bindingTriggers")) {
                if (targetName.equals(trigger.path("action").path("payload").path("targetName").asText())) {
                    return trigger;
                }
            }
        }
        throw new AssertionError("missing UPDATE target=" + targetName);
    }
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
        ObjectNode branch = node(new NodeCase("FUNC_NODE", "BRANCH"));
        branch.withArray("internalVariables").addObject()
                .put("name", "temperature")
                .put("dataType", "DOUBLE");
        return requestWith(branch);
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

    private ObjectNode interfaceNamed(ObjectNode node, String name) {
        for (JsonNode item : node.withArray("interfaces")) {
            if (name.equals(item.path("name").asText())) return (ObjectNode) item;
        }
        throw new IllegalArgumentException("接口不存在: " + name);
    }

    private ObjectNode workflowInterface(String name, String direction) {
        ObjectNode item = JsonNodeSupport.objectNode();
        item.put("name", name);
        item.put("direction", direction);
        item.put("interfaceType", "WORKFLOW");
        item.putArray("allowedSignals").add("ACTIVE");
        item.putArray("bindingTriggers");
        return item;
    }
}
