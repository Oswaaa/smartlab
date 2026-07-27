# 工作流节点完整配置与执行链路Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不修改六个最终模型文件和数据库结构的前提下，完成工作流节点业务配置、双类型画布连接、后端权威校验、设备孪生变量映射、确定性动作执行、端口数据传递和保存加载往返

**Architecture:** 前端以“受保护的系统骨架＋可编辑的业务配置”生成和编辑节点，画布分别维护WORKFLOW接口连接与数据端口连接。后端以`WorkflowDefinitionCompiler`和`WorkflowService`作为保存前权威校验边界，以`WorkflowEngine`、动作执行器和`WorkflowExecutionOperations`完成真实运行时语义，所有任务变量、设备孪生属性和端口值写入`TASK_STEP.variable_space`

**Tech Stack:** Vue 3、TypeScript、Element Plus、Vue Flow、Node.js内置测试运行器、Java 21、Spring Boot 3.3.5、Jackson、JUnit 5、Mockito、Maven

## Global Constraints

- 不修改`Backend/src/main/resources/schemas`中的六个最终模型文件
- 不修改数据库表结构，不新增字段
- 引擎必需的接口、生命周期、系统触发器和系统动作不可删除，只允许修改业务配置并添加自定义变量、端口、触发器和动作
- 工作流模型只绑定`deviceModelId`，设备实例只在创建任务时写入`TASK.RESOURCE_MAP`
- 前端不允许用户手写JSON配置节点
- WORKFLOW接口连接传递`ACTIVE`并写入`interfaceConnections`
- 数据端口连接传递变量值并写入`portConnections`，端口连接本身不激活节点
- DEV_NODE的`NODE_TO_DEVICE`与`DEVICE_TO_NODE`连接由系统生成，画布不开放手工创建
- INTEGER、DOUBLE、STRING、BOOLEAN、JSON严格按声明类型处理，不进行隐式类型转换
- `actionName`按节点内唯一业务名称处理，`actionType`只允许`EMIT`或`UPDATE`
- 画布坐标只保存在浏览器布局存储中，不进入模型payload和数据库
- 保留现有`management`、`engine`、`global`目录边界，不重排项目架构
- 工作区已有未提交改动，实施时只编辑和暂存本任务明确列出的文件，不回退任何现有改动

---

## 文件结构与责任边界

### 前端

- Create: `Frontend/src/utils/workflowNodeDefinition.js`
  - 唯一负责节点系统模板、系统项标识、业务配置修改、能力参数序列化和节点级校验
- Test: `Frontend/tests/workflowNodeDefinition.test.mjs`
  - 纯函数单元测试，不依赖浏览器和Vue挂载
- Modify: `Frontend/src/utils/workflowCanvas.js`
  - 唯一负责Vue Flow节点/边映射、WORKFLOW连接、PORT连接、连接删除和保存payload清理
- Modify: `Frontend/tests/workflowCanvas.test.mjs`
  - 覆盖接口连接与端口连接的创建、类型校验、重命名和删除同步
- Create: `Frontend/src/components/task/workflow/WorkflowCanvasNode.vue`
  - 只负责画布节点展示和多接口、多端口Handle渲染
- Create: `Frontend/src/components/task/workflow/WorkflowTypedValueInput.vue`
  - 只负责按数据类型编辑能力参数和JSON键值列表
- Create: `Frontend/src/components/task/workflow/WorkflowVariablesPortsPanel.vue`
  - 只负责内部变量、属性映射和端口表单
- Create: `Frontend/src/components/task/workflow/WorkflowTriggersActionsPanel.vue`
  - 只负责动作、触发器及系统项锁定展示
- Create: `Frontend/src/components/task/workflow/WorkflowNodeInspector.vue`
  - 只负责四页签抽屉编排与对外事件，不直接操作画布
- Modify: `Frontend/src/views/task/WorkflowDesigner.vue`
  - 负责资源加载、节点选择、组件编排、连接落库、保存加载和页面级错误汇总

### 后端

- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCompiler.java`
  - 负责不依赖数据库的模型结构、系统骨架、端口类型、动作和触发器静态校验
- Modify: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowDefinitionCompilerTest.java`
  - 覆盖完整合法定义及所有静态拒绝路径
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowService.java`
  - 负责需要查询设备模型的能力、参数、属性映射校验及完整持久化往返
- Modify: `Backend/src/test/java/com/smartlab/management/service/db/workflow/WorkflowServiceTest.java`
  - 覆盖设备模型语义校验和SUBFLOW说明往返
- Modify: `Backend/src/main/java/com/smartlab/engine/constraint/ConstraintExpressionEvaluator.java`
  - 在保持约束布尔求值兼容的同时提供通用表达式值求解
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/action/WorkflowValueResolver.java`
  - 负责UPDATE表达式的路径、字面量和算术求值
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/action/UpdateWorkflowActionExecutor.java`
  - 负责UPDATE目标变量声明校验与结果生成
- Create: `Backend/src/test/java/com/smartlab/engine/workflow/action/UpdateWorkflowActionExecutorTest.java`
  - 覆盖表达式、嵌套payload路径、类型和值错误
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowExecutionOperations.java`
  - 增加运行时外部数据装载抽象
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/DefaultWorkflowExecutionOperations.java`
  - 根据任务资源映射读取设备孪生状态并生成节点变量更新
- Create: `Backend/src/test/java/com/smartlab/engine/workflow/DefaultWorkflowExecutionOperationsTest.java`
  - 覆盖实例解析、属性映射、缺失属性和类型错误
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowEngine.java`
  - 负责变量同步、触发器全量求值、UPDATE优先、单EMIT约束和端口传播
- Create: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowEngineExecutionTest.java`
  - 覆盖动作顺序、多EMIT失败、分支路由、端口传播和任务失败收敛

---

### Task 1: 建立节点系统模板与受保护配置模型

**Files:**
- Create: `Frontend/src/utils/workflowNodeDefinition.js`
- Test: `Frontend/tests/workflowNodeDefinition.test.mjs`

**Interfaces:**
- Consumes: 设备模型对象中的`capabilities[]`、`attributes[]`
- Produces:
  - `createFunctionNode(functionType: string, name: string): object`
  - `createDeviceNode(model: object, name: string): object`
  - `createSubflowNode(workflow: object, name: string): object`
  - `isSystemItem(item: object): boolean`
  - `replaceCapability(node: object, capability: object): object`
  - `removeVariable(node: object, variableName: string, portConnections: object[]): object`
  - `removePort(node: object, portName: string, portConnections: object[]): { node: object, portConnections: object[] }`
  - `validateNodeDefinition(node: object, context: object): Array<{ path: string, message: string }>`

- [ ] **Step 1: 写系统模板失败测试**

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  createFunctionNode,
  createDeviceNode,
  createSubflowNode,
  isSystemItem
} from '../src/utils/workflowNodeDefinition.js'

test('BRANCH生成真假两个系统出口及互斥触发器', () => {
  const node = createFunctionNode('BRANCH', 'branch-1')
  assert.deepEqual(node.interfaces.map(item => item.name), [
    'Interface_workflow_in',
    'Interface_true_out',
    'Interface_false_out'
  ])
  assert.deepEqual(node.actions.map(item => [item.actionName, item.actionType]), [
    ['emitTrue', 'EMIT'],
    ['emitFalse', 'EMIT']
  ])
  assert.equal(node.interfaces[0].bindingTriggers.length, 2)
  assert.equal(node.interfaces[0].bindingTriggers[0].condition.object, 'expression')
  assert.equal(node.interfaces[0].bindingTriggers[0].condition.threshold, true)
  assert.equal(node.interfaces[0].bindingTriggers[1].condition.threshold, false)
  assert.ok(node.actions.every(isSystemItem))
})

test('DEV_NODE生成状态机调用与完成回传系统骨架', () => {
  const model = { id: 7, capabilities: [{ capabilityName: 'heat', parameters: [] }] }
  const node = createDeviceNode(model, 'heater')
  assert.equal(node.deviceModelId, 7)
  assert.deepEqual(node.interfaces.map(item => item.name), [
    'Interface_workflow_in',
    'Interface_state_out',
    'Interface_state_in',
    'Interface_workflow_out'
  ])
  assert.deepEqual(node.actions.map(item => item.actionName), ['startDevice', 'completeNode'])
  assert.equal(node.lifecycle.initialState, 'IDLE')
})

test('SUBFLOW_NODE没有允许绕过子流程的系统EMIT动作', () => {
  const node = createSubflowNode({ id: 9, flowName: '开盖' }, 'open-lid')
  assert.equal(node.subFlowModelId, 9)
  assert.deepEqual(node.actions, [])
  assert.deepEqual(node.interfaces.map(item => item.name), [
    'Interface_workflow_in',
    'Interface_workflow_out'
  ])
})
```

