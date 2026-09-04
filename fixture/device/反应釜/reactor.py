"""
Simulated chemical reactor physical device.
Represents real hardware:
- Maintains chamber physical state: Temperature (°C) and Pressure (MPa).
- Temperature has a 30s slow jitter of ±0.01°C; Pressure is strictly stable.
- Only handles hardware reads (returns {temperature, pressure}) and hardware writes (sets target setpoints).
- Does NOT send or know about SmartLab command lifecycle events.
"""

import json
import logging
import random
import socket
import threading
import time
import tkinter as tk
from tkinter import ttk

logger = logging.getLogger("fixture.device.reactor")


class ReactorDeviceApp:
    """
    Physical Reactor Device simulation.
    Only exposes sensor register reading and actuator target setpoints over TCP.
    """

    def __init__(self, root: tk.Tk, host: str = "127.0.0.1", port: int = 9997):
        self.root = root
        self.root.title("反应釜腔体环境模拟器 (Reactor Device)")
        self.root.geometry("500x300")
        self.root.resizable(False, False)

        self.host = host
        self.port = port
        self._running = False
        self._server = None
        self._clients = []
        self._lock = threading.Lock()

        # Chamber internal physical temperature and pressure
        self.current_temp_base = 25.0       # °C
        self.current_pressure_base = 0.10    # MPa

        # Actuator targets (simulating physical heaters / coolers / valves)
        self.target_temp = 25.0
        self.target_pressure = 0.10

        # Temperature 30s slow jitter (30s 变化一次，跳动 0.01)
        self._last_temp_jitter_time = time.time()
        self._temp_jitter_offset = 0.00

        # GUI variables
        self.display_temp_str = tk.StringVar(value=f"{self.current_temp_base:.2f} °C")
        self.display_pressure_str = tk.StringVar(value=f"{self.current_pressure_base:.3f} MPa")
        self.preset_temp_var = tk.DoubleVar(value=self.current_temp_base)
        self.preset_pressure_var = tk.DoubleVar(value=self.current_pressure_base)
        self.status_var = tk.StringVar(value="设备状态: 等待连接 (TCP 9997)...")

        self._build_gui()

        # Start physics simulation loop
        self._running = True
        self._physics_thread = threading.Thread(target=self._physics_simulation_loop, daemon=True)
        self._physics_thread.start()

        # Start TCP server in background
        self._server_thread = threading.Thread(target=self._run_server, daemon=True)
        self._server_thread.start()

        # Start periodic GUI display update
        self._schedule_gui_update()

    def _safe_after(self, callback):
        try:
            if self._running:
                self.root.after(0, callback)
        except Exception:
            pass

    def _build_gui(self):
        main_frame = ttk.Frame(self.root, padding="15")
        main_frame.pack(fill=tk.BOTH, expand=True)

        # Title
        title_label = ttk.Label(main_frame, text="⚗️ 反应釜腔体环境模拟器 (真实设备)", font=("Arial", 13, "bold"))
        title_label.grid(row=0, column=0, columnspan=5, pady=(0, 15))

        # --- Temperature Section ---
        ttk.Label(main_frame, text="当前腔内温度:", font=("Arial", 10)).grid(
            row=1, column=0, sticky=tk.W, pady=8
        )
        self.temp_disp_lbl = ttk.Label(
            main_frame,
            textvariable=self.display_temp_str,
            font=("Arial", 12, "bold"),
            foreground="#d9534f"
        )
        self.temp_disp_lbl.grid(row=1, column=1, sticky=tk.W, padx=(5, 15), pady=8)

        ttk.Label(main_frame, text="预填项 (°C):").grid(row=1, column=2, sticky=tk.W, pady=8)
        temp_entry = ttk.Spinbox(
            main_frame,
            from_=-20.0,
            to=350.0,
            increment=0.5,
            textvariable=self.preset_temp_var,
            width=8
        )
        temp_entry.grid(row=1, column=3, sticky=tk.W, padx=5, pady=8)

        temp_btn = ttk.Button(main_frame, text="改变温度", command=self._apply_temp_change)
        temp_btn.grid(row=1, column=4, padx=5, pady=8)

        # --- Pressure Section ---
        ttk.Label(main_frame, text="当前腔内压力:", font=("Arial", 10)).grid(
            row=2, column=0, sticky=tk.W, pady=8
        )
        self.pressure_disp_lbl = ttk.Label(
            main_frame,
            textvariable=self.display_pressure_str,
            font=("Arial", 12, "bold"),
            foreground="#0275d8"
        )
        self.pressure_disp_lbl.grid(row=2, column=1, sticky=tk.W, padx=(5, 15), pady=8)

        ttk.Label(main_frame, text="预填项 (MPa):").grid(row=2, column=2, sticky=tk.W, pady=8)
        pressure_entry = ttk.Spinbox(
            main_frame,
            from_=0.0,
            to=10.0,
            increment=0.05,
            textvariable=self.preset_pressure_var,
            width=8
        )
        pressure_entry.grid(row=2, column=3, sticky=tk.W, padx=5, pady=8)

        pressure_btn = ttk.Button(main_frame, text="改变压力", command=self._apply_pressure_change)
        pressure_btn.grid(row=2, column=4, padx=5, pady=8)

        # --- Divider & Quick Actions ---
        ttk.Separator(main_frame, orient=tk.HORIZONTAL).grid(
            row=3, column=0, columnspan=5, sticky=tk.EW, pady=12
        )

        action_frame = ttk.Frame(main_frame)
        action_frame.grid(row=4, column=0, columnspan=5, pady=5)
        ttk.Button(action_frame, text="同时改变温度与压力", command=self._apply_both_change).pack(side=tk.LEFT, padx=5)
        ttk.Button(action_frame, text="复位常温常压 (25℃, 0.1MPa)", command=self._reset_defaults).pack(side=tk.LEFT, padx=5)

        # --- Status Info ---
        status_label = ttk.Label(main_frame, textvariable=self.status_var, foreground="#555555", font=("Arial", 9))
        status_label.grid(row=5, column=0, columnspan=5, sticky=tk.W, pady=(15, 0))

    def _apply_temp_change(self):
        """Manually change chamber base temperature using the preset input value."""
        try:
            val = float(self.preset_temp_var.get())
            with self._lock:
                self.current_temp_base = val
                self.target_temp = val
                self._temp_jitter_offset = 0.00
                self._last_temp_jitter_time = time.time()
            self.status_var.set(f"设备状态: 当前温度已改变为 {val:.2f} °C")
        except Exception:
            self.status_var.set("输入格式错误: 请在预填项输入有效温度数字")

    def _apply_pressure_change(self):
        """Manually change chamber base pressure using the preset input value."""
        try:
            val = float(self.preset_pressure_var.get())
            with self._lock:
                self.current_pressure_base = val
                self.target_pressure = val
            self.status_var.set(f"设备状态: 当前压力已改变为 {val:.3f} MPa")
        except Exception:
            self.status_var.set("输入格式错误: 请在预填项输入有效压力数字")

    def _apply_both_change(self):
        self._apply_temp_change()
        self._apply_pressure_change()

    def _reset_defaults(self):
        with self._lock:
            self.preset_temp_var.set(25.0)
            self.preset_pressure_var.set(0.10)
            self.current_temp_base = 25.0
            self.target_temp = 25.0
            self.current_pressure_base = 0.10
            self.target_pressure = 0.10
            self._temp_jitter_offset = 0.00
            self._last_temp_jitter_time = time.time()
        self.status_var.set("设备状态: 已复位为常温常压 (25.0 °C / 0.100 MPa)")

    def _get_sensor_reading(self) -> dict:
        """Read sensor data with 30s slow jitter on temperature and stable pressure."""
        now = time.time()
        with self._lock:
            if now - self._last_temp_jitter_time >= 30.0:
                self._last_temp_jitter_time = now
                self._temp_jitter_offset = random.choice([-0.01, 0.00, 0.01])

            t_val = round(self.current_temp_base + self._temp_jitter_offset, 2)
            p_val = round(self.current_pressure_base, 3)

        return {
            "temperature": t_val,
            "pressure": p_val,
            "timestamp": round(now, 3)
        }

    def _physics_simulation_loop(self):
        """Physical transition simulation (heaters / coolers / valves)."""
        while self._running:
            try:
                with self._lock:
                    # Temperature transition towards actuator target
                    if abs(self.current_temp_base - self.target_temp) > 0.05:
                        diff = self.target_temp - self.current_temp_base
                        step = max(1.5, min(8.0, abs(diff) * 0.6)) * 0.2
                        if diff > 0:
                            self.current_temp_base = min(self.target_temp, self.current_temp_base + step)
                        else:
                            self.current_temp_base = max(self.target_temp, self.current_temp_base - step)

                    # Pressure transition towards actuator target
                    if abs(self.current_pressure_base - self.target_pressure) > 0.005:
                        p_diff = self.target_pressure - self.current_pressure_base
                        p_step = max(0.05, min(0.5, abs(p_diff) * 0.6)) * 0.2
                        if p_diff > 0:
                            self.current_pressure_base = min(self.target_pressure, self.current_pressure_base + p_step)
                        else:
                            self.current_pressure_base = max(self.target_pressure, self.current_pressure_base - p_step)

                time.sleep(0.2)
            except Exception as e:
                logger.error("Physics loop error: %s", e)

    def _schedule_gui_update(self):
        """Periodically update the displayed reading."""
        if self._running:
            reading = self._get_sensor_reading()
            self.display_temp_str.set(f"{reading['temperature']:.2f} °C")
            self.display_pressure_str.set(f"{reading['pressure']:.3f} MPa")
            self.root.after(300, self._schedule_gui_update)

    def _handle_client(self, conn: socket.socket, addr):
        logger.info("Device connection accepted: %s", addr)
        self._safe_after(lambda: self.status_var.set(f"设备状态: 已连接 ({addr[0]}:{addr[1]})"))
        self._clients.append(conn)

        buffer = ""
        try:
            conn.settimeout(1.0)
            while self._running:
                try:
                    data = conn.recv(1024).decode("utf-8")
                    if not data:
                        break
                    buffer += data
                    while "\n" in buffer:
                        line, buffer = buffer.split("\n", 1)
                        if not line.strip():
                            continue
                        try:
                            req = json.loads(line)
                        except json.JSONDecodeError:
                            continue

                        # 1. Active Read Request: {"action": "read"} -> returns sensor reading
                        action = req.get("action", "")
                        if action == "read" or "read" in req:
                            reading = self._get_sensor_reading()
                            conn.sendall((json.dumps(reading) + "\n").encode("utf-8"))

                        # 2. Control/Write Request: {"action": "set", "targetTemperature": ..., "targetPressure": ...}
                        elif action == "set" or "setTemperature" in req or "setPressure" in req or "targetTemperature" in req:
                            with self._lock:
                                if "targetTemperature" in req:
                                    self.target_temp = float(req["targetTemperature"])
                                elif "setTemperature" in req:
                                    self.target_temp = float(req["setTemperature"])

                                if "targetPressure" in req:
                                    self.target_pressure = float(req["targetPressure"])
                                elif "setPressure" in req:
                                    self.target_pressure = float(req["setPressure"])

                            # Simple hardware ACK: {"status": "ok"}
                            conn.sendall((json.dumps({"status": "ok"}) + "\n").encode("utf-8"))

                except socket.timeout:
                    continue
                except OSError:
                    break
        finally:
            if conn in self._clients:
                self._clients.remove(conn)
            try:
                conn.close()
            except Exception:
                pass
            logger.info("Device disconnected: %s", addr)
            if not self._clients:
                self._safe_after(lambda: self.status_var.set("设备状态: 等待连接 (TCP 9997)..."))

    def _run_server(self):
        self._server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self._server.bind((self.host, self.port))
        self._server.listen(5)
        self._server.settimeout(1.0)
        logger.info("Physical Reactor Device listening on %s:%d", self.host, self.port)

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
            try:
                c.close()
            except Exception:
                pass
        self._clients.clear()
        if self._server:
            try:
                self._server.close()
            except Exception:
                pass


def main():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(name)s] %(message)s")
    root = tk.Tk()
    app = ReactorDeviceApp(root)

    def on_closing():
        app.stop()
        root.destroy()

    root.protocol("WM_DELETE_WINDOW", on_closing)
    root.mainloop()


if __name__ == "__main__":
    main()
