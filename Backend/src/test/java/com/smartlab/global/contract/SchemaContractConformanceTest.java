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
        if (actual.has("allowedSignalsRef")) {
            String definitionName = actual.path("allowedSignalsRef").asText()
                    .substring(actual.path("allowedSignalsRef").asText().lastIndexOf('/') + 1);
            assertEquals(ProtocolContract.enumValues(definitionName), allowedSignals, name + ".allowedSignalsRef");
        } else {
            assertEquals(allowedSignals, textValues(actual.path("allowedSignals")), name + ".allowedSignals");
        }
    }

    private String normalizeTransition(JsonNode item) {
        JsonNode action = item.path("actions").get(0).path("payload");
        return String.join("|",
                item.path("stateSpace").asText(),
                item.path("fromStateName").asText(),
                item.path("toStateName").asText(),
                item.path("trigger").path("interfaceName").asText(),
                item.path("trigger").path("signalName").asText(),
                action.path("interfaceName").asText(),
                action.path("signalName").asText());
    }

    private String normalizeTransition(SystemExecutionContract.SystemTransitionDefinition item) {
        return String.join("|", item.stateSpace(), item.fromStateName(), item.toStateName(),
                item.triggerInterfaceName(), item.triggerSignalName(), item.actionInterfaceName(), item.actionSignalName());
    }
}
