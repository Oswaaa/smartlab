from pathlib import Path
import configparser
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from core import AdapterSetup, SystemCommand
from southbound import MotionEngine, VirtualCard, VirtualClock
from devices import DeviceDispatcher

BASE = Path(__file__).resolve().parent


class DeviceTest(unittest.TestCase):
    def setUp(self):
        self.setup = AdapterSetup.load(BASE / 'adapterSetup.ini')
        self.clock = VirtualClock()
        self.card = VirtualCard(self.clock)
        self.engine = MotionEngine(self.card, self.clock)
        self.engine.initialize('local', 1, 'card', 1)
        self.events = []
        self.data = []
        self.runtime = DeviceDispatcher(self.setup.point_fields, self.clock, self.events.append, self.data.append)
        self.runtime.bind_engine(self.engine)
        self.runtime.vision_get = lambda: 'true_true'

    def send(self, point, name, params=None, mid='request'):
        self.runtime.execute(SystemCommand(mid, point, name, params or {}, 1))

    def tick(self, ms=100):
        self.clock.advance(ms)
        self.runtime.pump()

    def terminal(self, point):
        return [e for e in self.events if e.device_point == point and e.event_name in ('COMMAND_COMPLETED', 'COMMAND_FAILED')]

    def test_contract_has_four_templates_and_eleven_points(self):
        self.assertEqual(11, len(self.setup.physical_points))
        self.assertEqual(4, len(set(self.setup.point_template.values())))
        self.assertEqual(7, len(self.setup.attribute_mapping['arm_001']))
        self.assertEqual('0', self.setup.point_fields['arm_001']['gripperBit'])
        self.assertNotIn('limitNstate', self.setup.attribute_mapping['arm_001'])

    def test_corrector_and_valve_share_message_id_without_cross_routing(self):
        self.send('corrector_001', 'SetExtDoBit', {'Value': 1})
        self.send('valve_01', 'SetExtDoBit', {'Value': 0})
        self.assertEqual('COMMAND_COMPLETED', self.terminal('valve_01')[-1].event_name)
        self.assertEqual([], self.terminal('corrector_001'))
        self.tick(1999)
        self.assertEqual([], self.terminal('corrector_001'))
        self.tick(1)
        self.assertEqual('COMMAND_COMPLETED', self.terminal('corrector_001')[-1].event_name)
        self.assertEqual(1, self.card.get_ext_do_bit(0, 9))

    def test_channel_cannot_be_overridden_and_invalid_value_never_writes(self):
        self.send('valve_01', 'SetExtDoBit', {'Value': 1, 'BitIndex': 9})
        self.assertIn(('MC_SetExtDoBit', 0, 1, 1), self.card.calls)
        count = len(self.card.calls)
        self.send('valve_02', 'SetExtDoBit', {'Value': 2}, 'bad')
        self.assertEqual('INVALID_PARAMETERS', self.terminal('valve_02')[-1].payload['errorCode'])
        self.assertEqual(count, len(self.card.calls))

    def test_wrong_device_command_and_busy_corrector_are_rejected(self):
        self.send('valve_01', 'MoveAbsolute', {'axis': 1, 'position': 20})
        self.assertEqual('UNSUPPORTED_COMMAND', self.terminal('valve_01')[-1].payload['errorCode'])
        self.send('corrector_001', 'SetExtDoBit', {'Value': 1}, 'first')
        self.send('corrector_001', 'SetExtDoBit', {'Value': 0}, 'second')
        self.assertEqual('BUSY', self.terminal('corrector_001')[-1].payload['errorCode'])
        self.tick(2000)
        self.assertEqual('first', self.terminal('corrector_001')[-1].message_id)

    def test_sdk_error_does_not_report_success(self):
        def fail(*args):
            raise OSError('SDK error 7')
        self.card.set_ext_do_bit = fail
        self.send('valve_01', 'SetExtDoBit', {'Value': 1})
        self.assertEqual(['COMMAND_FAILED'], [e.event_name for e in self.terminal('valve_01')])

    def test_vision_works_without_motion_card(self):
        self.runtime.bind_engine(None)
        self.send('vision_001', 'VisionCheck', {'tube': 1})
        self.runtime.poll_vision()
        self.assertEqual('COMMAND_COMPLETED', self.terminal('vision_001')[-1].event_name)
        self.assertTrue(self.terminal('vision_001')[-1].payload['closed'])
        names = [e.event_name for e in self.events]
        self.assertIn('TUBE1_CLOSED', names)
        self.assertIn('TUBE4_CLOSED', names)
        self.assertNotIn('TUBE1_OPEN', names)

    def test_vision_rejection_and_transport_failure_are_different(self):
        for body, code, name in [('true_false_1,3', 'TUBE_OPEN', 'TUBE2_CLOSED'),
                                 ('false_false', 'LABELS_MISSING', None),
                                 (None, 'VISION_UNAVAILABLE', None),
                                 ('garbage', 'VISION_INVALID_RESPONSE', None)]:
            with self.subTest(body=body):
                self.events.clear()
                self.runtime.vision_get = lambda b=body: b
                self.send('vision_001', 'VisionCheck', {'tube': 1}, mid=str(code))
                for _ in range(10):
                    self.runtime.poll_vision()
                    self.clock.advance(200)
                self.assertEqual(code, self.terminal('vision_001')[-1].payload['errorCode'])
                self.assertNotIn('TUBE1_OPEN', [e.event_name for e in self.events])
                if name:
                    self.assertIn(name, [e.event_name for e in self.events])

    def test_gripper_uses_bit_zero_and_leaves_motion_running(self):
        self.send('arm_001', 'MoveAbsolute', {'axis': 1, 'position': 800}, mid='move')
        self.send('arm_001', 'SetGripper', {'Value': 1, 'BitIndex': 9}, mid='grip')
        self.assertEqual(1, self.card.get_ext_do_bit(0, 0))
        self.assertEqual(0, self.card.get_ext_do_bit(0, 9))
        self.assertEqual('grip', self.terminal('arm_001')[-1].message_id)
        self.assertTrue(self.terminal('arm_001')[-1].payload['gripperOpen'])
        self.assertIn('GRIPPER_OPENED', [e.event_name for e in self.events])
        self.assertEqual('move', self.runtime.pending['arm_001'].message_id)
        self.send('arm_001', 'SetGripper', {'Value': 2}, mid='bad')
        self.assertEqual('INVALID_PARAMETERS', self.terminal('arm_001')[-1].payload['errorCode'])
        self.assertEqual('move', self.runtime.pending['arm_001'].message_id)

    def test_arm_motion_event_does_not_name_an_axis(self):
        self.tick()
        self.assertIn('STOPPED', [e.event_name for e in self.events if e.device_point == 'arm_001'])
        self.send('arm_001', 'Jog', {'axis': 1, 'dir': 1})
        self.tick()
        moving = [e for e in self.events if e.device_point == 'arm_001' and e.event_name == 'MOVING']
        self.assertEqual(1, len(moving))
        self.assertNotIn('axis', moving[-1].payload)
        for _ in range(5):
            self.tick()
        names = [e.event_name for e in self.events if e.device_point == 'arm_001']
        self.assertEqual('STOPPED', names[-1])
        self.assertEqual(1, names.count('MOVING'))

    def test_telemetry_and_events_stay_on_their_device(self):
        self.tick()
        arm = [d for d in self.data if d.device_point == 'arm_001'][-1]
        self.assertEqual(7, len(arm.fields))
        self.assertIs(False, arm.fields['gripperOpen'])
        self.assertIn('axis1_pos', arm.fields)
        self.assertIsInstance(arm.fields['axis1_status'], str)
        self.assertEqual('到位', arm.fields['axis1_status'])
        self.assertEqual('到位', arm.fields['axis3_status'])
        self.assertNotIn('axis1_planned_pos', arm.fields)
        self.assertNotIn('axis1_actual_pos', arm.fields)
        self.assertNotIn('limitNstate', arm.fields)
        valve = [d for d in self.data if d.device_point == 'valve_01'][-1]
        self.assertEqual({'isOpen': False}, valve.fields)
        parser = configparser.ConfigParser(interpolation=None)
        parser.optionxform = str
        parser.read(BASE / 'adapterSetup.ini', encoding='utf-8')
        for event in self.events:
            template = self.setup.point_template[event.device_point]
            declared = set()
            for group in ('cmdEvents', 'opEvents'):
                section = f'deviceTemplates.{template}.events.{group}'
                if parser.has_section(section):
                    declared.update(parser.options(section))
            self.assertIn(event.event_name, declared)

    def test_unchanged_telemetry_is_only_repeated_at_keepalive(self):
        def valve_samples():
            return [d for d in self.data if d.device_point == 'valve_01']

        self.tick()
        self.assertEqual(1, len(valve_samples()))
        for _ in range(9):
            self.tick()
        self.assertEqual(1, len(valve_samples()))
        self.tick()
        self.assertEqual(2, len(valve_samples()))
        self.send('valve_01', 'SetExtDoBit', {'Value': 1})
        self.assertEqual({'isOpen': True}, valve_samples()[-1].fields)
        self.assertEqual(3, len(valve_samples()))
        self.tick()
        self.assertEqual(3, len(valve_samples()))

    def test_moving_arm_reports_every_poll(self):
        self.tick()
        self.send('arm_001', 'MoveAbsolute', {'axis': 1, 'position': 5000}, mid='move')
        before = len([d for d in self.data if d.device_point == 'arm_001'])
        for _ in range(5):
            self.tick()
        after = len([d for d in self.data if d.device_point == 'arm_001'])
        self.assertEqual(5, after - before)

    def test_stop_cancels_homing_without_restarting_axes(self):
        self.send('arm_001', 'ResetMotion', mid='home')
        self.tick()
        self.send('arm_001', 'Stop', mid='stop')
        self.card.calls.clear()
        for _ in range(10):
            self.tick()
        self.assertFalse(any(c[0] == 'MC_Update' for c in self.card.calls))
        self.assertEqual(0, self.engine.reset)
        self.assertTrue(any(e.message_id == 'home' and e.event_name == 'COMMAND_FAILED' for e in self.events))
        self.assertTrue(any(e.message_id == 'stop' and e.event_name == 'COMMAND_COMPLETED' for e in self.events))


if __name__ == '__main__':
    unittest.main()
