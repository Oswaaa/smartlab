import test from 'node:test'
import assert from 'node:assert/strict'
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
  interfaces: [
    {
      name: 'Interface_workflow_in',
      direction: 'IN',
      interfaceType: 'WORKFLOW',
      allowedSignals: ['ACTIVE'],
      bindingTriggers: [{
        condition: { object: 'inputSignalName', operator: '=', threshold: 'ACTIVE' },
        action: 'emitActive',
        _system: true,
        _systemKey: 'aggregate.activeTrigger',
      }],
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
  actions: [{
    actionName: 'emitActive',
    actionType: 'EMIT',
    targetInterfaceName: 'Interface_workflow_out',
    signalName: 'ACTIVE',
    _system: true,
    _systemKey: 'aggregate.emitActive',
  }],
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
  node.actions.push({ actionName: 'setCounter', actionType: 'UPDATE', internalVariableName: 'counter', valueExpression: '1' })
  node.interfaces[0].bindingTriggers.push({
    condition: { object: 'counter', operator: '=', threshold: 0 },
    action: 'setCounter',
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
  assert.equal(restored.interfaces[0].bindingTriggers[0]._systemKey, 'aggregate.activeTrigger')
  assert.equal(restored.actions[0]._systemKey, 'aggregate.emitActive')
  assert.deepEqual(restored.internalVariables, node.internalVariables)
  assert.deepEqual(restored.ports, node.ports)
  assert.deepEqual(restored.actions[1], node.actions[1])
  assert.deepEqual(restored.interfaces[0].bindingTriggers[1], node.interfaces[0].bindingTriggers[1])
  assert.deepEqual(validateNodeDefinition(restored), [])
})

test('custom triggers can target only custom UPDATE actions', () => {
  const node = createFunctionNode('AGGREGATE', 'aggregate')
  node.internalVariables.push({ name: 'counter', dataType: 'INTEGER' })
  node.actions.push({ actionName: 'setCounter', actionType: 'UPDATE', internalVariableName: 'counter', valueExpression: '1' })
  assert.deepEqual(customTriggerActionNames(node), ['setCounter'])

  node.interfaces[0].bindingTriggers.push({
    condition: { object: 'counter', operator: '=', threshold: 0 },
    action: 'emitActive',
  })
  assert.ok(validateNodeDefinition(node).some(error => error.path.endsWith('.action')))

  node.interfaces[0].bindingTriggers.pop()
  node.actions.push({ actionName: 'customEmit', actionType: 'EMIT', targetInterfaceName: 'Interface_workflow_out', signalName: 'ACTIVE' })
  assert.ok(validateNodeDefinition(node).some(error => error.path === 'actions[2].actionType'))
})
