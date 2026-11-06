<template>
  <article class="confirm-card" :class="stateClass">
    <header class="card-head">
      <h3 class="card-title">{{ card.title || '确认操作' }}</h3>
      <span class="state-pill" :class="stateClass">{{ stateLabel }}</span>
    </header>

    <p v-if="card.summary" class="card-summary">{{ card.summary }}</p>

    <section v-if="rows.length" class="card-block">
      <div v-for="(row, index) in rows" :key="'row-' + index" class="card-row">
        <span class="row-label">{{ row.label }}</span>
        <span class="row-value">{{ row.value }}</span>
        <span v-if="row.source" class="source-badge" :class="badgeClass(row.source)">
          {{ badgeText(row.source) }}
        </span>
      </div>
    </section>

    <section v-if="parameters.length" class="card-block">
      <p class="block-label">
        {{ isFlowDraft ? '节点' : '参数' }}
        <span v-if="isFlowDraft" class="block-count">共 {{ parameters.length }} 个</span>
      </p>
      <div v-for="(row, index) in parameters" :key="'param-' + index" class="card-row">
        <span class="row-label">{{ row.label }}</span>
        <span class="row-value" :class="{ mono: !isFlowDraft }">{{ row.value }}</span>
        <span v-if="row.source" class="source-badge" :class="badgeClass(row.source)">
          {{ badgeText(row.source) }}
        </span>
      </div>
      <p v-if="isFlowDraft && parameters.length === 0" class="block-empty">这份草稿里还没有节点</p>
    </section>

    <section v-if="visibleBindings.length" class="card-block">
      <p class="block-label">
        设备绑定
        <span class="block-count">共 {{ bindings.length }} 条</span>
      </p>
      <div v-for="(row, index) in visibleBindings" :key="'bind-' + index" class="card-row">
        <span class="row-label">{{ row.label }}</span>
        <span class="row-value">{{ row.value }}</span>
        <span v-if="row.source" class="source-badge" :class="badgeClass(row.source)">
          {{ badgeText(row.source) }}
        </span>
      </div>
      <button
        v-if="bindings.length > COLLAPSED_BINDINGS"
        type="button"
        class="expand-btn"
        @click="bindingsExpanded = !bindingsExpanded"
      >
        {{ bindingsExpanded ? '收起' : `展开其余 ${bindings.length - COLLAPSED_BINDINGS} 条` }}
      </button>
    </section>

    <section v-if="warnings.length" class="card-block">
      <p class="block-label">提醒</p>
      <div v-for="(item, index) in warnings" :key="'warn-' + index" class="issue-box warning">
        {{ item.text }}
      </div>
    </section>

    <section v-if="blocking.length" class="card-block">
      <p class="block-label">阻断项</p>
      <div v-for="(item, index) in blocking" :key="'block-' + index" class="issue-box danger">
        {{ item.text }}
      </div>
    </section>

    <p v-if="resultText" class="result-line" :class="stateClass">{{ resultText }}</p>

    <footer v-if="state === 'PENDING' || state === 'BUSY'" class="card-foot">
      <button type="button" class="btn-ghost" :disabled="state === 'BUSY'" @click="$emit('cancel')">
        取消
      </button>
      <button
        v-if="canSimulate"
        type="button"
        class="btn-sim"
        :disabled="state === 'BUSY' || !canConfirm"
        @click="$emit('simulate')"
      >
        {{ state === 'BUSY' && busyAction === 'simulate' ? '仿真中…' : '仿真执行' }}
      </button>
      <button
        type="button"
        class="btn-solid"
        :disabled="!canConfirm"
        @click="$emit('confirm')"
      >
        {{ state === 'BUSY' ? (busyAction === 'simulate' ? '仿真中…' : busyLabel) : confirmLabel }}
      </button>
    </footer>

    <footer v-else-if="state === 'DONE' && detail" class="card-foot plain">
      <span v-for="(value, key) in detailRow" :key="key" class="detail-chip">
        {{ key }} {{ value }}
      </span>
    </footer>
  </article>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  card: { type: Object, required: true },
  state: { type: String, default: 'PENDING' },
  result: { type: String, default: '' },
  detail: { type: Object, default: null },
  busyAction: { type: String, default: '' }
})

defineEmits(['confirm', 'cancel', 'simulate'])

const COLLAPSED_BINDINGS = 3
const bindingsExpanded = ref(false)

