package com.smartlab.global.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.global.util.JsonNodeSupport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaContractConformanceTest {

    @Test
    void protocolEnumsMatchExecutableProtocolContract() throws Exception {
        JsonNode protocol = resource("协议规范.json");
        for (String name : List.of(
                "DataType", "CommunicationProtocol", "WorkflowNodeSignal",
                "WorkflowControlSignal", "ManualControlSignal",
                "ConstraintControlSignal", "AdapterOutboundSignal", "StatusSignal")) {
            assertEquals(ProtocolContract.enumValues(name), enumValues(protocol, name), name);
        }
    }

    @Test
    void systemDefinitionsAndInterfacesMatchExecutableContract() throws Exception {
        JsonNode spec = resource("系统执行规范.json");
        assertEquals(
                Arrays.stream(NodeLifecycleState.values()).map(Enum::name).toList(),
                enumValues(spec, "NodeLifecycleState"));
        assertEquals(
                Arrays.stream(TaskLifecycleState.values()).map(Enum::name).toList(),
                enumValues(spec, "TaskLifecycleState"));
        assertEquals(
                Arrays.stream(ConstraintOperator.values()).map(ConstraintOperator::value).toList(),
                enumValues(spec, "ConstraintOperator"));
        assertEquals(
                SystemExecutionContract.commandStateNames(),
                textValues(spec.path("stateMachineEngine").path("commandStateNames")));

        assertStandardInterfaces(
                spec.path("stateMachineEngine").path("standardInterfaces"),
                SystemExecutionContract.stateMachineInterfaces(List.of()));
        assertNodeInterfaces(
                spec.path("workflowEngine").path("standardNodeInterfaces"),
                SystemExecutionContract.workflowNodeInterfaces());
    }

    @Test
    void fixedSystemTransitionsMatchExecutableContract() throws Exception {
        JsonNode transitions = resource("系统执行规范.json")
                .path("stateMachineEngine").path("systemTransitions");
        List<String> schemaTransitions = StreamSupport.stream(transitions.spliterator(), false)
                .filter(item -> "CMD".equals(item.path("stateSpace").asText()))
                .filter(item -> !item.path("trigger").path("signalName").asText().startsWith("<"))
                .filter(item -> item.path("actions").isArray() && !item.path("actions").isEmpty())
                .map(this::normalizeTransition)
                .toList();
        List<String> contractTransitions = SystemExecutionContract.stateMachineSystemTransitions().stream()
                .map(this::normalizeTransition)
                .toList();

        assertEquals(contractTransitions, schemaTransitions, "固定系统转移");
    }

    @Test
    void workflowInterfaceSnapshotDefinitionsUseCanonicalCurrentValueShape() throws Exception {
        JsonNode definitions = resource("系统执行规范.json").path("definitions");
        JsonNode item = definitions.path("WorkflowInterfaceSnapshotItem");
        JsonNode snapshot = definitions.path("WorkflowInterfaceSnapshot");

        assertEquals("object", item.path("type").asText());
        assertEquals(List.of("interfaceName", "signalName"), textValues(item.path("required")));
        assertEquals("string", item.path("properties").path("interfaceName").path("type").asText());
        assertEquals(List.of("string", "null"),
                textValues(item.path("properties").path("signalName").path("type")));
        assertEquals("object", item.path("properties").path("payload").path("type").asText());
        assertEquals("array", snapshot.path("type").asText());
        assertEquals("#/definitions/WorkflowInterfaceSnapshotItem",
                snapshot.path("items").path("$ref").asText());
    }

    private JsonNode resource(String name) throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/schemas/" + name)) {
            assertNotNull(input, "缺少Schema资源: " + name);
            return JsonNodeSupport.MAPPER.readTree(input);
        }
    }

    private List<String> enumValues(JsonNode root, String definitionName) {
        return StreamSupport.stream(
                        root.path("definitions").path(definitionName).path("enum").spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }

    private List<String> textValues(JsonNode values) {
        return StreamSupport.stream(values.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }

    private void assertStandardInterfaces(JsonNode schemaInterfaces,
                                          List<SystemExecutionContract.InterfaceDefinition> contractInterfaces) {
        assertEquals(contractInterfaces.size(), schemaInterfaces.size(), "标准接口数量");
        for (int i = 0; i < contractInterfaces.size(); i++) {
            JsonNode actual = schemaInterfaces.get(i);
            SystemExecutionContract.InterfaceDefinition expected = contractInterfaces.get(i);
            assertInterface(actual, expected.name(), expected.direction(), expected.interfaceType(), expected.allowedSignals());
        }
    }

    private void assertNodeInterfaces(JsonNode schemaInterfaces,
                                      List<SystemExecutionContract.NodeInterfaceDefinition> contractInterfaces) {
        assertEquals(contractInterfaces.size(), schemaInterfaces.size(), "标准节点接口数量");
        for (int i = 0; i < contractInterfaces.size(); i++) {
            JsonNode actual = schemaInterfaces.get(i);
            SystemExecutionContract.NodeInterfaceDefinition expected = contractInterfaces.get(i);
            assertInterface(actual, expected.name(), expected.direction(), expected.interfaceType(), expected.allowedSignals());
        }
    }

    private void assertInterface(JsonNode actual, String name, String direction,
                                 InterfaceType interfaceType, List<String> allowedSignals) {
        assertEquals(name, actual.path("name").asText(), name + ".name");
        assertEquals(direction, actual.path("direction").asText(), name + ".direction");
        assertEquals(interfaceType.name(), actual.path("interfaceType").asText(), name + ".interfaceType");
        if ("Interface_adapter_in".equals(name)) {
            assertTrue(actual.has("allowedSignals"), name + ".allowedSignals must exist");
            assertTrue(actual.path("allowedSignals").isArray(), name + ".allowedSignals must be an array");
            assertTrue(!actual.has("allowedSignalsRef"), name + ".allowedSignalsRef must not exist");
            assertEquals(List.of(), textValues(actual.path("allowedSignals")), name + ".allowedSignals");
        } else {
            assertTrue(actual.has("allowedSignalsRef"), name + ".allowedSignalsRef must exist");
            assertTrue(actual.path("allowedSignalsRef").isTextual(), name + ".allowedSignalsRef must be text");
            String definitionName = definitionNameForSignals(allowedSignals);
            String expectedRef = "协议规范.json#/definitions/" + definitionName;
            assertEquals(expectedRef, actual.path("allowedSignalsRef").asText(), name + ".allowedSignalsRef");
            assertEquals(ProtocolContract.enumValues(definitionName), allowedSignals, name + ".allowedSignalsRef");
        }
    }

    private String definitionNameForSignals(List<String> allowedSignals) {
        return List.of("WorkflowNodeSignal", "WorkflowControlSignal", "ManualControlSignal",
                        "ConstraintControlSignal", "AdapterOutboundSignal", "StatusSignal")
                .stream()
                .filter(name -> ProtocolContract.enumValues(name).equals(allowedSignals))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到标准信号定义: " + allowedSignals));
    }

    private String normalizeTransition(JsonNode item) {
        JsonNode actions = item.path("actions");
        assertTrue(actions.isArray() && actions.size() == 1, "system transition actions must contain exactly one action");
        JsonNode action = actions.get(0);
        assertEquals("SEND", action.path("actionName").asText(), "system transition actionName");
        JsonNode payload = action.path("payload");
        assertTrue(payload.isObject(), "system transition action payload must be an object");
        assertTrue(payload.path("interfaceName").isTextual(), "system transition action interfaceName must be text");
        assertTrue(payload.path("signalName").isTextual(), "system transition action signalName must be text");
        return String.join("|",
                item.path("stateSpace").asText(),
                item.path("fromStateName").asText(),
                item.path("toStateName").asText(),
                item.path("trigger").path("interfaceName").asText(),
                item.path("trigger").path("signalName").asText(),
                payload.path("interfaceName").asText(),
                payload.path("signalName").asText());
    }

    private String normalizeTransition(SystemExecutionContract.SystemTransitionDefinition item) {
        return String.join("|", item.stateSpace(), item.fromStateName(), item.toStateName(),
                item.triggerInterfaceName(), item.triggerSignalName(), item.actionInterfaceName(), item.actionSignalName());
    }
}
