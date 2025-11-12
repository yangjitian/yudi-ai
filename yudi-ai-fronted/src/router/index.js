import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    name: 'Chat',
    component: () => import('@/views/Chat.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/:conversationId',
    name: 'ChatWithId',
    component: () => import('@/views/Chat.vue'),
    meta: { requiresAuth: true }
  },
  // 为了在URL中直观看到后端端点与会话ID，增加以下等价路由
  {
    path: '/cook/chat/stream/:conversationId',
    name: 'ChatStreamWithId',
    component: () => import('@/views/Chat.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/cook/chat/:conversationId',
    name: 'ChatNonStreamWithId',
    component: () => import('@/views/Chat.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/cook/yd_streamChat/:conversationId',
    name: 'YdChatStreamWithId',
    component: () => import('@/views/Chat.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/cook/yd_chat/:conversationId',
    name: 'YdChatNonStreamWithId',
    component: () => import('@/views/Chat.vue'),
    meta: { requiresAuth: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  
  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    next('/login')
  } else if (to.path === '/login' && userStore.isLoggedIn) {
    next('/')
  } else {
    next()
  }
})

export default router

