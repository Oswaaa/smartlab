import fs from "node:fs/promises";

const outputPath = "D:/SmartLab2.0/doc/generated_images/08_SmartLab2.0_端到端业务流程架构图_黑白线稿.svg";
const W = 3200;
const H = 1850;
const out = [];

const esc = (value) => String(value)
  .replaceAll("&", "&amp;")
  .replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;")
  .replaceAll('"', "&quot;");

function rect(x, y, w, h, cls = "item") {
  out.push(`<rect x="${x}" y="${y}" width="${w}" height="${h}" class="${cls}"/>`);
}

function text(x, y, value, cls = "label", anchor = "middle") {
  out.push(`<text x="${x}" y="${y}" text-anchor="${anchor}" class="${cls}">${esc(value)}</text>`);
}

function lines(cx, top, values, cls = "small", gap = 21) {
  values.forEach((value, index) => text(cx, top + index * gap, value, cls));
}

function polyline(points, cls = "main-flow", markerStart = false) {
  const pointText = points.map(([x, y]) => `${x},${y}`).join(" ");
  out.push(`<polyline points="${pointText}" class="${cls}"${markerStart ? ' marker-start="url(#arrowStart)"' : ""}/>`);
}

function sectionBox(x, y, w, h, title, subtitle = "") {
  rect(x, y, w, h, "section");
  rect(x, y, w, 48, "section-header");
  text(x + 18, y + 31, title, "section-title", "start");
  if (subtitle) text(x + w - 18, y + 30, subtitle, "tiny", "end");
}

function itemBox(x, y, w, h, title, details = [], cls = "item") {
  rect(x, y, w, h, cls);
  text(x + w / 2, y + 28, title, "item-title");
  if (details.length) {
    const gap = details.length > 3 ? 18 : 21;
    const first = y + Math.max(55, (h + 25 - (details.length - 1) * gap) / 2);
    lines(x + w / 2, first, details, details.some((value) => value.length > 33) ? "tiny" : "small", gap);
  }
}

function stageBox(x, step, title, moduleName, details = []) {
  const y = 720;
  const w = 300;
  const h = 230;
  rect(x, y, w, h, "stage");
  rect(x, y, w, 38, "stage-header");
  text(x + 14, y + 25, `步骤 ${step}`, "tiny", "start");
  text(x + w - 14, y + 25, moduleName, "tiny", "end");
  text(x + w / 2, y + 75, title, title.length > 15 ? "stage-title-small" : "stage-title");
  if (details.length) lines(x + w / 2, y + 128, details, "small", 22);
}

out.push(`<?xml version="1.0" encoding="UTF-8"?>`);
out.push(`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">`);
out.push(`<defs>
  <style>
    .title{font:700 32px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#000}
    .subtitle{font:14px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#000}
    .zone-title{font:700 18px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#000}
    .section-title{font:700 17px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#000}
    .item-title{font:700 15px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#000}
    .stage-title{font:700 16px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#000}
    .stage-title-small{font:700 14px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#000}
    .label{font:14px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#000}
    .small{font:12.5px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#000}
    .tiny{font:11px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#000}
    .background,.label-bg{fill:#fff;stroke:none}
    .outer{fill:#fff;stroke:#000;stroke-width:2}
    .mechanism-zone,.landing-zone{fill:#fff;stroke:#000;stroke-width:1.5;stroke-dasharray:9 7}
    .section,.stage,.item,.primary-item{fill:#fff;stroke:#000;stroke-width:1.45}
    .section{stroke-width:1.8}
    .stage{stroke-width:2.2}
    .primary-item{stroke-width:1.8}
    .section-header,.stage-header{fill:#fff;stroke:#000;stroke-width:1.2}
    .main-flow{fill:none;stroke:#000;stroke-width:2.6;marker-end:url(#arrow)}
    .branch-flow{fill:none;stroke:#000;stroke-width:1.7;marker-end:url(#arrow)}
    .mechanism-flow{fill:none;stroke:#000;stroke-width:1.45;stroke-dasharray:7 5;marker-end:url(#arrow)}
    .landing-flow{fill:none;stroke:#000;stroke-width:1.4;stroke-dasharray:3 4;marker-end:url(#arrow)}
    .feedback-flow{fill:none;stroke:#000;stroke-width:1.5;stroke-dasharray:10 6;marker-end:url(#arrow)}
  </style>
  <marker id="arrow" markerWidth="10" markerHeight="8" refX="9" refY="4" orient="auto" markerUnits="strokeWidth"><path d="M0,0 L10,4 L0,8 Z" fill="#000"/></marker>
  <marker id="arrowStart" markerWidth="10" markerHeight="8" refX="1" refY="4" orient="auto-start-reverse" markerUnits="strokeWidth"><path d="M10,0 L0,4 L10,8 Z" fill="#000"/></marker>
</defs>`);

