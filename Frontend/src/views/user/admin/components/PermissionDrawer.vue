<template>
  <el-drawer :model-value="modelValue" size="860px" destroy-on-close :before-close="onBeforeClose" @update:model-value="$emit('update:modelValue', $event)">
    <template #header>
      <div class="pd-head">
        <strong>权限配置</strong>
        <span v-if="user">{{ user.userName }} · {{ roleLabel(role(user)) }} · {{ user.lab || '未分配实验室' }}</span>
      </div>
    </template>

    <div class="pd-body" v-loading="loading">
      <!-- Stats row -->
      <div class="pd-stats">
        <div class="pd-stat"><b>{{ ownedCount }}</b><span>已拥有</span></div>
        <div class="pd-stat"><b>{{ inherited.length }}</b><span>角色默认</span></div>
        <div class="pd-stat"><b>{{ explicit.length }}</b><span>直接授权</span></div>
        <div class="pd-stat"><b>{{ availableCount }}</b><span>可分配</span></div>
      </div>

      <!-- Change notice -->
      <div v-if="dirty" class="pd-notice">
        <span>有未保存的更改（+{{ added }} -{{ removed }}）</span>
        <el-button link size="small" @click="undo">撤销</el-button>
      </div>

      <!-- Dual column -->
      <div class="pd-cols">
        <!-- Owned -->
        <section class="pd-panel">
          <div class="pd-panel-hd"><strong>已拥有权限</strong><span>{{ ownedCount }} 项</span></div>
          <el-scrollbar class="pd-scroll">
            <!-- Inherited -->
            <div class="pd-section">
              <div class="pd-section-hd"><span>角色默认</span><small>{{ inherited.length }} 项，不可撤销</small></div>
              <div v-for="p in inherited" :key="p.id" class="pd-row inherited">
                <span class="pd-lv">{{ lvBadge(p) }}</span>
                <div class="pd-info"><strong>{{ title(p) }}</strong><span>{{ obj(p.object) }} · {{ act(p.action) }}</span></div>
                <el-icon class="pd-lock"><Lock /></el-icon>
              </div>
              <div v-if="!inherited.length" class="pd-empty">该角色无默认权限</div>
            </div>
            <!-- Explicit -->
            <div class="pd-section">
              <div class="pd-section-hd"><span>直接授权</span><small>{{ explicit.length }} 项</small></div>
              <div v-for="p in explicit" :key="p.id" class="pd-row" :class="{ dup: isInherited(p) }">
                <span class="pd-lv">{{ lvBadge(p) }}</span>
                <div class="pd-info">
                  <div class="pd-info-top"><strong>{{ title(p) }}</strong><el-tag v-if="isInherited(p)" size="small" type="warning" effect="plain">角色已覆盖</el-tag></div>
                  <span>{{ obj(p.object) }} · {{ act(p.action) }}</span>
                </div>
                <el-button link type="danger" size="small" @click="remove(p)">撤销</el-button>
              </div>
              <div v-if="!explicit.length" class="pd-empty">无直接授权</div>
            </div>
          </el-scrollbar>
        </section>

        <!-- Available -->
        <section class="pd-panel">
          <div class="pd-panel-hd"><strong>可分配权限</strong><span>{{ availableCount }} 项</span></div>
          <div class="pd-search-bar">
            <el-input v-model="kw" placeholder="搜索权限..." clearable size="small" />
            <el-button v-if="visibleAvail.length" size="small" type="primary" plain @click="addAll">添加 {{ visibleAvail.length }} 项</el-button>
          </div>
          <el-scrollbar class="pd-scroll">
            <div v-for="group in groups" :key="group.name" class="pd-section">
              <div class="pd-section-hd"><span>{{ group.name }}</span><small>{{ group.items.length }} 项</small></div>
              <div v-for="p in group.items" :key="p.id" class="pd-row available" @click="add(p)">
                <span class="pd-lv">{{ lvBadge(p) }}</span>
                <div class="pd-info"><strong>{{ title(p) }}</strong><span>{{ obj(p.object) }} · {{ act(p.action) }}</span></div>
                <span class="pd-add">+ 添加</span>
              </div>
            </div>
            <el-empty v-if="!availableCount" description="已拥有全部权限" :image-size="48" />
            <el-empty v-else-if="!groups.length" :description="kw ? '无匹配权限' : ''" :image-size="48" />
          </el-scrollbar>
        </section>
      </div>
    </div>

    <template #footer>
      <div class="pd-footer">
        <span>{{ dirty ? '保存后生效' : '无变更' }}</span>
        <div class="pd-footer-actions">
          <el-button @click="close">关闭</el-button>
          <el-button v-if="dirty" @click="undo">撤销</el-button>
          <el-button type="primary" :loading="saving" :disabled="!dirty" @click="save">保存权限</el-button>
        </div>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'

