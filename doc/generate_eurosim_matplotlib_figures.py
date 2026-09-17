# -*- coding: utf-8 -*-
"""
Generate the 3 formal diagrams for EUROSIM 2026 Presentation using matplotlib.
Canvas size: 7.35 x 3.90 inches at 300 DPI (2205 x 1170 px), aspect ratio ~1.88:1.
"""

import os
import matplotlib.pyplot as plt
import matplotlib.patches as patches
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch, Circle, PathPatch
from matplotlib.path import Path
from pptx import Presentation
from pptx.util import Inches, Pt

# Output directories
OUT_DIR = r"d:\SmartLab2.0\doc\output\figures"
ARTIFACT_DIR = r"C:\Users\22960\.gemini\antigravity\brain\76c097e5-c278-48e9-adcc-d5faa12beab0"
os.makedirs(OUT_DIR, exist_ok=True)
os.makedirs(ARTIFACT_DIR, exist_ok=True)

# Font configuration
plt.rcParams['font.sans-serif'] = ['Microsoft YaHei', 'SimHei', 'Segoe UI', 'sans-serif']
plt.rcParams['axes.unicode_minus'] = False

# ======================================================================
# Figure 1: 统一执行链路 (PPT 第 8 页)
# ======================================================================
def create_figure_1():
    fig, ax = plt.subplots(figsize=(7.35, 3.90), dpi=300)
    fig.patch.set_facecolor('#FFFFFF')
    ax.set_facecolor('#FFFFFF')
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    # Outer Border Card
    outer_card = FancyBboxPatch((0.8, 0.8), 98.4, 98.4, boxstyle="round,pad=0.3,rounding_size=1.2",
                                facecolor='#FFFFFF', edgecolor='#E2E8F0', linewidth=1.2, zorder=1)
    ax.add_patch(outer_card)

    # Title Bar
    title_bar = FancyBboxPatch((2.2, 92.5), 0.7, 4.2, boxstyle="round,pad=0.1,rounding_size=0.3",
                               facecolor='#1E3A8A', edgecolor='none', zorder=3)
    ax.add_patch(title_bar)
    ax.text(4.0, 94.6, "正式图 1：统一执行链路结构", fontsize=11.5, fontweight='bold', color='#0F172A', va='center', zorder=4)
    ax.text(32.5, 94.4, "(多源意图收敛 · 状态机独占发令 · 约束全局观察无旁路)", fontsize=8.5, color='#64748B', va='center', zorder=4)

    # ----------------------------------------------------
    # 1. Left Intent Sources Box (X: 2.2 to 20.0, Y: 46 to 90)
    # ----------------------------------------------------
    intent_box = FancyBboxPatch((2.2, 46.0), 18.0, 44.0, boxstyle="round,pad=0.4,rounding_size=1.0",
                                facecolor='#F8FAFC', edgecolor='#CBD5E1', linewidth=1.0, linestyle='--', zorder=2)
    ax.add_patch(intent_box)
    ax.text(11.2, 86.8, "意图来源", fontsize=9.0, fontweight='bold', color='#475569', ha='center', va='center', zorder=3)

    # 3 Sources
    sources = [
        ("人工交互", 78.5, '#64748B'),
        ("业务流程", 67.0, '#2563EB'),
        ("大模型意图", 55.5, '#7C3AED')
    ]
    for name, y_pos, color in sources:
        box = FancyBboxPatch((3.5, y_pos - 4.0), 15.4, 8.0, boxstyle="round,pad=0.2,rounding_size=0.8",
                             facecolor='#FFFFFF', edgecolor=color, linewidth=1.0, zorder=3)
        ax.add_patch(box)
        circ = Circle((5.5, y_pos), 1.3, facecolor=color, edgecolor='none', zorder=4)
        ax.add_patch(circ)
        ax.text(8.0, y_pos, name, fontsize=8.2, fontweight='bold', color='#1E293B', va='center', zorder=4)

        # Connector lines to convergence point
        ax.plot([18.9, 21.5], [y_pos, y_pos], color='#2563EB', linewidth=1.2, zorder=4)

    # Convergence vertical bar & entry
    ax.plot([21.5, 21.5], [55.5, 78.5], color='#2563EB', linewidth=1.2, zorder=4)
    ax.plot([21.5, 23.5], [67.0, 67.0], color='#2563EB', linewidth=1.2, zorder=4)

    # Convergence Badge
    entry_badge = FancyBboxPatch((23.5, 62.5), 13.0, 9.0, boxstyle="round,pad=0.2,rounding_size=1.0",
                                 facecolor='#EFF6FF', edgecolor='#3B82F6', linewidth=1.2, zorder=4)
    ax.add_patch(entry_badge)
    ax.text(30.0, 67.0, "不同意图\n共同执行入口", fontsize=7.2, fontweight='bold', color='#1E40AF', ha='center', va='center', zorder=5)

    # Arrow from Entry Badge into State Machine
    entry_arrow = FancyArrowPatch((36.5, 67.0), (39.5, 67.0), arrowstyle='-|>', mutation_scale=10,
                                  linewidth=1.8, color='#2563EB', zorder=6)
    ax.add_patch(entry_arrow)

    # ----------------------------------------------------
    # 2. Main Vertical Chain (X: 40.0 to 71.0)
    # ----------------------------------------------------
    chain_x = 40.0
    chain_w = 31.0

    # Layer 1: Workflow Node (Y: 79 to 90)
    wf_box = FancyBboxPatch((chain_x, 79.5), chain_w, 10.5, boxstyle="round,pad=0.3,rounding_size=1.0",
                            facecolor='#F0F7FF', edgecolor='#2563EB', linewidth=1.5, zorder=3)
    ax.add_patch(wf_box)
    ax.text(chain_x + 1.8, 86.2, "工作流节点 (Workflow Node)", fontsize=9.2, fontweight='bold', color='#1E3A8A', va='center', zorder=4)
    ax.text(chain_x + 1.8, 82.2, "作用：接口传递控制信号 · 数据端口 · 步骤生命周期", fontsize=6.8, color='#475569', va='center', zorder=4)

    # Down/Up arrows between Workflow and Device Model
    ax.add_patch(FancyArrowPatch((chain_x + 8.0, 79.5), (chain_x + 8.0, 74.0),
                                 arrowstyle='-|>', mutation_scale=10, linewidth=1.6, color='#2563EB', zorder=5))
    ax.text(chain_x + 7.0, 76.7, "控制请求", fontsize=6.5, fontweight='bold', color='#1D4ED8', ha='right', va='center', zorder=6)

    ax.add_patch(FancyArrowPatch((chain_x + 23.0, 74.0), (chain_x + 23.0, 79.5),
                                 arrowstyle='-|>', mutation_scale=10, linewidth=1.6, color='#16A34A', zorder=5))
    ax.text(chain_x + 24.0, 76.7, "状态反馈", fontsize=6.5, fontweight='bold', color='#15803D', ha='left', va='center', zorder=6)

    # Layer 2: Device Model Container (Y: 48.0 to 74.0)
    dm_container = FancyBboxPatch((chain_x, 48.0), chain_w, 26.0, boxstyle="round,pad=0.3,rounding_size=1.0",
                                  facecolor='#F8FAFC', edgecolor='#1E3A8A', linewidth=1.8, zorder=3)
    ax.add_patch(dm_container)
    dm_header = FancyBboxPatch((chain_x, 68.5), chain_w, 5.5, boxstyle="round,pad=0.1,rounding_size=0.6",
                               facecolor='#1E3A8A', edgecolor='none', zorder=4)
    ax.add_patch(dm_header)
    ax.text(chain_x + chain_w/2, 71.2, "设备模型 (Device Model) · 统一控制核心", fontsize=8.2, fontweight='bold', color='#FFFFFF', ha='center', va='center', zorder=5)

    # Sub-block: 能力模型 (Capability Model)
    cap_box = FancyBboxPatch((chain_x + 1.2, 50.0), 13.5, 16.5, boxstyle="round,pad=0.2,rounding_size=0.6",
                             facecolor='#FFFFFF', edgecolor='#93C5FD', linewidth=1.0, zorder=4)
    ax.add_patch(cap_box)
    ax.text(chain_x + 7.95, 63.5, "能力模型", fontsize=8.0, fontweight='bold', color='#1D4ED8', ha='center', va='center', zorder=5)
    ax.text(chain_x + 7.95, 57.5, "属性与参数声明\n动作语义契约\n(说明书/静态定义)", fontsize=6.2, color='#475569', ha='center', va='center', zorder=5)

    # Sub-block: 状态机 (State Machine)
    sm_box = FancyBboxPatch((chain_x + 16.3, 50.0), 13.5, 16.5, boxstyle="round,pad=0.2,rounding_size=0.8",
                            facecolor='#EFF6FF', edgecolor='#2563EB', linewidth=1.4, zorder=4)
    ax.add_patch(sm_box)
    ax.text(chain_x + 23.05, 63.5, "★ 状态机 (Core)", fontsize=8.0, fontweight='bold', color='#1E40AF', ha='center', va='center', zorder=5)
    ax.text(chain_x + 23.05, 57.5, "发令独占控制实体\n状态转移 / 异常锁存\n(控制必穿透路径)", fontsize=6.2, fontweight='bold', color='#2563EB', ha='center', va='center', zorder=5)

    # Link between Cap and SM
    ax.plot([chain_x + 14.7, chain_x + 16.3], [58.25, 58.25], color='#94A3B8', linewidth=1.2, linestyle=':', zorder=5)

    # Down/Up arrows between State Machine and Adapter
    ax.add_patch(FancyArrowPatch((chain_x + 8.0, 48.0), (chain_x + 8.0, 41.5),
                                 arrowstyle='-|>', mutation_scale=10, linewidth=1.6, color='#2563EB', zorder=5))
    ax.text(chain_x + 7.0, 44.7, "下发命令", fontsize=6.5, fontweight='bold', color='#1D4ED8', ha='right', va='center', zorder=6)

    ax.add_patch(FancyArrowPatch((chain_x + 23.0, 41.5), (chain_x + 23.0, 48.0),
                                 arrowstyle='-|>', mutation_scale=10, linewidth=1.6, color='#16A34A', zorder=5))
    ax.text(chain_x + 24.0, 44.7, "反馈转换", fontsize=6.5, fontweight='bold', color='#15803D', ha='left', va='center', zorder=6)

    # Layer 3: Adapter (Y: 28.5 to 41.5)
    ad_box = FancyBboxPatch((chain_x, 30.5), chain_w, 11.0, boxstyle="round,pad=0.3,rounding_size=1.0",
                            facecolor='#F0FDF4', edgecolor='#059669', linewidth=1.5, zorder=3)
    ax.add_patch(ad_box)
    ax.text(chain_x + 1.8, 38.2, "Adapter (边缘适配层)", fontsize=9.0, fontweight='bold', color='#065F46', va='center', zorder=4)
    ax.text(chain_x + 1.8, 33.5, "作用：协议映射 · 指令下发 · 遥测/反馈转换", fontsize=6.8, color='#047857', va='center', zorder=4)

    # Down/Up arrows between Adapter and Physical Equipment
    ax.add_patch(FancyArrowPatch((chain_x + 8.0, 30.5), (chain_x + 8.0, 23.5),
                                 arrowstyle='-|>', mutation_scale=9, linewidth=1.4, color='#2563EB', zorder=5))
    ax.add_patch(FancyArrowPatch((chain_x + 23.0, 23.5), (chain_x + 23.0, 30.5),
                                 arrowstyle='-|>', mutation_scale=9, linewidth=1.4, color='#16A34A', zorder=5))

    # Layer 4: Physical Equipment (Y: 13.0 to 23.5)
    phys_box = FancyBboxPatch((chain_x, 13.5), chain_w, 10.0, boxstyle="round,pad=0.3,rounding_size=1.0",
                              facecolor='#1E293B', edgecolor='#0F172A', linewidth=1.4, zorder=3)
    ax.add_patch(phys_box)
    ax.text(chain_x + chain_w/2, 20.2, "物理设备 (Physical Equipment / PLC)", fontsize=8.8, fontweight='bold', color='#F8FAFC', ha='center', va='center', zorder=4)
    ax.text(chain_x + chain_w/2, 16.0, "作用：执行操作 · 产生测量与真实运行状态", fontsize=6.8, color='#94A3B8', ha='center', va='center', zorder=4)

    # ----------------------------------------------------
    # 3. Right Constraint Observation & Handling (X: 74.0 to 97.5)
    # ----------------------------------------------------
    cg_x = 74.5
    cg_w = 23.0

    const_box = FancyBboxPatch((cg_x, 30.5), cg_w, 59.5, boxstyle="round,pad=0.3,rounding_size=1.0",
                               facecolor='#FFF7ED', edgecolor='#EA580C', linewidth=1.5, zorder=3)
    ax.add_patch(const_box)

    const_header = FancyBboxPatch((cg_x, 84.5), cg_w, 5.5, boxstyle="round,pad=0.1,rounding_size=0.6",
                                  facecolor='#EA580C', edgecolor='none', zorder=4)
    ax.add_patch(const_header)
    ax.text(cg_x + cg_w/2, 87.2, "约束观察与处置 (Constraints)", fontsize=8.2, fontweight='bold', color='#FFFFFF', ha='center', va='center', zorder=5)

    # Observation scope subcard
    obs_card = FancyBboxPatch((cg_x + 1.2, 64.5), cg_w - 2.4, 18.0, boxstyle="round,pad=0.2,rounding_size=0.6",
                              facecolor='#FFFFFF', edgecolor='#FDBA74', linewidth=0.9, zorder=4)
    ax.add_patch(obs_card)
    ax.text(cg_x + 2.2, 79.5, "约束观察", fontsize=8.0, fontweight='bold', color='#C2410C', va='center', zorder=5)
    ax.text(cg_x + 2.2, 74.5, "• 观察工作流上下文时序\n• 观察资源属性与状态历史\n【独立旁路观察 · 严禁直穿】", fontsize=6.2, color='#7C2D12', va='center', zorder=5)

    # Action subcard
    act_card = FancyBboxPatch((cg_x + 1.2, 45.0), cg_w - 2.4, 17.5, boxstyle="round,pad=0.2,rounding_size=0.6",
                              facecolor='#FFFFFF', edgecolor='#FB923C', linewidth=0.9, zorder=4)
    ax.add_patch(act_card)
    ax.text(cg_x + 2.2, 59.5, "多级处理动作", fontsize=8.0, fontweight='bold', color='#C2410C', va='center', zorder=5)
    
    # 4 Action badges
    actions = [("警告", '#FEF3C7', '#B45309'), ("报警", '#FFEDD5', '#C2410C'),
               ("停止", '#FEE2E2', '#B91C1C'), ("中止", '#450A0A', '#FEF2F2')]
    for idx, (t, bg, fg) in enumerate(actions):
        bx = cg_x + 2.0 + idx * 4.9
        b_box = FancyBboxPatch((bx, 52.0), 4.4, 4.8, boxstyle="round,pad=0.1,rounding_size=0.4",
                               facecolor=bg, edgecolor='none', zorder=5)
        ax.add_patch(b_box)
        ax.text(bx + 2.2, 54.4, t, fontsize=6.2, fontweight='bold', color=fg, ha='center', va='center', zorder=6)
    ax.text(cg_x + 2.2, 48.0, "依据规则触发降级或联锁", fontsize=6.0, color='#64748B', va='center', zorder=5)

    # Injection card
    inj_card = FancyBboxPatch((cg_x + 1.2, 32.5), cg_w - 2.4, 10.5, boxstyle="round,pad=0.2,rounding_size=0.6",
                              facecolor='#FFFBEB', edgecolor='#F59E0B', linewidth=0.9, zorder=4)
    ax.add_patch(inj_card)
    ax.text(cg_x + cg_w/2, 39.5, "动作回注统一执行机制", fontsize=7.2, fontweight='bold', color='#B45309', ha='center', va='center', zorder=5)
    ax.text(cg_x + cg_w/2, 35.2, "驱动状态机锁存与节点收敛", fontsize=6.0, color='#92400E', ha='center', va='center', zorder=5)

    # Observation connector lines (Orange dashed from Main Chain to Constraints)
    ax.plot([71.0, cg_x], [84.5, 84.5], color='#F97316', linewidth=1.2, linestyle='--', zorder=5)
    ax.scatter([71.0], [84.5], s=12, color='#F97316', zorder=6)

    ax.plot([71.0, cg_x], [61.0, 61.0], color='#F97316', linewidth=1.2, linestyle='--', zorder=5)
    ax.scatter([71.0], [61.0], s=12, color='#F97316', zorder=6)

    ax.plot([71.0, 72.8, 72.8, cg_x], [36.0, 36.0, 70.0, 70.0], color='#F97316', linewidth=1.2, linestyle='--', zorder=5)
    ax.scatter([71.0], [36.0], s=12, color='#F97316', zorder=6)

    # Action Return Arrow: from Constraints back to State Machine
    action_arrow = FancyArrowPatch((cg_x, 48.0), (chain_x + 29.8, 54.0),
                                   connectionstyle="arc3,rad=0.15",
                                   arrowstyle='-|>', mutation_scale=11, linewidth=1.8,
                                   linestyle='--', color='#EA580C', zorder=7)
    ax.add_patch(action_arrow)
    ax.text(72.5, 52.0, "处置回注", fontsize=6.5, fontweight='bold', color='#EA580C', ha='center', va='center', zorder=8)

    # Invariant Summary Card on Bottom-Right
    inv_card = FancyBboxPatch((cg_x, 13.5), cg_w, 15.0, boxstyle="round,pad=0.3,rounding_size=0.8",
                             facecolor='#F8FAFC', edgecolor='#E2E8F0', linewidth=1.0, zorder=3)
    ax.add_patch(inv_card)
    ax.text(cg_x + 2.0, 25.0, "核心结论：不可旁路原则", fontsize=7.5, fontweight='bold', color='#0F172A', va='center', zorder=4)
    ax.text(cg_x + 2.0, 19.5, "• 人/流程/大模型共用设备模型\n• 状态机独占下发，约束闭环回注", fontsize=6.2, color='#475569', va='center', zorder=4)

    # Left Bottom Note
    left_note = FancyBboxPatch((2.2, 13.5), 35.5, 29.0, boxstyle="round,pad=0.3,rounding_size=0.8",
                               facecolor='#F8FAFC', edgecolor='#E2E8F0', linewidth=1.0, zorder=3)
    ax.add_patch(left_note)
    ax.text(4.0, 38.5, "统一执行链路关键机制", fontsize=7.8, fontweight='bold', color='#1E3A8A', va='center', zorder=4)
    ax.text(4.0, 27.5, "1. 共同执行入口：避免产生人工或大模型需先变为\n   工作流节点的误解，三者同一层级接入模型。\n2. 状态机中枢：所有发令必须穿透状态机。\n3. 原路闭环：反馈由物理层逆向逐级回传并推进。",
            fontsize=6.2, color='#334155', va='center', zorder=4)

    plt.tight_layout(pad=0.2)
    p_png = os.path.join(OUT_DIR, "figure_1_unified_execution.png")
    p_art = os.path.join(ARTIFACT_DIR, "figure_1_unified_execution.png")
    plt.savefig(p_png, dpi=300, facecolor=fig.get_facecolor(), edgecolor='none')
    plt.savefig(p_art, dpi=300, facecolor=fig.get_facecolor(), edgecolor='none')
    plt.close()
    print("Generated Figure 1:", p_png)


