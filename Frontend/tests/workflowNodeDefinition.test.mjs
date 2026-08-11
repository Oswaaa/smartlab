import test from 'node:test'
import assert from 'node:assert/strict'
import {
  canCustomizeControlInterfaces,
  canEditControlItem,
  configureWorkflowNodeTemplates,
  createDeviceNode,
  createFunctionNode,
  createSubflowNode,
  customTriggerActionNames,
  normalizeTypedValue,
  normalizeWorkflowNodeDefinition,
  orderedControlInterfaces,
  removeAction,
  removeInterface,
  removePort,
  removeVariable,
  replaceCapability,
  resetWorkflowNodeTemplatesForTest,
  validateNodeDefinition,
  workflowNodeTemplates,
} from '../src/utils/workflowNodeDefinition.js'

const states = ['PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'TERMINATING', 'TERMINATED']
const transitions = [
  ['PENDING', 'RUNNING'], ['PENDING', 'TERMINATED'], ['RUNNING', 'SUCCEEDED'],
  ['RUNNING', 'FAILED'], ['RUNNING', 'TERMINATING'], ['TERMINATING', 'TERMINATED'],
  ['TERMINATING', 'FAILED'],
].map(([fromStateName, toStateName]) => ({ fromStateName, toStateName, _system: true, _systemKey: `lifecycle.${fromStateName}.${toStateName}` }))

function lifecycle(key) {
  return { initialStateName: 'PENDING', states, transitions, _system: true, _systemKey: `${key}.lifecycle` }
}

function action(actionName, payload) { return { actionName, payload } }
function trigger(key, object, threshold, inlineAction) {
  return { condition: { object, operator: '=', threshold }, action: inlineAction, _system: true, _systemKey: key }
}
function iface(key, name, direction, interfaceType, allowedSignals, bindingTriggers = []) {
  return { name, direction, interfaceType, allowedSignals, bindingTriggers, _system: true, _systemKey: key }
}

const workflowTemplateFixture = {
  START: {
    lifecycle: lifecycle('start'),
    interfaces: [iface('start.out', 'Interface_workflow_out', 'OUT', 'WORKFLOW', ['ACTIVE'], [
      trigger('start.begin', 'nodeLifecycleState', 'PENDING', action('UPDATE', { updateType: 'NODE_LIFECYCLE', targetName: 'RUNNING' })),
      trigger('start.emit', 'nodeLifecycleState', 'RUNNING', action('EMIT', { targetInterfaceName: 'Interface_workflow_out', signalName: 'ACTIVE' })),
    ])],
    actions: ['UPDATE', 'EMIT'],
  },
  END: {
    lifecycle: lifecycle('end'),
    interfaces: [iface('end.in', 'Interface_workflow_in', 'IN', 'WORKFLOW', ['ACTIVE'], [
      trigger('end.activate', 'inputSignalName', 'ACTIVE', action('UPDATE', { updateType: 'NODE_LIFECYCLE', targetName: 'RUNNING' })),
    ])],
    actions: ['UPDATE'],
  },
  BRANCH: {
    lifecycle: lifecycle('branch'),
    interfaces: [iface('branch.in', 'Interface_workflow_in', 'IN', 'WORKFLOW', ['ACTIVE'], [
      trigger('branch.activate', 'inputSignalName', 'ACTIVE', action('UPDATE', { updateType: 'NODE_LIFECYCLE', targetName: 'RUNNING' })),
    ])],
    actions: ['UPDATE'],
  },
  AGGREGATE: {
    lifecycle: lifecycle('aggregate'),
    interfaces: [
      iface('aggregate.in', 'Interface_workflow_in', 'IN', 'WORKFLOW', ['ACTIVE'], [
        trigger('aggregate.activate', 'inputSignalName', 'ACTIVE', action('UPDATE', { updateType: 'NODE_LIFECYCLE', targetName: 'RUNNING' })),
      ]),
      iface('aggregate.out', 'Interface_workflow_out', 'OUT', 'WORKFLOW', ['ACTIVE'], [
        trigger('aggregate.emit', 'nodeLifecycleState', 'RUNNING', action('EMIT', { targetInterfaceName: 'Interface_workflow_out', signalName: 'ACTIVE' })),
      ]),
    ],
    actions: ['UPDATE', 'EMIT'],
  },
  DEV_NODE: {
    lifecycle: lifecycle('device'),
    interfaces: [
      iface('device.workflowIn', 'Interface_workflow_in', 'IN', 'WORKFLOW', ['ACTIVE'], [
        trigger('device.activate', 'inputSignalName', 'ACTIVE', action('UPDATE', { updateType: 'NODE_LIFECYCLE', targetName: 'RUNNING' })),
      ]),
      iface('device.stateOut', 'Interface_state_out', 'OUT', 'STATE', ['WF_EXECUTE_START'], [
        trigger('device.execute', 'nodeLifecycleState', 'RUNNING', action('EMIT', { targetInterfaceName: 'Interface_state_out', signalName: 'WF_EXECUTE_START' })),
      ]),
      iface('device.stateIn', 'Interface_state_in', 'IN', 'STATE', ['CMD_STATE', 'OP_STATE'], [
        trigger('device.complete', 'inputPayload.stateName', 'COMPLETED', action('UPDATE', { updateType: 'NODE_LIFECYCLE', targetName: 'SUCCEEDED' })),
      ]),
      iface('device.workflowOut', 'Interface_workflow_out', 'OUT', 'WORKFLOW', ['ACTIVE'], [
        trigger('device.route', 'nodeLifecycleState', 'SUCCEEDED', action('EMIT', { targetInterfaceName: 'Interface_workflow_out', signalName: 'ACTIVE' })),
      ]),
    ],
    actions: ['UPDATE', 'EMIT'],
  },
  SUBFLOW_NODE: {
    lifecycle: lifecycle('subflow'),
    interfaces: [
      iface('subflow.in', 'Interface_workflow_in', 'IN', 'WORKFLOW', ['ACTIVE']),
      iface('subflow.out', 'Interface_workflow_out', 'OUT', 'WORKFLOW', ['ACTIVE']),
    ],
    actions: ['UPDATE', 'EMIT'],
  },
}

test.before(() => configureWorkflowNodeTemplates(workflowTemplateFixture))

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
  const deviceModel = { id: 7, capabilities: [{ capabilityName: 'heat', parameters: [] }] }
  const node = createDeviceNode(deviceModel, 'device1')
  node.capability = { capabilityName: 'heat', capabilityParameters: {} }
  node.interfaces.push({ name: 'user_out', direction: 'OUT', interfaceType: 'WORKFLOW', allowedSignals: ['ACTIVE'], bindingTriggers: [] })
  assert.ok(validateNodeDefinition(node, { deviceModel })
    .some(error => /非功能节点/.test(error.message)))
})

