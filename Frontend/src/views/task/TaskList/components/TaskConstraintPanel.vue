<template>
  <div class="task-constraint-section">
    <div class="section-card-head">
      <div class="section-card-title">
        <span class="sec-idx-badge">03</span>
        <span class="sec-title-text">任务级约束</span>
        <span class="sec-desc-text">随当前任务保存，仅观测并保护本任务及其绑定的设备实例</span>
      </div>
      <button class="btn-aliyun-cta" type="button" @click="emit('edit')">
        <el-icon><Plus /></el-icon><span>添加任务约束</span>
      </button>
    </div>
    
    <div class="section-card-body">
      <!-- 彻底去掉纸箱图标，采用统一紧凑空状态 -->
      <div v-if="!rules.length" class="empty-plain">
        <strong>未配置任务级约束</strong>
        <span>如需针对本任务设置温度/压力越限保护，请点击右上角“添加任务约束”</span>
      </div>

      <!-- 约束规则卡片列表 -->
      <div v-else class="constraint-card-list">
        <div v-for="(rule, index) in rules" :key="index" class="constraint-item-card" :class="{ 'has-warning': reviews[index] }">
          <div class="constraint-info-left">
            <div class="constraint-title-row">
              <strong>{{ rule.ruleName }}</strong>
              <span class="constraint-formula-code">{{ rule.expression }}</span>
              <span v-if="reviews[index]" class="badge-review-warn">需要复核</span>
            </div>
            <div class="constraint-meta-hint">
              <span>{{ Object.keys(rule.bindings || {}).length }} 个变量 · {{ (rule.violationActions || []).length }} 个动作</span>
              <span v-if="reviews[index]" class="review-message">（{{ reviews[index] }}）</span>
            </div>
          </div>
          <div class="constraint-item-actions">
            <button class="btn-link-action" type="button" @click="emit('edit', index)">编辑</button>
            <button class="btn-link-danger" type="button" @click="emit('remove', index)">删除</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue'

type Item = Record<string, any>
withDefaults(defineProps<{ rules: Item[], reviews?: Array<string | null> }>(), { reviews: () => [] })
const emit = defineEmits<{ edit: [index?: number], remove: [index: number] }>()
</script>

<style scoped>
.task-constraint-section {
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

.section-card-body {
  padding: 0;
}

/* 按钮规范 */
.btn-aliyun-cta {
  height: 24px;
  padding: 0 10px;
  font-size: 11px;
}

.btn-link-action {
  border: none;
  background: transparent;
  color: var(--sl-primary, #2563eb);
  font-size: 11px;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 3px;
  transition: var(--sl-ease-smooth, all 0.18s ease);
}
.btn-link-action:hover {
  text-decoration: underline;
}

.btn-link-danger {
  border: none;
  background: transparent;
  color: var(--sl-danger, #dc2626);
  font-size: 11px;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 3px;
  transition: var(--sl-ease-smooth, all 0.18s ease);
}
.btn-link-danger:hover {
  text-decoration: underline;
}

.empty-plain {
  padding: 14px 14px 16px;
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

/* 约束规则卡片列表 */
.constraint-card-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px 14px;
}

.constraint-item-card {
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 4px);
  padding: 8px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  transition: var(--sl-ease-smooth, all 0.18s ease);
}
.constraint-item-card:hover {
  border-color: #cbd5e1;
  background: var(--sl-bg-hover, #f8fafc);
}
.constraint-item-card.has-warning {
  background: #fffdf5;
  border-color: #fde68a;
}

.constraint-info-left {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.constraint-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.constraint-title-row strong {
  font-size: 12px;
  color: var(--sl-text-heading, #0f172a);
}

.badge-review-warn {
  font-size: 10px;
  padding: 0 4px;
  border-radius: 2px;
  background: var(--sl-warning-light, #fffbeb);
  color: var(--sl-warning, #d97706);
  border: 1px solid var(--sl-warning-border, #fde68a);
  font-weight: 600;
}

.constraint-formula-code {
  font-family: var(--sl-font-mono, monospace);
  font-size: 11px;
  color: var(--sl-primary, #2563eb);
  font-weight: 600;
}

.constraint-meta-hint {
  font-size: 10.5px;
  color: var(--sl-text-secondary, #64748b);
}
.review-message {
  color: var(--sl-warning, #d97706);
}

.constraint-item-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
