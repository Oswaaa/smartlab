package com.smartlab.agent.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.entity.resource.device.DeviceCategory;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.service.db.resource.device.DeviceCategoryService;
import com.smartlab.management.service.db.resource.device.DeviceModelService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DeviceCatalogAssembler {
    private final DeviceModelService deviceModelService;
    private final DeviceCategoryService deviceCategoryService;

    public DeviceCatalogAssembler(DeviceModelService deviceModelService, DeviceCategoryService deviceCategoryService) {
        this.deviceModelService = deviceModelService;
        this.deviceCategoryService = deviceCategoryService;
    }

    public List<DeviceCatalogItem> list(String keyword) {
        Map<Long, String> categoryNames = categoryNames();
        List<DeviceCatalogItem> items = new ArrayList<>();
        for (DeviceModels model : deviceModelService.list()) {
            DeviceCatalogItem item = summarize(model, categoryNames.get(model.getCategoryId()), false);
            if (matches(item, keyword)) items.add(item);
        }
        return items;
    }

    public DeviceCatalogItem detail(long deviceModelId) {
        DeviceModels model = deviceModelService.getById(String.valueOf(deviceModelId));
        if (model == null) return null;
        String categoryName = null;
        if (model.getCategoryId() != null) {
            DeviceCategory category = deviceCategoryService.getById(model.getCategoryId());
            categoryName = category == null ? null : category.getCategoryName();
        }
        return summarize(model, categoryName, true);
    }

    DeviceCatalogItem summarize(DeviceModels model, String categoryName, boolean includeInterfaces) {
        if (model == null) return null;
        return new DeviceCatalogItem(
                model.getId(),
                model.getModelName(),
                categoryName,
                capabilities(model.getCapabilities()),
                attributes(model.getAttributes()),
                includeInterfaces ? ports(model.getPorts()) : null,
                includeInterfaces ? interfaces(model.getStateMachineInterfaces()) : null);
    }

    private Map<Long, String> categoryNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (DeviceCategory category : deviceCategoryService.list()) {
            if (category.getId() != null) names.put(category.getId(), category.getCategoryName());
        }
        return names;
    }

    private List<CatalogCapability> capabilities(JsonNode source) {
        List<CatalogCapability> result = new ArrayList<>();
        for (JsonNode item : iterable(source)) {
            List<CatalogParameter> parameters = new ArrayList<>();
            for (JsonNode parameter : iterable(item.path("parameters"))) {
                parameters.add(new CatalogParameter(
                        text(parameter, "name"),
                        text(parameter, "displayName"),
                        text(parameter, "dataType")));
            }
            result.add(new CatalogCapability(
                    text(item, "capabilityName"),
                    text(item, "displayName"),
                    List.copyOf(parameters)));
        }
        return List.copyOf(result);
    }

    private List<CatalogAttribute> attributes(JsonNode source) {
        List<CatalogAttribute> result = new ArrayList<>();
        for (JsonNode item : iterable(source)) {
            result.add(new CatalogAttribute(
                    text(item, "attributeName"),
                    text(item, "displayName"),
                    text(item, "dataType"),
                    text(item, "unit")));
        }
        return List.copyOf(result);
    }

    private List<CatalogPort> ports(JsonNode source) {
        List<CatalogPort> result = new ArrayList<>();
        for (JsonNode item : iterable(source)) {
            String name = text(item, "portName");
            if (name.isBlank()) name = text(item, "name");
            String dataType = text(item, "dataType");
            result.add(new CatalogPort(name, text(item, "direction"), dataType.isBlank() ? null : dataType));
        }
        return List.copyOf(result);
    }

    private List<CatalogInterface> interfaces(JsonNode source) {
        List<CatalogInterface> result = new ArrayList<>();
        JsonNode items = source != null && source.isObject() && source.has("interfaces") ? source.get("interfaces") : source;
        for (JsonNode item : iterable(items)) {
            String interfaceType = text(item, "interfaceType");
            if (!"WORKFLOW".equals(interfaceType) && !"STATE".equals(interfaceType)) continue;
            result.add(new CatalogInterface(
                    text(item, "name"),
                    text(item, "direction"),
                    interfaceType));
        }
        return List.copyOf(result);
    }

    private boolean matches(DeviceCatalogItem item, String keyword) {
        if (keyword == null || keyword.isBlank()) return true;
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        if (contains(item.deviceModelName(), needle) || contains(item.categoryName(), needle)) return true;
        for (CatalogCapability capability : item.capabilities()) {
            if (contains(capability.capabilityName(), needle) || contains(capability.displayName(), needle)) return true;
        }
        return false;
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private Iterable<JsonNode> iterable(JsonNode node) {
        return node != null && node.isArray() ? node : List.of();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }
}
