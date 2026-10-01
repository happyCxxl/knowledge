<template>
  <div class="layout" :class="{ 'layout-side-collapsed': sideCollapsed }">
    <div class="layout-glow"></div>
    <aside class="layout-side">
      <!-- 品牌行：logo + 项目名 -->
      <div class="layout-brand-row">
        <!-- 品牌名可点回首页；收起时只剩图标 -->
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
      </div>

      <!--
        折叠开关：骑在菜单栏右边界垂直中点上，做成一道发光的"激光缝"。
        两种状态**同色系（青蓝）**，只靠三角方向区分：展开朝左、收起朝右。
      -->
      <button
        class="layout-side-toggle"
        :class="{ 'is-collapsed': sideCollapsed }"
        type="button"
        :title="sideCollapsed ? '展开菜单' : '收起菜单'"
        aria-label="展开或收起侧边菜单"
        @click="toggleSide"
      ></button>
      <router-link
        v-for="item in visibleNavItems"
        :key="item.path"
        class="layout-nav-item"
        :class="{ 'layout-nav-item-active': isNavActive(item.path) }"
        :to="item.path"
        :title="sideCollapsed ? item.label : undefined"
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
          <path v-for="(d, index) in item.iconPaths" :key="index" :d="d" stroke-linejoin="round" />
          <circle v-if="item.iconCircle" cx="8" cy="5.4" r="2.6" />
        </svg>
        <span class="layout-nav-label">{{ item.label }}</span>
      </router-link>
      <div class="layout-side-foot">
        <el-dropdown class="layout-user-drop" trigger="click" placement="top-start">
          <div class="layout-user-trigger">
            <img
              v-if="avatarUrl"
              class="layout-avatar layout-avatar-img"
              :src="avatarUrl"
              alt=""
              @error="onAvatarError"
            />
            <div v-else class="layout-avatar">{{ avatarText }}</div>
            <div class="layout-user">
              <div class="layout-user-name">{{ displayNameLabel }}</div>
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
        <!-- 左侧只有返回按钮：页面名称不出现在顶栏。
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
          <el-dropdown trigger="click" placement="bottom-end">
            <img
              v-if="avatarUrl"
              class="layout-avatar layout-avatar-clickable layout-avatar-img"
              :src="avatarUrl"
              alt=""
              @error="onAvatarError"
            />
            <div v-else class="layout-avatar layout-avatar-clickable">{{ avatarText }}</div>
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
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAuthStore } from '@/stores/auth';
import { readCollapsed, writeCollapsed } from '@/utils/ui-state-storage';

// 侧边导航：数据驱动，按角色的可见性在渲染前过滤
const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

/** 侧边栏折叠状态：收起后只留图标，宽度让给内容区。偏好持久化，刷新后保持 */
const sideCollapsed = ref(readCollapsed('layout-side'));

function toggleSide(): void {
  sideCollapsed.value = !sideCollapsed.value;
  writeCollapsed('layout-side', sideCollapsed.value);
}

interface NavItem {
  label: string;
  path: string;
  iconPaths: string[];
  iconCircle?: boolean;
  /** 仅管理员可见的菜单 */
  adminOnly?: boolean;
}

