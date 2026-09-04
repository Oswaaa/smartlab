"""
Simulated temperature sensor device with GUI.
Communicates via a simple JSON-line protocol over a local TCP socket.
Supports receiving 'heat' and 'cool' commands to dynamically adjust temperature.
"""

import json
import random
import socket
import threading
import time
import logging
import tkinter as tk
from tkinter import ttk

logger = logging.getLogger("fixture.device")

class DeviceApp:
    def __init__(self, root, host="127.0.0.1", port=9999, interval=1.0):
        self.root = root
        self.root.title("模拟温度传感器 (SmartLab Fixture)")
        self.root.geometry("350x200")
        
        self.host = host
        self.port = port
        self.interval = interval
        self._running = False
        self._server = None
        self._clients = []
        self._command_lock = threading.Lock()

        # Variables for GUI
        self.temp_var = tk.DoubleVar(value=30.0)

        self._build_gui()
        
        # Start TCP server in background
        self._server_thread = threading.Thread(target=self._run_server, daemon=True)
        self._server_thread.start()

    def _build_gui(self):
        main_frame = ttk.Frame(self.root, padding="20")
        main_frame.pack(fill=tk.BOTH, expand=True)

        ttk.Label(main_frame, text="温度模拟器", font=("Arial", 14, "bold")).grid(row=0, column=0, columnspan=2, pady=(0, 20))

        # Temperature
        ttk.Label(main_frame, text="温度传感器 (°C):").grid(row=1, column=0, sticky=tk.W, pady=5)
        temp_spin = ttk.Spinbox(main_frame, from_=-50.0, to=150.0, increment=0.5, textvariable=self.temp_var, width=10)
        temp_spin.grid(row=1, column=1, sticky=tk.E, pady=5)
        
        # Status info
        self.status_var = tk.StringVar(value="状态: 等待连接...")
        status_label = ttk.Label(main_frame, textvariable=self.status_var, foreground="gray")
        status_label.grid(row=2, column=0, columnspan=2, sticky=tk.W, pady=(20, 0))

    def _generate_reading(self):
        # Read from GUI, add slight random noise
        base_temp = self.temp_var.get()
        return {
            "temperature": round(base_temp + random.uniform(-0.1, 0.1), 2),
            "timestamp": time.time()
        }
        
    def _send_to_conn(self, conn, payload):
        try:
            conn.sendall((json.dumps(payload) + "\n").encode())
        except Exception:
            pass

    def _telemetry_loop(self, conn):
        try:
            while self._running and conn in self._clients:
                reading = self._generate_reading()
                self._send_to_conn(conn, reading)
                time.sleep(self.interval)
        except Exception:
            pass

    def _execute_heat(self, conn, target, duration, mid):
        with self._command_lock:
            self.root.after(0, lambda: self.status_var.set(f"状态: 正在均匀加热至 {target}℃ (预计{duration}秒)"))
            self._send_to_conn(conn, {"event": "COMMAND_RUNNING", "messageId": mid})
            
            start_temp = self.temp_var.get()
            diff = target - start_temp
            
            # 计算每 0.5 秒需要增加的温度
            steps = max(1, duration * 2)
            step_increment = diff / steps
            
            for _ in range(steps):
                current = self.temp_var.get()
                # 防止浮点误差，确保不超过目标温度 (升温或降温)
                if diff > 0 and current >= target:
                    break
                if diff < 0 and current <= target:
                    break
                new_val = current + step_increment
                self.root.after(0, lambda v=new_val: self.temp_var.set(v))
                time.sleep(0.5)
            
            # 确保最终达到精确目标值
            self.root.after(0, lambda: self.temp_var.set(target))
            self._send_to_conn(conn, {"event": "COMMAND_COMPLETED", "messageId": mid})
            self.root.after(0, lambda: self.status_var.set("状态: 加热完成"))

    def _execute_cool(self, conn, duration, mid):
        with self._command_lock:
            self.root.after(0, lambda: self.status_var.set(f"状态: 正在匀速散热 ({duration}秒)"))
            self._send_to_conn(conn, {"event": "COMMAND_RUNNING", "messageId": mid})
            
            steps = duration * 2
            for _ in range(steps): 
                # 每秒降低 0.2 摄氏度，即每 0.5 秒降低 0.1 摄氏度
                current = self.temp_var.get()
                new_val = max(20.0, current - 0.1)
                self.root.after(0, lambda v=new_val: self.temp_var.set(v))
                time.sleep(0.5)
                
            self._send_to_conn(conn, {"event": "COMMAND_COMPLETED", "messageId": mid})
            self.root.after(0, lambda: self.status_var.set("状态: 散热完成"))

    def _handle_client(self, conn, addr):
        logger.info("Device client connected: %s", addr)
        self.root.after(0, lambda: self.status_var.set(f"状态: 已连接 {addr[0]}:{addr[1]}"))
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
                        break # Connection closed
                    buffer += data
                    while "\n" in buffer:
                        line, buffer = buffer.split("\n", 1)
                        if not line.strip(): continue
                        cmd_data = json.loads(line)
                        if "command" in cmd_data:
                            cmd = cmd_data["command"]
                            mid = cmd_data.get("messageId", "")
                            self._send_to_conn(conn, {"event": "COMMAND_RECEIVED", "messageId": mid})
                            if cmd == "heat":
                                target = float(cmd_data.get("targetTemperature", 30))
                                hold = int(cmd_data.get("holdDurationSec", 5))
                                threading.Thread(target=self._execute_heat, args=(conn, target, hold, mid), daemon=True).start()
                            elif cmd == "cool":
                                duration = int(cmd_data.get("durationSec", 5))
                                threading.Thread(target=self._execute_cool, args=(conn, duration, mid), daemon=True).start()
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
            logger.info("Device client disconnected: %s", addr)
            if not self._clients:
                self.root.after(0, lambda: self.status_var.set("状态: 等待连接..."))

    def _run_server(self):
        self._running = True
        self._server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self._server.bind((self.host, self.port))
        self._server.listen(5)
        self._server.settimeout(1.0)
        logger.info("Device server started on %s:%d", self.host, self.port)
        
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
    app = DeviceApp(root)
    
    def on_closing():
        app.stop()
        root.destroy()
        
    root.protocol("WM_DELETE_WINDOW", on_closing)
    root.mainloop()

if __name__ == "__main__":
    main()
