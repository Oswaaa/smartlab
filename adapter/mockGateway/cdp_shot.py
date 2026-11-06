import base64
import json
import sys
import time
import urllib.request

import websocket

PORT = 9333
URL = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:5173/#/device-instance-management"
OUT = sys.argv[2] if len(sys.argv) > 2 else "D:/smartlab2/Frontend/page-check.png"
WAIT = float(sys.argv[3]) if len(sys.argv) > 3 else 9.0


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


ws = websocket.create_connection(find_target(), timeout=90, suppress_origin=True)
counter = [0]


def send(method, params=None):
    counter[0] += 1
    ws.send(json.dumps({"id": counter[0], "method": method, "params": params or {}}))
    while True:
        message = json.loads(ws.recv())
        if message.get("id") == counter[0]:
            return message


send("Page.enable")
send("Runtime.enable")
send("Console.enable")
send("Page.navigate", {"url": URL})
time.sleep(WAIT)

probe = send("Runtime.evaluate", {
    "expression": """(() => {
      const fab = document.querySelector('.dock-fab');
      const app = document.getElementById('app');
      return JSON.stringify({
        hasApp: !!app,
        appChildren: app ? app.children.length : 0,
        hasFab: !!fab,
        fabText: fab ? fab.textContent.trim() : '',
        hasNav: !!document.querySelector('header, .app-top-nav, nav'),
        bodyText: (document.body.innerText || '').slice(0, 120)
      });
    })()""",
    "returnByValue": True
})
print("PROBE:", probe.get("result", {}).get("result", {}).get("value"))

shot = send("Page.captureScreenshot", {"format": "png"})
data = shot.get("result", {}).get("data")
if not data:
    print("截图失败:", json.dumps(shot)[:300])
    sys.exit(1)
with open(OUT, "wb") as handle:
    handle.write(base64.b64decode(data))
print("saved", OUT, len(base64.b64decode(data)), "bytes")

opened = send("Runtime.evaluate", {
    "expression": """(() => {
      const fab = document.querySelector('.dock-fab');
      if (!fab) return 'no-fab';
      fab.click();
      return 'clicked';
    })()""",
    "returnByValue": True
})
print("CLICK:", opened.get("result", {}).get("result", {}).get("value"))
time.sleep(2.0)

panel = send("Runtime.evaluate", {
    "expression": "!!document.querySelector('.dock-panel')",
    "returnByValue": True
})
print("PANEL:", panel.get("result", {}).get("result", {}).get("value"))

shot2 = send("Page.captureScreenshot", {"format": "png"})
data2 = shot2.get("result", {}).get("data")
if data2:
    out2 = OUT.replace(".png", "-open.png")
    with open(out2, "wb") as handle:
        handle.write(base64.b64decode(data2))
    print("saved", out2, len(base64.b64decode(data2)), "bytes")

ws.close()
