"""
GetPLCData 南向驱动。

PLC 用 Modbus TCP 功能码 0x03 / 0x06，地址是点位表 Addr_0 到 Addr_295。
加压电机用 485 上的 Modbus RTU。加热程序由 PLC 执行；本模块只写段寄存器和启动位，
再读反应温度判断指令是否结束。
"""

from __future__ import annotations

import json
import logging
import math
import socket
import struct
import threading
import time
from pathlib import Path
from typing import Any, Callable

from core import AdapterEvent, DeviceData

logger = logging.getLogger("smartlab.adapter.getplcdata.control")

MIRROR_SIZE = 297
HOLD_MINUTES = 9999
MANUAL_REG = 267
CHROM_ACTUAL = 276
CHROM_ALARM = 277
CHROM_SET = 278
MUTE_REG = 293
HEATSINK_ALARM = {1: 220, 2: 221, 3: 222, 4: 223, 5: 289, 6: 290, 7: 292, 8: 291}
# 报警温度先恢复并逐个读回核验；其他设定随后逐个写入、读回核验。
# 使能位和启动位最后恢复，267 单独处理。
ALARM_REGISTERS = tuple(range(212, 226)) + (277,) + tuple(range(289, 293))
PRESERVE_BEFORE_CONTROL = (
    ALARM_REGISTERS
    + tuple(range(34, 212))
    + tuple(range(235, 251))
    + (278,)
    + tuple(range(293, 296))
    + tuple(range(226, 235))
    + tuple(range(268, 276))
)


def now_ms() -> int:
    return int(time.time() * 1000)


def calc_crc16_modbus(data: bytes) -> int:
    crc = 0xFFFF
    for pos in data:
        crc ^= pos
        for _ in range(8):
            if (crc & 1) != 0:
                crc >>= 1
                crc ^= 0xA001
            else:
                crc >>= 1
    return crc


def step_temp_addr(channel: int, step: int) -> int:
    return 34 + (channel - 1) * 22 + (step - 1) * 2


def step_time_addr(channel: int, step: int) -> int:
    return step_temp_addr(channel, step) + 1


def back_pressure_addr(channel: int) -> int:
    return 11 + (channel - 1) * 3


def heatsink_set_addr(channel: int) -> int:
    return 54 + (channel - 1) * 22


def heatsink_actual_addr(channel: int) -> int:
    return 278 + channel


def gas_set_addr(channel: int, segment: int) -> int:
    return 235 + (channel - 1) * 2 + (segment - 1)


def gas_read_addr(channel: int, segment: int) -> int:
    return 251 + (channel - 1) * 2 + (segment - 1)


def start_addr(channel: int) -> int:
    return 267 + channel


def _blank(value: Any) -> bool:
    return value is None or str(value).strip() == ""


def segments_from_params(params: dict[str, Any]) -> list[tuple[float, int]]:
    """从 temperature1/minutes1 到 temperature10/minutes10 读取加热段。

    某一段分钟数为 0，或温度与分钟都没填，则这一段和后面的段都不使用。
    """
    segments: list[tuple[float, int]] = []
    for index in range(1, 11):
        temperature = params.get(f"temperature{index}")
        minutes = params.get(f"minutes{index}")
        if _blank(temperature) and _blank(minutes):
            break
        if _blank(minutes) or int(round(float(minutes))) == 0:
            break
        if _blank(temperature):
            raise ValueError(f"第{index}段缺少温度")
        minute_value = int(round(float(minutes)))
        if minute_value < 0:
            raise ValueError("加热分钟数不能为负")
        segments.append((float(temperature), minute_value))
    if not segments:
        raise ValueError("至少要填写第1段的温度和分钟数")
    return segments


def plan_heat_writes(segments: list[tuple[float, int]]) -> tuple[list[tuple[int, int]], int]:
    """返回要写入的 (温度整数, 分钟) 列表，以及用来判断完成的恒温温度。

    未满 10 段且最后一段不是 9999 分钟时，在下一段补 9999 分钟、温度与最后一段相同。
    补段之后的寄存器不在这个列表里，调用方不得写入。
    """
    if not 1 <= len(segments) <= 10:
        raise ValueError("加热段数必须是 1 到 10")
    planned = [(int(round(temperature)), minutes) for temperature, minutes in segments]
    if planned[-1][1] != HOLD_MINUTES and len(planned) < 10:
        planned.append((planned[-1][0], HOLD_MINUTES))
    return planned, planned[-1][0]


class _HoldWatch:
    def __init__(self, start_event: str, event_name: str, payload: dict[str, Any], key: str, generation: int, ready) -> None:
        self.start_event = start_event
        self.event_name = event_name
        self.payload = payload
        self.key = key
        self.generation = generation
        self.ready = ready


def as_bool(raw: Any) -> bool:
    if isinstance(raw, bool):
        return raw
    if isinstance(raw, (int, float)):
        return int(raw) != 0
    text = str(raw).strip().lower()
    if text in {"1", "true", "yes"}:
        return True
    if text in {"0", "false", "no"}:
        return False
    raise ValueError(f"无法解析布尔值: {raw}")


def _kind(point: str) -> str:
    if point.startswith("Reactor"):
        return "reactor"
    if point.startswith("Heatsink"):
        return "heatsink"
    if point.startswith("CoFlow") or point.startswith("H2Flow"):
        return "flow"
    if point.startswith("Incubator"):
        return "incubator"
    if point == "Chromatography":
        return "chromatography"
    if point in {"CoValve", "H2Valve"}:
        return "valve"
    if point == "Plant":
        return "plant"
    return ""


