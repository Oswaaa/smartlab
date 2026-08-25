const editorOnlyKeys = new Set(['position', 'compiled', 'runtime'])

export function toAuthoringPayload(workflow) {
  return toWorkflowModelDocument(workflow)
}

export function toWorkflowModelDocument(workflow) {
  const cleaned = authoringValue(JSON.parse(JSON.stringify(workflow || {})))
  return {
    metadata: {
      flowModelId: cleaned.id ?? null,
      flowModelName: cleaned.name || '',
      description: cleaned.description || '',
    },
    nodes: cleaned.nodesDef || cleaned.nodes || [],
    interfaceConnections: cleaned.interfaceConnections || [],
    portConnections: cleaned.portConnections || [],
  }
}

export function toDesignerWorkflow(view = {}) {
  const metadata = view.metadata || {}
  return {
    id: metadata.flowModelId ?? view.id ?? null,
    name: metadata.flowModelName || view.flowModelName || view.flowName || view.name || '',
    description: metadata.description ?? view.description ?? '',
    version: view.version ?? 1,
    status: view.status || 'DRAFT',
    predecessorId: view.predecessorId ?? null,
    nodeIdRefs: view.nodeIdRefs,
    nodesDef: view.nodesDef || view.nodes || [],
    interfaceConnections: view.interfaceConnections || [],
    portConnections: view.portConnections || [],
    creatorId: view.creatorId,
    createTime: view.createTime,
  }
}

export function adoptPreparedWorkflow(prepared = {}) {
  const definition = prepared.definition || {}
  return toDesignerWorkflow({
    ...definition,
    version: prepared.version ?? definition.version,
    status: prepared.status || definition.status,
    predecessorId: prepared.predecessorId ?? definition.predecessorId,
  })
}

export function workflowModelName(item) {
  if (!item) return ''
  return item.metadata?.flowModelName || item.flowModelName || item.flowName || item.name || ''
}

export function workflowModelVersionLabel(item) {
  const version = Number(item?.version)
  return Number.isInteger(version) && version > 0 ? `v${version}` : ''
}

export function workflowModelDisplayName(item) {
  const name = workflowModelName(item)
  const version = workflowModelVersionLabel(item)
  return version ? `${name} ${version}` : name
}

export function workflowModelId(item) {
  if (item == null || typeof item !== 'object') return null
  return item.metadata?.flowModelId ?? item.flowModelId ?? item.id ?? null
}

export function workflowNodes(item) {
  return item?.nodesDef || item?.nodes || []
}

export function indexWorkflowIssues(issues = []) {
  const byElement = {}
  const byPath = {}
  const all = JSON.parse(JSON.stringify(issues || []))
  for (const issue of all) {
    const type = issue.elementType || 'WORKFLOW'
    const id = issue.elementId || 'workflow'
    ;(byElement[type] ||= {})[id] ||= []
    byElement[type][id].push(issue)
    if (issue.path) {
      ;(byPath[issue.path] ||= []).push(issue)
    }
  }
  return { all, byElement, byPath }
}

function authoringValue(value) {
  if (Array.isArray(value)) return value.map(authoringValue)
  if (!value || typeof value !== 'object') return value

  return Object.entries(value).reduce((result, [key, child]) => {
    if (key.startsWith('_') || editorOnlyKeys.has(key)) return result
    result[key] = authoringValue(child)
    return result
  }, {})
}
