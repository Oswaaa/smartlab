#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
SmartLab 2.0 - 系统架构图 (完整版V2 - 仿参考图风格)
三大研究板块：SysML建模 / 事件总线与协调 / 数据管理
"""

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import (FancyBboxPatch, FancyArrowPatch,
                                 Rectangle, Ellipse, Arc)
from matplotlib.font_manager import FontProperties
import os, sys

sys.stdout.reconfigure(encoding='utf-8')

# ─────────────────────────── Config ───────────────────────────
FIG_W, FIG_H = 30, 17
C_EDGE  = '#333333'
C_DASH  = '#777777'
C_TEXT  = '#222222'
C_CYL   = '#DCDCDC'
C_BG    = '#FFFFFF'
C_FILL  = '#F0F0F0'
C_LABEL = '#8B6914'

def FONT(sz=9, w='normal'):
    return FontProperties(family='Microsoft YaHei', size=sz, weight=w)

fig, ax = plt.subplots(figsize=(FIG_W, FIG_H))
ax.set_xlim(0, FIG_W)
ax.set_ylim(0, FIG_H)
ax.set_aspect('equal')
ax.axis('off')
fig.patch.set_facecolor(C_BG)

# ─────────────────────────── Helpers ───────────────────────────

def rbox(x, y, w, h, label, fs=9, fc='white', ec=C_EDGE, lw=1.2, zorder=3):
    b = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.08",
                        fc=fc, ec=ec, lw=lw, zorder=zorder)
    ax.add_patch(b)
    ax.text(x+w/2, y+h/2, label, fontproperties=FONT(fs), color=C_TEXT,
            ha='center', va='center', zorder=zorder+1)

def sbox(x, y, w, h, label='', fs=9, fc='white', ec=C_EDGE, lw=1, ls='-', zorder=2):
    r = Rectangle((x, y), w, h, fc=fc, ec=ec, lw=lw, ls=ls, zorder=zorder)
    ax.add_patch(r)
    if label:
        ax.text(x+w/2, y+h/2, label, fontproperties=FONT(fs), color=C_TEXT,
                ha='center', va='center', zorder=zorder+1)

def cyl(cx, cy, w, h, label, fs=9, fc=C_CYL, ec=C_EDGE, lw=1.2, zorder=4):
    eh = h * 0.30
    body_bot = cy - h/2 + eh/2
    body_top = cy + h/2 - eh/2
    body_h = body_top - body_bot
    body = Rectangle((cx-w/2, body_bot), w, body_h, fc=fc, ec='none', zorder=zorder-0.5)
    ax.add_patch(body)
    ax.plot([cx-w/2, cx-w/2], [body_bot, body_top], color=ec, lw=lw, zorder=zorder)
    ax.plot([cx+w/2, cx+w/2], [body_bot, body_top], color=ec, lw=lw, zorder=zorder)
    te = Ellipse((cx, body_top), w, eh, fc=fc, ec=ec, lw=lw, zorder=zorder+1)
    ax.add_patch(te)
    ba = Arc((cx, body_bot), w, eh, angle=0, theta1=180, theta2=360,
             ec=ec, lw=lw, zorder=zorder+1)
    ax.add_patch(ba)
    ax.text(cx, cy - 0.05, label, fontproperties=FONT(fs), color=C_TEXT,
            ha='center', va='center', zorder=zorder+2)

def arr(x1, y1, x2, y2, label='', fs=8, lw=1.2, style='->',
        conn='arc3,rad=0', lo=(0, 0.2), zorder=5, color=C_EDGE):
    a = FancyArrowPatch((x1, y1), (x2, y2), arrowstyle=style,
                         connectionstyle=conn, color=color, lw=lw,
                         zorder=zorder, mutation_scale=12)
    ax.add_patch(a)
    if label:
        mx = (x1+x2)/2 + lo[0]
        my = (y1+y2)/2 + lo[1]
        ax.text(mx, my, label, fontproperties=FONT(fs), color=C_TEXT,
                ha='center', va='center', zorder=zorder+1)

def txt(x, y, s, fs=9, ha='center', va='center', color=C_TEXT, w='normal', zorder=6, rotation=0):
    ax.text(x, y, s, fontproperties=FONT(fs, w), color=color,
            ha=ha, va=va, zorder=zorder, rotation=rotation)

def dash_group(x, y, w, h, label='', fs=10, lpos='top-left', fc='none', lw=1.2):
    r = Rectangle((x, y), w, h, fc=fc, ec=C_DASH, lw=lw, ls='--', zorder=1)
    ax.add_patch(r)
    if label and lpos == 'top-left':
        txt(x+0.2, y+h-0.3, label, fs=fs, ha='left', va='top', zorder=2)


# ═══════════════════════════════════════════════════════════════
#  TITLE
# ═══════════════════════════════════════════════════════════════
txt(15, 16.5, '基于SysML流程建模的约束执行与运行时预警机制研究',
    fs=14, w='bold', color='#1a1a1a')

# ═══════════════════════════════════════════════════════════════
#  LEFT — SysML MODELING  (x 0.5–7)
# ═══════════════════════════════════════════════════════════════

# 实验流程图
rbox(2.0, 14.0, 2.5, 0.7, '实验流程图', fs=10)

# SysML 流程模型
sy_x, sy_y, sy_w, sy_h = 0.5, 10.2, 6.0, 2.8
sbox(sy_x, sy_y, sy_w, sy_h, fc='#FAFAFA', lw=1.5)
lbl_w = 1.0
sbox(sy_x, sy_y, lbl_w, sy_h, fc='#EEEEEE', lw=1)
txt(sy_x + lbl_w/2, sy_y + sy_h/2, 'SysML\n流程\n模型', fs=8)
cells = ['需求', '结构', '接口', '行为', '约束']
cell_w = (sy_w - lbl_w) / 5
for i, c in enumerate(cells):
    sbox(sy_x + lbl_w + i*cell_w, sy_y, cell_w, sy_h, c, fs=9, lw=0.8)

# 实验流程图 → SysML
arr(3.25, 14.0, 3.25, sy_y + sy_h + 0.08, lw=1)

# 流程建模文本语法 & NL2SysML
rbox(0.3, 8.0, 2.6, 0.7, '流程建模\n文本语法', fs=8)
rbox(3.8, 8.0, 2.3, 0.7, 'NL2SysML模型', fs=9)
arr(2.9, 8.35, 3.8, 8.35, '依赖', fs=7, lo=(0, 0.22))

# 专业语言需求描述
rbox(1.0, 6.2, 2.5, 0.7, '专业语言\n需求描述', fs=8)
arr(3.5, 6.55, 5.0, 8.0, '输入', fs=7, lo=(0.3, 0.15))

# SysML → 协调层
arr(sy_x + sy_w, sy_y + sy_h/2, 8.2, 11.6, lw=1.5)

# Bottom label
txt(3.2, 5.0, '基于NL2SysML的流程模型生成方法', fs=9, color=C_LABEL)


# ═══════════════════════════════════════════════════════════════
#  CENTER — 协调层  (x 8–17.5)
# ═══════════════════════════════════════════════════════════════

# 请求服务 dashed line
ax.plot([8.5, 16], [13.2, 13.2], color=C_DASH, lw=1, ls='--', zorder=1)
txt(12, 13.5, '请求服务', fs=9)

co_x, co_y, co_w, co_h = 8.0, 8.8, 9.5, 4.0
dash_group(co_x, co_y, co_w, co_h, '协调层', fs=11)

# 执行引擎 / 监测引擎
rbox(8.5, 11.4, 2.3, 0.8, '执行引擎', fs=10, fc=C_FILL)
rbox(8.5, 10.0, 2.3, 0.8, '监测引擎', fs=10, fc=C_FILL)

# 事件总线 区域
eb_x, eb_y = 11.3, 9.2
sbox(eb_x, eb_y, 6.0, 3.2, fc='none', ec=C_DASH, lw=0.8, ls='--')
txt(13.5, 12.0, '事件总线', fs=10, w='bold')

txt(12.5, 11.2, '发布事件\n订阅回执', fs=8)

# Circle A
circle = plt.Circle((14.0, 10.3), 0.33, fc='white', ec=C_EDGE, lw=1.3, zorder=4)
ax.add_patch(circle)
txt(14.0, 10.3, 'A', fs=11, zorder=5)
txt(14.0, 9.6, '动作事件A', fs=7)

# BROKER
rbox(15.3, 9.9, 1.8, 0.85, 'BROKER', fs=10, fc=C_FILL)

# Internal arrows
arr(10.8, 11.8, 11.8, 11.4, lw=0.8)
arr(10.8, 10.4, 11.8, 10.4, lw=0.8)
arr(14.33, 10.3, 15.3, 10.3, lw=0.8)


# ═══════════════════════════════════════════════════════════════
#  SERVICE / DEVICE DBs
# ═══════════════════════════════════════════════════════════════
cyl(10.0, 14.6, 1.8, 1.2, '服务库', fs=9)
cyl(13.5, 14.6, 1.8, 1.2, '设备库', fs=9)

arr(10.0, 13.2, 10.0, 13.95, lw=1)
arr(13.5, 13.2, 13.5, 13.95, lw=1)
txt(9.3, 14.6, '返回', fs=8, ha='right')
txt(14.6, 14.6, '请求', fs=8, ha='left')


# ═══════════════════════════════════════════════════════════════
#  ADAPTERS  (x 8–18, y 2.5–7.8)
# ═══════════════════════════════════════════════════════════════
ad_x, ad_y, ad_w, ad_h = 8.0, 3.0, 10.5, 5.2
dash_group(ad_x, ad_y, ad_w, ad_h)
txt(ad_x + 0.3, ad_y + ad_h - 0.3, 'Adapters', fs=10, ha='left')

arr(12, co_y, 12, ad_y + ad_h + 0.08, lw=1.2)

a_labels = ['Adapter 1', 'Adapter 2', 'Adapter 3', '...', 'Adapter N']
a_x0, a_gap = 8.5, 2.0
for i, al in enumerate(a_labels):
    bx = a_x0 + i * a_gap
    if al == '...':
        txt(bx + 0.8, 6.5, '· · ·', fs=14)
    else:
        rbox(bx, 6.1, 1.7, 0.8, al, fs=8, fc=C_FILL)

d_data = [('设备1', '设备类别1'), ('设备2', '设备类别2'),
          ('设备3', '设备类别3'), None, ('设备N', '设备类别N')]
for i, dd in enumerate(d_data):
    if dd is None: continue
    bx = a_x0 + i * a_gap
    sbox(bx + 0.1, 4.0, 1.5, 0.7, dd[0], fs=8, fc='#FAFAFA')
    txt(bx + 0.85, 3.4, dd[1], fs=7)
    arr(bx + 0.85, 6.1, bx + 0.85, 4.75, lw=0.8)

# 数据 / 回执
arr(ad_x + ad_w, 5.6, 20.0, 5.6, '数据', fs=8, lo=(0, 0.25))
arr(20.0, 4.8, ad_x + ad_w, 4.8, '回执', fs=8, lo=(0, -0.25))

txt(13.5, 2.0, '基于事件总线与溯源的模块化解耦架构', fs=9, color=C_LABEL)


# ═══════════════════════════════════════════════════════════════
#  RIGHT — DATA MANAGEMENT  (x 17.5–29)
# ═══════════════════════════════════════════════════════════════

# BROKER → 结构化 → 记录
arr(17.1, 10.3, 18.5, 10.3, '结构化', fs=8, lo=(0, 0.25))
rbox(18.8, 9.9, 1.4, 0.8, '记录', fs=9)

# 记录 → 上传 (vertical line up to 数据库 level)
arr(20.2, 10.3, 20.8, 10.3, lw=1)
# Vertical line going up
ax.plot([20.8, 20.8], [10.3, 12.2], color=C_EDGE, lw=1.2, zorder=3)
txt(21.15, 11.2, '上传', fs=8, rotation=90)
# Horizontal to 数据库
arr(20.8, 12.2, 21.8, 12.2, lw=1.2)

# 数据库
cyl(23.0, 12.2, 2.0, 1.5, '数据库', fs=10)

# 事件库
sbox(22.2, 9.8, 1.5, 0.7, '事件库', fs=9)
arr(23.0, 11.4, 22.95, 10.55, lw=0.8)

# 模板库 (top right)
cyl(28.0, 15.2, 1.5, 1.0, '模板库', fs=9)

# 元数据 grid
meta_x, meta_y = 25.0, 14.0
sbox(meta_x, meta_y, 4.5, 1.8, ec=C_EDGE, lw=1.2)
txt(meta_x + 0.7, meta_y + meta_h - 0.35 if 'meta_h' in dir() else meta_y + 1.45,
    '元数据', fs=9, w='bold', ha='left')

mc_labels = ['结构', '类型', '映射']
for i, mc in enumerate(mc_labels):
    sbox(meta_x + 0.3 + i*1.3, meta_y + 0.25, 1.1, 0.8, mc, fs=8, fc='#FAFAFA')

# 数据库 → 元数据
arr(23.8, 13.0, 26.0, 14.0, lw=0.8)

# 提取 (from 数据库 right side, going down)
txt(24.5, 11.2, '提取', fs=8)
arr(24.0, 12.0, 24.5, 10.9, lw=0.8)

# 切片 / 时间戳
rbox(23.5, 9.3, 1.2, 0.6, '切片', fs=8)
rbox(25.0, 9.3, 1.5, 0.6, '时间戳', fs=8)
arr(24.5, 10.7, 24.5, 9.95, lw=0.8)

# 特征 / 标识 / 截面
rbox(23.0, 7.8, 1.2, 0.6, '特征', fs=8)
rbox(24.5, 7.8, 1.2, 0.6, '标识', fs=8)
rbox(26.0, 7.8, 1.2, 0.6, '截面', fs=8)
arr(24.5, 9.3, 24.5, 8.45, lw=0.8)

# 联合检索 / 测源数据
rbox(24.0, 6.2, 2.0, 0.7, '联合检索', fs=9)
rbox(27.0, 6.2, 1.8, 0.7, '测源\n数据', fs=8)
arr(25.0, 7.8, 25.0, 6.95, lw=0.8)
arr(26.0, 6.55, 27.0, 6.55, lw=0.8)

# Bottom right label
txt(26.0, 2.0, '面向过程的化学实验全周期数据\n报管理与可追溯分析方法研究',
    fs=9, color=C_LABEL)


# ═══════════════════════════════════════════════════════════════
#  SAVE
# ═══════════════════════════════════════════════════════════════
out_dir = r'd:\SmartLab2.0\doc\generated_images'
os.makedirs(out_dir, exist_ok=True)
out_png = os.path.join(out_dir, 'SmartLab2.0_系统架构图_完整版.png')
out_svg = os.path.join(out_dir, 'SmartLab2.0_系统架构图_完整版.svg')

fig.savefig(out_png, dpi=200, bbox_inches='tight', facecolor=C_BG)
fig.savefig(out_svg, bbox_inches='tight', facecolor=C_BG)
plt.close()
print(f"PNG: {out_png}")
print(f"SVG: {out_svg}")
print("Done!")
