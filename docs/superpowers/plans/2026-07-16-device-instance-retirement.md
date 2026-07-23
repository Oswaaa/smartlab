# Device Instance Retirement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace physical device-instance deletion with lifecycle retirement and consistently exclude retired instances from new business operations.

**Architecture:** `DEVICE_INSTANCES.LIFECYCLE_STATUS` is the sole lifecycle source. Management services enforce availability at resource boundaries; the state-machine engine remains lifecycle-agnostic. Components and historical data are retained and become read-only through the parent lifecycle.

**Tech Stack:** Java 21, Spring Boot, MyBatis-Plus, JUnit 5/Mockito, Vue 3, Element Plus, Vite.

## Global Constraints

- Lifecycle values are exactly `使用中` and `已注销`.
- Retirement is irreversible and never physically deletes related records.
- No retirement metadata is stored in `INSTANCE_CONFIG`.
- Component replacement status is not extended.

---

### Task 1: Lifecycle domain and management service

**Files:**
- Create: `Backend/src/main/java/com/smartlab/management/entity/resource/device/DeviceInstanceLifecycle.java`
- Modify: `Backend/src/main/java/com/smartlab/management/entity/resource/device/DeviceInstances.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/resource/device/DeviceInstanceService.java`
- Modify: `Backend/src/main/java/com/smartlab/management/controller/resource/device/DeviceInstanceController.java`
- Test: `Backend/src/test/java/com/smartlab/management/service/db/resource/device/DeviceInstanceServiceTest.java`

- [ ] Write failing tests for creation default, retirement preservation, retired edit rejection and active filtering.
- [ ] Add the mapped lifecycle field and lifecycle helper.
- [ ] Replace delete with transactional retirement and expose lifecycle filters.
- [ ] Run the focused service tests.

### Task 2: Runtime business boundaries

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/management/service/protocol/AdapterPayloadMapperService.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowTaskResourceService.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowEngine.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/resource/device/DeviceComponentService.java`
- Modify: data-set creation service located by call tracing.
- Test: focused adapter, workflow and component tests.

- [ ] Write failing tests proving retired resources are rejected before state-machine dispatch.
- [ ] Filter Adapter routes to usable instances.
- [ ] Reject retired instances at task binding/execution, data-set creation and component mutation boundaries.
- [ ] Run focused backend tests.

### Task 3: Device-instance UI and resource selectors

**Files:**
- Modify: `Frontend/src/views/device/DeviceInstanceManagement.vue`
- Modify: `Frontend/src/views/task/TaskList.vue`
- Modify: `Frontend/src/views/data/DataManagement.vue`
- Modify: other instance selectors found by repository search.

- [ ] Add lifecycle normalization and active/retired filtering to the instance page.
- [ ] Replace delete interaction with retirement confirmation and API call.
- [ ] Make retired details read-only while retaining historical tabs.
- [ ] Restrict all resource selectors to usable instances.
- [ ] Run frontend regression tests and production build.

### Task 4: Full review

- [ ] Search for physical instance deletion and unguarded runtime entry points.
- [ ] Run `mvn clean test`.
- [ ] Run frontend tests and `npm run build`.
- [ ] Run `git diff --check` and review the scoped diff.
