# 生成工作流草稿

按顺序执行。某步发现前面不对，回到出错的那一步改完，再从该步起按顺序重做后面的步骤。

1. 分析用户描述，将需求拆解为具体操作步骤（如加热、冷却、搅拌、检测），识别每步可能涉及的设备类型或已有流程。不要直接把用户的措辞当作设备能力（`capability`）关键词——设备能力名和用户口语可能不同（如"热阱"和"反应釜"都与温度相关，但能力名各不相同）。对不上用户意图时，回到此步重新分析。
2. 调用 `list_device_catalog` 与 `list_workflow_catalog`。根据第 1 步的分析，从设备列表中按设备名称、类别或设备能力匹配候选设备；从已有流程列表中按名称或描述匹配可复用的已有流程。`list_workflow_catalog` 仅返回 ACTIVE 状态的已有流程。已经拿到列表后不要再整表列出。`deviceModelId`、`subFlowModelId` 只能来自工具返回值。
3. 对候选项做详细确认：
   - **3a.** 对初筛出的设备调用 `get_device_model`，根据设备能力、能力参数、设备属性、数据端口、状态机控制接口确认该设备满足需求。
   - **3b.** 对初筛出的已有流程调用 `get_workflow_model`，查看节点概览（包含的设备及设备能力、子流程引用）确认该流程覆盖所需操作。确认后可在工作流草稿中用 `SUBFLOW_NODE` 引用。
   - 目录里没有合适设备、且已有流程也不能覆盖用户需求时，停止并说明。
4. 按下方「工作流模型结构说明」编写工作流草稿，覆盖用户需求。描述含糊时按最合理解读，把解读写入 `metadata.description`。
5. 调用 `validate_workflow`。返回的 `issues` 数组中，每条 issue 含 `blocking`、`message`、`suggestion`、`repair` 字段。只根据返回的 issue 改稿；不要抄返回里没有的 definition。
6. 有 blocking 时按 `issues` 修改工作流草稿，需要重新查看设备或已有流程时可重新调用对应工具，然后再次调用 `validate_workflow`。重复直到校验无 blocking。反复 blocking 无法解决时停止，不调用 `save_draft`。
7. 无 blocking 之后调用 `simulate_workflow`。在内存编译并走图，不保存草稿。设备节点走真实状态机与进程内 Adapter 模拟器。返回 `walkable`、`pathTaken`、`paths`、`issues`。未走通时按 issues 改稿，从第 5 步再校验；`SIM_NO_ADAPTER_EVENT` 通常改稿走不通，停止并向人说明。不要把走图失败当成引擎该改。
8. 走图通过之后自行验收：流程是否满足用户需求，数据端口、条件中的节点变量、能力参数、子流程引用等细节是否到位。不到位就改稿再走第 5 步。
9. 验收通过后调用 `save_draft`。把含糊句的解读留在 `metadata.description`，交给人在设计器里修改。

# 工作流模型结构说明

该说明用于描述工作流模型的结构。它不是一份标准的样板流程，而是对每一部分的解释，供编写工作流草稿时对照。

约束：

1. 控制连线（`interfaceConnections`）与数据连线（`portConnections`）分离：控制连线表达谁激活谁、谁驱动设备；数据连线表达谁把节点变量的值交给谁。
2. 每个节点包含四项配置：业务配置、节点变量（`internalVariables`）、数据端口（`ports`）、控制接口（`interfaces`）。某项无内容时按 6.1 节该种类的组合规则省略字段或写空数组。
3. 节点变量必须先在 `internalVariables` 中声明，才能在触发器 `condition.object`、`expression` 赋值目标、数据端口 `internalVariableName` 中引用。系统标识（`nodeLifecycleState`、`taskLifecycleState`、`signalName`、`payload.stateName`、`aggregateCount`）除外——它们是引擎内置名，不在 `internalVariables` 中声明。`aggregateCount` 由引擎自动初始化为 0，仅在聚合节点（`AGGREGATE`）的触发器中可用。
4. 跨节点传递数据必须经数据端口和数据连线，不能通过控制连线传递，也不能直接引用其他节点的变量名。
5. 节点与物理动作严格一一对应（禁止冗余/占位节点）：流程中的每个 `DEV_NODE` 必须严格对应一次实际有物理意义的设备操作。严禁为了“获取设备属性”、“读取数据”、“传递变量”或“连接数据端口”，人为编造“持续时间为0”、“0秒加热”、“目标参数不变的假动作”等无实际物理意义的占位节点（Dummy Node）。当分支节点（`BRANCH`）需要依据设备属性做判断时，必须就近复用前置已执行物理动作的真实 `DEV_NODE`，在其上配置 `attributesMapping` 并经数据端口引出。

