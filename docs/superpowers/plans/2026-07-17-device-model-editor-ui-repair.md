# Device Model Editor UI Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the missing mapping and state-machine editors, unify execution-lifecycle rule configuration, and repair the layout of all model-creation form sections without changing persisted model contracts.

**Architecture:** Keep `DeviceModelEditorDrawer.vue` as the existing orchestration component, but extract execution-lifecycle row generation and serialization into a focused JavaScript module with direct Node tests. Restore the deleted template blocks using the existing draft APIs, while keeping registered Adapter definitions read-only and mapping fields editable.

**Tech Stack:** Vue 3, Element Plus, Vite, Node test runner.

## Global Constraints

- Adapter commands, attributes, events, protocol, adapter name, and category remain registered-source data and are read-only in the device-model editor.
- Attribute and operation mappings remain editable.
- Automatic transitions remain valid only for CMD transitions.
- Failure branches with no selected event are omitted rather than serialized as automatic transitions.
- No new frontend dependencies.

---

### Task 1: Lifecycle rule model

**Files:**
- Create: `Frontend/src/views/device/components/deviceModel/deviceModelLifecycle.js`
- Create: `Frontend/tests/device-model-lifecycle.test.mjs`
- Modify: `Frontend/src/views/device/components/deviceModel/DeviceModelEditorDrawer.vue`

**Interfaces:**
- Produces `buildLifecycleRuleRows(mainPath)` for normal and failure rule rows.
- Produces `serializeLifecycleTransitions(rows, bindings, adapterInterfaceName)`.
- Produces `hydrateLifecycleBindings(transitions, rows)`.

- [ ] Write tests proving optional blank stages become automatic CMD transitions, required blank stages fail, selected required events are serialized, and failure rows are independent and omitted when blank.
- [ ] Run `node --test tests/device-model-lifecycle.test.mjs` and verify it fails because the module is absent.
- [ ] Implement the three lifecycle helpers and replace the drawer's special `FAILED` binding logic.
- [ ] Run the lifecycle tests and verify all cases pass.

### Task 2: Restore mapping and state definitions

**Files:**
- Modify: `Frontend/src/views/device/components/deviceModel/DeviceModelEditorDrawer.vue`
- Create: `Frontend/tests/device-model-editor-structure.test.mjs`

**Interfaces:**
- Consumes existing draft fields `adapterContract.telemetry.attributesMapping`, `functionMappings`, `stateMachineInterfaces`, `cmdState`, and `opState`.

- [ ] Add a structural regression test asserting the SFC contains `edit-mapping`, `edit-state`, execution lifecycle, functional state, attribute mapping, and operation mapping regions.
- [ ] Run the structural test and verify it fails against the currently deleted regions.
- [ ] Restore editable attribute/operation mappings, read-only interfaces and CMD states, and editable OP states.
- [ ] Move lifecycle transitions under `edit-state` instead of nesting them inside the Adapter contract section.
- [ ] Run the structural test and verify it passes.

### Task 3: Repair form layout and tables

**Files:**
- Modify: `Frontend/src/views/device/components/deviceModel/DeviceModelEditorDrawer.vue`

**Interfaces:**
- Keeps existing click handlers and persistence payload unchanged.

- [ ] Add a shared section header action layout with `display:flex`, wrapping behavior, and non-overlapping buttons.
- [ ] Convert basic information to a responsive two-column grid.
- [ ] Standardize action buttons for attributes, operations, ports, constraints, BOM, mappings, and transition rules.
- [ ] Replace inline transition layouts with named grid classes and consistent table columns.
- [ ] Normalize constraint and BOM headings, labels, widths, row actions, and empty states.
- [ ] Add responsive rules for narrower drawers.

### Task 4: Verification

**Files:**
- Test: `Frontend/tests/*.test.mjs`

- [ ] Run `node --test tests/auth-store.test.mjs tests/state-machine-contract.test.mjs tests/device-model-lifecycle.test.mjs tests/device-model-editor-structure.test.mjs` and verify zero failures.
- [ ] Run `npm run build` and verify Vite exits with code 0.
- [ ] Open the device-model creation drawer and verify all anchor links resolve, states are visible, functional states can be added, lifecycle badges react to event selection, and tables remain aligned.
- [ ] Run `git diff --check` and inspect the final diff for accidental deletions or hardcoded Adapter contract editing paths.
