# Workflow Frontend Closed-Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有工作流前端重构为两个独立菜单页面，并完成节点接口/端口可视化、只读系统契约、任务运行图和规范化运行详情闭环。

**Architecture:** 保持 Vue 3、Element Plus 和 Vue Flow 技术栈，将超大页面按 `views/task/WorkflowDesigner` 与 `views/task/TaskList` 页面域拆分。纯规则放入可由 Node Test 直接验证的工具模块；页面组件只负责展示、选择状态和事件上抛。后端接口和数据库结构不变，运行图由流程定义拓扑与 `TASK_STEP` 快照在前端组合产生。

**Tech Stack:** Vue 3.5、TypeScript SFC、Element Plus 2.13、Vue Flow 1.48、Vite 6、Node.js `node:test`

## Global Constraints

- 顶部菜单中的 `任务列表` 和 `流程设计` 保持两个独立路由页面，不增加页面内模拟路由切换栏。
- 节点配置标签固定为：业务配置、变量空间、数据端口、控制接口、生命周期。
- 系统默认接口、端口和触发器只读；只有 `FUNC_NODE` 能新增用户控制接口和触发器。
- `DEV_NODE` 与 `SUBFLOW_NODE` 不提供新增或删除控制接口和触发器的入口。
- 控制接口按 `OUT → IN` 展示；条件对象使用 `signalName` / `payload`，不使用旧 `inputSignalName` / `inputPayload` 别名。
- 任务创建必须保留预检、设备实例绑定和任务级约束。
- 任务详情同时提供执行流程图、步骤详情、设备绑定、任务约束和业务事件。
- 业务事件不得显示轮询、线程池、队列领取、心跳或调度器扫描。
- 接口快照严格使用 `{ interfaceName, signalName, payload? }[]`；`signalName: null` 表示尚无信号。
- 普通变量空间隐藏 `_triggerStates`。
- 前端运行详情默认 1 秒刷新；后端 100ms 调度行为不修改。
- 保留“发布启用”文案。
- 不新增前端依赖，不修改后端或数据库结构。

---

## File Structure

### Domain utilities

- `Frontend/src/utils/workflowNodeDefinition.js`：节点模板恢复、系统项识别、控制契约权限和节点定义校验。
- `Frontend/src/utils/workflowExecution.js`：任务运行视图的接口快照、步骤树、运行拓扑、变量和业务事件转换。

### Workflow designer page domain

- `Frontend/src/views/task/WorkflowDesigner/WorkflowDesigner.vue`：设计器页面编排和数据加载。
- `Frontend/src/views/task/WorkflowDesigner/components/WorkflowCanvasNode.vue`：画布节点及控制接口/数据端口连接点。
- `Frontend/src/views/task/WorkflowDesigner/components/WorkflowNodeInspector.vue`：五标签节点检查器。
- `Frontend/src/views/task/WorkflowDesigner/components/WorkflowOverviewPanel.vue`：流程基本配置和校验总览。
- `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowBusinessPanel.vue`：节点业务配置。
- `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowVariablesPanel.vue`：内部变量和设备属性映射。
- `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowDataPortsPanel.vue`：数据端口。
- `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowControlInterfacesPanel.vue`：接口主从列表和权限控制。
- `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowTriggerEditor.vue`：单个接口的触发器与内联动作编辑。
- `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowLifecyclePanel.vue`：生命周期只读视图。
- `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowTypedValueInput.vue`：类型化常量输入。

### Task list page domain

- `Frontend/src/views/task/TaskList/TaskList.vue`：任务列表页面编排和请求生命周期。
- `Frontend/src/views/task/TaskList/components/TaskSummaryStrip.vue`：状态汇总。
- `Frontend/src/views/task/TaskList/components/TaskFilterBar.vue`：查询条件。
- `Frontend/src/views/task/TaskList/components/TaskCreateDrawer.vue`：创建任务编排。
- `Frontend/src/views/task/TaskList/components/TaskPreflightPanel.vue`：任务预检。
- `Frontend/src/views/task/TaskList/components/TaskResourceBindingCanvas.vue`：资源绑定。
- `Frontend/src/views/task/TaskList/components/TaskConstraintPanel.vue`：任务级约束。
- `Frontend/src/views/task/TaskList/components/TaskExecutionDrawer.vue`：运行详情抽屉。
- `Frontend/src/views/task/TaskList/components/TaskRuntimeGraph.vue`：图形化运行拓扑。
- `Frontend/src/views/task/TaskList/components/TaskStepTree.vue`：层级步骤列表。
- `Frontend/src/views/task/TaskList/components/TaskStepDetail.vue`：选中步骤详情。
- `Frontend/src/views/task/TaskList/components/InterfaceSnapshotPanel.vue`：规范化接口快照。
- `Frontend/src/views/task/TaskList/components/TaskBusinessEventList.vue`：业务事件。

### Shared task-domain components

- `Frontend/src/views/task/components/WorkflowStatusBadge.vue`：任务和节点状态标签。
- `Frontend/src/views/task/components/WorkflowEmptyState.vue`：统一空状态。
- `Frontend/src/views/task/components/WorkflowJsonValue.vue`：JSON 值预览。

---

### Task 1: Add node-contract permission and ordering rules

**Files:**
- Modify: `Frontend/src/utils/workflowNodeDefinition.js`
- Modify: `Frontend/tests/workflowNodeDefinition.test.mjs`

