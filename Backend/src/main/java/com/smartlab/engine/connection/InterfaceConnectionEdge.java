package com.smartlab.engine.connection;

/**
 * 工作流接口连接的结构边。模板在 FLOW_MODELS.interface_connection；
 * 设备端只记录型号与接口名，运行时实例来自任务 resourceMap。
 */
public record InterfaceConnectionEdge(
        String connectionType,
        Long flowModelId,
        Long sourceNodeIdRef,
        String sourceInterfaceName,
        Long targetNodeIdRef,
        String targetInterfaceName,
        Long deviceModelId
) {
}
