import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from core import SystemCommand
from simulation import SimulationImpl


class StopHeatSimulationTest(unittest.TestCase):
    def test_stop_heat_completes_when_nothing_is_heating(self) -> None:
        sim = SimulationImpl({"batchSize": "1", "intervalSec": "10", "simStepMs": "1000"})
        events = []
        sim.set_event_handler(events.append)
        sim.attach("Reactor1_sim_40", "Reactor1")
        sim.execute(SystemCommand("stop-1", "Reactor1_sim_40", "stopHeat", {}, 1))
        names = [event.event_name for event in events]
        self.assertEqual(
            ["COMMAND_RUNNING", "HEATING_STOPPED", "COMMAND_COMPLETED"],
            names,
        )
        self.assertTrue(all(event.message_id == "stop-1" for event in events))

    def test_stop_heat_cancels_the_unfinished_heat_then_completes(self) -> None:
        sim = SimulationImpl({"batchSize": "1", "intervalSec": "10", "simStepMs": "1000"})
        events = []
        sim.set_event_handler(events.append)
        sim.attach("Reactor1_sim_40", "Reactor1")
        sim.execute(SystemCommand(
            "heat-1",
            "Reactor1_sim_40",
            "heat",
            {"temperature1": 20, "minutes1": 20},
            1,
        ))
        events.clear()
        sim.execute(SystemCommand("stop-1", "Reactor1_sim_40", "stopHeat", {}, 1))
        self.assertEqual(
            [
                ("COMMAND_RUNNING", "stop-1"),
                ("COMMAND_FAILED", "heat-1"),
                ("HEATING_STOPPED", "stop-1"),
                ("COMMAND_COMPLETED", "stop-1"),
            ],
            [(event.event_name, event.message_id) for event in events],
        )


if __name__ == "__main__":
    unittest.main()
