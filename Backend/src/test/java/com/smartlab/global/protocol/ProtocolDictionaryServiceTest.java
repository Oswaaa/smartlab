package com.smartlab.global.protocol;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolDictionaryServiceTest {

    private final ProtocolDictionaryService service = new ProtocolDictionaryService();

    @Test
    void readsEnumValuesFromProtocolDictionary() {
        assertEquals(List.of("INTEGER", "DOUBLE", "STRING", "BOOLEAN", "JSON"), service.enumValues("DataType"));
        assertEquals(List.of("START", "END", "BRANCH", "AGGREGATE"),
                service.enumValues("WorkflowNodeFunctionType"));
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
    void exposesFrontendMetadataFromProtocolDictionary() {
        ObjectNode metadata = service.frontendMetadata();

        assertTrue(metadata.path("dataTypes").isArray());
        assertTrue(metadata.path("constraintOperators").isArray());
        assertTrue(metadata.path("mqttTopics").has("commandTopic"));
        assertTrue(metadata.path("adapterRegisterFormats").isArray());
    }

    @Test
    void exposesSystemSignalDefinitionsFromProtocolDictionary() {
        assertEquals(List.of("MQTT", "HTTP"), service.enumValues("CommunicationProtocol"));
        assertEquals(List.of("Interface_workflow_in", "Interface_status_out", "Interface_control_in",
                        "Interface_constraint_in", "Interface_adapter_in", "Interface_adapter_out"),
                service.enumValues("SystemInterfaceName"));
        assertTrue(service.enumValues("AdapterCommandLifecycleEvent").contains("COMMAND_COMPLETED"));
        assertTrue(service.enumValues("CommandLifecycleState").contains("RUNNING"));

        ObjectNode metadata = service.frontendMetadata();
        assertTrue(metadata.path("communicationProtocols").isArray());
        assertTrue(metadata.path("adapterCommandLifecycleEvents").isArray());
        assertTrue(metadata.path("commandLifecycleStates").isArray());
    }
}
