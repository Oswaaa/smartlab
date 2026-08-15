const BUILT_INS = new Set(['true', 'false', 'delta', 'avg', 'rate'])
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
export function validateDisplayExpressionSyntax(expression) {
  const source = String(expression || '')
  let index = 0
  while (index < source.length) {
    const character = source[index]
    if (character === "'" || character === '"') {
      const quote = character
      index++
      while (index < source.length && source[index] !== quote) {
        if (source[index] === '\\' && index + 1 < source.length) index += 2
        else index++
      }
      if (index >= source.length) throw new Error('表达式字符串缺少结束引号')
      index++
      continue
    }
    if (character === '@') {
      if (!IDENTIFIER_START.test(source[index + 1] || '')) throw new Error('@后必须填写合法变量名')
      index += 2
      while (index < source.length && IDENTIFIER_PART.test(source[index])) index++
      continue
    }
    if (IDENTIFIER_START.test(character)) {
      const start = index++
      while (index < source.length && IDENTIFIER_PART.test(source[index])) index++
      const identifier = source.slice(start, index)
      if (!BUILT_INS.has(identifier.toLowerCase())) {
        throw new Error(`标识符${identifier}不是@变量；变量必须使用@引用，字符串常量必须使用引号`)
      }
      continue
    }
    index++
  }
}

export function sourceCategoryLabel(sourceType) {
  const map = {
    DEVICE_ATTRIBUTE: '设备属性',
    DEVICE_OPERATION_STATE: '设备OP状态',
    DEVICE_COMMAND_LIFECYCLE: '设备CMD状态',
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
        desc = '任务实例状态'
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
      const inst = instances.find(i => Number(i.id) === Number(act.deviceInstanceId))
      const mdl = models.find(m => Number(m.id || m.modelId) === Number(inst?.deviceModelId))
      const cap = (mdl?.capabilities || []).find(c => c.capabilityName === act.capabilityName)
      const devName = inst?.instanceName || (act.deviceInstanceId ? `设备${act.deviceInstanceId}` : '设备')
      return `${devName}.${cap?.displayName || act.capabilityName || '能力'}`
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
