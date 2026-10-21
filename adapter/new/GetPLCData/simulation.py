"""
GetPLCData 虚拟点仿真。

加快倍数与 fixture 反应釜相同，由 runtime.ini 的三个参数合成：
K = batchSize * simStepMs / (intervalSec * 1000)。
加热只把温度推向恒温段目标，不等 9999 分钟。
"""

from __future__ import annotations

import logging
import random
import threading
import time
from typing import Any, Callable

from control import as_bool, plan_heat_writes, segments_from_params
from core import AdapterEvent, DeviceBatchData, DeviceData

logger = logging.getLogger("smartlab.adapter.getplcdata.simulation")


def now_ms() -> int:
    return int(time.time() * 1000)


def kind_of(physical_point: str) -> str:
    if physical_point.startswith("Reactor"):
        return "reactor"
    if physical_point.startswith("Heatsink"):
        return "heatsink"
    if physical_point.startswith("CoFlow") or physical_point.startswith("H2Flow"):
        return "flow"
    if physical_point.startswith("Incubator"):
        return "incubator"
    if physical_point == "Chromatography":
        return "chromatography"
    if physical_point in {"CoValve", "H2Valve"}:
        return "valve"
    if physical_point == "Plant":
        return "plant"
    return ""


def default_fields(kind: str) -> dict[str, Any]:
    if kind == "reactor":
        return {"raw_temp": 25.0, "raw_press": 0.1}
    if kind in {"heatsink", "incubator", "chromatography"}:
        return {"raw_temp": 25.0}
    if kind == "flow":
        return {"raw_flow": 0.0}
    if kind == "valve":
        return {"raw_open": False}
    if kind == "plant":
        fields: dict[str, Any] = {"raw_our_control": False}
        for enable_id in range(1, 10):
            fields[f"raw_enable_{enable_id}"] = False
        return fields
    return {}


class _Job:
    def __init__(
        self,
        message_id: str,
        field: str,
        target: float,
        tolerance: float,
        op_event: str,
        op_payload: dict[str, Any],
    ) -> None:
        self.message_id = message_id
        self.field = field
        self.target = target
        self.tolerance = tolerance
        self.op_event = op_event
        self.op_payload = op_payload


class VirtualDevice:
    def __init__(self, virtual_point: str, physical_point: str) -> None:
        self.virtual_point = virtual_point
        self.physical_point = physical_point
        self.kind = kind_of(physical_point)
        self.fields = default_fields(self.kind)
        self.sim_timestamp = now_ms()
        self.last_report = 0.0
        self.jobs: dict[str, _Job] = {}
        self.lock = threading.Lock()

    def running(self) -> bool:
        with self.lock:
            return bool(self.jobs)

    def restore(self, state: dict[str, Any] | None) -> None:
        if not isinstance(state, dict):
            return
        with self.lock:
            for key in list(self.fields):
                if key in state:
                    self.fields[key] = state[key]

    def snapshot(self) -> dict[str, Any]:
        with self.lock:
            return dict(self.fields)

    def cancel(self, field: str) -> str | None:
        with self.lock:
            job = self.jobs.pop(field, None)
        return None if job is None else job.message_id

    def start_job(
        self,
        field: str,
        message_id: str,
        target: float,
        tolerance: float,
        op_event: str,
        op_payload: dict[str, Any],
    ) -> str | None:
        with self.lock:
            previous = self.jobs.get(field)
            self.jobs[field] = _Job(message_id, field, target, tolerance, op_event, op_payload)
            self.sim_timestamp = max(now_ms(), self.sim_timestamp)
        if previous is None or previous.message_id == message_id:
            return None
        return previous.message_id

    def step(self, step_ms: int) -> list[tuple[str, str, dict[str, Any]]]:
        """推进一步。返回已完成的 (event_name, message_id, payload)。"""
        finished: list[tuple[str, str, dict[str, Any]]] = []
        with self.lock:
            self.sim_timestamp += step_ms
            done: list[str] = []
            for field, job in self.jobs.items():
                current = float(self.fields.get(field, 0.0))
                alpha = min(1.0, step_ms / 30000.0)
                nxt = current + (job.target - current) * alpha
                limit = job.tolerance if job.tolerance > 0 else 0.05
                if abs(job.target - nxt) <= limit:
                    self.fields[field] = job.target
                    done.append(field)
                    finished.append((job.op_event, job.message_id, job.op_payload))
                    finished.append(("COMMAND_COMPLETED", job.message_id, {}))
                else:
                    self.fields[field] = nxt
            for field in done:
                self.jobs.pop(field, None)
        return finished


