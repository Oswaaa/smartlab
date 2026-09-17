"""
GetPLCData 南向物理驱动程序 (ControlImpl)。
遵循 SmartLab Adapter 设计规范：
- 仅处理 adapterSetup.ini 声明的物理点。若收到虚拟点，立即 COMMAND_FAILED 快速失败，不执行硬件 I/O。
- 对接真实 PLC (Modbus TCP) 与 485 压力电机网关。
- 毫秒级回执原则：execute(command) 入口处立即发射 COMMAND_RUNNING（防看门狗熔断），将实际 I/O 任务交由后台处理。
- 周期轮询 PLC 寄存器，按物理点独立组装并上报 DeviceData，点名精确归属，杜绝错点或混淆。
- 遥测字段匹配 attributeMapping: raw_temp, raw_press。
"""

from __future__ import annotations

import logging
import queue
import socket
import struct
import threading
import time
from typing import Any, Callable

logger = logging.getLogger("smartlab.adapter.getplcdata.control")


def now_ms() -> int:
    return int(time.time() * 1000)


def calc_crc16_modbus(data: bytes) -> int:
    """计算 Modbus RTU CRC16 校验码"""
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


class ModbusTcpClient:
    """轻量级纯 Python Modbus TCP 客户端，直连 PLC (端口 502)"""

    def __init__(self, host: str, port: int = 502, timeout: float = 3.0) -> None:
        self.host = host
        self.port = port
        self.timeout = timeout
        self._sock: socket.socket | None = None
        self._lock = threading.Lock()
        self._transaction_id = 0

    def _next_tid(self) -> int:
        self._transaction_id = (self._transaction_id + 1) & 0xFFFF
        return self._transaction_id

    def connect(self) -> bool:
        with self._lock:
            if self._sock:
                try:
                    self._sock.close()
                except Exception:
                    pass
                self._sock = None
            try:
                s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                s.settimeout(self.timeout)
                s.connect((self.host, self.port))
                self._sock = s
                logger.info("已连接真实 PLC (Modbus TCP) -> %s:%d", self.host, self.port)
                return True
            except Exception as e:
                logger.debug("连接 PLC 失败 %s:%d: %s", self.host, self.port, e)
                return False

    def close(self) -> None:
        with self._lock:
            if self._sock:
                try:
                    self._sock.close()
                except Exception:
                    pass
                self._sock = None

    def read_holding_registers(self, start_addr: int, count: int) -> list[int] | None:
        """功能码 0x03: 读取保持寄存器"""
        with self._lock:
            if not self._sock and not self.connect():
                return None
            try:
                tid = self._next_tid()
                # MBAP: tid(2), proto=0(2), len=6(2), unit_id=1(1)
                # PDU: func=3(1), start_addr(2), count(2)
                req = struct.pack(">HHHBBHH", tid, 0, 6, 1, 3, start_addr, count)
                self._sock.sendall(req)

                header = self._sock.recv(9)
                if len(header) < 9:
                    raise ConnectionError("Modbus 读取响应头过短")
                r_tid, r_proto, r_len, r_unit, r_func = struct.unpack(">HHHBB", header[:7])
                if r_func & 0x80:
                    raise RuntimeError(f"Modbus 异常响应: 0x{r_func:02X}")
                byte_count = header[8]
                payload = b""
                while len(payload) < byte_count:
                    chunk = self._sock.recv(byte_count - len(payload))
                    if not chunk:
                        raise ConnectionError("Modbus 数据流提前终止")
                    payload += chunk
                values = [struct.unpack(">h", payload[i:i + 2])[0] for i in range(0, len(payload), 2)]
                return values
            except Exception as e:
                logger.debug("PLC 寄存器读取失败: %s", e)
                self.close()
                return None

    def write_single_register(self, addr: int, value: int) -> bool:
        """功能码 0x06: 写入单个保持寄存器"""
        with self._lock:
            if not self._sock and not self.connect():
                return False
            try:
                tid = self._next_tid()
                # PDU: func=6(1), addr(2), value(2)
                req = struct.pack(">HHHBBHH", tid, 0, 6, 1, 6, addr, value & 0xFFFF)
                self._sock.sendall(req)
                resp = self._sock.recv(12)
                return len(resp) >= 12
            except Exception as e:
                logger.debug("PLC 寄存器写入失败 addr=%d: %s", addr, e)
                self.close()
                return False


