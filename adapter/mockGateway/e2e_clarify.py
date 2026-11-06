"""验证两个关键行为：设备歧义必须反问；反问后同一会话能接着上下文往下走。"""

import json
import urllib.request

BASE = "http://127.0.0.1:8080"
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def call(path, payload, token=None):
    request = urllib.request.Request(BASE + path, data=json.dumps(payload).encode("utf-8"), method="POST")
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    with opener.open(request, timeout=180) as response:
        return json.loads(response.read().decode("utf-8"))


def main():
    token = call("/api/user/login", {"userName": "admin", "password": "123456"})["data"]["token"]

    print("=== 第一句：模糊设备名 ===")
    first = call("/api/agent/operator/chat", {"message": "让流量计1加热到100度"}, token)
    data = first.get("data") or {}
    session_id = data.get("sessionId")
    print("trace:", data.get("trace"))
    print("token:", data.get("token"))
    print("reply:", (data.get("message") or "")[:500])

    print("\n=== 第二句：同一会话补充设备 ===")
    second = call("/api/agent/operator/chat",
                  {"sessionId": session_id, "message": "用CO流量计1"}, token)
    data2 = second.get("data") or {}
    print("trace:", data2.get("trace"))
    print("token:", data2.get("token"))
    print("reply:", (data2.get("message") or "")[:600])
    card = data2.get("card")
    if card:
        print("card ready:", card.get("ready"))
        for row in card.get("rows") or []:
            print("  row:", row)
        for row in card.get("blocking") or []:
            print("  block:", row)


if __name__ == "__main__":
    main()
