"""物理设备点。十一路指令按各自的设备点进入同一个分发器。

运动轮询、点动看门狗和第九路等待在运动线程；视觉 HTTP 在另一条线程。
南向连不上板卡时不阻断北向：进程继续注册、心跳和租约；
非视觉点 heartbeat online=false；真机指令回 COMMAND_FAILED；后台定时重连。
"""

from __future__ import annotations

import logging
import threading
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
        self._wake = threading.Event()
        self._motion_thread: threading.Thread | None = None
        self._vision_thread: threading.Thread | None = None
        self._reconnect_thread: threading.Thread | None = None

    def set_data_handler(self, handler: Callable) -> None:
        self._data_handler = handler

    def set_event_handler(self, handler: Callable) -> None:
        self._event_handler = handler

    def heartbeat_points(self) -> list[dict[str, Any]]:
        with self._lock:
            card_online = bool(self.engine and self.engine.opened)
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
        self._try_connect(announce_fail=True)
        self._motion_thread = threading.Thread(target=self._motion_loop, name="motion-poll", daemon=True)
        self._vision_thread = threading.Thread(target=self._vision_loop, name="vision-poll", daemon=True)
        self._reconnect_thread = threading.Thread(target=self._reconnect_loop, name="motion-reconnect", daemon=True)
        self._motion_thread.start()
        self._vision_thread.start()
        self._reconnect_thread.start()

    def stop(self) -> None:
        self._running = False
        self._wake.set()
        timeout = max(2.0, self.dispatcher.vision_timeout + 0.5)
        for thread in (self._vision_thread, self._motion_thread, self._reconnect_thread):
            if thread is not None and thread is not threading.current_thread():
                thread.join(timeout=timeout)
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
        self.dispatcher.execute(command)

    def _poll_motion(self) -> None:
        with self._lock:
            engine = self.engine
        if engine is None or not engine.opened:
            return
        try:
            self.dispatcher.pump()
        except OSError as error:
            # 以太网偶发一次无响应（MC_COM_ERR_TIME_OUT=-7）只跳过本周期，连续失败才判掉线。
            self._poll_failures += 1
            if self._poll_failures < self.poll_failure_tolerance:
                logger.warning("运动卡轮询失败（连续第 %d 次），下一周期重试: %s", self._poll_failures, error)
                return
            self._fail_card(error)
        except Exception as error:
            self._fail_card(error)
        else:
            self._poll_failures = 0

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
        with self._lock:
            if self._closing:
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
                self.engine = engine
                self._poll_failures = 0
                self.dispatcher.bind_engine(engine)
                self.dispatcher.card_connected()
                logger.info("运动台已连接 %s:%s", self.card_ip, self.card_port)
                return True
            except Exception as error:
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
        while self._running:
            if self._wake.wait(self.reconnect_sec):
                break
            if not self._running:
                break
            with self._lock:
                opened = self.engine is not None and self.engine.opened and not self._closing
            if opened:
                continue
            if self._try_connect(announce_fail=False):
                logger.info("运动台重连成功 %s:%s", self.card_ip, self.card_port)

    def _fail_card(self, error: BaseException) -> None:
        with self._lock:
            engine = self.engine
            if engine is None or self._closing:
                return
            self._closing = True
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

    def _fail(self, point: str, message_id: str, message: str) -> None:
        self._relay_event(
            AdapterEvent(point, "COMMAND_FAILED", str(message_id), {"message": message}, self.clock.stamp_ms())
        )
