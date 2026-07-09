// ==========================================
// 1. UI 静态常量与下拉菜单项
// ==========================================
export const attributeDataTypes = ['INTEGER', 'DOUBLE', 'BOOLEAN']
export const adapterDataTypes = ['INTEGER', 'DOUBLE', 'BOOLEAN', 'STRING']
export const operators = ['GT', 'LT', 'GE', 'LE', 'EQ', 'NE', 'BETWEEN', 'IN']
export const interfaceTypes = ['WORKFLOW', 'STAT', 'ADAPTER', 'CONTROL', 'CONSTRAINT']
export const stateActionNames = ['SEND', 'ASSIGN']
export const standardCmdEvents = ['COMMAND_RECEIVED', 'COMMAND_RUNNING', 'COMMAND_COMPLETED', 'COMMAND_FAILED', 'COMMAND_TIMEOUT', 'COMMAND_CANCELLED']
export const adapterOutSignals = ['CMD_START', 'CMD_CANCEL', 'CMD_PAUSE', 'CMD_RESUME', 'CMD_RESET']

// ==========================================
// 2. 信号格式化与展示辅助函数
// ==========================================
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

// ==========================================
// 3. 状态机与契约默认配置结构
// ==========================================
const asArray = value => Array.isArray(value) ? value : []
const stringValue = value => value == null ? '' : String(value).trim()
const uniqueStrings = values => [...new Set(asArray(values).map(stringValue).filter(Boolean))]
const makeUiKey = prefix => prefix + '_' + Math.random().toString(36).substr(2, 9)

function normalizeDataType(value, fallback, allowed) {
  const text = String(value || '').toUpperCase()
  return allowed.includes(text) ? text : fallback
}

export function defaultAdapterContract() {
  return { config: { protocol: 'MQTT' }, commands: [], telemetry: { adapterAttributes: [], attributesMapping: [] }, events: [] }
}

export function defaultStateSpace(initialStateName) {
  return {
    initialStateName,
    states: [{ _key: makeUiKey('state'), stateName: initialStateName, onEntry: defaultStateEntryActions('OP', initialStateName) }]
  }
}

export function defaultCommandLifecycle() {
  return {
    initialStateName: 'IDLE',
    states: commandLifecycleStateNames().map(stateName => ({
      _key: makeUiKey('cmd_state'),
      stateName,
      onEntry: defaultStateEntryActions('CMD', stateName)
    }))
  }
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

// ==========================================
// 4. 适配器 Manifest 配置映射逻辑
// ==========================================
export function adapterDeviceCategoryOptions(parsed) {
  return asArray(parsed?.deviceCategories).map(category => {
    const template = category?.deviceTemplate || {}
    const categoryName = stringValue(category?.categoryName)
    const templateName = stringValue(template.templateName)
    return {
      key: categoryName || templateName,
      name: categoryName || templateName || '未命名类别',
      description: stringValue(category?.categoryDescription || template.description),
      categoryName,
      templateName,
      category
    }
  }).filter(item => item.key)
}

export function adapterCategoryKey(option) {
  return stringValue(option?.key || option?.categoryName || option?.templateName || option?.name)
}

export function adapterCategoryLabel(option) {
  const name = adapterCategoryKey(option) || '未命名类别'
  return option?.description ? name + ' - ' + option.description : name
}

function normalizeCommandParameter(param) {
  return {
    _key: param._key || makeUiKey('cmd_param'),
    paramName: stringValue(param.paramName || param.name),
    dataType: normalizeDataType(param.dataType || param.type, 'DOUBLE', adapterDataTypes),
    description: stringValue(param.description),
    hidden: param.hidden === true,
    sourceField: stringValue(param.sourceField)
  }
}

function normalizeEventsToFlatList(events) {
  if (Array.isArray(events)) {
    return events.map(event => ({
      _key: event._key || makeUiKey('event'),
      eventName: stringValue(event.eventName || event.name),
      description: stringValue(event.description),
      eventType: event.eventType || 'OP'
    }))
  }

  const rows = []
  asArray(events?.cmdEvents).forEach(event => {
    rows.push({
      _key: event._key || makeUiKey('event'),
      eventName: stringValue(event.eventName || event.name),
      description: stringValue(event.description),
      eventType: 'CMD'
    })
  })
  asArray(events?.opEvents).forEach(event => {
    rows.push({
      _key: event._key || makeUiKey('event'),
      eventName: stringValue(event.eventName || event.name),
      description: stringValue(event.description),
      eventType: 'OP'
    })
  })
  return rows
}

export function buildAdapterContractFromManifestCategory(parsed, category) {
  const template = category?.deviceTemplate || {}
  const commands = asArray(template.commands).map(command => ({
    commandName: stringValue(command.name || command.commandName),
    description: stringValue(command.description),
    commandParameters: asArray(command.parameters || command.commandParameters).map(normalizeCommandParameter)
  }))

  const adapterAttributes = asArray(template.attributes).map(attr => ({
    name: stringValue(attr.name),
    dataType: normalizeDataType(attr.dataType, 'DOUBLE', adapterDataTypes),
    description: stringValue(attr.description)
  }))

  return {
    config: {
      protocol: 'MQTT',
      adapterName: stringValue(parsed.adapterName),
      categoryName: stringValue(category?.categoryName),
      templateName: stringValue(template.templateName)
    },
    commands,
    telemetry: { adapterAttributes, attributesMapping: [] },
    events: normalizeEventsToFlatList(template.events)
  }
}
