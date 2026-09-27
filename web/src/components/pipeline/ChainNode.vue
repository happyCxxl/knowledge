<template>
  <div class="chain-node" :class="nodeClass">
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

    <span class="chain-node-stage">
      {{ stageText }}
      <b class="chain-node-stage-code">{{ stageCode }}</b>
    </span>

    <span class="chain-node-status">
      <i
        class="chain-dot"
        :class="{
          'tone-ok': tone === 'ok',
          'tone-run': tone === 'run',
          'tone-wait': tone === 'wait',
          'tone-fail': tone === 'fail',
        }"
      ></i>
      {{ statusText }}
    </span>

    <span class="chain-node-strategy" :class="{ 'is-em': !strategyText }">{{ strategyText }}</span>

    <span v-if="stats.length > 0" class="chain-node-stats">
      <span v-for="(text, i) in stats" :key="i">{{ text }}</span>
    </span>

    <span v-if="data.node.errorMsg" class="chain-node-err" :title="data.node.errorMsg">
      {{ data.node.errorMsg }}
    </span>

    <span class="chain-node-time">{{ timeText }}</span>

    <!-- 选中且是路径末端时，才允许从这里触发下游 -->
    <span v-if="data.pathEnd" class="chain-node-more">触发下一环节</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { Handle, Position } from '@vue-flow/core';
import type { NodeProps } from '@vue-flow/core';

import { STAGE_CODES, stageLabel, statusTone, taskStatusLabel } from '@/types/pipeline';
import type { ChainNodeData } from '@/types/pipeline';

// 执行树节点 = 一次运行。这里只负责「长什么样」，位置与选中由外层图组件控制。
// 注意：Vue Flow 要求自定义节点接收 NodeProps，自定义载荷放在 data 里
const props = defineProps<NodeProps<ChainNodeData>>();

/** 环节名画在节点上：这样节点被拖到任何位置，读图都不会认错环节 */
const stageText = computed(() => stageLabel(props.data.node.stage));
const stageCode = computed(() => STAGE_CODES[props.data.node.stage] ?? '');

const tone = computed(() => statusTone(props.data.node.status));
const statusText = computed(() => taskStatusLabel(props.data.node.status));

/** 有策略的环节显示策略版本；无策略环节（解析/组装）显示能力快照 */
const strategyText = computed(
  () => props.data.node.strategyVersion ?? props.data.node.capability ?? '—',
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

/** 统计摘要带键名，否则多个值会连成一串读不通 */
const stats = computed(() =>
  Object.entries(props.data.node.stats ?? {})
    .slice(0, 2)
    .map(([key, value]) => `${key} ${value}`),
);

const timeText = computed(() => {
  const value = props.data.node.startedAt;
  return value ? value.replace('T', ' ').slice(5, 16) : '';
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
.chain-node {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 120px;

  /* 定高下限：与 ChainGraph 的 NODE_ROW_HEIGHT 对齐。
     节点行距按固定高度算，若实际高度随内容浮动就会互相压住（实测过）。
     内容超出下限时自然撑高，但错误信息已做两行截断，不会失控 */
  min-height: 132px;
  padding: 8px 9px;
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

/* 节点状态样式（选中 / 路径末端 / 失败 / 运行中）定义在全局 styles/chain-graph.css：
   这些类名由计算属性动态给出，scoped 样式里的声明会被「未使用」检查判为死代码 */

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

.chain-node-status {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-top: 5px;
  color: var(--kb-text-2);
  font-size: 11px;
}

/* 环节名：节点的第一行，标明这次运行属于哪个环节 */
.chain-node-stage {
  display: flex;
  gap: 6px;
  align-items: baseline;
  color: var(--kb-text-2);
  font-size: 11px;
  font-weight: 600;
}

.chain-node-stage-code {
  color: var(--kb-text-3);
  font-size: 9px;
  font-weight: 400;
}

.chain-dot {
  width: 6px;
  height: 6px;
  flex: none;
  border-radius: 50%;
}

.chain-node-strategy {
  margin-top: 6px;
  overflow: hidden;
  color: var(--kb-text-2);
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chain-node-strategy.is-em {
  color: var(--kb-text-3);
  font-style: italic;
}

.chain-node-stats {
  display: flex;
  gap: 8px;
  margin-top: 6px;
  color: var(--kb-text-3);
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-size: 10px;
}

.chain-node-err {
  display: -webkit-box;
  margin-top: 5px;
  overflow: hidden;
  color: var(--kb-danger);
  font-size: 10px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow-wrap: anywhere;
}

.chain-node-time {
  margin-top: 5px;
  color: var(--kb-text-3);
  font-size: 9px;
}

.chain-node-more {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed var(--kb-line);
  color: var(--kb-primary);
  font-size: 9px;
  font-weight: 600;
}

.tone-ok {
  background: var(--kb-ok);
}

.tone-run {
  background: var(--kb-primary);
}

.tone-wait {
  background: var(--kb-warn);
}

.tone-fail {
  background: var(--kb-danger);
}
</style>
