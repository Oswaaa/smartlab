# GpioValveAdapter 开发文档（MQTT 电磁阀控制器）

日期：2026-10-10
状态：设计定稿，供实现。

- `adapterSetup.ini` 已用后端 `AdapterManifestService.parseRawConfig` / `buildAdapterContract` 和标准 `core.AdapterSetup.load` 实际解析通过。
- control / simulation 的行为已用一次性原型验证：第 11 节列出的 30 个单元测试全部通过；在本机 Broker 的隔离测试主题上，经真实 paho 客户端与假控制器跑通开、关、确认超时（含重发）、控制器离线四种场景。
- 设备时序已由 2026-10-08 的 gpiocnt01 实测报文确定（周期 5 秒、状态变化后立即补报，见 1.1.1）。超时默认值按此设定，并用模仿该时序的假控制器验证（11.6）。
- **尚未用本 Adapter 接真实 gpiocnt01 控制器验证**，联调待办见第 13 节。

本文是实现输入。凡写"必须""禁止"的地方没有自由发挥空间；与本文冲突时以本文为准，本文未覆盖的通用规则以 `Backend/src/main/resources/samples/adapter-development-guide.md` 为准。

---

## 0. 实现者先读

1. 目录：`adapter/new/GpioValveAdapter/`。
2. `core.py` 从 `fixture/adapter/反应釜/core.py` **逐字复制，一个字符都不改**（仓库内三个已落地 adapter 的 core.py 与它完全一致）。
3. `adapterSetup.ini`、`runtime.ini` 使用第 4、5 节的**完整原文**，不得增删 section、键名或修改事件名。
4. 你要写的只有 `control.py`、`simulation.py`、测试和假控制器工具。
5. 用户说的"先完成 adapter 的核心部分，再写与上位机交互的部分"，对应本规范是：**先写 control.py（与电磁阀控制器交互），再接 core.py（与上位机 SmartLab 交互）**。注意规范里叫 `core.py` 的那个文件恰恰是"与上位机交互"的部分，它已经焊死，只需复制。
6. `adapter/new/ValueAdapterMQTT` 是之前为 PLC（Modbus）写的程序，设备、协议和完成判定都不同，**不作为本 Adapter 的参考**。本 Adapter 的完成判定、遥测和失败处理以本文第 7 节为准。

---

## 1. 范围

### 1.1 设备协议（用户给定）

| 方向 | 主题 | 报文 |
| --- | --- | --- |
| 控制器 → Adapter | `gpiocnt01/gpio/state` | `{"gpios":[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],"timestamp":6991124,"rssi":-41}`，持续上报 |
| Adapter → 控制器 | `gpiocnt01/gpio/set` | `{"gpio":0,"val":0}`，`gpio` 为通道号，`val` 0=关 1=开 |

- `gpios` 共 16 项，下标就是 GPIO 通道号 0~15，值 0=关、1=开。
- `timestamp` 是控制器上电后的毫秒数（6991124 ≈ 1.9 小时），**不是**纪元时间。
- `rssi` 是控制器 WiFi 信号强度，属于控制器而不是某一路阀。

### 1.1.1 实测时序（2026-10-08 17:14:50 ~ 17:16:15 报文）

| 项 | 实测 | 对设计的影响 |
| --- | --- | --- |
| 周期上报 P | 设备时间严格每 5000 ms 一条（尾数恒为 …1124），到达间隔 4.94~5.07 s | `stateTimeoutSec`、`confirmTimeoutSec` 以 P = 5 s 计算（5.1） |
| 变化补报 | 三次 set 后均立即多出一条非周期报文（设备时间 6907309、6909966、6913041），距 set 142 / 171 / 483 ms | 正常情况下指令在 0.5 s 内确认 |
| 补报与周期的关系 | 补报不打乱周期节拍，下一条周期报文照常在 …1124 到达 | 补报丢失时，最迟由下一条周期报文（≤5 s）确认 |
| QoS | state 与 set 均为 QoS 0 | 单条报文可能丢失，必须有重发和兜底确认 |
| 空档 | 17:15:00.440 与 17:15:25.487 之间缺 4 条周期报文（设备时间跳 25000） | 若是真实丢包，10 s 的离线判定会误判；按 30 s 设计。是否复制时省略待确认（第 13 节） |
| 时钟漂移 | 85 s 内设备时钟与墙上时钟相差约 40 ms | 不影响，设备时间本来就不用 |

调试工具发出的 set 写法是 `{"gpio":0,"val": 0}`（`val` 冒号后有空格）。Adapter 发出的是紧凑 JSON `{"gpio":0,"val":0}`，二者都是合法 JSON，但紧凑写法尚未在真机上发过，第 13 节第 1 步先确认。

### 1.2 本 Adapter 的边界

- 一个 Adapter 进程只管一台控制器（gpiocnt01）的 16 路阀，即 16 个物理点。以后增加 gpiocnt02，复制一份目录，改 `adapterName`（如 `GpioValveAdapter-02`）和 `runtime.ini` 中的两个主题，独立运行。
- 同一台控制器同一时间只能有一个 Adapter 进程在控制。
- 不提供终止能力（原因见 2.4）。
- `timestamp`、`rssi` 不进入系统契约。

---

## 2. 设备建模结论：每路阀有哪些状态、功能、属性

你的判断（功能 = 打开/关闭，状态 = 开/关）是对的。对"属性应该没有了"，我的结论是**保留一个属性 `isOpen`**。三者在 SmartLab 中走的是三条不同的通路，缺一条就会少一种能力：

| 维度 | 定义 | 走的通路 | 谁在用 |
| --- | --- | --- | --- |
| 功能（命令/能力） | `open`、`close`，系统侧无参数 | command 主题 → 状态机 CMD 生命周期 | 工作流设备节点、人工控制台、约束动作 |
| 状态（OP 状态） | 区域 `valveState`：`CLOSED` / `OPEN` | opEvents `VALVE_OPENED` / `VALVE_CLOSED` → 状态机 OP 区域 | 状态机、约束引擎（`DEVICE_OPERATION_STATE`）、控制台显示 |
| 属性（遥测） | `isOpen`，BOOLEAN | telemetry 主题 → 孪生属性、数据集 | 孪生快照、历史数据、约束表达式（`DEVICE_ATTRIBUTE`） |

### 2.1 为什么要保留属性 `isOpen`

- 控制器本来就在持续上报真实开闭，这在系统里就是遥测。系统的属性快照和历史数据只能来自遥测，OP 状态不会写进数据集。
- OP 状态完全由事件驱动，属性则每条遥测都按控制器的真实值覆盖。有人手动拨阀、控制器重启全部归零、某条事件丢失时，属性会如实纠正。
- 约束模型可以同时观察属性（`DEVICE_ATTRIBUTE`）和 OP 状态（`DEVICE_OPERATION_STATE`）。约束表达式支持 `true/false` 字面量和布尔相等比较（`ConstraintExpressionEvaluator` 已核对），例如 `isOpen == false`。
- 工作流设备节点只消费 `CMD_STATE`（`系统执行规范.json`：DEV_NODE 的 `Interface_state_in.allowedSignals` 只有 `CMD_STATE`），属性和 OP 状态都不会直接进入工作流节点，保不保留属性这一点都一样。
- 代价只有一个属性和一条映射，外加每个实例要绑定数据集（见 12.4）。

### 2.2 为什么是 BOOLEAN 而不是 INTEGER

两位阀语义上就是真/假。后端对遥测做严格类型校验（`AdapterPayloadMapperService.validatedValue`）：声明 BOOLEAN 后只接受 JSON `true/false`，发 `0/1` 会让整条遥测被拒收。因此 control 必须把设备的 0/1 转成布尔值再上报。

唯一的取舍：设备内置约束（`intrinsicConstraints`）只认数值，布尔属性不能用。阀门不需要内置约束；将来真要用，必须同时把 Setup 和设备模型里的类型改成 INTEGER。

### 2.3 为什么只有 open / close 两个命令

不提供带布尔参数的 `setValve(state)`。两个无参数命令在工作流里直接选能力即可，没有参数映射和参数类型校验的出错面。设备一路一个 `gpio` 通道号，由 core 通过 internal 参数注入，系统侧看不到。

### 2.4 为什么不提供终止能力

两位阀没有"执行到一半停下来"的物理语义，一条指令从下发到确认的时间取决于控制器回报速度，最长不超过 `confirmTimeoutSec`。设备模型里两个能力的 `abortCapabilityName` 设为 `null`。

后果要清楚：如果任务终止恰好发生在某条阀门指令的确认窗口内，状态机会拒绝 `WF_EXECUTE_ABORT`（报"设备能力未在物模型中配置关联的终止能力"），该指令在确认窗口内自然完成或失败。

### 2.5 指令"完成"的含义

`COMMAND_COMPLETED` 只在**控制器回报的状态数组里该路已等于目标值**时发出，不是"set 已发出"就算完成。没等到就是失败，不会假成功。

---

## 3. 目录与文件

```text
adapter/new/GpioValveAdapter/
├── core.py                      # 逐字复制 fixture/adapter/反应釜/core.py
├── main.py                      # 薄入口，见 10.2
├── control.py                   # 南向：经 MQTT 控制 gpiocnt01（本文第 6、7 节）
├── simulation.py                # 虚拟阀（本文第 9 节）
├── adapterSetup.ini             # 第 4 节原文
├── runtime.ini                  # 第 5 节原文
├── requirements.txt             # paho-mqtt>=2.0,<3
├── test_control.py              # 第 11 节
├── test_simulation.py
├── test_core_integration.py
└── fake_gpio_device.py          # 假控制器，仅联调/测试用，运行时不导入
```

测试放在 adapter 根目录（与 `fixture/adapter/反应釜` 一致），在该目录下执行 `python -m unittest -v`。运行时会在该目录生成 `virtual_leases.json`，不要提交。

Python ≥ 3.10。

