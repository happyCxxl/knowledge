<template>
  <Teleport to="body">
    <div v-if="visible" class="parse-drawer-mask" @click="onClose">
      <aside class="parse-drawer" role="dialog" aria-modal="true" @click.stop>
        <!-- 头部：文件 + 本次运行标识 + 状态徽标 -->
        <header class="parse-drawer-head">
          <div class="parse-drawer-title">
            <span class="parse-drawer-file">{{ fileName }}</span>
            <span class="parse-drawer-badge" :class="badgeClass">{{ statusText }}</span>
          </div>
          <button class="parse-drawer-x" type="button" @click="onClose">关闭</button>
        </header>
        <div class="parse-drawer-ident">
          <span v-if="runOrdinal > 1" class="parse-drawer-ident-item"
            >第 <span class="parse-drawer-ident-num">{{ runOrdinal }}</span> 次执行</span
          >
          <span class="parse-drawer-ident-item">开始 {{ formatClock(detail?.startedAt) }}</span>
          <span class="parse-drawer-ident-item">耗时 {{ formatDuration(stats?.durationMs) }}</span>
          <span class="parse-drawer-ident-item">任务 {{ taskId }}</span>
        </div>

        <!-- 统计条：固定，一行摘要 + 一行统计 -->
        <div class="parse-drawer-statbar">
          <span class="parse-drawer-conclusion" :class="conclusionClass">{{ conclusionText }}</span>
          <div class="parse-drawer-stats">
            <span v-for="item in statItems" :key="item.key" class="parse-drawer-stat">
              <span class="parse-drawer-stat-value">{{ item.value }}</span>
              {{ item.label }}
            </span>
          </div>
        </div>

        <!-- 失败态：固定，解析失败的运行一眼可见 -->
        <section
          v-if="isFailed"
          class="parse-drawer-block parse-drawer-block-bad parse-drawer-failbar"
        >
          <span class="parse-drawer-error-code">{{ detail?.errorCode ?? '—' }}</span>
          <span class="parse-drawer-error-msg">{{
            detail?.errorMsg ?? '本次运行没有留下失败说明'
          }}</span>
          <span class="parse-drawer-advice">建议：{{ failAdvice }}</span>
        </section>

        <div ref="splitBodyRef" class="parse-drawer-body" :class="{ 'is-dragging': dragging }">
          <!-- 左栏：原文预览。工具条固定在本区顶，只有画布滚动；宽度由分隔条决定 -->
          <section class="parse-drawer-pane parse-drawer-pane-source" :style="sourcePaneStyle">
            <div class="parse-drawer-pane-head">
              <h4 class="parse-drawer-block-title">原文预览</h4>
              <span class="parse-drawer-hint">
                {{ fileKindText }}<span v-if="fileSizeText"> · {{ fileSizeText }}</span>
                <span v-if="previewTotal > 0"> · 共 {{ previewTotal }} 页</span>
                <span v-if="canPreview && highlightCount > 0">
                  · 可定位 {{ highlightCount }} 个元素</span
                >
              </span>
            </div>
            <div class="parse-drawer-pane-fill">
              <p v-if="sourceError" class="parse-drawer-hint parse-drawer-hint-bad">
                {{ sourceError }}
              </p>
              <p v-else-if="!sourceBlob && previewKind !== 'none'" class="parse-drawer-hint">
                原文件加载中…
              </p>
              <!-- 没有浏览器端渲染器的类型（.doc/.xls/图片等）：只给文件信息与下载 -->
              <p v-else-if="previewKind === 'none'" class="parse-drawer-hint">
                该类型暂不支持内嵌预览（{{ fileKindText }}），可用底部「下载原文件」查看
              </p>
              <!-- 拿到字节后一律交给对应预览组件：解析失败由它自己报原因（不在这里摘掉组件，
                   否则页码与错误明细都会跟着消失） -->
              <PdfSourcePreview
                v-else-if="previewKind === 'pdf'"
                ref="previewRef"
                :blob="sourceBlob"
                :highlights="highlights"
                :active-key="activeKey"
                @page-change="onPreviewPage"
                @pick="onPreviewPick"
                @loaded="onPreviewLoaded"
              />
              <DocxSourcePreview
                v-else-if="previewKind === 'docx'"
                ref="docxRef"
                :blob="sourceBlob"
                :anchors="docxAnchors"
                :active-key="activeKey"
                @pick="onPreviewPick"
                @loaded="onOfficeLoaded"
              />
              <XlsxSourcePreview
                v-else
                ref="xlsxRef"
                :blob="sourceBlob"
                :anchors="xlsxAnchors"
                :active-key="activeKey"
                @pick="onPreviewPick"
                @loaded="onOfficeLoaded"
              />
            </div>
          </section>

          <!-- 分隔条：拖动调两栏宽度；方向键微调，双击回默认比例 -->
          <div
            class="parse-drawer-splitter"
            :class="{ 'is-dragging': dragging }"
            role="separator"
            aria-orientation="vertical"
            aria-label="调整原文预览与解析结果宽度"
            :aria-valuenow="Math.round(splitRatio * 100)"
            aria-valuemin="20"
            aria-valuemax="90"
            tabindex="0"
            :title="splitterTitle"
            @pointerdown="onSplitterDown"
            @pointermove="onSplitterMove"
            @pointerup="onSplitterUp"
            @pointercancel="onSplitterUp"
            @dblclick="resetSplit"
            @keydown="onSplitterKeydown"
          ></div>

          <!-- 右栏：解析结果。页签固定在本区顶，页签内容各自滚动 -->
          <div ref="resultPaneRef" class="parse-drawer-pane parse-drawer-pane-result">
            <div class="parse-drawer-tabs" role="tablist">
              <button
                v-for="tab in RESULT_TABS"
                :key="tab.key"
                class="parse-drawer-tab"
                :class="{ 'is-on': activeTab === tab.key }"
                type="button"
                role="tab"
                :aria-selected="activeTab === tab.key"
                @click="activeTab = tab.key"
              >
                {{ tab.label }}
                <span v-if="tabCount(tab.key) > 0" class="parse-drawer-tab-num">{{
                  tabCount(tab.key)
                }}</span>
              </button>
            </div>

            <p v-if="detailLoading" class="parse-drawer-hint">加载中…</p>
            <p v-else-if="detailError" class="parse-drawer-hint parse-drawer-hint-bad">
              {{ detailError }}
            </p>

            <!-- 页签：元素 -->
            <div v-else-if="activeTab === 'elements'" class="parse-drawer-tabpane">
              <div class="parse-drawer-tabhead">
                <input
                  v-model="keyword"
                  class="parse-drawer-search"
                  type="search"
                  placeholder="按文本 / 类型过滤"
                />
                <label class="parse-drawer-check">
                  <input v-model="followPreview" type="checkbox" />
                  只列预览当前页
                </label>
                <span v-if="keyword.trim()" class="parse-drawer-hint">只过滤当前页</span>
                <span v-if="itemsLoading" class="parse-drawer-hint">加载中…</span>
                <span v-else-if="itemsError" class="parse-drawer-hint parse-drawer-hint-bad">
                  {{ itemsError }}
                </span>
                <span v-else-if="pageMatched !== null" class="parse-drawer-hint"
                  >本页命中 {{ pageMatched }}</span
                >
              </div>

              <div class="parse-drawer-tabscroll">
                <p v-if="!itemsLoading && elementTotal === 0" class="parse-drawer-hint">
                  {{ followPreview && previewReady ? '该页没有解析元素' : '本次运行没有产物内容' }}
                </p>
                <p v-else-if="visibleItems.length === 0" class="parse-drawer-hint">
                  没有匹配的元素
                </p>

                <ul v-else class="parse-drawer-elements">
                  <li
                    v-for="item in visibleItems"
                    :key="item.key"
                    class="parse-drawer-element"
                    :class="{
                      'is-active': item.key === activeKey,
                      'is-picked': item.key === pickedKey,
                    }"
                    :data-key="item.key"
                    @click="onElementPick(item.key)"
                  >
                    <div class="parse-drawer-element-head">
                      <span class="parse-drawer-type">{{ item.typeText }}</span>
                      <span class="parse-drawer-element-meta">#{{ item.seq }}</span>
                      <span v-if="item.page !== null" class="parse-drawer-element-meta"
                        >第 {{ item.page }} 页</span
                      >
                      <span v-if="item.source" class="parse-drawer-element-meta">{{
                        item.source
                      }}</span>
                      <span v-if="item.grid" class="parse-drawer-element-meta">{{
                        item.grid
                      }}</span>
                      <button
                        v-if="item.text"
                        class="parse-drawer-toggle"
                        type="button"
                        @click.stop="toggleExpand(item.key)"
                      >
                        {{ expanded.has(item.key) ? '收起' : '展开' }}
                      </button>
                    </div>
                    <pre
                      v-if="item.text"
                      class="parse-drawer-element-text"
                      :class="{ 'is-expanded': expanded.has(item.key) }"
                      :title="item.text"
                      >{{ item.text }}</pre>
                    <span v-else class="parse-drawer-element-meta">（无文本）</span>
                  </li>
                </ul>
              </div>

              <div class="parse-drawer-tabfoot">
                <button
                  class="parse-drawer-page-btn"
                  type="button"
                  :disabled="page <= 1 || itemsLoading"
                  @click="goPage(page - 1)"
                >
                  上一页
                </button>
                <span class="parse-drawer-page-info">第 {{ page }} / {{ pageCount }} 页</span>
                <button
                  class="parse-drawer-page-btn"
                  type="button"
                  :disabled="page >= pageCount || itemsLoading"
                  @click="goPage(page + 1)"
                >
                  下一页
                </button>
                <span class="parse-drawer-page-info"
                  >共 {{ elementTotal }} 条 · 每页 {{ PAGE_SIZE }}</span
                >
              </div>
            </div>

            <!-- 页签：告警 -->
            <div v-else-if="activeTab === 'warnings'" class="parse-drawer-tabpane">
              <div class="parse-drawer-tabscroll">
                <p v-if="warnings.length === 0" class="parse-drawer-hint">本次运行没有告警</p>
                <ul v-else class="parse-drawer-warnings">
                  <li v-for="(warn, index) in warnings" :key="index" class="parse-drawer-warning">
                    <template v-for="(part, partIndex) in warningParts(warn)" :key="partIndex">
                      <button
                        v-if="part.page"
                        class="parse-drawer-page-link"
                        type="button"
                        @click="goDocPage(part.page)"
                      >
                        {{ part.text }}
                      </button>
                      <span v-else>{{ part.text }}</span>
                    </template>
                  </li>
                </ul>
              </div>
            </div>

            <!-- 页签：过程（子步骤） -->
            <div v-else class="parse-drawer-tabpane">
              <div class="parse-drawer-tabscroll">
                <p v-if="steps.length === 0" class="parse-drawer-hint">本次运行没有子步骤记录</p>
                <ul v-else class="parse-drawer-steps">
                  <li
                    v-for="step in steps"
                    :key="step.stepName"
                    class="parse-drawer-step"
                    :class="{ 'is-bad': step.status === 'FAILED' }"
                  >
                    <span class="parse-drawer-step-name">{{ step.stepName }}</span>
                    <span class="parse-drawer-step-meta">{{ step.status ?? '—' }}</span>
                    <span class="parse-drawer-step-meta">{{ formatDuration(step.duration) }}</span>
                    <span class="parse-drawer-step-meta">告警 {{ step.warningCount ?? 0 }}</span>
                    <span v-if="step.error" class="parse-drawer-step-error">{{ step.error }}</span>
                  </li>
                </ul>
              </div>
            </div>
          </div>
        </div>

        <!-- 底部操作 -->
        <footer class="parse-drawer-foot">
          <button
            class="parse-drawer-btn"
            type="button"
            :disabled="!canDownload || downloading"
            :title="downloadReason"
            @click="downloadSource"
          >
            {{ downloading ? '下载中…' : '下载原文件' }}
          </button>
          <button class="parse-drawer-btn parse-drawer-btn-primary" type="button" @click="copyAll">
            复制全部文本
          </button>
          <span v-if="copyHint" class="parse-drawer-hint">{{ copyHint }}</span>
        </footer>
      </aside>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { readSplitRatio, removeSplitRatio, writeSplitRatio } from '@/utils/drawer-split-storage';
