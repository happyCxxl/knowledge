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
            :data-file-id="file.id"
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

      <!-- 右：执行链（占满剩余宽度）。链图是一棵树：同一环节可以按不同策略反复分叉 -->
      <section class="stage-panel stage-chain-wrap">
        <div class="stage-canvas">
          <!-- 加载态只在「还没有任何图」时占位。
               若已有图就保留它、就地替换数据，否则切文件时图会整个卸载重建，
               表现为「闪一下 + 位置全变」（实测过） -->
          <div v-if="lineageLoading && !hasNodes" class="stage-empty">加载中…</div>
          <div v-else-if="!selectedFileId" class="stage-empty">
            从左侧选择一个文件，查看它的处理链
          </div>
          <ChainGraph
            v-if="hasNodes"
            :lineage="lineage"
            :file-key="selectedFileId"
            :position-store="nodePositions"
            :bound-versions="boundVersions"
            :class="{ 'is-refreshing': lineageLoading }"
            @select="onPathSelect"
          />

          <!-- 还没有任何运行：解析是链路起点、无上游产物，不需要「先选节点」，
               这里直接给入口，否则用户的整条链路第一步就卡住 -->
          <div v-else-if="lineage && !lineageLoading" class="stage-start">
            <span class="stage-start-title">该文件还没有任何环节运行过</span>
            <span class="stage-start-hint">从解析开始——它不需要上游产物</span>
            <el-button
              class="stage-trigger-btn"
              type="primary"
              :loading="triggering"
              @click="onTriggerParse"
            >
              触发解析
            </el-button>
          </div>
        </div>

        <div class="stage-legend">
          <span class="stage-legend-item"><i class="stage-nd tone-ok"></i>成功</span>
          <span class="stage-legend-item"><i class="stage-nd tone-run"></i>运行中</span>
          <span class="stage-legend-item"><i class="stage-nd tone-wait"></i>排队 / 未开始</span>
          <span class="stage-legend-item"><i class="stage-nd tone-fail"></i>失败</span>
          <span class="stage-legend-hit"
            ><i class="stage-nd tone-hit"></i>金色标记 = 命中知识库策略</span
          >
          <div class="stage-legend-ops">
            <span v-if="pathEndNode" class="stage-legend-sel">
              已选末端：{{ stageLabel(pathEndNode.stage) }}
            </span>
            <el-button
              class="stage-trigger-btn"
              type="primary"
              :disabled="!canTrigger"
              @click="openTrigger"
            >
              {{ triggerLabel }}
            </el-button>
          </div>
        </div>
      </section>
    </div>

    <TriggerStageDialog
      v-model="triggerVisible"
      :stage="nextStage"
      :upstream="pathEndNode"
      :submitting="triggering"
      @confirm="onTriggerConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { getKnowledgeBaseDetail } from '@/api/knowledge-base';
import { getFileResults, getLineage, getStrategyBinding, addStageTrigger } from '@/api/pipeline';
import ChainGraph from '@/components/pipeline/ChainGraph.vue';
import TriggerStageDialog from '@/components/pipeline/TriggerStageDialog.vue';
import { prunePositions, readPositions } from '@/utils/chain-layout-storage';
import {
  PIPELINE_STAGES,
  STRATEGY_BINDING_TYPES,
  isTaskPending,
  stageLabel,
  stageStatusList,
  statusTone,
  taskStatusLabel,
} from '@/types/pipeline';
import type {
  FileResult,
  Lineage,
  LineageNode,
  NodePosition,
  NodePositionStore,
  PipelineStage,
} from '@/types/pipeline';

// 环节页：左侧文件列表（含五环节进度）+ 右侧执行链。
// 链图是一棵树：同一环节可按不同策略反复分叉，分叉点由上游节点的 productId 指定
const route = useRoute();
const knowledgeBaseId = String(route.params.id);

const kbName = ref('');
const files = ref<FileResult[]>([]);
const total = ref(0);
const filesLoading = ref(false);
const fileQuery = ref({ current: 1, size: 10 });

const selectedFileId = ref<string>('');
const lineage = ref<Lineage | null>(null);
const lineageLoading = ref(false);

/** 选中路径的末端节点：从这里触发下游，其 productId 即分叉点 */
const pathEndNode = ref<LineageNode | null>(null);

/**
 * 各文件的节点坐标记忆：由页面持有，切文件再切回来能恢复用户摆好的布局。
 *
 * <p>放在页面而不是图组件里：切文件时 `selectedFileId` 立即变、`lineage` 要等接口返回，
 * 这个窗口期里图组件无法判断手上的坐标属于哪个文件；按文件 ID 存放就没有归属歧义。
 */
const nodePositions: NodePositionStore = new Map();
/** 执行树里是否已有运行节点：为空时要给「触发解析」入口 */
const hasNodes = computed(() => (lineage.value?.nodes ?? []).length > 0);

const triggerVisible = ref(false);
const triggering = ref(false);

