const STATUS_TYPES = {
  PENDING: 'info',
  RUNNING: 'primary',
  SUCCEEDED: 'success',
  FAILED: 'danger',
  TERMINATING: 'warning',
  TERMINATED: 'warning',
}

const STATUS_LABELS = {
  PENDING: '排队中',
  RUNNING: '运行中',
  SUCCEEDED: '已完成',
  FAILED: '失败',
  TERMINATING: '终止中',
  TERMINATED: '已终止',
}

export function statusType(status) {
  return STATUS_TYPES[status] || 'info'
}

export function statusLabel(status) {
  return STATUS_LABELS[status] || status
}

export function selectPreferredStepId(steps = [], preferredId = null) {
  if (steps.some(step => step.id === preferredId)) return preferredId
  return steps.find(step => step.nodeStatus === 'RUNNING')?.id ?? steps.at(-1)?.id ?? null
}
