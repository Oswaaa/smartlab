import assert from 'node:assert/strict'
import { test } from 'node:test'

const lifecycle = await import('../src/views/device/components/deviceModel/deviceModelLifecycle.js')

const mainPath = [
  { fromStateName: 'IDLE', toStateName: 'SENT', triggerPolicy: 'SYSTEM' },
  { fromStateName: 'SENT', toStateName: 'RECEIVED', triggerPolicy: 'OPTIONAL' },
  { fromStateName: 'RECEIVED', toStateName: 'RUNNING', triggerPolicy: 'OPTIONAL' },
  { fromStateName: 'RUNNING', toStateName: 'COMPLETED', triggerPolicy: 'REQUIRED' }
]

test('builds normal lifecycle rows and an independent failure row for each active state', () => {
  const rows = lifecycle.buildLifecycleRuleRows(mainPath)
  assert.deepEqual(rows.map(row => row.key), [
    'main:SENT:RECEIVED',
    'main:RECEIVED:RUNNING',
    'main:RUNNING:COMPLETED',
    'failure:SENT:FAILED',
    'failure:RECEIVED:FAILED',
    'failure:RUNNING:FAILED'
  ])
})

test('optional stages without events serialize as CMD automatic transitions', () => {
  const rows = lifecycle.buildLifecycleRuleRows(mainPath)
  const bindings = {
    'main:RUNNING:COMPLETED': 'DONE'
  }
  const transitions = lifecycle.serializeLifecycleTransitions(rows, bindings, 'Interface_adapter_in')
  assert.equal(transitions[0].trigger, null)
  assert.equal(transitions[1].trigger, null)
  assert.deepEqual(transitions[2].trigger, {
    interfaceName: 'Interface_adapter_in',
    signalName: 'DONE'
  })
})

test('required lifecycle stages reject missing events', () => {
  const rows = lifecycle.buildLifecycleRuleRows(mainPath)
  assert.throws(
    () => lifecycle.serializeLifecycleTransitions(rows, {}, 'Interface_adapter_in'),
    /RUNNING → COMPLETED 必须绑定 Adapter cmdEvent/
  )
})

test('failure branches use independent events and blank branches are omitted', () => {
  const rows = lifecycle.buildLifecycleRuleRows(mainPath)
  const transitions = lifecycle.serializeLifecycleTransitions(rows, {
    'main:RUNNING:COMPLETED': 'DONE',
    'failure:SENT:FAILED': 'REJECTED',
    'failure:RUNNING:FAILED': 'FAILED'
  }, 'Interface_adapter_in')
  assert.deepEqual(
    transitions.filter(row => row.toStateName === 'FAILED').map(row => [row.fromStateName, row.trigger.signalName]),
    [['SENT', 'REJECTED'], ['RUNNING', 'FAILED']]
  )
})

test('hydrates each transition binding without collapsing failure events', () => {
  const rows = lifecycle.buildLifecycleRuleRows(mainPath)
  const bindings = lifecycle.hydrateLifecycleBindings([
    { stateSpace: 'CMD', fromStateName: 'RUNNING', toStateName: 'COMPLETED', trigger: { signalName: 'DONE' } },
    { stateSpace: 'CMD', fromStateName: 'SENT', toStateName: 'FAILED', trigger: { signalName: 'REJECTED' } },
    { stateSpace: 'CMD', fromStateName: 'RUNNING', toStateName: 'FAILED', trigger: { signalName: 'FAILED' } }
  ], rows)
  assert.equal(bindings['main:RUNNING:COMPLETED'], 'DONE')
  assert.equal(bindings['failure:SENT:FAILED'], 'REJECTED')
  assert.equal(bindings['failure:RUNNING:FAILED'], 'FAILED')
})