/** 知识库当前绑定的策略版本（name-version 合成串集合），用于链图上的「命中策略」金标 */
const boundVersions = ref<Set<string>>(new Set());

/** 轮询句柄：触发后要等任务跑完，定时刷新执行树直到进入终态 */
let pollTimer: number | null = null;

/** 下一个待触发环节：选中路径末端之后的那个环节；已到末端则为 null */
const nextStage = computed<PipelineStage | null>(() => {
  const current = pathEndNode.value?.stage;
  if (!current) {
    return null;
  }
  const index = PIPELINE_STAGES.indexOf(current as PipelineStage);
  if (index < 0 || index >= PIPELINE_STAGES.length - 1) {
    return null;
  }
  return PIPELINE_STAGES[index + 1];
});

/**
 * 能否从当前末端触发下游。
 *
 * <p>必须同时满足两点：
 * 1. 还有下一个环节（切片之后没有向量化之外的环节，向量化是末端）；
 * 2. 末端节点**产出了产物**——分叉点就是产物，失败的运行没有产物，
 *    此时若仍允许触发，后端会退化成「按绑定的最新成功产物」解析，
 *    用户以为自己指定了分叉点其实没有，属于静默走样，故直接禁用。
 */
const canTrigger = computed(
  () => nextStage.value !== null && Boolean(pathEndNode.value?.productId),
);

/** 触发按钮文案：说清当前状态而不是只显示一个不能点的按钮 */
const triggerLabel = computed(() => {
  if (!hasNodes.value) {
    return '尚未开始';
  }
  if (!pathEndNode.value) {
    return '先在图上选择起点';
  }
  if (!nextStage.value) {
    return '已是最后一个环节';
  }
  if (!pathEndNode.value.productId) {
    return '该运行无产物，无法分叉';
  }
  return `触发${stageLabel(nextStage.value)}`;
});

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
  stopPolling();
  selectedFileId.value = file.id;
  pathEndNode.value = null;
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
    syncPositionsWithLineage(selectedFileId.value, lineage.value);
  } catch {
    lineage.value = null;
    // 失败提示已由接口层统一拦截处理
  } finally {
    lineageLoading.value = false;
  }
}

/** 各文件上一次同步坐标时的节点集合签名：避免每次轮询都白扫一遍 localStorage */
const syncedNodeSignatures = new Map<string, string>();

/**
 * 把本地记住的节点坐标与刚取到的血缘对齐。
 *
 * <p>两件事：
 * <ol>
 *   <li>**恢复**：把该文件上次存下的坐标装进内存表，供链图渲染时优先使用；</li>
 *   <li>**清理**：任务 ID 是一次性的，重新触发会产生新 ID、旧记录永远匹配不上。
 *       血缘里已不存在的任务 ID，其坐标记录直接删掉，避免死数据越积越多。</li>
 * </ol>
 *
 * <p>只在节点集合真的变化时才做，避免每 2 秒的轮询反复扫 localStorage。
 */
function syncPositionsWithLineage(fileKey: string, data: Lineage | null): void {
  if (!data) {
    return;
  }
  const signature = data.nodes.map((node) => node.taskId).join(',');
  if (syncedNodeSignatures.get(fileKey) === signature) {
    return;
  }
  syncedNodeSignatures.set(fileKey, signature);

  prunePositions(fileKey, new Set(data.nodes.map((node) => node.taskId)));

  let map = nodePositions.get(fileKey);
  if (!map) {
    map = new Map<string, NodePosition>();
    nodePositions.set(fileKey, map);
  }
  // 本地记录只用作「没算过的新节点的初始值」，已有的内存值优先（可能刚被拖动过）
  for (const [taskId, pos] of readPositions(fileKey)) {
    if (!map.has(taskId)) {
      map.set(taskId, pos);
    }
  }
}

/** 链图选中路径变化：末端节点决定「下一个可触发的环节」 */
function onPathSelect(node: LineageNode | null): void {
  pathEndNode.value = node;
}

function openTrigger(): void {
  if (nextStage.value) {
    triggerVisible.value = true;
  }
}

/**
 * 触发解析：链路的第一个环节。
 *
 * <p>解析没有上游产物，所以不需要「先选路径末端」——直接调接口即可。
 * 这是全新文件唯一的入口，缺了它整条链路无法从零启动。
 */
async function onTriggerParse(): Promise<void> {
  if (!selectedFileId.value) {
    return;
  }
  triggering.value = true;
  try {
    await addStageTrigger(selectedFileId.value, 'PARSE', {});
    startPolling();
  } catch {
    // 失败提示已由接口层统一拦截处理
  } finally {
    triggering.value = false;
  }
}

/**
 * 确认触发：以选中末端的产物为分叉点，调用该环节的触发接口。
 *
 * <p>接口是异步的（只返回任务 ID），所以触发后开始轮询执行树，直到新任务进入终态。
 * 上游产物 ID 缺失时不传该参数，退回后端默认解析（知识库绑定策略 + 同策略最新成功运行）。
 */
