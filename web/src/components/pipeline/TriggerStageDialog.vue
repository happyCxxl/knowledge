<template>
  <el-dialog
    v-model="visible"
    :title="`触发${stageText}`"
    width="460px"
    :close-on-click-modal="false"
    @closed="onClosed"
  >
    <div v-if="upstream" class="trigger-upstream">
      <div class="trigger-row">
        <span class="trigger-key">从哪个产物分叉</span>
        <span class="trigger-val">{{ upstreamText }}</span>
      </div>
      <div class="trigger-row">
        <span class="trigger-key">上游状态</span>
        <span class="trigger-val">
          <i
            class="trigger-dot"
            :class="{
              'tone-ok': upstreamTone === 'ok',
              'tone-partial': upstreamTone === 'partial',
              'tone-run': upstreamTone === 'run',
              'tone-wait': upstreamTone === 'wait',
              'tone-fail': upstreamTone === 'fail',
            }"
          ></i>
          {{ upstreamStatusText }}
        </span>
      </div>
    </div>
    <div v-else class="trigger-upstream trigger-warn">
      未选中路径末端，无法确定从哪个产物触发下游
    </div>

    <!-- 存储类型不一致：与上面的空态并列显示，确认按钮同时置灰 -->
    <div v-if="blockReason" class="trigger-block">{{ blockReason }}</div>

    <!-- 有策略的环节才需要选策略；解析/组装无策略 -->
    <div v-if="strategyRequired" class="trigger-strategy">
      <div class="trigger-row">
        <span class="trigger-key">使用策略</span>
        <span class="trigger-val">{{ strategyHint }}</span>
      </div>
      <el-select
        v-model="pickedStrategyId"
        class="trigger-select"
        placeholder="选择策略版本"
        :loading="strategiesLoading"
        clearable
      >
        <el-option
          v-for="item in strategies"
          :key="item.id"
          :label="strategyDisplayName(item)"
          :value="item.id"
        >
          <span class="trigger-option">
            <span>{{ strategyDisplayName(item) }}</span>
            <span class="trigger-option-status">{{ item.status }}</span>
          </span>
        </el-option>
      </el-select>
      <p class="trigger-note">不选则由后端按知识库绑定的策略解析</p>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="!upstream || blockReason !== ''"
        @click="onConfirm"
      >
        触发执行
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import { getStrategyVersions } from '@/api/strategy';
import { capabilityText, stageLabel, statusTone, taskStatusLabel } from '@/types/pipeline';
import type { LineageNode, PipelineStage } from '@/types/pipeline';
import { strategyDisplayName } from '@/types/strategy';
import type { StrategyVersion } from '@/types/strategy';

// 触发确认弹窗：明确「从哪个产物分叉」+「用哪个策略」，确认后才真正调接口。
// 产生真实产物的操作：必须先让用户看清输入再执行
const visible = defineModel<boolean>({ required: true });

const props = defineProps<{
  /** 要触发的环节 */
  stage: PipelineStage | null;
  /** 上游节点（选中路径的末端），其 productId 即分叉点 */
  upstream: LineageNode | null;
  submitting: boolean;
  /**
   * 当前文件的存储类型与当前写入后端不一致时的原因文案（一致或码值缺失时为空串）。
   *
   * <p>判据与文案由页面给：弹窗只负责显示与置灰。
   */
  blockReason: string;
}>();

const emit = defineEmits<{
  confirm: [strategyVersionId: string | null];
}>();

const strategies = ref<StrategyVersion[]>([]);
const strategiesLoading = ref(false);
const pickedStrategyId = ref<string>('');

const stageText = computed(() => (props.stage ? stageLabel(props.stage) : ''));

/** 解析与组装无策略，只有后三个环节需要选策略 */
const strategyRequired = computed(() =>
  ['PREPROCESS', 'CHUNK', 'EMBED'].includes(props.stage ?? ''),
);

const upstreamText = computed(() => {
  const node = props.upstream;
  if (!node) {
    return '';
  }
  // 能力快照现在是对象，用 capabilityText 拼成可读文案（与卡片上的口径一致）
  const name = node.strategyVersion ?? capabilityText(node.capability) ?? '—';
  return `${stageLabel(node.stage)} · ${name}`;
});

const upstreamTone = computed(() => statusTone(props.upstream?.status));
const upstreamStatusText = computed(() => taskStatusLabel(props.upstream?.status ?? ''));

const strategyHint = computed(() =>
  pickedStrategyId.value ? '按所选策略执行' : '未指定，将使用知识库绑定的策略',
);

async function loadStrategies(): Promise<void> {
  if (!strategyRequired.value || !props.stage) {
    strategies.value = [];
    return;
  }
  strategiesLoading.value = true;
  try {
    strategies.value = await getStrategyVersions(props.stage);
  } catch {
    strategies.value = [];
  } finally {
    strategiesLoading.value = false;
  }
}

function onConfirm(): void {
  emit('confirm', pickedStrategyId.value || null);
}

function onClosed(): void {
  pickedStrategyId.value = '';
}

// 每次打开（或换环节）都重新拉策略，避免拿到过期的 ACTIVE 列表
watch(
  () => [visible.value, props.stage],
  ([open]) => {
    if (open) {
      pickedStrategyId.value = '';
      void loadStrategies();
    }
  },
);
</script>

<style scoped lang="css">
.trigger-upstream {
  padding: 12px 14px;
  border: 1px solid var(--kb-line);
  border-radius: 10px;
  background: rgb(255 255 255 / 3%);
}

.trigger-warn {
  border-color: rgb(251 191 36 / 35%);
  background: rgb(251 191 36 / 8%);
  color: var(--kb-warn);
  font-size: 12px;
}

/* 存储类型不一致的原因：与上游信息块同宽，单独一条告警条 */
.trigger-block {
  margin-top: 12px;
  padding: 10px 14px;
  border: 1px solid rgb(251 191 36 / 35%);
  border-radius: 10px;
  background: rgb(251 191 36 / 8%);
  color: var(--kb-warn);
  font-size: 12px;
}

.trigger-row {
  display: flex;
  gap: 12px;
  align-items: center;
  font-size: 13px;
}

.trigger-row + .trigger-row {
  margin-top: 8px;
}

.trigger-key {
  width: 96px;
  flex: none;
  color: var(--kb-text-3);
  font-size: 12px;
}

.trigger-val {
  display: flex;
  gap: 6px;
  align-items: center;
  color: var(--kb-text-1);
}

.trigger-dot {
  width: 6px;
  height: 6px;
  flex: none;
  border-radius: 50%;
}

.trigger-strategy {
  margin-top: 16px;
}

.trigger-select {
  width: 100%;
  margin-top: 10px;
}

.trigger-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.trigger-option-status {
  color: var(--kb-text-3);
  font-size: 11px;
}

.trigger-note {
  margin: 8px 0 0;
  color: var(--kb-text-3);
  font-size: 11px;
}

.tone-ok {
  background: var(--kb-ok);
}

.tone-partial {
  background: var(--kb-warn);
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
