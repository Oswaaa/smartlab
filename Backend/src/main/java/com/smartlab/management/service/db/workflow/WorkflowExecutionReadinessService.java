package com.smartlab.management.service.db.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.service.db.resource.adapter.AdapterIndexService;
import com.smartlab.management.service.db.resource.device.DeviceTwinStateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 执行任务前检查其绑定设备和 Adapter 是否真实具备下发条件。 */
@Service
public class WorkflowExecutionReadinessService {

    private final WorkflowTaskResourceService resourceService;
    private final DeviceTwinStateService twinStateService;
    private final AdapterIndexService adapterIndexService;
    private final long adapterHeartbeatTimeoutSeconds;

    public WorkflowExecutionReadinessService(
            WorkflowTaskResourceService resourceService,
            DeviceTwinStateService twinStateService,
            AdapterIndexService adapterIndexService,
            @Value("${smartlab.adapter.heartbeat-timeout-seconds:30}") long adapterHeartbeatTimeoutSeconds) {
        this.resourceService = resourceService;
        this.twinStateService = twinStateService;
        this.adapterIndexService = adapterIndexService;
        if (adapterHeartbeatTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("Adapter心跳超时秒数必须大于0");
        }
        this.adapterHeartbeatTimeoutSeconds = adapterHeartbeatTimeoutSeconds;
    }

    public void validate(JsonNode resourceMap) {
        inspect(resourceMap).stream().filter(WorkflowIssue::blocking).findFirst()
                .ifPresent(issue -> { throw new IllegalStateException(issue.message()); });
    }

    public List<WorkflowIssue> inspect(JsonNode resourceMap) {
        List<WorkflowIssue> issues = new ArrayList<>();
        for (WorkflowTaskResourceService.BoundDeviceBinding binding : resourceService.boundDeviceBindings(resourceMap)) {
            Long instanceId = binding.deviceInstanceId();
            DeviceInstances instance;
            try { instance = resourceService.requireUsableInstance(instanceId); }
            catch (RuntimeException error) {
                issues.add(issue("DEVICE_UNAVAILABLE", binding.slotId(), instanceId, error.getMessage(), "选择可用设备实例"));
                continue;
            }
            try { requireDeviceOnline(instance); }
            catch (RuntimeException error) { issues.add(issue("DEVICE_OFFLINE", binding.slotId(), instanceId, error.getMessage(), "等待设备上线后重试")); }
            try { requireAdapterOnline(instance); }
            catch (RuntimeException error) {
                String message = error.getMessage();
                String code = message != null && message.contains("心跳") ? "ADAPTER_HEARTBEAT_STALE"
                        : message != null && message.contains("尚未绑定") ? "DEVICE_ADAPTER_MISSING"
                        : message != null && message.contains("未注册") ? "ADAPTER_UNREGISTERED" : "ADAPTER_OFFLINE";
                issues.add(issue(code, binding.slotId(), instanceId, message, "检查 Adapter 连接和心跳"));
            }
        }
        return List.copyOf(issues);
    }

    private WorkflowIssue issue(String code, String slotId, Long instanceId, String message, String suggestion) {
        String id = instanceId == null ? "" : String.valueOf(instanceId);
        return new WorkflowIssue(code, "READINESS", "deviceBindings[" + slotId + "]", "deviceBinding", slotId,
                true, message == null ? "设备执行就绪检查失败" : message, suggestion);
    }

    private void requireDeviceOnline(DeviceInstances instance) {
        DeviceTwinStates state = twinStateService.getByInstanceId(instance.getId());
        if (state == null || !"ONLINE".equalsIgnoreCase(state.getOnlineStatus())) {
            throw new IllegalStateException("设备实例“" + displayName(instance) + "”当前不在线，不能启动任务");
        }
    }

    private void requireAdapterOnline(DeviceInstances instance) {
        String adapterName = instance.getBoundAdapterName();
        if (adapterName == null || adapterName.isBlank()) {
            throw new IllegalStateException("设备实例“" + displayName(instance) + "”尚未绑定Adapter，不能启动任务");
        }
        AdapterIndex adapter = adapterIndexService.getByName(adapterName);
        if (adapter == null) {
            throw new IllegalStateException("设备实例“" + displayName(instance) + "”绑定的Adapter未注册: " + adapterName);
        }
        String status = adapter.getStatus() == null ? "" : adapter.getStatus().trim().toUpperCase(Locale.ROOT);
        if ("DISABLED".equals(status)) {
            throw new IllegalStateException("Adapter“" + adapterName + "”已被停用，不能启动任务");
        }
        Instant heartbeatDeadline = Instant.now().minusSeconds(adapterHeartbeatTimeoutSeconds);
        if (adapter.getLastHeartbeat() == null || adapter.getLastHeartbeat().toInstant().isBefore(heartbeatDeadline)) {
            throw new IllegalStateException("Adapter“" + adapterName + "”心跳已超时，不能启动任务");
        }
    }

    private String displayName(DeviceInstances instance) {
        String name = instance.getInstanceName();
        return name == null || name.isBlank() ? String.valueOf(instance.getId()) : name + "（" + instance.getId() + "）";
    }
}
