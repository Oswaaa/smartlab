package com.smartlab.engine.observation;

import com.smartlab.engine.observation.device.DeviceTwinSnapshotRegistry;
import com.smartlab.engine.observation.statemachine.StateMachineObservationRegistry;
import com.smartlab.engine.observation.workflow.WorkflowRuntimeSnapshotRegistry;
import com.smartlab.global.contract.ObservableObjectType;
import com.smartlab.global.util.JsonNodeSupport;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObservableSnapshotServiceTest {
    @Test
    void returnsUnavailableInsteadOfReadingDatabaseOnMissingValue() {
        DeviceTwinSnapshotRegistry devices = mock(DeviceTwinSnapshotRegistry.class);
        ObservableSnapshotService service = new ObservableSnapshotService(devices,
                mock(StateMachineObservationRegistry.class), mock(WorkflowRuntimeSnapshotRegistry.class));
        ObservableKey key = new ObservableKey(ObservableObjectType.DEVICE_ATTRIBUTE, 9L, null, null,
                null, null, "temperature", null);
        assertEquals(ObservationStatus.UNAVAILABLE, service.read(key).status());
    }
}
