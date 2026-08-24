import test from 'node:test'
import assert from 'node:assert/strict'
import { formatLogClock, formatLogDateTime } from '../src/utils/formatLogTime.js'

test('formats log timestamps with milliseconds', () => {
  const date = new Date(2026, 7, 22, 0, 36, 44, 123)

  assert.equal(formatLogClock(date), '00:36:44.123')
  assert.equal(formatLogDateTime(date), '2026/8/22 00:36:44.123')
})

test('keeps unparseable log timestamps visible', () => {
  assert.equal(formatLogDateTime(null), '-')
  assert.equal(formatLogDateTime(''), '-')
  assert.equal(formatLogDateTime('not-a-date'), 'not-a-date')
})
