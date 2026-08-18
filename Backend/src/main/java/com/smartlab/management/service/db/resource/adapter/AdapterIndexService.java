package com.smartlab.management.service.db.resource.adapter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.mapper.resource.adapter.AdapterIndexMapper;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.adapter.AdapterManifestService;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.smartlab.management.mapper.resource.device.DeviceTwinStatesMapper;
import org.springframework.scheduling.annotation.Scheduled;

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
    private final DeviceInstancesMapper deviceInstancesMapper;
    private final DeviceModelsMapper deviceModelsMapper;
    private final DeviceTwinStatesMapper deviceTwinStatesMapper;

    public AdapterIndexService(AdapterIndexMapper mapper,
                               AdapterManifestService manifestService,
                               DeviceInstancesMapper deviceInstancesMapper) {
        this(mapper, manifestService, deviceInstancesMapper, null, null);
    }

    public AdapterIndexService(AdapterIndexMapper mapper,
                               AdapterManifestService manifestService,
                               DeviceInstancesMapper deviceInstancesMapper,
                               DeviceModelsMapper deviceModelsMapper) {
        this(mapper, manifestService, deviceInstancesMapper, deviceModelsMapper, null);
    }

    @Autowired
    public AdapterIndexService(AdapterIndexMapper mapper,
                               AdapterManifestService manifestService,
                               DeviceInstancesMapper deviceInstancesMapper,
                               DeviceModelsMapper deviceModelsMapper,
                               DeviceTwinStatesMapper deviceTwinStatesMapper) {
        super(mapper);
        this.mapper = mapper;
        this.manifestService = manifestService;
        this.deviceInstancesMapper = deviceInstancesMapper;
        this.deviceModelsMapper = deviceModelsMapper;
        this.deviceTwinStatesMapper = deviceTwinStatesMapper;
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
        ensureConfigNotChangedWhileReferenced(entity);
        OffsetDateTime now = OffsetDateTime.now();
        if (entity.getId() == null) {
            entity.setCreateTime(now);
            if (entity.getStatus() == null || entity.getStatus().isBlank()) {
                entity.setStatus("ENABLED");
            }
        } else if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus("ENABLED");
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
     * 保存 AdapterRegisterRequest 到 ADAPTER_INDEX。
     */
    public AdapterIndex register(Map<String, Object> payload) {
        ObjectNode manifest = manifestFromRegisterPayload(payload);
        String adapterName = manifest.path("adapterName").asText();
        AdapterIndex adapter = getByName(adapterName);
        if (adapter == null) {
            adapter = new AdapterIndex();
            adapter.setAdapterName(adapterName);
            adapter.setCreateTime(OffsetDateTime.now());
            adapter.setStatus("ENABLED");
        } else if (hasReferencingDeviceModel(adapterName)) {
            throw new IllegalStateException("Adapter " + adapterName + " 已有关联设备模型，不能更新配置。请先删除所有关联模型后再试。");
        }
        adapter.setOriginalConfig(stringValue(payload.get("rawConfigContent")));
        adapter.setParsedConfig(manifest);
        if (adapter.getStatus() == null || adapter.getStatus().isBlank()) {
            adapter.setStatus("ENABLED");
        }
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
     * 记录 Adapter 心跳并更新时间戳，若处于 ENABLED 状态则联动更新下属使用中 (IN_USE) 设备实例为 ONLINE。
     */
    public AdapterIndex heartbeat(String adapterName, String status) {
        AdapterIndex adapter = getByName(adapterName);
        if (adapter == null) {
            adapter = new AdapterIndex();
            adapter.setAdapterName(adapterName);
            adapter.setStatus("ENABLED");
        } else if (adapter.getStatus() == null || adapter.getStatus().isBlank()) {
            adapter.setStatus("ENABLED");
        }
        OffsetDateTime now = OffsetDateTime.now();
        adapter.setLastHeartbeat(now);
        AdapterIndex saved = save(adapter);
        if (deviceTwinStatesMapper != null && adapterName != null && !adapterName.isBlank()
                && !"DISABLED".equalsIgnoreCase(adapter.getStatus())) {
            deviceTwinStatesMapper.updateOnlineStatusByAdapter(adapterName, "ONLINE", now);
        }
        return saved;
    }

    /**
     * 自动扫描超时未上报心跳的 Adapter（超过 30 秒），联动将其下属设备实例置为 OFFLINE。
     */
    @Scheduled(fixedDelay = 5000)
    public void scanAndExpireHeartbeats() {
        OffsetDateTime threshold = OffsetDateTime.now().minusSeconds(30);
        List<AdapterIndex> expiredAdapters = mapper.selectList(
                Wrappers.<AdapterIndex>lambdaQuery()
                        .isNotNull(AdapterIndex::getLastHeartbeat)
                        .lt(AdapterIndex::getLastHeartbeat, threshold)
        );
        if (expiredAdapters == null || expiredAdapters.isEmpty()) return;
        OffsetDateTime now = OffsetDateTime.now();
        for (AdapterIndex adapter : expiredAdapters) {
            if (deviceTwinStatesMapper != null && adapter.getAdapterName() != null) {
                deviceTwinStatesMapper.updateOnlineStatusByAdapter(adapter.getAdapterName(), "OFFLINE", now);
            }
        }
    }

    /**
     * 更新 Adapter 的管理状态 (ENABLED / DISABLED)。
     */
    public AdapterIndex updateStatus(Long id, String status) {
        AdapterIndex adapter = getById(id);
        if (adapter == null) {
            throw new IllegalArgumentException("Adapter 不存在: " + id);
        }
        String resolvedStatus = "DISABLED".equalsIgnoreCase(status) ? "DISABLED" : "ENABLED";
        adapter.setStatus(resolvedStatus);
        OffsetDateTime now = OffsetDateTime.now();
        adapter.setUpdateTime(now);
        save(adapter);
        if (deviceTwinStatesMapper != null && adapter.getAdapterName() != null) {
            if ("DISABLED".equals(resolvedStatus)) {
                deviceTwinStatesMapper.updateOnlineStatusByAdapter(adapter.getAdapterName(), "OFFLINE", now);
            } else if (adapter.getLastHeartbeat() != null && adapter.getLastHeartbeat().isAfter(now.minusSeconds(30))) {
                deviceTwinStatesMapper.updateOnlineStatusByAdapter(adapter.getAdapterName(), "ONLINE", now);
            }
        }
        return adapter;
    }

    public boolean isAdapterDisabled(String adapterName) {
        if (adapterName == null || adapterName.isBlank()) return false;
        AdapterIndex adapter = getByName(adapterName);
        return adapter != null && "DISABLED".equalsIgnoreCase(adapter.getStatus());
    }

    /**
     * Adapter 被设备实例引用时不能直接物理删除，避免留下不可执行的绑定配置。
     */
    @Override
    public void delete(java.io.Serializable id) {
        AdapterIndex adapter = getById(id);
        if (adapter == null) {
            throw new IllegalArgumentException("Adapter 不存在: " + id);
        }
        long boundCount = deviceInstancesMapper.selectCount(
                Wrappers.<DeviceInstances>lambdaQuery()
                        .eq(DeviceInstances::getBoundAdapterName, adapter.getAdapterName())
        );
        if (boundCount > 0) {
            throw new IllegalStateException("Adapter 仍被 " + boundCount + " 个设备实例绑定，请先解除绑定");
        }
        if (hasReferencingDeviceModel(adapter.getAdapterName())) {
            throw new IllegalStateException("Adapter 已被设备模型引用，不能删除");
        }
        super.delete(id);
    }

    private void ensureConfigNotChangedWhileReferenced(AdapterIndex entity) {
        if (entity.getId() == null || entity.getParsedConfig() == null || deviceModelsMapper == null) return;
        AdapterIndex existing = mapper.selectById(entity.getId());
        if (existing == null || Objects.equals(existing.getParsedConfig(), entity.getParsedConfig())) return;
        if (hasReferencingDeviceModel(entity.getAdapterName())) {
            throw new IllegalStateException("Adapter 已被设备模型引用，不能修改或更新配置");
        }
    }

    private boolean hasReferencingDeviceModel(String adapterName) {
        if (deviceModelsMapper == null || adapterName == null || adapterName.isBlank()) return false;
        return deviceModelsMapper.selectList(Wrappers.<DeviceModels>lambdaQuery()).stream()
                .anyMatch(model -> model.getAdapterContract() != null
                        && adapterName.equals(model.getAdapterContract().path("config").path("adapterName").asText("")));
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
