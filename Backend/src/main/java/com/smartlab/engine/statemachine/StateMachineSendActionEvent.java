package com.smartlab.engine.statemachine;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/** 状态机通过Adapter输出接口发出的能力命令 */
public record StateMachineSendActionEvent(
        Long instanceId,
        String interfaceName,
        String interfaceType,
        String signalName,
        String capabilityName,
        String commandName,
        String messageId,
        Map<String, Object> parameters,
        ObjectNode interfaceSignal
) {
}