import test from 'node:test'
import assert from 'node:assert/strict'
import {
  addWorkflowTriggerCondition,
  canCustomizeControlInterfaces,
  canEditControlItem,
  canEditControlTriggers,
  configureWorkflowNodeTemplates,
  changeWorkflowInterfaceDirection,
  createDeviceNode,
  createFunctionNode,
  createWorkflowInterfaceDefinition,
  createSubflowNode,
  customTriggerActionNames,
  defaultWorkflowInterfaceDirection,
  emptyWorkflowUpdateValue,
  normalizeTypedValue,
  normalizeWorkflowNodeDefinition,
  orderedControlInterfaces,
  removeWorkflowTriggerCondition,
  replaceWorkflowTriggerCondition,
  removeInterface,
  removePort,
  removeVariable,
  replaceCapability,
  resetWorkflowNodeTemplatesForTest,
  validateNodeDefinition,
  workflowUpdateVariableDataType,
  workflowTriggerConditions,
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
      trigger('end.activate', 'signalName', 'ACTIVE', action('UPDATE', { updateType: 'NODE_LIFECYCLE', targetName: 'RUNNING' })),
    ])],
    actions: ['UPDATE'],
  },
  BRANCH: {
    lifecycle: lifecycle('branch'),
    interfaces: [iface('branch.in', 'Interface_workflow_in', 'IN', 'WORKFLOW', ['ACTIVE'], [
      trigger('branch.activate', 'signalName', 'ACTIVE', action('UPDATE', { updateType: 'NODE_LIFECYCLE', targetName: 'RUNNING' })),
    ])],
    actions: ['UPDATE', 'EMIT'],
  },
  AGGREGATE: {
    lifecycle: lifecycle('aggregate'),
    internalVariables: [{ name: 'aggregateCount', dataType: 'INTEGER', initialValue: 0, _system: true, _systemKey: 'aggregate.count' }],
    interfaces: [
      iface('aggregate.in', 'Interface_workflow_in', 'IN', 'WORKFLOW', ['ACTIVE'], [
        trigger('aggregate.countInput', 'signalName', 'ACTIVE', action('UPDATE', { updateType: 'INTERNAL_VARIABLE', targetName: 'aggregateCount', valueExpression: 'aggregateCount + 1' })),
        {
          condition: { logic: 'AND', conditions: [
            { object: 'aggregateCount', operator: '>', threshold: 0 },
            { object: 'nodeLifecycleState', operator: '=', threshold: 'PENDING' },
          ] },
          action: action('UPDATE', { updateType: 'NODE_LIFECYCLE', targetName: 'RUNNING' }),
          _system: true,
          _systemKey: 'aggregate.activate',
        },
      ]),
      iface('aggregate.out', 'Interface_workflow_out', 'OUT', 'WORKFLOW', ['ACTIVE'], []),
    ],
    actions: ['UPDATE', 'EMIT'],
  },
  DEV_NODE: {
    lifecycle: lifecycle('device'),
    interfaces: [
      iface('device.workflowIn', 'Interface_workflow_in', 'IN', 'WORKFLOW', ['ACTIVE'], [
        trigger('device.activate', 'signalName', 'ACTIVE', action('UPDATE', { updateType: 'NODE_LIFECYCLE', targetName: 'RUNNING' })),
      ]),
      iface('device.stateOut', 'Interface_state_out', 'OUT', 'STATE', ['WF_EXECUTE_START'], [
        trigger('device.execute', 'nodeLifecycleState', 'RUNNING', action('EMIT', { targetInterfaceName: 'Interface_state_out', signalName: 'WF_EXECUTE_START' })),
      ]),
      iface('device.stateIn', 'Interface_state_in', 'IN', 'STATE', ['CMD_STATE'], [
        {
          condition: {
            logic: 'AND',
            conditions: [
              { object: 'nodeLifecycleState', operator: '=', threshold: 'RUNNING' },
              { object: 'signalName', operator: '=', threshold: 'CMD_STATE' },
              { object: 'payload.stateName', operator: '=', threshold: 'COMPLETED' },
            ],
          },
          action: action('UPDATE', { updateType: 'NODE_LIFECYCLE', targetName: 'SUCCEEDED' }),
          _system: true,
          _systemKey: 'device.complete',
        },
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

test('聚合节点保留只读计数能力并允许配置系统输出接口的触发器', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  assert.deepEqual(node.internalVariables.map(item => [item.name, item.dataType, item.initialValue]), [
    ['aggregateCount', 'INTEGER', 0],
  ])
  assert.equal(canEditControlTriggers(node, node.interfaces[0]), false)
  assert.equal(canEditControlTriggers(node, node.interfaces[1]), true)
  assert.deepEqual(node.interfaces[1].bindingTriggers, [])
})

test('聚合节点新增输入接口时自动附带只读计数触发器', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  const input = createWorkflowInterfaceDefinition(node, 'IN', 'aggregate_in_2')
  assert.equal(input.direction, 'IN')
  assert.equal(input.interfaceType, 'WORKFLOW')
  assert.deepEqual(input.allowedSignals, ['ACTIVE'])
  assert.equal(input.bindingTriggers[0]._system, true)
  assert.equal(input.bindingTriggers[0].action.payload.targetName, 'aggregateCount')
  assert.equal(input.bindingTriggers[0].action.payload.valueExpression, 'aggregateCount + 1')
  node.interfaces.push(input)
  assert.deepEqual(validateNodeDefinition(node), [])
  input.bindingTriggers = []
  assert.ok(validateNodeDefinition(node).some(error => /聚合输入接口必须包含计数触发器/.test(error.message)))
})

test('新增接口方向按功能节点职责给出默认值', () => {
  assert.equal(defaultWorkflowInterfaceDirection(createFunctionNode('START', 'start')), 'OUT')
  assert.equal(defaultWorkflowInterfaceDirection(createFunctionNode('END', 'end')), 'IN')
  assert.equal(defaultWorkflowInterfaceDirection(createFunctionNode('BRANCH', 'branch')), 'OUT')
  assert.equal(defaultWorkflowInterfaceDirection(createFunctionNode('AGGREGATE', 'aggregate')), 'IN')
})

test('聚合自定义接口切换为IN时清理连线和EMIT并补齐唯一计数触发器', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  node.interfaces.push({
    name: 'aggregate_custom',
    direction: 'OUT',
    interfaceType: 'WORKFLOW',
    allowedSignals: ['ACTIVE'],
    bindingTriggers: [
      { condition: { object: 'nodeLifecycleState', operator: '=', threshold: 'RUNNING' }, action: action('EMIT', { targetInterfaceName: 'aggregate_custom', signalName: 'ACTIVE' }) },
      { condition: { object: 'aggregateCount', operator: '>', threshold: 1 }, action: action('UPDATE', { updateType: 'NODE_LIFECYCLE', targetName: 'SUCCEEDED' }) },
    ],
  })
  const result = changeWorkflowInterfaceDirection(node, 'aggregate_custom', 'IN', [
    { source: { nodeName: 'aggregate', interfaceName: 'aggregate_custom' }, target: { nodeName: 'end', interfaceName: 'workflow_in' } },
    { source: { nodeName: 'other', interfaceName: 'workflow_out' }, target: { nodeName: 'end', interfaceName: 'workflow_in' } },
  ])

  const changed = result.node.interfaces.find(item => item.name === 'aggregate_custom')
  assert.equal(changed.direction, 'IN')
  assert.equal(result.removedConnectionCount, 1)
  assert.equal(result.interfaceConnections.length, 1)
  assert.equal(result.removedTriggerCount, 1)
  assert.equal(changed.bindingTriggers.filter(item => item._systemKey === 'aggregate.countInput.aggregate_custom').length, 1)
  assert.equal(changed.bindingTriggers.some(item => item._systemKey === 'aggregate.activate'), false)
  assert.equal(changed.bindingTriggers.some(item => item.action.actionName === 'EMIT'), false)
  assert.equal(changed.bindingTriggers.some(item => item.action.payload.targetName === 'SUCCEEDED'), true)
})

test('聚合自定义接口切换为OUT时移除计数触发器并保留其他UPDATE', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  const input = createWorkflowInterfaceDefinition(node, 'IN', 'aggregate_custom')
  input.bindingTriggers.push({
    condition: { object: 'aggregateCount', operator: '>', threshold: 1 },
    action: action('UPDATE', { updateType: 'NODE_LIFECYCLE', targetName: 'SUCCEEDED' }),
  })
  node.interfaces.push(input)

  const result = changeWorkflowInterfaceDirection(node, 'aggregate_custom', 'OUT', [])
  const changed = result.node.interfaces.find(item => item.name === 'aggregate_custom')
  assert.equal(changed.direction, 'OUT')
  assert.equal(result.removedTriggerCount, 1)
  assert.equal(changed.bindingTriggers.some(item => item.action.payload.targetName === 'aggregateCount'), false)
  assert.equal(changed.bindingTriggers.some(item => item.action.payload.targetName === 'SUCCEEDED'), true)
})

