package com.smartlab.adapter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TestAdapterIniContractTest {

    @Test
    void mqttPlcAdapterSetupIsAcceptedByBackendParser() throws Exception {
        Path configPath = Path.of("..", "adapter", "testAdapter", "adapterSetup.ini").normalize();
        String rawConfig = Files.readString(configPath, StandardCharsets.UTF_8);

        ObjectNode parsed = new AdapterManifestService().parseRawConfig(
                "PLCControllerAdapter", "INI", rawConfig, 1719892800000L);

        assertEquals("PLCControllerAdapter", parsed.path("adapterName").asText());
        assertEquals("PLCController",
                parsed.path("deviceCategories").get(0).path("categoryName").asText());
        assertEquals(1, parsed.path("deviceCategories").get(0).path("devicePoints").size());

        var commands = parsed.path("deviceCategories").get(0)
                .path("deviceTemplate").path("commands");
        assertEquals(3, commands.size());
        assertEquals("setOperatingMode", commands.get(0).path("name").asText());
        assertFalse(commands.get(0).path("parameters").get(0).path("internal").asBoolean());

        var attributes = parsed.path("deviceCategories").get(0)
                .path("deviceTemplate").path("attributes");
        assertEquals(1, attributes.size());
        assertEquals("temperature", attributes.get(0).path("name").asText());
        assertEquals("PLC1", parsed.path("deviceCategories").get(0)
                .path("devicePoints").get(0).path("devicePoint").asText());

        var events = parsed.path("deviceCategories").get(0)
                .path("deviceTemplate").path("events");
        assertFalse(events.path("cmdEvents").isEmpty());
        assertFalse(events.path("opEvents").isEmpty());
    }
}
