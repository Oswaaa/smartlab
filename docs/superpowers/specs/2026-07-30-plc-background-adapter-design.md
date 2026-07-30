# PLC 后台 Adapter 设计规格

## 目标

基于 `adapter/testAdapter/plc控制器/plc_controller.py` 和厂商说明手册，
在 `adapter/testAdapter` 中开发一个无 GUI、通过 `python main.py` 启动并常驻运行的
SmartLab Adapter。

Adapter 与 SmartLab、PLC 分别建立独立 MQTT 连接。生产运行代码由
`main.py`、`adapterRuntime.py` 和 `core.py` 组成，不修改六份 Schema、数据库结构或
现有后端协议。

## 项目结构

```text
adapter/testAdapter/
├── main.py
├── adapterRuntime.py
├── core.py
├── runtime.ini
├── adapterSetup.ini
└── requirements.txt
```

现有 `PLCcodesys/`、`plc控制器/plc_controller.py` 和厂商说明手册作为设备与厂商资料
保留，不作为 Adapter 启动入口。

用户明确要求本次不创建 `tests/` 或自动化测试文件。

## 运行模型

Adapter 是单进程、无 GUI 的常驻程序：

```text
python main.py
  ├─ 创建 Core
  ├─ 创建 AdapterRuntime 并注入 Core
  ├─ 启动 PLC MQTT 客户端
  ├─ 启动 SmartLab MQTT 客户端
  ├─ 注册、订阅命令并启动心跳和超时扫描
  └─ 等待 Ctrl+C 或进程终止信号后优雅退出
```

SmartLab MQTT 和 PLC MQTT 使用独立客户端、独立 Client ID 和独立连接配置。即使两者
部署时指向同一 Broker，也不共享 MQTT 客户端或回调。

## 文件职责

### `main.py`

- 作为唯一启动入口。
- 定位 `runtime.ini` 和 `adapterSetup.ini`。
- 创建 `Core` 与 `AdapterRuntime`。
- 组装回调并调用 `run_forever()`。
- 将 Ctrl+C 和终止信号交给 Runtime 执行有序关闭。
- 不包含 SmartLab 报文、PLC 寄存器或设备业务逻辑。

### `adapterRuntime.py`

- 读取并校验本地 `runtime.ini`。
- 读取 `adapterSetup.ini` 完整原文，用于 Adapter 注册。
- 独立连接 SmartLab MQTT Broker。
- 按当前协议生成注册、心跳、命令、遥测和事件 Topic。
- 发布注册和 `ALIVE` 心跳。
- 断线重连后重新注册并重新订阅全部设备点命令 Topic。
- 校验命令报文身份和基础字段，再调用 `Core.execute_command()`。
- 将 Core 回调的遥测、CMD 事件和 OP 事件包装为当前协议报文并发布。
- 管理启动、停止、线程、心跳定时器和 Core 命令超时扫描。
- 不解释 PLC 寄存器或设备安全规则。

### `core.py`

- 从厂商 `plc_controller.py` 提取并改造设备通信能力，移除 Tkinter UI。
- 独立连接 PLC MQTT Broker，订阅 PLC 数据 Topic，发布 PLC 命令 Topic。
- 按 `DeviceSN` 选择目标设备并解析最新 `TagData`。
- 合并增量寄存器值，维护最新 PLC 状态。
- 把系统语义命令翻译为 PLC 寄存器写入。
- 根据 PLC 反馈确认命令完成，维护 `messageId` 幂等与命令超时。
- 从连续 PLC 状态变化推导 OP 事件。
- 通过回调把标准化遥测和事件交给 Runtime，不直接发布 SmartLab Topic。
- 不读取 SmartLab 协议字段，不生成注册或心跳报文。

## 配置边界

### `runtime.ini`

`runtime.ini` 只供 Adapter 本地运行，不发送给 SmartLab，包含：

```ini
[smartlab]
broker =
port =
username =
password =
clientId =
keepAliveSec =
qos =

[plc]
broker =
port =
username =
password =
clientId =
dataTopic =
commandTopic =
deviceSN =

[runtime]
heartbeatIntervalSec =
commandTimeoutSec =

[logging]
level =
```

日志不得输出密码或完整认证配置。

SmartLab Topic 不在 `runtime.ini` 中自由配置，而是按当前协议模板生成：

- `smartlab/adapter/register`
- `smartlab/adapter/{adapterName}/heartbeat`
- `smartlab/adapter/{adapterName}/{devicePoint}/command`
- `smartlab/adapter/{adapterName}/{devicePoint}/telemetry`
- `smartlab/adapter/{adapterName}/{devicePoint}/event`

### `adapterSetup.ini`

