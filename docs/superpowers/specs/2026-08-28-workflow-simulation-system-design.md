# 工作流 / 任务模拟执行设计

日期：2026-08-28  
状态：方案已确认  
数据库：只两列——`DEVICE_INSTANCES.instance_kind`（`PHYSICAL` / `SIMULATED` / `TEMPORARY`）、`TASK.execution_kind`（`PRODUCTION` / `SIMULATION`）

---

## 0. 明确不做 / 已点头

### 0.1 明确不做

| 项 | 说明 |
|---|---|
| `simulation_origin`、`simulation_session_id` | 不加列。流程模拟 vs 任务仿真看绑定的是 `TEMPORARY` 还是 `SIMULATED` |
| 任务仿真走系统内模拟器 | 任务仿真走真 MQTT → 虚拟点位 |
| `SIMULATED` 进 Adapter 模拟器 | 常驻机只出现在任务仿真 |
| 用名字区分真/假 | 只认 `instance_kind` |
| 改编译器 / 流程 schema | 错稿仍走校验 |
| Adapter 后补虚拟点位再给旧模型补建 `SIMULATED` | 改 Adapter 时其下不能有模型/实例；改完用户会重建模型，创建时再判断即可 |

### 0.2 已点头

| 项 | 结论 |
|---|---|
| 虚拟点位声明 | INI：`[devicePoints.Reactor2]` 下加 `virtual=true`（JSON 为 `"virtual": true`） |
| DRAFT / 未保存画布 | 流程模拟允许 |
| 流程模拟 WAIT | 立即到期 |
| `SIMULATED` 复位 | 只重置孪生/运行时，**不清**数据表 |
| 控制台点动 | 仅 `PHYSICAL` |
| 设备列表 | 默认只出 `PHYSICAL` |
| 任务列表 | **全部显示**，带正式/模拟标记，并支持筛选（不是默认只显示生产） |
| 流程模拟总超时 | 整次运行墙钟默认 **60s**（可配置）。到点失败并清理。单条指令仍用 10s SENT 看门狗 |
| 流程模拟遇错 | **停下**。流程自身的问题不得忽略 |
| Adapter 模拟器出数 | 只回 `parsed_config` 已声明的 cmd 事件；**不写遥测、不改孪生属性、不按 `target*` 对名填温度**。分支覆盖靠结构遍历，不靠假量让 XOR 取胜 |

### 0.3 改动边界（不该改的不改）

沿用现有 `WorkflowEngine`、`StateMachineEngine`、`TASK` / `TASK_STEP`、编译/校验、MQTT 命令信封。不为模拟第二套状态机，不为跑通去放宽编译器。

| | 内容 |
|---|---|
| **不准改** | 状态机转移语义；`StateMachineSendActionEvent` 字段；`WorkflowDefinitionCompiler` / Canonicalizer / `WorkflowModelDocument`；生产 `requireExecutableDefinition`（仍只认 ACTIVE）；真机 MQTT 路径；`PHYSICAL` 的就绪检查；Agent 第一期；**不要把生产 `WorkflowEngine` 改成 DFS 全路径** |
| **只加、不改语义** | `instance_kind`、`execution_kind`；设备/任务列表过滤与标记；绑定互斥；`TEMPORARY` 创建/删除；`SIMULATED` 在模型保存时创建与复位 |
| **adapter 包出站** | 用 `instanceId` 查 kind。仅 `TEMPORARY` 不 `publishCommand`、改走模拟器。模拟器行为来自 `ADAPTER_INDEX.parsed_config`，**不是**设备模型 `adapterContract`。`PHYSICAL` / `SIMULATED` 与现在完全相同 |
| **流程模拟编排** | 新结构遍历器跟 `NODE_TO_NODE`，不求值触发器。DEV_NODE 用原状态机探测。可落 `execution_kind=SIMULATION` 任务供列表/报告，**禁止 `start` 生产引擎跑整图** |

任务仿真：引擎不读 kind，与真机同一条执行链。  
流程模拟：整图不走 `WorkflowEngine`；设备探测仍走 `StateMachineEngine` + MQTT 分叉。

