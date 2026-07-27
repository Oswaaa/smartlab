const system = (systemKey, value) => ({ ...value, _system: true, _systemKey: systemKey })

const workflowInterface = (name, direction, bindingTriggers = []) => ({
  name,
  direction,
  bindingTriggers
})

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

const systemWorkflowInterface = (systemKey, name, direction, bindingTriggers) =>
  system(systemKey, workflowInterface(name, direction, bindingTriggers))

const linearInterfaces = () => [
  systemWorkflowInterface('linear.workflowIn', 'Interface_workflow_in', 'IN'),
  systemWorkflowInterface('linear.workflowOut', 'Interface_workflow_out', 'OUT')
]

export function isSystemItem(item) {
  return item?._system === true
}

export function createFunctionNode(functionType, name) {
  if (functionType === 'BRANCH') {
    const emitTrue = system('branch.emitTrue', {
      actionName: 'emitTrue',
      actionType: 'EMIT',
      targetInterfaceName: 'Interface_true_out',
      signalName: 'ACTIVE'
    })
    const emitFalse = system('branch.emitFalse', {
      actionName: 'emitFalse',
      actionType: 'EMIT',
      targetInterfaceName: 'Interface_false_out',
      signalName: 'ACTIVE'
    })
    return {
      name,
      nodeType: 'FUNC_NODE',
      functionType,
      expression: '',
      internalVariables: [],
      lifecycle: lockedLifecycle(),
      interfaces: [
        systemWorkflowInterface('branch.workflowIn', 'Interface_workflow_in', 'IN', [
          trigger('branch.true', 'expression', 'EQUALS', true, 'emitTrue'),
          trigger('branch.false', 'expression', 'EQUALS', false, 'emitFalse')
        ]),
        systemWorkflowInterface('branch.trueOut', 'Interface_true_out', 'OUT'),
        systemWorkflowInterface('branch.falseOut', 'Interface_false_out', 'OUT')
      ],
      ports: [],
      actions: [emitTrue, emitFalse]
    }
  }
  return createLinearFunctionNode(functionType, name)
}

function createLinearFunctionNode(functionType, name) {
  const node = {
    name,
    nodeType: 'FUNC_NODE',
    functionType,
    internalVariables: [],
    lifecycle: lockedLifecycle(),
    interfaces: linearInterfaces(),
    ports: [],
    actions: []
  }
  if (functionType === 'START') {
    node.actions = [system('start.emitActive', {
      actionName: 'emitActive', actionType: 'EMIT', targetInterfaceName: 'Interface_workflow_out', signalName: 'ACTIVE'
    })]
  }
  return node
}

export function createDeviceNode(model, name) {
  return {
    name,
    nodeType: 'DEV_NODE',
    deviceModelId: model.id,
    capability: model.capabilities?.[0]
      ? { capabilityName: model.capabilities[0].capabilityName, capabilityParameters: {} }
      : null,
    internalVariables: [],
    lifecycle: lockedLifecycle(),
    interfaces: [
      systemWorkflowInterface('device.workflowIn', 'Interface_workflow_in', 'IN'),
      systemWorkflowInterface('device.stateOut', 'Interface_state_out', 'OUT'),
      systemWorkflowInterface('device.stateIn', 'Interface_state_in', 'IN'),
      systemWorkflowInterface('device.workflowOut', 'Interface_workflow_out', 'OUT')
    ],
    ports: [],
    actions: [
      system('device.startDevice', { actionName: 'startDevice', actionType: 'DEVICE_CALL' }),
      system('device.completeNode', {
        actionName: 'completeNode', actionType: 'EMIT', targetInterfaceName: 'Interface_workflow_out', signalName: 'ACTIVE'
      })
    ]
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
  const current = node.capability?.capabilityParameters ?? {}
  const parameters = Object.fromEntries((capability.parameters ?? [])
    .filter(parameter => sameParameterType(current[parameter.parameterName], parameter.dataType))
    .map(parameter => [parameter.parameterName, current[parameter.parameterName]]))
  return { ...node, capability: { capabilityName: capability.capabilityName, capabilityParameters: parameters } }
}

function sameParameterType(value, dataType) {
  if (value === undefined || value === null) return false
  return (dataType === 'INTEGER' && Number.isInteger(value)) ||
    (dataType === 'DOUBLE' && typeof value === 'number') ||
    (dataType === 'BOOLEAN' && typeof value === 'boolean') ||
    (dataType === 'STRING' && typeof value === 'string')
}

export function removeVariable(node, variableName, portConnections) {
  const port = (node.ports ?? []).find(item => item.internalVariableName === variableName)
  if (port) throw new Error(`变量${variableName}仍被端口${port.name}引用`)
  return { ...node, internalVariables: (node.internalVariables ?? []).filter(item => item.name !== variableName) }
}

export function removePort(node, portName, portConnections) {
  if ((node.ports ?? []).find(item => item.name === portName && isSystemItem(item))) {
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
    if (!allowed.has(variable.dataType)) {
      errors.push({ path: `internalVariables[${index}].dataType`, message: '变量类型无效' })
    }
  })
}
function validateSystemSkeleton(node, errors) {
  const collections = ['lifecycle', 'interfaces', 'actions']
  collections.forEach(collection => {
    const items = collection === 'lifecycle' ? [node.lifecycle] : (node[collection] ?? [])
    items.filter(isSystemItem).forEach(item => {
      if (!item._systemKey) errors.push({ path: collection, message: '系统项缺少_systemKey' })
    })
  })
  if (node.functionType === 'BRANCH') {
    for (const name of ['Interface_true_out', 'Interface_false_out']) {
      if (!(node.interfaces ?? []).some(item => item.name === name)) errors.push({ path: 'interfaces', message: `BRANCH缺少${name}` })
    }
    for (const systemKey of ['branch.emitTrue', 'branch.emitFalse']) {
      if (!(node.actions ?? []).some(item => item._systemKey === systemKey)) {
        errors.push({ path: 'actions', message: `BRANCH缺少${systemKey}系统动作` })
      }
    }
  }  if (node.functionType === 'START' && !(node.actions ?? []).some(item => item._systemKey === 'start.emitActive')) {
    errors.push({ path: 'actions', message: 'START缺少emitActive系统动作' })
  }
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
