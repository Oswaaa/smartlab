
<template>
  <div class="workflow-designer">
    <aside class="sidebar">
      <el-tabs v-model="sidebarTab" class="sidebar-tabs" stretch>
        <el-tab-pane label="节点组件库" name="palette">
          <div class="sidebar-scroll" v-loading="loadingBase">
            <div class="group-title">控制节点</div>
            <div class="logic-grid">
              <div
                v-for="item in logicTemplates"
                :key="item.kind"
                class="logic-item"
                draggable="true"
                @dragstart="onPaletteDragStart($event, item.kind)"
              >
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.label }}</span>
              </div>
            </div>

            <div class="group-title">设备模型节点</div>
            <div class="palette-list">
              <div
                v-for="model in deviceModels"
                :key="model.modelId"
                class="palette-item"
                draggable="true"
                @dragstart="onDeviceDragStart($event, model)"
              >
                <el-icon><Box /></el-icon>
                <div>
                  <div class="item-title">{{ model.modelName }}</div>
                  <div class="item-sub mono">{{ model.modelId }}</div>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="已有流程" name="workflows">
          <div class="sidebar-scroll" v-loading="loadingWorkflows">
            <div
              v-for="wf in workflows"
              :key="workflowId(wf)"
              :class="['workflow-item', { active: selectedWorkflowId === workflowId(wf) }]"
              @click="loadWorkflow(workflowId(wf))"
            >
              <div class="item-title">{{ workflowNameText(wf) }}</div>
              <div class="item-sub mono">{{ workflowId(wf) }}</div>
            </div>
            <el-empty v-if="workflows.length === 0" description="暂无流程" :image-size="60" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </aside>

    <section class="main">
      <div class="header">
        <div class="header-left">
          <el-input v-model="workflowName" placeholder="请输入流程名称" class="name-input" />
          <div class="protocol-strip">
            <span>执行切片</span>
            <span>节点 {{ nodes.length }}</span>
            <span>控制流 {{ interfaceEdgeCount }}</span>
            <span>数据流 {{ portEdgeCount }}</span>
            <span>Sigma: smartlab.signal.v1</span>
          </div>
        </div>
        <div class="header-actions">
          <el-button @click="clearCanvas">清空画布</el-button>
          <el-button type="primary" :loading="saving" @click="saveWorkflow">保存流程</el-button>
        </div>
      </div>

      <div class="canvas-wrap" ref="flowContainer" @drop="onDrop" @dragover.prevent>
        <VueFlow
          v-model:nodes="nodes"
          v-model:edges="edges"
          :snap-to-grid="true"
          :snap-grid="[20, 20]"
          :default-edge-options="{ type: 'smoothstep', markerEnd: MarkerType.ArrowClosed }"
          @connect="onConnect"
          @node-click="onNodeClick"
          @pane-click="onPaneClick"
        >
          <Background :gap="20" pattern-color="#d7dbe1" />
          <Controls position="bottom-right" />

          <template #node-custom="props">
            <div class="node-shell" :class="`kind-${props.data.kind}`">
              <Handle
                v-for="(h, idx) in getFlowInHandles(props.data.interfaces)"
                :key="`if_in_${h.interfaceId}`"
                :id="`if:${h.interfaceId}`"
                type="target"
                :position="Position.Top"
                class="node-handle flow-handle"
                :style="{ left: `${(idx + 1) * (100 / (getFlowInHandles(props.data.interfaces).length + 1))}%` }"
              />
              <Handle
                v-for="(h, idx) in getFlowOutHandles(props.data.interfaces)"
                :key="`if_out_${h.interfaceId}`"
                :id="`if:${h.interfaceId}`"
                type="source"
                :position="Position.Bottom"
                class="node-handle flow-handle"
                :style="{ left: `${(idx + 1) * (100 / (getFlowOutHandles(props.data.interfaces).length + 1))}%` }"
              />
              <Handle
                v-for="(p, idx) in getDataInPorts(props.data.ports)"
                :key="`port_in_${p.portId}`"
                :id="`port:${p.portId}`"
                type="target"
                :position="Position.Left"
                class="node-handle data-handle"
                :style="{ top: calcOffset(idx, getDataInPorts(props.data.ports).length) }"
              />
              <Handle
                v-for="(p, idx) in getDataOutPorts(props.data.ports)"
                :key="`port_out_${p.portId}`"
                :id="`port:${p.portId}`"
                type="source"
                :position="Position.Right"
                class="node-handle data-handle"
                :style="{ top: calcOffset(idx, getDataOutPorts(props.data.ports).length) }"
              />

              <div class="node-head">
                <span class="node-tag">{{ kindLabel(props.data.kind) }}</span>
                <span class="node-title">{{ props.data.name }}</span>
              </div>
              <div class="node-sub">{{ nodeSummary(props.data) }}</div>
              <div class="node-meta">
                <span>接口 {{ props.data.interfaces.length }}</span>
                <span>端口 {{ props.data.ports.length }}</span>
                <span v-if="props.data.kind === 'device'">信号 {{ props.data.functionId || '未配置' }}</span>
              </div>
            </div>
          </template>
        </VueFlow>
      </div>
    </section>

    <el-drawer v-model="drawerVisible" title="节点配置" size="620px">
      <div v-if="selectedNode" class="drawer-content">
        <el-form label-width="100px" size="small">
          <el-form-item label="节点名称"><el-input v-model="selectedNode.data.name" /></el-form-item>
          <el-form-item label="节点类型"><el-input :model-value="kindLabel(selectedNode.data.kind)" disabled /></el-form-item>
        </el-form>

        <el-tabs v-model="configTab" class="config-tabs">
          <el-tab-pane label="基础配置" name="basic">
            <el-form label-width="110px" size="small">
              <template v-if="selectedNode.data.kind === 'device'">
                <el-form-item label="设备模型">
                  <el-select v-model="selectedNode.data.modelId" style="width: 100%" @change="onModelChanged">
                    <el-option v-for="m in deviceModels" :key="m.modelId" :label="m.modelName" :value="m.modelId" />
                  </el-select>
                </el-form-item>
                <el-form-item label="执行能力">
                  <el-select v-model="selectedNode.data.functionId" style="width: 100%" @change="onFunctionChanged">
                    <el-option
                      v-for="op in selectedNodeOps"
                      :key="capabilityOptionValue(op)"
                      :label="capabilityOptionLabel(op)"
                      :value="capabilityOptionValue(op)"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="协议预览">
                  <div class="signal-preview">
                    <div><span>Envelope</span><b>smartlab.signal.v1</b></div>
                    <div><span>输入接口</span><b>if_wf_cmd_in</b></div>
                    <div><span>信号类型</span><b>{{ selectedNode.data.functionId || '未选择' }}</b></div>
                    <div><span>目标命令</span><b>{{ selectedCommandId || '-' }}</b></div>
                  </div>
                </el-form-item>
                <el-form-item label="能力参数">
                  <el-table :data="selectedNodeParamRows" border size="small" style="width: 100%">
                    <el-table-column prop="key" label="参数" width="180" />
                    <el-table-column label="值">
                      <template #default="{ row }"><el-input v-model="selectedNode.data.parameters[row.key]" /></template>
                    </el-table-column>
                  </el-table>
                </el-form-item>
              </template>

              <template v-else-if="selectedNode.data.kind === 'branch'">
                <el-form-item label="条件出口">
                  <div class="condition-list">
                    <div v-for="(c, index) in selectedNode.data.conditions" :key="c.interfaceId" class="condition-row branch-condition-row">
                      <el-input v-model="c.label" placeholder="出口名称" style="width: 110px" />
                      <el-input v-model="c.subject" placeholder="变量路径，如 temperature" :disabled="c.isDefault" />
                      <el-select v-model="c.operator" style="width: 100px" :disabled="c.isDefault">
                        <el-option v-for="operator in workflowOperators" :key="operator" :label="operator" :value="operator" />
                      </el-select>
                      <el-input v-model="c.threshold" placeholder="阈值；数组使用 JSON" :disabled="c.isDefault" />
                      <el-checkbox v-model="c.isDefault">默认</el-checkbox>
                      <el-button link type="danger" :disabled="selectedNode.data.conditions.length <= 2" @click="removeBranchCondition(index)">删除</el-button>
                    </div>
                    <el-button size="small" @click="addBranchCondition">新增出口</el-button>
                  </div>
                </el-form-item>
              </template>

              <template v-else-if="selectedNode.data.kind === 'subflow'">
                <el-form-item label="子流程">
                  <el-select v-model="selectedNode.data.subFlowModelId" style="width: 100%" filterable>
                    <el-option v-for="wf in workflows" :key="workflowId(wf)" :label="workflowNameText(wf)" :value="Number(workflowId(wf))" />
                  </el-select>
                </el-form-item>
              </template>
            </el-form>
          </el-tab-pane>
          <el-tab-pane label="内部动作" name="actions">
            <WorkflowActionEditor
              v-model="selectedNode.data.actions"
              :node-type="selectedNode.data.nodeType"
              :catalog="workflowActionCatalog"
              :calculation-operators="workflowCalculationOperators"
              :workflow-control-signals="workflowControlSignals"
            />
          </el-tab-pane>
          <el-tab-pane label="端口与变量" name="ports">
            <el-divider content-position="left">内部变量</el-divider>
            <div class="table-actions"><el-button size="small" @click="addInternalVariable">新增变量</el-button></div>
            <el-table :data="selectedNode.data.internalVariables" border size="small" style="width: 100%">
              <el-table-column label="变量名">
                <template #default="{ row }"><el-input v-model="row.name" /></template>
              </el-table-column>
              <el-table-column label="类型" width="140">
                <template #default="{ row }">
                  <el-select v-model="row.dataType" style="width: 100%">
                    <el-option label="字符串" value="string" />
                    <el-option label="数字" value="number" />
                    <el-option label="布尔" value="boolean" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="映射" width="180">
                <template #default="{ row }"><el-input v-model="row.mapping" placeholder="可选" /></template>
              </el-table-column>
              <el-table-column label="操作" width="80">
                <template #default="{ $index }"><el-button link type="danger" @click="removeInternalVariable($index)">删除</el-button></template>
              </el-table-column>
            </el-table>

            <el-divider content-position="left">数据端口</el-divider>
            <div class="table-actions"><el-button size="small" @click="addPort">新增端口</el-button></div>
            <el-table :data="selectedNode.data.ports" border size="small" style="width: 100%">
              <el-table-column label="端口ID" width="150">
                <template #default="{ row }"><el-input v-model="row.portId" /></template>
              </el-table-column>
              <el-table-column label="方向" width="110">
                <template #default="{ row }">
                  <el-select v-model="row.direction" style="width: 100%">
                    <el-option label="输入" value="IN" />
                    <el-option label="输出" value="OUT" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="类型" width="120">
                <template #default="{ row }">
                  <el-select v-model="row.dataType" style="width: 100%">
                    <el-option label="字符串" value="string" />
                    <el-option label="数字" value="number" />
                    <el-option label="布尔" value="boolean" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="绑定变量">
                <template #default="{ row }">
                  <el-select v-model="row.variableBinding" style="width: 100%" clearable>
                    <el-option v-for="v in selectedNode.data.internalVariables" :key="v.name" :label="v.name" :value="v.name" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80">
                <template #default="{ $index }"><el-button link type="danger" @click="removePort($index)">删除</el-button></template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="接口与触发" name="interfaces">
            <div class="table-actions"><el-button size="small" @click="addInterface">新增接口</el-button></div>
            <el-table :data="selectedNode.data.interfaces" border size="small" style="width: 100%">
              <el-table-column label="接口ID" width="140"><template #default="{ row }"><el-input v-model="row.interfaceId" /></template></el-table-column>
              <el-table-column label="方向" width="100">
                <template #default="{ row }">
                  <el-select v-model="row.direction" style="width: 100%">
                    <el-option label="输入" value="IN" />
                    <el-option label="输出" value="OUT" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="信号" width="120">
                <template #default="{ row }">
                  <el-select v-model="row.signalType" style="width: 100%">
                    <el-option v-for="signal in workflowSignals" :key="signal" :label="signal" :value="signal" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="触发方式" width="120">
                <template #default="{ row }">
                  <el-select v-model="row.triggerMode" style="width: 100%">
                    <el-option label="总是触发" value="ALWAYS" />
                    <el-option label="按信号" value="SIGNAL" />
                    <el-option label="按表达式" value="EXPRESSION" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="触发条件">
                <template #default="{ row }"><el-input v-model="row.triggerExpr" :disabled="row.triggerMode !== 'EXPRESSION'" /></template>
              </el-table-column>
              <el-table-column label="操作" width="80"><template #default="{ $index }"><el-button link type="danger" @click="removeInterface($index)">删除</el-button></template></el-table-column>
            </el-table>
            <div class="hint">控制流接口用于流程推进；数据端口用于节点间数据交换。两类连接会分别入库。</div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { Box, CircleCheck, Connection, Share, VideoPlay } from '@element-plus/icons-vue'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { Handle, MarkerType, Position, VueFlow, useVueFlow } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import WorkflowActionEditor from './components/WorkflowActionEditor.vue'
