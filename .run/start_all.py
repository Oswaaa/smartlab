"""注册并启动 SmartLab 全部常驻任务（后端 / 前端 / 两个 adapter）。

用 Task Scheduler COM 注册 0 触发器任务后立即 Run，进程存活于当前登录会话，
不会被 WorkBuddy 的 Bash 沙箱回收。参数全 ASCII，避免 GBK/UTF-8 乱码。
"""

import os
import sys

import pythoncom
import win32api
import win32com.client

PYTHON = r"C:\Users\22654\AppData\Local\Programs\Python\Python312\python.exe"
RUN_DIR = r"D:\smartlab2\.run"

# MQTT broker 是 EMQX 的 Windows 服务（进程 erl.exe），随服务自启动，
# 不在这里托管；只要 1883 在监听就不用管。
TASKS = {
    "SmartLabBackend": (PYTHON, r'"D:\smartlab2\.run\run_backend.py"', RUN_DIR),
    "SmartLabFrontend": (PYTHON, r'"D:\smartlab2\.run\run_frontend.py"', RUN_DIR),
    "SmartLabAdapterGetPLCData": (
        PYTHON,
        r'"D:\smartlab2\.run\run_adapter.py" GetPLCData',
        RUN_DIR,
    ),
    "SmartLabAdapterMotionStage": (
        PYTHON,
        r'"D:\smartlab2\.run\run_adapter.py" MotionStageController',
        RUN_DIR,
    ),
}


def register(svc, name: str, exe: str, args: str, cwd: str, user: str) -> None:
    folder = svc.GetFolder("\\")
    td = svc.NewTask(0)  # TASK_CREATE
    td.RegistrationInfo.Description = "SmartLab " + name
    act = td.Actions.Create(0)  # TASK_ACTION_EXEC
    act.Path = exe
    act.Arguments = args
    act.WorkingDirectory = cwd
    td.Settings.ExecutionTimeLimit = "PT0S"  # 常驻，不限时长
    td.Settings.DisallowStartIfOnBatteries = False
    td.Settings.StopIfGoingOnBatteries = False
    td.Settings.MultipleInstances = 3  # TASK_INSTANCES_STOP_EXISTING
    folder.RegisterTaskDefinition(
        name, td, 6, user, None, 3  # CREATE_OR_UPDATE, TASK_LOGON_INTERACTIVE_TOKEN
    )


def main() -> int:
    pythoncom.CoInitialize()
    svc = win32com.client.Dispatch("Schedule.Service")
    svc.Connect()
    user = win32api.GetUserNameEx(2)  # DOMAIN\user
    folder = svc.GetFolder("\\")

    only = sys.argv[1:] or list(TASKS)
    for name in only:
        exe, args, cwd = TASKS[name]
        register(svc, name, exe, args, cwd, user)
        print("registered:", name)

    for name in only:
        folder.GetTask(name).Run(None)
        print("started   :", name)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
