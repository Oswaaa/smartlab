package com.smartlab.management.controller.resource.device;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.management.entity.resource.device.DeviceInstanceKind;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.adapter.MqttAdapterMessagingService;
import com.smartlab.engine.statemachine.StateMachineEngine;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.resource.device.VirtualMachineView;
import com.smartlab.management.service.db.resource.adapter.VirtualLeaseService;
import com.smartlab.management.service.db.resource.device.DeviceInstanceService;
import com.smartlab.management.service.db.user.CurrentUserPermissionService;
import com.smartlab.management.service.protocol.AdapterPayloadMapperService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceInstanceControllerTest {

    @Test
    void manualControlDispatchesModelCapabilityNameInsteadOfAdapterCommandName() {
        DeviceInstanceService instanceService = mock(DeviceInstanceService.class);
        MqttAdapterMessagingService mqttService = mock(MqttAdapterMessagingService.class);
        StateMachineEngine stateMachineEngine = mock(StateMachineEngine.class);
        AdapterPayloadMapperService payloadMapperService = mock(AdapterPayloadMapperService.class);
        CurrentUserPermissionService permissionService = mock(CurrentUserPermissionService.class);
        DeviceInstanceController controller = new DeviceInstanceController(
                instanceService, mqttService, stateMachineEngine, payloadMapperService, permissionService);
        DeviceInstances physical = new DeviceInstances();
        physical.setId(7L);
        physical.setInstanceKind(DeviceInstanceKind.PHYSICAL);
        when(instanceService.requireControllable(7L)).thenReturn(physical);
        ObjectNode result = JsonNodeSupport.objectNode().put("accepted", true);
        when(stateMachineEngine.handleManualControl(
                eq(7L), eq("MANUAL_EXECUTE_START"), eq("startHeating"), eq(Map.of("targetTemperature", 80))))
                .thenReturn(result);

        ApiResponse<ObjectNode> response = controller.control("7", Map.of(
                "capabilityName", "startHeating",
                "commandId", "PLC_HEAT_START",
                "signalName", "MANUAL_EXECUTE_START",
                "parameters", Map.of("targetTemperature", 80)));

        assertTrue(response.isSuccess());
        verify(permissionService).require("device_instance", "control");
        verify(instanceService).requireOnline(7L);
        verify(stateMachineEngine).handleManualControl(
                7L, "MANUAL_EXECUTE_START", "startHeating", Map.of("targetTemperature", 80));
    }

    @Test
    void virtualControlDispatchesToVirtualInstanceWithoutOnlineCheck() {
        DeviceInstanceService instanceService = mock(DeviceInstanceService.class);
        MqttAdapterMessagingService mqttService = mock(MqttAdapterMessagingService.class);
        StateMachineEngine stateMachineEngine = mock(StateMachineEngine.class);
        AdapterPayloadMapperService payloadMapperService = mock(AdapterPayloadMapperService.class);
        CurrentUserPermissionService permissionService = mock(CurrentUserPermissionService.class);
        VirtualLeaseService leases = mock(VirtualLeaseService.class);
        DeviceInstanceController controller = new DeviceInstanceController(
                instanceService, mqttService, stateMachineEngine, payloadMapperService, permissionService, null, leases);
        DeviceInstances virtual = new DeviceInstances();
        virtual.setId(24L);
        virtual.setInstanceKind(DeviceInstanceKind.VIRTUAL);
        when(instanceService.requireControllable(24L)).thenReturn(virtual);
        ObjectNode result = JsonNodeSupport.objectNode().put("accepted", true);
        when(stateMachineEngine.handleManualControl(
                eq(24L), eq("MANUAL_EXECUTE_START"), eq("startHeating"), eq(Map.of("targetTemperature", 80))))
                .thenReturn(result);

        ApiResponse<ObjectNode> response = controller.control("24", Map.of(
                "capabilityName", "startHeating",
                "signalName", "MANUAL_EXECUTE_START",
                "parameters", Map.of("targetTemperature", 80)));

        assertTrue(response.isSuccess());
        verify(stateMachineEngine).handleManualControl(
                24L, "MANUAL_EXECUTE_START", "startHeating", Map.of("targetTemperature", 80));
        verify(instanceService, never()).requireOnline(24L);
        verify(leases).touchLastUsedForInstance(24L);
    }

    @Test
    void applyVirtualRequiresPhysicalAndDelegates() {
        DeviceInstanceService instanceService = mock(DeviceInstanceService.class);
        MqttAdapterMessagingService mqttService = mock(MqttAdapterMessagingService.class);
        StateMachineEngine stateMachineEngine = mock(StateMachineEngine.class);
        AdapterPayloadMapperService payloadMapperService = mock(AdapterPayloadMapperService.class);
        CurrentUserPermissionService permissionService = mock(CurrentUserPermissionService.class);
        VirtualLeaseService leases = mock(VirtualLeaseService.class);
        DeviceInstanceController controller = new DeviceInstanceController(
                instanceService, mqttService, stateMachineEngine, payloadMapperService, permissionService, null, leases);
        DeviceInstances physical = new DeviceInstances();
        physical.setId(3L);
        physical.setInstanceKind(DeviceInstanceKind.PHYSICAL);
        when(instanceService.requirePhysical(3L)).thenReturn(physical);
        when(leases.applyPokeVirtual(3L)).thenReturn(new VirtualMachineView(
                3L, 24L, 11L, "Reactor1_sim_11", "VIRTUAL-reactor", "ACTIVE", null, null));

        ApiResponse<VirtualMachineView> response = controller.applyVirtual("3");

        assertTrue(response.isSuccess());
        verify(permissionService).require("device_instance", "control");
        verify(leases).applyPokeVirtual(3L);
    }

    @Test
    void releaseVirtualDelegatesToLeaseService() {
        DeviceInstanceService instanceService = mock(DeviceInstanceService.class);
        MqttAdapterMessagingService mqttService = mock(MqttAdapterMessagingService.class);
        StateMachineEngine stateMachineEngine = mock(StateMachineEngine.class);
        AdapterPayloadMapperService payloadMapperService = mock(AdapterPayloadMapperService.class);
        CurrentUserPermissionService permissionService = mock(CurrentUserPermissionService.class);
        VirtualLeaseService leases = mock(VirtualLeaseService.class);
        DeviceInstanceController controller = new DeviceInstanceController(
                instanceService, mqttService, stateMachineEngine, payloadMapperService, permissionService, null, leases);

        ApiResponse<String> response = controller.releaseVirtual(11L);

        assertTrue(response.isSuccess());
        verify(leases).releasePokeVirtual(11L);
    }
}
