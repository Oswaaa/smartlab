<template>
  <div class="task-preflight-section" :class="{ 'is-compact': !loading && !result }">
    <div class="section-card-head">
      <div class="section-card-title">
        <span class="sec-idx-badge">04</span>
        <span class="sec-title-text">创建前检查</span>
        <span class="sec-desc-text">全面校验设备绑定完整度、任务级约束有效性与流程可执行条件</span>
      </div>
      <button v-if="!loading" class="btn-aliyun" type="button" @click="$emit('retry')">
        {{ result ? '重新检查' : '检查' }}
      </button>
    </div>

    <div v-if="loading || result" class="section-card-body padded">
      <div v-if="loading" class="preflight-loading">
        <el-skeleton :rows="2" animated />
        <span>正在检查任务前置条件...</span>
      </div>

      <template v-else-if="result">
        <!-- 检查结果 Banner (无纸箱插画) -->
        <div class="preflight-banner" :class="{ ready: result.ready, blocked: !result.ready }">
          <el-icon :size="20"><component :is="result.ready ? CircleCheckFilled : WarningFilled" /></el-icon>
          <div class="banner-text-group">
            <strong>{{ result.ready ? '前置检查全部通过，具备创建与启动条件' : '前置检查未通过' }}</strong>
            <span>{{ result.ready ? '所有节点设备已成功分配实例，能力参数完整，约束规则校验无冲突。' : (result.message || '请修复以下阻断项后重新检查') }}</span>
          </div>
        </div>

        <!-- 详细检查项网格 -->
        <div v-if="result.checks?.length" class="preflight-checks-grid">
          <div v-for="check in result.checks" :key="check.code || check.label" class="check-item-cell">
            <el-icon :size="15" :class="check.passed ? 'check-passed' : 'check-failed'">
              <component :is="check.passed ? CircleCheck : CircleClose" />
            </el-icon>
            <div class="check-info-wrap">
              <strong>{{ check.label || check.message || '检查项' }}</strong>
              <span v-if="!check.passed">{{ check.detail || check.message }}</span>
            </div>
            <el-tag v-if="check.severity === 'ERROR'" size="small" type="danger">阻断</el-tag>
            <el-tag v-else-if="check.severity === 'WARNING'" size="small" type="warning">提醒</el-tag>
          </div>
        </div>

        <div v-if="result.issues?.length" class="preflight-issues">
          <div v-for="(issue, index) in result.issues" :key="issue.elementId || issue.code || index" class="preflight-issue-row">
            <el-tag size="small" :type="issue.blocking ? 'danger' : 'warning'">{{ issue.blocking ? '阻断' : '提醒' }}</el-tag>
            <div><strong>{{ presentPreflightIssue(issue).title }}</strong><span>{{ presentPreflightIssue(issue).detail }}</span></div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { CircleCheck, CircleClose, CircleCheckFilled, WarningFilled } from '@element-plus/icons-vue'
import { presentPreflightIssue } from '../taskPreflightPresentation.js'

type Item = Record<string, any>
defineProps<{ result?: Item | null, loading?: boolean }>()
defineEmits<{ retry: [] }>()
</script>

<style scoped>
.task-preflight-section {
  display: flex;
  flex-direction: column;
  background: #ffffff;
}

.section-card-head {
  background: #ffffff;
  padding: 8px 14px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sec-idx-badge {
  display: inline-flex;
  width: 22px;
  height: 18px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--sl-primary-border, #bfdbfe);
  border-radius: 3px;
  background: var(--sl-primary-light, #eff6ff);
  color: var(--sl-primary, #2563eb);
  font-size: 11px;
  font-weight: 700;
  font-family: var(--sl-font-mono, monospace);
  flex-shrink: 0;
}

.sec-title-text {
  font-size: 13px;
  font-weight: 700;
  color: var(--sl-text-heading, #0f172a);
}

.sec-desc-text {
  font-size: 11.5px;
  color: var(--sl-text-secondary, #64748b);
  margin-left: 4px;
  font-weight: 400;
}

.section-card-body.padded {
  padding: 10px 14px;
}

.task-preflight-section.is-compact .section-card-head {
  border-bottom: 0;
}

/* 按钮规范 */
.btn-aliyun {
  height: 24px;
  padding: 0 10px;
  font-size: 11px;
  font-weight: 500;
  color: var(--sl-text-body, #334155);
  background: #ffffff;
  border: 1px solid var(--sl-border-input, #cbd5e1);
  border-radius: var(--sl-radius-sm, 4px);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: var(--sl-ease-smooth, all 0.18s ease);
}
.btn-aliyun:hover {
  border-color: #94a3b8;
  background: var(--sl-bg-hover, #f8fafc);
  color: var(--sl-text-heading, #0f172a);
}

.empty-plain {
  padding: 8px 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.empty-plain strong {
  font-size: 12px;
  color: var(--sl-text-heading, #0f172a);
}
.empty-plain span {
  font-size: 11.5px;
  color: var(--sl-text-secondary, #64748b);
  line-height: 1.5;
}

.preflight-loading {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 0;
  font-size: 11.5px;
  color: var(--sl-text-secondary, #64748b);
}

/* 检查结果 Banner */
.preflight-banner {
  padding: 8px 12px;
  border-radius: var(--sl-radius-sm, 4px);
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.preflight-banner.ready {
  background: var(--sl-success-light, #f0fdf4);
  border: 1px solid var(--sl-success-border, #bbf7d0);
  color: var(--sl-success, #16a34a);
}
.preflight-banner.blocked {
  background: var(--sl-danger-light, #fef2f2);
  border: 1px solid var(--sl-danger-border, #fecaca);
  color: var(--sl-danger, #dc2626);
}
.banner-text-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.banner-text-group strong {
  font-size: 12px;
  font-weight: 700;
}
.banner-text-group span {
  font-size: 11px;
  color: var(--sl-text-secondary, #64748b);
}

/* 详细检查项网格 */
.preflight-checks-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 6px;
}
.check-item-cell {
  padding: 6px 10px;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 4px);
  background: #ffffff;
  display: flex;
  align-items: center;
  gap: 8px;
}
.check-passed { color: var(--sl-success, #16a34a); }
.check-failed { color: var(--sl-danger, #dc2626); }
.check-info-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.check-info-wrap strong {
  font-size: 11.5px;
  color: var(--sl-text-heading, #0f172a);
}
.check-info-wrap span {
  font-size: 10.5px;
  color: var(--sl-danger, #dc2626);
}

.preflight-issues {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 6px;
}
.preflight-issue-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 4px);
  background: #ffffff;
}
.preflight-issue-row strong {
  font-size: 11.5px;
  color: var(--sl-text-heading, #0f172a);
}
.preflight-issue-row span {
  font-size: 10.5px;
  color: var(--sl-text-secondary, #64748b);
}
</style>
