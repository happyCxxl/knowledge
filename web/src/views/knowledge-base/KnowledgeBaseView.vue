<template>
  <div class="kb-page">
    <div class="kb-page-head">
      <div class="kb-page-title-wrap">
        <h1 class="kb-page-title">知识库</h1>
        <p class="kb-page-desc">管理你的知识库与文档资产，查看构建与运行状态</p>
      </div>
      <div class="kb-actions">
        <el-button class="kb-btn-ghost" plain @click="handlePending">导入文档</el-button>
        <el-button class="kb-btn-primary" @click="handlePending">新建知识库</el-button>
      </div>
    </div>
    <div class="kb-stats">
      <div v-for="item in statItems" :key="item.label" class="kb-stat">
        <span class="kb-stat-num">{{ item.value }}</span>
        <span class="kb-stat-label">{{ item.label }}</span>
        <span class="kb-stat-delta">{{ item.delta }}</span>
      </div>
    </div>
    <div class="kb-panel">
      <div class="kb-panel-head">
        <span class="kb-panel-title">全部知识库</span>
        <button
          v-for="filter in statusFilters"
          :key="filter.value"
          class="kb-chip"
          :class="{ 'kb-chip-on': selectedStatus === filter.value }"
          type="button"
          @click="handleFilter(filter.value)"
        >
          {{ filter.label }}
        </button>
        <button class="kb-refresh" type="button" @click="handlePending">
          <svg
            class="kb-refresh-icon"
            width="14"
            height="14"
            viewBox="0 0 16 16"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
          >
            <path d="M13.4 8a5.4 5.4 0 1 1-1.6-3.8M13.4 1.9v2.4H11" />
          </svg>
        </button>
      </div>
      <div v-if="filteredList.length > 0" class="kb-grid">
        <KnowledgeBaseCard
          v-for="item in filteredList"
          :key="item.id"
          :kb="item"
          @update="handlePending"
          @evaluate="handlePending"
          @delete="handlePending"
        />
        <button class="kb-new" type="button" @click="handlePending">
          <span class="kb-new-plus">+</span>
          新建知识库
        </button>
      </div>
      <div v-else class="kb-empty">暂无知识库</div>
      <div class="kb-panel-foot">
        <span>共 12 个知识库</span>
        <el-pagination class="kb-pager" layout="prev, pager, next" :total="120" :page-size="12" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed, ref } from 'vue';

import KnowledgeBaseCard from '@/components/knowledge-base/KnowledgeBaseCard.vue';
import type { KnowledgeBase, KnowledgeBaseStatus } from '@/types/knowledge-base';

// 知识库页：统计概览与知识库卡片列表
type StatusFilter = 'all' | KnowledgeBaseStatus;

const statusFilters: { value: StatusFilter; label: string }[] = [
  { value: 'all', label: '全部' },
  { value: 'running', label: '运行中' },
  { value: 'building', label: '构建中' },
  { value: 'stopped', label: '已停止' },
];

const statItems = [
  { value: '12', label: '知识库', delta: '+3' },
  { value: '1,284', label: '文档总数', delta: '+156' },
  { value: '3', label: '运行中知识库', delta: '全部正常' },
  { value: '1.2 TB', label: '向量存储', delta: '+0.1' },
];

// 知识库示例数据
const kbList: KnowledgeBase[] = [
  {
    id: '1',
    name: '金融研报库',
    description: '金融行业 · 研究报告与公告',
    dimension: 1024,
    documentCount: 356,
    status: 'running',
    updatedAt: '2026-09-24T08:12:00',
  },
  {
    id: '2',
    name: '技术文档库',
    description: '工程规范 · 接口文档',
    dimension: 768,
    documentCount: 512,
    status: 'running',
    updatedAt: '2026-09-24T07:45:00',
  },
  {
    id: '3',
    name: '评测题库',
    description: '检索评测 · 标注语料',
    dimension: 1024,
    documentCount: 208,
    status: 'building',
    updatedAt: '2026-09-24T06:58:00',
  },
  {
    id: '4',
    name: '产品手册库',
    description: '产品使用说明 · 常见问题',
    dimension: 512,
    documentCount: 96,
    status: 'running',
    updatedAt: '2026-09-23T22:30:00',
  },
  {
    id: '5',
    name: '运维知识沉淀',
    description: '故障案例 · 运维手册',
    dimension: 768,
    documentCount: 112,
    status: 'stopped',
    updatedAt: '2026-09-22T16:05:00',
  },
  {
    id: '6',
    name: '法务合规库',
    description: '合同模板 · 制度文件',
    dimension: 512,
    documentCount: 74,
    status: 'running',
    updatedAt: '2026-09-23T11:20:00',
  },
];