test('creation requires templates and always clones them', () => {
  resetWorkflowNodeTemplatesForTest()
  assert.throws(() => createFunctionNode('START', 'start'), /工作流系统模板尚未加载:START/)
  configureWorkflowNodeTemplates(workflowTemplateFixture)
  const first = createFunctionNode('BRANCH', 'branch-a')
  first.actions.push('EMIT')
  assert.deepEqual(workflowNodeTemplates().BRANCH.actions, ['UPDATE'])
})

test('BRANCH starts without fixed true/false outputs and accepts user-defined N-way outputs', () => {
  const node = createFunctionNode('BRANCH', 'branch')
  assert.deepEqual(node.interfaces.map(item => item.name), ['Interface_workflow_in'])
  assert.equal(node.expression, '')
  node.actions.push('EMIT')
  for (const name of ['low', 'normal', 'high']) {
    node.interfaces.push({ name, direction: 'OUT', interfaceType: 'WORKFLOW', allowedSignals: ['ACTIVE'], bindingTriggers: [
      { condition: { object: 'expression', operator: '=', threshold: name }, action: action('EMIT', { targetInterfaceName: name, signalName: 'ACTIVE' }) },
    ] })
  }
  assert.deepEqual(validateNodeDefinition(node), [])
})

test('DEV_NODE exposes four independent inline lifecycle and signal triggers', () => {
  const node = createDeviceNode({ id: 7, capabilities: [] }, 'device')
  assert.deepEqual(node.actions, ['UPDATE', 'EMIT'])
  assert.deepEqual(node.interfaces.map(item => item.bindingTriggers[0].action.actionName), ['UPDATE', 'EMIT', 'UPDATE', 'EMIT'])
  assert.equal(node.interfaces[1].bindingTriggers[0].action.payload.signalName, 'WF_EXECUTE_START')
  assert.equal(node.interfaces[2].bindingTriggers[0].action.payload.targetName, 'SUCCEEDED')
})

