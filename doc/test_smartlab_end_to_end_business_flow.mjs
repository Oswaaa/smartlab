import assert from "node:assert/strict";
import fs from "node:fs";
import { spawnSync } from "node:child_process";
import test from "node:test";

const generatorPath = "D:/SmartLab2.0/doc/generate_smartlab_end_to_end_business_flow.mjs";
const outputPath = "D:/SmartLab2.0/doc/generated_images/08_SmartLab2.0_端到端业务流程架构图_黑白线稿.svg";

function generateSvg() {
  const run = spawnSync(process.execPath, [generatorPath], { encoding: "utf8" });
  assert.equal(run.status, 0, `生成器执行失败：${run.stderr || run.stdout}`);
  assert.equal(fs.existsSync(outputPath), true, "生成器没有创建 SVG 文件");
  return fs.readFileSync(outputPath, "utf8");
}

test("用户业务主轴按端到端顺序贯穿九个业务步骤", () => {
  const svg = generateSvg();
  const stages = [
    "用户登录与实验入口",
    "描述实验需求与设计流程",
    "Agent 生成并校验流程",
    "用户确认并启动",
    "任务与步骤实例化",
    "选择执行方式",
    "设备或虚拟设备运行",
    "实时监控与约束处置",
    "结果查询与实验追溯"
  ];
  let previous = -1;
  for (const stage of stages) {
    const position = svg.indexOf(`>${stage}<`);
    assert.ok(position > previous, `业务步骤缺失或顺序错误：${stage}`);
    previous = position;
  }
  assert.ok((svg.match(/class="main-flow"/g) || []).length >= 8, "业务主轴连接不足");
});

test("生产和仿真分支在执行链上出现并汇合到统一监控", () => {
  const svg = generateSvg();
  for (const label of ["生产执行", "仿真执行", "设备或虚拟设备运行", "统一观测与反馈"]) {
    assert.match(svg, new RegExp(`>${label}<`), `缺少执行分支或汇合节点：${label}`);
  }
});

test("内部机制和数据库落点同时挂接到业务主轴", () => {
  const svg = generateSvg();
  for (const label of [
    "Agent 内部机制",
    "任务编排与执行控制",
    "设备执行机制",
    "观测与反馈机制",
    "FLOW_MODELS / FLOW_NODE",
    "TASK / TASK_STEP / EXECUTION_LOG",
    "DATA_TEMPLATE / DATA_INDEX / DATA_RECORD_XXXX",
    "DEVICE_TWIN_STATES",
    "CONSTRAINT_RULE / VIOLATION_LOG"
  ]) {
    assert.ok(svg.includes(`>${label}<`), `缺少机制或数据落点：${label}`);
  }
  assert.ok((svg.match(/class="mechanism-flow"/g) || []).length >= 4, "内部机制挂接不足");
  assert.ok((svg.match(/class="landing-flow"/g) || []).length >= 5, "数据落点挂接不足");
});

test("图形保持直角方框和黑白线稿", () => {
  const svg = generateSvg();
  assert.doesNotMatch(svg, /<(ellipse|circle|polygon)\b/i, "存在非方框图形元素");
  assert.doesNotMatch(svg, /\brx=["'](?!0(?:\.0+)?["'])/i, "存在圆角方框");
  assert.doesNotMatch(svg, /#[0-9a-f]{6}/gi, "存在非黑白十六进制颜色");
  for (const legacyLabel of ["服务池", "资源池", "Role Layer", "Resource Library Layer"]) {
    assert.equal(svg.includes(legacyLabel), false, `仍包含旧架构术语：${legacyLabel}`);
  }
});
