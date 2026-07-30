# PLC Background Adapter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a headless, long-running PLC Adapter launched by `python main.py`, with independent SmartLab and PLC MQTT clients.

**Architecture:** `main.py` composes one `Core` and one `AdapterRuntime` in a single process. `core.py` owns all PLC MQTT, register semantics, state changes, command confirmation and timeout behavior; `adapterRuntime.py` owns SmartLab MQTT, registration, heartbeat, protocol envelopes and topic routing.

**Tech Stack:** Python 3.11+, standard-library `configparser`/`dataclasses`/`logging`/`threading`, `paho-mqtt`.

## Global Constraints

- Do not modify the six Schema files, database structure, backend protocol, `PLCcodesys/`, vendor `plc_controller.py`, or the vendor PDF.
- Keep the Adapter headless: `python main.py` remains attached to the console and runs until Ctrl+C or a termination signal.
- Use separate SmartLab and PLC MQTT clients, Client IDs and connection settings.
- Upload only the complete `adapterSetup.ini` text; never upload `runtime.ini`.
- Expose only `temperature` as telemetry; `MW10`, `MW20`, `MW21` and `MW22` remain internal state.
- Do not create a `tests/` directory or automated test files, per explicit user instruction.
- Preserve unrelated dirty-worktree changes and stage only files named by the current task.

## File Map

- Create `adapter/testAdapter/core.py`: headless PLC client, register state, semantic commands, confirmations, timeouts and OP events.
- Create `adapter/testAdapter/adapterRuntime.py`: configuration models, SmartLab client, registration, heartbeat, command routing and Core callbacks.
- Create `adapter/testAdapter/main.py`: thin composition and signal-aware process entry point.
- Create `adapter/testAdapter/runtime.ini`: local SmartLab/PLC deployment configuration.
- Create `adapter/testAdapter/adapterSetup.ini`: public flat INI capability contract uploaded during registration.
- Create `adapter/testAdapter/requirements.txt`: `paho-mqtt` dependency.

---

### Task 1: Implement the headless PLC Core

**Files:**
- Create: `adapter/testAdapter/core.py`
- Create: `adapter/testAdapter/runtime.ini`

**Interfaces:**
- Produces: `Core(plc_config: Mapping[str, Any], device_point: str, command_timeout_sec: float)`
- Produces: `Core.set_handlers(telemetry_handler, event_handler) -> None`
- Produces: `Core.start() -> None`, `Core.stop() -> None`, `Core.is_connected() -> bool`
- Produces: `Core.execute_command(message_id: str, device_point: str, command_name: str, parameters: Mapping[str, Any]) -> None`
- Produces: `Core.expire_commands(now_ms: int | None = None) -> None`
- Calls telemetry handler as `(device_point: str, telemetry_data: dict[str, Any], timestamp_ms: int)`.
- Calls event handler as `(device_point: str, event_name: str, message_id: str | None, payload: dict[str, Any], timestamp_ms: int)`.

- [ ] **Step 1: Define typed internal state**

Add `PendingCommand` with `message_id`, `command_name`, `expected_registers` and `deadline_ms`.
Add a thread-safe `Core` state containing the MQTT client, connection flag, merged register map,
baseline flag, pending-command map and bounded completed-result map.

- [ ] **Step 2: Port the vendor PLC MQTT transport without GUI**

Use the vendor program's MQTT V2 callbacks and wire format:

```python
payload = json.dumps(
    [{"DeviceSN": self.device_sn, "TagData": [registers]}],
    separators=(",", ":"),
)
```

On connect, subscribe to `dataTopic` at configured QoS. On message, accept a top-level list or
single object, select the configured `DeviceSN`, take the final `TagData` row, validate numeric
`MW0/MW10/MW20/MW21/MW22`, and merge only fields present in the update.

- [ ] **Step 3: Emit temperature telemetry and state-change events**

For every valid snapshot with known `MW0`, emit:

```python
{"temperature": float(registers["MW0"]) / 100.0}
```

The first valid state snapshot establishes the baseline and emits no OP event. Later changes emit:

```text
MW21 false→true  AUTOMATIC_MODE_ENTERED
MW22 false→true  MANUAL_MODE_ENTERED
MW20 false→true  COOLING_STARTED
MW20 true→false  COOLING_STOPPED
MW10 false→true  ALARM_TRIGGERED
MW10 true→false  ALARM_CLEARED
```

- [ ] **Step 4: Implement semantic commands**

Map commands exactly:

