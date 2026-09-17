import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const outDir = join(here, 'generated_images');
const outFile = join(outDir, '09_SmartLab2.0_设备能力节点示意图_黑白线稿.svg');
mkdirSync(outDir, { recursive: true });

const width = 3000;
const height = 1800;
const flow = [];
const boxes = [];
const labels = [];

const esc = (value) => String(value)
  .replaceAll('&', '&amp;')
  .replaceAll('<', '&lt;')
  .replaceAll('>', '&gt;')
  .replaceAll('"', '&quot;');

function rect(x, y, w, h, cls = 'box') {
  boxes.push(`<rect class="${cls}" x="${x}" y="${y}" width="${w}" height="${h}"/>`);
}

function text(x, y, value, cls = 'small', anchor = 'start') {
  labels.push(`<text class="${cls}" x="${x}" y="${y}" text-anchor="${anchor}">${esc(value)}</text>`);
}

function line(points, cls = 'control-flow', both = false) {
  const attrs = both ? ' marker-start="url(#arrow)" marker-end="url(#arrow)"' : ' marker-end="url(#arrow)"';
  flow.push(`<polyline class="${cls}" points="${points.map(([x, y]) => `${x},${y}`).join(' ')}"${attrs}/>`);
}

function plainLine(points, cls = 'separator') {
  flow.push(`<polyline class="${cls}" points="${points.map(([x, y]) => `${x},${y}`).join(' ')}"/>`);
}

function edgeLabel(x, y, w, value) {
  rect(x, y, w, 34, 'label-bg');
  text(x + w / 2, y + 24, value, 'edge-label', 'middle');
}

function titledBox(x, y, w, h, title, titleClass = 'section-title', cls = 'box') {
  rect(x, y, w, h, cls);
  plainLine([[x, y + 52], [x + w, y + 52]], 'separator');
  text(x + 20, y + 36, title, titleClass);
}

function chip(x, y, w, h, title, detail = '') {
  rect(x, y, w, h, 'chip');
  text(x + 14, y + 26, title, 'label');
  if (detail) text(x + 14, y + 52, detail, 'tiny');
}

// Main execution and feedback paths (drawn first so boxes mask line crossings).
line([[350, 905], [380, 905], [380, 1015]], 'control-flow');
line([[630, 1015], [700, 1015]], 'control-flow');
line([[1400, 1040], [1430, 1040]], 'control-flow');
line([[2280, 1040], [2380, 1040]], 'control-flow');
line([[2630, 1040], [2650, 1040]], 'control-flow');

line([[880, 170], [940, 170]], 'data-flow');
line([[1090, 240], [1090, 630]], 'data-flow');
line([[1050, 890], [1050, 1490]], 'data-flow');
line([[1125, 1450], [1125, 1490]], 'data-flow');
line([[1300, 1540], [1430, 1540]], 'data-flow');

line([[2280, 1040], [2325, 1040], [2325, 515], [2380, 515]], 'control-flow');
line([[2630, 515], [2660, 515]], 'control-flow');
line([[2810, 740], [2810, 805], [2630, 805]], 'feedback-flow');
line([[2380, 805], [2280, 805]], 'feedback-flow');
line([[2810, 1120], [2810, 1260]], 'control-flow');

line([[1400, 760], [1430, 760]], 'internal-flow');
line([[1855, 890], [1855, 920]], 'internal-flow');
line([[1430, 1040], [1400, 1040]], 'internal-flow', true);
line([[1855, 920], [1855, 595]], 'internal-flow');
line([[1855, 1160], [1855, 1190]], 'runtime-flow');

line([[690, 1280], [610, 1280], [610, 1580], [690, 1580]], 'observe-flow');
line([[700, 770], [570, 770], [570, 1630], [690, 1630]], 'observe-flow');
line([[1430, 770], [2360, 770], [2360, 1640], [2570, 1640]], 'observe-flow');
line([[2280, 1280], [2480, 1280], [2480, 1590], [2570, 1590]], 'observe-flow');

