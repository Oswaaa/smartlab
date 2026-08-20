const IDENTIFIER_START = /[A-Za-z_]/
const IDENTIFIER_PART = /[A-Za-z0-9_]/

export function extractExpressionVariables(expression) {
  const result = []
  for (const match of String(expression || '').matchAll(/@([A-Za-z_][A-Za-z0-9_]*)/g)) {
    if (!result.includes(match[1])) result.push(match[1])
  }
  return result
}
export function toBackendExpression(expression) {
  return String(expression || '').replace(/@([A-Za-z_][A-Za-z0-9_]*)/g, '$1').trim()
}
const TEMPORAL_FUNCTIONS = new Set(['delta', 'avg', 'rate'])
const TWO_CHAR_OPS = ['||', '&&', '==', '!=', '>=', '<=']

export function validateDisplayExpressionSyntax(expression) {
  const source = String(expression || '')
  if (!source.trim()) return
  parseConstraintPredicate(source)
}

function tokenizeDisplay(source) {
  const tokens = []
  let index = 0
  while (index < source.length) {
    const character = source[index]
    if (/\s/.test(character)) {
      index++
      continue
    }
    const two = source.slice(index, index + 2)
    if (TWO_CHAR_OPS.includes(two)) {
      tokens.push({ type: 'OP', text: two, pos: index })
      index += 2
      continue
    }
    if ('><+-*/!(),'.includes(character)) {
      tokens.push({ type: 'OP', text: character, pos: index })
      index++
      continue
    }
    if (character === "'" || character === '"') {
      const quote = character
      const start = index++
      let text = ''
      while (index < source.length && source[index] !== quote) {
        if (source[index] === '\\' && index + 1 < source.length) {
          text += source[index + 1]
          index += 2
        } else {
          text += source[index++]
        }
      }
      if (index >= source.length) throw new Error('表达式字符串缺少结束引号')
      index++
      tokens.push({ type: 'STRING', text, pos: start })
      continue
    }
    if (character === '@') {
      if (!IDENTIFIER_START.test(source[index + 1] || '')) throw new Error('@后必须填写合法变量名')
      const start = index++
      while (index < source.length && IDENTIFIER_PART.test(source[index])) index++
      tokens.push({ type: 'VAR', text: source.slice(start, index), pos: start })
      continue
    }
    if (character === '.' && /\d/.test(source[index + 1] || '')) {
      const start = index++
      while (index < source.length && /\d/.test(source[index])) index++
      tokens.push({ type: 'NUMBER', text: source.slice(start, index), pos: start })
      continue
    }
    if (/\d/.test(character)) {
      const start = index++
      while (index < source.length && (/\d/.test(source[index]) || source[index] === '.')) index++
      tokens.push({ type: 'NUMBER', text: source.slice(start, index), pos: start })
      continue
    }
    if (IDENTIFIER_START.test(character)) {
      const start = index++
      while (index < source.length && IDENTIFIER_PART.test(source[index])) index++
      tokens.push({ type: 'IDENT', text: source.slice(start, index), pos: start })
      continue
    }
    throw new Error(`无法识别的表达式字符: ${character}`)
  }
  tokens.push({ type: 'EOF', text: '', pos: source.length })
  return tokens
}

