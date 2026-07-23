package com.smartlab.management.service.db.resource.adapter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import com.smartlab.management.mapper.resource.adapter.AdapterIndexMapper;
import com.smartlab.adapter.AdapterManifestService;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adapter 索引表服务。
 * 对应 ADAPTER_INDEX 表，用于维护设备执行代理的注册配置与在线状态。
 */
@Service
/**
 * 适配器网关静态注册与模板加载持久层业务服务。
 */
public class AdapterIndexService extends ManagementCrudService<AdapterIndex> {

    private final AdapterIndexMapper mapper;
    private final AdapterManifestService manifestService;

    public AdapterIndexService(AdapterIndexMapper mapper,
                               AdapterManifestService manifestService) {
        super(mapper);
        this.mapper = mapper;
        this.manifestService = manifestService;
    }

    /**
     * 查询全部 Adapter 索引，按更新时间倒序排列。
     */
    @Override
    public List<AdapterIndex> list() {
        return mapper.selectList(Wrappers.<AdapterIndex>lambdaQuery().orderByDesc(AdapterIndex::getUpdateTime, AdapterIndex::getId));
    }

    /**
     * 按 Adapter 标识名查询注册记录。
     */
    public AdapterIndex getByName(String adapterName) {
        if (adapterName == null || adapterName.isBlank()) {
            return null;
        }
        return mapper.selectOne(
                Wrappers.<AdapterIndex>lambdaQuery()
                        .eq(AdapterIndex::getAdapterName, adapterName.trim())
                        .last("limit 1")
        );
    }

    /**
     * 保存 Adapter 索引，自动维护创建时间和更新时间。
     */
    @Override
    public AdapterIndex save(AdapterIndex entity) {
        if (entity.getAdapterName() == null || entity.getAdapterName().isBlank()) {
            throw new IllegalArgumentException("Adapter 标识名不能为空");
        }
        entity.setAdapterName(entity.getAdapterName().trim());
        if (entity.getParsedConfig() != null) {
            manifestService.validate(entity.getParsedConfig());
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (entity.getId() == null) {
            entity.setCreateTime(now);
            if (entity.getStatus() == null || entity.getStatus().isBlank()) {
                entity.setStatus("UNKNOWN");
            }
        }
        entity.setUpdateTime(now);
        return super.save(entity);
    }

    /**
     * 解析 Adapter 注册报文但不落库，用于建模页面预览。
     */
    public ObjectNode previewRegisterPayload(Map<String, Object> payload) {
        String adapterName = stringValue(payload.get("adapterName"));
        String format = stringValue(payload.getOrDefault("rawConfigFormat", "JSON"));
        String content = stringValue(payload.get("rawConfigContent"));
        return manifestService.parseRawConfig(adapterName, format, content, longValue(payload.get("timestamp")));
    }

    /**
     * 保存 AdapterRegisterRequest。rawConfigContent 是 adapter 自己提供的 manifest。
     */
    public AdapterIndex register(Map<String, Object> payload) {
        ObjectNode manifest = manifestFromRegisterPayload(payload);
        String adapterName = manifest.path("adapterName").asText();
        AdapterIndex adapter = getByName(adapterName);
        if (adapter == null) {
            adapter = new AdapterIndex();
            adapter.setAdapterName(adapterName);
            adapter.setCreateTime(OffsetDateTime.now());
        }
        adapter.setOriginalConfig(stringValue(payload.get("rawConfigContent")));
        adapter.setParsedConfig(manifest);
        adapter.setStatus(stringValue(payload.getOrDefault("status", "REGISTERED")));
        adapter.setUpdateTime(OffsetDateTime.now());
        return save(adapter);
    }

    private ObjectNode manifestFromRegisterPayload(Map<String, Object> payload) {
        Object reviewed = payload.get("parsedConfig");
        if (reviewed instanceof ObjectNode objectNode) {
            manifestService.validate(objectNode);
            return objectNode;
        }
        if (reviewed instanceof JsonNode jsonNode) {
            if (!jsonNode.isObject()) {
                throw new IllegalArgumentException("审阅后的 Adapter 配置必须是 JSON 对象");
            }
            ObjectNode objectNode = jsonNode.deepCopy();
            manifestService.validate(objectNode);
            return objectNode;
        }
        if (reviewed != null) {
            JsonNode node = JsonNodeSupport.MAPPER.valueToTree(reviewed);
            if (!node.isObject()) {
                throw new IllegalArgumentException("审阅后的 Adapter 配置必须是 JSON 对象");
            }
            ObjectNode objectNode = (ObjectNode) node;
            manifestService.validate(objectNode);
            return objectNode;
        }
        return previewRegisterPayload(payload);
    }

    /**
     * 查询 Adapter 的模板列表。
     */
    public ArrayNode listTemplates(String adapterName) {
        AdapterIndex adapter = requireAdapter(adapterName);
        ArrayNode templates = JsonNodeSupport.arrayNode();
        for (JsonNode category : adapter.getParsedConfig().path("deviceCategories")) {
            ObjectNode template = category.path("deviceTemplate").deepCopy();
            template.put("categoryName", category.path("categoryName").asText(""));
            template.put("categoryDescription", category.path("categoryDescription").asText(""));
            templates.add(template);
        }
        return templates;
    }

    public ArrayNode listAdapterCategories(String adapterName) {
        AdapterIndex adapter = requireAdapter(adapterName);
        ArrayNode categories = JsonNodeSupport.arrayNode();
        for (JsonNode category : adapter.getParsedConfig().path("deviceCategories")) {
            categories.add(category);
        }
        return categories;
    }

    public ArrayNode listDevicePoints(String adapterName, String categoryName) {
        AdapterIndex adapter = requireAdapter(adapterName);
        ArrayNode points = JsonNodeSupport.arrayNode();
        JsonNode category = manifestService.findCategory(adapter.getParsedConfig(), categoryName);
        if (category != null) {
            for (JsonNode point : category.path("devicePoints")) {
                points.add(point);
            }
        }
        return points;
    }

    public ObjectNode buildAdapterContract(String adapterName, String categoryName) {
        return manifestService.buildAdapterContract(requireAdapter(adapterName), categoryName);
    }
    public AdapterIndex requireAdapter(String adapterName) {
        AdapterIndex adapter = getByName(adapterName);
        if (adapter == null || adapter.getParsedConfig() == null) {
            throw new IllegalArgumentException("Adapter 未注册或没有解析后的配置: " + Objects.toString(adapterName, ""));
        }
        return adapter;
    }

    /**
     * 记录 Adapter 心跳并更新状态。
     */
    public AdapterIndex heartbeat(String adapterName, String status) {
        AdapterIndex adapter = getByName(adapterName);
        if (adapter == null) {
            adapter = new AdapterIndex();
            adapter.setAdapterName(adapterName);
        }
        adapter.setStatus(status == null || status.isBlank() ? "ONLINE" : status);
        adapter.setLastHeartbeat(OffsetDateTime.now());
        return save(adapter);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("timestamp 必须为整数");
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

}
