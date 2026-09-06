import assert from "node:assert/strict";
import fs from "node:fs";
import { spawnSync } from "node:child_process";
import test from "node:test";

const generatorPath = "D:/SmartLab2.0/doc/generate_smartlab_five_module_box_diagram.mjs";
const outputPath = "D:/SmartLab2.0/doc/generated_images/07_SmartLab2.0_五模块系统结构图_全方框黑白线稿.svg";

function generateSvg() {
  const run = spawnSync(process.execPath, [generatorPath], { encoding: "utf8" });
  assert.equal(run.status, 0, `生成器执行失败：${run.stderr || run.stdout}`);
  assert.equal(fs.existsSync(outputPath), true, "生成器没有创建 SVG 文件");
  return fs.readFileSync(outputPath, "utf8");
}

test("生成的结构图包含五个一级模块及生产、仿真两个执行分支", () => {
  const svg = generateSvg();
  for (const label of ["Agent", "实验执行", "数据管理", "设备接入", "用户界面", "生产执行", "仿真执行"]) {
    assert.match(svg, new RegExp(`>${label}<`), `缺少模块：${label}`);
  }
});

test("所有结构元素使用直角方框和黑白线条", () => {
  const svg = generateSvg();
  assert.doesNotMatch(svg, /<(ellipse|circle|polygon)\b/i, "存在非方框图形元素");
  assert.doesNotMatch(svg, /\brx=["'](?!0(?:\.0+)?["'])/i, "存在圆角方框");
  assert.doesNotMatch(svg, /#[0-9a-f]{6}/gi, "存在非黑白十六进制颜色");
  assert.doesNotMatch(svg, /\b(fill|stroke)\s*:\s*(?!#000\b|#fff\b|none\b)[^;}]+/gi, "存在非黑白填充或线条");
});

test("新版结构图不再使用原四层、服务池或资源池术语", () => {
  const svg = generateSvg();
  for (const legacyLabel of ["Role Layer", "Service Pool", "Resource Library Layer", "资源池", "服务池"]) {
    assert.equal(svg.includes(legacyLabel), false, `仍包含旧术语：${legacyLabel}`);
  }
});
