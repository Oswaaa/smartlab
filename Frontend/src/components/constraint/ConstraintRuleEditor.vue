<template>
  <div class="constraint-editor">
    <!-- 顶部实时业务逻辑解析预览 -->
    <div class="preview-banner">
      <div class="preview-banner-title">判定逻辑预览</div>

      <div class="preview-sentence">
        <span class="preview-static">当</span>
        <span class="preview-chip emphasis">{{ previewData.conditionExpr }}</span>
        <span class="preview-static">持续</span>
        <span class="preview-chip">{{ previewData.windowText }}</span>
        <span class="preview-arrow">➔</span>
        <span class="preview-static">执行</span>
        <span v-for="(act, idx) in previewData.actionLabels" :key="idx" class="preview-chip action">
          {{ act }}
        </span>
      </div>
    </div>


    <!-- 1. 规则基本信息 -->
    <div class="editor-section">
      <el-row :gutter="16">
        <el-col :span="18">
          <el-form-item label="规则名称" required>
            <el-input v-model="form.ruleName" placeholder="例如：反应釜超温防爆保护" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="启用状态">
            <el-switch v-model="form.isEnabled" active-text="启用" inactive-text="停用" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="规则说明" style="margin-bottom: 0;">
        <el-input v-model="form.description" type="textarea" :rows="2" placeholder="简要说明该约束规则的工艺或安全防护目的（选填）" />
      </el-form-item>
    </div>

    <!-- 2. 违规判定公式与工具栏 (对齐功能节点 Expression Editor 体系) -->
    <div class="editor-section">
      <div class="section-title">
        <span>违规判定公式</span>
        <span class="section-hint">公式中使用 @变量 声明占位标识符，下方将自动生成对应的数据绑定槽位</span>
      </div>

      <!-- 工具栏 -->
      <div class="expression-toolbar">
        <div class="toolbar-row">
          <span class="toolbar-label">通用模板</span>
          <button v-for="tpl in universalTemplates" :key="tpl.name" type="button" class="tool-btn tpl" @click="applyTemplate(tpl.expression)">
            {{ tpl.name }}
          </button>
        </div>

        <div class="toolbar-row">
          <span class="toolbar-label">时序函数</span>
          <el-tooltip v-for="fn in temporalFunctions" :key="fn.key" placement="top" :show-after="200">
            <template #content>
              <div style="max-width:260px; line-height:1.5;">
                <strong>{{ fn.name }}</strong><br>
                <span style="font-size:12px; color:#cbd5e1;">{{ fn.description }}</span><br>
                <code style="font-size:11.5px; color:#38bdf8;">{{ fn.example }}</code>
              </div>
            </template>
            <button type="button" class="tool-btn fn" @click="insertToken(fn.insert)">{{ fn.name }}</button>
          </el-tooltip>
        </div>

        <div class="toolbar-row">
          <span class="toolbar-label">操作符</span>
          <button v-for="op in operatorTokens" :key="op" type="button" class="tool-btn op" @click="insertToken(op)">
            {{ op.trim() }}
          </button>
        </div>
      </div>

      <!-- 表达式输入框 -->
      <el-input
        ref="formulaInputRef"
        v-model="form.displayExpression"
        type="textarea"
        :rows="2"
        class="formula-input"
        placeholder="例如：@observedValue > @threshold || delta(@observedValue, 10) > 15.0"
        spellcheck="false"
        clearable
      />

      <div class="formula-meta-bar">
        <span>后端执行表达式预览：<code>{{ backendExpression || '等待输入' }}</code></span>
        <span v-if="syntaxError" class="syntax-error">● {{ syntaxError }}</span>
        <span v-else-if="backendExpression" class="syntax-valid">● 表达式语法校验通过</span>
      </div>
    </div>

    <!-- 3. 变量标识符绑定区 (自动提取 @变量) -->
    <div class="editor-section">
      <div class="section-title">
        <span>变量标识符绑定 (Bindings)</span>
        <span class="section-hint">根据表达式中的 @ 标识符自动生成，逐一绑定实际可观测数据源或固定常量</span>
      </div>

      <el-empty v-if="!form.bindings.length" description="请选择上方公式模板或在表达式中输入 @变量 标识符" :image-size="44" />

      <div v-for="item in form.bindings" :key="item.name" class="binding-card">
        <div class="binding-card-head">
          <div class="identifier-pill">
            <code>@{{ item.name }}</code>
            <span class="identifier-type-desc">{{ item.bindingType === 'OBSERVABLE' ? '观测数据源' : '固定常量值' }}</span>
          </div>
          <el-radio-group v-model="item.bindingType" size="small" @change="onBindingTypeChange(item)">
            <el-radio-button value="OBSERVABLE">观测数据</el-radio-button>
            <el-radio-button value="LITERAL">固定值</el-radio-button>
          </el-radio-group>
        </div>

        <!-- 固定值配置 -->
        <div v-if="item.bindingType === 'LITERAL'" class="binding-inputs-row">
          <el-form-item label="数据类型" style="width: 180px; margin-bottom: 0;">
            <el-select v-model="item.dataType" size="small">
              <el-option v-for="t in scalarTypes" :key="t" :label="dataTypeLabel(t)" :value="t" />
            </el-select>
          </el-form-item>
          <el-form-item label="固定值" style="flex: 1; margin-bottom: 0;">
            <el-select v-if="item.dataType === 'BOOLEAN'" v-model="item.value" size="small" style="width: 120px;">
              <el-option label="true (真)" :value="true" />
              <el-option label="false (假)" :value="false" />
            </el-select>
            <el-input-number v-else-if="isNumeric(item.dataType)" v-model="item.value" size="small" :controls="false" placeholder="请输入数值" style="width: 100%;" />
            <el-input v-else v-model="item.value" size="small" placeholder="请输入固定字符或常量" />
          </el-form-item>
        </div>

        <!-- 观测数据源配置 -->
        <div v-else class="binding-observable-grid">
          <el-form-item label="观测来源" style="margin-bottom: 0;">
            <el-select v-model="item.sourceType" size="small" @change="resetSource(item)">
              <el-option v-for="type in availableSourceTypes" :key="type" :label="sourceLabel(type)" :value="type" />
            </el-select>
          </el-form-item>

          <!-- 设备相关来源 (DEVICE_ATTRIBUTE / DEVICE_OPERATION_STATE / DEVICE_COMMAND_LIFECYCLE) -->
          <template v-if="isDeviceSource(item.sourceType)">
            <template v-if="taskMode">
              <el-form-item label="任务设备" style="margin-bottom: 0;">
                <el-select v-model="item.resourceKey" size="small" @change="selectTaskResource(item)">
                  <el-option v-for="res in taskResources" :key="res.bindingKey" :label="resourceLabel(res)" :value="res.bindingKey" />
                </el-select>
              </el-form-item>
            </template>
            <template v-else>
              <el-form-item label="设备模型" style="margin-bottom: 0;">
                <el-select v-model="item.deviceModelId" size="small" filterable placeholder="选择设备模型" @change="resetDevice(item)">
                  <el-option v-for="m in models" :key="m.id" :label="m.modelName" :value="m.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="设备实例" style="margin-bottom: 0;">
                <el-select v-model="item.deviceInstanceId" size="small" clearable placeholder="全模型实例">
                  <el-option v-for="inst in instancesFor(item.deviceModelId)" :key="inst.id" :label="inst.instanceName || ('实例' + inst.id)" :value="inst.id" />
                </el-select>
              </el-form-item>
            </template>

            <!-- 属性名 -->
            <el-form-item v-if="item.sourceType === 'DEVICE_ATTRIBUTE'" label="监测属性" style="margin-bottom: 0;">
              <el-select v-model="item.targetName" size="small" filterable placeholder="选择监测物理属性">
                <el-option v-for="attr in attributesFor(item.deviceModelId)" :key="attr.attributeName" :label="attr.displayName || attr.attributeName" :value="attr.attributeName" />
              </el-select>
            </el-form-item>

            <!-- OP 状态空间 -->
            <el-form-item v-if="item.sourceType === 'DEVICE_OPERATION_STATE'" label="状态分区" style="margin-bottom: 0;">
              <el-select v-model="item.regionName" size="small" placeholder="选择OP状态分区">
                <el-option v-for="region in operationRegions(item.deviceModelId)" :key="region.regionName" :label="region.regionName" :value="region.regionName" />
              </el-select>
            </el-form-item>
          </template>

          <!-- 任务状态 -->
          <template v-else-if="item.sourceType === 'TASK_LIFECYCLE_STATE'">
            <div v-if="taskMode" class="task-mode-hint">自动绑定当前执行任务的状态</div>
            <el-form-item v-else label="目标任务" style="margin-bottom: 0;">
              <el-select v-model="item.taskId" size="small" filterable placeholder="选择目标任务">
                <el-option v-for="task in tasks" :key="task.id" :label="(task.taskName || '任务') + ' #' + task.id" :value="task.id" />
              </el-select>
            </el-form-item>
          </template>

          <!-- 节点相关来源 (仅任务模式可用) -->
          <template v-else-if="isNodeSource(item.sourceType)">
            <el-form-item label="工作流" style="margin-bottom: 0;">
              <el-select v-model="item.workflowTemplateId" size="small" @change="item.nodeName='';item.variableName=''">
                <el-option v-for="flow in availableWorkflows" :key="flow.id" :label="flow.flowName" :value="flow.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="节点" style="margin-bottom: 0;">
              <el-select v-model="item.nodeName" size="small" filterable @change="item.variableName=''">
                <el-option v-for="node in nodesFor(item.workflowTemplateId)" :key="node.name" :label="node.name" :value="node.name" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="item.sourceType === 'NODE_INTERNAL_VARIABLE'" label="内部变量" style="margin-bottom: 0;">
              <el-select v-model="item.variableName" size="small" filterable>
                <el-option v-for="v in internalVariablesFor(item)" :key="v.name" :label="v.name + ' (' + v.dataType + ')'" :value="v.name" />
              </el-select>
            </el-form-item>
          </template>

          <div class="resolved-type-tag">
            <span>数据类型:</span>
            <strong>{{ dataTypeLabel(observableDataType(item)) }}</strong>
          </div>
        </div>
      </div>
    </div>

    <!-- 4. 判定持续时间与违规处置动作 -->
    <div class="editor-section">
      <div class="section-title">
        <span>判定窗口与处置动作</span>
        <span class="section-hint">配置连续触发时间及判定成立后的处置动作组合</span>
      </div>

      <el-form-item label="持续判定时间">
        <div style="display: flex; align-items: center; gap: 8px;">
          <el-input-number v-model="form.windowSeconds" size="small" :controls="false" :min="1" placeholder="留空为瞬时触发" style="width: 140px;" />
          <span style="font-size: 12.5px; color: #64748b;">秒（公式连续成立达到该时间后判定违规，为空表示瞬时触发）</span>
        </div>
      </el-form-item>

      <div style="margin-top: 14px;">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
          <span style="font-size: 13px; font-weight: 600; color: #1e293b;">违规处置动作列表</span>
          <el-button size="small" class="btn-aliyun" @click="addAction">+ 添加处置动作</el-button>
        </div>

        <div v-for="(act, index) in form.actions" :key="act.key" class="action-card">
          <div class="action-card-head">
            <span style="font-weight: 600; font-size: 13px;">动作 {{ index + 1 }}</span>
            <div style="display: flex; align-items: center; gap: 12px;">
              <el-radio-group v-model="act.actionType" size="small" @change="resetAction(act)">
                <el-radio-button value="DEVICE_CAPABILITY">设备能力</el-radio-button>
                <el-radio-button value="SYSTEM">系统动作</el-radio-button>
              </el-radio-group>
              <el-button v-if="canRemoveAction" link class="btn-aliyun-danger-link" size="small" @click="form.actions.splice(index, 1)">删除</el-button>
            </div>
          </div>


          <!-- 设备能力动作 -->
          <div v-if="act.actionType === 'DEVICE_CAPABILITY'" class="action-inputs-grid">
            <template v-if="taskMode">
              <el-form-item label="任务设备" style="margin-bottom: 0;">
                <el-select v-model="act.resourceKey" size="small" @change="selectActionResource(act)">
                  <el-option v-for="res in taskResources" :key="res.bindingKey" :label="resourceLabel(res)" :value="res.bindingKey" />
                </el-select>
              </el-form-item>
            </template>
            <template v-else>
              <el-form-item label="设备模型" style="margin-bottom: 0;">
                <el-select v-model="act.deviceModelId" size="small" filterable @change="resetActionDevice(act)">
                  <el-option v-for="m in models" :key="m.id" :label="m.modelName" :value="m.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="设备实例" style="margin-bottom: 0;">
                <el-select v-model="act.deviceInstanceId" size="small" placeholder="选择执行实例">
                  <el-option v-for="inst in instancesFor(act.deviceModelId)" :key="inst.id" :label="inst.instanceName || ('实例' + inst.id)" :value="inst.id" />
                </el-select>
              </el-form-item>
            </template>

            <el-form-item label="执行能力" style="margin-bottom: 0;">
              <el-select v-model="act.capabilityName" size="small" filterable @change="initParameters(act)">
                <el-option v-for="cap in capabilitiesFor(act.deviceModelId)" :key="cap.capabilityName" :label="cap.displayName || cap.capabilityName" :value="cap.capabilityName" />
              </el-select>
            </el-form-item>

            <!-- 动态参数列表 -->
            <template v-if="parameterDefinitions(act).length">
              <el-form-item v-for="param in parameterDefinitions(act)" :key="param.name" :label="param.name" style="margin-bottom: 0;">
                <el-select v-if="param.dataType === 'BOOLEAN'" v-model="act.parameters[param.name]" size="small">
                  <el-option label="true" :value="true" />
                  <el-option label="false" :value="false" />
                </el-select>
                <el-input-number v-else-if="isNumeric(param.dataType)" v-model="act.parameters[param.name]" size="small" :controls="false" />
                <el-input v-else v-model="act.parameters[param.name]" size="small" />
              </el-form-item>
            </template>
          </div>

          <!-- 系统动作 -->
          <div v-else class="action-inputs-grid" style="grid-template-columns: 180px 1fr;">
            <el-form-item label="系统处置" style="margin-bottom: 0;">
              <el-select v-model="act.action" size="small">
                <el-option label="终止当前任务 (ABORT)" value="ABORT" />
                <el-option label="暂停当前任务 (PAUSE)" value="PAUSE" />
                <el-option label="发布系统告警 (ALERT)" value="ALERT" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="!taskMode && act.action !== 'ALERT'" label="目标任务" style="margin-bottom: 0;">
              <el-select v-model="act.targetTaskId" size="small" filterable placeholder="指定触发违规的目标任务">
                <el-option v-for="task in tasks" :key="task.id" :label="(task.taskName || '任务') + ' #' + task.id" :value="task.id" />
              </el-select>
            </el-form-item>
            <div v-else class="task-mode-hint">系统动作将自动作用于触发违规的当前任务</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  extractExpressionVariables,
  toBackendExpression,
  validateDisplayExpressionSyntax,
  sourceCategoryLabel
} from '../../utils/constraintExpression.js'

