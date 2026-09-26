<template>
  <div class="stage-page">
    <!-- 无自带页头：内容直接占满 -->
    <div class="stage-body">
      <!-- 左：文件列表 -->
      <section class="stage-panel stage-files">
        <div class="stage-panel-head">
          <span class="stage-panel-title">文件</span>
          <span class="stage-panel-count">{{ total }}</span>
        </div>
        <div v-if="files.length > 0" class="stage-file-list">
          <div
            v-for="file in files"
            :key="file.id"
            class="stage-file"
            :class="{ 'stage-file-on': file.id === selectedFileId }"
            @click="selectFile(file)"
          >
            <div class="stage-file-top">
              <span class="stage-file-name" :title="file.fileName">{{ file.fileName }}</span>
              <span class="stage-file-size">{{ formatSize(file.fileSize) }}</span>
            </div>
            <!-- 五环节进度条：未跑过的环节为灰色空槽 -->
            <div class="stage-file-dots">
              <span
                v-for="stage in PIPELINE_STAGES"
                :key="stage"
                class="stage-file-dot"
                :class="`tone-${stageTone(file, stage)}`"
                :title="`${stageLabel(stage)}：${stageStatusText(file, stage)}`"
              ></span>
            </div>
            <div class="stage-file-meta" :class="{ 'stage-file-meta-err': hasFailure(file) }">
              {{ fileSummary(file) }}
            </div>
          </div>
        </div>
        <div v-else-if="filesLoading" class="stage-empty">加载中…</div>
        <div v-else class="stage-empty">该知识库还没有文件</div>
        <div class="stage-pager">
          <el-pagination
            v-model:current-page="fileQuery.current"
            layout="prev, pager, next"
            :total="total"
            :page-size="fileQuery.size"
            small
            @current-change="loadFiles"
          />
        </div>
      </section>

      <!-- 右：执行链（占满剩余宽度）
           原先这里有一条面板头写「执行链 + 文件名」，与页头面包屑重复又占高度，
           已去掉：文件名与运行次数并入面包屑，画布因此多出约 42px -->
      <section class="stage-panel stage-chain-wrap">
        <div class="stage-canvas">
          <div class="stage-canvas-tools">
            <button class="stage-icon-btn" title="缩小" @click="zoomStep(-1)">−</button>
            <button class="stage-icon-btn" title="适应窗口" @click="resetZoom">⤢</button>
            <button class="stage-icon-btn" title="放大" @click="zoomStep(1)">+</button>
          </div>

          <div v-if="lineageLoading" class="stage-empty">加载中…</div>
          <div v-else-if="!selectedFileId" class="stage-empty">
            从左侧选择一个文件，查看它的处理链
          </div>
          <div v-else-if="lineage && lineage.nodes.length === 0" class="stage-empty">
            该文件还没有任何环节运行过
          </div>

          <div v-else-if="lineage" class="stage-chain" :style="{ zoom: zoomLevel }">
            <template v-for="(col, index) in stageColumns" :key="col.stage">
              <div
                v-if="index > 0"
                class="stage-arrow"
                :class="{ 'stage-arrow-dim': !col.latest }"
              ></div>
              <div class="stage-col">
                <div class="stage-col-label">
                  {{ stageLabel(col.stage) }}
                  <b class="stage-col-code">{{ STAGE_CODES[col.stage] }}</b>
                </div>

                <template v-if="col.latest">
                  <!-- 主节点：该环节最近一次运行 -->
                  <button
                    class="stage-node"
                    :class="{
                      'stage-node-sel': selectedNode?.taskId === col.latest.taskId,
                      'stage-node-fail': statusTone(col.latest.status) === 'fail',
                      'stage-node-run': statusTone(col.latest.status) === 'run',
                    }"
                    type="button"
                    @click="selectNode(col.latest)"
                  >
                    <span v-if="isStrategyHit(col.latest)" class="stage-node-hit">命中策略</span>
                    <span class="stage-node-status">
                      <i class="stage-nd" :class="`tone-${statusTone(col.latest.status)}`"></i>
                      {{ taskStatusLabel(col.latest.status) }}
                    </span>
                    <span
                      class="stage-node-strategy"
                      :class="{ 'is-em': !col.latest.strategyVersion }"
                    >
                      {{ col.latest.strategyVersion ?? col.latest.capability ?? '—' }}
                    </span>
                    <span v-if="statEntries(col.latest).length" class="stage-node-stats">
                      <span v-for="(text, i) in statEntries(col.latest)" :key="i">{{ text }}</span>
                    </span>
                    <span
                      v-if="col.latest.errorMsg"
                      class="stage-node-err"
                      :title="col.latest.errorMsg"
                    >
                      {{ col.latest.errorMsg }}
                    </span>
                    <span class="stage-node-time">{{ formatTime(col.latest.startedAt) }}</span>
                    <span v-if="col.older.length > 0" class="stage-node-more">
                      另外跑过 {{ col.older.length }} 次
                    </span>
                  </button>

                  <!-- 同环节的其他运行：堆叠展示 -->
                  <div v-if="col.older.length > 0" class="stage-stacked">
                    <button
                      v-for="node in col.older"
                      :key="node.taskId"
                      class="stage-stacked-item"
                      :class="{ 'stage-stacked-item-hit': isStrategyHit(node) }"
                      type="button"
                      @click="selectNode(node)"
                    >
                      <i class="stage-nd" :class="`tone-${statusTone(node.status)}`"></i>
                      <span class="stage-stacked-text">
                        {{ node.strategyVersion ?? node.capability ?? '—' }} ·
                        {{ taskStatusLabel(node.status) }}
                      </span>
                      <span v-if="isStrategyHit(node)" class="stage-stacked-hit">命中</span>
                    </button>
                  </div>
                </template>

                <!-- 该环节还没跑过 -->
                <div v-else class="stage-node stage-node-todo">
                  <span class="stage-node-status">
                    <i class="stage-nd tone-none"></i>
                    未运行
                  </span>
                  <span class="stage-node-strategy is-em">{{ todoHint(col.stage) }}</span>
                </div>
              </div>
            </template>
          </div>
        </div>

        <div class="stage-legend">
          <span class="stage-legend-item"><i class="stage-nd tone-ok"></i>成功</span>
          <span class="stage-legend-item"><i class="stage-nd tone-run"></i>运行中</span>
          <span class="stage-legend-item"><i class="stage-nd tone-wait"></i>排队 / 未开始</span>
          <span class="stage-legend-item"><i class="stage-nd tone-fail"></i>失败</span>
          <span class="stage-legend-hit"
            ><i class="stage-nd tone-hit"></i>金色边框 = 命中知识库策略</span
          >
          <span v-if="selectedNode" class="stage-legend-sel">
            已选：{{ stageLabel(selectedNode.stage) }}
          </span>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { getKnowledgeBaseDetail } from '@/api/knowledge-base';
