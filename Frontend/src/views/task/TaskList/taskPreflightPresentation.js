const ISSUE_TITLES = {
  TASK_BINDING_MISSING: '设备未全部绑定',
  TASK_BINDING_REQUIREMENTS_INVALID: '流程设备配置无效',
  TASK_BINDING_INVALID: '设备绑定无效',
  WORKFLOW_NOT_EXECUTABLE: '流程不可执行',
  DEVICE_OFFLINE: '设备不在线',
  DEVICE_BUSY: '设备当前不可用',
  TASK_CONSTRAINT_INVALID: '任务约束无效'
}

const ISSUE_DETAILS = {
  TASK_BINDING_MISSING: '请为所有设备节点选择可用的设备实例'
}

export function presentPreflightIssue(issue = {}) {
  return {
    title: ISSUE_TITLES[issue.code] || issue.message || '检查未通过',
    detail: ISSUE_DETAILS[issue.code] || issue.suggestion || issue.message || ''
  }
}