# ======================================================================
# Figure 2: 加热任务执行时序 (PPT 第 9 页)
# ======================================================================
def create_figure_2():
    fig, ax = plt.subplots(figsize=(7.35, 3.90), dpi=300)
    fig.patch.set_facecolor('#FFFFFF')
    ax.set_facecolor('#FFFFFF')
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    # Outer Border Card
    outer_card = FancyBboxPatch((0.8, 0.8), 98.4, 98.4, boxstyle="round,pad=0.3,rounding_size=1.2",
                                facecolor='#FFFFFF', edgecolor='#E2E8F0', linewidth=1.2, zorder=1)
    ax.add_patch(outer_card)

    # Title Bar
    title_bar = FancyBboxPatch((2.2, 92.5), 0.7, 4.2, boxstyle="round,pad=0.1,rounding_size=0.3",
                               facecolor='#1E3A8A', edgecolor='none', zorder=3)
    ax.add_patch(title_bar)
    ax.text(4.0, 94.6, "正式图 2：加热任务执行时序", fontsize=11.5, fontweight='bold', color='#0F172A', va='center', zorder=4)
    ax.text(28.5, 94.4, "(控制下发 · 遥测回传 · 条件判断推进 · 异常约束介入)", fontsize=8.5, color='#64748B', va='center', zorder=4)

    # 4 Lifelines X coordinates
    lx = [14.0, 36.0, 58.0, 80.0]
    headers = [
        ("加热节点", "Workflow Node", '#EFF6FF', '#2563EB', '#1E40AF'),
        ("设备状态机", "State Machine (Core)", '#F5F3FF', '#7C3AED', '#5B21B6'),
        ("Adapter", "边缘适配层", '#F0FDF4', '#059669', '#065F46'),
        ("反应釜", "物理仪器 / PLC", '#1E293B', '#0F172A', '#F8FAFC')
    ]

    # Draw Lifeline Headers & Lines
    for i in range(4):
        title, sub, bg, border, fg = headers[i]
        hb = FancyBboxPatch((lx[i] - 9.0, 81.0), 18.0, 9.5, boxstyle="round,pad=0.3,rounding_size=0.8",
                            facecolor=bg, edgecolor=border, linewidth=1.2, zorder=4)
        ax.add_patch(hb)
        ax.text(lx[i], 86.8, title, fontsize=8.5, fontweight='bold', color=fg, ha='center', va='center', zorder=5)
        ax.text(lx[i], 83.2, sub, fontsize=6.2, color=border if fg != '#F8FAFC' else '#94A3B8', ha='center', va='center', zorder=5)

        # Vertical dashed lifeline
        ax.plot([lx[i], lx[i]], [26.0, 81.0], color='#CBD5E1', linewidth=1.2, linestyle='--', zorder=2)

    # Lifeline Activation bars (slim rectangles on lifelines)
    ax.add_patch(FancyBboxPatch((lx[0] - 0.7, 30.0), 1.4, 46.0, boxstyle="round,pad=0.1,rounding_size=0.3",
                                facecolor='#DBEAFE', edgecolor='#2563EB', linewidth=0.8, zorder=3))
    ax.add_patch(FancyBboxPatch((lx[1] - 0.7, 32.0), 1.4, 42.0, boxstyle="round,pad=0.1,rounding_size=0.3",
                                facecolor='#EDE9FE', edgecolor='#7C3AED', linewidth=0.8, zorder=3))
    ax.add_patch(FancyBboxPatch((lx[2] - 0.7, 39.0), 1.4, 30.0, boxstyle="round,pad=0.1,rounding_size=0.3",
                                facecolor='#D1FAE5', edgecolor='#059669', linewidth=0.8, zorder=3))
    ax.add_patch(FancyBboxPatch((lx[3] - 0.7, 47.0), 1.4, 16.0, boxstyle="round,pad=0.1,rounding_size=0.3",
                                facecolor='#E2E8F0', edgecolor='#475569', linewidth=0.8, zorder=3))

    # 6 Numbered Messages (Top to bottom)
    # Message 1: 加热节点 -> 状态机: 1. 请求加热
    y1 = 74.0
    ax.add_patch(FancyArrowPatch((lx[0] + 0.7, y1), (lx[1] - 0.7, y1),
                                 arrowstyle='-|>', mutation_scale=10, linewidth=1.6, color='#2563EB', zorder=6))
    mb1 = FancyBboxPatch(((lx[0] + lx[1])/2 - 7.5, y1 + 1.0), 15.0, 4.5, boxstyle="round,pad=0.1,rounding_size=0.4",
                         facecolor='#EFF6FF', edgecolor='#93C5FD', linewidth=0.8, zorder=7)
    ax.add_patch(mb1)
    ax.text((lx[0] + lx[1])/2, y1 + 3.2, "1. 请求加热 (WF_START)", fontsize=6.8, fontweight='bold', color='#1D4ED8', ha='center', va='center', zorder=8)

    # Message 2: 状态机 -> Adapter: 2. 下发加热命令
    y2 = 66.0
    ax.add_patch(FancyArrowPatch((lx[1] + 0.7, y2), (lx[2] - 0.7, y2),
                                 arrowstyle='-|>', mutation_scale=10, linewidth=1.6, color='#2563EB', zorder=6))
    mb2 = FancyBboxPatch(((lx[1] + lx[2])/2 - 8.5, y2 + 1.0), 17.0, 4.5, boxstyle="round,pad=0.1,rounding_size=0.4",
                         facecolor='#EFF6FF', edgecolor='#93C5FD', linewidth=0.8, zorder=7)
    ax.add_patch(mb2)
    ax.text((lx[1] + lx[2])/2, y2 + 3.2, "2. 下发加热命令 (CMD_START)", fontsize=6.8, fontweight='bold', color='#1D4ED8', ha='center', va='center', zorder=8)

    # Message 3: Adapter -> 反应釜: 3. 执行加热
    y3 = 58.0
    ax.add_patch(FancyArrowPatch((lx[2] + 0.7, y3), (lx[3] - 0.7, y3),
                                 arrowstyle='-|>', mutation_scale=10, linewidth=1.6, color='#2563EB', zorder=6))
    mb3 = FancyBboxPatch(((lx[2] + lx[3])/2 - 7.5, y3 + 1.0), 15.0, 4.5, boxstyle="round,pad=0.1,rounding_size=0.4",
                         facecolor='#EFF6FF', edgecolor='#93C5FD', linewidth=0.8, zorder=7)
    ax.add_patch(mb3)
    ax.text((lx[2] + lx[3])/2, y3 + 3.2, "3. 执行加热 (物理控制帧)", fontsize=6.8, fontweight='bold', color='#1D4ED8', ha='center', va='center', zorder=8)

    # Message 4: 反应釜 -> Adapter: 4. 温度 / 运行状态
    y4 = 49.0
    ax.add_patch(FancyArrowPatch((lx[3] - 0.7, y4), (lx[2] + 0.7, y4),
                                 arrowstyle='-|>', mutation_scale=10, linewidth=1.6, color='#16A34A', zorder=6))
    mb4 = FancyBboxPatch(((lx[2] + lx[3])/2 - 9.0, y4 + 1.0), 18.0, 4.5, boxstyle="round,pad=0.1,rounding_size=0.4",
                         facecolor='#F0FDF4', edgecolor='#86EFAC', linewidth=0.8, zorder=7)
    ax.add_patch(mb4)
    ax.text((lx[2] + lx[3])/2, y4 + 3.2, "4. 温度 / 运行状态 (遥测)", fontsize=6.8, fontweight='bold', color='#15803D', ha='center', va='center', zorder=8)
    ax.text((lx[2] + lx[3])/2, y4 - 2.2, "（持续遥测采样，非任务完成）", fontsize=5.8, color='#64748B', ha='center', va='center', zorder=8)

    # Message 5: Adapter -> 状态机: 5. 反馈更新
    y5 = 40.0
    ax.add_patch(FancyArrowPatch((lx[2] - 0.7, y5), (lx[1] + 0.7, y5),
                                 arrowstyle='-|>', mutation_scale=10, linewidth=1.6, color='#16A34A', zorder=6))
    mb5 = FancyBboxPatch(((lx[1] + lx[2])/2 - 7.5, y5 + 1.0), 15.0, 4.5, boxstyle="round,pad=0.1,rounding_size=0.4",
                         facecolor='#F0FDF4', edgecolor='#86EFAC', linewidth=0.8, zorder=7)
    ax.add_patch(mb5)
    ax.text((lx[1] + lx[2])/2, y5 + 3.2, "5. 反馈更新 (event/telemetry)", fontsize=6.8, fontweight='bold', color='#15803D', ha='center', va='center', zorder=8)

    # Message 6: 状态机 -> 加热节点: 6. 执行状态
    y6 = 32.0
    ax.add_patch(FancyArrowPatch((lx[1] - 0.7, y6), (lx[0] + 0.7, y6),
                                 arrowstyle='-|>', mutation_scale=10, linewidth=1.6, color='#16A34A', zorder=6))
    mb6 = FancyBboxPatch(((lx[0] + lx[1])/2 - 7.5, y6 + 1.0), 15.0, 4.5, boxstyle="round,pad=0.1,rounding_size=0.4",
                         facecolor='#F0FDF4', edgecolor='#86EFAC', linewidth=0.8, zorder=7)
    ax.add_patch(mb6)
    ax.text((lx[0] + lx[1])/2, y6 + 3.2, "6. 执行状态 (CMD_STATE)", fontsize=6.8, fontweight='bold', color='#15803D', ha='center', va='center', zorder=8)

    # ----------------------------------------------------
    # Step 6 Critical Condition Check Callout (Bottom-Left)
    # ----------------------------------------------------
    callout = FancyBboxPatch((2.2, 5.5), 44.0, 18.0, boxstyle="round,pad=0.3,rounding_size=0.8",
                             facecolor='#EFF6FF', edgecolor='#3B82F6', linewidth=1.2, zorder=4)
    ax.add_patch(callout)
    ax.text(4.0, 19.5, "★ 关键结论：反馈到达 ≠ 任务自动完成", fontsize=7.8, fontweight='bold', color='#1E40AF', va='center', zorder=5)
    ax.text(4.0, 14.5, "• 节点依据预设条件判断：如「温度达标(80℃) 且维持10分钟」\n• 满足完成条件后，节点才推进后续步骤 (触发出接口 ACTIVE 信号)",
            fontsize=6.5, color='#1D4ED8', va='center', zorder=5)
    ax.text(4.0, 8.5, "• 任务生命周期由工作流条件与安全约束共同仲裁，而非硬件单方决定", fontsize=6.0, color='#64748B', va='center', zorder=5)

    # ----------------------------------------------------
    # Orange Constraint Branch (Bottom-Right)
    # ----------------------------------------------------
    ob_box = FancyBboxPatch((48.5, 5.5), 49.0, 18.0, boxstyle="round,pad=0.3,rounding_size=0.8",
                            facecolor='#FFF7ED', edgecolor='#EA580C', linewidth=1.4, zorder=4)
    ax.add_patch(ob_box)
    circ_warn = Circle((51.2, 19.5), 1.4, facecolor='#EA580C', edgecolor='none', zorder=5)
    ax.add_patch(circ_warn)
    ax.text(53.5, 19.5, "约束介入分支：温度越界 / 设备异常", fontsize=7.8, fontweight='bold', color='#9A3412', va='center', zorder=5)
    ax.text(50.0, 14.5, "• 触发动作：按规则采取停止、报警或安全降温 (非串行旁路)\n• 必须回到「设备状态机」统一处理 (异常锁存/优雅中止)，严禁直穿物理层",
            fontsize=6.5, color='#C2410C', va='center', zorder=5)
    ax.text(50.0, 8.5, "【保证异常状态全局可见、审计留痕，状态机收敛终态后安全解锁】", fontsize=6.0, fontweight='bold', color='#B45309', va='center', zorder=5)

    # Orange arrow looping back from Constraint Box to State Machine
    ax.add_patch(FancyArrowPatch((70.0, 23.5), (lx[1] + 1.5, 27.0),
                                 connectionstyle="arc3,rad=-0.25",
                                 arrowstyle='-|>', mutation_scale=11, linewidth=1.6,
                                 linestyle='--', color='#EA580C', zorder=7))
    ax.text(52.0, 25.5, "异常回注状态机", fontsize=6.5, fontweight='bold', color='#EA580C', ha='center', va='center', zorder=8)

    # Legend at Top-Right
    ax.plot([72.0, 75.5], [94.5, 94.5], color='#2563EB', linewidth=1.8, zorder=4)
    ax.text(76.2, 94.5, "控制请求", fontsize=6.5, color='#1E3A8A', va='center', zorder=4)

    ax.plot([82.0, 85.5], [94.5, 94.5], color='#16A34A', linewidth=1.8, zorder=4)
    ax.text(86.2, 94.5, "状态反馈", fontsize=6.5, color='#15803D', va='center', zorder=4)

    ax.plot([92.0, 95.5], [94.5, 94.5], color='#EA580C', linewidth=1.6, linestyle='--', zorder=4)
    ax.text(96.2, 94.5, "约束介入", fontsize=6.5, color='#C2410C', va='center', zorder=4)

    plt.tight_layout(pad=0.2)
    p_png = os.path.join(OUT_DIR, "figure_2_heating_sequence.png")
    p_art = os.path.join(ARTIFACT_DIR, "figure_2_heating_sequence.png")
    plt.savefig(p_png, dpi=300, facecolor=fig.get_facecolor(), edgecolor='none')
    plt.savefig(p_art, dpi=300, facecolor=fig.get_facecolor(), edgecolor='none')
    plt.close()
    print("Generated Figure 2:", p_png)


