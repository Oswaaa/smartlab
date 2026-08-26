# SmartLab Fixture 测试装置库

SmartLab 设备与 Adapter 的端到端测试装置集合。包含温度传感器与智能泄压阀两套模拟体系。

---

## 目录结构

```text
fixture/
├── device/
│   ├── temp_sensor.py           # 模拟温度传感器 (GUI, TCP 9999 端口, °C)
│   └── relief_valve.py          # [新增] 模拟智能泄压阀 (GUI, TCP 9998 端口, MPa)
├── adapter/                     # 温度传感器 Adapter 套件
│   ├── main.py
│   ├── core.py
│   ├── adapter_runtime.py
│   ├── adapterSetup.ini
│   └── runtime.ini
├── adapter_relief_valve/        # [新增] 智能泄压阀 Adapter 套件
│   ├── main.py                  # 泄压阀 Adapter 入口
│   ├── core.py                  # 泄压阀业务逻辑 (压力遥测、泄压/调压/排气指令)
│   ├── adapter_runtime.py       # MQTT 注册、心跳、遥测、事件发布与指令订阅
│   ├── adapterSetup.ini         # 泄压阀物模型注册契约 (严格遵循 samples 规范)
│   └── runtime.ini              # MQTT Broker 地址 (1883) 与设备端口 (9998)
├── run_device.py                # 一键启动模拟温度传感器
├── run_adapter.py               # 一键启动温度传感器 Adapter
├── run_relief_valve_device.py   # [新增] 一键启动模拟泄压阀设备
└── run_relief_valve_adapter.py  # [新增] 一键启动泄压阀 Adapter
```

---

## 使用方式

### A. 智能泄压阀测试 (Pressure Relief Valve, 单位 MPa)

#### 1. 启动泄压阀模拟设备
```bash
python fixture/run_relief_valve_device.py
```
* 设备在 `127.0.0.1:9998` 监听；
* 弹出 Tkinter 图形窗口，实时显示管道压力（MPa）与阀门开度（0-100%），提供加压、泄压与保压手动控制。

#### 2. 启动泄压阀 Adapter
```bash
python fixture/run_relief_valve_adapter.py
```
* 自动向 MQTT Broker 的 `smartlab/adapter/register` 广播注册 `FixtureReliefValveAdapter`；
* 周期上报 `smartlab/adapter/FixtureReliefValveAdapter/Valve1/telemetry` 遥测数据（压力 `pressure`、开度 `valveOpening`）；
* 订阅 `smartlab/adapter/FixtureReliefValveAdapter/+/command` 执行 `releasePressure`、`adjustPressure` 与 `emergencyVent` 控制指令。

---

### B. 温度传感器测试 (Temperature Sensor, 单位 °C)

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

1. 启动模拟设备：`python fixture/run_relief_valve_device.py`
2. 启动对应 Adapter：`python fixture/run_relief_valve_adapter.py`
3. SmartLab 后端收到 MQTT 注册消息 → 在【执行代理管理】出现 `FixtureReliefValveAdapter`
4. 审核并注册 Adapter → 自动生成设备类别 `ReliefValve`
5. 创建设备模型与设备实例 → 绑定到 `Valve1` 点位
6. 在【任务流程设计器】或【安全约束】中使用该设备的能力与属性（例如：当 `pressure >= 0.5 MPa` 时自动执行 `releasePressure` 泄压指令）
7. 启动任务，观察闭环指令执行过程与实时数据曲线。
