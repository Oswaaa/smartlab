<template>
  <div class="user-page">
    <header class="page-header">
      <div>
        <div class="page-eyebrow">系统与权限</div>
        <h1>用户管理</h1>
        <p>管理系统成员、实验室归属和功能特权。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openRegisterDialog">新增用户</el-button>
    </header>

    <section class="summary-strip">
      <div><span>全部用户</span><strong>{{ users.length }}</strong></div>
      <div><span>管理员</span><strong>{{ administratorCount }}</strong></div>
      <div><span>研究人员</span><strong>{{ researcherCount }}</strong></div>
      <div><span>实验室</span><strong>{{ labCount }}</strong></div>
    </section>

    <section class="table-shell">
      <div class="table-toolbar">
        <div class="toolbar-filters">
          <el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="搜索用户名、实验室或说明" class="user-search" />
          <el-select v-model="roleFilter" clearable placeholder="全部角色" class="role-filter">
            <el-option label="系统管理员" value="system_admin" />
            <el-option label="实验室管理员" value="lab_admin" />
            <el-option label="研究员" value="researcher" />
            <el-option label="观察员" value="observer" />
          </el-select>
        </div>
        <span class="result-count">{{ filteredUsers.length }} 个用户</span>
      </div>

      <el-table :data="filteredUsers" v-loading="loadingUsers" height="100%" class="user-table" @row-click="openPermissionDialog">
        <el-table-column prop="id" label="ID" width="72" />
        <el-table-column label="用户" min-width="190">
          <template #default="{ row }">
            <div class="user-identity">
              <span class="user-avatar">{{ String(row.userName || '?').slice(0, 1).toUpperCase() }}</span>
              <div><strong>{{ row.userName }}</strong><span>{{ row.userBasicInfo?.description || '暂无说明' }}</span></div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="130">
          <template #default="{ row }"><el-tag size="small" effect="plain" :type="roleTagType(row.roleName)">{{ roleLabel(row.roleName) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="lab" label="所属实验室" min-width="160"><template #default="{ row }">{{ row.lab || '-' }}</template></el-table-column>
        <el-table-column label="说明" min-width="220"><template #default="{ row }">{{ row.userBasicInfo?.description || '-' }}</template></el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <div class="row-actions" @click.stop>
              <el-button link type="primary" @click="openPermissionDialog(row)">权限</el-button>
              <el-button link @click="viewTaskHistory(row)">任务记录</el-button>
              <el-popconfirm title="确认删除该用户？" @confirm="deleteUser(row.id)">
                <template #reference><el-button link type="danger">删除</el-button></template>
              </el-popconfirm>
            </div>
          </template>
        </el-table-column>
        <template #empty><el-empty description="没有匹配的用户" :image-size="72" /></template>
      </el-table>
    </section>

    <el-dialog v-model="registerDialogVisible" title="新增用户" width="520px" destroy-on-close>
      <el-form :model="registerForm" :rules="registerRules" ref="registerFormRef" label-position="top">
        <div class="form-grid">
          <el-form-item label="用户名" prop="userName"><el-input v-model="registerForm.userName" placeholder="登录系统使用的唯一名称" /></el-form-item>
          <el-form-item label="角色" prop="roleName"><el-select v-model="registerForm.roleName" style="width: 100%"><el-option label="系统管理员" value="system_admin" /><el-option label="实验室管理员" value="lab_admin" /><el-option label="研究员" value="researcher" /><el-option label="观察员" value="observer" /></el-select></el-form-item>
          <el-form-item label="密码" prop="password"><el-input v-model="registerForm.password" type="password" show-password /></el-form-item>
          <el-form-item label="实验室" prop="lab"><el-input v-model="registerForm.lab" placeholder="例如：有机合成实验室" /></el-form-item>
        </div>
        <el-form-item label="说明"><el-input v-model="registerForm.description" type="textarea" :rows="3" placeholder="记录课题组、岗位或管理范围" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="registerDialogVisible = false">取消</el-button><el-button type="primary" :loading="registering" @click="submitRegister">创建用户</el-button></template>
    </el-dialog>

    <el-drawer v-model="permissionDrawerVisible" :title="`用户权限 · ${currentUser?.userName || ''}`" size="720px" class="unified-workflow-drawer">
      <div class="permission-body" v-loading="loadingPermissions">
        <div class="drawer-context"><strong>{{ currentUser?.userName }}</strong><span>{{ roleLabel(currentUser?.roleName) }} · {{ currentUser?.lab || '未分配实验室' }}</span></div>
        <div class="section-heading"><div><h3>功能特权</h3><p>勾选该用户可以执行的系统动作。</p></div><el-tag effect="plain">已选择 {{ selectedPermissionIds.length }} 项</el-tag></div>
        <el-checkbox-group v-model="selectedPermissionIds" class="perm-grid">
          <el-checkbox v-for="perm in permissionDict" :key="perm.id" :label="perm.id" border class="perm-item"><strong>{{ perm.object }}</strong><span>{{ perm.action }}</span></el-checkbox>
        </el-checkbox-group>
      </div>
      <template #footer><el-button @click="permissionDrawerVisible = false">取消</el-button><el-button type="primary" :loading="savingPermissions" @click="savePermissions">保存权限</el-button></template>
    </el-drawer>

    <el-drawer v-model="historyDrawerVisible" :title="`任务记录 · ${currentUser?.userName || ''}`" size="78%" class="unified-workflow-drawer">
      <div class="history-body">
        <el-table :data="userHistory" v-loading="loadingHistory" height="100%">
          <el-table-column prop="taskId" label="任务 ID" width="100" />
          <el-table-column prop="taskName" label="任务名称" min-width="200" />
          <el-table-column prop="flowModelId" label="流程" min-width="130" />
          <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag size="small" effect="plain">{{ row.taskStatus || '-' }}</el-tag></template></el-table-column>
          <el-table-column label="开始时间" min-width="170"><template #default="{ row }">{{ formatTime(row.startTime) }}</template></el-table-column>
          <el-table-column label="结束时间" min-width="170"><template #default="{ row }">{{ formatTime(row.endTime) }}</template></el-table-column>
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'