import { defaultActionsForNode, validateAndOrderActions } from './workflowActions.js'

type DeviceModel = {
  modelId: string
  modelName: string
  capabilitySpec?: {
    operations?: Array<{ name: string; description?: string; parameters?: Record<string, any> | Array<{ name?: string; paramName?: string; id?: string }> }>
    capabilities?: Array<{
      capabilityId: string
      adapterCommandId: string
      name: string
      displayName?: string
      parameters?: Array<{ id?: string; name?: string; displayName?: string }>
    }>
  }
}

type FlowNodeKind = 'start' | 'end' | 'branch' | 'aggregate' | 'subflow' | 'device'
type NodeVariable = { name: string; dataType: 'string' | 'number' | 'boolean'; mapping: string }
type NodePort = { portId: string; direction: 'IN' | 'OUT'; dataType: 'string' | 'number' | 'boolean'; variableBinding: string }
type NodeInterface = {
  interfaceId: string
  direction: 'IN' | 'OUT'
  signalType: string
  triggerMode: 'ALWAYS' | 'SIGNAL' | 'EXPRESSION'
  triggerExpr: string
}

type WorkflowNodeData = {
  kind: FlowNodeKind
  name: string
  nodeType: 'DEVICE_CAPABILITY_NODE' | 'FUNCTIONAL_NODE' | 'SUB_FLOW_NODE'
  modelId: string
  functionId: string
  parameters: Record<string, any>
  subFlowModelId: number | null
  conditions: Array<{ label: string; subject: string; operator: string; threshold: string; interfaceId: string; isDefault: boolean }>
  interfaces: NodeInterface[]
  ports: NodePort[]
  internalVariables: NodeVariable[]
  actions: Array<{ actionName: string; payload: Record<string, any> }>
}

