<template>
  <div class="layout-container">
    <AppTopNav />
    <main class="content-area">
      <router-view v-slot="{ Component }">
        <transition name="fade-slide" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'
import AppTopNav from '../layout/AppTopNav.vue'
import { useConsoleStore } from '../../stores/consoleStore'

const consoleStore = useConsoleStore()

onMounted(() => {
  consoleStore.initGlobalStream()
})

onUnmounted(() => {
  consoleStore.closeGlobalStream()
})
</script>

<style scoped>
.layout-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100vw;
  background-color: var(--bg-primary);
  overflow: hidden;
}

.content-area {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: var(--bg-primary);
}

.fade-slide-enter-active,
.fade-slide-leave-active { transition: all 0.20s cubic-bezier(0.4, 0, 0.2, 1); }
.fade-slide-enter-from { opacity: 0; transform: translateY(6px); }
.fade-slide-leave-to { opacity: 0; transform: translateY(-6px); }
</style>
