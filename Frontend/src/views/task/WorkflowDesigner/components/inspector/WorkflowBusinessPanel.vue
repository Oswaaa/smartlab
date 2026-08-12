<template>
  <section class="business-panel">
    <template v-if="node.nodeType === 'DEV_NODE'">
      <section class="capability-config-card">
        <div class="panel-section-heading"><div><h4>设备能力</h4><p>选择设备模型提供的业务能力，并配置本次调用参数。</p></div><span>{{ selectedCapability?.parameters?.length || 0 }} 项参数</span></div>
        <label class="capability-selector"><span>执行能力</span>
          <el-select :model-value="node.capability?.capabilityName" filterable :disabled="readonly || !contractReady" placeholder="选择运行时调用的能力" @update:model-value="changeCapability">
            <el-option v-for="item in deviceCapabilities" :key="item.capabilityName" :label="item.displayName || item.capabilityName" :value="item.capabilityName" />
          </el-select>
        </label>
        <div class="parameter-subheading"><strong>能力参数</strong><span>参数值随节点定义保存，并在运行时作为能力调用参数</span></div>
        <div v-if="selectedCapability?.parameters?.length" class="capability-parameter-form">
          <div v-for="parameter in selectedCapability.parameters" :key="parameter.name" class="parameter-form-row">
            <div class="parameter-description">
              <strong>{{ parameter.displayName || parameter.name }}</strong>
              <span>{{ parameter.name }}</span>
              <el-tag size="small" type="info">{{ parameter.dataType }}</el-tag>
            </div>
            <div class="parameter-control">
              <WorkflowTypedValueInput :model-value="node.capability?.capabilityParameters?.[parameter.name]" :disabled="readonly" :data-type="parameter.dataType" :options="parameter.allowedValues || parameter.enumValues || parameter.options || []" @update:model-value="updateParameter(parameter.name, $event)" />
            </div>
          </div>
        </div>
        <WorkflowConfigurationEmpty v-else title="该能力无需配置参数" description="当前能力不需要额外输入，运行时将直接使用能力定义。" />
      </section>
    </template>
    <template v-else-if="node.nodeType === 'SUBFLOW_NODE'">
      <dl class="property-list">
        <div><dt>引用流程模型</dt><dd>ID {{ node.subFlowModelId || '-' }}</dd></div>
        <div><dt>进入条件</dt><dd>父节点进入运行状态后，由引擎创建子流程执行上下文</dd></div>
        <div><dt>完成条件</dt><dd>子流程到达结束节点后，由引擎通知父节点</dd></div>
      </dl>
    </template>
    <template v-else-if="['BRANCH', 'AGGREGATE'].includes(node.functionType)">
      <section class="capability-config-card">
        <div class="panel-section-heading"><div><h4>计算表达式</h4><p>将计算结果写入内部变量，控制接口触发器再根据该变量进行判断。</p></div><span>支持时序函数</span></div>
        <label class="expression-field"><span>表达式内容</span>
          <WorkflowExpressionEditor :model-value="node.expression" :readonly="readonly" :variables="node.internalVariables || []" :assignment="true" :temporal="true" @update:model-value="updateBasic('expression', $event)" />
        </label>
      </section>
    </template>
    <div v-else class="semantic-card"><strong>{{ guidance.title }}</strong><span>{{ guidance.detail }}</span></div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import WorkflowTypedValueInput from './WorkflowTypedValueInput.vue'
import WorkflowConfigurationEmpty from './WorkflowConfigurationEmpty.vue'
import WorkflowExpressionEditor from './WorkflowExpressionEditor.vue'
import { replaceCapability } from '../../../../../utils/workflowNodeDefinition.js'

