package com.smartlab.management.sse;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.StateMachineInterfaceSignalEvent;
import com.smartlab.global.util.JsonNodeSupport;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeviceConsoleSseHubTest {

    @Test
    void registerAndBroadcastSignal() {
        DeviceConsoleSseHub hub = new DeviceConsoleSseHub();
        assertThrows(IllegalArgumentException.class, () -> hub.register(null));

        SseEmitter emitter = hub.register(101L);
        assertNotNull(emitter);

        ObjectNode signal = JsonNodeSupport.objectNode();
        signal.put("signalName", "CMD_STATE");
        ObjectNode payload = JsonNodeSupport.objectNode();
        payload.put("stateName", "RUNNING");
        payload.put("messageId", "msg-12345");
        signal.set("payload", payload);

        StateMachineInterfaceSignalEvent event = new StateMachineInterfaceSignalEvent(
                101L, "Interface_state_out", "STATE", signal, Map.of("capabilityName", "heat")
        );

        SseEmitter globalEmitter = hub.registerGlobal();
        assertNotNull(globalEmitter);

        // Broadcast to registered and global emitters
        hub.handleStateMachineSignal(event);

        // Broadcast for untracked instance should broadcast to globalEmitter without failing
        StateMachineInterfaceSignalEvent untrackedEvent = new StateMachineInterfaceSignalEvent(
                999L, "Interface_state_out", "STATE", signal, Map.of()
        );
        hub.handleStateMachineSignal(untrackedEvent);
    }
}
