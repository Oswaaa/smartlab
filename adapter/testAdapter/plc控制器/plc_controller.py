"""
PLC 遥控器  —  极简版
依赖：pip install paho-mqtt
运行：python plc_controller.py
"""

import json
import queue
import time
import tkinter as tk
from tkinter import font as tkfont

try:
    import paho.mqtt.client as mqtt
except ImportError:
    raise SystemExit("请先安装依赖：pip install paho-mqtt")

# ══════════════════════════════════════════════════════════════
# 连接配置
# ══════════════════════════════════════════════════════════════
BROKER    = "192.168.11.178"
PORT      = 1883
USER      = "plc"
PASSWORD  = "123456"
CLIENT_ID = "plc_controller"          # 客户端名称

TOPIC_SUB = "plc/0001/data"           # 订阅：PLC 上报数据
TOPIC_PUB = "plc/0001/command"        # 发布：向 PLC 写寄存器
DEVICE_SN = "plc0001"

# 寄存器说明
# MW0  : 温度原始值 (÷100 → °C)
# MW20 : 散热输出   1=开 0=关
# MW21 : 自动模式   1=是 0=否
# MW22 : 手动模式   1=是 0=否


# ══════════════════════════════════════════════════════════════
# MQTT 后台工作线程
# ══════════════════════════════════════════════════════════════
class MqttWorker:
    def __init__(self, q: queue.Queue):
        self._q = q
        self._c = mqtt.Client(
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
            client_id=CLIENT_ID,
            clean_session=True,
        )
        self._c.username_pw_set(USER, PASSWORD)
        self._c.reconnect_delay_set(2, 30)
        self._c.on_connect    = self._on_connect
        self._c.on_disconnect = self._on_disconnect
        self._c.on_message    = self._on_message

    def start(self):
        self._q.put(("log", f"正在连接 {BROKER}:{PORT} …", "info"))
        self._c.connect_async(BROKER, PORT, keepalive=30)
        self._c.loop_start()

    def stop(self):
        self._c.loop_stop()
        self._c.disconnect()

    def send(self, registers: dict):
        """按 PLC 协议格式发布指令"""
        payload = json.dumps(
            [{"DeviceSN": DEVICE_SN, "TagData": [registers]}],
            separators=(",", ":"),
        )
        result = self._c.publish(TOPIC_PUB, payload, qos=1)
        ok = result.rc == mqtt.MQTT_ERR_SUCCESS
        self._q.put(("log",
                     f"发布 {TOPIC_PUB}  {'✓' if ok else '✗ rc=' + str(result.rc)}",
                     "cmd" if ok else "err"))

    def _on_connect(self, client, _ud, _flags, rc, _props=None):
        if getattr(rc, "is_failure", False) or (isinstance(rc, int) and rc != 0):
            self._q.put(("status", f"连接失败 rc={rc}", "err"))
            return
        client.subscribe(TOPIC_SUB, qos=1)
        self._q.put(("status", "已连接 ✓", "ok"))
        self._q.put(("log", f"已订阅 {TOPIC_SUB}", "ok"))

    def _on_disconnect(self, _client, _ud, *args):
        rc = args[-2] if len(args) >= 2 else (args[0] if args else None)
        self._q.put(("status", "断开，重连中…", "warn"))
        self._q.put(("log", f"连接断开 rc={rc}，自动重连", "warn"))

    def _on_message(self, _c, _ud, msg):
        topic = msg.topic
        try:
            data  = json.loads(msg.payload)
        except Exception as e:
            self._q.put(("log", f"JSON解析失败 [{topic}]: {e}", "err"))
            return

        # 记录收到的实际话题，方便排查
        self._q.put(("log", f"收到消息 [{topic}]", "info"))

        # 解析数组 or 单对象两种格式
        entries = data if isinstance(data, list) else [data]
        entry   = next(
            (e for e in entries
             if isinstance(e, dict) and e.get("DeviceSN") == DEVICE_SN),
            None,
        )
        if entry is None:
            # DeviceSN 不匹配时记录一下，便于诊断
            sns = [e.get("DeviceSN") for e in entries if isinstance(e, dict)]
            self._q.put(("log",
                         f"未找到 DeviceSN={DEVICE_SN}，报文中有: {sns}", "warn"))
            return

        rows = entry.get("TagData") or []
        row  = rows[-1] if rows else {}
        regs = {k: v for k, v in row.items() if k.startswith("MW")}
        if regs:
            self._q.put(("data", regs, ""))
        else:
            self._q.put(("log", f"TagData 中无 MW 寄存器: {row}", "warn"))


