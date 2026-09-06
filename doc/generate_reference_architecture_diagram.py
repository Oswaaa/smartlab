# -*- coding: utf-8 -*-
import os
import matplotlib.pyplot as plt
import matplotlib.patches as patches
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch, Circle

plt.rcParams['font.sans-serif'] = ['Microsoft YaHei', 'SimHei', 'Arial', 'DejaVu Sans']
plt.rcParams['axes.unicode_minus'] = False

out_dir = r'd:\SmartLab2.0\doc\generated_images'
os.makedirs(out_dir, exist_ok=True)

def draw_cylinder(ax, x, y, w, h, facecolor='#EFF6FF', edgecolor='#3B82F6', title='', subtitle='', text_color='#1E3A8A', fs_title=8.2, fs_sub=6.5):
    eh = h * 0.18
    rect = patches.Rectangle((x, y + eh/2), w, h - eh, facecolor=facecolor, edgecolor='none', zorder=2)
    ax.add_patch(rect)
    ax.plot([x, x], [y + eh/2, y + h - eh/2], color=edgecolor, lw=1.5, zorder=3)
    ax.plot([x + w, x + w], [y + eh/2, y + h - eh/2], color=edgecolor, lw=1.5, zorder=3)
    bottom = patches.Arc((x + w/2, y + eh/2), w, eh, angle=0, theta1=180, theta2=360, color=edgecolor, lw=1.5, zorder=3)
    ax.add_patch(bottom)
    bottom_fill = patches.Ellipse((x + w/2, y + eh/2), w, eh, facecolor=facecolor, edgecolor='none', zorder=2)
    ax.add_patch(bottom_fill)
    top = patches.Ellipse((x + w/2, y + h - eh/2), w, eh, facecolor=facecolor, edgecolor=edgecolor, lw=1.5, zorder=3)
    ax.add_patch(top)
    if title:
        ax.text(x + w/2, y + h*0.55, title, ha='center', va='center', fontsize=fs_title, fontweight='bold', color=text_color, zorder=4)
    if subtitle:
        ax.text(x + w/2, y + h*0.25, subtitle, ha='center', va='center', fontsize=fs_sub, color='#475569', zorder=4)

def draw_split_cylinder(ax, x, y, w, h, main_title, left_title, left_sub, right_title, right_sub, facecolor='#F0FDF4', edgecolor='#16A34A', fs_main=9.2, fs_sub=6.2):
    eh = h * 0.16
    rect = patches.Rectangle((x, y + eh/2), w, h - eh, facecolor=facecolor, edgecolor='none', zorder=2)
    ax.add_patch(rect)
    ax.plot([x, x], [y + eh/2, y + h - eh/2], color=edgecolor, lw=1.5, zorder=3)
    ax.plot([x + w, x + w], [y + eh/2, y + h - eh/2], color=edgecolor, lw=1.5, zorder=3)
    bottom = patches.Arc((x + w/2, y + eh/2), w, eh, angle=0, theta1=180, theta2=360, color=edgecolor, lw=1.5, zorder=3)
    ax.add_patch(bottom)
    bottom_fill = patches.Ellipse((x + w/2, y + eh/2), w, eh, facecolor=facecolor, edgecolor='none', zorder=2)
    ax.add_patch(bottom_fill)
    top = patches.Ellipse((x + w/2, y + h - eh/2), w, eh, facecolor=facecolor, edgecolor=edgecolor, lw=1.5, zorder=3)
    ax.add_patch(top)
    
    ax.text(x + w/2, y - 2.2, main_title, ha='center', va='center', fontsize=fs_main, fontweight='bold', color='#0F172A', zorder=4)
    
    inner_w = w * 0.43
    inner_h = h * 0.58
    inner_y = y + h * 0.18
    draw_cylinder(ax, x + w*0.05, inner_y, inner_w, inner_h, facecolor='#FFFFFF', edgecolor=edgecolor, title=left_title, subtitle=left_sub, text_color=edgecolor, fs_title=fs_main*0.88, fs_sub=fs_sub)
    draw_cylinder(ax, x + w*0.52, inner_y, inner_w, inner_h, facecolor='#FFFFFF', edgecolor=edgecolor, title=right_title, subtitle=right_sub, text_color=edgecolor, fs_title=fs_main*0.88, fs_sub=fs_sub)

