<template>
  <div class="security-page">
    <div class="constraint-workbench-canvas">
      <!-- 工作台头部 -->
      <div class="workbench-header">
        <div class="header-left">
          <h2 class="header-title">全局约束规则管理</h2>
          <span class="header-subtitle">安全联锁与设备工艺边界规则；违规时自动执行硬件安全能力与任务级熔断调度</span>
        </div>
        <div class="header-actions">
          <el-input
            v-model="query.keyword"
            class="search-input"
            size="small"
            clearable
            placeholder="搜索规则名称或说明..."
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
          <button class="btn-aliyun" type="button" :disabled="exporting" @click="exportGlobalModel">
            <el-icon><Download /></el-icon><span>导出约束模型</span>
          </button>
          <button class="btn-aliyun" type="button" @click="load">
            <el-icon><Refresh /></el-icon><span>刷新</span>
          </button>
          <button v-if="canCreate" class="btn-aliyun-cta" type="button" @click="open()">
            <el-icon><Plus /></el-icon><span>新建约束规则</span>
          </button>
        </div>
      </div>

      <!-- 规则主列表 (唯一局部滚动区) -->
      <div class="table-scroll-container">
        <div class="table-card" v-loading="loading">
          <el-table
            :data="filteredItems"
            size="small"
            class="master-table"
            height="100%"
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

            <!-- 操作列 (统一行内链接按键) -->
            <el-table-column label="操作" width="140" align="center" fixed="right">
              <template #default="{ row }">
                <div class="row-actions" @click.stop>
                  <button class="btn-link" type="button" @click="openDetail(row)">详情</button>
                  <button v-if="canEdit" class="btn-link" type="button" @click="open(row)">编辑</button>
                  <el-popconfirm v-if="canDelete" title="确认删除该约束规则？" @confirm="remove(row)">
                    <template #reference>
                      <button class="btn-link danger" type="button">删除</button>
                    </template>
                  </el-popconfirm>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <!-- 分页栏 -->
          <div class="pager-wrap">
            <el-pagination
              v-model:current-page="query.pageNo"
              v-model:page-size="query.pageSize"
              :total="total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              background
              size="small"
              @current-change="load"
              @size-change="load"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== 1. 规则详情右侧抽屉 (Slide-Over Detail Drawer) ==================== -->
    <el-drawer
      v-model="detailVisible"
      title="约束规则详情与监控"
      size="80%"
      destroy-on-close
      class="detail-drawer unified-workflow-drawer"
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
            <!-- 实时指标横幅 (35px 规范锁定) -->
            <div class="metric-ribbon">
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

              <div ref="chartHost" class="chart-wrapper">
                <svg
                  v-if="chartPoints.length > 1"
                  :viewBox="`0 0 ${CHART_W} ${CHART_H}`"
                  :width="CHART_W"
                  :height="CHART_H"
                  class="chart-svg"
                  preserveAspectRatio="xMinYMin meet"
                  @mousemove="onChartHover"
                  @mouseleave="hoverIndex = null"
                >
                  <defs>
                    <linearGradient id="tsAreaFill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stop-color="#2563eb" stop-opacity="0.14" />
                      <stop offset="100%" stop-color="#2563eb" stop-opacity="0" />
                    </linearGradient>
                  </defs>

                  <!-- Y 轴网格线与刻度标签 -->
                  <g v-for="tick in yTicks" :key="tick.value">
                    <line :x1="PAD_L" :x2="CHART_W - PAD_R" :y1="tick.y" :y2="tick.y" class="chart-grid-line" shape-rendering="crispEdges" />
                    <text :x="PAD_L - 6" :y="tick.y + 4" class="chart-axis-label" text-anchor="end">{{ tick.label }}</text>
                  </g>
                  <line :x1="PAD_L" :x2="CHART_W - PAD_R" :y1="PLOT_BOTTOM" :y2="PLOT_BOTTOM" class="chart-axis-line" shape-rendering="crispEdges" />

                  <!-- 违规区域 (阈值之上或之下的浅红填充, Datadog marker 风格) -->
                  <rect
                    v-if="violationRegion"
                    :x="PAD_L"
                    :y="violationRegion.y"
                    :width="CHART_W - PAD_L - PAD_R"
                    :height="violationRegion.h"
                    class="chart-violation-region"
                  />

                  <!-- 警戒阈值线与标签 -->
                  <template v-if="ruleThresholdValue !== null">
                    <line :x1="PAD_L" :x2="CHART_W - PAD_R" :y1="thresholdY" :y2="thresholdY" class="chart-threshold-line" />
                    <text :x="CHART_W - PAD_R - 4" :y="thresholdLabelY" class="chart-threshold-label" text-anchor="end">阈值 {{ ruleThresholdValue }}</text>
                  </template>

                  <!-- 渐变面积与平滑曲线 -->
                  <path :d="svgAreaD" fill="url(#tsAreaFill)" />
                  <path :d="svgPathD" class="chart-ts-line" />

                  <!-- X 轴时间刻度 -->
                  <text
                    v-for="tick in xTicks"
                    :key="tick.x"
                    :x="tick.x"
                    :y="CHART_H - 5"
                    class="chart-axis-label"
                    :text-anchor="tick.anchor"
                  >{{ tick.label }}</text>

                  <!-- 悬浮十字准线与命中点 -->
                  <g v-if="hoverPoint">
                    <line :x1="hoverPoint.x" :x2="hoverPoint.x" :y1="PAD_T" :y2="PLOT_BOTTOM" class="chart-crosshair" />
                    <circle :cx="hoverPoint.x" :cy="hoverPoint.y" r="3.5" class="chart-hover-dot" :class="{ violated: hoverPoint.violated }" />
                  </g>

                  <!-- 最新采样点与末端数值标签 -->
                  <template v-else>
                    <circle :cx="latestPoint.x" :cy="latestPoint.y" r="7" class="chart-latest-halo" />
                    <circle :cx="latestPoint.x" :cy="latestPoint.y" r="3" class="chart-latest-dot" />
                    <text :x="latestPoint.x - 8" :y="latestPoint.y - 9" class="chart-latest-label" text-anchor="end">{{ formatChartValue(latestPoint.value) }}</text>
                  </template>
                </svg>

                <!-- 悬浮读数气泡 -->
                <div v-if="hoverPoint" class="chart-tooltip" :style="tooltipStyle">
                  <span class="tip-time">{{ hoverPoint.timeLabel }}</span>
                  <span class="tip-value" :class="{ violated: hoverPoint.violated }">{{ formatChartValue(hoverPoint.value) }}</span>
                </div>

                <div v-if="chartPoints.length <= 1" class="chart-empty-state">
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
              <div class="section-heading">变量标识符绑定清单</div>
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
              <div class="section-heading">违规处置动作清单</div>
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
                <button class="btn-link" type="button" @click="loadViolationLogs">刷新日志</button>
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
      width="1080px"
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
          <button class="btn-aliyun" type="button" @click="editorVisible = false">取消</button>
          <button class="btn-primary-blue" type="button" :disabled="saving" @click="save">
            <span>{{ saving ? '保存中...' : '保存规则' }}</span>
          </button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>


