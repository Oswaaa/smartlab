package com.smartlab.management.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class DeviceInstanceDTO {

    private Long id;
    private Long deviceModelId;
    private String instanceName;
    private Map<String, Object> instanceConfig;
    private String picture;
    private List<Map<String, Object>> componentBindings;
}

