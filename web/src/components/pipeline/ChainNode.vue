<template>
  <div
    class="chain-node"
    :class="[nodeClass, { 'is-fallback': detailIsFallback }]"
    :title="hoverTitle"
  >
    <!-- 连接桩：仅供后端血缘边路由用；用户不能拖拽连线（外层已禁用 connectable） -->
    <Handle type="target" :position="Position.Left" class="chain-handle" />
    <!-- 源桩按出度铺开：同一节点被多次用不同策略触发时，
         每条边从各自独立的桩出发，起点就不会叠在同一个点上 -->
    <Handle
      v-for="index in outCount"
      :id="`out-${index - 1}`"
      :key="index"
      type="source"
      :position="Position.Right"
      class="chain-handle chain-handle-out"
      :style="{ top: handleTop(index - 1) }"
    />
    <Handle v-if="outCount === 0" type="source" :position="Position.Right" class="chain-handle" />

    <span v-if="hit" class="chain-node-hit">命中策略</span>

    <!-- 主行：环节名 + 状态，放最上且字号最大 -->
    <span class="chain-node-head">
      <i class="chain-dot" :class="toneClass"></i>
      <span class="chain-node-stage">{{ stageText }}</span>
    </span>

    <!-- 副行：有策略显策略版本，无策略环节显能力快照（后端已解析成对象，这里拼成可读文案） -->
    <span v-if="detailText" class="chain-node-detail">{{ detailText }}</span>

    <!-- 数据行：统计摘要（键名映射成中文，最多两项） -->
    <span v-if="stats.length > 0" class="chain-node-stats">
      <span v-for="item in stats" :key="item.key" class="chain-node-stat">
        <span class="chain-node-stat-label">{{ item.label }}</span>
        <span class="chain-node-stat-value">{{ item.value }}</span>
      </span>
    </span>

    <span class="chain-node-time">{{ timeText }}</span>

    <!--
      选中且是路径末端时，才允许从这里触发下游。
      这是个**真按钮**（不是装饰性文字），长在它作用的节点上。
      `@pointerdown.stop` + `@click.stop` 阻止冒泡：点击不被 Vue Flow 当成
      选中/拖拽节点。
    -->
    <button
      v-if="data.pathEnd"
      class="chain-node-more"
      type="button"
      @pointerdown.stop
      @click.stop="data.onTrigger?.()"
    >
      触发下一环节
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { Handle, Position } from '@vue-flow/core';
import type { NodeProps } from '@vue-flow/core';

import {
  capabilityText,
  stageLabel,
  statEntry,
  statusTone,
  taskStatusLabel,
} from '@/types/pipeline';
import type { ChainNodeData } from '@/types/pipeline';

// 执行树节点 = 一次运行。这里只负责「长什么样」，位置与选中由外层图组件控制。
// 注意：Vue Flow 要求自定义节点接收 NodeProps，自定义载荷放在 data 里
const props = defineProps<NodeProps<ChainNodeData>>();

/** 环节名画在节点上：这样节点被拖到任何位置，读图都不会认错环节 */
const stageText = computed(() => stageLabel(props.data.node.stage));

const tone = computed(() => statusTone(props.data.node.status));
const statusText = computed(() => taskStatusLabel(props.data.node.status));

/**
 * 状态色调类名。
 *
 * <p>写成「取值 → 字面类名」的表，而不是拼 `tone-${tone}`：样式检查脚本只认模板/样式里
 * 出现过的字面类名，拼接出来的会被判成"样式类未使用"。
 */
const TONE_CLASSES: Record<string, string> = {
  ok: 'tone-ok',
  run: 'tone-run',
  wait: 'tone-wait',
  fail: 'tone-fail',
};

const toneClass = computed(() => TONE_CLASSES[tone.value] ?? 'tone-wait');

/**
 * 副行文案：有策略的环节显示策略版本，无策略环节（解析/组装）显示能力快照。
 *
 * <p>能力快照是**对象**（后端已解析），这里拼成 `pdfbox 3.0.4` 这样的可读文案；
 * 两者都没有时显示 `—`，让"这一格没有内容"和"内容为空"能区分开。
 */
const detailText = computed(() => {
  const strategy = props.data.node.strategyVersion;
  if (strategy) {
    return strategy;
  }
  return capabilityText(props.data.node.capability) ?? '—';
});

/** 是否为"能力快照缺失"的兜底文案：样式上弱化，不冒充真实值 */
const detailIsFallback = computed(
  () => !props.data.node.strategyVersion && capabilityText(props.data.node.capability) === null,
);

const hit = computed(() => props.data.hit);

/** 该节点的出度（下游运行数）：决定铺几个源连接桩 */
const outCount = computed(() => Math.max(props.data.outCount, 0));

/** 源桩在节点右侧的纵向位置：按序号均布，避免所有边从同一点出发 */
function handleTop(index: number): string {
  const total = outCount.value;
  if (total <= 1) {
    return '50%';
  }
  // 在 22%~78% 之间均布，既不贴边角也不重叠
  const ratio = 0.22 + (0.56 * index) / (total - 1);
  return `${Math.round(ratio * 100)}%`;
}

/**
 * 统计摘要：键名映射成中文标签，最多显示两项。
 *
 * <p>卡片宽度有限，统计项多的环节（如向量化有 recordCount + cachedCount）全列会换行，
 * 截断到两项。
 */