const sidebarTab = ref('palette')
const configTab = ref('basic')
const loadingBase = ref(false)
const loadingWorkflows = ref(false)
const saving = ref(false)

const workflowName = ref('')
const selectedWorkflowId = ref('')
const deviceModels = ref<DeviceModel[]>([])
const workflows = ref<any[]>([])
const flowContainer = ref<HTMLElement | null>(null)
const nodes = ref<any[]>([])
const edges = ref<any[]>([])
const selectedNodeId = ref('')
let nextNodeIdRef = Date.now() * 1000
const allocateNodeIdRef = () => String(++nextNodeIdRef)
const drawerVisible = ref(false)

const { project, addEdges } = useVueFlow()

const workflowFunctionTypes = ref<string[]>([])
const workflowSignals = ref<string[]>([])
const workflowOperators = ref<string[]>([])
const workflowActionCatalog = ref<any[]>([])
const workflowCalculationOperators = ref<string[]>([])
const workflowControlSignals = ref<string[]>([])
const logicTemplateCatalog = [
  { functionType: 'START', kind: 'start' as FlowNodeKind, label: '开始', icon: VideoPlay },
  { functionType: 'END', kind: 'end' as FlowNodeKind, label: '结束', icon: CircleCheck },
  { functionType: 'BRANCH', kind: 'branch' as FlowNodeKind, label: '分支', icon: Share },
  { functionType: 'AGGREGATE', kind: 'aggregate' as FlowNodeKind, label: '聚合', icon: Connection }
]
const logicTemplates = computed(() => [
  ...logicTemplateCatalog.filter(item => workflowFunctionTypes.value.includes(item.functionType)),
  { functionType: 'SUB_FLOW', kind: 'subflow' as FlowNodeKind, label: '子流程', icon: Connection }
])

const selectedNode = computed(() => nodes.value.find(n => n.id === selectedNodeId.value) || null)
const modelMap = computed(() => {
  const map: Record<string, DeviceModel> = {}
  deviceModels.value.forEach(item => (map[item.modelId] = item))
  return map
})
const selectedNodeOps = computed(() => {
  if (!selectedNode.value?.data?.modelId) return [] as Array<{ name: string; parameters?: any }>
  const spec = modelMap.value[selectedNode.value.data.modelId]?.capabilitySpec || {}
  const caps = spec.capabilities || []
  if (caps.length > 0) return caps.map((cap: any) => ({
    name: cap.capabilityId || cap.name,
    description: cap.displayName || cap.name,
    adapterCommandId: cap.adapterCommandId,
    parameters: cap.parameters || []
  }))
  return spec.operations || []
})
const selectedNodeParamRows = computed(() => {
  if (!selectedNode.value?.data?.functionId) return [] as Array<{ key: string }>
  const op = selectedNodeOps.value.find((item: any) => capabilityOptionValue(item) === selectedNode.value?.data?.functionId)
  if (!op?.parameters) return []
  if (Array.isArray(op.parameters)) return op.parameters.map((p: any) => ({ key: p.name || p.paramName || p.id || '' })).filter(v => v.key)
  return Object.keys(op.parameters).map(key => ({ key }))
})
const selectedCommandId = computed(() => {
  const op = selectedNodeOps.value.find((item: any) => capabilityOptionValue(item) === selectedNode.value?.data?.functionId) as any
  return op?.adapterCommandId || op?.name || ''
})

const interfaceEdgeCount = computed(() => edges.value.filter((e: any) => String(e.sourceHandle || '').startsWith('if:')).length)
const portEdgeCount = computed(() => edges.value.filter((e: any) => String(e.sourceHandle || '').startsWith('port:')).length)

const workflowId = (wf: any) => wf.id || ''
const workflowNameText = (wf: any) => wf.flowName || wf.name || '未命名流程'

const kindLabel = (kind: FlowNodeKind) => {
  if (kind === 'start') return '开始'
  if (kind === 'end') return '结束'
  if (kind === 'branch') return '分支'
  if (kind === 'aggregate') return '聚合'
  if (kind === 'subflow') return '子流程'
  if (kind === 'device') return '设备'
  return '设备'
}

const nodeSummary = (data: WorkflowNodeData) => {
  if (data.kind === 'device') return `${modelMap.value[data.modelId]?.modelName || '未选模型'} / ${data.functionId || '未选能力'}`
  if (data.kind === 'branch') return `条件出口 ${data.conditions.length}`
  if (data.kind === 'subflow') return workflows.value.find(wf => Number(workflowId(wf)) === data.subFlowModelId)?.flowName || '未选择子流程'
  return '流程控制'
}

const capabilityOptionValue = (op: any) => op.capabilityId || op.name || op.commandId || ''
const capabilityOptionLabel = (op: any) => {
  const value = capabilityOptionValue(op)
  const command = op.adapterCommandId ? ` -> ${op.adapterCommandId}` : ''
  return `${op.displayName || op.description || op.name || value}${command}`
}

const getFlowInHandles = (interfaces: NodeInterface[] = []) => interfaces.filter(i => i.direction === 'IN')
const getFlowOutHandles = (interfaces: NodeInterface[] = []) => interfaces.filter(i => i.direction === 'OUT')
const getDataInPorts = (ports: NodePort[] = []) => ports.filter(p => p.direction === 'IN')
const getDataOutPorts = (ports: NodePort[] = []) => ports.filter(p => p.direction === 'OUT')
const calcOffset = (index: number, total: number) => `${(index + 1) * (100 / (total + 1))}%`