type Item = Record<string, any>
const props = withDefaults(defineProps<{ node: Item, readonly?: boolean, contractReady?: boolean, deviceCapabilities?: Item[], guidance?: Item }>(), {
  readonly: false,
  contractReady: false,
  deviceCapabilities: () => [],
  guidance: () => ({ title: '节点配置', detail: '配置节点业务参数。' }),
})
const emit = defineEmits<{ 'update:node': [node: Item] }>()
const selectedCapability = computed(() => props.deviceCapabilities.find(item => item.capabilityName === props.node.capability?.capabilityName))

function publish(next: Item) { if (!props.readonly) emit('update:node', next) }
function updateBasic(field: string, value: unknown) { publish({ ...props.node, [field]: value }) }
function changeCapability(name: string) {
  if (!props.contractReady) return
  const capability = props.deviceCapabilities.find(item => item.capabilityName === name)
  if (capability) publish(replaceCapability(props.node, capability, props.node.capability))
}
function updateParameter(name: string, value: unknown) {
  publish({ ...props.node, capability: { ...props.node.capability, capabilityParameters: { ...props.node.capability?.capabilityParameters, [name]: value } } })
}
</script>

<style scoped>
.business-panel{display:grid;gap:12px;padding:16px 20px;background:#fff}.business-panel :deep(.el-select),.business-panel :deep(.el-input-number){width:100%}.capability-config-card{border:1px solid #e5e5e5;border-radius:2px;background:#fff}.panel-section-heading{min-height:56px;display:flex;align-items:center;justify-content:space-between;gap:16px;padding:10px 14px;border-bottom:1px solid #e5e5e5;background:#fafafa}.panel-section-heading h4{margin:0;color:#262626;font-size:13px;font-weight:500}.panel-section-heading p{margin:2px 0 0;color:#8c8c8c;font-size:11px;line-height:17px}.panel-section-heading>span{color:#8c8c8c;font-size:10px}.capability-selector,.expression-field{display:grid;gap:6px;padding:14px}.capability-selector>span,.expression-field>span{color:#595959;font-size:11px;font-weight:500}.capability-selector :deep(.el-select){max-width:360px}.capability-parameter-form{display:grid}.parameter-form-row{display:grid;grid-template-columns:210px minmax(240px,1fr);align-items:center;gap:24px;min-height:66px;padding:10px 14px;border-bottom:1px solid #f0f0f0;box-sizing:border-box;transition:background-color .15s ease}.parameter-form-row:last-child{border-bottom:0}.parameter-form-row:hover{background:#fafafa}.parameter-description{display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:center;gap:3px 8px;min-width:0}.parameter-description strong{overflow:hidden;color:#262626;font-size:12px;font-weight:500;text-overflow:ellipsis;white-space:nowrap}.parameter-description span{grid-column:1;color:#8c8c8c;font-size:10px}.parameter-description :deep(.el-tag){grid-column:2;grid-row:1 / span 2}.parameter-control{width:100%;max-width:360px;min-width:0}.semantic-card{display:grid;gap:7px;padding:16px;border:1px solid #e5e5e5;border-radius:2px;background:#fafafa}.semantic-card strong{font-size:13px;font-weight:500}.semantic-card span{color:#595959;font-size:11px;line-height:1.7}.property-list{margin:0;border:1px solid #e5e5e5}.property-list>div{display:grid;grid-template-columns:150px 1fr;border-bottom:1px solid #e5e5e5}.property-list>div:last-child{border-bottom:0}.property-list dt,.property-list dd{margin:0;padding:11px 14px;font-size:11px;line-height:1.6}.property-list dt{background:#fafafa;color:#595959}.property-list dd{color:#262626}@media(max-width:760px){.parameter-form-row{grid-template-columns:1fr;gap:8px}.parameter-description,.parameter-control{max-width:none}}
.parameter-subheading{display:flex;align-items:baseline;gap:10px;padding:12px 14px 8px;border-top:1px solid #f0f0f0}.parameter-subheading strong{font-size:12px;font-weight:500}.parameter-subheading span{color:#8c8c8c;font-size:10px}
</style>
