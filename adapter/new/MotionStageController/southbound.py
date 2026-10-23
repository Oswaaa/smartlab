"""旧 MotionController 的南向移植。

运动调用、回零分支、点动看门狗和视觉检测与
adapter/MotionStageController/.../MotionController.cpp 保持一致。
真卡走 MultiCard.dll 的 C++ 成员函数；仿真走同一套状态机下的假卡。
"""

from __future__ import annotations

import math
import ctypes
from ctypes import (
    Structure,
    byref,
    c_char_p,
    c_float,
    c_int,
    c_long,
    c_short,
    c_uint64,
    c_ulong,
    c_ushort,
    c_void_p,
    c_double,
)
import logging
import os
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any, Callable

logger = logging.getLogger("smartlab.adapter.motionstage.southbound")

MC_LIMIT_POSITIVE = 0
MC_LIMIT_NEGATIVE = 1
AXIS_STATUS_ENABLE = 0x00000200
AXIS_STATUS_RUNNING = 0x00000400
AXIS_STATUS_ARRIVE = 0x00000800
_AXIS_STATUS_TEXT = (
    (0x00000001, "急停"),
    (0x00000002, "报警"),
    (0x00000004, "正软限位"),
    (0x00000008, "负软限位"),
    (0x00000020, "正硬限位"),
    (0x00000040, "负硬限位"),
    (0x00000400, "运动中"),
    (0x00000800, "到位"),
)


def describe_axis_status(word: int) -> str:
    value = int(word) & 0xFFFFFFFF
    labels = [label for mask, label in _AXIS_STATUS_TEXT if value & mask]
    return "、".join(labels) if labels else "静止"

FIELD_KEYS = tuple(f"axis{axis}_{field}" for axis in (1, 2, 3)
                   for field in ("pos", "status"))

def _cfg_float(data: dict[str, Any], key: str, default: float) -> float:
    raw = data.get(key, "")
    if raw is None or str(raw).strip() == "":
        return default
    return float(raw)


def _cfg_int(data: dict[str, Any], key: str, default: int) -> int:
    return int(_cfg_float(data, key, float(default)))


class MotionParams:
    """旧 MotionController 里的加减速度、速度和回零位置。缺省与源码一致。"""

    def __init__(self, data: dict[str, Any] | None = None) -> None:
        src = data or {}
        self.stop_dec_smooth = _cfg_float(src, "stopDecSmooth", 0.5)
        self.stop_dec_abrupt = _cfg_float(src, "stopDecAbrupt", 5.0)
        self.move_acc = (
            _cfg_float(src, "moveAcc1", 0.05),
            _cfg_float(src, "moveAcc2", 0.07),
            _cfg_float(src, "moveAcc3", 0.05),
        )
        self.move_dec = (
            _cfg_float(src, "moveDec1", 0.05),
            _cfg_float(src, "moveDec2", 0.07),
            _cfg_float(src, "moveDec3", 0.05),
        )
        self.move_smooth_time = _cfg_int(src, "moveSmoothTime", 50)
        self.move_vel_start = _cfg_float(src, "moveVelStart", 0.0)
        self.move_vel = (
            _cfg_float(src, "moveVel1", 80.0),
            _cfg_float(src, "moveVel2", 25.0),
            _cfg_float(src, "moveVel3", 3.0),
        )
        self.move_arrive_band = _cfg_int(src, "moveArriveBand", 10)
        self.home_timeout_ms = _cfg_int(src, "homeTimeoutMs", 120000)
        self.stop_timeout_ms = _cfg_int(src, "stopTimeoutMs", 10000)
        self.jog_acc = _cfg_float(src, "jogAcc", 0.5)
        self.jog_dec = _cfg_float(src, "jogDec", 0.5)
        self.jog_smooth = _cfg_float(src, "jogSmooth", 0.1)
        self.jog_speed = (
            _cfg_float(src, "jogSpeedX", 1.0),
            _cfg_float(src, "jogSpeedY", 1.0),
            _cfg_float(src, "jogSpeedZ", 10.0),
        )
        self.jog_watchdog_ms = _cfg_int(src, "jogWatchdogMs", 200)
        self.home_jog_acc = _cfg_float(src, "homeJogAcc", 0.1)
        self.home_jog_dec = _cfg_float(src, "homeJogDec", 0.1)
        self.home_jog_smooth = _cfg_float(src, "homeJogSmooth", 0.0)
        self.home_seek_vel = (
            _cfg_float(src, "homeSeekVel1", 50.0),
            _cfg_float(src, "homeSeekVel2", 25.0),
            _cfg_float(src, "homeSeekVel3", 15.0),
        )
        self.home_fast_pos = _cfg_int(src, "homeFastPos", 50000)
        self.home_fast_vel = _cfg_float(src, "homeFastVel", 1500.0)
        self.home_fast_acc = _cfg_float(src, "homeFastAcc", 10.0)
        self.home_fast_dec = _cfg_float(src, "homeFastDec", 1.0)
        self.home_fast_smooth = _cfg_int(src, "homeFastSmooth", 0)
        self.home_return_vel = _cfg_float(src, "homeReturnVel", 25.0)
        self.home_axis2_pos = _cfg_int(src, "homeAxis2Pos", 5000)
        self.home_axis3_pos = _cfg_int(src, "homeAxis3Pos", 1000)
        self.home_place_vel2 = _cfg_float(src, "homePlaceVel2", 50.0)
        self.home_place_vel3 = _cfg_float(src, "homePlaceVel3", 50.0)
        self.home_place_acc = _cfg_float(src, "homePlaceAcc", 0.1)
        self.home_place_dec = _cfg_float(src, "homePlaceDec", 0.1)
        self.home_axis2_smooth = _cfg_int(src, "homeAxis2Smooth", 150)
        self.home_axis3_smooth = _cfg_int(src, "homeAxis3Smooth", 0)
        self.home_creep_vel = _cfg_float(src, "homeCreepVel", 1.0)
        self.soft_wide = (
            _cfg_int(src, "softLimitWide1", -2147483645),
            _cfg_int(src, "softLimitWide2", 2147483647),
        )
        self.soft_final = (
            (_cfg_int(src, "softLimit1A", -1000), _cfg_int(src, "softLimit1B", 1900000)),
            (_cfg_int(src, "softLimit2A", -100), _cfg_int(src, "softLimit2B", 430000)),
            (_cfg_int(src, "softLimit3A", -10), _cfg_int(src, "softLimit3B", 45000)),
        )
        self.ext_do_hold_bit = _cfg_int(src, "extDoHoldBit", 9)
        self.ext_do_hold_ms = _cfg_int(src, "extDoHoldMs", 2000)
        self.loop_period_ms = max(1, _cfg_int(src, "loopPeriodMs", 100))
        self.telemetry_every = max(1, _cfg_int(src, "telemetryEveryLoops", 10))
        self.neg_limit_pos = (
            _cfg_float(src, "negLimitPos1", 0.0),
            _cfg_float(src, "negLimitPos2", 0.0),
            _cfg_float(src, "negLimitPos3", 0.0),
        )

    def move_acc_dec(self, axis: int) -> tuple[float, float]:
        if axis == 1:
            return self.move_acc[0], self.move_dec[0]
        if axis == 3:
            return self.move_acc[2], self.move_dec[2]
        return self.move_acc[1], self.move_dec[1]

    def move_speed(self, axis: int) -> float:
        if axis == 1:
            return self.move_vel[0]
        if axis == 2:
            return self.move_vel[1]
        return self.move_vel[2]


