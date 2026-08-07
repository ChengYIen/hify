import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    redirect: '/provider',
  },
  {
    path: '/provider',
    name: 'Provider',
    component: () => import('@/views/provider/ProviderList.vue'),
  },
  {
    path: '/agent',
    name: 'Agent',
    component: () => import('@/views/agent/AgentList.vue'),
  },
  {
    path: '/conversation',
    name: 'Conversation',
    component: () => import('@/views/conversation/ConversationView.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// =========================================================================
// 路由守卫 —— 未登录强制跳转 /login
// =========================================================================

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')

  // 公开路由直接放行
  if (to.meta.public) {
    // 已登录则跳走（不要重复登录）
    if (token && to.path === '/login') {
      next('/provider')
      return
    }
    next()
    return
  }

  // 需要认证但无 token → 跳转登录
  if (!token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  next()
})

export default router
