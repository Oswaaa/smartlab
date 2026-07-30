import assert from 'node:assert/strict'
import { test } from 'node:test'

const lifecycle = await import('../src/views/device/components/deviceModel/deviceModelLifecycle.js')

const requirements = [
  { kind: 'MAIN', fromStateName: 'SENT', toStateName: 'RUNNING', triggerPolicy: 'REQUIRED' },
  { kind: 'MAIN', fromStateName: 'RUNNING', toStateName: 'COMPLETED', triggerPolicy: 'REQUIRED' },
  { kind: 'FAILURE', fromStateName: 'RUNNING', toStateName: 'FAILED', triggerPolicy: 'REQUIRED' },
  { kind: 'TERMINATION', fromStateName: 'ABORTING', toStateName: 'ABORTED', triggerPolicy: 'REQUIRED' }
]

test('builds exactly the four adapter-driven command lifecycle transitions from the final contract', () => {
  const rows = lifecycle.buildLifecycleRuleRows(requirements)
  assert.deepEqual(rows.map(row => row.key), [
    'main:SENT:RUNNING',
    'main:RUNNING:COMPLETED',
    'failure:RUNNING:FAILED',
    'termination:ABORTING:ABORTED'
  ])
})

test('serializes every adapter-driven command transition with its real cmdEvent', () => {
  const rows = lifecycle.buildLifecycleRuleRows(requirements)
  const bindings = {
    'main:SENT:RUNNING': 'STARTED',
    'main:RUNNING:COMPLETED': 'DONE',
    'failure:RUNNING:FAILED': 'FAILED',
    'termination:ABORTING:ABORTED': 'ABORTED'
  }
  const transitions = lifecycle.serializeLifecycleTransitions(rows, bindings, 'Interface_adapter_in')
  assert.deepEqual(
    transitions.map(row => [row.fromStateName, row.toStateName, row.trigger.signalName]),
    [
      ['SENT', 'RUNNING', 'STARTED'],
      ['RUNNING', 'COMPLETED', 'DONE'],
      ['RUNNING', 'FAILED', 'FAILED'],
      ['ABORTING', 'ABORTED', 'ABORTED']
    ]
  )
})

test('rejects saving when any required adapter lifecycle event is missing', () => {
  const rows = lifecycle.buildLifecycleRuleRows(requirements)
  assert.throws(
    () => lifecycle.serializeLifecycleTransitions(rows, {}, 'Interface_adapter_in'),
    /SENT → RUNNING 必须绑定 Adapter cmdEvent/
  )
})

test('does not create SENT failure or direct SENT/RUNNING to ABORTED transitions', () => {
  const rows = lifecycle.buildLifecycleRuleRows(requirements)
  assert.equal(rows.some(row => row.fromStateName === 'SENT' && row.toStateName === 'FAILED'), false)
  assert.equal(rows.some(row => ['SENT', 'RUNNING'].includes(row.fromStateName) && row.toStateName === 'ABORTED'), false)
})

test('hydrates each final lifecycle event binding by its from and to state', () => {
  const rows = lifecycle.buildLifecycleRuleRows(requirements)
  const bindings = lifecycle.hydrateLifecycleBindings([
    { stateSpace: 'CMD', fromStateName: 'SENT', toStateName: 'RUNNING', trigger: { signalName: 'STARTED' } },
    { stateSpace: 'CMD', fromStateName: 'RUNNING', toStateName: 'COMPLETED', trigger: { signalName: 'DONE' } },
    { stateSpace: 'CMD', fromStateName: 'RUNNING', toStateName: 'FAILED', trigger: { signalName: 'FAILED' } },
    { stateSpace: 'CMD', fromStateName: 'ABORTING', toStateName: 'ABORTED', trigger: { signalName: 'ABORTED' } }
  ], rows)
  assert.equal(bindings['main:SENT:RUNNING'], 'STARTED')
  assert.equal(bindings['main:RUNNING:COMPLETED'], 'DONE')
  assert.equal(bindings['failure:RUNNING:FAILED'], 'FAILED')
  assert.equal(bindings['termination:ABORTING:ABORTED'], 'ABORTED')
})