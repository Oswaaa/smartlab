package com.smartlab.engine.observation.device;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.observation.ObservableKey;
import com.smartlab.engine.observation.ObservationHistoryStore;
import com.smartlab.engine.observation.ObservationStatus;
import com.smartlab.engine.observation.SnapshotOrigin;
import com.smartlab.engine.observation.event.ObservableChangedEvent;
import com.smartlab.engine.observation.event.ObservableTopologyChangedEvent;
import com.smartlab.global.contract.ObservableObjectType;
import com.smartlab.global.event.DeviceTelemetryUpdatedEvent;
import com.smartlab.global.event.DeviceInstanceRetiredEvent;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceInstanceLifecycle;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceTwinStatesMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DeviceTwinSnapshotRegistry {
    private final ConcurrentHashMap<Long, AtomicReference<DeviceTwinSnapshot>> snapshots = new ConcurrentHashMap<>();
    private final DeviceTwinStatesMapper twinStatesMapper;
    private final DeviceInstancesMapper instancesMapper;
    private final ApplicationEventPublisher events;
    private final ObservationHistoryStore history;

    /** Compatibility constructor for focused unit tests. */
    public DeviceTwinSnapshotRegistry(DeviceTwinStatesMapper twinStatesMapper,
                                      DeviceInstancesMapper instancesMapper,
                                      ApplicationEventPublisher events) {
        this(twinStatesMapper, instancesMapper, events, new ObservationHistoryStore());
    }

    @Autowired
    public DeviceTwinSnapshotRegistry(DeviceTwinStatesMapper twinStatesMapper,
                                      DeviceInstancesMapper instancesMapper,
                                      ApplicationEventPublisher events,
                                      ObservationHistoryStore history) {
        this.twinStatesMapper = twinStatesMapper;
        this.instancesMapper = instancesMapper;
        this.events = events;
        this.history = history;
    }

    @PostConstruct
    public void restorePersistedState() {
        for (DeviceTwinStates state : twinStatesMapper.selectList(Wrappers.lambdaQuery())) {
            DeviceInstances instance = instancesMapper.selectById(state.getInstanceId());
            if (!DeviceInstanceLifecycle.isUsable(instance)) continue;
            Long modelId = instance.getDeviceModelId();
            Instant observedAt = state.getUpdateTime() == null ? Instant.now()
                    : state.getUpdateTime().toInstant();
            restore(state.getInstanceId(), modelId, state.getCurrentAttr(), state.getOnlineStatus(), observedAt);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void observeTelemetry(DeviceTelemetryUpdatedEvent event) {
        updateAttributes(event.deviceInstanceId(), event.deviceModelId(), event.attributes(), event.occurredAt());
    }

    public DeviceTwinSnapshot updateAttributes(Long instanceId, Long modelId, JsonNode changed, Instant observedAt) {
        if (instanceId == null || changed == null || !changed.isObject()) return snapshot(instanceId);
        AtomicReference<DeviceTwinSnapshot> reference = snapshots.computeIfAbsent(instanceId,
                ignored -> new AtomicReference<>());
        DeviceTwinSnapshot updated;
        boolean created;
        while (true) {
            DeviceTwinSnapshot previous = reference.get();
            created = previous == null;
            ObjectNode merged = previous != null && previous.attributes() != null && previous.attributes().isObject()
                    ? (ObjectNode) previous.attributes() : JsonNodeSupport.objectNode();
            changed.fields().forEachRemaining(entry -> merged.set(entry.getKey(), entry.getValue().deepCopy()));
            long revision = previous == null ? 1 : previous.revision() + 1;
            Instant now = Instant.now();
            updated = new DeviceTwinSnapshot(instanceId,
                    modelId == null && previous != null ? previous.deviceModelId() : modelId,
                    merged, "ONLINE", observedAt == null ? now : observedAt, now, revision,
                    ObservationStatus.VALID, SnapshotOrigin.LIVE);
            if (reference.compareAndSet(previous, updated)) break;
        }
        if (created) events.publishEvent(new ObservableTopologyChangedEvent("DEVICE", instanceId));
        var names = changed.fieldNames();
        while (names.hasNext()) {
            String targetName = names.next();
            history.append(attributeKey(instanceId, targetName), updated.attributes().get(targetName),
                    updated.observedAt(), updated.revision());
            events.publishEvent(new ObservableChangedEvent(attributeKey(instanceId, targetName),
                    updated.revision(), updated.updatedAt()));
        }
        return copy(updated);
    }

    public void restore(Long instanceId, Long modelId, JsonNode attributes, String onlineStatus, Instant observedAt) {
        if (instanceId == null) return;
        Instant time = observedAt == null ? Instant.now() : observedAt;
        snapshots.put(instanceId, new AtomicReference<>(new DeviceTwinSnapshot(instanceId, modelId, attributes,
                onlineStatus, time, Instant.now(), 1, ObservationStatus.STALE, SnapshotOrigin.RECOVERED)));
    }

    /** Releases the live read model for a retired device; its persisted audit snapshot remains intact. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleInstanceRetired(DeviceInstanceRetiredEvent event) {
        remove(event == null ? null : event.deviceInstanceId());
    }

    public void remove(Long instanceId) {
        if (instanceId == null) return;
        AtomicReference<DeviceTwinSnapshot> removed = snapshots.remove(instanceId);
        history.removeIf(key -> key.sourceType() == ObservableObjectType.DEVICE_ATTRIBUTE
                && instanceId.equals(key.deviceInstanceId()));
        if (removed != null) {
            events.publishEvent(new ObservableTopologyChangedEvent("DEVICE", instanceId));
        }
    }

    public DeviceTwinSnapshot snapshot(Long instanceId) {
        AtomicReference<DeviceTwinSnapshot> reference = instanceId == null ? null : snapshots.get(instanceId);
        return reference == null ? null : copy(reference.get());
    }

    public Map<Long, DeviceTwinSnapshot> snapshotBatch(Collection<Long> ids) {
        Map<Long, DeviceTwinSnapshot> result = new LinkedHashMap<>();
        if (ids != null) for (Long id : ids) {
            DeviceTwinSnapshot value = snapshot(id);
            if (value != null) result.put(id, value);
        }
        return Map.copyOf(result);
    }

    public Map<Long, DeviceTwinSnapshot> snapshotAll() {
        return snapshotBatch(snapshots.keySet());
    }

    public Map<Long, DeviceTwinSnapshotVersion> snapshotVersions() {
        Map<Long, DeviceTwinSnapshotVersion> result = new LinkedHashMap<>();
        snapshots.forEach((instanceId, reference) -> {
            DeviceTwinSnapshot value = reference.get();
            if (value != null) {
                result.put(instanceId, new DeviceTwinSnapshotVersion(
                        value.deviceInstanceId(), value.deviceModelId(), value.revision()));
            }
        });
        return Map.copyOf(result);
    }

    public static ObservableKey attributeKey(Long instanceId, String targetName) {
        return new ObservableKey(ObservableObjectType.DEVICE_ATTRIBUTE, instanceId, null, null,
                null, null, targetName, null);
    }

    private DeviceTwinSnapshot copy(DeviceTwinSnapshot value) {
        return value == null ? null : new DeviceTwinSnapshot(value.deviceInstanceId(), value.deviceModelId(),
                value.attributes(), value.onlineStatus(), value.observedAt(), value.updatedAt(), value.revision(),
                value.status(), value.origin());
    }
}