---

## 1. Adapter 包怎么知道实例类型

状态机**不告诉**它 kind。现有 `StateMachineSendActionEvent` 只有 `instanceId`，没有 `instance_kind`，也不为此改事件、不改状态机。

`MqttAdapterMessagingService.handleStateMachineSendAction` 用 `event.instanceId()` **自己查** `DEVICE_INSTANCES.instance_kind`（一次按主键读取）：

- `TEMPORARY` → 不 `publishCommand`，交给包内模拟器
- `PHYSICAL` / `SIMULATED` → 现有 MQTT

查不到实例或 kind 非法 → 当作出站失败，不要默默当真机发。

---

## 2. `virtual=true` 要改多少地方

**解析器不用大改。** 现状：

- INI 的 `[devicePoints.xxx]` **没有**白名单；`index = 2` 这类字段已经经 `putIniScalar` 进 JSON。`virtual=true` 会变成布尔 `true`。
- JSON 的 `normalizeDevicePoint` 会**原样拷贝**点位上除映射外的字段。

真正要动的是认这个字段并使用它，不是重写配置格式：

| 改 | 做什么 |
|---|---|
| `AdapterManifestService` | 可选：规范化 `virtual` 为 boolean；提供 `isVirtualPoint` |
| 模型保存 | 该模型对应 Adapter 是否存在 `virtual==true` 的点位 → 才建 `SIMULATED` 并绑该点 |
| 真机绑定下拉 | 物理实例不要列出虚拟点位 |
| 样例 INI/JSON、开发指南 | 加一行说明 |
| 审阅页（可选） | 点位表显示是否虚拟 |

没有虚拟点位的旧配置行为不变。不必改 MQTT topic、命令/遥测信封、状态机。

---

## 3. 目标与原则

- **任务仿真**：数字仿真、留数。MQTT 真链路。引擎不区分 `PHYSICAL` 与 `SIMULATED`。
- **流程模拟**：设计器验走通。DRAFT 可跑。不发 MQTT，不写数据表。Java 模拟器只按模型已有契约回事件。
- 一个工作流引擎、一个状态机。分叉只在 adapter 包出站。
- 生产只绑 `PHYSICAL`；任务仿真只绑 `SIMULATED`；流程模拟只绑本次 `TEMPORARY`。

到达 END 只表示控制图能闭环，不是「实验科学正确」。

---

## 4. 概念（只有这两列）

| 列 | 值 | 含义 |
|---|---|---|
| `instance_kind` | `PHYSICAL` | 真机 |
| | `SIMULATED` | 每模型至多一台隐藏常驻机，映虚拟点位，有数据表 |
| | `TEMPORARY` | 流程模拟临时机，不绑点位、不建表，跑完删除 |
| `execution_kind` | `PRODUCTION` | 真跑 |
| | `SIMULATION` | 模拟跑（再看绑定区分任务仿真 / 流程模拟） |

「每模型一台 SIMULATED」靠**服务层**：创建前查该 `device_model_id` 是否已有 `SIMULATED`。  
**UNIQUE 索引**是数据库再加一道锁，防止两个请求同时插入两台。你现在只有列、没有唯一索引。建议：**代码保证即可，不必再加 UNIQUE**。

---

## 5. 总链路

```text
创建设备模型
  → 对应 Adapter 是否有 virtual=true 的点位？
      是：插入 SIMULATED（列表默认不可见）→ 现有逻辑建数据表 → 绑该虚拟点位
      否：不建（不支持任务仿真）

任务「模拟执行」
  → execution_kind=SIMULATION，流程须 ACTIVE
  → 按模型自动绑那台 SIMULATED（同模型多节点共用这一台）
  → 启动前复位孪生（不清数据表）
  → 与真机同一套引擎 + MQTT

设计器「模拟执行」
  → validate（DRAFT / 未保存 document 均可）
  → 按 deviceModelId 去重，每模型一条 TEMPORARY + 孪生（整次模拟共用，节点结束不删）
  → **不创建 Task**
  → 结构遍历 NODE_TO_NODE：BRANCH 逐条边走完；AGGREGATE 等全部入边到齐再继续；不求值条件
  → DEV_NODE：原 SM `WF_EXECUTE_START` → TEMPORARY 出站进模拟器 → 异步回 cmd 事件 → 以 CMD COMPLETED（或完成后复位 IDLE）为节点完成
  → 结束：删 TEMPORARY + 孪生；报告只在当次 HTTP 返回里
```

