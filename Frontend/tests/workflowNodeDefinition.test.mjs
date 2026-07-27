import test from 'node:test'
import assert from 'node:assert/strict'
import {
  createFunctionNode,
  createDeviceNode,
  createSubflowNode,
  isSystemItem,
  replaceCapability,
  removeVariable,
  removeAction,
  validateNodeDefinition
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

test('切换能力只保留同名同类型参数', () => {
  const node = { capability: { capabilityName: 'old', capabilityParameters: { speed: 3, mode: 'AUTO', obsolete: true } } }
  const next = replaceCapability(node, { capabilityName: 'new', parameters: [
    { parameterName: 'speed', dataType: 'INTEGER' }, { parameterName: 'mode', dataType: 'BOOLEAN' }
  ] })
  assert.deepEqual(next.capability.capabilityParameters, { speed: 3 })
})

test('被端口引用的变量不能直接删除', () => {
  const node = {
    internalVariables: [{ name: 'temperature', dataType: 'DOUBLE' }],
    ports: [{ name: 'temperatureOut', direction: 'OUT', internalVariableName: 'temperature' }]
  }
  assert.throws(() => removeVariable(node, 'temperature', []), /变量temperature仍被端口temperatureOut引用/)
})

test('系统动作不能被业务删除', () => {
  const node = createFunctionNode('START', 'start')
  assert.throws(() => removeAction(node, 'emitActive'), /系统动作emitActive不可删除/)
})

test('节点校验拒绝无效变量类型和缺失的START系统动作', () => {
  const node = createFunctionNode('START', 'start')
  node.internalVariables.push({ name: 'invalid', dataType: 'UNKNOWN' })
  node.actions = []
  assert.deepEqual(validateNodeDefinition(node).map(error => error.path), [
    'internalVariables[0].dataType', 'actions'
  ])
})
test('节点校验拒绝缺失的BRANCH系统动作', () => {
  const node = createFunctionNode('BRANCH', 'branch')
  node.actions = [node.actions[0]]
  assert.deepEqual(validateNodeDefinition(node).map(error => error.path), [
    'actions', 'interfaces[0].bindingTriggers[1].actionName'
  ])
})