/** 侧边导航项（扁平结构）。 */
const navItems: NavItem[] = [
  {
    label: '首页',
    path: '/home',
    iconPaths: ['M2.4 6.6 8 2.6l5.6 4v6.8H2.4z', 'M6.2 13.4V9.2h3.6v4.2'],
  },
  {
    label: '知识库',
    path: '/knowledge-base',
    iconPaths: ['M2.2 5.2 8 2l5.8 3.2v5.6L8 14 2.2 10.8V5.2Z', 'M2.2 5.2 8 8.4l5.8-3.2M8 8.4V14'],
  },
  {
    // 策略管理：预处理/切片/向量化/检索四类策略的版本管理
    label: '策略管理',
    path: '/strategy',
    iconPaths: ['M2.6 4.4h10.8M2.6 8h10.8M2.6 11.6h10.8', 'M5.6 2.8v3.2M10.4 6.4v3.2M6.8 10v3.2'],
  },
  {
    label: '用户管理',
    path: '/user',
    iconPaths: ['M2.8 13.6c0-2.4 2.3-3.8 5.2-3.8s5.2 1.4 5.2 3.8'],
    iconCircle: true,
    adminOnly: true,
  },
  {
    // 个人中心：账号信息、联系方式与改密；放在主导航末项，人人可见
    label: '个人中心',
    path: '/profile',
    iconPaths: [
      'M8 2.2a5.8 5.8 0 1 1 0 11.6A5.8 5.8 0 0 1 8 2.2',
      'M4.8 12.4c0-1.7 1.4-2.8 3.2-2.8s3.2 1.1 3.2 2.8',
    ],
    iconCircle: true,
  },
];

/** 按角色过滤菜单：管理员看全部，普通用户看不到管理类菜单 */
const visibleNavItems = computed(() =>
  navItems.filter((item) => !item.adminOnly || authStore.isAdmin),
);

function isNavActive(path: string): boolean {
  // 精确匹配：'/' 前缀的首页项只应命中首页
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
  IndexBuild: { path: '/knowledge-base', label: '知识库' },
  RetrievalEval: { path: '/knowledge-base', label: '知识库' },
  StrategyManagement: { path: '/home', label: '首页' },
  UserManagement: { path: '/home', label: '首页' },
  Profile: { path: '/home', label: '首页' },
};

const routeName = computed(() => String(route.name ?? ''));
const backTarget = computed(() => BACK_TARGETS[routeName.value] ?? null);

/** 返回上一层：按钮仅在 backTarget 非空时渲染，故此处无需再判空 */
function goBack(): void {
  void router.push(backTarget.value.path);
}

// 展示用姓名：优先真实姓名（登录响应下发并本地持久化），无则回落登录名
const displayNameLabel = computed(() => authStore.displayName ?? authStore.username ?? '未登录');
const avatarText = computed(() => displayNameLabel.value.slice(0, 1).toUpperCase());

// 头像：登录与资料接口带回的是可直接渲染的接口地址；未设置、或图挂了都回落姓名首字
const avatarFailed = ref(false);
const avatarUrl = computed(() => (avatarFailed.value ? null : authStore.avatar));

function onAvatarError(): void {
  avatarFailed.value = true;
}

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
  transition: grid-template-columns 0.22s ease;
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

/*
 * 品牌行：logo + 项目名（折叠开关已移到右边界，不在这行）
 *
 * <p>**左内边距在展开/收起两态必须完全相同**：两态都不设左内边距，
 * logo 位置只由这一处决定。
 */
.layout-brand-row {
  display: flex;
  gap: 8px;
  align-items: center;

  /*
   * 品牌 logo 与导航图标对齐到同一条竖中线：
   *   logo 左缘 = 14(侧栏) + 10 = 24，logo 21px → 中线 34.5 ≈ 导航图标中线(14 + 12 + 8 = 34)
   * 微调：只改这个 padding 的左右值，1px 对应 1px。
   */
  padding: 0 0 24px;
}

/*
 * 品牌链接：`flex: none` 让它**不被压缩**。
 *
 * <p>收窄由内部文字块承担（它自己有定宽 + `overflow: hidden`）。
 */