- [ ] **Step 2: 运行测试并确认因模块不存在而失败**

Run: `cd Frontend; node --test tests/workflowNodeDefinition.test.mjs`

Expected: FAIL，错误包含`Cannot find module workflowNodeDefinition.js`

- [ ] **Step 3: 实现节点模板与系统项标识**

在`workflowNodeDefinition.js`中使用非持久化字段`_system: true`和`_systemKey`标识前端锁定项；`sanitizeWorkflowPayload`会统一移除下划线字段

```js
const system = (systemKey, value) => ({ ...value, _system: true, _systemKey: systemKey })

export function isSystemItem(item) {
  return item?._system === true
}

export function createFunctionNode(functionType, name) {
  if (functionType === 'BRANCH') {
    const emitTrue = system('branch.emitTrue', {
      actionName: 'emitTrue',
      actionType: 'EMIT',
      targetInterfaceName: 'Interface_true_out',
      signalName: 'ACTIVE'
    })
    const emitFalse = system('branch.emitFalse', {
      actionName: 'emitFalse',
      actionType: 'EMIT',
      targetInterfaceName: 'Interface_false_out',
      signalName: 'ACTIVE'
    })
    return {
      name,
      nodeType: 'FUNC_NODE',
      functionType,
      expression: '',
      internalVariables: [],
      lifecycle: lockedLifecycle(),
      interfaces: [
        system('branch.workflowIn', workflowInterface('Interface_workflow_in', 'IN', [
          trigger('branch.true', 'expression', 'EQUALS', true, 'emitTrue'),
          trigger('branch.false', 'expression', 'EQUALS', false, 'emitFalse')
        ])),
        system('branch.trueOut', workflowInterface('Interface_true_out', 'OUT')),
        system('branch.falseOut', workflowInterface('Interface_false_out', 'OUT'))
      ],
      ports: [],
      actions: [emitTrue, emitFalse]
    }
  }
  return createLinearFunctionNode(functionType, name)
}
```

START、END、AGGREGATE、DEV_NODE和SUBFLOW_NODE严格按确认规格生成；系统触发器、接口、动作和生命周期都带稳定`_systemKey`

- [ ] **Step 4: 写能力切换、变量引用保护和节点校验失败测试**

```js
test('切换能力只保留同名同类型参数', () => {
  const node = {
    capability: {
      capabilityName: 'old',
      capabilityParameters: { speed: 3, mode: 'AUTO', obsolete: true }
    }
  }
  const next = replaceCapability(node, {
    capabilityName: 'new',
    parameters: [
      { parameterName: 'speed', dataType: 'INTEGER' },
      { parameterName: 'mode', dataType: 'BOOLEAN' }
    ]
  })
  assert.deepEqual(next.capability.capabilityParameters, { speed: 3 })
})

test('被端口引用的变量不能直接删除', () => {
  const node = {
    internalVariables: [{ name: 'temperature', dataType: 'DOUBLE' }],
    ports: [{ name: 'temperatureOut', direction: 'OUT', internalVariableName: 'temperature' }]
  }
  assert.throws(
    () => removeVariable(node, 'temperature', []),
    /变量temperature仍被端口temperatureOut引用/
  )
})

test('系统动作不能被业务删除', () => {
  const node = createFunctionNode('START', 'start')
  assert.throws(
    () => removeAction(node, 'emitActive'),
    /系统动作emitActive不可删除/
  )
})
```

- [ ] **Step 5: 实现业务修改和节点级校验**

`validateNodeDefinition`返回结构化错误，不弹消息，不修改原对象。至少校验唯一名称、变量类型、端口引用、EMIT目标、UPDATE目标、触发器动作引用、系统项完整性、BRANCH真假出口和能力参数本地类型

```js
export function validateNodeDefinition(node, context = {}) {
  const errors = []
  uniqueErrors(errors, node.internalVariables, 'name', 'internalVariables')
  uniqueErrors(errors, node.ports, 'name', 'ports')
  uniqueErrors(errors, node.interfaces, 'name', 'interfaces')
  uniqueErrors(errors, node.actions, 'actionName', 'actions')
  validateSystemSkeleton(node, errors)
  validatePorts(node, errors)
  validateActions(node, errors)
  validateTriggers(node, errors)
  if (node.nodeType === 'DEV_NODE') validateDeviceConfiguration(node, context.deviceModel, errors)
  return errors
}
```

- [ ] **Step 6: 运行前端模板测试**

Run: `cd Frontend; node --test tests/workflowNodeDefinition.test.mjs`

Expected: PASS，所有节点模板、锁定规则、能力参数和引用保护测试通过

- [ ] **Step 7: 暂存本任务文件并提交**

```bash
git add Frontend/src/utils/workflowNodeDefinition.js Frontend/tests/workflowNodeDefinition.test.mjs
git commit -m "feat: define protected workflow node templates"
```

---

### Task 2: 支持WORKFLOW接口与数据端口双连接

**Files:**
- Modify: `Frontend/src/utils/workflowCanvas.js`
- Modify: `Frontend/tests/workflowCanvas.test.mjs`

**Interfaces:**
- Consumes: Task 1生成的`interfaces[]`、`ports[]`
- Produces:
  - `interfaceHandleId(interfaceName: string): string`
  - `portHandleId(portName: string): string`
  - `parseHandleId(handleId: string): { kind: 'INTERFACE'|'PORT', name: string }`
  - `buildFlowEdges(interfaceConnections: object[], portConnections: object[]): object[]`
  - `createCanvasConnection(args: object): { collection: 'interfaceConnections'|'portConnections', value: object }`
  - `removeCanvasEdge(edge: object, interfaceConnections: object[], portConnections: object[]): object`

- [ ] **Step 1: 写双连接失败测试**

```js
test('接口边为蓝色实线且保留具体接口Handle', () => {
  const edges = buildFlowEdges([{
    connectionType: 'NODE_TO_NODE',
    source: { nodeName: 'branch', interfaceName: 'Interface_true_out' },
    target: { nodeName: 'heater', interfaceName: 'Interface_workflow_in' }
  }], [])
  assert.equal(edges[0].data.connectionKind, 'INTERFACE')
  assert.equal(edges[0].sourceHandle, 'interface:Interface_true_out')
  assert.equal(edges[0].style.stroke, '#3276d2')
  assert.equal(edges[0].style.strokeDasharray, undefined)
})

test('端口边为紫色虚线', () => {
  const edges = buildFlowEdges([], [{
    source: { nodeName: 'sensor', portName: 'temperatureOut' },
    target: { nodeName: 'heater', portName: 'targetIn' }
  }])
  assert.equal(edges[0].data.connectionKind, 'PORT')
  assert.equal(edges[0].sourceHandle, 'port:temperatureOut')
  assert.equal(edges[0].targetHandle, 'port:targetIn')
  assert.equal(edges[0].style.stroke, '#7c4dce')
  assert.equal(edges[0].style.strokeDasharray, '7 5')
})

test('禁止接口连接点与端口连接点交叉连接', () => {
  assert.throws(() => createCanvasConnection({
    sourceNodeName: 'a',
    sourceHandle: 'interface:Interface_workflow_out',
    targetNodeName: 'b',
    targetHandle: 'port:valueIn',
    nodes: canvasNodes
  }), /接口连接点不能连接数据端口/)
})
```

