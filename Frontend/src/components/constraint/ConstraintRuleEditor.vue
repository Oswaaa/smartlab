<template>
  <div class="constraint-editor">

    <!-- 1. 实时业务逻辑解析预览条 (纯净字阶 · 纯蓝字/纯红字直接呈现 · 无胶囊) -->
    <div class="logic-preview-bar">
      <span class="logic-preview-label">判定逻辑解析:</span>
      <div class="logic-preview-content">
        <span class="preview-static">当</span>
        <span class="text-blue-var">{{ previewData.conditionExpr }}</span>
        <span class="preview-static">持续</span>
        <span class="text-duration-val">{{ previewData.windowText }}</span>
        <span class="preview-arrow">➔</span>
        <span class="preview-static">执行</span>
        <template v-for="(act, idx) in previewData.actionLabels" :key="idx">
          <span v-if="idx > 0" class="action-separator">、</span>
          <span class="text-danger-act">{{ act }}</span>
        </template>
      </div>
    </div>

    <!-- 2. 规则基本信息分段 -->
    <div class="card-section-block">
      <div class="section-head-bar">
        <span class="section-title">1. 基本信息</span>
        <div class="enable-inline">
          <span class="enable-label">启用规则</span>
          <el-switch v-model="form.isEnabled" size="small" />
        </div>
      </div>
      <div class="rule-info-grid">
        <div class="form-field-row">
          <label class="form-lbl-compact required">规则名称</label>
          <el-input v-model="form.ruleName" placeholder="例如：反应釜超温防爆保护" class="form-control-28" />
        </div>
        <div class="form-field-row">
          <label class="form-lbl-compact">规则说明</label>
          <el-input v-model="form.description" placeholder="简要说明该约束规则的工艺或安全防护目的（选填）" class="form-control-28" />
        </div>
      </div>
    </div>

    <!-- 3. 声明变量与数据源分段 -->
    <div class="card-section-block">
      <div class="section-head-bar">
        <span class="section-title">2. 声明变量与数据源</span>
        <div class="section-actions">
          <button class="btn-aliyun small" type="button" @click="addObservable">+ 观测变量</button>
          <button class="btn-aliyun small" type="button" @click="addLiteral">+ 固定阈值</button>
        </div>
      </div>

      <div v-if="!form.bindings.length" class="binding-guide">
        <span class="guide-step">1</span>
        <span>点击下方公式模板，或手动添加观测变量 / 固定阈值</span>
        <span class="guide-step">2</span>
        <span>绑定数据源后，点选已声明变量组成判定式</span>
      </div>

      <div v-for="item in form.bindings" :key="item._key" class="var-decl-row" :class="{ unused: !isVarUsed(item.name) }">
        <div class="var-decl-head">
          <div class="var-name-ident">
            <span class="at-prefix">@</span>
            <el-input
              :key="item._key + '-' + (item._nameRev || 0)"
              :model-value="item.name"
              size="small"
              class="var-name-input-clean"
              placeholder="变量名"
              @change="(val) => renameBinding(item, val)"
            />
            <span class="identifier-type-desc">{{ item.bindingType === 'OBSERVABLE' ? '观测数据' : '固定阈值' }}</span>
            <span v-if="!isVarUsed(item.name)" class="unused-hint">● 未写入公式</span>
            <span v-else class="used-hint">● 已在公式中引用</span>
          </div>
          <div class="binding-head-actions">
            <el-radio-group v-model="item.bindingType" size="small" @change="onBindingTypeChange(item)">
              <el-radio-button value="OBSERVABLE">观测数据</el-radio-button>
              <el-radio-button value="LITERAL">固定值</el-radio-button>
            </el-radio-group>
            <button class="btn-link-danger" type="button" @click="removeBinding(item.name)">删除</button>
          </div>
        </div>

        <!-- LITERAL 固定值 -->
        <div v-if="item.bindingType === 'LITERAL'" class="literal-row-flex">
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

        <!-- OBSERVABLE 观测数据 -->
        <div v-else class="var-grid-layout">
          <!-- 来源类型 -->
          <div class="field field-source">
            <span class="field-label">观测来源</span>
            <el-select v-model="item.sourceType" size="small" @change="resetSource(item)">
              <el-option v-for="type in availableSourceTypes" :key="type" :label="sourceLabel(type)" :value="type" />
            </el-select>
          </div>

          <!-- 设备来源 -->
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
          </template>

          <!-- 任务生命周期 -->
          <template v-else-if="item.sourceType === 'TASK_LIFECYCLE_STATE'">
            <div v-if="taskMode" class="task-mode-hint field-grow">自动绑定当前执行任务的状态</div>
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

          <!-- 节点来源 -->
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
            <div v-if="item.sourceType === 'NODE_INTERNAL_VARIABLE'" class="field field-grow">
              <span class="field-label">内部变量</span>
              <el-select v-model="item.variableName" size="small" filterable>
                <el-option v-for="v in internalVariablesFor(item)" :key="v.name" :label="v.name + ' (' + v.dataType + ')'" :value="v.name" />
              </el-select>
            </div>
          </template>

          <!-- 数据类型纯文字显示 (一直占位展示列，未选择属性时显示短横线，去掉灰底框) -->
          <div class="field field-type-view">
            <span class="field-label">数据类型</span>
            <div class="data-type-cell">{{ observableDataType(item) ? dataTypeLabel(observableDataType(item)) : '-' }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 4. 判定公式点选与编写分段 -->
    <div class="card-section-block">
      <div class="section-head-bar">
        <span class="section-title">3. 判定公式点选与编写</span>
        <span class="section-desc-hint">先声明变量与阈值，再点选组成判定布尔式</span>
      </div>

      <div class="composer-plain-box">
        <!-- 1行：公式模板 (浅蓝底浅蓝字) -->
        <div class="token-line">
          <span class="token-line-lbl">公式模板:</span>
          <button v-for="tpl in formulaTemplates" :key="tpl.name" type="button" class="btn-token-blue" @click="applyTemplate(tpl)">
            {{ tpl.name }}
          </button>
        </div>

        <!-- 2行：时序函数 (浅蓝底浅蓝字) -->
        <div class="token-line">
          <span class="token-line-lbl">时序函数:</span>
          <el-tooltip v-for="fn in temporalFunctions" :key="fn.key" placement="top" :show-after="200">
            <template #content>
              <div class="fn-tooltip">
                <strong>{{ fn.name }}</strong>
                <span>{{ fn.description }}</span>
                <code>{{ fn.example }}</code>
              </div>
            </template>
            <button type="button" class="btn-token-blue fn" @click="insertFunction(fn)">{{ fn.name }}</button>
          </el-tooltip>
          <span class="picker-empty">返回数值，需再比较；窗口秒数插入后可改</span>
        </div>

        <!-- 3行：操作符 (白底黑字) -->
        <div class="token-line">
          <span class="token-line-lbl">操作符:</span>
          <button v-for="op in operatorTokens" :key="op" type="button" class="btn-token-plain op" @click="insertText(op)">
            {{ op.trim() }}
          </button>
        </div>

        <!-- 4行：已声明变量 (独立新的一行，浅蓝底蓝字) -->
        <div class="token-line">
          <span class="token-line-lbl">已声明:</span>
          <button
            v-for="item in form.bindings"
            :key="'chip-' + item.name"
            type="button"
            class="btn-token-blue var"
            @click="insertText('@' + item.name + ' ')"
          >@{{ item.name }}</button>
          <span v-if="!form.bindings.length" class="picker-empty">请先在上方声明变量</span>
        </div>

        <!-- 双层高亮输入框 -->
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

    <!-- 5. 判定窗口与处置动作分段 -->
    <div class="card-section-block">
      <div class="section-head-bar">
        <span class="section-title">4. 判定窗口与违规处置动作</span>
        <button class="btn-aliyun small" type="button" @click="addAction">+ 添加处置动作</button>
      </div>

      <!-- 持续判定时间 -->
      <div class="window-duration-row">
        <label class="form-lbl-compact" style="font-weight:600;">持续判定时间:</label>
        <el-input-number v-model="form.windowSeconds" size="small" :controls="false" :min="1" placeholder="留空为瞬时" style="width: 100px;" />
        <span class="window-hint">秒（公式连续成立达到该时间后判定违规，为空表示瞬时触发）</span>
      </div>

      <!-- 动作列表 -->
      <div v-for="(act, index) in form.actions" :key="act.key" class="action-decl-row">
        <div class="action-decl-head">
          <span class="action-idx-title">动作 {{ index + 1 }}</span>
          <div style="display: flex; align-items: center; gap: 10px;">
            <el-radio-group v-model="act.actionType" size="small" @change="resetAction(act)">
              <el-radio-button value="DEVICE_CAPABILITY">设备能力</el-radio-button>
              <el-radio-button value="SYSTEM">系统动作</el-radio-button>
            </el-radio-group>
            <button v-if="canRemoveAction" class="btn-link-danger" type="button" @click="form.actions.splice(index, 1)">删除</button>
          </div>
        </div>

        <!-- 设备能力 (两行结构：第一行目标与能力，第二行参数) -->
        <div v-if="act.actionType === 'DEVICE_CAPABILITY'" class="action-device-block">
          <div class="action-device-main-row">
            <template v-if="taskMode">
              <div class="field field-grow">
                <span class="field-label">任务设备</span>
                <el-select v-model="act.resourceKey" size="small" @change="selectActionResource(act)">
                  <el-option v-for="res in taskResources" :key="res.bindingKey" :label="resourceLabel(res)" :value="res.bindingKey" />
                </el-select>
              </div>
            </template>
            <template v-else>
              <div class="field field-grow">
                <span class="field-label">设备模型</span>
                <el-select v-model="act.deviceModelId" size="small" filterable @change="resetActionDevice(act)">
                  <el-option v-for="m in models" :key="m.id" :label="m.modelName" :value="m.id" />
                </el-select>
              </div>
              <div class="field field-grow">
                <span class="field-label">{{ deviceBindingsAreModelWide ? '执行实例（可选）' : '设备实例' }}</span>
                <el-select
                  v-model="act.deviceInstanceId"
                  size="small"
                  :clearable="deviceBindingsAreModelWide"
                  :placeholder="deviceBindingsAreModelWide ? '与观测同源' : '选择执行实例'"
                >
                  <el-option v-for="inst in instancesFor(act.deviceModelId)" :key="inst.id" :label="inst.instanceName || ('实例' + inst.id)" :value="inst.id" />
                </el-select>
              </div>
            </template>

            <div class="field field-grow">
              <span class="field-label">执行能力</span>
              <el-select v-model="act.capabilityName" size="small" filterable @change="initParameters(act)">
                <el-option v-for="cap in capabilitiesFor(act.deviceModelId)" :key="cap.capabilityName" :label="cap.displayName || cap.capabilityName" :value="cap.capabilityName" />
              </el-select>
            </div>
          </div>

          <!-- 第二行：执行能力参数 -->
          <div v-if="parameterDefinitions(act).length" class="action-device-params-row">
            <div v-for="param in parameterDefinitions(act)" :key="param.name" class="field field-param">
              <span class="field-label">{{ param.displayName }}</span>
              <el-select v-if="param.dataType === 'BOOLEAN'" v-model="act.parameters[param.name]" size="small">
                <el-option label="true" :value="true" />
                <el-option label="false" :value="false" />
              </el-select>
              <el-input-number v-else-if="isNumeric(param.dataType)" v-model="act.parameters[param.name]" size="small" :controls="false" />
              <el-input v-else v-model="act.parameters[param.name]" size="small" />
            </div>
          </div>
        </div>

        <!-- 系统动作 (比例均衡、无偏移排布) -->
        <div v-else class="action-system-row">
          <div class="field field-sys">
            <span class="field-label">系统处置</span>
            <el-select v-model="act.action" size="small">
              <el-option label="终止当前任务 (ABORT)" value="ABORT" />
              <el-option label="暂停当前任务 (PAUSE)" value="PAUSE" />
              <el-option label="发布系统告警 (ALERT)" value="ALERT" />
            </el-select>
          </div>
          <div v-if="!taskMode && act.action !== 'ALERT'" class="field field-task">
            <span class="field-label">{{ taskBindingsAreTemplateWide ? '目标任务（可选）' : '目标任务' }}</span>
            <el-select
              v-model="act.targetTaskId"
              size="small"
              filterable
              :clearable="taskBindingsAreTemplateWide"
              :placeholder="taskBindingsAreTemplateWide ? '与观测同源' : '指定触发违规的目标任务'"
            >
              <el-option v-for="task in tasks" :key="task.id" :label="(task.taskName || '任务') + ' #' + task.id" :value="task.id" />
            </el-select>
          </div>
          <div v-else-if="act.action === 'ALERT'" class="field field-desc">
            <span class="field-label">处置说明</span>
            <div class="action-hint-cell">触发违规时将在系统告警中心与顶部通知栏实时播报</div>
          </div>
          <div v-else class="field field-desc">
            <span class="field-label">处置说明</span>
            <div class="action-hint-cell">系统处置动作将自动作用于触发违规的当前任务</div>
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
    if (!item.targetName) return ''
    return attributesFor(item.deviceModelId).find((x: any) => x.attributeName === item.targetName)?.dataType || ''
  }
  if (item.sourceType === 'DEVICE_OPERATION_STATE') {
    if (!item.regionName) return ''
    return 'JSON'
  }
  if (item.sourceType === 'DEVICE_COMMAND_LIFECYCLE' || item.sourceType === 'TASK_LIFECYCLE_STATE' || item.sourceType === 'NODE_LIFECYCLE_STATE') {
    return 'STRING'
  }
  if (item.sourceType === 'NODE_INTERNAL_VARIABLE') {
    if (!item.variableName) return ''
    return internalVariablesFor(item).find((v: any) => v.name === item.variableName)?.dataType || ''
  }
  return ''
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
  const parameters = act.parameters || {}
  for (const param of parameterDefinitions(act)) {
    const value = parameters[param.name]
    if (value === null || value === undefined || value === '') {
      throw Error(`设备能力参数「${param.displayName || param.name}」不能为空`)
    }
  }
  const payload: Record<string, any> = {
    actionType: 'DEVICE_CAPABILITY',
    deviceModelId: Number(act.deviceModelId),
    capabilityName: act.capabilityName,
    parameters
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
/* 全局防毛边与抗锯齿渲染 (整框大卡片一体化，去除冗余嵌套卡片和外边距) */
.constraint-editor {
  display: flex;
  flex-direction: column;
  background: #ffffff;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-rendering: optimizeLegibility;
}

/* 1. 实时业务逻辑解析预览条 (纯净字阶 · 无胶囊 · 纯蓝字/纯红字直接呈现) */
.logic-preview-bar {
  background: var(--sl-bg-hover, #f8fafc);
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  padding: 6px 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  line-height: 1.4;
}

.logic-preview-label {
  color: var(--sl-primary, #2563eb);
  font-weight: 700;
  font-size: 11.5px;
  flex-shrink: 0;
}

.logic-preview-content {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.preview-static {
  color: var(--sl-text-secondary, #64748b);
}

.preview-arrow {
  color: var(--sl-text-disabled, #94a3b8);
  margin: 0 2px;
}

.action-separator {
  color: var(--sl-text-secondary, #64748b);
}

.text-blue-var {
  color: var(--sl-primary, #2563eb);
  font-family: var(--sl-font-mono);
  font-weight: 700;
}

.text-danger-act {
  color: var(--sl-danger, #dc2626);
  font-family: var(--sl-font-mono);
  font-weight: 700;
}

.text-duration-val {
  color: var(--sl-text-heading, #0f172a);
  font-family: var(--sl-font-mono);
  font-weight: 700;
}

/* 章节分段块 (边到边排布，无多余空白) */
.card-section-block {
  padding: 7px 14px;
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9);
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.card-section-block:last-child {
  border-bottom: none;
}

.section-head-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 2px;
}

.section-title {
  font-size: 12.5px;
  font-weight: 700;
  color: var(--sl-text-heading, #0f172a);
  display: flex;
  align-items: center;
  gap: 6px;
}
.section-title::before {
  content: "";
  width: 3px;
  height: 12px;
  background: var(--sl-primary, #2563eb);
  border-radius: 2px;
  display: inline-block;
}

.section-desc-hint {
  font-size: 11px;
  color: var(--sl-text-secondary, #64748b);
  font-weight: normal;
}

.section-actions {
  display: flex;
  gap: 6px;
}

/* 按钮规范 */
.btn-aliyun {
  height: 28px;
  padding: 0 12px;
  font-size: 12px;
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

.btn-aliyun.small {
  height: 24px;
  padding: 0 8px;
  font-size: 11px;
}

.btn-link-danger {
  border: none;
  background: transparent;
  color: var(--sl-danger, #dc2626);
  font-size: 11px;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 3px;
  transition: var(--sl-ease-smooth, all 0.18s ease);
}
.btn-link-danger:hover {
  text-decoration: underline;
}

/* 28px 高密度表单控件 */
.form-control-28 :deep(.el-input__wrapper) {
  height: 28px;
  padding: 0 8px;
  font-size: 11.5px;
  box-shadow: 0 0 0 1px var(--sl-border-input, #cbd5e1) inset;
  border-radius: var(--sl-radius-sm, 4px);
}
.form-control-28 :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--sl-primary, #2563eb) inset;
}

.form-lbl-compact {
  font-size: 11.5px;
  color: var(--sl-text-secondary, #64748b);
  white-space: nowrap;
}
.form-lbl-compact.required::before {
  content: "* ";
  color: var(--sl-danger, #dc2626);
  font-weight: bold;
}

.rule-info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.form-field-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.form-field-row .form-lbl-compact {
  width: 60px;
  text-align: right;
  flex-shrink: 0;
}
.form-field-row .form-control-28 {
  flex: 1;
}

.enable-inline {
  display: flex;
  align-items: center;
  gap: 6px;
}
.enable-label {
  font-size: 11px;
  color: var(--sl-text-secondary, #64748b);
}

/* 变量引导提示 */
.binding-guide {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 10px;
  padding: 6px 10px;
  background: #ffffff;
  border: 1px dashed var(--sl-border-input, #cbd5e1);
  border-radius: var(--sl-radius-sm, 4px);
  font-size: 11.5px;
  color: var(--sl-text-body, #334155);
}
.guide-step {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--sl-primary, #2563eb);
  color: #ffffff;
  font-size: 10px;
  font-weight: 700;
  flex-shrink: 0;
}

/* 变量声明行 (纯白底 · 无灰色底块) */
.var-decl-row {
  background: #ffffff;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 4px);
  padding: 6px 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.var-decl-row.unused {
  border-style: dashed;
}

.var-decl-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
}

.var-name-ident {
  display: flex;
  align-items: center;
  gap: 4px;
}
.at-prefix {
  font-family: var(--sl-font-mono);
  font-weight: 700;
  font-size: 12px;
  color: var(--sl-primary, #2563eb);
}
.var-name-input-clean {
  width: 100px;
}
.var-name-input-clean :deep(.el-input__wrapper) {
  padding: 0 6px;
  height: 24px;
  font-family: var(--sl-font-mono);
  font-weight: 600;
  font-size: 11.5px;
}
.identifier-type-desc {
  font-size: 11px;
  color: var(--sl-text-secondary, #64748b);
  margin-left: 4px;
}
.unused-hint {
  font-size: 10.5px;
  color: var(--sl-text-disabled, #94a3b8);
}
.used-hint {
  font-size: 10.5px;
  color: var(--sl-success, #16a34a);
  font-weight: 600;
}

.binding-head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 观测变量自适应对齐布局 (Flex + 统一 field 结构，杜绝垂直偏移) */
.var-grid-layout {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  width: 100%;
}
.var-grid-layout .field-source {
  width: 130px;
  flex-shrink: 0;
}
.var-grid-layout .field-grow {
  flex: 1;
  min-width: 120px;
}
.var-grid-layout .field-attr {
  flex: 1.2;
  min-width: 130px;
}
.var-grid-layout .field-type-view {
  width: 70px;
  flex-shrink: 0;
}

/* 固定值单行紧凑布局 (固定宽度，不无限向右拉伸) */
.literal-row-flex {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  width: 100%;
}
.literal-row-flex .field-type {
  width: 130px;
  flex-shrink: 0;
}
.literal-row-flex .field-value {
  width: 180px;
  flex-shrink: 0;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.field-label {
  font-size: 11px;
  color: var(--sl-text-secondary, #64748b);
  line-height: 14px;
}
.field :deep(.el-select),
.field :deep(.el-input),
.field :deep(.el-input-number) {
  width: 100%;
}
.field :deep(.el-input__wrapper),
.field :deep(.el-select__wrapper) {
  height: 28px;
  font-size: 11.5px;
}

/* 数据类型纯文本展示 (高度严格 28px 与输入框水平绝对对齐，与上方标题严格左对齐) */
.data-type-cell {
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  font-size: 11.5px;
  font-family: var(--sl-font-mono);
  font-weight: 600;
  color: var(--sl-text-secondary, #64748b);
  background: transparent;
  border: none;
  padding: 0 2px;
  box-sizing: border-box;
}

/* 公式工作台 */
.composer-plain-box {
  border: 1px solid var(--sl-border-input, #cbd5e1);
  border-radius: var(--sl-radius-sm, 4px);
  background: #ffffff;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.token-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  font-size: 11px;
}
.token-line-lbl {
  color: var(--sl-text-secondary, #64748b);
  font-size: 10.5px;
  width: 52px;
  flex-shrink: 0;
}

/* 浅蓝底浅蓝字：公式模板、时序函数、已声明变量 */
.btn-token-blue {
  height: 22px;
  padding: 0 7px;
  border-radius: 3px;
  border: 1px solid var(--sl-primary-border, #bfdbfe);
  background: var(--sl-primary-light, #eff6ff);
  color: var(--sl-primary, #2563eb);
  font-size: 11px;
  font-family: var(--sl-font-mono);
  font-weight: 600;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  transition: var(--sl-ease-smooth, all 0.18s ease);
}
.btn-token-blue:hover {
  background: #dbeafe;
  border-color: #93c5fd;
  color: #1d4ed8;
}
.btn-token-blue.fn {
  font-weight: 600;
}
.btn-token-blue.var {
  font-weight: 700;
}

/* 白底黑字：操作符 */
.btn-token-plain {
  height: 22px;
  padding: 0 6px;
  border-radius: 3px;
  border: 1px solid var(--sl-border-input, #cbd5e1);
  background: #ffffff;
  color: var(--sl-text-heading, #0f172a);
  font-size: 11px;
  font-family: var(--sl-font-mono);
  font-weight: 600;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  transition: var(--sl-ease-smooth, all 0.18s ease);
}
.btn-token-plain:hover {
  border-color: var(--sl-primary, #2563eb);
  color: var(--sl-primary, #2563eb);
  background: var(--sl-bg-hover, #f8fafc);
}

.picker-empty {
  font-size: 11px;
  color: var(--sl-text-disabled, #94a3b8);
}
.fn-tooltip {
  display: grid;
  gap: 4px;
  max-width: 260px;
}
.fn-tooltip strong {
  font-size: 12px;
}
.fn-tooltip span {
  font-size: 11.5px;
  line-height: 1.5;
}
.fn-tooltip code {
  font-family: var(--sl-font-mono);
  font-size: 11px;
  color: #93c5fd;
}

/* 高亮输入框 */
.formula-input-shell {
  position: relative;
  height: 32px;
  border: 1px solid var(--sl-border-input, #cbd5e1);
  border-radius: var(--sl-radius-sm, 4px);
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
  padding: 5px 8px;
  color: var(--sl-text-heading, #0f172a);
  font-family: var(--sl-font-mono);
  font-size: 12px;
  line-height: 20px;
  white-space: pre;
  text-rendering: geometricPrecision;
}
.formula-input-token {
  color: var(--sl-primary, #2563eb);
  font-weight: 700;
}
.formula-input-unbound {
  color: var(--sl-danger, #dc2626);
  font-weight: 700;
  text-decoration: underline;
}
.formula-plain-input {
  position: relative;
  z-index: 2;
  width: 100%;
  height: 30px;
  box-sizing: border-box;
  padding: 5px 8px;
  border: 0;
  background: transparent;
  color: transparent;
  -webkit-text-fill-color: transparent;
  caret-color: var(--sl-text-heading, #0f172a);
  font-family: var(--sl-font-mono);
  font-size: 12px;
  line-height: 20px;
  outline: 0;
}
.formula-plain-input::placeholder {
  color: var(--sl-text-disabled, #94a3b8);
  -webkit-text-fill-color: var(--sl-text-disabled, #94a3b8);
}
.formula-input-shell:focus-within {
  border-color: var(--sl-primary, #2563eb);
  box-shadow: 0 0 0 1px var(--sl-primary, #2563eb);
}

.formula-meta-bar {
  font-size: 11px;
  color: var(--sl-text-secondary, #64748b);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 2px;
}
.formula-meta-bar code {
  color: var(--sl-primary, #2563eb);
  font-family: var(--sl-font-mono);
}
.syntax-valid {
  color: var(--sl-success, #16a34a);
  font-size: 11px;
  font-weight: 600;
}
.syntax-error {
  color: var(--sl-danger, #dc2626);
  font-size: 11px;
  font-weight: 600;
}

/* 判定窗口与处置动作 (纯白底 · 无灰色底块) */
.window-duration-row {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #ffffff;
  padding: 6px 10px;
  border-radius: var(--sl-radius-sm, 4px);
  border: 1px solid var(--sl-border-base, #e2e8f0);
}
.window-hint {
  font-size: 11px;
  color: var(--sl-text-secondary, #64748b);
}

.action-decl-row {
  background: #ffffff;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 4px);
  padding: 6px 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 6px;
}
.action-decl-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.action-idx-title {
  font-size: 11.5px;
  font-weight: 700;
  color: var(--sl-text-heading, #0f172a);
}

/* 动作两行结构 */
.action-device-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.action-device-main-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 8px;
  align-items: center;
}
.action-device-params-row {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 8px;
  padding-top: 6px;
  border-top: 1px dashed var(--sl-border-base, #e2e8f0);
  margin-top: 2px;
}

/* 系统动作两列/三列平衡排布 (无偏移、对称对齐) */
.action-system-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  width: 100%;
}
.action-system-row .field-sys {
  width: 220px;
  flex-shrink: 0;
}
.action-system-row .field-task {
  width: 240px;
  flex-shrink: 0;
}
.action-system-row .field-desc {
  flex: 1;
  min-width: 200px;
}
.action-hint-cell {
  height: 28px;
  display: flex;
  align-items: center;
  padding: 0 10px;
  font-size: 11.5px;
  color: var(--sl-text-secondary, #64748b);
  background: var(--sl-bg-hover, #f8fafc);
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 4px);
  box-sizing: border-box;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
