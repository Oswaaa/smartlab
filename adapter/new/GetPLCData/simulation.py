"""
GetPLCData 虚拟设备仿真引擎 (Digital Twin / Sandbox for LEASE)。
遵循 SmartLab Adapter 设计规范：
- 仅模拟通过 LEASE 创建的虚拟点（命名规则：{物理点}_sim_{leaseId}）。
- 假设备行为与出数完全在内存中完成，绝不连真机南向 I/O。
- 双模态时钟机制：
  1. 空闲态 (IDLE)：以 1.0 秒间隔与现实墙上时钟同步推进，叠加轻微物理噪声；
  2. 工作态 (RUNNING)：按 simStepMs (默认1000ms) 步长离散时间压缩推演，微批次 (BATCH) 打包上报；
  3. 到位闭环触发：物理量达到设定阈值时，毫秒级直接发射 TARGET_xxx_REACHED 与 COMMAND_COMPLETED，严禁使用 time.sleep 等待。
- 毫秒级回执原则：execute(command) 入口处立即发送 COMMAND_RUNNING，满足系统 10 秒看门狗要求。
"""

from __future__ import annotations

import logging
import random
import threading
import time
from typing import Any, Callable

logger = logging.getLogger("smartlab.adapter.getplcdata.simulation")


def now_ms() -> int:
    return int(time.time() * 1000)


class ActiveCommand:
    def __init__(
        self,
        command_name: str,
        message_id: str,
        target_value: float,
        start_value: float,
        duration_sec: float,
        sim_step_ms: int,
    ) -> None:
        self.command_name = command_name
        self.message_id = message_id
        self.target_value = target_value
        self.start_value = start_value
        self.duration_sec = duration_sec
        self.total_steps = max(1, int(round((max(0.1, duration_sec) * 1000) / max(1, sim_step_ms))))
        self.current_step = 0


