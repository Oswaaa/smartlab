# -*- coding: utf-8 -*-
"""
SmartLab2.0 系统分层架构图（论文版）
自上而下四层：角色层 → 服务池层 → 资源库层 → 边缘接入层
内容与 draw.io 原稿一致，走线重新整理；白底、无图内标题，适合论文排版。
输出：PNG(300dpi) / SVG / PDF
"""
import os

import matplotlib

matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch, Circle

plt.rcParams['font.sans-serif'] = ['Microsoft YaHei', 'SimHei', 'Arial', 'DejaVu Sans']
plt.rcParams['axes.unicode_minus'] = False

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'generated_images')
os.makedirs(OUT_DIR, exist_ok=True)

# ── 配色（Tailwind 系） ────────────────────────────────────────────
LAYER_FC, LAYER_EC, LAYER_TC = '#F8FAFC', '#94A3B8', '#334155'
C_ALGO = ('#FEF2F2', '#EF4444')   # Algorithm Lib
C_META = ('#FEF3C7', '#D97706')   # Meta Lib
C_HUB = ('#FFEDD5', '#EA580C')    # Meta Driven 驱动中枢
C_PROC = ('#F5F3FF', '#7C3AED')   # Exp.Process Lib
C_EDATA = ('#ECFDF5', '#10B981')  # Exp.Data Lib
C_DEVLIB = ('#EFF6FF', '#3B82F6')  # Device Lib
C_COMP = ('#F5F3FF', '#7C3AED')   # Computing Service Pool
C_FUNC = ('#EFF6FF', '#2563EB')   # Function Service Pool
C_DATA = ('#ECFDF5', '#059669')   # Data Service Pool
C_RESEARCHER = ('#E0E7FF', '#4F46E5')
C_ADMIN = ('#FEF3C7', '#D97706')
C_USERS = ('#F3E8FF', '#9333EA')
C_ADAPTER = ('#F1F5F9', '#64748B')
C_PHY = ('#F8FAFC', '#475569')

COL_OP = '#4F46E5'      # Operation
COL_MG = '#D97706'      # Manage
COL_EX = '#2563EB'      # Export
COL_SH = '#9333EA'      # Share（虚线）
COL_PU = '#10B981'      # Push
COL_CO = '#059669'      # Collect
COL_IN = '#7C3AED'      # Invoke
COL_CT = '#DC2626'      # Control


def draw_layer(ax, x, y, w, h, title):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle='round,pad=0,rounding_size=1.2',
                                fc=LAYER_FC, ec=LAYER_EC, lw=1.4, zorder=1))
    ax.plot([x + 1.5, x + w - 1.5], [y + h - 4.5, y + h - 4.5],
            color='#CBD5E1', lw=1.0, zorder=2)
    ax.text(x + 3, y + h - 2.2, title, fontsize=9.5, fontweight='bold',
            color=LAYER_TC, va='center', ha='left', zorder=4)


def draw_node(ax, x, y, w, h, title, sub='', sub2='', fc='#FFFFFF', ec='#CBD5E1',
              tc='#0F172A', sc='#475569', lw=1.5, radius=0.9, tfs=8.6, sfs=6.8):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle=f'round,pad=0,rounding_size={radius}',
                                fc=fc, ec=ec, lw=lw, zorder=3))
    lines = [(title, tfs, True, tc), (sub, sfs, False, sc), (sub2, sfs, False, sc)]
    lines = [l for l in lines if l[0]]
    n = len(lines)
    if n == 1:
        ys = [y + h / 2]
    elif n == 2:
        ys = [y + h * 0.63, y + h * 0.32]
    else:
        ys = [y + h * 0.74, y + h * 0.47, y + h * 0.22]
    for (txt, fs, bold, color), yy in zip(lines, ys):
        ax.text(x + w / 2, yy, txt, fontsize=fs, fontweight='bold' if bold else 'normal',
                color=color, ha='center', va='center', zorder=4)


def draw_role(ax, x, y, w, h, title, sub, fc, ec):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle='round,pad=0,rounding_size=1.0',
                                fc=fc, ec=ec, lw=1.6, zorder=3))
    ic = ec
    cy = y + h * 0.60
    ax.add_patch(Circle((x + 5.2, cy + 1.1), 1.35, fc=ic, ec='none', zorder=4))
    ax.add_patch(FancyBboxPatch((x + 3.0, cy - 3.1), 4.4, 2.6,
                                boxstyle='round,pad=0,rounding_size=1.2',
                                fc=ic, ec='none', zorder=4))
    ax.text(x + 10.5, y + h * 0.63, title, fontsize=8.6, fontweight='bold',
            color='#0F172A', ha='left', va='center', zorder=4)
    ax.text(x + 10.5, y + h * 0.30, sub, fontsize=6.9,
            color='#475569', ha='left', va='center', zorder=4)


