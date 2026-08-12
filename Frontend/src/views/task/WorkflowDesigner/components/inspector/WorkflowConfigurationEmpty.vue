<template>
  <section class="configuration-empty" role="status">
    <strong>{{ title }}</strong>
    <p>{{ description }}</p>
    <el-button v-if="actionLabel" class="btn-aliyun" size="small" @click="emit('action')">
      {{ actionLabel }}
    </el-button>
  </section>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ title: string, description: string, actionLabel?: string }>(), { actionLabel: '' })
const emit = defineEmits<{ action: [] }>()
</script>

<style scoped>
.configuration-empty{display:flex;min-height:148px;flex-direction:column;align-items:center;justify-content:center;box-sizing:border-box;padding:28px 24px;border:1px dashed #d9dce3;background:#fafbfc;text-align:center}.configuration-empty strong{color:#1f2329;font-size:14px;font-weight:500}.configuration-empty p{max-width:520px;margin:6px 0 14px;color:#8f959e;font-size:12px;line-height:20px}.configuration-empty :deep(.el-button){min-width:88px}
</style>
