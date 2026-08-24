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
      <div class="rule-info-head">
        <el-form-item label="规则名称" required class="rule-name-item">
          <el-input v-model="form.ruleName" placeholder="例如：反应釜超温防爆保护" />
        </el-form-item>
        <div class="enable-inline">
          <span class="enable-label">启用</span>
          <el-switch v-model="form.isEnabled" />
        </div>
      </div>
      <el-form-item label="规则说明" class="rule-desc-item">
        <el-input v-model="form.description" type="textarea" :rows="2" placeholder="简要说明该约束规则的工艺或安全防护目的（选填）" />
      </el-form-item>
    </div>

    <!-- 2. 判定公式 = 变量绑定 + 表达式点选 (对齐功能节点 Expression Editor) -->
    <div class="editor-section">
      <div class="section-title">
        <span>判定公式</span>
        <span class="section-hint">先绑定观测变量与阈值，再点选变量与操作符组成判定式</span>
      </div>

      <div class="binding-toolbar">
        <span class="binding-toolbar-label">变量声明</span>
        <button class="btn-aliyun" type="button" @click="addObservable">+ 观测变量</button>
        <button class="btn-aliyun" type="button" @click="addLiteral">+ 固定阈值</button>
      </div>

      <div v-if="!form.bindings.length" class="binding-guide">
        <span class="guide-step">1</span>
        <span>点击下方公式模板，或手动添加观测变量 / 固定阈值</span>
        <span class="guide-step">2</span>
        <span>绑定数据源后，点选已声明变量组成判定式</span>
      </div>

      <div v-for="item in form.bindings" :key="item._key" class="binding-card" :class="{ unused: !isVarUsed(item.name) }">
        <div class="binding-card-head">
          <div class="identifier-pill">
            <span class="at-prefix">@</span>
            <el-input
              :key="item._key + '-' + (item._nameRev || 0)"
              :model-value="item.name"
              size="small"
              class="var-name-input"
              placeholder="变量名"
              @change="(val) => renameBinding(item, val)"
            />
            <span class="identifier-type-desc">{{ item.bindingType === 'OBSERVABLE' ? '观测数据' : '固定阈值' }}</span>
            <span v-if="!isVarUsed(item.name)" class="unused-hint">未写入公式</span>
          </div>
          <div class="binding-head-actions">
            <el-radio-group v-model="item.bindingType" size="small" @change="onBindingTypeChange(item)">
              <el-radio-button value="OBSERVABLE">观测数据</el-radio-button>
              <el-radio-button value="LITERAL">固定值</el-radio-button>
            </el-radio-group>
            <button class="btn-link danger" type="button" @click="removeBinding(item.name)">删除</button>
          </div>
        </div>

        <div v-if="item.bindingType === 'LITERAL'" class="binding-literal-row">
          <div class="field field-type">
            <span class="field-label">数据类型</span>
            <el-select v-model="item.dataType" size="small">
              <el-option v-for="t in scalarTypes" :key="t" :label="dataTypeLabel(t)" :value="t" />
            </el-select>
          </div>
          <div class="field field-value">
            <span class="field-label">固定值</span>
            <el-select v-if="item.dataType === 'BOOLEAN'" v-model="item.value" size="small">
              <el-option label="true" :value="true" />
              <el-option label="false" :value="false" />
            </el-select>
            <el-input-number v-else-if="isNumeric(item.dataType)" v-model="item.value" size="small" :controls="false" placeholder="请输入数值" />
            <el-input v-else v-model="item.value" size="small" placeholder="请输入常量" />
          </div>
        </div>

        <div v-else class="binding-observable">
          <div class="obs-row">
            <div class="field field-source">
              <span class="field-label">观测来源</span>
              <el-select v-model="item.sourceType" size="small" @change="resetSource(item)">
                <el-option v-for="type in availableSourceTypes" :key="type" :label="sourceLabel(type)" :value="type" />
              </el-select>
            </div>

            <template v-if="isDeviceSource(item.sourceType)">
              <template v-if="taskMode">
                <div class="field field-grow">
                  <span class="field-label">任务设备</span>
                  <el-select v-model="item.resourceKey" size="small" @change="selectTaskResource(item)">
                    <el-option v-for="res in taskResources" :key="res.bindingKey" :label="resourceLabel(res)" :value="res.bindingKey" />
                  </el-select>
                </div>
              </template>
              <template v-else>
                <div class="field field-grow">
                  <span class="field-label">设备模型</span>
                  <el-select v-model="item.deviceModelId" size="small" filterable placeholder="选择设备模型" @change="resetDevice(item)">
                    <el-option v-for="m in models" :key="m.id" :label="m.modelName" :value="m.id" />
                  </el-select>
                </div>
                <div class="field field-grow">
                  <span class="field-label">设备实例</span>
                  <el-select v-model="item.deviceInstanceId" size="small" clearable placeholder="全模型实例">
                    <el-option v-for="inst in instancesFor(item.deviceModelId)" :key="inst.id" :label="inst.instanceName || ('实例' + inst.id)" :value="inst.id" />
                  </el-select>
                </div>
              </template>
            </template>

            <template v-else-if="item.sourceType === 'TASK_LIFECYCLE_STATE'">
              <div v-if="taskMode" class="task-mode-hint">自动绑定当前执行任务的状态</div>
              <template v-else>
                <div class="field field-grow">
                  <span class="field-label">工作流</span>
                  <el-select v-model="item.workflowTemplateId" size="small" filterable placeholder="选择工作流模板" @change="item.taskId = null">
                    <el-option v-for="flow in availableWorkflows" :key="flow.id" :label="workflowModelName(flow) || ('流程' + flow.id)" :value="flow.id" />
                  </el-select>
                </div>
                <div class="field field-grow">
                  <span class="field-label">目标任务（可选）</span>
                  <el-select v-model="item.taskId" size="small" filterable clearable placeholder="全任务实例">
                    <el-option v-for="task in tasksFor(item.workflowTemplateId)" :key="task.id" :label="(task.taskName || '任务') + ' #' + task.id" :value="task.id" />
                  </el-select>
                </div>
              </template>
            </template>

            <template v-else-if="isNodeSource(item.sourceType)">
              <div class="field field-grow">
                <span class="field-label">工作流</span>
                <el-select v-model="item.workflowTemplateId" size="small" @change="item.nodeName='';item.variableName=''">
                  <el-option v-for="flow in availableWorkflows" :key="flow.id" :label="workflowModelName(flow)" :value="flow.id" />
                </el-select>
              </div>
              <div class="field field-grow">
                <span class="field-label">节点</span>
                <el-select v-model="item.nodeName" size="small" filterable @change="item.variableName=''">
                  <el-option v-for="node in nodesFor(item.workflowTemplateId)" :key="node.name" :label="node.name" :value="node.name" />
                </el-select>
              </div>
            </template>
            <div v-if="!needsSecondObsRow(item)" class="field field-type">
              <span class="field-label">数据类型</span>
              <div class="resolved-type-value">{{ dataTypeLabel(observableDataType(item)) }}</div>
            </div>
          </div>

          <div v-if="needsSecondObsRow(item)" class="obs-row">
            <div v-if="item.sourceType === 'DEVICE_ATTRIBUTE'" class="field field-attr">
              <span class="field-label">监测属性</span>
              <el-select v-model="item.targetName" size="small" filterable placeholder="选择监测物理属性">
                <el-option v-for="attr in attributesFor(item.deviceModelId)" :key="attr.attributeName" :label="attr.displayName || attr.attributeName" :value="attr.attributeName" />
              </el-select>
            </div>
            <div v-else-if="item.sourceType === 'DEVICE_OPERATION_STATE'" class="field field-grow">
              <span class="field-label">状态分区</span>
              <el-select v-model="item.regionName" size="small" placeholder="选择功能状态分区">
                <el-option v-for="region in operationRegions(item.deviceModelId)" :key="region.regionName" :label="region.regionName" :value="region.regionName" />
              </el-select>
            </div>
            <div v-else-if="item.sourceType === 'NODE_INTERNAL_VARIABLE'" class="field field-grow">
              <span class="field-label">内部变量</span>
              <el-select v-model="item.variableName" size="small" filterable>
                <el-option v-for="v in internalVariablesFor(item)" :key="v.name" :label="v.name + ' (' + v.dataType + ')'" :value="v.name" />
              </el-select>
            </div>
            <div class="field field-type">
              <span class="field-label">数据类型</span>
              <div class="resolved-type-value">{{ dataTypeLabel(observableDataType(item)) }}</div>
            </div>
          </div>
        </div>
      </div>

      <div class="expression-toolbar">
        <div class="toolbar-row">
          <span class="toolbar-label">公式模板</span>
          <button v-for="tpl in formulaTemplates" :key="tpl.name" type="button" class="tool-btn tpl" @click="applyTemplate(tpl)">
            {{ tpl.name }}
          </button>
        </div>
        <div class="toolbar-row">
          <span class="toolbar-label">时序函数</span>
          <el-tooltip v-for="fn in temporalFunctions" :key="fn.key" placement="top" :show-after="200">
            <template #content>
              <div class="fn-tooltip">
                <strong>{{ fn.name }}</strong>
                <span>{{ fn.description }}</span>
                <code>{{ fn.example }}</code>
              </div>
            </template>
            <button type="button" class="tool-btn fn" @click="insertFunction(fn)">{{ fn.name }}</button>
          </el-tooltip>
          <span class="picker-empty">返回数值，需再比较；窗口秒数插入后可改</span>
        </div>
        <div class="toolbar-row">
          <span class="toolbar-label">操作符</span>
          <button v-for="op in operatorTokens" :key="op" type="button" class="tool-btn op" @click="insertText(op)">
            {{ op.trim() }}
          </button>
        </div>
        <div class="toolbar-row">
          <span class="toolbar-label">已绑定</span>
          <button
            v-for="item in form.bindings"
            :key="'chip-' + item.name"
            type="button"
            class="tool-btn var"
            @click="insertText('@' + item.name + ' ')"
          >@{{ item.name }}</button>
          <span v-if="!form.bindings.length" class="picker-empty">请先绑定变量</span>
        </div>
      </div>

      <div class="formula-composer">
        <div class="formula-input-shell">
          <div class="formula-input-highlight" aria-hidden="true">
            <div class="formula-input-highlight-content" :style="{ marginLeft: `-${inputScrollLeft}px` }">
              <template v-for="(token, index) in inputTokens" :key="index + '-' + token.value">
                <span :class="{ 'formula-input-token': token.kind === 'bound', 'formula-input-unbound': token.kind === 'unbound' }">{{ token.value }}</span>
              </template>
            </div>
          </div>
          <input
            ref="formulaInputRef"
            class="formula-plain-input"
            :value="form.displayExpression"
            placeholder="例如：@value > @limit"
            spellcheck="false"
            @input="onFormulaInput"
            @scroll="syncHighlightScroll"
          />
        </div>
        <div class="formula-meta-bar">
          <span>执行表达式：<code>{{ backendExpression || '等待输入' }}</code></span>
          <span v-if="undeclaredVars.length" class="syntax-error">● 暂无引用绑定关系：{{ undeclaredVars.map(n => '@' + n).join(' ') }}</span>
          <span v-else-if="syntaxError" class="syntax-error">● {{ syntaxError }}</span>
          <span v-else-if="backendExpression" class="syntax-valid">● 语法校验通过</span>
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
          <span style="font-size: 13px; font-weight: 600; color: var(--sl-text-heading);">违规处置动作列表</span>
          <button class="btn-aliyun" type="button" @click="addAction">+ 添加处置动作</button>
        </div>

        <div v-for="(act, index) in form.actions" :key="act.key" class="action-card">
          <div class="action-card-head">
            <span style="font-weight: 600; font-size: 13px;">动作 {{ index + 1 }}</span>
            <div style="display: flex; align-items: center; gap: 12px;">
              <el-radio-group v-model="act.actionType" size="small" @change="resetAction(act)">
                <el-radio-button value="DEVICE_CAPABILITY">设备能力</el-radio-button>
                <el-radio-button value="SYSTEM">系统动作</el-radio-button>
              </el-radio-group>
              <button v-if="canRemoveAction" class="btn-link danger" type="button" @click="form.actions.splice(index, 1)">删除</button>
            </div>
          </div>


          <div v-if="act.actionType === 'DEVICE_CAPABILITY'" class="action-device-block">
            <div class="action-inputs-grid action-row-targets">
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
                <el-form-item :label="deviceBindingsAreModelWide ? '执行实例（可选）' : '设备实例'" style="margin-bottom: 0;">
                  <el-select
                    v-model="act.deviceInstanceId"
                    size="small"
                    :clearable="deviceBindingsAreModelWide"
                    :placeholder="deviceBindingsAreModelWide ? '与观测同源' : '选择执行实例'"
                  >
                    <el-option v-for="inst in instancesFor(act.deviceModelId)" :key="inst.id" :label="inst.instanceName || ('实例' + inst.id)" :value="inst.id" />
                  </el-select>
                </el-form-item>
              </template>
            </div>

            <div class="action-inputs-grid action-row-capability">
              <el-form-item label="执行能力" style="margin-bottom: 0;">
                <el-select v-model="act.capabilityName" size="small" filterable @change="initParameters(act)">
                  <el-option v-for="cap in capabilitiesFor(act.deviceModelId)" :key="cap.capabilityName" :label="cap.displayName || cap.capabilityName" :value="cap.capabilityName" />
                </el-select>
              </el-form-item>

              <template v-if="parameterDefinitions(act).length">
                <el-form-item v-for="param in parameterDefinitions(act)" :key="param.name" :label="param.displayName" style="margin-bottom: 0;">
                  <el-select v-if="param.dataType === 'BOOLEAN'" v-model="act.parameters[param.name]" size="small">
                    <el-option label="true" :value="true" />
                    <el-option label="false" :value="false" />
                  </el-select>
                  <el-input-number v-else-if="isNumeric(param.dataType)" v-model="act.parameters[param.name]" size="small" :controls="false" />
                  <el-input v-else v-model="act.parameters[param.name]" size="small" />
                </el-form-item>
              </template>
            </div>
          </div>

          <!-- 系统动作 -->
          <div v-else class="action-inputs-grid" style="grid-template-columns: 180px 1fr;">
            <el-form-item label="系统处置" style="margin-bottom: 0;">
              <el-select v-model="act.action" size="small">
                <el-option label="终止当前任务" value="ABORT" />
                <el-option label="暂停当前任务" value="PAUSE" />
                <el-option label="发布系统告警" value="ALERT" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="!taskMode && act.action !== 'ALERT'" :label="taskBindingsAreTemplateWide ? '目标任务（可选）' : '目标任务'" style="margin-bottom: 0;">
              <el-select
                v-model="act.targetTaskId"
                size="small"
                filterable
                :clearable="taskBindingsAreTemplateWide"
                :placeholder="taskBindingsAreTemplateWide ? '与观测同源' : '指定触发违规的目标任务'"
              >
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
import { computed, nextTick, reactive, ref, watch } from 'vue'
import {
  extractExpressionVariables,
  toBackendExpression,
  validateDisplayExpressionSyntax,
  sourceCategoryLabel,
  formatDeviceActionLabel,
  modelCapabilities,
  bindingDeviceModelId
} from '../../utils/constraintExpression.js'
import { workflowModelName, workflowNodes } from '../../utils/workflowAuthoring.js'

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
const formulaInputRef = ref<HTMLInputElement | null>(null)
const syntaxError = ref('')
const inputScrollLeft = ref(0)

