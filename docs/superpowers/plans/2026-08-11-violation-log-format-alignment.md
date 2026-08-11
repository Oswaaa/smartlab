# VIOLATION_LOG Format Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align global and task constraint violation persistence with the new JSONB `VIOLATION_LOG` columns while preserving state-machine and intrinsic-constraint boundaries.

**Architecture:** Keep `ConstraintEngine.writeViolationLog()` as the only log construction and persistence entry. Carry compiler-produced observable binding descriptors in `RuntimeConstraint`, use a constraint-only read service for task/device scope snapshots, and map the four JSONB fields in place.

**Tech Stack:** Java 21, Spring Boot, MyBatis-Plus, Jackson `JsonNode`, PostgreSQL JSONB, JUnit 5, Mockito, Maven.

## Global Constraints

- Only global and task constraints may write `VIOLATION_LOG` in this change.
- Do not modify `IntrinsicConstraintMonitor`, `IntrinsicConstraintPlanRegistry`, `StateMachineEngine`, or intrinsic exception latching.
- Do not add `ViolationLogFactory` or a cross-engine audit abstraction.
- Keep expression evaluation, window timing, trigger deduplication, action execution, and one-log-row-per-action behavior unchanged.
- Preserve unrelated uncommitted work and stage only files listed by each task.

---

### Task 1: Map the new VIOLATION_LOG entity columns

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/management/entity/constraint/ViolationLog.java`
- Create: `Backend/src/test/java/com/smartlab/management/entity/constraint/ViolationLogMappingTest.java`

**Interfaces:**
- Produces: `JsonNode getObservedVariable()`, `JsonNode getExpression()`, `JsonNode getActualValue()`, and `JsonNode getVariableSnapshot()`.
- Removes: `getExpectedCondition()` and the `expected_condition` mapping.

- [ ] **Step 1: Write the failing mapping and serialization test**

```java
@Test
void mapsTheFourViolationEvidenceFieldsToJsonbColumns() throws Exception {
    assertJsonbField("observedVariable", "observed_variable");
    assertJsonbField("expression", "expression");
    assertJsonbField("actualValue", "actual_value");
    assertJsonbField("variableSnapshot", "variable_snapshot");
    assertThrows(NoSuchFieldException.class,
            () -> ViolationLog.class.getDeclaredField("expectedCondition"));
}

