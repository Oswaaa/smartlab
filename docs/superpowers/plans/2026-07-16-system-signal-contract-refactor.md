# System Signal Contract Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make protocol, device models, state-machine execution, Adapter messaging, workflow runtime, and frontend metadata use one small hard-coded signal semantic set without reset signals or duplicated routing data.

**Architecture:** JSON resources remain the source of signal names and model enums. Java code implements the fixed START/ABORT payload semantics, while workflow connections and runtime context provide routing. Adapter-defined events flow through `adapterContract.events` into state-machine interfaces without a global lifecycle enum.

**Tech Stack:** Java 17+, Spring Boot, Jackson, JSON Schema draft-07, JUnit 5/Mockito, Vue 3, TypeScript, Vite.

## Global Constraints

- Do not modify the database schema.
- Do not preserve compatibility with old signal names.
- Do not use JSON Schema `oneOf` for system signals.
- Do not add source or target routing fields to `SystemSignalFormat`.
- START requires `commandName` and `parameters`; ABORT has no required payload.
- Terminal command states publish once and then automatically return to `IDLE`.

---

### Task 1: Protocol and schema sources

**Files:**
- Modify: `Backend/src/main/resources/schemas/protocol-dict.json`
- Modify: `Backend/src/main/resources/schemas/device-capability-model.json`
- Modify: `Backend/src/main/resources/schemas/device-state-machine-model.json`
- Modify: `Backend/src/main/resources/schemas/workflow-model.json`
- Modify: `Backend/src/main/resources/samples/adapterSetup.json`
- Modify: `Backend/src/main/resources/samples/adapterSetup.ini`
- Test: `Backend/src/test/java/com/smartlab/global/protocol/ProtocolDictionaryServiceTest.java`
- Test: `Backend/src/test/java/com/smartlab/global/schema/SchemaMetadataServiceTest.java`

**Interfaces:**
- Produces: `CommunicationProtocol`, the four fixed signal families, generic optional-payload `SystemSignalFormat`, model-owned workflow lifecycle metadata, and Adapter-owned input events.

- [ ] Write failing assertions that `CommunicationProtocol` is `MQTT/HTTP`, old lifecycle definitions are absent, and only new START/ABORT names are exposed.
- [ ] Run `mvn -Dtest=ProtocolDictionaryServiceTest,SchemaMetadataServiceTest test` from `Backend` and verify the assertions fail for the current resources.
- [ ] Update the four JSON resources and samples; remove Adapter configuration schema definitions from protocol and change capability `config.protocol` to `$ref` `CommunicationProtocol`.
- [ ] Update schema metadata extraction to read workflow lifecycle from `workflow-model.json` and Adapter configuration documentation from `samples` only where the UI requires it.
- [ ] Re-run the two tests and verify they pass.

### Task 2: Fixed signal payload semantics

**Files:**
- Create: `Backend/src/main/java/com/smartlab/engine/signal/SystemSignal.java`
- Create: `Backend/src/main/java/com/smartlab/engine/signal/SystemSignalValidator.java`
- Test: `Backend/src/test/java/com/smartlab/engine/signal/SystemSignalValidatorTest.java`

**Interfaces:**
- Produces: `SystemSignal(String signalName, JsonNode payload)` and `void validate(SystemSignal signal)`.
- Validation: `WF_EXECUTE_START`, `MANUAL_EXECUTE_START`, and `CMD_START` require a nonblank `commandName` and object `parameters`; ABORT signals accept a missing payload; all names must exist in protocol/model metadata.

- [ ] Write failing tests for valid START, missing START command name, missing START parameters, and payload-free ABORT.
- [ ] Run `mvn -Dtest=SystemSignalValidatorTest test` and verify failure because the API does not exist.
- [ ] Implement the record and explicit switch/set-based validator without a generic rule engine.
- [ ] Re-run the focused test and verify it passes.

### Task 3: Device-model and state-machine definitions

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/global/schema/StateMachineInterfacePolicyService.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/resource/device/DeviceModelService.java`
- Test: `Backend/src/test/java/com/smartlab/global/schema/StateMachineInterfacePolicyServiceTest.java`
- Test: `Backend/src/test/java/com/smartlab/management/service/db/resource/device/DeviceModelServiceTest.java`

**Interfaces:**
- Consumes: protocol/model metadata and `adapterContract.events`.
- Produces: standard interfaces using only new control signals; Adapter input allowed signals using only selected Adapter events; no generated `COMMAND_*` transitions.

- [ ] Change tests to expect new names, no RESET, and Adapter input equality with the selected contract events.
- [ ] Run both tests and verify the current implementation fails.
- [ ] Remove global Adapter lifecycle merging and hard-coded Adapter event transitions; retain START/ABORT control transitions with new names.
- [ ] Ensure command states come from `device-state-machine-model.json` and terminal-to-IDLE is an engine behavior, not a RESET transition.
- [ ] Re-run both tests and verify they pass.

### Task 4: State-machine runtime and automatic IDLE

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineEngine.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineDictionary.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineSendActionEvent.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineInterfaceSignalEvent.java`
- Test: `Backend/src/test/java/com/smartlab/engine/statemachine/StateMachineEngineTest.java`

