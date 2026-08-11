<template><pre class="json-value">{{ formatted }}</pre></template>
<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ value: unknown }>()
const formatted = computed(() => { try { return JSON.stringify(props.value, null, 2) } catch { return String(props.value) } })
</script>
<style scoped>.json-value{max-height:180px;margin:0;overflow:auto;padding:8px 10px;border:1px solid #e5e9ef;border-radius:4px;background:#f7f9fb;color:#314158;font:11px/1.55 Consolas,Monaco,monospace;white-space:pre-wrap;word-break:break-all}</style>
