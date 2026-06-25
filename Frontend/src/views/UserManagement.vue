<template>
  <div class="user-page">
    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="card-header">
          <span>用户管理</span>
          <el-button type="primary" size="small" @click="openRegisterDialog">新增用户</el-button>
        </div>
      </template>

      <el-table :data="users" border stripe size="small" v-loading="loadingUsers">
        <el-table-column prop="id" label="用户编号" width="90" />
        <el-table-column prop="userName" label="用户名" min-width="140" />
        <el-table-column label="角色" min-width="120">
          <template #default="{ row }">{{ row.roleName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="lab" label="实验室" min-width="120" />
        <el-table-column label="说明" min-width="160">
          <template #default="{ row }">{{ row.userBasicInfo?.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openPermissionDialog(row)">分配特权</el-button>
            <el-button link type="primary" @click="viewTaskHistory(row)">任务记录</el-button>
            <el-popconfirm title="确认删除该用户？" @confirm="deleteUser(row.id)">
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="registerDialogVisible" title="注册用户" width="460px">
      <el-form :model="registerForm" :rules="registerRules" ref="registerFormRef" label-width="100px" size="small">
        <el-form-item label="用户名" prop="userName"><el-input v-model="registerForm.userName" /></el-form-item>
        <el-form-item label="密码" prop="password"><el-input v-model="registerForm.password" type="password" show-password /></el-form-item>
        <el-form-item label="角色" prop="roleName">
          <el-select v-model="registerForm.roleName" style="width: 100%">
            <el-option label="系统管理员" value="system_admin" />
            <el-option label="实验室管理员" value="lab_admin" />
            <el-option label="研究员" value="researcher" />
            <el-option label="观察员" value="observer" />
          </el-select>
        </el-form-item>
        <el-form-item label="实验室" prop="lab"><el-input v-model="registerForm.lab" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="registerForm.description" placeholder="例如：有机实验组" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="registerDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="registering" @click="submitRegister">确认</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="permissionDrawerVisible" :title="`分配特权：${currentUser?.userName || ''}`" size="420px">
      <div v-loading="loadingPermissions">
        <el-checkbox-group v-model="selectedPermissionIds" class="perm-list">
          <el-checkbox
            v-for="perm in permissionDict"
            :key="perm.id"
            :label="perm.id"
            border
            class="perm-item"
          >
            {{ perm.object }} / {{ perm.action }}
          </el-checkbox>
        </el-checkbox-group>
        <div class="drawer-actions">
          <el-button type="primary" :loading="savingPermissions" @click="savePermissions">保存</el-button>
        </div>
      </div>
    </el-drawer>

    <el-drawer v-model="historyDrawerVisible" :title="`用户任务记录：${currentUser?.userName || ''}`" size="60%">
      <el-table :data="userHistory" border stripe size="small" v-loading="loadingHistory">
        <el-table-column prop="taskId" label="任务编号" width="100" />
        <el-table-column prop="taskName" label="任务名称" min-width="180" />
        <el-table-column prop="templateId" label="流程" min-width="120" />
        <el-table-column prop="currentStatus" label="状态" width="100" />
        <el-table-column label="开始时间" min-width="150">
          <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" min-width="150">
          <template #default="{ row }">{{ formatTime(row.endTime) }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import axios from 'axios'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

const users = ref<any[]>([])
const loadingUsers = ref(false)

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
.user-page { padding: 16px; background: #f2f4f7; min-height: calc(100vh - 52px); }
.page-card { border: 1px solid #d8dce3; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.perm-list { display: flex; flex-direction: column; gap: 8px; }
.perm-item { margin-right: 0 !important; }
.drawer-actions { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