**Interfaces:**
- Consumes: `SystemSignal` plus separately supplied runtime routing context.
- Produces: compact `{signalName,payload?}` interface signals and typed execution events carrying routing metadata outside the signal.

- [ ] Add failing tests proving START payload validation, payload-free ABORT, terminal status publication before automatic `IDLE`, and absence of routing fields inside the signal body.
- [ ] Run the focused state-machine test and verify the failures match current behavior.
- [ ] Refactor dispatch and SEND execution to keep routing context in Java event fields; publish terminal output synchronously, then persist `IDLE`.
- [ ] Re-run the focused test and verify it passes.

### Task 5: Adapter event classification and outbound start/abort

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/management/service/protocol/AdapterPayloadMapperService.java`
- Modify: `Backend/src/main/java/com/smartlab/adapter/MqttAdapterMessagingService.java`
- Modify: `Backend/src/main/java/com/smartlab/adapter/AdapterManifestService.java`
- Test: `Backend/src/test/java/com/smartlab/adapter/AdapterManifestServiceTest.java`
- Test: `Backend/src/test/java/com/smartlab/adapter/MqttAdapterMessagingServiceTest.java`

**Interfaces:**
- Consumes: selected `adapterContract.events`, `CMD_START`, `CMD_ABORT`, and active execution context.
- Produces: normal command publication for START and cancellation publication for ABORT; command-event `messageId` validation based on membership in `cmdEvents`.

- [ ] Write failing tests showing arbitrary configured command events require `messageId`, operation events do not, and `CMD_ABORT` does not call the normal command builder.
- [ ] Run focused Adapter tests and verify failure.
- [ ] Replace global lifecycle-enum lookup with Adapter-contract lookup; branch outbound handling on `event.signalName()` and build the correct transport message.
- [ ] Remove old-format aliases and duplicate validation encountered in the touched manifest path; preserve description review behavior.
- [ ] Re-run focused Adapter tests and verify they pass.

### Task 6: Workflow runtime and control entry points

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowEngine.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowTaskControlService.java`
- Modify: `Backend/src/main/java/com/smartlab/management/controller/resource/device/DeviceInstanceController.java`
- Test: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowEngineTest.java`
- Test: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowTaskControlServiceTest.java`

**Interfaces:**
- Produces: workflow START signals with command payload, payload-free ABORT signals, and task progression based on terminal status emitted before automatic IDLE.

- [ ] Update tests to expect `WF_EXECUTE_START`, `WF_EXECUTE_ABORT`, `MANUAL_EXECUTE_START`, and `MANUAL_EXECUTE_ABORT`.
- [ ] Run focused workflow tests and verify failure against old names.
- [ ] Update dispatch sites and remove RESET assumptions; keep task/step/device routing in runtime context rather than signal payload.
- [ ] Re-run focused workflow tests and verify they pass.

### Task 7: Frontend protocol-driven rendering

**Files:**
- Modify: `Frontend/src/views/device/components/deviceModel/deviceModelConstants.js`
- Modify: `Frontend/src/views/device/components/deviceModel/normalizers.js`
- Modify: `Frontend/src/views/device/DeviceInstanceManagement.vue`
- Modify: `Frontend/src/views/task/WorkflowDesigner.vue`

**Interfaces:**
- Consumes: schema metadata endpoint containing new signal names and workflow lifecycle states.
- Produces: editors and manual controls without old signal fallbacks or reset actions.

- [ ] Replace old fallback arrays and generated transitions with new START/ABORT names.
- [ ] Remove front-end generated `COMMAND_*` Adapter transitions; render configured Adapter events from `adapterContract.events`.
- [ ] Ensure START controls collect command and parameter values while ABORT controls send no payload.
- [ ] Run `npm run build` from `Frontend` and fix all compile errors.

### Task 8: Full regression and conflict audit

**Files:**
- Review: all modified files from Tasks 1-7.

**Interfaces:**
- Produces: verified end-to-end signal contract with no old compatibility path.

- [ ] Run `rg` for every old signal and removed protocol definition; only migration documentation may match.
- [ ] Run `mvn test` from `Backend` and require zero failures.
- [ ] Run `npm run build` from `Frontend` and require exit code zero.
- [ ] Inspect `git diff --check` and the scoped diff for duplicate sources, raw maps crossing module boundaries, hard-coded Adapter event names, and unrelated formatting churn.