---

## 1. 流程总结构

工作流草稿由四段组成：

| 段 | 作用 |
|---|---|
| `metadata` | 流程名称与说明 |
| `nodes` | 节点列表 |
| `interfaceConnections` | 控制连线 |
| `portConnections` | 数据连线 |

```json
{
  "metadata": {
    "flowModelId": null,
    "flowModelName": "名称",
    "description": "说明"
  },
  "nodes": [],
  "interfaceConnections": [],
  "portConnections": []
}
```

新建时 `flowModelId` 为 `null`。`flowModelName` 必填。`nodes`、`interfaceConnections`、`portConnections` 必须为数组；`nodes` 非空；节点 `name` 在同一流程内唯一。

---

## 2. 拓扑约束

以下入边、出边仅计入 `NODE_TO_NODE` 类型的控制连线。`NODE_TO_DEVICE`、`DEVICE_TO_NODE` 不计入。

1. 有且仅有一个 `nodeType=FUNC_NODE` 且 `functionType=START` 的节点。
2. 有且仅有一个 `nodeType=FUNC_NODE` 且 `functionType=END` 的节点。
3. START 无 `NODE_TO_NODE` 入边；END 无 `NODE_TO_NODE` 出边。
4. 其余每个节点均须同时具有 `NODE_TO_NODE` 入边与出边。
5. 控制连线不得成环；从 START 沿 `NODE_TO_NODE` 可达每一节点，每一节点可达 END。
6. 每个 `DEV_NODE` 须恰好各有一条 `NODE_TO_DEVICE` 与一条 `DEVICE_TO_NODE`。

---

## 3. 控制连线

每条控制连线须含 `connectionType`、`source`、`target`。禁止使用已废弃的 `fromNodeId`、`toNodeId` 字段，使用 `source`/`target` + `nodeName` + `interfaceName`。`interfaceName` 取第 5 节该种类的控制接口名。

**NODE_TO_NODE**（节点→节点）：

```json
{
  "connectionType": "NODE_TO_NODE",
  "source": { "nodeName": "<上游节点 name>", "interfaceName": "<上游 WORKFLOW + OUT 类型控制接口名>" },
  "target": { "nodeName": "<下游节点 name>", "interfaceName": "<下游 WORKFLOW + IN 类型控制接口名>" }
}
```

**NODE_TO_DEVICE**（节点→设备）：

```json
{
  "connectionType": "NODE_TO_DEVICE",
  "source": { "nodeName": "<DEV_NODE name>", "interfaceName": "Interface_state_out" },
  "target": { "deviceModelId": "<与该 DEV_NODE.deviceModelId 相同>", "interfaceName": "<get_device_model 中接收控制的状态机接口名>" }
}
```

**DEVICE_TO_NODE**（设备→节点）：

```json
{
  "connectionType": "DEVICE_TO_NODE",
  "source": { "deviceModelId": "<与该 DEV_NODE.deviceModelId 相同>", "interfaceName": "<get_device_model 中推送状态的状态机接口名>" },
  "target": { "nodeName": "<DEV_NODE name>", "interfaceName": "Interface_state_in" }
}
```

---

## 4. 数据连线

无数据传递时 `portConnections` 为 `[]`，各节点 `ports` 为 `[]`。

```json
{
  "source": { "nodeName": "<源节点 name>", "portName": "<OUT 数据端口 name>" },
  "target": { "nodeName": "<目标节点 name>", "portName": "<IN 数据端口 name>" }
}
```

`source` 为 OUT 数据端口，`target` 为 IN 数据端口。两端绑定的节点变量的 `dataType` 必须相同。每个 IN 数据端口至多一条数据连线。

---

## 5. 节点种类

`nodeType` 取值：`FUNC_NODE`、`DEV_NODE`、`SUBFLOW_NODE`。
`functionType` 取值：`START`、`END`、`BRANCH`、`AGGREGATE`，仅 `FUNC_NODE` 拥有此字段。

控制连线的 `interfaceName` 按下表。未列出的控制接口名不得用于该种类的连线。

