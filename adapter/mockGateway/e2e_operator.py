"""端到端验证：预演 → 确认 → 执行 → 回读状态。

只用于联调自测，不参与生产逻辑。
"""

import json
import sys
import time
import urllib.request

import psycopg2

BASE = "http://127.0.0.1:8080"
DSN = dict(host="101.42.50.179", port=5432, dbname="lab", user="user", password="123456", connect_timeout=8)

opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def call(path, payload=None, token=None, method="POST"):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(BASE + path, data=data, method=method)
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    with opener.open(request, timeout=180) as response:
        return json.loads(response.read().decode("utf-8"))


def login():
    result = call("/api/user/login", {"userName": "admin", "password": "123456"})
    return result["data"]["token"]


def state_of(instance_id):
    conn = psycopg2.connect(**DSN)
    try:
        cur = conn.cursor()
        cur.execute('select online_status, current_cmd_state from "DEVICE_TWIN_STATES" where instance_id=%s',
                    (instance_id,))
        return cur.fetchone()
    finally:
        conn.close()


def show(result):
    print("success:", result.get("success"), "| message:", result.get("message"))
    data = result.get("data") or {}
    if isinstance(data, dict):
        for key in ("sessionId", "token", "trace", "status", "detail"):
            if key in data:
                print(f"  {key}: {data[key]}")
        if data.get("message"):
            print("  reply:", str(data["message"])[:400])
        card = data.get("card")
        if isinstance(card, dict):
            print(f"  card: kind={card.get('kind')} ready={card.get('ready')}")
            for row in card.get("rows") or []:
                print("    row:", row)
            for row in card.get("parameters") or []:
                print("    param:", row)
            for row in card.get("warnings") or []:
                print("    warn:", row)
            for row in card.get("blocking") or []:
                print("    block:", row)


def main():
    token = login()
    message = sys.argv[1] if len(sys.argv) > 1 else "让反应釜1加热到500度，加热30分钟"
    print("=== 1. chat ===")
    chat = call("/api/agent/operator/chat", {"message": message}, token)
    show(chat)
    data = chat.get("data") or {}
    session_id = data.get("sessionId")
    confirm_token = data.get("token")
    if not confirm_token:
        print("=== 没有待确认动作，结束 ===")
        return

    print("\n=== 2. 执行前状态 ===")
    print("  twin:", state_of(220))

    print("\n=== 3. confirm ===")
    confirmed = call("/api/agent/operator/confirm",
                     {"sessionId": session_id, "token": confirm_token}, token)
    show(confirmed)

    print("\n=== 4. 状态回读 ===")
    for _ in range(12):
        current = state_of(220)
        print("  twin:", current)
        if current and current[1] in ("COMPLETED", "FAILED"):
            break
        time.sleep(1)


if __name__ == "__main__":
    main()
