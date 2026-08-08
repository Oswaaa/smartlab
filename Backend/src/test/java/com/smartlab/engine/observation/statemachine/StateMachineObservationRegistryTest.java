package com.smartlab.engine.observation.statemachine;

import com.smartlab.engine.observation.ObservableKey;
import com.smartlab.engine.observation.ObservationHistoryStore;
import com.smartlab.engine.observation.ObservationStatus;
import com.smartlab.global.contract.ObservableObjectType;
import com.smartlab.global.event.DeviceInstanceRetiredEvent;
import com.smartlab.global.event.DeviceInstanceSavedEvent;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceTwinStatesMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StateMachineObservationRegistryTest {

    @Test
    void startupSkipsRetiredDevicesAndRetirementClearsLiveState() {
        DeviceTwinStatesMapper twins = mock(DeviceTwinStatesMapper.class);
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceTwinStates persisted = new DeviceTwinStates();
        persisted.setInstanceId(7L);
        persisted.setCurrentCmdState("IDLE");
        DeviceInstances retired = new DeviceInstances();
        retired.setId(7L);
        retired.setLifecycleStatus("已注销");
        when(twins.selectList(any())).thenReturn(List.of(persisted));
        when(instances.selectById(7L)).thenReturn(retired);
        ObservationHistoryStore history = new ObservationHistoryStore();
        StateMachineObservationRegistry registry = new StateMachineObservationRegistry(
                mock(ApplicationEventPublisher.class), twins, instances, history);
        ObservableKey key = new ObservableKey(ObservableObjectType.DEVICE_COMMAND_LIFECYCLE,
                7L, null, null, null, null, null, null);

        registry.restorePersistedState();
        assertEquals(ObservationStatus.UNAVAILABLE, registry.read(key).status());

        var signal = JsonNodeSupport.objectNode();
        signal.put("signalName", "CMD_STATE");
        signal.putObject("payload").put("stateName", "IDLE").put("timestamp", Instant.now().toEpochMilli());
        registry.observe(new com.smartlab.engine.statemachine.StateMachineInterfaceSignalEvent(
                7L, "Interface_state_out", "STATE", signal, java.util.Map.of()));
        assertEquals(ObservationStatus.VALID, registry.read(key).status());

        registry.handleInstanceRetired(new DeviceInstanceRetiredEvent(7L));
        assertEquals(ObservationStatus.UNAVAILABLE, registry.read(key).status());
        assertEquals(List.of(), history.read(key, Instant.now().minusSeconds(5)));
    }

    @Test
    void newInstanceRestoresItsInitialStateAfterCommit() {
        DeviceTwinStatesMapper twins = mock(DeviceTwinStatesMapper.class);
        DeviceTwinStates persisted = new DeviceTwinStates();
        persisted.setInstanceId(7L);
        persisted.setCurrentCmdState("IDLE");
        persisted.setCurrentOpState(JsonNodeSupport.objectNode()
                .set("Exception", JsonNodeSupport.arrayNode()));
        when(twins.selectOne(any())).thenReturn(persisted);
        StateMachineObservationRegistry registry = new StateMachineObservationRegistry(
                mock(ApplicationEventPublisher.class), twins);

        registry.handleInstanceSaved(new DeviceInstanceSavedEvent(7L, 9L, true));

        ObservableKey command = new ObservableKey(ObservableObjectType.DEVICE_COMMAND_LIFECYCLE,
                7L, null, null, null, null, null, null);
        ObservableKey exception = new ObservableKey(ObservableObjectType.DEVICE_OPERATION_STATE,
                7L, null, null, "Exception", null, null, null);
        assertEquals("IDLE", registry.read(command).value().asText());
        assertEquals(0, registry.read(exception).value().size());
    }
}
