# SmartLab 工作流动作与完整运行时重构设计

## 1. 目标

重构状态机和工作流执行层，使 Schema 中声明的每一种动作、节点类型和功能类型都有可发现、可校验、可测试的真实执行器。引擎只负责编排上下文和推进生命周期，不再通过散落的 `switch`、字符串判断、静默跳过或隐藏运行字段模拟能力。

本轮同时完成后端执行链、前端设计器、任务监控和端到端测试。数据库结构保持不变。

## 2. 当前问题

1. 状态机已经有 `StateMachineDictionary.Registry`，但只有嵌套式 `SEND` 实现，缺少启动期“Schema 声明与执行器覆盖一致”检查。
2. 工作流的 `EMIT_SIGNAL` 由 `WorkflowEngine.executionAction()` 手工查找，没有动作注册表。
3. `WorkflowEngine` 用节点类型 `switch` 和功能类型 `switch` 承担所有语义，扩展点不清晰。
4. 前端固定生成 `EMIT_SIGNAL`，没有动作目录及参数编辑能力。
5. 当前动作模型不能表达此前设计中提到的非阻塞等待、变量赋值和结构化计算。
6. 工作流运行状态依靠定时扫描推进，但没有统一的动作结果协议。

## 3. 设计原则

- Schema 是允许声明哪些动作及其参数结构的源。
- 动作注册表是动作名到函数实现的唯一映射。
- 节点执行器只处理节点类型语义；功能执行器只处理 START、END、BRANCH、AGGREGATE 语义。
- 未注册动作、节点或功能类型必须在应用启动或流程保存时失败，不能运行时静默忽略。
- WAIT 必须非阻塞，禁止线程休眠。
- 禁止任意 JavaScript、SpEL、Groovy 或 Python 表达式执行。
- 不新增数据库字段，也不借用接口快照、端口快照或变量空间保存隐藏动作游标。
- 所有可重试动作必须幂等；所有有外部副作用的动作必须有显式去重依据。

## 4. Schema 与动作目录

动作属于对应引擎领域：

- `StateMachineAction` 移入 `device-state-machine-model.json`；
- `WorkflowNodeAction` 移入 `workflow-model.json`；
- `protocol-dict.json` 只保留跨模块通信格式、公共类型、信号和 Topic。

两个领域 Schema 都提供 `x-actionCatalog`。它是 Schema 的注解元数据，不会写入模型实例，包含：

- `actionName`；
- `displayName`；
- `description`；
- `payloadDefinition`；
- 允许的节点类型；
- 执行特征，如 pure、mustBeFirst、mustBeLast。

后端 `SchemaMetadataService` 解析动作目录并发送给前端。执行器注册表启动时核对：

```text
Schema actionName 集合 == Spring ActionExecutor 集合
```

存在缺失执行器或多余执行器时应用拒绝启动。

## 5. 状态机动作字典

### 5.1 接口

```java
interface StateMachineActionExecutor {
    String actionName();
    StateMachineActionResult execute(StateMachineActionContext context, JsonNode payload);
}
```

注册表使用构造器注入全部执行器，拒绝重复动作名，并提供不可变目录。

### 5.2 SEND

状态机本轮唯一动作是 `SEND`，职责为：

1. 校验目标输出接口存在；
2. 校验信号位于该接口 `allowedSignals`；
3. 按 `SystemSignalFormat` 构造 `signalName + payload`；
4. STAT 输出发布 `StateMachineInterfaceSignalEvent`；
5. ADAPTER 输出发布 `StateMachineSendActionEvent`；
6. CMD_STATE/OP_STATE 从当前数字孪生状态生成状态载荷；
7. CMD_START 从执行上下文取得 commandName 和 parameters；
8. CMD_ABORT 不附加业务参数。

状态转移负责改状态，SEND 不允许直接改写状态。

## 6. 工作流动作字典

### 6.1 统一接口和结果

```java
interface WorkflowActionExecutor {
    String actionName();
    WorkflowActionResult execute(WorkflowActionContext context, JsonNode payload);
}

enum WorkflowActionStatus {
    CONTINUE,
    SUSPEND_UNTIL,
    AWAIT_EXTERNAL_SIGNAL
}
```

上下文包含 Task、TaskStep、FlowNode、任务变量、步骤变量、已解析设备实例及运行服务。动作不能直接访问 Mapper。

### 6.2 WAIT

参数：

```json
{ "durationMs": 1000 }
```

