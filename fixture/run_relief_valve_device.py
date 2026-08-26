import subprocess
import sys
import time
from pathlib import Path

BASE = Path(__file__).resolve().parent

def main():
    print("=== SmartLab Pressure Relief Valve Device Fixture ===")
    print("Starting simulated relief valve device with GUI (0-2.5 MPa)...")
    try:
        subprocess.run(
            [sys.executable, str(BASE / "device" / "relief_valve.py")],
            check=True
        )
    except KeyboardInterrupt:
        print("\nShutting down relief valve device...")

if __name__ == "__main__":
    main()