class ModbusTcpClient:
    def __init__(self, host: str, port: int, timeout: float) -> None:
        self.host = host
        self.port = port
        self.timeout = timeout
        self._sock: socket.socket | None = None
        self._lock = threading.Lock()
        self._transaction_id = 0

    def close(self) -> None:
        with self._lock:
            self._close_locked()

    def _close_locked(self) -> None:
        if self._sock is not None:
            try:
                self._sock.close()
            except Exception:
                pass
            self._sock = None

    def _connect_locked(self) -> bool:
        self._close_locked()
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(self.timeout)
            sock.connect((self.host, self.port))
            self._sock = sock
            logger.info("已连接 PLC %s:%d", self.host, self.port)
            return True
        except Exception as error:
            logger.warning("连接 PLC 失败 %s:%d: %s", self.host, self.port, error)
            self._sock = None
            return False

    def _next_tid(self) -> int:
        self._transaction_id = (self._transaction_id + 1) & 0xFFFF
        if self._transaction_id == 0:
            self._transaction_id = 1
        return self._transaction_id

    def _recv_exact(self, count: int) -> bytes:
        assert self._sock is not None
        buf = b""
        while len(buf) < count:
            chunk = self._sock.recv(count - len(buf))
            if not chunk:
                raise ConnectionError("PLC 连接已关闭")
            buf += chunk
        return buf

    def read_holding_registers(self, start_addr: int, count: int) -> list[int] | None:
        with self._lock:
            if self._sock is None and not self._connect_locked():
                return None
            try:
                tid = self._next_tid()
                req = struct.pack(">HHHBBHH", tid, 0, 6, 1, 3, start_addr, count)
                assert self._sock is not None
                self._sock.sendall(req)
                header = self._recv_exact(9)
                r_tid, _proto, _length, _unit, func = struct.unpack(">HHHBB", header[:8])
                if r_tid != tid or func != 3 or header[8] != count * 2:
                    raise RuntimeError(f"PLC 读响应不匹配 tid={r_tid} func={func} bytes={header[8]}")
                payload = self._recv_exact(header[8])
                return [struct.unpack(">h", payload[i:i + 2])[0] for i in range(0, len(payload), 2)]
            except Exception as error:
                logger.warning("读寄存器 %s 数量 %s 失败: %s", start_addr, count, error)
                self._close_locked()
                return None

    def write_single_register(self, addr: int, value: int) -> bool:
        word = int(value) & 0xFFFF
        with self._lock:
            if self._sock is None and not self._connect_locked():
                return False
            try:
                tid = self._next_tid()
                req = struct.pack(">HHHBBHH", tid, 0, 6, 1, 6, addr, word)
                assert self._sock is not None
                self._sock.sendall(req)
                resp = self._recv_exact(12)
                r_tid, _proto, _length, _unit, func, r_addr, r_val = struct.unpack(">HHHBBHH", resp)
                if func == 0x86:
                    raise RuntimeError("PLC 拒绝写入")
                if r_tid != tid or func != 6 or r_addr != addr or r_val != word:
                    raise RuntimeError("PLC 写响应回显不一致")
                return True
            except Exception as error:
                logger.warning("写寄存器 %s=%s 失败: %s", addr, value, error)
                self._close_locked()
                return False


class Rs485Client:
    def __init__(self, host: str, port: int, timeout: float) -> None:
        self.host = host
        self.port = port
        self.timeout = timeout
        self._sock: socket.socket | None = None
        self._lock = threading.Lock()

    def close(self) -> None:
        with self._lock:
            self._close_locked()

    def _close_locked(self) -> None:
        if self._sock is not None:
            try:
                self._sock.close()
            except Exception:
                pass
            self._sock = None

    def _connect_locked(self) -> bool:
        self._close_locked()
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(self.timeout)
            sock.connect((self.host, self.port))
            self._sock = sock
            logger.info("已连接 485 网关 %s:%d", self.host, self.port)
            return True
        except Exception as error:
            logger.warning("连接 485 失败 %s:%d: %s", self.host, self.port, error)
            self._sock = None
            return False

    def _exchange(self, frame: bytes, expect: int) -> bytes | None:
        with self._lock:
            if self._sock is None and not self._connect_locked():
                return None
            try:
                assert self._sock is not None
                self._sock.sendall(frame)
                buf = b""
                while len(buf) < expect:
                    chunk = self._sock.recv(expect - len(buf))
                    if not chunk:
                        raise ConnectionError("485 连接已关闭")
                    buf += chunk
                return buf
            except Exception as error:
                logger.warning("485 通信失败: %s", error)
                self._close_locked()
                return None

    def move(self, dev_addr: int, direction: int, speed: float, pulse: int) -> bool:
        speed_val = int(abs(speed))
        if 0 < speed_val < 2:
            speed_val = 2
        body = struct.pack(
            ">BBHHBBBHI BB".replace(" ", ""),
            dev_addr & 0xFF,
            0x10,
            0x00FD,
            5,
            10,
            direction & 0xFF,
            0,
            speed_val & 0xFFFF,
            pulse & 0xFFFFFFFF,
            0,
            0,
        )
        frame = body + struct.pack("<H", calc_crc16_modbus(body))
        response = self._exchange(frame, 8)
        echo = struct.pack(">BBHH", dev_addr & 0xFF, 0x10, 0x00FD, 5)
        return response == echo + struct.pack("<H", calc_crc16_modbus(echo))

    def read_alarm(self, dev_addr: int) -> bool | None:
        body = struct.pack(">BBHH", dev_addr & 0xFF, 0x04, 0x0043, 0x0010)
        frame = body + struct.pack("<H", calc_crc16_modbus(body))
        resp = self._exchange(frame, 37)
        if (resp is None or len(resp) != 37 or resp[:3] != bytes([dev_addr & 0xFF, 4, 32])
                or resp[-2:] != struct.pack("<H", calc_crc16_modbus(resp[:-2]))):
            return None
        return bool(resp[34] & 0x08)

    def stop(self, dev_addr: int) -> bool:
        """沿用旧 PLCCtrl::stop：向 0x00FE 写 0x9800，地址为 1 到 8。"""
        if not 1 <= dev_addr <= 8:
            raise ValueError("加压电机地址必须是 1 到 8")
        body = struct.pack(">BBHHBH", dev_addr, 0x10, 0x00FE, 1, 2, 0x9800)
        frame = body + struct.pack("<H", calc_crc16_modbus(body))
        response = self._exchange(frame, 8)
        echo = struct.pack(">BBHH", dev_addr, 0x10, 0x00FE, 1)
        expected = echo + struct.pack("<H", calc_crc16_modbus(echo))
        if response != expected:
            logger.warning("加压电机 %s 停止失败：未收到有效响应", dev_addr)
            return False
        return True