import { getFileResults, getLineage, getStrategyBinding } from '@/api/pipeline';
import {
  PIPELINE_STAGES,
  STAGE_CODES,
  STRATEGY_BINDING_TYPES,
  stageLabel,
  statusTone,
  taskStatusLabel,
} from '@/types/pipeline';
import type { FileResult, Lineage, LineageNode, PipelineStage } from '@/types/pipeline';

// 环节页：左侧文件列表（含五环节进度）+ 右侧执行链
// 节点详情本期留空——点节点只做选中；详情形态待后续单独讨论
const route = useRoute();
const knowledgeBaseId = String(route.params.id);

const kbName = ref('');
const files = ref<FileResult[]>([]);
const total = ref(0);
const filesLoading = ref(false);
const fileQuery = ref({ current: 1, size: 10 });

const selectedFileId = ref<string>('');
const selectedFileName = ref('');
const lineage = ref<Lineage | null>(null);
const lineageLoading = ref(false);
const selectedNode = ref<LineageNode | null>(null);
const zoomLevel = ref(1);

/** 知识库当前绑定的策略版本（name-version 合成串集合），用于链图上的「命中策略」金标 */
const boundVersions = ref<Set<string>>(new Set());

async function loadStrategyBindings(): Promise<void> {
  const results = await Promise.all(
    STRATEGY_BINDING_TYPES.map((type) =>
      getStrategyBinding(knowledgeBaseId, type).catch(() => null),
    ),
  );
  const versions = new Set<string>();
  for (const binding of results) {
    if (binding?.strategyName && binding.strategyVersion) {
      versions.add(`${binding.strategyName}-${binding.strategyVersion}`);
    }
  }
  boundVersions.value = versions;
}

