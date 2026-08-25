<template>
  <section class="business-panel">
    <template v-if="node.nodeType === 'DEV_NODE'">
      <section class="capability-config-card">
        <div class="panel-section-heading">
          <div class="heading-text">
            <h4>设备能力配置</h4>
            <p>选择设备模型提供的业务能力，并配置本次调用参数。</p>
          </div>
          <span class="param-count-badge">{{ selectedCapability?.parameters?.length || 0 }} 项参数</span>
        </div>

        <div class="capability-selector-row">
          <div class="field-description">
            <strong>执行能力</strong>
            <span>（选择当前节点在运行时调用的设备能力）</span>
          </div>
          <el-select
            :model-value="node.capability?.capabilityName"
            filterable
            :disabled="readonly || !contractReady"
            placeholder="请选择设备能力"
            @update:model-value="changeCapability"
          >
            <el-option
              v-for="item in deviceCapabilities"
              :key="item.capabilityName"
              :label="item.displayName || item.capabilityName"
              :value="item.capabilityName"
            />
          </el-select>
        </div>

        <div class="parameter-subheading">
          <strong>能力参数</strong>
            <span>（已填写的值随流程发布；留空则创建任务时再填）</span>
        </div>

        <div v-if="selectedCapability?.parameters?.length" class="capability-parameter-form">
          <div v-for="parameter in selectedCapability.parameters" :key="parameter.name" class="parameter-form-row">
            <div class="parameter-description">
              <div class="param-label-group">
                <strong class="param-name">{{ parameter.displayName || parameter.name }}</strong>
                <span class="param-desc">{{ parameter.description || '配置该参数在本次能力调用中的取值' }}</span>
              </div>
              <el-tag size="small" type="info" class="param-type-tag">{{ parameter.dataType }}</el-tag>
            </div>
            <div class="parameter-control">
              <WorkflowTypedValueInput
                :model-value="node.capability?.capabilityParameters?.[parameter.name]"
                :disabled="readonly"
                :nullable="true"
                :data-type="parameter.dataType"
                :options="parameter.allowedValues || parameter.enumValues || parameter.options || []"
                @update:model-value="updateParameter(parameter.name, $event)"
              />
            </div>
          </div>
        </div>
        <WorkflowConfigurationEmpty v-else title="该能力无需配置参数" description="当前能力不需要额外输入，运行时将直接使用能力定义。" />
      </section>
    </template>

    <template v-else-if="node.nodeType === 'SUBFLOW_NODE'">
      <section class="capability-config-card">
        <div class="panel-section-heading">
          <div class="heading-text">
            <h4>子流程调用配置</h4>
            <p>创建独立的子流程执行上下文，子流程结束后当前节点才完成。</p>
          </div>
        </div>
        <dl class="property-list">
          <div><dt>引用流程模型</dt><dd>ID {{ node.subFlowModelId || '-' }}</dd></div>
          <div><dt>进入条件</dt><dd>父节点进入运行状态后，由引擎创建子流程执行上下文</dd></div>
          <div><dt>完成条件</dt><dd>子流程到达结束节点后，由引擎通知父节点</dd></div>
        </dl>
      </section>
    </template>

    <template v-else-if="['BRANCH', 'AGGREGATE'].includes(node.functionType)">
      <section class="calculation-rule-card">
        <div class="panel-section-heading">
          <div class="heading-text">
            <h4>计算规则配置</h4>
            <p>将计算结果写入内部变量，控制接口触发器可继续引用该变量。</p>
          </div>
          <span class="param-count-badge">可选配置</span>
        </div>
        <div class="calculation-editor">
          <WorkflowExpressionEditor
            variant="spacious"
            :model-value="node.expression"
            :readonly="readonly"
            :variables="node.internalVariables || []"
            :assignment="true"
            :temporal="true"
            @update:model-value="updateBasic('expression', $event)"
          />
        </div>
      </section>
    </template>

    <div v-else class="semantic-card">
      <strong>{{ guidance.title }}</strong>
      <span>{{ guidance.detail }}</span>
    </div>
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
.business-panel {
  display: grid;
  gap: 12px;
  padding: 0;
  background: transparent;
}
.business-panel :deep(.el-select),
.business-panel :deep(.el-input-number) {
  width: 100%;
}
.capability-config-card,
.calculation-rule-card {
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 6px);
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.02);
  overflow: hidden;
}
.panel-section-heading {
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9);
  background: #ffffff;
}
.heading-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.panel-section-heading h4 {
  margin: 0;
  color: var(--sl-text-heading, #0f172a);
  font-size: 13px;
  font-weight: 700;
}
.panel-section-heading p {
  margin: 0;
  color: var(--sl-text-secondary, #64748b);
  font-size: 11px;
  line-height: 16px;
}
.param-count-badge {
  font-size: 10.5px;
  font-weight: 600;
  color: var(--sl-primary, #2563eb);
  background: var(--sl-primary-light, #eff6ff);
  padding: 1px 6px;
  border-radius: 4px;
  flex: none;
}
.capability-selector-row {
  display: grid;
  gap: 6px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9);
}
.field-description {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.field-description strong {
  color: var(--sl-text-heading, #0f172a);
  font-size: 12px;
  font-weight: 600;
}
.field-description span {
  color: var(--sl-text-secondary, #64748b);
  font-size: 10.5px;
}
.parameter-subheading {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding: 10px 14px 6px;
  background: #ffffff;
}
.parameter-subheading strong {
  font-size: 12px;
  font-weight: 600;
  color: var(--sl-text-heading, #0f172a);
}
.parameter-subheading span {
  color: var(--sl-text-secondary, #64748b);
  font-size: 10.5px;
}
.capability-parameter-form {
  display: flex;
  flex-direction: column;
}
.parameter-form-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 14px 10px;
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9);
  transition: background-color 0.15s ease;
}
.parameter-form-row:last-child {
  border-bottom: none;
}
.parameter-form-row:hover {
  background: var(--sl-bg-hover, #f8fafc);
}
.parameter-description {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
.param-label-group {
  display: flex;
  align-items: baseline;
  gap: 6px;
  flex-wrap: wrap;
}
.param-name {
  color: var(--sl-text-heading, #0f172a);
  font-size: 11.5px;
  font-weight: 600;
}
.param-desc {
  color: var(--sl-text-secondary, #64748b);
  font-size: 10.5px;
}
.param-type-tag {
  font-size: 10px;
  font-family: var(--sl-font-mono);
  margin-left: auto;
  flex: none;
}
.parameter-control {
  width: 100%;
}
.calculation-editor {
  padding: 12px 14px;
}
.semantic-card {
  display: grid;
  gap: 6px;
  padding: 14px;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 6px);
  background: #ffffff;
}
.semantic-card strong {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--sl-text-heading, #0f172a);
}
.semantic-card span {
  color: var(--sl-text-body, #334155);
  font-size: 11.5px;
  line-height: 1.6;
}
.property-list {
  margin: 0;
  border-top: 1px solid var(--sl-border-subtle, #f1f5f9);
}
.property-list > div {
  display: grid;
  grid-template-columns: 120px 1fr;
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9);
}
.property-list > div:last-child {
  border-bottom: none;
}
.property-list dt,
.property-list dd {
  margin: 0;
  padding: 10px 14px;
  font-size: 11.5px;
  line-height: 1.6;
}
.property-list dt {
  background: var(--sl-bg-hover, #f8fafc);
  color: var(--sl-text-secondary, #64748b);
}
.property-list dd {
  color: var(--sl-text-heading, #0f172a);
}
</style>
