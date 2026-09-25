import { createRouter, createWebHistory } from 'vue-router';

import { useAuthStore } from '@/stores/auth';

// 路由表：页面按规范 §7 懒加载；path 用 kebab-case，name 与组件名保持一致
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/LoginView.vue'),
      // 免登录页：已登录用户访问时回落到工作台
      meta: { public: true },
    },
    {
      path: '/',
      name: 'AppLayout',
      component: () => import('@/layouts/AppLayout.vue'),
      redirect: '/knowledge-base',
      children: [
        {
          path: 'knowledge-base',
          name: 'KnowledgeBase',
          component: () => import('@/views/knowledge-base/KnowledgeBaseView.vue'),
        },
        {
          path: 'user',
          name: 'UserManagement',
          component: () => import('@/views/user/UserManagementView.vue'),
          // 仅管理员可进入；非管理员回落工作台
          meta: { adminOnly: true },
        },
      ],
    },
  ],
});

// 登录态与角色守卫：无令牌回登录页并记住来源；已登录访问登录页回工作台；
// 非管理员访问管理类页面回工作台
router.beforeEach((to) => {
  const authStore = useAuthStore();
  if (to.meta.public) {
    return authStore.isLoggedIn ? { path: '/knowledge-base' } : true;
  }
  if (!authStore.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  if (to.meta.adminOnly && !authStore.isAdmin) {
    return { path: '/knowledge-base' };
  }
  return true;
});

export default router;
