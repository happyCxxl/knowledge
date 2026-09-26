<template>
  <div class="home">
    <!-- 欢迎语。导入/新建属于知识库页面的操作，首页不再重复放置 -->
    <div class="home-greet">
      <h1 class="home-hi">欢迎回来，{{ username }}</h1>
    </div>

    <!-- 动作卡：首页的主入口，三张都接了真实可用的去处 -->
    <div class="home-actions">
      <article class="home-action" @click="goImport">
        <span class="home-action-icon">↑</span>
        <h2 class="home-action-title">导入文档</h2>
        <p class="home-action-desc">上传文件到指定知识库，建档后逐环节触发处理</p>
        <span class="home-action-go">选择知识库 →</span>
      </article>
      <article class="home-action home-action-primary" @click="goCreate">
        <span class="home-action-icon">＋</span>
        <h2 class="home-action-title">新建知识库</h2>
        <p class="home-action-desc">创建知识库并绑定预处理、切片、向量化策略</p>
        <span class="home-action-go">开始创建 →</span>
      </article>
      <article class="home-action" @click="goKnowledgeBase">
        <span class="home-action-icon">⇄</span>
        <h2 class="home-action-title">查看处理链</h2>
        <p class="home-action-desc">进入知识库后点卡片，看每个文件卡在哪个环节</p>
        <span class="home-action-go">进入知识库 →</span>
      </article>
    </div>

    <!-- 数字细带：三格均来自 /knowledge-base/stats -->
    <div class="home-strip">
      <div class="home-strip-cell">
        <span class="home-strip-label">知识库总数</span>
        <span class="home-strip-num">{{ statsText.knowledgeBaseCount }}</span>
      </div>
      <div class="home-strip-cell">
        <span class="home-strip-label">文档总数</span>
        <span class="home-strip-num">{{ statsText.documentCount }}</span>
      </div>
      <div class="home-strip-cell">
        <span class="home-strip-label">启用中</span>
        <span class="home-strip-num">{{ statsText.enabledCount }}</span>
      </div>
    </div>

    <!-- 最近更新：点击直接进该库的处理链页 -->
    <div class="home-section-head">
      <span class="home-section-title">最近更新</span>
      <button class="home-section-more" type="button" @click="goKnowledgeBase">全部知识库 →</button>
    </div>

    <div v-if="recentLoading" class="home-panel home-empty">加载中…</div>
    <div v-else-if="recent.length === 0" class="home-panel home-empty">
      还没有知识库，先新建一个
    </div>
    <div v-else class="home-panel home-recent">
      <div v-for="kb in recent" :key="kb.id" class="home-recent-row" @click="goStages(kb.id)">
        <span class="home-dot" :class="{ 'is-off': kb.status !== KB_STATUS_ACTIVE }"></span>
        <div class="home-recent-main">
          <div class="home-recent-name">{{ kb.name }}</div>
          <div class="home-recent-desc">{{ recentDesc(kb) }}</div>
        </div>
        <div class="home-recent-right">
          <span>{{ kb.documentCount ?? 0 }} 篇</span>
          <span>{{ formatDate(kb.updateTime ?? '') }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { getKnowledgeBasePage, getKnowledgeBaseStats } from '@/api/knowledge-base';
import { useAuthStore } from '@/stores/auth';
import { KB_STATUS_ACTIVE } from '@/types/knowledge-base';
import type { KnowledgeBase, KnowledgeBaseStats } from '@/types/knowledge-base';
import { formatDate } from '@/utils/date';

// 系统首页：登录后的默认落地页，也是全局返回的终点。
// 数据只用两个现成接口，不新增后端依赖：
//   /knowledge-base/stats         → 三格数字
//   /knowledge-base/page?sort=UPDATED → 最近更新
const RECENT_SIZE = 5;

const router = useRouter();
const authStore = useAuthStore();

const username = computed(() => authStore.username ?? '');
const recent = ref<KnowledgeBase[]>([]);
const recentLoading = ref(false);
const stats = ref<KnowledgeBaseStats | null>(null);

/** 数字按千分位展示；未加载完显示占位符，避免闪 0 */
const statsText = computed(() => ({
  knowledgeBaseCount: formatCount(stats.value?.knowledgeBaseCount),
  documentCount: formatCount(stats.value?.documentCount),
  enabledCount: formatCount(stats.value?.enabledCount),
}));

function formatCount(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const num = Number(value);
  return Number.isNaN(num) ? '—' : num.toLocaleString('en-US');
}

/** 一行摘要：策略版本 + 索引发布状态，都是列表已有的真实字段 */
function recentDesc(kb: KnowledgeBase): string {
  if (kb.status !== KB_STATUS_ACTIVE) {
    return '已停用';
  }
  const strategy = kb.embedStrategyVersion ?? '未绑定向量化策略';
  const index = kb.publishedIndexVersion ? `已发布索引 ${kb.publishedIndexVersion}` : '未发布索引';
  return `${strategy} · ${index}`;
}

function goKnowledgeBase(): void {
  void router.push('/knowledge-base');
}

function goImport(): void {
  void router.push({ path: '/knowledge-base', query: { action: 'import' } });
}

function goCreate(): void {
  void router.push({ path: '/knowledge-base', query: { action: 'create' } });
}

function goStages(id: string): void {
  void router.push(`/knowledge-base/${id}/stages`);
}

async function loadStats(): Promise<void> {
  try {
    stats.value = await getKnowledgeBaseStats();
  } catch {
    // 失败提示已由接口层统一拦截；这里保持占位符
  }
}

async function loadRecent(): Promise<void> {
  recentLoading.value = true;
  try {
    const page = await getKnowledgeBasePage({ current: 1, size: RECENT_SIZE, sort: 'UPDATED' });
    recent.value = page.records;
  } catch {
    recent.value = [];
  } finally {
    recentLoading.value = false;
  }
}

onMounted(() => {
  void loadStats();
  void loadRecent();
});
</script>

<style scoped lang="css">
.home {
  display: flex;
  flex-direction: column;
}

.home-greet {
  margin-bottom: 14px;
}

.home-hi {
  margin: 0;
  font-size: 22px;
  font-weight: 650;
}

/* 动作卡 */
.home-actions {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(3, 1fr);
  margin-bottom: 14px;
}

.home-action {
  display: flex;
  flex-direction: column;
  padding: 18px;
  border: 1px solid var(--kb-line);
  border-radius: var(--kb-radius);
  background: linear-gradient(180deg, rgb(255 255 255 / 3.5%), rgb(255 255 255 / 1.2%));
  cursor: pointer;
  transition: border-color 0.15s;
}

.home-action:hover {
  border-color: rgb(52 211 153 / 45%);
}

.home-action-primary {
  border-color: rgb(52 211 153 / 40%);
  background: linear-gradient(180deg, rgb(52 211 153 / 9%), rgb(52 211 153 / 2%));
}

.home-action-icon {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: 10px;
  background: var(--kb-tint);
  color: var(--kb-primary);
  font-size: 17px;
}

.home-action-title {
  margin: 13px 0 0;
  font-size: 15px;
  font-weight: 650;
}

.home-action-desc {
  margin: 6px 0 0;
  color: var(--kb-text-3);
  font-size: 12px;
  line-height: 1.55;
}

.home-action-go {
  margin-top: 13px;
  color: var(--kb-primary);
  font-size: 12px;
  font-weight: 600;
}

/* 数字细带 */
.home-strip {
  display: flex;
  margin-bottom: 14px;
  border: 1px solid var(--kb-line);
  border-radius: var(--kb-radius);
  background: linear-gradient(180deg, rgb(255 255 255 / 3.5%), rgb(255 255 255 / 1.2%));
}

.home-strip-cell {
  display: flex;
  flex: 1;
  gap: 10px;
  align-items: baseline;
  padding: 12px 20px;
}

.home-strip-cell + .home-strip-cell {
  border-left: 1px solid var(--kb-line);
}

.home-strip-label {
  color: var(--kb-text-3);
  font-size: 12px;
}

.home-strip-num {
  margin-left: auto;
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-size: 18px;
  font-weight: 600;
}

/* 最近更新 */
.home-section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 10px;
}

