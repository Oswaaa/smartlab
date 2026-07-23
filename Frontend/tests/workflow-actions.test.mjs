import assert from 'node:assert/strict'
import { after, test } from 'node:test'
import { createServer } from 'vite'

const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
const actions = await server.ssrLoadModule('/src/views/task/workflowActions.js')
after(() => server.close())

const catalog = [
  { actionName: 'WAIT', allowedNodeTypes: ['DEVICE_CAPABILITY_NODE', 'FUNCTIONAL_NODE'] },
  { actionName: 'ASSIGN', allowedNodeTypes: ['DEVICE_CAPABILITY_NODE', 'FUNCTIONAL_NODE'] },
  { actionName: 'CALCULATE', allowedNodeTypes: ['DEVICE_CAPABILITY_NODE', 'FUNCTIONAL_NODE'] },
  { actionName: 'EMIT_SIGNAL', allowedNodeTypes: ['DEVICE_CAPABILITY_NODE'] }
]

test('filters actions by schema catalog and creates canonical device defaults', () => {
  assert.deepEqual(actions.actionsAllowedForNode(catalog, 'FUNCTIONAL_NODE').map(item => item.actionName),
    ['WAIT', 'ASSIGN', 'CALCULATE'])
  assert.deepEqual(actions.defaultActionsForNode('DEVICE_CAPABILITY_NODE', catalog, ['WF_EXECUTE_START']), [{
    actionName: 'EMIT_SIGNAL', payload: { interfaceType: 'WORKFLOW', signalName: 'WF_EXECUTE_START' }
  }])
})

test('validates action ordering without a local fallback catalog', () => {
  const valid = [
    actions.createWorkflowAction(catalog, 'WAIT'),
    actions.createWorkflowAction(catalog, 'ASSIGN'),
    actions.createWorkflowAction(catalog, 'EMIT_SIGNAL')
  ]
  assert.equal(actions.validateAndOrderActions(valid, 'DEVICE_CAPABILITY_NODE', catalog).length, 3)
  assert.throws(() => actions.validateAndOrderActions([valid[1], valid[0], valid[2]], 'DEVICE_CAPABILITY_NODE', catalog), /WAIT/)
  assert.throws(() => actions.createWorkflowAction(catalog, 'UNKNOWN'), /未声明/)
})
