# Workflow Node Trigger Polling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the old input-event/named-action workflow runtime with per-node polling, frozen-snapshot rising-edge triggers, inline `EMIT`/`UPDATE` actions, validated lifecycle transitions, and user-defined N-way branch outputs.

**Architecture:** The scheduler selects every pollable `TASK_STEP`, then processes one node at a time. A node poll builds one immutable evaluation snapshot, walks OUT interfaces before IN interfaces while preserving declaration order, evaluates each trigger independently, and executes every rising-edge action immediately; action effects become visible only in the next poll. Compiler and frontend normalization produce the same canonical inline action structure, while `_triggerStates` is the only system-reserved value persisted inside `VARIABLE_SPACE`.

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, Jackson, JUnit 5, Mockito, AssertJ, Vue 3, Element Plus, Node.js built-in test runner, Vite.

## Global Constraints

- Preserve the user-edited `Backend/src/main/resources/schemas/工作流模型.json`; change it only to complete the approved contract.
- `WorkflowNodeAction.actionName` is exactly `EMIT` or `UPDATE`; there is no `actionType` and no arbitrary action instance name.
- `node.actions[]` is the unique allowed action-name subset; every `bindingTriggers[].action` is a complete inline `WorkflowNodeAction` object and its `actionName` must be in that subset.
- `UPDATE/INTERNAL_VARIABLE` must contain exactly one of `payload.value` and `payload.valueExpression`.
- `UPDATE/NODE_LIFECYCLE` uses `payload.targetName`, contains neither value field, and may only persist a transition declared by `node.lifecycle.transitions`.
- The engine must not change `PENDING` to `RUNNING` merely because `ACTIVE` was received.
- Each poll uses one frozen node snapshot; action effects are not visible to later triggers until the next poll.
- Traverse every OUT interface first, then every IN interface; preserve model order within each direction and trigger order within each interface.
- Execute every rising-edge trigger independently and immediately; do not collect, deduplicate, reorder, or limit multiple `EMIT` actions.
- Persist edge state only under `TASK_STEP.VARIABLE_SPACE._triggerStates`; derive `nodeLifecycleState` from `TASK_STEP.NODE_STATUS` and never expose `_triggerStates` as a user variable or emitted payload.
- The workflow engine consumes node-level internal variables, lifecycle, interface signal/payload, and BRANCH/AGGREGATE expression results, not raw device telemetry.
- BRANCH outputs are user-defined and N-way; no runtime or compiler dependency on `Interface_true_out` or `Interface_false_out`.
- Keep backward compatibility in the canonicalizer for persisted legacy definitions, but all compiled/runtime structures must use the canonical inline format.
- No new runtime dependency or database column.

---

### Task 1: Canonical inline action contract and compiler validation

**Files:**
- Modify: `Backend/src/main/resources/schemas/工作流模型.json`
- Modify: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowDefinitionCompilerTest.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCompiler.java`
- Create: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowDefinitionCanonicalizerTest.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCanonicalizer.java`

**Interfaces:**
- Consumes: node JSON containing `actions: ["EMIT", "UPDATE"]` and inline `bindingTriggers[].action` objects.
- Produces: compiler validation for `validateAction(JsonNode node, JsonNode action, String path)` and canonical node definitions without `actionType` or string action references.

- [ ] **Step 1: Write failing compiler tests for the approved action matrix**

Add focused cases that accept an inline constant UPDATE, inline expression UPDATE, lifecycle UPDATE, OUT-interface trigger, and multiple triggers with the same `actionName`; reject an action not listed in `node.actions[]`, invalid EMIT target/signal, invalid `updateType`, missing target, both/neither `value` fields, incompatible constant types, lifecycle value fields, and undeclared lifecycle targets/transitions.

```java
@Test
void acceptsInlineConstantAndExpressionUpdatesOnAnyInterface() {
    ObjectNode definition = validDefinition();
    ObjectNode node = firstNode(definition);
    node.set("actions", array("UPDATE", "EMIT"));
    outInterface(node).set("bindingTriggers", array(
        trigger("ready", "=", true, updateConstant("ready", true)),
        trigger("temperature", ">", 100, updateExpression("scaled", "temperature / 100"))));
    assertThatCode(() -> compiler.compile(definition)).doesNotThrowAnyException();
}

@Test
void rejectsInternalVariableUpdateWithBothValueSources() {
    ObjectNode action = updateExpression("scaled", "temperature / 100");
    ((ObjectNode) action.path("payload")).put("value", 200);
    assertThatThrownBy(() -> compileWithTrigger(action))
        .hasMessageContaining("value与valueExpression必须且只能存在一个");
}
```

