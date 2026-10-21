"""虚拟点按物理点的设备类型克隆：机械臂、普通阀、矫正器或视觉。

运动和输出等待走虚拟时钟上的分发器；视觉 HTTP 在独立线程，不推进运动批次。
"""

from __future__ import annotations

import logging
import threading
import time
from typing import Any, Callable

from core import AdapterEvent, DeviceBatchData, DeviceData
from devices import COMMANDS, DeviceDispatcher
from southbound import MotionEngine, MotionParams, VirtualCard, VirtualClock, WallClock

logger = logging.getLogger("smartlab.adapter.motionstage.simulation")


class _VirtualDevice:
    def __init__(
        self,
        virtual_point: str,
        physical_point: str,
        kind: str,
        clock: Any,
        engine: MotionEngine | None,
        dispatcher: DeviceDispatcher,
    ) -> None:
        self.virtual_point = virtual_point
        self.physical_point = physical_point
        self.kind = kind
        self.clock = clock
        self.engine = engine
        self.dispatcher = dispatcher
        self.samples: list[dict[str, Any]] = []
        self.collecting = False


class SimulationImpl:
    def __init__(self, config: dict[str, Any] | None = None) -> None:
        cfg = dict(config or {})
        motion_cfg = cfg.pop("motion", {})
        self.points = {name: dict(fields) for name, fields in dict(cfg.pop("points", {})).items()}
        self.vision = dict(cfg.pop("vision", {}) or {})
        self.motion = MotionParams(motion_cfg if isinstance(motion_cfg, dict) else {})
        self.batch_size = max(1, int(cfg.get("batchSize", 20)))
        self.interval_sec = max(0.1, float(cfg.get("intervalSec", 0.5)))
        self.sim_step_ms = max(self.motion.loop_period_ms, int(cfg.get("simStepMs", 1000)))
        self.pumps_per_point = max(1, round(self.sim_step_ms / self.motion.loop_period_ms))
        self._data_handler: Callable | None = None
        self._batch_handler: Callable | None = None
        self._event_handler: Callable | None = None
        self._devices: dict[str, _VirtualDevice] = {}
        self._lock = threading.RLock()
        self._running = False
        self._wake = threading.Event()
        self._thread: threading.Thread | None = None
        self._vision_thread: threading.Thread | None = None
        self._idle_at = 0.0

    def set_data_handler(self, handler: Callable) -> None:
        self._data_handler = handler

    def set_batch_data_handler(self, handler: Callable) -> None:
        self._batch_handler = handler

    def set_event_handler(self, handler: Callable) -> None:
        self._event_handler = handler

    def start(self) -> None:
        self._wake.clear()
        self._running = True
        self._thread = threading.Thread(target=self._loop, name="motion-sim", daemon=True)
        self._vision_thread = threading.Thread(target=self._vision_loop, name="vision-sim", daemon=True)
        self._thread.start()
        self._vision_thread.start()

    def stop(self) -> None:
        self._running = False
        self._wake.set()
        for thread in (self._vision_thread, self._thread):
            if thread is not None and thread is not threading.current_thread():
                thread.join(timeout=2.0)
        with self._lock:
            devices = list(self._devices.values())
        for device in devices:
            self._close_device(device)

    def attach(self, virtual_point: str, physical_point: str, state: dict[str, Any] | None = None) -> None:
        with self._lock:
            if virtual_point in self._devices:
                return
            cfg = dict(self.points.get(physical_point) or {})
            kind = str(cfg.get("kind") or "")
            if kind not in COMMANDS:
                raise ValueError(f"未知物理点，无法确定设备类型: {physical_point}")
            engine: MotionEngine | None = None
            try:
                if kind == "vision":
                    clock: Any = WallClock()
                else:
                    clock = VirtualClock()
                    engine = MotionEngine(VirtualCard(clock, self.motion), clock, self.motion)
                    engine.http_get = lambda: None
                    if not engine.initialize("192.168.0.200", 60000, "192.168.0.199", 60000):
                        engine.close()
                        engine = None
                        raise RuntimeError("虚拟设备初始化失败")
                box: list[_VirtualDevice] = []

                def publish(data: DeviceData, _box: list[_VirtualDevice] = box) -> None:
                    self._on_sample(_box[0], data)

                dispatcher = DeviceDispatcher(
                    {virtual_point: cfg}, clock, self._relay_event, publish, self.vision,
                    self.motion.loop_period_ms * self.motion.telemetry_every,
                )
                device = _VirtualDevice(virtual_point, physical_point, kind, clock, engine, dispatcher)
                box.append(device)
                if engine is not None:
                    dispatcher.bind_engine(engine)
                    if kind == "arm" and isinstance(state, dict):
                        engine.restore_fields(state)
                        if "gripperOpen" in state and "gripperBit" in cfg:
                            engine.card.set_ext_do_bit(0, int(cfg["gripperBit"]), 1 if state["gripperOpen"] else 0)
                    if kind in ("valve", "corrector") and isinstance(state, dict) and "isOpen" in state:
                        engine.card.set_ext_do_bit(0, int(cfg["bitIndex"]), 1 if state["isOpen"] else 0)
                elif isinstance(state, dict):
                    kept = {key: state[key] for key in (
                        "tube1_closed", "tube2_closed", "tube3_closed", "tube4_closed"
                    ) if key in state}
                    if kept:
                        dispatcher.snapshots[virtual_point] = kept
                self._devices[virtual_point] = device
            except Exception:
                if engine is not None and virtual_point not in self._devices:
                    engine.close()
                raise
            logger.info("挂载虚拟%s %s，克隆自 %s", kind, virtual_point, physical_point)

    def detach(self, virtual_point: str) -> None:
        with self._lock:
            device = self._devices.pop(virtual_point, None)
        if device is not None:
            self._close_device(device)

    def get_device_state(self, virtual_point: str) -> dict[str, Any] | None:
        with self._lock:
            device = self._devices.get(virtual_point)
        if device is None:
            return None
        return self._state_of(device)

    def get_all_states(self) -> dict[str, dict[str, Any]]:
        with self._lock:
            devices = list(self._devices.items())
        return {name: self._state_of(device) for name, device in devices}

    def execute(self, command: Any) -> None:
        point = command.device_point
        with self._lock:
            device = self._devices.get(point)
        if device is None:
            self._relay_event(
                AdapterEvent(point, "COMMAND_FAILED", str(command.message_id),
                             {"message": f"虚拟点未挂载: {point}"}, int(time.time() * 1000))
            )
            return
        device.dispatcher.execute(command)

    def _loop(self) -> None:
        while self._running and not self._wake.is_set():
            with self._lock:
                devices = list(self._devices.values())
            busy = [device for device in devices if self._busy(device)]
            now = time.time()
            if busy:
                for device in busy:
                    self._run_batch(device)
                self._idle_at = now
                if self._wake.wait(self.interval_sec):
                    break
            else:
                if now - self._idle_at >= 1.0:
                    for device in devices:
                        self._publish_idle(device)
                    self._idle_at = now
                if self._wake.wait(0.1):
                    break

    def _vision_loop(self) -> None:
        while self._running and not self._wake.is_set():
            with self._lock:
                devices = [device for device in self._devices.values() if device.kind == "vision"]
            for device in devices:
                try:
                    device.dispatcher.poll_vision()
                except Exception:
                    logger.exception("虚拟视觉轮询失败 %s", device.virtual_point)
            if self._wake.wait(0.05):
                break

    def _busy(self, device: _VirtualDevice) -> bool:
        if device.kind == "vision":
            return False
        if device.dispatcher.has_pending():
            return True
        return bool(device.engine and device.engine.busy())

    def _run_batch(self, device: _VirtualDevice) -> None:
        device.samples.clear()
        device.collecting = True
        try:
            for _group in range(self.batch_size):
                for _pump in range(self.pumps_per_point):
                    device.clock.advance(self.motion.loop_period_ms)
                    try:
                        device.dispatcher.pump()
                    except Exception as error:
                        logger.warning("虚拟设备 %s 轮询失败: %s", device.virtual_point, error)
                        self._drop_card(device, error)
                        break
                if device.engine is None or not self._busy(device):
                    break
        finally:
            device.collecting = False
        if not device.samples:
            return
        handler = self._batch_handler
        if handler is None:
            return
        handler(DeviceBatchData(device.virtual_point, list(device.samples), device.clock.stamp_ms()))

    def _publish_idle(self, device: _VirtualDevice) -> None:
        if device.kind == "vision" or self._busy(device):
            return
        handler = self._data_handler
        if handler is None:
            return
        wall = int(time.time() * 1000)
        device.clock.align_idle(wall)
        fields = self._state_of(device)
        if not fields:
            return
        handler(DeviceData(device.virtual_point, fields, wall))

    def _state_of(self, device: _VirtualDevice) -> dict[str, Any]:
        with device.dispatcher._lock:
            if device.kind == "arm" and device.engine is not None:
                fields = device.engine.fields()
                cfg = self.points.get(device.physical_point) or {}
                if "gripperBit" in cfg:
                    bit = int(cfg["gripperBit"])
                    fields["gripperOpen"] = device.engine.card.get_ext_do_bit(0, bit) == 1
                return fields
            if device.kind in ("valve", "corrector") and device.engine is not None:
                bit = int(self.points[device.physical_point]["bitIndex"])
                return {"isOpen": device.engine.card.get_ext_do_bit(0, bit) == 1}
            snapshot = device.dispatcher.snapshots.get(device.virtual_point)
            return dict(snapshot or {})

    def _on_sample(self, device: _VirtualDevice, data: DeviceData) -> None:
        if device.collecting:
            device.samples.append({"timestamp": data.timestamp, "fields": dict(data.fields)})
            return
        handler = self._data_handler
        if handler is not None:
            handler(data)

    def _drop_card(self, device: _VirtualDevice, error: BaseException) -> None:
        try:
            device.dispatcher.card_failed(str(error))
        except Exception:
            logger.exception("清理虚拟设备指令失败 %s", device.virtual_point)
        engine = device.engine
        device.engine = None
        if engine is not None:
            try:
                engine.close()
            except Exception:
                logger.exception("关闭虚拟运动卡失败 %s", device.virtual_point)

    def _close_device(self, device: _VirtualDevice) -> None:
        device.dispatcher.bind_engine(None)
        engine = device.engine
        device.engine = None
        if engine is not None:
            engine.close()

    def _relay_event(self, event: AdapterEvent) -> None:
        handler = self._event_handler
        if handler is not None:
            handler(event)