test('系统接口不允许切换方向', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  assert.throws(
    () => changeWorkflowInterfaceDirection(node, 'Interface_workflow_in', 'OUT', []),
    /系统接口Interface_workflow_in不可修改方向/,
  )
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

test('non-function nodes reject custom triggers on system interfaces', () => {
  const deviceModel = { id: 7, capabilities: [{ capabilityName: 'heat', parameters: [] }] }
  const node = createDeviceNode(deviceModel, 'device1')
  node.capability = { capabilityName: 'heat', capabilityParameters: {} }
  node.interfaces[0].bindingTriggers.push({
    condition: { object: 'signalName', operator: '=', threshold: 'RETRY' },
    action: action('UPDATE', { updateType: 'NODE_LIFECYCLE', targetName: 'RUNNING' }),
  })
  assert.ok(validateNodeDefinition(node, { deviceModel })
    .some(error => /非功能节点不能声明自定义触发器/.test(error.message)))
})

test('creation requires templates and always clones them', () => {
  resetWorkflowNodeTemplatesForTest()
  assert.throws(() => createFunctionNode('START', 'start'), /工作流系统模板尚未加载:START/)
  configureWorkflowNodeTemplates(workflowTemplateFixture)
  const first = createFunctionNode('BRANCH', 'branch-a')
  assert.deepEqual(workflowNodeTemplates().BRANCH.actions, ['UPDATE', 'EMIT'])
})

test('BRANCH starts without fixed true/false outputs and accepts user-defined N-way outputs', () => {
  const node = createFunctionNode('BRANCH', 'branch')
  assert.deepEqual(node.interfaces.map(item => item.name), ['Interface_workflow_in'])
  assert.deepEqual(node.interfaces[0].bindingTriggers.map(item => item.action.payload.targetName), ['RUNNING'])
  assert.equal(node.expression, '')
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
  assert.deepEqual(node.interfaces[2].allowedSignals, ['CMD_STATE'])
  assert.deepEqual(workflowTriggerConditions(node.interfaces[2].bindingTriggers[0].condition).map(item => item.object), [
    'nodeLifecycleState', 'signalName', 'payload.stateName',
  ])
  assert.equal(node.interfaces[2].bindingTriggers[0].action.payload.targetName, 'SUCCEEDED')
})

test('trigger conditions expand to one-level AND and collapse back to a single predicate', () => {
  const lifecycle = { object: 'nodeLifecycleState', operator: '=', threshold: 'RUNNING' }
  const signal = { object: 'signalName', operator: '=', threshold: 'ACTIVE' }
  const grouped = addWorkflowTriggerCondition(lifecycle, signal)
  assert.deepEqual(grouped, { logic: 'AND', conditions: [lifecycle, signal] })
  assert.deepEqual(workflowTriggerConditions(grouped), [lifecycle, signal])
  assert.deepEqual(removeWorkflowTriggerCondition(grouped, 1), lifecycle)
  assert.throws(() => removeWorkflowTriggerCondition(lifecycle, 0), /至少保留一个条件/)
})

test('trigger condition helpers accept reactive proxy data from the node inspector', () => {
  const lifecycle = new Proxy({ object: 'nodeLifecycleState', operator: '=', threshold: 'RUNNING' }, {})
  const signal = new Proxy({ object: 'signalName', operator: '=', threshold: 'ACTIVE' }, {})

  const grouped = addWorkflowTriggerCondition(lifecycle, signal)

  assert.deepEqual(grouped, {
    logic: 'AND',
    conditions: [
      { object: 'nodeLifecycleState', operator: '=', threshold: 'RUNNING' },
      { object: 'signalName', operator: '=', threshold: 'ACTIVE' },
    ],
  })
  assert.deepEqual(removeWorkflowTriggerCondition(new Proxy(grouped, {}), 1), {
    object: 'nodeLifecycleState', operator: '=', threshold: 'RUNNING',
  })
})

test('replacing a trigger condition returns plain data when the current group is reactive', () => {
  const grouped = new Proxy({
    logic: 'AND',
    conditions: [
      new Proxy({ object: 'nodeLifecycleState', operator: '=', threshold: 'RUNNING' }, {}),
      new Proxy({ object: 'signalName', operator: '=', threshold: 'ACTIVE' }, {}),
    ],
  }, {})

  assert.deepEqual(replaceWorkflowTriggerCondition(grouped, 1, {
    object: 'signalName', operator: '=', threshold: 'SUBFLOW_COMPLETED',
  }), {
    logic: 'AND',
    conditions: [
      { object: 'nodeLifecycleState', operator: '=', threshold: 'RUNNING' },
      { object: 'signalName', operator: '=', threshold: 'SUBFLOW_COMPLETED' },
    ],
  })
})

test('trigger validation accepts AND and rejects OR or nested groups', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  const trigger = {
    condition: addWorkflowTriggerCondition(
      { object: 'nodeLifecycleState', operator: '=', threshold: 'RUNNING' },
      { object: 'signalName', operator: '=', threshold: 'ACTIVE' },
    ),
    action: action('EMIT', { targetInterfaceName: 'Interface_workflow_out', signalName: 'ACTIVE' }),
  }
  node.interfaces[1].bindingTriggers.push(trigger)
  assert.deepEqual(validateNodeDefinition(node), [])
  trigger.condition.logic = 'OR'
  assert.ok(validateNodeDefinition(node).some(error => /只支持AND/.test(error.message)))
  trigger.condition = { logic: 'AND', conditions: [
    { object: 'signalName', operator: '=', threshold: 'ACTIVE' },
    { logic: 'AND', conditions: [
      { object: 'nodeLifecycleState', operator: '=', threshold: 'RUNNING' },
      { object: 'signalName', operator: '=', threshold: 'ACTIVE' },
    ] },
  ] }
  assert.ok(validateNodeDefinition(node).some(error => /不允许嵌套/.test(error.message)))
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
  node.interfaces[1].bindingTriggers.at(-1).action.payload.value = 1
  assert.ok(validateNodeDefinition(node).some(error => error.path.endsWith('.action.payload')))
})

