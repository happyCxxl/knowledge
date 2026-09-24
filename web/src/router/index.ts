import { createRouter, createWebHistory } from 'vue-router';

// 路由表：页面按规范 §7 懒加载；path 用 kebab-case，name 与组件名保持一致
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/LoginView.vue'),
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
      ],
    },
  ],
});

export default router;
