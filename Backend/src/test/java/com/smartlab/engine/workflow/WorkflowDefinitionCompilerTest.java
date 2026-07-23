package com.smartlab.engine.workflow;

import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowSaveRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowDefinitionCompilerTest {

    private final com.smartlab.global.protocol.ProtocolDictionaryService protocol =
            new com.smartlab.global.protocol.ProtocolDictionaryService();
    private final WorkflowDefinitionCompiler compiler = new WorkflowDefinitionCompiler(
            protocol, new com.smartlab.global.schema.SchemaMetadataService(protocol));

    @Test
    void compilesExplicitNodeReferences() {
        WorkflowSaveRequest request = requestWithNodes(
                node(1, "FUNCTIONAL_NODE", "START"),
                node(2, "FUNCTIONAL_NODE", "END"));
        request.setInterfaceConnections(JsonNodeSupport.arrayNode().add(connection(1, "flow_out", 2, "flow_in")));

        var compiled = compiler.compile(request);

        assertEquals(1L, compiled.startNodeIdRef());
        assertEquals(2L, compiled.outgoing(1L).getFirst().targetNodeIdRef());
    }

    @Test
    void rejectsDuplicateStartsAndImplicitStringReferences() {
        WorkflowSaveRequest duplicate = requestWithNodes(
                node(1, "FUNCTIONAL_NODE", "START"),
                node(2, "FUNCTIONAL_NODE", "START"),
                node(3, "FUNCTIONAL_NODE", "END"));
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(duplicate));

        WorkflowSaveRequest implicit = requestWithNodes(
                node(1, "FUNCTIONAL_NODE", "START"),
                node(2, "FUNCTIONAL_NODE", "END"));
        var oldConnection = JsonNodeSupport.objectNode();
        oldConnection.putObject("source").put("interfaceRef", "1_flow_out");
        oldConnection.putObject("target").put("interfaceRef", "2_flow_in");
        implicit.setInterfaceConnections(JsonNodeSupport.arrayNode().add(oldConnection));
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(implicit));
    }

    @Test
    void requiresBranchOutcomesAndAggregateInputs() {
        var branch = node(2, "FUNCTIONAL_NODE", "BRANCH");
        branch.with("capability").putArray("branches")
                .addObject().put("interfaceName", "yes").put("isDefault", true);
        WorkflowSaveRequest request = requestWithNodes(
                node(1, "FUNCTIONAL_NODE", "START"), branch,
                node(3, "FUNCTIONAL_NODE", "AGGREGATE"),
                node(4, "FUNCTIONAL_NODE", "END"));
        request.setInterfaceConnections(JsonNodeSupport.arrayNode()
                .add(connection(1, "flow_out", 2, "flow_in"))
                .add(connection(2, "yes", 3, "flow_in"))
                .add(connection(3, "flow_out", 4, "flow_in")));

        assertThrows(IllegalArgumentException.class, () -> compiler.compile(request));
    }

    @Test
    void rejectsWorkflowActionsThatAreNotDeclaredByProtocol() {
        var start = node(1, "FUNCTIONAL_NODE", "START");
        start.putArray("actions").addObject()
                .put("actionName", "EXECUTE_LOGIC")
                .putObject("payload").put("functionType", "START");
        WorkflowSaveRequest request = requestWithNodes(
                start, node(2, "FUNCTIONAL_NODE", "END"));
        request.setInterfaceConnections(JsonNodeSupport.arrayNode()
                .add(connection(1, "flow_out", 2, "flow_in")));

        assertThrows(IllegalArgumentException.class, () -> compiler.compile(request));
    }

    @Test
    void acceptsOrderedActionChainAndRejectsWrongOrder() {
        var device = node(2, "DEVICE_CAPABILITY_NODE", "DEVICE");
        device.with("capability").put("deviceModelRef", 9).put("capabilityRef", "heat");
        var actions = device.withArray("actions");
        actions.addObject().put("actionName", "WAIT").putObject("payload").put("durationMs", 100);
        var assign = actions.addObject().put("actionName", "ASSIGN").putObject("payload");
        assign.put("target", "command.target");
        assign.putObject("source").put("kind", "LITERAL").put("value", 50);
        actions.addObject().put("actionName", "EMIT_SIGNAL").putObject("payload")
                .put("interfaceType", "WORKFLOW").put("signalName", "WF_EXECUTE_START");
        WorkflowSaveRequest valid = requestWithNodes(
                node(1, "FUNCTIONAL_NODE", "START"), device,
                node(3, "FUNCTIONAL_NODE", "END"));
        valid.setInterfaceConnections(JsonNodeSupport.arrayNode()
                .add(connection(1, "flow_out", 2, "flow_in"))
                .add(connection(2, "flow_out", 3, "flow_in")));

        assertEquals(2L, compiler.compile(valid).outgoing(1L).getFirst().targetNodeIdRef());

        var wrong = valid.getNodesDef().get(1).deepCopy();
        var reordered = JsonNodeSupport.arrayNode();
        reordered.add(wrong.path("actions").get(1));
        reordered.add(wrong.path("actions").get(0));
        reordered.add(wrong.path("actions").get(2));
        ((com.fasterxml.jackson.databind.node.ObjectNode) wrong).set("actions", reordered);
        valid.setNodesDef(JsonNodeSupport.arrayNode()
                .add(valid.getNodesDef().get(0)).add(wrong).add(valid.getNodesDef().get(2)));
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(valid));
    }

    private WorkflowSaveRequest requestWithNodes(com.fasterxml.jackson.databind.node.ObjectNode... nodes) {
        WorkflowSaveRequest request = new WorkflowSaveRequest();
        request.setName("test workflow");
        var values = JsonNodeSupport.arrayNode();
        for (var node : nodes) values.add(node);
        request.setNodesDef(values);
        request.setInterfaceConnections(JsonNodeSupport.arrayNode());
        request.setPortConnections(JsonNodeSupport.arrayNode());
        return request;
    }

    private com.fasterxml.jackson.databind.node.ObjectNode node(long ref, String type, String functionType) {
        var node = JsonNodeSupport.objectNode();
        node.put("nodeIdRef", ref);
        node.put("name", functionType.toLowerCase());
        node.put("nodeType", type);
        node.putObject("capability").put("functionType", functionType);
        node.set("internalVariables", JsonNodeSupport.arrayNode());
        node.set("lifecycle", JsonNodeSupport.objectNode());
        var interfaces = JsonNodeSupport.arrayNode();
        if (!"START".equals(functionType)) interfaces.addObject().put("name", "flow_in").put("direction", "IN");
        if (!"END".equals(functionType)) interfaces.addObject().put("name", "flow_out").put("direction", "OUT");
        node.set("interfaces", interfaces);
        node.set("ports", JsonNodeSupport.arrayNode());
        node.set("actions", JsonNodeSupport.arrayNode());
        return node;
    }

    private com.fasterxml.jackson.databind.node.ObjectNode connection(long source, String sourceInterface,
                                                                       long target, String targetInterface) {
        var connection = JsonNodeSupport.objectNode();
        connection.putObject("source").put("nodeIdRef", source).put("interfaceName", sourceInterface);
        connection.putObject("target").put("nodeIdRef", target).put("interfaceName", targetInterface);
        return connection;
    }
}
