# MQTT PLC 测试 Adapter 设计规格

## 目标

在 `adapter/testAdapter` 中实现一个 Python Adapter。它作为 SmartLab 系统与单台 PLC 之间的 MQTT 中间件，连接同一个 Broker，并完成 Adapter 注册、心跳、系统命令转发、PLC 遥测上报和 PLC 事件上报。

## 配置边界

### `adapterconfig.ini`

这是发送给 SmartLab 系统的原始 Adapter 契约，严格采用现有 INI 规范，包含：

- Adapter 名称、说明和规范版本；
- 一个 PLC 设备模板；
- `temperature` 和 `powerState` 属性；
- `configureHeating(targetTemperature, durationSec)` 命令；
- `setPower(enabled)` 命令；
- CMD 事件和 OP 事件；
- 一个实际 PLC 设备点；
- 属性物理字段映射。

该文件不包含 Broker 地址、密码、Topic 或运行参数。程序注册时读取完整原文，并作为 `AdapterRegisterRequest.rawConfigContent` 发送给系统。

### `setup.ini`

这是 Adapter 自身运行配置，不发送给系统。包含：

- Broker 地址、端口、用户名、密码、Client ID、keepalive；
- QoS、心跳周期和日志级别；
- 系统注册、心跳、命令订阅、遥测发布、事件发布 Topic 模板；
- PLC 命令发布、遥测订阅、事件订阅 Topic 模板。

所有 Topic 均可配置，程序不写死最终 Topic。Topic 模板支持 `{adapterName}` 和 `{devicePoint}` 占位符。系统和 PLC 使用同一个 MQTT 客户端连接同一 Broker。

## 消息流

### 启动与注册

1. 加载并校验两个 INI 文件。
2. 建立 MQTT 连接。
3. 订阅系统命令 Topic、PLC 遥测 Topic、PLC 事件 Topic。
4. 按 `AdapterRegisterRequest` 发布完整 `adapterconfig.ini`。
5. 周期发布 `AdapterHeartbeat`。

### 系统命令到 PLC

1. 根据接收 Topic 和消息体确定 `devicePoint`。
2. 校验 `messageId`、`adapterName`、`devicePoint`、`commandName`、`parameters` 和 `timestamp`。
3. 根据 `adapterconfig.ini` 校验命令与公开参数。
4. 根据 `internal=true/sourceField` 从设备点配置注入 Adapter 内部参数。
5. 向系统发布 `COMMAND_RECEIVED`，并保存 `messageId` 关联记录。
6. 将命令以 PLC JSON 消息发布到 PLC 命令 Topic。

PLC 命令消息格式：

```json
{
  "messageId": "系统命令ID",
  "devicePoint": "PLC1",
  "commandName": "configureHeating",
  "parameters": {
    "targetTemperature": 80.0,
    "durationSec": 120
  },
  "timestamp": 1719892800000
}
```

### PLC 遥测到系统

PLC 遥测消息采用：

```json
{
  "devicePoint": "PLC1",
  "timestamp": 1719892800000,
  "data": {
    "PLC_TEMPERATURE": 25.6,
    "PLC_POWER_STATE": true
  }
}
```

Adapter 使用 `attributeMapping` 把 PLC 物理字段转换成模型属性，随后按 `TelemetryMessageFormat` 发布到系统。

### PLC 事件到系统

PLC 事件消息采用：

```json
{
  "messageId": "关联命令ID，可选",
  "devicePoint": "PLC1",
  "eventName": "COMMAND_RUNNING",
  "timestamp": 1719892800000,
  "payload": {}
}
```

Adapter 校验事件是否在当前模板的 `cmdEvents` 或 `opEvents` 中。CMD 事件必须携带已知 `messageId`；终态事件 `COMMAND_COMPLETED`、`COMMAND_FAILED`、`COMMAND_ABORTED` 会清除关联记录。随后按 `EventMessageFormat` 发布到系统。

## 功能契约

- 读取温度：PLC 主动发布遥测，Adapter 转换并上报系统；不额外定义轮询命令。
- 设置加热：系统调用 `configureHeating`，参数为目标温度和加热时间。
- PLC 开关：系统调用 `setPower`，参数 `enabled` 为布尔值。
- Adapter 内部参数仅保留在 `adapterconfig.ini` 和 PLC 下行消息中，不进入系统设备能力模型。

## 代码结构

- `main.py`：进程入口、信号处理和生命周期管理。
- `smartlab_adapter/config.py`：两个 INI 文件的加载、解析和校验。
- `smartlab_adapter/messages.py`：系统/PLC 消息构造与基础校验。
- `smartlab_adapter/router.py`：命令、遥测和事件的纯业务转换。
- `smartlab_adapter/runtime.py`：Paho MQTT 连接、订阅、发布、重连和心跳。
- `tests/`：配置解析、内部参数注入、属性映射、事件校验和 Topic 渲染测试。

## 错误处理

- 配置缺失或格式错误时启动失败，并明确指出 section/key。
- 非法系统命令不会下发 PLC；如能识别设备点和 `messageId`，则上报 `COMMAND_FAILED`。
- 非法 PLC 遥测或事件只记录错误，不转发污染系统数据。
- MQTT 断线由 Paho 自动退避重连；重连后重新订阅并重新注册。
- 日志不输出 MQTT 密码。

## 验证标准

- 单元测试无需真实 Broker 或 PLC 即可运行。
- 配置、路由和消息转换测试全部通过。
- Python 语法编译通过。
- `adapterconfig.ini` 可被现有后端 INI 解析器接受。
- README 明确列出启动命令、PLC 消息格式和配置方法。