const props = withDefaults(
  defineProps<{
    models: any[]
    instances: any[]
    workflows: any[]
    tasks: any[]
    taskMode?: boolean
    taskResources?: any[]
    taskWorkflowNodes?: any[]
    sourceTypes?: string[]
    systemActions?: string[]
  }>(),
  {
    taskMode: false,
    taskResources: () => [],
    taskWorkflowNodes: () => [],
    sourceTypes: () => ['DEVICE_ATTRIBUTE', 'DEVICE_OPERATION_STATE', 'DEVICE_COMMAND_LIFECYCLE', 'TASK_LIFECYCLE_STATE'],
    systemActions: () => ['ABORT', 'PAUSE', 'ALERT']
  }
)

let serial = 0
const formulaInputRef = ref<any>()
const syntaxError = ref('')

const universalTemplates = [
  { name: '单向阈值比较', expression: '@observedValue > @threshold' },
  { name: '允许区间越界', expression: '@value < @minimum || @value > @maximum' },
  { name: '时序突变量', expression: 'delta(@observedValue, 10) > @threshold' },
  { name: '滑动均值', expression: 'avg(@observedValue, 60) > @threshold' },
  { name: '不等判断', expression: '@actual != @expected' },
  { name: '布尔条件', expression: '@flag == true' }
]

