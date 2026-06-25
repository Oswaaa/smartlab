# SmartLab Edge Agent Runtime

`runtime.py` 是 SmartLab 的独立边缘运行时。它部署在靠近设备的工控机、Orange Pi、用户电脑或设备控制主机上，不属于 Spring Boot 单体。

## 职责

- 北向连接 SmartLab MQTT Broker。
- 接收上位机下发的设备执行代理配置。
- 订阅标准命令主题并发布 ACK、状态、事件、遥测和告警。
- 南向执行设备协议、设备服务程序代理、进程托管或仿真逻辑。

## 启动

```bash
pip install paho-mqtt
python runtime.py -c runtime.config.example.json
```

生产环境建议复制 `runtime.config.example.json` 为 `runtime.config.json`，填入真实 broker、gatewayId 和本机需要预加载的 agents。

如果使用 Modbus 或串口连接器，在边缘节点额外安装：

```bash
pip install pymodbus pyserial
```

## 支持的南向连接器

| connectorType | 说明 |
| --- | --- |
| `simulated` | 内置仿真执行与遥测，适合联调、数字孪生和无设备开发 |
| `proxy-http` | 将系统命令转换为 HTTP 请求，适合调用既有 C++/Python 设备服务程序 |
| `tcp-client` | 按配置发送 TCP 帧并读取响应 |
| `mqtt-device` / `plc-mqtt` | adapter 南向再与 PLC 或设备 MQTT 通信 |
| `process` | 执行本机命令或托管设备侧进程，适合代码型设备代理 |
| `modbus-tcp` / `modbus-rtu` | 通过 `pymodbus` 读写寄存器或线圈 |
| `serial` / `rs485` | 通过 `pyserial` 发送串口帧 |
| `custom` | 加载用户自定义 Python connector 插件 |

Modbus、RS-485、串口、PLC SDK 等不应写入 Spring Boot。简单点位协议优先用配置型 connector；复杂设备使用 `custom` 插件，或由 `process/proxy-http` 调用已有驱动服务。

## 配置边界

- `northbound`：系统与 adapter 的 MQTT 契约，来源于设备模型 `adapterContract`。
- `southbound`：adapter 如何控制设备或旧服务程序，来源于设备模型 `adapterProfileTemplate`。

运行中后端只按 `northbound.commandTopic` 发布 `COMMAND`，runtime 只按 `southbound.commandMappings` 执行南向动作。
