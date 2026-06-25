import { useAuthStore } from '../stores/authStore'
import { ElMessage } from 'element-plus'

// 封装一个带有 Token 的 fetch 方法
export const request = async (url, options = {}) => {
  const authStore = useAuthStore()
  
  // 1. 设置默认 Headers，并携带 Token
  const headers = {
    'Content-Type': 'application/json',
    ...options.headers,
  }
  
  // 核心：如果本地有 Token，就塞进 Authorization 请求头里
  if (authStore.token) {
    headers['Authorization'] = `Bearer ${authStore.token}`
  }

  // 2. 发起请求
  try {
    const response = await fetch(url, { ...options, headers })
    
    // 3. 处理 401 未授权情况（Token过期或无效）
    if (response.status === 401) {
      authStore.clearAuth()
      ElMessage.error('登录已过期，请重新登录')
      window.location.href = '/login' // 强制跳回登录页
      throw new Error('Unauthorized')
    }

    const data = await response.json()
    return data;
  } catch (error) {
    throw error
  }
}