- [ ] **Step 2: Run compiler tests and verify RED**

Run: `cd Backend && mvn -Dtest=WorkflowDefinitionCompilerTest test`

Expected: failures show the compiler still rejects OUT triggers and still expects string action references plus `actionType`.

- [ ] **Step 3: Implement minimal compiler validation for inline actions**

Index `node.actions[]` as a `Set<String>`, validate each trigger action object in place, and dispatch payload checks by `actionName` and `updateType`. Resolve the target variable's declared `dataType` for constants: INTEGER requires an integral JSON number, DOUBLE any JSON number, STRING text, BOOLEAN boolean, JSON any JSON value. Remove the rule forbidding OUT-interface triggers and remove arbitrary action-name lookup.

```java
private void validateTriggerAction(JsonNode node, JsonNode action, Set<String> allowedActions, String path) {
    String actionName = requiredText(action, "actionName", path + ".actionName");
    if (!allowedActions.contains(actionName)) fail(path + ".actionName", "动作未在node.actions中声明");
    switch (actionName) {
        case "EMIT" -> validateEmit(node, action.path("payload"), path + ".payload");
        case "UPDATE" -> validateUpdate(node, action.path("payload"), path + ".payload");
        default -> fail(path + ".actionName", "仅支持EMIT或UPDATE");
    }
}
```

- [ ] **Step 4: Run compiler tests and verify GREEN**

Run: `cd Backend && mvn -Dtest=WorkflowDefinitionCompilerTest test`

Expected: all compiler tests pass.

- [ ] **Step 5: Write failing canonicalizer tests for legacy definitions**

Cover legacy named actions (`actionName: "startDevice", actionType: "EMIT"` plus trigger `action: "startDevice"`) and flat action payload fields. Assert canonical output has `actions: ["EMIT"]` and the trigger embeds `{actionName:"EMIT", payload:{...}}`. Also assert already-canonical actions remain unchanged.

- [ ] **Step 6: Run canonicalizer tests and verify RED**

Run: `cd Backend && mvn -Dtest=WorkflowDefinitionCanonicalizerTest test`

Expected: legacy action remains a string reference or retains `actionType`.

- [ ] **Step 7: Implement canonicalizer migration and rerun focused tests**

Build a legacy action map before replacing `actions[]`; inline a deep copy into each referring trigger, move flat fields under `payload`, translate `actionType` to `actionName`, deduplicate the allowed action-name subset while preserving first appearance, and leave canonical definitions stable.

Run: `cd Backend && mvn -Dtest=WorkflowDefinitionCanonicalizerTest,WorkflowDefinitionCompilerTest test`

Expected: both suites pass.

- [ ] **Step 8: Commit the contract/compiler slice**

Stage only the Schema, compiler, canonicalizer, and their tests. Commit message: `feat(workflow): compile inline node trigger actions`.

---

### Task 2: Action runtime for constants, expressions, and lifecycle requests

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/action/WorkflowActionDefinition.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/action/WorkflowActionRegistry.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/action/UpdateWorkflowActionExecutor.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/action/EmitSignalWorkflowActionExecutor.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowExecutionOperations.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/DefaultWorkflowExecutionOperations.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowRuntimeService.java`
- Modify: `Backend/src/test/java/com/smartlab/engine/workflow/action/UpdateWorkflowActionExecutorTest.java`
- Modify: `Backend/src/test/java/com/smartlab/engine/workflow/action/EmitSignalWorkflowActionExecutorTest.java`
- Modify: `Backend/src/test/java/com/smartlab/management/service/db/workflow/WorkflowRuntimeServiceTest.java`

**Interfaces:**
- Consumes: canonical `{actionName, payload}` objects and `WorkflowActionContext`.
- Produces: `WorkflowActionDefinition(String actionName, ObjectNode payload)`, constant/expression variable updates, and `transitionNodeLifecycle(Task, TaskStep, FlowNode, String)`.

- [ ] **Step 1: Write failing action-definition and UPDATE executor tests**

Assert `WorkflowActionDefinition.from()` reads only `actionName` and a copied payload. Add executor cases proving constant values bypass the expression evaluator, expressions are evaluated, lifecycle UPDATE delegates to the transition operation without writing `nodeLifecycleState` into variable updates, and malformed payloads fail defensively.

```java
@Test
void writesConstantWithoutEvaluatingExpression() {
    WorkflowActionDefinition action = action("UPDATE", payload(
        "updateType", "INTERNAL_VARIABLE", "targetName", "temperature", "value", 200));
    WorkflowActionResult result = executor.execute(action, context);
    assertThat(result.variableUpdates().path("temperature").asInt()).isEqualTo(200);
    verifyNoInteractions(expressionEvaluator);
}

