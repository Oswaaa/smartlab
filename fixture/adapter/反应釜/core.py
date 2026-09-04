"""SmartLab Adapter 核心：MQTT、点表、租约、真/假分流。换设备时不要改本文件。"""

from __future__ import annotations

import configparser
import json
import logging
import os
import re
import sys
import threading
import time
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable, Protocol

try:
    import paho.mqtt.client as mqtt
except ImportError:  # pragma: no cover
    mqtt = None

REGISTER_TOPIC = "smartlab/adapter/register"


def now_ms() -> int:
    return int(time.time() * 1000)


def topic_segment(value: str, field_name: str) -> str:
    text = str(value or "").strip()
    if not text:
        raise ValueError(f"{field_name} 不能为空")
    if any(ch in text for ch in ("/", "+", "#")):
        raise ValueError(f"{field_name} 不能包含 MQTT topic 分隔符或通配符")
    return text


def virtual_point_name(physical_point: str, lease_id: int) -> str:
    return topic_segment(f"{physical_point}_sim_{lease_id}", "virtualDevicePoint")


def parse_virtual_point(physical_points: tuple[str, ...] | list[str], virtual_point: str) -> tuple[str, int] | None:
    """从 Reactor1_sim_7 解析物理点与 leaseId；命名不符合时返回 None。"""
    marker = "_sim_"
    idx = virtual_point.rfind(marker)
    if idx <= 0:
        return None
    physical = virtual_point[:idx]
    if physical not in physical_points:
        return None
    suffix = virtual_point[idx + len(marker):]
    if not suffix.isdigit():
        return None
    return physical, int(suffix)


_BRACKET = re.compile(r"\[((?:\\.|[^\]])*)\]")


def parse_ini_brackets(raw: str) -> list[str]:
    """解析 adapterSetup.ini 中 [DOUBLE][描述][internal=true][sourceField=index] 语法。"""
    text = str(raw or "").strip()
    parts = _BRACKET.findall(text)
    if not parts:
        return [text] if text else []
    return [part.replace(r"\]", "]").replace(r"\\", "\\") for part in parts]


def parse_parameter_value(raw: str) -> dict[str, str]:
    tokens = parse_ini_brackets(raw)
    parsed: dict[str, str] = {}
    if tokens:
        parsed["dataType"] = tokens[0]
    if len(tokens) > 1:
        parsed["description"] = tokens[1]
    for token in tokens[2:]:
        if "=" in token:
            key, value = token.split("=", 1)
            parsed[key.strip()] = value.strip()
    return parsed


@dataclass(frozen=True)
class SystemCommand:
    message_id: str
    device_point: str
    command_name: str
    parameters: dict[str, Any]
    timestamp: int


@dataclass(frozen=True)
class DeviceData:
    device_point: str
    fields: dict[str, Any]
    timestamp: int


@dataclass(frozen=True)
class DeviceBatchData:
    device_point: str
    items: list[dict[str, Any]]
    timestamp: int


@dataclass(frozen=True)
class AdapterEvent:
    device_point: str
    event_name: str
    message_id: str | None
    payload: dict[str, Any]
    timestamp: int


class Control(Protocol):
    """原控制程序。只处理物理点，不知道 MQTT 和租约。"""

    def start(self) -> None: ...
    def stop(self) -> None: ...

    def execute(self, command: SystemCommand) -> None: ...

    def set_data_handler(self, handler: Callable[[DeviceData], None]) -> None: ...
    def set_event_handler(self, handler: Callable[[AdapterEvent], None]) -> None: ...


class Simulation(Protocol):
    """假设备。只处理已挂载的虚拟点。"""

    def start(self) -> None: ...
    def stop(self) -> None: ...

    def attach(self, virtual_point: str, physical_point: str) -> None: ...
    def detach(self, virtual_point: str) -> None: ...

    def execute(self, command: SystemCommand) -> None: ...

    def set_data_handler(self, handler: Callable[[DeviceData], None]) -> None: ...
    def set_event_handler(self, handler: Callable[[AdapterEvent], None]) -> None: ...


@dataclass
class LeaseRecord:
    lease_id: int
    physical_point: str
    virtual_point: str
    released: bool = False
    status: str = "ACTIVE"


