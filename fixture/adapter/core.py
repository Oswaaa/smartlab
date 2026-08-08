"""
Fixture adapter core: reads temperature from simulated device,
executes heat/cool commands, and emits telemetry + events.
"""

import json
import logging
import socket
import threading
import time
from dataclasses import dataclass, field
from typing import Callable

logger = logging.getLogger("fixture.adapter.core")

@dataclass
class DeviceReading:
    temperature: float
    timestamp: float

class Core:
    """Business logic for the fixture temperature adapter."""

    def __init__(self, device_host="127.0.0.1", device_port=9999, command_timeout=30):
        self.host = device_host
        self.port = device_port
        self.command_timeout = command_timeout
        self._sock = None
        self._running = False
        self._current_temp = 0.0
        self._on_telemetry: Callable | None = None
        self._on_event: Callable | None = None
        self._command_lock = threading.Lock()

    def on_telemetry(self, callback):
        self._on_telemetry = callback

    def on_event(self, callback):
        self._on_event = callback

    def current_temperature(self):
        return self._current_temp

    def connect(self):
        self._sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._sock.connect((self.host, self.port))
        self._sock.settimeout(5.0)
        logger.info("Connected to device at %s:%d", self.host, self.port)

    def start_reading(self):
        self._running = True
        self._sock.settimeout(1.0)
        def _loop():
            buffer = ""
            while self._running:
                try:
                    data = self._sock.recv(1024).decode()
                    if not data:
                        logger.warning("Device connection closed")
                        break
                    buffer += data
                    while "\n" in buffer:
                        line, buffer = buffer.split("\n", 1)
                        if not line.strip(): continue
                        reading = json.loads(line)
                        
                        if "event" in reading:
                            if self._on_event:
                                self._on_event(reading["event"], reading.get("messageId", ""))
                        
                        if "temperature" in reading:
                            self._current_temp = reading["temperature"]
                            if self._on_telemetry:
                                self._on_telemetry({"temperature": self._current_temp})
                except socket.timeout:
                    continue
                except Exception as e:
                    logger.error("Read error: %s", e)
                    break
        t = threading.Thread(target=_loop, daemon=True)
        t.start()

    def execute_heat(self, target_temp: float, hold_sec: int, message_id: str):
        """Send heat command to the device."""
        with self._command_lock:
            cmd = {
                "command": "heat",
                "targetTemperature": target_temp,
                "holdDurationSec": hold_sec,
                "messageId": message_id
            }
            logger.info("Sending heat command: %s", cmd)
            try:
                self._sock.sendall((json.dumps(cmd) + "\n").encode())
            except Exception as e:
                logger.error("Failed to send heat command: %s", e)

    def execute_cool(self, duration_sec: int, message_id: str):
        """Send cool command to the device."""
        with self._command_lock:
            cmd = {
                "command": "cool",
                "durationSec": duration_sec,
                "messageId": message_id
            }
            logger.info("Sending cool command: %s", cmd)
            try:
                self._sock.sendall((json.dumps(cmd) + "\n").encode())
            except Exception as e:
                logger.error("Failed to send cool command: %s", e)

    def stop(self):
        self._running = False
        if self._sock:
            try: self._sock.close()
            except: pass