def orth_arrow(ax, pts, label='', color='#475569', lw=1.4, dashed=False,
               label_at=None, label_fs=6.6, rot=0, zorder=5):
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    ax.plot(xs, ys, color=color, lw=lw, ls='--' if dashed else '-',
            zorder=zorder, solid_capstyle='round')
    ax.add_patch(FancyArrowPatch(pts[-2], pts[-1], arrowstyle='-|>',
                                 mutation_scale=10, lw=lw, color=color,
                                 shrinkA=0, shrinkB=0, zorder=zorder + 1))
    if label:
        if label_at is None:
            label_at = ((pts[0][0] + pts[-1][0]) / 2, (pts[0][1] + pts[-1][1]) / 2)
        ax.text(label_at[0], label_at[1], label, fontsize=label_fs, color=color,
                ha='center', va='center', rotation=rot, zorder=zorder + 2,
                bbox=dict(boxstyle='round,pad=0.18', fc='#FFFFFF', ec=color, lw=0.7, alpha=0.96))


def curve_arrow(ax, p1, p2, rad=0.2, label='', color='#475569', lw=1.4, dashed=False,
                label_t=0.5, label_fs=6.6, label_dxy=(0, 0), zorder=5, head=True):
    ax.add_patch(FancyArrowPatch(p1, p2, connectionstyle=f'arc3,rad={rad}',
                                 arrowstyle='-|>' if head else '-',
                                 mutation_scale=10, lw=lw, color=color,
                                 linestyle='--' if dashed else '-', shrinkA=0, shrinkB=0,
                                 zorder=zorder))
    if label:
        (x1, y1), (x2, y2) = p1, p2
        mx, my = (x1 + x2) / 2, (y1 + y2) / 2
        cx, cy = mx + rad * (y2 - y1), my - rad * (x2 - x1)
        t = label_t
        bx = (1 - t) ** 2 * x1 + 2 * t * (1 - t) * cx + t ** 2 * x2
        by = (1 - t) ** 2 * y1 + 2 * t * (1 - t) * cy + t ** 2 * y2
        ax.text(bx + label_dxy[0], by + label_dxy[1], label, fontsize=label_fs, color=color,
                ha='center', va='center', zorder=zorder + 2,
                bbox=dict(boxstyle='round,pad=0.18', fc='#FFFFFF', ec=color, lw=0.7, alpha=0.96))


def legend_note(ax, x, y, w, h):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle='round,pad=0,rounding_size=0.8',
                                fc='#FFFFFF', ec='#CBD5E1', lw=0.9, linestyle='--', zorder=3))
    ax.text(x + 2.2, y + h - 3.2, '图  例', fontsize=6.6, fontweight='bold',
            color='#334155', ha='left', va='center', zorder=4)
    ax.plot([x + 2.2, x + 7.0], [y + h - 7.4, y + h - 7.4], color='#475569', lw=1.4, zorder=4)
    ax.add_patch(FancyArrowPatch((x + 7.0, y + h - 7.4), (x + 8.6, y + h - 7.4),
                                 arrowstyle='-|>', mutation_scale=8, lw=1.4,
                                 color='#475569', shrinkA=0, shrinkB=0, zorder=4))
    ax.text(x + 10, y + h - 7.4, '数据 / 控制流', fontsize=6.2, color='#475569',
            ha='left', va='center', zorder=4)
    ax.plot([x + 2.2, x + 8.6], [y + h - 11.4, y + h - 11.4], color=COL_SH, lw=1.4,
            ls='--', zorder=4)
    ax.add_patch(FancyArrowPatch((x + 8.6, y + h - 11.4), (x + 10.2, y + h - 11.4),
                                 arrowstyle='-|>', mutation_scale=8, lw=1.4,
                                 color=COL_SH, shrinkA=0, shrinkB=0, zorder=4))
    ax.text(x + 11.6, y + h - 11.4, '共享分发', fontsize=6.2, color=COL_SH,
            ha='left', va='center', zorder=4)


