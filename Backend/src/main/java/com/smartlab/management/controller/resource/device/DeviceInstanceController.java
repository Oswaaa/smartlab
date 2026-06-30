package com.smartlab.management.controller.resource.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.management.dto.resource.adapter.AdapterRouteDTO;
import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.adapter.MqttAdapterMessagingService;
import com.smartlab.management.service.db.resource.device.DeviceInstanceService;
import com.smartlab.adapter.AdapterPayloadMapperService;
import com.smartlab.engine.statemachine.StateMachineEngine;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/device/instance")
/**
 * 设备物理实例生命周期与点位绑定控制器。控制物理硬件的网关配对、MQTT点位映射及指令/状态广播下发。
 */
public class DeviceInstanceController {

    private final DeviceInstanceService deviceInstanceService;
    private final MqttAdapterMessagingService mqttAdapterMessagingService;
    private final StateMachineEngine stateMachineEngine;
    private final AdapterPayloadMapperService protocolMapperService;

    public DeviceInstanceController(DeviceInstanceService deviceInstanceService,
                                    MqttAdapterMessagingService mqttAdapterMessagingService,
                                    StateMachineEngine stateMachineEngine,
                                    AdapterPayloadMapperService protocolMapperService) {
        this.deviceInstanceService = deviceInstanceService;
        this.mqttAdapterMessagingService = mqttAdapterMessagingService;
        this.stateMachineEngine = stateMachineEngine;
        this.protocolMapperService = protocolMapperService;
    }

    @GetMapping("/list")
    public ApiResponse<List<DeviceInstances>> list() {
        return ApiResponse.ok(deviceInstanceService.list());
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<DeviceInstances>> page(@RequestParam(defaultValue = "1") long pageNo,
                                                        @RequestParam(defaultValue = "24") long pageSize,
                                                        @RequestParam(required = false) String modelId,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) Boolean online) {
        return ApiResponse.ok(deviceInstanceService.page(pageNo, pageSize, modelId, keyword, online));
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Long>> summary(@RequestParam(required = false) String modelId) {
        return ApiResponse.ok(deviceInstanceService.summary(modelId));
    }

    @PostMapping("/save")
    public ApiResponse<Map<String, String>> save(@RequestBody Map<String, Object> payload) {
        try {
            DeviceInstances instance = deviceInstanceService.savePayload(payload);
            mqttAdapterMessagingService.trySubscribeDevicePointTopics(instance.getBoundAdapterName(), instance.getBoundDevicePoint());
            return ApiResponse.ok(Map.of("instanceId", String.valueOf(instance.getId())));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable String id) {
        try {
            deviceInstanceService.delete(id);
            return ApiResponse.ok("注销成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/snapshots")
    public ApiResponse<List<DeviceTwinStates>> listSnapshots() {
        return ApiResponse.ok(deviceInstanceService.listSnapshots());
    }

    @GetMapping("/snapshot/{id}")
    public ApiResponse<DeviceTwinStates> getSnapshot(@PathVariable String id) {
        DeviceTwinStates snapshot = deviceInstanceService.getSnapshot(id);
        return snapshot == null ? ApiResponse.fail("设备状态机监控记录不存在") : ApiResponse.ok(snapshot);
    }

    @GetMapping("/adapter-routes")
    public ApiResponse<Map<String, AdapterRouteDTO>> adapterRoutes(@RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResponse.ok(refresh ? protocolMapperService.refreshAdapterRouteTable() : protocolMapperService.getAdapterRouteTable());
    }

    @GetMapping("/binding/preview")
    public ApiResponse<JsonNode> previewBinding(@RequestParam Long modelId,
                                                @RequestParam String adapterName,
                                                @RequestParam String devicePoint) {
        try {
            return ApiResponse.ok(protocolMapperService.buildAdapterBinding(modelId, adapterName, devicePoint));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/control/{id}")
    public ApiResponse<ObjectNode> control(@PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            String commandId = body == null ? "" : String.valueOf(body.getOrDefault("commandId", ""));
            String signalName = body == null ? "MANUAL_EXECUTE" : String.valueOf(body.getOrDefault("signalName", "MANUAL_EXECUTE"));
            Map<String, Object> parameters = new HashMap<>();
            if (body != null && body.get("parameters") instanceof Map<?, ?> raw) {
                raw.forEach((key, value) -> parameters.put(String.valueOf(key), value));
            }
            ObjectNode result = stateMachineEngine.handleManualControl(Long.valueOf(id), signalName, commandId, parameters);
            return ApiResponse.ok(result);
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}
