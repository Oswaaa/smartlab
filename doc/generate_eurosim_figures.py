# -*- coding: utf-8 -*-
"""
Generate the 3 formal diagrams for EUROSIM 2026 Presentation (PPT slides 8, 9, 10).
Exact canvas size: 735 x 390 px (Aspect ratio ~ 1.88:1).
Professional, clean, flat vector style with Microsoft YaHei font.
Color palette:
  - Deep Blue: Control & structure (#1E3A8A, #2563EB, #1D4ED8)
  - Green: Status / data feedback (#16A34A, #10B981, #059669)
  - Orange: Constraint triggers / alerts (#EA580C, #F97316, #C2410C)
  - Light Gray / Neutral: Protocols, environments, borders (#F8FAFC, #F1F5F9, #E2E8F0, #64748B, #475569)
"""

import os
import subprocess

OUTPUT_DIR = r"d:\SmartLab2.0\doc\output\figures"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# ----------------------------------------------------------------------
# Common SVG Header & Helpers
# ----------------------------------------------------------------------
COMMON_DEFS = '''
  <defs>
    <!-- Arrow Markers -->
    <!-- Blue solid arrow for control request -->
    <marker id="arrow-blue" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
      <path d="M 1 1.5 L 8 5 L 1 8.5 z" fill="#2563EB" />
    </marker>
    <!-- Dark Blue arrow -->
    <marker id="arrow-darkblue" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
      <path d="M 1 1.5 L 8 5 L 1 8.5 z" fill="#1E3A8A" />
    </marker>
    <!-- Green solid arrow for status/data feedback -->
    <marker id="arrow-green" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
      <path d="M 1 1.5 L 8 5 L 1 8.5 z" fill="#16A34A" />
    </marker>
    <!-- Orange dashed arrow for constraints -->
    <marker id="arrow-orange" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
      <path d="M 1 1.5 L 8 5 L 1 8.5 z" fill="#EA580C" />
    </marker>
    <!-- Subtle drop shadows for key cards -->
    <filter id="card-shadow" x="-4%" y="-4%" width="108%" height="108%" filterUnits="userSpaceOnUse">
      <feDropShadow dx="0" dy="2" stdDeviation="3" flood-color="#0F172A" flood-opacity="0.06"/>
    </filter>
    <filter id="badge-shadow" x="-6%" y="-6%" width="112%" height="112%" filterUnits="userSpaceOnUse">
      <feDropShadow dx="0" dy="1" stdDeviation="2" flood-color="#0F172A" flood-opacity="0.08"/>
    </filter>
  </defs>
'''

