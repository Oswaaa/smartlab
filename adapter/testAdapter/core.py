"""Headless PLC control core derived from the vendor remote-control program."""

from __future__ import annotations

import json
import logging
import threading
import time
from collections import OrderedDict
from dataclasses import dataclass
from typing import Any, Callable, Mapping

try:
    import paho.mqtt.client as mqtt
except ImportError:  # pragma: no cover - reported as a startup error
    mqtt = None


TelemetryHandler = Callable[[str, dict[str, Any], int], None]
EventHandler = Callable[[str, str, str | None, dict[str, Any], int], None]


@dataclass(frozen=True)
class PendingCommand:
    message_id: str
    command_name: str
    expected_registers: dict[str, int]
    deadline_ms: int


class Core:
    """Owns the PLC-side connection, device semantics, and command tracking."""

    _REGISTER_NAMES = ("MW0", "MW10", "MW20", "MW21", "MW22")
    _RESULT_HISTORY_LIMIT = 256

    def __init__(
        self,
        plc_config: Mapping[str, Any],
        device_point: str,
        command_timeout_sec: float,
    ) -> None:
        self._config = dict(plc_config)
        self.device_point = self._required_text(device_point, "devicePoint")
        self.device_sn = self._required_text(self._config.get("deviceSN"), "plc.deviceSN")
        self.data_topic = self._required_text(self._config.get("dataTopic"), "plc.dataTopic")
        self.command_topic = self._required_text(
            self._config.get("commandTopic"), "plc.commandTopic"
        )
        self.qos = self._positive_int(self._config.get("qos", 1), "plc.qos", allow_zero=True)
        if self.qos > 2:
            raise ValueError("plc.qos 必须是 0、1 或 2")
        self.command_timeout_ms = int(float(command_timeout_sec) * 1000)
        if self.command_timeout_ms <= 0:
            raise ValueError("commandTimeoutSec 必须大于 0")

        self._logger = logging.getLogger("smartlab.adapter.core")
        self._lock = threading.RLock()
        self._event_lock = threading.RLock()
        self._client = None
        self._connected = False
        self._stopping = False
        self._registers: dict[str, int | float] = {}
        self._baseline_ready = False
        self._inflight: set[str] = set()
        self._pending: dict[str, PendingCommand] = {}
        self._completed: OrderedDict[str, tuple[str, dict[str, Any]]] = OrderedDict()
        self._telemetry_handler: TelemetryHandler = lambda *_args: None
        self._event_handler: EventHandler = lambda *_args: None

    def set_handlers(
        self,
        telemetry_handler: TelemetryHandler,
        event_handler: EventHandler,
    ) -> None:
        if not callable(telemetry_handler) or not callable(event_handler):
            raise TypeError("Core handlers 必须可调用")
        self._telemetry_handler = telemetry_handler
        self._event_handler = event_handler

    def start(self) -> None:
        if mqtt is None:
            raise RuntimeError("缺少 paho-mqtt，请先执行 pip install -r requirements.txt")
        with self._lock:
            if self._client is not None:
                return
            self._stopping = False
            client = mqtt.Client(
                callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
                client_id=self._required_text(self._config.get("clientId"), "plc.clientId"),
                clean_session=True,
            )
            username = str(self._config.get("username", "")).strip()
            password = str(self._config.get("password", ""))
            if username:
                client.username_pw_set(username, password)
            client.reconnect_delay_set(2, 30)
            client.on_connect = self._on_connect
            client.on_disconnect = self._on_disconnect
            client.on_message = self._on_message
            self._client = client

        broker = self._required_text(self._config.get("broker"), "plc.broker")
        port = self._positive_int(self._config.get("port"), "plc.port")
        keep_alive = self._positive_int(
            self._config.get("keepAliveSec", 30), "plc.keepAliveSec"
        )
        self._logger.info("PLC MQTT 正在连接 %s:%s", broker, port)
        client.connect_async(broker, port, keepalive=keep_alive)
        client.loop_start()

    def stop(self) -> None:
        with self._lock:
            self._stopping = True
            client = self._client
            self._client = None
            self._connected = False
        if client is None:
            return
        try:
            client.disconnect()
        except Exception as exc:
            self._logger.warning("PLC MQTT 断开时出现异常: %s", exc)
        finally:
            client.loop_stop()

    def is_connected(self) -> bool:
        with self._lock:
            return self._connected

    def execute_command(
        self,
        message_id: str,
        device_point: str,
        command_name: str,
        parameters: Mapping[str, Any],
    ) -> None:
        message_id = self._required_text(message_id, "messageId")
        device_point = self._required_text(device_point, "devicePoint")
        command_name = self._required_text(command_name, "commandName")

        with self._lock:
            completed = self._completed.get(message_id)
            if completed is not None:
                event_name, payload = completed
                self._completed.move_to_end(message_id)
            else:
                event_name = ""
                payload = {}
            if message_id in self._inflight or message_id in self._pending:
                return
            if completed is None:
                self._inflight.add(message_id)

        if completed is not None:
            self._emit_event(event_name, message_id, dict(payload))
            return

        self._emit_event(
            "COMMAND_RECEIVED",
            message_id,
            {"commandName": command_name},
        )

        try:
            if device_point != self.device_point:
                raise ValueError(f"未知设备点: {device_point}")
            registers = self._translate_command(command_name, parameters)
        except (TypeError, ValueError) as exc:
            self._fail_command(message_id, command_name, str(exc))
            return

        with self._lock:
            connected = self._connected
            client = self._client
        if not connected or client is None:
            self._fail_command(message_id, command_name, "PLC MQTT 未连接")
            return

        wire_payload = json.dumps(
            [{"DeviceSN": self.device_sn, "TagData": [registers]}],
            ensure_ascii=False,
            separators=(",", ":"),
        )
        with self._event_lock:
            try:
                result = client.publish(self.command_topic, wire_payload, qos=self.qos)
            except Exception as exc:
                self._fail_command(message_id, command_name, f"PLC 指令发布异常: {exc}")
                return
            if result.rc != mqtt.MQTT_ERR_SUCCESS:
                self._fail_command(
                    message_id,
                    command_name,
                    f"PLC 指令发布失败 rc={result.rc}",
                )
                return

            pending = PendingCommand(
                message_id=message_id,
                command_name=command_name,
                expected_registers=registers,
                deadline_ms=self._now_ms() + self.command_timeout_ms,
            )
            with self._lock:
                self._inflight.discard(message_id)
                self._pending[message_id] = pending
            self._emit_event(
                "COMMAND_RUNNING",
                message_id,
                {"commandName": command_name},
            )

    def expire_commands(self, now_ms: int | None = None) -> None:
        current_time = self._now_ms() if now_ms is None else int(now_ms)
        expired: list[PendingCommand] = []
        with self._lock:
            for message_id, pending in list(self._pending.items()):
                if pending.deadline_ms <= current_time:
                    expired.append(self._pending.pop(message_id))
                    self._remember_result_locked(
                        message_id,
                        "COMMAND_TIMEOUT",
                        {"commandName": pending.command_name, "reason": "等待 PLC 状态确认超时"},
                    )
        for pending in expired:
            self._emit_event(
                "COMMAND_TIMEOUT",
                pending.message_id,
                {"commandName": pending.command_name, "reason": "等待 PLC 状态确认超时"},
            )

    def _translate_command(
        self,
        command_name: str,
        parameters: Mapping[str, Any],
    ) -> dict[str, int]:
        if not isinstance(parameters, Mapping):
            raise TypeError("parameters 必须是对象")
        if command_name == "setOperatingMode":
            mode = parameters.get("mode")
            if not isinstance(mode, str):
                raise TypeError("setOperatingMode.mode 必须是字符串")
            normalized = mode.strip().upper()
            if normalized == "AUTO":
                return {"MW21": 1, "MW22": 0}
            if normalized == "MANUAL":
                return {"MW21": 0, "MW22": 1}
            raise ValueError("setOperatingMode.mode 只允许 AUTO 或 MANUAL")

        if command_name == "setCooling":
            enabled = parameters.get("enabled")
            if type(enabled) is not bool:
                raise TypeError("setCooling.enabled 必须是 BOOLEAN")
            if enabled:
                with self._lock:
                    if not self._baseline_ready:
                        raise ValueError("尚未收到 PLC 状态，不能开启散热")
                    if not (int(self._registers.get("MW22", 0)) & 1):
                        raise ValueError("只有手动模式才能开启散热")
            return {"MW20": 1 if enabled else 0}

        if command_name == "setAlarm":
            enabled = parameters.get("enabled")
            if type(enabled) is not bool:
                raise TypeError("setAlarm.enabled 必须是 BOOLEAN")
            return {"MW10": 1 if enabled else 0}

        raise ValueError(f"未知命令: {command_name}")

    def _on_connect(self, client, _userdata, _flags, reason_code, _properties=None) -> None:
        failed = getattr(reason_code, "is_failure", False)
        if failed or (isinstance(reason_code, int) and reason_code != 0):
            self._logger.error("PLC MQTT 连接失败 rc=%s", reason_code)
            return
        with self._lock:
            self._connected = True
        client.subscribe(self.data_topic, qos=self.qos)
        self._logger.info("PLC MQTT 已连接并订阅 %s", self.data_topic)

    def _on_disconnect(
        self,
        _client,
        _userdata,
        _disconnect_flags,
        reason_code,
        _properties=None,
    ) -> None:
        with self._lock:
            self._connected = False
            stopping = self._stopping
        if not stopping:
            self._logger.warning("PLC MQTT 已断开 rc=%s，等待自动重连", reason_code)

    def _on_message(self, _client, _userdata, message) -> None:
        try:
            document = json.loads(message.payload)
            entries = document if isinstance(document, list) else [document]
            entry = next(
                (
                    item
                    for item in entries
                    if isinstance(item, dict) and item.get("DeviceSN") == self.device_sn
                ),
                None,
            )
            if entry is None:
                self._logger.warning("PLC 数据中未找到目标 DeviceSN=%s", self.device_sn)
                return
            rows = entry.get("TagData")
            if not isinstance(rows, list) or not rows or not isinstance(rows[-1], dict):
                raise ValueError("TagData 必须是非空对象数组")
            registers = self._validated_registers(rows[-1])
            if not registers:
                raise ValueError("TagData 中没有受支持的 MW 寄存器")
            self._handle_registers(registers)
        except (json.JSONDecodeError, TypeError, ValueError) as exc:
            self._logger.error("PLC 数据无效 topic=%s: %s", message.topic, exc)
        except Exception:
            self._logger.exception("处理 PLC 数据时发生未预期异常")

    def _validated_registers(self, row: Mapping[str, Any]) -> dict[str, int | float]:
        result: dict[str, int | float] = {}
        for name in self._REGISTER_NAMES:
            if name not in row:
                continue
            value = row[name]
            if isinstance(value, bool) or not isinstance(value, (int, float)):
                raise TypeError(f"{name} 必须是数值")
            result[name] = value
        return result

    def _handle_registers(self, new_values: Mapping[str, int | float]) -> None:
        timestamp_ms = self._now_ms()
        op_events: list[tuple[str, dict[str, Any]]] = []
        completed_commands: list[PendingCommand] = []

        with self._lock:
            previous = dict(self._registers)
            had_baseline = self._baseline_ready
            self._registers.update(new_values)
            current = dict(self._registers)
            self._baseline_ready = True

            if had_baseline:
                transitions = (
                    ("MW21", "AUTOMATIC_MODE_ENTERED", None),
                    ("MW22", "MANUAL_MODE_ENTERED", None),
                    ("MW20", "COOLING_STARTED", "COOLING_STOPPED"),
                    ("MW10", "ALARM_TRIGGERED", "ALARM_CLEARED"),
                )
                for register, enabled_event, disabled_event in transitions:
                    if register not in previous or register not in current:
                        continue
                    before = bool(int(previous[register]) & 1)
                    after = bool(int(current[register]) & 1)
                    if before == after:
                        continue
                    event_name = enabled_event if after else disabled_event
                    if event_name is not None:
                        op_events.append((event_name, {"active": after}))

            for message_id, pending in list(self._pending.items()):
                if all(current.get(name) == value for name, value in pending.expected_registers.items()):
                    completed_commands.append(self._pending.pop(message_id))
                    self._remember_result_locked(
                        message_id,
                        "COMMAND_COMPLETED",
                        {"commandName": pending.command_name},
                    )

        if "MW0" in current:
            self._telemetry_handler(
                self.device_point,
                {"temperature": float(current["MW0"]) / 100.0},
                timestamp_ms,
            )
        for event_name, payload in op_events:
            self._emit_event(event_name, None, payload, timestamp_ms)
        for pending in completed_commands:
            self._emit_event(
                "COMMAND_COMPLETED",
                pending.message_id,
                {"commandName": pending.command_name},
                timestamp_ms,
            )

    def _fail_command(self, message_id: str, command_name: str, reason: str) -> None:
        payload = {"commandName": command_name, "reason": reason}
        with self._lock:
            self._inflight.discard(message_id)
            self._pending.pop(message_id, None)
            self._remember_result_locked(message_id, "COMMAND_FAILED", payload)
        self._emit_event("COMMAND_FAILED", message_id, payload)

    def _remember_result_locked(
        self,
        message_id: str,
        event_name: str,
        payload: Mapping[str, Any],
    ) -> None:
        self._completed[message_id] = (event_name, dict(payload))
        self._completed.move_to_end(message_id)
        while len(self._completed) > self._RESULT_HISTORY_LIMIT:
            self._completed.popitem(last=False)

    def _emit_event(
        self,
        event_name: str,
        message_id: str | None,
        payload: Mapping[str, Any],
        timestamp_ms: int | None = None,
    ) -> None:
        with self._event_lock:
            self._event_handler(
                self.device_point,
                event_name,
                message_id,
                dict(payload),
                self._now_ms() if timestamp_ms is None else int(timestamp_ms),
            )

    @staticmethod
    def _now_ms() -> int:
        return int(time.time() * 1000)

    @staticmethod
    def _required_text(value: Any, field: str) -> str:
        text = "" if value is None else str(value).strip()
        if not text:
            raise ValueError(f"{field} 不能为空")
        return text

    @staticmethod
    def _positive_int(value: Any, field: str, allow_zero: bool = False) -> int:
        try:
            parsed = int(value)
        except (TypeError, ValueError) as exc:
            raise ValueError(f"{field} 必须是整数") from exc
        invalid = parsed < 0 if allow_zero else parsed <= 0
        if invalid:
            raise ValueError(f"{field} 必须{'大于等于' if allow_zero else '大于'} 0")
        return parsed