const rows = computed(() => props.card?.rows || [])
const parameters = computed(() => props.card?.parameters || [])
const bindings = computed(() => props.card?.bindings || [])
const warnings = computed(() => props.card?.warnings || [])
const blocking = computed(() => props.card?.blocking || [])

const visibleBindings = computed(() => {
  if (!bindings.value.length) return []
  return bindingsExpanded.value ? bindings.value : bindings.value.slice(0, COLLAPSED_BINDINGS)
})

const isFlowDraft = computed(() => props.card?.kind === 'FLOW_DRAFT')
const canSimulate = computed(() => props.card?.canSimulate === true)
const isTaskCard = computed(() => props.card?.kind === 'CREATE_TASK' || props.card?.kind === 'START_TASK')
const confirmLabel = computed(() => {
  if (props.card?.kind === 'CREATE_TASK') return '确认创建'
  if (props.card?.kind === 'START_TASK') return '确认启动'
  if (isFlowDraft.value) return '确认保存草稿'
  return '确认执行'
})

const busyLabel = computed(() => (isFlowDraft.value ? '保存中…' : '执行中…'))

const canConfirm = computed(() => props.state === 'PENDING' && props.card?.ready === true)

const resultText = computed(() => {
  if (props.state === 'DONE' || props.state === 'BLOCKED') return props.result
  if (props.state === 'CANCELLED') return isFlowDraft.value ? '已取消，草稿没有保存' : '已取消，设备没有任何动作'
  if (props.state === 'EXPIRED') return '待确认已过期，请重新说一遍要做什么'
  return ''
})

const detailRow = computed(() => {
  const detail = props.detail
  if (!detail || typeof detail !== 'object') return {}
  const picked = {}
  if (detail.flowModelId) picked['草稿 id'] = detail.flowModelId
  if (detail.taskId) picked['任务 id'] = detail.taskId
  if (detail.taskStatus) picked['状态'] = detail.taskStatus
  if (detail.status) picked['状态'] = detail.status
  if (detail.instanceName) picked['设备'] = detail.instanceName
  return picked
})

const stateLabel = computed(() => {
  switch (props.state) {
    case 'PENDING': return '待确认'
    case 'BUSY': return props.busyAction === 'simulate' ? '仿真中' : (isFlowDraft.value ? '保存中' : '执行中')
    case 'DONE': return isFlowDraft.value ? '已保存' : '已执行'
    case 'BLOCKED': return '不可执行'
    case 'CANCELLED': return '已取消'
    case 'EXPIRED': return '已过期'
    default: return '待确认'
  }
})

const stateClass = computed(() => String(props.state || 'PENDING').toLowerCase())

function badgeText(source) {
  switch (source) {
    case 'USER': return '用户指定'
    case 'DEFAULT': return '服务端补全'
    case 'AGENT_INFERRED': return 'Agent 推断'
    default: return source
  }
}

function badgeClass(source) {
  switch (source) {
    case 'USER': return 'success'
    case 'DEFAULT': return 'warning'
    case 'AGENT_INFERRED': return 'info'
    default: return 'neutral'
  }
}
</script>

<style scoped>
.confirm-card {
  background: var(--sl-bg-surface);
  border: 1px solid var(--sl-border-base);
  border-left: 3px solid var(--sl-warning);
  border-radius: var(--sl-radius-md);
  padding: 14px 16px;
  font-size: 13px;
  color: var(--sl-text-body);
  box-shadow: var(--sl-shadow-sm);
}
.confirm-card.blocked { border-left-color: var(--sl-danger); }
.confirm-card.done { border-left-color: var(--sl-success); }
.confirm-card.cancelled,
.confirm-card.expired { border-left-color: var(--sl-text-disabled); }
.confirm-card.busy { border-left-color: var(--sl-primary); }

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}
.card-title {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--sl-text-heading);
  line-height: 1.45;
}
.card-summary {
  margin: 4px 0 0;
  font-size: 12.5px;
  color: var(--sl-text-secondary);
  line-height: 1.6;
  white-space: pre-line;
}

