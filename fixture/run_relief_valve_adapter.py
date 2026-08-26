import subprocess
import sys
import time
from pathlib import Path

BASE = Path(__file__).resolve().parent

def main():
    print("=== SmartLab Pressure Relief Valve Adapter Fixture ===")
    print("Starting relief valve adapter...")
    print("  Make sure the device (run_relief_valve_device.py) is already running.")
    print("  Adapter MQTT broker: see adapter_relief_valve/runtime.ini")
    try:
        subprocess.run(
            [sys.executable, str(BASE / "adapter_relief_valve" / "main.py")],
            check=True
        )
    except KeyboardInterrupt:
        print("\nShutting down relief valve adapter...")

if __name__ == "__main__":
    main()
