# -*- coding: utf-8 -*-
"""
Generate high-resolution architecture diagrams for SmartLab 2.0:
1. SmartLab2.0_系统全局分层拓扑架构.png & .svg
2. SmartLab2.0_端到端执行流与数据流.png & .svg
Stored in D:\\SmartLab2.0\\doc\\generated_images\\
"""

import os
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch

plt.rcParams['font.sans-serif'] = ['Microsoft YaHei', 'SimHei', 'Arial', 'DejaVu Sans']
plt.rcParams['axes.unicode_minus'] = False

OUTPUT_DIR = r"D:\SmartLab2.0\doc\generated_images"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# -------------------------------------------------------------
# 辅助绘图函数
# -------------------------------------------------------------
def draw_card(ax, x, y, w, h, title="", subtitle="", fc="#FFFFFF", ec="#CBD5E1", 
              lw=1.2, radius=0.8, title_color="#0F172A", subtitle_color="#64748B", 
              title_size=9.5, sub_size=7.5, zorder=2):
    """绘制带圆角的容器卡片"""
    patch = FancyBboxPatch((x, y), w, h, boxstyle=f"round,pad=0,rounding_size={radius}",
                           fc=fc, ec=ec, lw=lw, zorder=zorder)
    ax.add_patch(patch)
    if title:
        ty = y + h - (1.6 if subtitle else h/2.0)
        ax.text(x + w/2.0, ty, title, ha='center', va='center', fontsize=title_size,
                fontweight='bold', color=title_color, zorder=zorder+1)
    if subtitle:
        sy = y + 1.4
        ax.text(x + w/2.0, sy, subtitle, ha='center', va='center', fontsize=sub_size,
                color=subtitle_color, zorder=zorder+1)
    return patch

def draw_pill(ax, x, y, w, h, text, fc="#EEF2F6", ec="#94A3B8", tc="#334155", 
              fs=7.5, bold=False, zorder=3):
    """绘制小徽章/标签"""
    patch = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0,rounding_size=0.4",
                           fc=fc, ec=ec, lw=0.9, zorder=zorder)
    ax.add_patch(patch)
    ax.text(x + w/2.0, y + h/2.0, text, ha='center', va='center', fontsize=fs,
            fontweight='bold' if bold else 'normal', color=tc, zorder=zorder+1)
    return patch

def draw_arrow(ax, p1, p2, color="#2563EB", lw=1.6, style="-|>", mutation_scale=11,
               dashed=False, zorder=5, label="", label_pos=(0, 0), label_color=None, label_fs=7.2):
    """绘制平滑箭头与标注"""
    ls = '--' if dashed else '-'
    if label_color is None:
        label_color = color
    arrow = FancyArrowPatch(p1, p2, arrowstyle=style, mutation_scale=mutation_scale,
                            linewidth=lw, color=color, linestyle=ls, zorder=zorder)
    ax.add_patch(arrow)
    if label:
        lx = (p1[0] + p2[0]) / 2.0 + label_pos[0]
        ly = (p1[1] + p2[1]) / 2.0 + label_pos[1]
        bbox = dict(boxstyle='round,pad=0.25', facecolor='#FFFFFF', edgecolor=color, alpha=0.95, lw=0.8)
        ax.text(lx, ly, label, ha='center', va='center', fontsize=label_fs,
                fontweight='bold', color=label_color, bbox=bbox, zorder=zorder+2)
    return arrow