const temporalFunctions = [
  { name: 'delta(var, s)', insert: 'delta(@var, 10)', description: 'N秒时间窗口内的变化量', example: 'delta(@temp, 10) > 5' },
  { name: 'avg(var, s)', insert: 'avg(@var, 60)', description: 'N秒时间窗口内的滚动均值', example: 'avg(@temp, 60) > 80' },
  { name: 'rate(var)', insert: 'rate(@var)', description: '每秒变化速率（单位/秒）', example: 'rate(@temp) > 2' }
]

const operatorTokens = [' > ', ' < ', ' >= ', ' <= ', ' == ', ' != ', ' && ', ' || ', '!', '+', '-', '*', '/', '(', ')']
const scalarTypes = ['DOUBLE', 'INTEGER', 'STRING', 'BOOLEAN']

const newBinding = (name: string) => ({
  name,
  bindingType: /threshold|minimum|maximum|expected|limit/i.test(name) ? 'LITERAL' : 'OBSERVABLE',
  dataType: 'DOUBLE',
  value: 0,
  sourceType: 'DEVICE_ATTRIBUTE',
  resourceKey: '',
  deviceModelId: null as number | null,
  deviceInstanceId: null as number | null,
  targetName: '',
  regionName: '',
  workflowTemplateId: null as number | null,
  nodeName: '',
  variableName: '',
  taskId: null as number | null
})