.state-pill {
  flex: none;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  line-height: 18px;
  border: 1px solid transparent;
}
.state-pill.pending { background: var(--sl-warning-light); color: var(--sl-warning); border-color: var(--sl-warning-border); }
.state-pill.busy { background: var(--sl-primary-light); color: var(--sl-primary); border-color: var(--sl-primary-border); }
.state-pill.done { background: var(--sl-success-light); color: var(--sl-success); border-color: var(--sl-success-border); }
.state-pill.blocked { background: var(--sl-danger-light); color: var(--sl-danger); border-color: var(--sl-danger-border); }
.state-pill.cancelled,
.state-pill.expired { background: var(--sl-bg-hover); color: var(--sl-text-secondary); border-color: var(--sl-border-base); }

.card-block { margin-top: 14px; }
.block-label {
  margin: 0 0 4px;
  font-size: 11px;
  color: var(--sl-text-secondary);
  display: flex;
  align-items: center;
  gap: 6px;
}
.block-count {
  color: var(--sl-text-disabled);
}

.card-row {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px solid var(--sl-border-subtle);
}
.card-row:last-child { border-bottom: none; }
.row-label {
  color: var(--sl-text-secondary);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row-value {
  color: var(--sl-text-body);
  font-size: 12.5px;
  word-break: break-all;
}
.row-value.mono {
  font-family: var(--sl-font-mono);
  font-variant-numeric: tabular-nums;
}

.source-badge {
  flex: none;
  padding: 0 7px;
  border-radius: 999px;
  font-size: 11px;
  line-height: 17px;
  border: 1px solid transparent;
}
.source-badge.success { background: var(--sl-success-light); color: var(--sl-success); border-color: var(--sl-success-border); }
.source-badge.warning { background: var(--sl-warning-light); color: var(--sl-warning); border-color: var(--sl-warning-border); }
.source-badge.info { background: var(--sl-info-light); color: var(--sl-info); border-color: var(--sl-info-border); }
.source-badge.neutral { background: var(--sl-bg-hover); color: var(--sl-text-secondary); border-color: var(--sl-border-base); }

.issue-box {
  padding: 7px 10px;
  border-radius: var(--sl-radius-sm);
  font-size: 12px;
  line-height: 1.6;
  margin-bottom: 4px;
}
.issue-box.warning {
  background: var(--sl-warning-light);
  border: 1px solid var(--sl-warning-border);
  color: var(--sl-warning);
}
.issue-box.danger {
  background: var(--sl-danger-light);
  border: 1px solid var(--sl-danger-border);
  color: var(--sl-danger);
}

.expand-btn {
  margin-top: 6px;
  padding: 0;
  background: none;
  border: none;
  color: var(--sl-primary);
  font-size: 12px;
  cursor: pointer;
}
.expand-btn:hover { text-decoration: underline; }

.block-empty {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--sl-text-disabled);
}

.result-line {
  margin: 12px 0 0;
  padding: 7px 10px;
  border-radius: var(--sl-radius-sm);
  font-size: 12px;
  line-height: 1.6;
}
.result-line.done { background: var(--sl-success-light); color: var(--sl-success); }
.result-line.blocked { background: var(--sl-danger-light); color: var(--sl-danger); }
.result-line.cancelled,
.result-line.expired { background: var(--sl-bg-hover); color: var(--sl-text-secondary); }

.card-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--sl-border-subtle);
}
.card-foot.plain { flex-wrap: wrap; gap: 6px; }

.detail-chip {
  font-size: 11.5px;
  color: var(--sl-text-secondary);
  background: var(--sl-bg-hover);
  border-radius: var(--sl-radius-sm);
  padding: 2px 8px;
}

.btn-ghost,
.btn-solid,
.btn-sim {
  padding: 5px 16px;
  border-radius: var(--sl-radius-sm);
  font-size: 12.5px;
  cursor: pointer;
  transition: var(--sl-ease-smooth);
}
.btn-ghost {
  background: var(--sl-bg-surface);
  border: 1px solid var(--sl-border-input);
  color: var(--sl-text-body);
}
.btn-ghost:hover:not(:disabled) { background: var(--sl-bg-hover); }
.btn-sim {
  background: var(--sl-primary-light);
  border: 1px solid var(--sl-primary-border);
  color: var(--sl-primary);
}
.btn-sim:hover:not(:disabled) { background: var(--sl-bg-hover); }
.btn-solid {
  background: var(--sl-primary);
  border: 1px solid var(--sl-primary);
  color: #fff;
}
.btn-solid:hover:not(:disabled) { background: var(--sl-primary-hover); }
.btn-ghost:disabled,
.btn-sim:disabled,
.btn-solid:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
