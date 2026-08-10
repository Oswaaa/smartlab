# Workflow Task Parallelism, Subflow Scheduling, and Interface Snapshots Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace workflow routing envelopes with schema-governed per-interface current-value arrays, add same-task subflow scheduling and completion notification, and run different tasks concurrently through a configurable 100ms soft-real-time dispatcher.

**Architecture:** `WorkflowEngine` remains the single workflow interpreter. A short scheduled dispatcher submits distinct task IDs to a dedicated bounded executor; each worker performs exactly one serialized task poll and releases the thread. Interface snapshots are manipulated only through a focused immutable helper, while subflow steps retain the parent task ID and use `PARENT_STEP_ID`/`STEP_DEPTH` for nesting.

**Tech Stack:** Java 17, Spring Boot scheduling and executors, MyBatis-Plus, Jackson, JUnit 5, Mockito, AssertJ, JSON Schema Draft-07.

## Global Constraints

- Preserve the user-edited `Backend/src/main/resources/schemas/协议规范.json` addition of `SUBFLOW_COMPLETED`; do not overwrite unrelated schema content.
- Runtime signal and snapshot field names must come from `协议规范.json`, `工作流模型.json`, and `系统执行规范.json`; do not retain routing-envelope aliases.
- `INTERFACE_IN_SNAPSHOT` and `INTERFACE_OUT_SNAPSHOT` are arrays containing one current-value item per declared interface in model order.
- An unassigned interface is `{ "interfaceName": "...", "signalName": null }`; a null signal must not retain payload.
- Do not persist `sourceNodeIdRef`, `sourceInterface`, `sourceInterfaceName`, `targetInterfaceName`, `inputSignalName`, `inputPayload`, or `sourceOutput` in interface snapshots.
- Trigger evaluation is interface-local and exposes `signalName` and `payload` for the interface currently being traversed.
- `VARIABLE_SPACE` keeps user internal variables plus the existing `_triggerStates` system reservation only; do not move lifecycle, device IDs, message IDs, or routing metadata into it.
- A subflow uses the parent task ID and is not a new `TASK`.
- Different tasks may execute concurrently; a single task, including all of its subflows and branches, remains sequential.
- Poll interval is configurable with `smartlab.workflow.poll-interval-ms` and defaults to `100` milliseconds.
- No database DDL change and no new runtime dependency.

---

### Task 1: Canonical interface snapshot contract and immutable helper

**Files:**
- Modify: `Backend/src/main/resources/schemas/系统执行规范.json`
- Preserve/complete: `Backend/src/main/resources/schemas/协议规范.json`
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowInterfaceSnapshots.java`
- Create: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowInterfaceSnapshotsTest.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowRuntimeService.java`
- Modify: `Backend/src/test/java/com/smartlab/management/service/db/workflow/WorkflowRuntimeServiceTest.java`

**Interfaces:**
- Consumes: node `interfaces` arrays and existing `TASK_STEP` snapshot JSON.
- Produces: `WorkflowInterfaceSnapshots.initialize(JsonNode interfaces, String direction)`, `withSignal(JsonNode current, JsonNode interfaces, String direction, String interfaceName, String signalName, JsonNode payload)`, and `find(JsonNode snapshot, String interfaceName)`.

- [ ] **Step 1: Write failing helper tests for complete arrays and replacement semantics**

Add tests that require model-order initialization, null signals, isolated replacement, payload clearing, and rejection of unknown interfaces, wrong directions, and signals absent from `allowedSignals`.

```java
@Test
void initializesEveryInterfaceAndReplacesOnlyTheTargetValue() {
    ArrayNode initial = WorkflowInterfaceSnapshots.initialize(interfaces(), "IN");
    assertThat(initial).hasSize(2);
    assertThat(initial.get(0).path("interfaceName").asText()).isEqualTo("workflow-in");
    assertThat(initial.get(0).path("signalName").isNull()).isTrue();

    ArrayNode updated = WorkflowInterfaceSnapshots.withSignal(initial, interfaces(), "IN",
            "state-in", "CMD_STATE", objectNode().put("stateName", "COMPLETED"));
    assertThat(updated.get(0)).isEqualTo(initial.get(0));
    assertThat(WorkflowInterfaceSnapshots.find(updated, "state-in").path("signalName").asText())
            .isEqualTo("CMD_STATE");
}

@Test
void signalWithoutPayloadRemovesThePreviousPayload() {
    ArrayNode updated = WorkflowInterfaceSnapshots.withSignal(snapshotWithPayload(), interfaces(),
            "IN", "workflow-in", "SUBFLOW_COMPLETED", null);
    assertThat(WorkflowInterfaceSnapshots.find(updated, "workflow-in").has("payload")).isFalse();
}
```

