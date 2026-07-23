package com.smartlab.global.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.protocol.ProtocolDictionaryService;
import com.smartlab.global.util.JsonNodeSupport;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SchemaMetadataService {

    private final ProtocolDictionaryService protocolDictionaryService;
    private final ConcurrentHashMap<String, JsonNode> schemaCache = new ConcurrentHashMap<>();

    public SchemaMetadataService() {
        this(new ProtocolDictionaryService());
    }

    public SchemaMetadataService(ProtocolDictionaryService protocolDictionaryService) {
        this.protocolDictionaryService = protocolDictionaryService;
    }

    public ObjectNode frontendMetadata() {
        ObjectNode root = JsonNodeSupport.objectNode();
        root.set("protocol", protocolMetadata());
        root.set("stateMachine", stateMachineMetadata());
        root.set("workflow", workflowMetadata());
        root.set("constraint", constraintMetadata());
        root.set("deviceCapability", deviceCapabilityMetadata());
        return root;
    }

    private ObjectNode protocolMetadata() {
        ObjectNode metadata = JsonNodeSupport.objectNode();
        metadata.set("mqttTopics", JsonNodeSupport.toNode(protocolDictionaryService.mqttTopicConvention()));
        metadata.set("adapterRegisterFormats",
                textArray(protocolDictionaryService.enumValuesFromProperty("AdapterRegisterRequest", "rawConfigFormat")));
        metadata.set("communicationProtocols",
                textArray(protocolDictionaryService.enumValues("CommunicationProtocol")));

        return metadata;
    }

    private ObjectNode stateMachineMetadata() {
        JsonNode schema = schema("schemas/device-state-machine-model.json");
        JsonNode systemSpec = schema("schemas/system-engine-spec.json");
        ObjectNode metadata = JsonNodeSupport.objectNode();
        metadata.set("interfaceTypes", systemSpec.path("interfaceTypes").deepCopy());
        metadata.set("commandStateNames", systemSpec.path("commandStateNames").deepCopy());
        metadata.set("standardInterfaces", resolveStandardInterfaces(systemSpec.path("standardInterfaces")));
        metadata.set("executionLifecycleMainPath", systemSpec.path("executionLifecycleMainPath").deepCopy());
        metadata.set("executionLifecycleBranches", systemSpec.path("executionLifecycleBranches").deepCopy());
        metadata.set("systemTransitions", systemSpec.path("systemTransitions").deepCopy());
        metadata.set("automaticTransitionStateSpaces", systemSpec.path("automaticTransitionStateSpaces").deepCopy());
        metadata.set("commandTerminalStates", systemSpec.path("commandTerminalStates").deepCopy());
        metadata.set("actionCatalog", systemSpec.path("actionCatalog").deepCopy());
        return metadata;
    }

    private ArrayNode resolveStandardInterfaces(JsonNode interfaces) {
        ArrayNode result = JsonNodeSupport.arrayNode();
        if (!interfaces.isArray()) {
            return result;
        }
        for (JsonNode item : interfaces) {
            ObjectNode copy = item.deepCopy();
            String ref = copy.path("allowedSignalsRef").asText("");
            if (!ref.isBlank()) {
                copy.remove("allowedSignalsRef");
                copy.set("allowedSignals", resolveProtocolEnumRef(ref));
            }
            result.add(copy);
        }
        return result;
    }

    private ArrayNode resolveProtocolEnumRef(String ref) {
        String prefix = "protocol-dict.json#/definitions/";
        if (!ref.startsWith(prefix)) {
            return JsonNodeSupport.arrayNode();
        }
        return textArray(protocolDictionaryService.enumValues(ref.substring(prefix.length())));
    }

    private ObjectNode workflowMetadata() {
        JsonNode schema = schema("schemas/workflow-model.json");
        ObjectNode metadata = JsonNodeSupport.objectNode();
        metadata.set("nodeTypes", enumAt(schema, "/definitions/FlowNodeDefinition/properties/nodeType/enum"));
        metadata.set("functionTypes", enumAt(schema, "/definitions/WorkflowNodeFunctionType/enum"));
        metadata.set("actionCatalog", schema.path("x-actionCatalog").deepCopy());
        metadata.set("calculationOperators", enumAt(schema, "/definitions/WorkflowCalculationOperator/enum"));
        metadata.set("nodeLifecycleEvents", enumAt(schema, "/definitions/WorkflowNodeLifecycleEvent/enum"));
        return metadata;
    }

    public List<String> stateMachineActionNames() {
        return catalogNames(schema("schemas/system-engine-spec.json").path("actionCatalog"));
    }

    public List<String> workflowActionNames() {
        return catalogNames(schema("schemas/workflow-model.json").path("x-actionCatalog"));
    }

    public List<String> workflowCalculationOperators() {
        return enumListAt(schema("schemas/workflow-model.json"), "/definitions/WorkflowCalculationOperator/enum");
    }

    public String workflowActionPayloadDefinition(String actionName) {
        JsonNode catalog = schema("schemas/workflow-model.json").path("x-actionCatalog");
        for (JsonNode item : catalog) {
            if (actionName.equals(item.path("actionName").asText()))
                return item.path("payloadDefinition").asText("");
        }
        throw new IllegalArgumentException("工作流 Schema 未声明动作: " + actionName);
    }

    public List<String> workflowNodeTypes() {
        return enumListAt(schema("schemas/workflow-model.json"), "/definitions/FlowNodeDefinition/properties/nodeType/enum");
    }

    public List<String> workflowFunctionTypes() {
        return enumListAt(schema("schemas/workflow-model.json"), "/definitions/WorkflowNodeFunctionType/enum");
    }

    private ObjectNode constraintMetadata() {
        JsonNode schema = schema("schemas/constraint-model.json");
        ObjectNode metadata = JsonNodeSupport.objectNode();
        metadata.set("objectTypes", enumAt(schema, "/properties/constraintObject/properties/objectType/enum"));
        metadata.set("operators", textArray(protocolDictionaryService.enumValues("ConstraintOperator")));
        metadata.set("dataTypes", textArray(protocolDictionaryService.enumValues("DataType")));
        metadata.set("violationActions", textArray(protocolDictionaryService.enumValues("SystemViolationAction")));
        return metadata;
    }

    private ObjectNode deviceCapabilityMetadata() {
        ObjectNode metadata = JsonNodeSupport.objectNode();
        metadata.set("dataTypes", textArray(protocolDictionaryService.enumValues("DataType")));
        metadata.set("constraintOperators", textArray(protocolDictionaryService.enumValues("ConstraintOperator")));
        return metadata;
    }

    private ArrayNode standardSignalsForInterface(JsonNode interfaces, String name) {
        if (interfaces.isArray()) {
            for (JsonNode item : interfaces) {
                if (name.equals(item.path("name").asText())) {
                    JsonNode signals = item.path("allowedSignals");
                    if (signals.isArray()) {
                        return signals.deepCopy();
                    }
                }
            }
        }
        return JsonNodeSupport.arrayNode();
    }

    private JsonNode schema(String resourcePath) {
        return schemaCache.computeIfAbsent(resourcePath, this::loadSchema);
    }

    private JsonNode loadSchema(String resourcePath) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            try (InputStream inputStream = resource.getInputStream()) {
                return JsonNodeSupport.MAPPER.readTree(inputStream);
            }
        } catch (Exception e) {
            throw new IllegalStateException("加载 schema 失败: " + resourcePath, e);
        }
    }

    private ArrayNode enumAt(JsonNode schema, String pointer) {
        JsonNode values = schema.at(pointer);
        if (!values.isArray()) {
            return JsonNodeSupport.arrayNode();
        }
        ArrayNode result = JsonNodeSupport.arrayNode();
        values.forEach(value -> result.add(value.asText()));
        return result;
    }

    private List<String> enumListAt(JsonNode schema, String pointer) {
        JsonNode values = schema.at(pointer);
        if (!values.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(value.asText()));
        return List.copyOf(result);
    }

    private List<String> catalogNames(JsonNode catalog) {
        if (!catalog.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        catalog.forEach(item -> result.add(item.path("actionName").asText()));
        return List.copyOf(result);
    }

    private ArrayNode textArray(List<String> values) {
        ArrayNode array = JsonNodeSupport.arrayNode();
        values.forEach(array::add);
        return array;
    }
}
