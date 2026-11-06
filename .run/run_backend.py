"""后端启动器：OS 级重定向日志后 execv 到 java，进程 PID 不变。"""

import os

BASE = r"D:\smartlab2"
JAVA = r"D:\smartlab\tools\installed\jdk-21.0.12.1+1\bin\java.exe"
BACKEND = os.path.join(BASE, "Backend")
JAR = os.path.join(BACKEND, "target", "smartlab-backend-2.0.0.jar")
LOG = os.path.join(BASE, ".run", "logs", "backend.log")

os.makedirs(os.path.dirname(LOG), exist_ok=True)
fd = os.open(LOG, os.O_WRONLY | os.O_CREAT | os.O_APPEND | os.O_BINARY)
os.dup2(fd, 1)
os.dup2(fd, 2)
os.close(fd)

os.chdir(BACKEND)
os.execv(JAVA, [JAVA, "-Dfile.encoding=UTF-8", "-jar", JAR])
