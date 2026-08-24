<template>
  <header class="top-navbar">
    <div class="navbar-brand">
      <div class="brand-icon">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
          <path d="M9 3H5a2 2 0 00-2 2v4m6-6h10a2 2 0 012 2v4M9 3v18m0 0h10a2 2 0 002-2V9M9 21H5a2 2 0 01-2-2V9m0 0h18"
            stroke="#60a5fa" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </div>
    </div>

    <nav class="navbar-menu-wrap">
      <el-menu
        mode="horizontal"
        router
        :default-active="activePath"
        class="navbar-menu"
        :ellipsis="false"
        background-color="transparent"
        active-text-color="#ffffff"
        text-color="rgba(255,255,255,0.72)"
      >
        <template v-for="item in authStore.menus" :key="item.name">
          <el-menu-item v-if="!item.children || item.children.length === 0" :index="item.path" class="nav-item">
            <el-icon class="nav-icon"><component :is="getMenuIcon(item.name)" /></el-icon>
            <span>{{ item.name }}</span>
          </el-menu-item>

          <el-sub-menu v-else :index="item.name" class="nav-item">
            <template #title>
              <el-icon class="nav-icon"><component :is="getMenuIcon(item.name)" /></el-icon>
              <span>{{ item.name }}</span>
            </template>
            <el-menu-item v-for="sub in item.children" :key="sub.name" :index="sub.path">
              <el-icon><component :is="getMenuIcon(sub.name)" /></el-icon>
              <span>{{ sub.name }}</span>
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </nav>

    <div class="navbar-right">
      <div class="info-chip">
        <el-icon><OfficeBuilding /></el-icon>
        <span>{{ authStore.lab || 'SmartLab' }}</span>
      </div>
      <div class="info-chip clock-chip">
        <el-icon><Clock /></el-icon>
        <span class="mono-time">{{ currentTime }}</span>
      </div>
      <div class="nav-divider"></div>
      <el-dropdown trigger="click" @command="handleDropdown">
        <div class="user-trigger">
          <el-avatar :size="28" class="user-avatar">{{ authStore.username ? authStore.username[0].toUpperCase() : 'U' }}</el-avatar>
          <div class="user-info">
            <span class="user-name">{{ authStore.username }}</span>
            <span class="user-role">{{ authStore.userBasicInfo }}</span>
          </div>
          <el-icon class="chevron-icon"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="logout">
              <el-icon><SwitchButton /></el-icon>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/authStore'
import { useConsoleStore } from '../../stores/consoleStore'
import {
  ArrowDown,
  Clock,
  Connection,
  DataAnalysis,
  EditPen,
  HomeFilled,
  List,
  Monitor,
  OfficeBuilding,
  Setting,
  Share,
  SwitchButton,
  Tools,
  User,
  VideoPlay
} from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'

const authStore = useAuthStore()
const consoleStore = useConsoleStore()
const route = useRoute()
const router = useRouter()
const activePath = computed(() => route.path)
const currentTime = ref('')
let timerId = null

const updateTime = () => {
  currentTime.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
}

onMounted(() => {
  updateTime()
  timerId = setInterval(updateTime, 1000)
})

onUnmounted(() => {
  if (timerId) clearInterval(timerId)
})

const getMenuIcon = (name) => {
  const map = {
    首页: HomeFilled,
    设备中心: Monitor,
    设备模型管理: Tools,
    设备实例管理: Connection,
    设备执行代理: Connection,
    资源结构管理: Share,
    数据中心: DataAnalysis,
    任务中心: List,
    任务列表: List,
    任务监控: VideoPlay,
    流程设计: EditPen,
    流程设计器: EditPen,
    约束管理: Setting,
    用户管理: User
  }
  return map[name] || List
}

const handleDropdown = (command) => {
  if (command !== 'logout') return
  ElMessageBox.confirm('确认安全退出 SmartLab 系统吗?', '退出确认', {
    confirmButtonText: '确定退出',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    consoleStore.closeGlobalStream()
    authStore.clearAuth()
    router.push('/login')
  }).catch(() => {})
}
</script>

<style scoped>
.top-navbar {
  display: flex;
  align-items: center;
  height: 50px;
  min-height: 50px;
  background: #0f172a;
  border-bottom: 1px solid #1e293b;
  padding: 0 16px;
  z-index: 1000;
  box-shadow: 0 1px 4px rgba(0,0,0,0.12);
  flex-shrink: 0;
}

.navbar-brand { display: flex; align-items: center; flex-shrink: 0; margin-right: 24px; user-select: none; }
.brand-icon { display: flex; align-items: center; justify-content: center; width: 30px; height: 30px; background: rgba(37,99,235,0.15); border: 1px solid rgba(37,99,235,0.3); border-radius: 6px; }
.navbar-menu-wrap { flex: 1; min-width: 0; display: flex; align-items: center; height: 50px; overflow: hidden; }
.navbar-menu { background: transparent !important; border: none !important; height: 50px !important; display: flex; align-items: stretch; }

:deep(.el-menu--horizontal > .el-menu-item),
:deep(.el-menu--horizontal > .el-sub-menu .el-sub-menu__title) {
  height: 50px !important;
  line-height: 50px !important;
  border-bottom: 2.5px solid transparent !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  padding: 0 14px !important;
  color: rgba(255,255,255,0.72) !important;
  transition: color 0.15s, background 0.15s, border-color 0.15s !important;
}

:deep(.el-menu--horizontal > .el-menu-item:hover),
:deep(.el-menu--horizontal > .el-sub-menu:hover .el-sub-menu__title) { color: #ffffff !important; background: rgba(255,255,255,0.07) !important; }
:deep(.el-menu--horizontal > .el-menu-item.is-active) { color: #ffffff !important; border-bottom-color: #2563eb !important; background: rgba(255,255,255,0.09) !important; }
:deep(.el-menu--horizontal > .el-sub-menu.is-active .el-sub-menu__title) { color: #ffffff !important; border-bottom-color: #2563eb !important; }

.nav-icon { font-size: 14px !important; margin-right: 4px !important; }
.navbar-right { display: flex; align-items: center; gap: 6px; flex-shrink: 0; margin-left: 16px; }
.info-chip { display: flex; align-items: center; gap: 5px; color: rgba(255,255,255,0.60); font-size: 12px; padding: 3px 9px; background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.09); border-radius: 5px; white-space: nowrap; }
.info-chip .el-icon { font-size: 12px; }
.clock-chip { min-width: 92px; justify-content: center; }
.mono-time { font-family: var(--sl-font-mono, monospace); font-size: 12px; letter-spacing: 0.04em; }
.nav-divider { width: 1px; height: 24px; background: rgba(255,255,255,0.14); margin: 0 6px; }
.user-trigger { display: flex; align-items: center; gap: 7px; padding: 4px 8px; border-radius: 6px; cursor: pointer; transition: background 0.15s; border: 1px solid rgba(255,255,255,0.09); background: rgba(255,255,255,0.04); }
.user-trigger:hover { background: rgba(255,255,255,0.10); border-color: rgba(255,255,255,0.16); }
.user-avatar { background: #2563eb !important; color: #fff !important; font-weight: 700 !important; font-size: 12px !important; flex-shrink: 0; }
.user-info { display: flex; flex-direction: column; line-height: 1.2; }
.user-name { font-size: 13px; font-weight: 600; color: #ffffff; white-space: nowrap; }
.user-role { font-size: 11px; color: rgba(255,255,255,0.48); white-space: nowrap; }
.chevron-icon { color: rgba(255,255,255,0.40); font-size: 11px; }
</style>

