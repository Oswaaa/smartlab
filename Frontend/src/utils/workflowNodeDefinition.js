let configuredTemplates
const requiredTemplateKeys = ['START', 'END', 'BRANCH', 'AGGREGATE', 'DEV_NODE', 'SUBFLOW_NODE']

export function configureWorkflowNodeTemplates(templates) {
  const missingKeys = requiredTemplateKeys.filter(key => !templates?.[key] || typeof templates[key] !== 'object')
  if (missingKeys.length) throw new Error(`工作流系统模板不完整:${missingKeys.join(',')}`)
  const snapshot = Object.fromEntries(Object.entries(templates)
    .map(([key, template]) => [key, normalizeWorkflowNodeDefinition(template)]))
  configuredTemplates = snapshot
}

export function workflowNodeTemplates() {
  return configuredTemplates ? structuredClone(configuredTemplates) : {}
}

export function resetWorkflowNodeTemplatesForTest() {
  configuredTemplates = undefined
}

function templateCopy(key) {
  const template = configuredTemplates?.[key]
  if (!template) throw new Error(`工作流系统模板尚未加载:${key}`)
  return structuredClone(template)
}
export function isSystemItem(item) {
  return item?._system === true
}

export function customTriggerActionNames(node) {
  return [...new Set((node?.actions ?? []).filter(action => action === 'UPDATE' || action === 'EMIT'))]
}

export function rehydrateWorkflowNodes(nodes = []) {
  return nodes.map(rehydrateWorkflowNode)
}

export function rehydrateWorkflowNode(node) {
  const restored = normalizeWorkflowNodeDefinition(node)
  const expected = templateCopy(templateKeyForNode(restored))

  restoreSystemIdentity(restored.lifecycle, expected.lifecycle)
  restoreMatchedItems(
    restored.lifecycle?.transitions,
    expected.lifecycle?.transitions,
    item => `${item?.fromStateName ?? ''}->${item?.toStateName ?? ''}`,
  )
  restoreMatchedItems(restored.interfaces, expected.interfaces, item => item?.name)
  for (const expectedInterface of expected.interfaces ?? []) {
    const actualInterface = (restored.interfaces ?? [])
      .find(item => item?.name === expectedInterface?.name)
    restoreMatchedItems(
      actualInterface?.bindingTriggers,
      expectedInterface?.bindingTriggers,
      triggerBusinessIdentity,
    )
  }
  return restored
}

function templateKeyForNode(node) {
  if (node?.nodeType === 'DEV_NODE') return 'DEV_NODE'
  if (node?.nodeType === 'SUBFLOW_NODE') return 'SUBFLOW_NODE'
  if (node?.nodeType === 'FUNC_NODE') {
    const key = { START: 'START', END: 'END', BRANCH: 'BRANCH', AGGREGATE: 'AGGREGATE' }[node.functionType]
    if (key) return key
  }
  throw new Error(`无法恢复节点系统模板:${node?.name || node?.nodeType || 'unknown'}`)
}

function restoreMatchedItems(actualItems = [], expectedItems = [], identity) {
  const actualByIdentity = new Map((actualItems ?? []).map(item => [identity(item), item]))
  for (const expected of expectedItems ?? []) {
    restoreSystemIdentity(actualByIdentity.get(identity(expected)), expected)
  }
}

function triggerBusinessIdentity(trigger) {
  return JSON.stringify([
    trigger?.condition?.object,
    trigger?.condition?.operator,
    trigger?.condition?.threshold,
    canonicalJson(trigger?.action),
  ])
}

function canonicalJson(value) {
  if (Array.isArray(value)) return value.map(canonicalJson)
  if (!value || typeof value !== 'object') return value
  return Object.fromEntries(Object.keys(value).sort().map(key => [key, canonicalJson(value[key])]))
}

export function normalizeWorkflowNodeDefinition(node = {}) {
  const restored = structuredClone(node)
  const legacyActions = new Map()
  const actionNames = []
  for (const action of restored.actions ?? []) {
    if (typeof action === 'string') {
      if (action === 'UPDATE' || action === 'EMIT') actionNames.push(action)
      continue
    }
    if (!action || typeof action !== 'object') continue
    const canonical = canonicalAction(action)
    if (action.actionName) legacyActions.set(action.actionName, canonical)
    if (canonical.actionName === 'UPDATE' || canonical.actionName === 'EMIT') actionNames.push(canonical.actionName)
  }
  for (const item of restored.interfaces ?? []) {
    item.bindingTriggers = (item.bindingTriggers ?? []).map(trigger => {
      const normalized = structuredClone(trigger)
      if (typeof normalized.action === 'string') {
        normalized.action = structuredClone(legacyActions.get(normalized.action) ?? {
          actionName: normalized.action,
          payload: {},
        })
      } else {
        normalized.action = canonicalAction(normalized.action)
      }
      return normalized
    })
  }
  restored.actions = [...new Set(actionNames)]
  return restored
}

