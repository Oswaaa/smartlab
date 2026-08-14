export function workflowLibraryGroups(workflows = []) {
  return [
    { key: 'active', label: '已启用流程', children: workflows.filter(item => item.status === 'ACTIVE') },
    { key: 'draft', label: '草稿流程', children: workflows.filter(item => item.status !== 'ACTIVE') },
  ]
}

export function canDragWorkflowResource(workflow, currentWorkflowId, editable) {
  const workflowId = Number(workflow?.id)
  if (!editable || !Number.isInteger(workflowId) || workflowId <= 0) return false
  return currentWorkflowId == null || Number(currentWorkflowId) !== workflowId
}

export function clearWorkflowCanvas(workflow) {
  return {
    ...workflow,
    nodesDef: [],
    interfaceConnections: [],
    portConnections: [],
  }
}

export function workflowNodeConnectionIssues(nodes = [], connections = []) {
  const nodeNames = new Set(nodes.map(node => node.name))
  const outgoing = new Map([...nodeNames].map(name => [name, []]))
  const incoming = new Map([...nodeNames].map(name => [name, []]))
  const validConnections = connections.filter(connection => nodeNames.has(connection.source?.nodeName) && nodeNames.has(connection.target?.nodeName))
  const targetInterfaceConnections = new Map()
  for (const connection of validConnections) {
    outgoing.get(connection.source.nodeName)?.push(connection.target.nodeName)
    incoming.get(connection.target.nodeName)?.push(connection.source.nodeName)
    const key = `${connection.target.nodeName}\u0000${connection.target.interfaceName || ''}`
    targetInterfaceConnections.set(key, (targetInterfaceConnections.get(key) || 0) + 1)
  }

  const issues = []
  const add = (node, code, title, detail) => issues.push({ code:`${code}-${node.name}`, title, detail, nodeName:node.name, path:'topology' })
  for (const node of nodes) {
    const inCount = incoming.get(node.name)?.length || 0
    const outCount = outgoing.get(node.name)?.length || 0
    if (node.functionType === 'START' && inCount) add(node, 'start-in', '开始节点存在上游连接', '请删除指向开始节点的连接。')
    else if (node.functionType !== 'START' && !inCount) add(node, 'missing-in', '未连接上游节点', '请将前一个节点连接到此节点。')

    if (node.functionType === 'END' && outCount) add(node, 'end-out', '结束节点存在下游连接', '请删除结束节点之后的连接。')
    else if (node.functionType !== 'END' && !outCount) add(node, 'missing-out', '未连接下游节点', '请将此节点连接到后续节点。')

    const duplicateInputs = [...targetInterfaceConnections.entries()]
      .filter(([key, count]) => key.startsWith(`${node.name}\u0000`) && count > 1)
    duplicateInputs.forEach(([key]) => add(node, `duplicate-input-${key.split('\u0000')[1]}`,
      '输入接口重复连接', '每个输入接口只能连接一个上游接口，请新增独立输入接口。'))

    if (node.functionType === 'AGGREGATE') {
      const connectedInputs = new Set(validConnections
        .filter(connection => connection.target?.nodeName === node.name)
        .map(connection => connection.target?.interfaceName)
        .filter(Boolean)).size
      aggregateThresholds(node).filter(item => item.required > connectedInputs)
        .forEach(item => add(node, `aggregate-threshold-${item.required}`,
          '聚合阈值不可达', `当前只有 ${connectedInputs} 个有效输入接口，无法达到阈值 ${item.required}。`))
    }
  }

  const starts = nodes.filter(node => node.functionType === 'START')
  if (starts.length === 1) {
    const reachable = walkWorkflowGraph(starts[0].name, outgoing)
    nodes.filter(node => (incoming.get(node.name)?.length || 0) > 0 && !reachable.has(node.name))
      .forEach(node => add(node, 'unreachable', '无法从开始节点到达', '请检查此前的执行路径是否与流程入口连通。'))
  }
  const ends = nodes.filter(node => node.functionType === 'END')
  if (ends.length === 1) {
    const reachesEnd = walkWorkflowGraph(ends[0].name, incoming)
    nodes.filter(node => (outgoing.get(node.name)?.length || 0) > 0 && !reachesEnd.has(node.name))
      .forEach(node => add(node, 'no-end', '无法到达结束节点', '请检查后续执行路径是否与流程出口连通。'))
  }
  return issues
}