`adapterSetup.ini` 是发送给 SmartLab 的公开能力契约。它严格使用
`Backend/src/main/resources/samples/adapterSetup.ini` 的 flat INI 格式，包含：

- Adapter 标识和说明。
- 一个 PLC 设备模板。
- 一个 `temperature` 属性。
- 三个公开命令。
- CMD 与 OP 事件。
- 一个 PLC 设备点及属性映射。

文件中不包含 Broker、账号、密码或其他部署秘密。注册时 Runtime 把完整原文放入
`AdapterRegisterRequest.rawConfigContent`，并声明 `rawConfigFormat=INI`。

## 设备语义

### 寄存器

| 寄存器 | Core 内部语义 | 对系统公开方式 |
| --- | --- | --- |
| `MW0` | 温度原始值 | `temperature = MW0 / 100` 遥测属性 |
| `MW10` | 报警状态 | 仅用于内部状态、命令确认和 OP 事件 |
| `MW20` | 散热输出 | 仅用于内部状态、命令确认和 OP 事件 |
| `MW21` | 自动模式 | 仅用于内部状态、命令确认和 OP 事件 |
| `MW22` | 手动模式 | 仅用于内部状态、命令确认和 OP 事件 |

对外遥测只包含 `temperature`，其余四项不声明为 Adapter 属性。

### 命令

1. `setOperatingMode(mode)`
   - `AUTO`：写入 `MW21=1, MW22=0`。
   - `MANUAL`：写入 `MW21=0, MW22=1`。
2. `setCooling(enabled)`
   - `true`：写入 `MW20=1`。
   - `false`：写入 `MW20=0`。
3. `setAlarm(enabled)`
   - `true`：写入 `MW10=1`。
   - `false`：写入 `MW10=0`。

`setCooling(true)` 要求 Core 已收到 PLC 状态且设备处于手动模式，否则拒绝执行。
`setCooling(false)` 始终允许，用作安全停止。

### 运行事件

- `AUTOMATIC_MODE_ENTERED`
- `MANUAL_MODE_ENTERED`
- `COOLING_STARTED`
- `COOLING_STOPPED`
- `ALARM_TRIGGERED`
- `ALARM_CLEARED`

首次合法 PLC 数据只建立状态基线并上报温度，不产生 OP 事件。

## 数据流

### 系统命令到 PLC

```text
SmartLab commandTopic
  → AdapterRuntime 校验报文
  → Core.execute_command(messageId, devicePoint, commandName, parameters)
  → Core 参数与安全规则校验
  → PLC MQTT commandTopic
  → 后续 PLC 数据确认目标寄存器
  → Core 回调 CMD 事件
  → AdapterRuntime 发布 SmartLab eventTopic
```

### PLC 数据到 SmartLab

```text
PLC dataTopic
  → Core 解析 DeviceSN 与最新 TagData
  → 合并 MW 寄存器状态
  → temperature = MW0 / 100
  → AdapterRuntime 发布 telemetryTopic
  → 状态变化时发布 OP eventTopic
```

## 命令生命周期

- 报文与参数校验成功：`COMMAND_RECEIVED`。
- PLC MQTT 发布被客户端接受：`COMMAND_RUNNING`。
- 后续 PLC 状态满足目标寄存器：`COMMAND_COMPLETED`。
- 未知命令、参数错误、安全规则失败或发布失败：`COMMAND_FAILED`。
- 等待 PLC 反馈超过 `commandTimeoutSec`：`COMMAND_TIMEOUT`。

CMD 事件必须回传原命令 `messageId`。重复 `messageId` 不重复写 PLC；已完成结果可直接
重放，执行中的重复请求不创建第二个待确认项。

## 错误与生命周期

- 两个 MQTT 连接分别重连，任何一侧重连不重建另一侧客户端。
- SmartLab 断线期间 Core 可以继续维护 PLC 最新状态，但无法发布的数据不无限缓存；
  重连后重新注册、恢复心跳并发布下一份最新遥测。
- PLC 断线时拒绝新设备命令并上报 `COMMAND_FAILED`。
- PLC JSON、DeviceSN、TagData 或寄存器类型错误只记录安全日志，不上传错误数据。
- 停止顺序为：停止接收系统命令、停止心跳和超时扫描、停止 PLC 客户端、停止
  SmartLab 客户端。

## 验证范围

按用户要求不创建自动化测试。实现完成后只执行：

- Python 语法编译检查。
- INI 必填项和公开/本地配置边界检查。
- 使用后端现有 `AdapterManifestService` 验证 `adapterSetup.ini` 可解析。
- 无真实 PLC 的启动失败路径检查，确认错误可读且日志不泄露密码。