- [ ] **Step 2: 运行测试并确认旧实现不支持PORT边**

Run: `cd Frontend; node --test tests/workflowCanvas.test.mjs`

Expected: FAIL，至少包含`buildFlowEdges`参数或`connectionKind`断言失败

- [ ] **Step 3: 实现Handle编码、双边映射和方向校验**

```js
export const interfaceHandleId = name => `interface:${name}`
export const portHandleId = name => `port:${name}`

export function parseHandleId(handleId) {
  const [prefix, ...parts] = String(handleId || '').split(':')
  if (prefix === 'interface') return { kind: 'INTERFACE', name: parts.join(':') }
  if (prefix === 'port') return { kind: 'PORT', name: parts.join(':') }
  throw new Error(`未知连接点:${handleId}`)
}

export function createCanvasConnection(args) {
  const sourceHandle = parseHandleId(args.sourceHandle)
  const targetHandle = parseHandleId(args.targetHandle)
  if (sourceHandle.kind !== targetHandle.kind) throw new Error('接口连接点不能连接数据端口')
  return sourceHandle.kind === 'INTERFACE'
    ? createInterfaceConnection(args, sourceHandle.name, targetHandle.name)
    : createPortConnection(args, sourceHandle.name, targetHandle.name)
}
```

接口连接校验WORKFLOW/OUT→WORKFLOW/IN；端口连接校验OUT→IN、两侧变量存在且`dataType`完全一致；重复边按具体接口名或端口名判断

- [ ] **Step 4: 写重命名、端口删除和payload清理测试**

```js
test('节点重命名同步两类连接', () => {
  const interfaces = [{ source: { nodeName: 'old' }, target: { nodeName: 'b' } }]
  const ports = [{ source: { nodeName: 'old' }, target: { nodeName: 'c' } }]
  renameNodeConnections(interfaces, 'old', 'next')
  renameNodeConnections(ports, 'old', 'next')
  assert.equal(interfaces[0].source.nodeName, 'next')
  assert.equal(ports[0].source.nodeName, 'next')
})

test('保存payload递归移除系统标识与画布字段', () => {
  const payload = sanitizeWorkflowPayload({
    nodesDef: [{
      name: 'start',
      _system: true,
      actions: [{ actionName: 'emitActive', _systemKey: 'start.emitActive' }],
      position: { x: 10, y: 20 }
    }]
  })
  assert.deepEqual(payload.nodesDef[0], {
    name: 'start',
    actions: [{ actionName: 'emitActive' }]
  })
})
```

- [ ] **Step 5: 实现双集合删除和递归清理**

`sanitizeWorkflowPayload`递归遍历对象和数组，删除所有以下划线开头的编辑器字段及任意层级的`position`

- [ ] **Step 6: 运行画布纯函数测试**

Run: `cd Frontend; node --test tests/workflowCanvas.test.mjs`

Expected: PASS

- [ ] **Step 7: 暂存本任务文件并提交**

```bash
git add Frontend/src/utils/workflowCanvas.js Frontend/tests/workflowCanvas.test.mjs
git commit -m "feat: support workflow and data port edges"
```

---

### Task 3: 实现类型化参数、变量和端口编辑组件

**Files:**
- Create: `Frontend/src/components/task/workflow/WorkflowTypedValueInput.vue`
- Create: `Frontend/src/components/task/workflow/WorkflowVariablesPortsPanel.vue`
- Modify: `Frontend/tests/workflowNodeDefinition.test.mjs`

**Interfaces:**
- Consumes:
  - `modelValue: unknown`
  - `dataType: 'INTEGER'|'DOUBLE'|'STRING'|'BOOLEAN'|'JSON'`
  - `node: object`
  - `deviceAttributes: object[]`
  - `portConnections: object[]`
- Produces:
  - `WorkflowTypedValueInput`事件`update:modelValue`
  - `WorkflowVariablesPortsPanel`事件`update:node`
  - `WorkflowVariablesPortsPanel`事件`update:portConnections`
  - `WorkflowVariablesPortsPanel`事件`validation-change`

- [ ] **Step 1: 写能力参数规范化失败测试**

```js
test('能力参数按声明类型序列化且拒绝隐式转换', () => {
  assert.equal(normalizeTypedValue('INTEGER', 3), 3)
  assert.equal(normalizeTypedValue('DOUBLE', 3.5), 3.5)
  assert.equal(normalizeTypedValue('BOOLEAN', false), false)
  assert.deepEqual(normalizeTypedValue('JSON', [{ key: 'mode', value: 'AUTO' }]), { mode: 'AUTO' })
  assert.throws(() => normalizeTypedValue('INTEGER', '3'), /INTEGER参数必须是整数/)
})
```

- [ ] **Step 2: 运行测试确认`normalizeTypedValue`尚不存在**

Run: `cd Frontend; node --test tests/workflowNodeDefinition.test.mjs`

Expected: FAIL，错误包含`normalizeTypedValue is not a function`

- [ ] **Step 3: 实现类型化输入组件**

组件选择规则固定为：

```vue
<el-input-number v-if="dataType==='INTEGER'" :model-value="modelValue" :precision="0" @update:model-value="emitValue" />
<el-input-number v-else-if="dataType==='DOUBLE'" :model-value="modelValue" @update:model-value="emitValue" />
<el-switch v-else-if="dataType==='BOOLEAN'" :model-value="modelValue" @update:model-value="emitValue" />
<el-input v-else-if="dataType==='STRING'" :model-value="modelValue" @update:model-value="emitValue" />
<div v-else class="json-entry-list">
  <div v-for="(entry,index) in jsonEntries" :key="entry.id" class="json-entry-row">
    <el-input v-model="entry.key" placeholder="键" />
    <el-input v-model="entry.value" placeholder="值" />
    <el-button link type="danger" @click="removeEntry(index)">删除</el-button>
  </div>
  <el-button @click="addEntry">添加键值</el-button>
</div>
```

JSON控件对外输出对象；重复键、空键在本组件显示错误并不发出新值

- [ ] **Step 4: 实现变量与端口面板**

面板实现以下不可绕过规则：

- DEV_NODE的`attributesMapping`使用设备模型属性下拉选项
- 选择设备属性后将变量`dataType`同步为属性`dataType`
- 非DEV_NODE不显示`attributesMapping`
- 端口`internalVariableName`只能选择当前节点变量
- 删除变量调用`removeVariable`
- 删除端口调用`removePort`，若存在连接先通过组件事件交给页面确认
- 系统项不在此面板中编辑

- [ ] **Step 5: 运行模板和序列化测试**

Run: `cd Frontend; node --test tests/workflowNodeDefinition.test.mjs`

Expected: PASS

- [ ] **Step 6: 运行前端构建检查Vue模板与TypeScript类型**

Run: `cd Frontend; npm run build`

Expected: PASS，不出现Vue编译错误、未解析组件或类型错误

- [ ] **Step 7: 暂存本任务文件并提交**

```bash
git add Frontend/src/components/task/workflow/WorkflowTypedValueInput.vue Frontend/src/components/task/workflow/WorkflowVariablesPortsPanel.vue Frontend/src/utils/workflowNodeDefinition.js Frontend/tests/workflowNodeDefinition.test.mjs
git commit -m "feat: add typed workflow variable and port forms"
```

---

### Task 4: 实现结构化动作、触发器与四页签节点抽屉

