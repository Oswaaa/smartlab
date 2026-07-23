# 工作流运行时一致化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不修改数据库结构的前提下，完成共享比较操作符、状态机接口信号契约、流程设计保存、任务生命周期和工作流运行时的一致化实现。

**Architecture:** `protocol-dict.json` 保存跨模型共享契约；各模型 Schema 保存自身结构并引用共享契约。Management 以 `FLOW_MODELS`、`TASK` 为聚合根，事务性维护 `FLOW_NODE`、`TASK_STEP` 和 `EXECUTION_LOG`；Engine 只调用 Management Service。设备能力节点通过状态机接口和带 `messageId` 的事件完成，不轮询孪生状态。

**Tech Stack:** Java 21、Spring Boot 3.3、MyBatis-Plus、Jackson、JSON Schema、Vue 3、TypeScript、Element Plus、Vue Flow。

## Global Constraints

- 不修改数据库表结构。
- 不兼容旧工作流引擎语义和 `GT/LE/EQ` 等旧比较操作符。
- 功能节点仅包含 `START`、`END`、`BRANCH`、`AGGREGATE`。
- 标准状态机接口由系统定义；用户配置状态、转移和信号选择，但不能任意改写系统接口的信号词汇。
- Adapter 接口输入信号由具体 Adapter 契约事件动态补全。
- `FLOW_MODELS.nodes` 只保存流程内 `nodeIdRef`；完整静态节点定义保存到 `FLOW_NODE`。
- `TASK_STEP.nodeIdRef` 是运行时从 `FLOW_NODE` 复制的受控冗余字段。

---

### Task 1: 共享比较操作符

**Files:**
- Modify: `Backend/src/main/resources/schemas/protocol-dict.json`
- Modify: `Backend/src/main/resources/schemas/{constraint-model,device-capability-model,workflow-model}.json`
- Modify: `Backend/src/main/java/com/smartlab/global/schema/SchemaMetadataService.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/constraint/ConstraintRuleService.java`
- Modify: `Frontend/src/views/device/**`, `Frontend/src/views/security/SecurityCenter.vue`, `Frontend/src/views/task/TaskList.vue`
- Test: `Backend/src/test/java/com/smartlab/global/protocol/ProtocolDictionaryServiceTest.java`
- Test: `Backend/src/test/java/com/smartlab/management/service/db/constraint/ConstraintRuleServiceTest.java`

**Produces:** `ConstraintOperator = [">", "<", ">=", "<=", "=", "!=", "BETWEEN", "IN"]`，旧别名直接拒绝。

- [ ] 先写协议枚举、旧值拒绝和集合操作数校验测试并验证失败。
- [ ] 将共享定义移回 protocol，三个模型只引用 protocol。
- [ ] 删除前后端所有旧值转换；`BETWEEN` 要求双元素 JSON 数组，`IN` 要求非空 JSON 数组。
- [ ] 运行目标测试并扫描旧值残留。

### Task 2: 状态机接口 allowedSignals 策略

**Files:**
- Modify: `Backend/src/main/resources/schemas/device-state-machine-model.json`
- Create: `Backend/src/main/java/com/smartlab/global/schema/StateMachineInterfacePolicyService.java`
- Modify: `Backend/src/main/java/com/smartlab/global/schema/SchemaMetadataService.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/resource/device/DeviceModelService.java`
- Modify: `Frontend/src/views/device/components/deviceModel/deviceModelConstants.js`
- Test: `Backend/src/test/java/com/smartlab/global/schema/StateMachineInterfacePolicyServiceTest.java`

**Produces:** `standardInterfaces(adapterEvents)` 和 `validateInterfaces(interfaces, adapterEvents)`。

- [ ] 写测试覆盖系统接口默认信号、Adapter 动态事件和非法自定义信号。
- [ ] 实现接口策略：系统接口的名称、方向、类型和信号来源固定；Adapter 输入并入当前 Adapter 的 cmd/op events。
- [ ] 前端从 `/api/schema-metadata/frontend` 渲染接口与信号，不维护重复枚举。
- [ ] 状态转移保存时校验 trigger 的接口和信号属于该模型声明。

### Task 3: 流程模型聚合保存与编译

**Files:**
- Create: `Backend/src/main/java/com/smartlab/management/dto/workflow/WorkflowSaveRequest.java`
- Create: `Backend/src/main/java/com/smartlab/management/dto/workflow/WorkflowDetailResponse.java`
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCompiler.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowService.java`
- Modify: `Backend/src/main/java/com/smartlab/management/controller/workflow/WorkflowController.java`
- Modify: `Backend/src/main/java/com/smartlab/management/controller/workflow/FlowNodeController.java`
- Test: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowDefinitionCompilerTest.java`

