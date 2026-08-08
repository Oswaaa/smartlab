# Workflow Model to Task Execution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a model-driven path from workflow authoring through publishing, device binding, preflight, execution, and model-aligned runtime feedback without exposing transport/API details to users.

**Architecture:** `FLOW_MODELS` and `FLOW_NODE` remain the single persisted workflow aggregate. The backend canonicalizes authoring input, merges the global system contract, compiles an immutable in-memory execution graph for a published model row, and exposes business projections for resource requirements, preflight, and execution monitoring. The frontend renders interfaces and ports explicitly but treats system lifecycle/actions as locked projections and never derives task bindings by parsing state-machine connections.

**Tech Stack:** Java 21, Spring Boot 3.3.5, MyBatis-Plus, Jackson, JUnit 5/Mockito, Vue 3, Element Plus, Vue Flow, Axios, Node.js built-in test runner, Vite.

## Global Constraints

- The workflow model is the only editable business source of truth; compiled execution graphs and observation snapshots are derived artifacts.
- Interfaces, ports, variables, business triggers, and business actions remain visible; required system items remain visible and locked.
- HTTP paths, message topics, `resourceMap`, `bindingKey`, `nodeIdRef`, `NODE_TO_DEVICE`, and `DEVICE_TO_NODE` must not appear in ordinary user-facing copy.
- Workflow authoring chooses a device model and capability; task creation binds concrete device instances.
- Existing `FLOW_MODELS`, `FLOW_NODE`, `TASK`, and `TASK_STEP` tables remain authoritative; do not introduce a duplicate workflow-definition table.
- Preserve legacy workflow/task reads and unrelated dirty-worktree changes.
- Add no frontend or backend dependency unless an existing library cannot implement the requirement.
- Every implementation task follows test-first development and stages only its own files.

---

## File and Responsibility Map

### Backend model boundary

- `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCanonicalizer.java` — deep-copy authoring input, restore authoritative system template items, and preserve business additions.
- `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCompiler.java` — public preparation/compile entry, semantic validation, and immutable execution graph.
- `Backend/src/main/java/com/smartlab/management/dto/workflow/WorkflowIssue.java` — structured design/publish/binding/preflight/execution issue.
- `Backend/src/main/java/com/smartlab/management/dto/workflow/WorkflowPreparationResponse.java` — normalized editor view, issues, and executable/published flags.
- `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowService.java` — draft/publish version lifecycle and persistence.
- `Backend/src/main/java/com/smartlab/management/controller/workflow/WorkflowController.java` — draft, publish, detail, and requirements application endpoints.

### Backend task boundary

- `Backend/src/main/java/com/smartlab/management/dto/workflow/DeviceBindingRequirement.java` — business resource slot projection.
- `Backend/src/main/java/com/smartlab/management/dto/workflow/TaskDeviceBindingRequest.java` — concrete instance selection without exposing internal resource-map layout.
- `Backend/src/main/java/com/smartlab/management/dto/workflow/TaskPreflightRequest.java` and `TaskPreflightResponse.java` — non-mutating readiness request/result.
- `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowTaskResourceService.java` — stable occurrence slots, legacy-key compatibility, and resource-map assembly.
- `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowExecutionReadinessService.java` — structured checks plus throwing compatibility method.
- `Backend/src/main/java/com/smartlab/management/service/db/workflow/TaskService.java` — create/start through the same binding and preflight logic.
- `Backend/src/main/java/com/smartlab/management/service/db/workflow/TaskExecutionViewService.java` — model-aligned task-step projection.
- `Backend/src/main/java/com/smartlab/management/controller/workflow/TaskController.java` — preflight and execution-view endpoints.

### Frontend workflow boundary

- `Frontend/src/services/workflowApi.js` — workflow draft/publish/detail/requirements transport adapter.
- `Frontend/src/utils/workflowAuthoring.js` — editor payload creation, canonical-response adoption, and issue indexing.
- `Frontend/src/views/task/WorkflowDesigner.vue` — page orchestration only.
- `Frontend/src/components/task/workflow/WorkflowInterfacesPortsPanel.vue` — visible system/business interfaces and ports.
- `Frontend/src/components/task/workflow/WorkflowLifecyclePanel.vue` — locked system lifecycle view.
- Existing workflow components — node canvas, variable mapping, trigger/action editing, and inspector composition.

### Frontend task boundary

- `Frontend/src/services/taskApi.js` — task create/preflight/control/execution-view adapter.
- `Frontend/src/components/task/TaskCreateDrawer.vue` — model selection, variables, device binding, and preflight orchestration.
- `Frontend/src/components/task/TaskPreflightPanel.vue` — structured readiness result.
- `Frontend/src/components/task/TaskExecutionView.vue` — model-aligned progress and failure display.
- `Frontend/src/components/task/TaskResourceBindingCanvas.vue` — consumes backend requirements only.
- `Frontend/src/utils/taskResourceBindings.js` — pure requirement grouping and selection serialization.
- `Frontend/src/views/task/TaskList.vue` — task list and component composition.

---

### Task 1: Canonicalize Authoring Models in the Backend

