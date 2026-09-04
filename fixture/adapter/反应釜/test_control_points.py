from __future__ import annotations

import unittest
from pathlib import Path

from control import ControlImpl
from core import AdapterSetup


class ControlPointReportingTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.setup = AdapterSetup.load(Path(__file__).resolve().parent / "adapterSetup.ini")

    def test_single_point_reading_uses_setup_point(self) -> None:
        ctrl = ControlImpl({}, self.setup.physical_points, self.setup.point_fields)
        self.assertEqual("Reactor1", ctrl._resolve_physical_point({"temperature": 30, "pressure": 0.1}))

    def test_multi_point_requires_identity(self) -> None:
        ctrl = ControlImpl(
            {},
            ("Reactor1", "Reactor2"),
            {"Reactor1": {"index": "1"}, "Reactor2": {"index": "2"}},
        )
        self.assertIsNone(ctrl._resolve_physical_point({"temperature": 30, "pressure": 0.1}))
        self.assertEqual("Reactor2", ctrl._resolve_physical_point({"temperature": 30, "pressure": 0.1, "index": "2"}))
        self.assertEqual(
            "Reactor1",
            ctrl._resolve_physical_point({"temperature": 30, "pressure": 0.1, "devicePoint": "Reactor1"}),
        )

    def test_telemetry_emits_resolved_point(self) -> None:
        ctrl = ControlImpl({}, self.setup.physical_points, self.setup.point_fields)
        captured = []
        ctrl.set_data_handler(lambda data: captured.append(data))
        ctrl._emit_telemetry("Reactor1", 40.0, 0.2)
        self.assertEqual(1, len(captured))
        self.assertEqual("Reactor1", captured[0].device_point)
        self.assertEqual(40.0, captured[0].fields["raw_temp"])


if __name__ == "__main__":
    unittest.main()
