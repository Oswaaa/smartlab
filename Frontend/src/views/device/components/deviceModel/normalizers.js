export const asArray = value => Array.isArray(value) ? value : []
export const stringId = value => value == null ? '' : String(value)
export const formatTime = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
export const formatJson = value => JSON.stringify(value || {}, null, 2)
export const valueKindLabel = value => value === 'DISCRETE' ? '离散值' : '连续值'
export const directionLabel = value => value === 'IN' ? '输入' : '输出'
export const operatorLabel = value => ({ '>': '大于', '<': '小于', '>=': '大于等于', '<=': '小于等于', '=': '等于', '!=': '不等于', BETWEEN: '区间内', IN: '属于' }[value] || value || '-')
export const eventTypeLabel = value => value === 'OP' ? '功能事件' : '指令事件'
export const describeAction = action => `${action?.actionName || '-'}${action?.payload?.signalName ? ':' + action.payload.signalName : ''}`
export const displayAttributeName = (attributeName, attributes) => asArray(attributes).find(item => item.attributeName === attributeName)?.displayName || attributeName || '-'
export function normalizeEventsToFlatList(events) {
  const result = []
  asArray(events?.cmdEvents).forEach(item => result.push({ ...item, eventType: 'CMD' }))
  asArray(events?.opEvents).forEach(item => result.push({ ...item, eventType: 'OP' }))
  return result
}
export function functionMappingGroups(capabilities) {
  return asArray(capabilities).filter(item => item.adapterCommandName).map(item => ({
    key: item.capabilityName,
    capabilityDisplayName: item.displayName || item.capabilityName,
    adapterCommandName: item.adapterCommandName,
    parameters: asArray(item.parameterMapping).map(mapping => ({
      key: `${item.capabilityName}:${mapping.commandParamName}`,
      commandParamName: mapping.commandParamName,
      isFixedValue: mapping.isFixedValue === true,
      fixedValue: mapping.fixedValue,
      capabilityParamDisplayName: asArray(item.parameters).find(param => param.name === mapping.capabilityParamName)?.displayName || mapping.capabilityParamName
    }))
  }))
}
export function normalizeModelBundle(value) {
  return { capabilityModel: value?.capabilityModel || {}, stateMachineModel: value?.stateMachineModel || {} }
}
export function normalizeModel(raw, categories = []) {
  const modelId = String(raw?.modelId || raw?.id || '')
  const categoryId = raw?.categoryId == null ? '' : String(raw.categoryId)
  return {
    ...raw,
    modelId,
    categoryId,
    categoryName: raw?.categoryName || asArray(categories).find(item => String(item.id) === categoryId)?.categoryName || '',
    attributes: asArray(raw?.attributes),
    capabilities: asArray(raw?.capabilities),
    adapterContract: raw?.adapterContract || {},
    ports: asArray(raw?.ports),
    intrinsicConstraints: asArray(raw?.intrinsicConstraints || raw?.intrinsicConstraint),
    stateMachineInterfaces: asArray(raw?.stateMachineInterfaces),
    opState: raw?.opState || { regions: [] },
    cmdState: raw?.cmdState || { initialStateName: 'IDLE', states: [] },
    stateTransitions: asArray(raw?.stateTransitions),
    componentsBom: asArray(raw?.componentsBom)
  }
}