**Files:**
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCanonicalizer.java`
- Create: `Backend/src/main/java/com/smartlab/management/dto/workflow/WorkflowIssue.java`
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowPreparation.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCompiler.java:32-208`
- Test: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowDefinitionCompilerTest.java`

**Interfaces:**
- Consumes: `WorkflowSaveRequest`, `WorkflowNodeSystemContract.template(nodeType, functionType)`.
- Produces: `WorkflowPreparation prepare(WorkflowSaveRequest request, Mode mode)` and backward-compatible `CompiledWorkflow compile(WorkflowSaveRequest request)`.

- [ ] **Step 1: Write failing canonicalization tests**

Add tests proving that marker-bearing or markerless system items are replaced by the backend template while custom variables, ports, UPDATE actions, and custom triggers survive.

```java
@Test
void draftPreparationRestoresSystemContractAndPreservesBusinessItems() {
    WorkflowSaveRequest request = markerlessAggregateWithCustomUpdate();

    WorkflowPreparation result = compiler.prepare(request, WorkflowPreparation.Mode.DRAFT);

    JsonNode node = result.normalized().getNodesDef().get(1);
    assertEquals("aggregate.lifecycle", node.path("lifecycle").path("_systemKey").asText());
    assertEquals("setCounter", node.path("actions").get(1).path("actionName").asText());
    assertTrue(result.issues().stream().noneMatch(WorkflowIssue::blocking));
}

@Test
void publishPreparationRejectsBusinessItemUsingReservedSystemIdentity() {
    WorkflowSaveRequest request = definitionWithCustomActionNamed("emitActive");
    WorkflowPreparation result = compiler.prepare(request, WorkflowPreparation.Mode.PUBLISH);
    assertTrue(result.issues().stream().anyMatch(issue ->
            issue.code().equals("WORKFLOW_SYSTEM_NAME_RESERVED") && issue.blocking()));
}
```

- [ ] **Step 2: Run the focused compiler tests and verify failure**

Run from `Backend`:

```bash
mvn -Dtest=WorkflowDefinitionCompilerTest test
```

Expected: compilation fails because `prepare`, `WorkflowPreparation`, and `WorkflowIssue` do not exist.

- [ ] **Step 3: Implement structured issues and canonicalization**

Use these exact public shapes:

```java
public record WorkflowIssue(
        String code, String stage, String path, String elementType,
        String elementId, boolean blocking, String message, String suggestion) {}

public record WorkflowPreparation(
        WorkflowSaveRequest normalized,
        List<WorkflowIssue> issues,
        WorkflowDefinitionCompiler.CompiledWorkflow compiled) {
    public enum Mode { DRAFT, PUBLISH }
    public boolean executable() { return compiled != null && issues.stream().noneMatch(WorkflowIssue::blocking); }
}
```

`WorkflowDefinitionCanonicalizer.canonicalize(WorkflowSaveRequest input)` must deep-copy JSON, merge system lifecycle/actions/interfaces/triggers by `_systemKey` or reserved template identity, preserve non-system items, and never accept client changes to system fields. Keep `compile(request)` as:

```java
public CompiledWorkflow compile(WorkflowSaveRequest request) {
    WorkflowPreparation prepared = prepare(request, WorkflowPreparation.Mode.PUBLISH);
    if (!prepared.executable()) throw new IllegalArgumentException(firstBlockingMessage(prepared.issues()));
    return prepared.compiled();
}
```

- [ ] **Step 4: Run compiler and system-contract tests**

```bash
mvn -Dtest=WorkflowDefinitionCompilerTest,WorkflowNodeSystemContractTest test
```

Expected: PASS; existing strict compiler behavior remains available through `compile`.

- [ ] **Step 5: Commit the canonical compiler boundary**

```bash
git add Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCanonicalizer.java Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCompiler.java Backend/src/main/java/com/smartlab/engine/workflow/WorkflowPreparation.java Backend/src/main/java/com/smartlab/management/dto/workflow/WorkflowIssue.java Backend/src/test/java/com/smartlab/engine/workflow/WorkflowDefinitionCompilerTest.java
git commit -m "refactor(workflow): canonicalize authoring definitions in backend"
```

### Task 2: Separate Draft Save from Publish

**Files:**
- Create: `Backend/src/main/java/com/smartlab/management/dto/workflow/WorkflowPreparationResponse.java`
- Modify: `Backend/src/main/java/com/smartlab/management/dto/workflow/WorkflowDetailResponse.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowService.java:86-185`
- Modify: `Backend/src/main/java/com/smartlab/management/controller/workflow/WorkflowController.java`
- Test: `Backend/src/test/java/com/smartlab/management/service/db/workflow/WorkflowServiceTest.java`
- Create test: `Backend/src/test/java/com/smartlab/management/controller/workflow/WorkflowControllerTest.java`

**Interfaces:**
- Consumes: `WorkflowDefinitionCompiler.prepare(WorkflowSaveRequest request, WorkflowPreparation.Mode mode)` from Task 1.
- Produces: `saveDraft`, `publish`, `POST /api/workflow/draft`, `POST /api/workflow/publish`; legacy `/save` delegates to publish.

- [ ] **Step 1: Write failing service tests for model-version lifecycle**

```java
@Test
void savesIncompleteDraftButDoesNotMakeItExecutable() {
    WorkflowPreparationResponse saved = fixture.service().saveDraft(incompleteRequest());
    assertEquals("DRAFT", saved.definition().getStatus());
    assertFalse(saved.executable());
    assertTrue(saved.issues().stream().anyMatch(WorkflowIssue::blocking));
}

@Test
void editingActiveModelCreatesSuccessorDraft() {
    WorkflowPreparationResponse saved = fixture.service().saveDraft(editRequestForActiveModel(7L));
    assertNotEquals(7L, saved.definition().getId());
    assertEquals(7L, fixture.savedModel().getPredecessorId());
    assertEquals(3, saved.definition().getVersion());
}
```

- [ ] **Step 2: Run service tests to confirm failure**

```bash
mvn -Dtest=WorkflowServiceTest test
```

Expected: FAIL because draft/publish methods and response do not exist.

- [ ] **Step 3: Implement transactional draft and publish methods**

```java
public record WorkflowPreparationResponse(
        WorkflowDetailResponse definition, List<WorkflowIssue> issues,
        boolean executable, boolean published) {}

