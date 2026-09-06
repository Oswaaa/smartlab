# -*- coding: utf-8 -*-
import os
import matplotlib.pyplot as plt
import matplotlib.patches as patches
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch, Circle

plt.rcParams['font.sans-serif'] = ['Microsoft YaHei', 'SimHei', 'Arial', 'DejaVu Sans']
plt.rcParams['axes.unicode_minus'] = False

out_dir = r'd:\SmartLab2.0\doc\generated_images'
os.makedirs(out_dir, exist_ok=True)

def draw_round_box(ax, x, y, w, h, title, subtitle='', subdesc='',
                   facecolor='#FFFFFF', edgecolor='#CBD5E1', title_color='#0F172A',
                   sub_color='#2563EB', desc_color='#64748B', lw=1.5, radius=1.0, zorder=3):
    box = FancyBboxPatch((x, y), w, h, boxstyle=f'round,pad=0.2,rounding_size={radius}',
                         facecolor=facecolor, edgecolor=edgecolor, linewidth=lw, zorder=zorder)
    ax.add_patch(box)
    
    if subtitle and subdesc:
        ax.text(x + w/2, y + h*0.74, title, ha='center', va='center', fontsize=9.2, fontweight='bold', color=title_color, zorder=zorder+1)
        ax.text(x + w/2, y + h*0.48, subtitle, ha='center', va='center', fontsize=7.6, fontweight='bold', color=sub_color, zorder=zorder+1)
        ax.text(x + w/2, y + h*0.22, subdesc, ha='center', va='center', fontsize=6.8, color=desc_color, zorder=zorder+1)
    elif subtitle:
        ax.text(x + w/2, y + h*0.64, title, ha='center', va='center', fontsize=9.2, fontweight='bold', color=title_color, zorder=zorder+1)
        ax.text(x + w/2, y + h*0.34, subtitle, ha='center', va='center', fontsize=7.2, color=sub_color, zorder=zorder+1)
    else:
        ax.text(x + w/2, y + h*0.50, title, ha='center', va='center', fontsize=9.2, fontweight='bold', color=title_color, zorder=zorder+1)

def draw_container(ax, x, y, w, h, header_text, facecolor='#F8FAFC', edgecolor='#94A3B8', header_color='#334155'):
    box = FancyBboxPatch((x, y), w, h, boxstyle='round,pad=0.3,rounding_size=1.8',
                         facecolor=facecolor, edgecolor=edgecolor, linewidth=1.5, zorder=1)
    ax.add_patch(box)
    ax.text(x + 3.0, y + h - 3.2, header_text, ha='left', va='center', fontsize=10.0, fontweight='bold', color=header_color, zorder=2)
    ax.plot([x, x + w], [y + h - 5.5, y + h - 5.5], color=edgecolor, lw=1.0, linestyle='-', zorder=2)

def draw_polyline_arrow(ax, points, label='', color='#475569', label_color='#1E293B', lw=1.3,
                        label_pos=None, label_offset=(0,0), arrowstyle='-|>', linestyle='-', zorder=5):
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    ax.plot(xs, ys, color=color, lw=lw, linestyle=linestyle, zorder=zorder)
    
    p_last = points[-1]
    p_prev = points[-2]
    arrow = FancyArrowPatch(p_prev, p_last, arrowstyle=arrowstyle, mutation_scale=11, lw=lw, color=color, zorder=zorder+1)
    ax.add_patch(arrow)
    
    if label:
        if label_pos is not None:
            lx, ly = label_pos
        else:
            mid_idx = len(points) // 2
            lx = (points[mid_idx-1][0] + points[mid_idx][0]) / 2 + label_offset[0]
            ly = (points[mid_idx-1][1] + points[mid_idx][1]) / 2 + label_offset[1]
        
        ax.text(lx, ly, label, ha='center', va='center', fontsize=7.2, fontweight='bold', color=label_color,
                bbox=dict(boxstyle='round,pad=0.2,rounding_size=0.3', fc='#FFFFFF', ec=color, lw=0.8, alpha=0.95), zorder=zorder+2)

