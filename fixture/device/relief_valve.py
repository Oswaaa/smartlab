"""
Simulated pressure relief valve device with GUI.
Communicates via a simple JSON-line protocol over a local TCP socket.
Supports receiving 'releasePressure', 'adjustPressure', and 'emergencyVent' commands.
Units: Pressure in MPa, Valve Opening in percentage (0-100%).
"""

import json
import random
import socket
import threading
import time
import logging
import tkinter as tk
from tkinter import ttk

logger = logging.getLogger("fixture.relief_valve")

class ReliefValveApp:
    def __init__(self, root, host="127.0.0.1", port=9998, interval=1.0):
        self.root = root
        self.root.title("模拟智能泄压阀 (SmartLab Fixture)")
        self.root.geometry("420x280")
        self.root.resizable(False, False)
        
        self.host = host
        self.port = port
        self.interval = interval
        self._running = False
        self._server = None
        self._clients = []
        self._command_lock = threading.Lock()

        # Variables for GUI
        self.pressure_var = tk.DoubleVar(value=0.450)  # MPa (4.5 bar)
        self.opening_var = tk.DoubleVar(value=0.0)     # %
        self.status_var = tk.StringVar(value="状态: 等待连接...")

        self._build_gui()
        
        # Start TCP server in background
        self._server_thread = threading.Thread(target=self._run_server, daemon=True)
        self._server_thread.start()

    def _build_gui(self):
        main_frame = ttk.Frame(self.root, padding="16")
        main_frame.pack(fill=tk.BOTH, expand=True)

        # Header Title
        title_label = ttk.Label(main_frame, text="智能泄压阀模拟器 (0-2.5 MPa)", font=("Segoe UI", 13, "bold"))
        title_label.grid(row=0, column=0, columnspan=2, pady=(0, 14), sticky=tk.W)

        # Current Pressure row
        ttk.Label(main_frame, text="管道压力 (MPa):", font=("Segoe UI", 10)).grid(row=1, column=0, sticky=tk.W, pady=6)
        pressure_spin = ttk.Spinbox(
            main_frame,
            from_=0.0,
            to=5.0,
            increment=0.01,
            textvariable=self.pressure_var,
            width=12,
            format="%.3f"
        )
        pressure_spin.grid(row=1, column=1, sticky=tk.E, pady=6)

        # Valve Opening row
        ttk.Label(main_frame, text="阀门开度 (%):", font=("Segoe UI", 10)).grid(row=2, column=0, sticky=tk.W, pady=6)
        opening_spin = ttk.Spinbox(
            main_frame,
            from_=0.0,
            to=100.0,
            increment=5.0,
            textvariable=self.opening_var,
            width=12,
            format="%.1f"
        )
        opening_spin.grid(row=2, column=1, sticky=tk.E, pady=6)

        # Quick Control Buttons Frame
        btn_frame = ttk.Frame(main_frame)
        btn_frame.grid(row=3, column=0, columnspan=2, pady=(12, 4), sticky=tk.EW)
        
        ttk.Button(btn_frame, text="加压至 0.60 MPa", command=lambda: self._gui_adjust_pressure(0.60)).pack(side=tk.LEFT, padx=(0, 4), expand=True, fill=tk.X)
        ttk.Button(btn_frame, text="泄压至 0.20 MPa", command=lambda: self._gui_release_pressure(0.20)).pack(side=tk.LEFT, padx=4, expand=True, fill=tk.X)
        ttk.Button(btn_frame, text="常压保压 (0.10 MPa)", command=lambda: self._gui_release_pressure(0.10)).pack(side=tk.LEFT, padx=(4, 0), expand=True, fill=tk.X)

        # Status info
        status_label = ttk.Label(main_frame, textvariable=self.status_var, foreground="#475569", font=("Segoe UI", 9))
        status_label.grid(row=4, column=0, columnspan=2, sticky=tk.W, pady=(14, 0))

    def _gui_adjust_pressure(self, target):
        threading.Thread(target=self._execute_adjust, args=(None, target, 4, "gui_manual"), daemon=True).start()

    def _gui_release_pressure(self, target):
        threading.Thread(target=self._execute_release, args=(None, target, 4, "gui_manual"), daemon=True).start()

    def _generate_reading(self):
        base_press = self.pressure_var.get()
        # Add slight physical white noise (+/- 0.002 MPa)
        jitter = random.uniform(-0.002, 0.002)
        current_p = max(0.0, round(base_press + jitter, 4))
        return {
            "pressure": current_p,
            "valveOpening": round(self.opening_var.get(), 1),
            "timestamp": time.time()
        }
        
    def _send_to_conn(self, conn, payload):
        if not conn:
            return
        try:
            conn.sendall((json.dumps(payload) + "\n").encode())
        except Exception:
            pass

    def _broadcast_event(self, event_name, mid=""):
        payload = {"event": event_name, "messageId": mid}
        for c in list(self._clients):
            self._send_to_conn(c, payload)

    def _telemetry_loop(self, conn):
        try:
            while self._running and conn in self._clients:
                reading = self._generate_reading()
                self._send_to_conn(conn, reading)
                time.sleep(self.interval)
        except Exception:
            pass

    def _execute_release(self, conn, target, duration, mid):
        with self._command_lock:
            start_p = self.pressure_var.get()
            self.root.after(0, lambda: self.status_var.set(f"状态: 正在泄压至 {target:.3f} MPa (预计{duration}秒)"))
            self._broadcast_event("COMMAND_RUNNING", mid)
            self._broadcast_event("RELIEF_STARTED", mid)

            # 动态计算阀门开度 (目标压差越大，开度越大，如 40% ~ 80%)
            diff = start_p - target
            opening = min(100.0, max(20.0, diff * 150.0))
            self.root.after(0, lambda: self.opening_var.set(opening))

            steps = max(1, duration * 2)
            step_decrement = diff / steps

            for _ in range(steps):
                current = self.pressure_var.get()
                if diff > 0 and current <= target:
                    break
                if diff < 0 and current >= target:
                    break
                new_val = max(0.0, current - step_decrement)
                self.root.after(0, lambda v=new_val: self.pressure_var.set(round(v, 4)))
                time.sleep(0.5)

            # 泄压完成，关闭阀门并锁定目标压力
            self.root.after(0, lambda: self.pressure_var.set(round(target, 4)))
            self.root.after(0, lambda: self.opening_var.set(0.0))
            self._broadcast_event("TARGET_PRESSURE_REACHED", mid)
            self._broadcast_event("PRESSURE_NORMAL", mid)
            self._broadcast_event("COMMAND_COMPLETED", mid)
            self.root.after(0, lambda: self.status_var.set(f"状态: 泄压完成，当前压力 {target:.3f} MPa"))

    def _execute_adjust(self, conn, target, duration, mid):
        with self._command_lock:
            start_p = self.pressure_var.get()
            self.root.after(0, lambda: self.status_var.set(f"状态: 正在调压至 {target:.3f} MPa (预计{duration}秒)"))
            self._broadcast_event("COMMAND_RUNNING", mid)

            # 调压微调，小开度 15%
            self.root.after(0, lambda: self.opening_var.set(15.0))
            diff = target - start_p
            steps = max(1, duration * 2)
            step_increment = diff / steps

            for _ in range(steps):
                current = self.pressure_var.get()
                if diff > 0 and current >= target:
                    break
                if diff < 0 and current <= target:
                    break
                new_val = max(0.0, current + step_increment)
                self.root.after(0, lambda v=new_val: self.pressure_var.set(round(v, 4)))
                time.sleep(0.5)

            self.root.after(0, lambda: self.pressure_var.set(round(target, 4)))
            self.root.after(0, lambda: self.opening_var.set(0.0))
            self._broadcast_event("PRESSURE_ADJUSTED", mid)
            self._broadcast_event("PRESSURE_NORMAL", mid)
            self._broadcast_event("COMMAND_COMPLETED", mid)
            self.root.after(0, lambda: self.status_var.set(f"状态: 调压完成，当前压力 {target:.3f} MPa"))

    def _execute_emergency_vent(self, conn, duration, mid):
        with self._command_lock:
            self.root.after(0, lambda: self.status_var.set("状态: ⚠️ 紧急全开快速泄压中！"))
            self._broadcast_event("COMMAND_RUNNING", mid)
            self._broadcast_event("EMERGENCY_VENT_TRIGGERED", mid)

            # 阀门 100% 全开
            self.root.after(0, lambda: self.opening_var.set(100.0))
            steps = max(1, duration * 4)
            start_p = self.pressure_var.get()
            target_p = 0.101  # 大气压约 0.101 MPa
            diff = max(0.0, start_p - target_p)
            step_dec = diff / steps

            for _ in range(steps):
                current = self.pressure_var.get()
                if current <= target_p:
                    break
                new_val = max(target_p, current - step_dec)
                self.root.after(0, lambda v=new_val: self.pressure_var.set(round(v, 4)))
                time.sleep(0.25)

            self.root.after(0, lambda: self.pressure_var.set(target_p))
            self.root.after(0, lambda: self.opening_var.set(0.0))
            self._broadcast_event("TARGET_PRESSURE_REACHED", mid)
            self._broadcast_event("COMMAND_COMPLETED", mid)
            self.root.after(0, lambda: self.status_var.set("状态: 紧急排气完毕，已恢复常压"))

    def _handle_client(self, conn, addr):
        logger.info("Relief valve client connected: %s", addr)
        self.root.after(0, lambda: self.status_var.set(f"状态: 已连接 Adapter {addr[0]}:{addr[1]}"))
        self._clients.append(conn)
        
        # Start telemetry loop
        t = threading.Thread(target=self._telemetry_loop, args=(conn,), daemon=True)
        t.start()
        
        try:
            conn.settimeout(1.0)
            buffer = ""
            while self._running:
                try:
                    data = conn.recv(1024).decode()
                    if not data:
                        break
                    buffer += data
                    while "\n" in buffer:
                        line, buffer = buffer.split("\n", 1)
                        if not line.strip(): continue
                        cmd_data = json.loads(line)
                        if "command" in cmd_data:
                            cmd = cmd_data["command"]
                            mid = cmd_data.get("messageId", "")
                            self._send_to_conn(conn, {"event": "COMMAND_RECEIVED", "messageId": mid})
                            if cmd in ("releasePressure", "relieve"):
                                target = float(cmd_data.get("targetPressure", 0.20))
                                duration = int(cmd_data.get("releaseDurationSec", 4))
                                threading.Thread(target=self._execute_release, args=(conn, target, duration, mid), daemon=True).start()
                            elif cmd in ("adjustPressure", "pressurize"):
                                target = float(cmd_data.get("targetPressure", 0.40))
                                duration = int(cmd_data.get("durationSec", 4))
                                threading.Thread(target=self._execute_adjust, args=(conn, target, duration, mid), daemon=True).start()
                            elif cmd in ("emergencyVent", "emergency_vent"):
                                duration = int(cmd_data.get("durationSec", 3))
                                threading.Thread(target=self._execute_emergency_vent, args=(conn, duration, mid), daemon=True).start()
                except socket.timeout:
                    continue
                except json.JSONDecodeError:
                    continue
        except OSError:
            pass
        finally:
            if conn in self._clients:
                self._clients.remove(conn)
            conn.close()
            logger.info("Relief valve client disconnected: %s", addr)
            if not self._clients:
                self.root.after(0, lambda: self.status_var.set("状态: 等待连接..."))

    def _run_server(self):
        self._running = True
        self._server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self._server.bind((self.host, self.port))
        self._server.listen(5)
        self._server.settimeout(1.0)
        logger.info("Relief valve server listening on %s:%d", self.host, self.port)
        
        while self._running:
            try:
                conn, addr = self._server.accept()
                t = threading.Thread(target=self._handle_client, args=(conn, addr), daemon=True)
                t.start()
            except socket.timeout:
                continue
            except OSError:
                break
        self._server.close()

    def stop(self):
        self._running = False
        for c in self._clients:
            try: c.close()
            except: pass
        self._clients.clear()

def main():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(name)s] %(message)s")
    root = tk.Tk()
    app = ReliefValveApp(root, port=9998)
    try:
        root.mainloop()
    finally:
        app.stop()

if __name__ == "__main__":
    main()