# ======================================================================
# Figure 3: 仿真反馈闭环 (PPT 第 10 页)
# ======================================================================
def create_figure_3():
    fig, ax = plt.subplots(figsize=(7.35, 3.90), dpi=300)
    fig.patch.set_facecolor('#FFFFFF')
    ax.set_facecolor('#FFFFFF')
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    # Outer Border Card
    outer_card = FancyBboxPatch((0.8, 0.8), 98.4, 98.4, boxstyle="round,pad=0.3,rounding_size=1.2",
                                facecolor='#FFFFFF', edgecolor='#E2E8F0', linewidth=1.2, zorder=1)
    ax.add_patch(outer_card)

    # Title Bar
    title_bar = FancyBboxPatch((2.2, 92.5), 0.7, 4.2, boxstyle="round,pad=0.1,rounding_size=0.3",
                               facecolor='#1E3A8A', edgecolor='none', zorder=3)
    ax.add_patch(title_bar)
    ax.text(4.0, 94.6, "正式图 3：仿真反馈闭环", fontsize=11.5, fontweight='bold', color='#0F172A', va='center', zorder=4)
    ax.text(26.0, 94.4, "(共用上层模型与规则 · 模拟闭环调整方案 · 满足条件平滑上机)", fontsize=8.5, color='#64748B', va='center', zorder=4)

    # ----------------------------------------------------
    # 1. TOP: Common Model Area (共同模型区: X: 24.0 to 73.0, Y: 56.0 to 90.0)
    # ----------------------------------------------------
    cm_x = 25.0
    cm_w = 48.0
    cm_box = FancyBboxPatch((cm_x, 57.0), cm_w, 32.0, boxstyle="round,pad=0.3,rounding_size=1.0",
                            facecolor='#F8FAFC', edgecolor='#1E3A8A', linewidth=1.8, zorder=3)
    ax.add_patch(cm_box)

    cm_header = FancyBboxPatch((cm_x, 83.0), cm_w, 6.0, boxstyle="round,pad=0.1,rounding_size=0.6",
                               facecolor='#1E3A8A', edgecolor='none', zorder=4)
    ax.add_patch(cm_header)
    ax.text(cm_x + cm_w/2, 86.0, "共同模型区（仿真 / 实机共用上层模型与规则）", fontsize=8.5, fontweight='bold', color='#FFFFFF', ha='center', va='center', zorder=5)

    # 3 Sub-blocks in Common Model Area
    sub_w = 14.5
    # Sub 1: Workflow
    s1 = FancyBboxPatch((cm_x + 1.2, 59.0), sub_w, 22.0, boxstyle="round,pad=0.2,rounding_size=0.6",
                        facecolor='#FFFFFF', edgecolor='#2563EB', linewidth=1.0, zorder=4)
    ax.add_patch(s1)
    ax.text(cm_x + 8.45, 77.0, "工作流定义", fontsize=7.8, fontweight='bold', color='#1E40AF', ha='center', va='center', zorder=5)
    ax.text(cm_x + 8.45, 68.0, "步骤时序编排\n接口控制信号\n端口数据拉取", fontsize=6.2, color='#475569', ha='center', va='center', zorder=5)

    # Sub 2: Capability & State Machine
    s2 = FancyBboxPatch((cm_x + 16.7, 59.0), sub_w, 22.0, boxstyle="round,pad=0.2,rounding_size=0.6",
                        facecolor='#EFF6FF', edgecolor='#2563EB', linewidth=1.2, zorder=4)
    ax.add_patch(s2)
    ax.text(cm_x + 23.95, 77.0, "设备能力与状态机", fontsize=7.8, fontweight='bold', color='#1E40AF', ha='center', va='center', zorder=5)
    ax.text(cm_x + 23.95, 68.0, "统一设备物模型\n发令状态机引擎\n(业务/生命周期)", fontsize=6.2, color='#2563EB', ha='center', va='center', zorder=5)

    # Sub 3: Constraints
    s3 = FancyBboxPatch((cm_x + 32.2, 59.0), sub_w, 22.0, boxstyle="round,pad=0.2,rounding_size=0.6",
                        facecolor='#FFF7ED', edgecolor='#EA580C', linewidth=1.0, zorder=4)
    ax.add_patch(s3)
    ax.text(cm_x + 39.45, 77.0, "安全约束规则", fontsize=7.8, fontweight='bold', color='#C2410C', ha='center', va='center', zorder=5)
    ax.text(cm_x + 39.45, 68.0, "内禀/基线约束\n时序窗口防抖\n统一合规底线", fontsize=6.2, color='#7C2D12', ha='center', va='center', zorder=5)

    # ----------------------------------------------------
    # 2. LEFT: LLM Decision Maker (大模型决策: X: 2.2 to 21.0, Y: 38.0 to 88.0)
    # ----------------------------------------------------
    llm_box = FancyBboxPatch((2.2, 40.0), 19.5, 49.0, boxstyle="round,pad=0.3,rounding_size=1.0",
                            facecolor='#FAF5FF', edgecolor='#7C3AED', linewidth=1.6, zorder=3)
    ax.add_patch(llm_box)
    llm_header = FancyBboxPatch((2.2, 83.0), 19.5, 6.0, boxstyle="round,pad=0.1,rounding_size=0.6",
                                facecolor='#7C3AED', edgecolor='none', zorder=4)
    ax.add_patch(llm_header)
    ax.text(11.95, 86.0, "大模型决策 (LLM Agent)", fontsize=8.2, fontweight='bold', color='#FFFFFF', ha='center', va='center', zorder=5)

    # Inside LLM Box
    gen_box = FancyBboxPatch((3.4, 63.5), 17.1, 17.5, boxstyle="round,pad=0.2,rounding_size=0.6",
                             facecolor='#FFFFFF', edgecolor='#C4B5FD', linewidth=0.9, zorder=4)
    ax.add_patch(gen_box)
    ax.text(11.95, 77.0, "自主方案规划", fontsize=7.5, fontweight='bold', color='#5B21B6', ha='center', va='center', zorder=5)
    ax.text(11.95, 70.0, "生成实验任务参数\n编排工艺步骤流程", fontsize=6.2, color='#6D28D9', ha='center', va='center', zorder=5)

    adj_box = FancyBboxPatch((3.4, 43.0), 17.1, 18.5, boxstyle="round,pad=0.2,rounding_size=0.6",
                             facecolor='#F5F3FF', edgecolor='#8B5CF6', linewidth=0.9, zorder=4)
    ax.add_patch(adj_box)
    ax.text(11.95, 57.0, "接收反馈并动态调整", fontsize=7.5, fontweight='bold', color='#5B21B6', ha='center', va='center', zorder=5)
    ax.text(11.95, 49.5, "依据仿真数据评估\n优化反应条件与配方", fontsize=6.2, color='#4C1D95', ha='center', va='center', zorder=5)

    # Blue Forward Arrow: LLM Decision -> Common Model Area
    ax.add_patch(FancyArrowPatch((21.7, 72.0), (cm_x, 72.0),
                                 arrowstyle='-|>', mutation_scale=11, linewidth=1.8, color='#2563EB', zorder=6))
    ax.text(23.3, 74.0, "下发方案", fontsize=6.5, fontweight='bold', color='#1D4ED8', ha='center', va='center', zorder=7)

    # ----------------------------------------------------
    # 3. BOTTOM: Simulation Environment (仿真 Adapter + 模拟环境: X: 25.0 to 65.0, Y: 8.0 to 45.0)
    # ----------------------------------------------------
    sim_x = 25.0
    sim_w = 40.0
    sim_box = FancyBboxPatch((sim_x, 8.0), sim_w, 36.0, boxstyle="round,pad=0.3,rounding_size=1.0",
                             facecolor='#F0FDF4', edgecolor='#16A34A', linewidth=1.6, zorder=3)
    ax.add_patch(sim_box)

    sim_header = FancyBboxPatch((sim_x, 38.0), sim_w, 6.0, boxstyle="round,pad=0.1,rounding_size=0.6",
                                facecolor='#16A34A', edgecolor='none', zorder=4)
    ax.add_patch(sim_header)
    ax.text(sim_x + sim_w/2, 41.0, "仿真执行环境（数字孪生 / 虚拟仿真）", fontsize=8.2, fontweight='bold', color='#FFFFFF', ha='center', va='center', zorder=5)

    # Sub-block 1: 仿真 Adapter
    sa_box = FancyBboxPatch((sim_x + 1.5, 10.5), 17.5, 25.5, boxstyle="round,pad=0.2,rounding_size=0.6",
                            facecolor='#FFFFFF', edgecolor='#86EFAC', linewidth=1.0, zorder=4)
    ax.add_patch(sa_box)
    ax.text(sim_x + 10.25, 31.5, "仿真 Adapter", fontsize=8.0, fontweight='bold', color='#15803D', ha='center', va='center', zorder=5)
    ax.text(sim_x + 10.25, 21.0, "接收上层统一发令\n模拟边缘协议交互\n转换虚拟遥测帧\n(与实机契约对齐)", fontsize=6.2, color='#047857', ha='center', va='center', zorder=5)

    # Arrow between Sim Adapter and Simulator
    ax.add_patch(FancyArrowPatch((sim_x + 19.0, 23.0), (sim_x + 21.0, 23.0),
                                 arrowstyle='-|>', mutation_scale=9, linewidth=1.4, color='#16A34A', zorder=5))

    # Sub-block 2: 模拟设备 / 环境
    se_box = FancyBboxPatch((sim_x + 21.0, 10.5), 17.5, 25.5, boxstyle="round,pad=0.2,rounding_size=0.6",
                            facecolor='#FFFFFF', edgecolor='#86EFAC', linewidth=1.0, zorder=4)
    ax.add_patch(se_box)
    ax.text(sim_x + 29.75, 31.5, "模拟设备 / 物理环境", fontsize=8.0, fontweight='bold', color='#15803D', ha='center', va='center', zorder=5)
    ax.text(sim_x + 29.75, 21.0, "热力学/动力学数值求解\n模拟温升与流场响应\n预演潜在超温与冲突\n(高保真环境计算)", fontsize=6.2, color='#047857', ha='center', va='center', zorder=5)

    # Downward Blue Arrow: Common Model Area -> Simulation Adapter
    ax.add_patch(FancyArrowPatch((cm_x + 10.0, 57.0), (cm_x + 10.0, 44.0),
                                 arrowstyle='-|>', mutation_scale=10, linewidth=1.6, color='#2563EB', zorder=5))
    ax.text(cm_x + 11.5, 50.5, "下发仿真指令", fontsize=6.5, fontweight='bold', color='#1D4ED8', va='center', zorder=6)

    # ----------------------------------------------------
    # MAIN CLOSED LOOP FEEDBACK (Green Solid Arrow returning to LLM)
    # ----------------------------------------------------
    sim_fb_arrow = FancyArrowPatch((sim_x + 1.5, 23.0), (12.0, 40.0),
                                   connectionstyle="arc3,rad=0.35",
                                   arrowstyle='-|>', mutation_scale=13, linewidth=2.2, color='#16A34A', zorder=8)
    ax.add_patch(sim_fb_arrow)
    
    # Feedback Callout Badge
    fb_badge = FancyBboxPatch((5.0, 20.0), 16.0, 11.0, boxstyle="round,pad=0.2,rounding_size=0.6",
                              facecolor='#F0FDF4', edgecolor='#86EFAC', linewidth=1.0, zorder=9)
    ax.add_patch(fb_badge)
    ax.text(13.0, 27.0, "模拟反馈 (闭环)", fontsize=7.2, fontweight='bold', color='#15803D', ha='center', va='center', zorder=10)
    ax.text(13.0, 23.0, "状态 · 工艺数据\n约束校验结果", fontsize=5.8, color='#047857', ha='center', va='center', zorder=10)

    # ----------------------------------------------------
    # 4. RIGHT: Gateway & Real Hardware Execution
    # ----------------------------------------------------
    # Gateway Box (满足预设上机条件)
    gw_x = 76.5
    gw_y = 66.0
    gw_w = 18.0
    gw_h = 14.0
    gw_box = FancyBboxPatch((gw_x, gw_y), gw_w, gw_h, boxstyle="round,pad=0.3,rounding_size=1.2",
                            facecolor='#FEF3C7', edgecolor='#D97706', linewidth=1.5, zorder=4)
    ax.add_patch(gw_box)
    ax.text(gw_x + gw_w/2, gw_y + 9.5, "满足预设", fontsize=8.0, fontweight='bold', color='#92400E', ha='center', va='center', zorder=5)
    ax.text(gw_x + gw_w/2, gw_y + 5.0, "上机条件？", fontsize=8.0, fontweight='bold', color='#92400E', ha='center', va='center', zorder=5)

    # Blue arrow from Common Model to Gateway
    ax.add_patch(FancyArrowPatch((cm_x + cm_w, 73.0), (gw_x, 73.0),
                                 arrowstyle='-|>', mutation_scale=10, linewidth=1.6, color='#2563EB', zorder=5))

    # Green dashed arrow from Simulation Environment to Gateway as evaluation basis
    sim_eval_arrow = FancyArrowPatch((sim_x + sim_w - 2.0, 36.0), (gw_x + 4.0, gw_y),
                                     connectionstyle="arc3,rad=-0.2",
                                     arrowstyle='-|>', mutation_scale=10, linewidth=1.5,
                                     linestyle='--', color='#16A34A', zorder=7)
    ax.add_patch(sim_eval_arrow)
    ax.text(70.0, 48.0, "仿真评估合格\n(作为上机依据)", fontsize=6.2, fontweight='bold', color='#15803D', ha='center', va='center', zorder=8)

    # Real Hardware System Container (Bottom Right: X: 76.5 to 97.5, Y: 8.0 to 56.0)
    rw_x = 76.5
    rw_w = 21.0
    rw_box = FancyBboxPatch((rw_x, 8.0), rw_w, 46.0, boxstyle="round,pad=0.3,rounding_size=1.0",
                            facecolor='#F8FAFC', edgecolor='#0F172A', linewidth=1.6, zorder=3)
    ax.add_patch(rw_box)
    rw_header = FancyBboxPatch((rw_x, 48.0), rw_w, 6.0, boxstyle="round,pad=0.1,rounding_size=0.6",
                               facecolor='#0F172A', edgecolor='none', zorder=4)
    ax.add_patch(rw_header)
    ax.text(rw_x + rw_w/2, 51.0, "实机执行系统", fontsize=8.2, fontweight='bold', color='#FFFFFF', ha='center', va='center', zorder=5)

    # Sub 1: 实机 Adapter
    ra_box = FancyBboxPatch((rw_x + 1.5, 30.0), rw_w - 3.0, 16.0, boxstyle="round,pad=0.2,rounding_size=0.6",
                            facecolor='#FFFFFF', edgecolor='#059669', linewidth=1.0, zorder=4)
    ax.add_patch(ra_box)
    ax.text(rw_x + rw_w/2, 40.5, "实机 Adapter", fontsize=7.8, fontweight='bold', color='#065F46', ha='center', va='center', zorder=5)
    ax.text(rw_x + rw_w/2, 34.0, "物理总线协议映射\n下发真实物理驱动命令", fontsize=6.0, color='#047857', ha='center', va='center', zorder=5)

    # Downward arrow from Gateway to Real Adapter
    ax.add_patch(FancyArrowPatch((gw_x + gw_w/2, gw_y), (rw_x + rw_w/2, 46.0),
                                 arrowstyle='-|>', mutation_scale=10, linewidth=1.6, color='#2563EB', zorder=6))
    ax.text(gw_x + gw_w/2 + 3.0, 60.0, "是 (Yes)", fontsize=6.5, fontweight='bold', color='#1D4ED8', va='center', zorder=7)

    # Sub 2: 真实物理仪器
    rp_box = FancyBboxPatch((rw_x + 1.5, 10.5), rw_w - 3.0, 16.0, boxstyle="round,pad=0.2,rounding_size=0.6",
                            facecolor='#1E293B', edgecolor='#0F172A', linewidth=1.0, zorder=4)
    ax.add_patch(rp_box)
    ax.text(rw_x + rw_w/2, 21.0, "真实物理仪器 / 反应釜", fontsize=7.8, fontweight='bold', color='#F8FAFC', ha='center', va='center', zorder=5)
    ax.text(rw_x + rw_w/2, 14.5, "执行真实物化实验操作\n产生真实世界传感器数据", fontsize=6.0, color='#94A3B8', ha='center', va='center', zorder=5)

    # Arrow from Real Adapter to Physical Equipment
    ax.add_patch(FancyArrowPatch((rw_x + rw_w/2, 30.0), (rw_x + rw_w/2, 26.5),
                                 arrowstyle='-|>', mutation_scale=9, linewidth=1.4, color='#2563EB', zorder=6))

    # Real Hardware Feedback back up into Common Model Area
    real_fb_arrow = FancyArrowPatch((rw_x + 1.5, 38.0), (cm_x + cm_w, 62.0),
                                    connectionstyle="arc3,rad=-0.25",
                                    arrowstyle='-|>', mutation_scale=10, linewidth=1.6, color='#16A34A', zorder=7)
    ax.add_patch(real_fb_arrow)
    ax.text(71.5, 56.0, "实机反馈\n(保持模型一致)", fontsize=6.2, fontweight='bold', color='#15803D', ha='center', va='center', zorder=8)

    # Bottom Architectural Takeaway Ribbon
    banner = FancyBboxPatch((25.0, 2.5), 48.0, 4.2, boxstyle="round,pad=0.1,rounding_size=0.4",
                            facecolor='#EFF6FF', edgecolor='#BFDBFE', linewidth=0.8, zorder=4)
    ax.add_patch(banner)
    ax.text(49.0, 4.6, "★ 架构结论：仿真与实机共用模型与约束规则，扩展点仅在 Adapter", fontsize=6.8, fontweight='bold', color='#1E40AF', ha='center', va='center', zorder=5)

    plt.tight_layout(pad=0.2)
    p_png = os.path.join(OUT_DIR, "figure_3_simulation_closed_loop.png")
    p_art = os.path.join(ARTIFACT_DIR, "figure_3_simulation_closed_loop.png")
    plt.savefig(p_png, dpi=300, facecolor=fig.get_facecolor(), edgecolor='none')
    plt.savefig(p_art, dpi=300, facecolor=fig.get_facecolor(), edgecolor='none')
    plt.close()
    print("Generated Figure 3:", p_png)