test('constant UPDATE preserves scalar number boolean and string types', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  node.internalVariables.push({ name: 'enabled', dataType: 'BOOLEAN' }, { name: 'mode', dataType: 'STRING' })
  node.interfaces[1].bindingTriggers.push({ condition: { object: 'enabled', operator: '=', threshold: false }, action: action('UPDATE', { updateType: 'INTERNAL_VARIABLE', targetName: 'enabled', value: true }) })
  node.interfaces[1].bindingTriggers.push({ condition: { object: 'mode', operator: '=', threshold: 'AUTO' }, action: action('UPDATE', { updateType: 'INTERNAL_VARIABLE', targetName: 'mode', value: 'MANUAL' }) })
  assert.deepEqual(validateNodeDefinition(node), [])
})

test('device attribute mapping reports variable and attribute type mismatches', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  node.internalVariables.push({ name: 'temperature', dataType: 'STRING', attributesMapping: 'temperature' })
  const errors = validateNodeDefinition(node, { deviceAttributes: [{ attributeName: 'temperature', dataType: 'DOUBLE' }] })
  assert.ok(errors.some(error => /不一致/.test(error.message)))
  node.internalVariables.find(item => item.name === 'temperature').dataType = 'DOUBLE'
  assert.deepEqual(validateNodeDefinition(node, { deviceAttributes: [{ attributeName: 'temperature', dataType: 'DOUBLE' }] }), [])
})