class VirtualReactor:
    """单个虚拟反应釜的状态与动力学演化"""

    def __init__(self, virtual_point: str, physical_point: str) -> None:
        self.virtual_point = virtual_point
        self.physical_point = physical_point
        self.current_temp = 25.0
        self.current_pressure = 0.10
        self.target_temp = 25.0
        self.target_pressure = 0.10
        self.operating_mode = "AUTO"
        self.alarm_temp = 120.0
        self.sim_timestamp = now_ms()
        self.last_report_time = 0.0
        self.active_command: ActiveCommand | None = None
        self.lock = threading.Lock()

    def is_running(self) -> bool:
        with self.lock:
            return self.active_command is not None

    def step_physics(self, sim_step_ms: int) -> tuple[dict[str, float], list[tuple[str, Any]]]:
        """
        单步离散动力学推演。
        返回 (fields, [(event_name, event_data), ...])
        """
        with self.lock:
            self.sim_timestamp += sim_step_ms
            events: list[tuple[str, Any]] = []

            sub_steps = max(1, int(sim_step_ms / 200))
            for _ in range(sub_steps):
                # 温度动力学逼近
                if abs(self.current_temp - self.target_temp) > 0.05:
                    diff = self.target_temp - self.current_temp
                    step = max(1.5, min(8.0, abs(diff) * 0.6)) * 0.2
                    if diff > 0:
                        self.current_temp = min(self.target_temp, self.current_temp + step)
                    else:
                        self.current_temp = max(self.target_temp, self.current_temp - step)
                else:
                    self.current_temp = self.target_temp

                # 压力动力学逼近
                if abs(self.current_pressure - self.target_pressure) > 0.005:
                    p_diff = self.target_pressure - self.current_pressure
                    p_step = max(0.05, min(0.5, abs(p_diff) * 0.6)) * 0.2
                    if p_diff > 0:
                        self.current_pressure = min(self.target_pressure, self.current_pressure + p_step)
                    else:
                        self.current_pressure = max(self.target_pressure, self.current_pressure - p_step)
                else:
                    self.current_pressure = self.target_pressure

            # 闭环达成检测：判定阈值（温度 <= 0.5℃，压力 <= 0.02MPa）
            if self.active_command is not None:
                cmd = self.active_command
                if cmd.command_name == "heat":
                    if abs(self.current_temp - self.target_temp) <= 0.5:
                        self.current_temp = self.target_temp
                        self.active_command = None
                        events.append(("TARGET_TEMPERATURE_REACHED", {"temperature": round(self.current_temp, 2)}))
                        events.append(("COMMAND_COMPLETED", cmd.message_id))
                elif cmd.command_name == "pressurize":
                    if abs(self.current_pressure - self.target_pressure) <= 0.02:
                        self.current_pressure = self.target_pressure
                        self.active_command = None
                        events.append(("TARGET_PRESSURE_REACHED", {"pressure": round(self.current_pressure, 3)}))
                        events.append(("COMMAND_COMPLETED", cmd.message_id))

            # 传感器读数轻微白噪声抖动
            jitter = random.choice([-0.01, 0.00, 0.01])
            fields = {
                "raw_temp": round(self.current_temp + jitter, 2),
                "raw_press": round(self.current_pressure, 3),
            }
            return fields, events

    def get_state(self) -> dict[str, float]:
        with self.lock:
            return {
                "current_temp": round(self.current_temp, 2),
                "current_pressure": round(self.current_pressure, 3),
                "target_temp": round(self.target_temp, 2),
                "target_pressure": round(self.target_pressure, 3),
            }

    def set_state(self, state: dict[str, Any]) -> None:
        with self.lock:
            if "current_temp" in state or "currentTemp" in state:
                self.current_temp = float(state.get("current_temp", state.get("currentTemp", self.current_temp)))
            if "current_pressure" in state or "currentPressure" in state:
                self.current_pressure = float(state.get("current_pressure", state.get("currentPressure", self.current_pressure)))
            if "target_temp" in state or "targetTemp" in state:
                self.target_temp = float(state.get("target_temp", state.get("targetTemp", self.target_temp)))
            if "target_pressure" in state or "targetPressure" in state:
                self.target_pressure = float(state.get("target_pressure", state.get("targetPressure", self.target_pressure)))


