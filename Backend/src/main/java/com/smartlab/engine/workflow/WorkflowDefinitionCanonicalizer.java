package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.contract.WorkflowNodeSystemContract;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.dto.workflow.WorkflowSaveRequest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Replaces system-owned workflow definitions with the backend contract. */
public class WorkflowDefinitionCanonicalizer {

    public CanonicalizationResult canonicalize(WorkflowSaveRequest input) {
        WorkflowSaveRequest normalized = copy(input);
        List<WorkflowIssue> issues = new ArrayList<>();
        if (normalized == null || normalized.getNodesDef() == null || !normalized.getNodesDef().isArray()) {
            return new CanonicalizationResult(normalized, List.copyOf(issues));
        }
        int position = 0;
        for (JsonNode item : normalized.getNodesDef()) {
            if (item instanceof ObjectNode node) canonicalizeNode(node, "nodes[" + position + "]", issues);
            position++;
        }
        return new CanonicalizationResult(normalized, List.copyOf(issues));
    }

    private void canonicalizeNode(ObjectNode node, String path, List<WorkflowIssue> issues) {
        String nodeType = text(node, "nodeType");
        String functionType = text(node, "functionType");
        ObjectNode template;
        try {
            template = WorkflowNodeSystemContract.template(nodeType, functionType);
        } catch (IllegalArgumentException ignored) {
            return;
        }

        JsonNode lifecycle = node.path("lifecycle");
        if (claimsSystem(lifecycle) && !sameBusiness(lifecycle, template.path("lifecycle"))) {
            modifiedSystem(issues, path + ".lifecycle", "lifecycle", "lifecycle");
        }
        ObjectNode canonicalLifecycle = template.path("lifecycle").deepCopy();
        canonicalLifecycle.put("_systemKey", lifecycleSystemKey(nodeType, functionType));
        node.set("lifecycle", canonicalLifecycle);
        mergeInterfaces(node, template.path("interfaces"), path, issues);
        mergeActions(node, template.path("actions"), path, issues);
        normalizeActionModel(node);
    }

    ObjectNode canonicalizeTemplate(ObjectNode template) {
        ObjectNode normalized = template.deepCopy();
        normalizeActionModel(normalized);
        return normalized;
    }

    private void normalizeActionModel(ObjectNode node) {
        Map<String, ObjectNode> legacyActions = new LinkedHashMap<>();
        LinkedHashSet<String> allowedActions = new LinkedHashSet<>();
        for (JsonNode item : items(node.path("actions"))) {
            if (item.isTextual()) {
                if (!item.asText().isBlank()) allowedActions.add(item.asText());
                continue;
            }
            if (!item.isObject()) continue;
            String legacyName = text(item, "actionName");
            ObjectNode canonical = canonicalAction(item);
            if (!legacyName.isBlank()) legacyActions.put(legacyName, canonical);
            String actionName = text(canonical, "actionName");
            if (!actionName.isBlank()) allowedActions.add(actionName);
        }
        for (JsonNode interfaceNode : items(node.path("interfaces"))) {
            if (!(interfaceNode instanceof ObjectNode objectInterface)) continue;
            for (JsonNode triggerNode : items(objectInterface.path("bindingTriggers"))) {
                if (!(triggerNode instanceof ObjectNode trigger)) continue;
                JsonNode rawAction = trigger.get("action");
                ObjectNode canonical;
                if (rawAction != null && rawAction.isTextual()) {
                    canonical = legacyActions.get(rawAction.asText());
                    if (canonical == null) {
                        canonical = JsonNodeSupport.objectNode();
                        canonical.put("actionName", rawAction.asText());
                        canonical.putObject("payload");
                    } else {
                        canonical = canonical.deepCopy();
                    }
                } else {
                    canonical = canonicalAction(rawAction);
                }
                trigger.set("action", canonical);
            }
        }
        ArrayNode actions = JsonNodeSupport.arrayNode();
        allowedActions.forEach(actions::add);
        node.set("actions", actions);
    }

    private ObjectNode canonicalAction(JsonNode source) {
        ObjectNode action = JsonNodeSupport.objectNode();
        if (source == null || !source.isObject()) return action;
        String actionName = text(source, "actionType");
        if (actionName.isBlank()) actionName = text(source, "actionName");
        action.put("actionName", actionName);
        if (source.path("payload").isObject()) {
            action.set("payload", source.path("payload").deepCopy());
            return action;
        }
        ObjectNode payload = action.putObject("payload");
        if ("EMIT".equals(actionName)) {
            copyField(source, payload, "targetInterfaceName");
            copyField(source, payload, "signalName");
        } else if ("UPDATE".equals(actionName)) {
            String updateType = text(source, "updateType");
            if (updateType.isBlank()) updateType = "INTERNAL_VARIABLE";
            payload.put("updateType", updateType);
            String targetName = text(source, "targetName");
            if (targetName.isBlank()) targetName = text(source, "internalVariableName");
            if (!targetName.isBlank()) payload.put("targetName", targetName);
            copyField(source, payload, "valueExpression");
            copyField(source, payload, "value");
        }
        return action;
    }

