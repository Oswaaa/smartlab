#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
SmartLab 2.0 系统架构图 — 规范学术黑白线稿版 (Black & White Wireframe)
精准复刻并升华用户提供的架构图：
- 五大层次拓扑：① 表现层、② 服务与核心引擎层、③ 消息中间件、④ 边缘驱动层、⑤ 持久化层
- 区分三种连线语义：
  1. 粗实线 (Bold Solid) = 执行控制链路 (禁止旁路)
  2. 虚线 (Dashed) = 观测 / 反馈链路
  3. 细实线 (Thin Solid) = API / 数据读写链路
- 零遮挡正交布线 (Manhattan Routing)：每条控制与数据流具备独立走线信道，杜绝线条穿越文字与异构图块
- 纯净学术黑白线稿风格：高对比度、清晰层级、适合学术论文与专著打印
"""

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch, Rectangle
from matplotlib.font_manager import FontProperties
import os, sys

sys.stdout.reconfigure(encoding='utf-8')

# ─────────────────────────────────────────────────────────────
# 画布与基础配置 (1:1 高清学术比例)
# ─────────────────────────────────────────────────────────────
FIG_SIZE = (18, 18)
DPI = 300
OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "generated_images")
os.makedirs(OUTPUT_DIR, exist_ok=True)

fig, ax = plt.subplots(figsize=FIG_SIZE, dpi=DPI)
fig.patch.set_facecolor('#FFFFFF')
ax.set_facecolor('#FFFFFF')
ax.set_xlim(0, 100)
ax.set_ylim(0, 100)
ax.set_aspect('equal')
ax.axis('off')

def font(size=9, weight='normal'):
    for family in ['Microsoft YaHei', 'SimHei', 'Source Han Sans CN', 'Noto Sans CJK SC', 'sans-serif']:
        try:
            return FontProperties(family=family, size=size, weight=weight)
        except Exception:
            continue
    return FontProperties(size=size, weight=weight)

# ─────────────────────────────────────────────────────────────
# 绘图基础组件封装 (黑白线稿专用)
# ─────────────────────────────────────────────────────────────

def draw_wire_box(x, y, w, h, title="", subtitle="", detail="", 
                  lw=1.0, ls='-', corner_pad=0.3, fill_color='#FFFFFF',
                  hatch=None, tfs=8.2, sfs=7.0, dfs=6.5, bold_title=True, zorder=3):
    """绘制黑白线稿方框组件"""
    box = FancyBboxPatch(
        (x, y), w, h,
        boxstyle=f"round,pad={corner_pad},rounding_size=0.5",
        fc=fill_color, ec='#000000', lw=lw, ls=ls,
        hatch=hatch, zorder=zorder
    )
    ax.add_patch(box)
    
    cx = x + w / 2.0
    cy = y + h / 2.0
    
    items = []
    if title: items.append(('title', title, tfs, 'bold' if bold_title else 'normal'))
    if subtitle: items.append(('sub', subtitle, sfs, 'normal'))
    if detail: items.append(('detail', detail, dfs, 'normal'))
    
    n = len(items)
    if n == 1:
        ax.text(cx, cy, items[0][1], fontproperties=font(items[0][2], items[0][3]),
                color='#000000', ha='center', va='center', zorder=zorder+2)
    elif n == 2:
        gap = h * 0.22
        ax.text(cx, cy + gap * 0.55, items[0][1], fontproperties=font(items[0][2], items[0][3]),
                color='#000000', ha='center', va='center', zorder=zorder+2)
        ax.text(cx, cy - gap * 0.55, items[1][1], fontproperties=font(items[1][2], items[1][3]),
                color='#222222', ha='center', va='center', zorder=zorder+2)
    elif n == 3:
        gap = h * 0.26
        ax.text(cx, cy + gap * 0.95, items[0][1], fontproperties=font(items[0][2], items[0][3]),
                color='#000000', ha='center', va='center', zorder=zorder+2)
        ax.text(cx, cy, items[1][1], fontproperties=font(items[1][2], items[1][3]),
                color='#222222', ha='center', va='center', zorder=zorder+2)
        ax.text(cx, cy - gap * 0.95, items[2][1], fontproperties=font(items[2][2], items[2][3]),
                color='#444444', ha='center', va='center', zorder=zorder+2)

def draw_container(x, y, w, h, badge_text="", badge_style='solid_black', 
                   badge_w=None, lw=1.2, ls='-', zorder=1):
    """
    绘制大容器。
    badge_style:
      - 'solid_black': 黑底白字 (经典学术大章节标题)
      - 'bordered': 白底黑字带黑框 (子模块标题)
    """
    container = FancyBboxPatch(
        (x, y), w, h,
        boxstyle="round,pad=0.4,rounding_size=0.8",
        fc='#FFFFFF', ec='#000000', lw=lw, ls=ls, zorder=zorder
    )
    ax.add_patch(container)
    
    if badge_text:
        tw = badge_w if badge_w else min(w - 1.0, len(badge_text) * 0.82 + 2.4)
        bx = x + 0.8
        by = y + h - 1.5
        bh = 2.2
        
        if badge_style == 'solid_black':
            badge = FancyBboxPatch(
                (bx, by), tw, bh,
                boxstyle="round,pad=0.2,rounding_size=0.4",
                fc='#000000', ec='#000000', lw=1.0, zorder=zorder+2
            )
            ax.add_patch(badge)
            ax.text(bx + tw/2.0, by + bh/2.0, badge_text,
                    fontproperties=font(8.2, 'bold'), color='#FFFFFF',
                    ha='center', va='center', zorder=zorder+3)
        else:
            badge = FancyBboxPatch(
                (bx, by), tw, bh,
                boxstyle="round,pad=0.2,rounding_size=0.4",
                fc='#FFFFFF', ec='#000000', lw=1.1, zorder=zorder+2
            )
            ax.add_patch(badge)
            ax.text(bx + tw/2.0, by + bh/2.0, badge_text,
                    fontproperties=font(7.5, 'bold'), color='#000000',
                    ha='center', va='center', zorder=zorder+3)

def draw_manhattan_arrow(points, label="", line_type='control', label_pos=0.5, label_offset=(0, 0)):
    """
    绘制正交直角折线箭头 (Manhattan Routing)，彻底避免穿越无关组件
    points: [(x1, y1), (x2, y2), ...]
    line_type: 'control' (粗实线), 'observe' (虚线), 'data' (细实线)
    """
    lw = 2.0 if line_type == 'control' else (1.2 if line_type == 'observe' else 1.0)
    ls = '--' if line_type == 'observe' else '-'
    mut_scale = 13 if line_type == 'control' else (10 if line_type == 'observe' else 9)
    
    # 绘制折线主体
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    ax.plot(xs, ys, color='#000000', lw=lw, linestyle=ls, zorder=6)
    
    # 在最后一段添加箭头末端
    p_last2 = points[-2]
    p_last = points[-1]
    arrow = FancyArrowPatch(
        p_last2, p_last,
        arrowstyle='-|>',
        color='#000000',
        lw=lw, linestyle=ls,
        zorder=7,
        mutation_scale=mut_scale
    )
    ax.add_patch(arrow)
    
    if label:
        total_len = 0.0
        lens = []
        for i in range(len(points)-1):
            dx = points[i+1][0] - points[i][0]
            dy = points[i+1][1] - points[i][1]
            seg_len = (dx*dx + dy*dy)**0.5
            lens.append(seg_len)
            total_len += seg_len
        
        target = total_len * label_pos
        curr = 0.0
        lx, ly = points[0]
        for i in range(len(lens)):
            if curr + lens[i] >= target:
                frac = (target - curr) / lens[i] if lens[i] > 0 else 0
                lx = points[i][0] + frac * (points[i+1][0] - points[i][0])
                ly = points[i][1] + frac * (points[i+1][1] - points[i][1])
                break
            curr += lens[i]
            
        lx += label_offset[0]
        ly += label_offset[1]
        
        ax.text(
            lx, ly, label,
            fontproperties=font(7.5, 'bold' if line_type == 'control' else 'normal'),
            color='#000000', ha='center', va='center', zorder=9,
            bbox=dict(boxstyle='square,pad=0.2', fc='#FFFFFF', ec='#666666', lw=0.6)
        )

def draw_straight_arrow(x1, y1, x2, y2, label="", line_type='control', rad=0.0, label_offset=(0, 0)):
    """绘制直箭头或圆弧箭头"""
    lw = 2.0 if line_type == 'control' else (1.2 if line_type == 'observe' else 1.0)
    ls = '--' if line_type == 'observe' else '-'
    mut_scale = 13 if line_type == 'control' else (10 if line_type == 'observe' else 9)
    
    conn = f"arc3,rad={rad}"
    arrow = FancyArrowPatch(
        (x1, y1), (x2, y2),
        arrowstyle='-|>',
        connectionstyle=conn,
        color='#000000',
        lw=lw, linestyle=ls,
        zorder=7,
        mutation_scale=mut_scale
    )
    ax.add_patch(arrow)
    
    if label:
        mx = (x1 + x2) / 2.0 + label_offset[0]
        my = (y1 + y2) / 2.0 + label_offset[1]
        ax.text(
            mx, my, label,
            fontproperties=font(7.5, 'bold' if line_type == 'control' else 'normal'),
            color='#000000', ha='center', va='center', zorder=9,
            bbox=dict(boxstyle='square,pad=0.2', fc='#FFFFFF', ec='#666666', lw=0.6)
        )

# ─────────────────────────────────────────────────────────────
# 1. 顶部标题与总体闭环控制链路
# ─────────────────────────────────────────────────────────────
ax.text(50, 98.2, "Smart Chemical Laboratory — 系统架构", 
        fontproperties=font(17, 'bold'), color='#000000', ha='center', va='center')
ax.text(50, 96.0, "工作流节点 → 状态机 → 适配器 → MQTT → 边缘驱动 → 物理设备 (禁止旁路)", 
        fontproperties=font(9.0, 'bold'), color='#333333', ha='center', va='center')

# ─────────────────────────────────────────────────────────────
# 2. ① 表现层 (Presentation Layer)
# ─────────────────────────────────────────────────────────────
draw_container(3.5, 84.8, 93.0, 9.4, "① 表现层 · Vue 3 + Vite + Element Plus + Vue Flow", 
               badge_style='solid_black', badge_w=36.0, lw=1.4)

ui_boxes = [
    ("设备与孪生控制台", "Device / StateMachine"),
    ("工作流设计器", "WorkflowDesigner"),
    ("大模型智能体助手", "WorkflowAgentChat"),
    ("任务监控与预检", "TaskList + SSE"),
    ("全局安全约束", "Constraint"),
    ("数据中心与权限", "Data & User")
]

for idx, (t, s) in enumerate(ui_boxes):
    bx = 5.2 + idx * 15.0
    draw_wire_box(bx, 85.5, 14.2, 6.0, title=t, subtitle=s, lw=1.0, tfs=8.2, sfs=7.0)

# ─────────────────────────────────────────────────────────────
# 3. ② 服务与核心引擎层 (Core Services & Engine Layer)
# ─────────────────────────────────────────────────────────────
# 容器顶部为 y = 81.8，徽标在 x=4.3 到 x=28.5
draw_container(3.5, 47.0, 93.0, 34.8, "② 服务与核心引擎层 · Spring Boot 3 + Java 21", 
               badge_style='solid_black', badge_w=25.0, lw=1.4)

# 3.1 核心引擎子系统 (com.smartlab.engine)
# 徽标宽度调整为 16.0，从 x=5.6 到 x=21.6，不阻挡右侧任何下行通道
draw_container(4.8, 47.8, 45.4, 29.8, "核心引擎 com.smartlab.engine", 
               badge_style='bordered', badge_w=16.8, lw=1.1, ls='-')

# [左上] 约束引擎 ConstraintEngine
draw_wire_box(
    6.0, 68.4, 19.8, 6.8,
    title="约束引擎 ConstraintEngine",
    subtitle="双轨调度 (事件+200ms) · 时序窗口",
    detail="安全熔断 ABORT/PAUSE · 保护动作",
    lw=1.0, tfs=8.0, sfs=6.8, dfs=6.5
)

# [右上] 工作流引擎 WorkflowEngine
draw_wire_box(
    28.6, 68.4, 20.2, 6.8,
    title="工作流引擎 WorkflowEngine",
    subtitle="100ms 轮询快照 · 上升沿求值",
    detail="5步收敛终止 · 动态步骤拓扑",
    lw=1.1, tfs=8.0, sfs=6.8, dfs=6.5
)

# [左中] 连接通道 Interface / Ports
draw_wire_box(
    6.0, 58.0, 19.8, 6.8,
    title="连接通道 Interface / Ports",
    subtitle="协议转发 · 下游主动拉取",
    detail="控制活边 / 数据只读隔离",
    lw=1.0, tfs=8.0, sfs=6.8, dfs=6.5
)

# [右中] 状态机引擎 StateMachineEngine
draw_wire_box(
    28.6, 58.0, 20.2, 6.8,
    title="状态机引擎 StateMachineEngine",
    subtitle="双状态空间 CMD+OP · 差速终止",
    detail="指令槽排他锁 · 状态原子跃迁",
    lw=1.1, tfs=8.0, sfs=6.8, dfs=6.5
)

# [底部] 统一观测空间 Observation Space
draw_wire_box(
    6.0, 48.4, 42.8, 7.2,
    title="统一观测空间 Observation Space",
    subtitle="6 类对象内存快照 · 有界历史时间河 · 引擎共享数据汇聚点",
    detail="纳秒级只读内存分发 · 软实时求值底盘",
    lw=1.2, tfs=8.8, sfs=7.2, dfs=6.6
)

# 核心引擎内部连线
# 约束 -> 工作流 拦截
draw_straight_arrow(25.8, 71.8, 28.6, 71.8, label="拦截", line_type='observe')
# 约束 -> 连接通道
draw_straight_arrow(15.9, 68.4, 15.9, 64.8, line_type='observe')
# 工作流 -> 状态机 控制 (主控制粗实线)
draw_straight_arrow(38.7, 68.4, 38.7, 64.8, label="控制", line_type='control', label_offset=(1.8, 0))
# 状态机 -> 连接通道
draw_straight_arrow(28.6, 61.4, 25.8, 61.4, line_type='observe')
# 连接通道 -> 统一观测空间
draw_straight_arrow(15.9, 58.0, 15.9, 55.6, line_type='observe')
# 状态机 -> 统一观测空间
draw_straight_arrow(38.7, 58.0, 38.7, 55.6, line_type='observe')
# 统一观测空间 -> 约束引擎 (虚线反馈环路，沿左侧安全绕行)
draw_manhattan_arrow([(6.0, 52.0), (4.2, 52.0), (4.2, 71.8), (6.0, 71.8)], line_type='observe')


# 3.2 智能体层 (agent)
draw_container(51.4, 47.8, 13.8, 29.8, "智能体层 agent", 
               badge_style='bordered', badge_w=9.2, lw=1.1, ls='-')
draw_wire_box(
    52.4, 62.6, 11.8, 11.8,
    title="NL → Workflow",
    subtitle="自然语言 → 可执行工作流草稿",
    detail="规范约束审查 · 契约注入",
    lw=1.0, tfs=8.2, sfs=6.8, dfs=6.5
)
draw_wire_box(
    52.4, 48.4, 11.8, 11.8,
    title="AgentLoop",
    subtitle="多轮 Tool-use 规划",
    detail="环境感知反馈 · 自动自愈",
    lw=1.0, tfs=8.2, sfs=6.8, dfs=6.5
)

# NL->Workflow 输送草稿到工作流引擎
draw_straight_arrow(52.4, 68.5, 48.8, 68.5, label="草稿", line_type='data', label_offset=(0, 0.7))


# 3.3 适配器接口 (adapter)
draw_container(66.4, 47.8, 14.0, 29.8, "适配器接口 adapter", 
               badge_style='bordered', badge_w=11.2, lw=1.1, ls='-')
draw_wire_box(
    67.4, 67.6, 12.0, 6.8,
    title="Mapping",
    subtitle="Payload 映射",
    detail="参数/遥测转换",
    lw=1.0, tfs=8.0, sfs=6.8, dfs=6.5
)
draw_wire_box(
    67.4, 58.0, 12.0, 6.8,
    title="Manifest",
    subtitle="清单 / Manifest",
    detail="动态自省声明",
    lw=1.0, tfs=8.0, sfs=6.8, dfs=6.5
)
draw_wire_box(
    67.4, 48.4, 12.0, 7.4,
    title="MQTT Client",
    subtitle="MQTT 上下行",
    detail="协议收发端",
    lw=1.1, tfs=8.0, sfs=6.8, dfs=6.5
)

# 核心执行链路：状态机引擎 -> MQTT Client (从状态机右侧引出，绕过 AgentLoop 下方空隙进入 MQTT Client，彻底避让文字)
draw_manhattan_arrow(
    [(48.8, 61.4), (50.5, 61.4), (50.5, 46.2), (66.4, 46.2), (67.4, 50.5)],
    label="命令", line_type='control', label_pos=0.18, label_offset=(0, 1.2)
)


# 3.4 管理与 API (management)
draw_container(81.6, 47.8, 14.0, 29.8, "管理与 API management", 
               badge_style='bordered', badge_w=12.6, lw=1.1, ls='-')
draw_wire_box(
    82.6, 67.6, 12.0, 6.8,
    title="RESTful API",
    subtitle="REST Controllers",
    detail="工作流/模型/设备管理",
    lw=1.0, tfs=8.0, sfs=6.8, dfs=6.5
)
draw_wire_box(
    82.6, 58.0, 12.0, 6.8,
    title="SSE Hub",
    subtitle="设备监控 / 任务看数推流",
    detail="实时快照下行",
    lw=1.0, tfs=8.0, sfs=6.8, dfs=6.5
)
draw_wire_box(
    82.6, 48.4, 12.0, 7.4,
    title="MyBatis-Plus",
    subtitle="持久化访问",
    detail="关系与 JSONB 存取",
    lw=1.0, tfs=8.0, sfs=6.8, dfs=6.5
)

# RESTful API -> SSE Hub
draw_straight_arrow(88.6, 67.6, 88.6, 64.8, line_type='observe')


# ─────────────────────────────────────────────────────────────
# 跨层正交走线通道 (Layer 1 ➔ Layer 2，无遮挡无交叉)
# ─────────────────────────────────────────────────────────────
# 1. 全局安全约束 -> 约束引擎 (下->左->下，在层间最高信道 y=83.2 独立走线，落点在 x=18.0，完美避让核心引擎徽标)
draw_manhattan_arrow(
    [(65.2, 85.5), (65.2, 83.2), (18.5, 83.2), (18.5, 75.2)],
    line_type='observe'
)

# 2. 工作流设计器 -> 工作流引擎 (主执行下发：从 x=29.2 出发，在 y=79.2 信道横移，避开徽标②)
draw_manhattan_arrow(
    [(29.2, 85.5), (29.2, 79.2), (38.7, 79.2), (38.7, 75.2)],
    label="—— 执行 ——", line_type='control', label_pos=0.55, label_offset=(0, 0.8)
)

# 3. 大模型智能体助手 -> NL → Workflow (下->右->下，在 y=77.5 独立走线)
draw_manhattan_arrow(
    [(42.0, 85.5), (42.0, 77.5), (61.5, 77.5), (61.5, 74.4)],
    line_type='data'
)

# 4. SSE Hub -> 任务监控与预检 (在 y=75.6 清晰横移通道，介于 Mapping 之上与徽标之下，从间隙通往表现层)
draw_manhattan_arrow(
    [(88.6, 74.4), (88.6, 75.6), (56.0, 75.6), (56.0, 85.5)],
    label="—— SSE 实时推流 ——", line_type='observe', label_pos=0.5, label_offset=(0, 0.8)
)


# ─────────────────────────────────────────────────────────────
# 4. ③ 消息中间件 (Message Broker)
# ─────────────────────────────────────────────────────────────
# 徽标宽度 24.0，从 x=4.3 到 x=28.3，不阻挡 x=31.0 处的上行遥测虚线
draw_container(3.5, 37.0, 61.5, 7.4, "③ 消息中间件 · MQTT Broker (EMQX / Mosquitto)", 
               badge_style='solid_black', badge_w=24.0, lw=1.4)
draw_wire_box(
    4.8, 37.8, 58.9, 4.4,
    title="Topics :  register  ·  heartbeat  ·  command  ·  telemetry  ·  event",
    lw=0.8, ls='-', tfs=8.2, bold_title=False
)

# MQTT Client 下发 CMD 到 MQTT Broker (左转弯下行入库，避让持久化区)
draw_manhattan_arrow(
    [(73.4, 48.4), (73.4, 45.4), (54.0, 45.4), (54.0, 42.2)],
    label="下行 CMD", line_type='control', label_pos=0.45, label_offset=(0, 0.8)
)

# MQTT Broker 遥测数据上报到 统一观测空间 (虚线向上，起点 x=31.0，完全避开徽标③)
draw_straight_arrow(31.0, 42.2, 31.0, 48.4, label="遥测 / 事件", line_type='observe', label_offset=(-3.8, 0))


# ─────────────────────────────────────────────────────────────
# 5. ④ 边缘驱动层 (Edge Driver Layer) & 物理设备
# ─────────────────────────────────────────────────────────────
draw_container(3.5, 20.8, 61.5, 14.8, "④ 边缘驱动层 · adapter/ & fixture/ · C++ / Python", 
               badge_style='solid_black', badge_w=28.0, lw=1.4)

edge_mods = [
    ("core", "语义转换 · 内部注入"),
    ("runtime", "生命周期 · Watchdog"),
    ("northbound", "MQTT Client 上行"),
    ("southbound", "Modbus / Serial / TCP / PLC")
]

for idx, (t, s) in enumerate(edge_mods):
    bx = 4.8 + idx * 14.8
    draw_wire_box(bx, 26.6, 14.0, 6.2, title=t, subtitle=s, lw=0.9, tfs=8.2, sfs=6.8)

# 物理设备底盘 (学术高对比度：黑白双重边框 + 细密对角线纹理，凸显实体硬件底盘)
draw_wire_box(
    4.8, 21.6, 58.9, 4.2,
    title="物理设备 / PLC / 传感器 / 仪器仪表",
    subtitle="真机物理接口 · 双向电气信号交互",
    lw=1.6, ls='-', hatch='///', fill_color='#FAFAFA', tfs=9.0, sfs=7.2, bold_title=True
)

# MQTT Broker -> northbound 下行控制
draw_straight_arrow(42.6, 37.0, 42.6, 32.8, line_type='control')
# northbound -> southbound 控制流
draw_straight_arrow(49.6, 29.7, 53.6, 29.7, line_type='control')
# southbound -> 物理设备
draw_straight_arrow(56.8, 26.6, 56.8, 25.8, label="驱动/传感", line_type='control', label_offset=(3.2, 0))

# 贯通核心驱动的遥测反馈虚线 (Broker 穿过 core 直达硬件)
draw_straight_arrow(11.8, 37.0, 11.8, 25.8, line_type='observe')


# ─────────────────────────────────────────────────────────────
# 6. ⑤ 持久化存储 (Persistence Layer)
# ─────────────────────────────────────────────────────────────
draw_container(66.4, 20.8, 30.1, 23.6, "⑤ 持久化 · PostgreSQL 15+", 
               badge_style='solid_black', badge_w=18.0, lw=1.4)

draw_wire_box(
    67.8, 22.0, 27.3, 19.0,
    title="存储内容 (Storage Schema)",
    subtitle="物模型 · 状态机定义 · 工作流拓扑\n任务生命周期 · 安全约束 · 审计日志\n时序遥测采样数据表 (DATA_INDEX)",
    detail="PostgreSQL 15+  |  JSONB 原生半结构化支持",
    lw=1.0, tfs=9.0, sfs=7.5, dfs=7.0
)

# MyBatis-Plus 向 PostgreSQL 读写数据
draw_straight_arrow(88.6, 48.4, 88.6, 41.0, label="读写", line_type='data', label_offset=(1.8, 0))


# ─────────────────────────────────────────────────────────────
# 7. 底部学术图例 (Academic Legend)
# ─────────────────────────────────────────────────────────────
legend_y = 18.2
# 粗实线图例
ax.plot([14.0, 19.0], [legend_y, legend_y], color='#000000', lw=2.0)
ax.annotate("", xy=(19.0, legend_y), xytext=(14.0, legend_y),
            arrowprops=dict(arrowstyle="-|>", color="#000000", lw=2.0, mutation_scale=12))
ax.text(20.0, legend_y, "粗实线 = 执行控制链路 (禁止旁路)", fontproperties=font(8.2, 'bold'),
        color='#000000', va='center')

# 虚线图例
ax.plot([47.0, 52.0], [legend_y, legend_y], color='#000000', lw=1.2, ls='--')
ax.annotate("", xy=(52.0, legend_y), xytext=(47.0, legend_y),
            arrowprops=dict(arrowstyle="-|>", color="#000000", lw=1.2, linestyle='--', mutation_scale=10))
ax.text(53.0, legend_y, "虚线 = 观测 / 反馈链路", fontproperties=font(8.2, 'bold'),
        color='#000000', va='center')

# 细实线图例
ax.plot([75.0, 80.0], [legend_y, legend_y], color='#000000', lw=1.0)
ax.annotate("", xy=(80.0, legend_y), xytext=(75.0, legend_y),
            arrowprops=dict(arrowstyle="-|>", color="#000000", lw=1.0, mutation_scale=9))
ax.text(81.0, legend_y, "细实线 = API / 数据读写", fontproperties=font(8.2, 'bold'),
        color='#000000', va='center')

# 外围整体图框
border = Rectangle((1.5, 16.5), 97.0, 82.5, fc='none', ec='#000000', lw=0.8, ls='-')
ax.add_patch(border)

# ─────────────────────────────────────────────────────────────
# 保存输出高清矢量图与高清位图
# ─────────────────────────────────────────────────────────────
png_out = os.path.join(OUTPUT_DIR, "SmartLab2.0_系统架构_黑白线稿.png")
svg_out = os.path.join(OUTPUT_DIR, "SmartLab2.0_系统架构_黑白线稿.svg")

plt.savefig(png_out, dpi=DPI, bbox_inches='tight', facecolor='#FFFFFF')
plt.savefig(svg_out, format='svg', bbox_inches='tight', facecolor='#FFFFFF')
plt.close()

print(f"Successfully generated:\n- {png_out}\n- {svg_out}")