const defaultInterfacesByKind = (kind: FlowNodeKind): NodeInterface[] => {
  if (kind === 'start') return [{ interfaceId: 'flow_out', direction: 'OUT', signalType: 'STARTED', triggerMode: 'ALWAYS', triggerExpr: '' }]
  if (kind === 'end') return [{ interfaceId: 'flow_in', direction: 'IN', signalType: 'COMPLETED', triggerMode: 'SIGNAL', triggerExpr: '' }]
  if (kind === 'branch') return [
    { interfaceId: 'flow_in', direction: 'IN', signalType: 'COMPLETED', triggerMode: 'SIGNAL', triggerExpr: '' },
    { interfaceId: 'flow_yes', direction: 'OUT', signalType: 'COMPLETED', triggerMode: 'SIGNAL', triggerExpr: '' },
    { interfaceId: 'flow_default', direction: 'OUT', signalType: 'COMPLETED', triggerMode: 'ALWAYS', triggerExpr: '' }
  ]
  return [
    { interfaceId: 'flow_in', direction: 'IN', signalType: 'STARTED', triggerMode: 'SIGNAL', triggerExpr: '' },
    { interfaceId: 'flow_out', direction: 'OUT', signalType: 'COMPLETED', triggerMode: 'ALWAYS', triggerExpr: '' }
  ]
}
const defaultPortsByKind = (kind: FlowNodeKind): NodePort[] => kind === 'device'
  ? [{ portId: 'data_in', direction: 'IN', dataType: 'number', variableBinding: '' }, { portId: 'data_out', direction: 'OUT', dataType: 'number', variableBinding: '' }]
  : []
const defaultVariablesByKind = (_kind: FlowNodeKind): NodeVariable[] => []

const createNodeData = (kind: FlowNodeKind, model?: DeviceModel): WorkflowNodeData => {
  if (kind === 'device') return {
    kind, name: `${model?.modelName || '设备'}节点`, nodeType: 'DEVICE_CAPABILITY_NODE', modelId: model?.modelId || '', functionId: '', parameters: {}, subFlowModelId: null,
    conditions: [], interfaces: defaultInterfacesByKind(kind), ports: defaultPortsByKind(kind), internalVariables: defaultVariablesByKind(kind),
    actions: defaultActionsForNode('DEVICE_CAPABILITY_NODE', workflowActionCatalog.value, workflowControlSignals.value)
  }
  if (kind === 'branch') return {
    kind, name: '分支节点', nodeType: 'FUNCTIONAL_NODE', modelId: '', functionId: 'BRANCH', parameters: {}, subFlowModelId: null,
    conditions: [
      { label: '满足条件', subject: '', operator: '=', threshold: 'true', interfaceId: 'flow_yes', isDefault: false },
      { label: '默认出口', subject: '', operator: '=', threshold: '', interfaceId: 'flow_default', isDefault: true }
    ],
    interfaces: defaultInterfacesByKind(kind), ports: defaultPortsByKind(kind), internalVariables: defaultVariablesByKind(kind), actions: []
  }
  if (kind === 'subflow') return { kind, name: '子流程节点', nodeType: 'SUB_FLOW_NODE', modelId: '', functionId: '', parameters: {}, subFlowModelId: null, conditions: [], interfaces: defaultInterfacesByKind(kind), ports: [], internalVariables: [], actions: [] }
  return { kind, name: `${kindLabel(kind)}节点`, nodeType: 'FUNCTIONAL_NODE', modelId: '', functionId: kind.toUpperCase(), parameters: {}, subFlowModelId: null, conditions: [], interfaces: defaultInterfacesByKind(kind), ports: defaultPortsByKind(kind), internalVariables: defaultVariablesByKind(kind), actions: [] }
}

const onPaletteDragStart = (event: DragEvent, kind: FlowNodeKind) => {
  if (!event.dataTransfer) return
  event.dataTransfer.setData('nodeKind', kind)
}
const onDeviceDragStart = (event: DragEvent, model: DeviceModel) => {
  if (!event.dataTransfer) return
  event.dataTransfer.setData('nodeKind', 'device')
  event.dataTransfer.setData('deviceModel', JSON.stringify(model))
}

const onDrop = (event: DragEvent) => {
  const kind = (event.dataTransfer?.getData('nodeKind') || '') as FlowNodeKind
  if (!kind || !flowContainer.value) return
  const bounds = flowContainer.value.getBoundingClientRect()
  const position = project({ x: event.clientX - bounds.left, y: event.clientY - bounds.top })

  let model: DeviceModel | undefined
  if (kind === 'device') {
    const raw = event.dataTransfer?.getData('deviceModel')
    if (raw) {
      try { model = JSON.parse(raw) } catch { model = undefined }
    }
  }

  nodes.value.push({
    id: allocateNodeIdRef(),
    type: 'custom',
    position,
    data: createNodeData(kind, model)
  })
}

const onConnect = (params: any) => {
  const sourceHandle = String(params.sourceHandle || '')
  const targetHandle = String(params.targetHandle || '')
  const sourceIsInterface = sourceHandle.startsWith('if:')
  const targetIsInterface = targetHandle.startsWith('if:')
  const sourceIsPort = sourceHandle.startsWith('port:')
  const targetIsPort = targetHandle.startsWith('port:')

  if ((sourceIsInterface && !targetIsInterface) || (sourceIsPort && !targetIsPort)) {
    ElMessage.warning('控制流接口和数据端口不能混连')
    return
  }
  if (params.source === params.target) {
    ElMessage.warning('节点不能自连接')
    return
  }

  addEdges([{
    ...params,
    id: `edge_${Date.now()}_${Math.floor(Math.random() * 1000)}`,
    type: 'smoothstep',
    animated: sourceIsInterface,
    style: {
      stroke: sourceIsInterface ? '#6b7280' : '#8f6d2a',
      strokeWidth: 2,
      strokeDasharray: sourceIsInterface ? 'none' : '5,4'
    },
    markerEnd: MarkerType.ArrowClosed
  }])
}

const onNodeClick = ({ node }: any) => {
  selectedNodeId.value = node.id
  configTab.value = 'basic'
  drawerVisible.value = true
}
const onPaneClick = () => {
  drawerVisible.value = false
  selectedNodeId.value = ''
}

const onModelChanged = () => {
  if (!selectedNode.value) return
  selectedNode.value.data.functionId = ''
  selectedNode.value.data.parameters = {}
}
const onFunctionChanged = () => {
  if (!selectedNode.value) return
  const op = selectedNodeOps.value.find((item: any) => capabilityOptionValue(item) === selectedNode.value.data.functionId)
  const params: Record<string, any> = {}
  if (op?.parameters) {
    if (Array.isArray(op.parameters)) op.parameters.forEach((p: any) => { const key = p.name || p.paramName || p.id; if (key) params[key] = '' })
    else Object.keys(op.parameters).forEach(key => { params[key] = '' })
  }
  selectedNode.value.data.parameters = params
}