function aggregateThresholds(node) {
  const result = []
  for (const item of node.interfaces || []) {
    if (item.direction !== 'OUT' || item.interfaceType !== 'WORKFLOW') continue
    for (const trigger of item.bindingTriggers || []) {
      const predicates = Array.isArray(trigger.condition?.conditions)
        ? trigger.condition.conditions
        : [trigger.condition]
      for (const predicate of predicates) {
        if (predicate?.object !== 'aggregateCount' || !Number.isInteger(predicate.threshold)) continue
        const required = predicate.operator === '>' ? predicate.threshold + 1
          : ['>=', '=', '=='].includes(predicate.operator) ? predicate.threshold : null
        if (required !== null) result.push({ required })
      }
    }
  }
  return result
}

function walkWorkflowGraph(start, graph) {
  const visited = new Set()
  const queue = [start]
  while (queue.length) {
    const name = queue.shift()
    if (visited.has(name)) continue
    visited.add(name)
    for (const next of graph.get(name) || []) if (!visited.has(next)) queue.push(next)
  }
  return visited
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

const MENTION_DELIMITER = /[\s+\-*/(),=@]/

export function workflowExpressionMentions(expression = '') {
  const source = String(expression ?? '')
  const mentions = []
  for (let index = 0; index < source.length; index += 1) {
    if (source[index] !== '@') continue
    const start = index
    let end = index + 1
    while (end < source.length && !MENTION_DELIMITER.test(source[end])) end += 1
    if (end > start + 1) mentions.push({ name: source.slice(start + 1, end), start, end })
    index = end - 1
  }
  return mentions
}

export function serializeWorkflowExpression(expression = '', variableNames = []) {
  const source = String(expression ?? '')
  const names = new Set(Array.isArray(variableNames) ? variableNames : [])
  const mentions = workflowExpressionMentions(source)
  if (!mentions.length) return source.trim()
  let result = ''
  let cursor = 0
  for (const mention of mentions) {
    result += source.slice(cursor, mention.start)
    result += names.size === 0 || names.has(mention.name) ? mention.name : `@${mention.name}`
    cursor = mention.end
  }
  return (result + source.slice(cursor)).trim()
}

export function workflowExpressionDisplayTokens(expression = '', variableNames = []) {
  const source = String(expression ?? '')
  const names = new Set(Array.isArray(variableNames) ? variableNames : [])
  const tokens = []
  let cursor = 0
  for (const mention of workflowExpressionMentions(source)) {
    if (mention.start > cursor) tokens.push({ value: source.slice(cursor, mention.start), variable: false })
    tokens.push({ value: source.slice(mention.start, mention.end), variable: names.has(mention.name) })
    cursor = mention.end
  }
  if (cursor < source.length) tokens.push({ value: source.slice(cursor), variable: false })
  return tokens
}

export function formatWorkflowExpression(expression = '', variableNames = []) {
  return serializeWorkflowExpression(expression, variableNames)
    .replace(/\s+/g, ' ')
    .replace(/\s*([=+*/])\s*/g, ' $1 ')
    .replace(/\s*-\s*/g, ' - ')
    .replace(/\s*,\s*/g, ', ')
    .replace(/\(\s*/g, '(')
    .replace(/\s*\)/g, ')')
    .replace(/\s+/g, ' ')
    .replace(/(^|[=(,+*/-])\s+-\s+(?=\d|[\p{L}_])/gu, '$1 -')
    .trim()
}

export const workflowTemporalFunctions = [
  { key: 'RATE', name: '变化速率', functionName: 'rate', defaultWindowSeconds: 10, template: 'rate(, 10)', example: 'rate(@温度, 10)', description: '计算变量在指定时间窗口内每秒的平均变化量。第二个参数是窗口秒数，可修改。' },
  { key: 'DELTA', name: '变化量', functionName: 'delta', defaultWindowSeconds: 10, template: 'delta(, 10)', example: 'delta(@温度, 10)', description: '计算变量在指定时间窗口内首末值之差。第二个参数是窗口秒数，可修改。' },
  { key: 'AVG', name: '窗口平均值', functionName: 'avg', defaultWindowSeconds: 60, template: 'avg(, 60)', example: 'avg(@温度, 60)', description: '计算变量在指定时间窗口内的平均值。第二个参数是窗口秒数，可修改。' },
  { key: 'MAX', name: '窗口最大值', functionName: 'max', defaultWindowSeconds: 60, template: 'max(, 60)', example: 'max(@温度, 60)', description: '取变量在指定时间窗口内的最大值。第二个参数是窗口秒数，可修改。' },
  { key: 'MIN', name: '窗口最小值', functionName: 'min', defaultWindowSeconds: 60, template: 'min(, 60)', example: 'min(@温度, 60)', description: '取变量在指定时间窗口内的最小值。第二个参数是窗口秒数，可修改。' },
  { key: 'ABS', name: '绝对值', functionName: 'abs', template: 'abs()', example: 'abs(@温差)', description: '返回变量或计算结果的绝对值。' },
]

export function validateWorkflowExpression(expression = '', variables = [], options = {}) {
  const source = String(expression).trim()
  if (!source) return ['计算表达式不能为空']
  const definitions = new Map((variables || []).map(variable => [variable.name, variable.dataType]))
  const allNames = [...definitions.keys()]
  const numericNames = allNames.filter(name => ['INTEGER', 'DOUBLE'].includes(definitions.get(name)))
  const knownNumericNames = new Set(numericNames)
  const equalIndex = topLevelEqualsIndex(source)
  if (options.assignment && equalIndex < 0) return ['请使用“赋值目标内部变量 = 计算表达式”的格式']
  const target = options.assignment ? source.slice(0, equalIndex).trim().replace(/^@/, '') : null
  if (target && !definitions.has(target)) return [`赋值目标未在变量空间中声明：${target}`]
  if (target && !knownNumericNames.has(target)) return [`赋值目标必须是数值类型（INTEGER 或 DOUBLE）：${target}`]
  const valueSource = options.assignment ? source.slice(equalIndex + 1).trim() : source
  const unresolved = workflowExpressionMentions(valueSource).filter(item => !definitions.has(item.name)).map(item => item.name)
  if (unresolved.length) return [`未匹配的内部变量：${[...new Set(unresolved)].join('、')}`]
  const nonNumeric = workflowExpressionMentions(valueSource).filter(item => definitions.has(item.name) && !knownNumericNames.has(item.name)).map(item => item.name)
  if (nonNumeric.length) return [`表达式只能引用数值类型变量：${[...new Set(nonNumeric)].join('、')}`]
  const normalized = serializeWorkflowExpression(valueSource, allNames)
  const allowedFunctions = new Set(options.temporal ? workflowTemporalFunctions.map(item => item.functionName) : [])
  const functionCalls = [...normalized.matchAll(/\b([A-Za-z_][A-Za-z0-9_]*)\s*\(/g)].map(match => match[1])
  const unsupported = functionCalls.find(name => !allowedFunctions.has(name))
  if (unsupported) return [`不支持函数 ${unsupported}`]
  if (/[^\p{L}\p{N}_+\-*/().,\s]/u.test(normalized)) return ['表达式包含不支持的符号']
  let depth = 0
  for (const character of normalized) {
    if (character === '(') depth += 1
    if (character === ')') depth -= 1
    if (depth < 0) return ['表达式括号不匹配']
  }
  if (depth !== 0) return ['表达式括号不匹配']
  const identifiers = normalized.match(/[\p{L}_][\p{L}\p{N}_]*/gu) ?? []
  const unknown = [...new Set(identifiers.filter(name => !knownNumericNames.has(name) && !allowedFunctions.has(name)))]
  return unknown.length ? [`未知变量或非数值变量：${unknown.join('、')}`] : []
}

function topLevelEqualsIndex(source) {
  let depth = 0
  for (let index = 0; index < source.length; index += 1) {
    const character = source[index]
    if (character === '(') depth += 1
    else if (character === ')') depth -= 1
    else if (character === '=' && depth === 0) return index
  }
  return -1
}