```text
setOperatingMode(mode="AUTO")   -> {"MW21": 1, "MW22": 0}
setOperatingMode(mode="MANUAL") -> {"MW21": 0, "MW22": 1}
setCooling(enabled=True)        -> {"MW20": 1}
setCooling(enabled=False)       -> {"MW20": 0}
setAlarm(enabled=True)          -> {"MW10": 1}
setAlarm(enabled=False)         -> {"MW10": 0}
```

Require exact parameter types. Reject `setCooling(True)` until at least one PLC snapshot exists
and `MW22 & 1 == 1`; always allow `setCooling(False)`.

Emit `COMMAND_RECEIVED` after validation, `COMMAND_RUNNING` after MQTT accepts the publish,
`COMMAND_FAILED` on validation/connection/publish failure, and create one pending confirmation
for the expected register values.

- [ ] **Step 5: Implement confirmation, timeout and idempotency**

After each register update, complete every pending command whose expected values match and emit
`COMMAND_COMPLETED`. `expire_commands()` emits `COMMAND_TIMEOUT` after the configured deadline.
Repeated active `messageId` values do not publish again. Repeated completed IDs replay their final
event without writing the PLC.

- [ ] **Step 6: Add local runtime configuration**

Create `runtime.ini` with `[smartlab]`, `[plc]`, `[runtime]` and `[logging]`. Use current local
defaults:

```ini
[smartlab]
broker = 192.168.11.178
port = 1883
username = plc
password = 123456
clientId = smartlab_plc_adapter
keepAliveSec = 30
qos = 1

[plc]
broker = 192.168.11.178
port = 1883
username = plc
password = 123456
clientId = smartlab_plc_core
keepAliveSec = 30
qos = 1
dataTopic = plc/0001/data
commandTopic = plc/0001/command
deviceSN = plc0001

[runtime]
heartbeatIntervalSec = 10
commandTimeoutSec = 15

[logging]
level = INFO
```

- [ ] **Step 7: Verify and commit Task 1**

Run:

```powershell
python -m py_compile adapter/testAdapter/core.py
```

Expected: exit code `0`; no GUI opens and no file outside Task 1 changes.

Commit only `core.py` and `runtime.ini`:

```powershell
git add -- adapter/testAdapter/core.py adapter/testAdapter/runtime.ini
git commit -m "feat: add headless PLC adapter core"
```

---

### Task 2: Implement the SmartLab Adapter Runtime and public setup

**Files:**
- Create: `adapter/testAdapter/adapterRuntime.py`
- Create: `adapter/testAdapter/adapterSetup.ini`

**Interfaces:**
- Consumes: the `Core` interface from Task 1.
- Produces: `RuntimeConfig.load(path: Path) -> RuntimeConfig`
- Produces: `AdapterSetup.load(path: Path) -> AdapterSetup`
- Produces: `AdapterRuntime(config: RuntimeConfig, setup: AdapterSetup, core: Core)`
- Produces: `AdapterRuntime.start() -> None`, `stop() -> None`, `run_forever() -> None`

- [ ] **Step 1: Parse and validate both configuration boundaries**

`RuntimeConfig.load()` requires all four local sections and typed positive port/interval/QoS values.
`AdapterSetup.load()` reads UTF-8 text, requires `[adapter]`, obtains `adapterName`, requires
`rawConfigFormat=INI`, and extracts device points from `[devicePoints.<name>]` sections while
excluding `.attributeMapping` sections. Keep the exact raw text for registration.

- [ ] **Step 2: Define SmartLab topics and protocol envelopes**

Generate topics only from the current fixed contract:

```text
smartlab/adapter/register
smartlab/adapter/{adapterName}/heartbeat
smartlab/adapter/{adapterName}/{devicePoint}/command
smartlab/adapter/{adapterName}/{devicePoint}/telemetry
smartlab/adapter/{adapterName}/{devicePoint}/event
```

Registration contains `adapterName`, `rawConfigFormat="INI"`, the exact setup text and epoch
milliseconds. Heartbeat contains only `status="ALIVE"` and timestamp.

- [ ] **Step 3: Connect the independent SmartLab MQTT client**

Use MQTT callback API V2, automatic reconnect delays and configured QoS. On every successful
connect, publish registration, subscribe to every configured device-point command topic and wake
the heartbeat loop. Never log passwords or complete connection dictionaries.

- [ ] **Step 4: Route and validate system commands**

Decode JSON objects and require `messageId`, `adapterName`, `devicePoint`, `commandName`,
`parameters` and `timestamp`. Require payload identity to match both the topic and loaded setup.
Call:

```python
self.core.execute_command(
    message_id,
    device_point,
    command_name,
    parameters,
)
```