const formulaTemplates = [
  { name: '上限越界', expression: '@value > @limit', bindings: [{ name: 'value', bindingType: 'OBSERVABLE' }, { name: 'limit', bindingType: 'LITERAL', dataType: 'DOUBLE', value: 0 }] },
  { name: '下限越界', expression: '@value < @limit', bindings: [{ name: 'value', bindingType: 'OBSERVABLE' }, { name: 'limit', bindingType: 'LITERAL', dataType: 'DOUBLE', value: 0 }] },
  { name: '区间越界', expression: '@value < @min || @value > @max', bindings: [{ name: 'value', bindingType: 'OBSERVABLE' }, { name: 'min', bindingType: 'LITERAL', dataType: 'DOUBLE', value: 0 }, { name: 'max', bindingType: 'LITERAL', dataType: 'DOUBLE', value: 0 }] },
  { name: '突变超限', expression: 'delta(@value, 10) > @limit', bindings: [{ name: 'value', bindingType: 'OBSERVABLE' }, { name: 'limit', bindingType: 'LITERAL', dataType: 'DOUBLE', value: 0 }] },
  { name: '均值超限', expression: 'avg(@value, 60) > @limit', bindings: [{ name: 'value', bindingType: 'OBSERVABLE' }, { name: 'limit', bindingType: 'LITERAL', dataType: 'DOUBLE', value: 0 }] },
  { name: '速率超限', expression: 'rate(@value, 10) > @limit', bindings: [{ name: 'value', bindingType: 'OBSERVABLE' }, { name: 'limit', bindingType: 'LITERAL', dataType: 'DOUBLE', value: 0 }] },
  { name: '状态不等', expression: '@actual != @expected', bindings: [{ name: 'actual', bindingType: 'OBSERVABLE' }, { name: 'expected', bindingType: 'LITERAL', dataType: 'STRING', value: '' }] }
]

