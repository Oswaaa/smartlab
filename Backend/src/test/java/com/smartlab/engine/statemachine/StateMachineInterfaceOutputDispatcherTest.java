package com.smartlab.engine.statemachine;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StateMachineInterfaceOutputDispatcherTest {

    @Test
    void ordersCompetingStateSignalsByExceptionCommandAndOperational() {
        StateMachineInterfaceOutputDispatcher dispatcher = new StateMachineInterfaceOutputDispatcher();
        List<String> sent = new ArrayList<>();

        dispatcher.submitBatch(7L, List.of(
                request(operation("Mode", "OPERATIONAL")),
                request(command()),
                request(operation("Exception", "EXCEPTION"))
        ), request -> sent.add(label(request.signal())));

        assertEquals(List.of("EXCEPTION", "CMD_STATE", "OPERATIONAL"), sent);
    }

    @Test
    void exceptionInsertedDuringDispatchPrecedesOnlySignalsNotYetSent() {
        StateMachineInterfaceOutputDispatcher dispatcher = new StateMachineInterfaceOutputDispatcher();
        List<String> sent = new ArrayList<>();

        dispatcher.submitBatch(7L, List.of(request(command()), request(operation("Mode", "OPERATIONAL"))), request -> {
            sent.add(label(request.signal()));
            if (sent.size() == 1) {
                dispatcher.submitBatch(7L, List.of(request(operation("Exception", "EXCEPTION"))),
                        inserted -> sent.add(label(inserted.signal())));
            }
        });

        assertEquals(List.of("CMD_STATE", "EXCEPTION", "OPERATIONAL"), sent);
    }

    private StateMachineInterfaceOutputDispatcher.OutputRequest request(ObjectNode signal) {
        return new StateMachineInterfaceOutputDispatcher.OutputRequest(
                "Interface_state_out", "STATE", signal, Map.of());
    }

    private ObjectNode command() {
        ObjectNode signal = JsonNodeSupport.objectNode();
        signal.put("signalName", "CMD_STATE");
        signal.putObject("payload").put("stateName", "RUNNING");
        return signal;
    }

    private ObjectNode operation(String regionName, String regionType) {
        ObjectNode signal = JsonNodeSupport.objectNode();
        signal.put("signalName", "OP_STATE");
        ObjectNode payload = signal.putObject("payload");
        payload.put("regionName", regionName);
        payload.put("regionType", regionType);
        payload.putArray("state").add("ACTIVE");
        return signal;
    }

    private String label(ObjectNode signal) {
        if ("CMD_STATE".equals(signal.path("signalName").asText())) return "CMD_STATE";
        return signal.path("payload").path("regionType").asText();
    }
}
