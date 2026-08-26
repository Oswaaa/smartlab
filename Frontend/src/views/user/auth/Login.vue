<template>
  <div class="auth-container">
    <div class="login-panel">
      <el-form :model="form" @keyup.enter="handleLogin" size="large">
        <el-form-item>
          <el-input v-model="form.userName" placeholder="用户名" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" show-password placeholder="密码" />
        </el-form-item>
        <el-button type="primary" :loading="loading" class="submit-btn" @click="handleLogin">登录</el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../../stores/authStore'

import axios from 'axios'
import { ElMessage } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()
const form = ref({ userName: '', password: '' })
const loading = ref(false)

const handleLogin = async () => {
  if (!form.value.userName || !form.value.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    const res = await axios.post('/api/user/login', form.value)
    if (res.data.success) {
      const { token, user } = res.data.data
      authStore.setAuth({
        username: user.username,
        roleName: user.roleName,
        userBasicInfo: user.userBasicInfo,
        lab: user.lab,
        token: token,
        menus: user.menus,
        permissions: user.permissions,
        authObjects: user.authObjects
      })
      ElMessage.success('登录成功')
      await router.push(authStore.firstVisiblePath())
    } else {
      ElMessage.error(res.data.message || '登录失败')
    }
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '网络异常，请检查后端连接')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: #f4f7fb;
  font-family: Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

.login-panel {
  width: 320px;
  padding: 24px;
  background: #fff;
  border: 1px solid #d8e1ef;
  border-radius: 6px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
}

.submit-btn {
  width: 100%;
  font-weight: 600;
}
</style>