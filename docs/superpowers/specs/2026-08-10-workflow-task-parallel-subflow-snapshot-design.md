# 工作流任务级并行、子流程调度与接口快照设计

日期：2026-08-10  
状态：已确认，待实施

## 1. 目标与适用范围

本设计扩展 `2026-08-10-workflow-node-trigger-polling-design.md`，补充并覆盖以下运行时行为：

- 工作流引擎按可配置周期集中扫描运行中任务，默认周期为 100ms；
- 不同任务并行处理，同一任务内主流程、分支和子流程步骤顺序求值；
- `TASK_STEP.INTERFACE_IN_SNAPSHOT` 和 `INTERFACE_OUT_SNAPSHOT` 保存节点各接口的当前值数组，不保存路由信封；
- 子流程仍属于原任务，由工作流引擎负责创建、调度并在完成后通知父节点；
- 所有持久化信号格式以《协议规范》《工作流模型》和《系统执行规范》为权威，运行时不得自行发明字段。

原设计中的接口多触发器、OUT 后 IN 遍历、冻结快照、独立上升沿、内联 `EMIT`/`UPDATE`、合法生命周期转移和 `_triggerStates` 规则继续有效。数据库表结构保持不变。

## 2. 接口当前值快照

### 2.1 规范定义

在《系统执行规范》中增加接口快照定义。单个快照项采用以下结构：

```json
{
  "interfaceName": "Interface_workflow_in",
  "signalName": "SUBFLOW_COMPLETED"
}
```

尚未收到或发出信号时：

```json
{
  "interfaceName": "Interface_workflow_in",
  "signalName": null
}
```

有协议载荷时：

```json
{
  "interfaceName": "Interface_state_in",
  "signalName": "CMD_STATE",
  "payload": {
    "deviceModelId": 1,
    "deviceInstanceId": 21,
    "messageId": "message-1",
    "stateName": "COMPLETED",
    "timestamp": 1786339200000
  }
}
```

《系统执行规范》中的 `WorkflowInterfaceSnapshotItem` 负责声明：

- `interfaceName` 为必填字符串；
- `signalName` 为必填字段，类型为字符串或 `null`；
- `payload` 为可选对象；
- `signalName == null` 时不得保留 `payload`；
- `signalName != null` 时，`signalName + payload` 必须符合《协议规范》的 `SystemSignalFormat` 及具体信号载荷定义。

`WorkflowInterfaceSnapshot` 定义为 `WorkflowInterfaceSnapshotItem` 数组。JSON Schema 无法完成的接口引用、方向、`allowedSignals` 成员关系和信号载荷选择，由后端编译及运行时语义校验负责。

### 2.2 TASK_STEP 持久化语义

创建 `TASK_STEP` 时，根据节点模型初始化两个完整数组：

- `INTERFACE_IN_SNAPSHOT` 包含该节点每个 IN 接口，顺序与模型声明一致；
- `INTERFACE_OUT_SNAPSHOT` 包含该节点每个 OUT 接口，顺序与模型声明一致；
- 每个初始项的 `signalName` 均为 `null`。

新信号只替换目标接口对应数组项的当前值，不追加历史记录，也不清空其他接口。新信号没有载荷时必须移除该项旧的 `payload`。

快照中不得保存以下路由字段：

- `sourceNodeIdRef`；
- `sourceInterfaceName` 或 `sourceInterface`；
- `targetInterfaceName`；
- `inputSignalName`；
- `inputPayload`；
- `sourceOutput`。

路由关系由工作流定义中的 `interfaceConnections` 提供；当前 `TASK_STEP` 已经标识目标节点，快照只需要接口名和该接口的当前协议值。

### 2.3 触发器求值上下文

引擎遍历某个接口时，从该接口自己的快照项构造求值上下文：

- `signalName` 表示当前接口信号名；
- `payload` 表示当前接口信号载荷；
- 内部变量来自 `VARIABLE_SPACE`，但排除 `_triggerStates`；
- `nodeLifecycleState` 只来自 `TASK_STEP.NODE_STATUS`；
- BRANCH、AGGREGATE 可额外读取本轮 `expression` 结果。

