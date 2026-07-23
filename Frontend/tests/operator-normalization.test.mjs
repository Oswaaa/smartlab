import assert from 'node:assert/strict'
import { after, test } from 'node:test'
import { createServer } from 'vite'

const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
const { applyProtocolMetadata } = await server.ssrLoadModule('/src/views/device/components/deviceModel/deviceModelConstants.js')
applyProtocolMetadata({
  constraint: { operators: ['>', '<', '>=', '<=', '=', '!=', 'BETWEEN', 'IN'] }
})
const { normalizeOperator } = await server.ssrLoadModule('/src/views/device/components/deviceModel/normalizers.js')

after(() => server.close())

test('rejects deprecated mnemonic constraint operators', () => {
  assert.throws(() => normalizeOperator('GT'), /不支持的约束操作符/)
  assert.throws(() => normalizeOperator('LE'), /不支持的约束操作符/)
})

test('keeps canonical protocol operators unchanged', () => {
  assert.equal(normalizeOperator('>'), '>')
  assert.equal(normalizeOperator('IN'), 'IN')
})

test('rejects operators outside the canonical and persisted sets', () => {
  assert.throws(() => normalizeOperator('LIKE'), /不支持的约束操作符/)
})
