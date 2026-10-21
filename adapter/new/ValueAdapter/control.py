"""GpioValveAdapter 南向驱动：经 MQTT 控制 GPIO 电磁阀控制器。只处理物理点，不知道 SmartLab 主题和租约。"""

from __future__ import annotations

import json
import logging
import os
import threading
import time
import uuid
from collections import deque
from dataclasses import dataclass
from typing import Any, Callable

import paho.mqtt.client as mqtt

from core import AdapterEvent, DeviceData, SystemCommand, now_ms

logger = logging.getLogger("smartlab.adapter.gpiovalve.control")

COMMAND_TARGETS = {"open": 1, "close": 0}
RAW_FIELD = "raw_open"
SEEN_MESSAGE_LIMIT = 256
WATCHDOG_TICK_SEC = 0.2


def op_event_name(value: int) -> str:
    return "VALVE_OPENED" if value == 1 else "VALVE_CLOSED"


def parse_state_payload(payload: bytes | str, channel_count: int) -> list[int] | None:
    """解析 {"gpios":[0,1,...],"timestamp":...,"rssi":...}；任何一项不合法都整包丢弃。"""
    try:
        body = json.loads(payload)
    except (ValueError, UnicodeDecodeError):
        return None
    if not isinstance(body, dict):
        return None
    gpios = body.get("gpios")
    if not isinstance(gpios, list) or len(gpios) != channel_count:
        return None
    values: list[int] = []
    for value in gpios:
        if isinstance(value, bool) or not isinstance(value, int) or value not in (0, 1):
            return None
        values.append(value)
    return values


def _topic(value: str, key: str) -> str:
    text = str(value or "").strip()
    if not text or "+" in text or "#" in text:
        raise ValueError(f"[gpio] {key} 必须是不含通配符的具体主题")
    return text


@dataclass(frozen=True)
class GpioSettings:
    broker: str
    port: int
    username: str
    password: str
    client_id: str
    keep_alive_sec: int
    qos: int
    state_topic: str
    set_topic: str
    channel_count: int
    confirm_timeout_sec: float
    resend_interval_sec: float
    state_timeout_sec: float
    telemetry_interval_sec: float
    op_resync_sec: float

    @classmethod
    def from_section(cls, section: dict[str, str]) -> "GpioSettings":
        if not section:
            raise ValueError("runtime.ini 缺少 [gpio] 段")
        broker = str(section.get("broker", "")).strip()
        if not broker:
            raise ValueError("[gpio] broker 不能为空")
        settings = cls(
            broker=broker,
            port=int(section.get("port", "1883")),
            username=str(section.get("username", "")).strip(),
            password=str(section.get("password", "")),
            client_id=str(section.get("clientId", "smartlab_gpio_valve_south")).strip(),
            keep_alive_sec=int(section.get("keepAliveSec", "30")),
            qos=int(section.get("qos", "1")),
            state_topic=_topic(section.get("stateTopic", ""), "stateTopic"),
            set_topic=_topic(section.get("setTopic", ""), "setTopic"),
            channel_count=int(section.get("channelCount", "16")),
            confirm_timeout_sec=float(section.get("confirmTimeoutSec", "8.0")),
            resend_interval_sec=float(section.get("resendIntervalSec", "2.0")),
            state_timeout_sec=float(section.get("stateTimeoutSec", "30.0")),
            telemetry_interval_sec=float(section.get("telemetryIntervalSec", "30")),
            op_resync_sec=float(section.get("opResyncSec", "30")),
        )
        settings.validate()
        return settings

    def validate(self) -> None:
        if not 1 <= self.port <= 65535:
            raise ValueError("[gpio] port 超出范围")
        if not self.client_id:
            raise ValueError("[gpio] clientId 不能为空")
        if self.qos not in (0, 1):
            raise ValueError("[gpio] qos 只能是 0 或 1")
        if self.state_topic == self.set_topic:
            raise ValueError("[gpio] stateTopic 与 setTopic 不能相同")
        if self.channel_count < 1:
            raise ValueError("[gpio] channelCount 必须 >= 1")
        if not 0.5 <= self.confirm_timeout_sec <= 60:
            raise ValueError("[gpio] confirmTimeoutSec 必须在 0.5~60")
        if self.resend_interval_sec != 0 and not 0.2 <= self.resend_interval_sec < self.confirm_timeout_sec:
            raise ValueError("[gpio] resendIntervalSec 为 0（关闭）或 0.2~confirmTimeoutSec")
        if not 1 <= self.state_timeout_sec <= 300:
            raise ValueError("[gpio] stateTimeoutSec 必须在 1~300")
        if not 1 <= self.telemetry_interval_sec <= 600:
            raise ValueError("[gpio] telemetryIntervalSec 必须在 1~600")
        if self.op_resync_sec != 0 and not 10 <= self.op_resync_sec <= 3600:
            raise ValueError("[gpio] opResyncSec 为 0（关闭）或 10~3600")


