package com.smartlab.engine.connection;

/**
 * 任务内一条活跃的端口连接边。模板仍在 FLOW_MODELS.port_connection；
 * 本记录只在下游步骤处于 PENDING/RUNNING/TERMINATING 时存在于运行时索引。
 */
public record PortConnectionEdge(
        Long taskId,
        Long flowModelId,
        Long sourceNodeIdRef,
        Long targetNodeIdRef,
        String sourcePortName,
        String targetPortName,
        Long targetStepId,
        Long parentStepId,
        int stepDepth
) {
}
