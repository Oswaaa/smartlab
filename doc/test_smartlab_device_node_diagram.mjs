import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const generator = join(here, 'generate_smartlab_device_node_diagram.mjs');
const output = join(here, 'generated_images', '09_SmartLab2.0_设备能力节点示意图_黑白线稿.svg');

test('生成完整、全方框、黑白线稿的 DEV_NODE 示意图', () => {
  assert.ok(existsSync(generator), '缺少设备能力节点示意图生成器');

  const run = spawnSync(process.execPath, [generator], {
    cwd: here,
    encoding: 'utf8',
  });
  assert.equal(run.status, 0, run.stderr || run.stdout);
  assert.ok(existsSync(output), '未生成 SVG 文件');

  const svg = readFileSync(output, 'utf8');
  const requiredLabels = [
    'SmartLab 2.0 设备能力节点（DEV_NODE）示意图',
    '节点身份与资源引用',
    '设备能力与参数',
    '内部变量空间',
    '节点生命周期',
    '接口触发器',
    '节点动作',
    '任务步骤运行时',
    'WORKFLOW IN',
    'WORKFLOW OUT',
    'STATE IN',
    'STATE OUT',
    'PORT IN',
    'PORT OUT',
    '工作流引擎',
    '连接通道',
    '设备状态机',
    'Adapter 与物理/虚拟设备',
    '统一观测与约束引擎',
    'ACTIVE',
    'CMD_STATE',
    'WF_EXECUTE_START',
    'WF_EXECUTE_ABORT',
    'UPDATE',
    'EMIT',
    'TASK_STEP',
    'deviceInstanceId',
    'messageId',
  ];

  for (const label of requiredLabels) {
    assert.ok(svg.includes(label), `缺少关键元素：${label}`);
  }

  assert.doesNotMatch(svg, /<(ellipse|circle|polygon)\b/i, '图中存在非方框元素');
  assert.doesNotMatch(svg, /\brx\s*=\s*["'](?!0(?:\.0+)?["'])/i, '图中存在圆角方框');
  assert.doesNotMatch(svg, /#[0-9a-fA-F]{6}/, '图中存在彩色十六进制颜色');
  assert.match(svg, /class="control-flow"/);
  assert.match(svg, /class="data-flow"/);
  assert.match(svg, /class="observe-flow"/);
  assert.match(svg, /marker-end="url\(#arrow\)"/);

  const rectCount = (svg.match(/<rect\b/g) || []).length;
  assert.ok(rectCount >= 30, `方框数量不足：${rectCount}`);
});
