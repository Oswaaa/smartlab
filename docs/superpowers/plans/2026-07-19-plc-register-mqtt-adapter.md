# PLC Register MQTT Adapter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the generic PLC JSON bridge with a register-aware Adapter that consumes the real `plc/data` array, writes the exact `plc/MQTTCommand` array, and reports SmartLab lifecycle events from register confirmation.

**Architecture:** `setup.ini` remains the only runtime source for Broker credentials and all Topic names. The configuration parser loads the new timeout and two PLC topics; the pure router converts PLC snapshots and system semantic commands; the MQTT runtime publishes `COMMAND_RUNNING`, monitors timeout results, and routes only the configured subscriptions.

**Tech Stack:** Python 3.11+, Paho MQTT 2.x, standard-library `unittest`, existing Java `AdapterManifestService` integration test.

## Global Constraints

- `setup.ini` values must take effect after Adapter restart without Python code changes.
- PLC input is the exact array payload containing `DeviceSN` and `TagData`.
- PLC output is the exact array payload containing `DeviceSN` and one `TagData` register object.
- `MW0`, `MW20`, `MW21`, and `MW22` are the only registers given business semantics.
- Enabling cooling outside manual mode is rejected; disabling cooling is always allowed.
- No target-temperature or heating-duration command remains without defined PLC registers.
- System messages continue to follow the current SmartLab MQTT protocol.

---

### Task 1: Runtime and Adapter configuration contract

**Files:**
- Modify: `adapter/testAdapter/tests/test_config.py`
- Modify: `adapter/testAdapter/smartlab_adapter/config.py`
- Modify: `adapter/testAdapter/setup.ini`
- Modify: `adapter/testAdapter/adapterconfig.ini`
- Modify: `Backend/src/test/java/com/smartlab/adapter/TestAdapterIniContractTest.java`

**Interfaces:**
- Produces: `RuntimeConfig.command_timeout_sec: int`
- Produces topics: `plc.telemetry=plc/data`, `plc.command=plc/MQTTCommand`; no PLC event topic.
- Produces commands: `setOperatingMode(mode)` and `setCooling(enabled)`.

- [ ] Write failing tests asserting the configured Broker credentials, system Topics, PLC Topics, timeout, four attributes, two commands, and `deviceSN=plc0001`.
- [ ] Run `python -B -m unittest tests.test_config -v` and confirm failures reflect the old contract.
- [ ] Update the parser and both INI files, preserving case-sensitive keys and runtime-only credentials.
- [ ] Run configuration tests and the Java Adapter manifest test; expect all to pass.

### Task 2: PLC snapshot decoder and command encoder

**Files:**
- Create: `adapter/testAdapter/tests/test_plc_protocol.py`
- Create: `adapter/testAdapter/smartlab_adapter/plc_protocol.py`

**Interfaces:**
- Produces: `decode_snapshot(payload, device_sn) -> PlcSnapshot`
- Produces: `encode_register_write(device_sn, values) -> list[dict]`
- `PlcSnapshot` exposes integer words and bit-zero boolean helpers.

- [ ] Write failing tests with the real PLC array, multiple devices, multiple `TagData` rows, malformed arrays, and exact write-array equality.
- [ ] Run `python -B -m unittest tests.test_plc_protocol -v`; expect import failure.
- [ ] Implement strict decoding and exact compact command structures without accepting the obsolete object format.
- [ ] Run the protocol tests; expect all to pass.

### Task 3: Register-aware SmartLab router

**Files:**
- Modify: `adapter/testAdapter/tests/test_router.py`
- Modify: `adapter/testAdapter/smartlab_adapter/router.py`

**Interfaces:**
- `handle_system_command()` returns `COMMAND_RECEIVED` plus a PLC register-write publication.
- `handle_plc_telemetry()` returns system telemetry, generated OP events, and completed CMD events.
- `mark_command_running(message_id)` returns `COMMAND_RUNNING` after MQTT accepts the PLC publication.
- `expire_commands(timestamp)` returns `COMMAND_FAILED` for timed-out pending commands.

- [ ] Replace old router tests with failing tests for register mapping, bit conversion, manual-mode protection, mode writes, cooling writes, abort, confirmation, OP changes, and timeout.
- [ ] Run router tests and confirm failures correspond to the old generic router.
- [ ] Implement latest-snapshot state, pending target-register expectations, event generation, and timeout handling under one lock.
- [ ] Run router tests and expect all to pass.

### Task 4: MQTT runtime routing and configurable Topics

**Files:**
- Modify: `adapter/testAdapter/tests/test_runtime.py`
- Modify: `adapter/testAdapter/smartlab_adapter/runtime.py`
- Modify: `adapter/testAdapter/main.py`

**Interfaces:**
- Subscribes only to the rendered system command Topic and configured `plc/data` Topic.
- Publishes PLC commands to the configured `plc/MQTTCommand` Topic.
- Publishes `COMMAND_RUNNING` only after the PLC MQTT publish call succeeds.
- Heartbeat loop also expires pending commands.

- [ ] Write failing fake-client tests for exact subscriptions, exact PLC array publication, configurable Topic overrides, running events, and timeout events.
- [ ] Run runtime tests and confirm failures reflect the old PLC event subscription and object command payload.
- [ ] Update runtime routing and publication ordering without waiting for PUBACK inside Paho callbacks.
- [ ] Run runtime tests and expect all to pass.

### Task 5: Documentation and complete verification

**Files:**
- Modify: `adapter/testAdapter/README.md`

**Interfaces:**
- Documents the exact PLC payloads, register meanings, commands, manual-mode rule, and every editable `setup.ini` field.

- [ ] Replace obsolete heating/power examples with actual register messages and setup instructions.
- [ ] Run `python -B -m unittest discover -s tests -v`; expect zero failures.
- [ ] Run `python -m compileall -q main.py smartlab_adapter tests`; expect exit 0, then remove generated cache directories.
- [ ] Run `mvn -Dtest=AdapterManifestServiceTest,TestAdapterIniContractTest test` in `Backend`; expect build success.
- [ ] Run scoped `git diff --check` and confirm no PLC project files were changed.
