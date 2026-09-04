import os
import matplotlib.pyplot as plt
import matplotlib.patches as patches
from matplotlib.patches import Polygon, FancyArrowPatch, FancyBboxPatch
import numpy as np

# Set Chinese font
plt.rcParams['font.sans-serif'] = ['Microsoft YaHei', 'SimHei', 'Arial']
plt.rcParams['axes.unicode_minus'] = False

out_dir = r"d:\SmartLab2.0\doc\generated_images"
os.makedirs(out_dir, exist_ok=True)

DEPTH_ANGLE = np.radians(24) # 24 degrees
DEPTH_SCALE = 0.55

def project(x, y, z):
    px = x + y * np.cos(DEPTH_ANGLE) * DEPTH_SCALE
    py = z + y * np.sin(DEPTH_ANGLE) * DEPTH_SCALE
    return px, py

def draw_3d_slab(ax, x0, y0, z0, dx, dy, dz, top_c, front_c, right_c, edge_c, zord=10):
    # Top face
    t1 = project(x0, y0, z0 + dz)
    t2 = project(x0 + dx, y0, z0 + dz)
    t3 = project(x0 + dx, y0 + dy, z0 + dz)
    t4 = project(x0, y0 + dy, z0 + dz)
    top_p = Polygon([t1, t2, t3, t4], closed=True, facecolor=top_c, edgecolor=edge_c, linewidth=1.1, zorder=zord+3)
    ax.add_patch(top_p)

    # Front face
    f1 = project(x0, y0, z0)
    f2 = project(x0 + dx, y0, z0)
    f3 = project(x0 + dx, y0, z0 + dz)
    f4 = project(x0, y0, z0 + dz)
    front_p = Polygon([f1, f2, f3, f4], closed=True, facecolor=front_c, edgecolor=edge_c, linewidth=1.1, zorder=zord+2)
    ax.add_patch(front_p)

    # Right face
    r1 = project(x0 + dx, y0, z0)
    r2 = project(x0 + dx, y0 + dy, z0)
    r3 = project(x0 + dx, y0 + dy, z0 + dz)
    r4 = project(x0 + dx, y0, z0 + dz)
    right_p = Polygon([r1, r2, r3, r4], closed=True, facecolor=right_c, edgecolor=edge_c, linewidth=1.1, zorder=zord+2)
    ax.add_patch(right_p)

    ctx = (t1[0] + t2[0] + t3[0] + t4[0]) / 4
    cty = (t1[1] + t2[1] + t3[1] + t4[1]) / 4
    return ctx, cty

