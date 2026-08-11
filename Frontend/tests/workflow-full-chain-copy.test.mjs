import assert from 'node:assert'
import { describe, test } from 'node:test'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const srcRoot = path.resolve(__dirname, '..', 'src')

function readSource(relativePath) {
  return readFileSync(path.join(srcRoot, relativePath), 'utf-8')
}

describe('Task 9 — Full-Chain Copy Regression', () => {
  const ordinaryUserSources = [
    readSource('views/task/TaskList/TaskList.vue'),
    readSource('views/task/WorkflowDesigner/WorkflowDesigner.vue'),
    readSource('views/task/TaskList/components/TaskResourceBindingCanvas.vue'),
    readSource('views/task/TaskList/components/TaskPreflightPanel.vue'),
    readSource('views/task/TaskList/components/TaskExecutionDrawer.vue'),
    readSource('views/task/WorkflowDesigner/components/WorkflowNodeInspector.vue'),
    readSource('views/task/WorkflowDesigner/components/inspector/WorkflowControlInterfacesPanel.vue'),
    readSource('views/task/WorkflowDesigner/components/inspector/WorkflowTriggerEditor.vue'),
  ]

  test('ordinary workflow and task UI hides internal transport vocabulary', () => {
    for (const source of ordinaryUserSources) {
      // Only check template sections for user-facing copy
      const templateSection = source.split('</template>')[0] || source
      assert.doesNotMatch(templateSection, /resourceMap/)
      assert.doesNotMatch(templateSection, /bindingKey/)
      assert.doesNotMatch(templateSection, /NODE_TO_DEVICE|DEVICE_TO_NODE/)
      assert.doesNotMatch(templateSection, /HTTP接口/)
      assert.doesNotMatch(templateSection, /消息主题/)
    }
  })

  test('preflight panel shows ready/blocked states and check results', () => {
    const source = readSource('views/task/TaskList/components/TaskPreflightPanel.vue')
    assert.match(source, /ready|preflight-result/)
    assert.match(source, /checks|preflight-checks/)
  })

  test('execution view maps node status without exposing transport identifiers', () => {
    const source = readSource('views/task/TaskList/components/TaskExecutionDrawer.vue')
    assert.match(source, /taskName|taskStatus/)
    const templateSection = source.split('</template>')[0] || source
    assert.doesNotMatch(templateSection, /messageId|resourceMap|bindingKey/)
  })

  test('task list delegates create flow without losing binding and constraints', () => {
    const source = readSource('views/task/TaskList/TaskList.vue')
    const drawer = readSource('views/task/TaskList/components/TaskCreateDrawer.vue')
    assert.match(source, /TaskCreateDrawer/)
    assert.match(drawer, /TaskPreflightPanel/)
    assert.match(drawer, /TaskResourceBindingCanvas/)
    assert.match(drawer, /TaskConstraintPanel/)
    assert.match(drawer, /发布启用|已发布|可执行流程/)
  })

  test('execution drawer exposes the confirmed runtime views', () => {
    const source = readSource('views/task/TaskList/components/TaskExecutionDrawer.vue')
    for (const label of ['运行概览', '执行流程图', '步骤详情', '设备绑定', '任务约束', '业务事件']) {
      assert.match(source, new RegExp(label))
    }
  })

  test('snapshot panel uses canonical fields and no routing envelope', () => {
    const source = readSource('views/task/TaskList/components/InterfaceSnapshotPanel.vue')
    assert.match(source, /interfaceName/)
    assert.match(source, /signalName/)
    assert.match(source, /payload/)
    assert.doesNotMatch(source, /messageId|capabilityRef|sourceNodeIdRef|inputSignalName/)
  })

  test('runtime graph source uses Vue Flow and emits selected step', () => {
    const source = readSource('views/task/TaskList/components/TaskRuntimeGraph.vue')
    assert.match(source, /VueFlow/)
    assert.match(source, /select-step/)
    assert.match(source, /WAITING/)
  })
})
