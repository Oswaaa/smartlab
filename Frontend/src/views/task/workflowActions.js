const catalogIndex = catalog => new Map((catalog || []).map(item => [item.actionName, item]))

export const actionsAllowedForNode = (catalog, nodeType) =>
  (catalog || []).filter(item => Array.isArray(item.allowedNodeTypes) && item.allowedNodeTypes.includes(nodeType))

export const createWorkflowAction = (catalog, actionName) => {
  const item = catalogIndex(catalog).get(actionName)
  if (!item) throw new Error(`动作目录未声明 ${actionName}`)
  if (actionName === 'WAIT') return { actionName, payload: { durationMs: 0 } }
  if (actionName === 'ASSIGN') return { actionName, payload: { target: '', source: { kind: 'LITERAL', value: null } } }
  if (actionName === 'CALCULATE') return { actionName, payload: { target: '', operator: '', operands: [] } }
  if (actionName === 'EMIT_SIGNAL') return { actionName, payload: { interfaceType: 'WORKFLOW', signalName: '' } }
  throw new Error(`动作 ${actionName} 没有前端参数编辑器`)
}

export const validateAndOrderActions = (actions, nodeType, catalog) => {
  const allowed = new Set(actionsAllowedForNode(catalog, nodeType).map(item => item.actionName))
  const copy = (actions || []).map(item => structuredClone(item))
  const seen = new Set()
  copy.forEach((action, index) => {
    if (!allowed.has(action.actionName)) throw new Error(`${nodeType} 不允许动作 ${action.actionName}`)
    if (seen.has(action.actionName) && ['WAIT', 'EMIT_SIGNAL'].includes(action.actionName))
      throw new Error(`${action.actionName} 只能配置一次`)
    seen.add(action.actionName)
    if (action.actionName === 'WAIT' && index !== 0) throw new Error('WAIT 必须是第一个动作')
    if (action.actionName === 'EMIT_SIGNAL' && index !== copy.length - 1) throw new Error('EMIT_SIGNAL 必须是最后一个动作')
  })
  if (nodeType === 'DEVICE_CAPABILITY_NODE' && !seen.has('EMIT_SIGNAL'))
    throw new Error('设备能力节点必须配置 EMIT_SIGNAL')
  return copy
}

export const defaultActionsForNode = (nodeType, catalog, workflowControlSignals = []) => {
  if (nodeType !== 'DEVICE_CAPABILITY_NODE') return []
  const action = createWorkflowAction(catalog, 'EMIT_SIGNAL')
  action.payload.signalName = workflowControlSignals[0] || ''
  return [action]
}
