import json
import codecs

file_path = "src/main/resources/schemas/device-state-machine-model.json"

with codecs.open(file_path, "r", "utf-8") as f:
    schema = json.load(f)

# Add all the x- extensions
schema["x-automaticTransitionStateSpaces"] = ["CMD"]
schema["x-commandTerminalStates"] = ["COMPLETED", "FAILED", "ABORTED"]

schema["x-executionLifecycleMainPath"] = [
    { "fromStateName": "IDLE", "toStateName": "SENT", "triggerPolicy": "SYSTEM" },
    { "fromStateName": "SENT", "toStateName": "RECEIVED", "triggerPolicy": "OPTIONAL" },
    { "fromStateName": "RECEIVED", "toStateName": "RUNNING", "triggerPolicy": "OPTIONAL" },
    { "fromStateName": "RUNNING", "toStateName": "COMPLETED", "triggerPolicy": "REQUIRED" }
]

schema["x-executionLifecycleBranches"] = [
    { "kind": "FAILURE", "targetStateName": "FAILED", "sourceStateNames": ["SENT", "RECEIVED", "RUNNING"], "triggerPolicy": "OPTIONAL_EVENT" },
    { "kind": "TERMINATION", "targetStateName": "ABORTED", "sourceStateNames": ["SENT", "RECEIVED", "RUNNING"], "triggerPolicy": "OPTIONAL_EVENT" }
]

schema["x-systemTransitions"] = [
    {
      "description": "工作流触发指令下发", "stateSpace": "CMD", "fromStateName": "IDLE", "toStateName": "SENT",
      "trigger": { "interfaceName": "Interface_workflow_in", "signalName": "WF_EXECUTE_START" },
      "actions": [{ "actionName": "SEND", "payload": { "interfaceName": "Interface_adapter_out", "signalName": "CMD_START" } }]
    },
    {
      "description": "用户手动触发指令下发", "stateSpace": "CMD", "fromStateName": "IDLE", "toStateName": "SENT",
      "trigger": { "interfaceName": "Interface_control_in", "signalName": "MANUAL_EXECUTE_START" },
      "actions": [{ "actionName": "SEND", "payload": { "interfaceName": "Interface_adapter_out", "signalName": "CMD_START" } }]
    },
    { "description": "工作流请求中止已发送指令", "stateSpace": "CMD", "fromStateName": "SENT", "toStateName": "SENT", "trigger": { "interfaceName": "Interface_workflow_in", "signalName": "WF_EXECUTE_ABORT" }, "actions": [{ "actionName": "SEND", "payload": { "interfaceName": "Interface_adapter_out", "signalName": "CMD_ABORT" } }] },
    { "description": "用户请求中止已发送指令", "stateSpace": "CMD", "fromStateName": "SENT", "toStateName": "SENT", "trigger": { "interfaceName": "Interface_control_in", "signalName": "MANUAL_EXECUTE_ABORT" }, "actions": [{ "actionName": "SEND", "payload": { "interfaceName": "Interface_adapter_out", "signalName": "CMD_ABORT" } }] },
    { "description": "约束引擎请求中止已发送指令", "stateSpace": "CMD", "fromStateName": "SENT", "toStateName": "SENT", "trigger": { "interfaceName": "Interface_constraint_in", "signalName": "CONSTRAINT_ABORT" }, "actions": [{ "actionName": "SEND", "payload": { "interfaceName": "Interface_adapter_out", "signalName": "CMD_ABORT" } }] },
    { "description": "工作流请求中止已接收指令", "stateSpace": "CMD", "fromStateName": "RECEIVED", "toStateName": "RECEIVED", "trigger": { "interfaceName": "Interface_workflow_in", "signalName": "WF_EXECUTE_ABORT" }, "actions": [{ "actionName": "SEND", "payload": { "interfaceName": "Interface_adapter_out", "signalName": "CMD_ABORT" } }] },
    { "description": "用户请求中止已接收指令", "stateSpace": "CMD", "fromStateName": "RECEIVED", "toStateName": "RECEIVED", "trigger": { "interfaceName": "Interface_control_in", "signalName": "MANUAL_EXECUTE_ABORT" }, "actions": [{ "actionName": "SEND", "payload": { "interfaceName": "Interface_adapter_out", "signalName": "CMD_ABORT" } }] },
    { "description": "约束引擎请求中止已接收指令", "stateSpace": "CMD", "fromStateName": "RECEIVED", "toStateName": "RECEIVED", "trigger": { "interfaceName": "Interface_constraint_in", "signalName": "CONSTRAINT_ABORT" }, "actions": [{ "actionName": "SEND", "payload": { "interfaceName": "Interface_adapter_out", "signalName": "CMD_ABORT" } }] },
    { "description": "工作流请求中止执行中指令", "stateSpace": "CMD", "fromStateName": "RUNNING", "toStateName": "RUNNING", "trigger": { "interfaceName": "Interface_workflow_in", "signalName": "WF_EXECUTE_ABORT" }, "actions": [{ "actionName": "SEND", "payload": { "interfaceName": "Interface_adapter_out", "signalName": "CMD_ABORT" } }] },
    { "description": "用户请求中止执行中指令", "stateSpace": "CMD", "fromStateName": "RUNNING", "toStateName": "RUNNING", "trigger": { "interfaceName": "Interface_control_in", "signalName": "MANUAL_EXECUTE_ABORT" }, "actions": [{ "actionName": "SEND", "payload": { "interfaceName": "Interface_adapter_out", "signalName": "CMD_ABORT" } }] },
    { "description": "约束引擎请求中止执行中指令", "stateSpace": "CMD", "fromStateName": "RUNNING", "toStateName": "RUNNING", "trigger": { "interfaceName": "Interface_constraint_in", "signalName": "CONSTRAINT_ABORT" }, "actions": [{ "actionName": "SEND", "payload": { "interfaceName": "Interface_adapter_out", "signalName": "CMD_ABORT" } }] }
]

schema["x-standardInterfaces"] = [
    {
      "name": "Interface_workflow_in",
      "direction": "IN",
      "interfaceType": "WORKFLOW",
      "allowedSignalsRef": "protocol-dict.json#/definitions/WorkflowControlSignal"
    },
    {
      "name": "Interface_status_out",
      "direction": "OUT",
      "interfaceType": "STAT",
      "allowedSignals": [
        "OP_STATE",
        "CMD_STATE"
      ]
    },
    {
      "name": "Interface_control_in",
      "direction": "IN",
      "interfaceType": "CONTROL",
      "allowedSignalsRef": "protocol-dict.json#/definitions/ManualControlSignal"
    },
    {
      "name": "Interface_constraint_in",
      "direction": "IN",
      "interfaceType": "CONSTRAINT",
      "allowedSignalsRef": "protocol-dict.json#/definitions/ConstraintControlSignal"
    },
    {
      "name": "Interface_adapter_in",
      "direction": "IN",
      "interfaceType": "ADAPTER",
      "allowedSignals": []
    },
    {
      "name": "Interface_adapter_out",
      "direction": "OUT",
      "interfaceType": "ADAPTER",
      "allowedSignalsRef": "protocol-dict.json#/definitions/AdapterOutboundSignal"
    }
]

schema["x-actionCatalog"] = [
    {
        "actionName": "SEND",
        "displayName": "发送信号",
        "description": "向外发送信号"
    }
]

with codecs.open(file_path, "w", "utf-8") as f:
    json.dump(schema, f, indent=2, ensure_ascii=False)
    
print("Added all missing schema extensions!")
