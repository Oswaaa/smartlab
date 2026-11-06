"""真实页面验证（第二步）：在首页输入框里说设备动作，应出确认卡片并可点确认。"""

import base64
import json
import time

import websocket

PORT = 9333
OUT_DIR = "D:/smartlab2/Frontend"


def find_target():
    for _ in range(40):
        try:
            data = json.load(urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json", timeout=3))
            for item in data:
                if item.get("type") == "page":
                    return item["webSocketDebuggerUrl"]
        except Exception:
            pass
        time.sleep(0.5)
    raise RuntimeError("找不到可调试页面")


ws = websocket.create_connection(find_target(), timeout=240, suppress_origin=True)
counter = [0]


def send(method, params=None):
    counter[0] += 1
    ws.send(json.dumps({"id": counter[0], "method": method, "params": params or {}}))
    while True:
        message = json.loads(ws.recv())
        if message.get("id") == counter[0]:
            return message


def evaluate(expr):
    result = send("Runtime.evaluate", {"expression": expr, "returnByValue": True, "awaitPromise": True})
    return result.get("result", {}).get("result", {}).get("value")


def shot(name):
    data = send("Page.captureScreenshot", {"format": "png"}).get("result", {}).get("data")
    if not data:
        print("截图失败", name)
        return
    with open(f"{OUT_DIR}/{name}.png", "wb") as handle:
        handle.write(base64.b64decode(data))
    print("saved", name)


send("Page.enable")
send("Runtime.enable")

ok = evaluate("""
(() => {
  const el = document.querySelector('.agent-chat-composer textarea');
  if (!el) return false;
  const setter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set;
  setter.call(el, '让反应釜3加热到200度，加热2分钟');
  el.dispatchEvent(new Event('input', { bubbles: true }));
  return true;
})()
""")
print("filled:", ok)
time.sleep(0.6)
evaluate("document.querySelector('.btn-send-round')?.click()")

for _ in range(40):
    time.sleep(2)
    if evaluate("!!document.querySelector('.confirm-card')"):
        break

print("card:", evaluate("!!document.querySelector('.confirm-card')"))
print("state:", evaluate("document.querySelector('.confirm-card .state-pill')?.textContent.trim()"))
print("title:", evaluate("document.querySelector('.confirm-card .card-title')?.textContent.trim()"))
print("has draft link:", evaluate("!!document.querySelector('.draft-link')"))
shot("ui-unified-02-card")

# 点确认
evaluate("document.querySelector('.confirm-card .btn-solid')?.click()")
for _ in range(30):
    time.sleep(1)
    state = evaluate("document.querySelector('.confirm-card .state-pill')?.textContent.trim()")
    if state and state != '待确认':
        break
print("state after confirm:", evaluate("document.querySelector('.confirm-card .state-pill')?.textContent.trim()"))
print("result:", evaluate("document.querySelector('.confirm-card .card-result')?.textContent.trim()"))
shot("ui-unified-03-done")
ws.close()
