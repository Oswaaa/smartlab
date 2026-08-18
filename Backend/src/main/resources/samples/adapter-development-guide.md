# SmartLab Adapter 设计与生成指导

## 1. 文档用途

本文档是 SmartLab Adapter 的标准设计规范，也是后续人工或自动生成 Adapter
代码时的直接输入。生成器应先根据设备说明书、厂商控制程序和通信协议识别设备能力，
再按照本文规定生成目录、模块、配置和测试。

本文中的“必须”表示所有 Adapter 都应遵守的约束；“可选”表示仅在对应设备需要时生成。

## 2. 标准架构

Adapter 采用四个逻辑模块：

1. `runtime.py`：进程生命周期和公共可靠性能力。
2. `northbound.py`：与 SmartLab 系统交换标准消息。
3. `core.py` 或 `core/`：设备语义、命令编排和双向数据翻译。
4. `southbound.py` 或 `southbound/`：与设备、厂商 SDK 或厂商控制程序交互。

标准数据流如下：

```text
SmartLab
  ⇅
northbound
  ⇅ 统一内部消息
core
  ⇅ 设备操作和设备原始数据
southbound
  ⇅
设备 / 厂商 SDK / 厂商控制程序
```

四个模块是逻辑边界，不要求四个独立进程。通常整个 Adapter 是一个进程；如果厂商
控制程序不能嵌入，则 Adapter 与厂商控制程序分别运行，通过 `southbound` 通信。

## 3. 标准目录

简单 Adapter 使用以下结构：

```text
<adapter-name>/
├── runtime.py
├── northbound.py
├── core.py
├── southbound.py
├── adapterconfig.ini
├── setup.ini
├── requirements.txt
├── README.md
└── tests/
    ├── test_config.py
    ├── test_core.py
    ├── test_southbound.py
    └── test_runtime.py
```

复杂 Adapter 可以把 `core.py`、`southbound.py` 扩展成同名包，但不能同时保留
同名文件和目录：

```text
<adapter-name>/
├── runtime.py
├── northbound.py
├── core/
│   ├── __init__.py
│   ├── models.py
│   ├── command_service.py
│   ├── command_translator.py
│   ├── telemetry_translator.py
│   ├── event_translator.py
│   └── command_tracker.py
├── southbound/
│   ├── __init__.py
│   ├── driver.py
│   ├── protocol_client.py
│   └── errors.py
├── vendor/                       # 可选：厂商源码、SDK 包装或二进制文件
├── adapterconfig.ini
├── setup.ini
├── requirements.txt
├── README.md
└── tests/
```

`vendor/` 不是 `core/`。即使 Adapter 直接修改了厂商控制程序，也应把厂商相关代码
保留在 `vendor/` 或独立子项目中，并通过 `southbound` 暴露统一设备接口。

## 4. 模块设计

### 4.1 `runtime.py`

#### 职责

- 加载并校验 `adapterconfig.ini` 和 `setup.ini`。
- 创建 `northbound`、`core` 和 `southbound` 实例并注入依赖。
- 按顺序启动、停止各模块。
- 管理进程信号、工作线程、定时任务和优雅退出。
- 触发 Adapter 注册、心跳、重连和命令超时扫描。
- 汇总健康状态，但不解释具体设备业务。

#### 推荐入口

```python
class AdapterRuntime:
    def start(self) -> None: ...
    def stop(self) -> None: ...
    def run_forever(self) -> None: ...
```

#### 允许依赖

- `northbound`
- `core`
- `southbound`
- 配置、日志和线程库

#### 禁止承担

- MQTT 消息字段到设备寄存器的转换。
- Modbus、串口或厂商 SDK 的具体调用。
- 设备命令的业务判断。

## 4.2 `northbound.py`

#### 职责

- 连接 SmartLab 使用的 MQTT Broker。
- 订阅系统命令 Topic。
- 解析并校验系统消息信封。
- 发布 Adapter 注册、心跳、遥测、事件和命令状态。
- 处理 QoS、重连、重新订阅和发布结果。
- 把系统消息转换为统一内部消息后交给 `core`。

#### 推荐接口

```python
class NorthboundClient:
    def connect(self) -> None: ...
    def subscribe_commands(self, handler) -> None: ...
    def publish_registration(self, raw_config: str) -> None: ...
    def publish_heartbeat(self, status: str) -> None: ...
    def publish_telemetry(self, message) -> None: ...
    def publish_event(self, message) -> None: ...
    def close(self) -> None: ...
```

#### 允许知道

