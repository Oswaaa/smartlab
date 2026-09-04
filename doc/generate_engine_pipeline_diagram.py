import os
import matplotlib.pyplot as plt
import matplotlib.patches as patches
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
import numpy as np

# Set Chinese font
plt.rcParams['font.sans-serif'] = ['Microsoft YaHei', 'SimHei', 'Arial']
plt.rcParams['axes.unicode_minus'] = False

out_dir = r"d:\SmartLab2.0\doc\generated_images"
os.makedirs(out_dir, exist_ok=True)

def render_clean_engine_pipeline():
    fig, ax = plt.subplots(figsize=(17, 11.5), dpi=300)
    fig.patch.set_facecolor('#F8FAFC')
    ax.set_facecolor('#F8FAFC')
    ax.set_xlim(0, 170)
    ax.set_ylim(0, 115)
    ax.axis('off')

    # Main Card Border
    outer_card = FancyBboxPatch((1.0, 1.0), 168, 113, boxstyle="round,pad=0.5,rounding_size=2.0",
                                facecolor='#FFFFFF', edgecolor='#CBD5E1', linewidth=1.5, zorder=1)
    ax.add_patch(outer_card)

    # Header Titles
    ax.text(85, 110.0, "SmartLab 2.0 各引擎模块核心逻辑链路与全景交互拓扑图", ha='center', va='center',
            fontsize=16.5, fontweight='bold', color='#0F172A', zorder=10)
    ax.text(85, 106.8, "执行域 (纵向不可旁路闭环) 与 治理域 (旁路全量观测与软实时约束联锁) 的正交解耦架构", ha='center', va='center',
            fontsize=9.5, color='#64748B', zorder=10)

    # =========================================================================
    # Left Column: 执行域 (Execution Domain) - x in [10, 84]
    # =========================================================================
    
    # Execution Domain Big Background Box
    exec_bg = FancyBboxPatch((8, 6), 76, 96, boxstyle="round,pad=0.5,rounding_size=1.5",
                             facecolor='#F0F9FF', edgecolor='#BAE6FD', linewidth=1.2, linestyle='--', zorder=2)
    ax.add_patch(exec_bg)
    ax.text(12, 100.0, "【执行域】动态任务推进与底层物理驱动闭环", ha='left', va='center',
            fontsize=10.5, fontweight='bold', color='#0369A1', zorder=3)

    # 1. Agent & UI Box
    b_agent = FancyBboxPatch((12, 85), 68, 12, boxstyle="round,pad=0.4,rounding_size=1.0",
                            facecolor='#FFFFFF', edgecolor='#3B82F6', linewidth=1.5, zorder=3)
    ax.add_patch(b_agent)
    ax.text(46, 94.0, "① 大模型代理与人机交互层 (Agent & UI)", ha='center', va='center',
            fontsize=10.5, fontweight='bold', color='#1D4ED8', zorder=4)
    ax.text(46, 90.8, "• Prompt-to-Workflow: 意图理解与工具链调用 (list_catalog / get_model / validate)", ha='center', va='center',
            fontsize=7.5, color='#1E40AF', zorder=4)
    ax.text(46, 87.8, "• 模型草稿校验 & 保存 (FLOW_MODELS)  |  人工控制台与 SSE 实时事件监控", ha='center', va='center',
            fontsize=7.5, color='#475569', zorder=4)

    # 2. Workflow Engine Box
    b_wfe = FancyBboxPatch((12, 59), 68, 21, boxstyle="round,pad=0.4,rounding_size=1.0",
                           facecolor='#FFFFFF', edgecolor='#22C55E', linewidth=1.5, zorder=3)
    ax.add_patch(b_wfe)
    ax.text(46, 76.8, "② 工作流引擎 (Workflow Engine)", ha='center', va='center',
            fontsize=11.0, fontweight='bold', color='#15803D', zorder=4)
    ax.text(46, 73.5, "100ms 冻结快照轮询管线 (PipelineEngine) · 接口上升沿触发求值", ha='center', va='center',
            fontsize=8.0, fontweight='bold', color='#166534', zorder=4)
    ax.text(46, 70.0, "• 节点体系: DEV_NODE (设备能力) / FUNC_NODE (计算分支) / SUBFLOW_NODE (嵌套)", ha='center', va='center',
            fontsize=7.2, color='#374151', zorder=4)
    ax.text(46, 66.8, "• 接口连接 (Interface): 信号控制流活边 (ACTIVE 推进)  |  端口连接 (Port): 数据向右单向拉取", ha='center', va='center',
            fontsize=7.2, color='#374151', zorder=4)
    ax.text(46, 63.5, "• 节点生命周期: PENDING → RUNNING → SUCCEEDED / FAILED / TERMINATED", ha='center', va='center',
            fontsize=7.2, color='#15803D', zorder=4)

    # 3. State Machine Engine Box
    b_sme = FancyBboxPatch((12, 33), 68, 21, boxstyle="round,pad=0.4,rounding_size=1.0",
                           facecolor='#FFFFFF', edgecolor='#9333EA', linewidth=1.5, zorder=3)
    ax.add_patch(b_sme)
    ax.text(46, 50.8, "③ 设备状态机引擎 (Device State Machine Engine)", ha='center', va='center',
            fontsize=11.0, fontweight='bold', color='#6B21A8', zorder=4)
    ax.text(46, 47.5, "★ 发令必经之路 · 独占硬件控制权 · 实例隔离 · 附着式终止", ha='center', va='center',
            fontsize=8.0, fontweight='bold', color='#7C3AED', zorder=4)
    ax.text(46, 44.0, "• CMD 状态空间: IDLE → SENT → RUNNING → COMPLETED (超时看门狗 + 终止作用域收敛)", ha='center', va='center',
            fontsize=7.2, color='#374151', zorder=4)
    ax.text(46, 40.8, "• OP 状态空间: OPERATIONAL 单状态互斥区  |  EXCEPTION 多异常集合锁存区", ha='center', va='center',
            fontsize=7.2, color='#374151', zorder=4)
    ax.text(46, 37.5, "• 内置约束监测器 (IntrinsicConstraintMonitor) 轮询设备属性，越限强行锁存异常态", ha='center', va='center',
            fontsize=7.2, color='#6D28D9', zorder=4)

    # 4. Edge Adapter & Physical Hardware Box
    b_edge = FancyBboxPatch((12, 8), 68, 20, boxstyle="round,pad=0.4,rounding_size=1.0",
                            facecolor='#FFFFFF', edgecolor='#0D9488', linewidth=1.5, zorder=3)
    ax.add_patch(b_edge)
    ax.text(46, 25.0, "⑥ 边缘适配器驱动与物理仪器硬件层 (Adapter & Physical)", ha='center', va='center',
            fontsize=11.0, fontweight='bold', color='#0F766E', zorder=4)
    ax.text(46, 21.8, "Adapter 4层工业架构 (runtime / northbound / core / southbound)", ha='center', va='center',
            fontsize=8.0, fontweight='bold', color='#115E59', zorder=4)
    ax.text(46, 18.2, "• core: 语义翻译、internal 寄存器参数注入  |  northbound: MQTT 通信信封", ha='center', va='center',
            fontsize=7.2, color='#374151', zorder=4)
    ax.text(46, 15.0, "• southbound: Modbus-RTU/TCP、串口、PLC 控制总线", ha='center', va='center',
            fontsize=7.2, color='#374151', zorder=4)
    ax.text(46, 11.8, "• 物理终端: 夹套反应釜、全自动加样器、顶置搅拌器、温度/压力变送器阵列", ha='center', va='center',
            fontsize=7.2, color='#0F766E', zorder=4)


    # =========================================================================
    # Right Column: 治理域 (Governance & Safety Domain) - x in [94, 162]
    # =========================================================================
    
    gov_bg = FancyBboxPatch((92, 6), 70, 96, boxstyle="round,pad=0.5,rounding_size=1.5",
                            facecolor='#FFF1F2', edgecolor='#FECDD3', linewidth=1.2, linestyle='--', zorder=2)
    ax.add_patch(gov_bg)
    ax.text(96, 100.0, "【治理域】统一可观测空间与旁路软实时约束联锁", ha='left', va='center',
            fontsize=10.5, fontweight='bold', color='#BE123C', zorder=3)

    # 5. Observation Space Box
    b_obs = FancyBboxPatch((96, 52), 62, 45, boxstyle="round,pad=0.4,rounding_size=1.0",
                           facecolor='#FFFFFF', edgecolor='#EC4899', linewidth=1.5, zorder=3)
    ax.add_patch(b_obs)
    ax.text(127, 93.2, "④ 统一可观测空间 (Observation Space)", ha='center', va='center',
            fontsize=11.0, fontweight='bold', color='#BE185D', zorder=4)
    ax.text(127, 90.0, "Obs = Obs_R ∪ Obs_F · 权威内存快照 (微秒级只读)", ha='center', va='center',
            fontsize=8.0, fontweight='bold', color='#DB2777', zorder=4)

    obs_content = [
        ("【静态资源观测 Obs_R】", "#831843", True),
        ("• DEVICE_ATTRIBUTE: 物理传感器采样与遥测属性", "#475569", False),
        ("• DEVICE_COMMAND_LIFECYCLE: CMD 指令生命周期状态", "#475569", False),
        ("• DEVICE_OPERATION_STATE: OP 业务运行与异常锁存集合", "#475569", False),
        ("【动态工作流观测 Obs_F】", "#166534", True),
        ("• NODE_LIFECYCLE_STATE: 工作流节点执行生命周期", "#475569", False),
        ("• NODE_INTERNAL_VARIABLE: 节点步骤内部变量与物料上下文", "#475569", False),
        ("• TASK_LIFECYCLE_STATE: 全局任务执行状态 (RUNNING/TERMINATING)", "#475569", False),
        ("【有界时序历史存储 (ObservationHistoryStore)】", "#9D174D", True),
        ("• 1800s / 4096 点双向有界环形缓存 · 递增版本号 (version++)", "#374151", False),
        ("• 提供时序分析算子: delta(var, s) / avg(var, s) / rate(var)", "#374151", False),
        ("• 抛出 ObservableChangedEvent 唤醒约束引擎求值", "#DB2777", False)
    ]
    cur_y = 86.5
    for text, col, is_bold in obs_content:
        ax.text(99, cur_y, text, ha='left', va='center',
                fontsize=7.0 if not is_bold else 7.6,
                fontweight='bold' if is_bold else 'normal', color=col, zorder=4)
        cur_y -= 2.8

    # 6. Constraint Engine Box
    b_ce = FancyBboxPatch((96, 8), 62, 38, boxstyle="round,pad=0.4,rounding_size=1.0",
                          facecolor='#FFFFFF', edgecolor='#F43F5E', linewidth=1.5, zorder=3)
    ax.add_patch(b_ce)
    ax.text(127, 42.5, "⑤ 约束与安全拦截引擎 (Constraint Engine)", ha='center', va='center',
            fontsize=11.0, fontweight='bold', color='#BE123C', zorder=4)
    ax.text(127, 39.5, "纯逻辑旁路计算 · 双轨调度 · 软实时联锁处置", ha='center', va='center',
            fontsize=8.0, fontweight='bold', color='#E11D48', zorder=4)

    ce_content = [
        ("【双轨驱动调度器 (ConstraintEvaluationCoordinator)】", "#9F1239", True),
        ("• 轨 1: 事件驱动 (ObservableChangedEvent 瞬间触发求值)", "#881337", False),
        ("• 轨 2: 200ms 周期时钟兜底扫描 (评估滑动窗口函数与状态漂移)", "#881337", False),
        ("• 规则形式化: c = <obj, op, val, hdl> (对象、比较符、边界、动作)", "#374151", False),
        ("• windowSeconds 持续防抖窗口: 过滤毛刺，确认违规才触发处置", "#374151", False),
        ("【三级安全处置与审计闭环】", "#9F1239", True),
        ("• SYSTEM 级: ABORT (5步优雅终止流程) / PAUSE (挂起任务)", "#E11D48", False),
        ("• DEVICE 级: CONSTRAINT_EXECUTE (强行接管设备执行保护能力)", "#E11D48", False),
        ("• 现场审计: 完整变量快照持久化落盘至 VIOLATION_LOG", "#475569", False)
    ]
    cur_y = 36.2
    for text, col, is_bold in ce_content:
        ax.text(99, cur_y, text, ha='left', va='center',
                fontsize=7.0 if not is_bold else 7.6,
                fontweight='bold' if is_bold else 'normal', color=col, zorder=4)
        cur_y -= 2.9


    # =========================================================================
    # Arrows & Linkages (Connecting the Entire Ecosystem)
    # =========================================================================

    # --- 1. Agent -> Workflow Engine ---
    ax.add_patch(FancyArrowPatch((46, 85), (46, 80), arrowstyle='-|>', mutation_scale=12,
                                 linewidth=2.0, color='#2563EB', zorder=5))
    ax.text(47.5, 82.5, "编排并实例化任务 (TASK / TASK_STEP)", ha='left', va='center',
            fontsize=7.2, fontweight='bold', color='#1D4ED8', zorder=6)

    # --- 2. Vertical Closed Loop: Workflow <-> State Machine ---
    # Downward WF_START
    ax.add_patch(FancyArrowPatch((35, 59), (35, 54), arrowstyle='-|>', mutation_scale=12,
                                 linewidth=2.2, color='#7C3AED', zorder=5))
    ax.text(33.5, 56.5, "① 活边投递\nWF_START", ha='right', va='center',
            fontsize=7.2, fontweight='bold', color='#6D28D9', zorder=6)

    # Upward CMD_STATE
    ax.add_patch(FancyArrowPatch((57, 54), (57, 59), arrowstyle='-|>', mutation_scale=12,
                                 linewidth=2.2, linestyle='--', color='#059669', zorder=5))
    ax.text(58.5, 56.5, "⑦ 状态回传\nCMD_STATE (推动终态)", ha='left', va='center',
            fontsize=7.2, fontweight='bold', color='#047857', zorder=6)

    # --- 3. Vertical Closed Loop: State Machine <-> Adapter ---
    # Downward CMD_START
    ax.add_patch(FancyArrowPatch((35, 33), (35, 28), arrowstyle='-|>', mutation_scale=12,
                                 linewidth=2.2, color='#7C3AED', zorder=5))
    ax.text(33.5, 30.5, "② 标准控制帧\nCMD_START (messageId)", ha='right', va='center',
            fontsize=7.2, fontweight='bold', color='#6D28D9', zorder=6)

    # Upward event
    ax.add_patch(FancyArrowPatch((57, 28), (57, 33), arrowstyle='-|>', mutation_scale=12,
                                 linewidth=2.2, linestyle='--', color='#059669', zorder=5))
    ax.text(58.5, 30.5, "③ 命令事件\nevent (RUN/DONE)", ha='left', va='center',
            fontsize=7.2, fontweight='bold', color='#047857', zorder=6)

    # --- 4. Cross-Domain: Left -> Right Observation Feeds ---
    # 4a. Adapter -> Obs Space (Telemetry)
    ax.add_patch(FancyArrowPatch((80, 18), (96, 60), connectionstyle="arc3,rad=-0.18",
                                 arrowstyle='-|>', mutation_scale=13, linewidth=2.0, color='#0D9488', zorder=5))
    ax.text(88, 36, "④ 物理遥测\ntelemetry", ha='center', va='center',
            fontsize=7.2, fontweight='bold', color='#0F766E', zorder=6)

    # 4b. State Machine -> Obs Space (State Broadcast)
    ax.add_patch(FancyArrowPatch((80, 44), (96, 70), connectionstyle="arc3,rad=-0.15",
                                 arrowstyle='-|>', mutation_scale=13, linewidth=2.0, color='#9333EA', zorder=5))
    ax.text(88, 57, "⑤ 状态广播\nCMD/OP_STATE", ha='center', va='center',
            fontsize=7.2, fontweight='bold', color='#7E22CE', zorder=6)

    # 4c. Workflow Engine -> Obs Space (Node & Variables)
    ax.add_patch(FancyArrowPatch((80, 68), (96, 80), connectionstyle="arc3,rad=-0.10",
                                 arrowstyle='-|>', mutation_scale=13, linewidth=2.0, color='#16A34A', zorder=5))
    ax.text(88, 77, "⑥ 节点与变量\n快照 (Obs_F)", ha='center', va='center',
            fontsize=7.2, fontweight='bold', color='#15803D', zorder=6)

    # --- 5. Obs Space -> Constraint Engine ---
    ax.add_patch(FancyArrowPatch((127, 52), (127, 46), arrowstyle='-|>', mutation_scale=13,
                                 linewidth=2.4, color='#E11D48', zorder=5))
    ax.text(128.5, 49.0, "⑧ ObservableChangedEvent / 200ms 周期时钟", ha='left', va='center',
            fontsize=7.2, fontweight='bold', color='#BE123C', zorder=6)

    # --- 6. Cross-Domain: Right -> Left Safety Interventions ---
    # 6a. Constraint Engine -> Workflow Engine (SYSTEM: ABORT / PAUSE)
    ax.add_patch(FancyArrowPatch((96, 25), (80, 62), connectionstyle="arc3,rad=0.22",
                                 arrowstyle='-|>', mutation_scale=14, linewidth=2.4, linestyle='-.',
                                 color='#DC2626', zorder=7))
    badge_sys = FancyBboxPatch((78, 47), 16, 5.0, boxstyle="round,pad=0.2,rounding_size=0.6",
                               facecolor='#FEE2E2', edgecolor='#EF4444', linewidth=1.0, zorder=8)
    ax.add_patch(badge_sys)
    ax.text(86, 49.5, "⑨ SYSTEM: ABORT\n(5步优雅终止)", ha='center', va='center',
            fontsize=6.6, fontweight='bold', color='#B91C1C', zorder=9)

    # 6b. Constraint Engine -> State Machine (CONSTRAINT_EXECUTE)
    ax.add_patch(FancyArrowPatch((96, 16), (80, 36), connectionstyle="arc3,rad=0.18",
                                 arrowstyle='-|>', mutation_scale=13, linewidth=2.2, linestyle='-.',
                                 color='#DC2626', zorder=7))
    badge_dev = FancyBboxPatch((78, 22), 16, 5.0, boxstyle="round,pad=0.2,rounding_size=0.6",
                               facecolor='#FEE2E2', edgecolor='#EF4444', linewidth=1.0, zorder=8)
    ax.add_patch(badge_dev)
    ax.text(86, 24.5, "⑩ 强控状态机\nCONSTRAINT_EXECUTE", ha='center', va='center',
            fontsize=6.5, fontweight='bold', color='#B91C1C', zorder=9)

    # Bottom Summary Banner
    b_banner = FancyBboxPatch((15, 1.8), 140, 3.8, boxstyle="round,pad=0.2,rounding_size=0.6",
                              facecolor='#EFF6FF', edgecolor='#BFDBFE', linewidth=1.0, zorder=10)
    ax.add_patch(b_banner)
    ax.text(85, 3.7, "【架构精髓】执行链路纵向闭环且严禁旁路 · 观测空间权威内存解耦 · 约束引擎纯逻辑旁路计算 · 违规动作强实时反向介入",
            ha='center', va='center', fontsize=8.0, fontweight='bold', color='#1E40AF', zorder=11)

    out_path = os.path.join(out_dir, "04_各引擎模块逻辑链路图.png")
    plt.tight_layout()
    plt.savefig(out_path, dpi=300, facecolor=fig.get_facecolor(), edgecolor='none')
    plt.close()
    print("Saved perfect engine pipeline diagram:", out_path)

if __name__ == "__main__":
    render_clean_engine_pipeline()