语义：以 `TASK_STEP.START_TIME + durationMs` 计算截止时间。未到期返回 `SUSPEND_UNTIL`，调度器本轮停止处理该步骤；到期返回 `CONTINUE`。不调用 sleep，也不保存隐藏截止时间。

约束：每个节点最多一个 WAIT，且必须是动作序列第一项。

### 6.3 ASSIGN

参数：

```json
{
  "target": "targetTemperature",
  "source": { "kind": "LITERAL", "value": 80 }
}
```

`source.kind` 支持：

- `LITERAL`：直接使用 value；
- `VARIABLE`：从合并后的任务/步骤变量按点路径读取。

结果写入 `TASK_STEP.VARIABLE_SPACE`。该动作是确定性、幂等的。

### 6.4 CALCULATE

参数：

```json
{
  "target": "energy",
  "operator": "MULTIPLY",
  "operands": [
    { "kind": "VARIABLE", "path": "power" },
    { "kind": "VARIABLE", "path": "duration" }
  ]
}
```

支持结构化数值运算：

- `ADD`；
- `SUBTRACT`；
- `MULTIPLY`；
- `DIVIDE`；
- `MOD`；
- `MIN`；
- `MAX`；
- `ROUND`。

DIVIDE/MOD 检查除零；所有操作数必须是数值。结果写入步骤变量。该动作不接受字符串表达式，不执行任意代码。

### 6.5 EMIT_SIGNAL

参数：

```json
{
  "interfaceType": "WORKFLOW",
  "signalName": "WF_EXECUTE_START"
}
```

仅允许设备能力节点使用，且必须是动作序列末项。执行器：

1. 解析该节点的设备实例；
2. 校验实例仍在使用中；
3. 创建或复用步骤的 messageId；
4. 组装 commandName、parameters、taskId 和 taskStepId；
5. 调用状态机输入接口；
6. 成功投递后返回 `AWAIT_EXTERNAL_SIGNAL`。

messageId 仍记录于 `INTERFACE_IN_SNAPSHOT`，这是接口调用快照的业务字段，不是隐藏动作游标。重复调度发现已有 messageId 时不会再次发送。

## 7. 动作顺序与无游标恢复

动作顺序受到编译器约束：

```text
[WAIT?] → [ASSIGN/CALCULATE]* → [EMIT_SIGNAL?]
```

- WAIT 只读步骤开始时间，可重复调用；
- ASSIGN/CALCULATE 是幂等纯计算；
- EMIT_SIGNAL 有 messageId 去重且必须最后；
- 功能节点在同一调度轮完成，不会在纯动作之后长期挂起；
- 子流程节点只在尚未创建子步骤时执行前置动作；
- AGGREGATE 只有全部前序完成后才执行动作。

因此无需动作游标，也不会把运行控制信息偷塞进变量空间。

## 8. 节点执行器和功能执行器

### 8.1 节点执行器注册表

```java
interface WorkflowNodeExecutor {
    String nodeType();
    NodeExecutionResult execute(WorkflowNodeExecutionContext context);
}
```

实现：

- `DeviceCapabilityNodeExecutor`；
- `FunctionalNodeExecutor`；
- `SubFlowNodeExecutor`。

`WorkflowEngine` 只负责取得活动步骤、创建上下文、调用注册表和处理统一结果。

### 8.2 功能执行器注册表

```java
interface WorkflowFunctionExecutor {
    String functionType();
    NodeExecutionResult execute(WorkflowNodeExecutionContext context);
}
```

实现：

- `StartFunctionExecutor`；
- `EndFunctionExecutor`；
- `BranchFunctionExecutor`；
- `AggregateFunctionExecutor`。

启动期同样校验 `workflow-model.json` 声明的 nodeType/functionType 是否全部有执行器。

## 9. 统一节点执行结果

```java
enum NodeExecutionStatus {
    COMPLETE_AND_ROUTE,
    COMPLETE_FLOW,
    SUSPENDED,
    AWAIT_EXTERNAL_SIGNAL,
    START_SUB_FLOW,
    FAILED
}
```

结果携带所需的输出接口、输出快照、失败原因或子流程 ID。引擎集中处理步骤完成、路由、失败和子流程返回，执行器不直接改变 Task 状态。

## 10. 完整工作流运行链

