# 工作流节点触发器轮询执行设计

日期：2026-08-10  
状态：已确认，待实施

## 1. 目标与覆盖关系

本设计把工作流引擎统一为“调度全部活跃步骤、逐节点独立解释”的轮询执行器。每个节点的接口触发器直接持有动作；引擎按条件上升沿立即执行当前动作，不跨节点收集动作，不在节点内部重排动作，也不因收到 `ACTIVE` 自动推进节点生命周期。

本设计覆盖以下既有设计中的冲突部分：

- `2026-08-06-workflow-model-to-task-execution-design.md` 中由引擎隐式推进节点状态、固定系统触发动作以及分支 true/false 固定出口的描述；
- `2026-07-19-workflow-action-runtime-design.md` 中命名动作引用、动作序列统一排序和旧动作游标语义。

不冲突的模型版本、资源绑定、设备状态机、约束和观察层设计继续有效。数据库表结构保持不变。

## 2. 模型边界

### 2.1 节点与调度单位

工作流引擎负责一个任务中的全部可轮询 `TASK_STEP`，但每个步骤代表的节点独立处理。可轮询步骤包括 `PENDING`、`RUNNING`、`TERMINATING`，以及刚进入终态但尚未完成终态触发器评估的步骤。调度器逐任务加锁，再逐步骤调用节点解释器；不存在跨节点动作批处理。

节点类型保持：

- `DEV_NODE`：设备能力节点；
- `FUNC_NODE`：功能节点，包括 START、END、BRANCH、AGGREGATE；
- `SUBFLOW_NODE`：子流程节点。

只有 BRANCH、AGGREGATE 可以声明和计算 `expression`。工作流引擎不直接读取设备遥测；设备属性映射层先把遥测值同步到节点内部变量，引擎只消费节点变量。

### 2.2 条件可见对象

`bindingTriggers[].condition.object` 只能解析：

- 节点内部变量；
- 由 `TASK_STEP.NODE_STATUS` 提供的只读 `nodeLifecycleState`；
- 当前接口输入信号名称；
- 当前接口输入载荷及其路径；
- BRANCH、AGGREGATE 的 `expression` 结果。

`nodeLifecycleState` 不写入 `VARIABLE_SPACE`。`VARIABLE_SPACE` 保存用户内部变量，以及唯一允许的系统保留区 `_triggerStates`。

### 2.3 动作目录与内联动作

`WorkflowNodeAction` 定义所有合法动作对象的公共格式。节点 `actions[]` 仅声明该节点允许使用的系统动作名子集，元素只能是 `EMIT` 或 `UPDATE`。触发器的 `action` 是完整内联对象，格式遵循 `WorkflowNodeAction`。

示例：

```json
{
  "actions": ["UPDATE", "EMIT"],
  "interfaces": [
    {
      "name": "Interface_workflow_in",
      "direction": "IN",
      "interfaceType": "WORKFLOW",
      "allowedSignals": ["ACTIVE"],
      "bindingTriggers": [
        {
          "condition": {
            "object": "inputSignalName",
            "operator": "=",
            "threshold": "ACTIVE"
          },
          "action": {
            "actionName": "UPDATE",
            "payload": {
              "updateType": "NODE_LIFECYCLE",
              "targetName": "RUNNING"
            }
          }
        }
      ]
    }
  ]
}
```

后端编译阶段必须验证 `trigger.action.actionName` 属于当前节点 `actions[]`。JSON Schema 负责对象结构、字段类型和枚举；动作条件字段、引用关系和跨字段成员关系由编译器负责。

## 3. 单节点轮询语义

### 3.1 本轮求值快照

处理节点前构造不可变的本轮求值快照，内容包括内部变量、`NODE_STATUS`、接口输入快照以及可用的 expression 结果。本轮所有 condition 都读取同一快照。

动作在触发时立即执行并持久化，但动作产生的变量或生命周期变化不写回本轮求值快照。因此，后续触发器要到下一轮才能观察到本轮动作的结果。

例如：

```text
第 N 轮：CMD_STATE == COMPLETED
         → 立即执行 UPDATE：RUNNING → SUCCEEDED

第 N+1 轮：nodeLifecycleState == SUCCEEDED
           → 立即执行 EMIT：ACTIVE
```

### 3.2 遍历顺序

单节点每轮按以下顺序处理：

1. 所有 OUT 接口，保持它们在模型中的相对声明顺序；
2. 所有 IN 接口，保持它们在模型中的相对声明顺序；
3. 每个接口内按 `bindingTriggers[]` 声明顺序处理。

接口方向不限制是否可以声明触发器。顺序只用于提供确定性执行次序。

### 3.3 立即执行和多触发器

引擎不先收集动作，也不统一执行 UPDATE 或 EMIT。某触发器出现有效上升沿时，立即执行该触发器内联的动作，然后继续遍历。

同一轮多个触发器满足条件时全部执行：

- 不按动作名去重；
- 不限制同一轮只能有一个 EMIT；
- 不强制 UPDATE 先于 EMIT；
- 每个触发器独立记录和恢复触发状态。

