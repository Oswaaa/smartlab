# Execution Lifecycle Auto Transitions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep Adapter internal parameters only in parsed configuration, and implement one schema-driven execution lifecycle `IDLE → SENT → RECEIVED → RUNNING → COMPLETED` with nullable automatic transitions.

**Architecture:** `device-state-machine-model.json` is the source for execution states, the canonical path, and system-owned transitions. Backend metadata exposes those definitions to both model generation and the frontend. The engine treats `trigger: null` as an immediate automatic CMD transition, while the frontend lets users bind Adapter command events only to fixed lifecycle edges.

**Tech Stack:** Java 21, Spring Boot, Jackson, JSON Schema draft-07, Vue 3, Element Plus, Node test, Maven/JUnit 5.

## Global Constraints

- Do not change database tables.
- Do not preserve the obsolete `STARTED` state or flat/guessed event formats.
- `PARSED_CONFIG` retains `internal` and `sourceField`; device-model `adapterContract` excludes them.
- Technical state-space identifiers remain `CMD` and `OP`; UI labels use “执行生命周期” and “功能状态”.
- `trigger: null` means immediate automatic transition.

---

### Task 1: Adapter contract public projection

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/adapter/AdapterManifestService.java`
- Modify: `Frontend/src/views/device/components/deviceModel/deviceModelConstants.js`
- Modify: `Frontend/src/views/device/components/deviceModel/normalizers.js`
- Modify: `Frontend/src/views/device/components/deviceModel/DeviceModelDetail.vue`
- Test: `Backend/src/test/java/com/smartlab/adapter/AdapterManifestServiceTest.java`
- Test: `Frontend/tests/state-machine-contract.test.mjs`

**Interfaces:**
- Consumes: parsed Adapter command parameters containing `{ internal, sourceField }`.
- Produces: public `adapterContract.commands[].commandParameters[]` without internal parameters.

- [ ] Write backend and frontend failing tests proving internal parameters do not enter the public contract.
- [ ] Run focused tests and confirm failures expose the current leak.
- [ ] Filter internal parameters at backend contract construction and both frontend projection paths.
- [ ] Run focused tests and confirm they pass.

### Task 2: Schema-owned execution lifecycle

**Files:**
- Modify: `Backend/src/main/resources/schemas/device-state-machine-model.json`
- Modify: `Backend/src/main/java/com/smartlab/global/schema/SchemaMetadataService.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/resource/device/DeviceModelService.java`
- Modify: `Frontend/src/views/device/components/deviceModel/deviceModelConstants.js`
- Test: `Backend/src/test/java/com/smartlab/global/schema/SchemaMetadataServiceTest.java`
- Test: `Backend/src/test/java/com/smartlab/management/service/db/resource/device/DeviceModelServiceTest.java`

**Interfaces:**
- Consumes: `x-executionLifecycleMainPath` and `x-systemTransitions` from the state-machine schema.
- Produces: state names, canonical path, and system rules used by backend and frontend.

- [ ] Write failing tests requiring no `STARTED`, the canonical five-state path, and schema-derived system transitions.
- [ ] Run focused tests and confirm current hard-coded behavior fails.
- [ ] Add schema metadata and expose it through `SchemaMetadataService`.
- [ ] Replace backend and frontend duplicated fixed transition arrays with schema metadata.
- [ ] Run focused tests and confirm they pass.

### Task 3: Nullable automatic CMD transitions

**Files:**
- Modify: `Backend/src/main/resources/schemas/device-state-machine-model.json`
- Modify: `Backend/src/main/java/com/smartlab/global/schema/StateMachineInterfacePolicyService.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineModels.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineEngine.java`
- Test: `Backend/src/test/java/com/smartlab/global/schema/StateMachineInterfacePolicyServiceTest.java`
- Test: `Backend/src/test/java/com/smartlab/engine/statemachine/StateMachineEngineTest.java`

**Interfaces:**
- Consumes: transitions with either a signal trigger object or `trigger: null`.
- Produces: deterministic automatic execution until the next signal-backed state.

- [ ] Write failing tests for null-trigger validation, uniqueness, and `SENT → RECEIVED → RUNNING` automatic advancement.
- [ ] Run focused tests and confirm failures.
- [ ] Parse nullable triggers, validate their unique key, and execute automatic CMD transitions synchronously.
- [ ] Run focused tests and confirm they pass.

### Task 4: Unified execution-lifecycle editor

**Files:**
- Modify: `Frontend/src/views/device/components/deviceModel/DeviceModelEditorDrawer.vue`
- Modify: `Frontend/src/views/device/components/deviceModel/DeviceModelDetail.vue`
- Modify: `Frontend/src/views/device/components/deviceModel/normalizers.js`
- Test: `Frontend/tests/state-machine-contract.test.mjs`

**Interfaces:**
- Consumes: schema lifecycle metadata and Adapter `cmdEvents`.
- Produces: fixed-edge CMD transitions where empty intermediate bindings serialize as `trigger: null` and completion requires an event.

- [ ] Write failing pure-function tests for lifecycle transition materialization and reload normalization.
- [ ] Run the frontend test and confirm failures.
- [ ] Replace the two CMD sections with one “执行生命周期转移规则” section and fixed edges.
- [ ] Generate CMD transitions from bindings while retaining the OP rule editor.
- [ ] Run frontend tests and production build.

### Task 5: Regression verification

**Files:**
- Verify all files above without unrelated edits.

- [ ] Run `mvn test` in `Backend` and require zero failures.
- [ ] Run frontend Node tests separately and require zero failures and no port conflict.
- [ ] Run `npm run build` in `Frontend` and require a successful production build.
- [ ] Run scoped `git diff --check` and inspect for remaining `STARTED`, duplicated fixed transitions, and internal parameter rendering.
