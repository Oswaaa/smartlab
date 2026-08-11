# Task List Detail Drawer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将任务列表改造成完整列表工作区，并通过 78% 右侧抽屉承载任务运行详情。

**Architecture:** 保留 `TaskList.vue` 负责列表查询和抽屉数据装载，`TaskExecutionDrawer.vue` 负责详情展示与步骤选择。新增无 UI 依赖的展示推导模块，使状态映射和默认步骤选择可直接用 Node 测试验证；现有运行接口、轮询与业务子组件保持不变。

**Tech Stack:** Vue 3 Composition API、TypeScript SFC、Element Plus、Node.js `node:test`、Vite

## Global Constraints

- 桌面端详情抽屉宽度固定为 `78%`，窄屏为 `100%`。
- 只有显式点击“详情”按钮才打开任务详情，普通行点击不触发。
- 抽屉页签为运行概览、执行流程图、步骤详情、设备绑定、任务约束、业务事件。
- 默认页签为执行流程图，并优先选择状态为 `RUNNING` 的步骤。
- 不修改后端 API、任务状态协议和详情轮询周期。
- 不增加新的前端依赖。

---

### Task 1: 详情展示推导逻辑

**Files:**
- Create: `Frontend/src/views/task/TaskList/taskExecutionPresentation.js`
- Create: `Frontend/tests/task-execution-presentation.test.mjs`
- Modify: `Frontend/src/views/task/TaskList/components/TaskExecutionDrawer.vue`

**Interfaces:**
- Produces: `statusType(status)`, `statusLabel(status)`, `selectPreferredStepId(steps, preferredId)`。
- Consumes: 任务步骤对象中的 `id` 与 `nodeStatus` 字段。

- [ ] **Step 1: Write the failing test**

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import { selectPreferredStepId, statusLabel } from '../src/views/task/TaskList/taskExecutionPresentation.js'

test('prefers the running step when opening task execution detail', () => {
  const steps = [{ id: 11, nodeStatus: 'SUCCEEDED' }, { id: 12, nodeStatus: 'RUNNING' }]
  assert.equal(selectPreferredStepId(steps, null), 12)
})

test('preserves a valid explicit selection', () => {
  const steps = [{ id: 11, nodeStatus: 'SUCCEEDED' }, { id: 12, nodeStatus: 'RUNNING' }]
  assert.equal(selectPreferredStepId(steps, 11), 11)
})

test('uses the task status vocabulary shown in the drawer', () => {
  assert.equal(statusLabel('PENDING'), '排队中')
  assert.equal(statusLabel('SUCCEEDED'), '已完成')
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test tests/task-execution-presentation.test.mjs`

Expected: FAIL because `taskExecutionPresentation.js` does not exist.

- [ ] **Step 3: Write minimal implementation**

```js
export function selectPreferredStepId(steps = [], preferredId = null) {
  if (steps.some(step => step.id === preferredId)) return preferredId
  return steps.find(step => step.nodeStatus === 'RUNNING')?.id ?? steps.at(-1)?.id ?? null
}
```

Move the existing status type and label maps into the same module and import all three functions from `TaskExecutionDrawer.vue`.

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test tests/task-execution-presentation.test.mjs`

Expected: PASS, 3 tests and 0 failures.

### Task 2: 任务列表显式详情交互与页面收敛

**Files:**
- Modify: `Frontend/src/views/task/TaskList/TaskList.vue`

**Interfaces:**
- Consumes: existing `handleRowClick` data-loading behavior, renamed to `openTaskDetail`.
- Produces: explicit detail-button interaction; full-width list shell.

- [ ] **Step 1: Add a failing source contract test**

Extend `Frontend/tests/task-execution-presentation.test.mjs` to parse `TaskList.vue` and assert that the `el-table` does not register `@row-click`, while the detail action calls `openTaskDetail(row)`.

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test tests/task-execution-presentation.test.mjs`

Expected: FAIL because the current table still contains `@row-click="handleRowClick"`.

- [ ] **Step 3: Implement the explicit detail interaction**

Remove the table row-click binding, rename `handleRowClick` to `openTaskDetail`, and bind only each “详情” button to it. Preserve the existing active-task assignment, detail fetching, constraint fetching and polling setup.

- [ ] **Step 4: Consolidate the page styles**

Delete obsolete and duplicate legacy style blocks from `TaskList.vue`. Keep one scoped console-style layout: full-height page, unified header/summary/filter/table/pagination card, compact status tags, and responsive padding.

- [ ] **Step 5: Run test to verify it passes**

Run: `node --test tests/task-execution-presentation.test.mjs`

Expected: PASS with explicit-button behavior protected.

### Task 3: 详情抽屉结构与运行流程图默认态

**Files:**
- Modify: `Frontend/src/views/task/TaskList/components/TaskExecutionDrawer.vue`
- Modify: `Frontend/src/views/task/TaskList/components/TaskRuntimeGraph.vue` only if visual inspection reveals overflow or selection affordance defects.

**Interfaces:**
- Consumes: `selectPreferredStepId`, existing drawer props and events.
- Produces: one-layer custom header, default graph tab, responsive 78% drawer and selected current step details.

- [ ] **Step 1: Apply tested default-step selection**

Set `activeTab` to `graph` and update the steps watcher to call `selectPreferredStepId(current, selectedStepId.value)`.

- [ ] **Step 2: Replace the duplicate drawer title hierarchy**

Use the Element Plus drawer header slot for task title, metadata, status, refresh and terminate actions. Remove the second `execution-header` from the drawer body.

- [ ] **Step 3: Build the drawer content hierarchy**

Keep the six existing tabs, make the tabs/body consume available height, and style the graph view as a large canvas plus a 340–360px current-step panel. Preserve existing bindings, constraints and event components.

- [ ] **Step 4: Add responsive behavior**

At widths below 900px, stack graph and detail content and let the Element Plus drawer use the existing global full-width mobile rule.

- [ ] **Step 5: Run focused tests**

Run: `node --test tests/task-execution-presentation.test.mjs`

Expected: PASS with 0 failures.

### Task 4: Build and visual verification

**Files:**
- Verify: `Frontend/src/views/task/TaskList/TaskList.vue`
- Verify: `Frontend/src/views/task/TaskList/components/TaskExecutionDrawer.vue`

**Interfaces:**
- Consumes: completed UI implementation.
- Produces: build and browser evidence.

- [ ] **Step 1: Run the full existing frontend tests**

Run: `Get-ChildItem tests/*.test.* | ForEach-Object { node --test $_.FullName }`

Expected: all current tests pass; any pre-authorized obsolete test must be removed rather than ignored.

- [ ] **Step 2: Run the production build**

Run: `npm run build`

Expected: Vite exits with code 0.

- [ ] **Step 3: Start the development server and inspect the page**

Run: `npm run dev -- --host 127.0.0.1`

Open `/task-management`, verify the list remains full-width, click “详情”, confirm the drawer opens from the right and the graph tab is active without horizontal overflow.

- [ ] **Step 4: Review the final diff**

Run: `git diff --check` and `git status --short`.

Expected: no whitespace errors and only task-list design files, tests and documentation are changed.
