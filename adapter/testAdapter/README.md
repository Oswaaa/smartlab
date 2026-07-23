# SmartLab MQTT PLC Adapter

该程序是 SmartLab 系统和一台 PLC 之间的 MQTT 中间件。系统、Adapter 与 PLC 使用同一个 Broker，但系统协议和 PLC 寄存器协议由 Adapter 隔离。

## 配置文件

- `adapterconfig.ini`：发送给 SmartLab 的能力契约，声明模板、属性、命令、事件和设备点。
- `setup.ini`：仅供 Adapter 本地运行，保存 Broker、认证、Client ID、超时和全部 Topic，不会发送给系统。

`setup.ini` 是运行配置的唯一来源。修改其中的地址、端口、用户名、密码、Client ID、QoS 或 Topic 后，重启 Adapter 即直接生效；Python 代码没有备用 Topic 或认证信息去覆盖它。

当前配置订阅：

- SmartLab 命令：`smartlab/adapter/{adapterName}/+/command`
- PLC 数据：`plc/data`

当前配置发布：

- Adapter 注册、心跳、遥测和事件：`[systemTopics]` 中对应 Topic
- PLC 寄存器命令：`plc/MQTTCommand`

## PLC 数据协议

PLC 在配置的 `plcTopics.telemetry` 上定期发布数组。Adapter 选择 `DeviceSN=plc0001` 的对象，并读取最后一条 `TagData`：

```json
[{"Cache":false,"DeviceSN":"plc0001","TagData":[{"Time":"2004-01-01T17:16:11.00000Z","MW0":36,"MW20":0,"MW21":1,"MW22":0}]}]
```

PLC 的 `Time` 当前不可信，系统遥测和事件时间使用 Adapter 收到 MQTT 报文的本地时间。

| PLC 寄存器 | SmartLab 属性 | 解释 |
|---|---|---|
| `MW0` | `temperature` | 温度数值 |
| `MW20 bit0` | `coolingEnabled` | 散热输出设置 |
| `MW21 bit0` | `automaticMode` | 自动模式 |
| `MW22 bit0` | `manualMode` | 手动模式 |

首个 PLC 快照用于建立状态基线并上报遥测，不生成状态变化事件。之后根据寄存器变化生成 `AUTOMATIC_MODE_ENTERED`、`MANUAL_MODE_ENTERED`、`COOLING_STARTED` 或 `COOLING_STOPPED`。

## 系统命令与 PLC 写入

### 切换运行模式

系统调用 `setOperatingMode`，公开参数 `mode` 只能是 `AUTO` 或 `MANUAL`。

自动模式写入：

```json
[{"DeviceSN":"plc0001","TagData":[{"MW21":1,"MW22":0}]}]
```

手动模式写入：

```json
[{"DeviceSN":"plc0001","TagData":[{"MW21":0,"MW22":1}]}]
```

### 设置散热

系统调用 `setCooling`，公开参数为布尔值 `enabled`。

开启散热：

```json
[{"DeviceSN":"plc0001","TagData":[{"MW20":1}]}]
```

关闭散热时 `MW20` 写 0。开启前必须已经从最新 `plc/data` 确认 `MW22 bit0=1`；否则 Adapter 返回 `COMMAND_FAILED`。关闭散热不受运行模式限制，Adapter 也不会偷偷切换 PLC 模式。

## 指令生命周期

1. 命令通过契约校验后上报 `COMMAND_RECEIVED`。
2. PLC MQTT 发布调用成功后上报 `COMMAND_RUNNING`。
3. 后续 `plc/data` 中目标寄存器全部等于写入值后上报 `COMMAND_COMPLETED`。
4. 参数错误、MQTT 发布失败或超过 `runtime.commandTimeoutSec` 未确认时上报 `COMMAND_FAILED`。
5. 系统发送 `ABORT` 时，Adapter 取消本地等待并上报 `COMMAND_ABORTED`。PLC 没有中止寄存器，因此不会伪造 PLC 中止命令。

## 启动与测试

```bash
python -m pip install -r requirements.txt
python main.py
```

修改 `setup.ini` 后需要重启进程，使新的 MQTT 连接参数和订阅关系生效。

无需 Broker 和 PLC 的测试：

```bash
python -B -m unittest discover -s tests -v
python -m compileall -q main.py smartlab_adapter tests
```