| 种类 | NODE_TO_NODE 入 | NODE_TO_NODE 出 | 设备连线控制接口 |
|---|---|---|---|
| START | 无 | `Interface_workflow_out` | 无 |
| END | `Interface_workflow_in` | 无 | 无 |
| BRANCH | `Interface_workflow_in` | 各出口控制接口的 `name`（见 6.5 节） | 无 |
| AGGREGATE | 第一条为 `Interface_workflow_in`；其余为额外入口控制接口的 `name`（见 6.5 节） | `Interface_workflow_out` | 无 |
| DEV_NODE | `Interface_workflow_in` | `Interface_workflow_out` | `NODE_TO_DEVICE` 用 `Interface_state_out`；`DEVICE_TO_NODE` 用 `Interface_state_in` |
| SUBFLOW_NODE | `Interface_workflow_in` | `Interface_workflow_out` | 无 |

### 5.1 设备节点

用 `DEV_NODE` 表达一次设备能力执行。`deviceModelId` 与 `capability` 必须来自第 2、3 步工具返回值。需要把设备属性交给后续节点时，在本节点声明节点变量并设置 `attributesMapping`，再经数据端口送出。

**禁止占位节点与数据读取复用规约**：
- 每一个 `DEV_NODE` 都会在物理硬件上真实下发指令。严禁为了传递属性或连接数据线而随意插入“持续时间为0”、“0秒加热/加压”、“参数重复无动作”的占位设备节点。
- 当后续分支（`BRANCH`）需要依据设备属性（如温度、压力）做判断时，直接在产生或到达该状态的前置真实 `DEV_NODE`（如刚刚完成加热30秒到300℃的节点）上声明节点变量并配置 `attributesMapping`，通过 OUT 数据端口直接连接到 `BRANCH` 的 IN 数据端口。严禁在两者之间插入中介设备节点。

### 5.2 功能节点（开始、结束、分支、聚合）

均为 `nodeType=FUNC_NODE`，用 `functionType` 区分。

- START：流程唯一入口。
- END：流程唯一出口。
- BRANCH：按本节点的节点变量把控制流分成多条互斥或可区分的出口。
- AGGREGATE：把多条控制入边汇成一条出边。汇聚门槛等于会到达的控制入边数；互斥分支汇合时汇聚门槛为 1。

### 5.3 子流程节点

用 `SUBFLOW_NODE` 引用 `list_workflow_catalog` 中的已有流程。`subFlowModelId` 只能取列表里的 `flowModelId`。引用前先调用 `get_workflow_model` 查看该流程的节点概览，确认它覆盖了所需操作。列表里没有对应流程就不要写该种类，改用设备节点和功能节点直接编写。不要把子流程内部的节点抄进本稿。不要引用正在编写的这份工作流草稿。

---

## 6. 节点结构

每个节点必有 `name`、`nodeType`、`ports`。其余按种类组合。

### 6.1 各种类配置组合

| 种类 | 业务配置 | 节点变量 `internalVariables` | 数据端口 `ports` | 节点 JSON 中的控制接口 `interfaces` |
|---|---|---|---|---|
| START | 无 | 不写 | `[]` | 不写 |
| END | 无 | 不写 | `[]` | 不写 |
| BRANCH | 可选 `expression` | 判断所用变量必须在本节点声明 | 值来自其他节点时增加 IN 数据端口；否则 `[]` | 必须写：至少 2 个 OUT，每个含 6.6.4 节 BRANCH 出口默认触发器 |
| AGGREGATE | 可选 `expression` | 禁止声明名为 `aggregateCount` 的节点变量 | 无数据传递时为 `[]` | 必须写：额外 IN（6.6.4 节聚合入口默认触发器）+ `Interface_workflow_out`（6.6.4 节聚合出口默认触发器） |
| DEV_NODE | `deviceModelId`、`capability` | 需要向外传递设备属性时声明节点变量并填写 `attributesMapping`；否则不写 | 有向外传递时增加 OUT 数据端口；否则 `[]` | 不写 |
| SUBFLOW_NODE | `subFlowModelId` | 不写 | `[]` | 不写 |

> 6.1 表格中"不写"表示节点 JSON 中省略该字段；系统默认控制接口（如 `Interface_workflow_in`、`Interface_workflow_out`、`Interface_state_in`、`Interface_state_out`）由后端补全，不需要写入节点 JSON。

### 6.2 业务配置

| 种类 | 字段 |
|---|---|
| START、END | 无额外字段 |
| BRANCH、AGGREGATE | `expression` 可选。格式：`目标变量 = 计算式`。目标必须已在本节点 `internalVariables` 中声明。无需赋值则不写 |
| DEV_NODE | `deviceModelId`（仅来自工具返回值）、`capability` |
| SUBFLOW_NODE | `subFlowModelId`（仅来自 `list_workflow_catalog` 的 `flowModelId`）。可选 `subFlowModelDescription`，取列表中的 `description` |

