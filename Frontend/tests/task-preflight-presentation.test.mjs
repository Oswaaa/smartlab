import test from 'node:test'
import assert from 'node:assert/strict'
import { presentPreflightIssue } from '../src/views/task/TaskList/taskPreflightPresentation.js'

test('presents missing device bindings as a concise business message', () => {
  assert.deepEqual(presentPreflightIssue({
    code: 'TASK_BINDING_MISSING',
    message: 'DEV_NODE缺少任务设备实例绑定',
    suggestion: '为该槽位选择一个可用设备实例'
  }), {
    title: '设备未全部绑定',
    detail: '请为所有设备节点选择可用的设备实例'
  })
})

test('uses backend message and suggestion for unknown preflight issues', () => {
  assert.deepEqual(presentPreflightIssue({
    code: 'CUSTOM_CHECK',
    message: '设备配置冲突',
    suggestion: '重新选择设备实例'
  }), {
    title: '设备配置冲突',
    detail: '重新选择设备实例'
  })
})
