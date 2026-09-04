import subprocess
import sys
from pathlib import Path

BASE = Path(__file__).resolve().parent

def main():
    print("=== SmartLab Chemical Reactor Adapter GUI ===")
    print("Starting Reactor Adapter GUI (connecting to reactor device at 127.0.0.1:9997)...")
    try:
        script = BASE / "adapter" / "反应釜" / "core.py"
        if not script.exists():
            script = BASE / "adapter" / "reactor" / "core.py"
        subprocess.run(
            [sys.executable, str(script)],
            check=True
        )
    except KeyboardInterrupt:
        print("\nShutting down reactor adapter...")

if __name__ == "__main__":
    main()
