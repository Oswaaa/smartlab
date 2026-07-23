# SmartLab Workflow Action Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Schema-backed state-machine/workflow action dictionary and a complete, testable workflow runtime with WAIT, ASSIGN, CALCULATE and EMIT_SIGNAL actions plus metadata-driven frontend editing.

**Architecture:** Domain JSON Schemas declare action catalogs and payload definitions. Spring registries map every declared action, node type and workflow function type to one executor, verify complete coverage at startup, and return uniform execution outcomes to a small orchestration engine. WAIT and asynchronous device calls resume without a hidden action cursor by enforcing `[WAIT?] -> [pure actions]* -> [EMIT_SIGNAL?]`.

**Tech Stack:** Java 21, Spring Boot, Jackson, networknt JSON Schema, MyBatis-Plus, JUnit 5/Mockito, Vue 3, TypeScript, Element Plus, Node test runner, Vite.

## Global Constraints

- Do not change database tables.
- Do not execute arbitrary script expressions.
- Do not preserve invalid legacy action names or payloads.
- Do not store an action cursor in variable or snapshot fields.
- Do not sleep a scheduler thread for WAIT.
- Do not claim multi-backend distributed scheduling support.
- Do not commit during this plan because the shared worktree contains extensive user-owned changes; use test checkpoints instead.

---

### Task 1: Move action contracts into their domain Schemas

**Files:**
- Modify: `Backend/src/main/resources/schemas/protocol-dict.json`
- Modify: `Backend/src/main/resources/schemas/device-state-machine-model.json`
- Modify: `Backend/src/main/resources/schemas/workflow-model.json`
- Modify: `Backend/src/main/java/com/smartlab/global/schema/SchemaMetadataService.java`
- Test: `Backend/src/test/java/com/smartlab/global/schema/SchemaMetadataServiceTest.java`
- Test: `Backend/src/test/java/com/smartlab/global/util/JsonSchemaValidationServiceTest.java`

**Interfaces:**
- Produces: `stateMachine.actionCatalog`, `workflow.actionCatalog`, and `workflow.calculationOperators` from `/api/schema-metadata/frontend`.
- Produces: `StateMachineAction` with `SEND`; `WorkflowNodeAction` with `WAIT`, `ASSIGN`, `CALCULATE`, `EMIT_SIGNAL`.

- [ ] Write tests asserting all catalog names and payload definitions are loaded from the two domain Schemas.
- [ ] Run `mvn -Dtest=SchemaMetadataServiceTest,JsonSchemaValidationServiceTest test` and verify the new assertions fail.
- [ ] Move action definitions out of `protocol-dict.json`, add payload definitions and `x-actionCatalog` to the domain Schemas, and expose them through `SchemaMetadataService`.
- [ ] Validate good and bad payload examples for all four workflow actions and SEND.
- [ ] Re-run the targeted tests and require zero failures.

### Task 2: Make the state-machine action dictionary complete and self-verifying

**Files:**
- Create: `Backend/src/main/java/com/smartlab/engine/statemachine/action/StateMachineActionExecutor.java`
- Create: `Backend/src/main/java/com/smartlab/engine/statemachine/action/StateMachineActionRegistry.java`
- Create: `Backend/src/main/java/com/smartlab/engine/statemachine/action/SendStateMachineActionExecutor.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineEngine.java`
- Delete after migration: `Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineDictionary.java`
- Test: `Backend/src/test/java/com/smartlab/engine/statemachine/StateMachineActionRegistryTest.java`
- Test: `Backend/src/test/java/com/smartlab/engine/statemachine/StateMachineEngineTest.java`

**Interfaces:**
- Produces: `StateMachineActionExecutor.actionName()` and `execute(ActionDefinition, EventContext)`.
- Produces: immutable registry lookup that rejects duplicate names and verifies exact Schema coverage.

- [ ] Write failing tests for duplicate registration, missing SEND executor, extra executor, invalid output interface, signal outside allowedSignals, CMD_START payload and CMD_ABORT payload.
- [ ] Run targeted tests and verify they fail for missing registry behavior.
- [ ] Extract SEND into a focused Spring component and implement exact coverage verification against the state-machine action catalog.
- [ ] Make SEND validate `SystemSignalFormat`, output interface direction/type and allowedSignals before publishing events.
- [ ] Replace `StateMachineEngine` lookup with the new registry and remove the old nested dictionary.
- [ ] Run all state-machine tests and require zero failures.

### Task 3: Implement workflow action contracts and registry