test('lifecycle UPDATE validates target state and rejects value fields', () => {
  const node = createFunctionNode('END', 'end')
  node.interfaces[0].bindingTriggers.push({ condition: { object: 'nodeLifecycleState', operator: '=', threshold: 'RUNNING' }, action: action('UPDATE', { updateType: 'NODE_LIFECYCLE', targetName: 'UNKNOWN', value: 1 }) })
  const paths = validateNodeDefinition(node).map(error => error.path)
  assert.ok(paths.some(path => path.endsWith('.targetName')))
  assert.ok(paths.some(path => path.endsWith('.action.payload')))
})

test('EMIT must use the trigger host output interface', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  node.interfaces[0].bindingTriggers.push({ condition: { object: 'signalName', operator: '=', threshold: 'OTHER' }, action: action('EMIT', { targetInterfaceName: 'Interface_workflow_out', signalName: 'ACTIVE' }) })
  assert.ok(validateNodeDefinition(node).some(error => error.message.includes('触发器所在接口')))
})

test('UPDATE and EMIT are fixed workflow action capabilities and old nodes are completed automatically', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  assert.deepEqual(customTriggerActionNames(node), ['UPDATE', 'EMIT'])
  assert.deepEqual(normalizeWorkflowNodeDefinition({ actions: ['UPDATE'], interfaces: [] }).actions, ['UPDATE', 'EMIT'])
  assert.deepEqual(normalizeWorkflowNodeDefinition({ interfaces: [] }).actions, ['UPDATE', 'EMIT'])
})

