# 工作流模块开发方案（冻结数据库与模型版本）

## 1. 目标与约束

本方案以现有数据库表、实体字段和 JSON Schema 为边界，**不执行任何 DDL、不新增或删除字段、不修改既有领域模型文件**。工作流开发的目标是以统一协议驱动以下链路：

`流程节点 → 状态机 → Adapter`，以及 `约束引擎 → 状态机 / 任务运行时`。

`Backend/src/main/resources/schemas/protocol-dict.json` 是统一协议字典的唯一规范入口。现有模型文件只引用它，不在设备模型、状态机模型、约束模型、工作流模型中重复定义通信报文。

页面 UI 不在本阶段范围内。

## 2. Management 的职责与当前缺口

Management 是数据库与前后端之间的业务访问边界：维护实体、Mapper、数据库聚合 Service、请求/响应 DTO 和 Controller；引擎、状态机、约束等模块只能调用 Management Service，不能直接注入 Mapper。

不是每张表都应有一套独立 CRUD 接口：

- `FLOW_MODELS`：流程模型聚合根，应有模型级 Service/Controller。
- `FLOW_NODE`：流程模型内部成员，不应对前端暴露无约束的独立写入；由流程模型保存事务批量维护。
- `TASK`：任务聚合根，应有 Service/Controller。
- `TASK_STEP`：运行时内部执行记录，只允许引擎写入；Management 只提供按任务读取步骤树的查询接口。
- `EXECUTION_LOG`：跨任务、手动控制、约束和系统的统一日志。需要独立的只读查询 Service/Controller；写入只允许后端内部服务调用。

当前真实缺口：`ExecutionLog` 已有实体和 Mapper，但只有 `TaskService.logs(taskId)` 的附属查询，不能查询手动、约束或系统来源日志。此项放入第一开发阶段。

### “裸 Map / 前端直传实体”的含义

当前 `WorkflowController.save`、`TaskController.save` 接收 `Map<String, Object>`；`FlowNodeController.save` 直接接收数据库实体。问题不是技术上不能运行，而是接口没有边界：前端可传任意键、数据库字段（如状态、时间、主键）和业务输入混在一起，无法做 Schema 校验、发布规则校验或稳定版本演进。

后续只将以下接口改为 DTO：

- `WorkflowSaveRequest` / `WorkflowDetailResponse`
- `TaskCreateRequest` / `TaskDetailResponse`
- `TaskStepTreeResponse`
- `ExecutionLogQuery` / `ExecutionLogResponse`

这不改变数据库，也不要求为每一张表建立 Controller；它只固定聚合接口的边界。

## 3. 协议规范

### 3.1 统一系统内部信号格式

使用既有 `SystemSignalFormat`：

```json
{
  "signalName": "workflow.node.started",
  "payload": {
    "signalId": "sig-uuid",
    "occurredAt": "2026-07-13T15:00:00Z",
    "source": "WORKFLOW_RUNTIME",
    "messageId": "cmd-uuid",
    "taskId": 101,
    "taskStepId": 1001,
    "deviceInstanceId": 301,
    "data": {}
  }
}
```

其中 `payload` 是既有 Schema 明确允许的扩展对象；本方案把其字段固定为协议约定，而不是由各模块临时拼接。

- `signalId`：本次内部事实或请求的唯一 ID。
- `messageId`：一次设备命令的关联 ID；直接复用既有 `CommandMessageFormat.messageId`。
- `taskId`、`taskStepId`、`deviceInstanceId`：不存在时省略，不使用 `0` 或空字符串。
- `data`：每种 `signalName` 的业务数据。

### 3.2 系统内部信号名

