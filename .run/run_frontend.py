"""前端启动器：execv 到 node 跑 vite dev，日志重定向到文件。"""

import os

BASE = r"D:\smartlab2"
# execv 不做引号处理，路径含空格会被截断，必须选无空格路径
NODE = r"C:\Users\22654\.workbuddy\binaries\node\versions\22.22.2-6\node.exe"
FRONTEND = os.path.join(BASE, "Frontend")
VITE = os.path.join(FRONTEND, "node_modules", "vite", "bin", "vite.js")
LOG = os.path.join(BASE, ".run", "logs", "frontend.log")

os.makedirs(os.path.dirname(LOG), exist_ok=True)
fd = os.open(LOG, os.O_WRONLY | os.O_CREAT | os.O_APPEND | os.O_BINARY)
os.dup2(fd, 1)
os.dup2(fd, 2)
os.close(fd)

os.chdir(FRONTEND)
os.execv(NODE, [NODE, VITE, "--host", "127.0.0.1", "--port", "5173"])