def build_diagram():
    fig, ax = plt.subplots(figsize=(20, 11.5), dpi=300)
    fig.patch.set_facecolor('#F8FAFC')
    ax.set_facecolor('#F8FAFC')
    ax.set_xlim(0, 195)
    ax.set_ylim(0, 112)
    ax.axis('off')

    canvas = FancyBboxPatch((1.5, 1.5), 192, 109, boxstyle='round,pad=0.5,rounding_size=2.0',
                            facecolor='#FFFFFF', edgecolor='#CBD5E1', linewidth=1.5, zorder=1)
    ax.add_patch(canvas)

    ax.text(97.5, 107.5, 'SmartLab 2.0 智能实验室系统总体结构图 (面向任务的可执行建模与运行时体系)',
            ha='center', va='center', fontsize=15.5, fontweight='bold', color='#0F172A', zorder=4)
    ax.text(97.5, 104.2, '严格对齐参考架构拓扑 · 融合完整数据库表规范 (资源层/工作流层/约束层) · 贯通控制/采集/联锁/仿真全链路',
            ha='center', va='center', fontsize=9.2, color='#64748B', zorder=4)

    # 1. ROLES
    admin_box = FancyBboxPatch((4, 52), 15, 12, boxstyle='round,pad=0.3,rounding_size=1.0',
                              facecolor='#FEF3C7', edgecolor='#D97706', linewidth=1.5, zorder=3)
    ax.add_patch(admin_box)
    Circle_admin = Circle((11.5, 60.5), 2.2, facecolor='#D97706', edgecolor='none', zorder=4)
    ax.add_patch(Circle_admin)
    ax.text(11.5, 55.5, 'Administrator\n(系统管理员)', ha='center', va='center', fontsize=8.0, fontweight='bold', color='#92400E', zorder=4)

    res_box = FancyBboxPatch((27, 77), 15, 11.5, boxstyle='round,pad=0.3,rounding_size=1.0',
                             facecolor='#E0E7FF', edgecolor='#4F46E5', linewidth=1.5, zorder=3)
    ax.add_patch(res_box)
    Circle_res = Circle((34.5, 85), 2.0, facecolor='#4F46E5', edgecolor='none', zorder=4)
    ax.add_patch(Circle_res)
    ax.text(34.5, 80.5, 'Researcher\n(科研人员)', ha='center', va='center', fontsize=8.0, fontweight='bold', color='#3730A3', zorder=4)

    # Users (Shifted to y=52 to avoid overlapping with Push arrow at y=40)
    users_box = FancyBboxPatch((134, 52), 14, 15, boxstyle='round,pad=0.3,rounding_size=1.0',
                               facecolor='#F3E8FF', edgecolor='#9333EA', linewidth=1.5, zorder=3)
    ax.add_patch(users_box)
    Circle_u1 = Circle((141, 61.5), 1.8, facecolor='#9333EA', edgecolor='none', zorder=4)
    Circle_u2 = Circle((138, 59.5), 1.5, facecolor='#A855F7', edgecolor='none', zorder=4)
    Circle_u3 = Circle((144, 59.5), 1.5, facecolor='#A855F7', edgecolor='none', zorder=4)
    ax.add_patch(Circle_u1); ax.add_patch(Circle_u2); ax.add_patch(Circle_u3)
    ax.text(141, 54.5, 'Users\n(科研终端用户)', ha='center', va='center', fontsize=8, fontweight='bold', color='#6B21A8', zorder=4)

    # 2. LIBRARIES
    draw_cylinder(ax, 21, 51.5, 12, 13, facecolor='#FEF9C3', edgecolor='#CA8A04',
                  title='Meta Lib\n(元数据库)', subtitle='PROPERTY_TYPE\nSCENE_MAIN/DETAIL\nRESOURCE_STRUCTURE', text_color='#854D0E', fs_title=8.0, fs_sub=5.8)

    draw_cylinder(ax, 54, 86, 28, 11, facecolor='#EEF2FF', edgecolor='#4F46E5',
                  title='Algorithm Lib (算法与约束规则库)',
                  subtitle='CONSTRAINT_RULE (约束规则) · VIOLATION_LOG (违规审计) · 预测聚类算法', text_color='#3730A3', fs_title=8.5, fs_sub=6.0)

    meta_box = FancyBboxPatch((35, 48), 16, 20.5, boxstyle='round,pad=0.3,rounding_size=1.0',
                              facecolor='#F8FAFC', edgecolor='#64748B', linewidth=1.5, zorder=3)
    ax.add_patch(meta_box)
    ax.text(43, 66.2, 'Meta Driven\n(元数据驱动中枢)', ha='center', va='center', fontsize=8.5, fontweight='bold', color='#1E293B', zorder=4)
    sub_m1 = FancyBboxPatch((36.0, 59.8), 14.0, 4.3, boxstyle='round,pad=0.2,rounding_size=0.5', facecolor='#FFFFFF', edgecolor='#94A3B8', zorder=4)
    sub_m2 = FancyBboxPatch((36.0, 54.3), 14.0, 4.3, boxstyle='round,pad=0.2,rounding_size=0.5', facecolor='#FFFFFF', edgecolor='#94A3B8', zorder=4)
    sub_m3 = FancyBboxPatch((36.0, 48.8), 14.0, 4.3, boxstyle='round,pad=0.2,rounding_size=0.5', facecolor='#FFFFFF', edgecolor='#94A3B8', zorder=4)
    ax.add_patch(sub_m1); ax.add_patch(sub_m2); ax.add_patch(sub_m3)
    ax.text(43, 62.0, 'Record Mgmt\n(动态表结构维护)', ha='center', va='center', fontsize=6.8, color='#334155', zorder=5)
    ax.text(43, 56.5, 'Structure Mgmt\n(场景与拓扑管理)', ha='center', va='center', fontsize=6.8, color='#334155', zorder=5)
    ax.text(43, 51.0, 'Search & Mapping\n(实例匹配与映射)', ha='center', va='center', fontsize=6.8, color='#334155', zorder=5)

    cat_d_box = FancyBboxPatch((56, 73.5), 24, 7.5, boxstyle='round,pad=0.3,rounding_size=0.8',
                               facecolor='#F0FDF4', edgecolor='#16A34A', linewidth=1.2, zorder=3)
    ax.add_patch(cat_d_box)
    ax.text(68.0, 78.8, 'Exp.Data Category (数据模板体系)', ha='center', va='center', fontsize=7.8, fontweight='bold', color='#166534', zorder=4)
    c_d1 = FancyBboxPatch((57.2, 74.3), 10.5, 3.2, boxstyle='round,pad=0.2,rounding_size=0.4', facecolor='#FFFFFF', edgecolor='#4ADE80', zorder=4)
    c_d2 = FancyBboxPatch((68.3, 74.3), 10.5, 3.2, boxstyle='round,pad=0.2,rounding_size=0.4', facecolor='#FFFFFF', edgecolor='#4ADE80', zorder=4)
    ax.add_patch(c_d1); ax.add_patch(c_d2)
    ax.text(62.4, 75.9, 'DATA_TEMPLATE_MAIN', ha='center', va='center', fontsize=5.8, color='#15803D', zorder=5)
    ax.text(73.5, 75.9, 'DATA_TEMPLATE_DETAIL', ha='center', va='center', fontsize=5.8, color='#15803D', zorder=5)

    draw_split_cylinder(ax, 54, 53, 28, 16.5,
                        main_title='Exp.Data Lib (实验数据库)',
                        left_title='Inf Lib\n(数据集索引)', left_sub='DATA_INDEX\n(表名/设备绑定)',
                        right_title='Data Lib\n(时序物理记录)', right_sub='DATA_RECORD_XXXX\n(动态字段/采集入库)',
                        facecolor='#DCFCE7', edgecolor='#16A34A', fs_main=8.8, fs_sub=5.8)

    cat_dev_box = FancyBboxPatch((56, 39), 24, 7.5, boxstyle='round,pad=0.3,rounding_size=0.8',
                                 facecolor='#EFF6FF', edgecolor='#2563EB', linewidth=1.2, zorder=3)
    ax.add_patch(cat_dev_box)
    ax.text(68.0, 44.3, 'Device Category (设备分类体系)', ha='center', va='center', fontsize=7.8, fontweight='bold', color='#1E40AF', zorder=4)
    c_dev1 = FancyBboxPatch((57.2, 39.8), 10.5, 3.2, boxstyle='round,pad=0.2,rounding_size=0.4', facecolor='#FFFFFF', edgecolor='#60A5FA', zorder=4)
    c_dev2 = FancyBboxPatch((68.3, 39.8), 10.5, 3.2, boxstyle='round,pad=0.2,rounding_size=0.4', facecolor='#FFFFFF', edgecolor='#60A5FA', zorder=4)
    ax.add_patch(c_dev1); ax.add_patch(c_dev2)
    ax.text(62.4, 41.4, 'DEVICE_CATEGORY', ha='center', va='center', fontsize=6.2, color='#1D4ED8', zorder=5)
    ax.text(73.5, 41.4, '多级树状继承标签', ha='center', va='center', fontsize=6.2, color='#1D4ED8', zorder=5)

    draw_split_cylinder(ax, 54, 18, 28, 16.5,
                        main_title='Device Lib (设备资产与孪生库)',
                        left_title='Inf Lib\n(设备模型库)', left_sub='DEVICE_MODELS\n能力/状态机/BOM/契约',
                        right_title='Data Lib\n(实例与状态)', right_sub='DEVICE_INSTANCES\nDEVICE_TWIN_STATES',
                        facecolor='#DBEAFE', edgecolor='#2563EB', fs_main=8.8, fs_sub=5.8)

    # Exp.Process Lib with larger height to fit text gracefully
    draw_split_cylinder(ax, 54, 3.5, 28, 12.0,
                        main_title='Exp.Process Lib (实验流程与任务执行库)',
                        left_title='Inf Lib\n(流程模型定义)', left_sub='FLOW_MODELS\nFLOW_NODE (DEV/FUNC)',
                        right_title='Element Process\n(任务过程执行)', right_sub='TASK (生产/仿真)\nTASK_STEP / EXEC_LOG',
                        facecolor='#F3E8FF', edgecolor='#7C3AED', fs_main=8.5, fs_sub=5.4)

    # Sources (Shifted slightly so arrows can route smoothly)
    src_data = FancyBboxPatch((85.0, 62), 17.5, 8.5, boxstyle='round,pad=0.2,rounding_size=0.6',
                              facecolor='#F1F5F9', edgecolor='#64748B', linewidth=1.0, zorder=3)
    ax.add_patch(src_data)
    ax.text(93.75, 68.3, 'Source (数据来源)', ha='center', va='center', fontsize=7.2, fontweight='bold', color='#334155', zorder=4)
    s_d1 = FancyBboxPatch((86.0, 65.2), 15.5, 2.4, boxstyle='round,pad=0.2,rounding_size=0.3', facecolor='#FFFFFF', edgecolor='#CBD5E1', zorder=4)
    s_d2 = FancyBboxPatch((86.0, 62.6), 15.5, 2.4, boxstyle='round,pad=0.2,rounding_size=0.3', facecolor='#FFFFFF', edgecolor='#CBD5E1', zorder=4)
    ax.add_patch(s_d1); ax.add_patch(s_d2)
    ax.text(93.75, 66.4, 'Manual Op (人工录入/标定)', ha='center', va='center', fontsize=5.8, color='#475569', zorder=5)
    ax.text(93.75, 63.8, 'Automatic Op (遥测动态入库)', ha='center', va='center', fontsize=5.8, color='#475569', zorder=5)

    src_dev = FancyBboxPatch((85.0, 26.5), 17.5, 8.5, boxstyle='round,pad=0.2,rounding_size=0.6',
                             facecolor='#F1F5F9', edgecolor='#64748B', linewidth=1.0, zorder=3)
    ax.add_patch(src_dev)
    ax.text(93.75, 32.8, 'Source (设备录入)', ha='center', va='center', fontsize=7.2, fontweight='bold', color='#334155', zorder=4)
    s_v1 = FancyBboxPatch((86.0, 29.7), 15.5, 2.4, boxstyle='round,pad=0.2,rounding_size=0.3', facecolor='#FFFFFF', edgecolor='#CBD5E1', zorder=4)
    s_v2 = FancyBboxPatch((86.0, 27.1), 15.5, 2.4, boxstyle='round,pad=0.2,rounding_size=0.3', facecolor='#FFFFFF', edgecolor='#CBD5E1', zorder=4)
    ax.add_patch(s_v1); ax.add_patch(s_v2)
    ax.text(93.75, 30.9, 'Manual Op (台账/BOM装配)', ha='center', va='center', fontsize=5.8, color='#475569', zorder=5)
    ax.text(93.75, 28.3, 'Automatic Op (Adapter注册发现)', ha='center', va='center', fontsize=5.8, color='#475569', zorder=5)

    # 3. SERVICE POOLS
    comp_pool = FancyBboxPatch((106, 80.5), 32, 17.5, boxstyle='round,pad=0.3,rounding_size=1.0',
                               facecolor='#EEF2FF', edgecolor='#6366F1', linewidth=1.5, zorder=3)
    ax.add_patch(comp_pool)
    ax.text(122, 95.2, 'Computing Service Pool (计算与约束服务池)', ha='center', va='center', fontsize=8.8, fontweight='bold', color='#312E81', zorder=4)
    cp1 = FancyBboxPatch((107.5, 88.0), 13.8, 5.2, boxstyle='round,pad=0.2,rounding_size=0.5', facecolor='#FFFFFF', edgecolor='#818CF8', zorder=4)
    cp2 = FancyBboxPatch((122.7, 88.0), 13.8, 5.2, boxstyle='round,pad=0.2,rounding_size=0.5', facecolor='#FFFFFF', edgecolor='#818CF8', zorder=4)
    cp3 = FancyBboxPatch((107.5, 81.8), 13.8, 5.2, boxstyle='round,pad=0.2,rounding_size=0.5', facecolor='#FFFFFF', edgecolor='#818CF8', zorder=4)
    cp4 = FancyBboxPatch((122.7, 81.8), 13.8, 5.2, boxstyle='round,pad=0.2,rounding_size=0.5', facecolor='#FFFFFF', edgecolor='#818CF8', zorder=4)
    ax.add_patch(cp1); ax.add_patch(cp2); ax.add_patch(cp3); ax.add_patch(cp4)
    ax.text(114.4, 90.6, 'Data Forecast\n(实验趋势预测)', ha='center', va='center', fontsize=6.8, color='#3730A3', zorder=5)
    ax.text(129.6, 90.6, 'Data Cluster\n(条件聚类挖掘)', ha='center', va='center', fontsize=6.8, color='#3730A3', zorder=5)
    ax.text(114.4, 84.4, 'Constraint Evaluator\n(200ms软实时约束监控)', ha='center', va='center', fontsize=6.8, color='#3730A3', zorder=5)
    ax.text(129.6, 84.4, 'Data Visualization\n(流程过程与违规大屏)', ha='center', va='center', fontsize=6.8, color='#3730A3', zorder=5)

    data_pool = FancyBboxPatch((107, 43.5), 24, 30.5, boxstyle='round,pad=0.3,rounding_size=1.0',
                               facecolor='#ECFDF5', edgecolor='#10B981', linewidth=1.5, zorder=3)
    ax.add_patch(data_pool)
    ax.text(119, 71.2, 'Data Service Pool\n(数据服务池)', ha='center', va='center', fontsize=8.8, fontweight='bold', color='#065F46', zorder=4)
    dp1 = FancyBboxPatch((108.5, 64.0), 21, 4.3, boxstyle='round,pad=0.2,rounding_size=0.4', facecolor='#FFFFFF', edgecolor='#34D399', zorder=4)
    dp2 = FancyBboxPatch((108.5, 58.7), 21, 4.3, boxstyle='round,pad=0.2,rounding_size=0.4', facecolor='#FFFFFF', edgecolor='#34D399', zorder=4)
    dp3 = FancyBboxPatch((108.5, 53.4), 21, 4.3, boxstyle='round,pad=0.2,rounding_size=0.4', facecolor='#FFFFFF', edgecolor='#34D399', zorder=4)
    dp4 = FancyBboxPatch((108.5, 48.1), 21, 4.3, boxstyle='round,pad=0.2,rounding_size=0.4', facecolor='#FFFFFF', edgecolor='#34D399', zorder=4)
    dp5 = FancyBboxPatch((108.5, 44.2), 21, 3.2, boxstyle='round,pad=0.2,rounding_size=0.4', facecolor='#D1FAE5', edgecolor='#059669', zorder=4)
    ax.add_patch(dp1); ax.add_patch(dp2); ax.add_patch(dp3); ax.add_patch(dp4); ax.add_patch(dp5)
    ax.text(119, 66.1, 'Data Extraction (时序数据抽取)', ha='center', va='center', fontsize=6.8, color='#047857', zorder=5)
    ax.text(119, 60.8, 'Step Snapshot (步骤/变量快照抽取)', ha='center', va='center', fontsize=6.8, color='#047857', zorder=5)
    ax.text(119, 55.5, 'Data Release (SSE/WS实时推送)', ha='center', va='center', fontsize=6.8, color='#047857', zorder=5)
    ax.text(119, 50.2, 'Data Access API (统一数据访问)', ha='center', va='center', fontsize=6.8, color='#047857', zorder=5)
    ax.text(119, 45.8, 'Observation Space (内存只读快照)', ha='center', va='center', fontsize=6.2, fontweight='bold', color='#065F46', zorder=5)

    func_pool = FancyBboxPatch((107, 12), 24, 24, boxstyle='round,pad=0.3,rounding_size=1.0',
                               facecolor='#EFF6FF', edgecolor='#3B82F6', linewidth=1.5, zorder=3)
    ax.add_patch(func_pool)
    ax.text(119, 33.2, 'Function Service Pool\n(功能与控制服务池)', ha='center', va='center', fontsize=8.8, fontweight='bold', color='#1E40AF', zorder=4)
    fp1 = FancyBboxPatch((108.5, 26.0), 21, 4.8, boxstyle='round,pad=0.2,rounding_size=0.4', facecolor='#FFFFFF', edgecolor='#60A5FA', zorder=4)
    fp2 = FancyBboxPatch((108.5, 19.8), 21, 4.8, boxstyle='round,pad=0.2,rounding_size=0.4', facecolor='#FFFFFF', edgecolor='#60A5FA', zorder=4)
    fp3 = FancyBboxPatch((108.5, 13.6), 21, 4.8, boxstyle='round,pad=0.2,rounding_size=0.4', facecolor='#FFFFFF', edgecolor='#60A5FA', zorder=4)
    ax.add_patch(fp1); ax.add_patch(fp2); ax.add_patch(fp3)
    ax.text(119, 28.4, 'Device State (数字孪生状态管理)\n(OP/CMD双状态机流转)', ha='center', va='center', fontsize=6.6, color='#1D4ED8', zorder=5)
    ax.text(119, 22.2, 'Device Function (设备操作功能)\n(指令封装/参数校验/路由)', ha='center', va='center', fontsize=6.6, color='#1D4ED8', zorder=5)
    ax.text(119, 16.0, 'Device Capability (设备能力抽象)\n(能力接口/状态转移声明)', ha='center', va='center', fontsize=6.6, color='#1D4ED8', zorder=5)

    sub_svc = FancyBboxPatch((86, 6.5), 17, 5.8, boxstyle='round,pad=0.2,rounding_size=0.5',
                             facecolor='#FDF4FF', edgecolor='#C084FC', linewidth=1.0, zorder=4)
    ax.add_patch(sub_svc)
    ax.text(94.5, 9.4, 'fine-grained sub service\n(细粒度能力调用服务)', ha='center', va='center', fontsize=6.5, fontweight='bold', color='#7E22CE', zorder=5)

    # 4. ADAPTERS & HARDWARE
    adp_box = FancyBboxPatch((155, 10), 20, 52, boxstyle='round,pad=0.3,rounding_size=1.0',
                             facecolor='#F8FAFC', edgecolor='#475569', linewidth=1.5, zorder=3)
    ax.add_patch(adp_box)
    ax.text(165, 59.2, 'Adapters\n(边缘适配器层)', ha='center', va='center', fontsize=9.5, fontweight='bold', color='#0F172A', zorder=4)
    
    adp1 = FancyBboxPatch((157, 48), 16, 7.5, boxstyle='round,pad=0.2,rounding_size=0.5', facecolor='#FFFFFF', edgecolor='#94A3B8', zorder=4)
    adp2 = FancyBboxPatch((157, 38.5), 16, 7.5, boxstyle='round,pad=0.2,rounding_size=0.5', facecolor='#FFFFFF', edgecolor='#94A3B8', zorder=4)
    adpn = FancyBboxPatch((157, 20.5), 16, 7.5, boxstyle='round,pad=0.2,rounding_size=0.5', facecolor='#FFFFFF', edgecolor='#94A3B8', zorder=4)
    ax.add_patch(adp1); ax.add_patch(adp2); ax.add_patch(adpn)
    ax.text(165, 51.7, 'Adapter 1 (反应釜)\nPLC-MQTT / 串口', ha='center', va='center', fontsize=6.8, color='#1E293B', zorder=5)
    ax.text(165, 42.2, 'Adapter 2 (温湿度计)\n遥测高频上报', ha='center', va='center', fontsize=6.8, color='#1E293B', zorder=5)
    ax.text(165, 32.5, '· · ·', ha='center', va='center', fontsize=12, color='#64748B', zorder=5)
    ax.text(165, 24.2, 'Adapter n (机械臂/光谱)\n四层架构/虚拟租约', ha='center', va='center', fontsize=6.8, color='#1E293B', zorder=5)
    
    adp_db = FancyBboxPatch((157, 12), 16, 5.5, boxstyle='round,pad=0.2,rounding_size=0.4', facecolor='#FEF3C7', edgecolor='#D97706', zorder=4)
    ax.add_patch(adp_db)
    ax.text(165, 14.7, 'ADAPTER_INDEX (注册)\nVIRTUAL_LEASE (租约)', ha='center', va='center', fontsize=6.0, fontweight='bold', color='#92400E', zorder=5)

    hw_box = FancyBboxPatch((179, 10), 10, 52, boxstyle='round,pad=0.3,rounding_size=1.0',
                            facecolor='#F1F5F9', edgecolor='#94A3B8', linewidth=1.0, zorder=3)
    ax.add_patch(hw_box)
    ax.text(184, 59.2, 'Hardware\n(装备层)', ha='center', va='center', fontsize=8, fontweight='bold', color='#475569', zorder=4)
    
    hw1 = FancyBboxPatch((180, 48), 8, 7.5, boxstyle='round,pad=0.2,rounding_size=0.4', facecolor='#FFFFFF', edgecolor='#CBD5E1', zorder=4)
    hw2 = FancyBboxPatch((180, 38.5), 8, 7.5, boxstyle='round,pad=0.2,rounding_size=0.4', facecolor='#FFFFFF', edgecolor='#CBD5E1', zorder=4)
    hw3 = FancyBboxPatch((180, 20.5), 8, 7.5, boxstyle='round,pad=0.2,rounding_size=0.4', facecolor='#FFFFFF', edgecolor='#CBD5E1', zorder=4)
    ax.add_patch(hw1); ax.add_patch(hw2); ax.add_patch(hw3)
    ax.text(184, 51.7, '反应釜\nReactor', ha='center', va='center', fontsize=6.2, color='#334155', zorder=5)
    ax.text(184, 42.2, '机械臂\nRobot', ha='center', va='center', fontsize=6.2, color='#334155', zorder=5)
    ax.text(184, 24.2, '分析仪\nSensor', ha='center', va='center', fontsize=6.2, color='#334155', zorder=5)

    # 5. CONNECTING ARROWS & LABELS
    def draw_labeled_arrow(p1, p2, text='', rad=0.0, color='#64748B', text_color='#0F172A', fs=7.0, b_offset=(0,0)):
        arrow = FancyArrowPatch(p1, p2, connectionstyle=f'arc3,rad={rad}',
                                arrowstyle='-|>', mutation_scale=11, lw=1.3, color=color, zorder=6)
        ax.add_patch(arrow)
        if text:
            mx = (p1[0] + p2[0])/2 + b_offset[0]
            my = (p1[1] + p2[1])/2 + b_offset[1]
            ax.text(mx, my, text, ha='center', va='center', fontsize=fs, fontweight='bold',
                    color=text_color, bbox=dict(boxstyle='square,pad=0.15', fc='#FFFFFF', ec='none', alpha=0.9), zorder=7)

    # Administrator -> Meta Lib
    draw_labeled_arrow((19, 58), (21, 58), text='Manage', color='#D97706', text_color='#B45309', fs=6.8, b_offset=(0, 1.3))
    
    # Administrator -> Exp.Process Lib
    ax.plot([11.5, 11.5, 54], [52, 6.5, 6.5], color='#D97706', lw=1.3, zorder=5)
    arrow_p = FancyArrowPatch((53, 6.5), (54, 6.5), arrowstyle='-|>', mutation_scale=11, lw=1.3, color='#D97706', zorder=6)
    ax.add_patch(arrow_p)
    ax.text(32, 8.0, 'Manage (流程模型管理)', ha='center', va='center', fontsize=6.8, fontweight='bold', color='#B45309',
            bbox=dict(boxstyle='square,pad=0.15', fc='#FFFFFF', ec='none', alpha=0.9), zorder=7)

    # Researcher -> Algorithm Lib & Meta Driven
    draw_labeled_arrow((42, 85), (54, 88), text='Operation', color='#4F46E5', text_color='#4338CA', fs=6.8, b_offset=(0, 1.5))
    draw_labeled_arrow((34.5, 77), (41, 68.5), text='Operation', color='#4F46E5', text_color='#4338CA', fs=6.8, b_offset=(-3, 0))

    # Meta Lib -> Meta Driven
    draw_labeled_arrow((33, 58), (35, 58), color='#CA8A04')

    # Meta Driven -> Exp.Data Lib & Device Lib
    draw_labeled_arrow((51, 62), (54, 62), color='#64748B')
    draw_labeled_arrow((51, 52), (54, 27), color='#64748B')

    # Category Support
    draw_labeled_arrow((68.0, 73.5), (68.0, 69.5), text='Support', color='#16A34A', text_color='#15803D', fs=6.8, b_offset=(0, 0))
    draw_labeled_arrow((68.0, 39), (68.0, 34.5), text='Support', color='#2563EB', text_color='#1D4ED8', fs=6.8, b_offset=(0, 0))

    # Algorithm Lib -> Computing Service Pool
    draw_labeled_arrow((82, 91), (106, 91), text='Operation', color='#4F46E5', text_color='#4338CA', fs=7.0, b_offset=(0, 1.4))

    # Exp.Data Lib -> Computing Service Pool (Export)
    draw_labeled_arrow((82, 65), (106, 83), text='Export', color='#16A34A', text_color='#15803D', fs=6.8, b_offset=(0, 1.3))

    # Exp.Data Lib -> Data Service Pool (Export - routed below Source box at y=57)
    draw_labeled_arrow((82, 56), (107, 56), text='Export', color='#16A34A', text_color='#15803D', fs=6.8, b_offset=(0, 1.3))

    # Device Lib -> Data Service Pool (Export - routed to bottom of Data Service Pool)
    draw_labeled_arrow((82, 28), (107, 46), text='Export', color='#2563EB', text_color='#1D4ED8', fs=6.8, b_offset=(0, 1.3))

    # Device Lib -> Function Service Pool (Export - routed below Source box at y=21)
    draw_labeled_arrow((82, 21), (107, 21), text='Export', color='#2563EB', text_color='#1D4ED8', fs=6.8, b_offset=(0, 1.3))

    # Exp.Process Lib -> fine-grained sub service -> Device Function
    draw_labeled_arrow((82, 9.4), (86, 9.4), text='Invoke', color='#7C3AED', text_color='#6D28D9', fs=6.8, b_offset=(0, 1.2))
    draw_labeled_arrow((103, 11), (108.5, 21), color='#7C3AED')

    # Sources Import -> Libs
    draw_labeled_arrow((85.0, 65), (82, 65), text='Import', color='#64748B', text_color='#334155', fs=6.5, b_offset=(0, 1.2))
    draw_labeled_arrow((85.0, 30), (82, 30), text='Import', color='#64748B', text_color='#334155', fs=6.5, b_offset=(0, 1.2))

    # Function Service Pool <-> Adapters
    draw_labeled_arrow((131, 26), (155, 26), text='Control (指令下发)', color='#DC2626', text_color='#B91C1C', fs=6.8, b_offset=(0, 1.4))
    draw_labeled_arrow((155, 18), (131, 18), text='Collect (状态采集)', color='#059669', text_color='#047857', fs=6.8, b_offset=(0, -1.4))

    # Adapters -> Data Service Pool (Push - passing under Users box at y=39)
    ax.plot([155, 140, 140, 131], [38, 38, 45, 45], color='#059669', lw=1.3, zorder=5)
    arrow_push = FancyArrowPatch((132, 45), (131, 45), arrowstyle='-|>', mutation_scale=11, lw=1.3, color='#059669', zorder=6)
    ax.add_patch(arrow_push)
    ax.text(143, 39.5, 'Push (高频遥测直推)', ha='center', va='center', fontsize=6.8, fontweight='bold', color='#047857',
            bbox=dict(boxstyle='square,pad=0.15', fc='#FFFFFF', ec='none', alpha=0.9), zorder=7)

    # Adapters <-> Hardware
    for y_hw in [51.7, 42.2, 24.2]:
        draw_labeled_arrow((173, y_hw), (180, y_hw), color='#64748B')
        draw_labeled_arrow((180, y_hw), (173, y_hw), color='#64748B')

    # Users Sharing
    draw_labeled_arrow((138, 85), (141, 67), text='Share', rad=-0.15, color='#9333EA', text_color='#7E22CE', fs=6.8, b_offset=(2, 0))
    draw_labeled_arrow((131, 59), (134, 59), text='Share', color='#9333EA', text_color='#7E22CE', fs=6.8, b_offset=(0, 1.3))
    draw_labeled_arrow((131, 31), (138, 52), text='Share', rad=0.15, color='#9333EA', text_color='#7E22CE', fs=6.8, b_offset=(-2, 0))

    png_path = os.path.join(out_dir, 'SmartLab2.0_系统总体结构图_基于参考模型.png')
    svg_path = os.path.join(out_dir, 'SmartLab2.0_系统总体结构图_基于参考模型.svg')
    plt.tight_layout()
    plt.savefig(png_path, dpi=300, facecolor=fig.get_facecolor(), edgecolor='none')
    plt.savefig(svg_path, dpi=300, facecolor=fig.get_facecolor(), edgecolor='none')
    plt.close()
    print('Successfully re-generated PNG and SVG')

if __name__ == '__main__':
    build_diagram()