const addInternalVariable = () => {
  selectedNode.value?.data.internalVariables.push({ name: '', dataType: 'string', mapping: '' })
}
const removeInternalVariable = (index: number) => {
  selectedNode.value?.data.internalVariables.splice(index, 1)
}
const addPort = () => {
  selectedNode.value?.data.ports.push({ portId: `port_${Date.now().toString().slice(-4)}`, direction: 'IN', dataType: 'string', variableBinding: '' })
}
const removePort = (index: number) => {
  if (!selectedNode.value) return
  const [removed] = selectedNode.value.data.ports.splice(index, 1)
  edges.value = edges.value.filter((edge: any) => !(edge.source === selectedNode.value.id && edge.sourceHandle === `port:${removed.portId}`) && !(edge.target === selectedNode.value.id && edge.targetHandle === `port:${removed.portId}`))
}
const addInterface = () => {
  selectedNode.value?.data.interfaces.push({ interfaceId: `flow_${Date.now().toString().slice(-4)}`, direction: 'OUT', signalType: 'COMPLETED', triggerMode: 'ALWAYS', triggerExpr: '' })
}
const addBranchCondition = () => {
  if (!selectedNode.value) return
  const suffix = Date.now().toString().slice(-5)
  selectedNode.value.data.conditions.push({ label: '条件出口', subject: '', operator: '=', threshold: '', interfaceId: `flow_${suffix}`, isDefault: false })
  selectedNode.value.data.interfaces.push({ interfaceId: `flow_${suffix}`, direction: 'OUT', signalType: 'COMPLETED', triggerMode: 'SIGNAL', triggerExpr: '' })
}
const removeBranchCondition = (index: number) => {
  if (!selectedNode.value || selectedNode.value.data.conditions.length <= 2) return
  const [removed] = selectedNode.value.data.conditions.splice(index, 1)
  const interfaceIndex = selectedNode.value.data.interfaces.findIndex((item: NodeInterface) => item.interfaceId === removed.interfaceId)
  if (interfaceIndex >= 0) selectedNode.value.data.interfaces.splice(interfaceIndex, 1)
  edges.value = edges.value.filter((edge: any) =>
    !(edge.source === selectedNode.value.id && edge.sourceHandle === `if:${removed.interfaceId}`)
    && !(edge.target === selectedNode.value.id && edge.targetHandle === `if:${removed.interfaceId}`))
}
const removeInterface = (index: number) => {
  if (!selectedNode.value) return
  const [removed] = selectedNode.value.data.interfaces.splice(index, 1)
  edges.value = edges.value.filter((edge: any) => !(edge.source === selectedNode.value.id && edge.sourceHandle === `if:${removed.interfaceId}`) && !(edge.target === selectedNode.value.id && edge.targetHandle === `if:${removed.interfaceId}`))
}
const clearCanvas = () => {
  nodes.value = []
  edges.value = []
  drawerVisible.value = false
  selectedNodeId.value = ''
}

const parseThreshold = (raw: string) => {
  const value = String(raw ?? '').trim()
  if (!value) return ''
  if (value === 'true') return true
  if (value === 'false') return false
  if (/^-?\d+(\.\d+)?$/.test(value)) return Number(value)
  if (value.startsWith('[') || value.startsWith('{')) {
    try { return JSON.parse(value) } catch { return value }
  }
  return value
}

const buildPayload = () => {
  const nodesDef = nodes.value.map((n: any) => {
    const interfaces = (n.data.interfaces || []).filter((it: NodeInterface) => it.interfaceId).map((it: NodeInterface) => ({
      name: it.interfaceId,
      direction: it.direction,
      interfaceType: 'SIGNAL',
      allowedSignals: [it.signalType]
    }))
    const ports = (n.data.ports || []).filter((p: NodePort) => p.portId).map((p: NodePort) => ({
      name: p.portId,
      direction: p.direction,
      dataType: ({ string: 'STRING', number: 'DOUBLE', boolean: 'BOOLEAN' } as Record<string, string>)[p.dataType],
      internalVariableName: p.variableBinding || ''
    }))
    const internalVariables = (n.data.internalVariables || []).filter((v: NodeVariable) => v.name).map((v: NodeVariable) => ({
      name: v.name,
      dataType: ({ string: 'STRING', number: 'DOUBLE', boolean: 'BOOLEAN' } as Record<string, string>)[v.dataType],
      attributesMapping: v.mapping || ''
    }))

    let capability: Record<string, any>
    if (n.data.kind === 'device') capability = {
      displayName: n.data.name,
      deviceModelRef: Number(n.data.modelId),
      capabilityRef: n.data.functionId,
      parameters: { ...n.data.parameters },
      bindingPolicy: 'TASK_RESOURCE_MAP'
    }
    else if (n.data.kind === 'branch') capability = {
      displayName: n.data.name,
      functionType: 'BRANCH',
      branches: n.data.conditions.map((condition: WorkflowNodeData['conditions'][number]) => ({
        interfaceName: condition.interfaceId,
        ...(condition.isDefault
          ? { isDefault: true }
          : { condition: { subject: condition.subject, operator: condition.operator, threshold: parseThreshold(condition.threshold) } })
      }))
    }
    else if (n.data.kind === 'aggregate') capability = { displayName: n.data.name, functionType: 'AGGREGATE', aggregationPolicy: 'ALL' }
    else if (n.data.kind === 'subflow') capability = { displayName: n.data.name }
    else capability = { displayName: n.data.name, functionType: n.data.kind.toUpperCase() }

    return {
      nodeIdRef: Number(n.id),
      name: n.data.name,
      nodeType: n.data.nodeType,
      ...(n.data.kind === 'subflow' ? { subFlowModelId: Number(n.data.subFlowModelId) } : {}),
      capability,
      internalVariables,
      lifecycle: { initialStateName: 'PENDING', states: ['PENDING', 'RUNNING', 'COMPLETED', 'FAILED'] },
      interfaces,
      ports,
      actions: validateAndOrderActions(n.data.actions || [], n.data.nodeType, workflowActionCatalog.value)
    }
  })

  const interfaceConnections = edges.value
    .filter((e: any) => String(e.sourceHandle || '').startsWith('if:') && String(e.targetHandle || '').startsWith('if:'))
    .map((e: any, index: number) => ({
      connectionId: `conn_if_${index + 1}`,
      connectionType: 'NODE_TO_NODE',
      source: { nodeIdRef: Number(e.source), interfaceName: String(e.sourceHandle).replace('if:', '') },
      target: { nodeIdRef: Number(e.target), interfaceName: String(e.targetHandle).replace('if:', '') }
    }))
  const portConnections = edges.value
    .filter((e: any) => String(e.sourceHandle || '').startsWith('port:') && String(e.targetHandle || '').startsWith('port:'))
    .map((e: any, index: number) => ({
      connectionId: `conn_port_${index + 1}`,
      source: { nodeIdRef: Number(e.source), portName: String(e.sourceHandle).replace('port:', '') },
      target: { nodeIdRef: Number(e.target), portName: String(e.targetHandle).replace('port:', '') }
    }))

  return {
    ...(selectedWorkflowId.value ? { id: Number(selectedWorkflowId.value) } : {}),
    name: workflowName.value,
    nodesDef,
    interfaceConnections,
    portConnections
  }
}