rect(0, 0, W, H, "background");
rect(22, 25, 3156, 1795, "outer");
text(1600, 68, "SmartLab 2.0 端到端业务流程架构图", "title");
text(1600, 98, "以用户业务路径为主轴，贯通智能建模、生产/仿真执行、设备控制、实时观测和数据追溯", "subtitle");

// 内部执行机制
rect(70, 132, 3060, 500, "mechanism-zone");
rect(1320, 116, 560, 32, "label-bg");
text(1600, 139, "内部执行机制（挂接在业务主轴上方）", "zone-title");

sectionBox(340, 175, 720, 395, "Agent 内部机制", "对应步骤 2—3");
itemBox(370, 240, 320, 118, "资源与能力检索", ["设备模型与能力目录", "已有流程及约束规则"]);
itemBox(710, 240, 320, 118, "大模型规划与工具调用", ["OpenAI-compatible API", "目录、校验、仿真工具"]);
itemBox(370, 378, 320, 145, "流程生成", ["节点、连接、参数", "执行条件与约束绑定"]);
itemBox(710, 378, 320, 145, "校验与自动修复", ["Schema 校验", "仿真验证及反馈迭代"]);

sectionBox(1130, 175, 820, 395, "任务编排与执行控制", "对应步骤 5—6");
itemBox(1160, 240, 240, 118, "任务调度", ["任务实例化", "步骤状态推进"]);
itemBox(1420, 240, 240, 118, "工作流引擎", ["100ms 调度", "冻结快照与上升沿"]);
itemBox(1680, 240, 240, 118, "连接通道", ["接口信号转发", "端口数据拉取"]);
itemBox(1160, 378, 240, 145, "设备状态机", ["实例隔离", "CMD / OP 双状态"]);
itemBox(1420, 378, 240, 145, "约束引擎", ["事件触发", "200ms 周期扫描"]);
itemBox(1680, 378, 240, 145, "任务控制", ["终止、暂停、告警", "设备保护与日志"]);

sectionBox(2020, 175, 470, 395, "设备执行机制", "对应步骤 7");
itemBox(2050, 240, 410, 75, "MQTT Broker", ["命令 / 遥测 / 事件 / 心跳"]);
itemBox(2050, 332, 410, 75, "Adapter Runtime", ["注册、配置、租约与看门狗"]);
itemBox(2050, 424, 410, 99, "南向协议连接器", ["Modbus、串口、TCP、HTTP、SDK"]);

sectionBox(2560, 175, 540, 395, "观测与反馈机制", "对应步骤 8—9");
itemBox(2590, 240, 230, 118, "统一观测空间", ["设备、状态机", "任务与历史快照"]);
itemBox(2840, 240, 230, 118, "遥测与事件", ["高频数据", "CMD / OP 状态"]);
itemBox(2590, 378, 230, 145, "实时推送", ["REST API", "SSE / WebSocket"]);
itemBox(2840, 378, 230, 145, "异常处置", ["约束求值", "中止、暂停、告警"]);

// 机制挂接
polyline([[700, 570], [700, 670], [910, 670], [910, 720]], "mechanism-flow");
polyline([[1540, 570], [1540, 685], [1600, 685], [1600, 720]], "mechanism-flow");
polyline([[1780, 570], [1780, 665], [1945, 665], [1945, 720]], "mechanism-flow");
polyline([[2255, 570], [2255, 720]], "mechanism-flow");
polyline([[2830, 570], [2830, 665], [2635, 665], [2635, 720]], "mechanism-flow");

// 用户业务主轴
rect(80, 672, 380, 34, "label-bg");
text(90, 697, "用户业务主轴", "zone-title", "start");
text(3050, 697, "从需求提出到结果复用", "subtitle", "end");

const stageXs = [70, 415, 760, 1105, 1450, 1795, 2140, 2485, 2830];
stageBox(stageXs[0], 1, "用户登录与实验入口", "用户界面", ["登录并选择项目", "进入实验工作台"]);
stageBox(stageXs[1], 2, "描述实验需求与设计流程", "用户界面", ["自然语言需求", "或可视化流程编辑"]);
stageBox(stageXs[2], 3, "Agent 生成并校验流程", "Agent", ["查询资源与能力", "生成、校验并修复"]);
stageBox(stageXs[3], 4, "用户确认并启动", "用户界面", ["确认步骤及参数", "选择生产或仿真"]);
stageBox(stageXs[4], 5, "任务与步骤实例化", "实验执行", ["创建 TASK / TASK_STEP", "冻结流程与设备快照"]);
stageBox(stageXs[5], 6, "选择执行方式", "实验执行");
itemBox(stageXs[5] + 15, 808, 128, 108, "生产执行", ["真实设备"]);
itemBox(stageXs[5] + 157, 808, 128, 108, "仿真执行", ["虚拟设备"]);
stageBox(stageXs[6], 7, "设备或虚拟设备运行", "设备接入");
itemBox(stageXs[6] + 15, 808, 128, 108, "物理设备", ["Adapter 控制"]);
itemBox(stageXs[6] + 157, 808, 128, 108, "虚拟设备", ["仿真状态演化"]);
stageBox(stageXs[7], 8, "实时监控与约束处置", "实验执行", ["统一观测与反馈", "异常检测及安全控制"]);
stageBox(stageXs[8], 9, "结果查询与实验追溯", "用户界面", ["数据、日志和快照", "复盘、导出与复用"]);