const temporalFunctions = [
  { key: 'delta', name: '变化量', insert: 'delta', window: 10, description: '窗口内末值减首值，单位与测点相同。第二个参数是窗口秒数，插入后可直接改。', example: 'delta(@value, 10) > @limit' },
  { key: 'avg', name: '窗口均值', insert: 'avg', window: 60, description: '窗口内全部样本的算术平均。第二个参数是窗口秒数，插入后可直接改。', example: 'avg(@value, 60) > @limit' },
  { key: 'rate', name: '变化速率', insert: 'rate', window: 10, description: '窗口内 (末值-首值)/实际间隔，单位是每秒。第二个参数是窗口秒数，插入后可直接改。', example: 'rate(@value, 10) > @limit' }
]

const operatorTokens = [' > ', ' < ', ' >= ', ' <= ', ' == ', ' != ', ' && ', ' || ', '!', '+', '-', '*', '/', '(', ')']
const scalarTypes = ['DOUBLE', 'INTEGER', 'STRING', 'BOOLEAN']

const newBinding = (name: string) => ({
  _key: ++serial,
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
  displayExpression: '',
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
    ? [...new Map(props.taskWorkflowNodes.map((x: any) => [Number(x.flowModelId), { id: Number(x.flowModelId), flowName: x.flowModelName || x.flowName || ('流程' + x.flowModelId), flowModelName: x.flowModelName || x.flowName || ('流程' + x.flowModelId) }])).values()]
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
  },
  { immediate: true }
)

