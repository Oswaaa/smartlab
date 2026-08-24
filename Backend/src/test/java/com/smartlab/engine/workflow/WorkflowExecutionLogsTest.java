package com.smartlab.engine.workflow;

import com.smartlab.global.util.JsonNodeSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowExecutionLogsTest {

    @Test
    void formatsNodeAndInterfaceEdgeMessages() {
        assertThat(WorkflowExecutionLogs.nodeCreated("start1", 1L)).isEqualTo("节点已创建: start1 (#1)");
        assertThat(WorkflowExecutionLogs.lifecycleChanged("start1", 1L, "PENDING", "RUNNING"))
                .isEqualTo("节点 start1 (#1) 生命周期 PENDING → RUNNING");
        assertThat(WorkflowExecutionLogs.emitted("start1", 1L, "Interface_workflow_out", "ACTIVE", null))
                .isEqualTo("节点 start1 (#1) 接口 Interface_workflow_out 发出信号 ACTIVE");
        assertThat(WorkflowExecutionLogs.received("device1", 2L, "Interface_state_in", "CMD_STATE",
                JsonNodeSupport.objectNode().put("stateName", "COMPLETED").put("messageId", "m-1")))
                .isEqualTo("节点 device1 (#2) 接口 Interface_state_in 收到信号 CMD_STATE，stateName=COMPLETED, messageId=m-1");
    }
}
