# Workflow Aggregate N-of-M Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用现有接口触发器表达 N-of-M 聚合，删除运行时聚合拓扑硬编码，并在任务运行图中准确展示节点状态和实际接收过信号的接口连线。

**Architecture:** 工作流每轮 condition 始终读取轮询开始时的冻结快照，动作按声明顺序立即执行；内部变量 UPDATE 表达式从最新步骤变量空间求值，但不会改变本轮后续 condition 的结果。聚合节点通过系统内部变量 `aggregateCount`、每个输入接口的一次性 ACTIVE 计数触发器和用户配置的输出触发器表达业务，不在运行引擎中增加 AGGREGATE 分支。终态步骤拒绝新的输入投递，并在最后一轮仅评估 OUT 接口完成收尾。

**Tech Stack:** Java 17、Spring Boot、Jackson、JUnit 5、Mockito、Vue 3、Element Plus、Vue Flow、Node.js test runner。

## Global Constraints

- 保留当前 `main` 工作区中既有的三处触发器编辑器未提交修复，不创建分支或 Git worktree。
- 不执行 Git 提交或推送；用户另行明确要求后再处理。
- OUT 接口允许连接多个下游；每个 NODE_TO_NODE 的 WORKFLOW IN 接口最多连接一个上游。
- 本轮中途发生生命周期 UPDATE 不缩短本轮接口遍历；只有轮询开始时已处于终态的步骤才关闭 IN 接口。
- `SUCCEEDED`、`FAILED`、`TERMINATED` 为终态；`PENDING`、`RUNNING`、`TERMINATING` 仍由 allowedSignals 和触发器决定可消费信号。
- 聚合输出阈值由用户触发器配置，不强制 N 等于有效输入接口数 M；简单计数阈值必须在 M 可达范围内。

---

### Task 1: Engine Poll Snapshot and Terminal Input Boundary

**Files:**
- Modify: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowEngineExecutionTest.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowEngine.java`

**Interfaces:**
- Consumes: `WorkflowRuntimeService.pollableSteps(Long)`、`WorkflowInterfaceSnapshots.withSignal(...)`。
- Produces: 终态轮询只评估 OUT；NODE_TO_NODE 和子流程完成信号不再写入终态步骤输入快照；同轮中途进入终态仍完成本轮既定接口遍历。

- [ ] **Step 1: Write failing engine behavior tests**

增加以下测试：

```java
@Test
void terminalPollEvaluatesOutputTriggersButNotInputTriggers() { /* SUCCEEDED步骤仅记录OUT动作 */ }

@Test
void lifecycleTransitionDuringPollDoesNotSkipRemainingInputTriggers() { /* RUNNING快照下OUT转终态后IN仍执行 */ }

@Test
void workflowEmissionDoesNotWriteInputSnapshotOfTerminalTarget() { /* 下游已SUCCEEDED时不投递 */ }
```

- [ ] **Step 2: Run tests and verify RED**

Run: `mvn -Dtest=WorkflowEngineExecutionTest test`

Expected: 新增测试分别因终态仍遍历 IN、路由仍写终态快照而失败。

- [ ] **Step 3: Implement minimal terminal boundary**

在 `WorkflowEngine` 中根据轮询开始状态固定本轮方向列表：非终态为 `OUT, IN`，终态为 `OUT`。在 NODE_TO_NODE 路由以及子流程完成通知写快照前检查目标步骤是否终态；不在本轮中途根据更新后的状态停止遍历。

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `mvn -Dtest=WorkflowEngineExecutionTest test`

Expected: PASS。

---

### Task 2: Latest UPDATE Context and Generic Internal Variable Initialization

**Files:**
- Modify: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowEngineExecutionTest.java`
- Modify: `Backend/src/test/java/com/smartlab/management/service/db/workflow/WorkflowRuntimeServiceTest.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowEngine.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowRuntimeService.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCompiler.java`
- Modify: `Backend/src/main/resources/schemas/工作流模型.json`

**Interfaces:**
- Consumes: `WorkflowActionContext.variables()`、节点 `internalVariables[].initialValue`。
- Produces: 同轮多个 `aggregateCount + 1` 顺序读取最新计数；步骤创建时通用初始化声明了 `initialValue` 的内部变量。

- [ ] **Step 1: Write failing tests for sequential UPDATE and initialValue**

```java
@Test
void updateExpressionsUseLatestPersistedVariablesWithoutChangingFrozenConditions() { /* 两次+1得到2 */ }

@Test
void createStepInitializesDeclaredInternalVariableValues() { /* aggregateCount初始为0 */ }
```

- [ ] **Step 2: Run tests and verify RED**

Run: `mvn -Dtest=WorkflowEngineExecutionTest,WorkflowRuntimeServiceTest test`

Expected: 计数仍覆盖为 1，步骤变量空间缺少声明初始值。

- [ ] **Step 3: Implement minimal generic behavior**

执行动作前基于本轮接口快照复制上下文，再以步骤最新 `VARIABLE_SPACE` 覆盖普通内部变量；保留冻结的 `signalName`、`payload`、`nodeLifecycleState`。步骤创建时将未被任务变量覆盖的 `internalVariables[].initialValue` 写入变量空间，并在编译器中校验初始值类型。

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `mvn -Dtest=WorkflowEngineExecutionTest,WorkflowRuntimeServiceTest test`

Expected: PASS。

---

### Task 3: Model-Driven Aggregate Template and Connection Validation

