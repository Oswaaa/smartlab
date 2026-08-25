<template>
  <section class="business-events">
    <div v-for="group in groups" :key="group.key" class="event-group">
      <h4>{{ group.label }}</h4>
      <div v-for="event in group.events" :key="event.id" class="event-row">
        <span class="event-time mono">{{ formatLogDateTime(event.logTime) }}</span>
        <span class="event-source">{{ sourceLabel(event.sourceType) }}</span>
        <span :class="['event-level', String(event.logLevel || '').toLowerCase()]">{{ event.logLevel || 'INFO' }}</span>
        <span class="event-text">{{ event.logInfo }}</span>
      </div>
    </div>
    <div v-if="!groups.length" class="events-empty">暂无业务事件</div>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { formatLogDateTime } from '../../../../utils/formatLogTime.js'
import { groupBusinessExecutionEvents } from '../../../../utils/workflowExecution.js'
type Item = Record<string, any>
const props = withDefaults(defineProps<{ logs: Item[], steps?: Item[], workflow?: Item | null }>(), {
  steps: () => [],
  workflow: null,
})
const groups = computed(() => groupBusinessExecutionEvents(props.logs, props.steps, props.workflow))
function sourceLabel(source: string) {
  return ({ TASK: '任务', MANUAL: '人工操作', CONSTRAINT: '任务约束', ADAPTER: '设备适配' } as Record<string, string>)[source] || '节点'
}
</script>
<style scoped>
.business-events { min-height: 100%; }
.event-group h4 {
  margin: 0;
  padding: 10px 16px 6px;
  color: var(--sl-text-heading, #0f172a);
  font-size: 12px;
  font-weight: 700;
  background: #fafbfc;
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9);
}
.event-row {
  display: grid;
  grid-template-columns: 176px 72px 52px minmax(0, 1fr);
  align-items: start;
  gap: 8px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9);
}
.event-row:hover { background: var(--sl-bg-hover, #f8fafc); }
.event-time { color: var(--sl-text-secondary, #64748b); font-size: 12px; line-height: 18px; white-space: nowrap; }
.mono { font-family: var(--sl-font-mono, ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace); }
.event-source { color: var(--sl-text-secondary, #64748b); font-size: 11.5px; line-height: 18px; }
.event-level { font-size: 11px; font-weight: 600; line-height: 18px; color: var(--sl-text-secondary, #64748b); }
.event-level.error { color: var(--sl-danger, #dc2626); }
.event-level.warn { color: var(--sl-warning, #d97706); }
.event-text { color: var(--sl-text-body, #334155); font-size: 12.5px; line-height: 20px; }
.events-empty {
  padding: 48px 16px;
  text-align: center;
  color: var(--sl-text-disabled, #94a3b8);
  font-size: 12.5px;
}
@media (max-width: 720px) {
  .event-row { grid-template-columns: 1fr auto; }
  .event-text { grid-column: 1 / -1; }
}
</style>
