import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import {
  selectPreferredStepId,
  statusLabel,
  statusType,
} from '../src/views/task/TaskList/taskExecutionPresentation.js'

test('prefers the running step when opening task execution detail', () => {
  const steps = [
    { id: 11, nodeStatus: 'SUCCEEDED' },
    { id: 12, nodeStatus: 'RUNNING' },
    { id: 13, nodeStatus: 'PENDING' },
  ]

  assert.equal(selectPreferredStepId(steps, null), 12)
})

test('preserves a valid explicit step selection', () => {
  const steps = [
    { id: 11, nodeStatus: 'SUCCEEDED' },
    { id: 12, nodeStatus: 'RUNNING' },
  ]

  assert.equal(selectPreferredStepId(steps, 11), 11)
})

test('falls back to the most recent step when none is running', () => {
  const steps = [
    { id: 11, nodeStatus: 'SUCCEEDED' },
    { id: 12, nodeStatus: 'FAILED' },
  ]

  assert.equal(selectPreferredStepId(steps, null), 12)
  assert.equal(selectPreferredStepId([], null), null)
})

test('uses the task status vocabulary and visual type shown in the drawer', () => {
  assert.equal(statusLabel('PENDING'), '排队中')
  assert.equal(statusLabel('SUCCEEDED'), '已完成')
  assert.equal(statusLabel('TERMINATED'), '已终止')
  assert.equal(statusType('RUNNING'), 'primary')
  assert.equal(statusType('FAILED'), 'danger')
  assert.equal(statusType('UNKNOWN'), 'info')
})

test('opens task detail only from the explicit detail action', async () => {
  const source = await readFile(new URL('../src/views/task/TaskList/TaskList.vue', import.meta.url), 'utf8')
  const template = source.slice(0, source.indexOf('<script setup'))

  assert.doesNotMatch(template, /@row-click=/)
  assert.match(template, />详情<\/el-button>/)
  assert.match(template, /@click="openTaskDetail\(row\)"/)
})

test('mounts the runtime graph only after the execution drawer viewport is ready', async () => {
  const source = await readFile(new URL('../src/views/task/TaskList/components/TaskExecutionDrawer.vue', import.meta.url), 'utf8')

  assert.match(source, /@open="handleDrawerOpening"/)
  assert.match(source, /@opened="handleDrawerOpened"/)
  assert.match(source, /@closed="handleDrawerClosed"/)
  assert.match(source, /<TaskRuntimeGraph\s+v-if="graphReady"/)
  assert.match(source, /const graphReady = ref\(false\)/)
})
