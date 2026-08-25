<template>
  <el-drawer
    :model-value="modelValue"
    title="新建任务"
    size="78%"
    append-to-body
    class="model-drawer unified-workflow-drawer"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @open="canvasReady = false"
    @opened="canvasReady = true"
    @closed="canvasReady = false"
  >
    <div class="drawer-body unified-drawer-scroll">
      <!-- 一体化全景大卡片 (Master Consolidated Card) -->
      <div class="master-drawer-card">
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <!-- 章节 01：基础配置 -->
          <section class="card-section-block">
            <div class="section-card-head">
              <div class="section-card-title">
                <span class="sec-idx-badge">01</span>
                <span class="sec-title-text">基础配置</span>
                <span class="sec-desc-text">定义任务名称并绑定已发布启用的可执行流程</span>
              </div>
            </div>
            <div class="section-card-body padded">
              <div class="field-grid-2col">
                <el-form-item label="任务名称" prop="taskName" class="form-item-compact">
                  <el-input
                    :model-value="form.taskName"
                    placeholder="例如：批次 PCR 扩增"
                    class="form-control-28"
                    @update:model-value="emit('update:taskName', $event)"
                  />
                </el-form-item>
                <el-form-item label="关联流程" prop="flowModelId" class="form-item-compact">
                  <el-select
                    :model-value="form.flowModelId"
                    placeholder="选择已发布、可执行流程"
                    v-loading="loadingWorkflows"
                    class="form-control-28"
                    @update:model-value="emit('update:flowModelId', $event)"
                  >
                    <el-option
                      v-for="workflow in workflows"
                      :key="workflow.id"
                      :label="workflowModelDisplayName(workflow)"
                      :value="workflow.id"
                    />
                  </el-select>
                </el-form-item>
              </div>
            </div>
          </section>

          <!-- 章节 02：流程与设备绑定 -->
          <section v-if="form.flowModelId" class="card-section-block">
            <div class="section-card-head">
              <div class="section-card-title">
                <span class="sec-idx-badge">02</span>
                <span class="sec-title-text">流程与设备绑定</span>
                <span class="sec-desc-text">流程图与设计器一致；点选设备节点后在右侧分配实例并填写参数</span>
              </div>
            </div>
            <div class="section-card-body">
              <TaskResourceBindingCanvas
                :canvas-ready="canvasReady"
                :requirements="requirements"
                :groups="groups"
                :errors="errors"
                :model-value="form.resourceBindings"
                :parameter-bindings="form.parameterBindings || {}"
                :instances="instances"
                :models="models"
                @update:model-value="emit('update:resourceBindings', $event)"
                @update:parameter-bindings="emit('update:parameterBindings', $event)"
              />
            </div>
          </section>

          <!-- 章节 03：任务级约束 -->
          <section class="card-section-block">
            <TaskConstraintPanel
              :rules="form.taskConstraints || []"
              :reviews="constraintReviews"
              @edit="emit('edit-constraint', $event)"
              @remove="emit('remove-constraint', $event)"
            />
          </section>
          
          <!-- 章节 04：创建前检查 -->
          <section v-if="form.flowModelId" class="card-section-block">
            <TaskPreflightPanel
              :result="preflightResult"
              :loading="preflighting"
              @retry="emit('preflight')"
            />
          </section>
        </el-form>
      </div>
    </div>

    <!-- 抽屉底部操作条 (与系统规范严格对齐) -->
    <template #footer>
      <div class="drawer-footer">
        <span v-if="missingBindingCount" class="footer-hint warning">
          ● 还有 {{ missingBindingCount }} 个设备未完成实例绑定
        </span>
        <span v-else-if="missingParameterCount" class="footer-hint warning">
          ● 还有 {{ missingParameterCount }} 个能力参数未填写
        </span>
        <span v-else-if="canSubmit" class="footer-hint ready">
          ● 所有前置条件已就绪
        </span>

        <button class="btn-aliyun" type="button" @click="emit('update:modelValue', false)">取消</button>
        <button class="btn-aliyun" type="button" :disabled="preflighting" @click="emit('preflight')">
          {{ preflighting ? '检查中...' : (preflightResult ? '重新检查' : '检查') }}
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
import { workflowModelDisplayName } from '../../../../utils/workflowAuthoring.js'
import { missingHoleCount } from '../../../../utils/taskResourceBindings.js'
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

const canvasReady = ref(false)
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'update:taskName': [value: string]
  'update:flowModelId': [value: number | null]
  'update:resourceBindings': [value: Record<string, number | null>]
  'update:parameterBindings': [value: Record<string, Record<string, unknown>>]
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
const missingParameterCount = computed(() => missingHoleCount(props.requirements, props.form.parameterBindings || {}))
const canSubmit = computed(() => Boolean(props.form.taskName?.trim() && props.form.flowModelId && !missingBindingCount.value && !missingParameterCount.value && props.preflightResult?.ready))

defineExpose({
  validate: () => formRef.value?.validate(),
  clearValidate: () => formRef.value?.clearValidate()
})
</script>

<style scoped>
.drawer-body {
  min-height: 0;
  padding: 12px 16px;
  background: var(--sl-bg-page, #f1f5f9);
  box-sizing: border-box;
}

/* 一体化大卡片容器 (Master Consolidated Card) */
.master-drawer-card {
  background: #ffffff;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 4px);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* 章节分段块 (大卡片内部连续平铺，以 1px 细线自然分区) */
.card-section-block {
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  display: flex;
  flex-direction: column;
  background: #ffffff;
}
.card-section-block:last-child {
  border-bottom: none;
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
.section-card-body.padded {
  padding: 10px 14px;
}

/* 28px 标准表单控件与紧凑间距 */
.field-grid-2col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.form-item-compact {
  margin-bottom: 0 !important;
}
.form-item-compact :deep(.el-form-item__label) {
  font-size: 11.5px;
  color: var(--sl-text-secondary, #64748b);
  padding-bottom: 4px;
  line-height: 1.2;
}

.form-control-28 {
  width: 100%;
}
.form-control-28 :deep(.el-input__wrapper),
.form-control-28 :deep(.el-select__wrapper) {
  height: 28px;
  font-size: 11.5px;
  box-shadow: 0 0 0 1px var(--sl-border-input, #cbd5e1) inset;
  border-radius: var(--sl-radius-sm, 4px);
}
.form-control-28 :deep(.el-input__wrapper.is-focus),
.form-control-28 :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px var(--sl-primary, #2563eb) inset;
}

/* 抽屉底部操作条 */
.drawer-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 8px 16px;
  background: #ffffff;
}

.footer-hint {
  margin-right: auto;
  font-size: 11.5px;
  font-weight: 500;
}
.footer-hint.warning {
  color: var(--sl-warning, #d97706);
}
.footer-hint.ready {
  color: var(--sl-success, #16a34a);
}

/* 按钮统一规范 */
.btn-aliyun {
  height: 26px;
  padding: 0 12px;
  font-size: 11.5px;
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

.btn-primary-blue {
  height: 26px;
  padding: 0 14px;
  font-size: 11.5px;
  font-weight: 600;
  color: #ffffff;
  background: var(--sl-primary, #2563eb);
  border: 1px solid var(--sl-primary, #2563eb);
  border-radius: var(--sl-radius-sm, 4px);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: var(--sl-ease-smooth, all 0.18s ease);
}
.btn-primary-blue:hover {
  background: var(--sl-primary-hover, #1d4ed8);
  border-color: var(--sl-primary-hover, #1d4ed8);
}
.btn-primary-blue:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
