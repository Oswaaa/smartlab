# VIOLATION_LOG 字段与格式对齐设计

## 目标

将全局约束和任务约束写入 `VIOLATION_LOG` 的后端实现对齐到已经调整完成的数据库结构，并修正各 JSONB 字段的运行时语义。

本次仅修改约束引擎的违规日志写入，不改变约束求值、违规动作、窗口触发和去重语义。

## 范围边界

### 包含

- 全局约束的违规日志。
- 任务约束的违规日志。
- `ViolationLog` Entity 与数据库新字段的映射。
- `ConstraintEngine.writeViolationLog()` 中的字段构造。
- 为完整变量空间快照提供只读采集能力。
- 后端接口序列化和回归测试。

### 不包含

- 设备模型 `intrinsicConstraints`。
- `IntrinsicConstraintMonitor`、`IntrinsicConstraintPlanRegistry` 和 `StateMachineEngine`。
- `DEVICE_TWIN_STATES.current_op_state` 的异常锁存与解除逻辑。
- 新增设备内置约束历史记录。
- 新增跨引擎审计服务或 `ViolationLogFactory`。
- 修改约束表达式求值、窗口时间、上升沿去重和违规动作执行方式。
- 迁移或回填已有 `VIOLATION_LOG` 历史数据。

设备内置约束仍由状态机负责检测并锁存异常状态。全局约束或任务约束如需对设备异常采取措施，应通过 `DEVICE_OPERATION_STATE` 观测对象自行判断；只有全局或任务约束命中时才写 `VIOLATION_LOG`。

## 数据库现状

数据库 `VIOLATION_LOG` 已调整为以下相关字段：

| 数据库列 | PostgreSQL 类型 | Java 字段 |
|---|---|---|
| `observed_variable` | `jsonb` | `JsonNode observedVariable` |
| `expression` | `jsonb` | `JsonNode expression` |
| `actual_value` | `jsonb` | `JsonNode actualValue` |
| `variable_snapshot` | `jsonb` | `JsonNode variableSnapshot` |

旧字段 `expected_condition` 不再存在。后端 Entity 必须删除 `expectedCondition` 映射，否则写入时会继续访问不存在的数据库列。

## 字段契约

### OBSERVED_VARIABLE

保存当前规则中所有 `bindingType=OBSERVABLE` 的绑定定义，键为表达式变量名，值使用有效约束模型中的规范化绑定格式：

```json
{
  "temperature": {
    "bindingType": "OBSERVABLE",
    "observableName": "global_12_temperature"
  }
}
```

规则如下：

- 不保存 `LITERAL` 绑定。
- 保留多个用于同一次比较的可观测变量，例如 `temperature1 > temperature2`。
- 不把规则名称写入该字段。
- `observableName` 与同一次有效约束模型编译产生的 `observableObjects[].name` 一致。
- 字段顺序按原始 `bindings` 声明顺序保持稳定。

### EXPRESSION

保存约束模型中的原始违规表达式，使用 JSONB 字符串值：

```json
"temperature > limit"
```

表达式结果为 `true` 时表示违规，与约束模型现有语义一致。不再保存冗余的 `{ "expression": "..." }` 包装对象。

### ACTUAL_VALUE

保存本次表达式求值使用的完整已解析绑定值，包括 `OBSERVABLE` 和 `LITERAL`：

```json
{
  "temperature": 215.7,
  "limit": 200
}
```

对于两个观测对象之间的比较：

```json
{
  "temperature1": 215.7,
  "temperature2": 210.3
}
```

字段始终使用 JSON 对象，不在单变量时退化为标量，保证 API 结构稳定。

### VARIABLE_SNAPSHOT

保存违规发生时与该运行时约束实例相关的完整作用域快照，而不局限于表达式中直接引用的绑定值。

统一结构为：

```json
{
  "tasks": {
    "7": {
      "taskStatus": "RUNNING",
      "taskVariables": {},
      "steps": {
        "31": {
          "nodeStatus": "RUNNING",
          "variableSpace": {}
        }
      }
    }
  },
  "devices": {
    "18": {
      "attributes": {},
      "operationState": {},
      "commandLifecycle": "IDLE"
    }
  }
}
```

采集规则：

