import subprocess
import sys
import time
from pathlib import Path

BASE = Path(__file__).resolve().parent

def main():
    print("=== SmartLab Device Fixture ===")
    print("Starting simulated device with GUI...")
    try:
        subprocess.run(
            [sys.executable, str(BASE / "device" / "temp_sensor.py")],
            check=True
        )
    except KeyboardInterrupt:
        print("\nShutting down device...")

if __name__ == "__main__":
    main()
