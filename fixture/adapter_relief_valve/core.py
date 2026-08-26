"""
Fixture relief valve adapter core: reads pressure and valveOpening from simulated relief valve,
executes releasePressure/adjustPressure/emergencyVent commands, and emits telemetry + events.
"""

import json
import logging
import socket
import threading
import time
from dataclasses import dataclass
from typing import Callable

logger = logging.getLogger("fixture.adapter.relief_valve.core")

@dataclass
class ValveReading:
    pressure: float
    valveOpening: float
    timestamp: float

class Core:
    """Business logic for the fixture relief valve adapter."""

    def __init__(self, device_host="127.0.0.1", device_port=9998, command_timeout=30):
        self.host = device_host
        self.port = device_port
        self.command_timeout = command_timeout
        self._sock = None
        self._running = False
        self._current_pressure = 0.0
        self._current_opening = 0.0
        self._on_telemetry: Callable | None = None
        self._on_event: Callable | None = None
        self._command_lock = threading.Lock()

    def on_telemetry(self, callback):
        self._on_telemetry = callback

    def on_event(self, callback):
        self._on_event = callback

    def current_pressure(self):
        return self._current_pressure

    def current_valve_opening(self):
        return self._current_opening

    def connect(self):
        self._sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._sock.connect((self.host, self.port))
        self._sock.settimeout(5.0)
        logger.info("Connected to relief valve device at %s:%d", self.host, self.port)

    def start_reading(self):
        self._running = True
        self._sock.settimeout(1.0)

        def _loop():
            buffer = ""
            while self._running:
                try:
                    data = self._sock.recv(1024).decode()
                    if not data:
                        logger.warning("Relief valve connection closed by device")
                        break
                    buffer += data
                    while "\n" in buffer:
                        line, buffer = buffer.split("\n", 1)
                        if not line.strip():
                            continue
                        reading = json.loads(line)

                        if "event" in reading:
                            if self._on_event:
                                self._on_event(reading["event"], reading.get("messageId", ""))

                        if "pressure" in reading:
                            self._current_pressure = float(reading["pressure"])
                            self._current_opening = float(reading.get("valveOpening", 0.0))
                            if self._on_telemetry:
                                self._on_telemetry({
                                    "pressure": self._current_pressure,
                                    "valveOpening": self._current_opening
                                })
                except socket.timeout:
                    continue
                except Exception as e:
                    logger.error("Read error from relief valve device: %s", e)
                    break

        t = threading.Thread(target=_loop, daemon=True)
        t.start()

    def execute_release(self, target_pressure: float, release_duration_sec: int, message_id: str):
        """Send releasePressure command to the relief valve."""
        with self._command_lock:
            cmd = {
                "command": "releasePressure",
                "targetPressure": target_pressure,
                "releaseDurationSec": release_duration_sec,
                "messageId": message_id
            }
            logger.info("Sending releasePressure command: %s", cmd)
            try:
                self._sock.sendall((json.dumps(cmd) + "\n").encode())
            except Exception as e:
                logger.error("Failed to send releasePressure command: %s", e)

    def execute_adjust(self, target_pressure: float, duration_sec: int, message_id: str):
        """Send adjustPressure command to the relief valve."""
        with self._command_lock:
            cmd = {
                "command": "adjustPressure",
                "targetPressure": target_pressure,
                "durationSec": duration_sec,
                "messageId": message_id
            }
            logger.info("Sending adjustPressure command: %s", cmd)
            try:
                self._sock.sendall((json.dumps(cmd) + "\n").encode())
            except Exception as e:
                logger.error("Failed to send adjustPressure command: %s", e)

    def execute_emergency_vent(self, duration_sec: int, message_id: str):
        """Send emergencyVent command to the relief valve."""
        with self._command_lock:
            cmd = {
                "command": "emergencyVent",
                "durationSec": duration_sec,
                "messageId": message_id
            }
            logger.info("Sending emergencyVent command: %s", cmd)
            try:
                self._sock.sendall((json.dumps(cmd) + "\n").encode())
            except Exception as e:
                logger.error("Failed to send emergencyVent command: %s", e)

    def stop(self):
        self._running = False
        if self._sock:
            try:
                self._sock.close()
            except Exception:
                pass