**Interfaces:**
- Produces: `canCustomizeControlInterfaces(node): boolean`
- Produces: `canEditControlItem(node, item): boolean`
- Produces: `orderedControlInterfaces(node): Array<object>`
- Produces: validation error `非功能节点不能声明自定义控制接口` for invalid imported models

- [ ] **Step 1: Write failing permission and ordering tests**

```js
import {
  canCustomizeControlInterfaces,
  canEditControlItem,
  orderedControlInterfaces,
  validateNodeDefinition,
} from '../src/utils/workflowNodeDefinition.js'

test('only function nodes customize user control interfaces', () => {
  const custom = { name: 'custom_out', direction: 'OUT' }
  const system = { name: 'workflow_in', direction: 'IN', _system: true }
  assert.equal(canCustomizeControlInterfaces({ nodeType: 'FUNC_NODE' }), true)
  assert.equal(canCustomizeControlInterfaces({ nodeType: 'DEV_NODE' }), false)
  assert.equal(canCustomizeControlInterfaces({ nodeType: 'SUBFLOW_NODE' }), false)
  assert.equal(canEditControlItem({ nodeType: 'FUNC_NODE' }, custom), true)
  assert.equal(canEditControlItem({ nodeType: 'FUNC_NODE' }, system), false)
})

test('control interfaces preserve declaration order inside OUT then IN groups', () => {
  const node = { interfaces: [
    { name: 'in_a', direction: 'IN' },
    { name: 'out_a', direction: 'OUT' },
    { name: 'in_b', direction: 'IN' },
    { name: 'out_b', direction: 'OUT' },
  ] }
  assert.deepEqual(orderedControlInterfaces(node).map(item => item.name), ['out_a', 'out_b', 'in_a', 'in_b'])
})

test('non-function nodes reject custom control interfaces', () => {
  const node = createDeviceNode(deviceModel(), 'device1')
  node.interfaces.push({ name: 'user_out', direction: 'OUT', interfaceType: 'WORKFLOW', allowedSignals: ['ACTIVE'], bindingTriggers: [] })
  assert.match(validateNodeDefinition(node, { deviceModel: deviceModel() })[0].message, /非功能节点/)
})
```

- [ ] **Step 2: Run the focused tests and verify failure**

Run: `node --test tests/workflowNodeDefinition.test.mjs` from `Frontend`  
Expected: FAIL because the three exported permission helpers do not exist.

- [ ] **Step 3: Implement the minimal permission helpers and validation**

```js
export function canCustomizeControlInterfaces(node) {
  return node?.nodeType === 'FUNC_NODE'
}

export function canEditControlItem(node, item) {
  return canCustomizeControlInterfaces(node) && !isSystemItem(item)
}

export function orderedControlInterfaces(node) {
  const interfaces = node?.interfaces ?? []
  return [
    ...interfaces.filter(item => item.direction === 'OUT'),
    ...interfaces.filter(item => item.direction === 'IN'),
  ]
}

function validateControlContractOwnership(node, errors) {
  if (canCustomizeControlInterfaces(node)) return
  ;(node.interfaces ?? []).forEach((item, index) => {
    if (!isSystemItem(item)) errors.push({
      path: `interfaces[${index}]`,
      message: '非功能节点不能声明自定义控制接口',
    })
  })
}
```

Call `validateControlContractOwnership(node, errors)` from `validateNodeDefinition` after trigger validation.

- [ ] **Step 4: Run the focused tests and verify pass**

Run: `node --test tests/workflowNodeDefinition.test.mjs`  
Expected: PASS.

- [ ] **Step 5: Commit the rule layer**

```bash
git add Frontend/src/utils/workflowNodeDefinition.js Frontend/tests/workflowNodeDefinition.test.mjs
git commit -m "feat(workflow-ui): enforce node control-contract ownership"
```

### Task 2: Move task pages and components into page domains

**Files:**
- Move: `Frontend/src/views/task/WorkflowDesigner.vue` → `Frontend/src/views/task/WorkflowDesigner/WorkflowDesigner.vue`
- Move: `Frontend/src/views/task/TaskList.vue` → `Frontend/src/views/task/TaskList/TaskList.vue`
- Move: `Frontend/src/components/task/TaskPreflightPanel.vue` → `Frontend/src/views/task/TaskList/components/TaskPreflightPanel.vue`
- Move: `Frontend/src/components/task/TaskResourceBindingCanvas.vue` → `Frontend/src/views/task/TaskList/components/TaskResourceBindingCanvas.vue`
- Move: `Frontend/src/components/task/workflow/WorkflowCanvasNode.vue` → `Frontend/src/views/task/WorkflowDesigner/components/WorkflowCanvasNode.vue`
- Move: `Frontend/src/components/task/workflow/WorkflowNodeInspector.vue` → `Frontend/src/views/task/WorkflowDesigner/components/WorkflowNodeInspector.vue`
- Move: `Frontend/src/components/task/workflow/WorkflowLifecyclePanel.vue` → `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowLifecyclePanel.vue`
- Move: `Frontend/src/components/task/workflow/WorkflowTypedValueInput.vue` → `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowTypedValueInput.vue`
- Modify: `Frontend/src/router/index.js`
- Modify: `Frontend/tests/workflow-designer-structure.test.mjs`
- Modify: `Frontend/tests/workflow-full-chain-copy.test.mjs`
- Modify: `Frontend/tests/final-review-workflow-contract.test.mjs`