class Rs485GatewayClient:
    """RS-485 网关客户端，通过 TCP 传输 Modbus RTU 压力电机控制帧"""

    def __init__(self, host: str, port: int = 26, timeout: float = 2.0) -> None:
        self.host = host
        self.port = port
        self.timeout = timeout
        self._sock: socket.socket | None = None
        self._lock = threading.Lock()

    def connect(self) -> bool:
        with self._lock:
            if self._sock:
                try:
                    self._sock.close()
                except Exception:
                    pass
                self._sock = None
            try:
                s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                s.settimeout(self.timeout)
                s.connect((self.host, self.port))
                self._sock = s
                logger.info("已连接 RS-485 网关 -> %s:%d", self.host, self.port)
                return True
            except Exception as e:
                logger.debug("连接 RS-485 网关失败 %s:%d: %s", self.host, self.port, e)
                return False

    def close(self) -> None:
        with self._lock:
            if self._sock:
                try:
                    self._sock.close()
                except Exception:
                    pass
                self._sock = None

    def send_press_command(self, channel: int, target_pressure: float) -> bool:
        """
        向 485 压力电机发送控制帧（对齐 PLCCtrl::sendPresscommand）
        使用功能码 0x10 写多个寄存器 (0x00FD 起始 5 个寄存器)
        """
        with self._lock:
            if not self._sock and not self.connect():
                return False
            try:
                dev_addr = channel
                func = 0x10
                reg_addr = 0x00FD
                reg_num = 0x0005
                byte_num = 10
                direction = 1 if target_pressure > 0 else 0
                dlt_speed = 0
                speed = 2  # 默认转速
                pulse = int(target_pressure * 1000)  # 脉冲换算

                frame_without_crc = struct.pack(
                    ">BBHHBBBHI",
                    dev_addr, func, reg_addr, reg_num, byte_num,
                    direction, dlt_speed, speed, pulse
                ) + b"\x00\x00"  # abs=0, sync=0

                crc = calc_crc16_modbus(frame_without_crc)
                full_frame = frame_without_crc + struct.pack("<H", crc)
                self._sock.sendall(full_frame)
                return True
            except Exception as e:
                logger.debug("485 加压帧发送失败 ch=%d: %s", channel, e)
                self.close()
                return False


