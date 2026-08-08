import test from 'node:test'
import assert from 'node:assert/strict'
import {
  adoptPreparedWorkflow,
  indexWorkflowIssues,
  toAuthoringPayload,
} from '../src/utils/workflowAuthoring.js'

const editorWorkflow = {
  id: 7,
  name: 'Heating flow',
  nodesDef: [{
    name: 'start',
    nodeType: 'FUNC_NODE',
    functionType: 'START',
    position: { x: 10, y: 20 },
    lifecycle: {
      _system: true,
      _systemKey: 'start.lifecycle',
      states: ['HACKED'],
    },
    interfaces: [],
    ports: [],
    actions: [],
  }],
  interfaceConnections: [],
  portConnections: [],
}

const canonicalWorkflow = {
  ...editorWorkflow,
  nodesDef: [{
    ...editorWorkflow.nodesDef[0],
    lifecycle: {
      _system: true,
      _systemKey: 'start.lifecycle',
      states: ['PENDING'],
    },
  }],
}

test('authoring payload preserves stable system keys but server response replaces system values', () => {
  const payload = toAuthoringPayload(editorWorkflow)
  assert.equal(payload.nodesDef[0].lifecycle._systemKey, 'start.lifecycle')
  assert.equal(Boolean(payload.nodesDef[0].lifecycle.states?.includes('HACKED')), false)
  assert.equal(payload.nodesDef[0].position, undefined)

  const adopted = adoptPreparedWorkflow({ definition: canonicalWorkflow, issues: [] })
  assert.deepEqual(adopted.nodesDef, canonicalWorkflow.nodesDef)
})

test('issues are indexed by model element instead of raw JSON path', () => {
  const index = indexWorkflowIssues([{ elementType: 'PORT', elementId: 'temperatureOut', blocking: true }])
  assert.equal(index.byElement.PORT.temperatureOut.length, 1)
})
