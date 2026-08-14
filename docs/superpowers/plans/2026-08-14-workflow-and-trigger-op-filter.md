# 工作流 AND 触发条件与 OP 接口过滤实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 工作流触发器同时支持现有单条件和一层 AND 条件组，并由目标接口的 `allowedSignals` 静默过滤不接收的状态广播，使设备能力节点只接收 `CMD_STATE`。

**Architecture:** `WorkflowConditionEvaluator` 负责单条件及 AND 组的运行期求值；`WorkflowDefinitionCompiler` 负责结构、对象、操作符和阈值的编译期校验；整个触发器继续只维护一个上升沿状态。状态机发送逻辑不变，工作流状态事件路由在写入快照前查询目标接口的 `allowedSignals`，不允许则保持快照不变。

**Tech Stack:** Java 17、Spring Boot、Jackson、JUnit 5、Vue 3、Element Plus、Node test runner、Vite。

## Global Constraints

- 不创建额外 Git 分支或工作区，不覆盖当前未提交的前端与后端改动。
- 设备状态机仍广播 `CMD_STATE` 和 `OP_STATE`；约束观测逻辑不变。
- 工作流设备能力节点 `Interface_state_in` 只允许 `CMD_STATE`。
- 单条件继续使用 `object/operator/threshold`；多条件只支持一层 `logic: AND` 与原子 `conditions[]`。
- AND 条件组整体维护一个上升沿状态；UPDATE 的结果由下一轮其他触发器观察。
- 不修改数据库表结构和接口快照 JSON 结构。

---

### Task 1: 后端条件组求值

**Files:**
- Modify: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowConditionEvaluatorTest.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowConditionEvaluator.java`

**Interfaces:**
- Consumes: `evaluate(JsonNode condition, JsonNode variables)`。
- Produces: 单条件保持原行为；`logic=AND` 时依次求值所有 `conditions[]`，全部为真才返回真。

- [ ] 增加 AND 全部成立、任一不成立、非法逻辑和非法子条件测试。
- [ ] 运行定向测试并确认因尚未支持条件组而失败。
- [ ] 实现最小递归边界：顶层识别 AND，子项仅走原子条件求值。
- [ ] 再次运行定向测试并确认通过。

### Task 2: 编译校验与系统模板

**Files:**
- Modify: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowDefinitionCompilerTest.java`
- Modify: `Backend/src/test/java/com/smartlab/global/contract/WorkflowNodeSystemContractTest.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCompiler.java`
- Modify: `Backend/src/main/java/com/smartlab/global/contract/WorkflowNodeSystemContract.java`

**Interfaces:**
- Consumes: `interfaces[].bindingTriggers[].condition`。
- Produces: 编译器校验单条件或一层 AND；设备系统 STATE 输入接口只声明 `CMD_STATE`；设备完成系统触发器使用 AND 同时验证节点状态、信号类型和指令状态。

- [ ] 增加合法 AND、非法 logic、空组、嵌套组与设备模板断言。
- [ ] 运行定向测试并确认失败原因正确。
- [ ] 增加统一的条件结构校验并更新系统模板。
- [ ] 再次运行定向测试并确认通过。

### Task 3: 状态广播按目标接口过滤

**Files:**
- Modify: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowEngineExecutionTest.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowEngine.java`

**Interfaces:**
- Consumes: `StateMachineInterfaceSignalEvent` 与目标节点 STATE/IN 接口的 `allowedSignals`。
- Produces: 允许的信号写入快照；不允许的广播保持原快照且不失败；底层 `withSignal()` 强校验不变。

- [ ] 增加 OP 不覆盖 CMD、CMD 正常写入的测试。
- [ ] 运行定向测试并确认当前 OP 会覆盖快照。
- [ ] 在状态事件投递层按目标接口白名单过滤。
- [ ] 再次运行定向测试并确认通过。

### Task 4: 前端 AND 条件编辑与校验

**Files:**
- Modify: `Frontend/tests/workflowNodeDefinition.test.mjs`
- Modify: `Frontend/tests/workflow-designer-structure.test.mjs`
- Modify: `Frontend/src/utils/workflowNodeDefinition.js`
- Modify: `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowTriggerEditor.vue`

**Interfaces:**
- Consumes: 单条件或 `{ logic: 'AND', conditions: Predicate[] }`。
- Produces: 添加第二项时转为 AND；删除至一项时转回单条件；每一项复用现有类型化编辑控件；系统触发器继续只读。

- [ ] 增加条件结构转换、校验和界面结构测试。
- [ ] 运行定向测试并确认失败。
- [ ] 实现条件项读取、添加、删除、更新和只读语义摘要。
- [ ] 再次运行前端定向测试并确认通过。

### Task 5: 规范同步与完整验证

**Files:**
- Modify: `Backend/src/main/resources/schemas/工作流模型.json`
- Modify: `Backend/src/main/resources/schemas/系统执行规范.json`
- Modify: 受系统模板格式影响的既有测试夹具。

**Interfaces:**
- Produces: Schema 描述单条件/AND 两种结构；系统规范明确 DEV_NODE 的 STATE 输入只消费 `CMD_STATE`，OP 继续供状态观测与约束使用。

- [ ] 同步 Schema 和系统执行规范，不修改状态机协议与数据库结构。
- [ ] 运行后端完整测试。
- [ ] 运行前端完整测试与生产构建。
- [ ] 检查 Git 差异，确认没有覆盖无关改动并记录验证结果。
