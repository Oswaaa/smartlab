import test from 'node:test'
import assert from 'node:assert/strict'
import {
  clearWorkflowCanvas,
  protocolSignalCandidates,
  serializeWorkflowExpression,
  validateWorkflowExpression,
  workflowTemporalFunctions,
  workflowLibraryGroups,
} from '../src/utils/workflowDesignerRules.js'

test('workflow library groups active and draft models without changing records', () => {
  const active = { id: 1, flowName: '已启用', status: 'ACTIVE' }
  const draft = { id: 2, flowName: '草稿', status: 'DRAFT' }
  assert.deepEqual(workflowLibraryGroups([draft, active]), [
    { key: 'active', label: '已启用流程', children: [active] },
    { key: 'draft', label: '草稿流程', children: [draft] },
  ])
})

test('clear canvas preserves flow identity and removes only nodes and connections', () => {
  const source = { id: 8, name: '反应流程', description: '说明', nodesDef: [{}], interfaceConnections: [{}], portConnections: [{}] }
  assert.deepEqual(clearWorkflowCanvas(source), {
    ...source,
    nodesDef: [],
    interfaceConnections: [],
    portConnections: [],
  })
})

test('signal candidates come from protocol metadata and hide subflow completion from custom interfaces', () => {
  const protocol = {
    workflowNodeSignals: ['ACTIVE', 'SUBFLOW_COMPLETED'],
    workflowControlSignals: ['WF_EXECUTE_START', 'WF_EXECUTE_ABORT'],
    statusSignals: ['CMD_STATE', 'OP_STATE'],
  }
  assert.deepEqual(protocolSignalCandidates(protocol, { interfaceType: 'WORKFLOW', direction: 'OUT' }, false), ['ACTIVE'])
  assert.deepEqual(protocolSignalCandidates(protocol, { interfaceType: 'STATE', direction: 'OUT' }, false), protocol.workflowControlSignals)
  assert.deepEqual(protocolSignalCandidates(protocol, { interfaceType: 'STATE', direction: 'IN' }, false), protocol.statusSignals)
  assert.deepEqual(protocolSignalCandidates(protocol, { interfaceType: 'WORKFLOW', direction: 'IN' }, true), protocol.workflowNodeSignals)
})

test('workflow assignment expression serializes mentions and validates temporal functions', () => {
  assert.equal(serializeWorkflowExpression('@temperature / 100'), 'temperature / 100')
  const variables = [{ name: 'temperature', dataType: 'DOUBLE' }, { name: 'temperatureRate', dataType: 'DOUBLE' }]
  assert.deepEqual(validateWorkflowExpression('@temperatureRate = abs(rate(@temperature, 10))', variables, { assignment: true, temporal: true }), [])
  assert.match(validateWorkflowExpression('@temperature / 100', variables, { assignment: true })[0], /赋值/)
  assert.match(validateWorkflowExpression('@unknown + 1', variables)[0], /未知变量/)
  assert.match(validateWorkflowExpression('rate(@temperature, 10)', variables)[0], /不支持函数/)
  assert.deepEqual(workflowTemporalFunctions.map(item => item.name), ['变化速率', '变化量', '窗口平均值', '窗口最大值', '窗口最小值', '绝对值'])
})
