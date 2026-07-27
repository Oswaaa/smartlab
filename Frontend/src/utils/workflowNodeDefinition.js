const system = (systemKey, value) => ({ ...value, _system: true, _systemKey: systemKey })

const workflowInterface = (name, direction, bindingTriggers = []) => ({ name, direction, bindingTriggers })

const trigger = (systemKey, object, operator, threshold, actionName) => system(systemKey, {
  actionName,
  condition: { object, operator, threshold }
})

const lockedLifecycle = () => system('lifecycle', {
  initialState: 'IDLE',
  states: [
    system('lifecycle.idle', { name: 'IDLE' }),
    system('lifecycle.running', { name: 'RUNNING' }),
    system('lifecycle.completed', { name: 'COMPLETED' })
  ]
})

const systemWorkflowInterface = (systemKey, name, direction, bindingTriggers = []) =>
  system(systemKey, workflowInterface(name, direction, bindingTriggers))

const emitAction = (systemKey, actionName, targetInterfaceName, signalName) => system(systemKey, {
  actionName,
  actionType: 'EMIT',
  targetInterfaceName,
  signalName
})

export function isSystemItem(item) {
  return item?._system === true
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
        trigger('branch.true', 'expression', 'EQUALS', true, 'emitTrue'),
        trigger('branch.false', 'expression', 'EQUALS', false, 'emitFalse')
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
      trigger('aggregate.active', 'inputSignalName', 'EQUALS', 'ACTIVE', 'emitActive')
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
        trigger('device.workflowStart', 'inputSignalName', 'EQUALS', 'ACTIVE', 'startDevice')
      ]),
      systemWorkflowInterface('device.stateOut', 'Interface_state_out', 'OUT'),
      systemWorkflowInterface('device.stateIn', 'Interface_state_in', 'IN', [
        trigger('device.stateCompleted', 'inputPayload.stateName', 'EQUALS', 'COMPLETED', 'completeNode')
      ]),
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

export function replaceCapability(node, capability) {
  const oldValues = node.capability?.capabilityParameters ?? {}
  const oldTypes = node._capabilityParameterTypes ?? {}
  const nextTypes = Object.fromEntries((capability.parameters ?? []).map(parameter => [parameter.parameterName, parameter.dataType]))
  const capabilityParameters = Object.fromEntries(Object.entries(nextTypes)
    .filter(([name, type]) => oldTypes[name] === type && Object.hasOwn(oldValues, name))
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
      connection.portName !== portName && connection.sourcePortName !== portName && connection.targetPortName !== portName)
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
  const allowed = new Set(['INTEGER', 'DOUBLE', 'BOOLEAN', 'STRING'])
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
  if (!actual || !isSystemItem(actual) || !sameStructure(actual, expected)) {
    errors.push({ path, message: `系统项${expected._systemKey}缺失或已篡改` })
  }
}

function sameStructure(actual, expected) {
  return JSON.stringify(actual) === JSON.stringify(expected)
}

function validatePorts(node, errors) {
  const variables = new Map((node.internalVariables ?? []).map(item => [item.name, item]))
  ;(node.ports ?? []).forEach((port, index) => {
    if (!variables.has(port.internalVariableName)) errors.push({ path: `ports[${index}].internalVariableName`, message: '端口引用的变量不存在' })
  })
}

function validateActions(node, errors) {
  const interfaces = new Set((node.interfaces ?? []).map(item => item.name))
  ;(node.actions ?? []).forEach((action, index) => {
    if ((action.actionType === 'EMIT' || action.actionType === 'UPDATE') && !interfaces.has(action.targetInterfaceName)) {
      errors.push({ path: `actions[${index}].targetInterfaceName`, message: `${action.actionType}目标接口不存在` })
    }
  })
}

function validateTriggers(node, errors) {
  const actionNames = new Set((node.actions ?? []).map(item => item.actionName))
  ;(node.interfaces ?? []).forEach((item, interfaceIndex) => (item.bindingTriggers ?? []).forEach((bindingTrigger, triggerIndex) => {
    if (!actionNames.has(bindingTrigger.actionName)) {
      errors.push({ path: `interfaces[${interfaceIndex}].bindingTriggers[${triggerIndex}].actionName`, message: '触发器引用的动作不存在' })
    }
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
    (dataType === 'STRING' && typeof value === 'string')
}