| 发起方 | 信号名 | 接收方 | 含义 |
|---|---|---|---|
| 工作流运行时 | `workflow.node.started` | 约束、日志 | 节点开始执行 |
| 工作流运行时 | `workflow.node.succeeded` | 下游节点、约束、日志 | 节点成功 |
| 工作流运行时 | `workflow.node.failed` | 任务运行时、约束、日志 | 节点失败 |
| 工作流运行时 | `device.capability.requested` | 状态机 | 请求设备业务能力 |
| 手动控制 | `device.capability.requested` | 状态机 | 请求设备业务能力，来源为 `MANUAL` |
| 约束引擎 | `device.capability.requested` | 状态机 | 请求安全处置能力，来源为 `CONSTRAINT` |
| 状态机 | `adapter.command.requested` | Adapter 网关 | 转换后的底层命令请求 |
| 状态机 | `device.command.accepted` | 工作流、约束 | Adapter 已接收命令 |
| 状态机 | `device.command.running` | 工作流、约束 | 命令执行中 |
| 状态机 | `device.command.succeeded` | 工作流、约束 | 命令成功 |
| 状态机 | `device.command.failed` | 工作流、约束 | 命令失败 |
| 状态机 | `device.command.cancelled` | 工作流、约束 | 命令取消 |
| 状态机 | `device.command.timed_out` | 工作流、约束 | 命令超时 |
| 状态机 | `device.operation.event` | 工作流、约束 | Adapter 上报的物理/运行事件 |
| 状态机 | `device.operation.state.changed` | 工作流、约束 | 状态机运行状态已改变 |
| 约束引擎 | `execution.pause.requested` | 任务运行时 | 请求暂停任务 |
| 约束引擎 | `execution.abort.requested` | 任务运行时 | 请求终止任务 |
| 约束引擎 | `constraint.violation.detected` | 日志、告警 | 约束违规事实 |

规则：`*.requested` 是意图，不等于执行成功；`*.succeeded/failed/changed` 是不可逆事实。约束和工作流均不能直接调用 Adapter。

### 3.3 Adapter 命令与事件协商

保留既有 MQTT 主题和 `CommandMessageFormat`。状态机向 Adapter 下发：

```json
{
  "messageId": "cmd-uuid",
  "adapterName": "TestHeatPressureAdapter-01",
  "devicePoint": "Reactor1",
  "commandName": "heat",
  "parameters": {
    "targetTemperature": 80,
    "durationSec": 300
  },
  "timestamp": 1783938600000
}
```

`internal=true` 参数不进入系统消息；Adapter 根据自己的 `devicePoint` 配置注入。

Adapter 的 `EventMessageFormat.payload` 必须回显 `messageId`：

```json
{
  "adapterName": "TestHeatPressureAdapter-01",
  "devicePoint": "Reactor1",
  "eventName": "COMMAND_COMPLETED",
  "timestamp": 1783938605000,
  "payload": { "messageId": "cmd-uuid" }
}
```

命令生命周期事件采用现有 Adapter 配置中的固定命名约定：

| Adapter `eventName` | 状态机内部事实 |
|---|---|
| `COMMAND_RECEIVED` | `device.command.accepted` |
| `COMMAND_RUNNING` | `device.command.running` |
| `COMMAND_COMPLETED` | `device.command.succeeded` |
| `COMMAND_FAILED` | `device.command.failed` |
| `COMMAND_TIMEOUT` | `device.command.timed_out` |
| `COMMAND_CANCELLED` | `device.command.cancelled` |

`opEvents` 不推断业务语义，统一转换为 `device.operation.event`，再由设备状态机模型的既有 transition 定义决定状态迁移。

## 4. 业务时间线

### 4.1 建模与发布

1. 用户注册 Adapter，系统解析并保存 `PARSED_CONFIG`。
2. 用户创建设备模型，选择 Adapter 模板和业务能力映射。
3. `CapabilityCatalogService` 对工作流、状态机、约束提供只读的已解析能力视图；各模块不直接读取 Adapter 表。
4. 用户保存流程模型：`FLOW_MODELS` 保存节点引用、接口连接、端口连接；`FLOW_NODE` 保存节点的完整静态定义。
5. 发布时由 `WorkflowDefinitionCompiler` 校验节点引用、`node_id_ref` 唯一性、连接端点、子流程引用与能力引用。只有通过校验的版本可创建任务。

### 4.2 任务与子流程执行

1. 创建 `TASK`，固定 `flow_model_id`、资源映射、任务变量和任务级约束。
2. 运行时为入口节点创建 `TASK_STEP`：`parent_step_id = null`、`step_depth = 0`、`node_status = PENDING`。
3. 节点进入 `RUNNING` 后，工作流运行时按节点类型执行：功能节点本地完成；设备能力节点发布 `device.capability.requested`；子流程节点创建自身步骤后启动子步骤。
4. 子流程内每个节点均创建新的 `TASK_STEP`，`parent_step_id` 指向子流程节点步骤，`step_depth = parent.step_depth + 1`。
5. 子流程出口成功时，父子流程节点步骤成功，再依据模型连接继续主流程；子流程失败时，父步骤失败。
6. 设备节点只在收到同一 `messageId` 的 `device.command.succeeded` 后成功；收到失败、取消或超时后失败。
7. 任务到达主流程结束节点后置为 `COMPLETED`；任一未被流程定义处理的节点失败则置为 `FAILED`。

