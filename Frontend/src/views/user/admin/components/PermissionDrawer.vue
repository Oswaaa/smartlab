<template>
  <el-drawer
    :model-value="modelValue"
    size="880px"
    destroy-on-close
    :before-close="onBeforeClose"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <!-- 抽屉头部 (无多余图标，标准字阶) -->
    <template #header>
      <div class="pd-drawer-header">
        <div class="header-title-row">
          <strong class="drawer-title">权限配置</strong>
          <span v-if="user" class="drawer-user-meta">
            {{ user.userName }} · {{ roleLabel(role(user)) }} · {{ user.lab || '未分配实验室' }}
          </span>
        </div>
      </div>
    </template>

    <div class="pd-drawer-body" v-loading="loading">
      <!-- 1. 顶部指标横幅 (35px 像素级高度锁定，1px 底边框，无卡片孤岛) -->
      <div class="pd-metric-ribbon">
        <div class="metric-item">
          <span>已拥有权限</span>
          <strong class="blue">{{ ownedCount }} 项</strong>
        </div>
        <div class="metric-sep">|</div>
        <div class="metric-item">
          <span>角色默认</span>
          <strong class="slate">{{ inherited.length }} 项</strong>
        </div>
        <div class="metric-sep">|</div>
        <div class="metric-item">
          <span>直接授权</span>
          <strong class="amber">{{ explicit.length }} 项</strong>
        </div>
        <div class="metric-sep">|</div>
        <div class="metric-item">
          <span>可分配权限</span>
          <strong class="green">{{ availableCount }} 项</strong>
        </div>
      </div>

      <!-- 2. 未保存变更提示横幅 -->
      <div v-if="dirty" class="pd-dirty-alert">
        <span>当前存在未保存的权限变更（新增 {{ added }} 项，移除 {{ removed }} 项）</span>
        <button class="btn-link" type="button" @click="undo">
          撤销全部变更
        </button>
      </div>

      <!-- 3. 双列平铺展开折叠列表主体 (平铺可折叠流，无嵌套孤岛卡片) -->
      <div class="pd-columns-layout">
        <!-- 左列：已拥有权限平铺折叠列表 -->
        <section class="pd-panel-box">
          <div class="panel-head-bar">
            <span class="panel-head-title">已拥有权限清单 ({{ ownedCount }} 项)</span>
            <div class="panel-head-ops">
              <button class="btn-link" type="button" @click="toggleAllOwned">
                {{ allOwnedExpanded ? '全部折叠' : '全部展开' }}
              </button>
            </div>
          </div>

          <div class="panel-scroll-content">
            <!-- 角色默认继承分区 (可折叠) -->
            <div class="collapse-perm-box">
              <div class="collapse-head-bar" @click="inheritedExpanded = !inheritedExpanded">
                <div class="collapse-title-left">
                  <span class="expand-arrow">{{ inheritedExpanded ? '▼' : '▶' }}</span>
                  <span class="collapse-title-text">角色默认继承</span>
                  <span class="collapse-badge">{{ inherited.length }} 项</span>
                </div>
                <span class="collapse-state-tag">{{ inheritedExpanded ? '点击折叠' : '点击展开' }}</span>
              </div>

              <div v-show="inheritedExpanded" class="collapse-content-list">
                <div v-for="p in inherited" :key="p.id" class="flat-perm-row inherited">
                  <div class="row-left-content">
                    <span class="level-pill">{{ lvBadge(p) }}</span>
                    <div class="perm-title-group">
                      <span class="perm-name">{{ title(p) }}</span>
                      <span class="perm-obj-act">{{ obj(p.object) }} · {{ act(p.action) }}</span>
                    </div>
                  </div>
                  <div class="row-right-meta">
                    <span class="role-inherit-tag">角色预置</span>
                  </div>
                </div>
                <div v-if="!inherited.length" class="empty-section-hint">当前角色无默认继承权限</div>
              </div>
            </div>

            <!-- 直接扩展授权分区 (可折叠) -->
            <div class="collapse-perm-box">
              <div class="collapse-head-bar" @click="explicitExpanded = !explicitExpanded">
                <div class="collapse-title-left">
                  <span class="expand-arrow">{{ explicitExpanded ? '▼' : '▶' }}</span>
                  <span class="collapse-title-text">直接扩展授权</span>
                  <span class="collapse-badge active">{{ explicit.length }} 项</span>
                </div>
                <span class="collapse-state-tag">{{ explicitExpanded ? '点击折叠' : '点击展开' }}</span>
              </div>

              <div v-show="explicitExpanded" class="collapse-content-list">
                <div v-for="p in explicit" :key="p.id" class="flat-perm-row explicit">
                  <div class="row-left-content">
                    <span class="level-pill active">{{ lvBadge(p) }}</span>
                    <div class="perm-title-group">
                      <div class="perm-title-top">
                        <span class="perm-name">{{ title(p) }}</span>
                        <span v-if="isInherited(p)" class="role-overlap-pill">角色已覆盖</span>
                      </div>
                      <span class="perm-obj-act">{{ obj(p.object) }} · {{ act(p.action) }}</span>
                    </div>
                  </div>
                  <div class="row-right-meta">
                    <button class="btn-link danger" type="button" @click="remove(p)">
                      撤销
                    </button>
                  </div>
                </div>
                <div v-if="!explicit.length" class="empty-section-hint">暂无直接扩展授权项</div>
              </div>
            </div>
          </div>
        </section>

        <!-- 右列：可分配权限平铺折叠列表 -->
        <section class="pd-panel-box">
          <div class="panel-head-bar">
            <span class="panel-head-title">可分配权限清单 (剩余 {{ availableCount }} 项)</span>
            <div class="panel-head-ops">
              <button class="btn-link" type="button" @click="toggleAllAvail">
                {{ allAvailExpanded ? '全部折叠' : '全部展开' }}
              </button>
            </div>
          </div>

          <div class="panel-search-bar">
            <el-input
              v-model="kw"
              placeholder="搜索权限名称、对象或操作..."
              clearable
              size="small"
              class="search-input"
            />
            <button
              v-if="visibleAvail.length"
              class="btn-aliyun-cta"
              type="button"
              @click="addAll"
            >
              + 一键添加全部 ({{ visibleAvail.length }})
            </button>
          </div>

          <div class="panel-scroll-content">
            <!-- 分组折叠框 -->
            <div
              v-for="group in groups"
              :key="group.name"
              class="collapse-perm-box"
            >
              <div class="collapse-head-bar" @click="toggleGroup(group.name)">
                <div class="collapse-title-left">
                  <span class="expand-arrow">{{ isGroupExpanded(group.name) ? '▼' : '▶' }}</span>
                  <span class="collapse-title-text">{{ group.name }}</span>
                  <span class="collapse-badge">{{ group.items.length }} 项</span>
                </div>
                <div class="collapse-head-actions">
                  <button
                    class="btn-aliyun-cta"
                    type="button"
                    style="height: 22px; padding: 0 6px; font-size: 11px;"
                    @click.stop="addGroup(group)"
                  >
                    + 添加该组
                  </button>
                  <span class="collapse-state-tag">{{ isGroupExpanded(group.name) ? '折叠' : '展开' }}</span>
                </div>
              </div>

              <div v-show="isGroupExpanded(group.name)" class="collapse-content-list">
                <div
                  v-for="p in group.items"
                  :key="p.id"
                  class="flat-perm-row available"
                  @click="add(p)"
                >
                  <div class="row-left-content">
                    <span class="level-pill">{{ lvBadge(p) }}</span>
                    <div class="perm-title-group">
                      <span class="perm-name">{{ title(p) }}</span>
                      <span class="perm-obj-act">{{ obj(p.object) }} · {{ act(p.action) }}</span>
                    </div>
                  </div>
                  <div class="row-right-meta">
                    <button class="btn-aliyun-cta" type="button" @click.stop="add(p)">
                      + 授权
                    </button>
                  </div>
                </div>
              </div>
            </div>

            <div v-if="!availableCount" class="compact-empty">
              <span>该用户已拥有全部权限点</span>
            </div>
            <div v-else-if="!groups.length" class="compact-empty">
              <span>{{ kw ? '无匹配的权限项目' : '暂无可分配权限' }}</span>
            </div>
          </div>
        </section>
      </div>
    </div>

    <!-- 4. 抽屉底部固定操作栏 (44px 紧凑单行，1px 顶边框) -->
    <template #footer>
      <div class="pd-drawer-footer">
        <div class="footer-status-text">
          <span :class="['status-msg', { highlight: dirty }]">
            {{ dirty ? '● 存在未保存修改，保存后立即生效' : '● 当前权限已是最新状态' }}
          </span>
        </div>
        <div class="footer-actions">
          <button class="btn-aliyun" type="button" @click="close">
            关闭
          </button>
          <button v-if="dirty" class="btn-aliyun" type="button" @click="undo">
            撤销变更
          </button>
          <button
            class="btn-primary-blue"
            type="button"
            :disabled="!dirty || saving"
            @click="save"
          >
            {{ saving ? '正在保存...' : '保存权限配置' }}
          </button>
        </div>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps<{ modelValue: boolean; user: any | null }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; saved: [] }>()