    private void copyField(JsonNode source, ObjectNode target, String field) {
        if (source.has(field)) target.set(field, source.path(field).deepCopy());
    }

    private void mergeInterfaces(ObjectNode node, JsonNode expectedItems, String path, List<WorkflowIssue> issues) {
        List<JsonNode> source = items(node.path("interfaces"));
        Set<Integer> consumed = new HashSet<>();
        ArrayNode result = JsonNodeSupport.arrayNode();
        for (JsonNode expected : expectedItems) {
            int sourceIndex = find(source, consumed, expected, "name");
            JsonNode actual = sourceIndex < 0 ? null : source.get(sourceIndex);
            if (sourceIndex >= 0) consumed.add(sourceIndex);
            if (actual == null && source.stream().anyMatch(this::claimsSystem)) {
                modifiedSystem(issues, path + ".interfaces." + expected.path("name").asText(), "interface", expected.path("name").asText());
            }
            if (actual != null && claimsSystem(actual) && !sameInterfaceBusiness(actual, expected)) {
                modifiedSystem(issues, path + ".interfaces." + expected.path("name").asText(), "interface", expected.path("name").asText());
            }
            ObjectNode canonical = expected.deepCopy();
            mergeTriggers(canonical, actual == null ? null : actual.path("bindingTriggers"), path, issues);
            result.add(canonical);
        }
        for (int index = 0; index < source.size(); index++) {
            if (consumed.contains(index)) continue;
            JsonNode item = source.get(index);
            if (claimsSystem(item) || reservedName(item, expectedItems, "name")) {
                reservedSystemName(issues, path + ".interfaces[" + index + "]", "interface", text(item, "name"));
            } else {
                result.add(item.deepCopy());
            }
        }
        node.set("interfaces", result);
    }

    private void mergeTriggers(ObjectNode canonicalInterface, JsonNode sourceTriggers, String path, List<WorkflowIssue> issues) {
        List<JsonNode> source = items(sourceTriggers);
        List<JsonNode> expected = items(canonicalInterface.path("bindingTriggers"));
        Set<Integer> consumed = new HashSet<>();
        ArrayNode result = JsonNodeSupport.arrayNode();
        for (JsonNode item : expected) {
            int sourceIndex = findTrigger(source, consumed, item);
            JsonNode actual = sourceIndex < 0 ? null : source.get(sourceIndex);
            if (sourceIndex >= 0) consumed.add(sourceIndex);
            if (actual != null && claimsSystem(actual) && !sameBusiness(actual, item)) {
                modifiedSystem(issues, path + ".interfaces." + canonicalInterface.path("name").asText() + ".bindingTriggers", "trigger", item.path("_systemKey").asText());
            }
            result.add(item.deepCopy());
        }
        for (int index = 0; index < source.size(); index++) {
            if (consumed.contains(index)) continue;
            JsonNode item = source.get(index);
            if (claimsSystem(item)) {
                modifiedSystem(issues, path + ".interfaces." + canonicalInterface.path("name").asText() + ".bindingTriggers[" + index + "]", "trigger", text(item, "_systemKey"));
            } else {
                result.add(item.deepCopy());
            }
        }
        canonicalInterface.set("bindingTriggers", result);
    }

    private void mergeActions(ObjectNode node, JsonNode expectedItems, String path, List<WorkflowIssue> issues) {
        List<JsonNode> source = items(node.path("actions"));
        if (items(expectedItems).stream().allMatch(JsonNode::isTextual)) {
            ArrayNode result = JsonNodeSupport.arrayNode();
            items(expectedItems).forEach(item -> result.add(item.asText()));
            source.forEach(item -> result.add(item.deepCopy()));
            node.set("actions", result);
            return;
        }
        Set<Integer> consumed = new HashSet<>();
        ArrayNode result = JsonNodeSupport.arrayNode();
        for (JsonNode expected : expectedItems) {
            int sourceIndex = find(source, consumed, expected, "actionName");
            JsonNode actual = sourceIndex < 0 ? null : source.get(sourceIndex);
            if (sourceIndex >= 0) consumed.add(sourceIndex);
            if (actual != null && "UPDATE".equals(text(actual, "actionType"))) {
                reservedSystemName(issues, path + ".actions." + expected.path("actionName").asText(), "action", expected.path("actionName").asText());
            } else if (actual != null && claimsSystem(actual) && !sameBusiness(actual, expected)) {
                modifiedSystem(issues, path + ".actions." + expected.path("actionName").asText(), "action", expected.path("actionName").asText());
            }
            result.add(expected.deepCopy());
        }
        for (int index = 0; index < source.size(); index++) {
            if (consumed.contains(index)) continue;
            JsonNode item = source.get(index);
            if (claimsSystem(item) || reservedName(item, expectedItems, "actionName")) {
                reservedSystemName(issues, path + ".actions[" + index + "]", "action", text(item, "actionName"));
            } else {
                result.add(item.deepCopy());
            }
        }
        node.set("actions", result);
    }