1. Task 创建时校验资源映射和设备可用性。
2. Task 启动后创建 START 步骤。
3. 调度器领取活动步骤并保证单进程内同一 taskId 串行处理。
4. 节点动作流水线先执行 WAIT/ASSIGN/CALCULATE。
5. 节点执行器执行 START/END/BRANCH/AGGREGATE、设备能力或子流程语义。
6. 设备节点通过 EMIT_SIGNAL 等待状态机结果。
7. 状态机 STAT 接口返回 CMD_STATE 后，以 messageId 定位步骤。
8. COMPLETED 完成步骤并路由；FAILED/ABORTED 失败步骤并失败任务。
9. 子流程 END 完成父步骤并恢复父流程。
10. 任务中止向所有运行中的设备步骤发送 WF_EXECUTE_ABORT，再终止任务与未完成步骤。
11. 每个步骤和任务生命周期写入 EXECUTION_LOG。

## 11. 并发和幂等

当前数据库没有步骤版本号或动作游标，本轮不假装提供分布式多实例调度保证。实现范围为单后端进程：

- 使用 taskId 级锁避免定时调度与状态机回调并发推进同一任务；
- `WorkflowRuntimeService.createStep()` 保证同一流程位置不重复创建；
- messageId 保证设备命令不重复发布；
- 完成/失败操作对已终态步骤保持幂等；
- 状态机事件只接受与运行步骤 messageId 匹配的结果。

如果未来部署多个后端实例，需要数据库行锁或任务租约字段；在数据库结构不变的约束下不声称已经支持该能力。

## 12. 前端设计器

后端 `/api/schema-metadata/frontend` 增加：

- `stateMachine.actionCatalog`；
- `workflow.actionCatalog`；
- `workflow.nodeTypes`；
- `workflow.functionTypes`；
- 计算操作符目录。

前端实现：

1. 节点属性抽屉增加“内部动作”区域；
2. 动作下拉和参数表单由 actionCatalog 渲染；
3. 根据 allowedNodeTypes 过滤动作；
4. WAIT 显示毫秒/秒输入；
5. ASSIGN 显示目标变量、来源类型、字面量或变量路径；
6. CALCULATE 显示目标变量、操作符和操作数列表；
7. 设备节点保留只读的 EMIT_SIGNAL，并允许编辑目标信号参数；
8. 拖动排序时实时校验 WAIT 首位、EMIT_SIGNAL 末位；
9. 任务详情显示当前步骤、等待截止时间、外部 messageId、变量空间和最近执行日志。

前端不再自建动作枚举，也不在提交时偷偷补全后端必需字段；默认动作由统一的模型构造器基于后端目录生成。

## 13. 校验分层

### Schema 校验

- 动作名和 payload 基础类型；
- required、minimum、数组长度；
- 节点、接口、端口和连接结构。

### 编译器校验

- 动作是否有执行器；
- 动作适用节点类型；
- WAIT/EMIT_SIGNAL 顺序；
- 变量名、计算操作数和结构化分支条件；
- START/END 唯一性；
- 连接完整性、不可达节点、环和子流程引用。

### 运行时校验

- 设备实例仍可用；
- 当前状态机状态允许输入信号；
- 变量路径存在；
- 数值类型、除零和溢出；
- messageId 与运行步骤一致；
- 子流程深度和任务状态。

## 14. 测试与验收

后端单元测试：

- 每个动作执行器正常、边界和失败路径；
- 注册表重复、缺失和多余执行器；
- WAIT 不阻塞线程且按时间恢复；
- ASSIGN/CALCULATE 的变量解析、类型和除零；
- EMIT_SIGNAL 去重与异步恢复；
- 三类节点执行器和四类功能执行器；
- BRANCH、AGGREGATE、SUB_FLOW、任务中止和失败传播；
- Schema、编译器和运行时三层拒绝非法输入；
- 同一任务并发回调只推进一次。

前端测试：

- 动作目录加载和缺失失败；
- 四种动作编辑器；
- 动作适用范围与排序；
- 序列化/反序列化保持一致；
- 任务监控展示等待、外部信号、变量和日志。

端到端验收场景：

```text
START
  → ASSIGN 目标温度
  → CALCULATE 加热时长
  → WAIT
  → DEVICE_CAPABILITY_NODE / EMIT_SIGNAL
  → Adapter CMD 事件
  → BRANCH
  → AGGREGATE
  → SUB_FLOW
  → END
```

验收命令包括后端全量测试、前端测试、前端生产构建和 testAdapter 测试。

## 15. 非目标

- 不修改数据库表结构；
- 不支持任意脚本执行；
- 不新增 LOOP 节点；
- 不宣称支持多后端实例分布式调度；
- 不兼容旧的未知动作名、旧操作符或错误节点格式；
- 不把约束引擎完整实现混入本轮工作流重构。