import { getParseDetail, getSourceFile, getStageContent } from '@/api/pipeline';
import PdfSourcePreview from '@/components/pipeline/PdfSourcePreview.vue';
import DocxSourcePreview from '@/components/pipeline/DocxSourcePreview.vue';
import XlsxSourcePreview from '@/components/pipeline/XlsxSourcePreview.vue';
import {
  bboxOf,
  elementTypeLabel,
  formatCount,
  formatDuration,
  pageOf,
  statNumber,
  statusTone,
  taskStatusLabel,
} from '@/types/pipeline';
import type { DocxAnchor } from '@/components/pipeline/DocxSourcePreview.vue';
import type { XlsxAnchor } from '@/components/pipeline/XlsxSourcePreview.vue';
import type { PreviewHighlight } from '@/components/pipeline/PdfSourcePreview.vue';
import type { LineageParseStats, ParseDetail, StageContentItem } from '@/types/pipeline';

// 解析详情抽屉：只看不改 —— 不发任何写请求，也不改链图的选中与轮询。
const props = defineProps<{
  visible: boolean;
  /** 当前文件结果 */
  fileResultId: string;
  /** 被查看的那次运行（必填：看的是"这一张卡"，不是"最新一次"） */
  taskId: string;
  /** 文件名（头部展示与下载保存名；由页面从文件列表带进来） */
  fileName: string;
  /** 源文件引用（原文件下载用：文件结果里的 fileId；缺失时下载按钮置灰） */
  sourceFileId: string;
  /** 本次运行是同一文件的第几次解析（页面按血缘节点顺序算出） */
  runOrdinal: number;
}>();

