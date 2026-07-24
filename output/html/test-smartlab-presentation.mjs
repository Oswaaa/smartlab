import assert from 'node:assert/strict';
import fs from 'node:fs';

const file = new URL('./smartlab-task-oriented-modeling-group-meeting.html', import.meta.url);
const html = fs.readFileSync(file, 'utf8');

assert.match(html, /<title>SmartLab/);
assert.equal((html.match(/class="slide/g) || []).length, 11, '应包含 11 页幻灯片');
assert.match(html, /function goTo\(/, '应包含幻灯片切换函数');
assert.match(html, /addEventListener\('keydown'/, '应支持键盘导航');
assert.match(html, /role="progressbar"/, '应包含进度条');

console.log('PASS: 11 slides and navigation hooks present.');