class SimulationImpl:
    def __init__(self, config: dict[str, Any] | None = None) -> None:
        self._config = config or {}
        self.batch_size = max(1, int(self._config.get("batchSize", 5)))
        self.interval_sec = max(0.1, float(self._config.get("intervalSec", 1.0)))
        self.sim_step_ms = max(10, int(self._config.get("simStepMs", 1000)))
        self.temperature_tolerance = float(self._config.get("temperatureTolerance", "0"))
        self._devices: dict[str, VirtualDevice] = {}
        self._lock = threading.Lock()
        self._running = False
        self._thread: threading.Thread | None = None
        self._data_handler: Callable | None = None
        self._batch_data_handler: Callable | None = None
        self._event_handler: Callable | None = None

    def set_data_handler(self, handler: Callable) -> None:
        self._data_handler = handler

    def set_batch_data_handler(self, handler: Callable) -> None:
        self._batch_data_handler = handler

    def set_event_handler(self, handler: Callable) -> None:
        self._event_handler = handler

    def start(self) -> None:
        self._running = True
        self._thread = threading.Thread(target=self._loop, name="plc-sim", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._running = False

    def attach(self, virtual_point: str, physical_point: str, state: dict[str, Any] | None = None) -> None:
        with self._lock:
            device = self._devices.get(virtual_point)
            if device is None:
                device = VirtualDevice(virtual_point, physical_point)
                self._devices[virtual_point] = device
                logger.info("挂接虚拟点 %s，来自 %s", virtual_point, physical_point)
            device.restore(state)

    def detach(self, virtual_point: str) -> None:
        with self._lock:
            self._devices.pop(virtual_point, None)

    def get_device_state(self, virtual_point: str) -> dict[str, Any] | None:
        with self._lock:
            device = self._devices.get(virtual_point)
        if device is None:
            return None
        return device.snapshot()

    def execute(self, command) -> None:
        point = command.device_point
        with self._lock:
            device = self._devices.get(point)
        if device is None:
            self._emit(point, "COMMAND_FAILED", command.message_id, {"reason": "VIRTUAL_POINT", "error": "虚拟点不存在"})
            return
        self._emit(point, "COMMAND_RUNNING", command.message_id)
        try:
            self._start(device, command)
        except Exception as error:
            logger.exception("仿真命令失败 %s", point)
            self._emit(point, "COMMAND_FAILED", command.message_id, {"reason": "COMMAND_FAILED", "error": str(error)})

    def _start(self, device: VirtualDevice, command) -> None:
        name = command.command_name
        params = command.parameters
        if name == "heat":
            _planned, hold = plan_heat_writes(segments_from_params(params))
            payload = {"temperature": float(hold)}
            self._replace_job(device, device.start_job(
                "raw_temp", command.message_id, float(hold), self.temperature_tolerance,
                "TEMPERATURE_HOLDING", payload,
            ))
            self._emit(device.virtual_point, "TEMPERATURE_ADJUSTING", command.message_id, payload)
            return
        if name == "stopHeat":
            self._replace_job(device, device.cancel("raw_temp"))
            self._finish(device.virtual_point, command.message_id, "HEATING_STOPPED", {})
            return
        if name == "pressurize":
            target = float(params["targetPressure"])
            payload = {"pressure": target}
            self._replace_job(device, device.start_job(
                "raw_press", command.message_id, target, 0.01,
                "PRESSURE_HOLDING", payload,
            ))
            self._emit(device.virtual_point, "PRESSURE_ADJUSTING", command.message_id, payload)
            return
        if name == "stopPressurize":
            self._replace_job(device, device.cancel("raw_press"))
            self._finish(device.virtual_point, command.message_id, "PRESSURE_STOPPED", {})
            return
        if name == "setTemp":
            target = float(params["temperature"])
            payload = {"temperature": target}
            self._replace_job(device, device.start_job(
                "raw_temp", command.message_id, target, self.temperature_tolerance,
                "TEMPERATURE_HOLDING", payload,
            ))
            self._emit(device.virtual_point, "TEMPERATURE_ADJUSTING", command.message_id, payload)
            return
        if name == "setFlow":
            flow = float(params["flow"])
            payload = {"flow": flow}
            self._replace_job(device, device.start_job(
                "raw_flow", command.message_id, flow, 0.0, "FLOW_HOLDING", payload,
            ))
            self._emit(device.virtual_point, "FLOW_ADJUSTING", command.message_id, payload)
            return
        if name == "setAlarmTemp":
            self._emit(device.virtual_point, "COMMAND_COMPLETED", command.message_id)
            return
        if name == "open":
            self._set_field(device, "raw_open", True)
            self._finish(device.virtual_point, command.message_id, "VALVE_OPENED", {"isOpen": True})
            return
        if name == "close":
            self._set_field(device, "raw_open", False)
            self._finish(device.virtual_point, command.message_id, "VALVE_CLOSED", {"isOpen": False})
            return
        if name == "takeControl":
            self._set_field(device, "raw_our_control", True)
            self._finish(device.virtual_point, command.message_id, "CONTROL_TAKEN", {"ourControl": True})
            return
        if name == "releaseControl":
            self._set_field(device, "raw_our_control", False)
            self._finish(device.virtual_point, command.message_id, "CONTROL_RELEASED", {"ourControl": False})
            return
        if name == "setEnable":
            enable_id = int(params["enableId"])
            if not 1 <= enable_id <= 9:
                raise ValueError("使能编号必须是 1 到 9")
            self._set_field(device, f"raw_enable_{enable_id}", as_bool(params["enabled"]))
            self._emit(device.virtual_point, "COMMAND_COMPLETED", command.message_id)
            return
        if name == "setAlarmMute":
            as_bool(params["muted"])
            self._emit(device.virtual_point, "COMMAND_COMPLETED", command.message_id)
            return
        raise ValueError(f"未知命令: {name}")

    def _finish(self, point: str, message_id: str, event_name: str, payload: dict[str, Any]) -> None:
        self._emit(point, event_name, message_id, payload)
        self._emit(point, "COMMAND_COMPLETED", message_id)

    def _replace_job(self, device: VirtualDevice, previous_message_id: str | None) -> None:
        if not previous_message_id:
            return
        self._emit(
            device.virtual_point,
            "COMMAND_FAILED",
            previous_message_id,
            {"reason": "STOPPED", "error": "尚未到达目标值，指令已被后续操作取消"},
        )

    def _set_field(self, device: VirtualDevice, key: str, value: Any) -> None:
        with device.lock:
            device.fields[key] = value

    def _loop(self) -> None:
        while self._running:
            with self._lock:
                devices = list(self._devices.values())
            now = time.time()
            for device in devices:
                if device.running():
                    items = []
                    finished: list[tuple[str, str, dict[str, Any]]] = []
                    for _ in range(self.batch_size):
                        finished.extend(device.step(self.sim_step_ms))
                        items.append({"timestamp": device.sim_timestamp, "fields": device.snapshot()})
                        if not device.running():
                            break
                    if self._batch_data_handler is not None and items:
                        self._batch_data_handler(DeviceBatchData(device.virtual_point, items, now_ms()))
                    for event_name, message_id, payload in finished:
                        self._emit(device.virtual_point, event_name, message_id, payload)
                    device.last_report = now
                elif now - device.last_report >= 1.0:
                    device.last_report = now
                    with device.lock:
                        if device.sim_timestamp < now_ms():
                            device.sim_timestamp = now_ms()
                        else:
                            device.sim_timestamp += 1000
                        fields = dict(device.fields)
                        if "raw_temp" in fields and isinstance(fields["raw_temp"], float):
                            fields["raw_temp"] = round(fields["raw_temp"] + random.choice([-0.01, 0.0, 0.01]), 2)
                    if self._data_handler is not None:
                        self._data_handler(DeviceData(device.virtual_point, fields, device.sim_timestamp))
            time.sleep(min(0.5, self.interval_sec))

    def _emit(self, point: str, name: str, message_id: str | None, payload: dict[str, Any] | None = None) -> None:
        if self._event_handler is not None:
            self._event_handler(AdapterEvent(point, name, message_id, payload or {}, now_ms()))
