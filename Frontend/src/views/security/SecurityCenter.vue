<template>
  <div class="security-page">
    <div class="page-heading">
      <div>
        <h1>约束管理</h1>
        <p>维护规则约束集合，查看系统执行中的违规记录。</p>
      </div>
      <div class="heading-actions">
        <el-button v-if="activeTab === 'rules'" :icon="Refresh" @click="fetchRules">刷新</el-button>
        <el-button v-if="activeTab === 'violations'" :icon="Refresh" @click="fetchViolations">刷新</el-button>
        <el-button v-if="activeTab === 'rules' && canCreateRule" type="primary" :icon="Plus" @click="openRuleDialog()">新增规则</el-button>
      </div>
    </div>

    <el-card shadow="never" class="page-card">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="约束规则" name="rules">
          <div class="toolbar">
            <el-input v-model="ruleQuery.keyword" clearable placeholder="搜索规则名称、对象、阈值或说明" :prefix-icon="Search" @keyup.enter="fetchRules" />
            <el-select v-model="ruleQuery.sourceType" clearable placeholder="来源类型">
              <el-option v-for="item in sourceTypes" :key="item" :label="item" :value="item" />
            </el-select>
            <el-select v-model="ruleQuery.operator" clearable placeholder="比较符">
              <el-option v-for="item in operators" :key="item" :label="item" :value="item" />
            </el-select>
            <el-select v-model="ruleQuery.isEnabled" clearable placeholder="启用状态">
              <el-option label="启用" :value="true" />
              <el-option label="停用" :value="false" />
            </el-select>
            <el-button type="primary" plain :icon="Search" @click="fetchRules">查询</el-button>
          </div>

          <el-table :data="rules" border stripe size="small" v-loading="loadingRules" class="data-table">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="ruleName" label="约束名称" min-width="150" show-overflow-tooltip />
            <el-table-column prop="sourceType" label="来源类型" min-width="190" show-overflow-tooltip />
            <el-table-column prop="objectEndpoint" label="监控端点" min-width="150" show-overflow-tooltip />
            <el-table-column prop="objectName" label="约束对象" min-width="150" show-overflow-tooltip />
            <el-table-column label="条件" min-width="140">
              <template #default="{ row }">
                <span class="condition-text">{{ row.operator }} {{ row.threshold }}</span>
              </template>
            </el-table-column>
            <el-table-column label="违规动作" min-width="220">
              <template #default="{ row }">
                <div class="action-tags">
                  <el-tag v-for="(action, index) in normalizeActions(row.violationActions)" :key="index" size="small" :type="action.actionType === 'SYSTEM' ? 'warning' : 'info'">
                    {{ summarizeAction(action) }}
                  </el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="92">
              <template #default="{ row }">
                <el-switch
                  v-model="row.isEnabled"
                  :disabled="!canEditRule"
                  size="small"
                  @change="(value) => toggleRule(row, value)"
                />
              </template>
            </el-table-column>
            <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
            <el-table-column label="创建时间" min-width="155">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button v-if="canEditRule" link type="primary" @click="openRuleDialog(row)">编辑</el-button>
                <el-popconfirm v-if="canDeleteRule" title="确认删除该约束规则？" @confirm="deleteRule(row)">
                  <template #reference>
                    <el-button link type="danger">删除</el-button>
                  </template>
                </el-popconfirm>
                <span v-if="!canEditRule && !canDeleteRule" class="muted">无操作权限</span>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-row">
            <el-pagination
              v-model:current-page="ruleQuery.pageNo"
              v-model:page-size="ruleQuery.pageSize"
              :total="ruleTotal"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="fetchRules"
              @size-change="fetchRules"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="违规日志" name="violations">
          <div class="toolbar">
            <el-input v-model="violationQuery.keyword" clearable placeholder="搜索观测变量、动作或约束类型" :prefix-icon="Search" @keyup.enter="fetchViolations" />
            <el-input v-model="violationQuery.constraintRuleId" clearable placeholder="规则 ID" />
            <el-input v-model="violationQuery.taskId" clearable placeholder="任务 ID" />
            <el-input v-model="violationQuery.deviceInstanceId" clearable placeholder="设备实例 ID" />
            <el-button type="primary" plain :icon="Search" @click="fetchViolations">查询</el-button>
            <el-button v-if="canExportViolation" :icon="Download" @click="exportViolations">导出当前页</el-button>
          </div>

          <el-table :data="violations" border stripe size="small" v-loading="loadingViolations" class="data-table">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="constraintRuleId" label="规则 ID" width="90" />
            <el-table-column prop="constraintType" label="约束类型" min-width="130" show-overflow-tooltip />
            <el-table-column prop="taskId" label="任务 ID" width="90" />
            <el-table-column prop="taskStepId" label="步骤 ID" width="90" />
            <el-table-column prop="deviceInstanceId" label="设备实例" width="105" />
            <el-table-column prop="observedVariable" label="观测变量" min-width="150" show-overflow-tooltip />
            <el-table-column label="期望条件" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ compactJson(row.expectedCondition) }}</template>
            </el-table-column>
            <el-table-column label="实际值" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ compactJson(row.actualValue) }}</template>
            </el-table-column>
            <el-table-column prop="actionTaken" label="执行动作" min-width="160" show-overflow-tooltip />
            <el-table-column label="发生时间" min-width="155">
              <template #default="{ row }">{{ formatTime(row.violationTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openViolationDrawer(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-row">
            <el-pagination
              v-model:current-page="violationQuery.pageNo"
              v-model:page-size="violationQuery.pageSize"
              :total="violationTotal"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="fetchViolations"
              @size-change="fetchViolations"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="ruleDialogVisible" :title="ruleDialogMode === 'create' ? '新增约束规则' : '编辑约束规则'" width="820px" destroy-on-close>
      <el-form :model="ruleForm" :rules="ruleFormRules" ref="ruleFormRef" label-width="108px" size="small">
        <div class="form-grid">
          <el-form-item label="约束名称" prop="ruleName">
            <el-input v-model="ruleForm.ruleName" placeholder="例如：反应釜温度上限" />
          </el-form-item>
          <el-form-item label="来源类型" prop="sourceType">
            <el-select v-model="ruleForm.sourceType" style="width: 100%" placeholder="请选择来源类型" @change="onSourceTypeChange">
              <el-option v-for="item in sourceTypes" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="监控端点" prop="objectEndpoint">
            <el-input v-model="ruleForm.objectEndpoint" placeholder="设备实例 ID / 节点定位" />
          </el-form-item>
          <el-form-item label="约束对象" prop="objectName">
            <el-input v-model="ruleForm.objectName" placeholder="用户定义的 observable object name" />
          </el-form-item>
          <el-form-item label="比较符" prop="operator">
            <el-select v-model="ruleForm.operator" style="width: 100%">
              <el-option v-for="item in operators" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="阈值" prop="threshold">
            <el-input v-model="ruleForm.threshold" placeholder="如 80 或 [60,90]" />
          </el-form-item>
        </div>
        <el-form-item label="规则说明">
          <el-input v-model="ruleForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="启用规则">
          <el-switch v-model="ruleForm.isEnabled" />
        </el-form-item>

        <div class="subsection">
          <div class="subsection-header">
            <span>违规触发动作集</span>
            <div>
              <el-button size="small" :icon="Plus" @click="addSystemAction">系统动作</el-button>
              <el-button size="small" :icon="Plus" @click="addDeviceAction">设备能力</el-button>
            </div>
          </div>
          <el-table :data="ruleForm.violationActions" border size="small" empty-text="请至少添加一个违规动作">
            <el-table-column label="类型" width="150">
              <template #default="{ row }">
                <el-select v-model="row.actionType" @change="onActionTypeChange(row)">
                  <el-option label="SYSTEM" value="SYSTEM" />
                  <el-option label="DEVICE_CAPABILITY" value="DEVICE_CAPABILITY" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="动作内容" min-width="420">
              <template #default="{ row }">
                <div v-if="row.actionType === 'SYSTEM'" class="inline-controls">
                  <el-select v-model="row.action" placeholder="系统动作" style="width: 180px">
                    <el-option v-for="item in systemActions" :key="item" :label="item" :value="item" />
                  </el-select>
                </div>
                <div v-else class="device-action-editor">
                  <div class="inline-controls">
                    <el-select v-model="row.deviceInstanceId" filterable placeholder="设备实例" style="width: 210px" @change="() => onDeviceActionInstanceChange(row)">
                      <el-option v-for="item in deviceInstances" :key="item.id" :label="deviceInstanceLabel(item)" :value="Number(item.id || item.instanceId)" />
                    </el-select>
                    <el-select v-model="row.capabilityName" filterable placeholder="设备能力" style="width: 190px">
                      <el-option v-for="cap in capabilityOptions(row)" :key="cap.name" :label="cap.displayName || cap.name" :value="cap.name" />
                    </el-select>
                  </div>
                  <el-input v-model="row.parametersText" type="textarea" :rows="2" class="json-input" placeholder='参数 JSON，例如 {"targetTemperature":80}' />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ $index }">
                <el-button link type="danger" @click="removeAction($index)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRule" @click="submitRule">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="violationDrawerVisible" title="违规日志详情" size="560px">
      <el-descriptions v-if="currentViolation" :column="1" border size="small">
        <el-descriptions-item label="日志 ID">{{ currentViolation.id }}</el-descriptions-item>
        <el-descriptions-item label="规则 ID">{{ currentViolation.constraintRuleId }}</el-descriptions-item>
        <el-descriptions-item label="约束类型">{{ currentViolation.constraintType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="任务/步骤">{{ currentViolation.taskId || '-' }} / {{ currentViolation.taskStepId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="设备实例">{{ currentViolation.deviceInstanceId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="观测变量">{{ currentViolation.observedVariable || '-' }}</el-descriptions-item>
        <el-descriptions-item label="执行动作">{{ currentViolation.actionTaken || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发生时间">{{ formatTime(currentViolation.violationTime) }}</el-descriptions-item>
      </el-descriptions>
      <div class="json-detail">
        <h3>期望条件</h3>
        <pre>{{ prettyJson(currentViolation?.expectedCondition) }}</pre>
        <h3>实际值</h3>
        <pre>{{ prettyJson(currentViolation?.actualValue) }}</pre>
        <h3>变量快照</h3>
        <pre>{{ prettyJson(currentViolation?.variableSnapshot) }}</pre>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Download, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { useAuthStore } from '../../stores/authStore'

const authStore = useAuthStore()
const activeTab = ref('rules')

const canCreateRule = computed(() => authStore.hasPermission('constraint_rule:create'))
const canEditRule = computed(() => authStore.hasPermission('constraint_rule:edit'))
const canDeleteRule = computed(() => authStore.hasPermission('constraint_rule:delete'))
const canViewViolation = computed(() => authStore.hasPermission('violation_log:view'))
const canExportViolation = computed(() => authStore.hasPermission('violation_log:export'))

const sourceTypes = ref<string[]>([])
const operators = ref<string[]>([])
const systemActions = ref<string[]>([])
const deviceModels = ref<any[]>([])
const deviceInstances = ref<any[]>([])

const loadingRules = ref(false)
const rules = ref<any[]>([])
const ruleTotal = ref(0)
const ruleQuery = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  sourceType: '',
  objectEndpoint: '',
  objectName: '',
  operator: '',
  isEnabled: undefined as boolean | undefined
})

const loadingViolations = ref(false)
const violations = ref<any[]>([])
const violationTotal = ref(0)
const violationQuery = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  constraintRuleId: '',
  taskId: '',
  deviceInstanceId: ''
})

const ruleDialogVisible = ref(false)
const ruleDialogMode = ref<'create' | 'edit'>('create')
const ruleFormRef = ref<FormInstance>()
const savingRule = ref(false)
const ruleForm = ref<any>(emptyRuleForm())

const violationDrawerVisible = ref(false)
const currentViolation = ref<any>(null)

const ruleFormRules: FormRules = {
  ruleName: [{ required: true, message: '请输入约束名称', trigger: 'blur' }],
  sourceType: [{ required: true, message: '请选择来源类型', trigger: 'change' }],
  objectEndpoint: [{ required: true, message: '请输入监控端点', trigger: 'blur' }],
  objectName: [{ required: true, message: '请输入约束对象名称', trigger: 'blur' }],
  operator: [{ required: true, message: '请选择比较符', trigger: 'change' }],
  threshold: [{ required: true, message: '请输入阈值界限', trigger: 'blur' }]
}

function emptyRuleForm() {
  return {
    id: null,
    ruleName: '',
    sourceType: '',
    objectEndpoint: '',
    objectName: '',
    operator: 'GT',
    threshold: '',
    violationActions: [] as any[],
    description: '',
    isEnabled: true
  }
}

async function fetchOptions() {
  try {
    const res = await axios.get('/api/constraint/rule/options')
    if (res.data?.success) {
      sourceTypes.value = res.data.data?.sourceTypes || []
      operators.value = res.data.data?.operators || []
      systemActions.value = res.data.data?.systemViolationActions || []
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '加载约束选项失败')
  }
}

async function fetchDeviceRefs() {
  try {
    const [modelRes, instanceRes] = await Promise.all([
      axios.get('/api/device/model/list'),
      axios.get('/api/device/instance/list')
    ])
    if (modelRes.data?.success) deviceModels.value = modelRes.data.data || []
    if (instanceRes.data?.success) deviceInstances.value = instanceRes.data.data || []
  } catch {
    // Device refs are optional for rule table rendering.
  }
}

async function fetchRules() {
  loadingRules.value = true
  try {
    const res = await axios.get('/api/constraint/rule/page', { params: cleanParams(ruleQuery) })
    if (res.data?.success) {
      const data = res.data.data || {}
      rules.value = data.records || []
      ruleTotal.value = Number(data.total || 0)
    } else {
      ElMessage.error(res.data?.message || '加载约束规则失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '加载约束规则失败')
  } finally {
    loadingRules.value = false
  }
}

async function fetchViolations() {
  if (!canViewViolation.value) return
  loadingViolations.value = true
  try {
    const res = await axios.get('/api/constraint/violation/page', { params: cleanParams(violationQuery) })
    if (res.data?.success) {
      const data = res.data.data || {}
      violations.value = data.records || []
      violationTotal.value = Number(data.total || 0)
    } else {
      ElMessage.error(res.data?.message || '加载违规日志失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '加载违规日志失败')
  } finally {
    loadingViolations.value = false
  }
}

function openRuleDialog(row?: any) {
  ruleDialogMode.value = row?.id ? 'edit' : 'create'
  ruleForm.value = row ? toEditableRule(row) : emptyRuleForm()
  ruleDialogVisible.value = true
}

function toEditableRule(row: any) {
  return {
    id: row.id,
    ruleName: row.ruleName || '',
    sourceType: row.sourceType || '',
    objectEndpoint: row.objectEndpoint || '',
    objectName: row.objectName || '',
    operator: row.operator || 'GT',
    threshold: row.threshold || '',
    description: row.description || '',
    isEnabled: row.isEnabled !== false,
    violationActions: normalizeActions(row.violationActions).map((action: any) => ({
      ...action,
      parametersText: action.parameters ? JSON.stringify(action.parameters) : '{}'
    }))
  }
}

async function submitRule() {
  if (!ruleFormRef.value) return
  await ruleFormRef.value.validate(async (valid) => {
    if (!valid) return
    const payload = buildRulePayload()
    if (!payload) return
    savingRule.value = true
    try {
      const request = payload.id
        ? axios.put(`/api/constraint/rule/${payload.id}`, payload)
        : axios.post('/api/constraint/rule', payload)
      const res = await request
      if (res.data?.success) {
        ElMessage.success('保存成功')
        ruleDialogVisible.value = false
        await fetchRules()
      } else {
        ElMessage.error(res.data?.message || '保存失败')
      }
    } catch (err: any) {
      ElMessage.error(err?.response?.data?.message || '保存失败')
    } finally {
      savingRule.value = false
    }
  })
}

function buildRulePayload() {
  if (!ruleForm.value.violationActions.length) {
    ElMessage.warning('请至少添加一个违规触发动作')
    return null
  }
  const actions: any[] = []
  for (const [index, action] of ruleForm.value.violationActions.entries()) {
    if (action.actionType === 'SYSTEM') {
      if (!action.action) {
        ElMessage.warning(`第 ${index + 1} 个系统动作未选择 action`)
        return null
      }
      actions.push({ actionType: 'SYSTEM', action: action.action })
    } else if (action.actionType === 'DEVICE_CAPABILITY') {
      if (!action.deviceInstanceId || !action.capabilityName) {
        ElMessage.warning(`第 ${index + 1} 个设备能力动作缺少设备实例或能力名`)
        return null
      }
      const parsed = parseJsonObject(action.parametersText || '{}', `第 ${index + 1} 个设备能力动作参数`)
      if (parsed === null) return null
      actions.push({
        actionType: 'DEVICE_CAPABILITY',
        deviceInstanceId: Number(action.deviceInstanceId),
        capabilityName: action.capabilityName,
        parameters: parsed
      })
    }
  }
  return {
    id: ruleForm.value.id,
    ruleName: ruleForm.value.ruleName,
    sourceType: ruleForm.value.sourceType,
    objectEndpoint: ruleForm.value.objectEndpoint,
    objectName: ruleForm.value.objectName,
    operator: ruleForm.value.operator,
    threshold: ruleForm.value.threshold,
    violationActions: actions,
    description: ruleForm.value.description,
    isEnabled: ruleForm.value.isEnabled
  }
}

async function deleteRule(row: any) {
  try {
    const res = await axios.delete(`/api/constraint/rule/${row.id}`)
    if (res.data?.success) {
      ElMessage.success('删除成功')
      await fetchRules()
    } else {
      ElMessage.error(res.data?.message || '删除失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '删除失败')
  }
}

async function toggleRule(row: any, enabled: boolean) {
  try {
    const res = await axios.patch(`/api/constraint/rule/${row.id}/enabled`, null, { params: { enabled } })
    if (!res.data?.success) throw new Error(res.data?.message || '更新状态失败')
    ElMessage.success(enabled ? '规则已启用' : '规则已停用')
  } catch (err: any) {
    row.isEnabled = !enabled
    ElMessage.error(err?.response?.data?.message || err?.message || '更新状态失败')
  }
}

function addSystemAction() {
  ruleForm.value.violationActions.push({ actionType: 'SYSTEM', action: systemActions.value[0] || 'ALERT' })
}

function addDeviceAction() {
  ruleForm.value.violationActions.push({ actionType: 'DEVICE_CAPABILITY', deviceInstanceId: undefined, capabilityName: '', parametersText: '{}' })
}

function removeAction(index: number) {
  ruleForm.value.violationActions.splice(index, 1)
}

function onActionTypeChange(row: any) {
  if (row.actionType === 'SYSTEM') {
    row.action = row.action || systemActions.value[0] || 'ALERT'
    delete row.deviceInstanceId
    delete row.capabilityName
    delete row.parametersText
  } else {
    row.deviceInstanceId = undefined
    row.capabilityName = ''
    row.parametersText = '{}'
    delete row.action
  }
}

function onDeviceActionInstanceChange(row: any) {
  row.capabilityName = ''
}

function onSourceTypeChange() {
  if (!ruleForm.value.objectName && ruleForm.value.sourceType) {
    ruleForm.value.objectName = ruleForm.value.sourceType
  }
}

function capabilityOptions(action: any) {
  const instance = deviceInstances.value.find((item: any) => Number(item.id || item.instanceId) === Number(action.deviceInstanceId))
  const modelId = instance?.deviceModelId || instance?.modelId
  const model = deviceModels.value.find((item: any) => Number(item.id || item.modelId) === Number(modelId))
  return Array.isArray(model?.capabilities) ? model.capabilities : []
}

function deviceInstanceLabel(item: any) {
  return `${item.instanceName || item.name || '设备实例'} #${item.id || item.instanceId}`
}

function normalizeActions(value: any) {
  if (Array.isArray(value)) return value
  if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value)
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return []
    }
  }
  return []
}

function summarizeAction(action: any) {
  if (action.actionType === 'SYSTEM') return `SYSTEM:${action.action || '-'}`
  if (action.actionType === 'DEVICE_CAPABILITY') return `DEVICE:${action.capabilityName || '-'}@${action.deviceInstanceId || '-'}`
  return action.actionType || '-'
}

function parseJsonObject(text: string, label: string) {
  try {
    const parsed = text?.trim() ? JSON.parse(text) : {}
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      ElMessage.warning(`${label}必须是 JSON 对象`)
      return null
    }
    return parsed
  } catch {
    ElMessage.warning(`${label}不是合法 JSON`)
    return null
  }
}

function openViolationDrawer(row: any) {
  currentViolation.value = row
  violationDrawerVisible.value = true
}

function exportViolations() {
  const rows = violations.value.map(row => JSON.stringify(row)).join('\n')
  const blob = new Blob([rows], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `violation-log-page-${violationQuery.pageNo}.jsonl`
  link.click()
  URL.revokeObjectURL(url)
}

function onTabChange(name: string | number) {
  if (name === 'rules') fetchRules()
  if (name === 'violations') fetchViolations()
}

function cleanParams(source: Record<string, any>) {
  const params: Record<string, any> = {}
  Object.entries(source).forEach(([key, value]) => {
    if (value !== '' && value !== undefined && value !== null) params[key] = value
  })
  return params
}

function compactJson(value: any) {
  if (value === null || value === undefined) return '-'
  if (typeof value === 'string') return value
  return JSON.stringify(value)
}

function prettyJson(value: any) {
  if (value === null || value === undefined) return '-'
  if (typeof value === 'string') {
    try { return JSON.stringify(JSON.parse(value), null, 2) } catch { return value }
  }
  return JSON.stringify(value, null, 2)
}

function formatTime(time?: string) {
  return time ? new Date(time).toLocaleString('zh-CN', { hour12: false }) : '-'
}

onMounted(async () => {
  await Promise.all([fetchOptions(), fetchDeviceRefs()])
  await fetchRules()
})
</script>

<style scoped>
.security-page {
  min-height: calc(100vh - 52px);
  background: #f3f5f8;
  padding: 16px;
  overflow: auto;
}

.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.page-heading h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 650;
  color: #1f2937;
}

.page-heading p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.heading-actions,
.toolbar,
.inline-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-card {
  border: 1px solid #d9dee7;
  border-radius: 6px;
}

.toolbar {
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.toolbar .el-input {
  width: 260px;
}

.toolbar .el-select {
  width: 180px;
}

.data-table {
  width: 100%;
}

.condition-text {
  font-family: Consolas, Menlo, monospace;
  color: #334155;
}

.action-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 12px;
}

.subsection {
  border: 1px solid #dfe4ec;
  border-radius: 6px;
  padding: 12px;
  background: #fbfcfe;
}

.subsection-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-weight: 600;
  color: #334155;
}

.device-action-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.json-input :deep(textarea),
.json-detail pre {
  font-family: Consolas, Menlo, monospace;
  font-size: 12px;
  line-height: 1.45;
}

.json-detail {
  margin-top: 14px;
}

.json-detail h3 {
  margin: 14px 0 6px;
  font-size: 13px;
  color: #334155;
}

.json-detail pre {
  margin: 0;
  padding: 10px;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-word;
}

.muted {
  color: #94a3b8;
  font-size: 12px;
}

@media (max-width: 900px) {
  .page-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .toolbar .el-input,
  .toolbar .el-select {
    width: 100%;
  }
}
</style>