function variableNames() {
  return extractExpressionVariables(form.displayExpression)
}

function isVarUsed(name: string) {
  return variableNames().includes(name)
}

const undeclaredVars = computed(() =>
  variableNames().filter(name => !form.bindings.some((b: any) => b.name === name))
)

function applyTemplate(tpl: any) {
  const specs = tpl.bindings || []
  const existing = new Map(form.bindings.map((b: any) => [b.name, b]))
  form.bindings = specs.map((spec: any) => {
    const prev = existing.get(spec.name)
    if (prev) {
      return {
        ...prev,
        bindingType: spec.bindingType || prev.bindingType,
        dataType: spec.dataType || prev.dataType,
        value: spec.bindingType === 'LITERAL' && (prev.value === null || prev.value === undefined || prev.value === '')
          ? (spec.value ?? 0)
          : prev.value
      }
    }
    return { ...newBinding(spec.name), ...spec }
  })
  form.displayExpression = tpl.expression
  nextTick(() => formulaInputRef.value?.focus())
}

function nextBindingName(base: string) {
  const used = new Set(form.bindings.map((b: any) => b.name))
  if (!used.has(base)) return base
  let n = 2
  while (used.has(base + n)) n++
  return base + n
}

function addObservable() {
  form.bindings.push(newBinding(nextBindingName('value')))
}