**Interfaces:**
- Produces route imports `../views/task/TaskList/TaskList.vue` and `../views/task/WorkflowDesigner/WorkflowDesigner.vue`
- Keeps route names and paths unchanged: `TaskManagement`, `TaskDesigner`, `/task-management`, `/task-designer`

- [ ] **Step 1: Change structure tests to require the page-domain paths**

```js
const inspectorSource = readSource('views/task/WorkflowDesigner/components/WorkflowNodeInspector.vue')
const designerSource = readSource('views/task/WorkflowDesigner/WorkflowDesigner.vue')
const taskListSource = readSource('views/task/TaskList/TaskList.vue')

test('task pages are colocated with page-specific components', () => {
  assert.match(designerSource, /\.\/components\/WorkflowCanvasNode\.vue/)
  assert.match(taskListSource, /\.\/components\/TaskPreflightPanel\.vue/)
})
```

Update every source-fixture path in the three tests; do not leave fallback reads for old paths.

- [ ] **Step 2: Run structure tests and verify failure**

Run: `node --test tests/workflow-designer-structure.test.mjs tests/workflow-full-chain-copy.test.mjs tests/final-review-workflow-contract.test.mjs`  
Expected: FAIL with `ENOENT` for the new paths.

- [ ] **Step 3: Move files and repair imports without behavior changes**

Update the router:

```js
{ path: 'task-management', name: 'TaskManagement', component: () => import('../views/task/TaskList/TaskList.vue'), meta: { title: '任务列表' } },
{ path: 'task-designer', name: 'TaskDesigner', component: () => import('../views/task/WorkflowDesigner/WorkflowDesigner.vue'), meta: { title: '流程设计' } },
```

Adjust relative imports after each move. Do not add re-export or compatibility components in the old locations.

- [ ] **Step 4: Run structure tests and build**

Run: `node --test tests/workflow-designer-structure.test.mjs tests/workflow-full-chain-copy.test.mjs tests/final-review-workflow-contract.test.mjs`  
Expected: PASS.  
Run: `npm run build`  
Expected: build succeeds; existing chunk-size warnings may remain.

- [ ] **Step 5: Commit the directory migration**

```bash
git add Frontend/src/router/index.js Frontend/src/views/task Frontend/src/components/task Frontend/tests
git commit -m "refactor(task-ui): colocate workflow pages and components"
```

### Task 3: Render all node control interfaces and data ports on the canvas

**Files:**
- Modify: `Frontend/src/views/task/WorkflowDesigner/components/WorkflowCanvasNode.vue`
- Modify: `Frontend/tests/workflow-designer-structure.test.mjs`
- Modify: `Frontend/tests/workflowCanvas.test.mjs`

**Interfaces:**
- Consumes: `interfaceHandleId(name)` and `portHandleId(name)` from `workflowCanvas.js`
- Produces: left-side `IN` control handles, right-side `OUT` control handles, top `OUT` data handles, bottom `IN` data handles

- [ ] **Step 1: Add failing source-structure tests for distinct interface and port semantics**

```js
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
```

- [ ] **Step 2: Run the focused structure test and verify failure**

Run: `node --test tests/workflow-designer-structure.test.mjs`  
Expected: FAIL because the component currently filters interfaces to `WORKFLOW` and uses old computed names.

- [ ] **Step 3: Replace workflow-only handle collections with control collections**

```ts
const controlInputs = computed(() => (props.node?.interfaces ?? [])
  .filter((item: Item) => item.direction === 'IN'))
const controlOutputs = computed(() => (props.node?.interfaces ?? [])
  .filter((item: Item) => item.direction === 'OUT'))
```

Keep the four edge placements and add visible legend classes:

```html
<span class="control-interface-key">控制接口</span>
<span class="data-port-key">数据端口</span>
```

Use blue circular handles for interfaces and purple square/diamond handles for ports. Keep labels inside the node boundary where possible and use `title` for the full name.

- [ ] **Step 4: Run focused tests and build**

Run: `node --test tests/workflow-designer-structure.test.mjs tests/workflowCanvas.test.mjs`  
Expected: PASS.  
Run: `npm run build`  
Expected: PASS.

- [ ] **Step 5: Commit canvas-node presentation**

```bash
git add Frontend/src/views/task/WorkflowDesigner/components/WorkflowCanvasNode.vue Frontend/tests/workflow-designer-structure.test.mjs Frontend/tests/workflowCanvas.test.mjs
git commit -m "feat(workflow-ui): show node interfaces and data ports"
```

### Task 4: Rebuild the node inspector around five business tabs

**Files:**
- Create: `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowBusinessPanel.vue`
- Create: `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowVariablesPanel.vue`
- Create: `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowDataPortsPanel.vue`
- Modify: `Frontend/src/views/task/WorkflowDesigner/components/WorkflowNodeInspector.vue`
- Remove after replacement: `Frontend/src/components/task/workflow/WorkflowVariablesPortsPanel.vue`
- Remove after replacement: `Frontend/src/components/task/workflow/WorkflowInterfacesPortsPanel.vue`
- Modify: `Frontend/tests/workflow-designer-structure.test.mjs`

**Interfaces:**
- Produces exact inspector tabs: `business`, `variables`, `ports`, `interfaces`, `lifecycle`
- `WorkflowBusinessPanel` emits `update:node`
- `WorkflowVariablesPanel` emits `update:node`
- `WorkflowDataPortsPanel` emits `update:node` and `remove-port-request`

