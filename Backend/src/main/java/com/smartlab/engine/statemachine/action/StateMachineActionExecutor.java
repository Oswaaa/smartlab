package com.smartlab.engine.statemachine.action;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.StateMachineModels;

public interface StateMachineActionExecutor {
    String actionName();
    ObjectNode execute(StateMachineModels.ActionDefinition action, StateMachineModels.EventContext context);
}
