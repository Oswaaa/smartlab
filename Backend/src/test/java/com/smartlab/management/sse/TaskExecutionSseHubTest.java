package com.smartlab.management.sse;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.event.TaskExecutionLogEvent;
import com.smartlab.global.event.TaskLifecycleObservationEvent;
import com.smartlab.global.event.WorkflowNodeObservationEvent;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.workflow.ExecutionLog;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskExecutionSseHubTest {

    @Test
    void registerAndBroadcastTaskEvents() {
        TaskExecutionSseHub hub = new TaskExecutionSseHub();
        assertThrows(IllegalArgumentException.class, () -> hub.register(null));

        SseEmitter emitter = hub.register(501L);
        assertNotNull(emitter);

        // Broadcast Execution Log
        ExecutionLog log = new ExecutionLog();
        log.setId(1L);
        log.setTaskId(501L);
        log.setTaskStepId(10L);
        log.setSourceType("TASK");
        log.setLogLevel("INFO");
        log.setLogInfo("Step 1 executed successfully");
        log.setLogTime(OffsetDateTime.now());
        hub.handleExecutionLog(new TaskExecutionLogEvent(log));

        // Broadcast Step Status
        ObjectNode vars = JsonNodeSupport.objectNode();
        vars.put("temp", 37.5);
        WorkflowNodeObservationEvent stepEvent = new WorkflowNodeObservationEvent(
                501L, 100L, 1L, 10L, "SUCCEEDED", vars, Instant.now()
        );
        hub.handleWorkflowNode(stepEvent);

        // Broadcast Task Status
        TaskLifecycleObservationEvent taskEvent = new TaskLifecycleObservationEvent(
                501L, "SUCCEEDED", Instant.now()
        );
        hub.handleTaskLifecycle(taskEvent);

        // Untracked task events should not throw
        hub.handleTaskLifecycle(new TaskLifecycleObservationEvent(999L, "RUNNING", Instant.now()));
    }
}
