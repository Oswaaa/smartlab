import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const readSource = relativePath => readFileSync(new URL(`../src/${relativePath}`, import.meta.url), 'utf8')

test('taskApi matches the TaskController routes used by task runtime details', () => {
  const source = readSource('services/taskApi.js')

  assert.match(source, /list:\s*\(params\)\s*=>\s*axios\.get\('\/api\/task\/page'/)
  assert.match(source, /detail:\s*\(id\)\s*=>\s*axios\.get\(`\/api\/task\/\$\{id\}`\)/)
  assert.match(source, /terminate:\s*\(id\)\s*=>\s*axios\.post\(`\/api\/task\/terminate\/\$\{id\}`\)/)
  assert.match(source, /delete:\s*\(id\)\s*=>\s*axios\.delete\(`\/api\/task\/delete\/\$\{id\}`\)/)
  assert.match(source, /snapshots:\s*\(id\)\s*=>\s*axios\.get\(`\/api\/task\/snapshots\/\$\{id\}`\)/)
  assert.match(source, /executionLogs:\s*\(id,\s*params\)\s*=>\s*axios\.get\(`\/api\/task\/logs\/\$\{id\}`,\s*\{\s*params\s*\}\)/)
  assert.match(source, /executionView:\s*\(id\)\s*=>\s*axios\.get\(`\/api\/task\/execution-view\/\$\{id\}`\)/)
})

test('TaskList loads task details through the centralized taskApi contract', () => {
  const source = readSource('views/task/TaskList/TaskList.vue')

  assert.match(source, /taskApi\.snapshots\(taskId\)/)
  assert.match(source, /taskApi\.executionLogs\(taskId,\s*logParams\)/)
  assert.match(source, /taskApi\.detail\(taskId\)/)
  assert.doesNotMatch(source, /axios\.get\(`\/api\/task\/snapshots\/\$\{taskId\}`\)/)
})