/** 链图渲染序列：按环节顺序展开，每个环节要么「有运行」（带主节点与更早运行），
 *  要么「未运行」（占位）。合成单一序列后模板只渲染一次，latest 保证非空 */
type StageColumn =
  | { stage: PipelineStage; latest: LineageNode; older: LineageNode[] }
  | { stage: PipelineStage; latest: null };

const stageColumns = computed<StageColumn[]>(() => {
  const nodes = lineage.value?.nodes ?? [];
  return PIPELINE_STAGES.map((stage) => {
    // 按开始时间排序取最近一次：后端按任务 id 升序返回，那是插入顺序而非时间顺序，
    // 重跑场景下两者会不一致（实测发现），因此这里显式按 startedAt 排
    const stageNodes = nodes
      .filter((node) => node.stage === stage)
      .slice()
      .sort((a, b) => toMillis(a.startedAt) - toMillis(b.startedAt));
    if (stageNodes.length === 0) {
      return { stage, latest: null };
    }
    return {
      stage,
      latest: stageNodes[stageNodes.length - 1],
      older: stageNodes.slice(0, -1).reverse(),
    };
  });
});

/** 时间戳转毫秒；缺失或非法按 0 处理（无时间的视为更早） */
function toMillis(value: string | null): number {
  if (!value) {
    return 0;
  }
  const time = new Date(value).getTime();
  return Number.isNaN(time) ? 0 : time;
}

async function loadKnowledgeBase(): Promise<void> {
  try {
    const detail = await getKnowledgeBaseDetail(knowledgeBaseId);
    kbName.value = detail.name;
  } catch {
    // 失败提示已由接口层统一拦截处理
  }
}

async function loadFiles(): Promise<void> {
  filesLoading.value = true;
  try {
    const page = await getFileResults(knowledgeBaseId, fileQuery.value);
    files.value = page.records;
    total.value = page.total;
    // 首次加载自动选中第一个文件，避免右侧空白
    if (!selectedFileId.value && page.records.length > 0) {
      await selectFile(page.records[0]);
    }
  } catch {
    // 失败提示已由接口层统一拦截处理
  } finally {
    filesLoading.value = false;
  }
}

async function selectFile(file: FileResult): Promise<void> {
  if (selectedFileId.value === file.id) {
    return;
  }
  selectedFileId.value = file.id;
  selectedFileName.value = file.fileName;
  selectedNode.value = null;
  await loadLineage();
}

async function loadLineage(): Promise<void> {
  if (!selectedFileId.value) {
    lineage.value = null;
    return;
  }
  lineageLoading.value = true;
  try {
    lineage.value = await getLineage(selectedFileId.value);
  } catch {
    lineage.value = null;
    // 失败提示已由接口层统一拦截处理
  } finally {
    lineageLoading.value = false;
  }
}

function selectNode(node: LineageNode): void {
  // 详情留空：只记录选中，供后续接入详情面板
  selectedNode.value = selectedNode.value?.taskId === node.taskId ? null : node;
}

function zoomStep(delta: number): void {
  const next = Number((zoomLevel.value + delta * 0.1).toFixed(2));
  zoomLevel.value = Math.min(1.6, Math.max(0.6, next));
}

