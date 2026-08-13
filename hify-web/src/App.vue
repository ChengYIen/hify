<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Setting, User, ChatDotRound, Collection, Fold, Expand, HomeFilled } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()

const isCollapsed = ref(false)
const version = 'v0.0.1'

/** 响应式断点：< 1200px 自动折叠侧边栏 */
const COLLAPSE_BREAKPOINT = 1200

function handleResize(): void {
  isCollapsed.value = window.innerWidth < COLLAPSE_BREAKPOINT
}

onMounted(() => {
  handleResize()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})

/* ---------- 菜单 ---------- */
const menuItems = [
  { path: '/provider',        title: '模型管理',   icon: Setting },
  { path: '/agent',           title: 'Agent 管理', icon: User },
  { path: '/knowledge-bases', title: '知识库',      icon: Collection },
  { path: '/conversation',    title: '对话',        icon: ChatDotRound },
]

function onMenuSelect(path: string) {
  router.push(path)
}

/* ---------- 面包屑 ---------- */
const breadcrumbs = computed(() => {
  const crumbs: { title: string; path?: string }[] = [
    { title: '首页', path: '/' },
  ]
  const item = menuItems.find(m => route.path === m.path || route.path.startsWith(`${m.path}/`))
  if (item) crumbs.push({ title: item.title })
  return crumbs
})

/* ---------- 折叠 ---------- */
function toggleCollapse() {
  isCollapsed.value = !isCollapsed.value
}
</script>

<template>
  <el-container class="layout">
    <!-- ================================================================
          侧边栏
          ================================================================ -->
    <aside class="sidebar" :class="{ collapsed: isCollapsed }">
      <!-- Logo -->
      <div class="logo-area">
        <div class="logo-icon"><span class="logo-h">H</span></div>
        <div v-show="!isCollapsed" class="logo-text">
          <span class="logo-brand">Hify</span>
          <span class="logo-sub">AI Agent Platform</span>
        </div>
      </div>

      <!-- 菜单 -->
      <nav class="nav-menu">
        <div
          v-for="item in menuItems"
          :key="item.path"
          class="menu-item"
          :class="{ active: route.path === item.path || route.path.startsWith(`${item.path}/`) }"
          @click="onMenuSelect(item.path)"
        >
          <span class="menu-indicator" />
          <el-icon class="menu-icon" :size="20">
            <component :is="item.icon" />
          </el-icon>
          <span v-show="!isCollapsed" class="menu-label">{{ item.title }}</span>
        </div>
      </nav>

      <!-- 底栏 -->
      <div class="sidebar-footer">
        <button class="collapse-btn" @click="toggleCollapse">
          <el-icon :size="16"><Fold v-if="!isCollapsed" /><Expand v-else /></el-icon>
          <span v-show="!isCollapsed" class="collapse-label">收起菜单</span>
        </button>
        <div v-show="!isCollapsed" class="version">Hify {{ version }}</div>
      </div>
    </aside>

    <!-- ================================================================
          右侧：顶栏 + 内容区
          ================================================================ -->
    <div class="right-panel">
      <!-- 顶栏 -->
      <header class="topbar">
        <div class="topbar-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="crumb in breadcrumbs" :key="crumb.title" :to="crumb.path">
              <el-icon v-if="crumb.path === '/'" :size="14"><HomeFilled /></el-icon>
              <span v-else>{{ crumb.title }}</span>
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="topbar-right">
          <div class="user-area">
            <el-avatar :size="32" class="user-avatar">
              <el-icon :size="18"><User /></el-icon>
            </el-avatar>
            <span class="user-name">开发者</span>
          </div>
        </div>
      </header>

      <!-- 内容区 -->
      <main class="content">
        <!-- 对话页需要确定高度（内部消息区滚动 + 输入区固定），其余页面保持自动高度 -->
        <div class="content-card" :class="{ 'content-card--fill': route.name === 'Conversation' }">
          <router-view />
        </div>
      </main>
    </div>
  </el-container>
</template>

<style scoped>
/* =================================================================
 * 整体布局
 * ================================================================= */
.layout {
  height: 100vh;
  overflow: hidden;
}

/* =================================================================
 * 侧边栏（保持上次设计不变）
 * ================================================================= */
.sidebar {
  --sidebar-width:     220px;
  --sidebar-collapsed:  64px;

  width:            var(--sidebar-width);
  min-width:        var(--sidebar-width);
  height:           100vh;
  background-color: var(--color-bg-dark);
  display:          flex;
  flex-direction:   column;
  overflow:         hidden;
  transition:       width 280ms cubic-bezier(0.4, 0, 0.2, 1),
                    min-width 280ms cubic-bezier(0.4, 0, 0.2, 1);
  z-index:          var(--hify-z-sidebar);
  border-right:     1px solid rgba(255, 255, 255, 0.06);
}

.sidebar.collapsed {
  width:     var(--sidebar-collapsed);
  min-width: var(--sidebar-collapsed);
}