@Transactional
public WorkflowPreparationResponse saveDraft(WorkflowSaveRequest request) {
    WorkflowPreparation prepared = compiler.prepare(request, WorkflowPreparation.Mode.DRAFT);
    return persistPrepared(request, prepared, "DRAFT", false);
}

@Transactional
public WorkflowPreparationResponse publish(WorkflowSaveRequest request) {
    WorkflowPreparation prepared = compiler.prepare(request, WorkflowPreparation.Mode.PUBLISH);
    if (!prepared.executable()) {
        return new WorkflowPreparationResponse(toDetailResponse(prepared.normalized()),
                prepared.issues(), false, false);
    }
    return persistPrepared(request, prepared, "ACTIVE", true);
}
```

Implement `persistPrepared(WorkflowSaveRequest, WorkflowPreparation, String status, boolean published)` and `toDetailResponse(WorkflowSaveRequest)` as private service methods. Update an existing `DRAFT` row in place; preserve each existing backend-assigned `nodeIdRef` across rename and reorder, and allocate `max(nodeIdRef) + 1` only for new nodes. Editing an `ACTIVE` row inserts a successor with `predecessorId`, incremented version, and `DRAFT`; publishing runs `Mode.PUBLISH`, returns blocking issues without changing persistence, and marks an executable exact row `ACTIVE`. `requireExecutableDefinition` continues to accept only `ACTIVE`.

- [ ] **Step 4: Add controller tests and endpoints**

```java
@PostMapping("/draft")
public ApiResponse<WorkflowPreparationResponse> saveDraft(@Valid @RequestBody WorkflowSaveRequest request) {
    return ApiResponse.ok(workflowService.saveDraft(request));
}

@PostMapping("/publish")
public ApiResponse<WorkflowPreparationResponse> publish(@Valid @RequestBody WorkflowSaveRequest request) {
    return ApiResponse.ok(workflowService.publish(request));
}
```

The response must carry the normalized definition and issues so the editor can adopt server truth and locate problems.

- [ ] **Step 5: Run workflow service/controller tests**

```bash
mvn -Dtest=WorkflowServiceTest,WorkflowControllerTest test
```

Expected: PASS.

- [ ] **Step 6: Commit draft/publish lifecycle**

```bash
git add Backend/src/main/java/com/smartlab/management/dto/workflow/WorkflowPreparationResponse.java Backend/src/main/java/com/smartlab/management/dto/workflow/WorkflowDetailResponse.java Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowService.java Backend/src/main/java/com/smartlab/management/controller/workflow/WorkflowController.java Backend/src/test/java/com/smartlab/management/service/db/workflow/WorkflowServiceTest.java Backend/src/test/java/com/smartlab/management/controller/workflow/WorkflowControllerTest.java
git commit -m "feat(workflow): add draft and publish lifecycle"
```

### Task 3: Expose Backend-Derived Device Binding Requirements

**Files:**
- Create: `Backend/src/main/java/com/smartlab/management/dto/workflow/DeviceBindingRequirement.java`
- Create: `Backend/src/main/java/com/smartlab/management/dto/workflow/WorkflowResourceRequirementsResponse.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowTaskResourceService.java:49-307`
- Modify: `Backend/src/main/java/com/smartlab/management/controller/workflow/WorkflowController.java`
- Test: `Backend/src/test/java/com/smartlab/management/service/db/workflow/WorkflowTaskResourceServiceTest.java`
- Test: `Backend/src/test/java/com/smartlab/management/controller/workflow/WorkflowControllerTest.java`

**Interfaces:**
- Consumes: compiled ACTIVE workflow definition.
- Produces: `requirements(Long workflowId)` using opaque `slotId` and user-readable `occurrencePath`; legacy binding keys remain readable internally.

- [ ] **Step 1: Write failing repeated-subflow and rename-safe requirement tests**

```java
@Test
void requirementsUseNodeRefsForIdentityAndNamesOnlyForDisplay() {
    List<DeviceBindingRequirement> result = service.requirements(1L).bindings();
    assertEquals(List.of("1:12/2:31", "1:18/2:31"),
            result.stream().map(DeviceBindingRequirement::slotId).toList());
    assertEquals(List.of("主流程 / 加热A / 温控", "主流程 / 加热B / 温控"),
            result.stream().map(DeviceBindingRequirement::occurrencePath).toList());
}
```

- [ ] **Step 2: Run the focused resource test and verify failure**

```bash
mvn -Dtest=WorkflowTaskResourceServiceTest test
```

Expected: FAIL because the public requirements projection does not exist.

- [ ] **Step 3: Implement the projection without exposing state-machine interfaces**

```java
public record DeviceBindingRequirement(
        String slotId, String occurrencePath, Long flowModelId, Integer flowVersion,
        long nodeIdRef, String flowName, String nodeName,
        long deviceModelId, String capabilityName) {}

public record WorkflowResourceRequirementsResponse(
        Long workflowId, Integer workflowVersion, List<DeviceBindingRequirement> bindings) {}
