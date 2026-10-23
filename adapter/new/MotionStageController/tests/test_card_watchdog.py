"""Offline regressions: no physical card or MQTT connection is opened."""
import sys
import threading
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from core import SystemCommand, AdapterCore, AdapterSetup, RuntimeConfig
from control import ControlImpl
from southbound import MotionEngine, VirtualCard, VirtualClock


class CardWatchdogTest(unittest.TestCase):
    def setUp(self):
        self.clock = VirtualClock()
        self.control = ControlImpl({'card': {'healthTimeoutMs': '1000', 'healthProbeIntervalMs': '100'}},
                                   ('arm', 'valve', 'vision'),
                                   {'arm': {'kind': 'arm'}, 'valve': {'kind': 'valve', 'bitIndex': '1'},
                                    'vision': {'kind': 'vision'}})
        self.card = VirtualCard(self.clock)
        self.engine = MotionEngine(self.card, self.clock)
        self.engine.initialize('local', 1, 'card', 1)
        self.control.clock = self.clock
        self.control.dispatcher.clock = self.clock
        self.control.engine = self.engine
        self.control.dispatcher.bind_engine(self.engine)
        self.events = []
        self.control.set_event_handler(self.events.append)
        self.control._poll_motion()

    def online(self):
        return {p['devicePoint']: p['online'] for p in self.control.heartbeat_points()}

    def send(self, name='MoveAbsolute', mid='move'):
        self.control.execute(SystemCommand(mid, 'arm', name, {'axis': 2, 'position': 2000}, 1))

    def test_read_failure_immediately_offline_and_rejects_commands(self):
        self.assertTrue(self.online()['arm'])
        self.card.get_axis_sample = lambda: (_ for _ in ()).throw(OSError('link lost'))
        self.control._poll_motion()
        self.assertFalse(self.online()['arm'])
        self.assertIsNotNone(self.control.engine)  # threshold controls teardown, not online
        self.card.calls.clear()
        self.send()
        self.assertEqual('CARD_OFFLINE', self.events[-1].payload['errorCode'])
        self.assertNotIn('MC_Update', [c[0] for c in self.card.calls])

    def test_no_new_samples_expires_online_even_with_open_handle(self):
        self.clock.advance(1001)
        self.assertTrue(self.engine.opened)
        self.assertFalse(self.online()['arm'])
        self.send()
        self.assertEqual('CARD_OFFLINE', self.events[-1].payload['errorCode'])

    def test_blocked_sdk_does_not_block_offline_report_or_resurrect_session(self):
        entered, release = threading.Event(), threading.Event()
        sample = self.card.get_axis_sample
        def blocked():
            entered.set()
            release.wait(3)
            return sample()
        self.card.get_axis_sample = blocked
        worker = threading.Thread(target=self.control._poll_motion)
        worker.start()
        try:
            self.assertTrue(entered.wait(1))
            self.clock.advance(1001)
            self.assertFalse(self.online()['arm'])
            self.control._watchdog_once()
            self.assertNotIn(('MC_Close',), self.card.calls)
        finally:
            release.set()
            worker.join(2)
        self.assertFalse(worker.is_alive())
        self.assertFalse(self.online()['arm'])

    def test_frozen_card_clock_is_not_healthy_despite_successful_reads(self):
        frozen = self.card.get_clock()
        self.card.get_clock = lambda: frozen
        for _ in range(11):
            self.clock.advance(100)
            self.control._poll_motion()
        self.assertFalse(self.online()['arm'])

    def test_offline_transition_wakes_heartbeat_without_waiting_period(self):
        import tempfile
        base = Path(__file__).resolve().parents[1]
        with tempfile.TemporaryDirectory() as folder:
            core = AdapterCore(AdapterSetup.load(base / 'adapterSetup.ini'),
                               RuntimeConfig.load(base / 'runtime.ini'), self.control,
                               leases_file=Path(folder) / 'leases.json')
            sent = threading.Event()
            bodies = []
            core._connected = True
            core._publish_json = lambda topic, body: (bodies.append(body), sent.set())
            thread = threading.Thread(target=core._heartbeat_loop, daemon=True)
            thread.start()
            try:
                self.card.get_axis_sample = lambda: (_ for _ in ()).throw(OSError('link lost'))
                self.control._poll_motion()
                self.assertTrue(sent.wait(0.5), 'offline heartbeat waited for periodic 10-second beat')
                self.assertFalse(next(p['online'] for p in bodies[-1]['points'] if p['devicePoint'] == 'arm'))
            finally:
                core._stop.set()
                if hasattr(core, '_heartbeat_wake'):
                    core._heartbeat_wake.set()
                thread.join(0.5)

    def test_blocked_command_cannot_report_success_after_watchdog_expiry(self):
        entered, release = threading.Event(), threading.Event()
        update = self.card.update
        def blocked(mask):
            entered.set()
            release.wait(3)
            update(mask)
        self.card.update = blocked
        worker = threading.Thread(target=lambda: self.send('Jog'))
        worker.start()
        try:
            self.assertTrue(entered.wait(1))
            self.clock.advance(1001)
            self.control._watchdog_once()
        finally:
            release.set()
            worker.join(2)
        self.assertFalse(any(e.event_name == 'COMMAND_COMPLETED' for e in self.events))

    def test_watchdog_expiry_during_setup_prevents_starting_axis(self):
        entered, release = threading.Event(), threading.Event()
        set_vel = self.card.set_vel
        def blocked(axis, vel):
            entered.set()
            release.wait(3)
            set_vel(axis, vel)
        self.card.set_vel = blocked
        worker = threading.Thread(target=self.send)
        worker.start()
        try:
            self.assertTrue(entered.wait(1))
            self.clock.advance(1001)
            self.control._watchdog_once()
        finally:
            release.set()
            worker.join(2)
        self.assertFalse(any(c[0] == 'MC_Update' for c in self.card.calls))

    def test_queued_command_rechecks_offline_state_after_dispatch_lock(self):
        attempted = threading.Event()
        dispatch = self.control.dispatcher.execute
        def intercepted(*args, **kwargs):
            attempted.set()
            return dispatch(*args, **kwargs)
        self.control.dispatcher.execute = intercepted
        with self.control.dispatcher._lock:
            worker = threading.Thread(target=self.send)
            worker.start()
            self.assertTrue(attempted.wait(1))
            self.control._io_failed(OSError('concurrent read failure'))
        worker.join(2)
        self.assertFalse(worker.is_alive())
        self.assertFalse(any(c[0] == 'MC_Update' for c in self.card.calls))
        self.assertEqual('CARD_OFFLINE', self.events[-1].payload['errorCode'])

    def test_offline_rejection_is_not_reexecuted_after_recovery(self):
        self.control._io_failed(OSError('link lost'))
        self.send(mid='offline-command')
        self.control._poll_motion()
        self.assertTrue(self.online()['arm'])
        self.send(mid='offline-command')
        self.assertFalse(any(c[0] == 'MC_Update' for c in self.card.calls))
        self.assertEqual('COMMAND_FAILED', self.events[-1].event_name)

    def test_newer_command_failure_is_not_overwritten_by_older_poll(self):
        pump = self.control.dispatcher.pump
        def failure_after_sample():
            pump()
            self.control._io_failed(OSError('command failed after successful sample'))
        self.control.dispatcher.pump = failure_after_sample
        self.control._poll_motion()
        self.assertFalse(self.online()['arm'])

    def test_initial_connection_cannot_block_start_or_heartbeat(self):
        control = ControlImpl({}, ('arm',), {'arm': {'kind': 'arm'}})
        entered, release, started = threading.Event(), threading.Event(), threading.Event()
        def blocked_connect(**kwargs):
            entered.set()
            release.wait(3)
            return False
        control._try_connect = blocked_connect
        starter = threading.Thread(target=lambda: (control.start(), started.set()))
        starter.start()
        try:
            self.assertTrue(entered.wait(1))
            self.assertTrue(started.wait(0.5), 'initial SDK call blocked northbound startup')
            self.assertEqual([{'devicePoint': 'arm', 'online': False}], control.heartbeat_points())
        finally:
            release.set()
            starter.join(2)
            control.stop()

    def test_card_clock_reset_invalidates_old_session(self):
        self.clock.advance(500)
        self.control._poll_motion()
        self.card.get_clock = lambda: 0.0
        self.clock.advance(100)
        self.control._poll_motion()
        self.assertFalse(self.online()['arm'])

    def test_disabled_axis_rejects_move_and_jog_but_io_remains_online(self):
        sample = self.card.get_axis_sample
        def disabled():
            pos, actual, status = sample()
            return pos, actual, [s & ~0x200 for s in status]
        self.card.get_axis_sample = disabled
        for name in ('MoveAbsolute', 'Jog', 'ResetMotion'):
            self.send(name, name)
            self.assertEqual('COMMAND_FAILED', self.events[-1].event_name)
            self.assertIn('not enabled', self.events[-1].payload['message'])
        self.assertTrue(self.online()['valve'])
        self.assertFalse(any(c[0] == 'MC_Update' for c in self.card.calls))

    def test_move_completion_requires_stopped_enabled_axis_and_actual_feedback(self):
        emitted = []
        self.engine.on_event = lambda *args: emitted.append(args)
        for status, actual in [(0, 2000), (0x600, 2000), (0x200, 0)]:
            self.engine.move = {'id': 'm', 'axis': 2, 'target': 2000}
            self.engine._poll_move([0, 2000, 0], [0x200, status, 0x200], [0, actual, 0])
            self.assertFalse(any(e[0] == 'evt_success' for e in emitted))

    def test_reconnect_reinitializes_and_does_not_replay_old_move(self):
        self.send()
        self.control._fail_card(OSError('power loss'))
        fresh = VirtualCard(self.clock)
        with patch('control.RealCard', return_value=fresh):
            self.assertTrue(self.control._try_connect())
        self.assertTrue(self.online()['arm'])
        self.assertEqual([('MC_AxisOn', 1), ('MC_AxisOn', 2), ('MC_AxisOn', 3)],
                         [c for c in fresh.calls if c[0] == 'MC_AxisOn'])
        self.assertFalse(any(c[0] == 'MC_Update' for c in fresh.calls))
        self.assertTrue(any(e.message_id == 'move' and e.event_name == 'COMMAND_FAILED' for e in self.events))


if __name__ == '__main__':
    unittest.main()
