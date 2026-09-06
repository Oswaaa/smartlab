import fs from "node:fs/promises";

const outputPath = "D:/SmartLab2.0/doc/generated_images/06_SmartLab2.0_系统结构图_数据库与交互链路.svg";
const W = 2400;
const H = 1400;
const out = [];

const esc = (value) => String(value)
  .replaceAll("&", "&amp;")
  .replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;")
  .replaceAll('"', "&quot;");

function rect(x, y, w, h, cls = "node", rx = 0) {
  out.push(`<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${rx}" class="${cls}"/>`);
}

function ellipse(cx, cy, rx, ry, cls = "node") {
  out.push(`<ellipse cx="${cx}" cy="${cy}" rx="${rx}" ry="${ry}" class="${cls}"/>`);
}

function text(x, y, value, cls = "label", anchor = "middle") {
  out.push(`<text x="${x}" y="${y}" text-anchor="${anchor}" class="${cls}">${esc(value)}</text>`);
}

function labelBox(x, y, value, cls = "edge-label", width = null) {
  const estimated = width ?? Math.max(62, [...value].reduce((n, c) => n + (c.charCodeAt(0) > 255 ? 15 : 8), 0) + 18);
  rect(x - estimated / 2, y - 18, estimated, 25, "label-bg", 2);
  text(x, y, value, cls);
}

function polyline(points, cls = "flow", markerStart = false) {
  const p = points.map(([x, y]) => `${x},${y}`).join(" ");
  out.push(`<polyline points="${p}" class="${cls}"${markerStart ? ' marker-start="url(#arrowStart)"' : ""}/>`);
}

function arrow(points, label = "", cls = "flow", lx = null, ly = null, markerStart = false, labelWidth = null) {
  polyline(points, cls, markerStart);
  if (label) {
    const mid = points[Math.floor(points.length / 2)];
    labelBox(lx ?? mid[0], ly ?? (mid[1] - 8), label, "edge-label", labelWidth);
  }
}

function role(x, y, w, h, title, subtitle) {
  rect(x, y, w, h, "role", 13);
  ellipse(x + w / 2, y + 42, 19, 19, "person-fill");
  rect(x + w / 2 - 31, y + 66, 62, 35, "person-fill", 14);
  text(x + w / 2, y + 126, title, "group-title");
  text(x + w / 2, y + 150, subtitle, "small");
}

function cylinder(x, y, w, h, title, items, subtitle = "PostgreSQL + JSONB") {
  const cap = 22;
  rect(x, y + cap, w, h - cap * 2, "cylinder-body");
  ellipse(x + w / 2, y + cap, w / 2, cap, "cylinder-cap");
  ellipse(x + w / 2, y + h - cap, w / 2, cap, "cylinder-cap-bottom");
  text(x + w / 2, y + 35, title, "section-title");
  text(x + w / 2, y + 56, subtitle, "tiny");
  const contentTop = y + 70;
  const contentBottom = y + h - 45;
  const gap = 8;
  const itemH = Math.min(42, (contentBottom - contentTop - gap * (items.length - 1)) / items.length);
  items.forEach((item, i) => {
    const iy = contentTop + i * (itemH + gap);
    rect(x + 24, iy, w - 48, itemH, "db-item", 3);
    text(x + w / 2, iy + itemH / 2 + 5, item, item.length > 34 ? "tiny" : "small");
  });
}

function pool(x, y, w, h, title, items, cls = "pool") {
  rect(x, y, w, h, cls, 10);
  rect(x + 22, y - 13, w - 44, 28, "label-bg", 2);
  text(x + w / 2, y + 8, title, "section-title");
  const top = y + 42;
  const gap = 9;
  const itemH = (h - 61 - gap * (items.length - 1)) / items.length;
  items.forEach((item, i) => {
    const iy = top + i * (itemH + gap);
    rect(x + 24, iy, w - 48, itemH, "service-item", 4);
    if (Array.isArray(item)) {
      text(x + w / 2, iy + itemH / 2 - 1, item[0], "label");
      text(x + w / 2, iy + itemH / 2 + 18, item[1], "tiny");
    } else {
      text(x + w / 2, iy + itemH / 2 + 5, item, "label");
    }
  });
}

