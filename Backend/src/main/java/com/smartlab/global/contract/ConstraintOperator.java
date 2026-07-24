package com.smartlab.global.contract;

public enum ConstraintOperator {
    GREATER_THAN(">"), LESS_THAN("<"), GREATER_THAN_OR_EQUAL(">="), LESS_THAN_OR_EQUAL("<="),
    EQUAL("="), NOT_EQUAL("!="), BETWEEN("BETWEEN"), IN("IN");

    private final String value;

    ConstraintOperator(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static boolean supports(String value) {
        for (ConstraintOperator operator : values()) {
            if (operator.value.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