const emit = defineEmits<{ close: [] }>();

/** 每页条数：与后端上限（1000）留出余量，单次响应可控 */
const PAGE_SIZE = 100;

const detail = ref<ParseDetail | null>(null);
const detailLoading = ref(false);
const detailError = ref('');

const rawItems = ref<StageContentItem[]>([]);
const elementTotal = ref(0);
const page = ref(1);
const itemsLoading = ref(false);
const itemsError = ref('');

const keyword = ref('');
const expanded = ref(new Set<string>());

// ---- 原文预览与双栏联动 ----

/** 原文件字节（预览用）与加载态；与解析详情各自独立取数 */
const sourceBlob = ref<Blob | null>(null);
const sourceError = ref('');
/** 预览组件的联合引用：按当前类型取到的那一个会被赋值 */
const previewRef = ref<InstanceType<typeof PdfSourcePreview> | null>(null);
const docxRef = ref<InstanceType<typeof DocxSourcePreview> | null>(null);
const xlsxRef = ref<InstanceType<typeof XlsxSourcePreview> | null>(null);
/** 右栏滚动容器（点原文后把命中的元素滚到可见） */
const resultPaneRef = ref<HTMLElement | null>(null);
/** 双栏容器（分隔条按它的宽度换算左栏像素宽与最小宽度约束） */
const splitBodyRef = ref<HTMLElement | null>(null);

// ---- 分隔条：两栏宽度 ----

/** 左栏最小宽（再窄原文读不了） */
const SPLIT_MIN_LEFT = 320;
/** 右栏最小宽（再窄元素列表不可用） */
const SPLIT_MIN_RIGHT = 360;
/** 方向键每次调整的像素 */
const SPLIT_KEY_STEP = 24;
/** 默认左栏比例（双击恢复到这个值），与样式里的初始宽度同口径 */
const SPLIT_DEFAULT_RATIO = 0.62;

/**
 * 左栏宽度（px）。**null 表示还没量过容器**，此时不写内联宽度，
 * 让样式里的默认值（62% / min-width 420px）生效，避免测量前闪一下。
 */
const sourceWidth = ref<number | null>(null);
const dragging = ref(false);
/** 拖动起点：指针 x 与当时的左栏宽 */
const dragFrom = ref({ pointerX: 0, width: 0 });

/** 双栏容器当前宽度；量不到时按 0 处理（约束函数会退回最小宽） */
function bodyWidth(): number {
  return splitBodyRef.value?.clientWidth ?? 0;
}

/**
 * 把想要的左栏宽夹进合法区间。
 *
 * <p>两侧最小宽都保证：容器太窄放不下两栏最小宽时，优先保左栏最小宽。
 */
function clampWidth(width: number): number {
  const total = bodyWidth();
  const maxLeft = Math.max(total - SPLIT_MIN_RIGHT, SPLIT_MIN_LEFT);
  return Math.min(Math.max(Math.round(width), SPLIT_MIN_LEFT), maxLeft);
}

/** 左栏占双栏的比例：量不到容器宽度时按默认比例 */
const splitRatio = computed(() => {
  const total = bodyWidth();
  if (total <= 0 || sourceWidth.value === null) {
    return SPLIT_DEFAULT_RATIO;
  }
  return sourceWidth.value / total;
});

const sourcePaneStyle = computed(() =>
  sourceWidth.value === null
    ? undefined
    : { width: `${sourceWidth.value}px`, minWidth: `${SPLIT_MIN_LEFT}px` },
);

const splitterTitle = computed(
  () =>
    `拖动调整两栏宽度；双击恢复默认（左栏 ${Math.round(SPLIT_DEFAULT_RATIO * 100)}%）；←/→ 微调`,
);

/** 按像素设置左栏宽（夹住后写回，并落到偏好里） */
function applyWidth(width: number, persist: boolean): void {
  const next = clampWidth(width);
  sourceWidth.value = next;
  if (persist) {
    writeSplitRatio(props.fileResultId, props.taskId, next / Math.max(bodyWidth(), 1));
  }
}

/** 按默认比例设置左栏宽 */
function applyDefaultWidth(): void {
  const total = bodyWidth();
  sourceWidth.value = clampWidth(total > 0 ? total * SPLIT_DEFAULT_RATIO : SPLIT_MIN_LEFT);
}

/**
 * 拖动开始：记下起点并捕获指针，后续 move/up 都发到分隔条自己身上。
 *
 * <p>`setPointerCapture` 放在 try 里：某些环境（合成事件、指针已被系统接管）
 * 会抛异常，而那不该让整个拖动挂掉 —— 捕获失败时事件仍会冒泡到本元素，
 * 只是指针移出分隔条后可能收不到 move。
 */
function onSplitterDown(event: PointerEvent): void {
  if (sourceWidth.value === null) {
    applyDefaultWidth();
  }
  dragging.value = true;
  dragFrom.value = { pointerX: event.clientX, width: sourceWidth.value ?? SPLIT_MIN_LEFT };
  try {
    (event.currentTarget as HTMLElement | null)?.setPointerCapture(event.pointerId);
  } catch {
    // 捕获失败：拖动仍能工作，只是指针离开分隔条后可能丢事件
  }
  event.preventDefault();
}

function onSplitterMove(event: PointerEvent): void {
  if (!dragging.value) {
    return;
  }
  applyWidth(dragFrom.value.width + (event.clientX - dragFrom.value.pointerX), false);
}

function onSplitterUp(event: PointerEvent): void {
  if (!dragging.value) {
    return;
  }
  dragging.value = false;
  const handle = event.currentTarget as HTMLElement | null;
  if (handle?.hasPointerCapture(event.pointerId)) {
    handle.releasePointerCapture(event.pointerId);
  }
  // 拖动结束才落盘：拖动过程中每帧写 localStorage 没有必要
  if (sourceWidth.value !== null) {
    writeSplitRatio(props.fileResultId, props.taskId, sourceWidth.value / Math.max(bodyWidth(), 1));
  }
}

/** 双击：回到默认比例，并把记录清掉（"默认"与"没记录过"是同一种状态） */
function resetSplit(): void {
  removeSplitRatio(props.fileResultId, props.taskId);
  applyDefaultWidth();
}

/** 方向键：左右各按固定像素调整，Home/End 到两端 */
function onSplitterKeydown(event: KeyboardEvent): void {
  const base = sourceWidth.value ?? SPLIT_MIN_LEFT;
  if (event.key === 'ArrowLeft') {
    applyWidth(base - SPLIT_KEY_STEP, true);
  } else if (event.key === 'ArrowRight') {
    applyWidth(base + SPLIT_KEY_STEP, true);
  } else if (event.key === 'Home') {
    applyWidth(SPLIT_MIN_LEFT, true);
  } else if (event.key === 'End') {
    applyWidth(Number.MAX_SAFE_INTEGER, true);
  } else {
    return;
  }
  event.preventDefault();
}