`capability` 结构：

```json
{
  "capabilityName": "<与 capabilities[].capabilityName 一致>",
  "capabilityParameters": { "<参数名>": "<值>" }
}
```

能力参数键来自该设备能力定义。禁止写 `capability.parameters`。禁止在节点顶层写 `capabilityName`。

### 6.3 节点变量

```json
{
  "name": "<节点内唯一>",
  "dataType": "INTEGER | DOUBLE | BOOLEAN | STRING",
  "initialValue": "<可选，类型与 dataType 一致>",
  "attributesMapping": "<可选，仅 DEV_NODE，值为 get_device_model 返回的设备属性名>"
}
```

- 触发器 `condition.object`、数据端口 `internalVariableName`、`expression` 赋值目标，除系统标识外，必须等于本节点某个 `internalVariables[].name`。
- 设备属性名不得直接作为 `condition.object`。在 `DEV_NODE` 用 `attributesMapping` 映射为节点变量；经数据连线传递后，接收节点用本节点的变量名判断。
- 仅 `DEV_NODE` 可写 `attributesMapping`。
- 禁止在 `internalVariables` 中声明名为 `aggregateCount` 的项。

### 6.4 数据端口

数据端口绑定的节点变量必须已在 `internalVariables` 中声明。

```json
{
  "name": "<数据端口名>",
  "direction": "IN | OUT",
  "internalVariableName": "<本节点 internalVariables[].name>"
}
```

`OUT` 输出绑定节点变量的当前值。`IN` 将接收值写入绑定的节点变量。

### 6.5 控制接口

仅 BRANCH 和 AGGREGATE 需要在节点 JSON 中书写控制接口。其余种类的系统默认控制接口由后端补全，节点 JSON 中不含 `interfaces`。

每个控制接口的字段结构：

```json
{
  "name": "<节点内唯一>",
  "direction": "IN | OUT",
  "interfaceType": "WORKFLOW",
  "allowedSignals": ["ACTIVE"],
  "bindingTriggers": [ /* 见 6.6 节 */ ]
}
```

固定规则：

- `interfaceType` 必须为 `WORKFLOW`
- `allowedSignals` 必须为 `["ACTIVE"]`
- `bindingTriggers` 至少一项，结构见 6.6 节
- EMIT 动作的 `signalName` 必须为 `ACTIVE`
- IN 控制接口用 `signalName` 判断时，`threshold` 必须为 `ACTIVE`

**BRANCH 控制接口命名与结构**：

| 控制接口 | `direction` | `name` | `bindingTriggers` |
|---|---|---|---|
| 出口 1 | OUT | `Interface_workflow_out_1` | 6.6.4 节 BRANCH 出口默认触发器 |
| 出口 2 | OUT | `Interface_workflow_out_2` | 6.6.4 节 BRANCH 出口默认触发器 |
| 出口 N | OUT | `Interface_workflow_out_N`（递增） | 6.6.4 节 BRANCH 出口默认触发器 |

至少 2 个 OUT。每个出口的默认触发器需修改 `condition` 以区分路径（见 6.6.4）。

**AGGREGATE 控制接口命名与结构**：

| 控制接口 | `direction` | `name` | `bindingTriggers` |
|---|---|---|---|
| 出口 | OUT | `Interface_workflow_out` | 6.6.4 节聚合出口默认触发器 |
| 额外入口 1 | IN | `Interface_workflow_in_1` | 6.6.4 节聚合入口默认触发器 |
| 额外入口 N | IN | `Interface_workflow_in_N`（递增） | 6.6.4 节聚合入口默认触发器 |

第一条控制入边使用系统默认的 `Interface_workflow_in`（不写入节点 JSON）。从第二条入边起，每条写入一个 IN 控制接口。出口必须写入 `Interface_workflow_out`。

### 6.6 触发器

位于 `interfaces[].bindingTriggers`。同一控制接口上各触发器独立求值。`condition` 从不成立变为成立时执行一次动作。多个条件须同时成立时使用 AND。

#### 6.6.1 字段

```json
{
  "condition": {},
  "action": {},
  "actions": []
}
```

