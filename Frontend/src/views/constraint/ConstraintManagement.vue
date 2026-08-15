<template>
  <div class="security-page">
    <!-- 顶部标题栏 -->
    <header class="page-heading">
      <div>
        <h1>全局约束规则管理</h1>
        <p>管理实验过程中长期生效的安全联锁与设备工艺边界规则；违规时自动执行硬件安全能力与任务级熔断调度</p>
      </div>
      <div class="header-actions">
        <el-button class="btn-aliyun" :icon="Download" :loading="exporting" @click="exportGlobalModel">导出约束模型</el-button>
        <el-button class="btn-aliyun" :icon="Refresh" @click="load">刷新</el-button>
        <el-button v-if="canCreate" class="btn-aliyun-cta" :icon="Plus" @click="open()">新建约束规则</el-button>
      </div>
    </header>

    <!-- 过滤工具栏 -->
    <div class="toolbar-box">
      <el-input
        v-model="query.keyword"
        clearable
        placeholder="搜索规则名称或说明..."
        class="search-input"
        @keyup.enter="load"
        @clear="load"
      />

      <div class="filter-group">
        <button
          v-for="item in sourceFilterOptions"
          :key="item.key"
          type="button"
          class="filter-tab-btn"
          :class="{ active: currentSourceFilter === item.key }"
          @click="setSourceFilter(item.key)"
        >
          {{ item.label }}
        </button>
      </div>
    </div>

    <!-- 规则主列表 (Master Table) -->
    <div class="table-card">
      <el-table
        :data="filteredItems"
        v-loading="loading"
        size="default"
        class="master-table"
        @row-click="openDetail"
      >
        <!-- 序号列 (低调中性) -->
        <el-table-column label="序号" width="60" align="center">
          <template #default="{ $index }">
            <span class="index-num">{{ formatIndex($index) }}</span>
          </template>
        </el-table-column>

        <!-- 规则名称 -->
        <el-table-column prop="ruleName" label="规则名称" width="190">
          <template #default="{ row }">
            <strong class="rule-name-text">{{ row.ruleName }}</strong>
          </template>
        </el-table-column>

        <!-- 业务判定逻辑 (自然语言结构化标签流) -->
        <el-table-column label="业务判定逻辑" min-width="440">
          <template #default="{ row }">
            <div class="sentence-stream">
              <span class="sentence-static">当</span>
              <span class="sentence-chip emphasis">{{ getSentenceTokens(row).conditionExpr }}</span>
              <span class="sentence-static">持续</span>
              <span class="sentence-chip">{{ getSentenceTokens(row).windowText }}</span>
              <span class="sentence-arrow">➔</span>
              <span class="sentence-static">执行</span>
              <span v-for="(act, idx) in getSentenceTokens(row).actionLabels" :key="idx" class="sentence-chip action">
                {{ act }}
              </span>
            </div>
          </template>
        </el-table-column>


        <!-- 观测空间来源 -->
        <el-table-column label="观测空间来源" width="140">
          <template #default="{ row }">
            <span class="source-label">{{ getRuleSourceLabel(row) }}</span>
          </template>
        </el-table-column>

        <!-- 判定窗口 -->
        <el-table-column label="判定窗口" width="95">
          <template #default="{ row }">
            <span class="window-label">{{ row.windowSeconds && row.windowSeconds > 0 ? row.windowSeconds + ' 秒' : '瞬时' }}</span>
          </template>
        </el-table-column>

        <!-- 状态开关 (同一层级唯一状态控制) -->
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <div @click.stop>
              <el-switch
                v-model="row.isEnabled"
                :disabled="!canEdit"
                size="small"
                @change="value => setEnabled(row, Boolean(value))"
              />
            </div>
          </template>
        </el-table-column>

        <!-- 操作列 (使用与数据中心一致的链接按键) -->
        <el-table-column label="操作" width="140" align="right" fixed="right">
          <template #default="{ row }">
            <div class="row-actions" @click.stop>
              <el-button link class="btn-aliyun-link" size="small" @click="openDetail(row)">详情</el-button>
              <el-button v-if="canEdit" link class="btn-aliyun-link" size="small" @click="open(row)">编辑</el-button>
              <el-popconfirm v-if="canDelete" title="确认删除该约束规则？" @confirm="remove(row)">
                <template #reference>
                  <el-button link class="btn-aliyun-danger-link" size="small">删除</el-button>
                </template>
              </el-popconfirm>
            </div>
          </template>
        </el-table-column>
      </el-table>


      <!-- 分页栏 -->
      <div class="pager-bar">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="load"
          @size-change="load"
        />
      </div>
    </div>

    <!-- ==================== 1. 规则详情右侧抽屉 (Slide-Over Detail Drawer) ==================== -->
    <el-drawer
      v-model="detailVisible"
      title="约束规则详情与监控"
      size="780px"
      destroy-on-close
      class="detail-drawer"
      @closed="stopTelemetryTimer"
    >
      <template #header>
        <div class="drawer-custom-header">
          <h2>{{ currentDetailRule?.ruleName || '约束规则详情' }}</h2>
          <span>{{ getRuleSourceLabel(currentDetailRule) }} · 判定窗口: {{ currentDetailRule?.windowSeconds ? currentDetailRule.windowSeconds + '秒' : '瞬时' }}</span>
        </div>
      </template>

      <div class="drawer-content">
        <el-tabs v-model="detailActiveTab">
          <!-- 标签 1: 实时监测 (完全聚焦运行态现场与真实安全波形) -->
          <el-tab-pane label="实时监测" name="observe">
            <!-- 实时指标横幅 -->
            <div class="metric-ribbon-card">
              <div class="metric-block">
                <span class="m-label">监测对象</span>
                <strong class="m-val" :title="getSentenceTokens(currentDetailRule).mainSource">
                  {{ getSentenceTokens(currentDetailRule).mainSource }}
                </strong>
              </div>
              <div class="metric-block">
                <span class="m-label">实时遥测读数</span>
                <strong class="m-val live-val">
                  {{ liveTelemetryValue !== null && liveTelemetryValue !== undefined ? liveTelemetryValue : '等待上报' }}
                </strong>
              </div>
              <div v-if="ruleThresholdValue !== null" class="metric-block">
                <span class="m-label">警戒红线阈值</span>
                <strong class="m-val threshold-val">{{ ruleThresholdValue }}</strong>
              </div>
              <div class="metric-block status-cell">
                <span class="m-label">实时状态</span>
                <div class="status-badge-wrapper">
                  <span class="live-status-pill" :class="ruleCurrentStatusClass">
                    <span class="status-dot" :class="ruleCurrentStatusClass"></span>
                    {{ ruleCurrentStatusText }}
                  </span>
                </div>
              </div>
            </div>

            <!-- 真实时序动态走势图 (基于后端 ObservationHistoryStore 内存真实数据) -->
            <div class="detail-section" style="margin-top: 16px;">
              <div class="section-heading">
                <span>时序走势图</span>
              </div>

              <div class="chart-wrapper">
                <svg v-if="chartPoints.length > 1" viewBox="0 0 680 160" class="chart-svg">
                  <!-- 网格基准线 -->
                  <line x1="40" y1="20" x2="660" y2="20" stroke="#f1f5f9" stroke-width="1" />
                  <line x1="40" y1="70" x2="660" y2="70" stroke="#f1f5f9" stroke-width="1" />
                  <line x1="40" y1="120" x2="660" y2="120" stroke="#f1f5f9" stroke-width="1" />

                  <!-- 红色警戒阈值线 -->
                  <template v-if="ruleThresholdValue !== null">
                    <line
                      x1="40"
                      :y1="thresholdY"
                      x2="660"
                      :y2="thresholdY"
                      stroke="#ef4444"
                      stroke-dasharray="4,4"
                      stroke-width="1.5"
                    />
                    <text x="560" :y="thresholdY - 5" fill="#ef4444" font-size="11" font-weight="600">
                      警戒红线: {{ ruleThresholdValue }}
                    </text>
                  </template>

                  <!-- 真实采样时序曲线 -->
                  <path :d="svgPathD" fill="none" stroke="#2563eb" stroke-width="2" stroke-linecap="round" />

                  <!-- 曲线下渐变填充区域 (微光) -->
                  <path :d="svgAreaD" fill="rgba(37, 99, 235, 0.05)" />

                  <!-- 当前最新采样高亮点 -->
                  <circle :cx="latestPointX" :cy="latestPointY" r="4" fill="#2563eb" stroke="#ffffff" stroke-width="2" />
                  <circle :cx="latestPointX" :cy="latestPointY" r="7" fill="none" stroke="#2563eb" stroke-opacity="0.4" />

                  <!-- X轴时间标尺 -->
                  <text x="40" y="145" fill="#94a3b8" font-size="10">-60s</text>
                  <text x="195" y="145" fill="#94a3b8" font-size="10">-45s</text>
                  <text x="350" y="145" fill="#94a3b8" font-size="10">-30s</text>
                  <text x="505" y="145" fill="#94a3b8" font-size="10">-15s</text>
                  <text x="640" y="145" fill="#2563eb" font-size="10" font-weight="600">当前</text>
                </svg>

                <div v-else class="chart-empty-state">
                  <span>{{ isAttributeRule(currentDetailRule) ? '正在等待设备高频遥测上报数据...' : '当前规则监测非数值型状态，状态机正常活动中' }}</span>
                </div>
              </div>
            </div>

            <!-- 实时触发判定与数据中心引导栏 -->
            <div class="live-trigger-bar">
              <div class="live-trigger-info">
                <span class="trigger-label">触发条件：</span>
                <strong class="trigger-expr">{{ getInstantiatedExpr(currentDetailRule) }}</strong>
                <span class="trigger-window">{{ currentDetailRule?.windowSeconds ? `持续 ${currentDetailRule.windowSeconds} 秒即触发违规` : '瞬时满足即触发违规' }}</span>
              </div>
              <router-link :to="datacenterRedirectUrl" class="datacenter-link">前往数据中心查看历史数据 ➔</router-link>
            </div>
          </el-tab-pane>


          <!-- 标签 2: 配置详情 (纯静态契约，零运行态干扰) -->
          <el-tab-pane label="配置详情" name="config">
            <!-- 规则基本信息与自然语言流 -->
            <div class="detail-section">
              <div class="section-heading">业务逻辑概览</div>
              <div class="sentence-stream" style="padding: 10px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 4px;">
                <span class="sentence-static">当</span>
                <span class="sentence-chip emphasis">{{ getSentenceTokens(currentDetailRule).conditionExpr }}</span>
                <span class="sentence-static">持续</span>
                <span class="sentence-chip">{{ getSentenceTokens(currentDetailRule).windowText }}</span>
                <span class="sentence-arrow">➔</span>
                <span class="sentence-static">执行</span>

                <span v-for="(act, idx) in getSentenceTokens(currentDetailRule).actionLabels" :key="idx" class="sentence-chip action">
                  {{ act }}
                </span>
              </div>
              <div class="meta-formula-text">
                <span>判定公式：<code>{{ getInstantiatedExpr(currentDetailRule) }}</code></span>
                <span style="margin-left: 18px; color: #64748b;">公式模板：<code>{{ currentDetailRule?.expression }}</code></span>
              </div>

              <div v-if="currentDetailRule?.description" class="meta-desc-text">
                规则说明：{{ currentDetailRule.description }}
              </div>
            </div>

            <!-- 变量绑定清单表格 (全面使用 displayName) -->
            <div class="detail-section" style="margin-top: 16px;">
              <div class="section-heading">变量标识符绑定清单 (Bindings)</div>
              <table class="grid-table">
                <thead>
                  <tr>
                    <th style="width: 18%;">变量标识符</th>
                    <th style="width: 20%;">绑定类型</th>
                    <th style="width: 42%;">关联目标</th>
                    <th style="width: 20%;">数据类型</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(b, name) in (currentDetailRule?.bindings || {})" :key="name">
                    <td><code>@{{ name }}</code></td>
                    <td>{{ b.bindingType === 'OBSERVABLE' ? '观测数据' : '固定常量' }}</td>
                    <td>{{ resolveBindingTargetDesc(b) }}</td>
                    <td>{{ resolveBindingDataType(b) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <!-- 处置动作清单表格 (全面使用 displayName) -->
            <div class="detail-section" style="margin-top: 16px;">
              <div class="section-heading">违规处置动作清单 (Actions)</div>
              <table class="grid-table">
                <thead>
                  <tr>
                    <th style="width: 14%;">执行顺序</th>
                    <th style="width: 20%;">动作类别</th>
                    <th style="width: 36%;">执行内容</th>
                    <th style="width: 30%;">参数配置</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(act, idx) in (currentDetailRule?.violationActions || [])" :key="idx">
                    <td>动作 {{ idx + 1 }}</td>
                    <td>{{ act.actionType === 'DEVICE_CAPABILITY' ? '设备能力' : '系统动作' }}</td>
                    <td>{{ resolveActionContent(act) }}</td>
                    <td>{{ resolveActionParams(act) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </el-tab-pane>

          <!-- 标签 3: 违规记录 (历史安全事件审计) -->
          <el-tab-pane label="违规记录" name="logs">
            <div class="detail-section">
              <div class="section-heading">
                <span>历史违规与联锁记录</span>
                <el-button link class="btn-aliyun-link" size="small" @click="loadViolationLogs">刷新日志</el-button>
              </div>

              <el-table :data="violationLogs" v-loading="violationLoading" size="small" border stripe>
                <el-table-column prop="violationTime" label="触发时间" min-width="160">
                  <template #default="{ row }">{{ formatTime(row.violationTime) }}</template>
                </el-table-column>
                <el-table-column prop="taskId" label="关联任务" width="100">
                  <template #default="{ row }">{{ row.taskId ? '任务 #' + row.taskId : '全局' }}</template>
                </el-table-column>
                <el-table-column label="现场测量值" min-width="160">
                  <template #default="{ row }">
                    <span style="color: #dc2626; font-weight: 600;">{{ formatActualValue(row.actualValue) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="actionTaken" label="处置结果" min-width="160">
                  <template #default="{ row }">
                    <span>{{ row.actionTaken || '成功执行' }}</span>
                  </template>
                </el-table-column>
              </el-table>

              <div v-if="!violationLogs.length && !violationLoading" class="empty-log-hint">
                该规则暂无违规触发记录，运行状态良好
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>


    <!-- ==================== 2. 新建/编辑规则弹窗 ==================== -->
    <el-dialog
      v-model="editorVisible"
      :title="editingId ? '编辑约束规则' : '新建约束规则'"
      width="980px"
      top="4vh"
      destroy-on-close
      class="constraint-editor-dialog"
      :close-on-click-modal="false"
      @opened="loadEditor"
    >
      <div class="editor-scroll-container">
        <ConstraintRuleEditor
          ref="editorRef"
          :models="models"
          :instances="instances"
          :workflows="workflows"
          :tasks="tasks"
          :source-types="sourceTypes"
          :system-actions="systemActions"
        />
      </div>
      <template #footer>
        <div class="dialog-custom-footer">
          <el-button class="btn-aliyun" @click="editorVisible = false">取消</el-button>
          <el-button class="btn-aliyun-cta" :loading="saving" @click="save">保存规则</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>


<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { Download, Plus, Refresh } from '@element-plus/icons-vue'
import { useAuthStore } from '../../stores/authStore'
import ConstraintRuleEditor from '../../components/constraint/ConstraintRuleEditor.vue'
import { formatRuleSentenceTokens, sourceCategoryLabel, instantiateExpression } from '../../utils/constraintExpression.js'

const auth = useAuthStore()
const canCreate = computed(() => auth.hasPermission('constraint_rule:create'))
const canEdit = computed(() => auth.hasPermission('constraint_rule:edit'))
const canDelete = computed(() => auth.hasPermission('constraint_rule:delete'))

const loading = ref(false)
const saving = ref(false)
const exporting = ref(false)
const editorVisible = ref(false)
const detailVisible = ref(false)
const detailActiveTab = ref('observe')

const items = ref<any[]>([])
const total = ref(0)
const editingId = ref<number | null>(null)
const editingRule = ref<any>(null)
const currentDetailRule = ref<any>(null)
const editorRef = ref<any>()

const models = ref<any[]>([])
const instances = ref<any[]>([])
const workflows = ref<any[]>([])
const tasks = ref<any[]>([])
const sourceTypes = ref<string[]>([])
const systemActions = ref<string[]>([])

const violationLogs = ref<any[]>([])
const violationLoading = ref(false)
const liveTelemetryValue = ref<any>(null)

const query = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  isEnabled: undefined as boolean | undefined
})

const currentSourceFilter = ref('ALL')
const sourceFilterOptions = [
  { key: 'ALL', label: '全部规则' },
  { key: 'DEVICE_ATTRIBUTE', label: '设备属性' },
  { key: 'DEVICE_OPERATION_STATE', label: '设备OP状态' },
  { key: 'DEVICE_COMMAND_LIFECYCLE', label: '设备CMD状态' },
  { key: 'TASK_LIFECYCLE_STATE', label: '任务实例状态' }
]

function formatIndex(idx: number) {
  const num = (query.pageNo - 1) * query.pageSize + idx + 1
  return num < 10 ? `0${num}` : `${num}`
}

function setSourceFilter(key: string) {
  currentSourceFilter.value = key
}

function getRuleSourceType(rule: any): string {
  const bindings = rule?.bindings || {}
  for (const b of Object.values(bindings) as any[]) {
    if (b.bindingType === 'OBSERVABLE' && b.source?.sourceType) {
      return b.source.sourceType
    }
  }
  return 'DEVICE_ATTRIBUTE'
}

function getRuleSourceLabel(rule: any): string {
  return sourceCategoryLabel(getRuleSourceType(rule))
}

const filteredItems = computed(() => {
  if (currentSourceFilter.value === 'ALL') return items.value
  return items.value.filter(item => getRuleSourceType(item) === currentSourceFilter.value)
})

function getSentenceTokens(rule: any) {
  return formatRuleSentenceTokens(rule, models.value, instances.value)
}

function isAttributeRule(rule: any): boolean {
  return getRuleSourceType(rule) === 'DEVICE_ATTRIBUTE'
}

async function refs() {
  const [o, m, i, w, t] = await Promise.all([
    axios.get('/api/constraint/rule/options'),
    axios.get('/api/device/model/list'),
    axios.get('/api/device/instance/list'),
    axios.get('/api/workflow/list'),
    axios.get('/api/task/page', { params: { pageNo: 1, pageSize: 500 } })
  ])
  if (o.data?.success) {
    sourceTypes.value = o.data.data.observableSourceTypes || []
    systemActions.value = o.data.data.systemViolationActions || []
  }
  if (m.data?.success) models.value = m.data.data || []
  if (i.data?.success) instances.value = i.data.data || []
  if (t.data?.success) tasks.value = t.data.data?.records || []
  if (w.data?.success) {
    const summaries = w.data.data || []
    workflows.value = await Promise.all(
      summaries.map(async (flow: any) => {
        try {
          const detail = await axios.get('/api/workflow/detail/' + flow.id)
          return detail.data?.success ? { ...flow, ...detail.data.data } : flow
        } catch {
          return flow
        }
      })
    )
  }
}

async function load() {
  loading.value = true
  try {
    const res = await axios.get('/api/constraint/rule/page', { params: query })
    if (!res.data?.success) throw Error(res.data?.message || '加载失败')
    items.value = res.data.data?.records || []
    total.value = res.data.data?.total || 0
  } catch (e: any) {
    ElMessage.error(e.message || '加载约束规则失败')
  } finally {
    loading.value = false
  }
}

async function exportGlobalModel() {
  exporting.value = true
  try {
    const res = await axios.get('/api/constraint/model/global/export', { responseType: 'blob' })
    downloadJsonBlob(res.data, 'global-constraint-model.json')
    ElMessage.success('全局约束模型已导出')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || '导出全局约束模型失败')
  } finally {
    exporting.value = false
  }
}

function downloadJsonBlob(data: any, filename: string) {
  const blob = data instanceof Blob ? data : new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

async function open(row?: any) {
  editingId.value = row?.id || null
  editingRule.value = row || null
  await refs()
  editorVisible.value = true
}

function loadEditor() {
  editorRef.value?.loadRule(editingRule.value)
}

async function save() {
  try {
    const rule = editorRef.value?.validateAndBuild()
    if (!rule) return
    const payload = { ...rule, id: editingId.value }
    saving.value = true
    const res = editingId.value
      ? await axios.put('/api/constraint/rule/' + editingId.value, payload)
      : await axios.post('/api/constraint/rule', payload)
    if (!res.data?.success) throw Error(res.data?.message || '保存失败')
    ElMessage.success('约束规则已保存')
    editorVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e.message || '保存约束规则失败')
  } finally {
    saving.value = false
  }
}

async function setEnabled(row: any, value: boolean) {
  try {
    const res = await axios.patch('/api/constraint/rule/' + row.id + '/enabled', null, { params: { enabled: value } })
    if (!res.data?.success) throw Error(res.data?.message || '更新失败')
    ElMessage.success(`规则已${value ? '启用' : '停用'}`)
  } catch (e: any) {
    row.isEnabled = !value
    ElMessage.error(e.message || '更新失败')
  }
}

async function remove(row: any) {
  try {
    const res = await axios.delete('/api/constraint/rule/' + row.id)
    if (!res.data?.success) throw Error(res.data?.message || '删除失败')
    ElMessage.success('约束规则已删除')
    load()
  } catch (e: any) {
    ElMessage.error(e.message || '删除失败')
  }
}

/* 详情抽屉时序走势与实时观测 */
const chartPoints = ref<{ time: string; value: number }[]>([])
const telemetryTimer = ref<any>(null)

const ruleThresholdValue = computed<number | null>(() => {
  if (!currentDetailRule.value) return null
  const bindings = currentDetailRule.value.bindings || {}
  for (const b of Object.values(bindings) as any[]) {
    if (b.bindingType === 'LITERAL' && typeof b.value === 'number') {
      return b.value
    }
  }
  const match = String(currentDetailRule.value.expression || '').match(/(?:>|<|>=|<=|==)\s*([0-9]+(?:\.[0-9]+)?)/)
  if (match) {
    const parsed = parseFloat(match[1])
    if (!isNaN(parsed)) return parsed
  }
  return null
})

// 计算实时报警状态 (正常 / 违规 / 已停用)
const isRuleViolated = computed(() => {
  if (!currentDetailRule.value?.isEnabled) return false
  if (liveTelemetryValue.value === null || liveTelemetryValue.value === undefined) return false
  if (ruleThresholdValue.value === null) return false
  const expr = String(currentDetailRule.value.expression || '')
  const val = Number(liveTelemetryValue.value)
  const threshold = Number(ruleThresholdValue.value)
  if (isNaN(val) || isNaN(threshold)) return false
  if (expr.includes('>') && !expr.includes('>=')) return val > threshold
  if (expr.includes('>=')) return val >= threshold
  if (expr.includes('<') && !expr.includes('<=')) return val < threshold
  if (expr.includes('<=')) return val <= threshold
  if (expr.includes('!=') || expr.includes('! ==')) return val !== threshold
  if (expr.includes('==')) return val === threshold
  return false
})

const ruleCurrentStatusText = computed(() => {
  if (!currentDetailRule.value?.isEnabled) return '已停用'
  return isRuleViolated.value ? '违规' : '正常'
})

const ruleCurrentStatusClass = computed(() => {
  if (!currentDetailRule.value?.isEnabled) return 'disabled'
  return isRuleViolated.value ? 'violated' : 'normal'
})

function getInstantiatedExpr(rule: any) {
  return instantiateExpression(rule, models.value, instances.value) || rule?.expression || '-'
}

const currentRuleInstanceId = computed<number | null>(() => {
  if (!currentDetailRule.value) return null
  const bindings = currentDetailRule.value.bindings || {}
  for (const b of Object.values(bindings) as any[]) {
    if (b.bindingType === 'OBSERVABLE' && b.source?.deviceInstanceId) {
      return Number(b.source.deviceInstanceId)
    }
  }
  return null
})

const datacenterRedirectUrl = computed(() => {
  if (currentRuleInstanceId.value) {
    return `/data-management?instanceId=${currentRuleInstanceId.value}`
  }
  return '/data-management'
})




// 计算图表 Y 轴映射边界与坐标
const yBounds = computed(() => {
  const values = chartPoints.value.map(p => p.value).filter(v => typeof v === 'number' && !isNaN(v))
  if (ruleThresholdValue.value !== null) values.push(ruleThresholdValue.value)
  if (!values.length) return { min: 0, max: 100 }
  let min = Math.min(...values)
  let max = Math.max(...values)
  if (min === max) {
    min -= 5
    max += 5
  } else {
    const span = max - min
    min -= span * 0.15
    max += span * 0.15
  }
  return { min, max }
})

function mapCoordY(val: number): number {
  const { min, max } = yBounds.value
  if (max === min) return 70
  // SVG 高度 160，有效绘图 Y 范围 20 ~ 130
  const ratio = (val - min) / (max - min)
  return 130 - ratio * 110
}

function mapCoordX(idx: number, total: number): number {
  if (total <= 1) return 350
  // 有效绘图 X 范围 40 ~ 660
  return 40 + (idx / (total - 1)) * 620
}

const thresholdY = computed(() => {
  if (ruleThresholdValue.value === null) return 30
  return mapCoordY(ruleThresholdValue.value)
})

const svgPathD = computed(() => {
  const total = chartPoints.value.length
  if (total < 2) return ''
  return chartPoints.value
    .map((p, i) => {
      const x = mapCoordX(i, total).toFixed(1)
      const y = mapCoordY(p.value).toFixed(1)
      return `${i === 0 ? 'M' : 'L'} ${x},${y}`
    })
    .join(' ')
})

const svgAreaD = computed(() => {
  const total = chartPoints.value.length
  if (total < 2) return ''
  const line = chartPoints.value
    .map((p, i) => {
      const x = mapCoordX(i, total).toFixed(1)
      const y = mapCoordY(p.value).toFixed(1)
      return `${i === 0 ? 'M' : 'L'} ${x},${y}`
    })
    .join(' ')
  const xFirst = mapCoordX(0, total).toFixed(1)
  const xLast = mapCoordX(total - 1, total).toFixed(1)
  return `${line} L ${xLast},140 L ${xFirst},140 Z`
})

const latestPointX = computed(() => mapCoordX(Math.max(0, chartPoints.value.length - 1), chartPoints.value.length))
const latestPointY = computed(() => {
  if (!chartPoints.value.length) return 70
  return mapCoordY(chartPoints.value[chartPoints.value.length - 1].value)
})

/* 详情抽屉 */
async function openDetail(row: any) {
  stopTelemetryTimer()
  currentDetailRule.value = row
  detailActiveTab.value = 'observe'
  detailVisible.value = true
  loadViolationLogs()
  fetchLiveTwinState(row)
}

function stopTelemetryTimer() {
  if (telemetryTimer.value) {
    clearInterval(telemetryTimer.value)
    telemetryTimer.value = null
  }
}

async function fetchLiveTwinState(rule: any) {
  liveTelemetryValue.value = null
  chartPoints.value = []
  const bindings = rule?.bindings || {}
  let boundInstanceId: number | null = null
  let boundTargetName = ''

  for (const b of Object.values(bindings) as any[]) {
    if (b.bindingType === 'OBSERVABLE' && b.source?.deviceInstanceId && b.source?.targetName) {
      boundInstanceId = b.source.deviceInstanceId
      boundTargetName = b.source.targetName
      break
    }
  }

  if (!boundInstanceId || !boundTargetName) return

  // 1. 真实拉取约束引擎内存中的最近 60 秒时序采样点
  try {
    const histRes = await axios.get('/api/device/twin-state/history', {
      params: { instanceId: boundInstanceId, targetName: boundTargetName, seconds: 60 }
    })
    if (histRes.data?.success && Array.isArray(histRes.data.data) && histRes.data.data.length > 0) {
      chartPoints.value = histRes.data.data.map((item: any) => ({
        time: item.observedAt,
        value: Number(item.value)
      }))
      liveTelemetryValue.value = chartPoints.value[chartPoints.value.length - 1].value
    }
  } catch {
    // 忽略历史拉取异常
  }

  // 2. 如果历史为空，拉取单点孪生最新快照建立初始点
  try {
    const twinRes = await axios.get('/api/device/twin-state/by-instance/' + boundInstanceId)
    if (twinRes.data?.success && twinRes.data.data?.attributes) {
      const val = twinRes.data.data.attributes[boundTargetName]
      if (val !== undefined && val !== null) {
        liveTelemetryValue.value = val
        if (!chartPoints.value.length) {
          chartPoints.value = [{ time: new Date().toISOString(), value: Number(val) }]
        }
      }
    }
  } catch {
    // 静默处理快照异常
  }

  // 3. 启动 1 秒定时器进行平滑推流采样更新
  telemetryTimer.value = setInterval(async () => {
    if (!detailVisible.value || !boundInstanceId) return
    try {
      const res = await axios.get('/api/device/twin-state/by-instance/' + boundInstanceId)
      if (res.data?.success && res.data.data?.attributes) {
        const val = res.data.data.attributes[boundTargetName]
        if (val !== undefined && val !== null) {
          liveTelemetryValue.value = val
          chartPoints.value.push({ time: new Date().toISOString(), value: Number(val) })
          if (chartPoints.value.length > 60) chartPoints.value.shift()
        }
      }
    } catch {
      // 忽略轮询抖动
    }
  }, 1000)
}


async function loadViolationLogs() {
  if (!currentDetailRule.value?.id) return
  violationLoading.value = true
  try {
    const res = await axios.get('/api/constraint/violation/page', {
      params: { constraintRuleId: currentDetailRule.value.id, pageNo: 1, pageSize: 20 }
    })
    if (res.data?.success) {
      violationLogs.value = res.data.data?.records || []
    }
  } catch {
    violationLogs.value = []
  } finally {
    violationLoading.value = false
  }
}

function resolveBindingTargetDesc(b: any): string {
  if (b.bindingType === 'LITERAL') return `固定值: ${b.value}`
  const src = b.source || {}
  const inst = instances.value.find(i => Number(i.id) === Number(src.deviceInstanceId))
  const mdl = models.value.find(m => Number(m.id || m.modelId) === Number(src.deviceModelId || inst?.deviceModelId))
  const prefix = inst?.instanceName || mdl?.modelName || (src.deviceInstanceId ? `实例${src.deviceInstanceId}` : '设备')
  if (src.sourceType === 'DEVICE_ATTRIBUTE') {
    const attr = (mdl?.attributes || []).find((a: any) => a.attributeName === src.targetName)
    return `${prefix} · 属性 ${attr?.displayName || src.targetName}`
  }
  if (src.sourceType === 'DEVICE_OPERATION_STATE') return `${prefix} · 分区 ${src.regionName}`
  if (src.sourceType === 'DEVICE_COMMAND_LIFECYCLE') return `${prefix} · 指令生命周期`
  if (src.sourceType === 'TASK_LIFECYCLE_STATE') return `任务 ${src.taskId ? '#' + src.taskId : '当前任务'} 状态`
  return `${prefix} (未知属性)`
}

function resolveBindingDataType(b: any): string {
  if (b.bindingType === 'LITERAL') return typeof b.value === 'number' ? '数值' : typeof b.value === 'boolean' ? '布尔值' : '字符串'
  const map: any = { DOUBLE: '浮点数', INTEGER: '整数', STRING: '字符串', BOOLEAN: '布尔值', JSON: 'JSON' }
  return map[b.source?.dataType] || b.source?.dataType || '-'
}

function resolveActionContent(act: any): string {
  if (act.actionType === 'SYSTEM') {
    const map: any = { ABORT: '终止当前任务 (ABORT)', PAUSE: '暂停当前任务 (PAUSE)', ALERT: '发布系统告警 (ALERT)' }
    return map[act.action] || act.action || '系统动作'
  }
  const inst = instances.value.find(i => Number(i.id) === Number(act.deviceInstanceId))
  const mdl = models.value.find(m => Number(m.id || m.modelId) === Number(inst?.deviceModelId))
  const cap = (mdl?.capabilities || []).find((c: any) => c.capabilityName === act.capabilityName)
  const dev = inst?.instanceName || (act.deviceInstanceId ? `设备${act.deviceInstanceId}` : '设备')
  return `${dev} · ${cap?.displayName || act.capabilityName}`
}

function resolveActionParams(act: any): string {
  if (act.actionType === 'SYSTEM') return act.targetTaskId ? `目标任务 #${act.targetTaskId}` : '作用于当前任务'
  const inst = instances.value.find(i => Number(i.id) === Number(act.deviceInstanceId))
  const mdl = models.value.find(m => Number(m.id || m.modelId) === Number(inst?.deviceModelId))
  const cap = (mdl?.capabilities || []).find((c: any) => c.capabilityName === act.capabilityName)
  const params = act.parameters || {}
  const entries = Object.entries(params)
  if (!entries.length) return '无额外参数'
  const rawParams = cap?.capabilityParameters || cap?.parameters || []
  return entries.map(([k, v]) => {
    let paramLabel = k
    if (Array.isArray(rawParams)) {
      const pDef = rawParams.find((p: any) => (p.parameterName || p.name) === k)
      if (pDef?.displayName) paramLabel = pDef.displayName
    }
    return `${paramLabel}=${v}`
  }).join(', ')
}


function formatTime(t: string) {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 19)
}

function formatActualValue(val: any) {
  if (val === null || val === undefined) return '-'
  if (typeof val === 'object') {
    return Object.entries(val)
      .map(([k, v]) => `${k}=${v}`)
      .join(', ')
  }
  return String(val)
}

onMounted(() => {
  load()
  refs().catch((e: any) => ElMessage.error(e.message || '加载约束配置资源失败'))
})
</script>

<style scoped>
/* 阿里云工业白底按键标准 (与数据中心和设备模型统一) */
.btn-aliyun {
  background: #ffffff !important;
  border: 1px solid #d9d9d9 !important;
  color: rgba(0, 0, 0, 0.88) !important;
  font-weight: 400 !important;
  transition: all 0.15s ease;
}
.btn-aliyun:hover:not(:disabled):not(.is-disabled) {
  background: #ffffff !important;
  border-color: #4096ff !important;
  color: #1677ff !important;
}

.btn-aliyun-cta {
  background: #ffffff !important;
  border: 1px solid #1677ff !important;
  color: #1677ff !important;
  font-weight: 500 !important;
  transition: all 0.15s ease;
}
.btn-aliyun-cta:hover:not(:disabled):not(.is-disabled) {
  background: #1677ff !important;
  border-color: #1677ff !important;
  color: #ffffff !important;
}

.btn-aliyun:disabled,
.btn-aliyun.is-disabled,
.btn-aliyun-cta:disabled,
.btn-aliyun-cta.is-disabled {
  background: #f5f5f5 !important;
  border-color: #d9d9d9 !important;
  color: rgba(0, 0, 0, 0.25) !important;
  cursor: not-allowed !important;
}

.btn-aliyun-link {
  background: transparent !important;
  border: none !important;
  color: #1677ff !important;
  padding: 0 4px !important;
  font-weight: 400 !important;
}
.btn-aliyun-link:hover {
  color: #4096ff !important;
  text-decoration: underline !important;
  background: transparent !important;
}

.btn-aliyun-danger-link {
  background: transparent !important;
  border: none !important;
  color: #ff4d4f !important;
  padding: 0 4px !important;
  font-weight: 400 !important;
}
.btn-aliyun-danger-link:hover {
  color: #ff7875 !important;
  text-decoration: underline !important;
  background: transparent !important;
}

.security-page {
  padding: 24px 32px 60px;
  background: #f8fafc;
  min-height: calc(100vh - 60px);
}

.page-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.page-heading h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #0f172a;
}
.page-heading p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}
.header-actions {
  display: flex;
  gap: 8px;
}

