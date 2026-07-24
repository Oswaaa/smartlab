#!/usr/bin/env python3
"""
SmartLab Edge Agent Runtime.

This process is the physical device execution layer. It loads agent profiles
from SmartLab, subscribes to SmartLab northbound command topics, translates
commands to southbound connectors, polls telemetry, and reports status/events
back to the upper system.
"""

from __future__ import annotations

import argparse
import importlib
import importlib.util
import json
import os
import signal
import socket
import subprocess
import sys
import threading
import time
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Callable
from urllib import request as http_request
from urllib.error import URLError
from urllib.parse import urlencode

try:
    import paho.mqtt.client as mqtt
except Exception:  # pragma: no cover - dependency can be installed on edge nodes
    mqtt = None


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def load_json(path: str) -> dict[str, Any]:
    with open(path, "r", encoding="utf-8") as fh:
        return json.load(fh)


def render_template(value: Any, context: dict[str, Any]) -> Any:
    if isinstance(value, str):
        exact = lookup_template_value(value, context)
        if exact is not None:
            return exact
        rendered = value
        for key, item in flatten_context(context).items():
            rendered = rendered.replace("${" + key + "}", str(item))
            rendered = rendered.replace("{" + key + "}", str(item))
        return rendered
    if isinstance(value, list):
        return [render_template(item, context) for item in value]
    if isinstance(value, dict):
        return {str(render_template(k, context)): render_template(v, context) for k, v in value.items()}
    return value


def lookup_template_value(value: str, context: dict[str, Any]) -> Any | None:
    text = value.strip()
    key = ""
    if text.startswith("${") and text.endswith("}"):
        key = text[2:-1].strip()
    elif text.startswith("{") and text.endswith("}") and text.count("{") == 1 and text.count("}") == 1:
        key = text[1:-1].strip()
    if not key:
        return None
    marker = object()
    found = path_get(context, key, marker)
    return None if found is marker else found


def flatten_context(value: Any, prefix: str = "") -> dict[str, Any]:
    result: dict[str, Any] = {}
    if isinstance(value, dict):
        for key, item in value.items():
            next_prefix = f"{prefix}.{key}" if prefix else str(key)
            result.update(flatten_context(item, next_prefix))
    else:
        result[prefix] = value
    return result


def path_get(value: Any, path: str, default: Any = None) -> Any:
    current = value
    for part in str(path or "").split("."):
        if not part:
            continue
        if isinstance(current, dict):
            current = current.get(part, default)
        elif isinstance(current, list) and part.isdigit():
            index = int(part)
            current = current[index] if index < len(current) else default
        else:
            return default
    return current


def path_set(value: dict[str, Any], path: str, item: Any) -> None:
    parts = [p for p in str(path or "").split(".") if p]
    if not parts:
        return
    current: dict[str, Any] = value
    for part in parts[:-1]:
        next_value = current.get(part)
        if not isinstance(next_value, dict):
            next_value = {}
            current[part] = next_value
        current = next_value
    current[parts[-1]] = item


def build_execution_context(profile: dict[str, Any], command_id: str, payload: dict[str, Any], mapping: dict[str, Any]) -> dict[str, Any]:
    context = {
        "profile": profile,
        "agent": profile.get("agent", {}),
        "commandId": command_id,
        "payload": dict(payload or {}),
        "mapping": mapping or {},
    }
    for rule in mapping.get("valueMaps") or []:
        if not isinstance(rule, dict):
            continue
        source = str(rule.get("source") or "").strip()
        target = str(rule.get("target") or "").strip()
        lookup = rule.get("map") or rule.get("values") or {}
        source_value = path_get(context, source if "." in source else "payload." + source)
        mapped = lookup.get(str(source_value), rule.get("default"))
        if target and mapped is not None:
            path_set(context, target if "." in target else "payload." + target, mapped)
    return context


def parse_bytes(value: Any, encoding: str = "utf-8") -> bytes:
    if isinstance(value, bytes):
        return value
    if isinstance(value, list):
        return bytes(int(v) & 0xFF for v in value)
    text = str(value or "")
    if text.startswith("hex:"):
        return bytes.fromhex(text[4:].replace(" ", ""))
    return text.encode(encoding)


def find_mapping(command_mappings: list[dict[str, Any]], command_id: str) -> dict[str, Any]:
    for mapping in command_mappings:
        if mapping.get("commandId") == command_id or mapping.get("commandName") == command_id:
            return mapping
    return {}


def require_non_empty_string(value: Any, field_name: str) -> str:
    text = str(value or "").strip()
    if not text:
        raise RuntimeError(f"{field_name} is required")
    return text


def validate_agent_config(config: dict[str, Any]) -> None:
    if not isinstance(config, dict):
        raise RuntimeError("agent profile must be an object")
    agent = config.get("agent") or {}
    agent_id = require_non_empty_string(config.get("agentId") or agent.get("agentId"), "agentId")
    northbound = config.get("northbound") or agent.get("northboundContract") or {}
    southbound = config.get("southbound") or agent.get("southboundProfile") or {}
    if not isinstance(northbound, dict):
        raise RuntimeError(f"agent {agent_id} northbound contract must be an object")
    if not isinstance(southbound, dict):
        raise RuntimeError(f"agent {agent_id} southbound profile must be an object")
    for topic_name in ("commandTopic", "statusTopic", "eventTopic", "telemetryTopic", "heartbeatTopic"):
        require_non_empty_string(northbound.get(topic_name), f"agent {agent_id} northbound.{topic_name}")

    commands = northbound.get("commands")
    if not isinstance(commands, list) or not commands:
        raise RuntimeError(f"agent {agent_id} northbound.commands must contain at least one command")
    command_ids: list[str] = []
    for index, command in enumerate(commands):
        if not isinstance(command, dict):
            raise RuntimeError(f"agent {agent_id} northbound.commands[{index}] must be an object")
        command_id = require_non_empty_string(command.get("commandId"), f"agent {agent_id} northbound.commands[{index}].commandId")
        if command_id in command_ids:
            raise RuntimeError(f"agent {agent_id} contains duplicate commandId: {command_id}")
        command_ids.append(command_id)

    require_non_empty_string(config.get("adapterName") or agent.get("adapterName"), f"agent {agent_id} adapterName")
    require_non_empty_string(config.get("devicePoint") or agent.get("devicePoint"), f"agent {agent_id} devicePoint")
    connector_type = require_non_empty_string(southbound.get("connectorType"), f"agent {agent_id} southbound.connectorType").lower()
    if connector_type == "simulated" and southbound.get("allowSimulation") is not True:
        raise RuntimeError(f"agent {agent_id} simulated connector requires southbound.allowSimulation=true")
    mappings = southbound.get("commandMappings") or []
    if not isinstance(mappings, list) or any(not isinstance(mapping, dict) for mapping in mappings):
        raise RuntimeError(f"agent {agent_id} southbound.commandMappings must be an array of objects")
    if connector_type != "simulated":
        mapped_ids = {
            str(mapping.get("commandId") or mapping.get("commandName") or "").strip()
            for mapping in mappings
        }
        missing = [command_id for command_id in command_ids if command_id not in mapped_ids]
        if missing:
            raise RuntimeError(f"agent {agent_id} southbound command mapping missing: {', '.join(missing)}")