---

## 4. adapterSetup.ini（逐字使用）

```ini
; SmartLab Adapter 能力契约：GPIO 电磁阀控制器（gpiocnt01，16 路）
; 格式严格遵循 Backend/src/main/resources/samples/adapterSetup.ini，不得增删 section 层级。
; description 是契约数据，不能写成注释；任何值行都不得带行尾注释。
; 注册时本文件完整原文作为 rawConfigContent 上传，rawConfigFormat = INI。

[adapter]
specVersion = smartlab.adapter.config.v1
adapterName = GpioValveAdapter-01
adapterDescription = 16路GPIO电磁阀控制器适配器，经MQTT下发开关指令并回读阀门开闭状态
rawConfigFormat = INI


; ============================================================
; deviceTemplates[]：一个模板 = 一路电磁阀
; ============================================================

[deviceTemplates.SolenoidValveTemplate]
categoryName = SolenoidValve
categoryDescription = 两位开关型电磁阀，每个设备点对应控制器上的一路GPIO
description = 单路电磁阀能力模板，支持打开、关闭与开闭状态回读


; ============================================================
; attributes[]：只有一个属性，阀门开闭状态
; 设备原始值 0/1 由 control 转成 JSON 布尔值后上报
; ============================================================

[deviceTemplates.SolenoidValveTemplate.attributes]
isOpen = [BOOLEAN][阀门开闭状态，true为开，false为关]


; ============================================================
; commands[]：open / close，系统侧无外部参数
; gpio 为 internal 参数，由 core 从 [devicePoints.<点>] 的 gpio 字段注入
; ============================================================

[deviceTemplates.SolenoidValveTemplate.commands.open]
description = 打开电磁阀，控制器回报该路为开后指令完成

[deviceTemplates.SolenoidValveTemplate.commands.open.parameters]
gpio = [INTEGER][GPIO通道号，0到15][internal=true][sourceField=gpio]


[deviceTemplates.SolenoidValveTemplate.commands.close]
description = 关闭电磁阀，控制器回报该路为关后指令完成

[deviceTemplates.SolenoidValveTemplate.commands.close.parameters]
gpio = [INTEGER][GPIO通道号，0到15][internal=true][sourceField=gpio]


; ============================================================
; events.cmdEvents[]：只声明本 Adapter 真实会发出的指令生命周期事件
; ============================================================

[deviceTemplates.SolenoidValveTemplate.events.cmdEvents]
COMMAND_RUNNING = 指令执行中，开关指令已下发到控制器
COMMAND_COMPLETED = 指令执行完成，控制器已回报目标开闭状态
COMMAND_FAILED = 指令执行失败，原因见payload中的reason与error


; ============================================================
; events.opEvents[]：阀门开闭状态变化事件
; ============================================================

[deviceTemplates.SolenoidValveTemplate.events.opEvents]
VALVE_OPENED = 阀门已打开
VALVE_CLOSED = 阀门已关闭


; ============================================================
; devicePoints[]：16 个物理点，点名序号 = GPIO 通道号
; gpio 字段供 internal 参数注入，也是 control 解析状态数组的唯一依据
; ============================================================

[devicePoints.Valve00]
templateName = SolenoidValveTemplate
description = 0号电磁阀，GPIO 0
gpio = 0

[devicePoints.Valve00.attributeMapping]
isOpen = raw_open


[devicePoints.Valve01]
templateName = SolenoidValveTemplate
description = 1号电磁阀，GPIO 1
gpio = 1

[devicePoints.Valve01.attributeMapping]
isOpen = raw_open


[devicePoints.Valve02]
templateName = SolenoidValveTemplate
description = 2号电磁阀，GPIO 2
gpio = 2

[devicePoints.Valve02.attributeMapping]
isOpen = raw_open


[devicePoints.Valve03]
templateName = SolenoidValveTemplate
description = 3号电磁阀，GPIO 3
gpio = 3

[devicePoints.Valve03.attributeMapping]
isOpen = raw_open


[devicePoints.Valve04]
templateName = SolenoidValveTemplate
description = 4号电磁阀，GPIO 4
gpio = 4

[devicePoints.Valve04.attributeMapping]
isOpen = raw_open


[devicePoints.Valve05]
templateName = SolenoidValveTemplate
description = 5号电磁阀，GPIO 5
gpio = 5

[devicePoints.Valve05.attributeMapping]
isOpen = raw_open


[devicePoints.Valve06]
templateName = SolenoidValveTemplate
description = 6号电磁阀，GPIO 6
gpio = 6

[devicePoints.Valve06.attributeMapping]
isOpen = raw_open


[devicePoints.Valve07]
templateName = SolenoidValveTemplate
description = 7号电磁阀，GPIO 7
gpio = 7

[devicePoints.Valve07.attributeMapping]
isOpen = raw_open


[devicePoints.Valve08]
templateName = SolenoidValveTemplate
description = 8号电磁阀，GPIO 8
gpio = 8

[devicePoints.Valve08.attributeMapping]
isOpen = raw_open


[devicePoints.Valve09]
templateName = SolenoidValveTemplate
description = 9号电磁阀，GPIO 9
gpio = 9

[devicePoints.Valve09.attributeMapping]
isOpen = raw_open


[devicePoints.Valve10]
templateName = SolenoidValveTemplate
description = 10号电磁阀，GPIO 10
gpio = 10

[devicePoints.Valve10.attributeMapping]
isOpen = raw_open


[devicePoints.Valve11]
templateName = SolenoidValveTemplate
description = 11号电磁阀，GPIO 11
gpio = 11

[devicePoints.Valve11.attributeMapping]
isOpen = raw_open


[devicePoints.Valve12]
templateName = SolenoidValveTemplate
description = 12号电磁阀，GPIO 12
gpio = 12

[devicePoints.Valve12.attributeMapping]
isOpen = raw_open


[devicePoints.Valve13]
templateName = SolenoidValveTemplate
description = 13号电磁阀，GPIO 13
gpio = 13

[devicePoints.Valve13.attributeMapping]
isOpen = raw_open


[devicePoints.Valve14]
templateName = SolenoidValveTemplate
description = 14号电磁阀，GPIO 14
gpio = 14

[devicePoints.Valve14.attributeMapping]
isOpen = raw_open


[devicePoints.Valve15]
templateName = SolenoidValveTemplate
description = 15号电磁阀，GPIO 15
gpio = 15

[devicePoints.Valve15.attributeMapping]
isOpen = raw_open
```

### 4.1 已验证的解析结果

后端 `AdapterManifestService` 解析后：1 个类别 `SolenoidValve`，16 个点；`gpio` 在 `parsed_config` 中为整数；`open` / `close` 的 `parameters` 为空（internal 参数被剔除）；`buildAdapterContract` 生成的契约中 `telemetry.adapterAttributes = [isOpen: BOOLEAN]`，cmdEvents 3 个、opEvents 2 个。

标准 core 加载后：`physical_points = Valve00..Valve15`，`point_fields["Valve03"] = {"gpio": "3"}`，`internal_params` 对 open/close 都是 `[("gpio", "gpio")]`。

**注意：core 注入到 `command.parameters["gpio"]` 的值是字符串 `"3"`，不是整数。**

### 4.2 这些写法不能改的原因

| 写法 | 原因 |
| --- | --- |
| 点名 `Valve00..Valve15`，序号 = GPIO 号 | 消除 1 起/0 起的错位。点名可以改，但 control 只能用 `gpio` 字段定位通道，**禁止**从点名里解析数字 |
| cmdEvents 只有 3 个 | 只声明真实会发出的事件。声明了不发的事件（如 `COMMAND_TIMEOUT`）会诱导建模人员配置永远不会触发的转移。超时统一走 `COMMAND_FAILED`，原因放在 `payload.reason` |
| 不写 `COMMAND_RECEIVED` | 标准 CMD 转移里没有它，发了也会被状态机忽略 |
| 所有属性都有 attributeMapping | 示例规范要求；core 依此把 `raw_open` 映射成 `isOpen` |
| 不写 `virtual=true`、不写 Broker/密码 | 规范禁止 |
| 值后面不写注释 | Python configparser 和后端解析器都不支持行尾注释，会把注释当成值的一部分 |
| 标识符只用字母数字下划线 | 后端要求 `[A-Za-z_][A-Za-z0-9_-]*`，且 `.` 是 section 层级分隔符 |

---

## 5. runtime.ini（逐字使用，部署时只改地址和口令）

```ini
; GpioValveAdapter 本地运行配置，不随注册上传。
; 只允许整行注释（以 ; 或 # 开头），任何值的后面都不能写注释。

[smartlab]
broker = 127.0.0.1
port = 1883
username = adapter
password = 123456
clientId = smartlab_gpio_valve_adapter
keepAliveSec = 30
qos = 1

[runtime]
heartbeatIntervalSec = 10

[logging]
level = INFO

; ---------- 以下段只由 control.py 读取 ----------
[gpio]
; 控制器所在 Broker，可以与 [smartlab] 是同一台
broker = 127.0.0.1
port = 1883
username = adapter
password = 123456
; 实际连接时会追加 _{pid}_{4位随机串}
clientId = smartlab_gpio_valve_south
keepAliveSec = 30
qos = 1
stateTopic = gpiocnt01/gpio/state
setTopic = gpiocnt01/gpio/set
channelCount = 16
confirmTimeoutSec = 8.0
resendIntervalSec = 2.0
stateTimeoutSec = 30.0
telemetryIntervalSec = 30
opResyncSec = 30

; ---------- 以下段只由 simulation.py 读取 ----------
[simulation]
intervalSec = 0.2
idleReportSec = 1.0
```

本机 `127.0.0.1:1883` 的 Broker 拒绝匿名连接，`[gpio]` 必须填用户名和口令。生产口令不要提交仓库。

### 5.1 `[gpio]` 键定义

