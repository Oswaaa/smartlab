"""物理设备点。十一路指令按各自的设备点进入同一个分发器。

运动轮询、点动看门狗和第九路等待在运动线程；视觉 HTTP 在另一条线程。
南向连不上板卡时不阻断北向：进程继续注册、心跳和租约；
非视觉点 heartbeat online=false；真机指令回 COMMAND_FAILED；后台定时重连。
"""

from __future__ import annotations

import logging
import math
import threading
from collections import OrderedDict
from pathlib import Path
from typing import Any, Callable

from core import AdapterEvent, DeviceData
from devices import DeviceDispatcher
from southbound import DEFAULT_DLL, MotionEngine, MotionParams, RealCard, WallClock

logger = logging.getLogger("smartlab.adapter.motionstage.control")


def _enabled(value: Any) -> bool:
    return str(value or "").strip().lower() in {"1", "true", "yes", "on"}


class ControlImpl:
    def __init__(
        self,
        extra: dict[str, dict[str, str]],
        physical_points: tuple[str, ...] | list[str] = (),
        point_fields: dict[str, dict[str, str]] | None = None,
    ) -> None:
        card_cfg = extra.get("card", {})
        self.local_ip = card_cfg.get("localIp", "192.168.0.200")
        self.card_ip = card_cfg.get("cardIp", "192.168.0.199")
        self.local_port = int(card_cfg.get("localPort", "60000"))
        self.card_port = int(card_cfg.get("cardPort", "60000"))
        self.reconnect_sec = max(1.0, float(card_cfg.get("reconnectIntervalSec", "5")))
        dll_text = card_cfg.get("dllPath", "").strip()
        self.dll_path = Path(dll_text) if dll_text else DEFAULT_DLL
        self.encoder_feedback = _enabled(card_cfg.get("encoderFeedbackEnabled", "false"))
        self.poll_failure_tolerance = max(1, int(card_cfg.get("pollFailureTolerance", "3")))
        self.health_probe_ms = int(card_cfg.get('healthProbeIntervalMs', '1000'))
        self.health_timeout_ms = int(card_cfg.get('healthTimeoutMs', '5000'))
        if not 0 < self.health_probe_ms < self.health_timeout_ms:
            raise ValueError('Require 0 < healthProbeIntervalMs < healthTimeoutMs')
        # 部分固件不维护 MC_GetSts 的采样时钟（恒为 0xCCCCCCCC），此时时钟既不递增
        # 也不可信；关闭后不读时钟，改由 pump() 内的轴状态/DI 往返体现通信存活。
        self.clock_health = _enabled(card_cfg.get('clockHealthEnabled', 'true'))
        io_poll_ms = max(1, int(card_cfg.get("ioPollIntervalMs", "1000")))
        self.motion = MotionParams(extra.get("motion", {}))
        self.physical_points = tuple(physical_points)
        fields = point_fields or {}
        catalog = {
            point: dict(fields[point])
            for point in self.physical_points
            if point in fields and fields[point].get("kind")
        }
        self.clock = WallClock()
        self._data_handler: Callable | None = None
        self._event_handler: Callable | None = None
        self.dispatcher = DeviceDispatcher(
            catalog,
            self.clock,
            self._relay_event,
            self._relay_data,
            extra.get("vision") or {},
            keepalive_ms=self.motion.loop_period_ms * self.motion.telemetry_every,
            io_poll_ms=io_poll_ms,
        )
        self._poll_failures = 0
        self.engine: MotionEngine | None = None
        self._running = False
        self._closing = False
        self._lock = threading.RLock()
        self._io_lock = threading.RLock()
        self._online = False
        self._io_failure_generation = 0
        self._offline_rejections: OrderedDict[tuple[str, str], AdapterEvent] = OrderedDict()
        self._health_engine: MotionEngine | None = None
        self._last_poll_ms = 0
        self._last_progress_ms = 0
        self._board_clock: float | None = None
        self._next_probe_ms = 0
        self._health_handler: Callable | None = None
        self.dispatcher.on_io_error = self._io_failed
        self.dispatcher.connection_check = self._card_is_online
        self._wake = threading.Event()
        self._motion_thread: threading.Thread | None = None
        self._vision_thread: threading.Thread | None = None
        self._reconnect_thread: threading.Thread | None = None
        self._watchdog_thread: threading.Thread | None = None

    def set_health_handler(self, handler: Callable) -> None:
        self._health_handler = handler

    def _set_online(self, online: bool, expected_generation: int | None = None) -> None:
        with self._lock:
            if online and (self._wake.is_set() or not self.engine or not self.engine.connection_valid
                           or (expected_generation is not None and expected_generation != self._io_failure_generation)):
                return
            changed = self._online != online
            self._online = online
        if changed:
            logger.info('运动卡通信状态: %s', 'online' if online else 'offline')
            if self._health_handler:
                self._health_handler()

    def _io_failed(self, error: BaseException) -> None:
        with self._lock:
            self._io_failure_generation += 1
            self._next_probe_ms = 0
            self._set_online(False)
        logger.warning('运动卡指令通信失败: %s', error)

    def _watchdog_once(self) -> None:
        # Never acquire dispatcher/engine/SDK locks here: that is what may be stuck.
        with self._lock:
            engine = self.engine
            expired = bool(engine and engine is self._health_engine and engine.connection_valid
                           and self.clock.mono_ms() - min(self._last_poll_ms, self._last_progress_ms)
                           >= self.health_timeout_ms)
            if expired:
                engine.connection_valid = False
        if expired:
            self._set_online(False)
            logger.error('运动卡健康看门狗超时；撤销在线状态，等待当前 SDK 调用退出后重连')

    def _watchdog_loop(self) -> None:
        while not self._wake.wait(0.1):
            self._watchdog_once()

    def _card_is_online(self) -> bool:
        self._watchdog_once()
        with self._lock:
            return bool(self._online and self.engine and self.engine.opened
                        and self.engine.connection_valid and not self._closing and not self._wake.is_set())

    def set_data_handler(self, handler: Callable) -> None:
        self._data_handler = handler

    def set_event_handler(self, handler: Callable) -> None:
        self._event_handler = handler

    def heartbeat_points(self) -> list[dict[str, Any]]:
        self._watchdog_once()
        with self._lock:
            card_online = bool(self._online and not self._closing and self.engine
                               and self.engine.opened and self.engine.connection_valid)
            kinds = {name: cfg.get("kind") for name, cfg in self.dispatcher.points.items()}
            vision_online = bool(self.dispatcher.vision_online)
        return [
            {
                "devicePoint": point,
                "online": vision_online if kinds.get(point) == "vision" else card_online,
            }
            for point in self.physical_points
        ]

    def start(self) -> None:
        self._wake.clear()
        self._running = True
        kinds = ", ".join(f"{name}={cfg.get('kind')}" for name, cfg in self.dispatcher.points.items())
        logger.info("设备点: %s", kinds or "(无)")
        logger.info("视觉查询 %s", self.dispatcher.vision_url)
        self._motion_thread = threading.Thread(target=self._motion_loop, name="motion-poll", daemon=True)
        self._vision_thread = threading.Thread(target=self._vision_loop, name="vision-poll", daemon=True)
        self._reconnect_thread = threading.Thread(target=self._reconnect_loop, name="motion-reconnect", daemon=True)
        self._watchdog_thread = threading.Thread(target=self._watchdog_loop, name='motion-health', daemon=True)
        self._motion_thread.start()
        self._vision_thread.start()
        self._reconnect_thread.start()
        self._watchdog_thread.start()

    def stop(self) -> None:
        self._running = False
        self._wake.set()
        with self._lock:
            if self.engine:
                self.engine.connection_valid = False
        self._set_online(False)
        timeout = max(2.0, self.dispatcher.vision_timeout + 0.5)
        for thread in (self._vision_thread, self._motion_thread, self._reconnect_thread, self._watchdog_thread):
            if thread is not None and thread is not threading.current_thread():
                thread.join(timeout=timeout)
        with self._io_lock:
            with self._lock:
                engine = self.engine
                self.engine = None
            self.dispatcher.bind_engine(None)
            if engine is not None:
                try:
                    engine.close()
                except Exception:
                    logger.exception("停止时关闭运动卡失败")

    def execute(self, command: Any) -> None:
        point = command.device_point
        if point not in self.physical_points:
            self._fail(point, command.message_id, "控制模块不处理虚拟点")
            return
        if point not in self.dispatcher.points:
            self._fail(point, command.message_id, "设备点未配置类型")
            return
        with self._lock:
            rejected = self._offline_rejections.get((point, str(command.message_id)))
        if rejected is not None:
            self._relay_event(rejected)
            return
        if self.dispatcher.points[point]['kind'] != 'vision':
            self._watchdog_once()
            with self._lock:
                engine = self.engine
                online = bool(self._online and engine and engine.opened and engine.connection_valid
                              and not self._closing and not self._wake.is_set())
            if not online:
                self._fail(point, command.message_id, 'Motion card is offline', 'CARD_OFFLINE')
                return
            self.dispatcher.execute(command, expected_engine=engine)
        else:
            self.dispatcher.execute(command)

    def _poll_motion(self) -> None:
        with self._io_lock:
            self._poll_motion_locked()

    def _poll_motion_locked(self) -> None:
        with self._lock:
            engine = self.engine
        if engine is None or not engine.opened:
            return
        self._watchdog_once()
        if not engine.connection_valid:
            self._fail_card(OSError('Motion card health expired'))
            return
        with self._lock:
            if self._health_engine is not engine:
                self._health_engine = engine
                self._last_poll_ms = self._last_progress_ms = self.clock.mono_ms()
                self._board_clock = None
                self._next_probe_ms = 0
        try:
            with self._lock:
                generation = self._io_failure_generation
            now = self.clock.mono_ms()
            if not self.clock_health:
                # 不做时钟判据：pump() 里的 MC_GetAllSysStatusSX / MC_GetDiRaw 是真往返，
                # 失败会被 checked 包装抛 OSError，进度仍靠它体现。
                self._last_progress_ms = self.clock.mono_ms()
            elif now >= self._next_probe_ms:
                board_clock = self.dispatcher.probe_card_clock()
                if not math.isfinite(board_clock):
                    raise OSError('Invalid motion card clock')
                if self._board_clock is not None and board_clock < self._board_clock:
                    engine.connection_valid = False
                    raise OSError('Motion card clock reset; rebuilding session')
                self._watchdog_once()
                if not engine.connection_valid:
                    raise OSError('Motion card health expired during clock read')
                if self._board_clock is None or board_clock > self._board_clock:
                    self._last_progress_ms = self.clock.mono_ms()
                self._board_clock = board_clock
                self._next_probe_ms = self.clock.mono_ms() + self.health_probe_ms
            self.dispatcher.pump()
            self._watchdog_once()
            if not engine.connection_valid:
                raise OSError('Motion card health expired during poll')
        except OSError as error:
            self._set_online(False)
            self._next_probe_ms = 0
            # First error removes online status; tolerance only delays teardown.
            self._poll_failures += 1
            if engine.connection_valid and self._poll_failures < self.poll_failure_tolerance:
                logger.warning("运动卡轮询失败（连续第 %d 次），下一周期重试: %s", self._poll_failures, error)
                return
            self._fail_card(error)
        except Exception as error:
            self._fail_card(error)
        else:
            self._poll_failures = 0
            with self._lock:
                self._last_poll_ms = self.clock.mono_ms()
            self._set_online(True, expected_generation=generation)

    def _poll_vision(self) -> None:
        self.dispatcher.poll_vision()

    def _motion_loop(self) -> None:
        period = self.motion.loop_period_ms / 1000.0
        while self._running:
            self._poll_motion()
            if self._wake.wait(period):
                break

    def _vision_loop(self) -> None:
        while self._running:
            try:
                self._poll_vision()
            except Exception:
                logger.exception("视觉轮询失败")
            if self._wake.wait(0.05):
                break

    def _try_connect(self, announce_fail: bool = False) -> bool:
        # SDK operations never hold the lock used by heartbeat_points.
        with self._io_lock:
            if self._closing or self._wake.is_set():
                return False
            if self.engine is not None and self.engine.opened:
                return True
            engine: MotionEngine | None = None
            try:
                card = RealCard(self.dll_path, encoder_feedback=self.encoder_feedback)
                engine = MotionEngine(card, self.clock, self.motion)
                # 视觉 HTTP 只走分发器的视觉线程。
                engine.http_get = lambda: None
                if not engine.initialize(self.local_ip, self.local_port, self.card_ip, self.card_port):
                    engine.close()
                    if announce_fail:
                        logger.warning(
                            "Open Card Fail (%s:%s)。北向继续运行，物理点 offline，后台每 %.0fs 重试",
                            self.card_ip,
                            self.card_port,
                            self.reconnect_sec,
                        )
                    return False
                if self._wake.is_set():
                    engine.close()
                    return False
                self.engine = engine
                self._poll_failures = 0
                self.dispatcher.bind_engine(engine)
                self.dispatcher.card_connected()
                self._poll_motion()
                if not self._online:
                    if self.engine is not None:
                        self._fail_card(OSError('Initial health probe failed'))
                    return False
                logger.info("运动台已连接 %s:%s", self.card_ip, self.card_port)
                return True
            except Exception as error:
                self._set_online(False)
                if announce_fail:
                    logger.warning("南向未就绪: %s；北向继续运行，后台重试", error)
                else:
                    logger.debug("南向重连失败: %s", error)
                if engine is not None:
                    try:
                        engine.close()
                    except Exception:
                        logger.debug("关闭失败的运动卡时出错", exc_info=True)
                return False

    def _reconnect_loop(self) -> None:
        first = True
        while self._running:
            with self._lock:
                opened = self.engine is not None and self.engine.opened and not self._closing
            if not opened and self._try_connect(announce_fail=first):
                logger.info("运动台重连成功 %s:%s", self.card_ip, self.card_port)
            first = False
            if self._wake.wait(self.reconnect_sec):
                break

    def _fail_card(self, error: BaseException) -> None:
        with self._io_lock:
            self._fail_card_locked(error)

    def _fail_card_locked(self, error: BaseException) -> None:
        with self._lock:
            engine = self.engine
            if engine is None or self._closing:
                return
            self._closing = True
            engine.connection_valid = False
        self._set_online(False)
        logger.warning("运动卡异常，停止未完成动作并等待重连: %s", error)
        try:
            self.dispatcher.card_failed(str(error))
        except Exception:
            logger.exception("清理未完成指令失败")
        try:
            engine.close()
        except Exception:
            logger.exception("关闭运动卡失败")
        finally:
            with self._lock:
                if self.engine is engine:
                    self.engine = None
                self._closing = False

    def _relay_event(self, event: AdapterEvent) -> None:
        handler = self._event_handler
        if handler is not None:
            handler(event)

    def _relay_data(self, data: DeviceData) -> None:
        handler = self._data_handler
        if handler is not None:
            handler(data)

    def _fail(self, point: str, message_id: str, message: str, error_code: str | None = None) -> None:
        event = AdapterEvent(point, 'COMMAND_FAILED', str(message_id),
                             {'message': message, **({'errorCode': error_code} if error_code else {})}, self.clock.stamp_ms())
        if error_code == 'CARD_OFFLINE':
            with self._lock:
                key = (point, str(message_id))
                event = self._offline_rejections.setdefault(key, event)
                self._offline_rejections.move_to_end(key)
                while len(self._offline_rejections) > 1024:
                    self._offline_rejections.popitem(last=False)
        self._relay_event(event)
