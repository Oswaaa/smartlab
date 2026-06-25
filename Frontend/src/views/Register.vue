<template>
  <div class="auth-container">
    <div class="auth-card">
      <div class="auth-header">
        <h1 class="logo-text">SMARTLAB</h1>
        <p class="subtitle">注册新账户</p>
      </div>
      <el-form :model="form" @keyup.enter="handleRegister" size="large">
        <el-form-item>
          <el-input v-model="form.userName" placeholder="用户名" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" show-password placeholder="密码" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.lab" placeholder="实验室名称" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="form.roleName" placeholder="分配角色" class="w-full">
            <el-option label="系统管理员" value="system_admin" />
            <el-option label="实验室管理员" value="lab_admin" />
            <el-option label="研究员" value="researcher" />
            <el-option label="观察员" value="observer" />
          </el-select>
        </el-form-item>
        <el-button type="primary" :loading="loading" class="w-full submit-btn" @click="handleRegister">注册</el-button>
        <div class="link-wrap">
          <router-link to="/login">返回登录</router-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const router = useRouter()
const form = ref({ userName: '', password: '', lab: '', roleName: 'researcher' })
const loading = ref(false)

const handleRegister = async () => {
  if (!form.value.userName || !form.value.password || !form.value.lab) {
    ElMessage.warning('请填写完整信息')
    return
  }
  loading.value = true
  try {
    const res = await axios.post('/api/user/register', form.value)
    if (res.data.success) {
      ElMessage.success('注册成功')
      router.push('/login')
    } else {
      ElMessage.error(res.data.message || '注册失败')
    }
  } catch (err) {
    ElMessage.error('网络异常，请检查后端连接')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
  font-family: 'Inter', -apple-system, sans-serif;
}
.auth-card {
  width: 380px;
  background: rgba(255, 255, 255, 0.95);
  padding: 40px 32px;
  border-radius: 12px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.3), 0 10px 10px -5px rgba(0, 0, 0, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
}
.auth-header {
  text-align: center;
  margin-bottom: 32px;
}
.logo-text {
  font-size: 32px;
  font-weight: 800;
  letter-spacing: 2px;
  background: linear-gradient(to right, #2563eb, #3b82f6);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  margin: 0 0 8px 0;
}
.subtitle {
  font-size: 14px;
  color: #64748b;
  margin: 0;
}
.w-full {
  width: 100%;
}
.submit-btn {
  background-color: #2563eb;
  border-color: #2563eb;
  font-weight: 600;
  transition: all 0.2s ease;
}
.submit-btn:hover {
  background-color: #1d4ed8;
  border-color: #1d4ed8;
}
.link-wrap {
  text-align: center;
  margin-top: 20px;
  font-size: 13px;
}
.link-wrap a {
  color: #2563eb;
  text-decoration: none;
  font-weight: 500;
}
.link-wrap a:hover {
  text-decoration: underline;
}
</style>