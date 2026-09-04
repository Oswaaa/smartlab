package com.smartlab.adapter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.StateMachineSendActionEvent;
import com.smartlab.global.event.AdapterDeletedEvent;
import com.smartlab.global.event.AdapterLeaseResultEvent;
import com.smartlab.global.event.DeviceInstanceDeletedEvent;
import com.smartlab.global.event.DeviceInstanceRetiredEvent;
import com.smartlab.global.protocol.ProtocolDictionaryService;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.resource.adapter.AdapterRouteDTO;
import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import com.smartlab.management.entity.resource.device.DeviceInstanceKind;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.service.db.resource.adapter.AdapterIndexService;
import com.smartlab.management.service.protocol.AdapterPayloadMapperService;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    void temporaryInstanceGoesToSimulatorNotMqtt() {
        AdapterPayloadMapperService mapper = mock(AdapterPayloadMapperService.class);
        MqttAdapterMessagingService service = spy(new MqttAdapterMessagingService(
                mock(AdapterIndexService.class), mapper, new ProtocolDictionaryService(), mock(Executor.class)));
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceInstances temporary = new DeviceInstances();
        temporary.setId(7L);
        temporary.setInstanceKind(DeviceInstanceKind.TEMPORARY);
        when(instances.selectById(7L)).thenReturn(temporary);
        service.setDeviceInstancesMapper(instances);
        InProcessAdapterSimulator simulator = mock(InProcessAdapterSimulator.class);
        service.setAdapterSimulator(simulator);

        service.handleStateMachineSendAction(new StateMachineSendActionEvent(
                7L, "Interface_adapter_out", "ADAPTER", "CMD_START",
                "heat", "heat_cmd", "msg-1", Map.of("target", 80), JsonNodeSupport.objectNode()));

        verify(simulator).handleSendAction(any());
        verify(mapper, never()).buildCommandMessage(any(), any(), any(), any());
        verify(service, never()).publishCommand(any());
    }

    @Test
    void virtualInstanceStillPublishesMqtt() {
        AdapterPayloadMapperService mapper = mock(AdapterPayloadMapperService.class);
        MqttAdapterMessagingService service = spy(new MqttAdapterMessagingService(
                mock(AdapterIndexService.class), mapper, new ProtocolDictionaryService(), mock(Executor.class)));
        doNothing().when(service).publishCommand(any());
        when(mapper.buildCommandMessage("7", "heat", "msg-1", Map.of("target", 80)))
                .thenReturn(message("start"));
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceInstances virtual = new DeviceInstances();
        virtual.setId(7L);
        virtual.setInstanceKind(DeviceInstanceKind.VIRTUAL);
        when(instances.selectById(7L)).thenReturn(virtual);
        service.setDeviceInstancesMapper(instances);

        service.handleStateMachineSendAction(new StateMachineSendActionEvent(
                7L, "Interface_adapter_out", "ADAPTER", "CMD_START",
                "heat", "heat_cmd", "msg-1", Map.of("target", 80), JsonNodeSupport.objectNode()));

        verify(mapper).buildCommandMessage("7", "heat", "msg-1", Map.of("target", 80));
        verify(service).publishCommand(any());
    }

    @Test
    void missingInstanceFailsOutboundInsteadOfPublishing() {
        AdapterPayloadMapperService mapper = mock(AdapterPayloadMapperService.class);
        MqttAdapterMessagingService service = spy(new MqttAdapterMessagingService(
                mock(AdapterIndexService.class), mapper, new ProtocolDictionaryService(), mock(Executor.class)));
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        when(instances.selectById(7L)).thenReturn(null);
        service.setDeviceInstancesMapper(instances);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                service.handleStateMachineSendAction(new StateMachineSendActionEvent(
                        7L, "Interface_adapter_out", "ADAPTER", "CMD_START",
                        "heat", "heat_cmd", "msg-1", Map.of("target", 80), JsonNodeSupport.objectNode())));
        verify(service, never()).publishCommand(any());
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

    @Test
    void registerRequestGoesToPendingQueueInsteadOfDatabase() {
        AdapterIndexService adapters = mock(AdapterIndexService.class);
        when(adapters.previewRegisterPayload(any())).thenReturn(registerManifest());
        when(adapters.hasCompletedRegistration(anyString())).thenReturn(false);
        MqttAdapterMessagingService service = new MqttAdapterMessagingService(
                adapters, mock(AdapterPayloadMapperService.class), new ProtocolDictionaryService(), mock(Executor.class));

        service.messageArrived("smartlab/adapter/register", mqttMessage(registerPayload()));

        verify(adapters, never()).register(any());
        assertEquals(1, service.pendingAdapterRegistrations().size());
        assertEquals("PLCControllerAdapter", service.pendingAdapterRegistrations().get(0).get("adapterName"));
    }

    @Test
    void heartbeatStubDoesNotHidePendingRegistration() {
        AdapterIndexService adapters = mock(AdapterIndexService.class);
        when(adapters.previewRegisterPayload(any())).thenReturn(registerManifest());
        when(adapters.hasCompletedRegistration("PLCControllerAdapter")).thenReturn(false);
        AdapterIndex stub = new AdapterIndex();
        stub.setAdapterName("PLCControllerAdapter");
        when(adapters.getByName("PLCControllerAdapter")).thenReturn(stub);
        MqttAdapterMessagingService service = new MqttAdapterMessagingService(
                adapters, mock(AdapterPayloadMapperService.class), new ProtocolDictionaryService(), mock(Executor.class));

        service.messageArrived("smartlab/adapter/register", mqttMessage(registerPayload()));

        verify(adapters, never()).register(any());
        assertEquals(1, service.pendingAdapterRegistrations().size());
    }

    @Test
    void completedRegistrationIgnoresDuplicateRegisterRequest() {
        AdapterIndexService adapters = mock(AdapterIndexService.class);
        when(adapters.previewRegisterPayload(any())).thenReturn(registerManifest());
        when(adapters.hasCompletedRegistration("PLCControllerAdapter")).thenReturn(true);
        MqttAdapterMessagingService service = new MqttAdapterMessagingService(
                adapters, mock(AdapterPayloadMapperService.class), new ProtocolDictionaryService(), mock(Executor.class));

        service.messageArrived("smartlab/adapter/register", mqttMessage(registerPayload()));

        verify(adapters, never()).register(any());
        assertEquals(0, service.pendingAdapterRegistrations().size());
    }

    @Test
    void desiredTopicsIncludeHeartbeatForRegisteredAdapterAndDevicePointStreams() {
        AdapterIndexService adapters = mock(AdapterIndexService.class);
        AdapterPayloadMapperService routes = mock(AdapterPayloadMapperService.class);
        AdapterIndex adapter = new AdapterIndex();
        adapter.setAdapterName("plc");
        adapter.setOriginalConfig("[adapter]\nadapterName = plc\n");
        when(adapters.list()).thenReturn(List.of(adapter));
        when(adapters.hasCompletedRegistration(adapter)).thenReturn(true);
        AdapterRouteDTO route = new AdapterRouteDTO();
        route.setBoundAdapterName("plc");
        route.setBoundDevicePoint("point-1");
        when(routes.refreshAdapterRouteTable()).thenReturn(Map.of("plc/point-1", route));
        MqttAdapterMessagingService service = new MqttAdapterMessagingService(
                adapters, routes, new ProtocolDictionaryService(), mock(Executor.class));

        Set<String> desired = service.desiredRuntimeTopics();

        assertTrue(desired.contains("smartlab/adapter/register"));
        assertTrue(desired.contains("smartlab/adapter/plc/heartbeat"));
        assertTrue(desired.contains("smartlab/adapter/plc/point-1/telemetry"));
        assertTrue(desired.contains("smartlab/adapter/plc/point-1/event"));
        assertTrue(desired.contains("smartlab/adapter/plc/leaseresult"));
    }

    @Test
    void leaseResultIsPublishedAfterProtocolValidation() {
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        MqttAdapterMessagingService service = new MqttAdapterMessagingService(
                mock(AdapterIndexService.class), mock(AdapterPayloadMapperService.class),
                new ProtocolDictionaryService(), mock(Executor.class));
        service.setApplicationEventPublisher(events);
        ObjectNode payload = JsonNodeSupport.objectNode();
        payload.put("leaseId", 11);
        payload.put("adapterName", "plc");
        payload.put("action", "LEASE");
        payload.put("devicePoint", "Reactor1_sim_11");
        payload.put("status", "GRANTED");
        payload.put("timestamp", 1719892800L);

        service.messageArrived("smartlab/adapter/plc/leaseresult", mqttMessage(payload));

        verify(events).publishEvent(any(AdapterLeaseResultEvent.class));
    }

    @Test
    void deletingAdapterOrDeviceDropsTheirRuntimeSubscriptionsFromTheDiff() {
        AdapterIndexService adapters = mock(AdapterIndexService.class);
        AdapterPayloadMapperService routes = mock(AdapterPayloadMapperService.class);
        when(adapters.list()).thenReturn(List.of());
        when(routes.refreshAdapterRouteTable()).thenReturn(Map.of());
        MqttAdapterMessagingService service = new MqttAdapterMessagingService(
                adapters, routes, new ProtocolDictionaryService(), mock(Executor.class));
        service.replaceTrackedSubscriptions(Set.of(
                "smartlab/adapter/register",
                "smartlab/adapter/plc/heartbeat",
                "smartlab/adapter/plc/point-1/telemetry",
                "smartlab/adapter/plc/point-1/event"));

        MqttAdapterMessagingService.TopicSubscriptionDiff diff = service.diffAgainst(service.desiredRuntimeTopics());

        assertEquals(Set.of(), diff.toSubscribe());
        assertTrue(diff.toUnsubscribe().contains("smartlab/adapter/plc/heartbeat"));
        assertTrue(diff.toUnsubscribe().contains("smartlab/adapter/plc/point-1/telemetry"));
        assertTrue(diff.toUnsubscribe().contains("smartlab/adapter/plc/point-1/event"));
        assertFalse(diff.toUnsubscribe().contains("smartlab/adapter/register"));
    }

    @Test
    void adapterAndDeviceLifecycleEventsResyncSubscriptions() {
        MqttAdapterMessagingService service = spy(new MqttAdapterMessagingService(
                mock(AdapterIndexService.class), mock(AdapterPayloadMapperService.class),
                new ProtocolDictionaryService(), mock(Executor.class)));
        doNothing().when(service).syncRuntimeSubscriptions();

        service.handleAdapterDeleted(new AdapterDeletedEvent("plc"));
        service.handleDeviceInstanceRetired(new DeviceInstanceRetiredEvent(7L));
        service.handleDeviceInstanceDeleted(new DeviceInstanceDeletedEvent(7L));

        verify(service, org.mockito.Mockito.times(3)).syncRuntimeSubscriptions();
    }

    private ObjectNode registerManifest() {
        ObjectNode manifest = JsonNodeSupport.objectNode();
        manifest.put("adapterName", "PLCControllerAdapter");
        manifest.putArray("deviceCategories").addObject()
                .put("categoryName", "PLC")
                .putArray("devicePoints");
        return manifest;
    }

    private ObjectNode registerPayload() {
        ObjectNode payload = JsonNodeSupport.objectNode();
        payload.put("adapterName", "PLCControllerAdapter");
        payload.put("rawConfigFormat", "INI");
        payload.put("rawConfigContent", "[adapter]\nadapterName = PLCControllerAdapter\n");
        payload.put("timestamp", 1719892800L);
        return payload;
    }

    private MqttMessage mqttMessage(ObjectNode payload) {
        try {
            return new MqttMessage(JsonNodeSupport.MAPPER.writeValueAsBytes(payload));
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private com.fasterxml.jackson.databind.node.ObjectNode message(String operation) {
        var message = JsonNodeSupport.objectNode();
        message.put("topic", "smartlab/test");
        message.putObject("payload").put("operation", operation);
        return message;
    }
}