const saveWorkflow = async () => {
  if (!workflowName.value.trim()) return ElMessage.warning('请输入流程名称')
  if (nodes.value.length === 0) return ElMessage.warning('请先添加节点')

  saving.value = true
  try {
    const payload = buildPayload()
    const res = await axios.post('/api/workflow/save', payload)
    if (res.data?.success) {
      selectedWorkflowId.value = String(res.data.data?.workflowId || selectedWorkflowId.value)
      ElMessage.success('流程保存成功')
      await fetchWorkflows()
    } else {
      ElMessage.error(res.data?.message || '流程保存失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '流程保存失败')
  } finally {
    saving.value = false
  }
}

const loadWorkflow = async (templateId: string) => {
  selectedWorkflowId.value = templateId
  try {
    const res = await axios.get(`/api/workflow/detail/${templateId}`)
    if (!res.data?.success || !res.data?.data) return ElMessage.error(res.data?.message || '加载流程失败')
    const tpl = res.data.data
    workflowName.value = tpl.name || ''
    const loadedNodes = (tpl.nodesDef || []).map((n: any, index: number) => {
      const capability = n.capability || {}
      const functionType = capability.functionType || ''
      const kind: FlowNodeKind = n.nodeType === 'DEVICE_CAPABILITY_NODE' ? 'device'
        : n.nodeType === 'SUB_FLOW_NODE' ? 'subflow'
        : functionType === 'START' ? 'start'
        : functionType === 'END' ? 'end'
        : functionType === 'BRANCH' ? 'branch' : 'aggregate'
      const rawInterfaces = (n.interfaces || []).map((item: any) => ({
        interfaceId: item.name || '', direction: item.direction || 'IN',
        signalType: item.allowedSignals?.[0] || 'COMPLETED', triggerMode: 'SIGNAL', triggerExpr: ''
      }))
      const rawPorts = (n.ports || []).map((port: any) => ({
        portId: port.name || '', direction: port.direction || 'IN',
        dataType: port.dataType === 'BOOLEAN' ? 'boolean' : port.dataType === 'STRING' ? 'string' : 'number',
        variableBinding: port.internalVariableName || ''
      }))
      const rawVariables = (n.internalVariables || []).map((variable: any) => ({
        name: variable.name || '', dataType: variable.dataType === 'BOOLEAN' ? 'boolean' : variable.dataType === 'STRING' ? 'string' : 'number',
        mapping: variable.attributesMapping || ''
      }))
      const conditions = kind === 'branch' ? (capability.branches || []).map((branch: any, branchIndex: number) => ({
        label: branch.isDefault ? '默认出口' : `条件出口 ${branchIndex + 1}`,
        subject: branch.condition?.subject || '', operator: branch.condition?.operator || '=',
        threshold: typeof branch.condition?.threshold === 'object' ? JSON.stringify(branch.condition.threshold) : String(branch.condition?.threshold ?? ''),
        interfaceId: branch.interfaceName, isDefault: Boolean(branch.isDefault)
      })) : []
      return {
        id: String(n.nodeIdRef), type: 'custom',
        position: { x: 120 + (index % 4) * 260, y: 80 + Math.floor(index / 4) * 150 },
        data: {
          kind, name: n.name || capability.displayName || kindLabel(kind), nodeType: n.nodeType,
          modelId: capability.deviceModelRef ? String(capability.deviceModelRef) : '',
          functionId: capability.capabilityRef || '', parameters: capability.parameters || {},
          subFlowModelId: n.subFlowModelId || null, conditions,
          interfaces: rawInterfaces.length ? rawInterfaces : defaultInterfacesByKind(kind),
          ports: rawPorts.length ? rawPorts : defaultPortsByKind(kind),
          internalVariables: rawVariables,
          actions: Array.isArray(n.actions) ? structuredClone(n.actions) : defaultActionsForNode(n.nodeType, workflowActionCatalog.value, workflowControlSignals.value)
        }
      }
    })
    nodes.value = loadedNodes
    nextNodeIdRef = Math.max(nextNodeIdRef, ...loadedNodes.map((node: any) => Number(node.id) || 0))
    const ifEdges = (tpl.interfaceConnections || []).map((connection: any, index: number) => ({
      id: `edge_if_${index}`, source: String(connection.source.nodeIdRef), sourceHandle: `if:${connection.source.interfaceName}`,
      target: String(connection.target.nodeIdRef), targetHandle: `if:${connection.target.interfaceName}`,
      type: 'smoothstep', animated: true, style: { stroke: '#6b7280', strokeWidth: 2 }, markerEnd: MarkerType.ArrowClosed
    }))
    const portEdges = (tpl.portConnections || []).map((connection: any, index: number) => ({
      id: `edge_port_${index}`, source: String(connection.source.nodeIdRef), sourceHandle: `port:${connection.source.portName}`,
      target: String(connection.target.nodeIdRef), targetHandle: `port:${connection.target.portName}`,
      type: 'smoothstep', animated: false, style: { stroke: '#8f6d2a', strokeWidth: 2, strokeDasharray: '5,4' }, markerEnd: MarkerType.ArrowClosed
    }))
    edges.value = [...ifEdges, ...portEdges]
    drawerVisible.value = false
    selectedNodeId.value = ''
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '加载流程失败')
  }
}

const fetchBaseData = async () => {
  loadingBase.value = true
  try {
    const [modelRes, metadataRes] = await Promise.all([
      axios.get('/api/device/model/list'),
      axios.get('/api/schema-metadata/frontend')
    ])
    if (modelRes.data?.success) deviceModels.value = modelRes.data.data || []
    const metadata = metadataRes.data?.data || metadataRes.data || {}
    const functionTypes = metadata.workflow?.functionTypes
    const lifecycleEvents = metadata.workflow?.nodeLifecycleEvents
    const operators = metadata.constraint?.operators
    const actionCatalog = metadata.workflow?.actionCatalog
    const calculationOperators = metadata.workflow?.calculationOperators
    const workflowInterface = (metadata.stateMachine?.standardInterfaces || [])
      .find((item: any) => item.name === 'Interface_workflow_in')
    if (!Array.isArray(functionTypes) || functionTypes.length === 0
      || !Array.isArray(lifecycleEvents) || lifecycleEvents.length === 0
      || !Array.isArray(operators) || operators.length === 0
      || !Array.isArray(actionCatalog) || actionCatalog.length === 0
      || !Array.isArray(calculationOperators) || calculationOperators.length === 0
      || !Array.isArray(workflowInterface?.allowedSignals) || workflowInterface.allowedSignals.length === 0) {
      throw new Error('模型规范元数据不完整，无法初始化流程设计器')
    }
    workflowFunctionTypes.value = functionTypes
    workflowSignals.value = lifecycleEvents
    workflowOperators.value = operators
  } catch (err: any) {
    workflowActionCatalog.value = actionCatalog
    workflowCalculationOperators.value = calculationOperators
    workflowControlSignals.value = workflowInterface.allowedSignals
    ElMessage.error(err?.response?.data?.message || err?.message || '流程设计器初始化失败')
  } finally {
    loadingBase.value = false
  }
}
const fetchWorkflows = async () => {
  loadingWorkflows.value = true
  try {
    const res = await axios.get('/api/workflow/list')
    if (res.data?.success) workflows.value = res.data.data || []
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '流程列表加载失败')
  } finally {
    loadingWorkflows.value = false
  }
}