out.push(`<?xml version="1.0" encoding="UTF-8"?>`);
out.push(`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">`);
out.push(`<defs>
  <style>
    .title{font:700 29px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#111}
    .subtitle{font:14px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#444}
    .group-title{font:700 18px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#111}
    .section-title{font:700 17px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#111}
    .label{font:15px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#111}
    .small{font:13px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#222}
    .tiny{font:11.5px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#333}
    .edge-label{font:600 12.5px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#111}
    .outer{fill:#fff;stroke:#111;stroke-width:1.8}
    .db-zone{fill:#fff;stroke:#333;stroke-width:1.25;stroke-dasharray:8 6}
    .node,.role,.pool{fill:#fff;stroke:#222;stroke-width:1.3}
    .role{fill:#fafafa;stroke-width:1.5}
    .person-fill{fill:#444;stroke:none}
    .label-bg{fill:#fff;stroke:none}
    .cylinder-body{fill:#fff;stroke:#222;stroke-width:1.3}
    .cylinder-cap{fill:#f5f5f5;stroke:#222;stroke-width:1.3}
    .cylinder-cap-bottom{fill:#fff;stroke:#222;stroke-width:1.3}
    .db-item{fill:#fff;stroke:#555;stroke-width:1}
    .service-item{fill:#fafafa;stroke:#444;stroke-width:1}
    .smart-pool{fill:#f7f7f7;stroke:#222;stroke-width:1.4}
    .data-pool{fill:#fff;stroke:#222;stroke-width:1.4}
    .runtime-pool{fill:#f7f7f7;stroke:#111;stroke-width:1.6}
    .external{fill:#fafafa;stroke:#333;stroke-width:1.2;stroke-dasharray:6 4}
    .flow{fill:none;stroke:#111;stroke-width:1.45;marker-end:url(#arrow)}
    .data-flow{fill:none;stroke:#333;stroke-width:1.35;stroke-dasharray:7 5;marker-end:url(#arrow)}
    .persist-flow{fill:none;stroke:#555;stroke-width:1.2;stroke-dasharray:3 4;marker-end:url(#arrow)}
    .feedback{fill:none;stroke:#111;stroke-width:1.4;marker-end:url(#arrow)}
  </style>
  <marker id="arrow" markerWidth="10" markerHeight="8" refX="9" refY="4" orient="auto" markerUnits="strokeWidth"><path d="M0,0 L10,4 L0,8 Z" fill="#111"/></marker>
  <marker id="arrowStart" markerWidth="10" markerHeight="8" refX="1" refY="4" orient="auto-start-reverse" markerUnits="strokeWidth"><path d="M10,0 L0,4 L10,8 Z" fill="#111"/></marker>
</defs>`);

rect(0, 0, W, H, "label-bg");
rect(22, 65, 2356, 1290, "outer", 4);
rect(655, 48, 1090, 34, "label-bg");
text(1200, 73, "SmartLab 2.0 智能实验室系统结构图", "title");
text(1200, 101, "基于数据库模型、系统交互链路与边缘设备闭环", "subtitle");

// Roles
role(52, 125, 205, 160, "科研人员", "流程设计、仿真与实验执行");
role(52, 425, 205, 160, "系统管理员", "模型、资源、权限与接入管理");
role(1712, 414, 190, 176, "科研终端用户", "任务监控与实验数据访问");

// Unified specification and AI modeling
cylinder(330, 120, 610, 185, "统一规范与智能建模库", [
  "协议规范 / 系统执行规范",
  "设备能力 / 状态机 / 工作流 / 约束 Schema",
  "Agent 技能、概念词典与生成提示词"
], "JSON Schema + Agent Resources");

pool(1100, 122, 550, 190, "智能工作流建模服务池", [
  ["工作流 Agent", "自然语言需求解析与多轮工具调用"],
  ["目录检索工具", "设备模型 / 能力 / 已有流程检索"],
  ["校验、仿真与修复", "Schema 校验 · 仿真执行 · 保存草稿"]
], "smart-pool");

rect(1980, 145, 260, 118, "external", 8);
text(2110, 180, "外部大模型服务", "section-title");
text(2110, 208, "OpenAI-compatible API", "label");
text(2110, 234, "规划、工具选择与结构化生成", "small");

// Database zone
rect(290, 354, 870, 900, "db-zone", 8);
rect(555, 339, 340, 28, "label-bg");
text(725, 360, "PostgreSQL 数据、模型与审计层", "group-title");

cylinder(320, 397, 360, 225, "元数据与资源库", [
  "USER_INFO / PERMISSION_INFO",
  "PROPERTY_TYPE",
  "SCENE_MAIN / SCENE_DETAIL",
  "RESOURCE_STRUCTURE"
]);

