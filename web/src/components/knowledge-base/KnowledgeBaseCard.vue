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

    <!--
      策略逐条列举（预处理 → 切片 → 向量化，即实际处理顺序）。
      **不放进上面的数值格**：那是「三个等宽短值」的布局、每格约 74px，
      而策略串是标识符（实测 13~30 字符，最长 hybrid-rrf-k60-top10-parent-v1
      在等宽 11px 下约 181px），塞进去必然截断 —— 截断后就认不出是哪个策略，
      这正是原来「向量策略」显示不全的原因。
      每条独占一行、标签左值右，一行放得下 30 字符。
    -->
    <div class="kb-strategies">
      <div v-for="s in strategies" :key="s.type" class="kb-strategy">
        <span class="kb-strategy-k">{{ s.label }}</span>
        <span class="kb-strategy-v" :class="{ 'kb-strategy-mute': !s.text }">
          {{ s.text || fallbackStrategyText }}
        </span>
      </div>
    </div>
    <div class="kb-ops">
      <!-- 操作按钮必须 .stop：卡片整体可点，不阻止冒泡会连带跳转到处理链页 -->
      <button class="kb-op" type="button" @click.stop="emit('import', kb)">导入文档</button>
      <button class="kb-op" type="button" @click.stop="emit('update', kb)">编辑</button>
      <button class="kb-op" type="button" @click.stop="openIndex">索引与发布</button>
      <button class="kb-op" type="button" @click.stop="openRetrieval">评测</button>
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
import { STRATEGY_TYPE_LABELS, STRATEGY_TYPES } from '@/types/strategy';
import { formatDate, formatTime } from '@/utils/date';

// 知识库卡片：名称、状态、关键指标与操作入口
const props = defineProps<{
  /** 知识库业务数据 */
  kb: KnowledgeBase;
}>();

const emit = defineEmits<{
  /** 编辑：带出当前卡片数据供页面填表 */
  update: [kb: KnowledgeBase];
  /** 导入文档：预选该知识库打开导入弹窗 */
  import: [kb: KnowledgeBase];
  /** 删除：默认库不渲染该入口，因此不会触发 */
  delete: [kb: KnowledgeBase];
}>();

const router = useRouter();

/** 点卡片（或回车/空格）进入该库的文件处理链页 */
function openStages(): void {
  void router.push(`/knowledge-base/${props.kb.id}/stages`);
}

/** 进入该库的索引与发布页（组合版本、发布与回退） */
function openIndex(): void {
  void router.push(`/knowledge-base/${props.kb.id}/index`);
}

/**
 * 进入该库的检索评测页（测试台检索 + 运行记录并排对比 + 规则选优发布）。
 *
 * <p>此前这里是 `emit('evaluate')`，页面侧只弹「该功能待接入后端接口」——
 * 后端其实早就就绪（阶段 17 的 5 个接口），现在改成真跳转。
 */
function openRetrieval(): void {
  void router.push(`/knowledge-base/${props.kb.id}/retrieval`);
}

const statusText = computed(() => (props.kb.status === KB_STATUS_ACTIVE ? '已启用' : '已停用'));
const timeText = computed(() => formatTime(props.kb.updateTime ?? ''));
const dateText = computed(() => formatDate(props.kb.updateTime ?? ''));

/**
 * 三条绑定策略（按实际处理顺序：预处理 → 切片 → 向量化）。
 *
 * <p>顺序用 {@link STRATEGY_TYPES} 的声明顺序，而不是手写三个字面量 ——
 * 那个常量与后端 StrategyType 对齐，加类型时这里自动跟上。
 */
const strategies = computed(() =>
  STRATEGY_TYPES.map((type) => ({
    type,
    label: STRATEGY_TYPE_LABELS[type] ?? type,
    text:
      type === 'PREPROCESS'
        ? props.kb.preprocessStrategyVersion
        : type === 'CHUNK'
          ? props.kb.chunkStrategyVersion
          : props.kb.embedStrategyVersion,
  })),
);

/**
 * 策略缺失时的占位文案，要区分两种情况：
 * 绑定开关关着（测评模式，本来就不会绑）说"开关已关闭"，否则才是"未绑定"。
 * 混成一句会让用户以为该去绑策略，实际是开关的问题。
 */
const fallbackStrategyText = computed(() =>
  props.kb.strategyBindingEnabled === 1 ? '未绑定' : '开关已关闭',
);
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

/*
 * 策略逐条列举：标签左（定宽）、策略串右（占满剩余）。
 *
 * <p>用与 .kb-meta 同一套"描边容器"语言，但**不做等分栅格** —— 策略串长度差异大
 * （13~30 字符），等分必然截断最长的那个。左标签定宽 52px（最长「向量化」三个字），
 * 其余全给策略串：380px 卡片下约 236px 可用，够放 30 字符的 hybrid-rrf-k60-top10-parent-v1。
 */
.kb-strategies {
  margin-top: 8px;
  padding: 8px 11px;
  border: 1px solid var(--kb-line);
  border-radius: 10px;
  background: rgb(0 0 0 / 18%);
}

.kb-strategy {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.kb-strategy + .kb-strategy {
  margin-top: 6px;
}

.kb-strategy-k {
  flex: none;
  width: 52px;
  color: var(--kb-text-3);
  font-size: 11px;
}

.kb-strategy-v {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--kb-primary-2);
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
  font-weight: 600;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 未绑定 / 开关已关闭：弱化成普通文字，不用主色（否则会像"已配置"） */
.kb-strategy-mute {
  color: var(--kb-text-3);
  font-weight: 400;
}

.kb-ops {
  display: flex;
  gap: 4px;
  align-items: center;
  padding-top: 10px;
  border-top: 1px solid var(--kb-line);

  /*
   * 不换行：五个操作按钮在 320px 卡片里会被折成两行（实测），
   * 那种"挤成两行"比字小一点更难看。窄窗口下宁可让按钮挨得近一点。
   */
  flex-wrap: nowrap;
  white-space: nowrap;
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

  /* 允许收缩：极端窄窗口下先缩按钮间距，而不是立刻折行 */
  flex: none;
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
