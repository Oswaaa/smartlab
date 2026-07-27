const system = (systemKey, value) => ({ ...value, _system: true, _systemKey: systemKey })

const workflowInterface = (name, direction, bindingTriggers = [], interfaceType = 'WORKFLOW', allowedSignals = ['ACTIVE']) => ({ name, direction, interfaceType, allowedSignals, bindingTriggers })

const trigger = (systemKey, object, operator, threshold, action) => system(systemKey, {
  action,
  condition: { object, operator, threshold }
})

const lockedLifecycle = () => system('lifecycle', {
  initialStateName: 'PENDING',
  states: ['PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'TERMINATING', 'TERMINATED'],
  transitions: [
    ['PENDING', 'RUNNING'], ['PENDING', 'TERMINATED'], ['RUNNING', 'SUCCEEDED'], ['RUNNING', 'FAILED'],
    ['RUNNING', 'TERMINATING'], ['TERMINATING', 'TERMINATED'], ['TERMINATING', 'FAILED']
  ].map(([fromStateName, toStateName]) => system(`lifecycle.${fromStateName.toLowerCase()}.${toStateName.toLowerCase()}`, { fromStateName, toStateName }))
})

const systemWorkflowInterface = (systemKey, name, direction, bindingTriggers = [], interfaceType = 'WORKFLOW', allowedSignals = ['ACTIVE']) =>
  system(systemKey, workflowInterface(name, direction, bindingTriggers, interfaceType, allowedSignals))

const emitAction = (systemKey, actionName, targetInterfaceName, signalName) => system(systemKey, {
  actionName,
  actionType: 'EMIT',
  targetInterfaceName,
  signalName
})

export function isSystemItem(item) {
  return item?._system === true
}

export function normalizeTypedValue(dataType, value) {
  if (dataType === 'JSON' && Array.isArray(value)) {
    const entries = value.map(entry => [String(entry?.key || '').trim(), entry?.value])
    if (entries.some(([key]) => !key) || new Set(entries.map(([key]) => key)).size !== entries.length) {
      throw new Error('JSON参数键不能为空且不能重复')
    }
    return Object.fromEntries(entries)
  }
  const valid = (dataType === 'INTEGER' && Number.isInteger(value)) ||
    (dataType === 'DOUBLE' && typeof value === 'number' && Number.isFinite(value)) ||
    (dataType === 'BOOLEAN' && typeof value === 'boolean') ||
    (dataType === 'STRING' && typeof value === 'string') ||
    (dataType === 'JSON' && value !== null && !Array.isArray(value) && typeof value === 'object')
  if (valid) return value
  const label = { INTEGER: '整数', DOUBLE: '数值', BOOLEAN: '布尔值', STRING: '字符串', JSON: '对象' }[dataType] || '有效值'
  throw new Error(`${dataType}参数必须是${label}`)
}

export function createFunctionNode(functionType, name) {
  if (functionType === 'BRANCH') return createBranchNode(name)
  if (functionType === 'START') return createStartNode(name)
  if (functionType === 'END') return createEndNode(name)
  if (functionType === 'AGGREGATE') return createAggregateNode(name)
  return createGenericFunctionNode(functionType, name)
}

function functionNode(name, functionType, interfaces, actions = []) {
  return { name, nodeType: 'FUNC_NODE', functionType, internalVariables: [], lifecycle: lockedLifecycle(), interfaces, ports: [], actions }
}

function createBranchNode(name) {
  const emitTrue = emitAction('branch.emitTrue', 'emitTrue', 'Interface_true_out', 'ACTIVE')
  const emitFalse = emitAction('branch.emitFalse', 'emitFalse', 'Interface_false_out', 'ACTIVE')
  return {
    ...functionNode(name, 'BRANCH', [
      systemWorkflowInterface('branch.workflowIn', 'Interface_workflow_in', 'IN', [
        trigger('branch.true', 'expression', '=', true, 'emitTrue'),
        trigger('branch.false', 'expression', '=', false, 'emitFalse')
      ]),
      systemWorkflowInterface('branch.trueOut', 'Interface_true_out', 'OUT'),
      systemWorkflowInterface('branch.falseOut', 'Interface_false_out', 'OUT')
    ], [emitTrue, emitFalse]),
    expression: ''
  }
}

function createStartNode(name) {
  return functionNode(name, 'START', [
    systemWorkflowInterface('start.workflowOut', 'Interface_workflow_out', 'OUT')
  ], [emitAction('start.emitActive', 'emitActive', 'Interface_workflow_out', 'ACTIVE')])
}

function createEndNode(name) {
  return functionNode(name, 'END', [
    systemWorkflowInterface('end.workflowIn', 'Interface_workflow_in', 'IN')
  ])
}

function createAggregateNode(name) {
  const emitActive = emitAction('aggregate.emitActive', 'emitActive', 'Interface_workflow_out', 'ACTIVE')
  return functionNode(name, 'AGGREGATE', [
    systemWorkflowInterface('aggregate.workflowIn', 'Interface_workflow_in', 'IN', [
      trigger('aggregate.active', 'inputSignalName', '=', 'ACTIVE', 'emitActive')
    ]),
    systemWorkflowInterface('aggregate.workflowOut', 'Interface_workflow_out', 'OUT')
  ], [emitActive])
}

function createGenericFunctionNode(functionType, name) {
  return functionNode(name, functionType, [
    systemWorkflowInterface('linear.workflowIn', 'Interface_workflow_in', 'IN'),
    systemWorkflowInterface('linear.workflowOut', 'Interface_workflow_out', 'OUT')
  ])
}

export function createDeviceNode(model, name) {
  const startDevice = emitAction('device.startDevice', 'startDevice', 'Interface_state_out', 'WF_EXECUTE_START')
  const completeNode = emitAction('device.completeNode', 'completeNode', 'Interface_workflow_out', 'ACTIVE')
  return {
    name,
    nodeType: 'DEV_NODE',
    deviceModelId: model.id,
    capability: model.capabilities?.[0]
      ? { capabilityName: model.capabilities[0].capabilityName, capabilityParameters: {} }
      : null,
    _capabilityParameterTypes: Object.fromEntries((model.capabilities?.[0]?.parameters ?? []).map(parameter => [parameter.parameterName, parameter.dataType])),
    internalVariables: [],
    lifecycle: lockedLifecycle(),
    interfaces: [
      systemWorkflowInterface('device.workflowIn', 'Interface_workflow_in', 'IN', [
        trigger('device.workflowStart', 'inputSignalName', '=', 'ACTIVE', 'startDevice')
      ]),
      systemWorkflowInterface('device.stateOut', 'Interface_state_out', 'OUT', [], 'STATE', ['WF_EXECUTE_START']),
      systemWorkflowInterface('device.stateIn', 'Interface_state_in', 'IN', [
        trigger('device.stateCompleted', 'inputPayload.stateName', '=', 'COMPLETED', 'completeNode')
      ], 'STATE', ['CMD_STATE', 'OP_STATE']),
      systemWorkflowInterface('device.workflowOut', 'Interface_workflow_out', 'OUT')
    ],
    ports: [],
    actions: [startDevice, completeNode]
  }
}

export function createSubflowNode(workflow, name) {
  return {
    name,
    nodeType: 'SUBFLOW_NODE',
    subFlowModelId: workflow.id,
    internalVariables: [],
    lifecycle: lockedLifecycle(),
    interfaces: [
      systemWorkflowInterface('subflow.workflowIn', 'Interface_workflow_in', 'IN'),
      systemWorkflowInterface('subflow.workflowOut', 'Interface_workflow_out', 'OUT')
    ],
    ports: [],
    actions: []
  }
}

export function replaceCapability(node, capability, previousCapability) {
  const oldValues = node.capability?.capabilityParameters ?? {}
  const oldTypes = Object.fromEntries((previousCapability?.parameters ?? []).map(parameter => [parameter.parameterName, parameter.dataType]))
  const resolvedOldTypes = Object.keys(oldTypes).length ? oldTypes : (node._capabilityParameterTypes ?? {})
  const nextTypes = Object.fromEntries((capability.parameters ?? []).map(parameter => [parameter.parameterName, parameter.dataType]))
  const capabilityParameters = Object.fromEntries(Object.entries(nextTypes)
    .filter(([name, type]) => resolvedOldTypes[name] === type && Object.hasOwn(oldValues, name))
    .map(([name]) => [name, oldValues[name]]))
  return {
    ...node,
    capability: { capabilityName: capability.capabilityName, capabilityParameters },
    _capabilityParameterTypes: nextTypes
  }
}

export function removeVariable(node, variableName) {
  const port = (node.ports ?? []).find(item => item.internalVariableName === variableName)
  if (port) throw new Error(`变量${variableName}仍被端口${port.name}引用`)
  return { ...node, internalVariables: (node.internalVariables ?? []).filter(item => item.name !== variableName) }
}

export function removePort(node, portName, portConnections) {
  if ((node.ports ?? []).some(item => item.name === portName && isSystemItem(item))) {
    throw new Error(`系统端口${portName}不可删除`)
  }
  return {
    node: { ...node, ports: (node.ports ?? []).filter(item => item.name !== portName) },
    portConnections: (portConnections ?? []).filter(connection =>
      connection.portName !== portName && connection.sourcePortName !== portName && connection.targetPortName !== portName && !(connection.source?.nodeName === node.name && connection.source?.portName === portName) && !(connection.target?.nodeName === node.name && connection.target?.portName === portName))
  }
}

export function removeAction(node, actionName) {
  const action = (node.actions ?? []).find(item => item.actionName === actionName)
  if (isSystemItem(action)) throw new Error(`系统动作${actionName}不可删除`)
  return { ...node, actions: (node.actions ?? []).filter(item => item.actionName !== actionName) }
}

export function validateNodeDefinition(node, context = {}) {
  const errors = []
  uniqueErrors(errors, node.internalVariables, 'name', 'internalVariables')
  uniqueErrors(errors, node.ports, 'name', 'ports')
  uniqueErrors(errors, node.interfaces, 'name', 'interfaces')
  uniqueErrors(errors, node.actions, 'actionName', 'actions')
  validateVariables(node, errors)
  validateSystemSkeleton(node, errors)
  validatePorts(node, errors)
  validateActions(node, errors)
  validateTriggers(node, errors)
  if (node.nodeType === 'DEV_NODE') validateDeviceConfiguration(node, context.deviceModel, errors)
  return errors
}

function uniqueErrors(errors, items = [], field, path) {
  const seen = new Set()
  items.forEach((item, index) => {
    if (seen.has(item[field])) errors.push({ path: `${path}[${index}].${field}`, message: `${field}必须唯一` })
    seen.add(item[field])
  })
}

function validateVariables(node, errors) {
  const allowed = new Set(['INTEGER', 'DOUBLE', 'BOOLEAN', 'STRING', 'JSON'])
  ;(node.internalVariables ?? []).forEach((variable, index) => {
    if (!allowed.has(variable.dataType)) errors.push({ path: `internalVariables[${index}].dataType`, message: '变量类型无效' })
  })
}

function validateSystemSkeleton(node, errors) {
  const expected = expectedSystemSkeleton(node)
  if (!expected) return
  validateSystemValue(node.lifecycle, expected.lifecycle, 'lifecycle', errors)
  validateSystemCollection(node.interfaces, expected.interfaces, 'interfaces', errors)
  validateSystemCollection(node.actions, expected.actions, 'actions', errors)
}

function expectedSystemSkeleton(node) {
  if (node.nodeType === 'DEV_NODE') return createDeviceNode({ id: node.deviceModelId, capabilities: [] }, node.name)
  if (node.nodeType === 'SUBFLOW_NODE') return createSubflowNode({ id: node.subFlowModelId }, node.name)
  if (node.nodeType === 'FUNC_NODE') return createFunctionNode(node.functionType, node.name)
  return null
}

function validateSystemCollection(actual = [], expected = [], path, errors) {
  const expectedByKey = new Map(expected.map(item => [item._systemKey, item]))
  for (const expectedItem of expected) {
    const actualItem = actual.find(item => item?._systemKey === expectedItem._systemKey)
    validateSystemValue(actualItem, expectedItem, path, errors)
  }
  actual.filter(isSystemItem).forEach(item => {
    if (!expectedByKey.has(item._systemKey)) errors.push({ path, message: `不允许的系统项${item._systemKey}` })
  })
}

function validateSystemValue(actual, expected, path, errors) {
  if (!actual || !isSystemItem(actual) || !sameSystemValue(actual, expected, path)) {
    errors.push({ path, message: `系统项${expected._systemKey}缺失或已篡改` })
  }
}

function sameSystemValue(actual, expected, path) {
  if (path !== 'interfaces') return JSON.stringify(actual) === JSON.stringify(expected)
  const { bindingTriggers: actualTriggers = [], ...actualInterface } = actual
  const { bindingTriggers: expectedTriggers = [], ...expectedInterface } = expected
  if (JSON.stringify(actualInterface) !== JSON.stringify(expectedInterface)) return false
  const expectedByKey = new Map(expectedTriggers.filter(isSystemItem).map(item => [item._systemKey, item]))
  return [...expectedByKey].every(([key, trigger]) => JSON.stringify(actualTriggers.find(item => item?._systemKey === key)) === JSON.stringify(trigger))
}

function validatePorts(node, errors) {
  const variables = new Map((node.internalVariables ?? []).map(item => [item.name, item]))
  ;(node.ports ?? []).forEach((port, index) => {
    if (!variables.has(port.internalVariableName)) errors.push({ path: `ports[${index}].internalVariableName`, message: '端口引用的变量不存在' })
  })
}

function validateActions(node, errors) {
  const interfaces = new Map((node.interfaces ?? []).map(item => [item.name, item]))
  const variables = new Set((node.internalVariables ?? []).map(item => item.name))
  ;(node.actions ?? []).forEach((action, index) => {
    if (action.actionType === 'EMIT') {
      const target = interfaces.get(action.targetInterfaceName)
      if (!target) errors.push({ path: `actions[${index}].targetInterfaceName`, message: `EMIT动作${action.actionName}引用的输出接口${action.targetInterfaceName}不存在` })
      else if (target.direction !== 'OUT') errors.push({ path: `actions[${index}].targetInterfaceName`, message: `EMIT动作${action.actionName}引用的接口${action.targetInterfaceName}必须是OUT接口` })
      else if (!target.allowedSignals?.includes(action.signalName)) errors.push({ path: `actions[${index}].signalName`, message: `EMIT动作${action.actionName}的信号${action.signalName}不被接口${action.targetInterfaceName}允许` })
    }
    if (action.actionType === 'UPDATE') {
      if (!variables.has(action.internalVariableName)) errors.push({ path: `actions[${index}].internalVariableName`, message: `UPDATE动作${action.actionName}引用的内部变量${action.internalVariableName}不存在` })
      if (!action.valueExpression?.trim()) errors.push({ path: `actions[${index}].valueExpression`, message: `UPDATE动作${action.actionName}的valueExpression不能为空` })
    }
  })
}
function validateTriggers(node, errors) {
  const actionNames = new Set((node.actions ?? []).map(item => item.actionName))
  ;(node.interfaces ?? []).forEach((item, interfaceIndex) => (item.bindingTriggers ?? []).forEach((bindingTrigger, triggerIndex) => {
    const path = `interfaces[${interfaceIndex}].bindingTriggers[${triggerIndex}]`
    if (item.direction !== 'IN') errors.push({ path, message: `${item.direction}接口不能声明bindingTriggers` })
    if (!actionNames.has(bindingTrigger.action)) errors.push({ path: `${path}.action`, message: `触发器引用的动作${bindingTrigger.action}不存在` })
  }))
}

function validateDeviceConfiguration(node, model, errors) {
  const capability = (model?.capabilities ?? []).find(item => item.capabilityName === node.capability?.capabilityName)
  if (!capability) {
    errors.push({ path: 'capability', message: '设备能力不存在' })
    return
  }
  const definitions = new Map((capability.parameters ?? []).map(item => [item.parameterName, item.dataType]))
  Object.entries(node.capability?.capabilityParameters ?? {}).forEach(([name, value]) => {
    if (!sameParameterType(value, definitions.get(name))) errors.push({ path: `capability.capabilityParameters.${name}`, message: '能力参数类型不匹配' })
  })
}

function sameParameterType(value, dataType) {
  return (dataType === 'INTEGER' && Number.isInteger(value)) ||
    (dataType === 'DOUBLE' && typeof value === 'number') ||
    (dataType === 'BOOLEAN' && typeof value === 'boolean') ||
    (dataType === 'STRING' && typeof value === 'string') ||
    (dataType === 'JSON' && value !== null && !Array.isArray(value) && typeof value === 'object')
}