onMounted(async () => {
  await Promise.all([fetchBaseData(), fetchWorkflows()])
})
</script>

<style scoped>
.workflow-designer {
  --bg-main: #f8fafc;
  --bg-panel: rgba(255, 255, 255, 0.85);
  --bg-node: rgba(255, 255, 255, 0.95);
  --line: rgba(0, 0, 0, 0.08);
  --line-strong: rgba(0, 0, 0, 0.15);
  --text-main: #0f172a;
  --text-sub: #64748b;
  --accent: #3b82f6;
  
  display: flex;
  height: calc(100vh - 52px);
  background: var(--bg-main);
  color: var(--text-main);
  font-family: "Inter", "PingFang SC", sans-serif;
}

.sidebar { 
  width: 320px; 
  background: var(--bg-panel); 
  backdrop-filter: blur(20px);
  border-right: 1px solid var(--line);
  box-shadow: 4px 0 24px rgba(0,0,0,0.03);
  z-index: 10;
}

.sidebar-tabs { height: 100%; }
.sidebar-tabs :deep(.el-tabs__header) { margin: 0; border-bottom: 1px solid var(--line); }
.sidebar-tabs :deep(.el-tabs__item) { color: var(--text-sub); }
.sidebar-tabs :deep(.el-tabs__item.is-active) { color: var(--text-main); font-weight: 600; }
.sidebar-tabs :deep(.el-tabs__active-bar) { background: var(--accent); }
.sidebar-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }

.sidebar-scroll { height: calc(100vh - 120px); overflow: auto; padding: 20px; }
.sidebar-scroll::-webkit-scrollbar { width: 6px; }
.sidebar-scroll::-webkit-scrollbar-thumb { background: rgba(0,0,0,0.1); border-radius: 4px; }

.group-title { font-size: 13px; font-weight: 700; color: #94a3b8; margin: 12px 0 12px; letter-spacing: 0.05em; text-transform: uppercase; }

.logic-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 20px; }
.logic-item { 
  display: flex; align-items: center; justify-content: center; gap: 8px; 
  padding: 12px 10px; 
  border: 1px solid rgba(0,0,0,0.08); 
  border-radius: 10px; 
  font-size: 13px; font-weight: 500;
  color: var(--text-main); 
  background: #ffffff; 
  cursor: grab; 
  transition: all 0.2s ease;
  box-shadow: 0 2px 6px rgba(0,0,0,0.02);
}
.logic-item:hover {
  background: #eff6ff;
  border-color: rgba(59,130,246,0.3);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(59,130,246,0.1);
}

.palette-list { display: flex; flex-direction: column; gap: 12px; margin-bottom: 20px; }
.palette-item, .workflow-item { 
  border: 1px solid var(--line); 
  border-radius: 12px; 
  background: #ffffff; 
  padding: 14px; 
  display: flex; align-items: center; gap: 12px; 
  cursor: pointer; 
  transition: all 0.2s ease;
  box-shadow: 0 2px 8px rgba(0,0,0,0.02);
}
.palette-item:hover, .workflow-item:hover {
  background: #f8fafc;
  border-color: var(--line-strong);
  box-shadow: 0 4px 12px rgba(0,0,0,0.04);
}
.workflow-item { display: block; margin-bottom: 10px; }
.workflow-item.active { 
  border-color: var(--accent); 
  background: #eff6ff; 
  box-shadow: inset 0 0 0 1px var(--accent), 0 2px 8px rgba(59,130,246,0.1);
}

.item-title { color: var(--text-main); font-size: 14px; font-weight: 600; }
.item-sub { color: var(--text-sub); font-size: 12px; margin-top: 4px; }
.mono { font-family: 'JetBrains Mono', Consolas, monospace; }

.main { flex: 1; min-width: 0; display: flex; flex-direction: column; position: relative; }

/* Floating Header with Blur */
.header { 
  position: absolute;
  top: 20px; left: 20px; right: 20px;
  z-index: 20;
  min-height: 64px; 
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(0,0,0,0.05);
  border-radius: 16px;
  display: flex; align-items: center; justify-content: space-between; 
  padding: 12px 20px; 
  box-shadow: 0 8px 32px rgba(0,0,0,0.04);
}

.header-left { display: flex; flex-direction: column; gap: 8px; min-width: 0; }
.name-input { width: min(420px, 42vw); }
.name-input :deep(.el-input__wrapper) {
  background-color: #ffffff;
  border: 1px solid rgba(0,0,0,0.1);
  box-shadow: 0 2px 6px rgba(0,0,0,0.02);
  color: #0f172a;
}
.name-input :deep(.el-input__inner) { color: #0f172a; font-weight: 600; }

.protocol-strip { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; color: #64748b; font-size: 12px; }
.protocol-strip span { 
  border: 1px solid rgba(0,0,0,0.08); 
  background: #f1f5f9; 
  border-radius: 6px; 
  padding: 4px 8px; 
  white-space: nowrap; 
}
.header-actions { display: flex; gap: 12px; }
.header-actions .el-button {
  border-radius: 8px;
  font-weight: 600;
  backdrop-filter: blur(4px);
}

.canvas-wrap { flex: 1; min-height: 0; width: 100%; height: 100%; position: absolute; inset: 0; }
.canvas-wrap :deep(.vue-flow__pane) { 
  background-image: 
    linear-gradient(rgba(0,0,0,0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0,0,0,0.05) 1px, transparent 1px);
  background-size: 24px 24px;
}

/* Premium Glass Node */
.node-shell { 
  width: 240px; min-height: 90px; 
  border: 1px solid rgba(0,0,0,0.1); 
  border-radius: 14px; 
  background: var(--bg-node); 
  backdrop-filter: blur(12px);
  padding: 14px; box-sizing: border-box; position: relative; 
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.05), inset 0 1px 0 #ffffff; 
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}
.node-shell:hover {
  transform: translateY(-4px) scale(1.02);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.08), inset 0 1px 0 #ffffff;
  border-color: rgba(0,0,0,0.15);
}
.vue-flow__node-custom.selected .node-shell {
  border-color: var(--accent);
  box-shadow: 0 0 0 2px rgba(59,130,246,0.3), 0 16px 40px rgba(0, 0, 0, 0.08);
}
.node-shell.kind-llm { border-style: dashed; background: rgba(248, 250, 252, 0.9); }

