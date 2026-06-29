package com.smartlab.management.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class DeviceStateMachineSaveDTO {

    private Long modelId;
    private JsonNode interfacesDef;
    private JsonNode commandLifecycleDef;
    private JsonNode operationStateDef;
    private JsonNode transitions;
}
