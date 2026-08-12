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
  const controlInterfacesSource = readSource('views/task/WorkflowDesigner/components/inspector/WorkflowControlInterfacesPanel.vue')
  const lifecycleSource = readSource('views/task/WorkflowDesigner/components/inspector/WorkflowLifecyclePanel.vue')
  const designerSource = readSource('views/task/WorkflowDesigner/WorkflowDesigner.vue')
  const triggersSource = readSource('views/task/WorkflowDesigner/components/inspector/WorkflowTriggerEditor.vue')
  const variablesSource = readSource('views/task/WorkflowDesigner/components/inspector/WorkflowVariablesPanel.vue')
  const portsSource = readSource('views/task/WorkflowDesigner/components/inspector/WorkflowDataPortsPanel.vue')
  const businessSource = readSource('views/task/WorkflowDesigner/components/inspector/WorkflowBusinessPanel.vue')
  const typedValueSource = readSource('views/task/WorkflowDesigner/components/inspector/WorkflowTypedValueInput.vue')

  test('task pages are colocated with page-specific components', () => {
    const taskListSource = readSource('views/task/TaskList/TaskList.vue')
    assert.match(designerSource, /\.\/components\/WorkflowCanvasNode\.vue/)
    assert.match(taskListSource, /\.\/components\/TaskCreateDrawer\.vue/)
  })

  test('designer exposes business control and data panels without transport implementation', () => {
    assert.match(inspectorSource, /WorkflowDataPortsPanel/)
    assert.match(inspectorSource, /WorkflowControlInterfacesPanel/)
    assert.match(controlInterfacesSource, /isSystemItem/)
    // Only check user-facing template copy, not internal implementation code
    const templateSection = designerSource.split('</template>')[0] || designerSource
    assert.doesNotMatch(templateSection, /resourceMap|NODE_TO_DEVICE|DEVICE_TO_NODE|HTTP接口|消息主题/)
  })

  test('system lifecycle is locked while inline trigger actions remain editable', () => {
    assert.match(lifecycleSource, /system-lifecycle|_system/, 'lifecycle panel should reference system markers')
    assert.match(lifecycleSource, /readonly|read-only|system-lifecycle/, 'lifecycle should indicate locked state')
    assert.match(controlInterfacesSource, /动作能力/)
    assert.match(controlInterfacesSource, /orderedControlInterfaces/)
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

  test('canvas nodes render only workflow control interfaces and every data port', () => {
    const source = readSource('views/task/WorkflowDesigner/components/WorkflowCanvasNode.vue')
    assert.match(source, /controlInputs/)
    assert.match(source, /controlOutputs/)
    assert.match(source, /inputPorts/)
    assert.match(source, /outputPorts/)
    assert.match(source, /interface-handle/)
    assert.match(source, /port-handle/)
    assert.match(source, /interfaceType === 'WORKFLOW'/)
  })

  test('canvas distinguishes compact nodes, workflow handles, data handles and edge types', () => {
    const canvasNodeSource = readSource('views/task/WorkflowDesigner/components/WorkflowCanvasNode.vue')
    assert.match(canvasNodeSource, /width:178px/)
    assert.match(canvasNodeSource, /border-radius:2px/)
    assert.match(canvasNodeSource, /\.interface-handle[^}]*border-radius:50%/)
    assert.match(canvasNodeSource, /\.port-handle[^}]*border-radius:1px/)
    assert.match(designerSource, /\.vue-flow__edge-path[^}]*#7b96b8/)
    assert.match(designerSource, /\.data-edge[^}]*stroke-dasharray/)
    assert.match(designerSource, /pattern-color="#d9d9d9"[^>]*:gap="16"/)
  })

  test('node inspector uses the five confirmed business tabs', () => {
    const source = readSource('views/task/WorkflowDesigner/components/WorkflowNodeInspector.vue')
    for (const label of ['业务配置', '变量空间', '数据端口', '控制接口', '生命周期']) {
      assert.match(source, new RegExp(label))
    }
    assert.doesNotMatch(source, /触发与动作|接口与端口|数据与端口/)
  })

  test('workflow overview stays in the right rail while node and edge details open in a drawer', () => {
    assert.match(designerSource, /class="inspector-panel workflow-overview-panel"/)
    assert.match(designerSource, /<strong>流程基本配置<\/strong>/)
    assert.match(designerSource, /<strong>静态建模检查<\/strong>/)
    assert.match(designerSource, /<el-drawer[\s\S]*class="workflow-element-drawer"/)
    assert.match(designerSource, /<WorkflowNodeInspector[\s\S]*v-if="selectedNode"/)
    assert.match(designerSource, /v-else-if="selectedEdge"/)
    assert.doesNotMatch(designerSource, /v-else-if="validationVisible"/)
  })

  test('designer keeps resources in the sidebar and restores function nodes to the canvas toolbar', () => {
    assert.match(designerSource, /grid-template-columns:218px minmax\(430px,1fr\) 304px/)
    assert.match(designerSource, /<strong>节点资源<\/strong>/)
    assert.match(designerSource, /<strong>流程属性<\/strong>/)
    assert.doesNotMatch(designerSource.split('<script setup')[0], /canvas-floating-island|CONFIGURATION|DEVICE CAPABILITY|PARAMETERS|EXPRESSION/)
    assert.doesNotMatch(designerSource.split('<script setup')[0], /class="resource-function-section"/)
    assert.match(designerSource.split('<script setup')[0], /class="canvas-floating-controls"/)
    assert.match(designerSource, /FolderOpened|Folder/)
  })

  test('node drawer uses a compact seamless console layout and top-level destructive action', () => {
    assert.match(designerSource, /size="62vw"/)
    assert.match(designerSource, /min-width:880px/)
    assert.match(designerSource, /max-width:1180px/)
    assert.match(inspectorSource, /class="node-profile-header"/)
    assert.match(inspectorSource, /class="node-config-workspace"/)
    assert.match(inspectorSource, /class="node-header-actions"[\s\S]*删除节点/)
    assert.match(inspectorSource, /\.node-profile-details\{[^}]*margin:0/)
    assert.match(inspectorSource, /\.node-config-workspace\{[^}]*margin:0/)
    const headerPosition = inspectorSource.indexOf('node-profile-header')
    const tabsPosition = inspectorSource.indexOf('<el-tabs')
    assert.ok(headerPosition >= 0 && headerPosition < tabsPosition)
    assert.doesNotMatch(inspectorSource, /class="node-footer"/)
  })

  test('saved workflows open read-only and expose explicit edit and clear commands', () => {
    assert.match(designerSource, /isEditing/)
    assert.match(designerSource, />编辑</)
    assert.match(designerSource, />清空</)
    assert.match(designerSource, /clearCanvas/)
    assert.match(designerSource, /:readonly="!canEdit"/)
  })

  test('business capability parameters use the new console parameter workspace', () => {
    assert.match(businessSource, /class="capability-config-card"/)
    assert.match(businessSource, /class="capability-parameter-form"/)
    assert.match(businessSource, /class="parameter-description"/)
    assert.match(businessSource, /max-width:360px/)
    assert.doesNotMatch(businessSource.split('<script setup')[0], /DEVICE CAPABILITY|PARAMETERS|EXPRESSION/)
  })

  test('trigger editor presents every trigger as structured rule rows and locks system fields', () => {
    assert.match(triggersSource, /class="condition-rule-row"/)
    assert.match(triggersSource, /class="action-rule-row"/)
    assert.match(triggersSource, />当</)
    assert.match(triggersSource, />则</)
    assert.match(triggersSource, /grid-template-columns:32px minmax\(180px,240px\) 96px minmax\(130px,190px\)/)
    assert.match(triggersSource, /grid-template-columns:32px 110px minmax\(180px,240px\) minmax\(130px,190px\)/)
    assert.match(triggersSource, /系统预置/)
    assert.match(triggersSource, /canEditTrigger/)
    assert.doesNotMatch(triggersSource, /systemSummary/)
    assert.match(triggersSource, /host-interface-summary/)
    assert.doesNotMatch(triggersSource, /condition-grid|action-grid/)
  })

  test('function-node expression editor uses assignment semantics and temporal function menu', () => {
    const expressionSource = readSource('views/task/WorkflowDesigner/components/inspector/WorkflowExpressionEditor.vue')
    assert.match(businessSource, /:assignment="true"/)
    assert.match(businessSource, /:temporal="true"/)
    assert.match(expressionSource, /时序函数/)
    assert.match(expressionSource, /workflowTemporalFunctions/)
    assert.doesNotMatch(expressionSource, /求和|求差|乘积|比值|两项平均值/)
  })

  test('variable and data-port collections use real editable tables', () => {
    for (const source of [variablesSource, portsSource]) {
      assert.match(source, /<el-table/)
      assert.match(source, /<el-table-column/)
      assert.doesNotMatch(source, /editor-table-head|class="editor-row"/)
    }
  })

  test('data-port fields use content-sized columns instead of filling the drawer', () => {
    assert.match(portsSource, /label="端口名称" width="260"/)
    assert.match(portsSource, /label="方向" width="112"/)
    assert.match(portsSource, /label="绑定变量" width="360"/)
    assert.match(portsSource, /<el-table-column min-width="40"/)
  })

  test('node configuration panels use text-and-action empty states without default illustrations', () => {
    for (const source of [variablesSource, portsSource, businessSource, controlInterfacesSource, triggersSource]) {
      assert.doesNotMatch(source, /<el-empty/)
    }
    assert.match(portsSource, /尚未配置数据端口/)
    assert.match(variablesSource, /尚未配置内部变量/)
    assert.match(triggersSource, /尚未配置触发器/)
  })

  test('numeric capability inputs do not expose spinner controls', () => {
    assert.match(typedValueSource, /<el-input-number[^>]*:controls="false"/)
  })

  test('control interface editor exposes creation only for function nodes', () => {
    assert.match(controlInterfacesSource, /canCustomizeControlInterfaces/)
    assert.match(controlInterfacesSource, /canEditControlItem/)
    assert.match(controlInterfacesSource, /新增接口/)
    assert.match(controlInterfacesSource, /系统默认/)
  })

  test('trigger editor uses interface-local condition names', () => {
    assert.match(triggersSource, /signalName/)
    assert.match(triggersSource, /payload/)
    assert.match(triggersSource, /nodeLifecycleState/)
    assert.doesNotMatch(triggersSource, /inputSignalName|inputPayload/)
  })
})
