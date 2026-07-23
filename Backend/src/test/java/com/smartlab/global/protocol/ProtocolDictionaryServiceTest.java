package com.smartlab.global.protocol;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolDictionaryServiceTest {

    private final ProtocolDictionaryService service = new ProtocolDictionaryService();

    @Test
    void readsEnumValuesFromProtocolDictionary() {
        assertEquals(List.of("INTEGER", "DOUBLE", "STRING", "BOOLEAN", "JSON"), service.enumValues("DataType"));
        assertEquals(List.of("MQTT"), service.enumValues("CommunicationProtocol"));
        assertEquals(List.of(">", "<", ">=", "<=", "=", "!=", "BETWEEN", "IN"),
                service.enumValues("ConstraintOperator"));
    }

    @Test
    void resolvesMqttTopicsFromProtocolConvention() {
        assertEquals("smartlab/adapter/ReactorAdapter/Reactor1/command",
                service.resolveMqttTopic("commandTopic", Map.of(
                        "adapterName", "ReactorAdapter",
                        "devicePoint", "Reactor1")));
        assertEquals("smartlab/adapter/ReactorAdapter/heartbeat",
                service.resolveMqttTopic("heartbeatTopic", Map.of("adapterName", "ReactorAdapter")));
    }

    @Test
    void matchesMqttTopicsAgainstProtocolConvention() {
        ProtocolTopicMatch match = service.matchMqttTopic("smartlab/adapter/ReactorAdapter/Reactor1/telemetry")
                .orElseThrow();

        assertEquals("telemetryTopic", match.topicName());
        assertEquals("ReactorAdapter", match.variables().get("adapterName"));
        assertEquals("Reactor1", match.variables().get("devicePoint"));
    }

    @Test
    void validatesMessageDefinitionRequiredFields() {
        ObjectNode command = JsonNodeSupport.objectNode();
        command.put("messageId", "msg-1");
        command.put("adapterName", "adapter1");
        command.put("devicePoint", "Reactor1");
        command.put("commandName", "heat");
        command.set("parameters", JsonNodeSupport.objectNode());
        command.put("timestamp", 1719892800L);

        service.validateDefinition("CommandMessageFormat", command);

        command.remove("commandName");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.validateDefinition("CommandMessageFormat", command));
        assertTrue(error.getMessage().contains("commandName"));
    }

    @Test
    void validatesStandardJsonSchemaKeywordsInsteadOfOnlyRequiredFields() {
        ObjectNode command = JsonNodeSupport.objectNode();
        command.put("messageId", "msg-1");
        command.put("adapterName", "");
        command.put("devicePoint", "Reactor1");
        command.put("commandName", "heat");
        command.set("parameters", JsonNodeSupport.objectNode());
        command.put("timestamp", 1719892800L);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.validateDefinition("CommandMessageFormat", command));

        assertTrue(error.getMessage().contains("adapterName"));
    }

    @Test
    void exposesProtocolSignalDefinitionsOnly() {
        assertEquals(List.of("WF_EXECUTE_START", "WF_EXECUTE_ABORT"),
                service.enumValues("WorkflowControlSignal"));
        assertEquals(List.of("MANUAL_EXECUTE_START", "MANUAL_EXECUTE_ABORT"),
                service.enumValues("ManualControlSignal"));
        assertEquals(List.of("CONSTRAINT_ABORT"), service.enumValues("ConstraintControlSignal"));
        assertEquals(List.of("CMD_START", "CMD_ABORT"), service.enumValues("AdapterOutboundSignal"));

        assertThrows(IllegalArgumentException.class, () -> service.definition("AdapterCommandLifecycleEvent"));
        assertThrows(IllegalArgumentException.class, () -> service.definition("ExecutionLifecycleEvent"));
        assertThrows(IllegalArgumentException.class, () -> service.definition("AdapterConfigFormat"));
    }

    @Test
    void validatesCommandPayloadPolicyFromProtocolSignalContracts() {
        assertTrue(service.requiresCommandPayload("WF_EXECUTE_START"));
        assertTrue(service.requiresCommandPayload("MANUAL_EXECUTE_START"));
        assertTrue(service.requiresCommandPayload("CMD_START"));
        assertFalse(service.requiresCommandPayload("WF_EXECUTE_ABORT"));

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> service.validateSignalExecutionContext("CMD_START", Map.of()));
        assertTrue(missing.getMessage().contains("commandName"));

        service.validateSignalExecutionContext("CMD_START", Map.of(
                "commandName", "heat",
                "parameters", Map.of("temperature", 80)));
        service.validateSignalExecutionContext("CMD_ABORT", Map.of());
        assertThrows(IllegalArgumentException.class,
                () -> service.requiresCommandPayload("UNKNOWN_SIGNAL"));
    }
}