本轮没有触发器命中只表示节点继续等待，不得因此把节点或任务标记失败。

## 4. 上升沿与持久化

### 4.1 判定规则

每个触发器保存上次条件结果：

```text
false → false：无动作
false → true ：立即执行动作；成功后记录 true
true  → true ：无动作
true  → false：记录 false，恢复下一次触发资格
```

动作失败时不得提前把状态记录为 true。失败按照现有步骤错误规则固化原因，不能伪装成成功触发。

### 4.2 状态结构与稳定键

状态保存在 `TASK_STEP.VARIABLE_SPACE._triggerStates`：

```json
{
  "temperature": 25.5,
  "_triggerStates": {
    "<stable-trigger-key>": true
  }
}
```

稳定键由节点接口名、触发器规范化内容及相同内容在该接口内的出现序号生成，不直接使用全局数组下标。规范化内容包括 condition 和内联 action；编辑条件或动作后视为新触发器并从 false 开始。即使两个触发器内容完全相同，也分别保存状态并分别执行，符合触发器互相独立的规则。

步骤从非终态进入 `SUCCEEDED`、`FAILED` 或 `TERMINATED` 后，必须在下一轮继续作为可轮询步骤，使终态条件触发器能够观察到本次变化。终态轮次完整执行后，在 `_triggerStates` 系统区记录终态已观察标记；此后该步骤不再参与常规轮询。该标记不得暴露为用户变量。

`_triggerStates` 不得作为用户内部变量、端口值、表达式目标或接口业务载荷向下游传播。

## 5. 动作语义

### 5.1 EMIT

格式：

```json
{
  "actionName": "EMIT",
  "payload": {
    "targetInterfaceName": "Interface_state_out",
    "signalName": "WF_EXECUTE_START"
  }
}
```

编译和运行时必须验证：

- `targetInterfaceName` 存在且方向为 OUT；
- `signalName` 位于目标接口 `allowedSignals`；
- WORKFLOW 信号只沿节点连接路由；
- STATE 信号只沿节点到设备状态机连接发送；
- 外部发送使用稳定 messageId 保证重试幂等。

WORKFLOW EMIT 负责发送和路由信号，不得绕过节点动作自动制造前置生命周期迁移。STATE EMIT 返回等待设备空闲或等待外部回执时，后续轮询保持节点活跃，不能因本轮没有再次 EMIT 而失败。

`WAIT_DEVICE_IDLE` 表示动作尚未成功，不把触发器状态置为 true，下一轮继续尝试同一动作。设备已接受命令并返回 `AWAIT_EXTERNAL_SIGNAL` 时，动作视为本次上升沿已经成功，记录 true 并等待设备回执；稳定 messageId 防止崩溃边界上的重复设备命令。

### 5.2 UPDATE：内部变量表达式

```json
{
  "actionName": "UPDATE",
  "payload": {
    "updateType": "INTERNAL_VARIABLE",
    "targetName": "scaledTemperature",
    "valueExpression": "temperature * 100"
  }
}
```

编译器验证目标变量存在且表达式非空；执行器计算表达式并验证结果符合变量声明类型。

### 5.3 UPDATE：内部变量常量

```json
{
  "actionName": "UPDATE",
  "payload": {
    "updateType": "INTERNAL_VARIABLE",
    "targetName": "retryEnabled",
    "value": true
  }
}
```

`value` 保存 JSON 常量，允许数字、字符串、布尔、对象和数组。执行器直接使用该值，不进入表达式求值器，再按内部变量声明类型校验。

对 `INTERNAL_VARIABLE` UPDATE，`value` 与 `valueExpression` 必须且只能出现一个；两者同时存在或同时缺失均为编译错误。

### 5.4 UPDATE：节点生命周期

```json
{
  "actionName": "UPDATE",
  "payload": {
    "updateType": "NODE_LIFECYCLE",
    "targetName": "RUNNING"
  }
}
```

生命周期 UPDATE 由节点动作发起，引擎只执行校验和持久化：

1. `targetName` 必须属于节点 `lifecycle.states`；
2. 当前 `TASK_STEP.NODE_STATUS → targetName` 必须存在于 `lifecycle.transitions`；
3. 合法时更新 `NODE_STATUS`，并维护开始、结束时间、耗时、日志和观察事件；
4. 非法时驳回动作并按步骤执行错误处理。

引擎收到 ACTIVE 时不得自动执行 `PENDING → RUNNING`。该迁移必须由节点触发器中的 UPDATE 动作发起。生命周期 UPDATE 不接受 `value` 或 `valueExpression`。

## 6. 功能节点和 N 叉分支

START、END 继续承担流程边界职责，但其运行语义也必须通过规范化后的节点触发器和内联动作表达，不依赖任意命名动作。

BRANCH 不再固定拥有 true/false 两个出口。用户可声明任意数量的 WORKFLOW OUT 接口，并在任意接口上配置触发器。expression 只产生计算结果，路径判断完全由触发器 condition 完成。多个出口条件同轮成立时全部 EMIT，形成并行路径。

AGGREGATE 在既有前序完成条件满足后才进入触发器求值；其 expression 可参与进一步计算，但不替代接口触发器。