/**
 * 量一次容器宽度并定下左栏宽。
 *
 * <p>优先用记住的比例，没有记录就按默认比例算 —— 两者都要过最小宽度约束。
 */
function measureSplit(): void {
  const total = bodyWidth();
  if (total <= 0) {
    return;
  }
  const remembered = readSplitRatio(props.fileResultId, props.taskId);
  sourceWidth.value = clampWidth(total * (remembered ?? SPLIT_DEFAULT_RATIO));
}

/** 窗口尺寸变化：左栏宽按新容器重新夹一次（保持像素意图，不做比例漂移） */
function onWindowResize(): void {
  if (dragging.value) {
    return;
  }
  if (sourceWidth.value === null) {
    measureSplit();
    return;
  }
  applyWidth(sourceWidth.value, false);
}
/** 预览当前页（0 = 预览还没就绪） */
const previewPage = ref(0);
const previewReady = computed(() => previewPage.value > 0);
/** 原文件总页数（预览加载完成后由子组件告知） */
const previewTotal = ref(0);
/** 是否只列预览当前页的元素 */
const followPreview = ref(true);
/** 在原文上点中的元素（与"选中"分开：点选保留高亮，选中随交互移动） */
const pickedKey = ref('');
/** 当前重点标注的元素 */
const activeKey = ref('');

/** 文件名后缀（大写下发给判断）；无后缀时为空 */
const fileExt = computed(() => {
  const name = props.fileName;
  const dot = name.lastIndexOf('.');
  return dot < 0 ? '' : name.slice(dot + 1).toUpperCase();
});

const fileKindText = computed(() => (fileExt.value ? `${fileExt.value} 文件` : '该文件'));