const newAction = () => ({
  key: ++serial,
  actionType: 'DEVICE_CAPABILITY',
  action: 'ALERT',
  targetTaskId: null as number | null,
  resourceKey: '',
  deviceModelId: null as number | null,
  deviceInstanceId: null as number | null,
  capabilityName: '',
  parameters: {} as Record<string, any>
})

const form = reactive<any>({
  ruleName: '',
  description: '',
  isEnabled: true,
  displayExpression: '@observedValue > @threshold',
  bindings: [],
  actions: [newAction()],
  windowSeconds: null
})

const backendExpression = computed(() => toBackendExpression(form.displayExpression))
const canRemoveAction = computed(() => form.actions.length > 1)

const availableSourceTypes = computed(() => {
  if (props.taskMode) {
    return ['DEVICE_ATTRIBUTE', 'DEVICE_OPERATION_STATE', 'DEVICE_COMMAND_LIFECYCLE', 'NODE_LIFECYCLE_STATE', 'NODE_INTERNAL_VARIABLE', 'TASK_LIFECYCLE_STATE']
  }
  return ['DEVICE_ATTRIBUTE', 'DEVICE_OPERATION_STATE', 'DEVICE_COMMAND_LIFECYCLE', 'TASK_LIFECYCLE_STATE']
})

const availableWorkflows = computed(() =>
  props.taskMode
    ? [...new Map(props.taskWorkflowNodes.map((x: any) => [Number(x.flowModelId), { id: Number(x.flowModelId), flowName: x.flowName || ('流程' + x.flowModelId) }])).values()]
    : props.workflows
)