def validate_command_envelope(envelope: dict[str, Any]) -> str:
    """Validate the final ProtocolSpec.CommandMessageFormat, not the retired smartlab.adapter.v1 envelope."""
    if not isinstance(envelope, dict):
        raise RuntimeError("command message must be an object")
    require_non_empty_string(envelope.get("messageId"), "command message messageId")
    require_non_empty_string(envelope.get("adapterName"), "command message adapterName")
    require_non_empty_string(envelope.get("devicePoint"), "command message devicePoint")
    command_name = require_non_empty_string(envelope.get("commandName"), "command message commandName")
    timestamp = envelope.get("timestamp")
    if not isinstance(timestamp, int) or isinstance(timestamp, bool):
        raise RuntimeError("command message timestamp must be an integer")
    if not isinstance(envelope.get("parameters"), dict):
        raise RuntimeError("command message parameters must be an object")
    return command_name


class SouthboundConnector:
    def execute(self, command_id: str, payload: dict[str, Any], mapping: dict[str, Any]) -> dict[str, Any]:
        raise NotImplementedError

    def poll(self) -> dict[str, Any]:
        return {}

    def close(self) -> None:
        return None


class SimulatedConnector(SouthboundConnector):
    def __init__(self, profile: dict[str, Any]):
        self.profile = profile
        self.counter = 0

    def execute(self, command_id: str, payload: dict[str, Any], mapping: dict[str, Any]) -> dict[str, Any]:
        delay_ms = int(mapping.get("simulateDelayMs", self.profile.get("simulateDelayMs", 300)))
        if delay_ms > 0:
            time.sleep(delay_ms / 1000)
        return {"ok": True, "commandId": command_id, "echo": payload}

    def poll(self) -> dict[str, Any]:
        self.counter += 1
        defaults = self.profile.get("telemetryDefaults") or {}
        data = dict(defaults)
        data.setdefault("runtimeCounter", self.counter)
        data.setdefault("timestamp", utc_now())
        return data


class ProxyHttpConnector(SouthboundConnector):
    def __init__(self, profile: dict[str, Any]):
        self.profile = profile
        self.base_url = str(profile.get("baseUrl", "")).rstrip("/")

    def execute(self, command_id: str, payload: dict[str, Any], mapping: dict[str, Any]) -> dict[str, Any]:
        if not self.base_url:
            raise RuntimeError("proxy-http connector requires southboundProfile.baseUrl")
        context = build_execution_context(self.profile, command_id, payload, mapping)
        method = str(mapping.get("method", "POST")).upper()
        path = render_template(mapping.get("path", f"/{command_id}"), context)
        body = render_template(mapping.get("bodyTemplate", payload), context)
        query = render_template(mapping.get("queryTemplate", {}), context)
        url = self.base_url + str(path)
        if query:
            url += "?" + urlencode(query)
        data = None
        headers = {"Content-Type": "application/json"}
        if method in {"POST", "PUT", "PATCH"}:
            data = json.dumps(body).encode("utf-8")
        req = http_request.Request(url, data=data, method=method, headers=headers)
        try:
            with http_request.urlopen(req, timeout=float(mapping.get("timeoutSec", 10))) as resp:
                text = resp.read().decode("utf-8", errors="replace")
                try:
                    parsed = json.loads(text) if text else {}
                except json.JSONDecodeError:
                    parsed = {"raw": text}
                return {"ok": 200 <= resp.status < 300, "status": resp.status, "body": parsed}
        except URLError as exc:
            raise RuntimeError(f"proxy-http request failed: {exc}") from exc

    def poll(self) -> dict[str, Any]:
        polling = self.profile.get("polling") or {}
        path = polling.get("path") or self.profile.get("telemetryPath")
        if not path or not self.base_url:
            return {}
        url = self.base_url + str(path)
        try:
            with http_request.urlopen(url, timeout=float(polling.get("timeoutSec", 5))) as resp:
                text = resp.read().decode("utf-8", errors="replace")
                return json.loads(text) if text else {}
        except Exception as exc:
            raise RuntimeError(f"proxy-http poll failed: {exc}") from exc


class ProcessConnector(SouthboundConnector):
    def __init__(self, profile: dict[str, Any]):
        self.profile = profile
        self.process: subprocess.Popen[str] | None = None
        command = profile.get("startupCommand")
        if command:
            self.process = subprocess.Popen(command, shell=True, text=True)

    def execute(self, command_id: str, payload: dict[str, Any], mapping: dict[str, Any]) -> dict[str, Any]:
        command = mapping.get("command") or mapping.get("shell")
        if not command:
            raise RuntimeError("process connector requires mapping.command")
        rendered = render_template(command, build_execution_context(self.profile, command_id, payload, mapping))
        completed = subprocess.run(rendered, shell=True, text=True, capture_output=True, timeout=float(mapping.get("timeoutSec", 30)))
        return {
            "ok": completed.returncode == 0,
            "returnCode": completed.returncode,
            "stdout": completed.stdout[-4000:],
            "stderr": completed.stderr[-4000:],
        }

    def close(self) -> None:
        if self.process and self.process.poll() is None:
            self.process.terminate()