const dict = ref<any[]>([])
const ids = ref<number[]>([])
const orig = ref<number[]>([])
const loading = ref(false)
const saving = ref(false)
const kw = ref('')

// 折叠展开状态控制
const inheritedExpanded = ref(true)
const explicitExpanded = ref(true)
const expandedGroups = ref<Record<string, boolean>>({
  '概览': true,
  '设备与资源': true,
  '数据资产': true,
  '流程与任务': true,
  '安全与约束': true,
  '系统与权限': true,
  '其他': true
})

const allOwnedExpanded = computed(() => inheritedExpanded.value && explicitExpanded.value)
const toggleAllOwned = () => {
  const next = !allOwnedExpanded.value
  inheritedExpanded.value = next
  explicitExpanded.value = next
}

const allAvailExpanded = computed(() => Object.values(expandedGroups.value).every(Boolean))
const toggleAllAvail = () => {
  const next = !allAvailExpanded.value
  cats.forEach(c => {
    expandedGroups.value[c] = next
  })
}

const isGroupExpanded = (groupName: string) => expandedGroups.value[groupName] !== false
const toggleGroup = (groupName: string) => {
  expandedGroups.value[groupName] = !isGroupExpanded(groupName)
}

const lv = computed(() => {
  const rl = ({ system_admin: 4, lab_admin: 3, researcher: 2, observer: 1 } as any)[props.user?.roleName || props.user?.role || ''] || 1
  return Number(props.user?.userLevel ?? rl)
})

