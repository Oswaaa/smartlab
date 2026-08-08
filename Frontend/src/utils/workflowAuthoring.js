const editorOnlyKeys = new Set(['position', 'compiled', 'runtime'])

export function toAuthoringPayload(workflow) {
  return authoringValue(JSON.parse(JSON.stringify(workflow || {})))
}

export function adoptPreparedWorkflow(prepared = {}) {
  return JSON.parse(JSON.stringify(prepared.definition || {}))
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

  if (isSystemItem(value)) return systemMarker(value)

  return Object.entries(value).reduce((result, [key, child]) => {
    if (key.startsWith('_') || editorOnlyKeys.has(key)) return result
    result[key] = authoringValue(child)
    return result
  }, {})
}

function isSystemItem(value) {
  return value._system === true || Boolean(value._systemKey)
}

function systemMarker(value) {
  // Preserve all data fields PLUS system markers
  const result = authoringValue(Object.fromEntries(
    Object.entries(value).filter(([k]) => !k.startsWith('_'))
  ))
  if (value._system === true) result._system = true
  if (value._systemKey) result._systemKey = value._systemKey
  return result
}