.node-head { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.node-tag { 
  display: inline-flex; align-items: center; height: 22px; padding: 0 10px; 
  border-radius: 12px; font-size: 11px; font-weight: 700;
  background: #eff6ff; color: #2563eb; 
}
.node-title { font-size: 14px; font-weight: 700; color: #0f172a; }
.node-sub { margin-top: 6px; font-size: 12px; color: var(--text-sub); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.node-meta { margin-top: 8px; font-size: 11px; color: #64748b; display: flex; gap: 8px; flex-wrap: wrap; }
.node-meta span { background: #f1f5f9; padding: 2px 6px; border-radius: 4px; border: 1px solid #e2e8f0; }

/* Glowing Edges & Handles */
.node-handle { 
  width: 12px !important; height: 12px !important; 
  border: 2px solid #94a3b8 !important; 
  background: #ffffff !important; 
  transition: all 0.2s;
}
.node-handle:hover {
  transform: scale(1.5);
  background: var(--accent) !important;
  border-color: #fff !important;
  box-shadow: 0 0 10px rgba(59,130,246,0.4);
}
.flow-handle { border-radius: 50% !important; }
.data-handle { border-radius: 3px !important; border-color: #8b5cf6 !important; }

/* Custom Drawer styling */
:deep(.el-drawer) {
  background: #ffffff;
  color: #0f172a;
}
:deep(.el-drawer__header) { color: #0f172a; border-bottom: 1px solid rgba(0,0,0,0.05); margin-bottom: 0; padding-bottom: 16px; }

.drawer-content { padding: 10px 4px; }
.config-tabs :deep(.el-tabs__item) { font-size: 14px; color: #64748b; }
.config-tabs :deep(.el-tabs__item.is-active) { color: #0f172a; font-weight: 600; }

.table-actions { display: flex; justify-content: flex-end; margin-bottom: 12px; }
.condition-list { display: flex; flex-direction: column; gap: 12px; width: 100%; }
.condition-row { display: flex; align-items: center; gap: 10px; }
.condition-label { width: 80px; color: var(--text-sub); font-size: 13px; }
.hint { margin-top: 10px; color: #64748b; font-size: 12px; }

.signal-preview { 
  width: 100%; border: 1px solid rgba(0,0,0,0.05); 
  border-radius: 8px; background: #f8fafc; 
  padding: 12px; display: grid; grid-template-columns: 1fr 1fr; gap: 8px 12px; 
}
.signal-preview div { display: flex; justify-content: space-between; gap: 10px; font-size: 13px; min-width: 0; }
.signal-preview span { color: #64748b; white-space: nowrap; }
.signal-preview b { color: #0f172a; font-family: 'JetBrains Mono', Consolas, monospace; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* Vue Flow Edge animation glow */
:deep(.vue-flow__edge-path) {
  stroke: rgba(148, 163, 184, 0.8);
}
:deep(.vue-flow__edge.selected .vue-flow__edge-path) {
  stroke: var(--accent);
  filter: drop-shadow(0 0 4px rgba(59,130,246,0.3));
}

@media (max-width: 920px) {
  .workflow-designer { flex-direction: column; }
  .sidebar { width: 100%; height: 260px; border-right: 0; border-bottom: 1px solid var(--line); }
  .sidebar-scroll { height: 210px; }
  .header { position: relative; top: 0; left: 0; right: 0; border-radius: 0; box-shadow: none; align-items: stretch; flex-direction: column; }
  .header-actions { justify-content: flex-end; }
  .name-input { width: 100%; }
  .canvas-wrap { position: relative; }
}

/* Enterprise workflow studio overrides */
.workflow-designer {
  --bg-main: #f5f7fa; --bg-panel: #ffffff; --bg-node: #ffffff; --line: #e5e7eb; --line-strong: #d1d5db; --text-main: #111827; --text-sub: #6b7280; --accent: #1677ff;
  background: var(--bg-main);
  font-family: Inter, 'PingFang SC', 'Microsoft YaHei', 'Segoe UI', sans-serif;
}
.sidebar { width: 300px; background: #fff; backdrop-filter: none; border-right: 1px solid var(--line); box-shadow: none; }
.sidebar-scroll { height: calc(100vh - 96px); padding: 12px; }
.group-title { margin: 10px 0 8px; color: #6b7280; font-size: 12px; letter-spacing: 0; text-transform: none; }
.logic-grid, .palette-list { gap: 8px; }
.logic-item, .palette-item, .workflow-item { border-radius: 6px; box-shadow: none; transition: border-color .15s, background-color .15s; }
.logic-item { padding: 9px 8px; }
.logic-item:hover, .palette-item:hover, .workflow-item:hover { transform: none; box-shadow: none; border-color: #b7d7ff; background: #f5f9ff; }
.header { position: relative; inset: auto; min-height: 64px; flex: 0 0 auto; border: 0; border-bottom: 1px solid var(--line); border-radius: 0; padding: 8px 16px; background: #fff; backdrop-filter: none; box-shadow: none; }
.header-left { gap: 4px; }
.protocol-strip span { padding: 2px 6px; border-color: #e5e7eb; background: #f7f8fa; }
.canvas-wrap { position: relative; flex: 1; min-height: 0; }
.node-shell { width: 224px; min-height: 82px; border-radius: 6px; background: #fff; backdrop-filter: none; padding: 12px; box-shadow: 0 1px 2px rgba(15, 23, 42, .06); transition: border-color .15s, box-shadow .15s; }
.node-shell:hover { transform: none; border-color: #93c5fd; box-shadow: 0 2px 8px rgba(15, 23, 42, .08); }
.vue-flow__node-custom.selected .node-shell { box-shadow: 0 0 0 2px rgba(22, 119, 255, .18); }
.node-tag { height: 20px; padding: 0 7px; border-radius: 3px; }
.node-handle:hover { transform: scale(1.2); box-shadow: none; }
:deep(.el-drawer) { background: #fff; }
.drawer-content { padding: 4px 0; }
@media (max-width: 920px) { .header { position: relative; } }
</style>