# ======================================================================
# Figure 1: 统一执行链路 (Unified Execution Pathway - Slide 8)
# ======================================================================
def generate_figure_1_svg():
    """
    735 x 390 px
    Left vertical main chain: Workflow Node -> Device Model (Capability + State Machine) -> Adapter -> Physical Device
    Intent sources: Human, Workflow, LLM -> converge at Device Model entrance
    Right side: Constraint observation line -> observe resources & context -> warning/stop/abort -> return to State Machine
    """
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 735 390" width="735" height="390" style="background:#FFFFFF; font-family:'Microsoft YaHei', 'PingFang SC', sans-serif;">
  {COMMON_DEFS}

  <!-- Canvas Outer Background & Subtle Border -->
  <rect x="0" y="0" width="735" height="390" fill="#FFFFFF" rx="8" stroke="#E2E8F0" stroke-width="1.5"/>

  <!-- Diagram Title Header Bar -->
  <g transform="translate(18, 14)">
    <rect x="0" y="0" width="4" height="18" fill="#1E3A8A" rx="2"/>
    <text x="12" y="15" font-size="16" font-weight="bold" fill="#0F172A" letter-spacing="0.5">正式图 1：统一执行链路结构</text>
    <text x="216" y="14.5" font-size="12" fill="#64748B">（多源意图收敛 · 状态机独占发令 · 约束全局观察无旁路）</text>
  </g>

  <!-- ==================== LEFT: INTENT SOURCES (3 来源) ==================== -->
  <g id="intent-sources">
    <!-- Outer Group Box -->
    <rect x="18" y="44" width="138" height="106" rx="6" fill="#F8FAFC" stroke="#CBD5E1" stroke-width="1.2" stroke-dasharray="3 3"/>
    <text x="87" y="60" text-anchor="middle" font-size="11" font-weight="bold" fill="#475569">意图来源</text>

    <!-- Source 1: 人工 -->
    <rect x="26" y="67" width="122" height="22" rx="4" fill="#FFFFFF" stroke="#94A3B8" stroke-width="1" filter="url(#badge-shadow)"/>
    <circle cx="37" cy="78" r="4" fill="#64748B"/>
    <text x="47" y="82" font-size="11" font-weight="bold" fill="#1E293B">人工交互意图</text>

    <!-- Source 2: 流程 -->
    <rect x="26" y="93" width="122" height="22" rx="4" fill="#FFFFFF" stroke="#2563EB" stroke-width="1" filter="url(#badge-shadow)"/>
    <circle cx="37" cy="104" r="4" fill="#2563EB"/>
    <text x="47" y="108" font-size="11" font-weight="bold" fill="#1D4ED8">自动化流程任务</text>

    <!-- Source 3: 大模型 -->
    <rect x="26" y="119" width="122" height="22" rx="4" fill="#FFFFFF" stroke="#7C3AED" stroke-width="1" filter="url(#badge-shadow)"/>
    <circle cx="37" cy="130" r="4" fill="#7C3AED"/>
    <text x="47" y="134" font-size="11" font-weight="bold" fill="#6D28D9">大模型智能体</text>
  </g>

  <!-- Intent Convergence Arrows into Unified Entrance -->
  <!-- Convergence Connector Node -->
  <path d="M 148 78 C 172 78, 172 104, 184 104" fill="none" stroke="#2563EB" stroke-width="1.6"/>
  <path d="M 148 104 L 184 104" fill="none" stroke="#2563EB" stroke-width="1.6"/>
  <path d="M 148 130 C 172 130, 172 104, 184 104" fill="none" stroke="#2563EB" stroke-width="1.6"/>

  <!-- Convergence Badge -->
  <g transform="translate(178, 93)">
    <rect x="0" y="0" width="112" height="22" rx="11" fill="#EFF6FF" stroke="#3B82F6" stroke-width="1.2" filter="url(#badge-shadow)"/>
    <text x="56" y="15" text-anchor="middle" font-size="10.5" font-weight="bold" fill="#1E40AF">共同执行入口</text>
  </g>

  <!-- Convergence Arrow into Device Model -->
  <path d="M 234 115 L 234 148" fill="none" stroke="#2563EB" stroke-width="2" marker-end="url(#arrow-blue)"/>
  <text x="238" y="136" font-size="10" font-weight="bold" fill="#2563EB">统一意图接入</text>


  <!-- ==================== MAIN EXECUTION CHAIN (纵向执行主链) ==================== -->
  <!-- Column Anchor: X=180, Width=290 -->

  <!-- LAYER 1: 工作流节点 (Workflow Node) -->
  <g id="layer-workflow" transform="translate(180, 44)">
    <rect x="0" y="0" width="280" height="42" rx="6" fill="#F0F7FF" stroke="#2563EB" stroke-width="1.8" filter="url(#card-shadow)"/>
    <!-- Node Icon & Title -->
    <rect x="10" y="9" width="24" height="24" rx="4" fill="#2563EB"/>
    <text x="22" y="25" text-anchor="middle" font-size="12" font-weight="bold" fill="#FFFFFF">WF</text>
    <text x="42" y="20" font-size="13" font-weight="bold" fill="#1E3A8A">工作流节点 (Workflow Node)</text>
    <text x="42" y="34" font-size="10.5" fill="#475569">作用：接口传递信号 · 数据端口交互 · 步骤生命周期</text>
  </g>

  <!-- Connection: Workflow to Device Model -->
  <!-- Downward Control Request -->
  <path d="M 270 86 L 270 148" fill="none" stroke="#2563EB" stroke-width="2" marker-end="url(#arrow-blue)"/>
  <rect x="250" y="110" width="40" height="16" rx="3" fill="#FFFFFF" stroke="#BFDBFE" stroke-width="0.8"/>
  <text x="270" y="122" text-anchor="middle" font-size="9.5" font-weight="bold" fill="#1D4ED8">控制请求</text>

  <!-- Upward State Feedback -->
  <path d="M 370 148 L 370 86" fill="none" stroke="#16A34A" stroke-width="2" marker-end="url(#arrow-green)"/>
  <rect x="350" y="110" width="40" height="16" rx="3" fill="#FFFFFF" stroke="#BBF7D0" stroke-width="0.8"/>
  <text x="370" y="122" text-anchor="middle" font-size="9.5" font-weight="bold" fill="#15803D">状态反馈</text>


  <!-- LAYER 2: 设备模型 (Device Model: Capability + State Machine) -->
  <g id="layer-model" transform="translate(180, 150)">
    <!-- Container Box -->
    <rect x="0" y="0" width="280" height="96" rx="6" fill="#F8FAFC" stroke="#1E3A8A" stroke-width="2" filter="url(#card-shadow)"/>
    <rect x="0" y="0" width="280" height="22" rx="6" fill="#1E3A8A"/>
    <text x="140" y="15" text-anchor="middle" font-size="11.5" font-weight="bold" fill="#FFFFFF">设备模型 (Device Model) · 执行控制核心</text>

    <!-- Sub-block 1: 能力模型 (Capability Model) -->
    <g transform="translate(8, 28)">
      <rect x="0" y="0" width="126" height="60" rx="4" fill="#FFFFFF" stroke="#93C5FD" stroke-width="1.2"/>
      <text x="63" y="16" text-anchor="middle" font-size="11" font-weight="bold" fill="#1D4ED8">能力模型</text>
      <text x="63" y="32" text-anchor="middle" font-size="9.5" fill="#475569">提供设备属性定义</text>
      <text x="63" y="46" text-anchor="middle" font-size="9.5" fill="#475569">动作语义说明与契约</text>
    </g>

    <!-- Internal Link between Capability and State Machine -->
    <path d="M 134 58 L 146 58" stroke="#94A3B8" stroke-width="1.5" stroke-dasharray="2 2"/>

    <!-- Sub-block 2: 状态机 (State Machine) -->
    <g transform="translate(146, 28)">
      <rect x="0" y="0" width="126" height="60" rx="4" fill="#EFF6FF" stroke="#2563EB" stroke-width="1.5"/>
      <text x="63" y="16" text-anchor="middle" font-size="11" font-weight="bold" fill="#1E40AF">★ 状态机 (Core)</text>
      <text x="63" y="32" text-anchor="middle" font-size="9.5" font-weight="bold" fill="#2563EB">发令独占控制实体</text>
      <text x="63" y="46" text-anchor="middle" font-size="9" fill="#475569">状态转移 / 异常锁存</text>
    </g>
  </g>

  <!-- Connection: State Machine to Adapter -->
  <!-- Downward Command Dispatch -->
  <path d="M 270 246 L 270 278" fill="none" stroke="#2563EB" stroke-width="2" marker-end="url(#arrow-blue)"/>
  <rect x="250" y="254" width="40" height="15" rx="3" fill="#FFFFFF" stroke="#BFDBFE" stroke-width="0.8"/>
  <text x="270" y="265" text-anchor="middle" font-size="9" font-weight="bold" fill="#1D4ED8">下发命令</text>

  <!-- Upward Event Update -->
  <path d="M 370 278 L 370 246" fill="none" stroke="#16A34A" stroke-width="2" marker-end="url(#arrow-green)"/>
  <rect x="350" y="254" width="40" height="15" rx="3" fill="#FFFFFF" stroke="#BBF7D0" stroke-width="0.8"/>
  <text x="370" y="265" text-anchor="middle" font-size="9" font-weight="bold" fill="#15803D">反馈转换</text>


  <!-- LAYER 3: 适配器 (Adapter) -->
  <g id="layer-adapter" transform="translate(180, 280)">
    <rect x="0" y="0" width="280" height="38" rx="6" fill="#F0FDF4" stroke="#059669" stroke-width="1.6" filter="url(#card-shadow)"/>
    <rect x="10" y="8" width="22" height="22" rx="4" fill="#059669"/>
    <text x="21" y="23" text-anchor="middle" font-size="11" font-weight="bold" fill="#FFFFFF">AD</text>
    <text x="38" y="17" font-size="12" font-weight="bold" fill="#065F46">Adapter (边缘适配层)</text>
    <text x="38" y="30" font-size="10" fill="#047857">作用：协议映射 · 硬件命令下发 · 原始数据/状态反馈转换</text>
  </g>

  <!-- Connection: Adapter to Physical Device -->
  <path d="M 270 318 L 270 340" fill="none" stroke="#2563EB" stroke-width="2" marker-end="url(#arrow-blue)"/>
  <path d="M 370 340 L 370 318" fill="none" stroke="#16A34A" stroke-width="2" marker-end="url(#arrow-green)"/>


  <!-- LAYER 4: 物理设备 (Physical Device) -->
  <g id="layer-physical" transform="translate(180, 342)">
    <rect x="0" y="0" width="280" height="36" rx="6" fill="#1E293B" stroke="#0F172A" stroke-width="1.5" filter="url(#card-shadow)"/>
    <text x="140" y="16" text-anchor="middle" font-size="12" font-weight="bold" fill="#F8FAFC">物理设备 (Physical Equipment / PLC)</text>
    <text x="140" y="29" text-anchor="middle" font-size="9.5" fill="#94A3B8">作用：执行物理动作 · 产生传感器测量与真实硬件运行状态</text>
  </g>


  <!-- ==================== RIGHT: CONSTRAINT OBSERVATION & HANDLING ==================== -->
  <!-- Constraint Engine Box -->
  <g id="constraint-system" transform="translate(490, 44)">
    <!-- Main Constraint Box -->
    <rect x="0" y="0" width="228" height="232" rx="6" fill="#FFF7ED" stroke="#EA580C" stroke-width="1.8" filter="url(#card-shadow)"/>
    
    <!-- Header -->
    <rect x="0" y="0" width="228" height="24" rx="6" fill="#EA580C"/>
    <text x="114" y="16" text-anchor="middle" font-size="11.5" font-weight="bold" fill="#FFFFFF">约束观察与处置机制 (Constraints)</text>

    <!-- Observation Scope Card -->
    <g transform="translate(10, 32)">
      <rect x="0" y="0" width="208" height="66" rx="4" fill="#FFFFFF" stroke="#FDBA74" stroke-width="1"/>
      <text x="10" y="16" font-size="10.5" font-weight="bold" fill="#C2410C">全链路实时约束观察</text>
      <text x="10" y="33" font-size="9.5" fill="#7C2D12">• 观察工作流上下文与节点时序</text>
      <text x="10" y="47" font-size="9.5" fill="#7C2D12">• 观察资源属性、状态与遥测历史</text>
      <text x="10" y="59" font-size="9" fill="#9A3412" font-weight="bold">【严禁绕过模型与状态机直连硬件】</text>
    </g>

    <!-- Trigger Action Card -->
    <g transform="translate(10, 106)">
      <rect x="0" y="0" width="208" height="64" rx="4" fill="#FFFFFF" stroke="#FB923C" stroke-width="1"/>
      <text x="10" y="16" font-size="10.5" font-weight="bold" fill="#C2410C">多级保护处理动作</text>
      <!-- Action Badges -->
      <g transform="translate(10, 25)">
        <rect x="0" y="0" width="38" height="18" rx="3" fill="#FEF3C7" stroke="#D97706" stroke-width="0.8"/>
        <text x="19" y="12.5" text-anchor="middle" font-size="9.5" font-weight="bold" fill="#B45309">警告</text>

        <rect x="46" y="0" width="38" height="18" rx="3" fill="#FFEDD5" stroke="#EA580C" stroke-width="0.8"/>
        <text x="65" y="12.5" text-anchor="middle" font-size="9.5" font-weight="bold" fill="#C2410C">报警</text>

        <rect x="92" y="0" width="38" height="18" rx="3" fill="#FEE2E2" stroke="#DC2626" stroke-width="0.8"/>
        <text x="111" y="12.5" text-anchor="middle" font-size="9.5" font-weight="bold" fill="#B91C1C">停止</text>

        <rect x="138" y="0" width="46" height="18" rx="3" fill="#450A0A" stroke="#7F1D1D" stroke-width="0.8"/>
        <text x="161" y="12.5" text-anchor="middle" font-size="9.5" font-weight="bold" fill="#FEF2F2">中止</text>
      </g>
      <text x="10" y="56" font-size="9.5" fill="#64748B">依规则触发优雅降级或强制联锁</text>
    </g>

    <!-- Feedback Back to Unified Execution Mechanism -->
    <g transform="translate(10, 178)">
      <rect x="0" y="0" width="208" height="44" rx="4" fill="#FFFBEB" stroke="#F59E0B" stroke-width="1"/>
      <text x="104" y="16" text-anchor="middle" font-size="10" font-weight="bold" fill="#B45309">处置动作回注统一执行机制</text>
      <text x="104" y="32" text-anchor="middle" font-size="9" fill="#92400E">驱动状态机异常锁存 / 节点生命周期流转</text>
    </g>
  </g>

  <!-- Observation Scope Connector Lines (Orange Dashed) -->
  <!-- Observing Workflow Node -->
  <path d="M 460 65 L 490 65" fill="none" stroke="#F97316" stroke-width="1.5" stroke-dasharray="3 3"/>
  <circle cx="460" cy="65" r="3" fill="#F97316"/>

  <!-- Observing Device Model -->
  <path d="M 460 198 L 490 198" fill="none" stroke="#F97316" stroke-width="1.5" stroke-dasharray="3 3"/>
  <circle cx="460" cy="198" r="3" fill="#F97316"/>

  <!-- Observing Adapter Feedback -->
  <path d="M 460 299 L 476 299 C 484 299, 490 270, 490 240" fill="none" stroke="#F97316" stroke-width="1.5" stroke-dasharray="3 3"/>
  <circle cx="460" cy="299" r="3" fill="#F97316"/>

  <!-- Action Injection Back to State Machine (Solid Orange Arrow returning left) -->
  <path d="M 490 230 C 472 230, 460 220, 396 220" fill="none" stroke="#EA580C" stroke-width="2" stroke-dasharray="4 2" marker-end="url(#arrow-orange)"/>
  <rect x="408" y="210" width="76" height="15" rx="3" fill="#FFFFFF" stroke="#FDBA74" stroke-width="0.8"/>
  <text x="446" y="221" text-anchor="middle" font-size="9" font-weight="bold" fill="#EA580C">约束动作回注</text>

  <!-- Bottom Clarification Card on Right -->
  <g transform="translate(490, 288)">
    <rect x="0" y="0" width="228" height="90" rx="6" fill="#F8FAFC" stroke="#E2E8F0" stroke-width="1.2"/>
    <text x="12" y="18" font-size="10.5" font-weight="bold" fill="#1E293B">核心架构原则 (Architecture Invariant)</text>
    <text x="12" y="36" font-size="9.5" fill="#475569">1. 状态机必须在发令路径上，严禁旁路</text>
    <text x="12" y="52" font-size="9.5" fill="#475569">2. 约束作为独立观察者，不可兼任控制旁路</text>
    <text x="12" y="68" font-size="9.5" fill="#475569">3. 所有反馈沿原路严格收敛闭环</text>
    <text x="12" y="82" font-size="9" font-weight="bold" fill="#0D9488">统一控制模型 · 状态强一致性保证</text>
  </g>