@Test
void requestsDeclaredLifecycleTransition() {
    executor.execute(action("UPDATE", payload(
        "updateType", "NODE_LIFECYCLE", "targetName", "RUNNING")), context);
    verify(operations).transitionNodeLifecycle(task, step, node, "RUNNING");
}
```

- [ ] **Step 2: Run action tests and verify RED**

Run: `cd Backend && mvn -Dtest=UpdateWorkflowActionExecutorTest,EmitSignalWorkflowActionExecutorTest test`

Expected: runtime still expects `actionType`, `internalVariableName`, and flat fields.

- [ ] **Step 3: Implement canonical action parsing and executors**

Change registry lookup to `action.actionName()`. In UPDATE, branch by `payload.updateType`; return variable updates for INTERNAL_VARIABLE and call the new execution operation for NODE_LIFECYCLE. In EMIT, read `payload.targetInterfaceName` and `payload.signalName`.

- [ ] **Step 4: Write failing lifecycle transition service tests**

Test a legal `PENDING -> RUNNING`, an undeclared transition rejection, a target absent from `lifecycle.states`, terminal timestamp/duration maintenance, and the absence of any automatic start transition in the scheduler-facing service API.

- [ ] **Step 5: Run lifecycle tests and verify RED**

Run: `cd Backend && mvn -Dtest=WorkflowRuntimeServiceTest test`

Expected: explicit node-definition-aware lifecycle transition API does not exist.

- [ ] **Step 6: Implement validated lifecycle transition persistence**

Add `transitionNodeLifecycle(Task task, TaskStep step, FlowNode node, String targetState)` to operations and runtime service. Verify `node.lifecycle.states` and the exact `{fromStateName,toStateName}` edge before updating `NODE_STATUS`; maintain `START_TIME` on first RUNNING entry and `END_TIME`/`DURATION_MS` for terminal states without writing lifecycle into `VARIABLE_SPACE`.

- [ ] **Step 7: Run action and runtime tests and verify GREEN**

Run: `cd Backend && mvn -Dtest=UpdateWorkflowActionExecutorTest,EmitSignalWorkflowActionExecutorTest,WorkflowRuntimeServiceTest test`

Expected: all focused tests pass.

- [ ] **Step 8: Commit the action-runtime slice**

Stage only action runtime, execution operations, runtime service, and focused tests. Commit message: `feat(workflow): execute update and emit payloads`.

---

### Task 3: Per-node frozen-snapshot polling and independent rising edges

**Files:**
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowTriggerState.java`
- Create: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowTriggerStateTest.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowEngine.java`
- Modify: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowEngineExecutionTest.java`

**Interfaces:**
- Consumes: a `TaskStep`, canonical node definition, and one frozen `ObjectNode` evaluation snapshot.
- Produces: stable trigger keys, independent edge transitions, and `processNodePoll(Task, TaskStep, FlowNode)` semantics.

- [ ] **Step 1: Write failing stable-key and edge-state tests**

Assert keys remain stable when an unrelated trigger is inserted on another interface; identical trigger definitions on one interface receive different occurrence ordinals; missing state is false; false-to-true is executable; true-to-true is suppressed; true-to-false resets.

```java
@Test
void identicalTriggersHaveIndependentOccurrenceKeys() {
    String first = WorkflowTriggerState.key("Interface_state_out", trigger, 0);
    String second = WorkflowTriggerState.key("Interface_state_out", trigger, 1);
    assertThat(first).isNotEqualTo(second);
}
```

- [ ] **Step 2: Run trigger-state tests and verify RED**

Run: `cd Backend && mvn -Dtest=WorkflowTriggerStateTest test`