---

## 6. 常驻 `SIMULATED`

模型保存成功且该模型所属 Adapter 有虚拟点位时创建。已有则跳过。无虚拟点位不创建。映射失败则整次失败，不留未绑点位的 `SIMULATED`。

不因 Adapter 后来改配置去补建（见 0.1）。

---

## 7. 任务仿真

自动绑定；无 `SIMULATED` 则拒绝并说明。就绪检查与真机相同（要发 MQTT）。启动前复位见第 8 节。数据进该实例 `DATA_INDEX`。时间压缩由边缘 Adapter 写 `create_time`。

任务列表展示全部任务，行上有 `execution_kind` 提示，可筛选。

---

## 8. `SIMULATED` 复位

仅任务仿真启动前（及异常结束后）。断言 `instance_kind==SIMULATED`。不走 `MANUAL_EXECUTE_RESET`。

1. 已有未终态任务占用该实例 → 拒绝  
2. 孪生写回刚创建时的 CMD / OP / 属性 / 在线  
3. 丢掉该 instance 的内存状态机  
4. **不清**数据表  

Adapter 植物由边缘自行归零。`TEMPORARY` 不复位，用完删除。

---

## 9. 流程模拟

允许 DRAFT 与请求体 `document`。**不创建 TASK**。WAIT 节点在结构遍历里视为立刻可过。按模型去重建 `TEMPORARY`，整次模拟结束才删。整次墙钟默认 60s（可配置），超时记 `SIM_WALL_TIMEOUT` 并清理。结论在当次 `POST /api/workflow/simulate` 返回：走通与否、全部分支路径、遇错节点/错误码/建议。

---

## 10. 系统内 Adapter 模拟器

只处理 `TEMPORARY`。模拟器是进程内 Adapter，**配置只来自该设备所属 Adapter 的 `ADAPTER_INDEX.parsed_config`（配置文件解析结果）**，不是设备模型上的 `adapterContract`。`adapterContract.config.adapterName` / `categoryName` 只用来在库里定位那份 `parsed_config`。状态机仍是原引擎；入站走现有 `dispatchAdapterEvent`。禁止编造 parsed_config 里没有的事件名。找不到完成事件 → 记 issue 并停下。

回灌必须在 `CMD_START` 发送返回、SENT 落稳之后（异步线程，禁止在 `SEND` 同一栈里完成生命周期）。须在 10s SENT 看门狗内回灌。

**模拟器只回 cmd 事件**（`COMMAND_RUNNING` / `COMMAND_COMPLETED` 及中止对应事件，且名字必须同时存在于 `parsed_config.cmdEvents` 与模型 `STATE_TRANSITIONS` 的唯一 Adapter 入站转移）。**不写遥测、不写 `current_attr`、不按参数名匹配属性。** 状态机仍可把 `current_cmd_state` 推到 SENT/RUNNING/COMPLETED/IDLE。

不写数据中心（无 `DATA_INDEX`）。流程能否走通由结构遍历覆盖全部 `NODE_TO_NODE` 边，不靠孪生属性去满足 XOR。

---

## 11. 遇错即停：流程问题不得忽略

两件事分开：

| | 可以 | 不可以 |
|---|---|---|
| Adapter 模拟器 | 按 parsed_config 回已声明 cmd 事件 | 发明未声明事件名；写假温度/同名参数拷贝/`target*` 对名；同步回灌 |
| 流程本身 | 结构上把每条分支边走到 END/汇合 | 忽略连线错误、缺完成事件、状态机拒收；把生产引擎改成全路径 |

