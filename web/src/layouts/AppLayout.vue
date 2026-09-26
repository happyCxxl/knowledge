<template>
  <div class="layout">
    <div class="layout-glow"></div>
    <aside class="layout-side">
      <!-- 品牌名可点回首页 -->
      <router-link class="layout-brand" to="/home">
        <div class="layout-logo">
          <svg
            class="layout-logo-mark"
            width="21"
            height="21"
            viewBox="0 0 26 26"
            fill="none"
            aria-hidden="true"
          >
            <path
              d="M13 2.5 22 7.5v11L13 23.5 4 18.5v-11L13 2.5Z"
              stroke="#041510"
              stroke-width="1.8"
              stroke-linejoin="round"
            />
            <path
              d="M9.2 14.6v-3.2l3.8-6.2 3.8 6.2v3.2l-3.8 5.8-3.8-5.8Z"
              stroke="#041510"
              stroke-width="1.6"
              stroke-linejoin="round"
              opacity="0.85"
            />
          </svg>
        </div>
        <div class="layout-brand-text">
          <div class="layout-brand-name">knowledge</div>
          <div class="layout-brand-sub">企业知识库平台</div>
        </div>
      </router-link>
      <template v-for="group in visibleNavGroups" :key="group.title">
        <div class="layout-nav-group">{{ group.title }}</div>
        <router-link
          v-for="item in group.items"
          :key="item.path"
          class="layout-nav-item"
          :class="{ 'layout-nav-item-active': isNavActive(item.path) }"
          :to="item.path"
        >
          <svg
            class="layout-nav-icon"
            width="16"
            height="16"
            viewBox="0 0 16 16"
            fill="none"
            stroke="currentColor"
            stroke-width="1.4"
          >
            <path
              v-for="(d, index) in item.iconPaths"
              :key="index"
              :d="d"
              stroke-linejoin="round"
            />
            <circle v-if="item.iconCircle" cx="8" cy="5.4" r="2.6" />
          </svg>
          {{ item.label }}
        </router-link>
      </template>
      <div class="layout-side-foot">
        <el-dropdown class="layout-user-drop" trigger="click" placement="top-start">
          <div class="layout-user-trigger">
            <div class="layout-avatar">{{ avatarText }}</div>
            <div class="layout-user">
              <div class="layout-user-name">{{ username }}</div>
              <div class="layout-user-role">{{ roleLabel }}</div>
            </div>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </aside>
    <div class="layout-main">
      <header class="layout-topbar">
        <!-- 左侧只有返回按钮：页面名称不再出现在顶栏。
             首页是层级起点，整个左侧为空 -->
        <div class="layout-topbar-left">
          <button
            v-if="backTarget"
            class="layout-back"
            type="button"
            :title="`返回${backTarget.label}`"
            @click="goBack"
          >
            <svg
              class="layout-back-icon"
              width="16"
              height="16"
              viewBox="0 0 16 16"
              fill="none"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linecap="round"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <path d="M10 3.2 5.2 8l4.8 4.8" />
            </svg>
          </button>
        </div>
        <div class="layout-top-right">
          <div class="layout-search">
            <svg
              class="layout-search-icon"
              width="14"
              height="14"
              viewBox="0 0 16 16"
              fill="none"
              stroke="currentColor"
              stroke-width="1.5"
            >
              <circle cx="7" cy="7" r="4.4" />
              <path d="m10.4 10.4 3.4 3.4" />
            </svg>
            <input class="layout-search-input" placeholder="搜索知识库、文档、任务…" />
          </div>
          <el-dropdown trigger="click" placement="bottom-end">
            <div class="layout-avatar layout-avatar-clickable">{{ avatarText }}</div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <div class="layout-content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAuthStore } from '@/stores/auth';

// 侧边导航：数据驱动，按角色的可见性在渲染前过滤
const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

interface NavItem {
  label: string;
  path: string;
  iconPaths: string[];
  iconCircle?: boolean;
  /** 仅管理员可见的菜单 */
  adminOnly?: boolean;
}

