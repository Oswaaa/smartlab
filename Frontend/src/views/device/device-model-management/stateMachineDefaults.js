import { adapterOutSignals, standardCmdEvents } from './deviceModelUiConstants'

const asArray = function(value) { return Array.isArray(value) ? value : [] }
const uniqueStrings = function(values) { return Array.from(new Set(asArray(values).filter(Boolean))) }
const makeUiKey = function(prefix) { return prefix + '_' + Math.random().toString(36).substr(2, 9) }

export function defaultAdapterContract() {
  return { config: { protocol: 'MQTT' }, commands: [], telemetry: { adapterAttributes: [], attributesMapping: [] }, events: [] }
}

export function defaultStateSpace(initialStateName) {
  return { initialStateName, states: [{ _key: makeUiKey('state'), stateName: initialStateName, onEntry: defaultStateEntryActions('OP', initialStateName) }] }
}

export function defaultCommandLifecycle() {
  return { initialStateName: 'IDLE', states: commandLifecycleStateNames().map(stateName => ({ _key: makeUiKey('cmd_state'), stateName, onEntry: defaultStateEntryActions('CMD', stateName) })) }
}

export function commandLifecycleStateNames() {
  return ['IDLE', 'SENT', 'RECEIVED', 'RUNNING', 'DONE', 'FAILED', 'TIMEOUT', 'CANCELLED']
}

export function defaultInterfaces(adapterSignals = []) {
  const signals = uniqueStrings([...standardCmdEvents, ...asArray(adapterSignals).filter(Boolean)])
  return [
    { _key: 'iface_workflow', name: 'Interface_workflow_in', direction: 'IN', interfaceType: 'WORKFLOW', allowedSignals: ['EXECUTE_START', 'EXECUTE_PAUSE', 'EXECUTE_RESUME', 'EXECUTE_CANCEL', 'EXECUTE_RESET'] },
    { _key: 'iface_status', name: 'Interface_status_out', direction: 'OUT', interfaceType: 'STAT', allowedSignals: ['OP_STATE', 'CMD_STATE'] },
    { _key: 'iface_control', name: 'Interface_control_in', direction: 'IN', interfaceType: 'CONTROL', allowedSignals: ['MANUAL_EXECUTE', 'MANUAL_CANCEL', 'MANUAL_PAUSE', 'MANUAL_RESUME', 'MANUAL_RESET'] },
    { _key: 'iface_constraint', name: 'Interface_constraint_in', direction: 'IN', interfaceType: 'CONSTRAINT', allowedSignals: ['CONSTRAINT_CANCEL', 'CONSTRAINT_PAUSE', 'CONSTRAINT_RESUME', 'CONSTRAINT_RESET'] },
    { _key: 'iface_adapter_in', name: 'Interface_adapter_in', direction: 'IN', interfaceType: 'ADAPTER', allowedSignals: signals },
    { _key: 'iface_adapter_out', name: 'Interface_adapter_out', direction: 'OUT', interfaceType: 'ADAPTER', allowedSignals: adapterOutSignals }
  ]
}

export function adapterInterfaceName() {
  return 'Interface_adapter_in'
}

export function adapterOutAction(signalName) {
  return { actionName: 'SEND', payload: { interfaceName: 'Interface_adapter_out', signalName } }
}

export function defaultStateEntryActions(type, stateName) {
  return [{
    actionName: 'SEND',
    payload: {
      interfaceName: 'Interface_status_out',
      signalName: type === 'CMD' ? 'CMD_STATE' : 'OP_STATE',
      stateName
    }
  }]
}

export function defaultCommandLifecycleTransitions() {
  return [
    { description: '工作流触发指令下发', fromStateName: 'IDLE', toStateName: 'SENT', trigger: { interfaceName: 'Interface_workflow_in', signalName: 'EXECUTE_START' }, actions: [adapterOutAction('CMD_START')] },
    { description: '用户手动触发指令下发', fromStateName: 'IDLE', toStateName: 'SENT', trigger: { interfaceName: 'Interface_control_in', signalName: 'MANUAL_EXECUTE' }, actions: [adapterOutAction('CMD_START')] },
    { description: 'Adapter 已接收', fromStateName: 'SENT', toStateName: 'RECEIVED', trigger: { interfaceName: adapterInterfaceName(), signalName: 'COMMAND_RECEIVED' }, actions: [] },
    { description: 'Adapter 执行中', fromStateName: 'RECEIVED', toStateName: 'RUNNING', trigger: { interfaceName: adapterInterfaceName(), signalName: 'COMMAND_RUNNING' }, actions: [] },
    { description: '执行完成', fromStateName: 'RUNNING', toStateName: 'DONE', trigger: { interfaceName: adapterInterfaceName(), signalName: 'COMMAND_COMPLETED' }, actions: [] },
    { description: '执行失败', fromStateName: 'RUNNING', toStateName: 'FAILED', trigger: { interfaceName: adapterInterfaceName(), signalName: 'COMMAND_FAILED' }, actions: [] },
    { description: '执行超时', fromStateName: 'RUNNING', toStateName: 'TIMEOUT', trigger: { interfaceName: adapterInterfaceName(), signalName: 'COMMAND_TIMEOUT' }, actions: [] },
    { description: 'Adapter 确认取消', fromStateName: 'SENT', toStateName: 'CANCELLED', trigger: { interfaceName: adapterInterfaceName(), signalName: 'COMMAND_CANCELLED' }, actions: [] },
    { description: '工作流取消指令', fromStateName: 'RUNNING', toStateName: 'CANCELLED', trigger: { interfaceName: 'Interface_workflow_in', signalName: 'EXECUTE_CANCEL' }, actions: [adapterOutAction('CMD_CANCEL')] },
    { description: '用户手动取消指令', fromStateName: 'RUNNING', toStateName: 'CANCELLED', trigger: { interfaceName: 'Interface_control_in', signalName: 'MANUAL_CANCEL' }, actions: [adapterOutAction('CMD_CANCEL')] },
    { description: '约束引擎取消指令', fromStateName: 'RUNNING', toStateName: 'CANCELLED', trigger: { interfaceName: 'Interface_constraint_in', signalName: 'CONSTRAINT_CANCEL' }, actions: [adapterOutAction('CMD_CANCEL')] }
  ]
}

