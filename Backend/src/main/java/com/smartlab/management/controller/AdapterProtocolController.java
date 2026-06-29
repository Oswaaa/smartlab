package com.smartlab.management.controller;

import com.smartlab.management.dto.ApiResponse;
import com.smartlab.management.service.adapter.MqttAdapterMessagingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/adapter/protocol")
public class AdapterProtocolController {

    private final MqttAdapterMessagingService mqttAdapterMessagingService;

    public AdapterProtocolController(MqttAdapterMessagingService mqttAdapterMessagingService) {
        this.mqttAdapterMessagingService = mqttAdapterMessagingService;
    }

    @GetMapping("/mqtt/status")
    public ApiResponse<Map<String, Object>> mqttStatus() {
        return ApiResponse.ok(mqttAdapterMessagingService.statusSnapshot());
    }

    @PostMapping("/mqtt/reconnect")
    public ApiResponse<Map<String, Object>> reconnectMqtt() {
        return ApiResponse.ok(mqttAdapterMessagingService.reconnectRegistrationListener());
    }
}
