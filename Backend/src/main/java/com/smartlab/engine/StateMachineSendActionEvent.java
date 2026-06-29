package com.smartlab.engine;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * 状态机 SEND 动作产生的接口输出事件。
 */
public record StateMachineSendActionEvent(
        Long instanceId,
        String interfaceName,
        String signalName,
        String commandId,
        Map<String, Object> parameters,
        ObjectNode interfaceSignal
) {
}