# ======================================================================
# Update PPTX Slide 8, 9, 10
# ======================================================================
def update_pptx():
    pptx_path = r"d:\SmartLab2.0\doc\output\EUROSIM2026_于涛_汇报修订版_含正式图占位.pptx"
    if not os.path.exists(pptx_path):
        print("PPTX not found:", pptx_path)
        return

    prs = Presentation(pptx_path)
    print(f"Loaded PPTX with {len(prs.slides)} slides.")

    # Slides to update: Slide 8 (idx 7), Slide 9 (idx 8), Slide 10 (idx 9)
    img_map = {
        7: ("FORMAL_FIGURE_1_PLACEHOLDER", os.path.join(OUT_DIR, "figure_1_unified_execution.png"), ["text-88", "text-89"]),
        8: ("FORMAL_FIGURE_2_PLACEHOLDER", os.path.join(OUT_DIR, "figure_2_heating_sequence.png"), ["text-99", "text-100"]),
        9: ("FORMAL_FIGURE_3_PLACEHOLDER", os.path.join(OUT_DIR, "figure_3_simulation_closed_loop.png"), ["text-110", "text-111"])
    }

    for slide_idx, (placeholder_name, img_path, text_names_to_clear) in img_map.items():
        slide = prs.slides[slide_idx]
        target_ph = None
        for shape in list(slide.shapes):
            if shape.name == placeholder_name:
                target_ph = shape
            elif shape.name in text_names_to_clear:
                # Clear placeholder text
                shape.text_frame.text = ""

        if target_ph and os.path.exists(img_path):
            left = target_ph.left
            top = target_ph.top
            width = target_ph.width
            height = target_ph.height
            # Add image in exact place
            slide.shapes.add_picture(img_path, left, top, width=width, height=height)
            print(f"Inserted image on slide {slide_idx+1}: {img_path}")

    out_pptx = r"d:\SmartLab2.0\doc\output\EUROSIM2026_于涛_汇报_含3张正式图.pptx"
    prs.save(out_pptx)
    print(f"Saved updated PPTX with all 3 formal figures to: {out_pptx}")


if __name__ == "__main__":
    create_figure_1()
    create_figure_2()
    create_figure_3()
    update_pptx()
