"""真实页面验证：首页一个输入框，说设备动作应当出确认卡片。

只用于联调自测。
"""

import base64
import json
import time
import urllib.request

import websocket

PORT = 9334
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


ws = websocket.create_connection(find_target(), timeout=180, suppress_origin=True)
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


def set_input(selector, value):
    return evaluate("""
(() => {
  const el = document.querySelector(%s);
  if (!el) return false;
  const proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
  const setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
  setter.call(el, %s);
  el.dispatchEvent(new Event('input', { bubbles: true }));
  return true;
})()
""" % (json.dumps(selector), json.dumps(value)))


send("Page.enable")
send("Runtime.enable")
send("Page.navigate", {"url": "http://127.0.0.1:5173/#/login"})
time.sleep(6)

evaluate("localStorage.clear()")
send("Page.navigate", {"url": "http://127.0.0.1:5173/#/login"})
time.sleep(5)

print("login page:", evaluate("document.querySelectorAll('input').length"))
set_input("input[placeholder='用户名']", "admin")
set_input("input[placeholder='密码']", "123456")
time.sleep(0.4)
evaluate("document.querySelector('.submit-btn')?.click()")
time.sleep(8)
print("url after login:", evaluate("location.hash"))

# 首页进入 AI 对话
evaluate("""
(() => {
  const target = [...document.querySelectorAll('button')].find(b => /AI|助手|对话|流程/.test(b.textContent || ''));
  if (target) target.click();
  return target ? target.textContent.trim() : null;
})()
""")
time.sleep(3)
print("chat open:", evaluate("!!document.querySelector('.agent-workspace')"))
print("composer:", evaluate("!!document.querySelector('.agent-chat-composer textarea')"))
# shot("ui-unified-01-home")


# ---- 第二步：说一句设备动作，等卡片 ----
evaluate("""
(() => {
  const el = document.querySelector('.agent-chat-composer textarea');
  if (!el) return false;
  const setter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set;
  setter.call(el, '让反应釜3加热到200度，加热2分钟');
  el.dispatchEvent(new Event('input', { bubbles: true }));
  return true;
})()
""")
time.sleep(0.6)
evaluate("document.querySelector('.btn-send-round')?.click()")

for _ in range(45):
    time.sleep(2)
    if evaluate("!!document.querySelector('.confirm-card')"):
        break

print("card:", evaluate("!!document.querySelector('.confirm-card')"))
print("state:", evaluate("document.querySelector('.confirm-card .state-pill')?.textContent.trim()"))
print("title:", evaluate("document.querySelector('.confirm-card .card-title')?.textContent.trim()"))
shot("ui-unified-02-card")

evaluate("document.querySelector('.confirm-card .btn-solid')?.click()")
for _ in range(40):
    time.sleep(1)
    state = evaluate("document.querySelector('.confirm-card .state-pill')?.textContent.trim()")
    if state and state != '待确认':
        break
print("state after confirm:", evaluate("document.querySelector('.confirm-card .state-pill')?.textContent.trim()"))
shot("ui-unified-03-done")
ws.close()