</svg>'''
    return svg


# ======================================================================
# Figure 2: 加热任务执行时序 (Heating Task Sequence - Slide 9)
# ======================================================================
def generate_figure_2_svg():
    """
    735 x 390 px
    4 Lifelines: 加热节点 | 设备状态机 | Adapter | 反应釜
    6 Numbered Messages:
      1. 加热节点 -> 状态机: 请求加热 (Blue ->)
      2. 状态机 -> Adapter: 下发加热命令 (Blue ->)
      3. Adapter -> 反应釜: 执行加热 (Blue ->)
      4. 反应釜 -> Adapter: 温度 / 运行状态 (Green <-)
      5. Adapter -> 状态机: 反馈更新 (Green <-)
      6. 状态机 -> 加热节点: 执行状态 (Green <-)
    Annotation near Step 6: 满足完成条件后，节点推进后续步骤
    Orange branch: 温度越界 / 设备异常 -> 按约束采取动作 (回到状态机处理)
    """
    x1 = 92   # 加热节点
    x2 = 252  # 设备状态机
    x3 = 412  # Adapter
    x4 = 572  # 反应釜

    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 735 390" width="735" height="390" style="background:#FFFFFF; font-family:'Microsoft YaHei', 'PingFang SC', sans-serif;">
  {COMMON_DEFS}

  <!-- Canvas Outer Background & Subtle Border -->
  <rect x="0" y="0" width="735" height="390" fill="#FFFFFF" rx="8" stroke="#E2E8F0" stroke-width="1.5"/>

  <!-- Diagram Title Header Bar -->
  <g transform="translate(18, 14)">
    <rect x="0" y="0" width="4" height="18" fill="#1E3A8A" rx="2"/>
    <text x="12" y="15" font-size="16" font-weight="bold" fill="#0F172A" letter-spacing="0.5">正式图 2：加热任务执行时序</text>
    <text x="216" y="14.5" font-size="12" fill="#64748B">（控制下发 · 遥测回传 · 条件判断推进 · 异常约束介入）</text>
  </g>

  <!-- ==================== LIFELINE HEADERS ==================== -->
  <!-- Header 1: 加热节点 -->
  <g transform="translate({x1-55}, 44)">
    <rect x="0" y="0" width="110" height="32" rx="5" fill="#EFF6FF" stroke="#2563EB" stroke-width="1.6" filter="url(#badge-shadow)"/>
    <text x="55" y="16" text-anchor="middle" font-size="12" font-weight="bold" fill="#1E40AF">加热节点</text>
    <text x="55" y="27" text-anchor="middle" font-size="9" fill="#3B82F6">Workflow Node</text>
  </g>

  <!-- Header 2: 设备状态机 -->
  <g transform="translate({x2-60}, 44)">
    <rect x="0" y="0" width="120" height="32" rx="5" fill="#F5F3FF" stroke="#7C3AED" stroke-width="1.8" filter="url(#badge-shadow)"/>
    <text x="60" y="16" text-anchor="middle" font-size="12" font-weight="bold" fill="#5B21B6">★ 设备状态机</text>
    <text x="60" y="27" text-anchor="middle" font-size="9" fill="#7C3AED">State Machine (Core)</text>
  </g>

  <!-- Header 3: Adapter -->
  <g transform="translate({x3-55}, 44)">
    <rect x="0" y="0" width="110" height="32" rx="5" fill="#F0FDF4" stroke="#059669" stroke-width="1.6" filter="url(#badge-shadow)"/>
    <text x="55" y="16" text-anchor="middle" font-size="12" font-weight="bold" fill="#065F46">Adapter</text>
    <text x="55" y="27" text-anchor="middle" font-size="9" fill="#10B981">边缘适配层</text>
  </g>

  <!-- Header 4: 反应釜 -->
  <g transform="translate({x4-55}, 44)">
    <rect x="0" y="0" width="110" height="32" rx="5" fill="#1E293B" stroke="#0F172A" stroke-width="1.6" filter="url(#badge-shadow)"/>
    <text x="55" y="16" text-anchor="middle" font-size="12" font-weight="bold" fill="#F8FAFC">反应釜</text>
    <text x="55" y="27" text-anchor="middle" font-size="9" fill="#94A3B8">物理仪器 / PLC</text>
  </g>

  <!-- Vertical Lifelines (Dashed Lines) -->
  <line x1="{x1}" y1="76" x2="{x1}" y2="350" stroke="#CBD5E1" stroke-width="1.5" stroke-dasharray="4 4"/>
  <line x1="{x2}" y1="76" x2="{x2}" y2="350" stroke="#CBD5E1" stroke-width="1.5" stroke-dasharray="4 4"/>
  <line x1="{x3}" y1="76" x2="{x3}" y2="350" stroke="#CBD5E1" stroke-width="1.5" stroke-dasharray="4 4"/>
  <line x1="{x4}" y1="76" x2="{x4}" y2="350" stroke="#CBD5E1" stroke-width="1.5" stroke-dasharray="4 4"/>

  <!-- Activation Bars (Slim Rectangles on Lifelines) -->
  <!-- Node Activation -->
  <rect x="{x1-5}" y="95" width="10" height="230" rx="2" fill="#DBEAFE" stroke="#2563EB" stroke-width="1"/>
  <!-- State Machine Activation -->
  <rect x="{x2-5}" y="105" width="10" height="210" rx="2" fill="#EDE9FE" stroke="#7C3AED" stroke-width="1"/>
  <!-- Adapter Activation -->
  <rect x="{x3-5}" y="135" width="10" height="150" rx="2" fill="#D1FAE5" stroke="#059669" stroke-width="1"/>
  <!-- Reactor Activation -->
  <rect x="{x4-5}" y="165" width="10" height="90" rx="2" fill="#E2E8F0" stroke="#475569" stroke-width="1"/>


  <!-- ==================== 6 NUMBERED MESSAGES ==================== -->

  <!-- 1. 加热节点 -> 状态机: 请求加热 (Blue ->) -->
  <g id="msg-1">
    <line x1="{x1+5}" y1="108" x2="{x2-5}" y2="108" stroke="#2563EB" stroke-width="2" marker-end="url(#arrow-blue)"/>
    <rect x="{ (x1+x2)/2 - 58 }" y="96" width="116" height="20" rx="4" fill="#EFF6FF" stroke="#93C5FD" stroke-width="1"/>
    <text x="{ (x1+x2)/2 }" y="110" text-anchor="middle" font-size="11" font-weight="bold" fill="#1D4ED8">1. 请求加热</text>
  </g>

  <!-- 2. 状态机 -> Adapter: 下发加热命令 (Blue ->) -->
  <g id="msg-2">
    <line x1="{x2+5}" y1="140" x2="{x3-5}" y2="140" stroke="#2563EB" stroke-width="2" marker-end="url(#arrow-blue)"/>
    <rect x="{ (x2+x3)/2 - 65 }" y="128" width="130" height="20" rx="4" fill="#EFF6FF" stroke="#93C5FD" stroke-width="1"/>
    <text x="{ (x2+x3)/2 }" y="142" text-anchor="middle" font-size="11" font-weight="bold" fill="#1D4ED8">2. 下发加热命令</text>
  </g>

  <!-- 3. Adapter -> 反应釜: 执行加热 (Blue ->) -->
  <g id="msg-3">
    <line x1="{x3+5}" y1="172" x2="{x4-5}" y2="172" stroke="#2563EB" stroke-width="2" marker-end="url(#arrow-blue)"/>
    <rect x="{ (x3+x4)/2 - 58 }" y="160" width="116" height="20" rx="4" fill="#EFF6FF" stroke="#93C5FD" stroke-width="1"/>
    <text x="{ (x3+x4)/2 }" y="174" text-anchor="middle" font-size="11" font-weight="bold" fill="#1D4ED8">3. 执行加热</text>
  </g>

  <!-- 4. 反应釜 -> Adapter: 温度 / 运行状态 (Green <-) -->
  <g id="msg-4">
    <line x1="{x4-5}" y1="216" x2="{x3+5}" y2="216" stroke="#16A34A" stroke-width="2" marker-end="url(#arrow-green)"/>
    <rect x="{ (x3+x4)/2 - 72 }" y="204" width="144" height="20" rx="4" fill="#F0FDF4" stroke="#86EFAC" stroke-width="1"/>
    <text x="{ (x3+x4)/2 }" y="218" text-anchor="middle" font-size="11" font-weight="bold" fill="#15803D">4. 温度 / 运行状态</text>
    <!-- Note that 4 is NOT completion -->
    <text x="{ (x3+x4)/2 }" y="235" text-anchor="middle" font-size="9" fill="#64748B">（遥测采样，非任务完成）</text>
  </g>

  <!-- 5. Adapter -> 状态机: 反馈更新 (Green <-) -->
  <g id="msg-5">
    <line x1="{x3-5}" y1="254" x2="{x2+5}" y2="254" stroke="#16A34A" stroke-width="2" marker-end="url(#arrow-green)"/>
    <rect x="{ (x2+x3)/2 - 58 }" y="242" width="116" height="20" rx="4" fill="#F0FDF4" stroke="#86EFAC" stroke-width="1"/>
    <text x="{ (x2+x3)/2 }" y="256" text-anchor="middle" font-size="11" font-weight="bold" fill="#15803D">5. 反馈更新</text>
  </g>

  <!-- 6. 状态机 -> 加热节点: 执行状态 (Green <-) -->
  <g id="msg-6">
    <line x1="{x2-5}" y1="288" x2="{x1+5}" y2="288" stroke="#16A34A" stroke-width="2" marker-end="url(#arrow-green)"/>
    <rect x="{ (x1+x2)/2 - 58 }" y="276" width="116" height="20" rx="4" fill="#F0FDF4" stroke="#86EFAC" stroke-width="1"/>
    <text x="{ (x1+x2)/2 }" y="290" text-anchor="middle" font-size="11" font-weight="bold" fill="#15803D">6. 执行状态</text>
  </g>

  <!-- CRITICAL ANNOTATION NEAR STEP 6 -->
  <g transform="translate(18, 318)">
    <rect x="0" y="0" width="220" height="52" rx="5" fill="#EFF6FF" stroke="#3B82F6" stroke-width="1.4" filter="url(#badge-shadow)"/>
    <text x="10" y="16" font-size="10.5" font-weight="bold" fill="#1E40AF">★ 任务推进判定条件：</text>
    <text x="10" y="32" font-size="9.5" fill="#1D4ED8">满足完成条件后，节点推进后续步骤</text>
    <text x="10" y="45" font-size="9" fill="#64748B">（例如目标温度 80℃ 且持续达到预设保持时长）</text>
  </g>


  <!-- ==================== ORANGE CONSTRAINT BRANCH (右侧异常分支) ==================== -->
  <g id="constraint-branch" transform="translate(485, 275)">
    <!-- Branch Container Box -->
    <rect x="0" y="0" width="232" height="95" rx="6" fill="#FFF7ED" stroke="#EA580C" stroke-width="1.5" filter="url(#card-shadow)"/>
    
    <!-- Header -->
    <g transform="translate(8, 8)">
      <circle cx="6" cy="6" r="5" fill="#EA580C"/>
      <text x="16" y="10" font-size="11" font-weight="bold" fill="#9A3412">约束触发分支 (异常防护)</text>
    </g>

    <text x="12" y="36" font-size="10.5" font-weight="bold" fill="#C2410C">温度越界 / 设备异常</text>
    <text x="12" y="52" font-size="9.5" fill="#7C2D12">→ 按规则采取动作 (停止/报警/安全降温)</text>

    <!-- Branch Action Label -->
    <rect x="12" y="60" width="208" height="24" rx="4" fill="#FFFFFF" stroke="#FDBA74" stroke-width="1"/>
    <text x="116" y="76" text-anchor="middle" font-size="9.5" font-weight="bold" fill="#EA580C">回到设备状态机处理 · 严禁直穿物理层</text>
  </g>

  <!-- Orange Constraint Arrow: From Right side looping back into State Machine lifeline -->
  <!-- Originates from Constraint Branch, points to State Machine lifeline -->
  <path d="M 485 322 C 380 322, 320 310, {x2+5} 310" fill="none" stroke="#EA580C" stroke-width="2" stroke-dasharray="4 3" marker-end="url(#arrow-orange)"/>
  <rect x="310" y="318" width="86" height="16" rx="3" fill="#FFFFFF" stroke="#FDBA74" stroke-width="0.8"/>
  <text x="353" y="330" text-anchor="middle" font-size="9" font-weight="bold" fill="#EA580C">异常动作回注状态机</text>

  <!-- Legend in lower middle -->
  <g transform="translate(254, 345)">
    <line x1="0" y1="8" x2="20" y2="8" stroke="#2563EB" stroke-width="2"/>
    <text x="24" y="11" font-size="9" fill="#1E3A8A">控制消息</text>

    <line x1="75" y1="8" x2="95" y2="8" stroke="#16A34A" stroke-width="2"/>
    <text x="99" y="11" font-size="9" fill="#15803D">反馈消息</text>

    <line x1="150" y1="8" x2="170" y2="8" stroke="#EA580C" stroke-width="1.8" stroke-dasharray="3 2"/>
    <text x="174" y="11" font-size="9" fill="#C2410C">约束动作</text>
  </g>

</svg>'''
    return svg