const fileSizeText = computed(() => {
  const size = sourceBlob.value?.size ?? 0;
  if (size <= 0) {
    return '';
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
});

/**
 * 预览形态：按当前支持的文件类型分派到对应渲染器。
 *
 * <p>白名单（`knowledge.file.enabled-formats`）= PDF/DOC/DOCX/XLS/XLSX；
 * 其中只有 PDF/DOCX/XLSX 有可用的浏览器端渲染器，DOC/XLS 与图片走降级
 * （图片在建档校验期就被拒，不会到这里）。
 */
type PreviewKind = 'pdf' | 'docx' | 'xlsx' | 'none';

const previewKind = computed<PreviewKind>(() => {
  if (fileExt.value === 'PDF') {
    return 'pdf';
  }
  if (fileExt.value === 'DOCX') {
    return 'docx';
  }
  if (fileExt.value === 'XLSX') {
    return 'xlsx';
  }
  return 'none';
});

/** 当前类型是否支持内嵌预览（决定提示文案与是否显示"可定位 N 个元素"） */
const canPreview = computed(() => previewKind.value !== 'none');

/** DOCX 元素 id 形如 `p12` / `t3`，其中数字即段落 / 表格序号（与解析产物同口径） */
function docxAnchorOf(item: StageContentItem, key: string): DocxAnchor | null {
  const id = item.alignKey ?? '';
  const match = /^([pt])(\d+)$/.exec(id);
  if (match === null) {
    return null;
  }
  const isTable = match[1] === 't';
  if (isTable && item.type !== 'TABLE') {
    return null;
  }
  const extra = item.extra ?? {};
  return {
    key,
    type: item.type ?? '',
    paragraphIndex: Number(match[2]),
    style: typeof extra.style === 'string' ? extra.style : '',
  };
}

/** XLSX 锚点：工作表名 + 单元格行列（0 基，与表格区域高亮同口径） */
function xlsxAnchorOf(item: StageContentItem, key: string): XlsxAnchor | null {
  const extra = item.extra ?? {};
  const sheetName = typeof extra.sheetName === 'string' ? extra.sheetName : '';
  const row = statNumber(extra.row as number | null);
  const col = statNumber(extra.col as number | null);
  // 表格元素只有工作表名、没有行列：用它定位整表
  if (sheetName !== '' && row === null && col === null) {
    return { key, type: item.type ?? '', sheetName, row: 0, col: 0, rowSpan: 0, colSpan: 0 };
  }
  if (sheetName === '' || row === null || col === null) {
    return null;
  }
  return {
    key,
    type: item.type ?? '',
    sheetName,
    row,
    col,
    rowSpan: statNumber(extra.rowSpan as number | null) ?? 1,
    colSpan: statNumber(extra.colSpan as number | null) ?? 1,
  };
}

/** Word 可定位锚点：只取能在渲染结果里对上号的元素 */
const docxAnchors = computed<DocxAnchor[]>(() =>
  rawItems.value
    .map((item, index) => docxAnchorOf(item, item.alignKey ?? `seq-${item.seq ?? index}`))
    .filter((anchor): anchor is DocxAnchor => anchor !== null),
);

/** Excel 可定位锚点 */
const xlsxAnchors = computed<XlsxAnchor[]>(() =>
  rawItems.value
    .map((item, index) => xlsxAnchorOf(item, item.alignKey ?? `seq-${item.seq ?? index}`))
    .filter((anchor): anchor is XlsxAnchor => anchor !== null),
);

/** 元素行：把 extra 里的页码/来源/网格/坐标摊平成可渲染字段，并按关键字过滤当前页 */
const visibleItems = computed(() => {
  const text = keyword.value.trim().toLowerCase();
  return rawItems.value
    .map((item, index) => {
      const extra = item.extra ?? {};
      const rows = statNumber(extra.rows as number | null);
      const cols = statNumber(extra.cols as number | null);
      return {
        key: item.alignKey ?? `seq-${item.seq ?? index}`,
        seq: item.seq ?? index + 1,
        typeText: elementTypeLabel(item.type),
        rawType: item.type ?? '',
        text: item.display ?? '',
        page: pageOf(item.extra),
        source: typeof extra.source === 'string' ? extra.source : '',
        grid: rows !== null && cols !== null ? `${rows}×${cols}` : '',
      };
    })
    .filter(
      (item) =>
        text === '' ||
        item.text.toLowerCase().includes(text) ||
        item.rawType.toLowerCase().includes(text) ||
        item.typeText.includes(text),
    );
});

/** 高亮框：只取有坐标且有页码的元素（Office 元素无坐标，自然不画） */
const highlights = computed<PreviewHighlight[]>(() =>
  rawItems.value
    .map((item, index) => {
      const bbox = bboxOf(item.extra);
      const page = pageOf(item.extra);
      if (!bbox || page === null) {
        return null;
      }
      const key = item.alignKey ?? `seq-${item.seq ?? index}`;
      const excerpt = (item.display ?? '').replace(/\s+/g, ' ').slice(0, 40);
      return {
        key,
        page,
        bbox,
        title: `${elementTypeLabel(item.type)}${excerpt ? `：${excerpt}` : ''}`,
      };
    })
    .filter((box): box is PreviewHighlight => box !== null),
);

/** 可用坐标定位的元素（画高亮框的条数）与它们覆盖的页数，说明预览联动是否可用 */
const highlightCount = computed(() => highlights.value.length);

const stats = computed<LineageParseStats | null>(() => detail.value?.parseStats ?? null);
const warnings = computed<string[]>(() => detail.value?.warnings ?? []);
const steps = computed(() => detail.value?.steps ?? []);

const statusText = computed(() => taskStatusLabel(detail.value?.status ?? ''));
const tone = computed(() => statusTone(detail.value?.status));
const isFailed = computed(() => tone.value === 'fail');

/** 状态徽标配色：类名写成字面量，样式检查才看得到 */
const TONE_CLASSES: Record<string, string> = {
  ok: 'is-ok',
  partial: 'is-partial',
  run: 'is-run',
  wait: 'is-wait',
  fail: 'is-fail',
};

const badgeClass = computed(() => TONE_CLASSES[tone.value] ?? 'is-wait');

const statItems = computed(() => {
  const s = stats.value;
  return [
    { key: 'pages', label: '页', value: formatCount(s?.pageCount) },
    { key: 'elements', label: '元素', value: formatCount(s?.elementCount) },
    { key: 'body', label: '正文', value: formatCount(s?.bodyCount) },
    { key: 'table', label: '表格', value: formatCount(s?.tableCount) },
    { key: 'image', label: '图片', value: formatCount(s?.imageCount) },
    { key: 'head', label: '页眉页脚', value: formatCount(s?.headerFooterCount) },
    { key: 'failed', label: '问题单元', value: formatCount(s?.failedUnitCount) },
    { key: 'duration', label: '耗时', value: formatDuration(s?.durationMs) },
  ];
});

/** 结论文案：后端给就用后端的，缺失时按状态回落 */
const conclusionText = computed(() => {
  if (detail.value?.parseSummary) {
    return detail.value.parseSummary;
  }
  if (isFailed.value) {
    return detail.value?.errorMsg ?? '—';
  }
  return '—';
});

const conclusionClass = computed(() => badgeClass.value);

/** 右栏页签：元素 / 告警 / 过程 */
const RESULT_TABS = [
  { key: 'elements', label: '元素' },
  { key: 'warnings', label: '告警' },
  { key: 'steps', label: '过程' },
] as const;

type ResultTabKey = (typeof RESULT_TABS)[number]['key'];

/** 当前页签：切换只换右栏内容，左栏位置与选中状态都不受影响 */
const activeTab = ref<ResultTabKey>('elements');

/** 页签角标数：没有内容的页签不显示数字 */
function tabCount(key: ResultTabKey): number {
  if (key === 'elements') {
    return elementTotal.value;
  }
  if (key === 'warnings') {
    return warnings.value.length;
  }
  return steps.value.length;
}

/** 失败建议：按错误码给下一步动作（同码不同意的都归到这几种） */
const FAIL_ADVICE: Record<string, string> = {
  PARSE_CORRUPTED:
    '文件内容无法解析：确认文件未损坏，或用带文本层的版本重新导出后，从页面工具栏再发起一次解析',
  SCANNED_UNSUPPORTED:
    '纯扫描件暂不支持：请提供带文本层的 PDF（OCR 能力尚未开放），再从页面工具栏发起解析',
  RATIO_BELOW_THRESHOLD:
    '成功单元占比过低：检查是否有大量扫描页或乱码，换一版文件后从页面工具栏发起解析',
};

const failAdvice = computed(() => {
  const code = detail.value?.errorCode ?? '';
  return (
    FAIL_ADVICE[code] ??
    '本次运行没有产物：确认文件可用后，从页面工具栏发起解析（会新建一条执行链）'
  );
});

/** 下载入口：后端 `GET /files/{fileId}` 流式返回原文件，fileId 由页面从文件结果带进来 */
const downloadReason = computed(() =>
  props.sourceFileId ? '' : '这条文件结果没有可用的源文件引用，暂时下载不了',
);

const canDownload = computed(() => Boolean(props.sourceFileId));

const downloading = ref(false);

/**
 * 下载原文件：取字节流 → 触发浏览器保存。
 *
 * <p>**不能直接开链接**：下载接口要 `Authorization` 头，用 `<a href>` 打开不会带令牌。
 * 故走统一实例取 blob，再用临时对象 URL 触发保存；触发后立刻回收 URL。
 */
async function downloadSource(): Promise<void> {
  if (!canDownload.value) {
    return;
  }
  downloading.value = true;
  copyHint.value = '';
  try {
    const blob = await getSourceFile(props.sourceFileId);
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = props.fileName;
    link.click();
    URL.revokeObjectURL(url);
  } catch {
    // 失败提示已由接口层统一拦截处理
    copyHint.value = '原文件下载失败';
  } finally {
    downloading.value = false;
  }
}

/** 当前页过滤命中数；未输入关键字时为 null（不展示"命中"） */
const pageMatched = computed(() => (keyword.value.trim() ? visibleItems.value.length : null));

const pageCount = computed(() => Math.max(1, Math.ceil(elementTotal.value / PAGE_SIZE)));

function onClose(): void {
  emit('close');
}

function toggleExpand(key: string): void {
  const next = new Set(expanded.value);
  if (next.has(key)) {
    next.delete(key);
  } else {
    next.add(key);
  }
  expanded.value = next;
}

/** 时间戳取到分钟（YYYY-MM-DD HH:mm） */
function formatClock(value: string | null | undefined): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '—';
}

/** 拉元素某一页；关键字过滤不参与查询（后端按页给），过滤在页内做 */
async function loadItems(): Promise<void> {
  itemsLoading.value = true;
  itemsError.value = '';
  try {
    const data = await getStageContent(props.fileResultId, 'PARSE', {
      taskId: props.taskId,
      docPage: followPreview.value && previewReady.value ? previewPage.value : undefined,
      page: page.value,
      limit: PAGE_SIZE,
    });
    rawItems.value = data.items ?? [];
    elementTotal.value = data.total ?? 0;
  } catch {
    // 失败提示已由接口层统一拦截处理
    rawItems.value = [];
    elementTotal.value = 0;
    itemsError.value = '元素内容加载失败';
  } finally {
    itemsLoading.value = false;
  }
}

/**
 * 取原文件字节：只依赖文件引用，与解析详情各自独立（解析失败也照常预览）。
 *
 * <p>失败分三类报，各自的下一步动作不同：
 * 没有文件引用 → 数据问题；HTTP 非 2xx → 取文件问题；拿到 JSON 错误信封 → 后端拒了这次读取。
 */
