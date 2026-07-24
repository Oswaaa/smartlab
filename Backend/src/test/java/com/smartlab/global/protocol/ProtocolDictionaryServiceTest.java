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
    void exposesOnlyFinalProtocolEnumerations() {
        assertEquals(List.of("INTEGER", "DOUBLE", "STRING", "BOOLEAN", "JSON"), service.enumValues("DataType"));
        assertEquals(List.of("MQTT"), service.enumValues("CommunicationProtocol"));
        assertEquals(List.of("CONSTRAINT_EXECUTE", "CONSTRAINT_ABORT"), service.enumValues("ConstraintControlSignal"));
        assertEquals(List.of("CMD_START", "CMD_ABORT"), service.enumValues("AdapterOutboundSignal"));
        assertThrows(IllegalArgumentException.class, () -> service.enumValues("ConstraintOperator"));
    }

    @Test
    void resolvesAndMatchesFinalMqttTopics() {
        assertEquals("smartlab/adapter/ReactorAdapter/Reactor1/command",
                service.resolveMqttTopic("commandTopic", Map.of("adapterName", "ReactorAdapter", "devicePoint", "Reactor1")));
        assertEquals("smartlab/adapter/ReactorAdapter/heartbeat",
                service.resolveMqttTopic("heartbeatTopic", Map.of("adapterName", "ReactorAdapter")));

        ProtocolTopicMatch match = service.matchMqttTopic("smartlab/adapter/ReactorAdapter/Reactor1/telemetry").orElseThrow();
        assertEquals("telemetryTopic", match.topicName());
        assertEquals("ReactorAdapter", match.variables().get("adapterName"));
        assertEquals("Reactor1", match.variables().get("devicePoint"));
    }

    @Test
    void acceptsTheFinalCommandMessageAndRejectsTheRemovedOperationField() {
        ObjectNode command = commandMessage();
        service.validateDefinition("CommandMessageFormat", command);

        command.put("operation", "ABORT");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.validateDefinition("CommandMessageFormat", command));
        assertTrue(error.getMessage().contains("operation"));
    }

    @Test
    void validatesSignalPayloadPoliciesWithoutSignalContractSchema() {
        assertTrue(service.requiresCommandPayload("WF_EXECUTE_START"));
        assertTrue(service.requiresCommandPayload("MANUAL_EXECUTE_START"));
        assertTrue(service.requiresCommandPayload("CMD_START"));
        assertFalse(service.requiresCommandPayload("WF_EXECUTE_ABORT"));
        assertFalse(service.requiresCommandPayload("CONSTRAINT_ABORT"));

        service.validateSignalExecutionContext("CONSTRAINT_EXECUTE", Map.of(
                "deviceInstanceId", 7,
                "capabilityName", "cooling",
                "parameters", Map.of("durationSec", 30)));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateSignalExecutionContext("CONSTRAINT_EXECUTE", Map.of("deviceInstanceId", 7)));
    }

    @Test
    void allowsOnlyAliveAdapterHeartbeat() {
        ObjectNode heartbeat = JsonNodeSupport.objectNode();
        heartbeat.put("status", "ALIVE");
        heartbeat.put("timestamp", 1719892800L);
        service.validateDefinition("AdapterHeartbeat", heartbeat);

        heartbeat.put("status", "DEGRADED");
        assertThrows(IllegalArgumentException.class, () -> service.validateDefinition("AdapterHeartbeat", heartbeat));
    }

    private ObjectNode commandMessage() {
        ObjectNode command = JsonNodeSupport.objectNode();
        command.put("messageId", "msg-1");
        command.put("adapterName", "adapter1");
        command.put("devicePoint", "Reactor1");
        command.put("commandName", "heat");
        command.set("parameters", JsonNodeSupport.objectNode());
        command.put("timestamp", 1719892800L);
        return command;
    }
}