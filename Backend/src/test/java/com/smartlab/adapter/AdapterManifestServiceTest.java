package com.smartlab.adapter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdapterManifestServiceTest {

    private static final String ADAPTER_NAME = "TestHeatPressureAdapter-01";
    private static final long REGISTERED_AT = 1719892800L;
    private final AdapterManifestService service = new AdapterManifestService();

    @Test
    void iniAndJsonSamplesProduceEquivalentDeviceContract() throws IOException {
        ObjectNode iniManifest = service.parseRawConfig(ADAPTER_NAME, "INI", resource("samples/adapterSetup.ini"), REGISTERED_AT);
        ObjectNode jsonManifest = service.parseRawConfig(ADAPTER_NAME, "JSON", resource("samples/adapterSetup.json"), REGISTERED_AT);

        assertEquals("INI", iniManifest.path("registerMeta").path("rawConfigFormat").asText());
        assertEquals("JSON", jsonManifest.path("registerMeta").path("rawConfigFormat").asText());
        assertEquals(REGISTERED_AT, iniManifest.path("registerMeta").path("registeredAt").asLong());
        assertEquals(jsonManifest.path("adapterName"), iniManifest.path("adapterName"));
        assertEquals(jsonManifest.path("adapterDescription"), iniManifest.path("adapterDescription"));
        assertEquals(jsonManifest.path("deviceCategories"), iniManifest.path("deviceCategories"));
        assertEquals(true, iniManifest.path("deviceCategories").get(0).path("deviceTemplate")
                .path("commands").get(0).path("parameters").get(2).path("internal").asBoolean());
    }


    private String resource(String name) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(name)) {
            if (stream == null) {
                throw new IOException("Missing test resource: " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}