watch(
  () => form.displayExpression,
  val => {
    try {
      validateDisplayExpressionSyntax(val)
      syntaxError.value = ''
    } catch (e: any) {
      syntaxError.value = e.message
    }
    syncBindings()
  },
  { immediate: true }
)

function variableNames() {
  return extractExpressionVariables(form.displayExpression)
}

function syncBindings() {
  const existing = new Map(form.bindings.map((x: any) => [x.name, x]))
  form.bindings = variableNames().map(name => existing.get(name) || newBinding(name))
}

function applyTemplate(expr: string) {
  form.displayExpression = expr
}

function insertToken(token: string) {
  form.displayExpression = (form.displayExpression || '') + token
}

function onBindingTypeChange(item: any) {
  if (item.bindingType === 'LITERAL') {
    item.dataType = 'DOUBLE'
    item.value = 0
  } else {
    item.sourceType = 'DEVICE_ATTRIBUTE'
  }
}

const isNumeric = (t: string) => t === 'INTEGER' || t === 'DOUBLE'
const isDeviceSource = (t: string) => ['DEVICE_ATTRIBUTE', 'DEVICE_OPERATION_STATE', 'DEVICE_COMMAND_LIFECYCLE'].includes(t)
const isNodeSource = (t: string) => ['NODE_LIFECYCLE_STATE', 'NODE_INTERNAL_VARIABLE'].includes(t)

const sourceLabel = (t: string) => sourceCategoryLabel(t)
const dataTypeLabel = (t: string) => ({ DOUBLE: '浮点数', INTEGER: '整数', STRING: '字符串', BOOLEAN: '布尔值', JSON: 'JSON对象' } as any)[t] || t || '-'

const model = (id: any) => props.models.find((x: any) => Number(x.id || x.modelId) === Number(id))
const instancesFor = (id: any) => props.instances.filter((x: any) => Number(x.deviceModelId || x.modelId) === Number(id))
const attributesFor = (id: any) => (Array.isArray(model(id)?.attributes) ? model(id).attributes : [])
const capabilitiesFor = (id: any) => (Array.isArray(model(id)?.capabilities) ? model(id).capabilities : [])
const operationRegions = (id: any) => (Array.isArray(model(id)?.opState?.regions) ? model(id).opState.regions : [])

function internalVariablesFor(item: any) {
  return nodesFor(item.workflowTemplateId).find((node: any) => node.name === item.nodeName)?.internalVariables || []
}

function observableDataType(item: any) {
  if (item.sourceType === 'DEVICE_ATTRIBUTE') {
    return attributesFor(item.deviceModelId).find((x: any) => x.attributeName === item.targetName)?.dataType || 'DOUBLE'
  }
  if (item.sourceType === 'DEVICE_OPERATION_STATE') return 'JSON'
  if (item.sourceType === 'DEVICE_COMMAND_LIFECYCLE' || item.sourceType === 'TASK_LIFECYCLE_STATE' || item.sourceType === 'NODE_LIFECYCLE_STATE') return 'STRING'
  if (item.sourceType === 'NODE_INTERNAL_VARIABLE') {
    return internalVariablesFor(item).find((v: any) => v.name === item.variableName)?.dataType || 'STRING'
  }
  return 'STRING'
}

function resetSource(item: any) {
  Object.assign(item, {
    resourceKey: '',
    deviceModelId: props.models.length === 1 ? props.models[0].id : null,
    deviceInstanceId: null,
    targetName: '',
    regionName: '',
    workflowTemplateId: props.taskMode && availableWorkflows.value.length === 1 ? availableWorkflows.value[0].id : null,
    nodeName: '',
    variableName: '',
    taskId: null
  })
}

function resetDevice(item: any) {
  Object.assign(item, { deviceInstanceId: null, targetName: '', regionName: '' })
}

function selectTaskResource(item: any) {
  const resource = props.taskResources.find((x: any) => x.bindingKey === item.resourceKey)
  if (resource) Object.assign(item, { deviceModelId: resource.deviceModelId, deviceInstanceId: resource.deviceInstanceId, targetName: '', regionName: '' })
}

function resourceLabel(resource: any) {
  return `${resource.flowName || resource.flowModelId} / ${resource.nodeName} → ${resource.instanceName || '实例' + (resource.deviceInstanceId || '待绑定')}`
}

function nodesFor(flowId: any) {
  const nodes = props.taskMode
    ? props.taskWorkflowNodes.filter((x: any) => Number(x.flowModelId) === Number(flowId))
    : props.workflows.find((x: any) => Number(x.id) === Number(flowId))?.nodesDef || []
  return [...new Map(nodes.map((node: any) => [node.name, node])).values()]
}

function addAction() {
  form.actions.push(newAction())
}

function resetAction(act: any) {
  Object.assign(act, {
    action: 'ALERT',
    targetTaskId: null,
    resourceKey: '',
    deviceModelId: null,
    deviceInstanceId: null,
    capabilityName: '',
    parameters: {}
  })
}

