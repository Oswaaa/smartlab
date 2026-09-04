package com.smartlab.management.controller.resource.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import com.smartlab.adapter.MqttAdapterMessagingService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/adapter")
/**
 * MQTT物理网关桥接运行时控制器。提供实时MQTT长连接状态监测、快照获取以及手动触发重连注册的控制端点。
 */
public class MqttBridgeController {

    private final MqttAdapterMessagingService mqttAdapterMessagingService;

    public MqttBridgeController(MqttAdapterMessagingService mqttAdapterMessagingService) {
        this.mqttAdapterMessagingService = mqttAdapterMessagingService;
    }

    @GetMapping({"/mqtt/mqtt/status", "/protocol/mqtt/status"})
    public ApiResponse<Map<String, Object>> mqttStatus() {
        return ApiResponse.ok(mqttAdapterMessagingService.statusSnapshot());
    }


    @GetMapping({"/mqtt/pending-registrations", "/protocol/pending-registrations"})
    public ApiResponse<Object> pendingRegistrations() {
        return ApiResponse.ok(mqttAdapterMessagingService.pendingAdapterRegistrations());
    }

    @GetMapping(value = {"/mqtt/pending-registrations/stream", "/protocol/pending-registrations/stream"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter pendingRegistrationStream() {
        return mqttAdapterMessagingService.subscribePendingRegistrationEvents();
    }

    @PostMapping({"/mqtt/pending-registrations/{adapterName}/complete", "/protocol/pending-registrations/{adapterName}/complete"})
    public ApiResponse<AdapterIndex> completePendingRegistration(@PathVariable String adapterName,
                                                                 @RequestBody(required = false) JsonNode body) {
        try {
            JsonNode reviewedConfig = body == null || body.isNull() ? null : body.path("parsedConfig");
            if (reviewedConfig != null && reviewedConfig.isMissingNode()) {
                reviewedConfig = body;
            }
            return ApiResponse.ok(mqttAdapterMessagingService.completePendingAdapterRegistration(adapterName, reviewedConfig));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping({"/mqtt/pending-registrations/{adapterName}", "/protocol/pending-registrations/{adapterName}"})
    public ApiResponse<String> discardPendingRegistration(@PathVariable String adapterName) {
        mqttAdapterMessagingService.discardPendingAdapterRegistration(adapterName);
        return ApiResponse.ok("已忽略");
    }
    @PostMapping({"/mqtt/mqtt/reconnect", "/protocol/mqtt/reconnect"})
    public ApiResponse<Map<String, Object>> reconnectMqtt() {
        try {
            return ApiResponse.ok(mqttAdapterMessagingService.reconnectRegistrationListener());
        } catch (Exception e) {
            return ApiResponse.fail("MQTT 重连失败: " + e.getMessage());
        }
    }
}