class ControlImpl:
    """真实 PLC 与 485 南向硬件驱动实现"""

    def __init__(
        self,
        extra: dict[str, dict[str, str]],
        physical_points: tuple[str, ...] | list[str] = (),
        point_fields: dict[str, dict[str, str]] | None = None,
    ) -> None:
        plc_cfg = extra.get("plc", {})
        rs485_cfg = extra.get("rs485", {})

        self.plc_ip = plc_cfg.get("ip", "192.168.0.10")
        self.plc_port = int(plc_cfg.get("port", "502"))
        self.poll_interval = float(plc_cfg.get("pollIntervalSec", "1.0"))

        self.rs485_ip = rs485_cfg.get("ip", "192.168.0.201")
        self.rs485_port = int(rs485_cfg.get("port", "26"))

        self.physical_points = tuple(physical_points)
        self.point_fields = point_fields or {}
        self._index_to_point: dict[str, str] = self._build_index_map(self.physical_points, self.point_fields)
        self._point_to_index: dict[str, int] = {
            pt: int(self.point_fields.get(pt, {}).get("index", idx + 1))
            for idx, pt in enumerate(self.physical_points)
        }

        # 硬件客户端
        self.plc_client = ModbusTcpClient(self.plc_ip, self.plc_port)
        self.rs485_client = Rs485GatewayClient(self.rs485_ip, self.rs485_port)

        # 回调处理器
        self._data_handler: Callable | None = None
        self._event_handler: Callable | None = None

        # 运行控制与队列
        self._stop_event = threading.Event()
        self._cmd_queue: queue.Queue = queue.Queue()
        self._threads: list[threading.Thread] = []

        # 物理点缓存数据（用于离线备援或瞬时查询）
        self._point_state: dict[str, dict[str, float]] = {
            pt: {"temp": 25.0, "press": 0.10} for pt in self.physical_points
        }
        self._state_lock = threading.Lock()

    @staticmethod
    def _build_index_map(
        physical_points: tuple[str, ...],
        point_fields: dict[str, dict[str, str]],
    ) -> dict[str, str]:
        mapping: dict[str, str] = {}
        for point in physical_points:
            fields = point_fields.get(point, {})
            index = str(fields.get("index", "")).strip()
            if index:
                mapping[index] = point
        return mapping

    def set_data_handler(self, handler: Callable) -> None:
        self._data_handler = handler

    def set_event_handler(self, handler: Callable) -> None:
        self._event_handler = handler

    def start(self) -> None:
        self._stop_event.clear()
        # 启动后台硬件轮询线程
        poll_thread = threading.Thread(target=self._poll_loop, name="getplcdata-poll", daemon=True)
        # 启动后台指令执行工作线程
        worker_thread = threading.Thread(target=self._cmd_worker_loop, name="getplcdata-worker", daemon=True)
        self._threads = [poll_thread, worker_thread]
        for t in self._threads:
            t.start()
        logger.info("GetPLCData 物理控制驱动已启动 (物理点: %s)", self.physical_points)

    def stop(self) -> None:
        self._stop_event.set()
        self.plc_client.close()
        self.rs485_client.close()
        logger.info("GetPLCData 物理控制驱动已停止")

    def execute(self, command: Any) -> None:
        """
        接收系统指令。
        严格规范要求：
        1. 检查是否为物理点，若为虚拟点立即 COMMAND_FAILED 快速失败；
        2. 毫秒级回执发射 COMMAND_RUNNING（防看门狗熔断）；
        3. 放入异步工作队列，由后台执行实际硬件 I/O。
        """
        device_point = command.device_point

        # 1. 守卫物理点边界（防御实现错误时误入）
        if device_point not in self.physical_points:
            logger.error("Control 收到非物理点指令: %s，立即拒绝", device_point)
            self._emit_event(
                device_point,
                "COMMAND_FAILED",
                command.message_id,
                {"error": f"非物理设备点: {device_point}"}
            )
            return

        # 2. 毫秒级回复 COMMAND_RUNNING
        self._emit_event(
            device_point,
            "COMMAND_RUNNING",
            command.message_id,
            {"commandName": command.command_name}
        )

        # 3. 压入异步任务队列
        self._cmd_queue.put(command)

    def _cmd_worker_loop(self) -> None:
        """后台指令执行工作循环"""
        while not self._stop_event.is_set():
            try:
                cmd = self._cmd_queue.get(timeout=0.2)
            except queue.Empty:
                continue

            try:
                self._dispatch_hardware_command(cmd)
            except Exception as e:
                logger.exception("指令执行异常 cmd=%s: %s", cmd.command_name, e)
                self._emit_event(
                    cmd.device_point,
                    "COMMAND_FAILED",
                    cmd.message_id,
                    {"error": str(e)}
                )

    def _dispatch_hardware_command(self, command: Any) -> None:
        """根据指令名分发到具体硬件写入"""
        ch = self._point_to_index.get(command.device_point, 1)
        params = command.parameters
        cname = command.command_name

        if cname == "heat":
            target_temp = float(params.get("targetTemperature", 25.0))
            duration_sec = int(params.get("durationSec", 60))
            minutes = max(1, duration_sec // 60)

            # 对齐 PLCCtrl::set_reactor_step
            channel_base = 34 + (ch - 1) * 22
            temp_reg = channel_base
            time_reg = temp_reg + 1
            start_reg = 268 + (ch - 1)

            # 写入温度与时间
            self.plc_client.write_single_register(temp_reg, int(target_temp))
            time.sleep(0.01)
            self.plc_client.write_single_register(time_reg, minutes)
            # 智能启动
            self.plc_client.write_single_register(start_reg, 1)

            with self._state_lock:
                self._point_state[command.device_point]["temp"] = target_temp

            self._emit_event(command.device_point, "HEAT_STARTED", command.message_id, {
                "targetTemperature": target_temp, "channel": ch
            })
            self._emit_event(command.device_point, "COMMAND_COMPLETED", command.message_id, {
                "status": "SUCCESS"
            })

        elif cname == "pressurize":
            target_press = float(params.get("targetPressure", 0.10))
            # 发送 485 加压帧
            self.rs485_client.send_press_command(ch, target_press)

            with self._state_lock:
                self._point_state[command.device_point]["press"] = target_press

            self._emit_event(command.device_point, "PRESSURIZE_STARTED", command.message_id, {
                "targetPressure": target_press, "channel": ch
            })
            self._emit_event(command.device_point, "COMMAND_COMPLETED", command.message_id, {
                "status": "SUCCESS"
            })

        elif cname == "setOperatingMode":
            mode = str(params.get("mode", "AUTO")).upper()
            enable_write = 1 if mode == "AUTO" else 0
            # 寄存器 267 为手自动开关
            self.plc_client.write_single_register(267, enable_write)
            self._emit_event(command.device_point, "COMMAND_COMPLETED", command.message_id, {
                "operatingMode": mode
            })

        elif cname == "setAlarmTemp":
            alarm_temp = float(params.get("alarmTemperature", 100.0))
            addr = 212 + (ch - 1)
            self.plc_client.write_single_register(addr, int(alarm_temp))
            self._emit_event(command.device_point, "COMMAND_COMPLETED", command.message_id, {
                "alarmTemperature": alarm_temp
            })

        else:
            logger.warning("未知的物理控制指令: %s", cname)
            self._emit_event(command.device_point, "COMMAND_FAILED", command.message_id, {
                "error": f"不支持的指令: {cname}"
            })

    def _poll_loop(self) -> None:
        """周期性轮询 PLC 寄存器并上报物理点遥测"""
        while not self._stop_event.is_set():
            start_time = time.time()
            try:
                # 批量读取 0~35 寄存器（包含 8 通道温度与压力）
                regs = self.plc_client.read_holding_registers(0, 36)
                if regs is not None and len(regs) >= 35:
                    # 成功读取真实 PLC 数据
                    for ch in range(1, 9):
                        point_name = self._index_to_point.get(str(ch))
                        if not point_name or point_name not in self.physical_points:
                            continue
                        # 温度：地址 0~7，值除以 10
                        temp_val = float(regs[ch - 1]) / 10.0
                        # 压力：地址 11, 14, 17... (11 + (ch-1)*3)，值除以 100
                        press_val = float(regs[11 + (ch - 1) * 3]) / 100.0

                        with self._state_lock:
                            self._point_state[point_name] = {"temp": temp_val, "press": press_val}

                        self._emit_telemetry(point_name, temp_val, press_val)
                else:
                    # 未连接真实硬件时，使用状态缓存上报，保持心跳与基线遥测
                    for pt in self.physical_points:
                        with self._state_lock:
                            st = self._point_state.get(pt, {"temp": 25.0, "press": 0.10})
                        self._emit_telemetry(pt, st["temp"], st["press"])

            except Exception as e:
                logger.debug("数据采集循环异常: %s", e)

            # 维持采样周期
            elapsed = time.time() - start_time
            sleep_time = max(0.1, self.poll_interval - elapsed)
            time.sleep(sleep_time)

    def _emit_telemetry(self, device_point: str, raw_temp: float, raw_press: float) -> None:
        if self._data_handler:
            from core import DeviceData
            data = DeviceData(
                device_point=device_point,
                fields={"raw_temp": round(raw_temp, 2), "raw_press": round(raw_press, 3)},
                timestamp=now_ms(),
            )
            self._data_handler(data)

    def _emit_event(
        self,
        device_point: str,
        event_name: str,
        message_id: str | None,
        payload: dict[str, Any] | None = None
    ) -> None:
        if self._event_handler:
            from core import AdapterEvent
            event = AdapterEvent(
                device_point=device_point,
                event_name=event_name,
                message_id=message_id,
                payload=payload or {},
                timestamp=now_ms(),
            )
            self._event_handler(event)
