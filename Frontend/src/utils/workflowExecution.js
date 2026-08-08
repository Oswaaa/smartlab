export function isExecutableWorkflow(workflow) {
  return String(workflow?.status || '').trim().toUpperCase() === 'ACTIVE'
}

export function filterExecutableWorkflows(workflows) {
  return Array.isArray(workflows) ? workflows.filter(isExecutableWorkflow) : []
}