class TcpConnector(SouthboundConnector):
    def __init__(self, profile: dict[str, Any]):
        self.profile = profile
        self.host = str(profile.get("host") or profile.get("ip") or "127.0.0.1")
        self.port = int(profile.get("port") or 0)
        if not self.port:
            raise RuntimeError("tcp connector requires southboundProfile.port")

    def execute(self, command_id: str, payload: dict[str, Any], mapping: dict[str, Any]) -> dict[str, Any]:
        context = build_execution_context(self.profile, command_id, payload, mapping)
        frame = render_template(mapping.get("frameTemplate", mapping.get("bodyTemplate", payload)), context)
        encoding = str(mapping.get("encoding") or self.profile.get("encoding") or "utf-8")
        timeout = float(mapping.get("timeoutSec", self.profile.get("timeoutSec", 5)))
        response_mode = str(mapping.get("responseMode", "text"))
        with socket.create_connection((self.host, self.port), timeout=timeout) as sock:
            sock.settimeout(timeout)
            sock.sendall(parse_bytes(frame, encoding))
            if mapping.get("appendNewline", self.profile.get("appendNewline", False)):
                sock.sendall(b"\n")
            response = b""
            if mapping.get("readResponse", True):
                response = sock.recv(int(mapping.get("maxResponseBytes", 4096)))
        if response_mode == "json":
            text = response.decode(encoding, errors="replace")
            return {"ok": True, "response": json.loads(text) if text else {}}
        if response_mode == "hex":
            return {"ok": True, "responseHex": response.hex(" ")}
        return {"ok": True, "response": response.decode(encoding, errors="replace")}


class MqttDeviceConnector(SouthboundConnector):
    def __init__(self, profile: dict[str, Any], runtime: "EdgeRuntime"):
        self.profile = profile
        self.runtime = runtime
        self.state: dict[str, Any] = {}
        self.state_lock = threading.Lock()
        for topic in profile.get("subscribeTopics") or []:
            runtime.subscribe(str(topic), self.handle_message)

    def handle_message(self, topic: str, envelope: dict[str, Any]) -> None:
        with self.state_lock:
            self.state["lastTopic"] = topic
            self.state["lastMessage"] = envelope
            for mapping in self.profile.get("stateMappings") or []:
                source = str(mapping.get("source") or "").strip()
                target = str(mapping.get("target") or "").strip()
                if source and target:
                    path_set(self.state, target, path_get(envelope, source))

    def execute(self, command_id: str, payload: dict[str, Any], mapping: dict[str, Any]) -> dict[str, Any]:
        context = build_execution_context(self.profile, command_id, payload, mapping)
        topic = str(render_template(mapping.get("topic") or self.profile.get("publishTopic"), context))
        if not topic:
            raise RuntimeError("mqtt-device connector requires mapping.topic or southboundProfile.publishTopic")
        body = render_template(mapping.get("payloadTemplate", payload), context)
        self.runtime.publish(topic, body if isinstance(body, dict) else {"payload": body}, qos=int(mapping.get("qos", self.profile.get("qos", 1))))
        success_when = mapping.get("successWhen") or {}
        if success_when:
            self.wait_until(success_when, float(success_when.get("timeoutMs", mapping.get("timeoutMs", 5000))) / 1000)
        return {"ok": True, "topic": topic}

    def wait_until(self, condition: dict[str, Any], timeout_sec: float) -> None:
        path = str(condition.get("path") or "")
        expected = condition.get("equals")
        deadline = time.time() + max(timeout_sec, 0.1)
        while time.time() < deadline:
            with self.state_lock:
                current = path_get(self.state, path)
            if current == expected or str(current) == str(expected):
                return
            time.sleep(0.05)
        raise RuntimeError(f"mqtt-device success condition timeout: {path} != {expected}")

    def poll(self) -> dict[str, Any]:
        telemetry = {}
        with self.state_lock:
            snapshot = dict(self.state)
        for mapping in self.profile.get("telemetryMappings") or []:
            source = str(mapping.get("source") or "")
            target = str(mapping.get("target") or source)
            if source and target:
                value = path_get(snapshot, source)
                if value is not None:
                    path_set(telemetry, target, value)
        return telemetry


