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
        assertEquals(List.of("LEASE", "RELEASE"), service.enumValues("LeaseAction"));
        assertEquals(List.of("GRANTED", "RELEASED", "FAILED"), service.enumValues("LeaseResultStatus"));
        assertThrows(IllegalArgumentException.class, () -> service.enumValues("ConstraintOperator"));
    }

    @Test
    void resolvesAndMatchesFinalMqttTopics() {
        assertEquals("smartlab/adapter/ReactorAdapter/Reactor1/command",
                service.resolveMqttTopic("commandTopic", Map.of("adapterName", "ReactorAdapter", "devicePoint", "Reactor1")));
        assertEquals("smartlab/adapter/ReactorAdapter/heartbeat",
                service.resolveMqttTopic("heartbeatTopic", Map.of("adapterName", "ReactorAdapter")));
        assertEquals("smartlab/adapter/ReactorAdapter/leaserequest",
                service.resolveMqttTopic("leaseRequestTopic", Map.of("adapterName", "ReactorAdapter")));
        assertEquals("smartlab/adapter/ReactorAdapter/leaseresult",
                service.resolveMqttTopic("leaseResultTopic", Map.of("adapterName", "ReactorAdapter")));
        assertThrows(IllegalArgumentException.class, () -> service.resolveMqttTopic(
                "commandTopic", Map.of("adapterName", "ReactorAdapter", "devicePoint", "Reactor1#sim-1")));

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

    @Test
    void validatesLeaseRequestAndResultContracts() {
        ObjectNode request = JsonNodeSupport.objectNode();
        request.put("leaseId", 11);
        request.put("adapterName", "plc");
        request.put("action", "LEASE");
        request.put("devicePoint", "Reactor1");
        request.put("timestamp", 1719892800L);
        service.validateDefinition("LeaseRequestFormat", request);

        ObjectNode granted = JsonNodeSupport.objectNode();
        granted.put("leaseId", 11);
        granted.put("adapterName", "plc");
        granted.put("action", "LEASE");
        granted.put("devicePoint", "Reactor1_sim_11");
        granted.put("status", "GRANTED");
        granted.put("timestamp", 1719892800L);
        service.validateDefinition("LeaseResultFormat", granted);

        granted.put("status", "RELEASED");
        assertThrows(IllegalArgumentException.class, () -> service.validateDefinition("LeaseResultFormat", granted));
    }

    @Test
    void validatesSingleAndBatchTelemetryFormats() {
        // 1. 单点格式（缺省 formatType 或 formatType=SINGLE）
        ObjectNode single = JsonNodeSupport.objectNode();
        single.put("timestamp", 1719892800L);
        single.put("adapterName", "adapterv2");
        single.put("devicePoint", "Reactor1");
        ObjectNode data = single.putObject("telemetryData");
        data.put("temperature", 25.0);
        service.validateDefinition("TelemetryMessageFormat", single);

        single.put("formatType", "SINGLE");
        service.validateDefinition("TelemetryMessageFormat", single);

        // 2. 批量格式（formatType=BATCH）
        ObjectNode batch = JsonNodeSupport.objectNode();
        batch.put("formatType", "BATCH");
        batch.put("timestamp", 1719892800L);
        batch.put("adapterName", "adapterv2");
        batch.put("devicePoint", "Reactor1_sim_16");
        var items = batch.putArray("items");
        var item1 = items.addObject();
        item1.put("timestamp", 1719892800100L);
        item1.putObject("telemetryData").put("temperature", 26.0);
        var item2 = items.addObject();
        item2.put("timestamp", 1719892800200L);
        item2.putObject("telemetryData").put("temperature", 27.0);

        service.validateDefinition("TelemetryMessageFormat", batch);

        // BATCH 缺少 items 抛异常
        batch.remove("items");
        assertThrows(IllegalArgumentException.class, () -> service.validateDefinition("TelemetryMessageFormat", batch));
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