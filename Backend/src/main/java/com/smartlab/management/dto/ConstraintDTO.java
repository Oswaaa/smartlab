package com.smartlab.management.dto;

import lombok.Data;

@Data
public class ConstraintDTO {

    private String constraintRuleId;
    private String observedObjectRef;
    private String operator;
    private Object threshold; // numeric or string
    private String violationHandlingRef;
}