function parseConstraintPredicate(source) {
  const tokens = tokenizeDisplay(source)
  let index = 0
  const peek = () => tokens[index]
  const take = () => tokens[index++]
  const matchOp = text => {
    if (peek().type === 'OP' && peek().text === text) {
      take()
      return true
    }
    return false
  }
  const expectOp = text => {
    if (!matchOp(text)) throw new Error(`表达式缺少 ${text}`)
  }

  const parseOr = () => {
    let left = parseAnd()
    while (matchOp('||')) {
      requireBoolish(left, '||')
      requireBoolish(parseAnd(), '||')
      left = { type: 'bool' }
    }
    return left
  }
  const parseAnd = () => {
    let left = parseEquality()
    while (matchOp('&&')) {
      requireBoolish(left, '&&')
      requireBoolish(parseEquality(), '&&')
      left = { type: 'bool' }
    }
    return left
  }
  const parseEquality = () => {
    let left = parseComparison()
    while (peek().text === '==' || peek().text === '!=') {
      take()
      parseComparison()
      left = { type: 'bool' }
    }
    return left
  }
  const parseComparison = () => {
    let left = parseAddition()
    while (['>', '<', '>=', '<='].includes(peek().text)) {
      take()
      parseAddition()
      left = { type: 'bool' }
    }
    return left
  }
  const parseAddition = () => {
    let left = parseMultiplication()
    while (peek().text === '+' || peek().text === '-') {
      const operator = take().text
      const right = parseMultiplication()
      left = { type: operator === '+' && (left.type === 'string' || right.type === 'string') ? 'string' : 'number' }
    }
    return left
  }
  const parseMultiplication = () => {
    let left = parseUnary()
    while (peek().text === '*' || peek().text === '/') {
      take()
      parseUnary()
      left = { type: 'number' }
    }
    return left
  }
  const parseUnary = () => {
    if (matchOp('!')) {
      requireBoolish(parseUnary(), '!')
      return { type: 'bool' }
    }
    if (matchOp('-')) {
      parseUnary()
      return { type: 'number' }
    }
    return parsePrimary()
  }
  const parsePrimary = () => {
    const token = peek()
    if (token.type === 'NUMBER') {
      take()
      return { type: 'number' }
    }
    if (token.type === 'STRING') {
      take()
      return { type: 'string' }
    }
    if (matchOp('(')) {
      const inner = parseOr()
      expectOp(')')
      return inner
    }
    if (token.type === 'VAR') {
      take()
      return { type: 'value' }
    }
    if (token.type === 'IDENT') {
      take()
      const name = token.text
      if (matchOp('(')) return parseFunction(name)
      if (name.toLowerCase() === 'true' || name.toLowerCase() === 'false') return { type: 'bool' }
      throw new Error(`标识符${name}不是@变量；变量必须使用@引用，字符串常量必须使用引号`)
    }
    throw new Error('表达式需要值')
  }
  const parseFunction = name => {
    const fn = name.toLowerCase()
    if (!TEMPORAL_FUNCTIONS.has(fn)) throw new Error(`不支持的约束内置函数: ${name}`)
    const first = peek()
    if (first.type !== 'VAR') throw new Error(`${fn}() 第一个参数必须是 @变量`)
    take()
    if (!matchOp(',')) throw new Error(`${fn}() 需要两个参数：@变量 和时间窗口秒数，例如 ${fn}(@value, 10)`)
    if (peek().type === 'NUMBER' && !(Number(peek().text) > 0)) {
      throw new Error(`${fn}() 的时间窗口必须大于 0 秒`)
    }
    const windowNode = parseOr()
    if (windowNode.type === 'bool' || windowNode.type === 'string') {
      throw new Error(`${fn}() 的时间窗口必须是大于 0 的秒数`)
    }
    expectOp(')')
    return { type: 'number', fn }
  }

  const result = parseOr()
  if (peek().type !== 'EOF') {
    throw new Error('表达式存在无法解析的内容。相邻两项之间必须使用比较或逻辑运算符，例如 delta(@value, 10) > @limit')
  }
  if (result.type === 'number') {
    const hint = result.fn ? `${result.fn}() 只返回数值` : '当前公式只计算出数值'
    throw new Error(`判定表达式必须返回 true/false。${hint}，需要与阈值比较，例如 ${result.fn || 'rate'}(@value, 10) > @limit`)
  }
  if (result.type === 'string') {
    throw new Error('判定表达式必须返回 true/false，不能只写字符串')
  }
}

function requireBoolish(node, operator) {
  if (node.type === 'bool' || node.type === 'value') return
  throw new Error(`${operator} 两侧必须是布尔判定。delta/avg/rate 只返回数值，请先比较，例如 avg(@value, 60) > @limit`)
}

export function sourceCategoryLabel(sourceType) {
  const map = {
    DEVICE_ATTRIBUTE: '设备属性',
    DEVICE_OPERATION_STATE: '设备功能状态',
    DEVICE_COMMAND_LIFECYCLE: '设备指令状态',
    NODE_LIFECYCLE_STATE: '节点生命周期',
    NODE_INTERNAL_VARIABLE: '节点内部变量',
    TASK_LIFECYCLE_STATE: '任务实例状态'
  }
  return map[sourceType] || sourceType || '未知来源'
}