**Produces:** `WorkflowDefinitionCompiler.compile(request)` 和事务性 `WorkflowService.saveDefinition(request)`。

- [ ] 写失败测试覆盖唯一 START/END、唯一 nodeIdRef、连接端点、分支出口、聚合入边和子流程引用。
- [ ] 编译为节点索引与控制流邻接表；不解析接口字符串前缀猜节点。
- [ ] 保存 `FLOW_MODELS.nodes=[nodeIdRef...]`，按流程模型事务性重建 `FLOW_NODE`。
- [ ] 详情接口重新组装 `nodesDef`；禁用节点实体的独立写入和删除接口。

### Task 4: 任务聚合与运行时存储边界

**Files:**
- Create: `Backend/src/main/java/com/smartlab/management/dto/workflow/TaskCreateRequest.java`
- Create: `Backend/src/main/java/com/smartlab/management/service/db/workflow/ExecutionLogService.java`
- Create: `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowRuntimeService.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/workflow/TaskService.java`
- Modify: `Backend/src/main/java/com/smartlab/management/controller/workflow/TaskController.java`
- Test: `Backend/src/test/java/com/smartlab/management/service/db/workflow/TaskServiceTest.java`

**Produces:** 合法任务状态流 `PENDING -> RUNNING -> COMPLETED|FAILED|ABORTED`，以及步骤和日志的内部原子操作。

- [ ] 写测试覆盖任务创建校验、重复启动、终态不可重启、终止时活动步骤同步终止。
- [ ] Controller 改用 DTO；前端不能写任务状态、当前节点、时间和步骤字段。
- [ ] Runtime Service 集中管理步骤创建、开始、完成、失败和当前节点指针。
- [ ] 日志统一写入 `EXECUTION_LOG` 并填充 `task_step_id`。

### Task 5: 事件驱动工作流引擎

**Files:**
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowRuntimeModels.java`
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDeviceLifecycleEvent.java`
- Rewrite: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowEngine.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineEngine.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineSendActionEvent.java`
- Modify: `Backend/src/main/java/com/smartlab/adapter/MqttAdapterMessagingService.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/protocol/AdapterPayloadMapperService.java`
- Test: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowEngineTest.java`

**Produces:** START、END、BRANCH、AGGREGATE、DEVICE_CAPABILITY_NODE、SUB_FLOW_NODE 的执行器与 `messageId` 关联。

- [ ] 写测试覆盖顺序流程、真假分支、聚合等待、设备成功/失败、一级和嵌套子流程。
- [ ] Engine 仅依赖 Management Service；删除 Mapper 注入、WAIT/SYNC/JOIN 和孪生状态轮询。
- [ ] BRANCH 使用结构化条件求值并只激活匹配出口；AGGREGATE 等待全部有效入边步骤完成。
- [ ] 子流程父步骤先创建，子步骤指向父步骤；子流程 END 完成父步骤并恢复父流程。
- [ ] 设备请求生成 `messageId` 写入步骤快照；Adapter 回调携带同一 ID 并事件驱动完成步骤。

### Task 6: 前端设计器与任务页一致化

**Files:**
- Modify: `Frontend/src/views/task/WorkflowDesigner.vue`
- Modify: `Frontend/src/views/task/TaskList.vue`

**Produces:** 只展示四类功能节点，保存载荷与聚合 DTO 一致，操作符和信号选项来自 Schema 元数据。

- [ ] 加载 `/api/schema-metadata/frontend`，生成节点类型、功能类型、生命周期和操作符选项。
- [ ] 节点使用数字 `nodeIdRef`；连接显式保存 `sourceNodeIdRef/sourceInterface` 与 `targetNodeIdRef/targetInterface`。
- [ ] 分支编辑器使用结构化 `subject/operator/threshold`；聚合节点只配置汇合策略 `ALL`。
- [ ] 任务创建只提交允许字段，监控按 `parentStepId/stepDepth` 展示步骤树。

### Task 7: 审查与验证

**Files:** all files above.

- [ ] 运行 `mvn clean test`，要求零失败。
- [ ] 运行 `npm run build`，要求构建成功。
- [ ] 扫描旧操作符、旧功能节点、引擎 Mapper 注入、裸 Map 写接口和前端硬编码协议枚举。
- [ ] 对照业务时间线逐项审查，修复所有阻断或重要问题。
- [ ] 再运行后端完整测试与前端构建。
