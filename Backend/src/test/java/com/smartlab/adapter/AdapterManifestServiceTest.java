package com.smartlab.adapter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdapterManifestServiceTest {

    private static final String ADAPTER_NAME = "TestHeatPressureAdapter-01";
    private static final long REGISTERED_AT = 1719892800L;
    private final AdapterManifestService service = new AdapterManifestService();

    @Test
    void iniAndJsonSamplesProduceEquivalentDeviceContract() throws IOException {
        ObjectNode iniManifest = service.parseRawConfig(ADAPTER_NAME, "INI", resource("samples/adapterSetup.ini"),
                REGISTERED_AT);
        ObjectNode jsonManifest = service.parseRawConfig(ADAPTER_NAME, "JSON", resource("samples/adapterSetup.json"),
                REGISTERED_AT);

        assertEquals("INI", iniManifest.path("registerMeta").path("rawConfigFormat").asText());
        assertEquals("JSON", jsonManifest.path("registerMeta").path("rawConfigFormat").asText());
        assertEquals(REGISTERED_AT, iniManifest.path("registerMeta").path("registeredAt").asLong());
        assertEquals(jsonManifest.path("adapterName"), iniManifest.path("adapterName"));
        assertEquals(jsonManifest.path("adapterDescription"), iniManifest.path("adapterDescription"));
        assertEquals(jsonManifest.path("deviceCategories"), iniManifest.path("deviceCategories"));
        assertEquals(true, iniManifest.path("deviceCategories").get(0).path("deviceTemplate")
                .path("commands").get(0).path("parameters").get(2).path("internal").asBoolean());
    }

    @Test
    void modelAdapterContractUsesCategoryAsItsOnlySelector() throws IOException {
        ObjectNode manifest = service.parseRawConfig(ADAPTER_NAME, "JSON", resource("samples/adapterSetup.json"),
                REGISTERED_AT);
        AdapterIndex adapter = new AdapterIndex();
        adapter.setAdapterName(ADAPTER_NAME);
        adapter.setParsedConfig(manifest);

        ObjectNode contract = service.buildAdapterContract(adapter, "ReactorUnit");

        assertEquals("ReactorUnit", contract.path("config").path("categoryName").asText());
        assertFalse(contract.path("config").has("templateName"));
        assertTrue(manifest.path("deviceCategories").get(0).path("deviceTemplate")
                .path("commands").get(0).path("parameters").get(2).path("internal").asBoolean());
        assertTrue(contract.path("commands").get(0).path("commandParameters").isArray());
        assertFalse(contract.path("commands").get(0).path("commandParameters").findValuesAsText("paramName")
                .contains("index"));
        assertEquals("COMMAND_RECEIVED",
                contract.path("events").path("cmdEvents").get(0).path("eventName").asText());
        assertFalse(contract.path("events").path("cmdEvents").get(0).has("name"));

    }
    @Test
    void adapterContractNeverExposesSourceBoundParameters() throws IOException {
        ObjectNode manifest = service.parseRawConfig(ADAPTER_NAME, "JSON", resource("samples/adapterSetup.json"),
                REGISTERED_AT);
        ObjectNode internalParameter = (ObjectNode) manifest.path("deviceCategories").get(0)
                .path("deviceTemplate").path("commands").get(0).path("parameters").get(2);
        internalParameter.remove("internal");
        internalParameter.put("hidden", true);

        AdapterIndex adapter = new AdapterIndex();
        adapter.setAdapterName(ADAPTER_NAME);
        adapter.setParsedConfig(manifest);

        ObjectNode contract = service.buildAdapterContract(adapter, "ReactorUnit");

        assertFalse(contract.path("commands").get(0).path("commandParameters").findValuesAsText("paramName")
                .contains("index"));
    }
    @Test
    void parsedConfigKeepsAdapterEventsAsTheContractSource() throws IOException {
        ObjectNode manifest = service.parseRawConfig(ADAPTER_NAME, "JSON", resource("samples/adapterSetup.json"),
                REGISTERED_AT);

        var events = manifest.path("deviceCategories").get(0)
                .path("deviceTemplate").path("events");
        assertTrue(events.path("cmdEvents").isArray());
        assertTrue(events.path("opEvents").isArray());
        assertEquals("COMMAND_RECEIVED", events.path("cmdEvents").get(0).path("name").asText());
    }

    @Test
    void rawConfigFormatStrictlySelectsTheParser() throws IOException {
        String json = resource("samples/adapterSetup.json");

        assertThrows(IllegalArgumentException.class,
                () -> service.parseRawConfig(ADAPTER_NAME, "INI", json, REGISTERED_AT));
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