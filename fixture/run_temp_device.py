"""
Launcher for Temperature Sensor Device GUI.
"""

import subprocess
import sys
from pathlib import Path

BASE = Path(__file__).resolve().parent

def main():
    print("=== SmartLab Temperature Simulator Device GUI ===")
    print("Starting simulated visual temperature sensor device on 127.0.0.1:9999...")
    try:
        script = BASE / "device" / "温度模拟器" / "temp_sensor.py"
        subprocess.run([sys.executable, str(script)], check=True)
    except KeyboardInterrupt:
        print("\nShutting down temperature simulator device...")

if __name__ == "__main__":
    main()
