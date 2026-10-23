"""对照旧 MotionController 的调用顺序和完成条件。不打开真卡。"""

from __future__ import annotations

import sys
import threading
import time
import unittest
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from core import AdapterSetup, RuntimeConfig, SystemCommand  # noqa: E402
from simulation import SimulationImpl  # noqa: E402
from control import ControlImpl  # noqa: E402
from southbound import (  # noqa: E402
    DEFAULT_DLL,
    LegacyEventTranslator,
    MotionEngine,
    MotionParams,
    RealCard,
    TAllSysStatusDataSX,
    VirtualCard,
    VirtualClock,
    advance_and_pump,
)


def engine() -> tuple[MotionEngine, VirtualCard]:
    clock = VirtualClock()
    card = VirtualCard(clock)
    motion = MotionEngine(card, clock)
    ok = motion.initialize("192.168.0.200", 60000, "192.168.0.199", 60000)
    if not ok:
        raise AssertionError("虚拟卡初始化失败")
    return motion, card


POINT_CATALOG = {
    "arm_001": {"kind": "arm"},
    "valve_01": {"kind": "valve", "bitIndex": "1", "settleTimeMs": "0"},
    "valve_02": {"kind": "valve", "bitIndex": "2", "settleTimeMs": "0"},
    "corrector_001": {"kind": "corrector", "bitIndex": "9", "settleTimeMs": "2000"},
    "vision_001": {"kind": "vision"},
}


def _simulation() -> SimulationImpl:
    return SimulationImpl({
        "batchSize": "5",
        "intervalSec": "0.5",
        "simStepMs": "1000",
        "points": POINT_CATALOG,
        "vision": {"maxAttempts": "2", "retryIntervalMs": "200"},
    })


def _virtual_control(points: tuple[str, ...] | None = None):
    selected = points or ("arm_001", "valve_01", "valve_02", "corrector_001", "vision_001")
    control = ControlImpl(
        {"vision": {"maxAttempts": "2", "retryIntervalMs": "200", "requestTimeoutSec": "2"}},
        selected,
        {point: POINT_CATALOG[point] for point in selected},
    )
    clock = VirtualClock()
    card = VirtualCard(clock, control.motion)
    motion = MotionEngine(card, clock, control.motion)
    motion.http_get = lambda: None
    if not motion.initialize("127.0.0.1", 1, "127.0.0.1", 1):
        raise AssertionError("虚拟卡初始化失败")
    control.clock = clock
    control.dispatcher.clock = clock
    control.engine = motion
    control.dispatcher.bind_engine(motion)
    control._poll_motion()
    return control, card, clock


def _failed(sim: SimulationImpl, found: list[Any], point: str, command: str) -> str:
    sim.execute(SystemCommand("rejected", point, command, {"axis": 1, "position": 10}, 1))
    return found[-1].payload["errorCode"]


def events_of(motion: MotionEngine) -> list[tuple[str, str, str]]:
    found: list[tuple[str, str, str]] = []
    motion.on_event = lambda kind, message_id, message: found.append((kind, message_id, message))
    return found


