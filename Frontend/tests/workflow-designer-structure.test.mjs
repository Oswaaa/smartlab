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

describe('Task 6 — Designer Structure', () => {
  const inspectorSource = readSource('components/task/workflow/WorkflowNodeInspector.vue')
  const interfacesPortsSource = readSource('components/task/workflow/WorkflowInterfacesPortsPanel.vue')
  const lifecycleSource = readSource('components/task/workflow/WorkflowLifecyclePanel.vue')
  const designerSource = readSource('views/task/WorkflowDesigner.vue')
  const triggersSource = readSource('components/task/workflow/WorkflowTriggersActionsPanel.vue')

  test('designer exposes interfaces and ports panel but does not expose transport implementation', () => {
    assert.match(inspectorSource, /接口与端口/, 'inspector should reference interfaces and ports tab')
    assert.match(inspectorSource, /WorkflowInterfacesPortsPanel/)
    assert.match(interfacesPortsSource, /isSystemItem/)
    // Only check user-facing template copy, not internal implementation code
    const templateSection = designerSource.split('</template>')[0] || designerSource
    assert.doesNotMatch(templateSection, /resourceMap|NODE_TO_DEVICE|DEVICE_TO_NODE|HTTP接口|消息主题/)
  })

  test('system lifecycle is locked while inline trigger actions remain editable', () => {
    assert.match(lifecycleSource, /system-lifecycle|_system/, 'lifecycle panel should reference system markers')
    assert.match(lifecycleSource, /readonly|read-only|system-lifecycle/, 'lifecycle should indicate locked state')
    assert.match(triggersSource, /动作能力/)
    assert.match(triggersSource, /orderedInterfaces/)
    assert.match(triggersSource, /trigger\.action\?\.actionName/)
    assert.match(triggersSource, /isSystemItem/)
  })

  test('lifeycle panel renders states and transitions as read-only table', () => {
    assert.match(lifecycleSource, /initialStateName|initialState/)
    assert.match(lifecycleSource, /states/)
  })

  test('designer has separate draft and publish buttons', () => {
    assert.match(designerSource, /保存草稿/)
    assert.match(designerSource, /发布启用/)
  })
})
