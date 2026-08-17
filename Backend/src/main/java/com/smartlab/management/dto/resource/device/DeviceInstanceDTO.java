package com.smartlab.management.dto.resource.device;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
/**
 * DeviceInstance业务数据传输载体对象（DTO）。聚合物理资产信息与实时通信状态。
 */
public class DeviceInstanceDTO {

    private Long id;
    private Long deviceModelId;
    private String instanceName;
    private JsonNode instanceConfig;
    private String boundAdapterName;
    private String boundDevicePoint;
    private String lifecycleStatus;
    private String onlineStatus;
    private Boolean isOnline;
    private String currentCmdState;
    private String picture;
    private OffsetDateTime createTime;
    private List<Map<String, Object>> componentBindings;

    public String getInstanceId() {
        return id == null ? null : String.valueOf(id);
    }

    public String getModelId() {
        return deviceModelId == null ? null : String.valueOf(deviceModelId);
    }
}