- [ ] **Step 2: Run helper tests and verify RED**

Run: `cd Backend && mvn -Dtest=WorkflowInterfaceSnapshotsTest test`

Expected: FAIL because `WorkflowInterfaceSnapshots` does not exist.

- [ ] **Step 3: Add the system-execution Schema definitions**

Add `WorkflowInterfaceSnapshotItem` with required `interfaceName` and nullable required `signalName`, optional object `payload`, plus `WorkflowInterfaceSnapshot` as its array. Keep non-null signal validation delegated to the existing protocol format and backend semantic validation.

```json
"WorkflowInterfaceSnapshotItem": {
  "type": "object",
  "properties": {
    "interfaceName": { "type": "string" },
    "signalName": { "type": [ "string", "null" ] },
    "payload": { "type": "object", "additionalProperties": true }
  },
  "required": [ "interfaceName", "signalName" ]
},
"WorkflowInterfaceSnapshot": {
  "type": "array",
  "items": { "$ref": "#/definitions/WorkflowInterfaceSnapshotItem" }
}
```

- [ ] **Step 4: Implement the immutable snapshot helper**

Return deep-copied `ArrayNode` values, validate that the named interface exists with the requested direction, reject a non-null signal absent from that interface's `allowedSignals`, preserve declared interface order, and remove payload when the new payload is absent.

```java
public final class WorkflowInterfaceSnapshots {
    public static ArrayNode initialize(JsonNode interfaces, String direction) {
        ArrayNode result = JsonNodeSupport.arrayNode();
        if (interfaces == null || !interfaces.isArray()) return result;
        for (JsonNode definition : interfaces) {
            if (!direction.equals(definition.path("direction").asText())) continue;
            ObjectNode slot = result.addObject();
            slot.put("interfaceName", definition.path("name").asText());
            slot.putNull("signalName");
        }
        return result;
    }

    public static ArrayNode withSignal(JsonNode current, JsonNode interfaces, String direction,
                                       String interfaceName, String signalName, JsonNode payload) {
        requireDeclaredInterface(interfaces, direction, interfaceName);
        ArrayNode result = current != null && current.isArray()
                ? (ArrayNode) current.deepCopy() : initialize(interfaces, direction);
        JsonNode found = find(result, interfaceName);
        if (!found.isObject()) throw new IllegalStateException("接口快照缺少接口: " + interfaceName);
        ObjectNode slot = (ObjectNode) found;
        if (signalName == null) slot.putNull("signalName");
        else slot.put("signalName", signalName);
        if (payload == null || payload.isMissingNode() || payload.isNull()) slot.remove("payload");
        else if (payload.isObject()) slot.set("payload", payload.deepCopy());
        else throw new IllegalArgumentException("接口信号payload必须是对象");
        return result;
    }

    public static JsonNode find(JsonNode snapshot, String interfaceName) {
        if (snapshot != null && snapshot.isArray()) {
            for (JsonNode slot : snapshot) {
                if (interfaceName.equals(slot.path("interfaceName").asText())) return slot;
            }
        }
        return MissingNode.getInstance();
    }
}
```

- [ ] **Step 5: Write failing runtime creation tests**

Assert `createStep()` initializes complete IN and OUT arrays from `FlowNode.interfaces`, ignores old object-envelope defaults, and persists no routing metadata.

```java
@Test
void createStepInitializesCanonicalInputAndOutputInterfaceArrays() {
    TaskStep created = service.createStep(task, nodeWithInAndOutInterfaces(), null, 0, null);
    assertThat(created.getInterfaceInSnapshot().isArray()).isTrue();
    assertThat(created.getInterfaceOutSnapshot().isArray()).isTrue();
    assertThat(created.getInterfaceInSnapshot().get(0).path("signalName").isNull()).isTrue();
}
```

- [ ] **Step 6: Run runtime tests and verify RED**

Run: `cd Backend && mvn -Dtest=WorkflowRuntimeServiceTest test`