control 启动时必须逐项校验，任一不合法直接抛 `ValueError`，进程启动失败（不要带病运行）。

| 键 | 默认 | 合法范围 | 含义 |
| --- | --- | --- | --- |
| `broker` | 无 | 非空 | 控制器所在 Broker 地址 |
| `port` | 1883 | 1~65535 | |
| `username` / `password` | 空 | | 空用户名表示匿名 |
| `clientId` | `smartlab_gpio_valve_south` | 非空 | 前缀；实际值 `{clientId}_{pid}_{uuid4().hex[:4]}` |
| `keepAliveSec` | 30 | 整数 | |
| `qos` | 1 | 0 或 1 | 订阅 state 与发布 set 的 QoS |
| `stateTopic` | 无 | 非空，不含 `+` `#` | 状态主题 |
| `setTopic` | 无 | 非空，不含 `+` `#`，不等于 stateTopic | 控制主题 |
| `channelCount` | 16 | ≥1 | 状态数组必须恰好这么长 |
| `confirmTimeoutSec` | 8.0 | 0.5~60 | 下发后等待目标状态的最长时间 |
| `resendIntervalSec` | 2.0 | 0（关闭）或 0.2~confirmTimeoutSec（不含） | 未确认时重发同一 set 的间隔 |
| `stateTimeoutSec` | 30.0 | 1~300 | 超过这么久没收到合法状态报文即判控制器离线 |
| `telemetryIntervalSec` | 30 | 1~600 | 状态不变时每点的遥测保活周期 |
| `opResyncSec` | 30 | 0（关闭）或 10~3600 | 周期性重发全部 OP 事件以自愈 OP 状态 |

默认值按实测 P = 5 s、变化后 0.5 s 内补报确定（1.1.1）。设备时序变化时按下列规则重算：

- `confirmTimeoutSec ≥ P + 3`（默认 8）。正常确认靠变化补报，0.5 s 内完成；但两种情况要等下一条周期报文：补报这条 QoS 0 报文丢了；或者阀已经在目标状态，set 不引起变化，控制器很可能不补报。下一条周期报文最晚 P 秒后到。用 5 s 会和它赛跑：模仿该时序的验证中，"已开再开"的确认耗时 4.976 s（11.6）。
- `stateTimeoutSec ≥ 6 × P`（默认 30）。它只决定"直接以 CONTROLLER_OFFLINE 失败"还是"照常下发再等确认"。判早了会让本可成功的指令直接失败；判晚了代价只是真离线时等 `confirmTimeoutSec` 后以 CONFIRM_TIMEOUT 失败。实测报文里有一段 25 s 的空档，必须能扛住。
- `resendIntervalSec < confirmTimeoutSec`（默认 2，确认窗口内最多下发 4 次：0/2/4/6 s）。set 是绝对值，重发幂等。

### 5.2 `[simulation]` 键定义

| 键 | 默认 | 合法范围 | 含义 |
| --- | --- | --- | --- |
| `intervalSec` | 0.2 | 0.1~1.0 | 仿真循环周期；虚拟指令在下一个周期内完成 |
| `idleReportSec` | 1.0 | 0.5~2.0 | 空闲态每个虚拟阀的上报周期（规范要求 1 秒） |

规范示例里的 `batchSize` / `simStepMs` 不使用：两位阀没有需要时间压缩推演的动力学，一次切换只有一个数据点（见 9.4）。

---

## 6. 设备侧协议规则（control 必须遵守）

### 6.1 状态报文解析

一条报文**任何一项不合法就整包丢弃**，不做部分应用。丢弃时打 WARNING，同类日志 10 秒最多一条。

1. `message.topic` 不等于 `stateTopic`：忽略。
2. `message.retain` 为真：忽略。按 MQTT 3.1.1，订阅建立时 Broker 补发的保留消息 retain 标志为 1，它可能是控制器离线前留下的旧状态，用它判断在线和确认指令都会出错。正常转发的消息 retain 为 0。
3. JSON 解析失败，或顶层不是对象：丢弃。
4. `gpios` 不是数组，或长度不等于 `channelCount`：丢弃。
5. `gpios` 中任一元素不是整数 0 或 1：丢弃。布尔值 `true/false`、字符串 `"1"`、`2` 都算不合法（Python 里 `bool` 是 `int` 子类，必须先排除）。
6. `timestamp`、`rssi` 不读取，也不校验。

参考实现（已测试）：

```python
def parse_state_payload(payload: bytes | str, channel_count: int) -> list[int] | None:
    try:
        body = json.loads(payload)
    except (ValueError, UnicodeDecodeError):
        return None
    if not isinstance(body, dict):
        return None
    gpios = body.get("gpios")
    if not isinstance(gpios, list) or len(gpios) != channel_count:
        return None
    values: list[int] = []
    for value in gpios:
        if isinstance(value, bool) or not isinstance(value, int) or value not in (0, 1):
            return None
        values.append(value)
    return values
```

### 6.2 控制报文

- 报文必须**恰好**是 `{"gpio": <int>, "val": <0|1>}`，两个都是 JSON 整数，没有其他字段。用 `json.dumps({"gpio": gpio, "val": value}, separators=(",", ":"))`。
- 发布 `retain=False`。**禁止 retain**：保留的 set 会在控制器每次重连时被 Broker 重新投递，阀门会被反复动作。
- QoS 用 `[gpio] qos`。
- Adapter 只在执行系统指令时发 set。**启动、停止、重连、断线时都禁止向控制器写任何东西**，包括"安全关阀"。安全策略属于系统（工作流、约束），不属于 Adapter。

### 6.3 时间戳

上报给 SmartLab 的遥测和事件时间戳一律用 Adapter 本机时钟 `now_ms()`（纪元毫秒）。设备的 `timestamp` 是上电毫秒数，后端会把它当成 1970 年的数据，**禁止使用**。

---

## 7. control.py 规格

### 7.1 接口

```python
class ControlImpl:
    def __init__(self, extra, physical_points, point_fields=None) -> None: ...
    def start(self) -> None: ...
    def stop(self) -> None: ...
    def execute(self, command: SystemCommand) -> None: ...
    def set_data_handler(self, handler) -> None: ...
    def set_event_handler(self, handler) -> None: ...
```

签名与规范第 8 节一致，core 的 `main()` 以 `ControlImpl(runtime.extra, setup.physical_points, setup.point_fields)` 构造。模块顶部 `from core import AdapterEvent, DeviceData, SystemCommand, now_ms`。

### 7.2 构造时校验（任一失败抛 `ValueError`）

1. `extra["gpio"]` 存在，所有键按 5.1 校验。
2. 物理点至少 1 个。
3. 每个物理点的 `point_fields[point]["gpio"]` 去空白后全是数字，转整数后 `< channelCount`。
4. 不同点的 gpio 不重复。

结果是 `self._gpio_by_point: dict[str, int]`，它是**状态数组下标与点名之间的唯一映射**。

```python
def build_gpio_map(physical_points, point_fields, channel_count) -> dict[str, int]:
    if not physical_points:
        raise ValueError("ControlImpl 需要至少一个物理点（来自 adapterSetup.ini）")
    gpio_by_point: dict[str, int] = {}
    owner: dict[int, str] = {}
    for point in physical_points:
        raw = str(point_fields.get(point, {}).get("gpio", "")).strip()
        if not raw.isdigit():
            raise ValueError(f"设备点 {point} 缺少合法的 gpio 字段: {raw!r}")
        gpio = int(raw)
        if gpio >= channel_count:
            raise ValueError(f"设备点 {point} 的 gpio={gpio} 超出 channelCount={channel_count}")
        if gpio in owner:
            raise ValueError(f"gpio={gpio} 被 {owner[gpio]} 与 {point} 重复占用")
        owner[gpio] = point
        gpio_by_point[point] = gpio
    return gpio_by_point
```

### 7.3 线程模型

| 线程 | 来源 | 做什么 |
| --- | --- | --- |
| SmartLab MQTT 网络线程 | core 的 paho | 调用 `execute()`。**禁止阻塞**：不 sleep、不等确认、不做同步 I/O |
| 南向 MQTT 网络线程 | control 自己的 paho（独立客户端） | `_on_connect` / `_on_disconnect` / `_on_message` |
| `gpio-valve-watchdog` | control 启动的守护线程 | 每 0.2 秒检查超时、重发、在线状态变化 |

- control 必须用**自己的** paho 客户端连控制器 Broker，不得复用或触碰 core 的客户端，不得订阅任何 `smartlab/` 主题。即使两个 Broker 是同一台也一样。
- 只用 paho 2.x 的 `CallbackAPIVersion.VERSION2` 回调签名。
- 一把 `threading.Lock` 保护全部内部状态。**持锁时禁止调用 data/event handler**：先在锁内算出要发的遥测和事件，出锁后按顺序发。handler 会进入 core 并调用 SmartLab 客户端的 `publish`，持锁调用有死锁和阻塞风险。

### 7.4 内部状态

| 字段 | 含义 |
| --- | --- |
| `_link_up: bool` | 南向客户端是否已连上 |
| `_last_report_mono: float \| None` | 最近一条**合法**状态报文的 `time.monotonic()` |
| `_known: dict[str, int]` | 每点最近一次看到的 0/1 |
| `_pending: dict[str, PendingCommand]` | 每点**最多一条**待确认指令 |
| `_last_telemetry_mono: dict[str, float]` | 每点最近一次上报遥测的时刻 |
| `_baseline_pending: bool` | 初始为 True；每次南向连接成功置 True；下一条合法报文消费后置 False |
| `_last_resync_mono: float` | 最近一次全量 OP 事件的时刻 |
| 最近 256 个 `messageId` 的集合 | 进程内去重 |

```python
@dataclass
class PendingCommand:
    message_id: str
    command_name: str
    gpio: int
    target: int            # 1=open, 0=close
    deadline: float        # monotonic
    next_resend: float | None
```