function addLiteral() {
  const item = newBinding(nextBindingName('limit'))
  item.bindingType = 'LITERAL'
  item.dataType = 'DOUBLE'
  item.value = 0
  form.bindings.push(item)
}

function removeBinding(name: string) {
  form.bindings = form.bindings.filter((b: any) => b.name !== name)
}

function renameBinding(item: any, nextName: string) {
  const name = String(nextName || '').trim()
  if (!name || name === item.name) return
  if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(name) || form.bindings.some((b: any) => b !== item && b.name === name)) {
    item._nameRev = (item._nameRev || 0) + 1
    return
  }
  const old = item.name
  item.name = name
  form.displayExpression = String(form.displayExpression || '').replace(
    new RegExp(`@${escapeRegExp(old)}\\b`, 'g'),
    `@${name}`
  )
}

function needsSecondObsRow(item: any) {
  return item.sourceType === 'DEVICE_ATTRIBUTE'
    || item.sourceType === 'DEVICE_OPERATION_STATE'
    || item.sourceType === 'NODE_INTERNAL_VARIABLE'
}

function insertText(value: string, select?: { start: number; end: number }) {
  const input = formulaInputRef.value
  const src = form.displayExpression || ''
  const focused = !!input && document.activeElement === input
  const start = focused ? (input?.selectionStart ?? src.length) : src.length
  const end = focused ? (input?.selectionEnd ?? start) : start
  form.displayExpression = src.slice(0, start) + value + src.slice(end)
  nextTick(() => {
    input?.focus()
    if (select) input?.setSelectionRange(start + select.start, start + select.end)
    else input?.setSelectionRange(start + value.length, start + value.length)
  })
}

function insertFunction(fn: any) {
  const firstObs = form.bindings.find((b: any) => b.bindingType === 'OBSERVABLE')
  const inner = firstObs ? `@${firstObs.name}` : '@value'
  const windowSeconds = Number(fn.window) > 0 ? Number(fn.window) : 10
  const prefix = `${fn.insert}(${inner}, `
  const token = `${prefix}${windowSeconds})`
  insertText(token, { start: prefix.length, end: prefix.length + String(windowSeconds).length })
}

function onFormulaInput(event: Event) {
  form.displayExpression = (event.target as HTMLInputElement).value
}

