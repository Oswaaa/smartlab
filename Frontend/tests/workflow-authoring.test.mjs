import test from 'node:test'
import assert from 'node:assert/strict'
import { indexWorkflowIssues } from '../src/utils/workflowAuthoring.js'

test('issues are indexed by model element instead of raw JSON path', () => {
  const index = indexWorkflowIssues([{ elementType: 'PORT', elementId: 'temperatureOut', blocking: true }])
  assert.equal(index.byElement.PORT.temperatureOut.length, 1)
})
