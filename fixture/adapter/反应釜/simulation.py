"""
Reactor virtual device simulation (Digital Twin / Sandbox for LEASE).
Follows SmartLab Adapter Design Specification:
- Simulates virtual points created via LEASE (e.g. Reactor1_sim_1001).
- Generates simulated telemetry and handles virtual commands completely in memory.
- Dual-mode operation:
  1. IDLE: Reports single steady-state readings (1s interval) synchronized with real wall clock.
  2. RUNNING: Fast time-compressed micro-batch推演 based on batchSize, intervalSec, and simStepMs.
  3. Closed-loop completion: Emits TARGET reached and COMMAND_COMPLETED immediately when physical steps finish.
- Never communicates with real hardware.
"""

from __future__ import annotations

import logging
import random
import threading
import time
from typing import Any, Callable

logger = logging.getLogger("smartlab.adapter.reactor.simulation")


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
        # 计算完成该指令所需的离散推演步数
        self.total_steps = max(1, int(round((max(0.1, duration_sec) * 1000) / max(1, sim_step_ms))))
        self.current_step = 0


class VirtualReactor:
    """Individual virtual reactor state machine."""

    def __init__(self, virtual_point: str, physical_point: str) -> None:
        self.virtual_point = virtual_point
        self.physical_point = physical_point
        self.current_temp = 25.0
        self.current_pressure = 0.10
        self.target_temp = 25.0
        self.target_pressure = 0.10
        self.sim_timestamp = now_ms()
        self.last_report_time = 0.0
        self.active_command: ActiveCommand | None = None
        self.lock = threading.Lock()

    def is_running(self) -> bool:
        with self.lock:
            return self.active_command is not None

    def step_physics(self, sim_step_ms: int) -> tuple[dict[str, float], list[tuple[str, Any]]]:
        """
        单步动力学推演（算法、步进速率、超调平滑与真实设备 reactor.py 100% 一致）。
        返回 (fields, [(event_name, event_data), ...])
        """
        with self.lock:
            self.sim_timestamp += sim_step_ms
            events: list[tuple[str, Any]] = []

            # 对应 1 秒的物理演进（reactor.py 内部 0.2 秒 1 次，共 5 次子步）
            sub_steps = max(1, int(sim_step_ms / 200))
            for _ in range(sub_steps):
                # 温度动力学：完全对齐 reactor.py
                if abs(self.current_temp - self.target_temp) > 0.05:
                    diff = self.target_temp - self.current_temp
                    step = max(1.5, min(8.0, abs(diff) * 0.6)) * 0.2
                    if diff > 0:
                        self.current_temp = min(self.target_temp, self.current_temp + step)
                    else:
                        self.current_temp = max(self.target_temp, self.current_temp - step)
                else:
                    self.current_temp = self.target_temp

                # 压力动力学：完全对齐 reactor.py
                if abs(self.current_pressure - self.target_pressure) > 0.005:
                    p_diff = self.target_pressure - self.current_pressure
                    p_step = max(0.05, min(0.5, abs(p_diff) * 0.6)) * 0.2
                    if p_diff > 0:
                        self.current_pressure = min(self.target_pressure, self.current_pressure + p_step)
                    else:
                        self.current_pressure = max(self.target_pressure, self.current_pressure - p_step)
                else:
                    self.current_pressure = self.target_pressure

            # 任务闭环达成检测：完全对齐 control.py 判定阈值 (温度<=0.5℃，压力<=0.02MPa)
            if self.active_command is not None:
                cmd = self.active_command
                if cmd.command_name in ("heat", "setTemperature"):
                    if abs(self.current_temp - self.target_temp) <= 0.5:
                        self.current_temp = self.target_temp
                        self.active_command = None
                        events.append(("TARGET_TEMPERATURE_REACHED", {"temperature": round(self.current_temp, 2)}))
                        events.append(("COMMAND_COMPLETED", cmd.message_id))
                elif cmd.command_name in ("pressurize", "adjustPressure", "setPressure"):
                    if abs(self.current_pressure - self.target_pressure) <= 0.02:
                        self.current_pressure = self.target_pressure
                        self.active_command = None
                        events.append(("TARGET_PRESSURE_REACHED", {"pressure": round(self.current_pressure, 3)}))
                        events.append(("COMMAND_COMPLETED", cmd.message_id))

            # 传感器读数与抖动：完全对齐 reactor.py 的 jitter 机制
            jitter = random.choice([-0.01, 0.00, 0.01])
            fields = {
                "raw_temp": round(self.current_temp + jitter, 2),
                "raw_press": round(self.current_pressure, 3),
            }
            return fields, events

    def update_physics(self) -> None:
        """平滑逼近目标值（完全对齐 reactor.py）。"""
        with self.lock:
            for _ in range(5):
                if abs(self.current_temp - self.target_temp) > 0.05:
                    diff = self.target_temp - self.current_temp
                    step = max(1.5, min(8.0, abs(diff) * 0.6)) * 0.2
                    if diff > 0:
                        self.current_temp = min(self.target_temp, self.current_temp + step)
                    else:
                        self.current_temp = max(self.target_temp, self.current_temp - step)
                else:
                    self.current_temp = self.target_temp

                if abs(self.current_pressure - self.target_pressure) > 0.005:
                    p_diff = self.target_pressure - self.current_pressure
                    p_step = max(0.05, min(0.5, abs(p_diff) * 0.6)) * 0.2
                    if p_diff > 0:
                        self.current_pressure = min(self.target_pressure, self.current_pressure + p_step)
                    else:
                        self.current_pressure = max(self.target_pressure, self.current_pressure - p_step)
                else:
                    self.current_pressure = self.target_pressure

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
            if "current_temp" in state:
                self.current_temp = float(state["current_temp"])
            elif "currentTemp" in state:
                self.current_temp = float(state["currentTemp"])
            if "current_pressure" in state:
                self.current_pressure = float(state["current_pressure"])
            elif "currentPressure" in state:
                self.current_pressure = float(state["currentPressure"])
            if "target_temp" in state:
                self.target_temp = float(state["target_temp"])
            elif "targetTemp" in state:
                self.target_temp = float(state["targetTemp"])
            if "target_pressure" in state:
                self.target_pressure = float(state["target_pressure"])
            elif "targetPressure" in state:
                self.target_pressure = float(state["targetPressure"])


