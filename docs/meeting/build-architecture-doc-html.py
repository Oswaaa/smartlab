# -*- coding: utf-8 -*-
"""Render 智能实验室系统技术架构文档.md to a self-contained HTML with drawn diagrams."""
from __future__ import annotations

import html as htmlmod
import re
from pathlib import Path

ROOT = Path(r"D:\SmartLab2.0")
MD_PATH = ROOT / "智能实验室系统技术架构文档.md"
OUT_PATH = ROOT / "docs" / "智能实验室系统技术架构文档.html"

DIAGRAMS = {}


def esc(s: str) -> str:
    return htmlmod.escape(s, quote=False)


def inline(s: str) -> str:
    s = s.replace(r"$\rightarrow$", "→")
    s = re.sub(r"\$([^$]+)\$", r"\1", s)
    s = esc(s)
    s = re.sub(r"`([^`]+)`", r"<code>\1</code>", s)
    s = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", s)
    s = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"<code>\1</code>", s)
    return s


def box(x, y, w, h, title, lines, fill="#fffcf7", stroke="#3b4a86"):
    text = [f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="6" fill="{fill}" stroke="{stroke}" stroke-width="1.4"/>']
    text.append(
        f'<text x="{x + 12}" y="{y + 22}" font-size="13" font-weight="700" fill="#1b2430">{esc(title)}</text>'
    )
    for i, line in enumerate(lines):
        text.append(
            f'<text x="{x + 12}" y="{y + 44 + i * 18}" font-size="12" fill="#3a4350">{esc(line)}</text>'
        )
    return "\n".join(text)


def arrow(x1, y1, x2, y2, label="", color="#5d6670"):
    parts = [
        f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" stroke-width="1.5" marker-end="url(#arrow)"/>'
    ]
    if label:
        mx, my = (x1 + x2) / 2, (y1 + y2) / 2 - 8
        parts.append(
            f'<text x="{mx}" y="{my}" text-anchor="middle" font-size="11" fill="#9a4a12">{esc(label)}</text>'
        )
    return "\n".join(parts)


def svg_wrap(w, h, body, caption):
    return f"""
<div class="diagram">
  <div class="caption">{esc(caption)}</div>
  <svg viewBox="0 0 {w} {h}" width="100%" role="img" aria-label="{esc(caption)}">
    <defs>
      <marker id="arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
        <path d="M0,0 L8,4 L0,8 z" fill="#5d6670"/>
      </marker>
    </defs>
    {body}
  </svg>
</div>
"""


DIAGRAMS[0] = lambda: svg_wrap(1100, 720, f"""
  {box(40, 20, 1020, 70, "前端交互层  Vue 3 + Pinia + Element Plus",
       ["工作流设计器    设备能力 / 状态机编辑器    约束规则配置    人工控制台"], "#e7eaf4")}
  {box(40, 120, 1020, 250, "核心引擎层  Spring Boot 运行时", [], "#fffcf7", "#1b2430")}
  {box(60, 155, 230, 195, "工作流引擎", ["驱动器", "任务控制", "TASK / TASK_STEP", "条件求值"], "#e7eaf4")}
  {box(310, 155, 250, 195, "状态机引擎", ["CMD 指令空间", "OP 运行 / 异常空间", "内置约束监测"], "#dceeea")}
  {box(580, 155, 230, 195, "约束引擎", ["调度协调", "表达式求值", "有效模型编译"], "#f3e6d4")}
  {box(830, 155, 210, 195, "可观测层", ["设备孪生快照", "CMD / OP 快照", "任务 / 节点 / 变量"], "#f3eee4")}
  {box(40, 400, 500, 110, "边缘通信层  MQTT", ["注册 / 心跳 / 命令 / 遥测 / 事件", "报文映射 · Manifest 解析"], "#e7eaf4")}
  {box(560, 400, 500, 110, "物理接入层  Adapter", ["Python / C++ 适配器进程", "PLC / 控制卡 / 传感器 / 仪器"], "#dceeea")}
  {box(40, 540, 1020, 90, "数据存储层  PostgreSQL + JSONB",
       ["DEVICE_*    FLOW_* / TASK_*    CONSTRAINT_RULE / VIOLATION_LOG    ADAPTER_INDEX"], "#f3eee4")}
  {arrow(175, 370, 435, 400, "1 启动 WF_EXECUTE_START")}
  {arrow(435, 350, 290, 400, "2 指令 CMD_START / ABORT")}
  {arrow(810, 350, 810, 400, "3/4 遥测与事件")}
  {arrow(435, 250, 695, 250, "5 状态 CMD_STATE")}
  {arrow(935, 350, 695, 350, "6 观测 → 约束")}
  {arrow(695, 350, 175, 350, "7 联锁 ABORT / PAUSE")}
  {arrow(695, 370, 435, 370, "8 CONSTRAINT_EXECUTE")}
""", "图 1 · 系统架构拓扑")


DIAGRAMS[1] = lambda: svg_wrap(1100, 340, f"""
  {box(430, 20, 240, 70, "协议规范.json", ["类型 · 信号 · MQTT 报文"], "#e7eaf4")}
  {box(40, 150, 180, 70, "设备能力模型", [], "#fffcf7")}
  {box(250, 150, 180, 70, "状态机模型", [], "#fffcf7")}
  {box(460, 150, 180, 70, "工作流模型", [], "#fffcf7")}
  {box(670, 150, 180, 70, "约束模型", [], "#fffcf7")}
  {box(880, 150, 180, 70, "运行时执行", ["Java / Python"], "#dceeea")}
  {box(250, 260, 400, 55, "ProtocolDictionary + SchemaMetadata  →  前端元数据 / 后端校验", [], "#f3eee4")}
  {arrow(550, 90, 130, 150, "$ref")}
  {arrow(550, 90, 340, 150)}
  {arrow(550, 90, 550, 150)}
  {arrow(550, 90, 760, 150)}
  {arrow(450, 205, 450, 260)}
""", "图 2 · 三层规范与 Schema 引用")


DIAGRAMS[2] = lambda: svg_wrap(1100, 280, f"""
  {box(40, 90, 110, 70, "IDLE", ["空闲可接令"], "#dceeea")}
  {box(200, 90, 110, 70, "SENT", ["已下发"], "#e7eaf4")}
  {box(360, 90, 130, 70, "RUNNING", ["物理执行中"], "#e7eaf4")}
  {box(540, 90, 140, 70, "COMPLETED", ["完成 → 复位"], "#dceeea")}
  {box(360, 200, 130, 60, "ABORTING", ["终止中"], "#f3e6d4")}
  {box(540, 200, 140, 60, "ABORTED", ["已终止"], "#f3e6d4")}
  {box(740, 90, 130, 70, "FAILED", ["失败 / 超时"], "#f3dce0")}
  {arrow(150, 125, 200, 125, "WF / 人工 / 约束启动")}
  {arrow(310, 125, 360, 125, "COMMAND_RUNNING")}
  {arrow(490, 125, 540, 125, "COMPLETED")}
  {arrow(425, 160, 425, 200, "ABORT")}
  {arrow(490, 230, 540, 230)}
  {arrow(490, 125, 740, 125, "FAILED")}
  <text x="40" y="40" font-size="14" fill="#5d6670">CMD 指令生命周期（终态后自动回到 IDLE）</text>
""", "图 3 · 设备指令状态机")


DIAGRAMS[3] = lambda: svg_wrap(1100, 220, f"""
  {box(20, 70, 110, 70, "START", ["初始化"], "#dceeea")}
  {box(160, 70, 130, 70, "DEV 加料", ["设备能力节点"], "#e7eaf4")}
  {box(320, 70, 140, 70, "BRANCH", ["条件判断"], "#f3e6d4")}
  {box(500, 20, 160, 70, "DEV 加热反应", ["温度正常"], "#e7eaf4")}
  {box(500, 130, 160, 70, "DEV 冷却排空", ["温度过高"], "#f3dce0")}
  {box(700, 70, 150, 70, "AGGREGATE", ["汇聚等待"], "#f3eee4")}
  {box(880, 70, 180, 70, "子流程 → END", ["分析检测"], "#dceeea")}
  {arrow(130, 105, 160, 105)}
  {arrow(290, 105, 320, 105)}
  {arrow(460, 90, 500, 55, "正常")}
  {arrow(460, 120, 500, 165, "过高")}
  {arrow(660, 55, 775, 90)}
  {arrow(660, 165, 775, 120)}
  {arrow(850, 105, 880, 105)}
""", "图 4 · 工作流节点拓扑示例")


DIAGRAMS[4] = lambda: svg_wrap(1100, 280, f"""
  {box(30, 40, 240, 200, "权威内存数据源", ["设备孪生属性", "CMD / OP 状态", "任务 / 节点 / 变量"], "#e7eaf4")}
  {box(310, 40, 230, 200, "双轨调度", ["事件：可观测变更", "时钟：200ms 扫描"], "#dceeea")}
  {box(580, 40, 240, 200, "约束引擎核心", ["编译有效模型", "监控计划", "表达式 + 时间窗口"], "#f3e6d4")}
  {box(860, 40, 210, 200, "违规处置", ["SYSTEM ABORT/PAUSE/ALERT", "设备保护能力", "写入 VIOLATION_LOG"], "#f3dce0")}
  {arrow(270, 140, 310, 140)}
  {arrow(540, 140, 580, 140)}
  {arrow(820, 140, 860, 140, "确认违规")}
""", "图 5 · 约束引擎求值与处置")


DIAGRAMS["adapter"] = lambda: svg_wrap(1100, 420, f"""
  {box(300, 20, 500, 60, "SmartLab 2.0 后端", ["command 下行 · telemetry / event / register / heartbeat 上行"], "#e7eaf4")}
  {box(300, 120, 500, 50, "MQTT Broker", ["smartlab/adapter/{name}/{point}/…"], "#f3eee4")}
  {box(80, 220, 940, 120, "Adapter 边缘进程", [], "#fffcf7")}
  {box(110, 250, 200, 70, "northbound.py", ["MQTT 标准信封"], "#e7eaf4")}
  {box(360, 250, 200, 70, "core.py", ["语义翻译 / 追踪"], "#dceeea")}
  {box(610, 250, 200, 70, "southbound.py", ["PLC / SDK 驱动"], "#f3e6d4")}
  {box(830, 250, 160, 70, "runtime.py", ["配置 / 心跳"], "#f3eee4")}
  {box(300, 370, 500, 40, "PLC / 反应釜 / 仪器硬件", [], "#1b2430")}
  <text x="550" y="395" text-anchor="middle" font-size="13" fill="#fffcf7">Modbus / TCP / 串口 / 厂商 SDK</text>
  {arrow(550, 80, 550, 120)}
  {arrow(550, 170, 550, 220)}
  {arrow(550, 340, 550, 370)}
""", "图 6 · Adapter 四层接入")


DIAGRAMS[5] = lambda: svg_wrap(1100, 360, f"""
  {box(40, 30, 180, 50, "ADAPTER_INDEX", [], "#e7eaf4")}
  {box(40, 120, 180, 50, "DEVICE_CATEGORY", [], "#e7eaf4")}
  {box(300, 75, 200, 50, "DEVICE_MODELS", [], "#dceeea")}
  {box(580, 75, 210, 50, "DEVICE_INSTANCES", [], "#dceeea")}
  {box(860, 30, 200, 50, "DEVICE_TWIN_STATES", [], "#f3eee4")}
  {box(860, 120, 200, 50, "DEVICE_COMPONENTS", [], "#f3eee4")}
  {box(40, 210, 180, 50, "FLOW_MODELS", [], "#e7eaf4")}
  {box(300, 210, 200, 50, "FLOW_NODE", [], "#dceeea")}
  {box(580, 210, 210, 50, "TASK", [], "#f3e6d4")}
  {box(860, 210, 200, 50, "TASK_STEP", [], "#f3e6d4")}
  {box(300, 300, 200, 40, "CONSTRAINT_RULE", [], "#f3dce0")}
  {box(580, 300, 210, 40, "VIOLATION_LOG", [], "#f3dce0")}
  {box(860, 300, 200, 40, "EXECUTION_LOG", [], "#f3eee4")}
  {arrow(220, 55, 300, 100)}
  {arrow(220, 145, 300, 100)}
  {arrow(500, 100, 580, 100)}
  {arrow(790, 100, 860, 55)}
  {arrow(790, 100, 860, 145)}
  {arrow(220, 235, 300, 235)}
  {arrow(220, 235, 580, 235)}
  {arrow(500, 235, 580, 235)}
  {arrow(790, 235, 860, 235)}
  {arrow(500, 235, 860, 235)}
  {arrow(500, 320, 580, 320)}
  {arrow(685, 260, 685, 300)}
  {arrow(790, 235, 960, 300)}
""", "图 7 · 核心表关系")


def convert_blocks(md: str) -> str:
    parts = re.split(r"(```[\s\S]*?```)", md)
    html_parts = []
    mermaid_i = 0
    for part in parts:
        if part.startswith("```"):
            m = re.match(r"```(\w+)?\n([\s\S]*?)```", part)
            if not m:
                continue
            lang, body = m.group(1) or "", m.group(2)
            if lang == "mermaid":
                html_parts.append(DIAGRAMS[mermaid_i]())
                mermaid_i += 1
            elif lang == "text" and "northbound" in body:
                html_parts.append(DIAGRAMS["adapter"]())
            else:
                html_parts.append(
                    f'<pre><code class="{esc(lang)}">{esc(body.rstrip())}</code></pre>'
                )
        else:
            html_parts.append(convert_markdown(part))
    return "\n".join(html_parts)


def convert_markdown(text: str) -> str:
    lines = text.replace("\r\n", "\n").split("\n")
    out = []
    i = 0
    while i < len(lines):
        line = lines[i]
        if line.strip() == "---":
            out.append("<hr/>")
            i += 1
            continue
        if line.startswith("# "):
            out.append(f"<h1>{inline(line[2:])}</h1>")
            i += 1
            continue
        if line.startswith("## "):
            title = line[3:]
            hid = slug(heading_text(title))
            out.append(f'<h2 id="{hid}">{inline(title)}</h2>')
            i += 1
            continue
        if line.startswith("### "):
            title = line[4:]
            hid = slug(heading_text(title))
            out.append(f'<h3 id="{hid}">{inline(title)}</h3>')
            i += 1
            continue
        if line.startswith("#### "):
            out.append(f"<h4>{inline(line[5:])}</h4>")
            i += 1
            continue
        if re.match(r"^\|.+\|$", line) and i + 1 < len(lines) and re.match(r"^\|[-: |]+\|$", lines[i + 1]):
            rows = []
            while i < len(lines) and re.match(r"^\|.+\|$", lines[i]):
                if re.match(r"^\|[-: |]+\|$", lines[i]):
                    i += 1
                    continue
                cells = [c.strip() for c in lines[i].strip("|").split("|")]
                rows.append(cells)
                i += 1
            if rows:
                thead = "".join(f"<th>{inline(c)}</th>" for c in rows[0])
                body = []
                for row in rows[1:]:
                    body.append("<tr>" + "".join(f"<td>{inline(c)}</td>" for c in row) + "</tr>")
                out.append(f"<table><thead><tr>{thead}</tr></thead><tbody>{''.join(body)}</tbody></table>")
            continue
        if re.match(r"^(\d+)\. ", line) or line.startswith("- "):
            ordered = bool(re.match(r"^(\d+)\. ", line))
            tag = "ol" if ordered else "ul"
            out.append(f"<{tag}>")
            while i < len(lines) and (
                (ordered and re.match(r"^(\d+)\. ", lines[i]))
                or (not ordered and (lines[i].startswith("- ") or lines[i].startswith("  - ") or lines[i].startswith("  1.")))
            ):
                raw = lines[i]
                if raw.startswith("  - "):
                    # keep as nested-looking item
                    out.append(f"<li>{inline(raw[4:])}</li>")
                elif raw.startswith("- "):
                    out.append(f"<li>{inline(raw[2:])}</li>")
                else:
                    out.append(f"<li>{inline(re.sub(r'^\\d+\\. ', '', raw))}</li>")
                i += 1
                # continuation indented lines
                while i < len(lines) and (lines[i].startswith("  - ") or lines[i].startswith("   - ")):
                    out.append(f"<li>{inline(lines[i].lstrip()[2:])}</li>")
                    i += 1
            out.append(f"</{tag}>")
            continue
        if not line.strip():
            i += 1
            continue
        para = [line]
        i += 1
        while i < len(lines) and lines[i].strip() and not lines[i].startswith("#") and not lines[i].startswith("- ") and not re.match(r"^\d+\. ", lines[i]) and lines[i].strip() != "---" and not re.match(r"^\|.+\|$", lines[i]):
            para.append(lines[i])
            i += 1
        out.append("<p>" + inline(" ".join(para)) + "</p>")
    return "\n".join(out)


def heading_text(title: str) -> str:
    t = re.sub(r"\[`?([^\]`]+)`?\]\([^)]+\)", r"\1", title)
    return t.replace("`", "")


def slug(title: str) -> str:
    return re.sub(r"[^\w\u4e00-\u9fff]+", "-", title).strip("-").lower()


def toc_from_md(md: str) -> str:
    items = []
    for line in md.splitlines():
        if line.startswith("## ") or line.startswith("### "):
            level = "h2" if line.startswith("## ") else "h3"
            t = line[3:] if level == "h2" else line[4:]
            t = heading_text(t)
            items.append(f'<a class="{level}" href="#{slug(t)}">{esc(t)}</a>')
    return "\n".join(items)


CSS = r"""
:root {
  --paper: #ebe4d6; --ink: #1b2430; --muted: #5d6670; --line: #ddd4c4;
  --theory: #3b4a86; --eng: #0f6e66; --white: #fffcf7; --chip: #f3eee4;
  --font: "Source Han Sans SC", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
  --serif: "Source Han Serif SC", "Noto Serif SC", "Songti SC", serif;
}
* { box-sizing: border-box; }
html { scroll-behavior: smooth; }
body {
  margin: 0; display: grid; grid-template-columns: 280px 1fr; min-height: 100vh;
  background: var(--paper); color: var(--ink); font-family: var(--font);
}
nav {
  position: sticky; top: 0; height: 100vh; overflow: auto;
  padding: 28px 18px 40px; border-right: 1px solid var(--line); background: #f6f1e6;
}
nav .brand { font-family: var(--serif); font-size: 16px; line-height: 1.4; margin-bottom: 18px; }
nav a { display: block; color: var(--muted); text-decoration: none; font-size: 13px; padding: 6px 8px; line-height: 1.4; }
nav a:hover { background: #e7eaf4; color: var(--theory); }
nav a.h3 { padding-left: 16px; font-size: 12px; }
main { max-width: 1100px; padding: 36px 48px 80px; }
.hero { color: var(--muted); margin-bottom: 24px; padding-bottom: 16px; border-bottom: 1px solid var(--line); }
article h1 { font-family: var(--serif); font-size: 30px; margin: 0 0 16px; }
article h2 { font-family: var(--serif); font-size: 22px; margin: 44px 0 14px; padding-top: 12px; border-top: 1px solid var(--line); }
article h2:first-of-type { border-top: 0; margin-top: 0; }
article h3 { font-size: 17px; margin: 26px 0 10px; color: var(--theory); }
article h4 { font-size: 15px; margin: 18px 0 8px; }
article p, article li { line-height: 1.7; font-size: 15px; }
article ul, article ol { padding-left: 22px; }
article li + li { margin-top: 4px; }
article code { font-family: ui-monospace, Consolas, monospace; font-size: .88em; background: var(--chip); padding: 1px 5px; }
article pre { background: #1b2430; color: #e8e2d6; padding: 16px 18px; overflow: auto; font-size: 13px; line-height: 1.5; }
article pre code { background: none; color: inherit; padding: 0; }
.diagram { background: var(--white); border: 1px solid var(--line); padding: 12px 12px 8px; margin: 18px 0 28px; }
.diagram .caption { font-size: 12px; letter-spacing: .08em; color: var(--muted); margin-bottom: 8px; text-transform: uppercase; }
table { width: 100%; border-collapse: collapse; background: var(--white); font-size: 14px; margin: 12px 0 24px; }
th, td { border: 1px solid var(--line); padding: 10px 12px; text-align: left; vertical-align: top; line-height: 1.5; }
th { background: var(--chip); color: var(--theory); }
hr { border: 0; border-top: 1px solid var(--line); margin: 24px 0; }
@media (max-width: 960px) { body { grid-template-columns: 1fr; } nav { display: none; } main { padding: 20px; } }
"""


def main() -> None:
    md = MD_PATH.read_text(encoding="utf-8")
    body = convert_blocks(md)
    page = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title>SmartLab 2.0 智能实验室系统技术架构文档</title>
  <style>{CSS}</style>
</head>
<body>
  <nav>
    <div class="brand">SmartLab 2.0<br/>技术架构</div>
    {toc_from_md(md)}
  </nav>
  <main>
    <p class="hero">来源：智能实验室系统技术架构文档.md · 架构图已绘制，可离线打开</p>
    <article>{body}</article>
  </main>
</body>
</html>
"""
    OUT_PATH.write_text(page, encoding="utf-8")
    print("wrote", OUT_PATH, OUT_PATH.stat().st_size)


if __name__ == "__main__":
    main()
