# SmartLab Fixture

模拟温度传感器 + Adapter 的端到端测试装置。

## 目录结构

```
fixture/
├── device/
│   └── temp_sensor.py    # 模拟温度传感器（28-38°C 随机值，TCP 9999端口）
├── adapter/
│   ├── main.py            # Adapter 入口
│   ├── core.py            # 业务逻辑（读温度、执行加热/散热命令）
│   ├── adapter_runtime.py # MQTT 通信（注册、心跳、遥测、事件、命令订阅）
│   ├── adapterSetup.ini   # Adapter 注册配置（发送给 SmartLab 后端）
│   └── runtime.ini        # MQTT Broker 地址和凭据
└── run_fixture.py         # 一键启动 device + adapter
```

## 使用方式

### 1. 只启动模拟设备

```bash
python fixture/device/temp_sensor.py
```

设备在 `localhost:9999` 监听，每 2 秒输出一行 JSON 温度数据。

### 2. 启动 Adapter（需要 MQTT Broker）

修改 `fixture/adapter/runtime.ini` 中的 MQTT Broker 地址。

```bash
pip install paho-mqtt
python fixture/adapter/main.py
```

### 3. 一键启动（两者同时运行）

```bash
python fixture/run_fixture.py
```

## 全链路测试流程

1. 启动 fixture：`python fixture/run_fixture.py`
2. SmartLab 后端收到 MQTT 注册消息 → Adapter 管理页面出现 `FixtureTempAdapter`
3. 审核并注册 Adapter → 自动生成设备类别 `TempSensor`
4. 创建设备模型 → 选择 `TempSensor` 模板 → 使用 `heat`/`cool` 能力
5. 创建设备实例 → 绑定到 `Sensor1` 设备点
6. 设计工作流：START → 设备能力节点（heat/cool）→ END
7. 创建任务 → 绑定实例 → preflight → 启动
8. 观察任务执行、遥测数据更新、约束引擎求值