不得继续使用节点级统一的“最后输入信号”，也不得把完整冻结求值快照自动作为 EMIT 载荷。EMIT 只能产生协议和模型明确声明的信号及载荷。

## 3. 子流程运行语义

### 3.1 子流程仍属于原任务

`SUBFLOW_NODE` 不创建新的 `TASK`。子流程步骤使用：

- 与父流程相同的 `TASK_ID`；
- 指向父 `SUBFLOW_NODE` 步骤的 `PARENT_STEP_ID`；
- 父步骤 `STEP_DEPTH + 1` 的层级深度。

主流程和全部层级子流程共享任务生命周期、资源映射、暂停、终止和失败处理。子流程不是可独立控制的任务。

### 3.2 启动与幂等

父 `SUBFLOW_NODE` 只能通过自身触发器的生命周期 `UPDATE` 从 `PENDING` 进入 `RUNNING`。引擎不得因收到 `ACTIVE` 自动改变其生命周期。

下一轮处理该任务时，引擎发现 `SUBFLOW_NODE == RUNNING`，根据 `subFlowModelId` 检查对应子流程 `START` 步骤：

1. 尚不存在时，使用同一 `TASK_ID`、父步骤 ID 和递增深度创建；
2. 已存在时直接复用，不重复创建；
3. 本轮步骤列表在任务轮次开始时冻结，新建 `START` 步骤从下一轮参与求值。

启动子流程属于工作流引擎的结构性调度职责，不增加“向引擎发送信号”的虚构接口或调度信号。

### 3.3 完成通知

引擎区分根流程 END 和子流程 END：

- 根流程 END 成功后完成整个 `TASK`；
- 子流程 END 成功后不完成 `TASK`，而是把父 `SUBFLOW_NODE` 的 `Interface_workflow_in` 当前值写为 `SUBFLOW_COMPLETED`；
- `SUBFLOW_COMPLETED` 当前不携带业务载荷；
- 父节点在下一轮由自己的触发器读取该信号，并通过生命周期 `UPDATE` 申请 `RUNNING -> SUCCEEDED`；
- 引擎只校验并持久化模型 `lifecycle.transitions` 中声明的合法转移。

不动态修改子流程 END 模型。子流程失败继续使用现有任务失败语义；在协议没有声明其他子流程结果信号前，不自行增加 `SUBFLOW_FAILED` 等信号。

## 4. 任务级并行调度

### 4.1 并发边界

采用一个共享的工作流引擎和一个专用有界工作线程池：

- 不同 `TASK_ID` 可以同时由不同工作线程处理；
- 同一 `TASK_ID` 同一时刻最多有一个工作线程；
- 同一任务内所有主流程、并行分支和子流程步骤保持顺序求值；
- 节点和任务都不长期绑定固定线程；
- 设备执行和等待子流程期间不占用工作线程。

同一任务的节点顺序求值不意味着设备顺序运行。节点 EMIT 设备启动信号后立即返回；多个设备可以在外部同时执行，状态回执持久化后由后续轮次处理。

### 4.2 调度流程

集中调度器使用可配置固定延迟：

```text
smartlab.workflow.poll-interval-ms = 100
```

每次调度只负责：

1. 查询数据库中的 `RUNNING` 任务；
2. 对不在 `inFlightTaskIds` 中的任务进行原子认领；
3. 把任务 ID 提交到专用有界线程池；
4. 立即结束本次扫描，不在调度线程中执行节点逻辑。

工作线程负责：

1. 按任务 ID 重新读取最新任务状态；
2. 在现有任务锁保护下读取本轮可轮询步骤列表；
3. 按步骤顺序逐节点执行一次完整求值；
4. 无论成功或异常，都在 `finally` 中移除 `inFlightTaskIds` 标记并归还线程。

若线程池拒绝提交，必须撤销该任务的 in-flight 标记并记录调度告警；任务保持 `RUNNING`，下一轮重新尝试，不能因为线程池繁忙而判定任务失败。

### 4.3 一轮任务求值

每个任务轮次采用以下层级：

```text
RUNNING任务
  -> 本轮可轮询TASK_STEP列表
    -> 单个节点
      -> OUT接口，保持模型相对顺序
      -> IN接口，保持模型相对顺序
        -> bindingTriggers声明顺序
```

