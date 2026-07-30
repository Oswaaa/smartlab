const asArray = value => Array.isArray(value) ? value : []
const text = value => value == null ? '' : String(value).trim()

const ruleKey = (kind, fromStateName, toStateName) => `${kind}:${fromStateName}:${toStateName}`

export function buildLifecycleRuleRows(requirements) {
  return asArray(requirements).map(requirement => {
    const kind = text(requirement.kind).toUpperCase() || 'MAIN'
    return {
      ...requirement,
      kind,
      triggerPolicy: requirement.triggerPolicy || 'REQUIRED',
      key: ruleKey(kind.toLowerCase(), requirement.fromStateName, requirement.toStateName)
    }
  })
}

export function serializeLifecycleTransitions(rows, bindings, adapterInterfaceName) {
  const transitions = []
  const triggerKeys = new Set()

  for (const row of asArray(rows)) {
    const signalName = text(bindings?.[row.key])
    if (!signalName) {
      if (row.triggerPolicy === 'REQUIRED') {
        throw new Error(`${row.fromStateName} → ${row.toStateName} 必须绑定 Adapter cmdEvent`)
      }
      continue
    }

    const triggerKey = `${row.fromStateName}:${signalName}`
    if (!triggerKeys.add(triggerKey)) {
      throw new Error(`${row.fromStateName} 状态不能重复绑定事件 ${signalName}`)
    }
    transitions.push({
      stateSpace: 'CMD',
      description: row.kind === 'FAILURE' ? 'Adapter事件进入执行失败状态'
        : row.kind === 'TERMINATION' ? 'Adapter事件进入中止完成状态'
        : row.toStateName === 'RUNNING' ? 'Adapter事件进入执行中状态'
        : 'Adapter事件推进执行生命周期',
      fromStateName: row.fromStateName,
      toStateName: row.toStateName,
      trigger: { interfaceName: adapterInterfaceName, signalName },
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