function resetActionDevice(act: any) {
  Object.assign(act, { deviceInstanceId: null, capabilityName: '', parameters: {} })
}

function selectActionResource(act: any) {
  const resource = props.taskResources.find((x: any) => x.bindingKey === act.resourceKey)
  if (resource) Object.assign(act, { deviceModelId: resource.deviceModelId, deviceInstanceId: resource.deviceInstanceId, capabilityName: '', parameters: {} })
}

function parameterDefinitions(act: any) {
  const cap = capabilitiesFor(act.deviceModelId).find((x: any) => x.capabilityName === act.capabilityName)
  const raw = cap?.capabilityParameters || cap?.parameters
  if (Array.isArray(raw)) return raw.map((x: any) => ({ name: x.parameterName || x.name, dataType: x.dataType || 'STRING' }))
  if (raw && typeof raw === 'object') return Object.entries(raw).map(([name, val]: any) => ({ name, dataType: val?.dataType || 'STRING' }))
  return []
}

function initParameters(act: any) {
  act.parameters = {}
  parameterDefinitions(act).forEach((param: any) => {
    act.parameters[param.name] = param.dataType === 'BOOLEAN' ? false : ''
  })
}

/* 顶部动态自然语言预览计算 */
const previewData = computed(() => {
  let conditionExpr = form.displayExpression || '等待配置判定公式'
  const varMap: Record<string, string> = {}

  for (const b of form.bindings) {
    if (b.bindingType === 'OBSERVABLE') {
      const inst = props.instances.find(i => Number(i.id) === Number(b.deviceInstanceId))
      const mdl = props.models.find(m => Number(m.id || m.modelId) === Number(b.deviceModelId))
      const prefix = inst?.instanceName || mdl?.modelName || (b.deviceInstanceId ? `实例${b.deviceInstanceId}` : '设备')
      if (b.sourceType === 'DEVICE_ATTRIBUTE') {
        const attr = (mdl?.attributes || []).find((a: any) => a.attributeName === b.targetName)
        varMap[b.name] = `${prefix}.${attr?.displayName || b.targetName || '属性'}`
      } else if (b.sourceType === 'DEVICE_OPERATION_STATE') {
        varMap[b.name] = `${prefix}.${b.regionName || 'OP'}状态`
      } else if (b.sourceType === 'DEVICE_COMMAND_LIFECYCLE') {
        varMap[b.name] = `${prefix}.CMD状态`
      } else if (b.sourceType === 'TASK_LIFECYCLE_STATE') {
        varMap[b.name] = '任务实例状态'
      } else if (b.sourceType === 'NODE_LIFECYCLE_STATE') {
        varMap[b.name] = `${b.nodeName || '节点'}.生命周期`
      } else if (b.sourceType === 'NODE_INTERNAL_VARIABLE') {
        varMap[b.name] = `${b.nodeName || '节点'}.${b.variableName || '变量'}`
      }
    } else if (b.bindingType === 'LITERAL') {
      varMap[b.name] = b.value !== null && b.value !== undefined && b.value !== '' ? String(b.value) : `[未输入常量]`
    }
  }

  for (const [name, val] of Object.entries(varMap).sort((a, b) => b[0].length - a[0].length)) {
    conditionExpr = conditionExpr
      .replace(new RegExp(`@${name}\\b`, 'g'), val)
      .replace(new RegExp(`\\b${name}\\b`, 'g'), val)
  }

  const actionLabels = form.actions.map((act: any) => {
    if (act.actionType === 'SYSTEM') {
      const map: any = { ABORT: '终止当前任务', PAUSE: '暂停当前任务', ALERT: '发布系统告警' }
      return map[act.action] || act.action || '系统处置'
    }
    if (act.actionType === 'DEVICE_CAPABILITY') {
      const inst = props.instances.find(i => Number(i.id) === Number(act.deviceInstanceId))
      const mdl = props.models.find(m => Number(m.id || m.modelId) === Number(act.deviceModelId))
      const cap = (mdl?.capabilities || []).find((c: any) => c.capabilityName === act.capabilityName)
      const devName = inst?.instanceName || (act.deviceInstanceId ? `设备${act.deviceInstanceId}` : '设备')
      return `${devName}.${cap?.displayName || act.capabilityName || '能力'}`
    }
    return '处置动作'
  })

  return {
    conditionExpr,
    windowText: form.windowSeconds && form.windowSeconds > 0 ? `持续 ${form.windowSeconds} 秒` : '瞬时判定',
    actionLabels: actionLabels.length ? actionLabels : ['发布系统告警']
  }
})


