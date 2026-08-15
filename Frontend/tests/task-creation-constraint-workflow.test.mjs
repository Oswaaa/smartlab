import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import { reviewTaskConstraintsAfterBindingChange } from '../src/views/task/TaskList/taskConstraintReview.js'

const srcRoot = fileURLToPath(new URL('../src', import.meta.url))
const readSource = relativePath => readFileSync(path.join(srcRoot, relativePath), 'utf8')

test('task creation drawer keeps task constraints reachable in its own scroll area', () => {
  const drawer = readSource('views/task/TaskList/components/TaskCreateDrawer.vue')

  assert.match(drawer, /class="drawer-body unified-drawer-scroll"/)
  assert.match(drawer, /<TaskConstraintPanel/)
})

test('task creation binds devices by clicking nodes in the workflow graph while keeping backend slot ids', () => {
  const drawer = readSource('views/task/TaskList/components/TaskCreateDrawer.vue')
  const bindingPanel = readSource('views/task/TaskList/components/TaskResourceBindingCanvas.vue')
  const taskList = readSource('views/task/TaskList/TaskList.vue')

  assert.match(drawer, /:requirements="requirements"/)
  assert.match(drawer, /:groups="groups"/)
  assert.match(drawer, /设备实例绑定/)
  assert.match(bindingPanel, /<VueFlow/)
  assert.match(bindingPanel, /TaskBindingWorkflowNode/)
  assert.match(bindingPanel, /@node-click="handleNodeClick"/)
  assert.match(bindingPanel, /进入子流程|enterChildFlow/)
  assert.match(bindingPanel, /modelValue\[selectedRequirement\.slotId\]/)
  assert.match(taskList, /:groups="selectedWorkflowGroups"/)
  assert.match(taskList, /buildBindingWorkflowView\(expanded, requirements\)\.errors/)
  assert.doesNotMatch(taskList, /bindRequirementsToExpandedWorkflow/)
})

test('changing device bindings preserves rules and marks only affected rules for review', () => {
  const taskList = readSource('views/task/TaskList/TaskList.vue')
  const panel = readSource('views/task/TaskList/components/TaskConstraintPanel.vue')

  assert.match(taskList, /reviewTaskConstraintsAfterBindingChange/)
  const bindingUpdate = taskList.match(/const updateResourceBindings[\s\S]*?\n}/)?.[0] || ''
  assert.doesNotMatch(bindingUpdate, /taskConstraints\s*=\s*\[\]/)
  assert.match(taskList, /taskConstraintReviews/)
  assert.match(panel, /需要复核/)
})

test('unresolved task-constraint reviews block preflight and task creation', () => {
  const taskList = readSource('views/task/TaskList/TaskList.vue')

  assert.match(taskList, /unresolvedTaskConstraintReviews/)
  assert.match(taskList, /请先复核受设备绑定变更影响的任务约束/)
})

test('binding review marks only rules that reference a device removed from task bindings', () => {
  const rules = [
    {
      bindings: { temperature: { bindingType:'OBSERVABLE', source:{ sourceType:'DEVICE_ATTRIBUTE', deviceInstanceId:10 } } },
      violationActions: [{ actionType:'SYSTEM', action:'ALERT' }]
    },
    {
      bindings: { state: { bindingType:'OBSERVABLE', source:{ sourceType:'TASK_LIFECYCLE' } } },
      violationActions: [{ actionType:'SYSTEM', action:'ALERT' }]
    },
    {
      bindings: { threshold: { bindingType:'LITERAL', value:200 } },
      violationActions: [{ actionType:'DEVICE_CAPABILITY', deviceInstanceId:30, capabilityName:'STOP' }]
    }
  ]

  const reviews = reviewTaskConstraintsAfterBindingChange(rules, { reactor:10, pump:30 }, { reactor:20, pump:30 })
  assert.equal(Boolean(reviews[0]), true)
  assert.equal(reviews[1], null)
  assert.equal(reviews[2], null)
})

test('binding review keeps a rule valid when its device remains bound through another route', () => {
  const rule = { bindings:{ temperature:{ bindingType:'OBSERVABLE', source:{ deviceInstanceId:10 } } }, violationActions:[] }
  const reviews = reviewTaskConstraintsAfterBindingChange([rule], { primary:10 }, { primary:20, backup:10 })
  assert.deepEqual(reviews, [null])
})
