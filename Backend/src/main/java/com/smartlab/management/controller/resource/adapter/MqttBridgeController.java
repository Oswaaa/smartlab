package com.smartlab.management.controller.resource.adapter;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.adapter.MqttAdapterMessagingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/adapter/mqtt")
/**
 * MQTT物理网关桥接运行时控制器。提供实时MQTT长连接状态监测、快照获取以及手动触发重连注册的控制端点。
 */
public class MqttBridgeController {

    private final MqttAdapterMessagingService mqttAdapterMessagingService;

    public MqttBridgeController(MqttAdapterMessagingService mqttAdapterMessagingService) {
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