function syncHighlightScroll(event: Event) {
  inputScrollLeft.value = (event.target as HTMLInputElement).scrollLeft
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

const inputTokens = computed(() => {
  const source = String(form.displayExpression || '')
  const declared = new Set(form.bindings.map((b: any) => b.name))
  if (!source) return []
  const re = /@([A-Za-z_][A-Za-z0-9_]*)/g
  const tokens: { value: string; kind: 'text' | 'bound' | 'unbound' }[] = []
  let last = 0
  let match: RegExpExecArray | null
  while ((match = re.exec(source))) {
    if (match.index > last) tokens.push({ value: source.slice(last, match.index), kind: 'text' })
    tokens.push({ value: match[0], kind: declared.has(match[1]) ? 'bound' : 'unbound' })
    last = match.index + match[0].length
  }
  if (last < source.length) tokens.push({ value: source.slice(last), kind: 'text' })
  return tokens
})

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

const deviceBindingsAreModelWide = computed(() => {
  if (props.taskMode) return false
  const obs = form.bindings.filter((b: any) => b.bindingType === 'OBSERVABLE' && isDeviceSource(b.sourceType))
  return obs.length > 0 && obs.every((b: any) => !b.deviceInstanceId)
})

const taskBindingsAreTemplateWide = computed(() => {
  if (props.taskMode) return false
  const obs = form.bindings.filter((b: any) => b.bindingType === 'OBSERVABLE' && (isNodeSource(b.sourceType) || b.sourceType === 'TASK_LIFECYCLE_STATE'))
  return obs.length > 0 && obs.every((b: any) => b.sourceType !== 'TASK_LIFECYCLE_STATE' || !b.taskId)
})

const sourceLabel = (t: string) => sourceCategoryLabel(t)
const dataTypeLabel = (t: string) => ({ DOUBLE: '浮点数', INTEGER: '整数', STRING: '字符串', BOOLEAN: '布尔值', JSON: 'JSON对象' } as any)[t] || t || '-'

const model = (id: any) => props.models.find((x: any) => Number(x.id || x.modelId) === Number(id))
const instancesFor = (id: any) => props.instances.filter((x: any) => Number(x.deviceModelId || x.modelId) === Number(id))
const tasksFor = (flowId: any) => props.tasks.filter((x: any) => Number(x.flowModelId) === Number(flowId))
const attributesFor = (id: any) => (Array.isArray(model(id)?.attributes) ? model(id).attributes : [])
const capabilitiesFor = (id: any) => modelCapabilities(model(id))
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
  return `${resource.flowModelName || resource.flowName || resource.flowModelId} / ${resource.nodeName} → ${resource.instanceName || '实例' + (resource.deviceInstanceId || '待绑定')}`
}

function nodesFor(flowId: any) {
  const nodes = props.taskMode
    ? props.taskWorkflowNodes.filter((x: any) => Number(x.flowModelId) === Number(flowId))
    : workflowNodes(props.workflows.find((x: any) => Number(x.id) === Number(flowId)))
  return [...new Map(nodes.map((node: any) => [node.name, node])).values()]
}

function addAction() {
  const act = newAction()
  act.deviceModelId = bindingDeviceModelId(form.bindings)
  form.actions.push(act)
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
  const raw = cap?.parameters
  if (Array.isArray(raw)) return raw.map((x: any) => ({ name: x.name, displayName: x.displayName || x.name, dataType: x.dataType || 'STRING' }))
  return []
}

function initParameters(act: any) {
  act.parameters = {}
  parameterDefinitions(act).forEach((param: any) => {
    act.parameters[param.name] = param.dataType === 'BOOLEAN' ? false : (isNumeric(param.dataType) ? null : '')
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
        varMap[b.name] = b.taskId ? `任务#${b.taskId}状态` : '工作流任务状态'
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
      return formatDeviceActionLabel(act, props.models, props.instances)
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
  } else if (item.sourceType === 'TASK_LIFECYCLE_STATE') {
    if (props.taskMode) {
      if (availableWorkflows.value.length === 1) source.workflowTemplateId = availableWorkflows.value[0].id
    } else {
      if (!item.workflowTemplateId) throw Error(`@${item.name} 请选择工作流模板`)
      source.workflowTemplateId = item.workflowTemplateId
      if (item.taskId) source.taskId = item.taskId
    }
  }
  return { bindingType: 'OBSERVABLE', source }
}

function actionPayload(act: any) {
  if (act.actionType === 'SYSTEM') {
    if (!props.taskMode && act.action !== 'ALERT' && !act.targetTaskId && !taskBindingsAreTemplateWide.value) {
      throw Error(`${act.action} 必须选择目标任务`)
    }
    return {
      actionType: 'SYSTEM',
      action: act.action,
      targetTaskId: props.taskMode ? null : act.targetTaskId || null
    }
  }
  if (!act.capabilityName) {
    throw Error('设备能力动作必须选择执行能力')
  }
  if (!act.deviceModelId) {
    throw Error('设备能力动作必须选择设备模型')
  }
  if (!act.deviceInstanceId && (props.taskMode || !deviceBindingsAreModelWide.value)) {
    throw Error('设备能力动作必须选择具体执行实例')
  }
  const payload: Record<string, any> = {
    actionType: 'DEVICE_CAPABILITY',
    deviceModelId: Number(act.deviceModelId),
    capabilityName: act.capabilityName,
    parameters: act.parameters || {}
  }
  if (act.deviceInstanceId) payload.deviceInstanceId = act.deviceInstanceId
  return payload
}

function validateAndBuild() {
  if (!form.ruleName.trim()) throw Error('请输入约束规则名称')
  validateDisplayExpressionSyntax(form.displayExpression)
  if (!backendExpression.value) throw Error('请输入违规判定表达式')
  if (!variableNames().length) throw Error('表达式中至少需要包含一个 @变量 标识符')
  if (undeclaredVars.value.length) {
    throw Error(`表达式引用了未声明变量：${undeclaredVars.value.map(n => '@' + n).join('、')}`)
  }

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
    displayExpression: rule ? displayExpression(rule) : '',
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
      return {
        ...newAction(),
        actionType: act.actionType || 'DEVICE_CAPABILITY',
        action: act.action || 'ALERT',
        targetTaskId: act.targetTaskId || null,
        resourceKey: res?.bindingKey || '',
        deviceModelId: act.deviceModelId || null,
        deviceInstanceId: act.deviceInstanceId || null,
        capabilityName: act.capabilityName || '',
        parameters: act.parameters || {}
      }
    })
  }
}