.toolbar-box {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  gap: 16px;
  flex-wrap: wrap;
}
.search-input {
  width: 280px;
}

.filter-group {
  display: flex;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
}
.filter-tab-btn {
  padding: 6px 14px;
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 12.5px;
  cursor: pointer;
  border-right: 1px solid #e2e8f0;
  transition: all 0.12s ease;
}
.filter-tab-btn:last-child {
  border-right: none;
}
.filter-tab-btn:hover {
  color: #0f172a;
}
.filter-tab-btn.active {
  background: #f1f5f9;
  color: #0f172a;
  font-weight: 600;
}

.table-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
}

.master-table {
  width: 100%;
}
.master-table :deep(tr) {
  cursor: pointer;
}
.master-table :deep(th.el-table__cell) {
  background: #f8fafc;
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
  padding: 10px 0;
}
.master-table :deep(td.el-table__cell) {
  padding: 14px 0;
}

.index-num {
  font-family: ui-monospace, monospace;
  color: #94a3b8;
  font-size: 12.5px;
}
.rule-name-text {
  font-size: 13.5px;
  color: #0f172a;
}

.sentence-stream {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  line-height: 1.6;
}
.sentence-static {
  color: #64748b;
}
.sentence-chip {
  padding: 1px 6px;
  border-radius: 3px;
  background: #f1f5f9;
  border: 1px solid #cbd5e1;
  color: #1e293b;
  font-size: 12px;
  font-weight: 500;
}
.sentence-chip.emphasis {
  font-weight: 600;
}
.sentence-chip.action {
  background: #fef2f2;
  border-color: #fecaca;
  color: #dc2626;
}
.sentence-arrow {
  color: #94a3b8;
  margin: 0 2px;
}

