"""SmartLab-facing runtime for the headless PLC Adapter."""

from __future__ import annotations

import configparser
import json
import logging
import threading
import time
from collections import deque
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

try:
    import paho.mqtt.client as mqtt
except ImportError:  # pragma: no cover - reported as a startup error
    mqtt = None

from core import Core


@dataclass(frozen=True)
class RuntimeConfig:
    smartlab: dict[str, str]
    plc: dict[str, str]
    heartbeat_interval_sec: float
    command_timeout_sec: float
    log_level: str

    @classmethod
    def load(cls, path: Path) -> "RuntimeConfig":
        parser = _read_ini(path)
        for section in ("smartlab", "plc", "runtime", "logging"):
            if not parser.has_section(section):
                raise ValueError(f"runtime.ini 缺少 [{section}] section")

        smartlab = dict(parser.items("smartlab"))
        plc = dict(parser.items("plc"))
        _require_keys(
            smartlab,
            "smartlab",
            ("broker", "port", "clientId", "keepAliveSec", "qos"),
        )
        _require_keys(
            plc,
            "plc",
            (
                "broker",
                "port",
                "clientId",
                "keepAliveSec",
                "qos",
                "dataTopic",
                "commandTopic",
                "deviceSN",
            ),
        )
        heartbeat_interval = _positive_float(
            parser.get("runtime", "heartbeatIntervalSec", fallback="10"),
            "runtime.heartbeatIntervalSec",
        )
        command_timeout = _positive_float(
            parser.get("runtime", "commandTimeoutSec", fallback="15"),
            "runtime.commandTimeoutSec",
        )
        log_level = parser.get("logging", "level", fallback="INFO").strip().upper()
        if log_level not in {"DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"}:
            raise ValueError("logging.level 必须是 DEBUG/INFO/WARNING/ERROR/CRITICAL")
        return cls(smartlab, plc, heartbeat_interval, command_timeout, log_level)


@dataclass(frozen=True)
class AdapterSetup:
    raw_content: str
    adapter_name: str
    raw_config_format: str
    device_points: tuple[str, ...]

    @classmethod
    def load(cls, path: Path) -> "AdapterSetup":
        raw_content = path.read_text(encoding="utf-8-sig")
        parser = _read_ini(path)
        if not parser.has_section("adapter"):
            raise ValueError("adapterSetup.ini 缺少 [adapter] section")

        adapter_name = _required_text(
            parser.get("adapter", "adapterName", fallback=""),
            "adapter.adapterName",
        )
        _topic_segment(adapter_name, "adapter.adapterName")
        raw_format = parser.get("adapter", "rawConfigFormat", fallback="").strip().upper()
        if raw_format != "INI":
            raise ValueError("adapter.rawConfigFormat 必须是 INI")

        device_points: list[str] = []
        for section in parser.sections():
            parts = section.split(".")
            if len(parts) == 2 and parts[0] == "devicePoints":
                point = _topic_segment(parts[1], f"[{section}]")
                device_points.append(point)
        if len(device_points) != 1:
            raise ValueError("当前 PLC Adapter 要求 adapterSetup.ini 恰好声明一个 devicePoint")

        point = device_points[0]
        mapping_section = f"devicePoints.{point}.attributeMapping"
        if not parser.has_section(mapping_section):
            raise ValueError(f"adapterSetup.ini 缺少 [{mapping_section}]")
        mappings = dict(parser.items(mapping_section))
        if mappings != {"temperature": "temperature"}:
            raise ValueError("公开属性必须且只能映射 temperature = temperature")

        return cls(raw_content, adapter_name, raw_format, tuple(device_points))


