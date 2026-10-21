# MotionStage device points implementation plan

**Goal:** One Adapter, four templates, eleven points: one three-axis arm, eight valves, one pneumatic corrector, one vision device.

**Architecture:** Preserve the existing motion engine and card ABI. Add a shared device dispatcher with per-request ownership, independent IO timers and independent vision polling. Both physical and simulated devices use the same dispatcher. No database migration or physical actuation during verification.

**Approved design:** Conversation: preserve SetExtDoBit, corrector channel 9 waits 2000 ms for both values without auto-reset; separate actual/planned position; retain legacy vision acceptance (all tags detected and all insertions accepted). Vision rejection remains a failed command with a distinct reason, not a camera fault.

**Tech stack:** Python standard library, existing paho MQTT, MultiCard DLL on Windows.

## Steps
- [ ] Add failing behavior tests for four templates, point isolation, corrector timing, vision pass/reject/transport errors, SDK failures and motion completion/cancellation.
- [ ] Update checked SDK access and motion completion, timeout and stop handling; preserve old motion parameters and homing sequence.
- [ ] Implement device dispatcher, per-axis state/fault events, IO readback and nonblocking vision execution.
- [ ] Wire physical control and virtual leases to the same dispatcher and define all emitted events in the correct template.
- [ ] Run all MotionStage tests, compile Python, check diffs, document migration and hardware validation requirements.

## Review focus
- Reused message IDs across different points must never cross-route results.
- IO readback is output state, not valve/cylinder mechanical confirmation.
- Vision negative inspection must differ from unreachable/malformed service responses.
- Stop during homing must prevent any subsequent homing movement.
- Missing encoder feedback must never be synthesized from planned position.
