import test from 'node:test'
import assert from 'node:assert/strict'
import { applyInheritedBinding, bindingKeyFromSegments, expandWorkflowDefinition } from '../src/utils/taskResourceBindings.js'

const definitions = {
  1: { flowName: '主流程', nodesDef: [
    { name: '开盖一', nodeType: 'SUBFLOW_NODE', subFlowModelId: 2 },
    { name: '开盖二', nodeType: 'SUBFLOW_NODE', subFlowModelId: 2 }
  ], interfaceConnections: [] },
  2: { flowName: '开盖流程', nodesDef: [
    { name: '机械臂/上移', nodeType: 'DEV_NODE', deviceModelId: 20 }
  ], interfaceConnections: [
    { connectionType: 'NODE_TO_DEVICE', source: { nodeName: '机械臂/上移', interfaceName: 'Interface_state_out' }, target: { deviceModelId: 20, interfaceName: 'Interface_workflow_in' } },
    { connectionType: 'DEVICE_TO_NODE', source: { deviceModelId: 20, interfaceName: 'Interface_state_out' }, target: { nodeName: '机械臂/上移', interfaceName: 'Interface_state_in' } }
  ] }
}

test('expands every repeated subflow occurrence into a distinct binding path', async () => {
  const result = await expandWorkflowDefinition(1, async id => definitions[id])
  assert.deepEqual(result.deviceRoutes.map(route => route.bindingKey), [
    'root/开盖一/机械臂~1上移',
    'root/开盖二/机械臂~1上移'
  ])
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
