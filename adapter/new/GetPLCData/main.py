"""
GetPLCData Adapter 启动入口。
读取 adapterSetup.ini 和 runtime.ini，组装 ControlImpl 与 SimulationImpl，启动 AdapterCore。
"""

import sys
from pathlib import Path

from core import main

if __name__ == "__main__":
    sys.exit(main())
