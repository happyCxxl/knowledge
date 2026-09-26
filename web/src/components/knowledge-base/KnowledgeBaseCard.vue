<template>
  <!-- 用 div 而非 router-link：卡片内含操作按钮，把 button 嵌进 a 是无效 HTML，
       浏览器会重排 DOM 导致事件行为不可预测。这里改为点击时编程式跳转 -->
  <div
    class="kb-card"
    role="link"
    tabindex="0"
    :title="`查看「${kb.name}」的文件处理链`"
    @click="openStages"
    @keydown.enter.prevent="openStages"
    @keydown.space.prevent="openStages"
  >
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
      <span v-if="kb.defaultFlag === 1" class="kb-default-tag">默认</span>
      <span
        class="kb-tag"
        :class="{
          'kb-tag-ok': kb.status === KB_STATUS_ACTIVE,
          'kb-tag-mute': kb.status === KB_STATUS_DISABLED,
        }"
      >
        <span class="kb-tag-dot"></span>
        {{ statusText }}
      </span>
    </div>
    <p class="kb-desc">{{ kb.description }}</p>
    <div class="kb-meta">
      <div class="kb-meta-item">
        <span class="kb-meta-value">{{ kb.documentCount ?? 0 }}</span>
        <span class="kb-meta-label">文档</span>
      </div>
      <div class="kb-meta-item">
        <span class="kb-meta-value">{{ kb.embedStrategyVersion ?? '未绑定' }}</span>
        <span class="kb-meta-label">向量策略</span>
      </div>
      <div class="kb-meta-item">
        <span class="kb-meta-value" :class="{ 'kb-meta-mute': !kb.publishedIndexVersion }">
          {{ kb.publishedIndexVersion ?? '未发布' }}
        </span>
        <span class="kb-meta-label">索引版本</span>
      </div>
      <div class="kb-meta-item">
        <span class="kb-meta-value">{{ timeText }}</span>
        <span class="kb-meta-label">更新</span>
      </div>
    </div>
    <div class="kb-ops">
      <!-- 操作按钮必须 .stop：卡片整体可点，不阻止冒泡会连带跳转到处理链页 -->
      <button class="kb-op" type="button" @click.stop="emit('update', kb)">编辑</button>
      <button class="kb-op" type="button" @click.stop="emit('evaluate')">评测</button>
      <!-- 默认库不可删除：直接不渲染入口，避免点了才被后端拒绝 -->
      <button
        v-if="kb.defaultFlag !== 1"
        class="kb-op kb-op-danger"
        type="button"
        @click.stop="emit('delete', kb)"
      >
        删除
      </button>
      <span class="kb-op-date">{{ dateText }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';

import type { KnowledgeBase } from '@/types/knowledge-base';
import { KB_STATUS_ACTIVE, KB_STATUS_DISABLED } from '@/types/knowledge-base';
import { formatDate, formatTime } from '@/utils/date';

// 知识库卡片：名称、状态、关键指标与操作入口
const props = defineProps<{
  /** 知识库业务数据 */
  kb: KnowledgeBase;
}>();

const emit = defineEmits<{
  /** 编辑：带出当前卡片数据供页面填表 */
  update: [kb: KnowledgeBase];
  evaluate: [];
  /** 删除：默认库不渲染该入口，因此不会触发 */
  delete: [kb: KnowledgeBase];
}>();

const router = useRouter();

/** 点卡片（或回车/空格）进入该库的文件处理链页 */
function openStages(): void {
  void router.push(`/knowledge-base/${props.kb.id}/stages`);
}

const statusText = computed(() => (props.kb.status === KB_STATUS_ACTIVE ? '已启用' : '已停用'));
const timeText = computed(() => formatTime(props.kb.updateTime ?? ''));
const dateText = computed(() => formatDate(props.kb.updateTime ?? ''));
</script>

<style scoped lang="css">
/* 整张卡片是进入环节页的入口（点击即跳转），故有指针与键盘可达性 */
.kb-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px;
  border: 1px solid var(--kb-line);
  border-radius: var(--kb-radius);
  background: linear-gradient(180deg, rgb(255 255 255 / 3.5%), rgb(255 255 255 / 1.2%));
  color: inherit;
  text-decoration: none;
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

/* 默认库标记：该库恒排最前且不可停用/删除，需与普通库一眼区分 */
.kb-default-tag {
  flex: none;
  padding: 2px 8px;
  border: 1px solid rgb(52 211 153 / 35%);
  border-radius: 999px;
  background: var(--kb-tint);
  color: var(--kb-primary);
  font-size: 11px;
  line-height: 1.5;
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
  overflow: hidden;
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 未发布索引：弱化显示，与真实版本号区分 */
.kb-meta-mute {
  color: var(--kb-text-3);
  font-weight: 400;
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
  text-decoration: none;
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