const navGroups: { title: string; items: NavItem[] }[] = [
  {
    title: '概览',
    items: [
      {
        label: '首页',
        path: '/home',
        iconPaths: ['M2.4 6.6 8 2.6l5.6 4v6.8H2.4z', 'M6.2 13.4V9.2h3.6v4.2'],
      },
    ],
  },
  {
    title: '资产',
    items: [
      {
        label: '知识库',
        path: '/knowledge-base',
        iconPaths: [
          'M2.2 5.2 8 2l5.8 3.2v5.6L8 14 2.2 10.8V5.2Z',
          'M2.2 5.2 8 8.4l5.8-3.2M8 8.4V14',
        ],
      },
    ],
  },
  {
    title: '系统',
    items: [
      {
        label: '用户管理',
        path: '/user',
        iconPaths: ['M2.8 13.6c0-2.4 2.3-3.8 5.2-3.8s5.2 1.4 5.2 3.8'],
        iconCircle: true,
        adminOnly: true,
      },
    ],
  },
];

// 按角色过滤菜单：管理员看全部，普通用户看不到管理类菜单
const visibleNavGroups = computed(() =>
  navGroups
    .map((group) => ({
      title: group.title,
      items: group.items.filter((item) => !item.adminOnly || authStore.isAdmin),
    }))
    .filter((group) => group.items.length > 0),
);

function isNavActive(path: string): boolean {
  // 精确匹配，否则 '/' 前缀的首页项会在所有页面都高亮
  if (route.path === path) {
    return true;
  }
  // 子页面归到所属菜单：/knowledge-base/xxx/stages 也要点亮「知识库」
  return path !== '/home' && route.path.startsWith(`${path}/`);
}

/**
 * 返回目标的集中定义：key 为当前路由名，value 为上一层。
 *
 * <p>层级：处理链页 → 知识库列表 → 首页；用户管理 → 首页；首页无上层。
 * 用表而不是逐页写按钮，保证全局只有一处逻辑。
 */
const BACK_TARGETS: Record<string, { path: string; label: string }> = {
  KnowledgeBase: { path: '/home', label: '首页' },
  PipelineStage: { path: '/knowledge-base', label: '知识库' },
  UserManagement: { path: '/home', label: '首页' },
};

const routeName = computed(() => String(route.name ?? ''));
const backTarget = computed(() => BACK_TARGETS[routeName.value] ?? null);

/** 返回上一层：按钮仅在 backTarget 非空时渲染，故此处无需再判空 */
function goBack(): void {
  void router.push(backTarget.value.path);
}

// 登录用户名与角色：JWT 载荷在登录时下发，前端只做展示与菜单渲染
const username = computed(() => authStore.username ?? '未登录');
const avatarText = computed(() => username.value.slice(0, 1).toUpperCase());

const roleLabels: Record<string, string> = {
  ADMIN: '管理员',
  USER: '普通用户',
};

const roleLabel = computed(() => roleLabels[authStore.role] ?? '普通用户');

// 退出登录：项目口径为客户端丢弃令牌（无登出接口），随后回到登录页
function handleLogout(): void {
  authStore.clearToken();
  ElMessage.success('已退出登录');
  void router.push('/login');
}
</script>

<style scoped lang="css">
.layout {
  position: relative;
  display: grid;
  grid-template-columns: 238px 1fr;
  height: 100vh;
  overflow: hidden;
  background: var(--kb-bg-0);
}

.layout-glow {
  position: fixed;
  top: -240px;
  left: 50%;
  z-index: 0;
  width: 1100px;
  height: 520px;
  background: radial-gradient(closest-side, var(--kb-aurora), transparent);
  filter: blur(20px);
  pointer-events: none;
  transform: translateX(-50%);
}

.layout-side {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  padding: 22px 14px;
  border-right: 1px solid var(--kb-line);
  background: linear-gradient(180deg, rgb(255 255 255 / 3%), transparent);
  backdrop-filter: blur(20px);
}

/* 品牌名是回首页的链接，需清掉锚点默认样式 */
.layout-brand {
  display: flex;
  gap: 11px;
  align-items: center;
  padding: 0 10px 24px;
  color: inherit;
  text-decoration: none;
}

.layout-logo {
  display: grid;
  flex: none;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 11px;
  background: linear-gradient(135deg, var(--kb-primary), var(--kb-primary-2));
  box-shadow: 0 0 20px var(--kb-glow);
}

.layout-brand-name {
  font-size: 18px;
  font-weight: 650;
  letter-spacing: 0.02em;
}

.layout-brand-sub {
  color: var(--kb-text-3);
  font-size: 11px;
  letter-spacing: 0.12em;
}