# =============================================================
# 1. 生成：SmartLab2.0 系统全局分层拓扑架构图 (完美优化版)
# =============================================================
def generate_layered_topology_diagram():
    fig, ax = plt.subplots(figsize=(26, 17), dpi=300)
    fig.patch.set_facecolor('#F8FAFC')
    ax.set_facecolor('#F8FAFC')
    ax.set_xlim(0, 120)
    ax.set_ylim(0, 100)
    ax.axis('off')

    # 主标题栏
    ax.text(60, 98.2, "SmartLab 2.0 智能实验室系统全局分层拓扑架构", ha='center', va='center',
            fontsize=20, fontweight='bold', color='#0F172A', zorder=10)
    ax.text(60, 96.2, "任务导向型可执行建模 · 权威内存快照驱动 · 控制流与数据流正交解耦 · 软硬件闭环联锁",
            ha='center', va='center', fontsize=11, color='#475569', zorder=10)

    # ---------------------------------------------------------
    # 分层 1: 前端交互与可视化层 (Vue 3 + Pinia + Element Plus + Vue Flow)
    # ---------------------------------------------------------
    draw_card(ax, 3, 84.8, 114, 9.5, title="", fc="#F1F5F9", ec="#94A3B8", lw=1.5, radius=1.0, zorder=1)
    ax.text(5.5, 92.5, "前端交互与可视化层 (Vue 3 + Pinia + Element Plus + Vue Flow)",
            fontsize=12, fontweight='bold', color='#1E293B', zorder=3)
    ax.text(115.5, 92.5, "统一单页应用 (SPA) · REST API 交互 · SSE 实时推送流",
            fontsize=9, color='#64748B', ha='right', zorder=3)

    ui_modules = [
        ("工作流设计与监控\nWorkflow Designer", "Vue Flow 画布 / 步骤拓扑 / 活边渲染\n甘特图 / 执行监控抽屉", "#EFF6FF", "#3B82F6", "#1E40AF"),
        ("设备模型与状态机管理\nDevice Model & SM", "属性空间 / 操作能力 / 契约绑定\n双状态空间定义 / 状态转移矩阵", "#F0FDF4", "#22C55E", "#15803D"),
        ("全局安全约束管理\nConstraint Manager", "多源可观测绑定 / SpEL 表达式求值\n持续防抖时间窗口 / 联动处置闭环", "#FEF2F2", "#EF4444", "#B91C1C"),
        ("设备实时调试控制台\nDevice Manual Console", "人工点动干预 / 指令直接下发\n内置约束异常锁存解除 / 强制复位", "#FFFBEB", "#F59E0B", "#B45309"),
        ("数据中心与模板管理\nData Management", "实验数据模板配置 / 动态数据集映射\n时序遥测记录检索 / 结果导出追溯", "#FAF5FF", "#A855F7", "#6B21A8"),
    ]
    for i, (title, desc, fc, ec, tc) in enumerate(ui_modules):
        x = 5.0 + i * 22.8
        draw_card(ax, x, 85.6, 21.5, 5.8, title="", fc=fc, ec=ec, lw=1.2, radius=0.6, zorder=2)
        lines = title.split('\n')
        ax.text(x + 10.75, 89.7, lines[0], ha='center', va='center', fontsize=9.2, fontweight='bold', color=tc, zorder=3)
        ax.text(x + 10.75, 88.5, lines[1], ha='center', va='center', fontsize=7.2, color='#64748B', zorder=3)
        dlines = desc.split('\n')
        ax.text(x + 10.75, 87.1, dlines[0], ha='center', va='center', fontsize=7.0, color='#334155', zorder=3)
        ax.text(x + 10.75, 86.1, dlines[1], ha='center', va='center', fontsize=7.0, color='#334155', zorder=3)

    # ---------------------------------------------------------
    # 分层 2: 后端核心引擎层 (Spring Boot 3 运行时)
    # ---------------------------------------------------------
    draw_card(ax, 3, 35.5, 114, 46.5, title="", fc="#F8FAFC", ec="#64748B", lw=1.6, radius=1.2, zorder=1)
    ax.text(5.5, 80.4, "后端核心引擎层 (Spring Boot 3 / JDK 17 高性能运行时)",
            fontsize=12.5, fontweight='bold', color='#0F172A', zorder=3)
    ax.text(115.5, 80.4, "权威内存快照中心 · 闭环生命周期管理 · 旁路软实时求值",
            fontsize=9.2, color='#475569', ha='right', zorder=3)

    # 2.1 工作流引擎 (Workflow Engine)
    draw_card(ax, 5.0, 58.5, 34.5, 20.0, title="", fc="#FFFFFF", ec="#3B82F6", lw=1.3, radius=0.8, zorder=2)
    ax.text(6.5, 76.8, "1. 工作流编排引擎 (Workflow Engine)", fontsize=10.0, fontweight='bold', color="#1D4ED8", zorder=3)
    draw_pill(ax, 28.5, 76.1, 10.0, 1.5, "100ms 轮询流水线", fc="#DBEAFE", ec="#93C5FD", tc="#1E40AF", fs=7.2, bold=True)
    
    wfe_items = [
        ("WorkflowEngine (核心驱动)", "100ms 周期冻结快照 · 接口上升沿求值 · 动作分发", "#EFF6FF", "#BFDBFE"),
        ("WorkflowTaskControlService", "任务启动 / 暂停 / 恢复 / 5步优雅终止收敛 (ABORT)", "#EFF6FF", "#BFDBFE"),
        ("WorkflowConditionEvaluator", "触发器条件求值 · 动作内联执行 (UPDATE/EMIT)", "#EFF6FF", "#BFDBFE"),
        ("WorkflowDefinitionCompiler", "模型静态契约编译 · 拓扑规范化 · 设备槽位需求解析", "#EFF6FF", "#BFDBFE"),
    ]
    for idx, (title, desc, fc, ec) in enumerate(wfe_items):
        y = 72.0 - idx * 4.2
        draw_card(ax, 6.2, y, 32.1, 3.5, title="", fc=fc, ec=ec, lw=0.9, radius=0.5, zorder=3)
        ax.text(7.2, y + 2.3, title, fontsize=8.2, fontweight='bold', color="#1E3A8A", zorder=4)
        ax.text(7.2, y + 1.0, desc, fontsize=7.0, color="#475569", zorder=4)

    # 2.2 连接通道体系 (Connection Channels)
    draw_card(ax, 41.5, 58.5, 34.5, 20.0, title="", fc="#FFFFFF", ec="#0284C7", lw=1.3, radius=0.8, zorder=2)
    ax.text(43.0, 76.8, "2. 通道拓扑体系 (Connection Channels)", fontsize=10.0, fontweight='bold', color="#0369A1", zorder=3)
    draw_pill(ax, 65.0, 76.1, 10.0, 1.5, "控制与数据正交解耦", fc="#E0F2FE", ec="#7DD3FC", tc="#075985", fs=7.2, bold=True)

    chn_items = [
        ("InterfaceConnectionForwarder", "控制信号活边 (Live Edge) · 节点间/节点与设备双向路由", "#F0F9FF", "#BAE6FD"),
        ("PortConnectionPuller", "数据/物料流 · 下游活跃步骤按需主动拉取 (上游只读冻结)", "#F0F9FF", "#BAE6FD"),
        ("InterfaceConnectionIndex", "运行时拓扑活边动态索引 · 槽位占用与原路写回查找", "#F0F9FF", "#BAE6FD"),
        ("InterfaceConnectionCatalog", "静态模型连接规范解析 (NODE_TO_NODE / DEV)", "#F0F9FF", "#BAE6FD"),
    ]
    for idx, (title, desc, fc, ec) in enumerate(chn_items):
        y = 72.0 - idx * 4.2
        draw_card(ax, 42.7, y, 32.1, 3.5, title="", fc=fc, ec=ec, lw=0.9, radius=0.5, zorder=3)
        ax.text(43.7, y + 2.3, title, fontsize=8.2, fontweight='bold', color="#0C4A6E", zorder=4)
        ax.text(43.7, y + 1.0, desc, fontsize=7.0, color="#475569", zorder=4)

    # 2.3 设备状态机引擎 (State Machine Engine)
    draw_card(ax, 78.0, 58.5, 37.5, 20.0, title="", fc="#FFFFFF", ec="#16A34A", lw=1.3, radius=0.8, zorder=2)
    ax.text(79.5, 76.8, "3. 设备状态机引擎 (State Machine Engine)", fontsize=10.0, fontweight='bold', color="#15803D", zorder=3)
    draw_pill(ax, 103.5, 76.1, 11.0, 1.5, "按物理实例绝对隔离", fc="#DCFCE7", ec="#86EFAC", tc="#166534", fs=7.2, bold=True)

    sme_items = [
        ("DeviceStateMachineRuntime", "单设备实例内存独立运行时 · 指令槽排他锁 · 状态原子跃迁", "#F0FDF4", "#BBF7D0"),
        ("CMD 状态空间 (指令生命周期)", "IDLE → SENT → RUNNING → COMPLETED / ABORTED", "#F0FDF4", "#BBF7D0"),
        ("OP 状态空间 (业务与异常)", "OPERATIONAL (互斥单状态) + EXCEPTION (异常状态多活动集合)", "#F0FDF4", "#BBF7D0"),
        ("IntrinsicConstraintMonitor", "设备物理内置硬约束 · 违规进入 EXCEPTION 并强锁存 (人工解除)", "#F0FDF4", "#BBF7D0"),
    ]
    for idx, (title, desc, fc, ec) in enumerate(sme_items):
        y = 72.0 - idx * 4.2
        draw_card(ax, 79.2, y, 35.1, 3.5, title="", fc=fc, ec=ec, lw=0.9, radius=0.5, zorder=3)
        ax.text(80.2, y + 2.3, title, fontsize=8.2, fontweight='bold', color="#14532D", zorder=4)
        ax.text(80.2, y + 1.0, desc, fontsize=7.0, color="#475569", zorder=4)

    # 2.4 统一可观测空间 (Observation Space)
    draw_card(ax, 5.0, 37.0, 42.0, 19.0, title="", fc="#FFFFFF", ec="#D97706", lw=1.3, radius=0.8, zorder=2)
    ax.text(6.5, 54.2, "4. 统一可观测空间 (Observation Space)", fontsize=10.0, fontweight='bold', color="#B45309", zorder=3)
    draw_pill(ax, 35.5, 53.5, 10.5, 1.5, "权威内存数据底盘", fc="#FEF3C7", ec="#FDE68A", tc="#92400E", fs=7.2, bold=True)

    obs_items = [
        ("DeviceTwinSnapshotRegistry", "传感器属性最新快照 (DEVICE_ATTRIBUTE) · 权威纳秒级读取", "#FFFBEB", "#FDE68A"),
        ("StateMachineObservationRegistry", "双状态机快照 (DEVICE_OPERATION_STATE / CMD_LIFECYCLE)", "#FFFBEB", "#FDE68A"),
        ("ObservationHistoryStore", "1800s / 4096 点有界时序历史缓存 (支撑 delta / avg / rate)", "#FFFBEB", "#FDE68A"),
    ]
    for idx, (title, desc, fc, ec) in enumerate(obs_items):
        y = 48.8 - idx * 4.8
        draw_card(ax, 6.2, y, 39.6, 4.0, title="", fc=fc, ec=ec, lw=0.9, radius=0.5, zorder=3)
        ax.text(7.2, y + 2.6, title, fontsize=8.2, fontweight='bold', color="#78350F", zorder=4)
        ax.text(7.2, y + 1.2, desc, fontsize=7.0, color="#475569", zorder=4)

    # 2.5 旁路约束引擎 (Constraint Engine)
    draw_card(ax, 49.0, 37.0, 44.0, 19.0, title="", fc="#FFFFFF", ec="#DC2626", lw=1.3, radius=0.8, zorder=2)
    ax.text(50.5, 54.2, "5. 旁路约束引擎 (Constraint Engine)", fontsize=10.0, fontweight='bold', color="#B91C1C", zorder=3)
    draw_pill(ax, 81.5, 53.5, 10.5, 1.5, "安全联锁与工艺监控", fc="#FEE2E2", ec="#FECACA", tc="#991B1B", fs=7.2, bold=True)

    ce_items = [
        ("ConstraintEvaluationCoordinator", "双轨调度器: ObservableChangedEvent 事件驱动 + 200ms 定时扫描", "#FEF2F2", "#FECACA"),
        ("ConstraintEngine & ExpressionEvaluator", "SpEL 逻辑/算术求值 · 滑动时序窗口 delta(var,s) / avg / rate", "#FEF2F2", "#FECACA"),
        ("违规处置闭环 (Violation Actions)", "SYSTEM (ABORT/PAUSE/ALERT) + 设备保护能力 (CONSTRAINT_EXECUTE)", "#FEF2F2", "#FECACA"),
    ]
    for idx, (title, desc, fc, ec) in enumerate(ce_items):
        y = 48.8 - idx * 4.8
        draw_card(ax, 50.2, y, 41.6, 4.0, title="", fc=fc, ec=ec, lw=0.9, radius=0.5, zorder=3)
        ax.text(51.2, y + 2.6, title, fontsize=8.2, fontweight='bold', color="#7F1D1D", zorder=4)
        ax.text(51.2, y + 1.2, desc, fontsize=7.0, color="#475569", zorder=4)

    # 2.6 实时推送中心 (SSE Hubs)
    draw_card(ax, 95.0, 37.0, 20.5, 19.0, title="", fc="#FFFFFF", ec="#9333EA", lw=1.3, radius=0.8, zorder=2)
    ax.text(96.2, 54.2, "实时推送中心 (SSE)", fontsize=10.0, fontweight='bold', color="#7E22CE", zorder=3)
    sse_items = [
        ("TaskExecutionSseHub", "任务拓扑节点状态\n执行日志实时推流", "#FAF5FF", "#E9D5FF"),
        ("DeviceConsoleSseHub", "设备状态机信号\n控制台调试终端直通", "#FAF5FF", "#E9D5FF"),
    ]
    for idx, (title, desc, fc, ec) in enumerate(sse_items):
        y = 46.2 - idx * 7.5
        draw_card(ax, 96.0, y, 18.5, 6.5, title="", fc=fc, ec=ec, lw=0.9, radius=0.5, zorder=3)
        ax.text(97.0, y + 4.8, title, fontsize=8.2, fontweight='bold', color="#581C87", zorder=4)
        dls = desc.split('\n')
        ax.text(97.0, y + 3.0, dls[0], fontsize=7.0, color="#475569", zorder=4)
        ax.text(97.0, y + 1.5, dls[1], fontsize=7.0, color="#475569", zorder=4)

    # ---------------------------------------------------------
    # 下半部重新规划：左侧数据库，右侧通信与边缘硬件
    # 彻底解决对角线穿插碰撞问题！
    # ---------------------------------------------------------
    
    # 3. 数据持久化层 (PostgreSQL 15+ 关系与 JSONB 存储) -> 放在左侧 (x: 3 to 48)
    draw_card(ax, 3, 2.0, 44.0, 31.5, title="", fc="#FAF5FF", ec="#8B5CF6", lw=1.4, radius=1.0, zorder=1)
    ax.text(5.5, 31.8, "数据持久化层 (PostgreSQL 15+ 关系与 JSONB 存储)", fontsize=11.0, fontweight='bold', color='#5B21B6', zorder=3)
    ax.text(45.0, 31.8, "冷启动恢复 · 归档 · 审计", fontsize=7.8, color='#6D28D9', ha='right', zorder=3)

    db_tables = [
        ("设备与物模型表群", "• DEVICE_MODELS: 模型定义 / capabilities (JSONB)\n• DEVICE_INSTANCES: 物理实例 / bound_adapter_name\n• DEVICE_TWIN_STATES: current_cmd_state / current_attr"),
        ("工作流与执行表群", "• FLOW_MODELS: 流程图拓扑 / interface_connection\n• FLOW_NODE: 节点定义 / capability / in_variables\n• TASK / TASK_STEP: 执行实例 / 接口快照 / variable_space\n• EXECUTION_LOG: 执行日志全记录"),
        ("安全约束与审计表群", "• CONSTRAINT_RULE: 表达式 / bindings / window_seconds\n• VIOLATION_LOG: 违约现场全量变量快照 / 处置措施"),
        ("动态数据时序表群", "• ADAPTER_INDEX: 适配器索引 / parsed_config (JSONB)\n• DATA_INDEX / DATA_TEMPLATE: 业务数据模板定义\n• DATA_RECORD_*: 传感器采集时序记录动态分表"),
    ]
    for idx, (title, desc) in enumerate(db_tables):
        y = 25.0 - idx * 7.0
        draw_card(ax, 4.5, y, 41.0, 6.2, title="", fc="#FFFFFF", ec="#C4B5FD", lw=0.9, radius=0.5, zorder=2)
        ax.text(5.7, y + 4.8, title, fontsize=8.2, fontweight='bold', color='#4C1D95', zorder=3)
        lines = desc.split('\n')
        for l_idx, line in enumerate(lines):
            ax.text(5.7, y + 3.4 - l_idx * 1.2, line, fontsize=6.5, color='#374151', zorder=3)

    # 4. 边缘通信层 (MQTT Broker & Gateway) -> 放在右上方 (x: 49 to 117)
    draw_card(ax, 49.0, 19.5, 68.0, 14.0, title="", fc="#F1F5F9", ec="#475569", lw=1.4, radius=1.0, zorder=1)
    ax.text(51.0, 31.8, "边缘通信层 (MQTT Broker & Gateway)", fontsize=11.0, fontweight='bold', color='#1E293B', zorder=3)
    ax.text(115.5, 31.8, "标准五类 MQTT 报文契约 · Paho MQTT 异步连接池", fontsize=8.2, color='#475569', ha='right', zorder=3)

    draw_card(ax, 50.5, 21.0, 20.5, 9.2, title="", fc="#FFFFFF", ec="#94A3B8", lw=0.9, radius=0.5, zorder=2)
    ax.text(51.5, 28.6, "MqttAdapterMessagingService", fontsize=8.0, fontweight='bold', color='#0F172A', zorder=3)
    ax.text(51.5, 26.8, "• MQTT Client 自动重连与订阅保活", fontsize=6.8, color='#475569', zorder=3)
    ax.text(51.5, 25.2, "• 适配器上线注册审核 (register)", fontsize=6.8, color='#475569', zorder=3)
    ax.text(51.5, 23.6, "• 心跳监测 (heartbeat) / 遥测下发", fontsize=6.8, color='#475569', zorder=3)

    draw_card(ax, 72.5, 21.0, 20.5, 9.2, title="", fc="#FFFFFF", ec="#94A3B8", lw=0.9, radius=0.5, zorder=2)
    ax.text(73.5, 28.6, "AdapterPayloadMapperService", fontsize=8.0, fontweight='bold', color='#0F172A', zorder=3)
    ax.text(73.5, 26.8, "• 物理点位到物模型属性双向映射", fontsize=6.8, color='#475569', zorder=3)
    ax.text(73.5, 25.2, "• internal=true 硬件私有参数注入", fontsize=6.8, color='#475569', zorder=3)
    ax.text(73.5, 23.6, "• 报文契约校验 (Schema Validation)", fontsize=6.8, color='#475569', zorder=3)

    draw_card(ax, 94.5, 21.0, 21.0, 9.2, title="", fc="#FFFFFF", ec="#0284C7", lw=1.1, radius=0.5, zorder=2)
    ax.text(95.5, 28.6, "EMQX / Mosquitto MQTT Broker", fontsize=8.0, fontweight='bold', color='#0369A1', zorder=3)
    ax.text(95.5, 26.8, "• smartlab/adapter/{name}/{pt}/command", fontsize=6.3, color='#0284C7', zorder=3)
    ax.text(95.5, 25.2, "• smartlab/adapter/{name}/{pt}/telemetry", fontsize=6.3, color='#059669', zorder=3)
    ax.text(95.5, 23.6, "• smartlab/adapter/{name}/{pt}/event", fontsize=6.3, color='#D97706', zorder=3)
    ax.text(95.5, 22.2, "• registerTopic & heartbeatTopic", fontsize=6.3, color='#475569', zorder=3)

    # 5. 边缘驱动接入与物理硬件层 -> 放在右下方 (x: 49 to 117)
    draw_card(ax, 49.0, 2.0, 68.0, 16.0, title="", fc="#F8FAFC", ec="#059669", lw=1.4, radius=1.0, zorder=1)
    ax.text(51.0, 16.5, "边缘驱动接入与物理硬件层 (Edge Adapters & Physical Instruments)",
            fontsize=11.0, fontweight='bold', color='#065F46', zorder=3)
    ax.text(115.5, 16.5, "标准四层 Adapter 架构 · 屏蔽硬件差异 · 闭环控制", fontsize=8.2, color='#047857', ha='right', zorder=3)

    draw_card(ax, 50.5, 3.0, 36.5, 12.2, title="", fc="#FFFFFF", ec="#10B981", lw=1.1, radius=0.6, zorder=2)
    ax.text(51.5, 13.8, "Edge Adapter 边缘驱动进程 (Python / C++)", fontsize=8.5, fontweight='bold', color='#065F46', zorder=3)
    
    adpt_layers = [
        ("runtime.py (生命周期管理)", "进程加载 · 配置解析 · 异常退出看门狗 · setup.ini 私有配置加载", "#ECFDF5", "#A7F3D0"),
        ("northbound.py (北向 MQTT 通信)", "MQTT Broker 连接 · 订阅命令 · 组装发布遥测/事件 (无硬件逻辑)", "#ECFDF5", "#A7F3D0"),
        ("core.py (核心控制与语义转换)", "系统指令到硬件动作转换 · 状态跟踪 · 内部点位映射与参数注入", "#ECFDF5", "#A7F3D0"),
        ("southbound.py (南向物理驱动)", "Modbus-TCP / 串口 RS485 / 厂商 SDK / DLL (无 SmartLab 格式)", "#ECFDF5", "#A7F3D0"),
    ]
    for idx, (title, desc, fc, ec) in enumerate(adpt_layers):
        y = 11.2 - idx * 2.6
        draw_card(ax, 51.5, y, 34.5, 2.3, title="", fc=fc, ec=ec, lw=0.8, radius=0.4, zorder=3)
        ax.text(52.2, y + 1.4, title, fontsize=7.2, fontweight='bold', color='#064E3B', zorder=4)
        ax.text(52.2, y + 0.5, desc, fontsize=6.2, color='#374151', zorder=4)

    draw_card(ax, 88.5, 3.0, 27.0, 12.2, title="", fc="#FFFFFF", ec="#64748B", lw=1.1, radius=0.6, zorder=2)
    ax.text(89.5, 13.8, "物理实验室仪器装备 (Physical Equipment)", fontsize=8.5, fontweight='bold', color='#1E293B', zorder=3)
    dev_items = [
        ("自动化反应釜 / 加热搅拌器", "温度、转速连续控制 / 超温硬件闭环保护"),
        ("高精度加样液体工作站", "微升注射泵 / 称量天平 / 进样机械臂"),
        ("在线分析仪器 (HPLC / UV-Vis)", "色谱工作站联机 / 实时光谱数据采集"),
        ("PLC / 工控卡 / 传感器网络", "汇川/西门子 PLC / 4-20mA 模拟量 / 继电器"),
    ]
    for idx, (title, desc) in enumerate(dev_items):
        y = 11.2 - idx * 2.6
        draw_card(ax, 89.5, y, 25.0, 2.3, title="", fc="#F8FAFC", ec="#E2E8F0", lw=0.8, radius=0.4, zorder=3)
        ax.text(90.2, y + 1.4, title, fontsize=7.2, fontweight='bold', color='#0F172A', zorder=4)
        ax.text(90.2, y + 0.5, desc, fontsize=6.2, color='#64748B', zorder=4)

    # ---------------------------------------------------------
    # 跨层交互连接线与信号标注 (正交直连，绝对零交叉碰撞！)
    # ---------------------------------------------------------
    # 1. UI -> WFE: 垂直向下
    draw_arrow(ax, (16.0, 85.6), (16.0, 78.5), color="#2563EB", lw=1.6, label="1. 启动任务 / 编排下发", label_pos=(0, 0))
    # SSE -> UI: 垂直向上
    draw_arrow(ax, (105.0, 56.0), (105.0, 85.6), color="#9333EA", lw=1.6, dashed=True, label="SSE 实时推流", label_pos=(0, 0))

    # WFE -> Channels: 水平向右
    draw_arrow(ax, (39.5, 68.5), (41.5, 68.5), color="#2563EB", lw=1.8, label="EMIT 信号", label_pos=(0, 1.0))
    # Channels -> SME: 水平向右
    draw_arrow(ax, (76.0, 68.5), (78.0, 68.5), color="#2563EB", lw=1.8, label="活边转发", label_pos=(0, 1.0))

    # 2. SME 下发命令到通信层: 垂直向下直达 MAMS / APMS！完美对齐！
    draw_arrow(ax, (83.0, 58.5), (83.0, 33.5), color="#2563EB", lw=1.8, label="2. 下发 CMD_START/ABORT", label_pos=(0, 0))
    # 通信层事件回传到 SME: 垂直向上直达！
    draw_arrow(ax, (89.0, 33.5), (89.0, 58.5), color="#059669", lw=1.8, dashed=True, label="6. 事件驱动状态迁移", label_pos=(0, 0))

    # 3. 通信层下发指令到边缘适配器: 垂直向下
    draw_arrow(ax, (65.0, 19.5), (65.0, 15.2), color="#2563EB", lw=1.8, label="3. MQTT 指令帧", label_pos=(0, 0))
    # 4. 边缘适配器上报遥测到通信层: 垂直向上
    draw_arrow(ax, (75.0, 15.2), (75.0, 19.5), color="#059669", lw=1.8, dashed=True, label="4. 遥测/事件上报", label_pos=(0, 0))

    # 适配器驱动物理设备 (水平交互)
    draw_arrow(ax, (87.0, 9.5), (88.5, 9.5), color="#2563EB", lw=1.5, label="物理驱动", label_pos=(0, 1.0))
    draw_arrow(ax, (88.5, 6.5), (87.0, 6.5), color="#059669", lw=1.5, dashed=True, label="采样反馈", label_pos=(0, 1.0))

    # 5. 通信层遥测更新到观测空间与数据库 (水平向左直通，绝不交叉！)
    draw_arrow(ax, (49.0, 26.0), (44.0, 26.0), color="#7C3AED", lw=1.8, dashed=True, label="时序落库 (DATA_RECORD)", label_pos=(0, 1.0))
    draw_arrow(ax, (50.5, 30.2), (38.0, 37.0), color="#D97706", lw=1.8, dashed=True, label="5. 遥测写快照 (TWIN)", label_pos=(0, 1.2))

    # 7. 观测空间唤醒约束引擎: 水平向右
    draw_arrow(ax, (47.0, 46.5), (49.0, 46.5), color="#DC2626", lw=1.8, label="7. 观测变化事件", label_pos=(0, 1.0))

    # 8. 约束引擎违规处置: 系统级处置到 WFE (向左上方)
    draw_arrow(ax, (50.0, 56.0), (38.0, 58.5), color="#DC2626", lw=1.8, dashed=True, label="8. SYSTEM:ABORT", label_pos=(0, 1.8))
    # 9. 约束引擎设备保护: 到 SME (向右上方)
    draw_arrow(ax, (80.0, 56.0), (80.0, 58.5), color="#DC2626", lw=1.8, dashed=True, label="9. 保护动作", label_pos=(0, 1.2))

    # 后端引擎到数据库异步落库: 垂直向下直通
    draw_arrow(ax, (25.0, 37.0), (25.0, 33.5), color="#7C3AED", lw=1.6, dashed=True, label="任务执行归档", label_pos=(0, 0))

    png_path = os.path.join(OUTPUT_DIR, "SmartLab2.0_系统全局分层拓扑架构.png")
    svg_path = os.path.join(OUTPUT_DIR, "SmartLab2.0_系统全局分层拓扑架构.svg")
    plt.savefig(png_path, dpi=300, bbox_inches='tight')
    plt.savefig(svg_path, format='svg', bbox_inches='tight')
    plt.close()
    print(f"[OK] Generated: {png_path}")
    print(f"[OK] Generated: {svg_path}")


