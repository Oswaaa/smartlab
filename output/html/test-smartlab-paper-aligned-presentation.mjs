import assert from 'node:assert/strict';
import fs from 'node:fs';

const file = new URL('./smartlab-paper-aligned-group-meeting.html', import.meta.url);
const html = fs.readFileSync(file, 'utf8');

assert.equal((html.match(/class="slide/g) || []).length, 11, '应包含 11 页内容');
assert.match(html, /1\. Introduction/, '应包含引言小节');
assert.match(html, /2\. Related Work/, '应包含相关工作小节');
assert.match(html, /3\.1 System Abstraction/, '应包含系统抽象小节');
assert.match(html, /模型逻辑图/, '应包含模型逻辑图');
assert.match(html, /系统链路图/, '应包含系统链路图');
assert.match(html, /跨层执行时序图/, '应包含跨层执行时序图');
assert.match(html, /viewBox="0 0 1600 900"/, '主要图应采用 16:9 SVG 画布');
assert.match(html, /addEventListener\('keydown'/, '应支持键盘导航');

console.log('PASS: paper-aligned 11-slide deck with logic, chain, and sequence diagrams.');