class SimulationImpl:
    """虚拟反应釜推演管理器，支持动态挂载、时序推进与微批次上报"""

    def __init__(self, config: dict[str, Any] | None = None) -> None:
        self._config = config or {}
        self.batch_size = max(1, int(self._config.get("batchSize", 20)))
        self.interval_sec = max(0.1, float(self._config.get("intervalSec", 0.5)))
        self.sim_step_ms = max(10, int(self._config.get("simStepMs", 1000)))

        self._data_handler: Callable | None = None
        self._batch_data_handler: Callable | None = None
        self._event_handler: Callable | None = None

        self._virtual_devices: dict[str, VirtualReactor] = {}
        self._lock = threading.Lock()
        self._running = False
        self._thread: threading.Thread | None = None

    def set_data_handler(self, handler: Callable) -> None:
        self._data_handler = handler

    def set_batch_data_handler(self, handler: Callable) -> None:
        self._batch_data_handler = handler

    def set_event_handler(self, handler: Callable) -> None:
        self._event_handler = handler

    def start(self) -> None:
        self._running = True
        self._thread = threading.Thread(target=self._sim_loop, name="getplcdata-sim-loop", daemon=True)
        self._thread.start()
        logger.info("GetPLCData 仿真推演引擎已启动 (微批次容量: %d, 推流间隔: %.1fs)", self.batch_size, self.interval_sec)

    def stop(self) -> None:
        self._running = False
        logger.info("GetPLCData 仿真推演引擎已停止")

    def attach(self, virtual_point: str, physical_point: str, state: dict[str, Any] | None = None) -> None:
        with self._lock:
            dev = self._virtual_devices.get(virtual_point)
            if dev is None:
                logger.info("挂载虚拟反应釜: %s (克隆自物理点 %s)", virtual_point, physical_point)
                dev = VirtualReactor(virtual_point, physical_point)
                self._virtual_devices[virtual_point] = dev
            if state:
                dev.set_state(state)

    def detach(self, virtual_point: str) -> None:
        with self._lock:
            if virtual_point in self._virtual_devices:
                logger.info("卸载虚拟反应釜: %s", virtual_point)
                del self._virtual_devices[virtual_point]

    def get_device_state(self, virtual_point: str) -> dict[str, float] | None:
        with self._lock:
            dev = self._virtual_devices.get(virtual_point)
            return dev.get_state() if dev else None

    def get_all_states(self) -> dict[str, dict[str, float]]:
        with self._lock:
            return {vp: dev.get_state() for vp, dev in self._virtual_devices.items()}

    def _sim_loop(self) -> None:
        while self._running:
            with self._lock:
                devices = list(self._virtual_devices.values())

            now = time.time()
            for dev in devices:
                if dev.is_running():
                    # 工作态 (RUNNING)：高速微批次聚合打包
                    batch_items = []
                    completed_events = []
                    for _ in range(self.batch_size):
                        fields, evts = dev.step_physics(self.sim_step_ms)
                        batch_items.append({
                            "timestamp": dev.sim_timestamp,
                            "fields": fields,
                        })
                        if evts:
                            completed_events.extend(evts)
                            if not dev.is_running():
                                break

                    # 发送微批次 BATCH 遥测
                    if self._batch_data_handler and batch_items:
                        from core import DeviceBatchData
                        self._batch_data_handler(DeviceBatchData(
                            device_point=dev.virtual_point,
                            items=batch_items,
                            timestamp=now_ms(),
                        ))
                    elif self._data_handler and batch_items:
                        from core import DeviceData
                        last_item = batch_items[-1]
                        self._data_handler(DeviceData(
                            device_point=dev.virtual_point,
                            fields=last_item["fields"],
                            timestamp=last_item["timestamp"],
                        ))

                    # 触发到位事件与完成事件
                    if self._event_handler:
                        from core import AdapterEvent
                        for evt_name, evt_data in completed_events:
                            if evt_name in ("TARGET_TEMPERATURE_REACHED", "TARGET_PRESSURE_REACHED"):
                                self._event_handler(AdapterEvent(
                                    device_point=dev.virtual_point,
                                    event_name=evt_name,
                                    message_id=None,
                                    payload=evt_data,
                                    timestamp=dev.sim_timestamp,
                                ))
                            elif evt_name == "COMMAND_COMPLETED":
                                self._event_handler(AdapterEvent(
                                    device_point=dev.virtual_point,
                                    event_name="COMMAND_COMPLETED",
                                    message_id=evt_data,
                                    payload={"status": "SUCCESS"},
                                    timestamp=dev.sim_timestamp,
                                ))

                    dev.last_report_time = now

                else:
                    # 空闲态 (IDLE)：1 秒 1 次平稳对齐现实时间
                    if now - dev.last_report_time >= 1.0:
                        dev.last_report_time = now
                        current_real_ms = now_ms()
                        if dev.sim_timestamp < current_real_ms:
                            dev.sim_timestamp = current_real_ms
                        else:
                            dev.sim_timestamp += 1000

                        jitter = random.choice([-0.01, 0.00, 0.01])
                        fields = {
                            "raw_temp": round(dev.current_temp + jitter, 2),
                            "raw_press": round(dev.current_pressure, 3),
                        }

                        if self._data_handler:
                            from core import DeviceData
                            self._data_handler(DeviceData(
                                device_point=dev.virtual_point,
                                fields=fields,
                                timestamp=dev.sim_timestamp,
                            ))
                        elif self._batch_data_handler:
                            from core import DeviceBatchData
                            self._batch_data_handler(DeviceBatchData(
                                device_point=dev.virtual_point,
                                items=[{"timestamp": dev.sim_timestamp, "fields": fields}],
                                timestamp=now_ms(),
                            ))

            time.sleep(min(0.5, self.interval_sec))

    def execute(self, command: Any) -> None:
        from core import AdapterEvent

        point = command.device_point
        cname = command.command_name
        params = command.parameters
        mid = command.message_id

        logger.info("仿真引擎接收指令: %s 点位=%s, 参数=%s, msgId=%s", cname, point, params, mid)

        with self._lock:
            dev = self._virtual_devices.get(point)

        if not dev:
            if self._event_handler:
                self._event_handler(AdapterEvent(
                    device_point=point,
                    event_name="COMMAND_FAILED",
                    message_id=mid,
                    payload={"error": f"虚拟点未挂载: {point}"},
                    timestamp=now_ms(),
                ))
            return

        # 1. 上报 COMMAND_RECEIVED
        if self._event_handler:
            self._event_handler(AdapterEvent(point, "COMMAND_RECEIVED", mid, {}, now_ms()))

        if cname == "heat":
            target_t = float(params.get("targetTemperature", 25.0))
            duration = max(0.1, float(params.get("durationSec", 5)))

            with dev.lock:
                dev.target_temp = target_t
                start_t = dev.current_temp
                dev.sim_timestamp = max(now_ms(), dev.sim_timestamp)
                dev.active_command = ActiveCommand(
                    command_name=cname,
                    message_id=mid,
                    target_value=target_t,
                    start_value=start_t,
                    duration_sec=duration,
                    sim_step_ms=self.sim_step_ms,
                )

            # 2. 毫秒级回执 COMMAND_RUNNING 与 HEAT_STARTED
            if self._event_handler:
                self._event_handler(AdapterEvent(point, "COMMAND_RUNNING", mid, {}, now_ms()))
                self._event_handler(AdapterEvent(point, "HEAT_STARTED", None, {"targetTemperature": target_t}, now_ms()))

        elif cname == "pressurize":
            target_p = float(params.get("targetPressure", 0.10))
            duration = max(0.1, float(params.get("durationSec", 3)))

            with dev.lock:
                dev.target_pressure = target_p
                start_p = dev.current_pressure
                dev.sim_timestamp = max(now_ms(), dev.sim_timestamp)
                dev.active_command = ActiveCommand(
                    command_name=cname,
                    message_id=mid,
                    target_value=target_p,
                    start_value=start_p,
                    duration_sec=duration,
                    sim_step_ms=self.sim_step_ms,
                )

            # 毫秒级回执 COMMAND_RUNNING 与 PRESSURIZE_STARTED
            if self._event_handler:
                self._event_handler(AdapterEvent(point, "COMMAND_RUNNING", mid, {}, now_ms()))
                self._event_handler(AdapterEvent(point, "PRESSURIZE_STARTED", None, {"targetPressure": target_p}, now_ms()))

        elif cname == "setOperatingMode":
            mode = str(params.get("mode", "AUTO")).upper()
            with dev.lock:
                dev.operating_mode = mode
            if self._event_handler:
                self._event_handler(AdapterEvent(point, "COMMAND_RUNNING", mid, {}, now_ms()))
                self._event_handler(AdapterEvent(point, "COMMAND_COMPLETED", mid, {"operatingMode": mode}, now_ms()))

        elif cname == "setAlarmTemp":
            alarm_t = float(params.get("alarmTemperature", 120.0))
            with dev.lock:
                dev.alarm_temp = alarm_t
            if self._event_handler:
                self._event_handler(AdapterEvent(point, "COMMAND_RUNNING", mid, {}, now_ms()))
                self._event_handler(AdapterEvent(point, "COMMAND_COMPLETED", mid, {"alarmTemperature": alarm_t}, now_ms()))

        else:
            if self._event_handler:
                self._event_handler(AdapterEvent(point, "COMMAND_FAILED", mid, {"error": f"不支持的指令: {cname}"}, now_ms()))