- SmartLab Topic 和消息格式。
- `adapterName`、`devicePoint`、`messageId`、时间戳和 QoS。
- SmartLab 协议要求的注册、心跳、遥测和事件信封。

#### 禁止知道

- PLC 寄存器地址。
- Modbus 功能码。
- 厂商 HTTP 路径、DLL 函数或串口帧。
- `setCooling` 为什么对应 `MW20=1` 一类设备语义。

## 4.3 `core.py` 或 `core/`

#### 职责

- 校验命令是否属于目标设备模板。
- 校验公开参数类型，并从设备点配置注入 `internal=true` 参数。
- 把 SmartLab 命令翻译为与通信协议无关的设备操作。
- 把设备原始状态翻译为模板属性、命令事件和运行事件。
- 管理命令关联、幂等、状态转换、确认条件和超时结果。
- 承担设备安全规则，例如“只有手动模式才能启动冷却”。

#### 推荐接口

```python
class AdapterCore:
    def handle_system_command(self, command) -> list: ...
    def handle_device_data(self, data) -> list: ...
    def handle_device_event(self, event) -> list: ...
    def expire_commands(self, now_ms: int) -> list: ...
```

返回值应是待执行设备操作或待发布系统消息，不应在纯翻译函数内直接进行网络 I/O。

#### 示例

```text
系统命令：
setCooling(enabled=true)

core 输出的设备操作：
SetCooling(enabled=true)

southbound 的 Modbus 实现：
write_register(address=20, value=1)
```

`core` 表达“设置冷却”，`southbound` 表达“怎样让具体设备设置冷却”。

#### 何时使用文件夹

满足以下任一条件时使用 `core/`：

- 有多个设备模板。
- 命令存在多步骤编排。
- 需要等待设备反馈确认命令完成。
- 需要维护状态机、安全联锁或复杂事件推导。
- 单个 `core.py` 超过约 500 行且职责已经可以独立测试。

## 4.4 `southbound.py` 或 `southbound/`

#### 职责

- 建立、维护和关闭设备侧连接。
- 执行 `core` 给出的设备操作。
- 读取、订阅或接收设备原始数据。
- 将协议错误统一转换为 Adapter 可识别的错误。
- 提供设备连接健康检查。

#### 推荐接口

```python
class SouthboundDriver:
    def connect(self) -> None: ...
    def execute(self, operation): ...
    def poll(self): ...
    def set_data_handler(self, handler) -> None: ...
    def health_check(self): ...
    def close(self) -> None: ...
```

设备只支持主动上报时，`poll()` 可以返回空结果；设备只支持轮询时，
`set_data_handler()` 可以不启用，但接口语义应保持一致。

#### 三种实现方式

1. **直接协议型**：在 `southbound` 中实现 Modbus、串口、TCP、设备 MQTT 等协议。
2. **外部程序代理型**：通过 HTTP、TCP、MQTT、命名管道或子进程与未修改的厂商
   控制程序交互。
3. **厂商代码嵌入型**：修改或包装厂商源码、DLL 或 SDK，在 `southbound` 中调用。

直接使用 Modbus 时仍然需要 `southbound`，因为连接、寄存器读写、重试和协议异常
都属于设备通信，而不是设备业务语义。

#### 禁止知道

- SmartLab MQTT Topic。
- Adapter 注册报文格式。
- SmartLab 命令生命周期消息格式。
- 用户界面如何展示命令参数。

## 5. 模块间统一对象

模块之间不得通过未经约束的任意 `dict` 长期传递数据。生成 Adapter 时至少定义以下
内部对象，可以使用 `dataclass`、类型化字典或对应语言的数据类：

- `SystemCommand`：`messageId`、`devicePoint`、`commandName`、`parameters`、
  `timestamp`。
- `DeviceOperation`：设备语义操作名、目标设备点、参数、关联 `messageId`。
- `DeviceData`：设备点、采集时间、原始字段。
- `Telemetry`：设备点、模型属性和值、时间戳。
- `AdapterEvent`：设备点、事件名、关联命令、载荷、时间戳。
- `DriverError`：错误类别、是否可重试、设备信息和安全日志消息。

`northbound` 与 `southbound` 不能互相直接调用。正常编排路径由 `runtime` 建立，
所有双向数据都经过 `core`。

## 6. 配置文件边界

### 6.1 `adapterconfig.ini` 或对应 JSON

这是 Adapter 对 SmartLab 暴露的能力契约，包含：

- Adapter 名称和规范版本。
- 设备模板、属性、命令、参数和事件。
- Adapter 管理的设备点。
- 模型属性到设备原始字段的映射。
- `internal=true` 参数所需的设备点固定字段。

