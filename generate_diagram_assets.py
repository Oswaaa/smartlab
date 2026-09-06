# -*- coding: utf-8 -*-
"""
Generate professional scientific 3D isometric architecture diagram assets
for SmartLab 2.0: SVG, HTML preview, and PPTX presentation.
"""

import math
import os
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE

OUTPUT_DIR = r"d:\SmartLab2.0"
BRAIN_DIR = r"C:\Users\22960\.gemini\antigravity\brain\94222834-5d73-43ff-b3d0-e5ba0ca9535e"

def get_svg_content():
    # Isometric projection parameters
    # Angle theta = 24 deg
    rad = math.radians(24)
    cos_t = round(math.cos(rad), 4)
    sin_t = round(math.sin(rad), 4)
    neg_cos_t = round(-cos_t, 4)
    
    # Origins for shelves: (X_width=480, Y_depth=340)
    x0 = 1060
    y0 = 480
    
    # Layer Z levels
    z_top = 340
    z_mid = 180
    z_base = 20
    
    # Corners helper
    def iso(x, y, z):
        px = x0 + (x - y) * cos_t
        py = y0 + (x + y) * sin_t - z
        return f"{px:.1f},{py:.1f}"

    def slab_paths(z, h, fill_top, stroke_top, fill_left, fill_right):
        # 4 top corners
        t0 = iso(0, 0, z + h)
        t1 = iso(480, 0, z + h)
        t2 = iso(480, 340, z + h)
        t3 = iso(0, 340, z + h)
        
        # bottom corners
        b2 = iso(480, 340, z)
        b3 = iso(0, 340, z)
        b1 = iso(480, 0, z)
        
        # Paths
        top_face = f'<polygon points="{t0} {t1} {t2} {t3}" fill="{fill_top}" stroke="{stroke_top}" stroke-width="1.8" />'
        front_left_face = f'<polygon points="{t3} {t2} {b2} {b3}" fill="{fill_left}" stroke="{stroke_top}" stroke-width="1.2" />'
        front_right_face = f'<polygon points="{t2} {t1} {b1} {b2}" fill="{fill_right}" stroke="{stroke_top}" stroke-width="1.2" />'
        return top_face, front_left_face, front_right_face

    # Slabs
    top_t, top_fl, top_fr = slab_paths(z_top, 14, "url(#topPlateGrad)", "#2E7D32", "#81C784", "#66BB6A")
    mid_t, mid_fl, mid_fr = slab_paths(z_mid, 14, "url(#midPlateGrad)", "#0288D1", "#4FC3F7", "#29B6F6")
    base_t, base_fl, base_fr = slab_paths(z_base, 22, "url(#basePlateGrad)", "#EF6C00", "#FFB74D", "#FFA726")

    # Wireframe outer bounds for cutaway glass cube
    cube_c0 = iso(0, 0, 410)
    cube_c1 = iso(480, 0, 410)
    cube_c2 = iso(480, 340, 410)
    cube_c3 = iso(0, 340, 410)
    
    cube_b0 = iso(0, 0, 0)
    cube_b1 = iso(480, 0, 0)
    cube_b2 = iso(480, 340, 0)
    cube_b3 = iso(0, 340, 0)

    # Vertical side plate on right face (X=480, Y in [30, 310], Z in [20, 390])
    p_t0 = iso(480, 50, 400)
    p_t1 = iso(480, 310, 400)
    p_b1 = iso(480, 310, 30)
    p_b0 = iso(480, 50, 30)

    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1600 940" width="100%" height="100%" style="background:#FFFFFF; font-family:'Segoe UI','Microsoft YaHei',-apple-system,sans-serif;">
  <defs>
    <!-- Gradients -->
    <linearGradient id="bgGrad" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#FFFFFF"/>
      <stop offset="100%" stop-color="#F8FAFC"/>
    </linearGradient>
    <linearGradient id="arrowGrad" x1="0%" y1="100%" x2="0%" y2="0%">
      <stop offset="0%" stop-color="#FFCDD2"/>
      <stop offset="40%" stop-color="#EF9A9A"/>
      <stop offset="100%" stop-color="#E57373"/>
    </linearGradient>
    <linearGradient id="topPlateGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#E8F5E9" stop-opacity="0.9"/>
      <stop offset="100%" stop-color="#C8E6C9" stop-opacity="0.75"/>
    </linearGradient>
    <linearGradient id="midPlateGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#E1F5FE" stop-opacity="0.9"/>
      <stop offset="100%" stop-color="#B3E5FC" stop-opacity="0.75"/>
    </linearGradient>
    <linearGradient id="basePlateGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#FFF3E0" stop-opacity="0.95"/>
      <stop offset="100%" stop-color="#FFE0B2" stop-opacity="0.85"/>
    </linearGradient>
    <linearGradient id="verticalPlateGrad" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#ECEFF1" stop-opacity="0.85"/>
      <stop offset="100%" stop-color="#CFD8DC" stop-opacity="0.6"/>
    </linearGradient>

    <!-- Filters -->
    <filter id="softShadow" x="-10%" y="-10%" width="125%" height="125%">
      <feDropShadow dx="2" dy="5" stdDeviation="6" flood-color="#000000" flood-opacity="0.12"/>
    </filter>
    <filter id="cardShadow" x="-10%" y="-10%" width="125%" height="125%">
      <feDropShadow dx="1" dy="3" stdDeviation="3" flood-color="#000000" flood-opacity="0.08"/>
    </filter>
    <filter id="glowGreen" x="-20%" y="-20%" width="140%" height="140%">
      <feDropShadow dx="0" dy="0" stdDeviation="4" flood-color="#2E7D32" flood-opacity="0.3"/>
    </filter>

    <!-- Markers -->
    <marker id="arrowRed" markerWidth="8" markerHeight="8" refX="4" refY="4" orient="auto">
      <path d="M1,1 L7,4 L1,7 Z" fill="#D32F2F" />
    </marker>
    <marker id="arrowGreen" markerWidth="8" markerHeight="8" refX="4" refY="4" orient="auto">
      <path d="M1,1 L7,4 L1,7 Z" fill="#2E7D32" />
    </marker>
    <marker id="arrowBlue" markerWidth="8" markerHeight="8" refX="4" refY="4" orient="auto">
      <path d="M1,1 L7,4 L1,7 Z" fill="#1976D2" />
    </marker>
    <marker id="dotRed" markerWidth="6" markerHeight="6" refX="3" refY="3">
      <circle cx="3" cy="3" r="2.5" fill="#D32F2F" />
    </marker>
    <marker id="dotBlue" markerWidth="6" markerHeight="6" refX="3" refY="3">
      <circle cx="3" cy="3" r="2.5" fill="#1976D2" />
    </marker>
  </defs>

  <!-- Background -->
  <rect width="1600" height="940" fill="url(#bgGrad)" />

  <!-- ================================================================= -->
  <!-- LEFT COLUMN: DRIVING FORCES, GRAPHS & DOMAIN CHALLENGES           -->
  <!-- ================================================================= -->

  <!-- Giant Upward Arrow -->
  <g filter="url(#softShadow)">
    <path d="M 125,780 L 375,780 L 375,200 L 455,200 L 250,50 L 45,200 L 125,200 Z"
          fill="url(#arrowGrad)" stroke="#E57373" stroke-width="2" />
  </g>

  <!-- Title Badge inside Upward Arrow -->
  <g transform="translate(100, 95)">
    <rect x="0" y="0" width="300" height="42" rx="6" fill="#1B5E20" filter="url(#cardShadow)"/>
    <text x="150" y="26" fill="#FFFFFF" font-size="18" font-weight="bold" text-anchor="middle" letter-spacing="1">
      任务导向可执行建模技术
    </text>
  </g>

  <!-- Middle Graph Container -->
  <g transform="translate(65, 235)" filter="url(#cardShadow)">
    <rect x="0" y="0" width="370" height="235" rx="8" fill="#FFFFFF" stroke="#B0BEC5" stroke-width="1.5" />
    
    <!-- Plot 1: Reactor Temperature Dynamics (T vs t) -->
    <g transform="translate(15, 12)">
      <text x="5" y="14" font-size="11" font-weight="bold" fill="#37474F">反应器升温温控动态响应 (T-t 曲线)</text>
      <!-- Axes -->
      <line x1="25" y1="85" x2="325" y2="85" stroke="#90A4AE" stroke-width="1.2" />
      <line x1="25" y1="20" x2="25" y2="85" stroke="#90A4AE" stroke-width="1.2" />
      <!-- Setpoint dotted line -->
      <line x1="25" y1="35" x2="325" y2="35" stroke="#E53935" stroke-dasharray="4,3" stroke-width="1.2" />
      <text x="328" y="38" font-size="9" fill="#E53935" font-weight="bold">设定温度 T_sp (85°C)</text>
      <!-- Safe Boundary -->
      <line x1="25" y1="23" x2="325" y2="23" stroke="#D32F2F" stroke-dasharray="2,2" stroke-width="1" />
      <text x="328" y="26" font-size="8.5" fill="#D32F2F">安全上限 (BC: 90°C)</text>
      <!-- Temperature Response Curve -->
      <path d="M 25,82 Q 70,82 100,55 T 160,35 Q 210,34 260,35 L 320,35" fill="none" stroke="#D32F2F" stroke-width="2.2" />
      <!-- Heating / Holding annotations -->
      <text x="90" y="76" font-size="8.5" fill="#78909C">升温段</text>
      <text x="210" y="48" font-size="8.5" fill="#78909C">恒温反应段</text>
    </g>

    <!-- Separator -->
    <line x1="15" y1="108" x2="355" y2="108" stroke="#ECEFF1" stroke-width="1.5" />

    <!-- Plot 2: Node State Step Sequence -->
    <g transform="translate(15, 115)">
      <text x="5" y="14" font-size="11" font-weight="bold" fill="#37474F">工作流节点生命周期时序阶梯 (St^node)</text>
      <!-- Axes -->
      <line x1="25" y1="75" x2="325" y2="75" stroke="#90A4AE" stroke-width="1.2" />
      <line x1="25" y1="20" x2="25" y2="75" stroke="#90A4AE" stroke-width="1.2" />
      <text x="20" y="32" font-size="8.5" fill="#78909C" text-anchor="end">Run</text>
      <text x="20" y="52" font-size="8.5" fill="#78909C" text-anchor="end">Ready</text>
      <text x="20" y="72" font-size="8.5" fill="#78909C" text-anchor="end">Init</text>
      <!-- Step wave -->
      <path d="M 25,70 L 60,70 L 60,50 L 100,50 L 100,30 L 220,30 L 220,15 L 290,15 L 290,70 L 325,70" 
            fill="none" stroke="#1E88E5" stroke-width="2" />
      <text x="140" y="42" font-size="8.5" fill="#1565C0" font-weight="bold">Step 2: 反应恒温执行</text>
    </g>

    <!-- Bottom sub-label boxes -->
    <g transform="translate(15, 198)">
      <rect x="0" y="0" width="165" height="26" rx="4" fill="#F1F8E9" stroke="#81C784" stroke-width="1"/>
      <text x="82" y="17" font-size="11" font-weight="bold" fill="#2E7D32" text-anchor="middle">实验任务切片</text>
      
      <rect x="175" y="0" width="165" height="26" rx="4" fill="#EDE7F6" stroke="#B39DDB" stroke-width="1"/>
      <text x="257" y="17" font-size="11" font-weight="bold" fill="#512DA8" text-anchor="middle">动态状态演化</text>
    </g>
  </g>

  <!-- 4 Challenge Cards at Bottom Left -->
  <g transform="translate(65, 485)">
    <!-- Card 1 -->
    <g transform="translate(0, 0)" filter="url(#cardShadow)">
      <rect width="370" height="46" rx="6" fill="#FFFFFF" stroke="#CFD8DC" stroke-width="1.2" />
      <circle cx="24" cy="23" r="10" fill="#E8F5E9" />
      <text x="24" y="27" font-size="10" font-weight="bold" fill="#2E7D32" text-anchor="middle">1</text>
      <text x="46" y="21" font-size="12" font-weight="bold" fill="#263238">设备高度异构性</text>
      <text x="46" y="36" font-size="9.5" fill="#78909C">适配器协议转换与物模型统一标准化封装</text>
    </g>

    <!-- Card 2 -->
    <g transform="translate(0, 56)" filter="url(#cardShadow)">
      <rect width="370" height="46" rx="6" fill="#FFFFFF" stroke="#CFD8DC" stroke-width="1.2" />
      <circle cx="24" cy="23" r="10" fill="#FFEBEE" />
      <text x="24" y="27" font-size="10" font-weight="bold" fill="#C62828" text-anchor="middle">2</text>
      <text x="46" y="21" font-size="12" font-weight="bold" fill="#263238">严苛化学安全边界</text>
      <text x="46" y="36" font-size="9.5" fill="#78909C">全天候基线约束与毫秒级联锁自动熔断机制</text>
    </g>

    <!-- Card 3 -->
    <g transform="translate(0, 112)" filter="url(#cardShadow)">
      <rect width="370" height="46" rx="6" fill="#FFFFFF" stroke="#CFD8DC" stroke-width="1.2" />
      <circle cx="24" cy="23" r="10" fill="#E1F5FE" />
      <text x="24" y="27" font-size="10" font-weight="bold" fill="#0277BD" text-anchor="middle">3</text>
      <text x="46" y="21" font-size="12" font-weight="bold" fill="#263238">动态时空并发依赖</text>
      <text x="46" y="36" font-size="9.5" fill="#78909C">接口控制活边路由与端口数据依赖拓扑拉取</text>
    </g>

    <!-- Card 4 -->
    <g transform="translate(0, 168)" filter="url(#cardShadow)">
      <rect width="370" height="46" rx="6" fill="#FFFFFF" stroke="#CFD8DC" stroke-width="1.2" />
      <circle cx="24" cy="23" r="10" fill="#FFF3E0" />
      <text x="24" y="27" font-size="10" font-weight="bold" fill="#E65100" text-anchor="middle">4</text>
      <text x="46" y="21" font-size="12" font-weight="bold" fill="#263238">形式化解耦与收敛</text>
      <text x="46" y="36" font-size="9.5" fill="#78909C">杜绝脚本式作弊特例代码，全生命周期强闭环</text>
    </g>
  </g>

  <!-- Projection Curves from Left to 3D Layers -->
  <g fill="none" stroke-linecap="round">
    <!-- Top projection -->
    <path d="M 445,280 C 530,260 620,180 770,170" stroke="#2E7D32" stroke-width="2" stroke-dasharray="6,4" marker-end="url(#arrowGreen)"/>
    <g transform="translate(520, 205)" filter="url(#cardShadow)">
      <rect width="135" height="24" rx="12" fill="#FFFFFF" stroke="#81C784" stroke-width="1.2"/>
      <text x="67" y="16" font-size="10" font-weight="bold" fill="#2E7D32" text-anchor="middle">任务拓扑切片映射</text>
    </g>

    <!-- Middle projection -->
    <path d="M 445,390 C 550,380 620,330 785,320" stroke="#0288D1" stroke-width="2" stroke-dasharray="6,4" marker-end="url(#arrowBlue)"/>
    <g transform="translate(535, 340)" filter="url(#cardShadow)">
      <rect width="135" height="24" rx="12" fill="#FFFFFF" stroke="#4FC3F7" stroke-width="1.2"/>
      <text x="67" y="16" font-size="10" font-weight="bold" fill="#0288D1" text-anchor="middle">内存观测与约束求值</text>
    </g>

    <!-- Bottom projection -->
    <path d="M 445,610 C 550,620 640,480 800,470" stroke="#E65100" stroke-width="2" stroke-dasharray="6,4" marker-end="url(#arrowRed)"/>
    <g transform="translate(535, 525)" filter="url(#cardShadow)">
      <rect width="135" height="24" rx="12" fill="#FFFFFF" stroke="#FFB74D" stroke-width="1.2"/>
      <text x="67" y="16" font-size="10" font-weight="bold" fill="#E65100" text-anchor="middle">物模型与复合状态机</text>
    </g>
  </g>


  <!-- ================================================================= -->
  <!-- RIGHT: 3D ISOMETRIC CUTAWAY CUBE (L = <R, F, C, Pi>)              -->
  <!-- ================================================================= -->

  <!-- 3D Glass Box Wireframe Structure (Back walls & Outer Frame) -->
  <g stroke="#90A4AE" stroke-width="1.2" fill="none">
    <!-- Back uprights -->
    <line x1="{cube_b0.split(',')[0]}" y1="{cube_b0.split(',')[1]}" x2="{cube_c0.split(',')[0]}" y2="{cube_c0.split(',')[1]}" stroke-dasharray="4,4" />
    <line x1="{cube_b1.split(',')[0]}" y1="{cube_b1.split(',')[1]}" x2="{cube_c1.split(',')[0]}" y2="{cube_c1.split(',')[1]}" />
    <line x1="{cube_b3.split(',')[0]}" y1="{cube_b3.split(',')[1]}" x2="{cube_c3.split(',')[0]}" y2="{cube_c3.split(',')[1]}" stroke-dasharray="4,4" />
    
    <!-- Top outer frame -->
    <polygon points="{cube_c0} {cube_c1} {cube_c2} {cube_c3}" stroke="#78909C" stroke-width="1.5" />
    <!-- Front upright -->
    <line x1="{cube_b2.split(',')[0]}" y1="{cube_b2.split(',')[1]}" x2="{cube_c2.split(',')[0]}" y2="{cube_c2.split(',')[1]}" stroke="#546E7A" stroke-width="1.8" />
  </g>

  <!-- ================================================================= -->
  <!-- LAYER 3 (BOTTOM): STATIC RESOURCE & EDGE ADAPTER LAYER (R)        -->
  <!-- ================================================================= -->
  <!-- Base Slab -->
  <g filter="url(#softShadow)">
    {base_fl}
    {base_fr}
    {base_t}
  </g>

  <!-- Base Layer Content (Isometric Projection Matrix) -->
  <g transform="matrix({cos_t}, {sin_t}, {neg_cos_t}, {sin_t}, {x0}, {y0 - z_base - 22})">
    <!-- Header Pill -->
    <rect x="30" y="20" width="420" height="36" rx="18" fill="#E65100" />
    <text x="240" y="44" fill="#FFFFFF" font-size="17" font-weight="bold" text-anchor="middle" letter-spacing="1">
      静态资源与边缘驱动层 (Static Resource Layer - R)
    </text>

    <!-- Sub tags -->
    <g transform="translate(30, 68)">
      <rect x="0" y="0" width="90" height="24" rx="4" fill="#FF8F00"/>
      <text x="45" y="16" fill="#FFFFFF" font-size="11" font-weight="bold" text-anchor="middle">内禀边界</text>
      <rect x="100" y="0" width="90" height="24" rx="4" fill="#FF8F00"/>
      <text x="145" y="16" fill="#FFFFFF" font-size="11" font-weight="bold" text-anchor="middle">指令生命周期</text>
      <rect x="200" y="0" width="90" height="24" rx="4" fill="#FF8F00"/>
      <text x="245" y="16" fill="#FFFFFF" font-size="11" font-weight="bold" text-anchor="middle">物理运行态</text>
      <rect x="300" y="0" width="120" height="24" rx="4" fill="#FF6F00"/>
      <text x="360" y="16" fill="#FFFFFF" font-size="11" font-weight="bold" text-anchor="middle">Adapter 4层契约</text>
    </g>

    <!-- Dual State Machine Card -->
    <g transform="translate(30, 105)">
      <rect width="210" height="150" rx="8" fill="#FFFFFF" stroke="#FFA726" stroke-width="1.5" />
      <text x="105" y="22" font-size="12" font-weight="bold" fill="#E65100" text-anchor="middle">
        设备复合状态机 (St = St^cmd × St^op)
      </text>
      <!-- CMD State Circle -->
      <circle cx="65" cy="80" r="40" fill="#FFF3E0" stroke="#FF9800" stroke-width="1.8" />
      <text x="65" y="75" font-size="10" font-weight="bold" fill="#E65100" text-anchor="middle">St^cmd</text>
      <text x="65" y="90" font-size="8" fill="#78909C" text-anchor="middle">Init/Exec/Done</text>
      <!-- OP State Circle -->
      <circle cx="145" cy="80" r="40" fill="#E8F5E9" stroke="#4CAF50" stroke-width="1.8" />
      <text x="145" y="75" font-size="10" font-weight="bold" fill="#2E7D32" text-anchor="middle">St^op</text>
      <text x="145" y="90" font-size="8" fill="#78909C" text-anchor="middle">Idle/Op/Except</text>
      <!-- Cross intersection label -->
      <text x="105" y="140" font-size="9" font-weight="bold" fill="#37474F" text-anchor="middle">
        解耦隔离 · 状态自洽
      </text>
    </g>

    <!-- Hardware & Edge Adapter Card -->
    <g transform="translate(250, 105)">
      <rect width="200" height="150" rx="8" fill="#FFFFFF" stroke="#FFA726" stroke-width="1.5" />
      <text x="100" y="22" font-size="12" font-weight="bold" fill="#E65100" text-anchor="middle">
        边缘适配驱动与物理硬件池
      </text>
      <!-- Adapter 4 layers mini bar -->
      <g transform="translate(15, 35)">
        <rect x="0" y="0" width="170" height="18" rx="3" fill="#FFE0B2" stroke="#FFB74D"/>
        <text x="85" y="13" font-size="8.5" font-weight="bold" fill="#D84315" text-anchor="middle">
          Runtime | North | Core | South
        </text>
      </g>
      <!-- Instruments list -->
      <g transform="translate(15, 62)">
        <rect x="0" y="0" width="80" height="32" rx="4" fill="#F5F5F5" stroke="#BDBDBD"/>
        <text x="40" y="18" font-size="9" font-weight="bold" fill="#424242" text-anchor="middle">恒温反应釜</text>
        <text x="40" y="28" font-size="7" fill="#757575" text-anchor="middle">Reactor</text>

        <rect x="90" y="0" width="80" height="32" rx="4" fill="#F5F5F5" stroke="#BDBDBD"/>
        <text x="130" y="18" font-size="9" font-weight="bold" fill="#424242" text-anchor="middle">精密温控仪</text>
        <text x="130" y="28" font-size="7" fill="#757575" text-anchor="middle">TCU / Modbus</text>

        <rect x="0" y="38" width="80" height="32" rx="4" fill="#F5F5F5" stroke="#BDBDBD"/>
        <text x="40" y="56" font-size="9" font-weight="bold" fill="#424242" text-anchor="middle">高精度蠕动泵</text>
        <text x="40" y="66" font-size="7" fill="#757575" text-anchor="middle">Dosing Pump</text>

        <rect x="90" y="38" width="80" height="32" rx="4" fill="#F5F5F5" stroke="#BDBDBD"/>
        <text x="130" y="56" font-size="9" font-weight="bold" fill="#424242" text-anchor="middle">在线传感器/PLC</text>
        <text x="130" y="66" font-size="7" fill="#757575" text-anchor="middle">pH / Pressure</text>
      </g>
    </g>

    <!-- Bottom Baseline Bar -->
    <g transform="translate(30, 270)">
      <rect width="420" height="42" rx="6" fill="#FFF8E1" stroke="#FFCA28" stroke-width="1.5" />
      <text x="210" y="26" font-size="12" font-weight="bold" fill="#FF6F00" text-anchor="middle">
        底座基石：软实时、高可靠、无作弊代码的数字物理执行环境
      </text>
    </g>
  </g>

  <!-- Vertical Dispatches between Base and Mid Layer -->
  <g transform="translate(830, 480)">
    <!-- Upward state arrow -->
    <path d="M 0,30 L 0,-35" stroke="#2E7D32" stroke-width="3" marker-end="url(#arrowGreen)"/>
    <text x="10" y="5" font-size="10" font-weight="bold" fill="#2E7D32">状态透传 (Obs_R)</text>

    <!-- Downward interlock abort arrow -->
    <path d="M 55,-35 L 55,30" stroke="#D32F2F" stroke-width="3" marker-end="url(#arrowRed)"/>
    <text x="65" y="5" font-size="10" font-weight="bold" fill="#D32F2F">联锁熔断 (Abort)</text>
  </g>


  <!-- ================================================================= -->
  <!-- LAYER 2 (MIDDLE): OBSERVATION & CONSTRAINT ENGINE LAYER (C)       -->
  <!-- ================================================================= -->
  <!-- Middle Slab -->
  <g filter="url(#softShadow)">
    {mid_fl}
    {mid_fr}
    {mid_t}
  </g>

  <!-- Middle Layer Content -->
  <g transform="matrix({cos_t}, {sin_t}, {neg_cos_t}, {sin_t}, {x0}, {y0 - z_mid - 14})">
    <!-- Header Pill -->
    <rect x="30" y="20" width="420" height="36" rx="18" fill="#0288D1" />
    <text x="240" y="44" fill="#FFFFFF" font-size="17" font-weight="bold" text-anchor="middle" letter-spacing="1">
      统一观测空间与安全约束引擎 (Obs &amp; Constraint - C)
    </text>

    <!-- Sub tags -->
    <g transform="translate(30, 68)">
      <rect x="0" y="0" width="115" height="24" rx="4" fill="#0277BD"/>
      <text x="57" y="16" fill="#FFFFFF" font-size="11" font-weight="bold" text-anchor="middle">滑动时序窗口</text>
      <rect x="125" y="0" width="115" height="24" rx="4" fill="#0277BD"/>
      <text x="182" y="16" fill="#FFFFFF" font-size="11" font-weight="bold" text-anchor="middle">动态约束求值</text>
      <rect x="250" y="0" width="170" height="24" rx="4" fill="#01579B"/>
      <text x="335" y="16" fill="#FFFFFF" font-size="11" font-weight="bold" text-anchor="middle">分级处置 (Warn/Stop/Abort)</text>
    </g>

    <!-- 4 Capsule Cards -->
    <g transform="translate(30, 105)">
      <!-- Card 1: Memory Snapshot -->
      <g transform="translate(0, 0)">
        <rect width="195" height="75" rx="6" fill="#FFFFFF" stroke="#4FC3F7" stroke-width="1.5" />
        <rect x="8" y="8" width="4" height="59" rx="2" fill="#0288D1"/>
        <text x="20" y="28" font-size="11.5" font-weight="bold" fill="#01579B">权威内存快照 (Obs)</text>
        <text x="20" y="46" font-size="9" fill="#546E7A">ConcurrentHashMap 存储</text>
        <text x="20" y="62" font-size="9" fill="#546E7A">纳秒级只读 · 隔离数据库直连</text>
      </g>

      <!-- Card 2: Time-series OHS -->
      <g transform="translate(215, 0)">
        <rect width="205" height="75" rx="6" fill="#FFFFFF" stroke="#4FC3F7" stroke-width="1.5" />
        <rect x="8" y="8" width="4" height="59" rx="2" fill="#00ACC1"/>
        <text x="20" y="28" font-size="11.5" font-weight="bold" fill="#00838F">有界时序历史 (OHS)</text>
        <text x="20" y="46" font-size="9" fill="#546E7A">滑动窗口增量计算: Δ / avg / rate</text>
        <text x="20" y="62" font-size="9" fill="#546E7A">200ms 周期求值 + 事件驱动</text>
      </g>

      <!-- Card 3: Dual Boundaries -->
      <g transform="translate(0, 88)">
        <rect width="195" height="75" rx="6" fill="#FFFFFF" stroke="#4FC3F7" stroke-width="1.5" />
        <rect x="8" y="8" width="4" height="59" rx="2" fill="#FB8C00"/>
        <text x="20" y="28" font-size="11.5" font-weight="bold" fill="#E65100">双层边界隔离体系</text>
        <text x="20" y="46" font-size="9" fill="#546E7A">全局底线基线约束 (BC)</text>
        <text x="20" y="62" font-size="9" fill="#546E7A">任务专属阶段约束 (TC_τ)</text>
      </g>

      <!-- Card 4: Safety Interlock Engine -->
      <g transform="translate(215, 88)">
        <rect width="205" height="75" rx="6" fill="#FFFFFF" stroke="#4FC3F7" stroke-width="1.5" />
        <rect x="8" y="8" width="4" height="59" rx="2" fill="#D32F2F"/>
        <text x="20" y="28" font-size="11.5" font-weight="bold" fill="#C62828">纯逻辑旁路判定引擎</text>
        <text x="20" y="46" font-size="9" fill="#546E7A">SpEL 表达式解析与求值</text>
        <text x="20" y="62" font-size="9" fill="#546E7A">四级处置响应: 告警/暂停/急停</text>
      </g>
    </g>

    <!-- Bottom summary line -->
    <g transform="translate(30, 280)">
      <rect width="420" height="34" rx="5" fill="#E0F7FA" stroke="#80DEEA"/>
      <text x="210" y="22" font-size="11.5" font-weight="bold" fill="#006064" text-anchor="middle">
        核心价值：将安全底线与业务执行彻底解耦，保障多设备协同不炸机
      </text>
    </g>
  </g>

  <!-- Vertical Dispatches between Top and Mid Layer -->
  <g transform="translate(830, 290)">
    <!-- Downward control arrow -->
    <path d="M 0,-30 L 0,35" stroke="#D32F2F" stroke-width="3" marker-end="url(#arrowRed)"/>
    <text x="10" y="5" font-size="10" font-weight="bold" fill="#D32F2F">【提示/下发】任务指令</text>

    <!-- Upward feedback arrow -->
    <path d="M 60,35 L 60,-30" stroke="#2E7D32" stroke-width="3" marker-end="url(#arrowGreen)"/>
    <text x="70" y="5" font-size="10" font-weight="bold" fill="#2E7D32">【促进】执行状态驱动</text>
  </g>


  <!-- ================================================================= -->
  <!-- LAYER 1 (TOP): DYNAMIC WORKFLOW LAYER (F)                         -->
  <!-- ================================================================= -->
  <!-- Top Slab -->
  <g filter="url(#softShadow)">
    {top_fl}
    {top_fr}
    {top_t}
  </g>

  <!-- Top Layer Content -->
  <g transform="matrix({cos_t}, {sin_t}, {neg_cos_t}, {sin_t}, {x0}, {y0 - z_top - 14})">
    <!-- Header Pill -->
    <rect x="30" y="20" width="420" height="36" rx="18" fill="#2E7D32" />
    <text x="240" y="44" fill="#FFFFFF" font-size="17" font-weight="bold" text-anchor="middle" letter-spacing="1">
      动态工作流层 (Dynamic Workflow Layer - F)
    </text>

    <!-- Sub tags -->
    <g transform="translate(30, 68)">
      <rect x="0" y="0" width="95" height="24" rx="4" fill="#388E3C"/>
      <text x="47" y="16" fill="#FFFFFF" font-size="11" font-weight="bold" text-anchor="middle">节点映射 Map</text>
      <rect x="105" y="0" width="115" height="24" rx="4" fill="#388E3C"/>
      <text x="162" y="16" fill="#FFFFFF" font-size="11" font-weight="bold" text-anchor="middle">控制接口活边 E_I</text>
      <rect x="230" y="0" width="115" height="24" rx="4" fill="#388E3C"/>
      <text x="287" y="16" fill="#FFFFFF" font-size="11" font-weight="bold" text-anchor="middle">数据端口活边 E_P</text>
      <rect x="355" y="0" width="65" height="24" rx="4" fill="#1B5E20"/>
      <text x="387" y="16" fill="#FFFFFF" font-size="11" font-weight="bold" text-anchor="middle">100ms 轮询</text>
    </g>

    <!-- Workflow Execution Pipeline -->
    <g transform="translate(30, 105)">
      <rect width="420" height="155" rx="8" fill="#FFFFFF" stroke="#81C784" stroke-width="1.5" />
      <text x="210" y="22" font-size="12" font-weight="bold" fill="#2E7D32" text-anchor="middle">
        实验任务执行切片模型 (Task-Specific Execution Slice)
      </text>

      <!-- 4 Workflow Nodes -->
      <!-- Node 1 -->
      <g transform="translate(15, 42)">
        <rect width="85" height="65" rx="6" fill="#F1F8E9" stroke="#689F38" stroke-width="1.2"/>
        <text x="42" y="22" font-size="10" font-weight="bold" fill="#33691E" text-anchor="middle">Node 1: 进样</text>
        <text x="42" y="38" font-size="8" fill="#558B2F" text-anchor="middle">蠕动泵加料</text>
        <rect x="12" y="46" width="60" height="14" rx="3" fill="#C8E6C9"/>
        <text x="42" y="56" font-size="7.5" font-weight="bold" fill="#1B5E20" text-anchor="middle">Completed</text>
      </g>

      <!-- Connection 1 -> 2 -->
      <line x1="102" y1="74" x2="118" y2="74" stroke="#D32F2F" stroke-width="2" marker-end="url(#arrowRed)" />
      <text x="110" y="68" font-size="7.5" fill="#D32F2F" text-anchor="middle">E_I</text>

      <!-- Node 2 (Active/Heating) -->
      <g transform="translate(120, 38)" filter="url(#glowGreen)">
        <rect width="95" height="73" rx="6" fill="#E8F5E9" stroke="#2E7D32" stroke-width="2"/>
        <text x="47" y="22" font-size="10.5" font-weight="bold" fill="#1B5E20" text-anchor="middle">Node 2: 温控反应</text>
        <text x="47" y="38" font-size="8" fill="#2E7D32" text-anchor="middle">反应釜 85°C</text>
        <rect x="15" y="48" width="65" height="18" rx="4" fill="#4CAF50"/>
        <text x="47" y="61" font-size="8.5" font-weight="bold" fill="#FFFFFF" text-anchor="middle">RUNNING</text>
      </g>

      <!-- Connection 2 -> 3 (Control & Data) -->
      <line x1="217" y1="68" x2="233" y2="68" stroke="#D32F2F" stroke-width="2" marker-end="url(#arrowRed)" />
      <path d="M 217,82 Q 225,92 233,82" fill="none" stroke="#1976D2" stroke-width="1.8" stroke-dasharray="3,2" />
      <text x="225" y="62" font-size="7" fill="#D32F2F" text-anchor="middle">E_I</text>
      <text x="225" y="100" font-size="7" fill="#1976D2" text-anchor="middle">E_P</text>

      <!-- Node 3 -->
      <g transform="translate(235, 42)">
        <rect width="85" height="65" rx="6" fill="#FAFAFA" stroke="#BDBDBD" stroke-width="1.2"/>
        <text x="42" y="22" font-size="10" font-weight="bold" fill="#424242" text-anchor="middle">Node 3: 监测</text>
        <text x="42" y="38" font-size="8" fill="#757575" text-anchor="middle">光谱在线表征</text>
        <rect x="12" y="46" width="60" height="14" rx="3" fill="#EEEEEE"/>
        <text x="42" y="56" font-size="7.5" fill="#757575" text-anchor="middle">Pending</text>
      </g>

      <!-- Connection 3 -> 4 -->
      <line x1="322" y1="74" x2="338" y2="74" stroke="#9E9E9E" stroke-width="1.5" stroke-dasharray="3,2" />

      <!-- Node 4 -->
      <g transform="translate(340, 42)">
        <rect width="65" height="65" rx="6" fill="#FAFAFA" stroke="#BDBDBD" stroke-width="1.2"/>
        <text x="32" y="22" font-size="10" font-weight="bold" fill="#424242" text-anchor="middle">Node 4</text>
        <text x="32" y="38" font-size="8" fill="#757575" text-anchor="middle">出料收集</text>
        <rect x="8" y="46" width="50" height="14" rx="3" fill="#EEEEEE"/>
        <text x="32" y="56" font-size="7.5" fill="#757575" text-anchor="middle">Pending</text>
      </g>

      <!-- Bottom Legend in Workflow -->
      <g transform="translate(20, 126)">
        <line x1="0" y1="10" x2="25" y2="10" stroke="#D32F2F" stroke-width="2"/>
        <text x="30" y="13" font-size="8.5" fill="#455A64">控制接口活边 (Interface Connection E_I: 信号路由)</text>
        <line x1="210" y1="10" x2="235" y2="10" stroke="#1976D2" stroke-width="1.8" stroke-dasharray="3,2"/>
        <text x="240" y="13" font-size="8.5" fill="#455A64">数据端口活边 (Port Connection E_P: 依赖拓扑)</text>
      </g>
    </g>

    <!-- Bottom summary tag -->
    <g transform="translate(30, 275)">
      <rect width="420" height="36" rx="5" fill="#DCEDC8" stroke="#AED581"/>
      <text x="210" y="23" font-size="11.5" font-weight="bold" fill="#33691E" text-anchor="middle">
        任务语义：将节点视为设备能力的具象映射，携带显式状态与数据依赖
      </text>
    </g>
  </g>


  <!-- ================================================================= -->
  <!-- RIGHT CUTAWAY WALL: CROSS-LAYER PROTOCOL & INTERLOCK PIPELINE (Pi) -->
  <!-- ================================================================= -->
  <g filter="url(#softShadow)">
    <polygon points="{p_t0} {p_t1} {p_b1} {p_b0}" 
             fill="url(#verticalPlateGrad)" stroke="#455A64" stroke-width="1.8" />
  </g>

  <!-- Right vertical wall text & badges (Isometric projection onto X=480 face) -->
  <!-- Matrix for X=480 vertical face: local coordinates (y, z) -->
  <!-- a = -cos_t, b = sin_t, c = 0, d = -1, e = x0 + 480*cos_t, f = y0 + 480*sin_t -->
  <g transform="matrix({neg_cos_t}, {sin_t}, 0, -1, {x0 + 480*cos_t:.1f}, {y0 + 480*sin_t:.1f})">
    <!-- Header vertically down -->
    <rect x="70" y="30" width="220" height="340" rx="6" fill="#FFFFFF" fill-opacity="0.9" stroke="#78909C" stroke-width="1.2" />
    
    <g transform="translate(85, 50)">
      <rect width="190" height="36" rx="4" fill="#37474F" />
      <text x="95" y="24" fill="#FFFFFF" font-size="12" font-weight="bold" text-anchor="middle" letter-spacing="1">
        全链路契约体系 (Π)
      </text>
      
      <!-- Items on vertical plate -->
      <g transform="translate(10, 52)">
        <circle cx="10" cy="10" r="5" fill="#0288D1"/>
        <text x="25" y="14" font-size="10.5" font-weight="bold" fill="#263238">MQTT 统一报文契约</text>
        <text x="25" y="28" font-size="8.5" fill="#546E7A">JSON / INI 接口自描述协议</text>
      </g>

      <g transform="translate(10, 102)">
        <circle cx="10" cy="10" r="5" fill="#E65100"/>
        <text x="25" y="14" font-size="10.5" font-weight="bold" fill="#263238">messageId 强追踪闭环</text>
        <text x="25" y="28" font-size="8.5" fill="#546E7A">指令-执行-反馈全链路溯源</text>
      </g>

      <g transform="translate(10, 152)">
        <circle cx="10" cy="10" r="5" fill="#D32F2F"/>
        <text x="25" y="14" font-size="10.5" font-weight="bold" fill="#263238">软实时安全联锁旁路</text>
        <text x="25" y="28" font-size="8.5" fill="#546E7A">旁路越级制动，直达底层设备</text>
      </g>

      <g transform="translate(10, 202)">
        <circle cx="10" cy="10" r="5" fill="#2E7D32"/>
        <text x="25" y="14" font-size="10.5" font-weight="bold" fill="#263238">数字孪生同步时钟</text>
        <text x="25" y="28" font-size="8.5" fill="#546E7A">100ms/200ms 双周期节拍</text>
      </g>
    </g>
  </g>


  <!-- ================================================================= -->
  <!-- BOTTOM CAPTION & FIGURE TITLE                                     -->
  <!-- ================================================================= -->
  <g transform="translate(800, 910)">
    <text x="0" y="0" font-size="16" font-weight="bold" fill="#263238" text-anchor="middle" letter-spacing="0.5">
      图 1.1 面向智能化学实验室的任务导向可执行建模架构拓扑与动态映射机制
    </text>
    <text x="0" y="20" font-size="11.5" fill="#546E7A" text-anchor="middle">
      Figure 1.1: Architecture topology and dynamic mapping mechanism of the task-oriented executable modeling framework for smart chemical laboratories
    </text>
  </g>