# ══════════════════════════════════════════════════════════════
# 配色
# ══════════════════════════════════════════════════════════════
BG   = "#0d1117"
SURF = "#161b22"
ELEV = "#1c2333"
BORD = "#30363d"
TEXT = "#e6edf3"
DIM  = "#8b949e"
BLUE = "#388bfd"
GRN  = "#3fb950"
ORG  = "#d29922"
RED  = "#f85149"
CYN  = "#39d4d4"
PRP  = "#bc8cff"


# ══════════════════════════════════════════════════════════════
# 主窗口
# ══════════════════════════════════════════════════════════════
class Remote(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("PLC 遥控器")
        self.configure(bg=BG)
        self.resizable(True, True)
        self.minsize(420, 560)
        sw, sh = self.winfo_screenwidth(), self.winfo_screenheight()
        self.geometry(f"460x640+{(sw - 460) // 2}+{(sh - 640) // 2}")

        self._regs: dict = {}           # 持久寄存器状态（合并更新）
        self._q: queue.Queue = queue.Queue()

        self._build_ui()

        self._mqtt = MqttWorker(self._q)
        self._mqtt.start()

        self.after(60, self._poll)
        self.protocol("WM_DELETE_WINDOW", self._quit)

    # ──────────────────────────────────────────────────────────
    # 构建界面
    # ──────────────────────────────────────────────────────────
    def _build_ui(self):
        fN = tkfont.Font(family="微软雅黑", size=9)
        fB = tkfont.Font(family="微软雅黑", size=10, weight="bold")
        fH = tkfont.Font(family="微软雅黑", size=11, weight="bold")
        fT = tkfont.Font(family="Consolas",  size=40, weight="bold")
        fC = tkfont.Font(family="Consolas",  size=12, weight="bold")
        fS = tkfont.Font(family="Consolas",  size=8)

        self.grid_columnconfigure(0, weight=1)
        self.grid_rowconfigure(4, weight=1)   # 日志行随窗口拉伸

        # ── 顶栏 ──────────────────────────────────────────────
        bar = tk.Frame(self, bg=SURF, height=38)
        bar.grid(row=0, column=0, sticky="ew")
        bar.pack_propagate(False)
        tk.Label(bar, text="⚡  PLC 遥控器",
                 bg=SURF, fg=TEXT, font=fH).pack(side="left", padx=12)
        self._lbl_conn = tk.Label(bar, text="连接中…",
                                   bg=SURF, fg=ORG, font=fN)
        self._lbl_conn.pack(side="right", padx=12)

        # ── 温度卡片 ──────────────────────────────────────────
        tc = self._card(1)
        tk.Label(tc, text="当前温度", bg=SURF, fg=DIM, font=fN).pack(pady=(8, 0))
        tr = tk.Frame(tc, bg=SURF)
        tr.pack()
        self._lbl_temp = tk.Label(tr, text="--.-",
                                   bg=SURF, fg=BLUE, font=fT)
        self._lbl_temp.pack(side="left")
        tk.Label(tr, text="°C", bg=SURF, fg=DIM,
                 font=tkfont.Font(family="微软雅黑", size=14)).pack(
            side="left", anchor="s", padx=(3, 0), pady=(0, 8))
        self._lbl_upd = tk.Label(tc, text="等待数据…",
                                  bg=SURF, fg=DIM, font=fN)
        self._lbl_upd.pack(pady=(0, 8))

        # ── 三色状态指示 ──────────────────────────────────────
        sf = tk.Frame(self, bg=BG)
        sf.grid(row=2, column=0, sticky="ew", padx=14, pady=3)
        sf.columnconfigure((0, 1, 2), weight=1)

        def indicator(col, title):
            f = tk.Frame(sf, bg=SURF,
                         highlightbackground=BORD, highlightthickness=1)
            f.grid(row=0, column=col, sticky="nsew",
                   padx=(0, 5 if col < 2 else 0))
            tk.Label(f, text=title, bg=SURF, fg=DIM, font=fN).pack(pady=(6, 1))
            dot = tk.Label(f, text="●", bg=SURF, fg=DIM,
                           font=tkfont.Font(family="微软雅黑",
                                            size=13, weight="bold"))
            dot.pack()
            val = tk.Label(f, text="--", bg=SURF, fg=DIM, font=fN)
            val.pack(pady=(1, 6))
            return dot, val

        self._dot_auto,   self._val_auto   = indicator(0, "自动模式")
        self._dot_manual, self._val_manual = indicator(1, "手动模式")
        self._dot_cool,   self._val_cool   = indicator(2, "散热输出")

        # ── 控制区（按钮 + 寄存器格） ─────────────────────────
        mid = self._card(3)
        mid.columnconfigure(0, weight=1)

        tk.Label(mid, text="指令控制",
                 bg=SURF, fg=DIM, font=fN).pack(anchor="w", padx=12, pady=(8, 4))

        bg_f = tk.Frame(mid, bg=SURF)
        bg_f.pack(fill="x", padx=12)
        bg_f.columnconfigure((0, 1), weight=1)

        def btn(text, color, cmd, row, col):
            b = tk.Button(bg_f, text=text, bg=ELEV, fg=color,
                          activebackground="#21262d", activeforeground=color,
                          font=fB, relief="flat", bd=0,
                          highlightbackground=color, highlightthickness=1,
                          cursor="hand2", command=cmd, pady=10)
            b.grid(row=row, column=col, sticky="nsew",
                   padx=(0, 6 if col == 0 else 0), pady=(0, 6))
            b.bind("<Enter>", lambda e: b.config(bg="#21262d"))
            b.bind("<Leave>", lambda e: b.config(bg=ELEV))

        btn("🤖  自动模式", GRN, self._auto,     0, 0)
        btn("🖐  手动模式", ORG, self._manual,   0, 1)
        btn("❄  开启散热", CYN, self._cool_on,  1, 0)
        btn("🔥  关闭散热", RED, self._cool_off, 1, 1)

        # 分隔线
        tk.Frame(mid, bg=BORD, height=1).pack(fill="x", padx=12, pady=(8, 5))

        # 寄存器实时值
        tk.Label(mid, text="寄存器实时值",
                 bg=SURF, fg=DIM, font=fN).pack(anchor="w", padx=12, pady=(0, 4))
        rg = tk.Frame(mid, bg=SURF)
        rg.pack(fill="x", padx=12, pady=(0, 10))
        for c in range(4):
            rg.columnconfigure(c, weight=1)

        def reg_cell(col, name, desc):
            cell = tk.Frame(rg, bg=ELEV,
                            highlightbackground=BORD, highlightthickness=1)
            cell.grid(row=0, column=col, sticky="nsew",
                      padx=(0, 5 if col < 3 else 0), ipadx=4, ipady=3)
            tk.Label(cell, text=name, bg=ELEV, fg=BLUE,
                     font=tkfont.Font(family="Consolas", size=9)).pack()
            tk.Label(cell, text=desc, bg=ELEV, fg=DIM,
                     font=tkfont.Font(family="微软雅黑", size=7)).pack()
            lbl = tk.Label(cell, text="--", bg=ELEV, fg=TEXT, font=fC)
            lbl.pack()
            return lbl

        self._lbl_mw0  = reg_cell(0, "MW0",  "温度")
        self._lbl_mw20 = reg_cell(1, "MW20", "散热")
        self._lbl_mw21 = reg_cell(2, "MW21", "自动")
        self._lbl_mw22 = reg_cell(3, "MW22", "手动")

        # ── 日志 ──────────────────────────────────────────────
        lf = self._card(4, expand=True)
        lf.rowconfigure(1, weight=1)
        lf.columnconfigure(0, weight=1)

        lh = tk.Frame(lf, bg=SURF)
        lh.grid(row=0, column=0, sticky="ew", padx=10, pady=(6, 2))
        tk.Label(lh, text="日志", bg=SURF, fg=DIM, font=fN).pack(side="left")
        tk.Button(lh, text="清空", bg=ELEV, fg=DIM,
                  activebackground="#21262d", font=fN,
                  relief="flat", bd=0, cursor="hand2",
                  command=self._clear_log).pack(side="right")

        li = tk.Frame(lf, bg=ELEV)
        li.grid(row=1, column=0, sticky="nsew", padx=10, pady=(0, 8))
        li.rowconfigure(0, weight=1)
        li.columnconfigure(0, weight=1)

        self._log = tk.Text(li, bg=ELEV, fg=TEXT, font=fS,
                            relief="flat", bd=0, state="disabled",
                            wrap="word", height=5)
        sb = tk.Scrollbar(li, command=self._log.yview,
                          bg=ELEV, troughcolor=ELEV, relief="flat", bd=0)
        self._log.config(yscrollcommand=sb.set)
        sb.grid(row=0, column=1, sticky="ns")
        self._log.grid(row=0, column=0, sticky="nsew", padx=3, pady=3)

        for tag, fg in [("info", DIM), ("ok", GRN), ("warn", ORG),
                        ("err", RED), ("cmd", PRP)]:
            self._log.tag_config(tag, foreground=fg)

    def _card(self, row, expand=False):
        f = tk.Frame(self, bg=SURF,
                     highlightbackground=BORD, highlightthickness=1)
        kw = dict(row=row, column=0, padx=14, pady=(0, 5))
        kw["sticky"] = "nsew" if expand else "ew"
        f.grid(**kw)
        return f

    # ──────────────────────────────────────────────────────────
    # 队列轮询（主线程安全更新 UI）
    # ──────────────────────────────────────────────────────────
    def _poll(self):
        try:
            while True:
                item = self._q.get_nowait()
                kind = item[0]
                if kind == "status":
                    _, text, level = item
                    clr = GRN if level == "ok" else (RED if level == "err" else ORG)
                    self._lbl_conn.config(text=text, fg=clr)
                    self._log_write(text, level)
                elif kind == "log":
                    _, text, level = item
                    self._log_write(text, level)
                elif kind == "data":
                    _, regs, _ = item
                    self._update(regs)
        except queue.Empty:
            pass
        self.after(60, self._poll)

    # ──────────────────────────────────────────────────────────
    # 寄存器更新（合并，不覆盖历史）
    # ──────────────────────────────────────────────────────────
    def _update(self, new: dict):
        self._regs.update(new)      # 关键：合并而非替换
        r = self._regs

        if "MW0" in r:
            self._lbl_temp.config(text=f"{r['MW0'] / 100:.2f}")
            self._lbl_upd.config(text=f"更新 {time.strftime('%H:%M:%S')}")

        for name, lbl in [("MW0",  self._lbl_mw0),
                           ("MW20", self._lbl_mw20),
                           ("MW21", self._lbl_mw21),
                           ("MW22", self._lbl_mw22)]:
            lbl.config(text=str(r[name]) if name in r else "--")

        auto   = bool(r.get("MW21", 0) & 1)
        manual = bool(r.get("MW22", 0) & 1)
        cool   = bool(r.get("MW20", 0) & 1)

        self._dot_auto.config(fg=GRN if auto else DIM)
        self._val_auto.config(text="开启" if auto else "关闭",
                              fg=GRN if auto else DIM)
        self._dot_manual.config(fg=ORG if manual else DIM)
        self._val_manual.config(text="开启" if manual else "关闭",
                                fg=ORG if manual else DIM)
        self._dot_cool.config(fg=CYN if cool else DIM)
        self._val_cool.config(text="运行" if cool else "停止",
                              fg=CYN if cool else DIM)

    # ──────────────────────────────────────────────────────────
    # 指令
    # ──────────────────────────────────────────────────────────
    def _auto(self):
        self._mqtt.send({"MW21": 1, "MW22": 0})

    def _manual(self):
        self._mqtt.send({"MW21": 0, "MW22": 1})

    def _cool_on(self):
        if self._regs and not (self._regs.get("MW22", 0) & 1):
            self._log_write("⚠ 建议先切手动模式再开散热", "warn")
        self._mqtt.send({"MW20": 1})

    def _cool_off(self):
        self._mqtt.send({"MW20": 0})

    # ──────────────────────────────────────────────────────────
    # 日志
    # ──────────────────────────────────────────────────────────
    def _log_write(self, msg: str, level: str = "info"):
        ts = time.strftime("%H:%M:%S")
        self._log.config(state="normal")
        self._log.insert("end", f"{ts}  ", "info")
        self._log.insert("end", f"{msg}\n", level)
        self._log.config(state="disabled")
        self._log.see("end")
        if int(self._log.index("end-1c").split(".")[0]) > 300:
            self._log.config(state="normal")
            self._log.delete("1.0", "20.0")
            self._log.config(state="disabled")

    def _clear_log(self):
        self._log.config(state="normal")
        self._log.delete("1.0", "end")
        self._log.config(state="disabled")

    def _quit(self):
        self._mqtt.stop()
        self.destroy()


if __name__ == "__main__":
    Remote().mainloop()
