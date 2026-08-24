package com.smartlab.engine.connection;

/**
 * 任务期内一条活跃的接口连接边。模板仍在 FLOW_MODELS.interface_connection；
 * 本记录带任务步骤与设备实例，任务/步骤结束后从运行时索引移除。
 */
public record LiveInterfaceConnection(
        String connectionType,
        Long taskId,
        Long flowModelId,
        Long sourceNodeIdRef,
        Long targetNodeIdRef,
        String sourceInterfaceName,
        String targetInterfaceName,
        Long deviceModelId,
        Long deviceInstanceId,
        Long sourceStepId,
        Long targetStepId
) {
}