class _ValveTravel:
    """启动时人工归零；记录指令行程，不冒充编码器反馈。

    正常小步等待名义运动时间后记账。中途停止时无法知道完成了多少，
    保留起止位置的范围，后续按最保守的剩余行程限制两端。
    """
    def __init__(self) -> None:
        self.low = self.high = 0
        self.pending: int | None = None
        self.ready_after = 0.0
        self.blocked = False

    def settle(self, now: float) -> None:
        if self.pending is not None and now >= self.ready_after:
            self.low += self.pending
            self.high += self.pending
            self.pending = None

    def begin(self, delta: int, duration: float) -> None:
        self.pending = delta
        self.ready_after = time.monotonic() + duration + 0.05

    def interrupt(self) -> None:
        self.settle(time.monotonic())
        if self.pending is not None:
            self.low += min(0, self.pending)
            self.high += max(0, self.pending)
            self.pending = None
        self.ready_after = time.monotonic()


class ControlImpl:
    def __init__(
        self,
        extra: dict[str, dict[str, str]],
        physical_points: tuple[str, ...] | list[str],
        point_fields: dict[str, dict[str, str]] | None = None,
    ) -> None:
        self.points = tuple(physical_points)
        self.point_fields = point_fields or {}
        # 命令级互斥：接管和整组设定恢复期间，不让其他 PLC 控制命令插入。
        self._command_lock = threading.Lock()
        plc = extra.get("plc", {})
        rs485 = extra.get("rs485", {})
        self.temperature_tolerance = float(plc.get("temperatureTolerance", "0"))
        self.pressure_tolerance = float(plc.get("pressureTolerance", "0.01"))
        self.link_timeout_sec = float(plc.get("linkTimeoutSec", "30"))
        self.poll_interval_sec = float(plc.get("pollIntervalSec", "1"))
        self.chunk_gap_sec = float(plc.get("chunkGapMs", "50")) / 1000.0
        self.pulses_per_turn = int(rs485.get("pulsesPerTurn", "3200"))
        self.valve_turns = float(rs485.get("valveTravelTurns", "4"))
        self.valve_travel_ratio = float(rs485.get("valveTravelRatio", "0.9"))
        self.pressure_poll_sec = float(rs485.get("pressurePollIntervalSec", "0.2"))
        self.pressure_fresh_sec = float(rs485.get("pressureFreshSec", "3"))
        # 兼容旧配置中的 pressureStepTurns，将其作为单步上限。
        self.pressure_step_turns = float(rs485.get("pressureMaxStepTurns", rs485.get("pressureStepTurns", "0.5")))
        self.pressure_full_step_error = float(rs485.get("pressureFullStepErrorMPa", "1"))
        self.pressure_step_speed = int(rs485.get("pressureStepSpeedRpm", "10"))
        self.pressure_settle_sec = float(rs485.get("pressureSettleSec", "2"))
        self.open_direction = int(rs485.get("openingDirection", "1"))
        numbers = (self.valve_turns, self.valve_travel_ratio, self.pressure_poll_sec,
                   self.pressure_fresh_sec, self.pressure_step_turns, self.pressure_settle_sec,
                   self.pressure_tolerance, self.pressure_full_step_error)
        if (not all(math.isfinite(v) for v in numbers) or self.pulses_per_turn <= 0
                or self.valve_turns <= 0 or not 0 < self.valve_travel_ratio <= 1
                or self.pressure_poll_sec <= 0 or self.pressure_fresh_sec <= 0
                or self.pressure_step_turns <= 0 or self.pressure_settle_sec <= 0
                or self.pressure_full_step_error <= 0
                or not 2 <= self.pressure_step_speed <= 100 or self.open_direction not in (0, 1)
                or self.pressure_tolerance < 0):
            raise ValueError("背压阀行程或调节参数无效")
        self.max_valve_pulses = int(self.pulses_per_turn * self.valve_turns * self.valve_travel_ratio)
        if self.max_valve_pulses < 1:
            raise ValueError("背压阀允许行程必须至少为一个脉冲")
        self.pressure_step_pulses = int(self.pulses_per_turn * self.pressure_step_turns)
        if self.pressure_step_pulses < 1:
            raise ValueError("背压阀每步行程必须至少为一个脉冲")
        self.plc = ModbusTcpClient(plc.get("ip", "192.168.0.10"), int(plc.get("port", "502")), float(plc.get("timeoutSec", "3")))
        self.rs485 = Rs485Client(rs485.get("ip", "192.168.0.201"), int(rs485.get("port", "26")), float(rs485.get("timeoutSec", "2")))
        self._mirror = [None] * MIRROR_SIZE
        self._data_lock = threading.Lock()
        self._last_ok = 0.0
        self._running = False
        self._poll_thread: threading.Thread | None = None
        self._pressure_thread: threading.Thread | None = None
        self._job_lock = threading.Lock()
        self._jobs: dict[str, int] = {}
        self._pressure_targets: dict[int, float] = {}
        self._pressure_decision_after: dict[int, float] = {}
        self._valve_travel = {channel: _ValveTravel() for channel in range(1, 9)}
        self._valve_travel_path = self._resolve_valve_travel_path(rs485.get("valveTravelFile", ""))
        self._load_valve_travel()
        self._pressure_samples: dict[int, tuple[float, float]] = {}
        self._pressure_points: dict[int, str] = {}
        self._pressure_failures: dict[int, tuple[int, str]] = {}
        self._pressure_last_sample: dict[int, float] = {}
        self._pressure_requested_at: dict[int, float] = {}
        self._pressure_limits: dict[int, str] = {}
        self._data_handler: Callable[[DeviceData], None] | None = None
        self._event_handler: Callable[[AdapterEvent], None] | None = None

    def _resolve_valve_travel_path(self, raw: str) -> Path | None:
        text = str(raw).strip()
        if not text:
            return None
        path = Path(text)
        if not path.is_absolute():
            path = Path(__file__).resolve().parent / path
        return path

    def _load_valve_travel(self) -> None:
        """文件不存在时八路保持最紧。参数不一致或内容损坏时也不沿用旧开度。"""
        path = self._valve_travel_path
        if path is None or not path.is_file():
            return
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
            if (payload.get("version") != 1
                    or int(payload.get("pulsesPerTurn")) != self.pulses_per_turn
                    or int(payload.get("maxValvePulses")) != self.max_valve_pulses):
                raise ValueError("行程文件与当前阀门参数不一致")
            channels = payload.get("channels")
            if not isinstance(channels, dict):
                raise ValueError("行程文件缺少通道")
            loaded: dict[int, tuple[int, int]] = {}
            for name, item in channels.items():
                channel = int(name)
                if channel not in self._valve_travel or not isinstance(item, dict):
                    raise ValueError(f"行程通道无效: {name}")
                low = int(item["low"])
                high = int(item["high"])
                if not 0 <= low <= high <= self.max_valve_pulses:
                    raise ValueError(f"行程越界: {name}")
                loaded[channel] = (low, high)
        except Exception as error:
            logger.warning("阀门行程文件无法读取，八路按最紧启动: %s", error)
            return
        for channel, (low, high) in loaded.items():
            travel = self._valve_travel[channel]
            travel.low = low
            travel.high = high

    def _persist_valve_travel(self) -> None:
        """把未完成的一步按停止时的保守开度写入文件，重启后不把这步当成已经走完。"""
        path = self._valve_travel_path
        if path is None:
            return
        channels = {}
        for channel, travel in self._valve_travel.items():
            low, high = travel.low, travel.high
            if travel.pending is not None:
                low += min(0, travel.pending)
                high += max(0, travel.pending)
            channels[str(channel)] = {"low": low, "high": high}
        payload = {
            "version": 1,
            "pulsesPerTurn": self.pulses_per_turn,
            "maxValvePulses": self.max_valve_pulses,
            "channels": channels,
        }
        try:
            path.parent.mkdir(parents=True, exist_ok=True)
            temporary = path.with_name(path.name + ".tmp")
            temporary.write_text(json.dumps(payload, indent=2), encoding="utf-8")
            temporary.replace(path)
        except Exception as error:
            logger.warning("阀门行程文件写入失败: %s", error)

    def start(self) -> None:
        self._running = True
        self._poll_thread = threading.Thread(target=self._poll_loop, name="plc-poll", daemon=True)
        self._pressure_thread = threading.Thread(target=self._pressure_loop, name="pressure-step", daemon=True)
        self._poll_thread.start()
        self._pressure_thread.start()

    def stop(self) -> None:
        self._running = False
        with self._job_lock:
            active = list(self._pressure_targets)
            for channel in active:
                self._valve_travel[channel].interrupt()
            self._persist_valve_travel()
            for channel in active:
                self.rs485.stop(channel)
            self._pressure_targets.clear()
        self.plc.close()
        self.rs485.close()

    def set_data_handler(self, handler: Callable[[DeviceData], None]) -> None:
        self._data_handler = handler

    def set_event_handler(self, handler: Callable[[AdapterEvent], None]) -> None:
        self._event_handler = handler

    def heartbeat_points(self) -> list[dict[str, Any]]:
        online = self._link_online()
        return [{"devicePoint": point, "online": online} for point in self.points]

    def execute(self, command) -> None:
        point = command.device_point
        if point not in self.points:
            self._fail(point, command.message_id, "VIRTUAL_POINT", "控制模块不处理虚拟点")
            return
        self._emit(point, "COMMAND_RUNNING", command.message_id)
        threading.Thread(
            target=self._run_command,
            args=(command,),
            name=f"plc-cmd-{point}",
            daemon=True,
        ).start()

    def _run_command(self, command) -> None:
        try:
            if command.command_name == "stopPressurize":
                # 停止加压走独立的 485 通道，保留紧急停止的即时性。
                reason = self._dispatch(command)
            else:
                with self._command_lock:
                    reason = self._dispatch(command)
        except Exception as error:
            logger.exception("命令失败 %s %s", command.device_point, command.command_name)
            reason = str(error)
        if isinstance(reason, str):
            self._fail(command.device_point, command.message_id, reason, reason)
            return
        if isinstance(reason, _HoldWatch):
            self._follow_hold(command.device_point, command.message_id, reason)
            return
        if isinstance(reason, tuple):
            event_name, payload = reason
            self._emit(command.device_point, event_name, command.message_id, payload)
        self._emit(command.device_point, "COMMAND_COMPLETED", command.message_id)

    def _dispatch(self, command) -> str | None:
        name = command.command_name
        point = command.device_point
        kind = _kind(point)
        params = command.parameters
        if name == "takeControl":
            failed = self._take_control()
            return failed or ("CONTROL_TAKEN", {"ourControl": True})
        if name == "releaseControl":
            failed = self._write_gate(0)
            return failed or ("CONTROL_RELEASED", {"ourControl": False})
        if name == "stopPressurize":
            return self._stop_pressurize(params)
        blocked = self._require_control()
        if blocked:
            return blocked
        if name == "heat":
            return self._heat(point, params)
        if name == "stopHeat":
            return self._stop_heat(point, params)
        if name == "pressurize":
            return self._pressurize(point, params)
        if name == "setAlarmTemp":
            return self._set_alarm(kind, params)
        if name == "setTemp":
            return self._set_temp(kind, params)
        if name == "setFlow":
            return self._set_flow(params)
        if name == "open":
            failed = self._write_ok(int(params["register"]), 1)
            return failed or ("VALVE_OPENED", {"isOpen": True})
        if name == "close":
            failed = self._write_ok(int(params["register"]), 0)
            return failed or ("VALVE_CLOSED", {"isOpen": False})
        if name == "setEnable":
            enable_id = int(params["enableId"])
            if not 1 <= enable_id <= 9:
                return "使能编号必须是 1 到 9"
            return self._write_ok(225 + enable_id, 1 if as_bool(params["enabled"]) else 0)
        if name == "setAlarmMute":
            return self._write_ok(MUTE_REG, 1 if as_bool(params["muted"]) else 0)
        return f"未知命令: {name}"

    def _heat(self, point: str, params: dict[str, Any]) -> str | None:
        channel = int(params["index"])
        planned, hold_temp = plan_heat_writes(segments_from_params(params))
        key = f"heat:{channel}"
        generation = self._bump(key)
        for step, (temperature, minutes) in enumerate(planned, start=1):
            if not self._is_current(key, generation):
                return "STOPPED"
            if not self.plc.write_single_register(step_temp_addr(channel, step), temperature):
                return "PLC_WRITE_FAILED"
            time.sleep(0.01)
            if not self.plc.write_single_register(step_time_addr(channel, step), minutes):
                return "PLC_WRITE_FAILED"
            time.sleep(0.01)
        if not self._is_current(key, generation):
            return "STOPPED"
        if not self.plc.write_single_register(start_addr(channel), 1):
            return "PLC_WRITE_FAILED"
        self._remember(start_addr(channel), 1)
        logger.info("%s 已写入 %d 段并启动，恒温目标 %s°C", point, len(planned), hold_temp)
        target = float(hold_temp)
        return _HoldWatch(
            "TEMPERATURE_ADJUSTING",
            "TEMPERATURE_HOLDING",
            {"temperature": target},
            key,
            generation,
            lambda: self._temperature_ready(channel - 1, target),
        )

    def _stop_heat(self, _point: str, params: dict[str, Any]) -> str | None:
        channel = int(params["index"])
        self._bump(f"heat:{channel}")
        if not self.plc.write_single_register(start_addr(channel), 0):
            return "PLC_WRITE_FAILED"
        self._remember(start_addr(channel), 0)
        return ("HEATING_STOPPED", {})

    def _pressurize(self, _point: str, params: dict[str, Any]) -> str | _HoldWatch:
        channel = int(params["index"])
        target = float(params["targetPressure"])
        if not 1 <= channel <= 8 or not math.isfinite(target) or target < 0:
            return "INVALID_PRESSURE_PARAMETERS"
        key = f"press:{channel}"
        with self._job_lock:
            if self._valve_travel[channel].blocked:
                return "VALVE_POSITION_UNKNOWN"
            generation = self._jobs.get(key, 0) + 1
            self._jobs[key] = generation
            self._pressure_targets[channel] = target
            self._pressure_points[channel] = _point
            self._pressure_requested_at[channel] = time.monotonic()
            self._pressure_limits.pop(channel, None)
        return _HoldWatch(
            "PRESSURE_ADJUSTING",
            "PRESSURE_HOLDING",
            {"pressure": target},
            key,
            generation,
            lambda: self._pressure_ready(channel, target),
        )

    def _stop_pressurize(self, params: dict[str, Any]) -> str | tuple[str, dict[str, Any]]:
        channel = int(params["index"])
        if not 1 <= channel <= 8:
            return "加压通道必须是 1 到 8"
        # 与调压下发共用锁，确保在途运动先结束发送，随后才发送停止命令。
        with self._job_lock:
            key = f"press:{channel}"
            self._jobs[key] = self._jobs.get(key, 0) + 1
            self._pressure_targets.pop(channel, None)
            self._valve_travel[channel].interrupt()
            self._persist_valve_travel()
            if not self.rs485.stop(channel):
                self._valve_travel[channel].blocked = True
                return "MOTOR_STOP_FAILED"
        return ("PRESSURE_STOPPED", {})

    def _set_temp(self, kind: str, params: dict[str, Any]) -> str | None:
        temperature = int(round(float(params["temperature"])))
        point_index = 0
        if kind == "heatsink":
            point_index = int(params["index"])
            addr = heatsink_set_addr(point_index)
            actual = heatsink_actual_addr(point_index)
        elif kind == "incubator":
            point_index = int(params["index"])
            addr = 209 + point_index
            actual = 286 + point_index
        elif kind == "chromatography":
            addr = CHROM_SET
            actual = CHROM_ACTUAL
        else:
            return "该设备没有设温命令"
        if not self.plc.write_single_register(addr, temperature):
            return "PLC_WRITE_FAILED"
        self._remember(addr, temperature)
        target = float(temperature)
        key = f"temp:{kind}:{point_index}"
        generation = self._bump(key)
        return _HoldWatch(
            "TEMPERATURE_ADJUSTING",
            "TEMPERATURE_HOLDING",
            {"temperature": target},
            key,
            generation,
            lambda: self._temperature_ready(actual, target),
        )

    def _set_alarm(self, kind: str, params: dict[str, Any]) -> str | None:
        temperature = int(round(float(params["alarmTemperature"])))
        if kind == "reactor":
            addr = 211 + int(params["index"])
        elif kind == "heatsink":
            channel = int(params["index"])
            addr = HEATSINK_ALARM.get(channel, 0)
            if addr == 0:
                return "热阱通道无效"
        elif kind == "incubator":
            addr = 223 + int(params["index"])
        elif kind == "chromatography":
            addr = CHROM_ALARM
        else:
            return "该设备没有报警温度"
        return self._write_ok(addr, temperature)

    def _set_flow(self, params: dict[str, Any]) -> str | None:
        channel = int(params["index"])
        meter = int(params["meter"])
        if meter not in (1, 2):
            return "流量计编号必须是 1 或 2"
        flow = int(round(float(params["flow"])))
        if not self.plc.write_single_register(gas_set_addr(channel, meter), flow):
            return "PLC_WRITE_FAILED"
        self._remember(gas_set_addr(channel, meter), flow)
        key = f"flow:{channel}:{meter}"
        generation = self._bump(key)
        return _HoldWatch(
            "FLOW_ADJUSTING",
            "FLOW_HOLDING",
            {"flow": float(flow)},
            key,
            generation,
            lambda: self._flow_ready(channel, meter, flow),
        )

    def _take_control(self) -> str | None:
        """读取现场快照，接管后逐个写入并读回核验可写设定。

        写 267=1 会把后续寄存器搬入生效区；搬入的值可能为 0，
        因此必须先保存现场值并接管，再从快照恢复设定。报警温度先恢复，
        每项写后立即读回；其余设定也按相同方式核验。
        """
        # 使用本次请求自己的读取结果，不能复用或再次读取轮询缓存。
        snapshot: list[int] = []
        for start in range(0, MIRROR_SIZE, 30):
            count = min(30, MIRROR_SIZE - start)
            values = self.plc.read_holding_registers(start, count)
            if values is None or len(values) != count:
                return "PLC_READ_FAILED"
            snapshot.extend(values)
            if start + count < MIRROR_SIZE:
                time.sleep(self.chunk_gap_sec)
        if self._write_gate(1):
            return "PLC_WRITE_FAILED"
        for addr in PRESERVE_BEFORE_CONTROL:
            if addr >= len(snapshot) or snapshot[addr] is None:
                return "PLC_RESTORE_FAILED"
            value = int(snapshot[addr])
            if addr in ALARM_REGISTERS and value == 0:
                value = 300
            if not self.plc.write_single_register(addr, value):
                return "PLC_RESTORE_WRITE_FAILED"
            self._remember(addr, value)
            readback = self.plc.read_holding_registers(addr, 1)
            if readback is None or len(readback) != 1 or int(readback[0]) != value:
                return "PLC_RESTORE_VERIFY_FAILED"
            self._remember(addr, int(readback[0]))
            time.sleep(0.01)

        gate = self.plc.read_holding_registers(MANUAL_REG, 1)
        if gate is None or len(gate) != 1:
            return "PLC_CONTROL_VERIFY_FAILED"
        self._remember(MANUAL_REG, int(gate[0]))
        if int(gate[0]) != 1:
            return "PLC_CONTROL_NOT_HELD"
        return None

    def _write_gate(self, value: int) -> str | None:
        if not self.plc.write_single_register(MANUAL_REG, value):
            return "PLC_WRITE_FAILED"
        self._remember(MANUAL_REG, value)
        return None

    def _write_ok(self, addr: int, value: int) -> str | None:
        if not self.plc.write_single_register(addr, value):
            return "PLC_WRITE_FAILED"
        self._remember(addr, value)
        return None

    def _follow_hold(self, point: str, message_id: str, watch: _HoldWatch) -> None:
        if not self._is_current(watch.key, watch.generation):
            reason = self._watch_failure(watch)
            self._fail(point, message_id, reason, reason)
            return
        self._emit(point, watch.start_event, message_id, watch.payload)
        while self._running and self._is_current(watch.key, watch.generation):
            if not watch.key.startswith("press:") and not self._link_online():
                self._fail(point, message_id, "PLC_OFFLINE", f"等待{watch.event_name}时PLC离线")
                return
            if watch.ready():
                self._emit(point, watch.event_name, message_id, watch.payload)
                self._emit(point, "COMMAND_COMPLETED", message_id)
                return
            time.sleep(1)
        if self._running:
            reason = self._watch_failure(watch)
            self._fail(point, message_id, reason, reason)

    def _watch_failure(self, watch: _HoldWatch) -> str:
        if watch.key.startswith("press:"):
            with self._job_lock:
                failure = self._pressure_failures.get(int(watch.key.split(":")[1]))
                if failure and failure[0] == watch.generation:
                    return failure[1]
        return "STOPPED"

    def _temperature_ready(self, addr: int, target: float) -> bool:
        raw = self._raw(addr)
        return raw is not None and abs((raw / 10.0) - target) <= self.temperature_tolerance

    def _pressure_ready(self, channel: int, target: float) -> bool:
        with self._job_lock:
            travel = self._valve_travel[channel]
            travel.settle(time.monotonic())
            with self._data_lock:
                sample = self._pressure_samples.get(channel)
            return (self._pressure_targets.get(channel) == target and sample is not None
                    and not travel.blocked and travel.pending is None
                    and sample[1] > travel.ready_after
                    and sample[1] > self._pressure_decision_after.get(channel, 0)
                    and sample[1] >= self._pressure_requested_at.get(channel, 0)
                    and time.monotonic() - sample[1] <= self.pressure_fresh_sec
                    and abs(sample[0] - target) <= self.pressure_tolerance + 1e-12)

    def _flow_ready(self, channel: int, segment: int, flow: int) -> bool:
        raw = self._raw(gas_read_addr(channel, segment))
        return raw is not None and int(raw) == flow

    def _require_control(self) -> str | None:
        if not self._link_online():
            return "PLC_OFFLINE"
        raw = self._raw(MANUAL_REG)
        if raw is None or int(raw) != 1:
            return "NOT_IN_CONTROL"
        return None

    def _poll_loop(self) -> None:
        while self._running:
            if self._read_all():
                self._publish_telemetry()
            time.sleep(self.poll_interval_sec)

    def _read_all(self) -> bool:
        mirror: list[int | None] = [None] * MIRROR_SIZE
        for start in range(0, 270, 30):
            if not self._running:
                return False
            values = self.plc.read_holding_registers(start, 30)
            if values is None or len(values) != 30:
                return False
            mirror[start:start + 30] = values
            time.sleep(self.chunk_gap_sec)
        tail = self.plc.read_holding_registers(270, 27)
        if tail is None or len(tail) != 27:
            return False
        mirror[270:297] = tail
        with self._data_lock:
            self._mirror = mirror
            self._last_ok = time.monotonic()
        return True

    def _publish_telemetry(self) -> None:
        if self._data_handler is None:
            return
        stamp = now_ms()
        for point in self.points:
            fields = self._fields_for(point)
            if fields is None:
                continue
            self._data_handler(DeviceData(point, fields, stamp))

    def _fields_for(self, point: str) -> dict[str, Any] | None:
        kind = _kind(point)
        fields = self.point_fields.get(point, {})
        if kind == "reactor":
            channel = int(fields["index"])
            temp = self._raw(channel - 1)
            press = self._raw(back_pressure_addr(channel))
            if temp is None or press is None:
                return None
            return {"raw_temp": temp / 10.0, "raw_press": press / 100.0}
        if kind == "heatsink":
            channel = int(fields["index"])
            temp = self._raw(heatsink_actual_addr(channel))
            return None if temp is None else {"raw_temp": temp / 10.0}
        if kind == "flow":
            channel = int(fields["index"])
            meter = int(fields["meter"])
            raw = self._raw(gas_read_addr(channel, meter))
            return None if raw is None else {"raw_flow": float(raw)}
        if kind == "incubator":
            incubator_id = int(fields["index"])
            temp = self._raw(286 + incubator_id)
            return None if temp is None else {"raw_temp": temp / 10.0}
        if kind == "chromatography":
            temp = self._raw(CHROM_ACTUAL)
            return None if temp is None else {"raw_temp": temp / 10.0}
        if kind == "valve":
            raw = self._raw(int(fields["register"]))
            return None if raw is None else {"raw_open": int(raw) != 0}
        if kind == "plant":
            gate = self._raw(MANUAL_REG)
            if gate is None:
                return None
            body: dict[str, Any] = {"raw_our_control": int(gate) == 1}
            for enable_id in range(1, 10):
                raw = self._raw(225 + enable_id)
                body[f"raw_enable_{enable_id}"] = False if raw is None else int(raw) != 0
            return body
        return None

    def _pressure_loop(self) -> None:
        while self._running:
            with self._job_lock:
                targets = dict(self._pressure_targets)
            if targets:
                self._read_pressure_samples()
            for channel, target in targets.items():
                self._pressure_step(channel, target)
            time.sleep(self.pressure_poll_sec)

    def _read_pressure_samples(self) -> bool:
        # 独立读取八路后段压力，避免每次等待整个 PLC 点表刷新。
        stamp = time.monotonic()
        values = self.plc.read_holding_registers(11, 22)
        with self._data_lock:
            self._pressure_samples = {}
            if values is None or len(values) != 22:
                return False
            for channel in range(1, 9):
                raw = values[(channel - 1) * 3]
                if raw is not None and raw >= 0:
                    self._pressure_samples[channel] = (raw / 100.0, stamp)
                    self._mirror[back_pressure_addr(channel)] = raw
        return True

    def _pressure_fault(self, channel: int, reason: str) -> None:
        """调用方持有 _job_lock；失败后取消调节，并终结仍在等待的命令。"""
        key = f"press:{channel}"
        generation = self._jobs.get(key, 0)
        self._pressure_failures[channel] = (generation, reason)
        self._jobs[key] = generation + 1
        self._pressure_targets.pop(channel, None)
        travel = self._valve_travel[channel]
        travel.interrupt()
        self._persist_valve_travel()
        stopped = self.rs485.stop(channel)
        if not stopped:
            travel.blocked = True
        self._emit(self._pressure_points.get(channel, f"Reactor{channel}"),
                   "PRESSURE_CONTROL_FAILED", None, {"reason": reason, "motorStopConfirmed": stopped})

    def _pressure_step(self, channel: int, target: float) -> None:
        with self._job_lock:
            if not self._running or self._pressure_targets.get(channel) != target:
                return
            with self._data_lock:
                sample = self._pressure_samples.get(channel)
            now = time.monotonic()
            if sample is None or now - sample[1] > self.pressure_fresh_sec:
                self._pressure_fault(channel, "PRESSURE_FEEDBACK_INVALID")
                return
            current, stamp = sample
            travel = self._valve_travel[channel]
            pending_before = travel.pending
            travel.settle(now)
            if pending_before is not None and travel.pending is None:
                self._persist_valve_travel()
            if (travel.pending is not None or stamp <= travel.ready_after
                    or stamp <= self._pressure_decision_after.get(channel, 0)
                    or stamp < self._pressure_requested_at.get(channel, 0)
                    or stamp <= self._pressure_last_sample.get(channel, 0)):
                return
            self._pressure_last_sample[channel] = stamp
            err = target - current
            if abs(err) <= self.pressure_tolerance + 1e-12:
                self._pressure_limits.pop(channel, None)
                return
            opening = err < 0  # 压力偏高：松开泄压；偏低：拧紧减少泄压。
            remaining = self.max_valve_pulses - travel.high if opening else travel.low
            if remaining <= 0:
                boundary = "MAX" if opening else "MIN"
                if self._pressure_limits.get(channel) != boundary:
                    self._pressure_limits[channel] = boundary
                    self._emit(self._pressure_points.get(channel, f"Reactor{channel}"),
                               "PRESSURE_LIMIT_REACHED", None,
                               {"boundary": boundary, "pressure": current, "targetPressure": target,
                                "openingTurnsMin": travel.low / self.pulses_per_turn,
                                "openingTurnsMax": travel.high / self.pulses_per_turn})
                return  # 保留目标，继续读压力，允许压力变化后反向调节。
            self._pressure_limits.pop(channel, None)
            direction = self.open_direction if opening else 1 - self.open_direction
            speed = self.pressure_step_speed
            ratio = min(abs(err) / self.pressure_full_step_error, 1.0)
            # 向下取整为整脉冲，微小误差修正避免0.2MPa等值少算一个脉冲。
            requested_pulses = max(1, int(self.pressure_step_pulses * ratio + 1e-9))
            pulse = min(remaining, self.pressure_step_pulses, requested_pulses)
            sent = self.rs485.move(channel, direction, speed, pulse)
            travel.begin(pulse if opening else -pulse, pulse * 60 / (speed * self.pulses_per_turn))
            # 运动结束时间与压力等待时间分开：等待时仍读压力、允许停止，
            # 但只能使用等待结束后采到的新读数进行下一次调整或判断达标。
            self._pressure_decision_after[channel] = travel.ready_after + self.pressure_settle_sec
            self._persist_valve_travel()
            if not sent:
                travel.blocked = True
                self._pressure_fault(channel, "MOTOR_MOVE_FAILED")
                return
            alarm = self.rs485.read_alarm(channel)
            if alarm is not False:
                travel.blocked = True
                self._pressure_fault(channel, "MOTOR_ALARM" if alarm else "MOTOR_FEEDBACK_INVALID")

    def _pressure(self, channel: int) -> float | None:
        with self._data_lock:
            sample = self._pressure_samples.get(channel)
        if sample is None or time.monotonic() - sample[1] > self.pressure_fresh_sec:
            return None
        return sample[0]

    def _raw(self, addr: int) -> int | None:
        with self._data_lock:
            if addr < 0 or addr >= len(self._mirror):
                return None
            value = self._mirror[addr]
        return None if value is None else int(value)

    def _remember(self, addr: int, value: int) -> None:
        signed = value if value < 0x8000 else value - 0x10000
        with self._data_lock:
            if 0 <= addr < len(self._mirror):
                self._mirror[addr] = signed

    def _link_online(self) -> bool:
        return self._last_ok > 0 and (time.monotonic() - self._last_ok) <= self.link_timeout_sec

    def _bump(self, key: str) -> int:
        with self._job_lock:
            self._jobs[key] = self._jobs.get(key, 0) + 1
            return self._jobs[key]

    def _is_current(self, key: str, generation: int) -> bool:
        with self._job_lock:
            return self._jobs.get(key) == generation

    def _emit(self, point: str, name: str, message_id: str | None, payload: dict[str, Any] | None = None) -> None:
        if self._event_handler is not None:
            self._event_handler(AdapterEvent(point, name, message_id, payload or {}, now_ms()))

    def _fail(self, point: str, message_id: str, reason: str, error: str) -> None:
        self._emit(point, "COMMAND_FAILED", message_id, {"reason": reason, "error": error})
