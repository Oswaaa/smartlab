<template>
<div class="detail-head category-detail-head">
            <div class="detail-title">
              <h2>{{ category.categoryName }}</h2>
              <p>{{ categoryPathLabel }} · {{ formatTime(category.createTime) }}</p>
            </div>
            <div class="detail-actions">
              <el-button
                v-if="canCreateModel"
                type="primary"
                plain
                :icon="Plus"
                :disabled="!selectedCategoryCanCreateModel"
                @click="openCreateDrawerWithCategory({ categoryId: category.id })"
              >在此类别新建模型</el-button>
            </div>
          </div>

          <el-scrollbar class="category-detail-scroll">
            <section class="category-stat-grid">
              <article class="category-stat-item">
                <span>下级类别</span>
                <strong>{{ categoryChildren.length }}</strong>
              </article>
              <article class="category-stat-item">
                <span>关联模型</span>
                <strong>{{ categoryModels.length }}</strong>
              </article>
              <article class="category-stat-item">
                <span>设备实例</span>
                <strong>{{ categoryInstances.length }}</strong>
              </article>
              <article class="category-stat-item">
                <span>数据集</span>
                <strong>{{ categoryDataAssets.length }}</strong>
              </article>
            </section>

            <section class="info-section section-cluster category-section">
              <div class="section-title">
                <h3>类别信息</h3>
              </div>
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="类别名称">{{ category.categoryName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="父类别">{{ parentCategoryName(category) }}</el-descriptions-item>
                <el-descriptions-item label="创建时间">{{ formatTime(category.createTime) }}</el-descriptions-item>
                <el-descriptions-item label="说明">{{ category.description || '-' }}</el-descriptions-item>
              </el-descriptions>
            </section>

            <section class="info-section section-cluster category-section">
              <div class="section-title">
                <h3>下级类别</h3>
                <span class="section-count">{{ categoryChildren.length }} 项</span>
              </div>
              <div v-if="categoryChildren.length === 0" class="compact-empty inline-empty">暂无下级类别</div>
              <div v-else class="category-child-grid">
                <button v-for="child in categoryChildren" :key="child.id" type="button" class="category-child-item" @click="selectCategoryById(child.id)">
                  <span>{{ child.categoryName }}</span>
                  <em>({{ categoryModelCount(child.id) }})</em>
                </button>
              </div>
            </section>

            <section class="info-section section-cluster category-section">
              <div class="section-title">
                <h3>类别下的设备模型</h3>
                <span class="section-count">{{ categoryModels.length }} 项</span>
              </div>
              <div v-if="categoryModels.length === 0" class="compact-empty inline-empty">暂无设备模型</div>
              <el-table v-else :data="categoryModels" border size="small" class="industrial-table compact-category-table">
                <el-table-column label="模型名称" min-width="180">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="selectModel(row.modelId)">{{ row.modelName || '-' }}</el-button>
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
            </section>

            <section class="info-section section-cluster category-section">
              <div class="section-title">
                <h3>BOM 引用</h3>
                <span class="section-count">{{ categoryBomUsages?.length || 0 }} 项</span>
              </div>
              <div v-if="!categoryBomUsages || categoryBomUsages.length === 0" class="compact-empty inline-empty">暂无模型在组件结构清单中引用该类别</div>
              <el-table v-else :data="categoryBomUsages" border size="small" class="industrial-table compact-category-table">
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
            </section>

            <section class="info-section section-cluster category-section">
              <div class="section-title">
                <h3>实例组件槽位</h3>
                <span class="section-count">{{ categoryComponentSlots?.length || 0 }} 项</span>
              </div>
              <div v-if="!categoryComponentSlots || categoryComponentSlots.length === 0" class="compact-empty inline-empty">暂无设备实例组件槽位使用该类别</div>
              <el-table v-else :data="categoryComponentSlots" border size="small" class="industrial-table compact-category-table">
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
            </section>

            <section class="info-section section-cluster category-section">
              <div class="section-title">
                <h3>相关数据集</h3>
                <span class="section-count">{{ categoryDataAssets?.length || 0 }} 项</span>
              </div>
              <div v-if="!categoryDataAssets || categoryDataAssets.length === 0" class="compact-empty inline-empty">暂无与该类别实例绑定的数据集</div>
              <el-table v-else :data="categoryDataAssets" border size="small" class="industrial-table compact-category-table">
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
            </section>
          </el-scrollbar>

</template>

<script setup>
import { computed } from 'vue'
import { Folder, Grid, Files, DataAnalysis } from '@element-plus/icons-vue'
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
.category-detail-scroll { flex: 1; min-height: 0; background: #f8fafc; }
.category-detail-scroll :deep(.el-scrollbar__view) { padding: 12px 14px 18px; }
.category-stat-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; margin-bottom: 10px; }
.category-stat-item { min-width: 0; display: grid; gap: 5px; padding: 10px 12px; border: 1px solid #dbe4ef; border-left: 3px solid #2563eb; border-radius: 4px; background: #fff; }
.category-stat-item span { color: #64748b; font-size: 12px; font-weight: 700; }
.category-stat-item strong { color: #0f172a; font-size: 22px; line-height: 1; }
.category-section { margin-top: 10px; }
.category-child-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 8px; }
.category-child-item { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 8px; padding: 8px 10px; border: 1px solid #dbe4ef; border-radius: 4px; background: #fff; color: #0f172a; cursor: pointer; text-align: left; }
.category-child-item:hover { border-color: #93c5fd; background: #eff6ff; }
.category-child-item span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; font-weight: 700; }
.category-child-item em { color: #64748b; font-size: 12px; font-style: normal; }
.compact-category-table :deep(.el-table__cell) { padding: 7px 8px; }
.section-title { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; min-height: 26px; }
.section-title h3 { margin: 0; font-size: 15px; line-height: 1.3; letter-spacing: 0; }
.section-count { color: #64748b; font-size: 12px; font-weight: 500; }
.compact-empty { display: flex; align-items: center; min-height: 30px; padding: 7px 10px; border: 1px dashed #cbd5e1; border-radius: 4px; background: #f8fafc; color: #64748b; font-size: 13px; line-height: 1.4; }
.inline-empty { margin-top: 8px; }
.detail-head { flex-shrink: 0; display: flex; justify-content: space-between; gap: 12px; padding: 12px 14px; border-bottom: 1px solid #e5e7eb; background: #fff; }
.detail-title { min-width: 0; }
.detail-title h2 { margin: 0 0 4px; font-size: 20px; line-height: 1.25; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; letter-spacing: 0; }
.detail-title p { margin: 0; color: var(--color-text-sub); font-size: 13px; }
.detail-actions { display: flex; align-items: center; gap: 8px; }
.info-section { margin-top: 8px; border: 1px solid #e5e7eb; border-radius: 4px; padding: 10px; background: #fff; }
.section-cluster { box-shadow: inset 3px 0 0 #dbeafe; }
</style>
