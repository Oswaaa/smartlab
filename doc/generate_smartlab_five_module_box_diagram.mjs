import fs from "node:fs/promises";

const outputPath = "D:/SmartLab2.0/doc/generated_images/07_SmartLab2.0_五模块系统结构图_全方框黑白线稿.svg";
const W = 2400;
const H = 1500;
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

function moduleBox(x, y, w, h, title, subtitle = "") {
  rect(x, y, w, h, "module");
  rect(x, y, w, 54, "module-header");
  text(x + 22, y + 35, title, "module-title", "start");
  if (subtitle) text(x + w - 22, y + 34, subtitle, "module-subtitle", "end");
}

function itemBox(x, y, w, h, title, details = [], cls = "item") {
  rect(x, y, w, h, cls);
  text(x + w / 2, y + 31, title, "item-title");
  if (details.length) {
    const gap = details.length > 3 ? 18 : 21;
    const total = (details.length - 1) * gap;
    const top = y + Math.max(59, (h + 32 - total) / 2);
    lines(x + w / 2, top, details, details.some((d) => d.length > 32) ? "tiny" : "small", gap);
  }
}

out.push(`<?xml version="1.0" encoding="UTF-8"?>`);
out.push(`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">`);
out.push(`<defs><style>
  .title{font:700 31px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#000}
  .subtitle{font:14px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#000}
  .module-title{font:700 20px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#000}
  .module-subtitle{font:13px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#000}
  .item-title{font:700 16px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#000}
  .label{font:15px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#000}
  .small{font:13px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#000}
  .tiny{font:11.5px "Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#000}
  .background{fill:#fff;stroke:none}
  .outer{fill:#fff;stroke:#000;stroke-width:2}
  .module{fill:#fff;stroke:#000;stroke-width:2.2}
  .module-header{fill:#fff;stroke:#000;stroke-width:1.5}
  .item{fill:#fff;stroke:#000;stroke-width:1.4}
  .primary-item{fill:#fff;stroke:#000;stroke-width:1.8}
  .minor-item{fill:#fff;stroke:#000;stroke-width:1.1}
</style></defs>`);

rect(0, 0, W, H, "background");
rect(24, 26, 2352, 1448, "outer");
text(1200, 72, "SmartLab 2.0 智能实验室系统结构图", "title");
text(1200, 101, "由 Agent、实验执行、数据管理、设备接入和用户界面五个功能模块组成", "subtitle");

// 用户界面
moduleBox(90, 135, 2220, 248, "用户界面", "User Interface");
itemBox(120, 214, 405, 132, "科研人员工作台", ["实验需求输入", "流程设计与仿真发起"]);
itemBox(555, 214, 405, 132, "可视化流程设计", ["节点、接口与连接编辑", "流程校验及版本管理"]);
itemBox(990, 214, 405, 132, "任务控制与监控", ["启动、暂停、终止", "步骤状态与异常查看"]);
itemBox(1425, 214, 405, 132, "数据查询与展示", ["实验数据检索", "实时曲线与历史回放"]);
itemBox(1860, 214, 420, 132, "系统管理", ["用户权限与菜单", "资源、设备与接入配置"]);

// Agent
moduleBox(90, 430, 665, 525, "Agent", "Intelligent Workflow Agent");
itemBox(120, 509, 290, 126, "需求理解", ["自然语言实验需求", "目标、条件与约束提取"]);
itemBox(435, 509, 290, 126, "资源与能力检索", ["设备模型与能力目录", "已有流程及约束规则"]);
itemBox(120, 661, 605, 120, "实验流程规划与生成", ["生成流程节点、连接关系、参数及执行条件"], "primary-item");
itemBox(120, 807, 290, 118, "结构校验与修复", ["Schema 校验", "错误定位与自动修复"]);
itemBox(435, 807, 290, 118, "仿真反馈优化", ["调用仿真执行", "依据结果迭代流程"]);

// 实验执行
moduleBox(795, 430, 1515, 525, "实验执行", "Production / Simulation");
itemBox(825, 509, 705, 416, "生产执行", [
  "任务实例化与步骤调度",
  "工作流引擎：冻结快照、上升沿求值",
  "连接通道：接口信号转发、端口数据拉取",
  "设备状态机：CMD / OP 双状态空间",
  "约束控制：终止、暂停、告警与设备保护",
  "执行状态、步骤结果与日志记录"
], "primary-item");
itemBox(1570, 509, 710, 416, "仿真执行", [
  "仿真任务与虚拟时钟",
  "虚拟设备及数字孪生状态演化",
  "工作流逻辑与连接关系求值",
  "约束规则和异常场景验证",
  "运行快照、仿真数据与结果对比",
  "向 Agent 返回校验与优化依据"
], "primary-item");

// 数据管理
moduleBox(90, 1005, 1365, 405, "数据管理", "Data Management");
itemBox(120, 1084, 245, 286, "元数据", [
  "用户与权限",
  "属性类型",
  "场景与资源结构"
]);
itemBox(385, 1084, 245, 286, "实验数据", [
  "数据模板",
  "数据索引",
  "动态实验记录"
]);
itemBox(650, 1084, 245, 286, "设备与孪生", [
  "设备模型与实例",
  "设备组件",
  "孪生状态"
]);
itemBox(915, 1084, 245, 286, "流程与任务", [
  "流程模型与节点",
  "任务与步骤",
  "执行日志"
]);
itemBox(1180, 1084, 245, 286, "约束与审计", [
  "约束规则",
  "违规记录",
  "现场快照"
]);

// 设备接入
moduleBox(1495, 1005, 815, 405, "设备接入", "Device Access");
itemBox(1525, 1084, 240, 125, "消息接入", ["MQTT Broker", "命令、遥测、事件、心跳"]);
itemBox(1785, 1084, 240, 125, "Adapter Runtime", ["注册、配置与租约", "生命周期及看门狗"]);
itemBox(2045, 1084, 235, 125, "协议连接器", ["Modbus、串口、TCP", "HTTP、SDK"]);
itemBox(1525, 1235, 365, 135, "物理设备", ["反应釜、泵阀、机械臂、PLC", "传感器与分析仪器"], "primary-item");
itemBox(1915, 1235, 365, 135, "虚拟设备", ["仿真实例与虚拟租约", "统一设备能力及状态接口"], "primary-item");

text(1200, 1448, "本图仅表示系统功能组成与包含关系，不表示分层结构或固定调用顺序。", "subtitle");
out.push(`</svg>`);

await fs.mkdir("D:/SmartLab2.0/doc/generated_images", { recursive: true });
await fs.writeFile(outputPath, out.join("\n"), "utf8");
console.log(outputPath);
