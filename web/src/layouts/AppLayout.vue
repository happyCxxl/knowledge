<template>
  <div class="layout">
    <div class="layout-glow"></div>
    <aside class="layout-side">
      <div class="layout-brand">
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
      </div>
      <div class="layout-nav-group">资产</div>
      <router-link
        class="layout-nav-item"
        :class="{ 'layout-nav-item-active': isActive }"
        to="/knowledge-base"
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
          <path d="M2.2 5.2 8 2l5.8 3.2v5.6L8 14 2.2 10.8V5.2Z" stroke-linejoin="round" />
          <path d="M2.2 5.2 8 8.4l5.8-3.2M8 8.4V14" />
        </svg>
        知识库
        <span class="layout-nav-badge">12</span>
      </router-link>
      <div class="layout-side-foot">
        <div class="layout-avatar">管</div>
        <div class="layout-user">
          <div class="layout-user-name">管理员</div>
          <div class="layout-user-role">knowledge 平台</div>
        </div>
      </div>
    </aside>
    <div class="layout-main">
      <header class="layout-topbar">
        <div class="layout-crumb">
          knowledge
          <span class="layout-crumb-sep">/</span>
          <span class="layout-crumb-current">知识库</span>
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
          <div class="layout-avatar">管</div>
        </div>
      </header>
      <div class="layout-content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';

// 侧边导航：当前激活项按路由路径判定
const route = useRoute();
const isActive = computed(() => route.path.startsWith('/knowledge-base'));
</script>

<style scoped>
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

.layout-brand {
  display: flex;
  gap: 11px;
  align-items: center;
  padding: 0 10px 24px;
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

.layout-nav-badge {
  margin-left: auto;
  padding: 1px 7px;
  border: 1px solid rgb(52 211 153 / 25%);
  border-radius: 99px;
  background: var(--kb-tint);
  color: var(--kb-primary);
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
}

.layout-side-foot {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-top: auto;
  padding: 14px 10px 4px;
  border-top: 1px solid var(--kb-line);
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

.layout-crumb {
  color: var(--kb-text-3);
  font-size: 13px;
  letter-spacing: 0.04em;
}

.layout-crumb-sep {
  margin: 0 8px;
  color: var(--kb-text-3);
}

.layout-crumb-current {
  color: var(--kb-text-2);
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

.layout-content {
  flex: 1;
  overflow: auto;
  padding: 26px 28px 40px;
}
</style>