test('switching device capability clears all previous parameters', () => {
  const node = { capability: { capabilityName: 'old', capabilityParameters: { speed: 3, mode: 'AUTO' } } }
  const next = replaceCapability(node, { capabilityName: 'new', parameters: [{ name: 'speed', dataType: 'INTEGER' }] })
  assert.deepEqual(next.capability.capabilityParameters, {})
})

test('typed values preserve their declared data types', () => {
  assert.equal(normalizeTypedValue('INTEGER', 3), 3)
  assert.equal(normalizeTypedValue('DOUBLE', 3.5), 3.5)
  assert.equal(normalizeTypedValue('BOOLEAN', false), false)
  assert.deepEqual(normalizeTypedValue('JSON', [{ key: 'mode', value: 'AUTO' }]), { mode: 'AUTO' })
  assert.throws(() => normalizeTypedValue('INTEGER', '3'), /INTEGER参数必须是整数/)
})

test('constant and expression UPDATE are both valid but mutually exclusive', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  node.internalVariables.push({ name: 'temperature', dataType: 'DOUBLE' })
  node.interfaces[1].bindingTriggers.push({ condition: { object: 'temperature', operator: '>', threshold: 100 }, action: action('UPDATE', { updateType: 'INTERNAL_VARIABLE', targetName: 'temperature', value: 200 }) })
  node.interfaces[1].bindingTriggers.push({ condition: { object: 'temperature', operator: '<', threshold: 0 }, action: action('UPDATE', { updateType: 'INTERNAL_VARIABLE', targetName: 'temperature', valueExpression: 'temperature / 100' }) })
  assert.deepEqual(validateNodeDefinition(node), [])
  node.interfaces[1].bindingTriggers[2].action.payload.value = 1
  assert.ok(validateNodeDefinition(node).some(error => error.path.endsWith('.action.payload')))
})

test('constant UPDATE preserves number boolean object and array types', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  node.internalVariables.push({ name: 'enabled', dataType: 'BOOLEAN' }, { name: 'payload', dataType: 'JSON' })
  node.interfaces[1].bindingTriggers.push({ condition: { object: 'enabled', operator: '=', threshold: false }, action: action('UPDATE', { updateType: 'INTERNAL_VARIABLE', targetName: 'enabled', value: true }) })
  node.interfaces[1].bindingTriggers.push({ condition: { object: 'enabled', operator: '=', threshold: true }, action: action('UPDATE', { updateType: 'INTERNAL_VARIABLE', targetName: 'payload', value: [1, 2] }) })
  assert.deepEqual(validateNodeDefinition(node), [])
})