.layout-brand {
  display: flex;
  gap: 11px;
  flex: none;
  align-items: center;
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

.layout-nav-item {
  position: relative;
  display: flex;
  gap: 11px;
  align-items: center;

  /* 固定左内边距：图标 x 坐标在折叠前后都算得出来，不会因 justify-content 变化而"跳" */
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

  /*
   * 左右内边距归 0：头像左缘 = 14(侧栏) + 0 + 4(触发元素) = 18，头像 32px → 中线 34，
   * 与导航图标的中线（14 + 12 + 8 = 34）重合。
   * 该值与折叠状态无关（折叠只改文字块宽度），折叠动画期间头像不横向移动。
   * 微调：改下面 .layout-user-trigger 的 padding-left，1px 对应 1px。
   */
  padding: 14px 0 4px;
  border-top: 1px solid var(--kb-line);
}

/* 底部用户块整块可点：点开是退出登录菜单 */
.layout-user-drop {
  width: 100%;
  min-width: 0;
  max-width: 100%;
}

.layout-user-trigger {
  display: flex;

  /*
   * 撑满整行：与上面菜单项的悬浮框同宽（左边距、宽度、圆角、底色全部一致）。
   * 宽度只决定盒子右边界，不影响头像位置 —— 头像的横向位置由 padding-left 决定。
   */
  width: 100%;
  gap: 10px;
  align-items: center;

  /*
   * 盒子绝不超出容器：hover 背景就是触发元素的盒子，max-width 夹住它即可。
   * 这里**不能**用 overflow: hidden —— 折叠态底栏只有 36px，而 4 + 32(头像) + 6 = 42px，
   * 裁剪会把头像右边切掉。
   */
  max-width: 100%;

  /* 左侧 4px 决定头像横向位置（对齐微调只改这一个值）；右侧 6px 给姓名留呼吸位，不影响对齐 */
  padding: 4px 6px 4px 4px;
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
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
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

/*
 * 内容区不自己滚动（overflow: hidden）：页面若要「固定高度 + 内部滚动」，
 * 百分比/flex 高度链必须一路确定下来。为此子页面需自行管理超高内容的滚动。
 */
.layout-content {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  padding: 26px 28px 40px;
}

/* ==================== 侧边栏折叠（放最后：覆盖上面的基础样式）==================== */

/*
 * 会被折叠挤掉的文字块：基础态显式给宽度 + transition。
 *
 * <p>**基础态必须写 width**：不写的话宽度是"自动"，折叠时从 auto 到 0
 * 无法过渡（auto 不可动画），表现为文字瞬间消失；显式给个上限宽度后，
 * 收起 0 / 展开回该宽度，两个方向都平滑。
 *
 * <p>**宽度取"大于容器可用宽"**：展开时侧栏内容区 210px。文字块若设成
 * 刚好放得下（如 148px），它会和 logo 一起参与 flex 分配——侧栏一收窄就先换行、
 * 再被压缩，中间过程交给引擎重新排版。设成 260px 后
 * 它**始终超出容器**，只会被侧栏边界匀速裁掉，全程不重排。
 *
 * <p>**必须配 `white-space: nowrap`**：宽度收到 0 时，不禁换行的文字会被逼成
 * 一行一个字竖着堆起来（品牌文字块高度会从 39px 涨到 129px）。禁换行后
 * 它只是被裁掉，高度不变。
 */
.layout-brand-text {
  width: 260px;
  min-height: 39px;
  overflow: hidden;
  white-space: nowrap;
  transition:
    width 0.22s ease,
    opacity 0.22s ease;
}

.layout-nav-label {
  width: 200px;
  overflow: hidden;
  white-space: nowrap;
  transition:
    width 0.22s ease,
    opacity 0.22s ease;
}

.layout-user {
  width: 200px;

  /* min-width:auto 会以"最宽一行文字"为下限，这个块会撑破侧栏；置 0 才允许收缩 */
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  transition:
    width 0.22s ease,
    opacity 0.22s ease;
}

/*
 * 收起 = 图标模式：栅格首列由 238px 转到 64px，菜单只留图标。
 *
 * <p>**不给 `.layout-nav-item` 换 `justify-content`、也不改它的 padding**：
 * 内边距自始至终不变，变的只有侧栏宽度与文字的宽度/透明度，图标随栏一起平移。
 */
.layout-side-collapsed {
  grid-template-columns: 64px 1fr;
}

.layout-side-collapsed .layout-brand-text,
.layout-side-collapsed .layout-nav-label,
.layout-side-collapsed .layout-user {
  width: 0;
  opacity: 0;
}

/*
 * 折叠开关：骑在菜单栏右边界垂直中点上，做成一道发光的"激光缝"（6×40）。
 *
 * <p>**定位**：`right: -3px` = 自身宽度的一半，这条缝压在边界线上、左右各露一半；
 * `top: 50%` + `translateY(-50%)` 落在垂直中点。
 * 只依赖栏的右边界，与栏内内容无关。
 *
 * <p>**两种状态同色系（青蓝）**，只靠三角方向区分：
 * 展开 = 三角朝左（提示可以往左收起）；收起 = 三角朝右（提示可以往右展开）。
 *
 * <p>主体是一道 6px 宽的渐变光缝，靠 `box-shadow` 外溢出发光：
 * 外面**不能有 `overflow: hidden`**（那会把光晕切掉）。
 */
.layout-side-toggle {
  position: absolute;
  top: 50%;
  right: -3px;
  z-index: 3;
  width: 6px;
  height: 40px;
  padding: 0;
  border: none;
  border-radius: 3px;
  background: linear-gradient(
    180deg,
    rgb(52 211 153 / 0%),
    rgb(52 211 153 / 30%),
    rgb(45 212 191 / 85%),
    rgb(52 211 153 / 30%),
    rgb(52 211 153 / 0%)
  );
  box-shadow: 0 0 10px rgb(52 211 153 / 50%);
  cursor: pointer;
  transform: translateY(-50%);
  transition:
    height 0.24s ease,
    background 0.24s ease,
    box-shadow 0.24s ease;
}

/*
 * 缝里的三角：提示"点它可以朝这个方向收/展"。
 * 用实心三角形（border 技巧）而不是图标：它只有 6px 宽，描边箭头会糊。
 * 颜色取面板底色 `--kb-bg-1`。
 */
.layout-side-toggle::after {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 0;
  height: 0;
  border-top: 3px solid transparent;
  border-right: 4px solid var(--kb-bg-1);
  border-bottom: 3px solid transparent;
  content: '';
  transform: translate(-38%, -50%);
  transition:
    border 0.24s ease,
    transform 0.24s ease;
}

/* 悬停：光缝拉长加亮，给一点"通电"的反馈 */
.layout-side-toggle:hover {
  height: 52px;
  box-shadow: 0 0 20px rgb(52 211 153 / 85%);
}

/* ==================== 收起态：同色系，只翻三角 ==================== */

/*
 * 收起态**不换色**。
 * 状态差异靠三角方向 + 光晕强弱表达，颜色始终是青蓝。
 */
.layout-side-toggle.is-collapsed {
  box-shadow: 0 0 14px rgb(52 211 153 / 65%);
}

.layout-side-toggle.is-collapsed::after {
  border-right: none;
  border-left: 4px solid var(--kb-bg-1);
  transform: translate(-62%, -50%);
}

.layout-side-toggle.is-collapsed:hover {
  box-shadow: 0 0 20px rgb(52 211 153 / 85%);
}

/* 图片头像：按方形裁切填满圆框，不设会拉变形 */
.layout-avatar-img {
  object-fit: cover;
}

/*
 * 侧栏底部用户块的触发元素：Element Plus 会给它加
 * `.el-tooltip__trigger:focus-visible { outline: 2px solid …; outline-offset: 1px }`（特异性 0,2,0），
 * 会盖过组件里 `.layout-user-trigger { outline: none }`（0,1,0），而且带 1px 外偏移 ——
 * 那个框会画到侧栏容器外面。借助 scoped 附加的 [data-v-*]（0,3,0）压掉它。
 */
.layout-user-trigger:focus,
.layout-user-trigger:focus-visible,
.layout-user-drop:focus-within {
  outline: none;
}
</style>