Expected: FAIL because `createStep()` currently stores an object input and leaves the output snapshot unset.

- [ ] **Step 7: Initialize both snapshots in `createStep()` and rerun focused tests**

Use `WorkflowInterfaceSnapshots.initialize(node.getInterfaces(), "IN")` and `initialize(..., "OUT")`; keep the existing method signature temporarily so callers can be migrated in Task 2, but reject non-null non-array initial snapshots.

Run: `cd Backend && mvn -Dtest=WorkflowInterfaceSnapshotsTest,WorkflowRuntimeServiceTest test`

Expected: PASS.

- [ ] **Step 8: Commit the snapshot contract slice**

Stage the two Schema files, helper, runtime creation logic, and focused tests. Commit message: `feat(workflow): define canonical interface snapshots`.

---

### Task 2: Interface-local trigger context, routing, and device correlation

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowEngine.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/DefaultWorkflowExecutionOperations.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowTaskControlService.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowRuntimeService.java`
- Modify: `Backend/src/main/java/com/smartlab/global/contract/WorkflowNodeSystemContract.java`
- Modify: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowEngineExecutionTest.java`
- Modify: `Backend/src/test/java/com/smartlab/engine/workflow/DefaultWorkflowExecutionOperationsTest.java`
- Modify: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowTaskControlServiceTest.java`
- Modify: `Backend/src/test/java/com/smartlab/global/contract/WorkflowNodeSystemContractTest.java`

**Interfaces:**
- Consumes: canonical snapshot arrays from Task 1 and state-machine events using `SystemSignalFormat`.
- Produces: interface-local trigger variables, canonical IN/OUT signal persistence, deterministic device message IDs, `WorkflowRuntimeService.runningDeviceSteps()`, and event correlation by `taskStepId` or resource mapping.

- [ ] **Step 1: Write failing interface-local evaluation and routing tests**

Prove two IN interfaces retain different current signals, each trigger sees only its host interface's `signalName/payload`, workflow EMIT updates only its target OUT slot, and routing updates only the connected target IN slot.

```java
@Test
void evaluatesEachTriggerAgainstItsOwnInterfaceCurrentValue() {
    step.setInterfaceInSnapshot(array(
            slot("workflow-in", "ACTIVE", null),
            slot("state-in", "CMD_STATE", objectNode().put("stateName", "COMPLETED"))));
    engine.processTask(task);
    assertThat(executionOrder).containsExactly("workflow-action", "device-complete-action");
}

@Test
void routingPersistsNoEnvelopeFields() {
    engine.processTask(task);
    JsonNode written = capturedInputSnapshot();
    assertThat(written.isArray()).isTrue();
    assertThat(written.toString()).doesNotContain("sourceNodeIdRef", "targetInterfaceName",
            "inputSignalName", "inputPayload", "sourceOutput");
}
```

- [ ] **Step 2: Run engine tests and verify RED**

Run: `cd Backend && mvn -Dtest=WorkflowEngineExecutionTest test`

Expected: FAIL because the engine reads one object-level `inputSignalName`, persists envelopes, and writes one output object.

- [ ] **Step 3: Refactor node snapshot construction and routing**

Build one immutable base snapshot per node poll. For each traversed interface, copy the base and inject only that interface item's `signalName` and `payload`. Replace `routeEmission()` and `route()` object writes with `WorkflowInterfaceSnapshots.withSignal(...)`; never use the complete frozen evaluation snapshot as a protocol payload.

```java
ObjectNode interfaceVariables = frozenSnapshot.deepCopy();
JsonNode current = WorkflowInterfaceSnapshots.find(snapshotFor(interfaceNode, step), interfaceName);
interfaceVariables.set("signalName", current.path("signalName").deepCopy());
if (current.has("payload")) interfaceVariables.set("payload", current.path("payload").deepCopy());
else interfaceVariables.remove("payload");
```

- [ ] **Step 4: Replace system-template condition aliases**

Change workflow and device input trigger conditions from `inputSignalName`/signal-specific boolean aliases to `signalName`; use payload paths such as `payload.stateName` for CMD state conditions. Update contract tests to reject old aliases in generated templates.

- [ ] **Step 5: Write failing deterministic message-correlation tests**

Assert `ensureMessageId()` returns the same UUID for the same task step without writing message/device/capability metadata into `VARIABLE_SPACE` or interface snapshots; task termination obtains the same ID through `ensureMessageId()`; CMD_STATE uses `executionContext.taskStepId` and validates the current step.

```java
@Test
void messageIdIsStableWithoutSnapshotMetadata() {
    String first = operations.ensureMessageId(step, 21L, "mix");
    String second = operations.ensureMessageId(step, 21L, "mix");
    assertThat(second).isEqualTo(first);
    assertThat(step.getInterfaceInSnapshot().toString()).doesNotContain("messageId", "deviceInstanceId");
}
```

- [ ] **Step 6: Run correlation tests and verify RED**

Run: `cd Backend && mvn -Dtest=DefaultWorkflowExecutionOperationsTest,WorkflowTaskControlServiceTest,WorkflowEngineExecutionTest test`

Expected: FAIL because message correlation is currently stored as object-level input snapshot metadata and CMD_STATE lookup scans that metadata.

- [ ] **Step 7: Implement deterministic correlation and canonical device-event writes**

Derive the message ID with `UUID.nameUUIDFromBytes()` from task ID, step ID, device instance ID, and capability name. Expose `WorkflowRuntimeService.runningDeviceSteps()` as the status-filtered candidate list. Use `executionContext.taskStepId` for command events; for OP_STATE without a task-step context, filter those candidates through the existing resource mapping boundary. Persist the exact state-machine signal name and protocol payload into the target STATE IN slot.

```java
String seed = "workflow:" + step.getTaskId() + ":" + step.getId() + ":"
        + deviceInstanceId + ":" + capabilityName;