@Test
void serializesExpressionAndDoesNotExposeExpectedCondition() {
    ViolationLog log = new ViolationLog();
    log.setExpression(JsonNodeSupport.toNode("temperature > limit"));
    JsonNode json = JsonNodeSupport.toNode(log);
    assertEquals("temperature > limit", json.path("expression").asText());
    assertFalse(json.has("expectedCondition"));
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `mvn -Dtest=ViolationLogMappingTest test`

Expected: compilation or assertion failure because `observedVariable` is `String`, `expression` is missing, and `expectedCondition` still exists.

- [ ] **Step 3: Implement the entity mapping**

```java
@TableField(value = "observed_variable", typeHandler = PostgresJsonbTypeHandler.class)
private JsonNode observedVariable;

@TableField(value = "expression", typeHandler = PostgresJsonbTypeHandler.class)
private JsonNode expression;
```

Delete `expectedCondition`; keep the existing JSONB handlers for `actualValue` and `variableSnapshot`.

- [ ] **Step 4: Run the test and verify GREEN**

Run: `mvn -Dtest=ViolationLogMappingTest test`

Expected: PASS.

- [ ] **Step 5: Commit Task 1**

```bash
git add Backend/src/main/java/com/smartlab/management/entity/constraint/ViolationLog.java Backend/src/test/java/com/smartlab/management/entity/constraint/ViolationLogMappingTest.java
git commit -m "fix(constraint): align violation log entity fields"
```

---

### Task 2: Carry canonical observable bindings into runtime constraints

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/engine/constraint/RuntimeConstraint.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/constraint/EffectiveConstraintModelCompiler.java`
- Modify: `Backend/src/test/java/com/smartlab/engine/constraint/EffectiveConstraintModelCompilerTest.java`

**Interfaces:**
- Produces: `JsonNode RuntimeConstraint.observedVariables()` containing only canonical `OBSERVABLE` bindings.
- Consumes: compiler-created `observableName` values already exported in the effective constraint model.

- [ ] **Step 1: Write the failing compiler test**

Extend `producesJsonAndRuntimePlanFromOneEffectiveCompilation()`:

```java
RuntimeConstraint runtime = result.monitoringPlan().constraints().values().stream()
        .filter(item -> item.rule().getId().equals(12L))
        .findFirst().orElseThrow();
assertEquals("OBSERVABLE",
        runtime.observedVariables().path("temperature").path("bindingType").asText());
assertEquals("global_12_temperature",
        runtime.observedVariables().path("temperature").path("observableName").asText());
assertFalse(runtime.observedVariables().has("limit"));
```

Add a second rule whose expression compares two observable bindings and assert both descriptors are retained in declaration order.

- [ ] **Step 2: Run the test and verify RED**

Run: `mvn -Dtest=EffectiveConstraintModelCompilerTest test`

Expected: compilation failure because `RuntimeConstraint.observedVariables()` does not exist.

- [ ] **Step 3: Implement canonical runtime descriptors**

Add a `JsonNode observedVariables` component to `RuntimeConstraint` and deep-copy it in the compact constructor. During `compileTemplate()`, copy each generated canonical `OBSERVABLE` binding into a dedicated `ObjectNode`. Add that object to `CompiledRuleTemplate` and pass it through `appendRuntime()` when constructing each `RuntimeConstraint`.

Keep a six-argument compatibility constructor for focused tests:

```java
public RuntimeConstraint(RuntimeConstraintKey key, ConstraintRule rule,
                         Map<String, ObservableKey> observableBindings,
                         Long taskId, Long taskStepId, Long deviceInstanceId) {
    this(key, rule, observableBindings, taskId, taskStepId, deviceInstanceId,
            JsonNodeSupport.objectNode());
}
```

- [ ] **Step 4: Run the compiler tests and verify GREEN**

Run: `mvn -Dtest=EffectiveConstraintModelCompilerTest test`

Expected: PASS.

- [ ] **Step 5: Commit Task 2**

```bash
git add Backend/src/main/java/com/smartlab/engine/constraint/RuntimeConstraint.java Backend/src/main/java/com/smartlab/engine/constraint/EffectiveConstraintModelCompiler.java Backend/src/test/java/com/smartlab/engine/constraint/EffectiveConstraintModelCompilerTest.java
git commit -m "feat(constraint): retain canonical observed bindings"
```

---

### Task 3: Read complete task and device violation scopes

**Files:**
- Create: `Backend/src/main/java/com/smartlab/engine/constraint/ConstraintScopeSnapshotReader.java`
- Create: `Backend/src/test/java/com/smartlab/engine/constraint/ConstraintScopeSnapshotReaderTest.java`

**Interfaces:**
- Produces: `ObjectNode snapshot(RuntimeConstraint constraint, JsonNode action)`.
- Consumes: `TaskMapper`, `TaskStepMapper`, and `DeviceTwinStatesMapper` as read-only dependencies.

- [ ] **Step 1: Write the failing task/device snapshot tests**

Create tests with mocked mappers that assert:

```java
ObjectNode snapshot = reader.snapshot(runtime, action);
assertEquals("RUNNING", snapshot.path("tasks").path("7").path("taskStatus").asText());
assertEquals(105.2, snapshot.path("tasks").path("7").path("steps")
        .path("31").path("variableSpace").path("temperature").asDouble());
assertEquals(105.2, snapshot.path("devices").path("18")
        .path("attributes").path("temperature").asDouble());
assertEquals("IDLE", snapshot.path("devices").path("18")
        .path("commandLifecycle").asText());
```

Add coverage for two device bindings, an action-only target device, empty scope, and one mapper throwing while the remaining scope is still captured with `snapshotError`.

- [ ] **Step 2: Run the test and verify RED**

Run: `mvn -Dtest=ConstraintScopeSnapshotReaderTest test`

Expected: compilation failure because `ConstraintScopeSnapshotReader` does not exist.

- [ ] **Step 3: Implement the read-only snapshot reader**

Implement `ConstraintScopeSnapshotReader` as a Spring `@Service` that:

1. Collects task IDs from `constraint.taskId()` and positive `action.targetTaskId`.
2. Collects device IDs from `constraint.observableBindings()`, `constraint.deviceInstanceId()`, and positive `action.deviceInstanceId`.
3. Reads each task and all of its steps, preserving full `taskVariables` and `variableSpace` JSON.
4. Reads each `DeviceTwinStates` row and copies `currentAttr`, `currentOpState`, and `currentCmdState`.
5. Catches failures per task/device and writes `snapshotError` inside that scope without aborting other reads.
6. Always returns top-level `tasks` and `devices` objects.

- [ ] **Step 4: Run the snapshot tests and verify GREEN**

Run: `mvn -Dtest=ConstraintScopeSnapshotReaderTest test`

Expected: PASS.

- [ ] **Step 5: Commit Task 3**

```bash
git add Backend/src/main/java/com/smartlab/engine/constraint/ConstraintScopeSnapshotReader.java Backend/src/test/java/com/smartlab/engine/constraint/ConstraintScopeSnapshotReaderTest.java
git commit -m "feat(constraint): capture violation scope snapshots"
```

---

### Task 4: Write the new JSONB evidence fields in place

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/engine/constraint/ConstraintEngine.java`
- Modify: `Backend/src/test/java/com/smartlab/engine/constraint/ConstraintEngineTest.java`

**Interfaces:**
- Consumes: `RuntimeConstraint.observedVariables()` and `ConstraintScopeSnapshotReader.snapshot(...)`.
- Produces: correctly populated `ViolationLog` rows through the existing `ViolationLogService.save()` call.

- [ ] **Step 1: Write failing log-format tests**

Capture the saved `ViolationLog` and assert:

```java
ViolationLog saved = logCaptor.getValue();
assertEquals("global_1_temperature",
        saved.getObservedVariable().path("temperature").path("observableName").asText());
assertFalse(saved.getObservedVariable().has("limit"));
assertEquals("temperature > limit", saved.getExpression().asText());
assertEquals(82, saved.getActualValue().path("temperature").asInt());
assertEquals(80, saved.getActualValue().path("limit").asInt());
assertEquals("RUNNING", saved.getVariableSnapshot().path("tasks")
        .path("7").path("taskStatus").asText());
```

Add tests for two observable values, task constraint metadata, one row per action, and snapshot-reader failure content. Keep all existing action and temporal-expression assertions.

- [ ] **Step 2: Run the engine test and verify RED**

Run: `mvn -Dtest=ConstraintEngineTest test`

Expected: compilation or assertion failure because the engine still writes rule name, `expectedCondition`, and the old snapshots.

- [ ] **Step 3: Implement the in-place write logic**

Inject `ConstraintScopeSnapshotReader` into `ConstraintEngine`. Pass `RuntimeConstraint` through `evaluateRule()`, `executeActions()`, and `writeViolationLog()` without changing evaluation or action ordering.

Inside the existing `writeViolationLog()`:

```java
logEntry.setObservedVariable(constraint.observedVariables().deepCopy());
logEntry.setExpression(JsonNodeSupport.toNode(rule.getExpression()));
logEntry.setActualValue(snapshot(values));
logEntry.setVariableSnapshot(scopeSnapshotReader.snapshot(constraint, action));
```

Retain current `constraintRuleId`, `constraintType`, task/step/device IDs, `actionTaken`, and `ViolationLogService.save()` behavior.

- [ ] **Step 4: Run focused constraint tests and verify GREEN**

Run: `mvn -Dtest=ConstraintEngineTest,EffectiveConstraintModelCompilerTest,ConstraintScopeSnapshotReaderTest,ViolationLogMappingTest test`

Expected: PASS.

- [ ] **Step 5: Commit Task 4**

```bash
git add Backend/src/main/java/com/smartlab/engine/constraint/ConstraintEngine.java Backend/src/test/java/com/smartlab/engine/constraint/ConstraintEngineTest.java
git commit -m "fix(constraint): persist structured violation evidence"
```

---

### Task 5: Verify module boundaries and the complete backend

**Files:**
- Verify only; no production file is added to the intrinsic-constraint path.

**Interfaces:**
- Verifies: database mapping, constraint runtime behavior, state-machine isolation, and complete regression safety.

- [ ] **Step 1: Verify no intrinsic-constraint dependency was introduced**

Run:

```powershell
rg -n "ViolationLog|ConstraintScopeSnapshotReader" Backend/src/main/java/com/smartlab/engine/statemachine
```

Expected: no matches.

- [ ] **Step 2: Run all constraint and state-machine tests**

Run:

```powershell
mvn -Dtest='com.smartlab.engine.constraint.*,com.smartlab.engine.statemachine.*' test
```

Expected: all selected tests pass.

- [ ] **Step 3: Run the complete backend test suite**

Run: `mvn test`

Expected: BUILD SUCCESS with zero failures and zero errors.

- [ ] **Step 4: Compile the backend against the new entity mapping**

Run: `mvn compile -q`

Expected: exit code 0.

- [ ] **Step 5: Review the final diff**

Run:

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors; unrelated pre-existing changes remain unstaged and unchanged.

---

### Task 6: Send completion notification

**Files:**
- No repository files.

**Interfaces:**
- Produces: one completion email to `2296029664@qq.com` after Task 5 passes.

- [ ] **Step 1: Prepare the completion message**

Subject: `SmartLab VIOLATION_LOG 新字段与格式适配已完成`

Body must include:

```text
本次仅修改全局约束和任务约束的 VIOLATION_LOG 写入。
设备内置约束、状态机和异常锁存逻辑未修改。
列出新增字段映射、JSON 格式、作用域快照和最终测试结果。
```

- [ ] **Step 2: Send and verify delivery state**

Send through the authenticated QQ mailbox and verify the message appears in “已发送”.

