import assert from 'node:assert/strict'
import { test } from 'node:test'

import {
  buildRuntimeGraph,
  buildStepTree,
  businessExecutionEvents,
  filterExecutableWorkflows,
  isExecutableWorkflow,
  normalizeInterfaceSnapshot,
  visibleVariableEntries,
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