```

Build `slotId` from the root/subflow occurrence chain of `flowModelId:nodeIdRef`. Keep the existing name-based key as a legacy alias accepted by `validate` and `resolveBinding`, but never return it to the new frontend.

- [ ] **Step 4: Add the requirements endpoint**

```java
@GetMapping("/{id}/requirements")
public ApiResponse<WorkflowResourceRequirementsResponse> requirements(@PathVariable Long id) {
    workflowService.requireExecutableDefinition(id);
    return ApiResponse.ok(resourceService.requirements(id));
}
```

- [ ] **Step 5: Run resource and controller tests**

```bash
mvn -Dtest=WorkflowTaskResourceServiceTest,WorkflowControllerTest test
```

Expected: PASS for root flow, repeated subflows, cycles, legacy aliases, and endpoint serialization.

- [ ] **Step 6: Commit backend-derived requirements**

```bash
git add Backend/src/main/java/com/smartlab/management/dto/workflow/DeviceBindingRequirement.java Backend/src/main/java/com/smartlab/management/dto/workflow/WorkflowResourceRequirementsResponse.java Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowTaskResourceService.java Backend/src/main/java/com/smartlab/management/controller/workflow/WorkflowController.java Backend/src/test/java/com/smartlab/management/service/db/workflow/WorkflowTaskResourceServiceTest.java Backend/src/test/java/com/smartlab/management/controller/workflow/WorkflowControllerTest.java
git commit -m "feat(task): derive device requirements from workflow model"
```

### Task 4: Add Structured Task Preflight and Binding Input

**Files:**
- Create: `Backend/src/main/java/com/smartlab/management/dto/workflow/TaskDeviceBindingRequest.java`
- Create: `Backend/src/main/java/com/smartlab/management/dto/workflow/TaskPreflightRequest.java`
- Create: `Backend/src/main/java/com/smartlab/management/dto/workflow/TaskPreflightResponse.java`
- Modify: `Backend/src/main/java/com/smartlab/management/dto/workflow/TaskCreateRequest.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowTaskResourceService.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowExecutionReadinessService.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/constraint/TaskConstraintService.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/workflow/TaskService.java:80-120`
- Modify: `Backend/src/main/java/com/smartlab/management/controller/workflow/TaskController.java`
- Test: `Backend/src/test/java/com/smartlab/management/service/db/workflow/WorkflowExecutionReadinessServiceTest.java`
- Test: `Backend/src/test/java/com/smartlab/management/service/db/constraint/TaskConstraintServiceTest.java`
- Test: `Backend/src/test/java/com/smartlab/management/service/db/workflow/TaskServiceTest.java`

**Interfaces:**
- Consumes: `slotId` requirements from Task 3.
- Produces: `preflight(TaskPreflightRequest request)`, `prepare(Long flowModelId, List<TaskDeviceBindingRequest> bindings)`, and task creation from `deviceBindings`.

- [ ] **Step 1: Write failing structured preflight tests**

```java
@Test
void preflightReturnsAllBindingAndReadinessIssuesWithoutMutatingTask() {
    TaskPreflightResponse result = service.preflight(requestWithOfflineAndMissingBindings());
    assertFalse(result.ready());
    assertEquals(List.of("TASK_BINDING_MISSING", "DEVICE_OFFLINE"),
            result.issues().stream().map(WorkflowIssue::code).sorted().toList());
    verify(taskMapper, never()).insert(any());
}
```

- [ ] **Step 2: Run readiness and task tests to confirm failure**

```bash
mvn -Dtest=WorkflowExecutionReadinessServiceTest,TaskServiceTest test
```

Expected: FAIL because preflight and binding DTOs do not exist.

- [ ] **Step 3: Implement binding conversion and non-throwing readiness inspection**

```java
public record TaskDeviceBindingRequest(String slotId, Long deviceInstanceId) {}

public record TaskPreflightRequest(
        Long flowModelId, JsonNode taskVariables,
        List<TaskDeviceBindingRequest> deviceBindings, JsonNode taskConstraints) {
    public static TaskPreflightRequest from(TaskCreateRequest request) {
        return new TaskPreflightRequest(request.getFlowModelId(), request.getTaskVariables(),
                request.getDeviceBindings(), request.getTaskConstraints());
    }
}

public record TaskPreflightResponse(boolean ready, List<WorkflowIssue> issues) {}
```

Add `WorkflowTaskResourceService.prepare(flowModelId, deviceBindings)` returning `PreparedTaskResources(JsonNode resourceMap, List<WorkflowIssue> issues)` and `WorkflowExecutionReadinessService.inspect(resourceMap)`. `PreparedTaskResources.blocked()` returns whether any issue is blocking. Preserve `validate(resourceMap)` as a compatibility wrapper that throws the first blocking issue.

- [ ] **Step 4: Make create and start reuse preflight rules**

`TaskService.create` must build the internal resource map from `deviceBindings`; legacy `resourceMap` is accepted only when `deviceBindings` is absent. `TaskService.start` reruns the same model/resource/readiness/constraint checks to prevent time-of-check/time-of-use drift.

```java
public TaskPreflightResponse preflight(TaskPreflightRequest request) {
    PreparedTaskResources resources = resourceService.prepare(
            request.flowModelId(), request.deviceBindings());
    List<WorkflowIssue> issues = new ArrayList<>(resources.issues());
    if (!resources.blocked()) {
        issues.addAll(executionReadinessService.inspect(resources.resourceMap()));
        issues.addAll(taskConstraintService.inspect(request.flowModelId(), request.taskVariables(),
                resources.resourceMap(), request.taskConstraints()));
    }
    boolean ready = issues.stream().noneMatch(WorkflowIssue::blocking);
    return new TaskPreflightResponse(ready, List.copyOf(issues));
}

