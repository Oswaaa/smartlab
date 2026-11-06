import base64
import json
import sys
import time
import urllib.request

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


ws = websocket.create_connection(find_target(), timeout=120, suppress_origin=True)
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
    path = f"{OUT_DIR}/{name}.png"
    with open(path, "wb") as handle:
        handle.write(base64.b64decode(data))
    print("saved", path)


send("Page.enable")
send("Runtime.enable")
send("Page.navigate", {"url": "http://127.0.0.1:5173/#/device-instance-management"})
time.sleep(7)

print("entry:", evaluate("!!document.querySelector('.operator-entry')"))
print("fab_left:", evaluate("!!document.querySelector('.dock-fab')"))
evaluate("document.querySelector('.operator-entry')?.click()")
time.sleep(1.5)
print("panel:", evaluate("!!document.querySelector('.dock-panel')"))
shot("ui-panel-empty")

evaluate("""
(() => {
  const el = document.querySelector('.dock-input');
  const setter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set;
  setter.call(el, '让反应釜1加热到500度');
  el.dispatchEvent(new Event('input', { bubbles: true }));
  return el.value;
})()
""")
time.sleep(0.6)
print("draft:", evaluate("document.querySelector('.dock-input').value"))
evaluate("document.querySelector('.send-btn').click()")

for _ in range(24):
    time.sleep(2)
    if evaluate("!!document.querySelector('.confirm-card')"):
        break

print("card:", evaluate("!!document.querySelector('.confirm-card')"))
print("card_state:", evaluate("document.querySelector('.confirm-card .state-pill')?.textContent.trim()"))
print("confirm_disabled:", evaluate("document.querySelector('.confirm-card .btn-solid')?.disabled"))
shot("ui-panel-card")

text = evaluate("document.querySelector('.dock-body')?.innerText || ''")
print("BODY_TEXT_START")
print((text or "")[:1200])
print("BODY_TEXT_END")
ws.close()
