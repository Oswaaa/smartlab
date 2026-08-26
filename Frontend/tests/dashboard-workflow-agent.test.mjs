import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const srcRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', 'src')

function readSource(relativePath) {
  return readFileSync(path.join(srcRoot, relativePath), 'utf8')
}

describe('homepage workflow agent chat', () => {
  const dashboard = readSource('views/dashboard/Dashboard.vue')
  const chat = readSource('views/dashboard/components/WorkflowAgentChat.vue')
  const designer = readSource('views/task/WorkflowDesigner/WorkflowDesigner.vue')

  test('homepage gives the generate pane the main column and a side lab strip', () => {
    assert.match(dashboard, /WorkflowAgentChat/)
    assert.match(dashboard, /dashboard-side-pane/)
    assert.doesNotMatch(dashboard, /dashboard-bottom-dock/)
    assert.doesNotMatch(dashboard, /inspectorTab/)
  })

  test('chat shows success state and a full interaction log', () => {
    assert.match(chat, /workflowApi\.generateStream/)
    assert.match(chat, /交互日志/)
    assert.match(chat, /result-pill/)
    assert.match(chat, /appendLog/)
    assert.match(chat, /正在请求大模型/)
  })

  test('successful generation offers designer navigation without auto jump', () => {
    assert.match(chat, /path: '\/task-designer'/)
    assert.match(chat, /query: \{ id: String\(id\) \}/)
    assert.doesNotMatch(chat.split('async function sendPrompt')[1] || '', /openDesigner\(flowModelId\)/)
    assert.match(designer, /route\.query\.id/)
    assert.match(designer, /openWorkflowFromQuery/)
  })
})

describe('login and homepage mqtt first paint', () => {
  test('login navigates without a full page reload', () => {
    const login = readSource('views/user/auth/Login.vue')
    assert.match(login, /await router\.push\(authStore\.firstVisiblePath\(\)\)/)
    assert.doesNotMatch(login, /location\.reload/)
  })

  test('homepage does not treat mqtt as disconnected before the status request returns', () => {
    const dashboard = readSource('views/dashboard/Dashboard.vue')
    assert.match(dashboard, /mqttStatus = ref<any>\(null\)/)
    assert.match(dashboard, /查询中/)
    assert.match(dashboard, /mqttStatus\.value == null/)
    assert.match(dashboard, /mqttStatus\.value != null/)
    assert.doesNotMatch(dashboard, /status: 'NOT_STARTED', connected: false/)
  })
})
