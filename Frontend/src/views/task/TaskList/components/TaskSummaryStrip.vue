<template>
  <section class="task-summary-strip" aria-label="任务状态概览">
    <button
      v-for="item in items"
      :key="item.status"
      type="button"
      :class="{ active: activeStatus === item.status }"
      @click="emit('select-status', item.status)"
    >
      <div class="summary-meta">
        <span class="summary-label">
          <i v-if="item.dot" :class="['summary-dot', item.dot]"></i>
          {{ item.label }}
        </span>
        <small class="summary-note">{{ item.note }}</small>
      </div>
      <strong class="summary-val">{{ item.value }}</strong>
    </button>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

type Summary = { total: number, pending: number, running: number, succeeded: number, failed: number, terminated: number }
const props = defineProps<{ summary: Summary, activeStatus: string }>()
const emit = defineEmits<{ 'select-status': [status: string] }>()

const items = computed(() => [
  { status: '', label: '全部任务', value: props.summary.total, note: '所有执行记录', dot: '' },
  { status: 'PENDING', label: '排队中', value: props.summary.pending, note: '等待启动', dot: 'pending' },
  { status: 'RUNNING', label: '运行中', value: props.summary.running, note: '正在执行', dot: 'running' },
  { status: 'SUCCEEDED', label: '已完成', value: props.summary.succeeded, note: '执行成功', dot: 'success' },
  { status: 'FAILED', label: '失败任务', value: props.summary.failed, note: `另 ${props.summary.terminated} 项已终止`, dot: 'danger' },
])
</script>

<style scoped>
.task-summary-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: var(--sl-bg-surface, #ffffff);
  flex-shrink: 0;
}

.task-summary-strip > button {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  height: 60px;
  padding: 0 16px;
  border: none;
  border-right: 1px solid var(--sl-border-base, #e2e8f0);
  color: inherit;
  text-align: left;
  background: var(--sl-bg-surface, #ffffff);
  cursor: pointer;
  transition: background 0.15s ease;
  user-select: none;
}

.task-summary-strip > button:last-child {
  border-right: none;
}

.task-summary-strip > button:hover {
  background: var(--sl-bg-hover, #f8fafc);
}

.task-summary-strip > button.active {
  background: var(--sl-primary-light, #eff6ff);
}

.task-summary-strip > button.active::after {
  content: '';
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 2px;
  background: var(--sl-primary, #2563eb);
}

.summary-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.summary-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--sl-text-body, #334155);
}

.summary-note {
  overflow: hidden;
  color: var(--sl-text-secondary, #64748b);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-val {
  font-family: var(--sl-font-mono, monospace);
  font-size: 20px;
  font-weight: 700;
  color: var(--sl-text-heading, #0f172a);
  line-height: 1;
  margin-left: 8px;
}

.task-summary-strip > button.active .summary-val {
  color: var(--sl-primary, #2563eb);
}

.summary-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--sl-text-disabled, #94a3b8);
}

.summary-dot.pending {
  background: var(--sl-text-secondary, #64748b);
}

.summary-dot.running {
  background: var(--sl-primary, #2563eb);
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.2);
}

.summary-dot.success {
  background: var(--sl-success, #16a34a);
}

.summary-dot.danger {
  background: var(--sl-danger, #dc2626);
}

@media (max-width: 1024px) {
  .task-summary-strip {
    grid-template-columns: repeat(5, minmax(140px, 1fr));
    overflow-x: auto;
  }
}
</style>
