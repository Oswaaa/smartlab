import os
import matplotlib.pyplot as plt
import matplotlib.patches as patches
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch, Polygon
import numpy as np

# Set Chinese font
plt.rcParams['font.sans-serif'] = ['Microsoft YaHei', 'SimHei', 'Arial']
plt.rcParams['axes.unicode_minus'] = False

out_dir = r"d:\SmartLab2.0\doc\generated_images"
os.makedirs(out_dir, exist_ok=True)

DEPTH_ANGLE = np.radians(24)
DEPTH_SCALE = 0.55

def project(x, y, z):
    px = x + y * np.cos(DEPTH_ANGLE) * DEPTH_SCALE
    py = z + y * np.sin(DEPTH_ANGLE) * DEPTH_SCALE
    return px, py

def draw_3d_box(ax, x0, y0, z0, dx, dy, dz, top_c, front_c, right_c, edge_c, zord=10, alpha=0.96):
    t1 = project(x0, y0, z0 + dz)
    t2 = project(x0 + dx, y0, z0 + dz)
    t3 = project(x0 + dx, y0 + dy, z0 + dz)
    t4 = project(x0, y0 + dy, z0 + dz)
    top_p = Polygon([t1, t2, t3, t4], closed=True, facecolor=top_c, edgecolor=edge_c, linewidth=1.2, zorder=zord+3, alpha=alpha)
    ax.add_patch(top_p)

    f1 = project(x0, y0, z0)
    f2 = project(x0 + dx, y0, z0)
    f3 = project(x0 + dx, y0, z0 + dz)
    f4 = project(x0, y0, z0 + dz)
    front_p = Polygon([f1, f2, f3, f4], closed=True, facecolor=front_c, edgecolor=edge_c, linewidth=1.2, zorder=zord+2, alpha=alpha)
    ax.add_patch(front_p)

    r1 = project(x0 + dx, y0, z0)
    r2 = project(x0 + dx, y0 + dy, z0)
    r3 = project(x0 + dx, y0 + dy, z0 + dz)
    r4 = project(x0 + dx, y0, z0 + dz)
    right_p = Polygon([r1, r2, r3, r4], closed=True, facecolor=right_c, edgecolor=edge_c, linewidth=1.2, zorder=zord+2, alpha=alpha)
    ax.add_patch(right_p)

    ctx = (t1[0] + t2[0] + t3[0] + t4[0]) / 4
    cty = (t1[1] + t2[1] + t3[1] + t4[1]) / 4
    return ctx, cty