# =============================================================
# 2. 生成：SmartLab2.0 端到端执行流与数据流时序交互图
# =============================================================
def generate_execution_and_data_flow_diagram():
    fig, ax = plt.subplots(figsize=(26, 22), dpi=300)
    fig.patch.set_facecolor('#F8FAFC')
    ax.set_facecolor('#F8FAFC')
    ax.set_xlim(0, 125)
    ax.set_ylim(0, 100)
    ax.axis('off')

    # 主标题栏
    ax.text(62.5, 98.4, "SmartLab 2.0 端到端执行流与数据流时序交互图 (Execution & Data Flow Sequence)",
            ha='center', va='center', fontsize=19, fontweight='bold', color='#0F172A', zorder=10)
    ax.text(62.5, 96.6, "任务下发 · 活边路由 · 指令闭环 · 遥测入库 · 旁路约束判定 · 5步优雅收敛",
            ha='center', va='center', fontsize=10.5, color='#475569', zorder=10)

    # 10 根泳道 Lifeline
    lifelines = [
        ("操作员/前端UI\nUser & Web UI", 8.0, "#EFF6FF", "#3B82F6", "#1E40AF"),
        ("工作流引擎\nWorkflowEngine", 20.0, "#EFF6FF", "#2563EB", "#1E3A8A"),
        ("接口转发器\nInterfaceForwarder", 32.0, "#F0F9FF", "#0284C7", "#075985"),
        ("设备状态机\nStateMachineEngine", 44.0, "#F0FDF4", "#16A34A", "#14532D"),
        ("统一观测空间\nObservationSpace", 56.0, "#FFFBEB", "#D97706", "#78350F"),
        ("旁路约束引擎\nConstraintEngine", 68.0, "#FEF2F2", "#DC2626", "#7F1D1D"),
        ("MQTT通信桥接\nMQTT Bridge", 80.0, "#F1F5F9", "#64748B", "#334155"),
        ("边缘适配器\nEdge Adapter", 92.0, "#ECFDF5", "#059669", "#064E3B"),
        ("物理硬件/PLC\nPhysical Device", 104.0, "#F8FAFC", "#475569", "#0F172A"),
        ("PostgreSQL数据库\nDatabase Storage", 116.0, "#FAF5FF", "#7C3AED", "#4C1D95"),
    ]

    # 绘制泳道头部卡片与虚线
    for title, x, fc, ec, tc in lifelines:
        draw_card(ax, x - 5.0, 91.0, 10.0, 4.4, title="", fc=fc, ec=ec, lw=1.2, radius=0.6, zorder=3)
        lines = title.split('\n')
        ax.text(x, 93.7, lines[0], ha='center', va='center', fontsize=8.2, fontweight='bold', color=tc, zorder=4)
        ax.text(x, 92.1, lines[1], ha='center', va='center', fontsize=6.8, color='#64748B', zorder=4)
        ax.plot([x, x], [1.5, 90.8], color='#CBD5E1', linestyle=':', lw=1.2, zorder=1)

    # 绘制阶段分段背景区域 (预留标题高度，彻底消除与消息条的碰撞)
    phases = [
        (90.0, 69.5, "阶段一：任务启动与指令下发 (Task Initiation & Downward Command Dispatch)", "#EFF6FF", "#BFDBFE"),
        (69.0, 49.5, "阶段二：底层执行与事件回传闭环 (Bottom Execution & Event Feedback Closure)", "#F0FDF4", "#BBF7D0"),
        (49.0, 26.5, "阶段三：遥测数据流与旁路约束评估 (Telemetry Data Flow & Bypass Constraint Evaluation)", "#FFFBEB", "#FDE68A"),
        (26.0, 2.0, "阶段四：任务优雅收敛与结清 (Graceful Task Settlement & Termination)", "#FEF2F2", "#FECACA"),
    ]
    for y_top, y_bot, p_title, p_fc, p_ec in phases:
        rect = FancyBboxPatch((1.5, y_bot), 122.0, y_top - y_bot, boxstyle="round,pad=0,rounding_size=0.8",
                              fc=p_fc, ec=p_ec, lw=1.0, alpha=0.35, zorder=0)
        ax.add_patch(rect)
        ax.text(2.5, y_top - 1.2, p_title, fontsize=8.8, fontweight='bold', color='#1E293B', zorder=2)

    # 绘制具体交互消息条 (坐标精准微调，确保绝对不重叠)
    messages = [
        # --- 阶段一 ---
        (87.0, 8.0, 20.0, "1. 启动任务 (REST API: /api/workflow/task/start)", "#2563EB", False, False),
        (84.9, 20.0, 20.0, "2. 100ms 周期扫描，求值 DEV_NODE 输入触发器 (上升沿满足)", "#2563EB", True, False),
        (82.8, 20.0, 32.0, "3. routeEmission (发出 WF_EXECUTE_START 信号)", "#2563EB", False, False),
        (80.7, 32.0, 32.0, "4. 索引活边，占用设备槽位 (occupy deviceInstanceId)", "#0284C7", True, False),
        (78.6, 32.0, 44.0, "5. dispatchInputSignal (下发能力名与业务参数)", "#2563EB", False, False),
        (76.5, 44.0, 44.0, "6. CMD 状态由 IDLE 迁移至 SENT", "#16A34A", True, False),
        (74.4, 44.0, 80.0, "7. 发布 StateMachineSendActionEvent (CMD_START)", "#2563EB", False, False),
        (72.3, 80.0, 92.0, "8. MQTT CommandMessageFormat (注入 internal 参数，携带全局唯一 messageId)", "#2563EB", False, False),
        (70.2, 92.0, 104.0, "9. 南向物理驱动 (Modbus 写寄存器: 设定目标参数)", "#2563EB", False, False),

        # --- 阶段二 ---
        (66.5, 104.0, 92.0, "10. 物理设备开始动作反馈", "#16A34A", False, True),
        (64.4, 92.0, 80.0, "11. 回传事件 (eventName='started', 回传 messageId)", "#16A34A", False, True),
        (62.3, 80.0, 44.0, "12. dispatchAdapterEvent ('started')", "#16A34A", False, True),
        (60.2, 44.0, 44.0, "13. CMD 状态迁移: SENT -> RUNNING", "#16A34A", True, False),
        (58.1, 44.0, 56.0, "14. 广播 CMD_STATE (RUNNING) 到观测空间", "#16A34A", False, False),
        (56.0, 44.0, 32.0, "15. 回程活边原路写回目标节点 IN 接口快照", "#0284C7", False, True),
        (53.9, 32.0, 20.0, "16. 唤醒工作流任务轮询 (requestPoll)", "#0284C7", False, True),
        (51.8, 20.0, 116.0, "17. 节点状态更新为 RUNNING，持久化 TASK_STEP 与变量空间", "#7C3AED", False, False),

        # --- 阶段三 ---
        (46.8, 104.0, 92.0, "18. 传感器周期采样 (温度持续上升: 125℃ 超温)", "#D97706", False, True),
        (44.7, 92.0, 80.0, "19. 发送 TelemetryMessageFormat (telemetryData: {temp: 125})", "#D97706", False, True),
        (42.6, 80.0, 116.0, "20. 时序落库: 写入 DATA_RECORD_* 与更新 DEVICE_TWIN_STATES", "#7C3AED", False, False),
        (40.5, 80.0, 56.0, "21. 刷新设备孪生快照 (DEVICE_ATTRIBUTE) 并入时序历史缓存 (OHS)", "#D97706", False, True),
        (38.4, 56.0, 68.0, "22. 抛出 ObservableChangedEvent 唤醒约束引擎求值", "#DC2626", False, False),
        (36.3, 68.0, 68.0, "23. SpEL 求值 (temp > 120 持续 5s 达成违规)", "#DC2626", True, False),
        (34.2, 68.0, 116.0, "24. 审计归档: 捕获全量现场上下文写入 VIOLATION_LOG", "#7C3AED", False, False),
        (32.1, 68.0, 20.0, "25. 触发系统违约动作 SYSTEM:ABORT (终止任务)", "#DC2626", False, True),
        (30.0, 68.0, 44.0, "26. 触发设备保护动作 CONSTRAINT_EXECUTE (急停降温)", "#DC2626", False, True),

        # --- 阶段四 ---
        (24.0, 20.0, 20.0, "27. 执行 5步优雅终止收敛流程: 任务状态置为 TERMINATING", "#DC2626", True, False),
        (21.9, 20.0, 32.0, "28. 运行中节点发出 WF_EXECUTE_ABORT 中止信号", "#DC2626", False, False),
        (19.8, 32.0, 44.0, "29. 活边转发中止信号，状态机派生附属终止指令槽", "#DC2626", False, False),
        (17.7, 44.0, 80.0, "30. 下发 CMD_ABORT 指令帧到 MQTT", "#DC2626", False, False),
        (15.6, 80.0, 92.0, "31. Adapter 接收急停指令，驱动硬件安全切断", "#DC2626", False, False),
        (13.5, 92.0, 80.0, "32. 回传事件 (eventName='stopped', 携带 messageId)", "#16A34A", False, True),
        (11.4, 80.0, 44.0, "33. CMD 状态变为 ABORTED -> 自动释放槽位并复位为 IDLE", "#16A34A", False, True),
        (9.3, 44.0, 20.0, "34. 节点感知终态收敛为 TERMINATED，整任务结清", "#16A34A", False, True),
        (7.2, 20.0, 116.0, "35. 任务与各步骤终态入库，释放租约与资源锁定", "#7C3AED", False, False),
        (5.1, 20.0, 8.0, "36. TaskExecutionSseHub 实时推流：任务终止与报警弹窗", "#9333EA", False, True),
    ]

    for y, x1, x2, text_label, col, is_self, is_dashed in messages:
        if is_self:
            # 自反弧
            loop_x = x1 + 3.0
            ax.plot([x1, loop_x, loop_x, x1], [y + 0.35, y + 0.35, y - 0.35, y - 0.35],
                    color=col, lw=1.4, zorder=4)
            ax.annotate('', xy=(x1, y - 0.35), xytext=(loop_x, y - 0.35),
                        arrowprops=dict(arrowstyle='->', color=col, lw=1.4, mutation_scale=9), zorder=4)
            bbox = dict(boxstyle='round,pad=0.2', facecolor='#FFFFFF', edgecolor=col, alpha=0.92, lw=0.7)
            ax.text(loop_x + 0.6, y, text_label, ha='left', va='center', fontsize=7.0,
                    fontweight='bold', color=col, bbox=bbox, zorder=5)
        else:
            ls = '--' if is_dashed else '-'
            ax.plot([x1, x2], [y, y], color=col, linestyle=ls, lw=1.4, zorder=4)
            ax.annotate('', xy=(x2, y), xytext=(x1, y),
                        arrowprops=dict(arrowstyle='->', color=col, lw=1.4, linestyle=ls, mutation_scale=10), zorder=4)
            mid_x = (x1 + x2) / 2.0
            bbox = dict(boxstyle='round,pad=0.2', facecolor='#FFFFFF', edgecolor=col, alpha=0.92, lw=0.7)
            ax.text(mid_x, y + 0.65, text_label, ha='center', va='center', fontsize=7.0,
                    fontweight='bold', color=col, bbox=bbox, zorder=5)

    png_path = os.path.join(OUTPUT_DIR, "SmartLab2.0_端到端执行流与数据流.png")
    svg_path = os.path.join(OUTPUT_DIR, "SmartLab2.0_端到端执行流与数据流.svg")
    plt.savefig(png_path, dpi=300, bbox_inches='tight')
    plt.savefig(svg_path, format='svg', bbox_inches='tight')
    plt.close()
    print(f"[OK] Generated: {png_path}")
    print(f"[OK] Generated: {svg_path}")

if __name__ == "__main__":
    generate_layered_topology_diagram()
    generate_execution_and_data_flow_diagram()