return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
```

- [ ] **Step 8: Run focused engine, operation, control, and contract tests**

Run: `cd Backend && mvn -Dtest=WorkflowEngineExecutionTest,DefaultWorkflowExecutionOperationsTest,WorkflowTaskControlServiceTest,WorkflowNodeSystemContractTest test`

Expected: PASS.

- [ ] **Step 9: Commit the interface-runtime slice**

Stage only engine routing/context, message correlation, system templates, and focused tests. Commit message: `feat(workflow): persist interface-local signal values`.

---

### Task 3: Same-task subflow orchestration and completion notification

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowEngine.java`
- Modify: `Backend/src/main/java/com/smartlab/global/contract/WorkflowNodeSystemContract.java`
- Modify: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowEngineExecutionTest.java`
- Modify: `Backend/src/test/java/com/smartlab/global/contract/WorkflowNodeSystemContractTest.java`

**Interfaces:**
- Consumes: canonical parent input snapshots, `FlowNode.subFlowModelId`, `TASK_STEP.PARENT_STEP_ID`, and `STEP_DEPTH`.
- Produces: `ensureSubflowStarted(Task task, TaskStep parent, FlowNode node)` and `settleSuccessfulEnd(Task task, TaskStep endStep, FlowNode endNode)` semantics.

- [ ] **Step 1: Write failing subflow-start tests**

Assert only a RUNNING `SUBFLOW_NODE` creates its child START, the child reuses the task ID, parent step ID, and incremented depth, repeated polls reuse the existing child, and a child created during this poll is not evaluated from the already-frozen step list.

```java
@Test
void runningSubflowCreatesOneNestedStartStepInTheSameTask() {
    parent.setNodeStatus("RUNNING");
    parent.setStepDepth(1);
    engine.processTask(task);
    verify(runtime).createStep(eq(task), eq(childStart), eq(parent.getId()), eq(2), isNull());
    assertThat(parent.getTaskId()).isEqualTo(task.getId());
}
```

- [ ] **Step 2: Run engine tests and verify RED**

Run: `cd Backend && mvn -Dtest=WorkflowEngineExecutionTest test`

Expected: FAIL because only the root workflow START is currently created.

- [ ] **Step 3: Implement idempotent subflow start scheduling**

During `processStep()`, when node type is `SUBFLOW_NODE` and the persisted step status is RUNNING, call the existing `startFlow(task, node.getSubFlowModelId(), step.getId(), step.getStepDepth() + 1)`. Keep `WorkflowRuntimeService.createStep()` as the idempotency boundary.

- [ ] **Step 4: Write failing END settlement tests**

Assert a root END at SUCCEEDED completes the task; a nested END at SUCCEEDED writes `SUBFLOW_COMPLETED` with no payload into the parent's `Interface_workflow_in`; it does not complete the task or directly change the parent lifecycle; the parent UPDATE trigger observes completion on the next poll only.

```java
@Test
void nestedSuccessfulEndNotifiesParentWithoutCompletingTask() {
    childEnd.setNodeStatus("SUCCEEDED");
    childEnd.setParentStepId(parent.getId());
    engine.processTask(task);
    verify(runtime).updateInputSnapshot(eq(parent), argThat(snapshot ->
            "SUBFLOW_COMPLETED".equals(WorkflowInterfaceSnapshots.find(snapshot,
                    "Interface_workflow_in").path("signalName").asText())));
    verify(runtime, never()).completeTask(task);
    verify(operations, never()).transitionNodeLifecycle(eq(task), eq(parent), any(), eq("SUCCEEDED"));
}
```

- [ ] **Step 5: Extend the SUBFLOW system template**

Ensure its workflow input allows `ACTIVE` and `SUBFLOW_COMPLETED`; keep the ACTIVE lifecycle UPDATE to RUNNING and add a separate `signalName == SUBFLOW_COMPLETED` lifecycle UPDATE to SUCCEEDED. Do not add a signal directed at the engine.

- [ ] **Step 6: Implement root/nested END settlement and rerun tests**

Settle an END only when it was already terminal at poll start, after its terminal trigger evaluation. Root END calls `completeTask`; nested END updates the parent interface snapshot and leaves the parent lifecycle to its next trigger poll.

Run: `cd Backend && mvn -Dtest=WorkflowEngineExecutionTest,WorkflowNodeSystemContractTest test`

Expected: PASS.

- [ ] **Step 7: Commit the subflow slice**

Stage subflow engine logic, template updates, and focused tests. Commit message: `feat(workflow): schedule nested subflows in parent tasks`.

---

### Task 4: Configurable 100ms task dispatcher and task-level parallelism

**Files:**
- Create: `Backend/src/main/java/com/smartlab/global/config/WorkflowEngineProperties.java`
- Modify: `Backend/src/main/java/com/smartlab/global/config/TaskExecutionConfig.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowEngine.java`
- Create: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowEngineSchedulingTest.java`
- Modify: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowEngineExecutionTest.java`
- Modify: `Backend/src/main/resources/application.yml`

**Interfaces:**
- Consumes: `WorkflowRuntimeService.runningTasks()` and existing task locks.
- Produces: a qualified `workflowEngineExecutor`, `dispatchTask(Long taskId)`, and an in-flight task-ID set that guarantees one worker per task in a single backend process.

- [ ] **Step 1: Write failing scheduling tests with controllable executors**

Use latches and a recording executor to prove different task IDs can overlap, the same task cannot be submitted twice while in flight, one worker processes one task poll only, rejection clears the claim, and one task failure does not prevent another submission.

```java
@Test
void dispatchesDifferentTasksConcurrentlyButDeduplicatesTheSameTask() throws Exception {
    engine.driveWorkflows();
    assertThat(startedTaskIds.await(1, SECONDS)).isTrue();
    assertThat(maxConcurrentTasks.get()).isGreaterThanOrEqualTo(2);

    engine.driveWorkflows();
    assertThat(submissionCountFor(task1.getId())).isEqualTo(1);
}