编译器必须允许业务接口和 OUT 接口触发器，删除“OUT 不得声明 bindingTriggers”“系统模板外不得新增接口”“自定义动作只能 UPDATE”等旧限制。

## 7. 编译、规范化与兼容

### 7.1 编译器责任

`WorkflowDefinitionCompiler` 负责：

- 校验节点 `actions[]` 仅包含 `EMIT`、`UPDATE` 且无重复；
- 校验每个内联动作的 `actionName` 属于节点 `actions[]`；
- 按动作名校验 payload 条件字段；
- 校验 UPDATE 目标、常量或表达式类型、生命周期目标；
- 校验 EMIT 接口、方向、接口类型和信号；
- 允许 IN/OUT 接口配置多触发器；
- 仅允许 BRANCH、AGGREGATE 声明 expression；
- 支持 N 个分支输出及其连线。

### 7.2 规范化器责任

`WorkflowDefinitionCanonicalizer` 和系统模板负责把旧模型转换为新模型：

- `actionName + actionType + 扁平参数` 转成内联 `actionName + payload`；
- 节点旧动作定义归并成 `actions[]` 动作名子集；
- 旧触发器字符串引用解析成完整内联动作；
- 旧分支 true/false 模板可以读入并转换，但新模型不强制固定出口；
- 保留系统项身份和已有业务接口、触发器、变量、连接。

运行中任务必须继续使用其固定模型版本，不把新草稿结构热替换到既有步骤。

## 8. 前端设计器

前端动作面板不再要求用户为动作起名，也不再展示 actionType：

- 节点 `actions[]` 通过 EMIT、UPDATE 两个系统动作开关或只读能力标识维护；
- 触发器直接编辑内联动作；
- EMIT 编辑目标 OUT 接口和允许信号；
- UPDATE 编辑 updateType、targetName，以及常量/表达式输入模式；
- INTERNAL_VARIABLE 常量模式按变量数据类型提供对应输入控件；
- NODE_LIFECYCLE 目标从合法状态集合中选择；
- 所有接口，包括 OUT，都可以增加多个触发器；
- BRANCH 可以增加和删除自定义输出接口，不显示固定 true/false 假设。

前端提供即时结构校验，后端编译器仍是最终权威。

## 9. 错误处理与并发边界

- 单进程内继续按任务锁串行处理步骤和状态机回执；
- 多实例部署时，触发器状态认领和外部动作幂等必须依赖数据库条件更新或稳定 messageId，不能只依赖 JVM 锁；
- 一个触发器动作失败不记录成功上升沿，步骤记录明确失败原因；
- 非法生命周期迁移、缺失动作字段、未声明动作名、非法信号和非法变量类型均在发布编译阶段尽早失败；
- 运行时发现持久化旧数据或外部状态竞争导致的同类错误时，不静默跳过。

## 10. 测试策略

### 10.1 Schema、规范化和编译

- 新动作目录、内联动作和 payload 格式；
- 未声明 actionName、非法 EMIT、非法 UPDATE；
- 常量和表达式二选一；
- 生命周期合法/非法转换；
- OUT 接口触发器、多触发器和重复触发器；
- 旧命名动作模型规范化；
- 三路及以上 BRANCH 编译和路由。

### 10.2 引擎

- OUT 接口先于 IN 接口；
- 触发器命中后立即执行，不收集、不排序、不去重；
- 多个同轮上升沿全部执行；
- UPDATE 的结果只在下一轮被其他条件观察；
- false→true 触发、true→true 抑制、true→false 复位；
- STATE EMIT 等待期间跨多轮不重复发送也不失败；
- WAIT_DEVICE_IDLE 能继续重试；
- 终态步骤在下一轮完成终态触发器评估后才退出轮询；
- 无触发器命中时节点保持活跃；
- `_triggerStates` 不进入业务输出；
- 引擎不自动执行 PENDING→RUNNING。

### 10.3 前端

- 内联 EMIT、变量 UPDATE、生命周期 UPDATE 编辑；
- 常量/表达式模式；
- OUT/IN 接口触发器；
- N 叉分支接口编辑；
- 旧模型加载和规范化结果回显；
- 生产构建。

## 11. 验收标准

1. 工作流引擎轮询全部活跃步骤并逐节点独立处理。
2. 单节点按 OUT 后 IN、各自声明顺序遍历触发器。
3. 每个上升沿触发器立即执行自己的内联动作，没有动作批处理或类型重排。
4. 同轮多个触发器全部执行，动作变化从下一轮开始参与条件求值。
5. 持续为真的条件不会重复产生副作用，恢复为 false 后可再次触发。
6. 节点只有通过 UPDATE 动作并通过 transitions 校验才能改变生命周期。
7. 内部变量 UPDATE 同时支持直接常量和表达式。
8. 工作流引擎只读取节点变量，不直接读取设备遥测。
9. BRANCH 支持任意数量输出接口和并行多命中。
10. 旧模型能够规范化为新结构，既有任务版本不发生语义漂移。
11. 后端测试、前端测试和生产构建全部通过。
