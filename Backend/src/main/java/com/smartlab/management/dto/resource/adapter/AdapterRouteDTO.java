package com.smartlab.management.dto.resource.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
/**
 * AdapterRoute业务数据传输载体对象（DTO）。
 */
public class AdapterRouteDTO {

    private Long deviceInstanceId;
    private Long deviceModelId;
    private String instanceName;
    private String boundAdapterName;
    private String boundDevicePoint;
    private String templateName;
    private String categoryName;
    private JsonNode resolvedAttributes;
    private java.util.Map<String, String> rawToModelMap;
}
