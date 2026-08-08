package com.smartlab.management.controller.resource.device;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.adapter.MqttAdapterMessagingService;
import com.smartlab.engine.statemachine.StateMachineEngine;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.service.db.resource.device.DeviceInstanceService;
import com.smartlab.management.service.db.user.CurrentUserPermissionService;
import com.smartlab.management.service.protocol.AdapterPayloadMapperService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
        verify(stateMachineEngine).handleManualControl(
                7L, "MANUAL_EXECUTE_START", "startHeating", Map.of("targetTemperature", 80));
    }
}