@dataclass
class AdapterSetup:
    path: Path
    raw_content: str
    adapter_name: str
    spec_version: str
    raw_config_format: str
    physical_points: tuple[str, ...]
    point_template: dict[str, str]
    attribute_mapping: dict[str, dict[str, str]]
    point_fields: dict[str, dict[str, str]]
    internal_params: dict[tuple[str, str], list[tuple[str, str]]]

    @classmethod
    def load(cls, path: Path) -> "AdapterSetup":
        """只解析已定义的 adapterSetup.ini 契约，不要改 section 层级或方括号语法。"""
        raw = path.read_text(encoding="utf-8-sig")
        parser = configparser.ConfigParser(interpolation=None)
        parser.optionxform = str
        parser.read_string(raw)
        if not parser.has_section("adapter"):
            raise ValueError("adapterSetup.ini 缺少 [adapter]")
        spec_version = parser.get("adapter", "specVersion", fallback="").strip()
        if spec_version != "smartlab.adapter.config.v1":
            raise ValueError("adapter.specVersion 必须是 smartlab.adapter.config.v1")
        name = topic_segment(parser.get("adapter", "adapterName", fallback=""), "adapter.adapterName")
        raw_format = parser.get("adapter", "rawConfigFormat", fallback="").strip().upper()
        if raw_format != "INI":
            raise ValueError("焊死加载器只收 INI；JSON 须先写成已定义的 adapterSetup.ini")
        points: list[str] = []
        templates: dict[str, str] = {}
        mappings: dict[str, dict[str, str]] = {}
        fields: dict[str, dict[str, str]] = {}
        internal_params: dict[tuple[str, str], list[tuple[str, str]]] = {}
        for section in parser.sections():
            parts = section.split(".")
            if len(parts) == 2 and parts[0] == "devicePoints":
                point = topic_segment(parts[1], section)
                points.append(point)
                templates[point] = parser.get(section, "templateName", fallback="").strip()
                extra = {
                    key: parser.get(section, key)
                    for key in parser.options(section)
                    if key not in {"templateName", "description", "devicePoint"}
                }
                fields[point] = extra
            elif len(parts) == 3 and parts[0] == "devicePoints" and parts[2] == "attributeMapping":
                point = topic_segment(parts[1], section)
                mappings[point] = {key: parser.get(section, key) for key in parser.options(section)}
            elif (
                len(parts) == 5
                and parts[0] == "deviceTemplates"
                and parts[2] == "commands"
                and parts[4] == "parameters"
            ):
                template_name, command_name = parts[1], parts[3]
                injections: list[tuple[str, str]] = []
                for key in parser.options(section):
                    parsed = parse_parameter_value(parser.get(section, key))
                    if parsed.get("internal", "").lower() == "true":
                        source_field = parsed.get("sourceField", "").strip()
                        if not source_field:
                            raise ValueError(f"{section}.{key} 的 internal=true 必须提供 sourceField")
                        injections.append((key, source_field))
                if injections:
                    internal_params[(template_name, command_name)] = injections
        if not points:
            raise ValueError("adapterSetup.ini 必须至少声明一个 [devicePoints.<name>]")
        return cls(
            path, raw, name, spec_version, raw_format, tuple(points),
            templates, mappings, fields, internal_params,
        )


@dataclass
class RuntimeConfig:
    broker: str
    port: int
    client_id: str
    username: str
    password: str
    keep_alive_sec: int
    qos: int
    heartbeat_interval_sec: float
    log_level: str
    extra: dict[str, dict[str, str]] = field(default_factory=dict)

    @classmethod
    def load(cls, path: Path) -> "RuntimeConfig":
        parser = configparser.ConfigParser(interpolation=None)
        parser.optionxform = str
        parser.read(path, encoding="utf-8-sig")
        if not parser.has_section("smartlab"):
            raise ValueError("runtime.ini 缺少 [smartlab]")
        smartlab = dict(parser.items("smartlab"))
        runtime = dict(parser.items("runtime")) if parser.has_section("runtime") else {}
        logging_s = dict(parser.items("logging")) if parser.has_section("logging") else {}
        extra = {
            section: dict(parser.items(section))
            for section in parser.sections()
            if section not in {"smartlab", "runtime", "logging"}
        }
        return cls(
            broker=smartlab["broker"].strip(),
            port=int(smartlab["port"]),
            client_id=smartlab["clientId"].strip(),
            username=str(smartlab.get("username", "")).strip(),
            password=str(smartlab.get("password", "")),
            keep_alive_sec=int(smartlab.get("keepAliveSec", "30")),
            qos=int(smartlab.get("qos", "1")),
            heartbeat_interval_sec=float(runtime.get("heartbeatIntervalSec", "10")),
            log_level=str(logging_s.get("level", "INFO")).strip().upper(),
            extra=extra,
        )


