# TestHeatPressureAdapter

用于本地联调 SmartLab adapter 协议的测试代理。它会：

- 发布注册消息到 `smartlab/adapter/register`
- 周期发布心跳到 `smartlab/adapter/TestHeatPressureAdapter-01/heartbeat`
- 订阅 `smartlab/adapter/TestHeatPressureAdapter-01/+/command`
- 对 `heat`、`pressurize` 命令补齐设备点内部 `index`，再模拟发布指令周期事件、业务事件和遥测数据

## 启动

```bash
pip install -r requirements.txt
python test_adapter.py
```

默认连接 `127.0.0.1:1883`，用户名 `lab_system`，密码 `123456`。可通过环境变量覆盖：

- `SMARTLAB_MQTT_HOST`
- `SMARTLAB_MQTT_PORT`
- `SMARTLAB_MQTT_USERNAME`
- `SMARTLAB_MQTT_PASSWORD`
- `SMARTLAB_MQTT_CLIENT_ID`