Expected: `WorkflowTriggerState` does not exist.

- [ ] **Step 3: Implement stable keys and state read/write helpers**

Canonicalize the trigger JSON with deterministic property ordering, hash interface name plus canonical trigger content, and append only the occurrence ordinal among identical triggers. Read/write booleans beneath `_triggerStates` without exposing that object as a normal variable.

- [ ] **Step 4: Write failing node-poll ordering and snapshot tests**

Use mocked action executors/operations to prove:

1. OUT interfaces execute before IN interfaces while retaining per-direction order;
2. all simultaneously rising triggers execute exactly once;
3. duplicate inline actions on different triggers both execute;
4. UPDATE followed by another trigger does not affect that trigger until the next poll;
5. multiple EMIT actions are allowed;
6. no matched trigger is a normal wait;
7. `PENDING` is not auto-started;
8. `_triggerStates` is absent from action variables and emitted payload;
9. only BRANCH/AGGREGATE expressions are evaluated;
10. node variables come from the node mapping boundary, not direct telemetry inspection.

- [ ] **Step 5: Run engine tests and verify RED**

Run: `cd Backend && mvn -Dtest=WorkflowEngineExecutionTest test`

Expected: failures expose input-only traversal, batching/deduplication, UPDATE-before-EMIT reordering, one-EMIT restriction, mutable variables, and automatic `startStep`.

- [ ] **Step 6: Implement one-node polling with a frozen snapshot**

At poll start, synchronize mapped values into `VARIABLE_SPACE`, build a deep-copied user evaluation snapshot, inject interface input fields and `nodeLifecycleState`, and evaluate BRANCH/AGGREGATE expression once. Sort interfaces with stable partition OUT then IN. For every trigger, evaluate against that same snapshot; on a rising edge execute its inline action immediately, and only after successful/accepted action execution persist true. On false persist/reset false. Never merge action results back into the frozen snapshot.

```java
for (JsonNode interfaceNode : outThenIn(node.getInterfaces())) {
    Map<String, Integer> duplicateOrdinals = new HashMap<>();
    for (JsonNode trigger : iterable(interfaceNode.path("bindingTriggers"))) {
        String key = triggerState.key(interfaceNode, trigger, duplicateOrdinals);
        boolean current = conditionEvaluator.evaluate(trigger.path("condition"), frozenSnapshot);
        evaluateEdgeAndExecute(task, step, node, trigger, key, current, frozenSnapshot);
    }
}
```

- [ ] **Step 7: Define action-result retry semantics with tests**

Assert `CONTINUE` and accepted `AWAIT_EXTERNAL_SIGNAL` latch the edge; `WAIT_DEVICE_IDLE` does not latch and retries next poll; execution exceptions do not latch; external send retries reuse a deterministic message id derived from task/step/trigger key.

- [ ] **Step 8: Implement result-aware latching and rerun focused tests**

Run: `cd Backend && mvn -Dtest=WorkflowTriggerStateTest,WorkflowEngineExecutionTest test`

Expected: all trigger and engine execution tests pass.

- [ ] **Step 9: Commit the polling-engine slice**

Stage only trigger-state helper, engine, and their tests. Commit message: `feat(workflow): poll node triggers on rising edges`.

---

### Task 4: Terminal observation, device signal persistence, and task settlement

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowRuntimeService.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowEngine.java`
- Modify: `Backend/src/test/java/com/smartlab/management/service/db/workflow/WorkflowRuntimeServiceTest.java`
- Modify: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowEngineExecutionTest.java`

**Interfaces:**
- Consumes: active or newly terminal task steps and `StateMachineInterfaceSignalEvent`.
- Produces: `pollableSteps(Long taskId)`, terminal trigger-settlement marker, and event-to-input-snapshot persistence without event-side trigger execution.

- [ ] **Step 1: Write failing pollable-step and terminal-grace tests**

Assert PENDING/RUNNING/TERMINATING steps are always returned, a newly SUCCEEDED/FAILED/TERMINATED step is returned until its terminal state has been evaluated once, and a settled terminal step is excluded. Assert a lifecycle UPDATE to SUCCEEDED emits workflow ACTIVE only on the following poll.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `cd Backend && mvn -Dtest=WorkflowRuntimeServiceTest,WorkflowEngineExecutionTest test`