def build_diagram():
    fig, ax = plt.subplots(figsize=(22, 12.2), dpi=300)
    fig.patch.set_facecolor('#F1F5F9')
    ax.set_facecolor('#F1F5F9')
    ax.set_xlim(0, 220)
    ax.set_ylim(0, 122)
    ax.axis('off')

    canvas = FancyBboxPatch((1.5, 1.5), 217, 119, boxstyle='round,pad=0.5,rounding_size=2.0',
                            facecolor='#FFFFFF', edgecolor='#CBD5E1', linewidth=1.5, zorder=0)
    ax.add_patch(canvas)

    # Diagram Header
    ax.text(110, 118.0, 'SmartLab 2.0 智能实验室系统总体架构图 (基于可执行建模与运行时全链路闭环)',
            ha='center', va='center', fontsize=15.5, fontweight='bold', color='#0F172A', zorder=4)
    ax.text(110, 114.5, '严格对齐拓扑交互结构 · 贯通知识与存储、边缘适配与装备、运行时服务池、系统角色四大层级',
            ha='center', va='center', fontsize=9.0, color='#64748B', zorder=4)

    # =========================================================================
    # 1. LAYER CONTAINERS
    # =========================================================================
    # Container 1: 核心知识与模型存储层
    draw_container(ax, 7, 36, 88, 55, '核心知识与模型存储层 (Storage & Model Layer)',
                   facecolor='#F8FAFC', edgecolor='#3B82F6', header_color='#1D4ED8')

    # Container 2: 边缘适配与硬件装备层
    draw_container(ax, 7, 6, 88, 26, '边缘适配与硬件装备层 (Edge Adapters & Hardware Layer)',
                   facecolor='#F8FAFC', edgecolor='#059669', header_color='#047857')

    # Container 3: 运行时引擎与服务池层
    draw_container(ax, 100, 10, 52, 81, '运行时引擎与服务池层 (Runtime Engine & Service Pools)',
                   facecolor='#F8FAFC', edgecolor='#7C3AED', header_color='#6D28D9')

    # Container 4: 系统角色与用户层
    draw_container(ax, 168, 10, 46, 81, '系统角色与用户层 (System Roles & User Layer)',
                   facecolor='#F8FAFC', edgecolor='#EA580C', header_color='#C2410C')

    # =========================================================================
    # 2. NODES INSIDE CONTAINERS
    # =========================================================================
    
    # --- Storage Layer ---
    draw_round_box(ax, 10, 71.5, 25, 13.5,
                   title='Algorithm Lib', subtitle='(约束规则 / 算法库)', subdesc='CONSTRAINT_RULE · VIOLATION_LOG',
                   facecolor='#EFF6FF', edgecolor='#3B82F6', title_color='#1E40AF', sub_color='#2563EB')

    draw_round_box(ax, 10, 54.5, 25, 13.5,
                   title='Meta Lib', subtitle='(元数据 / 空间拓扑)', subdesc='PROPERTY_TYPE · SCENE · 拓扑表',
                   facecolor='#EFF6FF', edgecolor='#3B82F6', title_color='#1E40AF', sub_color='#2563EB')

    draw_round_box(ax, 10, 38.0, 25, 13.5,
                   title='Exp.Process Lib', subtitle='(工作流与任务切片)', subdesc='FLOW_MODELS · FLOW_NODE · TASK',
                   facecolor='#EFF6FF', edgecolor='#3B82F6', title_color='#1E40AF', sub_color='#2563EB')

    draw_round_box(ax, 38, 53, 20, 16.5,
                   title='Meta Driven', subtitle='(元数据驱动中枢)', subdesc='动态建表 · 拓扑驱动\n资源检索与匹配',
                   facecolor='#FEF3C7', edgecolor='#D97706', title_color='#92400E', sub_color='#B45309')

    draw_round_box(ax, 62, 65, 30, 16,
                   title='Exp.Data Lib', subtitle='(时序实验数据库)', subdesc='DATA_TEMPLATE (模板) · DATA_INDEX (索引)\nDATA_RECORD_XXXX (动态时序表)',
                   facecolor='#ECFDF5', edgecolor='#10B981', title_color='#065F46', sub_color='#047857')

    draw_round_box(ax, 62, 42, 30, 16,
                   title='Device Lib', subtitle='(数字孪生与模型库)', subdesc='DEVICE_MODELS (模型/状态机/BOM)\nDEVICE_INSTANCES · TWIN_STATES (孪生)',
                   facecolor='#EFF6FF', edgecolor='#2563EB', title_color='#1E40AF', sub_color='#1D4ED8')

    # --- Edge & Hardware ---
    draw_round_box(ax, 10, 9, 36, 17,
                   title='Adapters 适配器群', subtitle='(PLC-MQTT / 虚拟租约)', subdesc='ADAPTER_INDEX (契约) · VIRTUAL_LEASE (租约)\n四层架构 (runtime/north/core/south)',
                   facecolor='#F0FDF4', edgecolor='#059669', title_color='#065F46', sub_color='#047857')

    draw_round_box(ax, 49, 9, 43, 17,
                   title='物理实验仪器 / 虚拟设备', subtitle='(自动化合成与物联装备)', subdesc='化学反应釜 (Reactor) · 移液机械臂 (Robot)\n高精度温度/pH/光谱传感器 (Sensors)',
                   facecolor='#F8FAFC', edgecolor='#64748B', title_color='#1E293B', sub_color='#475569')

    # --- Service Pools ---
    draw_round_box(ax, 103, 64, 46, 20,
                   title='Computing Service Pool', subtitle='(计算与约束服务池)', subdesc='软实时约束监控 (200ms周期) · 实验趋势预测\n条件聚类挖掘 · 流程与违规大屏可视化',
                   facecolor='#F5F3FF', edgecolor='#7C3AED', title_color='#5B21B6', sub_color='#6D28D9')

    draw_round_box(ax, 103, 38.5, 46, 21,
                   title='Data Service Pool', subtitle='(数据服务池)', subdesc='统一可观测空间 (Observation Space 内存只读快照)\n高频遥测动态入库 · 步骤/变量快照抽取 · SSE/WS 实时分发',
                   facecolor='#ECFDF5', edgecolor='#10B981', title_color='#065F46', sub_color='#047857')

    draw_round_box(ax, 103, 14, 46, 20,
                   title='Function Service Pool', subtitle='(功能与控制服务池)', subdesc='数字孪生双状态机驱动 (OP/CMD) · 细粒度原子能力解析\n设备操作指令路由 · 固有约束前置安全校验',
                   facecolor='#EFF6FF', edgecolor='#3B82F6', title_color='#1E40AF', sub_color='#1D4ED8')

    # --- System Roles ---
    draw_round_box(ax, 171, 64, 40, 20,
                   title='Researcher (科研人员)', subtitle='(User Level 2: 实验设计与开发者)', subdesc='编排流程模型 (FLOW_MODELS)\n配置约束规则 (CONSTRAINT_RULE)\n发起生产/仿真实验任务 (TASK)',
                   facecolor='#FEF3C7', edgecolor='#D97706', title_color='#92400E', sub_color='#B45309')

    draw_round_box(ax, 171, 38.5, 40, 21,
                   title='Administrator (系统管理员)', subtitle='(User Level 4/3: 系统与实验室管理)', subdesc='维护属性字典 (PROPERTY_TYPE)\n场景空间三维拓扑 (SCENE_MAIN/DETAIL)\n管理边缘适配器契约 (ADAPTER_INDEX)',
                   facecolor='#FEE2E2', edgecolor='#DC2626', title_color='#991B1B', sub_color='#B91C1C')

    draw_round_box(ax, 171, 14, 40, 20,
                   title='Users (科研协作组)', subtitle='(User Level 1/2: 观察者与科研受众)', subdesc='订阅高频遥测大屏 (Data Access)\n实时查看设备数字孪生姿态与状态\n接收安全联锁与违规预警 (VIOLATION_LOG)',
                   facecolor='#F3E8FF', edgecolor='#9333EA', title_color='#6B21A8', sub_color='#7E22CE')

    # =========================================================================
    # 3. CONNECTIONS & BUSES
    # =========================================================================

    # --- A. TOP RETURN CONTROL BUSES (Clean routing along left perimeter, never crossing boxes) ---
    
    # Bus 1: Administrator -> Exp.Process Lib (Manage)
    draw_polyline_arrow(ax, [
        (211, 45), (217.5, 45), (217.5, 109.5), (3.5, 109.5), (3.5, 44.7), (10, 44.7)
    ], label='Manage (流程规范管理)', color='#DC2626', label_color='#991B1B', label_pos=(110, 109.5))

    # Bus 2: Administrator -> Meta Lib (Manage)
    draw_polyline_arrow(ax, [
        (211, 49), (215.5, 49), (215.5, 106.0), (5.5, 106.0), (5.5, 61.2), (10, 61.2)
    ], label='Manage (元数据空间治理)', color='#DC2626', label_color='#991B1B', label_pos=(110, 106.0))

    # Bus 3: Researcher -> Algorithm Lib (Operation)
    draw_polyline_arrow(ax, [
        (171, 75), (163, 75), (163, 102.5), (22.5, 102.5), (22.5, 85.0)
    ], label='Operation (约束与算法配置)', color='#D97706', label_color='#92400E', label_pos=(110, 102.5))

    # Bus 4: Researcher -> Meta Driven (Operation)
    draw_polyline_arrow(ax, [
        (171, 71), (159, 71), (159, 99.0), (48, 99.0), (48, 69.5)
    ], label='Operation (元模型驱动与编排)', color='#D97706', label_color='#92400E', label_pos=(110, 99.0))

    # --- B. INTERNAL STORAGE LAYER CONNECTIONS ---
    draw_polyline_arrow(ax, [(35, 61.2), (38, 61.2)], color='#3B82F6')
    draw_polyline_arrow(ax, [(58, 65), (62, 70)], color='#3B82F6')
    draw_polyline_arrow(ax, [(58, 57), (62, 51)], color='#3B82F6')

    # --- C. STORAGE TO RUNTIME SERVICE POOLS ---
    draw_polyline_arrow(ax, [
        (35, 78.2), (62, 78.2), (62, 85.0), (97, 85.0), (97, 76), (103, 76)
    ], label='Operation (规则载入求值)', color='#7C3AED', label_color='#5B21B6', label_pos=(75, 86.5))

    draw_polyline_arrow(ax, [
        (92, 73), (103, 73)
    ], label='Export (数据挖掘)', color='#10B981', label_color='#065F46', label_pos=(97.5, 74.5))

    draw_polyline_arrow(ax, [
        (92, 67), (96.5, 67), (96.5, 53), (103, 53)
    ], label='Export (动态时序)', color='#10B981', label_color='#065F46', label_pos=(96.5, 60))

    draw_polyline_arrow(ax, [
        (92, 50), (103, 47)
    ], label='Export (状态缓存)', color='#2563EB', label_color='#1E40AF', label_pos=(97.5, 49.0))

    draw_polyline_arrow(ax, [
        (92, 44), (96.5, 44), (96.5, 27), (103, 27)
    ], label='Export (能力契约)', color='#2563EB', label_color='#1E40AF', label_pos=(96.5, 35.5))

    draw_polyline_arrow(ax, [
        (35, 42), (95, 42), (95, 18), (103, 18)
    ], label='Invoke (细粒度能力调用服务)', color='#7C3AED', label_color='#5B21B6', label_pos=(66, 40.2))

    # --- D. SERVICE POOLS TO EDGE & HARDWARE ---
    draw_polyline_arrow(ax, [(46, 20), (49, 20)], color='#059669')
    draw_polyline_arrow(ax, [(49, 14), (46, 14)], color='#059669')
    ax.text(47.5, 17, '总线互联', ha='center', va='center', fontsize=6.8, fontweight='bold', color='#065F46',
            bbox=dict(boxstyle='square,pad=0.1', fc='#FFFFFF', ec='none'), zorder=6)

    draw_polyline_arrow(ax, [
        (103, 15), (96, 15), (96, 4.0), (28, 4.0), (28, 9)
    ], label='Control (指令下发)', color='#DC2626', label_color='#991B1B', label_pos=(62, 4.0))

    draw_polyline_arrow(ax, [
        (46, 11), (97, 11), (97, 14), (103, 14)
    ], label='Collect (状态采集)', color='#059669', label_color='#047857', label_pos=(71, 11))

    draw_polyline_arrow(ax, [
        (28, 26), (28, 33), (98, 33), (98, 41), (103, 41)
    ], label='Push (高频遥测直推)', color='#059669', label_color='#047857', label_pos=(62, 33))

    # --- E. SERVICE POOLS TO USERS (Share) ---
    draw_polyline_arrow(ax, [(149, 70), (171, 29)], label='Share (告警/分析)',
                        color='#9333EA', label_color='#6B21A8', linestyle='--', label_pos=(160, 58))

    draw_polyline_arrow(ax, [(149, 45), (171, 24)], label='Share (遥测大屏)',
                        color='#9333EA', label_color='#6B21A8', linestyle='--', label_pos=(160, 39))

    draw_polyline_arrow(ax, [(149, 20), (171, 19)], label='Share (孪生监控)',
                        color='#9333EA', label_color='#6B21A8', linestyle='--', label_pos=(160, 20))

    # =========================================================================
    # 4. SAVE OUTPUTS
    # =========================================================================
    png_path = os.path.join(out_dir, 'SmartLab2.0_系统架构图_流程链路版.png')
    svg_path = os.path.join(out_dir, 'SmartLab2.0_系统架构图_流程链路版.svg')
    plt.tight_layout()
    plt.savefig(png_path, dpi=300, facecolor=fig.get_facecolor(), edgecolor='none')
    plt.savefig(svg_path, dpi=300, facecolor=fig.get_facecolor(), edgecolor='none')
    plt.close()
    print('Successfully re-generated:', png_path, svg_path)

if __name__ == '__main__':
    build_diagram()
