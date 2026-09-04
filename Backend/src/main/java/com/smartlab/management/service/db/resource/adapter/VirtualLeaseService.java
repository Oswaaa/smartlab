package com.smartlab.management.service.db.resource.adapter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.adapter.MqttAdapterMessagingService;
import com.smartlab.global.contract.MqttTopic;
import com.smartlab.global.event.AdapterLeaseResultEvent;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.resource.device.VirtualMachineView;
import com.smartlab.management.entity.resource.adapter.VirtualLease;
import com.smartlab.management.entity.resource.adapter.VirtualLeaseStatus;
import com.smartlab.management.entity.resource.device.DeviceInstanceKind;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.data.DataIndex;
import com.smartlab.management.mapper.resource.adapter.VirtualLeaseMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import com.smartlab.management.service.db.resource.data.DataIndexService;
import com.smartlab.management.service.db.resource.device.DeviceInstanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.Serializable;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 虚拟点租约：向 Adapter 发 LEASE/RELEASE，按 result 创建或拆除 VIRTUAL 实例。
 */
@Service
public class VirtualLeaseService extends ManagementCrudService<VirtualLease> {
    private static final Logger log = LoggerFactory.getLogger(VirtualLeaseService.class);
    private static final Set<String> IN_FLIGHT = Set.of(
            VirtualLeaseStatus.LEASING, VirtualLeaseStatus.ACTIVE, VirtualLeaseStatus.RELEASING);

    private final VirtualLeaseMapper mapper;
    private final DeviceInstanceService deviceInstanceService;
    private final ConcurrentHashMap<Long, CompletableFuture<VirtualLease>> outcomes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> lastFailReasons = new ConcurrentHashMap<>();
    private MqttAdapterMessagingService mqttAdapterMessagingService;
    private DataIndexService dataIndexService;
    private VirtualLeaseService self;
    private long grantTimeoutSeconds = 15;
    private long pokeIdleTimeoutSeconds = 3600;

    public VirtualLeaseService(VirtualLeaseMapper mapper, DeviceInstanceService deviceInstanceService) {
        super(mapper);
        this.mapper = mapper;
        this.deviceInstanceService = deviceInstanceService;
    }

    @Autowired(required = false)
    public void setMqttAdapterMessagingService(MqttAdapterMessagingService mqttAdapterMessagingService) {
        this.mqttAdapterMessagingService = mqttAdapterMessagingService;
    }

    @Autowired(required = false)
    public void setDataIndexService(DataIndexService dataIndexService) {
        this.dataIndexService = dataIndexService;
    }

    @Autowired
    @Lazy
    public void setSelf(VirtualLeaseService self) {
        this.self = self;
    }

    @Value("${smartlab.virtual-lease.grant-timeout-seconds:15}")
    public void setGrantTimeoutSeconds(long grantTimeoutSeconds) {
        this.grantTimeoutSeconds = Math.max(1, grantTimeoutSeconds);
    }

    @Value("${smartlab.virtual-lease.poke-idle-timeout-seconds:3600}")
    public void setPokeIdleTimeoutSeconds(long pokeIdleTimeoutSeconds) {
        this.pokeIdleTimeoutSeconds = Math.max(1, pokeIdleTimeoutSeconds);
    }

    public List<VirtualLease> list(Long physicalInstanceId, Long taskId, String status) {
        return mapper.selectList(Wrappers.<VirtualLease>lambdaQuery()
                .eq(physicalInstanceId != null, VirtualLease::getPhysicalInstanceId, physicalInstanceId)
                .eq(taskId != null, VirtualLease::getTaskId, taskId)
                .eq(status != null && !status.isBlank(), VirtualLease::getStatus, VirtualLeaseStatus.normalize(status))
                .orderByDesc(VirtualLease::getId));
    }