const props = defineProps<{ modelValue: boolean; user: any | null }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; saved: [] }>()

const dict = ref<any[]>([])
const ids = ref<number[]>([])
const orig = ref<number[]>([])
const loading = ref(false)
const saving = ref(false)
const kw = ref('')

  const lv = computed(() => { const rl = ({ system_admin: 4, lab_admin: 3, researcher: 2, observer: 1 } as any)[props.user?.roleName || props.user?.role || ''] || 1; return Number(props.user?.userLevel ?? rl) })
const isExp = (p: any) => ids.value.includes(Number(p.id))
const isInh = (p: any) => p.permissionLevel == null || lv.value >= Number(p.permissionLevel)
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
const groups = computed(() => cats.map(n => ({ name: n, items: visibleAvail.value.filter((p: any) => cat(p.object) === n) })).filter(g => g.items.length))

function sort(a: any, b: any) { return (Number(a.permissionLevel ?? 0) - Number(b.permissionLevel ?? 0)) || (Number(a.id) - Number(b.id)) }
function cat(o: string) { if (o === 'dashboard') return '概览'; if (['device_category','device_model','device_instance','device_component','adapter','resource','scene'].includes(o)) return '设备与资源'; if (['data_template','data_dataset','property_type'].includes(o)) return '数据资产'; if (['workflow','task'].includes(o)) return '流程与任务'; if (['constraint_rule','violation_log'].includes(o)) return '安全与约束'; if (['user','permission','all'].includes(o)) return '系统与权限'; return '其他' }
function obj(o: string) { return ({ all:'全部',dashboard:'概览',device_category:'设备类别',device_model:'设备模型',device_instance:'设备实例',device_component:'组件',adapter:'适配器',data_template:'数据模板',data_dataset:'数据集',property_type:'字段类型',workflow:'工作流',task:'任务',constraint_rule:'约束规则',violation_log:'违规记录',scene:'场景',resource:'资源',user:'用户',permission:'权限' } as any)[o] || o }
function act(a: string) { return ({ all:'全部',view:'查看',create:'创建',edit:'编辑',delete:'删除',control:'控制',export:'导出',start:'启动',stop:'停止',assign:'分配' } as any)[a] || a }
function title(p: any) { return p.permisDesc || obj(p.object) + '·' + act(p.action) }
function lvBadge(p: any) { return p.permissionLevel == null ? '—' : 'L' + p.permissionLevel }
const role = (u: any) => u?.roleName || u?.role || ''
const roleLabel = (r: string) => ({ system_admin:'系统管理员',lab_admin:'实验室管理员',researcher:'研究员',observer:'观察员' } as any)[r] || r

async function load() {
  if (!props.user) return
  loading.value = true; kw.value = ''
  try {
    const [dr, ur] = await Promise.all([dict.value.length ? Promise.resolve(null) : axios.get('/api/user/permissions/list'), axios.get('/api/user/permissions/by-user/' + props.user.id)])
    if (dr?.data?.success) dict.value = dr.data.data || []
    if (ur.data?.success) { const i = (ur.data.data || []).map(Number); ids.value = i; orig.value = [...i] }
  } catch (e: any) { ElMessage.error('加载失败') } finally { loading.value = false }
}
watch(() => [props.modelValue, props.user], ([v, u]) => { if (v && u) load() })

function add(p: any) { if (isInh(p)) return; ids.value = [...new Set([...ids.value, Number(p.id)])] }
function remove(p: any) { ids.value = ids.value.filter((i: number) => Number(i) !== Number(p.id)) }
async function addAll() {
  const c = visibleAvail.value.length; if (!c) return
  if (c > 1) { try { await ElMessageBox.confirm('将添加 ' + c + ' 项权限', '批量授权', { type: 'warning', confirmButtonText: '确认' }) } catch { return } }
  ids.value = [...new Set([...ids.value, ...visibleAvail.value.map((p: any) => Number(p.id))])]
}
function undo() { ids.value = [...orig.value] }