for (let index = 0; index < stageXs.length - 1; index += 1) {
  polyline([[stageXs[index] + 300, 835], [stageXs[index + 1], 835]], "main-flow");
}

// 数据落点
rect(70, 1040, 3060, 540, "landing-zone");
rect(1335, 1024, 530, 32, "label-bg");
text(1600, 1047, "数据落点（随业务步骤同步沉淀）", "zone-title");

sectionBox(190, 1100, 520, 400, "模型与元数据落点", "步骤 2—5");
itemBox(220, 1165, 460, 65, "FLOW_MODELS / FLOW_NODE");
itemBox(220, 1245, 460, 65, "DEVICE_CATEGORY / DEVICE_MODELS");
itemBox(220, 1325, 460, 65, "SCENE_MAIN / SCENE_DETAIL / RESOURCE_STRUCTURE");
itemBox(220, 1405, 460, 65, "USER_INFO / PERMISSION_INFO");

sectionBox(770, 1100, 510, 400, "任务执行数据落点", "步骤 5—8");
itemBox(800, 1165, 450, 83, "TASK / TASK_STEP / EXECUTION_LOG");
itemBox(800, 1265, 450, 83, "任务变量 / 节点快照 / 端口快照");
itemBox(800, 1365, 450, 83, "执行状态 / 步骤结果 / 错误信息");

sectionBox(1340, 1100, 520, 400, "实验数据落点", "步骤 7—9");
itemBox(1370, 1165, 460, 92, "DATA_TEMPLATE / DATA_INDEX / DATA_RECORD_XXXX");
itemBox(1370, 1275, 460, 75, "设备采样、过程数据与计算结果");
itemBox(1370, 1368, 460, 75, "数据索引、时间戳与来源标识");

sectionBox(1920, 1100, 470, 400, "设备状态落点", "步骤 7—8");
itemBox(1950, 1165, 410, 83, "DEVICE_TWIN_STATES");
itemBox(1950, 1265, 410, 83, "ADAPTER_INDEX / VIRTUAL_LEASE");
itemBox(1950, 1365, 410, 83, "属性值 / 在线状态 / CMD / OP 状态");

sectionBox(2450, 1100, 550, 400, "约束与审计落点", "步骤 8—9");
itemBox(2480, 1165, 490, 83, "CONSTRAINT_RULE / VIOLATION_LOG");
itemBox(2480, 1265, 490, 83, "bindings / window / actions / snapshot");
itemBox(2480, 1365, 490, 83, "违规现场 / 处置动作 / 告警审计");

polyline([[910, 950], [910, 1005], [450, 1005], [450, 1100]], "landing-flow");
polyline([[1600, 950], [1600, 1018], [1025, 1018], [1025, 1100]], "landing-flow");
polyline([[2290, 950], [2290, 1028], [1600, 1028], [1600, 1100]], "landing-flow");
polyline([[2290, 950], [2290, 1070], [2155, 1070], [2155, 1100]], "landing-flow");
polyline([[2635, 950], [2635, 1075], [2725, 1075], [2725, 1100]], "landing-flow");

// 结果复用闭环
polyline([[2980, 950], [3150, 950], [3150, 1690], [45, 1690], [45, 650], [565, 650], [565, 720]], "feedback-flow");
rect(1260, 1667, 680, 34, "label-bg");
text(1600, 1692, "结果复用 / 流程优化 / 下一轮实验", "zone-title");

text(70, 1772, "实线：用户业务主轴与执行分支", "small", "start");
text(1600, 1772, "虚线：内部机制挂接与闭环反馈", "small");
text(3130, 1772, "点线：数据库表与运行数据落点", "small", "end");
out.push(`</svg>`);

await fs.mkdir("D:/SmartLab2.0/doc/generated_images", { recursive: true });
await fs.writeFile(outputPath, out.join("\n"), "utf8");
console.log(outputPath);
