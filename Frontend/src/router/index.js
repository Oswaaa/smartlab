import { createRouter, createWebHashHistory } from 'vue-router'
import MainLayout from '../components/layouts/MainLayout.vue'
import { useAuthStore } from '../stores/authStore'

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/auth/Login.vue'), meta: { requiresAuth: false } },
  { path: '/register', name: 'Register', component: () => import('../views/auth/Register.vue'), meta: { requiresAuth: false } },
  {
    path: '/',
    name: 'MainLayout',
    component: MainLayout,
    redirect: '/home',
    meta: { requiresAuth: true },
    children: [
      { path: 'home', name: 'Home', component: () => import('../views/dashboard/Dashboard.vue'), meta: { title: '首页' } },
      { path: 'device-model-management', name: 'DeviceModelManagement', component: () => import('../views/device/DeviceModelManagement.vue'), meta: { title: '设备模型管理' } },
      { path: 'device-instance-management', name: 'DeviceInstanceManagement', component: () => import('../views/device/DeviceInstanceManagement.vue'), meta: { title: '设备实例管理' } },
      { path: 'adapter-management', name: 'AdapterManagement', component: () => import('../views/device/AdapterManagement.vue'), meta: { title: '设备执行代理' } },
      { path: 'data-management', name: 'DataManagement', component: () => import('../views/data/DataManagement.vue'), meta: { title: '数据中心' } },
      { path: 'task-management', name: 'TaskManagement', component: () => import('../views/task/TaskList/TaskList.vue'), meta: { title: '任务列表' } },
      { path: 'task-designer', name: 'TaskDesigner', component: () => import('../views/task/WorkflowDesigner/WorkflowDesigner.vue'), meta: { title: '流程设计' } },
      { path: 'constraint-management', name: 'ConstraintManagement', component: () => import('../views/security/SecurityCenter.vue'), meta: { title: '约束管理' } },
      { path: 'user-management', name: 'UserManagement', component: () => import('../views/admin/UserManagement.vue'), meta: { title: '用户管理' } }
    ]
  },
  { path: '/:pathMatch(.*)*', name: 'NotFound', redirect: '/home' }
]

const router = createRouter({ history: createWebHashHistory(), routes })

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth)

  if (requiresAuth && !authStore.isAuthenticated) return '/login'
  if ((to.path === '/login' || to.path === '/register') && authStore.isAuthenticated) return authStore.firstVisiblePath()

  if (requiresAuth && authStore.isAuthenticated) {
    if (!authStore.menuLoaded) await authStore.refreshProfile()
    if (!authStore.isAuthenticated) return '/login'

    const publicAuthedPaths = new Set(['/home'])
    const visiblePaths = authStore.visiblePaths()
    if (!publicAuthedPaths.has(to.path) && !visiblePaths.has(to.path)) return authStore.firstVisiblePath()
  }

  return true
})

export default router