class SouthboundTest(unittest.TestCase):
    def test_status_struct_matches_header_layout(self) -> None:
        self.assertEqual(344, __import__("ctypes").sizeof(TAllSysStatusDataSX))

    def test_axis_status_uses_manual_text(self) -> None:
        from southbound import describe_axis_status
        self.assertEqual("运动中", describe_axis_status(0x400))
        self.assertEqual("到位", describe_axis_status(0x200 | 0x800))
        self.assertEqual("急停、正硬限位、运动中", describe_axis_status(0x1 | 0x20 | 0x400))
        self.assertEqual("静止", describe_axis_status(0))
        self.assertEqual("静止", describe_axis_status(0x10 | 0x1000 | 0x8000))

    def test_init_call_order(self) -> None:
        _motion, card = engine()
        self.assertEqual(
            [
                ("MC_StartDebugLog", 0),
                ("MC_Open", 1, "192.168.0.200", 60000, "192.168.0.199", 60000),
                ("MC_SetStopDec", 1, 0.5, 5.0),
                ("MC_SetStopDec", 2, 0.5, 5.0),
                ("MC_SetStopDec", 3, 0.5, 5.0),
                ("MC_LmtsOn", 1, -1),
                ("MC_LmtsOn", 2, -1),
                ("MC_LmtsOn", 3, -1),
                ("MC_LmtSns", 0),
                ("MC_AxisOn", 1),
                ("MC_AxisOn", 2),
                ("MC_AxisOn", 3),
            ],
            card.calls,
        )

    def test_move_absolute_uses_fixed_axis_speeds(self) -> None:
        motion, card = engine()
        found = events_of(motion)
        card.calls.clear()
        motion.submit("MoveAbsolute", "m1", {"axis": 1, "position": 50000})
        self.assertEqual(("MC_PrfTrap", 1), card.calls[0])
        self.assertEqual(("MC_SetTrapPrm", 1, 0.05, 0.05, 0.0, 50), card.calls[1])
        self.assertEqual(("MC_SetPos", 1, 50000), card.calls[2])
        self.assertEqual(("MC_SetVel", 1, 80.0), card.calls[3])
        self.assertEqual(("MC_Update", 0x0001), card.calls[4])
        advance_and_pump(motion, 5)
        self.assertGreater(card.pos[0], 100.0)
        self.assertLess(card.pos[0], 8000.0)
        advance_and_pump(motion, 40)
        self.assertIn(("evt_success", "m1", "Target position reached."), found)
        self.assertEqual(50000.0, card.pos[0])
        self.assertFalse(card.running[0])
        advance_and_pump(motion, 10)
        self.assertEqual(50000.0, card.pos[0])
        self.assertEqual(0.0, card.vel[0])
        self.assertEqual(("evt_ack", "m1", "Command parsed: MoveAbsolute"), found[0])
        self.assertEqual(("evt_started", "m1", "Moving axis 1"), found[1])

        card.calls.clear()
        motion.submit("MoveAbsolute", "m3", {"axis": 3, "position": 300})
        self.assertEqual(("MC_SetTrapPrm", 3, 0.05, 0.05, 0.0, 50), card.calls[1])
        self.assertEqual(("MC_SetVel", 3, 3.0), card.calls[3])
        card.calls.clear()
        motion.submit("MoveAbsolute", "m2", {"axis": 2, "position": 100})
        self.assertEqual(("MC_SetTrapPrm", 2, 0.07, 0.07, 0.0, 50), card.calls[1])
        self.assertEqual(("MC_SetVel", 2, 25.0), card.calls[3])

    def test_jog_watchdog_and_single_axis_stop(self) -> None:
        motion, card = engine()
        found = events_of(motion)
        card.calls.clear()
        motion.submit("Jog", "j1", {"axis": 1, "dir": 0, "speed": 10})
        self.assertEqual(("MC_PrfJog", 1), card.calls[0])
        self.assertEqual(("MC_SetJogPrm", 1, 0.5, 0.5, 0.1), card.calls[1])
        self.assertEqual(("MC_SetVel", 1, -1.0), card.calls[2])
        self.assertEqual(("MC_Update", 1), card.calls[3])
        before = len(card.calls)
        motion.submit("Jog", "j2", {"axis": 1, "dir": 1, "speed": 10})
        self.assertEqual(before, len(card.calls))
        self.assertIn(("evt_success", "j1", "Jog heartbeat accepted."), found)
        advance_and_pump(motion, 2)
        self.assertNotIn(("MC_Stop", 1, 1), card.calls)
        advance_and_pump(motion, 1)
        self.assertIn(("MC_Stop", 1, 1), card.calls)

        card.calls.clear()
        motion.submit("Jog", "j3", {"axis": 2})
        motion.submit("JogStop", "js", {"axis": 2})
        self.assertIn(("MC_Stop", 2, 2), card.calls)
        self.assertIn(("evt_success", "js", "Jog stopped."), found)

        found.clear()
        card.calls.clear()
        motion.submit("Jog", "jz", {"axis": 3, "dir": 1})
        self.assertIn(("MC_SetVel", 3, 10.0), card.calls)
        found.clear()
        motion.submit("Jog", "bad", {"axis": 4})
        self.assertEqual([("evt_ack", "bad", "Command parsed: Jog")], found)

    def test_stop_all_and_unknown(self) -> None:
        motion, card = engine()
        found = events_of(motion)
        card.calls.clear()
        motion.submit("Stop", "s1", {})
        self.assertIn(("MC_Stop", 0xFFFF, 0xFFFF), card.calls)
        self.assertIn(("evt_started", "s1", "Stopping all axes..."), found)
        self.assertIn(("evt_success", "s1", "Axes stopped successfully."), found)
        found.clear()
        motion.submit("NoSuch", "u1", {})
        self.assertEqual(
            [
                ("evt_ack", "u1", "Command parsed: NoSuch"),
                ("evt_error", "u1", "Unknown operation code."),
            ],
            found,
        )

    def test_ext_do_bit_nine_holds_two_seconds(self) -> None:
        motion, card = engine()
        found = events_of(motion)
        card.calls.clear()
        motion.submit("SetExtDoBit", "io", {"BitIndex": 9, "Value": 1})
        self.assertEqual(("MC_SetExtDoBit", 0, 9, 1), card.calls[0])
        self.assertIn(("evt_started", "io", "Setting IO bit..."), found)
        advance_and_pump(motion, 19)
        self.assertNotIn(("evt_success", "io", "IO Bit set successfully."), found)
        advance_and_pump(motion, 1)
        self.assertIn(("evt_success", "io", "IO Bit set successfully."), found)

        found.clear()
        motion.submit("SetExtDoBit", "io2", {"BitIndex": 3, "Value": 0})
        self.assertIn(("evt_success", "io2", "IO Bit set successfully."), found)

    def test_homing_sequence_ends_at_zero_with_final_soft_limits(self) -> None:
        motion, card = engine()
        found = events_of(motion)
        card.calls.clear()
        motion.submit("ResetMotion", "h1", {})
        self.assertEqual(
            [
                ("MC_SetSoftLimit", 1, -2147483645, 0x7FFFFFFF),
                ("MC_SetSoftLimit", 2, -2147483645, 0x7FFFFFFF),
                ("MC_SetSoftLimit", 3, -2147483645, 0x7FFFFFFF),
            ],
            card.calls[:3],
        )
        advance_and_pump(motion, 200)
        self.assertIn(("evt_started", "h1", "Hardware homing sequence started."), found)
        self.assertIn(("evt_success", "h1", "Hardware homing completed."), found)
        limits = [call for call in card.calls if call[0] == "MC_SetSoftLimit"]
        self.assertEqual(("MC_SetSoftLimit", 1, -1000, 1900000), limits[-3])
        self.assertEqual(("MC_SetSoftLimit", 2, -100, 430000), limits[-2])
        self.assertEqual(("MC_SetSoftLimit", 3, -10, 45000), limits[-1])
        self.assertEqual(0, motion.reset)
        self.assertEqual(1, motion.ready)
        self.assertEqual([0, 0, 0], [int(card.pos[i]) for i in range(3)])
        names = [call[0] for call in card.calls]
        self.assertIn("MC_SetPos", names)
        self.assertIn(("MC_SetPos", 1, 50000), card.calls)
        self.assertIn(("MC_SetVel", 1, 1500.0), card.calls)
        self.assertIn(("MC_SetPos", 2, 5000), card.calls)
        self.assertIn(("MC_SetPos", 3, 1000), card.calls)
        self.assertIn(("MC_SetTrapPrm", 2, 0.1, 0.1, 0.0, 150), card.calls)
        self.assertIn(("MC_SetVel", 3, -15.0), card.calls)
        self.assertIn(("MC_SetVel", 2, -1.0), card.calls)

    def test_vision_pass_and_fail(self) -> None:
        motion, _card = engine()
        found = events_of(motion)
        motion.http_get = lambda: "true_TRUE_extra"
        motion.submit("VisionCheck", "v1", {"currentTopNo": 7})
        advance_and_pump(motion, 2)
        self.assertIn(("evt_success", "v1", "Vision check pass."), found)

        found.clear()
        motion.http_get = lambda: "no"
        motion.submit("VisionCheck", "v2", {})
        advance_and_pump(motion, 30)
        self.assertIn(("evt_error", "v2", "Vision check fail: no"), found)

    def test_card_closed_move_does_not_start(self) -> None:
        clock = VirtualClock()
        card = VirtualCard(clock)
        motion = MotionEngine(card, clock)
        found = events_of(motion)
        motion.submit("MoveAbsolute", "x", {"axis": 1, "position": 10})
        self.assertIn(("evt_error", "x", "Motion card not initialized."), found)
        self.assertNotIn(("evt_started", "x", "Moving axis 1"), found)
        self.assertFalse(any(call[0] == "MC_PrfTrap" for call in card.calls))

    def test_real_card_object_can_be_constructed_without_opening(self) -> None:
        if not DEFAULT_DLL.exists():
            self.skipTest("MultiCard.dll 不在旧工程目录")
        card = RealCard(DEFAULT_DLL)
        card.destroy()

    def test_setup_uses_old_command_names_and_default_device(self) -> None:
        base = Path(__file__).resolve().parents[1]
        setup = AdapterSetup.load(base / "adapterSetup.ini")
        runtime = RuntimeConfig.load(base / "runtime.ini")
        self.assertEqual("MotionStageAdapter", setup.adapter_name)
        self.assertEqual(
            (
                "arm_001", "valve_01", "valve_02", "valve_03", "valve_04", "valve_05",
                "valve_06", "valve_07", "valve_08", "corrector_001", "vision_001",
            ),
            setup.physical_points,
        )
        self.assertEqual(
            {"ArmTemplate", "SolenoidValveTemplate", "PneumaticCorrectorTemplate", "VisionTemplate"},
            set(setup.point_template.values()),
        )
        self.assertEqual("arm", setup.point_fields["arm_001"]["kind"])
        self.assertEqual("9", setup.point_fields["corrector_001"]["bitIndex"])
        self.assertEqual("2000", setup.point_fields["corrector_001"]["settleTimeMs"])
        self.assertEqual("vision", setup.point_fields["vision_001"]["kind"])
        self.assertEqual("false", runtime.extra["card"]["encoderFeedbackEnabled"])
        self.assertEqual("http://127.0.0.1:8089/status", runtime.extra["vision"]["statusUrl"])
        self.assertEqual("admin", runtime.username)
        self.assertEqual("192.168.0.199", runtime.extra["card"]["cardIp"])
        loaded = __import__("southbound", fromlist=["MotionParams"]).MotionParams(runtime.extra["motion"])
        self.assertEqual(0.05, loaded.move_acc[0])
        self.assertEqual(0.07, loaded.move_dec[1])
        self.assertEqual(80.0, loaded.move_vel[0])
        self.assertEqual(3.0, loaded.move_vel[2])
        self.assertEqual(1500.0, loaded.home_fast_vel)
        self.assertEqual(200, loaded.jog_watchdog_ms)
        self.assertEqual((1.0, 1.0, 10.0), loaded.jog_speed)
        text = (base / "adapterSetup.ini").read_text(encoding="utf-8")
        for name in ("MoveAbsolute", "Jog", "Stop", "ResetMotion", "SetExtDoBit", "VisionCheck"):
            self.assertIn(name, text)
        self.assertNotIn("JogStop", text)
        self.assertNotIn("virtual=true", text)
        self.assertNotIn("current_temp", (base / "core.py").read_text(encoding="utf-8"))

    def test_control_rejects_virtual_point_without_a_card(self) -> None:
        control = ControlImpl({}, ("reactor_001",))
        found: list[str] = []
        control.set_event_handler(lambda event: found.append(event.event_name))
        control.execute(SystemCommand("id", "reactor_001_sim_3", "Stop", {}, 1))
        self.assertEqual(["COMMAND_FAILED"], found)
        self.assertEqual([{"devicePoint": "reactor_001", "online": False}], control.heartbeat_points())

    def test_control_start_survives_open_card_failure(self) -> None:
        missing = Path(__file__).resolve().parents[1] / "missing-MultiCard.dll"
        control = ControlImpl(
            {
                "card": {
                    "dllPath": str(missing),
                    "reconnectIntervalSec": "60",
                }
            },
            ("reactor_001",),
        )
        control.start()
        try:
            self.assertEqual("motion-poll", control._motion_thread.name)
            self.assertEqual("vision-poll", control._vision_thread.name)
            self.assertTrue(control._motion_thread.is_alive())
            self.assertTrue(control._vision_thread.is_alive())
            self.assertEqual([{"devicePoint": "reactor_001", "online": False}], control.heartbeat_points())
            found: list[str] = []
            control.set_event_handler(lambda event: found.append(event.event_name))
            control.execute(SystemCommand("id", "reactor_001", "Stop", {}, 1))
            self.assertEqual(["COMMAND_FAILED"], found)
        finally:
            control.stop()
            self.assertFalse(control._motion_thread.is_alive())
            self.assertFalse(control._vision_thread.is_alive())
            self.assertFalse(control._reconnect_thread.is_alive())

    def test_homing_timeout_cancels_without_continuing(self) -> None:
        clock = VirtualClock()
        card = VirtualCard(clock)
        motion = MotionEngine(card, clock, MotionParams({"homeTimeoutMs": 500}))
        self.assertTrue(motion.initialize("192.168.0.200", 60000, "192.168.0.199", 60000))
        found = events_of(motion)
        motion.submit("ResetMotion", "h", {})
        advance_and_pump(motion, 4)
        self.assertEqual(1, motion.reset)
        advance_and_pump(motion, 2)
        self.assertIn(("evt_error", "h", "Homing timeout."), found)
        self.assertEqual(0, motion.reset)
        card.calls.clear()
        advance_and_pump(motion, 5)
        self.assertFalse(any(call[0] == "MC_Update" for call in card.calls))

    def test_simulation_four_kinds_stay_isolated(self) -> None:
        sim = _simulation()
        found: list[Any] = []
        sim.set_event_handler(found.append)
        sim.attach("arm_001_sim_1", "arm_001", state={"axis1_pos": 0})
        sim.attach("valve_01_sim_1", "valve_01", state={"isOpen": False})
        sim.attach("corrector_001_sim_1", "corrector_001")
        sim.attach("vision_001_sim_1", "vision_001")
        with self.assertRaises(ValueError):
            sim.attach("reactor_001_sim_1", "reactor_001")

        sim.execute(SystemCommand("j", "arm_001_sim_1", "Jog", {"axis": 1, "dir": 1}, 1))
        self.assertEqual(
            ["COMMAND_RECEIVED", "COMMAND_RUNNING", "COMMAND_COMPLETED"],
            [event.event_name for event in found if event.device_point == "arm_001_sim_1"],
        )
        self.assertEqual("Jog heartbeat accepted.", found[-1].payload["message"])
        arm = sim._devices["arm_001_sim_1"]
        advance_and_pump(arm.engine, 5)
        self.assertFalse(arm.engine.jog_running[1])
        self.assertFalse(arm.engine.busy())
        found.clear()
        sim.execute(SystemCommand("m", "arm_001_sim_1", "MoveAbsolute", {"axis": 1, "position": 800}, 1))
        advance_and_pump(arm.engine, 15)
        self.assertEqual("Target position reached.", found[-1].payload["message"])
        self.assertEqual(
            ["COMMAND_RECEIVED", "COMMAND_RUNNING", "COMMAND_COMPLETED"],
            [event.event_name for event in found],
        )
        state = sim.get_device_state("arm_001_sim_1")
        self.assertEqual(800, state["axis1_pos"])
        self.assertNotIn("axis1_planned_pos", state)
        self.assertNotIn("axis1_actual_pos", state)
        sim.attach("arm_001_sim_1", "arm_001", state={"axis1_pos": 1})
        self.assertEqual(800, sim.get_device_state("arm_001_sim_1")["axis1_pos"])

        sim.execute(SystemCommand("bit", "valve_01_sim_1", "SetExtDoBit", {"Value": 1, "BitIndex": 9}, 1))
        valve = sim._devices["valve_01_sim_1"]
        self.assertEqual(1, valve.engine.card.get_ext_do_bit(0, 1))
        self.assertNotIn(("MC_SetExtDoBit", 0, 9, 1), valve.engine.card.calls)
        self.assertFalse(any(call[0] == "MC_SetExtDoBit" for call in arm.engine.card.calls))
        self.assertEqual("UNSUPPORTED_COMMAND", _failed(sim, found, "valve_01_sim_1", "MoveAbsolute"))

        sim.execute(SystemCommand("c", "corrector_001_sim_1", "SetExtDoBit", {"Value": 1}, 1))
        corrector = sim._devices["corrector_001_sim_1"]
        self.assertFalse(any(
            event.device_point == "corrector_001_sim_1" and event.event_name == "COMMAND_COMPLETED" for event in found
        ))
        sim._run_batch(corrector)
        self.assertEqual(1, corrector.engine.card.get_ext_do_bit(0, 9))
        self.assertTrue(any(
            event.device_point == "corrector_001_sim_1" and event.event_name == "COMMAND_COMPLETED" for event in found
        ))

        vision = sim._devices["vision_001_sim_1"]
        self.assertIsNone(vision.engine)
        vision.dispatcher.vision_get = lambda: "true_true"
        sim.execute(SystemCommand("vis", "vision_001_sim_1", "VisionCheck", {"tube": 1}, 1))
        vision.dispatcher.poll_vision()
        self.assertTrue(any(
            event.device_point == "vision_001_sim_1" and event.event_name == "TUBE1_CLOSED" for event in found
        ))

    def test_startup_config_reaches_control_and_simulation(self) -> None:
        base = Path(__file__).resolve().parents[1]
        setup = AdapterSetup.load(base / "adapterSetup.ini")
        runtime = RuntimeConfig.load(base / "runtime.ini")
        control = ControlImpl(runtime.extra, setup.physical_points, setup.point_fields)
        self.assertEqual(
            ["arm", "valve", "valve", "valve", "valve", "valve", "valve", "valve", "valve", "corrector", "vision"],
            [control.dispatcher.points[name]["kind"] for name in setup.physical_points],
        )
        self.assertEqual("1", control.dispatcher.points["valve_01"]["bitIndex"])
        self.assertEqual("9", control.dispatcher.points["corrector_001"]["bitIndex"])
        self.assertEqual("2000", control.dispatcher.points["corrector_001"]["settleTimeMs"])
        self.assertEqual("http://127.0.0.1:8089/status", control.dispatcher.vision_url)
        self.assertEqual(10, control.dispatcher.vision_retries)
        self.assertFalse(control.encoder_feedback)
        sim = SimulationImpl({
            "batchSize": runtime.extra["simulation"]["batchSize"],
            "intervalSec": runtime.extra["simulation"]["intervalSec"],
            "simStepMs": runtime.extra["simulation"]["simStepMs"],
            "motion": dict(runtime.extra["motion"]),
            "vision": dict(runtime.extra["vision"]),
            "points": {name: dict(fields) for name, fields in setup.point_fields.items()},
        })
        self.assertEqual("corrector", sim.points["corrector_001"]["kind"])
        self.assertEqual("http://127.0.0.1:8089/status", sim.vision["statusUrl"])

    def test_control_routes_points_without_sharing_results(self) -> None:
        control, card, clock = _virtual_control()
        found: list[Any] = []
        control.set_event_handler(found.append)
        control.execute(SystemCommand("same", "valve_01", "SetExtDoBit", {"Value": 1, "BitIndex": 9}, 1))
        control.execute(SystemCommand("same", "valve_02", "SetExtDoBit", {"Value": 0}, 1))
        self.assertEqual(1, card.get_ext_do_bit(0, 1))
        self.assertEqual(0, card.get_ext_do_bit(0, 2))
        self.assertNotIn(("MC_SetExtDoBit", 0, 9, 1), card.calls)
        self.assertEqual(
            {"valve_01", "valve_02"},
            {event.device_point for event in found if event.event_name == "COMMAND_COMPLETED"},
        )
        writes = [call for call in card.calls if call[0] == "MC_SetExtDoBit"]
        control.execute(SystemCommand("same", "valve_01", "SetExtDoBit", {"Value": 1}, 1))
        self.assertEqual(writes, [call for call in card.calls if call[0] == "MC_SetExtDoBit"])

        control.execute(SystemCommand("hold", "corrector_001", "SetExtDoBit", {"Value": 1}, 1))
        clock.advance(1999)
        control._poll_motion()
        self.assertFalse(any(
            event.device_point == "corrector_001" and event.event_name == "COMMAND_COMPLETED" for event in found
        ))
        clock.advance(1)
        control._poll_motion()
        self.assertTrue(any(
            event.device_point == "corrector_001" and event.event_name == "COMMAND_COMPLETED" for event in found
        ))

    def test_control_stop_cancels_homing(self) -> None:
        control, card, clock = _virtual_control(("arm_001",))
        found: list[Any] = []
        control.set_event_handler(found.append)
        control.execute(SystemCommand("home", "arm_001", "ResetMotion", {}, 1))
        clock.advance(100)
        control._poll_motion()
        control.execute(SystemCommand("stop", "arm_001", "Stop", {}, 1))
        card.calls.clear()
        for _ in range(10):
            clock.advance(100)
            control._poll_motion()
        self.assertFalse(any(call[0] == "MC_Update" for call in card.calls))
        self.assertEqual(0, control.engine.reset)
        self.assertTrue(any(event.message_id == "home" and event.event_name == "COMMAND_FAILED" for event in found))
        self.assertTrue(any(event.message_id == "stop" and event.event_name == "COMMAND_COMPLETED" for event in found))

    def test_sdk_error_stops_pending_motion_and_closes_card(self) -> None:
        control, card, _clock = _virtual_control(("arm_001", "valve_01"))
        found: list[Any] = []
        control.set_event_handler(found.append)
        control.execute(SystemCommand("move", "arm_001", "MoveAbsolute", {"axis": 1, "position": 5000}, 1))

        def boom() -> None:
            raise OSError("link down")

        card.get_axis_sample = boom
        for _ in range(control.poll_failure_tolerance - 1):
            control._poll_motion()
            self.assertIsNotNone(control.engine)
            self.assertFalse(any(event.event_name == "COMMAND_FAILED" for event in found))
        control._poll_motion()
        failed = [event for event in found if event.message_id == "move" and event.event_name == "COMMAND_FAILED"]
        self.assertEqual(["CARD_OFFLINE"], [event.payload.get("errorCode") for event in failed])
        self.assertFalse(any(event.event_name in ("CARD_ONLINE", "CARD_OFFLINE") for event in found))
        self.assertIsNone(control.engine)
        self.assertIsNone(control.dispatcher.engine)
        self.assertIn(("MC_Close",), card.calls)
        found.clear()
        control.execute(SystemCommand("again", "valve_01", "SetExtDoBit", {"Value": 1}, 1))
        self.assertEqual("CARD_OFFLINE", found[-1].payload["errorCode"])
        self.assertFalse(any(call[0] == "MC_SetExtDoBit" for call in card.calls[card.calls.index(("MC_Close",)) + 1:]))

    def test_single_poll_timeout_does_not_drop_card_or_motion(self) -> None:
        control, card, clock = _virtual_control(("arm_001", "valve_01"))
        found: list[Any] = []
        control.set_event_handler(found.append)
        control.execute(SystemCommand("move", "arm_001", "MoveAbsolute", {"axis": 1, "position": 500}, 1))
        real_sample = card.get_axis_sample
        failures = iter([True])

        def flaky():
            if next(failures, False):
                raise OSError("MC_GetAllSysStatusSX failed: -7")
            return real_sample()

        card.get_axis_sample = flaky
        clock.advance(100)
        control._poll_motion()
        self.assertIs(control.engine, control.dispatcher.engine)
        self.assertNotIn(("MC_Close",), card.calls)
        for _ in range(100):
            clock.advance(100)
            control._poll_motion()
        self.assertEqual(0, control._poll_failures)
        self.assertEqual(
            ["COMMAND_COMPLETED"],
            [e.event_name for e in found if e.message_id == "move" and e.event_name in ("COMMAND_COMPLETED", "COMMAND_FAILED")],
        )

    def test_close_releases_card_even_when_stop_times_out(self) -> None:
        clock = VirtualClock()
        card = VirtualCard(clock)
        motion = MotionEngine(card, clock)
        motion.initialize("127.0.0.1", 1, "127.0.0.1", 1)
        destroyed = []
        card.destroy = lambda: destroyed.append(True)

        def timeout(*_args):
            raise OSError("MC_Stop failed: -7")

        card.stop = timeout
        motion.close()
        self.assertFalse(motion.opened)
        self.assertIn(("MC_Close",), card.calls)
        self.assertEqual([True], destroyed)

    def test_failed_stop_is_resent_before_anything_else(self) -> None:
        clock = VirtualClock()
        card = VirtualCard(clock)
        motion = MotionEngine(card, clock)
        motion.initialize("127.0.0.1", 1, "127.0.0.1", 1)
        found = events_of(motion)
        motion.submit("MoveAbsolute", "move", {"axis": 1, "position": 5000})
        real_stop = card.stop
        attempts = []

        def flaky_stop(mask, option):
            attempts.append(mask)
            if len(attempts) == 1:
                raise OSError("MC_Stop failed: -7")
            real_stop(mask, option)

        card.stop = flaky_stop
        with self.assertRaises(OSError):
            motion._cancel_motion("Hardware fault during motion.")
        self.assertIn(("evt_error", "move", "Hardware fault during motion."), found)
        clock.advance(100)
        motion.pump_once()
        self.assertEqual(2, len(attempts))
        self.assertFalse(motion._stop_owed)

        def always_timeout(*_args):
            raise OSError("MC_Stop failed: -7")

        card.stop = always_timeout
        with self.assertRaises(OSError):
            motion._stop_all_axes()
        card.calls.clear()
        with self.assertRaises(OSError):
            motion.submit("MoveAbsolute", "again", {"axis": 1, "position": 100})
        self.assertFalse(any(call[0] == "MC_Update" for call in card.calls))

    def test_each_poll_reads_axis_status_once_and_outputs_once_per_second(self) -> None:
        control, card, clock = _virtual_control(("arm_001", "valve_01", "valve_02", "corrector_001"))
        samples = []
        reads = []
        real_sample, real_read = card.get_axis_sample, card.get_ext_do_bit
        card.get_axis_sample = lambda: samples.append(1) or real_sample()
        card.get_ext_do_bit = lambda index, bit: reads.append(bit) or real_read(index, bit)
        for _ in range(10):
            clock.advance(100)
            control._poll_motion()
        self.assertEqual(10, len(samples))
        self.assertEqual([1, 2, 9], sorted(reads))
        reads.clear()
        control.execute(SystemCommand("hold", "corrector_001", "SetExtDoBit", {"Value": 1}, 1))
        reads.clear()
        clock.advance(50)
        control._poll_motion()
        self.assertEqual([9], reads)

    def test_vision_poll_does_not_block_motion_poll(self) -> None:
        control, card, _clock = _virtual_control()
        entered = threading.Event()
        release = threading.Event()

        def slow() -> str:
            entered.set()
            release.wait(2)
            return "true_true"

        control.dispatcher.vision_get = slow
        control.execute(SystemCommand("v", "vision_001", "VisionCheck", {"tube": 1}, 1))
        worker = threading.Thread(target=control._poll_vision)
        worker.start()
        self.assertTrue(entered.wait(1))
        started = time.perf_counter()
        control.execute(SystemCommand("m", "arm_001", "MoveAbsolute", {"axis": 1, "position": 1000}, 1))
        control._poll_motion()
        elapsed = time.perf_counter() - started
        release.set()
        worker.join(2)
        self.assertLess(elapsed, 0.5)
        self.assertTrue(any(call[0] == "MC_PrfTrap" for call in card.calls))

    def test_translator_inserts_running_for_jog_only(self) -> None:
        mapper = LegacyEventTranslator()
        self.assertEqual(
            [("COMMAND_RECEIVED", "Command parsed: Jog"), ("COMMAND_RUNNING", ""), ("COMMAND_COMPLETED", "Jog heartbeat accepted.")],
            mapper.translate("evt_ack", "j", "Command parsed: Jog")
            + mapper.translate("evt_success", "j", "Jog heartbeat accepted."),
        )
        move = LegacyEventTranslator()
        self.assertEqual(
            [("COMMAND_RUNNING", "Moving axis 1"), ("COMMAND_COMPLETED", "Target position reached.")],
            move.translate("evt_started", "m", "Moving axis 1")
            + move.translate("evt_success", "m", "Target position reached."),
        )


if __name__ == "__main__":
    unittest.main()