**Files:**
- Create: `Frontend/src/components/task/workflow/WorkflowTriggersActionsPanel.vue`
- Create: `Frontend/src/components/task/workflow/WorkflowNodeInspector.vue`
- Modify: `Frontend/tests/workflowNodeDefinition.test.mjs`

**Interfaces:**
- Consumes:
  - Task 1的`validateNodeDefinition`、`isSystemItem`
  - Task 3的类型化输入和变量端口面板
- Produces:
  - `WorkflowTriggersActionsPanel`事件`update:node`
  - `WorkflowNodeInspector`事件`rename`
  - `WorkflowNodeInspector`事件`update:node`
  - `WorkflowNodeInspector`事件`update:portConnections`
  - `WorkflowNodeInspector`事件`remove-node`
  - `WorkflowNodeInspector`事件`close`

- [ ] **Step 1: 写动作触发器校验失败测试**

```js
test('EMIT必须指向本节点OUT接口且信号在allowedSignals中', () => {
  const node = createFunctionNode('START', 'start')
  node.actions.push({
    actionName: 'badEmit',
    actionType: 'EMIT',
    targetInterfaceName: 'missing',
    signalName: 'UNKNOWN'
  })
  const messages = validateNodeDefinition(node).map(item => item.message)
  assert.ok(messages.includes('EMIT动作badEmit引用的输出接口missing不存在'))
})

test('UPDATE必须引用真实内部变量且表达式非空', () => {
  const node = createFunctionNode('AGGREGATE', 'join')
  node.actions.push({
    actionName: 'updateCount',
    actionType: 'UPDATE',
    internalVariableName: 'count',
    valueExpression: ''
  })
  const messages = validateNodeDefinition(node).map(item => item.message)
  assert.ok(messages.includes('UPDATE动作updateCount引用的内部变量count不存在'))
  assert.ok(messages.includes('UPDATE动作updateCount的valueExpression不能为空'))
})

test('触发器只能挂在IN接口且动作引用必须存在', () => {
  const node = createFunctionNode('START', 'start')
  node.interfaces[0].bindingTriggers = [{
    condition: { object: 'inputSignalName', operator: 'EQUALS', threshold: 'ACTIVE' },
    action: 'missingAction'
  }]
  const messages = validateNodeDefinition(node).map(item => item.message)
  assert.ok(messages.some(message => message.includes('OUT接口不能声明bindingTriggers')))
  assert.ok(messages.some(message => message.includes('missingAction不存在')))
})
```

- [ ] **Step 2: 运行测试并确认动作校验尚不完整**

Run: `cd Frontend; node --test tests/workflowNodeDefinition.test.mjs`

Expected: FAIL，至少一个动作或触发器断言失败

- [ ] **Step 3: 实现动作表单**

动作表单字段固定为：

```text
公共字段: actionName, actionType
EMIT字段: targetInterfaceName, signalName
UPDATE字段: internalVariableName, valueExpression
```

系统动作使用锁图标和只读控件；SUBFLOW_NODE隐藏“新增EMIT”入口；用户动作名称在节点内唯一

- [ ] **Step 4: 实现触发器表单**

触发器按每个IN接口分组；`condition.object`的选择项为：

```js
[
  'inputSignalName',
  'inputPayload.stateName',
  'nodeLifecycleState',
  'expression',
  ...node.internalVariables.map(item => item.name)
]
```

同时保留可筛选可创建的`inputPayload.<字段路径>`输入。`condition.operator`只使用后端已有`WorkflowConditionEvaluator`支持的操作符。`action`下拉只列出当前节点动作名称

- [ ] **Step 5: 实现四页签抽屉**

```vue
<el-tabs v-model="activeTab">
  <el-tab-pane label="基础配置" name="basic"><section class="basic-configuration"><WorkflowTypedValueInput /></section></el-tab-pane>
  <el-tab-pane label="变量与端口" name="variables"><WorkflowVariablesPortsPanel /></el-tab-pane>
  <el-tab-pane label="触发器与动作" name="triggers"><WorkflowTriggersActionsPanel /></el-tab-pane>
  <el-tab-pane label="接口与生命周期" name="interfaces"><section class="read-only-system-skeleton"><el-table :data="node.interfaces" /></section></el-tab-pane>
</el-tabs>
```

基础配置按节点类型显示能力参数、表达式或子流程说明；接口与生命周期页签只读展示系统/自定义标记。错误按`path`分配到对应页签，并在页签标题显示数量

- [ ] **Step 6: 运行纯函数测试和前端构建**

Run: `cd Frontend; node --test tests/workflowNodeDefinition.test.mjs; npm run build`

Expected: 两个命令均PASS

- [ ] **Step 7: 暂存本任务文件并提交**

```bash
git add Frontend/src/components/task/workflow/WorkflowTriggersActionsPanel.vue Frontend/src/components/task/workflow/WorkflowNodeInspector.vue Frontend/src/utils/workflowNodeDefinition.js Frontend/tests/workflowNodeDefinition.test.mjs
git commit -m "feat: add structured workflow action inspector"
```

---

### Task 5: 将完整节点配置与双连接接入流程设计页面

**Files:**
- Create: `Frontend/src/components/task/workflow/WorkflowCanvasNode.vue`
- Modify: `Frontend/src/views/task/WorkflowDesigner.vue`
- Modify: `Frontend/src/utils/workflowCanvas.js`
- Modify: `Frontend/tests/workflowCanvas.test.mjs`

**Interfaces:**
- Consumes:
  - `WorkflowCanvasNode`接收`node`和`issues`
  - `WorkflowNodeInspector`接收选中节点、设备模型和端口连接
  - Task 2的`createCanvasConnection`
- Produces:
  - 页面保存payload完整包含`nodesDef`、`interfaceConnections`、`portConnections`
  - 画布删除边同步修改正确集合
  - 页面保存前执行全部节点校验

- [ ] **Step 1: 写多Handle映射失败测试**

```js
test('画布节点数据包含全部WORKFLOW接口和数据端口', () => {
  const nodes = buildFlowNodes([{
    name: 'branch',
    nodeType: 'FUNC_NODE',
    interfaces: [
      { name: 'Interface_workflow_in', direction: 'IN', interfaceType: 'WORKFLOW' },
      { name: 'Interface_true_out', direction: 'OUT', interfaceType: 'WORKFLOW' },
      { name: 'Interface_false_out', direction: 'OUT', interfaceType: 'WORKFLOW' }
    ],
    ports: [
      { name: 'temperatureIn', direction: 'IN', internalVariableName: 'temperature' },
      { name: 'decisionOut', direction: 'OUT', internalVariableName: 'decision' }
    ]
  }])
  assert.equal(nodes[0].data.interfaces.length, 3)
  assert.equal(nodes[0].data.ports.length, 2)
})
```

- [ ] **Step 2: 运行画布测试并确认旧`buildFlowNodes`未传递接口与端口**

Run: `cd Frontend; node --test tests/workflowCanvas.test.mjs`

Expected: FAIL，错误包含`Cannot read properties of undefined`或长度断言失败

- [ ] **Step 3: 实现画布节点组件**

`WorkflowCanvasNode.vue`分别渲染：

- 左侧WORKFLOW/IN Handle
- 右侧WORKFLOW/OUT Handle，BRANCH显示两个独立出口标签
- 下方PORT/IN Handle
- 上方PORT/OUT Handle
- 错误徽标、节点类型、能力或表达式摘要

每个Handle使用Task 2的编码方法，不再通过“第一个WORKFLOW接口”推断连接端点

- [ ] **Step 4: 替换页面内联节点与抽屉**

`WorkflowDesigner.vue`移除内联节点模板和旧单页抽屉，改为：