test('lifecycle UPDATE validates target state and rejects value fields', () => {
  const node = createFunctionNode('END', 'end')
  node.interfaces[0].bindingTriggers.push({ condition: { object: 'nodeLifecycleState', operator: '=', threshold: 'RUNNING' }, action: action('UPDATE', { updateType: 'NODE_LIFECYCLE', targetName: 'UNKNOWN', value: 1 }) })
  const paths = validateNodeDefinition(node).map(error => error.path)
  assert.ok(paths.some(path => path.endsWith('.targetName')))
  assert.ok(paths.some(path => path.endsWith('.action.payload')))
})

test('EMIT validates output interface and allowed signal on any trigger host interface', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  node.interfaces[0].bindingTriggers.push({ condition: { object: 'inputSignalName', operator: '=', threshold: 'OTHER' }, action: action('EMIT', { targetInterfaceName: 'Interface_workflow_in', signalName: 'ACTIVE' }) })
  assert.ok(validateNodeDefinition(node).some(error => error.message.includes('必须是OUT接口')))
})

test('actions are a unique UPDATE/EMIT capability subset', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  assert.deepEqual(customTriggerActionNames(node), ['UPDATE', 'EMIT'])
  node.actions.push('EMIT', 'CUSTOM')
  assert.deepEqual(validateNodeDefinition(node).map(error => error.path), ['actions[2]', 'actions[3]'])
})

test('legacy named actions and string references normalize to inline actions', () => {
  const normalized = normalizeWorkflowNodeDefinition({
    actions: [{ actionName: 'setTemperature', actionType: 'UPDATE', internalVariableName: 'temperature', valueExpression: 'temperature / 100' }],
    interfaces: [{ bindingTriggers: [{ condition: { object: 'temperature', operator: '>', threshold: 10 }, action: 'setTemperature' }] }],
  })
  assert.deepEqual(normalized.actions, ['UPDATE'])
  assert.deepEqual(normalized.interfaces[0].bindingTriggers[0].action, action('UPDATE', {
    updateType: 'INTERNAL_VARIABLE', targetName: 'temperature', valueExpression: 'temperature / 100',
  }))
})

test('used action capability cannot be removed', () => {
  const node = createFunctionNode('START', 'start')
  assert.throws(() => removeAction(node, 'EMIT'), /仍被触发器引用/)
})

test('referenced variables and ports are protected and connections are cleaned', () => {
  const node = { name: 'n', internalVariables: [{ name: 'temperature', dataType: 'DOUBLE' }], ports: [{ name: 'out', direction: 'OUT', internalVariableName: 'temperature' }] }
  assert.throws(() => removeVariable(node, 'temperature'), /仍被端口out引用/)
  const result = removePort(node, 'out', [
    { source: { nodeName: 'n', portName: 'out' }, target: { nodeName: 'x', portName: 'in' } },
    { source: { nodeName: 'x', portName: 'out' }, target: { nodeName: 'n', portName: 'other' } },
  ])
  assert.equal(result.portConnections.length, 1)
})

test('removing a custom branch interface also removes its connections', () => {
  const node = { name: 'branch', interfaces: [{ name: 'low', direction: 'OUT', interfaceType: 'WORKFLOW' }, { name: 'normal', direction: 'OUT', interfaceType: 'WORKFLOW' }] }
  const result = removeInterface(node, 'low', [
    { source: { nodeName: 'branch', interfaceName: 'low' }, target: { nodeName: 'end', interfaceName: 'in' } },
    { source: { nodeName: 'branch', interfaceName: 'normal' }, target: { nodeName: 'end', interfaceName: 'in' } },
  ])
  assert.deepEqual(result.node.interfaces.map(item => item.name), ['normal'])
  assert.equal(result.interfaceConnections.length, 1)
})

test('device and subflow nodes retain the complete lifecycle state set', () => {
  assert.deepEqual(createDeviceNode({ id: 1, capabilities: [] }, 'dev').lifecycle.states, states)
  assert.deepEqual(createSubflowNode({ id: 2 }, 'sub').lifecycle.states, states)
})
