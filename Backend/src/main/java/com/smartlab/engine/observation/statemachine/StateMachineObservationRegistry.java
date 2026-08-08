package com.smartlab.engine.observation.statemachine;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.engine.observation.ObservableKey;
import com.smartlab.engine.observation.ObservationHistoryStore;
import com.smartlab.engine.observation.ObservationSnapshot;
import com.smartlab.engine.observation.ObservationStatus;
import com.smartlab.engine.observation.SnapshotOrigin;
import com.smartlab.engine.observation.event.ObservableChangedEvent;
import com.smartlab.engine.observation.event.ObservableTopologyChangedEvent;
import com.smartlab.engine.statemachine.StateMachineInterfaceSignalEvent;
import com.smartlab.global.contract.ObservableObjectType;
import com.smartlab.global.event.DeviceInstanceRetiredEvent;
import com.smartlab.global.event.DeviceInstanceSavedEvent;
import com.smartlab.management.entity.resource.device.DeviceInstanceLifecycle;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceTwinStatesMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class StateMachineObservationRegistry {
    private final ConcurrentHashMap<ObservableKey, ObservationSnapshot> values = new ConcurrentHashMap<>();
    private final AtomicLong revisions = new AtomicLong();
    private final ApplicationEventPublisher events;
    private final DeviceTwinStatesMapper twinStatesMapper;
    private final DeviceInstancesMapper instancesMapper;
    private final ObservationHistoryStore history;

    /** Compatibility constructor for focused unit tests. */
    public StateMachineObservationRegistry(ApplicationEventPublisher events,
                                            DeviceTwinStatesMapper twinStatesMapper) {
        this(events, twinStatesMapper, null, new ObservationHistoryStore());
    }

    @Autowired
    public StateMachineObservationRegistry(ApplicationEventPublisher events,
                                            DeviceTwinStatesMapper twinStatesMapper,
                                            DeviceInstancesMapper instancesMapper,
                                            ObservationHistoryStore history) {
        this.events = events;
        this.twinStatesMapper = twinStatesMapper;
        this.instancesMapper = instancesMapper;
        this.history = history;
    }

    @PostConstruct
    public void restorePersistedState() {
        for (DeviceTwinStates state : twinStatesMapper.selectList(Wrappers.lambdaQuery())) {
            if (instancesMapper != null) {
                DeviceInstances instance = instancesMapper.selectById(state.getInstanceId());
                if (!DeviceInstanceLifecycle.isUsable(instance)) continue;
            }
            restoreState(state);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleInstanceSaved(DeviceInstanceSavedEvent event) {
        if (event == null || !event.created() || event.deviceInstanceId() == null) return;
        DeviceTwinStates state = twinStatesMapper.selectOne(Wrappers.<DeviceTwinStates>lambdaQuery()
                .eq(DeviceTwinStates::getInstanceId, event.deviceInstanceId()));
        if (state != null) {
            restoreState(state);
            events.publishEvent(new ObservableTopologyChangedEvent("STATE_MACHINE", event.deviceInstanceId()));
        }
    }

    @EventListener
    public void observe(StateMachineInterfaceSignalEvent event) {
        if (!"STATE".equals(event.interfaceType()) || !"Interface_state_out".equals(event.interfaceName())) return;
        String signalName = event.signal().path("signalName").asText();
        JsonNode payload = event.signal().path("payload");
        Instant observedAt = payload.path("timestamp").canConvertToLong()
                ? Instant.ofEpochMilli(payload.path("timestamp").asLong()) : Instant.now();
        ObservableKey key;
        JsonNode value;
        if ("CMD_STATE".equals(signalName)) {
            key = new ObservableKey(ObservableObjectType.DEVICE_COMMAND_LIFECYCLE, event.instanceId(), null,
                    null, null, null, null, null);
            value = payload.path("stateName");
        } else if ("OP_STATE".equals(signalName)) {
            key = new ObservableKey(ObservableObjectType.DEVICE_OPERATION_STATE, event.instanceId(), null,
                    null, payload.path("regionName").asText(), null, null, null);
            value = payload.path("state");
        } else return;
        update(key, value, observedAt);
    }

    public ObservationSnapshot read(ObservableKey key) {
        ObservationSnapshot value = values.get(key);
        return value == null ? ObservationSnapshot.unavailable(key) : value;
    }

    public Map<ObservableKey, ObservationSnapshot> readBatch(Collection<ObservableKey> keys) {
        Map<ObservableKey, ObservationSnapshot> result = new LinkedHashMap<>();
        if (keys != null) for (ObservableKey key : keys) result.put(key, read(key));
        return Map.copyOf(result);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleInstanceRetired(DeviceInstanceRetiredEvent event) {
        removeDevice(event == null ? null : event.deviceInstanceId());
    }

    public void removeDevice(Long instanceId) {
        if (instanceId == null) return;
        values.keySet().removeIf(key -> instanceId.equals(key.deviceInstanceId())
                && (key.sourceType() == ObservableObjectType.DEVICE_COMMAND_LIFECYCLE
                || key.sourceType() == ObservableObjectType.DEVICE_OPERATION_STATE));
        history.removeIf(key -> instanceId.equals(key.deviceInstanceId())
                && (key.sourceType() == ObservableObjectType.DEVICE_COMMAND_LIFECYCLE
                || key.sourceType() == ObservableObjectType.DEVICE_OPERATION_STATE));
        events.publishEvent(new ObservableTopologyChangedEvent("STATE_MACHINE", instanceId));
    }

    private void update(ObservableKey key, JsonNode value, Instant observedAt) {
        long revision = revisions.incrementAndGet();
        Instant now = Instant.now();
        values.put(key, new ObservationSnapshot(key, value, observedAt, now, revision,
                ObservationStatus.VALID, SnapshotOrigin.LIVE));
        history.append(key, value, observedAt, revision);
        events.publishEvent(new ObservableChangedEvent(key, revision, now));
    }

    private void restore(ObservableKey key, JsonNode value, Instant observedAt) {
        long revision = revisions.incrementAndGet();
        values.put(key, new ObservationSnapshot(key, value, observedAt, Instant.now(), revision,
                ObservationStatus.STALE, SnapshotOrigin.RECOVERED));
    }

    private void restoreState(DeviceTwinStates state) {
        Instant time = state.getUpdateTime() == null ? Instant.now() : state.getUpdateTime().toInstant();
        if (state.getCurrentCmdState() != null) restore(new ObservableKey(
                ObservableObjectType.DEVICE_COMMAND_LIFECYCLE, state.getInstanceId(), null, null,
                null, null, null, null), com.smartlab.global.util.JsonNodeSupport.MAPPER.getNodeFactory()
                .textNode(state.getCurrentCmdState()), time);
        if (state.getCurrentOpState() != null && state.getCurrentOpState().isObject()) {
            state.getCurrentOpState().fields().forEachRemaining(entry -> restore(new ObservableKey(
                    ObservableObjectType.DEVICE_OPERATION_STATE, state.getInstanceId(), null, null,
                    entry.getKey(), null, null, null), entry.getValue(), time));
        }
    }

}
