import json
import urllib.request

BASE = "http://127.0.0.1:8080"


def call(path, payload, token=None):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(BASE + path, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=180) as resp:
        return json.loads(resp.read().decode("utf-8"))


login = call("/api/user/login", {"userName": "admin", "password": "123456"})
token = login["data"]["token"]
print("login ok")

r = call("/api/agent/operator/chat", {"sessionId": None, "message": "让反应釜1加热到500度"}, token)
print("success:", r.get("success"))
data = r.get("data") or {}
print("sessionId:", bool(data.get("sessionId")), "token:", (data.get("token") or "")[:8] + "...")
print("message:", (data.get("message") or "")[:80])

card = data.get("card")
if card:
    print("\n--- card 顶层字段 ---")
    for key, value in card.items():
        if isinstance(value, list):
            print(f"  {key}: list({len(value)})")
        else:
            print(f"  {key}: {value}")
    print("\n--- rows[] 样本 ---")
    for row in card.get("rows", [])[:3]:
        print("  ", row)
    print("\n--- parameters[] 全量 ---")
    for row in card.get("parameters", []):
        print("  ", row)
    print("\n--- warnings[] ---")
    for item in card.get("warnings", []):
        print("  ", item)
    print("\n--- blocking[] ---")
    for item in card.get("blocking", []):
        print("  ", item)

r2 = call("/api/agent/operator/cancel", {"sessionId": data.get("sessionId")}, token)
print("\ncancel:", r2.get("data"))