export function instantiateExpression(rule, models = [], instances = []) {
  if (!rule) return ''
  const bindings = rule.bindings || {}
  let expr = rule.expression || ''
  const varMap = {}

  for (const [name, binding] of Object.entries(bindings)) {
    if (binding?.bindingType === 'OBSERVABLE') {
      const src = binding.source || {}
      const mdl = models.find(m => Number(m.id || m.modelId) === Number(src.deviceModelId))
      if (src.sourceType === 'DEVICE_ATTRIBUTE') {
        const attr = (mdl?.attributes || []).find(a => a.attributeName === src.targetName)
        varMap[name] = attr?.displayName || src.targetName || name
      } else if (src.sourceType === 'DEVICE_OPERATION_STATE') {
        varMap[name] = `${src.regionName || 'OP'}状态`
      } else if (src.sourceType === 'DEVICE_COMMAND_LIFECYCLE') {
        varMap[name] = 'CMD状态'
      } else if (src.sourceType === 'TASK_LIFECYCLE_STATE') {
        varMap[name] = '任务状态'
      } else if (src.sourceType === 'NODE_LIFECYCLE_STATE') {
        varMap[name] = `${src.nodeName || '节点'}.生命周期`
      } else if (src.sourceType === 'NODE_INTERNAL_VARIABLE') {
        varMap[name] = `${src.nodeName || '节点'}.${src.variableName || '变量'}`
      }
    } else if (binding?.bindingType === 'LITERAL') {
      varMap[name] = String(binding.value ?? '')
    }
  }

  for (const [name, val] of Object.entries(varMap).sort((a, b) => b[0].length - a[0].length)) {
    expr = expr
      .replace(new RegExp(`@${name}\\b`, 'g'), val)
      .replace(new RegExp(`\\b${name}\\b`, 'g'), val)
  }
  return expr
}

export function formatRuleSentenceTokens(rule, models = [], instances = []) {
  if (!rule) return { mainSource: '监测对象', conditionExpr: '', windowText: '瞬时判定', actionLabels: [] }
  const bindings = rule.bindings || {}
  const actions = rule.violationActions || []
  let displayExpr = rule.expression || ''

  // 替换表达式中的变量名为主体描述
  const varDescriptions = {}
  let mainSourceLabel = ''

  for (const [name, binding] of Object.entries(bindings)) {
    if (binding?.bindingType === 'OBSERVABLE') {
      const src = binding.source || {}
      const inst = instances.find(i => Number(i.id) === Number(src.deviceInstanceId))
      const mdl = models.find(m => Number(m.id || m.modelId) === Number(src.deviceModelId))
      const targetPrefix = inst?.instanceName || mdl?.modelName || (src.deviceInstanceId ? `实例${src.deviceInstanceId}` : '设备')

      let desc = targetPrefix
      if (src.sourceType === 'DEVICE_ATTRIBUTE') {
        const attr = (mdl?.attributes || []).find(a => a.attributeName === src.targetName)
        desc = `${targetPrefix}.${attr?.displayName || src.targetName || '属性'}`
      } else if (src.sourceType === 'DEVICE_OPERATION_STATE') {
        desc = `${targetPrefix}.${src.regionName || 'OP'}状态`
      } else if (src.sourceType === 'DEVICE_COMMAND_LIFECYCLE') {
        desc = `${targetPrefix}.CMD状态`
      } else if (src.sourceType === 'TASK_LIFECYCLE_STATE') {
        desc = src.taskId ? `任务#${src.taskId}状态` : '工作流任务状态'
      } else if (src.sourceType === 'NODE_LIFECYCLE_STATE') {
        desc = `${src.nodeName || '节点'}.生命周期`
      } else if (src.sourceType === 'NODE_INTERNAL_VARIABLE') {
        desc = `${src.nodeName || '节点'}.${src.variableName || '变量'}`
      }
      varDescriptions[name] = desc
      if (!mainSourceLabel) mainSourceLabel = desc
    } else if (binding?.bindingType === 'LITERAL') {
      varDescriptions[name] = String(binding.value ?? '')
    }
  }

  // 格式化实例化之后的判定公式 (消除抽象的 @observedValue / @threshold)
  let instantiatedCondition = displayExpr
  for (const [name, desc] of Object.entries(varDescriptions).sort((a, b) => b[0].length - a[0].length)) {
    instantiatedCondition = instantiatedCondition
      .replace(new RegExp(`@${name}\\b`, 'g'), desc)
      .replace(new RegExp(`\\b${name}\\b`, 'g'), desc)
  }

  // 格式化动作
  const actionLabels = actions.map(act => {
    if (act.actionType === 'SYSTEM') {
      const sysMap = { ABORT: '终止当前任务', PAUSE: '暂停当前任务', ALERT: '发布系统告警' }
      return sysMap[act.action] || act.action || '系统处置'
    }
    if (act.actionType === 'DEVICE_CAPABILITY') {
      return formatDeviceActionLabel(act, models, instances)
    }
    return '执行处置'
  })

  // 整理时序条件
  const windowText = rule.windowSeconds && rule.windowSeconds > 0 ? `持续 ${rule.windowSeconds} 秒` : '瞬时判定'

  return {
    mainSource: mainSourceLabel || '监测对象',
    conditionExpr: instantiatedCondition,
    windowText,
    actionLabels: actionLabels.length ? actionLabels : ['发布系统告警']
  }
}

