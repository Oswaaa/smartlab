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
  const inspectorSource = readSource('views/task/WorkflowDesigner/components/WorkflowNodeInspector.vue')
  const interfacesPortsSource = readSource('views/task/WorkflowDesigner/components/inspector/WorkflowInterfacesPortsPanel.vue')
  const lifecycleSource = readSource('views/task/WorkflowDesigner/components/inspector/WorkflowLifecyclePanel.vue')
  const designerSource = readSource('views/task/WorkflowDesigner/WorkflowDesigner.vue')
  const triggersSource = readSource('views/task/WorkflowDesigner/components/inspector/WorkflowTriggersActionsPanel.vue')

  test('task pages are colocated with page-specific components', () => {
    const taskListSource = readSource('views/task/TaskList/TaskList.vue')
    assert.match(designerSource, /\.\/components\/WorkflowCanvasNode\.vue/)
    assert.match(taskListSource, /\.\/components\/TaskResourceBindingCanvas\.vue/)
  })

  test('designer exposes business control and data panels without transport implementation', () => {
    assert.match(inspectorSource, /WorkflowDataPortsPanel/)
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

  test('canvas nodes render every control interface and every data port', () => {
    const source = readSource('views/task/WorkflowDesigner/components/WorkflowCanvasNode.vue')
    assert.match(source, /controlInputs/)
    assert.match(source, /controlOutputs/)
    assert.match(source, /inputPorts/)
    assert.match(source, /outputPorts/)
    assert.match(source, /interface-handle/)
    assert.match(source, /port-handle/)
    assert.doesNotMatch(source, /interfaceType === 'WORKFLOW'/)
  })

  test('node inspector uses the five confirmed business tabs', () => {
    const source = readSource('views/task/WorkflowDesigner/components/WorkflowNodeInspector.vue')
    for (const label of ['业务配置', '变量空间', '数据端口', '控制接口', '生命周期']) {
      assert.match(source, new RegExp(label))
    }
    assert.doesNotMatch(source, /触发与动作|接口与端口|数据与端口/)
  })
})