- 若运行时约束具有 `taskId`，保存该任务的任务状态、任务变量，以及该任务全部 `TASK_STEP` 的节点状态和完整 `VARIABLE_SPACE`。
- 保存规则中所有可观测绑定涉及的设备实例。
- 保存违规动作明确引用的设备实例。
- 对每个相关设备保存 `DEVICE_TWIN_STATES` 中的完整属性、运行状态和指令生命周期。
- 多设备比较时保存所有相关设备。
- 没有任务或设备作用域时保留空的 `tasks` 和 `devices` 对象。
- 快照采集失败不得阻止违规动作和主日志写入；失败的局部作用域写入带有 `snapshotError` 的对象，其余可用内容继续保存。

快照采集只读取当前持久化/内存运行状态，不修改工作流、状态机或设备孪生状态。

## 代码设计

### 原地修改日志构造

保留 `ConstraintEngine.writeViolationLog()` 作为唯一写入入口，不新增 `ViolationLogFactory`。

字段构造继续在 `ConstraintEngine` 内完成。为控制方法长度，可以新增同类私有方法：

- `observedVariables(RuntimeConstraint constraint)`
- `actualValues(Map<String, JsonNode> values)`
- `variableSnapshot(RuntimeConstraint constraint, JsonNode action)`

这些方法只服务于当前类，不形成新的业务抽象。

### 运行时携带规范化观测绑定

`EffectiveConstraintModelCompiler` 已经在编译有效约束模型时生成 `observableName`。编译结果应把每条规则的规范化可观测绑定同时携带到 `RuntimeConstraint`，供日志写入直接使用，避免 `ConstraintEngine` 再次发明命名规则。

### 快照读取边界

新增约束模块内部的 `ConstraintScopeSnapshotReader`，集中只读访问任务、任务步骤和设备孪生现有数据源。它接收当前 `RuntimeConstraint` 和违规动作，返回 `VARIABLE_SNAPSHOT` 所需的 `tasks`、`devices` JSON 对象。

该读取器不构造或保存 `ViolationLog`，不执行约束动作，也不被状态机调用。设置这一边界是为了避免把 `TaskMapper`、`TaskStepMapper` 和 `DeviceTwinStatesMapper` 的查询细节直接堆入 `ConstraintEngine`；违规日志其余字段仍在原有 `writeViolationLog()` 中拼装。

### 写入顺序

维持当前行为：每个违规动作执行后写入对应日志行，`ACTION_TAKEN` 记录该动作的实际结果。一个规则配置多个违规动作时仍产生多行日志，不在本次修改中改变事件粒度。

## 错误处理

- 表达式求值失败：保持现有逻辑，不执行动作、不写违规日志。
- 违规动作失败：保持现有逻辑，仍写日志，并在 `ACTION_TAKEN` 中记录 `FAILED:...`。
- 变量快照局部读取失败：不回滚已经执行的违规动作；保存可用快照及局部错误信息。
- `ViolationLogService.save()` 失败：保持当前异常传播和日志策略，不引入静默吞错。

## 测试设计

采用 TDD，至少覆盖：

1. `ViolationLog` 使用四个 JSONB 新字段，并且不存在 `expectedCondition`。
2. 单观测值与字面量规则：
   - `OBSERVED_VARIABLE` 仅包含可观测绑定；
   - `EXPRESSION` 为 JSON 字符串；
   - `ACTUAL_VALUE` 同时包含观测值和字面量。
3. 两个观测对象比较：两个规范化绑定和两个实际值都被保存。
4. 任务约束：保留 `TASK_CONSTRAINT` 类型、任务 ID 和完整任务步骤快照。
5. 全局设备约束：保留全局规则 ID、设备 ID 和完整设备孪生快照。
6. 多违规动作：继续每个动作产生一行日志。
7. 快照局部失败：违规日志仍写入，快照中记录局部错误。
8. 设备内置约束测试不出现 `ViolationLogService` 依赖，证明状态机边界未被改变。

## 验收标准

- 后端能够在新数据库列结构下正常写入全局和任务违规日志。
- `OBSERVED_VARIABLE` 不再出现规则名称。
- API 返回字段名为 `observedVariable`、`expression`、`actualValue`、`variableSnapshot`。
- 四个字段的 JSON 结构符合本设计。
- 设备内置约束链路无代码和依赖变化。
- 约束引擎、状态机和后端全量测试通过。