function canonicalAction(source = {}) {
  if (!source || typeof source !== 'object') return { actionName: '', payload: {} }
  const actionName = source.actionType || source.actionName || ''
  if (source.payload && typeof source.payload === 'object' && !Array.isArray(source.payload)) {
    return { actionName, payload: structuredClone(source.payload) }
  }
  if (actionName === 'EMIT') {
    return { actionName, payload: {
      targetInterfaceName: source.targetInterfaceName ?? '',
      signalName: source.signalName ?? '',
    } }
  }
  const payload = {
    updateType: source.updateType || 'INTERNAL_VARIABLE',
    targetName: source.targetName || source.internalVariableName || '',
  }
  if (Object.hasOwn(source, 'value')) payload.value = structuredClone(source.value)
  if (Object.hasOwn(source, 'valueExpression')) payload.valueExpression = source.valueExpression
  return { actionName, payload }
}

function restoreSystemIdentity(actual, expected) {
  if (!actual || !expected || expected._system !== true || !expected._systemKey) return
  actual._system = true
  actual._systemKey = expected._systemKey
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
  const templateKey = { START: 'START', END: 'END', BRANCH: 'BRANCH', AGGREGATE: 'AGGREGATE' }[functionType]
  if (!templateKey) throw new Error(`工作流系统模板尚未加载:FUNC_NODE+${functionType}`)
  return {
    name,
    nodeType: 'FUNC_NODE',
    functionType,
    internalVariables: [],
    ...templateCopy(templateKey),
    ports: [],
    ...(functionType === 'BRANCH' ? { expression: '' } : {})
  }
}

export function createDeviceNode(model, name) {
  return {
    name,
    nodeType: 'DEV_NODE',
    deviceModelId: model.id,
    capability: model.capabilities?.[0]
      ? { capabilityName: model.capabilities[0].capabilityName, capabilityParameters: {} }
      : null,
    _capabilityParameterTypes: Object.fromEntries((model.capabilities?.[0]?.parameters ?? []).map(parameter => [parameter.name, parameter.dataType])),
    internalVariables: [],
    ...templateCopy('DEV_NODE'),
    ports: []
  }
}

export function createSubflowNode(workflow, name) {
  return {
    name,
    nodeType: 'SUBFLOW_NODE',
    subFlowModelId: workflow.id,
    internalVariables: [],
    ...templateCopy('SUBFLOW_NODE'),
    ports: []
  }
}

