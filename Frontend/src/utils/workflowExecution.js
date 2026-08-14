export function isExecutableWorkflow(workflow) {
  return String(workflow?.status || '').trim().toUpperCase() === 'ACTIVE'
}

export function filterExecutableWorkflows(workflows) {
  return Array.isArray(workflows) ? workflows.filter(isExecutableWorkflow) : []
}

export function normalizeInterfaceSnapshot(snapshot, direction) {
  if (snapshot == null) return []
  if (!Array.isArray(snapshot)) throw new Error('接口快照格式不符合当前协议')
  return snapshot.map((item) => {
    const signalName = item?.signalName
    if (!item || typeof item !== 'object' || Array.isArray(item) || typeof item.interfaceName !== 'string' || (signalName !== null && typeof signalName !== 'string')) {
      throw new Error('接口快照格式不符合当前协议')
    }
    return {
      direction,
      interfaceName: item.interfaceName,
      signalName,
      payload: Object.hasOwn(item, 'payload') ? item.payload : undefined,
    }
  })
}

export function visibleVariableEntries(variableSpace) {
  return Object.entries(variableSpace ?? {}).filter(([name]) => name !== '_triggerStates')
}

export function buildStepTree(steps) {
  const nodes = new Map((steps ?? []).map(step => [step.id, { ...step, children: [] }]))
  const roots = []
  for (const step of nodes.values()) {
    const parent = nodes.get(step.parentStepId)
    if (parent) parent.children.push(step)
    else roots.push(step)
  }
  return roots
}

const RUNTIME_STATUSES = new Set(['PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'TERMINATING', 'TERMINATED'])

function runtimeStatus(status) {
  const normalized = String(status || '').toUpperCase()
  if (normalized === 'COMPLETED') return 'SUCCEEDED'
  if (normalized === 'ABORTED') return 'TERMINATED'
  return RUNTIME_STATUSES.has(normalized) ? normalized : 'WAITING'
}

function stepNodeName(step) {
  return step?.nodeName ?? step?.flowNodeName ?? step?.node?.name ?? null
}

function definitionIdRef(node) {
  return node?.nodeIdRef ?? node?.idRef ?? node?.flowNodeId ?? null
}

function latestStep(steps) {
  return [...steps].sort((left, right) => Number(right?.id ?? 0) - Number(left?.id ?? 0))[0] || null
}

export function buildRuntimeGraph(workflow, steps) {
  const definitions = workflow?.nodesDef ?? workflow?.nodes ?? []
  const allSteps = Array.isArray(steps) ? steps : []
  const stepTree = buildStepTree(allSteps)
  const treeById = new Map()
  const indexTree = (items) => items.forEach(item => { treeById.set(item.id, item); indexTree(item.children || []) })
  indexTree(stepTree)

  const nodes = definitions.map((definition, index) => {
    const name = definition?.name ?? `node-${index + 1}`
    const idRef = definitionIdRef(definition)
    const matches = allSteps.filter(step => stepNodeName(step) === name || (idRef != null && step?.nodeIdRef != null && String(step.nodeIdRef) === String(idRef)))
    const step = latestStep(matches)
    return {
      ...definition,
      name,
      status: step ? runtimeStatus(step.nodeStatus ?? step.status) : 'WAITING',
      stepId: step?.id ?? null,
      step: step || null,
      children: step ? (treeById.get(step.id)?.children || []) : [],
    }
  })
  const runtimeNodeByName = new Map(nodes.map(node => [node.name, node]))

  const interfaceEdges = (workflow?.interfaceConnections ?? []).map((connection, index) => {
    const sourceName = connection?.source?.interfaceName
    const targetName = connection?.target?.interfaceName
    const targetNode = runtimeNodeByName.get(connection?.target?.nodeName)
    const targetSnapshot = Array.isArray(targetNode?.step?.interfaceInSnapshot)
      ? targetNode.step.interfaceInSnapshot
      : []
    const accepted = targetSnapshot.find(item => item?.interfaceName === targetName)
    return {
      id: `interface-${index}`,
      kind: 'INTERFACE',
      source: connection?.source?.nodeName,
      target: connection?.target?.nodeName,
      sourceName,
      targetName,
      sourceHandle: `interface:${sourceName}`,
      targetHandle: `interface:${targetName}`,
      used: typeof accepted?.signalName === 'string' && accepted.signalName.length > 0,
      targetStatus: targetNode?.status ?? 'WAITING',
    }
  }).filter(edge => edge.source && edge.target)
  const portEdges = (workflow?.portConnections ?? []).map((connection, index) => ({
    id: `port-${index}`,
    kind: 'PORT',
    source: connection?.source?.nodeName,
    target: connection?.target?.nodeName,
    sourceName: connection?.source?.portName,
    targetName: connection?.target?.portName,
    sourceHandle: `port:${connection?.source?.portName}`,
    targetHandle: `port:${connection?.target?.portName}`,
    used: false,
  })).filter(edge => edge.source && edge.target)
  return { nodes, edges: [...interfaceEdges, ...portEdges] }
}

const TECHNICAL_EVENT_PATTERN = /轮询|线程池|调度器|队列领取|心跳|锁续期|扫描完成/

export function businessExecutionEvents(logs) {
  return (logs ?? []).filter(log =>
    log?.sourceType !== 'SYSTEM' && !TECHNICAL_EVENT_PATTERN.test(String(log?.logInfo ?? '')))
}
