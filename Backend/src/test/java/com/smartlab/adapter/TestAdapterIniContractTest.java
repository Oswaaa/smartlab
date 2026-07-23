package com.smartlab.adapter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAdapterIniContractTest {

    @Test
    void mqttPlcAdapterConfigIsAcceptedByBackendParser() throws Exception {
        Path configPath = Path.of("..", "adapter", "testAdapter", "adapterconfig.ini").normalize();
        String rawConfig = Files.readString(configPath, StandardCharsets.UTF_8);

        ObjectNode parsed = new AdapterManifestService().parseRawConfig(
                "SmartLabPLCAdapter-01", "INI", rawConfig, 1719892800000L);

        assertEquals("SmartLabPLCAdapter-01", parsed.path("adapterName").asText());
        assertEquals("PLCThermalUnit",
                parsed.path("deviceCategories").get(0).path("categoryName").asText());
        assertEquals(1, parsed.path("deviceCategories").get(0).path("devicePoints").size());

        var commands = parsed.path("deviceCategories").get(0)
                .path("deviceTemplate").path("commands");
        assertEquals(2, commands.size());
        var internalParameters = commands.get(0).path("parameters");
        assertTrue(internalParameters.get(1).path("internal").asBoolean());
        assertEquals("deviceSN", internalParameters.get(1).path("sourceField").asText());

        var attributes = parsed.path("deviceCategories").get(0)
                .path("deviceTemplate").path("attributes");
        assertEquals(4, attributes.size());
        assertEquals("plc0001", parsed.path("deviceCategories").get(0)
                .path("devicePoints").get(0).path("deviceSN").asText());

        var events = parsed.path("deviceCategories").get(0)
                .path("deviceTemplate").path("events");
        assertFalse(events.path("cmdEvents").isEmpty());
        assertFalse(events.path("opEvents").isEmpty());
    }
}