const isExp = (p: any) => ids.value.includes(Number(p.id))
const isInh = (p: any) => p.permissionLevel == null || lv.value >= Number(p.permissionLevel)
const isInherited = (p: any) => isInh(p)

const inherited = computed(() => dict.value.filter(isInh).sort(sort))
const explicit = computed(() => dict.value.filter(isExp).sort(sort))
const ownedCount = computed(() => dict.value.filter((p: any) => isInh(p) || isExp(p)).length)
const availableCount = computed(() => dict.value.filter((p: any) => !isInh(p) && !isExp(p)).length)

const sn = computed(() => [...new Set(ids.value.map(Number))].sort((a, b) => a - b))
const on = computed(() => [...new Set(orig.value.map(Number))].sort((a, b) => a - b))
const dirty = computed(() => JSON.stringify(sn.value) !== JSON.stringify(on.value))
const added = computed(() => sn.value.filter((id: number) => !on.value.includes(id)).length)
const removed = computed(() => on.value.filter((id: number) => !sn.value.includes(id)).length)

const visibleAvail = computed(() => {
  const q = kw.value.trim().toLowerCase()
  return dict.value.filter((p: any) => !isInh(p) && !isExp(p) && (!q || [title(p), p.object, p.action].join(' ').toLowerCase().includes(q))).sort(sort)
})

