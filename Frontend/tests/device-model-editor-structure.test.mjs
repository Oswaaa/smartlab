import assert from 'node:assert/strict'
import fs from 'node:fs'
import { test } from 'node:test'

const source = fs.readFileSync(
  new URL('../src/views/device/components/deviceModel/DeviceModelEditorDrawer.vue', import.meta.url),
  'utf8'
)

test('device model editor contains mapping and state-machine workspaces', () => {
  for (const marker of [
    'id="edit-mapping"',
    'id="edit-state"',
    '属性映射',
    '操作映射',
    '执行生命周期',
    '功能状态',
    '功能状态转移规则'
  ]) {
    assert.ok(source.includes(marker), `missing editor marker: ${marker}`)
  }
})

test('adapter contract is explicitly read-only while mappings remain editable', () => {
  assert.ok(source.includes('Adapter 定义由注册配置提供'))
  assert.ok(source.includes('@click="addAttributeMapping"'))
  assert.ok(source.includes('@click="addFunctionMapping"'))
  assert.equal(source.includes('@click="addAdapterCommand"'), false)
  assert.equal(source.includes('@click="addAdapterAttribute"'), false)
})
