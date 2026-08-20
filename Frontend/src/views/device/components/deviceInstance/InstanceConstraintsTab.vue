<template>
  <div class="tab-pane-content">
    <div class="sub-section-head">
      <div class="head-left">
        <span class="head-title">设备实例安全监控约束</span>
        <span class="head-desc">继承自模型并允许针对当前物理实例个性化定制的数值安全阈值规则</span>
      </div>
      <div class="head-actions" style="display: flex; align-items: center; gap: 8px;">
        <template v-if="!isEditingConstraints">
          <button v-if="canEdit" class="btn-aliyun" type="button" @click="startEditConstraints">
            <el-icon><Edit /></el-icon><span>编辑约束</span>
          </button>
        </template>
        <template v-else>
          <button class="btn-aliyun" type="button" @click="addConstraint">
            <el-icon><Plus /></el-icon><span>添加约束规则</span>
          </button>
          <button class="btn-aliyun" type="button" @click="cancelEditConstraints">
            <el-icon><Close /></el-icon><span>取消</span>
          </button>
        </template>
      </div>
    </div>

    <div v-if="!isEditingConstraints" class="constraint-view-container">
      <el-table :data="localConstraints" border stripe size="small" style="width: 100%" empty-text="暂未配置安全约束规则">
        <el-table-column label="监控物模型属性" min-width="160">
          <template #default="{ row }">
            <div class="constraint-prop-cell">
              <span class="prop-name">{{ getAttributeName(row.objectAttributeName) }}</span>
              <span v-if="row.objectAttributeName" class="prop-code">({{ row.objectAttributeName }})</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="条件算子" min-width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info" effect="plain">{{ operatorLabel(row.operator) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="判定阈值" min-width="110">
          <template #default="{ row }">
            <span class="threshold-value">{{ row.boundaryValue != null ? row.boundaryValue : '-' }}</span>
            <span v-if="row.unit" class="threshold-unit">{{ row.unit }}</span>
          </template>
        </el-table-column>
        <el-table-column label="违规转向状态" min-width="140">
          <template #default="{ row }">
            <el-tag v-if="row.violationStateName" size="small" type="danger" effect="light">{{ row.violationStateName }}</el-tag>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="约束描述" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
      </el-table>
    </div>

    <div v-else class="constraint-edit-container">
      <el-table :data="localConstraints" border size="small" style="width: 100%" empty-text="暂无约束规则，请点击右上角“添加约束规则”">
        <el-table-column label="监控物模型属性" min-width="160">
          <template #default="{ row }">
            <el-select v-model="row.objectAttributeName" size="small" style="width: 100%" placeholder="选择属性" @change="(val: string) => onConstraintAttrChange(row, val)">
              <el-option
                v-for="prop in getModelAttributes(instance.modelId, models, modelOptions)"
                :key="propKey(prop)"
                :label="propLabel(prop)"
                :value="propKey(prop)"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="条件算子" min-width="130">
          <template #default="{ row }">
            <el-select v-model="row.operator" size="small" style="width: 100%">
              <el-option v-for="op in validOperatorsList" :key="op" :label="operatorLabel(op)" :value="op" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="判定阈值" min-width="110">
          <template #default="{ row }">
            <el-input-number v-model="row.boundaryValue" size="small" :controls="false" placeholder="数值" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="物理单位" min-width="90">
          <template #default="{ row }">
            <el-input v-model="row.unit" size="small" placeholder="如: ℃" />
          </template>
        </el-table-column>
        <el-table-column label="违规转向状态" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.violationStateName" size="small" placeholder="如: 温度异常" />
          </template>
        </el-table-column>
        <el-table-column label="约束描述" min-width="180">
          <template #default="{ row }">
            <el-input v-model="row.description" size="small" placeholder="异常规则说明..." />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ $index }">
            <button class="btn-link danger" type="button" @click="removeConstraint($index)">删除</button>
          </template>
        </el-table-column>
      </el-table>
      <div class="bottom-action-bar">
        <button class="btn-aliyun" type="button" @click="cancelEditConstraints"><span>取消</span></button>
        <button class="btn-primary-blue" type="button" :disabled="saving" @click="saveConstraints">
          <span>{{ saving ? '保存中...' : '保存约束规则' }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Close, Edit, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { operators } from '../deviceModel/deviceModelConstants'
import * as api from './deviceInstanceApi'
import {
  asArray,
  buildInstanceSavePayload,
  constraintsFromSource,
  findModelById,
  getModelAttributes,
  operatorLabel,
  propKey,
  propLabel
} from './normalizers'
import type { DeviceInstance, DeviceModel, InstanceConstraint } from './types'

const props = defineProps<{
  instance: DeviceInstance
  models: DeviceModel[]
  modelOptions: DeviceModel[]
  constraints: InstanceConstraint[]
  canEdit: boolean
  retired: boolean
}>()

const emit = defineEmits<{
  'update:constraints': [value: InstanceConstraint[]]
  saved: []
}>()

const isEditingConstraints = ref(false)
const originalConstraints = ref<InstanceConstraint[]>([])
const saving = ref(false)
const localConstraints = computed({
  get: () => props.constraints,
  set: (val) => emit('update:constraints', val)
})

const validOperatorsList = computed(() => {
  const list = operators.length ? operators : ['>', '<', '>=', '<=', '=', '!=']
  return list.filter((op: string) => op !== 'BETWEEN' && op !== 'IN')
})

watch(() => props.instance?.instanceId, (instanceId) => {
  isEditingConstraints.value = false
  if (!instanceId) {
    emit('update:constraints', [])
    return
  }
  const model = findModelById(props.instance.modelId, props.models, props.modelOptions)
  const cfg = props.instance.instanceConfig || {}
  let list = cfg.intrinsicConstraints
  if (!list || list.length === 0) list = asArray(model?.intrinsicConstraints || model?.intrinsicConstraint)
  emit('update:constraints', constraintsFromSource(list))
}, { immediate: true })

const getAttributeName = (key: string) => {
  const attr = getModelAttributes(props.instance.modelId, props.models, props.modelOptions)
    .find((v: any) => String(v.attributeName) === String(key))
  return attr?.displayName || attr?.attributeName || key
}

const startEditConstraints = () => {
  originalConstraints.value = JSON.parse(JSON.stringify(localConstraints.value))
  isEditingConstraints.value = true
}

const cancelEditConstraints = () => {
  emit('update:constraints', JSON.parse(JSON.stringify(originalConstraints.value)))
  isEditingConstraints.value = false
}

const onConstraintAttrChange = (row: any, val: string) => {
  const attr = getModelAttributes(props.instance.modelId, props.models, props.modelOptions)
    .find((v: any) => String(v.attributeName) === String(val))
  if (attr?.unit && !row.unit) row.unit = attr.unit
}

const addConstraint = () => {
  emit('update:constraints', [
    ...localConstraints.value,
    { objectAttributeName: '', operator: '<=', boundaryValue: null, unit: '', violationStateName: '', description: '' }
  ])
}

const removeConstraint = (index: number) => {
  const next = localConstraints.value.slice()
  next.splice(index, 1)
  emit('update:constraints', next)
}

const saveConstraints = async () => {
  if (props.retired) return
  saving.value = true
  try {
    const payload = buildInstanceSavePayload(props.instance, localConstraints.value)
    const data = await api.saveInstance(payload)
    if (data?.success) {
      ElMessage.success('约束保存成功')
      isEditingConstraints.value = false
      emit('saved')
    } else {
      ElMessage.error(data?.message || '保存失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

</script>

<style scoped>
.tab-pane-content { display: flex; flex-direction: column; gap: 12px; }
.sub-section-head {
  padding: 8px 12px;
  background: #f8fafc;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.head-left { display: flex; flex-direction: column; gap: 2px; }
.sub-section-head .head-title { font-size: 12.5px; font-weight: 700; color: var(--sl-text-heading); }
.sub-section-head .head-desc { font-size: 11px; color: var(--sl-text-secondary); }
.constraint-prop-cell { display: flex; flex-direction: column; gap: 2px; }
.constraint-prop-cell .prop-name { font-weight: 500; color: var(--sl-text-primary, #1e293b); }
.constraint-prop-cell .prop-code { font-size: 11px; color: var(--sl-text-muted, #94a3b8); font-family: monospace; }
.threshold-value { font-family: var(--sl-font-mono, monospace); font-weight: 600; color: var(--sl-text-primary, #1e293b); }
.threshold-unit { font-size: 12px; color: var(--sl-text-muted, #64748b); margin-left: 4px; }
.bottom-action-bar { display: flex; justify-content: flex-end; gap: 10px; margin-top: 16px; }
.muted { color: var(--sl-text-disabled); font-size: 12px; }
</style>