function resetZoom(): void {
  zoomLevel.value = 1;
}

/** 是否命中知识库当前绑定的策略：节点上的 strategyVersion 是 name-version 合成串，
 *  而绑定返回的 name 与 version 是两个字段，拼起来比对
 *  （后端按零冗余设计不返回该标记，要求前端自行比对） */
function isStrategyHit(node: LineageNode): boolean {
  return node.strategyVersion !== null && boundVersions.value.has(node.strategyVersion);
}

/** 统计摘要（键名 + 值）：只显示值会读成「4031」这种无法理解的数字串，
 *  所以带上键名。最多 2 项，避免节点被撑宽 */
function statEntries(node: LineageNode): string[] {
  return Object.entries(node.stats ?? {})
    .slice(0, 2)
    .map(([key, value]) => `${key} ${value}`);
}

/** 文件在某个环节的状态色调 */
function stageTone(file: FileResult, stage: PipelineStage): string {
  const status = file.stageStatuses.find((item) => item.stage === stage);
  if (!status) {
    return 'none';
  }
  return statusTone(status.status);
}

function stageStatusText(file: FileResult, stage: PipelineStage): string {
  const status = file.stageStatuses.find((item) => item.stage === stage);
  if (!status) {
    return '未运行';
  }
  return taskStatusLabel(status.status ?? '');
}

function hasFailure(file: FileResult): boolean {
  return file.stageStatuses.some((item) => statusTone(item.status) === 'fail');
}

/** 文件一句话摘要：优先展示失败原因，其次展示进行中的环节，最后展示完成度 */
function fileSummary(file: FileResult): string {
  const failed = file.stageStatuses.find((item) => statusTone(item.status) === 'fail');
  if (failed) {
    const label = stageLabel(failed.stage);
    return failed.errorMsg ? `${label}失败 · ${failed.errorMsg}` : `${label}失败`;
  }
  const running = file.stageStatuses.find((item) => statusTone(item.status) === 'run');
  if (running) {
    return `${stageLabel(running.stage)}运行中`;
  }
  const done = file.stageStatuses.filter((item) => statusTone(item.status) === 'ok').length;
  if (done === 0) {
    return '尚未运行';
  }
  return `${done}/${PIPELINE_STAGES.length} 环节完成`;
}

/** 该环节未运行时给一句上下文提示 */
function todoHint(stage: PipelineStage): string {
  const index = PIPELINE_STAGES.indexOf(stage);
  if (index === 0) {
    return '尚未开始';
  }
  const prev = PIPELINE_STAGES[index - 1];
  return `等待上游「${stageLabel(prev)}」产物`;
}

function formatSize(bytes: number | null): string {
  if (bytes === null) {
    return '—';
  }
  if (bytes < 1024) {
    return `${bytes}B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(0)}K`;
  }
  return `${(bytes / 1024 / 1024).toFixed(1)}M`;
}

function formatTime(value: string | null): string {
  if (!value) {
    return '';
  }
  return value.replace('T', ' ').slice(5, 16);
}

onMounted(() => {
  void loadKnowledgeBase();
  void loadStrategyBindings();
  void loadFiles();
});
</script>

<style scoped lang="css">
.stage-page {
  display: flex;
  flex-direction: column;

  /* 无自带页头（面包屑在顶栏），body 直接占满 */
  height: calc(100vh - 128px);
  min-height: 420px;
}

.stage-body {
  display: flex;
  flex: 1;
  gap: 14px;
  min-height: 0;
}

.stage-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--kb-line);
  border-radius: var(--kb-radius);
  background: linear-gradient(180deg, rgb(255 255 255 / 3%), rgb(255 255 255 / 1.2%));
}

.stage-panel-head {
  display: flex;
  flex: none;
  gap: 9px;
  align-items: center;
  padding: 12px 15px;
  border-bottom: 1px solid var(--kb-line);
}