const stats = computed(() =>
  Object.entries(props.data.node.stats ?? {})
    .slice(0, 2)
    .map(([key, value]) => ({ key, ...statEntry(key, value) })),
);

const timeText = computed(() => {
  const value = props.data.node.startedAt;
  return value ? value.replace('T', ' ').slice(5, 16) : '';
});

/** 悬停提示：卡片不展示错误原因，但排查时需要一个入口（完整的节点详情面板后续再做） */
const hoverTitle = computed(() => {
  const node = props.data.node;
  const parts = [stageText.value, statusText.value];
  if (node.errorMsg) {
    parts.push(node.errorMsg);
  }
  return parts.join(' · ');
});

const nodeClass = computed(() => ({
  'is-selected': props.data.onPath,
  'is-end': props.data.pathEnd,
  'is-fail': tone.value === 'fail',
  'is-run': tone.value === 'run',
}));

defineOptions({ name: 'ChainNode' });
</script>

<style scoped lang="css">
/*
 * 卡片尺寸 168×150：按"最长内容 + 余量"定，须容下 `chunk-window-v1` 这类标签
 * 与"向量化"环节的多行子块。
 *
 * <p>与 ChainGraph 的 NODE_WIDTH / NODE_ROW_HEIGHT 保持一致
 * （行距按固定高度算，不一致会互相压住）。
 */
.chain-node {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 5px;
  width: 168px;
  min-height: 150px;
  padding: 9px 11px;
  border: 1px solid var(--kb-line-strong);
  border-radius: 11px;
  background: var(--kb-bg-2);
  cursor: grab;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}

.chain-node:active {
  cursor: grabbing;
}

/* 节点状态样式（选中 / 路径末端 / 失败 / 运行中 / 兜底文案）以及状态点的 tone-* 色
   都定义在全局 styles/chain-graph.css：这些类名由计算属性动态给出，
   scoped 样式里的声明会被「未使用」检查判为死代码 */

.chain-handle {
  width: 5px;
  height: 5px;
  border: none;
  background: var(--kb-line-strong);
  opacity: 0.9;
}

/* 多源桩：绝对定位在右边缘，按 handleTop 给出的百分比落位 */
.chain-handle-out {
  right: -3px;
  transform: translateY(-50%);
}

.chain-node-hit {
  position: absolute;
  top: -8px;
  right: 7px;
  padding: 1px 6px;
  border: 1px solid rgb(251 191 36 / 35%);
  border-radius: 99px;
  background: rgb(251 191 36 / 16%);
  color: var(--kb-warn);
  font-size: 9px;
}

/* ==================== 卡片内容：三档层级 ==================== */

/*
 * 主行：状态点 + 环节名（13px 粗）。
 * 环节名是读图时最先认的东西，给它最大的字号；状态靠左侧的圆点颜色表达，
 * 不单独占一行写"部分成功/成功"这类文字（失败另有整卡红边，见 chain-graph.css）。
 */
.chain-node-head {
  display: flex;
  gap: 7px;
  align-items: center;
}

.chain-node-stage {
  overflow: hidden;
  color: var(--kb-text-1);
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/*
 * 副行：策略版本或能力快照（11px 等宽）。
 * 内容多为 `chunk-window-v1` / `pdfbox 3.0.4` 这类标识符，用等宽字体。
 */
.chain-node-detail {
  overflow: hidden;
  color: var(--kb-text-2);
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 兜底的 `—`：弱化，不冒充真实值 */
.chain-node.is-fallback .chain-node-detail {
  color: var(--kb-text-3);
}

/* 数据行：统计（10px，最弱色） */
.chain-node-stats {
  display: flex;
  gap: 10px;
  color: var(--kb-text-3);
  font-size: 10px;
}

.chain-node-stat {
  display: flex;
  gap: 4px;
  align-items: baseline;
  white-space: nowrap;
}

/* 统计的数值用等宽 + 略亮的色，让"标签"和"数字"分得开 */
.chain-node-stat-value {
  color: var(--kb-text-2);
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
}

.chain-node-stat-label {
  color: var(--kb-text-3);
}

.chain-node-time {
  margin-top: auto;
  color: var(--kb-text-3);
  font-size: 10px;
}

/*
 * 「触发下一环节」按钮：实心主色小按钮，而不是一行装饰性文字。
 *
 * <p>它是卡片上唯一的**动作**（其余都是信息），用整块主色；信息区一律是文字，
 * 两者不会混淆。
 */
.chain-node-more {
  display: block;
  width: 100%;
  padding: 5px 0;
  border: none;
  border-radius: 6px;
  background: var(--kb-primary);
  color: var(--kb-btn-text);

  /* 显式写完整字体栈而不是 `font-family: inherit`：项目规范要求字体声明以通用族结尾 */
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition:
    background 0.15s,
    box-shadow 0.15s;
}

.chain-node-more:hover {
  box-shadow: 0 0 12px rgb(52 211 153 / 45%);
}

/* 键盘可达：卡片本身不聚焦，按钮是唯一焦点目标 */
.chain-node-more:focus-visible {
  outline: 2px solid var(--kb-primary-2);
  outline-offset: 2px;
}
</style>