const SYSTEM_ACTION_LABELS = {
  ABORT: '终止当前任务',
  PAUSE: '暂停当前任务',
  ALERT: '发布系统告警'
}

const TEMPORAL_FN_EXPLAIN = {
  rate: seconds => `rate(测点, ${seconds}) 表示过去 ${seconds} 秒内该测点每秒的平均变化量（末值减首值，再除以首末样本的实际间隔）`,
  delta: seconds => `delta(测点, ${seconds}) 表示过去 ${seconds} 秒内该测点的变化量（窗口内末值减首值）`,
  avg: seconds => `avg(测点, ${seconds}) 表示过去 ${seconds} 秒内该测点全部样本的算术平均值`
}

export function explainConstraintExpression(expression, windowSeconds) {
  const source = String(expression || '')
  const parts = []
  const seen = new Set()
  const matcher = /\b(delta|avg|rate)\s*\(\s*[^,()]+,\s*([^,()]+)\)/gi
  let match
  while ((match = matcher.exec(source))) {
    const name = match[1].toLowerCase()
    const windowArg = String(match[2] || '').trim()
    const key = `${name}:${windowArg}`
    if (seen.has(key) || !TEMPORAL_FN_EXPLAIN[name]) continue
    seen.add(key)
    parts.push(TEMPORAL_FN_EXPLAIN[name](windowArg))
  }
  if (windowSeconds && Number(windowSeconds) > 0) {
    parts.push(`「持续 ${windowSeconds} 秒」表示条件连续成立达到该时间后才触发`)
  } else {
    parts.push('「瞬时判定」表示条件一旦成立就触发，不再额外等待')
  }
  return parts.join('。') + '。'
}

export function describeBindingTarget(binding, models = [], instances = []) {
  if (!binding) return { primary: '-', secondary: '' }
  if (binding.bindingType === 'LITERAL') {
    return { primary: String(binding.value ?? ''), secondary: '固定阈值' }
  }
  const src = binding.source || {}
  const inst = instances.find(i => Number(i.id) === Number(src.deviceInstanceId))
  const mdl = models.find(m => Number(m.id || m.modelId) === Number(src.deviceModelId))
  const objectName = inst?.instanceName || mdl?.modelName || (src.deviceInstanceId ? `设备 #${src.deviceInstanceId}` : '')

  if (src.sourceType === 'DEVICE_ATTRIBUTE') {
    const attr = (mdl?.attributes || []).find(a => a.attributeName === src.targetName)
    return { primary: attr?.displayName || src.targetName || '未指定', secondary: `${objectName || '设备'}·属性` }
  }
  if (src.sourceType === 'DEVICE_OPERATION_STATE') {
    return { primary: src.regionName || '未指定分区', secondary: `${objectName || '设备'}·功能状态` }
  }
  if (src.sourceType === 'DEVICE_COMMAND_LIFECYCLE') {
    return { primary: '指令生命周期', secondary: objectName ? `${objectName}·指令` : '设备·指令' }
  }
  if (src.sourceType === 'TASK_LIFECYCLE_STATE') {
    return { primary: '任务状态', secondary: src.taskId ? `任务 #${src.taskId}` : (src.workflowTemplateId ? `工作流 #${src.workflowTemplateId}·全任务` : '全任务') }
  }
  if (src.sourceType === 'NODE_LIFECYCLE_STATE') {
    return { primary: '节点生命周期', secondary: src.nodeName || '节点' }
  }
  if (src.sourceType === 'NODE_INTERNAL_VARIABLE') {
    return { primary: src.variableName || '未指定', secondary: `${src.nodeName || '节点'}·内部变量` }
  }
  return { primary: '未知测点', secondary: objectName || '监测对象' }
}

