<template>
  <section class="business-panel">
    <template v-if="node.nodeType === 'DEV_NODE'">
      <section class="capability-config-card">
        <div class="panel-section-heading"><div><h4>设备能力</h4><p>选择设备模型提供的业务能力，并配置本次调用参数。</p></div><span>{{ selectedCapability?.parameters?.length || 0 }} 项参数</span></div>
        <div class="capability-selector-row">
          <div class="field-description"><strong>执行能力</strong><span>{{ selectedCapability?.description || '选择当前节点在运行时调用的设备能力。' }}</span></div>
          <el-select :model-value="node.capability?.capabilityName" filterable :disabled="readonly || !contractReady" placeholder="请选择设备能力" @update:model-value="changeCapability">
            <el-option v-for="item in deviceCapabilities" :key="item.capabilityName" :label="item.displayName || item.capabilityName" :value="item.capabilityName" />
          </el-select>
        </div>
        <div class="parameter-subheading"><strong>能力参数</strong><span>参数值随节点定义保存，并在运行时作为能力调用参数</span></div>
        <div v-if="selectedCapability?.parameters?.length" class="capability-parameter-form">
          <div v-for="parameter in selectedCapability.parameters" :key="parameter.name" class="parameter-form-row">
            <div class="parameter-description">
              <strong>{{ parameter.displayName || '未命名参数' }}</strong>
              <span v-if="parameter.description">{{ parameter.description }}</span>
              <span v-else>配置该参数在本次能力调用中的取值</span>
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
      <section class="calculation-rule-card">
        <div class="panel-section-heading"><div><h4>计算规则</h4><p>将计算结果写入内部变量，控制接口触发器可继续引用该变量。</p></div><span>可选配置</span></div>
        <div class="calculation-editor">
          <WorkflowExpressionEditor :model-value="node.expression" :readonly="readonly" :variables="node.internalVariables || []" :assignment="true" :temporal="true" @update:model-value="updateBasic('expression', $event)" />
        </div>
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
.business-panel{display:grid;gap:12px;padding:14px 16px;background:#fff}.business-panel :deep(.el-select),.business-panel :deep(.el-input-number){width:100%}.capability-config-card,.calculation-rule-card{border:1px solid var(--sl-border-base);border-radius:var(--sl-radius-sm,6px);background:#fff}.panel-section-heading{min-height:48px;display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 14px;border-bottom:1px solid var(--sl-border-base);background:#fff}.panel-section-heading h4{margin:0;color:var(--sl-text-heading);font-size:13px;font-weight:500}.panel-section-heading p{margin:2px 0 0;color:var(--sl-text-secondary);font-size:11px;line-height:17px}.panel-section-heading>span{color:var(--sl-text-secondary);font-size:10px}.capability-selector-row{display:grid;grid-template-columns:1fr;align-items:center;gap:8px;padding:12px 14px}.field-description{display:grid;gap:3px}.field-description strong{color:var(--sl-text-heading);font-size:12px;font-weight:500}.field-description span{color:var(--sl-text-secondary);font-size:10px;line-height:16px}.capability-selector-row :deep(.el-select){max-width:none}.capability-parameter-form{display:grid}.parameter-form-row{display:grid;grid-template-columns:1fr;align-items:center;gap:8px;min-height:0;padding:10px 14px;border-bottom:1px solid var(--sl-border-subtle);box-sizing:border-box;transition:background-color .15s ease}.parameter-form-row:last-child{border-bottom:0}.parameter-form-row:hover{background:var(--sl-bg-hover)}.parameter-description{display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:center;gap:3px 8px;min-width:0}.parameter-description strong{overflow:hidden;color:var(--sl-text-heading);font-size:12px;font-weight:500;text-overflow:ellipsis;white-space:nowrap}.parameter-description span{grid-column:1;color:var(--sl-text-secondary);font-size:10px;line-height:16px}.parameter-description :deep(.el-tag){grid-column:2;grid-row:1 / span 2}.parameter-control{width:100%;max-width:none;min-width:0}.calculation-editor{padding:14px}.semantic-card{display:grid;gap:7px;padding:16px;border:1px solid var(--sl-border-base);border-radius:var(--sl-radius-sm,6px);background:#fff}.semantic-card strong{font-size:13px;font-weight:500}.semantic-card span{color:var(--sl-text-body);font-size:11px;line-height:1.7}.property-list{margin:0;border:1px solid var(--sl-border-base)}.property-list>div{display:grid;grid-template-columns:120px 1fr;border-bottom:1px solid var(--sl-border-base)}.property-list>div:last-child{border-bottom:0}.property-list dt,.property-list dd{margin:0;padding:11px 14px;font-size:11px;line-height:1.6}.property-list dt{background:var(--sl-bg-hover);color:var(--sl-text-body)}.property-list dd{color:var(--sl-text-heading)}
.parameter-subheading{display:flex;align-items:baseline;gap:10px;padding:12px 14px 8px;border-top:1px solid var(--sl-border-subtle)}.parameter-subheading strong{font-size:12px;font-weight:500}.parameter-subheading span{color:var(--sl-text-secondary);font-size:10px}
</style>