class AdapterCore:
    def __init__(
        self,
        setup: AdapterSetup,
        runtime: RuntimeConfig,
        control: Control,
        simulation: Simulation | None = None,
        leases_file: Path | None = None,
    ) -> None:
        self.setup = setup
        self.runtime = runtime
        self.control = control
        self.simulation = simulation
        self._log = logging.getLogger("smartlab.adapter.core")
        self._lock = threading.RLock()
        self._client = None
        self._connected = False
        self._stopping = False
        self._stop = threading.Event()
        self._has_registered = False
        self._unique_client_id = f"{runtime.client_id}_{os.getpid()}_{uuid.uuid4().hex[:4]}"
        self.leases_file = leases_file or (self.setup.path.parent / "virtual_leases.json")
        self._leases: dict[int, LeaseRecord] = {}
        self._virtual_to_lease: dict[str, int] = {}
        self._command_topics: dict[str, str] = {
            self._command_topic(point): point for point in setup.physical_points
        }
        self.control.set_data_handler(self._on_backend_data)
        self.control.set_event_handler(self._on_backend_event)
        if self.simulation is not None:
            self.simulation.set_data_handler(self._on_backend_data)
            if hasattr(self.simulation, "set_batch_data_handler"):
                self.simulation.set_batch_data_handler(self._on_backend_batch_data)
            self.simulation.set_event_handler(self._on_backend_event)
        self._load_stored_leases()

    def _load_stored_leases(self) -> None:
        if not self.leases_file.exists():
            return
        try:
            raw = self.leases_file.read_text(encoding="utf-8")
            if not raw.strip():
                return
            data = json.loads(raw)
            if not isinstance(data, dict):
                return
            with self._lock:
                for key, item in data.items():
                    if not isinstance(item, dict):
                        continue
                    lease_id = int(item.get("leaseId", key))
                    phys = str(item.get("physicalPoint", "")).strip()
                    virt = str(item.get("virtualPoint", "")).strip()
                    status = str(item.get("status", "ACTIVE")).strip().upper()
                    released = bool(item.get("released", False)) or (status == "RELEASED")
                    state = item.get("state")
                    if released or status != "ACTIVE":
                        self._log.info("跳过已释放的历史虚拟点位: %s (leaseId=%d, status=%s)", virt, lease_id, status)
                        continue
                    if phys in self.setup.physical_points and virt:
                        rec = LeaseRecord(lease_id, phys, virt, released=False, status="ACTIVE")
                        self._leases[lease_id] = rec
                        self._virtual_to_lease[virt] = lease_id
                        topic = self._command_topic(virt)
                        self._command_topics[topic] = virt
                        if self.simulation is not None:
                            self.simulation.attach(virt, phys, state=state)
                        self._log.info("已从本地存储恢复活跃虚拟点位: %s (leaseId=%d, 对应物理点=%s)", virt, lease_id, phys)
        except Exception as e:
            self._log.warning("加载本地虚拟租约文件失败: %s", e)

    def _save_stored_leases(self) -> None:
        try:
            snapshot: dict[str, Any] = {}
            with self._lock:
                for lease_id, rec in self._leases.items():
                    state = None
                    if self.simulation is not None and hasattr(self.simulation, "get_device_state"):
                        state = self.simulation.get_device_state(rec.virtual_point)
                    status = "RELEASED" if (rec.released or rec.status == "RELEASED") else "ACTIVE"
                    snapshot[str(lease_id)] = {
                        "leaseId": lease_id,
                        "physicalPoint": rec.physical_point,
                        "virtualPoint": rec.virtual_point,
                        "status": status,
                        "released": (status == "RELEASED"),
                        "state": state or {"current_temp": 25.0, "current_pressure": 0.10},
                        "updatedTime": now_ms(),
                    }
            tmp_path = self.leases_file.with_suffix(".tmp")
            tmp_path.write_text(json.dumps(snapshot, indent=2, ensure_ascii=False), encoding="utf-8")
            tmp_path.replace(self.leases_file)
        except Exception as e:
            self._log.warning("持久化虚拟租约失败: %s", e)

    def heartbeat_topic(self) -> str:
        return f"smartlab/adapter/{self.setup.adapter_name}/heartbeat"

    def lease_request_topic(self) -> str:
        return f"smartlab/adapter/{self.setup.adapter_name}/leaserequest"

    def lease_result_topic(self) -> str:
        return f"smartlab/adapter/{self.setup.adapter_name}/leaseresult"

    def _command_topic(self, device_point: str) -> str:
        point = topic_segment(device_point, "devicePoint")
        return f"smartlab/adapter/{self.setup.adapter_name}/{point}/command"

    def _telemetry_topic(self, device_point: str) -> str:
        point = topic_segment(device_point, "devicePoint")
        return f"smartlab/adapter/{self.setup.adapter_name}/{point}/telemetry"

    def _event_topic(self, device_point: str) -> str:
        point = topic_segment(device_point, "devicePoint")
        return f"smartlab/adapter/{self.setup.adapter_name}/{point}/event"

    def start(self) -> None:
        if mqtt is None:
            raise RuntimeError("缺少 paho-mqtt")
        self.control.start()
        if self.simulation is not None:
            self.simulation.start()
        
        # Support both paho-mqtt v2.x and v1.x
        if hasattr(mqtt, "CallbackAPIVersion"):
            client = mqtt.Client(
                callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
                client_id=self._unique_client_id,
                clean_session=True,
            )
        else:
            client = mqtt.Client(
                client_id=self._unique_client_id,
                clean_session=True,
            )

        if self.runtime.username:
            client.username_pw_set(self.runtime.username, self.runtime.password)
        if hasattr(client, "reconnect_delay_set"):
            client.reconnect_delay_set(2, 30)
        client.on_connect = self._on_connect
        client.on_disconnect = self._on_disconnect
        client.on_message = self._on_message
        self._client = client
        self._log.info("正在启动 MQTT 连接 (Broker: %s:%d, ClientId: %s)...", self.runtime.broker, self.runtime.port, self._unique_client_id)
        client.connect_async(self.runtime.broker, self.runtime.port, keepalive=self.runtime.keep_alive_sec)
        client.loop_start()
        threading.Thread(target=self._heartbeat_loop, name="adapter-heartbeat", daemon=True).start()

    def stop(self) -> None:
        self._stopping = True
        self._stop.set()
        self._save_stored_leases()
        if self._client is not None:
            self._client.loop_stop()
            self._client.disconnect()
        if self.simulation is not None:
            self.simulation.stop()
        self.control.stop()
        self._log.info("Adapter 已优雅停机并保存状态")

    def run_forever(self) -> None:
        self.start()
        try:
            while not self._stop.wait(0.5):
                pass
        finally:
            self.stop()

    def _on_connect(self, client, _userdata, _flags, reason_code, _properties=None) -> None:
        rc = getattr(reason_code, "value", reason_code) if hasattr(reason_code, "value") else reason_code
        is_fail = getattr(reason_code, "is_failure", False) if hasattr(reason_code, "is_failure") else (rc != 0)
        if rc != 0 or is_fail:
            self._log.error("SmartLab MQTT 连接失败 rc=%s (%s)", rc, reason_code)
            self._connected = False
            return
        self._connected = True
        self._log.info("SmartLab MQTT 连接成功 (Broker: %s:%d, ClientId: %s)", self.runtime.broker, self.runtime.port, self._unique_client_id)
        qos = self.runtime.qos
        with self._lock:
            for record in self._leases.values():
                if record.released or record.status != "ACTIVE":
                    continue
                topic = self._command_topic(record.virtual_point)
                self._command_topics[topic] = record.virtual_point
                self._virtual_to_lease[record.virtual_point] = record.lease_id

        # 订阅租约请求主题
        client.subscribe(self.lease_request_topic(), qos=qos)
        # 使用单层通配符订阅指令主题，彻底保证所有物理点与未来/历史虚拟点位均能接收指令
        wildcard_cmd = f"smartlab/adapter/{self.setup.adapter_name}/+/command"
        client.subscribe(wildcard_cmd, qos=qos)
        for topic in self._command_topics:
            client.subscribe(topic, qos=qos)
        self._log.info("已订阅主题: %s, %s", self.lease_request_topic(), wildcard_cmd)

        # 仅在初次连接时发布注册报文，避免重连时重复全量广播
        if not self._has_registered:
            self._publish_json(REGISTER_TOPIC, {
                "adapterName": self.setup.adapter_name,
                "rawConfigFormat": self.setup.raw_config_format,
                "rawConfigContent": self.setup.raw_content,
                "timestamp": now_ms(),
            })
            self._has_registered = True
            self._log.info("已向系统发布初始注册元数据 (%s)", REGISTER_TOPIC)

        self._publish_json(self.heartbeat_topic(), {"status": "ALIVE", "timestamp": now_ms()})

    def _on_disconnect(self, _client, _userdata, _flags, reason_code, _properties=None) -> None:
        self._connected = False
        if not self._stopping:
            self._log.warning("SmartLab MQTT 断开 rc=%s", reason_code)

    def _on_message(self, _client, _userdata, message) -> None:
        try:
            payload = json.loads(message.payload)
            if not isinstance(payload, dict):
                raise TypeError("payload 必须是对象")
            if message.topic == self.lease_request_topic():
                self._handle_lease(payload)
                return
            point = self._command_topics.get(message.topic)
            if point is None:
                # 动态从 topic 中识别点位：smartlab/adapter/{adapterName}/{devicePoint}/command
                parts = message.topic.split("/")
                if len(parts) >= 5 and parts[0] == "smartlab" and parts[1] == "adapter" and parts[-1] == "command":
                    candidate = parts[3]
                    if candidate in self.setup.physical_points:
                        point = candidate
                        self._command_topics[message.topic] = point
                    elif self._is_virtual(candidate):
                        self._ensure_virtual_ready(candidate)
                        point = candidate
                if point is None:
                    raise ValueError(f"未知命令主题: {message.topic}")
            self._handle_command(point, payload)
        except Exception:
            self._log.exception("处理 MQTT 消息失败 topic=%s", message.topic)

    def _handle_command(self, topic_point: str, payload: dict[str, Any]) -> None:
        for key in ("messageId", "adapterName", "devicePoint", "commandName", "parameters", "timestamp"):
            if key not in payload:
                raise ValueError(f"命令缺少字段: {key}")
        if payload["adapterName"] != self.setup.adapter_name:
            raise ValueError("adapterName 与本 Adapter 不一致")
        if payload["devicePoint"] != topic_point:
            raise ValueError("devicePoint 与 topic 不一致")
        if not isinstance(payload["parameters"], dict):
            raise TypeError("parameters 必须是对象")
        command = SystemCommand(
            message_id=str(payload["messageId"]),
            device_point=topic_point,
            command_name=str(payload["commandName"]),
            parameters=dict(payload["parameters"]),
            timestamp=int(payload["timestamp"]),
        )
        parameters = dict(command.parameters)
        source_point = self._physical_source(topic_point)
        template_name = self.setup.point_template.get(source_point, "")
        for param_name, source_field in self.setup.internal_params.get((template_name, command.command_name), []):
            if source_field not in self.setup.point_fields.get(source_point, {}):
                raise ValueError(f"设备点 {source_point} 缺少 internal 来源字段: {source_field}")
            parameters[param_name] = self.setup.point_fields[source_point][source_field]
        command = SystemCommand(
            message_id=command.message_id,
            device_point=command.device_point,
            command_name=command.command_name,
            parameters=parameters,
            timestamp=command.timestamp,
        )
        if self._is_virtual(topic_point):
            if self.simulation is None:
                raise RuntimeError("收到虚拟点命令但未装配 simulation")
            self._ensure_virtual_ready(topic_point)
            self.simulation.execute(command)
        else:
            self.control.execute(command)

    def _handle_lease(self, payload: dict[str, Any]) -> None:
        for key in ("leaseId", "adapterName", "action", "devicePoint", "timestamp"):
            if key not in payload:
                raise ValueError(f"租约请求缺少字段: {key}")
        if payload["adapterName"] != self.setup.adapter_name:
            raise ValueError("lease adapterName 与本 Adapter 不一致")
        lease_id = int(payload["leaseId"])
        action = str(payload["action"]).upper()
        device_point = str(payload["devicePoint"]).strip()
        try:
            if action == "LEASE":
                record = self._lease(lease_id, device_point)
                self._publish_lease_result(lease_id, "LEASE", "GRANTED", record.virtual_point)
            elif action == "RELEASE":
                virtual = self._release(lease_id, device_point)
                self._publish_lease_result(lease_id, "RELEASE", "RELEASED", virtual)
            else:
                raise ValueError(f"不支持的租约 action: {action}")
        except Exception as error:
            self._log.exception("租约失败 leaseId=%s action=%s devicePoint=%s", lease_id, action, device_point)
            extra = {"errorMessage": str(error)}
            if action == "LEASE":
                self._publish_lease_result(lease_id, "LEASE", "FAILED", None, extra)
            else:
                self._publish_lease_result(lease_id, "RELEASE", "FAILED", device_point or None, extra)

    def _lease(self, lease_id: int, physical_point: str) -> LeaseRecord:
        if physical_point not in self.setup.physical_points:
            raise ValueError(f"未知物理点: {physical_point}")
        if self.simulation is None:
            raise RuntimeError("未装配 simulation，不能 LEASE")
        with self._lock:
            existing = self._leases.get(lease_id)
            if existing is not None and not existing.released:
                if existing.physical_point != physical_point:
                    raise ValueError("同一 leaseId 不能换物理点")
                return existing
            virtual = virtual_point_name(physical_point, lease_id)
            self.simulation.attach(virtual, physical_point)
            topic = self._command_topic(virtual)
            self._command_topics[topic] = virtual
            if self._client is not None and self._connected:
                self._client.subscribe(topic, qos=self.runtime.qos)
            record = LeaseRecord(lease_id, physical_point, virtual)
            self._leases[lease_id] = record
            self._virtual_to_lease[virtual] = lease_id
            self._save_stored_leases()
            return record

    def _release(self, lease_id: int, virtual_point: str) -> str:
        with self._lock:
            record = self._leases.get(lease_id)
            if record is None:
                if not virtual_point:
                    raise ValueError("RELEASE 找不到租约")
                return virtual_point
            if record.released:
                return record.virtual_point
            if virtual_point and virtual_point != record.virtual_point:
                raise ValueError("RELEASE 的 devicePoint 与租约虚拟点不一致")
            if self.simulation is not None:
                self.simulation.detach(record.virtual_point)
            topic = self._command_topic(record.virtual_point)
            self._command_topics.pop(topic, None)
            self._virtual_to_lease.pop(record.virtual_point, None)
            if self._client is not None and self._connected:
                self._client.unsubscribe(topic)
            record.released = True
            record.status = "RELEASED"
            self._save_stored_leases()
            return record.virtual_point

    def _is_virtual(self, device_point: str) -> bool:
        return device_point not in self.setup.physical_points

    def _ensure_virtual_ready(self, virtual_point: str) -> None:
        if self.simulation is None:
            raise RuntimeError("收到虚拟点命令但未装配 simulation")
        parsed = parse_virtual_point(self.setup.physical_points, virtual_point)
        if parsed is None:
            raise ValueError(f"无法解析虚拟点位: {virtual_point}")
        physical_point, lease_id = parsed
        with self._lock:
            record = self._leases.get(lease_id)
            if record is None or record.released or record.virtual_point != virtual_point:
                record = LeaseRecord(lease_id, physical_point, virtual_point)
                self._leases[lease_id] = record
            self._virtual_to_lease[virtual_point] = lease_id
            topic = self._command_topic(virtual_point)
            self._command_topics[topic] = virtual_point
            if self._client is not None and self._connected:
                self._client.subscribe(topic, qos=self.runtime.qos)
        self.simulation.attach(virtual_point, physical_point)
        self._save_stored_leases()

    def _physical_source(self, device_point: str) -> str:
        if device_point in self.setup.physical_points:
            return device_point
        parsed = parse_virtual_point(self.setup.physical_points, device_point)
        if parsed is not None:
            return parsed[0]
        lease_id = self._virtual_to_lease.get(device_point)
        if lease_id is None:
            raise ValueError(f"未知点位: {device_point}")
        return self._leases[lease_id].physical_point

    def _on_backend_data(self, data: DeviceData) -> None:
        source = self._physical_source(data.device_point)
        mapping = self.setup.attribute_mapping.get(source, {})
        telemetry = {}
        if mapping:
            for attribute, field_name in mapping.items():
                if field_name in data.fields:
                    telemetry[attribute] = data.fields[field_name]
        else:
            telemetry = dict(data.fields)
        self._publish_json(self._telemetry_topic(data.device_point), {
            "formatType": "SINGLE",
            "timestamp": data.timestamp or now_ms(),
            "adapterName": self.setup.adapter_name,
            "devicePoint": data.device_point,
            "telemetryData": telemetry,
        })

    def _on_backend_batch_data(self, batch: DeviceBatchData) -> None:
        source = self._physical_source(batch.device_point)
        mapping = self.setup.attribute_mapping.get(source, {})
        mapped_items = []
        for item in batch.items:
            fields = item.get("fields", {})
            telemetry = {}
            if mapping:
                for attribute, field_name in mapping.items():
                    if field_name in fields:
                        telemetry[attribute] = fields[field_name]
            else:
                telemetry = dict(fields)
            mapped_items.append({
                "timestamp": item.get("timestamp", now_ms()),
                "telemetryData": telemetry,
            })
        self._publish_json(self._telemetry_topic(batch.device_point), {
            "formatType": "BATCH",
            "timestamp": batch.timestamp or now_ms(),
            "adapterName": self.setup.adapter_name,
            "devicePoint": batch.device_point,
            "items": mapped_items,
        })

    def _on_backend_event(self, event: AdapterEvent) -> None:
        body: dict[str, Any] = {
            "timestamp": event.timestamp or now_ms(),
            "adapterName": self.setup.adapter_name,
            "devicePoint": event.device_point,
            "eventName": event.event_name,
        }
        if event.message_id:
            body["messageId"] = event.message_id
        if event.payload:
            body["payload"] = event.payload
        self._publish_json(self._event_topic(event.device_point), body)

    def _publish_lease_result(
        self,
        lease_id: int,
        action: str,
        status: str,
        device_point: str | None,
        extra: dict[str, Any] | None = None,
    ) -> None:
        body: dict[str, Any] = {
            "leaseId": lease_id,
            "adapterName": self.setup.adapter_name,
            "action": action,
            "status": status,
            "timestamp": now_ms(),
        }
        if device_point:
            body["devicePoint"] = device_point
        if extra:
            body.update(extra)
        self._publish_json(self.lease_result_topic(), body)

    def _heartbeat_loop(self) -> None:
        interval = max(self.runtime.heartbeat_interval_sec, 1.0)
        while not self._stop.wait(interval):
            if self._connected:
                self._publish_json(self.heartbeat_topic(), {"status": "ALIVE", "timestamp": now_ms()})
            if self._leases:
                self._save_stored_leases()

    def _publish_json(self, topic: str, payload: dict[str, Any]) -> None:
        client = self._client
        if client is None or not self._connected:
            return
        try:
            client.publish(topic, json.dumps(payload, ensure_ascii=False), qos=self.runtime.qos, retain=False)
        except Exception as e:
            self._log.debug("发布 MQTT 消息异常: %s", e)


def main() -> int:
    from control import ControlImpl
    from simulation import SimulationImpl

    base = Path(__file__).resolve().parent
    setup = AdapterSetup.load(base / "adapterSetup.ini")
    runtime = RuntimeConfig.load(base / "runtime.ini")
    if sys.platform == "win32":
        try:
            sys.stdout.reconfigure(encoding="utf-8")
            sys.stderr.reconfigure(encoding="utf-8")
        except Exception:
            pass
    logging.basicConfig(
        level=getattr(logging, runtime.log_level, logging.INFO),
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
        handlers=[logging.StreamHandler(sys.stdout)]
    )
    logger = logging.getLogger("smartlab.adapter")
    logger.info("==================================================")
    logger.info("SmartLab Adapter V2 正在启动 (Adapter: %s)", setup.adapter_name)
    logger.info("物理点位: %s", setup.physical_points)
    logger.info("MQTT Broker: %s:%d", runtime.broker, runtime.port)
    logger.info("==================================================")
    control = ControlImpl(runtime.extra, setup.physical_points, setup.point_fields)
    simulation = SimulationImpl(runtime.extra.get("simulation", {}))
    AdapterCore(setup, runtime, control, simulation).run_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