DEFAULT_DLL = (
    Path(__file__).resolve().parents[2]
    / "MotionStageController"
    / "MotionStageController"
    / "MotionStageController"
    / "MultiCard.dll"
)

# 与 MultiCard.h 在 pack(2) 下的 _AllSysStatusDataSX 一致。long 在 Win64 上是 4 字节。
class TAllSysStatusDataSX(Structure):
    _pack_ = 2
    _fields_ = [
        ("lAxisEncPos", c_long * 16),
        ("lAxisPrfPos", c_long * 16),
        ("lAxisStatus", c_ulong * 16),
        ("nADCValue", c_short * 2),
        ("lUserSegNum", c_long * 2),
        ("lRemainderSegNum", c_short * 2),
        ("nCrdRunStatus", c_short * 2),
        ("lCrdSpace", c_short * 2),
        ("dCrdVel", c_float * 2),
        ("lCrdPos", (c_long * 5) * 2),
        ("lLimitPosRaw", c_short),
        ("lLimitNegRaw", c_short),
        ("lAlarmRaw", c_short),
        ("lHomeRaw", c_short),
        ("lMPGEncPos", c_long),
        ("lMPG", c_int),
        ("lGpiRaw", c_long * 8),
        ("lGpoRaw", c_long * 8),
    ]


class TTrapPrm(Structure):
    _pack_ = 2
    _fields_ = [
        ("acc", c_double),
        ("dec", c_double),
        ("velStart", c_double),
        ("smoothTime", c_short),
    ]


class TJogPrm(Structure):
    _pack_ = 2
    _fields_ = [
        ("dAcc", c_double),
        ("dDec", c_double),
        ("dSmooth", c_double),
    ]


def _bind(dll: ctypes.WinDLL, export: str, restype: Any, argtypes: list[Any]) -> Any:
    fn = dll[export]
    fn.restype = restype
    fn.argtypes = argtypes
    return fn