- [ ] **Step 1: Add failing tests for the exact five-tab contract**

```js
test('node inspector uses the five confirmed business tabs', () => {
  const source = readSource('views/task/WorkflowDesigner/components/WorkflowNodeInspector.vue')
  for (const label of ['业务配置', '变量空间', '数据端口', '控制接口', '生命周期']) {
    assert.match(source, new RegExp(label))
  }
  assert.doesNotMatch(source, /触发与动作|接口与端口/)
})
```

- [ ] **Step 2: Run the focused test and verify failure**

Run: `node --test tests/workflow-designer-structure.test.mjs`  
Expected: FAIL because the old inspector still exposes `数据与端口`、`触发与动作` and `接口与端口`.

- [ ] **Step 3: Extract business, variable and port responsibilities**

Move existing business configuration without behavior changes into `WorkflowBusinessPanel.vue` and preserve the device capability reset behavior:

```ts
function changeCapability(name: string) {
  const capability = props.deviceCapabilities.find(item => item.capabilityName === name)
  if (!capability) return
  publish(replaceCapability(props.node, capability, props.node.capability))
}
```

Move internal-variable editing into `WorkflowVariablesPanel.vue`. Move port editing into `WorkflowDataPortsPanel.vue`; use `isSystemItem` to disable system ports and continue raising `remove-port-request` for safe connection cleanup.

- [ ] **Step 4: Replace the inspector template with the exact tab order**

```vue
<el-tabs v-model="activeTab" class="node-tabs">
  <el-tab-pane name="business" label="业务配置"><WorkflowBusinessPanel ... /></el-tab-pane>
  <el-tab-pane name="variables" label="变量空间"><WorkflowVariablesPanel ... /></el-tab-pane>
  <el-tab-pane name="ports" label="数据端口"><WorkflowDataPortsPanel ... /></el-tab-pane>
  <el-tab-pane name="interfaces" label="控制接口"><WorkflowControlInterfacesPanel ... /></el-tab-pane>
  <el-tab-pane name="lifecycle" label="生命周期"><WorkflowLifecyclePanel ... /></el-tab-pane>
</el-tabs>
```

Keep tab error counters mapped to the new names. For lifecycle-less function nodes, the lifecycle panel renders `此功能节点不使用节点生命周期` instead of disappearing.

- [ ] **Step 5: Run the focused tests and build**

Run: `node --test tests/workflow-designer-structure.test.mjs tests/workflowNodeDefinition.test.mjs`  
Expected: PASS.  
Run: `npm run build`  
Expected: PASS.

- [ ] **Step 6: Commit the inspector decomposition**

```bash
git add Frontend/src/views/task/WorkflowDesigner Frontend/src/components/task/workflow Frontend/tests/workflow-designer-structure.test.mjs
git commit -m "refactor(workflow-ui): organize node inspector by business concepts"
```

### Task 5: Build the control-interface master-detail editor

**Files:**
- Create: `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowControlInterfacesPanel.vue`
- Create: `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowTriggerEditor.vue`
- Remove after replacement: `Frontend/src/components/task/workflow/WorkflowTriggersActionsPanel.vue`
- Modify: `Frontend/tests/workflow-designer-structure.test.mjs`
- Modify: `Frontend/tests/workflowNodeDefinition.test.mjs`
- Modify: `Frontend/tests/final-review-workflow-contract.test.mjs`

**Interfaces:**
- Consumes: `orderedControlInterfaces`, `canCustomizeControlInterfaces`, `canEditControlItem`, `isSystemItem`, `removeInterface`
- `WorkflowControlInterfacesPanel` emits `update:node` and `update:interfaceConnections`
- `WorkflowTriggerEditor` receives `{ node, interfaceItem }` and emits `update:interface`

- [ ] **Step 1: Add failing permission and condition-scope tests**

```js
test('control interface editor exposes creation only for function nodes', () => {
  const source = readSource('views/task/WorkflowDesigner/components/inspector/WorkflowControlInterfacesPanel.vue')
  assert.match(source, /canCustomizeControlInterfaces/)
  assert.match(source, /canEditControlItem/)
  assert.match(source, /新增接口/)
  assert.match(source, /系统默认/)
})

test('trigger editor uses interface-local condition names', () => {
  const source = readSource('views/task/WorkflowDesigner/components/inspector/WorkflowTriggerEditor.vue')
  assert.match(source, /signalName/)
  assert.match(source, /payload/)
  assert.match(source, /nodeLifecycleState/)
  assert.doesNotMatch(source, /inputSignalName|inputPayload/)
})
```

Update old fixtures in `workflowNodeDefinition.test.mjs` and `final-review-workflow-contract.test.mjs`:

```js
condition: { object: 'signalName', operator: '=', threshold: 'ACTIVE' }
condition: { object: 'payload.stateName', operator: '=', threshold: 'COMPLETED' }
```

- [ ] **Step 2: Run focused tests and verify failure**

Run: `node --test tests/workflow-designer-structure.test.mjs tests/workflowNodeDefinition.test.mjs tests/final-review-workflow-contract.test.mjs`  
Expected: FAIL because the new components do not exist and legacy aliases remain.

- [ ] **Step 3: Implement the interface master list and node-type permissions**