@Test
void rejectedSubmissionCanBeRetriedOnTheNextScan() {
    rejectingExecutor.rejectNext();
    engine.driveWorkflows();
    engine.driveWorkflows();
    assertThat(rejectingExecutor.acceptedTaskIds()).contains(task.getId());
}
```

- [ ] **Step 2: Run scheduling tests and verify RED**

Run: `cd Backend && mvn -Dtest=WorkflowEngineSchedulingTest test`

Expected: FAIL because `driveWorkflows()` currently processes every task synchronously in the scheduler thread.

- [ ] **Step 3: Add the dedicated bounded executor**

Keep the existing `workflowTaskExecutor` used by MQTT startup unchanged. Add `workflowEngineExecutor` with configurable core size, max size, queue capacity, `workflow-engine-` thread names, and `AbortPolicy` rejection.

```java
@Bean(name = "workflowEngineExecutor")
public Executor workflowEngineExecutor(WorkflowEngineProperties properties) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(properties.getCorePoolSize());
    executor.setMaxPoolSize(properties.getMaxPoolSize());
    executor.setQueueCapacity(properties.getQueueCapacity());
    executor.setThreadNamePrefix("workflow-engine-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.initialize();
    return executor;
}
```

Use configuration defaults of core size 4, max size 8, and queue capacity 200. Add the corresponding properties under `smartlab.workflow` without changing unrelated datasource or MQTT settings.

```java
@Component
@ConfigurationProperties(prefix = "smartlab.workflow")
@lombok.Data
public class WorkflowEngineProperties {
    private long pollIntervalMs = 100;
    private int corePoolSize = 4;
    private int maxPoolSize = 8;
    private int queueCapacity = 200;
}
```

- [ ] **Step 4: Implement asynchronous task dispatch and in-flight cleanup**

Change the schedule annotation to `@Scheduled(fixedDelayString = "${smartlab.workflow.poll-interval-ms:100}")`. Atomically add each task ID to `ConcurrentHashMap.newKeySet()`, submit one worker, reload task state inside the worker, process one poll, and remove the ID in `finally`. On `RejectedExecutionException`, remove the ID and log a scheduling warning without failing the task.

- [ ] **Step 5: Preserve testable synchronous construction**

Keep a package-private constructor used by existing focused tests that supplies `Runnable::run`; use the Spring `@Autowired` constructor with `@Qualifier("workflowEngineExecutor")` in production. Existing `processTask(Task)` tests remain deterministic.

- [ ] **Step 6: Run scheduling and engine tests**

Run: `cd Backend && mvn -Dtest=WorkflowEngineSchedulingTest,WorkflowEngineExecutionTest test`

Expected: PASS.

- [ ] **Step 7: Commit the task-parallel scheduling slice**

Stage executor configuration, workflow scheduler, application properties, and tests. Commit message: `feat(workflow): dispatch running tasks in parallel`.

---

### Task 5: Contract scan and complete backend regression

**Files:**
- Modify only as focused failures require: workflow fixtures and tests under `Backend/src/test`
- Modify only if implementation exposes a contradiction: `docs/superpowers/specs/2026-08-10-workflow-task-parallel-subflow-snapshot-design.md`

**Interfaces:**
- Consumes: all four implementation slices.
- Produces: one schema-valid, regression-tested workflow runtime with no old snapshot envelope writers.

- [ ] **Step 1: Parse all three JSON Schemas**

Run a focused JSON parser test or existing schema validation test over:

```text
Backend/src/main/resources/schemas/协议规范.json
Backend/src/main/resources/schemas/工作流模型.json
Backend/src/main/resources/schemas/系统执行规范.json
```

Expected: all three parse successfully and cross-file references remain resolvable by the existing loader.

- [ ] **Step 2: Search for stale runtime snapshot writers and readers**

Run:

```powershell
rg -n 'sourceNodeIdRef|sourceInterfaceName|sourceInterface|targetInterfaceName|inputSignalName|inputPayload|sourceOutput|InterfaceInSnapshot\(\)\.path\("messageId"' Backend/src/main/java/com/smartlab/engine/workflow Backend/src/main/java/com/smartlab/management/service/db/workflow
```

Expected: no workflow snapshot persistence or trigger-evaluation use remains. `targetInterfaceName` may remain only inside `WorkflowNodeAction` EMIT configuration and must not be removed from the model contract.

- [ ] **Step 3: Run focused workflow tests**

Run: `cd Backend && mvn -Dtest='com.smartlab.engine.workflow.**,com.smartlab.management.service.db.workflow.WorkflowRuntimeServiceTest,com.smartlab.global.contract.WorkflowNodeSystemContractTest' test`

Expected: all focused tests pass.

- [ ] **Step 4: Run the complete backend suite**

Run: `cd Backend && mvn test`

Expected: zero failures and zero errors. Do not treat obsolete assertions for removed envelope fields as approved failures; update or delete tests whose expectations contradict the confirmed contract.

- [ ] **Step 5: Inspect diff integrity**

Run: `git diff --check` and inspect `git status --short`. Confirm no frontend files, database migrations, unrelated protocol entries, or user-owned changes outside the approved protocol addition were modified.

- [ ] **Step 6: Commit regression-only adjustments if present**

Stage only relevant fixtures/tests. Commit message: `test(workflow): verify parallel subflow polling`.

- [ ] **Step 7: Record final evidence**

Report focused and full test results, schema validation, the effective polling default, changed files, and commits. Do not claim completion without fresh command output.