const cats = ['概览', '设备与资源', '数据资产', '流程与任务', '安全与约束', '系统与权限', '其他']
const groups = computed(() => cats.map(n => ({
  name: n,
  items: visibleAvail.value.filter((p: any) => cat(p.object) === n)
})).filter(g => g.items.length))

function sort(a: any, b: any) {
  return (Number(a.permissionLevel ?? 0) - Number(b.permissionLevel ?? 0)) || (Number(a.id) - Number(b.id))
}

function cat(o: string) {
  if (o === 'dashboard') return '概览'
  if (['device_category', 'device_model', 'device_instance', 'device_component', 'adapter', 'resource', 'scene'].includes(o)) return '设备与资源'
  if (['data_template', 'data_dataset', 'property_type'].includes(o)) return '数据资产'
  if (['workflow', 'task'].includes(o)) return '流程与任务'
  if (['constraint_rule', 'violation_log'].includes(o)) return '安全与约束'
  if (['user', 'permission', 'all'].includes(o)) return '系统与权限'
  return '其他'
}

function obj(o: string) {
  return ({
    all: '全部',
    dashboard: '概览',
    device_category: '设备类别',
    device_model: '设备模型',
    device_instance: '设备实例',
    device_component: '组件',
    adapter: '执行代理',
    data_template: '数据模板',
    data_dataset: '数据集',
    property_type: '字段类型',
    workflow: '工作流',
    task: '任务',
    constraint_rule: '约束规则',
    violation_log: '违规记录',
    scene: '场景',
    resource: '资源',
    user: '用户',
    permission: '权限'
  } as any)[o] || o
}

function act(a: string) {
  return ({
    all: '全部',
    view: '查看',
    create: '创建',
    edit: '编辑',
    delete: '删除',
    control: '控制',
    export: '导出',
    start: '启动',
    stop: '停止',
    assign: '分配'
  } as any)[a] || a
}

function title(p: any) {
  return p.permisDesc || obj(p.object) + ' · ' + act(p.action)
}

function lvBadge(p: any) {
  return p.permissionLevel == null ? 'L1' : 'L' + p.permissionLevel
}

const role = (u: any) => u?.roleName || u?.role || ''
const roleLabel = (r: string) => ({
  system_admin: '系统管理员',
  lab_admin: '实验室管理员',
  researcher: '研究员',
  observer: '观察员'
} as any)[r] || r

async function load() {
  if (!props.user) return
  loading.value = true
  kw.value = ''
  try {
    const [dr, ur] = await Promise.all([
      dict.value.length ? Promise.resolve(null) : axios.get('/api/user/permissions/list'),
      axios.get('/api/user/permissions/by-user/' + props.user.id)
    ])
    if (dr?.data?.success) dict.value = dr.data.data || []
    if (ur.data?.success) {
      const i = (ur.data.data || []).map(Number)
      ids.value = i
      orig.value = [...i]
    }
  } catch {
    ElMessage.error('加载权限列表失败')
  } finally {
    loading.value = false
  }
}

watch(() => [props.modelValue, props.user], ([v, u]) => {
  if (v && u) load()
})

function add(p: any) {
  if (isInh(p)) return
  ids.value = [...new Set([...ids.value, Number(p.id)])]
}

function addGroup(group: any) {
  const toAdd = group.items.map((p: any) => Number(p.id))
  ids.value = [...new Set([...ids.value, ...toAdd])]
}

function remove(p: any) {
  ids.value = ids.value.filter((i: number) => Number(i) !== Number(p.id))
}

async function addAll() {
  const c = visibleAvail.value.length
  if (!c) return
  if (c > 1) {
    try {
      await ElMessageBox.confirm(`确认将当前筛选出的 ${c} 项权限一键添加给该用户？`, '批量授权', {
        type: 'warning',
        confirmButtonText: '确认添加',
        cancelButtonText: '取消'
      })
    } catch {
      return
    }
  }
  ids.value = [...new Set([...ids.value, ...visibleAvail.value.map((p: any) => Number(p.id))])]
}