cylinder(750, 397, 360, 225, "实验数据资料库", [
  "DATA_TEMPLATE_MAIN / DETAIL",
  "DATA_INDEX",
  "DATA_RECORD_XXXX 动态实验记录"
]);

cylinder(320, 710, 360, 252, "设备与数字孪生库", [
  "DEVICE_CATEGORY / DEVICE_MODELS",
  "DEVICE_INSTANCES / DEVICE_COMPONENTS",
  "DEVICE_TWIN_STATES",
  "ADAPTER_INDEX / VIRTUAL_LEASE"
]);

cylinder(750, 710, 360, 252, "工作流与任务资料库", [
  "FLOW_MODELS / FLOW_NODE",
  "TASK / TASK_STEP",
  "EXECUTION_LOG",
  "任务变量、接口与端口快照"
]);

cylinder(535, 1042, 360, 178, "约束规则与违规审计库", [
  "CONSTRAINT_RULE",
  "VIOLATION_LOG",
  "bindings / window / actions / snapshot"
]);

// Service pools
pool(1200, 380, 440, 250, "元数据驱动管理服务池", [
  ["资源与设备管理", "分类、模型、实例、组件、场景拓扑"],
  ["数据模板与动态表管理", "模板定义、索引、设备属性映射"],
  ["用户、权限与菜单", "JWT、等级权限与功能入口过滤"],
  ["Adapter 注册与虚拟租约", "Manifest 解析、心跳与租约生命周期"]
], "pool");

pool(1200, 700, 440, 255, "观测与数据服务池", [
  ["设备孪生快照", "属性、在线状态、CMD / OP 状态"],
  ["任务与步骤快照", "节点、变量、接口、端口与执行日志"],
  ["统一观测空间", "只读快照与有界时序历史"],
  ["数据访问与实时推送", "REST API · SSE · WebSocket"]
], "data-pool");

pool(1200, 1020, 440, 260, "任务执行与安全控制服务池", [
  ["工作流引擎", "100ms 调度、冻结快照与上升沿求值"],
  ["连接通道体系", "接口信号转发与端口数据拉取"],
  ["设备状态机引擎", "实例隔离、CMD / OP 双状态空间"],
  ["约束引擎与任务控制", "事件 + 200ms 扫描、终止 / 暂停 / 告警"]
], "runtime-pool");

// Edge and hardware
rect(1950, 675, 265, 486, "node", 10);
rect(1986, 661, 193, 28, "label-bg");
text(2082, 681, "边缘接入与 Adapter 层", "section-title");
rect(1974, 715, 217, 66, "service-item", 4);
text(2082, 742, "MQTT Broker 与消息服务", "label");
text(2082, 764, "命令 / 遥测 / 事件 / 心跳", "tiny");
rect(1974, 802, 217, 66, "service-item", 4);
text(2082, 829, "Adapter Runtime", "label");
text(2082, 851, "配置、生命周期与看门狗", "tiny");
rect(1974, 889, 217, 78, "service-item", 4);
text(2082, 918, "northbound / core / southbound", "small");
text(2082, 943, "通信 · 语义转换 · 硬件驱动", "tiny");
rect(1974, 988, 217, 75, "service-item", 4);
text(2082, 1017, "南向协议连接器", "label");
text(2082, 1042, "Modbus · 串口 · TCP · HTTP · SDK", "tiny");
rect(1974, 1083, 217, 50, "service-item", 4);
text(2082, 1114, "Python / C++ / 边缘网关", "small");

rect(2242, 675, 112, 486, "node", 10);
rect(2255, 661, 86, 28, "label-bg");
text(2298, 681, "装备层", "section-title");
[
  [715, "反应釜"],
  [805, "泵 / 阀"],
  [895, "机械臂 / PLC"],
  [985, "传感器"],
  [1075, "分析仪器"]
].forEach(([y, t]) => {
  rect(2257, y, 82, 61, "service-item", 4);
  text(2298, y + 36, t, t.length > 6 ? "tiny" : "small");
});

// Interaction arrows - roles and modeling
arrow([[257, 180], [330, 180]], "定义模型", "flow", 293, 160, false, 72);
arrow([[257, 220], [1080, 220], [1100, 220]], "自然语言实验需求", "flow", 755, 202, false, 140);
arrow([[940, 212], [1100, 212]], "规范与技能", "flow", 1020, 194, false, 94);
arrow([[1650, 205], [1980, 205]], "模型调用", "flow", 1815, 187, true, 82);
arrow([[1100, 278], [1060, 278], [1060, 335], [715, 335], [715, 675], [930, 675], [930, 710]], "校验通过后保存流程草稿", "persist-flow", 875, 326, false, 178);

