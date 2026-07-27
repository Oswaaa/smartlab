package com.smartlab.engine.workflow.action;

public enum WorkflowActionStatus {
    CONTINUE,
    SUSPEND_UNTIL,
    WAIT_DEVICE_IDLE,
    AWAIT_EXTERNAL_SIGNAL
}
