package com.smartlab.management.dto;

import lombok.Data;

@Data
public class AdapterRouteDTO {

    private Long deviceInstanceId;
    private Long deviceModelId;
    private String instanceName;
    private String boundAdapterName;
    private String boundDevicePoint;
}