```vue
<template #node-workflow="slotProps">
  <WorkflowCanvasNode
    :node="nodeByName(slotProps.data.nodeName)"
    :selected="slotProps.selected"
    :issues="nodeIssues(slotProps.data.nodeName)"
  />
</template>

<WorkflowNodeInspector
  v-model:visible="nodeDrawerVisible"
  :node="selectedNode"
  :device-model="selectedDeviceModel"
  :port-connections="form.portConnections"
  @rename="renameSelectedNode"
  @update:node="replaceSelectedNode"
  @update:port-connections="replacePortConnections"
  @remove-node="removeSelectedNode"
/>
```

- [ ] **Step 5: 接入双连接和保存前校验**

`connectNodes(connection)`调用`createCanvasConnection`并根据`collection`写入对应数组。`removeDeletedEdges(edges)`根据`edge.data.connectionKind`删除正确集合

`save()`执行：

```js
const errors = form.nodesDef.flatMap(node =>
  validateNodeDefinition(node, { deviceModel: modelById(node.deviceModelId) })
)
if (errors.length) {
  selectedNodeName.value = errors[0].nodeName
  nodeDrawerVisible.value = true
  throw new Error(errors[0].message)
}
const payload = sanitizeWorkflowPayload(form)
await axios.post('/api/workflow/save', payload)
```

错误用`ElMessage.error`显示并定位节点，不发送HTTP请求

- [ ] **Step 6: 实现删除同步**

删除节点同时删除两类连接。删除端口前若存在连接，展示具体连接列表并要求确认；确认后删除端口和对应`portConnections`

- [ ] **Step 7: 运行前端单元测试与构建**

Run: `cd Frontend; node --test tests/workflowNodeDefinition.test.mjs tests/workflowCanvas.test.mjs; npm run build`

Expected: PASS

- [ ] **Step 8: 暂存本任务文件并提交**

```bash
git add Frontend/src/components/task/workflow/WorkflowCanvasNode.vue Frontend/src/views/task/WorkflowDesigner.vue Frontend/src/utils/workflowCanvas.js Frontend/tests/workflowCanvas.test.mjs
git commit -m "feat: integrate complete workflow node editor"
```

---

### Task 6: 增强后端模型编译与数据库语义校验

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCompiler.java`
- Modify: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowDefinitionCompilerTest.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowService.java`
- Modify: `Backend/src/test/java/com/smartlab/management/service/db/workflow/WorkflowServiceTest.java`

**Interfaces:**
- Consumes: 保存请求中的完整工作流定义、`DeviceModelService`返回的设备模型
- Produces:
  - `WorkflowDefinitionCompiler.compile(WorkflowSaveRequest request): CompiledWorkflow`
  - `WorkflowService.validateDeviceConfiguration(WorkflowSaveRequest request): void`
  - SUBFLOW_NODE的`subFlowModelDescription`通过`FlowNode.capability`JSONB完整往返

- [ ] **Step 1: 写编译器静态校验失败测试**

新增以下具名测试：

```java
@Test
void rejectsPortConnectionWhenVariableTypesDiffer() {
    WorkflowSaveRequest request = validDefinition();
    ObjectNode connection = ((ArrayNode) request.getPortConnections()).addObject();
    connection.putObject("source").put("nodeName", "source").put("portName", "valueOut");
    connection.putObject("target").put("nodeName", "target").put("portName", "valueIn");
    ObjectNode sourceNode = (ObjectNode) request.getNodesDef().get(0);
    ObjectNode targetNode = (ObjectNode) request.getNodesDef().get(1);
    ((ObjectNode) sourceNode.withArray("internalVariables").get(0)).put("dataType", "DOUBLE");
    ((ObjectNode) targetNode.withArray("internalVariables").get(0)).put("dataType", "STRING");

    assertThatThrownBy(() -> compiler.compile(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("portConnections[0]")
            .hasMessageContaining("数据类型不一致");
}

@Test
void rejectsBranchWithoutFalseOutput() {
    WorkflowSaveRequest request = validBranchDefinition();
    ObjectNode branch = (ObjectNode) request.getNodesDef().get(1);
    removeNamedItem(branch.withArray("interfaces"), "Interface_false_out");
    assertThatThrownBy(() -> compiler.compile(request))
            .hasMessageContaining("节点branch")
            .hasMessageContaining("Interface_false_out");
}

@Test
void rejectsEmitToInputInterface() {
    WorkflowSaveRequest request = validDefinition();
    ObjectNode start = (ObjectNode) request.getNodesDef().get(0);
    ((ObjectNode) start.withArray("actions").get(0))
            .put("targetInterfaceName", "Interface_workflow_in");
    assertThatThrownBy(() -> compiler.compile(request))
            .hasMessageContaining("EMIT")
            .hasMessageContaining("OUT接口");
}
```

- [ ] **Step 2: 运行编译器测试并确认缺失校验导致失败**

Run: `cd Backend; mvn -Dtest=WorkflowDefinitionCompilerTest test`

Expected: FAIL，新增拒绝测试未抛异常或错误路径不明确

- [ ] **Step 3: 在编译器中建立显式索引并完成静态校验**

为每个节点建立：

```java
record NodeIndex(
        String nodeName,
        String nodeType,
        Map<String, JsonNode> variables,
        Map<String, JsonNode> interfaces,
        Map<String, JsonNode> ports,
        Map<String, JsonNode> actions) {}
```

新增私有方法：

```java
private NodeIndex validateAndIndexNode(JsonNode node, String path)
private void validateSystemSkeleton(NodeIndex index, JsonNode node, String path)
private void validateActions(NodeIndex index, JsonNode node, String path)
private void validateTriggers(NodeIndex index, JsonNode node, String path)
private void validatePortConnections(JsonNode connections, Map<String, NodeIndex> nodes)
private void validateInterfaceConnections(JsonNode connections, Map<String, NodeIndex> nodes)
private void requireDataType(String dataType, String path)
```

所有异常使用`节点<name>.<field path>: <原因>`格式；模型专用的`actionName`枚举冲突不作为拒绝依据，实际读取`actionType`

- [ ] **Step 4: 运行编译器测试**

Run: `cd Backend; mvn -Dtest=WorkflowDefinitionCompilerTest test`

Expected: PASS

- [ ] **Step 5: 写设备模型语义与SUBFLOW往返失败测试**

```java
@Test
void rejectsUnknownCapabilityAndExtraParameter() {
    WorkflowSaveRequest request = validDeviceWorkflow();
    ((ObjectNode) request.getNodesDef().get(1)).with("capability")
            .put("capabilityName", "missing")
            .with("capabilityParameters").put("extra", 1);

    assertThatThrownBy(() -> workflowService.saveDefinition(request))
            .hasMessageContaining("节点heater.capability.capabilityName")
            .hasMessageContaining("missing");
}

@Test
void rejectsAttributeMappingWithDifferentDataType() {
    WorkflowSaveRequest request = validDeviceWorkflow();
    ((ObjectNode) request.getNodesDef().get(1)).withArray("internalVariables").get(0)
            .put("name", "temperature")
            .put("dataType", "STRING")
            .put("attributesMapping", "temperature");
    assertThatThrownBy(() -> workflowService.saveDefinition(request))
            .hasMessageContaining("attributesMapping")
            .hasMessageContaining("数据类型不一致");
}

@Test
void roundTripsSubFlowModelDescriptionThroughCapabilityJson() {
    WorkflowSaveRequest request = validSubflowWorkflow();
    ((ObjectNode) request.getNodesDef().get(1)).put("subFlowModelDescription", "机械臂打开装置");
    WorkflowDetailResponse saved = workflowService.saveDefinition(request);
    WorkflowDetailResponse loaded = workflowService.getDefinition(saved.getId());
    assertThat(loaded.getNodesDef().get(1).path("subFlowModelDescription").asText())
            .isEqualTo("机械臂打开装置");
}
```

- [ ] **Step 6: 运行服务测试并确认失败**

Run: `cd Backend; mvn -Dtest=WorkflowServiceTest test`