Expected: terminal steps disappear from `activeSteps()` before lifecycle triggers can observe them.

- [ ] **Step 3: Implement terminal-grace polling and settlement**

Replace scheduler use of `activeSteps()` with `pollableSteps()`. Store a reserved terminal-evaluation marker inside `_triggerStates`; mark it only after the full terminal poll finishes. Determine “no active work” only after excluding settled terminal steps, and do not fail a task merely because one poll found no matching trigger.

- [ ] **Step 4: Write failing state-machine event tests**

Assert a device event updates the target step's interface input snapshot and mapped internal variables but does not invoke trigger actions in the event listener. The next scheduler poll must observe the persisted signal/payload.

- [ ] **Step 5: Implement persistence-only event handling**

Keep routing/target validation in the listener, persist the input signal and payload, and let the scheduler own all trigger evaluation. Preserve task locking around persistence and polling.

- [ ] **Step 6: Run focused tests and verify GREEN**

Run: `cd Backend && mvn -Dtest=WorkflowRuntimeServiceTest,WorkflowEngineExecutionTest test`

Expected: terminal lifecycle triggers and device-return triggers pass without event-driven duplicate execution.

- [ ] **Step 7: Commit the lifecycle/event slice**

Stage runtime service, engine, and focused tests. Commit message: `feat(workflow): settle terminal trigger polls`.

---

### Task 5: System templates and user-defined N-way branches

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/global/contract/WorkflowNodeSystemContract.java`
- Modify: `Backend/src/test/java/com/smartlab/global/contract/WorkflowNodeSystemContractTest.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCompiler.java`
- Modify: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowDefinitionCompilerTest.java`

**Interfaces:**
- Consumes: node-system template requests and BRANCH definitions with arbitrary OUT interfaces.
- Produces: canonical templates using `actions[]` subsets plus inline triggers, and compiler acceptance of N-way branch routing.

- [ ] **Step 1: Write failing system-template tests**

Assert DEV_NODE uses an ACTIVE-triggered lifecycle UPDATE, a RUNNING lifecycle trigger that emits `WF_EXECUTE_START`, a device-completion trigger that updates lifecycle, and a SUCCEEDED lifecycle trigger that emits workflow ACTIVE. Assert template actions are string subsets, triggers are inline objects, and no template contains `actionType`.

- [ ] **Step 2: Write failing N-way branch tests**

Build a BRANCH with outputs `low`, `normal`, and `high`, each with a distinct expression-result condition and inline EMIT. Assert it compiles and all three routes are retained; assert the compiler does not require true/false names or mutual exclusivity.

- [ ] **Step 3: Run contract/compiler tests and verify RED**

Run: `cd Backend && mvn -Dtest=WorkflowNodeSystemContractTest,WorkflowDefinitionCompilerTest test`

Expected: old templates contain named action objects and BRANCH requires true/false interfaces.

- [ ] **Step 4: Replace templates with canonical trigger/action structures**

Generate only system-required interfaces and lifecycle transitions. Represent every operation as an inline trigger action and list each permitted kind once in `actions[]`. Remove branch true/false special validation and allow user-created OUT interfaces/connections.

- [ ] **Step 5: Run contract/compiler tests and verify GREEN**

Run: `cd Backend && mvn -Dtest=WorkflowNodeSystemContractTest,WorkflowDefinitionCompilerTest test`

Expected: all system-contract and N-way branch tests pass.

- [ ] **Step 6: Commit the template/branch slice**

Stage system contract, compiler changes, and tests. Commit message: `feat(workflow): support n-way branch triggers`.

---

### Task 6: Frontend editor and normalization for inline actions

**Files:**
- Modify: `Frontend/src/utils/workflowNodeDefinition.js`
- Modify: `Frontend/src/components/task/workflow/WorkflowTriggersActionsPanel.vue`
- Modify: `Frontend/src/components/task/workflow/WorkflowNodeInspector.vue`
- Modify: `Frontend/src/components/task/workflow/WorkflowCanvasNode.vue`
- Modify: `Frontend/tests/workflowNodeDefinition.test.mjs`
- Modify: `Frontend/tests/final-review-workflow-contract.test.mjs`
- Modify: `Frontend/tests/workflowCanvas.test.mjs`

