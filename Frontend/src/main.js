// 从Vue核心库中导入创建应用实例的方法
import { createApp } from 'vue'
// 从Pinia状态管理库中导入创建Pinia实例的方法
import { createPinia } from 'pinia'
// 导入全局设计系统样式（Design Tokens 与组件样式）
import './assets/styles/index.css'

// 导入根组件App（整个应用的“总容器”）
import App from './App.vue'
// 导入路由模块（实现多页面会用到）
import router from './router';
// 导入Element Plus UI库和它的样式
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// // 导入Element Plus的图标组件（如果需要使用图标的话）
// import * as ElementPlusIconsVue from '@element-plus/icons-vue'

// 创建Pinia实例 → 让Vue应用使用Pinia状态管理库
const pinia = createPinia()

// 创建Vue应用实例 → 启用路由 → 挂载到#app上
const app = createApp(App);
// 注意：必须先挂载 pinia，再挂载 router，因为 router 的 beforeEach 中用到了 authStore
app.use(pinia);// 让应用拥有Pinia状态管理库的功能
app.use(router);// 让应用拥有路由能力
app.use(ElementPlus);// 让应用拥有Element Plus UI组件库的功能


// // 注册Element Plus图标组件（如果需要使用图标的话）
// for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
//   app.component(key, component)
// }

// 配置 Axios 全局拦截器以自动附加 JWT Token 并处理 401 认证失效
import axios from 'axios'
import { useAuthStore } from './stores/authStore'

axios.interceptors.request.use(config => {
  try {
    const authStore = useAuthStore()
    if (authStore.token) {
      config.headers['Authorization'] = 'Bearer ' + authStore.token
    }
  } catch (e) {
    // 忽略未完全初始化时的 Store 读取错误
  }
  return config
}, error => {
  return Promise.reject(error)
})

axios.interceptors.response.use(response => {
  return response
}, error => {
  if (error.response && error.response.status === 401) {
    try {
      const authStore = useAuthStore()
      authStore.clearAuth()
      router.push('/login')
    } catch (e) {
      localStorage.removeItem('smartlab_auth')
      window.location.hash = '#/login'
    }
  }
  return Promise.reject(error)
})

app.mount('#app'); // 把App组件渲染到index.html的#app元素里


