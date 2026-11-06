"""验证建任务链路：认流程 → 绑定推导 → 预检。"""

import json
import urllib.request

BASE = "http://127.0.0.1:8080"
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def call(path, payload, token):
    request = urllib.request.Request(BASE + path, data=json.dumps(payload).encode("utf-8"), method="POST")
    request.add_header("Content-Type", "application/json")
    request.add_header("Authorization", "Bearer " + token)
    with opener.open(request, timeout=180) as response:
        return json.loads(response.read().decode("utf-8"))


def show(result, label):
    print(f"\n=== {label} ===")
    data = result.get("data") or {}
    print("success:", result.get("success"), "|", result.get("message"))
    print("trace:", data.get("trace"))
    print("token:", data.get("token"))
    print("reply:", (data.get("message") or "")[:800])
    card = data.get("card")
    if isinstance(card, dict):
        print("card kind:", card.get("kind"), "ready:", card.get("ready"))
        for row in card.get("rows") or []:
            print("  row:", row)
        bindings = card.get("bindings") or []
        for row in bindings[:8]:
            print("  bind:", row)
        if len(bindings) > 8:
            print(f"  ... 共 {len(bindings)} 条绑定")
        for row in card.get("warnings") or []:
            print("  warn:", row)
        for row in card.get("blocking") or []:
            print("  block:", row)
    return data


def main():
    login = urllib.request.Request(BASE + "/api/user/login",
                                   data=json.dumps({"userName": "admin", "password": "123456"}).encode(),
                                   method="POST")
    login.add_header("Content-Type", "application/json")
    with opener.open(login, timeout=30) as response:
        token = json.loads(response.read().decode())["data"]["token"]

    first = call("/api/agent/operator/chat",
                 {"message": "用流程「将1#通道气密连接器打开」建一个任务，任务名叫测试任务A"}, token)
    data = show(first, "第一句：只给流程名和任务名")
    session_id = data.get("sessionId")

    if data.get("token"):
        print("\n已有待确认动作，跳过补充绑定步骤")
        return

    second = call("/api/agent/operator/chat",
                  {"sessionId": session_id,
                   "message": "气密连接器用气密连接器#1，三轴机械臂就用三轴机械臂"}, token)
    data2 = show(second, "第二句：补充设备绑定")

    confirm_token = data2.get("token") or data.get("token")
    if not confirm_token:
        print("\n没有待确认动作，跳过 confirm")
        return
    third = call("/api/agent/operator/confirm",
                 {"sessionId": session_id, "token": confirm_token}, token)
    show(third, "第三步：确认创建")


if __name__ == "__main__":
    main()
