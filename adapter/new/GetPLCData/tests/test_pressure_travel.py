import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from control import ControlImpl, back_pressure_addr


class PressureTravelTest(unittest.TestCase):
    def setUp(self):
        self.clock = patch('control.time.monotonic', return_value=100.0)
        self.now = self.clock.start()
        self.addCleanup(self.clock.stop)
        # 行程/等待回归使用小上限和已饱和的压差，比例公式另由专门测试覆盖。
        self.ctrl = ControlImpl({'rs485': {'pressureMaxStepTurns': '0.02',
            'pressureFullStepErrorMPa': '0.01'}}, ('Reactor1', 'Reactor2'), {
            'Reactor1': {'index': '1'}, 'Reactor2': {'index': '2'}})
        self.ctrl._running = True
        self.ctrl.rs485 = Mock()
        self.ctrl.rs485.move.return_value = True
        self.ctrl.rs485.stop.return_value = True
        self.ctrl.rs485.read_alarm.return_value = False
        self.ctrl.plc = Mock()
        self.events = []
        self.ctrl.set_event_handler(self.events.append)
        self.ctrl._pressurize('Reactor1', {'index': 1, 'targetPressure': 1.0})

    def sample(self, pressure, at=None):
        if at is not None:
            self.now.return_value = at
        values = [0] * 22
        values[0] = round(pressure * 100)
        self.ctrl.plc.read_holding_registers.return_value = values
        self.assertTrue(self.ctrl._read_pressure_samples())

    def step(self, pressure, at=None):
        self.sample(pressure, at)
        self.ctrl._pressure_step(1, 1.0)

    def test_startup_zero_and_three_point_six_turn_limit(self):
        self.assertEqual(11520, self.ctrl.max_valve_pulses)
        self.assertEqual((0, 0), (self.ctrl._valve_travel[1].low, self.ctrl._valve_travel[1].high))
        self.step(0.5)
        self.ctrl.rs485.move.assert_not_called()
        self.assertIn(1, self.ctrl._pressure_targets)
        self.assertEqual('PRESSURE_LIMIT_REACHED', self.events[-1].event_name)
        self.assertEqual('MIN', self.events[-1].payload['boundary'])
        self.step(0.5, 101)
        self.assertEqual(1, len(self.events))
        self.step(1.5, 102)
        self.assertEqual(1, self.ctrl.rs485.move.call_args.args[1])

    def test_upper_and_lower_steps_are_clipped_and_reverse_is_allowed(self):
        travel = self.ctrl._valve_travel[1]
        travel.low = travel.high = self.ctrl.max_valve_pulses - 10
        self.step(2.0)
        self.assertEqual(10, self.ctrl.rs485.move.call_args.args[3])
        self.step(2.0, 103)
        self.assertEqual(1, self.ctrl.rs485.move.call_count)
        self.assertEqual('MAX', self.events[-1].payload['boundary'])
        self.step(0.5, 106)
        self.assertEqual(0, self.ctrl.rs485.move.call_args.args[1])
        travel.pending = None
        travel.ready_after = 0
        self.ctrl._pressure_decision_after[1] = 0
        travel.low = travel.high = 5
        self.step(0.5, 109)
        self.assertEqual(5, self.ctrl.rs485.move.call_args.args[3])

    def test_no_motion_in_tolerance_or_from_reused_pressure_sample(self):
        self.step(1.0)
        self.ctrl.rs485.move.assert_not_called()
        self.step(2.0, 101)
        args = self.ctrl.rs485.move.call_args.args
        self.assertEqual(64, args[3])
        self.now.return_value = 102
        self.ctrl._pressure_step(1, 1.0)
        self.assertEqual(1, self.ctrl.rs485.move.call_count)
        self.step(2.0, 104)
        self.assertEqual(2, self.ctrl.rs485.move.call_count)

    def test_stale_or_failed_pressure_read_stops_and_reports_failure(self):
        for fail_read in (True, False):
            with self.subTest(fail_read=fail_read):
                self.ctrl._pressurize('Reactor1', {'index': 1, 'targetPressure': 1.0})
                self.sample(2.0, 100)
                if fail_read:
                    self.ctrl.plc.read_holding_registers.return_value = None
                    self.assertFalse(self.ctrl._read_pressure_samples())
                else:
                    self.now.return_value = 110
                self.ctrl._pressure_step(1, 1.0)
                self.assertNotIn(1, self.ctrl._pressure_targets)
                self.assertEqual('PRESSURE_CONTROL_FAILED', self.events[-1].event_name)
                self.assertEqual('PRESSURE_FEEDBACK_INVALID', self.events[-1].payload['reason'])
        self.assertEqual(2, self.ctrl.rs485.stop.call_count)
        self.ctrl.rs485.move.assert_not_called()

    def test_stop_mid_move_preserves_uncertainty_and_new_target_does_not_zero(self):
        self.step(2.0)
        pulse = self.ctrl.rs485.move.call_args.args[3]
        self.ctrl._stop_pressurize({'index': 1})
        travel = self.ctrl._valve_travel[1]
        self.assertEqual((0, pulse), (travel.low, travel.high))
        self.ctrl._pressurize('Reactor1', {'index': 1, 'targetPressure': 1.0})
        self.step(0.5, 101)
        self.assertEqual(1, self.ctrl.rs485.move.call_count)
        self.assertEqual((0, pulse), (travel.low, travel.high))

    def test_move_failure_blocks_further_motion_and_does_not_fake_position(self):
        self.ctrl.rs485.move.return_value = False
        self.step(2.0)
        travel = self.ctrl._valve_travel[1]
        self.assertTrue(travel.blocked)
        self.assertEqual(0, travel.low)
        self.assertGreater(travel.high, 0)
        self.assertNotIn(1, self.ctrl._pressure_targets)
        result = self.ctrl._pressurize('Reactor1', {'index': 1, 'targetPressure': 1.0})
        self.assertEqual('VALVE_POSITION_UNKNOWN', result)

    def test_channels_are_independent_and_pressure_reads_are_live(self):
        self.ctrl._pressurize('Reactor2', {'index': 2, 'targetPressure': 1.0})
        self.step(2.0)
        self.assertEqual((0, 0), (self.ctrl._valve_travel[2].low, self.ctrl._valve_travel[2].high))
        self.ctrl.plc.read_holding_registers.assert_called_with(11, 22)
        self.assertEqual(2.0, self.ctrl._pressure(1))
        self.assertEqual(200, self.ctrl._mirror[back_pressure_addr(1)])

    def test_invalid_targets_do_not_start_motor_work(self):
        for target in (float('nan'), float('inf'), -1):
            self.assertIsInstance(self.ctrl._pressurize('Reactor1', {'index': 1, 'targetPressure': target}), str)

    def test_fault_terminates_waiting_command_with_actual_reason(self):
        watch = self.ctrl._pressurize('Reactor1', {'index': 1, 'targetPressure': 1.0})
        self.ctrl.plc.read_holding_registers.return_value = None
        self.ctrl._read_pressure_samples()
        self.ctrl._pressure_step(1, 1.0)
        self.ctrl._follow_hold('Reactor1', 'pressure-command', watch)
        event = self.events[-1]
        self.assertEqual(('COMMAND_FAILED', 'pressure-command'), (event.event_name, event.message_id))
        self.assertEqual('PRESSURE_FEEDBACK_INVALID', event.payload['reason'])

    def test_full_open_and_close_sweeps_stay_inside_allowed_pulses(self):
        for i in range(1, 201):
            self.step(3.0, 100 + i * 3)
        opening_pulses = sum(call.args[3] for call in self.ctrl.rs485.move.call_args_list)
        self.assertEqual(11520, opening_pulses)
        self.ctrl.rs485.move.reset_mock()
        for i in range(1, 201):
            self.step(0.0, 700 + i * 3)
        closing_pulses = sum(call.args[3] for call in self.ctrl.rs485.move.call_args_list)
        self.assertEqual(11520, closing_pulses)
        self.assertEqual((0, 0), (self.ctrl._valve_travel[1].low, self.ctrl._valve_travel[1].high))

    def test_new_command_requires_pressure_measured_after_command(self):
        self.sample(1.0, 100)
        self.now.return_value = 101
        self.ctrl._pressurize('Reactor1', {'index': 1, 'targetPressure': 1.0})
        self.assertFalse(self.ctrl._pressure_ready(1, 1.0))
        self.sample(1.0, 102)
        self.assertTrue(self.ctrl._pressure_ready(1, 1.0))

    def test_fixed_step_waits_and_requires_sample_after_settling(self):
        self.step(4.0, 100)
        self.assertEqual((1, 1, 10, 64), self.ctrl.rs485.move.call_args.args)
        self.step(1.0, 100.5)
        self.assertFalse(self.ctrl._pressure_ready(1, 1.0))
        self.step(0.5, 102.0)
        self.assertEqual(1, self.ctrl.rs485.move.call_count)
        self.now.return_value = 102.5
        self.ctrl._pressure_step(1, 1.0)
        self.assertEqual(1, self.ctrl.rs485.move.call_count)
        self.step(0.5, 102.6)
        self.assertEqual((1, 0, 10, 64), self.ctrl.rs485.move.call_args.args)

    def test_target_replacement_cannot_skip_pressure_wait(self):
        self.step(4.0, 100)
        self.now.return_value = 100.5
        self.ctrl._pressurize('Reactor1', {'index': 1, 'targetPressure': 1.0})
        self.step(4.0, 101)
        self.assertEqual(1, self.ctrl.rs485.move.call_count)
        self.step(4.0, 103)
        self.assertEqual(2, self.ctrl.rs485.move.call_count)

    def test_stop_while_waiting_preserves_completed_step_and_blocks_more_motion(self):
        self.step(4.0, 100)
        self.now.return_value = 101
        self.ctrl._stop_pressurize({'index': 1})
        travel = self.ctrl._valve_travel[1]
        self.assertEqual((64, 64), (travel.low, travel.high))
        self.step(4.0, 103)
        self.assertEqual(1, self.ctrl.rs485.move.call_count)
        self.ctrl.rs485.stop.assert_called_once_with(1)

    def test_step_and_wait_settings_are_configurable_and_validated(self):
        configured = ControlImpl({'rs485': {'pressureStepTurns': '0.01',
            'pressureStepSpeedRpm': '20', 'pressureSettleSec': '5'}}, ())
        self.assertEqual(32, configured.pressure_step_pulses)
        self.assertEqual(20, configured.pressure_step_speed)
        self.assertEqual(5, configured.pressure_settle_sec)
        for key, value in [('pressureStepTurns', '0'), ('pressureStepTurns', 'nan'),
                           ('pressureStepSpeedRpm', '0'), ('pressureSettleSec', '0'),
                           ('pressureSettleSec', '-1'),
                           ('pressureSettleSec', 'inf')]:
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                ControlImpl({'rs485': {key: value}}, ())

    def test_waiting_channel_does_not_block_other_channel(self):
        self.step(4.0, 100)
        self.ctrl._pressurize('Reactor2', {'index': 2, 'targetPressure': 1.0})
        values = [0] * 22
        values[0] = values[3] = 400
        self.now.return_value = 100.1
        self.ctrl.plc.read_holding_registers.return_value = values
        self.ctrl._read_pressure_samples()
        self.ctrl._pressure_step(1, 1.0)
        self.ctrl._pressure_step(2, 1.0)
        self.assertEqual([1, 2], [call.args[0] for call in self.ctrl.rs485.move.call_args_list])

    def test_read_failure_during_wait_still_stops_and_cancels(self):
        self.step(4.0, 100)
        self.now.return_value = 100.5
        self.ctrl.plc.read_holding_registers.return_value = None
        self.ctrl._read_pressure_samples()
        self.ctrl._pressure_step(1, 1.0)
        self.ctrl.rs485.stop.assert_called_once_with(1)
        self.assertNotIn(1, self.ctrl._pressure_targets)
        self.assertEqual('PRESSURE_CONTROL_FAILED', self.events[-1].event_name)