**控制器在线** ⇔ `_link_up` 为真 **且** 收到过合法报文 **且** `now - _last_report_mono ≤ stateTimeoutSec`。进程刚启动、还没收到第一条报文时视为离线。

### 7.5 `execute(command)` 流程

顺序不能调换。每一步失败都已在前面发过 `COMMAND_RUNNING`，原因见 8.1。

1. `messageId` 已在最近 256 个里：记 WARNING，**不发任何事件、不下发**，直接返回。（QoS1 至少一次投递可能重复；重复的 `open` 在别人关阀之后被重新执行是安全问题。）
2. 点不在 `_gpio_by_point`（虚拟点被误送到这里，属于 core 分流错误的最后防线）：`COMMAND_RUNNING` + `COMMAND_FAILED(NOT_PHYSICAL_POINT)`，不碰控制器。
3. 发 `COMMAND_RUNNING`，payload `{"commandName": name, "gpio": gpio}`。
4. 命令名不是 `open` / `close`：`COMMAND_FAILED(UNSUPPORTED_COMMAND)`。
5. `parameters["gpio"]` 存在且 `str(值).strip() != str(点表gpio)`：`COMMAND_FAILED(GPIO_MISMATCH)`。（正常不会发生，二者同源，属于防御。）
6. 持锁判在线。离线：出锁后 `COMMAND_FAILED(CONTROLLER_OFFLINE)`，**不下发**。
7. 在线：持锁取出该点旧的待确认指令（若有），登记新的 `PendingCommand`。**必须先登记再发布**，否则控制器很快回报时会漏掉确认。
8. 出锁。若有旧指令，对**旧** `messageId` 发 `COMMAND_FAILED(SUPERSEDED)`。
9. 发布 set。发布失败时，持锁检查该点待确认的还是不是本 `messageId`：是才摘除并发 `COMMAND_FAILED(PUBLISH_FAILED)`；不是说明设备线程已经把它确认完成，**什么都不发**。这一步保证每条指令只有一个终态。

参考实现（已测试）：

```python
COMMAND_TARGETS = {"open": 1, "close": 0}

def execute(self, command: SystemCommand) -> None:
    point = command.device_point
    mid = command.message_id
    name = command.command_name
    if not self._remember(mid):
        logger.warning("忽略重复投递的指令 messageId=%s point=%s", mid, point)
        return
    if point not in self._gpio_by_point:
        logger.error("control 收到非物理点指令 %s，拒绝执行", point)
        self._fail_now(point, mid, name, "NOT_PHYSICAL_POINT", f"非物理设备点: {point}")
        return
    gpio = self._gpio_by_point[point]
    self._event(point, "COMMAND_RUNNING", mid, {"commandName": name, "gpio": gpio})
    target = COMMAND_TARGETS.get(name)
    if target is None:
        self._event(point, "COMMAND_FAILED", mid, {"reason": "UNSUPPORTED_COMMAND", "error": f"不支持的指令: {name}"})
        return
    injected = command.parameters.get("gpio")
    if injected is not None and str(injected).strip() != str(gpio):
        self._event(point, "COMMAND_FAILED", mid,
                    {"reason": "GPIO_MISMATCH", "error": f"gpio 参数 {injected} 与点表 {gpio} 不一致"})
        return
    now = time.monotonic()
    s = self.settings
    with self._lock:
        online = self._online_locked(now)
        superseded = None
        if online:
            superseded = self._pending.pop(point, None)
            self._pending[point] = PendingCommand(
                mid, name, gpio, target, now + s.confirm_timeout_sec,
                now + s.resend_interval_sec if s.resend_interval_sec > 0 else None,
            )
    if not online:
        self._event(point, "COMMAND_FAILED", mid,
                    {"reason": "CONTROLLER_OFFLINE", "error": "GPIO 控制器离线或状态报文超时"})
        return
    if superseded is not None:
        self._event(point, "COMMAND_FAILED", superseded.message_id,
                    {"reason": "SUPERSEDED", "error": f"被新指令 {mid} 取代"})
    if not self._publish_set(gpio, target):
        with self._lock:
            current = self._pending.get(point)
            owned = current is not None and current.message_id == mid
            if owned:
                del self._pending[point]
        if owned:
            self._event(point, "COMMAND_FAILED", mid, {"reason": "PUBLISH_FAILED", "error": "控制主题发布失败"})

def _fail_now(self, point, mid, name, reason, error) -> None:
    self._event(point, "COMMAND_RUNNING", mid, {"commandName": name})
    self._event(point, "COMMAND_FAILED", mid, {"reason": reason, "error": error})

def _publish_set(self, gpio: int, value: int) -> bool:
    client = self._client
    if client is None or not self._link_up:
        return False
    body = json.dumps({"gpio": gpio, "val": value}, separators=(",", ":"))
    info = client.publish(self.settings.set_topic, body, qos=self.settings.qos, retain=False)
    ok = info.rc == mqtt.MQTT_ERR_SUCCESS
    logger.info("下发 %s %s -> %s", self.settings.set_topic, body, "OK" if ok else f"rc={info.rc}")
    return ok
```

`_event(point, name, mid, payload)` 构造 `AdapterEvent(point, name, mid, payload, now_ms())` 并交给 event handler。

### 7.6 状态报文处理 `_apply_report(gpios)`

对每个物理点按 `_gpio_by_point` 取 `value = gpios[gpio]`，`old = _known.get(point)`，`changed = old is None or value != old`，然后：

**遥测**：`changed`，或该点从未上报过，或距上次上报 ≥ `telemetryIntervalSec` 时，产出 `DeviceData(point, {"raw_open": value == 1}, now_ms())`。字段值必须是 Python `bool`。

**指令确认**：该点有待确认指令且 `value == target`：摘除，产出 `VALVE_OPENED|VALVE_CLOSED(messageId=该指令, source=COMMAND)`，紧接着 `COMMAND_COMPLETED(该指令)`。值已经等于目标（阀本来就开着又收到 open）也照此确认，同样发 OP 事件，用于把 OP 状态校正一遍。

**OP 事件**：没有走上面的确认分支时，若 `changed`、或本条是连接后的首条（BASELINE）、或到了 `opResyncSec` 周期（RESYNC），产出 `VALVE_OPENED|VALVE_CLOSED(messageId=None)`，payload `source` 取 `BASELINE` / `CHANGE` / `RESYNC`。同一条报文对同一个点最多一条 OP 事件。

值不等于目标时**不**判失败，继续等，交给看门狗超时。这样"下发前已在途的旧报文"不会让指令误完成或误失败。

**发出顺序**：先发全部遥测，再按产出顺序发事件。效果是工作流看到 `COMPLETED` 时，孪生属性和 OP 状态都已经是新值。

参考实现（已测试）：

```python
def _apply_report(self, gpios: list[int]) -> None:
    now = time.monotonic()
    ts = now_ms()
    data: list[DeviceData] = []
    events: list[AdapterEvent] = []
    s = self.settings
    with self._lock:
        self._last_report_mono = now
        baseline = self._baseline_pending
        self._baseline_pending = False
        resync = s.op_resync_sec > 0 and now - self._last_resync_mono >= s.op_resync_sec
        if baseline or resync:
            self._last_resync_mono = now
        for point, gpio in self._gpio_by_point.items():
            value = gpios[gpio]
            old = self._known.get(point)
            self._known[point] = value
            changed = old is None or value != old
            last_tel = self._last_telemetry_mono.get(point)
            if changed or last_tel is None or now - last_tel >= s.telemetry_interval_sec:
                self._last_telemetry_mono[point] = now
                data.append(DeviceData(point, {"raw_open": value == 1}, ts))
            pending = self._pending.get(point)
            if pending is not None and value == pending.target:
                del self._pending[point]
                events.append(AdapterEvent(point, op_event_name(value), pending.message_id,
                                           {"gpio": gpio, "source": "COMMAND"}, ts))
                events.append(AdapterEvent(point, "COMMAND_COMPLETED", pending.message_id,
                                           {"gpio": gpio, "isOpen": value == 1}, ts))
            elif changed or baseline or resync:
                source = "BASELINE" if baseline else ("CHANGE" if changed else "RESYNC")
                events.append(AdapterEvent(point, op_event_name(value), None,
                                           {"gpio": gpio, "source": source}, ts))
    for item in data:
        self._emit_data(item)
    for event in events:
        self._emit(event)


def op_event_name(value: int) -> str:
    return "VALVE_OPENED" if value == 1 else "VALVE_CLOSED"
```

为什么要 BASELINE 和 RESYNC：设备模型的 OP 初始状态是 `CLOSED`，阀如果本来开着，系统不知道；后端重启、或 Adapter 启动时 SmartLab 连接还没建好导致首轮事件被 core 丢弃，OP 状态也会偏。重复的 OP 事件在状态机里没有对应转移（`OPEN→OPEN` 不存在），会被安全忽略，所以周期重发没有副作用。**不要关闭 `opResyncSec`。**

### 7.7 看门狗 `_watchdog_tick()`（每 0.2 秒）

1. 持锁遍历待确认指令：到 `deadline` 的摘除，记入超时列表；到 `next_resend` 的把 `next_resend` 推后一个 `resendIntervalSec`，记入重发列表。同时计算在线状态。
2. 出锁。在线状态与上次不同时记一条日志（上线 INFO，离线 WARNING），只在变化时记。
3. 对重发列表再发一次同样的 set（目标值是绝对的，重发幂等）。发布失败不处理，交给超时。
4. 对超时列表发 `COMMAND_FAILED(CONFIRM_TIMEOUT)`。

整个 tick 包在 `try/except Exception` 里并 `logger.exception`，看门狗线程不能因异常退出。

