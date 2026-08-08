package com.smartlab.engine.observation;

import com.smartlab.engine.observation.device.DeviceTwinSnapshot;
import com.smartlab.engine.observation.device.DeviceTwinSnapshotRegistry;
import com.smartlab.engine.observation.statemachine.StateMachineObservationRegistry;
import com.smartlab.engine.observation.workflow.WorkflowRuntimeSnapshotRegistry;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ObservableSnapshotService implements ObservableSnapshotReader {
    private final DeviceTwinSnapshotRegistry devices;
    private final StateMachineObservationRegistry stateMachines;
    private final WorkflowRuntimeSnapshotRegistry workflows;
    private final ObservationHistoryStore history;

    /** Compatibility constructor for focused unit tests. */
    public ObservableSnapshotService(DeviceTwinSnapshotRegistry devices,
                                     StateMachineObservationRegistry stateMachines,
                                     WorkflowRuntimeSnapshotRegistry workflows) {
        this(devices, stateMachines, workflows, new ObservationHistoryStore());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ObservableSnapshotService(DeviceTwinSnapshotRegistry devices,
                                     StateMachineObservationRegistry stateMachines,
                                     WorkflowRuntimeSnapshotRegistry workflows,
                                     ObservationHistoryStore history) {
        this.devices = devices;
        this.stateMachines = stateMachines;
        this.workflows = workflows;
        this.history = history;
    }

    public ObservationSnapshot read(ObservableKey key) {
        return switch (key.sourceType()) {
            case DEVICE_ATTRIBUTE -> deviceAttribute(key);
            case DEVICE_OPERATION_STATE, DEVICE_COMMAND_LIFECYCLE -> stateMachines.read(key);
            case NODE_LIFECYCLE_STATE, NODE_INTERNAL_VARIABLE, TASK_LIFECYCLE_STATE -> workflows.read(key);
        };
    }

    public Map<ObservableKey, ObservationSnapshot> readBatch(Collection<ObservableKey> keys) {
        Map<ObservableKey, ObservationSnapshot> result = new LinkedHashMap<>();
        if (keys != null) for (ObservableKey key : keys) result.put(key, read(key));
        return Map.copyOf(result);
    }

    @Override
    public List<ObservationSample> readHistory(ObservableKey key, Instant since) {
        return history.read(key, since);
    }

    @Override
    public Map<ObservableKey, List<ObservationSample>> readHistoryBatch(Collection<ObservableKey> keys,
                                                                         Instant since) {
        return history.readBatch(keys, since);
    }

    private ObservationSnapshot deviceAttribute(ObservableKey key) {
        DeviceTwinSnapshot snapshot = devices.snapshot(key.deviceInstanceId());
        if (snapshot == null || snapshot.attributes() == null) return ObservationSnapshot.unavailable(key);
        var value = snapshot.attributes().get(key.targetName());
        if (value == null) return ObservationSnapshot.unavailable(key);
        return new ObservationSnapshot(key, value, snapshot.observedAt(), snapshot.updatedAt(), snapshot.revision(),
                snapshot.status(), snapshot.origin());
    }
}