</svg>
'''
    return svg

def generate_assets():
    svg_content = get_svg_content()
    
    # 1. Write SVG file to project root and brain
    svg_path_proj = os.path.join(OUTPUT_DIR, "smartlab_3d_architecture.svg")
    svg_path_brain = os.path.join(BRAIN_DIR, "smartlab_3d_architecture.svg")
    
    with open(svg_path_proj, "w", encoding="utf-8") as f:
        f.write(svg_content)
    with open(svg_path_brain, "w", encoding="utf-8") as f:
        f.write(svg_content)
    print(f"Generated SVG: {svg_path_proj}")

    # 2. Write HTML interactive viewer
    html_content = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <title>SmartLab 2.0 - 3D 架构图交互预览</title>
  <style>
    body {{
      margin: 0;
      padding: 20px;
      background: #0F172A;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      color: #E2E8F0;
      display: flex;
      flex-direction: column;
      align-items: center;
    }}
    .header {{
      text-align: center;
      margin-bottom: 20px;
    }}
    .header h1 {{
      font-size: 24px;
      margin: 0 0 8px 0;
      color: #38BDF8;
    }}
    .header p {{
      margin: 0;
      color: #94A3B8;
      font-size: 14px;
    }}
    .card {{
      background: #FFFFFF;
      border-radius: 12px;
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5), 0 8px 10px -6px rgba(0, 0, 0, 0.5);
      padding: 16px;
      max-width: 1400px;
      width: 100%;
      box-sizing: border-box;
    }}
    .toolbar {{
      margin-top: 16px;
      display: flex;
      gap: 12px;
      justify-content: center;
    }}
    button {{
      background: #2563EB;
      color: white;
      border: none;
      padding: 8px 16px;
      border-radius: 6px;
      font-weight: 500;
      cursor: pointer;
      font-size: 13px;
      transition: background 0.2s;
    }}
    button:hover {{
      background: #1D4ED8;
    }}
  </style>
</head>
<body>
  <div class="header">
    <h1>SmartLab 2.0 任务导向可执行建模三维立体架构图</h1>
    <p>基于数学严格等轴测投影构建的矢量架构图 · 支持无限放大、分层解构与 PPT 矢量导入</p>
  </div>
  <div class="card">
    {svg_content}
  </div>
  <div class="toolbar">
    <button onclick="window.print()">打印 / 导出为 PDF</button>
  </div>
</body>
</html>
"""
    html_path = os.path.join(OUTPUT_DIR, "preview_architecture.html")
    with open(html_path, "w", encoding="utf-8") as f:
        f.write(html_content)
    print(f"Generated HTML preview: {html_path}")

    # 3. Create PPTX Presentation with editable layout & shapes
    prs = Presentation()
    prs.slide_width = Inches(13.333)  # 16:9 widescreen
    prs.slide_height = Inches(7.5)
    blank_layout = prs.slide_layouts[6]

    # Slide 1: Reference Render + Overview
    slide1 = prs.slides.add_slide(blank_layout)
    
    # Title
    tb = slide1.shapes.add_textbox(Inches(0.8), Inches(0.4), Inches(11.7), Inches(0.8))
    tf = tb.text_frame
    p = tf.paragraphs[0]
    p.text = "SmartLab 2.0: 任务导向可执行建模三维架构图 (概念原型)"
    p.font.size = Pt(22)
    p.font.bold = True
    p.font.color.rgb = RGBColor(33, 33, 33)

    # Insert rendered image if available
    img_render = os.path.join(BRAIN_DIR, "smartlab_3d_architecture_1788671259716.jpg")
    if os.path.exists(img_render):
        slide1.shapes.add_picture(img_render, Inches(0.8), Inches(1.3), width=Inches(10.5))

    # Slide 2: Editable Decomposition Slide (Ready for customization)
    slide2 = prs.slides.add_slide(blank_layout)
    
    tb2 = slide2.shapes.add_textbox(Inches(0.8), Inches(0.4), Inches(11.7), Inches(0.8))
    tf2 = tb2.text_frame
    p2 = tf2.paragraphs[0]
    p2.text = "SmartLab 2.0: 可编辑模块与参数配置清单 (可直接修改形状与文字)"
    p2.font.size = Pt(20)
    p2.font.bold = True
    p2.font.color.rgb = RGBColor(33, 33, 33)

    # Left Arrow (native PPT shape)
    arrow = slide2.shapes.add_shape(MSO_SHAPE.UP_ARROW, Inches(0.8), Inches(1.5), Inches(2.8), Inches(5.2))
    arrow.fill.solid()
    arrow.fill.fore_color.rgb = RGBColor(239, 154, 154)
    arrow.line.color.rgb = RGBColor(229, 115, 115)
    arrow.text = "\n\n任务导向\n可执行建模需求\n\n(Task-Oriented Demand)"

    # Right 3 Tier cards
    tiers = [
        ("动态工作流层 (Dynamic Workflow Layer - F)", 
         "• 节点生命周期: Pending -> Running -> Completed\n• 接口控制活边 (E_I): START / ABORT 信号路由\n• 数据端口通道 (E_P): 反应物料与传感参数拓扑拉取\n• 调度节拍: 100ms 周期轮询与上升沿求值驱动", 
         RGBColor(232, 245, 233), RGBColor(76, 175, 80), Inches(1.5)),
        
        ("统一观测空间与安全约束层 (Obs & Constraint - C)", 
         "• 权威内存快照: ConcurrentHashMap (纳秒级只读访问)\n• 时序窗口历史 (OHS): 滑动窗口实时计算 Δ / 均值 / 速率\n• 双层边界防护: 全天候基线约束 (BC) + 任务专属阶段约束 (TC)\n• 分级联锁处置: Warning -> Alarm -> Pause -> Interlock Abort", 
         RGBColor(225, 245, 254), RGBColor(3, 169, 244), Inches(3.2)),
        
        ("静态资源与边缘驱动层 (Static Resource Layer - R)", 
         "• 双状态空间复合状态机: St = St^cmd (指令生命周期) × St^op (物理运行态)\n• 设备内禀约束 (Constr_D): 单机级硬防护，违规立即转异常态\n• 适配器 4 层契约模型: Runtime | Northbound | Core | Southbound\n• 硬件接入池: 恒温反应釜、蠕动泵、精密温控仪 (TCU)、在线光谱仪", 
         RGBColor(255, 243, 224), RGBColor(255, 152, 0), Inches(4.9))
    ]

    for title, desc, fill_color, line_color, top_y in tiers:
        card = slide2.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(4.0), top_y, Inches(8.5), Inches(1.5))
        card.fill.solid()
        card.fill.fore_color.rgb = fill_color
        card.line.color.rgb = line_color
        card.line.width = Pt(1.5)
        
        tf_card = card.text_frame
        tf_card.word_wrap = True
        p_t = tf_card.paragraphs[0]
        p_t.text = title
        p_t.font.bold = True
        p_t.font.size = Pt(13)
        p_t.font.color.rgb = RGBColor(33, 33, 33)
        
        p_d = tf_card.add_paragraph()
        p_d.text = desc
        p_d.font.size = Pt(10)
        p_d.font.color.rgb = RGBColor(66, 66, 66)

    pptx_path = os.path.join(OUTPUT_DIR, "smartlab_3d_architecture.pptx")
    prs.save(pptx_path)
    print(f"Generated PPTX: {pptx_path}")

if __name__ == "__main__":
    generate_assets()
