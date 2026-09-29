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
      redirect: '/home',
      children: [
        {
          // 系统首页：登录后的默认落地页，也是全局返回的终点
          path: 'home',
          name: 'Home',
          component: () => import('@/views/home/HomeView.vue'),
        },
        {
          path: 'knowledge-base',
          name: 'KnowledgeBase',
          component: () => import('@/views/knowledge-base/KnowledgeBaseView.vue'),
        },
        {
          path: 'knowledge-base/:id/stages',
          name: 'PipelineStage',
          component: () => import('@/views/knowledge-base/PipelineStageView.vue'),
        },
        {
          // 索引与发布：同一知识库的策略组合版本、发布与回退
          path: 'knowledge-base/:id/index',
          name: 'IndexBuild',
          component: () => import('@/views/knowledge-base/IndexBuildView.vue'),
        },
        {
          // 检索评测：测试台检索 + 运行记录并排对比 + 规则选优发布
          path: 'knowledge-base/:id/retrieval',
          name: 'RetrievalEval',
          component: () => import('@/views/knowledge-base/RetrievalEvalView.vue'),
        },
        {
          // 策略管理：四类策略（预处理/切片/向量化/检索）的版本管理
          path: 'strategy',
          name: 'StrategyManagement',
          component: () => import('@/views/strategy/StrategyManagementView.vue'),
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

// 登录态与角色守卫：无令牌回登录页并记住来源；已登录访问登录页回首页；
// 非管理员访问管理类页面回首页
const HOME_PATH = '/home';

router.beforeEach((to) => {
  const authStore = useAuthStore();
  if (to.meta.public) {
    return authStore.isLoggedIn ? { path: HOME_PATH } : true;
  }
  if (!authStore.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  if (to.meta.adminOnly && !authStore.isAdmin) {
    return { path: HOME_PATH };
  }
  return true;
});

export default router;
