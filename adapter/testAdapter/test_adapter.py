import json
import os
import signal
import threading
import time
import uuid
from pathlib import Path

try:
    import paho.mqtt.client as mqtt
except ImportError as exc:
    raise SystemExit("Missing dependency: pip install -r requirements.txt") from exc

BASE_DIR = Path(__file__).resolve().parent
MANIFEST_PATH = BASE_DIR / "adapter-manifest.json"
MANIFEST = json.loads(MANIFEST_PATH.read_text(encoding="utf-8-sig"))
ADAPTER_NAME = MANIFEST["adapterName"]

BROKER_HOST = os.getenv("SMARTLAB_MQTT_HOST", "127.0.0.1")
BROKER_PORT = int(os.getenv("SMARTLAB_MQTT_PORT", "1883"))
MQTT_USERNAME = os.getenv("SMARTLAB_MQTT_USERNAME", "lab_system")
MQTT_PASSWORD = os.getenv("SMARTLAB_MQTT_PASSWORD", "123456")
CLIENT_ID = os.getenv("SMARTLAB_MQTT_CLIENT_ID", f"{ADAPTER_NAME}-{uuid.uuid4().hex[:8]}")

REGISTER_TOPIC = "smartlab/adapter/register"
HEARTBEAT_TOPIC = f"smartlab/adapter/{ADAPTER_NAME}/heartbeat"
COMMAND_TOPIC = f"smartlab/adapter/{ADAPTER_NAME}/+/command"

running = threading.Event()
running.set()
state_lock = threading.Lock()
telemetry_state = {
    point["devicePoint"]: {"temperature": 25.0, "pressure": 1.0}
    for point in MANIFEST.get("devicePoints", [])
}


def now_ms():
    return int(time.time() * 1000)


def find_template(template_name):
    for template in MANIFEST.get("deviceTemplates", []):
        if template.get("templateName") == template_name:
            return template
    return None


def find_device_point(device_point):
    for point in MANIFEST.get("devicePoints", []):
        if point.get("devicePoint") == device_point:
            return point
    return None


def find_command(template, command_name):
    for command in template.get("commands", []):
        if command.get("name") == command_name:
            return command
    return None


def topic(kind, device_point):
    return f"smartlab/adapter/{ADAPTER_NAME}/{device_point}/{kind}"


def publish_json(client, mqtt_topic, payload):
    text = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    result = client.publish(mqtt_topic, text, qos=1)
    result.wait_for_publish(timeout=5)
    if result.rc != mqtt.MQTT_ERR_SUCCESS:
        raise RuntimeError(f"publish failed topic={mqtt_topic} rc={result.rc}")


def publish_register(client):
    payload = {
        "adapterName": ADAPTER_NAME,
        "rawConfigFormat": MANIFEST.get("rawConfigFormat", "JSON"),
        "rawConfigContent": json.dumps(MANIFEST, ensure_ascii=False),
        "timestamp": now_ms(),
    }
    publish_json(client, REGISTER_TOPIC, payload)
    print(f"registered {ADAPTER_NAME}")


def publish_heartbeat(client):
    publish_json(client, HEARTBEAT_TOPIC, {"status": "ALIVE", "timestamp": now_ms()})


def publish_event(client, device_point, event_name, payload=None):
    publish_json(
        client,
        topic("event", device_point),
        {
            "timestamp": now_ms(),
            "adapterName": ADAPTER_NAME,
            "devicePoint": device_point,
            "eventName": event_name,
            "payload": payload or {},
        },
    )


def publish_telemetry(client, device_point):
    with state_lock:
        data = dict(telemetry_state.setdefault(device_point, {"temperature": 25.0, "pressure": 1.0}))
    publish_json(
        client,
        topic("telemetry", device_point),
        {
            "timestamp": now_ms(),
            "adapterName": ADAPTER_NAME,
            "devicePoint": device_point,
            "data": data,
        },
    )


def build_internal_parameters(device_point_config, command_def, parameters):
    internal = {}
    for param in command_def.get("parameters", []):
        name = param.get("name")
        if not name:
            continue
        if param.get("internal"):
            source_field = param.get("sourceField")
            if source_field not in device_point_config:
                raise ValueError(f"internal parameter {name} sourceField={source_field} not found on devicePoint")
            internal[name] = device_point_config[source_field]
        else:
            if name not in parameters:
                raise ValueError(f"missing command parameter: {name}")
            internal[name] = parameters[name]
    return internal


