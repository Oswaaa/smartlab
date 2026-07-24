<template>
  <el-drawer v-model="visible" :title="editing ? '编辑设备模型' : '新建设备模型'" size="min(980px,96vw)" destroy-on-close>
    <el-form :model="form" label-width="100px">
      <el-row :gutter="16"><el-col :span="12"><el-form-item label="模型名称" required><el-input v-model="form.modelName" placeholder="设备模型名称" /></el-form-item></el-col><el-col :span="12"><el-form-item label="设备类别" required><el-select v-model="form.categoryId" filterable style="width:100%" placeholder="选择叶子类别"><el-option v-for="item in categories" :key="item.id" :label="item.categoryName" :value="item.id" /></el-select></el-form-item></el-col></el-row>
      <el-alert type="info" :closable="false" title="这里保存最终设备能力模型和设备状态机模型。属性使用attributeName，遥测使用telemetryName；CMD主链为IDLE→SENT→RUNNING→COMPLETED，终止命令由系统状态机处理，Adapter只回传实际事件" />
      <el-form-item label="模型配置JSON"><div class="editor-block"><p>包含attributes、capabilities、adapterContract、ports、intrinsicConstraints、stateMachineInterfaces、cmdState、opState、stateTransitions、componentsBom和defaultDataTemplate</p><el-input v-model="definitionText" type="textarea" :rows="31" class="json-editor" /></div></el-form-item>
    </el-form>
    <template #footer><el-button @click="visible=false">取消</el-button><el-button @click="formatDefinition">格式化JSON</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
  </el-drawer>
</template>

<script setup>
import { ref } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const props = defineProps({ categories: { type: Array, default: () => [] } })
const emit = defineEmits(['saved'])
const visible = ref(false), saving = ref(false), editing = ref(false)
const form = ref({ modelId: null, modelName: '', categoryId: null })
const definitionText = ref('')
const clone = value => JSON.parse(JSON.stringify(value))
const standardInterfaces = () => [
  { name: 'Interface_workflow_in', direction: 'IN', interfaceType: 'WORKFLOW', allowedSignals: ['WF_EXECUTE_START', 'WF_EXECUTE_ABORT'] },
  { name: 'Interface_control_in', direction: 'IN', interfaceType: 'CONTROL', allowedSignals: ['MANUAL_EXECUTE_START', 'MANUAL_EXECUTE_ABORT'] },
  { name: 'Interface_constraint_in', direction: 'IN', interfaceType: 'CONSTRAINT', allowedSignals: ['CONSTRAINT_EXECUTE', 'CONSTRAINT_ABORT'] },
  { name: 'Interface_adapter_in', direction: 'IN', interfaceType: 'ADAPTER', allowedSignals: [] },
  { name: 'Interface_adapter_out', direction: 'OUT', interfaceType: 'ADAPTER', allowedSignals: ['CMD_START', 'CMD_ABORT'] },
  { name: 'Interface_state_out', direction: 'OUT', interfaceType: 'STATE', allowedSignals: ['CMD_STATE', 'OP_STATE'] }
]
const emptyDefinition = () => ({
  attributes: [], capabilities: [],
  adapterContract: { config: { protocol: 'MQTT', adapterName: '', categoryName: '' }, commands: [], telemetry: { adapterAttributes: [], attributesMapping: [] }, events: { cmdEvents: [], opEvents: [] } },
  ports: [], intrinsicConstraints: [], stateMachineInterfaces: standardInterfaces(),
  cmdState: { initialStateName: 'IDLE', states: ['IDLE', 'SENT', 'RUNNING', 'COMPLETED', 'FAILED', 'ABORTING', 'ABORTED'].map(stateName => ({ stateName, onEntry: [] })) },
  opState: { regions: [] }, stateTransitions: [], componentsBom: [], defaultDataTemplate: { enabled: false }
})
function reset(model = null, categoryId = null) {
  editing.value = !!model
  form.value = { modelId: model?.modelId ?? model?.id ?? null, modelName: model?.modelName || '', categoryId: model?.categoryId ?? categoryId ?? null }
  const definition = model ? {
    attributes: clone(model.attributes || []), capabilities: clone(model.capabilities || []), adapterContract: clone(model.adapterContract || emptyDefinition().adapterContract), ports: clone(model.ports || []), intrinsicConstraints: clone(model.intrinsicConstraint || model.intrinsicConstraints || []), stateMachineInterfaces: clone(model.stateMachineInterfaces || standardInterfaces()), cmdState: clone(model.cmdState || emptyDefinition().cmdState), opState: clone(model.opState || { regions: [] }), stateTransitions: clone(model.stateTransitions || []), componentsBom: clone(model.componentsBom || []), defaultDataTemplate: clone(model.defaultDataTemplate || { enabled: false })
  } : emptyDefinition()
  definitionText.value = JSON.stringify(definition, null, 2)
}
function openForCreate(categoryId = null) { reset(null, categoryId); visible.value = true }
function openForEdit(model) { reset(model); visible.value = true }
function parseDefinition() { try { const parsed = JSON.parse(definitionText.value); if (!parsed || Array.isArray(parsed)) throw new Error(); return parsed } catch { throw new Error('模型配置必须是合法JSON对象') } }
function formatDefinition() { try { definitionText.value = JSON.stringify(parseDefinition(), null, 2) } catch (error) { ElMessage.error(error.message) } }
async function save() {
  if (!form.value.modelName.trim()) return ElMessage.error('请输入模型名称')
  if (!form.value.categoryId) return ElMessage.error('请选择设备类别')
  let definition
  try { definition = parseDefinition() } catch (error) { ElMessage.error(error.message); return }
  const category = props.categories.find(item => String(item.id) === String(form.value.categoryId))
  const payload = { modelId: form.value.modelId, modelName: form.value.modelName.trim(), categoryId: Number(form.value.categoryId), categoryName: category?.categoryName || '', ...definition }
  saving.value = true
  try { const res = await axios.post('/api/device/model/save', payload); if (!res.data?.success) throw new Error(res.data?.message || '保存失败'); const modelId = res.data.data?.modelId; ElMessage.success('设备模型已保存'); visible.value = false; emit('saved', modelId) } catch (error) { ElMessage.error(error?.message || '保存设备模型失败') } finally { saving.value = false }
}
async function loadPropertyTypesForTemplate() { return [] }
defineExpose({ openForCreate, openForEdit, loadPropertyTypesForTemplate })
</script>

<style scoped>
.editor-block { width:100%; }.editor-block p { margin:0 0 8px; color:#64748b; font-size:12px; }.json-editor :deep(.el-textarea__inner) { font-family:Consolas,Monaco,monospace; line-height:1.45; }
</style>