Expected: FAIL，设备模型语义未校验或子流程说明丢失

- [ ] **Step 7: 实现数据库语义校验和完整往返**

`WorkflowService`在编译通过后、持久化前查询每个DEV_NODE的设备模型并校验：

- capabilityName存在
- 所有能力参数均存在且没有多余参数
- 参数值符合数据类型
- attributesMapping存在于模型属性中
- 映射变量与属性dataType一致

SUBFLOW_NODE保存时：

```java
ObjectNode capability = JsonNodeSupport.objectNode();
capability.put("subFlowModelId", node.path("subFlowModelId").asLong());
capability.put("subFlowModelDescription", node.path("subFlowModelDescription").asText(""));
entity.setCapability(capability);
```

读取时从`capability`恢复`subFlowModelId`和`subFlowModelDescription`

- [ ] **Step 8: 运行编译器和服务测试**

Run: `cd Backend; mvn -Dtest=WorkflowDefinitionCompilerTest,WorkflowServiceTest test`

Expected: PASS

- [ ] **Step 9: 暂存本任务文件并提交**

```bash
git add Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCompiler.java Backend/src/test/java/com/smartlab/engine/workflow/WorkflowDefinitionCompilerTest.java Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowService.java Backend/src/test/java/com/smartlab/management/service/db/workflow/WorkflowServiceTest.java
git commit -m "feat: validate complete workflow definitions"
```

---

### Task 7: 提供真实UPDATE表达式和值类型校验

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/engine/constraint/ConstraintExpressionEvaluator.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/action/WorkflowValueResolver.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/action/UpdateWorkflowActionExecutor.java`
- Create: `Backend/src/test/java/com/smartlab/engine/workflow/action/UpdateWorkflowActionExecutorTest.java`
- Modify: `Backend/src/test/java/com/smartlab/engine/constraint/ConstraintEngineTest.java`

**Interfaces:**
- Produces:
  - `ConstraintExpressionEvaluator.evaluateValue(String expression, Map<String, JsonNode> variables, Map<String, List<TimedValue>> histories, Instant now): JsonNode`
  - `WorkflowValueResolver.resolveExpression(String expression, JsonNode variables): JsonNode`
- Preserves:
  - `ConstraintExpressionEvaluator.evaluate(String expression, Map<String, JsonNode> variables, Map<String, List<TimedValue>> histories, Instant now): boolean`
  - 约束表达式的`delta`、`avg`、`rate`行为

- [ ] **Step 1: 写UPDATE真实表达式失败测试**

```java
@Test
void evaluatesArithmeticAndNestedInputPayloadBeforeWritingVariable() {
    ObjectNode variables = JsonNodeSupport.objectNode();
    variables.put("offset", 2);
    variables.putObject("inputPayload").put("temperature", 30.5);
    WorkflowActionDefinition action = definition(
            "updateTarget",
            "UPDATE",
            object("internalVariableName", "target", "valueExpression", "inputPayload.temperature + offset"));

    WorkflowActionResult result = executor.execute(action, context(variables, variableDefinition("target", "DOUBLE")));

    assertThat(result.variableUpdates().path("target").decimalValue())
            .isEqualByComparingTo("32.5");
}

@Test
void rejectsUpdateResultThatDoesNotMatchDeclaredType() {
    WorkflowActionDefinition action = definition(
            "updateEnabled",
            "UPDATE",
            object("internalVariableName", "enabled", "valueExpression", "\"true\""));
    assertThatThrownBy(() -> executor.execute(
            action,
            context(JsonNodeSupport.objectNode(), variableDefinition("enabled", "BOOLEAN"))))
            .hasMessageContaining("变量enabled要求BOOLEAN");
}
```

- [ ] **Step 2: 运行测试并确认旧解析器把算术表达式当字符串**

Run: `cd Backend; mvn -Dtest=UpdateWorkflowActionExecutorTest test`

Expected: FAIL，算术结果或严格类型断言失败

- [ ] **Step 3: 提取表达式值求解接口**

`ConstraintExpressionEvaluator.evaluateValue`把解析结果转换为JsonNode：

```java
public JsonNode evaluateValue(String expression, Map<String, JsonNode> variables,
                              Map<String, List<TimedValue>> histories, Instant now) {
    Object value = new Parser(expression, variables, histories, now).parse();
    return JsonNodeSupport.MAPPER.valueToTree(value);
}

public boolean evaluate(String expression, Map<String, JsonNode> variables,
                        Map<String, List<TimedValue>> histories, Instant now) {
    JsonNode value = evaluateValue(expression, variables, histories, now);
    if (!value.isBoolean()) throw new IllegalArgumentException("约束expression必须返回boolean");
    return value.booleanValue();
}
```

Lexer允许标识符包含点号。解析变量时通过`resolvePath(variables, identifier)`读取`inputPayload.temperature`，同时保持约束绑定的普通变量名兼容

- [ ] **Step 4: 将WorkflowValueResolver接到统一求值器**

`resolveExpression`先尝试完整路径直接读取，再调用`evaluateValue`。直接字符串不再作为无法解析表达式的兜底；字符串必须使用单引号或双引号

- [ ] **Step 5: 在UPDATE执行器校验声明类型**

从`WorkflowActionContext.node().getInVariables()`找到目标变量声明。新增私有方法：

```java
private JsonNode declaredVariable(JsonNode nodeDefinition, String variableName)
private void requireCompatibleType(String variableName, String dataType, JsonNode value)
```

INTEGER只接受`isIntegralNumber()`，DOUBLE接受`isNumber()`，STRING接受`isTextual()`，BOOLEAN接受`isBoolean()`，JSON接受`isObject()`或`isArray()`

- [ ] **Step 6: 运行UPDATE与约束回归测试**

Run: `cd Backend; mvn -Dtest=UpdateWorkflowActionExecutorTest,ConstraintEngineTest test`

Expected: PASS，约束布尔表达式和窗口函数行为不回归

- [ ] **Step 7: 暂存本任务文件并提交**

```bash
git add Backend/src/main/java/com/smartlab/engine/constraint/ConstraintExpressionEvaluator.java Backend/src/main/java/com/smartlab/engine/workflow/action/WorkflowValueResolver.java Backend/src/main/java/com/smartlab/engine/workflow/action/UpdateWorkflowActionExecutor.java Backend/src/test/java/com/smartlab/engine/workflow/action/UpdateWorkflowActionExecutorTest.java Backend/src/test/java/com/smartlab/engine/constraint/ConstraintEngineTest.java
git commit -m "feat: evaluate workflow update expressions"
```

---

### Task 8: 从设备孪生状态同步DEV_NODE内部变量

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowExecutionOperations.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/DefaultWorkflowExecutionOperations.java`
- Create: `Backend/src/test/java/com/smartlab/engine/workflow/DefaultWorkflowExecutionOperationsTest.java`

**Interfaces:**
- Consumes:
  - `WorkflowTaskResourceService.resolveDeviceInstance(Task task, TaskStep step, FlowNode node): DeviceInstances`
  - `DeviceTwinStateService.getByInstanceId(Long deviceInstanceId)`
- Produces:
  - `WorkflowExecutionOperations.resolveMappedVariables(Task task, TaskStep step, FlowNode node): ObjectNode`

- [ ] **Step 1: 写设备属性映射失败测试**