def handle_command(client, device_point, message):
    command_name = message.get("commandName")
    message_id = message.get("messageId") or uuid.uuid4().hex
    parameters = message.get("parameters") or {}

    point_config = find_device_point(device_point)
    if not point_config:
        raise ValueError(f"unknown devicePoint: {device_point}")
    template = find_template(point_config.get("templateName"))
    if not template:
        raise ValueError(f"unknown template: {point_config.get('templateName')}")
    command_def = find_command(template, command_name)
    if not command_def:
        raise ValueError(f"unknown command: {command_name}")

    internal_parameters = build_internal_parameters(point_config, command_def, parameters)
    event_payload = {
        "messageId": message_id,
        "commandName": command_name,
        "parameters": internal_parameters,
    }

    publish_event(client, device_point, "COMMAND_RECEIVED", event_payload)
    time.sleep(0.2)
    publish_event(client, device_point, "COMMAND_RUNNING", event_payload)

    if command_name == "heat":
        publish_event(client, device_point, "HEAT_STARTED", event_payload)
        target = float(internal_parameters["targetTemperature"])
        time.sleep(min(float(internal_parameters.get("durationSec", 1)), 2.0))
        with state_lock:
            telemetry_state.setdefault(device_point, {})["temperature"] = target
        publish_telemetry(client, device_point)
        publish_event(client, device_point, "TARGET_TEMPERATURE_REACHED", {**event_payload, "temperature": target})
    elif command_name == "pressurize":
        publish_event(client, device_point, "PRESSURIZE_STARTED", event_payload)
        target = float(internal_parameters["targetPressure"])
        time.sleep(min(float(internal_parameters.get("durationSec", 1)), 2.0))
        with state_lock:
            telemetry_state.setdefault(device_point, {})["pressure"] = target
        publish_telemetry(client, device_point)
        if target > 5:
            publish_event(client, device_point, "PRESSURE_ALARM", {**event_payload, "pressure": target})
        else:
            publish_event(client, device_point, "TARGET_PRESSURE_REACHED", {**event_payload, "pressure": target})

    publish_event(client, device_point, "COMMAND_COMPLETED", event_payload)
    print(f"handled command {command_name} for {device_point}: {internal_parameters}")


def on_connect(client, _userdata, _flags, rc):
    if rc != 0:
        print(f"MQTT connect failed rc={rc}")
        return
    print(f"connected mqtt {BROKER_HOST}:{BROKER_PORT} as {CLIENT_ID}")
    client.subscribe(COMMAND_TOPIC, qos=1)
    publish_register(client)


def on_message(client, _userdata, msg):
    try:
        parts = msg.topic.split("/")
        device_point = parts[3] if len(parts) == 5 else ""
        payload = json.loads(msg.payload.decode("utf-8"))
        handle_command(client, device_point, payload)
    except Exception as exc:
        print(f"command handling failed: {exc}")
        try:
            if "device_point" in locals() and device_point:
                publish_event(client, device_point, "COMMAND_FAILED", {"errorMessage": str(exc)})
        except Exception as publish_exc:
            print(f"failed to publish COMMAND_FAILED: {publish_exc}")


def heartbeat_loop(client):
    while running.is_set():
        try:
            publish_heartbeat(client)
        except Exception as exc:
            print(f"heartbeat failed: {exc}")
        running.wait(10)


def stop(_signum=None, _frame=None):
    running.clear()


def main():
    signal.signal(signal.SIGINT, stop)
    signal.signal(signal.SIGTERM, stop)

    client = mqtt.Client(client_id=CLIENT_ID, clean_session=True)
    if MQTT_USERNAME:
        client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD)
    client.on_connect = on_connect
    client.on_message = on_message
    client.reconnect_delay_set(min_delay=2, max_delay=30)
    client.connect(BROKER_HOST, BROKER_PORT, keepalive=30)
    client.loop_start()

    thread = threading.Thread(target=heartbeat_loop, args=(client,), daemon=True)
    thread.start()
    while running.is_set():
        running.wait(0.5)
    client.loop_stop()
    client.disconnect()


if __name__ == "__main__":
    main()