> **`action` 与 `actions` 的关系**：`condition` 必填。`action` 与 `actions` 至少写其一。`action` 是单一动作，`actions` 是按顺序执行的动作列表。存在 `actions` 时，`action` 必须等于 `actions[0]`。单个动作时可只写 `action`；多个动作时必须写 `actions` 且 `action` 等于 `actions[0]`。

#### 6.6.2 条件

单个判断：

```json
{
  "object": "<标识>",
  "operator": "<运算符>",
  "threshold": "<阈值>"
}
```

AND 组合：`logic` 必须为 `AND`。`conditions` 仅含单个判断，禁止嵌套 AND。

```json
{
  "logic": "AND",
  "conditions": []
}
```

`object` 取值：

| `object` | 用途 | `threshold` |
|---|---|---|
| `nodeLifecycleState`（系统标识） | 本节点生命周期状态 | `PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED`、`TERMINATING`、`TERMINATED` |
| `signalName`（系统标识） | 本 IN 控制接口收到的信号 | `ACTIVE` |
| 本节点 `internalVariables[].name` | 按节点变量判断 | 与该变量 `dataType` 兼容 |
| `aggregateCount`（系统标识） | 聚合节点已到达的控制入边计数 | 整数，不得大于该节点 `NODE_TO_NODE` 入边数 |

`operator`：`>`、`<`、`>=`、`<=`、`=`、`!=`、`BETWEEN`、`IN`。
`BETWEEN` 的 `threshold` 为含两个元素的数组。`IN` 的 `threshold` 为数组。状态名与 `ACTIVE` 使用 `=`。

#### 6.6.3 动作

**EMIT 动作**（通过控制接口发送信号）：

```json
{
  "actionName": "EMIT",
  "payload": {
    "targetInterfaceName": "<本节点 OUT 控制接口的 name>",
    "signalName": "ACTIVE"
  }
}
```

**UPDATE 动作——更新节点变量**：

```json
{
  "actionName": "UPDATE",
  "payload": {
    "updateType": "INTERNAL_VARIABLE",
    "targetName": "<本节点变量名 或 aggregateCount>",
    "valueExpression": "<表达式>"
  }
}
```

常量写入使用 `value` 字段，不得与 `valueExpression` 同时出现。

**UPDATE 动作——更新生命周期状态**：

```json
{
  "actionName": "UPDATE",
  "payload": {
    "updateType": "NODE_LIFECYCLE",
    "targetName": "<目标状态名>"
  }
}
```

#### 6.6.4 默认触发器

新增控制接口时必须写入下列对应结构。修改分支条件时只改 `condition`，不得删除 `actions` 中的 UPDATE 与 EMIT。

**BRANCH 出口**（`direction=OUT`）。将 `<出口名>` 换为该控制接口的 `name`。

```json
{
  "condition": {
    "object": "nodeLifecycleState",
    "operator": "=",
    "threshold": "RUNNING"
  },
  "action": {
    "actionName": "UPDATE",
    "payload": { "updateType": "NODE_LIFECYCLE", "targetName": "SUCCEEDED" }
  },
  "actions": [
    {
      "actionName": "UPDATE",
      "payload": { "updateType": "NODE_LIFECYCLE", "targetName": "SUCCEEDED" }
    },
    {
      "actionName": "EMIT",
      "payload": { "targetInterfaceName": "<出口名>", "signalName": "ACTIVE" }
    }
  ]
}
```

区分多条出口：将 `condition` 改为 AND，保留 `nodeLifecycleState = RUNNING`，并追加对本节点变量的判断。各出口条件须互斥或可区分路径。

**AGGREGATE 出口**（控制接口 `name` 必须为 `Interface_workflow_out`）。动作结构与 BRANCH 出口相同，`condition` 固定为：

```json
{
  "logic": "AND",
  "conditions": [
    { "object": "aggregateCount", "operator": ">=", "threshold": "<NODE_TO_NODE 入边数>" },
    { "object": "nodeLifecycleState", "operator": "=", "threshold": "RUNNING" }
  ]
}
```

`threshold` 等于该聚合节点实际控制入边数（汇聚门槛）。EMIT 动作的 `targetInterfaceName` 为 `Interface_workflow_out`。

**AGGREGATE 额外入口**（`direction=IN`）：

```json
{
  "condition": {
    "object": "signalName",
    "operator": "=",
    "threshold": "ACTIVE"
  },
  "action": {
    "actionName": "UPDATE",
    "payload": {
      "updateType": "INTERNAL_VARIABLE",
      "targetName": "aggregateCount",
      "valueExpression": "aggregateCount + 1"
    }
  }
}
```