const users = ref<any[]>([])
const loadingUsers = ref(false)
const keyword = ref('')
const roleFilter = ref('')

const filteredUsers = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return users.value.filter(user => {
    const matchesRole = !roleFilter.value || user.roleName === roleFilter.value
    const haystack = [user.userName, user.lab, user.userBasicInfo?.description].filter(Boolean).join(' ').toLowerCase()
    return matchesRole && (!query || haystack.includes(query))
  })
})
const administratorCount = computed(() => users.value.filter(user => ['system_admin', 'lab_admin'].includes(user.roleName)).length)
const researcherCount = computed(() => users.value.filter(user => user.roleName === 'researcher').length)
const labCount = computed(() => new Set(users.value.map(user => user.lab).filter(Boolean)).size)

const roleLabel = (role?: string) => ({ system_admin: '系统管理员', lab_admin: '实验室管理员', researcher: '研究员', observer: '观察员' }[role || ''] || role || '-')
const roleTagType = (role?: string) => role === 'system_admin' ? 'danger' : role === 'lab_admin' ? 'warning' : role === 'researcher' ? 'primary' : 'info'

const registerDialogVisible = ref(false)
const registerFormRef = ref<FormInstance>()
const registering = ref(false)
const registerForm = ref({
  userName: '',
  password: '',
  roleName: 'researcher',
  lab: '',
  description: ''
})

const registerRules: FormRules = {
  userName: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  roleName: [{ required: true, message: '请选择角色', trigger: 'change' }],
  lab: [{ required: true, message: '请输入实验室', trigger: 'blur' }]
}

const permissionDrawerVisible = ref(false)
const currentUser = ref<any>(null)
const permissionDict = ref<any[]>([])
const selectedPermissionIds = ref<number[]>([])
const loadingPermissions = ref(false)
const savingPermissions = ref(false)

const historyDrawerVisible = ref(false)
const userHistory = ref<any[]>([])
const loadingHistory = ref(false)