export function replaceCapability(node, capability, previousCapability) {
  const nextTypes = Object.fromEntries((capability.parameters ?? []).map(parameter => [parameter.name, parameter.dataType]))
  return {
    ...node,
    capability: { capabilityName: capability.capabilityName, capabilityParameters: {} },
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
  const used = (node.interfaces ?? []).some(item => (item.bindingTriggers ?? [])
    .some(trigger => trigger.action?.actionName === actionName))
  if (used) throw new Error(`动作能力${actionName}仍被触发器引用`)
  return { ...node, actions: (node.actions ?? []).filter(item => item !== actionName) }
}

export function removeInterface(node, interfaceName, interfaceConnections) {
  const target = (node.interfaces ?? []).find(item => item.name === interfaceName)
  if (isSystemItem(target)) throw new Error(`系统接口${interfaceName}不可删除`)
  return {
    node: { ...node, interfaces: (node.interfaces ?? []).filter(item => item.name !== interfaceName) },
    interfaceConnections: (interfaceConnections ?? []).filter(connection =>
      !(connection.source?.nodeName === node.name && connection.source?.interfaceName === interfaceName) &&
      !(connection.target?.nodeName === node.name && connection.target?.interfaceName === interfaceName)),
  }
}

export function validateNodeDefinition(node, context = {}) {
  const errors = []
  uniqueErrors(errors, node.internalVariables, 'name', 'internalVariables')
  uniqueErrors(errors, node.ports, 'name', 'ports')
  uniqueErrors(errors, node.interfaces, 'name', 'interfaces')
  uniqueActionErrors(errors, node.actions)
  validateVariables(node, errors)
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

function validatePorts(node, errors) {
  const variables = new Map((node.internalVariables ?? []).map(item => [item.name, item]))
  ;(node.ports ?? []).forEach((port, index) => {
    if (!variables.has(port.internalVariableName)) errors.push({ path: `ports[${index}].internalVariableName`, message: '端口引用的变量不存在' })
  })
}

function validateActions() {}
function validateTriggers(node, errors) {
  validateInlineTriggers(node, errors)
}

function validateInlineTriggers(node, errors) {
  const actionNames = new Set(node.actions ?? [])
  const interfaces = new Map((node.interfaces ?? []).map(item => [item.name, item]))
  const variables = new Map((node.internalVariables ?? []).map(item => [item.name, item]))
  const lifecycleStates = new Set(node.lifecycle?.states ?? [])
  ;(node.interfaces ?? []).forEach((item, interfaceIndex) => (item.bindingTriggers ?? []).forEach((trigger, triggerIndex) => {
    const path = `interfaces[${interfaceIndex}].bindingTriggers[${triggerIndex}]`
    const action = trigger.action
    const actionName = action?.actionName
    const payload = action?.payload
    if (!action || typeof action !== 'object' || !payload || typeof payload !== 'object') {
      errors.push({ path: `${path}.action`, message: '触发器action必须是内联对象' })
      return
    }
    if (!actionNames.has(actionName)) {
      errors.push({ path: `${path}.action.actionName`, message: `动作能力${actionName || ''}未在actions中声明` })
      return
    }
    if (actionName === 'EMIT') validateEmitPayload(path, payload, interfaces, errors)
    if (actionName === 'UPDATE') validateUpdatePayload(path, payload, variables, lifecycleStates, errors)
  }))
}

function validateEmitPayload(path, payload, interfaces, errors) {
  const target = interfaces.get(payload.targetInterfaceName)
  if (!target) errors.push({ path: `${path}.action.payload.targetInterfaceName`, message: `EMIT目标接口${payload.targetInterfaceName || ''}不存在` })
  else if (target.direction !== 'OUT') errors.push({ path: `${path}.action.payload.targetInterfaceName`, message: `EMIT目标接口${payload.targetInterfaceName}必须是OUT接口` })
  else if (!target.allowedSignals?.includes(payload.signalName)) errors.push({ path: `${path}.action.payload.signalName`, message: `信号${payload.signalName || ''}不在接口allowedSignals中` })
}

function validateUpdatePayload(path, payload, variables, lifecycleStates, errors) {
  if (payload.updateType === 'NODE_LIFECYCLE') {
    if (!lifecycleStates.has(payload.targetName)) {
      errors.push({ path: `${path}.action.payload.targetName`, message: `生命周期状态${payload.targetName || ''}不存在` })
    }
    if (Object.hasOwn(payload, 'value') || Object.hasOwn(payload, 'valueExpression')) {
      errors.push({ path: `${path}.action.payload`, message: '生命周期UPDATE不能包含value或valueExpression' })
    }
    return
  }
  if (payload.updateType !== 'INTERNAL_VARIABLE') {
    errors.push({ path: `${path}.action.payload.updateType`, message: 'updateType只允许INTERNAL_VARIABLE或NODE_LIFECYCLE' })
    return
  }
  const variable = variables.get(payload.targetName)
  if (!variable) errors.push({ path: `${path}.action.payload.targetName`, message: `内部变量${payload.targetName || ''}不存在` })
  const hasValue = Object.hasOwn(payload, 'value')
  const hasExpression = typeof payload.valueExpression === 'string' && payload.valueExpression.trim() !== ''
  if (hasValue === hasExpression) {
    errors.push({ path: `${path}.action.payload`, message: 'INTERNAL_VARIABLE UPDATE必须且只能设置value或valueExpression之一' })
  } else if (hasValue && variable && !matchesInternalValue(payload.value, variable.dataType)) {
    errors.push({ path: `${path}.action.payload.value`, message: `常量类型与内部变量${payload.targetName}不一致` })
  }
}

function matchesInternalValue(value, dataType) {
  if (value === null) return false
  return (dataType === 'INTEGER' && Number.isInteger(value)) ||
    (dataType === 'DOUBLE' && typeof value === 'number' && Number.isFinite(value)) ||
    (dataType === 'BOOLEAN' && typeof value === 'boolean') ||
    (dataType === 'STRING' && typeof value === 'string') ||
    (dataType === 'JSON' && typeof value === 'object')
}

function validateDeviceConfiguration(node, model, errors) {
  const capability = (model?.capabilities ?? []).find(item => item.capabilityName === node.capability?.capabilityName)
  if (!capability) {
    errors.push({ path: 'capability', message: '设备能力不存在' })
    return
  }
  const definitions = new Map((capability.parameters ?? []).map(item => [item.name, item.dataType]))
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

function uniqueActionErrors(errors, actions = []) {
  const seen = new Set()
  actions.forEach((action, index) => {
    if (action !== 'UPDATE' && action !== 'EMIT') {
      errors.push({ path: `actions[${index}]`, message: '动作能力只允许UPDATE或EMIT' })
    } else if (seen.has(action)) {
      errors.push({ path: `actions[${index}]`, message: '动作能力必须唯一' })
    }
    seen.add(action)
  })
}