async function onBeforeClose(done: () => void) { if (!dirty.value) return done(); try { await ElMessageBox.confirm('有未保存更改，确定放弃？', '', { type: 'warning', confirmButtonText: '放弃' }); undo(); done() } catch {} }
async function close() { await onBeforeClose(() => { emit('update:modelValue', false) }) }

async function save() {
  if (!props.user || !dirty.value) return
  saving.value = true
  try {
    const lr = await axios.get('/api/user/permissions/by-user/' + props.user.id)
    const latest = [...new Set((lr.data?.data || []).map(Number))].sort((a: number, b: number) => a - b)
    if (JSON.stringify(latest) !== JSON.stringify(on.value)) { ElMessage.warning('权限已被他人修改，请重新打开'); ids.value = latest; orig.value = [...latest]; saving.value = false; return }
    await axios.post('/api/user/permissions/assign', { userId: props.user.id, permissionIds: sn.value })
    ElMessage.success('已保存'); orig.value = [...sn.value]; emit('saved'); emit('update:modelValue', false)
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '保存失败') } finally { saving.value = false }
}
</script>

<style scoped>
.pd-head{display:grid;gap:2px}.pd-head strong{font-size:17px;color:#1a1d23}.pd-head span{color:#8b95a5;font-size:12px}

.pd-body{padding:0 24px;display:flex;flex-direction:column;gap:16px;height:calc(100vh - 180px)}

.pd-stats{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}
.pd-stat{padding:12px 14px;border:1px solid #e8eaed;border-radius:6px;background:#fff;display:grid;gap:2px}
.pd-stat b{font-size:20px;color:#1a1d23}
.pd-stat span{font-size:11px;color:#8b95a5}

.pd-notice{display:flex;align-items:center;justify-content:space-between;padding:8px 12px;border:1px solid #fde68a;border-radius:6px;background:#fffbeb;font-size:12px;color:#92400e}

.pd-cols{flex:1;min-height:0;display:grid;grid-template-columns:1fr 1fr;gap:12px}

.pd-panel{border:1px solid #e8eaed;border-radius:6px;background:#fff;display:flex;flex-direction:column;min-height:0;overflow:hidden}
.pd-panel-hd{display:flex;align-items:center;justify-content:space-between;padding:10px 14px;border-bottom:1px solid #edf0f3;font-size:12px}
.pd-panel-hd strong{font-size:13px;color:#3a3f4a}
.pd-panel-hd span{color:#8b95a5;font-size:10px}

.pd-search-bar{display:flex;align-items:center;gap:8px;padding:8px 14px;border-bottom:1px solid #edf0f3}
.pd-search-bar .el-input{flex:1}

.pd-scroll{flex:1;min-height:0}
.pd-section{padding:0}.pd-section+.pd-section{border-top:1px solid #f0f1f3}
.pd-section-hd{display:flex;align-items:center;justify-content:space-between;padding:8px 14px 4px;font-size:11px;color:#8b95a5}
.pd-section-hd span{font-weight:600;color:#5f6b7a}
.pd-section-hd small{color:#a0a9b5}

.pd-row{display:flex;align-items:flex-start;gap:10px;padding:7px 14px;cursor:default}
.pd-row.inherited{opacity:.85}
.pd-row.dup{background:#fffdf5}
.pd-row.available{cursor:pointer}
.pd-row.available:hover{background:#f5f8fc}
.pd-row+.pd-row{border-top:1px solid #f5f6f8}

.pd-lv{min-width:22px;padding:0 4px;border-radius:3px;background:#eef2f7;color:#6b7280;text-align:center;font-size:9px;font-weight:700;line-height:18px;flex:none}
.pd-info{flex:1;min-width:0;display:grid;gap:2px}
.pd-info strong{font-size:12px;color:#1f2937;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.pd-info span{font-size:11px;color:#8b95a5}
.pd-info-top{display:flex;align-items:center;gap:6px}
.pd-lock{color:#a0a9b5;flex:none;margin-top:2px}
.pd-add{color:#2563eb;font-size:11px;font-weight:600;flex:none;white-space:nowrap}
.pd-empty{padding:20px;text-align:center;color:#a0a9b5;font-size:12px}

.pd-footer{display:flex;align-items:center;justify-content:space-between;padding:0 24px;height:56px}
.pd-footer>span{color:#8b95a5;font-size:12px}
.pd-footer-actions{display:flex;gap:8px}

@media(max-width:860px){.pd-cols{grid-template-columns:1fr}.pd-stats{grid-template-columns:repeat(2,1fr)}}
</style>