`TASK_STEP.node_id_ref` 保留：它是由 `flow_node_id` 写入时同步生成的受控冗余，用于按流程内节点快速查询执行历史；不得由前端传入。

`TASK.current_flow_node_id` 与 `current_node_id_ref` 在第一阶段仅作为任务运行时更新的查询字段，不由前端写入。其语义统一为“当前活动的最深层步骤”；主流程进度由深度为 0 的步骤树推导，不再混用两种含义。

`TASK.task_variables` 只保存任务参数、非敏感变量和引用标识；密码、Token 等不写入 JSONB。

### 4.3 约束并发处理

1. 约束引擎订阅状态机和运行时发出的事实信号。
2. 规则命中后写入约束违规记录，并发出 `constraint.violation.detected`。
3. `SYSTEM` 动作转换为 `execution.pause.requested` 或 `execution.abort.requested`；设备动作转换为来源为 `CONSTRAINT` 的 `device.capability.requested`。
4. 任务运行时与状态机分别处理请求，并记录最终状态事实到执行日志。

## 5. 执行日志

`EXECUTION_LOG` 是统一执行日志，不是任务专属日志。

- `source_type`：`TASK`、`MANUAL`、`CONSTRAINT`、`SYSTEM`、`ADAPTER`。
- `task_id`、`task_step_id`、`device_instance_id`：按来源可为空。
- `log_level`：`INFO`、`WARN`、`ERROR`。
- `log_info`：可读摘要；不得包含密码、Token 等敏感数据。

现有表没有独立的 `message_id` 字段。任务来源的 `messageId` 放入 `TASK_STEP` 的接口快照 JSONB，作为该步骤的命令交互快照；执行日志只记录可读摘要。手动或约束来源的跨进程关联查询无法在现有表中按 `messageId` 建索引，这是冻结数据库下的既有限制，不以非语义字段规避。

## 6. 开发阶段与并行边界

### 阶段 A：Management 收口

- 新增 `ExecutionLogService`：内部 `append(...)` 与只读分页查询。
- 新增只读 `ExecutionLogController`：按来源、任务、步骤、设备、时间分页检索。
- `TaskService.logs` 改为委托 `ExecutionLogService`，保留任务日志便利接口。
- 定义并接入工作流聚合 DTO；禁止 Controller 直接接收 `Map` 或数据库实体作为业务写入协议。
- 让引擎停止直接注入 Mapper，改为调用 Management Service。

### 阶段 B：协议与适配层

- 在代码中建立 `protocol` 包：信号名称常量、信号对象、消息校验器、Adapter 事件翻译器。
- 统一生成和校验 `messageId`，验证 Adapter 回调回显。
- 建立 `CapabilityCatalogService` 只读接口。

### 阶段 C：流程定义编译

- 实现 `WorkflowDefinitionCompiler`。
- 校验 `FLOW_MODELS` 三个 JSONB 与 `FLOW_NODE` 的引用关系。
- 校验后生成内存中的 `ExecutionPlan`；不调设备。
- 流程发布、任务创建只能使用通过校验的流程版本。

### 阶段 D：纯运行时

- 重写任务状态流转和 `TASK_STEP` 父子步骤树。
- 实现入口、功能节点、子流程、结束、失败传播与执行日志。
- 使用假的 `CommandGateway` 做端到端测试，不连接真实 Adapter。

### 阶段 E：真实设备与约束接入

- 状态机实现 `device.capability.requested` 到 Adapter 命令的映射。
- 处理 Adapter 生命周期回调，驱动步骤完成或失败。
- 接入约束事实订阅、暂停/终止和安全能力调用。
- 删除旧 `WorkflowEngine` 中直接调用手动控制、轮询设备状态的路径；不兼容旧执行逻辑。

## 7. 完成标准

1. Management 不再允许工作流运行时直接使用 Mapper。
2. 流程模型发布前能校验节点、连接、能力和子流程引用。
3. 子流程执行可由 `TASK_STEP.parent_step_id` 与 `step_depth` 完整还原。
4. 同一设备命令通过 `messageId` 从工作流请求关联到 Adapter 回调。
5. 任务、手动、约束和系统来源的日志均能查询。
6. 旧工作流引擎不再参与新任务执行。
