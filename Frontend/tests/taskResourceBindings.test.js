import test from 'node:test'
import assert from 'node:assert/strict'
import {
  applyInheritedBinding,
  bindingKeyFromSegments,
  buildBindingWorkflowView,
  buildDeviceBindings,
  buildTaskCreatePayload,
  expandWorkflowDefinition,
  groupRequirementsByOccurrencePath,
  isParameterHole,
  presentCapabilityDisplayName,
  missingHoleCount
} from '../src/utils/taskResourceBindings.js'

const definitions = {
  1: { name: '主流程', nodeIdRefs: [
    { nodeIdRef: 1, nodeName: '开盖一' },
    { nodeIdRef: 2, nodeName: '开盖二' }
  ], nodesDef: [
    { name: '开盖一', nodeType: 'SUBFLOW_NODE', subFlowModelId: 2 },
    { name: '开盖二', nodeType: 'SUBFLOW_NODE', subFlowModelId: 2 }
  ], interfaceConnections: [] },
  2: { name: '开盖流程', nodeIdRefs: [
    { nodeIdRef: 7, nodeName: '机械臂/上移' }
  ], nodesDef: [
    { name: '机械臂/上移', nodeType: 'DEV_NODE', deviceModelId: 20 }
  ], interfaceConnections: [
    { connectionType: 'NODE_TO_DEVICE', source: { nodeName: '机械臂/上移', interfaceName: 'Interface_state_out' }, target: { deviceModelId: 20, interfaceName: 'Interface_workflow_in' } },
    { connectionType: 'DEVICE_TO_NODE', source: { deviceModelId: 20, interfaceName: 'Interface_state_out' }, target: { nodeName: '机械臂/上移', interfaceName: 'Interface_state_in' } }
  ] }
}

test('joins nodeIdRefs to nodesDef and expands repeated subflows into backend slot ids', async () => {
  const result = await expandWorkflowDefinition(1, async id => definitions[id])
  assert.deepEqual(result.deviceRoutes.map(route => route.bindingKey), [
    '1:1/2:7',
    '1:2/2:7'
  ])
  assert.deepEqual(result.groups[0].nodes.map(node => node.nodeIdRef), [1, 2])
  assert.equal(result.groups[1].nodes[0].nodeIdRef, 7)
  assert.equal(result.deviceRoutes[0].inheritanceKey, result.deviceRoutes[1].inheritanceKey)
})

test('escapes resource path segments with JSON Pointer rules', () => {
  assert.equal(bindingKeyFromSegments(['root', '子~流程', '设备/节点']), 'root/子~0流程/设备~1节点')
})

test('inherits a reusable subflow binding only into matching unbound occurrences', () => {
  const routes = [
    { bindingKey: 'root/a/arm', inheritanceKey: '2:arm' },
    { bindingKey: 'root/b/arm', inheritanceKey: '2:arm' },
    { bindingKey: 'root/c/heater', inheritanceKey: '3:heater' }
  ]
  assert.deepEqual(applyInheritedBinding(routes, { 'root/b/arm': 202 }, routes[0], 201), {
    'root/a/arm': 201,
    'root/b/arm': 202
  })
})

test('rejects recursive subflow cycles', async () => {
  const cyclic = { 1: { flowName: '循环', nodesDef: [{ name: 'self', nodeType: 'SUBFLOW_NODE', subFlowModelId: 1 }], interfaceConnections: [] } }
  await assert.rejects(() => expandWorkflowDefinition(1, async id => cyclic[id]), /循环引用/)
})

test('groups backend requirements by occurrence path without workflow-node matching', () => {
  const first = { slotId: '1:1', occurrencePath: '加热流程 / device1', flowName: '加热流程', nodeName: 'device1', deviceModelId: 20 }
  const second = { slotId: '1:2/2:7', occurrencePath: '主流程 / 子流程A / device2', flowName: '子流程A', nodeName: 'device2', deviceModelId: 21 }

  assert.deepEqual(groupRequirementsByOccurrencePath([first, second]), [
    { path: '加热流程', requirements: [first] },
    { path: '主流程 / 子流程A', requirements: [second] }
  ])
})