class ProportionalPressureStepTest(unittest.TestCase):
    def run_step(self, pressure, target=1.0, settings=None, position=5000):
        ctrl = ControlImpl({'rs485': settings or {}}, ('Reactor1',), {'Reactor1': {'index': '1'}})
        ctrl._running = True
        ctrl.rs485 = Mock()
        ctrl.rs485.move.return_value = True
        ctrl.rs485.read_alarm.return_value = False
        ctrl.rs485.stop.return_value = True
        with patch('control.time.monotonic', return_value=100):
            ctrl._pressurize('Reactor1', {'index': 1, 'targetPressure': target})
            ctrl._pressure_samples[1] = (pressure, 100)
            ctrl._valve_travel[1].low = ctrl._valve_travel[1].high = position
            ctrl._pressure_step(1, target)
        return ctrl

    def test_error_scales_step_and_caps_at_half_turn(self):
        for error, pulses in ((0.02, 32), (0.2, 320), (0.5, 800), (1, 1600), (3, 1600)):
            for sign in (-1, 1):
                with self.subTest(error=error, sign=sign):
                    ctrl = self.run_step(4 + sign * error, target=4)
                    self.assertEqual((1, 1 if sign > 0 else 0, 10, pulses), ctrl.rs485.move.call_args.args)

    def test_zero_target_deadband_and_remaining_travel(self):
        ctrl = self.run_step(0.2, target=0)
        self.assertEqual(320, ctrl.rs485.move.call_args.args[3])
        for pressure in (1.0, 1.01, 0.99):
            self.run_step(pressure).rs485.move.assert_not_called()
        ctrl = self.run_step(0.0, position=7)
        self.assertEqual(7, ctrl.rs485.move.call_args.args[3])

    def test_configurable_full_step_error_and_maximum(self):
        ctrl = self.run_step(1.5, settings={'pressureMaxStepTurns': '0.4', 'pressureFullStepErrorMPa': '2'})
        self.assertEqual(320, ctrl.rs485.move.call_args.args[3])
        for value in ('0', '-1', 'nan', 'inf'):
            with self.subTest(value=value), self.assertRaises(ValueError):
                ControlImpl({'rs485': {'pressureFullStepErrorMPa': value}}, ())

    def test_large_step_waits_for_movement_then_pressure_settling(self):
        ctrl = self.run_step(4.0)
        self.assertAlmostEqual(105.05, ctrl._pressure_decision_after[1])
        with patch('control.time.monotonic', return_value=103):
            ctrl._pressure_samples[1] = (2, 103)
            ctrl._pressure_step(1, 1.0)
        self.assertEqual(1, ctrl.rs485.move.call_count)
        with patch('control.time.monotonic', return_value=105.1):
            ctrl._pressure_step(1, 1.0)
            self.assertEqual(1, ctrl.rs485.move.call_count)
            ctrl._pressure_samples[1] = (1.2, 105.1)
            ctrl._pressure_step(1, 1.0)
        self.assertEqual(320, ctrl.rs485.move.call_args.args[3])

    def test_opening_file_survives_restart_and_absent_or_invalid_file_stays_tight(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "valve-travel.json"
            settings = {
                "valveTravelFile": str(path),
                "pressureMaxStepTurns": "0.02",
                "pressureFullStepErrorMPa": "0.01",
            }
            ctrl = ControlImpl({"rs485": settings}, ("Reactor1",), {"Reactor1": {"index": "1"}})
            self.assertFalse(path.exists())
            self.assertEqual((0, 0), (ctrl._valve_travel[1].low, ctrl._valve_travel[1].high))
            ctrl._running = True
            ctrl.rs485 = Mock()
            ctrl.rs485.move.return_value = True
            ctrl.rs485.stop.return_value = True
            ctrl.rs485.read_alarm.return_value = False
            ctrl.plc = Mock()
            values = [0] * 22
            values[0] = 200
            ctrl.plc.read_holding_registers.return_value = values
            with patch("control.time.monotonic", return_value=1000.0):
                ctrl._pressurize("Reactor1", {"index": 1, "targetPressure": 1.0})
                self.assertTrue(ctrl._read_pressure_samples())
                ctrl._pressure_step(1, 1.0)
            saved = json.loads(path.read_text(encoding="utf-8"))
            self.assertEqual(0, saved["channels"]["1"]["low"])
            self.assertGreater(saved["channels"]["1"]["high"], 0)
            restarted = ControlImpl({"rs485": settings}, ("Reactor1",), {"Reactor1": {"index": "1"}})
            self.assertEqual(
                (saved["channels"]["1"]["low"], saved["channels"]["1"]["high"]),
                (restarted._valve_travel[1].low, restarted._valve_travel[1].high),
            )
            self.assertIsNone(restarted._valve_travel[1].pending)
            self.assertEqual((0, 0), (restarted._valve_travel[2].low, restarted._valve_travel[2].high))
            mismatched = dict(settings)
            mismatched["pulsesPerTurn"] = "1600"
            ignored = ControlImpl({"rs485": mismatched}, ("Reactor1",), {"Reactor1": {"index": "1"}})
            self.assertEqual((0, 0), (ignored._valve_travel[1].low, ignored._valve_travel[1].high))
            path.write_text("{", encoding="utf-8")
            broken = ControlImpl({"rs485": settings}, ("Reactor1",), {"Reactor1": {"index": "1"}})
            self.assertEqual((0, 0), (broken._valve_travel[1].low, broken._valve_travel[1].high))


if __name__ == '__main__':
    unittest.main()