**Files:**
- Modify: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowDefinitionCompilerTest.java`
- Modify: `Backend/src/main/java/com/smartlab/global/contract/WorkflowNodeSystemContract.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCanonicalizer.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCompiler.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowEngine.java`
- Modify: `Frontend/tests/workflowNodeDefinition.test.mjs`
- Modify: `Frontend/tests/workflowCanvas.test.mjs`
- Modify: `Frontend/src/utils/workflowNodeDefinition.js`
- Modify: `Frontend/src/utils/workflowCanvas.js`
- Modify: `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowControlInterfacesPanel.vue`
- Modify: `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowTriggerEditor.vue`
- Modify: `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowVariablesPanel.vue`

**Interfaces:**
- Consumes: `aggregateCount` INTEGER 初始值 0、ACTIVE 上升沿、用户配置的输出触发器。
- Produces: 聚合模板默认输入计数、任一输入后进入 RUNNING、可新增独立输入接口、用户配置 N-of-M 输出；每个 WORKFLOW IN 最多一个上游。

- [ ] **Step 1: Write failing backend compiler/template tests**

覆盖：模板声明系统 `aggregateCount`；聚合输入包含 `ACTIVE -> aggregateCount + 1`；系统输出接口不再预置固定 EMIT；同一目标 IN 的第二条连接被拒绝；OUT 一对多仍可编译；可达的 `aggregateCount >= N` 被接受，超过 M 的阈值被拒绝。

- [ ] **Step 2: Verify backend RED**

Run: `mvn -Dtest=WorkflowDefinitionCompilerTest test`

Expected: 模板、连接基数和阈值测试失败。

- [ ] **Step 3: Implement backend model and remove aggregateReady**

更新系统模板、系统变量规范化和编译校验；从 `WorkflowEngine.processStep` 删除 `aggregateReady()` 及其方法。只在编译阶段识别聚合结构，不在运行时读取上游拓扑。

- [ ] **Step 4: Verify backend GREEN**

Run: `mvn -Dtest=WorkflowDefinitionCompilerTest,WorkflowEngineExecutionTest test`

Expected: PASS。

- [ ] **Step 5: Write failing frontend authoring tests**

覆盖：聚合节点创建时包含只读 `aggregateCount`；新增聚合 IN 自动携带只读计数触发器；系统聚合 OUT 允许用户编辑触发器但不允许修改接口字段；画布拒绝第二个上游连接同一 IN，仍允许 OUT 广播多个下游。

- [ ] **Step 6: Verify frontend RED**

Run: `node --test tests/workflowNodeDefinition.test.mjs tests/workflowCanvas.test.mjs`

Expected: 新增聚合配置和连接测试失败。

- [ ] **Step 7: Implement aggregate authoring UI**

为聚合节点提供“新增输入接口/新增输出接口”；新增输入自动生成 `signalName = ACTIVE -> aggregateCount = aggregateCount + 1`；默认 OUT 的接口定义只读、触发器可配置；变量表展示系统计数及初始值但不允许删除或改名。

- [ ] **Step 8: Verify frontend GREEN**

Run: `node --test tests/workflowNodeDefinition.test.mjs tests/workflowCanvas.test.mjs`

Expected: PASS。

---

### Task 4: Runtime Node Status and Accepted Interface Edge UI

**Files:**
- Modify: `Frontend/tests/workflow-execution.test.mjs`
- Modify: `Frontend/src/utils/workflowExecution.js`
- Modify: `Frontend/src/views/task/TaskList/components/TaskRuntimeGraph.vue`

**Interfaces:**
- Consumes: `TaskStep.NODE_STATUS`、`INTERFACE_IN_SNAPSHOT` 数组、工作流接口连接。
- Produces: 带具体接口 Handle 的运行连线、已被目标接口接收的绿色连线、运行中路径动画和统一状态配色。

- [ ] **Step 1: Write failing runtime presentation tests**

```javascript
test('marks only interface edges whose target interface accepted a signal as used', () => { /* target snapshot精确匹配 */ })
test('keeps port edges outside workflow signal usage coloring', () => { /* 数据端口不变绿 */ })
```

- [ ] **Step 2: Verify frontend RED**

Run: `node --test tests/workflow-execution.test.mjs`

Expected: 边缺少 `used`、目标步骤状态和接口 Handle 信息。

- [ ] **Step 3: Implement runtime graph presentation**

`buildRuntimeGraph` 根据目标步骤的输入接口快照标记接口边；`TaskRuntimeGraph` 为 WORKFLOW IN/OUT 渲染对应 Handle，接口边绑定准确 Handle。使用灰、蓝、绿、红、黄、橙区分 PENDING、RUNNING、SUCCEEDED、FAILED、TERMINATING、TERMINATED；已接收信号的接口边为绿色，目标仍 RUNNING 时启用轻量流动动画。

- [ ] **Step 4: Verify focused frontend tests**

Run: `node --test tests/workflow-execution.test.mjs tests/workflowCanvas.test.mjs tests/workflowNodeDefinition.test.mjs`

Expected: PASS。

---

### Task 5: Full Regression Verification

**Files:**
- Verify only; do not create unrelated changes.

**Interfaces:**
- Consumes: Tasks 1-4 outputs。
- Produces: 可编译、测试通过的完整工作流聚合闭环。

- [ ] **Step 1: Run backend workflow tests**

Run: `mvn -Dtest='com.smartlab.engine.workflow.**,com.smartlab.management.service.db.workflow.**' test`

Expected: PASS。

- [ ] **Step 2: Run full backend tests**

Run: `mvn test`

Expected: PASS，若存在用户已批准忽略的既有失败则单独列明且确认与本次无关。

- [ ] **Step 3: Run full frontend tests and build**

Run: `npm test`

Run: `npm run build`

Expected: 全部测试通过且 Vite 生产构建成功。

- [ ] **Step 4: Inspect final diff**

确认仅包含聚合引擎、模型、前端配置、运行图 UI、测试和本计划文件，以及进入本轮前已存在的三处触发器编辑器修复。
