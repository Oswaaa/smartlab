"""Adapter 启动器：固定 cwd / sys.path，日志重定向后跑指定 main.py。

用法: python run_adapter.py <adapter 目录名>
"""

import os
import runpy
import sys

BASE = r"D:\smartlab2"
ADAPTER_ROOT = os.path.join(BASE, "adapter", "new")


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: run_adapter.py <GetPLCData|MotionStageController>")
        return 2
    name = sys.argv[1]
    adapter = os.path.join(ADAPTER_ROOT, name)
    if not os.path.isdir(adapter):
        print("adapter 目录不存在:", adapter)
        return 2

    log_dir = os.path.join(BASE, ".run", "logs")
    os.makedirs(log_dir, exist_ok=True)
    log_path = os.path.join(log_dir, name + ".log")

    os.chdir(adapter)
    sys.path.insert(0, adapter)

    fd = os.open(log_path, os.O_WRONLY | os.O_CREAT | os.O_APPEND | os.O_BINARY)
    os.dup2(fd, 1)
    os.dup2(fd, 2)
    os.close(fd)
    sys.stdout = os.fdopen(1, "w", encoding="utf-8", buffering=1)
    sys.stderr = sys.stdout

    runpy.run_path(os.path.join(adapter, "main.py"), run_name="__main__")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
