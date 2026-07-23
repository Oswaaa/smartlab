# 系统信号契约重构设计

## 目标

统一 protocol、设备能力模型、设备状态机、工作流引擎、Adapter 消息链路和前端所使用的信号名称与语义；不修改数据库，不兼容旧信号。

## 设计边界

- 工作流模型的 `interfaceConnections` 和任务资源映射负责路由，信号不重复携带 source、target、节点或接口信息。
- 系统信号格式固定为 `signalName` 加可选 `payload`。
- 不使用 JSON Schema `oneOf`，不构建通用 payload 规则引擎。
- 协议文件声明信号名称；后端代码实现少量固定信号的 payload 语义。
- `WF_EXECUTE_START`、`MANUAL_EXECUTE_START` 和 `CMD_START` 必须携带 `commandName`、`parameters`。
- `WF_EXECUTE_ABORT`、`MANUAL_EXECUTE_ABORT`、`CONSTRAINT_ABORT` 和 `CMD_ABORT` 不要求 payload，执行层根据当前任务步骤或设备当前活动指令停止动作。
- 状态输出 `CMD_STATE`、`OP_STATE` 的状态值放入 payload；路由元数据保留在 Java 执行事件中，不写入信号。
- `COMPLETED`、`FAILED`、`ABORTED` 先对外发布终态，再自动回到 `IDLE`；不存在 RESET 信号。

## 模型职责

- `protocol-dict.json`：数据类型、通信协议、固定系统信号名、MQTT topic 与消息格式。
- Adapter 原始配置：由 `samples/adapterSetup.json` 和 `samples/adapterSetup.ini` 说明，Adapter 开发者负责提供。
- `ADAPTER_INDEX.PARSED_CONFIG`：后端解析器生成的内部归一化记录，其结构由 `com.smartlab.adapter` 代码维护，不新增 schema。
- `device-capability-model.adapterContract`：设备模型从 `PARSED_CONFIG` 选择一个类别后形成的契约切片。
- `device-state-machine-model.Interface_adapter_in.allowedSignals`：只来自当前 `adapterContract.events`。
- Adapter 事件到指令/运行状态的映射：由设备状态机 `transitions` 明确配置，不根据名称或 description 猜测。

## 运行时结构

系统信号：

```json
{
  "signalName": "WF_EXECUTE_START",
  "payload": {
    "commandName": "heat",
    "parameters": { "targetTemperature": 80 }
  }
}
```

路由和持久化关联由 Java 执行上下文承担，例如任务 ID、步骤 ID、设备实例 ID、接口名和 Adapter messageId。它们不能被复制进信号 payload 成为第二数据源。

## 固定控制语义

| 来源 | 开始 | 中止 |
|---|---|---|
| 工作流 | `WF_EXECUTE_START` | `WF_EXECUTE_ABORT` |
| 人工控制 | `MANUAL_EXECUTE_START` | `MANUAL_EXECUTE_ABORT` |
| 约束引擎 | 无 | `CONSTRAINT_ABORT` |
| 状态机到 Adapter | `CMD_START` | `CMD_ABORT` |

开始信号由代码校验 payload；中止信号忽略空 payload，并从当前执行上下文解析活动指令。

## Adapter 事件

删除全局 `AdapterCommandLifecycleEvent`。Adapter 的 `cmdEvents` 和 `opEvents` 从原始配置进入 `PARSED_CONFIG`，再进入设备能力模型 `adapterContract.events`，最终成为 `Interface_adapter_in.allowedSignals`。指令事件是否要求 `messageId` 由其属于 `cmdEvents` 这一事实判断。

## 工作流生命周期

删除 protocol 中的 `ExecutionLifecycleEvent`。节点生命周期状态属于 `workflow-model.json` 和执行代码，不是跨模块通信协议。前端从工作流模型元数据读取，不从 protocol 读取。

## 验证

- protocol 元数据测试验证只暴露新信号。
- 状态机接口策略测试验证 Adapter 输入仅包含 `adapterContract.events`。
- 设备模型测试验证不生成旧信号或硬编码 `COMMAND_*` 转移。
- 状态机测试验证开始 payload、无 payload 中止和终态自动回到 `IDLE`。
- MQTT 测试分别验证 `CMD_START` 与 `CMD_ABORT`，防止中止被误发成普通命令。
- 后端完整测试和前端生产构建作为最终回归门禁。
