"""
Launcher for Temperature Sensor Adapter.
"""

import subprocess
import sys
from pathlib import Path

BASE = Path(__file__).resolve().parent

def main():
    print("=== SmartLab Temperature Simulator Adapter ===")
    print("Starting Temperature Simulator Adapter (connecting to device at 127.0.0.1:9999)...")
    try:
        script = BASE / "adapter" / "温度模拟器" / "main.py"
        subprocess.run([sys.executable, str(script)], check=True)
    except KeyboardInterrupt:
        print("\nShutting down temperature adapter...")

if __name__ == "__main__":
    main()
