export function workflowLibraryGroups(workflows = []) {
  return [
    { key: 'active', label: '已启用流程', children: workflows.filter(item => item.status === 'ACTIVE') },
    { key: 'draft', label: '草稿流程', children: workflows.filter(item => item.status !== 'ACTIVE') },
  ]
}

export function clearWorkflowCanvas(workflow) {
  return {
    ...workflow,
    nodesDef: [],
    interfaceConnections: [],
    portConnections: [],
  }
}

export function protocolSignalCandidates(protocol = {}, interfaceItem = {}, systemItem = false) {
  if (interfaceItem.interfaceType === 'STATE') {
    return interfaceItem.direction === 'OUT'
      ? [...(protocol.workflowControlSignals ?? [])]
      : [...(protocol.statusSignals ?? [])]
  }
  const values = [...(protocol.workflowNodeSignals ?? [])]
  return systemItem ? values : values.filter(signal => signal !== 'SUBFLOW_COMPLETED')
}

export function serializeWorkflowExpression(expression = '') {
  return String(expression).replace(/@([A-Za-z_][A-Za-z0-9_]*)/g, '$1').trim()
}

export const workflowTemporalFunctions = [
  { key: 'RATE', name: '变化速率', functionName: 'rate', template: (name) => `rate(${name}, 10)` },
  { key: 'DELTA', name: '变化量', functionName: 'delta', template: (name) => `delta(${name}, 10)` },
  { key: 'AVG', name: '窗口平均值', functionName: 'avg', template: (name) => `avg(${name}, 60)` },
  { key: 'MAX', name: '窗口最大值', functionName: 'max', template: (name) => `max(${name}, 60)` },
  { key: 'MIN', name: '窗口最小值', functionName: 'min', template: (name) => `min(${name}, 60)` },
  { key: 'ABS', name: '绝对值', functionName: 'abs', template: (name) => `abs(${name})` },
]

export function validateWorkflowExpression(expression = '', variables = [], options = {}) {
  const source = String(expression).trim()
  if (!source) return ['计算表达式不能为空']
  const normalizedSource = serializeWorkflowExpression(source)
  const assignment = options.assignment ? normalizedSource.match(/^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.+)$/) : null
  if (options.assignment && !assignment) return ['请使用“赋值目标内部变量 = 计算表达式”的格式']
  const target = assignment?.[1]
  const normalized = assignment?.[2] ?? normalizedSource
  const allowedFunctions = new Set(options.temporal ? workflowTemporalFunctions.map(item => item.functionName) : [])
  const functionCalls = [...normalized.matchAll(/\b([A-Za-z_][A-Za-z0-9_]*)\s*\(/g)].map(match => match[1])
  const unsupported = functionCalls.find(name => !allowedFunctions.has(name))
  if (unsupported) return [`不支持函数 ${unsupported}`]
  if (/[^A-Za-z0-9_+\-*/().,\s]/.test(normalized)) return ['表达式包含不支持的符号']
  let depth = 0
  for (const character of normalized) {
    if (character === '(') depth += 1
    if (character === ')') depth -= 1
    if (depth < 0) return ['表达式括号不匹配']
  }
  if (depth !== 0) return ['表达式括号不匹配']
  const numeric = new Set(variables
    .filter(variable => ['INTEGER', 'DOUBLE'].includes(variable.dataType))
    .map(variable => variable.name))
  if (target && !numeric.has(target)) return [`赋值目标不是已声明的数值变量：${target}`]
  const identifiers = normalized.match(/[A-Za-z_][A-Za-z0-9_]*/g) ?? []
  const unknown = [...new Set(identifiers.filter(name => !numeric.has(name) && !allowedFunctions.has(name)))]
  return unknown.length ? [`未知变量或非数值变量：${unknown.join('、')}`] : []
}