public Task create(TaskCreateRequest request) {
    TaskPreflightRequest input = TaskPreflightRequest.from(request);
    TaskPreflightResponse result = preflight(input);
    requireReady(result);
    JsonNode resourceMap = resourceService.prepare(
            request.getFlowModelId(), request.getDeviceBindings()).resourceMap();
    return persistPendingTask(request, resourceMap);
}
```

Extract the existing insert/log/publish block into `private Task persistPendingTask(TaskCreateRequest request, JsonNode resourceMap)` and add `private void requireReady(TaskPreflightResponse response)` that throws the first blocking issue message. Add `TaskConstraintService.inspect(Long flowModelId, JsonNode taskVariables, JsonNode resourceMap, JsonNode constraints)` as a non-mutating wrapper that returns one structured issue per invalid constraint reference.

- [ ] **Step 5: Add `POST /api/task/preflight` and run tests**

```bash
mvn -Dtest=WorkflowExecutionReadinessServiceTest,WorkflowTaskResourceServiceTest,TaskConstraintServiceTest,TaskServiceTest test
```

Expected: PASS; preflight is non-mutating and start remains authoritative.

- [ ] **Step 6: Commit structured preflight**

```bash
git add Backend/src/main/java/com/smartlab/management/dto/workflow/TaskDeviceBindingRequest.java Backend/src/main/java/com/smartlab/management/dto/workflow/TaskPreflightRequest.java Backend/src/main/java/com/smartlab/management/dto/workflow/TaskPreflightResponse.java Backend/src/main/java/com/smartlab/management/dto/workflow/TaskCreateRequest.java Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowTaskResourceService.java Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowExecutionReadinessService.java Backend/src/main/java/com/smartlab/management/service/db/constraint/TaskConstraintService.java Backend/src/main/java/com/smartlab/management/service/db/workflow/TaskService.java Backend/src/main/java/com/smartlab/management/controller/workflow/TaskController.java Backend/src/test/java/com/smartlab/management/service/db/workflow/WorkflowExecutionReadinessServiceTest.java Backend/src/test/java/com/smartlab/management/service/db/constraint/TaskConstraintServiceTest.java Backend/src/test/java/com/smartlab/management/service/db/workflow/TaskServiceTest.java
git commit -m "feat(task): add model-driven preflight"
```

### Task 5: Move Frontend Workflow Authority to Server Responses

**Files:**
- Create: `Frontend/src/services/workflowApi.js`
- Create: `Frontend/src/utils/workflowAuthoring.js`
- Modify: `Frontend/src/utils/workflowNodeDefinition.js:1-330`
- Modify: `Frontend/src/utils/workflowCanvas.js:205-240`
- Test: `Frontend/tests/workflow-authoring.test.mjs`
- Modify test: `Frontend/tests/workflowNodeDefinition.test.mjs`
- Modify test: `Frontend/tests/final-review-workflow-contract.test.mjs`

**Interfaces:**
- Consumes: normalized definitions/issues from Tasks 1-2.
- Produces: `toAuthoringPayload`, `adoptPreparedWorkflow`, `indexWorkflowIssues`, and transport functions used by the designer.

- [ ] **Step 1: Write failing frontend authority tests**

```js
test('authoring payload preserves stable system keys but server response replaces system values', () => {
  const payload = toAuthoringPayload(editorWorkflow)
  assert.equal(payload.nodesDef[0].lifecycle._systemKey, 'start.lifecycle')
  assert.equal(payload.nodesDef[0].lifecycle.states.includes('HACKED'), false)

  const adopted = adoptPreparedWorkflow({ definition: canonicalWorkflow, issues: [] })
  assert.deepEqual(adopted.nodesDef, canonicalWorkflow.nodesDef)
})

test('issues are indexed by model element instead of raw JSON path', () => {
  const index = indexWorkflowIssues([{ elementType: 'PORT', elementId: 'temperatureOut', blocking: true }])
  assert.equal(index.byElement.PORT.temperatureOut.length, 1)
})
```

- [ ] **Step 2: Run frontend workflow tests and verify failure**

Run from `Frontend`:

```bash
node --test tests/workflow-authoring.test.mjs tests/workflowNodeDefinition.test.mjs tests/final-review-workflow-contract.test.mjs
```

Expected: FAIL because the authoring utility and service do not exist.

- [ ] **Step 3: Implement the workflow transport adapter**

```js
export const workflowApi = {
  list: () => axios.get('/api/workflow/list'),
  detail: id => axios.get(`/api/workflow/detail/${id}`),
  saveDraft: definition => axios.post('/api/workflow/draft', definition),
  publish: definition => axios.post('/api/workflow/publish', definition),
  requirements: id => axios.get(`/api/workflow/${id}/requirements`),
}
```

`toAuthoringPayload` may remove editor-only position fields, but must not synthesize lifecycle/actions or treat the frontend template as authoritative. Node creation may clone backend metadata solely to render a locked preview.

- [ ] **Step 4: Refactor node utilities around business edits**

Keep pure operations for capability replacement, business variable/port/action/trigger editing, and fast connection validation. Remove the requirement that frontend validation prove the full system skeleton; publish validity comes from backend issues.

- [ ] **Step 5: Run focused frontend tests**

```bash
node --test tests/workflow-authoring.test.mjs tests/workflowNodeDefinition.test.mjs tests/workflowCanvas.test.mjs tests/final-review-workflow-contract.test.mjs
```

Expected: PASS.

- [ ] **Step 6: Commit the frontend model boundary**

```bash
git add Frontend/src/services/workflowApi.js Frontend/src/utils/workflowAuthoring.js Frontend/src/utils/workflowNodeDefinition.js Frontend/src/utils/workflowCanvas.js Frontend/tests/workflow-authoring.test.mjs Frontend/tests/workflowNodeDefinition.test.mjs Frontend/tests/final-review-workflow-contract.test.mjs
git commit -m "refactor(workflow-ui): adopt backend canonical model"
```

### Task 6: Rebuild the Designer around Visible Contracts and Locked System Behavior

**Files:**
- Create: `Frontend/src/components/task/workflow/WorkflowInterfacesPortsPanel.vue`
- Create: `Frontend/src/components/task/workflow/WorkflowLifecyclePanel.vue`
- Modify: `Frontend/src/components/task/workflow/WorkflowNodeInspector.vue`
- Modify: `Frontend/src/components/task/workflow/WorkflowVariablesPortsPanel.vue`
- Modify: `Frontend/src/components/task/workflow/WorkflowTriggersActionsPanel.vue`
- Modify: `Frontend/src/components/task/workflow/WorkflowCanvasNode.vue`
- Modify: `Frontend/src/views/task/WorkflowDesigner.vue`
- Create test: `Frontend/tests/workflow-designer-structure.test.mjs`

**Interfaces:**
- Consumes: Task 5 utilities and backend preparation response.
- Produces: draft/publish commands, visible interface/port editing, locked lifecycle/system items, and element-level issue navigation.

- [ ] **Step 1: Write failing designer-structure tests**

```js
test('designer exposes interfaces and ports but does not expose transport implementation', () => {
  assert.match(inspectorSource, /接口与端口/)
  assert.match(inspectorSource, /生命周期/)
  assert.doesNotMatch(designerSource, /resourceMap|NODE_TO_DEVICE|DEVICE_TO_NODE|HTTP|消息主题/)
})

