package com.smartlab.management.dto.constraint;

import lombok.Data;

@Data
/**
 * Constraint业务数据传输载体对象（DTO）。
 */
public class ConstraintDTO {

    private String constraintRuleId;
    private String observedObjectRef;
    private String operator;
    private Object threshold; // numeric or string
    private String violationHandlingRef;
}

