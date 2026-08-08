package com.smartlab.engine.statemachine;

import com.smartlab.engine.observation.ObservationStatus;
import com.smartlab.engine.observation.SnapshotOrigin;
import com.smartlab.engine.observation.device.DeviceTwinSnapshot;
import com.smartlab.engine.observation.device.DeviceTwinSnapshotRegistry;
import com.smartlab.engine.observation.device.DeviceTwinSnapshotVersion;
import com.smartlab.global.util.JsonNodeSupport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IntrinsicConstraintMonitorTest {

    @Test
    void pollsInMemoryAttributeSnapshotsIndependently() {
        DeviceTwinSnapshotRegistry snapshots = mock(DeviceTwinSnapshotRegistry.class);
        IntrinsicConstraintPlanRegistry plans = mock(IntrinsicConstraintPlanRegistry.class);
        StateMachineEngine engine = mock(StateMachineEngine.class);
        var attributes = JsonNodeSupport.objectNode().put("temperature", 205.0);
        DeviceTwinSnapshot snapshot = new DeviceTwinSnapshot(7L, 9L, attributes, "ONLINE",
                Instant.now(), Instant.now(), 1, ObservationStatus.VALID, SnapshotOrigin.LIVE);
        DeviceTwinSnapshotVersion version = new DeviceTwinSnapshotVersion(7L, 9L, 1);
        when(snapshots.snapshotVersions()).thenReturn(Map.of(7L, version));
        when(snapshots.snapshot(7L)).thenReturn(snapshot);
        when(plans.needsEvaluation(version)).thenReturn(true);
        when(plans.needsEvaluation(snapshot)).thenReturn(true);

        new IntrinsicConstraintMonitor(snapshots, plans, engine).scan();

        verify(engine).evaluateIntrinsicConstraints(snapshot);
        verify(plans).markEvaluated(7L, 1);
    }

    @Test
    void skipsAttributeSnapshotWhenRevisionWasAlreadyEvaluated() {
        DeviceTwinSnapshotRegistry snapshots = mock(DeviceTwinSnapshotRegistry.class);
        IntrinsicConstraintPlanRegistry plans = mock(IntrinsicConstraintPlanRegistry.class);
        StateMachineEngine engine = mock(StateMachineEngine.class);
        DeviceTwinSnapshot snapshot = new DeviceTwinSnapshot(7L, 9L,
                JsonNodeSupport.objectNode().put("temperature", 205.0), "ONLINE",
                Instant.now(), Instant.now(), 3, ObservationStatus.VALID, SnapshotOrigin.LIVE);
        DeviceTwinSnapshotVersion version = new DeviceTwinSnapshotVersion(7L, 9L, 3);
        when(snapshots.snapshotVersions()).thenReturn(Map.of(7L, version));
        when(plans.needsEvaluation(version)).thenReturn(false);

        new IntrinsicConstraintMonitor(snapshots, plans, engine).scan();

        verifyNoInteractions(engine);
        verify(plans, never()).markEvaluated(7L, 3);
    }
}