<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
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
  { key: 'DEVICE_OPERATION_STATE', label: '设备功能状态' },
  { key: 'DEVICE_COMMAND_LIFECYCLE', label: '设备指令状态' },
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




/* 时序走势图几何体系：viewBox 宽度跟随容器像素，避免等比留白与文字缩放发虚 */
const chartHost = ref<HTMLElement | null>(null)
const chartWidth = ref(720)
const CHART_W = computed(() => Math.max(520, chartWidth.value))
const CHART_H = 200
const PAD_L = 56
const PAD_R = 16
const PAD_T = 14
const PAD_B = 26
const PLOT_BOTTOM = CHART_H - PAD_B
const hoverIndex = ref<number | null>(null)
let chartRo: ResizeObserver | null = null

function bindChartResize() {
  chartRo?.disconnect()
  if (!chartHost.value) return
  chartRo = new ResizeObserver(entries => {
    const w = Math.floor(entries[0]?.contentRect?.width || 0)
    if (w > 0 && w !== chartWidth.value) chartWidth.value = w
  })
  chartRo.observe(chartHost.value)
}

watch(detailVisible, vis => {
  if (vis) nextTick(bindChartResize)
  else {
    chartRo?.disconnect()
    chartRo = null
    hoverIndex.value = null
  }
})