```java
@Test
void mapsDeclaredDeviceAttributesFromCurrentTwinState() {
    FlowNode node = deviceNodeWithVariable("temperature", "DOUBLE", "currentTemperature");
    DeviceTwinStates twin = new DeviceTwinStates();
    twin.setCurrentAttr(objectNode("currentTemperature", 31.5));
    DeviceInstances instance = new DeviceInstances();
    instance.setId(101L);
    when(taskResourceService.resolveDeviceInstance(task, step, node)).thenReturn(instance);
    when(twinStateService.getByInstanceId(101L)).thenReturn(twin);

    ObjectNode updates = operations.resolveMappedVariables(task, step, node);

    assertThat(updates.path("temperature").decimalValue()).isEqualByComparingTo("31.5");
}

@Test
void rejectsMappedAttributeWithWrongDeclaredType() {
    FlowNode node = deviceNodeWithVariable("temperature", "STRING", "currentTemperature");
    DeviceTwinStates twin = new DeviceTwinStates();
    twin.setCurrentAttr(objectNode("currentTemperature", 31.5));
    DeviceInstances instance = new DeviceInstances();
    instance.setId(101L);
    when(taskResourceService.resolveDeviceInstance(task, step, node)).thenReturn(instance);
    when(twinStateService.getByInstanceId(101L)).thenReturn(twin);

    assertThatThrownBy(() -> operations.resolveMappedVariables(task, step, node))
            .hasMessageContaining("变量temperature要求STRING")
            .hasMessageContaining("currentTemperature");
}
```

- [ ] **Step 2: 运行测试并确认接口尚无映射方法**

Run: `cd Backend; mvn -Dtest=DefaultWorkflowExecutionOperationsTest test`

Expected: FAIL，编译错误包含`resolveMappedVariables`

- [ ] **Step 3: 扩展运行时操作接口**

```java
default ObjectNode resolveMappedVariables(Task task, TaskStep step, FlowNode node) {
    return JsonNodeSupport.objectNode();
}
```

使用default方法保持现有测试替身和非Spring实现兼容

- [ ] **Step 4: 实现真实设备孪生读取**

`DefaultWorkflowExecutionOperations`注入`DeviceTwinStateService`。仅DEV_NODE执行映射：

1. 从任务资源映射解析设备实例
2. 调用`getByInstanceId`
3. 读取`currentAttr`
4. 遍历`node.getInVariables()`
5. 对存在`attributesMapping`的变量读取真实属性
6. 属性不存在时跳过，不写默认值
7. 属性存在但类型不匹配时抛出带变量名、属性名的异常

- [ ] **Step 5: 运行设备映射测试**

Run: `cd Backend; mvn -Dtest=DefaultWorkflowExecutionOperationsTest test`

Expected: PASS

- [ ] **Step 6: 暂存本任务文件并提交**

```bash
git add Backend/src/main/java/com/smartlab/engine/workflow/WorkflowExecutionOperations.java Backend/src/main/java/com/smartlab/engine/workflow/DefaultWorkflowExecutionOperations.java Backend/src/test/java/com/smartlab/engine/workflow/DefaultWorkflowExecutionOperationsTest.java
git commit -m "feat: map workflow variables from device twins"
```

---

### Task 9: 实现确定性触发器动作顺序与严格端口数据流

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowEngine.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowRuntimeService.java`
- Create: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowEngineExecutionTest.java`

**Interfaces:**
- Consumes:
  - Task 7的UPDATE表达式执行
  - Task 8的`resolveMappedVariables`
- Produces:
  - `WorkflowEngine.collectMatchedActions(FlowNode node, JsonNode inputInterface, ObjectNode variables): List<JsonNode>`
  - `WorkflowEngine.executeActions(Task task, TaskStep step, FlowNode node, Iterable<JsonNode> actions, ObjectNode variables): ActionRunResult`
  - `WorkflowEngine.mapPortValues(FlowNode sourceNode, FlowNode targetNode, TaskStep sourceStep): ObjectNode`

  - `WorkflowRuntimeService.appendStepLog(Task task, TaskStep step, String level, String message): void`

- [ ] **Step 1: 写UPDATE优先和多EMIT失败测试**

```java
@Test
void executesAllMatchedUpdatesBeforeSingleEmit() {
    FlowNode node = nodeWithActions(
            update("setTemperature", "temperature", "inputPayload.value"),
            emit("continue", "Interface_workflow_out", "ACTIVE"));
    node.setInterfaces(interfaceWithTriggers(
            trigger("inputSignalName", "EQUALS", "ACTIVE", "continue"),
            trigger("inputSignalName", "EQUALS", "ACTIVE", "setTemperature")));

    engine.handleSignal(task, step, node, signal("ACTIVE", objectNode("value", 28)));

    InOrder order = inOrder(actionRegistry, runtimeService);
    order.verify(actionRegistry).execute(argThat(action -> action.actionType().equals("UPDATE")), any());
    order.verify(actionRegistry).execute(argThat(action -> action.actionType().equals("EMIT")), any());
    verify(runtimeService).mergeVariableSpace(eq(step), argThat(value -> value.path("temperature").asInt() == 28));
}

@Test
void failsNodeWhenSameInputMatchesTwoEmitActions() {
    FlowNode node = nodeWithTwoMatchingEmitActions();
    engine.handleSignal(task, step, node, signal("ACTIVE", JsonNodeSupport.objectNode()));
    verify(runtimeService).failStep(eq(step), contains("同一输入命中多个EMIT动作"));
    verifyNoInteractions(executionOperations);
}
```

- [ ] **Step 2: 运行执行顺序测试并确认旧引擎提前在EMIT返回**

Run: `cd Backend; mvn -Dtest=WorkflowEngineExecutionTest test`

Expected: FAIL，调用顺序或多EMIT失败收敛断言不满足

- [ ] **Step 3: 重构触发器求值与动作执行**

算法固定为：

```java
List<WorkflowActionDefinition> matched = collectMatchedActions(inputInterface, variables);
List<WorkflowActionDefinition> updates = matched.stream()
        .filter(action -> "UPDATE".equals(action.actionType()))
        .toList();
List<WorkflowActionDefinition> emits = matched.stream()
        .filter(action -> "EMIT".equals(action.actionType()))
        .toList();
if (emits.size() > 1) throw new IllegalStateException("同一输入命中多个EMIT动作");
for (WorkflowActionDefinition update : updates) {
    WorkflowActionResult result = actionRegistry.required("UPDATE").execute(
            update, new WorkflowActionContext(task, step, node, variables, Instant.now(), executionOperations));
    runtime.mergeVariableSpace(step, result.variableUpdates());
    mergeObject(variables, result.variableUpdates());
}
if (emits.size() == 1) {
    WorkflowActionResult result = actionRegistry.required("EMIT").execute(
            emits.get(0), new WorkflowActionContext(task, step, node, variables, Instant.now(), executionOperations));
    return ActionRunResult.emitted(result.emittedInterfaceName(), result.emittedSignalName(), variables);
}
return ActionRunResult.continueWithoutEmission();
```

START使用同一函数处理`actions`，先执行所有UPDATE，最后执行唯一EMIT

- [ ] **Step 4: 在每次处理前同步设备映射**

在构造条件与动作上下文前：

```java
ObjectNode mapped = executionOperations.resolveMappedVariables(task, step, node);
if (!mapped.isEmpty()) {
    runtime.mergeVariableSpace(step, mapped);
}
```

同步异常进入现有节点和任务FAILED路径并写执行日志

- [ ] **Step 5: 写端口传播失败测试**

