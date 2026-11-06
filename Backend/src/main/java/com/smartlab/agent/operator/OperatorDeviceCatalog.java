package com.smartlab.agent.operator;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.mapper.resource.adapter.AdapterIndexMapper;
import com.smartlab.management.service.db.resource.device.DeviceInstanceService;
import com.smartlab.management.service.db.resource.device.DeviceModelService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;

/**
 * 设备目录：实例名解析器 + 模型 + 实时状态快照。
 *
 * <p>解析器带 5 秒缓存。设备增删不频繁，但如果每次都全表重建，
 * 一轮对话里十几次工具调用就会白查十几次。
 */
@Service
public class OperatorDeviceCatalog {

    private static final long RESOLVER_CACHE_MILLIS = 5_000;

    private final DeviceInstanceService instanceService;
    private final DeviceModelService modelService;
    private final AdapterIndexMapper adapterIndexMapper;

    private volatile InstanceResolver resolver;
    private volatile long resolverBuiltAt;

    public OperatorDeviceCatalog(DeviceInstanceService instanceService,
                                 DeviceModelService modelService,
                                 AdapterIndexMapper adapterIndexMapper) {
        this.instanceService = instanceService;
        this.modelService = modelService;
        this.adapterIndexMapper = adapterIndexMapper;
    }

    public synchronized InstanceResolver resolver() {
        long now = System.currentTimeMillis();
        if (resolver == null || now - resolverBuiltAt > RESOLVER_CACHE_MILLIS) {
            resolver = new InstanceResolver(instanceService.list());
            resolverBuiltAt = now;
        }
        return resolver;
    }

    public void invalidate() {
        resolver = null;
        resolverBuiltAt = 0;
    }

    public DeviceInstances instance(long instanceId) {
        return instanceService.requireUsable(instanceId);
    }

    public DeviceModels model(long modelId) {
        DeviceModels model = modelService.getById(String.valueOf(modelId));
        if (model == null) throw new IllegalArgumentException("设备模型不存在: " + modelId);
        return model;
    }

    public DeviceModels modelOrNull(long modelId) {
        return modelService.getById(String.valueOf(modelId));
    }

    /** 组装守卫需要的实时快照。 */
    public ActionGuard.Snapshot snapshot(long instanceId) {
        DeviceInstances instance = instanceService.requireUsable(instanceId);
        DeviceTwinStates twin = instanceService.getSnapshot(instanceId);
        String adapterStatus = null;
        String adapterName = instance.getBoundAdapterName();
        if (adapterName != null && !adapterName.isBlank() && adapterIndexMapper != null) {
            AdapterIndex adapter = adapterIndexMapper.selectOne(Wrappers.<AdapterIndex>lambdaQuery()
                    .eq(AdapterIndex::getAdapterName, adapterName.trim()).last("limit 1"));
            if (adapter != null) adapterStatus = adapter.getStatus();
        }
        String online = twin == null ? "OFFLINE" : twin.getOnlineStatus();
        OffsetDateTime lastOnline = twin == null ? null : twin.getLastOnlineTime();
        String cmdState = twin == null ? "IDLE" : twin.getCurrentCmdState();
        return new ActionGuard.Snapshot(
                instance.getInstanceName(),
                instance.getLifecycleStatus(),
                instance.getInstanceKind(),
                adapterStatus,
                online,
                lastOnline,
                cmdState
        );
    }

    /** 按型号找全部可用实例，用于"槽位绑定只有一个候选"的判定。 */
    public Map<Long, java.util.List<DeviceInstances>> instancesByModel() {
        Map<Long, java.util.List<DeviceInstances>> grouped = new HashMap<>();
        for (DeviceInstances instance : instanceService.list()) {
            if (instance.getDeviceModelId() == null) continue;
            grouped.computeIfAbsent(instance.getDeviceModelId(), key -> new java.util.ArrayList<>()).add(instance);
        }
        return grouped;
    }

    public OptionalLong singleInstanceOfModel(long modelId) {
        java.util.List<DeviceInstances> all = instancesByModel().getOrDefault(modelId, java.util.List.of());
        if (all.size() == 1 && all.get(0).getId() != null) {
            return OptionalLong.of(all.get(0).getId());
        }
        return OptionalLong.empty();
    }

    public String modelName(long modelId) {
        DeviceModels model = modelOrNull(modelId);
        return model == null ? ("#" + modelId) : model.getModelName();
    }
}
