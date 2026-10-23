import sys
import threading
import time
import unittest
from pathlib import Path
from unittest.mock import Mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from control import MANUAL_REG, ControlImpl, Rs485Client, calc_crc16_modbus
import struct
from core import SystemCommand
from simulation import SimulationImpl


def _control() -> tuple[ControlImpl, list]:
    ctrl = ControlImpl({}, ("Reactor1",), {"Reactor1": {"index": "1"}})
    events = []
    ctrl.set_event_handler(events.append)
    ctrl._running = True
    ctrl.rs485 = Mock()
    ctrl.rs485.stop.return_value = True
    ctrl.rs485.move.return_value = True
    ctrl.rs485.read_alarm.return_value = False
    return ctrl, events


class StopPressurizeControlTest(unittest.TestCase):
    def test_stop_cancels_unfinished_pressurize_and_clears_target(self) -> None:
        ctrl, events = _control()
        ctrl._last_ok = time.monotonic()
        ctrl._mirror[MANUAL_REG] = 1
        press = SystemCommand("press-1", "Reactor1", "pressurize", {"index": 1, "targetPressure": 1.5}, 1)
        worker = threading.Thread(target=ctrl._run_command, args=(press,))
        worker.start()
        deadline = time.monotonic() + 2
        while 1 not in ctrl._pressure_targets and time.monotonic() < deadline:
            time.sleep(0.01)
        self.assertEqual(1.5, ctrl._pressure_targets.get(1))

        ctrl._run_command(SystemCommand("stop-1", "Reactor1", "stopPressurize", {"index": 1}, 1))
        worker.join(timeout=3)

        self.assertFalse(worker.is_alive())
        self.assertNotIn(1, ctrl._pressure_targets)
        ctrl.rs485.stop.assert_called_once_with(1)
        names = [(event.event_name, event.message_id) for event in events]
        self.assertIn(("PRESSURE_STOPPED", "stop-1"), names)
        self.assertIn(("COMMAND_COMPLETED", "stop-1"), names)
        self.assertIn(("COMMAND_FAILED", "press-1"), names)
        self.assertNotIn(("COMMAND_COMPLETED", "press-1"), names)

    def test_stop_works_without_control_or_plc_link(self) -> None:
        ctrl, events = _control()
        ctrl._pressure_targets[1] = 1.5
        ctrl._run_command(SystemCommand("stop-1", "Reactor1", "stopPressurize", {"index": 1}, 1))
        self.assertNotIn(1, ctrl._pressure_targets)
        ctrl.rs485.stop.assert_called_once_with(1)
        self.assertEqual(
            ["PRESSURE_STOPPED", "COMMAND_COMPLETED"],
            [event.event_name for event in events],
        )

    def test_stop_failure_does_not_report_success_or_resume_control(self) -> None:
        ctrl, events = _control()
        ctrl._pressure_targets[1] = 1.5
        ctrl._mirror[11] = 10
        ctrl.rs485.stop.return_value = False
        ctrl._run_command(SystemCommand("stop-1", "Reactor1", "stopPressurize", {"index": 1}, 1))
        self.assertEqual(["COMMAND_FAILED"], [e.event_name for e in events])
        self.assertEqual("MOTOR_STOP_FAILED", events[-1].payload["reason"])
        self.assertNotIn(1, ctrl._pressure_targets)
        ctrl._pressure_step(1, 1.5)
        ctrl.rs485.move.assert_not_called()

    def test_stop_is_sent_after_inflight_move_and_blocks_stale_control_work(self) -> None:
        ctrl, events = _control()
        ctrl._pressure_targets[1] = 1.5
        ctrl._mirror[11] = 10
        # 从零开度出发，压力高于目标才允许松开；调节必须有新鲜读数。
        ctrl._pressure_samples[1] = (2.0, time.monotonic())
        moving, release, stopping, finished = (threading.Event() for _ in range(4))
        operations = []
        def move(*args):
            moving.set()
            release.wait(3)
            operations.append("move")
            return True
        def stop(channel):
            operations.append("stop")
            return True
        ctrl.rs485.move.side_effect = move
        ctrl.rs485.stop.side_effect = stop
        def run_stop():
            stopping.set()
            ctrl._run_command(SystemCommand("stop-1", "Reactor1", "stopPressurize", {"index": 1}, 1))
            finished.set()
        pid = threading.Thread(target=ctrl._pressure_step, args=(1, 1.5))
        stopper = threading.Thread(target=run_stop)
        pid.start()
        try:
            self.assertTrue(moving.wait(2))
            stopper.start()
            self.assertTrue(stopping.wait(2))
            self.assertFalse(finished.wait(0.1))
        finally:
            release.set()
            pid.join(3)
            if stopper.ident is not None:
                stopper.join(3)
        self.assertFalse(pid.is_alive())
        self.assertFalse(stopper.is_alive())
        self.assertEqual(["move", "stop"], operations)
        ctrl._pressure_step(1, 1.5)
        self.assertEqual(["move", "stop"], operations)


