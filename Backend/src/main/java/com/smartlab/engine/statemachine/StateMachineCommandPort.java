package com.smartlab.engine.statemachine;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

/**
 * Command boundary exposed to other logical engines. Implementations must route
 * through the modeled state-machine input interface and never expose runtime state.
 */
public interface StateMachineCommandPort {
    List<ObjectNode> dispatchInputSignal(Long deviceInstanceId,
                                          String interfaceName,
                                          String signalName,
                                          Map<String, Object> context);

    List<ObjectNode> executeConstraintCapability(Long deviceInstanceId,
                                                  String capabilityName,
                                                  Map<String, Object> parameters);

    List<ObjectNode> abortConstraintCommand(Long deviceInstanceId, String messageId);
}