const selectedStatus = ref<StatusFilter>('all');

const filteredList = computed(() => {
  if (selectedStatus.value === 'all') {
    return kbList;
  }
  return kbList.filter((item) => item.status === selectedStatus.value);
});

function handleFilter(value: StatusFilter): void {
  selectedStatus.value = value;
}

// 待接入后端接口的功能统一提示入口
function handlePending(): void {
  ElMessage.info('该功能待接入后端接口');
}
</script>

<style scoped lang="css">
.kb-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.kb-page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
}

.kb-page-title {
  margin: 0;
  font-size: 22px;
  font-weight: 650;
}

.kb-page-desc {
  margin: 6px 0 0;
  color: var(--kb-text-3);
  font-size: 13px;
}

.kb-actions {
  display: flex;
  gap: 10px;
}

.kb-btn-ghost {
  border-color: var(--kb-line-strong);
  background: rgb(255 255 255 / 4%);
  color: var(--kb-text-1);
}

.kb-btn-primary {
  border: none;
  background: linear-gradient(135deg, var(--kb-primary), var(--kb-primary-2));
  box-shadow: 0 6px 22px rgb(52 211 153 / 25%);
}

.kb-btn-primary:hover,
.kb-btn-primary:focus {
  background: linear-gradient(135deg, var(--kb-primary), var(--kb-primary-2));
  box-shadow: 0 8px 28px var(--kb-glow);
  filter: brightness(1.08);
}

.kb-stats {
  display: flex;
  align-items: baseline;
  padding: 6px 2px;
}

.kb-stat {
  display: flex;
  gap: 10px;
  align-items: baseline;
  padding: 0 30px;
}

.kb-stat:first-child {
  padding-left: 0;
}

.kb-stat + .kb-stat {
  border-left: 1px solid var(--kb-line);
}

.kb-stat-num {
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-size: 24px;
  font-weight: 650;
}

.kb-stat-label {
  color: var(--kb-text-3);
  font-size: 13px;
}

.kb-stat-delta {
  color: var(--kb-ok);
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
}

.kb-panel {
  overflow: hidden;
  border: 1px solid var(--kb-line);
  border-radius: var(--kb-radius);
  background: linear-gradient(180deg, rgb(255 255 255 / 3%), rgb(255 255 255 / 1.2%));
}

.kb-panel-head {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 15px 18px;
  border-bottom: 1px solid var(--kb-line);
}

.kb-panel-title {
  font-size: 15px;
  font-weight: 600;
}

.kb-chip {
  padding: 5px 12px;
  border: 1px solid var(--kb-line);
  border-radius: 99px;
  background: none;
  color: var(--kb-text-2);
  font-size: 12px;
  cursor: pointer;
  transition:
    border-color 0.18s,
    background 0.18s,
    color 0.18s;
}

.kb-chip:hover {
  border-color: var(--kb-line-strong);
  color: var(--kb-text-1);
}

.kb-chip-on {
  border-color: rgb(52 211 153 / 40%);
  background: var(--kb-tint);
  color: var(--kb-primary);
}

.kb-refresh {
  display: grid;
  width: 30px;
  height: 30px;
  margin-left: auto;
  place-items: center;
  border: 1px solid var(--kb-line);
  border-radius: 10px;
  background: rgb(255 255 255 / 3%);
  color: var(--kb-text-2);
  cursor: pointer;
  transition:
    border-color 0.2s,
    color 0.2s;
}

.kb-refresh:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}

.kb-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  padding: 18px;
}

.kb-new {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  justify-content: center;
  min-height: 180px;
  border: 1px dashed var(--kb-line-strong);
  border-radius: var(--kb-radius);
  background: transparent;
  color: var(--kb-text-3);
  cursor: pointer;
  transition:
    border-color 0.2s,
    background 0.2s,
    color 0.2s;
}

.kb-new:hover {
  border-color: rgb(52 211 153 / 50%);
  background: var(--kb-tint);
  color: var(--kb-primary);
}

.kb-new-plus {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border: 1px dashed rgb(52 211 153 / 50%);
  border-radius: 50%;
  color: var(--kb-primary);
  font-size: 19px;
}

.kb-empty {
  padding: 60px 0;
  color: var(--kb-text-3);
  font-size: 13px;
  text-align: center;
}

.kb-panel-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 13px 18px;
  border-top: 1px solid var(--kb-line);
  color: var(--kb-text-3);
  font-size: 13px;
}
</style>
