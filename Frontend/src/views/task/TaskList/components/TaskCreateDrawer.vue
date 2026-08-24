<template>
  <el-drawer
    :model-value="modelValue"
    title="新建任务"
    size="80%"
    class="model-drawer unified-workflow-drawer"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="drawer-body unified-drawer-scroll">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <section class="drawer-section">
          <div class="section-title">
            <strong>基础配置</strong>
            <span>仅可选择发布启用的可执行流程</span>
          </div>
          <el-form-item label="任务名称" prop="taskName">
            <el-input :model-value="form.taskName" placeholder="例如：批次 PCR 扩增" @update:model-value="emit('update:taskName', $event)" />
          </el-form-item>
          <el-form-item label="关联流程" prop="flowModelId">
            <el-select :model-value="form.flowModelId" placeholder="选择已发布、可执行流程" v-loading="loadingWorkflows" @update:model-value="emit('update:flowModelId', $event)">
              <el-option v-for="workflow in workflows" :key="workflow.id" :label="workflowModelName(workflow)" :value="workflow.id" />
            </el-select>
          </el-form-item>
        </section>

        <section v-if="requirements.length || errors.length" class="drawer-section">
          <div class="section-title">
            <strong>设备实例绑定</strong>
            <span>按流程中设备节点的出现路径绑定具体实例</span>
          </div>
          <TaskResourceBindingCanvas :requirements="requirements" :groups="groups" :errors="errors" :model-value="form.resourceBindings" :instances="instances" :models="models" @update:model-value="emit('update:resourceBindings', $event)" />
        </section>

        <section class="drawer-section">
          <TaskConstraintPanel :rules="form.taskConstraints || []" :reviews="constraintReviews" @edit="emit('edit-constraint', $event)" @remove="emit('remove-constraint', $event)" />
        </section>
        
        <section v-if="form.flowModelId" class="drawer-section">
          <div class="section-title">
            <strong>创建前检查</strong>
            <span>校验设备绑定、任务约束和流程执行条件</span>
          </div>
          <TaskPreflightPanel :result="preflightResult" :loading="preflighting" @retry="emit('preflight')" />
        </section>
      </el-form>
    </div>
    <template #footer>
      <div class="drawer-footer">
        <span v-if="missingBindingCount" class="footer-hint">还有 {{ missingBindingCount }} 个设备未绑定</span>
        <button class="btn-aliyun" type="button" @click="emit('update:modelValue', false)">取消</button>
        <button class="btn-aliyun" type="button" :disabled="preflighting" @click="emit('preflight')">
          {{ preflighting ? '检查中...' : '检查' }}
        </button>
        <button class="btn-primary-blue" type="button" :disabled="!canSubmit || creating" @click="emit('submit')">
          {{ creating ? '创建中...' : '创建任务' }}
        </button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { workflowModelName } from '../../../../utils/workflowAuthoring.js'
import TaskConstraintPanel from './TaskConstraintPanel.vue'
import TaskPreflightPanel from './TaskPreflightPanel.vue'
import TaskResourceBindingCanvas from './TaskResourceBindingCanvas.vue'

type Item = Record<string, any>
const props = withDefaults(defineProps<{
  modelValue: boolean
  form: Item
  workflows?: Item[]
  loadingWorkflows?: boolean
  requirements?: Item[]
  groups?: Item[]
  errors?: string[]
  instances?: Item[]
  models?: Item[]
  preflightResult?: Item | null
  preflighting?: boolean
  creating?: boolean
  constraintReviews?: Array<string | null>
}>(), {
  workflows: () => [],
  requirements: () => [],
  groups: () => [],
  errors: () => [],
  instances: () => [],
  models: () => [],
  preflightResult: null,
  constraintReviews: () => []
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'update:taskName': [value: string]
  'update:flowModelId': [value: number | null]
  'update:resourceBindings': [value: Record<string, number | null>]
  'edit-constraint': [index?: number]
  'remove-constraint': [index: number]
  preflight: []
  submit: []
}>()

const formRef = ref<FormInstance>()
const rules: FormRules = {
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  flowModelId: [{ required: true, message: '请选择流程', trigger: 'change' }]
}

const missingBindingCount = computed(() => props.requirements.filter(requirement => !props.form.resourceBindings?.[requirement.slotId]).length)
const canSubmit = computed(() => Boolean(props.form.taskName?.trim() && props.form.flowModelId && !missingBindingCount.value && props.preflightResult?.ready))

defineExpose({
  validate: () => formRef.value?.validate(),
  clearValidate: () => formRef.value?.clearValidate()
})
</script>

<style scoped>
.drawer-body {
  min-height: 0;
  padding: 0 20px;
  box-sizing: border-box;
}

.drawer-section {
  display: grid;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  border-left: 3px solid var(--sl-primary, #2563eb);
  padding-left: 8px;
  margin-bottom: 4px;
}

.section-title strong {
  font-size: 13.5px;
  font-weight: 700;
  color: var(--sl-text-heading, #0f172a);
}

.section-title span {
  color: var(--sl-text-secondary, #64748b);
  font-size: 11.5px;
}

.drawer-section :deep(.el-select) {
  width: 100%;
}

.drawer-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 20px;
}

.footer-hint {
  margin-right: auto;
  color: var(--sl-warning, #d97706);
  font-size: 12px;
  font-weight: 500;
}
</style>
