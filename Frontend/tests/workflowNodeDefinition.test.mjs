import test from 'node:test'
import assert from 'node:assert/strict'
import {
  createFunctionNode,
  createDeviceNode,
  createSubflowNode,
  configureWorkflowNodeTemplates,
  resetWorkflowNodeTemplatesForTest,
  workflowNodeTemplates,
  isSystemItem,
  normalizeTypedValue,
  replaceCapability,
  removeVariable,
  removeAction,
  removePort,
  validateNodeDefinition
} from '../src/utils/workflowNodeDefinition.js'

const workflowTemplateFixture = {
  START: { lifecycle: { _system: true, _systemKey: 'start.lifecycle' }, interfaces: [{ name: 'Interface_workflow_out', direction: 'OUT', interfaceType: 'WORKFLOW', allowedSignals: ['ACTIVE'], bindingTriggers: [], _system: true, _systemKey: 'start.workflowOut' }], actions: [{ actionName: 'emitActive', actionType: 'EMIT', targetInterfaceName: 'Interface_workflow_out', signalName: 'ACTIVE', _system: true, _systemKey: 'start.emitActive' }] },
  END: { lifecycle: { _system: true, _systemKey: 'end.lifecycle' }, interfaces: [{ name: 'Interface_workflow_in', direction: 'IN', interfaceType: 'WORKFLOW', allowedSignals: ['ACTIVE'], bindingTriggers: [], _system: true, _systemKey: 'end.workflowIn' }], actions: [] },
  BRANCH: { lifecycle: { _system: true, _systemKey: 'branch.lifecycle' }, interfaces: [{ name: 'Interface_workflow_in', direction: 'IN', interfaceType: 'WORKFLOW', allowedSignals: ['ACTIVE'], bindingTriggers: [{ condition: { object: 'expression', operator: '=', threshold: true }, action: 'emitTrue', _system: true, _systemKey: 'branch.trueTrigger' }, { condition: { object: 'expression', operator: '=', threshold: false }, action: 'emitFalse', _system: true, _systemKey: 'branch.falseTrigger' }], _system: true, _systemKey: 'branch.workflowIn' }, { name: 'Interface_true_out', direction: 'OUT', interfaceType: 'WORKFLOW', allowedSignals: ['ACTIVE'], _system: true, _systemKey: 'branch.trueOut' }, { name: 'Interface_false_out', direction: 'OUT', interfaceType: 'WORKFLOW', allowedSignals: ['ACTIVE'], _system: true, _systemKey: 'branch.falseOut' }], actions: [{ actionName: 'emitTrue', actionType: 'EMIT', targetInterfaceName: 'Interface_true_out', signalName: 'ACTIVE', _system: true, _systemKey: 'branch.emitTrue' }, { actionName: 'emitFalse', actionType: 'EMIT', targetInterfaceName: 'Interface_false_out', signalName: 'ACTIVE', _system: true, _systemKey: 'branch.emitFalse' }] },
  AGGREGATE: { lifecycle: { _system: true, _systemKey: 'aggregate.lifecycle' }, interfaces: [{ name: 'Interface_workflow_in', direction: 'IN', interfaceType: 'WORKFLOW', allowedSignals: ['ACTIVE'], bindingTriggers: [{ condition: { object: 'inputSignalName', operator: '=', threshold: 'ACTIVE' }, action: 'emitActive', _system: true, _systemKey: 'aggregate.activeTrigger' }], _system: true, _systemKey: 'aggregate.workflowIn' }, { name: 'Interface_workflow_out', direction: 'OUT', interfaceType: 'WORKFLOW', allowedSignals: ['ACTIVE'], _system: true, _systemKey: 'aggregate.workflowOut' }], actions: [{ actionName: 'emitActive', actionType: 'EMIT', targetInterfaceName: 'Interface_workflow_out', signalName: 'ACTIVE', _system: true, _systemKey: 'aggregate.emitActive' }] },
  DEV_NODE: { lifecycle: { initialStateName: 'PENDING', states: ['PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'TERMINATING', 'TERMINATED'], transitions: [['PENDING', 'RUNNING', 'lifecycle.pending.running'], ['PENDING', 'TERMINATED', 'lifecycle.pending.terminated'], ['RUNNING', 'SUCCEEDED', 'lifecycle.running.succeeded'], ['RUNNING', 'FAILED', 'lifecycle.running.failed'], ['RUNNING', 'TERMINATING', 'lifecycle.running.terminating'], ['TERMINATING', 'TERMINATED', 'lifecycle.terminating.terminated'], ['TERMINATING', 'FAILED', 'lifecycle.terminating.failed']].map(([fromStateName, toStateName, _systemKey]) => ({ fromStateName, toStateName, _system: true, _systemKey })) }, interfaces: [{ name: 'Interface_workflow_in', direction: 'IN', interfaceType: 'WORKFLOW', allowedSignals: ['ACTIVE'], bindingTriggers: [{ condition: { object: 'inputSignalName', operator: '=', threshold: 'ACTIVE' }, action: 'startDevice', _system: true, _systemKey: 'device.startTrigger' }], _system: true, _systemKey: 'device.workflowIn' }, { name: 'Interface_state_out', direction: 'OUT', interfaceType: 'STATE', allowedSignals: ['WF_EXECUTE_START'], _system: true, _systemKey: 'device.stateOut' }, { name: 'Interface_state_in', direction: 'IN', interfaceType: 'STATE', allowedSignals: ['CMD_STATE', 'OP_STATE'], bindingTriggers: [{ condition: { object: 'inputPayload.stateName', operator: '=', threshold: 'COMPLETED' }, action: 'completeNode', _system: true, _systemKey: 'device.completeTrigger' }], _system: true, _systemKey: 'device.stateIn' }, { name: 'Interface_workflow_out', direction: 'OUT', interfaceType: 'WORKFLOW', allowedSignals: ['ACTIVE'], _system: true, _systemKey: 'device.workflowOut' }], actions: [{ actionName: 'startDevice', actionType: 'EMIT', targetInterfaceName: 'Interface_state_out', signalName: 'WF_EXECUTE_START', _system: true, _systemKey: 'device.startDevice' }, { actionName: 'completeNode', actionType: 'EMIT', targetInterfaceName: 'Interface_workflow_out', signalName: 'ACTIVE', _system: true, _systemKey: 'device.completeNode' }] },
  SUBFLOW_NODE: { lifecycle: { initialStateName: 'PENDING', states: ['PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'TERMINATING', 'TERMINATED'], transitions: [['PENDING', 'RUNNING', 'lifecycle.pending.running'], ['PENDING', 'TERMINATED', 'lifecycle.pending.terminated'], ['RUNNING', 'SUCCEEDED', 'lifecycle.running.succeeded'], ['RUNNING', 'FAILED', 'lifecycle.running.failed'], ['RUNNING', 'TERMINATING', 'lifecycle.running.terminating'], ['TERMINATING', 'TERMINATED', 'lifecycle.terminating.terminated'], ['TERMINATING', 'FAILED', 'lifecycle.terminating.failed']].map(([fromStateName, toStateName, _systemKey]) => ({ fromStateName, toStateName, _system: true, _systemKey })) }, interfaces: [{ name: 'Interface_workflow_in', direction: 'IN', interfaceType: 'WORKFLOW', allowedSignals: ['ACTIVE'], _system: true, _systemKey: 'subflow.workflowIn' }, { name: 'Interface_workflow_out', direction: 'OUT', interfaceType: 'WORKFLOW', allowedSignals: ['ACTIVE'], _system: true, _systemKey: 'subflow.workflowOut' }], actions: [] }
}

test.before(() => configureWorkflowNodeTemplates(workflowTemplateFixture))

test('system templates reject creation before loading and restore fixture', () => {
  resetWorkflowNodeTemplatesForTest()
  assert.throws(() => createFunctionNode('START', 'start'), /工作流系统模板尚未加载:START/)
  configureWorkflowNodeTemplates(workflowTemplateFixture)
})

test('node creation uses backend fixture and clones DEV_NODE and BRANCH templates', () => {
  const device = createDeviceNode({ id: 7, capabilities: [] }, 'heater')
  const branch = createFunctionNode('BRANCH', 'branch')
  assert.equal(device.interfaces[1].name, workflowTemplateFixture.DEV_NODE.interfaces[1].name)
  assert.equal(branch.actions[0].actionName, workflowTemplateFixture.BRANCH.actions[0].actionName)
  device.interfaces[1].allowedSignals.push('MUTATED')
  branch.actions[0].actionName = 'MUTATED'
  assert.deepEqual(workflowNodeTemplates().DEV_NODE.interfaces[1].allowedSignals, ['WF_EXECUTE_START'])
  assert.equal(workflowNodeTemplates().BRANCH.actions[0].actionName, 'emitTrue')
})

test('能力参数按声明类型序列化且拒绝隐式转换', () => {
  assert.equal(normalizeTypedValue('INTEGER', 3), 3)
  assert.equal(normalizeTypedValue('DOUBLE', 3.5), 3.5)
  assert.equal(normalizeTypedValue('BOOLEAN', false), false)
  assert.deepEqual(normalizeTypedValue('JSON', [{ key: 'mode', value: 'AUTO' }]), { mode: 'AUTO' })
  assert.throws(() => normalizeTypedValue('INTEGER', '3'), /INTEGER参数必须是整数/)
})

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
  const expectedTransitions = [
    { fromStateName: 'PENDING', toStateName: 'RUNNING', _system: true, _systemKey: 'lifecycle.pending.running' },
    { fromStateName: 'PENDING', toStateName: 'TERMINATED', _system: true, _systemKey: 'lifecycle.pending.terminated' },
    { fromStateName: 'RUNNING', toStateName: 'SUCCEEDED', _system: true, _systemKey: 'lifecycle.running.succeeded' },
    { fromStateName: 'RUNNING', toStateName: 'FAILED', _system: true, _systemKey: 'lifecycle.running.failed' },
    { fromStateName: 'RUNNING', toStateName: 'TERMINATING', _system: true, _systemKey: 'lifecycle.running.terminating' },
    { fromStateName: 'TERMINATING', toStateName: 'TERMINATED', _system: true, _systemKey: 'lifecycle.terminating.terminated' },
    { fromStateName: 'TERMINATING', toStateName: 'FAILED', _system: true, _systemKey: 'lifecycle.terminating.failed' }
  ]
  for (const node of [createDeviceNode({ id: 1, capabilities: [] }, 'dev'), createSubflowNode({ id: 2 }, 'sub')]) {
    assert.equal(node.lifecycle.initialStateName, 'PENDING')
    assert.deepEqual(node.lifecycle.states, expectedStates)
    assert.deepEqual(node.lifecycle.transitions, expectedTransitions)
  }
})

test('系统IN接口允许业务触发器调用自定义UPDATE动作', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  node.internalVariables.push({ name: 'ready', dataType: 'BOOLEAN' })
  node.interfaces[0].bindingTriggers.push({ action: 'markReady', condition: { object: 'ready', operator: '=', threshold: false } })
  node.actions.push({ actionName: 'markReady', actionType: 'UPDATE', internalVariableName: 'ready', valueExpression: 'true' })
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

test('UPDATE拒绝不存在的internalVariableName', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  node.internalVariables.push({ name: 'payload', dataType: 'JSON' })
  node.actions.push({ actionName: 'setMissing', actionType: 'UPDATE', internalVariableName: 'missing', valueExpression: '{}' })
  assert.ok(validateNodeDefinition(node).some(error => error.path === 'actions[1].internalVariableName' && /UPDATE/.test(error.message)))
})

test('UPDATE拒绝空白的valueExpression', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  node.internalVariables.push({ name: 'payload', dataType: 'JSON' })
  node.actions.push({ actionName: 'setEmpty', actionType: 'UPDATE', internalVariableName: 'payload', valueExpression: '   ' })
  assert.ok(validateNodeDefinition(node).some(error => error.path === 'actions[1].valueExpression' && /valueExpression/.test(error.message)))
})

test('UPDATE拒绝缺失的valueExpression', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  node.internalVariables.push({ name: 'payload', dataType: 'JSON' })
  node.actions.push({ actionName: 'setMissing', actionType: 'UPDATE', internalVariableName: 'payload' })
  assert.ok(validateNodeDefinition(node).some(error => error.path === 'actions[1].valueExpression' && /valueExpression/.test(error.message)))
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
test('EMIT必须指向本节点OUT接口且信号在allowedSignals中', () => {
  const node = createFunctionNode('START', 'start')
  node.actions.push({ actionName: 'badEmit', actionType: 'EMIT', targetInterfaceName: 'missing', signalName: 'UNKNOWN' })
  const messages = validateNodeDefinition(node).map(item => item.message)
  assert.ok(messages.includes('EMIT动作badEmit引用的输出接口missing不存在'))
})

test('UPDATE必须引用真实内部变量且表达式非空', () => {
  const node = createFunctionNode('AGGREGATE', 'join')
  node.actions.push({ actionName: 'updateCount', actionType: 'UPDATE', internalVariableName: 'count', valueExpression: '' })
  const messages = validateNodeDefinition(node).map(item => item.message)
  assert.ok(messages.includes('UPDATE动作updateCount引用的内部变量count不存在'))
  assert.ok(messages.includes('UPDATE动作updateCount的valueExpression不能为空'))
})

test('触发器只能挂在IN接口且动作引用必须存在', () => {
  const node = createFunctionNode('START', 'start')
  node.interfaces[0].bindingTriggers = [{ condition: { object: 'inputSignalName', operator: 'EQUALS', threshold: 'ACTIVE' }, action: 'missingAction' }]
  const messages = validateNodeDefinition(node).map(item => item.message)
  assert.ok(messages.some(message => message.includes('OUT接口不能声明bindingTriggers')))
  assert.ok(messages.some(message => message.includes('missingAction不存在')))
})