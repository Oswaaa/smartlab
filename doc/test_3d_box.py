import os
import matplotlib.pyplot as plt
import matplotlib.patches as patches
from matplotlib.patches import Polygon, FancyBboxPatch, FancyArrowPatch
import numpy as np

# Set Chinese font
plt.rcParams['font.sans-serif'] = ['Microsoft YaHei', 'SimHei', 'Arial']
plt.rcParams['axes.unicode_minus'] = False

out_dir = r"d:\SmartLab2.0\doc\generated_images"
os.makedirs(out_dir, exist_ok=True)

# Define Oblique Projection function
# x: width (0 to 100, left to right)
# y: depth (0 to 100, front to back)
# z: height (0 to 100, bottom to top)
#
# User's sketch has:
# Front-left: (x=0, y=0)
# Front-right: (x=W, y=0)
# Back-left: (x=0, y=D)
# Back-right: (x=W, y=D)
# Shift for depth:
DEPTH_ANGLE = np.radians(24) # 24 degrees up-right
DEPTH_SCALE = 0.55 # foreshortening

def project(x, y, z):
    # px = x + y * cos(angle) * scale
    # py = z + y * sin(angle) * scale
    px = x + y * np.cos(DEPTH_ANGLE) * DEPTH_SCALE
    py = z + y * np.sin(DEPTH_ANGLE) * DEPTH_SCALE
    return px, py

def test_box():
    fig, ax = plt.subplots(figsize=(10, 8), dpi=300)
    fig.patch.set_facecolor('#F8FAFC')
    ax.set_facecolor('#F8FAFC')
    ax.axis('off')

    # Draw the 3D bounding box matching user sketch
    W = 68
    D = 42
    H = 64
    base_x = 12
    base_z = 15

    # 4 Layers Z-levels
    z_levels = [base_z, base_z + H * 0.33, base_z + H * 0.67, base_z + H]
    layer_names = [
        ("第 4 层: 物理设备层 (Physical Equipment)", "物理反应釜 · 加热搅拌装置 · 传感器阵列", "#1E293B", "#475569", "#F8FAFC"),
        ("第 3 层: 边缘适配器层 (Edge Adapter)", "四层架构 (runtime/NB/core/SB) · internal注入", "#0F766E", "#14B8A6", "#FFFFFF"),
        ("第 2 层: 设备状态机层 (State Machine)", "★ 发令必经之路 · 独占硬件控制权 · 双状态空间", "#6D28D9", "#8B5CF6", "#FFFFFF"),
        ("第 1 层: 任务工作流层 (Workflow Layer)", "DEV_NODE · 步骤生命周期维护 · 逻辑编排", "#1E40AF", "#3B82F6", "#FFFFFF")
    ]

    # Back wireframes
    p_bl_bot = project(base_x, D, base_z)
    p_bl_top = project(base_x, D, base_z + H)
    p_br_bot = project(base_x + W, D, base_z)
    p_br_top = project(base_x + W, D, base_z + H)
    
    # Draw back vertical line
    ax.plot([p_bl_bot[0], p_bl_top[0]], [p_bl_bot[1], p_bl_top[1]], color='#CBD5E1', linestyle='--', linewidth=1.2, zorder=1)
    ax.plot([p_br_bot[0], p_br_top[0]], [p_br_bot[1], p_br_top[1]], color='#CBD5E1', linestyle='--', linewidth=1.2, zorder=1)

    # For each layer, draw the 3D horizontal plane (shelf)
    for idx, z in enumerate(z_levels):
        p0 = project(base_x, 0, z)
        p1 = project(base_x + W, 0, z)
        p2 = project(base_x + W, D, z)
        p3 = project(base_x, D, z)
        
        # Draw translucent polygon for shelf
        poly = Polygon([p0, p1, p2, p3], closed=True,
                       facecolor='#E2E8F0', edgecolor='#94A3B8',
                       alpha=0.45, linewidth=1.4, zorder=2 + idx*4)
        ax.add_patch(poly)

    # Front wireframes
    p_fl_bot = project(base_x, 0, base_z)
    p_fl_top = project(base_x, 0, base_z + H)
    p_fr_bot = project(base_x + W, 0, base_z)
    p_fr_top = project(base_x + W, 0, base_z + H)

    ax.plot([p_fl_bot[0], p_fl_top[0]], [p_fl_bot[1], p_fl_top[1]], color='#475569', linewidth=2.0, zorder=20)
    ax.plot([p_fr_bot[0], p_fr_top[0]], [p_fr_bot[1], p_fr_top[1]], color='#475569', linewidth=2.0, zorder=20)

    # Top frame
    p_top_fl = project(base_x, 0, base_z + H)
    p_top_fr = project(base_x + W, 0, base_z + H)
    p_top_br = project(base_x + W, D, base_z + H)
    p_top_bl = project(base_x, D, base_z + H)
    top_poly = Polygon([p_top_fl, p_top_fr, p_top_br, p_top_bl], closed=True,
                       facecolor='none', edgecolor='#334155', linewidth=2.0, zorder=21)
    ax.add_patch(top_poly)

    # Bottom frame
    p_bot_fl = project(base_x, 0, base_z)
    p_bot_fr = project(base_x + W, 0, base_z)
    p_bot_br = project(base_x + W, D, base_z)
    p_bot_bl = project(base_x, D, base_z)
    bot_poly = Polygon([p_bot_fl, p_bot_fr, p_bot_br, p_bot_bl], closed=True,
                       facecolor='none', edgecolor='#334155', linewidth=2.0, zorder=21)
    ax.add_patch(bot_poly)

    ax.set_xlim(0, 110)
    ax.set_ylim(0, 100)
    out_path = os.path.join(out_dir, "test_wireframe.png")
    plt.savefig(out_path, dpi=300)
    plt.close()
    print("Saved wireframe:", out_path)

test_box()