def build_diagram():
    fig, ax = plt.subplots(figsize=(18, 13), dpi=300)
    fig.patch.set_facecolor('#FFFFFF')
    ax.set_facecolor('#FFFFFF')
    ax.set_xlim(0, 180)
    ax.set_ylim(0, 130)
    ax.axis('off')

    # ── 层容器 ──────────────────────────────────────────────────
    draw_layer(ax, 6, 112, 168, 17, '角色层  Role Layer')
    draw_layer(ax, 6, 78, 168, 30, '服务池层  Service Pool Layer')
    draw_layer(ax, 6, 22, 148, 50, '资源库层  Resource Library Layer')
    draw_layer(ax, 6, 2, 168, 20, '边缘接入层  Edge Access Layer')

    # ── 角色层 ──────────────────────────────────────────────────
    draw_role(ax, 14, 114, 38, 10, 'Researcher', '科研人员', *C_RESEARCHER)
    draw_role(ax, 64, 114, 38, 10, 'Administrator', '系统管理员', *C_ADMIN)
    draw_role(ax, 122, 114, 38, 10, 'Users', '科研协作组', *C_USERS)

    # ── 服务池层 ────────────────────────────────────────────────
    draw_node(ax, 16, 82, 42, 19, 'Computing Service Pool',
              '约束软实时监控 · 200ms', '数据预测 · 违规大屏', *C_COMP)
    draw_node(ax, 64, 82, 42, 19, 'Function Service Pool',
              '状态机驱动 · 指令路由', '细粒度能力解析', *C_FUNC)
    draw_node(ax, 118, 82, 42, 19, 'Data Service Pool',
              '统一可观测空间', '快照抽取 · 实时分发', *C_DATA)

    # ── 资源库层 ────────────────────────────────────────────────
    draw_node(ax, 10, 51, 32, 14, 'Algorithm Lib', '约束规则 / 算法',
              fc=C_ALGO[0], ec=C_ALGO[1])
    draw_node(ax, 46, 48, 28, 18, 'Meta Driven', '驱动中枢',
              fc=C_HUB[0], ec=C_HUB[1], lw=2.0, tfs=9.0)
    draw_node(ax, 80, 51, 32, 14, 'Exp.Process Lib', '工作流切片',
              fc=C_PROC[0], ec=C_PROC[1])
    draw_node(ax, 116, 51, 34, 14, 'Device Lib', '数字孪生与模型',
              fc=C_DEVLIB[0], ec=C_DEVLIB[1])
    draw_node(ax, 10, 30, 32, 14, 'Meta Lib', '元数据 / 空间拓扑',
              fc=C_META[0], ec=C_META[1])
    draw_node(ax, 80, 30, 32, 14, 'Exp.Data Lib', '时序数据集',
              fc=C_EDATA[0], ec=C_EDATA[1])
    legend_note(ax, 116, 29, 34, 14)

    # ── 边缘接入层 ──────────────────────────────────────────────
    draw_node(ax, 12, 4, 46, 11, 'Adapters 适配器群', 'PLC-MQTT / 虚拟租约',
              fc=C_ADAPTER[0], ec=C_ADAPTER[1])
    draw_node(ax, 90, 4, 46, 11, '物理实验仪器 / 虚拟设备', '反应釜 / 机械臂 / 传感器',
              fc=C_PHY[0], ec=C_PHY[1])

    # ── 资源库层内部连线 ────────────────────────────────────────
    orth_arrow(ax, [(42, 58), (46, 58)], color=COL_MG, lw=1.3)                     # Algorithm→MD
    orth_arrow(ax, [(42, 37), (44, 37), (44, 52), (46, 52)], color=COL_MG, lw=1.3)  # MetaLib→MD
    orth_arrow(ax, [(74, 57), (80, 57)], color=COL_MG, lw=1.3)                     # MD→Exp.Process
    orth_arrow(ax, [(60, 48), (60, 34), (80, 34)], color=COL_MG, lw=1.3)           # MD→Exp.Data
    orth_arrow(ax, [(68, 48), (68, 45.6), (140, 45.6), (140, 51)], color=COL_MG, lw=1.3)  # MD→Device

    # ── 资源库层 → 服务池层 ─────────────────────────────────────
    orth_arrow(ax, [(36, 65), (36, 83)], 'Operation', color=COL_OP,
               label_at=(36, 79))                                                  # Algorithm→Computing
    orth_arrow(ax, [(96, 44), (96, 47.4), (77.5, 47.4), (77.5, 75.4), (54, 75.4), (54, 82)],
               'Export', color=COL_EX, label_at=(64, 75.4))                         # Exp.Data→Computing
    orth_arrow(ax, [(88, 65), (88, 83)], 'Export', color=COL_EX, label_at=(88, 71))  # Exp.Process→Function
    orth_arrow(ax, [(127, 65), (127, 83)], 'Export', color=COL_EX, label_at=(127, 71))  # Device→Data
    orth_arrow(ax, [(100, 30), (100, 26), (152, 26), (152, 80), (140, 80), (140, 83)],
               'Export', color=COL_EX, label_at=(124, 26))                          # Exp.Data→Data

    # ── 角色层连线 ──────────────────────────────────────────────
    orth_arrow(ax, [(24, 114), (24, 108)], 'Operation', color=COL_OP, label_at=(24, 112))
    orth_arrow(ax, [(83, 114), (83, 108)], 'Manage', color=COL_MG, label_at=(83, 112))
    orth_arrow(ax, [(14, 119), (3, 119), (3, 47), (6, 47)], 'Operation', color=COL_OP,
               label_at=(3, 90), label_fs=6.3)
    orth_arrow(ax, [(70, 114), (70, 110.3), (11, 110.3), (11, 72)], 'Manage', color=COL_MG,
               label_at=(40, 110.3), label_fs=6.3)

    # ── 服务池层 ⇄ 边缘接入层（右侧走廊） ───────────────────────
    orth_arrow(ax, [(136, 13), (162, 13), (162, 91), (160, 91)], 'Push (高频遥测直推)',
               color=COL_PU, label_at=(163.6, 45), rot=90, label_fs=6.2)
    orth_arrow(ax, [(136, 7), (167, 7), (167, 74), (98, 74), (98, 83)], 'Collect (状态采集)',
               color=COL_CO, label_at=(168.6, 42), rot=90, label_fs=6.2)
    orth_arrow(ax, [(70, 83), (70, 76.5), (172, 76.5), (172, 10), (136, 10)],
               'Invoke (细粒度子服务)', color=COL_IN, label_at=(173.6, 45), rot=90, label_fs=6.2)
    orth_arrow(ax, [(66, 83), (66, 73.2), (1.5, 73.2), (1.5, 8), (12, 8)],
               'Control (指令下发)', color=COL_CT, label_at=(22, 75.4), label_fs=6.4)

    # ── 服务池层 → Users（Share 虚线） ──────────────────────────
    orth_arrow(ax, [(50, 101), (126, 114)], 'Share', color=COL_SH, dashed=True,
               label_at=(91.8, 108.2), lw=1.3)
    orth_arrow(ax, [(95, 101), (133, 114)], 'Share', color=COL_SH, dashed=True,
               label_at=(115.9, 108.2), lw=1.3)
    orth_arrow(ax, [(139, 101), (146, 114)], 'Share', color=COL_SH, dashed=True,
               label_at=(141.8, 106.2), lw=1.3)

    # ── 适配器 ⇄ 物理仪器 ───────────────────────────────────────
    ax.plot([58, 88], [9.5, 9.5], color='#475569', lw=1.4, zorder=5)
    ax.add_patch(FancyArrowPatch((58, 9.5), (61.5, 9.5), arrowstyle='-|>', mutation_scale=9,
                                 lw=1.4, color='#475569', shrinkA=0, shrinkB=0, zorder=6))
    ax.add_patch(FancyArrowPatch((88, 9.5), (84.5, 9.5), arrowstyle='-|>', mutation_scale=9,
                                 lw=1.4, color='#475569', shrinkA=0, shrinkB=0, zorder=6))

    # ── 输出 ────────────────────────────────────────────────────
    stem = os.path.join(OUT_DIR, '06_SmartLab2.0_系统分层架构图_论文版')
    fig.tight_layout()
    fig.savefig(stem + '.png', dpi=300, facecolor=fig.get_facecolor(), edgecolor='none')
    fig.savefig(stem + '.svg', facecolor=fig.get_facecolor(), edgecolor='none')
    fig.savefig(stem + '.pdf', facecolor=fig.get_facecolor(), edgecolor='none')
    plt.close(fig)
    print('saved:', stem + '.{png,svg,pdf}')


if __name__ == '__main__':
    build_diagram()
