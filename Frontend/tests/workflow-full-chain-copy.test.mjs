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
    readSource('components/task/TaskExecutionView.vue'),
    readSource('views/task/WorkflowDesigner/components/WorkflowNodeInspector.vue'),
    readSource('views/task/WorkflowDesigner/components/inspector/WorkflowInterfacesPortsPanel.vue'),
    readSource('views/task/WorkflowDesigner/components/inspector/WorkflowTriggersActionsPanel.vue'),
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

  test('execution view maps node status to workflow nodes without exposing internal IDs', () => {
    const source = readSource('components/task/TaskExecutionView.vue')
    assert.match(source, /nodeName|taskName/)
    const templateSection = source.split('</template>')[0] || source
    assert.doesNotMatch(templateSection, /messageId|resourceMap|bindingKey/)
  })
})