async function loadSource(): Promise<void> {
  sourceError.value = '';
  sourceBlob.value = null;
  previewPage.value = 0;
  if (!props.sourceFileId) {
    sourceError.value = '这条文件结果没有可用的源文件引用';
    return;
  }
  try {
    const blob = await getSourceFile(props.sourceFileId);
    if (blob.size === 0) {
      sourceError.value = '原文件内容为空（服务端返回 0 字节）';
      return;
    }
    // 下载失败时后端仍以 200 + JSON 错误信封返回，这里把它认出来
    if (blob.type.includes('json')) {
      sourceError.value = `取原文件失败：${await errorEnvelopeText(blob)}`;
      return;
    }
    sourceBlob.value = blob;
  } catch (err) {
    sourceError.value = `取原文件失败${httpStatusText(err)}`;
  }
}

/** 取 axios 错误里的 HTTP 状态码，拼成可读后缀 */
function httpStatusText(err: unknown): string {
  const status = (err as { response?: { status?: number } }).response?.status;
  return typeof status === 'number' ? `（HTTP ${status}）` : '';
}

/** 把 JSON 错误信封读成 `code msg`；读不出来时给原文串 */
async function errorEnvelopeText(blob: Blob): Promise<string> {
  try {
    const body = JSON.parse(await blob.text()) as { code?: number; msg?: string };
    return `${body.code ?? '—'} ${body.msg ?? '服务端返回了错误信息'}`;
  } catch {
    return '服务端返回的不是文件内容';
  }
}

/**
 * 左栏翻页 → 右栏切到该页元素。
 *
 * <p>跟随开关打开时按页重取（后端 docPage 过滤）；关闭时右栏保持全量列表，只移动选中项。
 */
function onPreviewPage(pageNo: number): void {
  previewPage.value = pageNo;
  if (!followPreview.value) {
    return;
  }
  page.value = 1;
  expanded.value = new Set<string>();
  void loadItems();
}

/** 预览文档就绪：记下总页数 / 工作表数（工具条展示用） */
function onPreviewLoaded(total: number): void {
  previewTotal.value = total;
}

/** Word / Excel 预览就绪：这两类没有页概念，栏头只显示文件信息与可定位元素数 */
function onOfficeLoaded(): void {
  previewTotal.value = 0;
}

/**
 * 右栏点元素 → 左栏定位并高亮。
 *
 * <p>按当前类型分派：PDF 画 bbox 框、Word 段落底色、Excel 单元格底色。
 * 三者都叫 `revealAnchor`（PDF 叫 `revealHighlight`），各自实现自己的定位方式。
 */
function onElementPick(key: string): void {
  activeKey.value = key;
  pickedKey.value = key;
  if (previewKind.value === 'pdf') {
    void previewRef.value?.revealHighlight(key);
    return;
  }
  if (previewKind.value === 'docx') {
    void docxRef.value?.revealAnchor(key);
    return;
  }
  if (previewKind.value === 'xlsx') {
    void xlsxRef.value?.revealAnchor(key);
  }
}

/** 左栏点原文 → 命中的元素滚到右栏并选中 */
function onPreviewPick(key: string | null): void {
  if (!key) {
    pickedKey.value = '';
    return;
  }
  pickedKey.value = key;
  activeKey.value = key;
  void scrollElementIntoView(key);
}

/** 把右栏某条元素滚到可见 */
async function scrollElementIntoView(key: string): Promise<void> {
  const resultPane = resultPaneRef.value;
  if (!resultPane) {
    return;
  }
  await nextTick();
  const row = resultPane.querySelector(`[data-key="${CSS.escape(key)}"]`);
  if (row instanceof HTMLElement) {
    row.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }
}

/** 双栏同跳某页：左栏滚到该页，右栏按该页重取（跟随关闭时也跳，保证"同跳"） */
async function goDocPage(pageNo: number): Promise<void> {
  if (!canPreview.value || !previewReady.value) {
    return;
  }
  await previewRef.value?.goToPage(pageNo);
  previewPage.value = pageNo;
  page.value = 1;
  expanded.value = new Set<string>();
  await loadItems();
}

/**
 * 告警文本切分：把可识别的页码范围拆成可点片段。
 *
 * <p>识别 `第 3 页`、`第 3–5 页`、`第 3-5 页` 三种写法（后端摘要用的是全角连字符）。
 */
function warningParts(text: string): { text: string; page: number | null }[] {
  const pattern = /第\s*(\d+)(?:\s*[–—-]\s*(\d+))?\s*页/g;
  const parts: { text: string; page: number | null }[] = [];
  let cursor = 0;
  for (const match of text.matchAll(pattern)) {
    const start = match.index;
    if (start > cursor) {
      parts.push({ text: text.slice(cursor, start), page: null });
    }
    parts.push({ text: match[0], page: Number(match[1]) });
    cursor = start + match[0].length;
  }
  if (cursor < text.length) {
    parts.push({ text: text.slice(cursor), page: null });
  }
  return parts.length > 0 ? parts : [{ text, page: null }];
}

function goPage(next: number): void {
  const target = Math.min(Math.max(next, 1), pageCount.value);
  if (target === page.value) {
    return;
  }
  page.value = target;
  expanded.value = new Set<string>();
  void loadItems();
}

/** 复制全部文本：只复制当前页已取回的文本，并按实际条数说明 */
async function copyAll(): Promise<void> {
  const text = visibleItems.value
    .map((item) => item.text)
    .filter(Boolean)
    .join('\n');
  if (!text) {
    copyHint.value = '当前页没有可复制的文本';
    return;
  }
  try {
    await navigator.clipboard.writeText(text);
    copyHint.value = `已复制当前页 ${visibleItems.value.length} 条（完整内容请逐页复制）`;
  } catch {
    copyHint.value = '浏览器拒绝了剪贴板访问，请手动选择文本复制';
  }
}

const copyHint = ref('');

// 跟随开关：打开时按预览当前页重取，关闭时回全量列表
watch(followPreview, () => {
  page.value = 1;
  expanded.value = new Set<string>();
  void loadItems();
});

// 预览就绪（拿到第一页）后，跟随模式要按该页重取一次
watch(previewReady, (ready) => {
  if (ready && followPreview.value) {
    page.value = 1;
    void loadItems();
  }
});

// 打开或切换到另一次运行时重新取数；关闭时不请求
watch(
  () => [props.visible, props.fileResultId, props.taskId] as const,
  ([visible]) => {
    if (!visible) {
      return;
    }
    detail.value = null;
    detailError.value = '';
    detailLoading.value = true;
    rawItems.value = [];
    page.value = 1;
    keyword.value = '';
    expanded.value = new Set<string>();
    copyHint.value = '';
    followPreview.value = true;
    previewPage.value = 0;
    activeKey.value = '';
    pickedKey.value = '';
    sourceError.value = '';
    void getParseDetail(props.fileResultId, props.taskId)
      .then((data) => {
        detail.value = data;
      })
      .catch(() => {
        detailError.value = '解析详情加载失败';
      })
      .finally(() => {
        detailLoading.value = false;
      });
    void loadSource();
    void loadItems();
    // 打开（或换运行）后量一次双栏宽度，按记住的比例定左栏宽
    sourceWidth.value = null;
    void nextTick(() => {
      measureSplit();
    });
  },
  { immediate: true },
);

