package com.smartlab.management.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class DeviceModelDTO {

    private Long id;
    private String modelName;
    private Long categoryId;
    private List<Map<String, Object>> attributes;
    private List<Map<String, Object>> capabilities;
    private Map<String, Object> adapterContract;
    private List<Map<String, Object>> ports;
    private List<Map<String, Object>> intrinsicConstraints;
    private List<Map<String, Object>> stateMachineInterfaces;
    private Map<String, Object> opState;
    private Map<String, Object> cmdState;
    private List<Map<String, Object>> stateTransitions;
    private List<Map<String, Object>> componentsBom;
}