class ModbusConnector(SouthboundConnector):
    def __init__(self, profile: dict[str, Any], mode: str):
        self.profile = profile
        self.mode = mode
        self.connection = {**(profile.get("connection") or {}), **profile}
        self.unit_id = int(self.connection.get("unitId") or self.connection.get("slaveId") or 1)
        self.timeout = float(self.connection.get("timeoutSec", self.connection.get("timeout", 3)))
        self.client = self.create_client()

    def create_client(self) -> Any:
        try:
            from pymodbus.client import ModbusTcpClient, ModbusSerialClient
        except Exception:
            try:
                from pymodbus.client.sync import ModbusTcpClient, ModbusSerialClient
            except Exception as exc:
                raise RuntimeError("modbus connector requires pymodbus. Install on the edge node with: pip install pymodbus") from exc

        if self.mode == "tcp":
            host = str(self.connection.get("host") or self.connection.get("ip") or "127.0.0.1")
            port = int(self.connection.get("port") or 502)
            return ModbusTcpClient(host=host, port=port, timeout=self.timeout)

        port_name = str(self.connection.get("port") or self.connection.get("serialPort") or self.connection.get("device") or "")
        if not port_name:
            raise RuntimeError("modbus-rtu connector requires connection.port")
        kwargs = {
            "port": port_name,
            "baudrate": int(self.connection.get("baudrate") or 9600),
            "bytesize": int(self.connection.get("bytesize") or 8),
            "parity": str(self.connection.get("parity") or "N"),
            "stopbits": int(self.connection.get("stopbits") or 1),
            "timeout": self.timeout,
        }
        try:
            return ModbusSerialClient(method="rtu", **kwargs)
        except TypeError:
            return ModbusSerialClient(**kwargs)

    def execute(self, command_id: str, payload: dict[str, Any], mapping: dict[str, Any]) -> dict[str, Any]:
        self.ensure_connected()
        context = build_execution_context(self.profile, command_id, payload, mapping)
        steps = mapping.get("steps") or mapping.get("operations") or [mapping]
        results = [self.run_step(step, context) for step in steps if isinstance(step, dict)]
        return {"ok": True, "commandId": command_id, "results": results}

    def poll(self) -> dict[str, Any]:
        self.ensure_connected()
        telemetry: dict[str, Any] = {}
        for mapping in self.profile.get("telemetryMappings") or []:
            if not isinstance(mapping, dict):
                continue
            result = self.run_step(mapping, build_execution_context(self.profile, "__poll__", {}, mapping))
            target = str(mapping.get("target") or mapping.get("field") or "")
            if target and "value" in result:
                path_set(telemetry, target, result["value"])
        return telemetry

    def run_step(self, step: dict[str, Any], context: dict[str, Any]) -> dict[str, Any]:
        op = self.normalize_operation(step.get("operation") or step.get("op") or "readHoldingRegisters")
        address = int(render_template(step.get("address", 0), context))
        count = int(render_template(step.get("count", 1), context))

        if op == "writeregister":
            value = int(render_template(step.get("valueTemplate", step.get("value", path_get(context, "payload.value", 0))), context))
            self.check_result(self.call_modbus(self.client.write_register, address, value), op)
            return {"operation": op, "address": address, "value": value}
        if op == "writeregisters":
            values = render_template(step.get("valuesTemplate", step.get("values", [])), context)
            if not isinstance(values, list):
                values = [values]
            values = [int(v) for v in values]
            self.check_result(self.call_modbus(self.client.write_registers, address, values), op)
            return {"operation": op, "address": address, "values": values}
        if op == "writecoil":
            value = bool(render_template(step.get("valueTemplate", step.get("value", path_get(context, "payload.value", False))), context))
            self.check_result(self.call_modbus(self.client.write_coil, address, value), op)
            return {"operation": op, "address": address, "value": value}
        if op == "writecoils":
            values = render_template(step.get("valuesTemplate", step.get("values", [])), context)
            if not isinstance(values, list):
                values = [values]
            values = [bool(v) for v in values]
            self.check_result(self.call_modbus(self.client.write_coils, address, values), op)
            return {"operation": op, "address": address, "values": values}

        if op == "readholdingregisters":
            response = self.check_result(self.call_modbus(self.client.read_holding_registers, address, count), op)
            values = list(getattr(response, "registers", []) or [])
            return self.format_read_result(step, address, values)
        if op == "readinputregisters":
            response = self.check_result(self.call_modbus(self.client.read_input_registers, address, count), op)
            values = list(getattr(response, "registers", []) or [])
            return self.format_read_result(step, address, values)
        if op == "readcoils":
            response = self.check_result(self.call_modbus(self.client.read_coils, address, count), op)
            values = list(getattr(response, "bits", []) or [])[:count]
            return self.format_read_result(step, address, values)
        if op == "readdiscreteinputs":
            response = self.check_result(self.call_modbus(self.client.read_discrete_inputs, address, count), op)
            values = list(getattr(response, "bits", []) or [])[:count]
            return self.format_read_result(step, address, values)
        raise RuntimeError(f"unsupported modbus operation: {step.get('operation')}")

    def format_read_result(self, step: dict[str, Any], address: int, values: list[Any]) -> dict[str, Any]:
        value: Any = values[0] if int(step.get("count", 1)) == 1 and values else values
        if isinstance(value, (int, float)):
            scale = float(step.get("scale", 1))
            offset = float(step.get("offset", 0))
            value = value * scale + offset
        return {"operation": self.normalize_operation(step.get("operation")), "address": address, "values": values, "value": value}

    def ensure_connected(self) -> None:
        if hasattr(self.client, "connected") and self.client.connected:
            return
        if hasattr(self.client, "connect") and not self.client.connect():
            raise RuntimeError(f"modbus-{self.mode} connection failed")

    def call_modbus(self, fn: Callable[..., Any], *args: Any) -> Any:
        try:
            return fn(*args, slave=self.unit_id)
        except TypeError:
            try:
                return fn(*args, unit=self.unit_id)
            except TypeError:
                return fn(*args)

    def check_result(self, result: Any, operation: str) -> Any:
        if hasattr(result, "isError") and result.isError():
            raise RuntimeError(f"modbus operation failed: {operation} -> {result}")
        return result

    def normalize_operation(self, value: Any) -> str:
        return str(value or "").replace("_", "").replace("-", "").lower()

    def close(self) -> None:
        if hasattr(self.client, "close"):
            self.client.close()


class SerialConnector(SouthboundConnector):
    def __init__(self, profile: dict[str, Any]):
        self.profile = profile
        self.connection = {**(profile.get("connection") or {}), **profile}

    def execute(self, command_id: str, payload: dict[str, Any], mapping: dict[str, Any]) -> dict[str, Any]:
        context = build_execution_context(self.profile, command_id, payload, mapping)
        response = self.transact(mapping, context)
        return {"ok": True, "commandId": command_id, **response}

    def poll(self) -> dict[str, Any]:
        telemetry: dict[str, Any] = {}
        for mapping in self.profile.get("telemetryMappings") or []:
            if not isinstance(mapping, dict):
                continue
            response = self.transact(mapping, build_execution_context(self.profile, "__poll__", {}, mapping))
            target = str(mapping.get("target") or mapping.get("field") or "")
            if target:
                path_set(telemetry, target, response.get("value", response.get("response")))
        return telemetry

    def transact(self, mapping: dict[str, Any], context: dict[str, Any]) -> dict[str, Any]:
        try:
            import serial
        except Exception as exc:
            raise RuntimeError("serial/rs485 connector requires pyserial. Install on the edge node with: pip install pyserial") from exc

        port_name = str(self.connection.get("port") or self.connection.get("serialPort") or self.connection.get("device") or "")
        if not port_name:
            raise RuntimeError("serial connector requires connection.port")
        encoding = str(mapping.get("encoding") or self.connection.get("encoding") or "utf-8")
        timeout = float(mapping.get("timeoutSec", self.connection.get("timeoutSec", 3)))
        frame = render_template(mapping.get("frameTemplate", mapping.get("bodyTemplate", mapping.get("payloadTemplate", ""))), context)
        read_response = bool(mapping.get("readResponse", True))
        with serial.Serial(
            port=port_name,
            baudrate=int(self.connection.get("baudrate") or 9600),
            bytesize=int(self.connection.get("bytesize") or 8),
            parity=str(self.connection.get("parity") or "N"),
            stopbits=int(self.connection.get("stopbits") or 1),
            timeout=timeout,
        ) as ser:
            ser.write(parse_bytes(frame, encoding))
            if mapping.get("appendNewline", self.connection.get("appendNewline", False)):
                ser.write(b"\n")
            raw = b""
            if read_response:
                read_until = mapping.get("readUntil")
                if read_until:
                    raw = ser.read_until(parse_bytes(read_until, encoding), int(mapping.get("maxResponseBytes", 4096)))
                else:
                    raw = ser.read(int(mapping.get("maxResponseBytes", 4096)))
        return self.format_response(raw, mapping, encoding)

    def format_response(self, raw: bytes, mapping: dict[str, Any], encoding: str) -> dict[str, Any]:
        mode = str(mapping.get("responseMode", "text"))
        if mode == "hex":
            value: Any = raw.hex(" ")
        else:
            text = raw.decode(encoding, errors="replace")
            if mode == "json":
                value = json.loads(text) if text else {}
            else:
                value = text
        return {"response": value, "value": value}


