# SmartLab Fixture 测试装置库

SmartLab 设备与 Adapter 的端到端测试装置集合。包含温度传感器与智能泄压阀两套模拟体系。

---

## 目录结构

```text
fixture/
├── device/                      # 真实物理设备仿真程序（南向硬件模拟）
│   ├── 反应釜/
│   │   └── reactor.py           # 模拟化学反应釜设备 (Tkinter GUI, TCP 9997 端口, °C & MPa)
│   └── 温度模拟器/
│       └── temp_sensor.py       # 模拟温度传感器设备 (Tkinter GUI, TCP 9999 端口, °C)
├── adapter/                     # 遵循开发规范的 Adapter 套件
│   ├── 反应釜/                  # 反应釜 Adapter V2（符合开发规范指南）
│   │   ├── core.py              # 核心层：MQTT 通信、注册心跳、租约持久化、真假分流
│   │   ├── simulation.py        # 仿真层：虚拟反应釜状态机、时钟推进、微批次打包
│   │   ├── control.py           # 控制层：真实设备 TCP 9997 协议驱动
│   │   ├── adapterSetup.ini     # 反应釜物模型注册契约
│   │   ├── runtime.ini          # 运行时配置：MQTT Broker、端口、仿真参数
│   │   └── virtual_leases.json  # 本地租约与设备状态快照持久化文件
│   └── 温度模拟器/              # 温度模拟器 Adapter 套件
│       ├── main.py              # 入口主程序
│       ├── core.py              # 业务控制逻辑与 TCP 驱动
│       ├── adapter_runtime.py   # MQTT 桥接运行时
│       ├── adapterSetup.ini     # 温度传感器物模型契约 (FixtureTempAdapter)
│       └── runtime.ini          # 运行时配置
├── run_reactor_device.py        # 一键启动反应釜模拟设备 GUI
├── run_reactor_adapter.py       # 一键启动反应釜 Adapter V2
├── run_temp_device.py           # 一键启动温度模拟器设备 GUI
└── run_temp_adapter.py          # 一键启动温度模拟器 Adapter
```

---

## 使用方式

### A. 反应釜测试 (Chemical Reactor, 温度 °C 与 压力 MPa)

#### 1. 启动反应釜模拟设备 (GUI)
```bash
python fixture/run_reactor_device.py
```
*(或 `python fixture/device/reactor.py`)*
* 设备在 `127.0.0.1:9997` 监听；
* 弹出 Tkinter 图形窗口，实时展示当前检测到的温度与压力；
* 支持在预填项输入数值并点击【改变温度】/【改变压力】直接变更检测值；
* 温度 30 秒慢速波动一次 ($\pm 0.01\ ^\circ\text{C}$)，压力完全稳定不跳动。

#### 2. 启动反应釜 Adapter
```bash
python fixture/run_reactor_adapter.py
```
*(或 `python fixture/adapter_reactor/main.py`)*
* 自动向 MQTT Broker 的 `smartlab/adapter/register` 广播注册 `FixtureReactorAdapter`；
* 周期上报 `smartlab/adapter/FixtureReactorAdapter/Reactor1/telemetry` 遥测数据（`temperature`、`pressure`）；
* 订阅 `smartlab/adapter/FixtureReactorAdapter/+/command` 执行 `heat`、`cool`、`setTemperature`、`adjustPressure`、`setPressure` 控制指令；
* 支持在 Adapter 控制台输入 `temp 80` 或 `press 0.5` 进行交互式手动调控，设备 GUI 窗口将实时平滑同步。

---

### B. 智能泄压阀测试 (Pressure Relief Valve, 单位 MPa)

#### 1. 启动泄压阀模拟设备
```bash
python fixture/run_relief_valve_device.py
```

#### 2. 启动泄压阀 Adapter
```bash
python fixture/run_relief_valve_adapter.py
```

---

### C. 温度传感器测试 (Temperature Sensor, 单位 °C)

#### 1. 启动温度传感器模拟设备
```bash
python fixture/run_device.py
```

#### 2. 启动温度传感器 Adapter
```bash
python fixture/run_adapter.py
```

---

## 全链路测试流程

1. 启动模拟设备：`python fixture/run_reactor_device.py`
2. 启动对应 Adapter：`python fixture/run_reactor_adapter.py`
3. SmartLab 后端收到 MQTT 注册消息 → 在【执行代理管理】出现 `FixtureReactorAdapter`
4. 审核并注册 Adapter → 自动生成设备类别 `Reactor`
5. 创建设备模型与设备实例 → 绑定到 `Reactor1` 点位
6. 在【任务流程设计器】或【安全约束】中使用该设备的能力与属性
7. 启动任务，观察闭环指令执行过程与实时数据曲线。
