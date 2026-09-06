#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
SmartLab 2.0 系统总体架构图 — 黑白线稿版
所有元素用方框表示，无色彩填充
"""

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle, FancyArrowPatch
from matplotlib.font_manager import FontProperties
import os, sys

sys.stdout.reconfigure(encoding='utf-8')

# ─── Setup ───
FIG_W, FIG_H = 36, 25
fig, ax = plt.subplots(figsize=(FIG_W, FIG_H))
ax.set_xlim(0, FIG_W)
ax.set_ylim(0, FIG_H)
ax.set_aspect('equal')
ax.axis('off')
fig.patch.set_facecolor('white')

def F(sz=9, w='normal'):
    return FontProperties(family='Microsoft YaHei', size=sz, weight=w)

# ─── Helpers ───

def box(x, y, w, h, lw=1.0, ls='-', zorder=2):
    r = Rectangle((x, y), w, h, fc='none', ec='black', lw=lw, ls=ls, zorder=zorder)
    ax.add_patch(r)

def txt(x, y, s, fs=9, ha='center', va='center', w='normal', zorder=6):
    ax.text(x, y, s, fontproperties=F(fs, w), color='black', ha=ha, va=va, zorder=zorder)

def lbox(x, y, w, h, title, sub='', detail='', tfs=10, sfs=8, dfs=7, lw=1.0):
    """Labeled box: title (bold) + subtitle + detail."""
    box(x, y, w, h, lw=lw)
    cx = x + w / 2
    if detail and sub:
        txt(cx, y + h * 0.72, title, fs=tfs, w='bold')
        txt(cx, y + h * 0.50, sub, fs=sfs)
        txt(cx, y + h * 0.22, detail, fs=dfs)
    elif sub:
        txt(cx, y + h * 0.60, title, fs=tfs, w='bold')
        txt(cx, y + h * 0.35, sub, fs=sfs)
    elif detail:
        txt(cx, y + h * 0.65, title, fs=tfs, w='bold')
        txt(cx, y + h * 0.30, detail, fs=dfs)
    else:
        txt(cx, y + h / 2, title, fs=tfs, w='bold')

def arrow(x1, y1, x2, y2, label='', fs=7, lw=0.8, lo=(0, 0.2), style='->'):
    a = FancyArrowPatch((x1, y1), (x2, y2), arrowstyle=style,
                         connectionstyle='arc3,rad=0', color='black',
                         lw=lw, zorder=5, mutation_scale=10)
    ax.add_patch(a)
    if label:
        mx = (x1 + x2) / 2 + lo[0]
        my = (y1 + y2) / 2 + lo[1]
        ax.text(mx, my, label, fontproperties=F(fs), color='black',
                ha='center', va='center', zorder=7,
                bbox=dict(fc='white', ec='none', pad=0.5))


# ═══════════════════════════════════════════════════════
#  TITLE
# ═══════════════════════════════════════════════════════
txt(18, 24.3, 'SmartLab 2.0 智能实验室系统总体架构图', fs=15, w='bold')
txt(18, 23.7, '(基于可执行建模与运行时全链路闭环)', fs=11)
txt(18, 23.2, '下述标注：斜体为主表，正常标注为引擎/工具集，括号内为中文说明', fs=7)


# ═══════════════════════════════════════════════════════
#  TOP BARS — 4 operational busses
# ═══════════════════════════════════════════════════════
bx0, bw = 4, 27
bars_data = [
    (22.4, 'Manage (管理层管理)'),
    (21.9, 'Storage (主数据存储操作)'),
    (21.4, 'Operation (作业与监控管控)'),
    (20.9, 'Operations (实际操控与引擎执行)'),
]
for by, bl in bars_data:
    box(bx0, by, bw, 0.35, lw=0.7)
    txt(bx0 + bw / 2, by + 0.175, bl, fs=7)


# ═══════════════════════════════════════════════════════
#  A. LEFT UPPER — 核心知识与模型运行层
#     (Storage & Model Layer)
# ═══════════════════════════════════════════════════════
LU_x, LU_y, LU_w, LU_h = 1, 10.5, 11, 10
box(LU_x, LU_y, LU_w, LU_h, lw=1.8, ls='--')
txt(LU_x + LU_w / 2, LU_y + LU_h - 0.35,
    '核心知识与模型运行层 (Storage & Model Layer)', fs=9, w='bold')

# — Algorithm Lib
lbox(1.5, 17.5, 4.2, 2.3,
     'Algorithm Lib', '(约束模型 / 算法库)',
     'CONSTRAINT_RULE\nCONSTRAINT_LOG')

# — Exp.Data Lib
lbox(6.2, 17.0, 5.3, 2.8,
     'Exp.Data Lib', '(实验数据源数据)',
     'DATA_TEMPLATE_MAIN/DETAIL\nDATA_INDEX, DATA_RECORD_XXXX\nDEVICE_TWIN_STATES')

# — Meta Lib
lbox(1.5, 14.5, 3.5, 2.5,
     'Meta Lib', '(元数据 / 元模型库)',
     'PROPERTY_TYPE\nSCENE_MAIN/DETAIL')

# — Meta Drivers
lbox(5.5, 14.5, 3.5, 2.5,
     'Meta Drivers', '(元数据驱动引擎)',
     '元数据加载 & 赋值\n属性类型实例化')

# — Exp Process Lib
lbox(1.5, 11.0, 4.5, 2.8,
     'Exp Process Lib', '(工作流 / 流程模板库)',
     'FLOW_MODELS, FLOW_NODE\nTASK, TASK_STEP')

# — Device Lib
lbox(6.5, 11.0, 5, 2.8,
     'Device Lib', '(设备模型库)',
     'DEVICE_CATEGORY, DEVICE_MODELS\nDEVICE_INSTANCES\nDEVICE_COMPONENTS')


# ═══════════════════════════════════════════════════════
#  B. LEFT LOWER — 边缘适配与硬件驱动层
#     (Edge Adapters & Hardware Layer)
# ═══════════════════════════════════════════════════════
LL_x, LL_y, LL_w, LL_h = 1, 2.0, 11, 8
box(LL_x, LL_y, LL_w, LL_h, lw=1.8, ls='--')
txt(LL_x + LL_w / 2, LL_y + LL_h - 0.35,
    '边缘适配与硬件驱动层 (Edge Adapters & Hardware Layer)', fs=9, w='bold')

# — Adaptors
lbox(1.5, 3.0, 5, 5.5,
     'Adaptors 适配器群', '(PLC-MQTT / 直连型)',
     'ADAPTER_INDEX\nVIRTUAL_LEASE\n协议适配 & 指令翻译\n设备发现 & 心跳检测',
     tfs=9)

# — 数据采集协议 / 固件驱动
lbox(7, 3.0, 4.5, 5.5,
     '数据采集协议\n/ 固件驱动', '(自适配上报与协议桥)',
     '实时采集 Modbus / OPC-UA\n协议解析 & 格式转换\nPARSED_CONFIG 映射',
     tfs=9)


# ═══════════════════════════════════════════════════════
#  C. CENTER — 运行时引擎与服务池层
#     (Runtime Engine & Service Pools)
# ═══════════════════════════════════════════════════════
CT_x, CT_y, CT_w, CT_h = 12.5, 2.0, 11, 18.5
box(CT_x, CT_y, CT_w, CT_h, lw=1.8, ls='--')
txt(CT_x + CT_w / 2, CT_y + CT_h - 0.35,
    '运行时引擎与服务池层 (Runtime Engine & Service Pools)', fs=9, w='bold')

# — Computing Service Pool
lbox(13, 16.5, 10, 3.0,
     'Computing Service Pool', '(约束与计算服务池)',
     'ConstraintEngine 约束检测引擎\n约束表达式解析 · 滑动窗口监测\n违规预警 VIOLATION_LOG')

# — Data Service Pool
lbox(13, 10.8, 10, 5.2,
     'Data Service Pool', '(数据服务池)',
     '配方 / 标签关联 · ConstraintScope 约束范围注入\n数据分片 · 数据索引与记录管理\n全周期数据追踪 (EXECUTION_LOG)\nRESOURCE_STRUCTURE 资源拓扑')

# — Function Service Pool
lbox(13, 2.5, 10, 7.8,
     'Function Service Pool', '(功能服务与执行池)',
     '功能节点编排器 FK-FUNC\nDEV_NODE 设备控制 · FUNC_NODE 算法调用\nSUB_FLOW_NODE 子流程嵌套\n约束检查 · 任务调度 · 步骤执行\nEXECUTION_LOG 执行日志')


# ═══════════════════════════════════════════════════════
#  D. RIGHT — 系统角色与用户层
#     (System Roles & User Layer)
# ═══════════════════════════════════════════════════════
RT_x, RT_y, RT_w, RT_h = 24, 2.0, 9, 18.5
box(RT_x, RT_y, RT_w, RT_h, lw=1.8, ls='--')
txt(RT_x + RT_w / 2, RT_y + RT_h - 0.35,
    '系统角色与用户层 (System Roles & User Layer)', fs=9, w='bold')

# — Researcher
lbox(24.5, 16.0, 8, 3.5,
     'Researcher (科研人员)', '',
     'User Level 3: 实验设计与环境搭建\n实验操作 — 工作流运行\n实验数据查看与结果分析',
     tfs=10)

# — Administrator
lbox(24.5, 10.5, 8, 5.0,
     'Administrator (系统管理员)', '',
     'User Level 4/5: 系统配置与权限管理\n设备注册 & ADAPTER 管理\n元数据 & 约束规则维护\nRESOURCE_STRUCTURE 配置',
     tfs=10)

# — Users
lbox(24.5, 2.5, 8, 7.5,
     'Users (科研作者)', '',
     'User Level 1/2: 课题管理与实验设计\n任务创建 & 流程定义\n数据记录 & 报告导出\n协同实验 & 数据共享',
     tfs=10)


# ═══════════════════════════════════════════════════════
#  E. ARROWS — Inter-section connections
# ═══════════════════════════════════════════════════════

# ── Left Upper → Center (horizontal arrows) ──

# Algorithm Lib → Computing Service Pool
arrow(5.7, 18.5, 13, 18.0,
      'Export (导出约束模型)', fs=7, lo=(0, 0.25))

# Exp.Data Lib → Computing Service Pool (lower path)
arrow(11.5, 18.0, 13, 17.5,
      'Operation (流驱动入产)', fs=7, lo=(0, -0.3))

# Exp.Data Lib → Data Service Pool
arrow(11.5, 17.0, 13, 14.0,
      'Export (导入数据模型)', fs=7, lo=(0.3, 0.2))

# Device Lib → Data Service Pool
arrow(11.5, 12.4, 13, 12.4,
      'Export (设备模型)', fs=7, lo=(0, 0.22))

# Exp Process Lib → Function Service Pool
arrow(6.0, 11.0, 13, 8.0,
      'Invoke (触发约束 / 条件触发)', fs=7, lo=(1, 0.25))

# Meta Drivers → Device Lib (internal)
arrow(7.25, 14.5, 8.0, 13.85, '', lw=0.6)

# ── Center → Right (horizontal arrows) ──

# Computing → Researcher
arrow(23, 17.5, 24.5, 17.5,
      'Share (资源共享)', fs=7, lo=(0, 0.22))

# Data → Administrator
arrow(23, 13.0, 24.5, 13.0,
      'Share (配置同步)', fs=7, lo=(0, 0.22))

# Function → Users
arrow(23, 6.0, 24.5, 6.0,
      'Share (任务下发)', fs=7, lo=(0, 0.22))

# ── Bottom Left ↔ Left Upper (vertical) ──

# Perf: Adaptors → Device Lib (upward)
arrow(4.5, 8.5, 4.5, 11.0,
      'Perf (设备状态与参数)', fs=7, lo=(1.0, 0))

# ── Bottom Left ↔ Center (horizontal) ──

# Export data from Edge to Function Service Pool
arrow(11.5, 6.5, 13, 6.5,
      'Export (数据)', fs=7, lo=(0, 0.22))

# Control: Function → Adaptors (backward)
arrow(13, 4.5, 11.5, 4.5,
      'Control (指令下发)', fs=7, lo=(0, -0.25))


# ═══════════════════════════════════════════════════════
#  SAVE
# ═══════════════════════════════════════════════════════
out_dir = r'd:\SmartLab2.0\doc\generated_images'
os.makedirs(out_dir, exist_ok=True)
out_png = os.path.join(out_dir, 'SmartLab2.0_系统总体架构图_黑白线稿.png')
out_svg = os.path.join(out_dir, 'SmartLab2.0_系统总体架构图_黑白线稿.svg')

fig.savefig(out_png, dpi=200, bbox_inches='tight', facecolor='white')
fig.savefig(out_svg, bbox_inches='tight', facecolor='white')
plt.close()

print(f"PNG: {out_png}")
print(f"SVG: {out_svg}")
print("Done!")