// Page frame and title.
rect(24, 24, 2952, 1752, 'outer');
text(70, 82, 'SmartLab 2.0 设备能力节点（DEV_NODE）示意图', 'title');
text(72, 122, '展示节点定义、控制接口、数据端口、触发执行、设备闭环与运行时落点', 'subtitle');
rect(2120, 65, 810, 86, 'legend');
rect(2150, 89, 70, 3, 'line-sample');
text(2235, 97, '控制 / 状态信号', 'tiny');
rect(2440, 89, 18, 3, 'line-sample');
rect(2466, 89, 18, 3, 'line-sample');
rect(2492, 89, 18, 3, 'line-sample');
text(2525, 97, '数据端口', 'tiny');
rect(2690, 89, 5, 3, 'line-sample');
rect(2706, 89, 5, 3, 'line-sample');
rect(2722, 89, 5, 3, 'line-sample');
rect(2738, 89, 5, 3, 'line-sample');
rect(2754, 89, 5, 3, 'line-sample');
text(2775, 97, '观测 / 运行时绑定', 'tiny');

// External actors and mechanisms.
titledBox(50, 250, 300, 225, '工作流引擎', 'section-title', 'external');
chip(75, 320, 250, 56, '100 ms 周期调度');
chip(75, 390, 250, 56, '冻结快照 / 上升沿');

titledBox(50, 540, 300, 150, '上游节点', 'section-title', 'external');
text(200, 625, '完成后发出 ACTIVE', 'small', 'middle');

titledBox(50, 770, 300, 245, '连接通道', 'section-title', 'external');
chip(75, 840, 250, 58, '接口转发', '传递控制与状态信号');
chip(75, 916, 250, 58, '端口拉取', '刷新数据变量快照');

titledBox(50, 1500, 640, 220, '统一观测与约束引擎', 'section-title', 'external');
chip(75, 1572, 180, 80, '观测对象', '状态 / 变量 / 设备');
chip(270, 1572, 180, 80, '约束判定', '阈值 / 时序 / 组合');
chip(465, 1572, 200, 80, '处置结果', '告警 / 终止 / 记录');
text(370, 1695, '只读观察节点快照，不改变节点业务语义', 'tiny', 'middle');

titledBox(2650, 320, 300, 420, '设备状态机', 'section-title', 'external');
chip(2675, 392, 250, 62, '接收执行意图', 'WF_EXECUTE_START');
chip(2675, 472, 250, 62, '资源与命令调度', '生成 messageId');
chip(2675, 552, 250, 62, '命令状态回传', 'CMD_STATE');
chip(2675, 632, 250, 62, '运行状态观测', 'OP_STATE');

titledBox(2650, 900, 300, 220, '下游节点', 'section-title', 'external');
text(2800, 985, '接收 ACTIVE', 'small', 'middle');
text(2800, 1025, '进入下一执行步骤', 'small', 'middle');

titledBox(2570, 1260, 380, 220, 'Adapter 与物理/虚拟设备', 'section-title', 'external');
chip(2595, 1332, 155, 82, 'Adapter', '协议适配 / 命令封装');
chip(2770, 1332, 155, 82, '设备', '执行能力 / 返回结果');

titledBox(2570, 1520, 380, 200, '运行记录与数据管理', 'section-title', 'external');
chip(2595, 1592, 155, 70, '过程记录', '步骤 / 状态 / 时间');
chip(2770, 1592, 155, 70, '结果数据', '输出变量 / 文件');

titledBox(550, 120, 330, 100, '上游节点 / 数据源', 'section-title', 'external');
titledBox(1430, 1490, 500, 100, '下游节点 / 数据管理', 'section-title', 'external');

// The node boundary and its six explicit interface/port boxes.
rect(650, 250, 1700, 1200, 'node-outer');
rect(680, 280, 1640, 82, 'node-header');
text(710, 334, 'DEV_NODE｜设备能力节点', 'module-title');
text(2285, 331, '定义态 + 运行态', 'module-subtitle', 'end');