Adapter 注册时必须读取该文件完整原文，并放入
`AdapterRegisterRequest.rawConfigContent`。该文件不能包含密码。

### 6.2 `setup.ini`

这是 Adapter 本地部署配置，不发送给 SmartLab，包含：

- Broker 地址、账号的安全引用、Client ID、QoS。
- 设备地址、串口、Modbus、厂商程序 URL 或进程路径。
- 心跳、轮询、命令确认和重试参数。
- 日志级别和运行参数。

密码优先通过环境变量或密钥文件引用，不应直接提交到仓库。

## 7. `adapter-config.schema.json` 的作用

`adapter-config.schema.json` 是 JSON Schema Draft 7 文档，用于描述 Adapter
开发者应提交的**原始能力配置**结构。它对应 `adapterSetup.json`，同时通过扩展字段
说明等价 INI 的写法。它适合用于：

- 文档和代码生成依据。
- 编辑器补全和静态提示。
- 未来接入标准 JSON Schema 校验器。
- 保持 JSON 示例与 INI 示例表达同一份契约。

它不是 Adapter 注册消息本身的 schema。注册消息外层仍然是：

```json
{
  "adapterName": "TestHeatPressureAdapter-01",
  "rawConfigFormat": "JSON",
  "rawConfigContent": "<adapterSetup.json 的完整原文>",
  "timestamp": 1719892800
}
```

### 7.1 `x-iniSections`

`x-iniSections` 是 SmartLab 自定义的说明性扩展，不是 JSON Schema 标准关键字。
它描述 JSON 字段在 INI 中应落到哪个 section。例如：

```json
"template": "[deviceTemplates.<templateName>]"
```

表示 JSON 的每个 `deviceTemplates[]` 元素在 INI 中对应：

```ini
[deviceTemplates.ReactorUnitTemplate]
```

其中 JSON 文件里的 `\u003c` 和 `\u003e` 只是 `<` 和 `>` 的 Unicode 转义，
反序列化后就是 `<templateName>`，没有额外运行时含义。

### 7.2 `x-iniValueGrammar`

`x-iniValueGrammar` 说明 INI 配置项值的自定义语法：

```ini
temperature = [DOUBLE][当前温度]
index = [INTEGER][设备点内部编号][internal=true][sourceField=index]
COMMAND_RUNNING = 指令执行中
```

- `attribute`：属性值为 `[数据类型][描述]`。
- `parameter`：参数值在数据类型和描述后，可以追加 `internal` 和 `sourceField`。
- `event`：key 是事件名，value 是事件描述。
- `scalar`：普通字符串或可被后端识别的标量。

这些 `x-` 字段即使交给标准 JSON Schema 校验器也会作为未知扩展被忽略；若生成器
需要支持 INI，必须主动读取这些扩展，或者按照本文和 `adapterSetup.ini` 实现。

### 7.3 当前后端是否使用该 schema

当前后端**没有加载或执行** `adapter-config.schema.json`。仓库中没有业务代码引用
该资源文件。实际解析和校验逻辑硬编码在
`AdapterManifestService`：

1. `parseRawConfig()` 根据注册报文的 `rawConfigFormat` 选择 JSON 或 INI 解析器。
2. INI section 的正则、允许字段和方括号语法由 Java 代码直接实现。
3. `normalize()` 把原始 flat 配置转换为后端内部 manifest。
4. `validate()` 校验模板、设备点、属性映射、命令参数和事件。
5. `AdapterIndexService` 在预览和保存注册信息时调用该服务。
6. `MqttAdapterMessagingService` 收到注册 Topic 消息后触发上述解析流程。

因此目前 `adapter-config.schema.json` 是“规范和工具元数据”，
`AdapterManifestService` 才是运行时事实来源。修改 schema 不会自动改变后端行为；
修改格式时必须同步修改 Java 解析器、测试、JSON/INI 示例和本文档。

## 8. 原始配置与后端内部结构

当前 Adapter 原始 JSON 必须使用 flat 结构：

```json
{
  "specVersion": "smartlab.adapter.config.v1",
  "adapterName": "adapter1",
  "adapterDescription": "对该 adapter 的描述",
  "rawConfigFormat": "JSON",
  "deviceTemplates": [
    {
      "templateName": "Reactor_Basic",
      "categoryName": "Reactor",
      "attributes": [],
      "commands": [],
      "events": {
        "cmdEvents": [],
        "opEvents": []
      }
    }
  ],
  "devicePoints": [
    {
      "devicePoint": "Reactor_01",
      "templateName": "Reactor_Basic",
      "index": 1
    }
  ]
}
```