For an envelope error with recoverable `messageId/devicePoint`, publish `COMMAND_FAILED` without
calling Core.

- [ ] **Step 5: Publish Core telemetry and events**

Wire Core handlers during Runtime construction. Publish temperature as:

```json
{
  "timestamp": 0,
  "adapterName": "PLCControllerAdapter-01",
  "devicePoint": "PLC1",
  "telemetryData": {"temperature": 0.0}
}
```

Publish events with timestamp, adapter/device identity, event name, optional `messageId`, and a
payload object. Do not queue unbounded output while SmartLab is offline.

- [ ] **Step 6: Add heartbeat, timeout scan and ordered shutdown**

One daemon maintenance thread publishes heartbeat at `heartbeatIntervalSec` and invokes
`core.expire_commands()` at least once per second. Stop accepting new commands first, stop the
maintenance loop, stop Core, then disconnect the SmartLab client.

- [ ] **Step 7: Create the public flat INI contract**

Create `adapterSetup.ini` using the backend sample grammar:

```text
adapterName: PLCControllerAdapter-01
template: PLCThermalController
category: PLCController
attribute: temperature [DOUBLE]
commands: setOperatingMode(mode STRING), setCooling(enabled BOOLEAN), setAlarm(enabled BOOLEAN)
device point: PLC1
attribute mapping: temperature = temperature
```

Declare CMD events `COMMAND_RECEIVED`, `COMMAND_RUNNING`, `COMMAND_COMPLETED`,
`COMMAND_FAILED`, `COMMAND_TIMEOUT`; declare the six OP events from Task 1. Do not declare
`MW10/MW20/MW21/MW22` as attributes and do not include credentials.

- [ ] **Step 8: Verify and commit Task 2**

Run:

```powershell
python -m py_compile adapter/testAdapter/adapterRuntime.py
```

Run the existing backend focused parser verification against the created INI using the repository's
`AdapterManifestService` test harness or a focused Maven test that parses the exact file.
Expected: the manifest contains one `temperature` attribute, three commands and one `PLC1` point.

Commit only:

```powershell
git add -- adapter/testAdapter/adapterRuntime.py adapter/testAdapter/adapterSetup.ini
git commit -m "feat: add SmartLab PLC adapter runtime"
```

---

### Task 3: Add the process entry point and perform integration verification

**Files:**
- Create: `adapter/testAdapter/main.py`
- Create: `adapter/testAdapter/requirements.txt`

**Interfaces:**
- Consumes: `RuntimeConfig`, `AdapterSetup`, `AdapterRuntime` and `Core`.
- Produces: executable `main() -> int`.

- [ ] **Step 1: Implement the thin process entry point**

Resolve files relative to `Path(__file__).parent`, load both configurations, configure sanitized
logging, create Core using the first and only configured device point, create AdapterRuntime, install
SIGINT/SIGTERM handlers, and call `runtime.run_forever()`. Return nonzero on startup failure without
printing passwords.

- [ ] **Step 2: Declare the runtime dependency**

Create `requirements.txt` containing:

```text
paho-mqtt>=2.1,<3
```

- [ ] **Step 3: Run fresh syntax verification**

Run:

```powershell
python -m py_compile adapter/testAdapter/main.py adapter/testAdapter/adapterRuntime.py adapter/testAdapter/core.py
```

Expected: exit code `0`.

- [ ] **Step 4: Verify configuration and repository scope**

Confirm `adapterSetup.ini` parses through the current backend manifest parser, contains only
`temperature`, and contains no `password`, `broker`, `username`, `MW10`, `MW20`, `MW21` or `MW22`
attribute declarations. Confirm `runtime.ini` is never included in registration payload construction.

Run `git diff --check` and inspect `git status --short`; preserve the pre-existing frontend, PLC
project, vendor-controller, manual and intentional deletion changes.

- [ ] **Step 5: Perform a bounded startup check**

Start `python adapter/testAdapter/main.py` with a short external timeout. Expected behavior is either:

- both clients connect and the process remains alive without GUI; or
- connection fails with a sanitized, actionable log and the process exits or keeps retrying according
  to the implemented startup policy.

Terminate only the Adapter process created by this check. Do not stop the backend, Vite, PLC or other
user processes.

- [ ] **Step 6: Commit the entry point**

```powershell
git add -- adapter/testAdapter/main.py adapter/testAdapter/requirements.txt
git commit -m "feat: add PLC adapter process entry point"
```

- [ ] **Step 7: Final review**

Review every design requirement against the implementation, verify the three Adapter commits contain
only intended files, and report any environment-dependent behavior without claiming real PLC command
execution unless an actual feedback snapshot was observed.