# ======================================================================
# Figure 3: 仿真反馈闭环 (Simulation Feedback Closed Loop - Slide 10)
# ======================================================================
def generate_figure_3_svg():
    """
    735 x 390 px
    Top: Common Model Area: 工作流 + 设备能力/状态机 + 约束
    Left: 大模型决策 (LLM Decision)
    Bottom: 仿真 Adapter + 模拟设备/环境
    Main Loop: 大模型决策 -> 共同模型区 -> 仿真 Adapter/模拟环境 -> 模拟反馈 -> 大模型调整 (Blue solid down, Green solid back to LLM)
    Annotation on feedback: 状态、数据、约束结果
    Right branch: 共同模型区向右 -> 网关: 满足预设上机条件 -> 实机 Adapter -> 物理设备
    Simulation assessment: Green dashed arrow to Gateway as judgment basis
    Real feedback: returns along Common Model area
    Callout: 仿真 / 实机共用上层模型与规则
    """
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 735 390" width="735" height="390" style="background:#FFFFFF; font-family:'Microsoft YaHei', 'PingFang SC', sans-serif;">
  {COMMON_DEFS}

  <!-- Canvas Outer Background & Subtle Border -->
  <rect x="0" y="0" width="735" height="390" fill="#FFFFFF" rx="8" stroke="#E2E8F0" stroke-width="1.5"/>

  <!-- Diagram Title Header Bar -->
  <g transform="translate(18, 14)">
    <rect x="0" y="0" width="4" height="18" fill="#1E3A8A" rx="2"/>
    <text x="12" y="15" font-size="16" font-weight="bold" fill="#0F172A" letter-spacing="0.5">正式图 3：仿真反馈闭环</text>
    <text x="200" y="14.5" font-size="12" fill="#64748B">（共用上层模型与规则 · 模拟闭环调整方案 · 满足条件平滑上机）</text>
  </g>


  <!-- ==================== TOP: COMMON MODEL AREA (共同模型区) ==================== -->
  <g id="common-model-area" transform="translate(195, 42)">
    <!-- Main Big Container -->
    <rect x="0" y="0" width="350" height="92" rx="6" fill="#F8FAFC" stroke="#1E3A8A" stroke-width="2" filter="url(#card-shadow)"/>
    
    <!-- Top Header Ribbon -->
    <rect x="0" y="0" width="350" height="22" rx="6" fill="#1E3A8A"/>
    <text x="175" y="15" text-anchor="middle" font-size="11.5" font-weight="bold" fill="#FFFFFF">共同模型区（仿真 / 实机共用上层模型与规则）</text>

    <!-- Sub-boxes inside Common Model Area -->
    <!-- 1. 工作流 -->
    <g transform="translate(10, 30)">
      <rect x="0" y="0" width="102" height="52" rx="4" fill="#FFFFFF" stroke="#2563EB" stroke-width="1.2"/>
      <text x="51" y="18" text-anchor="middle" font-size="11" font-weight="bold" fill="#1E40AF">工作流定义</text>
      <text x="51" y="32" text-anchor="middle" font-size="9" fill="#64748B">任务步骤编排</text>
      <text x="51" y="44" text-anchor="middle" font-size="9" fill="#64748B">接口控制/端口数据</text>
    </g>

    <!-- 2. 设备能力与状态机 -->
    <g transform="translate(120, 30)">
      <rect x="0" y="0" width="112" height="52" rx="4" fill="#EFF6FF" stroke="#2563EB" stroke-width="1.4"/>
      <text x="56" y="18" text-anchor="middle" font-size="11" font-weight="bold" fill="#1E40AF">能力与状态机</text>
      <text x="56" y="32" text-anchor="middle" font-size="9" fill="#64748B">统一设备抽象能力</text>
      <text x="56" y="44" text-anchor="middle" font-size="9" font-weight="bold" fill="#2563EB">发令状态机引擎</text>
    </g>

    <!-- 3. 约束规则 -->
    <g transform="translate(240, 30)">
      <rect x="0" y="0" width="100" height="52" rx="4" fill="#FFF7ED" stroke="#EA580C" stroke-width="1.2"/>
      <text x="50" y="18" text-anchor="middle" font-size="11" font-weight="bold" fill="#C2410C">安全约束规则</text>
      <text x="50" y="32" text-anchor="middle" font-size="9" fill="#7C2D12">内禀 / 基线约束</text>
      <text x="50" y="44" text-anchor="middle" font-size="9" fill="#7C2D12">时序窗口防抖判定</text>
    </g>
  </g>


  <!-- ==================== LEFT: 大模型决策 (LLM / Agent) ==================== -->
  <g id="llm-decision" transform="translate(18, 95)">
    <!-- LLM Card -->
    <rect x="0" y="0" width="138" height="155" rx="6" fill="#FAF5FF" stroke="#7C3AED" stroke-width="1.8" filter="url(#card-shadow)"/>
    
    <!-- Title -->
    <rect x="0" y="0" width="138" height="24" rx="6" fill="#7C3AED"/>
    <text x="69" y="16" text-anchor="middle" font-size="12" font-weight="bold" fill="#FFFFFF">大模型决策</text>
    <text x="69" y="32" text-anchor="middle" font-size="9.5" fill="#6D28D9">（LLM Agent 规划）</text>

    <!-- Content -->
    <g transform="translate(8, 40)">
      <rect x="0" y="0" width="122" height="42" rx="4" fill="#FFFFFF" stroke="#C4B5FD" stroke-width="1"/>
      <text x="61" y="16" text-anchor="middle" font-size="10" font-weight="bold" fill="#4C1D95">自主生成方案</text>
      <text x="61" y="30" text-anchor="middle" font-size="9" fill="#6D28D9">配方参数与步骤流程</text>
    </g>

    <!-- Loop Adjustment Badge -->
    <g transform="translate(8, 90)">
      <rect x="0" y="0" width="122" height="55" rx="4" fill="#F5F3FF" stroke="#8B5CF6" stroke-width="1"/>
      <text x="61" y="16" text-anchor="middle" font-size="10" font-weight="bold" fill="#5B21B6">↺ 接收反馈并迭代</text>
      <text x="61" y="30" text-anchor="middle" font-size="9" fill="#4C1D95">评估时序与约束状态</text>
      <text x="61" y="44" text-anchor="middle" font-size="9" font-weight="bold" fill="#7C3AED">持续优化工艺参数</text>
    </g>
  </g>

  <!-- Forward Blue Arrow: LLM Decision -> Common Model Area -->
  <path d="M 156 125 C 175 125, 175 75, 195 75" fill="none" stroke="#2563EB" stroke-width="2" marker-end="url(#arrow-blue)"/>
  <rect x="145" y="65" width="46" height="16" rx="3" fill="#FFFFFF" stroke="#BFDBFE" stroke-width="0.8"/>
  <text x="168" y="77" text-anchor="middle" font-size="9.5" font-weight="bold" fill="#1D4ED8">下发方案</text>


  <!-- ==================== BOTTOM: SIMULATION LAYER (仿真闭环) ==================== -->
  <g id="sim-layer" transform="translate(195, 230)">
    <!-- Container -->
    <rect x="0" y="0" width="310" height="135" rx="6" fill="#F0FDF4" stroke="#16A34A" stroke-width="1.8" filter="url(#card-shadow)"/>
    
    <!-- Title Ribbon -->
    <rect x="0" y="0" width="310" height="22" rx="6" fill="#16A34A"/>
    <text x="155" y="15" text-anchor="middle" font-size="11.5" font-weight="bold" fill="#FFFFFF">仿真执行环境（数字孪生 / 虚拟仿真）</text>

    <!-- Sub-block 1: 仿真 Adapter -->
    <g transform="translate(12, 32)">
      <rect x="0" y="0" width="136" height="88" rx="5" fill="#FFFFFF" stroke="#86EFAC" stroke-width="1.2"/>
      <text x="68" y="18" text-anchor="middle" font-size="11" font-weight="bold" fill="#15803D">仿真 Adapter</text>
      <text x="68" y="32" text-anchor="middle" font-size="9" fill="#047857">（Sim Adapter）</text>
      <line x1="10" y1="38" x2="126" y2="38" stroke="#E2E8F0" stroke-width="1"/>
      <text x="68" y="52" text-anchor="middle" font-size="9.5" fill="#475569">对接模型统一命令</text>
      <text x="68" y="66" text-anchor="middle" font-size="9.5" fill="#475569">模拟硬件通信响应</text>
      <text x="68" y="80" text-anchor="middle" font-size="9" font-weight="bold" fill="#059669">协议与实机完全对齐</text>
    </g>

    <!-- Arrow between Sim Adapter and Sim Environment -->
    <path d="M 148 76 L 160 76" stroke="#16A34A" stroke-width="1.5" marker-end="url(#arrow-green)"/>

    <!-- Sub-block 2: 模拟设备 / 环境 -->
    <g transform="translate(162, 32)">
      <rect x="0" y="0" width="136" height="88" rx="5" fill="#FFFFFF" stroke="#86EFAC" stroke-width="1.2"/>
      <text x="68" y="18" text-anchor="middle" font-size="11" font-weight="bold" fill="#15803D">模拟设备与物理环境</text>
      <text x="68" y="32" text-anchor="middle" font-size="9" fill="#047857">（Physics Simulator）</text>
      <line x1="10" y1="38" x2="126" y2="38" stroke="#E2E8F0" stroke-width="1"/>
      <text x="68" y="52" text-anchor="middle" font-size="9.5" fill="#475569">数值求解 (传热/流体)</text>
      <text x="68" y="66" text-anchor="middle" font-size="9.5" fill="#475569">生成虚拟传感器指标</text>
      <text x="68" y="80" text-anchor="middle" font-size="9" font-weight="bold" fill="#059669">预演潜在冲突与越限</text>
    </g>
  </g>

  <!-- Blue Solid Arrow: Common Model Area -> Sim Adapter (Downward) -->
  <path d="M 280 134 L 280 230" fill="none" stroke="#2563EB" stroke-width="2" marker-end="url(#arrow-blue)"/>
  <rect x="250" y="172" width="60" height="16" rx="3" fill="#FFFFFF" stroke="#BFDBFE" stroke-width="0.8"/>
  <text x="280" y="184" text-anchor="middle" font-size="9.5" font-weight="bold" fill="#1D4ED8">下发仿真命令</text>


  <!-- ==================== MAIN CLOSED LOOP FEEDBACK ==================== -->
  <!-- Green Solid Loop Arrow: Sim Adapter -> LLM Decision -->
  <!-- Sim Adapter returns feedback to LLM, forming the unmistakable closed-loop arrow! -->
  <path d="M 195 295 C 100 295, 87 280, 87 250" fill="none" stroke="#16A34A" stroke-width="2.5" marker-end="url(#arrow-green)"/>
  
  <!-- Feedback Label Callout -->
  <g transform="translate(60, 275)">
    <rect x="0" y="0" width="130" height="34" rx="4" fill="#F0FDF4" stroke="#86EFAC" stroke-width="1.2" filter="url(#badge-shadow)"/>
    <text x="65" y="14" text-anchor="middle" font-size="10.5" font-weight="bold" fill="#15803D">模拟反馈（闭环驱动）</text>
    <text x="65" y="27" text-anchor="middle" font-size="9" fill="#047857">状态 · 工艺数据 · 约束结果</text>
  </g>


  <!-- ==================== RIGHT: REAL HARDWARE BRANCH (实机执行分支) ==================== -->
  
  <!-- Gateway: 满足预设上机条件 (Diamond / Hexagon Style) -->
  <g id="gateway" transform="translate(565, 50)">
    <!-- Gateway Box -->
    <polygon points="55,0 110,24 110,56 55,80 0,56 0,24" fill="#FEF3C7" stroke="#D97706" stroke-width="1.6" filter="url(#badge-shadow)"/>
    <text x="55" y="28" text-anchor="middle" font-size="10.5" font-weight="bold" fill="#92400E">满足预设</text>
    <text x="55" y="44" text-anchor="middle" font-size="10.5" font-weight="bold" fill="#92400E">上机条件？</text>
    <text x="55" y="58" text-anchor="middle" font-size="8.5" fill="#B45309">（验证通过）</text>
  </g>

  <!-- Green Dashed Arrow from Simulation Result to Gateway as evaluation basis -->
  <path d="M 440 230 C 490 200, 520 160, 565 105" fill="none" stroke="#16A34A" stroke-width="1.8" stroke-dasharray="4 3" marker-end="url(#arrow-green)"/>
  <rect x="460" y="160" width="86" height="28" rx="3" fill="#FFFFFF" stroke="#86EFAC" stroke-width="0.8"/>
  <text x="503" y="173" text-anchor="middle" font-size="9" font-weight="bold" fill="#15803D">仿真评估通过</text>
  <text x="503" y="184" text-anchor="middle" font-size="8.5" fill="#64748B">作为上机依据</text>

  <!-- Blue Arrow from Common Model Area to Gateway -->
  <path d="M 545 78 L 565 78" fill="none" stroke="#2563EB" stroke-width="2" marker-end="url(#arrow-blue)"/>

  <!-- From Gateway to Real Adapter (Downward) -->
  <path d="M 620 130 L 620 180" fill="none" stroke="#2563EB" stroke-width="2" marker-end="url(#arrow-blue)"/>
  <rect x="625" y="148" width="48" height="16" rx="3" fill="#FFFFFF" stroke="#BFDBFE" stroke-width="0.8"/>
  <text x="649" y="160" text-anchor="middle" font-size="9.5" font-weight="bold" fill="#1D4ED8">是 (Yes)</text>


  <!-- REAL HARDWARE BLOCK: 实机 Adapter + 物理设备 -->
  <g id="real-hardware" transform="translate(545, 180)">
    <!-- Container -->
    <rect x="0" y="0" width="172" height="185" rx="6" fill="#F8FAFC" stroke="#0F172A" stroke-width="1.6" filter="url(#card-shadow)"/>
    
    <!-- Header -->
    <rect x="0" y="0" width="172" height="22" rx="6" fill="#0F172A"/>
    <text x="86" y="15" text-anchor="middle" font-size="11.5" font-weight="bold" fill="#FFFFFF">实机执行系统</text>

    <!-- Sub 1: 实机 Adapter -->
    <g transform="translate(10, 30)">
      <rect x="0" y="0" width="152" height="54" rx="4" fill="#FFFFFF" stroke="#059669" stroke-width="1.2"/>
      <text x="76" y="17" text-anchor="middle" font-size="11" font-weight="bold" fill="#065F46">实机 Adapter</text>
      <text x="76" y="32" text-anchor="middle" font-size="9" fill="#64748B">下发真实物理指令</text>
      <text x="76" y="45" text-anchor="middle" font-size="9" fill="#059669">真实通信报文封包</text>
    </g>

    <!-- Control to physical -->
    <path d="M 86 84 L 86 102" stroke="#2563EB" stroke-width="1.5" marker-end="url(#arrow-blue)"/>

    <!-- Sub 2: 物理设备 -->
    <g transform="translate(10, 102)">
      <rect x="0" y="0" width="152" height="54" rx="4" fill="#1E293B" stroke="#0F172A" stroke-width="1.2"/>
      <text x="76" y="18" text-anchor="middle" font-size="11" font-weight="bold" fill="#F8FAFC">真实物理仪器</text>
      <text x="76" y="33" text-anchor="middle" font-size="9" fill="#94A3B8">反应釜 / 机器人 / PLC</text>
      <text x="76" y="46" text-anchor="middle" font-size="8.5" fill="#38BDF8">执行真实物理化学实验</text>
    </g>

    <!-- Note -->
    <text x="86" y="174" text-anchor="middle" font-size="8.5" font-weight="bold" fill="#475569">真实世界状态收敛</text>
  </g>

  <!-- Real Hardware Feedback back into Common Model Area -->
  <path d="M 545 220 C 525 220, 520 180, 520 134" fill="none" stroke="#16A34A" stroke-width="2" marker-end="url(#arrow-green)"/>
  <rect x="460" y="195" width="76" height="16" rx="3" fill="#FFFFFF" stroke="#86EFAC" stroke-width="0.8"/>
  <text x="498" y="206" text-anchor="middle" font-size="8.5" font-weight="bold" fill="#15803D">实机状态反馈</text>

  <!-- Bottom Core Architectural Takeaway Ribbon -->
  <g transform="translate(195, 370)">
    <rect x="0" y="0" width="310" height="15" rx="3" fill="#EFF6FF"/>
    <text x="155" y="11.5" text-anchor="middle" font-size="9.5" font-weight="bold" fill="#1E40AF">★ 统一架构：仿真与实机仅在 Adapter 区分，绝无两套业务逻辑</text>
  </g>

</svg>'''
    return svg

def main():
    fig1 = generate_figure_1_svg()
    fig2 = generate_figure_2_svg()
    fig3 = generate_figure_3_svg()

    path1 = os.path.join(OUTPUT_DIR, "figure_1_unified_execution.svg")
    path2 = os.path.join(OUTPUT_DIR, "figure_2_heating_sequence.svg")
    path3 = os.path.join(OUTPUT_DIR, "figure_3_simulation_closed_loop.svg")

    with open(path1, "w", encoding="utf-8") as f:
        f.write(fig1)
    with open(path2, "w", encoding="utf-8") as f:
        f.write(fig2)
    with open(path3, "w", encoding="utf-8") as f:
        f.write(fig3)

    print(f"Generated SVGs:\n1: {path1}\n2: {path2}\n3: {path3}")

if __name__ == "__main__":
    main()
