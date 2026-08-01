import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
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

export default router