```python
def _watchdog_tick(self) -> None:
    now = time.monotonic()
    expired: list[tuple[str, PendingCommand]] = []
    resend: list[PendingCommand] = []
    with self._lock:
        for point, pending in list(self._pending.items()):
            if now >= pending.deadline:
                del self._pending[point]
                expired.append((point, pending))
            elif pending.next_resend is not None and now >= pending.next_resend:
                pending.next_resend = now + self.settings.resend_interval_sec
                resend.append(pending)
        online = self._online_locked(now)
    if online != self._online_logged:
        self._online_logged = online
        if online:
            logger.info("GPIO 控制器在线")
        else:
            logger.warning("GPIO 控制器离线")
    for pending in resend:
        self._publish_set(pending.gpio, pending.target)
    for point, pending in expired:
        self._event(point, "COMMAND_FAILED", pending.message_id,
                    {"reason": "CONFIRM_TIMEOUT",
                     "error": f"{self.settings.confirm_timeout_sec}s 内未收到 gpio {pending.gpio} 的目标状态"})
```

### 7.8 南向连接与启停

```python
def start(self) -> None:
    s = self.settings
    client = mqtt.Client(
        callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
        client_id=f"{s.client_id}_{os.getpid()}_{uuid.uuid4().hex[:4]}",
        clean_session=True,
    )
    if s.username:
        client.username_pw_set(s.username, s.password)
    client.reconnect_delay_set(2, 30)
    client.on_connect = self._on_connect
    client.on_disconnect = self._on_disconnect
    client.on_message = self._on_message
    self._client = client
    client.connect_async(s.broker, s.port, keepalive=s.keep_alive_sec)
    client.loop_start()
    # 启动 gpio-valve-watchdog 守护线程

def _on_connect(self, client, _userdata, _flags, reason_code, _properties=None) -> None:
    rc = getattr(reason_code, "value", reason_code)
    if rc != 0 or getattr(reason_code, "is_failure", False):
        logger.error("GPIO Broker 连接失败: %s", reason_code)
        self._link_up = False
        return
    with self._lock:
        self._link_up = True
        self._baseline_pending = True
    client.subscribe(self.settings.state_topic, qos=self.settings.qos)

def _on_disconnect(self, _client, _userdata, _flags, reason_code, _properties=None) -> None:
    self._link_up = False

def _on_message(self, _client, _userdata, message) -> None:
    if message.topic != self.settings.state_topic:
        return
    if message.retain:
        return
    gpios = parse_state_payload(message.payload, self.settings.channel_count)
    if gpios is None:
        self._log_invalid(message.payload)   # 10 秒限流的 WARNING
        return
    try:
        self._apply_report(gpios)
    except Exception:
        logger.exception("处理 GPIO 状态报文失败")
```

- clientId 必须带 pid 和随机后缀，否则多进程或快速重启时 Broker 会互踢（规范第 11 节陷阱 1）。
- 每次连上都要重新订阅（`clean_session=True` 不保留订阅），并置 `_baseline_pending = True`。
- `stop()`：置停止标志 → `client.disconnect()` → `client.loop_stop()` → 清空待确认指令，若有未完成的，用 WARNING 记下它们的 `messageId`。此时 core 已断开 SmartLab 连接，事件发不出去，所以停机时**不**补发失败事件（见 14 节）。
- 断线期间不上报遥测，**不得**用缓存值冒充实时数据。

### 7.9 失败原因码

`COMMAND_FAILED` 的 payload 固定为 `{"reason": <码>, "error": <中文说明>}`。

| reason | 触发 | control | simulation |
| --- | --- | --- | --- |
| `NOT_PHYSICAL_POINT` | control 收到非物理点 | ✓ | |
| `VIRTUAL_POINT_NOT_ATTACHED` | simulation 收到未挂载的点 | | ✓ |
| `UNSUPPORTED_COMMAND` | 命令名不是 open/close | ✓ | ✓ |
| `GPIO_MISMATCH` | 注入的 gpio 与点表不一致 | ✓ | |
| `CONTROLLER_OFFLINE` | 南向未连接或状态报文超时 | ✓ | |
| `PUBLISH_FAILED` | set 发布返回非成功 | ✓ | |
| `CONFIRM_TIMEOUT` | 超时未见目标状态 | ✓ | |
| `SUPERSEDED` | 同一点来了新指令，旧的被取代 | ✓ | ✓ |

### 7.10 禁止事项

- 禁止在 `execute()` 里 sleep、等确认或做同步 I/O。
- 禁止"set 发出即 COMPLETED"。
- 禁止在没有 `COMMAND_RUNNING` 的情况下直接发 `COMMAND_FAILED`。
- 禁止发 Setup 里没声明的事件名（后端会以"状态机没有可接收信号的接口"拒收）。
- 禁止持锁调用 handler。
- 禁止从点名推导 gpio。
- 禁止 retain 发布、禁止在启停时写控制器。
- 禁止用设备 `timestamp` 作为上报时间。
- 禁止离线时上报遥测或伪造状态。

---

## 8. 事件与遥测时序

### 8.1 为什么失败也必须先发 RUNNING

标准 CMD 生命周期只有 `SENT→RUNNING`、`RUNNING→COMPLETED`、`RUNNING→FAILED` 三条 Adapter 事件转移（`SystemExecutionContract.deviceCommandTransitionRequirements`，三条均为 REQUIRED）。状态机按 `fromState` 精确匹配（`StateMachineEngine.computeNextState`）：指令还在 `SENT` 时收到 `COMMAND_FAILED` 找不到转移，被**静默忽略**，要等 10 秒看门狗才判失败。先发 `COMMAND_RUNNING` 再发 `COMMAND_FAILED`，失败能立刻生效；即使建模人员额外加了 `SENT→FAILED` 也照样正确。

规范 9.3 的毫秒级回执要求也由此满足：`COMMAND_RUNNING` 总是在 `execute()` 内同步发出。

### 8.2 真机：打开成功

```text
系统 → smartlab/adapter/GpioValveAdapter-01/Valve03/command
       {"messageId":"m1","adapterName":"GpioValveAdapter-01","devicePoint":"Valve03",
        "commandName":"open","parameters":{},"timestamp":...}
core  注入 parameters = {"gpio": "3"}，按点表送 control
control → event      COMMAND_RUNNING     m1  {"commandName":"open","gpio":3}
control → 控制器     gpiocnt01/gpio/set  {"gpio":3,"val":1}   qos=1 retain=false
控制器 → state       {"gpios":[0,0,0,1,0,...],...}
control → telemetry  Valve03 {"isOpen": true}
control → event      VALVE_OPENED        m1  {"gpio":3,"source":"COMMAND"}
control → event      COMMAND_COMPLETED   m1  {"gpio":3,"isOpen":true}
```

core 实际发出的北向报文：

```json
{"formatType":"SINGLE","timestamp":1791596400123,"adapterName":"GpioValveAdapter-01","devicePoint":"Valve03","telemetryData":{"isOpen":true}}
{"timestamp":1791596400123,"adapterName":"GpioValveAdapter-01","devicePoint":"Valve03","eventName":"COMMAND_COMPLETED","messageId":"m1","payload":{"gpio":3,"isOpen":true}}
```

### 8.3 真机：其他场景

| 场景 | 该 messageId 的事件序列 | 其他输出 |
| --- | --- | --- |
| 正常开/关 | RUNNING → VALVE_OPENED/CLOSED → COMPLETED，按实测约 0.15~0.5 s | |
| 阀本来就开，又收到 open | RUNNING → VALVE_OPENED → COMPLETED | 仍下发 set；控制器若不对无变化的 set 补报，要等下一条周期报文，最长约 5 s；遥测仅在保活到期时发 |
| 变化补报丢失 | 同正常开/关，由下一条周期报文确认，最长约 5 s | |
| 控制器不响应 | RUNNING →（2/4/6 秒重发 set）→ 8 秒 FAILED(CONFIRM_TIMEOUT) | |
| 控制器离线 30 秒以上 / 启动后尚未收到首条报文 | RUNNING → FAILED(CONTROLLER_OFFLINE) | 不下发 |
| 同一点新指令 m2 取代 m1 | m1: RUNNING → FAILED(SUPERSEDED)；m2 正常走完 | |
| messageId 重复投递 | 第二次无任何事件 | 不下发 |
| 命令名未知 | RUNNING → FAILED(UNSUPPORTED_COMMAND) | 不下发 |
| 多路同时开 | 各自 RUNNING，同一条状态报文里各自 COMPLETED | |

| 无指令时的场景 | 输出 |
| --- | --- |
| 南向连接后的首条报文 | 16 点遥测 + 16 条 OP 事件（BASELINE，无 messageId） |
| 某一路被外部改变 | 该点遥测 + 该点 OP 事件（CHANGE） |
| 状态不变 | 控制器每 5 秒一条报文；Adapter 每点每 30 秒一条遥测，每 30 秒 16 条 OP 事件（RESYNC） |
| 控制器重启全部归零 | 原先开着的点各一条遥测 + VALVE_CLOSED（CHANGE） |

### 8.4 虚拟点：打开成功

```text
LEASE  leaseId=12, devicePoint=Valve03  →  core 回 GRANTED Valve03_sim_12，simulation.attach
系统 → Valve03_sim_12/command  open
core  按点表判为虚拟点，注入物理点 Valve03 的 gpio="3"，送 simulation（不会进 control）
sim  → event      COMMAND_RUNNING     m9           （execute 内同步发出）
sim  下一个周期（≤ intervalSec）：
     → telemetry  BATCH, items=[{"timestamp":T,"telemetryData":{"isOpen":true}}]
     → event      VALVE_OPENED        m9  {"source":"COMMAND"}
     → event      COMMAND_COMPLETED   m9  {"isOpen":true}
空闲：每 idleReportSec 一条 SINGLE 遥测
```

---

## 9. simulation.py 规格

### 9.1 接口

与规范第 9 节一致：`__init__(config)`、`start`、`stop`、`attach(virtual_point, physical_point, state=None)`、`detach`、`get_device_state`、`get_all_states`、`execute`、`set_data_handler`、`set_batch_data_handler`、`set_event_handler`。core 以 `SimulationImpl(runtime.extra.get("simulation", {}))` 构造。

