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
  removePort,
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
  assert.equal(node.lifecycle.initialStateName, 'PENDING')
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
  const node = { capability: { capabilityName: 'old', capabilityParameters: { speed: 3, mode: 'AUTO', obsolete: true } }, _capabilityParameterTypes: { speed: 'INTEGER', mode: 'STRING', obsolete: 'BOOLEAN' } }
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
    'actions', 'interfaces[0].bindingTriggers[1].action'
  ])
})
test('START仅生成工作流出口及ACTIVE系统动作', () => {
  const node = createFunctionNode('START', 'start')
  assert.deepEqual(node.interfaces.map(item => item.name), ['Interface_workflow_out'])
  assert.equal(node.interfaces[0].bindingTriggers.length, 0)
  assert.deepEqual(node.actions.map(({ actionName, actionType, targetInterfaceName, signalName }) => ({ actionName, actionType, targetInterfaceName, signalName })), [
    { actionName: 'emitActive', actionType: 'EMIT', targetInterfaceName: 'Interface_workflow_out', signalName: 'ACTIVE' }
  ])
})

test('END仅生成工作流入口且没有输出动作', () => {
  const node = createFunctionNode('END', 'end')
  assert.deepEqual(node.interfaces.map(item => item.name), ['Interface_workflow_in'])
  assert.equal(node.interfaces[0].bindingTriggers.length, 0)
  assert.deepEqual(node.actions, [])
})

test('AGGREGATE生成ACTIVE触发器和工作流回传', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  assert.deepEqual(node.interfaces.map(item => item.name), ['Interface_workflow_in', 'Interface_workflow_out'])
  assert.deepEqual(node.actions.map(item => [item.actionName, item.actionType, item.targetInterfaceName, item.signalName]), [
    ['emitActive', 'EMIT', 'Interface_workflow_out', 'ACTIVE']
  ])
  assert.deepEqual(node.interfaces[0].bindingTriggers.map(item => [item.condition.object, item.condition.operator, item.condition.threshold, item.action]), [
    ['inputSignalName', '=', 'ACTIVE', 'emitActive']
  ])
})

test('DEV_NODE生成工作流和状态机之间的两个系统触发器', () => {
  const node = createDeviceNode({ id: 7, capabilities: [] }, 'device')
  assert.deepEqual(node.actions.map(item => [item.actionName, item.actionType, item.targetInterfaceName, item.signalName]), [
    ['startDevice', 'EMIT', 'Interface_state_out', 'WF_EXECUTE_START'],
    ['completeNode', 'EMIT', 'Interface_workflow_out', 'ACTIVE']
  ])
  assert.deepEqual(node.interfaces[0].bindingTriggers.map(item => [item.condition.object, item.condition.operator, item.condition.threshold, item.action]), [
    ['inputSignalName', '=', 'ACTIVE', 'startDevice']
  ])
  assert.deepEqual(node.interfaces[2].bindingTriggers.map(item => [item.condition.object, item.condition.operator, item.condition.threshold, item.action]), [
    ['inputPayload.stateName', '=', 'COMPLETED', 'completeNode']
  ])
})

test('节点校验拒绝删除或篡改DEV系统骨架', () => {
  const node = createDeviceNode({ id: 7, capabilities: [] }, 'device')
  node.interfaces = node.interfaces.filter(item => item.name !== 'Interface_state_in')
  node.actions[0].signalName = 'OTHER'
  const errors = validateNodeDefinition(node)
  assert.ok(errors.some(error => error.path === 'interfaces' && /stateIn/.test(error.message)))
  assert.ok(errors.some(error => error.path === 'actions' && /startDevice/.test(error.message)))
})

test('能力参数仅在编辑器类型映射相同的时候保留，INTEGER切DOUBLE会移除', () => {
  const node = {
    capability: { capabilityName: 'old', capabilityParameters: { count: 2, speed: 3 } },
    _capabilityParameterTypes: { count: 'INTEGER', speed: 'INTEGER' }
  }
  const next = replaceCapability(node, {
    capabilityName: 'new',
    parameters: [{ parameterName: 'count', dataType: 'DOUBLE' }, { parameterName: 'speed', dataType: 'INTEGER' }]
  })
  assert.deepEqual(next.capability.capabilityParameters, { speed: 3 })
  assert.deepEqual(next._capabilityParameterTypes, { count: 'DOUBLE', speed: 'INTEGER' })
})

