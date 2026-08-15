<template>
  <div class="category-detail-view">
    <div class="model-view-header">
      <div class="header-left">
        <h2 class="model-title">{{ category.categoryName }}</h2>
        <span class="model-subtitle">{{ categoryPathLabel }} · 创建于 {{ formatTime(category.createTime) }}</span>
      </div>
      <div class="header-actions">
        <button
          v-if="canCreateModel"
          class="btn-aliyun-cta"
          type="button"
          :disabled="!selectedCategoryCanCreateModel"
          @click="openCreateDrawerWithCategory({ categoryId: category.id })"
        >
          <el-icon><Plus /></el-icon> 在此类别新建模型
        </button>
      </div>
    </div>

    <!-- 顶部 35px 像素级锁定指标横幅 -->
    <div class="metric-ribbon">
      <div class="m-cell">
        <span class="m-label">下级子类别</span>
        <strong class="m-val highlight">{{ categoryChildren.length }} 个</strong>
      </div>
      <div class="m-cell">
        <span class="m-label">关联设备模型</span>
        <strong class="m-val highlight">{{ categoryModels.length }} 个</strong>
      </div>
      <div class="m-cell">
        <span class="m-label">挂载设备实例</span>
        <strong class="m-val">{{ categoryInstances.length }} 台</strong>
      </div>
      <div class="m-cell">
        <span class="m-label">关联数据资产</span>
        <strong class="m-val">{{ categoryDataAssets.length }} 张表</strong>
      </div>
    </div>

    <el-scrollbar class="category-detail-scroll">
      <section class="info-section">
        <div class="section-title">
          <h3>类别基础信息</h3>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="类别名称">{{ category.categoryName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="父级类别">{{ parentCategoryName(category) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(category.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="类别说明">{{ category.description || '-' }}</el-descriptions-item>
        </el-descriptions>
      </section>

      <section class="info-section">
        <div class="section-title">
          <h3>下级子类别</h3>
          <span class="section-count">{{ categoryChildren.length }} 项</span>
        </div>
        <div v-if="categoryChildren.length === 0" class="compact-empty inline-empty">暂无下级子类别</div>
        <div v-else class="category-child-grid">
          <button v-for="child in categoryChildren" :key="child.id" type="button" class="category-child-item" @click="selectCategoryById(child.id)">
            <span class="child-cat-name">{{ child.categoryName }}</span>
            <em class="child-cat-count">({{ categoryModelCount(child.id) }})</em>
          </button>
        </div>
      </section>

      <section class="info-section">
        <div class="section-title">
          <h3>类别下的设备模型</h3>
          <span class="section-count">{{ categoryModels.length }} 项</span>
        </div>
        <div v-if="categoryModels.length === 0" class="compact-empty inline-empty">暂无设备模型</div>
        <div v-else class="table-card">
          <el-table :data="categoryModels" border stripe size="small" class="unified-table-el">
            <el-table-column label="模型名称" min-width="180">
              <template #default="{ row }">
                <button class="btn-link" type="button" @click="selectModel(row.modelId)">{{ row.modelName || '-' }}</button>
              </template>
            </el-table-column>
            <el-table-column label="所属类别" min-width="150">
              <template #default="{ row }">{{ row.categoryName || categoryNameById(row.categoryId) || '-' }}</template>
            </el-table-column>
            <el-table-column label="属性 / 操作" width="140">
              <template #default="{ row }">{{ asArray(row.attributes).length }} / {{ asArray(row.capabilities).length }}</template>
            </el-table-column>
            <el-table-column label="更新时间" min-width="180">
              <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section class="info-section">
        <div class="section-title">
          <h3>BOM 引用</h3>
          <span class="section-count">{{ categoryBomUsages?.length || 0 }} 项</span>
        </div>
        <div v-if="!categoryBomUsages || categoryBomUsages.length === 0" class="compact-empty inline-empty">暂无模型在组件结构清单中引用该类别</div>
        <div v-else class="table-card">
          <el-table :data="categoryBomUsages" border stripe size="small" class="unified-table-el">
            <el-table-column label="引用模型" min-width="170">
              <template #default="{ row }">{{ modelNameById(row.parentModelId) || '-' }}</template>
            </el-table-column>
            <el-table-column label="组件槽位" min-width="160">
              <template #default="{ row }">{{ row.slotName || row.componentName || row.name || '-' }}</template>
            </el-table-column>
            <el-table-column label="组件类别" min-width="150">
              <template #default="{ row }">{{ categoryNameById(row.categoryId) || '-' }}</template>
            </el-table-column>
            <el-table-column label="数量" width="90">
              <template #default="{ row }">{{ row.quantity || 1 }}</template>
            </el-table-column>
            <el-table-column label="说明" min-width="220">
              <template #default="{ row }">{{ row.description || '-' }}</template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section class="info-section">
        <div class="section-title">
          <h3>实例组件槽位</h3>
          <span class="section-count">{{ categoryComponentSlots?.length || 0 }} 项</span>
        </div>
        <div v-if="!categoryComponentSlots || categoryComponentSlots.length === 0" class="compact-empty inline-empty">暂无设备实例组件槽位使用该类别</div>
        <div v-else class="table-card">
          <el-table :data="categoryComponentSlots" border stripe size="small" class="unified-table-el">
            <el-table-column label="组件名称" min-width="160">
              <template #default="{ row }">{{ row.componentName || '-' }}</template>
            </el-table-column>
            <el-table-column label="所属实例" min-width="170">
              <template #default="{ row }">{{ instanceNameById(row.parentInstanceId) || '-' }}</template>
            </el-table-column>
            <el-table-column label="绑定实例" min-width="170">
              <template #default="{ row }">{{ instanceNameById(row.childInstanceId) || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">{{ row.status || '-' }}</template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section class="info-section">
        <div class="section-title">
          <h3>相关数据集</h3>
          <span class="section-count">{{ categoryDataAssets?.length || 0 }} 项</span>
        </div>
        <div v-if="!categoryDataAssets || categoryDataAssets.length === 0" class="compact-empty inline-empty">暂无与该类别实例绑定的数据集</div>
        <div v-else class="table-card">
          <el-table :data="categoryDataAssets" border stripe size="small" class="unified-table-el">
            <el-table-column label="数据集" min-width="180">
              <template #default="{ row }">{{ row.dataDesc || row.dataTable || '-' }}</template>
            </el-table-column>
            <el-table-column label="设备实例" min-width="170">
              <template #default="{ row }">{{ instanceNameById(row.instanceId) || '-' }}</template>
            </el-table-column>
            <el-table-column label="设备模型" min-width="170">
              <template #default="{ row }">{{ modelNameById(row.modelId) || '-' }}</template>
            </el-table-column>
            <el-table-column label="物理表" min-width="190">
              <template #default="{ row }">{{ row.dataTable || '-' }}</template>
            </el-table-column>
          </el-table>
        </div>
      </section>
    </el-scrollbar>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Folder, Grid, Files, DataAnalysis, Plus } from '@element-plus/icons-vue'
import { asArray, formatTime } from './normalizers.js'
import { stringId } from './normalizers.js'

const props = defineProps({
  category: Object,
  categoryPathLabel: String,
  categoryChildren: Array,
  categoryModels: Array,
  categoryInstances: Array,
  categoryBomUsages: Array,
  categoryComponentSlots: Array,
  categoryDataAssets: Array,
  canCreateModel: Boolean,
  selectedCategoryCanCreateModel: Boolean,
  categories: Array, // Added to support categoryModelCount
  models: Array, // Added to support categoryModelCount
  categoryChildrenByParent: Map, // Added to support categoryModelCount
})



const emit = defineEmits(['select-category', 'select-model', 'create-model-in-category'])

function selectCategory(data) {
  emit('select-category', data?.categoryId ?? data?.id)
}

function openCreateDrawerWithCategory(data) {
  emit('create-model-in-category', data)
}

function collectCategoryDescendantIds(categoryId) {
  const rootId = stringId(categoryId)
  if (!rootId) return []
  const ids = []
  const visit = (id) => {
    ids.push(id)
    if (props.categoryChildrenByParent) {
      asArray(props.categoryChildrenByParent.get(id)).forEach(child => visit(stringId(child.id)))
    }
  }
  visit(rootId)
  return ids
}

function categoryModelCount(categoryId) {
  const ids = new Set(collectCategoryDescendantIds(categoryId))
  return (props.models || []).filter(model => ids.has(stringId(model.categoryId))).length
}

function selectModel(id) {
  emit('select-model', id)
}

function selectCategoryById(id) {
  emit('select-category', id)
}

function formatJson(val) {
  return JSON.stringify(val)
}

function categoryNameById(id) {
  return props.categories?.find(item => String(item.id) === String(id))?.categoryName || ''
}

function parentCategoryName(cat) {
  if (!cat?.parentCategoryId) return '根类别'
  return categoryNameById(cat.parentCategoryId) || '根类别'
}

function modelNameById(id) {
  return props.models?.find(item => String(item.modelId) === String(id))?.modelName || ''
}

function instanceNameById(id) {
  return props.categoryInstances?.find(item => String(item.instanceId) === String(id))?.instanceName || ''
}

</script>

<style scoped>
.category-detail-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: #ffffff;
}

.model-view-header {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--sl-border-base);
  background: #ffffff;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}
.model-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--sl-text-heading);
  line-height: 1.3;
}
.model-subtitle {
  font-size: 11px;
  color: var(--sl-text-secondary);
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 顶部 35px 像素级锁定指标横幅 */
.metric-ribbon {
  padding: 5px 16px;
  border-bottom: 1px solid var(--sl-border-base);
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  background: #ffffff;
  flex-shrink: 0;
}
.m-cell {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
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
.m-val.highlight {
  font-size: 14.5px;
  color: var(--sl-primary);
  font-family: var(--sl-font-mono);
}

.category-detail-scroll {
  flex: 1;
  min-height: 0;
  background: #ffffff;
}
.category-detail-scroll :deep(.el-scrollbar__view) {
  padding: 12px 18px 24px;
}

.info-section {
  margin-bottom: 14px;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  padding: 10px 12px;
  background: #ffffff;
}
.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  min-height: 24px;
}
.section-title h3 {
  margin: 0;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--sl-text-heading);
}
.section-count {
  color: var(--sl-text-secondary);
  font-size: 11.5px;
  font-weight: 500;
}

.category-child-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 8px;
}
.category-child-item {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 6px 10px;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  background: #ffffff;
  color: var(--sl-text-heading);
  cursor: pointer;
  text-align: left;
  transition: all 0.15s ease;
}
.category-child-item:hover {
  border-color: var(--sl-primary-border);
  background: var(--sl-primary-light);
}
.child-cat-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12.5px;
  font-weight: 600;
}
.child-cat-count {
  color: var(--sl-text-secondary);
  font-size: 11px;
  font-style: normal;
}

.compact-empty {
  display: flex;
  align-items: center;
  min-height: 28px;
  padding: 6px 10px;
  border: 1px dashed var(--sl-border-base);
  border-radius: 4px;
  background: #f8fafc;
  color: var(--sl-text-secondary);
  font-size: 12px;
}
.inline-empty {
  margin-top: 6px;
}

/* 一体化表格卡片容器 */
.table-card {
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  overflow: hidden;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
}
.table-card :deep(.el-table) {
  border: none !important;
}
</style>
