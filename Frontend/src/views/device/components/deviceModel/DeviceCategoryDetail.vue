<template>
  <div class="category-detail-view">
    <header class="model-view-header">
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
          <el-icon><Plus /></el-icon>
          <span>在此类别新建模型</span>
        </button>
      </div>
    </header>

    <!-- 顶部 35px 像素级锁定指标横幅 -->
    <div class="metric-ribbon">
      <div class="ribbon-items-flow">
        <div class="ribbon-cell">
          <span class="label">下级子类别</span>
          <strong class="val highlight mono">{{ categoryChildren.length }} 个</strong>
        </div>
        <div class="ribbon-cell">
          <span class="label">关联设备模型</span>
          <strong class="val highlight mono">{{ categoryModels.length }} 个</strong>
        </div>
        <div class="ribbon-cell">
          <span class="label">挂载设备实例</span>
          <strong class="val mono">{{ categoryInstances.length }} 台</strong>
        </div>
        <div class="ribbon-cell">
          <span class="label">关联数据资产</span>
          <strong class="val mono">{{ categoryDataAssets.length }} 张表</strong>
        </div>
      </div>
    </div>

    <el-scrollbar class="category-detail-scroll">
      <!-- 类别基础信息 -->
      <section class="flat-table-section">
        <div class="section-heading-bar">
          <div class="heading-left">
            <span class="sec-index-badge">01</span>
            <span class="sec-main-title">类别基础信息</span>
          </div>
        </div>
        <el-descriptions :column="2" border size="small" class="industrial-desc-table">
          <el-descriptions-item label="类别名称">
            <strong class="text-heading">{{ category.categoryName || '-' }}</strong>
          </el-descriptions-item>
          <el-descriptions-item label="父级类别">{{ parentCategoryName(category) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">
            <span class="mono-text">{{ formatTime(category.createTime) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="类别说明">{{ category.description || '-' }}</el-descriptions-item>
        </el-descriptions>
      </section>

      <!-- 下级子类别 -->
      <section class="flat-table-section">
        <div class="section-heading-bar">
          <div class="heading-left">
            <span class="sec-index-badge">02</span>
            <span class="sec-main-title">下级子类别</span>
          </div>
          <span class="sec-right-count">共 {{ categoryChildren.length }} 项</span>
        </div>
        <div v-if="categoryChildren.length === 0" class="compact-empty">暂无下级子类别</div>
        <div v-else class="state-token-clean-row">
          <button
            v-for="child in categoryChildren"
            :key="child.id"
            type="button"
            class="btn-link"
            style="margin-right: 16px; font-weight: 600;"
            @click="selectCategoryById(child.id)"
          >
            <el-icon style="margin-right: 4px;"><Folder /></el-icon>
            <span>{{ child.categoryName }}</span>
            <span class="text-secondary mono-text" style="font-size: 11px; margin-left: 4px;">({{ categoryModelCount(child.id) }})</span>
          </button>
        </div>
      </section>

      <!-- 类别下的设备模型 -->
      <section class="flat-table-section">
        <div class="section-heading-bar">
          <div class="heading-left">
            <span class="sec-index-badge">03</span>
            <span class="sec-main-title">类别下的设备模型</span>
          </div>
          <span class="sec-right-count">共 {{ categoryModels.length }} 项</span>
        </div>
        <div v-if="categoryModels.length === 0" class="compact-empty">暂无设备模型</div>
        <el-table
          v-else
          :data="categoryModels"
          stripe
          size="small"
          class="industrial-fullbleed-table"
        >
          <el-table-column label="模型名称" min-width="180">
            <template #default="{ row }">
              <button class="btn-link" type="button" @click="selectModel(row.modelId)">
                <strong class="text-primary">{{ row.modelName || '-' }}</strong>
              </button>
            </template>
          </el-table-column>
          <el-table-column label="所属类别" min-width="150">
            <template #default="{ row }">{{ row.categoryName || categoryNameById(row.categoryId) || '-' }}</template>
          </el-table-column>
          <el-table-column label="属性 / 操作" width="140">
            <template #default="{ row }">
              <span class="mono-text">{{ asArray(row.attributes).length }} / {{ asArray(row.capabilities).length }}</span>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" min-width="180">
            <template #default="{ row }">
              <span class="mono-text">{{ formatTime(row.updateTime) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <!-- BOM 引用 -->
      <section class="flat-table-section">
        <div class="section-heading-bar">
          <div class="heading-left">
            <span class="sec-index-badge">04</span>
            <span class="sec-main-title">BOM 引用清单</span>
          </div>
          <span class="sec-right-count">共 {{ categoryBomUsages?.length || 0 }} 项</span>
        </div>
        <div v-if="!categoryBomUsages || categoryBomUsages.length === 0" class="compact-empty">暂无模型在组件结构清单中引用该类别</div>
        <el-table
          v-else
          :data="categoryBomUsages"
          stripe
          size="small"
          class="industrial-fullbleed-table"
        >
          <el-table-column label="引用模型" min-width="170">
            <template #default="{ row }">
              <strong>{{ modelNameById(row.parentModelId) || '-' }}</strong>
            </template>
          </el-table-column>
          <el-table-column label="组件槽位" min-width="160">
            <template #default="{ row }">
              <span class="mono-text text-heading">{{ row.slotName || row.componentName || row.name || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="组件类别" min-width="150">
            <template #default="{ row }">{{ categoryNameById(row.categoryId) || '-' }}</template>
          </el-table-column>
          <el-table-column label="数量" width="90">
            <template #default="{ row }">
              <span class="mono-text">{{ row.quantity || 1 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="220">
            <template #default="{ row }">{{ row.description || '-' }}</template>
          </el-table-column>
        </el-table>
      </section>

      <!-- 实例组件槽位 -->
      <section class="flat-table-section">
        <div class="section-heading-bar">
          <div class="heading-left">
            <span class="sec-index-badge">05</span>
            <span class="sec-main-title">实例组件槽位</span>
          </div>
          <span class="sec-right-count">共 {{ categoryComponentSlots?.length || 0 }} 项</span>
        </div>
        <div v-if="!categoryComponentSlots || categoryComponentSlots.length === 0" class="compact-empty">暂无设备实例组件槽位使用该类别</div>
        <el-table
          v-else
          :data="categoryComponentSlots"
          stripe
          size="small"
          class="industrial-fullbleed-table"
        >
          <el-table-column label="组件名称" min-width="160">
            <template #default="{ row }">
              <strong class="mono-text text-heading">{{ row.componentName || '-' }}</strong>
            </template>
          </el-table-column>
          <el-table-column label="所属实例" min-width="170">
            <template #default="{ row }">{{ instanceNameById(row.parentInstanceId) || '-' }}</template>
          </el-table-column>
          <el-table-column label="绑定实例" min-width="170">
            <template #default="{ row }">{{ instanceNameById(row.childInstanceId) || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <span class="mono-text">{{ row.status || '-' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <!-- 相关数据集 -->
      <section class="flat-table-section">
        <div class="section-heading-bar">
          <div class="heading-left">
            <span class="sec-index-badge">06</span>
            <span class="sec-main-title">相关数据集</span>
          </div>
          <span class="sec-right-count">共 {{ categoryDataAssets?.length || 0 }} 项</span>
        </div>
        <div v-if="!categoryDataAssets || categoryDataAssets.length === 0" class="compact-empty">暂无与该类别实例绑定的数据集</div>
        <el-table
          v-else
          :data="categoryDataAssets"
          stripe
          size="small"
          class="industrial-fullbleed-table"
        >
          <el-table-column label="数据集" min-width="180">
            <template #default="{ row }">
              <strong>{{ row.dataDesc || row.dataTable || '-' }}</strong>
            </template>
          </el-table-column>
          <el-table-column label="设备实例" min-width="170">
            <template #default="{ row }">{{ instanceNameById(row.instanceId) || '-' }}</template>
          </el-table-column>
          <el-table-column label="设备模型" min-width="170">
            <template #default="{ row }">{{ modelNameById(row.modelId) || '-' }}</template>
          </el-table-column>
          <el-table-column label="物理表" min-width="190">
            <template #default="{ row }">
              <span class="mono-text text-secondary">{{ row.dataTable || '-' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </el-scrollbar>
  </div>
</template>

<script setup>
import { Plus, Folder } from '@element-plus/icons-vue'
import { asArray, formatTime, stringId } from './normalizers.js'

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
  categories: Array,
  models: Array,
  categoryChildrenByParent: Map,
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
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
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
  color: var(--sl-text-heading, #0f172a);
  line-height: 1.3;
}
.model-subtitle {
  font-size: 11.5px;
  color: var(--sl-text-secondary, #64748b);
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 顶部 35px 像素级锁定指标横幅 */
.metric-ribbon {
  height: 35px;
  max-height: 35px;
  padding: 0 16px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: #fafbfc;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}
.ribbon-items-flow {
  display: flex;
  align-items: center;
  gap: 24px;
}
.ribbon-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  white-space: nowrap;
}
.ribbon-cell .label {
  font-size: 11px;
  color: var(--sl-text-secondary, #64748b);
  line-height: 1;
}
.ribbon-cell .val {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--sl-text-heading, #0f172a);
  line-height: 1;
}
.ribbon-cell .val.highlight {
  color: var(--sl-primary, #2563eb);
}
.ribbon-cell .val.mono {
  font-family: var(--sl-font-mono, monospace);
}

.category-detail-scroll {
  flex: 1;
  min-height: 0;
  background: #ffffff;
}
.category-detail-scroll :deep(.el-scrollbar__view) {
  padding: 0;
}

.flat-table-section {
  display: flex;
  flex-direction: column;
  width: 100%;
}

.section-heading-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 36px;
  padding: 0 16px;
  background: #f8fafc;
  border-top: 1px solid var(--sl-border-base, #e2e8f0);
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  flex-shrink: 0;
}
.flat-table-section:first-child .section-heading-bar {
  border-top: none;
}
.heading-left {
  display: flex;
  align-items: center;
}
.sec-index-badge {
  display: inline-flex;
  width: 22px;
  height: 18px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--sl-primary-border, #bfdbfe);
  border-radius: 3px;
  background: var(--sl-primary-light, #eff6ff);
  color: var(--sl-primary, #2563eb);
  font-size: 11px;
  font-weight: 700;
  font-family: var(--sl-font-mono, monospace);
  margin-right: 8px;
}
.sec-main-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--sl-text-heading, #0f172a);
}
.sec-right-count {
  font-size: 11.5px;
  color: var(--sl-text-secondary, #64748b);
  font-family: var(--sl-font-mono, monospace);
}

.industrial-fullbleed-table {
  width: 100%;
  border-radius: 0;
}
.industrial-fullbleed-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}
.industrial-fullbleed-table :deep(th.el-table__cell) {
  background: #ffffff !important;
  color: var(--sl-text-secondary, #64748b) !important;
  font-size: 11.5px !important;
  font-weight: 600 !important;
  padding: 8px 16px !important;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0) !important;
  vertical-align: middle !important;
}
.industrial-fullbleed-table :deep(td.el-table__cell) {
  padding: 8px 16px !important;
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9) !important;
  color: var(--sl-text-body, #334155) !important;
  font-size: 12.5px !important;
  font-family: var(--sl-font-family) !important;
  vertical-align: middle !important;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
.industrial-fullbleed-table :deep(.cell) {
  display: flex;
  align-items: center;
  line-height: 1.4;
}

.industrial-desc-table {
  width: 100%;
  border-radius: 0;
}
.industrial-desc-table :deep(.el-descriptions__body) {
  border-radius: 0;
}
.industrial-desc-table :deep(.el-descriptions__cell) {
  padding: 8px 16px !important;
  font-size: 12.5px !important;
  font-family: var(--sl-font-family) !important;
  vertical-align: middle !important;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
.industrial-desc-table :deep(.el-descriptions__label) {
  background: #f8fafc !important;
  color: var(--sl-text-secondary, #64748b) !important;
  font-weight: 500 !important;
  width: 140px;
}
.industrial-desc-table :deep(.el-descriptions__content) {
  color: var(--sl-text-body, #334155) !important;
}

.state-token-clean-row {
  padding: 10px 16px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.compact-empty {
  padding: 20px 16px;
  text-align: center;
  color: var(--sl-text-secondary, #64748b);
  font-size: 12px;
}

.mono-text {
  font-family: var(--sl-font-mono, monospace);
  font-size: 12px;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
.text-heading {
  color: var(--sl-text-heading, #0f172a);
  font-family: var(--sl-font-family);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
.text-body {
  color: var(--sl-text-body, #334155);
  font-family: var(--sl-font-family);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
.text-secondary { color: var(--sl-text-secondary, #64748b); }
.text-primary { color: var(--sl-primary, #2563eb); }
.text-success { color: var(--sl-success, #16a34a); }
.text-danger { color: var(--sl-danger, #dc2626); }

.btn-aliyun-cta {
  height: 28px;
  padding: 0 12px;
  background: #ffffff;
  color: var(--sl-primary, #2563eb);
  border: 1px solid var(--sl-primary-border, #bfdbfe);
  border-radius: var(--sl-radius-sm, 6px);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: all 0.18s ease;
}
.btn-aliyun-cta:hover {
  background: var(--sl-primary-light, #eff6ff);
  border-color: var(--sl-primary, #2563eb);
}
.btn-aliyun-cta:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-link {
  background: transparent;
  border: none;
  color: var(--sl-primary, #2563eb);
  font-size: 12px;
  cursor: pointer;
  padding: 0;
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.btn-link:hover {
  text-decoration: underline;
}
</style>