```ts
const interfaces = computed(() => orderedControlInterfaces(props.node))
const canAddInterface = computed(() => canCustomizeControlInterfaces(props.node))
const selectedEditable = computed(() => canEditControlItem(props.node, selectedInterface.value))
```

Rules:

- Show `新增接口` only when `canAddInterface` is true.
- Disable every field and remove action when `selectedEditable` is false.
- Only editable function-node interfaces show `添加触发器`.
- When an interface is removed, call `removeInterface` and emit the cleaned connection list.
- Preserve array order when editing an item; do not sort persisted data.

- [ ] **Step 4: Implement the focused trigger editor**

Condition objects are derived per host interface:

```ts
const conditionObjects = computed(() => [
  'signalName',
  'payload',
  'payload.stateName',
  'nodeLifecycleState',
  ...(['BRANCH', 'AGGREGATE'].includes(props.node.functionType) ? ['expression'] : []),
  ...(props.node.internalVariables ?? []).map(variable => variable.name),
])
```

Keep inline actions and the existing mutually-exclusive source conversion:

```ts
const { value: _value, valueExpression: _expression, ...payload } = trigger.action.payload
const nextPayload = source === 'EXPRESSION'
  ? { ...payload, valueExpression: '' }
  : { ...payload, value: null }
```

System triggers render the same summary and fields but every control is disabled and deletion is absent.

- [ ] **Step 5: Run focused tests and build**

Run: `node --test tests/workflow-designer-structure.test.mjs tests/workflowNodeDefinition.test.mjs tests/final-review-workflow-contract.test.mjs`  
Expected: PASS.  
Run: `npm run build`  
Expected: PASS.

- [ ] **Step 6: Commit control-interface editing**

```bash
git add Frontend/src/views/task/WorkflowDesigner Frontend/src/components/task/workflow Frontend/tests
git commit -m "feat(workflow-ui): add governed control-interface editor"
```

### Task 6: Add strict runtime presentation utilities

**Files:**
- Modify: `Frontend/src/utils/workflowExecution.js`
- Modify: `Frontend/tests/workflow-execution.test.mjs`

**Interfaces:**
- Produces: `normalizeInterfaceSnapshot(snapshot, direction): { direction, interfaceName, signalName, payload }[]`
- Produces: `visibleVariableEntries(variableSpace): [string, unknown][]`
- Produces: `buildStepTree(steps): StepTreeNode[]`
- Produces: `buildRuntimeGraph(workflow, steps): { nodes, edges }`
- Produces: `businessExecutionEvents(logs): object[]`

- [ ] **Step 1: Add failing utility tests**

```js
import {
  normalizeInterfaceSnapshot,
  visibleVariableEntries,
  buildStepTree,
  buildRuntimeGraph,
  businessExecutionEvents,
} from '../src/utils/workflowExecution.js'

test('normalizes only canonical interface snapshot arrays', () => {
  assert.deepEqual(normalizeInterfaceSnapshot([
    { interfaceName: 'workflow_in', signalName: null },
    { interfaceName: 'state_in', signalName: 'DONE', payload: { result: 1 } },
  ], 'IN'), [
    { direction: 'IN', interfaceName: 'workflow_in', signalName: null, payload: undefined },
    { direction: 'IN', interfaceName: 'state_in', signalName: 'DONE', payload: { result: 1 } },
  ])
  assert.throws(() => normalizeInterfaceSnapshot({ messageId: 'legacy' }, 'IN'), /快照格式/)
})

test('hides engine trigger state from user variables', () => {
  assert.deepEqual(visibleVariableEntries({ temp: 20, _triggerStates: { a: true } }), [['temp', 20]])
})

test('builds nested subflow steps inside the same task', () => {
  const tree = buildStepTree([
    { id: 1, parentStepId: null, stepDepth: 0 },
    { id: 2, parentStepId: 1, stepDepth: 1 },
  ])
  assert.equal(tree[0].children[0].id, 2)
})

test('runtime graph keeps uncreated workflow nodes waiting', () => {
  const graph = buildRuntimeGraph({ nodesDef: [{ name: 'start' }, { name: 'end' }], interfaceConnections: [] }, [
    { id: 1, nodeName: 'start', nodeStatus: 'SUCCEEDED' },
  ])
  assert.deepEqual(graph.nodes.map(node => [node.name, node.status]), [['start', 'SUCCEEDED'], ['end', 'WAITING']])
})

test('business events exclude engine polling and scheduler diagnostics', () => {
  const events = businessExecutionEvents([
    { id: 1, sourceType: 'TASK', logInfo: '节点开始执行: 4' },
    { id: 2, sourceType: 'SYSTEM', logInfo: '轮询完成' },
    { id: 3, sourceType: 'SYSTEM', logInfo: '线程池领取任务' },
  ])
  assert.deepEqual(events.map(item => item.id), [1])
})
```

- [ ] **Step 2: Run the focused test and verify failure**

Run: `node --test tests/workflow-execution.test.mjs`  
Expected: FAIL because the new functions are missing.

- [ ] **Step 3: Implement strict snapshot, variables and step-tree transforms**

