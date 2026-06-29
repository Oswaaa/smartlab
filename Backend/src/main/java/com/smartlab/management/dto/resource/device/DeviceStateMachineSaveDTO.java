package com.smartlab.management.dto.resource.device;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
/**
 * DeviceStateMachineSave业务数据传输载体对象（DTO）。
 */
public class DeviceStateMachineSaveDTO {

    private Long modelId;
    private JsonNode interfacesDef;
    private JsonNode commandLifecycleDef;
    private JsonNode operationStateDef;
    private JsonNode transitions;
}