**Interfaces:**
- Consumes: backend canonical templates and node definitions using string action subsets plus inline trigger actions.
- Produces: editor operations for both IN/OUT triggers, constant/expression/lifecycle UPDATE payloads, EMIT payloads, and arbitrary BRANCH outputs.

- [ ] **Step 1: Rewrite frontend contract tests first**

Assert created nodes contain no arbitrary action names or `actionType`; every trigger contains an inline action; action availability comes from `actions[]`; both OUT and IN interfaces are editable; constant values preserve number/boolean/JSON types; UPDATE source mode enforces value xor expression; lifecycle targets use declared states; and branch interfaces can be added/removed without true/false assumptions.

- [ ] **Step 2: Run frontend unit tests and verify RED**

Run: `cd Frontend && node --test tests/workflowNodeDefinition.test.mjs tests/final-review-workflow-contract.test.mjs tests/workflowCanvas.test.mjs`

Expected: old tests/runtime still use named action objects, string references, and input-only triggers.

- [ ] **Step 3: Implement canonical frontend normalization and validation**

Normalize `actions` to unique `EMIT`/`UPDATE` strings; normalize inline action payloads; migrate legacy named action references on load; validate EMIT target/signal, UPDATE target/type, value xor expression, and action subset membership. Preserve `_system` metadata only in editor state and strip it in published payloads using existing conventions.

- [ ] **Step 4: Implement the trigger/action editor**

Display OUT interfaces before IN interfaces. Each trigger edits its own `action.actionName` and payload. For INTERNAL_VARIABLE UPDATE, provide a constant/expression source selector; preserve typed JSON constants rather than coercing everything to strings. For NODE_LIFECYCLE, select from declared lifecycle states. For EMIT, select an OUT interface and one of its allowed signals.

- [ ] **Step 5: Remove branch true/false presentation assumptions**

Use declared interface names/labels in the canvas, allow custom BRANCH OUT interfaces in the inspector, and keep connection cleanup behavior when an interface is removed.

- [ ] **Step 6: Run frontend tests and production build**

Run: `cd Frontend && node --test tests/workflowNodeDefinition.test.mjs tests/final-review-workflow-contract.test.mjs tests/workflowCanvas.test.mjs`

Then run: `cd Frontend && npm run build`

Expected: all frontend tests pass and Vite completes successfully.

- [ ] **Step 7: Commit the frontend slice**

Stage only the workflow editor utilities/components/tests. Commit message: `feat(workflow-ui): edit inline trigger actions`.

---

### Task 7: Compatibility fixtures, complete regression, and documentation alignment

**Files:**
- Modify as failures require: workflow fixtures under `Backend/src/test/resources` and `Frontend/tests`
- Modify: `docs/superpowers/specs/2026-08-10-workflow-node-trigger-polling-design.md` only if implementation reveals a concrete contradiction

**Interfaces:**
- Consumes: all implementation slices.
- Produces: one verified, backward-compatible workflow model/runtime/editor behavior.

- [ ] **Step 1: Search for stale workflow action structures**

Run:

```powershell
rg -n 'actionType|Interface_true_out|Interface_false_out|"action"\s*:\s*"|internalVariableName' Backend/src Frontend/src Backend/src/test Frontend/tests
```

Expected: no stale workflow-node usage remains; unrelated constraint/state-machine `actionType` usages may remain and must not be changed.

- [ ] **Step 2: Run the complete backend suite**

Run: `cd Backend && mvn test`

Expected: all backend tests pass with zero failures/errors.

- [ ] **Step 3: Run the complete frontend suite and build**

Run: `cd Frontend && node --test tests/*.test.js tests/*.test.mjs`

Then run: `cd Frontend && npm run build`

Expected: all tests pass and the production build succeeds.

- [ ] **Step 4: Inspect the final diff and Schema validity**

Parse all three referenced JSON Schemas with a JSON parser, inspect `git diff --check`, and verify the final diff contains no unrelated changes, no `_triggerStates` data leak, no automatic ACTIVE lifecycle transition, and no trigger batching/deduplication.

- [ ] **Step 5: Commit compatibility/test updates**

Stage only relevant fixtures, documentation adjustments, and regression fixes. Commit message: `test(workflow): verify trigger polling integration`.

- [ ] **Step 6: Record final evidence**

Report backend test counts, frontend test/build results, changed files, commits, and any intentionally retained backward-compatibility behavior. Do not claim completion without fresh output from every command above.