function bindingPayload(item: any) {
  if (item.bindingType === 'LITERAL') {
    if (item.value === null || item.value === undefined || item.value === '') {
      throw Error(`@${item.name} 缺少固定常量值`)
    }
    return { bindingType: 'LITERAL', value: item.value }
  }

  const resolvedDataType = observableDataType(item)
  if (!scalarTypes.includes(resolvedDataType) && resolvedDataType !== 'JSON') {
    throw Error(`@${item.name} 缺少合法的数据类型`)
  }

  const source: any = { sourceType: item.sourceType, dataType: resolvedDataType }
  if (isDeviceSource(item.sourceType)) {
    if (!item.deviceModelId) throw Error(`@${item.name} 请选择设备模型`)
    if (props.taskMode && !item.deviceInstanceId) throw Error(`@${item.name} 请选择任务关联设备`)
    source.deviceModelId = item.deviceModelId
    if (item.deviceInstanceId) source.deviceInstanceId = item.deviceInstanceId
    if (item.sourceType === 'DEVICE_ATTRIBUTE') {
      if (!item.targetName) throw Error(`@${item.name} 请选择监测属性`)
      source.targetName = item.targetName
    }
    if (item.sourceType === 'DEVICE_OPERATION_STATE') {
      if (!item.regionName) throw Error(`@${item.name} 请选择OP状态空间`)
      source.regionName = item.regionName
    }
  } else if (isNodeSource(item.sourceType)) {
    if (!item.workflowTemplateId || !item.nodeName) throw Error(`@${item.name} 缺少工作流节点`)
    source.workflowTemplateId = item.workflowTemplateId
    source.nodeName = item.nodeName
    if (item.sourceType === 'NODE_INTERNAL_VARIABLE') {
      if (!item.variableName) throw Error(`@${item.name} 缺少内部变量`)
      source.variableName = item.variableName
    }
  } else if (!props.taskMode && item.sourceType === 'TASK_LIFECYCLE_STATE') {
    if (!item.taskId) throw Error(`@${item.name} 请指定目标任务`)
    source.taskId = item.taskId
  }
  return { bindingType: 'OBSERVABLE', source }
}

function actionPayload(act: any) {
  if (act.actionType === 'SYSTEM') {
    if (!props.taskMode && act.action !== 'ALERT' && !act.targetTaskId) {
      throw Error(`${act.action} 必须选择目标任务`)
    }
    return {
      actionType: 'SYSTEM',
      action: act.action,
      targetTaskId: props.taskMode ? null : act.action === 'ALERT' ? null : act.targetTaskId
    }
  }
  if (!act.deviceInstanceId || !act.capabilityName) {
    throw Error('设备能力动作必须选择具体执行实例与能力')
  }
  return {
    actionType: 'DEVICE_CAPABILITY',
    deviceInstanceId: act.deviceInstanceId,
    capabilityName: act.capabilityName,
    parameters: act.parameters || {}
  }
}

function validateAndBuild() {
  if (!form.ruleName.trim()) throw Error('请输入约束规则名称')
  validateDisplayExpressionSyntax(form.displayExpression)
  if (!backendExpression.value) throw Error('请输入违规判定表达式')
  if (!variableNames().length) throw Error('表达式中至少需要包含一个 @变量 标识符')

  const bindings: any = {}
  for (const item of form.bindings) {
    bindings[item.name] = bindingPayload(item)
  }

  if (!form.actions.length) throw Error('请至少配置一个违规处置动作')

  return {
    ruleName: form.ruleName.trim(),
    description: form.description.trim(),
    expression: backendExpression.value,
    bindings,
    windowSeconds: form.windowSeconds || null,
    violationActions: form.actions.map(actionPayload),
    isEnabled: form.isEnabled
  }
}

function displayExpression(rule: any) {
  let expr = rule?.expression || ''
  for (const name of Object.keys(rule?.bindings || {}).sort((a, b) => b.length - a.length)) {
    expr = expr.replace(new RegExp(`\\b${name}\\b`, 'g'), `@${name}`)
  }
  return expr
}

function loadRule(rule?: any) {
  Object.assign(form, {
    ruleName: rule?.ruleName || '',
    description: rule?.description || '',
    isEnabled: rule?.isEnabled !== false,
    displayExpression: rule ? displayExpression(rule) : '@observedValue > @threshold',
    bindings: [],
    actions: [newAction()],
    windowSeconds: rule?.windowSeconds ?? null
  })

  if (rule?.bindings) {
    form.bindings = Object.entries(rule.bindings).map(([name, binding]: any) => {
      const src = binding.source || {}
      const res = props.taskResources.find((x: any) => Number(x.deviceInstanceId) === Number(src.deviceInstanceId))
      return {
        ...newBinding(name),
        name,
        bindingType: binding.bindingType || 'OBSERVABLE',
        value: binding.value,
        dataType: src.dataType || (typeof binding.value === 'boolean' ? 'BOOLEAN' : 'DOUBLE'),
        sourceType: src.sourceType || 'DEVICE_ATTRIBUTE',
        resourceKey: res?.bindingKey || '',
        deviceModelId: src.deviceModelId || null,
        deviceInstanceId: src.deviceInstanceId || null,
        targetName: src.targetName || '',
        regionName: src.regionName || '',
        workflowTemplateId: src.workflowTemplateId || null,
        nodeName: src.nodeName || '',
        variableName: src.variableName || '',
        taskId: src.taskId || null
      }
    })
  }

  if (rule?.violationActions && rule.violationActions.length) {
    form.actions = rule.violationActions.map((act: any) => {
      const res = props.taskResources.find((x: any) => Number(x.deviceInstanceId) === Number(act.deviceInstanceId))
      const inst = props.instances.find((x: any) => Number(x.id) === Number(act.deviceInstanceId))
      return {
        ...newAction(),
        actionType: act.actionType || 'DEVICE_CAPABILITY',
        action: act.action || 'ALERT',
        targetTaskId: act.targetTaskId || null,
        resourceKey: res?.bindingKey || '',
        deviceModelId: res?.deviceModelId || inst?.deviceModelId || inst?.modelId || null,
        deviceInstanceId: act.deviceInstanceId || null,
        capabilityName: act.capabilityName || '',
        parameters: act.parameters || {}
      }
    })
  }

  syncBindings()
}