```java
@Test
void copiesSourceOutVariableIntoTargetBeforeTargetCanBeProcessed() {
    FlowNode source = nodeWithVariableAndPort("measured", "DOUBLE", "temperatureOut", "OUT");
    FlowNode target = nodeWithVariableAndPort("target", "DOUBLE", "temperatureIn", "IN");
    TaskStep sourceStep = stepWithVariable("measured", 26.5);
    TaskStep targetStep = emptyStep(target);
    stubWorkflowWithPortConnection(source, target, sourceStep, targetStep,
            "temperatureOut", "temperatureIn");

    engine.processTask(task);

    InOrder order = inOrder(runtimeService);
    order.verify(runtimeService).createStep(
            eq(task), eq(target), eq(sourceStep.getParentStepId()), eq(sourceStep.getStepDepth()),
            argThat(input -> "ACTIVE".equals(input.path("inputSignalName").asText())));
    order.verify(runtimeService).updateInputSnapshot(
            eq(targetStep), argThat(input -> "ACTIVE".equals(input.path("inputSignalName").asText())));
    order.verify(runtimeService).mergeVariableSpace(
            eq(targetStep),
            argThat(value -> value.path("target").decimalValue().compareTo(new BigDecimal("26.5")) == 0));
}

@Test
void skipsUnsetPortValueAndWritesStepLog() {
    FlowNode source = nodeWithVariableAndPort("measured", "DOUBLE", "temperatureOut", "OUT");
    FlowNode target = nodeWithVariableAndPort("target", "DOUBLE", "temperatureIn", "IN");
    TaskStep sourceStep = sourceStepWithoutValue();
    TaskStep targetStep = emptyStep(target);
    stubWorkflowWithPortConnection(source, target, sourceStep, targetStep,
            "temperatureOut", "temperatureIn");

    engine.processTask(task);

    verify(runtimeService).appendStepLog(
            eq(task), eq(sourceStep), eq("WARN"),
            contains("端口temperatureOut绑定变量measured尚无值"));
}
```

- [ ] **Step 6: 实现严格端口传播**

对每个从当前节点指向目标节点的`portConnection`：

- 查找源OUT端口和目标IN端口
- 查找两侧绑定变量声明
- 再次校验dataType一致，防止绕过保存校验的历史脏数据
- 从源步骤`variableSpace`读取值
- 无值时调用`appendStepLog`记录WARN并跳过
- 值存在时按目标声明执行严格类型检查
- 调用`mergeVariableSpace`写入目标步骤
- 先完成全部端口写入，再发送ACTIVE

- [ ] **Step 7: 运行工作流执行测试**

Run: `cd Backend; mvn -Dtest=WorkflowEngineExecutionTest,DefaultWorkflowExecutionOperationsTest,UpdateWorkflowActionExecutorTest test`

Expected: PASS

- [ ] **Step 8: 暂存本任务文件并提交**

```bash
git add Backend/src/main/java/com/smartlab/engine/workflow/WorkflowEngine.java Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowRuntimeService.java Backend/src/test/java/com/smartlab/engine/workflow/WorkflowEngineExecutionTest.java
git commit -m "feat: execute deterministic workflow data flow"
```

---

### Task 10: 完整回归、浏览器验收与脏工作区核对

**Files:**
- Modify only if verification exposes defects in files from Tasks 1-9

**Interfaces:**
- Consumes: Tasks 1-9全部交付
- Produces: 可重复的前端、后端和浏览器验收证据

- [ ] **Step 1: 运行全部前端纯函数测试**

Run: `cd Frontend; node --test tests/*.test.js tests/*.test.mjs`

Expected: PASS，约束、任务资源绑定和工作流画布测试全部通过

- [ ] **Step 2: 运行前端生产构建**

Run: `cd Frontend; npm run build`

Expected: PASS，Vite生成`dist`且无Vue编译错误

- [ ] **Step 3: 运行工作流与约束后端定向测试**

Run: `cd Backend; mvn -Dtest=WorkflowDefinitionCompilerTest,WorkflowServiceTest,UpdateWorkflowActionExecutorTest,DefaultWorkflowExecutionOperationsTest,WorkflowEngineExecutionTest,ConstraintEngineTest test`

Expected: PASS

- [ ] **Step 4: 运行后端完整测试**

Run: `cd Backend; mvn test`

Expected: PASS；若存在与本任务无关的历史失败，记录测试类、失败断言和与本次改动无关的证据，不修改无关代码

- [ ] **Step 5: 启动前后端**

Run backend: `cd Backend; mvn spring-boot:run`

Run frontend: `cd Frontend; npm run dev -- --host 127.0.0.1`

Expected: 后端正常启动；前端输出本地URL，默认应为`http://127.0.0.1:5173`

- [ ] **Step 6: 使用浏览器完成建模验收**

按固定流程操作并截图关键状态：

1. 添加START、BRANCH、两个DEV_NODE和END
2. 从START连接到BRANCH的WORKFLOW/IN
3. 从BRANCH的true和false两个独立出口分别连接两个DEV_NODE
4. 两个DEV_NODE连接END
5. 在BRANCH创建内部变量并填写表达式
6. 在DEV_NODE选择能力并完成全部类型化参数
7. 创建OUT和IN数据端口并用紫色虚线连接
8. 验证系统接口、系统动作和系统触发器显示锁定且无法删除
9. 添加UPDATE动作和自定义触发器
10. 保存、刷新、重新打开流程
11. 核对能力参数、变量、端口、动作、触发器、接口连接、端口连接和子流程说明未丢失

- [ ] **Step 7: 使用真实任务执行链路验收**

创建任务并逐节点绑定设备实例，执行任务后核对：

- `TASK.RESOURCE_MAP`按节点路径保存实例绑定
- DEV_NODE命令经状态机与adapter发送
- 设备状态返回后完成节点
- `TASK_STEP.VARIABLE_SPACE`包含设备孪生属性映射值和端口传入值
- BRANCH只激活一个出口
- 同一设备非IDLE时任务等待，不重复下发命令
- 执行日志中没有模拟成功、伪造设备状态或默认属性值

- [ ] **Step 8: 核对最终差异范围**

Run: `git status --short`

Run: `git diff -- Backend/src/main/java/com/smartlab/engine/workflow Backend/src/main/java/com/smartlab/management/service/db/workflow Frontend/src/views/task/WorkflowDesigner.vue Frontend/src/components/task/workflow Frontend/src/utils/workflowCanvas.js Frontend/src/utils/workflowNodeDefinition.js`

Expected:

- 六个模型文件无本轮新增差异
- 数据库迁移和实体字段无新增差异
- 没有编辑器字段进入保存payload
- 没有模拟数据、强制类型转换或绕过校验的hack

- [ ] **Step 9: 提交验收修复**

仅在Step 1-8发现并修复本任务缺陷时，先用`git diff --name-only`列出文件，再逐个执行`git add --`并附上实际文件路径；确认暂存区只包含Tasks 1-9范围的验收修复后执行提交。

```bash
git diff --name-only
git status --short
```

若没有验收修复，不创建空提交

---

## 自检结果

### 规格覆盖

- 系统骨架保护：Task 1、Task 4、Task 6
- 三类节点与START/END/BRANCH/AGGREGATE模板：Task 1
- WORKFLOW与PORT双连接：Task 2、Task 5
- 类型化能力参数和JSON键值编辑：Task 3
- 内部变量、属性映射和端口：Task 3、Task 8、Task 9
- 结构化动作与触发器：Task 4、Task 7、Task 9
- UPDATE先于EMIT、单EMIT约束：Task 9
- 设备孪生真实数据：Task 8、Task 9
- 严格类型、无隐式转换：Task 1、Task 3、Task 6、Task 7、Task 8、Task 9
- 完整持久化往返：Task 5、Task 6
- 不改模型和数据库：Global Constraints、Task 10
- 前端、后端、浏览器和真实任务闭环验收：Task 10

### 占位符检查

计划中没有待定接口、未命名文件或未定义的后续实现项。Task 10提交命令中的尖括号表示只允许暂存实际验收修复文件，且该步骤仅在确有修复时执行

### 类型一致性

- 前端统一使用`actionName`作为业务唯一名称、`actionType`作为`EMIT|UPDATE`
- 接口Handle统一使用`interface:<name>`，端口Handle统一使用`port:<name>`
- 后端变量更新统一使用Jackson`ObjectNode`
- 设备属性映射统一读取`DEVICE_TWIN_STATES.current_attr`
- SUBFLOW说明统一存入`FlowNode.capability.subFlowModelDescription`
- 端口连接统一使用`source.nodeName/source.portName`与`target.nodeName/target.portName`
