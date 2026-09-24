import { createRouter, createWebHistory } from 'vue-router';

// 路由表：页面按规范 §7 懒加载；path 用 kebab-case，name 与页面组件名保持一致
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'Home',
      component: () => import('@/views/home/HomeView.vue'),
    },
  ],
});

export default router;