onMounted(() => {
  window.addEventListener('resize', onWindowResize);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', onWindowResize);
});
</script>

<style scoped lang="css">
/* 遮罩：只负责把抽屉压在链图之上并承接"点外部关闭" */
.parse-drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  justify-content: flex-end;
  background: rgb(7 11 20 / 62%);
}

/*
 * 抽屉本体：宽屏取 1440px，窄屏按视口收窄且不溢出。
 *
 * 四个固定分区（头部 / 统计条 / 双栏 / 底部）纵向排开，高度锁在视口内、
 * overflow 隐藏：滚动只发生在中间双栏各自的容器里，外层不出滚动条。
 */
.parse-drawer {
  display: flex;
  width: min(1440px, 100vw);
  height: 100%;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  border-left: 1px solid var(--kb-line);
  background: var(--kb-bg-1);
  box-shadow: -12px 0 32px rgb(0 0 0 / 45%);
}

.parse-drawer-head {
  display: flex;
  flex: none;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--kb-line);
}

.parse-drawer-title {
  display: flex;
  min-width: 0;
  gap: 10px;
  align-items: center;
}

.parse-drawer-file {
  overflow: hidden;
  font-size: 15px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 状态徽标：底色与文字色按状态 tone 给（is-* 规则在全局 chain-graph.css —— 
   它们由计算属性动态给出，静态检查看不到 scoped 里的声明） */
.parse-drawer-badge {
  flex: none;
  padding: 2px 8px;
  border-radius: 99px;
  font-size: 11px;
  font-weight: 600;
}

.parse-drawer-x {
  flex: none;
  padding: 4px 10px;
  border: none;
  border-radius: 8px;
  background: var(--kb-surface);
  color: var(--kb-text-2);
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
  font-size: 12px;
  cursor: pointer;
}

.parse-drawer-x:hover {
  background: var(--kb-line-2);
}

.parse-drawer-ident {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  gap: 16px;
  padding: 9px 18px;
  border-bottom: 1px solid var(--kb-line);
  color: var(--kb-text-3);
  font-size: 11px;
}

.parse-drawer-ident-num {
  color: var(--kb-text-1);
  font-weight: 600;
}

/* 统计条：固定分区，内容一行放不下就换行，自身不滚动 */
.parse-drawer-statbar {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  gap: 8px 18px;
  align-items: baseline;
  padding: 10px 18px;
  border-bottom: 1px solid var(--kb-line);
}

/* 失败条：固定分区，把错误码 / 说明 / 建议压成一行一行的短句 */
.parse-drawer-failbar {
  flex: none;
  margin: 10px 18px 0;
  gap: 6px;
}

/*
 * 中间双栏：唯一会伸缩的分区。
 * flex: 1 + min-height: 0 是关键 —— 少了 min-height，子内容会把这一区撑高，
 * 于是整页出现滚动条。
 */
.parse-drawer-body {
  display: flex;
  flex: 1;
  min-height: 0;
  gap: 0;
  overflow: hidden;
}

/* 左栏：原文预览（失败/不支持只影响本栏）。
   width/min-width 是"还没量到容器"时的默认值（62% / 320px）；量到之后由 :style
   写内联宽度与 min-width 覆盖。min-width 一旦由脚本接管，拖动的最小值就以脚本的
   320px 为准 —— 与样式里的默认值同口径，不会出现"能拖到 320 却被 420 挡住"。 */
.parse-drawer-pane-source {
  display: flex;
  width: 62%;
  min-width: 320px;
  flex: none;
  flex-direction: column;
  overflow: hidden;
  border-right: 1px solid var(--kb-line);
}

/*
 * 分隔条：两栏之间的竖直拖拽条。
 * 视觉 6px，实际命中区靠 ::before 向两侧各扩 3px（太细不好点）。
 */
.parse-drawer-splitter {
  position: relative;
  flex: none;
  width: 6px;
  background: var(--kb-bg-1);
  cursor: col-resize;
  touch-action: none;
}

.parse-drawer-splitter::before {
  position: absolute;
  top: 0;
  bottom: 0;
  left: -3px;
  width: 12px;
  content: '';
}

.parse-drawer-splitter:hover,
.parse-drawer-splitter:focus-visible,
.parse-drawer-splitter.is-dragging {
  background: var(--kb-primary);
  outline: none;
}

/* 拖动过程中锁掉文字选中，避免拖到一半把标签文字选蓝 */
.parse-drawer-body.is-dragging {
  user-select: none;
}

/* 栏头：固定在本区顶部，不随内容滚动 */
.parse-drawer-pane-head {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  gap: 4px 10px;
  align-items: baseline;
  padding: 12px 14px 8px;
}

/* 预览组件占满栏头之下的空间；它内部自带工具条（固定）与画布（滚动） */
.parse-drawer-pane-fill {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  padding: 0 14px 12px;
}

/* 右栏：解析结果（页签固定，页签内容各自滚动） */
.parse-drawer-pane-result {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  overflow: hidden;
}

/* 页签条：固定在本区顶部 */
.parse-drawer-tabs {
  display: flex;
  flex: none;
  gap: 4px;
  padding: 8px 14px 0;
  border-bottom: 1px solid var(--kb-line);
}

.parse-drawer-tab {
  padding: 6px 12px;
  border: none;
  border-bottom: 2px solid transparent;
  background: none;
  color: var(--kb-text-3);
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
  font-size: 12px;
  cursor: pointer;
}

.parse-drawer-tab:hover {
  color: var(--kb-text-1);
}

/* 当前页签 */
.parse-drawer-tab.is-on {
  border-bottom-color: var(--kb-primary);
  color: var(--kb-primary);
  font-weight: 600;
}

.parse-drawer-tab-num {
  margin-left: 5px;
  padding: 0 5px;
  border-radius: 99px;
  background: var(--kb-tint);
  font-size: 10px;
}

/* 页签面板：头部/底部固定，中间那层滚动 */
.parse-drawer-tabpane {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
}

/* 页签内的固定工具行（搜索 / 跟随开关） */
.parse-drawer-tabhead {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  gap: 8px 12px;
  align-items: center;
  padding: 10px 14px;
  border-bottom: 1px solid var(--kb-line);
}

/* 页签内的固定底行（翻页） */
.parse-drawer-tabfoot {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 8px 14px;
  border-top: 1px solid var(--kb-line);
}

/*
 * 页签内容的滚动容器：本区唯一的滚动条就在这里。
 * min-height: 0 让它在 flex 里真正被压缩，内容才不会把面板顶出去。
 */
.parse-drawer-tabscroll {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  gap: 9px;
  overflow: hidden auto;
  padding: 12px 14px;
}

.parse-drawer-block {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.parse-drawer-block-title {
  display: flex;
  margin: 0;
  flex: none;
  gap: 10px;
  align-items: baseline;
  color: var(--kb-text-2);
  font-size: 12px;
  font-weight: 650;
}

/* 结论文案沿用状态色（规则同样在全局 chain-graph.css） */
.parse-drawer-conclusion {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}

.parse-drawer-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
}

.parse-drawer-stat {
  display: flex;
  gap: 5px;
  align-items: baseline;
  color: var(--kb-text-3);
  font-size: 11px;
}

.parse-drawer-stat-value {
  color: var(--kb-text-1);
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-size: 13px;
  font-weight: 600;
}

.parse-drawer-warnings {
  display: flex;
  margin: 0;
  padding: 0;
  gap: 5px;
  flex-direction: column;
  list-style: none;
}

.parse-drawer-warning {
  padding: 6px 10px;
  border-left: 2px solid var(--kb-warn);
  background: rgb(251 191 36 / 8%);
  color: var(--kb-text-2);
  font-size: 12px;
}

/* 告警里的页码范围：点了左右两栏一起跳到该页 */
.parse-drawer-page-link {
  padding: 0 2px;
  border: none;
  background: transparent;
  color: var(--kb-primary);
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
  font-size: 12px;
  text-decoration: underline;
  cursor: pointer;
}

.parse-drawer-page-link:hover {
  color: var(--kb-text-1);
}

/* 跟随预览当前页的开关 */
.parse-drawer-check {
  display: flex;
  gap: 6px;
  align-items: center;
  color: var(--kb-text-3);
  font-size: 11px;
  cursor: pointer;
}

.parse-drawer-block-bad {
  padding: 12px 14px;
  border: 1px solid rgb(248 113 113 / 28%);
  border-radius: 10px;
  background: rgb(248 113 113 / 8%);
}

.parse-drawer-error-code {
  margin: 0;
  color: var(--kb-danger);
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
  font-weight: 600;
}

.parse-drawer-error-msg {
  margin: 0;
  color: var(--kb-text-1);
  font-size: 12px;
}

.parse-drawer-advice {
  margin: 0;
  color: var(--kb-text-2);
  font-size: 12px;
  line-height: 1.6;
}

.parse-drawer-steps {
  display: flex;
  margin: 0;
  padding: 0;
  gap: 6px;
  flex-direction: column;
  list-style: none;
}

.parse-drawer-step {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: baseline;
  padding: 6px 10px;
  border: 1px solid var(--kb-line);
  border-radius: 8px;
  background: var(--kb-surface);
  font-size: 11px;
}

/* 失败的子步骤：左侧红条 + 红字，一眼看出卡在哪一步 */
.parse-drawer-step.is-bad {
  border-left: 2px solid var(--kb-danger);
}

.parse-drawer-step-name {
  color: var(--kb-text-1);
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-weight: 600;
}

.parse-drawer-step-meta {
  color: var(--kb-text-3);
}

.parse-drawer-step-error {
  flex-basis: 100%;
  color: var(--kb-danger);
}

.parse-drawer-search {
  width: 220px;
  max-width: 100%;
  padding: 6px 10px;
  border: 1px solid var(--kb-line-2);
  border-radius: 8px;
  background: var(--kb-surface);
  color: var(--kb-text-1);
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
  font-size: 12px;
}

.parse-drawer-search::placeholder {
  color: var(--kb-text-4);
}

.parse-drawer-elements {
  display: flex;
  margin: 0;
  padding: 0;
  gap: 8px;
  flex-direction: column;
  list-style: none;
}

.parse-drawer-element {
  padding: 8px 10px;
  border: 1px solid var(--kb-line);
  border-radius: 8px;
  background: var(--kb-surface);
  cursor: pointer;
}

/* 当前重点标注的元素（左栏同时画高亮框） */
.parse-drawer-element.is-active {
  border-color: var(--kb-warn);
  background: rgb(251 191 36 / 8%);
}

/* 在原文上点选命中的元素 */
.parse-drawer-element.is-picked {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 1px var(--kb-primary) inset;
}

.parse-drawer-element-head {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: baseline;
}

/* 类型徽标：元素列表靠它一眼分辨类型 */
.parse-drawer-type {
  flex: none;
  padding: 1px 7px;
  border-radius: 5px;
  background: var(--kb-tint);
  color: var(--kb-primary);
  font-size: 11px;
  font-weight: 600;
}

.parse-drawer-element-meta {
  color: var(--kb-text-3);
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-size: 10px;
}

.parse-drawer-toggle {
  margin-left: auto;
  padding: 1px 8px;
  border: 1px solid var(--kb-line-2);
  border-radius: 6px;
  background: none;
  color: var(--kb-text-2);
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
  font-size: 10px;
  cursor: pointer;
}

.parse-drawer-toggle:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}