class CustomPluginConnector(SouthboundConnector):
    def __init__(self, profile: dict[str, Any], runtime: "EdgeRuntime" | None = None):
        self.profile = profile
        self.runtime = runtime
        self.delegate = self.load_delegate()

    def load_delegate(self) -> Any:
        plugin = self.profile.get("plugin") or {}
        module_name = plugin.get("module") or self.profile.get("module")
        plugin_path = plugin.get("path") or self.profile.get("pluginPath")
        if plugin_path:
            spec = importlib.util.spec_from_file_location(f"smartlab_adapter_plugin_{uuid.uuid4().hex}", plugin_path)
            if spec is None or spec.loader is None:
                raise RuntimeError(f"custom connector plugin cannot be loaded: {plugin_path}")
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
        elif module_name:
            module = importlib.import_module(str(module_name))
        else:
            raise RuntimeError("custom connector requires plugin.path or plugin.module")

        factory_name = plugin.get("factory") or self.profile.get("factory")
        class_name = plugin.get("className") or self.profile.get("className") or "Connector"
        if factory_name:
            factory = getattr(module, str(factory_name))
            try:
                return factory(self.profile, self.runtime)
            except TypeError:
                return factory(self.profile)
        connector_class = getattr(module, str(class_name))
        try:
            return connector_class(self.profile, self.runtime)
        except TypeError:
            return connector_class(self.profile)

    def execute(self, command_id: str, payload: dict[str, Any], mapping: dict[str, Any]) -> dict[str, Any]:
        if hasattr(self.delegate, "execute"):
            return self.delegate.execute(command_id, payload, mapping)
        if hasattr(self.delegate, "execute_command"):
            return self.delegate.execute_command(command_id, payload, mapping)
        raise RuntimeError("custom connector must implement execute(command_id, payload, mapping)")

    def poll(self) -> dict[str, Any]:
        if hasattr(self.delegate, "poll"):
            return self.delegate.poll()
        return {}

    def close(self) -> None:
        if hasattr(self.delegate, "close"):
            self.delegate.close()


class UnsupportedProtocolConnector(SouthboundConnector):
    def __init__(self, profile: dict[str, Any], connector_type: str):
        self.profile = profile
        self.connector_type = connector_type

    def execute(self, command_id: str, payload: dict[str, Any], mapping: dict[str, Any]) -> dict[str, Any]:
        raise RuntimeError(
            f"connector '{self.connector_type}' is declared but not implemented in this Python runtime. "
            "Implement a connector/plugin or use proxy-http/process to wrap an existing device service."
        )


def build_connector(profile: dict[str, Any], runtime: "EdgeRuntime" | None = None) -> SouthboundConnector:
    connector_type = str(profile.get("connectorType", "")).strip().lower()
    if not connector_type:
        raise RuntimeError("southbound.connectorType is required; refusing to fall back to a simulated device")
    if connector_type == "direct-driver":
        next_profile = dict(profile)
        next_profile["connectorType"] = require_non_empty_string(profile.get("driverType"), "southbound.driverType")
        return build_connector(next_profile, runtime)
    if connector_type in {"proxy-http", "http"}:
        return ProxyHttpConnector(profile)
    if connector_type in {"tcp", "tcp-client"}:
        return TcpConnector(profile)
    if connector_type in {"mqtt-device", "plc-mqtt"}:
        if runtime is None:
            raise RuntimeError("mqtt-device connector requires runtime")
        return MqttDeviceConnector(profile, runtime)
    if connector_type == "process":
        return ProcessConnector(profile)
    if connector_type == "modbus-tcp":
        return ModbusConnector(profile, "tcp")
    if connector_type == "modbus-rtu":
        return ModbusConnector(profile, "rtu")
    if connector_type in {"rs485", "serial"}:
        return SerialConnector(profile)
    if connector_type in {"custom", "plugin"}:
        return CustomPluginConnector(profile, runtime)
    if connector_type == "simulated":
        return SimulatedConnector(profile)
    return UnsupportedProtocolConnector(profile, connector_type)


