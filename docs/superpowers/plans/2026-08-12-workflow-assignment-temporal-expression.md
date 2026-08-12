# Workflow Assignment And Temporal Expression Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 恢复系统触发器的完整只读配置界面，并让 BRANCH/AGGREGATE 节点通过赋值 expression 计算内部变量，支持少量实验室时序函数。

**Architecture:** 节点 expression 使用 `target = valueExpression`，运行时从任务/节点隔离的 `ObservationHistoryStore` 读取内部变量历史，计算成功后先持久化目标值，再建立本轮触发器快照。触发器 UPDATE.valueExpression 继续沿用动作执行边界，其结果仅在下一轮可见。

**Tech Stack:** Java 21、Spring Boot、Jackson、JUnit 5、Vue 3、Element Plus、Node Test Runner。

## Global Constraints

- 工作流引擎不直接读取设备遥测。
- 系统触发器显示完整配置但不可编辑或删除。
- 节点 expression 仅适用于 BRANCH、AGGREGATE，左侧必须是已声明的数值内部变量。
- 历史样本不足不覆盖目标变量、不终止节点或任务。
- 不增加数据库字段和第三方依赖。

---

### Task 1: Assignment expression contract

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/engine/constraint/ConstraintExpressionEvaluator.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCompiler.java`
- Test: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowDefinitionCompilerTest.java`

**Interfaces:**
- Produces: `WorkflowAssignmentExpression.parse(String)` and compiler validation for `target = expression`.

- [ ] Write tests accepting `scaled = temperature * 100` and rejecting missing assignment, unknown target, unsupported function, and invalid window seconds.
- [ ] Run focused tests and verify RED.
- [ ] Implement assignment parsing and validation for arithmetic plus `rate/delta/avg/max/min/abs`.
- [ ] Run focused tests and verify GREEN.

### Task 2: Runtime temporal evaluation

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowEngine.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/constraint/ConstraintExpressionEvaluator.java`
- Test: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowEngineExecutionTest.java`

**Interfaces:**
- Consumes: node variable histories from `ObservableSnapshotReader.readHistory`.
- Produces: same-poll target variable visibility for node expression and unavailable-result handling.

- [ ] Write tests proving same-poll trigger visibility, time-window rate calculation, and insufficient-history no-op.
- [ ] Run focused tests and verify RED.
- [ ] Inject the snapshot reader, resolve node-scoped history keys, evaluate and persist expression before freezing trigger variables.
- [ ] Run focused tests and verify GREEN.

### Task 3: Trigger and expression UI

**Files:**
- Modify: `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowTriggerEditor.vue`
- Modify: `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowExpressionEditor.vue`
- Modify: `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowBusinessPanel.vue`
- Modify: `Frontend/src/utils/workflowDesignerRules.js`
- Modify: `Frontend/src/views/task/WorkflowDesigner/WorkflowDesigner.vue`
- Test: `Frontend/tests/workflow-designer-rules.test.mjs`
- Test: `Frontend/tests/workflow-designer-structure.test.mjs`

**Interfaces:**
- Produces: assignment editor, temporal function insert menu, structured locked system triggers, and wider drawer.

- [ ] Write tests for assignment serialization/validation, temporal function catalog, structured system trigger fields, and drawer width.
- [ ] Run focused tests and verify RED.
- [ ] Implement the minimal UI and rules changes.
- [ ] Run focused tests and verify GREEN.

### Task 4: Full verification

- [ ] Run `mvn test` and confirm zero failures.
- [ ] Run `node --test --test-reporter=dot tests` and confirm zero failures.
- [ ] Run `npm run build` and confirm exit code 0.
- [ ] Reload the authenticated workflow designer and inspect system/custom trigger and function-node drawer states.