class MotorStopProtocolTest(unittest.TestCase):
    def test_motion_and_alarm_reject_corrupt_or_wrong_device_responses(self) -> None:
        client = Rs485Client("unused", 0, 1)
        echo = bytes.fromhex("01 10 00 fd 00 05")
        valid = echo + struct.pack('<H', calc_crc16_modbus(echo))
        client._exchange = Mock(return_value=valid)
        self.assertTrue(client.move(1, 1, 20, 100))
        client._exchange = Mock(return_value=b'\x00' * 8)
        self.assertFalse(client.move(1, 1, 20, 100))
        body = bytes([1, 4, 32]) + bytes(32)
        reply = body + struct.pack('<H', calc_crc16_modbus(body))
        client._exchange = Mock(return_value=reply)
        self.assertIs(client.read_alarm(1), False)
        self.assertIsNone(client.read_alarm(2))
        client._exchange = Mock(return_value=bytes(37))
        self.assertIsNone(client.read_alarm(1))

    def test_stop_uses_device_address_and_legacy_register_with_crc(self) -> None:
        client = Rs485Client("unused", 0, 1)
        client._exchange = Mock(return_value=bytes.fromhex("01 10 00 fe 00 01 60 39"))
        self.assertTrue(client.stop(1))
        client._exchange.assert_called_once_with(bytes.fromhex("01 10 00 fe 00 01 02 98 00 d8 4e"), 8)

    def test_stop_rejects_missing_corrupt_or_wrong_device_reply(self) -> None:
        client = Rs485Client("unused", 0, 1)
        for reply in (None, b"", b"\x00" * 8, bytes.fromhex("08 10 00 fe 00 01 60 a0")):
            with self.subTest(reply=reply):
                client._exchange = Mock(return_value=reply)
                self.assertFalse(client.stop(1))


class StopPressurizeSimulationTest(unittest.TestCase):
    def test_stop_cancels_the_unfinished_pressurize_then_completes(self) -> None:
        sim = SimulationImpl({"batchSize": "1", "intervalSec": "10", "simStepMs": "1000"})
        events = []
        sim.set_event_handler(events.append)
        sim.attach("Reactor1_sim_40", "Reactor1")
        sim.execute(SystemCommand("press-1", "Reactor1_sim_40", "pressurize", {"targetPressure": 1.5}, 1))
        events.clear()
        sim.execute(SystemCommand("stop-1", "Reactor1_sim_40", "stopPressurize", {}, 1))
        self.assertEqual(
            [
                ("COMMAND_RUNNING", "stop-1"),
                ("COMMAND_FAILED", "press-1"),
                ("PRESSURE_STOPPED", "stop-1"),
                ("COMMAND_COMPLETED", "stop-1"),
            ],
            [(event.event_name, event.message_id) for event in events],
        )


if __name__ == "__main__":
    unittest.main()
