const asArray = value => Array.isArray(value) ? value : []
const text = value => value == null ? '' : String(value).trim()

const ruleKey = (kind, fromStateName, toStateName) => `${kind}:${fromStateName}:${toStateName}`

export function buildLifecycleRuleRows(mainPath) {
  const configurableMainRows = asArray(mainPath)
    .filter(row => row.triggerPolicy !== 'SYSTEM')
    .map(row => ({
      ...row,
      kind: 'MAIN',
      key: ruleKey('main', row.fromStateName, row.toStateName)
    }))

  const failureRows = [...new Set(configurableMainRows.map(row => row.fromStateName))]
    .map(fromStateName => ({
      key: ruleKey('failure', fromStateName, 'FAILED'),
      kind: 'FAILURE',
      fromStateName,
      toStateName: 'FAILED',
      triggerPolicy: 'OPTIONAL_EVENT'
    }))

  const terminationRows = ['SENT', 'RECEIVED', 'RUNNING'].map(fromStateName => ({
    key: ruleKey('termination', fromStateName, 'ABORTED'),
    kind: 'TERMINATION',
    fromStateName,
    toStateName: 'ABORTED',
    triggerPolicy: 'OPTIONAL_EVENT'
  }))

  return [...configurableMainRows, ...failureRows, ...terminationRows]
}

export function serializeLifecycleTransitions(rows, bindings, adapterInterfaceName) {
  const transitions = []
  const triggerKeys = new Set()

  for (const row of asArray(rows)) {
    const signalName = text(bindings?.[row.key])
    if (row.kind === 'FAILURE' && !signalName) continue
    if (row.triggerPolicy === 'REQUIRED' && !signalName) {
      throw new Error(`${row.fromStateName} → ${row.toStateName} 必须绑定 Adapter cmdEvent`)
    }
    if (signalName) {
      const triggerKey = `${row.fromStateName}:${signalName}`
      if (!triggerKeys.add(triggerKey)) {
        throw new Error(`${row.fromStateName} 状态不能重复绑定事件 ${signalName}`)
      }
    }
    transitions.push({
      stateSpace: 'CMD',
      description: row.kind === 'FAILURE' ? 'Adapter 事件进入执行失败状态'
        : row.kind === 'TERMINATION' ? 'Adapter 事件进入中止完成状态'
        : signalName ? 'Adapter 事件推进执行生命周期' : '自动推进执行生命周期',
      fromStateName: row.fromStateName,
      toStateName: row.toStateName,
      trigger: signalName ? { interfaceName: adapterInterfaceName, signalName } : null,
      actions: []
    })
  }
  return transitions
}

export function hydrateLifecycleBindings(transitions, rows) {
  const bindings = Object.fromEntries(asArray(rows).map(row => [row.key, '']))
  const rowIndex = new Map(asArray(rows).map(row => [
    `${row.fromStateName}:${row.toStateName}`,
    row
  ]))
  for (const transition of asArray(transitions)) {
    if (transition?.stateSpace !== 'CMD' || !transition?.trigger?.signalName) continue
    const row = rowIndex.get(`${transition.fromStateName}:${transition.toStateName}`)
    if (row) bindings[row.key] = text(transition.trigger.signalName)
  }
  return bindings
}
