"""任务计划专用的后端启动器。

为什么单独一份：run_backend.py 依赖调用方已有的环境（模型 key、端口），
而任务计划不继承当前 shell 的环境，所以这里自己去注册表取 key，并显式指定端口。
进程用 execv 顶掉自身，PID 不变。
"""

import os
import winreg

BASE = r"D:\smartlab2"
BACKEND = os.path.join(BASE, "Backend")
JAR = os.path.join(BACKEND, "target", "smartlab-backend-2.0.0.jar")
LOG = os.path.join(BASE, ".run", "logs", "backend.log")
JAVA = r"D:\smartlab\tools\installed\jdk-21.0.12.1+1\bin\java.exe"
JAVA_HOME = r"D:\smartlab\tools\installed\jdk-21.0.12.1+1"


def user_env(name):
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, "Environment") as key:
            value, _ = winreg.QueryValueEx(key, name)
            return value
    except OSError:
        return None


api_key = user_env("SMARTLAB_AGENT_API_KEY")
if api_key:
    os.environ["SMARTLAB_AGENT_API_KEY"] = api_key
os.environ["JAVA_HOME"] = JAVA_HOME
# 这两个会把 Spring 的 server.port 顶掉，必须清掉
for name in ("SERVER__PORT", "SERVER__HOST"):
    os.environ.pop(name, None)

os.makedirs(os.path.dirname(LOG), exist_ok=True)
fd = os.open(LOG, os.O_WRONLY | os.O_CREAT | os.O_APPEND | os.O_BINARY)
os.dup2(fd, 1)
os.dup2(fd, 2)
os.close(fd)

os.chdir(BACKEND)
os.execv(JAVA, [JAVA, "-Dfile.encoding=UTF-8", "-jar", JAR, "--server.port=8080"])
