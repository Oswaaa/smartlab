from __future__ import annotations

import configparser
import json
import logging
import re
import signal
import threading
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

# ==============================================================================
# Utility & Message Models
# ==============================================================================

@dataclass(frozen=True)
class Publication:
    topic: str
    payload: Any
    qos: int
    retain: bool = False


def now_ms() -> int:
    return int(time.time() * 1000)


def require_object(value: Any, field: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ValueError(f"{field} must be a JSON object")
    return value


def require_string(value: Any, field: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{field} must be a non-empty string")
    return value.strip()


def require_timestamp(value: Any) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise ValueError("timestamp must be an integer")
    return value


def extract_device_point(topic_template: str, actual_topic: str, adapter_name: str) -> str:
    expected = topic_template.replace("{adapterName}", adapter_name)
    expected_parts = expected.split("/")
    actual_parts = actual_topic.split("/")
    if len(expected_parts) != len(actual_parts):
        raise ValueError(f"topic does not match configured template: {actual_topic}")
    device_point = ""
    for configured, actual in zip(expected_parts, actual_parts):
        if configured in {"+", "{devicePoint}"}:
            if device_point and device_point != actual:
                raise ValueError("topic template contains conflicting devicePoint positions")
            device_point = actual
        elif configured == "#":
            raise ValueError("multi-level wildcard is not supported for devicePoint extraction")
        elif configured != actual:
            raise ValueError(f"topic does not match configured template: {actual_topic}")
    if not device_point:
        raise ValueError("topic template must contain {devicePoint} or +")
    return device_point


def mqtt_topic_matches(subscription: str, topic: str) -> bool:
    expected = subscription.split("/")
    actual = topic.split("/")
    for index, segment in enumerate(expected):
        if segment == "#":
            return index == len(expected) - 1
        if index >= len(actual):
            return False
        if segment != "+" and segment != actual[index]:
            return False
    return len(expected) == len(actual)


def matches_data_type(value: Any, data_type: str) -> bool:
    if data_type == "DOUBLE":
        return not isinstance(value, bool) and isinstance(value, (int, float))
    if data_type == "INTEGER":
        return not isinstance(value, bool) and isinstance(value, int)
    if data_type == "BOOLEAN":
        return isinstance(value, bool)
    if data_type == "STRING":
        return isinstance(value, str)
    if data_type == "JSON":
        return isinstance(value, (dict, list))
    return False


# ==============================================================================
# PLC Protocol Parser
# ==============================================================================

REGISTER_NAME = re.compile(r"MW\d+")


class PlcProtocolError(ValueError):
    """Raised when a PLC MQTT payload violates the configured register protocol."""


@dataclass(frozen=True)
class PlcSnapshot:
    device_sn: str
    values: dict[str, int | float]
    received_at_ms: int

    def word(self, register: str) -> int | float:
        try:
            return self.values[register]
        except KeyError as exc:
            raise PlcProtocolError(f"PLC snapshot is missing register {register}") from exc

    def bit(self, register: str, index: int = 0) -> bool:
        value = self.word(register)
        if not isinstance(value, int):
            raise PlcProtocolError(f"register {register} must be an integer to read a bit")
        if index < 0:
            raise PlcProtocolError("bit index cannot be negative")
        return bool(value & (1 << index))


def decode_snapshot(payload: Any, device_sn: str, received_at_ms: int) -> PlcSnapshot:
    if not isinstance(payload, list) or not payload:
        raise PlcProtocolError("PLC telemetry payload must be a non-empty array")

    matching: Mapping[str, Any] | None = None
    for entry in payload:
        if not isinstance(entry, dict):
            raise PlcProtocolError("each PLC telemetry array item must be an object")
        if entry.get("DeviceSN") == device_sn:
            matching = entry
            break
    if matching is None:
        raise PlcProtocolError(f"PLC telemetry does not contain DeviceSN {device_sn}")

    tag_data = matching.get("TagData")
    if not isinstance(tag_data, list) or not tag_data:
        raise PlcProtocolError(f"PLC {device_sn} TagData must be a non-empty array")
    row = tag_data[-1]
    if not isinstance(row, dict):
        raise PlcProtocolError(f"PLC {device_sn} TagData row must be an object")

    values: dict[str, int | float] = {}
    for name, value in row.items():
        if not REGISTER_NAME.fullmatch(name):
            continue
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            raise PlcProtocolError(f"PLC register {name} must be numeric")
        values[name] = value
    if not values:
        raise PlcProtocolError(f"PLC {device_sn} TagData contains no MW registers")
    return PlcSnapshot(device_sn=device_sn, values=values, received_at_ms=received_at_ms)


def encode_register_write(device_sn: str, values: Mapping[str, int]) -> list[dict[str, Any]]:
    if not device_sn:
        raise PlcProtocolError("DeviceSN cannot be empty")
    if not values:
        raise PlcProtocolError("register write cannot be empty")
    registers: dict[str, int] = {}
    for name, value in values.items():
        if not REGISTER_NAME.fullmatch(name):
            raise PlcProtocolError(f"invalid PLC register name: {name}")
        if isinstance(value, bool) or not isinstance(value, int):
            raise PlcProtocolError(f"PLC register write {name} must be an integer")
        registers[name] = value
    return [{"DeviceSN": device_sn, "TagData": [registers]}]


# ==============================================================================
# Configuration Loaders
# ==============================================================================

SUPPORTED_DATA_TYPES = {"DOUBLE", "INTEGER", "BOOLEAN", "STRING", "JSON"}


class ConfigError(ValueError):
    """Raised when an Adapter or runtime INI file violates its contract."""


@dataclass(frozen=True)
class AttributeDefinition:
    name: str
    data_type: str
    description: str


@dataclass(frozen=True)
class ParameterDefinition:
    name: str
    data_type: str
    description: str
    internal: bool = False
    source_field: str = ""


@dataclass(frozen=True)
class CommandDefinition:
    name: str
    description: str
    parameters: tuple[ParameterDefinition, ...]

    @property
    def public_parameters(self) -> tuple[ParameterDefinition, ...]:
        return tuple(item for item in self.parameters if not item.internal)

    @property
    def internal_parameters(self) -> tuple[ParameterDefinition, ...]:
        return tuple(item for item in self.parameters if item.internal)


@dataclass(frozen=True)
class TemplateDefinition:
    name: str
    category_name: str
    category_description: str
    description: str
    attributes: dict[str, AttributeDefinition]
    commands: dict[str, CommandDefinition]
    cmd_events: dict[str, str]
    op_events: dict[str, str]


@dataclass(frozen=True)
class DevicePointDefinition:
    name: str
    template_name: str
    description: str
    fields: dict[str, Any]
    attribute_mapping: dict[str, str]


@dataclass(frozen=True)
class BoundCommand:
    template: TemplateDefinition
    definition: CommandDefinition

    @property
    def name(self) -> str:
        return self.definition.name

    @property
    def public_parameters(self) -> tuple[ParameterDefinition, ...]:
        return self.definition.public_parameters

    @property
    def internal_parameters(self) -> tuple[ParameterDefinition, ...]:
        return self.definition.internal_parameters


@dataclass(frozen=True)
class AdapterConfig:
    spec_version: str
    adapter_name: str
    adapter_description: str
    raw_config_format: str
    templates: dict[str, TemplateDefinition]
    device_points: dict[str, DevicePointDefinition]
    raw_content: str

    def device_point(self, name: str) -> DevicePointDefinition:
        try:
            return self.device_points[name]
        except KeyError as exc:
            raise ConfigError(f"unknown devicePoint: {name}") from exc

    def command(self, device_point: str, command_name: str) -> BoundCommand:
        point = self.device_point(device_point)
        template = self.templates[point.template_name]
        try:
            definition = template.commands[command_name]
        except KeyError as exc:
            raise ConfigError(f"unknown command for {device_point}: {command_name}") from exc
        return BoundCommand(template=template, definition=definition)


@dataclass(frozen=True)
class BrokerConfig:
    host: str
    port: int
    username: str
    password: str
    client_id: str
    keep_alive_sec: int
    qos: int


@dataclass(frozen=True)
class RuntimeConfig:
    broker: BrokerConfig
    heartbeat_interval_sec: int
    command_timeout_sec: int
    log_level: str
    topics: dict[str, str]

    def topic(self, name: str, **values: str) -> str:
        try:
            template = self.topics[name]
        except KeyError as exc:
            raise ConfigError(f"unknown topic template: {name}") from exc
        try:
            return template.format_map(values)
        except KeyError as exc:
            raise ConfigError(f"topic {name} requires value: {exc.args[0]}") from exc


def _new_parser() -> configparser.RawConfigParser:
    parser = configparser.RawConfigParser(interpolation=None, strict=True)
    parser.optionxform = str
    return parser


def _read_ini(path: str | Path) -> tuple[configparser.RawConfigParser, str]:
    source = Path(path)
    try:
        raw_content = source.read_text(encoding="utf-8-sig")
    except OSError as exc:
        raise ConfigError(f"cannot read config file {source}: {exc}") from exc
    parser = _new_parser()
    try:
        parser.read_string(raw_content, source=str(source))
    except configparser.Error as exc:
        raise ConfigError(f"invalid INI {source}: {exc}") from exc
    return parser, raw_content


def _required(parser: configparser.RawConfigParser, section: str, key: str) -> str:
    if not parser.has_section(section):
        raise ConfigError(f"missing section [{section}]")
    value = parser.get(section, key, fallback="").strip()
    if not value:
        raise ConfigError(f"missing config value: {section}.{key}")
    return value


def _parse_scalar(value: str) -> Any:
    text = value.strip()
    lowered = text.lower()
    if lowered in {"true", "false"}:
        return lowered == "true"
    try:
        return int(text)
    except ValueError:
        try:
            return float(text)
        except ValueError:
            return text


def _parse_bracket_parts(value: str, context: str) -> list[str]:
    parts: list[str] = []
    index = 0
    while index < len(value):
        while index < len(value) and value[index].isspace():
            index += 1
        if index >= len(value):
            break
        if value[index] != "[":
            raise ConfigError(f"{context} must use [value] groups")
        index += 1
        buffer: list[str] = []
        while index < len(value):
            char = value[index]
            if char == "\\":
                index += 1
                if index >= len(value):
                    raise ConfigError(f"{context} ends with an incomplete escape")
                buffer.append(value[index])
            elif char == "]":
                break
            else:
                buffer.append(char)
            index += 1
        if index >= len(value) or value[index] != "]":
            raise ConfigError(f"{context} contains an unclosed group")
        parts.append("".join(buffer))
        index += 1
    return parts


def _typed_item(name: str, value: str, context: str) -> tuple[str, str, dict[str, str]]:
    parts = _parse_bracket_parts(value, context)
    if not parts or not parts[0].strip():
        raise ConfigError(f"{context} is missing data type")
    data_type = parts[0].strip().upper()
    if data_type not in SUPPORTED_DATA_TYPES:
        raise ConfigError(f"{context} has unsupported data type: {data_type}")
    description = parts[1].strip() if len(parts) > 1 else ""
    modifiers: dict[str, str] = {}
    for modifier in parts[2:]:
        if "=" not in modifier:
            raise ConfigError(f"{context} has invalid modifier: {modifier}")
        key, raw = modifier.split("=", 1)
        modifiers[key.strip()] = raw.strip()
    return data_type, description, modifiers


def _parse_template(parser: configparser.RawConfigParser, name: str) -> TemplateDefinition:
    base = f"deviceTemplates.{name}"
    category_name = _required(parser, base, "categoryName")
    attributes_section = f"{base}.attributes"
    if not parser.has_section(attributes_section):
        raise ConfigError(f"missing section [{attributes_section}]")
    attributes: dict[str, AttributeDefinition] = {}
    for attribute_name, value in parser.items(attributes_section):
        data_type, description, _ = _typed_item(attribute_name, value, f"{attributes_section}.{attribute_name}")
        attributes[attribute_name] = AttributeDefinition(attribute_name, data_type, description)

    command_prefix = f"{base}.commands."
    command_names = [
        section[len(command_prefix):]
        for section in parser.sections()
        if section.startswith(command_prefix)
        and "." not in section[len(command_prefix):]
    ]
    commands: dict[str, CommandDefinition] = {}
    for command_name in command_names:
        command_section = f"{command_prefix}{command_name}"
        parameter_section = f"{command_section}.parameters"
        parameters: list[ParameterDefinition] = []
        if parser.has_section(parameter_section):
            for parameter_name, value in parser.items(parameter_section):
                data_type, description, modifiers = _typed_item(
                    parameter_name, value, f"{parameter_section}.{parameter_name}"
                )
                internal = modifiers.get("internal", "false").lower() == "true"
                source_field = modifiers.get("sourceField", "").strip()
                if internal and not source_field:
                    raise ConfigError(f"{parameter_section}.{parameter_name}: internal=true requires sourceField")
                parameters.append(ParameterDefinition(
                    name=parameter_name,
                    data_type=data_type,
                    description=description,
                    internal=internal,
                    source_field=source_field,
                ))
        commands[command_name] = CommandDefinition(
            name=command_name,
            description=parser.get(command_section, "description", fallback="").strip(),
            parameters=tuple(parameters),
        )

    cmd_section = f"{base}.events.cmdEvents"
    op_section = f"{base}.events.opEvents"
    cmd_events = dict(parser.items(cmd_section)) if parser.has_section(cmd_section) else {}
    op_events = dict(parser.items(op_section)) if parser.has_section(op_section) else {}
    return TemplateDefinition(
        name=name,
        category_name=category_name,
        category_description=parser.get(base, "categoryDescription", fallback="").strip(),
        description=parser.get(base, "description", fallback="").strip(),
        attributes=attributes,
        commands=commands,
        cmd_events=cmd_events,
        op_events=op_events,
    )


def load_adapter_config(path: str | Path) -> AdapterConfig:
    parser, raw_content = _read_ini(path)
    spec_version = _required(parser, "adapter", "specVersion")
    adapter_name = _required(parser, "adapter", "adapterName")
    raw_format = _required(parser, "adapter", "rawConfigFormat").upper()
    if raw_format != "INI":
        raise ConfigError("adapter.rawConfigFormat must be INI")

    template_names = [
        section.removeprefix("deviceTemplates.")
        for section in parser.sections()
        if section.startswith("deviceTemplates.")
        and "." not in section.removeprefix("deviceTemplates.")
    ]
    if not template_names:
        raise ConfigError("adapter config must contain at least one device template")
    templates = {name: _parse_template(parser, name) for name in template_names}

    point_names = [
        section.removeprefix("devicePoints.")
        for section in parser.sections()
        if section.startswith("devicePoints.")
        and "." not in section.removeprefix("devicePoints.")
    ]
    if not point_names:
        raise ConfigError("adapter config must contain at least one device point")
    points: dict[str, DevicePointDefinition] = {}
    for name in point_names:
        section = f"devicePoints.{name}"
        template_name = _required(parser, section, "templateName")
        if template_name not in templates:
            raise ConfigError(f"{section}.templateName references unknown template: {template_name}")
        standard = {"templateName", "description"}
        fields = {key: _parse_scalar(value) for key, value in parser.items(section) if key not in standard}
        mapping_section = f"{section}.attributeMapping"
        if not parser.has_section(mapping_section):
            raise ConfigError(f"missing section [{mapping_section}]")
        mapping = dict(parser.items(mapping_section))
        expected = set(templates[template_name].attributes)
        missing = expected - set(mapping)
        unknown = set(mapping) - expected
        if missing:
            raise ConfigError(f"{mapping_section} is missing attributes: {', '.join(sorted(missing))}")
        if unknown:
            raise ConfigError(f"{mapping_section} contains unknown attributes: {', '.join(sorted(unknown))}")
        points[name] = DevicePointDefinition(
            name=name,
            template_name=template_name,
            description=parser.get(section, "description", fallback="").strip(),
            fields=fields,
            attribute_mapping=mapping,
        )

    return AdapterConfig(
        spec_version=spec_version,
        adapter_name=adapter_name,
        adapter_description=parser.get("adapter", "adapterDescription", fallback="").strip(),
        raw_config_format=raw_format,
        templates=templates,
        device_points=points,
        raw_content=raw_content,
    )


def _positive_int(parser: configparser.RawConfigParser, section: str, key: str) -> int:
    value = _required(parser, section, key)
    try:
        result = int(value)
    except ValueError as exc:
        raise ConfigError(f"{section}.{key} must be an integer") from exc
    if result <= 0:
        raise ConfigError(f"{section}.{key} must be positive")
    return result


def load_runtime_config(path: str | Path) -> RuntimeConfig:
    parser, _ = _read_ini(path)
    qos_text = _required(parser, "mqtt", "qos")
    try:
        qos = int(qos_text)
    except ValueError as exc:
        raise ConfigError("mqtt.qos must be an integer") from exc
    if qos not in {0, 1, 2}:
        raise ConfigError("mqtt.qos must be 0, 1 or 2")
    broker = BrokerConfig(
        host=_required(parser, "mqtt", "host"),
        port=_positive_int(parser, "mqtt", "port"),
        username=parser.get("mqtt", "username", fallback="").strip(),
        password=parser.get("mqtt", "password", fallback=""),
        client_id=_required(parser, "mqtt", "clientId"),
        keep_alive_sec=_positive_int(parser, "mqtt", "keepAliveSec"),
        qos=qos,
    )
    topic_keys = {
        "system.register": ("systemTopics", "register"),
        "system.heartbeat": ("systemTopics", "heartbeat"),
        "system.command": ("systemTopics", "command"),
        "system.telemetry": ("systemTopics", "telemetry"),
        "system.event": ("systemTopics", "event"),
        "plc.command": ("plcTopics", "command"),
        "plc.telemetry": ("plcTopics", "telemetry"),
    }
    topics = {name: _required(parser, section, key) for name, (section, key) in topic_keys.items()}
    return RuntimeConfig(
        broker=broker,
        heartbeat_interval_sec=_positive_int(parser, "runtime", "heartbeatIntervalSec"),
        command_timeout_sec=_positive_int(parser, "runtime", "commandTimeoutSec"),
        log_level=_required(parser, "runtime", "logLevel").upper(),
        topics=topics,
    )


# ==============================================================================
# Adapter Routing & Command Manager
# ==============================================================================

class RoutingError(ValueError):
    """Raised when a system or PLC message cannot be safely routed."""


@dataclass(frozen=True)
class PendingCommand:
    message_id: str
    device_point: str
    command_name: str
    expected_registers: dict[str, int]
    created_at_ms: int


class AdapterRouter:
    def __init__(self, adapter: AdapterConfig, runtime: RuntimeConfig):
        self.adapter = adapter
        self.runtime = runtime
        self._pending: dict[str, PendingCommand] = {}
        self._latest: dict[str, PlcSnapshot] = {}
        self._lock = threading.RLock()

    @property
    def pending_count(self) -> int:
        with self._lock:
            return len(self._pending)

    def handle_system_command(
        self, payload: Any, topic: str, *, received_at_ms: int | None = None
    ) -> tuple[Publication, ...]:
        try:
            message = require_object(payload, "command")
            message_id = require_string(message.get("messageId"), "messageId")
            adapter_name = require_string(message.get("adapterName"), "adapterName")
            device_point = require_string(message.get("devicePoint"), "devicePoint")
            timestamp = require_timestamp(message.get("timestamp"))
            if adapter_name != self.adapter.adapter_name:
                raise ValueError(f"adapterName must be {self.adapter.adapter_name}")
            topic_point = extract_device_point(
                self.runtime.topics["system.command"], topic, self.adapter.adapter_name
            )
            if topic_point != device_point:
                raise ValueError(f"devicePoint does not match topic: {device_point} != {topic_point}")

            if message.get("operation") is not None:
                raise ValueError("operation不属于最终CommandMessageFormat；终止必须声明为isAbort=true的具体commandName")

            command_name = require_string(message.get("commandName"), "commandName")
            parameters = require_object(message.get("parameters"), "parameters")
            bound = self.adapter.command(device_point, command_name)
            all_parameters = self._build_parameters(bound, parameters, device_point)
            device_sn = require_string(all_parameters.get("deviceSN"), "deviceSN")
            registers = self._command_registers(command_name, all_parameters, device_point)
            pending = PendingCommand(
                message_id=message_id,
                device_point=device_point,
                command_name=command_name,
                expected_registers=registers,
                created_at_ms=timestamp if received_at_ms is None else received_at_ms,
            )
            with self._lock:
                if message_id in self._pending:
                    raise ValueError(f"messageId is already active: {message_id}")
                self._pending[message_id] = pending
        except (ValueError, KeyError, PlcProtocolError) as exc:
            raise RoutingError(str(exc)) from exc

        return (
            self._system_event(
                device_point=device_point,
                event_name="COMMAND_RECEIVED",
                timestamp=timestamp,
                message_id=message_id,
                payload={"commandName": command_name},
            ),
            Publication(
                topic=self.runtime.topic(
                    "plc.command",
                    adapterName=self.adapter.adapter_name,
                    devicePoint=device_point,
                ),
                payload=encode_register_write(device_sn, registers),
                qos=self.runtime.broker.qos,
            ),
        )

    def mark_command_running(self, message_id: str, timestamp: int) -> Publication:
        with self._lock:
            pending = self._pending.get(message_id)
            if pending is None:
                raise RoutingError(f"messageId is not active: {message_id}")
        return self._system_event(
            device_point=pending.device_point,
            event_name="COMMAND_RUNNING",
            timestamp=timestamp,
            message_id=message_id,
            payload={"commandName": pending.command_name},
        )

    def handle_plc_telemetry(
        self, payload: Any, topic: str, *, received_at_ms: int
    ) -> tuple[Publication, ...]:
        subscription = (
            self.runtime.topics["plc.telemetry"]
            .replace("{adapterName}", self.adapter.adapter_name)
            .replace("{devicePoint}", "+")
        )
        if not mqtt_topic_matches(subscription, topic):
            raise RoutingError(f"topic does not match configured PLC telemetry topic: {topic}")
        publications: list[Publication] = []
        matched = False
        for device_point, point in self.adapter.device_points.items():
            device_sn = point.fields.get("deviceSN")
            if not isinstance(device_sn, str) or not device_sn:
                raise RoutingError(f"devicePoint {device_point} is missing deviceSN")
            try:
                snapshot = decode_snapshot(payload, device_sn, received_at_ms)
            except PlcProtocolError as exc:
                if "does not contain DeviceSN" in str(exc):
                    continue
                raise RoutingError(str(exc)) from exc
            matched = True
            publications.extend(self._process_snapshot(device_point, point, snapshot))
        if not matched:
            raise RoutingError("PLC telemetry contains no configured DeviceSN")
        return tuple(publications)

    def expire_commands(self, timestamp: int) -> tuple[Publication, ...]:
        timeout_ms = self.runtime.command_timeout_sec * 1000
        expired: list[PendingCommand] = []
        with self._lock:
            for message_id, pending in list(self._pending.items()):
                if timestamp - pending.created_at_ms >= timeout_ms:
                    expired.append(pending)
                    del self._pending[message_id]
        return tuple(
            self._system_event(
                device_point=pending.device_point,
                event_name="COMMAND_FAILED",
                timestamp=timestamp,
                message_id=pending.message_id,
                payload={
                    "commandName": pending.command_name,
                    "errorMessage": f"等待 PLC 寄存器确认超时（{self.runtime.command_timeout_sec} 秒）",
                },
            )
            for pending in expired
        )

    def failure_publication(self, payload: Any, error: Exception) -> Publication | None:
        if not isinstance(payload, dict):
            return None
        message_id = payload.get("messageId")
        device_point = payload.get("devicePoint")
        if not isinstance(message_id, str) or not message_id.strip():
            return None
        if not isinstance(device_point, str) or device_point not in self.adapter.device_points:
            return None
        with self._lock:
            self._pending.pop(message_id, None)
        return self._system_event(
            device_point=device_point,
            event_name="COMMAND_FAILED",
            timestamp=payload.get("timestamp") if isinstance(payload.get("timestamp"), int) else 0,
            message_id=message_id,
            payload={"errorMessage": str(error)},
        )

    def _abort(
        self, message_id: str, device_point: str, timestamp: int, operation: Any
    ) -> tuple[Publication, ...]:
        if operation != "ABORT":
            raise ValueError(f"unsupported command operation: {operation}")
        with self._lock:
            pending = self._pending.get(message_id)
            if pending is None:
                raise ValueError(f"messageId is not active: {message_id}")
            if pending.device_point != device_point:
                raise ValueError("messageId belongs to another devicePoint")
            del self._pending[message_id]
        return (self._system_event(
            device_point=device_point,
            event_name="COMMAND_ABORTED",
            timestamp=timestamp,
            message_id=message_id,
            payload={"commandName": pending.command_name},
        ),)

    def _command_registers(
        self, command_name: str, parameters: dict[str, Any], device_point: str
    ) -> dict[str, int]:
        if command_name == "setOperatingMode":
            mode = parameters["mode"].strip().upper()
            if mode == "AUTO":
                return {"MW21": 1, "MW22": 0}
            if mode == "MANUAL":
                return {"MW21": 0, "MW22": 1}
            raise ValueError("mode must be AUTO or MANUAL")
        if command_name == "setCooling":
            enabled = parameters["enabled"]
            if enabled:
                with self._lock:
                    latest = self._latest.get(device_point)
                if latest is None or not latest.bit("MW22"):
                    raise ValueError("开启散热前必须先由 PLC 数据确认已进入手动模式")
            return {"MW20": 1 if enabled else 0}
        raise ValueError(f"unsupported PLC command: {command_name}")

    def _process_snapshot(
        self, device_point: str, point: DevicePointDefinition, snapshot: PlcSnapshot
    ) -> list[Publication]:
        template = self.adapter.templates[point.template_name]
        model_data: dict[str, Any] = {}
        for model_name, register in point.attribute_mapping.items():
            definition = template.attributes[model_name]
            if definition.data_type == "BOOLEAN":
                value: Any = snapshot.bit(register)
            else:
                value = snapshot.word(register)
            if model_name == "temperature":
                value = float(value) / 100.0
            if not matches_data_type(value, definition.data_type):
                raise RoutingError(f"register {register} does not match {definition.data_type}")
            model_data[model_name] = value

        publications = [Publication(
            topic=self.runtime.topic(
                "system.telemetry",
                adapterName=self.adapter.adapter_name,
                devicePoint=device_point,
            ),
            payload={
                "timestamp": snapshot.received_at_ms,
                "adapterName": self.adapter.adapter_name,
                "devicePoint": device_point,
                "telemetryData": model_data,
            },
            qos=self.runtime.broker.qos,
        )]

        with self._lock:
            previous = self._latest.get(device_point)
            self._latest[device_point] = snapshot
            completed = [
                pending for pending in self._pending.values()
                if pending.device_point == device_point
                and all(snapshot.values.get(name) == value for name, value in pending.expected_registers.items())
            ]
            for pending in completed:
                del self._pending[pending.message_id]

        if previous is not None:
            publications.extend(self._operation_events(device_point, previous, snapshot))
        publications.extend(
            self._system_event(
                device_point=device_point,
                event_name="COMMAND_COMPLETED",
                timestamp=snapshot.received_at_ms,
                message_id=pending.message_id,
                payload={"commandName": pending.command_name},
            )
            for pending in completed
        )
        return publications

    def _operation_events(
        self, device_point: str, previous: PlcSnapshot, current: PlcSnapshot
    ) -> list[Publication]:
        names: list[str] = []
        if not previous.bit("MW21") and current.bit("MW21"):
            names.append("AUTOMATIC_MODE_ENTERED")
        if not previous.bit("MW22") and current.bit("MW22"):
            names.append("MANUAL_MODE_ENTERED")
        if previous.bit("MW20") != current.bit("MW20"):
            names.append("COOLING_STARTED" if current.bit("MW20") else "COOLING_STOPPED")
        return [
            self._system_event(
                device_point=device_point,
                event_name=name,
                timestamp=current.received_at_ms,
                message_id="",
                payload={},
            )
            for name in names
        ]

    def _build_parameters(
        self, command: BoundCommand, parameters: dict[str, Any], device_point: str
    ) -> dict[str, Any]:
        allowed = {item.name for item in command.public_parameters}
        unknown = set(parameters) - allowed
        if unknown:
            raise ValueError(f"parameters contain unknown fields: {', '.join(sorted(unknown))}")
        result: dict[str, Any] = {}
        for definition in command.public_parameters:
            if definition.name not in parameters:
                raise ValueError(f"missing command parameter: {definition.name}")
            value = parameters[definition.name]
            if not matches_data_type(value, definition.data_type):
                raise ValueError(
                    f"command parameter {definition.name} does not match {definition.data_type}"
                )
            result[definition.name] = value
        point = self.adapter.device_point(device_point)
        for definition in command.internal_parameters:
            if definition.source_field not in point.fields:
                raise ValueError(
                    f"internal parameter {definition.name} sourceField={definition.source_field} is missing"
                )
            value = point.fields[definition.source_field]
            if not matches_data_type(value, definition.data_type):
                raise ValueError(f"internal parameter {definition.name} has the wrong type")
            result[definition.name] = value
        return result

    def _system_event(
        self,
        *,
        device_point: str,
        event_name: str,
        timestamp: int,
        message_id: str,
        payload: dict[str, Any],
    ) -> Publication:
        message: dict[str, Any] = {
            "timestamp": timestamp,
            "adapterName": self.adapter.adapter_name,
            "devicePoint": device_point,
            "eventName": event_name,
            "payload": payload,
        }
        if message_id:
            message["messageId"] = message_id
        return Publication(
            topic=self.runtime.topic(
                "system.event",
                adapterName=self.adapter.adapter_name,
                devicePoint=device_point,
            ),
            payload=message,
            qos=self.runtime.broker.qos,
        )


# ==============================================================================
# MQTT Communications Runtime
# ==============================================================================

LOGGER = logging.getLogger(__name__)


def create_mqtt_client(config: RuntimeConfig):
    try:
        import paho.mqtt.client as mqtt
    except ImportError as exc:
        raise RuntimeError("missing dependency; run: pip install -r requirements.txt") from exc
    return mqtt.Client(
        callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
        client_id=config.broker.client_id,
        clean_session=True,
    )


class AdapterRuntime:
    def __init__(
        self,
        adapter: AdapterConfig,
        config: RuntimeConfig,
        router: AdapterRouter,
        *,
        client: Any | None = None,
    ):
        self.adapter = adapter
        self.config = config
        self.router = router
        self.client = client if client is not None else create_mqtt_client(config)
        self._stop_event = threading.Event()
        self._heartbeat_thread: threading.Thread | None = None
        self._stopped = False
        if config.broker.username:
            self.client.username_pw_set(config.broker.username, config.broker.password)
        self.client.reconnect_delay_set(min_delay=2, max_delay=30)
        self.client.on_connect = self._on_connect
        self.client.on_disconnect = self._on_disconnect
        self.client.on_message = self._on_message

    def start(self) -> None:
        self._stopped = False
        self._stop_event.clear()
        broker = self.config.broker
        LOGGER.info("1. 正在连接 MQTT 服务器 %s:%s as %s...", broker.host, broker.port, broker.client_id)
        self.client.connect(broker.host, broker.port, broker.keep_alive_sec)
        self.client.loop_start()
        self._heartbeat_thread = threading.Thread(
            target=self._heartbeat_loop,
            name="adapter-heartbeat",
            daemon=True,
        )
        self._heartbeat_thread.start()

    def stop(self) -> None:
        if self._stopped:
            return
        self._stopped = True
        self._stop_event.set()
        try:
            self.publish_heartbeat("SHUTTING_DOWN", wait=True)
        except Exception as exc:
            LOGGER.warning("could not publish shutdown heartbeat: %s", exc)
        if self._heartbeat_thread and self._heartbeat_thread.is_alive():
            self._heartbeat_thread.join(timeout=2)
        self.client.loop_stop()
        self.client.disconnect()

    def publish_heartbeat(self, status: str = "ALIVE", *, wait: bool = False) -> None:
        if status not in {"ALIVE", "DEGRADED", "SHUTTING_DOWN"}:
            raise ValueError(f"unsupported heartbeat status: {status}")
        self._publish(Publication(
            topic=self.config.topic("system.heartbeat", adapterName=self.adapter.adapter_name),
            payload={"status": status, "timestamp": now_ms()},
            qos=self.config.broker.qos,
        ), wait=wait)

    def publish_expired_commands(self, timestamp: int | None = None) -> None:
        for publication in self.router.expire_commands(now_ms() if timestamp is None else timestamp):
            self._publish(publication)

    def _on_connect(self, client, _userdata, _flags, reason_code, _properties=None) -> None:
        if getattr(reason_code, "is_failure", False) or (
            isinstance(reason_code, int) and reason_code != 0
        ):
            LOGGER.error("MQTT 服务器连接失败: %s", reason_code)
            return
        LOGGER.info("MQTT 服务器连接成功！")
        LOGGER.info("2. 正在连接 PLC 并订阅通信频道...")
        for name in ("system.command", "plc.telemetry"):
            client.subscribe(self._subscription_topic(name), qos=self.config.broker.qos)
        LOGGER.info("PLC 通信与指令频道订阅成功！")
        self._publish_registration()

    def _on_disconnect(self, _client, _userdata, *args) -> None:
        reason_code = args[-2] if len(args) >= 2 else args[0] if args else None
        if not self._stop_event.is_set():
            LOGGER.warning("MQTT disconnected; Paho will reconnect automatically: %s", reason_code)

    def _on_message(self, _client, _userdata, message) -> None:
        payload: Any = None
        command_message = False
        try:
            payload = json.loads(message.payload.decode("utf-8"))
            command_topic = self._subscription_topic("system.command")
            command_message = mqtt_topic_matches(command_topic, message.topic)
            if command_message:
                publications = self.router.handle_system_command(payload, message.topic)
                for publication in publications:
                    self._publish(publication)
                if len(publications) >= 2:
                    self._publish(self.router.mark_command_running(payload["messageId"], now_ms()))
            elif mqtt_topic_matches(self._subscription_topic("plc.telemetry"), message.topic):
                publications = self.router.handle_plc_telemetry(payload, message.topic, received_at_ms=now_ms())
                for publication in publications:
                    self._publish(publication)
        except Exception as exc:
            if command_message:
                LOGGER.error("command execution failed: %s", exc)
                failure = self.router.failure_publication(payload, exc)
                if failure is not None:
                    try:
                        self._publish(failure)
                    except Exception:
                        LOGGER.exception("could not publish COMMAND_FAILED for topic %s", message.topic)
        except Exception:
            LOGGER.exception("unexpected MQTT message handler failure on topic %s", message.topic)

    def _subscription_topic(self, name: str) -> str:
        return (
            self.config.topics[name]
            .replace("{adapterName}", self.adapter.adapter_name)
            .replace("{devicePoint}", "+")
        )

    def _publish_registration(self) -> None:
        LOGGER.info("3. 正在发送 Adapter 注册信息 (Topic: %s)...", self.config.topic("system.register"))
        self._publish(Publication(
            topic=self.config.topic("system.register"),
            payload={
                "adapterName": self.adapter.adapter_name,
                "rawConfigFormat": self.adapter.raw_config_format,
                "rawConfigContent": self.adapter.raw_content,
                "timestamp": now_ms(),
            },
            qos=self.config.broker.qos,
        ))
        LOGGER.info("Adapter 注册信息发送成功！")

    def _publish(self, publication: Publication, *, wait: bool = False) -> None:
        payload = json.dumps(publication.payload, ensure_ascii=False, separators=(",", ":"))
        result = self.client.publish(
            publication.topic,
            payload,
            qos=publication.qos,
            retain=publication.retain,
        )
        if wait:
            result.wait_for_publish(timeout=5)
        if getattr(result, "rc", 0) != 0:
            raise RuntimeError(f"MQTT publish failed topic={publication.topic} rc={result.rc}")

    def _heartbeat_loop(self) -> None:
        while not self._stop_event.wait(self.config.heartbeat_interval_sec):
            try:
                self.publish_heartbeat("ALIVE")
                self._publish_registration()
                self.publish_expired_commands()
            except Exception as exc:
                LOGGER.warning("heartbeat, registration or command-timeout publish failed: %s", exc)


# ==============================================================================
# Main Entrypoint
# ==============================================================================

BASE_DIR = Path(__file__).resolve().parent


def main() -> int:
    try:
        adapter = load_adapter_config(BASE_DIR / "adapterconfig.ini")
        runtime_config = load_runtime_config(BASE_DIR / "setup.ini")
    except ConfigError as exc:
        print(f"Adapter configuration error: {exc}")
        return 2

    logging.basicConfig(
        level=getattr(logging, runtime_config.log_level, logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s - %(message)s",
    )
    stop_requested = threading.Event()

    def request_stop(_signum=None, _frame=None):
        stop_requested.set()

    signal.signal(signal.SIGINT, request_stop)
    signal.signal(signal.SIGTERM, request_stop)

    router = AdapterRouter(adapter, runtime_config)
    runtime = AdapterRuntime(adapter, runtime_config, router)
    try:
        runtime.start()
        stop_requested.wait()
    except KeyboardInterrupt:
        pass
    finally:
        runtime.stop()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
