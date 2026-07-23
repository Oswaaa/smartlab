# State Machine Contract Boundary Fixes Implementation Plan

> **For agentic workers:** Execute inline with test-first checkpoints; do not delegate this plan.

**Goal:** Correct execution-lifecycle signaling, constrain automatic transitions to CMD, and make registered Adapter configuration the only source of device-model Adapter contracts.

**Architecture:** State-machine standards remain declared in `device-state-machine-model.json` and are exposed through `SchemaMetadataService`. Runtime execution enters each state sequentially so every `onEntry` action observes its own state. Device models store a filtered projection of the selected registered Adapter contract; users edit only business mappings, never Adapter commands, attributes, or events.

**Tech Stack:** Java 17, Spring Boot, Jackson, JUnit 5/Mockito, Vue 3, Element Plus, Node test runner.

## Global Constraints

- Automatic transitions are valid only in the CMD state space.
- `internal` Adapter parameters remain in Adapter `PARSED_CONFIG` and never enter device capability models.
- Terminal CMD states automatically return to `IDLE` through normal state-entry behavior.
- Existing unrelated worktree changes must remain untouched.

### Task 1: State-machine runtime semantics

**Files:**
- Modify: `Backend/src/test/java/com/smartlab/engine/statemachine/StateMachineEngineTest.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineEngine.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineDictionary.java`

- [ ] Add a failing test asserting `SENT`, `RECEIVED`, and `RUNNING` are emitted in order across an automatic chain.
- [ ] Add a failing test asserting terminal reset emits the `IDLE` entry signal.
- [ ] Implement ordered state-entry execution and run the focused tests.

### Task 2: Automatic-transition validation

**Files:**
- Modify: `Backend/src/test/java/com/smartlab/global/schema/StateMachineInterfacePolicyServiceTest.java`
- Modify: `Backend/src/main/java/com/smartlab/global/schema/StateMachineInterfacePolicyService.java`
- Modify: `Backend/src/main/resources/schemas/device-state-machine-model.json`

- [ ] Add a failing test for an OP transition with `trigger: null`.
- [ ] Reject automatic OP transitions with a clear validation error.
- [ ] Clarify the Schema description and run focused tests.

### Task 3: Registered Adapter contract boundary

**Files:**
- Modify: `Backend/src/test/java/com/smartlab/management/service/db/resource/device/DeviceModelServiceTest.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/resource/device/DeviceModelService.java`
- Modify: `Frontend/src/views/device/components/deviceModel/DeviceModelEditorDrawer.vue`
- Modify: `Frontend/tests/state-machine-contract.test.mjs`

- [ ] Add tests proving a submitted contract cannot introduce commands outside the registered Adapter projection.
- [ ] Rebuild the immutable contract fields from `adapterName + categoryName`; preserve only business attribute mappings.
- [ ] Remove manual configuration import and command/attribute mutation controls from the model editor.
- [ ] Render Adapter commands, attributes, and events read-only.

### Task 4: Metadata consistency and verification

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/global/schema/StateMachineInterfacePolicyService.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/resource/device/DeviceModelService.java`
- Modify: `Frontend/src/views/device/components/deviceModel/deviceModelConstants.js`
- Modify: `Frontend/src/views/device/components/deviceModel/DeviceModelEditorDrawer.vue`

- [ ] Resolve standard interfaces by type/direction instead of repeating interface names outside metadata adapters.
- [ ] Remove frontend lifecycle fallback arrays that can mask missing metadata.
- [ ] Run focused backend and frontend tests.
- [ ] Run the full backend test suite and frontend production build.
- [ ] Review `git diff` for unrelated churn and remaining duplicated lifecycle literals.
