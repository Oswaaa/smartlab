"""
Reactor physical device controller (Southbound driver).
Follows SmartLab Adapter Design Specification:
- Only handles physical points declared in adapterSetup.ini.
- Actively polls real reactor device (TCP) for raw_temp and raw_press.
- Executes commands, writes setpoints to hardware, and autonomously verifies command completion.
- Telemetry and events always carry the actual physical device_point that produced them.
"""

from __future__ import annotations

import json
import logging
import queue
import socket
import threading
import time
from typing import Any, Callable

logger = logging.getLogger("smartlab.adapter.reactor.control")


def now_ms() -> int:
    return int(time.time() * 1000)


class ControlImpl:
    """Real reactor physical device controller."""

    def __init__(
        self,
        extra: dict[str, dict[str, str]],
        physical_points: tuple[str, ...] | list[str] = (),
        point_fields: dict[str, dict[str, str]] | None = None,
    ) -> None:
        reactor_cfg = extra.get("reactor", {})
        self.host = reactor_cfg.get("host", "127.0.0.1")
        self.port = int(reactor_cfg.get("port", "9997"))
        self.poll_interval = float(reactor_cfg.get("pollIntervalSec", "1.0"))

        self.physical_points = tuple(physical_points)
        self.point_fields = point_fields or {}
        self._index_to_point = self._build_index_map(self.physical_points, self.point_fields)

        self._data_handler: Callable | None = None
        self._event_handler: Callable | None = None
        self._sock: socket.socket | None = None
        self._connected = False
        self._running = False

        # Per-physical-point sensor snapshot
        self._point_state: dict[str, dict[str, float]] = {
            point: {"temp": 25.0, "pressure": 0.10} for point in self.physical_points
        }

        self._cmd_queue: queue.Queue = queue.Queue()
        self._stop_event = threading.Event()

    @staticmethod
    def _build_index_map(
        physical_points: tuple[str, ...],
        point_fields: dict[str, dict[str, str]],
    ) -> dict[str, str]:
        mapping: dict[str, str] = {}
        for point in physical_points:
            fields = point_fields.get(point, {})
            index = str(fields.get("index", "")).strip()
            if index:
                mapping[index] = point
        return mapping

    def set_data_handler(self, handler: Callable) -> None:
        self._data_handler = handler

    def set_event_handler(self, handler: Callable) -> None:
        self._event_handler = handler

    def _try_connect(self) -> bool:
        try:
            if self._sock:
                try:
                    self._sock.close()
                except Exception:
                    pass
                self._sock = None

            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.connect((self.host, self.port))
            s.settimeout(2.0)
            self._sock = s
            self._connected = True
            logger.info("Connected to physical reactor at %s:%d", self.host, self.port)
            return True
        except Exception as e:
            self._connected = False
            logger.debug("Device connection attempt failed: %s", e)
            return False

    def _io_exchange(self, payload: dict, recv_buffer: str) -> tuple[dict | None, str]:
        if not self._sock:
            return None, recv_buffer
        try:
            req_bytes = (json.dumps(payload) + "\n").encode("utf-8")
            self._sock.sendall(req_bytes)

            while "\n" not in recv_buffer:
                chunk = self._sock.recv(1024).decode("utf-8")
                if not chunk:
                    raise ConnectionResetError("Device closed socket")
                recv_buffer += chunk

            line, recv_buffer = recv_buffer.split("\n", 1)
            line = line.strip()
            if line:
                return json.loads(line), recv_buffer
        except Exception as e:
            logger.debug("Socket I/O error with physical device: %s", e)
            self._connected = False
            if self._sock:
                try:
                    self._sock.close()
                except Exception:
                    pass
                self._sock = None
            return None, ""
        return None, recv_buffer

    def _resolve_physical_point(self, resp: dict[str, Any], fallback: str | None = None) -> str | None:
        """Map a hardware reading/command reply onto a configured physical point."""
        named = str(resp.get("devicePoint") or resp.get("device_point") or "").strip()
        if named:
            return named if named in self._point_state else None
        index = str(resp.get("index", "")).strip()
        if index and index in self._index_to_point:
            return self._index_to_point[index]
        if fallback and fallback in self._point_state:
            return fallback
        if len(self.physical_points) == 1:
            return self.physical_points[0]
        return None

    def _emit_telemetry(self, device_point: str, temp: float, pressure: float) -> None:
        state = self._point_state.setdefault(device_point, {"temp": 25.0, "pressure": 0.10})
        state["temp"] = temp
        state["pressure"] = pressure
        if not self._data_handler:
            return
        from core import DeviceData
        self._data_handler(DeviceData(
            device_point=device_point,
            fields={
                "raw_temp": temp,
                "raw_press": pressure,
            },
            timestamp=now_ms(),
        ))

    def start(self) -> None:
        if not self.physical_points:
            raise ValueError("ControlImpl 需要至少一个物理点（来自 adapterSetup.ini）")
        self._running = True
        self._stop_event.clear()

        def _poll_worker():
            recv_buffer = ""
            while self._running:
                if not self._connected or not self._sock:
                    time.sleep(1.0)
                    self._try_connect()
                    recv_buffer = ""
                    continue

                while not self._cmd_queue.empty():
                    try:
                        cmd_payload = self._cmd_queue.get_nowait()
                        _, recv_buffer = self._io_exchange(cmd_payload, recv_buffer)
                    except queue.Empty:
                        break

                if not self._connected:
                    continue

                # Poll every configured physical point. Single-device fixtures may
                # omit identity; multi-point hardware must echo devicePoint or index.
                for point in self.physical_points:
                    payload: dict[str, Any] = {"action": "read", "devicePoint": point}
                    fields = self.point_fields.get(point, {})
                    index = str(fields.get("index", "")).strip()
                    if index:
                        payload["index"] = int(index) if index.isdigit() else index
                    resp, recv_buffer = self._io_exchange(payload, recv_buffer)
                    if not resp or "temperature" not in resp or "pressure" not in resp:
                        continue
                    resolved = self._resolve_physical_point(resp, fallback=point)
                    if resolved is None:
                        logger.warning(
                            "Physical reading has no matching devicePoint/index; drop telemetry: %s",
                            resp,
                        )
                        continue
                    self._emit_telemetry(
                        resolved,
                        float(resp["temperature"]),
                        float(resp["pressure"]),
                    )

                time.sleep(self.poll_interval)

        threading.Thread(target=_poll_worker, name="reactor-control-poll", daemon=True).start()

    def stop(self) -> None:
        self._running = False
        self._stop_event.set()
        if self._sock:
            try:
                self._sock.close()
            except Exception:
                pass
            self._sock = None

    def execute(self, command) -> None:
        """
        Execute system command on physical point:
        1. Emits COMMAND_RECEIVED
        2. Writes setpoints to hardware
        3. Emits COMMAND_RUNNING
        4. Autonomously verifies measured data against target
        5. Emits COMMAND_COMPLETED or COMMAND_FAILED
        """
        from core import AdapterEvent

        cmd_name = command.command_name
        params = command.parameters
        mid = command.message_id
        point = command.device_point

        if "_sim_" in point or point not in self._point_state:
            logger.error("Physical controller must not execute non-local point %s", point)
            if self._event_handler:
                self._event_handler(AdapterEvent(
                    point,
                    "COMMAND_FAILED",
                    mid,
                    {"error": f"Physical controller received unknown or virtual point: {point}"},
                    now_ms(),
                ))
            return

        logger.info("Control executing %s on %s, params=%s, msgId=%s", cmd_name, point, params, mid)

        if self._event_handler:
            self._event_handler(AdapterEvent(point, "COMMAND_RECEIVED", mid, {}, now_ms()))

        write_base: dict[str, Any] = {"devicePoint": point}
        index = params.get("index")
        if index is not None and str(index).strip() != "":
            write_base["index"] = index
        else:
            fields = self.point_fields.get(point, {})
            configured = str(fields.get("index", "")).strip()
            if configured:
                write_base["index"] = int(configured) if configured.isdigit() else configured

        if cmd_name in ("heat", "setTemperature"):
            target_t = float(params.get("targetTemperature", 25.0))
            duration = int(params.get("durationSec", 15))
            timeout = max(duration + 10, 25)

            self._cmd_queue.put({**write_base, "action": "set", "targetTemperature": target_t})

            if self._event_handler:
                self._event_handler(AdapterEvent(point, "COMMAND_RUNNING", mid, {}, now_ms()))
                self._event_handler(AdapterEvent(point, "HEAT_STARTED", None, {"targetTemperature": target_t}, now_ms()))

            def _verify_temp():
                start = time.time()
                success = False
                while self._running and (time.time() - start) < timeout:
                    current = self._point_state[point]["temp"]
                    if abs(current - target_t) <= 0.5:
                        success = True
                        break
                    time.sleep(0.5)

                current = self._point_state[point]["temp"]
                if success and self._event_handler:
                    self._event_handler(AdapterEvent(point, "TARGET_TEMPERATURE_REACHED", None, {"temperature": current}, now_ms()))
                    self._event_handler(AdapterEvent(point, "COMMAND_COMPLETED", mid, {}, now_ms()))
                    logger.info("Heat command completed on %s: current temp=%.2f°C", point, current)
                elif not success and self._event_handler:
                    self._event_handler(AdapterEvent(point, "COMMAND_FAILED", mid, {"error": "Timeout reaching target temperature"}, now_ms()))
                    logger.warning("Heat command timeout/failed on %s: current temp=%.2f°C", point, current)

            threading.Thread(target=_verify_temp, daemon=True).start()

        elif cmd_name in ("pressurize", "adjustPressure", "setPressure"):
            target_p = float(params.get("targetPressure", 0.10))
            duration = int(params.get("durationSec", 10))
            timeout = max(duration + 10, 20)

            self._cmd_queue.put({**write_base, "action": "set", "targetPressure": target_p})

            if self._event_handler:
                self._event_handler(AdapterEvent(point, "COMMAND_RUNNING", mid, {}, now_ms()))
                self._event_handler(AdapterEvent(point, "PRESSURIZE_STARTED", None, {"targetPressure": target_p}, now_ms()))

            def _verify_press():
                start = time.time()
                success = False
                while self._running and (time.time() - start) < timeout:
                    current = self._point_state[point]["pressure"]
                    if abs(current - target_p) <= 0.02:
                        success = True
                        break
                    time.sleep(0.5)

                current = self._point_state[point]["pressure"]
                if success and self._event_handler:
                    self._event_handler(AdapterEvent(point, "TARGET_PRESSURE_REACHED", None, {"pressure": current}, now_ms()))
                    self._event_handler(AdapterEvent(point, "COMMAND_COMPLETED", mid, {}, now_ms()))
                    logger.info("Pressure command completed on %s: current pressure=%.3f MPa", point, current)
                elif not success and self._event_handler:
                    self._event_handler(AdapterEvent(point, "COMMAND_FAILED", mid, {"error": "Timeout reaching target pressure"}, now_ms()))
                    logger.warning("Pressure command timeout/failed on %s: current pressure=%.3f MPa", point, current)

            threading.Thread(target=_verify_press, daemon=True).start()

        else:
            logger.warning("Unknown command on physical point %s: %s", point, cmd_name)
            if self._event_handler:
                self._event_handler(AdapterEvent(point, "COMMAND_FAILED", mid, {"error": f"Unsupported command {cmd_name}"}, now_ms()))
