"""最小 MQTT Mock 网关，只用于联调，不碰 PLC、不碰摄像头。

它顶替一个真实 adapter 的 MQTT 客户端：
  - 每 10 秒发一次心跳，把所有点位报成在线
  - 订阅下行命令，收到后立刻回 COMMAND_RUNNING，2 秒后回 COMMAND_COMPLETED
  - 其余一概不处理（不做遥测、不模拟真实物理过程）

用法：
  python mock_gateway.py                      # 默认 GetPLCDataAdapter
  python mock_gateway.py --adapter GpioValveAdapter-01
  python mock_gateway.py --broker 127.0.0.1 --port 1883 --delay 2
"""

import argparse
import json
import sys
import threading
import time
import uuid

import paho.mqtt.client as mqtt

try:
    import psycopg2
except ImportError:
    psycopg2 = None

DSN = dict(host="101.42.50.179", port=5432, dbname="lab", user="user", password="123456", connect_timeout=8)

COMMAND_TOPIC = "smartlab/adapter/{adapter}/{point}/command"
EVENT_TOPIC = "smartlab/adapter/{adapter}/{point}/event"
HEARTBEAT_TOPIC = "smartlab/adapter/{adapter}/heartbeat"


def load_points(adapter_name, exclude=None):
    """从库里读这台 adapter 负责的点位，避免手写清单和现场不一致。"""
    if psycopg2 is None:
        return []
    conn = psycopg2.connect(**DSN)
    try:
        cur = conn.cursor()
        cur.execute(
            """select bound_device_point from "DEVICE_INSTANCES"
               where bound_adapter_name = %s and bound_device_point is not null
               order by bound_device_point""",
            (adapter_name,),
        )
        points = [row[0] for row in cur.fetchall()]
    finally:
        conn.close()
    if exclude:
        points = [point for point in points if point not in exclude]
    return points


def now_ms():
    return int(time.time() * 1000)


class MockGateway:
    def __init__(self, args):
        self.args = args
        self.points = args.points or load_points(args.adapter, set(args.exclude or []))
        if not self.points:
            print(f"[warn] adapter {args.adapter} 没有任何点位，心跳将没有内容")
        # client_id 必须全局唯一：两个 adapter 同一秒启动时，秒级时间戳会撞车，
        # EMQX 会不停把彼此踢下线，表现为掉线重连循环、命令恰好落在掉线窗口里丢失。
        self.client = mqtt.Client(
            mqtt.CallbackAPIVersion.VERSION2,
            client_id=f"mock-gw-{args.adapter}-{uuid.uuid4().hex[:10]}",
        )
        if args.username:
            self.client.username_pw_set(args.username, args.password)
        self.client.on_connect = self.on_connect
        self.client.on_message = self.on_message
        self.client.on_disconnect = self.on_disconnect
        self._stop = threading.Event()

    def on_connect(self, client, userdata, flags, reason_code, properties=None):
        print(f"[connect] rc={reason_code}")
        if reason_code != 0:
            print("[error] 连接 broker 失败，检查地址与端口")
            return
        topic = COMMAND_TOPIC.format(adapter=self.args.adapter, point="+")
        client.subscribe(topic, qos=1)
        print(f"[subscribe] {topic}")
        self.send_heartbeat()
        threading.Thread(target=self.heartbeat_loop, daemon=True).start()

    def on_disconnect(self, client, userdata, flags, reason_code, properties=None):
        if not self._stop.is_set():
            print(f"[disconnect] rc={reason_code}")

    def on_message(self, client, userdata, message):
        try:
            payload = json.loads(message.payload.decode("utf-8"))
        except Exception as error:
            print(f"[error] 命令报文不是合法 JSON: {error}")
            return
        point = payload.get("devicePoint")
        command = payload.get("commandName")
        message_id = payload.get("messageId")
        print(f"[command] point={point} command={command} messageId={message_id} params={payload.get('parameters')}")
        if not point or not command or not message_id:
            print("[warn] 命令缺少 devicePoint / commandName / messageId，忽略")
            return
        # 看门狗 10 秒，必须立刻回 RUNNING
        self.publish_event(point, "COMMAND_RUNNING", message_id)
        threading.Thread(
            target=self.finish_later, args=(point, message_id), daemon=True
        ).start()

    def finish_later(self, point, message_id):
        time.sleep(max(0.0, self.args.delay))
        self.publish_event(point, "COMMAND_COMPLETED", message_id)

    def publish_event(self, point, event_name, message_id):
        topic = EVENT_TOPIC.format(adapter=self.args.adapter, point=point)
        payload = {
            "timestamp": now_ms(),
            "adapterName": self.args.adapter,
            "devicePoint": point,
            "eventName": event_name,
            "messageId": message_id,
        }
        info = self.client.publish(topic, json.dumps(payload), qos=1)
        print(f"[event] {event_name} -> {topic} rc={info.rc}")

    def send_heartbeat(self):
        if not self.points:
            return
        topic = HEARTBEAT_TOPIC.format(adapter=self.args.adapter)
        payload = {
            "status": "ALIVE",
            "timestamp": now_ms(),
            "points": [{"devicePoint": point, "online": True} for point in self.points],
        }
        info = self.client.publish(topic, json.dumps(payload), qos=1)
        print(f"[heartbeat] {len(self.points)} points rc={info.rc}")

    def heartbeat_loop(self):
        interval = self.args.heartbeat
        while not self._stop.wait(interval):
            self.send_heartbeat()

    def run(self):
        print(f"[start] adapter={self.args.adapter} points={len(self.points)} broker={self.args.broker}:{self.args.port}")
        self.client.connect_async(self.args.broker, self.args.port, keepalive=30)
        self.client.loop_start()
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            print("[stop] 收到中断")
        finally:
            self._stop.set()
            self.client.loop_stop()
            self.client.disconnect()


def main():
    parser = argparse.ArgumentParser(description="最小 MQTT Mock 网关")
    parser.add_argument("--adapter", default="GetPLCDataAdapter")
    parser.add_argument("--broker", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=1883)
    parser.add_argument("--username", default="lab_system", help="broker 账号，本机默认 lab_system")
    parser.add_argument("--password", default="123456")
    parser.add_argument("--heartbeat", type=float, default=10.0)
    parser.add_argument("--delay", type=float, default=2.0, help="收到命令后多久回 COMPLETED")
    parser.add_argument("--points", nargs="*", default=None, help="手动指定点位，默认从数据库读")
    parser.add_argument("--exclude", nargs="*", default=["vision_001"],
                        help="不报在线的点位，默认排除摄像头")
    args = parser.parse_args()
    MockGateway(args).run()


if __name__ == "__main__":
    sys.exit(main())
