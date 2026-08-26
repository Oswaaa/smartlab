import assert from 'node:assert/strict'
import { test } from 'node:test'

import {
  buildRuntimeGraph,
  buildStepTree,
  businessExecutionEvents,
  groupBusinessExecutionEvents,
  filterExecutableWorkflows,
  formatSnapshotScalar,
  isExecutableWorkflow,
  interfaceDefinitionForSnapshot,
  isWorkflowTriggerFired,
  normalizeInterfaceSnapshot,
  normalizePortSnapshot,
  runtimeParentStepId,
  runtimeTrailLabel,
  stepsForLayer,
  triggerStatesOf,
  UNSTARTED_PARENT_STEP_ID,
  visibleVariableEntries,
  workflowDocumentFromGroup,
  workflowTriggerIndexKey,
} from '../src/utils/workflowExecution.js'

test('task creation exposes only ACTIVE workflows', () => {
  const workflows = [
    { id: 1, status: 'DRAFT' },
    { id: 2, status: 'ACTIVE' },
    { id: 3, status: ' active ' },
    { id: 4 },
  ]

  assert.deepEqual(filterExecutableWorkflows(workflows).map(item => item.id), [2, 3])
  assert.equal(isExecutableWorkflow(workflows[0]), false)
  assert.equal(isExecutableWorkflow(workflows[1]), true)
})

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
  assert.deepEqual(triggerStatesOf({ temp: 20, _triggerStates: { 'out::abc::0': true } }), { 'out::abc::0': true })
})