**Files:**
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/action/WorkflowActionExecutor.java`
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/action/WorkflowActionContext.java`
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/action/WorkflowActionResult.java`
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/action/WorkflowActionRegistry.java`
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/action/WorkflowValueResolver.java`
- Test: `Backend/src/test/java/com/smartlab/engine/workflow/action/WorkflowActionRegistryTest.java`
- Test: `Backend/src/test/java/com/smartlab/engine/workflow/action/WorkflowValueResolverTest.java`

**Interfaces:**
- Produces: `WorkflowActionStatus { CONTINUE, SUSPEND_UNTIL, AWAIT_EXTERNAL_SIGNAL }`.
- Produces: exact Schema coverage verification and dotted-path value resolution for task/step variables.

- [ ] Write failing registry tests for four exact executors, duplicates, missing names and extra names.
- [ ] Write failing resolver tests for literals, nested variable paths, missing paths and non-object traversal.
- [ ] Implement immutable registry and resolver without database access.
- [ ] Run action registry/resolver tests and require zero failures.

### Task 4: Implement WAIT, ASSIGN and CALCULATE

**Files:**
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/action/WaitWorkflowActionExecutor.java`
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/action/AssignWorkflowActionExecutor.java`
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/action/CalculateWorkflowActionExecutor.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowRuntimeService.java`
- Test: `Backend/src/test/java/com/smartlab/engine/workflow/action/WaitWorkflowActionExecutorTest.java`
- Test: `Backend/src/test/java/com/smartlab/engine/workflow/action/AssignWorkflowActionExecutorTest.java`
- Test: `Backend/src/test/java/com/smartlab/engine/workflow/action/CalculateWorkflowActionExecutorTest.java`

**Interfaces:**
- Consumes: `WorkflowValueResolver` and `WorkflowRuntimeService.mergeVariableSpace`.
- Produces: non-blocking deadline result and deterministic variable mutations.

- [ ] Write failing WAIT tests using an injected `Clock`, proving no sleep and correct resume boundary.
- [ ] Implement WAIT from `TaskStep.startTime + durationMs`.
- [ ] Write failing ASSIGN tests for literal/variable sources and missing target/path.
- [ ] Implement ASSIGN as an idempotent merge into step variable space.
- [ ] Write failing CALCULATE tests for all operators, decimals, ROUND, divide/modulo by zero and non-numeric operands.
- [ ] Implement calculations with `BigDecimal` and explicit result conversion.
- [ ] Run the three targeted suites and require zero failures.

### Task 5: Implement EMIT_SIGNAL as the only asynchronous workflow action

**Files:**
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/action/EmitSignalWorkflowActionExecutor.java`
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowExecutionOperations.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowEngine.java`
- Test: `Backend/src/test/java/com/smartlab/engine/workflow/action/EmitSignalWorkflowActionExecutorTest.java`

**Interfaces:**
- Produces: `WorkflowExecutionOperations.resolveDeviceInstance`, `ensureMessageId`, `dispatchStateMachineSignal`.
- Produces: `AWAIT_EXTERNAL_SIGNAL` after one successful dispatch and no second dispatch for an existing messageId.

- [ ] Write failing tests for unusable instances, unsupported interface/signal, first dispatch, messageId persistence and duplicate scheduling.
- [ ] Implement EMIT_SIGNAL without direct Mapper access.
- [ ] Implement the operations boundary in `WorkflowEngine` using existing runtime and state-machine services.
- [ ] Run the targeted tests and existing workflow/state-machine tests.

### Task 6: Validate workflow action ordering and payload semantics at save time

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCompiler.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowService.java`
- Test: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowDefinitionCompilerTest.java`

**Interfaces:**
- Consumes: Schema action catalog and `WorkflowActionRegistry` names.
- Produces: compiled definitions where WAIT is first, EMIT_SIGNAL is last, only pure actions precede EMIT_SIGNAL, and every payload is valid.

- [ ] Write failing tests for duplicate WAIT/EMIT_SIGNAL, wrong order, EMIT on non-device nodes, invalid payloads and an allowed full action chain.
- [ ] Replace the single-action compiler assumptions with ordered action validation.
- [ ] Validate every action payload against its named payload definition.
- [ ] Run compiler/service tests and require zero failures.

### Task 7: Replace workflow switches with node and function executor registries

