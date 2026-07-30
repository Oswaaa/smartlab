"""Start the headless SmartLab PLC Adapter."""

from __future__ import annotations

import logging
import signal
import threading
from pathlib import Path

from adapterRuntime import AdapterRuntime, AdapterSetup, RuntimeConfig
from core import Core


BASE_DIR = Path(__file__).resolve().parent


def _configure_logging(level: str) -> None:
    logging.basicConfig(
        level=getattr(logging, level),
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )


def main() -> int:
    runtime: AdapterRuntime | None = None
    stop_event = threading.Event()
    try:
        config = RuntimeConfig.load(BASE_DIR / "runtime.ini")
        setup = AdapterSetup.load(BASE_DIR / "adapterSetup.ini")
        _configure_logging(config.log_level)
        logger = logging.getLogger("smartlab.adapter")

        core = Core(
            config.plc,
            setup.device_points[0],
            config.command_timeout_sec,
        )
        runtime = AdapterRuntime(config, setup, core)

        def request_stop(signum=None, _frame=None) -> None:
            if not stop_event.is_set():
                if signum is not None:
                    logger.info("收到退出信号 %s，正在停止 Adapter", signum)
                stop_event.set()

        signal.signal(signal.SIGINT, request_stop)
        if hasattr(signal, "SIGTERM"):
            signal.signal(signal.SIGTERM, request_stop)

        runtime.start()
        logger.info(
            "Adapter %s 已启动（设备点: %s），按 Ctrl+C 停止",
            setup.adapter_name,
            setup.device_points[0],
        )
        while not stop_event.wait(1):
            pass
        return 0
    except KeyboardInterrupt:
        return 0
    except Exception:
        logging.getLogger("smartlab.adapter").exception("Adapter 启动或运行失败")
        return 1
    finally:
        if runtime is not None:
            runtime.stop()


if __name__ == "__main__":
    raise SystemExit(main())
