"""假网关启动器：固定 cwd / sys.path，日志重定向后跑 mock_gateway.py。

任务计划里只能用纯 ASCII 路径（Excel 式的编码坑），所以这个文件放在 .run 下。

用法: python run_mock_gateway.py [mock_gateway.py 的参数...]
"""

import os
import runpy
import sys

BASE = r"D:\smartlab2"
GATEWAY = os.path.join(BASE, "adapter", "mockGateway")


def main() -> int:
    if not os.path.isdir(GATEWAY):
        print("mockGateway 目录不存在:", GATEWAY)
        return 2

    log_dir = os.path.join(BASE, ".run", "logs")
    os.makedirs(log_dir, exist_ok=True)
    log_path = os.path.join(log_dir, "mock_gateway.log")

    os.chdir(GATEWAY)
    sys.path.insert(0, GATEWAY)

    # 每次启动重写日志，免得几 MB 的旧内容混进来
    fd = os.open(log_path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC | os.O_BINARY)
    os.dup2(fd, 1)
    os.dup2(fd, 2)
    os.close(fd)
    sys.stdout = os.fdopen(1, "w", encoding="utf-8", buffering=1)
    sys.stderr = sys.stdout

    sys.argv = ["mock_gateway.py"] + sys.argv[1:]
    runpy.run_path(os.path.join(GATEWAY, "mock_gateway.py"), run_name="__main__")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
