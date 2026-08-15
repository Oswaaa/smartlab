package com.smartlab.management.controller.resource.device;

import com.smartlab.engine.observation.ObservableKey;
import com.smartlab.engine.observation.ObservableSnapshotService;
import com.smartlab.engine.observation.ObservationSample;
import com.smartlab.global.contract.ObservableObjectType;
import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.service.db.resource.device.DeviceTwinStateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/device/twin-state")
/**
 * 设备孪生影子（Device Twin）运行态快照控制器。提供高频遥测产生的逻辑属性状态及状态机所处阶段的检索端点。
 */
public class DeviceTwinStateController {

    private final DeviceTwinStateService service;
    private final ObservableSnapshotService snapshotService;

    @Autowired
    public DeviceTwinStateController(DeviceTwinStateService service,
                                     @Autowired(required = false) ObservableSnapshotService snapshotService) {
        this.service = service;
        this.snapshotService = snapshotService;
    }

    /**
     * 读取指定设备实例与物理属性在内存时序缓冲区中的真实历史点序列（用于安全监控动态波形图）。
     */
    @GetMapping("/history")
    public ApiResponse<List<Map<String, Object>>> getHistory(
            @RequestParam Long instanceId,
            @RequestParam String targetName,
            @RequestParam(defaultValue = "60") int seconds) {
        if (snapshotService == null) {
            return ApiResponse.ok(List.of());
        }
        ObservableKey key = new ObservableKey(
                ObservableObjectType.DEVICE_ATTRIBUTE,
                instanceId,
                null,
                null,
                null,
                null,
                targetName,
                null
        );
        Instant since = Instant.now().minusSeconds(Math.max(1, Math.min(seconds, 1800)));
        List<ObservationSample> samples = snapshotService.readHistory(key, since);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ObservationSample sample : samples) {
            Map<String, Object> item = new HashMap<>();
            item.put("observedAt", sample.observedAt().toString());
            item.put("value", sample.value());
            item.put("revision", sample.revision());
            result.add(item);
        }
        return ApiResponse.ok(result);
    }



    @GetMapping("/{id}")
    public ApiResponse<DeviceTwinStates> get(@PathVariable Long id) {
        DeviceTwinStates entity = service.getById(id);
        return entity == null ? ApiResponse.fail("设备实时状态不存在") : ApiResponse.ok(entity);
    }

    @GetMapping("/by-instance/{instanceId}")
    public ApiResponse<DeviceTwinStates> getByInstance(@PathVariable Long instanceId) {
        DeviceTwinStates entity = service.getByInstanceId(instanceId);
        return entity == null ? ApiResponse.fail("设备实时状态不存在") : ApiResponse.ok(entity);
    }

    @PostMapping("/save")
    public ApiResponse<DeviceTwinStates> save(@RequestBody DeviceTwinStates entity) {
        try {
            return ApiResponse.ok(service.save(entity));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}