**Files:**
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/node/WorkflowNodeExecutor.java`
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/node/WorkflowNodeExecutorRegistry.java`
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/node/DeviceCapabilityNodeExecutor.java`
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/node/FunctionalNodeExecutor.java`
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/node/SubFlowNodeExecutor.java`
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/function/WorkflowFunctionExecutor.java`
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/function/WorkflowFunctionExecutorRegistry.java`
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/function/StartFunctionExecutor.java`
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/function/EndFunctionExecutor.java`
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/function/BranchFunctionExecutor.java`
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/function/AggregateFunctionExecutor.java`
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/node/NodeExecutionResult.java`
- Create: `Backend/src/main/java/com/smartlab/engine/workflow/node/WorkflowNodeExecutionContext.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowEngine.java`
- Test: `Backend/src/test/java/com/smartlab/engine/workflow/node/WorkflowNodeExecutorRegistryTest.java`
- Test: `Backend/src/test/java/com/smartlab/engine/workflow/function/WorkflowFunctionExecutorRegistryTest.java`
- Test: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowEngineTest.java`

**Interfaces:**
- Produces: uniform node results handled centrally by WorkflowEngine.
- Produces: exact Schema coverage checks for nodeType and functionType.

- [ ] Write failing coverage tests and one behavior test per node/function executor.
- [ ] Add uniform result/context records and registries.
- [ ] Move existing device, functional, subflow, start, end, branch and aggregate semantics into focused executors.
- [ ] Reduce `WorkflowEngine.processStep()` to registry lookup plus centralized result handling.
- [ ] Preserve complete routing, port mapping, aggregate readiness and subflow return semantics.
- [ ] Run all workflow tests and require zero failures.

### Task 8: Complete task lifecycle, idempotency and execution logging

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowEngine.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowTaskControlService.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowRuntimeService.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/workflow/TaskService.java`
- Test: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowEngineTest.java`
- Test: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowTaskControlServiceTest.java`
- Test: `Backend/src/test/java/com/smartlab/management/service/db/workflow/TaskServiceTest.java`

**Interfaces:**
- Produces: taskId-scoped single-process locks and idempotent terminal writes.
- Produces: device failure/abort immediately propagates to task and EXECUTION_LOG.

- [ ] Write failing tests for scheduler/callback races, duplicate completion, device failure, task abort propagation and child-flow completion.
- [ ] Add keyed task locking without presenting it as distributed locking.
- [ ] Make runtime complete/fail operations no-op for already terminal entities.
- [ ] Ensure abort signals all active device steps before terminating remaining steps/task.
- [ ] Append meaningful task/step/device execution log entries for all terminal paths.
- [ ] Run all workflow Management and engine tests.

### Task 9: Build metadata-driven workflow action editing in Vue

**Files:**
- Modify: `Frontend/src/views/task/WorkflowDesigner.vue`
- Create: `Frontend/src/views/task/components/WorkflowActionEditor.vue`
- Create: `Frontend/src/views/task/workflowActions.ts`
- Test: `Frontend/tests/workflow-action-editor.test.mjs`
- Test: `Frontend/tests/workflow-designer-serialization.test.mjs`

**Interfaces:**
- Consumes: `workflow.actionCatalog` and calculation operators from `/api/schema-metadata/frontend`.
- Produces: canonical ordered action arrays with no local action-name fallback.

- [ ] Write failing Node tests for catalog loading, per-node filtering, default action creation, WAIT-first/EMIT-last validation and round-trip serialization.
- [ ] Implement pure action catalog helpers in `workflowActions.ts`.
- [ ] Implement WAIT, ASSIGN, CALCULATE and EMIT_SIGNAL parameter forms in the editor component.
- [ ] Integrate the editor into the selected-node drawer and remove hardcoded action construction.
- [ ] Run all frontend Node tests.

### Task 10: Complete task monitoring UI and final verification

**Files:**
- Modify: `Frontend/src/views/task/TaskList.vue`
- Test: `Frontend/tests/task-runtime-monitor.test.mjs`
- Verify: all Backend and Frontend test files

**Interfaces:**
- Consumes: existing task detail, step snapshots, variables and execution-log endpoints.
- Produces: visible running/waiting/external-call state without introducing new persistence fields.

- [ ] Write failing UI structure/helper tests for WAIT deadline, messageId, variable space, parent/depth and latest log display.
- [ ] Add runtime detail sections using existing task-step fields and log endpoints.
- [ ] Run `mvn test` in `Backend` and require zero failures.
- [ ] Run every `Frontend/tests/*.test.mjs` with Node and require zero failures.
- [ ] Run `npm run build` in `Frontend` and require a successful production build.
- [ ] Run `python -m unittest discover -s tests -v` in `adapter/testAdapter` and require zero failures.
- [ ] Search Backend/Frontend for old action switches, silent unknown-action branches, local action enums and hidden cursor fields; require no matches outside negative tests.
- [ ] Run `git diff --check -- Backend Frontend adapter/testAdapter docs` and fix all whitespace errors.