test('creates and preflights tasks with the same slot-id deviceBindings contract', () => {
  const payload = buildTaskCreatePayload({
    taskName: '升温任务',
    flowModelId: 1,
    resourceBindings: { '1:1/2:7': 101, '1:2/2:7': 102 },
    taskConstraints: [{ name: '温度上限' }]
  }, [
    { slotId: '1:1/2:7' },
    { slotId: '1:2/2:7' }
  ])

  assert.deepEqual(payload, {
    taskName: '升温任务',
    flowModelId: 1,
    executionKind: 'PRODUCTION',
    deviceBindings: [
      { slotId: '1:1/2:7', deviceInstanceId: 101 },
      { slotId: '1:2/2:7', deviceInstanceId: 102 }
    ],
    taskConstraints: [{ name: '温度上限' }],
    taskVariables: {}
  })
})

test('simulation create payload includes physical instance ids and marks execution kind', () => {
  const payload = buildTaskCreatePayload({
    taskName: '仿真升温',
    flowModelId: 1,
    executionKind: 'SIMULATION',
    resourceBindings: { '1:1': 101 },
    parameterBindings: { '1:1': { target: 80 } },
    taskConstraints: []
  }, [{
    slotId: '1:1',
    capabilityParameters: [{ name: 'target', hole: true }]
  }])

  assert.equal(payload.executionKind, 'SIMULATION')
  assert.deepEqual(payload.deviceBindings, [
    { slotId: '1:1', deviceInstanceId: 101, capabilityParameters: { target: 80 } }
  ])
})

test('decorates only device nodes with requirements using exact backend slot ids', async () => {
  const expanded = await expandWorkflowDefinition(1, async id => definitions[id])
  const requirements = [
    { slotId: '1:1/2:7', occurrencePath: '主流程 / 开盖一 / 机械臂/上移', deviceModelId: 20 },
    { slotId: '1:2/2:7', occurrencePath: '主流程 / 开盖二 / 机械臂/上移', deviceModelId: 20 }
  ]

  const view = buildBindingWorkflowView(expanded, requirements)

  assert.equal(view.errors.length, 0)
  assert.equal(view.groups[0].nodes[0].slotId, null)
  assert.equal(view.groups[1].nodes[0].slotId, '1:1/2:7')
  assert.equal(view.groups[2].nodes[0].slotId, '1:2/2:7')
  assert.equal(typeof view.groups[1].nodes[0].position.x, 'number')
})

test('reports a device node whose backend binding requirement is missing', async () => {
  const expanded = await expandWorkflowDefinition(1, async id => definitions[id])
  const view = buildBindingWorkflowView(expanded, [{ slotId: '1:1/2:7', deviceModelId: 20 }])

  assert.match(view.errors.join('\n'), /开盖二.*绑定要求/)
  assert.equal(view.groups[2].nodes[0].bindingInvalid, true)
})

test('reports incomplete device connections without exposing transport connection names', async () => {
  const invalid = {
    name: '加热流程',
    nodeIdRefs: [{ nodeIdRef: 3, nodeName: '加热节点' }],
    nodesDef: [{ name: '加热节点', nodeType: 'DEV_NODE', deviceModelId: 20 }],
    interfaceConnections: []
  }

  const expanded = await expandWorkflowDefinition(8, async () => invalid)

  assert.deepEqual(expanded.errors, ['加热流程 / 加热节点的设备状态接口连接不完整或设备模型不一致'])
})

