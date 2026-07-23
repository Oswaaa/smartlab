package com.smartlab.global.util;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonSchemaValidationServiceTest {

    private final JsonSchemaValidationService service = new JsonSchemaValidationService();

    @Test
    void acceptsUnsavedCapabilityPreviewUsingCategoryName() {
        ObjectNode model = emptyCapabilityModel();

        assertDoesNotThrow(() -> service.validate(model, "device-capability-model.json"));
    }

    @Test
    void rejectsTemplateNameAsAdapterCategorySelector() {
        ObjectNode model = emptyCapabilityModel();
        ObjectNode config = (ObjectNode) model.path("adapterContract").path("config");
        config.put("templateName", "ReactorTemplate");

        assertThrows(IllegalArgumentException.class,
                () -> service.validate(model, "device-capability-model.json"));
    }

    @Test
    void validatesAWorkflowNodeAgainstItsSchemaDefinition() {
        ObjectNode node = JsonNodeSupport.objectNode();
        node.put("nodeIdRef", 1);
        node.put("name", "start");
        node.put("nodeType", "FUNCTIONAL_NODE");
        node.putObject("capability").put("functionType", "START");
        node.putArray("interfaces").addObject()
                .put("name", "flow_out").put("direction", "OUT")
                .put("interfaceType", "SIGNAL").putArray("allowedSignals").add("STARTED");
        node.putArray("ports");
        node.putArray("actions").addObject()
                .put("actionName", "EXECUTE_LOGIC").putObject("payload");

        assertThrows(IllegalArgumentException.class, () -> service.validateDefinition(
                node, "workflow-model.json", "FlowNodeDefinition"));

        node.putArray("actions");
        assertDoesNotThrow(() -> service.validateDefinition(
                node, "workflow-model.json", "FlowNodeDefinition"));
    }

    private ObjectNode emptyCapabilityModel() {
        ObjectNode model = JsonNodeSupport.objectNode();
        model.putObject("metadata")
                .putNull("modelId")
                .put("modelName", "")
                .putNull("deviceCategoryId");
        model.putArray("attributes");
        model.putArray("capabilities");
        ObjectNode contract = model.putObject("adapterContract");
        contract.putObject("config")
                .put("protocol", "MQTT")
                .put("adapterName", "")
                .put("categoryName", "");
        contract.putArray("commands");
        ObjectNode telemetry = contract.putObject("telemetry");
        telemetry.putArray("adapterAttributes");
        telemetry.putArray("attributesMapping");
        ObjectNode events = contract.putObject("events");
        events.putArray("cmdEvents");
        events.putArray("opEvents");
        model.putArray("ports");
        model.putArray("intrinsicConstraints");
        return model;
    }
}