test('removeVariable不保留未使用的端口连接参数', () => {
  assert.equal(removeVariable.length, 2)
})
test('接口声明后端类型及允许信号，触发器使用action和等号', () => {
  const dev = createDeviceNode({ id: 1, capabilities: [] }, 'dev')
  const workflow = dev.interfaces.filter(item => item.interfaceType === 'WORKFLOW')
  assert.equal(workflow.length, 2)
  assert.ok(workflow.every(item => JSON.stringify(item.allowedSignals) === JSON.stringify(['ACTIVE'])))
  assert.deepEqual(dev.interfaces[1].allowedSignals, ['WF_EXECUTE_START'])
  assert.deepEqual(dev.interfaces[2].allowedSignals, ['CMD_STATE', 'OP_STATE'])
  assert.equal(dev.interfaces[0].bindingTriggers[0].action, 'startDevice')
  assert.equal(dev.interfaces[0].bindingTriggers[0].condition.operator, '=')
})

test('DEV与SUBFLOW使用后端所需完整生命周期', () => {
  const expectedStates = ['PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'TERMINATING', 'TERMINATED']
  for (const node of [createDeviceNode({ id: 1, capabilities: [] }, 'dev'), createSubflowNode({ id: 2 }, 'sub')]) {
    assert.equal(node.lifecycle.initialStateName, 'PENDING')
    assert.deepEqual(node.lifecycle.states, expectedStates)
    assert.equal(node.lifecycle.transitions.length, 7)
  }
})

test('系统IN接口允许业务触发器且系统动作允许业务动作', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  node.interfaces[0].bindingTriggers.push({ action: 'notify', condition: { object: 'inputSignalName', operator: '=', threshold: 'OTHER' } })
  node.actions.push({ actionName: 'notify', actionType: 'EMIT', targetInterfaceName: 'Interface_workflow_out', signalName: 'ACTIVE' })
  assert.deepEqual(validateNodeDefinition(node), [])
})

test('节点校验接受合法UPDATE并拒绝非法EMIT目标', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  node.internalVariables.push({ name: 'payload', dataType: 'JSON' })
  node.actions.push({ actionName: 'setPayload', actionType: 'UPDATE', internalVariableName: 'payload', valueExpression: '{}' })
  assert.deepEqual(validateNodeDefinition(node), [])
  node.actions[1] = { actionName: 'badEmit', actionType: 'EMIT', targetInterfaceName: 'Interface_workflow_in', signalName: 'ACTIVE' }
  assert.ok(validateNodeDefinition(node).some(error => error.path === 'actions[1].targetInterfaceName'))
})

test('保存重载后传入旧能力声明仍保留同名同类型参数', () => {
  const node = { capability: { capabilityName: 'old', capabilityParameters: { speed: 3, count: 1 } } }
  const previous = { capabilityName: 'old', parameters: [{ parameterName: 'speed', dataType: 'INTEGER' }, { parameterName: 'count', dataType: 'INTEGER' }] }
  const next = replaceCapability(node, { capabilityName: 'new', parameters: [{ parameterName: 'speed', dataType: 'INTEGER' }, { parameterName: 'count', dataType: 'DOUBLE' }] }, previous)
  assert.deepEqual(next.capability.capabilityParameters, { speed: 3 })
})

test('removePort删除真实嵌套连接格式中的关联连接', () => {
  const node = { name: 'n', ports: [{ name: 'out' }] }
  const result = removePort(node, 'out', [
    { source: { nodeName: 'n', portName: 'out' }, target: { nodeName: 'x', portName: 'in' } },
    { source: { nodeName: 'x', portName: 'out' }, target: { nodeName: 'n', portName: 'other' } }
  ])
  assert.equal(result.portConnections.length, 1)
})
test('DEV和SUBFLOW生命周期状态使用编译器所需的纯字符串数组', () => {
  const expected = ['PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'TERMINATING', 'TERMINATED']
  assert.deepEqual(createDeviceNode({ id: 1, capabilities: [] }, 'dev').lifecycle.states, expected)
  assert.deepEqual(createSubflowNode({ id: 2 }, 'sub').lifecycle.states, expected)
})

test('UPDATE使用internalVariableName字段定位内部变量', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  node.internalVariables.push({ name: 'payload', dataType: 'JSON' })
  node.actions.push({ actionName: 'setPayload', actionType: 'UPDATE', internalVariableName: 'payload', valueExpression: '{}' })
  assert.deepEqual(validateNodeDefinition(node), [])
})

test('DEV状态接口明确声明STATE类型和各自允许信号', () => {
  const node = createDeviceNode({ id: 1, capabilities: [] }, 'dev')
  const stateOut = node.interfaces.find(item => item.name === 'Interface_state_out')
  const stateIn = node.interfaces.find(item => item.name === 'Interface_state_in')
  assert.equal(stateOut.interfaceType, 'STATE')
  assert.deepEqual(stateOut.allowedSignals, ['WF_EXECUTE_START'])
  assert.equal(stateIn.interfaceType, 'STATE')
  assert.deepEqual(stateIn.allowedSignals, ['CMD_STATE', 'OP_STATE'])
})