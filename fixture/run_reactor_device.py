import subprocess
import sys
from pathlib import Path

BASE = Path(__file__).resolve().parent

def main():
    print("=== SmartLab Chemical Reactor Device GUI ===")
    print("Starting simulated visual reactor device on 127.0.0.1:9997...")
    try:
        script = BASE / "device" / "反应釜" / "reactor.py"
        if not script.exists():
            script = BASE / "device" / "reactor.py"
        subprocess.run(
            [sys.executable, str(script)],
            check=True
        )
    except KeyboardInterrupt:
        print("\nShutting down reactor device...")

if __name__ == "__main__":
    main()