@dataclass
class AgentInstance:
    runtime: "EdgeRuntime"
    config: dict[str, Any]
    connector: SouthboundConnector = field(init=False)
    running: bool = field(default=True)
    poll_thread: threading.Thread | None = field(default=None)
    upload_thread: threading.Thread | None = field(default=None)
    telemetry_buffer: list[dict[str, Any]] = field(default_factory=list)
    telemetry_lock: threading.Lock = field(default_factory=threading.Lock)

    def __post_init__(self) -> None:
        validate_agent_config(self.config)
        self.agent = self.config.get("agent") or {}
        self.agent_id = str(self.config.get("agentId") or self.agent.get("agentId"))
        self.gateway_id = str(self.config.get("gatewayId") or self.agent.get("gatewayId") or self.runtime.gateway_id)
        self.adapter_name = require_non_empty_string(self.config.get("adapterName") or self.agent.get("adapterName"), "adapterName")
        self.device_point = require_non_empty_string(self.config.get("devicePoint") or self.agent.get("devicePoint"), "devicePoint")
        self.northbound = self.config.get("northbound") or self.agent.get("northboundContract") or {}
        self.southbound = self.config.get("southbound") or self.agent.get("southboundProfile") or {}
        self.data_upload = self.config.get("dataUploadContract") or self.northbound.get("dataUploadContract") or {}
        self.connector = build_connector(self.southbound, self.runtime)

    def start(self) -> None:
        command_topic = self.northbound.get("commandTopic")
        if command_topic:
            self.runtime.subscribe(str(command_topic), self.handle_command)
        polling = self.southbound.get("polling") or {}
        if polling.get("enabled"):
            self.poll_thread = threading.Thread(target=self.poll_loop, name=f"poll-{self.agent_id}", daemon=True)
            self.poll_thread.start()
        if self.data_upload.get("enabled"):
            self.upload_thread = threading.Thread(target=self.upload_loop, name=f"upload-{self.agent_id}", daemon=True)
            self.upload_thread.start()
        self.publish_status("RUNNING", {"reason": "agent_loaded"})

    def stop(self) -> None:
        self.running = False
        self.connector.close()
        self.publish_status("STOPPED", {"reason": "agent_unloaded"})

    def handle_command(self, topic: str, envelope: dict[str, Any]) -> None:
        command_name = ""
        mapping: dict[str, Any] = {}
        try:
            command_name = validate_command_envelope(envelope)
            mapping = find_mapping(self.southbound.get("commandMappings") or [], command_name)
            if not mapping:
                raise RuntimeError(f"southbound command mapping missing: {command_name}")
            event_names = mapping.get("eventNames")
            if not isinstance(event_names, dict):
                raise RuntimeError(f"command mapping eventNames is required: {command_name}")
            for phase in ("received", "running", "completed", "failed"):
                require_non_empty_string(event_names.get(phase), f"command mapping eventNames.{phase}")
            parameters = envelope["parameters"]
            self.publish_protocol_event(str(event_names["received"]), envelope, {"commandName": command_name, "topic": topic})
            self.publish_protocol_event(str(event_names["running"]), envelope, {"commandName": command_name})
            self.publish_status("EXECUTING", {"commandName": command_name, "messageId": envelope["messageId"]})
            result = self.connector.execute(command_name, parameters, mapping)
            self.publish_protocol_event(str(event_names["completed"]), envelope, {"commandName": command_name, "result": result})
            self.publish_status("RUNNING", {"lastCommand": command_name, "messageId": envelope["messageId"]})
        except Exception as exc:
            event_names = mapping.get("eventNames") if isinstance(mapping.get("eventNames"), dict) else {}
            failed_event = str(event_names.get("failed") or "").strip()
            if failed_event and isinstance(envelope, dict) and envelope.get("messageId"):
                self.publish_protocol_event(failed_event, envelope, {"commandName": command_name, "message": str(exc)})
            self.publish_status("ERROR", {"commandName": command_name, "message": str(exc)})

    def poll_loop(self) -> None:
        polling = self.southbound.get("polling") or {}
        interval = max(float(polling.get("intervalMs", 1000)) / 1000, 0.1)
        while self.running and self.runtime.running:
            try:
                telemetry = self.connector.poll()
                if telemetry:
                    self.publish_telemetry(telemetry)
            except Exception as exc:
                self.publish_status("ERROR", {"pollError": str(exc)})
            time.sleep(interval)

    def publish_status(self, state: str, payload: dict[str, Any]) -> None:
        topic = self.northbound.get("statusTopic") or self.runtime.agent_status_topic(self.agent_id)
        self.runtime.publish(topic, {
            "specVersion": "smartlab.adapter.v1",
            "messageType": "STATUS",
            "messageId": str(uuid.uuid4()),
            "tenantId": self.runtime.tenant_id,
            "labId": self.runtime.lab_id,
            "gatewayId": self.gateway_id,
            "agentId": self.agent_id,
            "status": {"agentState": state, "operationState": state, "online": self.running},
            "payload": payload,
            "timestamp": utc_now(),
        }, qos=1)

    def publish_protocol_event(self, event_name: str, command: dict[str, Any], payload: dict[str, Any]) -> None:
        self.runtime.publish(str(self.northbound["eventTopic"]), {
            "timestamp": epoch_millis(),
            "messageId": str(command["messageId"]),
            "adapterName": str(command["adapterName"]),
            "devicePoint": str(command["devicePoint"]),
            "eventName": event_name,
            "payload": payload,
        }, qos=1)

    def publish_event(self, event_id: str, trace_id: str, correlation_id: str, payload: dict[str, Any]) -> None:
        topic = self.northbound.get("eventTopic") or self.runtime.agent_event_topic(self.agent_id)
        self.runtime.publish(topic, {
            "specVersion": "smartlab.adapter.v1",
            "messageType": "EVENT",
            "messageId": str(uuid.uuid4()),
            "eventId": event_id,
            "traceId": trace_id,
            "correlationId": correlation_id,
            "tenantId": self.runtime.tenant_id,
            "labId": self.runtime.lab_id,
            "gatewayId": self.gateway_id,
            "agentId": self.agent_id,
            "payload": payload,
            "timestamp": utc_now(),
        }, qos=1)

    def publish_telemetry(self, data: dict[str, Any]) -> None:
        topic = self.northbound.get("telemetryTopic") or self.runtime.agent_event_topic(self.agent_id)
        record = self.build_ingestion_record(data)
        self.buffer_telemetry_record(record)
        self.runtime.publish(topic, {
            "specVersion": "smartlab.adapter.v1",
            "messageType": "TELEMETRY",
            "messageId": str(uuid.uuid4()),
            "tenantId": self.runtime.tenant_id,
            "labId": self.runtime.lab_id,
            "gatewayId": self.gateway_id,
            "agentId": self.agent_id,
            "data": data,
            "payload": data,
            "timestamp": utc_now(),
        }, qos=0)

    def build_ingestion_record(self, data: dict[str, Any]) -> dict[str, Any]:
        message_id = str(uuid.uuid4())
        field_mapping = self.data_upload.get("fieldMapping") or {}
        payload = {}
        if isinstance(field_mapping, dict) and field_mapping:
            for target_field, source_field in field_mapping.items():
                source = str(source_field)
                if source in data:
                    payload[str(target_field)] = data.get(source)
        else:
            payload = dict(data)
        device_instance_id = self.data_upload.get("deviceInstanceId") or self.agent.get("deviceInstanceId")
        device_sn = self.data_upload.get("deviceSn") or self.agent.get("deviceSn")
        return {
            "messageId": message_id,
            "idempotencyKey": f"{self.gateway_id}:{self.agent_id}:{message_id}",
            "deviceInstanceId": device_instance_id,
            "deviceSn": device_sn,
            "templateRef": self.data_upload.get("templateRef"),
            "taskId": self.data_upload.get("taskId"),
            "nodeId": self.data_upload.get("nodeId"),
            "data": payload,
            "telemetry": data,
            "collectTime": utc_now(),
            "context": {
                "gatewayId": self.gateway_id,
                "agentId": self.agent_id,
            },
        }

    def buffer_telemetry_record(self, record: dict[str, Any]) -> None:
        if not self.data_upload.get("enabled"):
            return
        if not record.get("templateRef") or not record.get("deviceInstanceId"):
            return
        with self.telemetry_lock:
            self.telemetry_buffer.append(record)
            max_buffer = int(self.data_upload.get("maxBufferSize", 10000))
            if len(self.telemetry_buffer) > max_buffer:
                self.telemetry_buffer = self.telemetry_buffer[-max_buffer:]
            batch_size = int(self.data_upload.get("batchSize", 100))
            should_flush = len(self.telemetry_buffer) >= max(batch_size, 1)
        if should_flush:
            self.flush_telemetry()

    def upload_loop(self) -> None:
        interval = max(float(self.data_upload.get("flushIntervalMs", 5000)) / 1000, 0.2)
        while self.running and self.runtime.running:
            time.sleep(interval)
            self.flush_telemetry()

    def flush_telemetry(self) -> None:
        endpoint = str(self.data_upload.get("endpoint") or "").strip()
        if not endpoint:
            return
        batch_size = max(int(self.data_upload.get("batchSize", 100)), 1)
        with self.telemetry_lock:
            if not self.telemetry_buffer:
                return
            records = self.telemetry_buffer[:batch_size]
        batch_id = str(uuid.uuid4())
        body = {
            "specVersion": "smartlab.ingestion.v1",
            "messageType": "DATA_BATCH",
            "messageId": batch_id,
            "traceId": str(uuid.uuid4()),
            "tenantId": self.runtime.tenant_id,
            "labId": self.runtime.lab_id,
            "gatewayId": self.gateway_id,
            "agentId": self.agent_id,
            "deviceInstanceId": self.data_upload.get("deviceInstanceId") or self.agent.get("deviceInstanceId"),
            "deviceSn": self.data_upload.get("deviceSn") or self.agent.get("deviceSn"),
            "uploadContractId": self.data_upload.get("contractId") or self.data_upload.get("templateRef"),
            "timestamp": utc_now(),
            "records": records,
            "context": {
                "batchId": batch_id,
                "contractVersion": self.data_upload.get("contractVersion", "smartlab.ingestion.v1"),
            },
        }
        try:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            req = http_request.Request(endpoint, data=data, method="POST", headers={"Content-Type": "application/json"})
            token = self.data_upload.get("token")
            if token:
                req.add_header("Authorization", f"Bearer {token}")
            with http_request.urlopen(req, timeout=float(self.data_upload.get("timeoutSec", 10))) as resp:
                if not (200 <= resp.status < 300):
                    raise RuntimeError(f"ingestion endpoint returned {resp.status}")
                response_text = resp.read().decode("utf-8", errors="replace")
                response_body = json.loads(response_text) if response_text else {}
            if isinstance(response_body, dict) and response_body.get("success") is False:
                raise RuntimeError(response_body.get("message") or "ingestion endpoint rejected batch")
            result = response_body.get("data") if isinstance(response_body, dict) else {}
            if isinstance(result, dict) and int(result.get("rejected") or 0) > 0:
                raise RuntimeError(f"ingestion rejected {result.get('rejected')} records: {result.get('errors')}")
            with self.telemetry_lock:
                sent_keys = {item.get("idempotencyKey") for item in records}
                self.telemetry_buffer = [item for item in self.telemetry_buffer if item.get("idempotencyKey") not in sent_keys]
            self.publish_event("data_uploaded", str(uuid.uuid4()), batch_id, {"batchId": batch_id, "count": len(records)})
        except Exception as exc:
            self.publish_status("UPLOAD_BACKLOG", {"error": str(exc), "bufferSize": len(self.telemetry_buffer)})