.layout-nav-group {
  margin: 14px 12px 8px;
  color: var(--kb-text-3);
  font-size: 11px;
  letter-spacing: 0.16em;
}

.layout-nav-item {
  position: relative;
  display: flex;
  gap: 11px;
  align-items: center;
  margin: 2px 0;
  padding: 10px 12px;
  border-radius: 10px;
  color: var(--kb-text-2);
  font-size: 14px;
  text-decoration: none;
  transition:
    background 0.18s,
    color 0.18s;
}

.layout-nav-item:hover {
  background: rgb(255 255 255 / 5%);
  color: var(--kb-text-1);
}

.layout-nav-item-active {
  background: var(--kb-tint);
  color: var(--kb-primary);
  font-weight: 600;
}

.layout-nav-item-active::before {
  position: absolute;
  top: 50%;
  left: -14px;
  width: 3px;
  height: 18px;
  border-radius: 2px;
  background: linear-gradient(180deg, var(--kb-primary), var(--kb-primary-2));
  box-shadow: 0 0 10px var(--kb-glow);
  content: '';
  transform: translateY(-50%);
}

.layout-nav-icon {
  flex: none;
  opacity: 0.8;
}

.layout-nav-item-active .layout-nav-icon {
  opacity: 1;
}

.layout-side-foot {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-top: auto;
  padding: 14px 10px 4px;
  border-top: 1px solid var(--kb-line);
}

/* 底部用户块整块可点：点开是退出登录菜单 */
.layout-user-drop {
  width: 100%;
}

.layout-user-trigger {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 4px 6px;
  border-radius: 10px;
  cursor: pointer;
  outline: none;
  transition: background 0.18s;
}

.layout-user-trigger:hover {
  background: rgb(255 255 255 / 5%);
}

.layout-avatar-clickable {
  cursor: pointer;
}

.layout-avatar {
  display: grid;
  flex: none;
  width: 32px;
  height: 32px;
  place-items: center;
  border: 1px solid rgb(52 211 153 / 40%);
  border-radius: 50%;
  background: linear-gradient(135deg, rgb(52 211 153 / 30%), rgb(45 212 191 / 20%));
  color: var(--kb-primary);
  font-size: 13px;
}

.layout-user-name {
  font-size: 13px;
}

.layout-user-role {
  color: var(--kb-text-3);
  font-size: 11px;
}

.layout-main {
  position: relative;
  z-index: 1;
  display: flex;
  min-width: 0;
  flex-direction: column;
  overflow: hidden;
}

.layout-topbar {
  display: flex;
  flex: none;
  gap: 18px;
  align-items: center;
  height: 62px;
  padding: 0 28px;
  border-bottom: 1px solid var(--kb-line);
  background: rgb(10 14 23 / 70%);
  backdrop-filter: blur(16px);
}

/* 顶栏左侧：只有返回按钮，首页不渲染（层级起点，整个左侧为空） */
.layout-topbar-left {
  display: flex;
  align-items: center;
}

/* 返回按钮：默认无边框，只有悬停才显出淡圆底。
   顶栏左侧是次要区域，返回是低重要度动作，常驻描边会让它过于抢眼 */
.layout-back {
  display: grid;
  width: 28px;
  height: 28px;
  flex: none;
  place-items: center;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: none;
  color: var(--kb-text-3);
  cursor: pointer;
  transition:
    background 0.15s,
    color 0.15s;
}

.layout-back:hover {
  background: rgb(255 255 255 / 7%);
  color: var(--kb-text-1);
}

.layout-top-right {
  display: flex;
  gap: 14px;
  align-items: center;
  margin-left: auto;
}

.layout-search {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 230px;
  padding: 8px 12px;
  border: 1px solid var(--kb-line);
  border-radius: 10px;
  background: rgb(255 255 255 / 3%);
  color: var(--kb-text-3);
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}

.layout-search:focus-within {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgb(52 211 153 / 12%);
}

.layout-search-input {
  width: 100%;
  border: none;
  outline: none;
  background: none;
  color: var(--kb-text-1);
  font-size: 13px;
}

.layout-search-input::placeholder {
  color: var(--kb-text-3);
}

/* 内容区不自己滚动（overflow: hidden）：页面若要「固定高度 + 内部滚动」，
   百分比/flex 高度链必须一路确定下来。为此子页面需自行管理超高内容的滚动。 */
.layout-content {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  padding: 26px 28px 40px;
}
</style>