后端解析后才会生成：

```text
deviceCategories[]
  ├── categoryName
  ├── deviceTemplate
  └── devicePoints[]
```

所以包含 `registerMeta` 和嵌套 `deviceCategories` 的对象是后端
`parsedConfig`/manifest 结构，不是当前 Adapter 应放进 `rawConfigContent` 的结构。
`registeredAt` 应来自注册消息时间戳或后端接收流程，不应写进 Adapter 原始配置。

如果把只有 `deviceCategories` 的对象作为当前原始配置提交，
`AdapterManifestService` 会因为缺少顶层 `deviceTemplates` 而拒绝。

## 9. Adapter 生成流程

生成器必须按以下顺序工作：

1. 读取设备说明书、厂商 API/SDK 文档和现有控制程序。
2. 列出设备模板、设备点、属性、命令、公开参数、内部参数和事件。
3. 生成 `adapterconfig.ini`，并保证可被当前 `AdapterManifestService` 解析。
4. 生成 `setup.ini`，放置部署和设备连接参数。
5. 选择南向模式：直接协议、外部程序代理或厂商代码嵌入。
6. 生成 `southbound`，先实现统一驱动接口，再实现具体协议。
7. 生成 `core`，完成语义翻译、安全规则、命令关联和反馈确认。
8. 生成通用 `northbound`，不得写入设备特有逻辑。
9. 生成 `runtime.py`，组装模块并管理启动、停止、心跳和超时。
10. 生成 README、依赖和无需真实设备即可运行的单元测试。

## 10. 生成决策表

| 设备情况 | `core` | `southbound` | 厂商代码 |
| --- | --- | --- | --- |
| 直接 Modbus/串口控制 | 语义转操作、数据转属性 | 实现 Modbus/串口 | 无 |
| 调用未修改的厂商程序 | 语义转厂商调用 | HTTP/TCP/MQTT/进程代理 | 独立运行 |
| 修改并嵌入厂商程序 | 语义转设备操作 | 包装统一驱动接口 | 放入 `vendor/` |
| 厂商程序已经提供标准服务 | 仍负责模型与事件语义 | 轻量服务客户端 | 外部依赖 |

即使系统命令和设备命令名称恰好相同，也应保留 `core` 边界；此时可以做简单透传和
类型校验，但不能让 `northbound` 直接依赖 `southbound`。

## 11. 错误和生命周期规则

- 北向格式错误：拒绝命令；能够识别 `messageId` 时发布失败事件。
- core 参数或安全规则失败：不调用设备，并发布明确失败原因。
- 南向连接失败：转换为可分类的 `DriverError`，不得泄露密码或完整认证信息。
- 命令发布成功不等于设备执行完成；完成状态应依据设备确认、状态反馈或明确同步响应。
- 断线重连后，`northbound` 应重新订阅并重新注册。
- Adapter 停止时先停止接收新命令，再停止轮询和连接，最后发布停止状态。
- 多个并发命令必须按 `messageId` 独立跟踪；重复 `messageId` 不得重复执行。

## 12. 最低测试要求

每个生成的 Adapter 至少覆盖：

- 原始配置解析以及公开/内部参数区分。
- 每条系统命令到设备操作的转换。
- 每个设备原始字段到模型属性的转换。
- 参数类型错误、未知设备点和未知命令。
- 南向连接失败、调用失败和可重试错误。
- 命令完成确认、失败和超时。
- 首次状态基线与后续运行事件推导。
- Topic 渲染、注册、心跳和重连后的重新订阅。
- 日志中不出现密码。

测试应优先使用 fake `NorthboundClient` 和 fake `SouthboundDriver`，使核心逻辑在没有
真实 Broker、厂商程序或设备的情况下也能验证。

## 13. 生成完成检查表

- [ ] 目录和文件名符合本文规范。
- [ ] `northbound` 不包含任何设备协议逻辑。
- [ ] `southbound` 不包含任何 SmartLab Topic 或注册逻辑。
- [ ] `core` 不直接建立网络或串口连接。
- [ ] 直接 Modbus 等协议实现位于 `southbound`。
- [ ] 厂商代码位于 `vendor/` 或独立进程，不冒充 `core`。
- [ ] `adapterconfig.ini` 与后端当前 flat 配置契约一致。
- [ ] `setup.ini` 不会随注册消息上传。
- [ ] 所有公开参数、内部参数和属性映射均有测试。
- [ ] 注册、心跳、遥测、事件、失败和超时路径均已验证。

