# Motion card watchdog implementation plan

## Scope and design

User authorized implementing Adapter-side offline detection and recovery. Work in
the existing checkout; preserve the unrelated application.yml edit. No live SDK
connection, equipment commands, service restart, or database changes during testing.

Use live poll freshness plus a periodic MC_GetClock probe. A failed read removes
online status immediately; the existing consecutive-failure threshold controls
connection teardown. A separate watchdog invalidates stale sessions without taking
SDK/dispatcher locks. Never destroy/reopen the DLL concurrently with an in-flight
call: cleanup/reconnect follows when that call returns. A permanently blocked DLL
therefore stays offline and requires process restart, not unsafe thread termination.

Clock regression invalidates the session (including possible clock wrap: conservative
reconnect). A frozen clock expires health even if reads return success. Reconnection
uses existing full initialization and verifies enable bits. Missing enable alone
blocks arm motion and fails pending motion, but does not auto-enable or call the
whole card offline. Digital outputs and vision retain their independent semantics.

## Tasks

- [x] Add regression tests for failed/stale reads, blocked SDK, frozen/reset clock,
      recovery, lost enable and false completion. Run red before implementation.
- [x] Implement health watchdog and nonblocking heartbeat notification in control.py
      and core.py; preserve connection teardown ordering and command deduplication.
- [x] Add clock binding, enable checks, session invalidation and completion checks
      in southbound.py; guard dispatcher completion after invalidation.
- [x] Run MotionStageController's full offline test suite; inspect diffs and document
      timing, physical feedback limits and permanent native-call hangs.

## Review focus

Stale SDK calls must not resurrect online state or emit success; initial connect must
not lock heartbeat reporting; stop must not race DLL teardown; reconnect must not
replay old movement; health notifications must not wait for the next 10-second beat.

## Execution record

- Initial watchdog tests: 7 failures, 1 pre-existing reconnect behavior passed.
- Heartbeat wake regression failed before adding core notification.
- Queue/setup invalidation regressions: 2 failures before admission/update guards.
- Fresh review found synchronous initial connect, rejected-message redelivery,
  stale poll overwriting command failure, and stop/reconnect lifetime races.
  Added tests for the first three, fixed all four (stop shares I/O teardown lock).
- Ruling: use public MC_GetSts clock out-parameter, not private MC_GetClock export;
  constructor test caught the ABI mismatch before any connection was opened.
- Full MotionStageController suite: 54 tests passed. See HEALTH.md for deployment
  limitations; no physical equipment tests were performed.
