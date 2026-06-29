package com.smartlab.management.dto.resource.device;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
/**
 * DeviceModelSave业务数据传输载体对象（DTO）。
 */
public class DeviceModelSaveDTO {

    private Long modelId;
    private String modelName;
    private Long categoryId;
    private String categoryName;
    private JsonNode attributes;
    private JsonNode capabilities;
    private JsonNode adapterContract;
    private JsonNode ports;
    private JsonNode intrinsicConstraints;
    private JsonNode stateMachineInterfaces;
    private JsonNode opState;
    private JsonNode cmdState;
    private JsonNode stateTransitions;
    private JsonNode componentsBom;
}