触发器在各自上升沿立即执行动作；同轮多个上升沿全部执行。本轮动作产生的状态变化不写回当前节点冻结快照，后续触发器从下一轮开始观察。

## 5. 软实时边界

默认 100ms 是调度目标，不是硬实时截止保证。正常响应延迟包括：

```text
0~100ms 等待下一次任务扫描
+ 数据库读取时间
+ 线程池排队时间
+ 本轮节点和触发器求值时间
```

为避免某个任务长期占用线程：

- 每次提交只处理该任务的一轮，不循环运行到任务终态；
- 节点动作不得同步等待设备或子流程完成；
- 同一任务仍在处理时，后续扫描直接跳过该任务；
- 工作线程数量、最大线程数和队列容量均配置化；
- 记录扫描耗时、任务排队时间、单轮处理耗时、活动任务数和拒绝提交次数。

若单轮处理超过 100ms，该任务会跳过重叠扫描，并在完成后的后续扫描重新进入。Java、数据库和外部设备组成的系统不承诺严格 100ms 上界。

本设计保证单个后端进程内的任务级互斥。未来部署多个工作流后端实例时，需要另行增加数据库租约或分布式任务认领；仅靠 JVM 内 `inFlightTaskIds` 和任务锁不能提供跨进程互斥。

## 6. 错误处理和一致性

- 单个任务求值异常只按既有规则影响该任务，不阻塞其他并行任务；
- 调度基础设施暂时繁忙不等于业务任务失败；
- 子流程 START 创建必须幂等；
- 子流程完成通知写入同一接口当前值时必须幂等；
- 接口快照更新必须保留数组中其他接口项；
- 信号非空时必须校验目标接口、方向、`allowedSignals` 和协议载荷；
- `_triggerStates` 仍是 `VARIABLE_SPACE` 唯一系统保留区，不得进入接口 payload；
- 所有新写入只产生规范数组格式，不再产生旧路由信封。

## 7. 测试策略

### 7.1 接口快照

- 创建步骤时按 IN/OUT 方向初始化完整接口数组；
- 未赋值接口保存 `signalName: null`；
- 更新一个接口不改变其他接口；
- 无载荷信号清除旧 payload；
- 输入和输出均使用 `interfaceName/signalName/payload`；
- 快照不包含任何路由信封字段；
- 触发器读取所属接口的当前值，而不是节点级最后输入；
- CMD_STATE、OP_STATE 载荷保持协议定义并可完成设备步骤定位。

### 7.2 子流程

- RUNNING 子流程节点幂等创建同任务 START 步骤；
- 子步骤继承 TASK_ID，正确设置 PARENT_STEP_ID 和 STEP_DEPTH；
- 新建 START 从下一轮开始求值；
- 子流程 END 成功写入父接口 `SUBFLOW_COMPLETED`；
- 父生命周期只由下一轮 UPDATE 触发器推进；
- 根 END 完成任务，子流程 END 不提前完成任务；
- 子流程失败不生成未声明信号。

### 7.3 任务并行

- 两个不同任务能够同时进入不同工作线程；
- 同一任务不会并发处理或重复入队；
- 同一任务内步骤仍按确定顺序求值；
- 拒绝提交后撤销 in-flight 标记并在下一轮重试；
- 一个任务异常不阻塞另一个任务；
- 轮询属性默认 100ms 且可以配置覆盖；
- 工作线程完成一轮后释放，不等待设备或任务终态。

## 8. 验收标准

1. 接口输入、输出快照均为完整的接口当前值数组。
2. 快照字段和信号载荷全部符合三份规范，不含自定义路由信封。
3. 未收到信号的接口明确保存 `signalName: null`。
4. 子流程使用同一 TASK_ID，由引擎幂等调度并以 `SUBFLOW_COMPLETED` 通知父节点。
5. 不同任务能够并行，同一任务的主流程和所有子流程步骤保持顺序求值。
6. 调度周期可配置且默认 100ms，调度线程只扫描和投递任务。
7. 工作线程每次只执行一个任务的一轮求值，等待期间不长期占用线程。
8. 调度拥塞、业务失败和协议错误具有互不混淆的处理结果。
9. 相关后端测试和完整回归测试通过。