test('UPDATE constant editor derives the target variable type and resets to its empty typed value', () => {
  const node = { internalVariables: [
    { name: 'count', dataType: 'INTEGER' },
    { name: 'temperature', dataType: 'DOUBLE' },
    { name: 'mode', dataType: 'STRING' },
    { name: 'enabled', dataType: 'BOOLEAN' },
  ] }
  assert.equal(workflowUpdateVariableDataType(node, 'count'), 'INTEGER')
  assert.equal(workflowUpdateVariableDataType(node, 'temperature'), 'DOUBLE')
  assert.equal(workflowUpdateVariableDataType(node, 'mode'), 'STRING')
  assert.equal(workflowUpdateVariableDataType(node, 'enabled'), 'BOOLEAN')
  assert.equal(workflowUpdateVariableDataType(node, 'missing'), null)
  assert.equal(emptyWorkflowUpdateValue('INTEGER'), null)
  assert.equal(emptyWorkflowUpdateValue('DOUBLE'), null)
  assert.equal(emptyWorkflowUpdateValue('STRING'), '')
  assert.equal(emptyWorkflowUpdateValue('BOOLEAN'), false)
})

test('legacy named actions and string references normalize to inline actions', () => {
  const normalized = normalizeWorkflowNodeDefinition({
    actions: [{ actionName: 'setTemperature', actionType: 'UPDATE', internalVariableName: 'temperature', valueExpression: 'temperature / 100' }],
    interfaces: [{ bindingTriggers: [{ condition: { object: 'temperature', operator: '>', threshold: 10 }, action: 'setTemperature' }] }],
  })
  assert.deepEqual(normalized.actions, ['UPDATE', 'EMIT'])
  assert.deepEqual(normalized.interfaces[0].bindingTriggers[0].action, action('UPDATE', {
    updateType: 'INTERNAL_VARIABLE', targetName: 'temperature', valueExpression: 'temperature / 100',
  }))
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
