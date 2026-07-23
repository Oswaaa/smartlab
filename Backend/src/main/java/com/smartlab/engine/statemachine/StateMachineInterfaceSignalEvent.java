package com.smartlab.engine.statemachine;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/** Emitted after the state machine produces an output interface signal. */
public record StateMachineInterfaceSignalEvent(
        Long instanceId,
        String interfaceName,
        String interfaceType,
        ObjectNode signal,
        Map<String, Object> executionContext
) {}