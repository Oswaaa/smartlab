<template>
  <el-drawer :model-value="modelValue" title="新建任务" size="78%" class="model-drawer unified-workflow-drawer" destroy-on-close @update:model-value="emit('update:modelValue', $event)">
    <div class="drawer-body unified-drawer-scroll">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <section class="drawer-section">
          <div class="section-title"><strong>基础配置</strong><span>仅可选择发布启用的可执行流程</span></div>
          <el-form-item label="任务名称" prop="taskName"><el-input :model-value="form.taskName" placeholder="例如：批次 PCR 扩增" @update:model-value="emit('update:taskName', $event)" /></el-form-item>
          <el-form-item label="关联流程" prop="flowModelId">
            <el-select :model-value="form.flowModelId" placeholder="选择已发布、可执行流程" v-loading="loadingWorkflows" @update:model-value="emit('update:flowModelId', $event)">
              <el-option v-for="workflow in workflows" :key="workflow.id" :label="workflow.flowName" :value="workflow.id" />
            </el-select>
          </el-form-item>
        </section>

        <section v-if="routes.length" class="drawer-section">
          <div class="section-title"><strong>设备实例绑定</strong><span>按流程中设备节点的出现路径绑定具体实例</span></div>
          <el-alert type="info" :closable="false" title="同一设备实例可以被多个节点复用，执行时仍由设备状态机统一仲裁。" />
          <TaskResourceBindingCanvas :groups="groups" :routes="routes" :errors="errors" :model-value="form.resourceBindings" :instances="instances" :models="models" @update:model-value="emit('update:resourceBindings', $event)" />
        </section>
        <section v-else-if="form.flowModelId && hasDeviceNodes" class="drawer-section"><el-alert type="warning" :closable="false" title="该流程包含设备能力节点，但当前无法生成设备绑定路径，请先修复流程模型。" /></section>

        <section class="drawer-section"><TaskConstraintPanel :rules="form.taskConstraints || []" :reviews="constraintReviews" @edit="emit('edit-constraint', $event)" @remove="emit('remove-constraint', $event)" /></section>
        <section v-if="form.flowModelId" class="drawer-section">
          <div class="section-title"><strong>创建前检查</strong><span>校验设备绑定、任务约束和流程执行条件</span></div>
          <TaskPreflightPanel :result="preflightResult" :loading="preflighting" @retry="emit('preflight')" />
        </section>
      </el-form>
    </div>
    <template #footer><div class="drawer-footer"><el-button class="btn-aliyun" @click="emit('update:modelValue', false)">取消</el-button><el-button class="btn-aliyun" :loading="preflighting" @click="emit('preflight')">检查</el-button><el-button class="btn-aliyun-cta" :loading="creating" @click="emit('submit')">创建任务</el-button></div></template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import TaskConstraintPanel from './TaskConstraintPanel.vue'
import TaskPreflightPanel from './TaskPreflightPanel.vue'
import TaskResourceBindingCanvas from './TaskResourceBindingCanvas.vue'

type Item = Record<string, any>
const props = withDefaults(defineProps<{
  modelValue: boolean,
  form: Item,
  workflows?: Item[],
  loadingWorkflows?: boolean,
  routes?: Item[],
  groups?: Item[],
  errors?: string[],
  instances?: Item[],
  models?: Item[],
  hasDeviceNodes?: boolean,
  preflightResult?: Item | null,
  preflighting?: boolean,
  creating?: boolean,
  constraintReviews?: Array<string | null>,
}>(), { workflows:()=>[], routes:()=>[], groups:()=>[], errors:()=>[], instances:()=>[], models:()=>[], preflightResult:null, constraintReviews:()=>[] })
const emit = defineEmits<{
  'update:modelValue':[value:boolean], 'update:taskName':[value:string], 'update:flowModelId':[value:number|null],
  'update:resourceBindings':[value:Record<string,number|null>], 'edit-constraint':[index?:number], 'remove-constraint':[index:number], preflight:[], submit:[]
}>()
const formRef = ref<FormInstance>()
const rules: FormRules = { taskName:[{ required:true, message:'请输入任务名称', trigger:'blur' }], flowModelId:[{ required:true, message:'请选择流程', trigger:'change' }] }
defineExpose({ validate: () => formRef.value?.validate(), clearValidate: () => formRef.value?.clearValidate() })
</script>

<style scoped>
.drawer-body{min-height:0;padding:0 16px;box-sizing:border-box}.drawer-section{display:grid;gap:12px;padding:18px 4px;border-bottom:1px solid #edf0f3}.section-title{display:flex;align-items:baseline;gap:10px;border-left:4px solid #1677ff;padding-left:10px}.section-title strong{font-size:14px}.section-title span{color:#8490a0;font-size:10px}.drawer-section :deep(.el-select){width:100%}.drawer-footer{display:flex;justify-content:flex-end;gap:8px}
</style>