/*
 * 元素文本：默认两行封顶（超出省略），展开后放开。
 * 用 `-webkit-box` + `line-clamp`：折叠态两行，展开态由 is-expanded 还原成普通块。
 */
.parse-drawer-element-text {
  display: -webkit-box;
  margin: 6px 0 0;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  color: var(--kb-text-2);
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.parse-drawer-element-text.is-expanded {
  display: block;
  -webkit-line-clamp: unset;
  line-clamp: unset;
}

.parse-drawer-page-btn {
  padding: 4px 12px;
  border: 1px solid var(--kb-line-2);
  border-radius: 8px;
  background: var(--kb-surface);
  color: var(--kb-text-2);
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
  font-size: 12px;
  cursor: pointer;
}

.parse-drawer-page-btn:disabled {
  color: var(--kb-text-4);
  cursor: not-allowed;
}

.parse-drawer-page-btn:not(:disabled):hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}

.parse-drawer-page-info {
  color: var(--kb-text-3);
  font-size: 11px;
}

.parse-drawer-hint {
  margin: 0;
  color: var(--kb-text-3);
  font-size: 11px;
}

.parse-drawer-hint-bad {
  color: var(--kb-danger);
}

.parse-drawer-foot {
  display: flex;
  flex: none;
  gap: 10px;
  align-items: center;
  padding: 12px 18px;
  border-top: 1px solid var(--kb-line);
}

.parse-drawer-btn {
  padding: 6px 14px;
  border: 1px solid var(--kb-line-2);
  border-radius: 8px;
  background: var(--kb-surface);
  color: var(--kb-text-2);
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
  font-size: 12px;
  cursor: pointer;
}

.parse-drawer-btn:disabled {
  color: var(--kb-text-4);
  cursor: not-allowed;
}

.parse-drawer-btn-primary {
  border-color: transparent;
  background: var(--kb-primary);
  color: var(--kb-btn-text);
  font-weight: 600;
}

.parse-drawer-btn-primary:hover {
  box-shadow: 0 0 12px var(--kb-glow);
}
</style>