def render_vertical_conceptual_structure():
    fig, ax = plt.subplots(figsize=(15, 11), dpi=300)
    fig.patch.set_facecolor('#F8FAFC')
    ax.set_facecolor('#F8FAFC')
    ax.set_xlim(0, 160)
    ax.set_ylim(0, 115)
    ax.axis('off')

    # Main Card
    outer_card = FancyBboxPatch((1.0, 1.0), 158, 113, boxstyle="round,pad=0.5,rounding_size=2.0",
                                facecolor='#FFFFFF', edgecolor='#CBD5E1', linewidth=1.5, zorder=1)
    ax.add_patch(outer_card)

    # Header Titles
    ax.text(80, 109.5, "纵向概念结构图 (单步执行闭环链路)", ha='center', va='center',
            fontsize=17, fontweight='bold', color='#0F172A', zorder=10)
    ax.text(80, 106.2, "单步控制链路：工作流执行节点 → 设备状态机 → 边缘适配器 → 物理设备 (反馈沿原路严格收敛)", ha='center', va='center',
            fontsize=9.8, color='#475569', zorder=10)

    # -------------------------------------------------------------
    # Left Side: 3D Isometric View matching the user's wireframe (x: 8 to 82)
    # -------------------------------------------------------------
    sub_title_3d = FancyBboxPatch((8, 99.5), 70, 5.2, boxstyle="round,pad=0.2,rounding_size=0.6",
                                  facecolor='#EFF6FF', edgecolor='#93C5FD', linewidth=1.0, zorder=3)
    ax.add_patch(sub_title_3d)
    ax.text(43, 102.1, "【三维立体构型】四层空间不可旁路穿透透视", ha='center', va='center',
            fontsize=9.5, fontweight='bold', color='#1E40AF', zorder=4)

    W = 46
    D = 30
    H = 60
    base_x = 16
    base_z = 12

    # 4 Layers Z-positions
    z_layers = [
        base_z,                     # Layer 4: Physical
        base_z + H * 0.333,         # Layer 3: Adapter
        base_z + H * 0.667,         # Layer 2: State Machine
        base_z + H                  # Layer 1: Workflow Node
    ]

    # Back wireframes
    p_bl_bot = project(base_x, D, base_z)
    p_bl_top = project(base_x, D, base_z + H)
    p_br_bot = project(base_x + W, D, base_z)
    p_br_top = project(base_x + W, D, base_z + H)
    ax.plot([p_bl_bot[0], p_bl_top[0]], [p_bl_bot[1], p_bl_top[1]], color='#94A3B8', linestyle='--', linewidth=1.2, zorder=2)
    ax.plot([p_br_bot[0], p_br_top[0]], [p_br_bot[1], p_br_top[1]], color='#94A3B8', linestyle='--', linewidth=1.2, zorder=2)
    ax.plot([p_bl_bot[0], p_br_bot[0]], [p_bl_bot[1], p_br_bot[1]], color='#94A3B8', linestyle='--', linewidth=1.2, zorder=2)

    tier_3d_info = [
        ("第 4 层: 物理设备层", "物理反应釜 / PLC", "#334155", "#1E293B", "#0F172A", "#475569", "#F8FAFC", "#94A3B8"),
        ("第 3 层: 边缘适配器层", "4层架构 · 协议转换", "#0D9488", "#0F766E", "#115E59", "#14B8A6", "#FFFFFF", "#CCFBF1"),
        ("第 2 层: 设备状态机层", "★ 发令必经之路 · 独占控制", "#7C3AED", "#6D28D9", "#5B21B6", "#8B5CF6", "#FFFFFF", "#DDD6FE"),
        ("第 1 层: 工作流节点层", "DEV_NODE · 声明能力", "#2563EB", "#1D4ED8", "#1E40AF", "#3B82F6", "#FFFFFF", "#BFDBFE")
    ]

    centers_3d = []

    for idx, z in enumerate(z_layers):
        zord = 5 + idx * 8
        # Shelf plane
        p0 = project(base_x, 0, z)
        p1 = project(base_x + W, 0, z)
        p2 = project(base_x + W, D, z)
        p3 = project(base_x, D, z)
        shelf = Polygon([p0, p1, p2, p3], closed=True, facecolor='#F1F5F9', edgecolor='#94A3B8',
                        alpha=0.55, linewidth=1.2, zorder=zord)
        ax.add_patch(shelf)

        # 3D Slab
        title, sub, tc, fc, rc, ec, txt_c, sub_c = tier_3d_info[idx]
        card_w = 38
        card_d = 20
        card_dz = 2.5
        card_x0 = base_x + (W - card_w) / 2
        card_y0 = 6.0

        ctx, cty = draw_3d_box(ax, card_x0, card_y0, z, card_w, card_d, card_dz,
                               tc, fc, rc, ec, zord=zord)
        centers_3d.append((ctx, cty, z + card_dz))

        ax.text(ctx, cty + 1.2, title, ha='center', va='center',
                fontsize=8.2, fontweight='bold', color=txt_c, zorder=zord+5)
        ax.text(ctx, cty - 1.2, sub, ha='center', va='center',
                fontsize=6.8, color=sub_c, zorder=zord+5)

    # Front outer wireframe posts & beams
    p_fl_bot = project(base_x, 0, base_z)
    p_fl_top = project(base_x, 0, base_z + H)
    p_fr_bot = project(base_x + W, 0, base_z)
    p_fr_top = project(base_x + W, 0, base_z + H)
    ax.plot([p_fl_bot[0], p_fl_top[0]], [p_fl_bot[1], p_fl_top[1]], color='#1E293B', linewidth=2.0, zorder=40)
    ax.plot([p_fr_bot[0], p_fr_top[0]], [p_fr_bot[1], p_fr_top[1]], color='#1E293B', linewidth=2.0, zorder=40)

    for z in z_layers:
        pfl = project(base_x, 0, z)
        pfr = project(base_x + W, 0, z)
        ax.plot([pfl[0], pfr[0]], [pfl[1], pfr[1]], color='#1E293B', linewidth=1.5, zorder=40)
        pbr = project(base_x + W, D, z)
        ax.plot([pfr[0], pbr[0]], [pfr[1], pbr[1]], color='#1E293B', linewidth=1.5, zorder=40)

    p_top_fl = project(base_x, 0, base_z + H)
    p_top_fr = project(base_x + W, 0, base_z + H)
    p_top_br = project(base_x + W, D, base_z + H)
    p_top_bl = project(base_x, D, base_z + H)
    ax.plot([p_top_fl[0], p_top_bl[0]], [p_top_fl[1], p_top_bl[1]], color='#1E293B', linewidth=1.5, zorder=40)
    ax.plot([p_top_bl[0], p_top_br[0]], [p_top_bl[1], p_top_br[1]], color='#1E293B', linewidth=1.5, zorder=40)

    # Vertical 3D Arrows
    # Downward
    ax.add_patch(FancyArrowPatch((centers_3d[3][0]-8, centers_3d[3][1]-4), (centers_3d[2][0]-8, centers_3d[2][1]+4),
                                 arrowstyle='-|>', mutation_scale=12, linewidth=2.2, color='#2563EB', zorder=45))
    ax.add_patch(FancyArrowPatch((centers_3d[2][0]-8, centers_3d[2][1]-4), (centers_3d[1][0]-8, centers_3d[1][1]+4),
                                 arrowstyle='-|>', mutation_scale=12, linewidth=2.2, color='#7C3AED', zorder=45))
    ax.add_patch(FancyArrowPatch((centers_3d[1][0]-8, centers_3d[1][1]-4), (centers_3d[0][0]-8, centers_3d[0][1]+4),
                                 arrowstyle='-|>', mutation_scale=11, linewidth=1.8, color='#0D9488', zorder=45))

    # Upward
    ax.add_patch(FancyArrowPatch((centers_3d[2][0]+8, centers_3d[2][1]+4), (centers_3d[3][0]+8, centers_3d[3][1]-4),
                                 arrowstyle='-|>', mutation_scale=12, linewidth=2.2, linestyle='--', color='#059669', zorder=45))
    ax.add_patch(FancyArrowPatch((centers_3d[1][0]+8, centers_3d[1][1]+4), (centers_3d[2][0]+8, centers_3d[2][1]-4),
                                 arrowstyle='-|>', mutation_scale=12, linewidth=2.2, linestyle='--', color='#059669', zorder=45))
    ax.add_patch(FancyArrowPatch((centers_3d[0][0]+8, centers_3d[0][1]+4), (centers_3d[1][0]+8, centers_3d[1][1]-4),
                                 arrowstyle='-|>', mutation_scale=11, linewidth=1.8, linestyle='--', color='#059669', zorder=45))

    # Forbidden Bypass Arrow on Right Side
    bypass_start = project(base_x + W + 2, 6, z_layers[3])
    bypass_end = project(base_x + W + 2, 6, z_layers[0])
    ax.add_patch(FancyArrowPatch(bypass_start, bypass_end, connectionstyle="arc3,rad=-0.42",
                                 arrowstyle='-|>', mutation_scale=13, linewidth=2.0,
                                 linestyle=':', color='#E11D48', zorder=45))
    mid_bp_x = (bypass_start[0] + bypass_end[0]) / 2 + 6.2
    mid_bp_y = (bypass_start[1] + bypass_end[1]) / 2
    cross_b = FancyBboxPatch((mid_bp_x - 6.2, mid_bp_y - 3.8), 12.4, 7.6, boxstyle="round,pad=0.2,rounding_size=0.6",
                             facecolor='#FFE4E6', edgecolor='#F43F5E', linewidth=1.1, zorder=47)
    ax.add_patch(cross_b)
    ax.text(mid_bp_x, mid_bp_y + 1.2, "[X] 严禁旁路", ha='center', va='center',
            fontsize=7.2, fontweight='bold', color='#E11D48', zorder=48)
    ax.text(mid_bp_x, mid_bp_y - 1.8, "状态机不可越级", ha='center', va='center',
            fontsize=6.2, color='#BE123C', zorder=48)


    # -------------------------------------------------------------
    # Right Side: Detailed Logical Architecture & State Space (x: 84 to 156)
    # -------------------------------------------------------------
    sub_title_detail = FancyBboxPatch((84, 97.5), 72, 5.2, boxstyle="round,pad=0.2,rounding_size=0.6",
                                     facecolor='#FAF5FF', edgecolor='#C084FC', linewidth=1.0, zorder=3)
    ax.add_patch(sub_title_detail)
    ax.text(120, 100.1, "【逻辑结构与状态演化详解】单步指令与反馈原路闭环", ha='center', va='center',
            fontsize=9.5, fontweight='bold', color='#6B21A8', zorder=4)

    # 1. Detail Tier 1: Workflow Node
    d_tier1 = FancyBboxPatch((86, 77), 68, 18, boxstyle="round,pad=0.4,rounding_size=1.0",
                             facecolor='#EFF6FF', edgecolor='#3B82F6', linewidth=1.4, zorder=3)
    ax.add_patch(d_tier1)
    ax.text(120, 92.2, "① 工作流执行节点 (Workflow DEV_NODE)", ha='center', va='center',
            fontsize=10.0, fontweight='bold', color='#1D4ED8', zorder=4)
    ax.text(120, 88.8, "• 绑定声明: 声明设备类型及操作能力 <Reactor, Heat>，配置工艺目标设定值", ha='center', va='center',
            fontsize=7.2, color='#1E40AF', zorder=4)
    ax.text(120, 85.8, "• 节点生命周期推进: PENDING  ──[上升沿激活]──>  RUNNING  ──[状态收敛]──>  SUCCEEDED", ha='center', va='center',
            fontsize=7.2, fontweight='bold', color='#0284C7', zorder=4)
    ax.text(120, 82.8, "• 信号出入: OUT接口 EMIT WF_START(携带参数) ； IN接口等待 CMD_STATE 推动终态", ha='center', va='center',
            fontsize=7.2, color='#334155', zorder=4)
    ax.text(120, 79.8, "• 数据端口拉取: 上游终态 PORT_OUT ──[单向拉取]──> 当前节点内部变量，源端严格冻结", ha='center', va='center',
            fontsize=7.0, color='#64748B', zorder=4)

    # 2. Detail Tier 2: Device State Machine
    d_tier2 = FancyBboxPatch((86, 45), 68, 27, boxstyle="round,pad=0.4,rounding_size=1.0",
                             facecolor='#FAF5FF', edgecolor='#9333EA', linewidth=1.4, zorder=3)
    ax.add_patch(d_tier2)
    ax.text(120, 69.5, "② 设备状态机 (Device State Machine) · 【★发令必经之路】", ha='center', va='center',
            fontsize=10.0, fontweight='bold', color='#6B21A8', zorder=4)
    ax.text(120, 66.5, "【双状态空间正交解耦架构】硬件控制权独占排队 · 实例严格隔离", ha='center', va='center',
            fontsize=7.5, fontweight='bold', color='#7C3AED', zorder=4)
    
    # CMD Space inside detail
    cmd_box = FancyBboxPatch((88, 54.5), 31.5, 9.5, boxstyle="round,pad=0.2,rounding_size=0.5",
                             facecolor='#EDE9FE', edgecolor='#A855F7', linewidth=1.0, zorder=4)
    ax.add_patch(cmd_box)
    ax.text(103.75, 61.8, "【CMD 指令生命周期空间】", ha='center', va='center',
            fontsize=7.5, fontweight='bold', color='#5B21B6', zorder=5)
    ax.text(103.75, 59.0, "IDLE → SENT → RUNNING → COMPLETED", ha='center', va='center',
            fontsize=6.8, fontweight='bold', color='#4C1D95', zorder=5)
    ax.text(103.75, 56.5, "异常: ABORTING → ABORTED / FAILED", ha='center', va='center',
            fontsize=6.5, color='#6D28D9', zorder=5)

    # OP Space inside detail
    op_box = FancyBboxPatch((121.5, 54.5), 31.5, 9.5, boxstyle="round,pad=0.2,rounding_size=0.5",
                            facecolor='#F3E8FF', edgecolor='#C084FC', linewidth=1.0, zorder=4)
    ax.add_patch(op_box)
    ax.text(137.25, 61.8, "【OP 业务与异常空间】", ha='center', va='center',
            fontsize=7.5, fontweight='bold', color='#5B21B6', zorder=5)
    ax.text(137.25, 59.0, "• OPERATIONAL 单值互斥区: IDLE / HEATING", ha='center', va='center',
            fontsize=6.5, color='#4C1D95', zorder=5)
    ax.text(137.25, 56.5, "• EXCEPTION 锁存区: 越限多状态锁存", ha='center', va='center',
            fontsize=6.5, color='#6D28D9', zorder=5)

    ax.text(120, 51.5, "• 附着式中止 (Attached Abort): 普通指令被中止时，派生终止状态机执行独立急停", ha='center', va='center',
            fontsize=7.0, color='#4C1D95', zorder=4)
    ax.text(120, 48.8, "• 终止作用域收敛 (Scope Convergence): 急停完成后其 scope 范围指令强转 ABORTED，防死锁", ha='center', va='center',
            fontsize=7.0, color='#4C1D95', zorder=4)
    ax.text(120, 46.2, "• 状态广播: Interface_state_out 向观测空间与工作流广播 CMD_STATE / OP_STATE", ha='center', va='center',
            fontsize=7.0, color='#4C1D95', zorder=4)

    # 3. Detail Tier 3: Edge Adapter
    d_tier3 = FancyBboxPatch((86, 26), 68, 14.5, boxstyle="round,pad=0.4,rounding_size=1.0",
                             facecolor='#F0FDFA', edgecolor='#0D9488', linewidth=1.4, zorder=3)
    ax.add_patch(d_tier3)
    ax.text(120, 38.2, "③ 边缘适配器层 (Edge Adapter) · 【四层工业标准】", ha='center', va='center',
            fontsize=10.0, fontweight='bold', color='#0F766E', zorder=4)
    ax.text(120, 35.0, "• runtime.py (生命周期/看门狗)  |  northbound.py (MQTT 通信信封解析，接收 commandTopic)", ha='center', va='center',
            fontsize=7.2, color='#115E59', zorder=4)
    ax.text(120, 32.0, "• core.py (核心语义翻译中枢): ★ internal 寄存器参数自动注入，生成物理总线报文", ha='center', va='center',
            fontsize=7.2, fontweight='bold', color='#0D9488', zorder=4)
    ax.text(120, 29.0, "• southbound.py (硬件驱动协议): Modbus-TCP / 串口 / 厂商 SDK 物理通信收发", ha='center', va='center',
            fontsize=7.2, color='#134E4A', zorder=4)

    # 4. Detail Tier 4: Physical Device
    d_tier4 = FancyBboxPatch((86, 8), 68, 14, boxstyle="round,pad=0.4,rounding_size=1.0",
                             facecolor='#F8FAFC', edgecolor='#475569', linewidth=1.4, zorder=3)
    ax.add_patch(d_tier4)
    ax.text(120, 19.5, "④ 物理仪器装备与传感器层 (Physical Equipment)", ha='center', va='center',
            fontsize=10.0, fontweight='bold', color='#1E293B', zorder=4)
    ax.text(120, 16.5, "• 物理执行终端: 夹套加热釜、顶置搅拌器、蠕动加样泵、电磁启闭阀 (严格执行动作)", ha='center', va='center',
            fontsize=7.2, color='#334155', zorder=4)
    ax.text(120, 13.5, "• 物理传感器阵列: Pt100 热电阻、压力变送器、液位传感器、转速编码器 (连续物理采样)", ha='center', va='center',
            fontsize=7.2, color='#334155', zorder=4)
    ax.text(120, 10.5, "• 遥测上报与事件回传: 遥测数据打向 telemetryTopic，动作完成事件回传 eventTopic", ha='center', va='center',
            fontsize=7.0, color='#64748B', zorder=4)

    # Right side Inter-tier Arrows
    # 1 -> 2
    ax.add_patch(FancyArrowPatch((105, 77), (105, 72), arrowstyle='-|>', mutation_scale=12,
                                 linewidth=2.2, color='#2563EB', zorder=5))
    ax.text(103.5, 74.5, "下行: WF_START (能力/参数)", ha='right', va='center',
            fontsize=7.0, fontweight='bold', color='#1D4ED8', zorder=6)

    ax.add_patch(FancyArrowPatch((135, 72), (135, 77), arrowstyle='-|>', mutation_scale=12,
                                 linewidth=2.2, linestyle='--', color='#059669', zorder=5))
    ax.text(136.5, 74.5, "回程: CMD_STATE (推进节点终态)", ha='left', va='center',
            fontsize=7.0, fontweight='bold', color='#047857', zorder=6)

    # 2 -> 3
    ax.add_patch(FancyArrowPatch((105, 45), (105, 40.5), arrowstyle='-|>', mutation_scale=12,
                                 linewidth=2.2, color='#7C3AED', zorder=5))
    ax.text(103.5, 42.8, "下行: CMD_START (messageId)", ha='right', va='center',
            fontsize=7.0, fontweight='bold', color='#6D28D9', zorder=6)

    ax.add_patch(FancyArrowPatch((135, 40.5), (135, 45), arrowstyle='-|>', mutation_scale=12,
                                 linewidth=2.2, linestyle='--', color='#059669', zorder=5))
    ax.text(136.5, 42.8, "回程: event (携带原 messageId)", ha='left', va='center',
            fontsize=7.0, fontweight='bold', color='#047857', zorder=6)

    # 3 -> 4
    ax.add_patch(FancyArrowPatch((105, 26), (105, 22), arrowstyle='-|>', mutation_scale=11,
                                 linewidth=1.8, color='#0D9488', zorder=5))
    ax.text(103.5, 24.0, "控制帧: Modbus/串口", ha='right', va='center',
            fontsize=6.8, color='#0F766E', zorder=6)

    ax.add_patch(FancyArrowPatch((135, 22), (135, 26), arrowstyle='-|>', mutation_scale=11,
                                 linewidth=1.8, linestyle='--', color='#059669', zorder=5))
    ax.text(136.5, 24.0, "传感器: 实时物理采样", ha='left', va='center',
            fontsize=6.8, color='#047857', zorder=6)

    # Bottom Summary Banner
    banner = FancyBboxPatch((10, 2.2), 140, 4.0, boxstyle="round,pad=0.2,rounding_size=0.6",
                            facecolor='#EFF6FF', edgecolor='#BFDBFE', linewidth=1.0, zorder=10)
    ax.add_patch(banner)
    ax.text(80, 4.2, "【纵向铁律】单步执行严禁绕过状态机直连底层驱动 · 指令下发必有 messageId 唯一强绑定 · 物理事件沿原路闭环收敛",
            ha='center', va='center', fontsize=8.2, fontweight='bold', color='#1E40AF', zorder=11)

    out_path = os.path.join(out_dir, "01_纵向概念结构图_单步闭环深度解析.png")
    plt.tight_layout()
    plt.savefig(out_path, dpi=300, facecolor=fig.get_facecolor(), edgecolor='none')
    plt.close()
    print("Saved vertical conceptual structure diagram:", out_path)

if __name__ == "__main__":
    render_vertical_conceptual_structure()
