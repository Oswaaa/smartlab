<template>
  <div class="um-page">
    <div class="um-toolbar">
      <div class="um-toolbar-left">
        <h2>用户管理</h2>
        <span class="um-count">{{ users.length }} 个账号</span>
      </div>
      <el-button type="primary" @click="openCreate">新建用户</el-button>
    </div>

    <UserTablePanel
      :users="users"
      :loading="loading"
      :deleting-id="deletingId"
      @refresh="load"
      @create="openCreate"
      @edit="openEdit"
      @permissions="openPermissions"
      @command="onCommand"
    />

    <UserFormDialog v-model="createVisible" mode="create" :loading="saving" @submit="doCreate" />
    <UserFormDialog v-model="editVisible" mode="edit" :user="editTarget" :loading="saving" @submit="doEdit" />

    <PermissionDrawer v-model="permVisible" :user="permUser" @saved="load" />

    <UserTaskHistoryDrawer v-model="historyVisible" :user-name="historyUser?.userName" :tasks="taskHistory" :loading="loadingHistory" @refresh="loadHistory" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import UserTablePanel from './components/UserTablePanel.vue'
import UserFormDialog from './components/UserFormDialog.vue'
import PermissionDrawer from './components/PermissionDrawer.vue'
import UserTaskHistoryDrawer from './components/UserTaskHistoryDrawer.vue'

const users = ref<any[]>([])
const loading = ref(false)
const saving = ref(false)
const deletingId = ref<number | null>(null)

const createVisible = ref(false)
const editVisible = ref(false)
const editTarget = ref<any>(null)

const permVisible = ref(false)
const permUser = ref<any>(null)

const historyVisible = ref(false)
const historyUser = ref<any>(null)
const taskHistory = ref<any[]>([])
const loadingHistory = ref(false)

async function load() {
  loading.value = true
  try { const r = await axios.get('/api/user/list'); users.value = r.data?.success ? (r.data.data || []) : [] } catch {} finally { loading.value = false }
}

function openCreate() { createVisible.value = true }
function openEdit(user: any) { editTarget.value = user; editVisible.value = true }
function openPermissions(user: any) { permUser.value = user; permVisible.value = true }

async function doCreate(payload: any) {
  saving.value = true
  try {
    await axios.post('/api/user/register', { userName: payload.userName, password: payload.password, roleName: payload.roleName, lab: payload.lab, userBasicInfo: { description: payload.description } })
    ElMessage.success('已创建'); createVisible.value = false; load()
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '失败') } finally { saving.value = false }
}

async function doEdit(payload: any) {
  saving.value = true
  try {
    const body: any = { id: payload.id, userName: payload.userName.trim(), roleName: payload.roleName, lab: payload.lab.trim(), userBasicInfo: { description: payload.description?.trim() || payload.roleName } }
    if (payload.password?.trim()) body.password = payload.password
    await axios.post('/api/user/update', body)
    ElMessage.success('已更新'); editVisible.value = false; load()
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '失败') } finally { saving.value = false }
}

async function onCommand(cmd: string, user: any) {
  if (cmd === 'history') { historyUser.value = user; historyVisible.value = true; await loadHistory() }
  if (cmd === 'delete') {
    if (deletingId.value) return
    deletingId.value = user.id
    try {
      const [h, p] = await Promise.all([axios.get('/api/user/task-history/' + user.id), axios.get('/api/user/permissions/by-user/' + user.id)])
      const tc = (h.data?.data || []).length; const pc = (p.data?.data || []).length
      const msg = tc || pc ? `该账号有 ${tc} 条任务记录和 ${pc} 条授权，删除不可撤销。` : '删除不可撤销。'
      await ElMessageBox.confirm(msg, '删除 ' + user.userName, { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
      await axios.delete('/api/user/delete/' + user.id)
      ElMessage.success('已删除'); load()
    } catch (e: any) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.response?.data?.message || e?.message || '失败') } finally { deletingId.value = null }
  }
}

async function loadHistory() {
  if (!historyUser.value) return
  loadingHistory.value = true
  try { const r = await axios.get('/api/user/task-history/' + historyUser.value.id); taskHistory.value = r.data?.success ? (r.data.data || []) : [] } catch {} finally { loadingHistory.value = false }
}

onMounted(load)
</script>

<style scoped>
.um-page{height:calc(100vh - 64px);display:flex;flex-direction:column;padding:24px;box-sizing:border-box;overflow:hidden;background:#f5f6f8}
.um-toolbar{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;flex:none}
.um-toolbar-left{display:flex;align-items:baseline;gap:12px}
.um-toolbar-left h2{margin:0;font-size:20px;font-weight:700;color:#1a1d23}
.um-count{color:#8b95a5;font-size:13px}
.um-page>:deep(.console-table-shell){flex:1;min-height:0}
</style>