### 9.2 每个虚拟阀的状态

`is_open: bool`（新挂载默认 False，即关）、`sim_ts`（单调递增的上报时间戳）、`last_idle_report`、`needs_op_sync`（挂载或恢复后为 True）、`active`（待完成的切换：messageId + 目标值，最多一条）、一把自己的锁。外层一把锁保护设备字典。

### 9.3 行为规则

- `attach`：同一 `virtual_point` 已存在则保留原实例（幂等）。`state` 中 `isOpen` 是布尔值时恢复它并置 `needs_op_sync`；其他内容一律忽略。原因：标准 core 在 `get_device_state` 返回空时会写入反应釜的默认快照 `{"current_temp":25.0,"current_pressure":0.10}`，恢复时必须能无害地忽略它，这样才能不改 core。
- `get_device_state`：已挂载返回 `{"isOpen": bool}`（非空字典），未挂载返回 None。
- `detach`：移除；有未完成切换就直接丢弃，不再发任何事件。重复 detach 无副作用。
- `execute`：
  1. messageId 重复 → 忽略。
  2. 发 `COMMAND_RUNNING`（同步）。
  3. 未挂载 → `COMMAND_FAILED(VIRTUAL_POINT_NOT_ATTACHED)`。
  4. 命令名不是 open/close → `COMMAND_FAILED(UNSUPPORTED_COMMAND)`。
  5. 登记 `active`；原有 active 的对旧 messageId 发 `COMMAND_FAILED(SUPERSEDED)`。
  6. **不得在 `execute` 内完成指令**，完成只能发生在仿真线程的下一个周期。
- 仿真线程每 `intervalSec` 对每个阀执行一步：
  - 有 `active`：`is_open = 目标`，`sim_ts = max(now_ms(), sim_ts + 1)`，发 1 项的 BATCH 遥测，然后 OP 事件（带 messageId，source=COMMAND），然后 `COMMAND_COMPLETED`。三者时间戳都是 `sim_ts`。重置空闲计时，清 `needs_op_sync`。
  - 无 `active` 且距上次上报 ≥ `idleReportSec`：`sim_ts = max(now_ms(), sim_ts + idleReportSec×1000)`，发 SINGLE 遥测；若 `needs_op_sync` 再发一条 OP 事件（无 messageId，source=BASELINE）。
  - 每个阀的一步包在 `try/except` 里，异常不能杀死线程。
  - 每步开始前确认该阀仍在字典里（防止 detach 后继续上报）。
- 绝不导入或调用 control，绝不连接控制器 Broker。

参考实现（已测试）：

```python
COMMAND_TARGETS = {"open": True, "close": False}

def execute(self, command: SystemCommand) -> None:
    point = command.device_point
    mid = command.message_id
    if not self._remember(mid):
        return
    with self._lock:
        dev = self._devices.get(point)
    self._event(point, "COMMAND_RUNNING", mid, {"commandName": command.command_name})
    if dev is None:
        self._event(point, "COMMAND_FAILED", mid,
                    {"reason": "VIRTUAL_POINT_NOT_ATTACHED", "error": f"虚拟点未挂载: {point}"})
        return
    target = COMMAND_TARGETS.get(command.command_name)
    if target is None:
        self._event(point, "COMMAND_FAILED", mid,
                    {"reason": "UNSUPPORTED_COMMAND", "error": f"不支持的指令: {command.command_name}"})
        return
    with dev.lock:
        superseded = dev.active
        dev.active = ActiveSwitch(mid, target)
    if superseded is not None:
        self._event(point, "COMMAND_FAILED", superseded.message_id,
                    {"reason": "SUPERSEDED", "error": f"被新指令 {mid} 取代"})

def _step(self, dev: VirtualValve) -> None:
    now = time.monotonic()
    with self._lock:
        if self._devices.get(dev.virtual_point) is not dev:
            return
    with dev.lock:
        active = dev.active
        dev.active = None
        if active is not None:
            dev.is_open = active.target
            dev.sim_ts = max(now_ms(), dev.sim_ts + 1)
            dev.last_idle_report = now
            dev.needs_op_sync = False
            ts, is_open, mode = dev.sim_ts, dev.is_open, "COMMAND"
        elif now - dev.last_idle_report >= self.idle_report_sec:
            dev.sim_ts = max(now_ms(), dev.sim_ts + int(self.idle_report_sec * 1000))
            dev.last_idle_report = now
            sync = dev.needs_op_sync
            dev.needs_op_sync = False
            ts, is_open, mode = dev.sim_ts, dev.is_open, ("IDLE_SYNC" if sync else "IDLE")
        else:
            return
    point = dev.virtual_point
    fields = {"raw_open": is_open}
    if mode == "COMMAND":
        if self._batch_data_handler is not None:
            self._batch_data_handler(DeviceBatchData(point, [{"timestamp": ts, "fields": fields}], now_ms()))
        self._emit(AdapterEvent(point, op_event_name(is_open), active.message_id, {"source": "COMMAND"}, ts))
        self._emit(AdapterEvent(point, "COMMAND_COMPLETED", active.message_id, {"isOpen": is_open}, ts))
        return
    if self._data_handler is not None:
        self._data_handler(DeviceData(point, fields, ts))
    if mode == "IDLE_SYNC":
        self._emit(AdapterEvent(point, op_event_name(is_open), None, {"source": "BASELINE"}, ts))
```

### 9.4 与规范第 9 节的对应关系

- 双轨遥测：工作态（切换）用 BATCH，空闲态用 SINGLE，规范 9.4 允许空闲单点上报。
- 时钟不逃逸：空闲时时间戳贴着墙上时钟走；没有时间压缩，所以不会飞到未来。
- 不假等待：没有 `time.sleep(duration)`，同一步内状态变化、事件和完成一起发生。
- 批次规模：一次切换只有 1 个数据点，低于规范 9.2 推荐的 10~100 点。9.2 的目的是防止大量小包冲垮后端；两位阀一次只有一个点，不存在这个风险，硬凑批次反而是伪造数据。
- 与 control 对称：同一命令发出同一套事件名，`messageId` 规则相同；不沿用 control 的超时、重发、在线判定。

---

## 10. core.py、main.py、requirements.txt

### 10.1 core.py

逐字复制 `fixture/adapter/反应釜/core.py`。它已经提供：注册（仅首次连接）、心跳、命令信封校验、internal 参数注入、物理点 → control / 非物理点 → simulation 的分流、LEASE/RELEASE 与 `leaseresult`、虚拟点通配订阅与重启恢复、`virtual_leases.json` 持久化、遥测的 attributeMapping 映射。

`_save_stored_leases` 里有一处反应釜默认快照（见 9.3），本 adapter 通过 simulation 忽略它，**不修改 core**。

### 10.2 main.py

```python
"""GpioValveAdapter 启动入口。"""

from core import main

if __name__ == "__main__":
    raise SystemExit(main())
```

### 10.3 requirements.txt

```text
paho-mqtt>=2.0,<3
```

control 使用 paho 2.x 的 `CallbackAPIVersion.VERSION2`。core 同时兼容 1.x/2.x，不受影响。当前开发机为 paho-mqtt 2.1.0。

---

## 11. 测试要求

全部用标准库 `unittest`。control 测试不连网：把 `control._client` 换成记录 `publish` 调用的假客户端，置 `control._link_up = True`，直接调用 `control._on_message(None, None, msg)` 喂报文（`msg` 需有 `topic`、`payload`、`retain` 属性），超时与重发通过改写 `PendingCommand.deadline` / `next_resend` 后手动调用 `_watchdog_tick()` 触发，不要 sleep 等真实超时。测试配置用较小的 `confirmTimeoutSec=1.0`、`resendIntervalSec=0.4`、`stateTimeoutSec=3.0`、`opResyncSec=0`。

### 11.1 test_control.py（必须全部通过）

| 编号 | 用例 | 断言 |
| --- | --- | --- |
| C1 | 状态报文解析 | 合法 16 项通过；15 项、含 2、含 `true`、含 `"1"`、非 JSON、顶层为数组均返回 None |
| C2 | 构造校验 | gpio 重复、gpio=16、缺 gpio、stateTopic 含 `+` 均抛 ValueError |
| C3 | 首条报文 | 16 条遥测，`raw_open` 为 bool，时间戳 > 1e12；16 条 OP 事件，无 messageId，开着的点为 VALVE_OPENED |
| C4 | 状态不变 / 单点变化 | 不变时无输出；仅 gpio9 变 1 时只有 Valve09 的遥测和 VALVE_OPENED |
| C5 | 打开成功 | execute 后只有 RUNNING，下发 `('gpiocnt01/gpio/set','{"gpio":3,"val":1}',1,False)`；报文到达后该 messageId 序列为 RUNNING, VALVE_OPENED, COMPLETED |
| C6 | 已开再开 | 仍下发 1 次；下一条报文后 RUNNING, VALVE_OPENED, COMPLETED |
| C7 | 未变报文不完成 | 目标未达到的报文到达后仍只有 RUNNING |
| C8 | 重发与超时 | 到重发时刻再发一条相同 set；到 deadline 后 FAILED(CONFIRM_TIMEOUT)，待确认表为空 |
| C9 | 未收到过报文 | RUNNING, FAILED(CONTROLLER_OFFLINE)，无下发 |
| C10 | 报文过期 | `_last_report_mono` 早于 stateTimeoutSec → CONTROLLER_OFFLINE，无下发 |
| C11 | 南向断开 | `_link_up=False` → CONTROLLER_OFFLINE，无下发 |
| C12 | 取代 | 旧 messageId: RUNNING, FAILED(SUPERSEDED)；新的正常完成 |
| C13 | 重复 messageId | 第二次无事件、无下发 |
| C14 | 虚拟点误送 | `Valve03_sim_7` → RUNNING, FAILED(NOT_PHYSICAL_POINT)，无下发 |
| C15 | 未知命令 / gpio 不一致 | UNSUPPORTED_COMMAND；`gpio="4"` 发往 Valve03 → GPIO_MISMATCH；均无下发 |
| C16 | 多路并发 | Valve00 与 Valve15 同时待确认，同一条报文后都 COMPLETED |
| C17 | RESYNC 与重连 BASELINE | 到 opResync 周期发 16 条 source=RESYNC；置 `_baseline_pending` 后发 16 条 source=BASELINE |
| C18 | 发布失败 | RUNNING, FAILED(PUBLISH_FAILED)，待确认表为空 |
| C19 | 确认与发布失败竞态 | 假客户端在 `publish` 内先喂一条已达目标的报文再返回失败码：序列为 RUNNING, VALVE_OPENED, COMPLETED，**没有** FAILED |
| C20 | 保留消息 | retain=True 的报文不产生任何输出，`_last_report_mono` 仍为 None |
| C21 | 非法报文 | 长度不对的报文不产生输出，不刷新在线时间 |