class AdapterRuntime:
    """Owns SmartLab MQTT registration, heartbeat, routing, and publishing."""

    REGISTER_TOPIC = "smartlab/adapter/register"
    COMMAND_EVENTS = {
        "COMMAND_RECEIVED",
        "COMMAND_RUNNING",
        "COMMAND_COMPLETED",
        "COMMAND_FAILED",
        "COMMAND_TIMEOUT",
    }

    def __init__(self, config: RuntimeConfig, setup: AdapterSetup, core: Core) -> None:
        self.config = config
        self.setup = setup
        self.core = core
        self._logger = logging.getLogger("smartlab.adapter.runtime")
        self._lock = threading.RLock()
        self._outbound_lock = threading.RLock()
        self._stop_event = threading.Event()
        self._maintenance_thread: threading.Thread | None = None
        self._client = None
        self._connected = False
        self._stopping = False
        self._pending_subscriptions: dict[int, str] = {}
        self._queued_command_events: deque[
            tuple[str, dict[str, Any], tuple[str, str]]
        ] = deque()
        self._queued_command_event_keys: set[tuple[str, str]] = set()
        self._command_topics = {
            self._command_topic(point): point for point in self.setup.device_points
        }
        self.core.set_handlers(self.publish_telemetry, self.publish_event)

    def start(self) -> None:
        if mqtt is None:
            raise RuntimeError("缺少 paho-mqtt，请先执行 pip install -r requirements.txt")
        with self._lock:
            if self._client is not None:
                return
            self._stopping = False
            self._stop_event.clear()
            client = mqtt.Client(
                callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
                client_id=_required_text(
                    self.config.smartlab.get("clientId"), "smartlab.clientId"
                ),
                clean_session=True,
            )
            username = str(self.config.smartlab.get("username", "")).strip()
            password = str(self.config.smartlab.get("password", ""))
            if username:
                client.username_pw_set(username, password)
            client.reconnect_delay_set(2, 30)
            client.on_connect = self._on_connect
            client.on_disconnect = self._on_disconnect
            client.on_message = self._on_message
            client.on_subscribe = self._on_subscribe
            self._client = client

        self.core.start()
        broker = _required_text(self.config.smartlab.get("broker"), "smartlab.broker")
        port = _positive_int(self.config.smartlab.get("port"), "smartlab.port")
        keep_alive = _positive_int(
            self.config.smartlab.get("keepAliveSec", "30"),
            "smartlab.keepAliveSec",
        )
        self._logger.info("SmartLab MQTT 正在连接 %s:%s", broker, port)
        try:
            client.connect_async(broker, port, keepalive=keep_alive)
            client.loop_start()
            maintenance = threading.Thread(
                target=self._maintenance_loop,
                name="adapter-maintenance",
                daemon=True,
            )
            self._maintenance_thread = maintenance
            maintenance.start()
        except Exception:
            self.core.stop()
            with self._lock:
                self._client = None
            raise

    def stop(self) -> None:
        with self._lock:
            self._stopping = True
            self._connected = False
            client = self._client
            self._client = None
            maintenance = self._maintenance_thread
            self._maintenance_thread = None
        self._stop_event.set()
        if client is not None:
            try:
                client.disconnect()
            except Exception as exc:
                self._logger.warning("SmartLab MQTT 断开时出现异常: %s", exc)
            finally:
                client.loop_stop()
        if maintenance is not None and maintenance is not threading.current_thread():
            maintenance.join(timeout=3)
        self.core.stop()

    def is_connected(self) -> bool:
        with self._lock:
            return self._connected

    def publish_registration(self) -> None:
        self._publish_json(
            self.REGISTER_TOPIC,
            {
                "adapterName": self.setup.adapter_name,
                "rawConfigFormat": self.setup.raw_config_format,
                "rawConfigContent": self.setup.raw_content,
                "timestamp": self._now_ms(),
            },
        )

    def publish_heartbeat(self) -> None:
        self._publish_json(
            self._heartbeat_topic(),
            {"status": "ALIVE", "timestamp": self._now_ms()},
        )

    def publish_telemetry(
        self,
        device_point: str,
        data: dict[str, Any],
        timestamp_ms: int,
    ) -> None:
        if set(data) != {"temperature"}:
            self._logger.error("拒绝上报未声明的遥测字段: %s", sorted(data))
            return
        self._publish_json(
            self._telemetry_topic(device_point),
            {
                "timestamp": int(timestamp_ms),
                "adapterName": self.setup.adapter_name,
                "devicePoint": device_point,
                "telemetryData": data,
            },
        )

    def publish_event(
        self,
        device_point: str,
        event_name: str,
        message_id: str | None,
        payload: dict[str, Any],
        timestamp_ms: int,
    ) -> None:
        message: dict[str, Any] = {
            "timestamp": int(timestamp_ms),
            "adapterName": self.setup.adapter_name,
            "devicePoint": device_point,
            "eventName": event_name,
        }
        if message_id is not None:
            message["messageId"] = message_id
        if payload:
            message["payload"] = payload
        topic = self._event_topic(device_point)
        if message_id is None or event_name not in self.COMMAND_EVENTS:
            self._publish_json(topic, message)
            return

        key = (message_id, event_name)
        with self._outbound_lock:
            if key in self._queued_command_event_keys:
                return
            if self._queued_command_events:
                self._enqueue_command_event_locked(topic, message, key)
                self._flush_command_events_locked()
                return
            if not self._publish_json(topic, message):
                self._enqueue_command_event_locked(topic, message, key)

    def _on_connect(self, client, _userdata, _flags, reason_code, _properties=None) -> None:
        failed = getattr(reason_code, "is_failure", False)
        if failed or (isinstance(reason_code, int) and reason_code != 0):
            self._logger.error("SmartLab MQTT 连接失败 rc=%s", reason_code)
            return
        with self._lock:
            self._connected = True
            self._pending_subscriptions.clear()
        qos = self._qos()
        accepted = 0
        for topic in self._command_topics:
            result, message_id = client.subscribe(topic, qos=qos)
            if result != mqtt.MQTT_ERR_SUCCESS:
                self._logger.error(
                    "SmartLab MQTT 订阅请求失败 topic=%s rc=%s",
                    topic,
                    result,
                )
                continue
            with self._lock:
                self._pending_subscriptions[message_id] = topic
            accepted += 1
        self._logger.info(
            "SmartLab MQTT 已连接，等待 %s 个命令订阅确认",
            accepted,
        )
        with self._outbound_lock:
            self.publish_registration()
            self.publish_heartbeat()
            self._flush_command_events_locked()

    def _on_subscribe(
        self,
        _client,
        _userdata,
        message_id,
        reason_codes,
        _properties=None,
    ) -> None:
        with self._lock:
            topic = self._pending_subscriptions.pop(message_id, "未知主题")
        failures = [
            code
            for code in reason_codes
            if getattr(code, "is_failure", False)
            or (isinstance(code, int) and code >= 128)
        ]
        if failures:
            self._logger.error(
                "SmartLab MQTT 命令订阅被拒绝 topic=%s reasons=%s",
                topic,
                failures,
            )
        else:
            self._logger.info("SmartLab MQTT 已订阅命令主题 %s", topic)

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
            self._pending_subscriptions.clear()
            stopping = self._stopping
        if not stopping:
            self._logger.warning("SmartLab MQTT 已断开 rc=%s，等待自动重连", reason_code)

    def _on_message(self, _client, _userdata, message) -> None:
        try:
            device_point = self._command_topics.get(message.topic)
            if device_point is None:
                raise ValueError(f"未知命令主题: {message.topic}")
            payload = json.loads(message.payload)
            if not isinstance(payload, dict):
                raise TypeError("命令 payload 必须是对象")
            required = (
                "messageId",
                "adapterName",
                "devicePoint",
                "commandName",
                "parameters",
                "timestamp",
            )
            missing = [name for name in required if name not in payload]
            if missing:
                raise ValueError(f"命令 payload 缺少字段: {', '.join(missing)}")

            message_id = _required_text(payload["messageId"], "messageId")
            adapter_name = _required_text(payload["adapterName"], "adapterName")
            payload_point = _required_text(payload["devicePoint"], "devicePoint")
            command_name = _required_text(payload["commandName"], "commandName")
            parameters = payload["parameters"]
            timestamp = payload["timestamp"]
            if adapter_name != self.setup.adapter_name or payload_point != device_point:
                raise ValueError("命令 topic 与 payload 身份不一致")
            if not isinstance(parameters, dict):
                raise TypeError("parameters 必须是对象")
            if isinstance(timestamp, bool) or not isinstance(timestamp, int):
                raise TypeError("timestamp 必须是整数毫秒时间戳")

            self.core.execute_command(
                message_id,
                device_point,
                command_name,
                parameters,
            )
        except (UnicodeDecodeError, json.JSONDecodeError, TypeError, ValueError) as exc:
            self._logger.error("SmartLab 命令无效 topic=%s: %s", message.topic, exc)
        except Exception:
            self._logger.exception("处理 SmartLab 命令时发生未预期异常")

    def _enqueue_command_event_locked(
        self,
        topic: str,
        message: dict[str, Any],
        key: tuple[str, str],
    ) -> None:
        self._queued_command_events.append((topic, dict(message), key))
        self._queued_command_event_keys.add(key)
        self._logger.warning(
            "SmartLab MQTT 当前不可用，暂存命令事件 messageId=%s event=%s",
            key[0],
            key[1],
        )

    def _flush_command_events_locked(self) -> None:
        while self._queued_command_events:
            topic, message, key = self._queued_command_events[0]
            if not self._publish_json(topic, message):
                return
            self._queued_command_events.popleft()
            self._queued_command_event_keys.discard(key)
            self._logger.info(
                "已补发命令事件 messageId=%s event=%s",
                key[0],
                key[1],
            )

    def _maintenance_loop(self) -> None:
        next_heartbeat = time.monotonic() + self.config.heartbeat_interval_sec
        while not self._stop_event.wait(0.5):
            self.core.expire_commands()
            now = time.monotonic()
            if now >= next_heartbeat:
                self.publish_heartbeat()
                next_heartbeat = now + self.config.heartbeat_interval_sec

    def _publish_json(self, topic: str, payload: Mapping[str, Any]) -> bool:
        with self._lock:
            client = self._client
            connected = self._connected
        if client is None or not connected:
            self._logger.debug("SmartLab MQTT 未连接，丢弃本次发布 topic=%s", topic)
            return False
        try:
            encoded = json.dumps(
                payload,
                ensure_ascii=False,
                separators=(",", ":"),
            )
            result = client.publish(topic, encoded, qos=self._qos())
            if result.rc != mqtt.MQTT_ERR_SUCCESS:
                self._logger.error("SmartLab MQTT 发布失败 topic=%s rc=%s", topic, result.rc)
                return False
            return True
        except Exception:
            self._logger.exception("SmartLab MQTT 发布异常 topic=%s", topic)
            return False

    def _qos(self) -> int:
        qos = _positive_int(
            self.config.smartlab.get("qos", "1"),
            "smartlab.qos",
            allow_zero=True,
        )
        if qos > 2:
            raise ValueError("smartlab.qos 必须是 0、1 或 2")
        return qos

    def _heartbeat_topic(self) -> str:
        return f"smartlab/adapter/{self.setup.adapter_name}/heartbeat"

    def _command_topic(self, device_point: str) -> str:
        return f"smartlab/adapter/{self.setup.adapter_name}/{device_point}/command"

    def _telemetry_topic(self, device_point: str) -> str:
        return f"smartlab/adapter/{self.setup.adapter_name}/{device_point}/telemetry"

    def _event_topic(self, device_point: str) -> str:
        return f"smartlab/adapter/{self.setup.adapter_name}/{device_point}/event"

    @staticmethod
    def _now_ms() -> int:
        return int(time.time() * 1000)