rect(380, 960, 250, 110, 'interface-box');
text(505, 1002, 'WORKFLOW IN', 'section-title', 'middle');
text(505, 1040, '接收 ACTIVE', 'small', 'middle');

rect(2380, 985, 250, 110, 'interface-box');
text(2505, 1027, 'WORKFLOW OUT', 'section-title', 'middle');
text(2505, 1065, '发出 ACTIVE', 'small', 'middle');

rect(2380, 450, 250, 130, 'interface-box');
text(2505, 486, 'STATE OUT', 'section-title', 'middle');
text(2505, 524, 'WF_EXECUTE_START', 'tiny', 'middle');
text(2505, 550, 'WF_EXECUTE_ABORT', 'tiny', 'middle');

rect(2380, 740, 250, 130, 'interface-box');
text(2505, 782, 'STATE IN', 'section-title', 'middle');
text(2505, 820, '接收 CMD_STATE', 'small', 'middle');

rect(940, 140, 300, 100, 'port-box');
text(1090, 180, 'PORT IN', 'section-title', 'middle');
text(1090, 214, '写入绑定变量', 'small', 'middle');

rect(950, 1490, 350, 100, 'port-box');
text(1125, 1530, 'PORT OUT', 'section-title', 'middle');
text(1125, 1564, '读取绑定变量', 'small', 'middle');

// Node definition areas.
titledBox(700, 395, 700, 200, '节点身份与资源引用');
chip(725, 465, 200, 90, 'name / nodeIdRef', '画布身份与稳定引用');
chip(945, 465, 200, 90, 'nodeType', 'DEV_NODE');
chip(1165, 465, 210, 90, 'deviceModelId', '目标设备模型');

titledBox(1430, 395, 850, 200, '设备能力与参数');
chip(1455, 465, 250, 90, 'capabilityName', '能力名称 / 操作语义');
chip(1725, 465, 255, 90, 'capabilityParameters', '参数值 / 变量引用');
chip(2000, 465, 255, 90, 'dispatch intent', '转换为设备执行请求');

titledBox(700, 630, 700, 260, '内部变量空间');
chip(725, 700, 200, 72, 'name', '变量唯一名称');
chip(945, 700, 200, 72, 'dataType', '数据类型');
chip(1165, 700, 210, 72, 'initialValue', '初始值');
chip(725, 792, 300, 68, 'attributesMapping', '可映射设备属性');
chip(1045, 792, 330, 68, 'port binding', 'IN 写入 / OUT 读取');

titledBox(1430, 630, 850, 260, '节点生命周期');
chip(1455, 700, 160, 72, 'PENDING', '等待触发');
chip(1635, 700, 160, 72, 'RUNNING', '正在执行');
chip(1815, 700, 190, 72, 'SUCCEEDED', '执行成功');
chip(2025, 700, 230, 72, 'FAILED / TERMINATED', '失败或终止');
plainLine([[1615, 736], [1635, 736]], 'state-flow');
plainLine([[1795, 736], [1815, 736]], 'state-flow');
plainLine([[2005, 736], [2025, 736]], 'state-flow');
text(1455, 838, 'initialStateName + states + transitions', 'small');
text(1455, 870, '状态迁移结果同步到任务步骤快照', 'tiny');

titledBox(700, 920, 700, 240, '接口触发器');
chip(725, 990, 205, 95, 'condition', '单条件或 AND 组合');
chip(950, 990, 205, 95, 'bindingTriggers', '接口绑定触发规则');
chip(1175, 990, 200, 95, 'evaluation', '冻结快照 + 上升沿');
text(725, 1135, '输入：接口信号 / 状态 / 变量　输出：待执行动作集合', 'tiny');

