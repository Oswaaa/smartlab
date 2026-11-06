import base64
import json
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
    raise RuntimeError("no target")


ws = websocket.create_connection(find_target(), timeout=150, suppress_origin=True)
counter = [0]


def send(method, params=None):
    counter[0] += 1
    ws.send(json.dumps({"id": counter[0], "method": method, "params": params or {}}))
    while True:
        message = json.loads(ws.recv())
        if message.get("id") == counter[0]:
            return message


def evaluate(expr):
    r = send("Runtime.evaluate", {"expression": expr, "returnByValue": True, "awaitPromise": True})
    return r.get("result", {}).get("result", {}).get("value")


def shot(name):
    data = send("Page.captureScreenshot", {"format": "png"}).get("result", {}).get("data")
    if data:
        path = f"{OUT_DIR}/{name}.png"
        with open(path, "wb") as handle:
            handle.write(base64.b64decode(data))
        print("saved", path)


send("Page.enable")
send("Runtime.enable")
send("Page.navigate", {"url": "http://127.0.0.1:5173/#/device-instance-management"})
time.sleep(7)

evaluate("localStorage.removeItem('smartlab_operator_admin')")
evaluate("location.reload()")
time.sleep(6)

evaluate("document.querySelector('.operator-entry')?.click()")
time.sleep(1.5)

evaluate("""
(() => {
  const el = document.querySelector('.dock-input');
  const setter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set;
  setter.call(el, '让反应釜3加热到200度，加热2分钟');
  el.dispatchEvent(new Event('input', { bubbles: true }));
  return el.value;
})()
""")
time.sleep(1.0)
print("send disabled:", evaluate("document.querySelector('.send-btn').disabled"))
evaluate("document.querySelector('.send-btn').click()")

for _ in range(30):
    time.sleep(2)
    if evaluate("!!document.querySelector('.confirm-card .btn-solid')"):
        break

print("card ready:", evaluate("!!document.querySelector('.confirm-card')"))
print("confirm disabled:", evaluate("document.querySelector('.confirm-card .btn-solid')?.disabled"))

evaluate("document.querySelector('.confirm-card .btn-solid').click()")

for _ in range(15):
    time.sleep(1.5)
    state = evaluate("document.querySelector('.confirm-card .state-pill')?.textContent.trim()")
    if state and state not in ("待确认", "执行中"):
        break

print("final state:", evaluate("document.querySelector('.confirm-card .state-pill')?.textContent.trim()"))
print("result line:", evaluate("document.querySelector('.confirm-card .result-line')?.textContent.trim()"))
print("buttons left:", evaluate("!!document.querySelector('.confirm-card .btn-solid')"))
shot("ui-panel-done")
ws.close()