test('system lifecycle and actions are locked while business actions remain editable', () => {
  assert.match(lifecycleSource, /isSystemItem/)
  assert.match(triggerActionSource, /新增 UPDATE/)
})
```

- [ ] **Step 2: Run the structure test and verify failure**

```bash
node --test tests/workflow-designer-structure.test.mjs
```

Expected: FAIL because the new panels and copy are absent.

- [ ] **Step 3: Implement the inspector composition**

Use these tabs and responsibilities:

```vue
<el-tab-pane label="基础配置" name="basic" />
<el-tab-pane label="接口与端口" name="contracts">
  <WorkflowInterfacesPortsPanel
    :node="node"
    :port-connections="portConnections"
    @update:node="$emit('update:node', $event)"
    @update:port-connections="$emit('update:portConnections', $event)"
  />
</el-tab-pane>
<el-tab-pane label="变量与数据映射" name="data" />
<el-tab-pane label="触发与动作" name="behavior" />
<el-tab-pane label="生命周期" name="lifecycle">
  <WorkflowLifecyclePanel :lifecycle="node.lifecycle" readonly />
</el-tab-pane>
```

Required system interfaces/ports/actions/triggers show a lock and reject edit/delete. Business items remain editable. Canvas handles continue to display actual interface/port names.

- [ ] **Step 4: Split orchestration from `WorkflowDesigner.vue`**

Replace inline Axios calls with `workflowApi`; add separate “保存草稿” and “检查并发布” commands. Adopt `response.definition` after both calls, index `response.issues`, focus the first blocking element, and use business copy such as “设备状态关系” rather than connection enum names.

- [ ] **Step 5: Run workflow UI tests and build**

```bash
node --test tests/workflow-designer-structure.test.mjs tests/workflow-authoring.test.mjs tests/workflowCanvas.test.mjs tests/workflowNodeDefinition.test.mjs
npm run build
```

Expected: all tests PASS and Vite build succeeds.

- [ ] **Step 6: Commit the model-driven designer**

```bash
git add Frontend/src/components/task/workflow/WorkflowInterfacesPortsPanel.vue Frontend/src/components/task/workflow/WorkflowLifecyclePanel.vue Frontend/src/components/task/workflow/WorkflowNodeInspector.vue Frontend/src/components/task/workflow/WorkflowVariablesPortsPanel.vue Frontend/src/components/task/workflow/WorkflowTriggersActionsPanel.vue Frontend/src/components/task/workflow/WorkflowCanvasNode.vue Frontend/src/views/task/WorkflowDesigner.vue Frontend/tests/workflow-designer-structure.test.mjs
git commit -m "feat(workflow-ui): expose model contracts in designer"
```

### Task 7: Replace Frontend Route Parsing with Requirements and Preflight

**Files:**
- Create: `Frontend/src/services/taskApi.js`
- Create: `Frontend/src/components/task/TaskCreateDrawer.vue`
- Create: `Frontend/src/components/task/TaskPreflightPanel.vue`
- Modify: `Frontend/src/components/task/TaskResourceBindingCanvas.vue`
- Modify: `Frontend/src/utils/taskResourceBindings.js`
- Modify: `Frontend/src/views/task/TaskList.vue`
- Modify test: `Frontend/tests/taskResourceBindings.test.js`
- Modify test: `Frontend/tests/workflow-execution.test.mjs`
- Create test: `Frontend/tests/task-create-structure.test.mjs`

**Interfaces:**
- Consumes: backend `DeviceBindingRequirement[]` and `TaskPreflightResponse`.
- Produces: `TaskDeviceBindingRequest[]` and task-create payload with no frontend-built `resourceMap`.

- [ ] **Step 1: Replace existing expansion tests with requirement-consumption tests**

```js
test('serializes only backend requirement slots and selected instances', () => {
  const result = buildDeviceBindings(requirements, { '1:12/2:31': 55 })
  assert.deepEqual(result, [{ slotId: '1:12/2:31', deviceInstanceId: 55 }])
})