titledBox(1430, 920, 850, 240, '节点动作');
chip(1455, 990, 250, 105, 'UPDATE', '更新内部变量或生命周期');
chip(1725, 990, 250, 105, 'EMIT', '向接口发出控制/状态信号');
chip(1995, 990, 260, 105, '执行能力', '驱动设备状态机与 Adapter');
text(1455, 1138, '触发器一次命中，可按顺序执行一组动作', 'tiny');

titledBox(700, 1190, 1580, 210, '任务步骤运行时');
chip(725, 1260, 210, 92, 'TASK_STEP', '任务中的节点实例');
chip(955, 1260, 260, 92, 'deviceInstanceId', '资源映射后的真实设备');
chip(1235, 1260, 235, 92, 'messageId', '命令与回执关联键');
chip(1490, 1260, 350, 92, 'runtime snapshot', '接口 / 端口 / 状态 / 变量');
chip(1860, 1260, 395, 92, 'trace context', 'taskId / stepId / 时间戳');

// Labels on the principal chain.
edgeLabel(362, 860, 180, '① 接收信号');
edgeLabel(1180, 885, 200, '② 判定触发');
edgeLabel(1410, 885, 180, '③ 执行动作');
edgeLabel(2380, 400, 180, '④ 下发意图');
edgeLabel(2635, 760, 225, '⑤ 回传 CMD_STATE');
edgeLabel(2635, 1090, 220, '⑥ 激活下游');
edgeLabel(1260, 150, 190, '刷新输入变量');
edgeLabel(720, 1454, 190, '输出结果数据');
edgeLabel(420, 1460, 190, '持续观测');
edgeLabel(2390, 1430, 190, '持久化落点');

text(1500, 1760, '节点闭环：接收控制/数据 → 冻结快照判定 → UPDATE/EMIT → 调用设备能力 → 接收 CMD_STATE → 更新生命周期并激活下游', 'subtitle', 'middle');

const svg = `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
  <defs>
    <marker id="arrow" markerWidth="11" markerHeight="8" refX="10" refY="4" orient="auto" markerUnits="strokeWidth">
      <path d="M 0 0 L 11 4 L 0 8 z" fill="#111"/>
    </marker>
  </defs>
  <style>
    rect { fill:#fff; stroke:#111; stroke-width:2; }
    text { fill:#111; font-family:'Microsoft YaHei','Noto Sans CJK SC',sans-serif; }
    .outer { fill:#fff; stroke:#111; stroke-width:2.5; }
    .node-outer { fill:#fff; stroke:#111; stroke-width:4; }
    .node-header { fill:rgb(247,247,247); stroke:#111; stroke-width:2.5; }
    .box,.external,.interface-box,.port-box,.legend { fill:#fff; stroke:#111; stroke-width:2; }
    .chip { fill:rgb(250,250,250); stroke:#333; stroke-width:1.7; }
    .label-bg { fill:#fff; stroke:none; }
    .line-sample { fill:#111; stroke:none; }
    .control-flow,.feedback-flow,.internal-flow,.runtime-flow,.state-flow,.separator,.data-flow,.observe-flow { fill:none; stroke:#111; stroke-width:2.2; }
    .feedback-flow { stroke-width:2.5; }
    .data-flow { stroke-dasharray:12 8; }
    .observe-flow,.runtime-flow { stroke-dasharray:3 8; }
    .separator { marker-end:none; stroke-width:1.6; }
    .title { font-size:38px; font-weight:700; }
    .subtitle { font-size:21px; }
    .module-title { font-size:29px; font-weight:700; }
    .module-subtitle { font-size:18px; }
    .section-title { font-size:23px; font-weight:700; }
    .label { font-size:19px; font-weight:700; }
    .small { font-size:18px; }
    .tiny { font-size:15px; }
    .edge-label { font-size:16px; font-weight:700; }
  </style>
  ${flow.join('\n  ')}
  ${boxes.join('\n  ')}
  ${labels.join('\n  ')}
</svg>
`;

writeFileSync(outFile, svg, 'utf8');
console.log(outFile);