def build_gpio_map(
    physical_points: tuple[str, ...] | list[str],
    point_fields: dict[str, dict[str, str]],
    channel_count: int,
) -> dict[str, int]:
    if not physical_points:
        raise ValueError("ControlImpl 需要至少一个物理点（来自 adapterSetup.ini）")
    gpio_by_point: dict[str, int] = {}
    owner: dict[int, str] = {}
    for point in physical_points:
        raw = str(point_fields.get(point, {}).get("gpio", "")).strip()
        if not raw.isdigit():
            raise ValueError(f"设备点 {point} 缺少合法的 gpio 字段: {raw!r}")
        gpio = int(raw)
        if gpio >= channel_count:
            raise ValueError(f"设备点 {point} 的 gpio={gpio} 超出 channelCount={channel_count}")
        if gpio in owner:
            raise ValueError(f"gpio={gpio} 被 {owner[gpio]} 与 {point} 重复占用")
        owner[gpio] = point
        gpio_by_point[point] = gpio
    return gpio_by_point


@dataclass
class PendingCommand:
    message_id: str
    command_name: str
    gpio: int
    target: int
    deadline: float
    next_resend: float | None


class ControlImpl:
    def __init__(
        self,
        extra: dict[str, dict[str, str]],
        physical_points: tuple[str, ...] | list[str],
        point_fields: dict[str, dict[str, str]] | None = None,
    ) -> None:
        self.settings = GpioSettings.from_section(extra.get("gpio", {}))
        self.physical_points = tuple(physical_points)
        self._gpio_by_point = build_gpio_map(self.physical_points, point_fields or {}, self.settings.channel_count)
        self._data_handler: Callable[[DeviceData], None] | None = None
        self._event_handler: Callable[[AdapterEvent], None] | None = None
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self._client: Any = None
        self._watchdog: threading.Thread | None = None
        self._link_up = False
        self._baseline_pending = True
        self._known: dict[str, int] = {}
        self._pending: dict[str, PendingCommand] = {}
        self._last_report_mono: float | None = None
        self._last_telemetry_mono: dict[str, float] = {}
        self._last_resync_mono = 0.0
        self._online_logged = False
        self._last_invalid_log = 0.0
        self._seen_ids: deque[str] = deque()
        self._seen_set: set[str] = set()

    def set_data_handler(self, handler: Callable[[DeviceData], None]) -> None:
        self._data_handler = handler

    def set_event_handler(self, handler: Callable[[AdapterEvent], None]) -> None:
        self._event_handler = handler

    def start(self) -> None:
        self._stop.clear()
        s = self.settings
        client = mqtt.Client(
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
            client_id=f"{s.client_id}_{os.getpid()}_{uuid.uuid4().hex[:4]}",
            clean_session=True,
        )
        if s.username:
            client.username_pw_set(s.username, s.password)
        client.reconnect_delay_set(2, 30)
        client.on_connect = self._on_connect
        client.on_disconnect = self._on_disconnect
        client.on_message = self._on_message
        self._client = client
        logger.info("连接 GPIO 控制器 Broker %s:%d，状态主题 %s，控制主题 %s",
                    s.broker, s.port, s.state_topic, s.set_topic)
        client.connect_async(s.broker, s.port, keepalive=s.keep_alive_sec)
        client.loop_start()
        self._watchdog = threading.Thread(target=self._watchdog_loop, name="gpio-valve-watchdog", daemon=True)
        self._watchdog.start()

    def stop(self) -> None:
        self._stop.set()
        client = self._client
        if client is not None:
            client.disconnect()
            client.loop_stop()
        with self._lock:
            unfinished = [p.message_id for p in self._pending.values()]
            self._pending.clear()
        if unfinished:
            logger.warning("停机时仍有未确认指令，无法再上报终态: %s", unfinished)

    # ---- 南向 MQTT 回调（paho 网络线程） ----

    def _on_connect(self, client, _userdata, _flags, reason_code, _properties=None) -> None:
        rc = getattr(reason_code, "value", reason_code)
        if rc != 0 or getattr(reason_code, "is_failure", False):
            logger.error("GPIO Broker 连接失败: %s", reason_code)
            self._link_up = False
            return
        with self._lock:
            self._link_up = True
            self._baseline_pending = True
        client.subscribe(self.settings.state_topic, qos=self.settings.qos)
        logger.info("GPIO Broker 已连接，已订阅 %s", self.settings.state_topic)

    def _on_disconnect(self, _client, _userdata, _flags, reason_code, _properties=None) -> None:
        self._link_up = False
        if not self._stop.is_set():
            logger.warning("GPIO Broker 断开: %s", reason_code)

    def _on_message(self, _client, _userdata, message) -> None:
        if message.topic != self.settings.state_topic:
            return
        if message.retain:
            logger.debug("忽略保留的 GPIO 状态报文（可能已过期）")
            return
        gpios = parse_state_payload(message.payload, self.settings.channel_count)
        if gpios is None:
            self._log_invalid(message.payload)
            return
        try:
            self._apply_report(gpios)
        except Exception:
            logger.exception("处理 GPIO 状态报文失败")

    def _log_invalid(self, payload: bytes) -> None:
        now = time.monotonic()
        if now - self._last_invalid_log >= 10:
            self._last_invalid_log = now
            logger.warning("丢弃不合法的 GPIO 状态报文: %r", payload[:200])

    def _apply_report(self, gpios: list[int]) -> None:
        now = time.monotonic()
        ts = now_ms()
        data: list[DeviceData] = []
        events: list[AdapterEvent] = []
        s = self.settings
        with self._lock:
            self._last_report_mono = now
            baseline = self._baseline_pending
            self._baseline_pending = False
            resync = s.op_resync_sec > 0 and now - self._last_resync_mono >= s.op_resync_sec
            if baseline or resync:
                self._last_resync_mono = now
            for point, gpio in self._gpio_by_point.items():
                value = gpios[gpio]
                old = self._known.get(point)
                self._known[point] = value
                changed = old is None or value != old
                last_tel = self._last_telemetry_mono.get(point)
                if changed or last_tel is None or now - last_tel >= s.telemetry_interval_sec:
                    self._last_telemetry_mono[point] = now
                    data.append(DeviceData(point, {RAW_FIELD: value == 1}, ts))
                pending = self._pending.get(point)
                if pending is not None and value == pending.target:
                    del self._pending[point]
                    events.append(AdapterEvent(point, op_event_name(value), pending.message_id,
                                               {"gpio": gpio, "source": "COMMAND"}, ts))
                    events.append(AdapterEvent(point, "COMMAND_COMPLETED", pending.message_id,
                                               {"gpio": gpio, "isOpen": value == 1}, ts))
                elif changed or baseline or resync:
                    source = "BASELINE" if baseline else ("CHANGE" if changed else "RESYNC")
                    events.append(AdapterEvent(point, op_event_name(value), None,
                                               {"gpio": gpio, "source": source}, ts))
        for item in data:
            self._emit_data(item)
        for event in events:
            self._emit(event)

    # ---- 系统指令（core 的 MQTT 线程调用，禁止阻塞） ----

    def execute(self, command: SystemCommand) -> None:
        point = command.device_point
        mid = command.message_id
        name = command.command_name
        if not self._remember(mid):
            logger.warning("忽略重复投递的指令 messageId=%s point=%s", mid, point)
            return
        if point not in self._gpio_by_point:
            logger.error("control 收到非物理点指令 %s，拒绝执行", point)
            self._fail_now(point, mid, name, "NOT_PHYSICAL_POINT", f"非物理设备点: {point}")
            return
        gpio = self._gpio_by_point[point]
        self._event(point, "COMMAND_RUNNING", mid, {"commandName": name, "gpio": gpio})
        target = COMMAND_TARGETS.get(name)
        if target is None:
            self._event(point, "COMMAND_FAILED", mid, {"reason": "UNSUPPORTED_COMMAND", "error": f"不支持的指令: {name}"})
            return
        injected = command.parameters.get("gpio")
        if injected is not None and str(injected).strip() != str(gpio):
            self._event(point, "COMMAND_FAILED", mid,
                        {"reason": "GPIO_MISMATCH", "error": f"gpio 参数 {injected} 与点表 {gpio} 不一致"})
            return
        now = time.monotonic()
        s = self.settings
        with self._lock:
            online = self._online_locked(now)
            superseded = None
            if online:
                superseded = self._pending.pop(point, None)
                self._pending[point] = PendingCommand(
                    mid, name, gpio, target, now + s.confirm_timeout_sec,
                    now + s.resend_interval_sec if s.resend_interval_sec > 0 else None,
                )
        if not online:
            self._event(point, "COMMAND_FAILED", mid,
                        {"reason": "CONTROLLER_OFFLINE", "error": "GPIO 控制器离线或状态报文超时"})
            return
        if superseded is not None:
            self._event(point, "COMMAND_FAILED", superseded.message_id,
                        {"reason": "SUPERSEDED", "error": f"被新指令 {mid} 取代"})
        if not self._publish_set(gpio, target):
            with self._lock:
                current = self._pending.get(point)
                owned = current is not None and current.message_id == mid
                if owned:
                    del self._pending[point]
            if owned:
                self._event(point, "COMMAND_FAILED", mid, {"reason": "PUBLISH_FAILED", "error": "控制主题发布失败"})

    def _remember(self, message_id: str) -> bool:
        with self._lock:
            if message_id in self._seen_set:
                return False
            self._seen_set.add(message_id)
            self._seen_ids.append(message_id)
            while len(self._seen_ids) > SEEN_MESSAGE_LIMIT:
                self._seen_set.discard(self._seen_ids.popleft())
            return True

    def heartbeat_points(self) -> list[dict[str, Any]]:
        """16 路共用控制器链路：最近一次合法状态报文未超时则全部在线。"""
        with self._lock:
            online = self._online_locked(time.monotonic())
        return [{"devicePoint": point, "online": online} for point in self.physical_points]

    def _online_locked(self, now: float) -> bool:
        return (
            self._link_up
            and self._last_report_mono is not None
            and now - self._last_report_mono <= self.settings.state_timeout_sec
        )

    def _publish_set(self, gpio: int, value: int) -> bool:
        client = self._client
        if client is None or not self._link_up:
            return False
        body = json.dumps({"gpio": gpio, "val": value}, separators=(",", ":"))
        info = client.publish(self.settings.set_topic, body, qos=self.settings.qos, retain=False)
        ok = info.rc == mqtt.MQTT_ERR_SUCCESS
        logger.info("下发 %s %s -> %s", self.settings.set_topic, body, "OK" if ok else f"rc={info.rc}")
        return ok

    # ---- 超时 / 重发 / 在线状态 ----

    def _watchdog_loop(self) -> None:
        while not self._stop.wait(WATCHDOG_TICK_SEC):
            try:
                self._watchdog_tick()
            except Exception:
                logger.exception("GPIO 看门狗异常")

    def _watchdog_tick(self) -> None:
        now = time.monotonic()
        expired: list[tuple[str, PendingCommand]] = []
        resend: list[PendingCommand] = []
        with self._lock:
            for point, pending in list(self._pending.items()):
                if now >= pending.deadline:
                    del self._pending[point]
                    expired.append((point, pending))
                elif pending.next_resend is not None and now >= pending.next_resend:
                    pending.next_resend = now + self.settings.resend_interval_sec
                    resend.append(pending)
            online = self._online_locked(now)
        if self._stop.is_set():
            return
        if online != self._online_logged:
            self._online_logged = online
            (logger.info if online else logger.warning)("GPIO 控制器%s", "在线" if online else "离线")
        for pending in resend:
            if self._stop.is_set():
                return
            self._publish_set(pending.gpio, pending.target)
        for point, pending in expired:
            self._event(point, "COMMAND_FAILED", pending.message_id,
                        {"reason": "CONFIRM_TIMEOUT",
                         "error": f"{self.settings.confirm_timeout_sec}s 内未收到 gpio {pending.gpio} 的目标状态"})

    # ---- 出站 ----

    def _fail_now(self, point: str, mid: str, name: str, reason: str, error: str) -> None:
        self._event(point, "COMMAND_RUNNING", mid, {"commandName": name})
        self._event(point, "COMMAND_FAILED", mid, {"reason": reason, "error": error})

    def _event(self, point: str, name: str, mid: str | None, payload: dict[str, Any]) -> None:
        self._emit(AdapterEvent(point, name, mid, payload, now_ms()))

    def _emit(self, event: AdapterEvent) -> None:
        if self._event_handler is not None:
            self._event_handler(event)

    def _emit_data(self, data: DeviceData) -> None:
        if self._data_handler is not None:
            self._data_handler(data)
