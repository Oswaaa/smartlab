package com.smartlab.engine.statemachine;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/** State-machine SEND output with its declared interface contract. */
public record StateMachineSendActionEvent(
        Long instanceId,
        String interfaceName,
        String interfaceType,
        String signalName,
        String commandName,
        String messageId,
        Map<String, Object> parameters,
        ObjectNode interfaceSignal
) {}