const yBounds = computed(() => {
  const values = chartPoints.value.map(p => p.value).filter(v => Number.isFinite(v))
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
  if (max === min) return (PAD_T + PLOT_BOTTOM) / 2
  const ratio = (val - min) / (max - min)
  return PLOT_BOTTOM - ratio * (PLOT_BOTTOM - PAD_T)
}

function mapCoordX(idx: number, total: number): number {
  const width = CHART_W.value
  if (total <= 1) return (PAD_L + width - PAD_R) / 2
  return PAD_L + (idx / (total - 1)) * (width - PAD_L - PAD_R)
}

const plotPoints = computed(() =>
  chartPoints.value.map((p, i) => ({
    x: mapCoordX(i, chartPoints.value.length),
    y: mapCoordY(p.value),
    value: p.value,
    time: p.time
  }))
)

function smoothPath(points: { x: number; y: number }[]): string {
  if (points.length < 2) return ''
  let d = `M ${points[0].x.toFixed(1)},${points[0].y.toFixed(1)}`
  for (let i = 0; i < points.length - 1; i++) {
    const p0 = points[Math.max(0, i - 1)]
    const p1 = points[i]
    const p2 = points[i + 1]
    const p3 = points[Math.min(points.length - 1, i + 2)]
    const c1x = p1.x + (p2.x - p0.x) / 6
    const c1y = p1.y + (p2.y - p0.y) / 6
    const c2x = p2.x - (p3.x - p1.x) / 6
    const c2y = p2.y - (p3.y - p1.y) / 6
    d += ` C ${c1x.toFixed(1)},${c1y.toFixed(1)} ${c2x.toFixed(1)},${c2y.toFixed(1)} ${p2.x.toFixed(1)},${p2.y.toFixed(1)}`
  }
  return d
}

const svgPathD = computed(() => smoothPath(plotPoints.value))

const svgAreaD = computed(() => {
  const pts = plotPoints.value
  const line = svgPathD.value
  if (pts.length < 2 || !line) return ''
  return `${line} L ${pts[pts.length - 1].x.toFixed(1)},${PLOT_BOTTOM} L ${pts[0].x.toFixed(1)},${PLOT_BOTTOM} Z`
})

const thresholdY = computed(() => {
  if (ruleThresholdValue.value === null) return PAD_T
  return Math.max(PAD_T, Math.min(PLOT_BOTTOM, mapCoordY(ruleThresholdValue.value)))
})

const violationAbove = computed<boolean | null>(() => {
  const expr = String(currentDetailRule.value?.expression || '')
  if (expr.includes('>')) return true
  if (expr.includes('<')) return false
  return null
})

const violationRegion = computed(() => {
  if (ruleThresholdValue.value === null || violationAbove.value === null) return null
  const y = thresholdY.value
  return violationAbove.value
    ? { y: PAD_T, h: Math.max(0, y - PAD_T) }
    : { y, h: Math.max(0, PLOT_BOTTOM - y) }
})

const thresholdLabelY = computed(() => {
  const y = thresholdY.value
  return violationAbove.value === true
    ? Math.min(y + 13, PLOT_BOTTOM - 2)
    : Math.max(y - 5, PAD_T + 9)
})

function formatChartValue(v: number): string {
  if (!Number.isFinite(v)) return '-'
  if (Math.abs(v) >= 1000) return v.toLocaleString('en-US', { maximumFractionDigits: 0 })
  return Number.isInteger(v) ? String(v) : v.toFixed(1)
}

const yTicks = computed(() => {
  const { min, max } = yBounds.value
  if (max === min) return []
  const ticks: { value: number; y: number; label: string }[] = []
  for (let i = 1; i <= 3; i++) {
    const value = min + ((max - min) * i) / 4
    ticks.push({ value, y: mapCoordY(value), label: formatChartValue(value) })
  }
  return ticks
})

const xTicks = computed(() => {
  const pts = chartPoints.value
  const total = pts.length
  if (total < 2) return []
  const lastTime = new Date(pts[total - 1].time).getTime()
  const fractions = [0, 0.25, 0.5, 0.75, 1]
  return fractions.map((f, i) => {
    const idx = Math.round(f * (total - 1))
    const diffSec = Math.max(0, Math.round((lastTime - new Date(pts[idx].time).getTime()) / 1000))
    return {
      x: mapCoordX(idx, total),
      label: i === fractions.length - 1 ? '当前' : `-${diffSec}s`,
      anchor: i === 0 ? 'start' : i === fractions.length - 1 ? 'end' : 'middle'
    }
  })
})

const latestPoint = computed(() => {
  const pts = plotPoints.value
  return pts.length ? pts[pts.length - 1] : { x: 0, y: 0, value: 0, time: '' }
})

const hoverPoint = computed(() => {
  if (hoverIndex.value === null) return null
  const p = plotPoints.value[hoverIndex.value]
  if (!p) return null
  const t = new Date(p.time)
  const pad = (n: number) => String(n).padStart(2, '0')
  const violated = ruleThresholdValue.value !== null && violationAbove.value !== null
    ? (violationAbove.value ? p.value > ruleThresholdValue.value : p.value < ruleThresholdValue.value)
    : false
  return { ...p, timeLabel: `${pad(t.getHours())}:${pad(t.getMinutes())}:${pad(t.getSeconds())}`, violated }
})

const tooltipStyle = computed(() => {
  const p = hoverPoint.value
  if (!p) return {}
  const leftPct = (p.x / CHART_W.value) * 100
  return {
    left: `${leftPct}%`,
    top: `${(p.y / CHART_H) * 100}%`,
    transform: leftPct > 70 ? 'translate(calc(-100% - 10px), -110%)' : 'translate(10px, -110%)'
  }
})

function onChartHover(e: MouseEvent) {
  const el = e.currentTarget as SVGSVGElement
  const rect = el.getBoundingClientRect()
  if (!rect.width) return
  const fx = ((e.clientX - rect.left) / rect.width) * CHART_W.value
  const pts = plotPoints.value
  if (pts.length < 2) return
  let best = 0
  let bestDist = Infinity
  pts.forEach((p, i) => {
    const d = Math.abs(p.x - fx)
    if (d < bestDist) {
      bestDist = d
      best = i
    }
  })
  hoverIndex.value = best
}

/* 详情抽屉 */
async function openDetail(row: any) {
  stopTelemetryTimer()
  hoverIndex.value = null
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
      chartPoints.value = histRes.data.data
        .map((item: any) => ({ time: item.observedAt, value: Number(item.value) }))
        .filter((p: any) => Number.isFinite(p.value))
      if (chartPoints.value.length) {
        liveTelemetryValue.value = chartPoints.value[chartPoints.value.length - 1].value
      }
    }
  } catch {
    // 忽略历史拉取异常
  }

  // 2. 如果历史为空，拉取单点孪生最新快照建立初始点
  try {
    const twinRes = await axios.get('/api/device/twin-state/by-instance/' + boundInstanceId)
    if (twinRes.data?.success && twinRes.data.data?.currentAttr) {
      const val = twinRes.data.data.currentAttr[boundTargetName]
      if (val !== undefined && val !== null) {
        liveTelemetryValue.value = val
        if (!chartPoints.value.length && Number.isFinite(Number(val))) {
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
      if (res.data?.success && res.data.data?.currentAttr) {
        const val = res.data.data.currentAttr[boundTargetName]
        if (val !== undefined && val !== null && Number.isFinite(Number(val))) {
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

function resolveActionModel(act: any) {
  const inst = instances.value.find(i => Number(i.id) === Number(act.deviceInstanceId))
  const modelId = act.deviceModelId || inst?.deviceModelId
  const mdl = models.value.find(m => Number(m.id || m.modelId) === Number(modelId))
  return { inst, mdl }
}

function resolveActionContent(act: any): string {
  if (act.actionType === 'SYSTEM') {
    const map: any = { ABORT: '终止当前任务', PAUSE: '暂停当前任务', ALERT: '发布系统告警' }
    return map[act.action] || act.action || '系统动作'
  }
  const { inst, mdl } = resolveActionModel(act)
  const cap = (mdl?.capabilities || []).find((c: any) => c.capabilityName === act.capabilityName)
  const dev = inst?.instanceName || (act.deviceInstanceId ? `设备${act.deviceInstanceId}` : '设备')
  return `${dev} · ${cap?.displayName || act.capabilityName}`
}

function resolveActionParams(act: any): string {
  if (act.actionType === 'SYSTEM') return act.targetTaskId ? `目标任务 #${act.targetTaskId}` : '作用于当前任务'
  const { mdl } = resolveActionModel(act)
  const cap = (mdl?.capabilities || []).find((c: any) => c.capabilityName === act.capabilityName)
  const entries = Object.entries(act.parameters || {})
  if (!entries.length) return '无额外参数'
  const paramDefs = Array.isArray(cap?.parameters) ? cap.parameters : []
  return entries.map(([k, v]) => {
    const pDef = paramDefs.find((p: any) => p.name === k)
    return `${pDef?.displayName || k}=${v}`
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

onUnmounted(() => {
  chartRo?.disconnect()
  stopTelemetryTimer()
})
</script>

<style scoped>
/* 视口绝对锁屏 (对齐设备模型/实例/Adapter/数据中心) */
.security-page {
  height: calc(100vh - 50px);
  padding: 10px 14px 14px;
  background-color: var(--sl-bg-page);
  box-sizing: border-box;
  overflow: hidden;
  display: flex;
}

.constraint-workbench-canvas {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--sl-bg-surface);
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-lg);
  box-shadow: var(--sl-shadow-container);
  overflow: hidden;
  min-height: 0;
  height: 100%;
}

/* 工作台头部工具栏 (对齐 workbench-header) */
.workbench-header {
  padding: 10px 16px;
  background: #ffffff;
  border-bottom: 1px solid var(--sl-border-base);
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
  gap: 12px;
  flex-wrap: wrap;
}
.header-left { display: flex; flex-direction: column; gap: 2px; }
.header-title { margin: 0; font-size: 15px; font-weight: 700; color: var(--sl-text-heading); }
.header-subtitle { font-size: 11.5px; color: var(--sl-text-secondary); }
.header-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; flex-wrap: wrap; }
.search-input { width: 240px; }

/* 来源过滤分段控件 */
.filter-group {
  display: flex;
  background: #ffffff;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  overflow: hidden;
  flex-shrink: 0;
}
.filter-tab-btn {
  height: 26px;
  padding: 0 12px;
  border: none;
  background: transparent;
  color: var(--sl-text-secondary);
  font-size: 12px;
  cursor: pointer;
  border-right: 1px solid var(--sl-border-base);
  transition: var(--sl-ease-smooth);
  display: inline-flex;
  align-items: center;
}
.filter-tab-btn:last-child { border-right: none; }
.filter-tab-btn:hover { color: var(--sl-text-heading); }
.filter-tab-btn.active {
  background: var(--sl-primary-light);
  color: var(--sl-primary);
  font-weight: 600;
}

/* 唯一局部滚动区 (表头吸顶) */
.table-scroll-container {
  flex: 1;
  min-height: 0;
  padding: 6px 12px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #ffffff;
}
.table-card {
  flex: 1;
  min-height: 0;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  overflow: hidden;
  background: #ffffff;
  display: flex;
  flex-direction: column;
}
.master-table { width: 100%; flex: 1; }
.master-table :deep(tr) { cursor: pointer; }
.master-table :deep(th.el-table__cell) {
  background: #f8fafc;
  color: var(--sl-text-secondary);
  font-size: 12px;
  font-weight: 600;
  padding: 8px 0;
}
.master-table :deep(td.el-table__cell) { padding: 10px 0; }

.index-num {
  font-family: var(--sl-font-mono);
  color: var(--sl-text-disabled);
  font-size: 12px;
}
.rule-name-text {
  font-size: 13px;
  font-weight: 600;
  color: var(--sl-text-heading);
}

.sentence-stream {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  line-height: 1.6;
}
.sentence-static { color: var(--sl-text-secondary); }
.sentence-chip {
  padding: 1px 6px;
  border-radius: var(--sl-radius-sm);
  background: var(--sl-bg-page);
  border: 1px solid var(--sl-border-input);
  color: var(--sl-text-body);
  font-size: 12px;
  font-weight: 500;
}
.sentence-chip.emphasis { font-weight: 600; }
.sentence-chip.action {
  background: var(--sl-danger-light);
  border-color: var(--sl-danger-border);
  color: var(--sl-danger);
}
.sentence-arrow { color: var(--sl-text-disabled); margin: 0 2px; }

.source-label,
.window-label {
  font-size: 12.5px;
  color: var(--sl-text-secondary);
}

.row-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.pager-wrap {
  flex-shrink: 0;
  padding: 6px 12px;
  border-top: 1px solid var(--sl-border-base);
  display: flex;
  justify-content: flex-end;
  background: #ffffff;
}

/* 详情抽屉 (统一 80% 规范；全局 el-drawer__body 无内边距，由内容容器承载) */
.drawer-content { padding: 16px 20px; }
.drawer-custom-header h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--sl-text-heading);
}
.drawer-custom-header span {
  font-size: 11.5px;
  color: var(--sl-text-secondary);
  margin-top: 2px;
  display: block;
}

.detail-section {
  background: #ffffff;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  padding: 14px 16px;
}
.section-heading {
  font-size: 13px;
  font-weight: 700;
  color: var(--sl-text-heading);
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.meta-formula-text {
  font-size: 12px;
  color: var(--sl-text-secondary);
  margin-top: 8px;
}
.meta-formula-text code {
  color: var(--sl-primary);
  font-family: var(--sl-font-mono);
}

.live-status-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  line-height: 1;
  padding: 3px 9px;
  border-radius: 12px;
  font-weight: 500;
}
.live-status-pill.normal {
  color: var(--sl-success);
  background: var(--sl-success-light);
  border: 1px solid var(--sl-success-border);
}
.live-status-pill.violated {
  color: var(--sl-danger);
  background: var(--sl-danger-light);
  border: 1px solid var(--sl-danger-border);
}
.live-status-pill.disabled {
  color: var(--sl-text-secondary);
  background: var(--sl-bg-page);
  border: 1px solid var(--sl-border-base);
}
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.status-dot.normal { background: var(--sl-success); }
.status-dot.violated { background: var(--sl-danger); }
.status-dot.disabled { background: var(--sl-text-disabled); }

/* 实时监测指标横幅 (对齐 35px Metric Ribbon 规范) */
.metric-ribbon {
  display: grid;
  grid-template-columns: 1.5fr 1fr 1fr 1fr;
  gap: 16px;
  background: #ffffff;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  padding: 8px 16px;
  align-items: center;
}
.metric-block {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
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
  font-size: 11px;
  color: var(--sl-text-secondary);
  line-height: 1.2;
}
.m-val {
  font-size: 13px;
  font-weight: 600;
  color: var(--sl-text-heading);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.3;
}
.m-val.live-val {
  font-size: 16px;
  color: var(--sl-primary);
  font-family: var(--sl-font-mono);
}
.m-val.threshold-val {
  font-size: 16px;
  color: var(--sl-danger);
  font-family: var(--sl-font-mono);
}

.chart-wrapper {
  position: relative;
  width: 100%;
  background: #ffffff;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  padding: 8px 8px 2px;
  box-sizing: border-box;
}
.chart-svg {
  display: block;
  width: 100%;
  height: 200px;
  cursor: crosshair;
}
.chart-grid-line { stroke: var(--sl-border-subtle); stroke-width: 1; }
.chart-axis-line { stroke: var(--sl-border-base); stroke-width: 1; }
.chart-axis-label {
  fill: var(--sl-text-disabled);
  font-size: 12px;
  font-family: var(--sl-font-mono);
  font-weight: 400;
  text-rendering: geometricPrecision;
}
.chart-violation-region { fill: rgba(220, 38, 38, 0.05); }
.chart-threshold-line { stroke: var(--sl-danger); stroke-width: 1; stroke-dasharray: 5 4; }
.chart-threshold-label {
  fill: var(--sl-danger);
  font-size: 12px;
  font-weight: 600;
  font-family: var(--sl-font-mono);
  text-rendering: geometricPrecision;
}
.chart-ts-line {
  fill: none;
  stroke: var(--sl-primary);
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.chart-crosshair { stroke: var(--sl-text-disabled); stroke-width: 1; stroke-dasharray: 3 3; }
.chart-hover-dot { fill: var(--sl-primary); stroke: #ffffff; stroke-width: 1.5; }
.chart-hover-dot.violated { fill: var(--sl-danger); }
.chart-latest-halo { fill: none; stroke: var(--sl-primary); stroke-opacity: 0.35; }
.chart-latest-dot { fill: var(--sl-primary); stroke: #ffffff; stroke-width: 1.5; }
.chart-latest-label {
  fill: var(--sl-primary);
  font-size: 12px;
  font-weight: 700;
  font-family: var(--sl-font-mono);
  text-rendering: geometricPrecision;
}
.chart-tooltip {
  position: absolute;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 9px;
  background: #0f172a;
  border-radius: var(--sl-radius-sm);
  pointer-events: none;
  box-shadow: 0 4px 10px rgba(15, 23, 42, 0.18);
  white-space: nowrap;
}
.chart-tooltip .tip-time {
  font-size: 11px;
  color: #94a3b8;
  font-family: var(--sl-font-mono);
}
.chart-tooltip .tip-value {
  font-size: 12px;
  font-weight: 700;
  color: #ffffff;
  font-family: var(--sl-font-mono);
}
.chart-tooltip .tip-value.violated { color: #fca5a5; }
.chart-empty-state {
  font-size: 12.5px;
  color: var(--sl-text-disabled);
  text-align: center;
  padding: 30px 0;
}

.live-trigger-bar {
  margin-top: 14px;
  padding: 10px 14px;
  background: var(--sl-bg-hover);
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
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
.trigger-label { color: var(--sl-text-secondary); }
.trigger-expr {
  color: var(--sl-primary);
  background: var(--sl-primary-light);
  border: 1px solid var(--sl-primary-border);
  padding: 2px 8px;
  border-radius: var(--sl-radius-sm);
  font-family: var(--sl-font-mono);
  font-size: 12.5px;
}
.trigger-window { color: var(--sl-text-secondary); font-size: 12px; }
.meta-desc-text {
  font-size: 12px;
  color: var(--sl-text-secondary);
  margin-top: 6px;
}

.datacenter-link {
  color: var(--sl-primary);
  text-decoration: none;
  font-weight: 500;
}
.datacenter-link:hover { text-decoration: underline; }

.grid-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
}
.grid-table th,
.grid-table td {
  padding: 8px 10px;
  border: 1px solid var(--sl-border-base);
  text-align: left;
  color: var(--sl-text-body);
}
.grid-table th {
  background: #f8fafc;
  color: var(--sl-text-secondary);
  font-weight: 600;
}

.empty-log-hint {
  text-align: center;
  font-size: 12.5px;
  color: var(--sl-text-disabled);
  padding: 24px 0;
}

/* 新建/编辑规则弹窗固定视口与内部滚动优化 */
:deep(.constraint-editor-dialog) {
  display: flex;
  flex-direction: column;
  max-height: 90vh;
  margin-top: 5vh !important;
  border-radius: var(--sl-radius-md);
  overflow: hidden;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
}

:deep(.constraint-editor-dialog .el-dialog__header) {
  padding: 14px 20px;
  margin-right: 0;
  border-bottom: 1px solid var(--sl-border-base);
  background: #ffffff;
  flex-shrink: 0;
}

:deep(.constraint-editor-dialog .el-dialog__title) {
  font-size: 15px;
  font-weight: 700;
  color: var(--sl-text-heading);
}

:deep(.constraint-editor-dialog .el-dialog__body) {
  padding: 0;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.editor-scroll-container {
  padding: 16px 20px;
  max-height: calc(90vh - 128px);
  overflow-y: auto;
  overflow-x: hidden;
  box-sizing: border-box;
}

/* 统一精致细滚动条 (6px) */
.editor-scroll-container::-webkit-scrollbar {
  width: 6px;
}
.editor-scroll-container::-webkit-scrollbar-track {
  background: var(--sl-bg-hover);
}
.editor-scroll-container::-webkit-scrollbar-thumb {
  background: var(--sl-border-input);
  border-radius: 3px;
}
.editor-scroll-container::-webkit-scrollbar-thumb:hover {
  background: var(--sl-text-disabled);
}

:deep(.constraint-editor-dialog .el-dialog__footer) {
  padding: 10px 16px;
  border-top: 1px solid var(--sl-border-base);
  background: #ffffff;
  flex-shrink: 0;
}

.dialog-custom-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>