    private int find(List<JsonNode> source, Set<Integer> consumed, JsonNode expected, String identity) {
        String systemKey = text(expected, "_systemKey");
        String reservedName = text(expected, identity);
        for (int index = 0; index < source.size(); index++) {
            JsonNode item = source.get(index);
            if (!consumed.contains(index) && (systemKey.equals(text(item, "_systemKey")) || reservedName.equals(text(item, identity)))) return index;
        }
        return -1;
    }

    private int findTrigger(List<JsonNode> source, Set<Integer> consumed, JsonNode expected) {
        String systemKey = text(expected, "_systemKey");
        for (int index = 0; index < source.size(); index++) {
            JsonNode item = source.get(index);
            if (!consumed.contains(index) && (systemKey.equals(text(item, "_systemKey"))
                    || (text(expected, "action").equals(text(item, "action")) && expected.path("condition").equals(item.path("condition"))))) return index;
        }
        return -1;
    }

    private boolean reservedName(JsonNode item, JsonNode expectedItems, String identity) {
        String value = text(item, identity);
        for (JsonNode expected : expectedItems) if (value.equals(text(expected, identity))) return true;
        return false;
    }

    private boolean claimsSystem(JsonNode item) {
        return item != null && item.isObject() && (item.has("_systemKey") || item.path("_system").asBoolean(false));
    }

    private boolean sameBusiness(JsonNode actual, JsonNode expected) {
        if (actual == null || !actual.isObject()) return false;
        ObjectNode cleanActual = ((ObjectNode) actual).deepCopy();
        ObjectNode cleanExpected = ((ObjectNode) expected).deepCopy();
        cleanActual.remove(List.of("_system", "_systemKey"));
        cleanExpected.remove(List.of("_system", "_systemKey"));
        return cleanActual.equals(cleanExpected);
    }

    private boolean sameInterfaceBusiness(JsonNode actual, JsonNode expected) {
        if (actual == null || !actual.isObject()) return false;
        ObjectNode cleanActual = ((ObjectNode) actual).deepCopy();
        ObjectNode cleanExpected = ((ObjectNode) expected).deepCopy();
        cleanActual.remove("bindingTriggers");
        cleanExpected.remove("bindingTriggers");
        return sameBusiness(cleanActual, cleanExpected);
    }

    private void modifiedSystem(List<WorkflowIssue> issues, String path, String elementType, String elementId) {
        issues.add(new WorkflowIssue("WORKFLOW_SYSTEM_FIELD_OVERRIDDEN", "CANONICALIZATION", path, elementType, elementId, false,
                "系统定义由后端模板恢复", "请不要修改系统维护的工作流定义"));
    }

    private void reservedSystemName(List<WorkflowIssue> issues, String path, String elementType, String elementId) {
        issues.add(new WorkflowIssue("WORKFLOW_SYSTEM_NAME_RESERVED", "CANONICALIZATION", path, elementType, elementId, false,
                "业务项使用了系统保留标识: " + elementId, "请为业务项使用非系统保留名称"));
    }

    private WorkflowSaveRequest copy(WorkflowSaveRequest input) {
        if (input == null) return null;
        WorkflowSaveRequest copy = new WorkflowSaveRequest();
        copy.setId(input.getId());
        copy.setName(input.getName());
        copy.setDescription(input.getDescription());
        copy.setVersion(input.getVersion());
        copy.setStatus(input.getStatus());
        copy.setCreatorId(input.getCreatorId());
        copy.setNodesDef(input.getNodesDef() == null ? null : input.getNodesDef().deepCopy());
        copy.setInterfaceConnections(input.getInterfaceConnections() == null ? null : input.getInterfaceConnections().deepCopy());
        copy.setPortConnections(input.getPortConnections() == null ? null : input.getPortConnections().deepCopy());
        return copy;
    }

    private String lifecycleSystemKey(String nodeType, String functionType) {
        if (!functionType.isBlank()) return functionType.toLowerCase() + ".lifecycle";
        if ("DEV_NODE".equals(nodeType)) return "device.lifecycle";
        if ("SUBFLOW_NODE".equals(nodeType)) return "subflow.lifecycle";
        return "lifecycle";
    }

    private List<JsonNode> items(JsonNode node) {
        List<JsonNode> result = new ArrayList<>();
        if (node != null && node.isArray()) node.forEach(result::add);
        return result;
    }

    private String text(JsonNode node, String field) {
        return node == null ? "" : node.path(field).asText("");
    }

    public record CanonicalizationResult(WorkflowSaveRequest normalized, List<WorkflowIssue> issues) {
    }
}
