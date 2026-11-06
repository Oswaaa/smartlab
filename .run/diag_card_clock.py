"""诊断：单独验证 MC_Open 与 MC_GetSts 返回值、板卡时钟是否前进。"""

import ctypes
import os
import time
from ctypes import POINTER, byref, c_char_p, c_int, c_long, c_short, c_ulong, c_ushort, c_void_p

DLL = r"D:\smartlab2\adapter\new\MotionStageController\MultiCard.dll"
LOCAL_IP = b"192.168.0.91"
CARD_IP = b"192.168.0.199"

os.add_dll_directory(os.path.dirname(DLL))
lib = ctypes.WinDLL(DLL)

buf = (ctypes.c_uint64 * (8 * 1024 * 1024 // 8))()
this = ctypes.addressof(buf)

ctor = getattr(lib, "??0MultiCard@@QEAA@XZ")
ctor.restype = None
ctor.argtypes = [c_void_p]
ctor(this)

openf = getattr(lib, "?MC_Open@MultiCard@@QEAAHFPEADG0G@Z")
openf.restype = c_int
openf.argtypes = [c_void_p, c_short, c_char_p, c_ushort, c_char_p, c_ushort]
rc = openf(this, 1, LOCAL_IP, 60000, CARD_IP, 60000)
print("MC_Open rc =", rc)
if rc != 0:
    raise SystemExit(1)

getsts = getattr(lib, "?MC_GetSts@MultiCard@@QEAAHFPEAJFPEAK@Z")
getsts.restype = c_int
getsts.argtypes = [c_void_p, c_short, POINTER(c_long), c_short, POINTER(c_ulong)]

print("axis clock  rc  status")
for i in range(6):
    val = c_ulong()
    status = (c_long * 3)()
    r = getsts(this, 1, status, 3, byref(val))
    print(f"  {i}  clock={val.value:<12} rc={r}  status={list(status)}")
    time.sleep(1)

closef = getattr(lib, "?MC_Close@MultiCard@@QEAAHXZ")
closef.restype = None
closef.argtypes = [c_void_p]
closef(this)
print("closed")