// Administrator and management
arrow([[257, 485], [320, 485]], "管理", "flow", 287, 465, false, 58);
arrow([[257, 540], [274, 540], [274, 655], [1170, 655], [1170, 540], [1200, 540]], "资源、设备、权限与接入管理", "flow", 760, 647, false, 196);
arrow([[680, 585], [700, 585], [700, 675], [1145, 675], [1145, 505], [1200, 505]], "元数据与资源配置", "flow", 910, 667, false, 142);
arrow([[1110, 505], [1200, 505]], "模板与索引", "persist-flow", 1155, 529, false, 84);
arrow([[1200, 585], [1128, 585], [1128, 820], [1110, 820]], "模型与实例持久化", "persist-flow", 1138, 685, false, 142);

// Users and services
arrow([[1640, 470], [1712, 470]], "REST 管理", "flow", 1676, 450, true, 84);
arrow([[1650, 250], [1770, 250], [1770, 414]], "生成、校验与仿真", "flow", 1782, 325, true, 132);
arrow([[1640, 805], [1712, 805], [1712, 560]], "查询与实时推送", "data-flow", 1676, 786, true, 124);
arrow([[1807, 590], [1807, 1145], [1640, 1145]], "启动 / 暂停 / 终止", "flow", 1819, 1010, false, 132);

// Database to services
arrow([[680, 900], [700, 900], [700, 985], [1180, 985], [1180, 820], [1200, 820]], "孪生状态与设备数据", "persist-flow", 920, 977, false, 148);
arrow([[1110, 500], [1165, 500], [1165, 765], [1200, 765]], "实验记录", "persist-flow", 1176, 665, false, 82);
arrow([[1110, 835], [1175, 835], [1175, 885], [1200, 885]], "步骤与日志", "persist-flow", 1176, 862, false, 90);
arrow([[680, 930], [715, 930], [715, 1005], [1120, 1005], [1120, 1090], [1200, 1090]], "设备模型与能力", "flow", 910, 997, false, 120);
arrow([[1110, 870], [1160, 870], [1160, 1150], [1200, 1150]], "流程模型与任务实例", "flow", 1171, 1030, false, 148);
arrow([[895, 1125], [1200, 1125]], "约束规则", "flow", 1048, 1107, false, 84);
arrow([[1200, 1220], [930, 1220], [930, 1165], [895, 1165]], "违规现场与处置审计", "persist-flow", 1045, 1242, false, 148);

// Runtime and observation loop
arrow([[1420, 1020], [1420, 955]], "运行时快照", "data-flow", 1475, 988, false, 98);
arrow([[1360, 955], [1360, 1020]], "约束读取", "data-flow", 1310, 994, false, 84);

// Edge closed loop
arrow([[1640, 1095], [1950, 1095]], "CMD_START / CMD_ABORT", "flow", 1795, 1077, false, 170);
arrow([[1950, 1150], [1640, 1150]], "event / CMD_STATE / OP_STATE", "feedback", 1795, 1174, false, 206);
arrow([[1950, 760], [1690, 760], [1690, 850], [1640, 850]], "telemetry 高频遥测", "data-flow", 1810, 742, false, 138);
arrow([[2215, 825], [2242, 825]], "控制", "flow", 2228, 806, false, 52);
arrow([[2242, 1040], [2215, 1040]], "状态与数据", "data-flow", 2228, 1066, false, 82);

// Bottom chain summary and legend
rect(425, 1296, 1550, 36, "label-bg");
text(1200, 1320, "工作流编排 → 任务实例化 → 状态机下发 → Adapter 执行 → 遥测与事件回传 → 观测与约束处置 → 日志审计", "small");
polyline([[70, 1321], [210, 1321]], "flow");
text(250, 1326, "控制 / 调用", "tiny", "start");
polyline([[2020, 1321], [2140, 1321]], "data-flow");
text(2160, 1326, "数据 / 遥测", "tiny", "start");

text(2350, 1377, "依据数据库表结构、系统技术架构与当前代码链路绘制", "tiny", "end");
out.push(`</svg>`);

await fs.mkdir("D:/SmartLab2.0/doc/generated_images", { recursive: true });
await fs.writeFile(outputPath, out.join("\n"), "utf8");
console.log(outputPath);
