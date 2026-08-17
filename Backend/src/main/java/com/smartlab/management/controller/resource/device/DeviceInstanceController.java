package com.smartlab.management.controller.resource.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.management.dto.resource.adapter.AdapterRouteDTO;
import com.smartlab.management.dto.resource.device.DeviceInstanceDTO;
import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.adapter.MqttAdapterMessagingService;
import com.smartlab.management.service.db.resource.device.DeviceInstanceService;
import com.smartlab.management.service.db.user.CurrentUserPermissionService;
import com.smartlab.management.service.protocol.AdapterPayloadMapperService;
import com.smartlab.global.contract.SystemExecutionContract;
import com.smartlab.engine.statemachine.StateMachineEngine;
import com.smartlab.management.sse.DeviceConsoleSseHub;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
    private final CurrentUserPermissionService permissionService;
    private final DeviceConsoleSseHub deviceConsoleSseHub;

    public DeviceInstanceController(DeviceInstanceService deviceInstanceService,
                                    MqttAdapterMessagingService mqttAdapterMessagingService,
                                    StateMachineEngine stateMachineEngine,
                                    AdapterPayloadMapperService protocolMapperService,
                                    CurrentUserPermissionService permissionService) {
        this(deviceInstanceService, mqttAdapterMessagingService, stateMachineEngine, protocolMapperService, permissionService, null);
    }

    @Autowired
    public DeviceInstanceController(DeviceInstanceService deviceInstanceService,
                                    MqttAdapterMessagingService mqttAdapterMessagingService,
                                    StateMachineEngine stateMachineEngine,
                                    AdapterPayloadMapperService protocolMapperService,
                                    CurrentUserPermissionService permissionService,
                                    DeviceConsoleSseHub deviceConsoleSseHub) {
        this.deviceInstanceService = deviceInstanceService;
        this.mqttAdapterMessagingService = mqttAdapterMessagingService;
        this.stateMachineEngine = stateMachineEngine;
        this.protocolMapperService = protocolMapperService;
        this.permissionService = permissionService;
        this.deviceConsoleSseHub = deviceConsoleSseHub;
    }

    @GetMapping("/list")
    public ApiResponse<List<DeviceInstanceDTO>> list(@RequestParam(required = false) String lifecycleStatus) {
        return ApiResponse.ok(deviceInstanceService.listDTO(lifecycleStatus));
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<DeviceInstanceDTO>> page(@RequestParam(defaultValue = "1") long pageNo,
                                                          @RequestParam(defaultValue = "24") long pageSize,
                                                          @RequestParam(required = false) String modelId,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) Boolean online,
                                                          @RequestParam(required = false) String lifecycleStatus) {
        return ApiResponse.ok(deviceInstanceService.pageDTO(pageNo, pageSize, modelId, keyword, online, lifecycleStatus));
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

    @PostMapping("/retire/{id}")
    public ApiResponse<String> retire(@PathVariable String id) {
        try {
            deviceInstanceService.retire(id);
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

    @GetMapping(value = "/console/stream/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamConsole(@PathVariable Long id) {
        if (deviceConsoleSseHub == null) {
            throw new IllegalStateException("SSE 模块未就绪");
        }
        return deviceConsoleSseHub.register(id);
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
            permissionService.require("device_instance", "control");
            deviceInstanceService.requireUsable(Long.valueOf(id));
            String signalName = body == null ? "MANUAL_EXECUTE_START" : String.valueOf(body.getOrDefault("signalName", "MANUAL_EXECUTE_START"));
            if (SystemExecutionContract.isCommandStartSignal(signalName)) {
                deviceInstanceService.requireOnline(Long.valueOf(id));
            }
            Object capabilityValue = body == null ? null
                    : body.getOrDefault("capabilityName", body.get("commandId"));
            String capabilityName = capabilityValue == null ? "" : String.valueOf(capabilityValue).trim();
            if (SystemExecutionContract.isCommandStartSignal(signalName) && capabilityName.isBlank()) {
                throw new IllegalArgumentException("设备能力标识 capabilityName 不能为空");
            }
            Map<String, Object> parameters = new HashMap<>();
            if (body != null && body.get("parameters") instanceof Map<?, ?> raw) {
                raw.forEach((key, value) -> parameters.put(String.valueOf(key), value));
            }
            ObjectNode result = stateMachineEngine.handleManualControl(Long.valueOf(id), signalName, capabilityName, parameters);
            return ApiResponse.ok(result);
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/{id}/exception/clear")
    public ApiResponse<List<ObjectNode>> clearException(@PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            permissionService.require("device_instance", "control");
            deviceInstanceService.requireUsable(Long.valueOf(id));
            String violationStateName = body == null ? "" : String.valueOf(body.getOrDefault("violationStateName", "")).trim();
            if (violationStateName.isBlank()) {
                throw new IllegalArgumentException("异常状态名称 violationStateName 不能为空");
            }
            List<ObjectNode> result = stateMachineEngine.clearIntrinsicException(Long.valueOf(id), violationStateName);
            return ApiResponse.ok(result);
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}
