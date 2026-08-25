<template>
  <div class="um-page-canvas">
    <!-- 超级大卡片工作台主容器 (对齐设备模型/实例页面统一标准，内部全平铺) -->
    <div class="um-workbench-card">
      <!-- 1. 工作台头部工具栏 (44px 紧凑单行，1px 底边框，纯中文无多余图标) -->
      <header class="um-card-header">
        <div class="header-left-group">
          <h2 class="header-title">用户与权限中心</h2>
          <span class="count-pill">共 {{ users.length }} 个用户账号</span>
        </div>
        <div class="header-actions">
          <button class="btn-aliyun-cta" type="button" @click="openCreate">
            + 新建用户
          </button>
        </div>
      </header>

      <!-- 2. 主体平铺表格工作台 (通栏自适应，唯一局部滚动) -->
      <main class="um-card-body">
        <UserTablePanel
          :users="users"
          :loading="loading"
          :deleting-id="deletingId"
          @refresh="load"
          @edit="openEdit"
          @permissions="openPermissions"
          @command="onCommand"
        />
      </main>
    </div>

    <!-- 新建/编辑用户表单弹窗 -->
    <UserFormDialog v-model="createVisible" mode="create" :loading="saving" @submit="doCreate" />
    <UserFormDialog v-model="editVisible" mode="edit" :user="editTarget" :loading="saving" @submit="doEdit" />

    <!-- 权限平铺展开折叠抽屉 -->
    <PermissionDrawer v-model="permVisible" :user="permUser" @saved="load" />

    <!-- 任务执行历史平铺抽屉 -->
    <UserTaskHistoryDrawer
      v-model="historyVisible"
      :user-name="historyUser?.userName"
      :tasks="taskHistory"
      :loading="loadingHistory"
      @refresh="loadHistory"
    />
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
  try {
    const r = await axios.get('/api/user/list')
    users.value = r.data?.success ? (r.data.data || []) : []
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载用户列表失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  createVisible.value = true
}

function openEdit(user: any) {
  editTarget.value = user
  editVisible.value = true
}

function openPermissions(user: any) {
  permUser.value = user
  permVisible.value = true
}

async function doCreate(payload: any) {
  saving.value = true
  try {
    await axios.post('/api/user/register', {
      userName: payload.userName,
      password: payload.password,
      roleName: payload.roleName,
      lab: payload.lab,
      userBasicInfo: { description: payload.description }
    })
    ElMessage.success('用户已创建')
    createVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '创建用户失败')
  } finally {
    saving.value = false
  }
}

async function doEdit(payload: any) {
  saving.value = true
  try {
    const body: any = {
      id: payload.id,
      userName: payload.userName.trim(),
      roleName: payload.roleName,
      lab: payload.lab.trim(),
      userBasicInfo: { description: payload.description?.trim() || payload.roleName }
    }
    if (payload.password?.trim()) {
      body.password = payload.password
    }
    await axios.post('/api/user/update', body)
    ElMessage.success('用户资料已更新')
    editVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '更新用户失败')
  } finally {
    saving.value = false
  }
}

async function onCommand(cmd: string, user: any) {
  if (cmd === 'history') {
    historyUser.value = user
    historyVisible.value = true
    await loadHistory()
  }
  if (cmd === 'delete') {
    if (deletingId.value) return
    deletingId.value = user.id
    try {
      const [h, p] = await Promise.all([
        axios.get('/api/user/task-history/' + user.id),
        axios.get('/api/user/permissions/by-user/' + user.id)
      ])
      const tc = (h.data?.data || []).length
      const pc = (p.data?.data || []).length
      const msg = tc || pc
        ? `该账号关联了 ${tc} 条任务记录与 ${pc} 项授权，删除后不可恢复。是否确认删除？`
        : '删除该账号后不可恢复，是否确认删除？'
      await ElMessageBox.confirm(msg, `删除用户: ${user.userName}`, {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消'
      })
      await axios.delete('/api/user/delete/' + user.id)
      ElMessage.success('用户已删除')
      load()
    } catch (e: any) {
      if (e !== 'cancel' && e !== 'close') {
        ElMessage.error(e?.response?.data?.message || e?.message || '删除失败')
      }
    } finally {
      deletingId.value = null
    }
  }
}

async function loadHistory() {
  if (!historyUser.value) return
  loadingHistory.value = true
  try {
    const r = await axios.get('/api/user/task-history/' + historyUser.value.id)
    taskHistory.value = r.data?.success ? (r.data.data || []) : []
  } catch {
    taskHistory.value = []
  } finally {
    loadingHistory.value = false
  }
}

onMounted(load)
</script>

<style scoped>
/* ==========================================================================
   用户与权限管理中心 - 大卡片主画卷 · 100% 对齐设备模型与设备实例页面标准
   ========================================================================== */
.um-page-canvas {
  height: calc(100vh - 50px);
  padding: 10px 14px 14px;
  background-color: #f8fafc;
  box-sizing: border-box;
  overflow: hidden;
  display: flex;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "微软雅黑", sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* 超级大卡片工作台主容器 */
.um-workbench-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  overflow: hidden;
  min-height: 0;
}

/* 顶部工作台标题栏 (44px) */
.um-card-header {
  height: 44px;
  padding: 0 16px;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  flex-shrink: 0;
}

.header-left-group {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.header-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.count-pill {
  font-size: 11.5px;
  color: #64748b;
  background: #f1f5f9;
  padding: 2px 8px;
  border-radius: 4px;
  border: 1px solid #e2e8f0;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.um-card-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 四级按钮体系标准 (对齐系统 UI 设计规范) */
.btn-aliyun-cta {
  height: 28px;
  padding: 0 12px;
  font-size: 12px;
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
.btn-aliyun-cta:active {
  background: #bfdbfe;
  color: #1e40af;
}
</style>