    public Long resolveArchiveDataIndexId(Long physicalInstanceId, Long taskId) {
        if (physicalInstanceId == null || taskId == null) {
            return null;
        }
        List<VirtualLease> leases = mapper.selectList(Wrappers.<VirtualLease>lambdaQuery()
                .eq(VirtualLease::getPhysicalInstanceId, physicalInstanceId)
                .eq(VirtualLease::getTaskId, taskId)
                .orderByDesc(VirtualLease::getId));
        if (leases == null || leases.isEmpty()) {
            return null;
        }
        VirtualLease activeWithPointer = null;
        VirtualLease latestWithPointer = null;
        for (VirtualLease lease : leases) {
            if (lease.getDataIndexId() == null) {
                continue;
            }
            if (latestWithPointer == null) {
                latestWithPointer = lease;
            }
            if (activeWithPointer == null && VirtualLeaseStatus.ACTIVE.equals(lease.getStatus())) {
                activeWithPointer = lease;
            }
        }
        VirtualLease chosen = activeWithPointer != null ? activeWithPointer : latestWithPointer;
        return chosen == null ? null : chosen.getDataIndexId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VirtualLease save(VirtualLease entity) {
        if (entity == null) {
            throw new IllegalArgumentException("租约不能为空");
        }
        if (entity.getPhysicalInstanceId() == null) {
            throw new IllegalArgumentException("物理实例ID不能为空");
        }
        if (entity.getAdapterName() == null || entity.getAdapterName().isBlank()) {
            throw new IllegalArgumentException("Adapter 标识名不能为空");
        }
        entity.setAdapterName(entity.getAdapterName().trim());
        entity.setStatus(VirtualLeaseStatus.normalize(entity.getStatus()));
        return super.save(entity);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public VirtualLease beginLease(Long physicalInstanceId, Long taskId) {
        DeviceInstances physical = requirePhysicalBinding(physicalInstanceId);
        if (taskId != null) {
            VirtualLease inflight = findInFlight(physicalInstanceId, taskId);
            if (inflight != null) {
                if (VirtualLeaseStatus.RELEASING.equals(inflight.getStatus())) {
                    throw new IllegalStateException("租约正在释放，不能再次申请");
                }
                publishLeaseRequest(inflight, "LEASE", physical.getBoundDevicePoint());
                return inflight;
            }
        }
        VirtualLease lease = new VirtualLease();
        lease.setPhysicalInstanceId(physicalInstanceId);
        lease.setAdapterName(physical.getBoundAdapterName().trim());
        lease.setTaskId(taskId);
        lease.setStatus(VirtualLeaseStatus.LEASING);
        save(lease);
        publishLeaseRequest(lease, "LEASE", physical.getBoundDevicePoint());
        return lease;
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public VirtualLease beginRelease(Long leaseId) {
        if (leaseId == null) {
            throw new IllegalArgumentException("租约ID不能为空");
        }
        VirtualLease lease = mapper.selectById(leaseId);
        if (lease == null) {
            throw new IllegalArgumentException("虚拟租约不存在: " + leaseId);
        }
        String status = lease.getStatus();
        if (VirtualLeaseStatus.RELEASED.equals(status)) {
            completeWaiter(lease);
            return lease;
        }
        if (VirtualLeaseStatus.FAILED.equals(status)) {
            throw new IllegalStateException("失败的租约不能再发 RELEASE");
        }
        String virtualPoint = lease.getVirtualDevicePoint();
        if (VirtualLeaseStatus.LEASING.equals(status)) {
            lease.setStatus(VirtualLeaseStatus.FAILED);
            lease.setCancelledTime(OffsetDateTime.now());
            mapper.updateById(lease);
            completeWaiter(lease);
            return lease;
        }
        if (virtualPoint == null || virtualPoint.isBlank()) {
            throw new IllegalStateException("租约没有虚拟点位，无法 RELEASE: " + leaseId);
        }
        // 已在注销中：只重发 RELEASE，等 Adapter 回 RELEASED，不改库
        if (VirtualLeaseStatus.RELEASING.equals(status)) {
            publishLeaseRequest(lease, "RELEASE", virtualPoint);
            return lease;
        }
        lease.setStatus(VirtualLeaseStatus.RELEASING);
        lease.setLastUsedTime(OffsetDateTime.now());
        mapper.updateById(lease);
        publishLeaseRequest(lease, "RELEASE", virtualPoint);
        return lease;
    }

    public ObjectNode buildLeaseRequest(VirtualLease lease, String action, String devicePoint) {
        if (lease == null || lease.getId() == null) {
            throw new IllegalArgumentException("租约尚未保存，无法构造租赁请求");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action不能为空");
        }
        if (devicePoint == null || devicePoint.isBlank()) {
            throw new IllegalArgumentException("devicePoint不能为空");
        }
        ObjectNode request = JsonNodeSupport.objectNode();
        request.put("leaseId", lease.getId());
        request.put("adapterName", lease.getAdapterName());
        request.put("action", action.trim().toUpperCase());
        request.put("devicePoint", devicePoint.trim());
        request.put("timestamp", System.currentTimeMillis());
        return request;
    }

    @EventListener
    public void onAdapterLeaseResult(AdapterLeaseResultEvent event) {
        if (event == null || event.payload() == null) {
            return;
        }
        proxy().applyLeaseResult(event.payload());
    }

    @Transactional(rollbackFor = Exception.class)
    public VirtualLease applyLeaseResult(JsonNode result) {
        if (result == null || !result.isObject()) {
            throw new IllegalArgumentException("LeaseResultFormat 必须是对象");
        }
        long leaseId = result.path("leaseId").asLong(0);
        if (leaseId <= 0) {
            throw new IllegalArgumentException("leaseId必须是正整数");
        }
        VirtualLease lease = mapper.selectById(leaseId);
        if (lease == null) {
            throw new IllegalArgumentException("虚拟租约不存在: " + leaseId);
        }
        String adapterName = result.path("adapterName").asText("").trim();
        if (!lease.getAdapterName().equals(adapterName)) {
            throw new IllegalArgumentException("租约结果 adapterName 与库中不一致");
        }
        String action = result.path("action").asText("").trim().toUpperCase();
        String status = result.path("status").asText("").trim().toUpperCase();
        String devicePoint = result.path("devicePoint").asText("").trim();
        // 迟到的 RELEASED：已收尸则幂等返回，避免重复删虚拟实例
        if (VirtualLeaseStatus.RELEASED.equals(lease.getStatus())
                && "RELEASE".equals(action)
                && "RELEASED".equals(status)) {
            return lease;
        }
        if ("LEASE".equals(action) && "GRANTED".equals(status)) {
            if (devicePoint.isBlank()) {
                throw new IllegalArgumentException("LEASE GRANTED 必须携带虚拟点位");
            }
            if (isMqttUnsafeSegment(devicePoint)) {
                rememberFailReason(leaseId, "虚拟点位不能包含 MQTT 分隔符或通配符: " + devicePoint);
                lease.setStatus(VirtualLeaseStatus.FAILED);
            } else {
                lease.setVirtualDevicePoint(devicePoint);
                DeviceInstances physical = deviceInstanceService.requireUsable(lease.getPhysicalInstanceId());
                DeviceInstances virtual = deviceInstanceService.findVirtual(lease.getAdapterName(), devicePoint);
                if (virtual == null) {
                    virtual = deviceInstanceService.createVirtual(physical, devicePoint);
                }
                attachDefaultDataIndex(lease, virtual);
                lease.setStatus(VirtualLeaseStatus.ACTIVE);
                lease.setLastUsedTime(OffsetDateTime.now());
            }
        } else if ("LEASE".equals(action) && "FAILED".equals(status)) {
            rememberFailReason(leaseId, result.path("errorMessage").asText("").trim());
            lease.setStatus(VirtualLeaseStatus.FAILED);
        } else if ("RELEASE".equals(action) && "RELEASED".equals(status)) {
            String releasedPoint = devicePoint.isBlank() ? lease.getVirtualDevicePoint() : devicePoint;
            finalizeReleased(lease, releasedPoint);
        } else if ("RELEASE".equals(action) && "FAILED".equals(status)) {
            rememberFailReason(leaseId, result.path("errorMessage").asText("").trim());
            lease.setStatus(VirtualLeaseStatus.FAILED);
        } else {
            throw new IllegalArgumentException("不支持的租约结果组合: action=" + action + ", status=" + status);
        }
        mapper.updateById(lease);
        VirtualLease updated = mapper.selectById(leaseId);
        completeWaiter(updated);
        return updated;
    }

    public void acquireForTask(Collection<Long> physicalInstanceIds, Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("模拟任务租约必须带 taskId");
        }
        Set<Long> uniqueIds = uniquePhysicalIds(physicalInstanceIds);
        List<VirtualLease> leases = new ArrayList<>();
        try {
            for (Long physicalId : uniqueIds) {
                leases.add(proxy().beginLease(physicalId, taskId));
            }
            Duration timeout = grantTimeout();
            for (VirtualLease lease : leases) {
                VirtualLease settled = awaitOutcome(lease.getId(), timeout);
                if (!VirtualLeaseStatus.ACTIVE.equals(settled.getStatus())) {
                    throw new IllegalStateException(grantFailedMessage("虚拟点租约未授予", settled));
                }
            }
        } catch (RuntimeException error) {
            releaseForTask(taskId);
            throw error;
        }
    }

    public void releaseForTask(Long taskId) {
        if (taskId == null) {
            return;
        }
        List<VirtualLease> leases = mapper.selectList(Wrappers.<VirtualLease>lambdaQuery()
                .eq(VirtualLease::getTaskId, taskId)
                .in(VirtualLease::getStatus, IN_FLIGHT)
                .orderByAsc(VirtualLease::getId));
        if (leases == null || leases.isEmpty()) {
            return;
        }
        for (VirtualLease lease : leases) {
            try {
                proxy().beginRelease(lease.getId());
            } catch (RuntimeException error) {
                log.warn("模拟任务释放租约失败: taskId={}, leaseId={}", taskId, lease.getId(), error);
            }
        }
    }

    public DeviceInstances requireActiveVirtual(Long physicalInstanceId, Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("查找任务虚拟机必须带 taskId");
        }
        VirtualLease lease = findInFlight(physicalInstanceId, taskId);
        if (lease == null || !VirtualLeaseStatus.ACTIVE.equals(lease.getStatus())) {
            throw new IllegalStateException("模拟执行没有生效的虚拟点租约: physicalInstanceId=" + physicalInstanceId);
        }
        return requireVirtualInstance(lease);
    }

    public VirtualMachineView applyPokeVirtual(Long physicalInstanceId) {
        VirtualLease lease = proxy().beginLease(physicalInstanceId, null);
        VirtualLease settled = awaitOutcome(lease.getId(), grantTimeout());
        if (!VirtualLeaseStatus.ACTIVE.equals(settled.getStatus())) {
            try {
                proxy().beginRelease(lease.getId());
            } catch (RuntimeException ignored) {
            }
            throw new IllegalStateException(grantFailedMessage("点动虚拟机租约未授予", settled));
        }
        return toView(settled, requireVirtualInstance(settled));
    }

    public Set<Long> listActiveVirtualInstanceIds(Long physicalInstanceId) {
        if (physicalInstanceId == null) {
            return Set.of();
        }
        List<VirtualLease> leases = mapper.selectList(Wrappers.<VirtualLease>lambdaQuery()
                .eq(VirtualLease::getPhysicalInstanceId, physicalInstanceId)
                .eq(VirtualLease::getStatus, VirtualLeaseStatus.ACTIVE)
                .orderByAsc(VirtualLease::getId));
        if (leases == null || leases.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (VirtualLease lease : leases) {
            try {
                ids.add(requireVirtualInstance(lease).getId());
            } catch (RuntimeException error) {
                log.warn("生效租约缺少虚拟实例: leaseId={}", lease.getId(), error);
            }
        }
        return Set.copyOf(ids);
    }

    public List<VirtualMachineView> listPokeVirtuals(Long physicalInstanceId) {
        if (physicalInstanceId == null) {
            throw new IllegalArgumentException("物理实例ID不能为空");
        }
        List<VirtualLease> leases = mapper.selectList(Wrappers.<VirtualLease>lambdaQuery()
                .eq(VirtualLease::getPhysicalInstanceId, physicalInstanceId)
                .isNull(VirtualLease::getTaskId)
                .eq(VirtualLease::getStatus, VirtualLeaseStatus.ACTIVE)
                .orderByDesc(VirtualLease::getId));
        if (leases == null || leases.isEmpty()) {
            return List.of();
        }
        List<VirtualMachineView> result = new ArrayList<>();
        for (VirtualLease lease : leases) {
            try {
                result.add(toView(lease, requireVirtualInstance(lease)));
            } catch (RuntimeException error) {
                log.warn("点动虚拟机台账缺少实例: leaseId={}", lease.getId(), error);
            }
        }
        return List.copyOf(result);
    }

    public void releasePokeVirtual(Long leaseId) {
        if (leaseId == null) {
            throw new IllegalArgumentException("租约ID不能为空");
        }
        VirtualLease lease = mapper.selectById(leaseId);
        if (lease == null) {
            throw new IllegalArgumentException("虚拟租约不存在: " + leaseId);
        }
        if (lease.getTaskId() != null) {
            throw new IllegalStateException("任务虚拟机不能从点动入口注销");
        }
        proxy().beginRelease(leaseId);
    }

    public void touchLastUsedForInstance(Long instanceId) {
        if (instanceId == null) {
            return;
        }
        DeviceInstances instance = deviceInstanceService.requireUsable(instanceId);
        if (!DeviceInstanceKind.isVirtual(instance)) {
            return;
        }
        VirtualLease lease = mapper.selectOne(Wrappers.<VirtualLease>lambdaQuery()
                .eq(VirtualLease::getAdapterName, instance.getBoundAdapterName())
                .eq(VirtualLease::getVirtualDevicePoint, instance.getBoundDevicePoint())
                .eq(VirtualLease::getStatus, VirtualLeaseStatus.ACTIVE)
                .isNull(VirtualLease::getTaskId)
                .orderByDesc(VirtualLease::getId)
                .last("limit 1"));
        if (lease == null) {
            return;
        }
        lease.setLastUsedTime(OffsetDateTime.now());
        mapper.updateById(lease);
    }

    public VirtualLease awaitOutcome(Long leaseId, Duration timeout) {
        if (leaseId == null) {
            throw new IllegalArgumentException("租约ID不能为空");
        }
        Duration wait = timeout == null || timeout.isNegative() || timeout.isZero() ? grantTimeout() : timeout;
        VirtualLease current = mapper.selectById(leaseId);
        if (isGrantSettled(current)) {
            return current;
        }
        CompletableFuture<VirtualLease> waiter = outcomes.computeIfAbsent(leaseId, id -> new CompletableFuture<>());
        current = mapper.selectById(leaseId);
        if (isGrantSettled(current)) {
            waiter.complete(current);
            outcomes.remove(leaseId, waiter);
            return current;
        }
        try {
            return waiter.get(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeoutError) {
            throw new IllegalStateException("等待 Adapter 租约结果超时: " + leaseId);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待租约结果被中断: " + leaseId, interrupted);
        } catch (ExecutionException execution) {
            Throwable cause = execution.getCause() == null ? execution : execution.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("等待租约结果失败: " + leaseId, cause);
        } finally {
            outcomes.remove(leaseId, waiter);
        }
    }

    @Scheduled(fixedDelayString = "${smartlab.virtual-lease.poke-expire-scan-ms:30000}")
    public void expireStalePokeLeases() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(pokeIdleTimeoutSeconds);
        List<VirtualLease> candidates = mapper.selectList(Wrappers.<VirtualLease>lambdaQuery()
                .isNull(VirtualLease::getTaskId)
                .in(VirtualLease::getStatus, VirtualLeaseStatus.LEASING, VirtualLeaseStatus.ACTIVE)
                .orderByAsc(VirtualLease::getId));
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        for (VirtualLease lease : candidates) {
            if (!isPokeIdleExpired(lease, cutoff)) {
                continue;
            }
            try {
                proxy().beginRelease(lease.getId());
            } catch (RuntimeException error) {
                log.warn("点动虚拟机空闲超时释放失败: leaseId={}", lease.getId(), error);
            }
        }
    }

    @Override
    public void delete(Serializable id) {
        VirtualLease lease = mapper.selectById(id);
        if (lease == null) {
            throw new IllegalArgumentException("虚拟租约不存在: " + id);
        }
        String status = lease.getStatus();
        if (IN_FLIGHT.contains(status)) {
            throw new IllegalStateException("进行中的租约不能直接删除，请先走 RELEASE");
        }
        super.delete(id);
    }

    private VirtualMachineView toView(VirtualLease lease, DeviceInstances virtual) {
        return new VirtualMachineView(
                lease.getPhysicalInstanceId(),
                virtual.getId(),
                lease.getId(),
                lease.getVirtualDevicePoint(),
                virtual.getInstanceName(),
                lease.getStatus(),
                lease.getLastUsedTime(),
                lease.getCreateTime());
    }

    private void attachDefaultDataIndex(VirtualLease lease, DeviceInstances virtual) {
        if (lease == null || lease.getDataIndexId() != null || virtual == null || dataIndexService == null) {
            return;
        }
        List<DataIndex> datasets = dataIndexService.listByDeviceInstance(virtual.getId());
        if (datasets == null || datasets.isEmpty()) {
            return;
        }
        lease.setDataIndexId(datasets.get(0).getId());
    }

    /**
     * 将租约收尸为 RELEASED：任务租约解绑数据表、删除虚拟实例。调用方负责 updateById / completeWaiter。
     */
    private void finalizeReleased(VirtualLease lease, String releasedPoint) {
        if (lease == null) {
            return;
        }
        if (releasedPoint != null && !releasedPoint.isBlank()) {
            lease.setVirtualDevicePoint(releasedPoint);
            DeviceInstances virtual = deviceInstanceService.findVirtual(lease.getAdapterName(), releasedPoint);
            if (virtual != null) {
                if (lease.getTaskId() != null) {
                    attachDefaultDataIndex(lease, virtual);
                    if (dataIndexService != null) {
                        dataIndexService.detachFromInstance(virtual.getId());
                    }
                }
                deviceInstanceService.deleteVirtual(virtual.getId());
            }
        }
        lease.setStatus(VirtualLeaseStatus.RELEASED);
        lease.setCancelledTime(OffsetDateTime.now());
    }

    private DeviceInstances requireVirtualInstance(VirtualLease lease) {
        String point = lease.getVirtualDevicePoint();
        if (point == null || point.isBlank()) {
            throw new IllegalStateException("租约已生效但没有虚拟点位: leaseId=" + lease.getId());
        }
        DeviceInstances virtual = deviceInstanceService.findVirtual(lease.getAdapterName(), point);
        if (virtual == null) {
            throw new IllegalStateException("租约已生效但虚拟实例不存在: " + lease.getAdapterName() + "/" + point);
        }
        return virtual;
    }

    private String grantFailedMessage(String prefix, VirtualLease settled) {
        String reason = lastFailReasons.remove(settled.getId());
        String message = prefix + ": leaseId=" + settled.getId() + ", status=" + settled.getStatus();
        if (reason == null || reason.isBlank()) {
            return message;
        }
        return message + ", reason=" + reason;
    }

    private void rememberFailReason(long leaseId, String reason) {
        if (reason == null || reason.isBlank()) {
            return;
        }
        lastFailReasons.put(leaseId, reason);
        log.warn("Adapter 租约失败: leaseId={}, reason={}", leaseId, reason);
    }

    private static boolean isMqttUnsafeSegment(String devicePoint) {
        try {
            MqttTopic.requireSafeSegment("devicePoint", devicePoint);
            return false;
        } catch (IllegalArgumentException ignored) {
            return true;
        }
    }

    private DeviceInstances requirePhysicalBinding(Long physicalInstanceId) {
        DeviceInstances physical = deviceInstanceService.requireUsable(physicalInstanceId);
        if (!DeviceInstanceKind.isPhysical(physical)) {
            throw new IllegalStateException("只能为物理设备实例申请虚拟点租约");
        }
        if (physical.getBoundAdapterName() == null || physical.getBoundAdapterName().isBlank()
                || physical.getBoundDevicePoint() == null || physical.getBoundDevicePoint().isBlank()) {
            throw new IllegalStateException("物理实例未绑定 Adapter 点位，无法申请租约: " + physicalInstanceId);
        }
        return physical;
    }

    private VirtualLease findInFlight(Long physicalInstanceId, Long taskId) {
        if (physicalInstanceId == null || taskId == null) {
            return null;
        }
        return mapper.selectOne(Wrappers.<VirtualLease>lambdaQuery()
                .eq(VirtualLease::getPhysicalInstanceId, physicalInstanceId)
                .in(VirtualLease::getStatus, IN_FLIGHT)
                .eq(VirtualLease::getTaskId, taskId)
                .orderByDesc(VirtualLease::getId)
                .last("limit 1"));
    }

    private boolean isPokeIdleExpired(VirtualLease lease, OffsetDateTime cutoff) {
        if (lease == null || lease.getTaskId() != null) {
            return false;
        }
        OffsetDateTime lastUsed = lease.getLastUsedTime();
        if (lastUsed != null) {
            return lastUsed.isBefore(cutoff);
        }
        OffsetDateTime created = lease.getCreateTime();
        return created != null && created.isBefore(cutoff);
    }

    private VirtualLeaseService proxy() {
        return self != null ? self : this;
    }

    private Duration grantTimeout() {
        return Duration.ofSeconds(grantTimeoutSeconds);
    }

    private Set<Long> uniquePhysicalIds(Collection<Long> physicalInstanceIds) {
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        if (physicalInstanceIds == null) {
            return unique;
        }
        for (Long id : physicalInstanceIds) {
            if (id != null && id > 0) {
                unique.add(id);
            }
        }
        return unique;
    }

    private boolean isGrantSettled(VirtualLease lease) {
        if (lease == null) {
            return false;
        }
        String status = lease.getStatus();
        return VirtualLeaseStatus.ACTIVE.equals(status) || VirtualLeaseStatus.FAILED.equals(status);
    }

    private void completeWaiter(VirtualLease lease) {
        if (lease == null || lease.getId() == null) {
            return;
        }
        if (!isGrantSettled(lease) && !VirtualLeaseStatus.RELEASED.equals(lease.getStatus())) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    signalWaiter(lease);
                }
            });
        } else {
            signalWaiter(lease);
        }
    }

    private void signalWaiter(VirtualLease lease) {
        CompletableFuture<VirtualLease> waiter = outcomes.computeIfAbsent(lease.getId(), id -> new CompletableFuture<>());
        waiter.complete(lease);
    }

    private void publishLeaseRequest(VirtualLease lease, String action, String devicePoint) {
        if (mqttAdapterMessagingService == null) {
            throw new IllegalStateException("MQTT 未装配，无法发送租约请求");
        }
        ObjectNode request = buildLeaseRequest(lease, action, devicePoint);
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    mqttAdapterMessagingService.publishLeaseRequest(request);
                }
            });
        } else {
            mqttAdapterMessagingService.publishLeaseRequest(request);
        }
    }
}