### 11.2 test_simulation.py

| 编号 | 用例 | 断言 |
| --- | --- | --- |
| S1 | 异步完成 | execute 后立即只有 RUNNING；start 后 3 秒内出现 COMPLETED；序列 RUNNING, VALVE_OPENED, COMPLETED；BATCH 1 项 `{"raw_open": True}`；状态 `{"isOpen": True}` |
| S2 | 空闲上报与恢复同步 | 以 `{"isOpen": True}` 恢复后，空闲 SINGLE 遥测时间戳严格递增；恰好一条 VALVE_OPENED（无 messageId） |
| S3 | 忽略外来快照 | 以反应釜快照 attach 后状态为 `{"isOpen": False}`；重复 attach 不新增；重复 detach 无异常 |
| S4 | 未挂载 / 未知命令 | 未挂载：RUNNING, FAILED；未知命令：UNSUPPORTED_COMMAND |
| S5 | 取代 | 同一虚拟点连续两条命令，旧的 FAILED(SUPERSEDED) |

### 11.3 test_core_integration.py

用 fixture 测试中的 `FakeClient` 写法：`core._client = FakeClient(); core._connected = True`，`leases_file` 指向临时文件。

| 编号 | 用例 | 断言 |
| --- | --- | --- |
| K1 | 物理点命令 | 送到 control，`parameters == {"gpio": "3"}` |
| K2 | 虚拟点命令 | `Valve03_sim_7` 不进 control；simulation 已自动挂载该点 |
| K3 | 遥测信封 | control 发出 `DeviceData("Valve03", {"raw_open": True}, ...)` 后，core 发布到 `smartlab/adapter/GpioValveAdapter-01/Valve03/telemetry`，`formatType=SINGLE`，`telemetryData == {"isOpen": True}` |
| K4 | 契约加载 | `AdapterSetup.load` 得到 16 个点，Valve00..Valve15 的 gpio 为 "0".."15" |

### 11.4 假控制器 fake_gpio_device.py

联调与自测工具，时序模仿实测的 gpiocnt01：订阅 `{prefix}/gpio/set`，维护 16 路状态，默认每 5 秒上报一次，set 引起状态变化时立即补报。值未变化的 set 默认**不**补报：真机是否补报尚未确认（第 13 节），按更保守的情况模拟，需要时用 `report_unchanged=True` 打开。另外支持：
- `ignore_set=True`：收到 set 不改状态，用于测超时。
- `mute_periodic=True`：停发周期报文、保留变化补报，用于模拟周期报文丢失。
- 记录收到的 set 和其中带 retain 的条数。

```python
"""假 GPIO 控制器：订阅 set 主题，维护 16 路状态，周期上报，状态变化时立即补报。仅用于联调测试。

时序按 2026-10-08 实测报文：周期 5 秒，变化后立即补报。值未变化的 set 默认不补报
（真机行为未确认，按更保守的情况模拟），此时确认要等下一条周期报文。
"""

from __future__ import annotations

import argparse
import json
import threading
import time

import paho.mqtt.client as mqtt


class FakeGpioDevice:
    def __init__(self, broker: str, port: int, username: str, password: str, prefix: str,
                 channels: int = 16, period: float = 5.0, ignore_set: bool = False,
                 report_unchanged: bool = False) -> None:
        self.state_topic = f"{prefix}/gpio/state"
        self.set_topic = f"{prefix}/gpio/set"
        self.gpios = [0] * channels
        self.period = period
        self.ignore_set = ignore_set
        self.report_unchanged = report_unchanged
        self.mute_periodic = False
        self.retained_sets = 0
        self.received_sets: list[dict] = []
        self._boot = time.monotonic()
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self.client = mqtt.Client(callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
                                  client_id=f"fake_gpio_{int(time.time() * 1000)}")
        if username:
            self.client.username_pw_set(username, password)
        self.client.on_connect = lambda c, *_: c.subscribe(self.set_topic, qos=1)
        self.client.on_message = self._on_set
        self.client.connect(broker, port, keepalive=30)

    def _on_set(self, _client, _userdata, message) -> None:
        if message.retain:
            self.retained_sets += 1
        body = json.loads(message.payload)
        self.received_sets.append(body)
        if self.ignore_set:
            return
        gpio, value = int(body["gpio"]), 1 if int(body["val"]) else 0
        with self._lock:
            changed = self.gpios[gpio] != value
            self.gpios[gpio] = value
        if changed or self.report_unchanged:
            self.publish_state()

    def publish_state(self) -> None:
        with self._lock:
            body = {"gpios": list(self.gpios), "timestamp": int((time.monotonic() - self._boot) * 1000), "rssi": -41}
        self.client.publish(self.state_topic, json.dumps(body), qos=0)

    def start(self) -> None:
        self.client.loop_start()
        threading.Thread(target=self._loop, daemon=True).start()

    def _loop(self) -> None:
        while not self._stop.wait(self.period):
            if not self.mute_periodic:
                self.publish_state()

    def stop(self) -> None:
        self._stop.set()
        self.client.disconnect()
        self.client.loop_stop()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--broker", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=1883)
    parser.add_argument("--username", default="")
    parser.add_argument("--password", default="")
    parser.add_argument("--prefix", default="gpiotest")
    parser.add_argument("--period", type=float, default=5.0)
    parser.add_argument("--report-unchanged", action="store_true")
    args = parser.parse_args()
    device = FakeGpioDevice(args.broker, args.port, args.username, args.password, args.prefix,
                            period=args.period, report_unchanged=args.report_unchanged)
    device.start()
    print(f"fake device on {device.state_topic} / {device.set_topic}; Ctrl+C to stop")
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        device.stop()
```

**假控制器的前缀必须不是 `gpiocnt01`。** 真控制器在同一 Broker 上时，两个"设备"往同一个状态主题发报文会让 Adapter 状态来回跳。

### 11.5 真实 Broker 冒烟（不接 SmartLab）

写一个脚本：以 `prefix=gpiotest_tmp` 启动假控制器（周期 0.5 秒），用相同主题构造 `ControlImpl`（`confirmTimeoutSec=2.0`、`resendIntervalSec=0.8`、`stateTimeoutSec=3.0`），挂上收集事件的 handler，依次验证：

1. 5 秒内收到 16 条首轮遥测。
2. Valve07 open：RUNNING, VALVE_OPENED, COMPLETED。原型实测约 50 ms。
3. Valve07 close：RUNNING, VALVE_CLOSED, COMPLETED。
4. 假控制器 `ignore_set=True` 后 Valve08 open：RUNNING, FAILED(CONFIRM_TIMEOUT)，假控制器共收到 3 次 set（0 / 0.8 / 1.6 秒）。
5. 停掉假控制器，等 3.6 秒后 Valve09 open：RUNNING, FAILED(CONTROLLER_OFFLINE)。
6. 假控制器统计到的 retain set 为 0；收到的 set 依次为 `{"gpio":7,"val":1}`、`{"gpio":7,"val":0}`、`{"gpio":8,"val":1}`。

这个脚本不启动 core，因此不会向 `smartlab/adapter/register` 发注册，也不会出现在系统的待审核注册列表里。

### 11.6 按实测时序验证默认配置

用 `runtime.ini` 的 `[gpio]` 默认值（confirm 8 s、resend 2 s、stateTimeout 30 s），只把两个主题换成 `gpiotest_rt/...`，假控制器周期 5 s、不对无变化的 set 补报。依次验证（括号内为原型实测）：

| 步骤 | 期望 |
| --- | --- |
| 启动后立即对 Valve02 下发 open | RUNNING, FAILED(CONTROLLER_OFFLINE)，不下发（启动后首条报文前的窗口，见第 14 节） |
| 等首轮报文 | 约 5 s 内收到 16 条遥测（5.02 s） |
| Valve00 open（状态变化） | RUNNING, VALVE_OPENED, COMPLETED（0.02 s） |
| Valve00 再次 open（无变化） | RUNNING, VALVE_OPENED, COMPLETED，靠下一条周期报文确认（4.976 s，必须 < confirmTimeoutSec） |
| Valve00 close | RUNNING, VALVE_CLOSED, COMPLETED（0.02 s） |
| 假控制器停发周期报文 25 s 后，Valve03 open | **不得**判离线：RUNNING, VALVE_OPENED, COMPLETED（0.02 s） |
| 假控制器 `ignore_set=True` 后 Valve01 open | RUNNING, FAILED(CONFIRM_TIMEOUT)（8.15 s），假控制器共收到 4 次 set |

旧默认值（confirm 5 s、stateTimeout 10 s）下，第 4 步会和周期报文赛跑而大概率超时，第 6 步会以 CONTROLLER_OFFLINE 直接失败。这就是两项默认值必须调整的依据。

---