```js
export function normalizeInterfaceSnapshot(snapshot, direction) {
  if (snapshot == null) return []
  if (!Array.isArray(snapshot)) throw new Error('接口快照格式不符合当前协议')
  return snapshot.map(item => ({
    direction,
    interfaceName: String(item?.interfaceName ?? ''),
    signalName: item?.signalName ?? null,
    payload: Object.hasOwn(item ?? {}, 'payload') ? item.payload : undefined,
  }))
}

export function visibleVariableEntries(variableSpace) {
  return Object.entries(variableSpace ?? {}).filter(([name]) => name !== '_triggerStates')
}

export function buildStepTree(steps) {
  const nodes = new Map((steps ?? []).map(step => [step.id, { ...step, children: [] }]))
  const roots = []
  for (const step of nodes.values()) {
    const parent = nodes.get(step.parentStepId)
    if (parent) parent.children.push(step)
    else roots.push(step)
  }
  return roots
}
```

- [ ] **Step 4: Implement runtime graph and business-event filtering**

`buildRuntimeGraph` joins by `nodeName` when available, then by `nodeIdRef` against the workflow node reference. It returns one node for every definition node and uses `WAITING` when no step exists. Nested child steps are attached to their parent subflow graph node through `children`.

```js
const TECHNICAL_EVENT_PATTERN = /轮询|线程池|调度器|队列领取|心跳|锁续期|扫描完成/

export function businessExecutionEvents(logs) {
  return (logs ?? []).filter(log =>
    log?.sourceType !== 'SYSTEM' && !TECHNICAL_EVENT_PATTERN.test(String(log?.logInfo ?? '')))
}
```

- [ ] **Step 5: Run the focused test and verify pass**

Run: `node --test tests/workflow-execution.test.mjs`  
Expected: PASS.

- [ ] **Step 6: Commit runtime transforms**

```bash
git add Frontend/src/utils/workflowExecution.js Frontend/tests/workflow-execution.test.mjs
git commit -m "feat(task-ui): add canonical runtime presentation model"
```

### Task 7: Split the task list while preserving create, binding and constraint flows

**Files:**
- Create: `Frontend/src/views/task/TaskList/components/TaskSummaryStrip.vue`
- Create: `Frontend/src/views/task/TaskList/components/TaskFilterBar.vue`
- Create: `Frontend/src/views/task/TaskList/components/TaskConstraintPanel.vue`
- Create: `Frontend/src/views/task/TaskList/components/TaskCreateDrawer.vue`
- Modify: `Frontend/src/views/task/TaskList/TaskList.vue`
- Modify: `Frontend/tests/workflow-full-chain-copy.test.mjs`

**Interfaces:**
- `TaskSummaryStrip` consumes summary and active status; emits `select-status`
- `TaskFilterBar` uses `v-model:keyword` and `v-model:status`; emits `query` and `reset`
- `TaskCreateDrawer` owns create-form presentation; parent keeps API requests and supplies workflows, preflight, routes, instances, models and constraints

- [ ] **Step 1: Add failing structure tests for preserved task creation capabilities**

```js
test('task list delegates create flow without losing binding and constraints', () => {
  const source = readSource('views/task/TaskList/TaskList.vue')
  const drawer = readSource('views/task/TaskList/components/TaskCreateDrawer.vue')
  assert.match(source, /TaskCreateDrawer/)
  assert.match(drawer, /TaskPreflightPanel/)
  assert.match(drawer, /TaskResourceBindingCanvas/)
  assert.match(drawer, /TaskConstraintPanel/)
  assert.match(drawer, /发布启用|已发布|可执行流程/)
})
```

- [ ] **Step 2: Run the structure test and verify failure**

Run: `node --test tests/workflow-full-chain-copy.test.mjs`  
Expected: FAIL because the extracted components do not exist.

- [ ] **Step 3: Extract summary and filters as stateless components**

Move current markup and CSS. Keep query state in `TaskList.vue`; emitted payloads must be primitives, not mutated prop objects.

- [ ] **Step 4: Extract the create drawer without changing payload creation**

Keep the existing create payload in the page orchestration layer:

```ts
const payload = {
  taskName: createForm.value.taskName,
  flowModelId: createForm.value.flowModelId,
  resourceMap: { formatVersion: 1, deviceBindings },
  taskConstraints: createForm.value.taskConstraints,
}
```

The drawer receives the current preflight result and binding model and emits granular updates. Do not rename backend request fields or move `ConstraintRuleEditor` from the global constraint component directory.

- [ ] **Step 5: Run focused tests and build**

Run: `node --test tests/workflow-full-chain-copy.test.mjs tests/taskResourceBindings.test.js tests/taskConstraintExpression.test.js`  
Expected: PASS.  
Run: `npm run build`  
Expected: PASS.

- [ ] **Step 6: Commit task-creation decomposition**

```bash
git add Frontend/src/views/task/TaskList Frontend/tests/workflow-full-chain-copy.test.mjs
git commit -m "refactor(task-ui): preserve task creation in page domain"
```

### Task 8: Build the execution drawer, step tree and canonical detail panels

**Files:**
- Create: `Frontend/src/views/task/TaskList/components/TaskExecutionDrawer.vue`
- Create: `Frontend/src/views/task/TaskList/components/TaskStepTree.vue`
- Create: `Frontend/src/views/task/TaskList/components/TaskStepDetail.vue`
- Create: `Frontend/src/views/task/TaskList/components/InterfaceSnapshotPanel.vue`
- Create: `Frontend/src/views/task/TaskList/components/TaskBusinessEventList.vue`
- Modify: `Frontend/src/views/task/TaskList/TaskList.vue`
- Remove: `Frontend/src/components/task/TaskExecutionView.vue`
- Modify: `Frontend/tests/workflow-full-chain-copy.test.mjs`
- Modify: `Frontend/tests/workflow-execution.test.mjs`

