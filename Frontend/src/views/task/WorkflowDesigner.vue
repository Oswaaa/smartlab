
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
                <el-form-item label="分支表达式">
                  <div class="condition-list">
                    <div v-for="c in selectedNode.data.conditions" :key="c.interfaceId" class="condition-row">
                      <span class="condition-label">{{ c.label }}</span>
                      <el-input v-model="c.expression" placeholder="例如 temperature >= 80" />
                    </div>
                  </div>
                </el-form-item>
              </template>
            </el-form>
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
                    <el-option label="START" value="START" />
                    <el-option label="DONE" value="DONE" />
                    <el-option label="ERROR" value="ERROR" />
                    <el-option label="ALERT" value="ALERT" />
                    <el-option label="USER_CONFIRM" value="USER_CONFIRM" />
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

type FlowNodeKind = 'start' | 'end' | 'branch' | 'join' | 'device'
type NodeVariable = { name: string; dataType: 'string' | 'number' | 'boolean'; mapping: string }
type NodePort = { portId: string; direction: 'IN' | 'OUT'; dataType: 'string' | 'number' | 'boolean'; variableBinding: string }
type NodeInterface = {
  interfaceId: string
  direction: 'IN' | 'OUT'
  signalType: 'START' | 'DONE' | 'ERROR' | 'ALERT' | 'USER_CONFIRM'
  triggerMode: 'ALWAYS' | 'SIGNAL' | 'EXPRESSION'
  triggerExpr: string
}