def _read_ini(path: Path) -> configparser.ConfigParser:
    if not path.is_file():
        raise FileNotFoundError(f"配置文件不存在: {path}")
    parser = configparser.ConfigParser(interpolation=None)
    parser.optionxform = str
    with path.open("r", encoding="utf-8-sig") as stream:
        parser.read_file(stream)
    return parser


def _require_keys(values: Mapping[str, Any], section: str, keys: tuple[str, ...]) -> None:
    for key in keys:
        _required_text(values.get(key), f"{section}.{key}")


def _required_text(value: Any, field: str) -> str:
    text = "" if value is None else str(value).strip()
    if not text:
        raise ValueError(f"{field} 不能为空")
    return text


def _topic_segment(value: Any, field: str) -> str:
    text = _required_text(value, field)
    if any(character in text for character in ("/", "+", "#")):
        raise ValueError(f"{field} 不能包含 MQTT topic 分隔符或通配符")
    return text


def _positive_int(value: Any, field: str, allow_zero: bool = False) -> int:
    try:
        parsed = int(value)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"{field} 必须是整数") from exc
    invalid = parsed < 0 if allow_zero else parsed <= 0
    if invalid:
        raise ValueError(f"{field} 必须{'大于等于' if allow_zero else '大于'} 0")
    return parsed


def _positive_float(value: Any, field: str) -> float:
    try:
        parsed = float(value)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"{field} 必须是数值") from exc
    if parsed <= 0:
        raise ValueError(f"{field} 必须大于 0")
    return parsed