loadRule()
defineExpose({ validateAndBuild, loadRule })
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

.constraint-editor {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.preview-banner {
  background: #f8fafc;
  border: 1px solid #dbeafe;
  border-left: 3px solid #2563eb;
  border-radius: 4px;
  padding: 12px 16px;
}
.preview-banner-title {
  font-size: 12px;
  font-weight: 600;
  color: #2563eb;
  margin-bottom: 6px;
}
.preview-sentence {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  line-height: 1.6;
}
.preview-static {
  color: #64748b;
}
.preview-chip {
  padding: 2px 7px;
  border-radius: 3px;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #1e293b;
  font-size: 12.5px;
  font-weight: 500;
}
.preview-chip.emphasis {
  font-weight: 600;
}
.preview-chip.action {
  background: #fef2f2;
  border-color: #fecaca;
  color: #dc2626;
}
.preview-arrow {
  color: #94a3b8;
  margin: 0 2px;
}

.editor-section {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  padding: 16px;
}
.section-title {
  font-size: 13.5px;
  font-weight: 600;
  color: #0f172a;
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.section-hint {
  font-size: 12px;
  font-weight: normal;
  color: #64748b;
}

/* 表达式工具栏 */
.expression-toolbar {
  border: 1px solid #e2e8f0;
  border-bottom: none;
  border-radius: 4px 4px 0 0;
  background: #f8fafc;
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.toolbar-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.toolbar-label {
  font-size: 11.5px;
  color: #64748b;
  width: 54px;
  flex-shrink: 0;
}
.tool-btn {
  padding: 2px 8px;
  border-radius: 3px;
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #334155;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.12s ease;
}
.tool-btn:hover {
  border-color: #2563eb;
  color: #2563eb;
}
.tool-btn.fn {
  color: #2563eb;
  background: #eff6ff;
  border-color: #bfdbfe;
  font-family: ui-monospace, monospace;
}
.tool-btn.op {
  font-family: ui-monospace, monospace;
  font-weight: 600;
}

.formula-input :deep(.el-textarea__inner) {
  border-radius: 0 0 4px 4px;
  font-family: ui-monospace, monospace;
  font-size: 13px;
  color: #1e293b;
  background: #ffffff;
}

.formula-meta-bar {
  margin-top: 6px;
  font-size: 12px;
  color: #64748b;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.formula-meta-bar code {
  color: #2563eb;
}
.syntax-valid {
  color: #16a34a;
  font-size: 12px;
}
.syntax-error {
  color: #dc2626;
  font-size: 12px;
}

/* 变量卡片 */
.binding-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  padding: 12px 14px;
  margin-bottom: 10px;
}
.binding-card:last-child {
  margin-bottom: 0;
}
.binding-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.identifier-pill {
  display: flex;
  align-items: center;
  gap: 8px;
}
.identifier-pill code {
  font-family: ui-monospace, monospace;
  font-weight: 700;
  font-size: 13px;
  background: #e2e8f0;
  padding: 2px 6px;
  border-radius: 3px;
  color: #0f172a;
}
.identifier-type-desc {
  font-size: 12px;
  color: #64748b;
}

.binding-inputs-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.binding-observable-grid {
  display: grid;
  grid-template-columns: 140px 1fr 1fr 1fr auto;
  gap: 10px;
  align-items: center;
}
.resolved-type-tag {
  font-size: 12px;
  color: #64748b;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}
.resolved-type-tag strong {
  color: #0f172a;
}

/* 动作卡片 */
.action-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  padding: 12px 14px;
  margin-bottom: 10px;
}
.action-card:last-child {
  margin-bottom: 0;
}
.action-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.action-inputs-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
  align-items: center;
}

.task-mode-hint {
  font-size: 12px;
  color: #64748b;
  padding: 4px 8px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 3px;
}
</style>
