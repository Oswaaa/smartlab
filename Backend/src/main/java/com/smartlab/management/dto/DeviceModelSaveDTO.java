package com.smartlab.management.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
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
