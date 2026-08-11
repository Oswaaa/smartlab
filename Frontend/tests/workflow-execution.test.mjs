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

test('business events exclude engine polling and scheduler diagnostics', () => {
  const events = businessExecutionEvents([
    { id: 1, sourceType: 'TASK', logInfo: '节点开始执行: 4' },
    { id: 2, sourceType: 'SYSTEM', logInfo: '轮询完成' },
    { id: 3, sourceType: 'SYSTEM', logInfo: '线程池领取任务' },
  ])
  assert.deepEqual(events.map(item => item.id), [1])
})