/* Logo */
.logo-area {
  display: flex; align-items: center; gap: 12px;
  padding: 20px 18px; min-height: 68px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.logo-icon {
  width: 36px; height: 36px; min-width: 36px; border-radius: 10px;
  background: linear-gradient(135deg, var(--hify-primary-500), var(--hify-primary-400));
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 4px 12px rgba(99, 89, 255, 0.35);
}
.logo-h { font-size: 18px; font-weight: 800; color: #fff; line-height: 1; }
.logo-text { display: flex; flex-direction: column; gap: 2px; overflow: hidden; white-space: nowrap; }
.logo-brand {
  font-size: 18px; font-weight: 800; letter-spacing: -0.3px;
  background: linear-gradient(135deg, var(--hify-primary-400) 0%, var(--hify-primary-200) 100%);
  -webkit-background-clip: text; background-clip: text;
  -webkit-text-fill-color: transparent; line-height: 1.25;
}
.logo-sub {
  font-size: 10px; font-weight: 500; color: var(--hify-gray-500);
  letter-spacing: 0.8px; text-transform: uppercase;
}

/* 菜单 */
.nav-menu { flex: 1; padding: 12px 0; display: flex; flex-direction: column; gap: 2px; overflow-y: auto; overflow-x: hidden; }
.menu-item {
  position: relative; display: flex; align-items: center; gap: 12px;
  height: 44px; padding: 0 18px; margin: 0 8px; border-radius: 8px;
  cursor: pointer; color: rgba(255, 255, 255, 0.65);
  transition: color 180ms ease, background-color 180ms ease;
  user-select: none; white-space: nowrap;
}
.sidebar.collapsed .menu-item { justify-content: center; padding: 0; gap: 0; }

.menu-indicator {
  position: absolute; left: -8px; top: 50%; transform: translateY(-50%);
  width: 3px; height: 20px; border-radius: 0 2px 2px 0;
  background: var(--hify-primary-400); opacity: 0;
  transition: opacity 200ms ease, height 200ms ease;
}
.menu-item.active .menu-indicator { opacity: 1; height: 28px; }
.menu-icon { min-width: 20px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; transition: color 180ms ease; }
.menu-label { font-size: 14px; font-weight: 500; }

.menu-item:hover { color: rgba(255, 255, 255, 0.9); background-color: rgba(255, 255, 255, 0.06); }
.menu-item.active { color: #fff; background-color: rgba(99, 89, 255, 0.12); }
.menu-item.active .menu-icon { color: var(--hify-primary-400); }

/* 底栏 */
.sidebar-footer { padding: 12px; border-top: 1px solid rgba(255, 255, 255, 0.06); display: flex; flex-direction: column; gap: 8px; }
.collapse-btn {
  display: flex; align-items: center; gap: 10px; padding: 8px 12px;
  border: none; border-radius: 8px; background: transparent;
  color: rgba(255, 255, 255, 0.45); cursor: pointer; font-size: 13px;
  font-family: inherit; width: 100%;
  transition: color 180ms ease, background-color 180ms ease;
}
.sidebar.collapsed .collapse-btn { justify-content: center; padding: 8px; }
.collapse-btn:hover { color: rgba(255, 255, 255, 0.75); background-color: rgba(255, 255, 255, 0.06); }
.collapse-label { font-size: 13px; font-weight: 500; }
.version { padding: 0 12px; font-size: 11px; font-weight: 500; color: rgba(255, 255, 255, 0.2); letter-spacing: 0.3px; }

/* =================================================================
 * 右侧面板
 * ================================================================= */
.right-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

/* =================================================================
 * 顶栏
 * ================================================================= */
.topbar {
  height:          56px;
  min-height:      56px;
  display:         flex;
  align-items:     center;
  justify-content: space-between;
  padding:         0 24px;
  background:      var(--hify-bg-container);
  border-bottom:   1px solid var(--hify-border-default);
  z-index:         50;
}

.topbar-left {
  display: flex;
  align-items: center;
}

/* 面包屑微调 */
.topbar :deep(.el-breadcrumb) {
  font-size: 14px;
  line-height: 1;
}

.topbar :deep(.el-breadcrumb__item) {
  display: flex;
  align-items: center;
}

.topbar :deep(.el-breadcrumb__inner) {
  color: var(--hify-text-tertiary);
  font-weight: 400;
  transition: color 150ms ease;
}

.topbar :deep(.el-breadcrumb__inner:hover) {
  color: var(--hify-text-brand);
}

.topbar :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--hify-text-primary);
  font-weight: 600;
  cursor: default;
}

.topbar :deep(.el-breadcrumb__separator) {
  color: var(--hify-gray-300);
  margin: 0 8px;
}

/* =================================================================
 * 用户区域
 * ================================================================= */
.topbar-right {
  display: flex;
  align-items: center;
}

.user-area {
  display:     flex;
  align-items: center;
  gap:         10px;
  padding:     4px 12px 4px 4px;
  border-radius: 8px;
  cursor:      pointer;
  transition:  background-color 180ms ease;
}

.user-area:hover {
  background-color: var(--hify-bg-hover);
}

.user-avatar {
  background: linear-gradient(135deg, var(--hify-primary-400), var(--hify-primary-500));
  color:      #fff;
  flex-shrink: 0;
}

.user-name {
  font-size:   14px;
  font-weight: 500;
  color:       var(--hify-text-secondary);
}

/* =================================================================
 * 内容区
 * ================================================================= */
.content {
  flex:             1;
  background-color: var(--color-bg-secondary);
  padding:          var(--hify-page-padding);
  overflow:         auto;
}

.content-card {
  background:      var(--hify-bg-container);
  border-radius:   var(--hify-radius-card);
  box-shadow:      var(--hify-shadow-card);
  border:          1px solid var(--hify-border-light);
  min-height:      calc(100% - 0px);
  padding:         var(--hify-card-padding);
}

/* 对话页：内容卡占满确定高度，页面不滚动，聊天内部滚动 */
.content-card--fill {
  height:       100%;
  min-height:   0;
  overflow:     hidden;
  padding:      0;
}
</style>
