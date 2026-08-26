"""Fixture relief valve adapter entry point. Run with: python main.py"""

import logging
import signal
import threading
from pathlib import Path

from configparser import ConfigParser
from core import Core
from adapter_runtime import RuntimeConfig, AdapterSetup, AdapterRuntime

BASE = Path(__file__).resolve().parent


def main():
    config = RuntimeConfig.load(BASE / "runtime.ini")
    setup = AdapterSetup.load(BASE / "adapterSetup.ini")

    logging.basicConfig(
        level=getattr(logging, config.client_id, logging.INFO) or logging.INFO,
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    )

    # Connect to device
    device_host = "127.0.0.1"
    device_port = 9998
    try:
        dev_cfg = ConfigParser()
        dev_cfg.read(BASE / "runtime.ini", encoding="utf-8")
        device_host = dev_cfg.get("device", "host", fallback="127.0.0.1")
        device_port = dev_cfg.getint("device", "port", fallback=9998)
    except Exception:
        pass

    core = Core(device_host=device_host, device_port=device_port)
    runtime = AdapterRuntime(config, setup, core)

    stop_event = threading.Event()

    def stop(signum=None, _frame=None):
        if not stop_event.is_set():
            stop_event.set()

    signal.signal(signal.SIGINT, stop)
    if hasattr(signal, "SIGTERM"):
        signal.signal(signal.SIGTERM, stop)

    try:
        runtime.start()
        logging.getLogger("fixture.relief_valve").info(
            "Adapter %s started (device %s:%d). Ctrl+C to stop.",
            setup.adapter_name, device_host, device_port,
        )
        while not stop_event.wait(1):
            pass
    except KeyboardInterrupt:
        pass
    finally:
        runtime.stop()
        logging.getLogger("fixture.relief_valve").info("Adapter stopped.")


if __name__ == "__main__":
    main()
