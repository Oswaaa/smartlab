# MQTT PLC Test Adapter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Python MQTT middleware Adapter in `adapter/testAdapter` that registers with SmartLab, forwards system commands to one PLC, and maps PLC telemetry/events back to SmartLab.

**Architecture:** One Paho MQTT client connects to the shared Broker and routes messages between configurable system and PLC topics. Pure configuration and routing modules own parsing, validation, internal-parameter injection, attribute mapping, and message transformation; the runtime module owns MQTT lifecycle and heartbeat behavior.

**Tech Stack:** Python 3.11+, standard library (`configparser`, `dataclasses`, `json`, `logging`, `threading`, `unittest`), `paho-mqtt>=2.1,<3`.

## Global Constraints

- Do not hard-code final MQTT topics, Broker credentials, Client ID, or PLC identifiers in Python code.
- `adapterconfig.ini` follows the existing SmartLab INI contract and is sent verbatim during registration.
- `setup.ini` is runtime-only and is never sent to SmartLab.
- System messages conform to `AdapterRegisterRequest`, `CommandMessageFormat`, `TelemetryMessageFormat`, `EventMessageFormat`, and `AdapterHeartbeat`.
- The Adapter and the single PLC use one Broker and one MQTT client connection.
- Adapter-internal parameters are injected from device-point fields and are never required from the system command payload.

---

### Task 1: Adapter configuration parser

**Files:**
- Create: `adapter/testAdapter/tests/test_config.py`
- Create: `adapter/testAdapter/smartlab_adapter/config.py`
- Create: `adapter/testAdapter/smartlab_adapter/__init__.py`
- Create: `adapter/testAdapter/adapterconfig.ini`
- Create: `adapter/testAdapter/setup.ini`

**Interfaces:**
- Produces: `load_adapter_config(path) -> AdapterConfig`
- Produces: `load_runtime_config(path) -> RuntimeConfig`
- Produces: `AdapterConfig.device_point(name)`, `AdapterConfig.command(point, name)`, and `RuntimeConfig.topic(template_name, **values)`

- [ ] **Step 1: Write failing tests** for parsing template attributes, commands, internal parameters, events, device point mappings, runtime credentials, and topic templates.
- [ ] **Step 2: Run tests** with `python -m unittest tests.test_config -v`; expect import failure because the parser does not exist.
- [ ] **Step 3: Implement parser and both INI files** with explicit section/key validation and no implicit ConfigParser interpolation.
- [ ] **Step 4: Run tests** and expect all configuration tests to pass.

### Task 2: Pure message router

**Files:**
- Create: `adapter/testAdapter/tests/test_router.py`
- Create: `adapter/testAdapter/smartlab_adapter/messages.py`
- Create: `adapter/testAdapter/smartlab_adapter/router.py`

**Interfaces:**
- Consumes: `AdapterConfig`, `RuntimeConfig`
- Produces: `AdapterRouter.handle_system_command(payload, topic)` returning PLC publications and system events.
- Produces: `AdapterRouter.handle_plc_telemetry(payload, topic)` returning a SmartLab telemetry publication.
- Produces: `AdapterRouter.handle_plc_event(payload, topic)` returning a SmartLab event publication.

- [ ] **Step 1: Write failing tests** for command validation, internal parameter injection, topic extraction, telemetry field mapping, CMD event correlation, OP event forwarding, and terminal-event cleanup.
- [ ] **Step 2: Run tests** with `python -m unittest tests.test_router -v`; expect import failure.
- [ ] **Step 3: Implement message builders and router** using immutable publication objects and a lock-protected pending-command registry.
- [ ] **Step 4: Run tests** and expect all router tests to pass.

### Task 3: MQTT runtime and process entrypoint

**Files:**
- Create: `adapter/testAdapter/tests/test_runtime.py`
- Create: `adapter/testAdapter/smartlab_adapter/runtime.py`
- Create: `adapter/testAdapter/main.py`
- Create: `adapter/testAdapter/requirements.txt`

**Interfaces:**
- Consumes: `AdapterConfig`, `RuntimeConfig`, `AdapterRouter`
- Produces: `AdapterRuntime.start()`, `AdapterRuntime.stop()`, reconnect callbacks, subscription routing, registration, heartbeat, and graceful shutdown.

- [ ] **Step 1: Write failing tests** using a small fake MQTT client for subscription and publication behavior.
- [ ] **Step 2: Run tests** with `python -m unittest tests.test_runtime -v`; expect import failure.
- [ ] **Step 3: Implement runtime and entrypoint** with dependency injection for the MQTT client, Paho v2 callbacks, reconnect backoff, heartbeat, and sanitized logging.
- [ ] **Step 4: Run tests** and expect all runtime tests to pass.

### Task 4: Documentation and integration verification

**Files:**
- Create: `adapter/testAdapter/README.md`
- Create: `adapter/testAdapter/tests/__init__.py`

**Interfaces:**
- Documents: setup, Topic templates, SmartLab/PLC JSON messages, PLC command names, and verification commands.

- [ ] **Step 1: Write README** with exact installation, configuration, launch, and PLC payload examples.
- [ ] **Step 2: Run all unit tests** with `python -m unittest discover -s tests -v`; expect zero failures.
- [ ] **Step 3: Compile all Python files** with `python -m compileall -q .`; expect exit code 0.
- [ ] **Step 4: Run the existing backend Adapter manifest tests** to confirm `adapterconfig.ini` follows the backend contract.
- [ ] **Step 5: Inspect scoped git diff** and confirm no PLC project files or unrelated worktree changes were modified.
