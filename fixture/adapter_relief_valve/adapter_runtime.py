"""
Fixture relief valve adapter runtime: MQTT registration, heartbeat, telemetry,
event publishing, and command subscription.
"""

import json
import logging
import time
import threading
import uuid
from configparser import ConfigParser
from dataclasses import dataclass

logger = logging.getLogger("fixture.adapter.relief_valve.runtime")

try:
    import paho.mqtt.client as mqtt
    HAS_MQTT = True
except ImportError:
    HAS_MQTT = False
    logger.warning("paho-mqtt not installed; MQTT features disabled")


@dataclass
class RuntimeConfig:
    broker: str = "127.0.0.1"
    port: int = 1883
    username: str = ""
    password: str = ""
    client_id: str = "fixture_relief_valve_adapter"
    keepalive: int = 30
    qos: int = 1

    @classmethod
    def load(cls, path):
        cp = ConfigParser()
        cp.read(path, encoding="utf-8")
        sl = cp["smartlab"]
        return cls(
            broker=sl.get("broker", "127.0.0.1"),
            port=sl.getint("port", 1883),
            username=sl.get("username", ""),
            password=sl.get("password", ""),
            client_id=sl.get("clientId") or f"fixture_relief_valve_adapter_{uuid.uuid4().hex[:8]}",
            keepalive=sl.getint("keepAliveSec", 30),
            qos=sl.getint("qos", 1),
        )


@dataclass
class AdapterSetup:
    adapter_name: str = ""
    spec_version: str = ""
    raw_config: str = ""

    @classmethod
    def load(cls, path):
        with open(path, encoding="utf-8") as f:
            raw = f.read()
        cp = ConfigParser()
        cp.read_string(raw)
        adapter = cp["adapter"]
        return cls(
            adapter_name=adapter.get("adapterName", ""),
            spec_version=adapter.get("specVersion", ""),
            raw_config=raw,
        )


class AdapterRuntime:
    """Manages MQTT registration, heartbeat, telemetry, events, and command dispatch for Relief Valve."""

    def __init__(self, config: RuntimeConfig, setup: AdapterSetup, core):
        self.config = config
        self.setup = setup
        self.core = core
        self._client = None
        self._connected = False
        self._stop_event = threading.Event()

        # Wire core callbacks
        core.on_telemetry(self._publish_telemetry)
        core.on_event(self._publish_event)

    def start(self):
        if not HAS_MQTT:
            logger.warning("MQTT disabled (paho-mqtt missing). Running in telemetry-only mode.")
            self.core.connect()
            self.core.start_reading()
            return

        self._client = mqtt.Client(client_id=self.config.client_id)
        if self.config.username:
            self._client.username_pw_set(self.config.username, self.config.password)
        self._client.on_connect = self._on_connect
        self._client.on_disconnect = self._on_disconnect
        self._client.on_message = self._on_message

        self._client.connect(self.config.broker, self.config.port, self.config.keepalive)
        self._client.loop_start()
        time.sleep(1)

        self._register()
        self.core.connect()
        self.core.start_reading()

        # Subscribe AFTER connection is stable
        time.sleep(0.5)
        if self._connected:
            topic = f"smartlab/adapter/{self.setup.adapter_name}/+/command"
            self._client.subscribe(topic, qos=self.config.qos)
            logger.info("Subscribed to %s", topic)

        # Start heartbeat
        threading.Thread(target=self._heartbeat_loop, daemon=True).start()

    def stop(self):
        self._stop_event.set()
        self.core.stop()
        if self._client:
            self._client.loop_stop()
            self._client.disconnect()

    def _on_connect(self, client, userdata, flags, rc):
        if rc == 0:
            self._connected = True
            logger.info("MQTT connected to %s:%d", self.config.broker, self.config.port)
        else:
            logger.error("MQTT connection failed: %d", rc)

    def _on_disconnect(self, client, userdata, rc):
        self._connected = False
        if rc != 0:
            logger.warning("MQTT disconnected unexpectedly (rc=%d), will reconnect", rc)
        else:
            logger.info("MQTT disconnected cleanly")

    def _register(self):
        topic = "smartlab/adapter/register"
        payload = {
            "adapterName": self.setup.adapter_name,
            "specVersion": self.setup.spec_version,
            "rawConfigFormat": "INI",
            "rawConfigContent": self.setup.raw_config,
            "timestamp": int(time.time() * 1000)
        }
        self._client.publish(topic, json.dumps(payload), qos=self.config.qos)
        logger.info("Registration sent to %s", topic)

    def _heartbeat_loop(self):
        topic = f"smartlab/adapter/{self.setup.adapter_name}/heartbeat"
        while not self._stop_event.wait(10):
            if self._connected:
                self._client.publish(topic, json.dumps({"status": "ALIVE", "timestamp": int(time.time() * 1000)}), qos=1)

    def _publish_telemetry(self, attributes: dict):
        if not self._connected:
            return
        device_point = "Valve1"
        topic = f"smartlab/adapter/{self.setup.adapter_name}/{device_point}/telemetry"
        payload = {
            "adapterName": self.setup.adapter_name,
            "devicePoint": device_point,
            "telemetryData": attributes,
            "timestamp": int(time.time() * 1000)
        }
        self._client.publish(topic, json.dumps(payload), qos=self.config.qos)

    def _publish_event(self, event_name: str, message_id: str = ""):
        if not self._connected:
            return
        device_point = "Valve1"
        topic = f"smartlab/adapter/{self.setup.adapter_name}/{device_point}/event"
        payload = {
            "adapterName": self.setup.adapter_name,
            "devicePoint": device_point,
            "eventName": event_name,
            "messageId": message_id,
            "timestamp": int(time.time() * 1000)
        }
        self._client.publish(topic, json.dumps(payload), qos=self.config.qos)

    def _on_message(self, client, userdata, msg):
        try:
            data = json.loads(msg.payload.decode())
            cmd = data.get("commandName", "")
            params = data.get("parameters", {})
            mid = data.get("messageId", str(uuid.uuid4()))

            logger.info("Received command: %s params=%s msgId=%s", cmd, params, mid)

            if cmd in ("releasePressure", "relieve"):
                target = float(params.get("targetPressure", 0.20))
                duration = int(params.get("releaseDurationSec", 4))
                threading.Thread(target=self.core.execute_release, args=(target, duration, mid), daemon=True).start()
            elif cmd in ("adjustPressure", "pressurize"):
                target = float(params.get("targetPressure", 0.40))
                duration = int(params.get("durationSec", 4))
                threading.Thread(target=self.core.execute_adjust, args=(target, duration, mid), daemon=True).start()
            elif cmd in ("emergencyVent", "emergency_vent"):
                duration = int(params.get("durationSec", 3))
                threading.Thread(target=self.core.execute_emergency_vent, args=(duration, mid), daemon=True).start()
            else:
                logger.warning("Unknown command for relief valve: %s", cmd)
        except Exception as e:
            logger.error("Command handling error: %s", e)
