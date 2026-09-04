"""Adapter 虚拟点路由回归测试。"""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from typing import Any

from core import AdapterCore, AdapterSetup, RuntimeConfig, SystemCommand, parse_virtual_point, virtual_point_name
from simulation import SimulationImpl


class RecordingControl:
    def __init__(self) -> None:
        self.commands: list[SystemCommand] = []

    def start(self) -> None:
        return None

    def stop(self) -> None:
        return None

    def set_data_handler(self, handler) -> None:
        return None

    def set_event_handler(self, handler) -> None:
        return None

    def execute(self, command: SystemCommand) -> None:
        self.commands.append(command)


class FakeClient:
    def __init__(self) -> None:
        self.subscriptions: list[str] = []

    def subscribe(self, topic: str, qos: int = 1) -> None:
        self.subscriptions.append(topic)

    def unsubscribe(self, topic: str) -> None:
        self.subscriptions = [item for item in self.subscriptions if item != topic]

    def publish(self, topic: str, payload: str, qos: int = 1, retain: bool = False) -> None:
        return None


class CoreRoutingTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        base = Path(__file__).resolve().parent
        cls.setup = AdapterSetup.load(base / "adapterSetup.ini")
        cls.runtime = RuntimeConfig.load(base / "runtime.ini")

    def _core(self) -> tuple[AdapterCore, RecordingControl, SimulationImpl]:
        control = RecordingControl()
        simulation = SimulationImpl()
        leases_file = Path(tempfile.gettempdir()) / f"test_leases_{id(self)}.json"
        leases_file.unlink(missing_ok=True)
        core = AdapterCore(self.setup, self.runtime, control, simulation, leases_file=leases_file)
        core._client = FakeClient()  # noqa: SLF001 - test hook
        core._connected = True
        return core, control, simulation

    def test_parse_virtual_point(self) -> None:
        self.assertEqual(("Reactor1", 7), parse_virtual_point(self.setup.physical_points, "Reactor1_sim_7"))
        self.assertIsNone(parse_virtual_point(self.setup.physical_points, "Reactor1"))

    def test_virtual_point_name_matches_parser(self) -> None:
        name = virtual_point_name("Reactor1", 7)
        self.assertEqual("Reactor1_sim_7", name)
        self.assertEqual(("Reactor1", 7), parse_virtual_point(self.setup.physical_points, name))

    def test_virtual_command_routes_to_simulation_without_lease_map(self) -> None:
        core, control, simulation = self._core()
        simulation.attach("Reactor1_sim_7", "Reactor1")

        payload = {
            "messageId": "msg-1",
            "adapterName": self.setup.adapter_name,
            "devicePoint": "Reactor1_sim_7",
            "commandName": "heat",
            "parameters": {"targetTemperature": 80.0, "durationSec": 1},
            "timestamp": 1,
        }
        topic = core._command_topic("Reactor1_sim_7")
        core._command_topics[topic] = "Reactor1_sim_7"
        core._handle_command("Reactor1_sim_7", payload)

        self.assertEqual([], control.commands)
        self.assertEqual(7, core._virtual_to_lease.get("Reactor1_sim_7"))

    def test_physical_command_routes_to_control(self) -> None:
        core, control, _simulation = self._core()
        payload = {
            "messageId": "msg-2",
            "adapterName": self.setup.adapter_name,
            "devicePoint": "Reactor1",
            "commandName": "heat",
            "parameters": {"targetTemperature": 80.0, "durationSec": 1},
            "timestamp": 1,
        }
        core._handle_command("Reactor1", payload)
        self.assertEqual(1, len(control.commands))
        self.assertEqual("Reactor1", control.commands[0].device_point)


if __name__ == "__main__":
    unittest.main()