def render_3d_vertical_diagram():
    fig, ax = plt.subplots(figsize=(8.5, 7.5), dpi=300)
    fig.patch.set_facecolor('#F8FAFC')
    ax.set_facecolor('#F8FAFC')
    ax.axis('off')
    ax.set_xlim(-2, 103)
    ax.set_ylim(-2, 102)

    # Outer Card Border
    outer_card = FancyBboxPatch((0.5, 0.5), 99, 99, boxstyle="round,pad=0.5,rounding_size=2.0",
                                facecolor='#FFFFFF', edgecolor='#CBD5E1', linewidth=1.5, zorder=1)
    ax.add_patch(outer_card)

    # Header Titles
    ax.text(50, 96.6, "纵向立体概念结构图 (单步执行闭环)", ha='center', va='center',
            fontsize=15.0, fontweight='bold', color='#0F172A', zorder=50)
    ax.text(50, 93.0, "四层立体空间严格贯通 · 控制必由状态机派发 · 严禁旁路直连 · 反馈原路收敛", ha='center', va='center',
            fontsize=8.2, color='#64748B', zorder=50)

    # Main 3D bounding box geometry
    W = 60
    D = 38
    H = 68
    base_x = 16
    base_z = 13

    # 4 Floors Z-positions
    z_layers = [
        base_z,                     # Layer 4 (Bottom floor)
        base_z + H * 0.333,         # Layer 3 (Lower-mid shelf)
        base_z + H * 0.667,         # Layer 2 (Upper-mid shelf)
        base_z + H                  # Layer 1 (Top roof)
    ]

    # Back Wireframe Edges (Dashed lines, behind everything)
    p_bl_bot = project(base_x, D, base_z)
    p_bl_top = project(base_x, D, base_z + H)
    p_br_bot = project(base_x + W, D, base_z)
    p_br_top = project(base_x + W, D, base_z + H)
    
    ax.plot([p_bl_bot[0], p_bl_top[0]], [p_bl_bot[1], p_bl_top[1]], color='#94A3B8', linestyle='--', linewidth=1.2, zorder=2)
    ax.plot([p_br_bot[0], p_br_top[0]], [p_br_bot[1], p_br_top[1]], color='#94A3B8', linestyle='--', linewidth=1.2, zorder=2)
    ax.plot([p_bl_bot[0], p_br_bot[0]], [p_bl_bot[1], p_br_bot[1]], color='#94A3B8', linestyle='--', linewidth=1.2, zorder=2)

    layer_configs = [
        # Layer 4: Physical
        {
            "name": "第 4 层: 物理设备与传感器层 (Physical Equipment)",
            "desc": "夹套反应釜 · 加热搅拌装置 · 温度/压力实时采样",
            "tag": "Layer 4: 物理层",
            "tag_bg": "#F1F5F9", "tag_txt": "#334155",
            "plane_bg": "#F8FAFC", "plane_edge": "#94A3B8",
            "top_c": "#334155", "front_c": "#1E293B", "right_c": "#0F172A", "edge_c": "#475569",
            "txt_color": "#F8FAFC", "sub_color": "#94A3B8"
        },
        # Layer 3: Adapter
        {
            "name": "第 3 层: 边缘适配器层 (Edge Adapter)",
            "desc": "四层标准架构 (runtime/NB/core/SB) · internal注入 · 协议转换",
            "tag": "Layer 3: 适配层",
            "tag_bg": "#CCFBF1", "tag_txt": "#0F766E",
            "plane_bg": "#F0FDFA", "plane_edge": "#5EEAD4",
            "top_c": "#0D9488", "front_c": "#0F766E", "right_c": "#115E59", "edge_c": "#14B8A6",
            "txt_color": "#FFFFFF", "sub_color": "#CCFBF1"
        },
        # Layer 2: State Machine
        {
            "name": "第 2 层: 设备状态机层 (State Machine)",
            "desc": "★ 发令必经之路 · 独占硬件控制权 · 双状态空间(CMD+OP)",
            "tag": "Layer 2: 状态机",
            "tag_bg": "#EDE9FE", "tag_txt": "#6D28D9",
            "plane_bg": "#FAF5FF", "plane_edge": "#C4B5FD",
            "top_c": "#7C3AED", "front_c": "#6D28D9", "right_c": "#5B21B6", "edge_c": "#8B5CF6",
            "txt_color": "#FFFFFF", "sub_color": "#DDD6FE"
        },
        # Layer 1: Workflow Node
        {
            "name": "第 1 层: 工作流执行层 (Workflow Layer)",
            "desc": "DEV_NODE · 声明能力映射 · 步骤生命周期维护与编排",
            "tag": "Layer 1: 工作流",
            "tag_bg": "#DBEAFE", "tag_txt": "#1D4ED8",
            "plane_bg": "#EFF6FF", "plane_edge": "#93C5FD",
            "top_c": "#2563EB", "front_c": "#1D4ED8", "right_c": "#1E40AF", "edge_c": "#3B82F6",
            "txt_color": "#FFFFFF", "sub_color": "#BFDBFE"
        }
    ]

    centers = []

    # Draw each floor from bottom to top
    for idx, z in enumerate(z_layers):
        cfg = layer_configs[idx]
        zord = 6 + idx * 8

        # 1. 3D horizontal floor plane (shelf)
        p0 = project(base_x, 0, z)
        p1 = project(base_x + W, 0, z)
        p2 = project(base_x + W, D, z)
        p3 = project(base_x, D, z)

        plane_poly = Polygon([p0, p1, p2, p3], closed=True,
                             facecolor=cfg["plane_bg"], edgecolor=cfg["plane_edge"],
                             alpha=0.60, linewidth=1.2, zorder=zord)
        ax.add_patch(plane_poly)

        # Left tag badge
        tag_x = p0[0] - 13.0
        tag_y = p0[1] - 0.2
        tag_poly = FancyBboxPatch((tag_x, tag_y - 2.2), 11.8, 4.4, boxstyle="round,pad=0.2,rounding_size=0.6",
                                  facecolor=cfg["tag_bg"], edgecolor=cfg["tag_txt"], linewidth=0.9, zorder=zord+3)
        ax.add_patch(tag_poly)
        ax.text(tag_x + 5.9, tag_y, cfg["tag"], ha='center', va='center',
                fontsize=6.8, fontweight='bold', color=cfg["tag_txt"], zorder=zord+4)

        # 2. 3D Volumetric Slab on this shelf
        card_w = 48
        card_d = 22
        card_dz = 1.8
        card_x0 = base_x + (W - card_w) / 2
        card_y0 = 8.0 # sits comfortably inside the shelf (y in [8, 30])

        ctx, cty = draw_3d_slab(ax, card_x0, card_y0, z, card_w, card_d, card_dz,
                                cfg["top_c"], cfg["front_c"], cfg["right_c"], cfg["edge_c"], zord=zord)
        centers.append((ctx, cty, z + card_dz))

        # Text on top face of slab
        ax.text(ctx, cty + 1.2, cfg["name"], ha='center', va='center',
                fontsize=8.5, fontweight='bold', color=cfg["txt_color"], zorder=zord+5)
        ax.text(ctx, cty - 1.4, cfg["desc"], ha='center', va='center',
                fontsize=6.8, color=cfg["sub_color"], zorder=zord+5)

    # Front outer wireframe posts & beams of the 3D bounding box
    p_fl_bot = project(base_x, 0, base_z)
    p_fl_top = project(base_x, 0, base_z + H)
    p_fr_bot = project(base_x + W, 0, base_z)
    p_fr_top = project(base_x + W, 0, base_z + H)

    # Two front vertical posts
    ax.plot([p_fl_bot[0], p_fl_top[0]], [p_fl_bot[1], p_fl_top[1]], color='#1E293B', linewidth=2.0, zorder=40)
    ax.plot([p_fr_bot[0], p_fr_top[0]], [p_fr_bot[1], p_fr_top[1]], color='#1E293B', linewidth=2.0, zorder=40)

    # Front horizontal shelf lines across the front face
    for z in z_layers:
        pf_l = project(base_x, 0, z)
        pf_r = project(base_x + W, 0, z)
        ax.plot([pf_l[0], pf_r[0]], [pf_l[1], pf_r[1]], color='#1E293B', linewidth=1.5, zorder=40)
        
        # Right side depth lines at each layer
        pr_f = project(base_x + W, 0, z)
        pr_b = project(base_x + W, D, z)
        ax.plot([pr_f[0], pr_b[0]], [pr_f[1], pr_b[1]], color='#1E293B', linewidth=1.5, zorder=40)

    # Top back and left edges of the roof
    p_top_fl = project(base_x, 0, base_z + H)
    p_top_fr = project(base_x + W, 0, base_z + H)
    p_top_br = project(base_x + W, D, base_z + H)
    p_top_bl = project(base_x, D, base_z + H)
    ax.plot([p_top_fl[0], p_top_bl[0]], [p_top_fl[1], p_top_bl[1]], color='#1E293B', linewidth=1.5, zorder=40)
    ax.plot([p_top_bl[0], p_top_br[0]], [p_top_bl[1], p_top_br[1]], color='#1E293B', linewidth=1.5, zorder=40)

    # Bottom back and left edges of the floor
    p_bot_fl = project(base_x, 0, base_z)
    p_bot_fr = project(base_x + W, 0, base_z)
    p_bot_br = project(base_x + W, D, base_z)
    p_bot_bl = project(base_x, D, base_z)
    ax.plot([p_bot_fl[0], p_bot_bl[0]], [p_bot_fl[1], p_bot_bl[1]], color='#94A3B8', linestyle='--', linewidth=1.0, zorder=3)

    # Vertical Inter-layer Control & Feedback Arrows
    arrow_shifts = [-9.5, 9.5] # left: downward control, right: upward feedback

    # 1. Between Layer 1 (Workflow) and Layer 2 (State Machine)
    # Downward: WF_START
    p_d1_s = (centers[3][0] + arrow_shifts[0], centers[3][1] - 4.5)
    p_d1_e = (centers[2][0] + arrow_shifts[0], centers[2][1] + 4.2)
    ax.add_patch(FancyArrowPatch(p_d1_s, p_d1_e, arrowstyle='-|>', mutation_scale=12,
                                 linewidth=2.2, color='#2563EB', zorder=45))
    ax.text(p_d1_s[0] - 2.0, (p_d1_s[1] + p_d1_e[1])/2, "下行控制\nWF_START",
            ha='right', va='center', fontsize=7.2, fontweight='bold', color='#1D4ED8', zorder=46)

    # Upward: CMD_STATE
    p_u1_s = (centers[2][0] + arrow_shifts[1], centers[2][1] + 4.2)
    p_u1_e = (centers[3][0] + arrow_shifts[1], centers[3][1] - 4.5)
    ax.add_patch(FancyArrowPatch(p_u1_s, p_u1_e, arrowstyle='-|>', mutation_scale=12,
                                 linewidth=2.2, linestyle='--', color='#059669', zorder=45))
    ax.text(p_u1_s[0] + 2.0, (p_u1_s[1] + p_u1_e[1])/2, "状态反馈\nCMD_STATE",
            ha='left', va='center', fontsize=7.2, fontweight='bold', color='#047857', zorder=46)

    # 2. Between Layer 2 (State Machine) and Layer 3 (Adapter)
    # Downward: CMD_START
    p_d2_s = (centers[2][0] + arrow_shifts[0], centers[2][1] - 4.5)
    p_d2_e = (centers[1][0] + arrow_shifts[0], centers[1][1] + 4.2)
    ax.add_patch(FancyArrowPatch(p_d2_s, p_d2_e, arrowstyle='-|>', mutation_scale=12,
                                 linewidth=2.2, color='#7C3AED', zorder=45))
    ax.text(p_d2_s[0] - 2.0, (p_d2_s[1] + p_d2_e[1])/2, "指令下发\nCMD_START",
            ha='right', va='center', fontsize=7.2, fontweight='bold', color='#6D28D9', zorder=46)

    # Upward: event / telemetry
    p_u2_s = (centers[1][0] + arrow_shifts[1], centers[1][1] + 4.2)
    p_u2_e = (centers[2][0] + arrow_shifts[1], centers[2][1] - 4.5)
    ax.add_patch(FancyArrowPatch(p_u2_s, p_u2_e, arrowstyle='-|>', mutation_scale=12,
                                 linewidth=2.2, linestyle='--', color='#059669', zorder=45))
    ax.text(p_u2_s[0] + 2.0, (p_u2_s[1] + p_u2_e[1])/2, "事件回传\nevent/telemetry",
            ha='left', va='center', fontsize=7.2, fontweight='bold', color='#047857', zorder=46)

    # 3. Between Layer 3 (Adapter) and Layer 4 (Physical Device)
    # Downward: Modbus / Serial
    p_d3_s = (centers[1][0] + arrow_shifts[0], centers[1][1] - 4.5)
    p_d3_e = (centers[0][0] + arrow_shifts[0], centers[0][1] + 4.2)
    ax.add_patch(FancyArrowPatch(p_d3_s, p_d3_e, arrowstyle='-|>', mutation_scale=11,
                                 linewidth=1.8, color='#0D9488', zorder=45))
    ax.text(p_d3_s[0] - 2.0, (p_d3_s[1] + p_d3_e[1])/2, "硬件控制帧\nModbus/PLC",
            ha='right', va='center', fontsize=6.8, color='#0F766E', zorder=46)

    # Upward: Sensor sampling
    p_u3_s = (centers[0][0] + arrow_shifts[1], centers[0][1] + 4.2)
    p_u3_e = (centers[1][0] + arrow_shifts[1], centers[1][1] - 4.5)
    ax.add_patch(FancyArrowPatch(p_u3_s, p_u3_e, arrowstyle='-|>', mutation_scale=11,
                                 linewidth=1.8, linestyle='--', color='#059669', zorder=45))
    ax.text(p_u3_s[0] + 2.0, (p_u3_s[1] + p_u3_e[1])/2, "传感器遥测\n物理连续采样",
            ha='left', va='center', fontsize=6.8, color='#047857', zorder=46)

    # Forbidden Bypass Arrow on the Right Side
    bypass_start = project(base_x + W + 2, 8, z_layers[3])
    bypass_end = project(base_x + W + 2, 8, z_layers[0])
    bypass_arrow = FancyArrowPatch(bypass_start, bypass_end, connectionstyle="arc3,rad=-0.40",
                                   arrowstyle='-|>', mutation_scale=13, linewidth=2.0,
                                   linestyle=':', color='#E11D48', zorder=45)
    ax.add_patch(bypass_arrow)
    
    mid_bypass_x = (bypass_start[0] + bypass_end[0]) / 2 + 6.6
    mid_bypass_y = (bypass_start[1] + bypass_end[1]) / 2
    
    cross_badge = FancyBboxPatch((mid_bypass_x - 6.8, mid_bypass_y - 4.0), 13.6, 8.0,
                                 boxstyle="round,pad=0.2,rounding_size=0.8",
                                 facecolor='#FFE4E6', edgecolor='#F43F5E', linewidth=1.2, zorder=47)
    ax.add_patch(cross_badge)
    ax.text(mid_bypass_x, mid_bypass_y + 1.2, "[X] 严禁旁路直连", ha='center', va='center',
            fontsize=7.2, fontweight='bold', color='#E11D48', zorder=48)
    ax.text(mid_bypass_x, mid_bypass_y - 1.8, "状态机不可绕过", ha='center', va='center',
            fontsize=6.2, color='#BE123C', zorder=48)

    # Bottom Banner
    banner = FancyBboxPatch((8, 3.5), 84, 4.8, boxstyle="round,pad=0.2,rounding_size=0.8",
                            facecolor='#EFF6FF', edgecolor='#BFDBFE', linewidth=1.0, zorder=45)
    ax.add_patch(banner)
    ax.text(50, 5.9, "【纵向铁律】四层立体空间严格贯通 · 控制指令必由状态机派发 · 反馈数据原路收敛闭环",
            ha='center', va='center', fontsize=7.8, fontweight='bold', color='#1E40AF', zorder=46)

    out_path = os.path.join(out_dir, "01_纵向概念结构图_3D立体版.png")
    plt.tight_layout()
    plt.savefig(out_path, dpi=300, facecolor=fig.get_facecolor(), edgecolor='none')
    plt.close()
    print("Saved 3D vertical diagram:", out_path)

if __name__ == "__main__":
    render_3d_vertical_diagram()
