<template>
  <section class="task-summary-strip" aria-label="任务状态概览">
    <button
      v-for="item in items"
      :key="item.status"
      type="button"
      :class="{ active: activeStatus === item.status }"
      :title="item.title"
      @click="emit('select-status', item.status)"
    >
      <span class="m-lbl">
        <i v-if="item.dot" :class="['summary-dot', item.dot]"></i>
        {{ item.label }}
      </span>
      <span :class="['m-val', item.tone]">{{ item.value }}</span>
    </button>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

type Summary = { total: number, pending: number, running: number, succeeded: number, failed: number, terminated: number }
const props = defineProps<{ summary: Summary, activeStatus: string }>()
const emit = defineEmits<{ 'select-status': [status: string] }>()

const items = computed(() => [
  { status: '', label: '全部任务', value: props.summary.total, dot: '', tone: '', title: '' },
  { status: 'PENDING', label: '排队中', value: props.summary.pending, dot: 'pending', tone: '', title: '' },
  { status: 'RUNNING', label: '运行中', value: props.summary.running, dot: 'running', tone: 'primary', title: '' },
  { status: 'SUCCEEDED', label: '已完成', value: props.summary.succeeded, dot: 'success', tone: '', title: '' },
  { status: 'FAILED', label: '失败', value: props.summary.failed, dot: 'danger', tone: '', title: props.summary.terminated ? `另 ${props.summary.terminated} 项已终止` : '' },
])
</script>

<style scoped>
.task-summary-strip {
  height: 35px;
  min-height: 35px;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  padding: 0 16px;
  gap: 16px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: #fafbfc;
  flex-shrink: 0;
  box-sizing: border-box;
}

.task-summary-strip > button {
  min-width: 0;
  height: 100%;
  padding: 0;
  border: none;
  display: flex;
  align-items: baseline;
  gap: 6px;
  color: inherit;
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.task-summary-strip > button.active .m-val {
  color: var(--sl-primary, #2563eb);
}

.m-lbl {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  line-height: 1.2;
  font-weight: 400;
  color: var(--sl-text-secondary, #64748b);
  flex-shrink: 0;
}

.m-val {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--sl-font-mono, ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace);
  font-size: 14.5px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--sl-text-heading, #0f172a);
}

.m-val.primary { color: var(--sl-primary, #2563eb); }

.summary-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--sl-text-disabled, #94a3b8);
}

.summary-dot.pending { background: var(--sl-text-secondary, #64748b); }
.summary-dot.running {
  background: var(--sl-primary, #2563eb);
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.2);
}
.summary-dot.success { background: var(--sl-success, #16a34a); }
.summary-dot.danger { background: var(--sl-danger, #dc2626); }

@media (max-width: 900px) {
  .task-summary-strip {
    grid-template-columns: repeat(5, minmax(120px, 1fr));
    overflow-x: auto;
  }
}
</style>