test('maps fired state by binding order index, not Object.keys insertion order', () => {
  const states = {
    [workflowTriggerIndexKey('Interface_workflow_in', 2)]: true,
    [workflowTriggerIndexKey('Interface_workflow_in', 0)]: false,
  }
  assert.equal(isWorkflowTriggerFired(states, 'Interface_workflow_in', 0), false)
  assert.equal(isWorkflowTriggerFired(states, 'Interface_workflow_in', 1), false)
  assert.equal(isWorkflowTriggerFired(states, 'Interface_workflow_in', 2), true)
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

test('runtime graph matches steps via workflow nodeIdRefs when nodes have no nodeIdRef field', () => {
  const graph = buildRuntimeGraph({
    nodeIdRefs: [
      { nodeName: 'start1', nodeIdRef: 1 },
      { nodeName: 'device1', nodeIdRef: 2 },
    ],
    nodesDef: [{ name: 'start1' }, { name: 'device1', nodeType: 'DEV_NODE' }],
    interfaceConnections: [],
  }, [
    { id: 11, nodeIdRef: 1, nodeStatus: 'SUCCEEDED' },
    { id: 12, nodeIdRef: 2, nodeStatus: 'SUCCEEDED' },
  ])
  assert.equal(graph.nodes[1].name, 'device1')
  assert.equal(graph.nodes[1].status, 'SUCCEEDED')
  assert.equal(graph.nodes[1].stepId, 12)
})

test('runtime graph matches numeric and string node references consistently', () => {
  const graph = buildRuntimeGraph({ nodesDef: [{ name: 'heater', nodeIdRef: '7' }], interfaceConnections: [] }, [
    { id: 2, nodeIdRef: 7, nodeStatus: 'RUNNING' },
  ])
  assert.equal(graph.nodes[0].status, 'RUNNING')
  assert.equal(graph.nodes[0].stepId, 2)
})

test('runtime graph marks only connections whose target interface accepted a signal', () => {
  const graph = buildRuntimeGraph({
    nodesDef: [
      { name: 'source', interfaces: [{ name: 'out', direction: 'OUT', interfaceType: 'WORKFLOW' }] },
      { name: 'aggregate', interfaces: [
        { name: 'in_1', direction: 'IN', interfaceType: 'WORKFLOW' },
        { name: 'in_2', direction: 'IN', interfaceType: 'WORKFLOW' },
      ] },
    ],
    interfaceConnections: [
      { source: { nodeName: 'source', interfaceName: 'out' }, target: { nodeName: 'aggregate', interfaceName: 'in_1' } },
      { source: { nodeName: 'source', interfaceName: 'out' }, target: { nodeName: 'aggregate', interfaceName: 'in_2' } },
    ],
  }, [{
    id: 4,
    nodeName: 'aggregate',
    nodeStatus: 'RUNNING',
    interfaceInSnapshot: [
      { interfaceName: 'in_1', signalName: 'ACTIVE' },
      { interfaceName: 'in_2', signalName: null },
    ],
  }])

  assert.equal(graph.edges[0].used, true)
  assert.equal(graph.edges[0].targetStatus, 'RUNNING')
  assert.equal(graph.edges[0].sourceHandle, 'interface:out')
  assert.equal(graph.edges[0].targetHandle, 'interface:in_1')
  assert.equal(graph.edges[1].used, false)
})

test('runtime graph lays nodes out left to right like the designer canvas', () => {
  const graph = buildRuntimeGraph({
    nodesDef: [
      { name: 'start1', functionType: 'START', interfaces: [{ name: 'Interface_workflow_out', direction: 'OUT', interfaceType: 'WORKFLOW' }] },
      { name: 'device1', nodeType: 'DEV_NODE', interfaces: [
        { name: 'Interface_workflow_in', direction: 'IN', interfaceType: 'WORKFLOW' },
        { name: 'Interface_workflow_out', direction: 'OUT', interfaceType: 'WORKFLOW' },
      ] },
      { name: 'end1', functionType: 'END', interfaces: [{ name: 'Interface_workflow_in', direction: 'IN', interfaceType: 'WORKFLOW' }] },
    ],
    interfaceConnections: [
      { connectionType: 'NODE_TO_NODE', source: { nodeName: 'start1', interfaceName: 'Interface_workflow_out' }, target: { nodeName: 'device1', interfaceName: 'Interface_workflow_in' } },
      { connectionType: 'NODE_TO_NODE', source: { nodeName: 'device1', interfaceName: 'Interface_workflow_out' }, target: { nodeName: 'end1', interfaceName: 'Interface_workflow_in' } },
    ],
  }, [])
  const byName = Object.fromEntries(graph.nodes.map(node => [node.name, node.position.x]))
  assert.ok(byName.start1 < byName.device1)
  assert.ok(byName.device1 < byName.end1)
})

test('runtime graph ignores device bindings when collecting node-to-node edges', () => {
  const graph = buildRuntimeGraph({
    nodesDef: [
      { name: 'device1', nodeType: 'DEV_NODE' },
      { name: 'device2', nodeType: 'DEV_NODE' },
    ],
    interfaceConnections: [
      { connectionType: 'NODE_TO_DEVICE', source: { nodeName: 'device1', interfaceName: 'Interface_state_out' }, target: { deviceModelId: 20, interfaceName: 'Interface_workflow_in' } },
      { connectionType: 'NODE_TO_NODE', source: { nodeName: 'device1', interfaceName: 'Interface_workflow_out' }, target: { nodeName: 'device2', interfaceName: 'Interface_workflow_in' } },
    ],
  }, [])
  assert.deepEqual(graph.edges.map(edge => [edge.source, edge.target]), [['device1', 'device2']])
})

test('runtime graph marks port edges used when the target port has a value', () => {
  const graph = buildRuntimeGraph({
    nodesDef: [
      { name: 'source', ports: [{ name: 'port1', direction: 'OUT' }] },
      { name: 'target', ports: [{ name: 'port1', direction: 'IN' }] },
    ],
    portConnections: [
      { source: { nodeName: 'source', portName: 'port1' }, target: { nodeName: 'target', portName: 'port1' } },
    ],
  }, [{
    id: 8,
    nodeName: 'target',
    nodeStatus: 'RUNNING',
    portInSnapshot: [{ portName: 'port1', value: 349.99 }],
  }])
  assert.equal(graph.edges[0].kind, 'PORT')
  assert.equal(graph.edges[0].used, true)
})

test('normalizes port snapshots and scalar display', () => {
  assert.deepEqual(normalizePortSnapshot([{ portName: 'port1', value: 349.99 }], 'OUT'), [
    { direction: 'OUT', portName: 'port1', value: 349.99 },
  ])
  assert.equal(formatSnapshotScalar(null), '—')
  assert.equal(formatSnapshotScalar(0), '0')
  assert.equal(formatSnapshotScalar(false), 'false')
  assert.throws(() => normalizePortSnapshot({ port1: 1 }, 'IN'), /端口快照/)
})

test('business events exclude engine polling and scheduler diagnostics', () => {
  const events = businessExecutionEvents([
    { id: 1, sourceType: 'TASK', logInfo: '节点开始执行: 4' },
    { id: 2, sourceType: 'SYSTEM', logInfo: '轮询完成' },
    { id: 3, sourceType: 'SYSTEM', logInfo: '线程池领取任务' },
  ])
  assert.deepEqual(events.map(item => item.id), [1])
})

test('runtime graph groups child steps under the parent subflow step', () => {
  const graph = buildRuntimeGraph({ nodesDef: [{ name: 'sub', nodeType: 'SUBFLOW_NODE' }], interfaceConnections: [] }, [
    { id: 10, nodeName: 'sub', nodeStatus: 'RUNNING', parentStepId: null, stepDepth: 0 },
    { id: 11, nodeName: 'child', nodeStatus: 'RUNNING', parentStepId: 10, stepDepth: 1 },
  ])
  assert.equal(graph.nodes[0].children[0].id, 11)
})

test('runtime graph does not attach child-layer snapshots to the parent subflow node', () => {
  const steps = [
    { id: 10, nodeName: 'subflow1', nodeIdRef: 3, nodeStatus: 'RUNNING', parentStepId: null },
    {
      id: 11,
      nodeName: 'branch1',
      nodeIdRef: 3,
      nodeStatus: 'SUCCEEDED',
      parentStepId: 10,
      interfaceOutSnapshot: [
        { interfaceName: 'Interface_workflow_out_1', signalName: 'ACTIVE' },
        { interfaceName: 'Interface_workflow_out_2', signalName: null },
        { interfaceName: 'Interface_workflow_out_3', signalName: null },
      ],
    },
  ]
  const parentGraph = buildRuntimeGraph({
    nodesDef: [{
      name: 'subflow1',
      nodeType: 'SUBFLOW_NODE',
      nodeIdRef: 3,
      interfaces: [
        { name: 'Interface_workflow_in', direction: 'IN' },
        { name: 'Interface_workflow_out', direction: 'OUT' },
      ],
    }],
    interfaceConnections: [],
  }, steps)
  assert.equal(parentGraph.nodes[0].stepId, 10)
  assert.equal(parentGraph.nodes[0].step.interfaceOutSnapshot, undefined)

  const childGraph = buildRuntimeGraph({
    nodesDef: [{ name: 'branch1', nodeType: 'FUNC_NODE', functionType: 'BRANCH', nodeIdRef: 3 }],
    interfaceConnections: [],
  }, steps, { parentStepId: 10 })
  assert.equal(childGraph.nodes[0].stepId, 11)
  assert.equal(childGraph.nodes[0].step.interfaceOutSnapshot[0].interfaceName, 'Interface_workflow_out_1')
})

test('runtime layer helpers resolve nested group parent steps', () => {
  const groups = [
    {
      groupKey: 'root',
      parentGroupKey: null,
      flowName: '测试',
      nodes: [{ name: 'subflow1', nodeType: 'SUBFLOW_NODE', nodeIdRef: 3, childGroupKey: '58:3' }],
    },
    {
      groupKey: '58:3',
      parentGroupKey: 'root',
      flowName: '子流程A',
      occurrencePath: '测试 / subflow1',
      nodes: [{ name: 'branch1', nodeIdRef: 3 }],
    },
  ]
  const steps = [
    { id: 10, nodeName: 'subflow1', nodeIdRef: 3, parentStepId: null },
    { id: 11, nodeName: 'branch1', nodeIdRef: 3, parentStepId: 10 },
  ]
  assert.equal(runtimeParentStepId(groups, steps, 'root'), null)
  assert.equal(runtimeParentStepId(groups, steps, '58:3'), 10)
  assert.equal(runtimeParentStepId(groups, steps, 'missing'), null)
  assert.deepEqual(stepsForLayer(steps, null).map(step => step.id), [10])
  assert.equal(runtimeTrailLabel(groups[1], 1), 'subflow1')
  assert.equal(workflowDocumentFromGroup(groups[1]).nodesDef[0].name, 'branch1')
  assert.equal(runtimeParentStepId(groups, [{ id: 10, nodeName: 'other', parentStepId: null }], '58:3'), UNSTARTED_PARENT_STEP_ID)
})

test('matches snapshot rows to workflow interface definitions by name and direction', () => {
  const node = {
    interfaces: [
      { name: 'high', direction: 'OUT', bindingTriggers: [{ action: { actionName: 'EMIT' } }] },
      { name: 'state-in', direction: 'IN', bindingTriggers: [] },
    ],
  }
  assert.equal(interfaceDefinitionForSnapshot(node, { interfaceName: 'high', direction: 'OUT' }).direction, 'OUT')
  assert.equal(interfaceDefinitionForSnapshot(node, { interfaceName: 'state-in', direction: 'IN' }).name, 'state-in')
  assert.equal(interfaceDefinitionForSnapshot(node, { interfaceName: 'missing', direction: 'IN' }), null)
})

test('business events are grouped by node name from log text or step id', () => {
  const groups = groupBusinessExecutionEvents([
    { id: 1, sourceType: 'NODE', logInfo: '节点 heater (#2) 生命周期 PENDING → RUNNING', logTime: '2026-01-01T00:00:00Z' },
    { id: 2, sourceType: 'NODE', logInfo: '节点 heater (#2) 接口 out 发出信号 ACTIVE', logTime: '2026-01-01T00:00:01Z' },
    { id: 3, sourceType: 'NODE', taskStepId: 12, logInfo: '节点已创建: cooler (#3)', logTime: '2026-01-01T00:00:02Z' },
    { id: 4, sourceType: 'CONSTRAINT', logInfo: '触发超温保护', logTime: '2026-01-01T00:00:03Z' },
    { id: 5, sourceType: 'SYSTEM', logInfo: '心跳', logTime: '2026-01-01T00:00:04Z' },
  ], [{ id: 12, nodeName: 'cooler' }])
  assert.deepEqual(groups.map(group => [group.label, group.events.length]), [
    ['heater', 2],
    ['cooler', 1],
    ['任务约束', 1],
  ])
  assert.equal(businessExecutionEvents([{ sourceType: 'SYSTEM', logInfo: '心跳' }]).length, 0)
})

test('task logs stay on the current workflow layer and expose subflow entry', () => {
  const logs = [
    { id: 1, taskStepId: 10, sourceType: 'TASK', logInfo: '节点已创建: subflow1 (#3)' },
    { id: 2, taskStepId: 10, sourceType: 'TASK', logInfo: '节点 subflow1 (#3) 接口 Interface_workflow_in 收到信号 ACTIVE' },
    { id: 3, taskStepId: 11, sourceType: 'TASK', logInfo: '节点已创建: branch1 (#3)' },
    { id: 4, taskStepId: 11, sourceType: 'TASK', logInfo: '节点 branch1 (#3) 接口 Interface_workflow_out_3 发出信号 ACTIVE' },
  ]
  const steps = [
    { id: 10, nodeName: 'subflow1', nodeIdRef: 3, parentStepId: null },
    { id: 11, nodeName: 'branch1', nodeIdRef: 3, parentStepId: 10 },
  ]
  const root = groupBusinessExecutionEvents(logs, steps, {
    nodesDef: [{ name: 'subflow1', nodeType: 'SUBFLOW_NODE', childGroupKey: '58:3' }],
  }, { parentStepId: null })
  assert.deepEqual(root.map(group => [group.label, group.events.length, group.childGroupKey]), [
    ['subflow1', 2, '58:3'],
  ])
  const child = groupBusinessExecutionEvents(logs, steps, {
    nodesDef: [{ name: 'branch1', nodeType: 'FUNC_NODE', functionType: 'BRANCH' }],
  }, { parentStepId: 10 })
  assert.deepEqual(child.map(group => [group.label, group.events.length]), [['branch1', 2]])
})