## 12. 系统侧接入与建模（给建模人员）

### 12.1 注册

启动 Adapter 后，系统"待确认注册"里出现 `GpioValveAdapter-01`（1 个类别、16 个点），确认注册。

### 12.2 设备模型（一个模型供 16 路阀共用）

在设备模型编辑器中选择 Adapter `GpioValveAdapter-01`、类别 `SolenoidValve`，系统自动生成 `adapterContract`。其余部分按下面填写。

属性：

```json
[{"attributeName": "isOpen", "displayName": "阀门开闭", "valueKind": "DISCRETE", "dataType": "BOOLEAN"}]
```

`adapterContract.telemetry.attributesMapping`：

```json
[{"adapterAttrName": "isOpen", "modelAttributeName": "isOpen"}]
```

能力（两个都不是终止能力，`abortCapabilityName` 必须显式为 `null`）：

```json
[
  {"capabilityName": "openValve", "adapterCommandName": "open", "displayName": "打开阀门",
   "parameters": [], "parameterMapping": [], "isAbort": false, "abortCapabilityName": null},
  {"capabilityName": "closeValve", "adapterCommandName": "close", "displayName": "关闭阀门",
   "parameters": [], "parameterMapping": [], "isAbort": false, "abortCapabilityName": null}
]
```

OP 状态空间（每个状态的 `onEntry` 必须且只能有一个 `OP_STATE` 广播，否则保存失败）：

```json
{"regions": [{
  "regionName": "valveState",
  "regionType": "OPERATIONAL",
  "initialStateName": "CLOSED",
  "states": [
    {"stateName": "CLOSED", "onEntry": [{"actionName": "SEND", "payload": {"interfaceName": "Interface_state_out", "signalName": "OP_STATE"}}]},
    {"stateName": "OPEN",   "onEntry": [{"actionName": "SEND", "payload": {"interfaceName": "Interface_state_out", "signalName": "OP_STATE"}}]}
  ]
}]}
```

转移（前三条是保存时强制要求的 CMD 转移）：

```json
[
  {"stateSpace": "CMD", "fromStateName": "SENT",    "toStateName": "RUNNING",   "trigger": {"interfaceName": "Interface_adapter_in", "signalName": "COMMAND_RUNNING"}},
  {"stateSpace": "CMD", "fromStateName": "RUNNING", "toStateName": "COMPLETED", "trigger": {"interfaceName": "Interface_adapter_in", "signalName": "COMMAND_COMPLETED"}},
  {"stateSpace": "CMD", "fromStateName": "RUNNING", "toStateName": "FAILED",    "trigger": {"interfaceName": "Interface_adapter_in", "signalName": "COMMAND_FAILED"}},
  {"stateSpace": "OP", "regionName": "valveState", "fromStateName": "CLOSED", "toStateName": "OPEN",   "trigger": {"interfaceName": "Interface_adapter_in", "signalName": "VALVE_OPENED"}},
  {"stateSpace": "OP", "regionName": "valveState", "fromStateName": "OPEN",   "toStateName": "CLOSED", "trigger": {"interfaceName": "Interface_adapter_in", "signalName": "VALVE_CLOSED"}}
]
```

说明：

- 保存时后端会按 `adapterName` + `categoryName` 重新生成 `adapterContract`，只保留你提交的 `attributesMapping`，所以契约部分不需要手写。
- 保存校验要求 CMD 转移只能引用 cmdEvents、OP 转移只能引用 opEvents，上面的写法已满足。
- 不配置内置约束（布尔属性不能用，见 2.2）。
- 上述 CMD 转移满足流程模拟（画布走图）对"唯一的 SENT→RUNNING、RUNNING→COMPLETED Adapter 事件"的要求，不需要另外配置。

### 12.3 实例

建 16 个物理实例，分别绑定 `GpioValveAdapter-01` 的 `Valve00` … `Valve15`。同一设备点不能绑定两个使用中的实例。

### 12.4 数据集

**每个实例必须绑定至少一个数据集**。配置了属性映射却没有数据集时，后端对该实例的每条遥测都会抛"设备实例缺少数据集，拒绝产生不可追溯的遥测状态"。

---

## 13. 真机联调步骤

上报周期（5 s）和变化即补报已由 2026-10-08 报文确认（1.1.1），不需要再测。联调前先用 MQTT 调试工具补齐下面三项**设备行为确认**，结果记入联调记录：

1. **紧凑写法是否被接受。** 手动向 `gpiocnt01/gpio/set` 发 `{"gpio":0,"val":1}`（无任何空格，就是 Adapter 发出的字节），确认 GPIO 0 动作、并收到补报。不接受时只改 `_publish_set` 的序列化方式，例如换成 `json.dumps(...)` 的默认分隔符，别的不动。
2. **无变化的 set 是否补报。** GPIO 0 已开时再发一次 `{"gpio":0,"val":1}`，看是否立即多出一条非周期报文（设备 timestamp 尾数不是 …1124）。
   - 补报：已在目标状态的指令也能在 0.5 s 内确认。
   - 不补报：最长约 5 s 确认，已被 `confirmTimeoutSec = 8` 覆盖，不需要改。
3. **25 秒空档是否真实。** 只读订阅（如 `mosquitto_sub -h <broker> -u <user> -P <pwd> -t gpiocnt01/gpio/state -F "%r %p"`，第一列为 retain 标志）挂 10 分钟，统计设备 timestamp 相邻差值。
   - 只有 5000：之前那段空档是复制时省略的。
   - 偶尔出现 10000 以上：确实在丢 QoS 0 报文，`stateTimeoutSec = 30` 能扛住；但如果频繁出现 30000 以上，先查控制器 WiFi，不要继续调大超时。
   - 同一订阅里，订阅瞬间收到一条第一列为 `1` 的报文，说明设备用了 retain。Adapter 会忽略它，只是首轮 BASELINE 晚一个周期。

然后：

4. 填写 `runtime.ini` 的 `[gpio]` Broker 与口令，确认与 `[smartlab]` 是否同一 Broker。
5. 先只跑 control 冒烟（11.6 的脚本把前缀换成 `gpiocnt01`，**只测一路、现场有人看着**；不要跑 `ignore_set`、`mute_periodic` 这类只对假控制器有效的步骤）。
6. 再完整启动 `python main.py`，完成第 12 节的注册、建模、实例、数据集。
7. 在人工控制台逐路执行 openValve / closeValve，核对：实物动作、控制台 CMD 状态到 COMPLETED、OP 状态切换、`isOpen` 属性与数据集记录。
8. 断开控制器电源：30 秒内日志出现"GPIO 控制器离线"。离线判定生效前下发的指令在 8 秒后以 CONFIRM_TIMEOUT 失败，生效后立即以 CONTROLLER_OFFLINE 失败。恢复供电后自动上线，OP 状态与实物一致。
9. 用工作流跑一次 TEMPORARY 流程模拟和一次 LEASE 虚拟执行，确认虚拟指令不会让实物动作。

---

## 14. 已知限制（不是缺陷，实现时不要试图在 Adapter 内"修"）

| 限制 | 说明 |
| --- | --- |
| 确认窗口内不可终止 | 见 2.4 |
| Adapter 停机时的在途指令 | core 先断开 SmartLab 再停 control，在途指令的终态发不出去，系统侧该指令停在 RUNNING，需人工控制台复位（`MANUAL_EXECUTE_RESET`）。窗口最长 `confirmTimeoutSec` |
| SmartLab 断线期间完成的指令 | core 在断线期间丢弃出站报文（规范要求），完成事件丢失，处理同上。遥测保活和 OP 重同步会在重连后自动纠正属性和 OP 状态 |
| 启动后首条报文前的窗口 | Adapter 启动到收到第一条状态报文之间（最长一个周期，约 5 s），控制器视为离线，指令以 CONTROLLER_OFFLINE 失败。系统注册确认本身就需要人工操作，实际很难在这个窗口内下发指令 |
| 启动时首轮 BASELINE 可能丢失 | control 先于 SmartLab 连接就绪时首轮事件被 core 丢弃，由 `opResyncSec`（30 秒）兜底 |
| 已在目标状态的指令确认较慢 | 若控制器不对无变化的 set 补报（第 13 节第 2 步），这类指令要等下一条周期报文，最长约 5 s。不用缓存状态提前完成，是为了保证 COMPLETED 一定对应控制器回报的状态 |
| 一个进程一台控制器 | 多台控制器各开一个进程、各自 adapterName |
| 进程内去重 | messageId 去重只覆盖最近 256 条且不跨重启 |

---

## 15. 验收清单

- [ ] `core.py` 与 `fixture/adapter/反应釜/core.py` 逐字一致（`git diff --no-index` 无差异）。
- [ ] `adapterSetup.ini`、`runtime.ini` 与第 4、5 节一致。
- [ ] 第 11 节 C1~C21、S1~S5、K1~K4 全部通过；11.5 冒烟六项、11.6 实测时序七项全部符合。
- [ ] 代码中只出现 5 个事件名：COMMAND_RUNNING、COMMAND_COMPLETED、COMMAND_FAILED、VALVE_OPENED、VALVE_CLOSED。
- [ ] 任何 COMMAND_FAILED 之前都有同一 messageId 的 COMMAND_RUNNING；每个 messageId 至多一个终态。
- [ ] 遥测 `raw_open` 为 bool，时间戳为本机纪元毫秒。
- [ ] set 报文恰为 `{"gpio":<int>,"val":<0|1>}`，retain=False；启停与重连时不写控制器。
- [ ] control 使用独立 paho 客户端，clientId 带 pid 与随机后缀，重连后重新订阅。
- [ ] control 持锁期间不调用 handler；`execute` 不阻塞。
- [ ] simulation 不导入 control、不连控制器 Broker；指令只在仿真线程完成。
- [ ] 第 13 节设备行为确认 1~3 项与真机联调 4~9 步完成并记录结果。
