"""Adapter 核心持久化、断网自愈与稳健性单元测试。"""

from __future__ import annotations

import json
import os
import shutil
import tempfile
import unittest
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from core import AdapterCore, AdapterSetup, RuntimeConfig
from simulation import SimulationImpl
from test_core_routing import FakeClient, RecordingControl


class CorePersistenceAndReliabilityTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = Path(tempfile.mkdtemp())
        base = Path(__file__).resolve().parent
        # 拷贝 setup.ini 与 runtime.ini 到临时测试目录
        shutil.copy(base / "adapterSetup.ini", self.temp_dir / "adapterSetup.ini")
        shutil.copy(base / "runtime.ini", self.temp_dir / "runtime.ini")
        self.setup = AdapterSetup.load(self.temp_dir / "adapterSetup.ini")
        self.runtime = RuntimeConfig.load(self.temp_dir / "runtime.ini")

    def tearDown(self) -> None:
        shutil.rmtree(self.temp_dir, ignore_errors=True)

    def test_dynamic_unique_client_id(self) -> None:
        control = RecordingControl()
        sim = SimulationImpl()
        core1 = AdapterCore(self.setup, self.runtime, control, sim)
        core2 = AdapterCore(self.setup, self.runtime, control, sim)
        self.assertNotEqual(core1._unique_client_id, core2._unique_client_id)
        self.assertTrue(core1._unique_client_id.startswith(self.runtime.client_id))

    def test_lease_persistence_and_restore(self) -> None:
        leases_file = self.temp_dir / "virtual_leases.json"
        control = RecordingControl()
        sim = SimulationImpl()
        core = AdapterCore(self.setup, self.runtime, control, sim, leases_file=leases_file)
        core._client = FakeClient()
        core._connected = True

        # 1. 申请租约
        lease_rec = core._lease(16, "Reactor1")
        self.assertEqual("Reactor1_sim_16", lease_rec.virtual_point)

        # 验证本地文件已生成
        self.assertTrue(leases_file.exists())
        data = json.loads(leases_file.read_text(encoding="utf-8"))
        self.assertIn("16", data)
        self.assertEqual("Reactor1_sim_16", data["16"]["virtualPoint"])

        # 模拟设置虚拟设备状态
        sim.attach("Reactor1_sim_16", "Reactor1", state={"current_temp": 65.5, "current_pressure": 0.25})
        core._save_stored_leases()

        # 2. 模拟 Adapter 进程重启，新建 core2 实例
        control2 = RecordingControl()
        sim2 = SimulationImpl()
        core2 = AdapterCore(self.setup, self.runtime, control2, sim2, leases_file=leases_file)
        fake_client2 = FakeClient()
        core2._client = fake_client2
        core2._connected = True

        # 验证 core2 自动从本地恢复了租约和状态
        self.assertIn(16, core2._leases)
        self.assertEqual("Reactor1_sim_16", core2._leases[16].virtual_point)
        state = sim2.get_device_state("Reactor1_sim_16")
        self.assertIsNotNone(state)
        self.assertAlmostEqual(65.5, state["current_temp"])
        self.assertAlmostEqual(0.25, state["current_pressure"])

        # 3. 验证 _on_connect 订阅了通配符与已恢复的点位
        core2._on_connect(fake_client2, None, None, 0)
        self.assertIn(f"smartlab/adapter/{self.setup.adapter_name}/+/command", fake_client2.subscriptions)
        self.assertIn("smartlab/adapter/adapterv2/Reactor1_sim_16/command", fake_client2.subscriptions)

        # 4. 验证释放租约后标记为 RELEASED，重启时不再恢复
        core2._release(16, "Reactor1_sim_16")
        data_after_release = json.loads(leases_file.read_text(encoding="utf-8"))
        self.assertIn("16", data_after_release)
        self.assertEqual("RELEASED", data_after_release["16"].get("status"))
        self.assertTrue(data_after_release["16"].get("released"))

        sim3 = SimulationImpl()
        core3 = AdapterCore(self.setup, self.runtime, RecordingControl(), sim3, leases_file=leases_file)
        self.assertNotIn(16, core3._leases)
        self.assertIsNone(sim3.get_device_state("Reactor1_sim_16"))

    def test_wildcard_command_routing_for_unseen_virtual_point(self) -> None:
        control = RecordingControl()
        sim = SimulationImpl()
        core = AdapterCore(self.setup, self.runtime, control, sim)
        fake_client = FakeClient()
        core._client = fake_client
        core._connected = True

        class FakeMsg:
            topic = "smartlab/adapter/adapterv2/Reactor1_sim_99/command"
            payload = json.dumps({
                "messageId": "msg-wildcard-1",
                "adapterName": "adapterv2",
                "devicePoint": "Reactor1_sim_99",
                "commandName": "heat",
                "parameters": {"targetTemperature": 90.0, "durationSec": 2},
                "timestamp": 1234567,
            }).encode("utf-8")

        # 收到从未在当前内存登记过的虚拟点命令
        core._on_message(fake_client, None, FakeMsg())

        # 验证虚拟设备已被动态识别、挂载并接收指令
        state = sim.get_device_state("Reactor1_sim_99")
        self.assertIsNotNone(state)
        self.assertAlmostEqual(90.0, state["target_temp"])

    def test_single_and_batch_telemetry_emission(self) -> None:
        from core import DeviceData, DeviceBatchData
        control = RecordingControl()
        sim = SimulationImpl()
        core = AdapterCore(self.setup, self.runtime, control, sim)

        class RecordingFakeClient(FakeClient):
            def __init__(self) -> None:
                super().__init__()
                self.published: list[tuple[str, dict[str, Any]]] = []

            def publish(self, topic: str, payload: str, qos: int = 1, retain: bool = False) -> None:
                self.published.append((topic, json.loads(payload)))

        client = RecordingFakeClient()
        core._client = client
        core._connected = True

        # 1. 生产模式（物理点单点上报） -> formatType: SINGLE
        core._on_backend_data(DeviceData(
            device_point="Reactor1",
            fields={"raw_temp": 40.0, "raw_press": 0.15},
            timestamp=100000
        ))
        self.assertEqual(1, len(client.published))
        topic, single_payload = client.published[0]
        self.assertEqual("smartlab/adapter/adapterv2/Reactor1/telemetry", topic)
        self.assertEqual("SINGLE", single_payload["formatType"])
        self.assertIn("telemetryData", single_payload)
        self.assertAlmostEqual(40.0, single_payload["telemetryData"]["temperature"])

        # 2. 仿真模式（虚拟点打包上报） -> formatType: BATCH，时间戳模拟推进
        core._on_backend_batch_data(DeviceBatchData(
            device_point="Reactor1_sim_16",
            items=[
                {"timestamp": 200000, "fields": {"raw_temp": 50.0, "raw_press": 0.20}},
                {"timestamp": 201000, "fields": {"raw_temp": 51.0, "raw_press": 0.21}},
            ],
            timestamp=201000
        ))
        self.assertEqual(2, len(client.published))
        b_topic, batch_payload = client.published[1]
        self.assertEqual("smartlab/adapter/adapterv2/Reactor1_sim_16/telemetry", b_topic)
        self.assertEqual("BATCH", batch_payload["formatType"])
        self.assertIn("items", batch_payload)
        self.assertEqual(2, len(batch_payload["items"]))
        self.assertEqual(200000, batch_payload["items"][0]["timestamp"])
        self.assertEqual(201000, batch_payload["items"][1]["timestamp"])
        self.assertAlmostEqual(50.0, batch_payload["items"][0]["telemetryData"]["temperature"])
        self.assertAlmostEqual(51.0, batch_payload["items"][1]["telemetryData"]["temperature"])

    def test_simulation_configurable_batching(self) -> None:
        custom_config = {
            "batchSize": "15",
            "intervalSec": "0.2",
            "simStepMs": "5000",
        }
        sim = SimulationImpl(custom_config)
        self.assertEqual(15, sim.batch_size)
        self.assertEqual(0.2, sim.interval_sec)
        self.assertEqual(5000, sim.sim_step_ms)

    def test_simulation_closed_loop_completion(self) -> None:
        sim = SimulationImpl({"batchSize": "20", "intervalSec": "0.1", "simStepMs": "1000"})
        events = []
        batches = []
        sim.set_event_handler(lambda evt: events.append(evt))
        sim.set_batch_data_handler(lambda b: batches.append(b))

        sim.attach("Reactor1_sim_88", "Reactor1", state={"current_temp": 25.0, "current_pressure": 0.1})
        dev = sim._virtual_devices["Reactor1_sim_88"]
        self.assertFalse(dev.is_running())

        # 下发加热到 100℃，持续 20 秒指令
        from core import SystemCommand, now_ms
        cmd = SystemCommand(
            device_point="Reactor1_sim_88",
            command_name="heat",
            parameters={"targetTemperature": 100.0, "durationSec": 20},
            message_id="msg-closed-loop-1",
            timestamp=now_ms(),
        )
        sim.execute(cmd)

        # 验证立即收到 COMMAND_RECEIVED 与 COMMAND_RUNNING
        self.assertTrue(dev.is_running())
        event_names = [e.event_name for e in events]
        self.assertIn("COMMAND_RECEIVED", event_names)
        self.assertIn("COMMAND_RUNNING", event_names)
        self.assertIn("HEAT_STARTED", event_names)

        # 模拟执行 1 次微批次推进 (20步)
        sim.start()
        time.sleep(0.25)
        sim.stop()

        # 验证推演已闭环完成，无需 time.sleep(20)
        self.assertFalse(dev.is_running())
        self.assertAlmostEqual(100.0, dev.current_temp)
        completed_names = [e.event_name for e in events]
        self.assertIn("TARGET_TEMPERATURE_REACHED", completed_names)
        self.assertIn("COMMAND_COMPLETED", completed_names)
        comp_evt = next(e for e in events if e.event_name == "COMMAND_COMPLETED")
        self.assertEqual("msg-closed-loop-1", comp_evt.message_id)


if __name__ == "__main__":
    unittest.main()