const fetchUsers = async () => {
  loadingUsers.value = true
  try {
    const res = await axios.get('/api/user/list')
    if (res.data?.success) {
      users.value = res.data.data || []
    } else {
      ElMessage.error(res.data?.message || '加载用户失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '加载用户失败')
  } finally {
    loadingUsers.value = false
  }
}

const openRegisterDialog = () => {
  registerForm.value = { userName: '', password: '', roleName: 'researcher', lab: '', description: '' }
  registerDialogVisible.value = true
}

const submitRegister = async () => {
  if (!registerFormRef.value) return
  await registerFormRef.value.validate(async valid => {
    if (!valid) return
    registering.value = true
    try {
      const payload = {
        userName: registerForm.value.userName,
        password: registerForm.value.password,
        roleName: registerForm.value.roleName,
        lab: registerForm.value.lab,
        userBasicInfo: { description: registerForm.value.description || registerForm.value.roleName }
      }
      const res = await axios.post('/api/user/register', payload)
      if (res.data?.success) {
        ElMessage.success('用户注册成功')
        registerDialogVisible.value = false
        await fetchUsers()
      } else {
        ElMessage.error(res.data?.message || '注册失败')
      }
    } catch (err: any) {
      ElMessage.error(err?.response?.data?.message || '注册失败')
    } finally {
      registering.value = false
    }
  })
}

const deleteUser = async (id: number) => {
  try {
    const res = await axios.delete(`/api/user/delete/${id}`)
    if (res.data?.success) {
      ElMessage.success('删除成功')
      await fetchUsers()
    } else {
      ElMessage.error(res.data?.message || '删除失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '删除失败')
  }
}

const openPermissionDialog = async (user: any) => {
  currentUser.value = user
  permissionDrawerVisible.value = true
  loadingPermissions.value = true
  try {
    const [dictRes, userRes] = await Promise.all([
      axios.get('/api/user/permissions/list'),
      axios.get(`/api/user/permissions/by-user/${user.id}`)
    ])
    if (dictRes.data?.success) permissionDict.value = dictRes.data.data || []
    if (userRes.data?.success) selectedPermissionIds.value = userRes.data.data || []
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '加载权限失败')
  } finally {
    loadingPermissions.value = false
  }
}

const savePermissions = async () => {
  if (!currentUser.value) return
  savingPermissions.value = true
  try {
    const res = await axios.post('/api/user/permissions/assign', {
      userId: currentUser.value.id,
      permissionIds: selectedPermissionIds.value
    })
    if (res.data?.success) {
      ElMessage.success('特权分配成功')
      permissionDrawerVisible.value = false
    } else {
      ElMessage.error(res.data?.message || '保存失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '保存失败')
  } finally {
    savingPermissions.value = false
  }
}

const viewTaskHistory = async (user: any) => {
  currentUser.value = user
  historyDrawerVisible.value = true
  loadingHistory.value = true
  try {
    const res = await axios.get(`/api/user/task-history/${user.id}`)
    if (res.data?.success) {
      userHistory.value = res.data.data || []
    } else {
      ElMessage.error(res.data?.message || '加载任务记录失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '加载任务记录失败')
  } finally {
    loadingHistory.value = false
  }
}

const formatTime = (time?: string) => (time ? new Date(time).toLocaleString('zh-CN', { hour12: false }) : '-')

onMounted(fetchUsers)
</script>

<style scoped>
.user-page { padding: 16px 20px 20px; display: flex; flex-direction: column; gap: 12px; overflow: hidden; }
.page-header { min-height: 64px; display: flex; align-items: center; justify-content: space-between; gap: 24px; }
.page-header h1 { margin: 0; }
.page-header p { margin: 2px 0 0; color: #6b7280; font-size: 13px; }
.page-eyebrow { margin-bottom: 2px; color: #8a93a6; font-size: 12px; }
.summary-strip { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); border: 1px solid #e5e7eb; border-radius: 6px; background: #fff; }
.summary-strip > div { min-height: 62px; padding: 10px 16px; border-right: 1px solid #e5e7eb; display: flex; flex-direction: column; justify-content: center; }
.summary-strip > div:last-child { border-right: 0; }
.summary-strip span { color: #6b7280; font-size: 12px; }
.summary-strip strong { margin-top: 2px; color: #111827; font-size: 20px; line-height: 24px; }
.table-shell { flex: 1; min-height: 0; display: flex; flex-direction: column; border: 1px solid #e5e7eb; border-radius: 6px; background: #fff; overflow: hidden; }
.table-toolbar { min-height: 52px; padding: 8px 12px; display: flex; align-items: center; justify-content: space-between; gap: 12px; border-bottom: 1px solid #e5e7eb; }
.toolbar-filters { display: flex; gap: 8px; }
.user-search { width: 300px; }
.role-filter { width: 160px; }
.result-count { color: #6b7280; font-size: 12px; }
.user-table { flex: 1; }
.user-identity { display: flex; align-items: center; gap: 10px; min-width: 0; }
.user-avatar { width: 30px; height: 30px; flex: 0 0 30px; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; background: #eaf3ff; color: #1677ff; font-weight: 700; }
.user-identity div { min-width: 0; }
.user-identity strong, .user-identity span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.user-identity span { margin-top: 1px; color: #9ca3af; font-size: 12px; }
.row-actions { white-space: nowrap; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 16px; }
.permission-body { padding: 20px 24px 24px; }
.drawer-context { padding: 12px 14px; border: 1px solid #e5e7eb; border-radius: 6px; background: #fafbfc; }
.drawer-context strong, .drawer-context span { display: block; }
.drawer-context span { margin-top: 3px; color: #6b7280; font-size: 12px; }
.section-heading { margin: 20px 0 12px; display: flex; align-items: flex-end; justify-content: space-between; }
.section-heading h3 { margin: 0; font-size: 15px; }
.section-heading p { margin: 3px 0 0; color: #6b7280; font-size: 12px; }
.perm-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.perm-item { width: 100%; height: auto; min-height: 52px; margin: 0 !important; padding: 8px 12px; }
.perm-item :deep(.el-checkbox__label) { min-width: 0; display: flex; flex-direction: column; line-height: 18px; }
.perm-item span { color: #6b7280; font-size: 12px; }
.history-body { height: 100%; min-height: 0; padding: 16px 20px 20px; }
@media (max-width: 860px) { .summary-strip { grid-template-columns: repeat(2, 1fr); } .summary-strip > div:nth-child(2) { border-right: 0; } .toolbar-filters { flex: 1; } .user-search { width: 100%; } }
</style>