export function parseActionTaken(actionTaken) {
  const raw = String(actionTaken || '').trim()
  if (!raw) return { ok: true, kind: 'empty' }

  if (raw.startsWith('FAILED:')) {
    const rest = raw.slice('FAILED:'.length)
    const split = rest.indexOf(':')
    const type = split >= 0 ? rest.slice(0, split) : rest
    const error = split >= 0 ? rest.slice(split + 1) : ''
    if (type === 'DEVICE_CAPABILITY') return { ok: false, kind: 'device', error }
    if (type === 'SYSTEM') return { ok: false, kind: 'system', error }
    return { ok: false, kind: 'unknown', error: rest }
  }

  if (raw.startsWith('DEVICE_CAPABILITY:')) {
    const capabilityName = raw.split(':').slice(1).filter(part => part && part !== 'CONSTRAINT_EXECUTE').join(':')
    return { ok: true, kind: 'device', capabilityName }
  }

  if (raw.startsWith('SYSTEM:')) {
    return { ok: true, kind: 'system', systemAction: raw.slice('SYSTEM:'.length) }
  }

  return { ok: true, kind: 'unknown' }
}

export function modelCapabilities(mdl) {
  if (Array.isArray(mdl?.capabilities) && mdl.capabilities.length) return mdl.capabilities
  if (Array.isArray(mdl?.capabilitySpec?.capabilities)) return mdl.capabilitySpec.capabilities
  return []
}

export function bindingDeviceModelId(bindings) {
  if (!bindings || typeof bindings !== 'object') return null
  const list = Array.isArray(bindings) ? bindings : Object.values(bindings)
  for (const binding of list) {
    const id = Number(binding?.source?.deviceModelId || binding?.deviceModelId)
    if (id > 0) return id
  }
  return null
}

export function formatDeviceActionLabel(act, models = [], instances = [], separator = '.') {
  const inst = instances.find(i => Number(i.id) === Number(act?.deviceInstanceId))
  const mdl = models.find(m => Number(m.id || m.modelId) === Number(act?.deviceModelId))
  const cap = modelCapabilities(mdl).find(c => c.capabilityName === act?.capabilityName)
  const devName = inst?.instanceName || (act?.deviceInstanceId ? `设备${act.deviceInstanceId}` : '与观测同源')
  return `${devName}${separator}${cap?.displayName || act?.capabilityName || '能力'}`
}

function instanceLabel(deviceInstanceId, instances = []) {
  if (!deviceInstanceId) return ''
  const inst = instances.find(i => Number(i.id) === Number(deviceInstanceId))
  return inst?.instanceName || `设备 #${deviceInstanceId}`
}

function capabilityLabel(deviceInstanceId, capabilityName, models = [], instances = []) {
  if (!capabilityName) return ''
  const inst = instances.find(i => Number(i.id) === Number(deviceInstanceId))
  const mdl = models.find(m => Number(m.id || m.modelId) === Number(inst?.deviceModelId || inst?.modelId))
  const cap = modelCapabilities(mdl).find(c => c.capabilityName === capabilityName)
  return cap?.displayName || ''
}

export function formatViolationActionTaken(log, models = [], instances = []) {
  const parsed = parseActionTaken(log?.actionTaken)
  const deviceLabel = instanceLabel(log?.deviceInstanceId, instances)
  const capLabel = capabilityLabel(log?.deviceInstanceId, parsed.capabilityName, models, instances)

  if (parsed.kind === 'system') {
    return {
      ok: parsed.ok,
      status: parsed.ok ? '已执行' : '执行失败',
      summary: SYSTEM_ACTION_LABELS[parsed.systemAction] || '系统处置',
      detail: parsed.error || ''
    }
  }

  if (parsed.kind === 'device') {
    const summary = [deviceLabel, capLabel || (parsed.ok ? '设备能力' : '')].filter(Boolean).join(' · ')
    return {
      ok: parsed.ok,
      status: parsed.ok ? '已下发' : '下发失败',
      summary: summary || '设备能力',
      detail: parsed.error || ''
    }
  }

  return {
    ok: parsed.ok,
    status: parsed.ok ? '已执行' : '执行失败',
    summary: '',
    detail: parsed.error || ''
  }
}