**Interfaces:**
- `TaskExecutionDrawer` consumes task, workflow definition, steps, logs, bindings, constraints and loading state
- Emits `refresh`, `terminate`, `select-step`, and `update:modelValue`
- `TaskStepTree` consumes `buildStepTree(steps)` output
- `InterfaceSnapshotPanel` consumes separate input/output snapshots and combines them for presentation

- [ ] **Step 1: Add failing component-contract tests**

```js
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
```

- [ ] **Step 2: Run focused tests and verify failure**

Run: `node --test tests/workflow-full-chain-copy.test.mjs tests/workflow-execution.test.mjs`  
Expected: FAIL because the new runtime components are absent.

- [ ] **Step 3: Implement step tree and canonical interface snapshot panel**

```ts
const inputRows = computed(() => normalizeInterfaceSnapshot(props.step?.interfaceInSnapshot, 'IN'))
const outputRows = computed(() => normalizeInterfaceSnapshot(props.step?.interfaceOutSnapshot, 'OUT'))
const rows = computed(() => [...inputRows.value, ...outputRows.value])
```

Render `signalName === null` as `尚未收到` for `IN` and `尚未发送` for `OUT`. Payload expands through `WorkflowJsonValue`; invalid snapshot format shows an `el-alert` and keeps the rest of the selected-step panel usable.

- [ ] **Step 4: Implement the task execution drawer and business event list**

Use the six confirmed tabs. `TaskBusinessEventList` consumes only `businessExecutionEvents(logs)` and labels records by task, node, device, constraint or adapter business source. It must not generate synthetic polling events.

Keep device-binding and effective-constraint displays from the existing monitor drawer. Rename user-facing `设备路由` to `设备绑定`; internal request payload names remain unchanged.

- [ ] **Step 5: Replace the old inline monitor in TaskList**

`TaskList.vue` retains request orchestration and passes normalized data into `TaskExecutionDrawer`. Delete the old `messageId` and `capabilityRef` snapshot markup and the unused `TaskExecutionView.vue`.

- [ ] **Step 6: Run focused tests and build**

Run: `node --test tests/workflow-full-chain-copy.test.mjs tests/workflow-execution.test.mjs`  
Expected: PASS.  
Run: `npm run build`  
Expected: PASS.

- [ ] **Step 7: Commit structured runtime detail**

```bash
git add Frontend/src/views/task/TaskList Frontend/src/components/task/TaskExecutionView.vue Frontend/tests
git commit -m "feat(task-ui): add structured execution details"
```

### Task 9: Add the graphical runtime topology and selected-step interaction

**Files:**
- Create: `Frontend/src/views/task/TaskList/components/TaskRuntimeGraph.vue`
- Modify: `Frontend/src/views/task/TaskList/components/TaskExecutionDrawer.vue`
- Modify: `Frontend/src/utils/workflowExecution.js`
- Modify: `Frontend/tests/workflow-execution.test.mjs`
- Modify: `Frontend/tests/workflow-full-chain-copy.test.mjs`

**Interfaces:**
- `TaskRuntimeGraph` consumes `{ workflow, steps, selectedStepId }`
- Emits `select-step` with a real step ID or `null` for an uncreated waiting node
- Consumes `buildRuntimeGraph(workflow, steps)`

- [ ] **Step 1: Add failing graph tests for statuses and subflow grouping**

```js
test('runtime graph groups child steps under the parent subflow step', () => {
  const graph = buildRuntimeGraph({
    nodesDef: [{ name: 'sub', nodeType: 'SUBFLOW_NODE' }],
    interfaceConnections: [],
  }, [
    { id: 10, nodeName: 'sub', nodeStatus: 'RUNNING', parentStepId: null, stepDepth: 0 },
    { id: 11, nodeName: 'child', nodeStatus: 'RUNNING', parentStepId: 10, stepDepth: 1 },
  ])
  assert.equal(graph.nodes[0].children[0].id, 11)
})

test('runtime graph source uses Vue Flow and emits selected step', () => {
  const source = readSource('views/task/TaskList/components/TaskRuntimeGraph.vue')
  assert.match(source, /VueFlow/)
  assert.match(source, /select-step/)
  assert.match(source, /WAITING/)
})
```

- [ ] **Step 2: Run focused tests and verify failure**

Run: `node --test tests/workflow-execution.test.mjs tests/workflow-full-chain-copy.test.mjs`  
Expected: FAIL until graph grouping and component exist.

- [ ] **Step 3: Implement the graph presentation model**

`buildRuntimeGraph` must:

