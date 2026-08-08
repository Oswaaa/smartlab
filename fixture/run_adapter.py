import subprocess
import sys
import time
from pathlib import Path

BASE = Path(__file__).resolve().parent

def main():
    print("=== SmartLab Adapter Fixture ===")
    print("Starting adapter...")
    print("  Make sure the device (run_device.py) is already running.")
    print("  Adapter MQTT broker: see adapter/runtime.ini")
    try:
        subprocess.run(
            [sys.executable, str(BASE / "adapter" / "main.py")],
            check=True
        )
    except KeyboardInterrupt:
        print("\nShutting down adapter...")

if __name__ == "__main__":
    main()