.home-section-title {
  font-size: 14px;
  font-weight: 650;
}

.home-section-more {
  padding: 0;
  border: none;
  background: none;
  color: var(--kb-text-3);
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
  font-size: 12px;
  cursor: pointer;
}

.home-section-more:hover {
  color: var(--kb-primary);
}

.home-panel {
  border: 1px solid var(--kb-line);
  border-radius: var(--kb-radius);
  background: linear-gradient(180deg, rgb(255 255 255 / 3.5%), rgb(255 255 255 / 1.2%));
}

.home-empty {
  padding: 36px 0;
  color: var(--kb-text-3);
  font-size: 13px;
  text-align: center;
}

.home-recent {
  padding: 6px;
}

.home-recent-row {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 8px 14px;
  border-radius: 10px;
  cursor: pointer;
}

.home-recent-row:hover {
  background: rgb(255 255 255 / 4%);
}

.home-dot {
  width: 7px;
  height: 7px;
  flex: none;
  border-radius: 50%;
  background: var(--kb-ok);
}

.home-dot.is-off {
  background: var(--kb-text-3);
}

.home-recent-main {
  min-width: 0;
}

.home-recent-name {
  font-size: 13px;
  font-weight: 600;
}

.home-recent-desc {
  margin-top: 3px;
  overflow: hidden;
  color: var(--kb-text-3);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-recent-right {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: 3px;
  margin-left: auto;
  color: var(--kb-text-3);
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
  text-align: right;
}
</style>
