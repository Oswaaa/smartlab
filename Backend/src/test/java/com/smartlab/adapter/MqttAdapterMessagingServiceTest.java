package com.smartlab.adapter;

import com.smartlab.engine.statemachine.StateMachineSendActionEvent;
import com.smartlab.global.protocol.ProtocolDictionaryService;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.service.db.resource.adapter.AdapterIndexService;
import com.smartlab.management.service.protocol.AdapterPayloadMapperService;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqttAdapterMessagingServiceTest {

    @Test
    void startAndAbortUseDifferentTransportMessages() {
        AdapterPayloadMapperService mapper = mock(AdapterPayloadMapperService.class);
        MqttAdapterMessagingService service = spy(new MqttAdapterMessagingService(
                mock(AdapterIndexService.class), mapper, new ProtocolDictionaryService(), mock(Executor.class)));
        doNothing().when(service).publishCommand(any());
        when(mapper.buildCommandMessage("7", "heat", "msg-1", Map.of("target", 80)))
                .thenReturn(message("start"));
        when(mapper.buildCommandMessage("7", "stopHeat", "msg-2", Map.of())).thenReturn(message("abort"));

        service.handleStateMachineSendAction(new StateMachineSendActionEvent(
                7L, "Interface_adapter_out", "ADAPTER", "CMD_START",
                "heat", "heat_cmd", "msg-1", Map.of("target", 80), JsonNodeSupport.objectNode()));
        service.handleStateMachineSendAction(new StateMachineSendActionEvent(
                7L, "Interface_adapter_out", "ADAPTER", "CMD_ABORT",
                "stopHeat", "stop_heat_cmd", "msg-2", Map.of(), JsonNodeSupport.objectNode()));

        verify(mapper).buildCommandMessage("7", "heat", "msg-1", Map.of("target", 80));
        verify(mapper).buildCommandMessage("7", "stopHeat", "msg-2", Map.of());
        verify(mapper, never()).buildCommandMessage("7", null, "msg-2", Map.of());
    }

    @Test
    void rejectsTelemetryWhosePayloadIdentityDoesNotMatchTheTopic() {
        AdapterPayloadMapperService mapper = mock(AdapterPayloadMapperService.class);
        MqttAdapterMessagingService service = new MqttAdapterMessagingService(
                mock(AdapterIndexService.class), mapper, new ProtocolDictionaryService(), mock(Executor.class));
        var payload = JsonNodeSupport.objectNode();
        payload.put("timestamp", 1719892800L);
        payload.put("adapterName", "adapter-B");
        payload.put("devicePoint", "point-1");
        payload.putObject("telemetryData").put("temperature", 25.0);
        MqttMessage message;
        try {
            message = new MqttMessage(JsonNodeSupport.MAPPER.writeValueAsBytes(payload));
        } catch (Exception error) {
            throw new AssertionError(error);
        }

        service.messageArrived("smartlab/adapter/adapter-A/point-1/telemetry", message);

        verify(mapper, never()).applyTelemetry(any(), any(), any());
    }

    private com.fasterxml.jackson.databind.node.ObjectNode message(String operation) {
        var message = JsonNodeSupport.objectNode();
        message.put("topic", "smartlab/test");
        message.putObject("payload").put("operation", operation);
        return message;
    }
}