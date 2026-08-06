# Task 4 — Structured Task Preflight and Binding Input

## Status
Completed. Task creation accepts structured `deviceBindings` and builds the internal canonical resource map. Legacy `resourceMap` requests remain supported when `deviceBindings` is absent.

## Delivered
- Added binding, preflight request, and preflight response DTOs.
- Reused workflow requirements `slotId` values to prepare canonical task resource bindings and return structured binding issues.
- Added non-throwing readiness and task-constraint inspection with structured `WorkflowIssue` output.
- Added `POST /api/task/preflight`.
- Applied the same model/resource/readiness/constraint preflight checks before create, start, and resume; blocking issues prevent persistence or lifecycle mutation.
- Preserved compatibility wrappers for legacy validation and legacy resource maps.

## Verification
- `mvn clean test -Dtest=WorkflowTaskResourceServiceTest,WorkflowExecutionReadinessServiceTest,TaskPreflightBehaviorTest,TaskConstraintServiceTest,TaskServiceTest`
- Result: 22 tests passed, 0 failures, 0 errors.

## Concerns
- Existing frontend task UI still uses legacy internal `resourceMap`/`bindingKey` fields. Per task coordination this belongs to the later frontend scope; this backend change exposes `requirements.slotId` plus `deviceBindings` so that UI can migrate without using internal resource-map structure.
## Review remediation
- Preflight now reports readiness for resolved bindings even when other slots are missing.
- Constraint inspection validates deep-copied rules with the same normalization and definition validator before persistence.
- Readiness issues use deviceBindings[slotId] paths and slot element IDs.
- Added MockMvc preflight response coverage and blocking-create non-insertion coverage.
- Final focused suite: 26 tests passed, 0 failures, 0 errors.