test('treats 0 and false as filled hole values and omits model-filled parameters from the create payload', () => {
  const requirements = [{
    slotId: '1:1',
    capabilityParameters: [
      { name: 'duration', hole: false, modelValue: 30 },
      { name: 'target', hole: true },
      { name: 'enabled', hole: true }
    ]
  }]
  const form = {
    taskName: '升温任务',
    flowModelId: 1,
    resourceBindings: { '1:1': 101 },
    parameterBindings: { '1:1': { target: 0, enabled: false, duration: 99 } },
    taskConstraints: []
  }

  assert.equal(isParameterHole(0), false)
  assert.equal(isParameterHole(false), false)
  assert.equal(isParameterHole(null), true)
  assert.equal(missingHoleCount(requirements, { '1:1': { target: 0, enabled: false } }), 0)
  assert.equal(missingHoleCount(requirements, { '1:1': { target: 0 } }), 1)
  assert.deepEqual(buildDeviceBindings(requirements, form.resourceBindings, form.parameterBindings), [
    { slotId: '1:1', deviceInstanceId: 101, capabilityParameters: { target: 0, enabled: false } }
  ])
  assert.deepEqual(buildTaskCreatePayload(form, requirements).deviceBindings[0].capabilityParameters, {
    target: 0,
    enabled: false
  })
})

test('copies data port connections onto each flow group for the shared canvas', async () => {
  const expanded = await expandWorkflowDefinition(8, async () => ({
    name: '加热流程',
    nodeIdRefs: [{ nodeIdRef: 3, nodeName: '加热节点' }],
    nodesDef: [{
      name: '加热节点',
      nodeType: 'DEV_NODE',
      deviceModelId: 20,
      ports: [{ name: 'temp', direction: 'OUT' }]
    }],
    interfaceConnections: [
      { connectionType: 'NODE_TO_DEVICE', source: { nodeName: '加热节点', interfaceName: 'Interface_state_out' }, target: { deviceModelId: 20, interfaceName: 'Interface_workflow_in' } },
      { connectionType: 'DEVICE_TO_NODE', source: { deviceModelId: 20, interfaceName: 'Interface_state_out' }, target: { nodeName: '加热节点', interfaceName: 'Interface_state_in' } }
    ],
    portConnections: [{ source: { nodeName: '加热节点', portName: 'temp' }, target: { nodeName: '加热节点', portName: 'temp' } }]
  }))
  assert.equal(expanded.groups[0].portConnections.length, 1)
  assert.equal(expanded.groups[0].interfaceConnections.length, 0)
})

test('keeps node-to-node connections even when connectionType is omitted', async () => {
  const expanded = await expandWorkflowDefinition(9, async () => ({
    name: '连线流程',
    nodesDef: [
      { name: 'start1', nodeType: 'FUNC_NODE', functionType: 'START' },
      { name: 'end1', nodeType: 'FUNC_NODE', functionType: 'END' },
    ],
    interfaceConnections: [
      { source: { nodeName: 'start1', interfaceName: 'Interface_workflow_out' }, target: { nodeName: 'end1', interfaceName: 'Interface_workflow_in' } },
      { connectionType: 'NODE_TO_DEVICE', source: { nodeName: 'start1' }, target: { deviceModelId: 20, interfaceName: 'in' } },
    ],
  }))
  assert.equal(expanded.groups[0].interfaceConnections.length, 1)
  assert.equal(expanded.groups[0].interfaceConnections[0].source.nodeName, 'start1')
  assert.equal(expanded.groups[0].interfaceConnections[0].connectionType, 'NODE_TO_NODE')
})

test('presents capability displayName from the requirement, then the device model', () => {
  assert.equal(presentCapabilityDisplayName({ capabilityName: 'HEAT', capabilityDisplayName: '恒温加热' }), '恒温加热')
  assert.equal(presentCapabilityDisplayName(
    { capabilityName: 'HEAT', deviceModelId: 7 },
    [{ id: 7, capabilities: [{ capabilityName: 'HEAT', displayName: '恒温加热' }] }]
  ), '恒温加热')
  assert.equal(presentCapabilityDisplayName({ capabilityName: 'HEAT' }), 'HEAT')
})