- Preserve definition-node order.
- Join the latest matching step to each definition node.
- Map missing steps to `WAITING` without inventing IDs.
- Build interface edges from workflow interface connections and data edges from port connections.
- Attach `children` from `buildStepTree` to the parent `SUBFLOW_NODE` graph node.
- Return status classes only from `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `TERMINATING`, `TERMINATED`, or `WAITING`.

- [ ] **Step 4: Implement the Vue Flow runtime component**

The graph is read-only: no connect, drag persistence or delete actions. Reuse saved designer layout when available; otherwise run the existing DAG layout helper. Node clicks emit the matched step ID. Use status text and icon in addition to color.

- [ ] **Step 5: Integrate graph selection with TaskStepDetail**

The `执行流程图` tab uses a two-pane layout: graph on the left, selected step detail on the right. Selecting a waiting node shows definition metadata and `尚未创建执行步骤`; it does not fabricate snapshots.

- [ ] **Step 6: Run focused tests and build**

Run: `node --test tests/workflow-execution.test.mjs tests/workflow-full-chain-copy.test.mjs tests/workflowCanvas.test.mjs`  
Expected: PASS.  
Run: `npm run build`  
Expected: PASS.

- [ ] **Step 7: Commit runtime graph**

```bash
git add Frontend/src/views/task/TaskList/components/TaskRuntimeGraph.vue Frontend/src/views/task/TaskList/components/TaskExecutionDrawer.vue Frontend/src/utils/workflowExecution.js Frontend/tests
git commit -m "feat(task-ui): visualize live workflow execution"
```

### Task 10: Add scoped auto-refresh and complete regression verification

**Files:**
- Modify: `Frontend/src/views/task/TaskList/TaskList.vue`
- Modify: `Frontend/src/views/task/TaskList/components/TaskExecutionDrawer.vue`
- Create: `Frontend/src/views/task/components/WorkflowStatusBadge.vue`
- Create: `Frontend/src/views/task/components/WorkflowEmptyState.vue`
- Create: `Frontend/src/views/task/components/WorkflowJsonValue.vue`
- Modify: `Frontend/tests/workflow-full-chain-copy.test.mjs`
- Modify: `Frontend/tests/workflow-designer-structure.test.mjs`

**Interfaces:**
- Auto-refresh interval: exactly `1000` ms while drawer is open and task status is `RUNNING`
- Terminal statuses: `SUCCEEDED`, `FAILED`, `TERMINATED`
- Manual refresh remains available for all statuses

- [ ] **Step 1: Add failing refresh and copy tests**

```js
test('task runtime refreshes only the opened running task at one second', () => {
  const source = readSource('views/task/TaskList/TaskList.vue')
  assert.match(source, /1000/)
  assert.match(source, /monitorDrawerVisible/)
  assert.match(source, /RUNNING/)
  assert.match(source, /SUCCEEDED|FAILED|TERMINATED/)
})

test('ordinary task UI does not expose engine scheduling vocabulary', () => {
  const source = readSource('views/task/TaskList/components/TaskExecutionDrawer.vue')
  const template = source.split('</template>')[0]
  assert.doesNotMatch(template, /轮询|线程池|调度器|队列领取|心跳|锁续期/)
})
```

- [ ] **Step 2: Run structure tests and verify failure**

Run: `node --test tests/workflow-full-chain-copy.test.mjs tests/workflow-designer-structure.test.mjs`  
Expected: FAIL until the interval and final copy are present.

- [ ] **Step 3: Implement scoped refresh lifecycle**

```ts
const TERMINAL_TASK_STATUSES = new Set(['SUCCEEDED', 'FAILED', 'TERMINATED'])
let runtimeRefreshTimer: number | undefined

function syncRuntimeRefresh() {
  if (runtimeRefreshTimer != null) window.clearInterval(runtimeRefreshTimer)
  runtimeRefreshTimer = undefined
  if (!monitorDrawerVisible.value || activeTask.value?.taskStatus !== 'RUNNING') return
  runtimeRefreshTimer = window.setInterval(() => fetchLogsAndSnapshots(true), 1000)
}
```

Call it when the drawer opens, closes, active task changes or task status reaches a terminal state. Clear the timer in `onUnmounted`.

- [ ] **Step 4: Consolidate shared status, empty and JSON presentation**

Replace repeated local status maps only where the new task pages use them. Do not refactor unrelated device or constraint pages.

- [ ] **Step 5: Run the complete frontend test suite**

Run: `node --test tests` from `Frontend`  
Expected: all tests pass. Existing Vite WebSocket port `24678` collision messages may appear as noise; record them but do not classify them as test failures when Node reports zero failed tests.

- [ ] **Step 6: Run the production build**

Run: `npm run build` from `Frontend`  
Expected: build succeeds. Record existing large-chunk warnings and compare chunk sizes to the baseline; do not add a dependency solely to remove the warning.

- [ ] **Step 7: Perform browser visual verification**

Verify at 1440px, 1280px and 1024px:

- Workflow node labels, control-interface handles and data-port handles do not overlap.
- Inspector exposes exactly five tabs.
- Function-node custom interface controls appear; device and subflow nodes are read-only.
- Task create drawer still exposes preflight, binding and constraints.
- Runtime graph status, subflow grouping and selected-step detail remain legible.
- Task business events contain no polling or scheduler language.

- [ ] **Step 8: Commit final integration**

```bash
git add Frontend/src/views/task Frontend/src/utils Frontend/tests
git commit -m "feat(task-ui): complete workflow authoring and runtime loop"
```

---

## Plan Self-Review

- Spec coverage: every acceptance criterion maps to Tasks 1–10.
- Scope: frontend-only; backend APIs, execution engine and schema remain unchanged.
- Type consistency: snapshot items use `interfaceName`, `signalName`, optional `payload`; step hierarchy uses `parentStepId` and `stepDepth` throughout.
- Permission consistency: `canCustomizeControlInterfaces` is the single node-type gate; `canEditControlItem` additionally rejects `_system` items.
- Placeholder scan: no deferred implementation markers remain.
- Test strategy: every behavioral task starts with a focused failing test, then runs targeted verification before commit.
