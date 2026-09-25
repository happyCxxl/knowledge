<template>
  <article class="kb-card">
    <div class="kb-top">
      <span class="kb-cube">
        <svg
          class="kb-icon"
          width="15"
          height="15"
          viewBox="0 0 16 16"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
        >
          <path d="M2.2 5.2 8 2l5.8 3.2v5.6L8 14 2.2 10.8V5.2Z" stroke-linejoin="round" />
        </svg>
      </span>
      <span class="kb-name">{{ kb.name }}</span>
      <span
        class="kb-tag"
        :class="{
          'kb-tag-ok': kb.status === 'running',
          'kb-tag-warn': kb.status === 'building',
          'kb-tag-mute': kb.status === 'stopped',
        }"
      >
        <span class="kb-tag-dot"></span>
        {{ statusText }}
      </span>
    </div>
    <p class="kb-desc">{{ kb.description }}</p>
    <div class="kb-meta">
      <div class="kb-meta-item">
        <span class="kb-meta-value">{{ kb.documentCount }}</span>
        <span class="kb-meta-label">文档</span>
      </div>
      <div class="kb-meta-item">
        <span class="kb-meta-value">{{ kb.dimension }}</span>
        <span class="kb-meta-label">维度</span>
      </div>
      <div class="kb-meta-item">
        <span class="kb-meta-value">{{ timeText }}</span>
        <span class="kb-meta-label">更新</span>
      </div>
    </div>
    <div class="kb-ops">
      <button class="kb-op" type="button" @click="emit('update')">编辑</button>
      <button class="kb-op" type="button" @click="emit('evaluate')">评测</button>
      <button class="kb-op kb-op-danger" type="button" @click="emit('delete')">删除</button>
      <span class="kb-op-date">{{ dateText }}</span>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import type { KnowledgeBase } from '@/types/knowledge-base';
import { formatDate, formatTime } from '@/utils/date';

// 知识库卡片：名称、状态、关键指标与操作入口
const props = defineProps<{
  /** 知识库业务数据 */
  kb: KnowledgeBase;
}>();

const emit = defineEmits<{
  update: [];
  evaluate: [];
  delete: [];
}>();

const statusTextMap: Record<KnowledgeBase['status'], string> = {
  running: '运行中',
  building: '构建中',
  stopped: '已停止',
};

const statusText = computed(() => statusTextMap[props.kb.status]);
const timeText = computed(() => formatTime(props.kb.updatedAt));
const dateText = computed(() => formatDate(props.kb.updatedAt));
</script>

<style scoped lang="css">
.kb-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px;
  border: 1px solid var(--kb-line);
  border-radius: var(--kb-radius);
  background: linear-gradient(180deg, rgb(255 255 255 / 3.5%), rgb(255 255 255 / 1.2%));
  cursor: pointer;
  transition:
    border-color 0.2s,
    box-shadow 0.2s,
    transform 0.2s;
}

.kb-card:hover {
  border-color: rgb(52 211 153 / 45%);
  box-shadow:
    0 14px 40px rgb(0 0 0 / 38%),
    0 0 24px rgb(52 211 153 / 8%);
  transform: translateY(-2px);
}

.kb-top {
  display: flex;
  gap: 11px;
  align-items: center;
}

.kb-cube {
  display: grid;
  flex: none;
  width: 38px;
  height: 38px;
  place-items: center;
  border: 1px solid rgb(52 211 153 / 22%);
  border-radius: 11px;
  background: var(--kb-tint);
  color: var(--kb-primary);
}

.kb-name {
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-tag {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  margin-left: auto;
  padding: 3px 10px;
  border: 1px solid;
  border-radius: 99px;
  font-size: 12px;
}

.kb-tag-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentcolor;
  box-shadow: 0 0 8px currentcolor;
}

.kb-tag-ok {
  border-color: rgb(163 230 53 / 30%);
  background: rgb(163 230 53 / 7%);
  color: var(--kb-ok);
}

.kb-tag-warn {
  border-color: rgb(251 191 36 / 30%);
  background: rgb(251 191 36 / 7%);
  color: var(--kb-warn);
}

.kb-tag-mute {
  border-color: var(--kb-line-strong);
  background: rgb(255 255 255 / 3%);
  color: var(--kb-text-3);
}

.kb-desc {
  min-height: 32px;
  margin: 0;
  color: var(--kb-text-3);
  font-size: 12px;
  line-height: 1.5;
}

.kb-meta {
  display: flex;
  align-items: center;
  padding: 9px 0;
  border: 1px solid var(--kb-line);
  border-radius: 10px;
  background: rgb(0 0 0 / 18%);
}

.kb-meta-item {
  position: relative;
  flex: 1;
  text-align: center;
}

.kb-meta-item + .kb-meta-item::before {
  position: absolute;
  top: 20%;
  left: 0;
  width: 1px;
  height: 60%;
  background: var(--kb-line);
  content: '';
}

.kb-meta-value {
  display: block;
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-size: 13px;
  font-weight: 600;
}

.kb-meta-label {
  display: block;
  margin-top: 2px;
  color: var(--kb-text-3);
  font-size: 11px;
}

.kb-ops {
  display: flex;
  gap: 4px;
  align-items: center;
  padding-top: 10px;
  border-top: 1px solid var(--kb-line);
}

.kb-op {
  margin-right: 6px;
  padding: 0;
  border: none;
  background: none;
  color: var(--kb-text-2);
  font-size: 13px;
  cursor: pointer;
  transition: color 0.15s;
}

.kb-op:hover {
  color: var(--kb-primary);
}

.kb-op-danger:hover {
  color: var(--kb-danger);
}

.kb-op-date {
  margin-left: auto;
  color: var(--kb-text-3);
  font-size: 11px;
}
</style>
