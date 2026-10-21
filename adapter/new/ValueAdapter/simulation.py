"""GpioValveAdapter 仿真：LEASE 派生的虚拟电磁阀，全部在内存中运行，绝不访问控制器。"""

from __future__ import annotations

import logging
import threading
import time
from collections import deque
from dataclasses import dataclass, field
from typing import Any, Callable

from core import AdapterEvent, DeviceBatchData, DeviceData, SystemCommand, now_ms

logger = logging.getLogger("smartlab.adapter.gpiovalve.simulation")

COMMAND_TARGETS = {"open": True, "close": False}
RAW_FIELD = "raw_open"
SEEN_MESSAGE_LIMIT = 256


def op_event_name(is_open: bool) -> str:
    return "VALVE_OPENED" if is_open else "VALVE_CLOSED"


@dataclass
class ActiveSwitch:
    message_id: str
    target: bool


@dataclass
class VirtualValve:
    virtual_point: str
    physical_point: str
    is_open: bool = False
    sim_ts: int = field(default_factory=now_ms)
    last_idle_report: float = 0.0
    needs_op_sync: bool = True
    active: ActiveSwitch | None = None
    lock: threading.Lock = field(default_factory=threading.Lock)


class SimulationImpl:
    def __init__(self, config: dict[str, Any] | None = None) -> None:
        cfg = config or {}
        self.interval_sec = float(cfg.get("intervalSec", 0.2))
        self.idle_report_sec = float(cfg.get("idleReportSec", 1.0))
        if not 0.1 <= self.interval_sec <= 1.0:
            raise ValueError("[simulation] intervalSec 必须在 0.1~1.0")
        if not 0.5 <= self.idle_report_sec <= 2.0:
            raise ValueError("[simulation] idleReportSec 必须在 0.5~2.0")
        self._data_handler: Callable[[DeviceData], None] | None = None
        self._batch_data_handler: Callable[[DeviceBatchData], None] | None = None
        self._event_handler: Callable[[AdapterEvent], None] | None = None
        self._devices: dict[str, VirtualValve] = {}
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self._seen_ids: deque[str] = deque()
        self._seen_set: set[str] = set()

    def set_data_handler(self, handler: Callable[[DeviceData], None]) -> None:
        self._data_handler = handler

    def set_batch_data_handler(self, handler: Callable[[DeviceBatchData], None]) -> None:
        self._batch_data_handler = handler

    def set_event_handler(self, handler: Callable[[AdapterEvent], None]) -> None:
        self._event_handler = handler

    def start(self) -> None:
        self._stop.clear()
        self._thread = threading.Thread(target=self._loop, name="gpio-valve-sim", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout=2.0)

    def attach(self, virtual_point: str, physical_point: str, state: dict[str, Any] | None = None) -> None:
        with self._lock:
            dev = self._devices.get(virtual_point)
            if dev is None:
                dev = VirtualValve(virtual_point, physical_point)
                self._devices[virtual_point] = dev
                logger.info("挂载虚拟电磁阀 %s（克隆自 %s）", virtual_point, physical_point)
        if state and isinstance(state.get("isOpen"), bool):
            with dev.lock:
                dev.is_open = state["isOpen"]
                dev.needs_op_sync = True

    def detach(self, virtual_point: str) -> None:
        with self._lock:
            if self._devices.pop(virtual_point, None) is not None:
                logger.info("卸载虚拟电磁阀 %s", virtual_point)

    def get_device_state(self, virtual_point: str) -> dict[str, Any] | None:
        with self._lock:
            dev = self._devices.get(virtual_point)
        if dev is None:
            return None
        with dev.lock:
            return {"isOpen": dev.is_open}

    def get_all_states(self) -> dict[str, dict[str, Any]]:
        with self._lock:
            points = list(self._devices)
        return {p: s for p in points if (s := self.get_device_state(p)) is not None}

    def execute(self, command: SystemCommand) -> None:
        point = command.device_point
        mid = command.message_id
        if not self._remember(mid):
            logger.warning("忽略重复投递的虚拟指令 messageId=%s point=%s", mid, point)
            return
        with self._lock:
            dev = self._devices.get(point)
        self._event(point, "COMMAND_RUNNING", mid, {"commandName": command.command_name})
        if dev is None:
            self._event(point, "COMMAND_FAILED", mid,
                        {"reason": "VIRTUAL_POINT_NOT_ATTACHED", "error": f"虚拟点未挂载: {point}"})
            return
        target = COMMAND_TARGETS.get(command.command_name)
        if target is None:
            self._event(point, "COMMAND_FAILED", mid,
                        {"reason": "UNSUPPORTED_COMMAND", "error": f"不支持的指令: {command.command_name}"})
            return
        with dev.lock:
            superseded = dev.active
            dev.active = ActiveSwitch(mid, target)
        if superseded is not None:
            self._event(point, "COMMAND_FAILED", superseded.message_id,
                        {"reason": "SUPERSEDED", "error": f"被新指令 {mid} 取代"})

    def _remember(self, message_id: str) -> bool:
        with self._lock:
            if message_id in self._seen_set:
                return False
            self._seen_set.add(message_id)
            self._seen_ids.append(message_id)
            while len(self._seen_ids) > SEEN_MESSAGE_LIMIT:
                self._seen_set.discard(self._seen_ids.popleft())
            return True

    def _loop(self) -> None:
        while not self._stop.wait(self.interval_sec):
            with self._lock:
                devices = list(self._devices.values())
            for dev in devices:
                try:
                    self._step(dev)
                except Exception:
                    logger.exception("虚拟电磁阀 %s 推演异常", dev.virtual_point)

    def _step(self, dev: VirtualValve) -> None:
        now = time.monotonic()
        with self._lock:
            if self._devices.get(dev.virtual_point) is not dev:
                return
        with dev.lock:
            active = dev.active
            dev.active = None
            if active is not None:
                dev.is_open = active.target
                dev.sim_ts = max(now_ms(), dev.sim_ts + 1)
                dev.last_idle_report = now
                dev.needs_op_sync = False
                ts, is_open = dev.sim_ts, dev.is_open
                mode = "COMMAND"
            elif now - dev.last_idle_report >= self.idle_report_sec:
                dev.sim_ts = max(now_ms(), dev.sim_ts + int(self.idle_report_sec * 1000))
                dev.last_idle_report = now
                sync = dev.needs_op_sync
                dev.needs_op_sync = False
                ts, is_open = dev.sim_ts, dev.is_open
                mode = "IDLE_SYNC" if sync else "IDLE"
            else:
                return
        point = dev.virtual_point
        fields = {RAW_FIELD: is_open}
        if mode == "COMMAND":
            if self._batch_data_handler is not None:
                self._batch_data_handler(DeviceBatchData(point, [{"timestamp": ts, "fields": fields}], now_ms()))
            self._emit(AdapterEvent(point, op_event_name(is_open), active.message_id, {"source": "COMMAND"}, ts))
            self._emit(AdapterEvent(point, "COMMAND_COMPLETED", active.message_id, {"isOpen": is_open}, ts))
            return
        if self._data_handler is not None:
            self._data_handler(DeviceData(point, fields, ts))
        if mode == "IDLE_SYNC":
            self._emit(AdapterEvent(point, op_event_name(is_open), None, {"source": "BASELINE"}, ts))

    def _event(self, point: str, name: str, mid: str | None, payload: dict[str, Any]) -> None:
        self._emit(AdapterEvent(point, name, mid, payload, now_ms()))

    def _emit(self, event: AdapterEvent) -> None:
        if self._event_handler is not None:
            self._event_handler(event)
