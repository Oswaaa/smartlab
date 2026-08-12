import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const srcRoot = fileURLToPath(new URL('../src', import.meta.url))

function vueFilesUnder(relativeDirectory) {
  const root = path.join(srcRoot, relativeDirectory)
  const files = []
  const visit = current => {
    for (const name of readdirSync(current)) {
      const target = path.join(current, name)
      if (statSync(target).isDirectory()) visit(target)
      else if (name.endsWith('.vue')) files.push(target)
    }
  }
  visit(root)
  return files
}

test('all standard task-page Element Plus buttons use the device-model button system', () => {
  const files = [
    ...vueFilesUnder('views/task/TaskList'),
    ...vueFilesUnder('views/task/WorkflowDesigner'),
    path.join(srcRoot, 'components/constraint/ConstraintRuleEditor.vue'),
  ]

  const missing = []
  for (const file of files) {
    const source = readFileSync(file, 'utf8')
    for (const match of source.matchAll(/<el-button\b[\s\S]*?>/g)) {
      if (!/class="[^"]*\bbtn-aliyun(?:-cta|-link|-danger-link)?\b/.test(match[0])) {
        missing.push(`${path.relative(srcRoot, file)}: ${match[0].replace(/\s+/g, ' ')}`)
      }
    }
  }

  assert.deepEqual(missing, [])
})

test('task header refresh and workflow command buttons use the same visual language as device models', () => {
  const taskList = readFileSync(path.join(srcRoot, 'views/task/TaskList/TaskList.vue'), 'utf8')
  const designer = readFileSync(path.join(srcRoot, 'views/task/WorkflowDesigner/WorkflowDesigner.vue'), 'utf8')

  assert.match(taskList, /<el-button class="btn-aliyun" :icon="Refresh"[^>]*>刷新<\/el-button>/)
  assert.doesNotMatch(taskList, /refresh-button|:icon="Refresh"[^>]*\bcircle\b/)
  assert.doesNotMatch(designer, /island-btn/)
  assert.match(designer, /class="btn-aliyun-cta"[^>]*@click="publishAndValidate"/)
})