type WorkflowNodeData = {
  kind: FlowNodeKind
  name: string
  nodeType: 'DEVICE_CAPABILITY_NODE' | 'FUNCTIONAL_NODE'
  modelId: string
  functionId: string
  parameters: Record<string, any>
  conditions: Array<{ label: string; expression: string; interfaceId: string }>
  interfaces: NodeInterface[]
  ports: NodePort[]
  internalVariables: NodeVariable[]
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
const drawerVisible = ref(false)

const { project, addEdges } = useVueFlow()

const logicTemplates = [
  { kind: 'start', label: '开始', icon: VideoPlay },
  { kind: 'end', label: '结束', icon: CircleCheck },
  { kind: 'branch', label: '分支', icon: Share },
  { kind: 'join', label: '汇聚', icon: Connection }
]

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

const workflowId = (wf: any) => wf.templateId || wf.id || ''
const workflowNameText = (wf: any) => wf.templateName || wf.name || '未命名流程'

const kindLabel = (kind: FlowNodeKind) => {
  if (kind === 'start') return '开始'
  if (kind === 'end') return '结束'
  if (kind === 'branch') return '分支'
  if (kind === 'join') return '汇聚'
  if (kind === 'device') return '设备'
  return '设备'
}

const nodeSummary = (data: WorkflowNodeData) => {
  if (data.kind === 'device') return `${modelMap.value[data.modelId]?.modelName || '未选模型'} / ${data.functionId || '未选能力'}`
  if (data.kind === 'branch') return `分支条件 ${data.conditions.length}`
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
  if (kind === 'start') return [{ interfaceId: 'flow_out', direction: 'OUT', signalType: 'START', triggerMode: 'ALWAYS', triggerExpr: '' }]
  if (kind === 'end') return [{ interfaceId: 'flow_in', direction: 'IN', signalType: 'DONE', triggerMode: 'SIGNAL', triggerExpr: '' }]
  if (kind === 'branch') return [
    { interfaceId: 'flow_in', direction: 'IN', signalType: 'DONE', triggerMode: 'SIGNAL', triggerExpr: '' },
    { interfaceId: 'flow_yes', direction: 'OUT', signalType: 'DONE', triggerMode: 'EXPRESSION', triggerExpr: 'result == true' },
    { interfaceId: 'flow_no', direction: 'OUT', signalType: 'DONE', triggerMode: 'EXPRESSION', triggerExpr: 'result == false' }
  ]
  return [
    { interfaceId: 'flow_in', direction: 'IN', signalType: 'START', triggerMode: 'SIGNAL', triggerExpr: '' },
    { interfaceId: 'flow_out', direction: 'OUT', signalType: 'DONE', triggerMode: 'ALWAYS', triggerExpr: '' }
  ]
}
const defaultPortsByKind = (kind: FlowNodeKind): NodePort[] => kind === 'device'
  ? [{ portId: 'data_in', direction: 'IN', dataType: 'number', variableBinding: '' }, { portId: 'data_out', direction: 'OUT', dataType: 'number', variableBinding: '' }]
  : []
const defaultVariablesByKind = (_kind: FlowNodeKind): NodeVariable[] => []

const createNodeData = (kind: FlowNodeKind, model?: DeviceModel): WorkflowNodeData => {
  if (kind === 'device') return {
    kind, name: `${model?.modelName || '设备'}节点`, nodeType: 'DEVICE_CAPABILITY_NODE', modelId: model?.modelId || '', functionId: '', parameters: {},
    conditions: [], interfaces: defaultInterfacesByKind(kind), ports: defaultPortsByKind(kind), internalVariables: defaultVariablesByKind(kind)
  }
  if (kind === 'branch') return {
    kind, name: '分支节点', nodeType: 'FUNCTIONAL_NODE', modelId: '', functionId: 'BRANCH_EVAL', parameters: {},
    conditions: [{ label: '满足条件', expression: 'result == true', interfaceId: 'flow_yes' }, { label: '不满足', expression: 'result == false', interfaceId: 'flow_no' }],
    interfaces: defaultInterfacesByKind(kind), ports: defaultPortsByKind(kind), internalVariables: defaultVariablesByKind(kind)
  }
  return { kind, name: `${kindLabel(kind)}节点`, nodeType: 'FUNCTIONAL_NODE', modelId: '', functionId: kind.toUpperCase(), parameters: {}, conditions: [], interfaces: defaultInterfacesByKind(kind), ports: defaultPortsByKind(kind), internalVariables: defaultVariablesByKind(kind) }
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
    id: `node_${Date.now()}_${Math.floor(Math.random() * 1000)}`,
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
  selectedNode.value?.data.ports.splice(index, 1)
}
const addInterface = () => {
  selectedNode.value?.data.interfaces.push({ interfaceId: `flow_${Date.now().toString().slice(-4)}`, direction: 'OUT', signalType: 'DONE', triggerMode: 'ALWAYS', triggerExpr: '' })
}
const removeInterface = (index: number) => {
  selectedNode.value?.data.interfaces.splice(index, 1)
}
const clearCanvas = () => {
  nodes.value = []
  edges.value = []
  drawerVisible.value = false
  selectedNodeId.value = ''
}

const buildPayload = () => {
  const nodesDef = nodes.value.map((n: any, idx: number) => {
    const interfaces = (n.data.interfaces || []).map((it: NodeInterface) => ({
      interfaceId: `${n.id}_${it.interfaceId}`,
      direction: it.direction,
      interfaceType: 'CONTROL_FLOW',
      allowedSignals: ['START', 'DONE', 'ERROR', 'ALERT', 'USER_CONFIRM'],
      triggerMode: it.triggerMode,
      triggerExpr: it.triggerExpr,
      signalType: it.signalType
    }))

    const ports = (n.data.ports || [])
      .filter((p: NodePort) => p.portId)
      .map((p: NodePort) => ({
        portId: `${n.id}_${p.portId}`,
        direction: p.direction,
        dataType: p.dataType,
        internalVariableBinding: p.variableBinding
      }))

    const internalVariables = (n.data.internalVariables || [])
      .filter((v: NodeVariable) => v.name)
      .map((v: NodeVariable, vi: number) => ({
        variableId: `${n.id}_var_${vi}`,
        name: v.name,
        dataType: v.dataType,
        mapping: v.mapping
      }))

    const capability =
      n.data.kind === 'device'
        ? { deviceModelRef: n.data.modelId, capabilityRef: n.data.functionId || 'NOOP', bindingPolicy: 'AUTO_ONLINE' }
        : { functionType: n.data.kind.toUpperCase(), capabilityRef: n.data.functionId || n.data.kind.toUpperCase() }

    const nodeParameters = n.data.kind === 'device' ? { ...n.data.parameters } : {}

    return {
      nodeId: n.id,
      name: n.data.name,
      nodeType: n.data.nodeType,
      capability,
      parameters: nodeParameters,
      internalVariables,
      lifecycle: {
        initialState: 'PENDING',
        states: [
          { stateId: 'PENDING', stateName: 'PENDING' },
          { stateId: 'RUNNING', stateName: 'RUNNING' },
          { stateId: 'SUCCESS', stateName: 'SUCCESS' },
          { stateId: 'FAILED', stateName: 'FAILED' }
        ],
        transitions: [
          { transitionId: `${n.id}_t1`, from: 'PENDING', to: 'RUNNING', trigger: { interfaceRef: `${n.id}_flow_in`, signalType: 'START' } },
          { transitionId: `${n.id}_t2`, from: 'RUNNING', to: 'SUCCESS', trigger: { interfaceRef: `${n.id}_flow_out`, signalType: 'DONE' } }
        ]
      },
      interfaces,
      ports,
      actions: [
        {
          actionId: `${n.id}_action`,
          actionType: n.data.kind === 'device' ? 'EMIT_SIGNAL' : 'EXECUTE_LOGIC',
          interfaceRef: n.data.kind === 'device' ? 'if_wf_cmd_in' : `${n.id}_flow_out`,
          signalType: n.data.kind === 'device' ? n.data.functionId || 'EXEC' : n.data.kind.toUpperCase(),
          logicExpression: n.data.kind === 'device' ? undefined : n.data.kind.toUpperCase(),
          payload: n.data.kind === 'device'
            ? { specVersion: 'smartlab.signal.v1', modelId: n.data.modelId, capabilityRef: n.data.functionId, commandId: n.data.functionId, parameters: n.data.parameters }
            : { conditions: n.data.conditions || [] }
        }
      ],
      _order: idx
    }
  })

  const interfaceConnections = edges.value
    .filter((e: any) => String(e.sourceHandle || '').startsWith('if:') && String(e.targetHandle || '').startsWith('if:'))
    .map((e: any, idx: number) => ({
      connectionId: `conn_if_${idx + 1}`,
      connectionType: 'NODE_TO_NODE',
      source: { interfaceRef: `${e.source}_${String(e.sourceHandle).replace('if:', '')}` },
      target: { interfaceRef: `${e.target}_${String(e.targetHandle).replace('if:', '')}` }
    }))

  const portConnections = edges.value
    .filter((e: any) => String(e.sourceHandle || '').startsWith('port:') && String(e.targetHandle || '').startsWith('port:'))
    .map((e: any, idx: number) => ({
      connectionId: `conn_port_${idx + 1}`,
      source: { nodeRef: e.source, portRef: `${e.source}_${String(e.sourceHandle).replace('port:', '')}` },
      target: { nodeRef: e.target, portRef: `${e.target}_${String(e.targetHandle).replace('port:', '')}` }
    }))

  return { templateId: selectedWorkflowId.value || '', templateName: workflowName.value, nodesDef, interfaceConnections, portConnections }
}

const saveWorkflow = async () => {
  if (!workflowName.value.trim()) return ElMessage.warning('请输入流程名称')
  if (nodes.value.length === 0) return ElMessage.warning('请先添加节点')

  saving.value = true
  try {
    const payload = buildPayload()
    const res = await axios.post('/api/workflow/save', payload)
    if (res.data?.success) {
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

const decodeRef = (refText: string, nodeIds: string[]) => {
  const matchedNodeId = nodeIds.find(id => refText.startsWith(`${id}_`))
  if (!matchedNodeId) return null
  return { nodeId: matchedNodeId, localRef: refText.replace(`${matchedNodeId}_`, '') }
}

const loadWorkflow = async (templateId: string) => {
  selectedWorkflowId.value = templateId
  try {
    const res = await axios.get(`/api/workflow/detail/${templateId}`)
    if (!res.data?.success || !res.data?.data) return ElMessage.error(res.data?.message || '加载流程失败')

    const tpl = res.data.data
    if ((tpl.nodesDef || []).some((n: any) => ['LLM_NODE', 'LLM'].includes(String(n.capability?.functionType || '').toUpperCase()))) {
      return ElMessage.error('该旧流程包含下层 LLM 节点，已禁止加载。请在上层编排中调用确定性 SmartLab 工作流。')
    }
    workflowName.value = tpl.templateName || tpl.name || ''

    const loadedNodes = (tpl.nodesDef || []).map((n: any, idx: number) => {
      const capability = n.capability || {}
      const functionType = capability.functionType || ''
      const kind: FlowNodeKind =
        n.nodeType === 'DEVICE_CAPABILITY_NODE' ? 'device'
        : functionType === 'START' ? 'start'
        : functionType === 'END' ? 'end'
        : functionType === 'BRANCH' ? 'branch'
        : 'join'

      const rawInterfaces = (n.interfaces || []).map((it: any) => ({
        interfaceId: String(it.interfaceId || '').replace(`${n.nodeId}_`, ''),
        direction: (it.direction || 'IN') as 'IN' | 'OUT',
        signalType: (it.signalType || 'DONE') as NodeInterface['signalType'],
        triggerMode: (it.triggerMode || 'ALWAYS') as NodeInterface['triggerMode'],
        triggerExpr: it.triggerExpr || ''
      }))
      const rawPorts = (n.ports || []).map((p: any) => ({
        portId: String(p.portId || '').replace(`${n.nodeId}_`, ''),
        direction: (p.direction || 'IN') as 'IN' | 'OUT',
        dataType: (p.dataType || 'string') as NodePort['dataType'],
        variableBinding: p.internalVariableBinding || ''
      }))
      const rawVariables = (n.internalVariables || []).map((v: any) => ({ name: v.name || '', dataType: (v.dataType || 'string') as NodeVariable['dataType'], mapping: v.mapping || '' }))

      return {
        id: n.nodeId,
        type: 'custom',
        position: { x: 120 + (idx % 4) * 260, y: 80 + Math.floor(idx / 4) * 150 },
        data: {
          kind,
          name: n.name || kindLabel(kind),
          nodeType: n.nodeType || (kind === 'device' ? 'DEVICE_CAPABILITY_NODE' : 'FUNCTIONAL_NODE'),
          modelId: capability.deviceModelRef || '',
          functionId: capability.capabilityRef || '',
          parameters: n.actions?.[0]?.payload?.parameters || {},
          conditions: n.actions?.[0]?.payload?.conditions || [],
          interfaces: rawInterfaces.length ? rawInterfaces : defaultInterfacesByKind(kind),
          ports: rawPorts.length ? rawPorts : defaultPortsByKind(kind),
          internalVariables: rawVariables.length ? rawVariables : defaultVariablesByKind(kind)
        }
      }
    })

    nodes.value = loadedNodes
    const nodeIds = loadedNodes.map((n: any) => n.id)

    const ifEdges = (tpl.interfaceConnections || []).map((conn: any, idx: number) => {
      const src = decodeRef(conn.source?.interfaceRef || '', nodeIds)
      const tgt = decodeRef(conn.target?.interfaceRef || '', nodeIds)
      if (!src || !tgt) return null
      return { id: `edge_if_${idx}`, source: src.nodeId, sourceHandle: `if:${src.localRef}`, target: tgt.nodeId, targetHandle: `if:${tgt.localRef}`, type: 'smoothstep', animated: true, style: { stroke: '#6b7280', strokeWidth: 2 }, markerEnd: MarkerType.ArrowClosed }
    }).filter(Boolean)

    const portEdges = (tpl.portConnections || []).map((conn: any, idx: number) => {
      const sourceRef = conn.source?.portRef || conn.sourcePort || conn.fromPort || ''
      const targetRef = conn.target?.portRef || conn.targetPort || conn.toPort || ''
      const src = decodeRef(sourceRef, nodeIds)
      const tgt = decodeRef(targetRef, nodeIds)
      if (!src || !tgt) return null
      return { id: `edge_port_${idx}`, source: src.nodeId, sourceHandle: `port:${src.localRef}`, target: tgt.nodeId, targetHandle: `port:${tgt.localRef}`, type: 'smoothstep', animated: false, style: { stroke: '#8f6d2a', strokeWidth: 2, strokeDasharray: '5,4' }, markerEnd: MarkerType.ArrowClosed }
    }).filter(Boolean)

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
    const res = await axios.get('/api/device/model/list')
    if (res.data?.success) deviceModels.value = res.data.data || []
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '设备模型加载失败')
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
  --bg-main: #eef1f4;
  --bg-panel: #f8f9fb;
  --line: #c9ced6;
  --line-strong: #aeb5bf;
  --text-main: #232a31;
  --text-sub: #5f6873;
  display: flex;
  height: calc(100vh - 52px);
  background: linear-gradient(180deg, #f6f7f9 0%, #eceff3 100%);
}
.sidebar { width: 300px; background: var(--bg-panel); border-right: 1px solid var(--line); }
.sidebar-tabs { height: 100%; }
.sidebar-tabs :deep(.el-tabs__header) { margin: 0; border-bottom: 1px solid var(--line); }
.sidebar-tabs :deep(.el-tabs__item.is-active) { color: var(--text-main); font-weight: 600; }
.sidebar-tabs :deep(.el-tabs__active-bar) { background: #7b8390; }
.sidebar-scroll { height: calc(100vh - 120px); overflow: auto; padding: 14px; }
.group-title { font-size: 12px; color: var(--text-sub); margin: 6px 0 10px; }
.logic-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-bottom: 14px; }
.logic-item { display: flex; align-items: center; justify-content: center; gap: 6px; padding: 10px 8px; border: 1px solid var(--line); border-radius: 8px; font-size: 12px; color: var(--text-main); background: #fff; cursor: grab; }
.palette-list { display: flex; flex-direction: column; gap: 8px; margin-bottom: 14px; }
.palette-item, .workflow-item { border: 1px solid var(--line); border-radius: 8px; background: #fff; padding: 10px; display: flex; align-items: center; gap: 8px; cursor: pointer; }
.workflow-item { display: block; margin-bottom: 8px; }
.workflow-item.active { border-color: var(--line-strong); background: #f2f4f7; }
.item-title { color: var(--text-main); font-size: 13px; font-weight: 600; }
.item-sub { color: var(--text-sub); font-size: 12px; margin-top: 2px; }
.mono { font-family: 'JetBrains Mono', Consolas, monospace; }
.main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.header { min-height: 64px; border-bottom: 1px solid var(--line); background: #fff; display: flex; align-items: center; justify-content: space-between; padding: 8px 14px; gap: 12px; }
.header-left { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.name-input { width: min(420px, 42vw); }
.protocol-strip { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; color: #566171; font-size: 11px; }
.protocol-strip span { border: 1px solid #d6dce4; background: #f7f9fb; border-radius: 4px; padding: 2px 6px; white-space: nowrap; }
.header-actions { display: flex; gap: 8px; }
.canvas-wrap { flex: 1; min-height: 0; }
.canvas-wrap :deep(.vue-flow__pane) { background: linear-gradient(#dde1e6 1px, transparent 1px), linear-gradient(90deg, #dde1e6 1px, transparent 1px); background-size: 20px 20px; background-color: #eef1f4; }
.node-shell { width: 208px; min-height: 84px; border: 1px solid var(--line-strong); border-radius: 8px; background: #fff; padding: 10px; box-sizing: border-box; position: relative; box-shadow: 0 3px 10px rgba(30, 40, 50, 0.08); }
.node-shell.kind-llm { border-style: dashed; }
.node-head { display: flex; align-items: center; gap: 8px; }
.node-tag { display: inline-flex; align-items: center; height: 20px; padding: 0 8px; border-radius: 999px; font-size: 11px; background: #eff2f6; color: #4f5966; }
.node-title { font-size: 13px; font-weight: 600; color: var(--text-main); }
.node-sub { margin-top: 6px; font-size: 12px; color: var(--text-sub); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.node-meta { margin-top: 6px; font-size: 11px; color: #6b7280; display: flex; gap: 6px; flex-wrap: wrap; }
.node-handle { width: 10px !important; height: 10px !important; border: 2px solid #757f8a !important; background: #fff !important; }
.flow-handle { border-radius: 50% !important; }
.data-handle { border-radius: 2px !important; border-color: #8f6d2a !important; }
.drawer-content { padding-right: 4px; }
.config-tabs :deep(.el-tabs__item) { font-size: 13px; }
.table-actions { display: flex; justify-content: flex-end; margin-bottom: 8px; }
.condition-list { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.condition-row { display: flex; align-items: center; gap: 8px; }
.condition-label { width: 72px; color: var(--text-sub); font-size: 12px; }
.hint { margin-top: 8px; color: #6b7280; font-size: 12px; }
.signal-preview { width: 100%; border: 1px solid #d8dde5; border-radius: 6px; background: #f8fafc; padding: 8px; display: grid; grid-template-columns: 1fr 1fr; gap: 6px 10px; }
.signal-preview div { display: flex; justify-content: space-between; gap: 10px; font-size: 12px; min-width: 0; }
.signal-preview span { color: #6b7280; white-space: nowrap; }
.signal-preview b { color: #111827; font-family: 'JetBrains Mono', Consolas, monospace; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

@media (max-width: 920px) {
  .workflow-designer { flex-direction: column; }
  .sidebar { width: 100%; height: 220px; border-right: 0; border-bottom: 1px solid var(--line); }
  .sidebar-scroll { height: 170px; }
  .header { align-items: stretch; flex-direction: column; }
  .header-actions { justify-content: flex-end; }
  .name-input { width: 100%; }
}
</style>
