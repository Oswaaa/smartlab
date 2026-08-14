import { validateWorkflowExpression } from './workflowDesignerRules.js'

let configuredTemplates
const requiredTemplateKeys = ['START', 'END', 'BRANCH', 'AGGREGATE', 'DEV_NODE', 'SUBFLOW_NODE']
const workflowActionCapabilities = ['UPDATE', 'EMIT']

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

export function canCustomizeControlInterfaces(node) {
  return node?.nodeType === 'FUNC_NODE'
}

export function canEditControlItem(node, item) {
  return canCustomizeControlInterfaces(node) && !isSystemItem(item)
}

export function canEditControlTriggers(node, item) {
  if (!canCustomizeControlInterfaces(node) || !item) return false
  if (!isSystemItem(item)) return true
  return node?.functionType === 'AGGREGATE' && item.direction === 'OUT' && item.interfaceType === 'WORKFLOW'
}

export function orderedControlInterfaces(node) {
  const interfaces = node?.interfaces ?? []
  return [
    ...interfaces.filter(item => item.direction === 'OUT'),
    ...interfaces.filter(item => item.direction === 'IN'),
  ]
}

export function customTriggerActionNames(node) {
  return [...workflowActionCapabilities]
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
  restoreMatchedItems(restored.internalVariables, expected.internalVariables, item => item?.name)
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
  if (restored.nodeType === 'FUNC_NODE' && restored.functionType === 'AGGREGATE') {
    for (const item of restored.interfaces ?? []) {
      if (item.direction !== 'IN' || item.interfaceType !== 'WORKFLOW') continue
      for (const trigger of item.bindingTriggers ?? []) {
        if (!isAggregateCounterTrigger(trigger)) continue
        trigger._system = true
        trigger._systemKey ||= `aggregate.countInput.${item.name}`
      }
    }
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
    canonicalJson(trigger?.condition),
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
  for (const action of restored.actions ?? []) {
    if (typeof action === 'string') continue
    if (!action || typeof action !== 'object') continue
    const canonical = canonicalAction(action)
    if (action.actionName) legacyActions.set(action.actionName, canonical)
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
  restored.actions = [...workflowActionCapabilities]
  return restored
}

export function workflowUpdateVariableDataType(node, targetName) {
  return (node?.internalVariables ?? []).find(variable => variable.name === targetName)?.dataType ?? null
}

export function emptyWorkflowUpdateValue(dataType) {
  if (dataType === 'BOOLEAN') return false
  if (dataType === 'STRING') return ''
  return null
}

export function workflowTriggerConditions(condition) {
  if (condition?.logic === 'AND' && Array.isArray(condition.conditions)) return condition.conditions
  return condition && typeof condition === 'object' ? [condition] : []
}

function cloneWorkflowData(value) {
  if (Array.isArray(value)) return value.map(cloneWorkflowData)
  if (!value || typeof value !== 'object') return value
  return Object.fromEntries(Object.entries(value)
    .map(([key, item]) => [key, cloneWorkflowData(item)]))
}

export function addWorkflowTriggerCondition(condition, predicate) {
  const conditions = workflowTriggerConditions(condition).map(cloneWorkflowData)
  conditions.push(cloneWorkflowData(predicate))
  return { logic: 'AND', conditions }
}

export function removeWorkflowTriggerCondition(condition, index) {
  const conditions = workflowTriggerConditions(condition).map(cloneWorkflowData)
  if (conditions.length <= 1) throw new Error('触发器至少保留一个条件')
  conditions.splice(index, 1)
  return conditions.length === 1 ? conditions[0] : { logic: 'AND', conditions }
}

export function replaceWorkflowTriggerCondition(condition, index, predicate) {
  const conditions = workflowTriggerConditions(condition).map(cloneWorkflowData)
  if (index < 0 || index >= conditions.length) throw new Error('触发器条件不存在')
  conditions[index] = cloneWorkflowData(predicate)
  return conditions.length === 1 ? conditions[0] : { logic: 'AND', conditions }
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

export function createWorkflowInterfaceDefinition(node, direction, name) {
  if (!['IN', 'OUT'].includes(direction)) throw new Error('接口方向只允许IN或OUT')
  const bindingTriggers = node?.nodeType === 'FUNC_NODE' && node?.functionType === 'AGGREGATE' && direction === 'IN'
    ? [aggregateCounterTrigger(name)]
    : []
  return {
    name,
    direction,
    interfaceType: 'WORKFLOW',
    allowedSignals: ['ACTIVE'],
    bindingTriggers,
  }
}

export function defaultWorkflowInterfaceDirection(node) {
  if (node?.nodeType !== 'FUNC_NODE') return 'OUT'
  return ['END', 'AGGREGATE'].includes(node.functionType) ? 'IN' : 'OUT'
}

function aggregateCounterTrigger(interfaceName) {
  return {
    condition: { object: 'signalName', operator: '=', threshold: 'ACTIVE' },
    action: {
      actionName: 'UPDATE',
      payload: {
        updateType: 'INTERNAL_VARIABLE',
        targetName: 'aggregateCount',
        valueExpression: 'aggregateCount + 1',
      },
    },
    _system: true,
    _systemKey: `aggregate.countInput.${interfaceName}`,
  }
}

function isAggregateCounterTrigger(trigger) {
  const payload = trigger?.action?.payload
  return trigger?.condition?.object === 'signalName' && trigger?.condition?.operator === '=' &&
    trigger?.condition?.threshold === 'ACTIVE' && trigger?.action?.actionName === 'UPDATE' &&
    payload?.updateType === 'INTERNAL_VARIABLE' && payload?.targetName === 'aggregateCount' &&
    String(payload?.valueExpression || '').replace(/\s+/g, '') === 'aggregateCount+1'
}

export function changeWorkflowInterfaceDirection(node, interfaceName, direction, interfaceConnections = []) {
  if (!['IN', 'OUT'].includes(direction)) throw new Error('接口方向只允许IN或OUT')
  const target = (node?.interfaces ?? []).find(item => item.name === interfaceName)
  if (!target) throw new Error(`接口${interfaceName}不存在`)
  if (isSystemItem(target)) throw new Error(`系统接口${interfaceName}不可修改方向`)
  if (target.direction === direction) {
    return {
      node,
      interfaceConnections,
      removedConnectionCount: 0,
      removedTriggerCount: 0,
    }
  }

  const connections = interfaceConnections ?? []
  const nextConnections = connections.filter(connection =>
    !(connection.source?.nodeName === node.name && connection.source?.interfaceName === interfaceName) &&
    !(connection.target?.nodeName === node.name && connection.target?.interfaceName === interfaceName))
  const currentTriggers = target.bindingTriggers ?? []
  const removedTriggerCount = direction === 'IN'
    ? currentTriggers.filter(trigger => trigger?.action?.actionName === 'EMIT').length
    : currentTriggers.filter(isAggregateCounterTrigger).length
  let nextTriggers = direction === 'IN'
    ? currentTriggers.filter(trigger => trigger?.action?.actionName !== 'EMIT')
    : currentTriggers.filter(trigger => !isAggregateCounterTrigger(trigger))

  if (direction === 'IN' && node?.nodeType === 'FUNC_NODE' && node?.functionType === 'AGGREGATE') {
    nextTriggers = [
      aggregateCounterTrigger(interfaceName),
      ...nextTriggers.filter(trigger => !isAggregateCounterTrigger(trigger)),
    ]
  }

  const nextInterface = { ...target, direction, bindingTriggers: nextTriggers }
  return {
    node: {
      ...node,
      interfaces: (node.interfaces ?? []).map(item => item.name === interfaceName ? nextInterface : item),
    },
    interfaceConnections: nextConnections,
    removedConnectionCount: connections.length - nextConnections.length,
    removedTriggerCount,
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
  validateVariables(node, errors, context.deviceAttributes || [])
  validatePorts(node, errors)
  validateActions(node, errors)
  validateTriggers(node, errors)
  validateControlContractOwnership(node, errors)
  validateAggregateInputs(node, errors)
  if (node.nodeType === 'DEV_NODE') validateDeviceConfiguration(node, context.deviceModel, errors)
  if (node.nodeType === 'FUNC_NODE' && ['BRANCH', 'AGGREGATE'].includes(node.functionType) && String(node.expression || '').trim()) {
    const expressionErrors = validateWorkflowExpression(node.expression, node.internalVariables || [], { assignment: true, temporal: true })
    expressionErrors.forEach(message => errors.push({ path: 'expression', message }))
  }
  return errors
}

function validateControlContractOwnership(node, errors) {
  if (canCustomizeControlInterfaces(node)) return
  ;(node.interfaces ?? []).forEach((item, index) => {
    if (!isSystemItem(item)) errors.push({
      path: `interfaces[${index}]`,
      message: '非功能节点不能声明自定义控制接口',
    })
    ;(item.bindingTriggers ?? []).forEach((trigger, triggerIndex) => {
      if (!isSystemItem(trigger)) errors.push({
        path: `interfaces[${index}].bindingTriggers[${triggerIndex}]`,
        message: '非功能节点不能声明自定义触发器',
      })
    })
  })
}

function uniqueErrors(errors, items = [], field, path) {
  const seen = new Set()
  items.forEach((item, index) => {
    if (seen.has(item[field])) errors.push({ path: `${path}[${index}].${field}`, message: `${field}必须唯一` })
    seen.add(item[field])
  })
}

function validateVariables(node, errors, deviceAttributes = []) {
  const allowed = new Set(['INTEGER', 'DOUBLE', 'BOOLEAN', 'STRING'])
  const attributes = new Map(deviceAttributes.map(item => [item.attributeName, item]))
  ;(node.internalVariables ?? []).forEach((variable, index) => {
    if (!allowed.has(variable.dataType)) errors.push({ path: `internalVariables[${index}].dataType`, message: '变量类型无效' })
    if (Object.hasOwn(variable, 'initialValue') && !matchesInternalValue(variable.initialValue, variable.dataType)) {
      errors.push({ path: `internalVariables[${index}].initialValue`, message: '变量初始值与数据类型不一致' })
    }
    if (variable.attributesMapping) {
      const attribute = attributes.get(variable.attributesMapping)
      if (!attribute) {
        errors.push({ path: `internalVariables[${index}].attributesMapping`, message: `设备属性${variable.attributesMapping}不存在` })
      } else if (attribute.dataType && attribute.dataType !== variable.dataType) {
        errors.push({ path: `internalVariables[${index}].dataType`, message: `变量类型与设备属性${variable.attributesMapping}不一致` })
      }
    }
  })
}

function validateAggregateInputs(node, errors) {
  if (node?.nodeType !== 'FUNC_NODE' || node?.functionType !== 'AGGREGATE') return
  ;(node.interfaces ?? []).forEach((item, index) => {
    if (item.direction !== 'IN' || item.interfaceType !== 'WORKFLOW') return
    if (!(item.bindingTriggers ?? []).some(isAggregateCounterTrigger)) {
      errors.push({
        path: `interfaces[${index}].bindingTriggers`,
        message: '聚合输入接口必须包含计数触发器',
      })
    }
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
  const actionNames = new Set(workflowActionCapabilities)
  const interfaces = new Map((node.interfaces ?? []).map(item => [item.name, item]))
  const variables = new Map((node.internalVariables ?? []).map(item => [item.name, item]))
  const lifecycleStates = new Set(node.lifecycle?.states ?? [])
  ;(node.interfaces ?? []).forEach((item, interfaceIndex) => (item.bindingTriggers ?? []).forEach((trigger, triggerIndex) => {
    const path = `interfaces[${interfaceIndex}].bindingTriggers[${triggerIndex}]`
    validateTriggerCondition(trigger.condition, `${path}.condition`, errors)
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
    if (actionName === 'EMIT') validateEmitPayload(path, payload, interfaces, item, errors)
    if (actionName === 'UPDATE') validateUpdatePayload(path, payload, variables, lifecycleStates, errors)
  }))
}

function validateTriggerCondition(condition, path, errors) {
  const grouped = condition?.logic !== undefined || condition?.conditions !== undefined
  if (!grouped) return
  if (condition?.logic !== 'AND') {
    errors.push({ path: `${path}.logic`, message: '条件组合当前只支持AND' })
    return
  }
  if (!Array.isArray(condition.conditions) || condition.conditions.length < 2) {
    errors.push({ path: `${path}.conditions`, message: 'AND条件组至少需要两个条件' })
    return
  }
  condition.conditions.forEach((predicate, index) => {
    if (predicate?.logic !== undefined || predicate?.conditions !== undefined) {
      errors.push({ path: `${path}.conditions[${index}]`, message: '条件组不允许嵌套' })
    }
  })
}

function validateEmitPayload(path, payload, interfaces, hostInterface, errors) {
  const target = interfaces.get(payload.targetInterfaceName)
  if (payload.targetInterfaceName !== hostInterface.name) errors.push({ path: `${path}.action.payload.targetInterfaceName`, message: `EMIT目标必须是触发器所在接口${hostInterface.name}` })
  else if (!target) errors.push({ path: `${path}.action.payload.targetInterfaceName`, message: `EMIT目标接口${payload.targetInterfaceName || ''}不存在` })
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