async function onTriggerConfirm(strategyVersionId: string | null): Promise<void> {
  const stage = nextStage.value;
  const upstream = pathEndNode.value;
  if (!stage || !upstream) {
    return;
  }
  triggering.value = true;
  try {
    await addStageTrigger(selectedFileId.value, stage, {
      strategyVersionId,
      upstreamProductId: upstream.productId,
    });
    triggerVisible.value = false;
    startPolling();
  } catch {
    // 失败提示已由接口层统一拦截处理
  } finally {
    triggering.value = false;
  }
}

/**
 * 轮询执行树：任务在跑时节点状态会变，跑完自动停。
 *
 * <p>停止条件用「是否还有未进入终态的任务」（类型层的 isTaskPending）：
 * 任务可能长期处于 QUEUED，也可能直接进入 PARTIAL_SUCCESS / FAILED，
 * 只盯 RUNNING 会漏判——实测踩过：PARTIAL_SUCCESS 被漏判，轮询永不停止。
 *
 * <p>未知状态按未完成处理，最多轮询 MAX_TICKS 次后放弃，避免无限打接口。
 */
function startPolling(): void {
  stopPolling();
  let ticks = 0;
  const MAX_TICKS = 150;
  pollTimer = window.setInterval(() => {
    ticks += 1;
    if (ticks > MAX_TICKS) {
      stopPolling();
      return;
    }
    void loadLineage().then(() => {
      if (!hasPendingTask()) {
        stopPolling();
      }
    });
  }, 2000);
}

/** 是否还有未进入终态的环节任务 */
function hasPendingTask(): boolean {
  return (lineage.value?.nodes ?? []).some((node) => isTaskPending(node.status));
}

function stopPolling(): void {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer);
    pollTimer = null;
  }
}

/** 文件在某个环节的状态色调 */
function stageTone(file: FileResult, stage: PipelineStage): string {
  const status = stageStatusList(file).find((item) => item.stage === stage);
  if (!status) {
    return 'none';
  }
  return statusTone(status.status);
}

function stageStatusText(file: FileResult, stage: PipelineStage): string {
  const status = stageStatusList(file).find((item) => item.stage === stage);
  if (!status) {
    return '未运行';
  }
  return taskStatusLabel(status.status ?? '');
}

function hasFailure(file: FileResult): boolean {
  return stageStatusList(file).some((item) => statusTone(item.status) === 'fail');
}

/** 文件一句话摘要：优先展示失败原因，其次展示进行中的环节，最后展示完成度 */
function fileSummary(file: FileResult): string {
  const statuses = stageStatusList(file);
  const failed = statuses.find((item) => statusTone(item.status) === 'fail');
  if (failed) {
    const label = stageLabel(failed.stage);
    return failed.errorMsg ? `${label}失败 · ${failed.errorMsg}` : `${label}失败`;
  }
  const running = statuses.find((item) => statusTone(item.status) === 'run');
  if (running) {
    return `${stageLabel(running.stage)}运行中`;
  }
  const done = statuses.filter((item) => statusTone(item.status) === 'ok').length;
  if (done === 0) {
    return '尚未运行';
  }
  return `${done}/${PIPELINE_STAGES.length} 环节完成`;
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

onMounted(() => {
  void loadKnowledgeBase();
  void loadStrategyBindings();
  void loadFiles();
});

// 离开页面要停掉轮询，否则定时器会继续打接口
onUnmounted(() => {
  stopPolling();
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
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;

  /* 画布自身不再滚动/留白：平移缩放交给 Vue Flow，工具按钮也在它内部右上角 */
  padding: 0 0 8px;
}

/* 刷新中：轻微降透明度而不是卸载重建，切文件时不会"闪一下" */
.stage-canvas > .is-refreshing {
  opacity: 0.55;
  transition: opacity 0.15s;
}

/* 状态色点：图例仍在使用（节点的状态色在 ChainNode 组件内） */
.stage-nd {
  width: 6px;
  height: 6px;
  flex: none;
  border-radius: 50%;
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

/* 图例右侧：当前选中末端的提示 + 触发下一环节的按钮。
   图例是 flex-wrap，用 margin-left: auto 直接把这一组推到行尾 */
.stage-legend-ops {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-left: auto;
}

.stage-legend-sel {
  color: var(--kb-primary);
}

.stage-trigger-btn {
  border: none;
  background: linear-gradient(135deg, var(--kb-primary), var(--kb-primary-2));
  color: var(--kb-btn-text);
  font-size: 12px;
  font-weight: 650;
}

/* 全新文件：链图为空，居中给出「触发解析」入口（解析无上游，是全链路唯一不需要选路径的环节） */
.stage-start {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  align-items: center;
  justify-content: center;
}

.stage-start-title {
  color: var(--kb-text-2);
  font-size: 13px;
}

.stage-start-hint {
  margin-bottom: 10px;
  color: var(--kb-text-3);
  font-size: 12px;
}

.stage-empty {
  flex: 1;
  padding: 60px 0;
  color: var(--kb-text-3);
  font-size: 13px;
  text-align: center;
}
</style>
