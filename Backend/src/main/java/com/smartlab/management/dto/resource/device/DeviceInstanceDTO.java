package com.smartlab.management.dto.resource.device;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
/**
 * DeviceInstance业务数据传输载体对象（DTO）。
 */
public class DeviceInstanceDTO {

    private Long id;
    private Long deviceModelId;
    private String instanceName;
    private Map<String, Object> instanceConfig;
    private String picture;
    private List<Map<String, Object>> componentBindings;
}

