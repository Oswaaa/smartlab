# PLC 寄存器 MQTT Adapter 设计规格

## 目标

修改 `adapter/testAdapter`，使 Adapter 通过同一个 MQTT Broker 与 SmartLab 系统和单台 PLC 通信。Adapter 订阅 PLC 的全寄存器快照，将寄存器转换成系统属性；同时把系统语义化命令转换成 PLC 要求的寄存器写入数组。

## MQTT 配置

`setup.ini` 是 Adapter 本地配置，不发送给系统。

- Broker：`127.0.0.1:1883`
- 用户名：`adapter`
- 密码：`123456`
- Client ID：`adapter_test_client`
- PLC 遥测订阅 Topic：`plc/data`
- PLC 命令发布 Topic：`plc/MQTTCommand`
- 系统 Topic 使用 `protocol-dict.json` 当前定义的注册、心跳、命令、遥测和事件 Topic 模板。
- 增加 `commandTimeoutSec`，用于等待 PLC 寄存器确认命令结果。

系统和 PLC 共用一个 Paho MQTT 客户端连接。

## PLC 遥测输入

PLC 在 `plc/data` 发布：

```json
[
  {
    "Cache": false,
    "DeviceSN": "plc0001",
    "TagData": [
      {
        "Time": "2004-01-01T17:16:11.00000Z",
        "MW0": 0,
        "MW20": 0,
        "MW21": 0,
        "MW22": 0
      }
    ]
  }
]
```

处理规则：

1. 顶层必须是数组。
2. 选择 `DeviceSN` 等于设备点配置 `deviceSN=plc0001` 的元素。
3. `TagData` 必须是非空数组，使用最后一条记录作为最新快照。
4. PLC 的 `Time` 不作为系统时间，因为当前值不可靠；系统遥测 `timestamp` 使用 Adapter 接收时间。
5. 其他 PLC 或格式错误的快照不会转发到系统。

## 系统属性

`adapterconfig.ini` 中定义四个属性：

| 系统属性 | PLC 寄存器 | 转换 |
|---|---|---|
| `temperature` | `MW0` | WORD 数值直接作为 DOUBLE 上报 |
| `coolingEnabled` | `MW20` | `(value & 1) == 1` |
| `automaticMode` | `MW21` | `(value & 1) == 1` |
| `manualMode` | `MW22` | `(value & 1) == 1` |

每次合法 PLC 快照生成一条符合 `TelemetryMessageFormat` 的系统遥测消息。

## 系统命令与 PLC 输出

### 设置运行模式

系统命令：

```json
{
  "commandName": "setOperatingMode",
  "parameters": { "mode": "MANUAL" }
}
```

`mode` 只允许 `AUTO` 或 `MANUAL`。

自动模式发布：

```json
[{"DeviceSN":"plc0001","TagData":[{"MW21":1,"MW22":0}]}]
```

手动模式发布：

```json
[{"DeviceSN":"plc0001","TagData":[{"MW21":0,"MW22":1}]}]
```

两个模式寄存器在同一条 PLC 消息中写入，避免中间状态。

### 设置散热

系统命令：

```json
{
  "commandName": "setCooling",
  "parameters": { "enabled": true }
}
```

开启散热发布：

```json
[{"DeviceSN":"plc0001","TagData":[{"MW20":1}]}]
```

关闭散热发布：

```json
[{"DeviceSN":"plc0001","TagData":[{"MW20":0}]}]
```

安全规则：

- `enabled=true` 时，Adapter 必须已经收到 PLC 快照，且 `MW22 bit 0 = 1`；否则拒绝命令并向系统上报 `COMMAND_FAILED`。
- Adapter 不会为了开启散热而自动切换运行模式。
- `enabled=false` 允许在任何模式下发送，作为安全停止操作。

## 指令生命周期

PLC 没有独立事件 Topic，因此 Adapter 根据消息投递与寄存器反馈生成 CMD 事件：

1. 系统命令校验通过：`COMMAND_RECEIVED`。
2. PLC 命令成功交给 MQTT 客户端：`COMMAND_RUNNING`。
3. 后续 `plc/data` 满足目标寄存器值：`COMMAND_COMPLETED`。
4. 命令校验失败、发布失败或等待超过 `commandTimeoutSec`：`COMMAND_FAILED`。
5. 系统发送 `ABORT` 时，Adapter 取消本地等待并上报 `COMMAND_ABORTED`；PLC 当前没有中止寄存器，因此不发送不存在的 PLC 中止命令。

Adapter 使用系统 `messageId` 维护待确认命令。多个命令可同时等待，只要 `messageId` 不重复。

## 功能事件

Adapter 比较连续两次 PLC 快照，在状态发生变化时发布：

- `AUTOMATIC_MODE_ENTERED`
- `MANUAL_MODE_ENTERED`
- `COOLING_STARTED`
- `COOLING_STOPPED`

首次快照只建立基线并上报遥测，不产生状态变化事件。

## Adapter 契约调整

删除没有寄存器支撑的 `configureHeating(targetTemperature, durationSec)` 和原 `setPower` 命令，替换为：

- `setOperatingMode(mode)`
- `setCooling(enabled)`

设备点保留在 `adapterconfig.ini`：

```ini
[devicePoints.PLC1]
templateName = PLCThermalTemplate
deviceSN = plc0001
```

`deviceSN` 是 Adapter 内部参数来源，不显示在系统设备能力模型中。

## 错误处理与验证

- PLC JSON 结构、DeviceSN、TagData、寄存器类型不合法时记录错误且不转发。
- 不记录 MQTT 密码和完整认证配置。
- 配置解析、PLC 快照转换、模式写入、手动模式保护、寄存器确认、超时、功能事件和 MQTT 路由均使用单元测试覆盖。
- 使用现有 Java `AdapterManifestService` 验证新版 `adapterconfig.ini`。
