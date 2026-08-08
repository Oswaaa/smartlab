import assert from 'node:assert/strict'
import { test } from 'node:test'

import { normalizeManualControlCapabilities } from '../src/utils/manualControlCapabilities.js'

test('manual control keeps the model capability name distinct from the adapter command name', () => {
  const [capability] = normalizeManualControlCapabilities([{
    capabilityName: 'startHeating',
    displayName: '开始加热',
    adapterCommandName: 'PLC_HEAT_START',
    parameters: [{ name: 'targetTemperature', dataType: 'DOUBLE' }]
  }])

  assert.equal(capability.capabilityName, 'startHeating')
  assert.equal(capability.adapterCommandName, 'PLC_HEAT_START')
  assert.equal(capability.displayName, '开始加热')
  assert.equal(capability.parameters.length, 1)
})

test('manual control does not use an adapter command as a missing model capability identity', () => {
  const capabilities = normalizeManualControlCapabilities([{ adapterCommandName: 'PLC_HEAT_START' }])
  assert.deepEqual(capabilities, [])
})
