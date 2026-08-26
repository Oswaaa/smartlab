<template>
  <span v-if="kind === 'empty'" class="runtime-value is-empty">—</span>
  <span v-else-if="kind === 'scalar'" :class="['runtime-value', { 'is-empty': value == null }]">{{ text }}</span>
  <dl v-else-if="kind === 'fields'" class="runtime-fields">
    <div v-for="[name, nested] in fields" :key="name">
      <dt>{{ name }}</dt>
      <dd><RuntimeStructuredValue :value="nested" :depth="depth + 1" /></dd>
    </div>
  </dl>
  <WorkflowJsonValue v-else :value="value" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import WorkflowJsonValue from '../../components/WorkflowJsonValue.vue'
import {
  formatSnapshotScalar,
  hasSnapshotPayload,
  isSnapshotScalar,
  snapshotObjectEntries,
} from '../../../../utils/workflowExecution.js'

const props = withDefaults(defineProps<{ value?: unknown, depth?: number }>(), { depth: 0 })

const fields = computed(() => snapshotObjectEntries(props.value))
const kind = computed(() => {
  if (isSnapshotScalar(props.value)) return 'scalar'
  if (!hasSnapshotPayload(props.value)) return 'empty'
  if (fields.value.length && props.depth < 2) return 'fields'
  return 'json'
})
const text = computed(() => formatSnapshotScalar(props.value))
</script>

<style scoped>
.runtime-value {
  color: var(--sl-text-heading, #0f172a);
  font-family: var(--sl-font-family);
  font-size: 12.5px;
  line-height: 1.45;
  word-break: break-all;
}
.runtime-value.is-empty {
  color: var(--sl-text-disabled, #94a3b8);
  font-family: inherit;
}
.runtime-fields {
  margin: 0;
  display: grid;
  gap: 6px;
}
.runtime-fields > div {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  gap: 8px;
  align-items: start;
}
.runtime-fields dt {
  margin: 0;
  color: var(--sl-text-secondary, #64748b);
  font-size: 12px;
  line-height: 1.45;
  word-break: break-all;
}
.runtime-fields dt::after {
  content: ':';
  margin-left: 1px;
}
.runtime-fields dd {
  margin: 0;
  min-width: 0;
}
</style>