loadRule()
defineExpose({ validateAndBuild, loadRule })
</script>

<style scoped>
.constraint-editor {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.preview-banner {
  background: var(--sl-primary-light);
  border: 1px solid var(--sl-primary-border);
  border-left: 3px solid var(--sl-primary);
  border-radius: var(--sl-radius-sm);
  padding: 12px 16px;
}
.preview-banner-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--sl-primary);
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
.preview-static { color: var(--sl-text-secondary); }
.preview-chip {
  padding: 2px 7px;
  border-radius: var(--sl-radius-sm);
  background: #ffffff;
  border: 1px solid var(--sl-border-input);
  color: var(--sl-text-heading);
  font-size: 12.5px;
  font-weight: 500;
}
.preview-chip.emphasis { font-weight: 600; }
.preview-chip.action {
  background: var(--sl-danger-light);
  border-color: var(--sl-danger-border);
  color: var(--sl-danger);
}
.preview-arrow { color: var(--sl-text-disabled); margin: 0 2px; }

.editor-section {
  background: #ffffff;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  padding: 16px;
}
.section-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--sl-text-heading);
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.section-hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--sl-text-secondary);
}

.rule-info-head {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  margin-bottom: 10px;
}
.rule-name-item {
  flex: 1;
  min-width: 0;
  margin-bottom: 0;
}
.rule-desc-item { margin-bottom: 0; }
.enable-inline {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 4px;
  flex-shrink: 0;
}
.enable-label {
  font-size: 13px;
  color: var(--sl-text-body);
}

.binding-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.binding-toolbar-label {
  font-size: 12px;
  color: var(--sl-text-secondary);
  margin-right: 4px;
}
.binding-guide {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 10px;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: var(--sl-primary-light);
  border: 1px dashed var(--sl-primary-border);
  border-radius: var(--sl-radius-sm);
  font-size: 12.5px;
  color: var(--sl-text-body);
  line-height: 1.4;
}
.guide-step {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--sl-primary);
  color: #ffffff;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}

.binding-card {
  background: var(--sl-bg-hover);
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  padding: 12px 14px;
  margin-bottom: 10px;
}
.binding-card.unused { border-style: dashed; }
.binding-card:last-of-type { margin-bottom: 12px; }
.binding-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.identifier-pill {
  display: flex;
  align-items: center;
  gap: 6px;
}
.at-prefix {
  font-family: var(--sl-font-mono);
  font-weight: 700;
  font-size: 13px;
  color: var(--sl-primary);
}
.var-name-input {
  width: 120px;
}
.var-name-input :deep(.el-input__wrapper) {
  padding: 0 8px;
  font-family: var(--sl-font-mono);
  font-weight: 600;
}
.identifier-type-desc { font-size: 12px; color: var(--sl-text-secondary); }
.unused-hint { font-size: 11px; color: var(--sl-text-disabled); }
.binding-head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.field-label {
  font-size: 12px;
  color: var(--sl-text-secondary);
  line-height: 16px;
}
.field :deep(.el-select),
.field :deep(.el-input),
.field :deep(.el-input-number) {
  width: 100%;
}
.field-type { width: 140px; flex-shrink: 0; }
.field-value { width: 200px; }
.field-source { min-width: 200px; flex: 1.1; }
.field-grow { flex: 1; min-width: 160px; }
.field-attr { width: 220px; flex: 0 0 220px; }

.binding-literal-row,
.obs-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}
.obs-row + .obs-row { margin-top: 10px; }

.resolved-type-value {
  height: 24px;
  display: inline-flex;
  align-items: center;
  padding: 0 10px;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  background: #ffffff;
  color: var(--sl-text-heading);
  font-size: 13px;
  font-weight: 500;
  line-height: 24px;
  white-space: nowrap;
}

