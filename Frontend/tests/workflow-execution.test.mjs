import assert from 'node:assert/strict'
import { test } from 'node:test'

import { filterExecutableWorkflows, isExecutableWorkflow } from '../src/utils/workflowExecution.js'

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