.source-label,
.window-label {
  font-size: 12.5px;
  color: #64748b;
}

.row-actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
}

.pager-bar {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
  border-top: 1px solid #e2e8f0;
  background: #ffffff;
}

/* 详情抽屉 */
.drawer-custom-header h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
}
.drawer-custom-header span {
  font-size: 12px;
  color: #64748b;
  margin-top: 2px;
  display: block;
}

.detail-section {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  padding: 16px;
}
.section-heading {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.meta-formula-text {
  font-size: 12px;
  color: #64748b;
  margin-top: 8px;
}
.meta-formula-text code {
  color: #2563eb;
  font-family: ui-monospace, monospace;
}

.live-status-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  line-height: 1;
  padding: 4px 10px;
  border-radius: 12px;
  font-weight: 500;
}
.live-status-pill.normal {
  color: #16a34a;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
}
.live-status-pill.violated {
  color: #dc2626;
  background: #fef2f2;
  border: 1px solid #fecaca;
}
.live-status-pill.disabled {
  color: #64748b;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
}
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.status-dot.normal {
  background: #16a34a;
}
.status-dot.violated {
  background: #dc2626;
}
.status-dot.disabled {
  background: #94a3b8;
}

/* 实时监测指标横幅与时序图表样式 */
.metric-ribbon-card {
  display: grid;
  grid-template-columns: 1.5fr 1fr 1fr 1fr;
  gap: 12px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  padding: 14px 18px;
  align-items: center;
}
.metric-block {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.metric-block.status-cell {
  align-items: flex-end;
  justify-content: center;
}
.status-badge-wrapper {
  display: flex;
  align-items: center;
  margin-top: 2px;
}
.m-label {
  font-size: 12px;
  color: #64748b;
}
.m-val {
  font-size: 14px;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.m-val.live-val {
  font-size: 18px;
  color: #2563eb;
  font-family: ui-monospace, monospace;
}
.m-val.threshold-val {
  font-size: 18px;
  color: #ef4444;
  font-family: ui-monospace, monospace;
}

.chart-wrapper {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  padding: 12px;
  min-height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.chart-svg {
  width: 100%;
  height: 160px;
}
.chart-empty-state {
  font-size: 12.5px;
  color: #94a3b8;
  text-align: center;
  padding: 30px 0;
}

.live-trigger-bar {
  margin-top: 14px;
  padding: 11px 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.live-trigger-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
}
.trigger-label {
  color: #64748b;
}
.trigger-expr {
  color: #2563eb;
  background: #eff6ff;
  border: 1px solid #dbeafe;
  padding: 2px 8px;
  border-radius: 3px;
  font-family: ui-monospace, monospace;
  font-size: 12.5px;
}
.trigger-window {
  color: #64748b;
  font-size: 12px;
}
.meta-desc-text {
  font-size: 12px;
  color: #64748b;
  margin-top: 6px;
}


.datacenter-redirect-hint {

  margin-top: 12px;
  padding: 10px 14px;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  border-radius: 4px;
  font-size: 12px;
  color: #64748b;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.datacenter-link {
  color: #2563eb;
  text-decoration: none;
  font-weight: 500;
}
.datacenter-link:hover {
  text-decoration: underline;
}

.grid-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
}
.grid-table th,
.grid-table td {
  padding: 8px 10px;
  border: 1px solid #e2e8f0;
  text-align: left;
}
.grid-table th {
  background: #f8fafc;
  color: #64748b;
  font-weight: 600;
}

.empty-log-hint {
  text-align: center;
  font-size: 12.5px;
  color: #94a3b8;
  padding: 24px 0;
}

/* 新建/编辑规则弹窗固定视口与内部滚动优化 */
:deep(.constraint-editor-dialog) {
  display: flex;
  flex-direction: column;
  max-height: 90vh;
  margin-top: 5vh !important;
  border-radius: 6px;
  overflow: hidden;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
}

:deep(.constraint-editor-dialog .el-dialog__header) {
  padding: 16px 20px;
  margin-right: 0;
  border-bottom: 1px solid #e2e8f0;
  background: #ffffff;
  flex-shrink: 0;
}

:deep(.constraint-editor-dialog .el-dialog__title) {
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
}

:deep(.constraint-editor-dialog .el-dialog__body) {
  padding: 0;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.editor-scroll-container {
  padding: 20px;
  max-height: calc(90vh - 128px);
  overflow-y: auto;
  overflow-x: hidden;
  box-sizing: border-box;
}

/* 统一精致细滚动条 */
.editor-scroll-container::-webkit-scrollbar {
  width: 6px;
}
.editor-scroll-container::-webkit-scrollbar-track {
  background: #f8fafc;
}
.editor-scroll-container::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 3px;
}
.editor-scroll-container::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}

:deep(.constraint-editor-dialog .el-dialog__footer) {
  padding: 12px 20px;
  border-top: 1px solid #e2e8f0;
  background: #ffffff;
  flex-shrink: 0;
}

.dialog-custom-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>


