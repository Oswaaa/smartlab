package com.smartlab.management.dto.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.workflow.FlowModels;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 模型文件与库表之间的转换；不再经过第二套保存 DTO。 */
public final class WorkflowModelDocuments {
    private static final Set<String> EDITOR_ONLY_KEYS = Set.of("position", "compiled", "runtime");
    private static final int FLOW_NAME_MAX_LENGTH = 80;
    private static final Pattern COPY_NAME = Pattern.compile("^(.*)（副本(?: (\\d+))?）$");

    private WorkflowModelDocuments() {
    }

    public static WorkflowModelDocument of(String flowModelName) {
        WorkflowModelDocument document = new WorkflowModelDocument();
        document.flowModelName(flowModelName);
        document.setNodes(JsonNodeSupport.arrayNode());
        document.setInterfaceConnections(JsonNodeSupport.arrayNode());
        document.setPortConnections(JsonNodeSupport.arrayNode());
        return document;
    }

    public static WorkflowModelDocument copy(WorkflowModelDocument input) {
        if (input == null) return null;
        WorkflowModelDocument copy = new WorkflowModelDocument();
        WorkflowModelMetadata metadata = input.getMetadata() == null ? new WorkflowModelMetadata() : input.getMetadata();
        copy.flowModelId(metadata.getFlowModelId());
        copy.flowModelName(metadata.getFlowModelName());
        copy.descriptionText(metadata.getDescription());
        copy.setNodes(input.getNodes() == null ? null : input.getNodes().deepCopy());
        copy.setInterfaceConnections(input.getInterfaceConnections() == null ? null : input.getInterfaceConnections().deepCopy());
        copy.setPortConnections(input.getPortConnections() == null ? null : input.getPortConnections().deepCopy());
        return copy;
    }

    public static WorkflowModelDocument sanitizeDocument(WorkflowModelDocument input) {
        WorkflowModelDocument document = copy(input);
        if (document == null) document = of("");
        document.setNodes(arrayOrEmpty(document.getNodes()));
        document.setInterfaceConnections(arrayOrEmpty(document.getInterfaceConnections()));
        document.setPortConnections(arrayOrEmpty(document.getPortConnections()));
        return document;
    }

    public static WorkflowModelDocument asNewDraft(WorkflowModelDocument input) {
        WorkflowModelDocument document = sanitizeDocument(input);
        document.flowModelId(null);
        document.flowModelName(copyFlowName(document.flowModelName()));
        return document;
    }

    public static String copyFlowName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) trimmed = "未命名流程";
        Matcher matcher = COPY_NAME.matcher(trimmed);
        if (!matcher.matches()) return withCopySuffix(trimmed, "（副本）");
        String base = matcher.group(1);
        int index = matcher.group(2) == null ? 2 : Integer.parseInt(matcher.group(2)) + 1;
        return withCopySuffix(base, "（副本 " + index + "）");
    }

    private static String withCopySuffix(String base, String suffix) {
        String source = base == null ? "" : base;
        if (source.length() + suffix.length() <= FLOW_NAME_MAX_LENGTH) return source + suffix;
        int keep = Math.max(0, FLOW_NAME_MAX_LENGTH - suffix.length());
        return source.substring(0, keep) + suffix;
    }

    public static WorkflowModelDocument toDocument(WorkflowDetailResponse detail) {
        WorkflowModelDocument document = new WorkflowModelDocument();
        if (detail != null) {
            document.flowModelId(detail.getId());
            document.flowModelName(detail.getName());
            document.descriptionText(detail.getDescription());
            document.setNodes(sanitize(detail.getNodesDef()));
            document.setInterfaceConnections(sanitize(detail.getInterfaceConnections()));
            document.setPortConnections(sanitize(detail.getPortConnections()));
        } else {
            document.setNodes(JsonNodeSupport.arrayNode());
            document.setInterfaceConnections(JsonNodeSupport.arrayNode());
            document.setPortConnections(JsonNodeSupport.arrayNode());
        }
        return document;
    }

    public static WorkflowDetailResponse toDetail(WorkflowModelDocument document) {
        WorkflowDetailResponse detail = new WorkflowDetailResponse();
        if (document == null) return detail;
        detail.setId(document.flowModelId());
        detail.setName(document.flowModelName());
        detail.setDescription(document.descriptionText());
        detail.setNodesDef(arrayOrEmpty(document.getNodes()));
        detail.setInterfaceConnections(arrayOrEmpty(document.getInterfaceConnections()));
        detail.setPortConnections(arrayOrEmpty(document.getPortConnections()));
        return detail;
    }

    public static WorkflowViewResponse toView(WorkflowDetailResponse detail) {
        WorkflowViewResponse view = new WorkflowViewResponse();
        WorkflowModelDocument document = toDocument(detail);
        view.setMetadata(document.getMetadata());
        view.setNodes(document.getNodes());
        view.setInterfaceConnections(document.getInterfaceConnections());
        view.setPortConnections(document.getPortConnections());
        if (detail == null) return view;
        view.setVersion(detail.getVersion());
        view.setStatus(detail.getStatus());
        view.setPredecessorId(detail.getPredecessorId());
        view.setNodeIdRefs(detail.getNodeIdRefs());
        view.setCreatorId(detail.getCreatorId());
        view.setCreateTime(detail.getCreateTime());
        return view;
    }

    public static WorkflowPreparationApiResponse toPreparation(WorkflowPreparationResponse prepared) {
        if (prepared == null) {
            return new WorkflowPreparationApiResponse(toDocument(null), null, null, null, java.util.List.of(), false, false);
        }
        WorkflowDetailResponse definition = prepared.definition();
        return new WorkflowPreparationApiResponse(
                toDocument(definition),
                definition == null ? null : definition.getVersion(),
                definition == null ? null : definition.getStatus(),
                definition == null ? null : definition.getPredecessorId(),
                prepared.issues(),
                prepared.executable(),
                prepared.published());
    }

    public static WorkflowSummaryResponse toSummary(FlowModels model) {
        WorkflowSummaryResponse summary = new WorkflowSummaryResponse();
        if (model == null) return summary;
        summary.setId(model.getId());
        summary.setFlowModelName(model.getFlowName());
        summary.setDescription(model.getDescription());
        summary.setVersion(model.getVersion());
        summary.setStatus(model.getStatus());
        summary.setPredecessorId(model.getPredecessorId());
        return summary;
    }

    private static JsonNode arrayOrEmpty(JsonNode source) {
        JsonNode sanitized = sanitize(source);
        return sanitized != null && sanitized.isArray() ? sanitized : JsonNodeSupport.arrayNode();
    }

    static JsonNode sanitize(JsonNode source) {
        if (source == null || source.isNull() || source.isMissingNode()) {
            return JsonNodeSupport.arrayNode();
        }
        return sanitizeNode(source);
    }

    private static JsonNode sanitizeNode(JsonNode source) {
        if (source == null || source.isNull() || source.isMissingNode()) return source;
        if (source.isArray()) {
            ArrayNode array = JsonNodeSupport.arrayNode();
            source.forEach(item -> array.add(sanitizeNode(item)));
            return array;
        }
        if (!source.isObject()) return source;
        ObjectNode object = JsonNodeSupport.objectNode();
        source.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (key.startsWith("_") || EDITOR_ONLY_KEYS.contains(key)) return;
            object.set(key, sanitizeNode(entry.getValue()));
        });
        return object;
    }
}