class EdgeRuntime:
    def __init__(self, config: dict[str, Any]):
        if mqtt is None:
            raise RuntimeError("paho-mqtt is required. Install with: pip install paho-mqtt")
        self.config = config
        self.tenant_id = str(config.get("tenantId", "default"))
        self.lab_id = str(config.get("labId", "lab1"))
        self.gateway_id = str(config.get("gatewayId", "gateway_lab1_01"))
        self.runtime_version = str(config.get("runtimeVersion", "edge-runtime-python-0.1.0"))
        broker = config.get("mqtt", {}).get("broker", "tcp://127.0.0.1:1883")
        self.host, self.port = self.parse_broker(str(broker))
        self.client = mqtt.Client(client_id=config.get("mqtt", {}).get("clientId", f"edge-{self.gateway_id}-{uuid.uuid4().hex[:6]}"))
        username = config.get("mqtt", {}).get("username")
        password = config.get("mqtt", {}).get("password")
        if username:
            self.client.username_pw_set(username, password)
        self.client.on_connect = self.on_connect
        self.client.on_message = self.on_message
        self.subscriptions: list[tuple[str, Callable[[str, dict[str, Any]], None]]] = []
        self.agents: dict[str, AgentInstance] = {}
        self.running = True
        self.heartbeat_thread = threading.Thread(target=self.heartbeat_loop, name="runtime-heartbeat", daemon=True)

    def parse_broker(self, broker: str) -> tuple[str, int]:
        value = broker.replace("tcp://", "").replace("mqtt://", "")
        if ":" in value:
            host, port = value.rsplit(":", 1)
            return host, int(port)
        return value, 1883

    def start(self) -> None:
        self.client.connect(self.host, self.port, keepalive=30)
        self.client.loop_start()
        self.heartbeat_thread.start()
        for profile in self.config.get("agents", []):
            self.load_agent(profile)
        while self.running:
            time.sleep(0.2)

    def stop(self) -> None:
        self.running = False
        for agent in list(self.agents.values()):
            agent.stop()
        self.client.loop_stop()
        self.client.disconnect()

    def on_connect(self, client: Any, userdata: Any, flags: Any, rc: int) -> None:
        self.subscribe(self.config_topic(), self.handle_config)
        self.subscribe(self.control_topic(), self.handle_control)
        for topic, _ in self.subscriptions:
            client.subscribe(topic, qos=1)
        self.publish_heartbeat()

    def on_message(self, client: Any, userdata: Any, msg: Any) -> None:
        try:
            envelope = json.loads(msg.payload.decode("utf-8"))
        except Exception:
            envelope = {"raw": msg.payload.decode("utf-8", errors="replace")}
        topic = msg.topic
        for pattern, handler in list(self.subscriptions):
            if mqtt.topic_matches_sub(pattern, topic):
                handler(topic, envelope)

    def subscribe(self, topic: str, handler: Callable[[str, dict[str, Any]], None]) -> None:
        if not any(existing == topic and existing_handler == handler for existing, existing_handler in self.subscriptions):
            self.subscriptions.append((topic, handler))
        self.client.subscribe(topic, qos=1)

    def publish(self, topic: str, payload: dict[str, Any], qos: int = 1, retain: bool = False) -> None:
        self.client.publish(topic, json.dumps(payload, ensure_ascii=False), qos=qos, retain=retain)

    def load_agent(self, config: dict[str, Any]) -> None:
        agent_id = str(config.get("agentId") or (config.get("agent") or {}).get("agentId"))
        if not agent_id:
            raise RuntimeError("agent profile missing agentId")
        if agent_id in self.agents:
            self.agents[agent_id].stop()
        agent = AgentInstance(self, config)
        self.agents[agent_id] = agent
        agent.start()

    def handle_config(self, topic: str, envelope: dict[str, Any]) -> None:
        agent_id = str(envelope.get("agentId") or (envelope.get("agent") or {}).get("agentId"))
        try:
            self.load_agent(envelope)
            self.publish(self.agent_status_topic(agent_id), {
                "specVersion": "smartlab.edge.v1",
                "messageType": "CONFIG_APPLIED",
                "gatewayId": self.gateway_id,
                "agentId": agent_id,
                "versionNo": envelope.get("versionNo"),
                "timestamp": utc_now(),
            }, qos=1)
        except Exception as exc:
            self.publish(self.agent_event_topic(agent_id or "unknown"), {
                "specVersion": "smartlab.edge.v1",
                "messageType": "CONFIG_FAILED",
                "gatewayId": self.gateway_id,
                "agentId": agent_id,
                "error": str(exc),
                "timestamp": utc_now(),
            }, qos=1)

    def handle_control(self, topic: str, envelope: dict[str, Any]) -> None:
        action = envelope.get("action")
        agent_id = str(envelope.get("agentId", ""))
        if action == "stop-agent" and agent_id in self.agents:
            self.agents[agent_id].stop()
            del self.agents[agent_id]

    def heartbeat_loop(self) -> None:
        interval = int(self.config.get("heartbeatIntervalSec", 10))
        while self.running:
            self.publish_heartbeat()
            time.sleep(max(interval, 1))

    def publish_heartbeat(self) -> None:
        self.publish(self.heartbeat_topic(), {
            "specVersion": "smartlab.edge.v1",
            "messageType": "HEARTBEAT",
            "gatewayId": self.gateway_id,
            "gatewayName": self.config.get("gatewayName", self.gateway_id),
            "tenantId": self.tenant_id,
            "labId": self.lab_id,
            "runtimeVersion": self.runtime_version,
            "hostAddress": self.config.get("hostAddress", ""),
            "status": "ONLINE",
            "capabilities": {
                "agentModes": ["CONFIG_DRIVEN", "CODE_DRIVEN", "PROXY"],
                "connectors": [
                    "simulated",
                    "proxy-http",
                    "tcp-client",
                    "mqtt-device",
                    "plc-mqtt",
                    "process",
                    "direct-driver",
                    "modbus-tcp",
                    "modbus-rtu",
                    "serial",
                    "rs485",
                    "custom",
                ],
            },
            "agents": list(self.agents.keys()),
            "timestamp": utc_now(),
        }, qos=0)

    def config_topic(self) -> str:
        return f"smartlab/v1/{self.tenant_id}/{self.lab_id}/gateway/{self.gateway_id}/config/+"

    def control_topic(self) -> str:
        return f"smartlab/v1/{self.tenant_id}/{self.lab_id}/gateway/{self.gateway_id}/control"

    def heartbeat_topic(self) -> str:
        return f"smartlab/v1/{self.tenant_id}/{self.lab_id}/gateway/{self.gateway_id}/heartbeat"

    def agent_status_topic(self, agent_id: str) -> str:
        return f"smartlab/v1/{self.tenant_id}/{self.lab_id}/gateway/{self.gateway_id}/agent/{agent_id}/status"

    def agent_event_topic(self, agent_id: str) -> str:
        return f"smartlab/v1/{self.tenant_id}/{self.lab_id}/gateway/{self.gateway_id}/agent/{agent_id}/event"


def main() -> int:
    parser = argparse.ArgumentParser(description="SmartLab Edge Agent Runtime")
    parser.add_argument("-c", "--config", default=os.environ.get("SMARTLAB_EDGE_CONFIG", "runtime.config.json"))
    args = parser.parse_args()
    runtime = EdgeRuntime(load_json(args.config))

    def shutdown(signum: int, frame: Any) -> None:
        runtime.stop()

    signal.signal(signal.SIGINT, shutdown)
    signal.signal(signal.SIGTERM, shutdown)
    try:
        runtime.start()
    finally:
        runtime.stop()
    return 0


if __name__ == "__main__":
    sys.exit(main())