class RealCard:
    """MultiCard C++ 对象。x64 成员函数的 this 从第一个参数传入。"""

    def __init__(self, dll_path: Path, encoder_feedback: bool = False) -> None:
        self.encoder_feedback = encoder_feedback
        if not dll_path.exists():
            raise FileNotFoundError(f"找不到 MultiCard.dll: {dll_path}")
        os.add_dll_directory(str(dll_path.parent))
        dll = ctypes.WinDLL(str(dll_path))
        self._buf = (c_uint64 * (8 * 1024 * 1024 // 8))()
        self._this = ctypes.addressof(self._buf)
        ctor = _bind(dll, "??0MultiCard@@QEAA@XZ", c_void_p, [c_void_p])
        self._dtor = _bind(dll, "??1MultiCard@@QEAA@XZ", None, [c_void_p])
        ctor(self._this)
        self._destroyed = False
        this = [c_void_p]
        self._MC_StartDebugLog = _bind(dll, "?MC_StartDebugLog@MultiCard@@QEAAHF@Z", c_int, this + [c_short])
        self._MC_Open = _bind(
            dll,
            "?MC_Open@MultiCard@@QEAAHFPEADG0G@Z",
            c_int,
            this + [c_short, c_char_p, c_ushort, c_char_p, c_ushort],
        )
        self._MC_Close = _bind(dll, "?MC_Close@MultiCard@@QEAAHXZ", c_int, this)
        self._MC_SetStopDec = _bind(dll, "?MC_SetStopDec@MultiCard@@QEAAHFNN@Z", c_int, this + [c_short, c_double, c_double])
        self._MC_LmtsOn = _bind(dll, "?MC_LmtsOn@MultiCard@@QEAAHFF@Z", c_int, this + [c_short, c_short])
        self._MC_LmtSns = _bind(dll, "?MC_LmtSns@MultiCard@@QEAAHG@Z", c_int, this + [c_ushort])
        self._MC_AxisOn = _bind(dll, "?MC_AxisOn@MultiCard@@QEAAHF@Z", c_int, this + [c_short])
        self._MC_PrfTrap = _bind(dll, "?MC_PrfTrap@MultiCard@@QEAAHF@Z", c_int, this + [c_short])
        self._MC_SetTrapPrm = _bind(
            dll, "?MC_SetTrapPrm@MultiCard@@QEAAHFPEAUTrapPrm@@@Z", c_int, this + [c_short, ctypes.POINTER(TTrapPrm)]
        )
        self._MC_SetPos = _bind(dll, "?MC_SetPos@MultiCard@@QEAAHFJ@Z", c_int, this + [c_short, c_long])
        self._MC_SetVel = _bind(dll, "?MC_SetVel@MultiCard@@QEAAHFN@Z", c_int, this + [c_short, c_double])
        self._MC_Update = _bind(dll, "?MC_Update@MultiCard@@QEAAHJ@Z", c_int, this + [c_long])
        self._MC_GetAllSysStatusSX = _bind(
            dll,
            "?MC_GetAllSysStatusSX@MultiCard@@QEAAHPEAU_AllSysStatusDataSX@@@Z",
            c_int,
            this + [ctypes.POINTER(TAllSysStatusDataSX)],
        )
        # Public API supplies the controller sample clock. MC_GetClock itself is
        # private in the shipped DLL; do not bind its version-dependent export.
        self._MC_GetSts = _bind(dll, "?MC_GetSts@MultiCard@@QEAAHFPEAJFPEAK@Z", c_int,
                               this + [c_short, ctypes.POINTER(c_long), c_short, ctypes.POINTER(c_ulong)])
        self._MC_GetDiRaw = _bind(dll, "?MC_GetDiRaw@MultiCard@@QEAAHFPEAJ@Z", c_int, this + [c_short, ctypes.POINTER(c_long)])
        self._MC_Stop = _bind(dll, "?MC_Stop@MultiCard@@QEAAHJJ@Z", c_int, this + [c_long, c_long])
        self._MC_SetExtDoBit = _bind(dll, "?MC_SetExtDoBit@MultiCard@@QEAAHFFG@Z", c_int, this + [c_short, c_short, c_ushort])
        self._MC_GetExtDoBit = _bind(dll, "?MC_GetExtDoBit@MultiCard@@QEAAHFFPEAG@Z", c_int, this + [c_short, c_short, ctypes.POINTER(c_ushort)])
        self._MC_PrfJog = _bind(dll, "?MC_PrfJog@MultiCard@@QEAAHF@Z", c_int, this + [c_short])
        self._MC_SetJogPrm = _bind(
            dll, "?MC_SetJogPrm@MultiCard@@QEAAHFPEAUJogPrm@@@Z", c_int, this + [c_short, ctypes.POINTER(TJogPrm)]
        )
        self._MC_ZeroPos = _bind(dll, "?MC_ZeroPos@MultiCard@@QEAAHFF@Z", c_int, this + [c_short, c_short])
        self._MC_SetSoftLimit = _bind(dll, "?MC_SetSoftLimit@MultiCard@@QEAAHFJJ@Z", c_int, this + [c_short, c_long, c_long])
        self._open = False
        # MC_Open 只看是否打开成功。MC_ZeroPos 返回 1 表示执行失败，旧程序不看这个返回值，回零继续。
        for name, fn in list(vars(self).items()):
            if name.startswith('_MC_') and name not in ('_MC_Open', '_MC_ZeroPos'):
                def checked(*args, _fn=fn, _name=name):
                    rc = _fn(*args)
                    if rc != 0:
                        raise OSError(f'{_name[1:]} failed: {rc}')
                    return rc
                setattr(self, name, checked)

    def start_debug_log(self, flag: int) -> None:
        self._MC_StartDebugLog(self._this, flag)

    def open(self, card_num: int, local_ip: str, local_port: int, card_ip: str, card_port: int) -> bool:
        local = local_ip.encode("ascii")
        card = card_ip.encode("ascii")
        self._ip_keep = (local, card)
        rc = self._MC_Open(self._this, card_num, local, local_port, card, card_port)
        self._open = rc == 0
        return self._open

    def close(self) -> None:
        if self._open:
            self._open = False
            self._MC_Close(self._this)

    def destroy(self) -> None:
        try:
            self.close()
        finally:
            if not self._destroyed:
                self._destroyed = True
                self._dtor(self._this)

    def set_stop_dec(self, axis: int, smooth: float, abrupt: float) -> None:
        self._MC_SetStopDec(self._this, axis, smooth, abrupt)

    def lmts_on(self, axis: int, limit_type: int) -> None:
        self._MC_LmtsOn(self._this, axis, limit_type)

    def lmt_sns(self, sense: int) -> None:
        self._MC_LmtSns(self._this, sense)

    def axis_on(self, axis: int) -> None:
        self._MC_AxisOn(self._this, axis)

    def prf_trap(self, axis: int) -> None:
        self._MC_PrfTrap(self._this, axis)

    def set_trap_prm(self, axis: int, acc: float, dec: float, vel_start: float, smooth_time: int) -> None:
        prm = TTrapPrm()
        prm.acc = acc
        prm.dec = dec
        prm.velStart = vel_start
        prm.smoothTime = smooth_time
        self._trap_keep = prm
        self._MC_SetTrapPrm(self._this, axis, byref(prm))

    def set_pos(self, axis: int, pos: int) -> None:
        self._MC_SetPos(self._this, axis, pos)

    def set_vel(self, axis: int, vel: float) -> None:
        self._MC_SetVel(self._this, axis, vel)

    def update(self, mask: int) -> None:
        self._MC_Update(self._this, mask)

    def get_status(self) -> tuple[list[int], list[int]]:
        pos, _actual, status = self.get_axis_sample()
        return pos, status

    def get_axis_sample(self):
        data = TAllSysStatusDataSX()
        self._MC_GetAllSysStatusSX(self._this, byref(data))
        pos = [int(data.lAxisPrfPos[i]) for i in range(3)]
        status = [int(data.lAxisStatus[i]) for i in range(3)]
        actual = [int(data.lAxisEncPos[i]) for i in range(3)] if self.encoder_feedback else None
        return pos, actual, status

    def get_clock(self) -> float:
        value = c_ulong()
        status = (c_long * 3)()
        self._MC_GetSts(self._this, 1, status, 3, byref(value))
        return float(value.value)

    def get_di_raw(self, di_type: int) -> int:
        value = c_long(0)
        self._MC_GetDiRaw(self._this, di_type, byref(value))
        return int(value.value)

    def stop(self, mask: int, option: int) -> None:
        self._MC_Stop(self._this, mask, option)

    def set_ext_do_bit(self, card_index: int, bit_index: int, value: int) -> None:
        self._MC_SetExtDoBit(self._this, card_index, bit_index, value)

    def get_ext_do_bit(self, card_index: int, bit_index: int) -> int:
        value = c_ushort()
        self._MC_GetExtDoBit(self._this, card_index, bit_index, byref(value))
        return int(value.value)

    def prf_jog(self, axis: int) -> None:
        self._MC_PrfJog(self._this, axis)

    def set_jog_prm(self, axis: int, acc: float, dec: float, smooth: float) -> None:
        prm = TJogPrm()
        prm.dAcc = acc
        prm.dDec = dec
        prm.dSmooth = smooth
        self._jog_keep = prm
        self._MC_SetJogPrm(self._this, axis, byref(prm))

    def zero_pos(self, axis: int) -> None:
        rc = self._MC_ZeroPos(self._this, axis, 1)
        if rc not in (0, 1):
            raise OSError(f'MC_ZeroPos failed: {rc}')

    def set_soft_limit(self, axis: int, positive: int, negative: int) -> None:
        self._MC_SetSoftLimit(self._this, axis, positive, negative)


class VirtualCard:
    """假卡。速度单位与 MC_SetVel 一致，为脉冲/ms；加速度为脉冲/ms²。

    绝对运动按梯形加减速走到指令位置，到达后坐标保持不变，不加噪声。
    负向运动碰到 negLimitPos 就停在开关坐标上，供原来的回零判断使用。
    """

    def __init__(self, clock: "Clock", motion: MotionParams | None = None) -> None:
        self.clock = clock
        self.motion = motion or MotionParams()
        self.calls: list[tuple[Any, ...]] = []
        self.pos = [0.0, 0.0, 0.0]
        self.vel = [0.0, 0.0, 0.0]
        self.target = [0.0, 0.0, 0.0]
        self.mode = ["idle", "idle", "idle"]
        self.running = [False, False, False]
        self.enabled = [False, False, False]
        self._pending_mode = ["idle", "idle", "idle"]
        self._pending_vel = [0.0, 0.0, 0.0]
        self._pending_target = [0.0, 0.0, 0.0]
        self._trap_acc = [0.0, 0.0, 0.0]
        self._trap_dec = [0.0, 0.0, 0.0]
        self._trap_smooth = [0.0, 0.0, 0.0]
        self._jog_acc = [self.motion.jog_acc, self.motion.jog_acc, self.motion.jog_acc]
        self._jog_dec = [self.motion.jog_dec, self.motion.jog_dec, self.motion.jog_dec]
        self.stop_smooth = [self.motion.stop_dec_smooth] * 3
        self.stop_abrupt = [self.motion.stop_dec_abrupt] * 3
        self.neg_limit_pos = list(self.motion.neg_limit_pos)
        self._t0 = [0, 0, 0]
        self._p0 = [0.0, 0.0, 0.0]
        self._v0 = [0.0, 0.0, 0.0]
        self._v_cmd = [0.0, 0.0, 0.0]
        self._acc = [0.0, 0.0, 0.0]
        self._dec = [0.0, 0.0, 0.0]
        self._p_final = [0.0, 0.0, 0.0]
        self._t_acc = [0.0, 0.0, 0.0]
        self._t_cruise = [0.0, 0.0, 0.0]
        self._t_dec = [0.0, 0.0, 0.0]
        self._v_peak = [0.0, 0.0, 0.0]
        self._total = [0.0, 0.0, 0.0]
        self._direction = [1.0, 1.0, 1.0]
        self.neg_limit = 0
        self.pos_limit = 0
        self.outputs: dict[int, int] = {}
        self._refresh_limits()

    def catch_up(self) -> None:
        return

    def start_debug_log(self, flag: int) -> None:
        self.calls.append(("MC_StartDebugLog", flag))

    def open(self, card_num: int, local_ip: str, local_port: int, card_ip: str, card_port: int) -> bool:
        self.calls.append(("MC_Open", card_num, local_ip, local_port, card_ip, card_port))
        return True

    def close(self) -> None:
        self.calls.append(("MC_Close",))

    def destroy(self) -> None:
        return

    def set_stop_dec(self, axis: int, smooth: float, abrupt: float) -> None:
        self.calls.append(("MC_SetStopDec", axis, smooth, abrupt))
        if 1 <= axis <= 3:
            self.stop_smooth[axis - 1] = float(smooth)
            self.stop_abrupt[axis - 1] = float(abrupt)

    def lmts_on(self, axis: int, limit_type: int) -> None:
        self.calls.append(("MC_LmtsOn", axis, limit_type))

    def lmt_sns(self, sense: int) -> None:
        self.calls.append(("MC_LmtSns", sense))

    def axis_on(self, axis: int) -> None:
        self.calls.append(("MC_AxisOn", axis))
        self.enabled[axis - 1] = True

    def prf_trap(self, axis: int) -> None:
        self.calls.append(("MC_PrfTrap", axis))
        if 1 <= axis <= 3:
            self._pending_mode[axis - 1] = "trap"

    def set_trap_prm(self, axis: int, acc: float, dec: float, vel_start: float, smooth_time: int) -> None:
        self.calls.append(("MC_SetTrapPrm", axis, acc, dec, vel_start, smooth_time))
        if 1 <= axis <= 3:
            self._trap_acc[axis - 1] = float(acc)
            self._trap_dec[axis - 1] = float(dec)
            self._trap_smooth[axis - 1] = float(smooth_time)

    def set_pos(self, axis: int, pos: int) -> None:
        self.calls.append(("MC_SetPos", axis, pos))
        if 1 <= axis <= 3:
            self._pending_target[axis - 1] = float(pos)

    def set_vel(self, axis: int, vel: float) -> None:
        self.calls.append(("MC_SetVel", axis, vel))
        if 1 <= axis <= 3:
            self._pending_vel[axis - 1] = float(vel)

    def update(self, mask: int) -> None:
        self.calls.append(("MC_Update", mask))
        now = self.clock.mono_ms()
        for i in range(3):
            if not (mask & (1 << i)):
                continue
            self._sample_axis(i, now)
            mode = self._pending_mode[i]
            if mode == "trap":
                self._begin_trap(i, now)
            elif mode == "jog":
                self._begin_jog(i, now)

    def any_running(self) -> bool:
        now = self.clock.mono_ms()
        for i in range(3):
            self._sample_axis(i, now)
        return any(self.running)

    def get_status(self) -> tuple[list[int], list[int]]:
        now = self.clock.mono_ms()
        for i in range(3):
            self._sample_axis(i, now)
        self._refresh_limits()
        pos = [int(round(self.pos[i])) for i in range(3)]
        status = [(AXIS_STATUS_RUNNING if self.running[i] else AXIS_STATUS_ARRIVE)
                  | (AXIS_STATUS_ENABLE if self.enabled[i] else 0) for i in range(3)]
        return pos, status

    def peek_status(self) -> tuple[list[int], list[int]]:
        return self.get_status()

    def get_axis_sample(self):
        pos, status = self.get_status()
        # This is the explicitly ideal simulated encoder, not a real-card fallback.
        return pos, list(pos), status

    def get_clock(self) -> float:
        return float(self.clock.mono_ms())

    def get_di_raw(self, di_type: int) -> int:
        self.calls.append(("MC_GetDiRaw", di_type))
        if di_type == MC_LIMIT_NEGATIVE:
            return self.neg_limit
        if di_type == MC_LIMIT_POSITIVE:
            return self.pos_limit
        return 0

    def stop(self, mask: int, option: int) -> None:
        self.calls.append(("MC_Stop", mask, option))
        now = self.clock.mono_ms()
        for i in range(3):
            if not (mask & (1 << i)):
                continue
            self._sample_axis(i, now)
            abrupt = bool(option & (1 << i))
            dec = self.stop_abrupt[i] if abrupt else self.stop_smooth[i]
            self._begin_stop(i, now, dec)

    def set_ext_do_bit(self, card_index: int, bit_index: int, value: int) -> None:
        self.calls.append(("MC_SetExtDoBit", card_index, bit_index, value))
        self.outputs[bit_index] = value

    def get_ext_do_bit(self, card_index: int, bit_index: int) -> int:
        return self.outputs.get(bit_index, 0)

    def prf_jog(self, axis: int) -> None:
        self.calls.append(("MC_PrfJog", axis))
        if 1 <= axis <= 3:
            self._pending_mode[axis - 1] = "jog"

    def set_jog_prm(self, axis: int, acc: float, dec: float, smooth: float) -> None:
        self.calls.append(("MC_SetJogPrm", axis, acc, dec, smooth))
        if 1 <= axis <= 3:
            self._jog_acc[axis - 1] = float(acc)
            self._jog_dec[axis - 1] = float(dec)

    def zero_pos(self, axis: int) -> None:
        self.calls.append(("MC_ZeroPos", axis, 1))
        if 1 <= axis <= 3:
            self._hold(axis - 1, 0.0)

    def set_soft_limit(self, axis: int, positive: int, negative: int) -> None:
        self.calls.append(("MC_SetSoftLimit", axis, positive, negative))

    def set_axis_pos(self, axis_index: int, pos: float) -> None:
        self._hold(axis_index, float(pos))

    def _begin_trap(self, i: int, now: int) -> None:
        target = self._pending_target[i]
        start = self.pos[i]
        distance = target - start
        if abs(distance) <= 0.5:
            self._hold(i, target)
            return
        direction = 1.0 if distance > 0 else -1.0
        if direction < 0 and start <= self.neg_limit_pos[i]:
            self._hold(i, self.neg_limit_pos[i])
            return
        acc = abs(self._trap_acc[i]) or 1e-6
        dec = abs(self._trap_dec[i]) or acc
        v_max = abs(self._pending_vel[i])
        if v_max < 1e-9:
            self.running[i] = False
            self.mode[i] = "idle"
            return
        smooth = max(0.0, self._trap_smooth[i])
        t_acc, t_cruise, t_dec, v_peak = _plan_trap(abs(distance), v_max, acc, dec, smooth)
        self.mode[i] = "trap"
        self.running[i] = True
        self.target[i] = target
        self._t0[i] = now
        self._p0[i] = start
        self._direction[i] = direction
        self._acc[i] = acc
        self._dec[i] = dec
        self._v_cmd[i] = v_max
        self._t_acc[i] = t_acc
        self._t_cruise[i] = t_cruise
        self._t_dec[i] = t_dec
        self._v_peak[i] = v_peak
        self._total[i] = t_acc + t_cruise + t_dec
        self._p_final[i] = target
        self.vel[i] = 0.0

    def _begin_jog(self, i: int, now: int) -> None:
        v_cmd = self._pending_vel[i]
        if abs(v_cmd) < 1e-12:
            self._hold(i, self.pos[i])
            return
        if v_cmd < 0 and self.pos[i] <= self.neg_limit_pos[i]:
            self._hold(i, self.neg_limit_pos[i])
            return
        self.mode[i] = "jog"
        self.running[i] = abs(v_cmd) > 0
        self._t0[i] = now
        self._p0[i] = self.pos[i]
        self._v0[i] = 0.0
        self._v_cmd[i] = v_cmd
        self._acc[i] = abs(self._jog_acc[i]) or 1e-6
        self._dec[i] = abs(self._jog_dec[i]) or self._acc[i]
        self.vel[i] = 0.0

    def _begin_stop(self, i: int, now: int, dec: float) -> None:
        dec = abs(dec) or 1e-6
        v0 = self.vel[i]
        if abs(v0) < 1e-9:
            self._hold(i, self.pos[i])
            return
        t_stop = abs(v0) / dec
        p_final = self.pos[i] + math.copysign(v0 * v0 / (2.0 * dec), v0)
        self.mode[i] = "stop"
        self.running[i] = True
        self._t0[i] = now
        self._p0[i] = self.pos[i]
        self._v0[i] = v0
        self._dec[i] = dec
        self._total[i] = t_stop
        self._p_final[i] = p_final

    def _sample_axis(self, i: int, now: int) -> None:
        mode = self.mode[i]
        if mode == "idle":
            self.vel[i] = 0.0
            self.running[i] = False
            return
        elapsed = max(0.0, float(now - self._t0[i]))
        if mode == "trap":
            self._sample_trap(i, elapsed)
        elif mode == "jog":
            self._sample_jog(i, elapsed)
        elif mode == "stop":
            self._sample_stop(i, elapsed)
        self._apply_neg_limit(i)

    def _sample_trap(self, i: int, elapsed: float) -> None:
        if elapsed >= self._total[i]:
            self._hold(i, self._p_final[i])
            return
        dist = _trap_distance(
            elapsed,
            self._t_acc[i],
            self._t_cruise[i],
            self._t_dec[i],
            self._v_peak[i],
            self._acc[i],
            self._dec[i],
        )
        full = abs(self._p_final[i] - self._p0[i])
        if dist >= full:
            self._hold(i, self._p_final[i])
            return
        self.pos[i] = self._p0[i] + self._direction[i] * dist
        self.vel[i] = self._direction[i] * _trap_velocity(
            elapsed,
            self._t_acc[i],
            self._t_cruise[i],
            self._t_dec[i],
            self._v_peak[i],
            self._acc[i],
            self._dec[i],
        )
        self.running[i] = True

    def _sample_jog(self, i: int, elapsed: float) -> None:
        v_cmd = self._v_cmd[i]
        acc = self._acc[i]
        t_acc = abs(v_cmd) / acc if acc else 0.0
        if elapsed < t_acc:
            v = math.copysign(acc * elapsed, v_cmd) if v_cmd else 0.0
            dist = math.copysign(0.5 * acc * elapsed * elapsed, v_cmd) if v_cmd else 0.0
        else:
            v = v_cmd
            dist = math.copysign(0.5 * acc * t_acc * t_acc, v_cmd) + v_cmd * (elapsed - t_acc)
        self.pos[i] = self._p0[i] + dist
        self.vel[i] = v
        self.running[i] = True

    def _sample_stop(self, i: int, elapsed: float) -> None:
        if elapsed >= self._total[i]:
            self._hold(i, self._p_final[i])
            return
        v0 = self._v0[i]
        dec = self._dec[i]
        sign = 1.0 if v0 > 0 else -1.0
        self.vel[i] = v0 - sign * dec * elapsed
        self.pos[i] = self._p0[i] + v0 * elapsed - sign * 0.5 * dec * elapsed * elapsed
        self.running[i] = True

    def _apply_neg_limit(self, i: int) -> None:
        limit = self.neg_limit_pos[i]
        if self.vel[i] < 0 and self.pos[i] <= limit:
            self._hold(i, limit)
        elif self.mode[i] == "jog" and self._v_cmd[i] < 0 and self.pos[i] <= limit:
            self._hold(i, limit)

    def _hold(self, i: int, pos: float) -> None:
        self.pos[i] = float(pos)
        self.vel[i] = 0.0
        self.running[i] = False
        self.mode[i] = "idle"
        self._refresh_limits()

    def _refresh_limits(self) -> None:
        neg = 0
        for i in range(3):
            if self.pos[i] <= self.neg_limit_pos[i] + 1e-9:
                neg |= 1 << i
        self.neg_limit = neg


def _plan_trap(distance: float, v_max: float, acc: float, dec: float, smooth: float) -> tuple[float, float, float, float]:
    distance = abs(distance)
    v_max = abs(v_max)
    acc = abs(acc) or 1e-6
    dec = abs(dec) or acc
    if v_max < 1e-9 or distance < 1e-9:
        return 0.0, 0.0, 0.0, 0.0
    d_acc = v_max * v_max / (2.0 * acc)
    d_dec = v_max * v_max / (2.0 * dec)
    if d_acc + d_dec >= distance:
        v_peak = math.sqrt(distance * 2.0 * acc * dec / (acc + dec))
        t_acc = v_peak / acc + smooth
        t_dec = v_peak / dec + smooth
        # 平滑拉长了时间，末点仍由 _sample_trap 在 total 处落到目标。
        return t_acc, 0.0, t_dec, v_peak
    t_acc = v_max / acc + smooth
    t_dec = v_max / dec + smooth
    d_cruise = distance - d_acc - d_dec
    t_cruise = d_cruise / v_max if v_max else 0.0
    return t_acc, max(0.0, t_cruise), t_dec, v_max


def _trap_distance(elapsed: float, t_acc: float, t_cruise: float, t_dec: float, v_peak: float, acc: float, dec: float) -> float:
    if elapsed <= 0:
        return 0.0
    acc = acc or 1e-6
    dec = dec or acc
    d_acc = v_peak * v_peak / (2.0 * acc)
    d_dec = v_peak * v_peak / (2.0 * dec)
    if elapsed < t_acc:
        if t_acc <= 0:
            return 0.0
        return d_acc * (elapsed / t_acc) ** 2
    if elapsed < t_acc + t_cruise:
        return d_acc + v_peak * (elapsed - t_acc)
    td = elapsed - t_acc - t_cruise
    if t_dec <= 0:
        return d_acc + v_peak * t_cruise
    covered = d_dec * (1.0 - (1.0 - td / t_dec) ** 2)
    return d_acc + v_peak * t_cruise + covered


def _trap_velocity(
    elapsed: float, t_acc: float, t_cruise: float, t_dec: float, v_peak: float, acc: float, dec: float
) -> float:
    if elapsed < t_acc:
        return v_peak * (elapsed / t_acc) if t_acc else v_peak
    if elapsed < t_acc + t_cruise:
        return v_peak
    td = elapsed - t_acc - t_cruise
    if t_dec <= 0:
        return 0.0
    return v_peak * max(0.0, 1.0 - td / t_dec)


class Clock:
    def mono_ms(self) -> int: ...
    def stamp_ms(self) -> int: ...
    def advance(self, ms: int) -> None: ...


class WallClock:
    def mono_ms(self) -> int:
        return int(time.monotonic() * 1000)

    def stamp_ms(self) -> int:
        return int(time.time() * 1000)

    def advance(self, ms: int) -> None:
        time.sleep(ms / 1000.0)


class VirtualClock:
    def __init__(self) -> None:
        self._now = int(time.time() * 1000)

    def mono_ms(self) -> int:
        return self._now

    def stamp_ms(self) -> int:
        return self._now

    def advance(self, ms: int) -> None:
        self._now += int(ms)

    def align_idle(self, wall_ms: int) -> None:
        self._now = max(wall_ms, self._now + 1000)


class LegacyEventTranslator:
    """旧 evt_* 到规范指令事件。Jog / JogStop 没有 evt_started，完成前补一条 COMMAND_RUNNING。"""

    def __init__(self) -> None:
        self._running: set[str] = set()

    def translate(self, kind: str, message_id: str, message: str) -> list[tuple[str, str]]:
        if kind == "evt_ack":
            return [("COMMAND_RECEIVED", message)]
        if kind == "evt_started":
            self._running.add(message_id)
            return [("COMMAND_RUNNING", message)]
        if kind == "evt_success":
            out: list[tuple[str, str]] = []
            if message_id not in self._running:
                out.append(("COMMAND_RUNNING", ""))
                self._running.add(message_id)
            out.append(("COMMAND_COMPLETED", message))
            return out
        if kind == "evt_error":
            return [("COMMAND_FAILED", message)]
        return []


def _as_int(params: dict[str, Any], key: str, default: int) -> int:
    if key not in params or params[key] is None:
        return default
    value = params[key]
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError("JSON Error")
    return int(value)


def _as_float(params: dict[str, Any], key: str, default: float) -> float:
    if key not in params or params[key] is None:
        return default
    value = params[key]
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError("JSON Error")
    return float(value)


class MotionEngine:
    """MotionController 去掉 MQTT 之后的指令分发和 StateUpdateLoop。"""

    def __init__(self, card: Any, clock: Clock, motion: MotionParams | None = None) -> None:
        self.card = card
        self.clock = clock
        self.motion = motion or MotionParams()
        self.opened = False
        self.speed = list(self.motion.home_seek_vel)
        self.reset = 0
        self.ready = 0
        self.autoreset = 0
        self.homing_message_id = ""
        self.homing_started_ms = 0
        self.stopping: dict[str, Any] | None = None
        self.jog_last: dict[int, int] = {}
        self.jog_running = {1: False, 2: False, 3: False}
        self.move: dict[str, Any] | None = None
        self.io_deadline: dict[str, Any] | None = None
        self.vision: dict[str, Any] | None = None
        self.on_event: Callable[[str, str, str], None] | None = None
        self.on_telemetry: Callable[[dict[str, int], int], None] | None = None
        self.http_get: Callable[[], str | None] = _http_get_status
        self._telemetry_tick = 0
        self._last_fields: dict[str, int] | None = None
        self._polled: tuple[list[int], list[int] | None, list[int]] | None = None
        self._stop_owed = False
        self._lock = threading.RLock()
        self._stop = False
        self._thread: threading.Thread | None = None
        # The watchdog may revoke a session without waiting for the SDK lock.
        self.connection_valid = True

    def initialize(self, local_ip: str, local_port: int, card_ip: str, card_port: int) -> bool:
        with self._lock:
            self.card.start_debug_log(0)
            if not self.card.open(1, local_ip, local_port, card_ip, card_port):
                return False
            for axis in (1, 2, 3):
                self.card.set_stop_dec(axis, self.motion.stop_dec_smooth, self.motion.stop_dec_abrupt)
            for axis in (1, 2, 3):
                self.card.lmts_on(axis, -1)
            self.card.lmt_sns(0)
            for axis in (1, 2, 3):
                self.card.axis_on(axis)
            _, _, status = self.card.get_axis_sample()
            if not all(word & AXIS_STATUS_ENABLE for word in status):
                raise OSError(f"Axis enable confirmation failed: {status!r}")
            self.connection_valid = True
            self.opened = True
            return True

    def start_wall_loop(self) -> None:
        self._stop = False
        self._thread = threading.Thread(target=self._wall_loop, name="motion-state", daemon=True)
        self._thread.start()

    def _wall_loop(self) -> None:
        while not self._stop:
            self.pump_once()
            if self._stop:
                break
            time.sleep(self.motion.loop_period_ms / 1000.0)

    def close(self) -> None:
        self._stop = True
        thread = self._thread
        if thread is not None and thread is not threading.current_thread():
            thread.join(timeout=2.0)
        # 掉线后才会走到这里，停轴和关闭都可能再超时；任何一步失败都不能跳过后面的释放。
        with self._lock:
            try:
                if self.opened:
                    self.opened = False
                    try:
                        self._stop_all_axes()
                    except OSError as error:
                        logger.warning("关闭运动卡时停轴失败: %s", error)
                    try:
                        self.card.close()
                    except OSError as error:
                        logger.warning("关闭运动卡连接失败: %s", error)
            finally:
                self.card.destroy()

    def submit(self, operation: str, message_id: str, params: dict[str, Any]) -> None:
        with self._lock:
            if not self.connection_valid:
                self._emit('evt_error', message_id, 'Motion card session expired.')
                return
            self._emit("evt_ack", message_id, "Command parsed: " + operation)
            if self._stop_owed:
                self._stop_all_axes()
            try:
                self._dispatch(operation, message_id, params)
            except ValueError:
                self._emit("evt_error", message_id, "JSON Error")

    def busy(self) -> bool:
        with self._lock:
            running = self.card.any_running() if hasattr(self.card, "any_running") else False
            return bool(
                running
                or self.reset
                or self.move
                or self.stopping
                or self.io_deadline
                or self.vision
                or any(self.jog_running.values())
            )

    def fields(self) -> dict[str, int]:
        with self._lock:
            return self._read_fields()

    def polled(self) -> tuple[dict[str, int | str], list[int]]:
        """pump_once 本周期读到的字段和轴状态字，避免同一周期重复读卡。"""
        with self._lock:
            if self._polled is None:
                self._polled = self.card.get_axis_sample()
            pos, actual, status = self._polled
            return _fields(pos, status, actual), list(status)

    def restore_fields(self, state: dict[str, Any]) -> None:
        with self._lock:
            restored: dict[str, int] = {}
            for key in FIELD_KEYS:
                if key in state and isinstance(state[key], (int, float)) and not isinstance(state[key], bool):
                    restored[key] = int(state[key])
            for index, key in enumerate(("axis1_pos", "axis2_pos", "axis3_pos")):
                if key in restored and hasattr(self.card, "set_axis_pos"):
                    self.card.set_axis_pos(index, restored[key])
            self._last_fields = self._read_fields()

    def pump_once(self) -> None:
        with self._lock:
            if not self.opened:
                return
            if self._stop_owed:
                self._stop_all_axes()
            self._jog_watchdog()
            pos, actual, status = self.card.get_axis_sample()
            if not self.connection_valid:
                return
            self._polled = (pos, actual, status)
            neg = self.card.get_di_raw(MC_LIMIT_NEGATIVE)
            if not self.connection_valid:
                return
            self._maybe_telemetry(pos, status, actual)
            # 0x4、0x8 是软限位。回零会经过软限位，这两位不作为失败条件。
            if (self.move or self.reset or any(self.jog_running.values())) and any(word & 0x400113 for word in status):
                self._cancel_motion('Hardware fault during motion.')
            if (self.move or self.reset or any(self.jog_running.values())) and not all(word & AXIS_STATUS_ENABLE for word in status):
                self._cancel_motion(f'Axis not enabled during motion: {status!r}')
            if self.reset and self.clock.mono_ms() - self.homing_started_ms >= self.motion.home_timeout_ms:
                self._cancel_motion('Homing timeout.')
            self._poll_move(pos, status, actual)
            self._poll_stop(status)
            self._poll_io()
            self._poll_vision()
            if any(bit & AXIS_STATUS_RUNNING for bit in status):
                return
            self._homing(neg)

    def _dispatch(self, operation: str, message_id: str, params: dict[str, Any]) -> None:
        if self.opened and operation in ('MoveAbsolute', 'Jog', 'ResetMotion'):
            _, _, status = self.card.get_axis_sample()
            if not self.connection_valid:
                self._emit('evt_error', message_id, 'Motion card session expired.')
                return
            axes = (1, 2, 3) if operation == 'ResetMotion' else (_as_int(params, 'axis', 1),)
            for axis in axes:
                if 1 <= axis <= 3 and not status[axis - 1] & AXIS_STATUS_ENABLE:
                    self._emit('evt_error', message_id, f'Axis {axis} not enabled (status=0x{status[axis - 1]:08X}).')
                    return
                if 1 <= axis <= 3 and status[axis - 1] & 0x400113:
                    self._emit('evt_error', message_id, f'Axis {axis} hardware fault (status=0x{status[axis - 1]:08X}).')
                    return
        if operation == "MoveAbsolute":
            axis = _as_int(params, "axis", 1)
            position = _as_int(params, "position", 0)
            self._move_absolute(message_id, axis, position)
        elif operation == "ResetMotion":
            self.homing_message_id = message_id
            self.homing_started_ms = self.clock.mono_ms()
            self._emit("evt_started", message_id, "Hardware homing sequence started.")
            self._reset_motion()
        elif operation == "Stop":
            self._emit("evt_started", message_id, "Stopping all axes...")
            self._cancel_motion('Motion cancelled by Stop.')
            self.stopping = {'id': message_id, 'axis': None, 'started': self.clock.mono_ms()}
            self._poll_stop(self.card.get_status()[1])
        elif operation == "SetExtDoBit":
            bit_index = _as_int(params, "BitIndex", 0)
            value = _as_int(params, "Value", 0)
            self._emit("evt_started", message_id, "Setting IO bit...")
            self.card.set_ext_do_bit(0, bit_index, value)
            if bit_index == self.motion.ext_do_hold_bit:
                self.io_deadline = {"id": message_id, "due": self.clock.mono_ms() + self.motion.ext_do_hold_ms}
            else:
                self._emit("evt_success", message_id, "IO Bit set successfully.")
        elif operation == "VisionCheck":
            _as_int(params, "currentTopNo", 0)
            self._emit("evt_started", message_id, "Vision checking...")
            self.vision = {"id": message_id, "due": self.clock.mono_ms(), "tries": 0, "body": ""}
        elif operation == "Jog":
            axis = _as_int(params, "axis", 1)
            direction = _as_int(params, "dir", 1)
            if 1 <= axis <= 3:
                speed = self.motion.jog_speed[axis - 1]
                self._emit('evt_started', message_id, 'Jog heartbeat accepted.')
                self.jog_last[axis] = self.clock.mono_ms()
                if not self.jog_running[axis]:
                    self.jog_running[axis] = True
                    self.card.prf_jog(axis)
                    self.card.set_jog_prm(axis, self.motion.jog_acc, self.motion.jog_dec, self.motion.jog_smooth)
                    self.card.set_vel(axis, speed if direction == 1 else -speed)
                    self._update_axis(1 << (axis - 1))
                self._emit("evt_success", message_id, "Jog heartbeat accepted.")
        elif operation == "JogStop":
            axis = _as_int(params, "axis", 1)
            if 1 <= axis <= 3:
                self._emit('evt_started', message_id, 'Stopping jog axis.')
                self.jog_running[axis] = False
                self.card.stop(1 << (axis - 1), 1 << (axis - 1))
                self.stopping = {'id': message_id, 'axis': axis, 'started': self.clock.mono_ms()}
                self._poll_stop(self.card.get_status()[1])
        else:
            self._emit("evt_error", message_id, "Unknown operation code.")

    def _move_absolute(self, message_id: str, axis: int, target_pos: int) -> None:
        if not self.opened:
            self._emit("evt_error", message_id, "Motion card not initialized.")
            return
        self._emit("evt_started", message_id, "Moving axis " + str(axis))
        acc, dec = self.motion.move_acc_dec(axis)
        self.card.prf_trap(axis)
        self.card.set_trap_prm(axis, acc, dec, self.motion.move_vel_start, self.motion.move_smooth_time)
        self.card.set_pos(axis, target_pos)
        vel = self.motion.move_speed(axis)
        self.card.set_vel(axis, vel)
        self._update_axis(0x0001 << (axis - 1))
        self.move = {"id": message_id, "axis": axis, "target": target_pos}
        pos, actual, status = self.card.get_axis_sample()
        self._poll_move(pos, status, actual)

    def _home_jog(self, axis: int, vel: float) -> None:
        m = self.motion
        self.card.prf_jog(axis)
        self.card.set_jog_prm(axis, m.home_jog_acc, m.home_jog_dec, m.home_jog_smooth)
        self.card.set_vel(axis, vel)
        self._update_axis(1 << (axis - 1))

    def _home_trap(self, axis: int, pos: int, vel: float, acc: float, dec: float, smooth: int) -> None:
        self.card.prf_trap(axis)
        self.card.set_trap_prm(axis, acc, dec, 0.0, smooth)
        self.card.set_pos(axis, pos)
        self.card.set_vel(axis, vel)
        self._update_axis(1 << (axis - 1))

    def _update_axis(self, mask: int) -> None:
        if not self.connection_valid:
            raise OSError('Motion card session expired before starting axis')
        self.card.update(mask)

    def _poll_move(self, pos: list[int], status: list[int], actual: list[int] | None) -> None:
        move = self.move
        if move is None:
            return
        axis = move["axis"]
        if not self.connection_valid or not 1 <= axis <= len(status):
            return
        word = status[axis - 1]
        if not word & AXIS_STATUS_ENABLE or word & (AXIS_STATUS_RUNNING | 0x400113):
            return
        if actual is not None and abs(actual[axis - 1] - move['target']) >= self.motion.move_arrive_band:
            return
        current = pos[axis - 1] if 1 <= axis <= len(pos) else 0
        if abs(current - move["target"]) < self.motion.move_arrive_band:
            self.move = None
            self._emit("evt_success", move["id"], "Target position reached.")

    def _cancel_motion(self, reason: str) -> None:
        move, home, stopping = self.move, self.homing_message_id, self.stopping
        # Cancel the software sequence before the SDK call, even if stopping fails.
        self.move = None
        self.homing_message_id = ''
        self.reset = self.autoreset = 0
        self.stopping = None
        if home:
            self.ready = 0
        try:
            self._stop_all_axes()
        finally:
            for message_id in (move['id'] if move else '', home, stopping['id'] if stopping else ''):
                if message_id:
                    self._emit('evt_error', message_id, reason)

    def _poll_stop(self, status) -> None:
        pending = self.stopping
        if not pending:
            return
        words = status if pending['axis'] is None else [status[pending['axis'] - 1]]
        if not any(word & AXIS_STATUS_RUNNING for word in words):
            self.stopping = None
            self._emit('evt_success', pending['id'], 'Axes stopped successfully.' if pending['axis'] is None else 'Jog stopped.')
        elif self.clock.mono_ms() - pending['started'] >= self.motion.stop_timeout_ms:
            self.stopping = None
            self._emit('evt_error', pending['id'], 'Stop confirmation timeout.')

    def _poll_io(self) -> None:
        pending = self.io_deadline
        if pending is None:
            return
        if self.clock.mono_ms() >= pending["due"]:
            self.io_deadline = None
            self._emit("evt_success", pending["id"], "IO Bit set successfully.")

    def _poll_vision(self) -> None:
        vision = self.vision
        if vision is None or self.clock.mono_ms() < vision["due"]:
            return
        body = self.http_get()
        vision["tries"] += 1
        if body is not None:
            vision["body"] = body
            text = body.strip()
            parts = text.split("_")
            if len(parts) >= 2 and parts[0].lower() == "true" and parts[1].lower() == "true":
                self.vision = None
                self._emit("evt_success", vision["id"], "Vision check pass.")
                return
        if vision["tries"] >= 10:
            self.vision = None
            self._emit("evt_error", vision["id"], "Vision check fail: " + vision["body"])
            return
        vision["due"] = self.clock.mono_ms() + 200

    def _reset_motion(self) -> None:
        wide_a, wide_b = self.motion.soft_wide
        self.card.set_soft_limit(1, wide_a, wide_b)
        self.card.set_soft_limit(2, wide_a, wide_b)
        self.card.set_soft_limit(3, wide_a, wide_b)
        self.ready = 0
        self.reset = 1
        self.autoreset = 0

    def _stop_all_axes(self) -> None:
        for axis in (1, 2, 3):
            self.jog_running[axis] = False
        # 通讯超时时停止指令可能没到卡上：记账，由下一次轮询或下一条指令先重发。
        self._stop_owed = True
        self.card.stop(0xFFFF, 0xFFFF)
        self._stop_owed = False

    def _jog_watchdog(self) -> None:
        now = self.clock.mono_ms()
        for axis in (1, 2, 3):
            if not self.jog_running[axis]:
                continue
            elapsed = now - self.jog_last.get(axis, now)
            if elapsed > self.motion.jog_watchdog_ms:
                self.card.stop(1 << (axis - 1), 1 << (axis - 1))
                self.jog_running[axis] = False

    def _homing(self, neg_limit: int) -> None:
        if not self.reset:
            return
        m = self.motion
        if self.autoreset == 0:
            self._home_jog(1, -m.home_seek_vel[0])
            self.autoreset += 1
        elif self.autoreset == 1:
            if not (neg_limit & 0x1):
                return
            self.card.zero_pos(1)
            self._home_trap(1, m.home_fast_pos, m.home_fast_vel, m.home_fast_acc, m.home_fast_dec, m.home_fast_smooth)
            self.autoreset += 1
        elif self.autoreset == 2:
            if neg_limit & 0x1:
                return
            self._home_jog(1, -m.home_return_vel)
            self.autoreset += 1
        elif self.autoreset == 3:
            if not (neg_limit & 0x1):
                return
            self.card.stop(0xFFFF, 0xFFFF)
            self.card.zero_pos(1)
            self.autoreset += 1
        elif self.autoreset == 4:
            self._home_jog(2, -m.home_seek_vel[1])
            self._home_jog(3, -m.home_seek_vel[2])
            self.autoreset += 1
        elif self.autoreset == 5:
            if not (neg_limit & 0x2) or not (neg_limit & 0x4):
                return
            self.card.zero_pos(2)
            self.card.zero_pos(3)
            self._home_trap(2, m.home_axis2_pos, m.home_place_vel2, m.home_place_acc, m.home_place_dec, m.home_axis2_smooth)
            self._home_trap(3, m.home_axis3_pos, m.home_place_vel3, m.home_place_acc, m.home_place_dec, m.home_axis3_smooth)
            self.autoreset += 1
        elif self.autoreset == 6:
            if (neg_limit & 0x2) or (neg_limit & 0x4):
                return
            self._home_jog(2, -m.home_creep_vel)
            self._home_jog(3, -m.home_creep_vel)
            self.autoreset += 1
        elif self.autoreset == 7:
            if not (neg_limit & 0x2) or not (neg_limit & 0x4):
                return
            self.card.zero_pos(1)
            self.card.zero_pos(2)
            self.card.zero_pos(3)
            self.reset = 0
            self.ready = 1
            self.card.set_soft_limit(1, m.soft_final[0][0], m.soft_final[0][1])
            self.card.set_soft_limit(2, m.soft_final[1][0], m.soft_final[1][1])
            self.card.set_soft_limit(3, m.soft_final[2][0], m.soft_final[2][1])
            if self.homing_message_id:
                message_id = self.homing_message_id
                self.homing_message_id = ""
                self._emit("evt_success", message_id, "Hardware homing completed.")

    def _maybe_telemetry(self, pos: list[int], status: list[int], actual: list[int] | None) -> None:
        tick = self._telemetry_tick
        self._telemetry_tick += 1
        if tick % self.motion.telemetry_every != 0:
            return
        fields = _fields(pos, status, actual)
        self._last_fields = fields
        if self.on_telemetry is not None:
            self.on_telemetry(dict(fields), self.clock.stamp_ms())

    def _read_fields(self) -> dict[str, int]:
        if hasattr(self.card, "catch_up"):
            self.card.catch_up()
        pos, actual, status = self.card.get_axis_sample()
        fields = _fields(pos, status, actual)
        self._last_fields = fields
        return fields

    def _emit(self, kind: str, message_id: str, message: str) -> None:
        if kind in ('evt_success', 'evt_started') and not self.connection_valid:
            kind, message = 'evt_error', 'Motion card session expired.'
        if self.on_event is not None:
            self.on_event(kind, message_id, message)


def _fields(pos: list[int], status: list[int], actual: list[int] | None) -> dict[str, int | str]:
    del actual
    fields: dict[str, int | str] = {}
    for i in range(3):
        fields[f'axis{i + 1}_pos'] = int(pos[i])
        fields[f'axis{i + 1}_status'] = describe_axis_status(status[i])
    return fields


def _http_get_status() -> str | None:
    try:
        with urllib.request.urlopen("http://127.0.0.1:8089/status", timeout=2) as response:
            return response.read().decode("utf-8", "replace")
    except (urllib.error.URLError, TimeoutError, OSError):
        return None


def advance_and_pump(engine: MotionEngine, count: int, step_ms: int = 100) -> None:
    for _ in range(count):
        engine.clock.advance(step_ms)
        engine.pump_once()
