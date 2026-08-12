<template>
  <section class="business-panel">
    <template v-if="node.nodeType === 'DEV_NODE'">
      <el-alert title="这里只选择设备模型能力；具体设备实例在创建任务时绑定。" type="info" :closable="false" />
      <el-form label-position="top">
        <el-form-item label="执行能力">
          <el-select :model-value="node.capability?.capabilityName" filterable :disabled="!contractReady" placeholder="选择运行时调用的能力" @update:model-value="changeCapability">
            <el-option v-for="item in deviceCapabilities" :key="item.capabilityName" :label="item.displayName || item.capabilityName" :value="item.capabilityName" />
          </el-select>
        </el-form-item>
        <el-form-item v-for="parameter in selectedCapability?.parameters || []" :key="parameter.name" :label="`${parameter.displayName || parameter.name} · ${parameter.dataType}`">
          <WorkflowTypedValueInput :model-value="node.capability?.capabilityParameters?.[parameter.name]" :data-type="parameter.dataType" @update:model-value="updateParameter(parameter.name, $event)" />
        </el-form-item>
      </el-form>
    </template>
    <template v-else-if="node.nodeType === 'SUBFLOW_NODE'">
      <dl class="property-list">
        <div><dt>引用流程模型</dt><dd>ID {{ node.subFlowModelId || '-' }}</dd></div>
        <div><dt>进入条件</dt><dd>父节点进入 RUNNING 后由引擎创建子流程执行上下文</dd></div>
        <div><dt>完成条件</dt><dd>子流程到达 END 后由引擎通知父节点</dd></div>
      </dl>
    </template>
    <template v-else-if="['BRANCH', 'AGGREGATE'].includes(node.functionType)">
      <el-form label-position="top">
        <el-form-item label="计算表达式">
          <el-input :model-value="node.expression" type="textarea" :rows="4" placeholder="例如：temp = temp / 100；条件判断由控制接口触发器完成" @update:model-value="updateBasic('expression', $event)" />
        </el-form-item>
      </el-form>
      <el-alert title="expression 只负责计算；分支选择和动作执行由各控制接口的触发器决定。" type="warning" :closable="false" />
    </template>
    <div v-else class="semantic-card"><strong>{{ guidance.title }}</strong><span>{{ guidance.detail }}</span></div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import WorkflowTypedValueInput from './WorkflowTypedValueInput.vue'
import { replaceCapability } from '../../../../../utils/workflowNodeDefinition.js'

type Item = Record<string, any>
const props = withDefaults(defineProps<{ node: Item, contractReady?: boolean, deviceCapabilities?: Item[], guidance?: Item }>(), {
  contractReady: false,
  deviceCapabilities: () => [],
  guidance: () => ({ title: '节点配置', detail: '配置节点业务参数。' }),
})
const emit = defineEmits<{ 'update:node': [node: Item] }>()
const selectedCapability = computed(() => props.deviceCapabilities.find(item => item.capabilityName === props.node.capability?.capabilityName))

function publish(next: Item) { emit('update:node', next) }
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
.business-panel{display:grid;gap:10px;padding:12px}.business-panel :deep(.el-select),.business-panel :deep(.el-input-number){width:100%}.business-panel :deep(.el-form-item){margin-bottom:10px}.semantic-card{display:grid;gap:7px;padding:12px;border:1px solid #dce3ec;background:#f8f9fb}.semantic-card strong{font-size:13px}.semantic-card span{color:#667488;font-size:11px;line-height:1.6}.property-list{margin:0;border:1px solid #e0e5eb}.property-list>div{display:grid;grid-template-columns:104px 1fr;border-bottom:1px solid #e8ecf1}.property-list>div:last-child{border-bottom:0}.property-list dt,.property-list dd{margin:0;padding:9px;font-size:11px;line-height:1.5}.property-list dt{background:#f6f7f9;color:#667386}.property-list dd{color:#28364a}
</style>
