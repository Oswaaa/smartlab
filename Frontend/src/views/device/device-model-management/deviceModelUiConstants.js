export const attributeDataTypes = ['INTEGER', 'DOUBLE', 'BOOLEAN']
export const adapterDataTypes = ['INTEGER', 'DOUBLE', 'BOOLEAN', 'STRING']
export const operators = ['GT', 'LT', 'GE', 'LE', 'EQ', 'NE', 'BETWEEN', 'IN']
export const interfaceTypes = ['WORKFLOW', 'STAT', 'ADAPTER', 'CONTROL', 'CONSTRAINT']
export const stateActionNames = ['SEND', 'ASSIGN']
export const standardCmdEvents = ['COMMAND_RECEIVED', 'COMMAND_RUNNING', 'COMMAND_COMPLETED', 'COMMAND_FAILED', 'COMMAND_TIMEOUT', 'COMMAND_CANCELLED']
export const adapterOutSignals = ['CMD_START', 'CMD_CANCEL', 'CMD_PAUSE', 'CMD_RESUME', 'CMD_RESET']

export function getSignalTagType(signalName) {
  if (signalName === 'OP_STATE') return 'success'
  if (signalName === 'CMD_STATE') return 'primary'
  if (signalName === 'CMD_START') return 'danger'
  if (signalName === 'CMD_CANCEL') return 'warning'
  return 'info'
}

export function formatSignalName(signalName) {
  if (signalName === 'OP_STATE') return '输出功能状态 (OP_STATE)'
  if (signalName === 'CMD_STATE') return '输出指令周期 (CMD_STATE)'
  if (signalName === 'CMD_START') return '下发启动命令 (CMD_START)'
  if (signalName === 'CMD_CANCEL') return '下发取消命令 (CMD_CANCEL)'
  return signalName || ''
}

export function formatSignalShortName(signalName) {
  if (signalName === 'OP_STATE') return '输出状态'
  if (signalName === 'CMD_STATE') return '输出指令'
  if (signalName === 'CMD_START') return '启动命令'
  if (signalName === 'CMD_CANCEL') return '取消命令'
  if (signalName === 'CMD_PAUSE') return '暂停命令'
  if (signalName === 'CMD_RESUME') return '恢复命令'
  if (signalName === 'CMD_RESET') return '重置命令'
  return signalName || ''
}

export function getSignalsForInterface(interfaceName) {
  if (interfaceName === 'Interface_status_out') return ['OP_STATE', 'CMD_STATE']
  if (interfaceName === 'Interface_adapter_out') return adapterOutSignals
  return []
}