.stage-panel-title {
  font-size: 13px;
  font-weight: 600;
}

.stage-panel-count {
  margin-left: auto;
  color: var(--kb-text-3);
  font-size: 12px;
}

/* 左：文件列表 */
.stage-files {
  width: 262px;
  flex: none;
}

.stage-file-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 6px;
}

.stage-file {
  padding: 10px 11px;
  border: 1px solid transparent;
  border-radius: 10px;
  cursor: pointer;
}

.stage-file:hover {
  background: rgb(255 255 255 / 4%);
}

.stage-file-on {
  border-color: rgb(52 211 153 / 35%);
  background: var(--kb-tint);
}

.stage-file-top {
  display: flex;
  gap: 8px;
  align-items: center;
}

.stage-file-name {
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stage-file-size {
  flex: none;
  margin-left: auto;
  color: var(--kb-text-3);
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
}

.stage-file-dots {
  display: flex;
  gap: 4px;
  margin-top: 8px;
}

.stage-file-dot {
  width: 100%;
  height: 3px;
  border-radius: 2px;
  background: rgb(255 255 255 / 8%);
}

.stage-file-meta {
  margin-top: 7px;
  overflow: hidden;
  color: var(--kb-text-3);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stage-file-meta-err {
  color: var(--kb-danger);
}

.stage-pager {
  display: flex;
  flex: none;
  justify-content: center;
  padding: 8px 0;
  border-top: 1px solid var(--kb-line);
}

/* 右：链图 */
.stage-chain-wrap {
  flex: 1;
  min-width: 0;
}

.stage-canvas {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: auto;

  /* 顶部留白避开右上角工具按钮 */
  padding: 52px 14px 26px;
}

.stage-canvas-tools {
  position: absolute;
  top: 14px;
  right: 16px;
  z-index: 3;
  display: flex;
  gap: 6px;
}

.stage-icon-btn {
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  border: 1px solid var(--kb-line);
  border-radius: 8px;
  background: rgb(255 255 255 / 3%);
  color: var(--kb-text-2);
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
  font-size: 12px;
  cursor: pointer;
}

.stage-icon-btn:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}

.stage-chain {
  display: flex;
  align-items: flex-start;
  min-width: 0;
}

/* 列宽用弹性分配而非固定像素：容器实际宽度受侧边栏/内边距影响，
  固定宽度在窄窗口下会累计超出（实测差 19px），flex 让浏览器自己算 */
.stage-col {
  display: flex;
  flex: 1 1 0;
  flex-direction: column;
  align-items: center;
  min-width: 0;
  padding: 0 4px;
}

.stage-col-label {
  margin-bottom: 10px;
  color: var(--kb-text-3);
  font-size: 11px;
  line-height: 1.5;
  text-align: center;
}

.stage-col-code {
  display: block;
  margin-top: 2px;
  color: var(--kb-line-strong);
  font-size: 10px;
  font-weight: 400;
}

.stage-arrow {
  position: relative;
  flex: none;
  width: 20px;
  height: 24px;
  padding-top: 44px;
}

.stage-arrow::before {
  position: absolute;
  top: 50%;
  right: 9px;
  left: 0;
  height: 1px;
  background: var(--kb-line-strong);
  content: '';
}

.stage-arrow::after {
  position: absolute;
  top: calc(50% - 4px);
  right: 5px;
  border-top: 4px solid transparent;
  border-bottom: 4px solid transparent;
  border-left: 6px solid var(--kb-line-strong);
  content: '';
}

.stage-arrow-dim {
  opacity: 0.35;
}

.stage-node {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  min-width: 0;
  padding: 10px 11px;
  border: 1px solid var(--kb-line-strong);
  border-radius: 11px;
  background: var(--kb-bg-2);
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
  text-align: left;
  cursor: pointer;
}

.stage-node:hover {
  border-color: rgb(52 211 153 / 45%);
}

.stage-node-sel {
  border-color: var(--kb-primary);
  background: var(--kb-tint);
  box-shadow: 0 0 0 2px var(--kb-tint);
}

.stage-node-sel::before {
  position: absolute;
  top: -1px;
  bottom: -1px;
  left: -1px;
  width: 3px;
  border-radius: 3px 0 0 3px;
  background: var(--kb-primary);
  content: '';
}

.stage-node-fail {
  border-left: 3px solid var(--kb-danger);
}

.stage-node-run {
  border-left: 3px solid var(--kb-primary);
}

.stage-node-todo {
  border-style: dashed;
  opacity: 0.6;
  cursor: default;
}

.stage-node-hit {
  position: absolute;
  top: -9px;
  right: 8px;
  padding: 1px 7px;
  border: 1px solid rgb(251 191 36 / 35%);
  border-radius: 99px;
  background: rgb(251 191 36 / 16%);
  color: var(--kb-warn);
  font-size: 10px;
}

.stage-node-status {
  display: flex;
  gap: 6px;
  align-items: center;
  color: var(--kb-text-2);
  font-size: 12px;
}

.stage-nd {
  width: 6px;
  height: 6px;
  flex: none;
  border-radius: 50%;
}

.stage-node-strategy {
  margin-top: 7px;
  overflow: hidden;
  color: var(--kb-text-2);
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stage-node-strategy.is-em {
  color: var(--kb-text-3);
  font-style: italic;
}

.stage-node-stats {
  display: flex;
  gap: 10px;
  margin-top: 7px;
  color: var(--kb-text-3);
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
}

.stage-node-err {
  margin-top: 6px;
  overflow: hidden;
  color: var(--kb-danger);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stage-node-time {
  margin-top: 6px;
  color: var(--kb-text-3);
  font-size: 10px;
}

.stage-node-more {
  margin-top: 7px;
  padding-top: 7px;
  border-top: 1px dashed var(--kb-line);
  color: var(--kb-text-3);
  font-size: 10px;
}

.stage-stacked {
  width: 100%;
  min-width: 0;
  margin-top: 6px;
  padding: 7px 9px;
  border: 1px dashed var(--kb-line-strong);
  border-radius: 9px;
  background: rgb(0 0 0 / 18%);
}

.stage-stacked-item {
  display: flex;
  gap: 6px;
  align-items: center;
  width: 100%;
  border: none;
  background: none;
  color: var(--kb-text-2);
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
  font-size: 10px;
  text-align: left;
  cursor: pointer;
}

.stage-stacked-item + .stage-stacked-item {
  margin-top: 5px;
}

.stage-stacked-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 折叠起来的运行若命中知识库策略，同样要能看出来 */
.stage-stacked-item-hit .stage-stacked-text {
  color: var(--kb-warn);
}

.stage-stacked-item:hover .stage-stacked-text {
  color: var(--kb-primary);
}

.stage-stacked-hit {
  flex: none;
  margin-left: auto;
  padding: 0 5px;
  border: 1px solid rgb(251 191 36 / 35%);
  border-radius: 99px;
  background: rgb(251 191 36 / 16%);
  color: var(--kb-warn);
  font-size: 9px;
}

/* 状态色调 */
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

.tone-none {
  background: rgb(255 255 255 / 22%);
}

.tone-hit {
  background: var(--kb-warn);
}

.stage-legend {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  padding: 10px 15px;
  border-top: 1px solid var(--kb-line);
  color: var(--kb-text-3);
  font-size: 11px;
}

.stage-legend-item {
  display: flex;
  gap: 6px;
  align-items: center;
}

.stage-legend-hit {
  color: var(--kb-warn);
}

.stage-legend-sel {
  margin-left: auto;
  color: var(--kb-primary);
}

.stage-empty {
  flex: 1;
  padding: 60px 0;
  color: var(--kb-text-3);
  font-size: 13px;
  text-align: center;
}
</style>