test('does not derive slots from workflow interface connections', () => {
  assert.equal(taskResourceSource.includes('NODE_TO_DEVICE'), false)
  assert.equal(taskResourceSource.includes('DEVICE_TO_NODE'), false)
})
```

- [ ] **Step 2: Run task frontend tests and verify failure**

```bash
node --test tests/taskResourceBindings.test.js tests/workflow-execution.test.mjs tests/task-create-structure.test.mjs
```

Expected: FAIL until the old recursive parser is removed.

- [ ] **Step 3: Implement `taskApi` and pure binding helpers**

```js
export const taskApi = {
  preflight: payload => axios.post('/api/task/preflight', payload),
  create: payload => axios.post('/api/task/save', payload),
  start: id => axios.post(`/api/task/start/${id}`),
  executionView: id => axios.get(`/api/task/${id}/execution-view`),
}

export function buildDeviceBindings(requirements, selections) {
  return requirements
    .filter(item => selections[item.slotId] != null)
    .map(item => ({ slotId: item.slotId, deviceInstanceId: Number(selections[item.slotId]) }))
}
```

- [ ] **Step 4: Implement the three-step create drawer**

Step 1 selects an ACTIVE model and task variables. Step 2 fetches requirements and binds compatible instances using node/flow/device-model/capability labels. Step 3 calls preflight, renders every issue with correction guidance, and enables create only when `ready` is true. Remove “设备路由”“状态机接口”“实例化路径”和 raw key `<code>` displays.

- [ ] **Step 5: Integrate into `TaskList.vue`, run tests, and build**

```bash
node --test tests/taskResourceBindings.test.js tests/workflow-execution.test.mjs tests/task-create-structure.test.mjs
npm run build
```

Expected: PASS; task payload includes `deviceBindings`, not `resourceMap`.

- [ ] **Step 6: Commit task creation UX**

```bash
git add Frontend/src/services/taskApi.js Frontend/src/components/task/TaskCreateDrawer.vue Frontend/src/components/task/TaskPreflightPanel.vue Frontend/src/components/task/TaskResourceBindingCanvas.vue Frontend/src/utils/taskResourceBindings.js Frontend/src/views/task/TaskList.vue Frontend/tests/taskResourceBindings.test.js Frontend/tests/workflow-execution.test.mjs Frontend/tests/task-create-structure.test.mjs
git commit -m "feat(task-ui): create tasks from model requirements"
```

### Task 8: Project Runtime State Back onto Workflow Nodes

**Files:**
- Create: `Backend/src/main/java/com/smartlab/management/dto/workflow/TaskNodeExecutionView.java`
- Create: `Backend/src/main/java/com/smartlab/management/dto/workflow/TaskExecutionView.java`
- Create: `Backend/src/main/java/com/smartlab/management/service/db/workflow/TaskExecutionViewService.java`
- Modify: `Backend/src/main/java/com/smartlab/management/controller/workflow/TaskController.java`
- Create test: `Backend/src/test/java/com/smartlab/management/service/db/workflow/TaskExecutionViewServiceTest.java`
- Create: `Frontend/src/components/task/TaskExecutionView.vue`
- Modify: `Frontend/src/views/task/TaskList.vue`
- Create test: `Frontend/tests/task-execution-view.test.mjs`

**Interfaces:**
- Consumes: `TASK_STEP`, compiled workflow node metadata, task bindings, constraint results, and observation snapshots.
- Produces: `GET /api/task/{id}/execution-view` with model-node-aligned status and business data.

- [ ] **Step 1: Write failing backend projection tests**

```java
@Test
void mapsNestedStepsToModelNodesAndKeepsInternalMessageIdsOutOfDefaultView() {
    TaskExecutionView result = service.get(9L);
    TaskNodeExecutionView device = result.nodes().stream()
            .filter(node -> node.nodeName().equals("加热")).findFirst().orElseThrow();
    assertEquals("RUNNING", device.status());
    assertEquals("heater-01", device.deviceName());
    assertFalse(device.outputs().has("messageId"));
}
```

- [ ] **Step 2: Run the projection test and verify failure**

```bash
mvn -Dtest=TaskExecutionViewServiceTest test
```

Expected: FAIL because projection types/service do not exist.

- [ ] **Step 3: Implement the read model**

```java
public record TaskNodeExecutionView(
        Long stepId, Long flowModelId, Integer flowVersion, long nodeIdRef,
        String occurrencePath, String nodeName, String nodeType, String status,
        String deviceName, String capabilityName, JsonNode inputs, JsonNode outputs,
        OffsetDateTime startTime, OffsetDateTime endTime, Long durationMs,
        List<WorkflowIssue> issues) {}

public record TaskExecutionView(
        Long taskId, String taskName, String status, int completedNodes,
        int totalNodes, List<TaskNodeExecutionView> nodes) {}
