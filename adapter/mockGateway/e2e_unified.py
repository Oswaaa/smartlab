"""统一入口自测：同一个接口，说设备动作出卡片，说设计流程出草稿。

只用于联调，不参与生产逻辑。
"""

import json
import sys
import urllib.request

BASE = "http://127.0.0.1:8080"
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def call(path, payload=None, token=None):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(BASE + path, data=data, method="POST")
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    with opener.open(request, timeout=600) as response:
        return json.loads(response.read().decode("utf-8"))


def login():
    return call("/api/user/login", {"userName": "admin", "password": "123456"})["data"]["token"]


def show(tag, body):
    data = body.get("data") or {}
    print(f"--- {tag} success={body.get('success')} msg={body.get('message')}")
    print("    sessionId:", data.get("sessionId"))
    print("    card:", "有" if data.get("card") else "无",
          "| title:", (data.get("card") or {}).get("title"))
    print("    flowModelId:", data.get("flowModelId"), "| status:", data.get("status"))
    print("    summary:", (data.get("summary") or "")[:160])
    tools = [item for item in (data.get("trace") or [])]
    print("    trace:", tools)
    return data


def main():
    token = login()
    print("login ok")

    # 1. 设备动作：应当出卡片，绝不该出草稿
    body = call("/api/agent/workflow/generate",
                {"prompt": "让反应釜1加热到500度，加热30分钟"}, token)
    data = show("设备动作", body)
    if not data.get("card"):
        print("!! 失败：设备动作没有出确认卡片")
        return 1
    if data.get("flowModelId"):
        print("!! 失败：设备动作居然存了草稿")
        return 1
    if data["card"].get("kind") != "DEVICE_ACTION":
        print("!! 失败：卡片类型不对", data["card"].get("kind"))
        return 1

    # 2. 确认：真正下发
    confirmed = call("/api/agent/operator/confirm",
                     {"sessionId": data["sessionId"], "token": data["token"]}, token)
    print("--- 确认 success=%s status=%s msg=%s" % (
        confirmed.get("success"), (confirmed.get("data") or {}).get("status"),
        (confirmed.get("data") or {}).get("message")))
    return 0


if __name__ == "__main__":
    sys.exit(main())
