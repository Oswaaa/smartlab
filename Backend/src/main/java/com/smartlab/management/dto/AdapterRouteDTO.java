package com.smartlab.management.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class AdapterRouteDTO {

    private Long deviceInstanceId;
    private Long deviceModelId;
    private String instanceName;
    private String boundAdapterName;
    private String boundDevicePoint;
    private String templateName;
    private JsonNode resolvedAttributes;
    private java.util.Map<String, String> rawToModelMap;
}