class SimulationImpl:
    """Simulation manager for leased virtual points."""

    def __init__(self, config: dict[str, Any] | None = None) -> None:
        self._config = config or {}
        self.batch_size = max(1, int(self._config.get("batchSize", 5)))
        self.interval_sec = max(0.1, float(self._config.get("intervalSec", 1.0)))
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
        self._thread = threading.Thread(target=self._sim_loop, name="reactor-sim-loop", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._running = False

    def attach(self, virtual_point: str, physical_point: str, state: dict[str, Any] | None = None) -> None:
        with self._lock:
            dev = self._virtual_devices.get(virtual_point)
            if dev is None:
                logger.info("Attaching virtual reactor: %s (cloned from %s)", virtual_point, physical_point)
                dev = VirtualReactor(virtual_point, physical_point)
                self._virtual_devices[virtual_point] = dev
            if state:
                dev.set_state(state)

    def detach(self, virtual_point: str) -> None:
        with self._lock:
            if virtual_point in self._virtual_devices:
                logger.info("Detaching virtual reactor: %s", virtual_point)
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
            devices = []
            with self._lock:
                devices = list(self._virtual_devices.values())

            now = time.time()
            for dev in devices:
                if dev.is_running():
                    # 工作态（RUNNING）：高速微批次推演
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

                    if self._batch_data_handler and batch_items:
                        from core import DeviceBatchData
                        self._batch_data_handler(DeviceBatchData(
                            device_point=dev.virtual_point,
                            items=batch_items,
                            timestamp=now_ms()
                        ))
                    elif self._data_handler and batch_items:
                        from core import DeviceData
                        last_item = batch_items[-1]
                        self._data_handler(DeviceData(
                            device_point=dev.virtual_point,
                            fields=last_item["fields"],
                            timestamp=last_item["timestamp"]
                        ))

                    # 触发物理量闭环到达事件与完成事件
                    if self._event_handler:
                        from core import AdapterEvent
                        for evt_name, evt_data in completed_events:
                            if evt_name in ("TARGET_TEMPERATURE_REACHED", "TARGET_PRESSURE_REACHED"):
                                self._event_handler(AdapterEvent(dev.virtual_point, evt_name, None, evt_data, dev.sim_timestamp))
                            elif evt_name == "COMMAND_COMPLETED":
                                self._event_handler(AdapterEvent(dev.virtual_point, "COMMAND_COMPLETED", evt_data, {}, dev.sim_timestamp))

                    dev.last_report_time = now

                else:
                    # 空闲态（IDLE）：1 秒 1 次与现实时间平稳同步
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
                                timestamp=dev.sim_timestamp
                            ))
                        elif self._batch_data_handler:
                            from core import DeviceBatchData
                            self._batch_data_handler(DeviceBatchData(
                                device_point=dev.virtual_point,
                                items=[{"timestamp": dev.sim_timestamp, "fields": fields}],
                                timestamp=now_ms()
                            ))

            time.sleep(min(0.5, self.interval_sec))

    def execute(self, command) -> None:
        from core import AdapterEvent

        point = command.device_point
        cmd_name = command.command_name
        params = command.parameters
        mid = command.message_id

        logger.info("Simulation executing %s on %s, params=%s, msgId=%s", cmd_name, point, params, mid)

        with self._lock:
            dev = self._virtual_devices.get(point)

        if not dev:
            if self._event_handler:
                self._event_handler(AdapterEvent(point, "COMMAND_FAILED", mid, {"error": "Virtual device not found"}, now_ms()))
            return

        # 1. 立即上报 COMMAND_RECEIVED
        if self._event_handler:
            self._event_handler(AdapterEvent(point, "COMMAND_RECEIVED", mid, {}, now_ms()))

        if cmd_name in ("heat", "setTemperature"):
            target_t = float(params.get("targetTemperature", 25.0))
            duration = max(0.1, float(params.get("durationSec", 5)))

            with dev.lock:
                dev.target_temp = target_t
                start_t = dev.current_temp
                dev.sim_timestamp = max(now_ms(), dev.sim_timestamp)
                dev.active_command = ActiveCommand(
                    command_name=cmd_name,
                    message_id=mid,
                    target_value=target_t,
                    start_value=start_t,
                    duration_sec=duration,
                    sim_step_ms=self.sim_step_ms,
                )

            # 2. 毫秒级回执 COMMAND_RUNNING 与 HEAT_STARTED，满足后端 10 秒看门狗
            if self._event_handler:
                self._event_handler(AdapterEvent(point, "COMMAND_RUNNING", mid, {}, now_ms()))
                self._event_handler(AdapterEvent(point, "HEAT_STARTED", None, {"targetTemperature": target_t}, now_ms()))

        elif cmd_name in ("pressurize", "adjustPressure", "setPressure"):
            target_p = float(params.get("targetPressure", 0.10))
            duration = max(0.1, float(params.get("durationSec", 3)))

            with dev.lock:
                dev.target_pressure = target_p
                start_p = dev.current_pressure
                dev.sim_timestamp = max(now_ms(), dev.sim_timestamp)
                dev.active_command = ActiveCommand(
                    command_name=cmd_name,
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

        else:
            if self._event_handler:
                self._event_handler(AdapterEvent(point, "COMMAND_FAILED", mid, {"error": f"Unsupported command {cmd_name}"}, now_ms()))
