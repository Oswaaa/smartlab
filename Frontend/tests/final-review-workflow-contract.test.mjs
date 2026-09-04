import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { sanitizeWorkflowPayload } from '../src/utils/workflowCanvas.js'
import {
  configureWorkflowNodeTemplates,
  createFunctionNode,
  customTriggerActionNames,
  rehydrateWorkflowNodes,
  validateNodeDefinition,
} from '../src/utils/workflowNodeDefinition.js'

const aggregateTemplate = {
  lifecycle: {
    initialStateName: 'PENDING',
    states: ['PENDING', 'RUNNING'],
    transitions: [{
      fromStateName: 'PENDING',
      toStateName: 'RUNNING',
      _system: true,
      _systemKey: 'aggregate.lifecycle.pending.running',
    }],
    _system: true,
    _systemKey: 'aggregate.lifecycle',
  },
  internalVariables: [{
    name: 'aggregateCount',
    dataType: 'INTEGER',
    initialValue: 0,
    _system: true,
    _systemKey: 'aggregate.count',
  }],
  interfaces: [
    {
      name: 'Interface_workflow_in',
      direction: 'IN',
      interfaceType: 'WORKFLOW',
      allowedSignals: ['ACTIVE'],
      bindingTriggers: [
        {
          condition: { object: 'signalName', operator: '=', threshold: 'ACTIVE' },
          action: { actionName: 'UPDATE', payload: { updateType: 'INTERNAL_VARIABLE', targetName: 'aggregateCount', valueExpression: 'aggregateCount + 1' } },
          _system: true,
          _systemKey: 'aggregate.countInput',
        },
        {
          condition: {
            logic: 'AND',
            conditions: [
              { object: 'aggregateCount', operator: '>', threshold: 0 },
              { object: 'nodeLifecycleState', operator: '=', threshold: 'PENDING' },
            ],
          },
          action: { actionName: 'UPDATE', payload: { updateType: 'NODE_LIFECYCLE', targetName: 'RUNNING' } },
          _system: true,
          _systemKey: 'aggregate.activate',
        },
      ],
      _system: true,
      _systemKey: 'aggregate.workflowIn',
    },
    {
      name: 'Interface_workflow_out',
      direction: 'OUT',
      interfaceType: 'WORKFLOW',
      allowedSignals: ['ACTIVE'],
      bindingTriggers: [],
      _system: true,
      _systemKey: 'aggregate.workflowOut',
    },
  ],
  actions: ['UPDATE', 'EMIT'],
}

configureWorkflowNodeTemplates({
  START: {},
  END: {},
  BRANCH: {},
  AGGREGATE: aggregateTemplate,
  DEV_NODE: {},
  SUBFLOW_NODE: {},
})

test('markerless workflow round trip restores system markers and preserves custom data', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  node.internalVariables.push({ name: 'counter', dataType: 'INTEGER' })
  node.ports.push({ name: 'counterOut', direction: 'OUT', internalVariableName: 'counter' })
  node.interfaces[1].bindingTriggers.push({
    condition: { object: 'counter', operator: '=', threshold: 0 },
    action: { actionName: 'UPDATE', payload: { updateType: 'INTERNAL_VARIABLE', targetName: 'counter', value: 1 } },
  })

  const payload = sanitizeWorkflowPayload({ nodesDef: [node] })
  const systemCondition = payload.nodesDef[0].interfaces[0].bindingTriggers[0].condition
  payload.nodesDef[0].interfaces[0].bindingTriggers[0].condition = {
    threshold: systemCondition.threshold,
    operator: systemCondition.operator,
    object: systemCondition.object,
  }
  assert.equal(JSON.stringify(payload).includes('_system'), false)

  const [restored] = rehydrateWorkflowNodes(payload.nodesDef)
  assert.equal(restored.lifecycle._systemKey, 'aggregate.lifecycle')
  assert.equal(restored.lifecycle.transitions[0]._systemKey, 'aggregate.lifecycle.pending.running')
  assert.equal(restored.interfaces[0]._systemKey, 'aggregate.workflowIn')
  assert.equal(restored.internalVariables[0]._systemKey, 'aggregate.count')
  assert.equal(restored.interfaces[0].bindingTriggers[0]._systemKey, 'aggregate.countInput')
  assert.equal(restored.interfaces[0].bindingTriggers[1]._systemKey, 'aggregate.activate')
  assert.deepEqual(restored.internalVariables, node.internalVariables)
  assert.deepEqual(restored.ports, node.ports)
  assert.deepEqual(restored.actions, ['UPDATE', 'EMIT'])
  const customTrigger = node.interfaces[1].bindingTriggers[0]
  assert.deepEqual(restored.interfaces[1].bindingTriggers[0], {
    ...customTrigger,
    actions: [customTrigger.action],
  })
  assert.deepEqual(validateNodeDefinition(restored), [])
})

test('inline triggers support UPDATE constants and EMIT on output interfaces', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  node.internalVariables.push({ name: 'counter', dataType: 'INTEGER' })
  assert.deepEqual(customTriggerActionNames(node), ['UPDATE', 'EMIT'])

  node.interfaces[1].bindingTriggers.push({
    condition: { object: 'counter', operator: '=', threshold: 0 },
    action: { actionName: 'UPDATE', payload: { updateType: 'INTERNAL_VARIABLE', targetName: 'counter', value: 200 } },
  })
  assert.deepEqual(validateNodeDefinition(node), [])

  node.interfaces[1].bindingTriggers[0].action.payload.valueExpression = 'counter + 1'
  assert.ok(validateNodeDefinition(node).some(error => error.path.endsWith('.action.payload')))
})

test('designer adopts prepared definitions and restores fixed capabilities for persisted details', () => {
  const designer = readFileSync(fileURLToPath(new URL('../src/views/task/WorkflowDesigner/WorkflowDesigner.vue', import.meta.url)), 'utf8')

  assert.match(designer, /workflowApi\.saveDraft/)
  assert.match(designer, /workflowApi\.saveAsNew/)
  assert.match(designer, /workflowApi\.validate/)
  assert.match(designer, /workflowApi\.simulate/)
  assert.match(designer, /workflowApi\.publish/)
  assert.match(designer, /adoptPreparedWorkflow/)
  assert.match(designer, /indexWorkflowIssues/)
  assert.match(designer, /rehydrateWorkflowNodes/)
})