```

Use `flowModelId + nodeIdRef + TASK_STEP parent chain` as the join. Filter internal correlation fields from default `inputs/outputs`; retain them only in existing diagnostic logs.

- [ ] **Step 4: Add endpoint and frontend component test**

```js
test('runtime view renders model node, bound device, capability and failure guidance', () => {
  assert.match(source, /nodeName/)
  assert.match(source, /deviceName/)
  assert.match(source, /capabilityName/)
  assert.doesNotMatch(source, /messageId|resourceMap|bindingKey/)
})
```

- [ ] **Step 5: Implement `TaskExecutionView.vue` and integrate details**

Show workflow-node status, progress, inputs/outputs, bound device, constraint/runtime issues, and timestamps. Keep raw execution logs in the existing separate diagnostic tab.

- [ ] **Step 6: Run backend/frontend focused tests**

```bash
mvn -Dtest=TaskExecutionViewServiceTest,TaskServiceTest,WorkflowEngineExecutionTest test
```

```bash
node --test tests/task-execution-view.test.mjs tests/workflow-execution.test.mjs
npm run build
```

Expected: PASS.

- [ ] **Step 7: Commit model-aligned runtime feedback**

```bash
git add Backend/src/main/java/com/smartlab/management/dto/workflow/TaskNodeExecutionView.java Backend/src/main/java/com/smartlab/management/dto/workflow/TaskExecutionView.java Backend/src/main/java/com/smartlab/management/service/db/workflow/TaskExecutionViewService.java Backend/src/main/java/com/smartlab/management/controller/workflow/TaskController.java Backend/src/test/java/com/smartlab/management/service/db/workflow/TaskExecutionViewServiceTest.java Frontend/src/components/task/TaskExecutionView.vue Frontend/src/views/task/TaskList.vue Frontend/tests/task-execution-view.test.mjs
git commit -m "feat(task): align execution feedback with workflow model"
```

### Task 9: Legacy Compatibility and Full-Chain Verification

**Files:**
- Modify: `Backend/src/test/java/com/smartlab/management/service/db/workflow/WorkflowServiceTest.java`
- Modify: `Backend/src/test/java/com/smartlab/management/service/db/workflow/WorkflowTaskResourceServiceTest.java`
- Modify: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowEngineExecutionTest.java`
- Create: `Backend/src/test/java/com/smartlab/management/service/db/workflow/WorkflowModelToTaskExecutionTest.java`
- Modify: `Frontend/tests/final-review-workflow-contract.test.mjs`
- Create: `Frontend/tests/workflow-full-chain-copy.test.mjs`

**Interfaces:**
- Consumes: all prior tasks.
- Produces: regression proof for old definitions/tasks and one executable design-to-completion scenario.

- [ ] **Step 1: Add a failing backend full-chain test**

The fixture must execute this exact scenario: save markerless legacy definition → reopen canonical editor view → publish → fetch requirements → preflight bindings → create/start task → execute a device node → accept same-message completion → reach END → read execution view.

```java
@Test
void runsPublishedModelFromAuthoringThroughObservedCompletion() {
    WorkflowPreparationResponse published = workflows.publish(legacyHeatingWorkflow());
    WorkflowResourceRequirementsResponse requirements = resources.requirements(published.definition().getId());
    assertTrue(tasks.preflight(boundRequest(requirements, 55L)).ready());
    Task task = tasks.create(createRequest(requirements, 55L));
    tasks.start(task.getId());
    engine.tick();
    deviceEvents.complete(lastCommandMessageId());
    engine.tick();
    assertEquals("SUCCEEDED", executionViews.get(task.getId()).status());
}
```

- [ ] **Step 2: Run the full-chain test and fix only discovered integration gaps**

```bash
mvn -Dtest=WorkflowModelToTaskExecutionTest test
```

Expected before fixes: FAIL at the first mismatched DTO/service boundary. Apply the smallest production change in the owning task files, then rerun until PASS.

- [ ] **Step 3: Add a frontend forbidden-copy regression test**

```js
test('ordinary workflow and task UI hides internal transport vocabulary', () => {
  for (const source of ordinaryUserSources) {
    assert.doesNotMatch(source, /resourceMap|bindingKey|NODE_TO_DEVICE|DEVICE_TO_NODE|HTTP接口|消息主题/)
  }
})
```

- [ ] **Step 4: Run all workflow/task frontend tests**

```bash
node --test tests/workflow-authoring.test.mjs tests/workflow-designer-structure.test.mjs tests/workflowNodeDefinition.test.mjs tests/workflowCanvas.test.mjs tests/final-review-workflow-contract.test.mjs tests/taskResourceBindings.test.js tests/workflow-execution.test.mjs tests/task-create-structure.test.mjs tests/task-execution-view.test.mjs tests/workflow-full-chain-copy.test.mjs
npm run build
```

Expected: every test PASS and production build succeeds.

- [ ] **Step 5: Run all backend tests**

```bash
mvn test
```

Expected: BUILD SUCCESS with no workflow, state-machine, constraint, observation, or adapter regression.

- [ ] **Step 6: Inspect the final diff and commit integration coverage**

```bash
git diff --check
git status --short
git add Backend/src/test/java/com/smartlab/management/service/db/workflow/WorkflowServiceTest.java Backend/src/test/java/com/smartlab/management/service/db/workflow/WorkflowTaskResourceServiceTest.java Backend/src/test/java/com/smartlab/engine/workflow/WorkflowEngineExecutionTest.java Backend/src/test/java/com/smartlab/management/service/db/workflow/WorkflowModelToTaskExecutionTest.java Frontend/tests/final-review-workflow-contract.test.mjs Frontend/tests/workflow-full-chain-copy.test.mjs
git commit -m "test(workflow): cover model-to-task execution chain"
```

## Completion Evidence

Before claiming completion, capture and report:

- Backend `mvn test` suite count and failure count.
- Frontend workflow/task Node test count and failure count.
- Frontend `npm run build` result.
- One full-chain test showing model publish, requirements, preflight, task start, device feedback, constraint/observation association, and successful completion.
- `git status --short` proving unrelated pre-existing changes were not staged or overwritten.

