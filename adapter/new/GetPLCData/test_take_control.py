import sys
import time
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))

from control import MANUAL_REG, MIRROR_SIZE, PRESERVE_BEFORE_CONTROL, ControlImpl
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

    def test_rewrites_current_setpoints_before_taking_control(self) -> None:
        ctrl, plc = _control({212: 400, 34: 237, 35: 39, 53: 9999, 268: 1, 294: 0})
        ctrl._run_command(SystemCommand("take-1", "Plant", "takeControl", {}, 1))
        self.assertEqual(plc.writes[-1], (MANUAL_REG, 1))
        preserved = dict(plc.writes[:-1])
        self.assertEqual(preserved[212], 400)
        self.assertEqual(preserved[34], 237)
        self.assertEqual(preserved[35], 39)
        self.assertEqual(preserved[53], 9999)
        self.assertEqual(preserved[268], 1)
        self.assertEqual(preserved[294], 0)
        self.assertEqual(set(preserved), set(PRESERVE_BEFORE_CONTROL))
        self.assertNotIn(0, preserved)
        self.assertNotIn(276, preserved)
        self.assertNotIn(MANUAL_REG, preserved)

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

    def test_stops_before_the_gate_when_a_rewrite_fails(self) -> None:
        ctrl, plc = _control({})
        plc.fail_at = 0
        events = []
        ctrl.set_event_handler(events.append)
        ctrl._run_command(SystemCommand("take-1", "Plant", "takeControl", {}, 1))
        self.assertEqual([], plc.writes)
        self.assertEqual("PLC_WRITE_FAILED", events[-1].payload["reason"])

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
        self.assertTrue(all(op[0] == "write" for op in plc.operations[first_write:]))
        self.assertEqual((MANUAL_REG, 1), plc.writes[-1])

    def test_repeated_take_control_reads_again_even_when_already_in_control(self) -> None:
        ctrl, plc = _control({212: 400, MANUAL_REG: 1})
        self.assertIsNone(ctrl._take_control())
        plc.values[212] = 500
        plc.operations.clear()
        plc.writes.clear()
        self.assertIsNone(ctrl._take_control())
        self.assertEqual("read", plc.operations[0][0])
        self.assertEqual(500, dict(plc.writes)[212])
        self.assertEqual((MANUAL_REG, 1), plc.writes[-1])

    def test_short_read_does_not_write_anything(self) -> None:
        ctrl, plc = _control({})
        plc.short_read = True
        self.assertEqual("PLC_READ_FAILED", ctrl._take_control())
        self.assertEqual([], plc.writes)


if __name__ == "__main__":
    unittest.main()