function undo() {
  ids.value = [...orig.value]
}

async function onBeforeClose(done: () => void) {
  if (!dirty.value) return done()
  try {
    await ElMessageBox.confirm('存在未保存的权限修改，确定放弃？', '提示', {
      type: 'warning',
      confirmButtonText: '放弃修改',
      cancelButtonText: '继续编辑'
    })
    undo()
    done()
  } catch {}
}

async function close() {
  await onBeforeClose(() => {
    emit('update:modelValue', false)
  })
}

async function save() {
  if (!props.user || !dirty.value) return
  saving.value = true
  try {
    const lr = await axios.get('/api/user/permissions/by-user/' + props.user.id)
    const latest = [...new Set((lr.data?.data || []).map(Number))].sort((a: number, b: number) => a - b)
    if (JSON.stringify(latest) !== JSON.stringify(on.value)) {
      ElMessage.warning('权限配置已由他人更新，请重新加载')
      ids.value = latest
      orig.value = [...latest]
      saving.value = false
      return
    }
    await axios.post('/api/user/permissions/assign', {
      userId: props.user.id,
      permissionIds: sn.value
    })
    ElMessage.success('权限配置已保存')
    orig.value = [...sn.value]
    emit('saved')
    emit('update:modelValue', false)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存权限配置失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
/* ==========================================================================
   权限配置抽屉 - 平铺折叠架构 · 零图标 · 严格对齐设备模型/实例页面规范
   ========================================================================== */
.pd-drawer-header {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.header-title-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.drawer-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.drawer-user-meta {
  font-size: 12px;
  color: #64748b;
}

.pd-drawer-body {
  height: calc(100vh - 110px);
  display: flex;
  flex-direction: column;
  background: #ffffff;
  overflow: hidden;
  box-sizing: border-box;
}

/* 1. 顶部指标横幅 (35px) */
.pd-metric-ribbon {
  height: 35px;
  padding: 0 16px;
  border-bottom: 1px solid #e2e8f0;
  background: #fafbfc;
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.metric-item {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 11.5px;
  color: #64748b;
}

.metric-item strong {
  font-size: 13px;
  font-weight: 700;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
.metric-item strong.blue { color: #2563eb; }
.metric-item strong.slate { color: #475569; }
.metric-item strong.amber { color: #d97706; }
.metric-item strong.green { color: #16a34a; }

.metric-sep {
  color: #e2e8f0;
}

/* 未保存提示条 */
.pd-dirty-alert {
  padding: 6px 16px;
  background: #fffbeb;
  border-bottom: 1px solid #fde68a;
  color: #92400e;
  font-size: 11.5px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

/* 2. 双列平铺布局 */
.pd-columns-layout {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: #ffffff;
}

.pd-panel-box {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-right: 1px solid #e2e8f0;
  background: #ffffff;
}
.pd-panel-box:last-child {
  border-right: none;
}

.panel-head-bar {
  height: 34px;
  padding: 0 14px;
  border-bottom: 1px solid #e2e8f0;
  background: #fafbfc;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.panel-head-title {
  font-size: 12px;
  font-weight: 700;
  color: #0f172a;
}

.panel-head-ops {
  display: flex;
  align-items: center;
}

.panel-search-bar {
  padding: 6px 12px;
  border-bottom: 1px solid #e2e8f0;
  background: #ffffff;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.panel-search-bar .search-input {
  flex: 1;
}

.panel-scroll-content {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

/* 3. 平铺折叠框结构 (Flat Collapsible Box) */
.collapse-perm-box {
  display: flex;
  flex-direction: column;
  border-bottom: 1px solid #e2e8f0;
}

.collapse-head-bar {
  height: 32px;
  padding: 0 12px;
  background: #f8fafc;
  border-bottom: 1px solid #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}
.collapse-head-bar:hover {
  background: #f1f5f9;
}

.collapse-title-left {
  display: flex;
  align-items: center;
  gap: 6px;
}

.expand-arrow {
  font-size: 10px;
  color: #64748b;
  width: 12px;
  display: inline-block;
}

.collapse-title-text {
  font-size: 12px;
  font-weight: 700;
  color: #334155;
}

.collapse-badge {
  font-size: 10.5px;
  color: #64748b;
  background: #e2e8f0;
  padding: 1px 5px;
  border-radius: 3px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
.collapse-badge.active {
  color: #2563eb;
  background: #eff6ff;
}

.collapse-head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.collapse-state-tag {
  font-size: 11px;
  color: #94a3b8;
}

.collapse-content-list {
  display: flex;
  flex-direction: column;
}

.flat-perm-row {
  min-height: 38px;
  padding: 6px 14px;
  border-bottom: 1px solid #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  background: #ffffff;
  transition: background 0.15s;
}
.flat-perm-row:hover {
  background: #f8fafc;
}
.flat-perm-row.inherited {
  background: #fafbfc;
}
.flat-perm-row.available {
  cursor: pointer;
}
.flat-perm-row.available:hover {
  background: #eff6ff;
}

.row-left-content {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}

.level-pill {
  font-size: 10.5px;
  font-weight: 700;
  color: #64748b;
  background: #f1f5f9;
  padding: 1px 5px;
  border-radius: 3px;
  flex-shrink: 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
.level-pill.active {
  color: #2563eb;
  background: #eff6ff;
}

.perm-title-group {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.perm-title-top {
  display: flex;
  align-items: center;
  gap: 6px;
}

.perm-name {
  font-size: 12.5px;
  font-weight: 600;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.perm-obj-act {
  font-size: 11px;
  color: #64748b;
  margin-top: 1px;
}

.role-overlap-pill {
  font-size: 10px;
  color: #d97706;
  background: #fffbeb;
  border: 1px solid #fde68a;
  padding: 0 4px;
  border-radius: 2px;
}

.row-right-meta {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.role-inherit-tag {
  font-size: 11px;
  color: #94a3b8;
}

.empty-section-hint {
  padding: 12px 14px;
  font-size: 11.5px;
  color: #94a3b8;
  text-align: center;
}

.compact-empty {
  padding: 28px 0;
  text-align: center;
  font-size: 11.5px;
  color: #94a3b8;
}

/* 4. 抽屉底部 (44px) */
.pd-drawer-footer {
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-top: 1px solid #e2e8f0;
  background: #fafbfc;
  box-sizing: border-box;
}

.footer-status-text {
  font-size: 11.5px;
}
.status-msg { color: #64748b; }
.status-msg.highlight { color: #d97706; font-weight: 600; }

.footer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 四级按钮体系标准 (对齐系统 UI 设计规范) */
.btn-primary-blue {
  height: 28px;
  padding: 0 14px;
  font-size: 12px;
  font-weight: 600;
  color: #ffffff;
  background: #2563eb;
  border: 1px solid #2563eb;
  border-radius: 4px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
}
.btn-primary-blue:hover:not(:disabled) {
  background: #1d4ed8;
  border-color: #1d4ed8;
}
.btn-primary-blue:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-aliyun-cta {
  height: 26px;
  padding: 0 10px;
  font-size: 11.5px;
  font-weight: 600;
  color: #2563eb;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 4px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
}
.btn-aliyun-cta:hover {
  background: #dbeafe;
  border-color: #93c5fd;
  color: #1d4ed8;
}

.btn-aliyun {
  height: 28px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 500;
  color: #334155;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
}
.btn-aliyun:hover {
  border-color: #94a3b8;
  background: #f8fafc;
  color: #0f172a;
}

.btn-link {
  font-size: 12px;
  font-weight: 500;
  color: #2563eb;
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 0;
  transition: color 0.15s;
}
.btn-link:hover {
  color: #1d4ed8;
  text-decoration: underline;
}
.btn-link.danger {
  color: #dc2626;
}
.btn-link.danger:hover {
  color: #b91c1c;
}
</style>