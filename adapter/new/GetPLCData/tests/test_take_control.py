import sys
import time
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from control import ALARM_REGISTERS, MANUAL_REG, MIRROR_SIZE, PRESERVE_BEFORE_CONTROL, ControlImpl
from core import SystemCommand


class _Plc:
    def __init__(self, fail_at: int | None = None) -> None:
        self.writes: list[tuple[int, int]] = []
        self.fail_at = fail_at
        self.values = [0] * MIRROR_SIZE
        self.operations = []
        self.fail_read_at = None
        self.short_read = False

    def read_holding_registers(self, addr: int, count: int):
        self.operations.append(("read", addr, count))
        if self.fail_read_at is not None and addr <= self.fail_read_at < addr + count:
            return None
        values = self.values[addr:addr + count]
        return values[:-1] if self.short_read else values

    def write_single_register(self, addr: int, value: int) -> bool:
        self.operations.append(("write", addr, value))
        if self.fail_at is not None and len(self.writes) == self.fail_at:
            return False
        self.writes.append((addr, int(value)))
        self.values[addr] = int(value)
        return True


def _control(values: dict[int, int]) -> tuple[ControlImpl, _Plc]:
    ctrl = ControlImpl({}, ("Plant",), {"Plant": {}})
    plc = _Plc()
    ctrl.plc = plc
    ctrl._last_ok = time.monotonic()
    ctrl._mirror = [0] * MIRROR_SIZE
    for addr, value in values.items():
        ctrl._mirror[addr] = value
        plc.values[addr] = value
    return ctrl, plc


class TakeControlTest(unittest.TestCase):
    def setUp(self) -> None:
        sleeper = patch("control.time.sleep")
        sleeper.start()
        self.addCleanup(sleeper.stop)

    def test_takes_control_before_rewriting_current_setpoints(self) -> None:
        ctrl, plc = _control({212: 400, 34: 237, 35: 39, 53: 9999, 268: 1, 294: 0})
        ctrl._run_command(SystemCommand("take-1", "Plant", "takeControl", {}, 1))
        self.assertEqual(plc.writes[0], (MANUAL_REG, 1))
        preserved = dict(plc.writes[1:])
        self.assertEqual(preserved[212], 400)
        self.assertEqual(preserved[34], 237)
        self.assertEqual(preserved[35], 39)
        self.assertEqual(preserved[53], 9999)
        self.assertEqual(preserved[268], 1)
        self.assertEqual(preserved[294], 0)
        self.assertEqual(set(preserved), set(PRESERVE_BEFORE_CONTROL))
        self.assertEqual(tuple(addr for addr, _ in plc.writes[1:1 + len(ALARM_REGISTERS)]),
                         ALARM_REGISTERS)
        self.assertNotIn(0, preserved)
        self.assertNotIn(276, preserved)
        self.assertNotIn(MANUAL_REG, preserved)
        self.assertEqual([addr for addr, _ in plc.writes[-17:]],
                         list(range(226, 235)) + list(range(268, 276)))

    def test_defaults_zero_alarm_values_to_300_but_preserves_nonzero_values(self) -> None:
        ctrl, plc = _control({212: 0, 213: 275})

        self.assertIsNone(ctrl._take_control())

        restored = dict(plc.writes[1:])
        self.assertEqual(300, restored[212])
        self.assertEqual(275, restored[213])
        self.assertTrue(all(restored[addr] == 300 for addr in ALARM_REGISTERS if addr != 213))
        for addr in ALARM_REGISTERS:
            alarm_readbacks = [
                op for op in plc.operations
                if op == ("read", addr, 1)
            ]
            self.assertEqual(1, len(alarm_readbacks), f"alarm register {addr} was not read back")

    def test_does_not_take_control_when_a_setpoint_was_not_read(self) -> None:
        ctrl, plc = _control({212: 400})
        plc.fail_read_at = 289
        events = []
        ctrl.set_event_handler(events.append)
        ctrl._run_command(SystemCommand("take-1", "Plant", "takeControl", {}, 1))
        self.assertNotIn((MANUAL_REG, 1), plc.writes)
        self.assertEqual([], plc.writes)
        self.assertEqual("COMMAND_FAILED", events[-1].event_name)
        self.assertEqual("PLC_READ_FAILED", events[-1].payload["reason"])

    def test_reports_control_held_when_a_rewrite_fails_after_taking_control(self) -> None:
        ctrl, plc = _control({})
        plc.fail_at = 1
        events = []
        ctrl.set_event_handler(events.append)
        ctrl._run_command(SystemCommand("take-1", "Plant", "takeControl", {}, 1))
        self.assertEqual([(MANUAL_REG, 1)], plc.writes)
        self.assertEqual("PLC_RESTORE_WRITE_FAILED", events[-1].payload["reason"])

    def test_uses_fresh_values_even_when_cached_link_is_offline(self) -> None:
        ctrl, plc = _control({212: 400})
        ctrl._mirror[212] = 0
        ctrl._last_ok = 0
        self.assertIsNone(ctrl._take_control())
        self.assertEqual(400, dict(plc.writes)[212])
        first_write = next(i for i, op in enumerate(plc.operations) if op[0] == "write")
        read_addresses = {
            addr for _, start, count in plc.operations[:first_write]
            for addr in range(start, start + count)
        }
        self.assertTrue(set(PRESERVE_BEFORE_CONTROL) <= read_addresses)
        restore_ops = plc.operations[first_write:]
        self.assertEqual(restore_ops[0], ("write", MANUAL_REG, 1))
        self.assertTrue(all(
            write[0] == "write" and read[0] == "read"
            for write, read in zip(restore_ops[1:-1:2], restore_ops[2:-1:2])
        ))
        self.assertEqual(restore_ops[-1], ("read", MANUAL_REG, 1))
        self.assertEqual((MANUAL_REG, 1), plc.writes[0])

    def test_repeated_take_control_reads_again_even_when_already_in_control(self) -> None:
        ctrl, plc = _control({212: 400, MANUAL_REG: 1})
        self.assertIsNone(ctrl._take_control())
        plc.values[212] = 500
        plc.operations.clear()
        plc.writes.clear()
        self.assertIsNone(ctrl._take_control())
        self.assertEqual("read", plc.operations[0][0])
        self.assertEqual(500, dict(plc.writes)[212])
        self.assertEqual((MANUAL_REG, 1), plc.writes[0])

    def test_short_read_does_not_write_anything(self) -> None:
        ctrl, plc = _control({})
        plc.short_read = True
        self.assertEqual("PLC_READ_FAILED", ctrl._take_control())
        self.assertEqual([], plc.writes)


if __name__ == "__main__":
    unittest.main()