.expression-toolbar {
  border: 1px solid var(--sl-border-base);
  border-bottom: none;
  border-radius: var(--sl-radius-sm) var(--sl-radius-sm) 0 0;
  background: var(--sl-bg-hover);
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
  color: var(--sl-text-secondary);
  width: 54px;
  flex-shrink: 0;
}
.tool-btn {
  height: 24px;
  padding: 0 8px;
  border-radius: var(--sl-radius-sm);
  border: 1px solid var(--sl-border-input);
  background: #ffffff;
  color: var(--sl-text-body);
  font-size: 12px;
  cursor: pointer;
  transition: var(--sl-ease-smooth);
}
.tool-btn:hover {
  border-color: var(--sl-primary);
  color: var(--sl-primary);
}
.tool-btn.fn {
  color: var(--sl-primary);
  background: var(--sl-primary-light);
  border-color: var(--sl-primary-border);
  font-family: var(--sl-font-mono);
}
.tool-btn.op {
  font-family: var(--sl-font-mono);
  font-weight: 600;
  min-width: 28px;
}
.tool-btn.var {
  border-color: var(--sl-primary-border);
  background: var(--sl-primary-light);
  color: var(--sl-primary);
  font-family: var(--sl-font-mono);
}
.picker-empty { font-size: 11.5px; color: var(--sl-text-disabled); }
.fn-tooltip { display: grid; gap: 4px; max-width: 260px; }
.fn-tooltip strong { font-size: 12px; }
.fn-tooltip span { font-size: 11.5px; line-height: 1.5; }
.fn-tooltip code { font-family: var(--sl-font-mono); font-size: 11px; color: #93c5fd; }

.formula-composer { display: flex; flex-direction: column; gap: 6px; }
.formula-input-shell {
  position: relative;
  height: 36px;
  border: 1px solid var(--sl-border-input);
  border-radius: 0 0 var(--sl-radius-sm) var(--sl-radius-sm);
  background: #ffffff;
}
.formula-input-highlight {
  position: absolute;
  inset: 0;
  z-index: 1;
  overflow: hidden;
  pointer-events: none;
}
.formula-input-highlight-content {
  width: max-content;
  min-width: 100%;
  box-sizing: border-box;
  padding: 7px 10px;
  color: var(--sl-text-heading);
  font-family: var(--sl-font-mono);
  font-size: 13px;
  line-height: 20px;
  white-space: pre;
  text-rendering: geometricPrecision;
}
.formula-input-token {
  border-radius: 3px;
  background: var(--sl-primary-light);
  box-shadow: 0 0 0 2px var(--sl-primary-light);
  color: var(--sl-primary);
  font-weight: 600;
}
.formula-input-unbound {
  border-radius: 3px;
  background: var(--sl-danger-light);
  box-shadow: 0 0 0 2px var(--sl-danger-light);
  color: var(--sl-danger);
  font-weight: 600;
}
.formula-plain-input {
  position: relative;
  z-index: 2;
  width: 100%;
  height: 34px;
  box-sizing: border-box;
  padding: 7px 10px;
  border: 0;
  background: transparent;
  color: transparent;
  -webkit-text-fill-color: transparent;
  caret-color: var(--sl-text-heading);
  font-family: var(--sl-font-mono);
  font-size: 13px;
  line-height: 20px;
  outline: 0;
}
.formula-plain-input::placeholder {
  color: var(--sl-text-disabled);
  -webkit-text-fill-color: var(--sl-text-disabled);
}
.formula-input-shell:focus-within {
  border-color: var(--sl-primary);
  box-shadow: 0 0 0 1px var(--sl-primary);
}

.formula-meta-bar {
  font-size: 12px;
  color: var(--sl-text-secondary);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.formula-meta-bar code { color: var(--sl-primary); font-family: var(--sl-font-mono); }
.syntax-valid { color: var(--sl-success); font-size: 12px; }
.syntax-error { color: var(--sl-danger); font-size: 12px; }

.action-card {
  background: var(--sl-bg-hover);
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  padding: 12px 14px;
  margin-bottom: 10px;
}
.action-card:last-child { margin-bottom: 0; }
.action-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.action-device-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.action-inputs-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 8px;
  align-items: center;
}
.action-row-targets,
.action-row-capability {
  grid-template-columns: minmax(180px, 1fr) minmax(240px, 1.4fr);
}
.action-row-targets :deep(.el-select),
.action-row-capability :deep(.el-select) {
  width: 100%;
}
.action-row-targets :deep(.el-select__placeholder) {
  overflow: visible;
  text-overflow: clip;
}

.task-mode-hint {
  font-size: 12px;
  color: var(--sl-text-secondary);
  padding: 6px 8px;
  background: #ffffff;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
}
</style>