契约不全、状态机拒收、环、超时 → **停**，指出节点、错误码、原因。禁止为了到达 END 把缺的量填成缺省值。

报告至少包含：`nodeIdRef` / 节点名 / 能力名、稳定错误码、人话原因与修复建议、已走过的路径。

| 情况 | 码（示例） | 行为 |
|---|---|---|
| 编译/校验 blocking | 现有 `WorkflowIssue` | 不开跑 |
| 模型没有 SENT→RUNNING 或完成事件 | `SIM_NO_ADAPTER_EVENT` | 停 |
| 状态机拒收 | 现有拒收语义 | 停 |
| SENT 看门狗 | 现有 `FAILED` | 停 |
| 约束终止 | 现有约束 | 停 |
| 状态机未接受 WF_EXECUTE_START | `SIM_DEVICE_REJECTED` | 停 |
| 指令以 FAILED/ABORTED 结束 | `SIM_DEVICE_FAILED` | 停 |
| NODE_TO_NODE / 子流程成环 | `SIM_CYCLE` | 停 |
| 整次 60s 墙钟超时 | `SIM_WALL_TIMEOUT` | 停并清理 |

---

## 12. 隔离

| 动作 | `PHYSICAL` | `SIMULATED` | `TEMPORARY` |
|---|---|---|---|
| 生产绑定 | 是 | 否 | 否 |
| 任务仿真绑定 | 否 | 是 | 否 |
| 流程模拟创建 | 否 | 否 | 是 |
| MQTT | 是 | 是 | 否 |
| 数据表 | 有 | 有 | 无 |
| 默认设备列表 | 是 | 否 | 否 |
| 点动 | 是 | 否 | 否 |
| 启动前复位 | 否 | 是 | 否 |
| 跑完删除 | 否 | 否 | 是 |

---

## 13. API / 前端

- `POST /api/workflow/simulate`：`flowModelId` 和/或 `document`。不创建任务。返回走通/失败报告（`paths` 为全部分支路径）。  
- 任务创建：`executionKind`，默认 `PRODUCTION`；`SIMULATION` 时后端自动绑 `SIMULATED`。  
- 设备分页：`instanceKind` 缺省 `PHYSICAL`。  
- 任务列表：生产 + 任务仿真；流程模拟不出现。  
- 设计器：DRAFT 可点「模拟执行」；不打开绑定。

---

## 14. 代码边界

| 职责 | 位置 |
|---|---|
| 出站查 kind、TEMPORARY 短路 | `MqttAdapterMessagingService` |
| 模拟器 | `com.smartlab.adapter` |
| 认 `virtual`、建 `SIMULATED` | `AdapterManifestService` + 模型/实例保存 |
| 复位 | 仅接受 `SIMULATED` 的窄服务 |
| 流程模拟编排（结构遍历 + 超时 + 清临时机） | `WorkflowSimulationService` + `WorkflowStructureWalker` |
| 编译器 | 不改语义 |
| Agent | 第一期不改 |

---

## 15. 测试要点

- 出站：只对 `TEMPORARY` 跳过 MQTT；查 kind 用 instanceId。  
- INI `virtual=true` 解析后点位 `virtual` 为 true；无该字段的旧 INI 仍能注册。  
- 无虚拟点位不建 `SIMULATED`；有则一台+表+绑定。  
- 复位不清数据表。  
- 流程模拟 DRAFT 可跑；不 `start` WorkflowEngine；BRANCH 两条边都能走到且不读属性；同模型两个 DEV_NODE 顺序 COMPLETED；遇契约/流程错误停下并带节点信息；60s 总超时清理临时实例。  
- 任务列表能看到 `SIMULATION` 行。

---

## 16. 落地顺序

1. 实体两列；绑定/列表/点动隔离。  
2. `virtual` 判定 + 模型保存建 `SIMULATED`。  
3. 任务仿真自动绑定 + 复位 + MQTT。  
4. adapter 包按 instanceId 查 kind + 模拟器。  
5. 流程模拟 API 与设计器；遇错报告。  
6. 任务列表标记与筛选。
