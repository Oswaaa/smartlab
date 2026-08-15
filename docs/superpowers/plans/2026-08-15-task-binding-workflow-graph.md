# Task Binding Workflow Graph Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在创建任务抽屉中恢复真实流程拓扑，并让用户通过点击设备节点完成基于 `slotId` 的实例绑定。

**Architecture:** 使用工作流详情递归构建按流程出现位置分组的只读图数据，使用后端 requirements 装饰设备节点并作为绑定事实来源。Vue Flow 只负责显示当前路径的流程拓扑，右侧面板只修改 `resourceBindings[slotId]`。

**Tech Stack:** Vue 3、TypeScript、Vue Flow、Element Plus、Node Test Runner

## Global Constraints

- 不恢复通过节点名称猜测后端槽位的逻辑。
- 设备绑定提交格式保持 `{ slotId, deviceInstanceId }`。
- 子流程不同出现位置必须独立绑定。
- 普通用户界面不显示内部传输字段和错误代码。
- 不修改后端接口。

---

### Task 1: 规范化工作流节点引用

**Files:**
- Modify: `Frontend/src/utils/taskResourceBindings.js`
- Test: `Frontend/tests/taskResourceBindings.test.js`

**Interfaces:**
- Consumes: 工作流详情中的 `nodeIdRefs`、`nodesDef`、`interfaceConnections`
- Produces: `expandWorkflowDefinition(rootFlowModelId, loadDefinition)` 返回带准确 `nodeIdRef`、父子路径和连接的流程出现位置

- [ ] **Step 1: Write the failing test**

新增测试，输入不含 `nodeIdRef` 的 `nodesDef` 和独立的 `nodeIdRefs`，断言展开结果中的设备节点具有后端节点引用，并且重复子流程生成不同的完整 `slotId`。

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test tests/taskResourceBindings.test.js`

Expected: FAIL，当前展开逻辑无法从 `nodeIdRefs` 恢复节点引用或生成后端一致的槽位。

- [ ] **Step 3: Write minimal implementation**

在读取定义时按 `nodeName` 合并 `nodeIdRefs` 与 `nodesDef`，递归记录 `{ flowModelId, nodeIdRef, nodeName }` 路径，并为设备节点生成和 requirements 一致的槽位标识。

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test tests/taskResourceBindings.test.js`

Expected: PASS。

### Task 2: 构建设备绑定图数据

**Files:**
- Modify: `Frontend/src/utils/taskResourceBindings.js`
- Test: `Frontend/tests/taskResourceBindings.test.js`

**Interfaces:**
- Consumes: `expandWorkflowDefinition` 结果与后端 requirements
- Produces: `buildBindingWorkflowView(expanded, requirements)`，返回流程路径、Vue Flow 节点、连接和绑定异常

- [ ] **Step 1: Write the failing test**

新增测试，断言设备节点通过准确 `slotId` 获得 requirement，功能节点不具有绑定槽位，缺失 requirement 的设备节点产生明确错误。

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test tests/taskResourceBindings.test.js`

Expected: FAIL，函数尚不存在。

- [ ] **Step 3: Write minimal implementation**

实现只读绑定图数据转换，复用 `buildWorkflowAutoLayout` 计算节点坐标，连接仅使用 `NODE_TO_NODE`。

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test tests/taskResourceBindings.test.js`

Expected: PASS。

### Task 3: 实现可点击绑定流程图

**Files:**
- Create: `Frontend/src/views/task/TaskList/components/TaskBindingWorkflowNode.vue`
- Modify: `Frontend/src/views/task/TaskList/components/TaskResourceBindingCanvas.vue`
- Modify: `Frontend/src/views/task/TaskList/components/TaskCreateDrawer.vue`
- Modify: `Frontend/src/views/task/TaskList/TaskList.vue`
- Test: `Frontend/tests/task-creation-constraint-workflow.test.mjs`
- Test: `Frontend/tests/workflow-full-chain-copy.test.mjs`

**Interfaces:**
- Consumes: `workflowGroups`、requirements、设备模型、设备实例和 `resourceBindings`
- Produces: 设备节点点击选择、子流程进入/返回、绑定更新事件

- [ ] **Step 1: Write the failing tests**

断言绑定组件使用 Vue Flow、自定义绑定节点、子流程路径导航、设备实例选择，并断言 TaskList 将展开后的流程组传入创建抽屉。

- [ ] **Step 2: Run tests to verify they fail**

Run: `node --test tests/task-creation-constraint-workflow.test.mjs tests/workflow-full-chain-copy.test.mjs`

Expected: FAIL，当前组件只有分组下拉列表。

- [ ] **Step 3: Write minimal implementation**

将绑定区域改为左侧 Vue Flow 和右侧绑定面板；设备节点支持选中，子流程节点支持进入子流程，路径导航支持返回；保留绑定进度和同模型批量应用。

- [ ] **Step 4: Run tests to verify they pass**

Run: `node --test tests/task-creation-constraint-workflow.test.mjs tests/workflow-full-chain-copy.test.mjs`

Expected: PASS。

### Task 4: 回归验证

**Files:**
- Test: `Frontend/tests/taskResourceBindings.test.js`
- Test: `Frontend/tests/task-creation-constraint-workflow.test.mjs`
- Test: `Frontend/tests/workflow-full-chain-copy.test.mjs`

**Interfaces:**
- Consumes: 完成后的任务创建链路
- Produces: 可验证的前端测试和生产构建结果

- [ ] **Step 1: Run targeted tests**

Run: `node --test tests/taskResourceBindings.test.js tests/task-creation-constraint-workflow.test.mjs tests/workflow-full-chain-copy.test.mjs`

- [ ] **Step 2: Run full frontend tests**

Run: `node --test`

- [ ] **Step 3: Run production build**

Run: `npm run build`

- [ ] **Step 4: Check diff formatting**

Run: `git diff --check`

Expected: 所有命令成功，无测试失败和差异格式错误。
